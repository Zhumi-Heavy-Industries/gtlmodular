package com.gtlmodular.client.renderer.preview;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.opengl.GL11;

/**
 * 两条通用 {@link RenderType}：一个不透明黑核、一条加性发光层。
 *
 * <p>2026-11-02（0.3.328）起这是<b>共享工具类</b>：
 * <ul>
 *   <li>{@code client.renderer.nyarlathotep.BlackSunRenderer}（奈亚中央黑日）用它画黑核 + 光环；</li>
 *   <li>{@code client.renderer.stellar.StellarForgeFxClient}（恒星炎炀锻炉的几何兜底）用它画加性几何。</li>
 * </ul>
 *
 * <h2>两条层</h2>
 * <ol>
 *   <li>{@link #BLACK_CORE}：纯黑、<b>关闭混合</b>、写颜色+深度、深度测试 LEQUAL、不剔除
 *       （{@code POSITION_COLOR} / QUADS / buffer 32768 / {@code getPositionColorShader}）。
 *       黑核必须<b>独立写深度</b>，绝不能并进下面那条加性层 —— 否则黑球会变成“不存在”（加性混黑=0）。</li>
 *   <li>{@link #GLOW}：{@code POSITION_COLOR} / QUADS / buffer 262144 / <b>加性混合</b>
 *       （{@code SRC_ALPHA, ONE}）/ 深度测试 LEQUAL / <b>不写深度</b>（{@code COLOR_WRITE}）/ 不剔除。</li>
 * </ol>
 *
 * <p><b>来源说明</b>：两条层的状态组合最初是按 {@code dishanhai/gtl_shanhai-dishanhai}
 * 的 {@code HostFxDraw#beamLayer() / blackCoreLayer()} 语义对齐写的（本类代码为自写，
 * 不是逐字节拷贝）；0.3.328 删除山海几何后，仅保留本工具类，声明见
 * {@code NOTICE_第三方素材_GTO.md} §7。
 *
 * <p><b>为什么 extends {@link RenderType}</b>：{@code NO_CULL} / {@code COLOR_WRITE} /
 * {@code LEQUAL_DEPTH_TEST} 等状态片是 {@link RenderStateShard} 的 {@code protected static} 字段，
 * 只有 {@code RenderType} 的子类才继承得到。
 *
 * <p><b>专用服务器安全</b>：本类带 {@link OnlyIn}，只在客户端分支被加载。
 */
@OnlyIn(Dist.CLIENT)
public final class ShanhaiInstabilityRenderTypes extends RenderType {

    /** 加性混合的状态片（{@code SRC_ALPHA, ONE}；清理时恢复默认）。 */
    private static final TransparencyStateShard ADDITIVE_TRANSPARENCY =
            new TransparencyStateShard("gtlmodular_shanhai_additive",
                    () -> {
                        RenderSystem.enableBlend();
                        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
                                GlStateManager.DestFactor.ONE);
                    },
                    () -> {
                        RenderSystem.disableBlend();
                        RenderSystem.defaultBlendFunc();
                    });

    /** 黑日视界球：纯黑、关混合、写颜色+深度。 */
    public static final RenderType BLACK_CORE = RenderType.create(
            "gtlmodular_shanhai_black_sun_core",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS,
            32768,
            false,
            false,
            RenderType.CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER)
                    .setTransparencyState(NO_TRANSPARENCY)
                    .setDepthTestState(new DepthTestStateShard("lequal", GL11.GL_LEQUAL))
                    .setWriteMaskState(new WriteMaskStateShard(true, true))
                    .setCullState(NO_CULL)
                    .createCompositeState(false));

    /** 加性发光层：表面残影 / 电弧 / 黑日光晕 / 密文全走这一条。 */
    public static final RenderType GLOW = RenderType.create(
            "gtlmodular_shanhai_instability_glow",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS,
            262144,
            false,
            false,
            RenderType.CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER)
                    .setTransparencyState(ADDITIVE_TRANSPARENCY)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    // 只写颜色、不写深度：加性层之间不应互相遮挡（同上游 beamLayer）。
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .createCompositeState(false));

    /** 只为「合法继承 RenderType」而存在；本类不会被实例化（{@code GtlRenderTypes} 同款）。 */
    private ShanhaiInstabilityRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize,
                                          boolean affectsCrumbling, boolean sortOnUpload,
                                          Runnable setupState, Runnable clearState) {
        super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setupState, clearState);
    }
}
