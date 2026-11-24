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
    // 清单底边留到 246，232 的「全部 N 条」才不会被最后一行压住
    // 网页 .rep-list 实测：left20 top94 w520 h152（y94..246）
    public static final float LIST_H = 152;
    // 实机数据行数远多于网页原型，行会一路画到 246 把 y233 的「全部 N 条 · ESC 关闭」压住。
    // 可视区止于 230（文字上沿 233 前留 3px），行永不再与页脚文字重叠；滚动上限同步用它。
    public static final float LIST_CLIP_H = 136;
    private static final float ROW_H = 21;
    // 网页 report 行实测：数量 div left418+list20=438、width26、text-align:right → 右边缘 464；
    // 坐标 x454+list20=474；展开符号 x526+list20=546（在 520 宽视口外，被 overflow:hidden 裁掉）
    private static final float C_QTY_RIGHT = 464;
    private static final float C_QTY_HEAD = 428;
    private static final float C_COORD = 474;
    private static final float C_COORD_HEAD = 464;
    private static final float C_TOGGLE = 526;

    public static void draw(GuiGraphics g, TerminalScreen s) {
        TerminalDraw.rect(g, 20, 35, 2, 14, TerminalTheme.COBALT);
        TerminalFonts.draw(g, Fam.SERIF, 13f, "Scan Report", 30, 35, TerminalTheme.INK);
        TerminalFonts.drawMixed(g, Fam.MONO, 6f, s.targetText(), 438, 37, 1.2f, TerminalTheme.INK_FAINT);

        TerminalDraw.hline(g, 20, 540, 54, TerminalTheme.RULE);

        // 摘要：用真实分类计数（之前写死的「可放置/冲突/缺失」在真实报告里永远后两个为 0）
        float x = 20;
        java.util.List<String> cats = s.distinctCategories();
        int shownCats = Math.min(cats.size(), 3);
        for (int i = 0; i < shownCats; i++) {
            String cat = cats.get(i);
            String label = cat + " ";
            TerminalFonts.drawMixed(g, Fam.MONO, 7f, label, x, 61, 0, TerminalTheme.INK_SOFT);
            x += TerminalFonts.mixedWidth(Fam.MONO, 7f, label, 0);
            String v = String.valueOf(s.countOf(cat));
            TerminalFonts.drawMixedHeavy(g, Fam.MONO, 7f, v, x, 61, 0, TerminalTheme.INK);
            x += TerminalFonts.mixedWidth(Fam.MONO, 7f, v, 0) + 10;
        }
        long ms = s.scanMs();
        TerminalFonts.drawMixed(g, Fam.MONO, 7f, "耗时 " + (ms > 0 ? ms + " ms" : "—"),
                x, 61, 0, TerminalTheme.INK_SOFT);

        // 刻度尺 + 表头
        for (float tx = 20; tx <= 540; tx += 12) {
            TerminalDraw.vline(g, tx, 74, 79, 0xB81F2427);
        }
        TerminalDraw.hline(g, 524, 540, 75, TerminalTheme.INK_FAINT);

        // 表头与行使用同一套列坐标（网页实测：类 20 / 材料 62 / 数量头左对齐 428、行值右对齐 464 / 坐标头 464、行坐标 474）
        TerminalFonts.drawMixed(g, Fam.MONO, 6.5f, "类", 20, 82, 1.6f, TerminalTheme.INK_FAINT);
        TerminalFonts.drawMixed(g, Fam.MONO, 6.5f, "材料", 62, 82, 1.6f, TerminalTheme.INK_FAINT);
        TerminalFonts.drawMixed(g, Fam.MONO, 6.5f, "数量", C_QTY_HEAD, 82, 1.6f, TerminalTheme.INK_FAINT);
        TerminalFonts.drawMixed(g, Fam.MONO, 6.5f, "坐标", C_COORD_HEAD, 82, 1.6f, TerminalTheme.INK_FAINT);
        TerminalDraw.hline(g, 20, 540, 91, TerminalTheme.RULE);

        // 清单（裁剪，展开行高 48）
        g.enableScissor(Math.round(s.originX + LIST_X * s.scale), Math.round(s.originY + LIST_Y * s.scale),
                Math.round(s.originX + (LIST_X + LIST_W) * s.scale), Math.round(s.originY + (LIST_Y + LIST_CLIP_H) * s.scale));
        float y = LIST_Y + 2 - s.reportScroll;
        int shown = 0;
        for (int i = 0; i < s.reportRows.size(); i++) {
            TerminalScreen.ReportRow row = s.reportRows.get(i);
            if (!matchesFilter(s, row)) continue;
            boolean open = s.expanded[i];
            float rowH = ROW_H + (open ? 48 : 0);
            if (y + rowH >= LIST_Y && y <= LIST_Y + LIST_CLIP_H) {
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
        // 网页实测：文本盒 top232（字号6，行高1.2）→ 字形顶约 233.5；分隔线 y248
        TerminalFonts.drawMixed(g, Fam.MONO, 6f, omitted, 20, 233, 0.6f, TerminalTheme.INK_FAINT);

        // v32 源码按钮位置/宽度：20/100/180/476，74/74/96/64
        button(g, s, 20, 258, 74, "复制全部", false, TerminalTheme.INK, s::copyReport, 1);
        button(g, s, 100, 258, 74, "显示方块", s.showBoxes, TerminalTheme.INK, s::toggleBoxes, 2);
        button(g, s, 180, 258, 96, "筛选：" + s.filterName(), false, TerminalTheme.INK, s::cycleFilter, 3);
        button(g, s, 476, 258, 64, "关闭", true, TerminalTheme.PAPER, s::onClose, 0);
    }

    private static String shortName(String name) {
        if (name == null) return "";
        int cut = name.indexOf('、');
        if (cut > 0) name = name.substring(0, cut);
        return name;
    }

    private static boolean matchesFilter(TerminalScreen s, TerminalScreen.ReportRow row) {
        // 与 TerminalScreen.rowVisible 保持同一套口径：按报告里实际出现的分类去筛，
        // 不再拿「可放置 / 冲突 / 缺失」这三个原型假标签硬比（那样一筛就全空）。
        if (s.filter <= 0) return true;
        java.util.List<String> cats = s.distinctCategories();
        int i = s.filter - 1;
        return i < cats.size() && cats.get(i).equals(row.category());
    }

    private static void drawRow(GuiGraphics g, TerminalScreen s, TerminalScreen.ReportRow row,
                                int index, float y, boolean open) {
        TerminalDraw.hline(g, 20, 540, y + 19, TerminalTheme.RULE_SOFT);
        TerminalDraw.rect(g, 20, y + 5, 2, 11, row.color());

        // 相对行顶：类 x26/y+0 6.5px；材料 x68/y+1 8px + 字距.8；数量右对齐 464/y+1；坐标 x474/y+2
        // （materials 原来用 y-1，会把中文顶到分割线上；行高 21、分隔线在 y+19，
        //   中文 8px 从 y+1 开始才居中）
        TerminalFonts.drawMixed(g, Fam.MONO, 6.5f, row.category(), 26, y + 1, 0f, TerminalTheme.INK_SOFT);
        TerminalFonts.drawMixed(g, Fam.CN, 8f, row.name(), 68, y + 1, 0.8f, TerminalTheme.INK);
        TerminalFonts.drawRight(g, Fam.MONO, 8f, String.valueOf(row.count()), C_QTY_RIGHT, y + 1, TerminalTheme.INK);
        // 网页 rep-coord：展开时显示第一条坐标，折叠显示「—」
        String coord = open && !row.coords().isEmpty() ? fmt(row.coords().get(0)) : "—";
        TerminalFonts.drawMixed(g, Fam.MONO, 6f, coord, C_COORD, y + 2, 0.4f, TerminalTheme.INK_SOFT);

        // 网页 .rep-tog 在 left526，而 .rep-list 只有 520 宽（overflow:hidden），实测被裁掉不可见。
        // 这里不画 +/−，保持与网页一致；整行仍可点。

        if (open && !row.coords().isEmpty()) {
            // 网页 .rep-sub: left48+20=68、top21，每条 14px，前缀「↳ 」、下方无分隔线。
            // ↳ 不交给字体（Consolas 无此码位，实机会是 tofu）：用两笔线画同样的拐角，
            // 拐角占 4px，坐标文字仍从 68+6=74 起（与网页「↳ 」的 advance 一致）。
            float cy = y + 21;
            for (int k = 0; k < row.coords().size() && k < 3; k++) {
                TerminalDraw.hline(g, 68, 72, cy + 3, TerminalTheme.INK_FAINT);
                TerminalDraw.vline(g, 68, cy + 3, cy + 6, TerminalTheme.INK_FAINT);
                TerminalFonts.drawMixed(g, Fam.MONO, 6f, fmt(row.coords().get(k)), 74, cy, 0.4f,
                        TerminalTheme.INK_FAINT);
                cy += 14;
            }
        }
        // 命中区必须裁到清单视口内，否则展开行的 69px 命中矩形会压到 258 的按钮上，
        // 准星就会在按钮区画一个"看不见目标"的框。
        float top = Math.max(y, LIST_Y);
        float bottom = Math.min(y + ROW_H + (open ? 48f : 0f), LIST_Y + LIST_CLIP_H);
        if (y < LIST_Y + LIST_CLIP_H && y + ROW_H > LIST_Y && bottom - top > 2f) {
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
        // 填充按钮的文字必须用纸白：调用方传的 textColor 对线框按钮没意义，
        // 如果照搬就会变成「黑底 + 黑字」（实机截图「显示方块」按下后整块全黑）。
        int c = fill ? (fallbackInk ? TerminalTheme.INK : TerminalTheme.PAPER) : TerminalTheme.INK;
        if (icon > 0) icon(g, icon, x + 7, y + 4, c);
        TerminalFonts.drawCenteredMixed(g, Fam.MONO, 7f, label, x + w / 2f + (icon > 0 ? 4 : 0), y + 4, c);
        s.hit(x, y, w, 16, label, action, false);
    }

    /**
     * 网页按钮里的小图标：复制全部 = 图标包 0000（方框勾）、显示方块 = 0028（眼睛）。
     *
     * <p>网页「筛选：全部」按钮里没有图标，所以 kind 3 不再画菱形——之前那个菱形是无中生有。</p>
     */
    private static void icon(GuiGraphics g, int kind, float x, float y, int color) {
        if (kind != 1 && kind != 2) return;
        int id = kind == 1 ? TerminalIcons.BOX : TerminalIcons.EYE;
        float a = (color >>> 24) / 255f;
        // 填充态按钮底色是墨黑、图标要给纸白变体（黑色剪影乘色乘不白）
        boolean onDark = ((color >>> 16) & 0xFF) > 0x80;
        if (onDark) TerminalIcons.drawOnDark(g, id, x, y + 1, 8, a);
        else TerminalIcons.draw(g, id, x, y + 1, 8, a);
    }

    static String fmt(BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }

    public static void copyToClipboard(String text) {
        Minecraft.getInstance().keyboardHandler.setClipboard(text);
    }
}
