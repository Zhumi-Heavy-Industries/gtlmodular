package com.gtlmodular.client.renderer.nyarlathotep;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.Objects;

/**
 * 奈亚中央黑日的单条 RenderType / 单套 shader（2026-11-02，0.3.334）。
 *
 * <p>0.3.332/333 试过「core + glow 两遍 / 两个 shader」，实机都只剩黑球（彩色那一遍没出来）。
 * 0.3.334 回到 0.3.331 验证过「能画出来」的单遍普通 alpha：
 * 黑核 alpha 固定 1（不乘 Intensity/vertexColor），彩色用高 alpha 直接盖上去；
 * 调色板仍是 GTO 星云 palette + 1.65 饱和，所以颜色是亮的。
 */
@OnlyIn(Dist.CLIENT)
public final class BlackSunRenderTypes extends RenderType {

    private static ShaderInstance blackSunShader;

    private static final RenderStateShard.ShaderStateShard BLACK_SUN_SHADER =
            new RenderStateShard.ShaderStateShard(
                    () -> Objects.requireNonNull(blackSunShader, "nyarlathotep_black_sun shader not loaded"));

    /** 单遍：普通 alpha、不写深度、不剔除。 */
    public static final RenderType BLACK_SUN = RenderType.create(
            "nyarlathotep_black_sun",
            DefaultVertexFormat.POSITION_TEX_COLOR,
            VertexFormat.Mode.QUADS,
            4096,
            false,
            false,
            RenderType.CompositeState.builder()
                    .setShaderState(BLACK_SUN_SHADER)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(NO_DEPTH_TEST)
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .createCompositeState(false));

    private BlackSunRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize,
                                boolean affectsCrumbling, boolean sortOnUpload,
                                Runnable setupState, Runnable clearState) {
        super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setupState, clearState);
    }

    public static void setShader(ShaderInstance shader) {
        blackSunShader = shader;
    }

    public static ShaderInstance getShader() {
        return blackSunShader;
    }
}
