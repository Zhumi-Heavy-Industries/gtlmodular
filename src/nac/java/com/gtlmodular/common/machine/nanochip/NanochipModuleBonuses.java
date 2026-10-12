package com.gtlmodular.common.machine.nanochip;

import com.gtlmodular.common.machine.nanochip.display.NanochipModuleBonusSnapshot;
import com.gtlmodular.common.machine.nanochip.registration.NanochipModuleSpec;
import com.gtlmodular.common.machine.nanochip.registration.NanochipModules;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/** 条件只采样一次，避免两种倍率和明细分别扫描流体库存后得到不同口径。 */
final class NanochipModuleBonuses {
    private static final int PARALLEL_STACK_CAP = 2;

    record Sample(NanochipModuleSpec spec, BlockPos position, boolean formed, double waterStrength,
                  boolean fullCalibration, double impurity, double impurityMultiplier) {}

    record Contribution(Sample sample, double eu, double duration, double effectiveWaterStrength) {}

    record Multipliers(double rawEu, double rawDuration) {
        double eu() { return Math.max(0.10D, Math.min(10.0D, rawEu)); }
        double duration() { return Math.max(0.05D, rawDuration); }
    }

    private final List<Contribution> contributions;

    NanochipModuleBonuses(List<Sample> samples) {
        List<Contribution> rows = new ArrayList<>();
        for (Sample sample : samples) {
            NanochipModuleSpec spec = sample.spec();
            double eu = 1.0D;
            double duration = 1.0D;
            boolean watered = !spec.isWaterScaledOrganizer() || !(sample.waterStrength() <= 0.0D);
            double strength = Math.min(4.0D, sample.waterStrength() * (sample.fullCalibration() ? 2.0D : 1.0D));
            if (sample.formed() && watered) {
                if (spec.euFactor() < 1.0D) {
                    eu = spec.euFactor();
                    if (spec.isWaterScaledOrganizer()) {
                        eu = Math.pow(eu, strength);
                    } else if (spec.features().board()) {
                        eu = eu * sample.impurityMultiplier();
                    }
                }
                // 激光只作用于模块自身加工，主机时长只读取规格中的普通倍率。
                if (spec.durationFactor() < 1.0D) {
                    duration = spec.isWaterScaledOrganizer() ? Math.pow(spec.durationFactor(), strength)
                            : spec.durationFactor();
                }
            }
            rows.add(new Contribution(sample, eu, duration, strength));
        }
        contributions = List.copyOf(rows);
    }

    static NanochipModuleBonuses capture(NanochipAssemblyComplexMachine host, NanochipCalibration calibration) {
        List<Sample> samples = new ArrayList<>();
        for (var candidate : host.getModules()) {
            if (!(candidate instanceof NanochipModuleMachine module)) continue;
            NanochipModuleSpec spec = module.getModuleSpec();
            boolean formed = module.isFormed();
            boolean organizer = formed && spec.isWaterScaledOrganizer();
            boolean board = formed && spec.features().board();
            samples.add(new Sample(spec, module.getPos().immutable(), formed,
                    organizer ? module.waterStrength() : 0.0D,
                    organizer && calibration.specialActive(spec.organizerFamily()),
                    board ? module.impurity() : 0.0D, board ? module.impurityEuMultiplier() : 1.0D));
        }
        return new NanochipModuleBonuses(samples);
    }

    Multipliers multipliers(NanochipCalibration.Family family) {
        double eu = 1.0D;
        double duration = 1.0D;
        // 保持主机集合的乘法顺序，避免浮点误差改变最终取整后的 EU/t 或时长。
        for (Contribution contribution : contributions) {
            Sample sample = contribution.sample();
            if (!sample.formed() || !sample.spec().appliesTo(family)) continue;
            eu *= contribution.eu();
            duration *= contribution.duration();
        }
        return new Multipliers(eu, duration);
    }

    /** 并行仍按目录顺序截断、饱和；显示不能把规格的小数倍率当作实际整数倍率。 */
    static int directParallelLimit(int base, ToIntFunction<NanochipModuleSpec> moduleCount) {
        long parallel = base;
        for (NanochipModuleSpec spec : NanochipModules.all()) {
            int count = Math.min(PARALLEL_STACK_CAP, moduleCount.applyAsInt(spec));
            for (int i = 0; i < count; i++) {
                parallel = NanochipModulePowerDistribution.saturatingMul(parallel, (long) spec.parallelFactor());
            }
        }
        return Math.max(1, NanochipAssemblyComplexMachine.saturatingToInt(parallel));
    }

    private int count(NanochipModuleSpec spec) {
        return (int) contributions.stream()
                .filter(c -> c.sample().formed() && c.sample().spec().id().equals(spec.id())).count();
    }

    NanochipModuleBonusSnapshot displaySnapshot(NanochipAssemblyComplexMachine host) {
        List<NanochipModuleBonusSnapshot.Module> modules = new ArrayList<>();
        for (Contribution contribution : contributions) {
            Sample sample = contribution.sample();
            NanochipModuleSpec spec = sample.spec();
            modules.add(new NanochipModuleBonusSnapshot.Module(spec.id().toString(),
                    Util.makeDescriptionId("block", spec.id()), spec.nameEn(), sample.position().toShortString(),
                    sample.formed(), spec.bonuses().familyFilter(), contribution.eu(), contribution.duration(),
                    spec.organizerFamily(), sample.waterStrength(), contribution.effectiveWaterStrength(), sample.fullCalibration(),
                    spec.features().board(), sample.impurity(), sample.impurityMultiplier(),
                    spec.unlocksFullMode(), spec.parallelFactor(), Math.min(PARALLEL_STACK_CAP, count(spec))));
        }
        List<NanochipModuleBonusSnapshot.Total> totals = new ArrayList<>();
        for (NanochipCalibration.Family family : NanochipCalibration.Family.values()) {
            Multipliers result = multipliers(family);
            totals.add(new NanochipModuleBonusSnapshot.Total(family, result.rawEu(), result.rawDuration(),
                    result.eu(), result.duration()));
        }
        int directLimit = directParallelLimit(host.directParallelCap(), this::count);
        return new NanochipModuleBonusSnapshot(modules, totals, host.fullMode(), host.getTier(),
                host.baseCrossRecipeThreads(), host.effectiveExtraThreads(), host.getMultipleThreads(),
                host.minDurationTicks(), directLimit);
    }
}
