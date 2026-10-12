package com.gtlmodular.mixin.independent;

import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gtladd.gtladditions.api.machine.logic.MutableRecipesLogic;
import it.unimi.dsi.fastutil.longs.LongLongPair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Set;

@Mixin(value = MutableRecipesLogic.class, remap = false)
public interface MutableRecipeLogicInvoker {
    @Invoker("lookupRecipeSet") Set<GTRecipe> gtlmodular$lookupCandidates();
    @Invoker("checkRecipe") boolean gtlmodular$checkCandidate(GTRecipe recipe);
    @Invoker("checkBeforeWorking") boolean gtlmodular$checkBeforeWorking();
    @Invoker("calculateParallel") LongLongPair gtlmodular$calculateParallel(IRecipeLogicMachine machine, GTRecipe recipe, long remaining);
    @Invoker("getEuMultiplier") double gtlmodular$euMultiplier();
    @Invoker("getEnergyReductionMultiplier") double gtlmodular$energyReduction();
    @Invoker("getDurationReductionMultiplier") double gtlmodular$durationReduction();
    @Invoker("getRecipeEut") long gtlmodular$recipeEut(GTRecipe recipe);
}
