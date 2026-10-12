package com.gtlmodular.common.machine.nanochip;

import com.gtlmodular.common.data.nanochip.NanochipModContext;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gtlmodular.common.data.nanochip.NanochipComplexPattern;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 用结构相对位置表定位拆块涉及的主机，避免每次事件扫描整座结构；最终判定仍交上游。 */
public final class NanochipStructurePosIndex {

    private NanochipStructurePosIndex() {
    }

    /** 默认启用拆块索引，关闭后不登记主机也不处理命中事件。 */
    public static final boolean ENABLED = true;

    /** 默认使用弱引用快查；关闭后从世界重新查机器，供不依赖缓存引用的路径使用。 */
    public static final boolean POINTER_LOOKUP = true;

    private static final int LOADED_PROBE_RADIUS = 32;
    private static final int LOADED_PROBE_STEP = 16;

    private static volatile long[] relativeCells;

    private static volatile boolean buildFailed;

    private static final Map<ResourceKey<Level>, Map<Long, HostEntry>> HOSTS = new ConcurrentHashMap<>();

    private static final class HostEntry {

        final long packed;

        final int x;
        final int y;
        final int z;

        /** 弱引用避免全局索引阻止卸载机器被回收。 */
        final WeakReference<NanochipAssemblyComplexMachine> pointer;

        HostEntry(long packed, int x, int y, int z, NanochipAssemblyComplexMachine host) {
            this.packed = packed;
            this.x = x;
            this.y = y;
            this.z = z;
            this.pointer = new WeakReference<>(host);
        }
    }

    public static long[] cells() {
        long[] local = relativeCells;
        if (local != null || buildFailed) {
            return local;
        }
        synchronized (NanochipStructurePosIndex.class) {
            if (relativeCells != null || buildFailed) {
                return relativeCells;
            }
            try {
                int[] flat = NanochipComplexPattern.hostCheckCells();
                int count = flat.length / 3;
                if (count == 0) {
                    buildFailed = true;
                    NanochipModContext.logger().warn("[gtlmodular] NAC 结构位置缓存：hostCheckCells() 为空 ⇒ "
                            + "本功能放弃（结构移除通知回到上游原有行为）");
                    return null;
                }
                long[] all = new long[count * NanochipExactPrecheck.ELEMENTS];
                int[] off = new int[3];
                int k = 0;
                for (int element = 0; element < NanochipExactPrecheck.ELEMENTS; element++) {
                    for (int i = 0; i < count; i++) {
                        int base = i * 3;
                        NanochipComplexPattern.elementOffset(element,
                                NanochipComplexPattern.NAC_CTRL_X - flat[base],
                                flat[base + 1] - NanochipComplexPattern.NAC_CTRL_ROW,
                                NanochipComplexPattern.NAC_CTRL_Z - flat[base + 2], off);
                        all[k++] = BlockPos.asLong(off[0], off[1], off[2]);
                    }
                }
                Arrays.sort(all);
                int uniq = 1;
                for (int i = 1; i < all.length; i++) {
                    if (all[i] != all[uniq - 1]) {
                        all[uniq++] = all[i];
                    }
                }
                long[] built = Arrays.copyOf(all, uniq);
                relativeCells = built;
                NanochipModContext.logger().info("[gtlmodular] NAC 结构位置缓存已建立：CHECK 格 {} × {} 朝向元素 "
                                + "⇒ 去重后 {} 项（约 {} KB，全模组一份；查表 = 每台主机一次二分）。"
                                + "依据：_census/结构位置缓存与移除失效_实施记录.md",
                        count, NanochipExactPrecheck.ELEMENTS, uniq, built.length * 8L / 1024L);
                return built;
            } catch (Throwable t) {
                buildFailed = true;
                NanochipModContext.logger().warn("[gtlmodular] NAC 结构位置缓存建表失败 ⇒ 本功能放弃"
                        + "（结构移除通知回到上游原有行为，不影响任何成形/失效判定）", t);
                return null;
            }
        }
    }

    public static int cachedCellCount() {
        long[] local = relativeCells;
        return local == null ? -1 : local.length;
    }

    public static void register(Level level, NanochipAssemblyComplexMachine host) {
        if (!ENABLED || NanochipModContext.config().isNanochipOriginalMode()
                || level == null || host == null || level.isClientSide) {
            return;
        }
        try {
            BlockPos pos = host.getPos();
            if (pos == null) {
                return;
            }
            long packed = pos.asLong();
            Map<Long, HostEntry> hosts = hostsOf(level.dimension(), true);
            if (hosts == null) {
                return;
            }
            hosts.put(packed, new HostEntry(packed, pos.getX(), pos.getY(), pos.getZ(), host));
            purgeDead(hosts);
        } catch (Throwable t) {
            NanochipModContext.logger().debug("[gtlmodular] NAC 结构位置登记失败（本功能对该台不可用，"
                    + "结构判定不受任何影响）：{}", t.toString());
        }
    }

    /** 注销使用登记时的维度键，避免依赖卸载阶段可能已不可用的世界引用。 */
    public static void unregister(ResourceKey<Level> dim, NanochipAssemblyComplexMachine host) {
        if (dim == null || host == null) {
            return;
        }
        try {
            Map<Long, HostEntry> hosts = HOSTS.get(dim);
            if (hosts == null) {
                return;
            }
            BlockPos pos = host.getPos();
            if (pos == null) {
                return;
            }
            long packed = pos.asLong();
            HostEntry entry = hosts.get(packed);
            if (entry == null) {
                return;
            }
            NanochipAssemblyComplexMachine current = entry.pointer.get();
            if (current != null && current != host) {
                return;
            }
            hosts.remove(packed, entry);                 // 按键和值一起删除，避免并发情况下误删同坐标的新条目。
        } catch (Throwable t) {
            NanochipModContext.logger().debug("[gtlmodular] NAC 结构位置注销失败（最坏只留一个空壳，"
                    + "使用前还有三道存活校验兜底）：{}", t.toString());
        }
    }

    /** 维度退出时整体清理，覆盖机器自身卸载回调未执行的情况。 */
    public static void forgetDimension(ResourceKey<Level> dim) {
        if (dim == null) {
            return;
        }
        try {
            HOSTS.remove(dim);
        } catch (Throwable t) {
            NanochipModContext.logger().debug("[gtlmodular] NAC 结构位置维度清理失败（弱引用兜底，无对象泄漏）：{}",
                    t.toString());
        }
    }

    public static int hostCount(Level level) {
        if (level == null) {
            return 0;
        }
        Map<Long, HostEntry> hosts = HOSTS.get(level.dimension());
        return hosts == null ? 0 : hosts.size();
    }

    private static Map<Long, HostEntry> hostsOf(ResourceKey<Level> dim, boolean create) {
        if (dim == null) {
            return null;
        }
        Map<Long, HostEntry> hosts = HOSTS.get(dim);
        if (hosts == null && create) {
            Map<Long, HostEntry> fresh = new ConcurrentHashMap<>();
            Map<Long, HostEntry> prev = HOSTS.putIfAbsent(dim, fresh);
            hosts = prev == null ? fresh : prev;
        }
        return hosts;
    }

    /** 弱引用回收不会自动删除 Map 条目，需要额外清理空壳。 */
    private static void purgeDead(Map<Long, HostEntry> hosts) {
        for (Map.Entry<Long, HostEntry> row : hosts.entrySet()) {
            HostEntry entry = row.getValue();
            NanochipAssemblyComplexMachine host = entry.pointer.get();
            if (host == null || host.isInValid()) {
                hosts.remove(row.getKey(), entry);
            }
        }
    }

    public static void noteBlockRemoved(Level level, BlockPos brokenPos) {
        if (!ENABLED || NanochipModContext.config().isNanochipOriginalMode()
                || level == null || level.isClientSide || brokenPos == null) {
            return;
        }
        try {
            Map<Long, HostEntry> hosts = HOSTS.get(level.dimension());
            if (hosts == null || hosts.isEmpty()) {
                return;
            }
            long[] table = cells();
            if (table == null) {
                return;
            }
            int bx = brokenPos.getX();
            int by = brokenPos.getY();
            int bz = brokenPos.getZ();
            for (Map.Entry<Long, HostEntry> row : hosts.entrySet()) {
                HostEntry entry = row.getValue();
                long key = BlockPos.asLong(bx - entry.x, by - entry.y, bz - entry.z);
                if (Arrays.binarySearch(table, key) < 0) {
                    continue;
                }
                NanochipAssemblyComplexMachine host = entry.pointer.get();
                if (host == null) {
                    hosts.remove(row.getKey(), entry);
                    continue;
                }
                if (!POINTER_LOOKUP) {
                    BlockPos hostPos = BlockPos.of(entry.packed);
                    if (MetaMachine.getMachine(level, hostPos) instanceof NanochipAssemblyComplexMachine world) {
                        host = world;
                    } else {
                        continue;
                    }
                }
                if (host.isInValid()) {
                    hosts.remove(row.getKey(), entry);
                    continue;
                }
                if (host.getPos().asLong() != entry.packed) {
                    hosts.remove(row.getKey(), entry);
                    continue;
                }
                host.noteCachedBlockRemoved(brokenPos);
            }
        } catch (Throwable t) {
            NanochipModContext.logger().debug("[gtlmodular] NAC 结构位置查表异常（已忽略，结构判定不受影响）：{}",
                    t.toString());
        }
    }

    /** 未加载区块不等于结构损坏，确认区块齐全后才能触发拆块重判。 */
    public static boolean structureChunksLoaded(Level level, BlockPos controllerPos) {
        if (level == null || controllerPos == null) {
            return false;
        }
        try {
            for (int dx = -LOADED_PROBE_RADIUS; dx <= LOADED_PROBE_RADIUS; dx += LOADED_PROBE_STEP) {
                for (int dz = -LOADED_PROBE_RADIUS; dz <= LOADED_PROBE_RADIUS; dz += LOADED_PROBE_STEP) {
                    if (!level.isLoaded(controllerPos.offset(dx, 0, dz))) {
                        return false;
                    }
                }
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
