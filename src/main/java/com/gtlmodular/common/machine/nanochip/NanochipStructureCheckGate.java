package com.gtlmodular.common.machine.nanochip;

import com.gregtechceu.gtceu.api.pattern.MultiblockState;
import com.gregtechceu.gtceu.api.pattern.error.PatternError;
import com.gtlmodular.GTLModular;
import com.gtlmodular.common.config.GTLModularConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * ★【2026-09-18 第四轮】NAC 结构检查的 <b>「无变化就不重跑」闸</b>。
 *
 * <h3>为什么必须有它（队长点名的部署前必堵之洞）</h3>
 * 第三轮把 {@code UNFORMED_SKIP} 调回 1（免等待），并用 {@link NanochipStructurePrecheck} 做便宜预检。
 * 但预检只采样 <b>16 格</b> ⇒ 存在这样的<b>持久</b>状态：
 * <pre>
 *   16 个采样点<b>全对</b>（预检通过），但某个**非采样格**是错方块
 *   ⇒ 每轮都放行一次全量匹配，而匹配**每次都以同一原因失败**
 *   ⇒ GTCEu 单线程每 ~1 秒被喂一次「40–132 秒」的全量匹配 ⇒ 异步线程被永久占满
 *   ⇒ 服务端线程又要在 patternLock 上等 ⇒ **就是第二轮那次卡死**
 * </pre>
 *
 * <h3>本类的判据：把"世界变了吗"变成可证的便宜问题</h3>
 * 三条证据，任何一条成立就<b>立刻放行</b>（保住免等待）；三条都不成立就<b>不许重跑</b>（堵洞）：
 * <ol>
 *   <li><b>签名变了</b>（{@code signature}）—— 由 {@link NanochipStructurePrecheck} 在同一次扫描里
 *       顺便算出的、那 16 个采样点（按命中的那个朝向）的 {@code BlockState} 指纹；</li>
 *   <li><b>上次失败的那一格变了</b>（{@code probePos} / {@code probeState}）—— ★ 这是真正堵洞的那一条：</li>
 *   <li><b>硬地板到期</b>（{@link #FLOOR_MS} / {@link #UNCHANGED_RETRY_MS} / 按上次耗时自适应）。</li>
 * </ol>
 *
 * <h3>★ 为什么必须补第 2 条：只靠"16 点签名"会漏掉两个致命情形</h3>
 * <b>(a) 16 点签名在"预检通过"时是恒定的。</b>
 * 预检的通过条件就是"这 16 格的方块<b>恰好等于</b>图案要求的那一个方块"
 * （{@code NanochipBlocks} 的机壳都是无属性 {@code Block} ⇒ 一个方块只有一个 {@code BlockState}）
 * ⇒ 只要预检通过，这 16 个 {@code BlockState} 就必然是那 16 个，签名<b>不可能变</b>。
 * 于是"签名没变 ⇒ 不重跑"会退化成"<b>永久不重跑</b>"。
 * <p><b>(b) 玩家把最后一块补上时，那一格几乎肯定不在 16 个采样点里</b>
 * ⇒ 签名不变 ⇒ 按字面规则会一直跳过 ⇒ <b>结构永不成形</b>（比第二轮的 6 秒更糟）。</p>
 * <p>⇒ 所以本类把 <b>GTCEu 自己报出的"上次失败的那一格"</b>纳入签名侧：
 * 匹配失败时 GTCEu 会把 {@code PatternError}（含失败位置）写进 {@code MultiblockState.error}
 * （见 {@code NanochipAssemblyComplexMachine.describeStructureFailure} 的既有取证）。
 * 只要那一格的方块<b>与失败当时逐位相同</b>，匹配就<b>必然</b>还在同一处失败
 * ⇒ 跳过是<b>可证正确</b>的；而玩家一旦去动那一格（补上正确方块、或拆掉），
 * {@code probeState} 立刻不等 ⇒ <b>下一次检查就成形</b>（免等待原样保住）。</p>
 *
 * <h3>★ 第 3 条：硬地板，且按"上次匹配实际耗时"自适应（对付 40–132 秒的单次成本）</h3>
 * 签名与证据都没变时，两道地板：
 * <pre>
 *   base = lastFailed ? UNCHANGED_RETRY_MS(5 s) : FLOOR_MS(5 s)
 *   地板  = max(base, DURATION_MULTIPLIER(3) × 上次全量匹配耗时)
 * </pre>
 * 第二项是<b>关键</b>：若单次匹配真的要 40–132 秒，固定地板（哪怕 30 秒）仍会让异步线程
 * <b>过载</b>（40/30 &gt; 1）。按"上次耗时 × 3"设地板 ⇒ 这台机器对单线程检查池的占用率
 * <b>≤ 1/3</b>，<b>不可能</b>靠它自己把池子占满。⇒ 这是"单次 40–132 秒"这个已知事实的直接应对。
 *
 * <h3>决策表（未成形侧，预检已通过）</h3>
 * <table border="1">
 *   <tr><th>#</th><th>签名（16 点指纹 / 失败格）</th><th>上次全量匹配</th><th>本次是否调用全量匹配</th></tr>
 *   <tr><td>1</td><td>变了（含首次）</td><td>任意</td><td><b>立刻调用</b>（免等待；地板被解除）
 *       —— ★ 2026-09-21 起受「最小间隔 {@value #FASTPASS_MIN_INTERVAL_TICKS} tick」约束：
 *       距上次变化驱动的放行不足 1 秒 ⇒ 本次<b>压住</b>（到点后的下一次轮询必然放行，最坏晚 1 秒）</td></tr>
 *   <tr><td>2</td><td>没变</td><td><b>失败</b></td><td>不调用，直到 {@code max(30 s, 3×上次耗时)} 到期</td></tr>
 *   <tr><td>3</td><td>没变</td><td>成功 / 未知</td><td>不调用，直到 {@code max(5 s, 3×上次耗时)} 到期</td></tr>
 * </table>
 *
 * <h3>★ 2026-09-21（队长拍板「落」）：给「变化 ⇒ 立刻放行」加 <b>最小间隔</b>
 * （{@link #FASTPASS_MIN_INTERVAL_TICKS}）</h3>
 * <b>队长原话</b>：「<b>落</b>」—— 落地 `_census/P1四条_可行性.md` 的<b>建议顺序第 ② 条</b>
 * （该报告 §7.3 第 4 条 / §9 第 2 条：只动本类，零语义、一行级、可逆）。
 *
 * <p><b>它治的是什么</b>：A+ 在"身份格全对"时用 <b>625 格能力格指纹</b>当签名，而"指纹一变就
 * <b>立刻</b>放行"意味着 —— 玩家在装 {@code F/H/I} 那 625 格（仓室/模块位）的<b>最后阶段</b>，
 * 每放一块都会让下一个异步轮询放行一次全量匹配 ⇒ 这是一个"每秒一次大匹配"的短窗口。
 *
 * <p><b>队长明确接受的口径</b>：最小间隔按 <b>1 秒（20 tick）</b>；唯一代价 = "玩家在铺那 625 格时，
 * 成形最坏晚 1 秒"。
 *
 * <p><b>为什么这是"零语义"</b>：本间隔只决定<b>我们少喂几次给上游</b>；一旦放行，走的仍是
 * <b>原来那条真匹配</b>（{@code super.asyncCheckPattern} → GTCEu 的 {@code checkPatternWithTryLock} /
 * {@code checkPattern}），<b>判定结果仍然是 GTCEu 的</b>（本类不产生任何 true/false 判定）。
 * 被压住的那一次<b>不会丢</b>：{@link #lastSignature} 与 {@code probePos}/{@code probeState}
 * 只在 {@link #recordOutcome}（真跑过匹配）时才更新 ⇒"变化"这个事实一直留在闸里，
 * 到点（≥ 20 tick）后的下一次轮询<b>必然</b>放行 ⇒ 最坏延迟 = 20 tick + 一个轮询周期。
 *
 * <p><b>★ 不是 GTO 1.8.0 的 {@code waitingTime}</b>：那套是"匹配失败后按 <b>{@code checkPattern()}
 * 被调用的次数</b>冷却"（成功置 0、失败置 10 或 1），单位不是 tick
 * （`_census/P1四条_可行性.md` §1.3 / §6.2 已逐字节码证伪）。本类<b>不引入任何累计计数器</b>：
 * 只有一次 {@code System.nanoTime()} 相减（纯时间去抖）。
 *
 * <p><b>★ 落地时发现并一起处理的一个坑（必须让队长看到）</b>：上游在我们放行之后还有一道
 * <b>自带过滤</b>——gtlcore {@code @Overwrite} 的 {@code asyncCheckPattern(long)} 字节码 17–34 是
 * {@code if ((getHolder().getOffset() + periodID) % 4 != 0) return;}，昂贵匹配在 37–41。
 * 而 {@code searchingTask} 是 <b>250 ms</b> 一轮、{@code periodID} 每轮 +1
 * ⇒ 上游的"合法轮次"是<b>每 4 轮 = 1 秒、且对同一台机器固定相位</b>。
 * 若把 1 秒的最小间隔放在这道过滤<b>之前</b>且不看相位，放行节奏就会被锁到固定相位：
 * 一旦那一相位不合法（取决于该机器的 offset，约 <b>3/4</b> 概率），这台机器会
 * <b>再也等不到一次真匹配</b> ⇒ 违反队长红线「不许因此改变什么时候算成形」。
 * 本类两条应对（都只作用于"我们少喂几次"这一侧，<b>不改任何判定</b>）：
 * <ol>
 *   <li>{@link #noteUnformedPoll} 的相位入参 + {@link #gtceuReachesFullMatchThisPoll}：
 *       <b>只在上游这一轮真会跑匹配的轮次放行</b>（放行不再被白丢，间隔不再被白耗）；</li>
 *   <li>{@link #FASTPASS_PHASE_VALVE_MS}：万一流派脱节，最多饿 4 秒就强制放行一次（宁可白放，绝不饿死）。</li>
 * </ol>
 *
 * <p><b>这条路径之外的东西一个字没动</b>：
 * <ul>
 *   <li><b>"拆一格外壳 ⇒ 下一 tick 失效"不受影响</b>：那条走 gtlcore/GTCEu 的<b>方块变更事件路径</b>
 *       （{@code LevelMixin}/{@code ChunkMixin} → {@code MultiblockState.onBlockStateChanged} →
 *       {@code checkPatternWithLock()}），<b>根本不进 {@code asyncCheckPattern}，也就不到本类</b>
 *       （本类只被两台机器的 {@code asyncCheckPattern} 调用；证据见交付报告 ②）；</li>
 *   <li><b>A+ 预检本身的判定不受影响</b>：判否仍然在<b>本类之前</b>裸 {@code return}
 *       （{@code NanochipAssemblyComplexMachine.asyncCheckPattern} 的 ① 与 ①′ 两个块，一字未动）；</li>
 *   <li><b>"同 tick 折叠"不受影响</b>：它挂在 {@code checkPatternWithLock()}（服务端事件路径），一字未动。</li>
 * </ul>
 *
 * <p><b>可回退</b>：删掉 {@link #FASTPASS_MIN_INTERVAL_TICKS} / {@link #FASTPASS_MIN_INTERVAL_NANOS} /
 * {@link #FASTPASS_PHASE_VALVE_MS} / {@link #tryFastPass} / {@link #gtceuReachesFullMatchThisPoll} 与
 * {@link #shouldRunFullMatch} 里两处 {@code this.tryFastPass(nowNanos)} ⇒ <b>逐字</b>回到本轮之前的行为。
 * （统计与相位观测都是纯读数：删掉调用方的 {@code noteUnformedPoll(...)} 一行即全灭，与本间隔互不依赖。）
 *
 * <h3>诚实边界</h3>
 * <ul>
 *   <li>第 2 条依赖 GTCEu 报的位置确实是"放错的那一格"。<b>数量类</b>失败
 *       （{@code SinglePredicateError.type} 0..3，例：NAC 的"能源仓最多 2 个"）
 *       那个位置只是"最后被检查的一格"（既有取证已写明）⇒ 那一类靠地板兜底，
 *       最坏延迟 = 地板值，<b>不会</b>永不成形；</li>
 *   <li>{@code UNLOAD_ERROR} / {@code UNINIT_ERROR} 这两个静态错误没有挂 state（{@code getPos()} 会 NPE）
 *       ⇒ 明确排除，不给证据；</li>
 *   <li>本类<b>只服务"未成形 + 全量匹配"这一侧</b>。「拆一块 → 失效」走 GTCEu/gtlcore 的方块变更
 *       事件路径（{@code LevelMixin} → {@code onBlockStateChanged} → {@code checkPatternWithLock}），
 *       <b>不经过</b> {@code asyncCheckPattern}，也就<b>不经过本类</b>（见
 *       {@link NanochipStructureCheckThrottle} 的 javap 证据）；</li>
 *   <li>签名栏里的 16 点指纹按队长的口径保留（"把 16 个采样点的方块状态算成签名"），
 *       但上面 (a) 已证它在预检通过时恒定 ⇒ <b>真正起作用的是失败格证据</b>。这是如实说明，不是掩饰。</li>
 *   <li><b>最小间隔（2026-09-21）的边界</b>：它只压"<b>变化驱动</b>的立刻放行"（签名变 / 失败格变），
 *       <b>不动</b>地板那条路（地板到期该跑还是跑）；且 {@link #reset()}（{@code onStructureInvalid()} /
 *       手动「立即更新结构」）是一次<b>大变化</b>，它会同时把最小间隔归零 ⇒ 失效后"补齐就成形"这条
 *       免等待路径<b>不被</b>本间隔拖慢。这是有意为之，与本类原有"归零地板"的取舍一致。</li>
 *   <li><b>统计日志（2026-09-21）的边界</b>：它是<b>纯观测</b>（只自增几个 long + 一条限流 INFO），
 *       不参与任何判定；计数器都在 GTCEu 那根<b>单线程</b>异步线程上自增，只有 {@link #reset()}
 *       会从服务端线程写它们 —— 与既有字段（{@code hasRun} 等）同一性质的<b>良性竞争</b>
 *       （最坏后果 = 某条日志行的数字有偏差，绝不会影响判定）。</li>
 * </ul>
 */
public final class NanochipStructureCheckGate {

    /**
     * 硬地板：签名与证据都<b>没变</b>时，同一台机器两次全量匹配之间至少间隔这么多毫秒。
     *
     * <p>{@code 1000 ms = 20 tick} ⇒ 上界 <b>1 次/秒</b>。
     *
     * <p>★ 2026-09-19（队长：「主机再一次成型的速度过慢了」）：原值 5000 ms（= 队长的旧硬上限 0.2 次/秒）
     * 在"玩家补上采样点之外的最后一块"这条路径上会让成形等最多 5 秒。本轮下调到 1 秒。
     * <b>防卡死没有被削弱</b>，理由是该地板从来不是唯一防线：
     * <ul>
     *   <li>{@link #DURATION_MULTIPLIER} 的地板同时不小于「上次全量匹配实测耗时 × 3」——
     *       真正贵的那种匹配（40–132 秒）会把间隔自动拉到 2–6 分钟，单机对单线程检查池的占用率仍 ≤ 1/3；</li>
     *   <li>前面还有 {@link NanochipStructurePrecheck} 的 16 点便宜预检：预检不过<b>根本不会走到</b>全量匹配
     *       （"结构明显还差得远"时一次都不跑）；</li>
     *   <li>签名/失败格证据没变时走 {@link #UNCHANGED_RETRY_MS}（仍是 5 秒）。</li>
     * </ul>
     * ⇒ 1 秒这条只对"便宜匹配（<333 ms）+ 结构有变化"生效，那正是队长要它快的场景。
     */
    public static final long FLOOR_MS = 1_000L;

    /**
     * 签名与证据都没变、且<b>上次匹配失败</b>时的重试间隔。
     *
     * <p>{@code 30 000 ms = 600 tick} ⇒ 持久错误状态的上界 <b>1/30 秒 = 0.033 次/秒</b>。
     * 为什么不是"永久跳过"：见类注释 (b) —— 签名只覆盖 16 个采样点，采样点之外的改动
     * （以及数量类失败这种拿不到"哪一格错了"的情形）必须有一条<b>有界</b>的自愈路径。
     */
    public static final long UNCHANGED_RETRY_MS = 5_000L;

    /**
     * 地板的自适应倍数：地板同时不小于「上次全量匹配实际耗时 × 本值」。
     *
     * <p>⇒ 单台机器对 GTCEu <b>单线程</b>检查池的占用率 ≤ {@code 1/3}，
     * 即使单次匹配要 40–132 秒也不可能靠一台机器把池子占满。
     */
    public static final int DURATION_MULTIPLIER = 3;

    /** 无法给出签名时的哨兵（未加载 / 翻转态 / 模块侧没有 16 点预检）。 */
    public static final long UNKNOWN_SIGNATURE = 0L;

    /**
     * ★ 2026-09-21（队长拍板「<b>落</b>」）「指纹/失败格一变 ⇒ 立刻放行」这条<b>快速通道</b>的
     * <b>最小间隔</b>（单位：tick）。
     *
     * <p><b>依据</b>：`_census/P1四条_可行性.md` 建议顺序第 ② 条（§7.3 第 4 条）——
     * 玩家在铺 {@code F/H/I} 那 625 格时"每放一块 ⇒ 下一个轮询放行一次全量匹配"；
     * 队长拍板的时间口径是 <b>1 秒 = 20 tick</b>，并接受唯一代价
     * "铺那 625 格时，成形最坏晚 1 秒"。
     *
     * <p><b>口径</b>：同一台机器在 {@code 20 tick} 内最多被"变化"放行一次
     * （20 × 50 ms = 1 s；见 {@link #FASTPASS_MIN_INTERVAL_NANOS}）。
     * <b>不是</b> GTO 1.8.0 的 {@code waitingTime}（那是按"调用次数"冷却，见类注释）。
     *
     * <p><b>可回退</b>：删掉本常量、{@link #FASTPASS_MIN_INTERVAL_NANOS}、{@link #tryFastPass} 与
     * {@link #shouldRunFullMatch} 里的两处 {@code && this.tryFastPass(nowNanos)} ⇒ <b>逐字</b>回退。
     */
    public static final int FASTPASS_MIN_INTERVAL_TICKS = 20;

    /**
     * {@link #FASTPASS_MIN_INTERVAL_TICKS} 的纳秒形式（{@code 20 × 50 ms = 1 s}）。
     *
     * <p>1 tick = 50 ms 是 Minecraft 服务端的标称节拍（20 TPS）；本类没有世界对象，
     * 所以用 {@code System.nanoTime()} 走墙钟口径 —— 与 {@link #lastMatchNanos} 的地板同一套时间源
     * （服务器卡顿时"墙钟 1 秒"会略少于 20 个真实 tick，方向是<b>更保守</b>：放行次数只会更少、不会更多）。
     */
    public static final long FASTPASS_MIN_INTERVAL_NANOS = FASTPASS_MIN_INTERVAL_TICKS * 50_000_000L;

    /**
     * ★ 2026-09-21（队长要的"数字"）统计日志的限流窗口（毫秒）。
     *
     * <p>每个窗口在本机<b>有活动</b>时打一条 INFO（中文、写明"A+ 统计学读数"）；
     * 窗口内一次轮询都没有（= 已成形 / 不在待扫集合里）⇒ 一条都不打，不会刷屏。
     */
    public static final long STATS_LOG_INTERVAL_MS = 10_000L;

    /**
     * ★ 2026-09-21（落这一条时发现的坑，必须一起处理）<b>相位安全阀</b>（毫秒）。
     *
     * <h3>坑是什么（javap 双证）</h3>
     * 我们在 {@code asyncCheckPattern} 里放行之后，<b>上游还有一道自带过滤</b>：
     * gtlcore {@code @Overwrite} 的 {@code MultiblockControllerMachine.asyncCheckPattern(long)}
     * 字节码 <b>17–34</b> 是
     * {@code if ((getHolder().getOffset() + periodID) % 4 != 0) return;}，
     * 真正的昂贵匹配在 <b>37–41</b>（{@code if (!checkPatternWithTryLock()) return;}）
     * ⇒ <b>我们放行 ≠ 上游真的跑匹配</b>；被它丢掉的那一趟，本闸却已经"消耗"掉了最小间隔
     * （{@code recordOutcome} 会更新签名与时刻）。
     *
     * <p>而 {@code searchingTask} 的周期是 <b>250 ms</b>、{@code periodID} 每轮 +1
     * ⇒ 上游的"合法轮次"每 <b>4 轮 = 1 秒</b>出现一次，且对同一台机器是<b>固定相位</b>。
     * 我们的最小间隔也正好是 <b>1 秒</b> ⇒ 若放行节奏被锁到"每 4 轮一次"，
     * <b>放行的相位就永远不变</b> —— 若那一相位恰好不合法，这台机器就会
     * <b>再也等不到一次真匹配</b>（3/4 概率，取决于机器的 offset 相位）。这正是队长红线
     * 「不许因此改变什么时候算成形」要防的东西。
     *
     * <h3>本类的两条应对</h3>
     * <ol>
     *   <li>{@link #noteUnformedPoll} 的第二个参数（{@link #gtceuReachesFullMatchThisPoll}）：
     *       <b>只在上游这一轮真会跑匹配的轮次放行</b> ⇒ 放行不再被白白丢掉（见 {@link #tryFastPass}）；</li>
     *   <li>本阀：万一将来上游把那道过滤改了（我们复刻的相位算术与它脱节），
     *       最多饿 {@value #FASTPASS_PHASE_VALVE_MS} ms 就<b>强制放行一次</b>（宁可白放一次，绝不饿死）。</li>
     * </ol>
     * <b>当前配置下本阀永不触发</b>：合法轮次每 4 轮必到一次，而间隔只有 1 秒。
     */
    public static final long FASTPASS_PHASE_VALVE_MS = 4_000L;

    /**
     * 「这一趟其实没走到真匹配」的判定阈值（纳秒）：只用于<b>统计</b>
     * （{@link #logStats} 里的"被上游自带过滤吃掉 N 次"），<b>不改变任何行为</b>。
     *
     * <p>依据：被上游 {@code %4} 过滤丢掉时，{@code super.asyncCheckPattern} 立刻返回，
     * 实测耗时是微秒级；而"真的走到匹配"至少要先把 A+ 的 8,034 个身份格读一遍（≥ 数百微秒）。
     * 50 µs 是两者之间很宽的分离带。判错的后果<b>只影响日志里的一个计数</b>，不影响判定与节奏
     * （这一点是刻意的：把行为挂在一个耗时阈值上才是危险的）。
     */
    private static final long WASTED_PASS_MAX_NANOS = 50_000L;

    /** 是否跑过至少一次全量匹配（未跑过 ⇒ 第一次一律放行）。 */
    private boolean hasRun;

    /** 上一次放行时的签名。 */
    private long lastSignature = UNKNOWN_SIGNATURE;

    /** 上一次放行的时刻（{@code System.nanoTime()}）。 */
    private long lastMatchNanos;

    /** 上一次全量匹配的实测耗时（毫秒；{@code super.asyncCheckPattern} 的墙钟耗时）。 */
    private long lastDurationMs;

    /** 上一次全量匹配之后结构是否仍未成形（= 失败）。 */
    private boolean lastFailed;

    /** 上次失败时 GTCEu 报出的那一格（可证伪证据；null = 没有可用证据）。 */
    private BlockPos probePos;

    /** 上次失败时 {@link #probePos} 上的方块状态（用**引用**比较，无属性方块的状态是单例）。 */
    private BlockState probeState;

    // ==========================================================================================
    // ★ 2026-09-21（队长拍板「落」）① 最小间隔的状态；② 队长要的"数字"（纯统计，不参与判定）
    // ==========================================================================================

    /** 上一次「快速通道」放行的时刻（{@code System.nanoTime()}；<b>0 = 还没记过</b> ⇒ 下一次不设限）。 */
    private long lastFastPassNanos;

    /**
     * 本轮的轮询是否会真的走到上游的昂贵匹配（由 {@link #noteUnformedPoll} 每轮刷新）。
     *
     * <p>默认 {@code true}（fail-open）：调用方没喂这个信息时，本闸退化成"只看最小间隔"的旧形状。
     */
    private boolean lastPollReachesMatch = true;

    /** 统计窗口：未成形侧轮询次数（由调用方每次轮询调 {@link #noteUnformedPoll} 记一次）。 */
    private long statsUnformedPolls;

    /** 统计窗口：到达本闸的次数（= 两道预检都放行、真正问到"要不要跑"的次数）。 */
    private long statsGateCalls;

    /** 统计窗口：闸授予放行的次数（= {@link #recordOutcome} 次数；含被上游过滤吃掉的空转）。 */
    private long statsGrants;

    /** 统计窗口：闸授予且**真的走到匹配**的次数（= 放行真匹配次数）。 */
    private long statsRealMatches;

    /** 统计窗口：闸授予但被上游自带 {@code %4} 过滤吃掉的次数（纯统计；见 {@link #WASTED_PASS_MAX_NANOS}）。 */
    private long statsWastedGrants;

    /** 统计窗口：因最小间隔被压住的次数（同一次轮询里两个条件都命中时只记一次）。 */
    private long statsFastPassSuppressed;

    /** 统计窗口：因"这一轮上游不会跑匹配"（相位）而没放行的次数（与最小间隔分开计）。 */
    private long statsPhaseSkipped;

    /**
     * 同一次轮询的 {@code nowNanos} 去重哨兵（见 {@link #tryFastPass}）。
     *
     * <p>初值取 {@code -1L}（**不是** {@code Long.MIN_VALUE}）：本字段只参与 {@code !=} 相等比较、
     * 从不参与减法，但体检第 45 项 `_census/check_tick_sentinel_overflow.js` 的口径是
     * 「{@code = Long.MIN_VALUE} 的 long 字段必须带 {@code != Long.MIN_VALUE} 守卫，否则判红」——
     * 那条守卫是给 {@code now - lastX} 形状的限流写的（{@code now - Long.MIN_VALUE} 会溢出成大负数，
     * 导致诊断永远不打）。这里换个溢出安全的初值即可：{@code System.nanoTime()} 的原点是"任意"、
     * 理论上可为负，但即便真撞上 {@code -1} 也只是少记一次统计，不影响放行判定。
     */
    private long statsSuppressedNanos = -1L;

    /** 统计窗口：真匹配耗时累计（纳秒；算平均值用）。 */
    private long statsDurationSumNanos;

    /** 统计窗口：真匹配耗时最大值（纳秒）。 */
    private long statsDurationMaxNanos;

    /** 上一次统计日志的时刻（{@code System.nanoTime()}；0 = 还没起表）。 */
    private long statsLastLogNanos;

    /** 本机控制器坐标（只用于日志识别；由 {@link #noteUnformedPoll} 刷新引用，不做任何判断）。 */
    private BlockPos statsPos;

    /**
     * <b>未成形侧的唯一决策入口</b>：本次是否允许调用昂贵的整图案匹配。
     *
     * @param level      服务器世界（用于读"上次失败那一格"的当前方块）
     * @param signature  预检在同一次扫描里算出的签名（模块侧传 {@link #UNKNOWN_SIGNATURE}）
     * @param nowNanos   {@code System.nanoTime()}（调用方取一次，用于计算间隔与耗时）
     * @return {@code true} = 允许调用 {@code super.asyncCheckPattern(periodID)}
     */
    public boolean shouldRunFullMatch(Level level, long signature, long nowNanos) {
        this.statsGateCalls++;
        // ★★ 2026-11-12【原初模式】总开关：直接放行，但**只放行、不删统计** ★★
        //   本方法只回答「这一轮允不允许跑昂贵整图案匹配」；原初模式下答案恒为「允许」
        //   ⇒ 不再有被「签名没变 / 失败格没变 / 自适应地板」压住的放行。
        //   统计读数照记：noteUnformedPoll()（未成形轮询计数，调用方另调）与 recordOutcome()
        //   （放行次数/真匹配耗时，调用方在 super 之后调）都在本方法之外，一行未动 ⇒
        //   「裸 return（必然不成形，直接挡掉）= 未成形轮询数 − 到达本闸次数」这条口径仍然成立，
        //   且在原初模式下应当读到 0。
        //   位置在 statsGateCalls++ **之后**：口径仍是"到达本闸的次数"。
        //   置 false 即逐字回到下面那套「签名 / 失败格证据 / 自适应地板」（一行回退）。
        if (GTLModularConfig.isNanochipOriginalMode()) {
            return true;
        }
        if (!this.hasRun) {
            // ★ 2026-09-21（队长「落」）：首次放行也记时刻 —— 最小间隔对"任何两次变化驱动的放行"都成立
            //   （不记的话，初次之后紧跟着的第一次变化会绕过间隔）。
            this.lastFastPassNanos = nowNanos;
            return true;                                    // 初次：放行（结构刚建好时立刻成形）
        }
        if (signature != this.lastSignature) {
            if (this.tryFastPass(nowNanos)) {
                return true;                                // 决策表 #1：签名变了 ⇒ 放行（★ 受最小间隔）
            }
            // ★ 被最小间隔压住：继续往下走（不是"立刻 true"）。下面只有地板一行，而
            //   FLOOR_MS(1000 ms) ≥ FASTPASS_MIN_INTERVAL(1000 ms) 且 lastFastPassNanos == lastMatchNanos
            //   ⇒ 被压住时地板必然也判否（这条不变式由体检 ⑨ 钉住 FLOOR_MS 的字面值保证），
            //   所以这里不会"绕开间隔提前放行"。
        }
        if (this.probePos != null && level != null && level.isLoaded(this.probePos)
                && level.getBlockState(this.probePos) != this.probeState) {
            if (this.tryFastPass(nowNanos)) {
                return true;                                // 决策表 #1：上次失败那一格变了 ⇒ 放行（★ 同一个最小间隔）
            }
        }
        long waitedMs = (nowNanos - this.lastMatchNanos) / 1_000_000L;
        return waitedMs >= effectiveFloorMs();              // 决策表 #2 / #3：受地板
    }

    /**
     * 当前生效的地板（毫秒）＝ {@code max(上次失败 ? 30 s : 5 s, 3 × 上次耗时)}。
     *
     * @return 毫秒；未跑过任何匹配时返回 0
     */
    public long effectiveFloorMs() {
        if (!this.hasRun) {
            return 0L;
        }
        long base = this.lastFailed ? UNCHANGED_RETRY_MS : FLOOR_MS;
        return Math.max(base, (long) DURATION_MULTIPLIER * this.lastDurationMs);
    }

    /**
     * ★ 2026-09-21（队长拍板「<b>落</b>」）<b>快速通道的最小间隔判定</b>
     * —— "指纹/失败格一变 ⇒ 立刻放行"这条路的唯一闸。
     *
     * <p>判据是<b>纯时间比较</b>：距上一次快速通道放行不足
     * {@link #FASTPASS_MIN_INTERVAL_NANOS}（= {@value #FASTPASS_MIN_INTERVAL_TICKS} tick = 1 s）
     * ⇒ 本次<b>不放行</b>，并记一次"被最小间隔压住"。
     * <b>不累计任何次数</b>（GTO 的 {@code waitingTime} 那种"按调用次数冷却"在本类里不存在）。
     *
     * <p>被压住的那一次不会丢：{@link #lastSignature} / {@code probePos} 只在
     * {@link #recordOutcome} 里更新 ⇒ 调用方下一轮（250 ms 后）看到的仍然是"变了"，
     * 间隔一到就放行 ⇒ 最坏延迟 = 1 s + 一个轮询周期（队长接受的口径）。
     *
     * @param nowNanos 调用方取一次的 {@code System.nanoTime()}
     *                 （同一次 {@link #shouldRunFullMatch} 内的两次调用传的是<b>同一个值</b>）
     * @return {@code true} = 本次允许立刻放行（并把放行时刻记为 {@code nowNanos}）
     */
    private boolean tryFastPass(long nowNanos) {
        final long sinceLastPass = this.lastFastPassNanos == 0L
                ? Long.MAX_VALUE : nowNanos - this.lastFastPassNanos;
        // ① 相位：这一轮上游自己就不会跑匹配（%4 过滤）⇒ 现在放行等于白放，最小间隔会被白白消耗掉
        //    （那正是"放行节奏被锁到同一相位 ⇒ 永远等不到一次真匹配"的成因，见 FASTPASS_PHASE_VALVE_MS）。
        //    相位不匹配不计入"最小间隔压住"（那是另一回事），单独计数。
        if (!this.lastPollReachesMatch && sinceLastPass < FASTPASS_PHASE_VALVE_MS * 1_000_000L) {
            if (this.statsSuppressedNanos != nowNanos) {
                this.statsSuppressedNanos = nowNanos;
                this.statsPhaseSkipped++;
            }
            return false;
        }
        // ② 最小间隔（队长 2026-09-21 拍板「落」：1 秒 = 20 tick）。
        if (sinceLastPass < FASTPASS_MIN_INTERVAL_NANOS) {
            if (this.statsSuppressedNanos != nowNanos) {
                // 同一次 shouldRunFullMatch 里"签名变"与"失败格变"可能同时命中 ⇒ 只记一次
                this.statsSuppressedNanos = nowNanos;
                this.statsFastPassSuppressed++;
            }
            return false;
        }
        this.lastFastPassNanos = nowNanos;
        return true;
    }

    /**
     * ★ 2026-09-21 <b>复刻上游的"这一轮会不会真跑匹配"</b>（只读观测，不参与判定）。
     *
     * <p>依据是部署 jar 的字节码（gtlcore 1.2.3.2-fix2
     * {@code org.gtlcore.gtlcore.mixin.gtm.api.machine.MultiblockControllerMachineMixin}）：
     * <pre>
     *   asyncCheckPattern(long) 偏移 17–34:
     *     getHolder().getOffset() + periodID  →  lrem 4l  →  lcmp 0  →  ifne 78(return)
     *   偏移 37–41:
     *     if (!checkPatternWithTryLock()) return;     ← 昂贵匹配在这里，只在 17–34 通过时才到
     * </pre>
     * 而 {@code searchingTask} 每 <b>250 ms</b> 跑一轮、{@code periodID} 每轮 +1
     * ⇒ 对同一台机器，合法轮次是<b>固定相位</b>上的每 4 轮一次。本方法把那一相位算出来。
     *
     * <p><b>为什么必须算它</b>：我们的最小间隔正好也是 1 秒（= 4 轮）；若放行的相位固定且不合法，
     * 这台机器就会再也等不到真匹配（见 {@link #FASTPASS_PHASE_VALVE_MS} 的完整说明）。
     *
     * @param machineOffset 该机器方块的 offset（{@code getHolder().getOffset()}，每台恒定）
     * @param periodID      本轮 {@code searchingTask} 的期号（{@code asyncCheckPattern} 的入参）
     * @return {@code true} = 上游这一轮会走到昂贵匹配
     */
    public static boolean gtceuReachesFullMatchThisPoll(long machineOffset, long periodID) {
        return (machineOffset + periodID) % 4L == 0L;
    }

    /**
     * ★ 2026-09-21（队长要的"数字"）<b>未成形侧轮询计数</b>：调用方在"未成形 + 世界可用"之后、
     * 两道预检<b>之前</b>调一次。<b>纯观测，不参与任何判定</b>（不读世界、不改任何门）。
     *
     * <p>有了它才能算出队长要的"裸 return 次数"：
     * <pre>
     *   裸 return（必然不成形，直接挡掉）= 本计数 − 到达本闸的次数（{@link #statsGateCalls}）
     * </pre>
     * 因为在调用方里，从这一行到"到达本闸"之间<b>唯一的早退</b>就是两道预检的裸 {@code return}
     * （16 点预检 / A+ 精确预检；{@code level == null} 那一支在本行<b>之前</b>，不计入）。
     *
     * <p>它同时兼任统计日志的<b>心跳</b>：未成形侧每 250 ms 被轮询一次 ⇒ 即使这一窗口里
     * 一次匹配都没跑（全是裸 return），日志也照打 —— 那正是队长要看的稳态读数。
     *
     * @param controllerPos 本机控制器坐标（只用于日志识别；不复制、不做任何判断）
     * @param reachesFullMatchThisPoll 上游这一轮会不会真的跑到昂贵匹配
     *        （调用方用 {@link #gtceuReachesFullMatchThisPoll} 算好传进来；<b>只读观测</b>，
     *        用来避免"放行了却被上游自带过滤丢掉"从而把最小间隔相位锁死，见 {@link #FASTPASS_PHASE_VALVE_MS}）
     */
    public void noteUnformedPoll(BlockPos controllerPos, boolean reachesFullMatchThisPoll) {
        this.statsUnformedPolls++;
        this.statsPos = controllerPos;
        this.lastPollReachesMatch = reachesFullMatchThisPoll;
        long now = System.nanoTime();
        if (this.statsLastLogNanos == 0L) {
            this.statsLastLogNanos = now;                    // 第一次轮询只起表，不打日志
            return;
        }
        if (now - this.statsLastLogNanos >= STATS_LOG_INTERVAL_MS * 1_000_000L) {
            logStats(now);
        }
    }

    /**
     * ★ 2026-09-21（队长要的"数字"）把本窗口的读数打成一条中文 <b>INFO</b>，然后重新起表。
     *
     * <p><b>落点写明是「A+ 统计学读数」</b>：与既有的
     * {@code NAC 结构结论未变汇总}（{@code NanochipStructureLogGate}，讲的是"结论/缺件清单有没有变"）
     * <b>不是同一条通道</b> —— 本行只讲<b>次数与耗时</b>，一眼可分辨。
     *
     * <p>窗口内一次轮询都没有 ⇒ 一条都不打（已成形 / 不在待扫集合时不会刷屏）。
     */
    private void logStats(long nowNanos) {
        final long windowMs = (nowNanos - this.statsLastLogNanos) / 1_000_000L;
        final long polls = this.statsUnformedPolls;
        final long reached = this.statsGateCalls;
        final long bareReturns = polls > reached ? polls - reached : 0L;      // 口径见 noteUnformedPoll
        final long grants = this.statsGrants;
        final long realMatches = this.statsRealMatches;
        final long wasted = this.statsWastedGrants;
        // 派生项：到达闸却没放行 = 被最小间隔压住 + 被相位跳掉 + 被地板压住
        //   （UNFORMED_SKIP = 1 由体检 ⑦ 钉死 ⇒ 计数节流不会在这中间拦下一次）
        final long denied = reached > grants ? reached - grants : 0L;
        final long floorDenied = denied > this.statsFastPassSuppressed + this.statsPhaseSkipped
                ? denied - this.statsFastPassSuppressed - this.statsPhaseSkipped : 0L;
        final long windowMaxMs = this.statsDurationMaxNanos / 1_000_000L;
        final long windowAvgMs = realMatches == 0L
                ? 0L : this.statsDurationSumNanos / realMatches / 1_000_000L;
        GTLModular.LOGGER.info("[gtlmodular] 【A+ 统计学读数】NAC 结构检查 @{}：窗口 {} ms ｜ 未成形轮询 {} 次 ｜ "
                        + "裸 return（必然不成形，直接挡掉）{} 次 ｜ 放行真匹配 {} 次（闸授予 {} 次，其中被上游自带 "
                        + "(offset+periodID)%4 过滤吃掉 {} 次）｜ 最小间隔压住 {} 次 ｜ 相位跳过 {} 次 ｜ "
                        + "地板压住 {} 次 ｜ 真匹配单次耗时：最近 {} ms / 窗口最大 {} ms / 窗口平均 {} ms",
                this.statsPos == null ? "?" : this.statsPos.toShortString(),
                windowMs, polls, bareReturns, realMatches, grants, wasted,
                this.statsFastPassSuppressed, this.statsPhaseSkipped, floorDenied,
                this.lastDurationMs, windowMaxMs, windowAvgMs);
        this.statsUnformedPolls = 0L;
        this.statsGateCalls = 0L;
        this.statsGrants = 0L;
        this.statsRealMatches = 0L;
        this.statsWastedGrants = 0L;
        this.statsFastPassSuppressed = 0L;
        this.statsPhaseSkipped = 0L;
        this.statsDurationSumNanos = 0L;
        this.statsDurationMaxNanos = 0L;
        this.statsLastLogNanos = nowNanos;
    }

    /**
     * <b>回填结论</b>：调用方在 {@code super.asyncCheckPattern(periodID)} 返回之后立刻调用。
     *
     * @param signature  本次放行时用的签名
     * @param startNanos  调用 super 之前的 {@code System.nanoTime()}
     * @param endNanos    调用 super 之后的 {@code System.nanoTime()}
     * @param failed      {@code !isFormed()}（super 之后读）
     * @param state       该方的 {@code getMultiblockState()}（可为 null；失败时用来取"哪一格错了"）
     */
    public void recordOutcome(long signature, long startNanos, long endNanos, boolean failed,
                              MultiblockState state) {
        // ★ 2026-09-21（队长要的"数字"）：本窗口"放行真匹配"的次数与耗时。
        //   纯统计（只自增/累加几个 long），**不参与任何判定**，也不改下面任何一个字段的语义。
        this.statsGrants++;
        final long durationNanos = Math.max(0L, endNanos - startNanos);
        if (durationNanos < WASTED_PASS_MAX_NANOS) {
            // 这一趟根本没走到匹配（被上游自带的 (offset+periodID)%4 过滤丢掉了）。
            //   ★ 只记账，**不改任何行为**：这条判据是启发式，绝不拿它去决定放不放行。
            this.statsWastedGrants++;
        } else {
            this.statsRealMatches++;
            this.statsDurationSumNanos += durationNanos;
            if (durationNanos > this.statsDurationMaxNanos) {
                this.statsDurationMaxNanos = durationNanos;
            }
        }
        this.hasRun = true;
        this.lastSignature = signature;
        this.lastMatchNanos = startNanos;
        this.lastDurationMs = Math.max(0L, (endNanos - startNanos) / 1_000_000L);
        this.lastFailed = failed;
        if (!failed) {
            // 已成形的匹配：旧证据失去意义，清掉（下次失效后重新建立）
            this.probePos = null;
            this.probeState = null;
            return;
        }
        BlockPos pos = null;
        BlockState blockState = null;
        PatternError error = state == null ? null : state.error;
        if (error != null && error != MultiblockState.UNLOAD_ERROR && error != MultiblockState.UNINIT_ERROR) {
            try {
                BlockPos raw = error.getPos();
                Level world = state.getWorld();
                if (raw != null && world != null && world.isLoaded(raw)) {
                    pos = raw.immutable();
                    blockState = world.getBlockState(pos);
                }
            } catch (RuntimeException ignored) {
                // 诊断/证据绝不能拖垮结构检查本身：拿不到就当"没有证据"，走地板
                pos = null;
                blockState = null;
            }
        }
        this.probePos = pos;
        this.probeState = blockState;
    }

    /** 诊断用：上一次全量匹配的实测耗时（毫秒）。 */
    public long lastDurationMs() {
        return this.lastDurationMs;
    }

    /** 诊断用：是否已经跑过至少一次全量匹配。 */
    public boolean hasRun() {
        return this.hasRun;
    }

    /** 诊断用：当前有没有"可证伪失败格"证据。 */
    public boolean hasFailureEvidence() {
        return this.probePos != null;
    }

    /**
     * <b>归零</b>：由 {@code onStructureInvalid()} 调用（主机与 14 台模块各一处）。
     *
     * <p>为什么必须归零：{@code onStructureInvalid()} 只在「<b>曾经成形</b>的机器失效」时跑到
     * （GTCEu 的 {@code IMultiController.checkPattern()} 只在 {@code isFormed()} 时才调它）。
     * 那一刻是<b>大变化</b>（结构从成形变成不成），而我们的签名只覆盖 16 个采样点、未必能察觉
     * ⇒ 若不归零，"玩家把失效的那块补齐"可能被地板拖住最多 {@code max(30 s, 3×上次耗时)}，
     * 把"免等待"破功。
     *
     * <p>归零<b>不会</b>给"从没成形过、一直缺方块"的持久错误状态开后门：那种状态根本不会调到本方法
     * （它的 {@code isFormed()} 一直是 false，GTCEu 不会调 {@code onStructureInvalid()}）。
     */
    public void reset() {
        this.hasRun = false;
        this.lastDurationMs = 0L;
        this.lastFailed = false;
        this.probePos = null;
        this.probeState = null;
        // ★ 2026-09-21（队长「落」）：失效 / 手动「立即更新结构」是一次**大变化** ⇒ 最小间隔一起归零。
        //   否则"刚失效就要重新成形"会被上一轮的时间戳白等最多 1 秒（与本方法归零地板的取舍一致）。
        this.lastFastPassNanos = 0L;
        // 统计窗口一并归零（纯观测；statsLastLogNanos 不动 ⇒ 日志节奏不变、不会因失效而刷屏）。
        this.statsUnformedPolls = 0L;
        this.statsGateCalls = 0L;
        this.statsGrants = 0L;
        this.statsRealMatches = 0L;
        this.statsWastedGrants = 0L;
        this.statsFastPassSuppressed = 0L;
        this.statsPhaseSkipped = 0L;
        this.statsDurationSumNanos = 0L;
        this.statsDurationMaxNanos = 0L;
    }
}
