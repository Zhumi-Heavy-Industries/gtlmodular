package com.gtlmodular.client.renderer;

import com.gtlmodular.common.data.nanochip.NanochipModContext;
import com.gtlmodular.common.diag.SwallowedErrors;
import com.gregtechceu.gtceu.api.block.IMachineBlock;
import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gtlmodular.client.ctm.ConnectedCtmBakedModel;
import com.gtlmodular.client.ctm.CtmConnectivity;
import com.gtlmodular.client.ctm.CtmFaceFrame;
import com.gtlmodular.client.ctm.CtmHullGroup;
import com.gtlmodular.client.ctm.MachineCtmBakedModel;
import com.gtlmodular.client.ctm.NacSlotIndex;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

/** 通过额外外壳几何补足机器 CTM，避免依赖机器模型是否提供可重贴图的烘焙 quad。 */
public final class NacMachineShellRenderer {

    public static final boolean ENABLED = true;

    /** 有侧面独立图案的机器可排除整块，避免外壳罩盖住原有标识。 */
    public static final Set<String> EXCLUDED_BLOCKS = Set.of();

    /** 仅个别面有独立图案时按面排除，避免整台机器失去连接外壳。 */
    public static final Set<String> EXCLUDED_FACES = Set.of();

    /** 沿法线略微外移，避免与原壳共面闪烁；相邻玻璃的内表面可能被这一薄层遮住。 */
    public static final float SHELL_OFFSET = 0.001F;

    public static final double MAX_DISTANCE = 96.0D;

    public static final int MAX_BLOCKS_PER_FRAME = 256;

    public static final int SEED_CHUNK_RADIUS = 4;

    public static final int SEED_EVERY_FRAMES = 20;

    public static final int STATS_EVERY_FRAMES = 600;

    private static final Direction[] DIRECTIONS = Direction.values();
    private static final int FACE_COUNT = 6;

    private static final int[] RING_ORTHO = { 0, 2, 4, 6 };
    private static final int[] RING_DIAG = { 1, 3, 5, 7 };

    private static final DirectionProperty[] FALLBACK_FACING = {
            BlockStateProperties.FACING, BlockStateProperties.HORIZONTAL_FACING,
    };

    private static final int CANDIDATE_BITS = 12;
    private static final int CANDIDATE_MASK = (1 << CANDIDATE_BITS) - 1;
    private static final int MAX_CANDIDATES = 1 << CANDIDATE_BITS;
    private static final double DIST_QUANT = 16.0D;

    private static volatile BakedQuad[][] variantQuads;
    private static volatile CtmFaceFrame frame;

    private static volatile boolean geometryTried;
    private static volatile String geometryFailure;
    private static final java.util.concurrent.atomic.AtomicInteger GEOMETRY_WARN = new java.util.concurrent.atomic.AtomicInteger(2);

    private static int lastChunkX = Integer.MIN_VALUE;
    private static int lastChunkZ = Integer.MIN_VALUE;
    private static int seedCountdown;

    private static int lastCandidates = -1;
    private static int lastDrawn = -1;
    private static int lastExcluded = -1;
    private static int framesSinceStats;
    private static boolean statsLogged;

    private static final java.util.concurrent.atomic.AtomicInteger FAILURE_WARN = new java.util.concurrent.atomic.AtomicInteger(3);

    private static final Set<String> ONCE = ConcurrentHashMap.newKeySet();

    private NacMachineShellRenderer() {
    }

    /** quad 和面内帧引用旧图集与旧模型，资源重载时必须失效以免花屏。 */
    public static void reset() {
        variantQuads = null;
        frame = null;
        geometryTried = false;
        geometryFailure = null;
        lastGeometryAttemptAt = 0L;
        GEOMETRY_WARN.set(2);
        lastChunkX = Integer.MIN_VALUE;
        lastChunkZ = Integer.MIN_VALUE;
        seedCountdown = 0;
        lastCandidates = -1;
        lastDrawn = -1;
        lastExcluded = -1;
        framesSinceStats = 0;
        statsLogged = false;
        ONCE.clear();
        if (ENABLED) {
            NanochipModContext.logger().info("[gtlmodular] NAC 机器外壳罩：已复位（开关 ENABLED={}；旧 quad 表 / 面内帧 / "
                    + "统计已作废，下一帧重新解析）。旧「换 quad 贴图」那条路 MACHINE_HULL_CTM={}（实机证明对机器无效）。",
                    ENABLED, com.gtlmodular.client.ctm.CtmClientSetup.MACHINE_HULL_CTM);
        }
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (!ENABLED) {
            return;
        }
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS) {
            return;
        }
        try {
            renderShells(event);
        } catch (Throwable failure) {
            SwallowedErrors.report("NacMachineShellRenderer#onRenderLevelStage", failure);
            warnFailure(failure);
        }
    }

    private static void renderShells(RenderLevelStageEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        Camera camera = event.getCamera();
        if (level == null || camera == null || event.getPoseStack() == null) {
            return;
        }
        Vec3 eye = camera.getPosition();
        BlockPos eyePos = BlockPos.containing(eye);

        seedIfDue(level, eyePos);
        Set<Long> slots = NacSlotIndex.snapshot();
        if (slots.isEmpty()) {
            reportStats(0, 0, 0);
            return;
        }

        if (!ensureGeometry()) {
            return;
        }
        TextureAtlasSprite[] variants = MachineCtmBakedModel.resolveSharedVariants(
                minecraft.getTextureAtlas(InventoryMenu.BLOCK_ATLAS));
        if (variants == null) {
            reportOnce("variants", "[gtlmodular] NAC 机器外壳罩：47 格变体 sprite 还没进图集 —— 本帧不画"
                    + "（外观 = 现状）；若一直这样，看 CtmClientSetup 的「尚未就绪」那条日志。");
            return;
        }
        CtmFaceFrame frames = frame;
        if (frames == null || !frames.complete()) {
            reportOnce("frame", "[gtlmodular] NAC 机器外壳罩：机壳的面内坐标帧不完整 —— 本帧不画"
                    + "（宁可保持现状，也不要把 47 格的接缝画到错的边上）。");
            return;
        }

        double maxDistanceSq = MAX_DISTANCE * MAX_DISTANCE;
        long[] inRange = new long[Math.min(slots.size(), MAX_CANDIDATES)];
        double[] distanceSq = new double[inRange.length];
        int count = 0;
        for (long packed : slots) {
            if (count >= inRange.length) {
                break;
            }
            BlockPos pos = BlockPos.of(packed);
            double dx = pos.getX() + 0.5D - eye.x;
            double dy = pos.getY() + 0.5D - eye.y;
            double dz = pos.getZ() + 0.5D - eye.z;
            double distSq = dx * dx + dy * dy + dz * dz;
            if (distSq > maxDistanceSq) {
                continue;
            }
            var frustum = event.getFrustum();
            if (frustum != null && !frustum.isVisible(new AABB(pos))) {
                continue;
            }
            inRange[count] = packed;
            distanceSq[count] = distSq;
            count++;
        }

        int drawn = 0;
        int excluded = 0;
        if (count > 0) {
            VertexConsumer consumer = minecraft.renderBuffers().bufferSource().getBuffer(RenderType.solid());
            PoseStack poseStack = event.getPoseStack();
            poseStack.pushPose();
            // 事件矩阵已含相机旋转，补上相机位置平移后才能使用世界坐标绘制。
            poseStack.translate(-eye.x, -eye.y, -eye.z);
            int limit = Math.min(count, MAX_BLOCKS_PER_FRAME);
            long[] order = count > limit ? nearestFirst(inRange, distanceSq, count) : null;
            for (int i = 0; i < limit; i++) {
                BlockPos pos = BlockPos.of(order == null ? inRange[i] : order[i]);
                BlockState state = level.getBlockState(pos);
                Block block = state.getBlock();
                if (!(block instanceof IMachineBlock)) {
                    continue;
                }
                if (!NacSlotIndex.isNacSlot(level, pos)) {
                    continue; // 候选缓存可能过时，绘制前必须复核当前仓位。
                }
                ResourceLocation id = ForgeRegistries.BLOCKS.getKey(block);
                String blockId = id == null ? "" : id.toString();
                if (EXCLUDED_BLOCKS.contains(blockId)) {
                    excluded++;
                    continue;
                }
                Direction front = frontFace(level, pos, state, block);
                if (front == null) {
                    excluded++;
                    continue;
                }
                Set<Direction> skipFaces = excludedFaces(blockId);
                if (drawOne(consumer, poseStack, level, pos, state, front, skipFaces, variants, frames)) {
                    drawn++;
                } else {
                    excluded++;
                }
            }
            poseStack.popPose();
            minecraft.renderBuffers().bufferSource().endBatch(RenderType.solid());
        }
        reportStats(count, drawn, excluded);
    }

    private static void seedIfDue(ClientLevel level, BlockPos eyePos) {
        int cx = eyePos.getX() >> 4;
        int cz = eyePos.getZ() >> 4;
        boolean chunkChanged = cx != lastChunkX || cz != lastChunkZ;
        if (!chunkChanged && --seedCountdown > 0) {
            return;
        }
        lastChunkX = cx;
        lastChunkZ = cz;
        seedCountdown = SEED_EVERY_FRAMES;
        NacSlotIndex.seedNearby(level, eyePos, SEED_CHUNK_RADIUS);
    }

    /** 将距离和下标打包排序，避免候选排序逐帧装箱分配。 */
    private static long[] nearestFirst(long[] packedPositions, double[] distanceSq, int count) {
        long[] keys = new long[count];
        for (int i = 0; i < count; i++) {
            long quantized = (long) Math.min(distanceSq[i] * DIST_QUANT, (double) Integer.MAX_VALUE);
            keys[i] = (quantized << CANDIDATE_BITS) | (i & CANDIDATE_MASK);
        }
        Arrays.sort(keys);
        long[] sorted = new long[Math.min(count, MAX_BLOCKS_PER_FRAME)];
        for (int i = 0; i < sorted.length; i++) {
            sorted[i] = packedPositions[(int) (keys[i] & CANDIDATE_MASK)];
        }
        return sorted;
    }

    private static boolean drawOne(VertexConsumer consumer, PoseStack poseStack, Level level, BlockPos pos,
                                   BlockState state, Direction front, Set<Direction> skipFaces,
                                   TextureAtlasSprite[] variants, CtmFaceFrame frames) {
        BakedQuad[][] quads = variantQuads;
        if (quads == null) {
            return false;
        }
        poseStack.pushPose();
        poseStack.translate(pos.getX(), pos.getY(), pos.getZ());
        PoseStack.Pose pose = poseStack.last();
        boolean any = false;
        for (Direction face : DIRECTIONS) {
            // 正脸与 overlay 共面，罩住它会抹掉机器的操作标识。
            if (face == front || skipFaces.contains(face) || frames.rightOf(face) == null) {
                continue;
            }
            int mask = computeMask(level, pos, face, frames);
            int index = CtmConnectivity.ctmIndex(mask);
            TextureAtlasSprite sprite = variants[index];
            BakedQuad quad = quads[face.ordinal()][index];
            if (sprite == null || quad == null) {
                continue;
            }
            float shade = faceShade(level, face);
            vertexLighting(level, pos, face, frames, quad, shade, VERTEX_LIGHT, VERTEX_BRIGHT);
            consumer.putBulkData(pose, quad, VERTEX_BRIGHT, 1.0F, 1.0F, 1.0F, 1.0F,
                    VERTEX_LIGHT, OverlayTexture.NO_OVERLAY, false);
            any = true;
        }
        poseStack.popPose();
        return any;
    }

    /** 逐顶点取光与遮挡，并限制到机器所在格的亮度，避免外壳在暗处比周围更亮。 */
    private static void vertexLighting(Level level, BlockPos pos, Direction face, CtmFaceFrame frames,
                                       BakedQuad quad, float shade, int[] lights, float[] bright) {
        Direction right = frames.rightOf(face);
        Direction up = frames.upOf(face);
        if (right == null || up == null) {
            int sky = 0, blk = 0;
            for (Direction d : DIRECTIONS) {
                BlockPos n = pos.relative(d);
                sky = Math.max(sky, level.getBrightness(LightLayer.SKY, n));
                blk = Math.max(blk, level.getBrightness(LightLayer.BLOCK, n));
            }
            int packed = LightTexture.pack(blk, sky);
            for (int i = 0; i < 4; i++) {
                lights[i] = packed;
                bright[i] = shade;
            }
            return;
        }
        // 不透明机器自身格子的光值不可靠，取六邻居最大值作为亮度上限。
        int ownSky = 0, ownBlock = 0;
        for (Direction d : DIRECTIONS) {
            BlockPos n = pos.relative(d);
            ownSky = Math.max(ownSky, level.getBrightness(LightLayer.SKY, n));
            ownBlock = Math.max(ownBlock, level.getBrightness(LightLayer.BLOCK, n));
        }
        final int[] v = quad.getVertices();
        final BlockPos outside = pos.relative(face);
        for (int vi = 0; vi < 4; vi++) {
            float dx = Float.intBitsToFloat(v[vi * VERTEX_STRIDE]) - 0.5F;
            float dy = Float.intBitsToFloat(v[vi * VERTEX_STRIDE + 1]) - 0.5F;
            float dz = Float.intBitsToFloat(v[vi * VERTEX_STRIDE + 2]) - 0.5F;
            int sr = (dx * right.getStepX() + dy * right.getStepY() + dz * right.getStepZ()) >= 0.0F ? 1 : -1;
            int su = (dx * up.getStepX() + dy * up.getStepY() + dz * up.getStepZ()) >= 0.0F ? 1 : -1;
            BlockPos sideRight = outside.relative(right, sr);
            BlockPos sideUp = outside.relative(up, su);
            BlockPos corner = sideRight.relative(up, su);
            int sky = 0, blk = 0;
            float occlusion = 0.0F;
            for (int k = 0; k < 4; k++) {
                BlockPos p = switch (k) {
                    case 0 -> outside;
                    case 1 -> sideRight;
                    case 2 -> sideUp;
                    default -> corner;
                };
                sky += level.getBrightness(LightLayer.SKY, p);
                blk += level.getBrightness(LightLayer.BLOCK, p);
                occlusion += level.getBlockState(p).isSolidRender(level, p) ? OCCLUDED_SHADE : 1.0F;
            }
            sky /= 4;
            blk /= 4;
            lights[vi] = LightTexture.pack(Math.min(blk, ownBlock), Math.min(sky, ownSky));
            bright[vi] = shade * (occlusion * 0.25F);
        }
    }

    private static final int VERTEX_STRIDE = 8;
    private static final float OCCLUDED_SHADE = 0.2F;
    /** 渲染线程复用缓冲，避免逐格逐帧分配光照数组。 */
    private static final int[] VERTEX_LIGHT = new int[4];
    private static final float[] VERTEX_BRIGHT = new float[4];

    private static float faceShade(Level level, Direction face) {
        return level.getShade(face, true);
    }

    private static int computeMask(Level level, BlockPos pos, Direction face, CtmFaceFrame frames) {
        Direction rightDir = frames.rightOf(face);
        Direction upDir = frames.upOf(face);
        if (rightDir == null || upDir == null) {
            return 0;
        }
        int mask = 0;
        for (int p : RING_ORTHO) {
            if (connects(level, pos, rightDir, upDir, p)) {
                mask |= 1 << p;
            }
        }
        if (CtmConnectivity.singletonIndex(mask) >= 0) {
            return mask;
        }
        for (int p : RING_DIAG) {
            if (connects(level, pos, rightDir, upDir, p)) {
                mask |= 1 << p;
            }
        }
        return mask;
    }

    private static boolean connects(Level level, BlockPos pos, Direction rightDir, Direction upDir, int p) {
        int sr = CtmConnectivity.STEP_RIGHT[p];
        int su = CtmConnectivity.STEP_UP[p];
        int dx = sr * rightDir.getStepX() + su * upDir.getStepX();
        int dy = sr * rightDir.getStepY() + su * upDir.getStepY();
        int dz = sr * rightDir.getStepZ() + su * upDir.getStepZ();
        if (dx == 0 && dy == 0 && dz == 0) {
            return false;
        }
        BlockPos neighbour = pos.offset(dx, dy, dz);
        return CtmHullGroup.rendersMeshCasingTile(level, neighbour, level.getBlockState(neighbour));
    }

    private static Direction frontFace(Level level, BlockPos pos, BlockState state, Block block) {
        try {
            MetaMachine machine = MetaMachine.getMachine(level, pos);
            if (machine != null) {
                Direction facing = machine.getFrontFacing();
                if (facing != null) {
                    return facing;
                }
            }
        } catch (Throwable ignored) {
            SwallowedErrors.report("NacMachineShellRenderer#frontFace", ignored);
        }
        if (block instanceof IMachineBlock machineBlock) {
            RotationState rotation = machineBlock.getRotationState();
            DirectionProperty property = rotation == null ? null : rotation.property;
            if (property != null && state.hasProperty(property)) {
                return state.getValue(property);
            }
        }
        for (DirectionProperty property : FALLBACK_FACING) {
            if (state.hasProperty(property)) {
                return state.getValue(property);
            }
        }
        return null;
    }

    private static Set<Direction> excludedFaces(String blockId) {
        if (EXCLUDED_FACES.isEmpty() || blockId.isEmpty()) {
            return Set.of();
        }
        for (String entry : EXCLUDED_FACES) {
            int eq = entry.indexOf('=');
            if (eq <= 0 || !entry.substring(0, eq).equals(blockId)) {
                continue;
            }
            Set<Direction> faces = new LinkedHashSet<>();
            for (String name : entry.substring(eq + 1).split(",")) {
                for (Direction face : DIRECTIONS) {
                    if (face.getName().equalsIgnoreCase(name.trim())) {
                        faces.add(face);
                    }
                }
            }
            return faces;
        }
        return Set.of();
    }

    /** 复用机壳模型的几何与面内帧，确保外壳罩和相邻机壳使用同一套 UV 方向。 */
    private static final long GEOMETRY_RETRY_MILLIS = 250L;
    private static volatile long lastGeometryAttemptAt;

    private static boolean ensureGeometry() {
        if (variantQuads != null && frame != null) {
            return true;
        }
        if (geometryTried && geometryFailure != null) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (geometryTried && now - lastGeometryAttemptAt < GEOMETRY_RETRY_MILLIS) {
            return false;
        }
        lastGeometryAttemptAt = now;
        geometryTried = true;
        try {
            Block casing = ForgeRegistries.BLOCKS.getValue(
                    NanochipModContext.registryId(CtmHullGroup.MESH_CASING_ID));
            if (casing == null) {
                return failGeometry("机壳方块 " + CtmHullGroup.MESH_CASING_ID + " 取不到（注册名漂移？）");
            }
            BlockState casingState = casing.defaultBlockState();
            Minecraft minecraft = Minecraft.getInstance();
            BakedModel casingModel = minecraft.getBlockRenderer().getBlockModelShaper().getBlockModel(casingState);
            CtmFaceFrame derived = CtmFaceFrame.derive(casingModel, casingState);
            if (!derived.complete()) {
                return failGeometry("机壳模型有面推不出面内坐标帧（几何不满足判据）");
            }
            BakedQuad[] base = new BakedQuad[FACE_COUNT];
            for (Direction face : DIRECTIONS) {
                List<BakedQuad> quads = casingModel.getQuads(casingState, face, RandomSource.create(42L));
                if (quads.isEmpty()) {
                    return failGeometry("机壳模型的面 " + face + " 没有 quad");
                }
                base[face.ordinal()] = offset(quads.get(0), face);
            }
            TextureAtlasSprite[] variants = MachineCtmBakedModel.resolveSharedVariants(
                    minecraft.getTextureAtlas(InventoryMenu.BLOCK_ATLAS));
            if (variants == null) {
                geometryTried = false;
                return false;
            }
            // 图集未齐时不能锁定 quad 表，否则兜底 sprite 会被永久缓存。
            if (!MachineCtmBakedModel.sharedVariantsComplete()) {
                geometryTried = false;
                return false;
            }
            BakedQuad[][] built = new BakedQuad[FACE_COUNT][];
            for (Direction face : DIRECTIONS) {
                BakedQuad[] row = new BakedQuad[CtmConnectivity.CTM_TILES];
                for (int v = 0; v < row.length; v++) {
                    TextureAtlasSprite sprite = variants[v];
                    row[v] = sprite == null ? null : ConnectedCtmBakedModel.retexture(base[face.ordinal()], sprite);
                }
                built[face.ordinal()] = row;
            }
            variantQuads = built; // 表填满后再发布，避免读取半初始化几何。
            frame = derived;
            return true;
        } catch (Throwable failure) {
            SwallowedErrors.report("NacMachineShellRenderer#ensureGeometry", failure);
            warnFailure(failure);
            geometryFailure = "解析抛异常：" + failure;
            return false;
        }
    }

    private static BakedQuad offset(BakedQuad quad, Direction face) {
        int[] vertices = quad.getVertices().clone();
        int stride = vertices.length / 4;
        float dx = face.getStepX() * SHELL_OFFSET;
        float dy = face.getStepY() * SHELL_OFFSET;
        float dz = face.getStepZ() * SHELL_OFFSET;
        for (int i = 0; i < 4; i++) {
            int o = i * stride;
            vertices[o] = Float.floatToRawIntBits(Float.intBitsToFloat(vertices[o]) + dx);
            vertices[o + 1] = Float.floatToRawIntBits(Float.intBitsToFloat(vertices[o + 1]) + dy);
            vertices[o + 2] = Float.floatToRawIntBits(Float.intBitsToFloat(vertices[o + 2]) + dz);
        }
        return new BakedQuad(vertices, quad.getTintIndex(), face, quad.getSprite(), quad.isShade());
    }

    private static boolean failGeometry(String reason) {
        geometryFailure = reason;
        if (GEOMETRY_WARN.getAndDecrement() > 0) {
            NanochipModContext.logger().warn("[gtlmodular] NAC 机器外壳罩：几何解析失败 —— {}（本帧起不画罩子，"
                    + "外观 = 现状；下一次资源重载会重试。请把这条日志回传。）", reason);
        }
        return false;
    }

    private static void reportStats(int candidates, int drawn, int excluded) {
        if (candidates == lastCandidates && drawn == lastDrawn && excluded == lastExcluded) {
            framesSinceStats++;
            if (!statsLogged || framesSinceStats < STATS_EVERY_FRAMES) {
                return;
            }
        }
        lastCandidates = candidates;
        lastDrawn = drawn;
        lastExcluded = excluded;
        framesSinceStats = 0;
        statsLogged = true;
        NanochipModContext.logger().debug("[gtlmodular] NAC 机器外壳罩：候选 {} 格 / 本帧绘制 {} 格 / 排除 {} 格"
                        + "（距离上限 {} 格、每帧上限 {} 格、罩子外移 {}；开关 ENABLED={}）",
                candidates, drawn, excluded, (int) MAX_DISTANCE, MAX_BLOCKS_PER_FRAME, SHELL_OFFSET, ENABLED);
    }

    private static void reportOnce(String key, String message) {
        if (ONCE.add(key)) {
            NanochipModContext.logger().warn(message);
        }
    }

    private static void warnFailure(Throwable failure) {
        if (FAILURE_WARN.getAndDecrement() > 0) {
            NanochipModContext.logger().warn("[gtlmodular] NAC 机器外壳罩：渲染路径抛异常 —— 已吞掉（本帧不画罩子，"
                    + "外观 = 现状）。这是外壳罩的 bug，请把下面这条堆栈连同复现步骤回传。", failure);
        }
    }

    public static boolean geometryReady() {
        return variantQuads != null && frame != null;
    }

    public static String geometryFailure() {
        return geometryFailure;
    }

    public static int[] lastStats() {
        return new int[] { lastCandidates, lastDrawn, lastExcluded };
    }

    public static String describeExclusions() {
        List<String> parts = new ArrayList<>();
        parts.add("整台排除 " + EXCLUDED_BLOCKS.size() + " 条 " + EXCLUDED_BLOCKS);
        parts.add("逐面排除 " + EXCLUDED_FACES.size() + " 条 " + EXCLUDED_FACES);
        return String.join("｜", parts);
    }
}
