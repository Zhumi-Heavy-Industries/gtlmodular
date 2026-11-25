package com.gtlmodular.common.machine.fuel;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gregtechceu.gtceu.api.pattern.predicates.SimplePredicate;
import com.gtlmodular.GTLModular;
import com.gtlmodular.common.config.GTLModularConfig;
import com.gtlmodular.common.data.fuel.OverclockHatches;
import com.gtlmodular.common.machine.thread.IThreadHatchPart;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 「超频仓装到<b>增强版核心接管的机器</b>上」的结构侧策略。
 *
 * <h2>增强版核心接管哪些机器</h2>
 * <p>GTL-Enhancedcore 在 {@code IvMachineScope} 里用<b>固定的 id 名单</b>决定接管范围
 * （取证：该类的常量 {@code NATIVE_IDS} / {@code FOREIGN_NATIVE_IDS} /
 * {@code ADD_THREAD_ONLY_IDS} / {@code BATCH_IDS}）。本类沿用同一份名单
 * —— <b>名单一致才谈得上“装得上也能生效”</b>：不在名单里的机器、它本来就不接管，
 * 我们在这里加仓位只会让玩家放进去却没有任何效果。
 *
 * <h2>为什么需要「凭据」</h2>
 * <p>{@code Predicates.abilities} 追加分支时无法得知当前结构属于哪台机器，
 * 因此由 {@code MultiblockDefinitionExtendHatchMixin} 在结构构建窗口里压入机器 id，
 * 本类在窗口内读栈顶判断。窗口外（或栈空）⇒ 一律不追加，宁可维持原状。
 */
public final class ExtendHatchPredicate {

    private ExtendHatchPredicate() {
    }

    /**
     * 增强版核心（GTL-Enhancedcore）接管的机器 id 名单。
     *
     * <p>与 {@code IvMachineScope} 常量池里的名单逐条对应。新增/删减时两边一起改，
     * 否则会出现「结构放得进、增强版不认」或相反的情况。
     */
    /** 增强版核心的结构改写入口（反射调用，见 {@link #enhancedTargets}）。 */
    private static final String ENHANCEDCORE_PATTERNS =
            "com.gtl.enhancedcore.common.structure.GtlMegastructurePatterns";

    private static final Set<String> ENHANCEDCORE_MACHINES = Set.of(
            // —— 巨构 / 高能
            "atomic_energy_excitation_plant",
            "crystalline_infinity",
            "suprachronal_assembly_line",
            "super_particle_collider",
            "advanced_sps_crafting",
            "advanced_neutron_activator",
            "gravitation_shockburst",
            // —— 大型处理
            "mega_extractor",
            "mega_presser",
            "mega_distillery",
            // 以下三台同样被增强版的 GtlMegastructurePatterns 改写结构
            // （见其 structures/gtl/manifest.json），漏掉会让加速仓漏放进来。
            "mega_canner",
            "mega_wiremill",
            "dimensional_focus_engraving_array",
            "super_blast_smelter",
            "huge_incubator",
            "holy_separator",
            "inferno_cleft_smelting_vault",
            "advanced_vacuum_drying_furnace",
            // —— 其他
            "field_extruder_factory",
            "cooling_tower",
            "mage_assembler");

    /**
     * 结构构建窗口栈。用 {@link ArrayDeque}：结构构建可能嵌套
     * （预览 / 形状信息生成时再触发一次 build），栈能保证嵌套时凭据正确恢复。
     */
    private static final Deque<ResourceLocation> BUILDING = new ArrayDeque<>();

    /**
     * 当前这次结构构建已经追加过哪些仓。
     *
     * <h2>为什么需要它</h2>
     * <p>{@code Predicates.autoAbilities(...)} 内部会为每种能力<b>各调一次</b>
     * {@code abilities(...)}（输入/输出能量、物品、流体、维护、并行…），
     * 一台机器最多调 6~7 次。若每次都在 RETURN 上追加一份超频仓，
     * 整台机器就会生成 6~7 个互相独立的谓词 —— 结构预览里于是出现 7 组
     * 「最多：1 个」超频仓（实测截图）。
     *
     * <p>按 GTO「天球分歧引擎」的写法：一个仓在整台机器只声明一次。
     * 这里在构建窗口内记下已追加的种类，重复调用直接跳过。
     */
    private static final Deque<Set<String>> ADDED = new ArrayDeque<>();

    /**
     * 该 id 是不是增强版接管的机器。
     *
     * <p>只看 {@code path}：这几台分属 gtceu / gtlcore / gtladditions / gtlsupb，
     * 命名空间各不相同，但 path 是唯一的。
     */
    public static boolean isExtendMachine(ResourceLocation id) {
        if (id == null) {
            return false;
        }
        // 首选：直接问增强版自己。GtlMegastructurePatterns.targets 是它的公开入口，
        // 覆盖它用 JSON 改写结构的全部机器（43 台，且随它升级自动同步）。
        // 手抄名单在模组换代后必然漏登记，曾经因此让加速仓漏放进被改写的机器，
        // 也让结构收尾改到了不该改的地方引发崩服。
        Boolean enhanced = enhancedTargets(id);
        if (enhanced != null) {
            return enhanced;
        }
        // 兜底：增强版改写「仓室/结构」但在 targets 名单之外的那几台（如原生隔离目标）。
        return ENHANCEDCORE_MACHINES.contains(id.getPath());
    }

    /**
     * 反射问增强版「这台机器是不是它的结构改写目标」。
     *
     * <p>走反射而不是直接 import：增强版核心对本模组是<b>可选依赖</b>，独立版
     * {@code gtl_nac} 不装它，直接引用会在类加载期抛 {@code NoClassDefFoundError}。
     *
     * @return {@code true}/{@code false} 表示问到结果；{@code null} 表示没装增强版核心或调用失败，
     *         调用方退回静态名单
     */
    private static Boolean enhancedTargets(ResourceLocation id) {
        try {
            Class<?> patterns = Class.forName(ENHANCEDCORE_PATTERNS);
            Object result = patterns.getMethod("targets", ResourceLocation.class).invoke(null, id);
            return result instanceof Boolean matched ? matched : null;
        } catch (ReflectiveOperationException | RuntimeException absent) {
            return null;
        }
    }

    /** 当前是否处在增强版机器的结构构建窗口内。 */
    public static boolean inExtendBuild() {
        return !BUILDING.isEmpty();
    }

    /**
     * 在凭据窗口内执行结构构建。
     *
     * <p>{@code try/finally} 保证无论结构构建是否抛异常都会弹栈 ——
     * 否则一台机器构建失败会把凭据漏给后面所有机器，造成仓到处可放。
     */
    public static BlockPattern buildWithCredential(ResourceLocation id, Supplier<BlockPattern> original) {
        BUILDING.push(id);
        ADDED.push(java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>()));
        try {
            // 窗口内只建立凭据，不改结构一个字节。
            //
            // 增强版的 GtlMegastructurePatterns.replace(...) 在构建过程中会校验
            // 「requiredParts 的原始规则没被动过」（common.size()==1 且 limited 为空），
            // 一旦我们改过有限列表就直接抛 "Required part rule changed"。
            // 而 replace 用的是缓存里的同一个 BlockPattern 对象，第一次改过，
            // 后续任何一次重新取值都会撞上这条校验 ⇒ 启动崩溃。
            //
            // 提速仓的收尾统一交给 FactoryBlockPatternGainHatchQuotaMixin：
            // 那里的 shouldExtend() 在窗口内为 false，只会复位下限、不会追加分支。
            return original.get();
        } finally {
            BUILDING.pop();
            ADDED.pop();
        }
    }

    /**
     * 是否要给这次 {@code abilities(...)} 追加超频仓分支。
     *
     * <p>条件：开关开 + 处在增强版机器的结构构建窗口内 + 结构作者自己没写过超频仓
     * （写过就不叠，避免同一能力两条 limited 分支把全局配额算两次）。
     */
    public static boolean shouldExtend(PartAbility[] abilities) {
        if (!GTLModularConfig.isExtendHatchesEnabled() || !inExtendBuild()) {
            return false;
        }
        if (abilities == null) {
            return true;
        }
        for (PartAbility ability : abilities) {
            if (ability == OverclockHatches.OVERCLOCK_HATCH) {
                return false;
            }
        }
        return true;
    }

    /**
     * 追加超频仓分支（限 1 台）。
     *
     * <p>带上限，与结构作者自己写时的口径一致：超频仓不叠加，装多台没有额外收益，
     * 反而会让玩家以为能堆。
     *
     * <h2>为什么不放线程仓</h2>
     * <p>增强版机器的线程数由它自己的模组决定，它不认识 {@link IThreadHatchPart} ——
     * 线程仓装上去能成形，但不会增加任何线程，属于对玩家的误导。因此这里不放行；
     * 本模组自己的机器另有结构谓词直接声明线程仓（那里才是真正生效的地方）。
     */
    /**
     * <b>不在这里追加任何仓。</b>
     *
     * <h2>为什么</h2>
     * <p>{@code Predicates.autoAbilities(...)} 里对 INPUT_ENERGY 的写法是
     * {@code setMinGlobalLimited(1).setMaxGlobalLimited(2).setPreviewCount(1)}。
     * 这几个方法会把<b>整个 limited 列表</b>（并把 common 全搬进去）逐项覆盖成同一个值：
     * <pre>
     *   for (SimplePredicate predicate : this.limited) predicate.maxCount = max;
     * </pre>
     * 只要我们的仓在这次调用之前进入了同一列表，就会被改成「最少1个、最多2个」——
     * 实测表现就是预览里同时出现「最多：1 个」与「最少1个，最多2个」两套定义，
     * 且结构里能放 2 台。
     *
     * <p>所以追加必须发生在<b>所有 setter 都跑完之后</b>，即结构彻底定型时。
     * 增强版结构由 {@code GtlMegastructurePatterns.replace(...)} 产出，
     * 在它的 RETURN 处统一处理（见 {@code EnhancedcoreReplaceHatchMixin}）。
     */
    public static TraceabilityPredicate union(TraceabilityPredicate original) {
        return original;
    }

    /** 超频仓能力（供 mixin 追加时引用）。 */
    public static final com.gregtechceu.gtceu.api.machine.multiblock.PartAbility OVERCLOCK_ABILITY =
            OverclockHatches.OVERCLOCK_HATCH;

    /** 加速仓能力。 */
    public static final com.gregtechceu.gtceu.api.machine.multiblock.PartAbility ACCELERATE_ABILITY =
            com.gtlmodular.common.data.fuel.AccelerateHatches.ACCELERATE_HATCH;

    /** 这台机器要不要在结构定型后追加增益仓。 */
    public static boolean shouldAttachFor(ResourceLocation id) {
        return isExtendMachine(id) && GTLModularConfig.isExtendHatchesEnabled();
    }

    /** 这一格是否已经能放超频仓。 */
    public static boolean containsOverclock(TraceabilityPredicate cell) {
        return containsAbility(cell, OVERCLOCK_ABILITY);
    }

    /** 这一格是否已经能放加速仓。 */
    public static boolean containsAccelerate(TraceabilityPredicate cell) {
        return containsAbility(cell, ACCELERATE_ABILITY);
    }

    private static boolean containsAbility(TraceabilityPredicate cell, com.gregtechceu.gtceu.api.machine.multiblock.PartAbility ability) {
        for (SimplePredicate simple : cell.limited) {
            if (isOnlyOf(simple, ability)) {
                return true;
            }
        }
        for (SimplePredicate simple : cell.common) {
            if (isOnlyOf(simple, ability)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isOnlyOf(SimplePredicate simple, com.gregtechceu.gtceu.api.machine.multiblock.PartAbility ability) {
        if (!(simple instanceof com.gregtechceu.gtceu.api.pattern.predicates.PredicateBlocks blocks)
                || blocks.blocks == null || blocks.blocks.length == 0) {
            return false;
        }
        var allowed = ability.getAllBlocks();
        if (allowed.isEmpty()) {
            return false;
        }
        for (var block : blocks.blocks) {
            if (!allowed.contains(block)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 把「可放某仓」就地追加到这一格。
     *
     * <p>放进 limited 并设成「最多 1 台、非必需」。由于调用点已在
     * {@code replace(...)} 的 RETURN，此时不会再有人遍历列表覆盖它。
     */
    public static void attach(TraceabilityPredicate cell, com.gregtechceu.gtceu.api.machine.multiblock.PartAbility ability) {
        TraceabilityPredicate branch = com.gregtechceu.gtceu.api.pattern.Predicates.abilities(ability)
                .setMaxGlobalLimited(1);
        for (SimplePredicate simple : branch.limited) {
            simple.minCount = -1;
            simple.minLayerCount = -1;
            simple.maxCount = 1;
            cell.limited.add(simple);
        }
        for (SimplePredicate simple : branch.common) {
            simple.minCount = -1;
            simple.minLayerCount = -1;
            simple.maxCount = 1;
            cell.limited.add(simple);
        }
    }

    /**
     * 在当前构建窗口里登记「这种仓已经追加过」。
     *
     * @return true 表示本次是第一次，调用方应该追加
     */
    private static boolean markAdded(com.gregtechceu.gtceu.api.machine.multiblock.PartAbility ability) {
        Set<String> current = ADDED.peek();
        if (current == null) {
            // 不在构建窗口内（理论上不会发生），保守起见不追加。
            return false;
        }
        // 每次 build 各记一套：增强版 raw / replaced 是两份不同的结构，
        // 各自都需要完整的一份，只防「同一次 build 内被 autoAbilities 调 7 次」。
        return current.add(ability.getName());
    }

    /** 这台机器是否在增强版名单内（供运行期判定复用）。 */
    public static boolean isExtendMachine(MetaMachine machine) {
        if (machine == null) {
            return false;
        }
        var definition = machine.getDefinition();
        return definition != null && isExtendMachine(definition.getId());
    }
}
