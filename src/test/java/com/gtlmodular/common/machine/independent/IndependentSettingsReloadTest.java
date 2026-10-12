package com.gtlmodular.common.machine.independent;

import com.gtlmodular.common.machine.independent.control.IndependentHandoff;
import com.gtlmodular.common.machine.independent.control.IndependentSettingsReload;
import com.gtlmodular.common.machine.independent.core.IndependentBatchSession;
import com.gtlmodular.common.machine.independent.core.IndependentSessionControl;
import com.gtlmodular.common.machine.independent.core.IndependentSessionSnapshot;
import com.gtlmodular.common.machine.independent.eligibility.IndependentThreadEligibility;
import com.gtlmodular.common.machine.independent.eligibility.ThreadEligibilitySettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigInteger;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(10)
class IndependentSettingsReloadTest {
    private static final String MACHINE = "gtladditions:arcane_cache_vault";
    private static final ThreadEligibilitySettings DISABLED = settings(false, false, false);
    private static final ThreadEligibilitySettings ENABLED = settings(true, false, false);

    @ParameterizedTest
    @MethodSource("changes")
    void watcherCannotChangeAdmissionBetweenMergeAndSetup(ThreadEligibilitySettings before,
                                                         ThreadEligibilitySettings after,
                                                         Class<?> logicClass) throws InterruptedException {
        var reload = new IndependentSettingsReload();
        var source = new AtomicReference<>(before);
        var reads = new AtomicInteger();
        var eligibility = new IndependentThreadEligibility(Set.of(Number.class));
        reload.request();
        assertTrue(reload.publishPending(source::get));
        boolean atMerge = eligibility.evaluate(reload.current(), MACHINE, logicClass, false).eligibleForNewBatches();

        onWatcher(() -> {
            source.set(after);
            reload.request();
        });

        assertSame(before, reload.current());
        assertEquals(atMerge, eligibility.evaluate(reload.current(), MACHINE, logicClass, false).eligibleForNewBatches());
        assertTrue(reload.publishPending(() -> {
            reads.incrementAndGet();
            return source.get();
        }));
        assertSame(after, reload.current());
        boolean nextTick = eligibility.evaluate(reload.current(), MACHINE, logicClass, false).eligibleForNewBatches();
        assertNotEquals(atMerge, nextTick);
        var owned = IndependentHandoff.decide(true, nextTick, true, true, false, false, false);
        assertTrue(owned.runIndependent());
        assertTrue(owned.blockUpstreamStarts());
        assertEquals(nextTick, owned.admit());
        assertFalse(reload.publishPending(() -> {
            reads.incrementAndGet();
            return source.get();
        }));
        assertEquals(1, reads.get());
    }

    static Stream<Arguments> changes() {
        return Stream.of(
                Arguments.of(DISABLED, ENABLED, Number.class),
                Arguments.of(ENABLED, DISABLED, Number.class),
                Arguments.of(ENABLED, settings(true, false, true), Number.class),
                Arguments.of(settings(true, false, true), ENABLED, Number.class),
                Arguments.of(ENABLED, settings(true, true, false), Integer.class),
                Arguments.of(settings(true, true, false), ENABLED, Integer.class));
    }

    @Test
    void notificationDuringPublicationRemainsPendingForTheNextTick() {
        var reload = new IndependentSettingsReload();
        var source = new AtomicReference<>(ENABLED);
        reload.request();
        assertTrue(reload.publishPending(() -> {
            var captured = source.get();
            assertDoesNotThrow(() -> onWatcher(() -> {
                source.set(DISABLED);
                reload.request();
            }));
            return captured;
        }));

        assertSame(ENABLED, reload.current());
        assertTrue(reload.publishPending(source::get));
        assertSame(DISABLED, reload.current());
        assertFalse(reload.publishPending(() -> fail("没有新通知时不应读取配置")));
    }

    @Test
    void failedSnapshotKeepsPreviousSettingsAndCanRetry() {
        var reload = new IndependentSettingsReload();
        reload.request();
        reload.publishPending(() -> ENABLED);
        reload.request();

        assertThrows(IllegalStateException.class, () -> reload.publishPending(() -> {
            throw new IllegalStateException("unavailable");
        }));
        assertSame(ENABLED, reload.current());
        assertTrue(reload.publishPending(() -> DISABLED));
        assertSame(DISABLED, reload.current());
    }

    @Test
    void restartUsesDisabledDefaultsUntilItsFirstServerTick() throws InterruptedException {
        var reload = new IndependentSettingsReload();
        assertEquals(DISABLED, reload.current());
        assertFalse(reload.publishPending(() -> fail("启动时还没有配置通知")));
        onWatcher(reload::request);
        assertEquals(DISABLED, reload.current());
        assertTrue(reload.publishPending(() -> ENABLED));

        reload.serverStopped();
        assertEquals(DISABLED, reload.current());
        assertTrue(reload.publishPending(() -> settings(true, true, false)));
        assertEquals(Set.of(MACHINE), reload.current().whitelist());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void restoredWorkDecidesWhetherToDrainWithTheFirstPublishedSettings(boolean enabled) {
        var reload = new IndependentSettingsReload();
        var saved = new IndependentSessionSnapshot<String, String>(false,
                List.of(new IndependentSessionSnapshot.Batch<>("paid batch", 20, 7, 3,
                        IndependentSessionSnapshot.Phase.RUNNING, null)), List.of());
        var session = IndependentBatchSession.restore(saved);
        reload.request();
        reload.publishPending(() -> settings(enabled, false, false));

        var handoff = IndependentHandoff.decide(session.hasOwnedWork(), reload.current().enabled(),
                true, true, false, false, false);
        session.tick(new IndependentSessionControl(handoff.admit(), false, true, true, 1, 3), null);

        assertEquals(!enabled, session.drainRequested());
        assertEquals(saved.batches(), session.batches());
        assertEquals(BigInteger.valueOf(3), session.occupiedBudget());
        assertTrue(session.hasOwnedWork());
    }

    private static ThreadEligibilitySettings settings(boolean enabled, boolean whitelist, boolean blacklist) {
        return new ThreadEligibilitySettings(enabled, whitelist ? Set.of(MACHINE) : Set.of(),
                blacklist ? Set.of(MACHINE) : Set.of());
    }

    private static void onWatcher(Runnable notification) throws InterruptedException {
        var failure = new AtomicReference<Throwable>();
        var watcher = new Thread(() -> {
            try {
                notification.run();
            } catch (Throwable caught) {
                failure.set(caught);
            }
        }, "config-reload-test");
        watcher.setDaemon(true);
        watcher.start();
        watcher.join(5000);
        assertFalse(watcher.isAlive(), "配置通知线程未退出");
        assertNull(failure.get());
    }
}
