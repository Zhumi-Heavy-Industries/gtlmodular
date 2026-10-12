package com.gtlmodular.client.jade;

/**
 * ★★★ 2026-10-28【独立跨配方线程 · Jade 显示】★★★
 * Jade 显示这一环<b>跨类共用</b>的常量：uid 路径、serverData 键、显示上限。
 *
 * <h2>为什么单独开一个「零依赖常量类」（不是洁癖）</h2>
 * <p>这三个常量要被<b>两边</b>读到：
 * <ol>
 *   <li>{@link NanochipJadePlugin} / {@link NanochipThreadedJadeProvider}（提供方与显示方）；</li>
 *   <li>{@code com.gtlmodular.mixin.gtceu.MixinWorkableProgressBar}（<b>混入类</b>）——
 *       它要按 serverData 里的标记判定「这是不是我们的机器」，所以必须知道键名。</li>
 * </ol>
 *
 * <p>如果键名直接写在 provider 的 {@code public static final} 上，混入类引用它就等于在
 * <b>混入类加载那一刻</b>拉起 {@code NanochipThreadedJadeProvider}（连带 Jade API 与 gtladditions
 * 机器族）—— 混入类在<b>配置解析阶段</b>就会被加载，这个加载时机不该塞进我们的机器代码。
 * 本类只有 {@code String} 字面量与一个私有构造器，<b>不 import 任何其它类</b>
 * （命名空间保留为字面量，以免常量读取触发模组入口初始化，见 {@link #MODID}）
 * ⇒ 引用它零副作用。
 *
 * <h2>判据（「只作用于我们的机器」怎么保证）</h2>
 * <p>{@link #KEY_THREAD_LIMIT} 写在 Jade <b>serverData 的根</b>上，而它<b>只由本模组的</b>
 * provider 写入 ⇒ 「这个键存在」本身就是「这块方块是我们的机器且在跑独立线程」的充分证据。
 * 别的模组（GTM / GCYM / gtlcore / gtladditions / enhancedcore / 参考实现 GTET）永远不会写这个键
 * （键名含本模组命名空间），所以判据天然限定在我们自己身上。
 *
 * @see NanochipThreadedJadeProvider
 */
public final class NanochipJadeKeys {

    /**
     * 保留旧命名空间常量，避免拆分产物时改变既有数据和文本标识。
     */
    public static final String MODID = "gtlmodular";

    /** Jade provider 的 uid 路径（uid = {@code gtlmodular:threaded_recipe_logic}）。 */
    public static final String UID_PATH = "threaded_recipe_logic";

    /**
     * Jade 方块提示（准星提示）里最多画几条<b>进度条</b>（= 几条线程明细）—— 悬浮框跟着准星走，
     * 行数一多会盖住半个屏幕。
     *
     * <p>★ 2026-11-02【上限 = 3，用户原话】：<blockquote>「最多也就同时只显示三个进度条吧」</blockquote>
     * ⇒ 玩家侧可见的进度条上限就是 <b>3 条</b>（按进度降序取最快的 3 条，同进度按稳定编号升序，
     * 排序在服务端 {@code NanochipThreadedRecipeLogic#snapshot()} 里做一次）。
     * 超出的部分<b>不假装不存在</b>：由 {@link NanochipJadeThreadRows}
     * 用一行「……还有 M 条线程未列出」如实交代（M = 在用总数 − 已画条数，中英各一条文案）。
     *
     * <p>★★ 2026-11-17【6 → 3：<b>退回队长原设定</b>】★★ 队长原话（逐字）：
     * <blockquote>「而且线程怎么增加到5个了？之前不是设定最多展示三个吗」</blockquote>
     * 上一轮为了让 6 条线程的产物都显示，把这个常量由 3 提到 6，而它<b>当时被两节共用</b>
     * ⇒「逐条线程行」（进度条那一节）也变成了 6 条 = 队长看到的「线程增加到 5/6 个」。
     * 本轮改回 <b>3</b>，并把两节<b>拆开</b>：「各线程产物」那一节改用独立的 {@link #OUTPUT_LINES}。
     *
     * <p>⚠⚠ <b>下一轮请勿再为了「多显示产物」抬高本常量</b>：产物条数<b>不</b>由它决定，
     * 抬高它只会让进度条变多（= 队长明确否掉的观感）。要改产物条数请改 {@link #OUTPUT_LINES}。
     *
     * <p>机器面板另有一份 {@code NanochipThreadedRecipeLogic.DISPLAY_LINES = 8}（<b>故意不同</b>：
     * 面板是固定窗口、Jade 是跟着准星飘的悬浮框，两者可见面积不同；数字口径仍同一函数）。
     * 两边的「在用 k 条」「同时处理 R 次」都按<b>全部</b>批次算，与这里画几条无关。
     *
     * <p>⚠ 本常量<b>不再</b>决定 serverData 里明细列表的截断长度（那是 {@link #OUTPUT_LINES}）：
     * 列表必须够长，产物节才拿得到第 4~6 条的数据（理由见 {@link #OUTPUT_LINES}）。
     */
    public static final int DISPLAY_LINES = 3;

    /**
     * ★★ 2026-11-17【新增：<b>「各线程产物」那一节的独立上限</b>（与进度条那一节彻底脱钩）】★★
     *
     * <h4>队长原话（两句<b>必须同时成立</b>）</h4>
     * <blockquote>「你看到的截图里面也有，同时跑 6 个不同的配方，但是那边能看到的产物就只显示一种，
     * <b>得让 6 个产物都显示呢</b>」／「<b>每条线程的产物都得看到</b>」</blockquote>
     * <blockquote>「而且线程怎么增加到5个了？之前不是设定最多展示三个吗」</blockquote>
     * ⇒ <b>进度条那节 = 最多 3 条</b>（{@link #DISPLAY_LINES}），
     * <b>产物那节 = 每条在跑的线程的产物都列出来</b>（本常量，不再被 3 条限制）。
     *
     * <h4>为什么它<b>必须</b>独立（三条理由，逐条）</h4>
     * <ol>
     *   <li><b>两节的诉求相反</b>：进度条越多越挡屏幕（队长要 ≤3 条）；产物是「谁在产什么」的清单，
     *       漏一条就是漏一条信息（队长要「每条都得看到」）⇒ 共用一个数必然委屈其中一边，
     *       上一轮的「6」就是这么来的，也正是队长本轮否掉的那一处；</li>
     *   <li><b>它同时是 serverData 明细列表的截断长度</b>
     *       （{@code NanochipThreadedJadeProvider#snapshotThreads}）：列表被截到 3 条时，
     *       第 4~6 条线程的产物数据<b>根本不在客户端手上</b> ⇒ 产物节就算不设上限也列不出来。
     *       <b>所以列表截断必须按本常量（两个里较大的那个）算</b>，而画几条是两节各自的循环上限；</li>
     *   <li><b>载荷纪律仍需一个上限</b>：线程上限可到 256（{@code MAX_CROSS_RECIPE_THREADS = 1 &lt;&lt; 8}），
     *       而这个 NBT 每次准星指过来都要走一遍网络通道 ⇒ 不能真的「有多少列多少」。
     *       取 <b>6</b> = 队长实测场景（同时跑 6 个不同配方）；超过 6 条时由既有那一行
     *       「还有 M 条未列出」（{@link NanochipJadeThreadRows#LANG_MORE_UNLISTED}）如实交代
     *       （M = 在用 − 已列，<b>绝不假装不存在</b>）。</li>
     * </ol>
     *
     * <h4>将来要改就只改这一个数</h4>
     * <p>队长机器常态 &gt;6 条线程时，<b>只改本常量</b>（进度条那节的 3 不受影响）。
     * 若要整节去掉产物，删掉 {@code NanochipThreadedJadeProvider#appendTooltip} 里那一句
     * {@code appendThreadOutputs(tooltip, data);} 即可（见那里的注释）。
     */
    public static final int OUTPUT_LINES = 6;

    /**
     * 线程数上限的 serverData 根键 —— <b>兼作「这是本模组机器」的标记</b>。
     *
     * <p>写在根上（不是某个子 tag）是 Jade 的契约：{@code IServerDataProvider#appendServerData}
     * 拿到的 {@code CompoundTag} 就是 {@code BlockAccessor#getServerData()}，
     * 两边同一个对象（证据：{@code _javap_jade_provider.txt:25-29} 的
     * {@code BlockAccessor.getServerData()} 与 {@code :52-54} 的写根 tag）。
     *
     * <p><b>无条件的</b>写（即使上限 ≤ 1 也写）：客户端的判定必须靠「键存在」而不是「值 &gt; 1」，
     * 否则会把别的模组的多线程机器（例如 gtladditions 自己的跨配方线程机器，它们也有
     * {@code getMultipleThreads()}）误判成我们自己的机器 ⇒ 连人家的进度条一起取消。
     */
    public static final String KEY_THREAD_LIMIT = "gtlmodular_jade_thread_limit";

    /** 「正在跑的线程条数」的 serverData 根键。 */
    public static final String KEY_THREAD_RUNNING = "gtlmodular_jade_thread_running";

    /** 「整机同时处理多少次配方运行（各线程之和）」的 serverData 根键。 */
    public static final String KEY_THREAD_RUNS = "gtlmodular_jade_thread_runs";

    /** 线程明细列表的 serverData 根键（每项一个 {@code CompoundTag}）。 */
    public static final String KEY_THREAD_LIST = "gtlmodular_jade_thread_list";

    /** 线程明细里「槽位」的键。 */
    public static final String KEY_SLOT = "slot";

    /** 线程明细里「进度（tick）」的键。 */
    public static final String KEY_PROGRESS = "progress";

    /** 线程明细里「总时长（tick）」的键。 */
    public static final String KEY_DURATION = "duration";

    /** 线程明细里「本条自己那份并行」的键。 */
    public static final String KEY_PARALLELS = "parallels";

    /**
     * ★ 2026-11-02【沿用原生进度条】「原生条代表的是哪一条线程」的键（= 该线程的稳定编号）。
     *
     * <p>服务端 step ④ 把<b>批次表第 0 条</b>的进度/时长镜像进机器基类的单进度字段
     * （{@code NanochipThreadedRecipeLogic#mirrorBatchZeroToBaseFields}），而 GTCEu 原生那条进度条读的
     * 正是这两个字段 ⇒ <b>原生条 = 批次表第 0 条</b>。客户端据此把「原生条已经代表的那一条」
     * 从追加列表里排除（见 {@code NanochipJadeThreadRows}），同一条线程不会被画两次。
     *
     * <p>{@code -1} = 这一刻没有批次（服务端照写，客户端自然不做排除）。
     */
    public static final String KEY_MIRRORED_SLOT = "gtlmodular_jade_mirrored_slot";

    /** ★ 见 {@link #KEY_MIRRORED_SLOT}：原生条那一条的并行（补说明行用，{@code long}）。 */
    public static final String KEY_MIRRORED_PARALLELS = "gtlmodular_jade_mirrored_parallels";

    /*
     * ★ 2026-11-02【本轮删除】原来的常量 {@code LANG_LATENCY_NOTE} =
     * {@code "gtlmodular.nanochip.threads.latency_note"} 连同它在两份 lang 里的文案<b>整条删除</b>
     * （用户明确要求：不需要那行「延迟说明」）。
     * 它唯一的写入点是 {@code NanochipThreadedJadeProvider#appendTooltip}，本轮同步删掉；
     * 「延迟：… µs」那一行本身由上游 gtlcore 的 {@code tick_time_provider} 下发，
     * 与本次改动无关（我们只是不再解释它）。
     * 键与文案一起删 ⇒ 不会留下「有键没人用」的孤儿条目。
     */

    /** 工具类：不实例化。 */
    private NanochipJadeKeys() {}
}
