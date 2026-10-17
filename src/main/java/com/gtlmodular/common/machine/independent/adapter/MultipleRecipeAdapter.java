package com.gtlmodular.common.machine.independent.adapter;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gtladd.gtladditions.api.machine.logic.GTLAddMultipleRecipesLogic;
import com.gtladd.gtladditions.api.machine.wireless.GTLAddWirelessWorkableElectricMultipleRecipesMachine;
import com.gtlmodular.common.config.GTLModularConfig;
import com.gtlmodular.common.machine.independent.persistence.BatchRecipeExtensions.Power;
import com.gtlmodular.common.machine.independent.recipe.*;
import com.gtlmodular.mixin.independent.MultipleRecipeEnergyInvoker;
import com.gtlmodular.mixin.independent.MultipleRecipeLogicInvoker;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

final class MultipleRecipeAdapter implements IndependentRecipeAdapter {
    private final GTLAddMultipleRecipesLogic logic;
    private final MultipleRecipeLogicInvoker access;
    private final MultipleRecipeEnergyInvoker energy;

    MultipleRecipeAdapter(GTLAddMultipleRecipesLogic logic) {
        this.logic = logic;
        access = (MultipleRecipeLogicInvoker) logic;
        energy = (MultipleRecipeEnergyInvoker) logic;
    }

    @Override public int threads() { return logic.getMultipleThreads(); }

    @Override
    public long totalBudget() {
        long maxParallel = logic.getMachine() instanceof LongParallelMachine longLimit
                ? longLimit.getMaxParallelLong() : logic.getMachine().getMaxParallel();
        return ParallelAllocation.totalBudget(maxParallel, threads());
    }
    @Override public boolean allowsNewBatches() { return access.gtlmodular$checkBeforeWorking(); }
    @Override public boolean allowsRunningBatches() { return access.gtlmodular$checkBeforeWorking(); }

    @Override
    public List<SingleRecipeCandidate> candidates(int availableSlots, long availableBudget, long availablePower) {
        if (availableSlots <= 0 || availableBudget <= 0 || !allowsNewBatches()) return List.of();
        long budget = Math.min(availableBudget, totalBudget());
        if (budget == 0) return List.of();
        var handler = logic.getMachine().getWirelessNetworkEnergyHandler();
        if (handler != null && !handler.isOnline()) return List.of();
        List<GTRecipe> origins = new ArrayList<>();
        for (GTRecipe recipe : access.gtlmodular$lookupCandidates()) origins.add(RecipeCandidateSnapshots.copy(recipe));
        long[] demands = new long[origins.size()];
        int maxBatchTicks = GTLModularConfig.OTHER_MACHINE_THREADS.maxBatchTicks();
        long maxBatchRuns = GTLModularConfig.OTHER_MACHINE_THREADS.maxBatchRuns();
        long powerCap = Math.min(availablePower, logic.getMachine().getOverclockVoltage());
        for (int i = 0; i < origins.size(); i++) {
            // lookup 已通过原检查；这里只虚调用并行试算，避免将构造器谓词重复执行。
            GTRecipe origin = origins.get(i);
            long demand = access.gtlmodular$maxParallel(origin, budget);
            if (demand < 0 || demand > budget) throw new IllegalArgumentException("Parallel override exceeds the offered budget");
            long maxAmount = BatchRunLimits.maxContentAmount(origin);
            demands[i] = handler == null
                    ? BatchRunLimits.cap(demand, energy.gtlmodular$totalRecipeEu(origin) * energy.gtlmodular$euMultiplier(),
                            powerCap, maxBatchTicks, maxBatchRuns, maxAmount)
                    : BatchRunLimits.cap(demand, 0.0D, 0L, 0, maxBatchRuns, maxAmount);
        }
        long[] allocated = FairParallelAllocation.allocate(budget, demands);
        List<RecipeCandidateGroup.Input> inputs = new ArrayList<>();
        List<Double> normalEnergy = new ArrayList<>();
        List<BigInteger> wirelessEnergy = new ArrayList<>();
        for (int i = 0; i < origins.size() && inputs.size() < availableSlots; i++) {
            long p = allocated[i];
            if (p == 0) continue;
            GTRecipe origin = origins.get(i);
            GTRecipe ingredients = RecipeCandidateSnapshots.scale(origin, p);
            inputs.add(new RecipeCandidateGroup.Input(ingredients, new ParallelAllocation(p, p), 1, 1));
            if (handler == null) {
                normalEnergy.add(SingleBatchEnergy.total(energy.gtlmodular$totalRecipeEu(origin), p, energy.gtlmodular$euMultiplier()));
            } else {
                wirelessEnergy.add(SingleBatchEnergy.wirelessTotal(access.gtlmodular$wirelessEut(origin), origin.duration,
                        p, energy.gtlmodular$euMultiplier(), access.gtlmodular$isEnergyConsumer()));
            }
        }
        if (handler == null) return RecipeCandidateGroup.normal(inputs, normalEnergy,
                Math.min(availablePower, logic.getMachine().getOverclockVoltage()), access.gtlmodular$limited().getLimitedDuration());
        Power wirelessKind = logic.getMachine() instanceof GTLAddWirelessWorkableElectricMultipleRecipesMachine
                ? Power.WIRELESS_ACCOUNT : Power.WIRELESS_TERMINAL;
        return RecipeCandidateGroup.wireless(inputs, wirelessEnergy, wirelessKind,
                total -> access.gtlmodular$buildWirelessRecipe(new ObjectArrayList<>(), new ObjectArrayList<>(), total));
    }
}
