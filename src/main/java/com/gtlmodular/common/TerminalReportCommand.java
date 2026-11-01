package com.gtlmodular.common;

import com.gtlmodular.GTLModular;
import com.gtlmodular.network.GTLModularNetwork;
import com.gtlmodular.network.OpenTerminalReportPacket;

import com.mojang.brigadier.arguments.LongArgumentType;

import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/**
 * 聊天报告链接的服务端兜底命令。
 *
 * <p>正常情况下客户端 {@code RegisterClientCommandsEvent} 会拦截并直接打开界面；
 * 但部分环境下点击事件仍会把命令发到服务端，于是聊天栏出现“未知命令”。
 * 这里注册同名服务端命令：收到后只通知客户端打开报告，不再报未知命令。
 */
@Mod.EventBusSubscriber(modid = GTLModular.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TerminalReportCommand {

    private TerminalReportCommand() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("gtlmodular_terminal_report")
                .then(Commands.argument("id", LongArgumentType.longArg())
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            long id = LongArgumentType.getLong(context, "id");
                            GTLModularNetwork.CHANNEL.send(
                                    PacketDistributor.PLAYER.with(() -> player),
                                    new OpenTerminalReportPacket(id));
                            return 1;
                        })));
    }
}
