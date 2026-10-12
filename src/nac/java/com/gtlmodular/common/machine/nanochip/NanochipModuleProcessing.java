package com.gtlmodular.common.machine.nanochip;

import com.gtlmodular.common.machine.nanochip.registration.NanochipModuleSpec;
import com.gtlmodular.common.data.nanochip.NanochipModContext;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.OverclockingLogic;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.content.ContentModifier;
import com.gregtechceu.gtceu.api.recipe.logic.OCParams;
import com.gregtechceu.gtceu.api.recipe.logic.OCResult;
import com.gregtechceu.gtceu.common.data.GTRecipeModifiers;
import com.gregtechceu.gtceu.utils.GTUtil;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;

import static com.gtlmodular.common.machine.nanochip.NanochipModuleMachine.MIN_DURATION_TICKS;
import static com.gtlmodular.common.machine.nanochip.NanochipModuleMachine.SIMPLE_LOGISTICS_OUTPUT_MULTIPLIER;

final class NanochipModuleProcessing {

    private final NanochipModuleMachine module;
    private final NanochipModuleSpec kind;
    private final NanochipModulePower power;
    private final DoubleSupplier readImpurity;
    private final DoubleConsumer writeImpurity;
    private final IntSupplier readFlushThreshold;

    private static final java.util.regex.Pattern PURIFIED_WATER =
            java.util.regex.Pattern.compile("grade_(\\d+)_purified_water");

    NanochipModuleProcessing(NanochipModuleMachine module, NanochipModuleSpec kind,
                             NanochipModulePower power, DoubleSupplier readImpurity,
                             DoubleConsumer writeImpurity, IntSupplier readFlushThreshold) {
        this.module = module;
        this.kind = kind;
        this.power = power;
        this.readImpurity = readImpurity;
        this.writeImpurity = writeImpurity;
        this.readFlushThreshold = readFlushThreshold;
    }

    double impurityEuMultiplier() {
        double y = readImpurity.getAsDouble();
        if (y <= 15.0D) {
            return 1.0D - Math.max(0.0D, (30.0D - 2.0D * y) / 100.0D);
        }
        if (y < 65.0D) {
            return 1.0D;
        }
        return 1.0D + (y - 65.0D) * 0.02D;
    }

    void onRecipeProduced() {
        if (!kind.features().board()) {
            return;
        }
        double step = NanochipModContext.config().getNanochipImpurityPerRecipe();
        if (step <= 0.0D) {
            return;
        }
        writeImpurity.accept(Math.min(100.0D, readImpurity.getAsDouble() + step));
        if (readImpurity.getAsDouble() >= readFlushThreshold.getAsInt()) {
            writeImpurity.accept(0.0D);
            if (module.getLevel() != null && !module.getLevel().isClientSide
                    && NanochipModContext.config().isNanochipDebugLogsEnabled()) {
                NanochipModContext.logger().info("[gtlmodular] 全板浸没装置自动排液 @{}（阈值 {}%）",
                        module.getPos(), readFlushThreshold.getAsInt());
            }
        }
    }

    int highestPurifiedWaterGrade() {
        int best = 0;
        for (com.lowdragmc.lowdraglib.side.fluid.FluidStack fs
                : NanochipModuleUpkeep.scanInputFluids(module)) {
            var id = net.minecraftforge.registries.ForgeRegistries.FLUIDS.getKey(fs.getFluid());
            if (id == null) {
                continue;
            }
            java.util.regex.Matcher m = PURIFIED_WATER.matcher(id.getPath());
            if (m.matches()) {
                best = Math.max(best, Integer.parseInt(m.group(1)));
            }
        }
        return best;
    }

    double waterStrength() {
        int grade = module.highestPurifiedWaterGrade();
        if (grade <= 0) {
            return 0.0D;
        }
        return Math.min(2.0D, 1.0D + (grade - 1) * (1.0D / 15.0D));
    }

    int laserHatchTier() {
        int best = 0;
        for (var part : module.getParts()) {
            MetaMachine m = part.self();
            if (m instanceof com.gregtechceu.gtceu.common.machine.multiblock.part.LaserHatchPartMachine
                    && m instanceof com.gregtechceu.gtceu.api.machine.feature.ITieredMachine tiered) {
                best = Math.max(best, tiered.getTier());
            }
        }
        return best;
    }

    long laserAmperage() {
        int tier = module.laserHatchTier();
        return tier <= 0 ? 0L : (256L << (2 * Math.max(0, tier - 6)));
    }

    double calibratedTotalEu(GTRecipe recipe, double originalEu) {
        NanochipAssemblyComplexMachine host = module.getHost();
        if (host == null || !module.hostConnected()) {
            return originalEu;
        }
        NanochipCalibration calibration = host.getCalibration();
        NanochipCalibration.Family family = NanochipAssemblyComplexMachine.familyOf(recipe);
        double total = NanochipCalibrationEnergy.totalEu(originalEu, calibration, family);
        if (kind.features().laser() && calibration.specialActive(NanochipCalibration.Family.CRYSTAL)) {
            total *= NanochipCalibrationEnergy.laserMultiplier(module.laserAmperage(), module.laserHatchTier());
        }
        return total;
    }

    GTRecipe moduleOverclock(GTRecipe recipe) {
        long maxVoltage = module.getOverclockVoltage();
        if (maxVoltage <= 0L) {
            return null;
        }
        GTRecipe copy = recipe.copy();
        long eut = Math.max(1L, RecipeHelper.getInputEUt(copy));
        int duration = Math.max(1, copy.duration);
        int hatchTier = GTUtil.getTierByVoltage(maxVoltage);
        int recipeTier = GTUtil.getTierByVoltage(eut);
        int maxOc = Math.max(0, hatchTier - recipeTier);
        long powerCap = power.availableInputPower(maxVoltage);
        int oc = 0;
        while (oc < maxOc
                && duration / 2 >= MIN_DURATION_TICKS
                && eut <= powerCap / 2L) {
            eut *= 2L;
            duration /= 2;
            oc++;
        }
        int floorTicks = MIN_DURATION_TICKS;
        if (kind.features().assembly()) {
            NanochipAssemblyComplexMachine host = module.getHost();
            if (host != null && module.hostConnected()) {
                NanochipCalibration.Family family = NanochipAssemblyComplexMachine.familyOf(recipe);
                double euMul = host.getCalibration().euMultiplier() * host.moduleEuMultiplier(family);
                double durMul = host.getCalibration().durationMultiplier(family)
                        * host.moduleDurationMultiplier(family);
                eut = (long) Math.max(1.0D, Math.min((double) Long.MAX_VALUE, eut * euMul));
                duration = (int) Math.max(floorTicks,
                        Math.min(Integer.MAX_VALUE, Math.round(duration * durMul)));
                floorTicks = host.minDurationTicks();
            }
        }
        if (kind.features().laser()) {
            NanochipAssemblyComplexMachine host = module.getHost();
            if (host != null && module.hostConnected()
                    && host.getCalibration().specialActive(NanochipCalibration.Family.CRYSTAL)) {
                long amperage = module.laserAmperage();
                if (amperage >= 512L) {
                    eut = Math.max(1L, eut / (amperage / 256L));
                }
                int laserTier = module.laserHatchTier();
                if (laserTier > 9) {
                    duration = Math.max(floorTicks, duration / (laserTier - 9));
                }
            }
        }
        RecipeHelper.setInputEUt(copy, eut);
        copy.duration = Math.max(floorTicks, duration);
        copy.ocTier = oc;
        return copy;
    }

    static GTRecipe moduleFlexibleOverclock(MetaMachine machine, GTRecipe recipe,
                                            OCParams params, OCResult result) {
        GTRecipe direct = NanochipModuleMachine.moduleOverclock(machine, recipe, params, result);
        GTRecipe modified;
        if (direct != null) {
            modified = direct;
        } else {
            modified = GTRecipeModifiers.ELECTRIC_OVERCLOCK
                    .apply(OverclockingLogic.NON_PERFECT_OVERCLOCK_SUBTICK)
                    .apply(machine, recipe, params, result);
        }
        return modified != null ? modified : recipe;
    }

    static GTRecipe scaleOutputsForSimpleLogistics(MetaMachine machine, GTRecipe recipe) {
        if (recipe == null || !NanochipModContext.config().isNanochipSimpleLogistics()) {
            return recipe;
        }
        if (!(machine instanceof NanochipModuleMachine module)
                || !module.getModuleSpec().features().assembly()) {
            return recipe;
        }
        if (SIMPLE_LOGISTICS_OUTPUT_MULTIPLIER >= 1.0D) {
            return recipe;
        }
        GTRecipe copy = recipe.copy();
        java.util.Map<RecipeCapability<?>, java.util.List<Content>> scaled =
                recipe.copyContents(recipe.outputs,
                        ContentModifier.multiplier(SIMPLE_LOGISTICS_OUTPUT_MULTIPLIER));
        copy.outputs.clear();
        copy.outputs.putAll(scaled);
        return copy;
    }
}
