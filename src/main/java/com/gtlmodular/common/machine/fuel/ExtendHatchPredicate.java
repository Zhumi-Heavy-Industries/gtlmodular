package com.gtlmodular.common.machine.fuel;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gtlmodular.GTLModular;
import com.gtlmodular.common.config.GTLModularConfig;
import com.gtlmodular.common.data.tentacle.ThreadHatchMachines;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 「线程仓 / 超频仓装到<b>增强版核心接管的机器</b>上」的结构侧策略。
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
     * 该 id 是不是增强版接管的机器。
     *
     * <p>只看 {@code path}：这几台分属 gtceu / gtlcore / gtladditions / gtlsupb，
     * 命名空间各不相同，但 path 是唯一的。
     */
    public static boolean isExtendMachine(ResourceLocation id) {
        return id != null && ENHANCEDCORE_MACHINES.contains(id.getPath());
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
        try {
            return original.get();
        } finally {
            BUILDING.pop();
        }
    }

    /**
     * 是否要给这次 {@code abilities(...)} 追加线程仓 / 超频仓分支。
     *
     * <p>条件：开关开 + 处在增强版机器的结构构建窗口内 + 结构作者自己没写过这两个能力
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
            if (ability == ThreadHatchMachines.THREAD_HATCH
                    || ability == OverclockHatches.OVERCLOCK_HATCH) {
                return false;
            }
        }
        return true;
    }

    /**
     * 追加两个分支：线程仓（限 1 台）+ 超频仓（限 1 台）。
     *
     * <p>都带上限，与结构作者自己写时的口径一致：这两仓都不叠加，
     * 装多台没有额外收益，反而会让玩家以为能堆。
     */
    public static TraceabilityPredicate union(TraceabilityPredicate original) {
        if (original == null) {
            return null;
        }
        try {
            return original
                    .or(com.gregtechceu.gtceu.api.pattern.Predicates.abilities(ThreadHatchMachines.THREAD_HATCH)
                            .setMaxGlobalLimited(1))
                    .or(OverclockHatches.predicate());
        } catch (RuntimeException failed) {
            GTLModular.LOGGER.warn("[gtlmodular] 增强版仓室补丁跳过一次：{}", failed.toString());
            return original;
        }
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
