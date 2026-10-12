package com.gtlmodular.common.machine.independent.recipe;

import java.math.BigInteger;

public final class MutableBatchEnergy {
    private MutableBatchEnergy() {}

    public static SingleBatchEnergy.Normal normal(long eut, int duration, long parallels, double euMultiplier, long voltage) {
        return SingleBatchEnergy.normal((double) eut * duration, parallels, euMultiplier, voltage, 20);
    }

    public static BigInteger wirelessTotal(long eut, int duration, long parallels, double euMultiplier) {
        return SingleBatchEnergy.wirelessTotal(eut, duration, parallels, euMultiplier, true);
    }
}
