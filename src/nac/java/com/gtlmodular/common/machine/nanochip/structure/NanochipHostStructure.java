package com.gtlmodular.common.machine.nanochip.structure;

import com.gregtechceu.gtceu.api.block.ActiveBlock;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.pattern.MultiblockState;
import com.gtlmodular.common.data.nanochip.NanochipComplexPattern;
import com.gtlmodular.common.data.nanochip.NanochipModContext;
import com.gtlmodular.common.machine.nanochip.NanochipAssemblyComplexMachine;
import com.gtlmodular.common.machine.nanochip.NanochipExactPrecheck;
import com.gtlmodular.common.machine.nanochip.NanochipPatternLockHardening;
import com.gtlmodular.common.machine.nanochip.NanochipStructureCheckGate;
import com.gtlmodular.common.machine.nanochip.NanochipStructureCheckThrottle;
import com.gtlmodular.common.machine.nanochip.NanochipStructurePrecheck;
import com.gtlmodular.common.machine.nanochip.NanochipStructureRefresh;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.function.LongConsumer;

public final class NanochipHostStructure {

    private final NanochipAssemblyComplexMachine host;
    private final LongConsumer upstreamAsyncCheck;
    private final NanochipStructureDiagnostics diagnostics;

    /** 全量结构匹配与同步检查共用锁，必须限制重复扫描，避免阻塞服务端。 */
    private final NanochipStructureCheckThrottle structureCheckThrottle = new NanochipStructureCheckThrottle();

    private final NanochipStructurePrecheck structurePrecheck = new NanochipStructurePrecheck();

    /** 采样通过不代表整座结构正确；失败证据未变时限制全量重试，避免持续占用检查线程。 */
    private final NanochipStructureCheckGate structureCheckGate = new NanochipStructureCheckGate();

    /** 身份格能提前否决结构，能力格指纹则让仓室变化及时触发完整检查。 */
    private final NanochipExactPrecheck exactPrecheck = NanochipExactPrecheck.host();

    private volatile long foldTick = FOLD_NO_TICK;

    private volatile boolean foldValue;

    private volatile boolean foldDebt;

    /** 这里只比较相等；若拿 Long.MIN_VALUE 哨兵做时间差，减法会溢出。 */
    private static final long FOLD_NO_TICK = Long.MIN_VALUE;

    /** 结构重查异步完成，需要保留请求人才能在结果就绪后反馈。 */
    private ServerPlayer pendingRefreshRequester;

    /** 预检可能提前返回，必须另设反馈期限，避免请求人一直等待。 */
    private long refreshReportAtTick = Long.MAX_VALUE;

    private static final long REFRESH_REPORT_DELAY_TICKS = 40L;

    public NanochipHostStructure(NanochipAssemblyComplexMachine host, LongConsumer upstreamAsyncCheck,
            NanochipStructureDiagnostics diagnostics) {
        this.host = host;
        this.upstreamAsyncCheck = upstreamAsyncCheck;
        this.diagnostics = diagnostics;
    }

    public void resetCheckGate() {
        this.structureCheckGate.reset();
    }

    public void tickRefreshReport(Level level) {
        if (this.pendingRefreshRequester != null && level.getGameTime() >= this.refreshReportAtTick) {
            reportRefreshResult();
        }
    }

    public void asyncCheckPattern(long periodID) {
        final long startNanos = System.nanoTime();
        boolean attemptedUnformed = false;
        long signature = NanochipStructureCheckGate.UNKNOWN_SIGNATURE;
        MultiblockState gateState = null;
        if (host.isFormed()) {
            // 已成形结构的变更判定另有入口，周期兜底不应经过未成形侧的预检。
            if (!this.structureCheckThrottle.shouldRun(true)) {
                return;
            }
        } else {
            Level level = host.getLevel();
            if (level == null) {
                return;
            }
            this.structureCheckGate.noteUnformedPoll(host.getPos(),
                    NanochipStructureCheckGate.gtceuReachesFullMatchThisPoll(host.getHolder().getOffset(), periodID));
            NanochipStructurePrecheck.Verdict verdict =
                    this.structurePrecheck.check(level, host.getPos(), host.isFlipped());
            if (!verdict.mayForm()) {
                // 预检失败时不做昂贵匹配；缺件诊断由 tickSync 的限流审计提供。
                return;
            }
            signature = verdict.signature();
            NanochipExactPrecheck.Verdict exact =
                    this.exactPrecheck.scan(level, host.getPos(), host.getFrontFacing(), host.getUpwardsFacing());
            if (!exact.mayForm()) {
                return;
            }
            if (!exact.unknown()) {
                signature = exact.signature();
            }
            if (!this.structureCheckGate.shouldRunFullMatch(level, signature, startNanos)) {
                return;
            }
            if (!this.structureCheckThrottle.shouldRun(false)) {
                return;
            }
            gateState = host.getMultiblockState();
            attemptedUnformed = true;
        }
        upstreamAsyncCheck.accept(periodID);
        if (attemptedUnformed) {
            this.structureCheckGate.recordOutcome(signature, startNanos, System.nanoTime(),
                    !host.isFormed(), gateState);
            diagnostics.logUnformedAudit();
            reportRefreshResult();
        }
    }

    /** 异步与服务端检查都会经过此入口，必须在取锁和全量匹配前挡住确定失败的结构。 */
    public boolean checkPatternWithTryLock() {
        Level level = host.getLevel();
        if (level != null && !level.isClientSide
                && !this.exactPrecheck.scan(level, host.getPos(), host.getFrontFacing(), host.getUpwardsFacing()).mayForm()) {
            return false;
        }
        return NanochipPatternLockHardening.withTryLock(host, NanochipAssemblyComplexMachine.NAC_PATTERN_LOCK_HARD_FIX);
    }

    /** 同 tick 的多次变更会重复触发全量匹配；缓存成功结果后必须在下一 tick 补判。 */
    public boolean checkPatternWithLock() {
        if (NanochipModContext.config().isNanochipOriginalMode()) {
            return NanochipPatternLockHardening.withLock(host, NanochipAssemblyComplexMachine.NAC_PATTERN_LOCK_HARD_FIX);
        }
        Level level = host.getLevel();
        if (level == null || level.isClientSide) {
            return NanochipPatternLockHardening.withLock(host, NanochipAssemblyComplexMachine.NAC_PATTERN_LOCK_HARD_FIX);
        }
        final long tick = level.getGameTime();
        if (tick == this.foldTick) {
            final boolean cached = this.foldValue;
            if (cached) {
                // 缓存的成功结果可能已被后续变更推翻，下一 tick 必须补一次真实判定。
                this.foldDebt = true;
            }
            return cached;
        }
        final boolean result = NanochipPatternLockHardening.withLock(host, NanochipAssemblyComplexMachine.NAC_PATTERN_LOCK_HARD_FIX);
        this.foldValue = result;
        this.foldTick = tick;                                // volatile tick 写在结果之后，供读方识别已发布的缓存。
        this.foldDebt = false;
        return result;
    }

    /** 补判交给上游的方块变更入口，让失效清理与重新成形沿用同一条生命周期。 */
    public void confirmFoldedPattern() {
        if (!this.foldDebt) {
            return;
        }
        Level level = host.getLevel();
        if (level == null || level.isClientSide || !host.isFormed()) {
            this.foldDebt = false;
            return;
        }
        MultiblockState state = host.getMultiblockState();
        if (state == null) {
            this.foldDebt = false;
            return;
        }
        if (!host.getPatternLock().tryLock()) {
            // 不能在服务端等待异步匹配持有的锁；保留待办供下一 tick 重试。
            return;
        }
        try {
            // MultiblockState#getCache 会直接遍历 cache，读取前必须检查底层字段是否为空。
            if (state.cache == null) {
                this.foldDebt = false;
                return;
            }
            var cacheIterator = state.cache.longIterator();
            while (cacheIterator.hasNext()) {
                BlockPos pos = BlockPos.of(cacheIterator.nextLong());
                if (pos.equals(host.getPos())) {
                    continue;                                    // 控制器位置触发拆机分支，不能用作普通结构重检的触发格。
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
            host.getPatternLock().unlock();
        }
        this.foldDebt = false;
    }

    public void requestStructureRefresh(ServerPlayer requester) {
        this.pendingRefreshRequester = requester;
        this.structureCheckGate.reset();
        this.structureCheckThrottle.reset();
        diagnostics.resetUnformedAudit();
        Level lv = host.getLevel();
        this.refreshReportAtTick = lv == null ? Long.MAX_VALUE : lv.getGameTime() + REFRESH_REPORT_DELAY_TICKS;
        if (host.isFormed() && !host.getMultiblockState().hasError() && NanochipLoadRecovery.hasRegisteredParts(host)) {
            // 已成形且零件齐全时上游不会再扫，直接反馈；空零件表不能视为恢复成功。
            reportRefreshResult();
            return;
        }
        if (requester != null) {
            requester.sendSystemMessage(Component.translatable(NanochipStructureRefresh.LANG_REQUESTED)
                    .withStyle(ChatFormatting.YELLOW));
        }
    }

    public void reportRefreshResult() {
        ServerPlayer requester = this.pendingRefreshRequester;
        if (requester == null) {
            return;
        }
        this.pendingRefreshRequester = null;
        this.refreshReportAtTick = Long.MAX_VALUE;
        if (host.isFormed()) {
            requester.sendSystemMessage(Component.translatable(NanochipStructureRefresh.LANG_OK)
                    .withStyle(ChatFormatting.GREEN));
            return;
        }
        requester.sendSystemMessage(Component.translatable(NanochipStructureRefresh.LANG_FAIL)
                .withStyle(ChatFormatting.RED));
        Level lv = host.getLevel();
        List<String> audit = lv == null ? List.of() : NanochipComplexPattern.auditStructure(lv, host.getPos(), 8);
        if (audit.isEmpty()) {
            requester.sendSystemMessage(Component.translatable(NanochipStructureRefresh.LANG_FAIL_OTHER)
                    .withStyle(ChatFormatting.GRAY));
        } else {
            for (String line : audit) {
                requester.sendSystemMessage(Component.literal("§c· §7" + line));
            }
        }
    }
}
