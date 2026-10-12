package com.gtlmodular.common.machine.independent.adapter;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gtladd.gtladditions.api.machine.logic.MutableRecipesLogic;
import com.gtladd.gtladditions.utils.RecipeCalculationHelper;
import com.gtlmodular.common.machine.independent.persistence.BatchRecipeExtensions.Power;
import com.gtlmodular.common.machine.independent.recipe.*;
import com.gtlmodular.mixin.independent.MutableRecipeLogicInvoker;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

final class MutableRecipeAdapter implements IndependentRecipeAdapter {
    private final MutableRecipesLogic<?> logic;
    private final MutableRecipeLogicInvoker access;

    MutableRecipeAdapter(MutableRecipesLogic<?> logic) {
        this.logic = logic;
        access = (MutableRecipeLogicInvoker) logic;
    }

    @Override public int threads() { return logic.getMultipleThreads(); }
    @Override public long totalBudget() { return ParallelAllocation.totalBudget(logic.getMachine().getMaxParallel(), threads()); }
    @Override public boolean allowsNewBatches() { return logic.isMultipleRecipeMode() && access.gtlmodular$checkBeforeWorking(); }
    @Override public boolean allowsRunningBatches() { return access.gtlmodular$checkBeforeWorking(); }

    @Override
    public List<SingleRecipeCandidate> candidates(int availableSlots, long availableBudget, long availablePower) {
        if (availableSlots <= 0 || availableBudget <= 0 || !allowsNewBatches()) return List.of();
        long remaining = Math.min(availableBudget, totalBudget());
        if (remaining == 0) return List.of();
        var handler = logic.getWirelessMachine().getWirelessNetworkEnergyHandler();
        if (handler != null && !handler.isOnline()) return List.of();
        List<RecipeCandidateGroup.Input> inputs = new ArrayList<>();
        List<Double> normalEnergy = new ArrayList<>();
        List<BigInteger> wirelessEnergy = new ArrayList<>();
        for (GTRecipe found : access.gtlmodular$lookupCandidates()) {
            if (remaining == 0 || inputs.size() >= availableSlots) break;
            GTRecipe origin = RecipeCandidateSnapshots.copy(found);
            var pair = access.gtlmodular$calculateParallel(logic.getMachine(), origin, remaining);
            var allocation = ParallelAllocation.mutable(pair.firstLong(), pair.secondLong(), remaining);
            if (allocation.parallels() == 0) continue;
            long eut = access.gtlmodular$recipeEut(origin);
            double multiplier = access.gtlmodular$euMultiplier();
            GTRecipe ingredients = RecipeCandidateSnapshots.scale(origin, allocation.parallels());
            inputs.add(new RecipeCandidateGroup.Input(ingredients, allocation,
                    access.gtlmodular$energyReduction(), access.gtlmodular$durationReduction()));
            if (handler == null) {
                normalEnergy.add(SingleBatchEnergy.total((double) eut * origin.duration, allocation.parallels(), multiplier));
            } else {
                wirelessEnergy.add(MutableBatchEnergy.wirelessTotal(eut, origin.duration, allocation.parallels(), multiplier));
            }
            remaining -= allocation.budget();
        }
        if (handler == null) return RecipeCandidateGroup.normal(inputs, normalEnergy,
                Math.min(availablePower, logic.getMachine().getOverclockVoltage()), 20);
        return RecipeCandidateGroup.wireless(inputs, wirelessEnergy, Power.WIRELESS_TERMINAL,
                total -> RecipeCalculationHelper.INSTANCE.buildWirelessRecipe(List.of(), List.of(), 20, total, GTRecipeTypes.DUMMY_RECIPES));
    }
}
