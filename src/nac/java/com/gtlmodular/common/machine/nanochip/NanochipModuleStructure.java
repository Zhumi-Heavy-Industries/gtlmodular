package com.gtlmodular.common.machine.nanochip;

import com.gtlmodular.common.machine.nanochip.registration.NanochipModuleSpec;
import com.gtlmodular.common.data.nanochip.NanochipModContext;
import com.gregtechceu.gtceu.api.block.ActiveBlock;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.pattern.MultiblockState;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.BooleanSupplier;
import java.util.function.LongConsumer;

import static com.gtlmodular.common.machine.nanochip.NanochipModuleMachine.NAC_MODULE_ACTIVE_CONNECT;
import static com.gtlmodular.common.machine.nanochip.NanochipModuleMachine.NAC_MODULE_FORMATION_BY_UPSTREAM;

final class NanochipModuleStructure {

    private final NanochipModuleMachine module;
    private final NanochipModuleSpec kind;
    private final BooleanSupplier upstreamTryLock;
    private final BooleanSupplier upstreamLock;
    private final LongConsumer upstreamAsyncCheck;

    private final NanochipStructureCheckThrottle structureCheckThrottle = new NanochipStructureCheckThrottle();

    private final NanochipStructureCheckGate structureCheckGate = new NanochipStructureCheckGate();

    private NanochipExactPrecheck exactPrecheck;

    private volatile long foldTick = FOLD_NO_TICK;

    private volatile boolean foldValue;

    private volatile boolean foldDebt;

    private static final long FOLD_NO_TICK = Long.MIN_VALUE;

    private ServerPlayer pendingRefreshRequester;

    private static final long UNFORMED_LOG_INTERVAL_TICKS = 200L;

    /** 时间差用于限流，不能以 Long.MIN_VALUE 作哨兵而让减法溢出。 */
    private long lastUnformedLogTick = -10_000L;

    private final NanochipStructureLogGate structureLogGate = new NanochipStructureLogGate();

    private static final String LOG_CONCLUSION_FORMED = "已成形";

    private static final String LOG_CONCLUSION_INVALID = "结构失效";

    private static final String LOG_CONCLUSION_UNFORMED = "尚未成形";

    private boolean loggedFormed;

    NanochipModuleStructure(NanochipModuleMachine module, NanochipModuleSpec kind,
                            BooleanSupplier upstreamTryLock, BooleanSupplier upstreamLock,
                            LongConsumer upstreamAsyncCheck) {
        this.module = module;
        this.kind = kind;
        this.upstreamTryLock = upstreamTryLock;
        this.upstreamLock = upstreamLock;
        this.upstreamAsyncCheck = upstreamAsyncCheck;
    }

    private NanochipExactPrecheck exactPrecheck() {
        NanochipExactPrecheck local = this.exactPrecheck;
        if (local == null) {
            net.minecraft.resources.ResourceLocation id =
                    module.getDefinition() == null ? null : module.getDefinition().getId();
            if (id == null) {
                return null;
            }
            local = NanochipExactPrecheck.forModule(id.toString());
            if (local == null) {
                return null;                       // 缺少预检信息不等于结构错误，仍需交上游判定。
            }
            this.exactPrecheck = local;
        }
        return local;
    }

    boolean checkPatternWithTryLock() {
        if (kind.features().assembly() && !NanochipAssemblyMatrixTier.checkPattern(module)) {
            return false;
        }
        if (!NAC_MODULE_FORMATION_BY_UPSTREAM) {
            Level level = module.getLevel();
            if (level != null && !level.isClientSide) {
                NanochipExactPrecheck pre = exactPrecheck();
                if (pre != null
                        && !pre.scan(level, module.getPos(), module.getFrontFacing(), module.getUpwardsFacing()).mayForm()) {
                    return false;
                }
            }
        }
        return upstreamTryLock.getAsBoolean();
    }

    boolean checkPatternWithLock() {
        if (NAC_MODULE_FORMATION_BY_UPSTREAM) {
            return upstreamLock.getAsBoolean();
        }
        Level level = module.getLevel();
        if (level == null || level.isClientSide) {
            return upstreamLock.getAsBoolean();
        }
        final long tick = level.getGameTime();
        if (tick == this.foldTick) {
            final boolean cached = this.foldValue;            // 先读发布用的 volatile tick，再读对应结果。
            if (cached) {
                this.foldDebt = true;
            }
            return cached;
        }
        final boolean result = upstreamLock.getAsBoolean();
        this.foldValue = result;
        this.foldTick = tick;                                 // 结果先写，volatile tick 后写以发布缓存。
        this.foldDebt = false;
        return result;
    }

    /** 折叠成功结果后必须补判；拿不到锁时延后，避免阻塞服务端。 */
    void confirmFoldedPattern() {
        if (!this.foldDebt) {
            return;
        }
        Level level = module.getLevel();
        if (level == null || level.isClientSide || !module.isFormed()) {
            this.foldDebt = false;
            return;
        }
        MultiblockState state = module.getMultiblockState();
        if (state == null) {
            this.foldDebt = false;
            return;
        }
        if (!module.getPatternLock().tryLock()) {
            return;
        }
        try {
            // MultiblockState#getCache 直接遍历 cache，须先检查底层字段是否为空。
            if (state.cache == null) {
                this.foldDebt = false;
                return;
            }
            var cacheIterator = state.cache.longIterator();
            while (cacheIterator.hasNext()) {
                BlockPos pos = BlockPos.of(cacheIterator.nextLong());
                if (pos.equals(module.getPos())) {
                    continue;                                // 控制器位置会触发拆机分支，不能作为普通结构重检的触发格。
                }
                BlockState bs = level.getBlockState(pos);
                if (bs.getBlock() instanceof ActiveBlock
                        || level.getBlockEntity(pos) instanceof IMachineBlockEntity) {
                    continue;
                }
                this.foldDebt = false;
                state.onBlockStateChanged(pos, bs);
                return;
            }
        } finally {
            module.getPatternLock().unlock();
        }
        this.foldDebt = false;
    }

    void asyncCheckPattern(long periodID) {
        final long startNanos = System.nanoTime();
        boolean attemptedUnformed = false;
        MultiblockState gateState = null;
        if (module.isFormed()) {
            if (!this.structureCheckThrottle.shouldRun(true)) {
                return;
            }
        } else {
            if (NAC_MODULE_FORMATION_BY_UPSTREAM) {
                upstreamAsyncCheck.accept(periodID);
                return;
            }
            if (asyncCheckPatternUnformed(periodID, startNanos)) {
                gateState = module.getMultiblockState();
                attemptedUnformed = true;
            } else {
                return;
            }
        }
        upstreamAsyncCheck.accept(periodID);
        if (attemptedUnformed) {
            this.structureCheckGate.recordOutcome(NanochipStructureCheckGate.UNKNOWN_SIGNATURE,
                    startNanos, System.nanoTime(), !module.isFormed(), gateState);
        }
        // 从未成形的模块不会触发失效日志，需另提供限流的失败原因。
        if (!module.isFormed() && (attemptedUnformed || NAC_MODULE_FORMATION_BY_UPSTREAM)) {
            logUnformedReason();
        }
        if (attemptedUnformed
                || (NAC_MODULE_FORMATION_BY_UPSTREAM && !loggedFormed)) {
            reportRefreshResult();
        }
    }

    private boolean asyncCheckPatternUnformed(long periodID, long startNanos) {
        Level unformedLevel = module.getLevel();
        if (unformedLevel == null) {
            return false;
        }
        this.structureCheckGate.noteUnformedPoll(module.getPos(),
                NanochipStructureCheckGate.gtceuReachesFullMatchThisPoll(module.getHolder().getOffset(), periodID));
        if (!this.structureCheckGate.shouldRunFullMatch(unformedLevel,
                NanochipStructureCheckGate.UNKNOWN_SIGNATURE, startNanos)) {
            return false;
        }
        return this.structureCheckThrottle.shouldRun(false);
    }

    void requestStructureRefresh(ServerPlayer requester) {
        NanochipAssemblyMatrixTier.forget(module);
        this.pendingRefreshRequester = requester;
        this.structureCheckGate.reset();
        this.structureCheckThrottle.reset();
        this.lastUnformedLogTick = -10_000L;
        if (module.isFormed() && !module.getMultiblockState().hasError()) {
            reportRefreshResult();
            return;
        }
        if (requester != null) {
            requester.sendSystemMessage(Component.translatable(NanochipStructureRefresh.LANG_REQUESTED)
                    .withStyle(ChatFormatting.YELLOW));
        }
    }

    private void reportRefreshResult() {
        ServerPlayer requester = this.pendingRefreshRequester;
        if (requester == null) {
            return;
        }
        this.pendingRefreshRequester = null;
        if (module.isFormed()) {
            requester.sendSystemMessage(Component.translatable(NanochipStructureRefresh.LANG_OK)
                    .withStyle(ChatFormatting.GREEN));
            return;
        }
        requester.sendSystemMessage(Component.translatable(NanochipStructureRefresh.LANG_FAIL)
                .withStyle(ChatFormatting.RED));
        String detail = NanochipAssemblyComplexMachine.describeStructureFailure(module.getMultiblockState());
        requester.sendSystemMessage(Component.literal("§c· §7" + kind.nameZh() + " @"
                + module.getPos().toShortString() + "：" + (detail == null
                ? "GTCEu 没留下逐格原因：数量类失败 / 区块未加载等路径不写 error"
                : detail)));
    }

    private void logUnformedReason() {
        Level lv = module.getLevel();
        if (lv == null || lv.isClientSide || module.isFormed()) {
            return;
        }
        long now = lv.getGameTime();
        if (now - this.lastUnformedLogTick < UNFORMED_LOG_INTERVAL_TICKS) {
            return;
        }
        this.lastUnformedLogTick = now;
        String detail = NanochipAssemblyComplexMachine.describeStructureFailure(module.getMultiblockState());
        String reason = detail == null
                ? "GTCEu 没留下逐格原因：数量类失败 / 区块未加载等路径不写 error"
                : detail;
        // 多台模块可能长期报告同一错误，合并日志以减少重复写盘。
        if (!this.structureLogGate.shouldEmitInfo(LOG_CONCLUSION_UNFORMED, reason,
                structureLogLabel(), now)) {
            if (NanochipModContext.logger().isDebugEnabled()) {
                NanochipModContext.logger().debug("[gtlmodular] NAC 模块 尚未成形 @{}（{}）：{}",
                        module.getPos(), kind.nameZh(), reason);
            }
            return;
        }
        NanochipModContext.logger().info("[gtlmodular] NAC 模块 尚未成形 @{}（{}）：{}",
                module.getPos(), kind.nameZh(), reason);
    }

    private String structureLogLabel() {
        return kind.nameZh() + "@" + module.getPos().toShortString();
    }

    void clearFormedError() {
        // 外部结构提示读取错误状态，成形后须清除残留错误以免仍显示结构不完整。
        MultiblockState formedState = module.getMultiblockState();
        if (formedState != null && formedState.hasError()) {
            formedState.setError(null);
        }
    }

    void logFormed(boolean linked) {
        if (!loggedFormed) {
            loggedFormed = true;
            Level formedLogLevel = module.getLevel();
            long formedLogNow = formedLogLevel == null ? 0L : formedLogLevel.getGameTime();
            if (this.structureLogGate.shouldEmitInfo(LOG_CONCLUSION_FORMED, "",
                    structureLogLabel(), formedLogNow)) {
                NanochipModContext.logger().info("[gtlmodular] NAC 模块 已成形 @{}（{}，复合体连接={}）"
                                + "{}",
                        module.getPos(), kind.nameZh(), linked ? "是" : "否",
                        linked ? "" : (NAC_MODULE_ACTIVE_CONNECT
                                ? "（复合体尚未成形或还没把本机登记 —— 连接由复合体按结构 F 位注入，"
                                + "本机已催促其立刻重算一次，最坏 1 秒内生效）"
                                : "（复合体尚未成形或还没把本机登记 —— 连接由复合体自己按结构 F 位注入，"
                                + "它每 20 tick 自查一次 ⇒ 最坏 1 秒内生效）"));
            }
        }
    }

    void resetLogGate() {
        this.structureLogGate.reset();
    }

    void onStructureInvalid() {
        NanochipAssemblyMatrixTier.forget(module);
        if (loggedFormed) {
            loggedFormed = false;
            String detail = NanochipAssemblyComplexMachine.describeStructureFailure(module.getMultiblockState());
            String invalidReason = detail == null ? "" : detail;
            Level invalidLogLevel = module.getLevel();
            long invalidLogNow = invalidLogLevel == null ? 0L : invalidLogLevel.getGameTime();
            if (this.structureLogGate.shouldEmitInfo(LOG_CONCLUSION_INVALID, invalidReason,
                    structureLogLabel(), invalidLogNow)) {
                NanochipModContext.logger().info("[gtlmodular] NAC 模块 结构失效 @{}（{}，复合体连接={}；"
                                + "只在状态翻转时打这条，避免刷屏）{}",
                        module.getPos(), kind.nameZh(), module.hostConnected() ? "是" : "否",
                        detail == null ? "" : "；GTCEu 首个不符处：" + detail);
            }
        }
        this.structureCheckGate.reset();
    }
}
