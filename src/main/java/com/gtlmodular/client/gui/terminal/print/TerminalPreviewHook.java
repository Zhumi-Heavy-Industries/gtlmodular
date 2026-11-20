package com.gtlmodular.client.gui.terminal.print;

import com.gtlmodular.GTLModular;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 开发期自动开屏钩子：仅当 JVM 参数 {@code -Dgtlmodular.terminal.preview=1} 时，
 * 进入世界 60 tick 后自动打开指定页面，供自动截图脚本使用；正常游戏里完全不生效。
 */
@Mod.EventBusSubscriber(modid = GTLModular.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TerminalPreviewHook {

    private static int ticks;
    private static boolean opened;

    private TerminalPreviewHook() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!Boolean.getBoolean("gtlmodular.terminal.preview")) return;
        Minecraft mc = Minecraft.getInstance();
        // 标题界面也允许自动开屏：自动截图脚本进世界很慢，字体/版面校准不需要世界数据。
        boolean ready = mc.player != null && mc.level != null;
        boolean title = mc.screen instanceof net.minecraft.client.gui.screens.TitleScreen;
        if (!ready && !title) {
            ticks = 0;
            opened = false;
            return;
        }
        if (opened) return;
        if (++ticks < 60) return;
        opened = true;
        String page = System.getProperty("gtlmodular.terminal.page", "specimen");
        if ("specimen".equals(page)) {
            mc.setScreen(new TerminalSpecimenScreen());
        } else {
            mc.setScreen(new TerminalScreen(null, true));
        }
    }
}
