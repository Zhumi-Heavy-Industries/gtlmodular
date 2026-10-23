package com.gtlmodular.common.machine.independent;

import com.gtlmodular.common.machine.independent.recipe.BatchRunLimits;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BatchRunLimitsTest {

    @Test
    void batchClockConvertsPowerAndPerRunEnergyBackToRuns() {
        assertEquals(10_000_000_000_000L, BatchRunLimits.timeCap(1.0D, 1_000_000_000L, 10_000));
        assertEquals(Long.MAX_VALUE, BatchRunLimits.timeCap(0.0D, 1_000_000_000L, 10_000));
        assertEquals(Long.MAX_VALUE, BatchRunLimits.timeCap(1.0D, 0L, 10_000));
        assertEquals(Long.MAX_VALUE, BatchRunLimits.timeCap(1.0D, 1_000_000_000L, 0));
        assertEquals(Long.MAX_VALUE, BatchRunLimits.timeCap(Double.NaN, 1_000_000_000L, 10_000));
    }

    @Test
    void capTakesTheStrictestOfClockRunAndOverflowLimits() {
        assertEquals(100L, BatchRunLimits.cap(100L, 1.0D, 1_000_000_000L, 10_000, 0L, 1L));
        assertEquals(10_000_000_000_000L,
                BatchRunLimits.cap(Long.MAX_VALUE, 1.0D, 1_000_000_000L, 10_000, 0L, 1L));
        assertEquals(5L, BatchRunLimits.cap(Long.MAX_VALUE, 0.0D, 0L, 0, 5L, 0L));
        assertEquals(Long.MAX_VALUE / 10L,
                BatchRunLimits.cap(Long.MAX_VALUE, 0.0D, 0L, 0, 0L, 10L));
        assertEquals(0L, BatchRunLimits.cap(0L, 1.0D, 1_000_000_000L, 10_000, 0L, 1L));
    }
}
