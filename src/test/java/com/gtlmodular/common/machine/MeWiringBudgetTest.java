package com.gtlmodular.common.machine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 接线重建的限频/限次/证伪：重建会重复注册输入变化监听器，不能无界重试；
 * 连续无效也必须停手，否则空转机器会每 100 tick 白重建一次。
 */
class MeWiringBudgetTest {

    @Test
    void firstAttemptIsAllowedAndImmediatelyCoolsDown() {
        MeWiringBudget budget = new MeWiringBudget();

        assertEquals(MeWiringBudget.Decision.ALLOWED, budget.peek(100L));
        budget.start(100L);
        assertEquals(1, budget.attempts());
        assertEquals(MeWiringBudget.MAX_ATTEMPTS - 1, budget.remaining());
        assertEquals(100L, budget.lastAttemptAt());

        assertEquals(MeWiringBudget.Decision.WAITING, budget.peek(101L));
        assertEquals(MeWiringBudget.Decision.WAITING, budget.peek(199L));
        assertEquals(MeWiringBudget.Decision.ALLOWED, budget.peek(200L));
    }

    @Test
    void changingReadingsResetTheFutileCounterAndKeepGoing() {
        MeWiringBudget budget = new MeWiringBudget();

        budget.start(0L);
        budget.record(0, 2);
        assertFalse(budget.stopped());

        budget.start(100L);
        budget.record(2, 3);
        assertFalse(budget.stopped());
        assertEquals(2, budget.attempts());
    }

    @Test
    void twoIdenticalReadingsStopTheBudgetPermanently() {
        MeWiringBudget budget = new MeWiringBudget();

        budget.start(0L);
        budget.record(5, 5);
        assertFalse(budget.stopped());

        budget.start(100L);
        budget.record(5, 5);
        assertTrue(budget.stopped());
        assertEquals(MeWiringBudget.Decision.STOPPED, budget.peek(10_000L));
        assertNotNull(budget.stopReason());
    }

    @Test
    void failureStopsImmediately() {
        MeWiringBudget budget = new MeWiringBudget();
        budget.start(0L);
        budget.fail("重建抛 RuntimeException");

        assertTrue(budget.stopped());
        assertEquals(0, budget.remaining());
        assertEquals(MeWiringBudget.Decision.STOPPED, budget.peek(1L));
    }

    @Test
    void budgetIsSpentAfterMaxAttempts() {
        MeWiringBudget budget = new MeWiringBudget();
        long tick = 0L;
        for (int i = 0; i < MeWiringBudget.MAX_ATTEMPTS; i++) {
            assertEquals(MeWiringBudget.Decision.ALLOWED, budget.peek(tick));
            budget.start(tick);
            // 每次读数都变，避免触发证伪停手，只考察次数上限。
            budget.record(i, i + 1);
            tick += MeWiringBudget.COOLDOWN_TICKS;
        }
        assertEquals(0, budget.remaining());
        assertEquals(MeWiringBudget.Decision.STOPPED, budget.peek(tick));
    }
}
