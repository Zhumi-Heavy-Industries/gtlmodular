package com.gtlmodular.common.machine.pattern;

import com.gtlmodular.GTLModular;
import com.lowdragmc.lowdraglib.syncdata.managed.IRef;

import java.util.function.IntSupplier;
import java.util.function.Supplier;

public final class PatternClientSyncRefresh {

    private int clientRebakeLeft = 40;
    private int clientSyncDiagLeft = 6;

    public void onSyncChanged(IRef ref, boolean isClient, Supplier<?> machineId, Supplier<?> position,
                              IntSupplier controllerCount, Runnable scheduleRenderUpdate) {
        if (!isClient) {
            return;
        }
        String field = ref.getKey().getName();
        boolean controllerField = "controllerPositions".equals(field);
        boolean early = clientRebakeLeft > 0;
        if (!early && !controllerField) {
            return;
        }
        if (early) {
            clientRebakeLeft--;
        }
        if (clientSyncDiagLeft > 0) {
            clientSyncDiagLeft--;
            GTLModular.LOGGER.info("[gtlmodular] 客户端同步: {} pos={} 字段={} 控制器数={}",
                    machineId.get(), position.get(), field, controllerCount.getAsInt());
        }
        scheduleRenderUpdate.run();
    }
}
