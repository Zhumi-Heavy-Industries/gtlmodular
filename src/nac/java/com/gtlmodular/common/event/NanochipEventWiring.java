package com.gtlmodular.common.event;

import com.gtlmodular.client.NanochipClientWiring;
import com.gtlmodular.common.data.nanochip.NanochipPreviewSelfCheck;
import com.gtlmodular.common.data.nanochip.NanochipProxyTableByTypeAlias;
import com.gtlmodular.common.diag.SwallowedErrorsEvents;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;

public final class NanochipEventWiring {

    private static boolean registered;

    private NanochipEventWiring() {
    }

    public static synchronized void register(IEventBus modBus) {
        if (registered) return;
        modBus.register(NanochipPreviewSelfCheck.class);
        MinecraftForge.EVENT_BUS.register(NanochipBlockRemovalHandler.class);
        MinecraftForge.EVENT_BUS.register(NanochipSprayCanHandler.class);
        MinecraftForge.EVENT_BUS.register(NanochipProxyTableByTypeAlias.class);
        MinecraftForge.EVENT_BUS.register(SwallowedErrorsEvents.class);
        // 方法引用的目标必须留在客户端类内，专用服务端才不会解析渲染依赖。
        DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> NanochipClientWiring::register);
        registered = true;
    }
}
