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

    private static void rawDraw(GuiGraphics g, Font font, String text, float x, float y, int color) {
        if (text.isEmpty()) return;
        // 某些 GUI（ModernUI 的模糊/背景层等）会留下非白的 shaderColor，文字着色器会把它当
        // ColorModulator 乘进顶点色 —— 白色字会变成灰黑、INK 变纯黑。画字前强制复位。
        float[] sc = com.mojang.blaze3d.systems.RenderSystem.getShaderColor();
        if (sc[0] < 0.99f || sc[1] < 0.99f || sc[2] < 0.99f || sc[3] < 0.99f) {
            if (!colorWarned) {
                colorWarned = true;
                GTLModular.LOGGER.warn("[TerminalFonts] 检测到残留 shaderColor={}, {}, {}, {}，已复位为白",
                        sc[0], sc[1], sc[2], sc[3]);
            }
            com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        }
        if (RENDER_TEXT != null) {
            try {
                RENDER_TEXT.invoke(font, text, x, y, color, false,
                        g.pose().last().pose(), g.bufferSource(), Font.DisplayMode.NORMAL, 0xF000F0, 0);
                return;
            } catch (Throwable ignored) {
                // 落到原版 API
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
            w += advance(font, new String(Character.toChars(cp))) + tracking;
            i += n;
        }
        return w - tracking;
    }

    /** 普通绘制；{@code y} 就是设计稿里的文字 top。 */
    public static void draw(GuiGraphics g, Fam fam, float size, String text, float x, float y, int color) {
        Font font = of(fam, size);
        if (font == null) return;
        rawDraw(g, font, text, x, y, color);
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
            rawDraw(g, font, ch, cx, y, color);
            cx += advanceOf(font, cp) + tracking;
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
            rawDraw(g, font, ch, x, cy, color);
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
            if (cjk) {
                if (cn == null) cn = of(Fam.CN, size);
                if (cn != null) f = cn;
            }
            float w = 0f;
            for (int k = i; k < j; ) {
                int cp = text.codePointAt(k);
                w += advanceOf(f, cp);
                k += Character.charCount(cp);
            }
            rawDraw(g, f, run.toString(), cx, y, color);
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
            w += advanceOf(f, cp) + tracking;
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
