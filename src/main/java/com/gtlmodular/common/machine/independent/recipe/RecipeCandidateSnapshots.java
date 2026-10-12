package com.gtlmodular.common.machine.independent.recipe;

import com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeCondition;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gtladd.gtladditions.api.recipe.WirelessGTRecipe;
import com.gtladd.gtladditions.utils.RecipeCalculationHelper;
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
        // multipleRecipe(..., 1) 会改写传入对象，GTRecipe.copy() 又共享 data，必须先隔离。
        return RecipeCalculationHelper.INSTANCE.multipleRecipe(copy(source), parallels);
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
