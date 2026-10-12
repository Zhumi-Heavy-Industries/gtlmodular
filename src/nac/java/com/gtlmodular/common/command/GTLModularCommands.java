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

/**
 * NAC 自检的入口：进世界自动跑一次（写进日志），也可以随时用命令重跑。
 *
 * <p><b>为什么</b>：静态自检能证明资源/配方文件自洽，但证明不了「注册表里真的有、
 * 数据包真的加载了、引用的流体真的存在」。这些只有跑起来才知道 ——
 * 而「进世界自动打日志」让这一层验证不需要玩家做任何操作就能完成。
 *
 * <p>命令：{@code /gtlmodular_nac}（权限等级 0，单人存档直接用）。
 */
public final class GTLModularCommands {

    private static boolean registered;
    private static String commandName;

    private GTLModularCommands() {
    }

    /** 在 @Mod 构造里调用一次。 */
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

    /** 服务器（含单人存档的集成服务器）启动完成后自动跑一次并写日志。 */
    private static void onServerStarted(ServerStartedEvent event) {
        try {
            NanochipSelfCheck.runAndLog(event.getServer());
        } catch (Throwable t) {
            NanochipModContext.logger().warn("[gtlmodular] NAC 自检自动执行失败（不影响游戏）", t);
        }
    }
}
