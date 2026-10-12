package com.gtlmodular.client;

import com.gtlmodular.client.ctm.CtmClientSetup;
import com.gtlmodular.client.ctm.NacSlotRefreshTracker;
import com.gtlmodular.client.renderer.NacMachineShellRenderer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

public final class NanochipClientWiring {

    private static boolean registered;

    private NanochipClientWiring() {
    }

    public static synchronized void register() {
        if (registered) return;
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.register(CcItemColorSetup.class);
        modBus.register(CtmClientSetup.class);
        MinecraftForge.EVENT_BUS.register(NacSlotRefreshTracker.class);
        MinecraftForge.EVENT_BUS.register(NacMachineShellRenderer.class);
        registered = true;
    }
}
