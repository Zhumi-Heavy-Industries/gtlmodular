package com.gtlmodular.client.gui.terminal.print;

import net.minecraft.client.gui.GuiGraphics;

/**
 * 页面过场（逐条移植网页 v32 的 {@code go(n)} + {@code burst(kind)}）。
 *
 * <p>网页端共四层，全部按原 CSS 时长/延迟/透明度复刻：</p>
 * <ol>
 *   <li><b>内容进场</b> {@code .entering .sheet>*} → {@code fx-in .55s cubic-bezier(.2,.8,.3,1)}：
 *       从下方 5px 淡入，同一页的元素按 index 错峰 {@code min(i*0.012, 0.14)}s。</li>
 *   <li><b>光幕</b> {@code .fx-veil}：{@code linear-gradient(100deg, transparent, rgba(58,91,194,.10), transparent)}，
 *       透明度 0→<b>.12</b>→0，{@code .75s ease-in-out}。
 *       注意网页 v32 的原话：「去掉原来 opacity .92 的全屏遮罩（那是"瞬间黑屏"真凶）」——
 *       Java 之前那层纸白 alpha 150 的遮罩正是它，必须换掉。</li>
 *   <li><b>构造标记三层闪切</b> {@code .fx-frame} ×3：圆环/连线/编号/衬线 N，
 *       {@code .9s ease}，延迟 {@code .04 / .18 / .32}s，峰值透明度 <b>.28</b>（20% 处），55% 回 0。</li>
 *   <li><b>三种变体</b> {@code kinds = [scan, mosaic, wipe]}，按目标页 {@code kinds[n % 3]} 选：
 *       <ul>
 *         <li>{@code scan}：6px 钴蓝光带自上而下扫过，{@code .85s}；</li>
 *         <li>{@code mosaic}：12 个彩色方块闪切，{@code .8s}，峰值 .35；</li>
 *         <li>{@code wipe}：115° 对角光擦平移 -60%→+60%，{@code .9s}，透明度 .22。</li>
 *       </ul></li>
 * </ol>
 *
 * <p>忙碌窗口 {@code .82s}（网页 {@code setTimeout(...,820)} 清 fx、解 busy）。</p>
 */
public final class TerminalTransition {

    /** {@code .sw-slot{transition:opacity .5s cubic-bezier(.4,0,.2,1)}} —— 交叉淡化时长。 */
    public static final float CROSSFADE_SEC = 0.5f;

    /**
     * 旧页 / 新页的 slot 透明度（网页 {@code .active}/{@code .leaving} 两个 class 的 opacity）。
     *
     * <p>实测验证（headless 逐帧采样 0/60/120/180/240/300/400ms）：
     * <pre>
     *  t(ms)   0    60   120   180   240   300   400
     *  旧页   1.00 0.96 0.79  0.48  0.25  0.12  0.04
     *  新页   0.00 0.04 0.21  0.52  0.75  0.88  0.96
     * </pre>
     * 与 {@code cubic-bezier(.4,0,.2,1)} 在有 .5s 内的输出逐点吻合（误差 ≤0.01）。</p>
     *
     * @return 新页的不透明度 0..1（旧页 = 1 - 该值）
     */
    public float crossfade(float seconds) {
        float age = seconds - startSec;
        if (age <= 0f) return 0f;
        if (age >= CROSSFADE_SEC) return 1f;
        return easeSlot(age / CROSSFADE_SEC);
    }

    /** {@code cubic-bezier(.4,0,.2,1)}：网页 .sw-slot 的缓动。 */
    private static float easeSlot(float t) {
        return cubicBezier(t, 0.4f, 0.0f, 0.2f, 1.0f);
    }

    /** 整个过场窗口（网页 820ms 后清空 fx 并解除 busy）。 */
    public static final float BUSY_SEC = 0.82f;

    private static final float FX_IN_SEC = 0.55f;
    /** 内容错峰：网页按 .sheet 子元素 index 递增，上限 140ms。 */
    private static final float STAGGER_STEP = 0.012f;
    private static final float STAGGER_MAX = 0.14f;

    private static final float VEIL_SEC = 0.75f;
    private static final float VEIL_PEAK = 0.12f;

    private static final float FRAME_SEC = 0.90f;
    private static final float FRAME_PEAK = 0.28f;
    private static final float[] FRAME_DELAY = {0.04f, 0.18f, 0.32f};

    private static final float BLOCK_SEC = 0.80f;
    private static final float BLOCK_PEAK = 0.35f;

    private static final float SCAN_SEC = 0.85f;
    private static final float WIPE_SEC = 0.90f;

    /** 网页 kinds 数组顺序：scan / mosaic / wipe。 */
    public enum Kind { SCAN, MOSAIC, WIPE }

    private Kind kind = Kind.SCAN;
    private float startSec = -100f;

    private static final int INK_FRAME = 0x2A3034;

    /** 切页时调用一次；{@code targetPage} 是目标页序号（0/1/2），{@code fromPage} 是离开的页。 */
    public void begin(float seconds, int targetPage, int fromPage) {
        this.startSec = seconds;
        this.prevPage = fromPage;
        Kind[] all = Kind.values();
        this.kind = all[Math.floorMod(targetPage, all.length)];
    }

    /** 是否仍在过场窗口内。 */
    public boolean active(float seconds) {
        return seconds - startSec < BUSY_SEC;
    }

    /** 交叉淡化是否仍在进行（决定是否要额外画旧页）。 */
    public boolean crossfading(float seconds) {
        float age = seconds - startSec;
        return age > 0f && age < CROSSFADE_SEC;
    }

    /** 上一页（交叉淡化时要把它也画出来）；-1 表示没有。 */
    private int prevPage = -1;

    public int previousPage() {
        return prevPage;
    }

    // ────────────────────────────── 内容进场

    /**
     * 内容块的纵向位移（网页 {@code fx-in} 的 {@code translateY(5px)}）。
     *
     * @param itemIndex 同一页内的元素序号，用来错峰（网页 min(i*0.012, 0.14)）
     */
    public float shiftY(float seconds, int itemIndex) {
        float delay = Math.min(itemIndex * STAGGER_STEP, STAGGER_MAX);
        float p = progress(seconds, delay, FX_IN_SEC);
        return (1f - easeFxIn(p)) * 5f;
    }

    /** 内容整体的"未进场"程度：0=完全进场，1=刚落墨在纸面上（用于纸色洗白，模拟 opacity 0→1）。 */
    public float wash(float seconds, int itemIndex) {
        float delay = Math.min(itemIndex * STAGGER_STEP, STAGGER_MAX);
        float p = progress(seconds, delay, FX_IN_SEC);
        return 1f - easeFxIn(p);
    }

    // ────────────────────────────── 叠加层

    /** 在内容之后调用：光幕 + 构造标记 + 变体。 */
    public void draw(GuiGraphics g, float seconds) {
        float age = seconds - startSec;
        if (age < 0f || age > BUSY_SEC) return;
        drawVeil(g, age);
        for (int f = 0; f < 3; f++) {
            drawFrame(g, age, f);
        }
        switch (kind) {
            case SCAN -> drawScan(g, age);
            case MOSAIC -> drawMosaic(g, age);
            case WIPE -> drawWipe(g, age);
        }
    }

    /** {@code .fx-veil}：106° 钴蓝薄光幕，峰值只有 12%。 */
    private void drawVeil(GuiGraphics g, float age) {
        if (age > VEIL_SEC) return;
        float u = age / VEIL_SEC;
        // fx-veil: 0%→0, 45%→.12, 100%→0（ease-in-out 近似为两段线性三角）
        float tri = u < 0.45f ? u / 0.45f : Math.max(0f, (1f - u) / 0.55f);
        float peak = VEIL_PEAK * tri;
        if (peak <= 0.001f) return;
        int strips = 40;
        float w = TerminalTheme.W / (float) strips;
        for (int i = 0; i < strips; i++) {
            float x = i * w;
            // 剖面：以 48% 为中心的高斯样包络，模拟 100deg 线性渐变
            float c = (i + 0.5f) / strips;
            float d = Math.abs(c - 0.48f) / 0.52f;
            float k = (float) Math.pow(Math.max(0f, 1f - d), 1.6f);
            if (k <= 0.01f) continue;
            int a = Math.round(255f * peak * k);
            if (a <= 0) continue;
            TerminalDraw.rect(g, x, 0, w + 0.6f, TerminalTheme.H, (a << 24) | 0x3A5BC2);
        }
    }

    /**
     * {@code .fx-frame[--f:0..2]}：网页 {@code frame(f)} 的确定性图形。
     * 5 个圆环 + 4 条引线 + 一个钴蓝编号点 + 一个大写衬线 N。
     */
    private void drawFrame(GuiGraphics g, float age, int f) {
        float a0 = progress(age, FRAME_DELAY[Math.min(f, 2)], FRAME_SEC);
        if (a0 <= 0f || a0 >= 1f) return;
        // fx-frame: 0%→0, 20%→.28, 55%→0
        float alpha = a0 < 0.20f
                ? FRAME_PEAK * (a0 / 0.20f)
                : FRAME_PEAK * Math.max(0f, (0.55f - a0) / 0.35f);
        if (alpha <= 0.002f) return;

        int seed = (f * 37 + 11) % 97;
        float sxK = TerminalTheme.W / 100f;
        float syK = TerminalTheme.H / 100f;

        // 网页：stroke-width .22（100 单位 viewBox）→ 560 设计宽下约 1.2px；
        // 但它还乘 stroke-opacity .5 与父层 opacity .28，最终只有 ~0.14，属于"几乎看不见的细线"。
        // Java 之前用 0.9f 线宽 + 满 alpha，比网页醒目得多，这里按原值调细。
        float strokeW = 0.22f * sxK;
        for (int i = 0; i < 5; i++) {
            float x = 8 + (seed * (i + 3)) % 84;
            float y = 10 + (seed * (i + 7)) % 78;
            float r = 1.5f + (seed * (i + 2)) % 6;
            TerminalDraw.ring(g, x * sxK, y * syK, r * sxK, Math.max(0.5f, strokeW),
                    withAlpha(INK_FRAME, alpha * 0.5f));
            if (i > 0) {
                TerminalDraw.line(g, (x - 6) * sxK, y * syK, (x + 8) * sxK, (y - 3) * syK,
                        Math.max(0.5f, 0.16f * sxK), withAlpha(INK_FRAME, alpha * 0.36f));
            }
        }
        // 编号点 20,24 + 白色 "1"
        TerminalDraw.fillCircle(g, 20 * sxK, 24 * syK, 1.4f * sxK, withAlpha(0x3A5BC2, alpha));
        TerminalFonts.draw(g, TerminalTheme.Fam.MONO, 2f * syK, "1",
                22 * sxK, 23 * syK, withAlpha(0xEFF1EF, alpha));

        // 大号衬线 N（Georgia italic 风格用 SERIF_IT 近似）
        float nx = 60 + (seed % 14);
        float ny = 30 + (seed % 40);
        float fs = (7 + (seed % 9)) * syK;
        TerminalFonts.draw(g, TerminalTheme.Fam.SERIF, fs, "N",
                nx * sxK, ny * syK - fs * 0.78f, withAlpha(INK_FRAME, alpha * 0.62f));
    }

    /** {@code fx-scan}：6px 光带自上而下；6/560 高 → 设计像素约 3.6，这里取 4。 */
    private void drawScan(GuiGraphics g, float age) {
        if (age > SCAN_SEC) return;
        float u = age / SCAN_SEC;
        float bandH = 4f;
        float top = u * (TerminalTheme.H - bandH);
        // fx-scanband: 0%{top:0;opacity:0} 20%{1} 80%{1} 100%{top:100%;opacity:0}
        float a = u < 0.2f ? u / 0.2f : (u > 0.8f ? Math.max(0f, (1f - u) / 0.2f) : 1f);
        if (a <= 0.01f) return;
        // 横向渐变：中间亮、两端透明
        int strips = 28;
        float w = TerminalTheme.W / (float) strips;
        for (int i = 0; i < strips; i++) {
            float c = (i + 0.5f) / strips;
            float k = (float) Math.pow(Math.max(0f, 1f - Math.abs(c - 0.5f) * 2f), 1.2f);
            int alphaA = Math.round(255f * 0.5f * a * k);
            if (alphaA <= 0) continue;
            TerminalDraw.rect(g, i * w, top, w + 0.6f, bandH, (alphaA << 24) | 0x3A5BC2);
        }
    }

    /** {@code fx-block} ×12：位置/尺寸/颜色全部按网页公式。 */
    private void drawMosaic(GuiGraphics g, float age) {
        if (age > BLOCK_SEC) return;
        float u = age / BLOCK_SEC;
        // fx-block: 0%→0, 20%→.35, 60%→0
        float a = u < 0.2f ? BLOCK_PEAK * (u / 0.2f) : BLOCK_PEAK * Math.max(0f, (0.6f - u) / 0.4f);
        if (a <= 0.002f) return;
        // 网页 stage 900 宽，方块尺寸以 px 计；换算到 560 设计宽
        float k = TerminalTheme.W / 900f;
        int[] palette = {0x1F2427, 0x3A5BC2, 0xB43A34, 0xC9CFCC};
        for (int j = 0; j < 12; j++) {
            float s = 12 + (j * 7) % 34;
            float l = 20 + (j * 29) % 72;
            float t = 10 + (j * 17) % 80;
            float x = l / 100f * TerminalTheme.W;
            float y = t / 100f * TerminalTheme.H;
            TerminalDraw.rect(g, x, y, s * k, s * 0.5f * k,
                    withAlpha(palette[j % 4], a));
        }
    }

    /** {@code fx-wipe}：115° 对角光擦，从 -60% 平移到 +60%。 */
    private void drawWipe(GuiGraphics g, float age) {
        if (age > WIPE_SEC) return;
        float u = age / WIPE_SEC;
        float pos = -0.6f + 1.2f * u;          // translateX(-60%) → 60%
        float centerX = pos * TerminalTheme.W;
        float bandW = TerminalTheme.W * 0.26f; // 近似渐变 42%..58% 的可见宽度
        int strips = 24;
        float w = bandW / strips;
        for (int i = 0; i < strips; i++) {
            float c = (i + 0.5f) / strips;                 // 0..1 across band
            float k = (float) Math.pow(Math.max(0f, 1f - Math.abs(c - 0.5f) * 2f), 1.1f);
            int a = Math.round(255f * 0.22f * k);
            if (a <= 0) continue;
            float x = centerX - bandW / 2f + i * w;
            // 对角：用倾斜的梯形近似（把每一竖条整体下移，形成约 25° 斜边）
            float slant = (x - centerX) * 0.45f;
            TerminalDraw.rect(g, x, slant - TerminalTheme.H * 0.3f,
                    w + 0.7f, TerminalTheme.H * 1.6f, (a << 24) | 0x3A5BC2);
        }
    }

    // ────────────────────────────── 工具

    private static float progress(float seconds, float delay, float duration) {
        float t = (seconds - delay) / duration;
        return t <= 0f ? 0f : (t >= 1f ? 1f : t);
    }

    /** {@code cubic-bezier(.2,.8,.3,1)}：网页 fx-in 的缓动。 */
    private static float easeFxIn(float t) {
        return cubicBezier(t, 0.2f, 0.8f, 0.3f, 1.0f);
    }

    /** 解 x(s)=t 后取 y(s)，s 用二分（次数很少，开销可忽略）。 */
    private static float cubicBezier(float t, float x1, float y1, float x2, float y2) {
        if (t <= 0f) return 0f;
        if (t >= 1f) return 1f;
        float lo = 0f, hi = 1f, s = t;
        for (int i = 0; i < 20; i++) {
            s = (lo + hi) * 0.5f;
            float x = bez(s, x1, x2);
            if (x < t) lo = s; else hi = s;
        }
        return bez(s, y1, y2);
    }

    private static float bez(float s, float p1, float p2) {
        float v = 1f - s;
        return 3f * v * v * s * p1 + 3f * v * s * s * p2 + s * s * s;
    }

    private static int withAlpha(int rgb, float alpha) {
        int a = Math.round(255f * Math.max(0f, Math.min(1f, alpha)));
        return (a << 24) | (rgb & 0xFFFFFF);
    }
}
