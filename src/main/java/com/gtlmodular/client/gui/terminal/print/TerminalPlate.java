package com.gtlmodular.client.gui.terminal.print;

import com.gtlmodular.client.gui.terminal.print.TerminalTheme.Fam;

import net.minecraft.client.gui.GuiGraphics;

/**
 * 主界面大圆盘：官方 1000×1000 坐标系按 0.27 等比映射到显示区 (264,26,270,270)。
 *
 * <p>实现方式：内部坐标经 {@code sx/sy/sr} 直接换算成设计像素后绘制，
 * 避免 pose 缩放把 1px 线压成亚像素（Minecraft 没有抗锯齿）。
 */
public final class TerminalPlate {

    private TerminalPlate() {}

    public static final float X = 264f;
    public static final float Y = 26f;
    public static final float K = 0.27f;
    public static final float CX = 520f;
    public static final float CY = 470f;
    public static final float R = 236f;

    private static float sx(float x) { return X + x * K; }
    private static float sy(float y) { return Y + y * K; }
    private static float sr(float r) { return r * K; }

    private static int ink(int alpha) { return (alpha << 24) | 0x1F2427; }
    private static int cob(int alpha) { return (alpha << 24) | 0x3A5BC2; }
    private static int paper(int alpha) { return (alpha << 24) | 0xEFF1EF; }

    public static void draw(GuiGraphics g, float seconds, boolean hover, int[] flags) {
        float t = seconds;
        // L1 镜像字母 N
        g.pose().pushPose();
        g.pose().translate(sx(CX), sy(430), 0);
        g.pose().scale(-1f, 1f, 1f);
        TerminalFonts.draw(g, Fam.SERIF, 36f, "N", -9, -18, ink(16));
        g.pose().popPose();

        // L1 S 形扫掠笔刷
        brush(g, 40, 860, 980, 260, 70, 150, 0.82f, cob(26));

        // L2 外圈
        TerminalDraw.ring(g, sx(CX), sy(CY), sr(360), 1f, ink(66));
        TerminalDraw.ring(g, sx(CX), sy(CY), sr(300), 1f, ink(56));
        // L2 放射线 72 根（每 5°）
        for (int i = 0; i < 72; i += 2) {
            double a = Math.toRadians(i * 5);
            TerminalDraw.line(g, sx(CX) + (float) Math.cos(a) * sr(300), sy(CY) + (float) Math.sin(a) * sr(300),
                    sx(CX) + (float) Math.cos(a) * sr(520), sy(CY) + (float) Math.sin(a) * sr(520),
                    1f, ink(54));
        }

        // L3 放射楔形 40 个
        for (int i = 0; i < 40; i++) {
            double a0 = Math.toRadians(i * 9 + 1.5);
            double a1 = Math.toRadians(i * 9 + 5.5);
            float rOut = 320 + (i % 5) * 38;
            float[] pts = {
                    sx(CX) + (float) Math.cos(a0) * sr(262), sy(CY) + (float) Math.sin(a0) * sr(262),
                    sx(CX) + (float) Math.cos(a0) * sr(rOut), sy(CY) + (float) Math.sin(a0) * sr(rOut),
                    sx(CX) + (float) Math.cos(a1) * sr(rOut), sy(CY) + (float) Math.sin(a1) * sr(rOut),
                    sx(CX) + (float) Math.cos(a1) * sr(262), sy(CY) + (float) Math.sin(a1) * sr(262),
            };
            TerminalDraw.polygon(g, pts, ink(40 + (i % 4) * 10));
        }

        // L3 描边环 / 虚线环 / 刻度
        TerminalDraw.ring(g, sx(CX), sy(CY), sr(300), 1.2f, ink(150));
        TerminalDraw.dashedRing(g, sx(CX), sy(CY), sr(268), 1f,
                (float) Math.toDegrees(2f / 268f), (float) Math.toDegrees(6f / 268f), ink(110));
        for (int i = 0; i < 96; i += 2) {
            double a = Math.toRadians(i * 3.75);
            boolean major = i % 12 == 0;
            float len = sr(major ? 16 : 8);
            TerminalDraw.line(g, sx(CX) + (float) Math.cos(a) * sr(300), sy(CY) + (float) Math.sin(a) * sr(300),
                    sx(CX) + (float) Math.cos(a) * (sr(300) + len),
                    sy(CY) + (float) Math.sin(a) * (sr(300) + len), 1f, ink(120));
        }

        // L3 断裂轴线
        dashed(g, sx(CX), sy(80), sx(CX), sy(900), 2.6f, 2.1f, ink(60));
        dashed(g, sx(90), sy(CY), sx(950), sy(CY), 2.6f, 2.1f, ink(60));

        // L4 环绕文字（r450，慢转）
        ringText(g, CX, CY, 450f, seconds * 3.0f,
                "GREGTECH LEISURE · LATERANO 1099 · FAITH · ORDER · FREEDOM · SIDESTORY 06 · ");

        // L4 点线圈 + 节点
        TerminalDraw.dottedRing(g, sx(CX), sy(CY), sr(382), 1.4f,
                (float) Math.toDegrees(11f / 382f), ink(120));
        for (int i = 0; i < 4; i++) {
            double a = Math.toRadians(i * 90 + 30);
            float nx = sx(CX) + (float) Math.cos(a) * sr(382);
            float ny = sy(CY) + (float) Math.sin(a) * sr(382);
            float pulse = 0.6f + 0.4f * (float) Math.sin(t * 1.7f + i);
            TerminalDraw.fillCircle(g, nx, ny, 2.4f + pulse * 1.2f, paper(210));
            TerminalDraw.ring(g, nx, ny, 2.4f + pulse * 1.2f, 1f, cob(200));
        }

        // L5 土星环
        TerminalDraw.ellipseRing(g, sx(CX), sy(196), sr(190), sr(40), 2f, ink(140));
        TerminalDraw.ellipseRing(g, sx(CX), sy(198), sr(150), sr(31), 1f, ink(80));
        brush(g, 360, 210, 680, 182, 6, 11, 0.5f, cob(200));

        // L6 实心钴蓝盘
        TerminalDraw.fillCircle(g, sx(CX), sy(CY), sr(R), TerminalTheme.COBALT);
        TerminalDraw.ring(g, sx(CX), sy(CY), sr(R), 1f, ink(210));
        // 盘上半调白点（黄金角）
        for (int i = 0; i < 90; i++) {
            double a = i * 2.39996;
            double rr = 20 + Math.sqrt(i / 90.0) * 210;
            float dx = sx(CX) + (float) Math.cos(a) * sr((float) rr);
            float dy = sy(CY) + (float) Math.sin(a) * sr((float) rr);
            // 半调网点 6 秒呼吸（v16-v22 规格：0.13↔0.26 透明度）
            int breathe = 48 + Math.round(14f * (0.5f + 0.5f * (float) Math.sin(t * Math.PI * 2f / 6f)));
            TerminalDraw.fillCircle(g, dx, dy, 1.6f, paper(breathe));
        }

        // L7 主扫掠带（纸白）
        brush(g, 330, 600, 720, 380, 16, 22, 0.45f, paper(120));

        // L7 星座
        float[] stars = {250, 700, 300, 670, 350, 720, 290, 760};
        for (int i = 0; i < stars.length; i += 2) {
            TerminalDraw.fillCircle(g, sx(stars[i]), sy(stars[i + 1]), 1.6f, ink(160));
        }
        for (int i = 0; i < 3; i++) {
            TerminalDraw.line(g, sx(stars[i * 2]), sy(stars[i * 2 + 1]),
                    sx(stars[i * 2 + 2]), sy(stars[i * 2 + 3]), 1f, cob(140));
        }

        // L7 星盘符号（双圆 + 8 放射线 + 中心点）
        starCompass(g, 408, 340);

        // L7 蜂巢 ×5
        float[][] hex = {{850, 690}, {790, 690}, {820, 745}, {760, 745}, {850, 800}};
        for (int i = 0; i < hex.length; i++) {
            hexagon(g, sx(hex[i][0]), sy(hex[i][1]), sr(26), ink(120));
            TerminalFonts.draw(g, Fam.MONO, 3f, String.format("%02d", i + 1),
                    sx(hex[i][0]) + 1, sy(hex[i][1]) - 2, ink(200));
        }

        // L7 马赛克块 ×4
        float[][] mosaic = {{170, 300}, {190, 300}, {170, 320}, {210, 310}};
        for (float[] m : mosaic) {
            TerminalDraw.rect(g, sx(m[0]), sy(m[1]), sr(16), sr(16), cob(200));
        }

        // L8 遮挡色块 ×4（硬闪）
        float[][] blocks = {{220, 420, 60, 26}, {200, 560, 44, 18}, {700, 400, 40, 22}, {600, 640, 50, 18}};
        for (int i = 0; i < blocks.length; i++) {
            float[] b = blocks[i];
            int a = flickerAlpha(t, i);
            TerminalDraw.rect(g, sx(b[0]), sy(b[1]), sr(b[2]), sr(b[3]), ink(a));
        }

        // L9 档案卡 ×3
        card(g, 110, 560, 200, 96, "RECORD · 06");
        card(g, 760, 520, 186, 88, "LATERANO");
        card(g, 640, 90, 170, 70, "SANCTUM");

        // L10 信封 ×3
        envelope(g, 330, 300);
        envelope(g, 860, 430);
        envelope(g, 150, 700);

        // L10 飞鸟 ×7
        for (int i = 0; i < 7; i++) {
            float bx = 580 + i * 44;
            float by = 230 - i * 18 + (i % 3) * 12 + (float) Math.sin(t * 0.9 + i * 0.8) * 5f;
            float dx = (float) Math.cos(t * 1.3 + i) * 1.5f;
            bird(g, sx(bx), sy(by + (by - 230) * 0.02f), dx);
        }

        // L11 描边数字 06
        outlineNumber(g, 90, 900, "06", 1.4f, ink(0) & 0x00FFFFFF | (0x66 << 24));
        outlineNumber(g, 80, 892, "06", 1.8f, cob(140));

        // L12 NESW
        TerminalFonts.draw(g, Fam.SERIF, 4.4f, "N", sx(508), sy(250), paper(220));
        TerminalFonts.draw(g, Fam.SERIF, 4.4f, "E", sx(742), sy(476), paper(220));
        TerminalFonts.draw(g, Fam.SERIF, 4.4f, "S", sx(508), sy(700), paper(220));
        TerminalFonts.draw(g, Fam.SERIF, 4.4f, "W", sx(290), sy(476), paper(220));

        // L12 扫描线（9s 掠过）
        float p = (t % 9f) / 9f;
        if (p > 0.05f && p < 0.35f) {
            float k = (p - 0.05f) / 0.3f;
            float x = sx(250) + k * (sx(480) - sx(250));
            float a = (float) (Math.sin(k * Math.PI)) * 0.85f;
            TerminalDraw.rect(g, x, sy(80), 1.2f, sr(760), paper((int) (a * 255)));
        }

        // v22 构造线：静止时很淡（.18），hover 才明显；它不是准星吸附框
        TerminalDraw.dashedRect(g, sx(300), sy(250), sr(440), sr(440), 2.2f, 3.2f,
                cob(hover ? 90 : 34));
    }

    private static int flickerAlpha(float t, int i) {
        float phase = (t + i * 0.37f) % 5.2f / 5.2f;
        if (phase > 0.61f && phase < 0.62f) return 64;
        if (phase > 0.63f && phase < 0.64f) return 140;
        if (phase > 0.88f && phase < 0.89f) return 0;
        return 199;
    }

    private static void dashed(GuiGraphics g, float x1, float y1, float x2, float y2,
                               float dash, float gap, int color) {
        float dx = x2 - x1, dy = y2 - y1;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        float ux = dx / len, uy = dy / len;
        float p = 0;
        while (p < len) {
            float end = Math.min(p + dash, len);
            TerminalDraw.line(g, x1 + ux * p, y1 + uy * p, x1 + ux * end, y1 + uy * end, 1f, color);
            p += dash + gap;
        }
    }

    private static void brush(GuiGraphics g, float x1, float y1, float x2, float y2,
                              float amp, float w, float bias, int color) {
        int n = 8;
        float[][] spine = new float[n + 1][2];
        float[] widths = new float[n + 1];
        float dx = x2 - x1, dy = y2 - y1;
        float nl = (float) Math.sqrt(dx * dx + dy * dy);
        float ux = -dy / nl, uy = dx / nl;
        for (int i = 0; i <= n; i++) {
            float p = i / (float) n;
            float off = (float) Math.sin(p * Math.PI * 1.6) * amp;
            spine[i][0] = sx(x1 + dx * p + ux * off);
            spine[i][1] = sy(y1 + dy * p + uy * off);
            float taper = 0.35f + 0.65f * (float) Math.sin(Math.PI * Math.pow(p, bias));
            widths[i] = w * taper * K;
        }
        TerminalDraw.brush(g, spine, widths, color);
    }

    private static void hexagon(GuiGraphics g, float cx, float cy, float r, int color) {
        float[] pts = new float[12];
        for (int i = 0; i < 6; i++) {
            double a = Math.PI / 3 * i - Math.PI / 2;
            pts[i * 2] = cx + (float) Math.cos(a) * r;
            pts[i * 2 + 1] = cy + (float) Math.sin(a) * r;
        }
        for (int i = 0; i < 6; i++) {
            TerminalDraw.line(g, pts[i * 2], pts[i * 2 + 1],
                    pts[((i + 1) % 6) * 2], pts[((i + 1) % 6) * 2 + 1], 1f, color);
        }
    }

    private static void card(GuiGraphics g, float x, float y, float w, float h, String title) {
        TerminalDraw.rect(g, sx(x), sy(y), sr(w), sr(h), paper(235));
        TerminalDraw.outline(g, sx(x), sy(y), sr(w), sr(h), ink(170));
        TerminalDraw.rect(g, sx(x), sy(y), sr(w), sr(22), ink(235));
        TerminalFonts.draw(g, Fam.MONO, 3.4f, title, sx(x + 6), sy(y + 6), paper(235));
        for (int k = 0; k < 2; k++) {
            TerminalDraw.hline(g, sx(x + 10), sx(x + w - 16), sy(y + 40 + k * 18), ink(110));
        }
    }

    private static void envelope(GuiGraphics g, float x, float y) {
        TerminalDraw.outline(g, sx(x), sy(y), sr(40), sr(26), ink(150));
        TerminalDraw.line(g, sx(x), sy(y), sx(x + 20), sy(y + 14), 0.9f, ink(130));
        TerminalDraw.line(g, sx(x + 40), sy(y), sx(x + 20), sy(y + 14), 0.9f, ink(130));
    }

    private static void bird(GuiGraphics g, float x, float y, float dx) {
        TerminalDraw.line(g, x - 3 + dx, y, x + dx, y - 2, 1.1f, ink(200));
        TerminalDraw.line(g, x + dx, y - 2, x + 3 + dx, y, 1.1f, ink(200));
    }

    private static void outlineNumber(GuiGraphics g, float x, float y, String s, float w, int color) {
        TerminalFonts.draw(g, Fam.SERIF, 60f, s, sx(x) - w, sy(y) - w, color);
        TerminalFonts.draw(g, Fam.SERIF, 60f, s, sx(x) + w, sy(y) + w, color);
        TerminalFonts.draw(g, Fam.SERIF, 60f, s, sx(x) - w, sy(y) + w, color);
        TerminalFonts.draw(g, Fam.SERIF, 60f, s, sx(x) + w, sy(y) - w, color);
    }

    /** 沿圆弧逐字排布并旋转（SVG textPath 的等价实现）。 */
    private static void ringText(GuiGraphics g, float cx, float cy, float r, float startDeg, String text) {
        float rr = sr(r);
        float angle = (float) Math.toRadians(startDeg);
        for (int i = 0; i < text.length(); i++) {
            String ch = String.valueOf(text.charAt(i));
            float adv = 4.3f;
            float a = angle + (i * adv) / rr;
            float x = sx(cx) + (float) Math.cos(a) * rr;
            float y = sy(cy) + (float) Math.sin(a) * rr;
            g.pose().pushPose();
            g.pose().translate(x, y, 0);
            g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees((float) Math.toDegrees(a) + 90f));
            TerminalFonts.draw(g, Fam.MONO, 4f, ch, -1.5f, -2.5f, ink(200));
            g.pose().popPose();
        }
    }

    /** 实心盘上的白色星盘符号。 */
    private static void starCompass(GuiGraphics g, float tx, float ty) {
        float cx = tx + 80 * 1.4f, cy = ty + 68 * 1.4f;
        float r1 = 45 * 1.4f, r2 = 39 * 1.4f;
        TerminalDraw.ring(g, sx(cx), sy(cy), sr(r1), 1.1f, paper(220));
        TerminalDraw.ring(g, sx(cx), sy(cy), sr(r2), 1.1f, paper(200));
        for (int i = 0; i < 8; i++) {
            double a = Math.PI / 4 * i;
            TerminalDraw.line(g,
                    sx(cx) + (float) Math.cos(a) * sr(r2), sy(cy) + (float) Math.sin(a) * sr(r2),
                    sx(cx) + (float) Math.cos(a) * sr(r1 * 1.28f), sy(cy) + (float) Math.sin(a) * sr(r1 * 1.28f),
                    1f, paper(200));
        }
        TerminalDraw.fillCircle(g, sx(cx), sy(cy), sr(5), paper(220));
        // 两翼
        TerminalDraw.line(g, sx(cx - r1 * 0.9f), sy(cy - r1 * 0.35f),
                sx(cx - r1 * 1.35f), sy(cy - r1 * 0.1f), 1f, paper(180));
        TerminalDraw.line(g, sx(cx + r1 * 0.9f), sy(cy + r1 * 0.35f),
                sx(cx + r1 * 1.35f), sy(cy + r1 * 0.1f), 1f, paper(180));
    }
}
