package com.gtlmodular.client.gui.terminal;

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;

/** 打开哦鲸鲸终端设置屏（只应客户端调用）。 */
public final class WhaleTerminalScreenOpener {

    private WhaleTerminalScreenOpener() {}

    public static void open(InteractionHand hand) {
        // 主界面改为 LDLib 印刷图版版；旧的 WhaleTerminalScreen 保留，便于对照与回退。
        Minecraft.getInstance().setScreen(new WhaleTerminalPlateScreen(hand));
    }
}
