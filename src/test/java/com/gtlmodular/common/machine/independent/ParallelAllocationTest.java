package com.gtlmodular.common.machine.independent;

import com.gtlmodular.common.machine.independent.core.BatchBudgetLedger;
import com.gtlmodular.common.machine.independent.recipe.FairParallelAllocation;
import com.gtlmodular.common.machine.independent.recipe.ParallelAllocation;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class ParallelAllocationTest {
    @Test
    void intLimitsMultiplyAsLongWithoutWrappingOrClampingToInt() {
        assertEquals(4_611_686_014_132_420_609L, ParallelAllocation.totalBudget(Integer.MAX_VALUE, Integer.MAX_VALUE));
        assertEquals(274_877_906_816L, ParallelAllocation.totalBudget(Integer.MAX_VALUE, 128));
        assertEquals(0, ParallelAllocation.totalBudget(100, 0));
        assertThrows(IllegalArgumentException.class, () -> ParallelAllocation.totalBudget(-1, 128));
        assertThrows(IllegalArgumentException.class, () -> ParallelAllocation.totalBudget(100, -1));
    }

    @Test
    void ordinaryDistributionRedistributesSmallDemandsAndKeepsIndivisibleRemainder() {
        assertArrayEquals(new long[]{2, 4, 4}, FairParallelAllocation.allocate(10, new long[]{2, 10, 10}));
        assertArrayEquals(new long[]{0, 50, 50}, FairParallelAllocation.allocate(100, new long[]{0, 100, 100}));
        assertArrayEquals(new long[]{3, 3, 3}, FairParallelAllocation.allocate(10, new long[]{10, 10, 10}));
        assertArrayEquals(new long[]{0, 0}, FairParallelAllocation.allocate(1, new long[]{10, 10}));
        assertArrayEquals(new long[]{1, 2}, FairParallelAllocation.allocate(100, new long[]{1, 2}));
    }

    @Test
    void aSingleRecipeCanUseTheWholeUpstreamBudget() {
        long total = ParallelAllocation.totalBudget(100, 2);
        assertArrayEquals(new long[]{200}, FairParallelAllocation.allocate(total, new long[]{200}));
        assertArrayEquals(new long[]{80}, FairParallelAllocation.allocate(total, new long[]{80}));
    }

    @Test
    void fairAllocationDoesNotOverflowWhenDemandsSumPastLongMax() {
        long half = Long.MAX_VALUE / 2;
        assertArrayEquals(new long[]{half, half},
                FairParallelAllocation.allocate(Long.MAX_VALUE, new long[]{Long.MAX_VALUE, Long.MAX_VALUE}));
        assertArrayEquals(new long[]{Long.MAX_VALUE}, FairParallelAllocation.allocate(Long.MAX_VALUE, new long[]{Long.MAX_VALUE}));
        assertArrayEquals(new long[]{Long.MAX_VALUE - 1, 1},
                FairParallelAllocation.allocate(Long.MAX_VALUE, new long[]{Long.MAX_VALUE, 1}));
    }

    @Test
    void randomizedDemandsStayWithinBothInventoryAndSharedBudget() {
        var random = new Random(31817);
        for (int sample = 0; sample < 2000; sample++) {
            long budget = random.nextLong() & Long.MAX_VALUE;
            long[] demands = new long[1 + random.nextInt(200)];
            Arrays.setAll(demands, i -> random.nextLong() & Long.MAX_VALUE);
            long[] result = FairParallelAllocation.allocate(budget, demands);
            BigInteger sum = BigInteger.ZERO;
            for (int i = 0; i < demands.length; i++) {
                assertTrue(result[i] >= 0 && result[i] <= demands[i]);
                sum = sum.add(BigInteger.valueOf(result[i]));
            }
            assertTrue(sum.compareTo(BigInteger.valueOf(budget)) <= 0);
        }
    }

    @Test
    void remainingBudgetAndShrinkingLimitRetainEachReservation() {
        var ledger = new BatchBudgetLedger<Object>();
        long total = ParallelAllocation.totalBudget(100, 2);
        var first = new Object();
        var second = new Object();
        var split = FairParallelAllocation.allocate(total, new long[]{total, total});
        assertTrue(ledger.reserve(first, split[0], total));
        assertTrue(ledger.reserve(second, split[1], total));
        assertEquals(0, ledger.available(total));
        // 待结算仍持有原预留；降槽不能挤掉这些占用。
        assertEquals(0, ledger.available(ParallelAllocation.totalBudget(100, 1)));
        assertEquals(BigInteger.valueOf(200), ledger.occupied());
        ledger.release(first);
        assertArrayEquals(new long[]{100}, FairParallelAllocation.allocate(ledger.available(total), new long[]{total}));
        assertEquals(0, ledger.available(ParallelAllocation.totalBudget(100, 1)));
        ledger.release(second);
        assertEquals(100, ledger.available(ParallelAllocation.totalBudget(100, 1)));
    }

    @Test
    void mutableActualParallelAndBudgetConsumptionAreNotInterchangeable() {
        var first = ParallelAllocation.mutable(80, 20, 100);
        var second = ParallelAllocation.mutable(160, 80, 100 - first.budget());
        assertEquals(240, first.parallels() + second.parallels());
        assertEquals(100, first.budget() + second.budget());
        assertEquals(new ParallelAllocation(0, 0), ParallelAllocation.mutable(0, 0, 0));
        assertEquals(0, ParallelAllocation.mutable(5, 0, 10).budget());
        assertThrows(IllegalArgumentException.class, () -> ParallelAllocation.mutable(1, 101, 100));
        assertThrows(IllegalArgumentException.class, () -> ParallelAllocation.mutable(0, 1, 100));
        assertThrows(IllegalArgumentException.class, () -> ParallelAllocation.mutable(-1, 1, 100));
        assertThrows(IllegalArgumentException.class, () -> ParallelAllocation.mutable(1, -1, 100));
    }

    @Test
    void emptyAndInvalidFairRequestsDoNotAllocate() {
        assertArrayEquals(new long[0], FairParallelAllocation.allocate(100, new long[0]));
        assertArrayEquals(new long[]{0, 0}, FairParallelAllocation.allocate(0, new long[]{10, 10}));
        assertThrows(IllegalArgumentException.class, () -> FairParallelAllocation.allocate(-1, new long[]{1}));
        assertThrows(IllegalArgumentException.class, () -> FairParallelAllocation.allocate(100, new long[]{-1}));
    }
}
