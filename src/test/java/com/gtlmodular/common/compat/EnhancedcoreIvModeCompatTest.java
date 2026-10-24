package com.gtlmodular.common.compat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnhancedcoreIvModeCompatTest {

    @BeforeEach
    void resetCompat() {
        EnhancedcoreIvModeCompat.resetForTest();
    }

    @Test
    void crossRecipeProbeDecidesTheAnswer() {
        Object machine = new Object();
        EnhancedcoreIvModeCompat.testProbe = () -> true;
        assertTrue(EnhancedcoreIvModeCompat.crossRecipeOn(machine, 100L));

        EnhancedcoreIvModeCompat.resetForTest();
        EnhancedcoreIvModeCompat.testProbe = () -> false;
        assertFalse(EnhancedcoreIvModeCompat.crossRecipeOn(machine, 100L));
    }

    @Test
    void answerIsReusedForTwentyTicksThenRecomputed() {
        Object machine = new Object();
        AtomicInteger calls = new AtomicInteger();
        EnhancedcoreIvModeCompat.testProbe = () -> {
            calls.incrementAndGet();
            return true;
        };

        EnhancedcoreIvModeCompat.crossRecipeOn(machine, 100L);
        EnhancedcoreIvModeCompat.crossRecipeOn(machine, 119L);
        assertEquals(1, calls.get());

        EnhancedcoreIvModeCompat.crossRecipeOn(machine, 120L);
        assertEquals(2, calls.get());
    }

    @Test
    void clockGoingBackwardsIsTreatedAsExpired() {
        Object machine = new Object();
        AtomicInteger calls = new AtomicInteger();
        EnhancedcoreIvModeCompat.testProbe = () -> {
            calls.incrementAndGet();
            return true;
        };

        EnhancedcoreIvModeCompat.crossRecipeOn(machine, 100L);
        EnhancedcoreIvModeCompat.crossRecipeOn(machine, 40L);
        assertEquals(2, calls.get());
    }

    @Test
    void machinesAreCachedSeparately() {
        Object first = new Object();
        Object second = new Object();
        AtomicInteger calls = new AtomicInteger();
        EnhancedcoreIvModeCompat.testProbe = () -> {
            calls.incrementAndGet();
            return true;
        };

        EnhancedcoreIvModeCompat.crossRecipeOn(first, 100L);
        EnhancedcoreIvModeCompat.crossRecipeOn(second, 100L);
        assertEquals(2, calls.get());

        assertTrue(EnhancedcoreIvModeCompat.crossRecipeOn(first, 101L));
        assertEquals(2, calls.get());
    }

    @Test
    void judgeFailureCountsAsNormalMachine() {
        Object machine = new Object();
        EnhancedcoreIvModeCompat.testProbe = () -> {
            throw new IllegalStateException("增强版判据抛异常");
        };

        assertFalse(EnhancedcoreIvModeCompat.crossRecipeOn(machine, 100L));
        // 失败结果同样进缓存，避免每 tick 重复抛异常。
        assertFalse(EnhancedcoreIvModeCompat.crossRecipeOn(machine, 105L));
    }

    @Test
    void machineWithoutHostControllerIsNotSuppressed() {
        assertFalse(EnhancedcoreIvModeCompat.suppressesModeSwitch(null, 100L));
    }
}
