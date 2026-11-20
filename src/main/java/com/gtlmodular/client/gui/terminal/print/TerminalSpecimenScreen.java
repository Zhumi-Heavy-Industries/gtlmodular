package com.gtlmodular.client.gui.terminal.print;

import com.gtlmodular.client.gui.terminal.print.TerminalTheme.Fam;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 字体标本屏（开发期校准用）：按设计稿 15 档字号逐行打印中/英/斜体/等宽，附基线尺。
 *
 * <p>只在系统属性 {@code -Dgtlmodular.terminal.preview=1} 或客户端命令
 * {@code /gtlmodular_terminal_specimen} 下打开，不影响正常玩家。
 */
public final class TerminalSpecimenScreen extends Screen {

    private float scale = 1f;
    private float originX;
    private float originY;

    public TerminalSpecimenScreen() {
        super(Component.literal("Terminal Font Specimen"));
    }

    @Override
    protected void init() {
        super.init();
        this.scale = Math.min(1f, Math.min((width - 16f) / TerminalTheme.W, (height - 16f) / TerminalTheme.H));
        this.originX = (width - TerminalTheme.W * scale) / 2f;
        this.originY = (height - TerminalTheme.H * scale) / 2f;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, TerminalTheme.BACKDROP);
        g.pose().pushPose();
        g.pose().translate(originX, originY, 0);
        g.pose().scale(scale, scale, 1f);
        g.drawManaged(() -> drawSheet(g));
        g.pose().popPose();
    }

    private void drawSheet(GuiGraphics g) {
        TerminalDraw.rect(g, 0, 0, TerminalTheme.W, TerminalTheme.H, TerminalTheme.PAPER);
        TerminalDraw.outline(g, 0, 0, TerminalTheme.W, TerminalTheme.H, 0x401F2427);

        TerminalFonts.draw(g, Fam.MONO, 7f, "FONT SPECIMEN · 字体标本 · v31", 14, 10, TerminalTheme.INK);
        TerminalFonts.draw(g, Fam.MONO, 5f,
                "CN=STSong  SERIF=Georgia  ITALIC=Georgia Italic  MONO=Consolas  (fallback = vanilla)",
                14, 22, TerminalTheme.INK_FAINT);
        TerminalDraw.hline(g, 14, 546, 30, TerminalTheme.RULE);

        int rows = TerminalTheme.SIZES.length;
        float top = 38f;
        float step = (TerminalTheme.H - top - 14f) / rows;

        for (int i = 0; i < rows; i++) {
            float size = TerminalTheme.SIZES[i];
            float y = top + i * step;
            TerminalDraw.hline(g, 14, 546, y + step - 3, TerminalTheme.RULE_SOFT);
            TerminalFonts.drawRight(g, Fam.MONO, 5f, trim(size) + "px", 34, y + 1, TerminalTheme.INK_FAINT);
            TerminalFonts.draw(g, Fam.CN, size, "哦鲸鲸终端 结构层数 648", 40, y, TerminalTheme.INK);
            TerminalFonts.draw(g, Fam.MONO, size, "THE WHALE TERMINAL 001/648", 220, y, TerminalTheme.INK_SOFT);
            TerminalFonts.draw(g, Fam.SERIF_IT, size, "wangxiaoai", 392, y, TerminalTheme.COBALT);
            TerminalFonts.draw(g, Fam.SERIF, size, "Scan Report", 470, y, TerminalTheme.INK);
        }

        // 字距测试
        TerminalFonts.drawTracked(g, Fam.MONO, 6f, "LETTER SPACING 2.6", 14, 322, 2.6f, TerminalTheme.INK_SOFT);
        TerminalFonts.drawTracked(g, Fam.CN, 9f, "成型所需舱室", 160, 320, 2f, TerminalTheme.INK);
        TerminalFonts.drawVertical(g, Fam.MONO, 5.5f, "LATERANO", 536, 40, 2.6f, TerminalTheme.INK_FAINT);
        TerminalDraw.rect(g, 34, 318, 2, 12, TerminalTheme.COBALT);
    }

    private static String trim(float v) {
        return v == Math.round(v) ? String.valueOf((int) v) : String.valueOf(v);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
