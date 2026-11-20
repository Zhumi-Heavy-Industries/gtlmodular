package com.gtlmodular.client.gui.terminal.print;

import java.util.ArrayList;
import java.util.List;

import com.gtlmodular.client.gui.terminal.print.TerminalTheme.Fam;

import net.minecraft.client.gui.GuiGraphics;

/** 鼠标准星（四角吸附框 + 标签）、点击涟漪。 */
public final class TerminalReticle {

    public record Target(float x, float y, float w, float h, String label, boolean ink) {}

    private record Ripple(float x, float y, float t0, String label, boolean ink) {}

    private final List<Ripple> ripples = new ArrayList<>();
    private float bx, by, bw, bh;
    private boolean primed;

    public void click(float x, float y, String label, boolean ink) {
        ripples.add(new Ripple(x, y, (float) (System.nanoTime() / 1e9), label, ink));
        if (ripples.size() > 8) ripples.remove(0);
    }

    /** 每帧在整页画完之后调用。 */
    public void draw(GuiGraphics g, float mx, float my, float seconds, Target target) {
        float tx, ty, tw, th;
        boolean locked = target != null;
        if (locked) {
            tx = Math.max(6, target.x - 4);
            ty = Math.max(6, target.y - 4);
            tw = Math.max(8, Math.min(554, target.x + target.w + 4) - tx);
            th = Math.max(8, Math.min(334, target.y + target.h + 4) - ty);
        } else {
            // 只 clamp 目标值，别动缓动值，否则贴边会抖
            tx = Math.max(6, Math.min(mx - 15, 548 - 30));
            ty = Math.max(6, Math.min(my - 15, TerminalTheme.H - 14 - 30));
            tw = 30;
            th = 30;
        }
        if (!primed) {
            bx = tx; by = ty; bw = tw; bh = th; primed = true;
        }
        float k = 0.28f;
        bx += (tx - bx) * k;
        by += (ty - by) * k;
        bw += (tw - bw) * k;
        bh += (th - bh) * k;

        int color = locked && target.ink() ? TerminalTheme.INK : TerminalTheme.COBALT;
        float arm = locked ? 10f : 7f;
        corner(g, bx, by, arm, 1, color);
        corner(g, bx + bw, by, -arm, 1, color);
        corner(g, bx, by + bh, 1, -arm, color);
        corner(g, bx + bw, by + bh, -arm, -arm, color);

        // 中心点
        TerminalDraw.rect(g, mx - 1, my - 1, 2, 2, TerminalTheme.COBALT);

        // 标签：先按宽度截断再画；下方放不下就翻到框上方，且水平/垂直都夹在纸面内
        String label = target != null ? target.label() : String.format("X %04d  Y %04d", (int) mx, (int) my);
        String shown = TerminalFonts.clip(Fam.MONO, 6f, label, 220f);
        float tw2 = TerminalFonts.trackedWidth(Fam.MONO, 6f, shown, 0.8f);
        float lx = Math.max(6f, Math.min(bx, 548f - (tw2 + 6f)));
        float below = by + bh + 4f;
        float above = Math.min(by - 14f, 288f);
        float ly = (below + 10f <= 288f) ? below : (above >= 6f ? above : Math.min(330f, below));
        TerminalDraw.rect(g, lx, ly, tw2 + 6, 10, color);
        TerminalFonts.drawTracked(g, Fam.MONO, 6f, shown, lx + 3, ly + 2, 0.8f, TerminalTheme.PAPER);

        // 涟漪
        for (int i = ripples.size() - 1; i >= 0; i--) {
            Ripple r = ripples.get(i);
            float age = seconds - r.t0;
            if (age < 0 || age > 1.15f) {
                ripples.remove(i);
                continue;
            }
            float p = age / 1.15f;
            float rad = 14 + p * 16;
            int alpha = (int) ((1 - p) * 255);
            int c = (r.ink ? TerminalTheme.INK : TerminalTheme.COBALT) & 0x00FFFFFF | (alpha << 24);
            TerminalDraw.ring(g, r.x, r.y, rad, 1f, c);
            TerminalFonts.drawTracked(g, Fam.MONO, 6f, "+ " + r.label, r.x + 16, r.y - 6, 0.8f,
                    (c & 0x00FFFFFF) | (((int) ((1 - p) * 210)) << 24));
            TerminalFonts.drawTracked(g, Fam.MONO, 5f,
                    String.format("%04d · %04d", (int) r.x, (int) r.y),
                    r.x + 16, r.y + 6, 0.6f, (c & 0x00FFFFFF) | (((int) ((1 - p) * 160)) << 24));
        }
    }

    private static void corner(GuiGraphics g, float x, float y, float dx, float dy, int color) {
        TerminalDraw.hline(g, x, x + dx, y, color);
        TerminalDraw.vline(g, x, y, y + dy, color);
        TerminalDraw.hline(g, x, x + dx, y + (dy > 0 ? 1 : -1), color);
        TerminalDraw.vline(g, x + (dx > 0 ? 1 : -1), y, y + dy, color);
    }
}
