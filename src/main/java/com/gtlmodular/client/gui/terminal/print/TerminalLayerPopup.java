package com.gtlmodular.client.gui.terminal.print;

import com.gtlmodular.client.gui.terminal.print.TerminalTheme.Fam;

import net.minecraft.client.gui.GuiGraphics;

/** 结构层数弹层（001–648、预设、应用写回）。 */
public final class TerminalLayerPopup {

    private TerminalLayerPopup() {}

    public static final int MAX = 648;

    public static void draw(GuiGraphics g, TerminalScreen s) {
        float x = 300, y = 124, w = 196, h = 150;
        TerminalDraw.rect(g, x + 2, y + 2, w, h, 0x381F2427);
        TerminalDraw.rect(g, x, y, w, h, TerminalTheme.PAPER);
        TerminalDraw.outline(g, x, y, w, h, TerminalTheme.INK);
        TerminalDraw.rect(g, x, y, w, 9, TerminalTheme.INK);
        TerminalFonts.drawMixed(g, Fam.MONO, 5f, "STRUCTURE / 结构层数", x + 5, y + 2, 1.2f, 0xFFFFFFFF);
        TerminalFonts.draw(g, Fam.MONO, 7f, "×", x + 180, y + 1, TerminalTheme.PAPER);
        s.hit(x + 178, y, 16, 10, "关闭", s::closeLayer, false);

        TerminalFonts.drawMixed(g, Fam.MONO, 5f, "默认 001 · 上限 648 · 步进 1",
                x + 6, y + 14, 0.6f, TerminalTheme.INK_FAINT);

        // − / 数字 / +
        button(g, s, x + 6, y + 26, 20, 20, "−", () -> s.layerDraft = Math.max(1, s.layerDraft - 1));
        button(g, s, x + 120, y + 26, 20, 20, "+", () -> s.layerDraft = Math.min(MAX, s.layerDraft + 1));
        TerminalFonts.drawCentered(g, Fam.MONO, 24f, String.format("%03d", s.layerDraft), x + 80, y + 19,
                TerminalTheme.COBALT);
        TerminalFonts.drawMixed(g, Fam.MONO, 6f, "/ 648", x + 148, y + 32, 0f, TerminalTheme.INK_FAINT);

        // 刻度尺
        float rx = x + 8, ry = y + 55, rw = 180;
        TerminalDraw.hline(g, rx, rx + rw, ry, TerminalTheme.RULE);
        for (int i = 0; i <= 9; i++) {
            float tx = rx + 20f * i;
            TerminalDraw.vline(g, tx, ry, ry + (i % 5 == 0 ? 8 : 5), 0xB055605F);
        }
        float cursor = rx + 2 + (s.layerDraft - 1) / (float) (MAX - 1) * (rw - 4);
        TerminalDraw.rect(g, cursor, ry - 2, 2, 10, TerminalTheme.COBALT);
        TerminalFonts.drawMixed(g, Fam.MONO, 5f, "1", rx, ry + 9, 0f, TerminalTheme.INK_FAINT);
        TerminalFonts.drawRight(g, Fam.MONO, 5f, String.valueOf(MAX), rx + rw, ry + 9, TerminalTheme.INK_FAINT);
        s.hit(rx, ry - 4, rw, 14, "刻度", s::layerFromMouse, false);

        // PRESET
        TerminalFonts.drawMixed(g, Fam.MONO, 5f, "PRESET / 预设", x + 6, y + 86, 1.2f, TerminalTheme.INK_FAINT);
        preset(g, s, x + 6, y + 96, 38, "001", 1);
        preset(g, s, x + 48, y + 96, 38, "008", 8);
        preset(g, s, x + 90, y + 96, 38, "064", 64);
        preset(g, s, x + 132, y + 96, 52, "MAX 648", MAX);

        // 取消 / 应用
        TerminalDraw.outline(g, x + 96, y + 122, 42, 18, TerminalTheme.INK);
        TerminalFonts.drawCenteredMixed(g, Fam.MONO, 7f, "取消", x + 117, y + 127, TerminalTheme.INK);
        s.hit(x + 96, y + 122, 42, 18, "取消", s::closeLayer, false);

        TerminalDraw.rect(g, x + 144, y + 122, 42, 18, TerminalTheme.INK);
        TerminalFonts.drawCenteredMixed(g, Fam.MONO, 7f, "应用", x + 165, y + 127, TerminalTheme.PAPER);
        s.hit(x + 144, y + 122, 42, 18, "应用", s::applyLayer, false);
    }

    private static void button(GuiGraphics g, TerminalScreen s, float x, float y, float w, float h,
                               String label, Runnable action) {
        boolean hover = s.isHover(x, y, w, h);
        TerminalDraw.outline(g, x, y, w, h, hover ? TerminalTheme.COBALT : TerminalTheme.INK);
        TerminalFonts.drawCentered(g, Fam.MONO, 9f, label, x + w / 2f, y + 5, TerminalTheme.INK);
        s.hit(x, y, w, h, label, action, false);
    }

    private static void preset(GuiGraphics g, TerminalScreen s, float x, float y, float w,
                               String label, int value) {
        boolean on = s.layerDraft == value;
        TerminalDraw.outline(g, x, y, w, 14, on ? TerminalTheme.COBALT : TerminalTheme.RULE);
        TerminalFonts.drawCentered(g, Fam.MONO, 6f, label, x + w / 2f, y + 4,
                on ? TerminalTheme.COBALT : TerminalTheme.INK_SOFT);
        s.hit(x, y, w, 14, label, () -> s.layerDraft = value, false);
    }
}
