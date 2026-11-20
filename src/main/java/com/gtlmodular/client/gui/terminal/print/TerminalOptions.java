package com.gtlmodular.client.gui.terminal.print;

import com.gtlmodular.client.gui.terminal.print.TerminalTheme.Fam;
import com.gtlmodular.client.gui.terminal.print.TerminalTheme.Page;

import net.minecraft.client.gui.GuiGraphics;

/** 选项页（01）：目标机器 + 二级页签 + 舱室类型表 / 开关设置面板。 */
public final class TerminalOptions {

    private TerminalOptions() {}

    private static final String[] TYPES = {"INPUT", "OUTPUT", "ENERGY", "MAINTENANCE", "PARALLEL", "MUFFLER", "DATA", "OTHER"};
    private static final String[] CN = {"输入仓室", "输出仓室", "电源仓", "维护仓", "并行仓", "消声仓", "数据仓", "其他仓室"};
    private static final String[] NEED = {"必需", "必需", "必需", "必需", "可选", "可选", "必需", "可选"};
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
            TerminalFonts.drawMixed(g, Fam.MONO, 6f, "8 类 · 6 必需", 230, 64, 1.2f, TerminalTheme.INK_FAINT);
        }
        TerminalFonts.draw(g, Fam.SERIF, 30f, "S", 300, 44, alpha(TerminalTheme.INK, 15));

        // 二级页签
        float tab1W = TerminalFonts.mixedWidth(Fam.MONO, 7f, "舱室类型", 1f) + 20;
        float tab2W = TerminalFonts.mixedWidth(Fam.MONO, 7f, "开关设置", 1f) + 20;
        boolean ca = !s.switchesView;
        TerminalFonts.drawMixed(g, Fam.MONO, 7f, "舱室类型", 30, 92, 1f, ca ? TerminalTheme.INK : TerminalTheme.INK_FAINT);
        TerminalFonts.drawMixed(g, Fam.MONO, 7f, "开关设置", 20 + tab1W + 10, 92, 1f, ca ? TerminalTheme.INK_FAINT : TerminalTheme.INK);
        if (ca) {
            TerminalDraw.rect(g, 20, 104, tab1W, 2, TerminalTheme.COBALT);
        } else {
            TerminalDraw.rect(g, 20 + tab1W, 104, tab2W, 2, TerminalTheme.COBALT);
        }
        s.hit(20, 88, tab1W, 18, "舱室类型", () -> s.switchesView = false, false);
        s.hit(20 + tab1W, 88, tab2W, 18, "开关设置", () -> s.switchesView = true, false);

        if (ca) drawChambers(g, s);
        else drawSwitches(g, s);
    }

    // ────────────────────────────── 视图 A：舱室类型

    private static void drawChambers(GuiGraphics g, TerminalScreen s) {
        TerminalFonts.drawMixed(g, Fam.MONO, 6f, "MINIMUM / REQUIRED", 20, 116, 2f, TerminalTheme.INK_FAINT);
        TerminalDraw.rect(g, 20, 126, 2, 38, TerminalTheme.COBALT);
        TerminalFonts.drawMixedHeavy(g, Fam.CN, 23f, "成型所需舱室", 30, 124, 2f, TerminalTheme.INK);
        TerminalFonts.drawMixed(g, Fam.MONO, 8f, "8 TYPES · 6 REQUIRED", 30, 158, 1.2f, TerminalTheme.INK_SOFT);
        TerminalDraw.hline(g, 20, 170, 186, TerminalTheme.RULE);
        TerminalFonts.drawMixed(g, Fam.MONO, 6f, "只列舱室类型", 20, 194, 0.6f, TerminalTheme.INK_FAINT);
        TerminalFonts.drawMixed(g, Fam.MONO, 6f, "状态来自手持扫描结果", 20, 204, 0.6f, TerminalTheme.INK_FAINT);

        plant(g, 18, 236, 56, 56, s.seconds);

        // 表格
        TerminalDraw.hline(g, 196, 540, 110, TerminalTheme.RULE);
        TerminalFonts.drawMixed(g, Fam.MONO, 6f, "类型", 200, 114, 1.4f, TerminalTheme.INK_FAINT);
        TerminalFonts.drawMixed(g, Fam.MONO, 6f, "舱室类型", 300, 114, 1.4f, TerminalTheme.INK_FAINT);
        TerminalFonts.drawMixed(g, Fam.MONO, 6f, "属性", 470, 114, 1.4f, TerminalTheme.INK_FAINT);
        TerminalDraw.hline(g, 196, 540, 126, TerminalTheme.RULE_SOFT);

        for (int i = 0; i < 8; i++) {
            float y = 132 + i * 18;
            boolean on = s.chamberOn[i];
            TerminalDraw.hline(g, 196, 540, y + 18, 0x1A1F2427);
            g.pose().pushPose();
            g.pose().translate(202, y + 5, 0);
            g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(45f));
            TerminalDraw.rect(g, -4, -4, 8, 8, COLORS[i]);
            TerminalDraw.outline(g, -4, -4, 8, 8, 0x661F2427);
            g.pose().popPose();
            int alpha = on ? 255 : 178;
            TerminalFonts.drawMixed(g, Fam.CN, 9f, CN[i], 220, y + 3, 0f, withAlpha(TerminalTheme.INK, alpha));
            TerminalFonts.drawMixed(g, Fam.MONO, 5f, TYPES[i], 300, y + 5, 0.5f, withAlpha(TerminalTheme.INK_FAINT, alpha));
            TerminalFonts.drawMixed(g, Fam.MONO, 5f, NEED[i], 430, y + 5, 1f, withAlpha(TerminalTheme.INK_SOFT, alpha));
            TerminalFonts.drawMixed(g, Fam.MONO, 6f, on ? "已放" : "缺失", 496, y + 4, 0f,
                    on ? TerminalTheme.COBALT : TerminalTheme.INK_FAINT);
        }
    }

    /** 植物生长：茎按进度画线，6 片叶沿茎依次展开（9s 循环）。 */
    private static void plant(GuiGraphics g, float x, float y, float w, float h, float t) {
        float p = (t % 9f) / 9f;
        float stem = Math.min(1f, p / 0.62f);
        float baseX = x + 8, baseY = y + h - 6;
        TerminalDraw.line(g, baseX, baseY, baseX + 2, baseY - 18 * stem, 1f, 0xB33A5BC2);
        TerminalDraw.line(g, baseX + 2, baseY - 18 * stem, baseX + 16 * stem, baseY - 34 * stem, 1f, 0xB33A5BC2);
        TerminalDraw.line(g, baseX + 16 * stem, baseY - 34 * stem, baseX + 24 * stem, baseY - 46 * stem, 1f, 0xB33A5BC2);

        float[] ly = {0.26f, 0.40f, 0.54f, 0.66f, 0.78f, 0.9f};
        for (int i = 0; i < 6; i++) {
            float leafStart = 0.06f + i * 0.09f;
            float k = (p - leafStart) / 0.10f;
            k = Math.max(0f, Math.min(1f, k));
            if (k <= 0f) continue;
            float lx = baseX + 4 + i * 3.2f;
            float ly0 = baseY - 12 - i * 5.2f;
            float len = 7f * k;
            TerminalDraw.line(g, lx, ly0, lx + len, ly0 - len * 0.55f, 1f, 0xCC3A5BC2);
            TerminalDraw.line(g, lx, ly0, lx + len * 0.9f, ly0 + len * 0.2f, 1f, 0xCC3A5BC2);
            TerminalDraw.fillCircle(g, lx, ly0, 1.1f, TerminalTheme.COBALT);
        }
    }

    // ────────────────────────────── 视图 B：开关设置

    private static void drawSwitches(GuiGraphics g, TerminalScreen s) {
        // 左侧 PROFILE
        panel(g, 20, 124, 156, 152);
        TerminalDraw.rect(g, 20, 124, 156, 20, TerminalTheme.INK);
        TerminalFonts.drawMixed(g, Fam.MONO, 7f, "PROFILE / 配置小结", 28, 130, 1.2f, 0xFFFFFFFF);

        TerminalFonts.drawMixed(g, Fam.MONO, 6f, "当前档位", 28, 152, 1.2f, TerminalTheme.INK_SOFT);
        TerminalFonts.draw(g, Fam.SERIF, 14f, "S-01", 28, 162, TerminalTheme.INK);
        TerminalFonts.drawMixed(g, Fam.MONO, 6f, "开启项", 28, 184, 1.2f, TerminalTheme.INK_SOFT);
        TerminalFonts.drawMixedHeavy(g, Fam.MONO, 20f, String.valueOf(s.onCount()), 28, 194, 0f, TerminalTheme.COBALT);
        TerminalFonts.drawMixed(g, Fam.MONO, 8f, "/ 8", 50, 203, 0f, TerminalTheme.INK_FAINT);
        TerminalDraw.hline(g, 28, 168, 222, TerminalTheme.RULE);
        TerminalFonts.drawMixed(g, Fam.MONO, 6f, "参照 · 上次扫描", 28, 228, 1.2f, TerminalTheme.INK_SOFT);
        TerminalFonts.drawMixed(g, Fam.CN, 9f, s.machineName, 28, 238, 0f, TerminalTheme.INK);
        TerminalFonts.drawMixed(g, Fam.MONO, 6f, "SNAP — · 128,64,-37", 28, 250, 0f, TerminalTheme.INK_FAINT);
        TerminalFonts.drawMixed(g, Fam.MONO, 5f, "S-01 / 结构替换选项", 28, 266, 1f, TerminalTheme.INK_FAINT);

        // 右侧开关面板
        panel(g, 196, 52, 330, 224);
        TerminalDraw.rect(g, 196, 52, 330, 22, TerminalTheme.INK);
        TerminalFonts.drawMixed(g, Fam.MONO, 8f, "S-01 · 结构替换选项", 204, 59, 1.2f, 0xFFFFFFFF);

        for (int i = 0; i < 8; i++) {
            float y = 82 + i * 22;
            boolean hover = s.isHover(204, y, 314, 21);
            if (hover) TerminalDraw.rect(g, 204, y, 314, 21, 0x0F3A5BC2);
            TerminalDraw.hline(g, 204, 518, y + 21, 0x1A1F2427);
            boolean on = s.flags[i] != 0;
            if (on) {
                TerminalDraw.line(g, 208, y + 10, 210, y + 13, 1.2f, TerminalTheme.COBALT);
                TerminalDraw.line(g, 210, y + 13, 215, y + 6, 1.2f, TerminalTheme.COBALT);
            } else {
                TerminalDraw.fillCircle(g, 211, y + 10, 1f, TerminalTheme.INK_FAINT);
            }
            TerminalFonts.drawMixed(g, Fam.CN, 9f, SW_CN[i], 224, y + 4, 0f, TerminalTheme.INK);
            TerminalFonts.drawMixed(g, Fam.MONO, 6f, SW_EN[i], 292, y + 7, 0.8f, TerminalTheme.INK_FAINT);
            TerminalFonts.drawMixed(g, Fam.MONO, 8f, on ? "ON" : "OFF", 498, y + 5, 0f,
                    on ? TerminalTheme.COBALT : TerminalTheme.INK_FAINT);
            final int idx = i;
            s.hit(204, y, 314, 21, SW_CN[i], () -> s.toggleFlag(idx), false);
        }
    }

    private static void panel(GuiGraphics g, float x, float y, float w, float h) {
        TerminalDraw.rect(g, x + 1, y + 1, w, h, 0x241F2427);
        TerminalDraw.rect(g, x, y, w, h, 0xF0EFF1EF);
        TerminalDraw.outline(g, x, y, w, h, 0x801F2427);
    }

    private static int alpha(int c, int a) {
        return (a << 24) | (c & 0x00FFFFFF);
    }

    private static int withAlpha(int c, int a) {
        return (a << 24) | (c & 0x00FFFFFF);
    }

    /** 供 TerminalScreen 调用：页签/行的总宽度。 */
    public static float tabWidth() {
        return TerminalFonts.mixedWidth(Fam.MONO, 7f, "舱室类型", 1f) + 20;
    }

    static final Page PAGE = Page.OPTIONS;
}
