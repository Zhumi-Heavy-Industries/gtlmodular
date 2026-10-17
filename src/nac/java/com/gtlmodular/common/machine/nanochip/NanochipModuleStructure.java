package com.gtlmodular.common.machine.nanochip;

import com.gtlmodular.common.machine.nanochip.registration.NanochipModuleSpec;
import com.gtlmodular.common.data.nanochip.NanochipModContext;
import com.gtlmodular.common.diag.SwallowedErrors;
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

    // ==========================================================================================
    // ★【模块塔身拆块兜底】—— 与主机的 NanochipHostStructureIndex 同款、同取舍：
    //   事件在方块真正被移除**之前**触发 ⇒ 只记坐标，下一 tick 读到新世界后才让上游重判。
    //   为什么需要：模块只能走上游 gtlcore 的 LevelMixin 事件路径，而那条路径要求
    //   `MultiblockState.cache` 里有这一格；读档/区块重载后缓存为空时，拆塔身外壳没有人重判
    //   ⇒ 模块一直显示已成形、主机也一直算它已连接、舱室的外壳连片纹理也就一直连着。
    //   ⚠ 本段**不产生**任何成形/失效结论：只调 `state.onBlockStateChanged(pos, 当前方块)`，
    //     跑不跑匹配、成不成形全部由上游自己的 checkPatternWithLock() 决定。
    // ==========================================================================================

    /** 待重判的移除坐标（{@code null} = 没有待办）。 */
    private volatile BlockPos pendingRemovedPos;

    /** 待办的存活窗口（tick）：区块没加载时等它加载，超时丢弃，避免陈旧事件无限滞留。 */
    private long pendingRemovalDeadline = -1L;

    private static final long PENDING_REMOVAL_WINDOW_TICKS = 200L;

    private static final String LOG_CONCLUSION_REMOVAL = "方块移除定点失效";

    private static final String LOG_REMOVAL_FORMAT =
            "[gtlmodular] NAC 模块 方块移除 ⇒ 定点重判 @{}（{}，移除格={}；"
                    + "跑不跑匹配、成形还是失效全部由 gtlcore 自己判）";

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
            final boolean upstream = upstreamLock.getAsBoolean();
            // 上游对 20 个装配格只要求「是某档外壳」，原地换档（/setblock、/fill replace、WorldEdit、
            // 直接 setBlockState）不会让它重新判档；档位缓存与「不同级不成形」只能在这里补一次，
            // 否则换成高层外壳的矩阵会一直按旧档拒绝配方，只有手动刷新结构或重进世界才恢复。
            if (!upstream || !kind.features().assembly()) {
                return upstream;
            }
            return NanochipAssemblyMatrixTier.checkPattern(module);
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

    /** 由 Forge 拆块事件登记（方块还没真的移除）：只记坐标，下一 tick 读到新世界后再请上游重判。 */
    void notePossibleStructureRemoval(BlockPos pos) {
        if (pos == null) {
            return;
        }
        this.pendingRemovedPos = pos.immutable();
        this.pendingRemovalDeadline = -1L;
    }

    /**
     * 每 tick 消费一次「塔身被拆」待办（由 {@code NanochipModuleMachine.tickHostState()} 调用）。
     *
     * <p>判定权完全在上游：本方法只把「这一格现在是 N 方块」交给
     * {@link MultiblockState#onBlockStateChanged(BlockPos, BlockState)}，
     * 成不成形、要不要断开连接全部由 gtlcore 自己的 {@code checkPatternWithLock()} 决定。
     * 拿不到 patternLock（异步匹配正在跑）就保留待办，下一 tick 重试。
     */
    void tickRemovalRecheck() {
        BlockPos broken = this.pendingRemovedPos;
        if (broken == null) {
            return;
        }
        Level level = module.getLevel();
        if (level == null || level.isClientSide) {
            dropPendingRemoval();
            return;
        }
        if (!module.isFormed()) {
            dropPendingRemoval();
            return;
        }
        if (!NanochipStructurePosIndex.structureChunksLoaded(level, module.getPos())) {
            // 未加载区块不能作为结构破坏证据，等加载完成再让上游重判。
            long now = level.getGameTime();
            if (this.pendingRemovalDeadline < 0L) {
                this.pendingRemovalDeadline = now + PENDING_REMOVAL_WINDOW_TICKS;
            } else if (now > this.pendingRemovalDeadline) {
                dropPendingRemoval();
            }
            return;
        }
        MultiblockState state = module.getMultiblockState();
        if (state == null) {
            dropPendingRemoval();
            return;
        }
        if (!module.getPatternLock().tryLock()) {
            return;
        }
        try {
            BlockState nowState = level.getBlockState(broken);
            dropPendingRemoval();
            logRemovalRecheck(broken, level.getGameTime());
            state.onBlockStateChanged(broken, nowState);
        } catch (RuntimeException e) {
            dropPendingRemoval();
            SwallowedErrors.report("NanochipModuleStructure#tickRemovalRecheck", e);
            NanochipModContext.logger().debug("[gtlmodular] NAC 模块 方块移除定点失效：本次重判定抛出异常，已放弃 @{}",
                    module.getPos(), e);
        } finally {
            module.getPatternLock().unlock();
        }
    }

    void dropPendingRemoval() {
        this.pendingRemovedPos = null;
        this.pendingRemovalDeadline = -1L;
    }

    private void logRemovalRecheck(BlockPos broken, long now) {
        String detail = "移除格=" + broken.toShortString();
        if (this.structureLogGate.shouldEmitInfo(LOG_CONCLUSION_REMOVAL, detail,
                structureLogLabel(), now)) {
            NanochipModContext.logger().info(LOG_REMOVAL_FORMAT, module.getPos(), kind.nameZh(), broken);
        } else if (NanochipModContext.logger().isDebugEnabled()) {
            NanochipModContext.logger().debug(LOG_REMOVAL_FORMAT, module.getPos(), kind.nameZh(), broken);
        }
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
        dropPendingRemoval();
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
