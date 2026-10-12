package com.gtlmodular.common.machine.nanochip;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * 真空传送管网的<b>连通分量</b>（方案 C「定向」的数据层）。
 *
 * <p>规则（详见 {@code NAC_下一阶段_管道与结构方案.md} §1.2 第 4 步）：
 * <ul>
 *   <li>节点 = <b>该颜色的真空传送管道</b> + <b>该颜色的真空端口</b>；边 = 六向相邻（只在同色节点之间）；</li>
 *   <li>端口<b>旁边没有同色管道</b> → 落在 {@link #UNWIRED}（分量 0）＝ 与"颜色就是线"的旧行为完全一致；</li>
 *   <li>端口<b>连上了管道</b> → 落在它所在的那个分量，只与该分量里的端口对接（这就是"定向"）。</li>
 * </ul>
 *
 * <p>实现上不需要"找对端"：每个端口只要拿到自己的分量号，后续把它当作**子通道键**用
 * （{@code (颜色, 分量)}）即可，搬运逻辑一行都不用改 —— 这也是"不铺管道零回归"的根据：
 * 一根管道都没有时，所有端口的分量号都是 0。
 *
 * <p>分量号是<b>按需惰性</b>算出来的（从被问到的端口做一次 BFS，穿过同色管道把沿途所有端口打成同一号），
 * 结果缓存在这里；管道/端口增删、管道换色、结构成形失效时调用 {@link #markDirty()} 让缓存失效。
 * 单次 BFS 只走管网，代价与"这个分量的管道数量"成正比。
 */
public final class NanochipPipeGraph {

    /** 没连到任何管道时的分量号（等价于旧的"同色全局对接"）。 */
    public static final int UNWIRED = 0;

    /**
     * ★ 全局"管网拓扑纪元"（2026-09-16 双模式路由新增）。
     *
     * <p>管道方块<b>不属于任何多方块</b>（{@link NanochipPipeMachine} 是独立方块），
     * 它<b>找不到</b>自己该通知哪台 NAC —— 所以不做"点对点通知"，改成一个全局累加计数器：
     * 任何管道/端口的<b>放置、破坏、换色</b>都 {@link #bumpTopologyEpoch()} 一次；
     * 每个 {@link NanochipPipeGraph} 缓存自己上次重算时的纪元号，号变了就整表失效。
     *
     * <p>代价：一次拓扑改动后，本维度上每台 NAC 会各自重算一次 BFS（管道数量级 10²–10³，毫秒级）；
     * 拓扑不变时<b>零开销</b>（纯查表，满足"搬运热路径 O(1)"）。
     * 全局共享（不按维度分）只会导致"别的维度铺了管子 ⇒ 这边也重算一次"，
     * 属可接受的极小过度失效，换来的是零耦合（管道不需要知道控制器在哪）。
     */
    private static volatile long TOPOLOGY_EPOCH = 0L;

    /** 管网拓扑变了（管道/端口 放置、破坏、换色）—— 由 {@link NanochipPipeMachine} / 端口调用。 */
    public static void bumpTopologyEpoch() {
        TOPOLOGY_EPOCH++;
    }

    /** 当前拓扑纪元号（测试/日志用）。 */
    public static long topologyEpoch() {
        return TOPOLOGY_EPOCH;
    }

    /** 本图最后一次重算时的拓扑纪元号。 */
    private long seenEpoch = -1L;

    /** 颜色 → (端口位置 → 分量号)。 */
    private final Map<NanochipColor, Map<BlockPos, Integer>> componentOf = new EnumMap<>(NanochipColor.class);

    /** 下一个要分配的分量号（从 1 开始，0 留给 {@link #UNWIRED}）。 */
    private int nextComponent = 1;

    /** 脏标记：管道/端口变动后置脏，下次查询时重算。 */
    private boolean dirty = true;

    public void markDirty() {
        dirty = true;
    }

    public boolean isDirty() {
        return dirty;
    }

    /** 清空（控制器结构失效/卸载时用）。 */
    public void clear() {
        componentOf.clear();
        nextComponent = 1;
        dirty = true;
        seenEpoch = -1L;
    }

    private Map<BlockPos, Integer> map(NanochipColor color) {
        return componentOf.computeIfAbsent(color, c -> new HashMap<>());
    }

    /**
     * 问某个端口的分量号（{@link #UNWIRED} = 它没连管道）。
     *
     * @param level    服务端世界
     * @param color    该端口的颜色
     * @param portPos  端口位置
     */
    public int componentOf(Level level, NanochipColor color, BlockPos portPos) {
        if (level == null || color == null || portPos == null) {
            return UNWIRED;
        }
        Map<BlockPos, Integer> cache = map(color);
        Integer known = cache.get(portPos);
        if (known != null) {
            return known;
        }
        // 失效判定：显式脏（结构成形/失效）+ 全局拓扑纪元变了（管道/端口增删换色）
        long epoch = TOPOLOGY_EPOCH;
        if (dirty || seenEpoch != epoch) {
            // 只清缓存，不清已分配号（号本身无意义，一致性靠"同一次 BFS 打成同一号"保证）
            for (Map<BlockPos, Integer> m : componentOf.values()) {
                m.clear();
            }
            dirty = false;
            seenEpoch = epoch;
        }
        return floodFill(level, color, portPos);
    }

    /**
     * 从 {@code start} 出发穿过同色管道，把沿途所有同色端口打成同一个分量号。
     *
     * @return {@code start} 的分量号（旁边没有同色管道 → {@link #UNWIRED}）
     */
    private int floodFill(Level level, NanochipColor color, BlockPos start) {
        Map<BlockPos, Integer> cache = map(color);

        // 先看起点旁边有没有同色管道；没有就是"没铺管道"
        boolean wired = false;
        for (Direction side : Direction.values()) {
            if (isPipeOf(level, start.relative(side), color)) {
                wired = true;
                break;
            }
        }
        if (!wired) {
            cache.put(start, UNWIRED);
            return UNWIRED;
        }

        int id = nextComponent++;
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        java.util.Set<BlockPos> seenPipes = new java.util.HashSet<>();
        queue.add(start);
        cache.put(start, id);

        while (!queue.isEmpty()) {
            BlockPos cur = queue.poll();
            for (Direction side : Direction.values()) {
                BlockPos np = cur.relative(side);
                if (isPipeOf(level, np, color)) {
                    if (seenPipes.add(np)) {
                        queue.add(np);
                    }
                } else if (isPortOf(level, np, color)) {
                    cache.putIfAbsent(np, id);   // 同分量的端口统一打号
                }
            }
        }
        // 管道自己也可能挨着别的管道（已在 seenPipes 里）；端口只在这里登记
        return id;
    }

    /** 该位置是不是"这个颜色的管道"。 */
    private static boolean isPipeOf(Level level, BlockPos pos, NanochipColor color) {
        return level.getBlockEntity(pos) instanceof MetaMachineBlockEntity be
                && be.getMetaMachine() instanceof NanochipPipeMachine pipe
                && pipe.color() == color;
    }

    /** 该位置是不是"这个颜色的真空端口"。 */
    private static boolean isPortOf(Level level, BlockPos pos, NanochipColor color) {
        return level.getBlockEntity(pos) instanceof MetaMachineBlockEntity be
                && be.getMetaMachine() instanceof NanochipVacuumHatchMachine hatch
                && hatch.color() == color;
    }
}
