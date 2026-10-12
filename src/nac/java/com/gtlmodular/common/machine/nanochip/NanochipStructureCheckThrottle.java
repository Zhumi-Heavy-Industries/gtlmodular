package com.gtlmodular.common.machine.nanochip;

import com.gtlmodular.common.data.nanochip.NanochipModContext;

/**
 * NAC 结构自检的<b>节流器</b>（2026-09-16 止血；2026-09-18 按实机证据加严；2026-09-18 第三轮改为「预检门 + 免等待」）。
 *
 * <h3>为什么需要它（实测证据，不是猜的）</h3>
 * 玩家实测出现过一次服务器落后 <b>123729ms / 2474 tick</b> 的停摆。线程转储显示：
 * <pre>
 *   "GTCEu Multiblock Async Thread-0" RUNNABLE
 *     at BlockPattern.gtlcore$matchAisles → gtlcore$replayLayout → matchSlice → getTileEntity
 *   "Server thread" WAITING on ReentrantLock … owned by "GTCEu Multiblock Async Thread-0"
 *     at MultiblockControllerMachine.lambda$asyncCheckPattern$2（:547，patternLock.lock()）
 * </pre>
 * 机制（对 GTCEu 1.4.4 字节码核对）：
 * <ol>
 *   <li>{@code MultiblockWorldSavedData} 用一个<b>单线程</b>调度器、<b>每 250ms</b> 跑一次
 *       {@code searchingTask()}，对<b>所有</b>"未成形或有错"的控制器调用 {@code asyncCheckPattern(periodID)}；
 *       每次调用会往服务端线程投一个 {@code TickTask}，该任务先 {@code patternLock.lock()}
 *       —— <b>服务端线程就这样被按在整个匹配过程的时长上</b>；</li>
 *   <li>控制器侧再按 {@code (offset+periodID)%4==0} 过滤 ⇒ 每个坏结构约<b>每 1 秒</b>全量重扫一次；</li>
 *   <li>而 NAC 的图案包围盒是 <b>63×49×63 = 194481 格</b>，且 <b>1 台主机 + 14 台模块塔共用同一个线程</b>
 *       ⇒ 一轮 pass 的累计工作量就是"一次卡顿"的量级。</li>
 * </ol>
 *
 * <h3>2026-09-18 二次实机取证（第二轮加严的依据）</h3>
 * {@code latest.log}（2026-09-18 22:33–22:48，那份 22:43:47 仍在写、就是卡死那次）原文：
 * <pre>
 *   [22:44:15] ModernFix integrated server watchdog/ERROR:
 *              A single server tick has taken 40001, more than 40000 milliseconds
 *   [22:45:00] … has taken 85752, more than 40000 milliseconds
 *   [22:45:47] … has taken 132410, more than 40000 milliseconds
 *   [22:46:03] [Server thread/WARN]: Can't keep up! … Running 147834ms or 2956 ticks behind
 * </pre>
 * 三次线程转储形状完全一致，且栈顶都落在
 * {@code gtlmodular@0.1.13/…/NanochipAssemblyComplexMachine.asyncCheckPattern:104}
 * —— 也就是说<b>这个节流器当时确实在跑，但没挡住</b>。原因是它只在 {@code !formed} 时才降频，
 * 而实测那一刻主机在「成形 / 失效」之间反复横跳（9 分钟里结构失效 114 次、已成形 40 次），
 * 相当一部分调用走的是 {@code formed == true} 分支 ⇒ <b>每次都真跑全量匹配</b>。
 *
 * <h3>2026-09-18 第三轮：把「频率」换成「预检门」（队长批准的方案）</h3>
 * 第二轮把两个分支都节流成 6，稳态开销降了，但<b>代价是玩家补上最后一块方块后最坏要等约 6 秒才成形</b>。
 * 第三轮改成：
 * <ul>
 *   <li><b>未成形这一侧</b>：先跑 {@link NanochipStructurePrecheck}（O(≤16) 恒定世界查询）。
 *       预检<b>失败</b> ⇒ 本轮直接 return，<b>不</b>碰昂贵匹配（那些格是"任何合法匹配都必须满足"的格，
 *       失败即一定不成形）；预检<b>通过</b> ⇒ <b>立刻</b>做整图案匹配
 *       （{@link #UNFORMED_SKIP} = 1，不再拖延）⇒「放最后一块 → 成形」= <b>下一轮检查即成</b>，
 *       延迟只受匹配自身耗时限制。</li>
 *   <li><b>已成形这一侧</b>：<b>不</b>过预检（预检绝不能挡住失效判定），保留 {@link #FORMED_SKIP} 的周期兜底节流。</li>
 * </ul>
 *
 * <h3>★ 第三轮的 javap 证据：为什么 {@code FORMED_SKIP} 对「拆一块 → 失效」<b>没有影响</b></h3>
 * <p><b>(a) {@code asyncCheckPattern} 什么时候才被叫到</b>（{@code MultiblockControllerMachine.asyncCheckPattern(long)} 字节码）：
 * <pre>
 *   0–7   : if (!getMultiblockState().hasError() &amp;&amp; isFormed) goto 78(return)   ← 已成形且无错 ⇒ 直接返回
 *   17–34 : if ((getHolder().getOffset() + periodID) % 4 != 0) return           ← GTCEu 自带的 1/4 过滤
 *   37–41 : if (!checkPatternWithTryLock()) return                              ← ★ 昂贵整图案匹配就在这里的调用线程上跑
 *   44–75 : server.execute(lambda$asyncCheckPattern$0)                          ← 往服务端线程投 TickTask
 *   78    : return
 * </pre>
 * 而 {@code MultiblockWorldSavedData.searchingTask()} 字节码 <b>offset 10–47</b> 就是
 * 「遍历 {@code controllers}（{@code CopyOnWriteArrayList}），逐个 {@code invokeinterface asyncCheckPattern(periodID)}」，
 * 它在<b>单线程</b>的 GTCEu Multiblock Async Thread 上执行（转储栈已证）。
 * <p><b>(b) 方块变更走的是另一条路（事件驱动，且与 {@code asyncCheckPattern} 无关）</b> ——
 * gtlcore {@code mixin/mc/LevelMixin.gtceu$updateChunkMultiBlocks} 字节码：
 * <pre>
 *   29–43  : MultiblockWorldSavedData.getOrCreate(serverLevel) → getControllersInChunk(chunk.getPos())
 *   85–90  : if (!state.getController().isFormed()) continue      ← 只处理【已成形】的控制器
 *   96–120 : if (state.isPosInCache(pos)) server.execute(() -&gt; state.onBlockStateChanged(pos, newState))
 * </pre>
 * 而 {@code MultiblockState.onBlockStateChanged(BlockPos, BlockState)} 字节码：
 * <pre>
 *   20–81  : 若改的正是控制器自己那格、且新方块不再是控制器 ⇒ <b>直接</b> onStructureInvalid() + removeMapping()
 *   84–147 : 取 controller；isFormed &amp;&amp; 新方块是 ActiveBlock &amp;&amp; 属于 vaBlocks ⇒ return（忽略活化状态变化）
 *   148–155: if (controller.checkPatternWithLock()) …                ← ★ 同步整图案匹配（服务端线程上）
 *   158–179: 成形成功 ⇒ setFlipped(isNeededFlip()) + onStructureFormed()
 *   182–219: 否则 ⇒ setFlipped(false) + <b>onStructureInvalid()</b> + removeMapping() + addAsyncLogic()
 * </pre>
 * <p><b>结论</b>：拆掉一块（在匹配缓存覆盖范围内）⇒ 服务端线程在
 * {@code MinecraftServer.execute} 的那个 Runnable 里<b>立刻</b>跑 {@code checkPatternWithLock()} 并立刻
 * {@code onStructureInvalid()}，<b>完全不走 {@code asyncCheckPattern}</b> ⇒ {@code FORMED_SKIP} 只影响
 * 「GTCEu 认为还没成形、或有错」时的周期兜底重扫，<b>不影响</b>"拆一块立刻失效"。故保留 ÷6。
 *
 * <h3>关于「拆掉一块」的一个诚实补充</h3>
 * 事件路径的两个前提是「控制器此刻已被 GTCEu 记为 {@code isFormed}」与「改的那格在
 * {@code MultiblockState.cache} 里」（{@code isPosInCache}）。若两者任一不成立，GTCEu 既不即时重查，
 * 也不会走周期路径（{@code isFormed &amp;&amp; !hasError} ⇒ {@code asyncCheckPattern} 第 0–7 行直接返回）
 * —— 那是 GTCEu 自身的行为，与本类取值无关，如实登记在报告里。
 *
 * <p><b>诚实边界</b>：单次全量匹配的绝对成本（40–132 秒/tick）未能从日志单独定位到某一处
 * （裸扫描 194481 格的估算远小于此，真正的大头疑在 {@code gtlcore$replayLayout} 重放 /
 * 15 个控制器共用单线程的累计 pass）。本轮用「预检门」把<b>该不该跑</b>管住；单次成本在 GTCEu/gtlcore
 * 内部，我们这侧无法改（见施工记录与本轮报告「无法优化项」）。
 */
public final class NanochipStructureCheckThrottle {

    /**
     * 未成形时，每 N 次被叫到才真跑一次检查。
     *
     * <p><b>2026-09-18 第三轮：6 → 1（免等待）。</b>
     *
     * <p>理由：未成形这一侧的"要不要跑昂贵匹配"已由 {@link NanochipStructurePrecheck} 这个
     * <b>O(≤16) 恒定开销的预检门</b>决定 —— 预检失败时调用点根本不进本方法（见
     * {@code NanochipAssemblyComplexMachine.asyncCheckPattern}）。所以这里再叠一层计数节流，
     * 只会把"预检刚刚通过（= 玩家可能补上了最后一块）"这件事往后拖最多 6 秒。
     *
     * <p>取 1 之后，未成形侧的昂贵匹配频率 = <b>GTCEu 原生的 ≈1 次/秒</b>（
     * {@code searchingTask} 每 250ms × {@code (offset+periodID)%4}），
     * <b>只在预检通过时发生</b>；稳态（结构残缺 / 已成形）时昂贵匹配的次数是 <b>0</b>。
     */
    public static final int UNFORMED_SKIP = 1;

    /**
     * <b>已成形</b>时，每 N 次被叫到才真跑一次检查。
     *
     * <p>取 6（第二轮的值，本轮不变）。依据见类注释的 javap 证据：
     * <b>「拆一块 → 立刻失效」走 GTCEu/gtlcore 的事件路径，不经过 {@code asyncCheckPattern}</b>
     * ⇒ 这一档<b>不影响</b>失效的即时性，只压掉「已成形却有错」时的周期兜底重扫。
     *
     * <p>另外注意：{@code asyncCheckPattern} 第 0–7 行决定了"已成形且无错"的控制器
     * <b>根本不会被调用</b>，所以这一档只在"GTCEu 认为有错 / 还没成形、我们已成形的分歧窗口"里生效。
     */
    public static final int FORMED_SKIP = 6;

    private int seen;

    /**
     * @param formed 当前是否已成形
     * @return 本次是否真的要跑结构检查
     */
    public boolean shouldRun(boolean formed) {
        // ★★ 2026-11-12【原初模式】总开关：不做我们那套计数节流，一律放行 ★★
        //   「多久检查一次」完全交回上游 MultiblockWorldSavedData.searchingTask 自己的节奏
        //   （每 250ms 一轮 × 上游自带的 (offset+periodID)%4 过滤）。
        //   置 false 即逐字回到原来的 `(++seen % skip) == 0`（一行回退；调用方的计数节流门随之恢复）。
        if (NanochipModContext.config().isNanochipOriginalMode()) {
            return true;
        }
        int skip = formed ? FORMED_SKIP : UNFORMED_SKIP;
        return (++this.seen % skip) == 0;
    }

    /** 计数归零（保留给"确实需要让下一次检查立刻生效"的调用点；当前无调用者）。 */
    public void reset() {
        this.seen = 0;
    }
}
