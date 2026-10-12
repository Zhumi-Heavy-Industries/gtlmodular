package com.gtlmodular.common.machine.nanochip;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.gtlmodular.common.config.NanochipConfig;
import com.gtlmodular.common.data.nanochip.NanochipModContext;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.ForgeConfigSpec;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;

import static com.gtlmodular.common.machine.nanochip.NanochipCalibration.Family.*;
import static org.junit.jupiter.api.Assertions.*;

class NanochipCalibrationTest {
    private static NanochipConfig config;

    @BeforeAll
    static void bindConfig() {
        var builder = new ForgeConfigSpec.Builder();
        config = new NanochipConfig(builder, "gtlmodular");
        builder.build().setConfig(CommentedConfig.inMemory());
        NanochipModContext.bind(new NanochipModContext("gtlmodular", "gtlmodular",
                () -> { throw new AssertionError("Calibration must not register machines"); },
                () -> { throw new AssertionError("Calibration must not register items"); },
                LoggerFactory.getLogger(NanochipCalibrationTest.class), () -> config, () -> false));
    }

    @Test
    void defaultWindowReachesTiersAndAppliesOnlyTheActiveFamilyDuration() {
        var calibration = new NanochipCalibration();
        assertEquals(0, calibration.activeTier());
        calibration.record(PRIMITIVE, 80);
        calibration.record(CRYSTAL, 20);
        assertEquals(0.2D, calibration.percentage(CRYSTAL));
        assertEquals(1, calibration.tierOf(CRYSTAL));
        assertEquals(0.8D, calibration.euMultiplier());
        assertEquals(1D, calibration.durationMultiplier(CRYSTAL));
        calibration.record(CRYSTAL, 60);
        assertEquals(2, calibration.tierOf(CRYSTAL));
        assertEquals(0.8D, calibration.durationMultiplier(CRYSTAL));
        assertEquals(1D, calibration.durationMultiplier(OPTICAL));
        calibration.record(CRYSTAL, 40);
        assertEquals(3, calibration.tierOf(CRYSTAL));
        assertTrue(calibration.specialActive(CRYSTAL));
    }

    @Test
    void specialFamiliesUseDurationWithoutEnergyDiscount() {
        var calibration = new NanochipCalibration();
        calibration.record(PRIMITIVE, 9900);
        calibration.record(COSMIC, 100);
        assertEquals(1, calibration.tierOf(COSMIC));
        assertEquals(1D, calibration.euMultiplier());
        assertEquals(0.8D, calibration.durationMultiplier(COSMIC));
        calibration.record(COSMIC, 500);
        assertEquals(2, calibration.tierOf(COSMIC));
        assertEquals(0.6D, calibration.durationMultiplier(COSMIC));
        calibration.record(COSMIC, 2000);
        assertEquals(3, calibration.tierOf(COSMIC));
        assertEquals(0.5D, calibration.durationMultiplier(COSMIC));
    }

    @Test
    void mergedEnergyKeepsEveryTierAndEveryRecipeFamily() {
        for (var active : List.of(CRYSTAL, WETWARE, BIOWARE, OPTICAL, EXOTIC, COSMIC, TEMPORAL)) {
            for (int tier = 1; tier <= 3; tier++) {
                var calibration = new NanochipCalibration();
                int count = (int) Math.round(active.threshold(tier) * 100000);
                calibration.record(PRIMITIVE, 100000 - count);
                calibration.record(active, count);
                assertEquals(tier, calibration.activeTier());
                double eu = active.specialStyleEffects() ? 1D : 0.8D;
                double duration = active.specialStyleEffects()
                        ? new double[] { 1D, 0.8D, 0.6D, 0.5D }[tier]
                        : tier >= 2 ? 0.8D : 1D;
                for (var recipeFamily : NanochipCalibration.Family.values()) {
                    double expected = 100000D * eu * (recipeFamily == active ? duration : 1D);
                    assertEquals(expected, NanochipCalibrationEnergy.totalEu(100000D, calibration, recipeFamily),
                            1e-8, active + " tier " + tier + " recipe " + recipeFamily);
                }
            }
        }
    }

    @Test
    void mergedEnergyWeightsEachRecipeBeforeSummingUnequalParallels() {
        var calibration = new NanochipCalibration();
        calibration.record(PRIMITIVE, 60);
        calibration.record(CRYSTAL, 40);
        double crystal = NanochipCalibrationEnergy.totalEu(10000D * 3, calibration, CRYSTAL);
        double optical = NanochipCalibrationEnergy.totalEu(1000D * 7, calibration, OPTICAL);
        assertEquals(19200D, crystal, 1e-8);
        assertEquals(5600D, optical, 1e-8);
        assertEquals(24800D, crystal + optical, 1e-8);
        assertEquals(37000D, NanochipCalibrationEnergy.totalEu(37000D, new NanochipCalibration(), CRYSTAL));
    }

    @Test
    void laserEnergyCombinesExistingAmperageAndTierDivisors() {
        assertEquals(1D, NanochipCalibrationEnergy.laserMultiplier(0, 0));
        assertEquals(1D, NanochipCalibrationEnergy.laserMultiplier(256, 6));
        assertEquals(0.5D, NanochipCalibrationEnergy.laserMultiplier(512, 9));
        assertEquals(1D / 1024D, NanochipCalibrationEnergy.laserMultiplier(65536, 13));
    }

    @Test
    void ringSurvivesRoundTripAndEvictsOnlyTheOldestCircuits() {
        var calibration = new NanochipCalibration();
        calibration.record(CRYSTAL, 100000);
        calibration.record(OPTICAL, 1000);
        var restored = new NanochipCalibration();
        restored.load(calibration.save());
        assertEquals(100000, restored.total());
        assertEquals(99000, restored.countOf(CRYSTAL));
        assertEquals(1000, restored.countOf(OPTICAL));
        assertEquals(calibration.activeTier(), restored.activeTier());
        restored.record(OPTICAL, Integer.MAX_VALUE);
        assertEquals(100000, restored.countOf(OPTICAL));
        assertEquals(0, restored.countOf(CRYSTAL));
        assertEquals(100000, restored.clear());
        assertEquals(0, restored.total());
    }

    @Test
    void fullWindowUsesOneHundredThousandEvenBeforeItIsFilled() {
        assertEquals(100000D, NanochipCalibration.denominatorFor(8, true));
        assertEquals(8D, NanochipCalibration.denominatorFor(8, false));
        assertEquals(0D, NanochipCalibration.denominatorFor(-1, false));
        config.setNanochipCalibrationUseFullWindow(true);
        try {
            var calibration = new NanochipCalibration();
            calibration.record(CRYSTAL, 19999);
            assertEquals(0, calibration.tierOf(CRYSTAL));
            calibration.record(CRYSTAL);
            assertEquals(1, calibration.tierOf(CRYSTAL));
            calibration.record(CRYSTAL, 20000);
            assertEquals(2, calibration.tierOf(CRYSTAL));
            calibration.record(CRYSTAL, 20000);
            assertEquals(3, calibration.tierOf(CRYSTAL));
        } finally {
            config.setNanochipCalibrationUseFullWindow(false);
        }
    }

    @Test
    void whitelistAcceptsBothProductNamespacesButRejectsMergedAndConversionIds() {
        for (String namespace : List.of("gtlmodular", "gtl_nac")) {
            List<String> whitelist = List.of(namespace + ":nanochip/assembly_matrix/*");
            assertTrue(NanochipCalibration.whitelistMatches(
                    new ResourceLocation(namespace, "nanochip/assembly_matrix/002_cc_crystal_processor"), whitelist));
            assertFalse(NanochipCalibration.whitelistMatches(new ResourceLocation("gtceu", "dummy/raw"), whitelist));
            assertFalse(NanochipCalibration.whitelistMatches(
                    new ResourceLocation(namespace, "nanochip/conversion/cc_crystal_processor"), whitelist));
        }
    }

    @Test
    void activeFamilyUsesTierThenCountAndExcludesMergedAwayFamily() {
        var calibration = new NanochipCalibration();
        calibration.record(CRYSTAL, 3000);
        calibration.record(COSMIC, 2000);
        calibration.record(PRIMITIVE, 5000);
        assertEquals(1, calibration.tierOf(CRYSTAL));
        assertEquals(3, calibration.tierOf(COSMIC));
        assertEquals(COSMIC, calibration.activeFamily());
        calibration.clear();
        calibration.record(CRYSTAL, 4000);
        calibration.record(OPTICAL, 5000);
        calibration.record(PRIMITIVE, 1000);
        assertEquals(OPTICAL, calibration.activeFamily());
        calibration.clear();
        calibration.record(SPECIAL, 100000);
        assertNull(calibration.activeFamily());
        assertEquals(0, calibration.activeTier());
    }

    @Test
    void malformedRingCannotRestorePhantomCalibration() {
        var calibration = new NanochipCalibration();
        calibration.record(CRYSTAL, 100000);
        CompoundTag corrupt = calibration.save();
        corrupt.putIntArray("RingRLE", new int[] { (100001 << 4) | CRYSTAL.ordinal() });
        calibration.load(corrupt);
        assertEquals(0, calibration.total());
        assertEquals(0, calibration.activeTier());
        assertEquals(0, calibration.countOf(CRYSTAL));
    }
}
