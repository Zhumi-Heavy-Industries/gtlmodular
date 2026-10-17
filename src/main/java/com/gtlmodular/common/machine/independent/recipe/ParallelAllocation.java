package com.gtlmodular.common.machine.independent.recipe;

public record ParallelAllocation(long parallels, long budget) {
    public ParallelAllocation {
        if (parallels < 0 || budget < 0 || (parallels == 0 && budget != 0)) {
            throw new IllegalArgumentException("Invalid parallel allocation");
        }
    }

    public static long totalBudget(int maxParallel, int threads) {
        return totalBudget((long) maxParallel, threads);
    }

    /** long 上限用于巨构级机器；乘爆时按上限饱和，不能回绕成负数或截到 int。 */
    public static long totalBudget(long maxParallel, int threads) {
        if (maxParallel < 0 || threads < 0) throw new IllegalArgumentException("Negative parallel limit");
        if (maxParallel == 0L || threads == 0) return 0L;
        return maxParallel > Long.MAX_VALUE / threads ? Long.MAX_VALUE : maxParallel * threads;
    }

    public static ParallelAllocation mutable(long parallels, long consumption, long remaining) {
        var allocation = new ParallelAllocation(parallels, consumption);
        if (remaining < 0 || consumption > remaining) throw new IllegalArgumentException("Parallel budget exceeded");
        return allocation;
    }
}
