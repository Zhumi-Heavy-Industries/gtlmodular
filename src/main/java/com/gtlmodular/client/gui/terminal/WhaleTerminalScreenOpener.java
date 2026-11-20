package com.gtlmodular.client.gui.terminal;

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;

/** 打开哦鲸鲸终端设置屏（只应客户端调用）。 */
public final class WhaleTerminalScreenOpener {

    private WhaleTerminalScreenOpener() {}

    public static void open(InteractionHand hand) {
        // v31 印刷档案版三页自绘界面；旧的 WhaleTerminalScreen / PlateScreen 保留，便于回退对照。
        Minecraft.getInstance().setScreen(
                new com.gtlmodular.client.gui.terminal.print.TerminalScreen(hand, false));
    }
}
