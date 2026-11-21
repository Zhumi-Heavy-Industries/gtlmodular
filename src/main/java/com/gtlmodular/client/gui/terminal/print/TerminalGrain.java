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
 * <p>即：一张 160×160 的湍流噪点图，整页平铺，再以 multiply 混色。multiply 与
 * 「黑色以 {@code a·(1-C)} 的透明度叠加」等价（C 为噪点灰度），所以贴图直接烘成
 * 黑色 RGB + 换算后的 alpha，绘制时按普通 alpha 混合即可，不需要混合模式。</p>
 *
 * <p>幅度说明：网页设了 {@code opacity:.5} 且噪点矩形自身 {@code opacity:.10}，
 * 叠加后最深只把纸色（#EFF1EF，239）往下降 3~4 级。所以这里刻意保持极轻——
 * 实测网页渲染出来的纸面就是 237~239 的细微抖动，再浓就不是「一模一样」而是加了滤镜。
 * 要临时看效果可调 {@link #STRENGTH}。</p>
 */
public final class TerminalGrain {

    private static final ResourceLocation TILE = new ResourceLocation(
            GTLModular.MOD_ID, "textures/gui/terminal/grain.png");

    /** 贴图边长（= 网页 SVG viewBox 160×160，1 纹素 : 1 设计像素）。 */
    private static final int SRC = 160;

    /** 幅度倍率：1.0 = 与网页实测同幅度。 */
    private static final float STRENGTH = 1.0f;

    private TerminalGrain() {
    }

    /**
     * 在当前位置平铺整页颗粒。调用点必须在纸底色块<b>之后</b>、页框与正文<b>之前</b>，
     * 与网页 DOM 里 {@code .grain} 位于内容之下一致。
     */
    public static void draw(GuiGraphics g) {
        if (STRENGTH <= 0.001f) return;
        g.setColor(1f, 1f, 1f, STRENGTH);
        try {
            for (int y = 0; y < TerminalTheme.H; y += SRC) {
                int h = Math.min(SRC, TerminalTheme.H - y);
                for (int x = 0; x < TerminalTheme.W; x += SRC) {
                    int w = Math.min(SRC, TerminalTheme.W - x);
                    // 边缘块按剩余尺寸裁源区，避免颗粒溢出纸外染到背景
                    g.blit(TILE, x, y, w, h, 0f, 0f, w, h, SRC, SRC);
                }
            }
        } finally {
            g.setColor(1f, 1f, 1f, 1f);
        }
    }
}
