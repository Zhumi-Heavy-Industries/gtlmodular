package com.gtlmodular.client.gui.terminal.print;

import java.io.File;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import org.lwjgl.stb.STBTTFontinfo;
import org.lwjgl.stb.STBTruetype;

import com.gtlmodular.GTLModular;
import com.gtlmodular.client.gui.terminal.print.TerminalTheme.Fam;
import com.mojang.blaze3d.font.TrueTypeGlyphProvider;
import com.mojang.blaze3d.platform.TextureUtil;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.resources.ResourceLocation;

/**
 * 终端字体管线：把 TTF 直接烘成 {@link FontSet}（不经过资源包 font json），按「字体族 + 设计字号」缓存。
 *
 * <p>设计稿用的是浏览器里的一套字：中文落到 STSong（华文宋体）、拉丁衬线是 Georgia、等宽是 Consolas。
 * Minecraft 没有字号参数，所以每个设计字号单独烘一个 FontSet —— 这也是 5/6px 微标能清晰的前提。
 *
 * <p>字体来源优先级：本机 Windows 字体（与设计稿像素级同源）→ 模组内置 OFL 子集 → 原版字体兜底。
 */
public final class TerminalFonts {

    private TerminalFonts() {}

    private static final Map<Long, Font> CACHE = new HashMap<>();
    private static final Map<String, java.nio.ByteBuffer> TTF = new HashMap<>();
    private static final Map<String, FontSet> SETS = new HashMap<>();
    private static final Map<Font, FontSet> FONT_SETS = new java.util.IdentityHashMap<>();
    private static boolean logged;

    // ── AWT 直绘：MC 的 TTF 图集是单通道，某些渲染路径会把字形当黑色纹理，
    //    导致白字/钴蓝字全变黑。这里直接用系统 TTF 烘成 ARGB 小贴图再 blit，
    //    颜色、字形都与设计稿一致；按 (族,字号,颜色,文本) 缓存，LRU 512。
    private record Key(Fam fam, int sizeQ, int color, String text) {}
    private static final class Tex {
        final ResourceLocation rl; final int w, h, tw, th;
        Tex(ResourceLocation rl, int w, int h, int tw, int th) { this.rl = rl; this.w = w; this.h = h; this.tw = tw; this.th = th; }
    }
    private static final Map<Key, Tex> TEX = new java.util.LinkedHashMap<>(128, 0.75f, true);
    private static final Map<Fam, java.awt.Font> AWTF = new java.util.EnumMap<>(Fam.class);
    private static int texSeq;
    private static java.awt.image.BufferedImage probeImg;
    private static java.awt.Graphics2D probeG;
    private record WKey(Fam fam, int px, String text) {}
    private static final Map<WKey, Float> AW = new java.util.LinkedHashMap<>(256, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<WKey, Float> eldest) {
            return size() > 4096;
        }
    };

    private static java.awt.Font awtFont(Fam fam, int px) {
        java.awt.Font base = AWTF.get(fam);
        if (base == null) {
            String file = pickFile(fam);
            if (file == null) return null;
            try {
                base = java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, new java.io.File(file));
            } catch (Throwable t) {
                return null;
            }
            AWTF.put(fam, base);
        }
        return base.deriveFont((float) px);
    }

    /** AWT 实际字宽（设计像素）；AWT 不可用时返回 -1。排版必须与绘制同源。 */
    private static float awtWidth(Fam fam, float size, String text) {
        if (text == null || text.isEmpty()) return 0f;
        float gs = Math.max(1f, Math.min(4f, (float) Minecraft.getInstance().getWindow().getGuiScale()));
        int px = Math.max(2, Math.round(size * gs));
        WKey wkey = new WKey(fam, px, text);
        Float cached = AW.get(wkey);
        if (cached != null) return cached;
        java.awt.Font af = awtFont(fam, px);
        if (af == null) return -1f;
        try {
            if (probeG == null) {
                probeImg = new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                probeG = probeImg.createGraphics();
            }
            probeG.setFont(af);
            float w = probeG.getFontMetrics().stringWidth(text) / gs;
            AW.put(wkey, w);
            return w;
        } catch (Throwable t) {
            return -1f;
        }
    }

    // 绕过 ModernUI：它的 mixin 接管了 Font.drawInBatch / StringSplitter。
    // 这里直接反射调用原版私有的 renderText 与包私有的 getFontSet，用我们自己的 FontSet 绘制/量宽。
    private static java.lang.reflect.Method RENDER_TEXT;
    private static java.lang.reflect.Method GET_FONT_SET;

    static {
        for (java.lang.reflect.Method m : Font.class.getDeclaredMethods()) {
            Class<?>[] p = m.getParameterTypes();
            if (m.getReturnType() == float.class && p.length == 10
                    && p[0] == String.class && p[5] == org.joml.Matrix4f.class
                    && p[6] == net.minecraft.client.renderer.MultiBufferSource.class
                    && p[7] == Font.DisplayMode.class) {
                try {
                    m.setAccessible(true);
                    RENDER_TEXT = m;
                } catch (Throwable ignored) {
                }
            }
            if (p.length == 1 && p[0] == ResourceLocation.class
                    && m.getReturnType() == FontSet.class) {
                try {
                    m.setAccessible(true);
                    GET_FONT_SET = m;
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static boolean colorWarned;
    private static boolean awtLogged;
    private static boolean awtFailed;

    private static void rawDraw(GuiGraphics g, Fam fam, float size, String text, float x, float y, int color) {
        if (text.isEmpty()) return;
        // 主墨色 0x1F2427：MC 字体本来就渲染成近黑，直接用，省掉大量 AWT 小贴图
        if ((color & 0xFFFFFF) == 0x1F2427) {
            rawDrawMc(g, of(fam, size), text, x, y, color);
            return;
        }
        if (drawAwt(g, fam, size, text, x, y, color)) return;
        // 兜底：走原版 Font（颜色可能仍偏黑，但至少不丢字）
        rawDrawMc(g, of(fam, size), text, x, y, color);
    }

    /** AWT 路径是否已经失败（用于黑底按钮的兜底配色）。 */
    public static boolean awtFailed() {
        return awtFailed;
    }

    /** 用 AWT 烘一张小贴图再 blit，彻底绕开 MC 图集的颜色问题。 */
    private static boolean drawAwt(GuiGraphics g, Fam fam, float size, String text, float x, float y, int color) {
        try {
            float gs = Math.max(1f, Math.min(4f, (float) Minecraft.getInstance().getWindow().getGuiScale()));
            int px = Math.max(2, Math.round(size * gs));
            Key key = new Key(fam, px, color, text);
            Tex tex = TEX.get(key);
            if (tex == null) {
                java.awt.Font base = AWTF.get(fam);
                if (base == null) {
                    String file = pickFile(fam);
                    if (file == null) {
                        return false; // 该字族缺文件时只回退该族，不影响其它文字
                    }
                    base = java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, new java.io.File(file));
                    AWTF.put(fam, base);
                }
                java.awt.Font af = base.deriveFont((float) px);
                java.awt.image.BufferedImage probe = new java.awt.image.BufferedImage(1, 1,
                        java.awt.image.BufferedImage.TYPE_INT_ARGB);
                java.awt.Graphics2D pg = probe.createGraphics();
                pg.setFont(af);
                java.awt.FontMetrics fm = pg.getFontMetrics();
                int tw = Math.max(1, fm.stringWidth(text) + 2);
                int th = Math.max(1, fm.getHeight());
                pg.dispose();

                java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(tw, th,
                        java.awt.image.BufferedImage.TYPE_INT_ARGB);
                java.awt.Graphics2D g2 = img.createGraphics();
                g2.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING,
                        java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setRenderingHint(java.awt.RenderingHints.KEY_FRACTIONALMETRICS,
                        java.awt.RenderingHints.VALUE_FRACTIONALMETRICS_ON);
                g2.setFont(af);
                g2.setColor(new java.awt.Color(color, true));
                g2.drawString(text, 1, Math.round(fm.getAscent()));
                g2.dispose();

                com.mojang.blaze3d.platform.NativeImage ni =
                        new com.mojang.blaze3d.platform.NativeImage(
                                com.mojang.blaze3d.platform.NativeImage.Format.RGBA, tw, th, false);
                for (int yy = 0; yy < th; yy++) {
                    for (int xx = 0; xx < tw; xx++) {
                        int argb = img.getRGB(xx, yy);
                        int a = (argb >>> 24) & 0xFF;
                        int r = (argb >>> 16) & 0xFF;
                        int gg = (argb >>> 8) & 0xFF;
                        int b = argb & 0xFF;
                        ni.setPixelRGBA(xx, yy, (a << 24) | (b << 16) | (gg << 8) | r);
                    }
                }
                net.minecraft.client.renderer.texture.DynamicTexture dyn =
                        new net.minecraft.client.renderer.texture.DynamicTexture(ni);
                ResourceLocation rl = Minecraft.getInstance().getTextureManager()
                        .register("gtl_term_" + (texSeq++), dyn);
                tex = new Tex(rl, Math.max(1, Math.round(tw / gs)), Math.max(1, Math.round(th / gs)), tw, th);
                if (TEX.size() > 512) {
                    var it = TEX.entrySet().iterator();
                    if (it.hasNext()) {
                        Tex old = it.next().getValue();
                        it.remove();
                        Minecraft.getInstance().getTextureManager().release(old.rl);
                    }
                }
                TEX.put(key, tex);
                if (!awtLogged) {
                    awtLogged = true;
                    GTLModular.LOGGER.info("[TerminalFonts] AWT 文字就绪：{} {}px texture {}x{}", fam, px, tw, th);
                }
            }
            int dx = Math.round(x), dy = Math.round(y);
            g.blit(tex.rl, dx, dy, tex.w, tex.h, 0f, 0f, tex.tw, tex.th, tex.tw, tex.th);
            return true;
        } catch (Throwable t) {
            awtFailed = true;
            if (!colorWarned) {
                colorWarned = true;
                GTLModular.LOGGER.warn("[TerminalFonts] AWT 文字失败，回退 MC 字体: {}", t.toString());
            }
            return false;
        }
    }

    /** 原反射路径，仅在 AWT 不可用时兜底。 */
    private static void rawDrawMc(GuiGraphics g, Font font, String text, float x, float y, int color) {
        if (text.isEmpty()) return;
        float[] sc = com.mojang.blaze3d.systems.RenderSystem.getShaderColor();
        if (sc[0] < 0.99f || sc[1] < 0.99f || sc[2] < 0.99f || sc[3] < 0.99f) {
            com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        }
        if (RENDER_TEXT != null) {
            try {
                RENDER_TEXT.invoke(font, text, x, y, color, false,
                        g.pose().last().pose(), g.bufferSource(), Font.DisplayMode.NORMAL, 0xF000F0, 0);
                return;
            } catch (Throwable ignored) {
            }
        }
        g.drawString(font, text, x, y, color, false);
    }

    private static FontSet setOf(Font font) {
        if (font == null) return null;
        FontSet cached = FONT_SETS.get(font);
        if (cached != null) return cached;
        if (GET_FONT_SET != null) {
            try {
                Object set = GET_FONT_SET.invoke(font, new ResourceLocation("minecraft", "default"));
                if (set instanceof FontSet fs) {
                    FONT_SETS.put(font, fs);
                    return fs;
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    /** 字宽：优先走自己 FontSet 的字形 advance，绕开 ModernUI 的 StringSplitter。 */
    private static float advance(Font font, String text) {
        FontSet set = setOf(font);
        if (set != null) {
            float w = 0;
            int i = 0;
            while (i < text.length()) {
                int cp = text.codePointAt(i);
                var info = set.getGlyphInfo(cp, false);
                if (info != null) w += info.getAdvance();
                i += Character.charCount(cp);
            }
            return w;
        }
        return font.width(text);
    }

    /** 设计稿字号 → STBTT 字号（1.0 = 1 设计像素）。 */
    private static long cacheKey(Fam fam, float size) {
        return ((long) fam.ordinal() << 32) | (Float.floatToIntBits(size) & 0xFFFFFFFFL);
    }

    public static Font of(Fam fam, float size) {
        long key = cacheKey(fam, size);
        Font cached = CACHE.get(key);
        if (cached != null) return cached;
        Font font = load(fam, size, fam.name() + "|" + size);
        CACHE.put(key, font);
        return font;
    }

    /** 单码点 advance（不创建字符串，绕开 ModernUI 的 StringSplitter）。 */
    private static float advanceOf(Font font, int cp) {
        FontSet set = setOf(font);
        if (set != null) {
            var info = set.getGlyphInfo(cp, false);
            return info == null ? 0f : info.getAdvance();
        }
        return font.width(new String(Character.toChars(cp)));
    }

    private static Font load(Fam fam, float size, String key) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getTextureManager() == null) return null;
        Font fallback = mc.font;
        try {
            String file = pickFile(fam);
            if (file == null) {
                warnOnce("找不到 " + fam + " 字体文件，回退原版字体");
                return fallback;
            }
            ByteBuffer data = TTF.get(file);
            if (data == null) {
                data = read(file);
                if (data != null) TTF.put(file, data);
            }
            if (data == null) {
                warnOnce("读取 " + file + " 失败，回退原版字体");
                return fallback;
            }
            STBTTFontinfo info = STBTTFontinfo.malloc();
            if (!STBTruetype.stbtt_InitFont(info, data)) {
                warnOnce(file + " 不是有效 TTF，回退原版字体");
                return fallback;
            }
            float gs = (float) Minecraft.getInstance().getWindow().getGuiScale();
            float oversample = Math.max(1f, Math.min(4f, gs));
            TrueTypeGlyphProvider provider =
                    new TrueTypeGlyphProvider(data, info, size, oversample, 0f, 0f, "");
            ResourceLocation id = new ResourceLocation(GTLModular.MOD_ID,
                    "terminal/" + fam.name().toLowerCase() + "_" + key.replace('.', '_').replace('|', '_'));
            FontSet set = new FontSet(mc.getTextureManager(), id);
            set.reload(java.util.List.of(provider));
            SETS.put(key, set);
            Font font = new Font(name -> set, false);
            return font;
        } catch (Throwable t) {
            warnOnce("字体烘焙失败（" + fam + "@" + size + "）：" + t);
            return fallback;
        }
    }

    private static void warnOnce(String message) {
        if (logged) return;
        logged = true;
        org.slf4j.LoggerFactory.getLogger("gtlmodular-terminal-ui").warn("[哦鲸鲸终端] {}", message);
    }

    /** 本机字体优先；没有就用模组内置字体。 */
    private static String pickFile(Fam fam) {
        String[] candidates = switch (fam) {
            case CN -> TerminalTheme.CN_FONTS;
            case SERIF -> TerminalTheme.SERIF_FONTS;
            case SERIF_IT -> TerminalTheme.SERIF_IT_FONTS;
            case MONO -> TerminalTheme.MONO_FONTS;
        };
        for (String path : candidates) {
            if (!new File(path).isFile()) continue;
            // TTC（simsun.ttc）STBTT 不认，跳过
            if (path.toLowerCase().endsWith(".ttc")) continue;
            return path;
        }
        return null;
    }

    private static ByteBuffer read(String path) {
        try (InputStream in = new java.io.FileInputStream(path)) {
            ByteBuffer buf = TextureUtil.readResource(in);
            buf.flip();
            return buf;
        } catch (Exception e) {
            return null;
        }
    }

    // ────────────────────────────── 文本绘制

    /** 墨色文字走 MC 字体：省掉大量 AWT 小贴图（环上铭文每帧上百个字），颜色本来就是深墨。 */
    public static void drawInkMc(GuiGraphics g, Fam fam, float size, String text,
                                 float x, float y, int color) {
        Font font = of(fam, size);
        if (font != null) rawDrawMc(g, font, text, x, y, color);
    }

    public static float width(Fam fam, float size, String text) {
        Font font = of(fam, size);
        return font == null ? 0f : advance(font, text);
    }

    public static float trackedWidth(Fam fam, float size, String text, float tracking) {
        Font font = of(fam, size);
        if (font == null) return 0f;
        float w = 0f;
        int i = 0;
        while (i < text.length()) {
            int cp = text.codePointAt(i);
            int n = Character.charCount(cp);
            String one = new String(Character.toChars(cp));
            float aw = awtWidth(fam, size, one);
            w += (aw >= 0 ? aw : advance(font, one)) + tracking;
            i += n;
        }
        return w - tracking;
    }

    /** 普通绘制；{@code y} 就是设计稿里的文字 top。 */
    public static void draw(GuiGraphics g, Fam fam, float size, String text, float x, float y, int color) {
        Font font = of(fam, size);
        if (font == null) return;
        rawDraw(g, fam, size, text, x, y, color);
    }

    /** 带字距绘制（设计稿 63 处 letter-spacing）。 */
    public static void drawTracked(GuiGraphics g, Fam fam, float size, String text,
                                   float x, float y, float tracking, int color) {
        Font font = of(fam, size);
        if (font == null) return;
        float cx = x;
        int i = 0;
        while (i < text.length()) {
            int cp = text.codePointAt(i);
            String ch = new String(Character.toChars(cp));
            rawDraw(g, fam, size, ch, cx, y, color);
            float aw = awtWidth(fam, size, ch);
            cx += (aw >= 0 ? aw : advanceOf(font, cp)) + tracking;
            i += Character.charCount(cp);
        }
    }

    /** 居中绘制（按实际字宽）。 */
    public static void drawCentered(GuiGraphics g, Fam fam, float size, String text,
                                    float centerX, float y, int color) {
        draw(g, fam, size, text, centerX - width(fam, size, text) / 2f, y, color);
    }

    /** 混排居中（按钮中文用）。 */
    public static void drawCenteredMixed(GuiGraphics g, Fam fam, float size, String text,
                                         float centerX, float y, int color) {
        drawMixed(g, fam, size, text, centerX - mixedWidth(fam, size, text, 0f) / 2f, y, 0f, color);
    }

    /** 混排右对齐。 */
    public static void drawRightMixed(GuiGraphics g, Fam fam, float size, String text,
                                      float rightX, float y, int color) {
        drawMixed(g, fam, size, text, rightX - mixedWidth(fam, size, text, 0f), y, 0f, color);
    }

    /** 右对齐绘制。 */
    public static void drawRight(GuiGraphics g, Fam fam, float size, String text,
                                 float rightX, float y, int color) {
        draw(g, fam, size, text, rightX - width(fam, size, text), y, color);
    }

    /** 竖排（writing-mode: vertical-rl），逐字下移。 */
    public static void drawVertical(GuiGraphics g, Fam fam, float size, String text,
                                    float x, float y, float tracking, int color) {
        Font font = of(fam, size);
        if (font == null) return;
        float cy = y;
        int i = 0;
        while (i < text.length()) {
            int cp = text.codePointAt(i);
            String ch = new String(Character.toChars(cp));
            rawDraw(g, fam, size, ch, x, cy, color);
            cy += advanceOf(font, cp) + tracking;
            i += Character.charCount(cp);
        }
    }

    /** 套印式加粗：同一行偏移 0.4px 再画一遍（设计稿的双打/600 字重的等价做法）。 */
    public static void drawHeavy(GuiGraphics g, Fam fam, float size, String text,
                                 float x, float y, int color) {
        draw(g, fam, size, text, x, y, color);
        draw(g, fam, size, text, x + 0.45f, y, color);
    }

    public static boolean isCjk(int cp) {
        return cp >= 0x2E80 && cp <= 0x9FFF
                || cp >= 0xF900 && cp <= 0xFAFF
                || cp >= 0xFF01 && cp <= 0xFF60
                || cp >= 0x3000 && cp <= 0x303F;
    }

    /**
     * 混排绘制：ASCII 用请求的字体族（等宽/衬线），CJK 用中文衬线。
     * 对应浏览器里 {@code --mono} 对中文的回退行为。
     */
    public static void drawMixed(GuiGraphics g, Fam fam, float size, String text,
                                 float x, float y, float tracking, int color) {
        if (fam == Fam.CN) {
            drawTracked(g, Fam.CN, size, text, x, y, tracking, color);
            return;
        }
        Font latin = of(fam, size);
        if (latin == null) return;
        Font cn = null;
        float cx = x;
        int i = 0;
        int n = text.length();
        while (i < n) {
            boolean cjk = isCjk(text.codePointAt(i));
            int j = i;
            StringBuilder run = new StringBuilder();
            int count = 0;
            while (j < n && isCjk(text.codePointAt(j)) == cjk) {
                int cp = text.codePointAt(j);
                run.appendCodePoint(cp);
                j += Character.charCount(cp);
                count++;
            }
            Font f = latin;
            Fam runFam = fam;
            if (cjk) {
                if (cn == null) cn = of(Fam.CN, size);
                if (cn != null) f = cn;
                runFam = Fam.CN;
            }
            String runText = run.toString();
            float aw = awtWidth(runFam, size, runText);
            float w = 0f;
            if (aw < 0) {
                for (int k = i; k < j; ) {
                    int cp = text.codePointAt(k);
                    w += advanceOf(f, cp);
                    k += Character.charCount(cp);
                }
            } else {
                w = aw;
            }
            rawDraw(g, runFam, size, runText, cx, y, color);
            cx += w + tracking * count;
            i = j;
        }
    }

    public static float mixedWidth(Fam fam, float size, String text, float tracking) {
        if (fam == Fam.CN) return trackedWidth(Fam.CN, size, text, tracking);
        Font latin = of(fam, size);
        if (latin == null) return 0f;
        float w = 0f;
        int i = 0;
        while (i < text.length()) {
            int cp = text.codePointAt(i);
            Font f = latin;
            if (isCjk(cp)) {
                Font cn = of(Fam.CN, size);
                if (cn != null) f = cn;
            }
            String one = new String(Character.toChars(cp));
            float aw = awtWidth(f == null ? fam : (isCjk(cp) ? Fam.CN : fam), size, one);
            w += (aw >= 0 ? aw : advanceOf(f, cp)) + tracking;
            i += Character.charCount(cp);
        }
        return w - tracking;
    }

    /** 混排 + 加粗（描两遍）。 */
    public static void drawMixedHeavy(GuiGraphics g, Fam fam, float size, String text,
                                      float x, float y, float tracking, int color) {
        drawMixed(g, fam, size, text, x, y, tracking, color);
        drawMixed(g, fam, size, text, x + 0.4f, y, tracking, color);
    }

    /** 按宽度截断（超出加 …），单次 advance 扫描，避免长材料名截断的 O(n²) 路径。 */
    public static String clip(Fam fam, float size, String text, float maxWidth) {
        if (text == null || text.isEmpty()) return "";
        Font latin = of(fam, size);
        if (latin == null) return text;
        Font cn = fam == Fam.CN ? latin : null;
        float ellipsisWidth = advanceOf(latin, '…');
        StringBuilder out = new StringBuilder(Math.min(text.length(), 64));
        float used = 0f;
        boolean clipped = false;
        for (int i = 0; i < text.length();) {
            int cp = text.codePointAt(i);
            Font f = latin;
            if (fam != Fam.CN && isCjk(cp)) {
                if (cn == null) cn = of(Fam.CN, size);
                if (cn != null) f = cn;
            }
            float advance = advanceOf(f, cp);
            if (used + advance + ellipsisWidth > maxWidth) {
                clipped = true;
                break;
            }
            out.appendCodePoint(cp);
            used += advance;
            i += Character.charCount(cp);
        }
        if (!clipped) return text;
        return out.append('…').toString();
    }
}
