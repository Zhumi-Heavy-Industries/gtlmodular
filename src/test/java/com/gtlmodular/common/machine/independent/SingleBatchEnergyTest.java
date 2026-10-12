package com.gtlmodular.common.machine.independent;

import com.gtlmodular.common.machine.independent.recipe.MutableBatchEnergy;
import com.gtlmodular.common.machine.independent.recipe.SingleBatchEnergy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.*;

class SingleBatchEnergyTest {
    @ParameterizedTest
    @CsvSource({"1999,20,99", "2000,20,100", "2049,20,100", "2050,21,100", "2051,21,100",
            "0,20,0", "-1999,20,-99"})
    void ordinaryUsesUpstreamRoundingAndSign(double totalEnergy, int duration, long eut) {
        assertEquals(new SingleBatchEnergy.Normal(duration, eut), SingleBatchEnergy.normal(totalEnergy, 1, 1, 100, 20));
    }

    @Test
    void maintenanceAndReductionApplyOnceInOriginalMultiplicationOrder() {
        double multiplier = 1.1 * 0.5 * 0.25;
        assertEquals(new SingleBatchEnergy.Normal(440, 100), SingleBatchEnergy.normal(40000, 8, multiplier, 100, 20));
        assertEquals(new SingleBatchEnergy.Normal(440, 100), MutableBatchEnergy.normal(200, 200, 8, multiplier, 100));
        assertEquals(BigInteger.valueOf(44000), MutableBatchEnergy.wirelessTotal(200, 200, 8, multiplier));
    }

    @Test
    void ordinarySaturatesDurationLikeKotlinInsteadOfOverflowing() {
        assertEquals(new SingleBatchEnergy.Normal(Integer.MAX_VALUE, 1), SingleBatchEnergy.normal(1, Long.MAX_VALUE, 1, 1, 20));
        assertEquals(new SingleBatchEnergy.Normal(20, Long.MIN_VALUE), SingleBatchEnergy.normal(-Double.MAX_VALUE, 1, 1, 1, 20));
    }

    @Test
    void wirelessPreservesBigIntegerBeyondLongAndDoubleExactIntegerRanges() {
        BigInteger expected = BigInteger.valueOf(Long.MAX_VALUE).pow(2).multiply(BigInteger.valueOf(20));
        BigInteger total = SingleBatchEnergy.wirelessTotal(Long.MAX_VALUE, 20, Long.MAX_VALUE, 1, true);
        assertEquals(expected, total);
        assertEquals(BigInteger.valueOf(Long.MAX_VALUE).pow(2).negate(), SingleBatchEnergy.wirelessTick(total, 20));
    }

    @ParameterizedTest
    @CsvSource({"101,true,-5", "101,false,5", "-101,true,5", "-101,false,-5", "0,true,0", "0,false,0"})
    void wirelessSignAndDivisionTruncateTowardsZero(long eut, boolean consumer, long expected) {
        BigInteger total = SingleBatchEnergy.wirelessTotal(eut, 1, 1, 1, consumer);
        assertEquals(BigInteger.valueOf(expected), SingleBatchEnergy.wirelessTick(total, 20));
    }

    @Test
    void wirelessRoundsDurationMultiplierBeforeTickDivision() {
        assertEquals(BigInteger.valueOf(3), SingleBatchEnergy.wirelessTotal(7, 3, 1, 0.15, true));
        assertEquals(BigInteger.ZERO, SingleBatchEnergy.wirelessTick(BigInteger.valueOf(3), 20));
        assertEquals(BigInteger.valueOf(-3), SingleBatchEnergy.wirelessTotal(-7, 3, 1, 0.15, true));
    }

    @Test
    void mutableKeepsItsOwnTwentyTickFloor() {
        assertEquals(new SingleBatchEnergy.Normal(20, 50), MutableBatchEnergy.normal(100, 10, 1, 1, 100));
        assertEquals(new SingleBatchEnergy.Normal(100, 10), SingleBatchEnergy.normal(1000, 1, 1, 100, 100));
    }

    @Test
    void invalidCalculationsAreRejectedBeforeAnyInputsCanBeOwned() {
        assertThrows(IllegalArgumentException.class, () -> SingleBatchEnergy.normal(100, 0, 1, 100, 20));
        assertThrows(IllegalArgumentException.class, () -> SingleBatchEnergy.normal(100, 1, 1, 0, 20));
        assertThrows(IllegalArgumentException.class, () -> SingleBatchEnergy.normal(100, 1, 1, 100, 0));
        assertThrows(IllegalArgumentException.class, () -> SingleBatchEnergy.normal(Double.NaN, 1, 1, 100, 20));
        assertThrows(IllegalArgumentException.class, () -> SingleBatchEnergy.normal(Double.MAX_VALUE, 2, 1, 100, 20));
        assertThrows(IllegalArgumentException.class, () -> SingleBatchEnergy.normal(100, 1, -1, 100, 20));
        assertThrows(IllegalArgumentException.class, () -> SingleBatchEnergy.wirelessTotal(100, 20, 1, Double.POSITIVE_INFINITY, true));
        assertThrows(IllegalArgumentException.class, () -> SingleBatchEnergy.wirelessTick(BigInteger.ONE, 0));
    }
}
