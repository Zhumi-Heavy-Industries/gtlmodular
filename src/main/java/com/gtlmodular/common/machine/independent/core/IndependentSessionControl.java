package com.gtlmodular.common.machine.independent.core;

public record IndependentSessionControl(boolean admissionAllowed, boolean upstreamOwnsWork,
                                        boolean paused, boolean structureValid, int slots, long budgetLimit) {
    public IndependentSessionControl {
        if (slots < 0 || budgetLimit < 0) {
            throw new IllegalArgumentException("Negative admission limit");
        }
    }

    public boolean canRun() {
        return !paused && structureValid && !upstreamOwnsWork;
    }
}
