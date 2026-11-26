package com.gtlmodular.client.gui.terminal.print;

import com.gtlmodular.client.gui.terminal.print.TerminalTheme.Fam;
import com.gtlmodular.client.gui.terminal.print.TerminalTheme.Page;

import net.minecraft.client.gui.GuiGraphics;

/** 三页统一边框：报头 / 四角裁切 / 竖排档案号 / 页脚 / 色票 / 落款 / 印章 / 页签。 */
public final class TerminalFrame {

    private TerminalFrame() {}

    private static final String[] REF = {
            "REF · GT-LM / 06-C",
            "CLASSIFIER · SHANHAI",
            "SHEET 03 · 1,284 NODES"
    };
    private static final String[] STAMP = {
            "SCAN 412 ms",
            "6 / 8 REQUIRED",
            "VERIFY / OK"
    };

    public static void draw(GuiGraphics g, Page page) {
        draw(g, page, -1);
    }

    /** @param required 本次扫描到的必需舱室类型数；-1 表示无报告，沿用原稿文案。 */
    public static void draw(GuiGraphics g, Page page, int required) {
        crops(g);
        header(g, page);
        rail(g);
        footer(g, page, required);
        micro(g, page);
    }

    // ────────────────────────────── 报头

    private static void crops(GuiGraphics g) {
        TerminalDraw.vline(g, 6, 6, 15, TerminalTheme.CROP);
        TerminalDraw.hline(g, 6, 15, 6, TerminalTheme.CROP);
        TerminalDraw.vline(g, 554, 6, 15, TerminalTheme.CROP);
        TerminalDraw.hline(g, 545, 554, 6, TerminalTheme.CROP);
        TerminalDraw.vline(g, 6, 325, 334, TerminalTheme.CROP);
        TerminalDraw.hline(g, 6, 15, 334, TerminalTheme.CROP);
        TerminalDraw.vline(g, 554, 325, 334, TerminalTheme.CROP);
        TerminalDraw.hline(g, 545, 554, 334, TerminalTheme.CROP);
    }

    private static void header(GuiGraphics g, Page page) {
        // ☑ 图标（网页 <img IC.box> 11×11 @14,10 opacity .85）：用图标包真图，不再手绘
        TerminalIcons.draw(g, TerminalIcons.BOX, 14, 10, 11, 0.85f);

        TerminalFonts.drawMixed(g, Fam.MONO, 7f, "哦鲸鲸终端", 29, 9, 1.6f, TerminalTheme.INK);
        TerminalFonts.drawMixed(g, Fam.MONO, 6f, "THE WHALE TERMINAL", 29, 19, 1.6f, TerminalTheme.INK_FAINT);

        TerminalFonts.drawMixed(g, Fam.MONO, 6f, "终端操控 · " + page.cn, 240, 13, 1.6f, TerminalTheme.INK_SOFT);

        String[] icons = {"eye", "star", "sun", "bolt", "box", "hex"};
        for (int i = 0; i < icons.length; i++) {
            toolIcon(g, icons[i], 482 + i * 12, 11);
        }
        TerminalDraw.hline(g, 14, 546, 29, TerminalTheme.RULE);
    }

    /**
     * 页头工具图标：直接用网页端的资源与回退规则，不再手绘任何一支。
     *
     * <p>网页原文：{@code for k in ["eye","star4","sun","bolt","box","hex"]: IC.get(k, IC["box"])}
     * —— {@code IC} 里没有 {@code "hex"} 这个键，所以第 6 个位置实际回退成 {@code 0000}（方框勾）。
     * 为了让实机与网页逐像素一致，这里照抄这个行为（而不是补一个网页没有的六边形）。</p>
     */
    private static void toolIcon(GuiGraphics g, String kind, float x, float y) {
        int id = switch (kind) {
            case "eye" -> TerminalIcons.EYE;
            case "star" -> TerminalIcons.STAR4;
            case "sun" -> TerminalIcons.SUN;
            case "bolt" -> TerminalIcons.BOLT;
            default -> TerminalIcons.BOX; // "box" 与 "hex" 在网页端都是 0000
        };
        TerminalIcons.draw(g, id, x, y, 9, 0.62f);
    }

    private static void rail(GuiGraphics g) {
        TerminalFonts.drawVertical(g, Fam.MONO, 5.5f,
                "LATERANO / 1099 / MEMORY ARCHIVE", 9, 44, 2.6f, TerminalTheme.INK_FAINT);
    }

    // ────────────────────────────── 页脚

    private static void footer(GuiGraphics g, Page page, int required) {
        int idx = page.ordinal();

        // 套准加号 ×2
        xmark(g, 20, 324);
        xmark(g, 531, 324);

        // 主刻度尺：每 60px 一根、高 7
        for (float x = 36; x <= 524; x += 60) {
            TerminalDraw.vline(g, x, 307, 314, 0xE055605F);
        }
        // 页脚线
        TerminalDraw.hline(g, 14, 546, 300, TerminalTheme.RULE);

        // CMYK 色票
        int[] cmyk = {TerminalTheme.CMYK_C, TerminalTheme.CMYK_M, TerminalTheme.CMYK_Y, TerminalTheme.CMYK_K};
        for (int i = 0; i < 4; i++) {
            TerminalDraw.rect(g, 16 + i * 9, 288, 7, 7, cmyk[i]);
        }

        // REF 落款
        TerminalFonts.drawMixed(g, Fam.MONO, 5f, REF[idx], 60, 289, 0.5f, TerminalTheme.INK_SOFT);

        // 状态印章（旋转 -6°）：第二页印章的必需舱室数随扫描报告变化。
        String stampText = idx == 1 && required >= 0
                ? required + " / 8 REQUIRED" : STAMP[idx];
        stamp(g, 456, 286, stampText);

        // 页签
        navTabs(g, page);
    }

    private static void xmark(GuiGraphics g, float x, float y) {
        TerminalDraw.vline(g, x + 4, y, y + 9, 0xBD55605F);
        TerminalDraw.hline(g, x, x + 9, y + 4, 0xBD55605F);
    }

    private static void stamp(GuiGraphics g, float left, float top, String text) {
        // 网页 .stamp 实测：left455.4 top282.7 w64.8 h20.6（含 rotate -6°）；内高 13、内边距 2px 5px、opacity .72
        float tw = Math.min(150f, TerminalFonts.trackedWidth(Fam.MONO, 6f, text, 1.4f));
        float w = tw + 10f;
        float h = 13f;
        int c = 0xB83A5BC2;
        g.pose().pushPose();
        g.pose().translate(left + w / 2f, top + h / 2f, 0);
        g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-6f));
        g.pose().translate(-w / 2f, -h / 2f, 0);
        TerminalDraw.outline(g, 0, 0, w, h, c);
        TerminalFonts.drawTracked(g, Fam.MONO, 6f, text, 5f, 3f, 1.4f, c);
        g.pose().popPose();
    }

    private static void navTabs(GuiGraphics g, Page active) {
        // 网页 .navfoot 实测：x36、gap26、单页签 38.6 宽；文字盒 y326（相对页签 +4）；下划线 y322 h2 左右各外扩 4
        float x = 36;
        for (Page p : Page.values()) {
            float numW = TerminalFonts.mixedWidth(Fam.MONO, 6f, p.num, 0f);
            float nameW = TerminalFonts.mixedWidth(Fam.MONO, 7f, p.cn, 1.2f);
            boolean on = p == active;
            if (on) {
                TerminalDraw.rect(g, x - 4, 322, numW + 5 + nameW + 8, 2, TerminalTheme.COBALT);
            }
            TerminalFonts.drawMixed(g, Fam.MONO, 6f, p.num, x, 326, 0f,
                    on ? TerminalTheme.COBALT : TerminalTheme.INK_FAINT);
            TerminalFonts.drawMixed(g, Fam.MONO, 7f, p.cn, x + numW + 5f, 326, 1.2f,
                    on ? TerminalTheme.INK : TerminalTheme.INK_FAINT);
            x += numW + 5f + nameW + 26f;
        }
    }

    private static void micro(GuiGraphics g, Page page) {
        if (page == Page.MAIN) {
            // 内容区局部刻度：每 12px 一根、高 5
            for (float x = 36; x <= 226; x += 12) {
                TerminalDraw.vline(g, x, 32, 37, 0xB81F2427);
            }
            TerminalDraw.hline(g, 536, 550, 183, TerminalTheme.INK_FAINT);
        } else if (page == Page.REPORT) {
            for (float x = 20; x <= 540; x += 12) {
                TerminalDraw.vline(g, x, 74, 79, 0xB81F2427);
            }
            TerminalDraw.hline(g, 524, 540, 75, TerminalTheme.INK_FAINT);
        }
    }
}
