package com.gtlmodular.client.gui.terminal.print;

import net.minecraft.client.gui.GuiGraphics;

/**
 * v31 印刷档案版基础图元（稳妥版）。
 *
 * <p>教训：自绘 QUADS 顶点流一旦出现退化/绕序问题，会被剔除或拼出横跨整屏的伪三角形。
 * 因此这里所有形状最终都落到原版 {@link GuiGraphics#fill} 上：
 * 直线用 Bresenham 逐像素、圆/椭圆用扫描线或逐点、多边形用扫描线填充。
 * 性能靠 {@code drawManaged} 合并，视觉上无抗锯齿但符合印刷版式。
 */
public final class TerminalDraw {

    private TerminalDraw() {}

    private static final float TAU = (float) (Math.PI * 2);

    // ────────────────────────────── 矩形 / 线

    public static void rect(GuiGraphics g, float x, float y, float w, float h, int color) {
        int x0 = Math.round(x), y0 = Math.round(y);
        int x1 = Math.round(x + w), y1 = Math.round(y + h);
        if (x1 <= x0 || y1 <= y0) return;
        g.fill(x0, y0, x1, y1, color);
    }

    public static void outline(GuiGraphics g, float x, float y, float w, float h, int color) {
        hline(g, x, x + w, y, color);
        hline(g, x, x + w, y + h - 1, color);
        vline(g, x, y, y + h, color);
        vline(g, x + w - 1, y, y + h, color);
    }

    public static void hline(GuiGraphics g, float x1, float x2, float y, int color) {
        int a = Math.round(Math.min(x1, x2)), b = Math.round(Math.max(x1, x2));
        int yy = Math.round(y);
        if (b <= a) return;
        g.fill(a, yy, b, yy + 1, color);
    }

    public static void vline(GuiGraphics g, float x, float y1, float y2, int color) {
        int a = Math.round(Math.min(y1, y2)), b = Math.round(Math.max(y1, y2));
        int xx = Math.round(x);
        if (b <= a) return;
        g.fill(xx, a, xx + 1, b, color);
    }

    /** 任意方向直线：姿态旋转后一次 fill，避免长射线逐像素调用数百次。 */
    public static void line(GuiGraphics g, float x1, float y1, float x2, float y2,
                            float thickness, int color) {
        float dx = x2 - x1, dy = y2 - y1;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.5f) {
            dot(g, Math.round((x1 + x2) * 0.5f), Math.round((y1 + y2) * 0.5f), color);
            return;
        }
        g.pose().pushPose();
        g.pose().translate((x1 + x2) * 0.5f, (y1 + y2) * 0.5f, 0f);
        g.pose().mulPose(com.mojang.math.Axis.ZP.rotation((float) Math.atan2(dy, dx)));
        int w = Math.max(1, Math.round(len));
        int h = Math.max(1, Math.round(thickness));
        g.fill(-w / 2, -h / 2, (w + 1) / 2, (h + 1) / 2, color);
        g.pose().popPose();
    }

    private static void dot(GuiGraphics g, int x, int y, int color) {
        g.fill(x, y, x + 1, y + 1, color);
    }

    public static void dashedH(GuiGraphics g, float x1, float x2, float y,
                               float dash, float gap, int color) {
        float x = Math.min(x1, x2), end = Math.max(x1, x2);
        while (x < end) {
            hline(g, x, Math.min(x + dash, end), y, color);
            x += dash + gap;
        }
    }

    public static void dashedV(GuiGraphics g, float x, float y1, float y2,
                               float dash, float gap, int color) {
        float y = Math.min(y1, y2), end = Math.max(y1, y2);
        while (y < end) {
            vline(g, x, y, Math.min(y + dash, end), color);
            y += dash + gap;
        }
    }

    public static void dashedRect(GuiGraphics g, float x, float y, float w, float h,
                                  float dash, float gap, int color) {
        dashedH(g, x, x + w, y, dash, gap, color);
        dashedH(g, x, x + w, y + h - 1, dash, gap, color);
        dashedV(g, x, y, y + h, dash, gap, color);
        dashedV(g, x + w - 1, y, y + h, dash, gap, color);
    }

    // ────────────────────────────── 圆 / 弧 / 椭圆

    public static void fillCircle(GuiGraphics g, float cx, float cy, float r, int color) {
        int ri = Math.round(r);
        for (int dy = -ri; dy <= ri; dy++) {
            int dx = (int) Math.sqrt(Math.max(0, ri * ri - dy * dy));
            g.fill(Math.round(cx) - dx, Math.round(cy) + dy,
                    Math.round(cx) + dx + 1, Math.round(cy) + dy + 1, color);
        }
    }

    public static void fillEllipse(GuiGraphics g, float cx, float cy, float rx, float ry, int color) {
        int ryi = Math.round(ry);
        for (int dy = -ryi; dy <= ryi; dy++) {
            float k = 1f - (dy * dy) / (float) (ryi * ryi + 1);
            int dx = Math.round(rx * (float) Math.sqrt(Math.max(0, k)));
            g.fill(Math.round(cx) - dx, Math.round(cy) + dy,
                    Math.round(cx) + dx + 1, Math.round(cy) + dy + 1, color);
        }
    }

    /** Full ring via scanlines: O(radius) fills instead of O(circumference) point fills. */
    public static void ring(GuiGraphics g, float cx, float cy, float r, float thickness, int color) {
        if (r <= 0) return;
        int outer = Math.max(1, Math.round(r + thickness * 0.5f));
        int inner = Math.max(0, Math.round(r - thickness * 0.5f));
        int icx = Math.round(cx), icy = Math.round(cy);
        int innerSq = inner * inner;
        int outerSq = outer * outer;
        for (int dy = -outer; dy <= outer; dy++) {
            int remain = outerSq - dy * dy;
            if (remain < 0) continue;
            int ox = (int) Math.sqrt(remain);
            if (inner > 0 && dy * dy < innerSq) {
                int ix = (int) Math.sqrt(innerSq - dy * dy);
                if (ix > 0) {
                    g.fill(icx - ox, icy + dy, icx - ix + 1, icy + dy + 1, color);
                    g.fill(icx + ix, icy + dy, icx + ox + 1, icy + dy + 1, color);
                } else {
                    g.fill(icx - ox, icy + dy, icx + ox + 1, icy + dy + 1, color);
                }
            } else {
                g.fill(icx - ox, icy + dy, icx + ox + 1, icy + dy + 1, color);
            }
        }
    }

    public static void arc(GuiGraphics g, float cx, float cy, float r,
                           float startDeg, float endDeg, float thickness, int color) {
        if (r <= 0) return;
        float start = (float) Math.toRadians(startDeg);
        float end = (float) Math.toRadians(endDeg);
        int layers = Math.max(1, Math.round(thickness));
        // 细小虚线段至少 1 段；整圈封顶 128 段，视觉小于 1px 误差，避免每帧数千 fill。
        int steps = Math.max(1, Math.min(128, (int) Math.ceil(Math.abs(end - start) * r / 2f)));
        for (int l = 0; l < layers; l++) {
            float rr = r + l - (layers - 1) / 2f;
            for (int i = 0; i <= steps; i++) {
                float a = start + (end - start) * i / steps;
                dot(g, Math.round(cx + (float) Math.cos(a) * rr),
                        Math.round(cy + (float) Math.sin(a) * rr), color);
            }
        }
    }

    public static void ellipseRing(GuiGraphics g, float cx, float cy, float rx, float ry,
                                   float thickness, int color) {
        int orx = Math.max(1, Math.round(rx + thickness * 0.5f));
        int ory = Math.max(1, Math.round(ry + thickness * 0.5f));
        int irx = Math.max(0, Math.round(rx - thickness * 0.5f));
        int iry = Math.max(0, Math.round(ry - thickness * 0.5f));
        int icx = Math.round(cx), icy = Math.round(cy);
        float orx2 = orx * (float) orx, ory2 = ory * (float) ory;
        float irx2 = irx * (float) irx, iry2 = iry * (float) iry;
        for (int dy = -ory; dy <= ory; dy++) {
            float outerK = 1f - (dy * (float) dy) / ory2;
            if (outerK < 0) continue;
            int ox = Math.round(orx * (float) Math.sqrt(outerK));
            if (irx > 0 && iry > 0 && dy * (float) dy < iry2) {
                float innerK = 1f - (dy * (float) dy) / iry2;
                int ix = Math.round(irx * (float) Math.sqrt(Math.max(0f, innerK)));
                if (ix > 0) {
                    g.fill(icx - ox, icy + dy, icx - ix + 1, icy + dy + 1, color);
                    g.fill(icx + ix, icy + dy, icx + ox + 1, icy + dy + 1, color);
                } else {
                    g.fill(icx - ox, icy + dy, icx + ox + 1, icy + dy + 1, color);
                }
            } else {
                g.fill(icx - ox, icy + dy, icx + ox + 1, icy + dy + 1, color);
            }
        }
    }

    public static void dottedRing(GuiGraphics g, float cx, float cy, float r,
                                  float dot, float gapDeg, int color) {
        int count = Math.max(4, (int) (360f / gapDeg));
        int size = Math.max(1, Math.round(dot));
        for (int i = 0; i < count; i++) {
            float a = (float) Math.toRadians(i * gapDeg);
            float x = cx + (float) Math.cos(a) * r;
            float y = cy + (float) Math.sin(a) * r;
            g.fill(Math.round(x), Math.round(y), Math.round(x) + size, Math.round(y) + size, color);
        }
    }

    public static void dashedRing(GuiGraphics g, float cx, float cy, float r, float thickness,
                                  float dashDeg, float gapDeg, int color) {
        float angle = 0f;
        while (angle < 360f) {
            float end = Math.min(angle + dashDeg, 360f);
            arc(g, cx, cy, r, angle, end, thickness, color);
            angle += dashDeg + gapDeg;
        }
    }

    // ────────────────────────────── 多边形 / 笔刷

    /** 扫描线填充任意简单多边形（点集 x0,y0,x1,y1,...）。 */
    public static void polygon(GuiGraphics g, float[] pts, int color) {
        int n = pts.length / 2;
        if (n < 3) return;
        float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (int i = 1; i < pts.length; i += 2) {
            minY = Math.min(minY, pts[i]);
            maxY = Math.max(maxY, pts[i]);
        }
        int y0 = Math.round(minY), y1 = Math.round(maxY);
        float[] xs = new float[n];
        for (int y = y0; y <= y1; y++) {
            int count = 0;
            for (int i = 0; i < n; i++) {
                int j = (i + 1) % n;
                float yi = pts[i * 2 + 1], yj = pts[j * 2 + 1];
                if ((yi <= y && yj > y) || (yj <= y && yi > y)) {
                    if (count < n) {
                        float t = (y - yi) / (yj - yi);
                        xs[count++] = pts[i * 2] + t * (pts[j * 2] - pts[i * 2]);
                    }
                }
            }
            if (count < 2) continue;
            java.util.Arrays.sort(xs, 0, count);
            for (int k = 0; k + 1 < count; k += 2) {
                int a = Math.round(xs[k]), b = Math.round(xs[k + 1]);
                if (b > a) g.fill(a, y, b, y + 1, color);
            }
        }
    }

    /** 沿 S 形笔刷：按样条点画圆点，避免任何自绘四边形。 */
    public static void brush(GuiGraphics g, float[][] spine, float[] widths, int color) {
        for (int i = 0; i < spine.length; i++) {
            float r = Math.max(0.5f, widths[Math.min(i, widths.length - 1)] / 2f);
            fillCircle(g, spine[i][0], spine[i][1], r, color);
        }
    }
}
