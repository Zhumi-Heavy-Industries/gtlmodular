package com.gtlmodular.common.machine.independent;

import com.gtlmodular.common.machine.independent.recipe.RecipeCandidateGroup;
import com.gtlmodular.common.machine.independent.recipe.SharedBatchEnergy;
import com.gtlmodular.common.machine.independent.recipe.SingleBatchEnergy;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 长批次的记账口径：N 次配方必须「同功率跑 N 倍时间」——
 * 每 tick EU/t 不变、时长为 N 倍、总耗电为 N 倍；产量按 p×N 结算，不能凭空放大。
 */
class LongBatchTest {

    @Test
    void floorBoundBatchKeepsTheTickPowerAndMultipliesTimeAndEnergyByRuns() {
        int runs = 5;
        var base = SharedBatchEnergy.normal(List.of(500.0), 100, 20);
        assertEquals(List.of(new SingleBatchEnergy.Normal(20, 25)), base);
        var longBatch = SharedBatchEnergy.normal(List.of(500.0 * runs), 100, 20 * runs);
        assertEquals(1, longBatch.size());
        assertEquals(20 * runs, longBatch.get(0).duration());
        assertEquals(base.get(0).eut(), longBatch.get(0).eut());
        assertEquals(500.0 * runs, (double) longBatch.get(0).duration() * longBatch.get(0).eut());
    }

    @Test
    void energyBoundBatchAlsoMultipliesOnlyTheDuration() {
        int runs = 4;
        var base = SharedBatchEnergy.normal(List.of(10000.0), 100, 20);
        assertEquals(List.of(new SingleBatchEnergy.Normal(100, 100)), base);
        var longBatch = SharedBatchEnergy.normal(List.of(10000.0 * runs), 100, 20 * runs);
        assertEquals(100 * runs, longBatch.get(0).duration());
        assertEquals(100, longBatch.get(0).eut());
    }

    @Test
    void mergedBatchesKeepTheirShareRatioWhenEveryShareIsScaledByRuns() {
        int runs = 3;
        var base = SharedBatchEnergy.normal(List.of(10000.0, 30000.0), 200, 20);
        var longBatch = SharedBatchEnergy.normal(List.of(10000.0 * runs, 30000.0 * runs), 200, 20 * runs);
        for (int i = 0; i < base.size(); i++) {
            assertEquals(base.get(i).duration() * runs, longBatch.get(i).duration());
            assertEquals(base.get(i).eut(), longBatch.get(i).eut());
        }
    }

    @Test
    void runsAreTargetTicksDividedByTheCurrentBatchClockAndCappedByStock() {
        assertEquals(10, RecipeCandidateGroup.runsFor(200, 20, Integer.MAX_VALUE));
        assertEquals(4, RecipeCandidateGroup.runsFor(200, 50, Integer.MAX_VALUE));
        assertEquals(3, RecipeCandidateGroup.runsFor(200, 20, 3));
        assertEquals(1, RecipeCandidateGroup.runsFor(200, 400, Integer.MAX_VALUE));
        assertEquals(1, RecipeCandidateGroup.runsFor(0, 20, Integer.MAX_VALUE));
        assertEquals(1, RecipeCandidateGroup.runsFor(200, 0, Integer.MAX_VALUE));
        assertEquals(1, RecipeCandidateGroup.runsFor(200, 20, 0));
    }
}
