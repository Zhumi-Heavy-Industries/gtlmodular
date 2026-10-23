package com.gtlmodular.common.machine.independent.recipe;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gtladd.gtladditions.api.recipe.WirelessGTRecipe;
import com.gtlmodular.common.machine.independent.persistence.BatchRecipeExtensions.Power;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public final class RecipeCandidateGroup {
    private RecipeCandidateGroup() {}

    public record Energy(double normal, BigInteger wireless, long powerLimit, int minimumDuration) {}

    /**
     * 一条候选。maxRuns 是长批次的份数上限（由输入库存决定），无线族恒为 1；
     * 显式写满 4 个参数的旧构造器保留，避免上游适配器被迫关心长批次。
     */
    public record Input(GTRecipe ingredients, ParallelAllocation allocation,
                        double energyReduction, double durationReduction, int maxRuns) {
        public Input(GTRecipe ingredients, ParallelAllocation allocation,
                     double energyReduction, double durationReduction) {
            this(ingredients, allocation, energyReduction, durationReduction, 1);
        }

        public Input {
            if (maxRuns < 1) throw new IllegalArgumentException("Invalid long batch headroom");
        }

        private SingleRecipeCandidate finish(GTRecipe execution, Power power, Energy energy) {
            return new SingleRecipeCandidate(ingredients, execution, allocation, power, energyReduction, durationReduction, energy);
        }
    }

    public static List<SingleRecipeCandidate> normal(List<Input> inputs, List<Double> energies,
                                                    long availablePower, int minimumDuration) {
        return normal(inputs, energies, availablePower, minimumDuration, 0);
    }

    /**
     * 长批次入口：目标时长内把一条批次合并成「跑 N 次配方」。
     * 能量与最小时长同乘 N，等价于「每 tick 只付 p 的电、时长×N」，总产出与总耗电都按 p×N 记账。
     * 对齐山海 DShanhaiBatchLogic.batch：输入/输出/时长整体×N，EUt 恢复原值。
     */
    public static List<SingleRecipeCandidate> normal(List<Input> inputs, List<Double> energies,
                                                    long availablePower, int minimumDuration, int longBatchTicks) {
        if (availablePower <= 0 || inputs.isEmpty()) return List.of();
        if (longBatchTicks <= 0) return build(inputs, energies, availablePower, minimumDuration, 1);
        var base = SharedBatchEnergy.normal(energies, availablePower, minimumDuration);
        int stock = Integer.MAX_VALUE;
        for (var input : inputs) stock = Math.min(stock, input.maxRuns());
        int runs = runsFor(longBatchTicks, base.get(0).duration(), stock);
        if (runs <= 1) return build(inputs, energies, availablePower, minimumDuration, 1);
        // 同功率、N 倍时长：能量与地板一起乘，EU/t 与原来逐 tick 相同。
        List<Double> scaled = new ArrayList<>(energies.size());
        for (double energy : energies) scaled.add(energy * runs);
        return build(inputs, scaled, availablePower, scaledFloor(minimumDuration, runs), runs);
    }

    private static List<SingleRecipeCandidate> build(List<Input> inputs, List<Double> energies, long availablePower,
                                                     int minimumDuration, int runs) {
        var shares = SharedBatchEnergy.normal(energies, availablePower, minimumDuration);
        List<SingleRecipeCandidate> result = new ArrayList<>();
        for (int i = 0; i < inputs.size(); i++) {
            var input = inputs.get(i);
            // runs > 1 时合并后的输入/产出落在独立副本上：只读的缩放缓存不能被改写。
            GTRecipe ingredients = runs == 1 ? input.ingredients()
                    : RecipeCandidateSnapshots.forRuns(input.ingredients(), runs);
            result.add(new SingleRecipeCandidate(ingredients,
                    RecipeCandidateSnapshots.normal(ingredients, shares.get(i)),
                    input.allocation(), Power.WIRED, input.energyReduction(), input.durationReduction(),
                    new Energy(energies.get(i), null, availablePower, minimumDuration), runs));
        }
        return List.copyOf(result);
    }

    /**
     * 一条批次合并几次配方：目标时长 ÷ 当前单批时长，再被库存上限夹住。
     * 目标更短、时长非法或库存只够一份时都退回 1（= 原行为）。
     */
    public static int runsFor(int longBatchTicks, int baseDuration, int stockRuns) {
        if (longBatchTicks <= 0 || baseDuration <= 0) return 1;
        int runs = Math.max(1, longBatchTicks / baseDuration);
        return Math.max(1, Math.min(runs, Math.max(1, stockRuns)));
    }

    /** 最小时长也要按份数放大，否则 N 倍时长会被 20 tick 地板吃掉；溢出时饱和到 int 上限。 */
    private static int scaledFloor(int minimumDuration, int runs) {
        long floor = (long) minimumDuration * runs;
        return floor >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.max(1L, floor);
    }

    public static List<SingleRecipeCandidate> wireless(List<Input> inputs, List<BigInteger> energies, Power power,
                                                      Function<BigInteger, WirelessGTRecipe> buildClock) {
        if (inputs.isEmpty()) return List.of();
        var clock = buildClock.apply(energies.stream().reduce(BigInteger.ZERO, BigInteger::add));
        var shares = SharedBatchEnergy.wireless(energies, clock.getWirelessEuTickInputs());
        List<SingleRecipeCandidate> result = new ArrayList<>();
        for (int i = 0; i < inputs.size(); i++) {
            var input = inputs.get(i);
            result.add(input.finish(RecipeCandidateSnapshots.wireless(input.ingredients(), clock, shares.get(i)), power,
                    new Energy(0, energies.get(i), 0, clock.duration)));
        }
        return List.copyOf(result);
    }
}
