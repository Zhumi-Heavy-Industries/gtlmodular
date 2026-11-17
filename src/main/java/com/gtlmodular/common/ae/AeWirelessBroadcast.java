package com.gtlmodular.common.ae;

import appeng.api.implementations.blockentities.IWirelessAccessPoint;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridConnection;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.pathing.ControllerState;
import appeng.items.tools.powered.WirelessTerminalItem;
import appeng.me.helpers.IGridConnectedBlockEntity;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.integration.ae2.machine.feature.IGridConnectedMachine;
import com.gtlmodular.GTLModular;
import com.gtlmodular.common.machine.MoltenModuleHostMachine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.gtlcore.gtlcore.api.machine.multiblock.IModularMachineModule;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 地心涌动能量转换枢纽的 <b>AE 无线信号广播</b>（参考数据能源「数据均分塔」的虚拟接管思路）。
 *
 * <h3>玩家口径（2026-11-10）</h3>
 * <ul>
 *   <li>主机<b>放下来的时候</b>，按放置者身上的 AE 无线终端绑定主网络；</li>
 *   <li>主机<b>扫描到模块并连上</b>时，同时把这个网络广播给模块里的 ME 节点；</li>
 *   <li>虚拟接管：不铺线、不建物理线缆连接；接管「单节点孤岛」和无原版 AE2 控制器的子网
 *       （均分塔范围模式 + 点对点模式的口径）；</li>
 *   <li>主机失效 / 没有模块 / 绑定网络不可用时，广播全部回收，子网恢复独立。</li>
 * </ul>
 *
 * <h3>实现（为什么是 IGridConnection）</h3>
 * 地心主机是 GT 多方块控制器，本身不是 AE 设备；而 gtlcore 的「无线 ME 网络」也是用
 * {@link GridHelper#createConnection(IGridNode, IGridNode)} 在核心节点与成员节点之间
 * <b>程序化建立连接</b>来实现无线的（见 {@code WirelessAeNetworkRuntime#connectMember}）。
 * 本类照同一套做法：
 * <ol>
 *   <li>绑定：从玩家无线终端拿 {@code GlobalPos}，运行时解析成 {@link IWirelessAccessPoint}，
 *       用它自己的 {@link appeng.api.networking.security.IActionHost#getActionableNode()} 当桥接节点；</li>
 *   <li>广播：先遍历已连接模块 / 主机大楼的 {@link MultiblockControllerMachine#getParts()}，
 *       凡是 {@link IGridConnectedMachine}（ME 输入/输出仓、样板总成等），把它的节点接到桥接节点；</li>
 *   <li>范围接管：再分区块游标扫描地心大楼包络（96/64 格半径），把范围内
 *       <b>单节点孤岛 / 无原版 AE2 控制器的 AE 子网</b>（{@link IInWorldGridNodeHost} 或
 *       {@link IGridConnectedBlockEntity}，如 NeoEco 存储子系统主机）
 *       也接到桥接节点（= 均分塔「范围模式 / 点对点模式」的同口径）；</li>
 *   <li>回收：模块断开 / 主机失效 / 绑定失效时 {@link IGridConnection#destroy()}，子网重新独立。</li>
 * </ol>
 *
 * <p><b>与均分塔的差异（有意不做）</b>：均分塔的「虚拟频道租约 / FE 均衡 / 跨源结算」是一整套
 * 独立系统（{@code TowerNetworkDomain} + {@code TowerEnergyTransferEngine}，1.21.1 专属），
 * 本实现借的是它的「范围模式接管无控制器单节点子网」主行为；本实现里频道仍由 AE2 原生 Pathing 计算。
 *
 * <p><b>副作用提示（AE2 语义）</b>：这条程序化连接对 Pathing 来说等价一根「致密线缆」——
 * 模块 ME 仓的频道需求与待机耗电都会并入主网；主网本来频道/电吃紧时，模块一接上可能让整体掉电。
 * 这是无线并网的正常代价；排查「接上后 ME 仓离线/主机掉电」时先看主网频道余量和待机耗电。
 */
public final class AeWirelessBroadcast {

    private static final String TAG_DIM = "aeBroadcastDim";
    private static final String TAG_POS = "aeBroadcastPos";

    /** 广播维护节拍：1 秒一次（模块/网络变化另有即时触发）。 */
    private static final int MAINTENANCE_INTERVAL_TICKS = 20;

    /**
     * 楼内范围接管（均分塔「范围模式」）：覆盖地心大楼包络，分区块游标扫描，
     * 每 20 tick 扫 16 个区块（约 11 秒扫完 169 个区块），避免一次性遍历卡服。
     */
    private static final int RANGE_R = 96;
    private static final int RANGE_VER = 64;
    private static final int RANGE_CHUNKS_PER_MAINTENANCE = 16;

    private final MoltenModuleHostMachine host;

    /** 绑定的无线终端链接目标（WAP 所在维度 + 坐标）。只存坐标，不存网络对象。 */
    private ResourceLocation boundDim;
    private BlockPos boundPos;

    /** 当前广播出去的虚拟连接：key = ME 部件坐标（BlockPos#asLong）。 */
    private final Map<Long, IGridConnection> connections = new LinkedHashMap<>();

    /** 楼内范围接管：key = 方块坐标；只接管没有物理连接的单节点 AE 子网（均分塔范围模式）。 */
    private final Map<Long, IGridConnection> rangeConnections = new LinkedHashMap<>();

    /** 范围扫描游标（区块线性下标），扫完一轮归零。 */
    private int rangeChunkCursor;

    /** 当前使用的桥接节点（WAP 的节点）。节点一变就全部重连。 */
    private IGridNode bridgeNode;

    private long lastMaintenanceTick = Long.MIN_VALUE;
    private boolean maintenanceQueued = true;

    private boolean loggedOnline;
    private boolean loggedNoBinding;

    /** 换绑确认：已绑定时，潜行右键只提示；10 秒内再点一次才真正换绑，避免静默覆盖。 */
    private GlobalPos pendingRebind;
    private long pendingRebindUntil;

    public AeWirelessBroadcast(MoltenModuleHostMachine host) {
        this.host = host;
    }

    // ------------------------------------------------------------------
    // 绑定：主机放下时按放置者身上的 AE 无线终端
    // ------------------------------------------------------------------

    /** {@code IMachineLife#onMachinePlaced} 回调。 */
    public void onPlaced(LivingEntity entity, ItemStack stack) {
        if (!(entity instanceof Player player) || player.level().isClientSide()) {
            return;
        }
        GlobalPos link = findTerminalLink(player);
        if (link == null) {
            if (!loggedNoBinding) {
                loggedNoBinding = true;
                GTLModular.LOGGER.info("[gtlmodular] 地心 @{}：放置者身上没有已绑定的 AE 无线终端，AE 无线广播保持未绑定",
                        host.getPos());
            }
            return;
        }
        bind(link, player);
    }

    /** 潜行 + 手持 AE 无线终端右键：重新绑定（放置时没带终端 / 想换网络的兜底）。 */
    public void rebindFromPlayer(Player player) {
        GlobalPos link = findTerminalLink(player);
        if (link == null) {
            if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
                sp.displayClientMessage(net.minecraft.network.chat.Component.literal(
                        "§c没有找到已绑定的 AE 无线终端"), true);
            }
            return;
        }
        // 已绑到别的网络时必须二段确认：第一次只提示，10 秒内再点一次才换，避免误触静默串网。
        if (isBound() && !isSameBinding(link)) {
            long now = host.getLevel() == null ? 0L : host.getLevel().getGameTime();
            boolean confirming = pendingRebind != null
                    && now <= pendingRebindUntil
                    && isSameLink(pendingRebind, link);
            if (!confirming) {
                pendingRebind = link;
                pendingRebindUntil = now + 200L; // 10 秒
                if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
                    sp.displayClientMessage(net.minecraft.network.chat.Component.literal(
                            "§e地心已绑定 §f" + boundDim + " " + boundPos.toShortString()
                                    + "§e；确定换成 §f" + link.dimension().location() + " "
                                    + link.pos().toShortString() + "§e 请 10 秒内再潜行右键一次"), true);
                }
                return;
            }
        }
        pendingRebind = null;
        bind(link, player);
    }

    private boolean isSameBinding(GlobalPos link) {
        return boundDim != null && boundPos != null
                && boundDim.equals(link.dimension().location())
                && boundPos.equals(link.pos());
    }

    private static boolean isSameLink(GlobalPos a, GlobalPos b) {
        return a.dimension().equals(b.dimension()) && a.pos().equals(b.pos());
    }

    private void bind(GlobalPos link, Player player) {
        Level hostLevel = host.getLevel();
        if (hostLevel != null && !link.dimension().equals(hostLevel.dimension())) {
            // AE 无线终端本身要求与无线访问点同维度；跨维度不做裸连接（会污染两边 Pathing）
            GTLModular.LOGGER.warn("[gtlmodular] 地心 @{}：无线终端绑定的无线访问点在 {}，与主机维度 {} 不同，忽略",
                    host.getPos(), link.dimension().location(), hostLevel.dimension().location());
            if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
                sp.displayClientMessage(net.minecraft.network.chat.Component.literal(
                        "§c无线终端绑定的无线访问点与地心不在同一维度，无法绑定"), true);
            }
            return;
        }
        this.boundDim = link.dimension().location();
        this.boundPos = link.pos();
        this.maintenanceQueued = true;
        this.loggedNoBinding = false;
        host.markDirty();
        GTLModular.LOGGER.info("[gtlmodular] 地心 @{}：AE 无线广播已绑定到 {} {}（玩家 {}）",
                host.getPos(), boundDim, boundPos, player.getGameProfile().getName());
        if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
            sp.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "§b已绑定 AE 无线网络：§f" + boundDim + " §7" + boundPos.toShortString()), true);
        }
    }

    /**
     * 找玩家身上「已链接到无线访问点」的 AE 无线终端。
     *
     * <p>★ 2026-11-10 复核修复：优先主手、再副手，最后才扫背包 —— 背包里有多把终端时
     * 不能静默绑到第一把（玩家手里拿的那把才是意图）。
     * 用 AE2 公开 API {@link WirelessTerminalItem#getLinkedPosition(ItemStack)}，不碰私有 NBT 键名。
     */
    private static GlobalPos findTerminalLink(Player player) {
        GlobalPos held = linkOf(player.getMainHandItem());
        if (held == null) {
            held = linkOf(player.getOffhandItem());
        }
        if (held != null) {
            return held;
        }
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            GlobalPos link = linkOf(inventory.getItem(slot));
            if (link != null) {
                return link;
            }
        }
        return null;
    }

    private static GlobalPos linkOf(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof WirelessTerminalItem terminal)) {
            return null;
        }
        return terminal.getLinkedPosition(stack);
    }

    // ------------------------------------------------------------------
    // 节拍与维护
    // ------------------------------------------------------------------

    /** 主机每 tick 调；内部按 {@link #MAINTENANCE_INTERVAL_TICKS} 节流。 */
    public void tick() {
        Level level = host.getLevel();
        if (!(level instanceof ServerLevel) || level.isClientSide) {
            return;
        }
        long now = level.getGameTime();
        if (!maintenanceQueued && now - lastMaintenanceTick < MAINTENANCE_INTERVAL_TICKS) {
            return;
        }
        maintenanceQueued = false;
        lastMaintenanceTick = now;
        maintain();
    }

    /** 模块连接/断开、主机成形等事件：下一 tick 立刻维护一次。 */
    public void onModuleChanged() {
        this.maintenanceQueued = true;
    }

    public void onHostFormed() {
        this.maintenanceQueued = true;
    }

    /** 主机结构失效 / 方块移除 / 区块卸载：广播全部回收（绑定信息保留）。 */
    public void dropAll() {
        for (IGridConnection connection : connections.values()) {
            destroyQuietly(connection);
        }
        connections.clear();
        for (IGridConnection connection : rangeConnections.values()) {
            destroyQuietly(connection);
        }
        rangeConnections.clear();
        rangeChunkCursor = 0;
        bridgeNode = null;
        loggedOnline = false;
    }

    private void maintain() {
        if (!host.isFormed() || !(host.getLevel() instanceof ServerLevel)) {
            dropAll();
            publish("未成形");
            return;
        }
        if (boundDim == null || boundPos == null) {
            dropAll();
            publish("未绑定（放置时携带已绑定的 AE 无线终端）");
            return;
        }
        // 持久化的绑定也要再核一次维度：/setblock、NBT 复制、结构工具都可能把 BE NBT 带到别的维度，
        // 跨维度裸连接会污染两边 Pathing。
        if (!boundDim.equals(host.getLevel().dimension().location())) {
            dropAll();
            publish("未绑定（绑定维度与主机不符）");
            return;
        }
        IGridNode bridge = resolveBridgeNode();
        if (bridge == null) {
            dropAll();
            publish("离线（绑定网络不可用）");
            return;
        }
        if (bridge != bridgeNode) {
            // WAP 被拆/换新 → 旧连接全部作废，按新节点重连
            for (IGridConnection connection : connections.values()) {
                destroyQuietly(connection);
            }
            connections.clear();
            for (IGridConnection connection : rangeConnections.values()) {
                destroyQuietly(connection);
            }
            rangeConnections.clear();
            rangeChunkCursor = 0;
            bridgeNode = bridge;
        }
        if (host.getModules().isEmpty()) {
            for (IGridConnection connection : connections.values()) {
                destroyQuietly(connection);
            }
            connections.clear();
            for (IGridConnection connection : rangeConnections.values()) {
                destroyQuietly(connection);
            }
            rangeConnections.clear();
            publish("待机（未连接模块）");
            loggedOnline = false;
            return;
        }

        Map<Long, IGridNode> desired = collectModuleNodes();

        // 1) 回收已不在广播范围内的连接（模块拆了 / 仓拆了 / 结构重判）
        for (Iterator<Map.Entry<Long, IGridConnection>> it = connections.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Long, IGridConnection> entry = it.next();
            if (!desired.containsKey(entry.getKey())) {
                destroyQuietly(entry.getValue());
                it.remove();
            }
        }

        // 2) 逐节点确保已广播
        int linked = 0;
        int skipped = 0;
        for (Map.Entry<Long, IGridNode> entry : desired.entrySet()) {
            if (ensureConnection(entry.getKey(), entry.getValue(), bridge)) {
                linked++;
            } else {
                skipped++;
            }
        }

        // 3) 楼内范围接管（均分塔「范围模式」：只发现没有物理连接的单节点 AE 子网）
        maintainRange(bridge);

        int rangeLinked = rangeConnections.size();
        int totalLinked = linked + rangeLinked;
        String text = "在线：广播 " + totalLinked + " 路 ME 节点";
        if (rangeLinked > 0) {
            text += "（其中楼内范围 " + rangeLinked + "）";
        }
        if (skipped > 0) {
            text += "（跳过已接线 " + skipped + "）";
        }
        publish(text, totalLinked);
        if (totalLinked > 0 && !loggedOnline) {
            loggedOnline = true;
            GTLModular.LOGGER.info("[gtlmodular] 地心 @{}：AE 无线广播上线，已接管 {} 路 ME 节点（模块 {} + 楼内范围 {}，绑定 {} {}）",
                    host.getPos(), totalLinked, linked, rangeLinked, boundDim, boundPos.toShortString());
        }
    }

    // ------------------------------------------------------------------
    // 楼内范围接管（分区块游标扫描，避免一次性遍历大楼卡服）
    // ------------------------------------------------------------------

    private void maintainRange(IGridNode bridge) {
        Level level = host.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        BlockPos center = host.getPos();
        int x0 = center.getX() - RANGE_R, x1 = center.getX() + RANGE_R;
        int y0 = center.getY() - RANGE_VER, y1 = center.getY() + RANGE_VER;
        int z0 = center.getZ() - RANGE_R, z1 = center.getZ() + RANGE_R;
        int chx0 = x0 >> 4, chx1 = x1 >> 4;
        int chz0 = z0 >> 4, chz1 = z1 >> 4;
        int nx = chx1 - chx0 + 1;
        int nz = chz1 - chz0 + 1;
        int total = nx * nz;
        if (total <= 0) {
            return;
        }
        if (rangeChunkCursor < 0 || rangeChunkCursor >= total) {
            rangeChunkCursor = 0;
        }
        int end = Math.min(total, rangeChunkCursor + RANGE_CHUNKS_PER_MAINTENANCE);
        for (int i = rangeChunkCursor; i < end; i++) {
            int cx = chx0 + (i % nx);
            int cz = chz0 + (i / nx);
            scanChunkForWireless(serverLevel, cx, cz, x0, x1, y0, y1, z0, z1, bridge);
        }
        rangeChunkCursor = end >= total ? 0 : end;

        // 回收已经拆掉/失效的范围连接（只需检查少量已接管坐标）
        for (Iterator<Map.Entry<Long, IGridConnection>> it = rangeConnections.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Long, IGridConnection> entry = it.next();
            BlockPos pos = BlockPos.of(entry.getKey());
            if (!serverLevel.isLoaded(pos)) {
                destroyQuietly(entry.getValue());
                it.remove();
                continue;
            }
            BlockEntity be = serverLevel.getBlockEntity(pos);
            if (!(be instanceof IInWorldGridNodeHost)) {
                destroyQuietly(entry.getValue());
                it.remove();
            }
        }
    }

    private void scanChunkForWireless(ServerLevel level, int chunkX, int chunkZ,
                                      int x0, int x1, int y0, int y1, int z0, int z1,
                                      IGridNode bridge) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
        if (chunk == null) {
            return;
        }
        // 复制一份再遍历：连接动作可能触碰区块实体表
        for (BlockEntity blockEntity : new ArrayList<>(chunk.getBlockEntities().values())) {
            BlockPos pos = blockEntity.getBlockPos();
            if (pos.getX() < x0 || pos.getX() > x1 || pos.getY() < y0 || pos.getY() > y1
                    || pos.getZ() < z0 || pos.getZ() > z1) {
                continue;
            }
            if (!(blockEntity instanceof IInWorldGridNodeHost)
                    && !(blockEntity instanceof IGridConnectedBlockEntity)) {
                continue;
            }
            long key = pos.asLong();
            if (connections.containsKey(key)) {
                // 模块/主机部件由精确路径处理，避免重复连线
                continue;
            }
            if (blockEntity.getBlockPos().equals(host.getPos())) {
                continue;
            }
            try {
                scanBlockEntityForWireless(key, blockEntity, bridge);
            } catch (RuntimeException ignored) {
                // 单个 mod 的节点宿主异常不能打断服务端 tick；下一轮再试
            }
        }
    }

    /**
     * 从一个方块实体上取 AE 节点并尝试接管。
     *
     * <p>为什么先看 {@link IGridConnectedBlockEntity}：NeoEco 的存储子系统主机
     * （{@code ECOStorageSystemBlockEntity}）虽然继承 {@code AENetworkBlockEntity}，
     * 但它的 {@code getGridConnectableSides} 只在<b>相邻方块也是 AE 设备</b>时才暴露面，
     * 孤零零放在那里时六面 {@code getGridNode} 全是 null —— 只看 {@link IInWorldGridNodeHost}
     * 会漏掉整台存储主机。{@code getMainNode().getNode()} 不受暴露面限制。
     */
    private void scanBlockEntityForWireless(long key, BlockEntity blockEntity, IGridNode bridge) {
        if (blockEntity instanceof IGridConnectedBlockEntity gridConnected) {
            try {
                IManagedGridNode managed = gridConnected.getMainNode();
                IGridNode node = managed == null ? null : managed.getNode();
                if (node != null && tryRangeConnect(key, node, bridge)) {
                    return;
                }
            } catch (RuntimeException ignored) {
                // 继续试暴露面
            }
        }
        if (blockEntity instanceof IInWorldGridNodeHost nodeHost) {
            for (Direction side : Direction.values()) {
                try {
                    IGridNode node = nodeHost.getGridNode(side);
                    if (node != null && tryRangeConnect(key, node, bridge)) {
                        return;
                    }
                } catch (RuntimeException ignored) {
                    // 该面失效，试下一面
                }
            }
        }
    }

    /**
     * 尝试把一个节点所在子网并入主网络。
     *
     * <ul>
     *   <li>单节点孤岛 ⇒ 接管（均分塔范围模式）；</li>
     *   <li>多节点但「没有原版 AE2 控制器」⇒ 也接管（均分塔点对点模式口径）：
     *       NeoEco 存储子系统这类多方块由多个 BE 组成一张无控制器子网，连一个节点即可整网并入；</li>
     *   <li>有原版 AE2 控制器的网络 ⇒ 视为别人的主网，不抢。</li>
     * </ul>
     */
    private boolean tryRangeConnect(long key, IGridNode node, IGridNode bridge) {
        if (node == bridge) {
            return false;
        }
        IGridConnection existing = rangeConnections.get(key);
        if (existing != null) {
            try {
                IGridNode other = existing.getOtherSide(bridge);
                if (other != null && other.getGrid() == bridge.getGrid()) {
                    return true;
                }
            } catch (RuntimeException ignored) {
                // 失效，下面重建
            }
            destroyQuietly(existing);
            rangeConnections.remove(key);
        }
        try {
            IGrid targetGrid = node.getGrid();
            IGrid bridgeGrid = bridge.getGrid();
            if (targetGrid == bridgeGrid) {
                return true; // 已经在主网络里
            }
            if (targetGrid.size() > 1 && hasController(targetGrid)) {
                return false; // 有控制器的网络不抢
            }
            rangeConnections.put(key, GridHelper.createConnection(bridge, node));
            GTLModular.LOGGER.info("[gtlmodular] 地心 @{}：AE 无线广播接管楼内设备 @{}（子网节点数 {}，无控制器）",
                    host.getPos(), BlockPos.of(key).toShortString(), targetGrid.size());
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    /** 子网是否有原版 AE2 控制器（有则视为别人的主网，不抢）。 */
    private static boolean hasController(IGrid grid) {
        try {
            var pathing = grid.getPathingService();
            return pathing != null && pathing.getControllerState() != ControllerState.NO_CONTROLLER;
        } catch (RuntimeException ignored) {
            // 判不清就当有控制器，保守不抢
            return true;
        }
    }

    /** 解析绑定目标 → 无线访问点的网格节点。 */
    private IGridNode resolveBridgeNode() {
        MinecraftServer server = host.getLevel() instanceof ServerLevel sl ? sl.getServer() : null;
        if (server == null || boundDim == null || boundPos == null) {
            return null;
        }
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, boundDim));
        if (level == null || !level.isLoaded(boundPos)) {
            return null;
        }
        BlockEntity blockEntity = level.getBlockEntity(boundPos);
        if (!(blockEntity instanceof IWirelessAccessPoint accessPoint) || !accessPoint.isActive()) {
            return null;
        }
        IGridNode node = accessPoint.getActionableNode();
        if (node == null || !node.isActive()) {
            return null;
        }
        return node;
    }

    /** 已连接模块里所有 ME 部件的格节点：key = 部件坐标。 */
    private Map<Long, IGridNode> collectModuleNodes() {
        Map<Long, IGridNode> desired = new LinkedHashMap<>();
        // 有模块连接时，地心大楼自己结构里的 ME 部件（如果有）也一起广播
        collectParts(host, desired);
        for (IModularMachineModule<MoltenModuleHostMachine, ?> module : host.getModules()) {
            if (!module.isFormed() || !(module instanceof MultiblockControllerMachine controller)) {
                continue;
            }
            collectParts(controller, desired);
        }
        return desired;
    }

    private static void collectParts(MultiblockControllerMachine controller, Map<Long, IGridNode> desired) {
        for (IMultiPart part : controller.getParts()) {
            if (!(part instanceof com.gregtechceu.gtceu.api.machine.MetaMachine partMachine)
                    || !(part instanceof IGridConnectedMachine gridMachine)) {
                continue;
            }
            IManagedGridNode managedNode = gridMachine.getMainNode();
            if (managedNode == null) {
                continue;
            }
            IGridNode node = managedNode.getNode();
            if (node == null) {
                continue;
            }
            desired.put(partMachine.getPos().asLong(), node);
        }
    }

    /**
     * 保证某个 ME 节点已接到桥接节点上。
     *
     * <p>只接管「没有物理连接的单节点子网」（与均分塔范围模式同口径）：
     * <ul>
     *   <li>已经在主网络里 ⇒ 视为已广播，不重复连线；</li>
     *   <li>所在子网节点数 &gt; 1 ⇒ 玩家已经接线，不抢；</li>
     *   <li>其余（单节点、无控制器）⇒ 程序化连线，把子网并入主网络。</li>
     * </ul>
     *
     * <p>★ 2026-11-10 复核修复：{@link IGridNode#getGrid()} 对「已销毁节点」会抛
     * {@link IllegalStateException}（不是返回 null），而本方法每 20 tick 从
     * {@code RecipeLogic#serverTick} 走到 ⇒ 必须整段 try/catch，否则一次区块卸载竞态
     * 就能让服务端 tick 反复抛异常。另外「连接对象还在」不等于「还挂着」，
     * 还要校验两端仍在同一个 Grid，否则 AE2 已拆线、对象仍在 map 里时会永久误判为已广播。
     */
    private boolean ensureConnection(long key, IGridNode target, IGridNode bridge) {
        IGridConnection existing = connections.get(key);
        if (existing != null) {
            try {
                if (existing.getOtherSide(bridge) == target
                        && target.getGrid() == bridge.getGrid()) {
                    return true;
                }
            } catch (RuntimeException ignored) {
                // 节点/连接已失效 —— 下面按失效处理
            }
            destroyQuietly(existing);
            connections.remove(key);
        }
        try {
            IGrid targetGrid = target.getGrid();
            IGrid bridgeGrid = bridge.getGrid();
            if (targetGrid == bridgeGrid) {
                // 已经在主网络里（玩家自己接的线 / 别的无线线），不重复连
                return true;
            }
            if (targetGrid.size() > 1) {
                // 已接成多节点子网 ⇒ 不抢（与均分塔范围模式同口径）
                return false;
            }
            connections.put(key, GridHelper.createConnection(bridge, target));
            return true;
        } catch (RuntimeException error) {
            GTLModular.LOGGER.debug("[gtlmodular] 地心 @{}：AE 无线广播连接失败（{}）：{}",
                    host.getPos(), target.getOwner(), error.toString());
            return false;
        }
    }

    /**
     * 连接两端都还挂着这条线时，{@link IGridConnection#destroy()} 才是安全的。
     *
     * <p>★ 为什么必须先判：AE2 的 {@code GridConnection#destroy()} 第一步取
     * {@code sideA.getInternalGrid()}；节点已被 destroy（{@code myGrid == null}）时，
     * {@code getInternalGrid()} 会 {@code Grid.create(死节点)} 并注册进全局 TickHandler，
     * 而 {@code validateGrid} 因节点未 ready 直接 return ⇒ 只含死节点的新网络<b>永不回收</b>，
     * 死节点还持有 BE/Level 引用（真实泄漏）。节点销毁时 AE2 自己会先拆连接，
     * 所以这种连接只需要从 map 移除，不能再 destroy。
     */
    private static boolean isConnectionAttached(IGridConnection connection) {
        try {
            IGridNode a = connection.a();
            IGridNode b = connection.b();
            return a != null && b != null
                    && a.getConnections().contains(connection)
                    && b.getConnections().contains(connection);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static void destroyQuietly(IGridConnection connection) {
        if (!isConnectionAttached(connection)) {
            return;
        }
        try {
            connection.destroy();
        } catch (RuntimeException ignored) {
            // 与 AE2 自身的卸载/拆除竞态：它已经先销毁了
        }
    }

    private void publish(String status) {
        publish(status, connections.size());
    }

    private void publish(String status, int activeLinks) {
        String text = status;
        if (boundDim != null && boundPos != null) {
            text += " · 绑定 " + boundDim.getPath() + " " + boundPos.toShortString();
        }
        host.setAeBroadcastStatus(text, activeLinks);
    }

    // ------------------------------------------------------------------
    // 存档
    // ------------------------------------------------------------------

    public void save(CompoundTag tag) {
        if (boundDim != null && boundPos != null) {
            tag.putString(TAG_DIM, boundDim.toString());
            tag.putLong(TAG_POS, boundPos.asLong());
        }
    }

    public void load(CompoundTag tag) {
        // 读档/修补可能发生在活机器上（部分工具直接改 BE NBT）⇒ 先拆干净旧连接再换绑定
        dropAll();
        this.pendingRebind = null;
        this.boundDim = null;
        this.boundPos = null;
        if (tag.contains(TAG_DIM) && tag.contains(TAG_POS)) {
            ResourceLocation dim = ResourceLocation.tryParse(tag.getString(TAG_DIM));
            if (dim != null) {
                this.boundDim = dim;
                this.boundPos = BlockPos.of(tag.getLong(TAG_POS));
            }
        }
        this.maintenanceQueued = true;
        this.loggedOnline = false;
    }

    public boolean isBound() {
        return boundDim != null && boundPos != null;
    }

    public ResourceLocation getBoundDim() {
        return boundDim;
    }

    public BlockPos getBoundPos() {
        return boundPos;
    }
}
