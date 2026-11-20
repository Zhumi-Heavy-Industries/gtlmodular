package com.gtlmodular.client.gui.terminal.print;

import com.gtlmodular.client.gui.terminal.print.TerminalTheme.Fam;

import net.minecraft.client.gui.GuiGraphics;

/**
 * 主界面右栏图版（v31 网页版 04_主界面_单页放大 的对位实现）。
 *
 * <p>设计稿这一块不是「实心钴蓝雷达盘」，而是纸白底上几乎看不见的构图：
 * 一圈虚线环、环外一段旋转铭文、几条很淡的钴蓝笔刷、几块遮档黑块、两个环上节点。
 * 之前版本按早期雷达稿画成大盘面，视觉重心完全压过文字，因此这里按 v31 重画。
 */
public final class TerminalPlate {

    private TerminalPlate() {}

    /** 图版构图中心与虚线环半径（设计像素）。 */
    private static final float CX = 408f;
    private static final float CY = 166f;
    private static final float RING = 100f;
    private static final float TEXT_R = 110f;

    private static int ink(int alpha) { return (alpha << 24) | 0x1F2427; }
    private static int cob(int alpha) { return (alpha << 24) | 0x3A5BC2; }
    private static int paper(int alpha) { return (alpha << 24) | 0xEFF1EF; }

    public static void draw(GuiGraphics g, float seconds, boolean hover) {
        float t = seconds;

        // 1. 两条很淡的钴蓝笔刷（网页里是背景水印级，不能盖过文字）
        band(g, 286, 262, 520, 76, 30, cob(7));
        band(g, 300, 74, 528, 246, 20, cob(6));
        band(g, 250, 200, 500, 150, 44, paper(120));

        // 2. 虚线环（1px，低墨量）
        TerminalDraw.dottedRing(g, CX, CY, RING, 1f,
                (float) Math.toDegrees(2.2f / RING), ink(78));

        // 3. 环上两个节点（纸白实心 + 墨线，缓慢呼吸）
        float pulse = 0.75f + 0.25f * (float) Math.sin(t * 1.3f);
        node(g, CX, CY - RING, 3.2f * pulse);
        node(g, CX - 34, CY + 94, 3.2f * pulse);

        // 4. 环外旋转铭文（56 秒/圈）
        ringText(g, CX, CY, TEXT_R, t * 0.45f,
                "GRECTECH LEISURE · LATERANO 1099 · FAITH · ORDER · FREEDOM · SIDESTORY 06 · ",
                4.6f, ink(100));

        // 5. 遮档黑块（网页里 5 块，位置对齐截图）
        TerminalDraw.rect(g, 318, 128, 19, 6, ink(190));
        TerminalDraw.rect(g, 449, 124, 17, 6, ink(190));
        TerminalDraw.rect(g, 310, 165, 16, 5, ink(190));
        TerminalDraw.rect(g, 423, 188, 15, 6, ink(190));
        TerminalDraw.rect(g, 529, 169, 13, 5, ink(190));

        // 6. 右缘一条测量线（网页左下角的横线对应物）
        TerminalDraw.hline(g, 520, 540, 176, ink(90));

        // 7. hover：虚框，与设计稿的 1px 钴蓝变换框一致
        if (hover) {
            TerminalDraw.dashedRect(g, CX - 118, CY - 118, 236, 236, 2.2f, 3.2f, cob(130));
        }
    }

    /** 用极低透明度的宽笔刷扫出斜向纸纹/钴蓝带。 */
    private static void band(GuiGraphics g, float x1, float y1, float x2, float y2,
                             float width, int color) {
        float dx = x2 - x1, dy = y2 - y1;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1f) return;
        float ux = -dy / len, uy = dx / len;
        int steps = 14;
        for (int i = 0; i <= steps; i++) {
            float p = i / (float) steps;
            float taper = (float) Math.sin(Math.PI * p);
            float w = width * (0.45f + 0.55f * taper);
            float cx = x1 + dx * p;
            float cy = y1 + dy * p;
            TerminalDraw.line(g, cx - ux * w / 2f, cy - uy * w / 2f,
                    cx + ux * w / 2f, cy + uy * w / 2f, 1f, color);
        }
    }

    private static void node(GuiGraphics g, float x, float y, float r) {
        TerminalDraw.fillCircle(g, x, y, r, paper(235));
        TerminalDraw.ring(g, x, y, r, 1f, ink(150));
    }

    /** 沿圆弧逐字排布并旋转（等价 SVG textPath）。 */
    private static void ringText(GuiGraphics g, float cx, float cy, float r, float startDeg,
                                 String text, float size, int color) {
        float angle = (float) Math.toRadians(startDeg);
        for (int i = 0; i < text.length(); i++) {
            String ch = String.valueOf(text.charAt(i));
            if (" ".equals(ch)) {
                angle += 4.2f / r;
                continue;
            }
            float a = angle;
            float x = cx + (float) Math.cos(a) * r;
            float y = cy + (float) Math.sin(a) * r;
            g.pose().pushPose();
            g.pose().translate(x, y, 0);
            g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees((float) Math.toDegrees(a) + 90f));
            TerminalFonts.draw(g, Fam.MONO, size, ch, -1.6f, -2.4f, color);
            g.pose().popPose();
            angle += 4.4f / r;
        }
    }
}
