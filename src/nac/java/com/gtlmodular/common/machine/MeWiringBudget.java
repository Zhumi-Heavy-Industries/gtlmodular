package com.gtlmodular.common.machine;

/** 接线重建的限次/限频/证伪状态机（纯 Java，便于单测）。 */
public final class MeWiringBudget {

    public enum Decision {
        /** 还没到下次尝试时机。 */
        WAITING,
        /** 可以尝试重建。 */
        ALLOWED,
        /** 次数用完或已判定无效，永久停手。 */
        STOPPED
    }

    /** 两次尝试之间至少间隔的 tick 数：重建会重复挂监听器，必须限频。 */
    public static final int COOLDOWN_TICKS = 100;

    /** 每台机器允许的重建总次数，用完即停手。 */
    public static final int MAX_ATTEMPTS = 32;

    /** 连续几次重建前后读数一字未变即判定无效。 */
    public static final int FUTILE_MAX = 2;

    private int budget = MAX_ATTEMPTS;
    private int attempts;
    private int futile;
    private long nextAttemptAt = -1L;
    private long lastAttemptAt = -1L;
    private int futileReadings = -1;
    private String stopReason;

    /** 只读询问此刻能不能重建，不改变任何状态。 */
    public Decision peek(long nowTick) {
        if (budget <= 0) return Decision.STOPPED;
        if (nextAttemptAt >= 0L && nowTick < nextAttemptAt) return Decision.WAITING;
        return Decision.ALLOWED;
    }

    /** 占一次额度并把冷却推到 {@link #COOLDOWN_TICKS} 之后；只能紧接在 {@link #peek} 返回 ALLOWED 后调用。 */
    public void start(long nowTick) {
        budget--;
        attempts++;
        lastAttemptAt = nowTick;
        nextAttemptAt = nowTick + COOLDOWN_TICKS;
    }

    /**
     * 记录重建结果。{@code before}/{@code after} 是「接线数 + 缓存配方数」的读数；
     * 连续 {@link #FUTILE_MAX} 次一字未变即永久停手。
     */
    public void record(int before, int after) {
        futileReadings = after;
        if (after == before) {
            futile++;
            if (futile >= FUTILE_MAX) stop("重建前后读数一字未变（" + after + "）");
        } else {
            futile = 0;
        }
    }

    /** 重建抛异常：立即永久停手。 */
    public void fail(String reason) {
        stop(reason);
    }

    private void stop(String reason) {
        budget = 0;
        stopReason = reason;
    }

    public boolean stopped() {
        return budget <= 0;
    }

    public int attempts() {
        return attempts;
    }

    public int remaining() {
        return Math.max(0, budget);
    }

    public long lastAttemptAt() {
        return lastAttemptAt;
    }

    public String stopReason() {
        return stopReason;
    }

    public int lastReading() {
        return futileReadings;
    }
}
