package com.gtlmodular.client;

import com.gtlmodular.GTLModular;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** 离开世界/断线时清掉客户端缓存的终端报告，避免重进后主界面残留上次扫描结果。 */
@Mod.EventBusSubscriber(modid = GTLModular.MOD_ID, value = Dist.CLIENT)
public final class ShanhaiTerminalSessionListener {

    private ShanhaiTerminalSessionListener() {}

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ShanhaiTerminalReportClient.clear();
    }
}
