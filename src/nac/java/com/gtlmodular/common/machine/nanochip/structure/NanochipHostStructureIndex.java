package com.gtlmodular.common.machine.nanochip.structure;

import com.gregtechceu.gtceu.api.pattern.MultiblockState;
import com.gtlmodular.common.data.nanochip.NanochipComplexPattern;
import com.gtlmodular.common.data.nanochip.NanochipModContext;
import com.gtlmodular.common.machine.nanochip.NanochipAssemblyComplexMachine;
import com.gtlmodular.common.machine.nanochip.NanochipStructureLogGate;
import com.gtlmodular.common.machine.nanochip.NanochipStructurePosIndex;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public final class NanochipHostStructureIndex {

    private final NanochipAssemblyComplexMachine host;
    private final NanochipStructureLogGate structureLogGate;

    private volatile BlockPos pendingRemovedPos;

    private long pendingRemovalDeadline = -1L;

    /** 区块未加载时保留拆块待办，但限制存活窗口，避免陈旧事件无限滞留。 */
    private static final long PENDING_REMOVAL_WINDOW_TICKS = 200L;

    private ResourceKey<Level> structureIndexDim;

    private boolean structureCellsCrossChecked;

    private static final String REMOVAL_INVALIDATE_CONCLUSION = "方块移除定点失效";

    private static final String REMOVAL_INVALIDATE_LINE_FORMAT =
            "[gtlmodular] 纳米芯片组装复合体 方块移除 ⇒ 定点重判 @{}（移除格={}；"
                    + "跑不跑匹配、成形还是失效全部由 gtlcore 自己判）";

    public NanochipHostStructureIndex(NanochipAssemblyComplexMachine host,
            NanochipStructureLogGate structureLogGate) {
        this.host = host;
        this.structureLogGate = structureLogGate;
    }

    public void syncStructurePosIndex() {
        if (!NanochipStructurePosIndex.ENABLED) {
            return;
        }
        Level level = host.getLevel();
        if (level == null || level.isClientSide) {
            return;
        }
        boolean formed = host.isFormed();
        boolean registered = this.structureIndexDim != null;
        if (formed == registered) {
            return;
        }
        if (formed) {
            registerIntoStructureIndex(level);
        } else {
            unregisterFromStructureIndex();
        }
    }

    private void registerIntoStructureIndex(Level level) {
        NanochipStructurePosIndex.register(level, host);
        this.structureIndexDim = level.dimension();
    }

    /** 卸载时 getLevel 可能已不可用，必须用登记时保留的维度键注销索引。 */
    public void unregisterFromStructureIndex() {
        ResourceKey<Level> dim = this.structureIndexDim;
        this.structureIndexDim = null;
        NanochipStructurePosIndex.unregister(dim, host);
    }

    /** 方块移除事件可能早于实际移除，只登记待办，下一 tick 再检查。 */
    public void noteCachedBlockRemoved(BlockPos pos) {
        if (!NanochipStructurePosIndex.ENABLED || pos == null) {
            return;
        }
        this.pendingRemovedPos = pos.immutable();
        this.pendingRemovalDeadline = -1L;
    }

    public void invalidateOnCachedRemoval() {
        if (!NanochipStructurePosIndex.ENABLED) {
            return;
        }
        BlockPos broken = this.pendingRemovedPos;
        if (broken == null) {
            return;
        }
        Level level = host.getLevel();
        if (level == null || level.isClientSide) {
            dropPendingRemoval();
            return;
        }
        if (!host.isFormed()) {
            dropPendingRemoval();
            return;
        }
        if (!NanochipStructurePosIndex.structureChunksLoaded(level, host.getPos())) {
            // 未加载区块不能作为结构破坏证据，等加载完成再让上游重判。
            long now = level.getGameTime();
            if (this.pendingRemovalDeadline < 0L) {
                this.pendingRemovalDeadline = now + PENDING_REMOVAL_WINDOW_TICKS;
            } else if (now > this.pendingRemovalDeadline) {
                dropPendingRemoval();
            }
            return;
        }
        MultiblockState state = host.getMultiblockState();
        if (state == null) {
            dropPendingRemoval();
            return;
        }
        if (!host.getPatternLock().tryLock()) {
            return;
        }
        try {
            BlockState nowState = level.getBlockState(broken);
            dropPendingRemoval();
            logRemovalInvalidation(broken, level.getGameTime());
            state.onBlockStateChanged(broken, nowState);
        } catch (Throwable t) {
            dropPendingRemoval();
            NanochipModContext.logger().warn("[gtlmodular] NAC 方块移除定点失效：本次重判定抛出异常，已放弃 @{}", host.getPos(), t);
        } finally {
            host.getPatternLock().unlock();
        }
    }

    public void dropPendingRemoval() {
        this.pendingRemovedPos = null;
        this.pendingRemovalDeadline = -1L;
    }

    private void logRemovalInvalidation(BlockPos broken, long now) {
        String detail = "移除格=" + broken.toShortString();
        boolean emit = this.structureLogGate.shouldEmitInfo(REMOVAL_INVALIDATE_CONCLUSION, detail,
                "复合体@" + host.getPos().toShortString(), now);
        if (emit || NanochipModContext.logger().isDebugEnabled()) {
            if (emit) {
                NanochipModContext.logger().info(REMOVAL_INVALIDATE_LINE_FORMAT, host.getPos(), broken);
            } else {
                NanochipModContext.logger().debug(REMOVAL_INVALIDATE_LINE_FORMAT, host.getPos(), broken);
            }
        }
    }

    private void crossCheckStructureCells() {
        MultiblockState state = host.getMultiblockState();
        if (state == null) {
            return;
        }
        int upstream = state.cache == null ? -1 : state.cache.size();
        int ours = NanochipComplexPattern.hostCheckCells().length / 3;
        if (upstream != ours) {
            NanochipModContext.logger().warn("[gtlmodular] NAC 结构位置缓存自证不一致 @{}："
                            + "本机图案推出来的格数={}，上游 MultiblockState.cache 实测={} —— "
                            + "两者本应相等（依据见 NanochipComplexPattern.hostCheckCells() 的 javadoc）。"
                            + "本功能按**上游 cache 的语义**工作，不等时只会少覆盖/多覆盖几格："
                            + "多覆盖 ⇒ 多跑一次真匹配；少覆盖 ⇒ 那几格的移除要等上游自己的路径。"
                            + "不影响任何成形/失效判定。",
                    host.getPos(), ours, upstream);
        }
    }

    public void onStructureFormed() {
        if (NanochipStructurePosIndex.ENABLED) {
            Level idxLevel = host.getLevel();
            if (idxLevel != null && !idxLevel.isClientSide) {
                registerIntoStructureIndex(idxLevel);
                if (!this.structureCellsCrossChecked) {
                    this.structureCellsCrossChecked = true;
                    crossCheckStructureCells();
                }
            }
        }
    }
}
