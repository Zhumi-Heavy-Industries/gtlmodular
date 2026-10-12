package com.gtlmodular.client.ctm;

import com.gtlmodular.common.data.nanochip.NanochipModContext;
import com.gtlmodular.common.diag.SwallowedErrors;
import com.gregtechceu.gtceu.api.block.IMachineBlock;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gtlmodular.common.machine.nanochip.NanochipAssemblyComplexMachine;
import com.gtlmodular.common.machine.nanochip.NanochipModuleMachine;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;

/** 同步字符串不可变，解码集合也整体替换，避免网格化线程读到半更新的仓位。 */
public final class NacSlotIndex {

    /** 键包含维度，避免不同世界的相同坐标互相命中。 */
    private static final Map<Key, Entry> CACHE = new ConcurrentHashMap<>();

    /** 限制派生缓存容量，避免反复读档或切换维度累积控制器坐标。 */
    private static final int MAX_CACHE_ENTRIES = 4096;

    private static final AtomicInteger WARN_BUDGET = new AtomicInteger(3);

    private NacSlotIndex() {
    }

    private record Key(ResourceKey<Level> dimension, long controller) {
    }

    private record Entry(String source, Set<Long> slots) {
    }

    /** 网格化视图不一定是 Level，因此接收 BlockGetter，维度由控制器自身的世界取得。 */
    public static boolean isNacSlot(BlockGetter level, BlockPos pos) {
        try {
            if (level == null || pos == null) {
                return false;
            }
            MetaMachine machine = MetaMachine.getMachine(level, pos);
            if (!(machine instanceof IMultiPart part)) {
                return false;
            }
            long key = pos.asLong();
            for (IMultiController controller : part.getControllers()) {
                if (!(controller instanceof MetaMachine meta)) {
                    continue;
                }
                Set<Long> slots = slotsOf(meta);
                if (slots != null && slots.contains(key)) {
                    return true;
                }
            }
            return false;
        } catch (Throwable failure) {
            SwallowedErrors.report("NacSlotIndex#isNacSlot", failure);
            // 缺少数据时保持原外观，不能猜成 NAC 仓位或让异常中断区块渲染。
            warnFailure(failure);
            return false;
        }
    }

    /** 提供候选快照以避免逐帧扫描世界；调用方仍需复核仓位，缓存可能已经过时。 */
    public static Set<Long> snapshot() {
        Set<Long> out = new HashSet<>();
        for (Entry entry : CACHE.values()) {
            out.addAll(entry.slots());
        }
        return out;
    }

    /** 按控制器保留已发布清单，供刷新器识别哪份清单变化并触发区块重新网格化。 */
    public static List<ControllerSlots> publishedCache() {
        try {
            List<ControllerSlots> out = new ArrayList<>(CACHE.size());
            for (Map.Entry<Key, Entry> cached : CACHE.entrySet()) {
                Key key = cached.getKey();
                Entry entry = cached.getValue();
                if (key == null || key.dimension() == null || entry == null) {
                    continue;
                }
                out.add(new ControllerSlots(key.dimension(), key.controller(),
                        BlockPos.of(key.controller()), entry.source(), entry.slots()));
            }
            return out;
        } catch (Throwable failure) {
            SwallowedErrors.report("NacSlotIndex#publishedCache", failure);
            warnFailure(failure);
            return List.of();
        }
    }

    public record ControllerSlots(ResourceKey<Level> dimension, long controller, BlockPos pos,
                                  String source, Set<Long> slots) {

        public String key() {
            return dimension.location() + "@" + controller;
        }
    }

    /** 补足被其他仓包围、不会被机壳邻居查询触及的仓位；只扫描已加载区块的方块实体。 */
    public static int seedNearby(Level level, BlockPos center, int chunkRadius) {
        if (level == null || center == null || chunkRadius < 0) {
            return 0;
        }
        try {
            int hits = 0;
            int cx = center.getX() >> 4;
            int cz = center.getZ() >> 4;
            for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
                for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                    var chunk = level.getChunkSource().getChunk(cx + dx, cz + dz, false);
                    if (chunk == null) {
                        continue;
                    }
                    for (BlockPos pos : chunk.getBlockEntities().keySet()) {
                        if (!(level.getBlockState(pos).getBlock() instanceof IMachineBlock)) {
                            continue;
                        }
                        if (isNacSlot(level, pos)) {
                            hits++;
                        }
                    }
                }
            }
            return hits;
        } catch (Throwable failure) {
            SwallowedErrors.report("NacSlotIndex#seedNearby", failure);
            warnFailure(failure);
            return 0;
        }
    }

    /** 同步值变化时整体替换集合，避免渲染线程与解码线程并发修改同一集合。 */
    private static Set<Long> slotsOf(MetaMachine controller) {
        String published = publishedSlots(controller);
        if (published == null || published.isEmpty()) {
            return null;
        }
        Level controllerLevel = controller.getLevel();
        Key key = new Key(controllerLevel == null ? null : controllerLevel.dimension(),
                controller.getPos().asLong());
        Entry cached = CACHE.get(key);
        if (cached != null && cached.source().equals(published)) {
            return cached.slots();
        }
        Set<Long> built = decode(published);
        CACHE.put(key, new Entry(published, built));
        if (CACHE.size() > MAX_CACHE_ENTRIES) {
            CACHE.clear();
        }
        return built;
    }

    private static String publishedSlots(MetaMachine controller) {
        if (controller instanceof NanochipAssemblyComplexMachine host) {
            return host.nacSlotsEncoded();
        }
        if (controller instanceof NanochipModuleMachine module) {
            return module.nacSlotsEncoded();
        }
        return null;
    }

    /** 坏片段只影响局部外观，跳过它以免一格无效数据中断整块渲染。 */
    private static Set<Long> decode(String encoded) {
        Set<Long> out = new HashSet<>();
        for (String token : encoded.split(",")) {
            String trimmed = token.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                out.add(Long.parseLong(trimmed));
            } catch (NumberFormatException ignored) {
            }
        }
        return out;
    }

    public static void reset() {
        CACHE.clear();
    }

    private static void warnFailure(Throwable failure) {
        if (WARN_BUDGET.getAndDecrement() > 0) {
            NanochipModContext.logger().warn("[gtlmodular] CTM: 【NAC 仓位索引】查询抛异常 —— 已吞掉并返回 false"
                    + "（本次不改任何贴图；外观 = 现状）。这是 CTM 的 bug，请把下面这条堆栈回传。", failure);
        }
    }
}
