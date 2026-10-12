package com.gtlmodular.common.machine.independent.recipe;

public final class FairParallelAllocation {
    private FairParallelAllocation() {}

    public static long[] allocate(long remaining, long[] demands) {
        if (remaining < 0) throw new IllegalArgumentException("Negative budget");
        long[] allocated = new long[demands.length];
        if (demands.length == 0) return allocated;
        long share = remaining / demands.length;
        int wanting = 0;
        for (int i = 0; i < demands.length; i++) {
            if (demands[i] < 0) throw new IllegalArgumentException("Negative demand");
            allocated[i] = Math.min(demands[i], share);
            remaining -= allocated[i];
            if (demands[i] > allocated[i]) wanting++;
        }
        // 上游在余量不足以给每个待分配候选一份时停止，不能擅自把尾数送给首项。
        while (wanting > 0 && (share = remaining / wanting) > 0) {
            int nextWanting = 0;
            for (int i = 0; i < demands.length; i++) {
                long extra = Math.min(demands[i] - allocated[i], share);
                allocated[i] += extra;
                remaining -= extra;
                if (demands[i] > allocated[i]) nextWanting++;
            }
            wanting = nextWanting;
        }
        return allocated;
    }
}
