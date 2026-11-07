package com.gtlmodular.client.renderer.stellar;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix4f;

/**
 * 「恒星炎炀锻炉」漩涡的<b>无自定义 shader</b> 几何（2026-11-02，光影兼容）。
 *
 * <h2>为什么要有这一层</h2>
 * <p>GTO 原版漩涡依赖自定义 {@code ShaderInstance}（抓屏 + 螺旋折射）。玩家开
 * <b>Oculus/Iris 光影包</b>时，自定义 shader 的几何在 Iris 管线里不保证被绘制
 * （实测：关光影能看到、开光影完全消失）；而<b>原版 shader</b> 的几何在光影下正常绘制。
 *
 * <p>做法：把 GTO 的 fsh（fbm 星云 + palette + 螺旋）<b>离线烘焙成贴图</b>
 * {@code textures/effect/stellar_vortex_nebula.png}（颜色常数与 fsh 一致），
 * 运行时用环面网格 + 旋转的极坐标 UV 复现转动；顶点格式 POSITION_TEX_COLOR，
 * 走 {@link StellarVortexRenderTypes#NEBULA}（原版 {@code position_color_tex} + 加性混合）。
 *
 * <p>几何：机器正面平面上的圆环盘（半径 10~46），中心 = 控制器上 31 格、向里 2 格（GTO offU/offD），
 * 18 圈 × 96 段，逐顶点 alpha 做内外淡出。
 */
@OnlyIn(Dist.CLIENT)
public final class StellarVortexNebula {

    /** 星云贴图（由 GTO fsh 的颜色/噪声离线烘焙）。 */
    public static final ResourceLocation TEXTURE =
            new ResourceLocation("gtlmodular", "textures/effect/stellar_vortex_nebula.png");

    /** 内半径（环心留空，不挡住机器核心）。 */
    private static final float INNER_RADIUS = 10.0f;

    /** 外半径（GTO DisappearRadius = 48，这里略收一点）。 */
    private static final float OUTER_RADIUS = 46.0f;

    /** 环面圈数。 */
    private static final int RINGS = 18;

    /** 每圈段数。 */
    private static final int SEGMENTS = 96;

    /** 中心偏移：上 31、向里 2（GTO offU / offD）。 */
    private static final float CENTER_UP = 31.0f;
    private static final float CENTER_FORWARD = -2.0f;

    private StellarVortexNebula() {
    }

    /** 把星云贴图绑定到 0 号纹理单元（调用方随后 {@code endBatch(NEBULA)}）。 */
    public static void bindTexture() {
        RenderSystem.setShaderTexture(0, TEXTURE);
    }

    /**
     * 写一帧星云盘。
     *
     * @param consumer  已绑定 {@link StellarVortexRenderTypes#NEBULA} 的顶点消费者
     * @param pose      已平移到控制器方块原点的矩阵
     * @param front     机器正面朝向
     * @param time      秒（贴图 UV 旋转用）
     * @param intensity 亮度系数（成型待机 0.55 / 运行 1.0）
     */
    public static void draw(VertexConsumer consumer, Matrix4f pose, Direction front,
                            float time, float intensity) {
        if (consumer == null || pose == null || front == null) {
            return;
        }
        float brightness = Math.max(0.0f, Math.min(intensity, 2.0f));
        if (brightness <= 0.001f) {
            return;
        }
        Direction right = front.getClockWise();
        Direction up = Direction.UP;
        float spin = time * 0.055f;
        for (int ring = 0; ring < RINGS; ring++) {
            float f0 = ring / (float) RINGS;
            float f1 = (ring + 1) / (float) RINGS;
            float r0 = INNER_RADIUS + f0 * (OUTER_RADIUS - INNER_RADIUS);
            float r1 = INNER_RADIUS + f1 * (OUTER_RADIUS - INNER_RADIUS);
            float a0 = ringAlpha(f0) * brightness;
            float a1 = ringAlpha(f1) * brightness;
            for (int seg = 0; seg < SEGMENTS; seg++) {
                float t0 = (float) (seg * Math.PI * 2.0 / SEGMENTS);
                float t1 = (float) ((seg + 1) * Math.PI * 2.0 / SEGMENTS);
                float c0 = (float) Math.cos(t0);
                float s0 = (float) Math.sin(t0);
                float c1 = (float) Math.cos(t1);
                float s1 = (float) Math.sin(t1);
                // 极坐标 UV + 每圈扭转（越往外扭得越多）⇒ 贴图里的螺旋被拉成旋涡
                float u0 = angleU(seg, spin + f0 * 0.35f);
                float u1 = angleU(seg + 1, spin + f0 * 0.35f);
                float u0b = angleU(seg, spin + f1 * 0.35f);
                float u1b = angleU(seg + 1, spin + f1 * 0.35f);
                float v0 = f0;
                float v1 = f1;
                vertex(consumer, pose, right, up, front, r0 * c0, r0 * s0, a0, u0, v0);
                vertex(consumer, pose, right, up, front, r1 * c0, r1 * s0, a1, u0b, v1);
                vertex(consumer, pose, right, up, front, r1 * c1, r1 * s1, a1, u1b, v1);
                vertex(consumer, pose, right, up, front, r0 * c1, r0 * s1, a0, u1, v0);
            }
        }
    }

    private static float angleU(int segment, float phase) {
        float u = segment / (float) SEGMENTS + phase;
        return u - (float) Math.floor(u);
    }

    /** 内外径向淡出（中心留空、外缘柔和）。 */
    private static float ringAlpha(float f) {
        float inner = Math.min(1.0f, f / 0.18f);
        float outer = 1.0f - Math.min(1.0f, Math.max(0.0f, (f - 0.62f) / 0.38f));
        return inner * outer * 1.0f;
    }

    /** 顶点：局部盘面坐标 → 机器局部坐标（右/上/正面）→ 漩涡中心偏移。 */
    private static void vertex(VertexConsumer vc, Matrix4f pose, Direction right, Direction up, Direction front,
                               float lx, float ly, float alpha, float u, float v) {
        float x = 0.5f + lx * right.getStepX() + ly * up.getStepX() + CENTER_FORWARD * front.getStepX();
        float y = 0.5f + lx * right.getStepY() + ly * up.getStepY() + CENTER_FORWARD * front.getStepY() + CENTER_UP;
        float z = 0.5f + lx * right.getStepZ() + ly * up.getStepZ() + CENTER_FORWARD * front.getStepZ();
        // ★ 元素顺序要跟顶点格式一致：
        //   手工 GL 批用的是 DefaultVertexFormat.POSITION_COLOR_TEX ⇒ POSITION → COLOR → UV。
        //   （写错顺序会抛 IllegalStateException: Not filled all elements of the vertex）
        vc.vertex(pose, x, y, z)
                .color(1.0f, 1.0f, 1.0f, Math.max(0.0f, Math.min(alpha, 1.0f)))
                .uv(u, v)
                .endVertex();
    }
}
