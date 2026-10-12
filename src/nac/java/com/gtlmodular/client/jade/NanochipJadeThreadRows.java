package com.gtlmodular.client.jade;

import com.gregtechceu.gtceu.utils.FormattingUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import snownee.jade.api.ITooltip;
import snownee.jade.api.ui.BoxStyle;

import java.util.Locale;

/**
 * ★★★ 2026-11-02【独立线程 · Jade 显示：<b>GTO 标准形态 —— 每条线程「标签一行 + 整行宽进度条一行」</b>】★★★
 *
 * <h2>目标形态（用户给的截图，逐字照抄）</h2>
 * <pre>
 * 木化工厂
 * 硬度: 3.5; 爆炸抗性: 3.5
 * 线程0:
 * [======== 2/4 s ========]        ← 整行宽的进度条，文字压在条上
 * 线程1:
 * [======== 2/4 s ========]
 * 线程2:
 * [======== 1/4 s ========]
 * ME 网络存储数量: 0 [可合成]
 * 同时处理至多 16,384 个配方
 * 配方输出: …
 * 结构已成型
 * 无需维护
 * </pre>
 *
 * <h2>我们的对应形态（本轮起）</h2>
 * <pre>
 * 独立线程：上限 256 条 · 在用 3 条
 * 线程0:
 * [======== 2/4 s ========]
 * 并行 128
 * 线程1:
 * [======== 2/4 s ========]
 * 并行 128
 * 线程2:
 * [======== 1/4 s ========]
 * 并行 64
 * 同时处理 R 次配方运行（各线程之和）
 * 相当于 3 台机器并行（在用 3 条 × 各条并行之和 = R 次配方运行）
 * 还有 M 条未列出                          ← 只有「在用 > 3」时才出现（M = 在用 − 已画）
 * </pre>
 * <ul>
 *   <li><b>标签行</b>：{@link #LANG_THREAD_LABEL}（{@code 线程0:} / {@code Thread 0:}），
 *       与 GTO 的 {@code 线程0:} 一字同形（<b>不再带 {@code #}</b>）；</li>
 *   <li><b>条行</b>：条内文字 = {@link #LANG_BAR} 的 {@code 2/4 s}（GTO 同款：<b>秒</b>），
 *       填充比 = {@code clamp(progress / duration, 0, 1)}（与 GTO 那条同一个口径）；</li>
 *   <li><b>并行行</b>：{@link #LANG_PARALLEL}（{@code 并行 128}）——
 *       并行信息<b>从条内移到条外的单独一行</b>（移出的理由见 {@link #appendThreadBar}）；</li>
 *   <li><b>条宽</b>：<b>不需要我们传宽度</b> —— Jade 的 {@code ProgressElement} 用
 *       「该行剩余宽度」（{@code maxX - x}）画条 ⇒ 它天生 = 提示框内容宽度 = 与其它文字行同宽。
 *       我们要做的就是<b>不让条内文字把提示框撑宽</b>：条内只剩 {@code 2/4 s}。</li>
 * </ul>
 *
 * <h2>「有几条线程就画几条」怎么保证</h2>
 * 服务端把<b>每一条在跑的批次</b>各写一个 NBT 子项进 {@code KEY_THREAD_LIST}
 * （{@code NanochipThreadedJadeProvider#appendServerData} 遍历 {@code logic.snapshot()}，
 * 排序=进度降序/同进度按稳定编号升序；<b>列表</b>截断到 {@link NanochipJadeKeys#OUTPUT_LINES} = <b>6</b> 条
 * —— 列表要够长，因为「各线程产物」那一节要列到 6 条；<b>本类（进度条那一节）只画前
 * {@link NanochipJadeKeys#DISPLAY_LINES} = 3 条</b>），
 * 客户端 {@link #appendAllRows} <b>列表里有一条就画一组「标签 + 条」</b> ——
 * 所以「几条线程几组条」是<b>列表长度与元素个数一一对应</b>，不是靠某条特判。
 * 在用超过 3 条的用一行「还有 M 条未列出」如实交代（M = 在用总数 − 已画条数；M=0 时本行不出现）。
 * <p>★ 2026-11-17【两个上限<b>不许再合并</b>（上一轮刚踩过）】：
 * 「逐条线程行」= {@link NanochipJadeKeys#DISPLAY_LINES} = <b>3</b>（队长原设定：
 * 「最多也就同时只显示三个进度条吧」／本轮「之前不是设定最多展示三个吗」）；
 * 「各线程产物」= {@link NanochipJadeKeys#OUTPUT_LINES} = <b>6</b>（队长要「每条线程的产物都得看到」）。
 * 上一轮把两节共用的那个常量由 3 提到 6 ⇒ 进度条也跟着变 6 条（队长当场否掉）。
 * <b>要改产物条数请改 OUTPUT_LINES，绝不要动这里读的 DISPLAY_LINES</b>。
 * <p>★ <b>标签编号口径（GTO 对齐）</b>：画出来的第 1 组永远叫 {@code 线程0}、
 * 第 2 组 {@code 线程1}…（= <b>本次列表内的下标</b>，与内部那个全局递增的
 * {@code seq} / {@code KEY_SLOT} <b>解耦</b>）—— 逐字照 GTO 截图的 {@code 线程0/1/2}。
 *
 * <h2>数字口径（显示即事实，不许夸大）</h2>
 * <ul>
 *   <li>「在用 k 条」= 服务端数出来的<b>真实在跑批次数</b>；「上限 N 条」= 本机线程上限
 *       （{@code MAX_CROSS_RECIPE_THREADS = 1 << 8 = 256}，用户截图里的「上限」也是这个口径）；</li>
 *   <li>「同时处理 R 次」= 各线程<b>自己那份并行之和</b>
 *       （{@code IGTRecipe.getRealParallels}，逐条之和恒等于 R）；</li>
 *   <li>「相当于 k 台机器并行」的 {@code k} <b>只取「在用」</b>：{@code k = 在用条数}（闲置的不算）。
 *       这就是用户口径「有 10 个独立线程就相当于 10 台机器在工作，各条还有并行就再乘上去」的落地写法，
 *       且 <b>R = 各线程自己的并行之和</b> ⇒ 三者自洽（实现见 {@link #appendEquivalenceLine}）。</li>
 * </ul>
 *
 * <h2>服务端/客户端边界</h2>
 * 本类只在<b>客户端</b>被调用（Jade 的准星提示是纯客户端渲染）：
 * <ul>
 *   <li>{@link NanochipThreadedJadeProvider#appendTooltip}（客户端）—— 画总览，并在
 *       {@link #OUR_OWN_BARS} 或「不用原生条」的形态下画全部条（<b>默认形态就是它</b>）；</li>
 *   <li>{@code com.gtlmodular.mixin.gtceu.MixinWorkableProgressBar}（客户端混入）——
 *       <b>默认</b>：{@code @At("HEAD")} 取消原生条；<b>回退形态</b>（{@code OUR_OWN_BARS = false}）：
 *       在原生 provider 返回处（{@code @At("TAIL")}）调用 {@link #appendAfterNativeBar} 逐行追加。</li>
 * </ul>
 * 两条路径共用本类 ⇒ 样式、文案键、截断口径<b>只有一处实现</b>（不会出现「两条路径外观不一样」）。
 *
 * <h2>为什么抽成普通类（不是写在混入里）</h2>
 * 混入类必须保持极薄（我们有过「混入里塞业务代码」的教训）：本类<b>没有 static 初始化块、
 * 没有非 private 静态可变字段、不引用任何机器/配方逻辑类</b>，只碰 Jade 的公共 API 与 MC 的
 * {@code Component} / {@code CompoundTag} ⇒ 织入链上加载它零副作用。
 *
 * <h2>一行回退</h2>
 * <ul>
 *   <li>{@link #OUR_OWN_BARS} 置 {@code false} ⇒ 逐字回到「原生条 + 在它下面追加其余线程」
 *       （那条路径的代码<b>一行没删</b>，混入里两个注入点都在，只是按开关分流）；</li>
 *   <li>{@link #NATIVE_BAR_FOR_FIRST_THREAD} 置 {@code false}（且 {@link #OUR_OWN_BARS} = false）⇒
 *       provider 自己画全部条、原生条也保留（旧口径，条会多一条）。</li>
 * </ul>
 */
public final class NanochipJadeThreadRows {

    /**
     * ★ <b>形态总开关（本轮默认 = 自绘）</b>：{@code true} = <b>原生条取消、我们自己逐条线程画</b>
     * （{@link NanochipThreadedJadeProvider#appendTooltip} → {@link #appendAllRows}）；
     * {@code false} = 逐字回到「沿用 GTCEu 原生条当第 0 条 + 混入在它下面追加其余」。
     *
     * <p><b>为什么默认自绘</b>：原生条读的是 {@code RecipeLogic} 基类的<b>单</b>进度字段
     * ⇒ 天生只能反映一条线程；而「原生 + 追加」形态下，多条条<b>完全依赖混入的注入生效</b>
     * （{@code require = 0} ⇒ 失败是静默的）⇒ 一旦那条链断掉，玩家「只会显示一个进度条」。
     * 自绘把清单里的条直接交给<b>我们自己的 provider</b> 画，混入只负责「取消原生条」这件可退让的事
     * ⇒ 任何显示故障都不会再退化成「只剩一条」。
     */
    public static final boolean OUR_OWN_BARS = true;

    /**
     * ★ <b>回退形态的开关</b>（只在 {@link #OUR_OWN_BARS} = {@code false} 时有意义）：
     * {@code true}（默认）= 沿用 GTCEu 原生进度条当第 0 条线程的条，其余线程由混入在它后面逐行追加；
     * {@code false} = provider 自己画全部线程的条（原生条会同时存在）。
     *
     * <p>混入与 provider <b>读同一组常量</b> ⇒ 不可能出现「一边追加、一边也画」的重复显示。
     */
    public static final boolean NATIVE_BAR_FOR_FIRST_THREAD = true;

    /**
     * 原生条下面是否补一行「上方那条 = 线程N: / 并行 p」的说明。
     *
     * <p>理由：原生条只显示 {@code 进度 / 时长 s}（GTCEu 的 {@code gtceu.jade.progress_sec}），
     * <b>不带我们的线程标签与并行</b>；而它下面追加的每一组都带 {@code 线程N:} ⇒ 不补这一行的话，
     * 玩家无法把最上面那条原生条与某条线程对上。**不碰原生条本身**（只加一行文字），
     * 所以「原生外观」逐字不变。
     */
    public static final boolean NATIVE_BAR_CAPTION = true;

    /**
     * 条上的文字是「压在条上的 overlay」（{@code true}，用户要的形态：条内就是 GTO 的 {@code 2/4 s}）
     * 还是「条上只有标签 + 数字并列在条后」（{@code false}，改前行为）。
     */
    public static final boolean OVERLAY_TEXT = true;

    /** 绿色填充（与 GTCEu「机器在跑」那条进度条的绿同一个值；沿用本包既有取值）。 */
    public static final int BAR_ARGB = 0xFF4CBB17;

    /** 条上文字用白：深绿底上打绿字看不清。 */
    public static final int TEXT_ARGB = 0xFFFFFFFF;

    /**
     * ★ <b>1 秒 = 多少 tick</b>（= 20）：条内文字的单位从 tick 换成<b>秒</b>才与 GTO 的
     * {@code 2/4 s} 同形（GTCEu/MC 固定 20 t/s，不是可配置值）。
     *
     * <p>它<b>只用于显示</b>：填充比例仍然用 tick 原值算（{@code progress / duration}），
     * 换算不影响条长、也不改任何数字口径。
     */
    public static final int TICKS_PER_SECOND = 20;

    /**
     * ★ <b>条内文字键</b>（overlay，2 个占位符：进度 / 时长）。
     *
     * <p>口径 = GTO 截图里的 {@code 2/4 s}：<b>进度 / 时长 + 单位</b>，就这一小段
     * ⇒ 它不会把提示框撑宽（条宽 = 提示框内容宽度，见类注释的「条宽」一条）。
     * 改前这一键是 {@code "#%s  %s/%s t  并行 %s"}（4 个占位符、很长，正是「条太长」的成因之一）；
     * 现在两个占位符都是<b>已经算好的秒数字符串</b>（{@link #appendThreadBar} 里换算），
     * 所以中英文案里只有 {@code s} 这一个单位，不会再出现 {@code t}。
     */
    public static final String LANG_BAR = "gtlmodular.nanochip.threads.bar";

    /**
     * ★ <b>并行信息行键</b>（1 个占位符：这条线程自己那份并行 p）——
     * 形如 {@code 并行 128} / {@code parallel 128}。
     *
     * <p>为什么单独一行：并行原本压在条上（{@code ...  并行 %s}）⇒ 与编号、单位一起
     * 把条内文字拉得很长，进而把整个提示框撑宽（用户嫌「条太长」的根因），
     * 而且条内文字一旦长到超过条宽就有被挤出的风险。移出条外后：
     * 条内只剩 {@code 2/4 s}，并行照旧可见。
     */
    public static final String LANG_PARALLEL = "gtlmodular.nanochip.threads.parallel";

    /**
     * ★ <b>线程标签行键</b>（1 个占位符：<b>本次列表内的下标</b>，从 0 起）—— 形如 {@code 线程0:} / {@code Thread 0:}，
     * <b>逐字照 GTO</b> 的 {@code 线程0:}。
     *
     * <p>★ 2026-11-02 起占位符的取值 = 显示下标（{@link #appendAllRows} 的 {@code i}）；
     * <b>改前</b>传的是服务端开批次时钉死的<b>全局递增稳定编号</b>
     * （{@code NanochipThreadedRecipeLogic#threadLabelOf}）⇒ 实机显示 {@code 线程1 / 线程5 / 线程16 …}
     * 一路往上涨，与 GTO 不同形。内部那个 {@code seq} 与 {@code KEY_SLOT} <b>一字未动</b>
     * （只在显示这一处解耦）。
     * 键名沿用 {@code ....bar.short}（历史名；改前它渲染 {@code #编号}）：<b>语义不变</b>
     * ——「这一条线程的短标签」，只是形态从 {@code #0} 改成 GTO 的 {@code 线程0:}。
     */
    public static final String LANG_THREAD_LABEL = "gtlmodular.nanochip.threads.bar.short";

    /**
     * ★ <b>线程标签行的旧名</b>（= {@link #LANG_THREAD_LABEL}）。
     *
     * <p>保留常量是为了不动回退形态的调用点（{@code OVERLAY_TEXT = false} 那条路原本读它）；
     * 两者指向<b>同一个键</b>，不会出现两份文案。
     */
    public static final String LANG_BAR_SHORT = LANG_THREAD_LABEL;

    /** 截断行键（2 个占位符：还有几条没列 / 上方列了几条）。 */
    public static final String LANG_MORE = "gtlmodular.nanochip.threads.more";

    /**
     * ★ <b>【GTO 界面口径 · 折叠行】「还有 M 条未列出」那一行的键</b>（<b>1 个占位符</b>：未列条数 M）。
     *
     * <p>与 {@link #LANG_MORE} 的区别：本键<b>逐字照用户给的 GTO 口径</b>
     * （中文「还有 %s 条未列出」、英文 "and %s more not listed"），<b>没有</b>那个
     * 「（上方只列进度最快的 %s 条）」尾巴 —— 提示框宽度由最长的一行决定
     * （见类注释的「条宽」一条），去掉长尾巴才不会把框撑宽。
     *
     * <p>{@link #LANG_MORE} <b>一字未改</b>：机器面板
     * （{@code NanochipThreadedRecipeLogic#LANG_THREADS_MORE}）仍读它（那边是 8 行窗口，
     * 「只列最快的 N 条」那句在场是必要的）。
     */
    public static final String LANG_MORE_UNLISTED = "gtlmodular.nanochip.threads.more.unlisted";

    /**
     * ★ <b>「等价并行」口径行键</b>（3 个占位符：在用 k 条 / 单条并行 p / 总计 R 次）。
     *
     * <p>落地用户原话：「独立线程就相当于，如果说现在有 10 个独立线程，那就相当于 10 台机器在工作，
     * 如果他有 100 的并行的话，那现在就相当于 1000 的并行」⇒
     * <b>总并行 R = Σ(各线程自己的并行)</b>，而「几台机器」<b>只按「在用 k 条」算</b>
     * （闲置的不算；上限 256 <b>不</b>当作 256 台机器）。
     */
    public static final String LANG_MACHINE_EQUIV = "gtlmodular.nanochip.threads.machine_equiv";

    /**
     * 原生条说明行键（2 个占位符：稳定编号 / 并行）—— 见 {@link #NATIVE_BAR_CAPTION}。
     *
     * <p>文案里的编号形态本轮同步改成 {@code 线程N:}（与标签行同形，改前是 {@code #N}）：
     * 否则回退形态下会出现「标签写 {@code 线程3:}、说明行写 {@code #3}」两套叫法。
     */
    public static final String LANG_NATIVE_HEAD = "gtlmodular.nanochip.threads.native";

    private NanochipJadeThreadRows() {
    }

    /**
     * 原生进度条的「服务端是否报了进度」判据 —— 用来决定<b>要不要跳过第 0 条</b>。
     *
     * <p>原生 {@code WorkableBlockProvider.addTooltip} 只有在它拿到的 {@code capData} 里有
     * {@code Progress} / {@code MaxProgress} 时才画条（键名取证：
     * {@code _census/_probe_nacmat_evidence/classstr_gtceu-1.20.1-1.4.4.txt:50928-50933}
     * 的 {@code WorkableBlockProvider} 常量池：{@code workable_provider} / {@code Progress} /
     * {@code MaxProgress} / {@code Research}）。
     *
     * <p><b>为什么必须判它</b>：机器在跑但那一刻原生条没画出来（例如它只在「在工作」时画，
     * 而我们的批次可能处于 WAITING）时，如果我们仍然跳过第 0 条，那条线程就会<b>在任何地方都不显示</b>。
     * 判据为假时我们追加<b>全部</b>线程 ⇒ 宁可多画一条，绝不漏掉一条。
     */
    public static boolean hasNativeProgressKeys(CompoundTag data) {
        return data != null && (data.contains("Progress") || data.contains("MaxProgress"));
    }

    /**
     * 在<b>原生条之后</b>追加其余线程的「标签 + 条」（<b>回退形态专用</b>：只由混入的
     * {@code @At("TAIL")} 注入点在 {@link #OUR_OWN_BARS} = {@code false} 且
     * {@link #NATIVE_BAR_FOR_FIRST_THREAD} = {@code true} 时调用；
     * 见类注释的服务端/客户端边界）。
     *
     * <p>布局与默认形态逐字一致（{@link #appendThreadLabel} + {@link #appendThreadBar}），
     * 并在末尾补 {@link #appendEquivalenceLine}（口径行）与超限交代行（{@link #LANG_MORE_UNLISTED}）
     * —— 两条路径的数字与文案同源。
     *
     * <p>★【GTO 界面口径 · 标签】这一支的标签同样用<b>列表下标</b>：原生条 = 第 0 条
     * ⇒ 它后面追加的第一组就是 {@code 线程1}（与默认形态的编号连续，不跳号）。
     *
     * @param nativeBarDrawn 原生条这一刻是否真的画了（{@link #hasNativeProgressKeys} 的结论）
     */
    public static void appendAfterNativeBar(ITooltip tooltip, CompoundTag data, boolean nativeBarDrawn) {
        if (tooltip == null || data == null) {
            return;
        }
        int running = data.getInt(NanochipJadeKeys.KEY_THREAD_RUNNING);
        if (running <= 0) {
            return;   // 一条线程都没有：只留总览，不画空条
        }
        ListTag list = data.getList(NanochipJadeKeys.KEY_THREAD_LIST, Tag.TAG_COMPOUND);
        int mirrored = data.getInt(NanochipJadeKeys.KEY_MIRRORED_SLOT);
        // 明细总行数（原生条的那一组 = 2 行：标签 + 条）不超过 DISPLAY_LINES 条线程：
        // 原生条算 1 条线程 ⇒ 这里最多再追加 DISPLAY_LINES-1 组
        int cap = Math.max(1, NanochipJadeKeys.DISPLAY_LINES - (nativeBarDrawn ? 1 : 0));
        if (nativeBarDrawn && NATIVE_BAR_CAPTION) {
            // 先补说明：它紧贴原生条，玩家一眼知道"上面那条是哪条线程"
            tooltip.add(Component.translatable(LANG_NATIVE_HEAD, mirrored,
                    FormattingUtil.formatNumbers(data.getLong(NanochipJadeKeys.KEY_MIRRORED_PARALLELS))));
        }
        int appended = 0;
        // ★【GTO 界面口径】标签改用「本次列表内的下标（从 0 起）」：原生条代表第 0 条
        //   ⇒ 它后面追加的第一组就是「线程1」（见 appendAllRows 的同款说明）。
        int labelIndex = nativeBarDrawn ? 1 : 0;
        for (int i = 0; i < list.size() && appended < cap; i++) {
            CompoundTag entry = list.getCompound(i);
            if (nativeBarDrawn && entry.getInt(NanochipJadeKeys.KEY_SLOT) == mirrored) {
                continue;   // 原生条就是它 ⇒ 不重复画一组
            }
            appendThreadLabel(tooltip, labelIndex);
            appendThreadBar(tooltip, entry);
            appended++;
            labelIndex++;
        }
        int shown = appended + (nativeBarDrawn ? 1 : 0);
        // ★ 口径行：k 与 R 都取服务端按【全部】批次算出来的数（不是已画出的那几组之和）
        appendEquivalenceLine(tooltip, running,
                data.getLong(NanochipJadeKeys.KEY_THREAD_RUNS));
        if (shown < running) {
            tooltip.add(Component.translatable(LANG_MORE_UNLISTED, running - shown));
        }
    }

    /**
     * ★ <b>默认形态（{@link #OUR_OWN_BARS} = true）的渲染入口</b>：把服务端给的批次明细
     * <b>一条线程一组「标签行 + 整行宽进度条」</b>地画出来 —— <b>列表里有几条就画几组</b>
     * （一一对应，没有「只画第一条」这类特判），最多 {@link NanochipJadeKeys#DISPLAY_LINES} 条；
     * 超出的用一行「还有 M 条未列出」如实交代（{@link #LANG_MORE_UNLISTED}，M=0 时本行不出现），并补一行「等价并行」口径
     * （{@link #appendEquivalenceLine}，用服务端按<b>全部</b>批次算出来的 k 与 R）。
     *
     * <p>调用者是<b>我们自己的 provider</b>（{@code NanochipThreadedJadeProvider#appendTooltip}）
     * ⇒ 条的出现<b>不依赖任何混入</b>：混入那条链就算整个失效，玩家至少还能看到这几组条
     * （外加一条没被取消掉的原生条），绝不会退化成「只有一条」。
     *
     * <p>「有没有原生条」与本方法无关：默认形态下混入已在 {@code @At("HEAD")} 把原生条取消掉了，
     * 所以这里不需要跳过任何一条（不跳过 ⇒ 「几条线程几组条」最直白）。
     */
    public static void appendAllRows(ITooltip tooltip, CompoundTag data) {
        if (tooltip == null || data == null) {
            return;
        }
        int running = data.getInt(NanochipJadeKeys.KEY_THREAD_RUNNING);
        if (running <= 0) {
            return;
        }
        ListTag list = data.getList(NanochipJadeKeys.KEY_THREAD_LIST, Tag.TAG_COMPOUND);
        int shown = 0;
        for (int i = 0; i < list.size() && shown < NanochipJadeKeys.DISPLAY_LINES; i++) {
            CompoundTag entry = list.getCompound(i);
            // ★【GTO 界面口径 · 标签】用「本次列表内的下标（从 0 起）」= 线程0 / 线程1 / 线程2，
            //   与 GTO 截图逐字同形；**不再**用服务端开批次时钉死的全局递增稳定编号
            //   （那会显示成 线程1 / 线程5 / 线程16 … 一路往上涨，用户实机看到的就是它）。
            //   内部 seq（KEY_SLOT / threadLabelOf / 原生条归属判定）**一律未动**，只有这一处显示解耦。
            appendThreadLabel(tooltip, i);
            appendThreadBar(tooltip, entry);
            shown++;
        }
        // ★ 口径行：k 与 R 都取服务端按【全部】批次算出来的数（不是已画出的那几组之和）
        appendEquivalenceLine(tooltip, running,
                data.getLong(NanochipJadeKeys.KEY_THREAD_RUNS));
        if (shown < running) {
            // ★【GTO 界面口径 · 折叠】第 4 条起不画进度条（循环上限 = DISPLAY_LINES = 3），
            //   在这里补一行「还有 M 条未列出」（M = 在用总数 − 已画条数；shown == running 时本行不出现）。
            tooltip.add(Component.translatable(LANG_MORE_UNLISTED, running - shown));
        }
    }

    /**
     * ★ <b>标签行</b>：单独一行 {@code 线程0:} / {@code Thread 0:}（GTO 的层次：<b>标签一行、条一行</b>）。
     *
     * <p>为什么要独占一行、而不是放进条内文字：Jade 的 {@code ProgressElement} 只有一个文字槽
     * （就是 overlay 那段），把编号塞进条里会<b>同时</b>把提示框与条一起撑宽
     * （{@code getSize().width += font.width(text) + 3}，而框宽 = 最宽的一行）——
     * 那正是用户说的「你为什么会是这么长条呢」。标签单独成行后，条内只剩 {@code 2/4 s}。
     *
     * <p>★ <b>2026-11-02 起编号口径 = 「本次列表内的下标」（从 0 起）</b>，与 GTO 截图的
     * {@code 线程0: / 线程1: / 线程2:} <b>逐字同形</b>。
     * <p>改前传的是服务端开批次时钉死的<b>全局递增稳定编号</b>
     * （{@code NanochipThreadedRecipeLogic#threadLabelOf}）⇒ 玩家实机看到 {@code 线程1}、
     * {@code 线程5/6}、{@code 线程16/17} 一路往上涨，与 GTO 不同形。
     * <b>本轮回退到「显示下标」</b>：显示层与内部 {@code seq} <b>解耦</b>，
     * {@code KEY_SLOT} / {@code threadLabelOf} / 原生条归属判定 <b>一字未动</b>
     * （内部的全局递增编号仍可能在别处使用）。
     */
    public static void appendThreadLabel(ITooltip tooltip, int displayIndex) {
        if (tooltip == null) {
            return;
        }
        tooltip.add(Component.translatable(LANG_THREAD_LABEL, displayIndex));
    }

    /**
     * ★ <b>「等价并行」口径行</b>：把用户那句口径如实落地，且<b>只用真实数字</b>。
     *
     * <h3>用户原话（本轮必须落地的那句）</h3>
     * <blockquote>「独立线程就相当于，如果说现在有 10 个独立线程，那就相当于 10 台机器在工作，
     * 如果他有 100 的并行的话，那现在就相当于 1000 的并行」</blockquote>
     *
     * <h3>怎么落到数字上（三个数各是什么，逐条对齐）</h3>
     * <ol>
     *   <li>{@code k = running}（<b>在用</b>条数，服务端 {@code runningThreadCount()} 数出来的）
     *       ⇒ 文案里的「相当于 k 台机器并行」<b>只按在用的算</b>：
     *       一台 256 上限的机器只开了 3 条，就是 {@code 3} 台，<b>不是 256 台</b>（闲置的不算）；</li>
     *   <li>{@code R = totalRuns}（各线程<b>自己那份并行之和</b>，{@code totalRunsAtOnce()}）
     *       ⇒ 文案后半句「= R 次配方运行」；用户例子里「10 台机器 × 各自 100 并行 = 1000」，
     *       在我们这里就是 {@code R}（{@code Σ p_i}），<b>不需要拿 k 去乘任何东西</b>：</li>
     *   <li>括号里补 {@code Σp = R} <b>只是标明 R 的来源</b>（不是再另算一遍并行），
     *       所以这一行的三个数与「同时在跑几条 / 一共几次运行」不会互相打架。</li>
     * </ol>
     *
     * <p><b>不许夸大的写法</b>：这里<b>没有</b>任何形如「{@code 上限 × 并行 = 多少多少并行}」的乘法
     * ——上限只是「最多能有几条」，闲置线程不产生并行，把它们算成机器就是虚标。
     *
     * @param running  在用线程条数 k（服务端按<b>全部</b>批次算，与画出几条无关）
     * @param totalRuns 整机同时处理次数 R（= 各线程自己那份并行之和）
     */
    public static void appendEquivalenceLine(ITooltip tooltip, int running, long totalRuns) {
        if (tooltip == null || running <= 0) {
            return;   // 一条没在跑：不补这一行（总览已经写了「在用 0 条」）
        }
        tooltip.add(Component.translatable(LANG_MACHINE_EQUIV,
                running,
                running,
                FormattingUtil.formatNumbers(totalRuns)));
    }

    /**
     * ★ <b>一条线程 = 三行</b>（GTO 的层次 + 并行单独一行）：{@link #appendThreadLabel} 画标签行
     * （{@code 线程0:}），本方法画<b>一整行宽的进度条</b>，随后 {@link #appendThreadParallel}
     * 画并行行 —— <b>条内文字只剩 {@code 2/4 s}</b>
     * （改前是 {@code #0  2/4 t  并行 128}，4 段文字，正是把框撑宽的主因）。
     *
     * <h3>条内文字 = {@code x/y s}（逐字照 GTO 那张图）</h3>
     * <ul>
     *   <li><b>单位是秒</b>：GTO 截图里就是 {@code 2/4 s} ⇒
     *       {@code NanochipJadeKeys.KEY_DURATION} 是 <b>tick</b>（{@code ThreadRec.duration()}
     *       来自配方时长），所以要 {@code /}{@value #TICKS_PER_SECOND}（GTCEu/MC 的 20 t = 1 s）；</li>
     *   <li><b>取整</b>：默认<b>整数秒</b>（照 GTO 的 {@code 2/4 s} 风格）——
     *       但整条配方不足 1 秒时整数秒会是 {@code 0}（{@code 0/0 s} 什么信息都没有）⇒
     *       只有<b>这种短配方</b>才显示 <b>1 位小数</b>（{@code 0.4/0.7 s}），格式写死 {@code Locale.ROOT}
     *       （不随客户端语言把小数点变成逗号）；</li>
     *   <li><b>除零</b>：{@code duration <= 0}（没在跑的直通档/异常值）⇒ 分母按 1 tick 算 ⇒
     *       输出 {@code 0/0.1 s}，<b>不会出现 NaN / 空白</b>；</li>
     *   <li><b>钳制</b>：{@code progress > duration} 时把比例夹到 1，文字也照同一夹法
     *       （条与文字永不失配）。</li>
     * </ul>
     *
     * <h3>并行信息移出条外</h3>
     * <p>并行（{@code p}）<b>不再压在条上</b>：由 {@link #appendThreadParallel} 单独一行
     * {@code 并行 128} / {@code parallel 128} —— 既保留「这条线程一次做几份」，
     * 又不把提示框撑宽（用户嫌长的根因）。
     *
     * <h3>条宽是怎么定的（源码级依据，不给它传任何宽度参数）</h3>
     * <p>Jade {@code 1.20.1-Forge-11.13.3}，{@code snownee/jade/impl/ui/ProgressElement.java}：
     * <ul>
     *   <li>{@code getSize()}：{@code height = text == null ? 8 : 14}、
     *       {@code width = max(20, border*2 + font.width(text) + 3)}
     *       ⇒ 这个宽度<b>只用来算「提示框要多宽」</b>（{@code TooltipRenderer#recalculateSize}
     *       取各行宽度的<b>最大值</b>），条本身并不按它画；</li>
     *   <li>{@code render(guiGraphics, x, y, maxX, maxY)} 里画的是
     *       {@code boxStyle.render(..., maxX - x, ...)} 与 {@code style.render(..., maxX - x - b*2, ...)}
     *       —— 用的是<b>该行剩余宽度</b>；而 {@code Tooltip.Line#render} 对<b>本行最后一个</b>元素传的
     *       {@code maxX} 正是提示框内容右边界 ⇒
     *       <b>条 = 提示框内容整宽 = 与其它文字行同宽</b>。</li>
     * </ul>
     * ⇒ 所以「条宽与文字同宽」不是靠传参，而是靠<b>把框宽交还给文字</b>：
     * 条内文字越短 ⇒ 框宽由最宽的文字行决定 ⇒ 条与文字行同宽（GTO 那条就是这个形态）。
     * ⚠️ 诚实边界：条<b>恰好</b>与哪一行等宽，取决于那一刻哪一行文字最长（Jade 不提供「指定条宽」的 API）。
     *
     * <p><b>「文字压在条上」同样有源码依据</b>：第 2 个参数就是条内文字，
     * {@code ProgressStyle.render(...)} 先画填充、<b>再</b>对同一矩形
     * {@code drawString(text, x + 1, y + height - lineHeight)} ⇒ 文字是叠在条身上的 overlay
     * （不是「条—字—条」把条切断）。调用形态与参考实现 GTET
     * {@code ThreadedRecipeLogicProvider.kt:129-141} 同款，颜色沿用本包既有取值
     * （{@link #BAR_ARGB} = GTCEu「机器在跑」的绿）。
     */
    public static void appendThreadBar(ITooltip tooltip, CompoundTag entry) {
        if (tooltip == null || entry == null) {
            return;
        }
        int slot = entry.getInt(NanochipJadeKeys.KEY_SLOT);
        int progress = entry.getInt(NanochipJadeKeys.KEY_PROGRESS);
        int duration = entry.getInt(NanochipJadeKeys.KEY_DURATION);
        long parallels = entry.getLong(NanochipJadeKeys.KEY_PARALLELS);
        float ratio = duration <= 0 ? 0.0F : (float) progress / (float) duration;
        if (ratio < 0.0F) {
            ratio = 0.0F;
        } else if (ratio > 1.0F) {
            ratio = 1.0F;
        }
        // 条内文字 = GTO 的 `x/y s`：先按秒取整，整条不足 1 秒时才用 1 位小数（理由见 javadoc）
        String progressText;
        String durationText;
        if (duration > TICKS_PER_SECOND) {
            progressText = Integer.toString(progress / TICKS_PER_SECOND);
            durationText = Integer.toString(duration / TICKS_PER_SECOND);
        } else {
            // ⚠ duration <= 0 也走这一支：分母按 1 tick 算 ⇒ 0/0.1 s，不出现 NaN / 空白
            int divisor = duration > 0 ? duration : 1;
            progressText = String.format(Locale.ROOT, "%.1f", progress / (double) TICKS_PER_SECOND);
            durationText = String.format(Locale.ROOT, "%.1f", divisor / (double) TICKS_PER_SECOND);
        }
        Component barText = OVERLAY_TEXT
                ? Component.translatable(LANG_BAR, progressText, durationText)
                : Component.translatable(LANG_BAR_SHORT, slot);
        tooltip.add(tooltip.getElementHelper().progress(
                ratio,
                barText,
                tooltip.getElementHelper().progressStyle()
                        .color(BAR_ARGB)
                        .textColor(TEXT_ARGB),
                BoxStyle.DEFAULT,
                true));
        // ★ 并行移出条外：单独一行（不与条挤在一起，也不撑宽）
        appendThreadParallel(tooltip, parallels);
        if (!OVERLAY_TEXT) {
            // 回退形态：条上只有标签，数字作为并列元素接在条后（改前行为，逐字）
            tooltip.append(Component.translatable("gtlmodular.nanochip.threads.line",
                    slot, progress, duration, FormattingUtil.formatNumbers(parallels)));
        }
    }

    /**
     * ★ <b>并行行</b>：{@code 并行 128} / {@code parallel 128} —— 条内被移出来的那条信息。
     *
     * <p>数字口径与总览行「同时处理 R 次」<b>同源</b>（服务端 {@code KEY_PARALLELS} =
     * {@code ThreadRec.parallels()} = {@code IGTRecipe.getRealParallels}）⇒ 逐条之和恒等于 R，
     * 两处不会打架；这里不做任何估算、不四舍五入。
     *
     * <p>{@code p <= 0}（异常 / 老数据缺键）时<b>不画这一行</b>：宁可少一行，也不谎报一个并行数。
     */
    public static void appendThreadParallel(ITooltip tooltip, long parallels) {
        if (tooltip == null || parallels <= 0L) {
            return;
        }
        tooltip.add(Component.translatable(LANG_PARALLEL,
                FormattingUtil.formatNumbers(parallels)));
    }
}

