package com.gtlmodular.client.ctm;

import com.gtlmodular.GTLModular;
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
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * <b>「NAC 仓位清单变了 ⇒ 把相关区块段重新网格化」的客户端补偿器</b>
 * —— 修「机器外壳连片（CTM）刷新时机」这个 bug。
 *
 * <h2>为什么必须要有它（本 bug 的根因，取证见下）</h2>
 * 「外壳连片」有两半，两半取的都是<b>同一份判据</b>（{@link CtmHullGroup#rendersMeshCasingTile}
 * → {@link NacSlotIndex#isNacSlot}），但<b>刷新时机完全不同</b>：
 * <ol>
 *   <li><b>机器自己那 5 个面</b>：由 {@code com.gtlmodular.client.renderer.NacMachineShellRenderer}
 *       在 {@code RenderLevelStageEvent.AFTER_SOLID_BLOCKS} 里<b>每帧现算现画</b>
 *       ⇒ 数据一到就自愈（这条<b>不需要</b>本类）。</li>
 *   <li><b>接口机壳那一格</b>（玩家一眼看到的那条缝）：走的是
 *       {@link ConnectedCtmBakedModel} 的 {@code getModelData(level,pos,state,data)} ——
 *       它在<b>区块网格化</b>时把 6 个面的 8 位连接掩码算成 {@code ModelData} 里的 {@code CTM_MASK}，
 *       再在 {@code getQuads} 里按掩码选 47 格变体。
 *       ⇒ <b>掩码一旦烤进区块网格，就不会自己变</b>；只有该区块段被重新网格化才会重算。</li>
 * </ol>
 * 而「仓位清单」（服务端 {@code NanochipAssemblyComplexMachine/NanochipModuleMachine.syncedNacSlots}
 * → 客户端 {@link NacSlotIndex}）<b>到达客户端时，没有任何东西把相关区块段标脏</b>：
 * <ul>
 *   <li>LDLib 的 {@code @RequireRerender} 在本整合包的三个 jar（ldlib / gtceu / gtlcore）里
 *       <b>除声明外零调用者</b>（取证见 {@code LazyPatternAssemblyPartMachine#onSyncChanged} 的注释）
 *       ⇒ 「同步到位后没人重烘焙」；</li>
 *   <li>{@code NacSlotIndex} 只是<b>内存索引</b>，它自身不碰任何渲染态；</li>
 *   <li>而 {@code syncedNacSlots} 变化<b>不会</b>触发方块状态更新（它不是方块属性、机器方块的状态也没变）
 *       ⇒ 没有任何邻居更新/区块脏标记被顺带发出。</li>
 * </ul>
 * ⇒ 复现路径与队长现场<b>逐条吻合</b>：
 * <pre>
 *   ① 先有主机/机壳、后把机器放进结构（或清过一次缓存再重进世界）
 *      ⇒ 机壳那一格<b>已经按"邻居还不是仓位"烤好了</b>，清单随后才到 ⇒ 缝一直在；
 *   ② 「破坏结构 → 让它重新成型」之所以能连上，是因为<b>拆掉/补上结构方块</b>本身会发邻居更新、
 *      把那一带的区块段标脏重烤 ⇒ 掩码这才被重算（= 一次"大事件"刷新，不是判据变了）。
 * </pre>
 *
 * <h2>本类做什么（只做一件事：把"清单变了"翻译成"区块段脏了"）</h2>
 * <ol>
 *   <li><b>基线</b>：每 tick 读一次 {@link NacSlotIndex#publishedCache()}（纯内存、<b>不查世界</b>，
 *       缓存里只有"已经被问到过的控制器"，条目数被 {@code NacSlotIndex.MAX_CACHE_ENTRIES} 限死）；</li>
 *   <li><b>逐控制器比内容</b>：{@link NacSlotIndex} 的缓存条目在同步值变化时是<b>整体替换</b>
 *       （不可变 {@code String} 源 + 新建 {@code Set}）⇒ 这里按 {@code Key} 比"源串 + 集合内容"，
 *       <b>没变就一个字节都不动</b>（稳态开销 = 一次 map 遍历 + N 次集合比较）；</li>
 *   <li><b>变了就标脏</b>：对<b>新增</b>与<b>消失</b>的那几格所在区块段调
 *       {@code ClientLevel.setSectionDirty(cx, cy, cz)} ⇒ 该段下一帧重新网格化 ⇒
 *       机壳那一格重算 8 位掩码 ⇒ 缝当场补上；</li>
 *   <li><b>有界重标</b>：同步/网格化有先后（清单可能比"区块已经烤过"晚到几十毫秒），
 *       所以每个被标脏的区块段在 {@link #WINDOW_TICKS} tick 内被再标脏几次，到点自动过期。</li>
 * </ol>
 *
 * <h2>性能红线（逐条对齐任务要求）</h2>
 * <ul>
 *   <li><b>不是每帧全量重算</b>：跑在 {@code ClientTickEvent}（20 Hz，不是 100+ FPS 的渲染阶段），
 *       且<b>只在清单真的变了时</b>才写任何渲染状态；</li>
 *   <li><b>不是全量扫描世界</b>：数据源是"已解析的控制器清单"（内存），
 *       <b>不</b>遍历方块、<b>不</b>加载区块；</li>
 *   <li><b>每 tick 有硬上限</b>：{@link #MAX_SECTIONS_PER_TICK} 个区块段 / 每控制器
 *       {@link #MAX_DIRTY_PER_CONTROLLER} 格 ⇒ 单 tick 工作量恒定，<b>与结构大小无关</b>；</li>
 *   <li><b>无界缓存不存在</b>：基线与窗口表都按当前缓存键集<b>剪枝</b>，窗口条目硬上限
 *       {@link #MAX_WINDOW_ENTRIES}；地图切换（{@code level} 引用变了）整体清零；</li>
 *   <li><b>热路径不外抛</b>：整条链包在 {@code try/catch(Throwable)}，
 *       失败只打限流 WARN 并放弃本轮（外观 = 现状，绝不把异常甩给 tick 循环）。</li>
 * </ul>
 *
 * <h2>与"服务端也要动"的分工</h2>
 * 本类<b>只解决"数据到了但没重烤"</b>。另一半「结构没散、只是换了几个 part ⇒ 服务端根本没重新发布」
 * 由服务端那条有界心跳兜底（{@code NanochipAssemblyComplexMachine#tickNacSlotHeartbeat} /
 * {@code NanochipModuleMachine#tickNacSlotHeartbeat}）：它<b>没有</b>在稳态下重跑结构匹配，
 * 只做「{@code getParts().size()} 变了才重建字符串」的 O(1) 比较。
 *
 * <h2>关闭方式（可回退）</h2>
 * 把 {@link #ENABLED} 改成 {@code false} ⇒ 本类每 tick 第一句就返回，行为与"没有本类"逐字等价
 * （也可以直接删掉本文件 + {@code NacSlotIndex.publishedCache()}，无其它引用）。
 */
@Mod.EventBusSubscriber(modid = GTLModular.MOD_ID, value = Dist.CLIENT)
public final class NacSlotRefreshTracker {

    // ------------------------------------------------------------------
    // 开关与常量
    // ------------------------------------------------------------------

    /**
     * 本补偿器的总开关（默认开）。
     *
     * <p>{@code false} = <b>与没有这个类完全等价</b>：{@code ClientTickEvent} 第一句就返回，
     * 不读索引、不标脏、不打日志（这也是"出问题就地回退、不用回滚版本"的那一手）。
     */
    public static final boolean ENABLED = true;

    /** 被标脏的区块段在这么多 tick 内会被"再标脏"几次（覆盖"清单比区块烘焙晚到"的窗口）。 */
    public static final int WINDOW_TICKS = 10;

    /** 每 tick 最多标脏几个区块段（硬上限；正常一次结构变化只涉及几段）。 */
    public static final int MAX_SECTIONS_PER_TICK = 64;

    /** 单个控制器一次变化最多标脏多少格（防"整座塔一次性换完"造成一帧几百段；超出的走窗口兜底）。 */
    public static final int MAX_DIRTY_PER_CONTROLLER = 256;

    /** 基线最多记多少个控制器（与 {@code NacSlotIndex.MAX_CACHE_ENTRIES} 同量级；超了就整体重来）。 */
    public static final int MAX_BASELINE_ENTRIES = 4096;

    /** 窗口表最多记多少个区块段（无界缓存红线）。 */
    public static final int MAX_WINDOW_ENTRIES = 512;

    /** 诊断日志总条数上限（渲染/tick 路径绝不刷屏；到点后静默）。 */
    public static final int MAX_DIAG_LINES = 200;

    // ------------------------------------------------------------------
    // 状态（只在客户端主线程读写）
    // ------------------------------------------------------------------

    /** {@code 控制器键 → 上一次看到的清单}（键 = {@code NacSlotIndex.ControllerSlots#key()}）。 */
    private static final Map<String, Baseline> BASELINE = new HashMap<>();

    /** {@code 区块段 → 到期 tick}：窗口内反复标脏（见 {@link #WINDOW_TICKS}）。 */
    private static final Map<Long, Integer> WINDOW = new HashMap<>();

    /** 上一次 tick 的客户端世界（引用比较 ⇒ 换地图/重进世界即整体清空）。 */
    private static ClientLevel lastLevel;

    /** 诊断限流。 */
    private static final AtomicInteger DIAG_LINES = new AtomicInteger(MAX_DIAG_LINES);
    private static long lastDiagTick = Long.MIN_VALUE;

    private NacSlotRefreshTracker() {
    }

    /**
     * 本补偿器的状态清理入口（幂等；换世界时本类自己也会清，这里供资源重载等外部时机显式调用）。
     *
     * <p>为什么需要：{@link NacSlotIndex#reset()} 会在<b>每次资源重载</b>时清空仓位缓存
     * （{@code CtmClientSetup.onModifyBakingResult} → {@code NacSlotIndex.reset()}）
     * ⇒ 那份基线也一起作废，否则重载后"内容完全相同的新对象"会被判成变化而全量重烤一遍。
     * 这个方法只是把清理动作摆到明面上（当前实现不依赖它：{@code level} 引用变化与内容比较
     * 已经覆盖了大部分情形）。
     */
    public static void reset() {
        BASELINE.clear();
        WINDOW.clear();
    }

    // ------------------------------------------------------------------
    // 事件入口
    // ------------------------------------------------------------------

    /**
     * ★ <b>唯一入口</b>：客户端 tick（20 Hz）。
     *
     * <p>为什么不用渲染事件：渲染阶段（{@code RenderLevelStageEvent}）里做"标脏"会让
     * "改渲染状态"与"正在提交几何"挤在同一帧的同一处；而且那是 100+ FPS 的频率，
     * 与本类"只在清单变化时才动"的语义不符。tick 是 20 Hz、且在安全点。
     */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (!ENABLED || event.phase != TickEvent.Phase.END) {
            return;
        }
        try {
            ClientLevel level = Minecraft.getInstance().level;
            if (level != lastLevel) {
                // 换维度 / 重进世界 / 换服务器：上一份基线与窗口全部作废（旧坐标在新世界里没有意义）
                lastLevel = level;
                BASELINE.clear();
                WINDOW.clear();
            }
            if (level == null) {
                return;
            }
            long now = level.getGameTime();
            List<NacSlotIndex.ControllerSlots> published = NacSlotIndex.publishedCache();
            // ① 比基线：只处理"真的变了"的控制器，并把新增/消失的格标脏
            int dirtied = trackChanges(level, published);
            // ② 窗口兜底：刚标过的段在 WINDOW_TICKS 内再标几次
            dirtied += reDirtyWindow(level, now);
            if (dirtied > 0) {
                report(published, dirtied, now);
            }
        } catch (Throwable failure) {
            warnFailure(failure);
        }
    }

    // ------------------------------------------------------------------
    // ① 基线比对 + 标脏
    // ------------------------------------------------------------------

    /**
     * 逐控制器比"清单内容"，对**新增**与**消失**的格所在区块段标脏。
     *
     * <p>为什么按内容比而不是按源串身份：{@link NacSlotIndex} 的缓存条目在源串变化时整条替换，
     * 但"同一条源串"在<b>缓存被整体清空后</b>（资源重载）会被重建 —— 那时内容可能完全相同，
     * 却是一次新的对象。按内容比 ⇒ 资源重载不会引发无意义的全量重烤。
     *
     * @return 本 tick 实际标脏的区块段数
     */
    private static int trackChanges(ClientLevel level, List<NacSlotIndex.ControllerSlots> published) {
        if (BASELINE.size() > MAX_BASELINE_ENTRIES) {
            BASELINE.clear(); // 防御性：基线与索引同生命周期，索引清空时这里也会被剪掉
        }
        Set<String> alive = new HashSet<>();
        int dirtied = 0;
        for (NacSlotIndex.ControllerSlots controller : published) {
            String key = controller.key();
            alive.add(key);
            Baseline previous = BASELINE.get(key);
            if (previous != null && previous.sameAs(controller)) {
                continue; // ★ 稳态快路径：内容没变 ⇒ 一格都不标（绝大多数 tick 走这里）
            }
            if (previous == null) {
                // 本会话**第一次**看到这台控制器（缓存刚被填上）。
                // 为什么第一次不标脏：缓存往往在"进世界后的第一帧"就被填满（机壳网格化时挨个问过来），
                // 那时把每台 NAC 的仓位全标一遍纯属浪费；而"后来才连上的那台机器"必然在**下一个 tick**
                // 以"内容变了"的形态再出现（见下面 tombstone 的口径）⇒ 不会漏。
                // 仍然登记一条**空基线**（tombstone）：这样它下一次出现时走的是"差集 = 整份清单"，
                // 而不是被误当成"又是第一次"再跳过一次。
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
                break; // 硬上限：剩下的下个 tick 继续（基线已更新，差集会在下一次比对里补齐）
            }
        }
        // 剪枝：已经不存在的控制器不再占基线（防跨维度/反复读档累积）。
        // ★ 注意：剪掉之后它若"重新出现"，会再走一次上面的"第一次"分支（空基线）
        //    ⇒ 下一次比对时整份清单都会被算成"新增"并标脏 ⇒ "拆了再装"这条路径不会漏。
        if (BASELINE.size() > alive.size()) {
            BASELINE.keySet().retainAll(alive);
        }
        return dirtied;
    }

    /**
     * 把一批仓位坐标所在的区块段标脏，并把它们放进 {@link #WINDOW} 以便有界重标。
     *
     * @param positions 要标脏的仓位坐标（{@link BlockPos#asLong()}）
     * @param fallback  这批坐标全为空时用的兜底坐标（一般是控制器自己那格；{@code null} = 不用）
     * @return 实际标脏的区块段数
     */
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

    /** 标脏一格所在区块段。 */
    private static void markSection(ClientLevel level, BlockPos pos) {
        int cx = SectionPos.blockToSectionCoord(pos.getX());
        int cy = SectionPos.blockToSectionCoord(pos.getY());
        int cz = SectionPos.blockToSectionCoord(pos.getZ());
        net.minecraft.client.Minecraft.getInstance().levelRenderer.setSectionDirty(cx, cy, cz);
        WINDOW.put(SectionPos.asLong(cx, cy, cz), (int) Math.min(level.getGameTime() + WINDOW_TICKS, Integer.MAX_VALUE));
        // 有界：超上限就挤掉最早放进去的那一段（窗口只是"多标几次"，丢一段最坏是多等一次结构变化）
        while (WINDOW.size() > MAX_WINDOW_ENTRIES) {
            Iterator<Long> oldest = WINDOW.keySet().iterator();
            if (!oldest.hasNext()) {
                break;
            }
            WINDOW.remove(oldest.next());
        }
    }

    // ------------------------------------------------------------------
    // ② 窗口内重标（对付"清单比区块烘焙晚到"）
    // ------------------------------------------------------------------

    /**
     * 把还在窗口里的区块段再标一次脏（每个段最多标 {@link #WINDOW_TICKS} 次），到点移除。
     *
     * <p>为什么需要：客户端收到同步、到网格线程真的把那一段重烤之间有几十毫秒到几百毫秒的间隔；
     * 而"晚到的同步"还可能再改一次清单（例如主机先发布自己的 part、模块随后补上）。
     * 窗口只有 {@link #WINDOW_TICKS} tick（0.5 秒）、条数有硬上限 ⇒ 不会变成"每 tick 全量重烤"。
     */
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

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    private static Set<Long> copy(Set<Long> slots) {
        return slots == null || slots.isEmpty() ? new HashSet<>() : new HashSet<>(slots);
    }

    /** 上一次看到的清单（内容比较用；{@code source} 保留以区分"同内容不同来源"的诊断）。 */
    private record Baseline(String source, Set<Long> slots) {
        boolean sameAs(NacSlotIndex.ControllerSlots controller) {
            return source != null && source.equals(controller.source()) && slots.equals(controller.slots());
        }
    }

    /** 限流的诊断行：验收抓手（见任务 ④），数字没变就不打。 */
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
        GTLModular.LOGGER.info("[gtlmodular] NAC 外壳连片重算：原因=仓位清单变化 ｜ 涉及 {} 个控制器 / {} 格 ｜ "
                        + "本次重烤区块段 {} 个（窗口 {} tick 内最多再标 {} 次）｜ 控制器={}{}",
                published.size(), total, sections, WINDOW_TICKS, WINDOW_TICKS,
                summary, published.size() > summary.size() ? " …" : "");
    }

    private static void warnFailure(Throwable failure) {
        if (DIAG_LINES.get() > 0) {
            DIAG_LINES.decrementAndGet();
            GTLModular.LOGGER.warn("[gtlmodular] NAC 外壳连片重算：本 tick 抛异常 —— 已吞掉（本轮不标脏，"
                    + "外观 = 现状）。这是刷新补偿器的 bug，请把下面这条堆栈回传。", failure);
        }
    }

    // ------------------------------------------------------------------
    // 诊断（自检 / 日志用；纯读）
    // ------------------------------------------------------------------

    /** 当前基线里的控制器数（诊断用）。 */
    public static int baselineSize() {
        return BASELINE.size();
    }

    /** 当前窗口里还等着重标的区块段数（诊断用）。 */
    public static int windowSize() {
        return WINDOW.size();
    }

    /** 还剩几条诊断额度（诊断用）。 */
    public static int diagBudgetLeft() {
        return DIAG_LINES.get();
    }
}
