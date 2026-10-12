package com.gtlmodular.common.machine.nanochip;


import com.gtlmodular.common.data.nanochip.NanochipModContext;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * NAC 结构状态日志的<b>降噪闸</b>（★ 2026-09-21 队长拍板「改」）。
 *
 * <h2>队长原话（2026-09-21）</h2>
 * <blockquote>
 *   「唉，现在一进游戏NAC相关的，<b>都要重新加载结构</b>，而且要<b>好久</b>，尤其是<b>主机</b>。」
 * </blockquote>
 *
 * <h2>为什么需要它（队长给的实机日志证据）</h2>
 * <p>{@code logs/latest.log}（3204 行）里进世界后 NAC 结构消息按分钟分布：
 * {@code 19:35=23、19:38=28、19:39=77、19:40=91、19:41=90}，之后<b>每分钟仍有 80~90 条</b>。
 * 绝大多数形如
 * <pre>[gtlmodular] NAC 模块 尚未成形 @BlockPos{x=-74,y=69,z=-105}（纳米芯片组装矩阵）：该格判定不符（通用结构错误）…</pre>
 * 也就是说：<b>结论没变、机器没变、原因也没变，只是"又到点了"就被重复写盘</b>。
 * 写盘本身（尤其主机 + 12 台模块同时刷）就是队长体感里那部分"好久"的负担。
 *
 * <h2>三条纪律（对应队长"状态发生变化时必须照打"）</h2>
 * <ol>
 *   <li><b>同一台机器 + 同一结论 + 同一原因</b>：{@value #UNCHANGED_LOG_INTERVAL_TICKS} tick（10 秒）内只允许一条
 *       INFO；窗口内的重复由调用方降到 {@code debug}（开 debug 能逐条看，INFO 不再写盘）。</li>
 *   <li><b>状态发生变化（成形↔未成形）或原因变了（哪一格错了 / 缺件清单变了）⇒ 立刻照打 INFO</b>，
 *       不受窗口约束 —— 队长要靠日志看状态，这条绝不能省。</li>
 *   <li><b>跨机器折叠</b>：结论没变、只是"又到点了"的那些机器，<b>不逐台打</b>，而是攒进一个
 *       <b>全局 10 秒窗口</b>，到点合并成<b>一条摘要</b>输出
 *       （形如「尚未成形 11 台（例：纳米芯片组装矩阵@(-74,69,-105)；另有 9 台同类结论已折叠）」）。
 *       ⇒ INFO 侧稳态上限 = <b>6 条/分钟</b>，无论场上挂多少台模块。</li>
 * </ol>
 *
 * <h2>为什么按"全局 10 秒窗口"折叠，而不是按"同一 tick"折叠</h2>
 * 12 台模块各按自己的计时器到点（实测日志里它们的时刻是错开的），
 * 按 tick 折叠基本合并不掉（同一 tick 里通常只有 0~1 台到点）。
 * 队长给的硬指标是「每分钟 90 条 → ≤6 条/分」，只有"全局预算 + 到点汇总"能做到；
 * 代价是重复结论不再逐台出现，但这正是"结论没变"的情形 —— 需要逐台时开 debug 即可。
 *
 * <h2>线程安全（队长点名：日志里出现过 {@code GTCEu Multiblock Async Thread-0}）</h2>
 * <ul>
 *   <li>本类的<b>实例</b>记忆（本机上一次打过的结论/原因/时刻）只被本机访问，
 *       且方法 {@code synchronized} —— 即使 {@code onStructureFormed} 落在 GTCEu 异步线程、
 *       {@code onLoad} 落在服务端线程，也不会有撕裂读；</li>
 *   <li>本类的<b>静态</b>折叠表只在 {@code static synchronized} 方法里读写
 *       ⇒ 不会出现 {@code ConcurrentModificationException}，也不会丢更新；</li>
 *   <li>锁序只有「实例 → 类」一个方向（没有反过来取锁的地方）⇒ 不构成死锁。</li>
 * </ul>
 *
 * <h2>为什么"记忆"放在实例上、折叠表放在静态上</h2>
 * 实例记忆随机器实例/重载自然清零 ⇒ <b>每次进世界，每台机器都会先把当前状态报一次</b>
 * （队长要的就是"进门先看见每台机器现在是什么状态"）；折叠表需要跨机器，所以是静态的。
 * 这样也不需要在静态表里按键存机器身份，避免静态表随机器实例无限增长。
 */
public final class NanochipStructureLogGate {

    /**
     * 同一台机器 + 同一结论 + 同一原因的重复窗口（tick）；<b>200 = 10 秒</b>。
     *
     * <p>取值依据（队长建议"建议 10 秒"）：进世界后主机 + 12 台模块的重复结论
     * 从 80~90 条/分压到 ≤6 条/分，且"结论没变"本来就是 10 秒粒度足够的信息。
     */
    public static final long UNCHANGED_LOG_INTERVAL_TICKS = 200L;

    /** 汇总行里每种结论最多列几台样例（再多就只报台数）。 */
    private static final int SAMPLES_PER_CONCLUSION = 2;

    // ---------- 静态：跨机器折叠（同一窗口里多台机器"结论未变"合成一条摘要） ----------

    /** 本窗口内被折叠掉的台次：结论 → 台数。 */
    private static final Map<String, Integer> PENDING_COUNTS = new LinkedHashMap<>();

    /** 本窗口内被折叠掉的样例标签：结论 → 前 {@value #SAMPLES_PER_CONCLUSION} 台。 */
    private static final Map<String, List<String>> PENDING_SAMPLES = new LinkedHashMap<>();

    /**
     * 「全打模式已在生效」这条提示是否已经打过（★ 2026-09-21 排障开关）。
     *
     * <p>为什么要在<b>这里</b>打而不是模组构造里：本类是<b>懒加载</b>的，第一次被调用
     * （第一次真的要打结构结论）时 COMMON 配置<b>一定</b>已经读进来了 ⇒
     * 这一行报出来的就是<b>真实的生效值</b>，不会像构造期读配置那样报出默认值骗人。
     */
    private static boolean verboseNoticeLogged;

    /**
     * 上一次输出汇总的 tick。
     *
     * <p>⚠️ 哨兵<b>不能</b>用 {@code Long.MIN_VALUE}：判据是 {@code now - last < INTERVAL}，
     * 而 {@code gameTime − Long.MIN_VALUE} 会溢出成负 ⇒ 恒小于阈值 ⇒ 汇总永不输出
     * （同一个坑 {@code NanochipAssemblyComplexMachine#lastUnformedAuditTick} 的注释里已登记）。
     */
    private static long lastSummaryTick = -10_000L;

    // ---------- 实例：本机记忆（随机器实例/重载清零，故"进门第一报"一定打得出来） ----------

    /** 本机上一次打过的结论短名（{@code null} = 本机本次会话还没报过）。 */
    private String lastConclusion;

    /** 本机上一次打过时的原因/清单全文（用于判"原因变了没有"）。 */
    private String lastDetail;

    /** 本机上一次真正打过 INFO 的 tick。 */
    private long lastInfoTick = -10_000L;

    /**
     * 本次结构结论<b>要不要照常打 INFO</b>。
     *
     * <p>调用方（NAC 主机 / NAC 模块）拿到的返回值语义：
     * <ul>
     *   <li>{@code true} ⇒ 照常 {@code LOGGER.info(...)}（首次 / 状态变化 / 原因变化）；</li>
     *   <li>{@code false} ⇒ <b>不要打 INFO</b>：
     *       <ul>
     *         <li>窗口内的重复 ⇒ 调用方降级为 {@code LOGGER.debug}（"重复的降到 debug"）；</li>
     *         <li>到点但结论未变 ⇒ 已并入跨机器折叠，稍后由一条摘要代表（调用方同样只降 debug）。</li>
     *       </ul>
     *   </li>
     * </ul>
     *
     * @param conclusion 结论短名（"已成形" / "尚未成形" / "结构失效" …），同时用作汇总分组键
     * @param detail     原因/缺件清单全文；变了就视为"原因变了"⇒ 必打
     * @param label      汇总行里显示本机的短标签（例：{@code 纳米芯片组装矩阵@(-74,69,-105)}）
     * @param nowTick    当前 {@code level.getGameTime()}
     * @return 是否照常打 INFO
     */
    public synchronized boolean shouldEmitInfo(String conclusion, String detail, String label, long nowTick) {
        // ★★ 2026-09-21 排障开关（配置 nanochipComplex.verboseStructureLogs，默认 true）★★
        //
        // 队长原话：「我更新了add，还有core现在我们的模块都不做东西了」＋（对三个选项回）「查」。
        // 为什么临时全打：本类 0.1.40 起把「同一台机器 + 同一结论 + 同一原因」压成 10 秒一条 INFO、
        //   重复降 debug，跨机器还合并成一条汇总 —— 结果是这次故障里
        //   「尚未成形」的逐格清单（期望什么方块 / 实际是什么 / GTCEu 记录在哪一格）
        //   在稳态下**只剩汇总行**，明细要开 debug 才看得到；而队长那台机器的
        //   logs/debug.log 是 09/15 的旧文件（本会话根本没写）⇒ 原因彻底看不见、只能猜。
        // 本分支的纪律（不许改坏）：
        //   · 只改「打不打 / 打多细」：恒返回 true ⇒ 调用方照常走 LOGGER.info，
        //     逐格清单（主机 logUnformedAuditBody / 模块 logUnformedReason）一条不少；
        //   · **不改**下面的实例记忆与折叠表逻辑 —— 关掉开关（verbose=false）后行为逐行等于 0.1.40；
        //   · 恒 true 时不进 fold() ⇒「NAC 结构结论未变汇总」那条合并行自然不再产生
        //     （没有东西被攒下），不会出现「既逐条打又打汇总」的双份。
        // 一行关回：把 config/gtlmodular-common.toml 的
        //   [nanochipComplex] verboseStructureLogs 改成 false（在游戏内配置界面「NAC 校准与排障」里点一下、保存即生效，无需重启；只有手改 config/gtlmodular-common.toml 才要重启）。
        if (NanochipModContext.config().isNanochipVerboseStructureLogs()) {
            if (!verboseNoticeLogged) {
                verboseNoticeLogged = true;
                NanochipModContext.logger().info("[gtlmodular] NAC 结构日志已进入「全打」模式（配置 nanochipComplex.verboseStructureLogs=true）："
                        + "结构结论与逐格原因一律 INFO、不折叠、不降 debug；"
                        + "排障结束后把该项改成 false（在游戏内配置界面「NAC 校准与排障」里点一下、保存即生效，无需重启；只有手改 config/gtlmodular-common.toml 才要重启）即可恢复 0.1.40 的降噪口径。"
                        + "（队长 2026-09-21：「我更新了add，还有core现在我们的模块都不做东西了」＋「查」）");
            }
            this.lastConclusion = conclusion;
            this.lastDetail = detail;
            this.lastInfoTick = nowTick;
            return true;
        }
        boolean changed = this.lastConclusion == null
                || !this.lastConclusion.equals(conclusion)
                || !Objects.equals(this.lastDetail, detail);
        this.lastConclusion = conclusion;
        this.lastDetail = detail;
        if (!changed && nowTick - this.lastInfoTick < UNCHANGED_LOG_INTERVAL_TICKS) {
            return false;   // Y 秒内的重复 ⇒ 调用方降到 debug
        }
        this.lastInfoTick = nowTick;
        if (changed) {
            // ★ 队长硬要求：状态变化（成形↔未成形）或原因变了 ⇒ 必须照打，不受窗口约束。
            //   顺手把已经攒到点的折叠摘要冲掉（免得摘要一直等下一次报告）。
            emitSummaryIfDue(nowTick);
            return true;
        }
        // 结论与原因都没变，只是"又到点了" ⇒ 不逐台打，交给跨机器汇总
        fold(conclusion, label, nowTick);
        return false;
    }

    /**
     * 清空本机记忆（{@code onLoad} 调用）。
     *
     * <p>作用：进世界 / 区块重载后本机第一次报状态时一定判为"变了" ⇒ 必打一条 INFO。
     * 这条对队长很重要 —— 他就是靠这几条确认"进门时每台机器现在是什么状态"。
     */
    public synchronized void reset() {
        this.lastConclusion = null;
        this.lastDetail = null;
        this.lastInfoTick = -10_000L;
    }

    // ---------- 静态折叠实现 ----------

    /** 记一台"结论未变、只是到点了"的机器，然后在窗口到点时合并输出。 */
    private static synchronized void fold(String conclusion, String label, long nowTick) {
        PENDING_COUNTS.merge(conclusion, 1, Integer::sum);
        List<String> samples = PENDING_SAMPLES.computeIfAbsent(conclusion, k -> new ArrayList<>());
        if (samples.size() < SAMPLES_PER_CONCLUSION) {
            samples.add(label);
        }
        emitSummaryIfDue(nowTick);
    }

    /**
     * 到点就把攒下的折叠合成<b>一条</b>摘要打出去（未到点或没攒下东西就什么都不做）。
     *
     * <p>窗口 = {@value #UNCHANGED_LOG_INTERVAL_TICKS} tick ⇒ INFO 侧稳态上限 6 条/分钟。
     */
    private static synchronized void emitSummaryIfDue(long nowTick) {
        if (PENDING_COUNTS.isEmpty()) {
            return;
        }
        if (nowTick - lastSummaryTick < UNCHANGED_LOG_INTERVAL_TICKS) {
            return;
        }
        lastSummaryTick = nowTick;
        StringBuilder sb = new StringBuilder();
        int total = 0;
        for (Map.Entry<String, Integer> entry : PENDING_COUNTS.entrySet()) {
            int count = entry.getValue();
            total += count;
            if (sb.length() > 0) {
                sb.append("；");
            }
            sb.append(entry.getKey()).append(' ').append(count).append(" 台");
            List<String> samples = PENDING_SAMPLES.get(entry.getKey());
            if (samples != null && !samples.isEmpty()) {
                sb.append("（例：").append(String.join("、", samples));
                if (count > samples.size()) {
                    sb.append("；另有 ").append(count - samples.size()).append(" 台同类结论已折叠");
                }
                sb.append('）');
            }
        }
        NanochipModContext.logger().info("[gtlmodular] NAC 结构结论未变汇总（最近 {} 秒，{} 台次重复结论已折叠成这一条）：{}",
                UNCHANGED_LOG_INTERVAL_TICKS / 20L, total, sb);
        PENDING_COUNTS.clear();
        PENDING_SAMPLES.clear();
    }

    /** 供一次性自检/调试用：当前窗口里攒了多少台次（不改变任何状态）。 */
    public static synchronized int pendingFoldedCount() {
        int total = 0;
        for (int n : PENDING_COUNTS.values()) {
            total += n;
        }
        return total;
    }
}
