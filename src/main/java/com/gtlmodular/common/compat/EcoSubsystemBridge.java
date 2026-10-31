package com.gtlmodular.common.compat;

import com.gtlmodular.common.item.ShanhaiUltimateTerminalConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

import java.lang.reflect.Method;

/**
 * 与 Neo ECO AE（eco）的软依赖桥：打开世界里已成型 ECO 子系统（计算/合成/存储…）的 UI。
 *
 * <p>全部走反射：没装 eco 时类不会加载，也不影响启动。eco 的
 * {@code BlockUIMenuType.openUI(ServerPlayer, BlockPos)} 是公开静态方法，直接借用它的原生 UI。
 */
public final class EcoSubsystemBridge {

    private static final String UI_TYPE = "cn.dancingsnow.neoecoae.platform.ui.runtime.gui.factory.BlockUIMenuType";
    private static final String UI_INTERFACE = UI_TYPE + "$BlockUI";
    private static final String COMPUTATION_BE =
            "cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationSystemBlockEntity";
    private static final String INTERFACE_BE =
            "cn.dancingsnow.neoecoae.blocks.entity.ECOMachineInterfaceBlockEntity";
    private static final int SCAN_RADIUS = 32;

    private static Method openUi;
    private static boolean resolved;

    private EcoSubsystemBridge() {}

    private static synchronized void resolve() {
        if (resolved) return;
        resolved = true;
        try {
            Class<?> type = Class.forName(UI_TYPE);
            openUi = type.getMethod("openUI", ServerPlayer.class, BlockPos.class);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            openUi = null;
        }
    }

    /** 方块本身是否实现了 eco 的 BlockUIMenuType.BlockUI。 */
    private static boolean blockHasUi(Block block) {
        for (Class<?> c = block.getClass(); c != null; c = c.getSuperclass()) {
            for (Class<?> i : c.getInterfaces()) {
                if (i.getName().equals(UI_INTERFACE)) return true;
                for (Class<?> parent : i.getInterfaces()) {
                    if (parent.getName().equals(UI_INTERFACE)) return true;
                }
            }
        }
        return false;
    }

    private static boolean isFormed(BlockEntity be) {
        try {
            Method formed = be.getClass().getMethod("isFormed");
            Object value = formed.invoke(be);
            return value instanceof Boolean b && b;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // 不是 eco 多方块就按“没有已成型”处理
            return false;
        }
    }

    public static boolean open(ServerPlayer player, BlockPos pos) {
        resolve();
        if (openUi == null) return false;
        try {
            Object result = openUi.invoke(null, player, pos);
            return result instanceof Boolean b && b;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }

    /** 目标方块是否是「计算系统通讯接口」（ECOMachineInterface 且 cluster 属于计算集群）。 */
    public static boolean isComputationInterface(ServerLevel level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null || !be.getClass().getName().equals(INTERFACE_BE)) return false;
        try {
            Method supports = be.getClass().getMethod("supportsComputationInterfaceUi");
            Object value = supports.invoke(be);
            return value instanceof Boolean b && b;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }

    /** 潜行右击：绑定计算通讯接口（与绑定 AE 网络同款操作）。 */
    public static boolean tryBindComputationInterface(ServerPlayer player, ItemStack terminal, BlockPos pos) {
        if (!(player.level() instanceof ServerLevel level)) return false;
        if (!isComputationInterface(level, pos)) return false;
        ShanhaiUltimateTerminalConfig.setBoundEco(terminal, GlobalPos.of(level.dimension(), pos.immutable()));
        return true;
    }

    /**
     * 远程打开**计算子系统主机**的 UI（不是通讯接口自己的 UI）。
     *
     * <p>绑定的是计算通讯接口；这里从接口 BE 取它所属的 NEComputationCluster，
     * 再取 cluster.getController()（ECOComputationSystemBlockEntity）作为真正要打开的方块。
     */
    public static boolean openBound(ServerPlayer player, GlobalPos bound) {
        if (bound == null) return false;
        if (!(player.level() instanceof ServerLevel level)) return false;
        if (!bound.dimension().equals(level.dimension())) return false;
        // 远程 UI 会绕过 eco 的 BlockUIHolder 有效性检查，导致结构拆除后残留幽灵界面并让 AE CPU 列表错乱。
        // 这里退回安全行为：只允许 8 格内打开；更远时提示靠近。
        if (player.blockPosition().distSqr(bound.pos()) > 64.0) {
            com.gtlmodular.GTLModular.LOGGER.info("[哦鲸鲸终端] 拒绝远距离打开（>8 格）：{}", bound.pos());
            return false;
        }
        // 接口周围 5×5 区块载入内存：集群/主机可能跨区块，未加载时 getCluster() 会是 null
        int cx = bound.pos().getX() >> 4;
        int cz = bound.pos().getZ() >> 4;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                level.getChunk(cx + dx, cz + dz);
            }
        }
        BlockEntity be = level.getBlockEntity(bound.pos());
        if (be == null) {
            com.gtlmodular.GTLModular.LOGGER.info("[哦鲸鲸终端] 远程打开失败：接口 BE 未加载 {}", bound.pos());
            return false;
        }

        HostTarget target = hostTarget(be);
        if (target == null) {
            com.gtlmodular.GTLModular.LOGGER.info("[哦鲸鲸终端] 远程打开失败：接口 {} 的集群里没有主机 controller", be.getClass().getName());
            return false;
        }

        com.gtlmodular.GTLModular.LOGGER.info("[哦鲸鲸终端] 打开绑定主机 {}", target.pos());
        boolean opened = open(player, target.pos());
        if (!opened) {
            com.gtlmodular.GTLModular.LOGGER.info("[哦鲸鲸终端] 远程打开失败：openUI 返回 false，host={}", target.pos());
        }
        return opened;
    }

    /** 主机坐标 + 集群所有 BE 所在区块（chunkX << 32 | chunkZ）。 */
    private record HostTarget(BlockPos pos, java.util.List<Long> chunks) {}

    /** 从接口/主机的 BlockEntity 反射找它所在计算集群的主机控制器，并收集集群区块。 */
    private static HostTarget hostTarget(BlockEntity be) {
        try {
            Object cluster = be.getClass().getMethod("getCluster").invoke(be);
            if (cluster == null) return null;
            Object controller = cluster.getClass().getMethod("getController").invoke(cluster);
            if (!(controller instanceof BlockEntity controllerBe)) return null;
            BlockPos host = controllerBe.getBlockPos();

            java.util.LinkedHashSet<Long> chunks = new java.util.LinkedHashSet<>();
            chunks.add(chunkKey(host));
            try {
                Object iterator = cluster.getClass().getMethod("getBlockEntities").invoke(cluster);
                if (iterator instanceof java.util.Iterator<?> it) {
                    while (it.hasNext() && chunks.size() < 64) {
                        Object next = it.next();
                        if (next instanceof BlockEntity clusterBe) {
                            chunks.add(chunkKey(clusterBe.getBlockPos()));
                        }
                    }
                }
            } catch (ReflectiveOperationException ignored) {
                // 拿不到迭代器就至少发主机区块
            }
            return new HostTarget(host, java.util.List.copyOf(chunks));
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // 不是 eco 计算集群/没成型
        }
        return null;
    }

    private static long chunkKey(BlockPos pos) {
        return ((long) (pos.getX() >> 4) << 32) | ((pos.getZ() >> 4) & 0xFFFFFFFFL);
    }

    /** 右键用：目标方块是已成型 eco 子系统才打开它的 UI。 */
    public static boolean tryOpen(ServerPlayer player, Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel)) return false;
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null || !isFormed(be)) return false;
        if (!blockHasUi(level.getBlockState(pos).getBlock())) return false;
        return open(player, pos);
    }

    /** 附近最近的已成型计算子系统（只找加载中的区块，不强制加载）。 */
    public static BlockPos findNearestComputation(ServerPlayer player, int radius) {
        if (!(player.level() instanceof ServerLevel level)) return null;
        BlockPos center = player.blockPosition();
        int minChunkX = (center.getX() - radius) >> 4;
        int maxChunkX = (center.getX() + radius) >> 4;
        int minChunkZ = (center.getZ() - radius) >> 4;
        int maxChunkZ = (center.getZ() + radius) >> 4;
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                if (!level.hasChunk(cx, cz)) continue;
                LevelChunk chunk = level.getChunk(cx, cz);
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (!be.getClass().getName().equals(COMPUTATION_BE)) continue;
                    if (!isFormed(be)) continue;
                    BlockPos pos = be.getBlockPos();
                    double dist = pos.distSqr(center);
                    if (dist <= (double) radius * radius && dist < bestDist) {
                        bestDist = dist;
                        best = pos;
                    }
                }
            }
        }
        return best;
    }

    public static int scanRadius() {
        return SCAN_RADIUS;
    }

    public static boolean openNearestComputation(ServerPlayer player) {
        BlockPos pos = findNearestComputation(player, SCAN_RADIUS);
        if (pos == null) return false;
        return open(player, pos);
    }

    /** 附近是否装着 eco（不加载任何类，仅凭注册的方块 id 判断会过重，这里按反射可用性即可）。 */
    public static boolean isEcoPresent() {
        resolve();
        return openUi != null;
    }
}
