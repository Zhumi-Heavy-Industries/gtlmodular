package com.gtlmodular.common.machine.independent.recipe;

import com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gtlmodular.common.machine.independent.io.BatchContents;
import org.gtlcore.gtlcore.api.recipe.IAdvancedContentModifier;
import org.gtlcore.gtlcore.api.recipe.IGTRecipe;
import org.gtlcore.gtlcore.api.recipe.RecipeExtensionCopier;

import java.util.List;
import java.util.Map;

/**
 * 批次缩放必须按 long 精确乘，不能只靠上游的 double 乘数：巨构级并行下 double 会丢低位。
 * 这里只在副本上改，原配方保持只读；EU/t 由能量层重新计算，不参与物料精确放大。
 */
public final class IndependentRecipeScale {

    private IndependentRecipeScale() {}

    public static GTRecipe scale(GTRecipe source, long parallels) {
        if (parallels < 1L) throw new IllegalArgumentException("Invalid parallel count");
        IGTRecipe sourceExtension = IGTRecipe.of(source);
        if (parallels == 1L) {
            sourceExtension.setRealParallels(1L);
            return source;
        }
        GTRecipe scaled = source.copy(IAdvancedContentModifier.preciseMultiplier(parallels), false);
        RecipeExtensionCopier.copy(source, scaled);
        IGTRecipe.of(scaled).setRealParallels(parallels);
        rescaleExactly(source, scaled, parallels);
        return scaled;
    }

    private static void rescaleExactly(GTRecipe source, GTRecipe scaled, long parallels) {
        rescaleMap(source.inputs, scaled.inputs, parallels);
        rescaleMap(source.outputs, scaled.outputs, parallels);
        rescaleMap(source.tickInputs, scaled.tickInputs, parallels);
        rescaleMap(source.tickOutputs, scaled.tickOutputs, parallels);
    }

    /** 对一组已隔离的配方内容做精确放大；供批次缩放与单测复用。 */
    public static void rescaleContents(Map<RecipeCapability<?>, List<Content>> originalMap,
                                       Map<RecipeCapability<?>, List<Content>> scaledMap, long parallels) {
        rescaleMap(originalMap, scaledMap, parallels);
    }

    private static void rescaleMap(Map<RecipeCapability<?>, List<Content>> originalMap,
                                   Map<RecipeCapability<?>, List<Content>> scaledMap, long parallels) {
        if (originalMap == null || scaledMap == null) return;
        for (var entry : scaledMap.entrySet()) {
            if (entry.getKey() == EURecipeCapability.CAP) continue;
            List<Content> original = originalMap.get(entry.getKey());
            List<Content> scaled = entry.getValue();
            if (original == null || scaled == null || original.size() != scaled.size()) continue;
            for (int i = 0; i < scaled.size(); i++) {
                // 电路是配置不是材料：并行不能把「1 份编程电路」放大成 p 份，否则永远凑不齐。
                if (BatchContents.isCircuit(original.get(i).content)) continue;
                long per = BatchContents.amount(entry.getKey(), original.get(i).content);
                if (per <= 0L) continue;
                BatchContents.amount(entry.getKey(), scaled.get(i), multiplyExact(per, parallels));
            }
        }
    }

    /** 精确乘法的单点入口；巨构级数量超出 long 时明确拒绝，不能静默饱和成假数量。 */
    public static long multiplyExact(long perRun, long parallels) {
        if (perRun <= 0L || parallels < 1L) throw new IllegalArgumentException("Invalid batch scale");
        try {
            return Math.multiplyExact(perRun, parallels);
        } catch (ArithmeticException overflow) {
            throw new IllegalArgumentException("Batch content overflow", overflow);
        }
    }
}
