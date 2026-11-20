package com.gtlmodular.client.gui.terminal.print;

import com.gtlmodular.client.gui.terminal.print.TerminalTheme.Fam;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;

/** 报告页（02）：Scan Report + 摘要 + 可展开清单 + 4 按钮。 */
public final class TerminalReport {

    private TerminalReport() {}

    private static final float LIST_X = 20;
    private static final float LIST_Y = 94;
    private static final float LIST_W = 520;
    // 清单底边留到 226，232 的「全部 N 条」才不会被最后一行压住
    public static final float LIST_H = 152;
    private static final float ROW_H = 21;
    private static final float C_QTY_RIGHT = 464;
    private static final float C_COORD = 474;
    private static final float C_TOGGLE = 526;

    public static void draw(GuiGraphics g, TerminalScreen s) {
        TerminalDraw.rect(g, 20, 35, 2, 14, TerminalTheme.COBALT);
        TerminalFonts.draw(g, Fam.SERIF, 13f, "Scan Report", 30, 35, TerminalTheme.INK);
        TerminalFonts.drawMixed(g, Fam.MONO, 6f, s.targetText(), 438, 37, 1.2f, TerminalTheme.INK_FAINT);

        TerminalDraw.hline(g, 20, 540, 54, TerminalTheme.RULE);

        // 摘要
        String[] parts = {"可放置", "冲突", "缺失"};
        int[] vals = {s.placeCount, s.conflictCount, s.missingCount};
        float x = 20;
        for (int i = 0; i < 3; i++) {
            TerminalFonts.drawMixed(g, Fam.MONO, 7f, parts[i] + " ", x, 61, 0, TerminalTheme.INK_SOFT);
            x += TerminalFonts.mixedWidth(Fam.MONO, 7f, parts[i] + " ", 0);
            TerminalFonts.drawMixedHeavy(g, Fam.MONO, 7f, String.valueOf(vals[i]), x, 61, 0, TerminalTheme.INK);
            x += TerminalFonts.mixedWidth(Fam.MONO, 7f, String.valueOf(vals[i]), 0) + 10;
        }
        long ms = s.scanMs();
        TerminalFonts.drawMixed(g, Fam.MONO, 7f, "耗时 " + (ms > 0 ? ms + " ms" : "—"),
                x, 61, 0, TerminalTheme.INK_SOFT);

        // 刻度尺 + 表头
        for (float tx = 20; tx <= 540; tx += 12) {
            TerminalDraw.vline(g, tx, 74, 79, 0xB81F2427);
        }
        TerminalDraw.hline(g, 524, 540, 75, TerminalTheme.INK_FAINT);

        TerminalFonts.drawMixed(g, Fam.MONO, 6.5f, "类", 20, 82, 1.4f, TerminalTheme.INK_FAINT);
        TerminalFonts.drawMixed(g, Fam.MONO, 6.5f, "材料", 62, 82, 1.4f, TerminalTheme.INK_FAINT);
        TerminalFonts.drawRightMixed(g, Fam.MONO, 6.5f, "数量", C_QTY_RIGHT, 82, TerminalTheme.INK_FAINT);
        TerminalFonts.drawMixed(g, Fam.MONO, 6.5f, "坐标", 474, 82, 1.4f, TerminalTheme.INK_FAINT);
        TerminalDraw.hline(g, 20, 540, 91, TerminalTheme.RULE);

        // 清单（裁剪，展开行高 48）
        g.enableScissor(Math.round(s.originX + LIST_X * s.scale), Math.round(s.originY + LIST_Y * s.scale),
                Math.round(s.originX + (LIST_X + LIST_W) * s.scale), Math.round(s.originY + (LIST_Y + LIST_H) * s.scale));
        float y = LIST_Y + 2 - s.reportScroll;
        int shown = 0;
        for (int i = 0; i < s.reportRows.size(); i++) {
            TerminalScreen.ReportRow row = s.reportRows.get(i);
            if (!matchesFilter(s, row)) continue;
            boolean open = s.expanded[i];
            float rowH = ROW_H + (open ? 48 : 0);
            if (y + rowH >= LIST_Y && y <= LIST_Y + LIST_H) {
                drawRow(g, s, row, i, y, open);
            }
            y += rowH;
            shown++;
        }
        g.disableScissor();

        // 必须先落一笔色块再画文字：disableScissor 已清批，若先建 text 缓冲，
        // 后面的「关闭」填充按钮会晚于文字出队并盖住标签。
        TerminalDraw.hline(g, 20, 540, 248, TerminalTheme.RULE);
        String omitted = s.reportOmitted > 0
                ? "另有 " + s.reportOmitted + " 条已省略 · ESC 关闭"
                : "全部 " + shown + " 条 · ESC 关闭";
        TerminalFonts.drawMixed(g, Fam.MONO, 6f, omitted, 20, 232, 0.6f, TerminalTheme.INK_FAINT);

        button(g, s, 20, 258, 84, "复制全部", false, TerminalTheme.INK, s::copyReport, 1);
        button(g, s, 110, 258, 92, "显示方块", s.showBoxes, TerminalTheme.INK, s::toggleBoxes, 2);
        button(g, s, 208, 258, 100, "筛选：" + s.filterName(), false, TerminalTheme.INK, s::cycleFilter, 3);
        button(g, s, 468, 258, 72, "关闭", true, TerminalTheme.PAPER, s::onClose, 0);
    }

    private static String shortName(String name) {
        if (name == null) return "";
        int cut = name.indexOf('、');
        if (cut > 0) name = name.substring(0, cut);
        return name;
    }

    private static boolean matchesFilter(TerminalScreen s, TerminalScreen.ReportRow row) {
        return switch (s.filter) {
            case 1 -> "可放置".equals(row.category());
            case 2 -> "冲突".equals(row.category());
            case 3 -> "缺失".equals(row.category());
            default -> true;
        };
    }

    private static void drawRow(GuiGraphics g, TerminalScreen s, TerminalScreen.ReportRow row,
                                int index, float y, boolean open) {
        TerminalDraw.hline(g, 20, 540, y + 19, TerminalTheme.RULE_SOFT);
        TerminalDraw.rect(g, 20, y + 5, 2, 11, row.color());

        TerminalFonts.drawMixed(g, Fam.MONO, 6f, row.category(), 26, y + 6, 0f, TerminalTheme.INK_SOFT);
        TerminalFonts.drawMixed(g, Fam.CN, 8f, row.name(),
                68, y + 3, 0f, TerminalTheme.INK);
        TerminalFonts.drawRight(g, Fam.MONO, 8f, String.valueOf(row.count()), C_QTY_RIGHT, y + 3, TerminalTheme.INK);
        // 网页 rep-coord：展开时显示第一条坐标，折叠显示「—」
        String coord = open && !row.coords().isEmpty() ? fmt(row.coords().get(0)) : "—";
        TerminalFonts.drawMixed(g, Fam.MONO, 6f, coord, C_COORD, y + 6, 0.4f, TerminalTheme.INK_SOFT);

        TerminalFonts.drawMixed(g, Fam.MONO, 7f, open ? "−" : "+", C_TOGGLE, y + 4, 0f,
                open ? TerminalTheme.COBALT : TerminalTheme.INK_FAINT);

        if (open && !row.coords().isEmpty()) {
            float cy = y + 21;
            for (int k = 0; k < row.coords().size() && k < 3; k++) {
                // 网页展开行用小拐角而不是 »；用两笔线画，避免字体缺字
                TerminalDraw.hline(g, 74, 78, cy + 7, TerminalTheme.INK_FAINT);
                TerminalDraw.vline(g, 74, cy + 3, cy + 7, TerminalTheme.INK_FAINT);
                TerminalFonts.drawMixed(g, Fam.MONO, 6f, fmt(row.coords().get(k)), 82, cy + 3, 0.4f,
                        TerminalTheme.INK_FAINT);
                TerminalDraw.hline(g, 74, 520, cy + 14, 0x141F2427);
                cy += 16;
            }
        }
        // 命中区必须裁到清单视口内，否则展开行的 69px 命中矩形会压到 258 的按钮上，
        // 准星就会在按钮区画一个"看不见目标"的框。
        float top = Math.max(y, LIST_Y);
        float bottom = Math.min(y + ROW_H + (open ? 48f : 0f), LIST_Y + LIST_H);
        if (y < LIST_Y + LIST_H && y + ROW_H > LIST_Y && bottom - top > 2f) {
            s.hit(20, top, 520, bottom - top, row.name(), () -> s.toggleRow(index), false);
        }
    }

    private static void button(GuiGraphics g, TerminalScreen s, float x, float y, float w,
                               String label, boolean fill, int textColor, Runnable action, int icon) {
        boolean hover = s.isHover(x, y, w, 16);
        if (fill) {
            TerminalDraw.rect(g, x, y, w, 16, TerminalTheme.INK);
        } else {
            TerminalDraw.outline(g, x, y, w, 16, hover ? TerminalTheme.COBALT : TerminalTheme.INK);
        }
        boolean fallbackInk = fill && TerminalFonts.awtFailed();
        if (fill && fallbackInk) {
            // AWT 文字不可用时，MC 字在纯黑底上会看不见；改成纸底墨字保证可读
            TerminalDraw.rect(g, x, y, w, 16, TerminalTheme.PAPER);
            TerminalDraw.outline(g, x, y, w, 16, TerminalTheme.INK);
        }
        if (fill) {
            // AWT 文字走 guiTextured，和 gui 填充不是同一个 RenderType；先落地填充，
            // 否则部分批次顺序会把黑底排在文字之后，字就被盖住。
            g.flush();
        }
        int c = fill ? (fallbackInk ? TerminalTheme.INK : textColor) : TerminalTheme.INK;
        if (icon > 0) icon(g, icon, x + 7, y + 4, c);
        TerminalFonts.drawCenteredMixed(g, Fam.MONO, 7f, label, x + w / 2f + (icon > 0 ? 4 : 0), y + 4, c);
        s.hit(x, y, w, 16, label, action, false);
    }

    /** 网页按钮里的小图标：☑ 复制 / ◉ 显示方块 / 筛选菱形。 */
    private static void icon(GuiGraphics g, int kind, float x, float y, int color) {
        switch (kind) {
            case 1 -> {
                TerminalDraw.outline(g, x, y + 1, 8, 8, color);
                TerminalDraw.line(g, x + 2, y + 5, x + 3.5f, y + 7, 1f, color);
                TerminalDraw.line(g, x + 3.5f, y + 7, x + 6.5f, y + 2.5f, 1f, color);
            }
            case 2 -> {
                TerminalDraw.ellipseRing(g, x + 4, y + 5, 4.2f, 2.4f, 1f, color);
                TerminalDraw.fillCircle(g, x + 4, y + 5, 1.2f, color);
            }
            case 3 -> {
                float[] pts = {x + 4, y + 1, x + 8, y + 5, x + 4, y + 9, x, y + 5};
                TerminalDraw.polygon(g, pts, 0);
                for (int i = 0; i < 4; i++) {
                    TerminalDraw.line(g, pts[i * 2], pts[i * 2 + 1],
                            pts[((i + 1) % 4) * 2], pts[((i + 1) % 4) * 2 + 1], 1f, color);
                }
            }
            default -> { }
        }
    }

    static String fmt(BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }

    public static void copyToClipboard(String text) {
        Minecraft.getInstance().keyboardHandler.setClipboard(text);
    }
}
