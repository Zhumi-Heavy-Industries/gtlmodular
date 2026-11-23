package com.gtlmodular.client.gui.terminal.print;

import com.gtlmodular.GTLModular;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * 纸面颗粒：与网页端 {@code .grain} 同源。
 *
 * <p>网页原文：</p>
 * <pre>
 *   .grain{position:absolute;inset:0;opacity:.5;mix-blend-mode:multiply;
 *     background-image:url("data:image/svg+xml,...feTurbulence type='fractalNoise'
 *       baseFrequency='.85' numOctaves='2' stitchTiles='stitch'...
 *       &lt;rect width='100%' height='100%' opacity='.10' filter='url(#n)'&gt;")}
 * </pre>
 *
 * <p>即一张噪点图整页平铺，再用 multiply 压暗纸色。multiply 与「黑色以
 * {@code a·(1-C)} 的透明度叠加」等价（C 为噪点灰度），所以这里<b>直接把压暗后的纸色
 * 烘进 RGB</b>，贴图完全不透明、按 1:1 平铺。</p>
 *
 * <p>为什么不直接在纸色上叠一层带 alpha 的黑色噪点：早期版本这么做，实机整片变纯黑
 * （贴图的 alpha 没有被混合，凡是 alpha&gt;0 的像素都按不透明色写进帧缓冲）。
 * 不透明贴图没有这个问题，而且即便被重复出队也是幂等的——画多少遍结果都一样。</p>
 */
public final class TerminalGrain {

    private static final ResourceLocation TILE = new ResourceLocation(
            GTLModular.MOD_ID, "textures/gui/terminal/paper_tile.png");

    /** 贴图边长（128×128 无缝噪点，1 纹素 : 1 设计像素）。 */
    private static final int SRC = 128;

    private TerminalGrain() {
    }

    /** 纸面贴图是否可用（缓存一次结果，避免每帧查资源管理器）。 */
    private static Boolean usable;

    public static boolean usable() {
        if (usable == null) {
            boolean ok;
            try {
                ok = net.minecraft.client.Minecraft.getInstance().getResourceManager()
                        .getResource(TILE).isPresent();
            } catch (Throwable t) {
                ok = false;
            }
            usable = ok;
            com.gtlmodular.GTLModular.LOGGER.info("[TermGrain] 纸面贴图 {} 可用={}", TILE, ok);
        }
        return usable;
    }

    /**
     * 平铺整页纸面（纸色 + 颗粒）。这是<b>不透明</b>贴图，相当于把整张纸画出来，
     * 调用点必须在页框与正文之前。
     */
    public static void drawPaper(GuiGraphics g) {
        for (int y = 0; y < TerminalTheme.H; y += SRC) {
            int h = Math.min(SRC, TerminalTheme.H - y);
            for (int x = 0; x < TerminalTheme.W; x += SRC) {
                int w = Math.min(SRC, TerminalTheme.W - x);
                // 边缘块按剩余尺寸裁源区，避免纸面溢出到纸张之外
                g.blit(TILE, x, y, w, h, 0f, 0f, w, h, SRC, SRC);
            }
        }
    }
}
