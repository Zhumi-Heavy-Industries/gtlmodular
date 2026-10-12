package com.gtlmodular.client.ctm;

import java.util.ArrayList;
import java.util.List;
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

/**
 * <b>多层模型的 CTM（「底层 + 前景层」两层各自连片）</b> —— 只服务 NH 的 <i>litcoil</i> 前景层那一档。
 *
 * <h2>为什么需要它（NH 的要求 vs 我们既有实现的形状）</h2>
 * NH 的线圈 CTM 是<b>两份独立配置</b>：
 * <pre>
 *   ctm/coils/naq/coil.properties     matchTiles=…MACHINE_COIL_NAQUADAH_BACKGROUND   tiles=0 1 1 0 1
 *   ctm/coils/naq/litcoil.properties  matchTiles=…MACHINE_COIL_NAQUADAH_FOREGROUND  tiles=10 11 11 10 11
 * </pre>
 * ⇒ <b>底层与点亮层各自一套 compact 5 格</b>，两层都要连片。
 *
 * <p>而 1.20.1 里 GTCEu 的线圈正是两层模型：方块状态 {@code active=false} 用 {@code cube_all}（单层 = 底层），
 * {@code active=true} 用 {@code gtceu:block/cube_2_layer/all}（<b>同一个 RenderType 下每面返回 2 个 quad</b>：
 * 第 0 个 = {@code machine_coil_naquadah} 底层，第 1 个 = {@code machine_coil_naquadah_bloom} 前景层）。
 * 既有的 {@link ConnectedCtmBakedModel} 每面只取 {@code quads.get(0)} 再重贴图 ⇒ 直接包上去会把
 * <b>bloom 层整个丢掉</b>（点亮态不再发光）。
 *
 * <h2>做法：把「层」拆成 6 个独立的单层模型，各包一层，再按 RenderType 合并</h2>
 * <ol>
 *   <li>{@link LayerView}：委托模型的「只暴露第 i 个 quad」视图（其余方法全部转发）；</li>
 *   <li>每个层视图外面套一个<b>未改动的</b> {@link ConnectedCtmBakedModel} —— 层 0 用底层前缀，
 *       层 1（及以后）用前景层前缀；</li>
 *   <li>本类只做分发：{@code getQuads(..., renderType)} 把该 RenderType 下各层的 quad
 *       <b>按层序拼接</b>（底层在前、前景在后 ⇒ 前景仍盖在底层上）。</li>
 * </ol>
 *
 * <p><b>为什么另起一个类而不是改 {@link ConnectedCtmBakedModel}</b>：后者是玻璃（已冻结、实机验过）
 * 与机壳共用的路径，任何改动都有回归风险；这里<b>一行都不动它</b>，而本类只在
 * 「登记了前景层前缀 <b>且</b> 该模型确实多于一层」时才被使用
 * （见 {@code CtmClientSetup} 的分支），所以玻璃 / 机壳的行为<b>逐字节不变</b>。
 *
 * <h2>兜底（绝不比现状更糟）</h2>
 * 层拆分失败（某个面在某个层上是空的）、或某个层的变体 sprite 没进图集 ⇒ 该层退回它自己的
 * 第 0 格（{@link ConnectedCtmBakedModel} 自己就有这层兜底）；层视图整体拿不到 quad ⇒
 * {@link #getQuads} 直接转发给原模型。
 */
public final class LayeredCtmBakedModel implements BakedModel {

    /** 一个「层」= (RenderType, 该 RenderType 下面内的第 i 个 quad)。 */
    private record Layer(RenderType renderType, int index) {
    }

    private final BakedModel delegate;
    private final Block block;
    private final List<Layer> layers;
    private final List<ConnectedCtmBakedModel> wrappers;
    private final List<String> prefixes;

    private LayeredCtmBakedModel(BakedModel delegate, Block block, List<Layer> layers,
                                 List<ConnectedCtmBakedModel> wrappers, List<String> prefixes) {
        this.delegate = delegate;
        this.block = block;
        this.layers = layers;
        this.wrappers = wrappers;
        this.prefixes = prefixes;
    }

    /**
     * 试着把 {@code delegate} 拆成多层并各包一层 CTM。
     *
     * @param prefixes 层序 → sprite 前缀（下标 0 = 底层；越界时钳到最后一个，即前景层）
     * @return 拆不出来（&lt;2 层，或某一层有面拿不到 quad）时返回 {@code null} ⇒ 调用方走单层老路
     */
    public static LayeredCtmBakedModel tryCreate(BakedModel delegate, Block block, List<String> prefixes,
                                                 BlockState frameState) {
        List<Layer> layers = planLayers(delegate, frameState);
        if (layers.size() < 2) {
            return null;
        }
        List<ConnectedCtmBakedModel> wrappers = new ArrayList<>(layers.size());
        for (int i = 0; i < layers.size(); i++) {
            Layer layer = layers.get(i);
            LayerView view = new LayerView(delegate, layer.renderType(), layer.index());
            // 层视图每个面都必须有 quad，否则这一层拆不出来 ⇒ 整体放弃（宁可回退原模型，也不丢层）
            for (Direction face : Direction.values()) {
                if (view.getQuads(frameState, face, RandomSource.create(42L)).isEmpty()) {
                    return null;
                }
            }
            String prefix = prefixes.get(Math.min(i, prefixes.size() - 1));
            // ★ 最后一个参数 = NH connect=tile「同贴图组」：线圈是 connect=block ⇒ 恒 false（行为不变）。
            wrappers.add(new ConnectedCtmBakedModel(view, block, prefix, true, frameState, false));
        }
        return new LayeredCtmBakedModel(delegate, block, layers, wrappers, prefixes);
    }

    /**
     * 枚举「层」：先取模型声明的全部 RenderType；每个 RenderType 下按<b>每面最多几个 quad</b> 展开。
     *
     * <p>覆盖两种真实形状：① 两层在同一个 RenderType（GTCEu 的 {@code cube_2_layer}，每面 2 个 quad）；
     * ② 两层分属两个 RenderType（每面各 1 个 quad）。
     */
    private static List<Layer> planLayers(BakedModel delegate, BlockState frameState) {
        List<Layer> out = new ArrayList<>();
        for (RenderType renderType : delegate.getRenderTypes(frameState, RandomSource.create(42L), ModelData.EMPTY)) {
            int max = 0;
            for (Direction face : Direction.values()) {
                max = Math.max(max,
                        delegate.getQuads(frameState, face, RandomSource.create(42L), ModelData.EMPTY, renderType).size());
            }
            for (int i = 0; i < Math.max(1, max); i++) {
                out.add(new Layer(renderType, i));
            }
        }
        return out;
    }

    // ------------------------------------------------------------------
    // 变体解析（照抄 CtmClientSetup 对单层模型的调用形状）
    // ------------------------------------------------------------------

    /** 全部层都就绪才算 true（与 {@link ConnectedCtmBakedModel#resolve} 同义）。 */
    public boolean resolve(Function<ResourceLocation, TextureAtlasSprite> atlas, BlockState frameState) {
        boolean all = true;
        for (ConnectedCtmBakedModel wrapper : this.wrappers) {
            all &= wrapper.resolve(atlas, frameState);
        }
        return all;
    }

    public int missedSprites() {
        int sum = 0;
        for (ConnectedCtmBakedModel wrapper : this.wrappers) {
            sum += wrapper.missedSprites();
        }
        return sum;
    }

    /** 实际命中的 sprite 命名空间（取第一层）。 */
    public String spriteNamespace() {
        return this.wrappers.get(0).spriteNamespace();
    }

    public boolean frameComplete() {
        for (ConnectedCtmBakedModel wrapper : this.wrappers) {
            if (!wrapper.frameComplete()) {
                return false;
            }
        }
        return true;
    }

    /** 日志摘要（与 {@code ConnectedCtmBakedModel.Dump} 同口径的一行）。 */
    public String summary() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < this.wrappers.size(); i++) {
            ConnectedCtmBakedModel.Dump d = this.wrappers.get(i).describe();
            if (sb.length() > 0) {
                sb.append(" + ");
            }
            sb.append(d.spritePrefix()).append(" × ").append(d.variantCount())
                    .append(d.frameComplete() ? "（面内坐标帧 OK）" : "（面内坐标帧未知 ⇒ 回退第 0 格）");
        }
        return sb.toString();
    }

    /** 层数（自检/日志用）。 */
    public int layerCount() {
        return this.wrappers.size();
    }

    // ------------------------------------------------------------------
    // 分发：同 RenderType 的各层按层序拼接（底层在前、前景在后）
    // ------------------------------------------------------------------

    @Override
    public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource rand, ModelData data,
                                    RenderType renderType) {
        if (side == null) {
            return this.delegate.getQuads(state, side, rand, data, renderType);
        }
        List<BakedQuad> out = new ArrayList<>(this.wrappers.size());
        for (int i = 0; i < this.wrappers.size(); i++) {
            if (this.layers.get(i).renderType() != renderType) {
                continue;
            }
            List<BakedQuad> quads = this.wrappers.get(i).getQuads(state, side, rand, data, renderType);
            if (quads != null && !quads.isEmpty()) {
                out.addAll(quads);
            }
        }
        return out.isEmpty() ? this.delegate.getQuads(state, side, rand, data, renderType) : out;
    }

    @Override
    public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData data) {
        // 各层的连接位图完全一致（同一个方块、同一帧、同一套邻居判定）⇒ 取第一层算出来的那份即可
        return this.wrappers.get(0).getModelData(level, pos, state, data);
    }

    @Override
    public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource rand, ModelData data) {
        return this.delegate.getRenderTypes(state, rand, data);
    }

    // ------------------------------------------------------------------
    // 其余一律转发（与 ConnectedCtmBakedModel 同纪律：不改渲染类型/AO/GUI/粒子/物品变换）
    // ------------------------------------------------------------------

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
    public ItemTransforms getTransforms() {
        return this.delegate.getTransforms();
    }

    @Override
    public ItemOverrides getOverrides() {
        return this.delegate.getOverrides();
    }

    /** 供自检/诊断：本模型包的是哪个方块。 */
    public Block block() {
        return this.block;
    }

    /** 层序 → 前缀（自检/诊断用）。 */
    public List<String> prefixes() {
        return List.copyOf(this.prefixes);
    }

    /**
     * <b>「只暴露某一个层的 quad」的委托视图</b>：除 quad 相关方法外全部转发给真正的模型。
     *
     * <p>这样每个层都能直接喂给<b>未改动</b>的 {@link ConnectedCtmBakedModel}
     * （它自己会做「取第 0 个 quad → 重贴图 → 供 52/120 个变体」的全套工作）。
     */
    private static final class LayerView implements BakedModel {

        private final BakedModel delegate;
        private final RenderType renderType;
        private final int index;

        private LayerView(BakedModel delegate, RenderType renderType, int index) {
            this.delegate = delegate;
            this.renderType = renderType;
            this.index = index;
        }

        private static List<BakedQuad> pick(List<BakedQuad> quads, int index) {
            if (quads == null || index >= quads.size()) {
                return List.of();
            }
            return List.of(quads.get(index));
        }

        @Override
        public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource rand) {
            return pick(this.delegate.getQuads(state, side, rand, ModelData.EMPTY, this.renderType), this.index);
        }

        @Override
        public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource rand, ModelData data,
                                        RenderType requested) {
            return pick(this.delegate.getQuads(state, side, rand, data, this.renderType), this.index);
        }

        @Override
        public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData data) {
            return this.delegate.getModelData(level, pos, state, data);
        }

        @Override
        public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource rand, ModelData data) {
            return ChunkRenderTypeSet.of(this.renderType);
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
        public ItemTransforms getTransforms() {
            return this.delegate.getTransforms();
        }

        @Override
        public ItemOverrides getOverrides() {
            return this.delegate.getOverrides();
        }
    }
}
