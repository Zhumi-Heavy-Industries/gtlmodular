package com.gtlmodular.common.diag;

import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class SwallowedErrorsEvents {

    private SwallowedErrorsEvents() {
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        SwallowedErrors.logShutdownSummary();
    }
}
