package com.gtlmodular.client.gui.terminal;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * 「eco」（Neo ECO AE）风格色板与控件绘制。
 *
 * <p>取值来自整合包里 `neoecoae-20.5.0.jar` 的 `assets/neoecoae/lss/eco.lss`
 * 与它的 AE2 风格界面截图：浅灰紫面板、浅色 1px 斜面边框、内凹槽位、深色小字、
 * 金色只做重点强调（不再拿金边包所有卡片）；不引入 ModernUI 依赖。
 */
final class EcoStyle {

    private EcoStyle() {}

    static final int PANEL = 0xFF878FA5;
    static final int PANEL_2 = 0xFF7E869B;
    static final int PANEL_DARK = 0xFF5F5F7A;
    static final int PANEL_DEEP = 0xFF413F54;
    static final int SLOT = 0xFF9A9FB4;
    static final int SLOT_HI = 0xFFADB0C4;
    static final int EDGE_LIGHT = 0xFFE1E1EB;
    static final int EDGE_MID = 0xFFCBCCD4;
    static final int GOLD = 0xFFFFD363;
    static final int WHITE = 0xFFFFFFFF;
    static final int LIGHT = 0xFFE1E1EB;
    static final int TEXT = 0xFFE1E1EB;
    static final int TEXT_DIM = 0xFFC8CBDD;
    static final int TEXT_DARK = 0xFF2E2C3E;
    static final int VALUE = 0xFFE8A0E8;
    static final int WARN = 0xFFFFD363;
    static final int BLUE = 0xFF4A90D9;
    static final int DEEP = 0xFF170819;

    static boolean hit(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /** 主面板：外深 1px + 内亮 1px + 浅灰底 + 上下斜面；eco 不用金边包整块面板。 */
    static void panel(GuiGraphics g, int l, int t, int r, int b) {
        g.fill(l - 2, t - 2, r + 2, b + 2, PANEL_DEEP);
        g.fill(l - 1, t - 1, r + 1, b + 1, EDGE_LIGHT);
        g.fill(l, t, r, b, PANEL);
        g.fill(l, t, r, t + 1, EDGE_MID);
        g.fill(l, b - 1, r, b, PANEL_DARK);
    }

    /** 分区框：细深边 + 顶亮底暗，底色比面板略浅一点。 */
    static void box(GuiGraphics g, int l, int t, int w, int h) {
        g.fill(l - 1, t - 1, l + w + 1, t + h + 1, PANEL_DEEP);
        g.fill(l, t, l + w, t + h, PANEL_2);
        g.fill(l, t, l + w, t + 1, SLOT_HI);
        g.fill(l, t + h - 1, l + w, t + h, PANEL_DARK);
    }

    /** 物品槽/内凹格：eco 的 item-slot 观感。 */
    static void slot(GuiGraphics g, int l, int t, int w, int h, boolean hover) {
        g.fill(l - 1, t - 1, l + w + 1, t + h + 1, PANEL_DEEP);
        g.fill(l, t, l + w, t + h, hover ? SLOT_HI : SLOT);
        g.fill(l, t, l + w, t + 1, EDGE_LIGHT);
        g.fill(l, t + h - 1, l + w, t + h, PANEL_DARK);
        g.fill(l + w - 1, t, l + w, t + h, PANEL_DARK);
    }

    static void button(GuiGraphics g, int l, int t, int w, int h, boolean hover, boolean enabled) {
        int fill = !enabled ? PANEL_DARK : (hover ? SLOT_HI : SLOT);
        g.fill(l - 1, t - 1, l + w + 1, t + h + 1, PANEL_DEEP);
        g.fill(l, t, l + w, t + h, fill);
        g.fill(l, t, l + w, t + 1, enabled ? EDGE_LIGHT : PANEL_DARK);
        g.fill(l, t + h - 1, l + w, t + h, PANEL_DARK);
        if (hover && enabled) g.fill(l, t + h - 1, l + w, t + h, GOLD);
    }

    /** eco 的竖直拨杆：深色槽内一条蓝色亮条 + 底部滑块。 */
    static void verticalSwitch(GuiGraphics g, int x, int y, boolean on) {
        g.fill(x, y, x + 14, y + 22, PANEL_DEEP);
        g.fill(x + 1, y + 1, x + 13, y + 21, PANEL_DARK);
        g.fill(x + 2, y + 2, x + 12, y + 20, on ? BLUE : 0xFF6C6C8A);
        if (on) g.fill(x + 2, y + 2, x + 12, y + 6, 0xFF8FC4FF);
        g.fill(x + 1, y + 14, x + 13, y + 21, 0xFF8A8AA0);
        g.fill(x + 1, y + 14, x + 13, y + 15, PANEL_DEEP);
        g.fill(x + 3, y + 16, x + 11, y + 19, 0xFFC8CBDD);
    }

    static void title(GuiGraphics g, Font font, Component text, int x, int y) {
        g.drawString(font, text, x, y, WHITE, true);
    }

    static void centered(GuiGraphics g, Font font, Component text, int l, int w, int y, int color) {
        g.drawString(font, text, l + (w - font.width(text)) / 2, y, color, false);
    }

    static void centeredShadow(GuiGraphics g, Font font, Component text, int l, int w, int y, int color) {
        g.drawString(font, text, l + (w - font.width(text)) / 2, y, color, true);
    }

    static void centeredRaw(GuiGraphics g, Font font, String text, int l, int w, int y, int color) {
        g.drawString(font, text, l + (w - font.width(text)) / 2, y, color, false);
    }
}
