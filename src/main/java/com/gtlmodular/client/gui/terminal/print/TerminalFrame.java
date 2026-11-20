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
        crops(g);
        header(g, page);
        rail(g);
        footer(g, page);
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
        // ☑ 图标
        TerminalDraw.outline(g, 14, 10, 11, 11, 0xD91F2427);
        TerminalDraw.line(g, 16, 15, 18, 18, 1.4f, TerminalTheme.INK);
        TerminalDraw.line(g, 18, 18, 22, 12, 1.4f, TerminalTheme.INK);

        TerminalFonts.drawMixed(g, Fam.MONO, 7f, "哦鲸鲸终端", 29, 9, 1.6f, TerminalTheme.INK);
        TerminalFonts.drawMixed(g, Fam.MONO, 6f, "THE WHALE TERMINAL", 29, 19, 1.6f, TerminalTheme.INK_FAINT);

        TerminalFonts.drawMixed(g, Fam.MONO, 6f, "终端操控 · " + page.cn, 240, 13, 1.6f, TerminalTheme.INK_SOFT);

        String[] icons = {"eye", "star", "sun", "bolt", "box", "hex"};
        for (int i = 0; i < icons.length; i++) {
            toolIcon(g, icons[i], 482 + i * 12, 11);
        }
        TerminalDraw.hline(g, 14, 546, 29, TerminalTheme.RULE);
    }

    private static void toolIcon(GuiGraphics g, String kind, float x, float y) {
        int c = 0x9E1F2427;
        float cx = x + 4.5f, cy = y + 4.5f;
        switch (kind) {
            case "eye" -> {
                TerminalDraw.arc(g, cx, cy, 3.6f, 200f, 340f, 1f, c);
                TerminalDraw.fillCircle(g, cx, cy, 1.2f, c);
            }
            case "star" -> {
                TerminalDraw.line(g, cx, y, cx, y + 9, 1f, c);
                TerminalDraw.line(g, x, cy, x + 9, cy, 1f, c);
                TerminalDraw.line(g, x + 1, y + 1, x + 8, y + 8, 0.8f, c);
                TerminalDraw.line(g, x + 8, y + 1, x + 1, y + 8, 0.8f, c);
            }
            case "sun" -> {
                TerminalDraw.ring(g, cx, cy, 2.4f, 1f, c);
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI * 2 * i / 8;
                    TerminalDraw.line(g, cx + (float) Math.cos(a) * 3.4f, cy + (float) Math.sin(a) * 3.4f,
                            cx + (float) Math.cos(a) * 4.4f, cy + (float) Math.sin(a) * 4.4f, 0.8f, c);
                }
            }
            case "bolt" -> TerminalDraw.polygon(g, new float[]{
                    cx + 1.5f, y, x + 1, cy + 1, cx, cy + 1, x + 1.5f, y + 9, x + 8, cy - 1, cx + 1, cy - 1
            }, c);
            case "box" -> {
                TerminalDraw.outline(g, x + 1, y + 1, 8, 8, c);
                TerminalDraw.line(g, x + 2, cy, x + 4, y + 6.5f, 1f, c);
                TerminalDraw.line(g, x + 4, y + 6.5f, x + 7.5f, y + 2.5f, 1f, c);
            }
            default -> {
                float r = 4.4f;
                float[] pts = new float[12];
                for (int i = 0; i < 6; i++) {
                    double a = Math.PI / 3 * i - Math.PI / 2;
                    pts[i * 2] = cx + (float) Math.cos(a) * r;
                    pts[i * 2 + 1] = cy + (float) Math.sin(a) * r;
                }
                for (int i = 0; i < 6; i++) {
                    TerminalDraw.line(g, pts[i * 2], pts[i * 2 + 1],
                            pts[((i + 1) % 6) * 2], pts[((i + 1) % 6) * 2 + 1], 0.9f, c);
                }
            }
        }
    }

    private static void rail(GuiGraphics g) {
        TerminalFonts.drawVertical(g, Fam.MONO, 5.5f,
                "LATERANO / 1099 / MEMORY ARCHIVE", 9, 44, 2.6f, TerminalTheme.INK_FAINT);
    }

    // ────────────────────────────── 页脚

    private static void footer(GuiGraphics g, Page page) {
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

        // 状态印章（旋转 -6°）
        stamp(g, 456, 286, STAMP[idx]);

        // 页签
        navTabs(g, page);
    }

    private static void xmark(GuiGraphics g, float x, float y) {
        TerminalDraw.vline(g, x + 4, y, y + 9, 0xBD55605F);
        TerminalDraw.hline(g, x, x + 9, y + 4, 0xBD55605F);
    }

    private static void stamp(GuiGraphics g, float left, float top, String text) {
        float tw = Math.min(150f, TerminalFonts.trackedWidth(Fam.MONO, 6f, text, 1.4f));
        float w = tw + 12f;
        float h = 13f;
        g.pose().pushPose();
        g.pose().translate(left + w / 2f, top + h / 2f, 0);
        g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-6f));
        g.pose().translate(-w / 2f, -h / 2f, 0);
        TerminalDraw.outline(g, 0, 0, w, h, 0xB83A5BC2);
        TerminalFonts.drawTracked(g, Fam.MONO, 6f, text, 6f, 3f, 1.4f, 0xB83A5BC2);
        g.pose().popPose();
    }

    private static void navTabs(GuiGraphics g, Page active) {
        float x = 36;
        for (Page p : Page.values()) {
            float numW = TerminalFonts.mixedWidth(Fam.MONO, 6f, p.num, 0f);
            float nameW = TerminalFonts.mixedWidth(Fam.MONO, 7f, p.cn, 1.2f);
            boolean on = p == active;
            if (on) {
                TerminalDraw.rect(g, x - 4, 322, numW + 5 + nameW + 8, 2, TerminalTheme.COBALT);
            }
            TerminalFonts.drawMixed(g, Fam.MONO, 6f, p.num, x, 324f, 0f,
                    on ? TerminalTheme.COBALT : TerminalTheme.INK_FAINT);
            TerminalFonts.drawMixed(g, Fam.MONO, 7f, p.cn, x + numW + 5f, 323f, 1.2f,
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
