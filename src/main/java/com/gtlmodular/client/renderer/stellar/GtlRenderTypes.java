package com.gtlmodular.client.renderer.stellar;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * 本模组的自定义 {@link RenderType} 集 —— 对应 GTO {@code com.gtocore.client.renderer.GTORenderTypes}，
 * <b>只保留 {@code STELLAR_FORGE_VORTEX} 这一条</b>（黑曜石/太阳风暴那十几条全部不带）。
 *
 * <p>GTO 原条目的 srg 形态（反编译结果，逐参数对照）：
 * <pre>
 * STELLAR_FORGE_VORTEX = RenderType.create("stellar_forge_vortex",
 *         DefaultVertexFormat.f_166851_,          // = POSITION_COLOR_NORMAL（已核实）
 *         VertexFormat.Mode.TRIANGLES, 262144, false, false,
 *         CompositeState.builder()
 *                 .setTransparencyState(TRANSLUCENT_TRANSPARENCY)   // m_110685_ / f_110139_
 *                 .setDepthTestState(NO_DEPTH_TEST)                 // m_110663_ / f_110111_
 *                 .setCullState(NO_CULL)                           // m_110661_ / f_110110_
 *                 .setShaderState(STELLAR_FORGE_VORTEX_SHADER)      // m_173292_
 *                 .setWriteMaskState(COLOR_WRITE)                   // m_110687_ / f_110115_
 *                 .createCompositeState(false));                    // m_110691_
 * </pre>
 *
 * <h2>与 GTO 的差异</h2>
 * <ol>
 *   <li>namespace 从 {@code gtocore} 换成 {@code gtlmodular}（shader 位置
 *       {@code assets/gtlmodular/shaders/core/stellar_forge_vortex.*}）。</li>
 *   <li>只留一个 {@code ShaderInstance} 字段 + 一组 set/get，删掉 GTO 的
 *       {@code SHADERS} 映射表与其余 9 条 RenderType。</li>
 *   <li>{@code ShaderStateShard} 的取值时机从 GTO 的 {@code Objects.requireNonNull} + 惰性 supplier
 *       保持完全一致 —— 即 shader 未加载时一旦真的去 setupRenderState 就会 fail-fast，
 *       而 {@link StellarForgeVortexFX#flushBatch} 会先判 {@code getShader() != null} 兜住。</li>
 * </ol>
 *
 * <p><b>为什么要 extends {@link RenderType}</b>：{@code TRANSLUCENT_TRANSPARENCY} /
 * {@code NO_DEPTH_TEST} / {@code NO_CULL} / {@code COLOR_WRITE} 都是
 * {@link RenderStateShard} 的 {@code protected static} 字段，只有 {@code RenderType} 的子类
 * 才继承得到（GTO / 增强版核心都是这个写法）。
 *
 * <p><b>专用服务器安全</b>：本类只被 {@code StellarForgeFxClient}（客户端入口）和
 * {@link StellarForgeVortexFX}（客户端 FX）引用，专用服务器上不会被加载。
 */
@OnlyIn(Dist.CLIENT)
public final class GtlRenderTypes extends RenderType {

    /** 漩涡 shader（由 {@code RegisterShadersEvent} 在资源加载后塞进来）。 */
    private static ShaderInstance stellarForgeVortexShader;

    /** 惰性取 shader 的状态片：真正用到时 shader 必须已加载。 */
    private static final RenderStateShard.ShaderStateShard STELLAR_FORGE_VORTEX_SHADER =
            new RenderStateShard.ShaderStateShard(
                    () -> Objects.requireNonNull(stellarForgeVortexShader, "stellar_forge_vortex shader not loaded"));

    /**
     * 漩涡专用 RenderType：三角形、透明混合、<b>关深度测试</b>（用屏幕空间采样覆盖场景）、
     * 不剔除、只写颜色。
     *
     * <p>{@code bufferSize = 262144}（与 GTO 一致）：24 × 96 段 × 6 顶点 ≈ 13824 顶点/实例，
     * 足够放十几个同时存在的漩涡。
     */
    public static final RenderType STELLAR_FORGE_VORTEX = RenderType.create(
            "stellar_forge_vortex",
            DefaultVertexFormat.POSITION_COLOR_NORMAL,
            VertexFormat.Mode.TRIANGLES,
            262144,
            false,
            false,
            RenderType.CompositeState.builder()
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(NO_DEPTH_TEST)
                    .setCullState(NO_CULL)
                    .setShaderState(STELLAR_FORGE_VORTEX_SHADER)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false));

    /**
     * 纯粹为了「合法地继承 RenderType」而存在的构造器 —— 本类不会被实例化，
     * 但 {@link RenderType} 没有无参构造，不写一个显式构造器就编译不过（GTO 同款写法）。
     */
    private GtlRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize,
                           boolean affectsCrumbling, boolean sortOnUpload, Runnable setupState, Runnable clearState) {
        super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setupState, clearState);
    }

    /** 由 {@code RegisterShadersEvent} 的回调调用（参数是 {@code Consumer<ShaderInstance>}）。 */
    public static void setStellarForgeVortexShader(ShaderInstance shader) {
        stellarForgeVortexShader = shader;
    }

    /** shader 未加载时返回 {@code null}（调用方必须判空）。 */
    public static ShaderInstance getStellarForgeVortexShader() {
        return stellarForgeVortexShader;
    }

    /** 供 {@code RegisterShadersEvent} 注册时复用的 shader 状态片（GTO 里是 private，这里公开给客户端入口做自检）。 */
    public static Supplier<ShaderInstance> stellarForgeVortexShaderSupplier() {
        return () -> stellarForgeVortexShader;
    }
}
