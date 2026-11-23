package com.gtlmodular.client.gui.terminal.print;

import java.util.HashMap;
import java.util.Map;

import com.gtlmodular.GTLModular;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * 图标包（iconpack11）真图绘制。
 *
 * <p>映射沿用网页端：</p>
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
 * <p>这里不再直接 blit 资源 PNG。原因：资源是 128×128，而页头图标只有 9 个设计像素
 * （3 倍缩放下 27 个物理像素），MC 对普通贴图的缩小采样是最近邻，2.4 倍下采样会<b>丢像素</b>，
 * 轮廓被啃成缺口的锯齿块——看着就像手绘的。现在按「目标物理像素尺寸」用 AWT 双三次
 * 重采样烘一张正好大小的贴图（同时把白色变体也从 alpha 通道生成），再 1:1 贴上去。</p>
 */
public final class TerminalIcons {

    private TerminalIcons() {}

    /** 资源图边长（已归一化 128×128，方形拉伸与网页 &lt;img width=9 height=9&gt; 一致）。 */
    private static final int SRC = 128;

    public static final int BOX = 0;
    public static final int SUN = 7;
    public static final int CHECK = 14;
    public static final int CROSS = 15;
    public static final int NO = 16;
    public static final int STAR4 = 20;
    public static final int BOLT = 24;
    public static final int EYE = 28;
    public static final int OK = 29;

    private record Key(int id, int px, boolean white) {}

    private static final Map<Key, ResourceLocation> TEX = new HashMap<>();
    private static final Map<Integer, java.awt.image.BufferedImage> SRCIMG = new HashMap<>();
    private static int seq;
    private static boolean bakeWarned;

    public static void draw(GuiGraphics g, int id, float x, float y, float size, float alpha) {
        paint(g, id, x, y, size, alpha, false);
    }

    /** 深底上的图标（墨黑底按钮）：用 alpha 生成纯白剪影，否则乘色乘不白。 */
    public static void drawOnDark(GuiGraphics g, int id, float x, float y, float size, float alpha) {
        paint(g, id, x, y, size, alpha, true);
    }

    private static void paint(GuiGraphics g, int id, float x, float y, float size, float alpha, boolean white) {
        int dest = Math.max(2, Math.round(size));
        // 目标物理像素：设计像素 × (GUI 缩放 × 纸张缩放)，烘焙到这个尺寸才能 1:1 上屏
        int px = Math.max(4, Math.round(size * TerminalFonts.deviceScale()));
        Key key = new Key(id, px, white);
        ResourceLocation rl = TEX.get(key);
        if (rl == null) {
            rl = bake(id, px, white);
            if (rl == null) return;
            TEX.put(key, rl);
        }
        g.setColor(1f, 1f, 1f, alpha);
        try {
            g.blit(rl, Math.round(x), Math.round(y), dest, dest, 0f, 0f, px, px, px, px);
        } finally {
            g.setColor(1f, 1f, 1f, 1f);
        }
    }

    private static ResourceLocation bake(int id, int px, boolean white) {
        try {
            java.awt.image.BufferedImage src = source(id);
            if (src == null) return null;
            java.awt.image.BufferedImage out = new java.awt.image.BufferedImage(
                    px, px, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g2 = out.createGraphics();
            g2.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                    java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g2.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING,
                    java.awt.RenderingHints.VALUE_RENDER_QUALITY);
            g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                    java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            // 方形拉伸：网页就是 <img width=9 height=9>，非等比也一样拉
            g2.drawImage(src, 0, 0, px, px, null);
            g2.dispose();

            int[] argb = ((java.awt.image.DataBufferInt) out.getRaster().getDataBuffer()).getData();
            com.mojang.blaze3d.platform.NativeImage ni =
                    new com.mojang.blaze3d.platform.NativeImage(
                            com.mojang.blaze3d.platform.NativeImage.Format.RGBA, px, px, false);
            int idx = 0;
            for (int yy = 0; yy < px; yy++) {
                for (int xx = 0; xx < px; xx++) {
                    int c = argb[idx++];
                    int a = (c >>> 24) & 0xFF;
                    int r = white ? 0xFF : ((c >>> 16) & 0xFF);
                    int gg = white ? 0xFF : ((c >>> 8) & 0xFF);
                    int b = white ? 0xFF : (c & 0xFF);
                    ni.setPixelRGBA(xx, yy, (a << 24) | (b << 16) | (gg << 8) | r);
                }
            }
            net.minecraft.client.renderer.texture.DynamicTexture dyn =
                    new net.minecraft.client.renderer.texture.DynamicTexture(ni);
            // 已经烘成目标尺寸，采样必须最近邻，否则又被插值糊一层
            dyn.setFilter(false, false);
            return Minecraft.getInstance().getTextureManager()
                    .register("gtl_icon_" + (seq++), dyn);
        } catch (Throwable t) {
            if (!bakeWarned) {
                bakeWarned = true;
                GTLModular.LOGGER.warn("[TerminalIcons] 图标烘焙失败，跳过绘制: {}", t.toString());
            }
            return null;
        }
    }

    private static java.awt.image.BufferedImage source(int id) throws java.io.IOException {
        if (SRCIMG.containsKey(id)) return SRCIMG.get(id);
        ResourceLocation rl = new ResourceLocation(GTLModular.MOD_ID,
                String.format("textures/gui/terminal/icons/%04d.png", id));
        var res = Minecraft.getInstance().getResourceManager().getResource(rl);
        if (res.isEmpty()) return null;
        try (var in = res.get().open()) {
            java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(in);
            if (img != null) SRCIMG.put(id, img);
            return img;
        }
    }
}
