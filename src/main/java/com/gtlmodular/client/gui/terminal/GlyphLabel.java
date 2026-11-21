package com.gtlmodular.client.gui.terminal;

import java.util.function.Supplier;

import com.gtlmodular.GTLModular;
import com.lowdragmc.lowdraglib.gui.widget.Widget;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * 用预烘的 Georgia 斜体字形图集绘制大字（仅 ASCII 32–126）。
 *
 * <p>Minecraft 自带位图字体做不到衬线斜体；把 3 倍像素的字形图集按 1/3 缩放着色绘制，
 * 既保留网页预览的字形，又不会像放大 8px 位图字体那样变成方块。
 *
 * <p>图集由 {@code gen_glyphs.py} 生成：16 列网格，单元格 118×125，
 * 字符顺序 ASCII 32..126，逐字步进见 {@link #ADVANCE}。
 */
final class GlyphLabel extends Widget {

    private static final ResourceLocation ATLAS =
            new ResourceLocation(GTLModular.MOD_ID, "textures/gui/terminal/nameglyphs.png");

    private static final int CELL_W = 128;
    private static final int CELL_H = 167;
    private static final int COLS = 16;
    private static final int IMG_W = 2048;
    private static final int IMG_H = 1002;
    /** 图集按 3 倍渲染，绘制时缩回逻辑像素。 */
    private static final float SHRINK = 1f / 2.7f;

    /** ASCII 32..126 的步进（图集像素）。 */
    private static final int[] ADVANCE = {
            29, 38, 50, 79, 58, 109, 96, 25, 58, 58, 58, 79, 32, 38, 38, 33,
            58, 58, 58, 58, 58, 58, 58, 58, 58, 58, 38, 38, 79, 79, 79, 44,
            109, 90, 82, 76, 90, 82, 82, 76, 96, 58, 70, 96, 76, 114, 96, 76,
            82, 76, 82, 70, 82, 96, 90, 114, 96, 96, 76, 58, 33, 58, 50, 59,
            39, 58, 50, 44, 58, 44, 38, 50, 58, 38, 38, 58, 32, 90, 64, 50,
            59, 50, 42, 44, 32, 64, 50, 76, 58, 52, 44, 57, 62, 57, 79
    };

    private final Supplier<String> text;
    private final int maxWidth;

    GlyphLabel(int x, int y, int w, int h, Supplier<String> text, int maxWidth) {
        super(x, y, w, h);
        this.text = text;
        this.maxWidth = maxWidth;
        setClientSideWidget();
    }

    private static boolean isAscii(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < 32 || c > 126) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        String value = text.get();
        if (value == null || value.isEmpty()) {
            return;
        }
        if (!isAscii(value)) {
            // 中文/非 ASCII 玩家名：图集没有这些字形，退回原版字体（不放大，避免方块感）
            graphics.drawString(net.minecraft.client.Minecraft.getInstance().font,
                    value, getPositionX(), getPositionY() + 6, 0xFF3A5BC2, false);
            return;
        }
        float unit = SHRINK;
        float pen = 0f;
        float limit = Math.max(0, maxWidth);

        graphics.pose().pushPose();
        graphics.pose().translate(getPositionX(), getPositionY(), 0);
        graphics.pose().scale(unit, unit, 1f);

        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch < 32 || ch > 126) {
                pen += CELL_W * 0.5f;
                continue;
            }
            int index = ch - 32;
            int adv = ADVANCE[index];
            if ((pen + adv) * unit > limit) {
                break;
            }
            int col = index % COLS;
            int row = index / COLS;
            // 目标位置用笔位 pen；图集取样位置才是格子坐标
            graphics.blit(ATLAS,
                    (int) pen, 0,
                    col * CELL_W, row * CELL_H,
                    CELL_W, CELL_H, IMG_W, IMG_H);
            pen += adv;
        }
        graphics.pose().popPose();
    }
}
