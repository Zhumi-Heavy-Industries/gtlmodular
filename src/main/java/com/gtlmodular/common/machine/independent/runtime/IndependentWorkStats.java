package com.gtlmodular.common.machine.independent.runtime;

/**
 * 仅用于调参的轻量统计：候选扫描和每条批次 tick 的耗时。
 * 默认关闭；开启后也只多做一次 nanoTime，不做日志刷屏。
 */
public final class IndependentWorkStats {
    private final boolean enabled;
    private long scans;
    private long candidates;
    private long scanNanos;
    private long maxScanNanos;
    private long ticks;
    private long tickNanos;
    private long maxTickNanos;

    public IndependentWorkStats(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean enabled() {
        return enabled;
    }

    public void scan(long nanos, int count) {
        if (!enabled) return;
        scans++;
        candidates += count;
        scanNanos += Math.max(0L, nanos);
        maxScanNanos = Math.max(maxScanNanos, nanos);
    }

    public void tick(long nanos) {
        if (!enabled) return;
        ticks++;
        tickNanos += Math.max(0L, nanos);
        maxTickNanos = Math.max(maxTickNanos, nanos);
    }

    public String summary() {
        if (!enabled) return "";
        return "扫描 " + scans + " 次/" + candidates + " 候选，平均 " + micros(scanNanos, scans)
                + "，最大 " + micros(maxScanNanos, 1)
                + "；批次 tick " + ticks + " 次，平均 " + micros(tickNanos, ticks)
                + "，最大 " + micros(maxTickNanos, 1);
    }

    public void reset() {
        scans = 0;
        candidates = 0;
        scanNanos = 0;
        maxScanNanos = 0;
        ticks = 0;
        tickNanos = 0;
        maxTickNanos = 0;
    }

    private static String micros(long nanos, long count) {
        if (count <= 0) return "0 µs";
        long average = nanos / count;
        return String.format(java.util.Locale.ROOT, "%.1f µs", average / 1000.0D);
    }
}
