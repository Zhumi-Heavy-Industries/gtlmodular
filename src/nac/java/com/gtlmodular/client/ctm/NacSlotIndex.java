package com.gtlmodular.client.ctm;

import com.gtlmodular.common.data.nanochip.NanochipModContext;
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

/**
 * <b>「这一格是不是某台成形 NAC 的仓位」的客户端索引</b> —— 让<b>任何 mod 的仓</b>（GTCEu 自己的仓、
 * GTMThings 的无线能源仓…）进了 NAC 之后也能与周围机壳连成一片（{@link MachineCtmBakedModel} 的
 * quad 判据之一）。
 *
 * <h2>数据从哪来（三层，全部走 GTCEu / LDLib 的既有同步通道，零自研网络包）</h2>
 * <ol>
 *   <li><b>仓位坐标清单</b>：NAC 主机 {@link NanochipAssemblyComplexMachine} 与每个模块
 *       {@link NanochipModuleMachine} 在<b>结构成形</b>时按 {@code MultiblockControllerMachine.getParts()}
 *       把「这一刻结构里真实站着的 part」的坐标编码成逗号分隔的 {@code BlockPos.asLong()} 字符串，
 *       写进各自新增的 {@code @DescSynced String syncedNacSlots}（失效时清空 ⇒ 空串）。
 *       <p>为什么用 {@code String} 而不是 {@code long[]} / {@code List<BlockPos>}：
 *       {@code @DescSynced} 的写入是「服务端算好一次性推给客户端」，而 LDLib 的集合类字段是**原地
 *       clear+addAll**（{@code SyncedFieldAccessors.collectionAccessor}）—— 渲染线程若在那半途迭代就会
 *       看到撕裂的集合。字符串是<b>不可变</b>的 ⇒ 发布永远是「一次引用写」，读方要么全旧要么全新。</li>
 *   <li><b>谁是我的控制器</b>：{@code IMultiPart.getControllers()} —— GTCEu 的
 *       {@code MultiblockPartMachine.controllerPositions} 带 {@code @DescSynced}
 *       （javap 逐字节确认：字段注解 {@code DescSynced + RequireRerender}，{@code getControllers()} =
 *       遍历该集合 + {@code MetaMachine.getMachine(getLevel(), pos)}）⇒ 客户端本来就知道
 *       「这一格的仓属于哪台控制器」，本类不需要自己扫世界、也不需要自己建索引表。</li>
 *   <li><b>本类</b>：把 ① 的那串坐标解成 {@code HashSet<Long>}（键 = {@code BlockPos.asLong()}），
 *       按「控制器坐标」缓存；<b>同步值一变就整体替换</b>（旧集合直接丢弃，绝不原地改）。</li>
 * </ol>
 *
 * <h2>安全口径（硬要求）</h2>
 * <ul>
 *   <li><b>拿不到数据 ⇒ 返回 false</b>（= {@link MachineCtmBakedModel} 什么都不改，外观 = 现状）；
 *       绝不抛异常（渲染路径抛异常 = 整个区块不渲染），绝不"拿不到就当真"；</li>
 *   <li>上限 {@link #MAX_CACHE_ENTRIES}：维度切换 / 反复读档时不会无界累积（超了整体清空重来 ——
 *       索引本来就是可重建的派生数据，丢了只是下一帧少换一张贴图）；</li>
 *   <li>本类只在<b>客户端</b>被调用（{@code MachineCtmBakedModel} / {@code CtmHullGroup} /
 *       {@code com.gtlmodular.client.renderer.NacMachineShellRenderer}），
 *       且不引用任何客户端专属类 ⇒ 即便被服务端类意外加载也不会挂（它自己没有任何静态初始化副作用）。</li>
 * </ul>
 *
 * <h2>★ 2026-09-18 本轮新增的两个入口（供「外壳罩」渲染器用）</h2>
 * <ul>
 *   <li>{@link #snapshot()}：把缓存里已解析的仓位<b>并起来</b>（纯内存，不查世界）——
 *       渲染器按距离取近处若干格，不再每帧盲扫 4096 格；</li>
 *   <li>{@link #seedNearby(Level, BlockPos, int)}：<b>有界播种</b>——扫相机附近若干区块的
 *       <b>方块实体表</b>（不加载区块、不扫方块），补上"四周全是别的仓、永远不被机壳 CTM 问到"
 *       的那类机器。</li>
 * </ul>
 * 两者都遵守同一口径：<b>拿不到 ⇒ 返回空 / 0（本帧不画），绝不猜、绝不抛。</b>
 */
public final class NacSlotIndex {

    /** 「控制器坐标 → 已解析的仓位集合」。键含维度（不同维度可以有同一个 BlockPos）。 */
    private static final Map<Key, Entry> CACHE = new ConcurrentHashMap<>();

    /**
     * 缓存条目上限（防御性：维度切换 / 反复读档 / 传送门来回 ⇒ 控制器坐标会累积）。
     *
     * <p>超了就整体清空：索引是<b>可重建的派生数据</b>，清空只会让下一帧多算几次
     * （而且只有当某一格真的在渲染时才会重算），比"无界增长"划算得多。
     */
    private static final int MAX_CACHE_ENTRIES = 4096;

    /** 兜底 WARN 的额度（限流：渲染路径刷日志比不连糟得多）。 */
    private static final AtomicInteger WARN_BUDGET = new AtomicInteger(3);

    private NacSlotIndex() {
    }

    /** 缓存键：维度 + 控制器坐标。 */
    private record Key(ResourceKey<Level> dimension, long controller) {
    }

    /** 缓存值：来源字符串（同步值原文）+ 由它解出的集合。同步值一变就整条替换。 */
    private record Entry(String source, Set<Long> slots) {
    }

    /**
     * ★ <b>唯一对外判据</b>：{@code (level, pos)} 这一格是不是某台<b>已成形 NAC</b> 的仓位。
     *
     * <pre>
     *   这一格没有机器                     → false
     *   有机器但不是 GTCEu 多方块 part      → false（别的 mod 的独立机器、空气、机壳块…）
     *   是 part，但控制器不是本模组 NAC      → false（普通多方块的仓：与本功能无关，外观照旧）
     *   是 NAC 的 part，且坐标在发布清单里   → ★ true（它的外壳面会被换成接口机壳 47 格变体）
     *   ★ 上面任何一步拿不到数据 / 抛异常    → false（什么都不改；绝不 true、绝不抛出去）
     * </pre>
     *
     * <p>★ 入参为什么是 {@code BlockGetter} 而不是 {@code Level}：区块网格化的那份视图
     * <b>不是</b> {@code Level} —— Embeddium 0.3.31 传的是 {@code WorldSlice}
     * （{@code implements BlockAndTintGetter}，javap 确认），vanilla 传的是 {@code RenderChunkRegion}。
     * 而 {@code MetaMachine.getMachine(BlockGetter, pos)} 只要求"能取到方块实体"，两种视图都满足
     * （Embeddium 的 {@code ClonedChunkSection.copyBlockEntities} 把**真** BE 实例搬进 map，不做克隆）。
     * 缓存键用的维度取自**控制器机器自己的** {@code getLevel()}（客户端恒为 {@code ClientLevel}）
     * ⇒ 与传进来的宽口径视图无关。
     */
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
            // ★★ 拿不到数据 ⇒ 返回 false（= 什么都不改），**绝不**抛异常、绝不返回 true。
            //    渲染路径（区块网格化线程）里抛异常最坏是整个区块不渲染；而"拿不到就当真"会把
            //    全包的机器外壳都换成接口机壳（比不连严重得多）。
            warnFailure(failure);
            return false;
        }
    }

    /**
     * ★ 2026-09-18 新增：<b>枚举「当前已知的全部候选仓位」</b>
     * （供 {@code com.gtlmodular.client.renderer.NacMachineShellRenderer} 用）。
     *
     * <h3>为什么需要它</h3>
     * 外壳罩渲染器必须知道"这一帧该看哪几格"，否则只能每帧扫相机附近的 4096 格再逐格判断
     * （纯浪费：里面 99% 是空气/机壳）。这里把 {@link #CACHE} 里**已经解析出来**的清单并起来
     * ⇒ 渲染器只需对这几格做距离剔除 / 视锥剔除 / 逐格复核。
     *
     * <h3>代价与语义（写清楚，别误用）</h3>
     * <ul>
     *   <li>O(已缓存控制器数 × 该控制器的仓位数)，纯内存操作、<b>不查世界</b>；
     *       每次资源重载 / {@link #reset()} 之后缓存为空 ⇒ 返回空集（渲染器据此"本帧不画"，
     *       绝不猜）；缓存随后会由世界侧的查询或 {@link #seedNearby} 重新填上。</li>
     *   <li>返回的是<b>新集合</b>（键 = {@link BlockPos#asLong()}），与内部缓存不共享可变状态
     *       ⇒ 渲染线程迭代它绝对安全（内部集合永远整体替换，见 {@link #slotsOf}）。</li>
     *   <li>★ 本方法<b>不做任何判断</b>：候选 ≠ 一定是仓位，渲染器仍要对每一格走
     *       {@link #isNacSlot}(...) 复核（同步值可能刚变、缓存可能是上一帧的）。</li>
     * </ul>
     */
    public static Set<Long> snapshot() {
        Set<Long> out = new HashSet<>();
        for (Entry entry : CACHE.values()) {
            out.addAll(entry.slots());
        }
        return out;
    }

    /**
     * ★ <b>2026-09-27 新增：按控制器枚举"已解析的仓位清单"</b>（只读快照，供
     * {@code com.gtlmodular.client.ctm.NacSlotRefreshTracker} 判断"清单内容变了没有"）。
     *
     * <h3>为什么需要它（修「外壳连片刷新时机」的那一环）</h3>
     * {@link #snapshot()} 只给出"所有控制器仓位的<b>并集</b>"，回答不了
     * "哪一台控制器的清单<b>内容</b>变了" —— 而那正是"该把哪些区块段标脏重烤"的唯一依据：
     * 机壳那一格的 47 格掩码是在<b>区块网格化</b>时算进 {@code ModelData} 的
     * （见 {@code ConnectedCtmBakedModel.getModelData}），清单变化不会自动让它重算。
     * 本方法让调用方拿到"每台控制器 + 它的坐标 + 它发布的原文 + 解出的集合"。
     *
     * <h3>安全口径</h3>
     * <ul>
     *   <li><b>纯内存</b>：只读 {@link #CACHE}，不查世界、不加载区块、不建同步包；</li>
     *   <li><b>返回不可变副本</b>：集合本身是 {@code slotsOf} 里<b>整体替换</b>的那个
     *       （永不被原地修改）⇒ 引用安全；外层 {@code List} 与 {@link ControllerSlots}
     *       都是新对象，调用方怎么用都不会污染缓存；</li>
     *   <li><b>有界</b>：条目数被 {@link #MAX_CACHE_ENTRIES} 限死；</li>
     *   <li><b>拿不到 ⇒ 空表</b>（绝不放真、绝不抛出去 —— 与 {@link #snapshot()} 同一条纪律）。</li>
     * </ul>
     */
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
            warnFailure(failure);
            return List.of();
        }
    }

    /**
     * 一台控制器的已解析清单（{@link #publishedCache()} 的元素）。
     *
     * <p>{@code source} = 服务端发布的字符串原文（同步值一变就是一个新实例）；
     * {@code slots} = 由它解出的坐标集合（同上，整体替换、永不被原地改）。
     * {@code dimension + controller} 共同构成"哪台机器"的身份（不同维度可以有同一个 {@code BlockPos}）。
     */
    public record ControllerSlots(ResourceKey<Level> dimension, long controller, BlockPos pos,
                                  String source, Set<Long> slots) {

        /** 身份键（诊断/基线表用；维度 + 控制器坐标）。 */
        public String key() {
            return dimension.location() + "@" + controller;
        }
    }

    /**
     * ★ 2026-09-18 新增：<b>有界播种</b> —— 扫「相机附近若干<b>区块</b>里的方块实体」，
     * 对每个「机器方块」调一次 {@link #isNacSlot}，把命中控制器的仓位清单灌进缓存。
     *
     * <h3>为什么需要它（缓存本来靠什么填）</h3>
     * 缓存的正常来源是世界侧的查询：机壳 CTM（{@code ConnectedCtmBakedModel.isSame} →
     * {@link CtmHullGroup#rendersMeshCasingTile}）在<b>区块网格化</b>时会对机壳邻居问一次
     * "你是不是 NAC 仓位"。但那条路只在"某台机器紧邻机壳"时才会问到；若某台仓四周全是别的仓
     * （塔内密集排布），它就<b>永远不会</b>被问到 ⇒ {@link #snapshot()} 里就没有它 ⇒ 外壳罩少画一格。
     * 本方法补上这个缺口。
     *
     * <h3>代价（为什么是"有界"而不是"扫世界"）</h3>
     * <ul>
     *   <li>遍历的是<b>区块的方块实体表</b>（{@code LevelChunk.getBlockEntities()}），不是方块
     *       —— 每区块通常几十个 BE，半径 4 区块 = 81 个区块 ⇒ 几千次 {@code instanceof} 而已；
     *       真正贵的 {@code isNacSlot}（要查机器实例 + 控制器）只对"机器方块"那一小撮调用。</li>
     *   <li>只取<b>已加载</b>的区块（{@code ChunkSource.getChunk(cx, cz, false)}，第三个参数
     *       {@code false} = 不加载/不生成）⇒ 渲染线程上不可能触发区块加载。</li>
     *   <li>调用方负责节流（见渲染器的"相机换区块 + 每 N 帧"）：本方法自己不做时间判断，
     *       便于自检与单测。</li>
     * </ul>
     *
     * @param level      客户端世界（{@code ClientLevel}；渲染期就是它）
     * @param center     播种中心（一般传相机所在格）
     * @param chunkRadius 区块半径（0 = 只扫中心那一个区块）
     * @return 本次播种<b>新命中</b>的机器格数（0 = 附近没有 NAC 仓位，属正常）
     */
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
                    // ★ 第三个参数 false = 绝不加载/生成区块（渲染线程上只会拿到已加载的那张表）
                    var chunk = level.getChunkSource().getChunk(cx + dx, cz + dz, false);
                    if (chunk == null) {
                        continue;
                    }
                    for (BlockPos pos : chunk.getBlockEntities().keySet()) {
                        if (!(level.getBlockState(pos).getBlock() instanceof IMachineBlock)) {
                            continue; // 只对机器方块做昂贵的 isNacSlot（普通 BE 直接跳过）
                        }
                        if (isNacSlot(level, pos)) {
                            hits++;
                        }
                    }
                }
            }
            return hits;
        } catch (Throwable failure) {
            // ★ 与 isNacSlot 同一条纪律：渲染路径上拿不到数据 ⇒ 返回 0（= 本帧不画），绝不抛出去。
            warnFailure(failure);
            return 0;
        }
    }

    /**
     * 取某台控制器的仓位集合（没有 / 拿不到 ⇒ {@code null}，调用方按 false 处理）。
     *
     * <p>同步值没变（同一份 {@code String} 实例）⇒ 直接复用缓存集合；变了 ⇒ <b>新建</b>
     * 一个 {@code HashSet} 整条替换（旧集合不再被任何路径读到 ⇒ 等价于"清空旧集合"，
     * 且不存在"边改边读"的窗口）。
     */
    private static Set<Long> slotsOf(MetaMachine controller) {
        String published = publishedSlots(controller);
        if (published == null || published.isEmpty()) {
            return null; // 未成形 / 不是本模组的 NAC / 清单为空 ⇒ 调用方返回 false
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
            CACHE.clear(); // 防御性：派生数据，清掉只会让下一帧重算
        }
        return built;
    }

    /**
     * 该控制器发布的仓位清单原文：**只认本模组的 NAC 主机 / 模块**，其余一律 {@code null}。
     *
     * <p>刻意写成显式两分支（而不是"任何 @DescSynced 字段"）：只有这两类机器"拥有" NAC 结构，
     * 别的多方块控制器（GTCEu 自己的、别的 mod 的）与本功能无关 ⇒ 零成本返回 null。
     */
    private static String publishedSlots(MetaMachine controller) {
        if (controller instanceof NanochipAssemblyComplexMachine host) {
            return host.nacSlotsEncoded();
        }
        if (controller instanceof NanochipModuleMachine module) {
            return module.nacSlotsEncoded();
        }
        return null;
    }

    /**
     * 清单原文 → {@code BlockPos.asLong()} 集合。格式 = 逗号分隔的十进制 long（见
     * {@code NanochipAssemblyComplexMachine#encodeNacSlots}）。
     *
     * <p>不认识的片段直接跳过（不抛）：清单是"观感优化的提示数据"，坏一格不该让整格不渲染。
     */
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
                // 坏片段：安静跳过（服务端编码器只产出十进制 long；真坏了也只是"少换一张贴图"）
            }
        }
        return out;
    }

    /** 资源重载 / 换维度时的清理入口（{@code CtmClientSetup} 在每次烘焙开始时调用）。 */
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
