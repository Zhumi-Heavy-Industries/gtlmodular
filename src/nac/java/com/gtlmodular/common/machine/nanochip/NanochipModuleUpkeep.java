package com.gtlmodular.common.machine.nanochip;

import com.gtlmodular.common.data.nanochip.NanochipModContext;
import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.IRecipeCapabilityHolder;
import com.gregtechceu.gtceu.api.capability.recipe.IRecipeHandler;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.MachineTrait;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.integration.ae2.slot.ExportOnlyAEFluidList;
import com.gregtechceu.gtceu.integration.ae2.slot.ExportOnlyAEFluidSlot;
import com.lowdragmc.lowdraglib.side.fluid.FluidStack;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.registries.ForgeRegistries;
import org.gtlcore.gtlcore.api.machine.trait.MEPart.IMEFilterIOPartMachine;
import org.gtlcore.gtlcore.api.recipe.RecipeResult;
import org.gtlcore.gtlcore.utils.MachineIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;

/**
 * NAC 模块的<b>运行耗材</b>（对应原版各模块的专属机制）。
 *
 * <p>原版各模块都有一份「必须持续供给」的东西，不供就停机；这里照抄那几条，并把
 * 「内槽 / 杂质 / 等级」这类需要专用 GUI 的细节折算成<b>连续耗液</b>：
 * <ul>
 *   <li><b>全板浸没装置</b>：原版内部 100 万 L 浸泡液槽、至少半满才能工作 —— 折算为持续消耗净化水；</li>
 *   <li><b>光学优化整理器</b>：原版需 2 份净化水并抽 X L/s 换加成 —— 折算为持续消耗净化水；</li>
 *   <li><b>超导线束分裂器</b>：原版 <b>1000 L/s 超导冷却液</b> —— 直接照抄（1:1 换算成 1000 mB/s）；</li>
 *   <li><b>加速生物协调器</b>：原版培养基 —— 直接照抄，并保留两条校准豁免：
 *       湿件满校准（T3）不消耗生长培养基，生物满校准（T3）<b>完全不消耗</b>。</li>
 * </ul>
 *
 * <p>用 GTM 定义级回调把无损预检与真实扣料分开，避免耗材不足时空转：
 * {@code beforeWorking} 只做无损预检（不够就 {@code false}，机器等待而不是空转），
 * {@code onWorking} 每 20 tick（= 1 游戏秒）真扣一次。
 */
public final class NanochipModuleUpkeep {

    private static final Logger LOGGER = LoggerFactory.getLogger("GTL-Modular");

    /** 扣费周期：20 tick = 每游戏秒（耗材速率都是按秒定义的）。 */
    public static final int DRAIN_INTERVAL_TICKS = 20;

    private NanochipModuleUpkeep() {
    }

    /** 注册入口：{@code .beforeWorking(NanochipModuleUpkeep::onBeforeWorking)}。 */
    public static boolean onBeforeWorking(IRecipeLogicMachine machine, GTRecipe recipe) {
        return check(machine, false);
    }

    /** 注册入口：{@code .onWorking(NanochipModuleUpkeep::onWorking)}。 */
    public static boolean onWorking(IRecipeLogicMachine machine) {
        return check(machine, true);
    }

    private static boolean check(IRecipeLogicMachine machine, boolean consume) {
        if (!(machine instanceof NanochipModuleMachine module)) {
            return true;
        }
        NanochipModuleKind kind = module.getModuleKind();
        if (!kind.hasUpkeep()) {
            return true;
        }
        if (isExempt(module, kind)) {
            return true;
        }
        Fluid fluid = fluidOf(kind.upkeepFluidId());
        if (fluid == null || fluid.isSame(Fluids.EMPTY)) {
            return true; // 流体不在整合包里 → 不拦（宁可不扣，也不让模块永远不动）
        }
        long amount = kind.upkeepMbPerSecond();
        if (amount <= 0L) {
            return true;
        }
        if (consume) {
            long offset = module.getOffsetTimer();
            if (offset % DRAIN_INTERVAL_TICKS != 0L) {
                return true;
            }
        }
        // ★ 2026-09-19（队长：「不给他提供净化水，他就弹这个，给他提供，他就把我材料吞了，然后也不做」）：
        //   病根 = **同一条链上两处口径不一致**：
        //     · 加成侧 `NanochipModuleMachine#highestPurifiedWaterGrade()` 用正则
        //       `grade_(\d+)_purified_water` 认**整族 1~16 级**；
        //     · 耗材闸这里却只认 `kind.upkeepFluidId()` 里写死的 **grade_1**（`fs.getFluid() == fluid`）。
        //   ⇒ 玩家供 2~16 级净化水时，闸认为"没有 1 级净化水" ⇒ beforeWorking 恒 false
        //     ⇒ 配方永远不开始（Jade 的「配方失败原因: 缺少耗材」就是这条），
        //     而 AE 早已把料交到我们这边 ⇒ 现象就是"吞了料又不做、取消也不退"。
        //   修法：净化水族的耗材按**族**判定（任一级都算够），并**扣实际供的那一种**
        //   （与加成侧同一份数据、同一套口径；其它耗材——PCB 冷却液 / 生长培养基——仍走精确匹配）。
        boolean family = isPurifiedWaterFamily(kind.upkeepFluidId());
        Fluid drain = pickAvailable(module, fluid, amount, family);
        if (drain == null) {
            notifyMissing(module, fluid, amount);
            return false;
        }
        if (consume) {
            MachineIO.inputFluid(module, FluidStack.create(drain, amount));
        }
        return true;
    }

    /**
     * <b>净化水 1~16 级 —— 每一级单独一行</b>（队长 2026-09-19 的明确要求：
     * 「你给他们每一级净化水都单独写一行代码，这样当检测到主机内有哪一级净化水时，就会自动进行判定」）。
     *
     * <p>为什么不用正则一次性匹配：正则"看起来对"，但一旦某个等级的 id 形状与预期不同、
     * 或某个等级在整合包里根本没注册，就会**静默漏判** —— 症状正好是"供水了却报缺料"。
     * 逐级列出来之后：命中哪一级是**可枚举、可打日志、可复核**的。
     *
     * <p>判定口径与加成侧（{@code NanochipModuleMachine#highestPurifiedWaterGrade()}）一致：
     * **任一级都算够**，且用**等级最高的那一级**。
     */
    public static final List<String> PURIFIED_WATER_TIERS = List.of(
            "gtceu:grade_1_purified_water",
            "gtceu:grade_2_purified_water",
            "gtceu:grade_3_purified_water",
            "gtceu:grade_4_purified_water",
            "gtceu:grade_5_purified_water",
            "gtceu:grade_6_purified_water",
            "gtceu:grade_7_purified_water",
            "gtceu:grade_8_purified_water",
            "gtceu:grade_9_purified_water",
            "gtceu:grade_10_purified_water",
            "gtceu:grade_11_purified_water",
            "gtceu:grade_12_purified_water",
            "gtceu:grade_13_purified_water",
            "gtceu:grade_14_purified_water",
            "gtceu:grade_15_purified_water",
            "gtceu:grade_16_purified_water");

    /** 该耗材 id 是不是"净化水族"（是则逐级判定，任一级都算够）。 */
    public static boolean isPurifiedWaterFamily(String id) {
        return id != null && PURIFIED_WATER_TIERS.contains(id);
    }

    /**
     * <b>公开给模块侧收口用</b>：该 id 是第几级净化水（1~16；0 = 不是）。
     *
     * <p>★ 2026-09-21（连接专项研究 C 节指出的"双真相"）：模块里 `highestPurifiedWaterGrade()`
     * 自带一份**正则**版族判据，与这里的**逐级白名单**并存 ⇒ 一旦某个等级的 id 形状偏离预期，
     * 两处会给出不同答案（"耗材闸放行、加成侧却认不到等级"）。这里提供唯一入口，供模块侧替换。
     */
    public static int purifiedWaterTierOf(String fluidId) {
        if (fluidId == null) {
            return 0;
        }
        int idx = PURIFIED_WATER_TIERS.indexOf(fluidId);
        return idx < 0 ? 0 : idx + 1;
    }

    /** 该流体属于第几级净化水（1~16；0 = 不是净化水）。逐级比对，命中即返回。 */
    private static int purifiedWaterTier(Fluid fluid) {
        ResourceLocation rid = ForgeRegistries.FLUIDS.getKey(fluid);
        if (rid == null) {
            return 0;
        }
        for (int i = 0; i < PURIFIED_WATER_TIERS.size(); i++) {
            if (PURIFIED_WATER_TIERS.get(i).equals(rid.toString())) {
                return i + 1;
            }
        }
        return 0;
    }

    /** 该流体是不是净化水族（跟加成侧认的是同一族）。 */
    private static boolean isPurifiedWater(Fluid fluid) {
        return purifiedWaterTier(fluid) > 0;
    }

    /** 最近一次检测到的净化水等级（1~16；0 = 没检测到）——供日志与界面显示。 */
    private static volatile int detectedTier;

    /** 最近一次检测到的净化水总存量（mB）。 */
    private static volatile long detectedAmount;

    /** 诊断用：最近一次检测到第几级净化水。 */
    public static int lastDetectedTier() {
        return detectedTier;
    }

    /** 诊断用：最近一次检测到的净化水总存量（mB）。 */
    public static long lastDetectedAmount() {
        return detectedAmount;
    }

    /** 存量 > 0 的最高等级（0 = 一级都没有）。 */
    private static int highestStockedTier(long[] perTier) {
        for (int tier = perTier.length - 1; tier >= 1; tier--) {
            if (perTier[tier] > 0L) {
                return tier;
            }
        }
        return 0;
    }

    /**
     * 找出"够 {@code amount} 的那种流体"并返回它（不够返回 null）。
     *
     * <p>{@code family = true}（净化水）时：**逐级**统计 1~16 级的存量（每级单独累加），
     * 总量够就放行，并返回**等级最高的那一级**（与加成侧"按最高等级算"同一口径）；
     * {@code family = false} 时只认 {@code fluid} 本身（PCB 冷却液 / 生长培养基，原版语义不变）。
     */
    private static Fluid pickAvailable(WorkableMultiblockMachine machine, Fluid fluid, long amount, boolean family) {
        List<FluidStack> stocked = scanInputFluids(machine);
        if (!family) {
            long exact = 0L;
            for (FluidStack fs : stocked) {
                if (fs.getFluid() == fluid) {
                    long add = fs.getAmount();
                    if (add > 0L) {
                        exact = saturatingAdd(exact, add);
                    }
                }
            }
            return exact >= amount ? fluid : null;
        }
        long[] perTier = new long[PURIFIED_WATER_TIERS.size() + 1];
        long total = 0L;
        for (FluidStack fs : stocked) {
            // ★★ 2026-09-21（0.1.26）**非正数一律忽略** + **饱和加法** —— 两个都必须有：
            //   队长的 AE 库存仓把"无上限"报成 `Long.MAX_VALUE`，两个槽一加就是
            //   `Long.MAX_VALUE + Long.MAX_VALUE = -2` ⇒ 总量变**负数** ⇒ 判定"没有" ⇒ 机器不动。
            //   实机日志原文（就是这条把病因钉死的）：
            //   `净化水逐级扫描 = 第 0 级 / 共 -2 mB（每秒需 100 mB）⇒ 判定不足`
            //   （`-2` 正是那个溢出的指纹；`第 0 级` 是因为每一级都被冲成负的 ⇒ 找不到正的级。）
            long add = fs.getAmount();
            if (add <= 0L) {
                continue;
            }
            int tier = purifiedWaterTier(fs.getFluid());
            if (tier > 0) {
                perTier[tier] = saturatingAdd(perTier[tier], add);
                total = saturatingAdd(total, add);
            }
        }
        int tier = highestStockedTier(perTier);
        detectedTier = tier;
        detectedAmount = total;
        if (total < amount) {
            return null;
        }
        return fluidOf(PURIFIED_WATER_TIERS.get(tier - 1));
    }

    /**
     * 校准豁免（原版第 3 档特效）：
     * 湿件满校准 → 加速生物协调器不消耗生长培养基；生物满校准 → 完全不消耗任何培养基。
     */
    private static boolean isExempt(NanochipModuleMachine module, NanochipModuleKind kind) {
        if (kind != NanochipModuleKind.BIOLOGICAL_COORDINATOR) {
            return false;
        }
        NanochipAssemblyComplexMachine host = module.getHost();
        if (host == null || !host.isFormed()) {
            return false;
        }
        NanochipCalibration calibration = host.getCalibration();
        return calibration.specialActive(NanochipCalibration.Family.WETWARE)
                || calibration.specialActive(NanochipCalibration.Family.BIOWARE);
    }

    /**
     * 饱和加法：{@code a + b} 溢出时夹到 {@link Long#MAX_VALUE}，**绝不回绕成负数**。
     *
     * <p>为什么必须用它（0.1.26 实机取证）：AE 库存类部件会把"无上限"的库存报成
     * {@code Long.MAX_VALUE}，两条一加就回绕成 {@code -2} —— 于是"水明明有 9.22 PB"，
     * 判定却得到"负数总量 ⇒ 不足"。任何来自第三方（AE / gtlcore）的**数量**都不可信，累加必须饱和。
     */
    private static long saturatingAdd(long a, long b) {
        if (b <= 0L) {
            return a;
        }
        return a > Long.MAX_VALUE - b ? Long.MAX_VALUE : a + b;
    }

    /** 无损预检：统计机器输入流体仓里该流体的总量。 */
    private static boolean hasFluid(WorkableMultiblockMachine machine, Fluid fluid, long amount) {
        long total = 0L;
        for (FluidStack fs : scanInputFluids(machine)) {
            if (fs.getFluid() == fluid) {
                long add = fs.getAmount();
                if (add > 0L) {
                    total = saturatingAdd(total, add);
                }
            }
        }
        return total >= amount;
    }

    /**
     * 扫描机器<b>可用</b>的全物流体（模块的多项机制都要读它：
     * 耗材预检、净化水等级、浸泡液杂质）。
     *
     * <h3>★ 2026-09-19（简单物流模式打通轮）：这里为什么还要读 gtlcore 的 ME 通道</h3>
     * <p><b>问题</b>：上表那条读取走的是 GTCEu 的 {@code IRecipeCapabilityHolder#getCapabilitiesProxy()}，
     * 而那张表只装 {@code IRecipeHandlerTrait}（标准总线 / 流体仓的 trait）。
     * gtlcore 的 ME 样板通道（样板总成 / 镜像仓 / ME 样板缓冲代理）走的是<b>另一套</b>
     * {@code IMERecipeHandlerTrait}（javap 现读：{@code NotifiableMERecipeHandlerTrait extends MachineTrait
     * implements IMERecipeHandlerTrait}，<b>不是</b> {@code IRecipeHandlerTrait}）
     * ⇒ 只靠 {@code getCapabilitiesProxy()} 永远看不到 ME 里推过来的流体。
     * <p>后果（只在简单模式下暴露）：14 台模块里有 <b>7 台自带耗材</b>
     * （全板浸没 100 mB/s 净化水、超导线束分裂器 1000 mB/s PCB 冷却液、四台优化组织器各 100 mB/s 净化水、
     * 加速生物调谐器 100 mB/s 生长培养基）—— 它们每 tick 走 {@link #check}，
     * 而 {@link #hasFluid} 读的就是本方法 ⇒ ME 供的液体<b>永远"看不见"</b> ⇒ 机器永远不动；
     * 同时四台组织器的「净化水等级加成」也恒为 0（{@code highestPurifiedWaterGrade()} 同样读本方法）。
     * <p><b>本方法的补法</b>：原路径<b>逐字保留</b>（原版模式走的就是它），
     * 简单模式下再<b>追加</b>一遍结构里 gtlcore ME 部件的流体通道内容。
     * 消费侧不需要改：{@link MachineIO#inputFluid} 内部走
     * {@code RecipeRunnerHelper.handleRecipeInputNocache(...)}（javap 现读），<b>本来就是 ME 感知的</b>，
     * 所以"预检看得见 + 真扣也扣得动"两边口径一致。
     *
     * @param machine 目标多方块（模块 / 复合体）
     */
    public static java.util.List<FluidStack> scanInputFluids(WorkableMultiblockMachine machine) {
        java.util.List<FluidStack> out = new java.util.ArrayList<>();
        // ⚠ 下面这几处「读不到就跳过」**不许写成 return**：简单模式下结构里可能**一台标准流体仓都没有**
        //   （只插了样板总成 / 镜像仓），那时 handlers == null，一旦 return 就把 ME 那一遍也吞掉了。
        IRecipeCapabilityHolder holder = holderOf(machine);
        List<IRecipeHandler<?>> handlers = null;
        if (holder != null) {
            try {
                handlers = holder.getCapabilitiesProxy().get(IO.IN, FluidRecipeCapability.CAP);
            } catch (RuntimeException e) {
                LOGGER.debug("[gtlmodular] NAC 模块读取流体输入仓失败", e);
            }
        }
        if (handlers != null) {
            for (IRecipeHandler<?> handler : handlers) {
                if (handler == null) {
                    continue;
                }
                for (Object content : handler.getContents()) {
                    if (content instanceof FluidStack fs && fs.getAmount() > 0L) {
                        out.add(fs);
                    }
                }
            }
        }
        // ★ 简单模式专属追加：ME 样板通道（样板总成 / 镜像仓 / 代理）里的流体。
        //   原版（NH）物流模式一行都不执行 ⇒ **那一条**的原版行为逐语义不变。
        //   ⚠️ 本 if 现在**只**盖住 appendMEFluidContents：下面那条「AE 库存类部件」的读取
        //      自 2026-11-15 起两种物流模式都走（理由与代价见紧邻它的注释）。
        if (NanochipModContext.config().isNanochipSimpleLogistics()) {
            appendMEFluidContents(machine, out);
        }
        // ★★ 2026-11-15【本轮任务 B′】「AE 库存类部件」的网络库存：**两种物流模式都读** ★★
        //   队长原话（症状）：「让超导线束分离器，允许使用 ME库存输入总成」。
        //   改前（2026-09-21 起）本行逐字在 `if (isNanochipSimpleLogistics()) { ... }` **之内**
        //   ⇒ 原版（NH）物流模式下这一遍一次都不跑，于是：
        //     · 该部件的流体只存在于 ExportOnlyAEFluidList（AE 网络视图）里、本机 tank 并不常驻；
        //     · 上面那条 appendMEFluidContents 只认 IMEFilterIOPartMachine，而 gtlcore 的
        //       「ME库存输入总成」（gtceu:me_dual_hatch_stock_part_machine / MEDualHatchStockPartMachine）
        //       **不实现**那个接口；
        //     ⇒ 两条路都读到 0 mB ⇒ 超导线束分离器（要 gtceu:super_coolant 1000 mB/s）恒"判定不足" ⇒ 机器不动。
        //   本次改法 = **最保守的一种**：把这一行**提到 if 之外**（两种模式各恰好调用一次）。
        //   为什么不用「新加一个 public static final 开关 + 只在原版模式下补一次调用」：
        //     那样要么把同一个调用写两遍（重复代码），要么把条件写成 `简单 || 开关` 的复合式
        //     （改动面更大）；而本改法在**简单模式下逐字节等价**（同一行、同一个相对次序：
        //     appendMEFluidContents 先、appendAEStockFluidContents 后）、只有一处 diff
        //     ⇒ 也就**不会出现"新增方法与既有方法同名"那类编译期风险**（本次两处改动都没有新增任何名字）。
        //   ⚠️ 这会放松的是**预检**，不是扣料：本方法只被 pickAvailable()（本文件）与
        //      NanochipModuleMachine#highestPurifiedWaterGrade() 这类"看得见多少"的读者使用
        //      （同文件的 hasFluid() 也读它，但全工程无调用点）；真正扣料仍在
        //      NanochipModuleUpkeep#check(..., consume=true) 里那一句
        //      MachineIO.inputFluid(module, ...) —— 它内部走 RecipeRunnerHelper.handleRecipeInputNocache(...)，
        //      本来就是 ME 感知的 ⇒ "预检看得见 + 真扣也扣得动"两边口径一致，
        //      本次改动**没有**动任何准入/扣料/并行/校准/白名单/窗口阈值判定。
        //   ⚠️ 代价（如实登记，未做限频/缓存）：原版物流模式下每个 scanInputFluids() 调用
        //      现在多出**一次** appendAEStockFluidContents：machine.getParts() + 每个部件一次
        //      part.self().getTraits() + 遍历 MachineTrait 找 ExportOnlyAEFluidList + 其槽数组循环。
        //      scanInputFluids() 的调用点是 pickAvailable()（每 tick 由 beforeWorking + onWorking
        //      各来一次）与 highestPurifiedWaterGrade()（waterStrength() / NanochipModuleMachine:2230）
        //      ⇒ 单台模块可达 **约 3~4 次/tick**，每次一遍**结构部件数**级遍历。
        //      量级说明：这一遍与简单模式下**本来就在跑**的 appendMEFluidContents 是同一形状、同一数量级，
        //      所以不是"新量级"，只是原版物流模式从 0 遍变成 1 遍；本方法内部对每个部件/每槽
        //      都 fail-safe 跳过（RuntimeException|LinkageError），不建表、不缓存、不分配集合。
        //      精确部件数/实测耗时【未取证】（需要运行期数据）。
        //   一行回退：把 `appendAEStockFluidContents(machine, out);` 这一行挪回上面 if 之内
        //   （逐字回到 2026-09-21 的形态）。
        appendAEStockFluidContents(machine, out);
        return out;
    }

    /**
     * 把结构里 gtlcore ME 部件的<b>可用流体</b>追加进 {@code out}。
     *
     * <p><b>只读、只追加；任何一步读不到就跳过该部件</b>（第三方 API，缺类/缺实现都不许把机器搞崩）。
     *
     * <p>取数口径与 gtlcore 自己的配方匹配<b>同一份数据</b>：
     * {@code IMERecipeHandler#getActiveAndUnCachedSlotsLimitContentsMap()} ——
     * javap 现读（{@code MEFluidHandlerBase} 偏移 43）：逐活跃槽返回
     * {@code InternalSlot.getLimitFluidStackInput()}（= 该槽真持有的流体 + 催化剂 + 虚拟供给）。
     * 镜像仓（{@code MEPatternBufferProxyRecipeHandler}）同方法<b>透传</b>给它绑定的样板总成
     * （javap 偏移 517），所以"插在模块里的镜像仓"与"插在模块里的样板总成"两条路都读得到。
     */
    private static void appendMEFluidContents(WorkableMultiblockMachine machine, List<FluidStack> out) {
        List<IMultiPart> parts;
        try {
            parts = machine.getParts();
        } catch (RuntimeException e) {
            LOGGER.debug("[gtlmodular] NAC 读取结构部件清单失败（跳过 ME 流体通道）", e);
            return;
        }
        if (parts == null) {
            return;
        }
        for (IMultiPart part : parts) {
            if (!(part instanceof IMEFilterIOPartMachine mePart)) {
                continue;
            }
            try {
                if (!mePart.getMETrait().getIO().support(IO.IN)) {
                    continue; // 未绑定 / 只出不进 ⇒ 它现在不给料
                }
                var traits = mePart.getMERecipeHandlerTraits();
                if (traits == null || traits.right() == null) {
                    continue;
                }
                Int2ObjectMap<List<FluidStack>> bySlot = traits.right().getActiveAndUnCachedSlotsLimitContentsMap();
                if (bySlot == null) {
                    continue;
                }
                for (List<FluidStack> slot : bySlot.values()) {
                    if (slot == null) {
                        continue;
                    }
                    for (FluidStack fs : slot) {
                        if (fs != null && fs.getAmount() > 0L) {
                            out.add(fs);
                        }
                    }
                }
            } catch (RuntimeException | LinkageError e) {
                LOGGER.debug("[gtlmodular] NAC 读取 ME 流体通道失败（该部件跳过）", e);
            }
        }
    }

    /**
     * ★【2026-09-21（0.1.25）队长：「让模块能读得到净化水」】把结构里
     * <b>AE 库存类部件</b>的<b>网络库存</b>也算进"可用流体"。
     *
     * <h3>为什么必须单开这一条（实测 + javap 取证）</h3>
     * 队长那台「ME库存输入总成」是 gtlcore 的 {@code gtceu:me_dual_hatch_stock_part_machine}
     * （{@code MEDualHatchStockPartMachine}，javap：{@code extends MEBusPartMachine}
     * {@code implements IDataStickInteractable, IModifiableSyncOffset}）——
     * <b>不实现</b> {@link IMEFilterIOPartMachine}，所以 {@link #appendMEFluidContents} 那条路看不到它。
     * 它的流体在 {@code public ExportOnlyAEFluidList aeFluidHandler}（= AE 网络视图）里，
     * 本机那个 {@code fluidTank} 里<b>不常驻</b>（按需从 AE 拉）。
     * ⇒ 两条老路都读到 <b>0 mB</b>，日志实况：
     * <pre>[gtlmodular] NAC 模块耗材检测 @…（奇异优化组织器）：净化水逐级扫描 = 第 0 级 / 共 0 mB（每秒需 100 mB）⇒ 判定不足</pre>
     * 而同一刻 ME 网络里有 8 级净化水 9.22 PB（队长截图）。
     *
     * <h3>取数口径（不 import gtlcore 的部件类，也不需要 AE2 的类）</h3>
     * 遍历结构里每个部件的 {@code MetaMachine.getTraits()}（javap 现读：{@code public List<MachineTrait> getTraits()}），
     * 凡是 {@link ExportOnlyAEFluidList} 就按 {@link ExportOnlyAEFluidList#getInventory()} 的槽读
     * {@link ExportOnlyAEFluidSlot#getFluid()}（LDLib 流体栈 ⇒ 拿到<b>流体身份</b>，净化水等级加成因此算得出来）
     * 与 {@link ExportOnlyAEFluidSlot#getFluidAmount()}（<b>AE 库存量</b>）。
     * <p>这样写有三个好处：① 不管那台部件是哪家的（gtlcore 的库存仓 / GTCEu 自己的 ME 库存件）都覆盖；
     * ② 不需要在编译期 import gtlcore 的部件类，也不碰 AE2 的类（本包已有的 AE2 依赖只用在样板那侧）；
     * ③ 拿到的是**流体身份**而不只是"有液体"，所以「等级越高加成越大」这条也能真正生效。
     *
     * <p>只读、只追加；任何一步读不到就跳过该部件/该槽（第三方 API，缺类/缺实现都不许把机器搞崩）。
     */
    private static void appendAEStockFluidContents(WorkableMultiblockMachine machine, List<FluidStack> out) {
        List<IMultiPart> parts;
        try {
            parts = machine.getParts();
        } catch (RuntimeException e) {
            LOGGER.debug("[gtlmodular] NAC 读取结构部件清单失败（跳过 AE 库存通道）", e);
            return;
        }
        if (parts == null) {
            return;
        }
        for (IMultiPart part : parts) {
            if (part == null || part.self() == null) {
                continue;
            }
            List<MachineTrait> traits;
            try {
                traits = part.self().getTraits();
            } catch (RuntimeException | LinkageError e) {
                continue;
            }
            if (traits == null) {
                continue;
            }
            for (MachineTrait trait : traits) {
                if (!(trait instanceof ExportOnlyAEFluidList stockList)) {
                    continue;
                }
                try {
                    ExportOnlyAEFluidSlot[] slots = stockList.getInventory();
                    if (slots == null) {
                        continue;
                    }
                    for (ExportOnlyAEFluidSlot slot : slots) {
                        if (slot == null) {
                            continue;
                        }
                        FluidStack fs = slot.getFluid();
                        long amount = slot.getFluidAmount();
                        if (fs == null || fs.getRawFluid() == null || amount <= 0L) {
                            continue;
                        }
                        // 用库存量而不是本地 tank 的量：AE 的库存可以远超 int，LDLib 的 FluidStack 收 long。
                        out.add(FluidStack.create(fs.getRawFluid(), amount, fs.getTag()));
                    }
                } catch (RuntimeException | LinkageError e) {
                    LOGGER.debug("[gtlmodular] NAC 读取 AE 库存流体失败（该槽跳过）", e);
                }
            }
        }
    }

    /** "逐级判定"的日志限流（tick；{@value} = 5 秒）。哨兵**不能**用 Long.MIN_VALUE（会溢出成负 ⇒ 永不打印）。 */
    private static long lastDetectLogTick = -10_000L;

    /**
     * 点名缺什么（中文流体名 + 每秒需要量）。
     *
     * <p>★ 2026-09-19 修：净化水族**不再报"缺 1 级净化水"** —— 那是 `kind.upkeepFluidId()` 里的写死 id
     * 带来的假象（队长实测：供 2 级水时照样弹"缺少一级净化水"）。现在报的是"净化水（1~16 级任一级）"，
     * 并把**逐级扫描的结果**（检测到第几级、共多少 mB）打进日志，便于对账。
     */
    private static void notifyMissing(NanochipModuleMachine module, Fluid fluid, long amount) {
        try {
            boolean water = isPurifiedWaterFamily(module.getModuleKind().upkeepFluidId());
            Component fluidName = water
                    ? Component.translatable("gtlmodular.nanochip.module.upkeep.water_any_tier")
                    : FluidStack.create(fluid, 1).getDisplayName();
            RecipeResult.of(module, RecipeResult.fail(
                    Component.translatable("gtlmodular.nanochip.module.upkeep.missing",
                            module.getModuleKind().nameZh(), fluidName, amount)));
            if (water) {
                net.minecraft.world.level.Level lv = module.getLevel();
                long now = lv == null ? 0L : lv.getGameTime();
                if (now - lastDetectLogTick >= 100L) {
                    lastDetectLogTick = now;
                    LOGGER.info("[gtlmodular] NAC 模块耗材检测 @{}（{}）：净化水逐级扫描 = 第 {} 级 / 共 {} mB"
                                    + "（每秒需 {} mB）⇒ 判定不足",
                            module.getPos(), module.getModuleKind().nameZh(), detectedTier, detectedAmount, amount);
                }
            }
        } catch (RuntimeException e) {
            LOGGER.debug("[gtlmodular] NAC 模块耗材提示发送失败", e);
        }
    }

    /** 运行时机器对象实现了 IRecipeCapabilityHolder；桥接转换以绕开静态类型检查。 */
    @SuppressWarnings("unchecked")
    private static IRecipeCapabilityHolder holderOf(WorkableMultiblockMachine machine) {
        try {
            return (IRecipeCapabilityHolder) (Object) machine;
        } catch (ClassCastException e) {
            LOGGER.error("[gtlmodular] NAC 模块未实现 IRecipeCapabilityHolder", e);
            return null;
        }
    }

    // ---------- 流体 id → Fluid（带缓存；无效 id 的报错只打一次） ----------

    private static final Map<String, Fluid> FLUID_CACHE = new ConcurrentHashMap<>();
    private static final Set<String> WARNED_IDS = ConcurrentHashMap.newKeySet();

    /** 供物品说明显示流体名用。 */
    public static Component fluidDisplayName(String fluidId, String fallback) {
        Fluid fluid = fluidOf(fluidId);
        if (fluid != null && !fluid.isSame(Fluids.EMPTY)) {
            try {
                return FluidStack.create(fluid, 1).getDisplayName();
            } catch (RuntimeException ignored) {
                // fall through
            }
        }
        return Component.literal(fallback);
    }

    private static Fluid fluidOf(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        Fluid cached = FLUID_CACHE.get(id);
        if (cached != null) {
            return cached;
        }
        Fluid fluid = ForgeRegistries.FLUIDS.getValue(new ResourceLocation(id));
        if (fluid == null) {
            if (WARNED_IDS.add(id)) {
                LOGGER.warn("[gtlmodular] NAC 模块耗材流体不存在: {}（本条只提示一次，该模块暂不扣料）", id);
            }
            return null;
        }
        FLUID_CACHE.put(id, fluid);
        return fluid;
    }

    /** 判断两个 id 是否指向同一种流体（界面显示用）。 */
    public static boolean sameFluid(String a, String b) {
        return Objects.equals(a, b);
    }
}
