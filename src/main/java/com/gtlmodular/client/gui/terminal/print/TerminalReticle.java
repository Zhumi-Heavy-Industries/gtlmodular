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
    /** 上一次是否处于吸附态：从自由态切到吸附态时直接落位，避免框在 0.2s 内与真实点击范围错位。 */
    private boolean wasLocked;
    /** 上一帧的时间戳，用来把缓动换成时间基准（网页 k=.22/帧）。 */
    private float lastSec = Float.NaN;

    public void click(float x, float y, String label, boolean ink) {
        // 时间基准必须与 draw() 的 seconds 一致（打开界面后的相对秒）：
        // 这里以前存的是 System.nanoTime()/1e9 的绝对秒，跟相对 seconds 相减恒为大负数，
        // 涟漪每帧都被判成「还没开始」当场删掉，点击特效从来没有出现过。
        float t = Float.isNaN(lastSec) ? 0f : lastSec;
        ripples.add(new Ripple(x, y, t, label, ink));
        if (ripples.size() > 8) ripples.remove(0);
    }

    /** 每帧在整页画完之后调用。 */
    public void draw(GuiGraphics g, float mx, float my, float seconds, Target target) {
        float tx, ty, tw, th;
        boolean locked = target != null;
        if (locked) {
            // 网页 loop() 是 tb.left-4 / +8（外扩 4px），但外扩后「框住的范围」比真正的
            // 可点范围大一圈，看起来就像生效范围和画面对不上。这里改为严格贴齐目标矩形：
            // 框住哪里，就能点哪里。
            tx = target.x;
            ty = target.y;
            tw = Math.max(6f, target.w);
            th = Math.max(6f, target.h);
        } else {
            // 网页 loop()：tx=X-15, ty=Y-15, 30×30，纯跟随、不夹边。
            // 这里以前夹了 `min(mx-15, 548-30)`，指针移到纸面右侧时框被卡住不再跟手，
            // 而网页端框是可以贴到纸外的——夹取才是"框不跟着指针"的真凶。
            tx = mx - 15f;
            ty = my - 15f;
            tw = 30f;
            th = 30f;
        }
        // 吸附瞬间直接贴合目标：缓动只用于「在同态内移动」，不能让框在新锁定的按钮上
        // 慢慢追——那段追帧时间里画出来的框根本不标记任何真实可点区域。
        boolean snap = locked && !wasLocked;
        wasLocked = locked;
        if (!primed || snap) {
            bx = tx; by = ty; bw = tw; bh = th; primed = true;
        }
        // 网页是每帧 k=.22 的指数缓动（≈60fps ⇒ 时间常数约 76ms）。
        // 按帧数收敛在低帧率下会慢得离谱（本界面单帧可能上百毫秒），所以换成时间基准。
        float dt = Float.isNaN(lastSec) ? 0.016f : Math.max(0f, Math.min(0.1f, seconds - lastSec));
        lastSec = seconds;
        float k = 1f - (float) Math.exp(-dt / 0.076f);
        bx += (tx - bx) * k;
        by += (ty - by) * k;
        bw += (tw - bw) * k;
        bh += (th - bh) * k;

        // 静止：墨色 7×7 L 角；吸附：钴蓝 10×10 L 角。
        // 网页 .fx-box b 两态 border 都是 1.3px（吸附只改颜色/尺寸，不改线宽）。
        int color = locked ? TerminalTheme.COBALT : TerminalTheme.INK;
        float arm = locked ? 10f : 7f;
        float thick = 1.3f;
        corner(g, bx, by, arm, arm, color, thick);
        corner(g, bx + bw, by, -arm, arm, color, thick);
        corner(g, bx, by + bh, arm, -arm, color, thick);
        corner(g, bx + bw, by + bh, -arm, -arm, color, thick);

        // 中心点：网页是 4×4 实心钴蓝方块，不是菱形
        TerminalDraw.rect(g, mx - 2, my - 2, 4, 4, TerminalTheme.COBALT);

        // 标签：先按宽度截断再画；只往目标下方放（网页 .fx-lab 的 top = 底边 +4），
        // 超出纸面就不画——网页那边由 sheet 的 overflow 裁掉，效果一样。
        // 以前放不下会翻到框上方：悬停底部页签时「报告页」标签就浮到页脚刻度线上、与方框脱开，
        // 看起来像页签自己冒出来一块（实机反馈「整体不整齐」）。
        String label = target != null ? target.label() : String.format("X %04d  Y %04d", (int) mx, (int) my);
        String shown = TerminalFonts.clip(Fam.MONO, 6f, label, 220f);
        float tw2 = TerminalFonts.trackedWidth(Fam.MONO, 6f, shown, 0.8f);
        float lx = Math.max(6f, Math.min(bx, 548f - (tw2 + 6f)));
        float ly = by + bh + 4f;
        if (ly + 10f <= TerminalTheme.H + 1f) {
            // 静止=半透明纸底墨字；吸附=钴蓝底纸白字（之前字色反了）
            int bg = locked ? TerminalTheme.COBALT : 0xD9EFF1EF;
            int fg = locked ? TerminalTheme.PAPER : TerminalTheme.INK;
            TerminalDraw.rect(g, lx, ly, tw2 + 6, 10, bg);
            // 标签底条先落地，纸白字才不会被同批次的填充盖住
            g.flush();
            TerminalFonts.drawTracked(g, Fam.MONO, 6f, shown, lx + 3, ly + 2, 0.8f, fg);
        }

        // 涟漪
        for (int i = ripples.size() - 1; i >= 0; i--) {
            Ripple r = ripples.get(i);
            float age = seconds - r.t0;
            if (age < 0 || age > 0.9f) {
                ripples.remove(i);
                continue;
            }
            float p = age / 0.9f;
            // 网页 .fx-rip：28px 环 scale .25→2.1 ⇒ 半径 3.5→29.4px
            float rad = 3.5f + p * 25.9f;
            int alpha = (int) ((1 - p) * 255);
            // 网页 .fx-rip 统一钴蓝，不随目标墨色变
            int c = TerminalTheme.COBALT & 0x00FFFFFF | (alpha << 24);
            TerminalDraw.ring(g, r.x, r.y, rad, 1f, c);
            // 环是色块、编号是贴图字，两套缓冲同批出队顺序不保证；环先单独落一批。
            g.flush();
            TerminalFonts.drawTracked(g, Fam.MONO, 6f, "+ " + r.label, r.x + 16, r.y - 6, 0.8f,
                    (c & 0x00FFFFFF) | (((int) ((1 - p) * 210)) << 24));
            TerminalFonts.drawTracked(g, Fam.MONO, 5f,
                    String.format("%04d · %04d", (int) r.x, (int) r.y),
                    r.x + 16, r.y + 6, 0.6f, (c & 0x00FFFFFF) | (((int) ((1 - p) * 160)) << 24));
        }
    }

    private static void corner(GuiGraphics g, float x, float y, float dx, float dy, int color, float thick) {
        // 水平臂（下缘角画在框内，避免伸到框外）
        float hy = y - (dy < 0 ? thick : 0);
        TerminalDraw.rect(g, Math.min(x, x + dx), hy, Math.abs(dx), thick, color);
        // 垂直臂（右缘角同样向内）
        float vx = x - (dx < 0 ? thick : 0);
        TerminalDraw.rect(g, vx, Math.min(y, y + dy), thick, Math.abs(dy), color);
    }
}
