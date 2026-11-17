package com.gtlmodular.common.machine.nanochip;

import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.IRecipeHandler;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.OverclockingLogic;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.content.ContentModifier;
import com.gregtechceu.gtceu.api.recipe.logic.OCParams;
import com.gregtechceu.gtceu.api.recipe.logic.OCResult;
import com.gregtechceu.gtceu.common.data.GTRecipeModifiers;
import com.gregtechceu.gtceu.utils.GTUtil;
import com.gtlmodular.GTLModular;
import com.gtlmodular.common.config.GTLModularConfig;
import net.minecraft.core.Direction;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * `NanochipModuleMachine` 里&#12288;<b>只读 / 无状态</b>&#12288;的那部分辅助逻辑（P2 M2 纯搬移，2026-11-08）。
 *
 * <p>本类是<b>逐字搬移</b>的产物：方法体、判断顺序、日志文本、常量与偏移表全部原样，唯一改的是
 * 「{@code this} → {@code module} 参数」与「私有成员 → 机器上的公开 getter」。
 * 这里<b>不持有任何状态</b>（无字段），所有方法都是纯读或纯算。
 *
 * <h2>搬家清单</h2>
 * <ul>
 *   <li><b>结构</b>：{@link #structureLogLabel}（日志短标签）、{@link #encodeNacSlots}（仓位清单编码）；</li>
 *   <li><b>连接</b>：{@link #hostConnected}（主机权威集合判定）、{@link HostEnergyContainer}
 *       （模块↔主机取电代理）；</li>
 *   <li><b>配方修正</b>：{@link #moduleAvailableInputPower}、{@link #moduleOverclock}、
 *       {@link #moduleFlexibleOverclock}、{@link #scaleOutputsForSimpleLogistics}。</li>
 * </ul>
 *
 * <p><b>为什么 {@code moduleOverclock} 算完要回调机器</b>：它原本同时写两处机器私有状态
 * （{@code MODULE_EUT} 与 {@code exposedEuPerTick}）。这两个字段是 private ⇒ 搬不走，
 * 由机器侧的 {@code applyOverclockEu(...)} 在原位置、用原值、按原顺序写回
 * （见 {@link #moduleOverclock} 的注释）。除此之外本类不写任何状态。
 *
 * <p>留在 {@code NanochipModuleMachine} 里的方法都是<b>有状态</b>的（tick 订阅、缓存、同步字段、
 * 结构折叠、NBT）或<b>接口契约</b>（{@code IModularMachineModule} 的 {@code @Override}），
 * 搬走会改变生命周期语义，故一律不动。
 */
public final class NanochipModuleLink {

    private NanochipModuleLink() {
    }

    // ==========================================================================================
    // 结构
    // ==========================================================================================

    /** 汇总行里本机的短标签（例：{@code 纳米芯片组装矩阵@(-74,69,-105)}）。 */
    static String structureLogLabel(NanochipModuleMachine module) {
        return module.getModuleKind().nameZh() + "@" + module.getPos().toShortString();
    }

    /**
     * ★ 把「结构里真实站着的 part」编码成逗号分隔的 {@code BlockPos.asLong()}。
     *
     * <p>与 {@code NanochipAssemblyComplexMachine#encodeNacSlots()} <b>逐字节一致</b>（客户端只有一个
     * 解码器）。数据来源 = GTCEu 的 {@code MultiblockControllerMachine.getParts()}，<b>不区分仓的来源</b>。
     */
    static String encodeNacSlots(NanochipModuleMachine module) {
        List<IMultiPart> parts = module.getParts();
        StringBuilder sb = new StringBuilder();
        int kept = 0;
        int dropped = 0;
        for (IMultiPart part : parts) {
            if (part == null || part.self() == null) {
                continue;
            }
            if (kept >= NanochipModuleMachine.MAX_NAC_SLOTS) {
                dropped++;
                continue;
            }
            if (kept > 0) {
                sb.append(',');
            }
            sb.append(part.self().getPos().asLong());
            kept++;
        }
        if (dropped > 0) {
            GTLModular.LOGGER.warn("[gtlmodular] CTM: 仓位清单超过上限 {} 条（结构里 {} 个 part @{}），"
                            + "已截断 {} 条 —— 被截断的那几格外壳保持原样（不参与连接纹理）。",
                    NanochipModuleMachine.MAX_NAC_SLOTS, parts.size(), module.getPos(), dropped);
        }
        return sb.toString();
    }

    // ==========================================================================================
    // 连接
    // ==========================================================================================

    /**
     * ★【2026-09-16】「本机连着复合体吗」—— <b>只读主机侧的权威结果</b>，模块自己不再写那个集合。
     *
     * <p>三个条件全都要满足：
     * <ol>
     *   <li>{@code isFormed()}：模块自己成形（没成形不可能被主机收进去；GTCEu 的
     *       {@code IModularMachineHost.isValidModule} 也是这么要求的）；</li>
     *   <li>主机成形；</li>
     *   <li><b>主机的「已连接模块集合」里有本机</b>（{@link NanochipAssemblyComplexMachine#isModuleConnected}）
     *       —— 这才是唯一权威。</li>
     * </ol>
     * <p>主机在结构成形那一刻、以及此后每 20 tick 按结构 {@code F} 位重算该集合，所以本机成形后
     * 最坏 1 秒内会被登记（旧代码里模块自己是"谁后跑谁覆盖"的一方，正是连接抖动的根因）。
     */
    static boolean hostConnected(NanochipModuleMachine module) {
        if (!module.isFormed()) {
            return false;
        }
        NanochipAssemblyComplexMachine h = module.getHost();
        return h != null && h.isFormed() && h.isModuleConnected(module);
    }

    /**
     * 代理容器：蓄电/耗电都走复合体。
     *
     * <p>★ 2026-11-08 修（0.3.350 崩溃：{@code HostEnergyContainer cannot be cast to IRecipeHandler}）：
     * 它以前只 implements {@link IEnergyContainer}，但我们又把它注册进了
     * {@code capabilitiesProxy} 的 EU 处理器列表 —— {@code RecipeRunner.handleContentsInternal}
     * 会把列表里的每一项都当 {@link IRecipeHandler} 强转 ⇒ 一进世界 tick 就 ClassCastException 崩服。
     * 现在补齐 {@link IRecipeHandler}&lt;{@code Long}&gt; 的四件套（能力/内容/总量/逐条处理），
     * 与主机侧 {@code NanochipEnergy.GridEnergyContainer} 完全同构。
     *
     * <p>取电语义仍不变：匹配期报「可用量」（电网/免费电 = 按需现取、视作充足；否则报模块缓存），
     * 真扣电只走 {@link #changeEnergy(long)}（扣模块缓存），不会凭空造电。
     */
    static final class HostEnergyContainer implements IEnergyContainer, IRecipeHandler<Long> {
        private final NanochipModuleMachine module;

        HostEnergyContainer(NanochipModuleMachine module) {
            this.module = module;
        }

        private NanochipAssemblyComplexMachine host() {
            NanochipAssemblyComplexMachine h = module.getHost();
            return (h != null && h.isFormed()) ? h : null;
        }

        @Override
        public long getEnergyStored() {
            NanochipAssemblyComplexMachine h = host();
            return h == null ? 0L : h.getTotalStoredEU();
        }

        @Override
        public long getEnergyCapacity() {
            NanochipAssemblyComplexMachine h = host();
            return h == null ? 0L : h.getTotalCapacityEU();
        }

        @Override
        public long getInputVoltage() {
            NanochipAssemblyComplexMachine h = host();
            return h == null ? 0L : h.getMaxInputVoltage();
        }

        @Override
        public long getInputAmperage() {
            return 1L;
        }

        @Override
        public boolean inputsEnergy(net.minecraft.core.Direction side) {
            return false; // 不从旁路充能，只由复合体供给
        }

        @Override
        public long acceptEnergyFromNetwork(net.minecraft.core.Direction side, long amount, long voltage) {
            return 0L; // 拒绝直接注入
        }

        @Override
        public long changeEnergy(long amount) {
            if (amount >= 0L) {
                return 0L; // 模块不回充复合体
            }
            // ★ 2026-09-21：吃主机的电改走**本模块自己的能量缓存**（缓存由主机每 20 tick 按优先级灌），
            //   所以这里不再直接向主机要电 —— 缓存不够就一点不扣，走 GT 的「等电」分支。
            long need = -amount;
            return module.consumeBufferEU(need) ? -need : 0L;
        }

        // ---------------- IRecipeHandler<Long>：让配方 EU 结算真的看得见这个容器 ----------------

        @Override
        public RecipeCapability<Long> getCapability() {
            return EURecipeCapability.CAP;
        }

        /**
         * 匹配期「本容器现在有多少 EU」。
         * 电网/免费电 = 按需现取（与主机同口径，视作充足）；否则报模块自己的缓存。
         */
        private long availableForMatching() {
            if (module.drawingFromHost()
                    && (GTLModularConfig.isNanochipFreeEnergyEnabled()
                        || GTLModularConfig.isNanochipGridPowerEnabled())) {
                return Long.MAX_VALUE;
            }
            return Math.max(0L, module.getBufferEU());
        }

        @Override
        public List<Object> getContents() {
            return Collections.singletonList(availableForMatching());
        }

        @Override
        public double getTotalContentAmount() {
            return (double) availableForMatching();
        }

        /**
         * 逐条结算：{@code IO.IN} 时扣 EU（扣模块缓存），返回「还差多少」（{@code null} = 满足）。
         * 模拟（{@code simulate}）只算不扣。与主机侧 GridEnergyContainer 的处理完全同构。
         */
        @Override
        public List<Long> handleRecipeInner(IO io, GTRecipe recipe, List<Long> left,
                                            @org.jetbrains.annotations.Nullable String slot,
                                            boolean simulate) {
            if (io != IO.IN || left == null || left.isEmpty()) {
                return left;
            }
            long need = 0L;
            for (Long v : left) {
                if (v == null || v <= 0L) {
                    continue;
                }
                need = need > Long.MAX_VALUE - v ? Long.MAX_VALUE : need + v;
            }
            if (need <= 0L) {
                return null;
            }
            long paid;
            if (simulate) {
                paid = Math.min(need, availableForMatching());
            } else {
                long signed = this.changeEnergy(-need);
                paid = signed >= 0L ? 0L : -signed;
            }
            long remain = need - paid;
            return remain <= 0L ? null : List.of(remain);
        }
    }

    // ==========================================================================================
    // 配方修正
    // ==========================================================================================

    /**
     * 该模块的「可用输入功率」（wiki：「超频后的功率不能超过 NAC 可用输入功率」）。
     *
     * <p>口径：模块自己装了能源仓 ⇒ 用它自己的电压；吃复合体的电（{@code drawingFromHost}）
     * ⇒ 取「模块电压」与「NAC 最大输入功率
     * （能源仓电压 × 安培数，{@link NanochipAssemblyComplexMachine#getMaxInputPower()}）」中的较大者。
     *
     * <p>★ 2026-09-21（能量缓存这一轮）：原先这里是 **1 A 的保守下界**，注释里写明"算准安培数属于能量缓存那一轮的活"
     * —— 现在那一轮到了：主机侧已经把 {@code getMaxInputPower()}（电压 × 安培）暴露出来，本方法改用它。
     */
    private static long moduleAvailableInputPower(NanochipModuleMachine module, long ownVoltage) {
        long cap = ownVoltage;
        NanochipAssemblyComplexMachine host = module.getHost();
        if (host != null && module.hostConnected()) {
            long hostPower = host.getMaxInputPower();
            if (hostPower > cap) {
                cap = hostPower;
            }
        }
        return cap;
    }

    /**
     * 模块配方修正：2/2 封顶的**无损**电力超频 + 5 秒时长下限。
     * （原版注释：{@code Modules have capped 2/2 overclocks ... The recipe time can not be
     * overclocked below 5 seconds.}）
     */
    public static GTRecipe moduleOverclock(MetaMachine machine, GTRecipe recipe,
                                           OCParams params, OCResult result) {
        if (!(machine instanceof NanochipModuleMachine module)) {
            return null;
        }
        long maxVoltage = module.getOverclockVoltage();
        if (maxVoltage <= 0L) {
            // ★ 2026-11-08：模块无法超频 ⇒ 这条配方根本 build 不出来（界面会显示「电力输入不足」）。
            //   打一条限流诊断，把「连没连主机 / 是不是吃主机电 / 主机电压与功率 / 自己有没有仓 / 电网开关」一次列清。
            module.logNoOverclockVoltage();
            return null;
        }
        GTRecipe copy = recipe.copy();
        long eut = Math.max(1L, RecipeHelper.getInputEUt(copy));
        int duration = Math.max(1, copy.duration);
        // ★★ 2026-09-21（0.1.27）对齐 NH 官方规格「无损超频(2/2)」★★
        //   本地 wiki 底本逐页写着（`_census/wiki_抓取/raw/*.wiki.txt`，19 页，同一段话）：
        //   「取 NAC能源仓等级 与 配方等级 之差，单次耗时除以2、**功率乘以2**；
        //     处理时间不会低于5秒，且超频后的功率不能超过 NAC 可用输入功率。」
        //   ⇒ 两条都必须改：
        //   ① 我方旧实现是 `eut *= 4L`（GT 的"完美超频"口径），**与"无损"矛盾** —— 无损是 ×2；
        //   ② 超频**次数**来自「能源仓等级 − 配方等级」，而不是只看"功率还够不够"。
        // ★ 2026-11-08 参考伪神之锻炉：吃无线电网/免费电的模块，档位 = 主机按余额折算的「假电压档」；
        //   物理能源仓模式仍按能源仓电压算。
        int hatchTier = module.isHostGridPowered()
                ? module.getTier()
                : GTUtil.getTierByVoltage(maxVoltage);
        int recipeTier = GTUtil.getTierByVoltage(eut);
        // ⚠️ **不再套 MAX_OVERCLOCKS(2) 封顶**：主机 wiki 页「超频与缓存」写的就是
        //    「取 NAC能源仓等级与配方等级之差」——**没有封顶**；我们自己的 parity 体检
        //    `check_nac_mechanics_parity.js` 的 X4 分歧条目也早用 NH 字节码写明了
        //    「NH = 时长÷2 / EU×2，次数 = 主机能源仓等级 − 配方等级（**无 2 次封顶**），地板 100 tick」。
        //    (2/2) 那个记法是 GTCEu 对"无损超频"的标注（EU×2 / 时长÷2），**不是"最多两次"**。
        int maxOc = Math.max(0, hatchTier - recipeTier);
        long powerCap = moduleAvailableInputPower(module, maxVoltage);
        int oc = 0;
        while (oc < maxOc
                && duration / 2 >= NanochipModuleMachine.MIN_DURATION_TICKS
                && eut <= powerCap / 2L) {
            eut *= 2L;
            duration /= 2;
            oc++;
        }
        // 组装矩阵享受复合体的纳米校准与模块加成（原版：模块的 Speed / EU cost / running cost
        // 由模块自身的机制与 NAC 的 Calibration 共同给出）
        int floorTicks = NanochipModuleMachine.MIN_DURATION_TICKS;
        if (module.getModuleKind() == NanochipModuleKind.ASSEMBLY_MATRIX) {
            NanochipAssemblyComplexMachine host = module.getHost();
            if (host != null && module.hostConnected()) {
                NanochipCalibration.Family family = NanochipAssemblyComplexMachine.familyOf(recipe);
                double euMul = host.getCalibration().euMultiplier() * host.moduleEuMultiplier(family);
                double durMul = host.getCalibration().durationMultiplier(family)
                        * host.moduleDurationMultiplier(family);
                eut = (long) Math.max(1.0D, Math.min((double) Long.MAX_VALUE, eut * euMul));
                duration = (int) Math.max(floorTicks,
                        Math.min(Integer.MAX_VALUE, Math.round(duration * durMul)));
                floorTicks = host.minDurationTicks();
            }
        }
        // 蚀刻阵列：晶体满校准后「激光源仓」开始生效（原版机制，1:1 照抄两条公式）
        if (module.getModuleKind() == NanochipModuleKind.ETCHING_ARRAY) {
            NanochipAssemblyComplexMachine host = module.getHost();
            if (host != null && module.hostConnected()
                    && host.getCalibration().specialActive(NanochipCalibration.Family.CRYSTAL)) {
                long amperage = module.laserAmperage();
                if (amperage >= 512L) {
                    // Divides EU Cost by each Amperage Interval over 256A
                    eut = Math.max(1L, eut / (amperage / 256L));
                }
                int laserTier = module.laserHatchTier();
                if (laserTier > 9) {
                    // Divides Recipe Duration by (Hatch Tier - 9)
                    duration = Math.max(floorTicks, duration / (laserTier - 9));
                }
            }
        }
        // 把真实 EU/t 存起来，配方本身的 EU 输入置 0：这样 GTCEu 的输入匹配不会
        // 在“模块没有能源仓”时把配方拦掉；真正的电在 handleRecipeWorking 里按这个值
        // 从主机无线电网/缓存持续扣。
        // ★ 2026-11-08【P2 M2】：两处写（MODULE_EUT / exposedEuPerTick）是机器私有状态，搬不走
        //   ⇒ 原位置、原值、原顺序回调机器写回（见 NanochipModuleMachine#applyOverclockEu）。
        NanochipModuleMachine.applyOverclockEu(module, Math.max(1L, eut));
        RecipeHelper.setInputEUt(copy, 0L);
        copy.duration = Math.max(floorTicks, duration);
        copy.ocTier = oc;
        return copy;
    }

    /** 备用入口：电压不足时仍按标准非完美超频处理（模块可单独使用）。 */
    public static GTRecipe moduleFlexibleOverclock(MetaMachine machine, GTRecipe recipe,
                                                   OCParams params, OCResult result) {
        GTRecipe direct = moduleOverclock(machine, recipe, params, result);
        GTRecipe modified;
        if (direct != null) {
            modified = direct;
        } else {
            modified = GTRecipeModifiers.ELECTRIC_OVERCLOCK
                    .apply(OverclockingLogic.NON_PERFECT_OVERCLOCK_SUBTICK)
                    .apply(machine, recipe, params, result);
        }
        // ★★ 2026-09-21（0.1.23）**产出 0.5 缩放已从这条链上搬走** ★★
        //   理由见 ModuleRecipeLogic.setupRecipe 的注释：这条链 = 注册在 builder 上的
        //   `recipeModifier`，而在 gtlcore 的 `MultipleRecipesLogic` 下它**根本不会被调用**
        //   （javap：该类全文无 fullModifyRecipe / doModifyRecipe 调用）⇒ 挂在这里等于没挂，
        //   队长看到的"简单模式产出依然是 16"就是这么来的。
        //   现在缩放只有**一个**作用点（`ModuleRecipeLogic.setupRecipe`）⇒ 结构上不可能被缩两次。
        // ★ 模块已改为继承原版 WorkableElectricMultiblockMachine（不再走 gtlcore 的 MultipleRecipesLogic）
        //   ⇒ 本修饰器**现在是真正会被调用的**（原版 RecipeLogic 走 fullModifyRecipe 这条链）。
        //   并行也改走原版：GTCEu 自带的 ParallelLogic 按「输入余量 / 输出可合并」放大整条配方，
        //   这就是模块「无限并行」的唯一来源。
        GTRecipe base = modified != null ? modified : recipe;
        return com.gregtechceu.gtceu.api.recipe.modifier.ParallelLogic
                .applyParallel(machine, base, Integer.MAX_VALUE, false).getFirst();
    }

    /**
     * 简单模式（{@code nanochipComplex.simpleLogisticsMode = true}）下，<b>组装矩阵</b>的物品与流体
     * 产出按本倍率缩放：配方 JSON 里仍是 {@code count = 16}，到机器结算这一步才乘 0.5 ⇒ <b>实出 8</b>。
     *
     * <p><b>为什么配方数据不动、改在结算处</b>（队长给的两个选项里选前者，理由三条）：
     * <ol>
     *   <li><b>一份数据两种模式都对</b>：{@code data/gtlmodular/recipes/nanochip/assembly_matrix/*.json}
     *       与两份规格 {@code _census/nac_matrix_spec.json} / {@code _census/nac_cw_matrix_spec.json}
     *       的 {@code outCount = 16} 全部<b>原样不动</b>，体检
     *       {@code check_nac_p1_recipes.js} / {@code check_nac_p2_recipes.js} / {@code check_nac_cc_items.js}
     *       的登记值一条都不用改（不会出现"规格 16 / 简单模式要 8"的两个真相）；
     *   <li><b>没有条件表达能力可用</b>：KubeJS/数据包侧的 {@code forge:mod_loaded} 只能表达"装了哪个模组"，
     *       <b>表达不了配置开关</b>；GTCEu 的配方 condition 里也没有"读本模组配置"的现成 condition
     *       （做过调查：{@code GTRecipeConditions} 只有 mod_loaded / 维度 / 研究 之类），
     *       硬做两套配方就得给整套数据包加一份 30 条的镜像 + 一套自定义 condition —— 风险远大于收益；
     *   <li><b>缩放点唯一且可证</b>：{@code moduleOverclock}/{@code moduleFlexibleOverclock} 已经是本模组
     *       唯一的模块配方入口（{@code NanochipMachines.java:534} 注册
     *       {@code .recipeModifier(NanochipModuleMachine::moduleFlexibleOverclock)}），改动只此一处。
     * </ol>
     *
     * <p>⚠️ <b>2026-09-21（0.1.23）更正</b>：上面第 3 条"缩放点唯一且可证"<b>当时是错的</b> ——
     * 那条链（注册的 {@code recipeModifier}）在 gtlcore 的 {@code MultipleRecipesLogic} 下
     * <b>从来不会被调用</b>（取证见 {@code ModuleRecipeLogic.setupRecipe} 的注释），
     * 所以 0.1.19～0.1.22 之间这段缩放<b>一次都没生效</b>（队长实测"简单模式产出依然是 16"）。
     * 现在真正的唯一作用点是 {@code ModuleRecipeLogic.setupRecipe(GTRecipe)} ——
     * gtlcore 每次拿到配方都会经过它，且只有它一处调用本方法。
     *
     * <p><b>为什么不会与配方解析冲突</b>：配方 JSON 照旧解析成 {@code outputs.item[0].content.count = 16}
     * 的 {@code GTRecipe}（注册表里那条共享对象<b>一个字节都不变</b>）；缩放发生在
     * {@code setupRecipe} 里，产出的是一条<b>深拷贝</b>（{@code GTRecipe.copy()} +
     * {@code copyContents(outputs, ContentModifier.multiplier(0.5))}），
     * 由 {@code RecipeLogic} 作为"本条要跑的配方"持有（{@code getLastRecipe()}）。
     * ⇒ 扣料 / 产出 / 纳米校准记账读的都是同一个对象，<b>实出多少就记多少</b>
     * （不会出现"界面写 16、实际给 8"或"校准按 16 记账"这种错位）。
     *
     * <p><b>⚠ 必须 copy()</b>：{@code GTRecipeModifiers.ELECTRIC_OVERCLOCK} 那条回落路径
     * <b>有可能原样返回入参</b>（= 配方注册表里那条共享对象，javap：{@code copyContents} 只在
     * {@code copy()} 里生成新 Map）—— 就地改它的 {@code outputs} 会污染所有机器。
     * 所以这里一律先 {@code copy()} 再整表替换 {@code outputs}。
     * （{@code copyContents} 的字节码已核：返回 {@code new HashMap} + 每个 {@code Content}
     * 都经 {@code Content.copy(cap, modifier)} 造新对象；{@code ItemRecipeCapability.copyWithModifier}
     * 走 {@code modifier.apply(amount).intValue()} ⇒ 16 × 0.5 = 8。装配矩阵 30 条配方均只有
     * <b>一个</b> item 输出（count=16），所以不存在"别的产物被缩成 0"的情形。）
     *
     * <p><b>不改这里的两件事</b>（如实登记）：
     * <ul>
     *   <li><b>模块层（元件 ×2）/ 换算层（真物品 ⇄ CC 1:1）</b>：一行没动；</li>
     *   <li><b>主机「直通档」</b>（{@code NanochipAssemblyComplexMachine.nacRecipeModifier}，
     *       跑的是 {@code circuit_assembler} 配方表）：<b>不在</b>本次缩放范围内 ——
     *       队长点名的是"组装矩阵"那 16 个，直通档是另一条路（它的产出由配方 JSON 决定）。</li>
     * </ul>
     *
     * <p>★ <b>2026-09-21 队长拍板：产出直接写进配方，运行期不再缩放</b>。
     * 队长原话：「组装矩阵的 JSON 16 → 8（让 JEI 也显示 8，跟运行期一致）」——
     * 本函数这一层 ×0.5 <b>停用</b>：
     * <ul>
     *   <li><b>产出落在配方里</b>：30 条 {@code data/gtlmodular/recipes/nanochip/assembly_matrix/*.json}
     *       的 {@code outputs.item[0].content.count} 与两份规格（{@code _census/nac_matrix_spec.json}
     *       20 行 / {@code _census/nac_cw_matrix_spec.json} 10 行）的 {@code outCount}
     *       先 <b>16 → 8</b>（规格留痕：{@code outCountBefore16 = 2}、{@code outCountBefore8 = 16}，
     *       顶层 {@code outCountRework20260921}），
     *       ★ 同日再按队长令「修改电路产出的数量，让每个系列最基础的产出八，4，2，1最后每个系列的主机产出一份」
     *       改成<b>按档位阶梯</b>：<b>处理器 8 / 装配 4 / 电脑 2 / 主机 1</b>
     *       （7 个系列同构；规格留痕 {@code outCountBeforeLadder = 8}，顶层 {@code outCountReworkLadder20260921}）；
     *       配方<b>条数不变</b>（仍 30 条），只改数值。JEI 读的就是这条数据 ⇒ JEI 显示的就是阶梯值；</li>
     *   <li><b>运行期不再缩放</b>：{@link NanochipModuleMachine#SIMPLE_LOGISTICS_OUTPUT_MULTIPLIER} 置 {@code 1.0D}，
     *       本函数上面那条 {@code >= 1.0D} 短路直接 {@code return recipe;} ⇒ 下面的深拷贝 +
     *       {@code ContentModifier.multiplier} 代码<b>保留但不再执行</b>（留作将来若又要
     *       "运行期有别于 JEI"的现成挂点，也让 {@code _census/check_module_output_scaling.js}
     *       的 A1/A2/A3/A5 结构判据继续成立：作用点唯一、只动产出、不误伤主机）；</li>
     *   <li>⇒ <b>JEI = 简单模式运行期 = 原版模式运行期 = 同一套阶梯（8/4/2/1）</b>，三处一致，
     *       不会再被缩两次（阶梯值再 ×0.5 会把处理器缩成 4、主机缩成 0 这条歧路已堵死）；</li>
     *   <li>上文提到的「配方 JSON 照旧解析成 {@code count = 16}」「16 × 0.5 = 8」
     *       「一个 item 输出（count=16）」是 <b>2026-09-19 当时的实况</b>，自本次起作废
     *       （原文保留，只为留痕）。</li>
     * </ul>
     */
    public static GTRecipe scaleOutputsForSimpleLogistics(MetaMachine machine, GTRecipe recipe) {
        if (recipe == null || !GTLModularConfig.isNanochipSimpleLogistics()) {
            return recipe;   // 原版模式：原样返回，行为逐语义不变
        }
        if (!(machine instanceof NanochipModuleMachine module)
                || module.getModuleKind() != NanochipModuleKind.ASSEMBLY_MATRIX) {
            return recipe;   // 只缩放组装矩阵；其余 13 台模块一字不动
        }
        if (NanochipModuleMachine.SIMPLE_LOGISTICS_OUTPUT_MULTIPLIER >= 1.0D) {
            return recipe;
        }
        GTRecipe copy = recipe.copy();
        java.util.Map<RecipeCapability<?>, java.util.List<Content>> scaled =
                recipe.copyContents(recipe.outputs,
                        ContentModifier.multiplier(NanochipModuleMachine.SIMPLE_LOGISTICS_OUTPUT_MULTIPLIER));
        copy.outputs.clear();
        copy.outputs.putAll(scaled);
        return copy;
    }
}
