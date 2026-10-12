package com.gtlmodular.common.diag;

import org.slf4j.Logger;

import java.util.List;
import java.util.Objects;

public final class SwallowedErrors {

    static final int REPORT = 0;
    static final int REPORT_DEBUG = 1;
    static final int BIND = 2;
    static final int SNAPSHOT = 3;
    static final int SHUTDOWN = 4;
    private static final int READ_MESSAGE = 5;
    private static final int WRITE_LOG = 6;

    // 惰性构造放在保护入口内，避免诊断工具初始化失败打断原有兜底。
    private static SwallowedErrorTracker shared;
    private static long internalFailures;

    private SwallowedErrors() {
    }

    public static void report(String site, Throwable failure) {
        guard(null, REPORT, site, failure, null);
    }

    public static void reportDebug(String site, Throwable failure) {
        guard(null, REPORT_DEBUG, site, failure, null);
    }

    public static void bind(String modId, Logger logger) {
        guard(null, BIND, modId, null, logger);
    }

    public static List<SiteSnapshot> snapshot() {
        return snapshot(null);
    }

    public static void logShutdownSummary() {
        guard(null, SHUTDOWN, null, null, null);
    }

    public record SiteSnapshot(String site, boolean debug, long total, long pending,
                               String latestType, String latestMessage, boolean firstReportPending) {
    }

    @SuppressWarnings("unchecked")
    static List<SiteSnapshot> snapshot(SwallowedErrorTracker tracker) {
        Object result = guard(tracker, SNAPSHOT, null, null, null);
        return result == null ? List.of() : (List<SiteSnapshot>) result;
    }

    static String message(Throwable failure) {
        Object result = guard(null, READ_MESSAGE, null, failure, null);
        return result == null ? "<message unavailable>" : (String) result;
    }

    static void write(SwallowedErrorTracker.Emission emission) {
        guard(null, WRITE_LOG, null, null, emission);
    }

    static Object guard(SwallowedErrorTracker tracker, int operation, String site,
                        Throwable failure, Object output) {
        try {
            if (operation == READ_MESSAGE) return singleLine(failure.getMessage());
            if (operation == WRITE_LOG) {
                ((SwallowedErrorTracker.Emission) output).write();
                return null;
            }
            if (tracker == null) tracker = shared();
            boolean outermost = tracker.enter();
            try {
                switch (operation) {
                    case REPORT, REPORT_DEBUG -> tracker.record(site, failure, operation == REPORT_DEBUG, outermost);
                    case BIND -> {
                        SwallowedErrorTracker.Output sink;
                        if (output instanceof SwallowedErrorTracker.Output callback) {
                            sink = callback;
                        } else {
                            Logger logger = Objects.requireNonNull((Logger) output, "logger");
                            sink = (debug, text, error) -> {
                                if (debug) {
                                    if (logger.isDebugEnabled()) logger.debug(text, error);
                                } else {
                                    logger.warn(text, error);
                                }
                            };
                        }
                        tracker.bind(site, sink, outermost);
                    }
                    case SNAPSHOT -> { return tracker.copySnapshot(); }
                    case SHUTDOWN -> { if (outermost) tracker.outputTotals(); }
                    default -> throw new IllegalArgumentException("Unknown diagnostic operation");
                }
            } finally {
                if (outermost) tracker.leave();
            }
        } catch (Throwable internalFailure) {
            SwallowedErrors.recordInternalFailure("SwallowedErrors#guard", internalFailure);
        }
        return null;
    }

    private static synchronized SwallowedErrorTracker shared() {
        if (shared == null) shared = new SwallowedErrorTracker(System::nanoTime);
        return shared;
    }

    // 终端计数不能再读取异常、调用日志器或分配诊断状态，否则可能递归失败。
    private static synchronized void recordInternalFailure(String site, Throwable failure) {
        if (internalFailures != Long.MAX_VALUE) internalFailures++;
    }

    static synchronized long internalFailureCount() {
        return internalFailures;
    }

    private static String singleLine(String message) {
        if (message == null) return "";
        int limit = 256;
        StringBuilder result = new StringBuilder(Math.min(message.length(), limit));
        int end = Math.min(message.length(), limit);
        if (message.length() > limit) end--;
        if (end > 0 && Character.isHighSurrogate(message.charAt(end - 1))) end--;
        for (int i = 0; i < end; i++) {
            char c = message.charAt(i);
            result.append(Character.isISOControl(c) || Character.isWhitespace(c) ? ' ' : c);
        }
        if (message.length() > limit) result.append('…');
        return result.toString();
    }
}
