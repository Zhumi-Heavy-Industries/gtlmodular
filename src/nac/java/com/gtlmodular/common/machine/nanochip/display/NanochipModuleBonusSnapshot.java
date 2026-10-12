package com.gtlmodular.common.machine.nanochip.display;

import com.gtlmodular.common.machine.nanochip.NanochipCalibration.Family;

import java.util.List;

/** 显示只持有取样结果，不能借客户端刷新重新计算或修改正在运行的配方。 */
public record NanochipModuleBonusSnapshot(List<Module> modules, List<Total> totals, boolean fullMode,
                                         int tier, int baseThreads, int additionalThreads, int totalThreads,
                                         int floorTicks, int directParallelLimit) {
    public NanochipModuleBonusSnapshot {
        modules = List.copyOf(modules);
        totals = List.copyOf(totals);
    }

    public record Module(String id, String nameKey, String fallbackName, String position, boolean formed,
                         List<Family> families, double eu, double duration, Family organizerFamily,
                         double waterStrength, double effectiveWaterStrength, boolean fullCalibration, boolean board, double impurity,
                         double impurityMultiplier, boolean unlocksFullMode, double parallelFactor, int parallelCount) {
        public Module { families = List.copyOf(families); }
    }

    public record Total(Family family, double rawEu, double rawDuration, double eu, double duration) {}
}
