package com.gtlmodular.common.machine.fuel;

import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gregtechceu.gtceu.api.pattern.predicates.PredicateBlocks;
import com.gregtechceu.gtceu.api.pattern.predicates.SimplePredicate;
import com.gtlmodular.common.data.fuel.AccelerateHatches;
import com.gtlmodular.common.data.fuel.OverclockHatches;
import net.minecraft.world.level.block.Block;

import java.util.HashSet;
import java.util.Set;

/**
 * 增益仓（加速仓 / 超频仓）的结构收尾修正：<b>限台数、复位下限、按需追加</b>。
 *
 * <h2>要修的两个问题</h2>
 *
 * <h3>一、同一结构里重复追加</h3>
 * <p>{@code Predicates.autoAbilities(...)} 内部对每种能力各调一次 {@code abilities(...)}
 * （输入/输出能量、物品、流体、维护、并行…），一台机器能调 6~8 次。曾经在
 * {@code abilities} 的 RETURN 上追加加速仓分支，于是同一结构被追加了 6~8 份。
 * <p>后果不止是结构预览里出现多个加速仓：{@code SimplePredicate#testGlobal} 的计数键是
 * <b>谓词对象本身</b>（{@code blockWorldState.getGlobalCount().get(this)}），
 * 每个分支独立计数 ⇒ N 个分支就能放 N 台。
 *
 * <h3>二、被结构作者的下限覆盖</h3>
 * <p>{@code setMinGlobalLimited} / {@code setExactLimit} 会把整个 limited 列表
 * （并把 common 全部搬进来）逐项覆盖成同一个值 ⇒ 增益仓变成「最少 1 台」，
 * 不放就判结构不完整。
 *
 * <h2>★ 绝不能改动的东西（改了会弄崩别的模组）</h2>
 * <p>{@code gtladditions} 用 {@code PatternPredicateSelector.matches(predicate)} 在结构构建后
 * 按<b>谓词内容</b>做补丁：它把 {@code predicate.common + predicate.limited} 里每一项的
 * {@code candidates} 摊平成方块集合再比对。因此本类：
 * <ul>
 *   <li><b>不删除</b>列表里的任何 {@link SimplePredicate} —— 删了会让它的选择器匹配不上，
 *       抛「Pattern patch 'xxx' matched no predicates」并中断启动；</li>
 *   <li><b>不替换</b>结构数组里的谓词对象 —— 换了同样会让按内容查找的补丁失效；</li>
 *   <li>只改两个数值 {@code maxCount} / {@code minCount}，并用<b>就地追加</b>
 *       （{@code limited.add}）补分支。</li>
 * </ul>
 *
 * <h2>为什么必须在结构构建之后</h2>
 * <p>上面两件事都发生在结构作者构造谓词的过程中，只有谓词表定型之后才能可靠处理 ——
 * 因此统一在 {@code FactoryBlockPattern#build()} 的返回处调用。
 *
 * <h2>最终口径</h2>
 * <p>两种增益仓都是：<b>最多 1 台、非必需、不进结构预览</b>。
 */
public final class HatchQuotaRepair {

    private HatchQuotaRepair() {
    }

    /** 是否这一项「只匹配增益仓方块」。 */
    private static boolean isGainHatch(SimplePredicate simple) {
        return isSubsetOf(simple, gainHatchBlocks());
    }

    /** 是否这一项「只匹配加速仓方块」。 */
    private static boolean isAccelerateHatch(SimplePredicate simple) {
        return isSubsetOf(simple, Set.copyOf(AccelerateHatches.ACCELERATE_HATCH.getAllBlocks()));
    }

    private static boolean isSubsetOf(SimplePredicate simple, Set<Block> allowed) {
        if (!(simple instanceof PredicateBlocks blocks) || blocks.blocks == null || blocks.blocks.length == 0) {
            return false;
        }
        if (allowed.isEmpty()) {
            return false;
        }
        for (Block block : blocks.blocks) {
            if (!allowed.contains(block)) {
                // 混了别的方块 ⇒ 不是纯增益仓谓词，不能动它。
                return false;
            }
        }
        return true;
    }

    private static Set<Block> gainHatchBlocks() {
        Set<Block> gain = new HashSet<>();
        gain.addAll(AccelerateHatches.ACCELERATE_HATCH.getAllBlocks());
        gain.addAll(OverclockHatches.OVERCLOCK_HATCH.getAllBlocks());
        return gain;
    }

    /**
     * 对结构里的一格做收尾修正。
     *
     * <p>只改数值，不增删结构里已有的对象。
     */
    public static void normalize(TraceabilityPredicate predicate) {
        if (predicate == null) {
            return;
        }
        // 重复的加速仓分支：第一份保留上限 1，其余归零。
        // 不能 remove —— gtladditions 的补丁按列表内容匹配。
        boolean seenAccelerate = false;
        for (SimplePredicate simple : predicate.limited) {
            if (!isAccelerateHatch(simple)) {
                continue;
            }
            simple.minCount = -1;
            simple.minLayerCount = -1;
            if (seenAccelerate) {
                simple.maxCount = 0;
            } else {
                simple.maxCount = 1;
                seenAccelerate = true;
            }
        }
        for (SimplePredicate simple : predicate.common) {
            if (isGainHatch(simple)) {
                simple.minCount = -1;
                simple.minLayerCount = -1;
            }
        }
    }

    /**
     * 收尾修正，并在这一格还没有加速仓位置时就地追加一份。
     *
     * <p>追加用 {@code limited.add(...)}，<b>不改结构数组里的谓词对象</b>。
     *
     * @param predicate     结构某一格最终的谓词
     * @param addAccelerate 是否要给这一格补加速仓位置
     */
    public static void normalizeAndMaybeAttach(TraceabilityPredicate predicate, boolean addAccelerate) {
        if (predicate == null) {
            return;
        }
        normalize(predicate);
        if (!addAccelerate || containsAccelerate(predicate)) {
            // 已经有位置了：不再追加，天然去重。
            return;
        }
        if (predicate.isAir()) {
            // 空气格绝不能动：gtladditions 用 PatternPredicateSelector.pureAir() 找这些格子
            // 做结构补丁（如 research_station_internal_air_relaxation），
            // 而 isAir() 要求 limited 为空。往里加任何东西都会让它匹配不上并抛异常。
            return;
        }
        TraceabilityPredicate branch = AccelerateHatchPredicate.branch();
        for (SimplePredicate simple : branch.limited) {
            attach(predicate, simple);
        }
        for (SimplePredicate simple : branch.common) {
            attach(predicate, simple);
        }
    }

    /** 把一份增益仓分支放进这一格，统一口径：最多 1 台、非必需。 */
    private static void attach(TraceabilityPredicate predicate, SimplePredicate simple) {
        simple.minCount = -1;
        simple.minLayerCount = -1;
        simple.maxCount = 1;
        predicate.limited.add(simple);
    }

    /** 这一格是否已经有加速仓位置。 */
    public static boolean containsAccelerate(TraceabilityPredicate predicate) {
        if (predicate == null) {
            return false;
        }
        for (SimplePredicate simple : predicate.common) {
            if (isAccelerateHatch(simple)) {
                return true;
            }
        }
        for (SimplePredicate simple : predicate.limited) {
            if (isAccelerateHatch(simple)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 对整个结构做收尾修正。
     *
     * <p>mixin 只负责把 {@link BlockPattern} 递进来，遍历与判定都在这里，避免两处各写一份。
     *
     * @param pattern       刚构建好的结构，可为 {@code null}
     * @param addAccelerate 是否要给没有加速仓位置的格子补一个
     */
    public static void normalizePattern(BlockPattern pattern, boolean addAccelerate) {
        TraceabilityPredicate[][][] matches = blockMatches(pattern);
        if (matches == null) {
            return;
        }
        for (TraceabilityPredicate[][] aisle : matches) {
            for (TraceabilityPredicate[] row : aisle) {
                for (TraceabilityPredicate cell : row) {
                    normalizeAndMaybeAttach(cell, addAccelerate);
                }
            }
        }
    }

    /**
     * 读结构的谓词表。
     *
     * <p>{@code blockMatches} 在 GTCEu 里是 {@code protected final}，本模组读它只为
     * 做上面的收尾修正；读不到就返回 {@code null}，调用方原样放行。
     */
    private static TraceabilityPredicate[][][] blockMatches(BlockPattern pattern) {
        if (pattern == null) {
            return null;
        }
        try {
            var field = BlockPattern.class.getDeclaredField("blockMatches");
            field.setAccessible(true);
            Object raw = field.get(pattern);
            return raw instanceof TraceabilityPredicate[][][] matches ? matches : null;
        } catch (ReflectiveOperationException | RuntimeException failed) {
            return null;
        }
    }
}
