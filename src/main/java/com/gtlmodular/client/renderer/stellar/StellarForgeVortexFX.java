package com.gtlmodular.client.renderer.stellar;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 恒星炎炀锻炉「漩涡特效」—— <b>原样移植</b> GTO
 * {@code com.gtocore.client.renderer.fx.StellarForgeVortexFX}。
 *
 * <p>效果本体：一个以机器正前方 {@code (0,31,-2)} 为中心、半径 48 格的球壳形薄漩涡，
 * 顶点在 {@code AGE} 里按「球坐标 → 笛卡尔」生成（{@link VortexMesh}），
 * 每帧把机器坐标系矩阵烘进顶点，一次性上传到 {@link VertexBuffer}，
 * 用自绘 shader {@code gtlmodular:stellar_forge_vortex} + 屏幕空间采样画出折光。
 *
 * <h2>与 GTO 的差异（逐条）</h2>
 * <ol>
 *   <li><b>机器类型判断改成按机器定义 id</b>：GTO 用 {@code instanceof StellarForgeMachine}
 *       （它自己的机器类）；本包同 id 的机器类是 {@code com.gtlmodular.common.machine.stellar.StellarForgeMachine}
 *       —— 但为了不把「渲染器认识某个具体装备类」写死，这里统一按
 *       {@code gtceu:stellar_forge} 这个定义 id 判断（见 {@link #isStellarForge(MetaMachine)}）。</li>
 *   <li><b>顶点格式常量</b>：{@code DefaultVertexFormat.f_166851_} = {@code POSITION_COLOR_NORMAL}
 *       （已用 srg→official 映射表 + 本文件 appendTo 的 pos/color/normal 三段写入 + shader 的
 *       {@code in vec3 Normal} 三方对齐确认，<b>不是</b> POSITION_COLOR_TEX）。</li>
 *   <li><b>Java 17</b>：{@code BATCH.getFirst()} ⇒ {@code BATCH.get(0)}
 *       （{@code List.getFirst()} 是 Java 21 的 SequencedCollection API，本工程 {@code options.release=17}）。</li>
 *   <li>与 {@link GtlRenderTypes} 同包，{@code GTORenderTypes.*} ⇒ {@code GtlRenderTypes.*}。</li>
 *   <li>没有任何 GTOConfig 引用。</li>
 * </ol>
 *
 * <h2>并发/线程</h2>
 * 全部方法只在渲染线程（{@code RenderLevelStageEvent}）或客户端线程（{@code tick}）调用；
 * {@code BATCH} 的生命周期被严格限制在同一个 {@code AFTER_CUTOUT_BLOCKS} 派发内
 * （{@code dispatch} 先 {@code beginBatchFrame()}，遍历完立刻 {@code flushBatch()}）。
 */
@OnlyIn(Dist.CLIENT)
public class StellarForgeVortexFX extends AbstractStellarFx {

    /** 机器 TESR 每帧都会 refresh；超过 12 tick 没被刷新 ⇒ 认为机器已经不在视野/已拆。 */
    private static final int STALE_TICKS = 12;

    /** 开场淡入（0 ⇒ 1）用的 tick 数。 */
    private static final int STARTUP_FADE_TICKS = 12;

    /** 半径小于这个值就等于「看不见」，直接不渲染。 */
    private static final float MIN_VISIBLE_RADIUS = 0.001F;

    /** 唯一一份静态网格（按 bandAngle 缓存：bandAngle 不变就不重建）。 */
    private static final VortexMesh MESH = new VortexMesh(24, 96);

    /** 本帧待绘制的批（在 {@code AFTER_CUTOUT_BLOCKS} 内累积、立刻 flush）。 */
    private static final List<Submission> BATCH = new ArrayList<>();

    /** 单位模型视图矩阵（顶点已是「世界坐标 - 相机坐标」，所以模型视图必须是单位阵）。 */
    private static final Matrix4f IDENTITY = new Matrix4f();

    /** 本帧 RenderLevelStageEvent 给的投影矩阵（AFTER_LEVEL 时 RenderSystem 里的可能已被重置）。 */
    private static Matrix4f frameProjection;

    /** 由 {@code StellarFxManager.dispatch} 每帧设置一次。 */
    public static void setFrameProjection(Matrix4f projection) {
        frameProjection = projection == null ? null : new Matrix4f(projection);
    }

    /**
     * 帧相机变换（在 {@code AFTER_CUTOUT_BLOCKS} 抓下来的 PoseStack 矩阵 + 相机世界坐标）。
     * <p>★ AFTER_LEVEL 时 {@code camera.rotation()} 可能已被重置成单位矩阵 ⇒ 特效会「钉在屏幕上跟着视角」，
     * 所以必须在世界渲染阶段先把它抓下来。
     */
    private static Matrix4f frameCameraMatrix;
    private static Vec3 frameCameraPos;

    /** 由 {@code StellarFxManager.dispatch} 在 {@code AFTER_CUTOUT_BLOCKS} 每帧设置一次。 */
    public static void setFrameCamera(Matrix4f poseMatrix, Vec3 cameraPos) {
        frameCameraMatrix = poseMatrix == null ? null : new Matrix4f(poseMatrix);
        frameCameraPos = cameraPos;
    }

    /** 复用的动态顶点缓冲（一帧一次 upload + 一次 draw）。 */
    private static VertexBuffer batchBuffer;

    /** 时间缩放：shader 里的 {@code Time} = (now - load) / 6000，即约 6 秒一个循环。 */
    private static final float TIME_SCALE = 6000.0F;

    /** 类加载时刻，作为 shader 时间轴原点。 */
    private static final long LOAD = System.currentTimeMillis();

    private final ResourceKey<Level> dimension;
    private final BlockPos machinePos;

    private Vec3 center;
    private Direction facing;
    private float bandAngle;
    private float decayRadius;
    private float disappearRadius;
    private int lastRefreshAge;

    public StellarForgeVortexFX(ResourceKey<Level> dimension, BlockPos machinePos, Vec3 center, Direction facing,
                                float bandAngle, float decayRadius, float disappearRadius) {
        this.dimension = dimension;
        this.machinePos = machinePos.immutable();
        this.refresh(center, facing, bandAngle, decayRadius, disappearRadius);
    }

    /** 主线程/TESR 每帧调用：更新位置与半径，并把「最后存活时间」推到当前 age。 */
    public void refresh(Vec3 center, Direction facing, float bandAngle, float decayRadius, float disappearRadius) {
        this.center = center;
        this.facing = facing;
        // 与 GTO 相同：三个半径都夹到合法区间，避免除零 / 半径倒挂
        this.bandAngle = Math.max(bandAngle, 0.001F);
        this.decayRadius = Math.max(decayRadius, 0.0F);
        this.disappearRadius = Math.max(disappearRadius, this.decayRadius + 0.001F);
        this.lastRefreshAge = this.age;
    }

    @Override
    public boolean shouldDiscard() {
        if (this.age - this.lastRefreshAge > STALE_TICKS) {
            return true;
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || !level.dimension().equals(this.dimension)) {
            return true;
        }
        BlockEntity blockEntity = level.getBlockEntity(this.machinePos);
        if (!(blockEntity instanceof MetaMachineBlockEntity machineBlockEntity)) {
            return true;
        }
        MetaMachine machine = machineBlockEntity.getMetaMachine();
        // 2026-11-06: also discard while the machine is not running -- effect only shows while working.
        // GTO 此处是 `!(machine instanceof StellarForgeMachine) || !machine.isFormed()`
        return !isStellarForge(machine) || !isFormed(machine) || !isRunning(machine);
    }

    /** 清空批队列（每帧 {@code AFTER_CUTOUT_BLOCKS} 起点）。 */
    public static void beginBatchFrame() {
        BATCH.clear();
    }

    /**
     * 把本帧累积的所有漩涡顶点一次性上传并绘制。
     *
     * <p>顺序与 GTO 完全一致：先 setupRenderState → 设 shader/uniforms/sampler → 把顶点烘成
     * 世界（相机相对）坐标 → bind VertexBuffer → upload → drawWithShader(单位模型视图, 投影, shader)
     * → finally 恢复 GL 状态。
     */
    public static void flushBatch(LevelRenderer levelRenderer) {
        if (BATCH.isEmpty()) {
            return;
        }
        ShaderInstance shader = GtlRenderTypes.getStellarForgeVortexShader();
        if (shader == null) {
            // shader 还没注册上（资源重载期间）：安静跳过，下一帧再画。
            return;
        }
        TextureTarget sceneTarget = ScreenSpaceSceneCapture.capture(levelRenderer);
        if (sceneTarget == null) {
            return;
        }
        // 一帧内所有漩涡共用同一组 uniform（GTO 直接取第一个 submission 的参数）
        Submission first = BATCH.get(0);
        shader.setSampler("DiffuseSampler", sceneTarget.getColorTextureId());
        shader.safeGetUniform("ScreenSize").set((float) sceneTarget.width, (float) sceneTarget.height);
        float time = (float) (System.currentTimeMillis() - LOAD) / TIME_SCALE;
        shader.safeGetUniform("Time").set(time);
        shader.safeGetUniform("BandAngle").set(first.bandAngle());
        shader.safeGetUniform("DecayRadius").set(first.decayRadius());
        shader.safeGetUniform("DisappearRadius").set(first.disappearRadius());
        shader.safeGetUniform("VortexIntensity").set(1.0F);

        BufferBuilder bufferBuilder = Tesselator.getInstance().getBuilder();
        bufferBuilder.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR_NORMAL);
        for (Submission submission : BATCH) {
            MESH.appendTo(bufferBuilder, submission);
        }
        if (batchBuffer == null) {
            batchBuffer = new VertexBuffer(VertexBuffer.Usage.DYNAMIC);
        }
        GtlRenderTypes.STELLAR_FORGE_VORTEX.setupRenderState();
        try {
            RenderSystem.setShader(() -> shader);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            batchBuffer.bind();
            batchBuffer.upload(bufferBuilder.end());
            Matrix4f projection = frameProjection != null ? frameProjection : RenderSystem.getProjectionMatrix();
        batchBuffer.drawWithShader(IDENTITY, projection, shader);
        } finally {
            VertexBuffer.unbind();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            GtlRenderTypes.STELLAR_FORGE_VORTEX.clearRenderState();
        }
    }

    /**
     * 每 stage 调一次，只在 {@code AFTER_CUTOUT_BLOCKS} 干活：算好矩阵塞进批队列
     * （真正的 draw 在 {@link #flushBatch} 里合并）。
     */
    @Override
    public void render(RenderLevelStageEvent.Stage stage, LevelRenderer levelRenderer, PoseStack poseStack,
                       Matrix4f projectionMatrix, float partialTick, Camera camera, Frustum frustum) {
        if (stage != RenderLevelStageEvent.Stage.AFTER_LEVEL || this.center == null || this.facing == null) {
            return;
        }
        if (this.disappearRadius <= MIN_VISIBLE_RADIUS) {
            return;
        }
        if (frustum != null && !frustum.isVisible(new AABB(
                this.center.x - (double) this.disappearRadius,
                this.center.y - (double) this.disappearRadius,
                this.center.z - (double) this.disappearRadius,
                this.center.x + (double) this.disappearRadius,
                this.center.y + (double) this.disappearRadius,
                this.center.z + (double) this.disappearRadius))) {
            return;
        }
        // ★ 2026-11-02：舞台在 AFTER_LEVEL（Iris 合成之后），但 AFTER_LEVEL 的
        //   camera.rotation() / 事件 PoseStack 都可能已被重置 ⇒ 用 AFTER_CUTOUT_BLOCKS 抓下来的
        //   帧相机矩阵 + 相机坐标；没有时退回 camera.rotation()。
        PoseStack worldStack = new PoseStack();
        if (frameCameraMatrix != null) {
            worldStack.mulPoseMatrix(frameCameraMatrix);
        } else {
            worldStack.mulPose(camera.rotation());
        }
        Vec3 cameraPos = frameCameraPos != null ? frameCameraPos : camera.getPosition();
        worldStack.translate(
                this.center.x - cameraPos.x,
                this.center.y - cameraPos.y,
                this.center.z - cameraPos.z);
        rotateFromLocalUpToFacing(worldStack, this.facing);
        worldStack.scale(this.disappearRadius, this.disappearRadius, this.disappearRadius);
        // 开场 12 tick 淡入，避免机器刚启动时整块球壳突然出现
        float startupFade = Mth.clamp((this.age + partialTick) / (float) STARTUP_FADE_TICKS, 0.0F, 1.0F);
        BATCH.add(new Submission(new Matrix4f(worldStack.last().pose()),
                this.bandAngle, this.decayRadius, this.disappearRadius, 0.9F * startupFade));
    }

    /**
     * 把「本地 +Y 朝上」的盘面旋到机器 {@code facing} 方向。
     *
     * <p>映射核对（{@code Axis.f_252529_} → official {@code Axis.XP}，
     * {@code Axis.f_252403_} → official {@code Axis.ZP}，由本地 srg→official 表实测）：
     * <ul>
     *   <li>DOWN：绕 X 转 180°（任何水平轴转 180° 都能把 +Y 翻成 -Y，行为与 GTO 一致）</li>
     *   <li>NORTH / SOUTH：绕 X ∓90°（+Y → ∓Z）</li>
     *   <li>EAST / WEST：绕 Z ∓90°（+Y → ±X）</li>
     *   <li>UP：不转（默认就是 +Y）</li>
     * </ul>
     */
    private static void rotateFromLocalUpToFacing(PoseStack poseStack, Direction facing) {
        switch (facing) {
            case DOWN -> poseStack.mulPose(Axis.XP.rotationDegrees(180.0F));
            case NORTH -> poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
            case SOUTH -> poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
            case EAST -> poseStack.mulPose(Axis.ZP.rotationDegrees(-90.0F));
            case WEST -> poseStack.mulPose(Axis.ZP.rotationDegrees(90.0F));
            default -> {
                // UP：本地 +Y 已经朝上，不用动
            }
        }
    }

    // ------------------------------------------------------------------
    // 机器判定（唯一改写 GTO「instanceof StellarForgeMachine」的地方）
    // ------------------------------------------------------------------

    /** 是否为 {@code gtceu:stellar_forge}（gtlcore 注册、本包换机后的恒星炎炀锻炉）。 */
    private static boolean isStellarForge(MetaMachine machine) {
        if (machine == null || machine.getDefinition() == null) {
            return false;
        }
        ResourceLocation id = machine.getDefinition().getId();
        return id != null && "gtceu".equals(id.getNamespace()) && "stellar_forge".equals(id.getPath());
    }

    /** 结构是否已成（多块机器的 isFormed 在 {@link MultiblockControllerMachine} 上）。 */
    private static boolean isFormed(MetaMachine machine) {
        return machine instanceof MultiblockControllerMachine controller && controller.isFormed();
    }

    /** Whether the machine is currently running a recipe ({@link RecipeLogic#isWorking()}). */
    private static boolean isRunning(MetaMachine machine) {
        if (!(machine instanceof com.gtlmodular.common.machine.stellar.StellarForgeMachine forge)) {
            return false;
        }
        return forge.getRecipeLogic() != null && forge.getRecipeLogic().isWorking();
    }


    /** 去重键：同一维度 + 同一机器方块坐标 ⇒ 同一个漩涡。 */
    public record Key(ResourceKey<Level> dimension, BlockPos machinePos) {
        public Key {
            machinePos = machinePos.immutable();
        }
    }

    /** 一个待绘制实例：烘好的世界矩阵 + 该实例的参数 + 透明度。 */
    private record Submission(Matrix4f matrix, float bandAngle, float decayRadius, float disappearRadius,
                              float alpha) {
    }

    /** 网格模板顶点（球坐标算出来的单位球面位置 + 逐层 alpha）。 */
    private record TemplateVertex(float x, float y, float z, float alpha) {
    }

    /** 球壳漩涡网格（按 bandAngle 缓存，bandAngle 变化才重建）。 */
    private static final class VortexMesh {

        private final int radialSegments;
        private final int angularSegments;
        private float cachedBandAngle = Float.NaN;
        private List<TemplateVertex> vertices = Collections.emptyList();

        private VortexMesh(int radialSegments, int angularSegments) {
            this.radialSegments = radialSegments;
            this.angularSegments = angularSegments;
        }

        /** 把一个 submission 的全部三角形写进 BufferBuilder（pos / color / normal 三段）。 */
        private void appendTo(BufferBuilder bufferBuilder, Submission submission) {
            for (TemplateVertex vertex : this.getVertices(submission.bandAngle())) {
                Vector3f transformed = submission.matrix()
                        .transformPosition(vertex.x(), vertex.y(), vertex.z(), new Vector3f());
                bufferBuilder.vertex((double) transformed.x(), (double) transformed.y(), (double) transformed.z())
                        .color(1.0F, 1.0F, 1.0F, vertex.alpha() * submission.alpha())
                        // Normal 属性被 shader 当「本地坐标」（localPosition）用 ⇒ 写未变换的模板坐标
                        .normal(vertex.x(), vertex.y(), vertex.z())
                        .endVertex();
            }
        }

        private List<TemplateVertex> getVertices(float bandAngle) {
            if (Float.compare(this.cachedBandAngle, bandAngle) != 0) {
                this.vertices = this.buildVertices(bandAngle);
                this.cachedBandAngle = bandAngle;
            }
            return this.vertices;
        }

        private List<TemplateVertex> buildVertices(float bandAngle) {
            List<TemplateVertex> builtVertices = new ArrayList<>();
            float safeBandAngle = Math.max(bandAngle, 0.001F);
            float phiStep = 0.5F;
            float phi = Mth.lerp(phiStep, -safeBandAngle, safeBandAngle);
            float layer = 1.0F - Math.abs(phi / safeBandAngle);
            float alpha = 0.018F + layer * 0.028F;
            for (int radialIndex = 0; radialIndex < this.radialSegments; ++radialIndex) {
                float r0 = (float) radialIndex / (float) this.radialSegments;
                float r1 = (float) (radialIndex + 1) / (float) this.radialSegments;
                for (int angularIndex = 0; angularIndex < this.angularSegments; ++angularIndex) {
                    float theta0 = (float) (Math.PI * 2.0 * (double) angularIndex / (double) this.angularSegments);
                    float theta1 = (float) (Math.PI * 2.0 * (double) (angularIndex + 1) / (double) this.angularSegments);
                    putVertex(builtVertices, r0, theta0, phi, alpha);
                    putVertex(builtVertices, r1, theta0, phi, alpha);
                    putVertex(builtVertices, r1, theta1, phi, alpha);
                    putVertex(builtVertices, r0, theta0, phi, alpha);
                    putVertex(builtVertices, r1, theta1, phi, alpha);
                    putVertex(builtVertices, r0, theta1, phi, alpha);
                }
            }
            return builtVertices;
        }

        /** 球坐标（半径 r、极角 phi、方位角 theta）⇒ 单位球面上的笛卡尔点。 */
        private static void putVertex(List<TemplateVertex> vertices, float r, float theta, float phi, float alpha) {
            float planeRadius = Mth.cos(phi) * r;
            float x = Mth.cos(theta) * planeRadius;
            float y = Mth.sin(phi) * r;
            float z = Mth.sin(theta) * planeRadius;
            vertices.add(new TemplateVertex(x, y, z, alpha));
        }
    }
}
