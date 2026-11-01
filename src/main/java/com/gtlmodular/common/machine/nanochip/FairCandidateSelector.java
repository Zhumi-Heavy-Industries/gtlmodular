package com.gtlmodular.common.machine.nanochip;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * <b>「显示即事实」的那一刀</b> —— 把一次配方搜索的候选集<b>稳定地截断</b>到指定条数。
 *
 * <h3>它解决的问题（机制，已取证）</h3>
 * gtladditions 的 {@code GTLAddMultipleRecipesLogic.getMultipleThreads()} <b>本身不限制并发条数</b>：
 * 它的真实语义是「并行预算倍数」（父类 {@code calculateParallels()} 里
 * {@code totalParallel = getMaxParallel() × getMultipleThreads()}，随后对 {@code lookupRecipeIterator()}
 * 返回的<b>全部</b>候选做公平分配）。而界面与 Jade 显示的「拥有 N 个跨配方线程」正是这个数字
 * ⇒ 若不在搜索阶段截断，就会出现作者原话里的「tips 写 N 条、实际同时跑几十条」。
 * 出处：{@code _census/enhancedcore-src/docs/history/20260917-before-refactor/启动上下文.md:76}
 * 与 {@code _census/真实多线程_机制与我方现状.md} §②。
 *
 * <h3>做法照抄自哪里（Enhancedcore，本方案唯一完整的 L3 样板）</h3>
 * {@code _census/enhancedcore-src/src/main/java/com/gtl/enhancedcore/common/util/FairRecipeSelector.java:11-31}
 * —— 原注释：「{@code Stable round-robin selection; permanently stocked recipes cannot starve
 * later matches.}」即<b>每轮轮换起选点</b>，避免某条「常备料」配方永远霸占全部线程、后面的配方永远轮不到。
 * 调用点：{@code ThreadLimitedRecipeLogic.java:307-320}
 * （{@code selector.select(candidates, getMultipleThreads(), recipe -> recipe.id.toString())}）。
 *
 * <h3>本实现的差别（三处，都是为了可回退与可读性，语义不变）</h3>
 * <ol>
 *   <li><b>不持有状态</b>：{@code lastSelected} 由调用方（{@code NanochipRecipeLogic}）持有并传回 ——
 *       这样本类是无状态纯函数，便于单测，也不会因为「同一个 RecipeLogic 实例被重建」而丢轮转位置
 *       （重建即从 0 开始，与 Enhancedcore 的原版行为一致）。</li>
 *   <li><b>返回值不可变</b>：返回 {@code Set.copyOf(...)}（不可变集合），避免调用方误改。</li>
 *   <li><b>不引用任何 gtladditions / gtlcore / GTCEu 类型</b>：泛型 {@code T}，只依赖
 *       {@code java.util} ⇒ 与「本模组不得强依赖 gtladditions 类」的既有纪律一致（缺了也不会类加载失败）。</li>
 * </ol>
 *
 * <h3>边界行为（逐条）</h3>
 * <ul>
 *   <li>候选为空 / {@code null} ⇒ 返回 {@code Set.of()}；</li>
 *   <li>{@code requested <= 0} ⇒ 按 {@code 1} 处理（<b>绝不返回空集</b>：那会让机器明明有料却不干活）；</li>
 *   <li>{@code requested >= 候选数} ⇒ 返回<b>全部</b>候选（截断只在真的超了才发生）；</li>
 *   <li>排序键取不到（{@code id} 返回 {@code null}）⇒ 视为 {@code ""}，仍然是<b>确定性</b>顺序，不会抛异常。</li>
 * </ul>
 *
 * <h3>可逆性</h3>
 * 删掉本文件 + {@code NanochipAssemblyComplexMachine.NanochipRecipeLogic.lookupRecipeIterator()} 的覆写
 * ⇒ 逐字回到「父类全量候选」的行为。
 */
public final class FairCandidateSelector {

    private FairCandidateSelector() {
    }

    /**
     * 从候选集里挑出至多 {@code requested} 条，起点接着上一次的终点（稳定轮转）。
     *
     * @param candidates  本次搜索出的全部候选（调用方已做过校验）
     * @param requested   本次希望同时挂起的条数（= 机器的跨配方线程数）
     * @param lastSelected 上一次选中的最后一条的 id（{@code null} = 第一轮，从排序后的第一条开始）
     * @param id          候选 → 稳定排序键（NAC 用 {@code GTRecipe::getId}）
     * @return 被选中的<b>不可变</b>集合；可能为空（候选本来就空）
     */
    public static <T> Set<T> select(java.util.Collection<T> candidates,
                                    int requested,
                                    String lastSelected,
                                    Function<T, ? extends CharSequence> id) {
        if (candidates == null || candidates.isEmpty()) {
            return Set.of();
        }
        Function<T, String> keyOf = candidate -> {
            CharSequence key = candidate == null ? null : id.apply(candidate);
            return key == null ? "" : key.toString();
        };
        List<T> sorted = new ArrayList<>(candidates);
        // 确定性顺序：同一批候选、同一批配方，每次排出来的次序都一样（公平轮转的前提）
        sorted.sort(Comparator.comparing(keyOf));
        int start = 0;
        if (lastSelected != null) {
            // 接着上次的终点往后找第一条「严格大于上次终点」的 —— 找不到就回到 0，构成环
            while (start < sorted.size() && keyOf.apply(sorted.get(start)).compareTo(lastSelected) <= 0) {
                start++;
            }
            if (start == sorted.size()) {
                start = 0;
            }
        }
        int count = Math.min(sorted.size(), Math.max(1, requested));
        Set<T> selected = new LinkedHashSet<>(Math.max(4, count * 2));
        for (int offset = 0; offset < count; offset++) {
            selected.add(sorted.get((start + offset) % sorted.size()));
        }
        return Set.copyOf(selected);
    }
}
