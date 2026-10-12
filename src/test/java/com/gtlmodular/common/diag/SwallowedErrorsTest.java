package com.gtlmodular.common.diag;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class SwallowedErrorsTest {

    private static final long FIVE_MINUTES = TimeUnit.MINUTES.toNanos(5);

    @Test
    void firstReportCarriesOriginalThrowableAndSummariesExcludeItAcrossWindows() {
        Harness h = new Harness();
        h.bind("gtlmodular");
        Throwable first = new IllegalStateException("first", new IllegalArgumentException("cause"));
        h.report("Machine#work", first);
        assertEquals(1, h.logs.size());
        assertSame(first, h.logs.get(0).failure());
        assertFalse(h.logs.get(0).debug());
        assertTrue(h.logs.get(0).text().startsWith("[gtlmodular/swallowed] Machine#work"));

        h.now.set(FIVE_MINUTES - 1);
        h.report("Machine#work", new IllegalStateException("second"));
        assertEquals(1, h.logs.size());
        h.now.incrementAndGet();
        h.report("Machine#work", new IllegalArgumentException("latest"));
        assertEquals(2, h.logs.size());
        assertNull(h.logs.get(1).failure());
        assertTrue(h.logs.get(1).text().contains("additional=2 total=3"));
        assertTrue(h.logs.get(1).text().endsWith("java.lang.IllegalArgumentException: latest"));
        assertEquals(0, h.site("Machine#work").pending());

        h.now.set(2 * FIVE_MINUTES - 1);
        h.report("Machine#work", new Exception("fourth"));
        assertEquals(2, h.logs.size());
        h.now.incrementAndGet();
        h.report("Machine#work", new Exception("fifth"));
        assertEquals(3, h.logs.size());
        assertTrue(h.logs.get(2).text().contains("additional=2 total=5"));
    }

    @Test
    void eachSiteKeepsItsOwnWindowAndSnapshotIsSortedImmutableAndDetached() {
        Harness h = new Harness();
        h.bind("gtl_nac");
        h.report("Z#work", new Exception("z"));
        h.now.set(FIVE_MINUTES / 2);
        h.report("A#work", new Exception("a"));
        List<SwallowedErrors.SiteSnapshot> before = h.snapshot();
        assertEquals(List.of("A#work", "Z#work"), before.stream().map(SwallowedErrors.SiteSnapshot::site).toList());
        assertThrows(UnsupportedOperationException.class, before::clear);

        h.now.set(FIVE_MINUTES);
        h.report("Z#work", new Exception("z2"));
        h.report("A#work", new Exception("a2"));
        assertEquals(3, h.logs.size());
        assertTrue(h.logs.get(2).text().startsWith("[gtl_nac/swallowed] Z#work"));
        assertEquals(1, before.get(0).total());
        assertEquals(1, h.site("A#work").pending());
        assertEquals(0, h.site("Z#work").pending());
        assertEquals("a2", h.site("A#work").latestMessage());
    }

    @Test
    void concurrentFirstReportsAndDueSummariesAreClaimedOnceWithoutLosingCounts() throws Exception {
        Harness h = new Harness();
        h.bind("gtl_nac");
        concurrently(12, () -> {
            for (int i = 0; i < 100; i++) h.report("Machine#parallel", new Exception("same window"));
        });
        assertEquals(1, h.logs.size());
        assertEquals(1200, h.site("Machine#parallel").total());
        assertEquals(1199, h.site("Machine#parallel").pending());

        h.now.set(FIVE_MINUTES);
        concurrently(12, () -> {
            for (int i = 0; i < 100; i++) {
                h.report("Machine#parallel", new Exception("next window"));
                var snapshot = h.site("Machine#parallel");
                assertTrue(snapshot.total() >= snapshot.pending() + 1);
            }
        });
        assertEquals(2, h.logs.size());
        var result = h.site("Machine#parallel");
        assertEquals(2400, result.total());
        String summary = h.logs.get(1).text();
        long reported = Long.parseLong(summary.split("additional=")[1].split(" ")[0]);
        assertEquals(result.total() - 1, reported + result.pending());
    }

    @Test
    void debugOffStillCountsAndEveryDebugOutputStaysAtDebugIncludingShutdown() {
        Harness h = new Harness();
        AtomicBoolean enabled = new AtomicBoolean();
        AtomicInteger debugChecks = new AtomicInteger();
        h.bindLogger("gtl_nac", recordingLogger(h.logs, enabled, debugChecks));
        h.debug("Codec#read", new Exception("first"));
        h.report("Machine#work", new Exception("warning"));
        h.now.set(FIVE_MINUTES);
        h.debug("Codec#read", new Exception("disabled summary"));
        h.shutdown();
        assertEquals(2, h.logs.size());
        assertTrue(h.logs.stream().noneMatch(Log::debug));
        assertTrue(h.logs.stream().noneMatch(log -> log.text().contains("Codec#read")));
        assertEquals(2, h.site("Codec#read").total());
        assertTrue(h.site("Codec#read").debug());

        enabled.set(true);
        h.debug("Codec#new", new Exception("enabled first"));
        h.now.set(2 * FIVE_MINUTES);
        h.debug("Codec#read", new Exception("enabled summary"));
        h.shutdown();
        assertEquals(4, h.logs.stream().filter(Log::debug).count());
        assertTrue(h.logs.stream().filter(log -> log.text().contains("Codec#")).allMatch(Log::debug));
        assertTrue(h.logs.stream().anyMatch(log -> log.debug() && log.text().contains("additional=1 total=3")));
        assertTrue(debugChecks.get() >= 7);
    }

    @Test
    void unboundFirstReportsAreReplayedWithOriginalThrowableAndRebindDoesNotReset() {
        Harness h = new Harness();
        Throwable first = new Exception("first before binding");
        h.report("Machine#early", first);
        h.report("Machine#early", new Exception("second before binding"));
        h.debug("Codec#early", new Exception("debug before binding"));
        assertTrue(h.logs.isEmpty());
        assertTrue(h.site("Machine#early").firstReportPending());
        assertEquals(1, h.site("Machine#early").pending());
        h.now.set(10 * FIVE_MINUTES);
        h.bind("gtlmodular");
        assertEquals(2, h.logs.size());
        assertSame(first, h.logs.get(1).failure());
        assertTrue(h.logs.get(0).debug());
        assertFalse(h.site("Machine#early").firstReportPending());
        assertEquals("second before binding", h.site("Machine#early").latestMessage());
        h.bind("gtlmodular");
        assertEquals(2, h.logs.size());
        assertEquals(2, h.site("Machine#early").total());

        List<Log> replacement = new ArrayList<>();
        h.bindOutput("gtlmodular", (debug, text, error) -> replacement.add(new Log(debug, text, error)));
        h.now.addAndGet(FIVE_MINUTES);
        h.report("Machine#early", new Exception("third"));
        assertEquals(2, h.logs.size());
        assertEquals(1, replacement.size());
        assertTrue(replacement.get(0).text().contains("additional=2 total=3"));
    }

    @Test
    void shutdownIncludesUnexpiredBacklogAndDoesNotResetSessionOrConsumeItTwice() {
        Harness h = new Harness();
        h.bind("gtlmodular");
        h.report("Machine#work", new Exception("first"));
        h.report("Machine#work", new Exception("pending"));
        h.shutdown();
        h.shutdown();
        assertEquals(3, h.logs.size());
        assertTrue(h.logs.get(1).text().contains("shutdown total=2 pending=1"));
        assertEquals(h.logs.get(1), h.logs.get(2));
        assertEquals(2, h.site("Machine#work").total());
        assertEquals(1, h.site("Machine#work").pending());
        h.now.set(FIVE_MINUTES);
        h.report("Machine#work", new Exception("after shutdown"));
        assertTrue(h.logs.get(3).text().contains("additional=2 total=3"));
    }

    @Test
    void loggerRuntimeExceptionsAndErrorsCannotEscapeAnyOutputPath() {
        for (boolean error : List.of(false, true)) {
            Harness h = new Harness();
            h.report("Machine#early", new Exception("unbound"));
            long before = SwallowedErrors.internalFailureCount();
            h.bindOutput("gtlmodular", (debug, text, failure) -> {
                if (error) throw new AssertionError("logger failed");
                throw new IllegalStateException("logger failed");
            });
            assertDoesNotThrow(() -> h.report("Machine#work", new Exception("first")));
            h.now.set(FIVE_MINUTES);
            assertDoesNotThrow(() -> h.report("Machine#work", new Exception("summary")));
            assertDoesNotThrow(h::shutdown);
            assertEquals(before + 5, SwallowedErrors.internalFailureCount());
            assertEquals(2, h.site("Machine#work").total());
            assertEquals(0, h.site("Machine#work").pending());
        }
    }

    @Test
    void failingDebugEnabledCheckIsContainedAndCounterStillAvailable() {
        Harness h = new Harness();
        Logger logger = (Logger) Proxy.newProxyInstance(Logger.class.getClassLoader(), new Class<?>[]{Logger.class},
                (proxy, method, args) -> { throw new AssertionError("logger unavailable"); });
        h.bindLogger("gtl_nac", logger);
        long before = SwallowedErrors.internalFailureCount();
        assertDoesNotThrow(() -> h.debug("Codec#read", new Exception("first")));
        assertEquals(before + 1, SwallowedErrors.internalFailureCount());
        assertEquals(1, h.site("Codec#read").total());
    }

    @Test
    void reentrantLoggerCountsWithoutRecursingAndDoesNotHoldTheStateLock() throws Exception {
        Harness h = new Harness();
        AtomicInteger callbacks = new AtomicInteger();
        var executor = Executors.newSingleThreadExecutor();
        try {
            h.bindOutput("gtlmodular", (debug, text, failure) -> {
                callbacks.incrementAndGet();
                h.report("Machine#work", new Exception("from logger"));
                h.shutdown();
                Future<List<SwallowedErrors.SiteSnapshot>> otherThread = executor.submit(h::snapshot);
                assertEquals(2, get(otherThread).get(0).total());
            });
            long before = SwallowedErrors.internalFailureCount();
            h.report("Machine#work", new Exception("first"));
            assertEquals(before, SwallowedErrors.internalFailureCount());
        } finally {
            executor.shutdownNow();
        }
        assertEquals(1, callbacks.get());
        assertEquals(2, h.site("Machine#work").total());
        assertEquals(1, h.site("Machine#work").pending());
    }

    @Test
    void reentrantNewSiteIsDeferredAndLatestMessageReadCannotOverwriteANewerOccurrence() {
        Harness h = new Harness();
        h.bindOutput("gtlmodular", (debug, text, failure) -> {
            h.logs.add(new Log(debug, text, failure));
            h.report("Logger#callback", new IllegalArgumentException("nested"));
        });
        h.report("Machine#work", new Exception("first"));
        assertEquals(1, h.logs.size());
        assertTrue(h.site("Logger#callback").firstReportPending());

        h.bind("gtlmodular");
        assertEquals(2, h.logs.size());
        assertFalse(h.site("Logger#callback").firstReportPending());
        h.report("Machine#work", new Exception() {
            @Override
            public String getMessage() {
                h.report("Machine#work", new IllegalArgumentException("newer"));
                return "older";
            }
        });
        assertEquals(3, h.site("Machine#work").total());
        assertEquals(IllegalArgumentException.class.getName(), h.site("Machine#work").latestType());
        assertEquals("<reentrant report>", h.site("Machine#work").latestMessage());
    }

    @Test
    void unreadableMessagesAreCountedWithoutPreventingFirstOrSubsequentReports() {
        Harness h = new Harness();
        h.bind("gtl_nac");
        Throwable broken = new Exception() {
            @Override
            public String getMessage() { throw new AssertionError("cannot read message"); }
        };
        long before = SwallowedErrors.internalFailureCount();
        assertDoesNotThrow(() -> h.report("Machine#work", broken));
        assertEquals(before + 1, SwallowedErrors.internalFailureCount());
        assertSame(broken, h.logs.get(0).failure());
        assertEquals("<message unavailable>", h.site("Machine#work").latestMessage());
        h.now.set(FIVE_MINUTES);
        h.report("Machine#work", new IllegalStateException("healthy"));
        assertEquals(2, h.logs.size());
        assertEquals("healthy", h.site("Machine#work").latestMessage());
    }

    @Test
    void messagesAreSingleLineBoundedAndNullSafe() {
        Harness h = new Harness();
        h.bind("gtlmodular");
        h.report("Machine#work", new Exception((String) null));
        assertEquals("", h.site("Machine#work").latestMessage());
        h.now.set(FIVE_MINUTES);
        h.report("Machine#work", new Exception("a\r\nb\t\u0085\u2028\u2029" + "x".repeat(300)));
        String message = h.site("Machine#work").latestMessage();
        assertEquals(256, message.length());
        assertTrue(message.endsWith("…"));
        assertTrue(message.chars().noneMatch(c -> Character.isISOControl(c) || c == 0x2028 || c == 0x2029));
        assertEquals(1, h.logs.get(1).text().lines().count());
        h.report("Machine#work", new Exception("x".repeat(254) + "😀" + "truncated"));
        assertEquals("x".repeat(254) + "…", h.site("Machine#work").latestMessage());
    }

    @Test
    void monotonicClockWraparoundPreservesTheFiveMinuteBoundary() {
        Harness h = new Harness();
        h.bind("gtlmodular");
        h.now.set(Long.MAX_VALUE - FIVE_MINUTES / 2);
        h.report("Machine#work", new Exception("before wrap"));
        h.now.addAndGet(FIVE_MINUTES - 1);
        h.report("Machine#work", new Exception("not due"));
        assertEquals(1, h.logs.size());
        h.now.incrementAndGet();
        h.report("Machine#work", new Exception("due"));
        assertEquals(2, h.logs.size());
    }

    @Test
    void failingClockIsContainedAndRetainsFirstThrowableForRecovery() {
        AtomicBoolean broken = new AtomicBoolean(true);
        SwallowedErrorTracker tracker = new SwallowedErrorTracker(() -> {
            if (broken.get()) throw new AssertionError("clock failed");
            return 0;
        });
        List<Log> logs = new ArrayList<>();
        SwallowedErrorTracker.Output output = (debug, text, failure) -> logs.add(new Log(debug, text, failure));
        SwallowedErrors.guard(tracker, SwallowedErrors.BIND, "gtlmodular", null, output);
        Throwable first = new Exception("first");
        long before = SwallowedErrors.internalFailureCount();
        assertDoesNotThrow(() -> SwallowedErrors.guard(tracker, SwallowedErrors.REPORT, "Machine#work", first, null));
        assertEquals(before + 1, SwallowedErrors.internalFailureCount());
        assertEquals(1, SwallowedErrors.snapshot(tracker).get(0).total());
        broken.set(false);
        SwallowedErrors.guard(tracker, SwallowedErrors.REPORT, "Machine#work", new Exception("second"), null);
        assertSame(first, logs.get(0).failure());
        assertEquals(2, SwallowedErrors.snapshot(tracker).get(0).total());
    }

    @Test
    void internalFailureCounterDoesNotLoseConcurrentFailures() throws Exception {
        Harness h = new Harness();
        h.bindOutput("gtlmodular", (debug, text, failure) -> { throw new AssertionError("logger"); });
        AtomicInteger sites = new AtomicInteger();
        long before = SwallowedErrors.internalFailureCount();
        concurrently(8, () -> h.report("Test#" + sites.incrementAndGet(), new Exception("first")));
        assertEquals(before + 8, SwallowedErrors.internalFailureCount());
        assertEquals(8, h.snapshot().size());
    }

    @Test
    void publicFacadeUsesTheSameProtectedPaths() {
        List<Log> logs = new ArrayList<>();
        SwallowedErrors.report("Facade#early", new Exception("first"));
        assertTrue(SwallowedErrors.snapshot().stream().anyMatch(site -> site.site().equals("Facade#early")));
        SwallowedErrors.bind("gtl_nac", recordingLogger(logs, new AtomicBoolean(true), new AtomicInteger()));
        SwallowedErrors.reportDebug("Facade#debug", new Exception("debug"));
        SwallowedErrors.logShutdownSummary();
        // 公共门面累计整个 JVM 的异常，其他测试的补报和关服汇总不属于本用例。
        assertEquals(4, logs.stream()
                .filter(log -> log.text().startsWith("[gtl_nac/swallowed] Facade#")).count());
        assertTrue(logs.stream().allMatch(log -> log.text().startsWith("[gtl_nac/swallowed]")));
        assertDoesNotThrow(() -> SwallowedErrors.report(null, null));
        assertDoesNotThrow(() -> SwallowedErrors.bind("gtl_nac", null));
    }

    private static Logger recordingLogger(List<Log> logs, AtomicBoolean debugEnabled, AtomicInteger debugChecks) {
        return (Logger) Proxy.newProxyInstance(Logger.class.getClassLoader(), new Class<?>[]{Logger.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("isDebugEnabled")) {
                        debugChecks.incrementAndGet();
                        return debugEnabled.get();
                    }
                    if (method.getName().equals("warn") || method.getName().equals("debug")) {
                        logs.add(new Log(method.getName().equals("debug"), (String) args[0], (Throwable) args[1]));
                        return null;
                    }
                    throw new AssertionError("Unexpected logger method: " + method);
                });
    }

    private static void concurrently(int count, Runnable action) throws Exception {
        var executor = Executors.newFixedThreadPool(count);
        try {
            CountDownLatch ready = new CountDownLatch(count);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < count; i++) futures.add(executor.submit(() -> {
                ready.countDown();
                assertTrue(start.await(5, TimeUnit.SECONDS));
                action.run();
                return null;
            }));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            for (Future<?> future : futures) future.get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
    }

    private static <T> T get(Future<T> future) {
        try {
            return future.get(5, TimeUnit.SECONDS);
        } catch (Exception failure) {
            throw new AssertionError(failure);
        }
    }

    private record Log(boolean debug, String text, Throwable failure) {
    }

    private static final class Harness {
        final AtomicLong now = new AtomicLong();
        final SwallowedErrorTracker tracker = new SwallowedErrorTracker(now::get);
        final List<Log> logs = new CopyOnWriteArrayList<>();

        void bind(String modId) {
            bindOutput(modId, (debug, text, failure) -> logs.add(new Log(debug, text, failure)));
        }

        void bindOutput(String modId, SwallowedErrorTracker.Output output) {
            SwallowedErrors.guard(tracker, SwallowedErrors.BIND, modId, null, output);
        }

        void bindLogger(String modId, Logger logger) {
            SwallowedErrors.guard(tracker, SwallowedErrors.BIND, modId, null, logger);
        }

        void report(String site, Throwable failure) {
            SwallowedErrors.guard(tracker, SwallowedErrors.REPORT, site, failure, null);
        }

        void debug(String site, Throwable failure) {
            SwallowedErrors.guard(tracker, SwallowedErrors.REPORT_DEBUG, site, failure, null);
        }

        List<SwallowedErrors.SiteSnapshot> snapshot() {
            return SwallowedErrors.snapshot(tracker);
        }

        SwallowedErrors.SiteSnapshot site(String site) {
            return snapshot().stream().filter(entry -> entry.site().equals(site)).findFirst().orElseThrow();
        }

        void shutdown() {
            SwallowedErrors.guard(tracker, SwallowedErrors.SHUTDOWN, null, null, null);
        }
    }
}
