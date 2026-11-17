package com.gtlmodular.client;

import com.gtlmodular.GTLModular;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.gtlmodular.client.gui.terminal.TerminalPreviewOpener;
import com.gtlmodular.common.data.GTLModularItems;
import com.gtlmodular.common.item.ShanhaiUltimateTerminalConfig;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
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
    public static final String PREVIEW_COMMAND = "gtlmodular_terminal_preview";

    private TerminalChatCommands() {}

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(PREVIEW_COMMAND).executes(context -> {
            var player = Minecraft.getInstance().player;
            if (player == null || Minecraft.getInstance().level == null) return 0;
            for (ItemStack stack : player.getInventory().items) {
                if (!stack.is(GTLModularItems.WHALE_TERMINAL.get())) continue;
                var bound = ShanhaiUltimateTerminalConfig.getBoundController(stack);
                if (bound == null || !bound.dimension().equals(Minecraft.getInstance().level.dimension())) continue;
                var machine = MetaMachine.getMachine(Minecraft.getInstance().level, bound.pos());
                if (machine instanceof MultiblockControllerMachine controller
                        && TerminalPreviewOpener.open(controller.getDefinition())) return 1;
            }
            return 0;
        }));
        event.getDispatcher().register(Commands.literal(COMMAND)
                .then(Commands.argument("id", LongArgumentType.longArg())
                        .executes(context -> {
                            ShanhaiTerminalReportClient.open(LongArgumentType.getLong(context, "id"));
                            return 1;
                        })));
    }
}
