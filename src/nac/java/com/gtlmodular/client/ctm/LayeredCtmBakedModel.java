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

/** 各层分别应用 CTM，避免单层模型包装只取首个 quad 而丢失发光前景。 */
public final class LayeredCtmBakedModel implements BakedModel {

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
            // 任一面缺 quad 就放弃该分层方案，避免仅画出部分层。
            for (Direction face : Direction.values()) {
                if (view.getQuads(frameState, face, RandomSource.create(42L)).isEmpty()) {
                    return null;
                }
            }
            String prefix = prefixes.get(Math.min(i, prefixes.size() - 1));
            wrappers.add(new ConnectedCtmBakedModel(view, block, prefix, true, frameState, false));
        }
        return new LayeredCtmBakedModel(delegate, block, layers, wrappers, prefixes);
    }

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

    public int layerCount() {
        return this.wrappers.size();
    }

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
        // 各层共用方块、帧和邻居判定，复用首层连接位图以免重复计算。
        return this.wrappers.get(0).getModelData(level, pos, state, data);
    }

    @Override
    public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource rand, ModelData data) {
        return this.delegate.getRenderTypes(state, rand, data);
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
    public ItemTransforms getTransforms() {
        return this.delegate.getTransforms();
    }

    @Override
    public ItemOverrides getOverrides() {
        return this.delegate.getOverrides();
    }

    public Block block() {
        return this.block;
    }

    public List<String> prefixes() {
        return List.copyOf(this.prefixes);
    }

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
