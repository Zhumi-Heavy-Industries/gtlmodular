package com.gtlmodular.client.renderer.stellar;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.core.Direction;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix4f;

/**
 * 「恒星炎炀锻炉」漩涡的<b>纯几何</b>版本（2026-11-02 重做）。
 *
 * <h2>为什么要重做</h2>
 * <p>旧实现是 GTO 原版那套：{@code RegisterShadersEvent} 注册自定义 {@code ShaderInstance}
 * （{@code gtlmodular:stellar_forge_vortex}）+ 屏幕空间抓屏折射，挂在一个 {@code IRenderer} 的
 * {@code render()} 里。两个致命点（同一天另一条会话用 {@code javap} 查出来的，见
 * {@code _部署记录_20261102_0.3.299_奈亚不稳定特效移植.md} §1）：
 * <ol>
 *   <li>GTCEu 1.4.4 的机器方块实体<b>根本不会调用</b> {@code IRenderer#render(...)}（那条 TESR 通道
 *       在机器上没有调用点）⇒ 我们的 {@code StellarForgeRenderer#render} 一次都没跑过，特效自然永远不出现；</li>
 *   <li>本包带 <b>Oculus/Iris + 光影包</b>，自定义 shader / RenderType 在 Iris 管线里不保证生效
 *       （日志里一堆 {@code CustomUniforms} 报错）。</li>
 * </ol>
 * <p>所以照「奈亚不稳定特效」的做法重写：<b>ClientTick 自己扫控制器 + RenderLevelStage 里直接画</b>，
 * 全部用原版 {@code POSITION_COLOR} 着色器 + 加性 {@code GLOW} 层（{@code ShanhaiInstabilityRenderTypes.GLOW}），
 * <b>不注册任何 shader、不用任何贴图</b>。
 *
 * <h2>几何</h2>
 * <p>局部坐标（{@code x}=机器右侧、{@code y}=上、{@code z}=机器正面）里，漩涡中心取 GTO 的偏移
 * <b>上 31 格、向里 2 格</b>（{@code offU=31, offD=-2}），盘面垂直于正面向外。组成：
 * <ol>
 *   <li>核心辉光：14 圈同心环，越靠中心越亮（紫罗兰 → 白紫）；</li>
 *   <li>5 条螺旋臂：半径 4→31，随半径扭转、随时间旋转，颜色 紫 → 品红 → 青；</li>
 *   <li>2 道倾斜虚线环（±18°），给一点 3D 进深感；</li>
 *   <li>42 颗公转粒子 + 外圈脉冲虚线环（半径 31.5，随呼吸明暗）。</li>
 * </ol>
 *
 * <p>所有顶点只写 {@code POSITION_COLOR}；亮度由 {@code intensity} 统一乘（成型待机 0.45、运行中 1.0）。
 * 全部数值都有界，不产生 NaN/Inf。
 */
@OnlyIn(Dist.CLIENT)
public final class StellarForgeVortexGeometry {

    /** 漩涡中心相对控制器方块中心的上方偏移（GTO offU）。 */
    private static final float CENTER_UP = 31.0f;
    /** 漩涡中心相对控制器方块中心的正向偏移（GTO offD = -2：向机器内部 2 格）。 */
    private static final float CENTER_FORWARD = -2.0f;

    /** 盘面最大半径（GTO 特效球半径 48；环内半径 32，外圈稍探出环外）。 */
    private static final float MAX_RADIUS = 34.0f;

    /** 一条螺旋臂的采样段数。 */
    private static final int ARM_STEPS = 64;

    /** 螺旋臂数量。 */
    private static final int ARM_COUNT = 5;

    private StellarForgeVortexGeometry() {
    }

    /**
     * 往 {@code consumer} 里写一帧漩涡几何。
     *
     * @param consumer  已绑好 {@code GLOW} RenderType 的顶点消费者
     * @param pose      当前 PoseStack 的 {@code last().pose()}（已平移到控制器方块原点）
     * @param front     机器正面朝向（决定盘面朝向与旋转轴）
     * @param time      秒级时间（用于旋转/脉动；建议 {@code Util.getMillis()/1000.0}）
     * @param intensity 总亮度（0~1 以上，内部夹到 0~1）
     */
    public static void draw(VertexConsumer consumer, Matrix4f pose, Direction front,
                            float time, float intensity) {
        if (front == null || consumer == null || pose == null) {
            return;
        }
        float brightness = clamp01(intensity);
        if (brightness <= 0.001f) {
            return;
        }
        Direction right = front.getClockWise();
        Direction up = Direction.UP;

        drawCore(consumer, pose, right, up, front, time, brightness);
        drawArms(consumer, pose, right, up, front, time, brightness);
        drawTiltedRings(consumer, pose, right, up, front, time, brightness);
        drawParticles(consumer, pose, right, up, front, time, brightness);
        drawOuterPulse(consumer, pose, right, up, front, time, brightness);
    }

    // ------------------------------------------------------------------
    // ① 核心辉光
    // ------------------------------------------------------------------

    private static void drawCore(VertexConsumer vc, Matrix4f pose, Direction ax, Direction ay, Direction az,
                                 float time, float intensity) {
        int rings = 14;
        int segments = 36;
        float maxR = 9.6f;
        for (int i = 0; i < rings; i++) {
            float r0 = 0.55f + i * (maxR / rings);
            float r1 = r0 + maxR / rings;
            float a0 = coreAlpha(r0, maxR) * intensity;
            float a1 = coreAlpha(r1, maxR) * intensity;
            // 核心整体轻微脉动：0.85~1.15
            float pulse = 1.0f + 0.15f * (float) Math.sin(time * 2.3);
            a0 *= pulse;
            a1 *= pulse;
            for (int s = 0; s < segments; s++) {
                float t0 = (float) (s * Math.PI * 2.0 / segments);
                float t1 = (float) ((s + 1) * Math.PI * 2.0 / segments);
                float c0 = (float) Math.cos(t0), s0 = (float) Math.sin(t0);
                float c1 = (float) Math.cos(t1), s1 = (float) Math.sin(t1);
                // 内亮外暗：紫罗兰 → 白紫
                float k = 1.0f - r0 / maxR;
                float red = 0.55f + 0.45f * k;
                float green = 0.30f + 0.55f * k;
                float blue = 1.0f;
                quad(vc, pose, ax, ay, az,
                        r0 * c0, r1 * c0, r1 * c1, r0 * c1,
                        r0 * s0, r1 * s0, r1 * s1, r0 * s1,
                        a0, a1, a1, a0,
                        red, green, blue);
            }
        }
    }

    private static float coreAlpha(float r, float maxR) {
        float k = clamp01(1.0f - r / maxR);
        return 0.34f * k * k;
    }

    // ------------------------------------------------------------------
    // ② 螺旋臂
    // ------------------------------------------------------------------

    private static void drawArms(VertexConsumer vc, Matrix4f pose, Direction ax, Direction ay, Direction az,
                                 float time, float intensity) {
        float spin = time * 0.62f;
        for (int arm = 0; arm < ARM_COUNT; arm++) {
            float baseAngle = arm * (float) (Math.PI * 2.0 / ARM_COUNT) + spin;
            for (int step = 0; step < ARM_STEPS; step++) {
                float f0 = step / (float) ARM_STEPS;
                float f1 = (step + 1) / (float) ARM_STEPS;
                float r0 = 4.0f + f0 * (MAX_RADIUS - 4.0f);
                float r1 = 4.0f + f1 * (MAX_RADIUS - 4.0f);
                // 对数螺旋：角度随半径增加而扭转
                float a0 = baseAngle + r0 * 0.30f;
                float a1 = baseAngle + r1 * 0.30f;
                float halfWidth = 0.55f + 0.55f * f0;
                float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0);
                float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
                // 臂宽沿「切向垂直」方向铺开（在盘面内）
                float nx0 = -s0, ny0 = c0;
                float nx1 = -s1, ny1 = c1;
                float alpha = armAlpha(r0) * intensity;
                float[] rgb = armColor(f0);
                quad(vc, pose, ax, ay, az,
                        r0 * c0 + halfWidth * nx0, r1 * c1 + halfWidth * nx1,
                        r1 * c1 - halfWidth * nx1, r0 * c0 - halfWidth * nx0,
                        r0 * s0 + halfWidth * ny0, r1 * s1 + halfWidth * ny1,
                        r1 * s1 - halfWidth * ny1, r0 * s0 - halfWidth * ny0,
                        alpha, alpha, alpha, alpha,
                        rgb[0], rgb[1], rgb[2]);
            }
        }
    }

    private static float armAlpha(float r) {
        float k = clamp01(1.0f - (r - 4.0f) / (MAX_RADIUS - 4.0f));
        return 0.06f + 0.30f * k * k;
    }

    /** 紫 → 品红 → 青 的三段渐变。 */
    private static float[] armColor(float f) {
        if (f < 0.5f) {
            float k = f / 0.5f;
            return new float[]{lerp(0.62f, 1.0f, k), lerp(0.26f, 0.38f, k), lerp(1.0f, 0.88f, k)};
        }
        float k = (f - 0.5f) / 0.5f;
        return new float[]{lerp(1.0f, 0.45f, k), lerp(0.38f, 0.80f, k), lerp(0.88f, 1.0f, k)};
    }

    // ------------------------------------------------------------------
    // ③ 两道倾斜虚线环
    // ------------------------------------------------------------------

    private static void drawTiltedRings(VertexConsumer vc, Matrix4f pose, Direction ax, Direction ay, Direction az,
                                        float time, float intensity) {
        float[] radii = {20.5f, 26.5f};
        float[] tilts = {0.32f, -0.26f};
        float[] speeds = {0.35f, -0.48f};
        float[] reds = {0.55f, 0.80f};
        float[] greens = {0.80f, 0.45f};
        float[] blues = {1.0f, 1.0f};
        int dashes = 56;
        for (int ring = 0; ring < radii.length; ring++) {
            float radius = radii[ring];
            float tilt = tilts[ring];
            float spin = time * speeds[ring];
            float cosTilt = (float) Math.cos(tilt);
            float sinTilt = (float) Math.sin(tilt);
            float alpha = 0.30f * intensity;
            for (int d = 0; d < dashes; d++) {
                // 每 3 个虚线段里画 2 段（虚线）
                if (d % 3 == 2) {
                    continue;
                }
                float a0 = spin + (float) (d * Math.PI * 2.0 / dashes);
                float a1 = spin + (float) ((d + 1) * Math.PI * 2.0 / dashes);
                float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0);
                float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
                float w = 0.35f;
                // 绕 x 轴倾斜：y 分量乘 cosTilt、z 分量取 sinTilt
                float y0a = radius * s0 * cosTilt, z0a = radius * s0 * sinTilt;
                float y0b = (radius - w) * s0 * cosTilt, z0b = (radius - w) * s0 * sinTilt;
                float y1a = radius * s1 * cosTilt, z1a = radius * s1 * sinTilt;
                float y1b = (radius - w) * s1 * cosTilt, z1b = (radius - w) * s1 * sinTilt;
                // 直接在局部坐标里写（z 分量沿盘面法线方向）
                vertex(vc, pose, ax, ay, az, radius * c0, y0a, z0a, reds[ring], greens[ring], blues[ring], alpha);
                vertex(vc, pose, ax, ay, az, radius * c1, y1a, z1a, reds[ring], greens[ring], blues[ring], alpha);
                vertex(vc, pose, ax, ay, az, (radius - w) * c1, y1b, z1b, reds[ring], greens[ring], blues[ring], alpha);
                vertex(vc, pose, ax, ay, az, (radius - w) * c0, y0b, z0b, reds[ring], greens[ring], blues[ring], alpha);
            }
        }
    }

    // ------------------------------------------------------------------
    // ④ 公转粒子
    // ------------------------------------------------------------------

    private static void drawParticles(VertexConsumer vc, Matrix4f pose, Direction ax, Direction ay, Direction az,
                                      float time, float intensity) {
        int count = 42;
        for (int i = 0; i < count; i++) {
            float seed = i * 0.6180339f;
            float radius = 9.0f + fract(seed * 7.13f) * 20.0f;
            float speed = 0.4f + fract(seed * 3.71f) * 0.9f;
            float angle = seed * 6.2831855f + time * speed;
            float bob = (float) Math.sin(time * 1.7f + i * 1.37f) * 2.2f;
            float depth = (float) Math.cos(time * 1.1f + i * 0.77f) * 1.6f;
            float size = 0.18f + fract(seed * 11.3f) * 0.22f;
            float alpha = (0.25f + 0.35f * fract(seed * 5.7f)) * intensity;
            float c = (float) Math.cos(angle), s = (float) Math.sin(angle);
            float px = radius * c, py = radius * s + bob;
            // 面向相机的小四边形（在盘面内绕 z 微旋，避免完全轴对齐）
            float ex = size, ey = size;
            float red = 0.75f, green = 0.55f, blue = 1.0f;
            vertex(vc, pose, ax, ay, az, px - ex, py - ey, depth, red, green, blue, alpha);
            vertex(vc, pose, ax, ay, az, px + ex, py - ey, depth, red, green, blue, alpha);
            vertex(vc, pose, ax, ay, az, px + ex, py + ey, depth, red, green, blue, alpha);
            vertex(vc, pose, ax, ay, az, px - ex, py + ey, depth, red, green, blue, alpha);
        }
    }

    // ------------------------------------------------------------------
    // ⑤ 外圈脉冲虚线环
    // ------------------------------------------------------------------

    private static void drawOuterPulse(VertexConsumer vc, Matrix4f pose, Direction ax, Direction ay, Direction az,
                                       float time, float intensity) {
        float radius = 31.5f;
        float pulse = 0.18f + 0.22f * (0.5f + 0.5f * (float) Math.sin(time * 2.0));
        float alpha = pulse * intensity;
        int dashes = 72;
        float spin = time * 0.22f;
        for (int d = 0; d < dashes; d++) {
            float a0 = spin + (float) (d * Math.PI * 2.0 / dashes);
            float a1 = spin + (float) ((d + 1) * Math.PI * 2.0 / dashes);
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0);
            float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            float w = 0.5f;
            vertex(vc, pose, ax, ay, az, radius * c0, radius * s0, 0, 0.45f, 0.72f, 1.0f, alpha);
            vertex(vc, pose, ax, ay, az, radius * c1, radius * s1, 0, 0.45f, 0.72f, 1.0f, alpha);
            vertex(vc, pose, ax, ay, az, (radius - w) * c1, (radius - w) * s1, 0, 0.45f, 0.72f, 1.0f, alpha);
            vertex(vc, pose, ax, ay, az, (radius - w) * c0, (radius - w) * s0, 0, 0.45f, 0.72f, 1.0f, alpha);
        }
    }

    // ------------------------------------------------------------------
    // 顶点工具
    // ------------------------------------------------------------------

    /**
     * 写一个四边形：四个角用「盘面内的 (x,y) × 4 个 + 统一 z」给出，alpha 逐角。
     *
     * @param x0..x3 四个角的盘面 x（右）
     * @param y0..y3 四个角的盘面 y（上）
     */
    private static void quad(VertexConsumer vc, Matrix4f pose, Direction ax, Direction ay, Direction az,
                             float x0, float x1, float x2, float x3,
                             float y0, float y1, float y2, float y3,
                             float a0, float a1, float a2, float a3,
                             float red, float green, float blue) {
        vertex(vc, pose, ax, ay, az, x0, y0, 0, red, green, blue, a0);
        vertex(vc, pose, ax, ay, az, x1, y1, 0, red, green, blue, a1);
        vertex(vc, pose, ax, ay, az, x2, y2, 0, red, green, blue, a2);
        vertex(vc, pose, ax, ay, az, x3, y3, 0, red, green, blue, a3);
    }

    /**
     * 写一个顶点：盘面局部坐标 {@code (lx, ly, lz)} 映射到机器局部坐标系
     * （{@code ax}=右、{@code ay}=上、{@code az}=正面），再叠加漩涡中心偏移。
     */
    private static void vertex(VertexConsumer vc, Matrix4f pose, Direction ax, Direction ay, Direction az,
                               float lx, float ly, float lz,
                               float red, float green, float blue, float alpha) {
        float x = 0.5f + lx * ax.getStepX() + ly * ay.getStepX() + (lz + CENTER_FORWARD) * az.getStepX();
        float y = 0.5f + lx * ax.getStepY() + ly * ay.getStepY() + (lz + CENTER_FORWARD) * az.getStepY() + CENTER_UP;
        float z = 0.5f + lx * ax.getStepZ() + ly * ay.getStepZ() + (lz + CENTER_FORWARD) * az.getStepZ();
        vc.vertex(pose, x, y, z)
                .color(clamp01(red), clamp01(green), clamp01(blue), clamp01(alpha))
                .endVertex();
    }

    private static float lerp(float from, float to, float t) {
        return from + (to - from) * t;
    }

    private static float fract(float value) {
        return value - (float) Math.floor(value);
    }

    private static float clamp01(float value) {
        if (!(value > 0.0f)) { // 同时挡住 NaN
            return 0.0f;
        }
        return Math.min(value, 1.0f);
    }
}
