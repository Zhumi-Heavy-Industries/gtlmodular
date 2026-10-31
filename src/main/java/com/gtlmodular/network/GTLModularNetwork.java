package com.gtlmodular.network;

import com.gtlmodular.GTLModular;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * 本模组网络通道（只服务哦鲸鲸终端：设置写回 / 报告下发 / 结构高亮）。
 *
 * <p>协议版本与通道名都独立于其他模组；新增包时 id 顺序追加，不要插队。
 */
public final class GTLModularNetwork {

    private static final String PROTOCOL_VERSION = "1";
    private static final ResourceLocation CHANNEL_NAME =
            new ResourceLocation(GTLModular.MOD_ID, "terminal");

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            CHANNEL_NAME,
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int packetId;

    private GTLModularNetwork() {}

    public static void init() {
        CHANNEL.registerMessage(packetId++,
                UltimateTerminalConfigPacket.class,
                UltimateTerminalConfigPacket::encode,
                UltimateTerminalConfigPacket::new,
                UltimateTerminalConfigPacket::handle);
        CHANNEL.registerMessage(packetId++,
                ShanhaiTerminalReportPacket.class,
                ShanhaiTerminalReportPacket::encode,
                ShanhaiTerminalReportPacket::new,
                ShanhaiTerminalReportPacket::handle);
        CHANNEL.registerMessage(packetId++,
                ShanhaiStructureHighlightPacket.class,
                ShanhaiStructureHighlightPacket::encode,
                ShanhaiStructureHighlightPacket::new,
                ShanhaiStructureHighlightPacket::handle);
        CHANNEL.registerMessage(packetId++,
                OpenEcoSubsystemPacket.class,
                OpenEcoSubsystemPacket::encode,
                OpenEcoSubsystemPacket::new,
                OpenEcoSubsystemPacket::handle);
        CHANNEL.registerMessage(packetId++,
                OpenTerminalReportPacket.class,
                OpenTerminalReportPacket::encode,
                OpenTerminalReportPacket::new,
                OpenTerminalReportPacket::handle);
    }
}
