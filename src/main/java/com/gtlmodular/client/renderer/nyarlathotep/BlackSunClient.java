package com.gtlmodular.client.renderer.nyarlathotep;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gtlmodular.GTLModular;
import com.gtlmodular.common.config.GTLModularConfig;
import com.gtlmodular.common.data.tentacle.NyarlathotepsTentacleMachines;
import com.gtlmodular.common.machine.tentacle.NyarlathotepsTentacleMachine;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 奈亚拉托提普之触 · 正中心黑日的客户端入口（2026-11-02，0.3.328）。
 *
 * <h2>它是怎么工作的</h2>
 * <ol>
 *   <li>{@link TickEvent.ClientTickEvent}：每 {@code scanIntervalTicks} 起一轮，
 *       以玩家所在 16³ 区段为单位扫 {@code scanRadius} 格内<b>已加载</b>区段
 *       （{@code getChunkNow}，不请求加载），命中控制器就缓存朝向/成型/运行状态；</li>
 *   <li>{@link RenderLevelStageEvent}（{@code AFTER_CUTOUT_BLOCKS}）：
 *       把本帧的黑日实例交给 {@link BlackSunRenderer#drawAll} —— 抓屏 + 自定义 shader
 *       一次画完（与恒星炎炀锻炉的屏幕空间特效同一套做法，不是加性几何）；</li>
 *   <li>{@code RegisterShadersEvent}（mod 总线）：注册 {@code gtlmodular:nyarlathotep_black_sun}。</li>
 * </ol>
 *
 * <p>0.3.328 起上一版的五层不稳定特效 + 五个千面相全部移除；机器外观渲染器也不再替换
 * （{@code attachRenderer} 已删），本体继续用定义里的 {@code .workableCasingRenderer(...)}。
 */
@OnlyIn(Dist.CLIENT)
public final class BlackSunClient {

    /** shader 位置：assets/gtlmodular/shaders/core/nyarlathotep_black_sun.*。 */
    private static final ResourceLocation BLACK_SUN_SHADER =
            new ResourceLocation("gtlmodular", "nyarlathotep_black_sun");

    private static final int SECTION_SIZE = 16;
    private static final int SECTIONS_PER_TICK = 16;
    private static final int SWEEP_LOG_EVERY = 20;

    private static int scanRadius;
    private static int sectionRadius;
    private static int sectionsPerAxis;
    private static int sectionsTotal;

    private static final Map<BlockPos, CachedController> CONTROLLERS = new HashMap<>();
    private static final Set<BlockPos> SEEN_THIS_SWEEP = new HashSet<>();

    private record CachedController(BlockPos pos, Direction front, Direction upwards,
                                    boolean flipped, boolean formed, boolean active) {
    }

    private static boolean initialized;
    private static boolean sweeping;
    private static int sectionCursor;
    private static int baseSectionX;
    private static int baseSectionY;
    private static int baseSectionZ;
    private static int ticks;
    private static int sweeps;

    private static boolean loggedFirstDraw;
    private static boolean loggedRenderFailure;
    private static boolean loggedScanFailure;

    private static Block controllerBlockCache;

    private BlackSunClient() {
    }

    /** 注册 shader + 扫描/绘制/卸载事件 + F10 开关。重复调用只注册一次。 */
    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        try {
            IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
            modBus.addListener(BlackSunClient::onRegisterShaders);
        } catch (Throwable throwable) {
            GTLModular.LOGGER.error("[gtlmodular] 奈亚中央黑日：shader 注册接线失败，黑日将不显示", throwable);
        }
        try {
            IEventBus gameBus = MinecraftForge.EVENT_BUS;
            gameBus.addListener((TickEvent.ClientTickEvent event) -> onClientTick(event));
            gameBus.addListener((RenderLevelStageEvent event) -> onRenderLevelStage(event));
            gameBus.addListener((LevelEvent.Unload event) -> onLevelUnload());
            com.gtlmodular.client.keybind.InstabilityToggleKeybind.init();
            GTLModular.LOGGER.info("[gtlmodular] 奈亚中央黑日：客户端接线完成"
                    + "（RegisterShaders 注册屏幕空间 black sun shader + ClientTick 扫描控制器 + "
                    + "cutout 抄相机 → AFTER_LEVEL 抓屏绘制；不是加性几何；F10 总开关）");
        } catch (Throwable throwable) {
            GTLModular.LOGGER.error("[gtlmodular] 奈亚中央黑日：客户端事件接线失败，黑日将不显示", throwable);
        }
    }

    private static void onRegisterShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(), BLACK_SUN_SHADER,
                            DefaultVertexFormat.POSITION_TEX_COLOR),
                    BlackSunRenderTypes::setShader);
            GTLModular.LOGGER.info("[gtlmodular] 奈亚中央黑日：已注册 shader {}", BLACK_SUN_SHADER);
        } catch (Throwable throwable) {
            GTLModular.LOGGER.error("[gtlmodular] 奈亚中央黑日：编译 shader {} 失败 ⇒ 黑日不显示",
                    BLACK_SUN_SHADER, throwable);
        }
    }

    /** 自检用：当前缓存的控制器数量。 */
    public static int cachedControllerCount() {
        return CONTROLLERS.size();
    }

    // ------------------------------------------------------------------
    // ① 扫描
    // ------------------------------------------------------------------

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        try {
            ticks++;
            if (!GTLModularConfig.isNyarlathotepFxEnabled()) {
                sweeping = false;
                if (!CONTROLLERS.isEmpty()) {
                    CONTROLLERS.clear();
                }
                return;
            }
            int interval = Math.max(1, GTLModularConfig.getNyarlathotepFxScanIntervalTicks());
            if (!sweeping) {
                if (ticks % interval != 0) {
                    return;
                }
                if (!beginSweep()) {
                    return;
                }
            }
            for (int i = 0; i < SECTIONS_PER_TICK && sweeping; i++) {
                scanNextSection();
            }
        } catch (Throwable failure) {
            sweeping = false;
            if (!loggedScanFailure) {
                loggedScanFailure = true;
                GTLModular.LOGGER.error("[gtlmodular] 奈亚中央黑日：控制器扫描失败，本次会话不再报错", failure);
            }
        }
    }

    private static boolean beginSweep() {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.player == null) {
            return false;
        }
        scanRadius = Math.max(SECTION_SIZE, GTLModularConfig.getNyarlathotepFxScanRadius());
        sectionRadius = Math.max(1, scanRadius / SECTION_SIZE);
        sectionsPerAxis = sectionRadius * 2 + 1;
        sectionsTotal = sectionsPerAxis * sectionsPerAxis * sectionsPerAxis;
        BlockPos eye = minecraft.player.blockPosition();
        baseSectionX = eye.getX() >> 4;
        baseSectionY = eye.getY() >> 4;
        baseSectionZ = eye.getZ() >> 4;
        sectionCursor = 0;
        SEEN_THIS_SWEEP.clear();
        sweeping = true;
        return true;
    }

    private static void scanNextSection() {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            sweeping = false;
            return;
        }
        Block controller = controllerBlock();
        if (controller == null) {
            sweeping = false;
            return;
        }
        int cursor = sectionCursor++;
        int sideSq = sectionsPerAxis * sectionsPerAxis;
        int sx = cursor / sideSq;
        int sy = (cursor % sideSq) / sectionsPerAxis;
        int sz = cursor % sectionsPerAxis;
        int sectionX = baseSectionX + sx - sectionRadius;
        int sectionY = baseSectionY + sy - sectionRadius;
        int sectionZ = baseSectionZ + sz - sectionRadius;

        LevelChunk chunk = level.getChunkSource().getChunkNow(sectionX, sectionZ);
        if (chunk != null && !chunk.isEmpty()) {
            LevelChunkSection[] sections = chunk.getSections();
            int index = (sectionY * SECTION_SIZE - chunk.getMinBuildHeight()) >> 4;
            LevelChunkSection section = index >= 0 && index < sections.length ? sections[index] : null;
            if (section != null && !section.hasOnlyAir()) {
                int originX = sectionX << 4, originY = sectionY << 4, originZ = sectionZ << 4;
                for (int y = 0; y < SECTION_SIZE; y++) {
                    for (int z = 0; z < SECTION_SIZE; z++) {
                        for (int x = 0; x < SECTION_SIZE; x++) {
                            BlockState state = section.getBlockState(x, y, z);
                            if (state.getBlock() != controller) {
                                continue;
                            }
                            remember(level, new BlockPos(originX + x, originY + y, originZ + z));
                        }
                    }
                }
            }
        }
        finishSweepIfDone();
    }

    private static void remember(ClientLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof IMachineBlockEntity holder)) {
            return;
        }
        if (!(holder.getMetaMachine() instanceof NyarlathotepsTentacleMachine machine)) {
            return;
        }
        SEEN_THIS_SWEEP.add(pos);
        CONTROLLERS.put(pos, new CachedController(pos, machine.getFrontFacing(), machine.getUpwardsFacing(),
                machine.isFlipped(), machine.isFormed(), machine.isActive()));
    }

    private static void finishSweepIfDone() {
        if (sectionCursor < sectionsTotal) {
            return;
        }
        sweeping = false;
        CONTROLLERS.keySet().removeIf(pos -> !SEEN_THIS_SWEEP.contains(pos));
        SEEN_THIS_SWEEP.clear();
        sweeps++;
        if (sweeps == 1 || sweeps % SWEEP_LOG_EVERY == 0) {
            GTLModular.LOGGER.debug("[gtlmodular] 奈亚中央黑日：扫描到 {} 台控制器（近 {} 格 / {} 区段）",
                    CONTROLLERS.size(), scanRadius, sectionsTotal);
        }
    }

    private static Block controllerBlock() {
        if (controllerBlockCache != null) {
            return controllerBlockCache;
        }
        try {
            MultiblockMachineDefinition definition = NyarlathotepsTentacleMachines.NYARLATHOTEPS_TENTACLE;
            if (definition == null) {
                return null;
            }
            controllerBlockCache = definition.getBlock();
        } catch (Throwable ignored) {
            return null;
        }
        return controllerBlockCache;
    }

    private static void onLevelUnload() {
        CONTROLLERS.clear();
        SEEN_THIS_SWEEP.clear();
        sweeping = false;
        sectionCursor = 0;
        controllerBlockCache = null;
        BlackSunCapture.release();
    }

    // ------------------------------------------------------------------
    // ② 绘制
    // ------------------------------------------------------------------

    private static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getCamera() == null) {
            return;
        }
        if (!GTLModularConfig.isNyarlathotepFxEnabled()) {
            return;
        }
        // ★ 0.3.329：分两段。
        //   AFTER_CUTOUT_BLOCKS：世界渲染阶段，PoseStack/Camera 还有效 ⇒ 抄下相机基向量/位置/3D 投影；
        //   AFTER_LEVEL：Iris 已经合成完 ⇒ 用快照画，光影下也能看见（与恒星锻炉的 0.3.327 修法同口径）。
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS) {
            BlackSunRenderer.captureFrame(event.getCamera(), event.getProjectionMatrix());
            return;
        }
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            return;
        }
        if (CONTROLLERS.isEmpty()) {
            return;
        }
        try {
            Minecraft minecraft = Minecraft.getInstance();
            ClientLevel level = minecraft.level;
            if (level == null || event.getLevelRenderer() == null) {
                return;
            }
            Vec3 eye = event.getCamera().getPosition();
            int drawDistance = Math.max(64, GTLModularConfig.getNyarlathotepFxDrawDistance());
            double maxDistanceSq = (double) drawDistance * drawDistance;
            List<BlackSunRenderer.Target> targets = new ArrayList<>(CONTROLLERS.size());
            for (CachedController controller : CONTROLLERS.values()) {
                if (!controller.formed()) {
                    continue;
                }
                BlockPos pos = controller.pos();
                if (eye.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) > maxDistanceSq) {
                    continue;
                }
                targets.add(new BlackSunRenderer.Target(controller.pos(), controller.front(),
                        controller.upwards(), controller.flipped(), controller.active()));
            }
            if (targets.isEmpty()) {
                return;
            }
            if (!BlackSunRenderer.shaderReady()) {
                return; // 渲染器里有一次 warn
            }
            if (!BlackSunRenderer.hasFrame()) {
                return; // cutout 阶段没抓到相机（资源加载中/被别的模组改事件）
            }
            if (!loggedFirstDraw) {
                loggedFirstDraw = true;
                GTLModular.LOGGER.info("[gtlmodular] 奈亚中央黑日：首帧绘制，本帧 {} 台"
                        + "（AFTER_LEVEL，Iris 合成之后，屏幕空间 shader）", targets.size());
            }
            BlackSunRenderer.drawAll(level, event.getLevelRenderer(), event.getPartialTick(), targets);
        } catch (Throwable failure) {
            if (!loggedRenderFailure) {
                loggedRenderFailure = true;
                GTLModular.LOGGER.error("[gtlmodular] 奈亚中央黑日：绘制入口失败，本次会话不再报错", failure);
            }
        }
    }
}
