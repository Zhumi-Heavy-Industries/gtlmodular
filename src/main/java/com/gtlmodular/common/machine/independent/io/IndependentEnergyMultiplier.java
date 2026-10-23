package com.gtlmodular.common.machine.independent.io;

/** 机器可对每 tick 的配方 EU 再乘一个系数；地心模块用它把枢纽线圈折扣带进引擎供电。 */
public interface IndependentEnergyMultiplier {
    double independentEnergyMultiplier();
}
