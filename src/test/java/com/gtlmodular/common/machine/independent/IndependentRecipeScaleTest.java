package com.gtlmodular.common.machine.independent;

import com.gtlmodular.common.machine.independent.recipe.IndependentRecipeScale;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IndependentRecipeScaleTest {

    @Test
    void exactMultiplierKeepsCountsBeyondDoubleIntegerRange() {
        assertEquals(9_000_000_000L, IndependentRecipeScale.multiplyExact(3L, 3_000_000_000L));
        assertEquals(6_000_000_000_000_000_000L,
                IndependentRecipeScale.multiplyExact(3L, 2_000_000_000_000_000_000L));
    }

    @Test
    void overflowIsRejectedInsteadOfSaturatingToAFakeCount() {
        assertThrows(IllegalArgumentException.class,
                () -> IndependentRecipeScale.multiplyExact(3L, Long.MAX_VALUE / 3L + 1L));
        assertThrows(IllegalArgumentException.class,
                () -> IndependentRecipeScale.multiplyExact(0L, 1L));
        assertThrows(IllegalArgumentException.class,
                () -> IndependentRecipeScale.multiplyExact(1L, 0L));
    }
}
