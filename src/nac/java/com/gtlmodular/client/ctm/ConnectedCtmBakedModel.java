package com.gtlmodular.client.ctm;

import com.gtlmodular.common.data.nanochip.NanochipModContext;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
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

/** getQuads 无法直接访问世界，通过 ModelData 传递邻居信息，避免为连接纹理增加方块状态。 */
public final class ConnectedCtmBakedModel implements BakedModel {

    public static final ModelProperty<Long> CTM_MASK = new ModelProperty<>();

    private static final int FACE_COUNT = 6;
    private static final int BITS_PER_FACE = 8;
    private static final int FACE_MASK = 0xFF;

    private static final int[] ORTHO_BITS = { 0, 2, 4, 6 };
    private static final int[] DIAG_BITS = { 1, 3, 5, 7 };

    private static final int QUADRANTS = 4;

    private final BakedModel delegate;
    private final Block block;
    private final String spritePrefix;
    private final boolean compact;
    private final boolean tileGroup;
    private final int variantCount;
    private final CtmFaceFrame frame;

    private final BakedQuad[] baseQuad = new BakedQuad[FACE_COUNT];
    /** 无法确认平面矩形时不切子块，避免不规则模型丢面。 */
    private final SubQuad[][] subTemplates = new SubQuad[FACE_COUNT][];
    private TextureAtlasSprite baseSprite;

    private volatile List<BakedQuad>[] variantQuads;
    private volatile List<BakedQuad>[] compactSubQuads;
    /** 只有全部 sprite 就绪才锁定结果，避免图集尚未就绪时永久保留缺失贴图。 */
    private volatile boolean resolved;
    private volatile boolean warned;
    private volatile int missed;
    /** 限制渲染期解析次数，避免永久缺失资源造成无限重试。 */
    private static final int MAX_LAZY_ATTEMPTS = 20;
    /** 各模型独立分配诊断额度，避免机壳耗尽额度后玻璃无日志可查。 */
    private final java.util.concurrent.atomic.AtomicInteger diagGetModelData = new java.util.concurrent.atomic.AtomicInteger(4);
    private final java.util.concurrent.atomic.AtomicInteger diagGetQuads = new java.util.concurrent.atomic.AtomicInteger(6);
    private volatile int lazyAttempts;
    private volatile long lastLazyAttemptAt;

    /** 烘焙与图集加载时序可能不同，经渲染期回调补一次解析。 */
    static volatile java.util.function.Consumer<ConnectedCtmBakedModel> lazyResolver;

    /** 尝试资源自身和 minecraft 命名空间，以兼容图集目录源的解析结果。 */
    private volatile String spriteNamespace;

    public ConnectedCtmBakedModel(BakedModel delegate, Block block, String spritePrefix, boolean compact,
                                  BlockState frameState, boolean tileGroup) {
        this.delegate = delegate;
        this.block = block;
        this.spritePrefix = spritePrefix;
        this.compact = compact;
        this.tileGroup = tileGroup;
        this.variantCount = compact ? CtmConnectivity.COMPACT_TILES : CtmConnectivity.CTM_TILES;
        this.frame = CtmFaceFrame.derive(delegate, frameState);
    }

    public Dump describe() {
        return new Dump(this.block, this.spritePrefix, this.variantCount, this.frame.complete());
    }

    public boolean fullyResolved() {
        return this.resolved;
    }

    public int missedSprites() {
        return this.missed;
    }

    public String spriteNamespace() {
        return this.spriteNamespace;
    }

    public boolean frameComplete() {
        return this.frame.complete();
    }

    public record Dump(Block block, String spritePrefix, int variantCount, boolean frameComplete) {
    }

    @Override
    public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData data) {
        if (!this.frame.complete()) {
            return data;
        }
        long packed = 0L;
        for (Direction face : Direction.values()) {
            int mask = computeMask(level, pos, face);
            packed |= (long) (mask & FACE_MASK) << (face.ordinal() * BITS_PER_FACE);
        }
        if (diagGetModelData.getAndDecrement() > 0) {
            NanochipModContext.logger().info("[gtlmodular] CTM 诊断 getModelData：{} @{} 掩码=0x{} 帧完整={}",
                    this.spritePrefix, pos, Long.toHexString(packed).toUpperCase(), this.frame.complete());
        }
        return data.derive().with(CTM_MASK, packed).build();
    }

    private int computeMask(BlockAndTintGetter level, BlockPos pos, Direction face) {
        Direction rightDir = this.frame.rightOf(face);
        Direction upDir = this.frame.upOf(face);
        if (rightDir == null || upDir == null) {
            return 0;
        }
        int mask = 0;
        for (int p : ORTHO_BITS) {
            if (isSame(level, pos, rightDir, upDir, p)) {
                mask |= 1 << p;
            }
        }
        // compact 的象限选择依赖对角，不能套用整面 47 格的对角剪枝。
        if (!this.compact && CtmConnectivity.singletonIndex(mask) >= 0) {
            return mask;
        }
        for (int p : DIAG_BITS) {
            if (isSame(level, pos, rightDir, upDir, p)) {
                mask |= 1 << p;
            }
        }
        return mask;
    }

    private boolean isSame(BlockAndTintGetter level, BlockPos pos, Direction rightDir, Direction upDir, int p) {
        int sr = CtmConnectivity.STEP_RIGHT[p];
        int su = CtmConnectivity.STEP_UP[p];
        int dx = sr * rightDir.getStepX() + su * upDir.getStepX();
        int dy = sr * rightDir.getStepY() + su * upDir.getStepY();
        int dz = sr * rightDir.getStepZ() + su * upDir.getStepZ();
        if (dx == 0 && dy == 0 && dz == 0) {
            return false;
        }
        BlockPos neighbour = pos.offset(dx, dy, dz);
        BlockState state = level.getBlockState(neighbour);
        if (state.is(this.block)) {
            return true;
        }
        if (!this.tileGroup) {
            return false;
        }
        return CtmHullGroup.rendersMeshCasingTile(level, neighbour, state);
    }

    @Override
    public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource rand, ModelData data, RenderType renderType) {
        if (side == null) {
            return this.delegate.getQuads(state, side, rand, data, renderType);
        }
        if (!this.resolved) {
            maybeResolveLazily();
        }
        List<BakedQuad>[] variants = this.variantQuads;
        if (variants == null) {
            return this.delegate.getQuads(state, side, rand, data, renderType);
        }
        Long packed = data == null ? null : data.get(CTM_MASK);
        if (packed == null) {
            if (diagGetQuads.getAndDecrement() > 0) {
                NanochipModContext.logger().info("[gtlmodular] CTM 诊断 getQuads：{} face={} **没拿到 ModelData/掩码** ⇒ 只能退回原模型（第 0 格）",
                        this.spritePrefix, side);
            }
            return this.delegate.getQuads(state, side, rand, data, renderType);
        }
        int mask = (int) ((packed >>> (side.ordinal() * BITS_PER_FACE)) & FACE_MASK);
        // 优先诊断多邻居面，避免额度全用在无法观察连片效果的孤立块上。
        if (Integer.bitCount(mask) >= 3 && diagGetQuads.getAndDecrement() > 0) {
            NanochipModContext.logger().info("[gtlmodular] CTM 诊断(邻居>=3)：{} face={} mask={} => {}｜该面 right={} up={}（全部就绪={}）",
                    this.spritePrefix, side, Integer.toBinaryString(mask), choice(mask),
                    this.frame.has(side) ? this.frame.rightOf(side) : null,
                    this.frame.has(side) ? this.frame.upOf(side) : null, this.resolved);
        }
        if (this.compact) {
            return compactQuads(state, side, rand, data, renderType, mask, variants);
        }
        int index = CtmConnectivity.ctmIndex(mask);
        List<BakedQuad> quads = variants[side.ordinal() * this.variantCount + index];
        return quads != null ? quads : this.delegate.getQuads(state, side, rand, data, renderType);
    }

    private String choice(int mask) {
        if (!this.compact) {
            return "tile " + CtmConnectivity.ctmIndex(mask);
        }
        if (mask == 0x00 || mask == FACE_MASK) {
            return "整面 tile " + CtmConnectivity.compactWholeSprite(mask);
        }
        StringBuilder sb = new StringBuilder("四象限 ");
        for (int hu = 0; hu < 2; hu++) {
            for (int hv = 0; hv < 2; hv++) {
                sb.append(CtmConnectivity.compactQuadrantSprite(mask, hu, hv)).append(' ');
            }
        }
        return sb.toString().trim();
    }

    private List<BakedQuad> compactQuads(BlockState state, Direction side, RandomSource rand, ModelData data,
                                         RenderType renderType, int mask, List<BakedQuad>[] variants) {
        if (mask == 0x00 || mask == FACE_MASK) {
            List<BakedQuad> whole = variants[side.ordinal() * this.variantCount
                    + CtmConnectivity.compactWholeSprite(mask)];
            if (whole != null && !whole.isEmpty()) {
                return whole;
            }
        } else {
            List<BakedQuad>[] subs = this.compactSubQuads;
            if (subs != null) {
                List<BakedQuad> out = new ArrayList<>(QUADRANTS);
                for (int hu = 0; hu < 2; hu++) {
                    for (int hv = 0; hv < 2; hv++) {
                        int v = CtmConnectivity.compactQuadrantSprite(mask, hu, hv);
                        List<BakedQuad> one = subs[(side.ordinal() * this.variantCount + v) * QUADRANTS + hu * 2 + hv];
                        if (one != null && !one.isEmpty()) {
                            out.addAll(one);
                        }
                    }
                }
                if (out.size() == QUADRANTS) {
                    return out;
                }
            }
        }
        // 子块不可用时回到整面或原模型，避免留下空洞。
        List<BakedQuad> fallback = variants[side.ordinal() * this.variantCount
                + CtmConnectivity.compactIndex(mask)];
        return fallback != null ? fallback : this.delegate.getQuads(state, side, rand, data, renderType);
    }

    private void maybeResolveLazily() {
        java.util.function.Consumer<ConnectedCtmBakedModel> resolver = lazyResolver;
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
            resolver.accept(this);
        } catch (Throwable ignored) {
            // CTM 解析失败不能中断正常渲染。
        }
    }

    @SuppressWarnings("unchecked")
    public boolean resolve(java.util.function.Function<ResourceLocation, TextureAtlasSprite> atlas, BlockState frameState) {
        if (this.resolved) {
            return true;
        }
        for (Direction face : Direction.values()) {
            List<BakedQuad> quads = this.delegate.getQuads(frameState, face, RandomSource.create(42L));
            if (quads.isEmpty()) {
                return false;
            }
            this.baseQuad[face.ordinal()] = quads.get(0);
            this.subTemplates[face.ordinal()] = this.compact ? buildSubQuads(quads.get(0)) : null;
        }
        this.baseSprite = this.baseQuad[0].getSprite();

        List<BakedQuad>[] built = new List[FACE_COUNT * this.variantCount];
        TextureAtlasSprite[] chosen = new TextureAtlasSprite[this.variantCount];
        int missing = 0;
        String firstMissing = null;
        TextureAtlasSprite zero = null;
        for (int v = 0; v < this.variantCount; v++) {
            TextureAtlasSprite sprite = lookup(atlas, v);
            if (isMissing(sprite)) {
                missing++;
                if (firstMissing == null) {
                    firstMissing = this.spritePrefix + "/" + v;
                }
                if (zero == null && v != 0) {
                    zero = lookup(atlas, 0);
                }
                sprite = (zero != null && !isMissing(zero)) ? zero : this.baseSprite;
            }
            chosen[v] = sprite;
            for (Direction face : Direction.values()) {
                built[face.ordinal() * this.variantCount + v] =
                        List.of(retexture(this.baseQuad[face.ordinal()], sprite));
            }
        }
        this.missed = missing;
        this.variantQuads = built;
        if (this.compact) {
            this.compactSubQuads = buildCompactSubQuads(chosen);
        }
        if (missing == 0) {
            this.resolved = true;
            this.lazyAttempts = 0;
            return true;
        }
        if (!this.warned) {
            this.warned = true;
            NanochipModContext.logger().warn("[gtlmodular] CTM: {}/{} 张变体 sprite 没在图集里找到（前缀 {}，例：{}）——"
                            + "这些变体暂时退回第 0 格；**图集缝合完成前解析会失败，之后会自动重试**。"
                            + "若一直重试失败，说明变体图没进图集（atlas 声明或缓存问题），把这条日志给我。",
                    missing, this.variantCount, this.spritePrefix, firstMissing);
        }
        return false;
    }

    private TextureAtlasSprite lookup(java.util.function.Function<ResourceLocation, TextureAtlasSprite> atlas, int v) {
        String hit = this.spriteNamespace;
        if (hit != null) {
            return atlas.apply(new ResourceLocation(hit, this.spritePrefix + "/" + v));
        }
        TextureAtlasSprite sprite = atlas.apply(NanochipModContext.assetId(this.spritePrefix + "/" + v));
        if (!isMissing(sprite)) {
            this.spriteNamespace = NanochipModContext.assetNamespace();
            return sprite;
        }
        sprite = atlas.apply(new ResourceLocation("minecraft", this.spritePrefix + "/" + v));
        if (!isMissing(sprite)) {
            this.spriteNamespace = "minecraft";
        }
        return sprite;
    }

    static boolean isMissing(TextureAtlasSprite sprite) {
        return sprite == null || sprite.contents().name().getPath().equals("missingno");
    }

    /** 顶点 UV 是图集坐标，替换 sprite 时必须同步重映射 UV，才能与相邻机壳保持一致。 */
    public static BakedQuad retexture(BakedQuad base, TextureAtlasSprite to) {
        TextureAtlasSprite from = base.getSprite();
        int[] vertices = base.getVertices().clone();
        int stride = vertices.length / 4;
        float u0 = from.getU0();
        float v0 = from.getV0();
        float du = from.getU1() - u0;
        float dv = from.getV1() - v0;
        for (int i = 0; i < 4; i++) {
            int b = i * stride;
            float u = Float.intBitsToFloat(vertices[b + 4]);
            float v = Float.intBitsToFloat(vertices[b + 5]);
            float su = du == 0.0F ? 0.0F : (u - u0) / du;
            float sv = dv == 0.0F ? 0.0F : (v - v0) / dv;
            vertices[b + 4] = Float.floatToRawIntBits(to.getU(snapSixteenth(su) * 16.0D));
            vertices[b + 5] = Float.floatToRawIntBits(to.getV(snapSixteenth(sv) * 16.0D));
        }
        return new BakedQuad(vertices, base.getTintIndex(), base.getDirection(), to, base.isShade());
    }

    private static float snapSixteenth(float s) {
        float t = s * 16.0F;
        float r = Math.round(t);
        return Math.abs(t - r) < 1.0E-3F ? r / 16.0F : s;
    }

    private static final class SubQuad {
        private final float[] pos = new float[12];
        private final float[] st = new float[8];
    }

    /** 按原顶点顺序插值子象限，保持面绕序；几何不满足贴图四角对应关系时不猜测切分。 */
    private static SubQuad[] buildSubQuads(BakedQuad base) {
        int[] data = base.getVertices();
        if (data.length < 32 || data.length % 4 != 0) {
            return null;
        }
        int stride = data.length / 4;
        TextureAtlasSprite from = base.getSprite();
        float u0 = from.getU0();
        float v0 = from.getV0();
        float du = from.getU1() - u0;
        float dv = from.getV1() - v0;
        if (du == 0.0F || dv == 0.0F) {
            return null;
        }
        float[] px = new float[4];
        float[] py = new float[4];
        float[] pz = new float[4];
        int[] ai = new int[4];
        int[] bi = new int[4];
        int[] seen = new int[4];
        for (int i = 0; i < 4; i++) {
            int o = i * stride;
            px[i] = Float.intBitsToFloat(data[o]);
            py[i] = Float.intBitsToFloat(data[o + 1]);
            pz[i] = Float.intBitsToFloat(data[o + 2]);
            float u = Float.intBitsToFloat(data[o + 4]);
            float v = Float.intBitsToFloat(data[o + 5]);
            int a = Math.round((u - u0) / du);
            int b = Math.round((v - v0) / dv);
            if (a < 0 || a > 1 || b < 0 || b > 1) {
                return null;
            }
            ai[i] = a;
            bi[i] = b;
            seen[a * 2 + b]++;
        }
        for (int c : seen) {
            if (c != 1) {
                return null;
            }
        }
        SubQuad[] out = new SubQuad[QUADRANTS];
        for (int hu = 0; hu < 2; hu++) {
            for (int hv = 0; hv < 2; hv++) {
                SubQuad quad = new SubQuad();
                for (int i = 0; i < 4; i++) {
                    float s = (ai[i] == 0) ? 0.5F * hu : 0.5F * hu + 0.5F;
                    float t = (bi[i] == 0) ? 0.5F * hv : 0.5F * hv + 0.5F;
                    quad.st[i * 2] = s;
                    quad.st[i * 2 + 1] = t;
                    float x = 0.0F;
                    float y = 0.0F;
                    float z = 0.0F;
                    for (int j = 0; j < 4; j++) {
                        float w = (ai[j] == 1 ? s : 1.0F - s) * (bi[j] == 1 ? t : 1.0F - t);
                        x += w * px[j];
                        y += w * py[j];
                        z += w * pz[j];
                    }
                    quad.pos[i * 3] = x;
                    quad.pos[i * 3 + 1] = y;
                    quad.pos[i * 3 + 2] = z;
                }
                out[hu * 2 + hv] = quad;
            }
        }
        return out;
    }

    private static BakedQuad bakeSubQuad(BakedQuad base, SubQuad template, TextureAtlasSprite sprite) {
        int[] from = base.getVertices();
        int stride = from.length / 4;
        int[] out = new int[from.length];
        for (int i = 0; i < 4; i++) {
            // 沿用原顶点颜色、光照和法线，避免切成四块后出现明暗接缝。
            System.arraycopy(from, i * stride, out, i * stride, stride);
            out[i * stride] = Float.floatToRawIntBits(template.pos[i * 3]);
            out[i * stride + 1] = Float.floatToRawIntBits(template.pos[i * 3 + 1]);
            out[i * stride + 2] = Float.floatToRawIntBits(template.pos[i * 3 + 2]);
            out[i * stride + 4] = Float.floatToRawIntBits(sprite.getU(template.st[i * 2] * 16.0D));
            out[i * stride + 5] = Float.floatToRawIntBits(sprite.getV(template.st[i * 2 + 1] * 16.0D));
        }
        return new BakedQuad(out, base.getTintIndex(), base.getDirection(), sprite, base.isShade());
    }

    @SuppressWarnings("unchecked")
    private List<BakedQuad>[] buildCompactSubQuads(TextureAtlasSprite[] sprites) {
        for (Direction face : Direction.values()) {
            if (this.subTemplates[face.ordinal()] == null) {
                return null;
            }
        }
        List<BakedQuad>[] subs = new List[FACE_COUNT * this.variantCount * QUADRANTS];
        for (int v = 0; v < this.variantCount; v++) {
            TextureAtlasSprite sprite = sprites[v] != null ? sprites[v] : this.baseSprite;
            for (Direction face : Direction.values()) {
                BakedQuad base = this.baseQuad[face.ordinal()];
                SubQuad[] tpl = this.subTemplates[face.ordinal()];
                for (int q = 0; q < QUADRANTS; q++) {
                    subs[(face.ordinal() * this.variantCount + v) * QUADRANTS + q] =
                            List.of(bakeSubQuad(base, tpl[q], sprite));
                }
            }
        }
        return subs;
    }

    @Override
    public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource rand) {
        return this.delegate.getQuads(state, side, rand);
    }

    @Override
    public boolean useAmbientOcclusion() {
        return this.delegate.useAmbientOcclusion();
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
    public net.minecraft.client.renderer.block.model.ItemTransforms getTransforms() {
        return this.delegate.getTransforms();
    }

    @Override
    public net.minecraft.client.renderer.block.model.ItemOverrides getOverrides() {
        return this.delegate.getOverrides();
    }

    @Override
    public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource rand, ModelData data) {
        return this.delegate.getRenderTypes(state, rand, data);
    }

    public Block block() {
        return this.block;
    }
}
