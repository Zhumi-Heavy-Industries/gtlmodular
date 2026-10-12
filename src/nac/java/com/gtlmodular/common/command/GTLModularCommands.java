package com.gtlmodular.common.command;

import com.gtlmodular.common.data.nanochip.NanochipModContext;
import com.gtlmodular.common.data.nanochip.NanochipSelfCheck;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;

import java.util.List;

/** 静态检查无法确认运行注册表和数据包的加载结果，需要在服务器就绪后自检。 */
public final class GTLModularCommands {

    private static boolean registered;
    private static String commandName;

    private GTLModularCommands() {
    }

    public static void register() {
        register(MinecraftForge.EVENT_BUS, "gtlmodular_nac");
    }

    public static synchronized void register(IEventBus forgeBus, String name) {
        if (registered) return;
        commandName = java.util.Objects.requireNonNull(name, "commandName");
        forgeBus.addListener(GTLModularCommands::onRegisterCommands);
        forgeBus.addListener(GTLModularCommands::onServerStarted);
        registered = true;
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal(commandName)
                .executes(ctx -> {
                    try {
                        List<String> lines = NanochipSelfCheck.collect(ctx.getSource().getServer());
                        long failed = lines.stream().filter(l -> l.startsWith("✗")).count();
                        ctx.getSource().sendSuccess(() -> Component.literal(
                                "=== NAC 自检（" + lines.size() + " 项，失败 " + failed + " 项）==="), false);
                        for (String line : lines) {
                            ctx.getSource().sendSuccess(() -> Component.literal(line), false);
                        }
                    } catch (Throwable t) {
                        NanochipModContext.logger().warn("[gtlmodular] NAC 自检命令执行失败", t);
                        ctx.getSource().sendFailure(Component.literal("NAC 自检执行失败: " + t));
                    }
                    return 1;
                }));
    }

    private static void onServerStarted(ServerStartedEvent event) {
        try {
            NanochipSelfCheck.runAndLog(event.getServer());
        } catch (Throwable t) {
            NanochipModContext.logger().warn("[gtlmodular] NAC 自检自动执行失败（不影响游戏）", t);
        }
    }
}
