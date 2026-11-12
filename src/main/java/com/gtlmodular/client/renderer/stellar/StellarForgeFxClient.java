package com.gtlmodular.client.renderer.stellar;

import com.gtlmodular.GTLModular;
import com.gtlmodular.client.renderer.preview.ShanhaiInstabilityRenderTypes;
import com.gtlmodular.common.machine.stellar.StellarForgeMachine;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.pattern.util.RelativeDirection;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.GTCEu;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
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
 * 「恒星炎炀锻炉」漩涡特效的客户端入口 —— <b>还原 GTO 的屏幕空间螺旋折射</b>。
 *
 * <h2>GTO 的表现是什么</h2>
 * <p>不是自发光几何，而是<b>抓屏 + 自定义 shader 的屏幕空间扭曲</b>：{@code stellar_forge_vortex.fsh}
 * 按半径把场景采样的 UV 拧成螺旋（{@code BandAngle / DecayRadius / DisappearRadius} 三个 uniform），
 * 所以看起来是「空间本身在转」。本模组保留了这条完整管线（vsh/fsh 与 GTO 逐字节相同）。
 *
 * <h2>★ 2026-11-02 第二次修正：特效实例从哪来（之前一直看不到的真因）</h2>
 * <p>旧实现把「创建/刷新特效」写死在 {@code StellarForgeRenderer#render(...)} 里 —— 但 GTCEu 1.4.4 的
 * 机器方块实体<b>不会调用</b> {@code IRenderer#render}（同一天另一条会话用 javap 查实）⇒
 * {@link StellarFxManager} 永远为空、shader 管线一次也没跑过。这才是「看不到特效」的根因，
 * 与 shader 本身无关（几何版之所以不好看，也是因为它根本没画那段扭曲）。
 *
 * <p>现在改为：ClientTick 扫 16³ 区段找到 {@link StellarForgeMachine} 控制器 →
 * <b>每个 tick</b> 用 {@link StellarFxManager#upsert} 建/刷新对应的 {@link StellarForgeVortexFX}
 * （{@code STALE_TICKS=12}，每 tick 刷新保证不被摘掉）。
 *
 * <p><b>兜底</b>：若自定义 shader 因光影包/驱动没注册上（{@code GtlRenderTypes} 取到 null），
 * 退化成 {@link StellarForgeVortexGeometry} 的加性发光几何（至少能看见），日志会明确标注。
 */
@OnlyIn(Dist.CLIENT)
public final class StellarForgeFxClient {

    /** 机器 id。 */
    private static final net.minecraft.resources.ResourceLocation STELLAR_FORGE = GTCEu.id("stellar_forge");

    /** 漩涡 shader（与 GTO 同名同路径：assets/gtlmodular/shaders/core/stellar_forge_vortex.*）。 */
    private static final net.minecraft.resources.ResourceLocation VORTEX_SHADER =
            new net.minecraft.resources.ResourceLocation("gtlmodular", "stellar_forge_vortex");

    /** 漩涡中心相对控制器的偏移：向上 31 格、向里 2 格（GTO offU / offD）。 */
    private static final int OFF_U = 31;
    private static final int OFF_D = -2;

    /** GTO 常量：{@code angle = PI/8}、衰减半径 32、消失半径 48。 */
    private static final float BAND_ANGLE = 0.3926991F;
    private static final float DECAY_RADIUS = 32.0F;
    private static final float DISAPPEAR_RADIUS = 48.0F;

    /** 区段边长。 */
    private static final int SECTION_SIZE = 16;

    /** 扫描半径（方块）。64 ⇒ 4 区段半径 ⇒ 9³ = 729 个区段一轮。 */
    private static final int SCAN_RADIUS = 64;

    /** 每 tick 扫的区段数（729 / 48 ≈ 16 tick 一轮）。 */
    private static final int SECTIONS_PER_TICK = 48;

    /** 每 N tick 起一轮扫描。 */
    private static final int SCAN_INTERVAL_TICKS = 20;

    /** 几何兜底的最远绘制距离（方块）。 */
    private static final int FALLBACK_DRAW_DISTANCE = 192;

    /** 缓存条目：位置 + 朝向（刷新特效用）。 */
    private record Cached(BlockPos pos, Direction front, Direction upwards, boolean flipped) {}

    private static final Map<BlockPos, Cached> CONTROLLERS = new HashMap<>();
    private static final Set<BlockPos> SEEN = new HashSet<>();

    private static boolean initialized;
    private static boolean rendererAttached;

    // 扫描游标
    private static boolean sweeping;
    private static int cursor;
    private static int baseSectionX;
    private static int baseSectionY;
    private static int baseSectionZ;
    private static int sectionRadius;
    private static int sectionsPerAxis;
    private static int sectionsTotal;
    private static int ticks;

    // 一次性日志
    private static boolean loggedFirstUpsert;
    private static boolean loggedScanFailure;
    private static boolean loggedFallbackDraw;
    private static boolean loggedFallbackFailure;
    private static boolean loggedShaderMissing;
    private static boolean loggedNebulaDraw;
    private static boolean loggedNebulaFailure;

    /** 控制器方块缓存。 */
    private static Block controllerBlockCache;

    private StellarForgeFxClient() {
    }

    /** 注册 shader 与客户端事件。重复调用只注册一次。 */
    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        try {
            IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
            modBus.addListener(StellarForgeFxClient::onRegisterShaders);
        } catch (Throwable throwable) {
            GTLModular.LOGGER.error("[gtlmodular] 恒星炎炀锻炉漩涡特效：shader 注册接线失败，将退化为几何兜底", throwable);
        }
        try {
            IEventBus gameBus = MinecraftForge.EVENT_BUS;
            // ★ 2026-11-02（忠实还原版）：**GTO 原版 shader 层重新启用**。
            //   实测：Iris 光影下「自定义 shader」的几何是能画的（前面 19:06 的截图就是它），
            //   而「原版 position_color_tex」的手工层在 Iris 里反而画不出来 ⇒ 观感必须由这一层负责。
            //   vsh/fsh、网格、三个 uniform、抓屏、舞台（AFTER_CUTOUT_BLOCKS）都与 GTO 一致。
            gameBus.addListener(StellarFxManager::dispatch);
            gameBus.addListener(StellarForgeFxClient::onClientTick);
            gameBus.addListener(StellarForgeFxClient::onLevelUnload);
            gameBus.addListener(StellarForgeFxClient::onRenderLevelStageFallback);
            // ★ 2026-11-02：自制「星云贴图」层停用 —— 它不是 GTO 原版效果，且 Iris 下画不出来，
            //   之前和 GTO 原版层叠在一起就是玩家看到的「重影」。忠实还原只保留上面的 GTO shader 层。
            // gameBus.addListener(StellarForgeFxClient::onRenderNebula);
            GTLModular.LOGGER.info("[gtlmodular] 恒星炎炀锻炉漩涡特效：客户端接线完成"
                    + "（RegisterShaders 注册 GTO 同款螺旋折射 shader + ClientTick 扫描控制器并每 tick 刷新特效 + "
                    + "RenderLevelStage 抓屏绘制；shader 不可用时退化为加性几何兜底）");
        } catch (Throwable throwable) {
            GTLModular.LOGGER.error("[gtlmodular] 恒星炎炀锻炉漩涡特效：客户端事件接线失败，特效将不显示", throwable);
        }
    }

    /** 外观渲染器（外壳 + 覆层「脸」），与特效无关。 */
    public static void attachRenderer() {
        try {
            MachineDefinition definition = GTRegistries.MACHINES.get(STELLAR_FORGE);
            if (definition == null) {
                GTLModular.LOGGER.warn("[gtlmodular] 找不到机器定义 {} ⇒ 恒星炎炀锻炉外观渲染器未挂载"
                        + "（gtlcore 未加载？）", STELLAR_FORGE);
                return;
            }
            if (!(definition instanceof MultiblockMachineDefinition)) {
                GTLModular.LOGGER.warn("[gtlmodular] {} 不是 MultiblockMachineDefinition（实为 {}），仍尝试挂渲染器",
                        STELLAR_FORGE, definition.getClass().getName());
            }
            definition.setRenderer(new StellarForgeRenderer());
            rendererAttached = true;
            GTLModular.LOGGER.info("[gtlmodular] 恒星炎炀锻炉外观渲染器已挂到 {}（外壳 atomic_casing + 覆层 "
                    + "electric_blast_furnace）", STELLAR_FORGE);
        } catch (Throwable throwable) {
            GTLModular.LOGGER.error("[gtlmodular] 挂载恒星炎炀锻炉外观渲染器失败（只影响外观）", throwable);
        }
    }

    public static boolean isInitialized() {
        return initialized;
    }

    public static boolean isRendererAttached() {
        return rendererAttached;
    }

    public static int cachedControllerCount() {
        return CONTROLLERS.size();
    }

    // ------------------------------------------------------------------
    // shader
    // ------------------------------------------------------------------

    private static void onRegisterShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(), VORTEX_SHADER,
                            DefaultVertexFormat.POSITION_COLOR_NORMAL),
                    GtlRenderTypes::setStellarForgeVortexShader);
            GTLModular.LOGGER.info("[gtlmodular] 恒星炎炀锻炉漩涡特效：已注册 shader {}", VORTEX_SHADER);
        } catch (Throwable throwable) {
            GTLModular.LOGGER.error("[gtlmodular] 编译 shader {} 失败 ⇒ 漩涡特效退化为几何兜底", VORTEX_SHADER, throwable);
        }
    }

    // ------------------------------------------------------------------
    // 扫描 + 每 tick 刷新特效
    // ------------------------------------------------------------------

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        try {
            ticks++;
            // 每 tick 推进特效 age / 摘过期（STALE_TICKS=12 靠下面的 upsert 续命）。
            StellarFxManager.tick();
            // 每 tick 刷新已缓存控制器的特效。
            refreshCachedFx();
            if (!sweeping) {
                if (ticks % SCAN_INTERVAL_TICKS != 0) {
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
                GTLModular.LOGGER.error("[gtlmodular] 恒星炎炀锻炉特效：扫描/刷新失败，本次会话不再报错", failure);
            }
        }
    }

    /** 每 tick 把缓存里的控制器 upsert 进特效管理器（保证 12 tick 内被刷新）。 */
    private static void refreshCachedFx() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || CONTROLLERS.isEmpty()) {
            return;
        }
        ResourceKey<Level> dimension = level.dimension();
        for (Cached cached : CONTROLLERS.values()) {
            // ★ 2026-11-06（玩家要求）：特效只在机器「运行中」产生 —— 没在跑就不登记/不续命，
            //   已存在的漩涡会在 STALE_TICKS（12 tick ≈ 0.6s）内被摘掉。
            if (!(level.getBlockEntity(cached.pos()) instanceof com.gregtechceu.gtceu.api.machine.IMachineBlockEntity holder)) {
                continue;
            }
            if (!(holder.getMetaMachine() instanceof com.gtlmodular.common.machine.stellar.StellarForgeMachine machine)
                    || !machine.isFormed()
                    || machine.getRecipeLogic() == null
                    || !machine.getRecipeLogic().isWorking()) {
                continue;
            }
            BlockPos offset = RelativeDirection.offsetPos(BlockPos.ZERO, cached.front(), cached.upwards(),
                    cached.flipped(), OFF_U, 0, OFF_D);
            Vec3 center = Vec3.atCenterOf(cached.pos().offset(offset));
            StellarFxManager.upsert(
                    new StellarForgeVortexFX.Key(dimension, cached.pos()),
                    () -> new StellarForgeVortexFX(dimension, cached.pos(), center, cached.front(),
                            BAND_ANGLE, DECAY_RADIUS, DISAPPEAR_RADIUS),
                    fx -> fx.refresh(center, cached.front(), BAND_ANGLE, DECAY_RADIUS, DISAPPEAR_RADIUS));
            if (!loggedFirstUpsert) {
                loggedFirstUpsert = true;
                GTLModular.LOGGER.info("[gtlmodular] 恒星炎炀锻炉特效：首个漩涡已登记 base={} center={} front={}",
                        cached.pos(), center, cached.front());
            }
        }
    }

    private static boolean beginSweep() {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.player == null) {
            return false;
        }
        sectionRadius = Math.max(1, SCAN_RADIUS / SECTION_SIZE);
        sectionsPerAxis = sectionRadius * 2 + 1;
        sectionsTotal = sectionsPerAxis * sectionsPerAxis * sectionsPerAxis;
        BlockPos eye = minecraft.player.blockPosition();
        baseSectionX = eye.getX() >> 4;
        baseSectionY = eye.getY() >> 4;
        baseSectionZ = eye.getZ() >> 4;
        cursor = 0;
        SEEN.clear();
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
        int index = cursor++;
        int sideSq = sectionsPerAxis * sectionsPerAxis;
        int sx = index / sideSq;
        int sy = (index % sideSq) / sectionsPerAxis;
        int sz = index % sectionsPerAxis;
        int sectionX = baseSectionX + sx - sectionRadius;
        int sectionY = baseSectionY + sy - sectionRadius;
        int sectionZ = baseSectionZ + sz - sectionRadius;

        LevelChunk chunk = level.getChunkSource().getChunkNow(sectionX, sectionZ);
        if (chunk != null && !chunk.isEmpty()) {
            LevelChunkSection[] sections = chunk.getSections();
            int sectionIndex = (sectionY * SECTION_SIZE - chunk.getMinBuildHeight()) >> 4;
            LevelChunkSection section =
                    sectionIndex >= 0 && sectionIndex < sections.length ? sections[sectionIndex] : null;
            if (section != null && !section.hasOnlyAir()) {
                int originX = sectionX << 4;
                int originY = sectionY << 4;
                int originZ = sectionZ << 4;
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
        if (!(holder.getMetaMachine() instanceof StellarForgeMachine machine)) {
            return;
        }
        SEEN.add(pos);
        CONTROLLERS.put(pos, new Cached(pos, machine.getFrontFacing(), machine.getUpwardsFacing(),
                machine.isFlipped()));
    }

    private static void finishSweepIfDone() {
        if (cursor < sectionsTotal) {
            return;
        }
        sweeping = false;
        CONTROLLERS.keySet().removeIf(pos -> !SEEN.contains(pos));
        SEEN.clear();
    }

    private static Block controllerBlock() {
        if (controllerBlockCache != null) {
            return controllerBlockCache;
        }
        try {
            MachineDefinition definition = GTRegistries.MACHINES.get(STELLAR_FORGE);
            if (definition == null) {
                return null;
            }
            controllerBlockCache = definition.getBlock();
        } catch (Throwable ignored) {
            return null;
        }
        return controllerBlockCache;
    }

    private static void onLevelUnload(LevelEvent.Unload event) {
        CONTROLLERS.clear();
        SEEN.clear();
        sweeping = false;
        cursor = 0;
        controllerBlockCache = null;
        StellarFxManager.clear();
    }

    // ------------------------------------------------------------------
    // 几何兜底（仅 shader 不可用时画）
    // ------------------------------------------------------------------

    // ------------------------------------------------------------------
    // ③ 光影兼容层：原版 shader 的星云盘（Iris 下也能画）
    // ------------------------------------------------------------------

    /**
     * 在 {@code AFTER_CUTOUT_BLOCKS} 画「星云贴图漩涡」。
     *
     * <p><b>为什么必须有这层</b>：GTO 原版的自定义 shader 在玩家开 Oculus/Iris 光影时不会被绘制
     * （关光影能看到、开光影消失）。这一层只用<b>原版 {@code position_tex_color}</b> + 加性混合，
     * 在光影下正常显示；两套同时画时，光影关：折射 + 星云，光影开：至少星云可见。
     */
    private static void onRenderNebula(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS) {
            return;
        }
        if (CONTROLLERS.isEmpty()) {
            return;
        }
        com.mojang.blaze3d.vertex.BufferBuilder builder = null;
        try {
            Minecraft minecraft = Minecraft.getInstance();
            ClientLevel level = minecraft.level;
            PoseStack poses = event.getPoseStack();
            if (level == null || poses == null || event.getCamera() == null) {
                return;
            }
            Vec3 eye = event.getCamera().getPosition();
            double maxDistanceSq = (double) FALLBACK_DRAW_DISTANCE * FALLBACK_DRAW_DISTANCE;
            float time = (Util.getMillis() % 3_600_000L) / 1000.0f;

            // 先筛出本帧要画的机器（没有就完全不动 GL 状态）
            List<Cached> drawable = new ArrayList<>();
            for (Cached cached : CONTROLLERS.values()) {
                if (!(level.getBlockEntity(cached.pos()) instanceof IMachineBlockEntity holder)
                        || !(holder.getMetaMachine() instanceof StellarForgeMachine machine)
                        || !machine.isFormed()) {
                    continue;
                }
                BlockPos pos = cached.pos();
                if (eye.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) > maxDistanceSq) {
                    continue;
                }
                drawable.add(cached);
            }
            if (drawable.isEmpty()) {
                return;
            }
            // 确保贴图进了纹理管理器
            minecraft.getTextureManager().getTexture(StellarVortexNebula.TEXTURE);

            // ★ 不走 RenderType，直接手工开批（避开 Iris 对自定义 RenderType 的干预）
            builder = Tesselator.getInstance().getBuilder();
            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_TEX);
            poses.pushPose();
            try {
                poses.translate(-eye.x, -eye.y, -eye.z);
                for (Cached cached : drawable) {
                    poses.pushPose();
                    try {
                        poses.translate(cached.pos().getX(), cached.pos().getY(), cached.pos().getZ());
                        StellarVortexNebula.draw(builder, poses.last().pose(), cached.front(), time, 1.0f);
                    } finally {
                        poses.popPose();
                    }
                }
            } finally {
                poses.popPose();
            }

            RenderSystem.enableBlend();
            RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE,
                    GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
            RenderSystem.disableCull();
            RenderSystem.depthMask(false);
            RenderSystem.disableDepthTest();
            RenderSystem.setShader(GameRenderer::getPositionColorTexShader);
            RenderSystem.setShaderTexture(0, StellarVortexNebula.TEXTURE);
            BufferUploader.drawWithShader(builder.end());
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            RenderSystem.defaultBlendFunc();

            if (!loggedNebulaDraw) {
                loggedNebulaDraw = true;
                StringBuilder detail = new StringBuilder();
                for (Cached cached : drawable) {
                    detail.append(cached.pos()).append(" front=").append(cached.front()).append("; ");
                }
                GTLModular.LOGGER.info("[gtlmodular] 恒星炎炀锻炉特效：星云层首帧绘制，本帧 {} 台（{}）",
                        drawable.size(), detail);
            }
        } catch (Throwable failure) {
            if (builder != null) {
                try {
                    builder.discard();
                } catch (Throwable ignored) {
                    // ignore
                }
            }
            if (!loggedNebulaFailure) {
                loggedNebulaFailure = true;
                GTLModular.LOGGER.error("[gtlmodular] 恒星炎炀锻炉特效：星云层绘制失败", failure);
            }
        }
    }

    /** 兜底绘制：只负责 {@code GtlRenderTypes} 取不到 shader 的情况。 */
    private static void onRenderLevelStageFallback(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) {
            return;
        }
        if (CONTROLLERS.isEmpty()) {
            return;
        }
        if (GtlRenderTypes.getStellarForgeVortexShader() != null) {
            return; // 走 GTO 原版管线
        }
        if (!loggedShaderMissing) {
            loggedShaderMissing = true;
            GTLModular.LOGGER.warn("[gtlmodular] 恒星炎炀锻炉特效：漩涡 shader 未注册（光影包/驱动？）⇒ 使用加性几何兜底");
        }
        try {
            Minecraft minecraft = Minecraft.getInstance();
            ClientLevel level = minecraft.level;
            PoseStack poses = event.getPoseStack();
            if (level == null || poses == null || event.getCamera() == null) {
                return;
            }
            Vec3 eye = event.getCamera().getPosition();
            double maxDistanceSq = (double) FALLBACK_DRAW_DISTANCE * FALLBACK_DRAW_DISTANCE;
            MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
            float time = (Util.getMillis() % 3_600_000L) / 1000.0f;
            RenderType glow = ShanhaiInstabilityRenderTypes.GLOW;
            poses.pushPose();
            boolean any = false;
            try {
                poses.translate(-eye.x, -eye.y, -eye.z);
                for (Cached cached : CONTROLLERS.values()) {
                    if (!(level.getBlockEntity(cached.pos()) instanceof IMachineBlockEntity holder)
                            || !(holder.getMetaMachine() instanceof StellarForgeMachine machine)
                            || !machine.isFormed()) {
                        continue;
                    }
                    BlockPos pos = cached.pos();
                    if (eye.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) > maxDistanceSq) {
                        continue;
                    }
                    boolean working = machine.getRecipeLogic() != null && machine.getRecipeLogic().isWorking();
                    VertexConsumer consumer = buffers.getBuffer(glow);
                    poses.pushPose();
                    try {
                        poses.translate(pos.getX(), pos.getY(), pos.getZ());
                        StellarForgeVortexGeometry.draw(consumer, poses.last().pose(), cached.front(), time,
                                working ? 1.0f : 0.45f);
                    } finally {
                        poses.popPose();
                    }
                    any = true;
                    if (!loggedFallbackDraw) {
                        loggedFallbackDraw = true;
                        GTLModular.LOGGER.info("[gtlmodular] 恒星炎炀锻炉特效：几何兜底首帧绘制 base={}", pos);
                    }
                }
            } finally {
                poses.popPose();
            }
            if (any) {
                buffers.endBatch(glow);
            }
        } catch (Throwable failure) {
            if (!loggedFallbackFailure) {
                loggedFallbackFailure = true;
                GTLModular.LOGGER.error("[gtlmodular] 恒星炎炀锻炉特效：几何兜底绘制失败", failure);
            }
        }
    }
}
