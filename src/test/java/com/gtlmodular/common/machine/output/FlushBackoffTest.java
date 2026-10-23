package com.gtlmodular.common.machine.output;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FlushBackoffTest {
    @Test
    void delaysDoubleUpTo64Ticks() {
        var backoff = new FlushBackoff();
        long now = 0;
        assertTrue(backoff.shouldAttempt(now));
        long[] expected = {1, 2, 4, 8, 16, 32, 64, 64, 64};
        for (long delay : expected) {
            backoff.onFailure(now);
            assertEquals(now + delay, firstAllowedTick(backoff, now));
            now += delay;
        }
    }

    @Test
    void successResetsEverything() {
        var backoff = new FlushBackoff();
        backoff.onFailure(0);
        backoff.onFailure(1);
        assertNotEquals(0, backoff.attempt());
        backoff.onSuccess();
        assertEquals(0, backoff.attempt());
        assertTrue(backoff.shouldAttempt(0));
    }

    /** 二分不如线性直观：从当前时刻向上找第一个允许尝试的 tick。 */
    private static long firstAllowedTick(FlushBackoff backoff, long from) {
        for (long t = from; t <= from + 128; t++) {
            if (backoff.shouldAttempt(t)) {
                return t;
            }
        }
        throw new AssertionError("128 tick 内都没恢复");
    }
}
