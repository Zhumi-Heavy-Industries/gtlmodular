package com.gtlmodular.client.ctm;

import com.gtlmodular.common.data.nanochip.NanochipModContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.ChunkRenderTypeSet;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelProperty;
import net.minecraftforge.registries.ForgeRegistries;

/** 机器几何需从带 ModelData 的委托调用取得；缺少几何或 CTM 数据时必须保留原结果。 */
public final class MachineCtmBakedModel implements BakedModel {

    private static final int FACE_COUNT = 6;
    private static final int FACE_MASK = 0xFF;
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final int[] RING_ORTHO = { 0, 2, 4, 6 };
    private static final int[] RING_DIAG = { 1, 3, 5, 7 };

    private static final int FRAME_UNSET = 0;

    private static int encodeFrame(int rightOrdinal, int upOrdinal) {
        return ((rightOrdinal + 1) << 8) | (upOrdinal + 1);
    }

    private static boolean validOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < FACE_COUNT;
    }

    private static Direction directionOf(int encoded) {
        int ordinal = encoded - 1;
        return validOrdinal(ordinal) ? DIRECTIONS[ordinal] : null;
    }

    private final BakedModel delegate;
    private final Block block;
    private final boolean vacuumHatch;

    private volatile TextureAtlasSprite[] sprites;
    private volatile TextureAtlasSprite hullSide;
    private volatile TextureAtlasSprite hullTop;
    private volatile TextureAtlasSprite hullBottom;

    /** 同一模型可服务不同朝向，帧按 BlockState 缓存；方向对原子发布，避免读取半写状态。 */
    private final ConcurrentHashMap<BlockState, java.util.concurrent.atomic.AtomicIntegerArray> frameCache =
            new ConcurrentHashMap<>();

    private volatile boolean resolved;
    private volatile int missed;
    private volatile int hullMissed;
    static volatile Runnable lazyResolver;

    private volatile int lazyAttempts;
    private volatile long lastLazyAttemptAt;
    private static final int MAX_LAZY_ATTEMPTS = 20;
    private final java.util.concurrent.atomic.AtomicInteger emptyQuadsWarn = new java.util.concurrent.atomic.AtomicInteger(2);
    private static final int EMPTY_QUADS_TOTAL_CAP = 12;
    private static final int EMPTY_QUADS_LOGGED_CAP = 64;
    private static final java.util.concurrent.atomic.AtomicInteger EMPTY_QUADS_TOTAL =
            new java.util.concurrent.atomic.AtomicInteger(EMPTY_QUADS_TOTAL_CAP);
    private static final java.util.Set<String> EMPTY_QUADS_LOGGED =
            java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final java.util.concurrent.atomic.AtomicInteger noFrameWarn = new java.util.concurrent.atomic.AtomicInteger(2);
    private final java.util.concurrent.atomic.AtomicInteger hullMissingWarn = new java.util.concurrent.atomic.AtomicInteger(2);
    private final java.util.concurrent.atomic.AtomicInteger noWorldPosWarn = new java.util.concurrent.atomic.AtomicInteger(1);
    private final java.util.concurrent.atomic.AtomicInteger variantsNotReadyWarn = new java.util.concurrent.atomic.AtomicInteger(1);
    private final java.util.concurrent.atomic.AtomicInteger ctmFailedWarn = new java.util.concurrent.atomic.AtomicInteger(1);

    /** 所有机器使用同一套变体，共享解析结果以避免逐实例重复查询图集。 */
    private static volatile TextureAtlasSprite[] sharedVariants;
    private static volatile int sharedMissed = -1;
    private static volatile long sharedAttemptAt;
    private static final long SHARED_RETRY_MILLIS = 250L;

    /** 限制包装数量，避免 CopyOnWriteArrayList 的反复复制拖慢资源重载。 */
    public static final int MAX_FOREIGN_WRAPPERS = 3000;

    /** 仅当委托对象和方块都相同才复用包装，避免重复分配又不混淆不同模型。 */
    private static final Map<Block, IdentityHashMap<BakedModel, MachineCtmBakedModel>> REUSE = new HashMap<>();

    public static void invalidateSharedVariants() {
        sharedVariants = null;
        sharedMissed = -1;
        sharedAttemptAt = 0L;
        EMPTY_QUADS_TOTAL.set(EMPTY_QUADS_TOTAL_CAP);
        EMPTY_QUADS_LOGGED.clear();
    }

    public static void clearReuse() {
        REUSE.clear();
    }

    static TextureAtlasSprite[] sharedVariants() {
        return sharedVariants;
    }

    /** 外壳罩会缓存带 sprite 引用的 quad，必须全量就绪后才锁定，避免永久保留缺图兜底。 */
    public static boolean sharedVariantsComplete() {
        return sharedVariants != null && sharedMissed == 0;
    }

    static boolean planOnce(BakedModel delegate, Block block) {
        IdentityHashMap<BakedModel, MachineCtmBakedModel> byDelegate =
                REUSE.computeIfAbsent(block, ignored -> new IdentityHashMap<>());
        if (byDelegate.containsKey(delegate)) {
            return false;
        }
        byDelegate.put(delegate, null);
        return true;
    }

    static MachineCtmBakedModel reuseOf(BakedModel delegate, Block block) {
        IdentityHashMap<BakedModel, MachineCtmBakedModel> byDelegate = REUSE.get(block);
        return byDelegate == null ? null : byDelegate.get(delegate);
    }

    static void bindReuse(BakedModel delegate, Block block, MachineCtmBakedModel wrapper) {
        REUSE.computeIfAbsent(block, ignored -> new IdentityHashMap<>()).put(delegate, wrapper);
    }

    public MachineCtmBakedModel(BakedModel delegate, Block block) {
        this.delegate = delegate;
        this.block = block;
        this.vacuumHatch = CtmHullGroup.isVacuumHatch(block);
    }

    public Block block() {
        return this.block;
    }

    public TextureAtlasSprite[] variantSprites() {
        return this.sprites;
    }

    public boolean fullyResolved() {
        return this.resolved;
    }

    public int missedSprites() {
        return this.missed;
    }

    public int missedHullSprites() {
        return this.hullMissed;
    }

    public String summary() {
        return CtmHullGroup.MESH_SPRITE_PREFIX + " × " + CtmConnectivity.CTM_TILES
                + (this.resolved ? "（已就绪）" : "（缺 " + this.missed + " 张）")
                + (this.vacuumHatch ? "｜两态：未并入 ⇒ " + CtmHullGroup.tierCasingSprite(
                        CtmHullGroup.hatchTier(), "side") + "（缺 " + this.hullMissed + "）" : "");
    }

    /** 必须返回委托生成的 ModelData；退回原输入会丢失 LDLib 的渲染器及世界信息，使机器隐形。 */
    @Override
    public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData data) {
        return this.delegate.getModelData(level, pos, state, data);
    }

    /** 先取得委托几何再捕获 CTM 异常，保证失败时总能返回未改写的模型。 */
    @Override
    public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource rand, ModelData data, RenderType renderType) {
        List<BakedQuad> quads = this.delegate.getQuads(state, side, rand, data, renderType);
        try {
            return retexture(state, side, data, quads);
        } catch (Throwable failure) {
            warnCtmFailed(state, side, failure);
            return quads;
        }
    }

    private List<BakedQuad> retexture(BlockState state, Direction side, ModelData data, List<BakedQuad> quads) {
        if (quads == null || quads.isEmpty()) {
            warnEmptyQuads(state, side);
            return quads;
        }
        if (side == null || data == null) {
            return quads;
        }
        BlockAndTintGetter level = null;
        BlockPos pos = null;
        for (ModelProperty<?> property : data.getProperties()) {
            Object value = data.get(property);
            if (level == null && value instanceof BlockAndTintGetter foundLevel) {
                level = foundLevel;
            } else if (pos == null && value instanceof BlockPos foundPos) {
                pos = foundPos;
            }
        }
        if (level == null || pos == null) {
            warnNoWorldPos(state, side, data);
            return quads;
        }
        BakedQuad hull = null;
        for (BakedQuad q : quads) {
            if (isMeshCasingTile(q.getSprite())) {
                hull = q;
                break;
            }
        }
        boolean foreignHull = false;
        if (hull == null) {
            BakedQuad tierQuad = null;
            for (BakedQuad q : quads) {
                if (isTierCasingTile(q.getSprite())) {
                    tierQuad = q;
                    break;
                }
            }
            if (tierQuad == null) {
                return quads;
            }
            if (!NacSlotIndex.isNacSlot(level, pos)) {
                return quads;
            }
            hull = tierQuad;
            foreignHull = true;
        }
        TextureAtlasSprite target = pickSprite(level, pos, side, state, hull);
        if (target == null) {
            return quads;
        }
        // 只换外壳 sprite，避免覆盖正脸、发光层和管道叠加纹理。
        List<BakedQuad> out = new ArrayList<>(quads.size());
        boolean changed = false;
        for (BakedQuad q : quads) {
            if (isMeshCasingTile(q.getSprite()) && q.getSprite() != target) {
                out.add(ConnectedCtmBakedModel.retexture(q, target));
                changed = true;
            } else if (foreignHull && isTierCasingTile(q.getSprite()) && q.getSprite() != target) {
                out.add(ConnectedCtmBakedModel.retexture(q, target));
                changed = true;
            } else {
                out.add(q);
            }
        }
        return changed ? out : quads;
    }

    @Override
    public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource rand) {
        return this.delegate.getQuads(state, side, rand);
    }

    static boolean isMeshCasingTile(TextureAtlasSprite sprite) {
        return sprite != null
                && CtmHullGroup.MESH_BASE_SPRITE.equals(sprite.contents().name().getPath());
    }

    static boolean isTierCasingTile(TextureAtlasSprite sprite) {
        return sprite != null
                && CtmHullGroup.isTierCasingSpritePath(sprite.contents().name().getPath());
    }

    private TextureAtlasSprite pickSprite(BlockAndTintGetter level, BlockPos pos, Direction side, BlockState state,
                                          BakedQuad hullQuad) {
        if (!this.resolved) {
            maybeResolveLazily();
        }
        if (this.vacuumHatch && !CtmHullGroup.isCasingFromStructure(level, pos)) {
            TextureAtlasSprite hullSprite = hullSprite(side);
            if (hullSprite == null) {
                warnHullMissing();
            }
            return hullSprite;
        }
        TextureAtlasSprite[] variants = this.sprites;
        if (variants == null) {
            variants = sharedVariants();
        }
        if (variants == null) {
            warnVariantsNotReady(state, side);
            return null;
        }
        int frame = ensureFrame(state, side, hullQuad);
        if (frame == FRAME_UNSET) {
            return null;
        }
        int mask = computeMask(level, pos, side, frame);
        if (mask < 0) {
            return null;
        }
        TextureAtlasSprite sprite = variants[CtmConnectivity.ctmIndex(mask)];
        return sprite != null ? sprite : null;
    }

    private TextureAtlasSprite hullSprite(Direction side) {
        if (side == Direction.UP) {
            return this.hullTop;
        }
        if (side == Direction.DOWN) {
            return this.hullBottom;
        }
        return this.hullSide;
    }

    /** 一次原子写入完整方向对，避免网格化线程拿到半写的方向序号。 */
    private int ensureFrame(BlockState state, Direction side, BakedQuad hullQuad) {
        java.util.concurrent.atomic.AtomicIntegerArray frames =
                this.frameCache.computeIfAbsent(state, ignored -> new java.util.concurrent.atomic.AtomicIntegerArray(FACE_COUNT));
        int slot = side.ordinal();
        int packed = frames.get(slot);
        if (packed != FRAME_UNSET) {
            return packed;
        }
        int[] derived = CtmFaceFrame.deriveFace(hullQuad, side);
        if (derived == null || !validOrdinal(derived[0]) || !validOrdinal(derived[1])) {
            if (this.noFrameWarn.getAndDecrement() > 0) {
                NanochipModContext.logger().warn("[gtlmodular] CTM: 机器 {} 的面 {} 推不出面内坐标帧（几何不满足判据）"
                        + "—— 该面退回原贴图（不连），不会变空。", this.block, side);
            }
            return FRAME_UNSET;
        }
        frames.set(slot, encodeFrame(derived[0], derived[1]));
        return frames.get(slot);
    }

    private int computeMask(BlockAndTintGetter level, BlockPos pos, Direction face, int packedFrame) {
        Direction rightDir = directionOf((packedFrame >> 8) & FACE_MASK);
        Direction upDir = directionOf(packedFrame & FACE_MASK);
        if (rightDir == null || upDir == null) {
            return -1;
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

    private boolean connects(BlockAndTintGetter level, BlockPos pos, Direction rightDir, Direction upDir, int p) {
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

    public boolean resolve(Function<ResourceLocation, TextureAtlasSprite> atlas) {
        if (this.resolved) {
            return true;
        }
        TextureAtlasSprite[] built = resolveSharedVariants(atlas);
        this.sprites = built;
        this.missed = sharedMissed;
        if (this.vacuumHatch) {
            int tier = CtmHullGroup.hatchTier();
            CtmHullGroup.warnIfTierCasingMissing(tier);
            TextureAtlasSprite[] hull = new TextureAtlasSprite[3];
            String[] faces = { "side", "top", "bottom" };
            int hullMissing = 0;
            for (int i = 0; i < faces.length; i++) {
                TextureAtlasSprite sprite = lookup(atlas, CtmHullGroup.tierCasingSprite(tier, faces[i]));
                if (ConnectedCtmBakedModel.isMissing(sprite)) {
                    hullMissing++;
                    sprite = null;
                }
                hull[i] = sprite;
            }
            this.hullSide = hull[0];
            this.hullTop = hull[1];
            this.hullBottom = hull[2];
            this.hullMissed = hullMissing;
        }
        if (this.missed == 0) {
            this.resolved = true;
            this.lazyAttempts = 0;
            return true;
        }
        return false;
    }

    /** 共享并节流图集查询，避免未就绪时每个包装实例各重试整套变体。 */
    public static TextureAtlasSprite[] resolveSharedVariants(Function<ResourceLocation, TextureAtlasSprite> atlas) {
        TextureAtlasSprite[] cached = sharedVariants;
        long now = System.currentTimeMillis();
        if (cached != null && (sharedMissed == 0 || now - sharedAttemptAt < SHARED_RETRY_MILLIS)) {
            return cached;
        }
        sharedAttemptAt = now;
        TextureAtlasSprite[] built = new TextureAtlasSprite[CtmConnectivity.CTM_TILES];
        TextureAtlasSprite zero = null;
        int missing = 0;
        for (int v = 0; v < built.length; v++) {
            TextureAtlasSprite sprite = lookup(atlas, CtmHullGroup.MESH_SPRITE_PREFIX + "/" + v);
            if (ConnectedCtmBakedModel.isMissing(sprite)) {
                missing++;
                if (zero == null && v != 0) {
                    zero = lookup(atlas, CtmHullGroup.MESH_SPRITE_PREFIX + "/0");
                }
                sprite = (zero != null && !ConnectedCtmBakedModel.isMissing(zero)) ? zero : null;
            }
            built[v] = sprite;
        }
        sharedMissed = missing;
        sharedVariants = built; // 填满数组后再发布，避免读方看到半初始化的 sprite 表。
        return built;
    }

    private static TextureAtlasSprite lookup(Function<ResourceLocation, TextureAtlasSprite> atlas, String path) {
        if (path == null) {
            return null;
        }
        TextureAtlasSprite sprite = atlas.apply(NanochipModContext.assetId(path));
        if (!ConnectedCtmBakedModel.isMissing(sprite)) {
            return sprite;
        }
        return atlas.apply(new ResourceLocation("minecraft", path));
    }

    private void maybeResolveLazily() {
        Runnable resolver = lazyResolver;
        if (resolver == null || this.lazyAttempts >= MAX_LAZY_ATTEMPTS) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - this.lastLazyAttemptAt < 1000L) {
            return;
        }
        this.lastLazyAttemptAt = now;
        this.lazyAttempts++;
        try {
            resolver.run();
        } catch (Throwable ignored) {
        }
    }

    private void warnEmptyQuads(BlockState state, Direction side) {
        if (this.emptyQuadsWarn.getAndDecrement() > 0 && allowEmptyQuadsWarn()) {
            NanochipModContext.logger().warn("[gtlmodular] CTM: 【delegate 空表】机器 {} 的 delegate 返回了 null/空 quads"
                            + "（state={} face={}）—— 已**原样转发**。★ 2026-09-18 实机已证明：**本路径对机器无效**"
                            + "（机器由 LDLib 的 IRenderer 逐 tile 绘制，烘焙 quad 恒为空表，见本类类注释的字节码证据）"
                            + "⇒ 机器外壳改由 NacMachineShellRenderer（RenderLevelStageEvent.AFTER_SOLID_BLOCKS）"
                            + "自己叠一层壳；本类保留是给「未来某条真给 quad 的渲染路径」用的。"
                            + "本 WARN 现为双层限流（每实例 {} 条 + 每方块名一条 / 全局最多 {} 条）。",
                    this.block, state, side, EMPTY_QUADS_LOGGED_CAP, EMPTY_QUADS_TOTAL_CAP);
        }
    }

    /** 单实例限流拦不住大量不同机器同时告警，因此还需方块去重与全局额度。 */
    private boolean allowEmptyQuadsWarn() {
        if (EMPTY_QUADS_TOTAL.getAndDecrement() <= 0) {
            return false;
        }
        String name = String.valueOf(ForgeRegistries.BLOCKS.getKey(this.block));
        if (EMPTY_QUADS_LOGGED.size() >= EMPTY_QUADS_LOGGED_CAP) {
            return false;
        }
        return EMPTY_QUADS_LOGGED.add(name);
    }

    private void warnNoWorldPos(BlockState state, Direction side, ModelData data) {
        if (data.getProperties().isEmpty()) {
            return;
        }
        if (this.noWorldPosWarn.getAndDecrement() > 0) {
            NanochipModContext.logger().warn("[gtlmodular] CTM: 【我们主动放弃】机器 {} 的 ModelData 里有 {} 个属性，"
                            + "但按类型找不到世界/坐标（state={} face={}）—— 本次不换 sprite，"
                            + "**原样返回 delegate 的 quads**（外观 = 现状，绝不会变空）。",
                    this.block, data.getProperties().size(), state, side);
        }
    }

    private void warnVariantsNotReady(BlockState state, Direction side) {
        if (this.variantsNotReadyWarn.getAndDecrement() > 0) {
            NanochipModContext.logger().warn("[gtlmodular] CTM: 【我们主动放弃】机器 {} 的 47 格变体 sprite 还没解析出来"
                            + "（state={} face={}）—— 本次保持接口机壳原贴图（不会变空）；若是资源缺失，"
                            + "启动日志里应同时有 CtmClientSetup 的「尚未就绪」那条。",
                    this.block, state, side);
        }
    }

    private void warnCtmFailed(BlockState state, Direction side, Throwable failure) {
        if (this.ctmFailedWarn.getAndDecrement() > 0) {
            NanochipModContext.logger().warn("[gtlmodular] CTM: 【兜底 catch】机器 {} 的连接纹理计算抛异常"
                            + "（state={} face={}）—— 已吞掉并**原样返回 delegate 的 quads**（不会让区块渲染失败）；"
                            + "这是 CTM 的 bug，请把下面这条堆栈连同复现步骤回传。",
                    this.block, state, side, failure);
        }
    }

    private void warnHullMissing() {
        if (this.hullMissingWarn.getAndDecrement() > 0) {
            NanochipModContext.logger().warn("[gtlmodular] CTM: 真空仓 {} 的 tier 机壳外壳 sprite 没在图集里找到"
                            + "（推导路径 {}）—— 该仓这一面保持接口机壳外观（不会变空）。",
                    this.block, CtmHullGroup.tierCasingSprite(CtmHullGroup.hatchTier(), "side"));
        }
    }

    @Override
    public boolean useAmbientOcclusion() {
        return this.delegate.useAmbientOcclusion();
    }

    @Override
    public boolean useAmbientOcclusion(BlockState state) {
        return this.delegate.useAmbientOcclusion(state);
    }

    @Override
    public boolean isGui3d() {
        return this.delegate.isGui3d();
    }

    @Override
    public boolean usesBlockLight() {
        return this.delegate.usesBlockLight();
    }

    @Override
    public boolean isCustomRenderer() {
        return this.delegate.isCustomRenderer();
    }

    @Override
    public TextureAtlasSprite getParticleIcon() {
        return this.delegate.getParticleIcon();
    }

    @Override
    public TextureAtlasSprite getParticleIcon(ModelData data) {
        return this.delegate.getParticleIcon(data);
    }

    @Override
    public ItemTransforms getTransforms() {
        return this.delegate.getTransforms();
    }

    @Override
    public ItemOverrides getOverrides() {
        return this.delegate.getOverrides();
    }

    @Override
    public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource rand, ModelData data) {
        return this.delegate.getRenderTypes(state, rand, data);
    }
}
