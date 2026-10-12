package com.gtlmodular.common.machine.independent.control;

public record IndependentHandoff(boolean runIndependent, boolean admit, boolean blockUpstreamStarts,
                                 boolean externalConflict) {
    public static IndependentHandoff decide(boolean owned, boolean eligible, boolean supported, boolean multipleMode,
                                            boolean upstreamPending, boolean externalRequested, boolean externalOwned) {
        boolean admission = eligible && supported && multipleMode && !externalRequested && !externalOwned;
        if (owned) return new IndependentHandoff(true, admission, true, externalOwned);
        if (!admission) {
            return new IndependentHandoff(false, false, false, false);
        }
        // 已扣料的旧单批继续走原完成分支，但该分支中的下一次合并必须被拦在扣料之前。
        return new IndependentHandoff(!upstreamPending, admission, true, false);
    }
}
