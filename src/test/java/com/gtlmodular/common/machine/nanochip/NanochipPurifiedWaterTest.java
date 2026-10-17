package com.gtlmodular.common.machine.nanochip;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 组织器净化水模型：档位表、两种水必须不同类别、启动检查、扣水取整与满校准平方。 */
class NanochipPurifiedWaterTest {

    private static long[] tiers(long... amountsByTier) {
        long[] perTier = new long[17];
        for (int i = 0; i < amountsByTier.length; i++) {
            perTier[i + 1] = amountsByTier[i];
        }
        return perTier;
    }

    @Test
    void everyTierMapsToItsBand() {
        assertSame(NanochipPurifiedWater.Grade.THREE, NanochipPurifiedWater.gradeOfTier(1));
        assertSame(NanochipPurifiedWater.Grade.THREE, NanochipPurifiedWater.gradeOfTier(3));
        assertSame(NanochipPurifiedWater.Grade.FOUR, NanochipPurifiedWater.gradeOfTier(4));
        assertSame(NanochipPurifiedWater.Grade.FOUR, NanochipPurifiedWater.gradeOfTier(6));
        assertSame(NanochipPurifiedWater.Grade.FIVE, NanochipPurifiedWater.gradeOfTier(7));
        assertSame(NanochipPurifiedWater.Grade.SIX, NanochipPurifiedWater.gradeOfTier(12));
        assertSame(NanochipPurifiedWater.Grade.SEVEN, NanochipPurifiedWater.gradeOfTier(13));
        assertSame(NanochipPurifiedWater.Grade.EIGHT, NanochipPurifiedWater.gradeOfTier(16));
        assertNull(NanochipPurifiedWater.gradeOfTier(0));
        assertNull(NanochipPurifiedWater.gradeOfTier(17));
    }

    @Test
    void oneCategoryIsNotEnough() {
        // 1~6 级全是水耗类：按类别聚成一条，凑不出两种水。
        NanochipPurifiedWater.Selection selection = NanochipPurifiedWater.select(
                NanochipPurifiedWater.stocksOf(tiers(5000, 0, 5000, 5000, 5000, 5000)));
        assertEquals(1, selection.stocks().size());
        assertFalse(selection.complete());
    }

    @Test
    void selectionTakesHighestTierOfTwoDifferentCategories() {
        NanochipPurifiedWater.Selection selection = NanochipPurifiedWater.select(
                NanochipPurifiedWater.stocksOf(tiers(5000, 0, 5000, 0, 0, 0, 0, 5000, 0, 0, 0, 0, 0, 0, 0, 5000)));
        assertEquals(2, selection.stocks().size());
        assertEquals(NanochipPurifiedWater.Category.EU, selection.stocks().get(0).category());
        assertEquals(NanochipPurifiedWater.Category.DURATION, selection.stocks().get(1).category());
        assertTrue(selection.complete());
    }

    @Test
    void eachWaterMustReachItsBaseAmount() {
        long[] perTier = tiers(999, 0, 0, 0, 0, 0, 2000);
        assertFalse(NanochipPurifiedWater.select(NanochipPurifiedWater.stocksOf(perTier)).complete());
        perTier[1] = 1000;
        assertTrue(NanochipPurifiedWater.select(NanochipPurifiedWater.stocksOf(perTier)).complete());
    }

    @Test
    void drainRoundsUpAndSquaresUnderFullCalibration() {
        long[] perTier = tiers(100000, 0, 0, 0, 0, 0, 100000);
        NanochipPurifiedWater.Selection selection =
                NanochipPurifiedWater.select(NanochipPurifiedWater.stocksOf(perTier));
        NanochipPurifiedWater.Stock consumption = selection.stockOf(NanochipPurifiedWater.Category.CONSUMPTION);
        NanochipPurifiedWater.Stock duration = selection.stockOf(NanochipPurifiedWater.Category.DURATION);
        assertEquals(800L, selection.drainMb(consumption, false));
        assertEquals(640L, selection.drainMb(duration, false));
        assertEquals(640L, selection.drainMb(consumption, true));
        assertEquals(512L, selection.drainMb(duration, true));
        assertEquals(1L, NanochipPurifiedWater.ceilMb(1L, 80L, 100L));
        assertEquals(0.8D, selection.effect(NanochipPurifiedWater.Category.CONSUMPTION, false), 1.0E-9D);
        assertEquals(1.0D, selection.effect(NanochipPurifiedWater.Category.EU, false), 1.0E-9D);
        assertEquals(0.64D, selection.effect(NanochipPurifiedWater.Category.CONSUMPTION, true), 1.0E-9D);
        assertEquals(0.81D, selection.effect(NanochipPurifiedWater.Category.DURATION, true), 1.0E-9D);
    }

    @Test
    void drainPlanUsesWholeCategoryAndNeverOverdraws() {
        long[] perTier = tiers(60000, 0, 60000, 0, 60000);
        NanochipPurifiedWater.Selection selection =
                NanochipPurifiedWater.select(NanochipPurifiedWater.stocksOf(perTier));
        NanochipPurifiedWater.Stock stock = selection.stocks().get(0);
        List<NanochipPurifiedWater.TierAmount> plan =
                NanochipPurifiedWater.drainPlan(selection, stock, perTier, false);
        long sum =
                plan.stream().mapToLong(NanochipPurifiedWater.TierAmount::amountMb).sum();
        assertEquals(selection.drainMb(stock, false), sum);
        assertTrue(sum <= 180000L);
    }
}
