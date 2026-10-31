package com.gtlmodular.network;

import com.gtlmodular.client.ShanhaiTerminalReportClient;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 服务端收到报告命令后，通知客户端打开对应报告。 */
public record OpenTerminalReportPacket(long id) {

    public OpenTerminalReportPacket(FriendlyByteBuf buf) {
        this(buf.readLong());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeLong(id);
    }

    public static void handle(OpenTerminalReportPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        if (context.getDirection().getReceptionSide().isClient()) {
            context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> ShanhaiTerminalReportClient.open(packet.id())));
        }
        context.setPacketHandled(true);
    }
}
