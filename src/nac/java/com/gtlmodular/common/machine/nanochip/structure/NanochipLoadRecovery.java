package com.gtlmodular.common.machine.nanochip.structure;

import com.gtlmodular.common.diag.SwallowedErrors;
import com.gregtechceu.gtceu.api.block.ActiveBlock;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.pattern.MultiblockState;
import com.gregtechceu.gtceu.api.pattern.MultiblockWorldSavedData;
import com.gregtechceu.gtceu.api.pattern.error.PatternError;
import com.gtlmodular.common.data.nanochip.NanochipModContext;
import com.gtlmodular.common.machine.nanochip.NanochipAssemblyComplexMachine;
import com.gtlmodular.common.machine.nanochip.NanochipStructurePosIndex;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.function.IntSupplier;

public final class NanochipLoadRecovery {

    private final NanochipAssemblyComplexMachine host;
    private final IntSupplier tier;

    private static final long LOAD_PARTS_RECOVERY_WINDOW_TICKS = 40L;

    private static final long LOAD_PARTS_RECOVERY_RETRY_TICKS = 20L;

    /** 反复重建仍无零件时必须停机，避免永久占用全量匹配线程。 */
    private static final int LOAD_PARTS_INVALIDATE_MAX_CYCLES = 4;

    /** 异常可能发生在重建结果返回之前，需要另设异常次数上限才能终止重试。 */
    private static final int LOAD_PARTS_INVALIDATE_MAX_THROWN = 8;

    private boolean loadPartsRecoveryDone;

    private long loadPartsRecoveryDeadline = -1L;

    private long loadPartsRecoveryNextTry = -1L;

    private int loadPartsRecoveryAttempts;

    private long loadPartsRecoveryLastAttempt = -1L;

    private long loadPartsRecoveryDiagTick = -1L;

    private static final long LOAD_PARTS_RECOVERY_DIAG_INTERVAL_TICKS = 200L;

    private static final int LOAD_PARTS_RECOVERY_DIAG_BUDGET = 6;

    private int loadPartsRecoveryDiagCount;

    private long loadPartsInvalidateLastTick = -1L;

    private int loadPartsInvalidateCount;

    private boolean invalidateStructurePending;

    private int loadPartsInvalidateIneffective;

    private boolean loadPartsInvalidateGaveUp;

    /** 异常轮与重建无效轮分开计数，防止其中一种故障绕过另一种停机门槛。 */
    private int loadPartsInvalidateThrown;

    private long loadPartsRecoveryThrowLastLog = -1L;

    private static final int DESCRIBE_THROWABLE_FRAMES = 4;

    public NanochipLoadRecovery(NanochipAssemblyComplexMachine host, IntSupplier tier) {
        this.host = host;
        this.tier = tier;
    }

    public void recoverLoadPartsIfNeeded(boolean enabled, boolean invalidateEnabled) {
        if (!enabled || this.loadPartsRecoveryDone) {
            return;
        }
        Level level = host.getLevel();
        if (level == null || level.isClientSide) {
            return;
        }
        long now = level.getGameTime();
        if (this.loadPartsRecoveryDeadline < 0L) {
            this.loadPartsRecoveryDeadline = now + LOAD_PARTS_RECOVERY_WINDOW_TICKS;
        }
        if (!host.isFormed()) {
            this.invalidateStructurePending = false;
            this.loadPartsRecoveryNextTry = -1L;
            return;
        }
        if (hasRegisteredParts(host)) {
            this.loadPartsRecoveryDone = true;
            this.loadPartsRecoveryNextTry = -1L;
            this.invalidateStructurePending = false;
            this.loadPartsInvalidateIneffective = 0;          // 只有零件真正恢复才清零异常计数；动作返回成功不代表恢复已有进展。
            this.loadPartsInvalidateThrown = 0;
            return;
        }
        if (this.invalidateStructurePending) {
            this.invalidateStructurePending = false;
            this.loadPartsInvalidateIneffective++;
            if (this.loadPartsInvalidateIneffective >= LOAD_PARTS_INVALIDATE_MAX_CYCLES) {
                this.loadPartsRecoveryDone = true;
                if (!this.loadPartsInvalidateGaveUp) {
                    this.loadPartsInvalidateGaveUp = true;
                    NanochipModContext.logger().warn("[gtlmodular] NAC 部件恢复：连续 {} 轮「失效 ⇒ 上游重判」都重新成形了、"
                                    + "零件表却仍然为空，已停止自动恢复 @{}（已尝试失效={} 次，最近一次 gameTime={}）"
                                    + "—— 这一招在本环境不适用，请人工拆放控制器确认，并看前面那条"
                                    + "「NAC 部件未就绪诊断」行的 hasError/error/cache 分量",
                            LOAD_PARTS_INVALIDATE_MAX_CYCLES, host.getPos(),
                            this.loadPartsInvalidateCount, this.loadPartsInvalidateLastTick);
                }
                this.loadPartsRecoveryNextTry = -1L;
                return;
            }
        }
        if (now > this.loadPartsRecoveryDeadline) {
            if (this.loadPartsRecoveryNextTry < 0L) {
                this.loadPartsRecoveryNextTry = now + LOAD_PARTS_RECOVERY_RETRY_TICKS;
            } else if (now < this.loadPartsRecoveryNextTry) {
                return;
            }
        }
        MultiblockState state = host.getMultiblockState();
        if (state == null) {
            this.loadPartsRecoveryNextTry = now + LOAD_PARTS_RECOVERY_RETRY_TICKS;
            return;
        }
        if (!NanochipStructurePosIndex.structureChunksLoaded(level, host.getPos())) {
            // 区块未加载时重判会产生卸载错误，必须等待加载完成再恢复。
            long resumed = now + LOAD_PARTS_RECOVERY_WINDOW_TICKS;
            this.loadPartsRecoveryDeadline = Math.max(this.loadPartsRecoveryDeadline, resumed);
            this.loadPartsRecoveryNextTry = now + LOAD_PARTS_RECOVERY_RETRY_TICKS;
            diagnoseLoadPartsNotReady(now, state, false);
            return;
        }
        if (!host.getPatternLock().tryLock()) {
            // 不能在服务端等异步匹配锁，延后重试以免卡住服务器。
            this.loadPartsRecoveryDeadline = Math.max(this.loadPartsRecoveryDeadline,
                    now + LOAD_PARTS_RECOVERY_WINDOW_TICKS);
            this.loadPartsRecoveryNextTry = now + LOAD_PARTS_RECOVERY_RETRY_TICKS;
            diagnoseLoadPartsNotReady(now, state, false);
            return;
        }
        this.loadPartsRecoveryAttempts++;
        this.loadPartsRecoveryLastAttempt = now;
        // 恢复动作会改变 isFormed，诊断必须在动作前采集才能保留故障现场。
        try {
            diagnoseLoadPartsNotReady(now, state, false);
            if (!invalidateEnabled) {
                if (requestUpstreamStructureRecheck(level, state, now)) {
                    return;
                }
                this.loadPartsRecoveryNextTry = now + LOAD_PARTS_RECOVERY_RETRY_TICKS;
                return;
            }
            if (invalidateThenRequestUpstreamRebuild(level, state, now)) {
                return;
            }
            this.loadPartsRecoveryNextTry = now + LOAD_PARTS_RECOVERY_RETRY_TICKS;
        } catch (Throwable t) {
            SwallowedErrors.report("NanochipLoadRecovery#recoverLoadPartsIfNeeded", t);
            this.loadPartsRecoveryNextTry = now + LOAD_PARTS_RECOVERY_RETRY_TICKS;
            this.loadPartsInvalidateThrown++;
            boolean firstEver = this.loadPartsRecoveryThrowLastLog < 0L;
            boolean dueByTime = firstEver
                    || now - this.loadPartsRecoveryThrowLastLog >= LOAD_PARTS_RECOVERY_DIAG_INTERVAL_TICKS;
            if (dueByTime) {
                this.loadPartsRecoveryThrowLastLog = now;
                NanochipModContext.logger().warn("[gtlmodular] NAC 读档部件恢复：本次「失效 ⇒ 交回上游重判」抛出异常"
                                + "（连续第 {} 轮；本机首次={}，两次日志至少隔 {} tick；"
                                + "请照下面这条栈定位 —— 异常类型 / message / 栈顶若干帧）"
                                + "，已保留重试 @{}",
                        this.loadPartsInvalidateThrown, firstEver,
                        LOAD_PARTS_RECOVERY_DIAG_INTERVAL_TICKS, host.getPos(), t);
            }
            if (this.loadPartsInvalidateThrown >= LOAD_PARTS_INVALIDATE_MAX_THROWN) {
                this.loadPartsRecoveryDone = true;
                this.loadPartsRecoveryNextTry = -1L;
                this.invalidateStructurePending = false;
                if (!this.loadPartsInvalidateGaveUp) {
                    this.loadPartsInvalidateGaveUp = true;
                    NanochipModContext.logger().warn("[gtlmodular] NAC 部件恢复：连续 {} 轮「失效 ⇒ 交回上游重判」都抛出异常，"
                                    + "已停止自动恢复 @{}（已尝试={}，累计失效={}，最近一次 gameTime={}）"
                                    + "—— 这不是抖动，是这一招在本环境不适用：请按上面最后一条栈定位"
                                    + "（栈顶那一帧就是抛出点），并对照同一条「NAC 部件未就绪诊断」行的"
                                    + "结构区块全加载 / cache 格数 / hasError 分量；未定位前不要再自动重试",
                            LOAD_PARTS_INVALIDATE_MAX_THROWN, host.getPos(), this.loadPartsRecoveryAttempts,
                            this.loadPartsInvalidateCount, this.loadPartsInvalidateLastTick);
                }
            }
        } finally {
            host.getPatternLock().unlock();
        }
    }

    private boolean invalidateThenRequestUpstreamRebuild(Level level, MultiblockState state, long now) {
        if (level instanceof ServerLevel serverLevel) {
            this.loadPartsInvalidateCount++;
            this.loadPartsInvalidateLastTick = now;
            // 只有失效与重新排队都完成后，才能记为等待上游恢复，避免异常造成假待办。
            host.onStructureInvalid();
            MultiblockWorldSavedData worldData = MultiblockWorldSavedData.getOrCreate(serverLevel);
            worldData.removeMapping(state);
            worldData.addAsyncLogic(host);               // 失效后必须重入异步队列，否则不会再检查结构。
            this.invalidateStructurePending = true;
            NanochipModContext.logger().info("[gtlmodular] NAC 读档部件恢复：已让结构失效并交回上游重判 @{} "
                            + "（已成形但零件表为空；累计失效={}，gameTime={}）",
                    host.getPos(), this.loadPartsInvalidateCount, now);
            return true;
        }
        return false;
    }

    private boolean requestUpstreamStructureRecheck(Level level, MultiblockState state, long now) {
        // MultiblockState#getCache 直接遍历底层 cache，底层为空时不能调用。
        if (state.cache == null || state.cache.isEmpty()) {
            return false;
        }
        var cacheIterator = state.cache.longIterator();
        while (cacheIterator.hasNext()) {
            BlockPos pos = BlockPos.of(cacheIterator.nextLong());
            if (pos.equals(host.getPos())) {
                continue;
            }
            BlockState bs = level.getBlockState(pos);
            if (bs.getBlock() instanceof ActiveBlock
                    || level.getBlockEntity(pos) instanceof IMachineBlockEntity) {
                continue;
            }
            NanochipModContext.logger().info("[gtlmodular] NAC 读档部件恢复：已补一次结构重判定 @{} "
                            + "（已成形但零件表为空；触发格={}，gameTime={}，累计尝试={}）",
                    host.getPos(), pos, now, this.loadPartsRecoveryAttempts);
            state.onBlockStateChanged(pos, bs);
            return true;
        }
        return false;
    }

    private void diagnoseLoadPartsNotReady(long now, MultiblockState state, boolean actionRefusedNoLever) {
        if (!NanochipModContext.logger().isInfoEnabled()) {
            return;
        }
        if (this.loadPartsRecoveryDiagCount >= LOAD_PARTS_RECOVERY_DIAG_BUDGET) {
            return;
        }
        if (this.loadPartsRecoveryDiagTick >= 0L
                && now - this.loadPartsRecoveryDiagTick < LOAD_PARTS_RECOVERY_DIAG_INTERVAL_TICKS) {
            return;
        }
        this.loadPartsRecoveryDiagTick = now;
        this.loadPartsRecoveryDiagCount++;
        List<IMultiPart> parts = host.getParts();
        int cache;
        try {
            cache = state.cache == null ? -1 : state.cache.size();
        } catch (RuntimeException e) {
            cache = -2;
        }
        boolean hasError;
        String errorName;
        try {
            PatternError error = state.error;
            hasError = error != null;
            errorName = hasError ? String.valueOf(error) : "无";
        } catch (RuntimeException e) {
            hasError = true;
            errorName = "读状态时抛 " + describeThrowable(e);
        }
        Level world = host.getLevel();
        boolean chunksLoaded = NanochipStructurePosIndex.structureChunksLoaded(world, host.getPos());
        NanochipModContext.logger().info("[gtlmodular] NAC 部件未就绪诊断 @{}：零件数={}，isFormed={}，"
                        + "显示等级字段 tier={}，hasError={}，error={}，cache 格数={}，结构区块全加载={}，"
                        + "窗口截止={}，上次尝试={}，已尝试={}，已失效={}，上次失效={}，连续无效={}，"
                        + "连续抛异常={}，"
                        + "本 tick 未能交出重判={}，gameTime={} —— 面板「结构部件尚未就绪」的判据是上游 "
                        + "isRecipeLogicAvailable()（= isFormed && !hasError，不看零件表）；"
                        + "显示等级只由 onStructureFormed 时按能源仓写下一次 ⇒ 部件没回来时那一行不可信",
                host.getPos(), parts == null ? -1 : parts.size(), host.isFormed(), tier.getAsInt(),
                hasError, errorName, cache, chunksLoaded,
                this.loadPartsRecoveryDeadline, this.loadPartsRecoveryLastAttempt,
                this.loadPartsRecoveryAttempts, this.loadPartsInvalidateCount,
                this.loadPartsInvalidateLastTick, this.loadPartsInvalidateIneffective,
                this.loadPartsInvalidateThrown,
                actionRefusedNoLever, now);
    }

    private static String describeThrowable(Throwable t) {
        if (t == null) {
            return "?（异常对象为 null）";
        }
        StringBuilder sb = new StringBuilder(128);
        sb.append(t.getClass().getName());
        String message = t.getMessage();
        if (message != null && !message.isEmpty()) {
            sb.append(": ").append(message);
        }
        StackTraceElement[] frames;
        try {
            frames = t.getStackTrace();
        } catch (RuntimeException e) {
            frames = null;
        }
        sb.append(" @栈顶[");
        if (frames == null || frames.length == 0) {
            sb.append("无栈（该异常未 fillInStackTrace / 取栈失败）");
        } else {
            int printed = 0;
            for (int i = 0; i < frames.length && printed < DESCRIBE_THROWABLE_FRAMES; i++) {
                if (printed > 0) {
                    sb.append(" < ");
                }
                sb.append(frames[i].getClassName()).append('.').append(frames[i].getMethodName())
                        .append(':').append(frames[i].getLineNumber());
                printed++;
            }
        }
        sb.append(']');
        return sb.toString();
    }

    public static boolean hasRegisteredParts(NanochipAssemblyComplexMachine host) {
        List<IMultiPart> parts = host.getParts();
        return parts != null && !parts.isEmpty();
    }
}
