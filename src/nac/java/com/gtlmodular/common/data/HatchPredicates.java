package com.gtlmodular.common.data;

import com.gtlmodular.common.data.nanochip.NanochipModContext;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.pattern.MultiblockState;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gtlmodular.common.machine.nanochip.NanochipModuleKind;
import com.lowdragmc.lowdraglib.utils.BlockInfo;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;

/**
 * 仓室判定（低开销版）—— 语义与 {@link com.gregtechceu.gtceu.api.pattern.Predicates#abilities} 等价，
 * 但<b>预览候选每种能力只实例化一台"标准仓"</b>。
 *
 * <h3>为什么需要它（现场验证过的 40 秒卡死）</h3>
 * {@code Predicates.abilities(...)} 内部其实是
 * {@code Arrays.stream(abilities).map(PartAbility::getAllBlocks).flatMap(...) → blocks(Block[])}，
 * 而 {@code blocks(...)} 在<b>生成结构预览候选</b>时会为每个方块调
 * {@code BlockInfo.fromBlock} —— 这一条已经<b>逐指令核实</b>：
 * <pre>
 * // LDLib BlockInfo.fromBlock(Block)            → fromBlockState(block.defaultBlockState())
 * // LDLib BlockInfo.fromBlockState(BlockState)  偏移 0..40：
 * //   0: aload_0
 * //   1: invokevirtual BlockState.getBlock
 * //   4: instanceof    net/minecraft/world/level/block/EntityBlock
 * //   7: ifeq 41
 * //  10..21: checkcast EntityBlock → BlockEntity.newBlockEntity(BlockPos.ZERO, state)   ← ★ 真的 new 方块实体
 * //  31: new BlockInfo(state, true)
 * //  41: catch(Exception) → new BlockInfo(state)     （异常表 0..40 → 44）
 * </pre>
 * 也就是说「造预览候选」= <b>把该能力下的机器一台台真的 new 出来</b>。
 * {@code PartAbility.IMPORT_ITEMS} 的方块表里包含 <b>ME 样板总成</b>（gtlcore / gtladditions 的
 * 超级样板总成也在其中），而它们的构造是 O(槽位²) 级别的重活：实测在 Server thread 里
 * 单个 tick 卡了 40 秒（ModernFix 看门狗转储的栈：{@code MESuperPatternBufferPartMachine.<init>} →
 * {@code MEPatternBufferPartMachine.<init>:186} 的 {@code Arrays.setAll} + 每格 {@code Stream.generate().toList()}）。
 *
 * <h3>★ 2026-09-19 修一：候选必须<b>确定性</b>，不能取 HashSet 的第一个</h3>
 * 旧实现写的是 {@code for (Block block : ability.getAllBlocks()) { preview.add(...); break; }}，
 * 注释里假设"第一个 = 最便宜的那台"。这个假设<b>是错的</b>，已按字节码否掉：
 * <pre>
 * // PartAbility 字段：private Int2ObjectMap&lt;Set&lt;Block&gt;&gt; registry;   （Int2ObjectOpenHashMap）
 * // PartAbility.register(int, Block) 偏移 4..19：computeIfAbsent(tier, lambda$register$1)
 * //   lambda$register$1(int) → new java/util/HashSet       ← ★ 每档一个普通 HashSet
 * // PartAbility.getAllBlocks() → Suppliers.memoize(lambda$new$0) 的 get()
 * //   lambda$new$0 偏移 0..29：registry.values().stream().flatMap(Set::stream).toList()
 * </pre>
 * {@code Block} 没有覆写 {@code hashCode} ⇒ 迭代顺序是按<b>身份哈希</b>决定的，
 * <b>每次开游戏都可能不一样</b>，与"档位/造价"毫无关系。
 * ⇒ 极端情况下"第一个"正好是 ME 样板总成 ⇒ 预览构建直接卡 40 秒；而且玩家每次启动看到的
 * 预览仓都不一样（同一个存档、同一个结构，两台不同的仓）—— 这本身就是缺陷。
 * 现在改成 {@link #representative(PartAbility)}：<b>先按白名单找 GTCEu 自带的标准档位仓
 * （ULV→MAX 逐档试，形如 {@code gtceu:ulv_input_bus} / {@code gtceu:lv_energy_input_hatch}），
 * 找不到再按"命名空间 gtceu 优先 + 注册 id 字典序"确定性回落</b>，并跳过已知的重机器（ME 样板总成等）。
 *
 * <h3>★ 2026-09-19 修二：为什么"只留一个候选"会让队长误以为<b>不允许放</b>输入输出仓</h3>
 * GTCEu 的预览取值在 {@code BlockPattern.getPreview(int[])} 里（javap -c 逐指令已核），它读的是
 * {@code SimplePredicate.previewCount}（默认 <b>-1</b>），分四趟挑每格画什么：
 * <ol>
 *   <li>先扫 {@code limited}，但<b>只认 {@code minLayerCount > 0}</b> 的（偏移 161-166）⇒ 我们不设层级下限，全跳过；</li>
 *   <li>再扫 {@code limited}：{@code minCount == -1 && previewCount == -1} 直接跳过（偏移 426-444）；
 *       否则先看 {@code previewCount}：配额没用完才画（偏移 447-550）；配额用完且 {@code minCount <= 0}
 *       ⇒ 也跳过（偏移 553-558）。<b>我们那些 {@code setMinGlobalLimited(0)} 的仓谓词 minCount=0、
 *       previewCount=-1 ⇒ 偏移 274 的 {@code getOrDefault(0) >= -1} 成立 ⇒ 转到 553 ⇒ {@code minCount<=0} ⇒ 全部跳过</b>；</li>
 *   <li>再扫 {@code common} 里 {@code previewCount > 0} 的（偏移 710-715）—— 旧实现没有这种谓词，空趟；</li>
 *   <li>最后扫 {@code common} 里 {@code previewCount == -1} 的（偏移 867-873）—— <b>这一趟命中的是
 *       {@code hatchOrCasing()} 里那台"网格接口机壳"</b>（{@code Predicates.blocks(...)} 的
 *       previewCount 也是 -1）。</li>
 * </ol>
 * ⇒ 结论：<b>修改前，模块 {@code V} 位的 39 格预览里画的全是"纳米芯片网格接口机械方块"，
 * 一台仓都不出现</b>；主机 {@code H} 位则因为"真空传送带仓 ≥1"（minCount=1）而全画成真空输入仓。
 * 玩家在 JEI/结构预览里逐格点名看，确实只能看到机壳 ⇒ 自然得出"竟然不允许放输入输出仓"。
 *
 * <p>而 GTCEu 官方给出的"让预览同时展出多种仓"的写法就是 <b>{@code setPreviewCount(n)}</b>
 * —— GTCEu 自己的多方块登记里就有实例（javap，{@code GTMachines.lambda$registerLargeBoiler$225}
 * 偏移 30-50：{@code abilities(IMPORT_FLUIDS).setMinGlobalLimited(1).setPreviewCount(1)}）。
 * 所以 {@link #previewAbilities(PartAbility...)} 追加的那批谓词带 {@code previewCount = 1}：
 * 每台仓各占<b>一格</b>预览，依次展出「输入总线 → 输出总线 → 输入仓 → 输出仓 → 能源仓 → 激光源仓 →
 * 维护仓」，剩下的格子才回落成机壳。
 *
 * <h3>纪律：这个类<b>只动预览候选</b>，一格匹配都不许改</h3>
 * <ul>
 *   <li>{@link #lowCostAbilities} / {@link #previewAbilities} 的判定体<b>是同一个方法</b>
 *       （{@link #applies(MultiblockState, PartAbility[])}）⇒ 任何带该能力的仓都仍然算数；</li>
 *   <li>{@link #previewAbilities} 追加的谓词走 {@code common} 列表（{@code minCount = -1}、
 *       {@code maxCount = -1}）⇒ <b>不进 {@code limited}、不参与全局配额</b>，
 *       {@code MultiblockState.getGlobalCount()} 的键（SimplePredicate 实例）也不受影响；</li>
 *   <li>{@code _census/check_hatch_abilities.js} 钉住上面两条，并带负向验证。</li>
 * </ul>
 */
public final class HatchPredicates {

    private HatchPredicates() {
    }

    /**
     * 档位前缀（低 → 高）。GTCEu 的分档机器 id = {@code gtceu:<档位小写>_<后缀>}
     * （证据：{@code GTMachines.<clinit>} 偏移 2351/2373/2395/2423… 用
     * {@code registerTieredMachines("input_bus", …, ALL_TIERS)} 注册，档位前缀即 {@code GTValues.VN[tier]}
     * 的小写形式；本机存档普查里实际出现的 id 就是 {@code gtceu:ulv_input_bus} / {@code gtceu:lv_input_hatch} 这一族）。
     */
    private static final String[] TIER_PREFIXES = {
            "ulv", "lv", "mv", "hv", "ev", "iv", "luv", "zpm", "uv", "uhv", "uev", "uiv", "uxv", "opv", "max"
    };

    /**
     * 预览代表性仓的"标准后缀"白名单：<b>每个能力只在预览里代表它自己那一台普通仓</b>。
     *
     * <p>取值口径（全部来自 GTCEu 自己的注册字节码 / 本机运行时注册表普查）：
     * <ul>
     *   <li>物品总线：{@code input_bus} / {@code output_bus}
     *       （{@code GTMachines.<clinit>} 偏移 2351、2373；{@code lambda$static$72} 里
     *       {@code abilities(IMPORT_ITEMS)}、导出总线那一支 {@code abilities(EXPORT_ITEMS)}）；</li>
     *   <li>流体仓：{@code input_hatch} / {@code output_hatch}
     *       （偏移 2395、2528；{@code registerFluidHatches(..., IMPORT_FLUIDS/IMPORT_FLUIDS_1X, ...)}、
     *       导出支 {@code EXPORT_FLUIDS/EXPORT_FLUIDS_1X}）；</li>
     *   <li>能源仓：{@code energy_input_hatch}（{@code PartAbility.INPUT_ENERGY}）；</li>
     *   <li>激光源仓：{@code 256a_laser_source_hatch}（最小安培档；GTCEu 的激光仓 id 形如
     *       {@code gtceu:iv_256a_laser_source_hatch}，<b>没有</b>裸 {@code lv_laser_hatch} 这种 id）；</li>
     *   <li>维护仓：{@code maintenance_hatch}（GTCEu 自带，不分档）；</li>
     *   <li>并行仓：{@code parallel_hatch}（本包 id 形如 {@code gtceu:uv_parallel_hatch}）。</li>
     * </ul>
     * 白名单里没有的能力（addon 自造能力等）走 {@link #representative(PartAbility)} 的确定性回落。
     */
    private static String[] standardSuffixes(PartAbility ability) {
        if (ability == PartAbility.IMPORT_ITEMS) {
            return new String[]{"input_bus"};
        }
        if (ability == PartAbility.EXPORT_ITEMS) {
            return new String[]{"output_bus"};
        }
        if (ability == PartAbility.IMPORT_FLUIDS) {
            return new String[]{"input_hatch"};
        }
        if (ability == PartAbility.EXPORT_FLUIDS) {
            return new String[]{"output_hatch"};
        }
        if (ability == PartAbility.INPUT_ENERGY) {
            return new String[]{"energy_input_hatch"};
        }
        if (ability == PartAbility.INPUT_LASER) {
            return new String[]{"256a_laser_source_hatch", "1024a_laser_source_hatch"};
        }
        if (ability == PartAbility.MAINTENANCE) {
            return new String[]{"maintenance_hatch"};
        }
        if (ability == PartAbility.PARALLEL_HATCH) {
            return new String[]{"parallel_hatch"};
        }
        return new String[0];
    }

    /**
     * 回落用的"重机器"关键词：命中这些词的方块<b>不许当预览代表</b>。
     *
     * <p>理由：这些机器的构造是 O(槽位²) 级重活（40 秒卡死的元凶就是它们），
     * 而且玩家在结构图里也认不出它们。**注意**：这里只影响"预览画哪一台"，
     * 不影响"哪些仓能放"（任何带该能力的仓依旧算数）。
     */
    private static final String[] HEAVY_HINTS = {
            "pattern_buffer", "super_pattern", "stocking", "proxy", "assembly", "window", "core"
    };

    /**
     * 该能力的预览代表方块（<b>确定性</b>）。
     *
     * <p>顺序：① 白名单 × 档位（ULV→MAX）→ ② 命名空间 {@code gtceu} 优先、其余按注册 id 字典序，
     * 且跳过 {@link #HEAVY_HINTS} 命中的重机器 → ③ 实在没有就取字典序第一台（宁可画一台怪仓，
     * 也不返回空表让玩家看到"这格什么都不能放"）。
     */
    private static Block representative(PartAbility ability) {
        List<Block> blocks = new ArrayList<>(ability.getAllBlocks());
        if (blocks.isEmpty()) {
            return null;
        }
        String[] suffixes = standardSuffixes(ability);
        for (String suffix : suffixes) {
            for (String tier : TIER_PREFIXES) {
                String wanted = "gtceu:" + tier + "_" + suffix;
                for (Block block : blocks) {
                    if (idOf(block).equals(wanted)) {
                        return block;
                    }
                }
            }
            // 维护仓这类不分档的：直接试 "gtceu:<后缀>"
            String plain = "gtceu:" + suffix;
            for (Block block : blocks) {
                if (idOf(block).equals(plain)) {
                    return block;
                }
            }
        }
        // ② 确定性回落：命名空间优先级 → 注册 id 字典序；重的排最后
        return blocks.stream().min((a, b) -> {
            int c = Integer.compare(rank(a), rank(b));
            return c != 0 ? c : idOf(a).compareTo(idOf(b));
        }).orElse(blocks.get(0));
    }

    /** 排序键：小的优先。gtceu 自带 = 0；含重机器关键词 = 2；其它 addon = 1。 */
    private static int rank(Block block) {
        String id = idOf(block);
        for (String hint : HEAVY_HINTS) {
            if (id.contains(hint)) {
                return 2;
            }
        }
        return id.startsWith("gtceu:") ? 0 : 1;
    }

    private static String idOf(Block block) {
        ResourceLocation key = BuiltInRegistries.BLOCK.getKey(block);
        return key == null ? "" : key.toString();
    }

    // ------------------------------------------------------------------
    // ★ 2026-10-02：结构预览「只留模块主机」白名单过滤
    // ------------------------------------------------------------------

    /**
     * <b>结构预览「只列模块主机」开关</b>（队长 2026-10-02：「这一行应该只显示他的那些模块主机，
     * 而不是显示那些输入仓、输出仓、能源靶仓这些的」）。
     *
     * <ul>
     *   <li>{@code true}（默认）＝ {@link #previewAbilities} 注入结构预览的候选方块
     *       <b>换成"本模组的 14 台 NAC 模块主机"</b>（见 {@code previewOfModuleHosts(...)}）；
     *       输入/输出总线、输入/输出仓、能源仓、激光仓、维护仓等 <b>I/O 类零件一律不进预览</b>；</li>
     *   <li>{@code false} ＝ 逐字回到 2026-09-19 的行为（每种仓各占一格预览）。</li>
     * </ul>
     *
     * <p><b>★ 方案纠正（第一版为什么不对）</b>：第一版写成"把 {@code previewOf(...)} 的 7 台 I/O 仓
     * 按白名单过滤掉" —— 但那 7 台里<b>本来就没有模块方块</b>（候选表是"每种仓一台"），
     * 于是过滤结果恒为空、只能回落成"网格接口机壳占位"，那一列还是<b>看不到模块主机</b> ⇒ 不符合需求。
     * 现在改成<b>正向构造</b>：候选表直接由 14 台模块方块组成（"每台一格"的配额语义与原"每仓一格"完全一致）。
     *
     * <p><b>为什么改这里就够</b>：全仓只有这一处把零件塞进结构预览
     * （唯一调用点 {@code NanochipComplexPattern.hatchOrCasing} → 主机 {@code H} 位与模块 {@code V} 位），
     * 而预览面板那一列图标就是上游 {@code PatternPreviewWidget} 的"零件/材料"表
     * （{@code MBPattern.parts}，由 {@code gatherBlockDrops(Map<BlockPos,BlockInfo>)} 从**预览网格**聚合；
     * 字节码字符串表见 {@code _census/_probe_nacmat_evidence/strings_gtceu-1.20.1-1.4.4.txt:49150-49327}）。
     * ⇒ 喂进"预览网格"的是什么方块，那一列就列出什么方块。
     */
    public static final boolean PREVIEW_MODULE_HOSTS_ONLY = true;

    /**
     * ★ 2026-10（队长原话：「为什么<b>纳米芯片组装矩阵</b>的多方块预览是<b>纳米芯片组装复合体</b>的预览」）
     * <b>把「模块主机候选表」限定在主机自己的图案上</b> —— 模块自己的图案（模块机 {@code V} 仓位、
     * 塔身 {@code HATCH} 位）的预览候选表回到 {@link #previewOf}
     * （= 2026-09-19 的原意：「每种 I/O 能力各占一格标准仓」），<b>不再</b>画成 14 台 NAC 模块主机方块。
     *
     * <h3>根因：预览块集合是「共享」的</h3>
     * {@code NanochipComplexPattern.hatchOrCasing(...)} 的返回值里追加了
     * {@link #previewAbilities}({@link #NAC_HATCH_ABILITIES})，而 {@code hatchOrCasing} 被<b>三处</b>共用：
     * 主机 {@code H} 位、<b>模块 {@code V} 位</b>（39 格）、<b>模块塔身 {@code HATCH} 位</b>
     * （调用点见 {@code NanochipComplexPattern.build()} / {@code buildModule()} / {@code predicateFor()}）。
     * 2026-10-02 把那张候选表换成「14 台 NAC 模块主机」之后（见 {@link #PREVIEW_MODULE_HOSTS_ONLY}），
     * <b>模块自己的预览页</b>里的仓位格也被画成了模块控制器方块
     * （= 复合体 12 个基座上那套方块；模块控制器与主机控制器共用同一张贴图
     * {@code gtlmodular:block/casings/nanochip_mesh_interface_casing}，见
     * {@code NanochipMachines.MODULE_CASING_TEX} 的注释）⇒ 玩家在模块的预览里看到「全是复合体那套
     * 模块主机」。<b>这个共享点就是本现象的唯一根因</b>（模块的 {@code .pattern(...)} 本身没有指错，
     * 见 {@code NanochipMachines.registerModules()} 的 {@code NanochipComplexPattern::buildModule}）。
     *
     * <ul>
     *   <li>{@code true}（默认 = 修复后行为）＝ 模块自己的图案只用「I/O 代表仓」预览
     *       （{@link #previewAbilitiesForModule} → {@link #previewOf}）；</li>
     *   <li>{@code false} ＝ 逐字回到 2026-10-02 之后的现状（模块 {@code V}/塔身仓位也画 14 台模块主机）。</li>
     * </ul>
     *
     * <p><b>主机 {@code H} 位一个字节不动</b>：它仍走 {@link #previewAbilities}
     * （= 队长 2026-10-02 点名要的那一列「只显示他的那些模块主机」），本次只切模块侧。
     * <p><b>本开关只影响预览候选表</b>：判定体 {@link #applies}、{@code previewCount}、
     * 谓词归属（{@code common}）与 {@link #lowCostAbilities}（自动搭建/点格看候选读的那张表）全都不变。
     */
    public static final boolean MODULE_SIDE_PREVIEW_IO_ONLY = true;

    /**
     * 白名单判定：<b>只认本模组的 NAC 模块主机方块</b>（id 形如 {@code gtlmodular:nanochip_assembly_matrix}）。
     *
     * <p>判据 = ① 命名空间是我们的 ② 路径能命中 {@link NanochipModuleKind#byMachineId(String)}
     * （14 台模块主机，见该枚举）。<b>不按名字里的 {@code bus/hatch/input/output/energy} 等关键词做黑名单</b>
     * —— 黑名单会漏掉 addon 自造的各种仓，而且"名字里带 hatch 的不许出现"这条规则本身没有语义依据。
     *
     * <p>因此这个白名单<b>不会误杀任何模块主机</b>：模块方块的注册 id 就是
     * {@code gtlmodular:<machineId>}（见 {@code NanochipMachines.registerModules()}，
     * `NanochipMachines.java:540` `.multiblock(kind.machineId(), …)`）。
     */
    public static boolean isPreviewModuleHost(Block block) {
        if (block == null) {
            return false;
        }
        ResourceLocation key = BuiltInRegistries.BLOCK.getKey(block);
        if (key == null || !NanochipModContext.modId().equals(key.getNamespace())) {
            return false;
        }
        return NanochipModuleKind.byMachineId(key.getPath()) != null;
    }

    /**
     * 取某个模块种类的<b>方块</b>（按注册 id {@code gtlmodular:<machineId>} 查；
     * 未注册 / 查表异常一律返回 {@code null}，绝不外抛）。
     *
     * <p>为什么走注册表而不是 {@code NanochipMachines.NANOCHIP_MODULES}：本方法只在**预览构建时**
     * 被求值（注册早已结束），按 id 查表既与 {@link #isPreviewModuleHost(Block)} 同一判据、又不引入
     * 本类对机器注册类的静态依赖（避免"预览候选"这条纯客户端的路子去碰注册顺序）。
     */
    private static Block moduleHostBlock(NanochipModuleKind kind) {
        if (kind == null) {
            return null;
        }
        try {
            Block block = BuiltInRegistries.BLOCK.get(NanochipModContext.registryId(kind.machineId()));
            return block == null || block == Blocks.AIR ? null : block;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 兜底占位方块：网格接口机壳（我们自己的方块，<b>不是</b> I/O 零件）
     * ＝ 2026-09-19 之前那几个格子的预览回落值。
     *
     * <p>只在整个白名单<b>一台模块都取不到</b>时才用（正常路径不会走到：
     * 14 台模块方块在预览构建时必然已注册）。为什么不用"空数组"兜底：
     * 上游 {@code BlockPattern.getPreview} 对长度 0 的候选取 {@code BlockInfo.EMPTY}（= 空气，
     * 见 {@code _census/multiblock_preview_diagnosis.md} §3.2 的偏移 1181-1189 原文），
     * 那会让 {@code H}/{@code V} 位在预览里变成一个个<b>空洞</b>。
     *
     * <p>拿不到机壳（注册表异常）时返回 {@code null}，由调用方回落到原候选表 —— <b>全程不抛异常</b>。
     */
    private static BlockInfo previewFallback() {
        try {
            Block mesh = NanochipBlocks.MESH_INTERFACE_CASING.get();
            return mesh == null ? null : BlockInfo.fromBlockState(mesh.defaultBlockState());
        } catch (Throwable t) {
            // 注册未就绪 / 方块实例化失败 ⇒ 退回原候选表（宁可预览照旧，也绝不打断预览构建）
            return null;
        }
    }

    /**
     * <b>预览候选表（"只列模块主机"版）</b> —— 只给 {@link #previewAbilities} 用。
     *
     * <h3>★ 2026-10-02 方案 A：正向构造模块主机候选表</h3>
     * <b>不再</b>从 {@code abilityList}（= {@code NAC_HATCH_ABILITIES} 那 7 类 I/O 仓）里过滤，
     * 而是<b>用 14 台 NAC 模块主机方块自己组成候选表</b>（{@code NanochipModuleKind.values()} 逐台按
     * {@code gtlmodular:<machineId>} 查方块，顺序 = 枚举序 ⇒ 确定、不依赖 HashSet 迭代顺序）：
     * <ul>
     *   <li>"每台一格"的配额语义与原来"每仓一格"<b>完全一致</b>（同一个 {@link #previewAbilities}
     *       实例、同样 {@code setPreviewCount(1)}，只是候选表内容换了）⇒ 上游预览的第 3 趟会把
     *       14 台模块主机各画一格，并因此进入"零件/材料"表（= 玩家看到的那一列）；</li>
     *   <li>I/O 类零件（输入/输出总线、输入/输出仓、能源仓、激光仓、维护仓）<b>一台都不再进预览</b>。</li>
     * </ul>
     *
     * <h3>为什么与 {@link #lowCostAbilities} 用的 {@link #previewOf} 分开</h3>
     * {@code lowCostAbilities} 的候选同时是 ①"点某一格看候选方块"的数据源、②GTCEu
     * {@code BlockPattern.autoBuild} 挑方块时读的 {@code candidates[0]}（{@code autoBuild} 在 {@code limited}
     * 分支上直接取下标 0，见 {@code NanochipComplexPattern.java:1393-1401} 的字节码注释）。
     * 把那里的候选换掉会让"自动搭建填机壳"与"这格能放什么"的提示一起变形
     * ⇒ <b>那张表一个字都不动</b>，被换掉的只有"纯预览用"的 {@link #previewAbilities}。
     *
     * <h3>成本</h3>
     * 求值时机 = 每次构建预览（上游按定义缓存 {@code PatternPreviewWidget.CACHE}）。
     * 每台模块走一次 {@link BlockInfo#fromBlock(Block)}（会 new 一次该机器的方块实体），
     * 共 <b>14 次</b>，且<b>只遍历一遍枚举</b>、不注册任何 tick 钩子 ⇒ 无每 tick 开销。
     * （对比：旧实现 7 次；历史上 40 秒卡死来自 ME 样板总成的 O(槽位²) 构造，本表全是本模组自己的机器，不含它们。）
     *
     * @param abilityList 开关关闭时回落到旧行为用的能力表（默认路径<b>不使用</b>它）
     */
    private static BlockInfo[] previewOfModuleHosts(PartAbility... abilityList) {
        if (!PREVIEW_MODULE_HOSTS_ONLY) {
            return previewOf(abilityList); // 开关关闭 ⇒ 逐字回到 2026-09-19 的"每种仓各占一格"
        }
        List<BlockInfo> hosts = new ArrayList<>(NanochipModuleKind.values().length);
        for (NanochipModuleKind kind : NanochipModuleKind.values()) {
            Block block = moduleHostBlock(kind);
            if (isPreviewModuleHost(block)) { // 白名单复核：同一条判据，双保险（不满足就跳过，绝不外抛）
                hosts.add(BlockInfo.fromBlock(block));
            }
        }
        if (!hosts.isEmpty()) {
            return hosts.toArray(new BlockInfo[0]);
        }
        // 一台模块都取不到（只可能是注册异常）⇒ 先试机壳占位（避免上游把空候选画成空气空洞），
        // 再不行才回落到旧候选表 —— 全程不抛异常。
        BlockInfo fallback = previewFallback();
        return fallback == null ? previewOf(abilityList) : new BlockInfo[]{fallback};
    }

    /**
     * 预览候选表：<b>每种能力恰好一台</b>（{@link #representative(PartAbility)}）。
     *
     * <p>上限口径：返回值长度 {@code <= abilityList.length}；调用方给几个能力就最多几台机器。
     * 本项目所有调用点给的能力数 ≤ 7 ⇒ 预览最多实例化 <b>7</b> 台机器（旧写法是"该能力下每一台"，
     * {@code IMPORT_ITEMS} 一档就有几十台，其中含 ME 样板总成）。
     */
    private static BlockInfo[] previewOf(PartAbility... abilityList) {
        List<BlockInfo> preview = new ArrayList<>(abilityList.length);
        for (PartAbility ability : abilityList) {
            Block block = representative(ability);
            if (block != null) {
                preview.add(BlockInfo.fromBlock(block)); // 每能力 1 台 ⇒ 实例化成本有界
            }
        }
        return preview.toArray(new BlockInfo[0]);
    }

    /**
     * <b>唯一的能力判定体</b> —— {@link #lowCostAbilities} 与 {@link #previewAbilities} 共用它。
     *
     * <p>与 {@code Predicates.abilities(...)} 的语义逐字等价：{@code PartAbility.isApplicable(Block)}
     * 的字节码是 {@code getAllBlocks().contains(block)}（javap -c 偏移 0..10），
     * 所以"任何带该能力的仓"都命中，与它的档位/来自哪个 addon 无关。
     */
    private static boolean applies(MultiblockState state, PartAbility[] abilityList) {
        Block block = state.getBlockState().getBlock();
        for (PartAbility ability : abilityList) {
            if (ability.isApplicable(block)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 等价于 {@code Predicates.abilities(abilities...)}，但预览候选每种能力只取
     * {@link #representative(PartAbility)} 那一台（确定性、且一定是普通仓）。
     *
     * <p>提示：如果需要更少/更多预览，可在返回结果上继续 {@code .setPreviewCount(n)}。
     */
    public static TraceabilityPredicate lowCostAbilities(PartAbility... abilityList) {
        return new TraceabilityPredicate(
                (MultiblockState state) -> applies(state, abilityList),
                () -> previewOf(abilityList));
    }

    /**
     * ★ 2026-09-19：<b>纯预览用</b>的能力谓词 —— 匹配与 {@link #lowCostAbilities} <b>同一个判定体</b>，
     * 只是带上 {@code previewCount = 1}，让 GTCEu 的 {@code BlockPattern.getPreview} 把"每种仓各画一格"。
     *
     * <h3>为什么必须另建实例，而不是给 {@link #lowCostAbilities} 的返回值加 previewCount</h3>
     * {@code nanochipHatches()} 那一个实例同时被主机 {@code H}（仓室位）与 {@code F}（模块位）引用
     * （{@code moduleSlots(hatches)}，见该类 javadoc：共用是为了让"至少 1 个能源仓"成为一条<b>全局</b>限制）。
     * 若在共用实例上设 previewCount，{@code F} 位的前几格预览也会变成"输入总线"，把模块位的示意盖掉。
     * 所以这里新建一个<b>只挂在 {@code H} / {@code V} 上</b>的实例：它落在 {@code common} 列表里
     * （{@code minCount = -1} ⇒ 不参与 {@code limited} 的全局配额），对匹配而言只是多了一条
     * "同能力、同判定"的 OR 分支 ⇒ <b>可接受的方块集合一个不变</b>。
     *
     * <p>★ <b>2026-10-02（队长，方案 A）</b>：这里的候选表已换成 {@link #previewOfModuleHosts} ——
     * 门控自 {@link #PREVIEW_MODULE_HOSTS_ONLY}，默认候选 = <b>14 台本模组 NAC 模块主机方块</b>
     * （不再是"7 类 I/O 仓各一台"）⇒ 结构预览那一列只剩模块主机，
     * 输入/输出/能源/激光/维护等 I/O 类零件不再进预览。
     * 判定体（{@code applies(...)}）与 {@code previewCount} 均未改 ⇒ 匹配语义与"每台一格"的配额机制一个字不变，
     * 关掉开关即逐字回到 2026-09-19 的"每种仓各占一格"。
     *
     * @param abilityList 要各自占一格预览的能力（本项目给 7 个：物品/流体进出 + 能源 + 激光 + 维护）
     */
    public static TraceabilityPredicate previewAbilities(PartAbility... abilityList) {
        return new TraceabilityPredicate(
                (MultiblockState state) -> applies(state, abilityList),
                () -> previewOfModuleHosts(abilityList)).setPreviewCount(1);
    }

    /**
     * ★ 2026-10：<b>模块自己的图案</b>（模块 {@code V} 位 / 塔身 {@code HATCH} 位）专用的纯预览谓词。
     *
     * <p>与 {@link #previewAbilities} 相比，<b>唯一的差别是候选表内容</b>：
     * <ul>
     *   <li>默认（{@link #MODULE_SIDE_PREVIEW_IO_ONLY} = {@code true}）：候选表 = {@link #previewOf}
     *       ＝「每种 I/O 能力各一台标准仓」（2026-09-19 的原始口径，也是模块自己那 39 格仓位的正确示意）；</li>
     *   <li>任一把开关关掉（{@code MODULE_SIDE_PREVIEW_IO_ONLY = false} 或
     *       {@code PREVIEW_MODULE_HOSTS_ONLY = false}）：直接转交 {@link #previewAbilities}
     *       ⇒ 与本修复之前<b>逐字等价</b>。</li>
     * </ul>
     *
     * <p><b>判定链一个字不变</b>：判定体仍是 {@link #applies}(state, abilityList)、{@code previewCount}
     * 仍是 1、谓词仍落 {@code common}（{@code minCount}/{@code maxCount} = -1 ⇒ 不进 {@code limited}、
     * 不参与全局配额）⇒ 可接受的方块集合、结构匹配、自动搭建读的候选表全部不受影响。
     * 依据与病根说明见 {@link #MODULE_SIDE_PREVIEW_IO_ONLY} 的 javadoc。
     *
     * @param abilityList 要各自占一格预览的能力（模块侧给 {@link #NAC_HATCH_ABILITIES}）
     */
    public static TraceabilityPredicate previewAbilitiesForModule(PartAbility... abilityList) {
        if (!MODULE_SIDE_PREVIEW_IO_ONLY || !PREVIEW_MODULE_HOSTS_ONLY) {
            return previewAbilities(abilityList);   // 开关关闭 ⇒ 逐字回到 2026-10-02 之后的现状
        }
        return new TraceabilityPredicate(
                (MultiblockState state) -> applies(state, abilityList),
                () -> previewOf(abilityList)).setPreviewCount(1);
    }

    /**
     * NAC 可换装仓位的<b>能力清单</b>（顺序 = 预览里轮播的顺序）。
     *
     * <p>这是 `nanochipHatches()` / `moduleHatches()` 与预览轮播之间的**唯一真相**：
     * 两边一旦不同步（比如谁少写一个 EXPORT_ITEMS），`_census/check_hatch_abilities.js` 直接判红。
     */
    public static final PartAbility[] NAC_HATCH_ABILITIES = {
            PartAbility.IMPORT_ITEMS,
            PartAbility.EXPORT_ITEMS,
            PartAbility.IMPORT_FLUIDS,
            PartAbility.EXPORT_FLUIDS,
            PartAbility.INPUT_ENERGY,
            PartAbility.INPUT_LASER,
            PartAbility.MAINTENANCE
    };

    /** 未限量的 IO 仓（物品/流体进出，下限 0），可选并行仓（最多 1）。 */
    public static TraceabilityPredicate ioUnlimited(boolean allowParallel) {
        TraceabilityPredicate p = lowCostAbilities(PartAbility.IMPORT_ITEMS).setMinGlobalLimited(0)
                .or(lowCostAbilities(PartAbility.EXPORT_ITEMS).setMinGlobalLimited(0))
                .or(lowCostAbilities(PartAbility.IMPORT_FLUIDS).setMinGlobalLimited(0))
                .or(lowCostAbilities(PartAbility.EXPORT_FLUIDS).setMinGlobalLimited(0));
        if (allowParallel) {
            p = p.or(lowCostAbilities(PartAbility.PARALLEL_HATCH)
                    .setMinGlobalLimited(0).setMaxGlobalLimited(1));
        }
        return p;
    }

    /** 参照实现（保留原始写法以便对照）：不要在新结构里用它。 */
    @SuppressWarnings("unused")
    private static TraceabilityPredicate vanillaForComparison(PartAbility... abilityList) {
        return com.gregtechceu.gtceu.api.pattern.Predicates.abilities(abilityList);
    }
}
