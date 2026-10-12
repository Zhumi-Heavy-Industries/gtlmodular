package com.gtlmodular.common.machine.nanochip;

import com.gtlmodular.common.data.nanochip.NanochipModContext;
import com.gregtechceu.gtceu.api.pattern.MultiblockState;
import com.gregtechceu.gtceu.api.pattern.error.PatternError;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** 预检通过仍可能在其他格失败，依据指纹与失败格限制无变化的重复全量检查。 */
public final class NanochipStructureCheckGate {

    public static final long FLOOR_MS = 1_000L;

    /** 失败证据未变也要周期重试，因为只记录首个失败格不足以证明整座结构没有变化。 */
    public static final long UNCHANGED_RETRY_MS = 5_000L;

    /** 间隔随上次匹配耗时增加，避免慢匹配持续占满检查线程。 */
    public static final int DURATION_MULTIPLIER = 3;

    public static final long UNKNOWN_SIGNATURE = 0L;

    public static final int FASTPASS_MIN_INTERVAL_TICKS = 20;

    public static final long FASTPASS_MIN_INTERVAL_NANOS = FASTPASS_MIN_INTERVAL_TICKS * 50_000_000L;

    public static final long STATS_LOG_INTERVAL_MS = 10_000L;

    /** 上游还有轮询相位过滤，定期放行以免两层节流长期错开而饿死匹配。 */
    public static final long FASTPASS_PHASE_VALVE_MS = 4_000L;

    private static final long WASTED_PASS_MAX_NANOS = 50_000L;

    private boolean hasRun;

    private long lastSignature = UNKNOWN_SIGNATURE;

    private long lastMatchNanos;

    private long lastDurationMs;

    private boolean lastFailed;

    private BlockPos probePos;

    private BlockState probeState;

    private long lastFastPassNanos;

    private boolean lastPollReachesMatch = true;

    private long statsUnformedPolls;

    private long statsGateCalls;

    private long statsGrants;

    private long statsRealMatches;

    private long statsWastedGrants;

    private long statsFastPassSuppressed;

    private long statsPhaseSkipped;

    private long statsSuppressedNanos = -1L;

    private long statsDurationSumNanos;

    private long statsDurationMaxNanos;

    private long statsLastLogNanos;

    private BlockPos statsPos;

    public boolean shouldRunFullMatch(Level level, long signature, long nowNanos) {
        this.statsGateCalls++;
        if (NanochipModContext.config().isNanochipOriginalMode()) {
            return true;
        }
        if (!this.hasRun) {
            this.lastFastPassNanos = nowNanos;
            return true;
        }
        if (signature != this.lastSignature) {
            if (this.tryFastPass(nowNanos)) {
                return true;
            }
        }
        if (this.probePos != null && level != null && level.isLoaded(this.probePos)
                && level.getBlockState(this.probePos) != this.probeState) {
            if (this.tryFastPass(nowNanos)) {
                return true;
            }
        }
        long waitedMs = (nowNanos - this.lastMatchNanos) / 1_000_000L;
        return waitedMs >= effectiveFloorMs();
    }

    public long effectiveFloorMs() {
        if (!this.hasRun) {
            return 0L;
        }
        long base = this.lastFailed ? UNCHANGED_RETRY_MS : FLOOR_MS;
        return Math.max(base, (long) DURATION_MULTIPLIER * this.lastDurationMs);
    }

    private boolean tryFastPass(long nowNanos) {
        final long sinceLastPass = this.lastFastPassNanos == 0L
                ? Long.MAX_VALUE : nowNanos - this.lastFastPassNanos;
        // 上游本次不做匹配时不要消耗快速通道间隔，避免反复放行却始终没执行。
        if (!this.lastPollReachesMatch && sinceLastPass < FASTPASS_PHASE_VALVE_MS * 1_000_000L) {
            if (this.statsSuppressedNanos != nowNanos) {
                this.statsSuppressedNanos = nowNanos;
                this.statsPhaseSkipped++;
            }
            return false;
        }
        if (sinceLastPass < FASTPASS_MIN_INTERVAL_NANOS) {
            if (this.statsSuppressedNanos != nowNanos) {
                // 签名和失败格可能同时变化，同次调用只统计一次抑制。
                this.statsSuppressedNanos = nowNanos;
                this.statsFastPassSuppressed++;
            }
            return false;
        }
        this.lastFastPassNanos = nowNanos;
        return true;
    }

    public static boolean gtceuReachesFullMatchThisPoll(long machineOffset, long periodID) {
        return (machineOffset + periodID) % 4L == 0L;
    }

    public void noteUnformedPoll(BlockPos controllerPos, boolean reachesFullMatchThisPoll) {
        this.statsUnformedPolls++;
        this.statsPos = controllerPos;
        this.lastPollReachesMatch = reachesFullMatchThisPoll;
        long now = System.nanoTime();
        if (this.statsLastLogNanos == 0L) {
            this.statsLastLogNanos = now;
            return;
        }
        if (now - this.statsLastLogNanos >= STATS_LOG_INTERVAL_MS * 1_000_000L) {
            logStats(now);
        }
    }

    private void logStats(long nowNanos) {
        final long windowMs = (nowNanos - this.statsLastLogNanos) / 1_000_000L;
        final long polls = this.statsUnformedPolls;
        final long reached = this.statsGateCalls;
        final long bareReturns = polls > reached ? polls - reached : 0L;
        final long grants = this.statsGrants;
        final long realMatches = this.statsRealMatches;
        final long wasted = this.statsWastedGrants;
        final long denied = reached > grants ? reached - grants : 0L;
        final long floorDenied = denied > this.statsFastPassSuppressed + this.statsPhaseSkipped
                ? denied - this.statsFastPassSuppressed - this.statsPhaseSkipped : 0L;
        final long windowMaxMs = this.statsDurationMaxNanos / 1_000_000L;
        final long windowAvgMs = realMatches == 0L
                ? 0L : this.statsDurationSumNanos / realMatches / 1_000_000L;
        NanochipModContext.logger().info("[gtlmodular] 【A+ 统计学读数】NAC 结构检查 @{}：窗口 {} ms ｜ 未成形轮询 {} 次 ｜ "
                        + "裸 return（必然不成形，直接挡掉）{} 次 ｜ 放行真匹配 {} 次（闸授予 {} 次，其中被上游自带 "
                        + "(offset+periodID)%4 过滤吃掉 {} 次）｜ 最小间隔压住 {} 次 ｜ 相位跳过 {} 次 ｜ "
                        + "地板压住 {} 次 ｜ 真匹配单次耗时：最近 {} ms / 窗口最大 {} ms / 窗口平均 {} ms",
                this.statsPos == null ? "?" : this.statsPos.toShortString(),
                windowMs, polls, bareReturns, realMatches, grants, wasted,
                this.statsFastPassSuppressed, this.statsPhaseSkipped, floorDenied,
                this.lastDurationMs, windowMaxMs, windowAvgMs);
        this.statsUnformedPolls = 0L;
        this.statsGateCalls = 0L;
        this.statsGrants = 0L;
        this.statsRealMatches = 0L;
        this.statsWastedGrants = 0L;
        this.statsFastPassSuppressed = 0L;
        this.statsPhaseSkipped = 0L;
        this.statsDurationSumNanos = 0L;
        this.statsDurationMaxNanos = 0L;
        this.statsLastLogNanos = nowNanos;
    }

    public void recordOutcome(long signature, long startNanos, long endNanos, boolean failed,
                              MultiblockState state) {
        this.statsGrants++;
        final long durationNanos = Math.max(0L, endNanos - startNanos);
        if (durationNanos < WASTED_PASS_MAX_NANOS) {
            // 短耗时只是诊断启发式，不能用它决定后续是否允许匹配。
            this.statsWastedGrants++;
        } else {
            this.statsRealMatches++;
            this.statsDurationSumNanos += durationNanos;
            if (durationNanos > this.statsDurationMaxNanos) {
                this.statsDurationMaxNanos = durationNanos;
            }
        }
        this.hasRun = true;
        this.lastSignature = signature;
        this.lastMatchNanos = startNanos;
        this.lastDurationMs = Math.max(0L, (endNanos - startNanos) / 1_000_000L);
        this.lastFailed = failed;
        if (!failed) {
            this.probePos = null;
            this.probeState = null;
            return;
        }
        BlockPos pos = null;
        BlockState blockState = null;
        PatternError error = state == null ? null : state.error;
        if (error != null && error != MultiblockState.UNLOAD_ERROR && error != MultiblockState.UNINIT_ERROR) {
            try {
                BlockPos raw = error.getPos();
                Level world = state.getWorld();
                if (raw != null && world != null && world.isLoaded(raw)) {
                    pos = raw.immutable();
                    blockState = world.getBlockState(pos);
                }
            } catch (RuntimeException ignored) {
                pos = null;
                blockState = null;
            }
        }
        this.probePos = pos;
        this.probeState = blockState;
    }

    public long lastDurationMs() {
        return this.lastDurationMs;
    }

    public boolean hasRun() {
        return this.hasRun;
    }

    public boolean hasFailureEvidence() {
        return this.probePos != null;
    }

    /** 结构失效后旧证据无效，清空时间闸以便及时检测修复后的结构。 */
    public void reset() {
        this.hasRun = false;
        this.lastDurationMs = 0L;
        this.lastFailed = false;
        this.probePos = null;
        this.probeState = null;
        this.lastFastPassNanos = 0L;
        this.statsUnformedPolls = 0L;
        this.statsGateCalls = 0L;
        this.statsGrants = 0L;
        this.statsRealMatches = 0L;
        this.statsWastedGrants = 0L;
        this.statsFastPassSuppressed = 0L;
        this.statsPhaseSkipped = 0L;
        this.statsDurationSumNanos = 0L;
        this.statsDurationMaxNanos = 0L;
    }
}
