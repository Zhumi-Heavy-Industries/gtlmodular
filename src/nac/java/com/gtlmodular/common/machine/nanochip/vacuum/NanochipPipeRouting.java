package com.gtlmodular.common.machine.nanochip.vacuum;

import com.gtlmodular.common.machine.nanochip.NanochipColor;
import com.gtlmodular.common.machine.nanochip.NanochipPipeMachine;
import com.gtlmodular.common.machine.nanochip.NanochipVacuumHatchMachine;
import static com.gtlmodular.common.machine.nanochip.NanochipPipeGraph.UNWIRED;
import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/** 同色但不连通的管道要使用不同库存池，因此缓存每种颜色的连通分量。 */
public final class NanochipPipeRouting {

    /** 独立管道没有主机引用，用拓扑纪元通知各主机在下次查询时重建图。 */
    private static volatile long TOPOLOGY_EPOCH = 0L;

    public static void bumpTopologyEpoch() {
        TOPOLOGY_EPOCH++;
    }

    public static long topologyEpoch() {
        return TOPOLOGY_EPOCH;
    }

    private long seenEpoch = -1L;

    private final Map<NanochipColor, Map<BlockPos, Integer>> componentOf = new EnumMap<>(NanochipColor.class);

    private int nextComponent = 1;

    private boolean dirty = true;

    public void markDirty() {
        dirty = true;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void clear() {
        componentOf.clear();
        nextComponent = 1;
        dirty = true;
        seenEpoch = -1L;
    }

    private Map<BlockPos, Integer> map(NanochipColor color) {
        return componentOf.computeIfAbsent(color, c -> new HashMap<>());
    }

    public int componentOf(Level level, NanochipColor color, BlockPos portPos) {
        if (level == null || color == null || portPos == null) {
            return UNWIRED;
        }
        Map<BlockPos, Integer> cache = map(color);
        Integer known = cache.get(portPos);
        if (known != null) {
            return known;
        }
        long epoch = TOPOLOGY_EPOCH;
        if (dirty || seenEpoch != epoch) {
            for (Map<BlockPos, Integer> m : componentOf.values()) {
                m.clear();
            }
            dirty = false;
            seenEpoch = epoch;
        }
        return floodFill(level, color, portPos);
    }

    private int floodFill(Level level, NanochipColor color, BlockPos start) {
        Map<BlockPos, Integer> cache = map(color);

        boolean wired = false;
        for (Direction side : Direction.values()) {
            if (isPipeOf(level, start.relative(side), color)) {
                wired = true;
                break;
            }
        }
        if (!wired) {
            cache.put(start, UNWIRED);
            return UNWIRED;
        }

        int id = nextComponent++;
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        java.util.Set<BlockPos> seenPipes = new java.util.HashSet<>();
        queue.add(start);
        cache.put(start, id);

        while (!queue.isEmpty()) {
            BlockPos cur = queue.poll();
            for (Direction side : Direction.values()) {
                BlockPos np = cur.relative(side);
                if (isPipeOf(level, np, color)) {
                    if (seenPipes.add(np)) {
                        queue.add(np);
                    }
                } else if (isPortOf(level, np, color)) {
                    cache.putIfAbsent(np, id);
                }
            }
        }
        return id;
    }

    private static boolean isPipeOf(Level level, BlockPos pos, NanochipColor color) {
        return level.getBlockEntity(pos) instanceof MetaMachineBlockEntity be
                && be.getMetaMachine() instanceof NanochipPipeMachine pipe
                && pipe.color() == color;
    }

    private static boolean isPortOf(Level level, BlockPos pos, NanochipColor color) {
        return level.getBlockEntity(pos) instanceof MetaMachineBlockEntity be
                && be.getMetaMachine() instanceof NanochipVacuumHatchMachine hatch
                && hatch.color() == color;
    }
}
