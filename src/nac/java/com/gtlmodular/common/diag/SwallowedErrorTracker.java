package com.gtlmodular.common.diag;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.LongSupplier;

final class SwallowedErrorTracker {

    private static final long SUMMARY_INTERVAL_NANOS = 300_000_000_000L;

    private final LongSupplier clock;
    private final TreeMap<String, Site> sites = new TreeMap<>();
    private final ThreadLocal<Boolean> active = new ThreadLocal<>();
    private Target target;

    SwallowedErrorTracker(LongSupplier clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    boolean enter() {
        if (active.get() != null) return false;
        active.set(Boolean.TRUE);
        return true;
    }

    void leave() {
        active.remove();
    }

    void record(String name, Throwable failure, boolean debug, boolean outermost) {
        Objects.requireNonNull(name, "site");
        Objects.requireNonNull(failure, "failure");
        Site site;
        long occurrence;
        synchronized (this) {
            site = sites.get(name);
            if (site == null) {
                site = new Site(name, debug, failure);
                sites.put(name, site);
            } else {
                site.pending++;
            }
            occurrence = ++site.total;
            site.latestType = failure.getClass().getName();
            site.latestMessage = outermost ? "<message unavailable>" : "<reentrant report>";
        }
        // 日志器和异常消息都可能回调 report；重入只记账，留待后续报告或关服输出。
        if (!outermost) return;
        String message = SwallowedErrors.message(failure);
        synchronized (this) {
            if (site.total == occurrence) site.latestMessage = message;
        }
        flush(site);
    }

    void bind(String modId, Output output, boolean outermost) {
        Objects.requireNonNull(modId, "modId");
        synchronized (this) {
            target = new Target("[" + modId + "/swallowed]", output);
        }
        if (outermost) {
            for (Site site : allSites()) flush(site);
        }
    }

    synchronized List<SwallowedErrors.SiteSnapshot> copySnapshot() {
        List<SwallowedErrors.SiteSnapshot> result = new ArrayList<>(sites.size());
        for (Site site : sites.values()) result.add(site.snapshot());
        return List.copyOf(result);
    }

    void outputTotals() {
        for (Site site : allSites()) flush(site);
        List<SwallowedErrors.SiteSnapshot> snapshot = copySnapshot();
        Target destination;
        synchronized (this) {
            destination = target;
        }
        if (destination == null) return;
        for (var site : snapshot) {
            SwallowedErrors.write(new Emission(destination, site.debug(),
                    site.site() + " shutdown total=" + site.total() + " pending=" + site.pending()
                            + " latest=" + site.latestType() + ": " + site.latestMessage(), null));
        }
    }

    private synchronized List<Site> allSites() {
        return List.copyOf(sites.values());
    }

    private void flush(Site site) {
        long now = clock.getAsLong();
        Emission emission;
        synchronized (this) {
            if (target == null) return;
            if (site.firstReportPending) {
                emission = new Emission(target, site.debug, site.name + " first occurrence; total=1", site.first);
                site.first = null;
                site.firstReportPending = false;
                site.lastReportNanos = now;
            } else if (site.pending > 0 && now - site.lastReportNanos >= SUMMARY_INTERVAL_NANOS) {
                emission = new Emission(target, site.debug,
                        site.name + " additional=" + site.pending + " total=" + site.total
                                + " latest=" + site.latestType + ": " + site.latestMessage, null);
                site.pending = 0;
                site.lastReportNanos = now;
            } else {
                return;
            }
        }
        // 先在锁内认领输出，再在锁外调用日志器，避免并发首报重复及外部回调死锁。
        SwallowedErrors.write(emission);
    }

    @FunctionalInterface
    interface Output {
        void log(boolean debug, String text, Throwable failure);
    }

    private record Target(String prefix, Output output) {
    }

    record Emission(Target target, boolean debug, String text, Throwable failure) {
        void write() {
            target.output.log(debug, target.prefix + " " + text, failure);
        }
    }

    private static final class Site {
        final String name;
        final boolean debug;
        long total;
        long pending;
        long lastReportNanos;
        String latestType;
        String latestMessage;
        boolean firstReportPending = true;
        Throwable first;

        Site(String name, boolean debug, Throwable first) {
            this.name = name;
            this.debug = debug;
            this.first = first;
        }

        SwallowedErrors.SiteSnapshot snapshot() {
            return new SwallowedErrors.SiteSnapshot(name, debug, total, pending,
                    latestType, latestMessage, firstReportPending);
        }
    }
}
