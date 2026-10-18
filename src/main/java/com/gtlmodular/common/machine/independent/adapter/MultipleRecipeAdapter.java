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
import net.minecraft.resources.ResourceLocation;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

final class MultipleRecipeAdapter implements IndependentRecipeAdapter {
    private final GTLAddMultipleRecipesLogic logic;
    private final MultipleRecipeLogicInvoker access;
    private final MultipleRecipeEnergyInvoker energy;
    private final Supplier<Map<ResourceLocation, Integer>> activeRecipes;
    private final RecipeFairness fairness = new RecipeFairness();
    private final CandidateCache<GTRecipe> candidateCache = new CandidateCache<>();

    MultipleRecipeAdapter(GTLAddMultipleRecipesLogic logic, Supplier<Map<ResourceLocation, Integer>> activeRecipes) {
        this.logic = logic;
        this.activeRecipes = activeRecipes;
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
    public void admitted(GTRecipe recipe) {
        fairness.admitted(recipe.id, now());
    }

    private long now() {
        return logic.getMachine().getLevel() == null ? 0L : logic.getMachine().getLevel().getGameTime();
    }

    @Override
    public List<SingleRecipeCandidate> candidates(int availableSlots, long availableBudget, long availablePower) {
        if (availableSlots <= 0 || availableBudget <= 0 || !allowsNewBatches()) return List.of();
        long budget = Math.min(availableBudget, totalBudget());
        if (budget == 0) return List.of();
        var handler = logic.getMachine().getWirelessNetworkEnergyHandler();
        if (handler != null && !handler.isOnline()) return List.of();
        List<GTRecipe> origins = new ArrayList<>();
        int cacheTicks = GTLModularConfig.OTHER_MACHINE_THREADS.candidateCacheTicks();
        long now = now();
        var cached = candidateCache.get(now, cacheTicks, () -> new ArrayList<>(access.gtlmodular$lookupCandidates()));
        for (GTRecipe recipe : cached) origins.add(RecipeCandidateSnapshots.copy(recipe));
        int maxBatchTicks = GTLModularConfig.OTHER_MACHINE_THREADS.maxBatchTicks();
        long maxBatchRuns = GTLModularConfig.OTHER_MACHINE_THREADS.maxBatchRuns();
        long powerCap = Math.min(availablePower, logic.getMachine().getOverclockVoltage());
        int maxPerRecipe = GTLModularConfig.OTHER_MACHINE_THREADS.maxBatchesPerRecipe();
        Map<ResourceLocation, Integer> active = activeRecipes.get();
        var order = fairness.orderIndices(origins, recipe -> recipe.id, maxPerRecipe,
                active == null ? Map.of() : active);
        int scanBudget = GTLModularConfig.OTHER_MACHINE_THREADS.candidateScanBudget();
        int window = scanBudget > 0
                ? Math.min(order.size(), scanBudget)
                : Math.min(order.size(), availableSlots + 8);
        long[] demands = new long[window];
        for (int n = 0; n < window; n++) {
            // lookup 已通过原检查；这里只虚调用并行试算，避免将构造器谓词重复执行。
            GTRecipe origin = origins.get(order.get(n));
            long demand = access.gtlmodular$maxParallel(origin, budget);
            if (demand < 0 || demand > budget) throw new IllegalArgumentException("Parallel override exceeds the offered budget");
            long maxAmount = BatchRunLimits.maxContentAmount(origin);
            demands[n] = handler == null
                    ? BatchRunLimits.cap(demand, energy.gtlmodular$totalRecipeEu(origin) * energy.gtlmodular$euMultiplier(),
                            powerCap, maxBatchTicks, maxBatchRuns, maxAmount)
                    : BatchRunLimits.cap(demand, 0.0D, 0L, 0, maxBatchRuns, maxAmount);
        }
        long[] allocated = FairParallelAllocation.allocate(budget, demands);
        List<RecipeCandidateGroup.Input> inputs = new ArrayList<>();
        List<Double> normalEnergy = new ArrayList<>();
        List<BigInteger> wirelessEnergy = new ArrayList<>();
        for (int n = 0; n < window; n++) {
            long p = allocated[n];
            if (p == 0) continue;
            GTRecipe origin = origins.get(order.get(n));
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
