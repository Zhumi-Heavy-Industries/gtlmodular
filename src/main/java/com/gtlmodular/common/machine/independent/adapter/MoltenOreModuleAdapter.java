package com.gtlmodular.common.machine.independent.adapter;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gtlmodular.GTLModular;
import com.gtlmodular.common.config.GTLModularConfig;
import com.gtlmodular.common.diag.SwallowedErrors;
import com.gtlmodular.common.machine.MoltenBlastModuleMachine;
import com.gtlmodular.common.machine.MoltenModuleHostMachine;
import com.gtlmodular.common.machine.MoltenModuleMachineBase;
import com.gtlmodular.common.machine.MoltenSpaceOreModuleMachine;
import com.gtlmodular.common.machine.independent.recipe.*;
import net.minecraft.resources.ResourceLocation;
import org.gtlcore.gtlcore.api.machine.trait.ILockRecipe;
import org.gtlcore.gtlcore.api.recipe.IGTRecipe;
import org.gtlcore.gtlcore.api.recipe.IParallelLogic;
import org.gtlcore.gtlcore.api.recipe.RecipeRunnerHelper;
import org.gtlcore.gtlcore.common.machine.trait.MultipleRecipesLogic;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 地心炼矿模块专用适配器。只读候选、不调用上游 getRecipe()（那会真实扣料），
 * 把 MultipleRecipesLogic 的合批拆成独立批次；能量折扣由机器侧的
 * IndependentEnergyMultiplier 提供，真正扣电仍走宿主枢纽。
 */
final class MoltenOreModuleAdapter implements IndependentRecipeAdapter {
    private final MultipleRecipesLogic logic;
    private final MoltenModuleMachineBase<?> machine;
    private final Supplier<Map<ResourceLocation, Integer>> activeRecipes;
    private final RecipeFairness fairness = new RecipeFairness();
    private final CandidateCache<GTRecipe> candidateCache = new CandidateCache<>();

    MoltenOreModuleAdapter(MultipleRecipesLogic logic, MoltenModuleMachineBase<?> machine,
                           Supplier<Map<ResourceLocation, Integer>> activeRecipes) {
        this.logic = logic;
        this.machine = machine;
        this.activeRecipes = activeRecipes;
    }

    @Override
    public int threads() {
        if (machine instanceof MoltenBlastModuleMachine blast) return Math.max(1, blast.totalThreads());
        if (machine instanceof MoltenSpaceOreModuleMachine ore) return Math.max(1, ore.totalThreads());
        return 1;
    }

    @Override
    public long totalBudget() {
        return ParallelAllocation.totalBudget(machine.getMaxParallel(), threads());
    }

    @Override
    public boolean allowsNewBatches() {
        return hostReady();
    }

    @Override
    public boolean allowsRunningBatches() {
        return hostReady();
    }

    private boolean hostReady() {
        MoltenModuleHostMachine host = machine.getHost();
        return machine.isFormed() && host != null && host.isFormed() && host.isWorkingEnabled();
    }

    private double multiplier() {
        return machine.independentEnergyMultiplier();
    }

    @Override
    public void admitted(GTRecipe recipe) {
        fairness.admitted(recipe.id, now());
        candidateCache.invalidate();
    }

    @Override
    public void invalidateCandidates() {
        candidateCache.invalidate();
    }

    private long now() {
        return machine.getLevel() == null ? 0L : machine.getLevel().getGameTime();
    }

    /** 空候选退避：缺料时不再每 5 tick 全量重扫，和山海的空结果退避同一思路。 */
    private long emptyBackoffUntil = Long.MIN_VALUE;
    private int emptyBackoffTicks = 20;

    private boolean check(GTRecipe recipe) {
        try {
            return RecipeRunnerHelper.matchRecipe(machine, recipe)
                    && IGTRecipe.of(recipe).getEuTier() <= machine.getTier()
                    && recipe.checkConditions(logic).isSuccess()
                    && (logic.getDataCheck() == null || logic.getDataCheck().test(recipe.data, machine));
        } catch (RuntimeException ignored) {
            SwallowedErrors.reportDebug("MoltenOreModuleAdapter.check", ignored);
            return false;
        }
    }

    /** 候选为空时，抽样一条配方逐项说清是哪道门卡住，免得只看到「未找到配方」。 */
    private void logWhyNoCandidates(GTRecipeType type) {
        if (type == null || !noCandidateLogged.add(type)) return;
        try {
            Iterator<GTRecipe> all = type.getLookup().getRecipeIterator(machine, recipe -> true);
            if (!all.hasNext()) {
                GTLModular.LOGGER.info("[independent] 候选为空：配方表本身就是空的 type={}", type.registryName);
                return;
            }
            GTRecipe recipe = all.next();
            boolean match = RecipeRunnerHelper.matchRecipe(machine, recipe);
            boolean tier = IGTRecipe.of(recipe).getEuTier() <= machine.getTier();
            boolean condition = recipe.checkConditions(logic).isSuccess();
            boolean data = logic.getDataCheck() == null || logic.getDataCheck().test(recipe.data, machine);
            GTLModular.LOGGER.info("[independent] 候选为空 type={} 抽样配方={} match={} tier={} condition={} data={}",
                    type.registryName, recipe.id, match, tier, condition, data);
        } catch (RuntimeException exception) {
            GTLModular.LOGGER.info("[independent] 候选为空排查失败: {}", exception.toString());
        }
    }

    private final java.util.Set<GTRecipeType> noCandidateLogged = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

    private Iterator<GTRecipe> lookup(GTRecipeType type) {
        ILockRecipe lock = logic instanceof ILockRecipe ? (ILockRecipe) logic : null;
        if (lock != null && lock.isLock()) {
            GTRecipe locked = lock.getLockRecipe();
            if (locked == null) {
                locked = type.getLookup().find(machine, this::check);
                lock.setLockRecipe(locked);
            }
            if (locked == null || !check(locked)) return List.<GTRecipe>of().iterator();
            return List.of(locked).iterator();
        }
        return type.getLookup().getRecipeIterator(machine, this::check);
    }

    @Override
    public List<SingleRecipeCandidate> candidates(int availableSlots, long availableBudget, long availablePower) {
        if (availableSlots <= 0 || availableBudget <= 0 || !allowsNewBatches()) return List.of();
        long budget = Math.min(availableBudget, totalBudget());
        if (budget <= 0L) return List.of();
        long now = now();
        if (now < emptyBackoffUntil) return List.of();
        int cacheTicks = GTLModularConfig.OTHER_MACHINE_THREADS.candidateCacheTicks();
        var cached = candidateCache.get(now, cacheTicks, () -> {
            List<GTRecipe> found = new ArrayList<>();
            Iterator<GTRecipe> iterator = lookup(machine.getRecipeType());
            while (iterator.hasNext()) {
                GTRecipe recipe = iterator.next();
                if (recipe != null) found.add(RecipeCandidateSnapshots.copy(recipe));
            }
            if (found.isEmpty()) logWhyNoCandidates(machine.getRecipeType());
            return found;
        });
        if (cached.isEmpty()) {
            emptyBackoffUntil = now + emptyBackoffTicks;
            emptyBackoffTicks = Math.min(200, emptyBackoffTicks * 2);
            return List.of();
        }
        emptyBackoffTicks = 20;
        List<GTRecipe> origins = new ArrayList<>(cached.size());
        for (GTRecipe recipe : cached) origins.add(RecipeCandidateSnapshots.copy(recipe));

        int maxPerRecipe = GTLModularConfig.OTHER_MACHINE_THREADS.maxBatchesPerRecipe();
        Map<ResourceLocation, Integer> active = activeRecipes.get();
        var order = fairness.orderIndices(origins, recipe -> recipe.id, maxPerRecipe,
                active == null ? Map.of() : active);
        int scanBudget = GTLModularConfig.OTHER_MACHINE_THREADS.candidateScanBudget();
        int window = scanBudget > 0
                ? Math.min(order.size(), scanBudget)
                : Math.min(order.size(), availableSlots + 8);
        long[] demands = new long[window];
        long powerCap = Math.min(availablePower, machine.getOverclockVoltage());
        int maxBatchTicks = GTLModularConfig.OTHER_MACHINE_THREADS.maxBatchTicks();
        long maxBatchRuns = GTLModularConfig.OTHER_MACHINE_THREADS.maxBatchRuns();
        double multiplier = multiplier();
        for (int n = 0; n < window; n++) {
            GTRecipe origin = origins.get(order.get(n));
            long demand = IParallelLogic.getMaxParallel(machine, origin, budget);
            if (demand < 0L || demand > budget) throw new IllegalArgumentException("Parallel override exceeds the offered budget");
            long maxAmount = BatchRunLimits.maxContentAmount(origin);
            double energyPerRun = RecipeHelper.getInputEUt(origin) * (double) origin.duration * multiplier;
            long capped = BatchRunLimits.cap(demand, energyPerRun, powerCap, maxBatchTicks, maxBatchRuns, maxAmount);
            // 再按「每 tick EU/t ≤ 机器功率」夹一次：否则 p 会把 tick 耗电放大到开不了工。
            demands[n] = BatchRunLimits.tickCap(capped, RecipeHelper.getInputEUt(origin) * multiplier, powerCap);
        }
        long[] allocated = FairParallelAllocation.allocate(budget, demands);
        List<RecipeCandidateGroup.Input> inputs = new ArrayList<>();
        List<Double> energy = new ArrayList<>();
        for (int n = 0; n < window; n++) {
            long p = allocated[n];
            if (p <= 0L) continue;
            GTRecipe origin = origins.get(order.get(n));
            GTRecipe ingredients = RecipeCandidateSnapshots.scale(origin, p);
            inputs.add(new RecipeCandidateGroup.Input(ingredients, new ParallelAllocation(p, p), 1, 1));
            energy.add(SingleBatchEnergy.total((double) RecipeHelper.getInputEUt(origin) * origin.duration, p, multiplier));
        }
        return RecipeCandidateGroup.normal(inputs, energy,
                Math.min(availablePower, machine.getOverclockVoltage()), 20);
    }
}
