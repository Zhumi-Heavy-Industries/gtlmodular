package com.gtlmodular.common.machine.independent;

import com.gtlmodular.common.machine.independent.recipe.SharedBatchEnergy;
import com.gtlmodular.common.machine.independent.recipe.SingleBatchEnergy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigInteger;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SharedBatchEnergyTest {
    @Test
    void twoFullVoltageBatchesHaveTheSameCompletionTimeAndDebitAsOneMergedRecipe() {
        var batches = SharedBatchEnergy.normal(List.of(10000.0, 10000.0), 100, 20);
        assertEquals(List.of(new SingleBatchEnergy.Normal(200, 50), new SingleBatchEnergy.Normal(200, 50)), batches);
        assertEquals(20000, batches.stream().mapToLong(batch -> batch.duration() * batch.eut()).sum());
    }

    @ParameterizedTest
    @CsvSource({"1999,20,99", "2000,20,100", "2049,20,100", "2050,21,100", "2051,21,100", "0,20,0"})
    void unequalBatchWeightsKeepTheMergedRoundingBoundary(double energy, int duration, long totalEut) {
        var batches = SharedBatchEnergy.normal(List.of(energy / 4, energy * 3 / 4), 100, 20);
        assertTrue(batches.stream().allMatch(batch -> batch.duration() == duration));
        assertEquals(totalEut, batches.stream().mapToLong(SingleBatchEnergy.Normal::eut).sum());
        assertEquals(duration * totalEut, batches.stream().mapToLong(batch -> batch.duration() * batch.eut()).sum());
    }

    @Test
    void minimumDurationAndMaintenanceApplyToTheMergedWorkOnlyOnce() {
        double multiplier = 1.1 * 0.5 * 0.25;
        double first = SingleBatchEnergy.total(40000, 6, multiplier);
        double second = SingleBatchEnergy.total(40000, 2, multiplier);
        assertEquals(List.of(new SingleBatchEnergy.Normal(440, 75), new SingleBatchEnergy.Normal(440, 25)),
                SharedBatchEnergy.normal(List.of(first, second), 100, 20));
        assertEquals(List.of(new SingleBatchEnergy.Normal(100, 5), new SingleBatchEnergy.Normal(100, 5)),
                SharedBatchEnergy.normal(List.of(500.0, 500.0), 100, 100));
        assertEquals(List.of(new SingleBatchEnergy.Normal(20, 25), new SingleBatchEnergy.Normal(20, 25)),
                SharedBatchEnergy.normal(List.of(500.0, 500.0), 100, 20));
    }

    @Test
    void rejectedCandidateIsExcludedBeforeFinalizingTheAcceptedClock() {
        assertEquals(new SingleBatchEnergy.Normal(200, 50), SharedBatchEnergy.normal(List.of(10000.0, 10000.0), 100, 20).get(0));
        assertEquals(List.of(new SingleBatchEnergy.Normal(100, 100)), SharedBatchEnergy.normal(List.of(10000.0), 100, 20));
    }

    @Test
    void oneEuMachineAndSubEuSharesDoNotLoseTheMergedDebit() {
        var batches = SharedBatchEnergy.normal(List.of(20.0, 20.0, 20.0), 1, 20);
        assertEquals(List.of(new SingleBatchEnergy.Normal(60, 0), new SingleBatchEnergy.Normal(60, 0),
                new SingleBatchEnergy.Normal(60, 1)), batches);
    }

    @Test
    void largeSharesDoNotOverflowAndDurationStillSaturatesLikeUpstream() {
        var batches = SharedBatchEnergy.normal(List.of(1.0e30, 2.0e30), Long.MAX_VALUE, 20);
        assertTrue(batches.stream().allMatch(batch -> batch.duration() == Integer.MAX_VALUE));
        assertEquals(BigInteger.valueOf(Long.MAX_VALUE), batches.stream().map(batch -> BigInteger.valueOf(batch.eut()))
                .reduce(BigInteger.ZERO, BigInteger::add));
    }

    @ParameterizedTest
    @CsvSource({"1,-10", "-1,10"})
    void wirelessAccountAndTerminalShareOneRoundedDebitWithoutImposingWiredVoltage(int sign, long expectedTick) {
        var energy = BigInteger.valueOf(101L * sign);
        var total = energy.multiply(BigInteger.TWO);
        assertEquals(List.of(BigInteger.valueOf(expectedTick / 2), BigInteger.valueOf(expectedTick / 2)),
                SharedBatchEnergy.wireless(List.of(energy, energy), SingleBatchEnergy.wirelessTick(total, 20)));
        var huge = BigInteger.valueOf(Long.MAX_VALUE).pow(2);
        var shares = SharedBatchEnergy.wireless(List.of(huge, huge), huge.negate());
        assertEquals(huge.negate(), shares.stream().reduce(BigInteger.ZERO, BigInteger::add));
    }

    @Test
    void wirelessRemaindersAreRoundedOnceForTheMachine() {
        var energies = List.of(BigInteger.valueOf(19), BigInteger.valueOf(19));
        assertEquals(List.of(BigInteger.ZERO, BigInteger.ONE.negate()),
                SharedBatchEnergy.wireless(energies, SingleBatchEnergy.wirelessTick(BigInteger.valueOf(38), 20)));
        assertEquals(List.of(BigInteger.ZERO, BigInteger.ZERO),
                SharedBatchEnergy.wireless(List.of(BigInteger.ZERO, BigInteger.ZERO), BigInteger.ZERO));
    }

    @Test
    void invalidGroupsCannotBecomeUnpaidWork() {
        assertThrows(IllegalArgumentException.class, () -> SharedBatchEnergy.normal(List.of(-1.0, 2.0), 100, 20));
        assertThrows(IllegalArgumentException.class, () -> SharedBatchEnergy.normal(List.of(Double.NaN), 100, 20));
        assertThrows(IllegalArgumentException.class, () -> SharedBatchEnergy.normal(List.of(Double.MAX_VALUE, Double.MAX_VALUE), 100, 20));
        assertThrows(IllegalArgumentException.class, () -> SharedBatchEnergy.wireless(List.of(BigInteger.ONE, BigInteger.ONE.negate()), BigInteger.ZERO));
    }
}
