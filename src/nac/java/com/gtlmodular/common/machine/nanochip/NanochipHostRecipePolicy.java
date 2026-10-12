package com.gtlmodular.common.machine.nanochip;

import com.gtlmodular.common.machine.nanochip.registration.NanochipModuleSpec;
import com.gtlmodular.common.data.nanochip.NanochipModContext;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gtlmodular.common.data.nanochip.NanochipRecipeTypes;
import org.gtlcore.gtlcore.api.machine.multiblock.IModularMachineModule;

import java.util.function.Supplier;

import static com.gtlmodular.common.machine.nanochip.NanochipAssemblyComplexMachine.NAC_MULTI_TYPE_RECIPES;
import static com.gtlmodular.common.machine.nanochip.NanochipAssemblyComplexMachine.FULL_MIN_DURATION_TICKS;
import static com.gtlmodular.common.machine.nanochip.NanochipAssemblyComplexMachine.DIRECT_MIN_DURATION_TICKS;
import static com.gtlmodular.common.machine.nanochip.NanochipAssemblyComplexMachine.MAX_CROSS_RECIPE_THREADS;
import static com.gtlmodular.common.machine.nanochip.NanochipAssemblyComplexMachine.NAC_SATURATING_PARALLEL;
import static com.gtlmodular.common.machine.nanochip.NanochipAssemblyComplexMachine.FULL_PARALLEL_CAP;
import static com.gtlmodular.common.machine.nanochip.NanochipAssemblyComplexMachine.LEGACY_CROSS_RECIPE_PARALLEL_CAP;
import static com.gtlmodular.common.machine.nanochip.NanochipAssemblyComplexMachine.MAX_OVERCLOCKS;

final class NanochipHostRecipePolicy {

    private final NanochipAssemblyComplexMachine host;
    private final NanochipCalibration calibration;
    private final Supplier<GTRecipeType[]> baseRecipeTypes;

    /** 限制重复模块的加成，避免直通档靠堆叠达到全流程档上限。 */
    private static final int PARALLEL_STACK_CAP = 2;

    /** 换算表追加在末尾，使组装表的模式下标保持稳定。 */
    private GTRecipeType[] simpleModeRecipeTypes;

    private long lastRecipeBuildLogTick = Long.MIN_VALUE;

    NanochipHostRecipePolicy(NanochipAssemblyComplexMachine host, NanochipCalibration calibration,
                            Supplier<GTRecipeType[]> baseRecipeTypes) {
        this.host = host;
        this.calibration = calibration;
        this.baseRecipeTypes = baseRecipeTypes;
    }

    GTRecipeType[] getRecipeTypes() {
        if (!NanochipModContext.config().isNanochipSimpleLogistics()) {
            return baseRecipeTypes.get();
        }
        if (simpleModeRecipeTypes == null) {
            GTRecipeType[] base = baseRecipeTypes.get();
            GTRecipeType[] extra = new GTRecipeType[base.length + 1];
            System.arraycopy(base, 0, extra, 0, base.length);
            extra[base.length] = NanochipRecipeTypes.CONVERSION;
            simpleModeRecipeTypes = extra;
        }
        return simpleModeRecipeTypes;
    }

    /** 父类按私有类型数组取当前模式；扩展列表后必须同步覆写此入口，避免下标越界。 */
    GTRecipeType getRecipeType() {
        GTRecipeType[] types = host.getRecipeTypes();
        if (types.length == 0) {
            return null;
        }
        int idx = host.getActiveRecipeType();
        if (idx < 0) {
            idx = 0;
        } else if (idx >= types.length) {
            idx = types.length - 1;
        }
        return types[idx];
    }

    GTRecipeType[] nanochipSearchTypes() {
        if (!NAC_MULTI_TYPE_RECIPES) {
            GTRecipeType active = host.getRecipeType();
            return active == null ? new GTRecipeType[0] : new GTRecipeType[] { active };
        }
        return host.getRecipeTypes();
    }

    boolean fullMode() {
        for (IModularMachineModule<NanochipAssemblyComplexMachine, ?> m : host.getModules()) {
            if (m instanceof NanochipModuleMachine module
                    && module.isFormed()
                    && module.getModuleSpec().unlocksFullMode()) {
                return true;
            }
        }
        return false;
    }

    int minDurationTicks() {
        return host.fullMode() ? FULL_MIN_DURATION_TICKS : DIRECT_MIN_DURATION_TICKS;
    }

    int directParallelCap() {
        return Math.max(1, NanochipModContext.config().getNanochipDirectParallelCap());
    }

    int directThreads() {
        return Math.max(1, Math.min(16, NanochipModContext.config().getNanochipDirectCrossRecipeThreads()));
    }

    int baseCrossRecipeThreads() {
        if (!host.isFormed()) {
            return 1;
        }
        if (!host.fullMode()) {
            return host.directThreads();
        }
        // 先用 long 移位，避免高电压档的线程数在 int 中回绕。
        long threads = 2L << Math.min(30, Math.max(0, host.getTier() - GTValues.LV));
        return (int) Math.min(MAX_CROSS_RECIPE_THREADS, threads);
    }

    int getMultipleThreads() {
        long total = (long) host.baseCrossRecipeThreads() + host.getAdditionalThread();
        return (int) Math.max(1L, Math.min(MAX_CROSS_RECIPE_THREADS, total));
    }

    int getMaxParallel() {
        if (!host.isFormed()) {
            return 1;
        }
        if (host.fullMode()) {
            return NAC_SATURATING_PARALLEL ? FULL_PARALLEL_CAP : LEGACY_CROSS_RECIPE_PARALLEL_CAP;
        }
        long parallel = host.directParallelCap();
        for (NanochipModuleSpec kind : com.gtlmodular.common.machine.nanochip.registration.NanochipModules.all()) {
            int count = Math.min(PARALLEL_STACK_CAP, host.countModulesById(kind.id()));
            for (int i = 0; i < count; i++) {
                parallel = NanochipModulePowerDistribution.saturatingMul(parallel, (long) kind.parallelFactor());
            }
        }
        return Math.max(1, NanochipAssemblyComplexMachine.saturatingToInt(parallel));
    }

    double moduleEuMultiplier(NanochipCalibration.Family family) {
        double mul = 1.0D;
        for (IModularMachineModule<NanochipAssemblyComplexMachine, ?> m : host.getModules()) {
            if (!(m instanceof NanochipModuleMachine module) || !module.isFormed()) {
                continue;
            }
            NanochipModuleSpec kind = module.getModuleSpec();
            if (!kind.appliesTo(family) || kind.euFactor() >= 1.0D) {
                continue;
            }
            double factor = kind.euFactor();
            if (kind.isWaterScaledOrganizer()) {
                double strength = module.waterStrength();
                if (strength <= 0.0D) {
                    continue;
                }
                NanochipCalibration.Family waterFamily = kind.organizerFamily();
                if (waterFamily != null && calibration.specialActive(waterFamily)) {
                    strength *= 2.0D;
                }
                factor = Math.pow(factor, Math.min(4.0D, strength));
            } else if (kind.features().board()) {
                factor = factor * module.impurityEuMultiplier();
            }
            mul *= factor;
        }
        return Math.max(0.10D, Math.min(10.0D, mul));
    }

    /** 激光加成作用于蚀刻模块自身加工，不能在主机倍率里再计算一次。 */
    double moduleDurationMultiplier(NanochipCalibration.Family family) {
        double mul = 1.0D;
        for (IModularMachineModule<NanochipAssemblyComplexMachine, ?> m : host.getModules()) {
            if (!(m instanceof NanochipModuleMachine module) || !module.isFormed()) {
                continue;
            }
            NanochipModuleSpec kind = module.getModuleSpec();
            if (!kind.appliesTo(family) || kind.durationFactor() >= 1.0D) {
                continue;
            }
            double factor = kind.durationFactor();
            if (kind.isWaterScaledOrganizer()) {
                double strength = module.waterStrength();
                if (strength <= 0.0D) {
                    continue;
                }
                NanochipCalibration.Family waterFamily = kind.organizerFamily();
                if (waterFamily != null && calibration.specialActive(waterFamily)) {
                    strength *= 2.0D;
                }
                factor = Math.pow(factor, Math.min(4.0D, strength));
            }
            mul *= factor;
        }
        return Math.max(0.05D, mul);
    }

    GTRecipe buildRecipe(GTRecipe recipe) {
        long maxVoltage = host.getOverclockVoltage();
        if (maxVoltage <= 0L) {
            return null;
        }
        GTRecipe copy = recipe.copy();
        long eut = Math.max(1L, RecipeHelper.getInputEUt(copy));
        int duration = Math.max(1, copy.duration);
        int floorTicks = host.minDurationTicks();

        int overclocks = 0;
        while (overclocks < MAX_OVERCLOCKS
                && eut <= maxVoltage / 4L
                && duration / 2 >= floorTicks) {
            eut *= 4L;
            duration /= 2;
            overclocks++;
        }

        NanochipCalibration.Family family = NanochipAssemblyComplexMachine.familyOf(recipe);

        double euMul = calibration.euMultiplier() * host.moduleEuMultiplier(family);
        double durMul = calibration.durationMultiplier(family) * host.moduleDurationMultiplier(family);

        long newEut = (long) Math.max(1.0D, Math.min((double) Long.MAX_VALUE, eut * euMul));
        int newDuration = (int) Math.max(floorTicks, Math.min(Integer.MAX_VALUE, Math.round(duration * durMul)));

        RecipeHelper.setInputEUt(copy, newEut);
        copy.duration = newDuration;
        copy.ocTier = overclocks;
        if (NanochipModContext.recipeDiagnosticsEnabled()) {
            long nowTick = host.getLevel() == null ? 0L : host.getLevel().getGameTime();
            if (nowTick - lastRecipeBuildLogTick >= 20L) {
                lastRecipeBuildLogTick = nowTick;
                NanochipCalibration.Family active = calibration.activeFamily();
                NanochipModContext.logger().info("[gtlmodular] NAC 配方定档 @{}: 本配方族={} 生效族={}(第{}档) "
                                + "超频={}次 EUt {}→{} 时长 {}→{}t（下限{}t） "
                                + "倍率[校准EU×{} 模块EU×{} ⇒ ×{}；校准时长×{} 模块时长×{} ⇒ ×{}] ⇒ 最终 {}t（{}.{}s）",
                        host.getPos(), family, active == null ? "无" : active,
                        active == null ? 0 : calibration.tierOf(active),
                        overclocks, eut, newEut,
                        Math.max(1, copy.duration == 0 ? 0 : duration), newDuration, floorTicks,
                        calibration.euMultiplier(), host.moduleEuMultiplier(family), euMul,
                        calibration.durationMultiplier(family), host.moduleDurationMultiplier(family), durMul,
                        newDuration, newDuration / 20, newDuration % 20);
            }
        }
        return copy;
    }
}
