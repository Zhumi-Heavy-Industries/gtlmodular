package com.gtlmodular.common.machine.nanochip;

import com.gtlmodular.common.data.nanochip.NanochipModContext;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gtladd.gtladditions.common.data.ParallelData;
import org.gtlcore.gtlcore.api.recipe.IAdvancedContentModifier;
import org.gtlcore.gtlcore.api.recipe.IGTRecipe;
import org.gtlcore.gtlcore.api.recipe.IParallelLogic;
import org.gtlcore.gtlcore.api.recipe.RecipeExtensionCopier;
import org.gtlcore.gtlcore.api.recipe.RecipeRunnerHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.LongPredicate;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static com.gtlmodular.common.machine.nanochip.NanochipAssemblyComplexMachine.ENERGY_DISTRIBUTE_INTERVAL;
import static com.gtlmodular.common.machine.nanochip.NanochipAssemblyComplexMachine.NAC_PER_THREAD_FULL_PARALLEL;

final class NanochipLegacyRecipePolicy<P> {

    private final NanochipAssemblyComplexMachine nac;
    private final IRecipeLogicMachine machine;
    private final Predicate<GTRecipe> checkRecipe;
    private final Supplier<Set<GTRecipe>> lookupRecipes;
    // 保留原私有 FitProbe 类型及实例，避免为拆分扩大嵌套类型的可见性。
    private final BiFunction<Boolean, GTRecipe, P> createProbe;
    private final Predicate<P> probeFits;

    private String lastSelectedRecipeId;

    private transient GTRecipe prechargeCachedRecipe;

    private transient int prechargeCachedDuration = -1;

    private transient long prechargeCachedTotalEu;

    NanochipLegacyRecipePolicy(NanochipAssemblyComplexMachine nac, IRecipeLogicMachine machine,
                              Predicate<GTRecipe> checkRecipe, Supplier<Set<GTRecipe>> lookupRecipes,
                              BiFunction<Boolean, GTRecipe, P> createProbe, Predicate<P> probeFits) {
        this.nac = nac;
        this.machine = machine;
        this.checkRecipe = checkRecipe;
        this.lookupRecipes = lookupRecipes;
        this.createProbe = createProbe;
        this.probeFits = probeFits;
    }

    /** 缓存按配方对象与时长绑定；大数乘法饱和，避免总电量回绕成负值。 */
    long plannedRecipeTotalEu(GTRecipe recipe) {
        if (recipe == null) {
            return 0L;
        }
        int duration = Math.max(1, recipe.duration);
        if (this.prechargeCachedRecipe == recipe && this.prechargeCachedDuration == duration) {
            return this.prechargeCachedTotalEu;
        }
        long perTick = Math.max(1L, com.gregtechceu.gtceu.api.recipe.RecipeHelper.getInputEUt(recipe));
        long total = NanochipModulePowerDistribution.saturatingMul(perTick, (long) duration);
        this.prechargeCachedRecipe = recipe;
        this.prechargeCachedDuration = duration;
        this.prechargeCachedTotalEu = total;
        return total;
    }

    /** 预蓄电闸保留一个分配周期的补充量容差；此处估算不能代替运行时的真实扣电。 */
    boolean hostAffordableWhole(RecipeLogic logic, long need) {
        if (need <= 0L) {
            return true;
        }
        com.gregtechceu.gtceu.api.misc.EnergyContainerList e = nac.getEnergyContainer();
        if (e == null) {
            return true;
        }
        long obtainable = Math.max(0L, nac.getObtainableEnergyEU());
        long supplyPerTick = Math.max(0L, nac.getMaxInputPower());
        long supplyInterval = NanochipModulePowerDistribution.saturatingMul(supplyPerTick, ENERGY_DISTRIBUTE_INTERVAL);
        long available = NanochipModulePowerDistribution.saturatingAdd(obtainable, supplyInterval);
        if (available >= need) {
            return true;
        }
        if (logic.isSuspend()) {
            return true;
        }
        return false;
    }

    /** 候选先校验再按线程数轮转，避免放不下的配方占用有限候选名额。 */
    Set<GTRecipe> lookupRecipeIterator() {
        try {
            return lookupRecipeIteratorInternal();
        } catch (RuntimeException e) {
            NanochipModContext.logger().debug("[gtlmodular] NAC 配方搜索异常（外层兜底），本次不开工：{}", e.toString());
            return Set.of();
        }
    }

    private Set<GTRecipe> lookupRecipeIteratorInternal() {
        String[] cursor = { this.lastSelectedRecipeId };
        Set<GTRecipe> selected;
        try {
            selected = NanochipCandidateSearch.search(machine, multiTypeRecipeTypes(),
                    machine.getRecipeType(), checkRecipe::test, nac.getMultipleThreads(), cursor);
        } catch (RuntimeException e) {
            NanochipModContext.logger().debug("[gtlmodular] NAC 配方搜索异常，本次不开工：{}", e.toString());
            return Set.of();
        }
        this.lastSelectedRecipeId = cursor[0];
        return selected;
    }

    private GTRecipeType[] multiTypeRecipeTypes() {
        try {
            GTRecipeType[] types = machine.getRecipeTypes();
            return types == null ? new GTRecipeType[0] : types;
        } catch (RuntimeException e) {
            NanochipModContext.logger().debug("[gtlmodular] NAC 多类型配方表读取失败，本 tick 不开工：{}", e.toString());
            return new GTRecipeType[0];
        }
    }

    private void collectCandidatesFromType(GTRecipeType type, Set<GTRecipe> candidates) {
        Iterator<GTRecipe> iterator = type.getLookup().getRecipeIterator(machine, recipe -> true);
        while (iterator.hasNext()) {
            GTRecipe candidate = iterator.next();
            if (candidate != null && checkRecipe.test(candidate)) {
                candidates.add(candidate);
            }
        }
    }

    /** 预算只是上限，必须探测真实输入与输出容量，才能得到可启动的并行数。 */
    ParallelData calculateParallels() {
        Set<GTRecipe> recipes = lookupRecipes.get();
        if (recipes.isEmpty()) {
            return null;
        }
        long perRecipeLimit = perRecipeParallelLimit();
        long[] parallels = new long[recipes.size()];
        List<GTRecipe> recipeList = new ArrayList<>(recipes.size());
        int index = 0;
        for (GTRecipe recipe : recipes) {
            long fit = findFitParallel(recipe, perRecipeLimit);
            if (fit <= 0L) {
                continue;
            }
            recipeList.add(recipe);
            parallels[index] = fit;
            index++;
        }
        if (recipeList.isEmpty()) {
            nac.noteNoFit(perRecipeLimit, recipes.size());
            return null;
        }
        // 前面的模拟没有消费输入；父类仍必须执行实际扣料。
        return new ParallelData(recipeList, parallels, true, null);
    }

    private long parallelBudget() {
        return NanochipModulePowerDistribution.saturatingMul(
                Math.max(1L, (long) nac.getMaxParallel()),
                Math.max(1L, (long) nac.getMultipleThreads()));
    }

    private long perRecipeParallelLimit() {
        if (NAC_PER_THREAD_FULL_PARALLEL) {
            return Long.MAX_VALUE;
        }
        return parallelBudget();
    }

    private long findFitParallel(GTRecipe recipe, long budget) {
        if (recipe == null || budget <= 0L) {
            return 0L;
        }
        long inputMax = IParallelLogic.getMaxParallel(machine, recipe, budget);
        if (inputMax <= 0L) {
            return 0L;
        }
        Map<Long, P> probes = new HashMap<>();
        long matchable = findHighestMatchableParallel(inputMax,
                candidate -> canFit(probes, recipe, candidate));
        return matchable <= 0L ? 0L : Math.min(matchable, budget);
    }

    private boolean canFit(Map<Long, P> probes, GTRecipe recipe, long parallel) {
        if (parallel <= 0L) {
            return false;
        }
        P cached = probes.get(parallel);
        if (cached != null) {
            return probeFits.test(cached);
        }
        GTRecipe scaled = scaleRecipePrecisely(recipe, parallel);
        boolean fits = RecipeRunnerHelper.matchRecipeInput(machine, scaled)
                && RecipeRunnerHelper.matchRecipeOutput(machine, scaled);
        probes.put(parallel, createProbe.apply(fits, fits ? scaled : null));
        return fits;
    }

    /** 并行数量可能超过浮点整数精度，内容缩放必须使用精确 long 运算。 */
    private GTRecipe scaleRecipePrecisely(GTRecipe recipe, long parallel) {
        if (parallel <= 1L) {
            return recipe;
        }
        // 追加独立线程的内容量校正会改变此路径的缩放结果，因此保留独立的缩放策略。
        GTRecipe scaled = recipe.copy(IAdvancedContentModifier.preciseMultiplier(parallel), false);
        // 扩展并行信息也需复制，否则下游会丢失 realParallels。
        RecipeExtensionCopier.copy(recipe, scaled);
        IGTRecipe.of(scaled).setRealParallels(parallel);
        return scaled;
    }

    private static long findHighestMatchableParallel(long requestedParallel, LongPredicate canMatch) {
        return NanochipParallelFit.findHighestMatchable(requestedParallel, canMatch);
    }
}
