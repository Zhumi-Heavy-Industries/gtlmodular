package com.gtlmodular.client.ctm;

import com.gtlmodular.common.data.nanochip.NanochipModContext;
import com.gtlmodular.common.diag.SwallowedErrors;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** 仓位同步不会自动更新已烘焙的连接掩码，需在清单变化时标脏相关区块段。 */
public final class NacSlotRefreshTracker {

    public static final boolean ENABLED = true;

    /** 短窗口内重标，覆盖同步到达与区块重新网格化之间的时差。 */
    public static final int WINDOW_TICKS = 10;

    public static final int MAX_SECTIONS_PER_TICK = 64;

    /** 限制单控制器的标脏工作量，避免大结构变化集中到一个 tick。 */
    public static final int MAX_DIRTY_PER_CONTROLLER = 256;

    public static final int MAX_BASELINE_ENTRIES = 4096;

    public static final int MAX_WINDOW_ENTRIES = 512;

    public static final int MAX_DIAG_LINES = 200;

    private static final Map<String, Baseline> BASELINE = new HashMap<>();

    private static final Map<Long, Integer> WINDOW = new HashMap<>();

    private static ClientLevel lastLevel;

    private static final AtomicInteger DIAG_LINES = new AtomicInteger(MAX_DIAG_LINES);
    private static long lastDiagTick = Long.MIN_VALUE;

    private NacSlotRefreshTracker() {
    }

    public static void reset() {
        BASELINE.clear();
        WINDOW.clear();
    }

    /** 在客户端 tick 处理标脏，避免以逐帧频率改动正在提交几何的渲染状态。 */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (!ENABLED || event.phase != TickEvent.Phase.END) {
            return;
        }
        try {
            ClientLevel level = Minecraft.getInstance().level;
            if (level != lastLevel) {
                // 换世界后旧坐标不再有效，基线与重标窗口必须一起清空。
                lastLevel = level;
                BASELINE.clear();
                WINDOW.clear();
            }
            if (level == null) {
                return;
            }
            long now = level.getGameTime();
            List<NacSlotIndex.ControllerSlots> published = NacSlotIndex.publishedCache();
            int dirtied = trackChanges(level, published);
            dirtied += reDirtyWindow(level, now);
            if (dirtied > 0) {
                report(published, dirtied, now);
            }
        } catch (Throwable failure) {
            SwallowedErrors.report("NacSlotRefreshTracker#onClientTick", failure);
            warnFailure(failure);
        }
    }

    /** 比较内容而非对象身份，避免同内容缓存重建引发无意义的重新网格化。 */
    private static int trackChanges(ClientLevel level, List<NacSlotIndex.ControllerSlots> published) {
        if (BASELINE.size() > MAX_BASELINE_ENTRIES) {
            BASELINE.clear();
        }
        Set<String> alive = new HashSet<>();
        int dirtied = 0;
        for (NacSlotIndex.ControllerSlots controller : published) {
            String key = controller.key();
            alive.add(key);
            Baseline previous = BASELINE.get(key);
            if (previous != null && previous.sameAs(controller)) {
                continue;
            }
            if (previous == null) {
                // 首次只登记空基线，使下一次比对把整份清单视为新增，避免反复跳过首次发布。
                BASELINE.put(key, new Baseline(controller.source(), Set.of()));
                continue;
            }
            Set<Long> added = new HashSet<>(controller.slots());
            added.removeAll(previous.slots());
            Set<Long> removed = new HashSet<>(previous.slots());
            removed.removeAll(controller.slots());
            BASELINE.put(key, new Baseline(controller.source(), copy(controller.slots())));
            dirtied += dirty(level, added, controller.pos());
            dirtied += dirty(level, removed, null);
            if (dirtied >= MAX_SECTIONS_PER_TICK) {
                break;
            }
        }
        // 移除不再存在的控制器，避免跨维度或反复读档累积基线。
        if (BASELINE.size() > alive.size()) {
            BASELINE.keySet().retainAll(alive);
        }
        return dirtied;
    }

    private static int dirty(ClientLevel level, Set<Long> positions, BlockPos fallback) {
        if (positions.isEmpty() && fallback == null) {
            return 0;
        }
        int count = 0;
        if (positions.isEmpty()) {
            markSection(level, fallback);
            return 1;
        }
        int seen = 0;
        for (long packed : positions) {
            if (seen++ >= MAX_DIRTY_PER_CONTROLLER) {
                break;
            }
            markSection(level, BlockPos.of(packed));
            count++;
            if (count >= MAX_SECTIONS_PER_TICK) {
                break;
            }
        }
        return count;
    }

    private static void markSection(ClientLevel level, BlockPos pos) {
        int cx = SectionPos.blockToSectionCoord(pos.getX());
        int cy = SectionPos.blockToSectionCoord(pos.getY());
        int cz = SectionPos.blockToSectionCoord(pos.getZ());
        net.minecraft.client.Minecraft.getInstance().levelRenderer.setSectionDirty(cx, cy, cz);
        WINDOW.put(SectionPos.asLong(cx, cy, cz), (int) Math.min(level.getGameTime() + WINDOW_TICKS, Integer.MAX_VALUE));
        // 重标是短期补偿，超容量时丢弃最早条目以限制缓存增长。
        while (WINDOW.size() > MAX_WINDOW_ENTRIES) {
            Iterator<Long> oldest = WINDOW.keySet().iterator();
            if (!oldest.hasNext()) {
                break;
            }
            WINDOW.remove(oldest.next());
        }
    }

    /** 有界重复标脏用于覆盖稍晚到达的同步，不依赖单次标记恰好赶上网格化。 */
    private static int reDirtyWindow(ClientLevel level, long now) {
        if (WINDOW.isEmpty()) {
            return 0;
        }
        int count = 0;
        Iterator<Map.Entry<Long, Integer>> it = WINDOW.entrySet().iterator();
        while (it.hasNext() && count < MAX_SECTIONS_PER_TICK) {
            Map.Entry<Long, Integer> entry = it.next();
            if (entry.getValue() <= now) {
                it.remove();
                continue;
            }
            long key = entry.getKey();
            net.minecraft.client.Minecraft.getInstance().levelRenderer.setSectionDirty(SectionPos.x(key), SectionPos.y(key), SectionPos.z(key));
            count++;
        }
        return count;
    }

    private static Set<Long> copy(Set<Long> slots) {
        return slots == null || slots.isEmpty() ? new HashSet<>() : new HashSet<>(slots);
    }

    private record Baseline(String source, Set<Long> slots) {
        boolean sameAs(NacSlotIndex.ControllerSlots controller) {
            return source != null && source.equals(controller.source()) && slots.equals(controller.slots());
        }
    }

    private static void report(List<NacSlotIndex.ControllerSlots> published, int sections, long now) {
        if (DIAG_LINES.get() <= 0 || now == lastDiagTick) {
            return;
        }
        lastDiagTick = now;
        DIAG_LINES.decrementAndGet();
        List<String> summary = new ArrayList<>();
        int total = 0;
        for (NacSlotIndex.ControllerSlots controller : published) {
            total += controller.slots().size();
            if (summary.size() < 4) {
                summary.add(controller.pos().toShortString() + "×" + controller.slots().size());
            }
        }
        NanochipModContext.logger().info("[gtlmodular] NAC 外壳连片重算：原因=仓位清单变化 ｜ 涉及 {} 个控制器 / {} 格 ｜ "
                        + "本次重烤区块段 {} 个（窗口 {} tick 内最多再标 {} 次）｜ 控制器={}{}",
                published.size(), total, sections, WINDOW_TICKS, WINDOW_TICKS,
                summary, published.size() > summary.size() ? " …" : "");
    }

    private static void warnFailure(Throwable failure) {
        if (DIAG_LINES.get() > 0) {
            DIAG_LINES.decrementAndGet();
            NanochipModContext.logger().warn("[gtlmodular] NAC 外壳连片重算：本 tick 抛异常 —— 已吞掉（本轮不标脏，"
                    + "外观 = 现状）。这是刷新补偿器的 bug，请把下面这条堆栈回传。", failure);
        }
    }

    public static int baselineSize() {
        return BASELINE.size();
    }

    public static int windowSize() {
        return WINDOW.size();
    }

    public static int diagBudgetLeft() {
        return DIAG_LINES.get();
    }
}
