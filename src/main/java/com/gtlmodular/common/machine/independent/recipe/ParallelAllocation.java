package com.gtlmodular.common.machine.independent.recipe;

public record ParallelAllocation(long parallels, long budget) {
    public ParallelAllocation {
        if (parallels < 0 || budget < 0 || (parallels == 0 && budget != 0)) {
            throw new IllegalArgumentException("Invalid parallel allocation");
        }
    }

    public static long totalBudget(int maxParallel, int threads) {
        if (maxParallel < 0 || threads < 0) throw new IllegalArgumentException("Negative parallel limit");
        return (long) maxParallel * threads;
    }

    public static ParallelAllocation mutable(long parallels, long consumption, long remaining) {
        var allocation = new ParallelAllocation(parallels, consumption);
        if (remaining < 0 || consumption > remaining) throw new IllegalArgumentException("Parallel budget exceeded");
        return allocation;
    }
}
