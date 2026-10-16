package com.gtlmodular.common.machine.nanochip;

import com.gtlmodular.common.data.nanochip.NanochipModContext;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.ingredient.SizedIngredient;
import org.gtlcore.gtlcore.api.recipe.IAdvancedContentModifier;
import org.gtlcore.gtlcore.api.recipe.IGTRecipe;
import org.gtlcore.gtlcore.api.recipe.RecipeExtensionCopier;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongIngredient;

import java.util.List;
import java.util.Map;
import java.util.function.LongPredicate;

/** 集中处理大数内容缩放与可容纳并行探测，避免各调用方使用不同的数量口径。 */
public final class NanochipParallelFit {

    /** 默认允许 long 范围并行；关闭后将搜索上界限制在 int 范围。 */
    public static final boolean NAC_PARALLEL_CEILING_TO_LONG = true;

    public static final long MAX_PARALLEL_SEARCH_BOUND =
            NAC_PARALLEL_CEILING_TO_LONG ? Long.MAX_VALUE : Integer.MAX_VALUE;

    // Single-batch ceiling: an infinite ME cell (Long.MAX_VALUE stock) or the vacuum network
    // per-entry 1e9 limit turns a stock reading into a batch size; clamp it so one batch stays sane.
    public static final long MAX_SINGLE_BATCH_PARALLEL = 1_000_000L;

    /** 保持关闭且不参与数量计算，产出必须由每份数量与实际并行决定。 */
    public static final boolean NAC_OUTPUT_AMOUNT_TO_CEILING = false;

    private NanochipParallelFit() {
    }

    public static long findHighestMatchableInSafeRange(long requestedParallel, LongPredicate canMatch) {
        if (requestedParallel <= 0L || canMatch == null) {
            return 0L;
        }
        long bounded = Math.min(requestedParallel, MAX_PARALLEL_SEARCH_BOUND);
        return findHighestMatchable(bounded, canMatch);
    }

    /** 复制配方后还需按原始内容校正数量，避免 double 乘数在大整数区间丢失精度。 */
    public static GTRecipe scalePrecisely(GTRecipe recipe, long parallel) {
        if (recipe == null) {
            return null;
        }
        if (parallel <= 1L) {
            return recipe;
        }
        GTRecipe scaled = recipe.copy(IAdvancedContentModifier.preciseMultiplier(parallel), false);
        // 扩展字段也须复制，避免丢失真实并行等结算信息。
        RecipeExtensionCopier.copy(recipe, scaled);
        IGTRecipe.of(scaled).setRealParallels(parallel);
        if (NAC_PARALLEL_CEILING_TO_LONG) {
            rescaleContentsExactly(recipe, scaled, parallel);
        }
        return scaled;
    }

    private static void rescaleContentsExactly(GTRecipe original, GTRecipe scaled, long parallel) {
        if (original == null || scaled == null || parallel <= 1L) {
            return;
        }
        rescaleMapExactly(original.inputs, scaled.inputs, parallel);
        rescaleMapExactly(original.outputs, scaled.outputs, parallel);
        rescaleMapExactly(original.tickInputs, scaled.tickInputs, parallel);
        rescaleMapExactly(original.tickOutputs, scaled.tickOutputs, parallel);
    }

    /** 按能力键和列表下标对齐副本，避免把一项内容的数量写到另一项。 */
    private static void rescaleMapExactly(Map<RecipeCapability<?>, List<Content>> originalMap,
                                          Map<RecipeCapability<?>, List<Content>> scaledMap,
                                          long parallel) {
        if (originalMap == null || originalMap.isEmpty() || scaledMap == null || scaledMap.isEmpty()) {
            return;
        }
        for (Map.Entry<RecipeCapability<?>, List<Content>> entry : scaledMap.entrySet()) {
            List<Content> scaledContents = entry.getValue();
            if (scaledContents == null || scaledContents.isEmpty()) {
                continue;
            }
            List<Content> originalContents = originalMap.get(entry.getKey());
            if (originalContents == null || originalContents.size() != scaledContents.size()) {
                continue;
            }
            for (int i = 0; i < scaledContents.size(); i++) {
                Content scaledContent = scaledContents.get(i);
                if (scaledContent == null) {
                    continue;
                }
                long per = contentAmountOf(originalContents.get(i));
                if (per <= 0L) {
                    continue;
                }
                long exact = saturatingMultiply(per, parallel);
                if (exact == contentAmountOf(scaledContent)) {
                    continue;
                }
                applyContentAmount(scaledContent, exact);
            }
        }
    }

    private static long contentAmountOf(Content content) {
        if (content == null) {
            return 0L;
        }
        Object inner = content.content;
        if (inner instanceof LongIngredient longIngredient) {
            return longIngredient.getActualAmount();
        }
        if (inner instanceof SizedIngredient sizedIngredient) {
            return Math.max(0L, (long) sizedIngredient.getAmount());
        }
        // 仅将 Long 视为数值数量，避免误把其他整数类型的编号或档位乘以并行。
        if (inner instanceof Long longValue) {
            return Math.max(0L, longValue.longValue());
        }
        return 0L;
    }

    /** 只替换数量并保留匹配谓词与标签；无法识别的类型保持原值。 */
    private static boolean applyContentAmount(Content content, long amount) {
        if (content == null || amount <= 0L) {
            return false;
        }
        Object inner = content.content;
        try {
            if (inner instanceof LongIngredient longIngredient) {
                content.content = LongIngredient.create(longIngredient.getInner(), amount);
                return true;
            }
            if (inner instanceof SizedIngredient sizedIngredient) {
                content.content = LongIngredient.create(sizedIngredient.getInner(), amount);
                return true;
            }
            if (inner instanceof Long) {
                content.content = Long.valueOf(amount);
                return true;
            }
            return false;
        } catch (RuntimeException e) {
            NanochipModContext.logger().debug("[gtlmodular] NAC 内容量 long 直乘：这一份内容换不出 long 数量（{}），保留原值",
                    e.toString());
            return false;
        }
    }

    /** 先判断上界再乘，避免溢出回绕成负数。 */
    private static long saturatingMultiply(long a, long b) {
        if (a <= 0L || b <= 0L) {
            return 0L;
        }
        return a > Long.MAX_VALUE / b ? Long.MAX_VALUE : a * b;
    }

    public static long findHighestMatchable(long requestedParallel, LongPredicate canMatch) {
        if (requestedParallel <= 0L || canMatch == null) {
            return 0L;
        }
        if (requestedParallel > MAX_SINGLE_BATCH_PARALLEL) {
            requestedParallel = MAX_SINGLE_BATCH_PARALLEL;
        }
        if (canMatch.test(requestedParallel)) {
            return requestedParallel;
        }
        if (requestedParallel == 1L) {
            return 0L;
        }
        long oneLess = requestedParallel - 1L;
        if (canMatch.test(oneLess)) {
            return oneLess;
        }
        long low = 1L;
        long high = requestedParallel - 2L;
        long best = 0L;
        while (low <= high) {
            long middle = low + ((high - low) >>> 1);
            if (canMatch.test(middle)) {
                best = middle;
                low = middle + 1L;
            } else {
                high = middle - 1L;
            }
        }
        return best;
    }
}
