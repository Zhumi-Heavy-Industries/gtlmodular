package com.gtlmodular.network;

import com.gtlmodular.common.compat.EcoSubsystemBridge;
import com.gtlmodular.common.item.ShanhaiUltimateTerminalConfig;

import net.minecraft.core.GlobalPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * C→S：请服务器打开附近最近的一个已成型 ECO 计算子系统 UI。
 *
 * <p>eco 是软依赖：没装时只回一条聊天提示，不影响终端本身。
 */
public record OpenEcoSubsystemPacket(InteractionHand hand) {

    public OpenEcoSubsystemPacket(FriendlyByteBuf buffer) {
        this(buffer.readEnum(InteractionHand.class));
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeEnum(hand);
    }

    public static void handle(OpenEcoSubsystemPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;
            if (!EcoSubsystemBridge.isEcoPresent()) {
                player.sendSystemMessage(Component.translatable(
                        "message.gtlmodular.whale_terminal.subsystem.unavailable"));
                return;
            }
            ItemStack terminal = player.getItemInHand(packet.hand());
            GlobalPos bound = ShanhaiUltimateTerminalConfig.getBoundEco(terminal);
            if (bound != null) {
                if (!bound.dimension().equals(player.level().dimension())) {
                    player.sendSystemMessage(Component.translatable(
                            "message.gtlmodular.whale_terminal.subsystem.other_dimension",
                            bound.dimension().location().toString()));
                    return;
                }
                if (EcoSubsystemBridge.openBound(player, bound)) {
                    player.sendSystemMessage(Component.translatable(
                            "message.gtlmodular.whale_terminal.subsystem.remote_opened"));
                } else {
                    player.sendSystemMessage(Component.translatable(
                            "message.gtlmodular.whale_terminal.subsystem.remote_failed"));
                }
                return;
            }
            if (EcoSubsystemBridge.openNearestComputation(player)) {
                player.sendSystemMessage(Component.translatable(
                        "message.gtlmodular.whale_terminal.subsystem.opened"));
            } else {
                player.sendSystemMessage(Component.translatable(
                        "message.gtlmodular.whale_terminal.subsystem.none",
                        EcoSubsystemBridge.scanRadius()));
            }
        });
        context.setPacketHandled(true);
    }
}
