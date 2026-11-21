package com.gtlmodular.client.gui.terminal.print;

import com.gtlmodular.GTLModular;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * 图标包（iconpack11）的真图绘制。
 *
 * <p>过去页头、报告按钮等处都是拿 {@link TerminalDraw} 的线段/圆去「近似」这些图标，
 * 形不达意。这里统一按网页端的映射贴真图：</p>
 * <pre>
 *   box   = 0000   方框勾（页头 ☑ / 复制全部）
 *   eye   = 0028   眼睛（显示方块）
 *   sun   = 0007   半月
 *   bolt  = 0024   闪电
 *   star4 = 0020   四角星群（选择档位）
 *   ok    = 0029   眼睛+勾
 *   no    = 0016   眼睛+叉
 *   check = 0014   圆圈勾
 *   cross = 0015   圆圈叉
 * </pre>
 *
 * <p>贴图已统一烘成 64×64（见 build 时的资源归一化），所以 UV 直接取满图。</p>
 */
public final class TerminalIcons {

    private TerminalIcons() {}

    /** 图标源图边长（资源已归一化为 64×64）。 */
    private static final int SRC = 64;

    public static final int BOX = 0;
    public static final int SUN = 7;
    public static final int CHECK = 14;
    public static final int CROSS = 15;
    public static final int NO = 16;
    public static final int STAR4 = 20;
    public static final int BOLT = 24;
    public static final int EYE = 28;
    public static final int OK = 29;

    /** 按设计尺寸绘制一枚黑色剪影图标；alpha 0..1（网页用 opacity 控制浓淡）。 */
    public static void draw(GuiGraphics g, int id, float x, float y, float size, float alpha) {
        blit(g, id, x, y, size, alpha, false);
    }

    /**
     * 深色底（填充按钮 / 黑条）上用白色变体。
     * 贴图是黑色剪影，setColor 只能乘色、没法反白，所以资源里另存了 {@code *_w.png}。
     */
    public static void drawOnDark(GuiGraphics g, int id, float x, float y, float size, float alpha) {
        blit(g, id, x, y, size, alpha, true);
    }

    private static void blit(GuiGraphics g, int id, float x, float y, float size, float alpha, boolean white) {
        ResourceLocation rl = new ResourceLocation(GTLModular.MOD_ID,
                String.format("textures/gui/terminal/icons/%04d%s.png", id, white ? "_w" : ""));
        int s = Math.round(size);
        g.setColor(1f, 1f, 1f, alpha);
        g.blit(rl, Math.round(x), Math.round(y), s, s, 0f, 0f, SRC, SRC, SRC, SRC);
        g.setColor(1f, 1f, 1f, 1f);
    }
}
