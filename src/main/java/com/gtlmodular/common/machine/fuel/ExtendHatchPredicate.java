package com.gtlmodular.common.machine.fuel;

import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gtlmodular.GTLModular;
import com.gtlmodular.common.config.GTLModularConfig;
import com.gtlmodular.common.data.fuel.OverclockHatches;
import com.gtlmodular.common.data.tentacle.ThreadHatchMachines;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Supplier;

/**
 * 「线程仓 / 超频仓装到增强版核心的机器上」的结构侧策略。
 *
 * <h2>范围（只到增强版，不含所有多方块）</h2>
 * <ul>
 *   <li><b>增强版核心自己的机器</b>：命名空间 {@code gtl_extend} 下的全部多方块；</li>
 *   <li><b>增强版核心改过的机器</b>：它用 {@code @Mixin} 改写的 gtceu / gtlcore 机器。
 *       这类机器 id 仍是原来的命名空间，靠 id 认不出来 —— 所以对<b>非</b> {@code gtl_extend}
 *       但运行时确实被增强版接管过的机器，本类按「结构构建期间是否处在增强版结构工厂内」放行，
 *       不靠猜 id。</li>
 * </ul>
 * 本类<b>不</b>给 gtceu 原生机器、gtlcore 原生机器、第三方附属加这两仓
 * —— 那是与加速仓不同的口径。
 *
 * <h2>为什么要有「凭据」</h2>
 * <p>{@code Predicates.abilities} 追加分支时无法得知当前结构属于哪台机器，
 * 因此由 {@code MultiblockDefinitionExtendHatchMixin} 在结构构建窗口里压入机器 id，
 * 本类在窗口内读栈顶判断。窗口外（或栈空）⇒ 一律不追加，宁可维持原状。
 *
 * <h2>为什么还要单独判断「这台机器是否有超频链路」</h2>
 * <p>结构放行只解决「装得上」。超频仓要真正生效，机器必须走
 * {@code OverclockProfiles.apply}（详见 {@link OverclockProfiles#logicFor}）。
 * 增强版自己的机器里有一半用 gtlcore 或它自己的配方逻辑，不经过这条链路
 * ⇒ 那部分由 {@code ExtendOverclockMixin} 在配方逻辑层补上，本类不重复处理。
 */
public final class ExtendHatchPredicate {

    private ExtendHatchPredicate() {
    }

    /** 增强版核心的注册命名空间。 */
    private static final String EXTEND_NAMESPACE = "gtl_extend";

    /**
     * 结构构建窗口栈。用 {@link ArrayDeque} 而非单值：结构构建可能嵌套
     * （预览 / 形状信息生成时再触发一次 build），栈能保证嵌套时凭据正确恢复。
     */
    private static final Deque<ResourceLocation> BUILDING = new ArrayDeque<>();

    /** 该 id 是不是增强版核心注册的机器。 */
    public static boolean isExtendMachine(ResourceLocation id) {
        return id != null && EXTEND_NAMESPACE.equals(id.getNamespace());
    }

    /** 当前是否处在增强版机器的结构构建窗口内。 */
    public static boolean inExtendBuild() {
        return !BUILDING.isEmpty();
    }

    /**
     * 在凭据窗口内执行结构构建。
     *
     * <p>用 {@code try/finally} 保证无论结构构建是否抛异常都会弹栈 ——
     * 否则一台机器的构建失败会把这个凭据漏给后面所有机器，造成仓到处可放。
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
     * <p>条件：总开关开 + 处在增强版结构构建窗口内 + 结构作者自己没写过这两个能力
     * （写过就不叠，避免同一能力两条 limited 分支把全局配额算两次）。
     */
    public static boolean shouldExtend(PartAbility[] abilities) {
        if (!GTLModularConfig.isExtendHatchesEnabled()) {
            return false;
        }
        if (!inExtendBuild()) {
            return false;
        }
        if (abilities == null) {
            return true;    // 空数组：结构作者没声明任何能力，追加两个分支是有意义的
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
}
