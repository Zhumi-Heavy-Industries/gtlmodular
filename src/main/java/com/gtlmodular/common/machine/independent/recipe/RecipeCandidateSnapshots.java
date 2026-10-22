package com.gtlmodular.common.machine.independent.recipe;

import com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeCondition;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gtladd.gtladditions.api.recipe.WirelessGTRecipe;
import com.gtlmodular.common.machine.independent.io.BatchContents;
import com.mojang.serialization.JsonOps;
import org.gtlcore.gtlcore.api.recipe.IGTRecipe;
import org.gtlcore.gtlcore.api.recipe.RecipeExtensionCopier;

import java.util.ArrayList;
import java.util.List;

public final class RecipeCandidateSnapshots {
    private RecipeCandidateSnapshots() {}

    public static GTRecipe copy(GTRecipe source) {
        if (source.getClass() != GTRecipe.class || source.id == null || !source.ingredientActions.isEmpty()) {
            throw new IllegalArgumentException("Recipe needs a dedicated snapshot adapter: " + source.getClass().getName());
        }
        GTRecipe copy = source.copy();
        copy.data = source.data.copy();
        copy.conditions.clear();
        for (RecipeCondition condition : source.conditions) {
            var encoded = RecipeCondition.CODEC.encodeStart(JsonOps.INSTANCE, condition).getOrThrow(false, error -> {});
            var restored = RecipeCondition.CODEC.parse(JsonOps.INSTANCE, encoded.deepCopy()).getOrThrow(false, error -> {});
            if (!encoded.equals(RecipeCondition.CODEC.encodeStart(JsonOps.INSTANCE, restored).getOrThrow(false, error -> {}))) {
                throw new IllegalArgumentException("Lossy recipe condition");
            }
            copy.conditions.add(restored);
        }
        RecipeExtensionCopier.copy(source, copy);
        copy.parallels = source.parallels;
        copy.ocTier = source.ocTier;
        return copy;
    }

    public static GTRecipe scale(GTRecipe source, long parallels) {
        if (parallels < 1) throw new IllegalArgumentException("Invalid parallels");
        // 先隔离 source，再按 long 精确乘；不能直接让上游 double 乘数处理巨构级并行。
        return IndependentRecipeScale.scale(copy(source), parallels);
    }

    /**
     * 长批次：把一条已按并行缩放的只读快照复制成「跑 runs 次」的独有配方。
     * 只放大开批输入与结算产出；tick 通道仍是单次份量，电由能量层按 p 计算。
     * 对齐山海 DShanhaiBatchLogic.batch：输入/输出/时长整体×N，EUt 恢复到原值。
     */
    public static GTRecipe forRuns(GTRecipe ingredients, int runs) {
        GTRecipe owned = copy(ingredients);
        if (runs > 1) {
            BatchContents.multiplyBy(owned.inputs, runs);
            BatchContents.multiplyBy(owned.outputs, runs);
            // 概率按「p×runs 份一起抽」处理：与现在按 p 并行抽签是同一段上游代码，只是份数更大。
            long real = Math.min(Long.MAX_VALUE / runs, Math.max(1L, IGTRecipe.of(ingredients).getRealParallels())) * runs;
            IGTRecipe.of(owned).setRealParallels(Math.max(1L, real));
            owned.parallels = ingredients.parallels;
        }
        return owned;
    }

    public static GTRecipe normal(GTRecipe ingredients, SingleBatchEnergy.Normal energy) {
        GTRecipe execution = copy(ingredients);
        execution.tickInputs.put(EURecipeCapability.CAP,
                new ArrayList<>(List.of(new Content(energy.eut(), 10000, 10000, 0, null, null))));
        execution.duration = energy.duration();
        IGTRecipe.of(execution).setHasTick(true);
        IGTRecipe.of(execution).setBatchProcessed(true);
        return execution;
    }

    public static GTRecipe wireless(GTRecipe ingredients, WirelessGTRecipe clock) {
        return wireless(ingredients, clock, clock.getWirelessEuTickInputs());
    }

    public static GTRecipe wireless(GTRecipe ingredients, WirelessGTRecipe clock, java.math.BigInteger tickEnergy) {
        if (clock.getWirelessEuTickInputs() == null || clock.duration < 1) {
            throw new IllegalArgumentException("Missing wireless calculation");
        }
        GTRecipe owned = copy(ingredients);
        owned.tickInputs.remove(EURecipeCapability.CAP);
        owned.tickOutputs.remove(EURecipeCapability.CAP);
        var execution = new WirelessGTRecipe(clock.recipeType, owned.id, owned.inputs, owned.outputs,
                owned.tickInputs, owned.tickOutputs, owned.inputChanceLogics, owned.outputChanceLogics,
                owned.tickInputChanceLogics, owned.tickOutputChanceLogics, owned.conditions, owned.ingredientActions,
                owned.data, clock.duration, owned.isFuel, tickEnergy);
        RecipeExtensionCopier.copy(owned, execution);
        execution.parallels = owned.parallels;
        execution.ocTier = owned.ocTier;
        IGTRecipe.of(execution).setHasTick(!execution.tickInputs.isEmpty() || !execution.tickOutputs.isEmpty());
        IGTRecipe.of(execution).setBatchProcessed(true);
        return execution;
    }
}
