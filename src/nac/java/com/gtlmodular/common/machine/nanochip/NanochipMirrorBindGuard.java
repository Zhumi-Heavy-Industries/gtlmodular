package com.gtlmodular.common.machine.nanochip;


import com.gtlmodular.common.data.nanochip.NanochipModContext;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachineBase;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferProxyPartMachine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * ★ 2026-11-11【样板总成镜像绑定 = 0 · 恢复 + 诊断 + 显示】★★
 * <b>把「已存过坐标、却连不上」的镜像绑定按上游正规入口重建一次</b>，并把这一格读数摊给玩家。
 *
 * <h3>一、这一格数字是谁算的（「样板总成镜像绑定数量：%s」）</h3>
 * <ol>
 *   <li><b>语言键</b>：{@code gtceu.top.proxies_bound = "样板总成镜像绑定数量：%s"} —— 键与中英两条文案都在
 *       <b>GTCEu</b> 的 lang 里（{@code gtl-dev/inspect/gtceu/assets/gtceu/lang/zh_cn.json:3971}、
 *       {@code en_us.json:3976 = "Buffer Proxies Bound: %s"}）。</li>
 *   <li><b>填它的显示层</b>：gtlcore 的 Jade provider
 *       {@code org.gtlcore.gtlcore.integration.jade.provider.MEPatternBufferProvider}
 *       （class 落在 {@code <工作区>/org/gtlcore/gtlcore/integration/jade/provider/MEPatternBufferProvider.class}；
 *       该 class 的常量池里同时有 {@code gtceu.top.proxies_bound} 与 {@code (Ljava/lang/String;)I}
 *       —— 即 {@code Component.translatable(key, <int>)}）。上游同族还有 {@code MEPatternBufferJadeMerger}
 *       （同包，用于把内部槽读数合并成「ME网络库存」那一行）。</li>
 *   <li><b>那个 int 的算式（唯一权威）</b>：{@code MEPatternBufferPartMachineBase.getProxies()}
 *       —— javap 逐指令：{@code _census/_javap_simple_gtlcore_bufferbase.txt:182-236}
 *       （同族第二份转储 {@code gtl-dev/api-dump/gtlcore_base.txt:182-236}）。全体是：
 *       <pre>
 * if (proxyMachines.size() != proxies.size()) {            // :186-190
 *     Level lv = getLevel();                               // :192
 *     if (lv == null) return unmodifiableSet(proxyMachines);// :194-199
 *     proxyMachines.clear();                               // :202
 *     for (BlockPos p : proxies) {                         // :204-208
 *         MetaMachine m = MetaMachine.getMachine(lv, p);   // :214-216
 *         if (m instanceof MEPatternBufferProxyPartMachine pxy) proxyMachines.add(pxy);  // :219-227
 *         else iterator.remove();                          // :230-231 ← 位置已空/换块 ⇒ 从 proxies 摘掉
 *     }
 * }
 * return unmodifiableSet(proxyMachines);                   // :233-236
 *       </pre>
 *       ⇒ <b>读数 = {@code proxyMachines.size()}（这一刻真解析得到、且已登记的镜像部件数）
 *       = 自愈后的 {@code proxies.size()}（记过坐标的镜像位置数）</b>。
 *       两个集合都是 {@code private final} 普通集合、<b>都不带 {@code @Persisted}</b>
 *       （字段清单 {@code gtlcore_base.txt:23/25}）⇒ 这个数字本身不入存档、读档后要从别处重建。</li>
 * </ol>
 *
 * <h3>二、「绑定」是怎么建立的（上游三步入链，逐条给行号）</h3>
 * <ol>
 *   <li><b>玩家侧（唯一的发起口）</b>：{@code MEPatternBufferPartMachineBase.onUse(...)}
 *       {@code gtlcore_base.txt:547-602} —— 手持 {@code GTItems.TOOL_DATA_STICK}（闪存）右键<b>样板总成</b>，
 *       把它自己的 {@code getPos()} 以 {@code int[3]} 写进闪存的 {@code "pos"} 键（{@code :571-591}）。</li>
 *   <li><b>镜像侧（读那把闪存）</b>：{@code MEPatternBufferProxyPartMachine.onUse(...)}
 *       {@code _census/_javap_simple_gtlcore_proxy.txt:274-343} —— 读闪存 {@code "pos"} 构造 {@code BlockPos}，
 *       调 {@code setBuffer(pos)}（{@code :335}）。</li>
 *   <li><b>入链</b>：{@code setBuffer(BlockPos)}（{@code :85-173}）=
 *       ①{@code releaseBuffer()} 先解旧链（{@code :94}）→
 *       ②{@code MetaMachine.getMachine(level,pos)} 且 {@code instanceof MEPatternBufferPartMachineBase}
 *         才继续（{@code :99-108}；<b>否则整段什么都不做</b>）→
 *       ③写自己的 {@code bufferPos} 字段（{@code :109-111}）→
 *       ④{@code host.addProxy(this)}（{@code :115-117}）—— <b>这就是那一格 +1 的唯一动作</b>
 *         （{@code addProxy} 只做两件事：{@code proxies.add(proxy.getPos())} + {@code proxyMachines.add(proxy)}，
 *          {@code gtlcore_base.txt:152-165}）→
 *       ⑤{@code updateIO()}（{@code :168-172}），而它会对每个控制器调
 *         {@code IRecipeCapabilityMachine.upDate()}（{@code :244-264}）—— <b>即镜像入链顺带把「输入侧接线」重建一次</b>
 *         （这正是 {@code NanochipThreadedRecipeLogic#refreshInputWiringIfStale()} 补的那一步）。
 *       <b>反链</b>：{@code releaseBuffer()} → {@code host.removeProxy(this)}（{@code :197}）；
 *       {@code onMachineRemoved()} 也调它（{@code :395-399}）。</li>
 * </ol>
 *
 * <h3>三、为什么读档后可能是 0（本类存在的理由）</h3>
 * <p>{@code proxies} 不入存档：{@code MEPatternBufferPartMachineBase.saveCustomPersistedData}
 * 只写 {@code "buffer"} 一个键（{@code gtlcore_base.txt:502-521}），{@code loadCustomPersistedData}
 * 也只读它（{@code :523-538}）⇒ 读档后 {@code proxies} 为空。唯一能把它填回来的路径是镜像那一侧的
 * {@code onLoad()}：{@code level.server.tell(TickTask(0, lambda$onLoad$1))}，而 {@code lambda$onLoad$1}
 * 就是 {@code setBuffer(bufferPos)}（{@code _javap_simple_gtlcore_proxy.txt:371-393} + {@code :439-445}）。
 * <p>于是「绑定数量 = 0」只有两种可能：
 * <ol>
 *   <li><b>玩家从来没绑过</b>（该机结构里没有任何镜像部件）⇒ 0 是<b>事实</b>，无缺陷可修。
 *       注意 {@code proxies}/{@code proxyMachines} <b>都不参与</b>「这台机器有没有配方可做」的判定
 *       （判定读的是 {@code mePatternRecipeHandleParts} 与各槽配方缓存，见
 *       {@code NanochipThreadedRecipeLogic#refreshInputWiringIfStale()} 的长注释）⇒ 这一格不该被读成"坏了"；</li>
 *   <li><b>绑过、但读档后没重新入链</b>：镜像的 {@code onLoad} 是「等 0 tick」的延迟任务，它执行时若宿主
 *       方块实体还没就绪（跨区块 / 读档头几秒），{@code setBuffer} 第 ② 步判假并<b>静默返回</b>
 *       （{@code :99-108} 的 {@code ifeq 183}），而 {@code bufferResolved} 已被置真（{@code :87-89}）
 *       ⇒ {@code getBuffer()} 的懒解析（{@code :175-186}）也<b>不会</b>再试一次
 *       ⇒ <b>这条链永久断掉</b>，直到玩家再拿闪存右键一次镜像。
 *       ★ 本类修的就是这一种 —— 而且<b>只修这一种</b>。</li>
 * </ol>
 *
 * <h3>四、为什么本类「不误判、不伪造」（显示器 = 事实）</h3>
 * <ul>
 *   <li><b>触发条件用了两个都必须为真的上游读数</b>：
 *       {@code getBoundProxyPositions()} 非空（上游 NBT 里<b>确实记着</b>镜像位置，{@code gtlcore_base.txt:238-246}）
 *       <b>且</b> {@code getProxies()} 为空（上游此刻解析不到任何镜像）。</li>
 *   <li><b>恢复动作只有一种、且是上游自己的 public 入口</b>：对每个记录位置找 {@code MetaMachine}，
 *       是 {@code MEPatternBufferProxyPartMachine} 就调 {@code setBuffer(host.getPos())} ——
 *       与 gtlcore 自己的复制/粘贴工具 {@code MEPatternBufferPartMachine.pasteFromTag} <b>逐指令同款</b>
 *       （{@code _census/_gto_wildcard/javap_gtlcore/GTL_Buffer_full.txt:1271} 起：{@code releaseProxies()} →
 *       读 {@code "proxies"} long[] → 逐个 {@code BlockPos.of(long)} → {@code MetaMachine.getMachine} →
 *       {@code instanceof MEPatternBufferProxyPartMachine} → {@code setBuffer(machine.getPos())}，
 *       见 {@code :1289-1309}）。<b>我们一个私有 API 都不碰、不反射。</b></li>
 *   <li><b>绝不新建绑定</b>：本类<b>不扫描</b>世界里的镜像方块、也<b>不猜</b>谁该绑谁 ——
 *       记录的坐标集合为空就什么都不做。<b>「0 就是 0」</b>，恢复成功才变 1。</li>
 *   <li><b>会不会误伤</b>：{@code setBuffer} 幂等（先 release 再 add）；本来已经入链的机器
 *       {@code getProxies()} 非空 ⇒ 本类连条件都不成立、一次都不调。异常一律只记 debug。</li>
 * </ul>
 *
 * <h3>五、防震荡与限频（三重 + 诊断独立限频）</h3>
 * <ol>
 *   <li><b>只在坏态</b>：{@code getBoundProxyPositions() 非空 && getProxies() 为空}（见上）；
 *       正常机器每一格都是「非空 + 非空」⇒ 一次都不进动作分支；</li>
 *   <li><b>时间闸</b>：两次尝试至少隔 {@link #RETRY_INTERVAL_TICKS} tick；</li>
 *   <li><b>总量闸 + 停机</b>：整机一生至多 {@link #MAX_ATTEMPTS} 次；
 *       连续 {@link #MAX_CONSECUTIVE_FAILURES} 次「试完仍然 {@code getProxies()} 为空」⇒ 永久停机
 *       （{@code halted}），只留一条 WARN 说明「这一招在这个环境不管用」；</li>
 *   <li><b>诊断行独立限频</b>：只在「断链台数」变化时、或间隔 {@link #DIAG_INTERVAL_TICKS} tick 到点时打，
 *       整机一生至多 {@link #DIAG_BUDGET} 行。</li>
 * </ol>
 *
 * <h3>六、开关与一行回退</h3>
 * <p>总开关 {@link #ENABLED}（默认 {@code true}）。回退：把
 * {@code NanochipAssemblyComplexMachine.tickModuleRefresh()} 里那一句 {@code mirrorBindGuard.tick(this);}
 * 删掉即可 —— 本类及其所有常量/字段都是纯加法，删掉调用点后一行都不会被执行
 * （与 {@code LOAD_PARTS_RECOVERY_ENABLED} 同款纪律：编译期常量、不是 {@code @Persisted}、
 * 不进存档、不改存档格式）。显示那一行（{@code addMirrorBindingDisplay}）用的是
 * {@link #readState} <b>纯只读</b>读数，与开关无关：开关关掉时它仍会照实显示上游真实读数。
 *
 * <h3>七、诚实边界（未取证项）</h3>
 * <p>镜像个体的 {@code bufferPos} 字段<b>是否真的带 {@code @Persisted}</b> 未取证 ✗ —— javap 不带注解，
 * 而本轮沙箱里 {@code pwsh} 全程被拒（{@code sandbox-local windows-acl temp grant materialization failed}）
 * ⇒ 拿不到 {@code javap -v}。同族的第三方实现
 * {@code ETMEPatternBufferProxyPartMachine.kt:98-100} 明写
 * {@code @field:Persisted @field:DescSynced var bufferPos: BlockPos?}
 * （{@code _census/gtet-src/src/main/kotlin/rain/gtetcore/gtet/common/machine/multiblock/part/ae/ETMEPatternBufferProxyPartMachine.kt}），
 * 而 gtlcore 的 {@code onLoad} + {@code lambda$onLoad$1} {@code setBuffer(bufferPos)} 与该实现同构
 * ⇒ <b>强指向「有持久化」</b>。若它其实没持久化，那么上面「三-2」那种断链在本环境里不存在，
 * 本类的触发条件恒不成立 ⇒ <b>它永远不会动手</b>（fail-open，不会作出任何错误动作）。
 */
public final class NanochipMirrorBindGuard {

    /**
     * ★ 总开关（默认 {@code true} = 开）。{@code false} ⇒ {@link #tick} 立刻返回、
     * 连一次遍历都不做、不写任何字段、不打任何日志。
     */
    public static final boolean ENABLED = true;

    /** 两次「重建镜像绑定」尝试之间的最小间隔（tick）；{@value} = 100 tick = 5 秒。 */
    public static final int RETRY_INTERVAL_TICKS = 100;

    /** 整机一生最多尝试几次（纯上限，防「每 5 秒折腾一整局」）。 */
    public static final int MAX_ATTEMPTS = 8;

    /** 连续「试完仍然一个镜像都没入链」多少次就永久停机（防震荡）。 */
    public static final int MAX_CONSECUTIVE_FAILURES = 3;

    /** 一条诊断行里最多列几个结构里的样板总成（避免大机器刷长行）。 */
    private static final int DIAG_MAX_BUFFERS = 4;

    /** 诊断行的最长间隔（tick）；{@value} = 600 tick = 30 秒。 */
    private static final int DIAG_INTERVAL_TICKS = 600;

    /** 诊断行总量上限（整机一生）；与 gtlmodular 既有的「诊断预算」同款纪律。 */
    private static final int DIAG_BUDGET = 16;

    /** 停机时那条 WARN 只打一次。 */
    private boolean warnedHalt;

    /** 见 {@link #MAX_ATTEMPTS}。 */
    private int attempts;

    /** 连续失败次数（见 {@link #MAX_CONSECUTIVE_FAILURES}）。 */
    private int consecutiveFailures;

    /** 永久停机位（达到 {@link #MAX_CONSECUTIVE_FAILURES} 后置真，此后不再尝试）。 */
    private boolean halted;

    /** 时间闸（见 {@link #RETRY_INTERVAL_TICKS}）。 */
    private int cooldown;

    /** 最近一次尝试的 gameTime（{@code -1} = 还没试过）；仅诊断。 */
    private long lastAttemptTick = -1L;

    /** 最近一次尝试的结果说明（{@code null} = 还没试过）；仅诊断。 */
    private String lastOutcome;

    // ── 诊断行限频（见类注释 §五-4） ──

    /** 见 {@link #DIAG_INTERVAL_TICKS}。 */
    private int diagCooldown;

    /** 见 {@link #DIAG_BUDGET}。 */
    private int diagBudget = DIAG_BUDGET;

    /** 上一次打出去的「台数 / 记录 / 已入链 / 断链台数」键（同一份内容绝不重复打）。 */
    private String diagLast;

    private NanochipMirrorBindGuard() {
    }

    /** 造一个（{@code NanochipAssemblyComplexMachine} 持有一个实例；本类无静态可变状态）。 */
    public static NanochipMirrorBindGuard create() {
        return new NanochipMirrorBindGuard();
    }

    /**
     * 一步：读状态 → 若处于「已记录坐标却解析不到镜像」的断链态、且三道闸都放行，就把绑定重建一次。
     *
     * <p><b>只在服务端 tick 上调</b>（调用点 {@code NanochipAssemblyComplexMachine.tickModuleRefresh()}，
     * 那是 {@code subscribeServerTick} 的回调）⇒ 不存在客户端线程碰世界/方块实体的问题。
     * 任何一步的 {@code RuntimeException} / {@code LinkageError}（第三方 API 缺类）都只记 debug，
     * <b>绝不影响</b>调用方（结构 / 派活 / 显示一条都不受影响）。
     */
    public void tick(NanochipAssemblyComplexMachine host) {
        if (!ENABLED) {
            return;
        }
        if (cooldown > 0) {
            cooldown--;
        }
        try {
            State now = readState(host);
            if (now == null) {
                return; // 未成形 / 零件表空 / 世界为空 ⇒ 什么都不做，也不按"0"下结论
            }
            diagnose(host, now);
            if (now.lostCount() <= 0) {
                // ★ 「上游已经入链」或「从来没绑过」两种情形都在这里收工：
                //   前者 `已入链>0`、后者 `记录=0` —— 本类**不**新建绑定（见类注释 §四）
                consecutiveFailures = 0;
                return;
            }
            if (halted || attempts >= MAX_ATTEMPTS || cooldown > 0 || now.level == null) {
                return; // 总量闸 / 时间闸
            }
            cooldown = RETRY_INTERVAL_TICKS;
            attempts++;
            lastAttemptTick = now.level.getGameTime();
            int sent = 0;
            for (LostRecord lost : now.lostRecords()) {
                for (BlockPos pos : lost.positions()) {
                    sent += rebind(lost.buffer(), now.level, pos);
                }
            }
            // 复读一次：上游 setBuffer / addProxy 是同步的 ⇒「没生效」是真的没生效，不是"还没刷新"
            State after = readState(host);
            boolean ok = after != null && after.lostCount() <= 0 && after.proxiesTotal > 0;
            int proxiesNow = after == null ? now.proxiesTotal : after.proxiesTotal;
            lastOutcome = "请求重建 " + sent + " 处 → 已入链 " + proxiesNow + " 条";
            if (ok) {
                consecutiveFailures = 0;
                NanochipModContext.logger().info("[gtlmodular] NAC 镜像绑定恢复 @{}：{}（上一刻 记录={} 已入链=0）"
                                + "—— 依据 _census/镜像绑定为零_修复记录.md §③-2 / §⑥",
                        host.getPos(), lastOutcome, now.recordedTotal);
            } else {
                consecutiveFailures++;
                NanochipModContext.logger().info("[gtlmodular] NAC 镜像绑定恢复 @{}：{}（记录={}，第 {} 次尝试，"
                                + "连续无效 {} / {}）", host.getPos(), lastOutcome, now.recordedTotal,
                        attempts, consecutiveFailures, MAX_CONSECUTIVE_FAILURES);
                if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                    halt(host, "连续 " + consecutiveFailures + " 次「已记录坐标却仍解析不到镜像部件」");
                }
            }
        } catch (RuntimeException | LinkageError e) {
            // 第三方 API 缺类/实现都不许把机器搞崩（fail-open：保持原样、不补偿、不影响派活）
            NanochipModContext.logger().debug("[gtlmodular] NAC 镜像绑定读取/恢复失败（保持原样，不影响派活）：{}",
                    e.toString());
        }
    }

    /**
     * 读「镜像绑定」的全部读数（<b>纯只读</b>：不写任何东西、不花任何重建、不调上游的 setter）。
     *
     * <p>这是<b>显示层与恢复动作共用</b>的唯一读数口 ⇒ 不可能出现「面板说已绑定、上游还是 0」这种打架。
     * 它只读三个<b>上游自己就在客户端/服务端两侧都读</b>的东西：
     * {@code WorkableMultiblockMachine.isFormed() / getParts()}、{@code getBoundProxyPositions()}、
     * {@code getProxies()}（后两个正是 gtlcore 自己那个 Jade provider 读的同一对）——
     * 所以服务端面板与客户端面板显示的是同一份事实。
     *
     * @return 状态快照；{@code null} = 这一刻不该读/读不到（未成形、零件表为空、世界为空）
     */
    public static State readState(NanochipAssemblyComplexMachine host) {
        if (!host.isFormed()) {
            return null;
        }
        Level level = host.getLevel();
        if (level == null) {
            return null;
        }
        // 候选样板总成：① 结构零件表里的（服务端恒有、客户端通常也有）；
        //              ② 兜底 = 主机自己（万一客户端读不到零件表，主机若是样板总成仍读得到，
        //                 与上游 Jade 在客户端读的是同一份数据）⇒ 宁可靠自己，不假装"未读取"。
        List<MEPatternBufferPartMachineBase> buffers = new ArrayList<>(2);
        List<IMultiPart> parts = host.getParts();
        if (parts != null) {
            for (IMultiPart part : parts) {
                if (part instanceof MEPatternBufferPartMachineBase buffer && !buffers.contains(buffer)) {
                    buffers.add(buffer);
                }
            }
        }
        // ★ 0.3.127 修正：host 是多方块主机（NanochipAssemblyComplexMachine），
        //   与 MEPatternBufferPartMachineBase 是并列类型 ⇒ Java 直接判"无法转换"编译错误。
        //   主机自己不可能同时是样板仓部件 ⇒ 该分支本就不可能成立，删除（行为不变）。
        if (buffers.isEmpty()) {
            return null; // ★ 零件表没回来 + 主机自己也不是总成 ⇒ 真的"没读到"，不是 0
        }
        State st = new State(level, buffers.size());
        for (MEPatternBufferPartMachineBase buffer : buffers) {
            int recorded;
            int proxies;
            List<BlockPos> positions = null;
            try {
                positions = buffer.getBoundProxyPositions();
                recorded = positions == null ? 0 : positions.size();
            } catch (RuntimeException e) {
                recorded = -1; // -1 = 没读到（绝不当成 0）
            }
            try {
                Set<MEPatternBufferProxyPartMachine> connected = buffer.getProxies();
                proxies = connected == null ? -1 : connected.size();
            } catch (RuntimeException e) {
                proxies = -1;
            }
            st.recordedTotal += Math.max(0, recorded);
            st.proxiesTotal += Math.max(0, proxies);
            st.readFailure |= recorded < 0 || proxies < 0;
            if (st.bufferNotes.size() < DIAG_MAX_BUFFERS) {
                st.bufferNotes.add(buffer.getPos().toShortString()
                        + "[记录=" + (recorded < 0 ? "未读到" : recorded)
                        + ",已入链=" + (proxies < 0 ? "未读到" : proxies) + ']');
            }
            if (recorded > 0 && proxies == 0 && positions != null) {
                st.lostRecords.add(new LostRecord(buffer, positions));
                st.lostCount++;
            }
        }
        return st;
    }

    /**
     * 按上游正规入口把「一个记录在案的镜像位置」重新接上：那个位置上确实是
     * {@code MEPatternBufferProxyPartMachine} 就让它 {@code setBuffer(该样板总成的坐标)}
     * （= 上游 {@code pasteFromTag} 的同一套动作，见类注释 §四的逐指令出处）。
     *
     * @param buffer 这条记录的主人（样板总成；{@code setBuffer} 要的正是它的坐标）
     * @return 真正发出过 {@code setBuffer} 的次数（1 或 0；是否生效由 {@link #tick} 复读判定）
     */
    private static int rebind(MEPatternBufferPartMachineBase buffer, Level level, BlockPos pos) {
        if (pos == null || buffer == null) {
            return 0;
        }
        try {
            MetaMachine machine = MetaMachine.getMachine(level, pos);
            if (machine instanceof MEPatternBufferProxyPartMachine proxy) {
                proxy.setBuffer(buffer.getPos()); // ★ 上游 public 入口，幂等
                return 1;
            }
        } catch (RuntimeException | LinkageError e) {
            NanochipModContext.logger().debug("[gtlmodular] NAC 镜像绑定恢复：位置 {} 跳过（{}）", pos, e.toString());
        }
        return 0;
    }

    /** 到达阈值 ⇒ 永久停机 + 唯一一条 WARN（此后 {@link #tick} 只读不写）。 */
    private void halt(NanochipAssemblyComplexMachine host, String reason) {
        halted = true;
        if (warnedHalt) {
            return;
        }
        warnedHalt = true;
        NanochipModContext.logger().warn("[gtlmodular] NAC 镜像绑定恢复 @{}：已停机不再尝试（{}；累计 {} 次尝试）。"
                        + "这一招在这个环境不管用 —— 请用闪存重新绑定镜像部件"
                        + "（或先确认镜像部件本身还在、且与样板总成互相在结构里）。"
                        + "依据 _census/镜像绑定为零_修复记录.md §③-2。",
                host.getPos(), reason, attempts);
    }

    /**
     * ★ 诊断行（限频见类注释 §五-4）：<b>把「绑定的各分量」打一行</b> ——
     * ① 上游镜像表大小（{@code getBoundProxyPositions().size()} = 记录在案的镜像位置数）、
     * ② 总成侧自称状态（{@code getProxies().size()} = 这一刻真的入链的镜像部件数，也就是 Jade 那一格的数）、
     * ③ 结构里的样板总成台数与逐台明细、④ 断链台数、⑤ 上次尝试时刻与结果、⑥ 是否已停机/累计尝试次数。
     *
     * <p>只在「断链台数 / 记录 / 已入链」这份键变化时、或到点时才打 ⇒ 稳态下最多一条。
     */
    private void diagnose(NanochipAssemblyComplexMachine host, State st) {
        if (!NanochipModContext.logger().isInfoEnabled()) {
            return;
        }
        String key = st.bufferCount + "/" + st.recordedTotal + "/" + st.proxiesTotal + "/"
                + st.lostCount + (st.readFailure ? "/R" : "/-");
        if (diagCooldown > 0) {
            diagCooldown--;
        }
        boolean changed = !key.equals(diagLast);
        if ((changed || diagCooldown <= 0) && diagBudget > 0) {
            diagBudget--;
            NanochipModContext.logger().info("[gtlmodular] NAC 镜像绑定诊断 @{}：{}", host.getPos(), describe(host, st));
        }
        diagLast = key;
        diagCooldown = DIAG_INTERVAL_TICKS;
    }

    /** 一行诊断文本（分量见 {@link #diagnose}；纯只读，不改任何状态）。 */
    public String describe(NanochipAssemblyComplexMachine host, State st) {
        if (st == null) {
            return "镜像绑定=未读取（未成形 / 零件表为空） @" + host.getPos();
        }
        StringBuilder sb = new StringBuilder(180);
        sb.append("样板总成=").append(st.bufferCount).append(" 台")
                .append("，上游镜像表：记录=").append(st.recordedTotal)
                .append(" / 已入链=").append(st.proxiesTotal)
                .append(st.readFailure ? "（有格未读到）" : "")
                .append("，断链台数=").append(st.lostCount)
                .append("，明细=").append(st.bufferNotes)
                .append("，上次尝试=").append(lastAttemptTick < 0 ? "无" : String.valueOf(lastAttemptTick))
                .append("（").append(lastOutcome == null ? "尚未尝试" : lastOutcome).append("）")
                .append("，尝试=").append(attempts).append('/').append(MAX_ATTEMPTS)
                .append(halted ? "（已停机）" : "")
                .append("，gameTime=").append(st.levelForDiag() == null ? -1L : st.levelForDiag().getGameTime());
        return sb.toString();
    }

    /** 累计尝试次数（仅显示/诊断用，不参与任何判定）。 */
    public int attemptsForDiag() {
        return attempts;
    }

    /** 是否已经永久停机（仅显示/诊断用，不参与任何判定）。 */
    public boolean haltedForDiag() {
        return halted;
    }

    /**
     * 「镜像绑定」这一格的状态快照（<b>纯值对象</b>；只读引用 + 基本类型，构造完就不再改）。
     */
    public static final class State {

        /** 读这一份快照时的世界（仅给诊断读 gameTime；不缓存方块实体）。 */
        private final Level level;

        /** 结构里读到的样板总成台数。 */
        private int bufferCount;

        /** 全部样板总成「记录在案的镜像位置」之和（= 上游 {@code proxies.size()}）。 */
        private int recordedTotal;

        /** 全部样板总成「这一刻真的入链的镜像部件」之和（= 上游 {@code proxyMachines.size()}，即那一格显示的数）。 */
        private int proxiesTotal;

        /** 有没有哪一格没读到（true ⇒ 这一份快照里的 0 不可信、显示层要照实说）。 */
        private boolean readFailure;

        /** 断链台数（记录 > 0 且已入链 == 0 的样板总成台数）。 */
        private int lostCount;

        /** 断链的每一台「样板总成 + 它记录在案的镜像位置」（仅用于恢复动作与诊断）。 */
        private final List<LostRecord> lostRecords = new ArrayList<>(2);

        /** 逐台的诊断短串（最多 {@link #DIAG_MAX_BUFFERS} 条）。 */
        private final List<String> bufferNotes = new ArrayList<>(DIAG_MAX_BUFFERS);

        private State(Level level, int bufferCount) {
            this.level = level;
            this.bufferCount = bufferCount;
        }

        /** {@code true} = 至少有一台处在「已记录镜像坐标、却解析不到镜像部件」的断链态。 */
        public boolean lost() {
            return lostCount > 0;
        }

        /** 断链台数。 */
        public int lostCount() {
            return lostCount;
        }

        /** 断链的那些「样板总成 + 记录位置」（只读；恢复动作用）。 */
        public List<LostRecord> lostRecords() {
            return Collections.unmodifiableList(lostRecords);
        }

        /** 结构里的样板总成台数。 */
        public int bufferCount() {
            return bufferCount;
        }

        /** 记录在案的镜像位置总数（上游 {@code proxies.size()} 之和）。 */
        public int recordedTotal() {
            return recordedTotal;
        }

        /** 这一刻真的入链的镜像部件总数（上游 {@code proxyMachines.size()} 之和 = 那一格显示的数）。 */
        public int proxiesTotal() {
            return proxiesTotal;
        }

        /** 有格子没读到（显示层要照实说「未读到」）。 */
        public boolean readFailure() {
            return readFailure;
        }

        /** 逐台诊断短串（只读；调用方不要改）。 */
        public List<String> bufferNotes() {
            return Collections.unmodifiableList(bufferNotes);
        }

        private Level levelForDiag() {
            return level;
        }
    }

    /**
     * 一条断链记录：<b>哪台样板总成</b> + <b>它记录在案的那些镜像位置</b>（构造后不再改）。
     */
    public record LostRecord(MEPatternBufferPartMachineBase buffer, List<BlockPos> positions) {

        /** 防御性拷贝 ⇒ 快照发布后不可能被上游集合的后续变动改到。 */
        public LostRecord {
            positions = positions == null ? List.of() : List.copyOf(positions);
        }
    }
}
