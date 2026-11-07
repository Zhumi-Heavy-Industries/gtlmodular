package com.gtlmodular.client.renderer.nyarlathotep;

import com.gregtechceu.gtceu.api.pattern.util.RelativeDirection;
import com.gtlmodular.GTLModular;
import com.gtlmodular.common.config.GTLModularConfig;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.List;

/**
 * 奈亚拉托提普之触 · 正中心黑日（屏幕空间 shader 版，0.3.328）。
 *
 * <h2>为什么不是几何</h2>
 * <p>玩家口径：恒星炎炀锻炉补的那版特效「不是几何的版本」——那是<b>抓屏 + 自定义 shader</b>
 * 的屏幕空间折射。黑日同理：一个面向相机的方片，片元 shader 采样已经画好的世界，
 * 在视界附近做引力透镜、在视界外画吸积盘/光子环。视觉主体是 shader，不是一圈圈加性线条。
 *
 * <h2>怎么定位</h2>
 * <ul>
 *   <li>结构几何中心 = 控制器局部 {@code (0,0,-24)}（GTO {@code .mbs} 逐格核对的中央菱形核心处）；</li>
 *   <li>方片在<b>视空间</b>里构建：先取结构中心，再沿「朝相机」方向前移 0.9R 到黑日近表面，
 *       ⇒ 菱形核心/更远的方块会被黑日遮住，而相机与黑日之间的机壳仍然正常挡住它；</li>
 *   <li>方片半边长 = 2.2R，shader 内部再把 {@code localUv} 映射成「视界 0.43 → 吸积盘 → 外晕」。</li>
 * </ul>
 *
 * <h2>渲染顺序</h2>
 * <ol>
 *   <li>{@code AFTER_CUTOUT_BLOCKS}：{@link BlackSunCapture#capture} 抓当前帧缓冲；</li>
 *   <li>把本帧所有黑日方片写进一个 {@code POSITION_TEX_COLOR} 批；</li>
 *   <li>{@link BlackSunRenderTypes#BLACK_SUN} setup → 绑定 shader/场景纹理/三个 uniform →
 *       一次 {@code drawWithShader} → clear；</li>
 *   <li>失败只记一次 error，本会话不再画（不影响机器）。</li>
 * </ol>
 */
@OnlyIn(Dist.CLIENT)
public final class BlackSunRenderer {

    /** 一个黑日实例（客户端扫描缓存转过来的最小信息）。 */
    public record Target(BlockPos pos, Direction front, Direction upwards, boolean flipped, boolean active) {
    }

    /** 单位模型视图矩阵（顶点已经是视空间坐标）。 */
    private static final Matrix4f IDENTITY = new Matrix4f();

    /** 复用的动态顶点缓冲。 */
    private static VertexBuffer batchBuffer;

    // ★ 0.3.329：AFTER_LEVEL 时 Camera.rotation() 已被重置成单位矩阵，不能在那里现取相机。
    //   在世界渲染阶段（AFTER_CUTOUT_BLOCKS）先把相机基向量/位置/3D 投影抄下来，AFTER_LEVEL 用。
    private static final org.joml.Vector3f FRAME_LOOK = new org.joml.Vector3f(0, 0, -1);
    private static final org.joml.Vector3f FRAME_UP = new org.joml.Vector3f(0, 1, 0);
    private static final org.joml.Vector3f FRAME_LEFT = new org.joml.Vector3f(-1, 0, 0);
    private static Vec3 frameCameraPos = Vec3.ZERO;
    private static Matrix4f frameProjection;
    private static boolean frameValid;

    private static boolean failureLogged;
    private static boolean shaderMissingLogged;

    private BlackSunRenderer() {
    }

    /** shader 是否注册成功。 */
    public static boolean shaderReady() {
        return BlackSunRenderTypes.getShader() != null;
    }

    /**
     * 在 {@code AFTER_CUTOUT_BLOCKS}（此时 PoseStack/Camera 仍有效）抄下本帧相机基向量、位置与 3D 投影。
     * AFTER_LEVEL 再画时直接用这份快照 —— 这就是 0.3.327 修「特效跟着视角跑 / 光影下看不见」的口径。
     */
    public static void captureFrame(Camera camera, Matrix4f projection) {
        if (camera == null || projection == null) {
            frameValid = false;
            return;
        }
        FRAME_LOOK.set(camera.getLookVector());
        FRAME_UP.set(camera.getUpVector());
        FRAME_LEFT.set(camera.getLeftVector());
        frameCameraPos = camera.getPosition();
        frameProjection = new Matrix4f(projection);
        frameValid = true;
    }

    /** 是否已抓到本帧相机快照（AFTER_LEVEL 画之前必须为 true）。 */
    public static boolean hasFrame() {
        return frameValid && frameProjection != null;
    }

    /**
     * 本帧统一画所有黑日（在 {@code AFTER_LEVEL} 调用，Iris 合成之后）。
     *
     * @param level         客户端世界
     * @param levelRenderer 世界渲染器（抓屏用）
     * @param partialTick   部分 tick
     * @param targets       本帧要画的黑日
     */
    public static void drawAll(Level level, LevelRenderer levelRenderer, float partialTick, List<Target> targets) {
        if (targets == null || targets.isEmpty() || level == null || !hasFrame()) {
            return;
        }
        if (!GTLModularConfig.isNyarlathotepFxEnabled()) {
            return;
        }
        float intensity = (float) Math.max(0.0, Math.min(4.0, GTLModularConfig.getNyarlathotepFxIntensity()));
        if (intensity <= 0.001f) {
            return;
        }
        double radius = Math.max(3.0, Math.min(10.0, GTLModularConfig.getNyarlathotepFxCoreRadius()));

        ShaderInstance shader = BlackSunRenderTypes.getShader();
        if (shader == null) {
            if (!shaderMissingLogged) {
                shaderMissingLogged = true;
                GTLModular.LOGGER.warn("[gtlmodular] 奈亚中央黑日：shader 未注册（资源加载失败/驱动不支持）"
                        + "⇒ 本会话不绘制黑日");
            }
            return;
        }

        try {
            BlackSunCapture.beginFrame();
            TextureTarget scene = BlackSunCapture.capture(levelRenderer);
            if (scene == null) {
                return;
            }
            // ★ 用 AFTER_CUTOUT_BLOCKS 抄下来的相机基向量把世界方向转到视空间。
            //   （AFTER_LEVEL 现取 camera.getLookVector() 会拿到已被重置的值 —— 0.3.327 踩过。）
            Vec3 cameraPos = frameCameraPos;
            org.joml.Vector3f look = FRAME_LOOK;
            org.joml.Vector3f up = FRAME_UP;
            org.joml.Vector3f left = FRAME_LEFT;
            double time = (level.getGameTime() + partialTick) / 20.0;

            BufferBuilder builder = Tesselator.getInstance().getBuilder();
            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            int quads = 0;
            for (Target target : targets) {
                if (target == null || target.pos() == null || target.front() == null || target.upwards() == null) {
                    continue;
                }
                Vec3 sunWorld = sunWorldCenter(target);
                double dx = sunWorld.x - cameraPos.x;
                double dy = sunWorld.y - cameraPos.y;
                double dz = sunWorld.z - cameraPos.z;
                // 视空间：x 右、y 上、z 朝观察者（相机看 -Z）
                Vector3f center = new Vector3f(
                        (float) (-(dx * left.x + dy * left.y + dz * left.z)),
                        (float) (dx * up.x + dy * up.y + dz * up.z),
                        (float) (-(dx * look.x + dy * look.y + dz * look.z)));
                // 视空间相机看 -Z；AFTER_LEVEL 用 NO_DEPTH_TEST，不再需要「近表面」深度偏移，
                // 直接按结构中心的距离投影，尺寸才和真实 3D 物体一致。
                float half = (float) (radius * 2.2);
                float alpha = target.active() ? 1.0f : 0.8f;
                vertex(builder, center.x - half, center.y - half, center.z, -1.0f, -1.0f, alpha);
                vertex(builder, center.x + half, center.y - half, center.z, 1.0f, -1.0f, alpha);
                vertex(builder, center.x + half, center.y + half, center.z, 1.0f, 1.0f, alpha);
                vertex(builder, center.x - half, center.y + half, center.z, -1.0f, 1.0f, alpha);
                quads++;
            }
            if (quads == 0) {
                builder.discard();
                return;
            }

            if (batchBuffer == null) {
                batchBuffer = new VertexBuffer(VertexBuffer.Usage.DYNAMIC);
            }
            batchBuffer.bind();
            batchBuffer.upload(builder.end());
            VertexBuffer.unbind();

            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            BlackSunRenderTypes.BLACK_SUN.setupRenderState();
            try {
                shader.setSampler("DiffuseSampler", scene.getColorTextureId());
                shader.safeGetUniform("ScreenSize").set((float) scene.width, (float) scene.height);
                shader.safeGetUniform("Time").set((float) time);
                shader.safeGetUniform("Intensity").set(intensity);
                RenderSystem.setShader(() -> shader);
                batchBuffer.bind();
                batchBuffer.drawWithShader(IDENTITY, frameProjection, shader);
            } finally {
                VertexBuffer.unbind();
                RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                BlackSunRenderTypes.BLACK_SUN.clearRenderState();
            }
        } catch (Throwable failure) {
            if (!failureLogged) {
                failureLogged = true;
                GTLModular.LOGGER.error("[gtlmodular] 奈亚中央黑日：绘制失败，本次会话不再绘制"
                        + "（机器本身不受影响）", failure);
            }
        }
    }

    /** 结构几何中心的世界坐标：base + 0.5 + 轴变换(0,0,-24)。 */
    private static Vec3 sunWorldCenter(Target target) {
        Direction x = RelativeDirection.LEFT.getRelativeFacing(target.front(), target.upwards(), target.flipped());
        if (target.front() == Direction.DOWN) {
            x = x.getOpposite();
        }
        Direction y = RelativeDirection.UP.getRelativeFacing(target.front(), target.upwards(), target.flipped());
        Direction z = RelativeDirection.FRONT.getRelativeFacing(target.front(), target.upwards(), target.flipped());
        double lx = 0, ly = 0, lz = -24;
        return new Vec3(
                target.pos().getX() + 0.5 + lx * x.getStepX() + ly * y.getStepX() + lz * z.getStepX(),
                target.pos().getY() + 0.5 + lx * x.getStepY() + ly * y.getStepY() + lz * z.getStepY(),
                target.pos().getZ() + 0.5 + lx * x.getStepZ() + ly * y.getStepZ() + lz * z.getStepZ());
    }

    private static void vertex(BufferBuilder builder, float x, float y, float z,
                               float u, float v, float alpha) {
        builder.vertex(x, y, z).uv(u, v).color(1.0f, 1.0f, 1.0f, alpha).endVertex();
    }
}
