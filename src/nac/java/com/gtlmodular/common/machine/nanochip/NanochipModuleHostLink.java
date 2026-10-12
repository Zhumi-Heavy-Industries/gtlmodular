package com.gtlmodular.common.machine.nanochip;

import net.minecraft.world.level.Level;

import java.util.function.BooleanSupplier;

final class NanochipModuleHostLink {

    private final NanochipModuleMachine module;
    private final BooleanSupplier upstreamReconnect;
    private final Runnable refreshHostSync;

    private NanochipAssemblyComplexMachine host;

    private static final int HOST_STATE_REFRESH_INTERVAL = 20;

    private long lastHostStateTick = Long.MIN_VALUE;

    NanochipModuleHostLink(NanochipModuleMachine module, BooleanSupplier upstreamReconnect,
                           Runnable refreshHostSync) {
        this.module = module;
        this.upstreamReconnect = upstreamReconnect;
        this.refreshHostSync = refreshHostSync;
    }

    boolean hostConnected() {
        if (!module.isFormed()) {
            return false;
        }
        NanochipAssemblyComplexMachine h = host;
        return h != null && h.isFormed() && h.isModuleConnected(module);
    }

    boolean findAndConnectToHost() {
        if (!NanochipModuleMachine.NAC_MODULE_ACTIVE_CONNECT) {
            return module.hostConnected();
        }
        return upstreamReconnect.getAsBoolean();
    }

    void requestHostResync() {
        if (!NanochipModuleMachine.NAC_MODULE_ACTIVE_CONNECT) {
            return;
        }
        Level level = module.getLevel();
        if (level == null || level.isClientSide) {
            return;
        }
        NanochipAssemblyComplexMachine h = host;
        if (h == null) {
            return;
        }
        h.requestCoalescedResync();
    }

    void onMachineRemoved() {
        NanochipAssemblyComplexMachine h = host;
        module.setHostPosition(null);
        module.setHost(null);
        if (NanochipModuleMachine.NAC_MODULE_ACTIVE_CONNECT && h != null) {
            h.requestCoalescedResync();
        }
    }

    NanochipAssemblyComplexMachine getHost() {
        return host;
    }

    void setHost(NanochipAssemblyComplexMachine host) {
        this.host = host;
    }

    void tickHostSync(long now) {
        if (lastHostStateTick != Long.MIN_VALUE && now - lastHostStateTick < HOST_STATE_REFRESH_INTERVAL) {
            return;
        }
        lastHostStateTick = now;
        refreshHostSync.run();
    }
}
