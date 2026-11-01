package com.gtlmodular.client.renderer.stellar;

import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gtlmodular.GTLModular;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * 恒星炎炀锻炉漩涡特效的<b>客户端入口</b>。由 client setup 调一次：
 * <pre>
 * StellarForgeFxClient.init();            // shader + 三个事件
 * StellarForgeFxClient.attachRenderer();  // 把渲染器挂到 gtceu:stellar_forge 定义上
 * </pre>
 *
 * <p>两个方法都 {@code public} 且<b>可重复调用</b>（内部有幂等保护 / 只是覆盖同一个字段），
 * 方便 Lead 在别处再挂一次而不会重复注册事件。
 *
 * <p>接线：
 * <ul>
 *   <li><b>mod 总线</b>：{@link RegisterShadersEvent} ⇒ 用 {@link ShaderInstance} 编译
 *       {@code gtlmodular:stellar_forge_vortex}，回调交给
 *       {@link GtlRenderTypes#setStellarForgeVortexShader}；</li>
 *   <li><b>game 总线</b>：{@link RenderLevelStageEvent} ⇒ {@link StellarFxManager#dispatch}；</li>
 *   <li><b>game 总线</b>：{@link TickEvent.ClientTickEvent}（END 相位）⇒ {@link StellarFxManager#tick}；</li>
 *   <li><b>game 总线</b>：{@link LevelEvent.Unload} ⇒ {@link StellarFxManager#clear}（释放离屏缓冲）。</li>
 * </ul>
 *
 * <h2>关键细节（踩坑记录）</h2>
 * <ol>
 *   <li>{@link ShaderInstance} 必须用 <b>ResourceLocation 版构造器</b>
 *       （{@code new ShaderInstance(provider, ResourceLocation, format)}）。String 版构造器内部是
 *       {@code new ResourceLocation(name)}，会把 namespace 默认成 {@code minecraft}，
 *       去读 {@code assets/minecraft/shaders/core/...} —— 一定失败。</li>
 *   <li>顶点格式必须是 {@code DefaultVertexFormat.POSITION_COLOR_NORMAL}
 *       （GTO 的 {@code f_166851_}；shader 用 {@code in vec3 Normal} 收「本地坐标」）。</li>
 *   <li>RegisterShadersEvent 属于 <b>IModBusEvent</b> ⇒ 只能挂 mod 总线；
 *       另外三个是 game 事件 ⇒ 只能挂 {@link MinecraftForge#EVENT_BUS}。</li>
 *   <li>{@link #init()} 整体 {@code try/catch(Throwable)}：特效是纯观感功能，
 *       任何一环失败都只记 error 日志，绝不把客户端启动/资源重载带崩。</li>
 * </ol>
 */
@OnlyIn(Dist.CLIENT)
public final class StellarForgeFxClient {

    /** shader 资源位置：{@code assets/gtlmodular/shaders/core/stellar_forge_vortex.{json,vsh,fsh}}。 */
    private static final ResourceLocation VORTEX_SHADER =
            new ResourceLocation(GTLModular.MOD_ID, "stellar_forge_vortex");

    /** 目标机器定义：gtlcore 注册的恒星炎炀锻炉。 */
    private static final ResourceLocation STELLAR_FORGE = new ResourceLocation("gtceu", "stellar_forge");

    /** init() 幂等标记。 */
    private static boolean initialized;

    /** attachRenderer() 的结果标记（只用于日志/自检，不影响可重复调用）。 */
    private static boolean rendererAttached;

    private StellarForgeFxClient() {
    }

    /** 注册 shader 与三个 game 事件。重复调用只会注册一次。 */
    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        try {
            IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
            modBus.addListener((RegisterShadersEvent event) -> onRegisterShaders(event));

            IEventBus gameBus = MinecraftForge.EVENT_BUS;
            gameBus.addListener((RenderLevelStageEvent event) -> StellarFxManager.dispatch(event));
            gameBus.addListener((TickEvent.ClientTickEvent event) -> {
                if (event.phase == TickEvent.Phase.END) {
                    StellarFxManager.tick();
                }
            });
            gameBus.addListener((LevelEvent.Unload event) -> StellarFxManager.clear());

            GTLModular.LOGGER.info("[gtlmodular] 恒星炎炀锻炉漩涡特效：客户端接线完成"
                    + "（RegisterShaders / RenderLevelStage / ClientTick / LevelUnload）");
        } catch (Throwable throwable) {
            GTLModular.LOGGER.error("[gtlmodular] 恒星炎炀锻炉漩涡特效：客户端接线失败，特效将不显示", throwable);
        }
    }

    /**
     * 把 {@link StellarForgeRenderer} 挂到 {@code gtceu:stellar_forge} 的机器定义上。
     *
     * <p>找不到定义（gtlcore 未装）时只 WARN 不抛；重复调用只是再 set 一次同一个字段。
     */
    public static void attachRenderer() {
        try {
            MachineDefinition definition = GTRegistries.MACHINES.get(STELLAR_FORGE);
            if (definition == null) {
                GTLModular.LOGGER.warn("[gtlmodular] 找不到机器定义 {} ⇒ 恒星炎炀锻炉漩涡渲染器未挂载"
                        + "（gtlcore 未加载？）", STELLAR_FORGE);
                return;
            }
            if (!(definition instanceof MultiblockMachineDefinition)) {
                // 理论上不可能（stellar_forge 是多块机器）；不拦，照挂，免得因为类型判定把功能丢掉。
                GTLModular.LOGGER.warn("[gtlmodular] {} 不是 MultiblockMachineDefinition（实为 {}），仍尝试挂渲染器",
                        STELLAR_FORGE, definition.getClass().getName());
            }
            definition.setRenderer(new StellarForgeRenderer());
            rendererAttached = true;
            GTLModular.LOGGER.info("[gtlmodular] 恒星炎炀锻炉漩涡渲染器已挂到 {}（外壳 atomic_casing + 覆层 fusion_reactor，"
                    + "视野 256 格）", STELLAR_FORGE);
        } catch (Throwable throwable) {
            GTLModular.LOGGER.error("[gtlmodular] 挂载恒星炎炀锻炉漩涡渲染器失败（特效将不显示）", throwable);
        }
    }

    /** 供 Lead 自检：init() 是否已执行。 */
    public static boolean isInitialized() {
        return initialized;
    }

    /** 供 Lead 自检：渲染器是否已挂上。 */
    public static boolean isRendererAttached() {
        return rendererAttached;
    }

    // ------------------------------------------------------------------

    /**
     * 编译并登记 {@code gtlmodular:stellar_forge_vortex}。
     *
     * <p>{@code registerShader(ShaderInstance, Consumer<ShaderInstance>)}：第一个参数是编译好的
     * shader，第二个参数在资源重载收尾时回调（把实例交给 {@link GtlRenderTypes}）。
     */
    private static void onRegisterShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(), VORTEX_SHADER,
                            DefaultVertexFormat.POSITION_COLOR_NORMAL),
                    GtlRenderTypes::setStellarForgeVortexShader);
        } catch (Throwable throwable) {
            GTLModular.LOGGER.error("[gtlmodular] 编译 shader {} 失败 ⇒ 漩涡特效不显示", VORTEX_SHADER, throwable);
        }
    }
}
