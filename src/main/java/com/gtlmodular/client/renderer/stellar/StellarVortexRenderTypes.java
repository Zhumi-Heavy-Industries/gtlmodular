package com.gtlmodular.client.renderer.stellar;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * 「恒星炎炀锻炉」星云层（光影兼容版）的 RenderType。
 *
 * <p>用<b>原版</b> {@code position_color_tex} shader（{@code POSITION_COLOR_TEX_SHADER}）+
 * 加性混合（{@code SRC_ALPHA, ONE}）+ 不写深度 + 不剔除。纹理由调用方在
 * {@code endBatch} 前用 {@code RenderSystem.setShaderTexture(0, …)} 绑定
 * （本 RenderType 不带 TextureState，因此不会覆盖手动绑定）。
 *
 * <p>为什么要 extends {@link RenderType}：{@code NO_DEPTH_TEST} / {@code COLOR_WRITE} /
 * {@code NO_CULL} / {@code TransparencyStateShard} 都是 {@link RenderStateShard} 的
 * {@code protected static} 成员，只有 {@code RenderType} 的子类才继承得到
 * （{@code ShanhaiInstabilityRenderTypes} 同款写法）。
 */
@OnlyIn(Dist.CLIENT)
public final class StellarVortexRenderTypes extends RenderType {

    /** 加性混合。 */
    private static final TransparencyStateShard ADDITIVE_TRANSPARENCY =
            new TransparencyStateShard("gtlmodular_stellar_vortex_additive",
                    () -> {
                        RenderSystem.enableBlend();
                        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
                                GlStateManager.DestFactor.ONE);
                    },
                    () -> {
                        RenderSystem.disableBlend();
                        RenderSystem.defaultBlendFunc();
                    });

    /** 星云盘：POSITION_TEX_COLOR + 加性 + 不写深度 + 不剔除。 */
    public static final RenderType NEBULA = RenderType.create(
            "gtlmodular_stellar_vortex_nebula",
            DefaultVertexFormat.POSITION_TEX_COLOR,
            VertexFormat.Mode.QUADS,
            262144,
            false,
            false,
            RenderType.CompositeState.builder()
                    .setShaderState(POSITION_COLOR_TEX_SHADER)
                    .setTransparencyState(ADDITIVE_TRANSPARENCY)
                    .setDepthTestState(NO_DEPTH_TEST)
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .createCompositeState(false));

    /** 只为「合法继承 RenderType」而存在；本类不会被实例化。 */
    private StellarVortexRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize,
                                     boolean affectsCrumbling, boolean sortOnUpload,
                                     Runnable setupState, Runnable clearState) {
        super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setupState, clearState);
    }
}
