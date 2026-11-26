package com.gtlmodular.client.gui.terminal.print;

import com.gtlmodular.client.gui.terminal.print.TerminalTheme.Fam;
import com.gtlmodular.client.gui.terminal.print.TerminalTheme.Page;

import net.minecraft.client.gui.GuiGraphics;

/** 选项页（01）：目标机器 + 二级页签 + 舱室类型表 / 开关设置面板。 */
public final class TerminalOptions {

    private TerminalOptions() {}

    private static final String[] TYPES = {"INPUT", "OUTPUT", "ENERGY", "MAINTENANCE", "PARALLEL", "MUFFLER", "DATA", "OTHER"};
    private static final String[] CN = {"输入仓室", "输出仓室", "电源仓", "维护仓", "并行仓", "消声仓", "数据仓", "其他仓室"};
    private static final int[] COLORS = {
            TerminalTheme.CH_INPUT, TerminalTheme.CH_OUTPUT, TerminalTheme.CH_ENERGY, TerminalTheme.CH_MAINTENANCE,
            TerminalTheme.CH_PARALLEL, TerminalTheme.CH_MUFFLER, TerminalTheme.CH_DATA, TerminalTheme.CH_OTHER
    };
    private static final String[] SW_CN = {"镜像", "替换", "绝对替换", "AE 模式", "顺序模式", "无仓", "拆除", "模块检查"};
    private static final String[] SW_EN = {"MIRROR", "REPLACE", "ABS REPLACE", "AE MODE", "ORDER MODE", "NO CHAMBER", "DISMANTLE", "MODULE CHECK"};

    public static void draw(GuiGraphics g, TerminalScreen s) {
        // 标题区
        TerminalFonts.drawMixed(g, Fam.MONO, 6f, "TARGET / 扫描目标", 20, 48, 1.6f, TerminalTheme.INK_FAINT);
        TerminalFonts.drawMixedHeavy(g, Fam.CN, 18f,
                TerminalFonts.clip(Fam.CN, 18f, s.machineName, 170f), 20, 60, 2f, TerminalTheme.INK);
        if (!s.switchesView) {
            TerminalFonts.drawMixed(g, Fam.MONO, 6f, "8 类 · " + s.requiredCount() + " 必需",
                    230, 64, 1.2f, TerminalTheme.INK_FAINT);
        }
        TerminalFonts.draw(g, Fam.SERIF, 30f, "S", 300, 44, alpha(TerminalTheme.INK, 15));

        // 二级页签（网页 .subtab .st 实测：x20/x72 各宽 52、text 左内边距 10、下划线 2px @ y104）
        boolean ca = !s.switchesView;
        TerminalFonts.drawMixed(g, Fam.MONO, 7f, "舱室类型", 30, 92, 1f, ca ? TerminalTheme.INK : TerminalTheme.INK_FAINT);
        TerminalFonts.drawMixed(g, Fam.MONO, 7f, "开关设置", 82, 92, 1f, ca ? TerminalTheme.INK_FAINT : TerminalTheme.INK);
        TerminalDraw.rect(g, ca ? 20 : 72, 104, 52, 2, TerminalTheme.COBALT);
        s.hit(20, 88, 52, 19, "舱室类型", () -> s.switchesView = false, false);
        s.hit(72, 88, 52, 19, "开关设置", () -> s.switchesView = true, false);

        if (ca) drawChambers(g, s);
        else drawSwitches(g, s);
    }

    // ────────────────────────────── 视图 A：舱室类型

    private static void drawChambers(GuiGraphics g, TerminalScreen s) {
        TerminalFonts.drawMixed(g, Fam.MONO, 6f, "MINIMUM / REQUIRED", 20, 116, 2f, TerminalTheme.INK_FAINT);
        TerminalDraw.rect(g, 20, 126, 2, 38, TerminalTheme.COBALT);
        TerminalFonts.drawMixedHeavy(g, Fam.CN, 23f, "成型所需舱室", 30, 124, 2f, TerminalTheme.INK);
        TerminalFonts.drawMixed(g, Fam.MONO, 8f, "8 TYPES · " + s.requiredCount() + " REQUIRED",
                30, 158, 1.2f, TerminalTheme.INK_SOFT);
        TerminalDraw.hline(g, 20, 170, 186, TerminalTheme.RULE);
        TerminalFonts.drawMixed(g, Fam.MONO, 6f, "只列舱室类型 · 只读", 20, 194, 0.6f, TerminalTheme.INK_FAINT);
        TerminalFonts.drawMixed(g, Fam.MONO, 6f, "状态来自扫描 · 属性据结构", 20, 204, 0.6f, TerminalTheme.INK_FAINT);

        plant(g, 18, 236, 56, 56, s.seconds);

        // 表格
        TerminalDraw.hline(g, 196, 540, 110, TerminalTheme.RULE);
        TerminalFonts.drawMixed(g, Fam.MONO, 6f, "类型", 200, 114, 1.4f, TerminalTheme.INK_FAINT);
        TerminalFonts.drawMixed(g, Fam.MONO, 6f, "舱室类型", 300, 114, 1.4f, TerminalTheme.INK_FAINT);
        TerminalFonts.drawMixed(g, Fam.MONO, 6f, "属性", 470, 114, 1.4f, TerminalTheme.INK_FAINT);
        TerminalDraw.hline(g, 196, 540, 126, TerminalTheme.RULE_SOFT);

        for (int i = 0; i < 8; i++) {
            // 网页 .ch-row：首行 y132、行高 19、行距 18；border-bottom 在行底（y+19）。
            // 列位置（实测）：.ch-sw 中心 (206, y+9.5)；.ch-cn 220/y+3；.ch-en 300/y+6；
            //                  .ch-need 430/y+5.5；.ch-state 496/y+5。
            float y = 132 + i * 18;
            boolean on = s.chamberOn[i];
            TerminalDraw.hline(g, 196, 540, y + 19, 0x1A1F2427);
            // 网页 .ch-sw 实测：left6（行内）8×8 rotate45，包围盒 11.3，中心 (206, y+9.5)
            g.pose().pushPose();
            g.pose().translate(206, y + 9.5f, 0);
            g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(45f));
            TerminalDraw.rect(g, -4, -4, 8, 8, COLORS[i]);
            TerminalDraw.outline(g, -4, -4, 8, 8, 0x661F2427);
            g.pose().popPose();
            int alpha = on ? 255 : 178;
            TerminalFonts.drawMixed(g, Fam.CN, 9f, CN[i], 220, y + 3, 0f, withAlpha(TerminalTheme.INK, alpha));
            TerminalFonts.drawMixed(g, Fam.MONO, 5f, TYPES[i], 300, y + 6, 0.5f, withAlpha(TerminalTheme.INK_FAINT, alpha));
            // 必需/可选来自扫描报告里的结构谓词 minCount，不再是网页原型写死的 6 必需。
            TerminalFonts.drawMixed(g, Fam.MONO, 5f, s.chamberRequired(i) ? "必需" : "可选",
                    430, y + 5.5f, 1f, withAlpha(TerminalTheme.INK_SOFT, alpha));
            TerminalFonts.drawMixed(g, Fam.MONO, 6f, on ? "已放" : "缺失", 496, y + 5, 0f,
                    on ? TerminalTheme.COBALT : TerminalTheme.INK_FAINT);
        }
    }

    /**
     * 植物生长（网页 .grow 的对位）：56×56，茎为两段三次贝塞尔，叶片是两段二次曲线围成的尖叶，
     * 带叶脉与节点，9 秒循环。之前用短线拼，实机看起来是「虚线小虫」，这里按设计稿曲线重画。
     */
    /** 网页 6 片叶子的独立延迟（秒）：{@code .grow .d1{.6s} .d2{1.5} .d3{2.4} .d4{3.3} .d5{4.2} .d6{5.1}}。 */
    private static final float[] LEAF_DELAY = {0.6f, 1.5f, 2.4f, 3.3f, 4.2f, 5.1f};
    private static final float GROW_CYCLE = 9f;

    private static void plant(GuiGraphics g, float ox, float oy, float w, float h, float t) {
        // ── 茎：grow-stem 9s ease-out infinite
        //    0%{dashoffset:100} → 62%,92%{0} → 100%{100}，即「长满→保持→缩回」
        float stemU = (t % GROW_CYCLE) / GROW_CYCLE;
        float stem;
        if (stemU < 0.62f) {
            stem = easeOut(stemU / 0.62f);            // 0→1 长满
        } else if (stemU < 0.92f) {
            stem = 1f;                                 // 保持
        } else {
            stem = 1f - easeOut((stemU - 0.92f) / 0.08f); // 缩回
        }
        if (stem <= 0.01f) return;

        // 茎：M6 50 C18 46,20 30,30 24 C40 18,44 10,50 6（设计稿数字）
        float[][] stemPts = bezier2(
                6, 50, 18, 46, 20, 30, 30, 24,
                40, 18, 44, 10, 50, 6, 36);
        int last = Math.max(1, Math.round(stemPts.length * stem));
        for (int i = 1; i < last; i++) {
            TerminalDraw.line(g, ox + stemPts[i - 1][0], oy + stemPts[i - 1][1],
                    ox + stemPts[i][0], oy + stemPts[i][1], 1f, 0xD93A5BC2);
        }

        float[][] leaves = {{14, 44, -1}, {22, 36, 1}, {28, 29, -1}, {36, 21, 1}, {41, 15, -1}, {47, 9, 1}};
        for (int i = 0; i < leaves.length; i++) {
            // ── 叶片：grow-leaf 9s，各自延迟 LEAF_DELAY[i]
            //    0%{opacity:0;scale:0} 6%,88%{opacity:1;scale:1} 96%,100%{opacity:0;scale:0}
            float local = ((t - LEAF_DELAY[i]) % GROW_CYCLE + GROW_CYCLE) % GROW_CYCLE / GROW_CYCLE;
            float grow;
            if (local < 0.06f) {
                grow = easeOut(local / 0.06f);            // 展开
            } else if (local < 0.88f) {
                grow = 1f;                                 // 保持
            } else if (local < 0.96f) {
                grow = 1f;                                 // 保持（88%~96% 仍可见）
            } else {
                grow = 1f - easeOut((local - 0.96f) / 0.04f); // 缩回
            }
            if (grow <= 0.02f) continue;
            float lx = ox + leaves[i][0];
            float ly = oy + leaves[i][1];
            float side = leaves[i][2];
            float ex = lx + side * 11f * grow;
            float ey = ly - 4.95f * grow;

            // 叶面：两段二次曲线围合后按扫描线填充（纸色偏冷，70% 不透明）
            java.util.List<float[]> poly = new java.util.ArrayList<>();
            float c1x = lx + side * 11f * 0.5f, c1y = ly - 3.2f * 2f * grow;
            float c2x = lx + side * 11f * 0.55f, c2y = ey + 3.2f * 0.6f * grow;
            for (int s = 0; s <= 8; s++) {
                float u = s / 8f;
                poly.add(quad(lx, ly, c1x, c1y, ex, ey, u));
            }
            for (int s = 1; s <= 8; s++) {
                float u = s / 8f;
                poly.add(quad(ex, ey, c2x, c2y, lx, ly, u));
            }
            float[] pts = new float[poly.size() * 2];
            for (int s = 0; s < poly.size(); s++) {
                pts[s * 2] = poly.get(s)[0];
                pts[s * 2 + 1] = poly.get(s)[1];
            }
            TerminalDraw.polygon(g, pts, (int) (0.70f * grow * 255) << 24 | 0xE2E7E9);
            // 叶缘
            for (int s = 1; s < poly.size(); s++) {
                TerminalDraw.line(g, poly.get(s - 1)[0], poly.get(s - 1)[1],
                        poly.get(s)[0], poly.get(s)[1], 0.9f, 0xD93A5BC2);
            }
            // 叶脉：grow-vein 只在 8%~88% 可见（峰值 .5）
            float vein = (local >= 0.08f && local < 0.88f) ? 0.5f : 0f;
            if (vein > 0f) {
                TerminalDraw.line(g, lx, ly, ex, ey, 0.8f, (int) (vein * 255) << 24 | 0x3A5BC2);
            }
            // 节点：grow-node 4%~88% 可见
            if (local >= 0.04f && local < 0.88f) {
                TerminalDraw.fillCircle(g, lx, ly, 1.4f, TerminalTheme.COBALT);
            }
        }
    }

    /** {@code ease-out}（CSS 默认的 ease-out 近似）。 */
    private static float easeOut(float k) {
        float u = Math.max(0f, Math.min(1f, k));
        return 1f - (1f - u) * (1f - u);
    }

    private static float[] quad(float x0, float y0, float cx, float cy, float x1, float y1, float u) {
        float v = 1f - u;
        return new float[]{
                v * v * x0 + 2 * v * u * cx + u * u * x1,
                v * v * y0 + 2 * v * u * cy + u * u * y1
        };
    }

    /** 两段三次贝塞尔拼接，按段数采样。 */
    private static float[][] bezier2(float x0, float y0, float x1, float y1, float x2, float y2,
                                     float x3, float y3, float x4, float y4, float x5, float y5,
                                     float x6, float y6, int steps) {
        float[][] out = new float[steps * 2][2];
        int n = 0;
        for (int s = 0; s < steps; s++) {
            float u = s / (float) (steps - 1);
            float v = 1f - u;
            out[n][0] = v * v * v * x0 + 3 * v * v * u * x1 + 3 * v * u * u * x2 + u * u * u * x3;
            out[n][1] = v * v * v * y0 + 3 * v * v * u * y1 + 3 * v * u * u * y2 + u * u * u * y3;
            n++;
        }
        for (int s = 1; s < steps; s++) {
            float u = s / (float) (steps - 1);
            float v = 1f - u;
            out[n][0] = v * v * v * x3 + 3 * v * v * u * x4 + 3 * v * u * u * x5 + u * u * u * x6;
            out[n][1] = v * v * v * y3 + 3 * v * v * u * y4 + 3 * v * u * u * y5 + u * u * u * y6;
            n++;
        }
        float[][] trimmed = new float[n][2];
        System.arraycopy(out, 0, trimmed, 0, n);
        return trimmed;
    }

    // ────────────────────────────── 视图 B：开关设置

    private static void drawSwitches(GuiGraphics g, TerminalScreen s) {
        // 左侧 PROFILE
        panel(g, 20, 124, 156, 152);
        TerminalDraw.rect(g, 20, 124, 156, 20, TerminalTheme.INK);
        g.flush();
        TerminalFonts.drawMixed(g, Fam.MONO, 7f, "PROFILE / 配置小结", 29, 130, 1.2f, 0xFFFFFFFF);

        TerminalFonts.drawMixed(g, Fam.MONO, 6f, "当前档位", 28, 153, 1.2f, TerminalTheme.INK_SOFT);
        TerminalFonts.draw(g, Fam.SERIF, 14f, "S-01", 28, 163, TerminalTheme.INK);
        TerminalFonts.drawMixed(g, Fam.MONO, 6f, "开启项", 28, 185, 1.2f, TerminalTheme.INK_SOFT);
        TerminalFonts.drawMixedHeavy(g, Fam.MONO, 20f, String.valueOf(s.onCount()), 28, 195, 0f, TerminalTheme.COBALT);
        // 网页 / 8：left8 + 数字宽，字号8，顶端在 206
        float onW = TerminalFonts.mixedWidth(Fam.MONO, 20f, String.valueOf(s.onCount()), 0f);
        TerminalFonts.drawMixed(g, Fam.MONO, 8f, "/ 8", 28 + onW, 206, 0f, TerminalTheme.INK_FAINT);
        TerminalDraw.hline(g, 28, 168, 222, TerminalTheme.RULE);
        TerminalFonts.drawMixed(g, Fam.MONO, 6f, "参照 · 上次扫描", 28, 229, 1.2f, TerminalTheme.INK_SOFT);
        TerminalFonts.drawMixed(g, Fam.CN, 9f, TerminalFonts.clip(Fam.CN, 9f, s.machineName, 140f),
                28, 239, 0f, TerminalTheme.INK);
        long scanMs = s.scanMs();
        String snap = "SNAP " + (scanMs > 0 ? scanMs + " ms" : "—") + " · " + s.scanTarget();
        TerminalFonts.drawMixed(g, Fam.MONO, 6f, snap, 28, 255, 0f, TerminalTheme.INK_FAINT);

        // 右侧开关面板
        panel(g, 196, 52, 330, 224);
        TerminalDraw.rect(g, 196, 52, 330, 22, TerminalTheme.INK);
        g.flush();
        TerminalFonts.drawMixed(g, Fam.MONO, 8f, "S-01 · 结构替换选项", 206, 58, 1.4f, 0xFFFFFFFF);

        for (int i = 0; i < 8; i++) {
            // 完全按网页 .sw-row 的盒子来（headless 实测）：
            //   row  x205 y83+i*21 w314 h21
            //   .sw-mark  x209 y+0(盒顶) 9px 字形（✓ = 6.2×10.8，· = 5×10.8）
            //   中文 x225 y+0（9px，盒高 10.8） 英文 x293 y+2（6px） ON/OFF 右缘 515 y+0
            //   行底线 .rule-soft 固定在【行内 top:15】，不是行底 y+21！
            //   上一版把线画在行底，整排视觉上就上移了一格。
            // 行距 21 是排布用的，命中/吸附框只能到行底线（y+15）为止：
            // 之前用满 21，准星方框会往下压到下一行的字上（实机反馈「判定范围往下延伸」）。
            final float hitH = 16f;
            float y = 83 + i * 21f;
            boolean hover = s.isHover(205, y, 314, hitH);
            if (hover) TerminalDraw.rect(g, 205, y, 314, hitH, 0x0F3A5BC2);
            boolean on = s.flags[i] != 0;
            if (on) {
                TerminalDraw.line(g, 209, y + 7, 211, y + 10, 1.1f, TerminalTheme.COBALT);
                TerminalDraw.line(g, 211, y + 10, 215.5f, y + 3, 1.1f, TerminalTheme.COBALT);
            } else {
                // 网页 .sw-mark 的 OFF 是「·」（5×10.8 的 9px 字形）；用 2×2 实心块代替，
                // 既不会像 fillCircle(r=1) 那样退化成十字，也不会比 ON 的勾重。
                TerminalDraw.rect(g, 211, y + 5, 2, 2, TerminalTheme.INK_FAINT);
            }
            TerminalFonts.drawMixed(g, Fam.CN, 9f, SW_CN[i], 225, y, 0f, TerminalTheme.INK);
            TerminalFonts.drawMixed(g, Fam.MONO, 6f, SW_EN[i], 293, y + 2, 0.8f, TerminalTheme.INK_FAINT);
            TerminalFonts.drawRightMixed(g, Fam.MONO, 8f, on ? "ON" : "OFF", 515, y,
                    on ? TerminalTheme.COBALT : TerminalTheme.INK_FAINT);
            // 行底线：网页在行内 top:15（宽 306，x209..515）
            TerminalDraw.hline(g, 209, 515, y + 15, 0x1A1F2427);
            final int idx = i;
            s.hit(205, y, 314, hitH, SW_CN[i], () -> s.toggleFlag(idx), false);
        }
    }

    private static void panel(GuiGraphics g, float x, float y, float w, float h) {
        TerminalDraw.rect(g, x + 1, y + 1, w, h, 0x241F2427);
        TerminalDraw.rect(g, x, y, w, h, 0xF0F4F5F3); // 网页 #F4F5F3
        TerminalDraw.outline(g, x, y, w, h, 0x801F2427);
    }

    private static int alpha(int c, int a) {
        return (a << 24) | (c & 0x00FFFFFF);
    }

    private static int withAlpha(int c, int a) {
        return (a << 24) | (c & 0x00FFFFFF);
    }

    /** 供 TerminalScreen 调用：页签/行的总宽度（网页实测单页签 52）。 */
    public static float tabWidth() {
        return 52f;
    }

    static final Page PAGE = Page.OPTIONS;
}
