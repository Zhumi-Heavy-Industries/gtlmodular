package com.gtlmodular.client;

import com.gtlmodular.GTLModular;
import com.mojang.brigadier.arguments.LongArgumentType;
import net.minecraft.commands.Commands;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 终端报告聊天链接的客户端命令：{@code /gtlmodular_terminal_report <id>}。
 *
 * <p>Forge 会在 {@code ClientPacketListener#sendCommand} 里先跑客户端命令
 * （ClientCommandHandler.runCommand），命中后不会发去服务器。
 */
@Mod.EventBusSubscriber(modid = GTLModular.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TerminalChatCommands {

    public static final String COMMAND = "gtlmodular_terminal_report";

    private TerminalChatCommands() {}

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(COMMAND)
                .then(Commands.argument("id", LongArgumentType.longArg())
                        .executes(context -> {
                            ShanhaiTerminalReportClient.open(LongArgumentType.getLong(context, "id"));
                            return 1;
                        })));
    }
}
