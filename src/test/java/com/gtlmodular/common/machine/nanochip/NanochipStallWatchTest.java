package com.gtlmodular.common.machine.nanochip;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NanochipStallWatchTest {
    @Test
    void materialWithoutCandidatesStillReportsAfterTenSeconds() {
        var watch = new NanochipStallWatch();
        watch.observe(100, "empty", false, true, 0);
        assertFalse(watch.reportDue(1000));
        assertTrue(watch.observe(1000, "plate=4", true, true, 0));
        assertFalse(watch.reportDue(1199));
        assertTrue(watch.reportDue(1200));
    }

    @Test
    void continuousInputChangesCannotPostponeReportOrBypassMinuteLimit() {
        var watch = new NanochipStallWatch();
        for (int tick = 0; tick <= 200; tick += 20) {
            watch.observe(tick, "plate=" + tick, true, true, 0);
        }
        assertTrue(watch.reportDue(200));
        watch.observe(300, "new pattern", true, true, 0);
        assertFalse(watch.reportDue(1399));
        assertTrue(watch.reportDue(1400));
    }

    @Test
    void successfulBatchesRestartDelayEvenWhenStockIsImmediatelyRefilled() {
        var watch = new NanochipStallWatch();
        watch.observe(0, "plate=4", true, true, 0);
        assertFalse(watch.observe(180, "plate=4", true, true, 1));
        assertFalse(watch.reportDue(200));
        assertTrue(watch.reportDue(380));
    }

    @Test
    void emptyInputDisarmsButLaterDeliveryStillHonorsRateLimit() {
        var watch = new NanochipStallWatch();
        watch.observe(0, "plate=4", true, true, 0);
        assertTrue(watch.reportDue(200));
        watch.observe(220, "empty", false, true, 0);
        assertFalse(watch.reportDue(1400));
        watch.observe(1400, "plate=4", true, true, 0);
        assertFalse(watch.reportDue(1599));
        assertTrue(watch.reportDue(1600));
    }

    @Test
    void unreadableInputDoesNotPretendPreviouslySeenMaterialsDisappeared() {
        var watch = new NanochipStallWatch();
        watch.observe(0, "plate=4", true, true, 0);
        watch.observe(100, "unreadable", false, false, 0);
        assertTrue(watch.reportDue(200));
    }

    @Test
    void timeRollbackDoesNotSuppressDiagnosticsForever() {
        var watch = new NanochipStallWatch();
        watch.observe(10000, "plate=4", true, true, 0);
        assertTrue(watch.reportDue(10200));
        watch.observe(0, "plate=4", true, true, 0);
        assertFalse(watch.reportDue(199));
        assertTrue(watch.reportDue(200));
    }
}
