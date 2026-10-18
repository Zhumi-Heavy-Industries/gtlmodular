package com.gtlmodular.common.machine.independent.adapter;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gtladd.gtladditions.api.machine.logic.MutableRecipesLogic;
import com.gtladd.gtladditions.utils.RecipeCalculationHelper;
import com.gtlmodular.common.config.GTLModularConfig;
import com.gtlmodular.common.machine.independent.persistence.BatchRecipeExtensions.Power;
import com.gtlmodular.common.machine.independent.recipe.*;
import com.gtlmodular.mixin.independent.MutableRecipeLogicInvoker;
import net.minecraft.resources.ResourceLocation;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

final class MutableRecipeAdapter implements IndependentRecipeAdapter {
    private final MutableRecipesLogic<?> logic;
    private final MutableRecipeLogicInvoker access;
    private final Supplier<Map<ResourceLocation, Integer>> activeRecipes;
    private final RecipeFairness fairness = new RecipeFairness();

    MutableRecipeAdapter(MutableRecipesLogic<?> logic, Supplier<Map<ResourceLocation, Integer>> activeRecipes) {
        this.logic = logic;
        this.activeRecipes = activeRecipes;
        access = (MutableRecipeLogicInvoker) logic;
    }

    @Override public int threads() { return logic.getMultipleThreads(); }

    @Override
    public long totalBudget() {
        long maxParallel = logic.getMachine() instanceof LongParallelMachine longLimit
                ? longLimit.getMaxParallelLong() : logic.getMachine().getMaxParallel();
        return ParallelAllocation.totalBudget(maxParallel, threads());
    }
    @Override public boolean allowsNewBatches() { return logic.isMultipleRecipeMode() && access.gtlmodular$checkBeforeWorking(); }
    @Override public boolean allowsRunningBatches() { return access.gtlmodular$checkBeforeWorking(); }

    @Override
    public void admitted(GTRecipe recipe) {
        fairness.admitted(recipe.id, logic.getMachine().getLevel() == null
                ? 0L : logic.getMachine().getLevel().getGameTime());
    }

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
        int maxBatchTicks = GTLModularConfig.OTHER_MACHINE_THREADS.maxBatchTicks();
        long maxBatchRuns = GTLModularConfig.OTHER_MACHINE_THREADS.maxBatchRuns();
        long power = Math.min(availablePower, logic.getMachine().getOverclockVoltage());
        List<GTRecipe> origins = new ArrayList<>();
        for (GTRecipe found : access.gtlmodular$lookupCandidates()) origins.add(RecipeCandidateSnapshots.copy(found));
        int maxPerRecipe = GTLModularConfig.OTHER_MACHINE_THREADS.maxBatchesPerRecipe();
        Map<ResourceLocation, Integer> active = activeRecipes.get();
        var order = fairness.orderIndices(origins, recipe -> recipe.id, maxPerRecipe,
                active == null ? Map.of() : active);
        int window = Math.min(order.size(), availableSlots + 8);
        for (int n = 0; n < window; n++) {
            if (remaining == 0) break;
            GTRecipe origin = origins.get(order.get(n));
            var pair = access.gtlmodular$calculateParallel(logic.getMachine(), origin, remaining);
            long eut = access.gtlmodular$recipeEut(origin);
            double multiplier = access.gtlmodular$euMultiplier();
            long capped = handler == null
                    ? BatchRunLimits.cap(pair.firstLong(), (double) eut * origin.duration * multiplier, power,
                            maxBatchTicks, maxBatchRuns, BatchRunLimits.maxContentAmount(origin))
                    : BatchRunLimits.cap(pair.firstLong(), 0.0D, 0L, 0, maxBatchRuns,
                            BatchRunLimits.maxContentAmount(origin));
            if (capped <= 0L) continue;
            var allocation = ParallelAllocation.mutable(capped, Math.min(pair.secondLong(), capped), remaining);
            long actual = allocation.parallels();
            if (actual == 0L) continue;
            GTRecipe ingredients = RecipeCandidateSnapshots.scale(origin, actual);
            inputs.add(new RecipeCandidateGroup.Input(ingredients, allocation,
                    access.gtlmodular$energyReduction(), access.gtlmodular$durationReduction()));
            if (handler == null) {
                normalEnergy.add(SingleBatchEnergy.total((double) eut * origin.duration, actual, multiplier));
            } else {
                wirelessEnergy.add(MutableBatchEnergy.wirelessTotal(eut, origin.duration, actual, multiplier));
            }
            remaining -= allocation.budget();
        }
        if (handler == null) return RecipeCandidateGroup.normal(inputs, normalEnergy,
                Math.min(availablePower, logic.getMachine().getOverclockVoltage()), 20);
        return RecipeCandidateGroup.wireless(inputs, wirelessEnergy, Power.WIRELESS_TERMINAL,
                total -> RecipeCalculationHelper.INSTANCE.buildWirelessRecipe(List.of(), List.of(), 20, total, GTRecipeTypes.DUMMY_RECIPES));
    }
}
