package com.gtlmodular.common.machine.independent;

import com.gtlmodular.common.machine.independent.runtime.IndependentWorkStats;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IndependentWorkStatsTest {

    @Test
    void disabledStatsCostNothingAndPrintNothing() {
        IndependentWorkStats stats = new IndependentWorkStats(false);
        stats.scan(1_000_000L, 10);
        stats.tick(2_000_000L);
        assertEquals("", stats.summary());
    }

    @Test
    void enabledStatsReportAveragesAndMaximums() {
        IndependentWorkStats stats = new IndependentWorkStats(true);
        stats.scan(1000L, 2);
        stats.scan(3000L, 4);
        stats.tick(5000L);
        stats.tick(7000L);

        String summary = stats.summary();
        assertTrue(summary.contains("2 次"));
        assertTrue(summary.contains("6 候选"));
        assertTrue(summary.contains("批次 tick 2 次"));
        stats.reset();
        assertTrue(stats.summary().contains("0 次"));
    }
}
