package com.gtlmodular.common.machine.nanochip.vacuum;

import com.gtlmodular.common.machine.nanochip.NanochipAssemblyComplexMachine;
import com.gtlmodular.common.machine.nanochip.NanochipVacuumNetwork;
import com.gtlmodular.common.machine.nanochip.NanochipPipeGraph;
import com.gtlmodular.common.data.nanochip.NanochipModContext;
import com.gtlmodular.common.machine.nanochip.display.NanochipLogisticsText;
import net.minecraft.nbt.CompoundTag;

public final class NanochipHostVacuum {

    private final NanochipAssemblyComplexMachine host;
    private final java.util.function.Consumer<String> publishSummary;
    private final NanochipVacuumNetwork vacuumNetwork = new NanochipVacuumNetwork();
    private final NanochipPipeGraph pipeGraph = new NanochipPipeGraph();
    private boolean vacuumDirty = true;
    private long lastVacuumTotal = -1L;

    public NanochipHostVacuum(NanochipAssemblyComplexMachine host,
                              java.util.function.Consumer<String> publishSummary) {
        this.host = host;
        this.publishSummary = publishSummary;
    }

    public NanochipVacuumNetwork vacuumNetwork() {
        return vacuumNetwork;
    }

    public NanochipPipeGraph pipeGraph() {
        return pipeGraph;
    }

    public static String routingModeText() {
        return NanochipLogisticsText.vacuumRouting().getString();
    }

    public void reconcileRoutingMode() {
        if (NanochipModContext.config().isVacuumRequiresPipes()) {
            return;
        }
        int moved = vacuumNetwork.flattenAll();
        if (moved > 0) {
            NanochipModContext.logger().info("[gtlmodular] 真空路由=简单模式：已把 {} 条分量池并回主通道（不丢件）@{}",
                    moved, host.getPos());
            host.markVacuumDirty();
        }
    }

    public void markVacuumDirty() {
        this.vacuumDirty = true;
    }

    public void updateSummary() {
        long vacuumTotal = vacuumNetwork.total();
        if (vacuumDirty || vacuumTotal != lastVacuumTotal) {
            vacuumDirty = false;
            lastVacuumTotal = vacuumTotal;
            publishSummary.accept(vacuumNetwork.summaryAll());
        }
    }

    public void markTopologyDirty() {
        pipeGraph.markDirty();
    }

    public void clearTopology() {
        pipeGraph.clear();
    }

    public void save(CompoundTag tag) {
        tag.put("GTLModularNanochipVacuum", vacuumNetwork.save());
    }

    public void load(CompoundTag tag) {
        if (tag.contains("GTLModularNanochipVacuum")) {
            vacuumNetwork.load(tag.getCompound("GTLModularNanochipVacuum"));
        }
    }
}
