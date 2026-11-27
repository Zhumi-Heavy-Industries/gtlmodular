package com.gtlmodular.common.machine.engine;

import com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.data.recipe.builder.GTRecipeBuilder;
import com.gregtechceu.gtceu.api.recipe.content.Content;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ★ 0.3.524：<b>合并配方器</b> —— 「独立线程关」这一路的配方合成内核。
 *
 * <p>口径（用户 2026-11-27 定稿）：
 * <b>判定等级 → 合并配方 → 算超频</b>。
 * <ol>
 *   <li>逐条做准入判定（等级 / 机器门槛 / 原配方输入匹配）；</li>
 *   <li>把通过判定的配方<b>完整合并</b>：输入 / 产物 / 非 EU 的每 tick 输入全部累加；
 *       时长 = <b>所有参与配方时长之和</b>（串行累加，不取最长）；
 *       ★ EU 是<b>总电摊到总时长</b>（0.3.527）：EU/t = Σ(每条 EU/t × 每条时长) ÷ Σ时长，
 *       不是把 EU/t 直接累加——后者会让总电耗变成 N 倍（功率满额却也跑满 N 倍时间）。</li>
 *   <li>合并结果交给机器自己的配方修改器走一次超频 / 等级折算。</li>
 * </ol>
 *
 * <p>合并后只产出<b>一条</b> GTRecipe ⇒ 引擎只开一条线程、只走一条进度条，
 * 但一次吃下所有参与配方的输入并一起出料。
 */
final class RecipeMerger {

    private RecipeMerger() {
    }

    /**
     * 把多条配方合成一条 raw 配方。
     *
     * @param recipes 已通过准入判定的配方（至少一条）
     * @return 合并后的配方；输入为空时返回 {@code null}
     */
    static GTRecipe merge(List<GTRecipe> recipes) {
        if (recipes == null || recipes.isEmpty()) {
            return null;
        }
        GTRecipe merged = GTRecipeBuilder.ofRaw().buildRawRecipe();
        int duration = 0;
        long totalEu = 0L;
        boolean hasEu = false;
        for (GTRecipe recipe : recipes) {
            if (recipe == null) {
                continue;
            }
            mergeContents(merged.inputs, recipe.inputs);
            mergeContents(merged.outputs, recipe.outputs);
            // ★ EU 不在这里累加：先跳过，最后按总电/总时长摊平成一条。
            mergeTickInputsSkippingEu(merged.tickInputs, recipe.tickInputs);
            // ★ 时长是「全部相加」，不是取最长：多条配方串行累加成一条进度条。
            duration += recipe.duration;
            // 每条配方的真实总电 = 该配方 EU/t × 该配方时长；EU/t 由 GTCEu 对 tickInputs 求和得到。
            long eut = RecipeHelper.getInputEUt(recipe);
            if (eut != 0L) {
                totalEu += eut * (long) recipe.duration;
                hasEu = true;
            }
        }
        if (merged.inputs.isEmpty() && merged.outputs.isEmpty()) {
            return null;
        }
        if (duration <= 0) {
            duration = 1;
        }
        merged.duration = duration;
        // ★ 摊平后的 EU/t：总电 ÷ 总时长。3 条 100 EU/t×100t ⇒ 30,000÷300 = 100 EU/t，
        //   总电与串行等价（30,000），不会因 EU/t 累加而翻 3 倍。
        if (hasEu) {
            long flatEut = Math.max(1L, totalEu / (long) duration);
            merged.tickInputs.put(EURecipeCapability.CAP,
                    List.of(new Content(flatEut, 10000, 10000, 0, null, null)));
        }
        return merged;
    }

    /** 每 tick 输入：非 EU 的照旧累加，EU 跳过（最后统一摊平）。 */
    private static void mergeTickInputsSkippingEu(Map<RecipeCapability<?>, List<Content>> target,
                                                  Map<RecipeCapability<?>, List<Content>> source) {
        for (Map.Entry<RecipeCapability<?>, List<Content>> entry : source.entrySet()) {
            if (entry.getKey() == EURecipeCapability.CAP) {
                continue;
            }
            mergeOneCapability(target, entry);
        }
    }

    /** 按 capability 逐个累加 Content 列表（同一 capability 的多条 Content 并列保留）。 */
    private static void mergeContents(Map<RecipeCapability<?>, List<Content>> target,
                                      Map<RecipeCapability<?>, List<Content>> source) {
        for (Map.Entry<RecipeCapability<?>, List<Content>> entry : source.entrySet()) {
            mergeOneCapability(target, entry);
        }
    }

    private static void mergeOneCapability(Map<RecipeCapability<?>, List<Content>> target,
                                           Map.Entry<RecipeCapability<?>, List<Content>> entry) {
        List<Content> list = target.computeIfAbsent(entry.getKey(), k -> new ArrayList<>());
        List<Content> incoming = entry.getValue();
        if (incoming == null || incoming.isEmpty()) {
            return;
        }
        for (Content content : incoming) {
            if (content != null) {
                list.add(copyContent(entry.getKey(), content));
            }
        }
    }

    /** Content 里可能持有可变对象（ItemStack / FluidStack），合并时必须复制一份，不能共用引用。 */
    private static Content copyContent(RecipeCapability<?> capability, Content content) {
        Object inner = content.content;
        Object copy = inner == null ? null : capability.copyContent(inner);
        return new Content(copy, content.chance, content.maxChance,
                content.tierChanceBoost, content.slotName, content.uiName);
    }
}
