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

    /**
     * 网页 {@code .pl-in} 的分层入场（一层一调）。
     *
     * <p>{@code animation: pl-in 1s cubic-bezier(.19,1,.22,1) both calc(var(--d,0)*.16s)}；
     * {@code from{opacity:0; translate:calc(d*30px) calc(d*-6px)}}。
     * 即：每层延迟 {@code d*0.16s}，在这 1s 内从「右 {@code d*30}px、上 {@code d*6}px」滑到位并淡入。</p>
     *
     * <p>Java 没有整层 alpha，所以除了位移，本方法返回该层的<b>不透明度</b>（0..1），
     * 由调用方用 {@link #withAlpha} 把这一层的所有颜色乘上去。</p>
     */
    private static float enter(GuiGraphics g, float t, float d) {
        float delay = d * 0.16f;
        float p = (t - delay) / 1.0f;
        if (p <= 0f) return -1f;                       // 还没开始 → 整层不画
        if (p >= 1f) p = 1f;
        float e = cubicBezier(p, 0.19f, 1f, 0.22f, 1f);
        g.pose().translate(e * 0f + (1f - e) * d * 30f, (1f - e) * d * -6f, 0);
        return e;
    }

    /** 把 alpha(0..1) 乘到已有颜色上（网页整层 opacity 的等效做法）。 */
    private static int withAlpha(int argb, float a) {
        if (a >= 0.999f) return argb;
        int base = (argb >>> 24) & 0xFF;
        int out = Math.round(base * Math.max(0f, a));
        return (out << 24) | (argb & 0x00FFFFFF);
    }

    private static float cubicBezier(float t, float x1, float y1, float x2, float y2) {
        if (t <= 0f) return 0f;
        if (t >= 1f) return 1f;
        float lo = 0f, hi = 1f, s = t;
        for (int i = 0; i < 22; i++) {
            s = (lo + hi) * 0.5f;
            float v = 1f - s;
            float x = 3f * v * v * s * x1 + 3f * v * s * s * x2 + s * s * s;
            if (x < t) lo = s; else hi = s;
        }
        float v = 1f - s;
        return 3f * v * v * s * y1 + 3f * v * s * s * y2 + s * s * s;
    }

    public static void draw(GuiGraphics g, float seconds, boolean hover, int[] flags) {
        float t = seconds;
        // L1 镜像描边大写 N（官方 scale(-1 1) + font-size 900，stroke 2）
        float aL0 = enter(g, t, 0.0f);
        if (aL0 >= 0f) {
            g.pose().pushPose();
            try {
            float nSize = 900f * K;
            float nTop = sy(860) - nSize * 0.82f;
            g.pose().pushPose();
            try {
                g.pose().translate(sx(760), 0, 0);
                g.pose().scale(-1f, 1f, 1f);
                TerminalFonts.drawOutlined(g, Fam.SERIF, nSize, "N", 0, nTop, ink(16), 0.6f);
            } finally {
                g.pose().popPose();
            }
                    } finally { g.pose().popPose(); }
        }
// L1 叶片三块（官方 line176 的三个填充 path）
        {
            leaf(g, 220, 480, 300, 24);
            leaf(g, 700, 300, 260, 18);
            leaf(g, 500, 800, 340, 22);

            // L1 S 形扫掠笔刷
            sweepBand(g, 40, 860, 980, 260, 70, 150, 0.82f, cob(26));
        }
// L2 外圈
        float aL1 = enter(g, t, 0.3f);
        if (aL1 >= 0f) {
            g.pose().pushPose();
            try {
            TerminalDraw.ring(g, sx(CX), sy(CY), sr(360), 1f, ink(66));
            TerminalDraw.ring(g, sx(CX), sy(CY), sr(300), 1f, ink(56));
            // L2 放射线 72 根（每 5°）
            for (int i = 0; i < 72; i++) {
                double a = Math.toRadians(i * 5);
                TerminalDraw.line(g, sx(CX) + (float) Math.cos(a) * sr(300), sy(CY) + (float) Math.sin(a) * sr(300),
                        sx(CX) + (float) Math.cos(a) * sr(520), sy(CY) + (float) Math.sin(a) * sr(520),
                        1f, ink(54));
            }

                    } finally { g.pose().popPose(); }
        }
// L3 放射楔形 40 个
        float aL2 = enter(g, t, 0.4f);
        if (aL2 >= 0f) {
            g.pose().pushPose();
            try {
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
            // 网页：{@code .pl-draw} = stroke-dasharray:1 + pl-draw 1.6s cubic-bezier(.6,0,.2,1) both .15s
            //       （stroke-dashoffset 1→0，即「描边自己画出来」）。
            // 这里按进度逐段画圆弧来等价实现（进度 0 不画，进度 1 画满整整一圈）。
            float drawP = drawProgress(t);
            if (drawP > 0f) {
                TerminalDraw.arc(g, sx(CX), sy(CY), sr(300), -90f, -90f + 360f * drawP, 1.3f, ink(150));
            }
            TerminalDraw.dashedRing(g, sx(CX), sy(CY), sr(268), 1f,
                    (float) Math.toDegrees(2f / 268f), (float) Math.toDegrees(6f / 268f), ink(110));
            for (int i = 0; i < 96; i++) {
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

                    } finally { g.pose().popPose(); }
        }
// L4 环绕文字（r450，慢转）
        float aL3 = enter(g, t, 0.45f);
        if (aL3 >= 0f) {
            g.pose().pushPose();
            try {
            String legend = "GREGTECH LEISURE · LATERANO 1099 · FAITH · ORDER · FREEDOM · SIDESTORY 06 · ";
            // v22 的 textPath 放了两遍，保证整圈铺满。
            // 网页 {@code .pl-spin{animation:pl-spin 120s linear infinite}}：120 秒才转一圈。
            // 之前写的是 seconds * 3.0（每秒 3°，约 20 秒一圈，比网页快 6 倍）。
            ringText(g, CX, CY, 450f, (t / 120f) * 360f, legend + legend);

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

                    } finally { g.pose().popPose(); }
        }
// L5 土星环
        float aL4 = enter(g, t, 0.5f);
        if (aL4 >= 0f) {
            g.pose().pushPose();
            try {
            TerminalDraw.ellipseRing(g, sx(CX), sy(196), sr(190), sr(40), 2f, ink(140));
            TerminalDraw.ellipseRing(g, sx(CX), sy(198), sr(150), sr(31), 1f, ink(80));
            brush(g, 360, 210, 680, 182, 6, 11, 0.5f, cob(200));

                    } finally { g.pose().popPose(); }
        }
// L6 实心钴蓝盘
        float aL5 = enter(g, t, 0.6f);
        if (aL5 >= 0f) {
            g.pose().pushPose();
            try {
            TerminalDraw.fillCircle(g, sx(CX), sy(CY), sr(R), TerminalTheme.COBALT);
            TerminalDraw.ring(g, sx(CX), sy(CY), sr(R), 1f, ink(210));
            // 盘上半调白点（黄金角）
            for (int i = 0; i < 90; i++) {
                double a = i * 2.39996;
                double rr = 20 + Math.sqrt(i / 90.0) * 210;
                float dx = sx(CX) + (float) Math.cos(a) * sr((float) rr);
                float dy = sy(CY) + (float) Math.sin(a) * sr((float) rr);
                // 网页 L6 半调是静态 opacity .22，不做呼吸
                TerminalDraw.fillCircle(g, dx, dy, 1.6f, paper(56));
            }

                    } finally { g.pose().popPose(); }
        }
// L7 主扫掠带（纸白）
        float aL6 = enter(g, t, 0.9f);
        if (aL6 >= 0f) {
            g.pose().pushPose();
            try {
            sweepBand(g, 330, 600, 720, 380, 16, 22, 0.45f, paper(153));

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

                    } finally { g.pose().popPose(); }
        }
// L8 遮挡色块 ×4（硬闪）
        float aL7 = enter(g, t, 0.95f);
        if (aL7 >= 0f) {
            g.pose().pushPose();
            try {
            float[][] blocks = {{220, 420, 60, 26}, {200, 560, 44, 18}, {700, 400, 40, 22}, {600, 640, 50, 18}};
            // 网页 --d:0.95 那层带 pl-flick：整组同步硬闪，63%~64% 还会右移 6 CSS px
            // 6 CSS px 在 1000 坐标系里 = 6*1000/270 ≈ 22.2，再经 sr() 换成设计像素
            float shiftInternal = flickerShiftCssPx(t) * (1000f / 270f);
            for (int i = 0; i < blocks.length; i++) {
                float[] b = blocks[i];
                int a = flickerAlpha(t, i);
                TerminalDraw.rect(g, sx(b[0] + shiftInternal), sy(b[1]), sr(b[2]), sr(b[3]), ink(a));
            }

                    } finally { g.pose().popPose(); }
        }
// L9 档案卡 ×3
        float aL8 = enter(g, t, 0.98f);
        if (aL8 >= 0f) {
            g.pose().pushPose();
            try {
            card(g, 110, 560, 200, 96, "RECORD · 06");
            card(g, 760, 520, 186, 88, "LATERANO");
            card(g, 640, 90, 170, 70, "SANCTUM");

                    } finally { g.pose().popPose(); }
        }
// L10 信封 ×3 + 飞鸟 ×7（网页同属 --d:1.2 一层）
        float aL9 = enter(g, t, 1.2f);
        if (aL9 >= 0f) {
            g.pose().pushPose();
            try {
            envelope(g, 330, 300);
            envelope(g, 860, 430);
            envelope(g, 150, 700);
            for (int i = 0; i < 7; i++) {
                float bx = 580 + i * 44;
                float by = 230 - i * 18 + (i % 3) * 12 + (float) Math.sin(t * 0.9 + i * 0.8) * 5f;
                float dx = (float) Math.cos(t * 1.3 + i) * 1.5f;
                bird(g, sx(bx), sy(by + (by - 230) * 0.02f), dx);
            }

                    } finally { g.pose().popPose(); }
        }
// L11 描边数字 06
        float aL10 = enter(g, t, 1.35f);
        if (aL10 >= 0f) {
            g.pose().pushPose();
            try {
            // 网页：盘外左下双描边 06，字号 260（字形盒 70.2×70.5 设计像素），两遍都是钴蓝。
            // 两遍实测盒：x=90/y=930 → (299.2,214.3)；x=80/y=920 → (296.5,211.6)
            outlineNumber(g, 90, 930, cob(102));
            outlineNumber(g, 80, 920, cob(140));

                    } finally { g.pose().popPose(); }
        }
// L12 NESW
        float aL11 = enter(g, t, 1.15f);
        if (aL11 >= 0f) {
            g.pose().pushPose();
            try {
            TerminalFonts.draw(g, Fam.SERIF, 4.4f, "N", sx(508), sy(250), paper(220));
            TerminalFonts.draw(g, Fam.SERIF, 4.4f, "E", sx(742), sy(476), paper(220));
            TerminalFonts.draw(g, Fam.SERIF, 4.4f, "S", sx(508), sy(700), paper(220));
            TerminalFonts.draw(g, Fam.SERIF, 4.4f, "W", sx(290), sy(476), paper(220));

                    } finally { g.pose().popPose(); }
        }
// L12 扫描线：{@code .pl-sweep}，9s 周期、延迟 2.4s、cubic-bezier(.6,0,.3,1)
        // 网页：x=250 y=80 w=3 h=760，translate 0 → 230 CSS px（= 230*1000/270 ≈ 852 内部），
        //       opacity 0%→0, 5%→.85, 24%→0, 100%→0（剩下 76% 时间停在终点、不可见）
        float sweepPhase = ((t - 2.4f) % 9f + 9f) % 9f / 9f;
        if (sweepPhase > 0f && sweepPhase < 0.24f) {
            float sp;
            if (sweepPhase < 0.05f) {
                sp = easeSweep(0f);                       // 移动量按 bezier 推进
                float aIn = sweepPhase / 0.05f;
                drawSweep(g, sp, 0.85f * aIn);
            } else {
                float k = (sweepPhase - 0.05f) / (0.24f - 0.05f);
                sp = easeSweep(k);
                float aOut = 1f - k;
                drawSweep(g, sp, 0.85f * aOut);
            }
        }

        // v22 构造线：静止时很淡（.18），hover 才明显；它不是准星吸附框
        TerminalDraw.dashedRect(g, sx(300), sy(250), sr(440), sr(440), 2.2f, 3.2f,
                cob(hover ? 90 : 34));
    }

    /**
     * 网页 {@code .pl-flick}（所有遮挡块共享同一时间轴，不是各自错峰）。
     *
     * <pre>
     * animation: pl-flick 5.2s steps(1) infinite;
     * @keyframes pl-flick{
     *   0%,100%{opacity:1}  61%{opacity:.25}  62%{opacity:1}
     *   63%{opacity:.55;translate:6px 0}  64%{opacity:1;translate:0 0}
     *   88%{opacity:0}  89%{opacity:1} }
     * </pre>
     *
     * <p>{@code steps(1)} 表示每一段内保持不变、到下一个关键帧才跳变（硬闪卡帧）。
     * 之前这里给每块加了 {@code i*0.37} 相位偏移，四块各自闪，与网页「整组同步硬闪」完全不同。</p>
     */
    /** {@code .pl-draw}：1.6s cubic-bezier(.6,0,.2,1)，延迟 0.15s，{@code both} 填充。 */
    private static float drawProgress(float t) {
        float p = (t - 0.15f) / 1.6f;
        if (p <= 0f) return 0f;
        if (p >= 1f) return 1f;
        return cubicBezier(p, 0.6f, 0f, 0.2f, 1f);
    }

    /** {@code cubic-bezier(.6,0,.3,1)}：pl-sweep 的位移缓动。 */
    private static float easeSweep(float k) {
        return cubicBezier(Math.max(0f, Math.min(1f, k)), 0.6f, 0f, 0.3f, 1f);
    }

    /** 画扫描条：x=250 起点，位移 230 CSS px（换算成内部 852.0）。 */
    private static void drawSweep(GuiGraphics g, float progress, float alpha) {
        if (alpha <= 0.002f) return;
        float x = 250f + progress * (230f * 1000f / 270f);
        TerminalDraw.rect(g, sx(x), sy(80), sr(3f), sr(760f), paper((int) (alpha * 255)));
    }

    private static int flickerAlpha(float t, int i) {
        float phase = (t % 5.2f) / 5.2f;       // 共享时间轴（不再 + i*0.37）
        if (phase >= 0.61f && phase < 0.62f) return 64;    // .25
        if (phase >= 0.63f && phase < 0.64f) return 140;   // .55
        if (phase >= 0.88f && phase < 0.89f) return 0;     // 0
        return 199;                                        // 1 → ink(199) 的等效
    }

    /** {@code pl-flick} 在 63%~64% 处还有 6px 的横向位移（translate:6px 0）。
     *
     * <p>注意：该 6px 是 <b>CSS 像素</b>，作用在 270px 显示 / 1000 坐标系的 SVG 上，
     * 换算成内部坐标是 {@code 6 * 1000/270 ≈ 22.2}，再乘 {@code K=0.27} 才得到设计像素。</p>
     */
    private static float flickerShiftCssPx(float t) {
        float phase = (t % 5.2f) / 5.2f;
        return (phase >= 0.63f && phase < 0.64f) ? 6f : 0f;
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

    /** 官方 L1 的叶片填充 path 的等价多边形。 */
    private static void leaf(GuiGraphics g, float x, float y, float len, float w) {
        float[] pts = {
                sx(x), sy(y),
                sx(x + len * 0.5f), sy(y - w),
                sx(x + len), sy(y + 10),
                sx(x + len * 0.5f), sy(y + w / 3f)
        };
        TerminalDraw.polygon(g, pts, ink(18));
    }

    /** 网页 _sweep_brush：两侧偏移曲线围成的填充带，不是圆点串。 */
    private static void sweepBand(GuiGraphics g, float x1, float y1, float x2, float y2,
                                  float amp, float w, float bias, int color) {
        int n = 14;
        float[] sx1 = new float[n + 1], sy1 = new float[n + 1];
        float[] leftX = new float[n + 1], leftY = new float[n + 1];
        float[] rightX = new float[n + 1], rightY = new float[n + 1];
        float dx = x2 - x1, dy = y2 - y1;
        float nl = (float) Math.sqrt(dx * dx + dy * dy);
        float ux = -dy / nl, uy = dx / nl;
        for (int i = 0; i <= n; i++) {
            float p = i / (float) n;
            float off = (float) Math.sin(p * Math.PI * 1.6) * amp;
            float px = x1 + dx * p + ux * off;
            float py = y1 + dy * p + uy * off;
            sx1[i] = sx(px);
            sy1[i] = sy(py);
        }
        for (int i = 0; i <= n; i++) {
            int a = Math.max(0, i - 1), b = Math.min(n, i + 1);
            float nx = -(sy1[b] - sy1[a]), ny = sx1[b] - sx1[a];
            float len = (float) Math.sqrt(nx * nx + ny * ny);
            if (len < 0.001f) len = 1f;
            nx /= len; ny /= len;
            float k = (float) Math.pow(Math.sin(Math.PI * Math.pow(i / (float) n, bias * 2f)), 0.75);
            float half = w * K * Math.max(0.03f, k) / 2f;
            leftX[i] = sx1[i] + nx * half;
            leftY[i] = sy1[i] + ny * half;
            rightX[i] = sx1[i] - nx * half;
            rightY[i] = sy1[i] - ny * half;
        }
        float[] pts = new float[(n + 1) * 4];
        int n2 = 0;
        for (int i = 0; i <= n; i++) { pts[n2++] = leftX[i]; pts[n2++] = leftY[i]; }
        for (int i = n; i >= 0; i--) { pts[n2++] = rightX[i]; pts[n2++] = rightY[i]; }
        TerminalDraw.polygon(g, pts, color);
    }

    private static void brush(GuiGraphics g, float x1, float y1, float x2, float y2,
                              float amp, float w, float bias, int color) {
        int n = 24;
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
        float bx = sx(x), by = sy(y), bw = sr(w), bh = sr(h), barH = sr(22);
        TerminalDraw.rect(g, bx, by, bw, bh, paper(235));
        TerminalDraw.outline(g, bx, by, bw, bh, ink(170));
        TerminalDraw.rect(g, bx, by, bw, barH, ink(235));
        // 标题必须落在黑条内（barH ≈ 5.9 设计像素）：字号 3.4，垂直居中到条内。
        // 原来用 sy(y+6)，已经掉到黑条外面 → 纸白字画在纸底上看不见（实机三张卡的黑条是空的）。
        g.flush();
        TerminalFonts.draw(g, Fam.MONO, 3.4f, title, bx + sr(6), by + Math.max(1f, (barH - 3.4f) / 2f),
                paper(235));
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

    /**
     * 空心的描边 06：AWT 轮廓在实机出现采样异常，这里直接用矢量线稿画「0」「6」。
     * 尺寸/位置按网页 SVG 的 getBBox 实测：字形盒 70.2×70.5 设计像素，单遍一层。
     */
    private static void outlineNumber(GuiGraphics g, float x, float y, int color) {
        // 字形盒左上角 = sx(x)+10.9 / sy(y)-62.8（由两遍实测盒反解，两遍一致）
        drawHollow06(g, sx(x) + 10.9f, sy(y) - 62.8f, color);
    }

    /** 单遍空心 06，本地坐标盒固定 70×70。
     *
     * <p>形状不是「两个差不多大的椭圆」——那在实机上看不出是数字。
     * 采用：0 一竖椭圆环；6 一个小闭环（偏右下）+ 一条从右上外起笔、向左收成开口的上臂。
     * 几何经离线栅格化验证（1.6px 描边下笔画连续、6 有明确开口）。</p>
     */
    private static void drawHollow06(GuiGraphics g, float bx, float by, int color) {
        final float th = 1.6f;
        // 0：竖椭圆环
        TerminalDraw.ellipseRing(g, bx + 18f, by + 37f, 15f, 32f, th, color);
        // 6：下方闭环（小、偏右下）
        TerminalDraw.ellipseRing(g, bx + 53f, by + 55f, 15f, 15f, th, color);
        // 6：上臂——从右上外侧起笔，向左上兜回接闭环左上（留出开口，不横穿顶部）
        float px = bx + 67f, py = by + 8f;
        float c1x = bx + 50f, c1y = by - 2f;
        float c2x = bx + 36f, c2y = by + 10f;
        float lx = bx + 38f, ly = by + 44f;
        float lastX = px, lastY = py;
        for (int i = 1; i <= 20; i++) {
            float u = i / 20f, v = 1f - u;
            float nx = v * v * v * px + 3 * v * v * u * c1x + 3 * v * u * u * c2x + u * u * u * lx;
            float ny = v * v * v * py + 3 * v * v * u * c1y + 3 * v * u * u * c2y + u * u * u * ly;
            TerminalDraw.line(g, lastX, lastY, nx, ny, th, color);
            lastX = nx;
            lastY = ny;
        }
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

    /** 实心盘上的白色星盘符号（v22 官方坐标：translate(408 340) scale(1.4)）。 */
    private static void starCompass(GuiGraphics g, float ox, float oy) {
        float ex = 408f, ey = 340f, k = 1.4f;
        float cx = sx(ex + 80 * k), cy = sy(ey + 68 * k);
        float rOuter = sr(45 * k), rInner = sr(39 * k);
        TerminalDraw.ring(g, cx, cy, rOuter, 1.4f, paper(220));
        TerminalDraw.ring(g, cx, cy, rInner, 1.4f, paper(200));
        // 长十字 + 斜线（官方 path 的等价线段）
        TerminalDraw.line(g, sx(ex + 80 * k), sy(ey + 5 * k), sx(ex + 80 * k), sy(ey + 153 * k), 1.2f, paper(210));
        TerminalDraw.line(g, sx(ex + 12 * k), sy(ey + 68 * k), sx(ex + 148 * k), sy(ey + 68 * k), 1.2f, paper(210));
        TerminalDraw.line(g, sx(ex + 27 * k), sy(ey + 31 * k), sx(ex + 133 * k), sy(ey + 137 * k), 1.2f, paper(200));
        TerminalDraw.line(g, sx(ex + 133 * k), sy(ey + 31 * k), sx(ex + 27 * k), sy(ey + 137 * k), 1.2f, paper(200));
        // 下方两翼（官方填充 path）
        float[] left = {
                sx(ex + 4 * k), sy(ey + 92 * k), sx(ex + 35 * k), sy(ey + 99 * k),
                sx(ex + 59 * k), sy(ey + 121 * k), sx(ex + 29 * k), sy(ey + 117 * k)
        };
        float[] right = {
                sx(ex + 156 * k), sy(ey + 92 * k), sx(ex + 125 * k), sy(ey + 99 * k),
                sx(ex + 101 * k), sy(ey + 121 * k), sx(ex + 131 * k), sy(ey + 117 * k)
        };
        TerminalDraw.polygon(g, left, paper(210));
        TerminalDraw.polygon(g, right, paper(210));
        TerminalDraw.fillCircle(g, cx, cy, sr(5 * k), paper(230));
    }
}
