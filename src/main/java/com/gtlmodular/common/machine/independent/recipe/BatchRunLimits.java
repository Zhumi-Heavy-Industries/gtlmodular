package com.gtlmodular.common.machine.independent.recipe;

import com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gtlmodular.common.machine.independent.io.BatchContents;

import java.util.List;
import java.util.Map;

/**
 * 巨构级批量的安全阀：并行数由库存和输出决定，但单批不能跑无限久，也不能把内容乘爆。
 * 这里只做上限计算，不做 IO；真实扣料仍由准入/扣料阶段重新校验。
 */
public final class BatchRunLimits {

    private BatchRunLimits() {}

    public static long cap(long demand, double energyPerRun, long power, int maxBatchTicks,
                           long maxBatchRuns, long maxAmountPerRun) {
        if (demand <= 0L) return 0L;
        long capped = demand;
        if (maxBatchRuns > 0L) capped = Math.min(capped, maxBatchRuns);
        if (maxAmountPerRun > 0L) capped = Math.min(capped, Long.MAX_VALUE / maxAmountPerRun);
        capped = Math.min(capped, timeCap(energyPerRun, power, maxBatchTicks));
        return Math.max(0L, capped);
    }

    /**
     * 每 tick 的 EU/t 是 p 倍放大：单批的 tick 耗电不能超过机器能给的功率。
     * 没有这一道时，时长上限会允许 p 大到「每 tick 要 1000 倍功率」，批量永远开不了工。
     */
    public static long tickCap(long runs, double energyPerTick, long power) {
        if (runs <= 0L || power <= 0L || !(energyPerTick > 0.0D) || !Double.isFinite(energyPerTick)) return runs;
        double max = (double) power / energyPerTick;
        if (!Double.isFinite(max) || max >= (double) runs) return runs;
        return Math.max(0L, (long) Math.floor(max));
    }

    /**
     * 单份总耗电 × p / 功率 = 这一批的 tick 数；用最长时长反推 p。
     * 功率或单份耗电无效时不加时长限制，交给输入/输出容量去夹。
     */
    public static long timeCap(double energyPerRun, long power, int maxBatchTicks) {
        if (maxBatchTicks <= 0 || power <= 0L || !(energyPerRun > 0.0D) || !Double.isFinite(energyPerRun)) {
            return Long.MAX_VALUE;
        }
        double runs = (double) power * maxBatchTicks / energyPerRun;
        if (!Double.isFinite(runs) || runs >= (double) Long.MAX_VALUE) return Long.MAX_VALUE;
        return Math.max(0L, (long) Math.floor(runs));
    }

    /** 配方里单份数量最大的一项；内容缩放按 p 乘它，超过 Long.MAX 就会溢出。 */
    public static long maxContentAmount(GTRecipe recipe) {
        long max = 0L;
        for (Map<RecipeCapability<?>, List<Content>> contents : List.of(recipe.inputs, recipe.outputs,
                recipe.tickInputs, recipe.tickOutputs)) {
            for (var entry : contents.entrySet()) {
                // EU/t 由能量层重新计算，不按 p 做 long 内容缩放；这里只保护会真实乘 p 的物料数量。
                if (entry.getKey() == EURecipeCapability.CAP) continue;
                for (Content content : entry.getValue()) {
                    max = Math.max(max, contentAmount(entry.getKey(), content));
                }
            }
        }
        return max;
    }

    // 只有会真实乘 p 的物品/流体需要溢出保护；其他能力由 IO 闸按原规则拒绝。
    private static long contentAmount(RecipeCapability<?> capability, Content content) {
        if (capability != ItemRecipeCapability.CAP && capability != FluidRecipeCapability.CAP) return 0L;
        return BatchContents.amount(capability, content.content);
    }
}
