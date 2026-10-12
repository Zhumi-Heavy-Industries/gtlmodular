package com.gtlmodular.client.ctm;

import com.gtlmodular.common.data.nanochip.NanochipModContext;
import com.gregtechceu.gtceu.api.block.IMachineBlock;
import com.gtlmodular.common.data.NanochipBlocks;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

/** 包装已烘焙模型以保留原资源定义；方块通过 Supplier 延后读取，避免静态初始化触及未就绪的注册表。 */
public final class CtmClientSetup {

    private static final Map<String, Target> TARGETS = new LinkedHashMap<>();

    /** 外部方块的外观变化作用于整个整合包，因此必须显式限定例外名单。 */
    private static final Set<String> FOREIGN_ALLOWED = Set.of(
            "gtceu:naquadah_coil_block");

    static {
        TARGETS.put("nanochip_mesh_interface_casing", new Target(
                "block/casings/ctm/nanochip_mesh_interface_casing", false,
                () -> NanochipBlocks.MESH_INTERFACE_CASING.get()));
        TARGETS.put("nanochip_complex_glass", new Target(
                "block/ctm/nanochip_complex_glass", true,
                () -> NanochipBlocks.COMPLEX_GLASS.get()));
        TARGETS.put("naquadah_coil_block", new Target(
                "block/ctm/naquadah_coil", true, "block/ctm/naquadah_coil_lit",
                () -> ForgeRegistries.BLOCKS.getValue(new ResourceLocation("gtceu", "naquadah_coil_block"))));
    }

    private static final List<ConnectedCtmBakedModel> WRAPPED = new java.util.concurrent.CopyOnWriteArrayList<>();
    private static final List<LayeredCtmBakedModel> LAYERED = new java.util.concurrent.CopyOnWriteArrayList<>();
    private static final List<MachineCtmBakedModel> MACHINES = new java.util.concurrent.CopyOnWriteArrayList<>();
    private static volatile boolean loggedReady;
    private static volatile int notReadyLogged;

    /** 先统计再包装，避免超出上限时同一方块只有部分状态获得 CTM。 */
    private static int foreignEntries;
    private static int foreignBlocks;
    private static int foreignInstances;
    private static boolean foreignFallback;

    private static final int MAX_DESCRIBED_MACHINES = 12;

    /** LDLib 机器模型的三参 getQuads 不提供几何，默认关闭此路径，机器外壳由 NacMachineShellRenderer 绘制。 */
    public static final boolean MACHINE_HULL_CTM = false;

    private CtmClientSetup() {
    }

    private record Target(String spritePrefix, boolean compact, String litSpritePrefix, Supplier<Block> block) {

        Target(String spritePrefix, boolean compact, Supplier<Block> block) {
            this(spritePrefix, compact, null, block);
        }
    }

    private static Set<ResourceLocation> planForeignHulls(Map<ResourceLocation, BakedModel> models) {
        foreignEntries = 0;
        foreignBlocks = 0;
        foreignInstances = 0;
        foreignFallback = false;
        if (!MACHINE_HULL_CTM) {
            return Set.of();
        }
        Set<ResourceLocation> accepted = new LinkedHashSet<>();
        Set<Block> blocks = new HashSet<>();
        int instances = 0;
        for (Map.Entry<ResourceLocation, BakedModel> entry : models.entrySet()) {
            ResourceLocation key = entry.getKey();
            if (TARGETS.get(key.getPath()) != null) {
                continue;
            }
            if (CtmHullGroup.isMachineHull(key)) {
                continue;
            }
            BakedModel delegate = entry.getValue();
            if (delegate instanceof MachineCtmBakedModel || delegate instanceof ConnectedCtmBakedModel
                    || delegate instanceof LayeredCtmBakedModel) {
                continue;
            }
            Block block = ForgeRegistries.BLOCKS.getValue(key);
            if (block == null || !(block instanceof IMachineBlock)) {
                continue;
            }
            accepted.add(key);
            blocks.add(block);
            if (MachineCtmBakedModel.planOnce(delegate, block)) {
                instances++;
            }
        }
        foreignEntries = accepted.size();
        foreignBlocks = blocks.size();
        foreignInstances = instances;
        if (instances > MachineCtmBakedModel.MAX_FOREIGN_WRAPPERS) {
            foreignFallback = true;
            NanochipModContext.logger().warn("[gtlmodular] CTM: 【硬上限回退】白名单之外的机器方块太多 —— "
                            + "命中 {} 个方块 / {} 条状态模型条目 / {} 个（去重后）包装实例，"
                            + "超过上限 {} ⇒ 本轮**只包装白名单那 {} 台**（外来机器一个都不包）。"
                            + "机器面 CTM 的判据本身没问题，是「全包任何 IMachineBlock」这条路在本整合包里太宽；"
                            + "要放开请调 MachineCtmBakedModel.MAX_FOREIGN_WRAPPERS 并回传这条日志。",
                    foreignBlocks, foreignEntries, instances, MachineCtmBakedModel.MAX_FOREIGN_WRAPPERS,
                    CtmHullGroup.MACHINE_HULL_IDS.length);
            MachineCtmBakedModel.clearReuse();
            return Set.of();
        }
        return accepted;
    }

    @SubscribeEvent
    public static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        // 资源重载必须释放旧包装和诊断状态，避免继续持有旧模型与图集。
        WRAPPED.clear();
        LAYERED.clear();
        MACHINES.clear();
        loggedReady = false;
        notReadyLogged = 0;
        // 共享 sprite、包装复用表与仓位索引也可能引用旧资源，必须同时失效。
        MachineCtmBakedModel.invalidateSharedVariants();
        MachineCtmBakedModel.clearReuse();
        NacSlotIndex.reset();
        // 外壳罩缓存内嵌旧图集的 sprite，重载时必须同步清空。
        com.gtlmodular.client.renderer.NacMachineShellRenderer.reset();
        // 渲染目标与邻居识别必须同源，避免只有一侧连接。
        String hullMismatch = CtmHullGroup.hullViewMismatch();
        if (hullMismatch != null) {
            NanochipModContext.logger().error("[gtlmodular] CTM: 机器外壳白名单的两个视图不一致：{} —— 这会造成"
                    + "「自己换了 sprite、邻居不认它」的单侧连接；请检查 CtmHullGroup.MACHINE_HULL_IDS 与注册处。",
                    hullMismatch);
        }
        ConnectedCtmBakedModel.lazyResolver = model -> resolveAll();
        if (MACHINE_HULL_CTM) {
            MachineCtmBakedModel.lazyResolver = CtmClientSetup::resolveAll;
        } else {
            MachineCtmBakedModel.lazyResolver = null;
        }
        Map<ResourceLocation, BakedModel> models = event.getModels();
        Set<ResourceLocation> foreignHullKeys = planForeignHulls(models);
        int wrapped = 0;
        int layeredCount = 0;
        int machines = 0;
        int machineEntries = 0;
        for (Map.Entry<ResourceLocation, BakedModel> entry : new ArrayList<>(models.entrySet())) {
            ResourceLocation key = entry.getKey();
            boolean whitelistedHull = CtmHullGroup.isMachineHull(key);
            boolean foreignHull = foreignHullKeys.contains(key);
            if (!NanochipModContext.modId().equals(key.getNamespace())
                    && !foreignHull
                    && !whitelistedHull
                    && !FOREIGN_ALLOWED.contains(key.getNamespace() + ":" + key.getPath())) {
                continue;
            }
            boolean machineHull = MACHINE_HULL_CTM
                    && (!NanochipModContext.modId().equals(key.getNamespace()) || TARGETS.get(key.getPath()) == null)
                    && (whitelistedHull || foreignHull);
            if (machineHull) {
                if (!(entry.getValue() instanceof MachineCtmBakedModel)) {
                    Block machineBlock = ForgeRegistries.BLOCKS.getValue(
                            new ResourceLocation(key.getNamespace(), key.getPath()));
                    if (machineBlock != null) {
                        // 同一模型和方块的状态共享包装，避免重复缓存及 CopyOnWriteArrayList 的反复复制。
                        MachineCtmBakedModel reused = MachineCtmBakedModel.reuseOf(entry.getValue(), machineBlock);
                        MachineCtmBakedModel model = reused;
                        if (model == null) {
                            model = new MachineCtmBakedModel(entry.getValue(), machineBlock);
                            MACHINES.add(model);
                            machines++;
                            MachineCtmBakedModel.bindReuse(entry.getValue(), machineBlock, model);
                        }
                        models.put(key, model);
                        machineEntries++;
                    }
                }
                continue;
            }
            Target target = TARGETS.get(key.getPath());
            if (target == null || entry.getValue() instanceof ConnectedCtmBakedModel
                    || entry.getValue() instanceof LayeredCtmBakedModel) {
                continue;
            }
            Block block = target.block().get();
            if (block == null) {
                continue;
            }
            BlockState frameState = block.defaultBlockState();
            LayeredCtmBakedModel layered = target.litSpritePrefix() == null ? null
                    : LayeredCtmBakedModel.tryCreate(entry.getValue(), block,
                            List.of(target.spritePrefix(), target.litSpritePrefix()), frameState);
            if (layered != null) {
                models.put(key, layered);
                LAYERED.add(layered);
                layeredCount++;
            } else {
                ConnectedCtmBakedModel model = new ConnectedCtmBakedModel(
                        entry.getValue(), block, target.spritePrefix(), target.compact(), frameState,
                        CtmHullGroup.MESH_CASING_ID.equals(key.getPath()));
                models.put(key, model);
                WRAPPED.add(model);
            }
            wrapped++;
        }
        assertTableSize("WRAPPED", WRAPPED.size(), wrapped - layeredCount);
        assertTableSize("LAYERED", LAYERED.size(), layeredCount);
        assertTableSize("MACHINES", MACHINES.size(), machines);
        if (wrapped == 0 && machines == 0) {
            NanochipModContext.logger().warn("[gtlmodular] CTM: 模型烘焙结果里没找到任何目标方块的条目 —— 连接纹理会退回「孤立变体」（= 现状）。"
                    + (MACHINE_HULL_CTM ? "" : "（机器面 CTM 总开关已关闭，机器条目**按设计**不包装。）")
                    + "若玩家反馈花纹仍未连片，请把这条日志连同 mods 列表一起回传。");
        } else {
            NanochipModContext.logger().info("[gtlmodular] CTM: 已包装 {} 个方块模型条目 + {} 个机器方块条目（{}）",
                    wrapped, machines, describe());
            NanochipModContext.logger().info("[gtlmodular] CTM: 机器面覆盖范围：含 {} 个机器方块条目"
                            + "（白名单 {} 台【外壳恒为接口机壳】+ 外来 IMachineBlock {} 个方块 / {} 条状态模型条目"
                            + " / {} 个包装实例）{}",
                    machineEntries, CtmHullGroup.MACHINE_HULL_IDS.length,
                    foreignBlocks, foreignEntries, foreignInstances,
                    foreignFallback ? "—— ★ 已触发硬上限回退：外来机器一个都没包（见上一条 WARN）"
                            : (MACHINE_HULL_CTM ? "" : "（总开关已关闭 ⇒ 零包装）"));
        }
        resolveAll();
    }

    /** 只记录包装数量不一致，避免诊断异常中断整个资源重载。 */
    private static void assertTableSize(String name, int actual, int expected) {
        if (actual != expected) {
            NanochipModContext.logger().error("[gtlmodular] CTM: 内部不一致 —— {} 表里有 {} 条，本轮只包装了 {} 条"
                    + "（要么重载没清空（线性泄漏），要么有别的代码往表里加）；功能仍可用但请回传这条日志。",
                    name, actual, expected);
        }
    }

    @SubscribeEvent
    public static void onBakingCompleted(ModelEvent.BakingCompleted event) {
        resolveAll();
    }

    /** 图集缝合后补解析，避免烘焙阶段尚未就绪的 sprite 永久缺失。 */
    @SubscribeEvent
    public static void onTextureStitch(TextureStitchEvent.Post event) {
        if (!InventoryMenu.BLOCK_ATLAS.equals(event.getAtlas().location())) {
            return;
        }
        resolveAll();
    }

    private static void resolveAll() {
        if (WRAPPED.isEmpty() && LAYERED.isEmpty() && MACHINES.isEmpty()) {
            return;
        }
        var atlas = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS);
        int ready = 0;
        int missing = 0;
        int total = WRAPPED.size() + LAYERED.size() + MACHINES.size();
        for (ConnectedCtmBakedModel model : WRAPPED) {
            if (model.resolve(atlas, model.block().defaultBlockState())) {
                ready++;
            }
            missing += model.missedSprites();
        }
        for (LayeredCtmBakedModel model : LAYERED) {
            if (model.resolve(atlas, model.block().defaultBlockState())) {
                ready++;
            }
            missing += model.missedSprites();
        }
        for (MachineCtmBakedModel model : MACHINES) {
            if (model.resolve(atlas)) {
                ready++;
            }
            missing += model.missedSprites();
        }
        if (ready == total) {
            if (!loggedReady) {
                loggedReady = true;
                NanochipModContext.logger().info("[gtlmodular] CTM: 全部变体 sprite 已就绪（命名空间 {}），连接纹理按邻居选变体生效（{}）",
                        spriteNamespaceOfFirst(), describe());
            }
            return;
        }
        int attempts = ++notReadyLogged;
        if (attempts <= 3 || attempts % 10 == 0) {
            NanochipModContext.logger().warn("[gtlmodular] CTM: 变体 sprite 尚未就绪（就绪 {}/{}，共缺 {} 张，第 {} 次尝试）——"
                            + "会继续重试；若一直不齐，把这条日志连同 mods 列表给我。{}",
                    ready, total, missing, attempts, describe());
        }
    }

    private static String spriteNamespaceOfFirst() {
        if (!WRAPPED.isEmpty()) {
            return WRAPPED.get(0).spriteNamespace();
        }
        if (!LAYERED.isEmpty()) {
            return LAYERED.get(0).spriteNamespace();
        }
        return NanochipModContext.assetNamespace();
    }

    private static String describe() {
        StringBuilder sb = new StringBuilder();
        for (ConnectedCtmBakedModel model : WRAPPED) {
            ConnectedCtmBakedModel.Dump d = model.describe();
            if (sb.length() > 0) {
                sb.append('；');
            }
            sb.append(d.spritePrefix()).append(" × ").append(d.variantCount())
                    .append(d.frameComplete() ? "（面内坐标帧 OK）" : "（面内坐标帧未知 ⇒ 回退第 0 格）");
        }
        for (LayeredCtmBakedModel model : LAYERED) {
            if (sb.length() > 0) {
                sb.append('；');
            }
            sb.append('[').append(model.layerCount()).append(" 层] ").append(model.summary());
        }
        if (!MACHINES.isEmpty()) {
            if (sb.length() > 0) {
                sb.append('；');
            }
            // 按方块去重并截断日志，避免大量状态条目撑满诊断输出。
            java.util.Set<String> shown = new java.util.LinkedHashSet<>();
            for (MachineCtmBakedModel model : MACHINES) {
                if (shown.size() >= MAX_DESCRIBED_MACHINES) {
                    break;
                }
                if (shown.add(model.block().getDescriptionId())) {
                    sb.append("[机器] ").append(model.block().getName().getString()).append(' ').append(model.summary())
                            .append(' ');
                }
            }
            sb.append("（共 ").append(MACHINES.size()).append(" 个包装实例");
            if (MACHINES.size() > shown.size()) {
                sb.append("，日志只列前 ").append(shown.size()).append(" 台");
            }
            sb.append("）");
        }
        return sb.toString();
    }
}
