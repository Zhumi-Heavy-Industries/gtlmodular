package com.gtlmodular.common.machine.nanochip;

import org.junit.jupiter.api.Test;

import java.util.List;

import static com.gtlmodular.common.machine.nanochip.NanochipCalibration.Family.*;
import static org.junit.jupiter.api.Assertions.*;

class NanochipCalibrationFluidsTest {

    @Test
    void wetwareExemptsGrowthMediaAndBiowareAlsoExemptsBiologicalMedia() {
        for (String fluid : List.of("gtceu:raw_growth_medium", "gtceu:sterilized_growth_medium")) {
            assertTrue(NanochipCalibrationFluids.exemptsMedium(WETWARE, fluid));
            assertTrue(NanochipCalibrationFluids.exemptsMedium(BIOWARE, fluid));
        }
        for (String fluid : List.of("gtceu:biomediumraw", "gtceu:biohmediumsterilized")) {
            assertFalse(NanochipCalibrationFluids.exemptsMedium(WETWARE, fluid));
            assertTrue(NanochipCalibrationFluids.exemptsMedium(BIOWARE, fluid));
        }
    }

    @Test
    void unrelatedFluidsAndInactiveFamiliesStillRequireTheirInputs() {
        for (var family : values()) {
            assertFalse(NanochipCalibrationFluids.exemptsMedium(family, "gtceu:grade_1_purified_water"));
            assertFalse(NanochipCalibrationFluids.exemptsMedium(family, "pack:raw_growth_medium"));
            assertFalse(NanochipCalibrationFluids.exemptsMedium(family, null));
            if (family != WETWARE && family != BIOWARE) {
                assertFalse(NanochipCalibrationFluids.exemptsMedium(family, "gtceu:raw_growth_medium"));
            }
        }
        assertFalse(NanochipCalibrationFluids.exemptsMedium(null, "gtceu:raw_growth_medium"));
    }
}
