package com.gtlmodular.common.machine.nanochip;

import com.gtlmodular.common.machine.nanochip.vacuum.NanochipPipeRouting;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public final class NanochipPipeGraph {

    public static final int UNWIRED = 0;

    private final NanochipPipeRouting routing = new NanochipPipeRouting();

    public static void bumpTopologyEpoch() {
        NanochipPipeRouting.bumpTopologyEpoch();
    }

    public static long topologyEpoch() {
        return NanochipPipeRouting.topologyEpoch();
    }

    public void markDirty() {
        routing.markDirty();
    }

    public boolean isDirty() {
        return routing.isDirty();
    }

    public void clear() {
        routing.clear();
    }

    public int componentOf(Level level, NanochipColor color, BlockPos portPos) {
        return routing.componentOf(level, color, portPos);
    }
}
