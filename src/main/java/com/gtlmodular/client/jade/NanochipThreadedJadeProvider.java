package com.gtlmodular.client.jade;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.utils.FormattingUtil;
import com.gtlmodular.GTLModular;
import com.gtlmodular.common.machine.nanochip.NanochipAssemblyComplexMachine;
import com.gtlmodular.common.machine.nanochip.NanochipThreadedRecipeLogic;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.IElement;
import snownee.jade.api.ui.IElementHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * ★★★ 2026-10-28【独立跨配方线程 · Jade 显示】★★★
 * 把本机（纳米芯片组装复合体）的<b>多线程状态</b>摆进 Jade 准星提示：
 * 「独立线程：上限 N 条 · 在用 k 条」+「同时处理 R 次配方运行（各线程之和）」+
 * <b>每条在跑的线程一组绿色进度条（标签 + 条 + 并行）</b>。
 *
 * <h2>★ 2026-11-02【最终形态：<b>我们自己画</b> · 几条线程画几条条】（用户原话）</h2>
 * <blockquote>「那个进度条还是<b>换回我们自己的</b>吧，它<b>只会显示一个进度条</b>，我需要他<b>多线程的时候，
 * 同时显示多个进度条</b>」</blockquote>
 * ⇒ <b>不再沿用 GTCEu 原生那条</b>：它读的是 {@code RecipeLogic} 基类的<b>单</b>进度字段
 * ⇒ 天生只反映一条线程；而「原生条 + 混入追加」形态下多条条<b>完全依赖混入的注入生效</b>
 * （{@code require = 0} ⇒ 失败静默）⇒ 一旦那条链断掉就退化成「只剩一条」。
 * 现在：混入只在 {@code @At("HEAD")} <b>取消</b>原生条（失败即退回原生条，玩家不会什么都看不到），
 * <b>条本身由本 provider 自己画</b>（{@link NanochipJadeThreadRows#appendAllRows}，
 * 渲染共用 {@link NanochipJadeThreadRows#appendThreadBar}）。
 * 本 provider 负责：<b>服务端写数据</b>（每条批次一个子项）＋ <b>客户端画总览两行 + 每条线程一组「标签/条/并行」</b>。
 *
 * <h2>★ 2026-11-02【多线程显示设计与理由】（逐条）</h2>
 * <ol>
 *   <li><b>每条线程一组三行（标签 / 条 / 并行）</b>：<b>几条在跑的批次就画几组</b>
 *       （一一对应：服务端列表（{@code NanochipThreadedJadeProvider#snapshotThreads}）里有一条，
 *       客户端就画一组 {@code 线程N:} + 整行宽进度条（条内 {@code 2/4 s}）+ {@code 并行 p}）。
 *       <b>编号保留</b>（玩家要区分是哪条），且已从「表内下标」换成<b>开批次时钉死的稳定编号</b>
 *       （{@code NanochipThreadedRecipeLogic#threadLabelOf}：批次跑完会从表里摘掉、下标会整体前移，
 *       而 fan-out 之后同一条配方会同时占多条，稳定编号才认得出是哪条）。</li>
 *   <li><b>顺序 = 进度降序（同进度按编号升序）</b>：玩家看提示的目的就是「哪条快好了 / 机器在不在动」
 *       ⇒ 把最接近完工的摆最上面；同进度用稳定编号兜底 ⇒ 同一 tick 内两次读取顺序一致、不抖动。
 *       （排序在服务端 {@code snapshot()} 里做一次，面板与 Jade 共用同一份顺序与同一份数字。）
 *       ⇒ 「列出来的 3 条」就是<b>进度最快的 3 条</b>。</li>
 *   <li><b>总览两行保持现状</b>：{@code 上限 N 条 · 在用 k 条} ＋ {@code 同时处理 R 次配方运行（各线程之和）}
 *       —— k 与 R 都由服务端按<b>全部</b>批次算（{@code runningThreadCount()} / {@code totalRunsAtOnce()}），
 *       与下面画了几条无关。</li>
 *   <li><b>超长截断（上限 3 条）</b>：进度条条数不超过 {@link NanochipJadeKeys#DISPLAY_LINES} = <b>3</b>
 *       （用户原话「最多也就同时只显示三个进度条吧」），多出来的用
 *       「还有 M 条未列出」一行如实交代（M = 在用总数 − 已画条数；
 *       键 {@code gtlmodular.nanochip.threads.more.unlisted}，<b>GTO 界面口径</b>）；
 *       <b>R 与 k 永远反映全部线程</b>（不是只把画出的那几条加起来）。</li>
 *   <li><b>每条线程 = 标签行 + 条行 + 并行行</b>（GTO 层次：{@code 线程0:} / 条 / {@code 并行 p}）——
 *       并行信息<b>从条内移到条外</b>（见 {@link NanochipJadeThreadRows#appendThreadBar}）；
 *       条内只剩 {@code 2/4 s}。</li>
 *   <li><b>空间</b>：Jade 提示只在玩家把准星指过来时才渲染 ⇒「只在玩家看着它时才展开明细」天然成立；
 *       高度靠「明细上限 3 条线程 + 一行交代 + 一行口径」封顶
 *       （≤ 3 组「标签 + 条 + 并行」+ 2 行总览 + 1 行等价并行 + 1 行超限交代），
 *       不会随线程数（上限 256）膨胀。
 *       <p>★ 2026-11-17 补注：上面说的是<b>进度条那一节</b>（{@link NanochipJadeKeys#DISPLAY_LINES} = 3）。
 *       「各线程产物」那一节是<b>另一节</b>，按它自己的上限 {@link NanochipJadeKeys#OUTPUT_LINES} = 6
 *       封顶（≤ 6 行「图标 + 线程N：数量 × [名]」+ 可能的一行「还有 M 条未列出」）⇒
 *       总高度 = 两节之和，<b>仍与机器实际在跑多少条线程无关</b>（上限 256 也一样）。</li>
 *   <li><b>数字口径纪律（显示即事实）</b>：每条的比例恒为 {@code progress / duration}（夹 0~1，不虚标）；
 *       「并行 p」与「同时处理 R」<b>同一个函数</b>（{@code IGTRecipe.getRealParallels}，取不到退回
 *       {@code recipe.parallels}）⇒ 逐条之和恒等于 R；「在用 k」等于真实批次数，不四舍五入凑整。</li>
 *   <li><b>中英双语</b>：条内 / 标签 / 并行 / 截断 / 原生条说明 / 等价并行六类文案在
 *       {@code assets/gtlmodular/lang/zh_cn.json} 与 {@code en_us.json} 各一条
 *       （{@code gtlmodular.nanochip.threads.bar} = {@code %s/%s s} /
 *       {@code ....bar.short} = {@code 线程%s:} / {@code ....parallel} = {@code 并行 %s} /
 *       {@code ....more} / {@code ....native} / {@code ....machine_equiv}）；
 *       并删掉了 {@code ....latency_note}（用户要求去掉那行「延迟说明」）。</li>
 *   <li><b>「等价并行」口径（照用户原话，且不许夸大）</b>：<blockquote>「独立线程就相当于，
 *       如果说现在有 10 个独立线程，那就相当于 10 台机器在工作，如果他有 100 的并行的话，
 *       那现在就相当于 1000 的并行」</blockquote> ⇒ 文案里「相当于 k 台机器并行」的 {@code k}
 *       <b>只取「在用」条数</b>（闲置的不算：上限 256、在用 3 就是 3 台，<b>不是 256 台</b>），
 *       总并行 {@code R = Σ(各线程自己的并行)}；实现见
 *       {@link NanochipJadeThreadRows#appendEquivalenceLine}。</li>
 * </ol>
 *
 * <h2>为什么仍要自己写 provider（承载「显示即事实」）</h2>
 * <p>GTCEu 原生那条进度条读的是 {@code RecipeLogic} 基类的<b>单</b>进度字段
 * （{@code progress / duration}）—— 它<b>只能</b>反映一条线程（我们让第 0 条镜像进去）。
 * 线程表<b>只在服务端有内容</b>（客户端那份是空的），所以「还有几条、各跑多快、一次几份」
 * 必须由服务端经 Jade 的 serverData 通道送过来；这就是本类同时实现
 * {@link IServerDataProvider}（服务端写 NBT）与 {@link IBlockComponentProvider}（客户端读 NBT 拼文本）的原因。
 *
 * <h2>服务端 / 客户端边界（逐条）</h2>
 * <ul>
 *   <li>{@link #appendServerData}：<b>只在服务端</b>跑 —— 此时才读得到真的线程表
 *       （{@code NanochipThreadedRecipeLogic#snapshot()} 是服务端权威数据）。</li>
 *   <li>{@link #appendTooltip}：<b>只在客户端</b>跑 —— Jade 的方块提示是纯客户端渲染，
 *       本方法只读 {@code accessor.getServerData()}，<b>不碰任何服务端对象</b>。</li>
 *   <li>服务端<b>不</b>注册 {@link #appendTooltip}（那是客户端注册表的事，见
 *       {@code NanochipJadePlugin#registerClient}）。</li>
 * </ul>
 *
 * <h2>判定「只作用于我们的机器」（不许影响别的模组）</h2>
 * <p>服务端的判据是本机配方逻辑的<b>运行时类型</b>：
 * {@code getRecipeLogic() instanceof NanochipThreadedRecipeLogic}
 * （本类是 {@code RecipeLogic} 的直接子类，且只在 {@code NAC_INDEPENDENT_THREADS} 打开时由
 * {@code createRecipeLogic} 返回）⇒ 旧逻辑的机器与别人的模组一概不命中、一个键都不写。
 * 客户端判定见 {@link NanochipJadeKeys#KEY_THREAD_LIMIT}（键存在 = 我们的机器）。
 *
 * <h2>诚实边界（不承诺夸大数字）</h2>
 * <ul>
 *   <li><b>N 是上限、k 是数出来的</b>：料不够 / 放不下 / 没电时 k &lt; N，照实显示。</li>
 *   <li><b>R 是各线程自己那份并行之和</b>（{@code totalRunsAtOnce()}），不是功率倍率，
 *       也不拿它算耗电。</li>
 *   <li><b>只画前 {@link NanochipJadeKeys#DISPLAY_LINES} 条</b>进度条（= 3 条，按进度降序取最快的那些），
 *       其余用一行交代「还有 M 条未列出」（{@code ....more.unlisted}）——
 *       悬浮框跟着准星走，不为了好看撑满屏幕；<b>k 与 R 仍是全部批次数出来的</b>，不是画出的那几条之和。</li>
 *   <li><b>直通档（上限 1 条）也照常显示</b>：那一档跨配方线程本来就是 1，
 *       而配套的混入已经把原生进度条取消 ⇒ 若这里收手就会「什么进度都没有」。
 *       显示内容与原生条<b>信息量等价</b>（一条状态行 + 那一条线程的进度条）。
 *       理由与数字来源见 {@link #appendTooltip} 的注释。</li>
 *   <li><b>旧逻辑机器（{@code NAC_INDEPENDENT_THREADS = false} 时）</b>：
 *       {@code getRecipeLogic()} 不是 {@code NanochipThreadedRecipeLogic} ⇒ 本 provider
 *       一个键都不写、什么都不显示，GTCEu 原生那条进度条<b>照旧</b>显示（回退无需改代码）。</li>
 * </ul>
 *
 * @see NanochipJadePlugin
 * @see NanochipJadeKeys
 */
public class NanochipThreadedJadeProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {

    /** 单例（provider 无状态）。 */
    public static final NanochipThreadedJadeProvider INSTANCE = new NanochipThreadedJadeProvider();

    /**
     * ★ 2026-11-02【可见性变更】：条的颜色 / 文案键 / 形态开关 / 画法<b>全部搬到
     * {@link NanochipJadeThreadRows}</b> —— 自绘形态下「画每一条线程的条」也走那边，
     * 与回退形态（混入在原生条后面追加）<b>共用同一份</b>渲染（否则两条路径外观/口径会走岔）。
     *
     * <p>形态（默认）：<b>我们自己画</b> —— 服务端把每条在跑的批次写成一个 NBT 子项，
     * 本 provider 逐条画一整条进度条（见 {@link NanochipJadeThreadRows#appendAllRows}），
     * 混入只负责在 {@code @At("HEAD")} 取消原生条。
     * 服务端数据里仍保留「原生条代表哪一条」的标记键（回退形态的说明行要用）。
     *
     * <p><b>一行回退</b>：{@link NanochipJadeThreadRows#OUR_OWN_BARS} 置 {@code false}
     * ⇒ 逐字回到「原生条 + 混入追加其余线程」（那条路径的代码一行没删）。
     */

    private static final ResourceLocation UID =
            new ResourceLocation(NanochipJadeKeys.MODID, NanochipJadeKeys.UID_PATH);

    /**
     * ★★ 0.3.132【Jade 校准折减读数 · 键】★★
     *
     * <h4>队长的问题（逐字）</h4>
     * <blockquote>「还有校准生效的时候，它不是会减少耗电吗？那为什么他工作的时候，我这边看不到主机的
     * 总能耗倍率有所改变，还有耗时倍率」</blockquote>
     *
     * <h4>已查实的根因（照用，不重新论证）</h4>
     * Jade 里那两行「总耗能倍率：%s / 总耗时倍率：%s」由 <b>gtlcore 的 {@code RecipeMultiplierProvider}</b>
     * 输出，读的是 {@code MultipleRecipesLogic.getReductionEUt()} / {@code getReductionDuration()} ——
     * <b>gtlcore 自己的 reduction 字段</b>；而我们的纳米校准是在<b>我们自己的</b>
     * {@code buildRecipe} / {@code moduleOverclock} 里乘 {@code calibration.euMultiplier()} /
     * {@code durationMultiplier(family)}（走 {@code RecipeModifier}）⇒ <b>从不经过那两个字段</b>
     * ⇒ 那两行恒 100%。方案（队长选定丙）：<b>不碰上游</b>，由我们<b>另加一行</b>如实显示校准实际折减。
     *
     * <h4>为什么键常量写在本类（而不是 {@link NanochipJadeKeys}）</h4>
     * 本轮改动范围只允许动本文件与 {@code NanochipAssemblyComplexMachine}（外加两份 lang）
     * ⇒ 键名以本类私有常量承载；{@link NanochipJadeKeys} 那边的「键存在 = 我们的机器」判据
     * （{@link NanochipJadeKeys#KEY_THREAD_LIMIT}）不受影响。
     *
     * <p>取值：{@code double}，1.0 = 无折减、0.8 = EU ×0.8。数值<b>逐字来自</b>
     * {@code NanochipCalibration.euMultiplier()} / {@code durationMultiplier(activeFamily())}，
     * 本类<b>不做任何二次计算</b>，也不参与任何判定。
     */
    private static final String KEY_CALIB_EU_MUL = "KalibEuMul";

    /** ★ 见 {@link #KEY_CALIB_EU_MUL}：本机校准当前的时长折减倍率（{@code double}，1.0 = 无折减）。 */
    private static final String KEY_CALIB_DUR_MUL = "KalibDurMul";

    /** ★ 见 {@link #KEY_CALIB_EU_MUL}：我们那一行「校准折减」的语言键（中英各一条，见两份 lang）。 */
    private static final String LANG_CALIB_REDUCTION = "gtlmodular.nanochip.jade.calibration_reduction";

    /**
     * ★★ 0.3.132【① 读数：把「K 条」与「K 种」分开 · 键】★★
     *
     * <h4>它回答什么</h4>
     * 「K 条」= 批次条数（{@link NanochipThreadedRecipeLogic#runningThreadCount()}，
     * 已经由 {@link NanochipJadeKeys#KEY_THREAD_RUNNING} 送到客户端）；
     * 「K 种」= <b>按配方 id 去重后的不同配方种数</b>（本键）。
     * ⇒ 两者相等且大于 1 = 真的在并行跑<b>不同</b>配方；
     * 种数小于条数 = 其中有同一条配方占了多条线程（fan-out）。
     * 队长要的效果正是 GTO 那句「同时处理至多 16 种<b>不同</b>配方」（口径与用途的完整说明见
     * {@link NanochipThreadedRecipeLogic#distinctRunningRecipeCount()} 的 javadoc）。
     *
     * <h4>为什么键常量写在本类（与 {@link #KEY_CALIB_EU_MUL} 同一处理）</h4>
     * 本轮改动范围只允许动本文件与那两个 Java 文件（外加两份 lang）⇒
     * {@link NanochipJadeKeys} 不动（那边的「键存在 = 我们的机器」判据
     * {@link NanochipJadeKeys#KEY_THREAD_LIMIT} 不受影响）。
     * 键名 {@code "DistinctRecipes"} 与既有各键（{@code KalibEuMul} 等）都不冲突。
     *
     * <p>取值：{@code int}，<b>恒写</b>（包括「一条没跑」时写 0）：
     * 键在 = 服务端确实读到了本机的批次表；键缺失 = 不是我们的机器 / 旧版服务端（客户端因此不显示）。
     */
    private static final String KEY_DISTINCT_RECIPES = "DistinctRecipes";

    /** ★ 见 {@link #KEY_DISTINCT_RECIPES}：我们那一行「K 条 ÷ K 种」的语言键（中英各一条，见两份 lang）。 */
    private static final String LANG_DISTINCT_RECIPES = "gtlmodular.nanochip.jade.distinct_recipes";

    /**
     * ★★ 2026-11-04【Jade 逐线程产物 · 键】★★
     *
     * <h4>队长原话（逐字）</h4>
     * <blockquote>「你看到的截图里面也有，同时跑 6 个不同的配方，但是那边能看到的产物就只显示一种，
     * <b>得让 6 个产物都显示呢</b>」／「<b>每条线程的产物都得看到</b>」</blockquote>
     *
     * <h4>为什么必须由我们自己的行来列（上游那行碰不到、也不许碰）</h4>
     * 截图里那行 {@code 配方输出: 250 × [封装 超因果RAM芯片]} <b>不是我们写的</b>：本模组
     * {@code assets/gtlmodular/lang/} 里没有这个键，它出自上游（gtlcore / gtladditions 的 Jade 提供者，
     * 读机器的"当前 / 上一次配方"）⇒ 它天生只反映<b>一条</b>配方，而「每条线程各产什么」只有批次表知道
     * ⇒ 由本 provider <b>另加一小节</b>如实列出（<b>不去改上游、也不去注掉它</b>）。
     *
     * <h4>形态为什么选（乙）「新增一小节」而不是（甲）「并进每条线程行」</h4>
     * 逐条线程行（{@code 线程N:} / 进度条 / {@code 并行 p}）的渲染全在
     * {@link NanochipJadeThreadRows#appendThreadBar}，而本轮允许改动的文件<b>不含</b>那个类 ⇒
     * (甲) 在不改那个文件的前提下做不到：既不能在 {@code appendThreadBar} 里插一段，
     * 也不能在本 provider 里手搓那份渲染再 {@code tooltip.append(...)} —— 那会打破该类 javadoc
     * 写死的"自绘与回退两条路径<b>共用同一份渲染</b>"不变量，还会把产物名压到条那一行上
     * （提示框宽度 = 最宽的一行 ⇒ 有把框撑宽的风险）。(乙) 是<b>纯追加</b>：既有行一个字都不动。
     *
     * <h4>载荷（为什么加了产物名也不会炸）</h4>
     * 只写「<b>短键</b> + 数字」，不写配方对象、不写 ItemStack 全量 NBT：
     * 条目数在 {@link #snapshotThreads} 已按 {@link NanochipJadeKeys#OUTPUT_LINES} = <b>6</b> 截断
     * ⇒ 每条新增 ≈ <b>70 字节</b>（String 键 ≈ 48 + Long ≈ 22，算法见
     * {@link #threadTag}），一次提示最多新增 ≈ <b>420 字节</b>（6 条）。
     *
     * <h4>截断口径（复用既有机制，不自创）</h4>
     * ★ 2026-11-17【与进度条那节<b>脱钩</b>】：列出条数上限 = {@link NanochipJadeKeys#OUTPUT_LINES}（= 6，
     * <b>独立</b>常量；进度条那节仍用 {@link NanochipJadeKeys#DISPLAY_LINES} = 3 条），
     * 两节读的是<b>同一份</b> serverData 列表；超出的用<b>既有键</b>
     * {@link NanochipJadeThreadRows#LANG_MORE_UNLISTED}
     * （{@code gtlmodular.nanochip.threads.more.unlisted}）如实交代 M = 在用 − 已列。
     * <b>为什么必须独立</b>：{@code snapshotThreads} 的截断长度也必须是两个里较大的那个，
     * 否则第 4~6 条线程的产物数据根本送不到客户端（完整理由见 {@link NanochipJadeKeys#OUTPUT_LINES}）。
     *
     * <h4>★★ 2026-11-05【本键的语义变更：<b>本地化键 → 物品注册名</b>】★★</h4>
     * 队长原话（逐字）：<b>「能不能给这些现成产物加上图标」</b>。
     * <p><b>为什么必须换本键的载荷</b>：原先写的是
     * {@code ItemStack#getDescriptionId()}（形如 {@code item.gtlmodular.cc_crystal_processor}）——
     * 那是<b>翻译用</b>的键，<b>反查不回 {@code Item}</b>（键→物品没有注册表映射）
     * ⇒ 客户端<b>拿不到 {@code ItemStack}</b> ⇒ 画不出图标。
     * 现在写 {@code BuiltInRegistries.ITEM.getKey(stack.getItem())} 的 {@code namespace:path}
     * （取值端 {@link NanochipThreadedRecipeLogic#firstItemOutputId}，那里有完整理由与先例行号）
     * ⇒ 客户端 {@code ResourceLocation.tryParse} → {@code BuiltInRegistries.ITEM.get(...)}
     * → {@code ItemStack} ⇒ <b>既能取译名（串与本键改前送出的逐字相同 ⇒ 文案一个字不变），
     * 又能建物品图标元素</b>（见 {@link #appendThreadOutputs}）。
     * <p><b>为什么译名不在参数里传</b>：客户端拿到 {@code ItemStack} 后<b>本地</b>取
     * （{@code Component.translatable(stack.getDescriptionId())}，或等价的
     * {@code stack.getHoverName()}）⇒ 载荷只要一个字段，不必再送一个名字串。
     * <p><b>载荷量级不变</b>：注册名（{@code gtlmodular:cc_crystal_processor}）与本地化键
     * （{@code item.gtlmodular.cc_crystal_processor}）长度同量级，估算仍为 ≈ 70 字节/条。
     */
    private static final String KEY_OUTPUT_ID = "OutputId";

    /** ★ 见 {@link #KEY_OUTPUT_ID}：这条线程产物的数量（{@code long}；<b>内容数量，已含并行 p</b>）。 */
    private static final String KEY_OUTPUT_COUNT = "OutputCount";

    /** ★ 见 {@link #KEY_OUTPUT_ID}：小节标题「各线程产物：」的语言键（中英各一条，见两份 lang）。 */
    private static final String LANG_OUTPUTS_HEADER = "gtlmodular.nanochip.jade.thread_outputs";

    /** ★ 见 {@link #KEY_OUTPUT_ID}：一行 {@code 线程N：250 × [名]}（3 个占位符：序号 / 数量 / 名）。 */
    private static final String LANG_OUTPUTS_LINE = "gtlmodular.nanochip.jade.thread_outputs.line";

    /** ★ 见 {@link #KEY_OUTPUT_ID}：这一条线程没有可显示的第一个物品产物（1 个占位符：序号）。 */
    private static final String LANG_OUTPUTS_NONE = "gtlmodular.nanochip.jade.thread_outputs.none";

    /**
     * ★★ 2026-11-05【GTCEu 风格横向分割线 · 文本】★★
     *
     * <h4>队长原话（逐字）</h4>
     * <blockquote>「然后你那个图标的问题，<b>山海说向 gtceu 那样写个分割线</b>」</blockquote>
     *
     * <h4>GTCEu 到底怎么画（取证，不是猜）</h4>
     * GTCEu 的分割线<b>不是</b> spacer / box 一类的版式元素，而是<b>一行纯文本</b>：
     * <ul>
     *   <li><b>值 = 25 个 {@code =}</b>：GTCEu 自带语言文件逐字
     *       {@code "behavior.portable_scanner.divider": "========================="}
     *       —— {@code _census/_probe_nacmat_evidence/lang/gtceu-1.20.1-1.4.4__gtceu__en_us.json:19}，
     *       且 {@code ...__zh_cn.json:19}、{@code ...__zh_tw.json:19}、{@code ...__ja_jp.json:19}、
     *       {@code ...__en_ud.json:19}、{@code ...__ru_ru.json:6547} <b>六种语言同一个值</b>
     *       ⇒ 这个串本来就跟语言无关，<b>逐字照抄它即可</b>；</li>
     *   <li><b>画法 = 往行列表里加一行该文本</b>：{@code PortableScannerBehavior#addScannerInfo}
     *       （方法名 {@code classstr_gtceu-1.20.1-1.4.4.txt:34567}，签名
     *       {@code (Player, Level, BlockPos, DisplayMode, List<Component>)I} 见同文件
     *       {@code :34568}／{@code :34575}）往 {@code List<Component>} 里加一行，
     *       该键就在同一个类的常量池 {@code :34584}（{@code !behavior.portable_scanner.divider}，
     *       首字节 {@code 0x21} = 33 = 键长）
     *       ⇒ 与「{@code tooltip.add(Component)} 加一行」<b>同一种形状</b>，没有新奇 API；</li>
     *   <li><b>反证（不许自己发明）</b>：GTCEu / gtlcore / gtladditions 三份常量池转储里
     *       <b>一个 {@code spacer} 都没有</b>（在本工作区 {@code _census} 下<b>全部</b> {@code .txt}
     *       常量池转储里 grep {@code spacer|Spacer} ⇒ <b>零命中</b>）；GTCEu 的 Jade
     *       {@code RecipeOutputProvider} 常量池
     *       （{@code classstr_gtceu-1.20.1-1.4.4.txt:50893-50911}：{@code OutputItems} /
     *       {@code addItemTooltips} / {@code FluidTextHelper} …）里也<b>没有任何分割线字样</b>
     *       ⇒ GTCEu 在 Jade 里没有别的「分割线」画法，本条就是它的画法。</li>
     * </ul>
     *
     * <h4>为什么这里内联 {@code Component.literal} 而不是用翻译键</h4>
     * 本轮允许改动的文件<b>不含两份 lang</b> ⇒ <b>不能</b>新增键；而 GTCEu 那个键在六种语言里
     * 值<b>完全相同</b>（行号见上）⇒ 直接内联<b>同一个串</b>，玩家看到的字符与 GTCEu
     * <b>逐字相同</b>，也不依赖第三方的键是否还在（缺键会显示键名，那比少一条线更难看）。
     * 字符串<b>一个字没改</b>，也不是新发明的字符。
     *
     * <h4>宽度（诚实边界）</h4>
     * 25 个 {@code =} 的像素宽度 ≈ 12 个半 CJK 字，而本提示里既有的
     * {@code 线程0：62 × [封装 流形振荡电池]} 这类行更宽 ⇒「提示框宽度 = 最宽的一行」，
     * 本条<b>不会</b>把框撑宽。此判断未在游戏里目视复核（本轮不能编译/不能启动）⇒ 见交付里的【未取证】。
     */
    private static final String GTCEU_DIVIDER_TEXT = "=========================";

    /**
     * 服务端：把线程状态写进 Jade 的 serverData。
     *
     * <p>写根 tag（{@code data}）而不是子 tag：客户端在 {@link #appendTooltip} 里读的是同一个对象，
     * 判定「这是不是我们的机器」也就有了可靠依据（见 {@link NanochipJadeKeys#KEY_THREAD_LIMIT}）。
     *
     * <p>整段包在 {@code try/catch(Throwable)} 里：这个方法的调用者是 Jade，
     * 任何意外（部件被拆 / 配方对象被数据包换掉）都只该表现为「这次不显示」，
     * <b>绝不允许</b>把玩家的准星提示、更不允许把服务端主线程掀翻。
     */
    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        try {
            if (data == null || accessor == null) {
                return;
            }
            NanochipThreadedRecipeLogic logic = threadedLogic(accessor);
            if (logic == null) {
                return;
            }
            int limit = logic.getThreadLimit();
            // 无条件写标记（包括 limit <= 1 的直通档）：客户端的判据是「键存在」= 这是我们的机器，
            // 用它把「我们的机器」与「别人的多线程机器」分开（理由见 NanochipJadeKeys 的类注释）。
            data.putInt(NanochipJadeKeys.KEY_THREAD_LIMIT, limit);
            int running = logic.runningThreadCount();
            data.putInt(NanochipJadeKeys.KEY_THREAD_RUNNING, running);
            // ★★ 0.3.132【① 读数：K 条 ÷ K 种】★★ —— 把「本刻在跑的批次里，按配方 id 去重后有几种
            //   **不同**配方」写进**我们自己的** serverData（取值 = 只读 getter，口径见
            //   NanochipThreadedRecipeLogic#distinctRunningRecipeCount() 的 javadoc）。
            //   · 拿不到线程逻辑 ⇒ 本方法开头的 `logic == null` 已经 return ⇒ **一个键都不写**，
            //     客户端因此不显示这一行（**绝不伪造**、绝不写一个猜出来的数）。
            //   · 恒写（一条没跑时也写 0）：键在 = 「服务端确实读到了本机的批次表」，
            //     与「不是我们的机器 / 旧版服务端」（键缺失）分开 —— 与 KEY_THREAD_LIMIT 的用法同一口径。
            //   · 它只是读数：不改状态、不进存档、不参与任何判定。
            //   ★ 一行回退：把下面这一行 `data.putInt(KEY_DISTINCT_RECIPES, ...);` 删掉
            //     （客户端 appendDistinctRecipes 见到键缺失即整行不显示）⇒ 服务端回到改动前。
            data.putInt(KEY_DISTINCT_RECIPES, logic.distinctRunningRecipeCount());
            data.putLong(NanochipJadeKeys.KEY_THREAD_RUNS, logic.totalRunsAtOnce());
            // ★ 2026-11-02【沿用原生进度条】：告诉客户端「原生那条进度条代表哪一条线程」
            //   （服务端 step ④ 把批次表第 0 条镜像进基类单进度字段 ⇒ 原生条 = 第 0 条）。
            //   客户端据此把这一条从「追加的条」里排除，并在原生条下面补一行说明。
            data.putInt(NanochipJadeKeys.KEY_MIRRORED_SLOT, logic.mirroredThreadLabel());
            data.putLong(NanochipJadeKeys.KEY_MIRRORED_PARALLELS, logic.mirroredThreadParallels());
            // ★★ 0.3.132【Jade 校准折减读数】★★ 把「本机校准实际折减了多少」写进**我们自己的** serverData。
            //   · 为什么不能靠上游那两行「总耗能倍率 / 总耗时倍率」：它们读 gtlcore 自己的 reduction 字段，
            //     而我们的校准走 RecipeModifier（见 KEY_CALIB_EU_MUL 的注释）⇒ 那两行与校准无关、恒 100%。
            //   · 拿不到机器 / 不是本机 ⇒ 一个键都不写（客户端因此不显示这一行，**绝不伪造** 100%）。
            //   · 未成形由主机那两个 getter 内部处理（返回 1.0 ⇒ 照实显示「−0.0％」，不是不显示）。
            writeCalibrationReduction(data, accessor);

            ListTag list = new ListTag();
            for (Map.Entry<Integer, NanochipThreadedRecipeLogic.ThreadRec> entry
                    : snapshotThreads(logic)) {
                list.add(threadTag(entry.getKey(), entry.getValue()));
            }
            data.put(NanochipJadeKeys.KEY_THREAD_LIST, list);
        } catch (Throwable t) {
            // 只记一条诊断（限频交给日志框架的等级过滤），显示端下一 tick 会再试
            GTLModular.LOGGER.debug("[gtlmodular] NAC Jade 线程状态写入失败（本次跳过）：{}", t.toString());
        }
    }

    /**
     * 客户端：只按 serverData 里的数字拼文本，不读服务端对象。
     *
     * <p><b>唯一的闸</b>：{@link NanochipJadeKeys#KEY_THREAD_LIMIT} 不在 ⇒ 不是我们的机器
     * （或服务端没写），直接收手。
     *
     * <p>★ <b>2026-11-02（本轮形态）：本 provider 画「总览两行 + 每条线程一组 标签/条/并行 + 等价并行口径行」</b>——
     * 条不再来自 GTCEu 原生 provider（它读机器基类的单进度字段，只能反映一条），
     * 而是按 serverData 的批次列表<b>逐条</b>画（{@link NanochipJadeThreadRows#appendAllRows}），
     * <b>列表里有一条就画一组</b>（上限 {@link NanochipJadeKeys#DISPLAY_LINES} = 3 条线程，
     * 超出用一行交代；条内文字 = GTO 那种 {@code 2/4 s}）。
     * 混入 {@code MixinWorkableProgressBar} 只负责取消原生条（失败即退回原生条，不影响这里的条）。
     *
     * <p>⚠️ <b>故意没有「线程上限 ≤ 1 就不显示」这一道闸</b>（机器面板那边有）：
     * 本机<b>直通档的跨配方线程基线就是 1 条</b>，而那一条线程的进度由<b>我们自己的条</b>显示 ⇒
     * 这里再收手就会把「上限 / 在用」这条读数也丢掉。上限 = 1 时照常显示总览。
     */
    /**
     * ★★ 0.3.132【压掉 Jade 里上游那两行 · 实现】★★
     *
     * <h4>为什么是"重建列表"而不是 {@code tooltip.remove(...)}</h4>
     * Jade 的 {@code ITooltip.remove(ResourceLocation)} <b>只对"打过 tag"的行有效</b>，
     * 而上游那两行是**单参** {@code add(Component)}（**没有 tag**）——
     * 证据见 {@code ParallelProviderMixin.appendTooltip} 的字节码
     * （`:35`/`:139` 取键后走到 `:63`/`:101` 的 `ITooltip.add(Component)`，全程没有 tag 参数）。
     * ⇒ 只能按**文本**辨认，再把整个列表重建一遍。
     *
     * <h4>文本匹配的取舍（如实登记）</h4>
     * {@code IElement} <b>没有</b>"取回 Component"的 API（只有 {@code getMessage():String}），
     * 所以做不到主机面板里那种"按语言键剔行"。这里改用**形态模式**同时覆盖中英与数字格式：
     * <ul>
     *   <li>本模组键 {@code gtceu.multiblock.parallel}：中 {@code 同时处理至多<数字>个配方}、
     *       英 {@code Simultaneously processes up to <数字> recipes}；</li>
     *   <li>本模组键 {@code gtladditions.multiblock.threads}：中 {@code 拥有<数字>个跨配方线程}、
     *       英 {@code Has <数字> cross-recipe threads} / {@code ... threads}。</li>
     * </ul>
     * ⚠ <b>不会误删我们自己的行</b>：我们的 GTO 行是 {@code 同时处理至多 N 种不同配方}
     * （有"种不同"），而这里要求 {@code 个配方} 紧跟数字后 ⇒ 不匹配。
     *
     * <h4>纪律</h4>
     * · <b>只删这两条</b>，别的一行不碰（不认识的行原样、原顺序放回）；
     * · 任何异常 ⇒ 直接放弃本函数（{@code catch} 在调用处）⇒ 表现为"这次没压成"，绝不破tooltip；
     * · 一行回退：把调用处那行 {@code filterUpstreamLines(tooltip);} 删掉。
     */
    private static void filterUpstreamLines(ITooltip tooltip) {
        int size = tooltip.size();
        if (size <= 0) {
            return;
        }
        java.util.List<snownee.jade.api.ui.IElement> kept = new java.util.ArrayList<>(size);
        boolean dropped = false;
        for (int i = 0; i < size; i++) {
            // ⚠ `ITooltip.get(int, Align)` 返回的是 **List<IElement>**（一行可能由多段元素组成，
            //   例如「标签 + 值」两段）⇒ 这里按"整行"处理：取该行全部元素，全部保留或全部丢弃。
            java.util.List<snownee.jade.api.ui.IElement> row =
                    tooltip.get(i, snownee.jade.api.ui.IElement.Align.LEFT);
            if (row == null || row.isEmpty()) {
                continue;
            }
            StringBuilder joined = new StringBuilder();
            for (snownee.jade.api.ui.IElement part : row) {
                try {
                    String m = part == null ? null : part.getMessage();
                    if (m != null) {
                        joined.append(m);
                    }
                } catch (RuntimeException ignored) {
                    // 取不到文本 ⇒ 当作"不认识的行"，原样保留（绝不因为读不到就删）
                }
            }
            if (isUpstreamParallelLine(joined.toString())) {
                dropped = true;
                continue;      // ★ 命中 ⇒ 这一整行丢弃
            }
            kept.addAll(row);  // 未命中 ⇒ 原样、原顺序放回
        }
        if (!dropped) {
            return;   // 一条都没命中 ⇒ 一个字都不动（零观感变化）
        }
        tooltip.clear();
        // ★ 用 `add(List<IElement>)`（**不是** `addAll(List<Component>)`）——
        //   我们手上的是已经渲染好的 IElement（含对齐/尺寸/贴图），原样放回才不会变样。
        tooltip.add(kept);
    }

    /** 形态匹配：上游那两个键的中英两种说法（见 {@link #filterUpstreamLines} 的取舍说明）。 */
    private static boolean isUpstreamParallelLine(String message) {
        if (message.isEmpty()) {
            return false;
        }
        // 并行行：中「同时处理至多<数字>个配方」/ 英「Simultaneously processes up to <数字> recipes」
        if ((message.startsWith("同时处理至多") && message.endsWith("个配方"))
                || (message.startsWith("Simultaneously processes up to") && message.endsWith("recipes"))) {
            return true;
        }
        // 线程行：中「拥有<数字>个跨配方线程」/ 英（含 threads 的那个句式）
        if (message.startsWith("拥有") && message.endsWith("个跨配方线程")) {
            return true;
        }
        return message.contains("cross-recipe thread") || message.contains("cross recipe thread");
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        try {
            if (tooltip == null || accessor == null) {
                return;
            }
            CompoundTag data = accessor.getServerData();
            if (data == null || !data.contains(NanochipJadeKeys.KEY_THREAD_LIMIT)) {
                return;
            }
            // ★★ 0.3.132【★ 已撤回：Jade 压制方案（破坏性重建 tooltip）★★
            //   ⚠⚠ 队长实机截图证明这一步**把别的行也改坏了**：
            //     `纳米芯片组装复合体` / `结构已成型` / `ME网络库存: 2` **三行被并成一行**。
            //   原因：`ITooltip` **不只是"一列文本"** —— 每行由版式元素组构成，而
            //     `get(i, Align.LEFT)` 取回的粒度/分组与原行结构**未必一一对应**；
            //     我 `tooltip.clear()` + `tooltip.add(kept)` 等于**对别人的列表做破坏性重建**
            //     ⇒ 行结构被打散。
            //   为什么原本不该这么做：主机面板那个 `filterUpstreamParallelLines` 改的是
            //     **我们自己的** `List<Component>`（想怎么改都行）；而 Jade 的 `ITooltip`
            //     是 **Jade 的领域** —— 两件事性质完全不同，我把它们混为一谈了。
            //   ⇒ **现行做法：不碰列表**。要压只有两条正路（见 `_census/错误历史与工作经验.md`）：
            //     ① 用 mixin `@Inject` 到上游 `appendTooltip` 的写入点 + `ci.cancel()`（不碰列表）；
            //     ② 用 Jade 的 `IPluginConfig` 把上游那个 provider 关掉。
            //   ⇒ 本行以下那套 `filterUpstreamLines` 保留在文件里**仅供留档**，**不再调用**。
            // filterUpstreamLines(tooltip);
            int limit = data.getInt(NanochipJadeKeys.KEY_THREAD_LIMIT);
            int running = data.getInt(NanochipJadeKeys.KEY_THREAD_RUNNING);
            tooltip.add(Component.translatable(NanochipThreadedRecipeLogic.LANG_THREADS, limit, running));
            // ★★ 0.3.132【① 读数：K 条 ÷ K 种】★★ —— **紧跟上面那条线程总览行**追加一行
            //   （「在它之后立刻加」，不隔行、也不加到别处）：
            //   「本刻在跑 k 条线程 · d 种配方（按配方 id 去重）」⇒ k == d 才是真的在并行跑不同配方。
            //   ⚠ 纪律（B21）：这一段**只有 `tooltip.add(...)`**（实现见 appendDistinctRecipes），
            //     绝不 clear() / 绝不重建列表 / 绝不按 UID 或文本删任何既有行。
            //   ★ 位置说明（有意为之）：放在下面 `running <= 0` 那条早退分支**之前** ⇒
            //     「一条没跑」时这一行也照实显示「0 条 · 0 种」，且两个分支里都与总览行相邻；
            //     若把它藏进早退分支之后，「键在就显示」这条规则会随分支走样。
            //   ★ 一行回退：把下面这一行 `appendDistinctRecipes(tooltip, data);` 删掉 ⇒ 客户端回到改动前
            //     （其余一个字都不用动；再删掉服务端那一句 putInt 与两份 lang 的键即整条撤销）。
            appendDistinctRecipes(tooltip, data);
            if (running <= 0) {
                // 上限存在但一条没跑：照实说「在用 0」，不补「满载」这类话，也不画空进度条
                // ★ 0.3.132：校准折减那一行与线程跑没跑无关（它读的是校准历史，不是批次表）
                //   ⇒ 这条早退分支里也必须追加，否则「一条没跑时读数整行消失」= 撒谎。
                appendCalibrationReduction(tooltip, data);
                return;
            }
            tooltip.add(Component.translatable(NanochipThreadedRecipeLogic.LANG_THREADS_RUNS,
                    FormattingUtil.formatNumbers(data.getLong(NanochipJadeKeys.KEY_THREAD_RUNS))));
            // ★ 2026-11-02【本轮删除】原来紧跟在这里的一行「延迟说明」
            //   （`tooltip.add(Component.translatable(NanochipJadeKeys.LANG_LATENCY_NOTE))`）
            //   连同它的常量与两份 lang 文案已整条删除（用户明确要求：不需要那行说明）。
            //   条内文字与「等价并行」口径行都在 NanochipJadeThreadRows.appendAllRows 里，
            //   这里保持「一行总览 + 一行 R + 明细」的骨架不变。
            if (NanochipJadeThreadRows.OUR_OWN_BARS
                    || !NanochipJadeThreadRows.NATIVE_BAR_FOR_FIRST_THREAD) {
                // ★ 默认形态 = 自绘：**几条在跑的批次就画几条进度条**（上限
                //   NanochipJadeKeys.DISPLAY_LINES = 3 条；标签用列表下标 线程0/1/2（GTO 口径），
                //   超出的用一行「还有 M 条未列出」交代）。
                //   渲染在 NanochipJadeThreadRows.appendAllRows：服务端列表里有一条就 add 一条，
                //   一一对应、没有特判；这条路**不经过任何混入** ⇒ 混入失效也不会退化成「只有一条」。
                //   （另一支是回退形态：原生条不参与，也由这里自己画全部条。）
                // ★★ 2026-11-05【分割线 ①：在「逐条线程行」这一节之前】★★ ——
                //   队长授权的位置（原话：「至少是「各线程产物」那一节之前，如果合适也可用于
                //   「逐条线程行」之前」）。画法/取证见上面那个常量 GTCEU_DIVIDER_TEXT 的 javadoc。
                //   ★ 位置纪律：本行与 appendAllRows 同一支（同一条件）⇒ 明细不画时
                //     这里也不会单独留下一条线；「一条没跑」的早退在更上面（running <= 0）⇒ 同样不会。
                //   ★ 一行回退：删掉下面这一行 `appendGtceuDivider(tooltip);`（其余一个字不用动）。
                appendGtceuDivider(tooltip);
                NanochipJadeThreadRows.appendAllRows(tooltip, data);
            }
            // ★★ 2026-11-04【逐线程产物】★★ —— 紧跟逐条线程明细之后追加**一小节**「各线程产物」，
            //   一行一条已列出的线程（`线程0：250 × [封装 超因果RAM芯片]`）。这是队长那两句
            //   「得让 6 个产物都显示呢 / 每条线程的产物都得看到」的落地：上游那行 `配方输出` 读的是
            //   机器的"当前/上一次配方"，天生只反映一条；「每条线程各产什么」只有批次表知道。
            //   ⚠ 纪律（B21）：这一段**只有 `tooltip.add(...)`**（实现见 appendThreadOutputs），
            //     绝不 clear() / 绝不重建列表 / 绝不按 UID 或文本删任何既有行；上游那行一个字不碰。
            //   ★ 位置说明（有意为之）：放在逐条线程明细**之后**、校准折减那一行**之前**
            //     —— 「线程」相关的信息聚在一起，且对既有输出是纯追加（不打乱任何现有行）。
            //   ★ 一行回退：把下面这一行 `appendThreadOutputs(tooltip, data);` 删掉 ⇒ 客户端回到改动前
            //     （其余一个字都不用动；再删服务端那 5 行与两份 lang 的 3 个键即整条撤销）。
            appendThreadOutputs(tooltip, data);
            // ★★ 0.3.132【Jade 校准折减读数】★★ —— 在<b>原有内容之后</b>追加**一行**，
            //   回答队长那句「校准生效时看不到倍率变化」（根因见 KEY_CALIB_EU_MUL 的注释）。
            //   ⚠ 纪律：本方法**只允许 `tooltip.add(...)`**，绝不 `clear()` / 重建 / 按 UID 删行
            //     —— 那是别人的列表（本工程刚因此出过事故，见 `_census/错误历史与工作经验.md` B21）。
            appendCalibrationReduction(tooltip, data);
        } catch (Throwable t) {
            // 客户端显示失败只丢这一次提示，绝不抛给 Jade（抛出去会连带整条准星提示一起没了）
            GTLModular.LOGGER.debug("[gtlmodular] NAC Jade 线程状态显示失败（本次跳过）：{}", t.toString());
        }
    }

    /**
     * ★★ 0.3.132【Jade 校准折减读数 · 客户端出口】★★ —— 只按 serverData 里的两个数字
     * <b>追加一行</b>，不读服务端对象、<b>不碰任何已有行</b>。
     *
     * <h4>显示规则（逐条，队长口径）</h4>
     * <ul>
     *   <li><b>两个键都在</b> ⇒ 显示 {@code 校准折减：能耗 −X.X％ · 时长 −Y.Y％}
     *       （语言键 {@link #LANG_CALIB_REDUCTION}），其中
     *       {@code X = (1 − KalibEuMul) × 100}、{@code Y = (1 − KalibDurMul) × 100}；</li>
     *   <li><b>某个倍率恰好 = 1.0</b> ⇒ <b>照实显示「−0.0％」</b>（例如「能耗 −0.0％ · 时长 −20.0％」）：
     *       <b>不</b>自作聪明省略、<b>不</b>在没折减时把整行藏掉 —— 队长要看的正是「到底减没减」；</li>
     *   <li><b>唯一的「整行不显示」= 键不存在</b>（不是我们的机器 / 服务端取不到机器 / 旧版服务端）
     *       —— 边界①。</li>
     * </ul>
     *
     * <h4>纪律（B21 教训）</h4>
     * 本方法<b>只有 `tooltip.add(...)`</b>：<b>绝不</b> {@code tooltip.clear()}、绝不重建列表、
     * 绝不按 UID/文本删别人的行（{@code _census/错误历史与工作经验.md} B21 —— Jade 的
     * {@code ITooltip} 每行由版式元素组构成，破坏性重建会把别人的行并成一行）。
     *
     * <p>调用处包在本类 {@code appendTooltip} 的 {@code try/catch(Throwable)} 里 ⇒
     * 任何意外只表现为「这次没显示这一行」，不会掀翻整条准星提示。
     */
    private static void appendCalibrationReduction(ITooltip tooltip, CompoundTag data) {
        // 边界①：键缺失 ⇒ 整行不显示（不是我们的机器 / 服务端没写）。
        //   ★ 键的写入端是 writeCalibrationReduction(...)：拿不到机器时它**不写**，
        //     所以这里「键在」就等于「服务端确实读到了本机的校准读数」。
        if (!data.contains(KEY_CALIB_EU_MUL) || !data.contains(KEY_CALIB_DUR_MUL)) {
            return;
        }
        double euMul = data.getDouble(KEY_CALIB_EU_MUL);
        double durMul = data.getDouble(KEY_CALIB_DUR_MUL);
        // 折减量 = 1 − 倍率（1.0 ⇒ 0.0 折减 ⇒ 显示「−0.0％」，照实显示，不省略）。
        tooltip.add(Component.translatable(LANG_CALIB_REDUCTION,
                percentOfReduction(euMul), percentOfReduction(durMul)));
    }

    /**
     * ★★ 0.3.132【① 读数：K 条 ÷ K 种 · 客户端出口】★★ —— 只按 serverData 里的两个 {@code int}
     * <b>追加一行</b>，不读服务端对象、<b>不碰任何已有行</b>。
     *
     * <h4>显示规则（逐条）</h4>
     * <ul>
     *   <li><b>键 {@link #KEY_DISTINCT_RECIPES} 在</b> ⇒ 显示 {@code 本刻在跑 k 条线程 · d 种配方（按配方 id 去重）}
     *       （语言键 {@link #LANG_DISTINCT_RECIPES}），其中
     *       {@code k = KEY_THREAD_RUNNING}（条数）、{@code d = KEY_DISTINCT_RECIPES}（种数）
     *       —— <b>两个 {@code %s} 的顺序就是「条数、种数」</b>，与两份 lang 的文案一致，别对调；</li>
     *   <li><b>唯一的「整行不显示」= 键不存在</b>（不是我们的机器 / 服务端取不到线程逻辑 / 旧版服务端）
     *       —— 边界①。此时**绝不**拿 {@code KEY_THREAD_RUNNING} 之类去凑一个种数出来；</li>
     *   <li>种数 = 0 也<b>照实显示</b>（例如「0 条 · 0 种」）：本行是读数，不替玩家判断「该不该有」。</li>
     * </ul>
     *
     * <h4>纪律（B21 教训）</h4>
     * 本方法<b>只有 {@code tooltip.add(...)}</b>：<b>绝不</b> {@code tooltip.clear()}、绝不重建列表、
     * 绝不按 UID/文本删别人的行（{@code _census/错误历史与工作经验.md} B21 —— Jade 的 {@code ITooltip}
     * 每行由版式元素组构成，破坏性重建会把别人的行并成一行）。
     *
     * <p>调用处包在本类 {@code appendTooltip} 的 {@code try/catch(Throwable)} 里 ⇒
     * 任何意外只表现为「这次没显示这一行」，不会掀翻整条准星提示。
     */
    private static void appendDistinctRecipes(ITooltip tooltip, CompoundTag data) {
        // 边界①：键缺失 ⇒ 整行不显示（不是我们的机器 / 服务端没写）。
        //   ★ 键的写入端是 appendServerData 里那一句 putInt：拿不到线程逻辑时它**不写**，
        //     所以这里「键在」就等于「服务端确实读到了本机的批次表」。
        if (!data.contains(KEY_DISTINCT_RECIPES)) {
            return;
        }
        int threads = data.getInt(NanochipJadeKeys.KEY_THREAD_RUNNING);
        int distinct = data.getInt(KEY_DISTINCT_RECIPES);
        tooltip.add(Component.translatable(LANG_DISTINCT_RECIPES, threads, distinct));
    }

    /**
     * ★★ 2026-11-05【GTCEu 风格横向分割线 · 出口】★★ —— 加<b>一行</b>
     * {@link #GTCEU_DIVIDER_TEXT}，把我们的几节内容在视觉上切开。
     *
     * <h4>用的是「取证到的正版画法」，不是替代画法</h4>
     * 与 GTCEu 一致：<b>一行纯文本</b>（25 个 {@code =}）走「往行列表里加一行」这条路 ——
     * 取证（语言文件行号 / 常量池行号 / {@code spacer} 零命中的反证）逐条写在
     * {@link #GTCEU_DIVIDER_TEXT} 的 javadoc 里，此处不重复。
     * <b>唯一与 GTCEu 不同的地方</b>：GTCEu 用翻译键承载那个串，我们内联同一个串
     * （本轮不许改两份 lang，理由见 {@link #GTCEU_DIVIDER_TEXT}）。
     *
     * <h4>纪律（B21 教训）</h4>
     * 本方法<b>只有一句 {@code tooltip.add(Component)}</b>：<b>绝不</b> {@code tooltip.clear()}、
     * 绝不重建列表、绝不按 UID/文本删别人的行（{@code _census/错误历史与工作经验.md:294-348} B21：
     * Jade 的 {@code ITooltip} 每行由版式元素组构成，破坏性重建会把别人的行并成一行）。
     *
     * <p>调用处都包在本类 {@code appendTooltip} 的 {@code try/catch(Throwable)} 里 ⇒
     * 任何意外只表现为「这次没画这条线」，不会掀翻整条准星提示。
     *
     * @param tooltip 当前这条准星提示（<b>只 add 一行</b>，不读它的内容）
     */
    private static void appendGtceuDivider(ITooltip tooltip) {
        tooltip.add(Component.literal(GTCEU_DIVIDER_TEXT));
    }

    /**
     * ★★ 2026-11-04【Jade 逐线程产物 · 客户端出口】★★ —— 只按 serverData 里的
     * 「产物<b>物品注册名</b> + 数量」<b>追加一小节</b>，不读服务端对象、<b>不碰任何已有行</b>。
     *
     * <h4>★★ 2026-11-05【每一行行首加该产物的<b>物品图标</b>】（队长：<b>「能不能给这些现成产物加上图标」</b>）★★</h4>
     * 画法与上游两个真实先例<b>逐字同形</b>（见下「API 依据」）：
     * <pre>
     * 各线程产物：
     * [图标] 线程0：62 × [封装 流形振荡电池]
     * [图标] 线程1：62 × [封装 水晶矩阵板]
     * </pre>
     * <b>图标位置</b>：<b>在行首</b>，文字紧接在图标右侧<b>同一行</b>
     * （{@code tooltip.add(图标)} 开这一行 → {@code tooltip.append(文字)} 续在同一行）
     * —— 与 GTET 的 {@code ThreadedRecipeLogicProvider.appendOutputs} 的注释同款
     * （{@code _census/gtet-src/src/main/kotlin/rain/gtetcore/gtet/integration/jade/provider/ThreadedRecipeLogicProvider.kt:157-161}：
     * 「{@code add(...)} 起一行、{@code append(...)} 把后续内容续在<b>同一行</b>」；
     * 它那里用的是 {@code smallItem}，我们<b>本轮起换成非 small 的 {@code item}，见下</b>）。
     *
     * <h4>★★ 2026-11-17【图标改用<b>非 small</b> 的那个 API】（队长：<b>「而且你这个各个物品的图标也太小了吧」</b>）★★</h4>
     * 改前用的是 {@code IElementHelper.smallItem(ItemStack)} —— 名字里就写着「小」，
     * 它<b>不是</b>普通图标：Jade 的 {@code ElementHelper.smallItem} 实现逐字是
     * {@code item(stack, 0.5F).size(SMALL_ITEM_SIZE).translate(SMALL_ITEM_OFFSET).message(null)}，
     * 其中 {@code SMALL_ITEM_SIZE = new Vec2(10, 10)} ⇒ 队长看到的正是 <b>10×10</b> 的小图标。
     * 现改用<b>同一接口上的非 small 重载</b> {@code IElementHelper.item(ItemStack)}
     * （取证见下「API 依据」）⇒ 它走 {@code ItemStackElement.of(stack)}（scale = 1）
     * ⇒ {@code getSize() = floor(18 × scale) = <b>18×18</b>}（边长 1.8 倍、面积 3.24 倍）。
     * <p><b>诚实边界（行高会跟着变大）</b>：18×18 的图标会让<b>这一行</b>的高度由 10 px 变 18 px
     * （Jade 的 {@code Tooltip.Line.getSize()} 取该行元素高度的<b>最大值</b>；
     * 逐字见 {@code Tooltip.java} 的 {@code Line.getSize()}）⇒ 产物那一节会比改前略高。
     * 这正是队长要的「图标大一点」；<b>行数一条没多</b>（仍然一次线程一行：图标 + 文字同一行）。
     * 另：{@code ItemStackElement.getMessage()} 会返回「数量 + 名字」，但 Jade 的
     * {@code Line.render} <b>只渲染元素本身</b>，{@code getMessage()} 只被
     * {@code Tooltip.getMessage()}（非渲染路径）读到 ⇒ <b>不会</b>在图标旁多出一段文字。
     *
     * <h4>显示规则（逐条）</h4>
     * <ul>
     *   <li>列表键 {@link NanochipJadeKeys#KEY_THREAD_LIST} 不在 / 在用为 0 / 列表为空 ⇒
     *       <b>整节不显示</b>（连标题都不加）—— 边界①「无批次」；</li>
     *   <li>否则先加标题 {@link #LANG_OUTPUTS_HEADER}，再对列表里每条线程加一行
     *       {@link #LANG_OUTPUTS_LINE}（{@code 线程0：62 × [封装 流形振荡电池]}，<b>行首带该产物的物品图标</b>）——
     *       序号取<b>列表下标</b>，与逐条线程行的 {@code 线程N:} <b>同一个编号口径、同一份列表</b>
     *       ⇒ 产物与进度条一一对得上；</li>
     *   <li>某条线程没有「可显示的第一个物品产物」⇒ 照实写 {@link #LANG_OUTPUTS_NONE}
     *       （{@code 线程0：（无物品产物或未识别）}），<b>绝不跳过这一条</b>
     *       （跳过会让玩家以为这条线程不存在）、<b>绝不</b>拿别的线程的产物顶上、<b>绝不</b>编一个数量；</li>
     *   <li><b>★ 图标拿不到时退化成纯文字行</b>（注册名解析失败 / 物品不存在 / 空气 /
     *       重建 ItemStack 抛异常）：<b>只少那个图标</b>，文字行<b>照旧显示</b>
     *       （与没有图标元素的形态逐字相同）—— 绝不因为画不出图标就把整行吞掉；</li>
     *   <li>截断：最多列 {@link NanochipJadeKeys#OUTPUT_LINES} 条（= 6，<b>本节的独立上限</b>；
     *       进度条那节仍是 {@link NanochipJadeKeys#DISPLAY_LINES} = 3 —— 两节<b>不共用</b>上限），
     *       两节读的是<b>同一份</b> serverData 列表，超出的用<b>既有键</b>
     *       {@link NanochipJadeThreadRows#LANG_MORE_UNLISTED} 如实交代 M = 在用 − 已列条数
     *       —— 与既有线程行的截断口径同一个 M（不是新发明的口径）。</li>
     * </ul>
     *
     * <h4>译名为什么在客户端取（★ 本键的载荷语义已从「本地化键」改成「物品注册名」）</h4>
     * 服务端送的是 item 的<b>物品注册名</b>
     * （{@code BuiltInRegistries.ITEM.getKey(stack.getItem())}，形如 {@code gtlmodular:cc_crystal_processor}；
     * 取值端与语义变更理由见 {@link NanochipThreadedRecipeLogic#firstItemOutputId}）——
     * 专用服务器上<b>没有</b>客户端的语言，在服务端取名字只会得到键本身；而送 ItemStack 全量 NBT
     * 会把载荷翻好几倍（见 {@link #threadTag} 的估算）。客户端这里：
     * ① 由注册名重建 {@link ItemStack}（{@code itemIconOf(ITooltip, String)}，源内 javadoc 见本类
     * 那个方法）——<b>只为画图标</b>；
     * ② 译名走 {@link #displayNameOf(String)}（{@code Component.translatable(注册名对应的
     * getDescriptionId())} + 兜底）取<b>玩家语言</b>的译名再嵌进我们的文案。
     * {@code stack.getDescriptionId()} 得到的串与改动前服务端送出的本地化键<b>逐字相同</b>
     * ⇒ <b>实机文案一个字都不变</b>（队长说「把现成产物加上图标」，那就只加图标）。
     * <b>诚实边界</b>：产物名取的是物品的<b>基础名</b>——铁砧改名 / 自定义名
     * （{@code stack.getHoverName()} 那一层）<b>不</b>反映在这里（那要送整栈 NBT，与载荷纪律冲突）；
     * 键在本客户端语言里查不到（或该键带占位符）时显示的就是<b>键本身</b>（不编一个名字）。
     *
     * <h4>API 依据（逐条取证，不许猜）</h4>
     * <ul>
     *   <li>{@code ITooltip.getElementHelper() → IElementHelper}：<b>本包既有编译在跑的用法</b>
     *       {@code NanochipJadeThreadRows.java:507}／{@code :510}
     *       （{@code tooltip.getElementHelper().progress(...)}）；上游同样用法
     *       {@code classstr_gtceu-1.20.1-1.4.4.txt:50699}（{@code AutoOutputBlockProvider} 常量池
     *       {@code getElementHelper} + {@code smallItem}）、
     *       {@code classstr_gtlcore-1.2.3.2-fix1.txt:36320-36325}（gtlcore {@code MEMAIOProvider}
     *       {@code getElementHelper():()Lsnownee/jade/api/ui/IElementHelper;} + {@code smallItem(ItemStack)→IElement}）；</li>
     *   <li><b>★ 2026-11-17【图标 API 换成非 small 的那个】—— 三重逐字依据</b>
     *       <ol>
     *         <li><b>本地字节码</b>（本工作区里已有的一份 Jade 接口字节码
     *             {@code _census/_jade_probe/snownee/jade/api/ui/IElementHelper.class}，
     *             用 {@code grep} 探针命中；⚠ 该探针目录的<b>来源与版本号已标【未取证】</b>
     *             —— 版本身份以下面那条<b>同版本源码</b>为准，本条只用来证明「这些名字/描述符
     *             确实同时存在于一份 Jade 的 IElementHelper 字节码里，不是我从源码里臆造的」）：常量池里存在长度为 4 的 Utf8 项 {@code item}
     *             （{@code \x04item} 命中）、长度为 9 的 {@code smallItem}（{@code \x09smallItem} 命中），
     *             以及描述符 {@code (Lnet/minecraft/world/item/ItemStack;)Lsnownee/jade/api/ui/IElement;}
     *             与 {@code (Lnet/minecraft/world/item/ItemStack;F)Lsnownee/jade/api/ui/IElement;}
     *             （<b>两项都命中</b>）⇒ <b>接口上确实有一族 {@code item} 重载（含带尺寸参数的那个），
     *             而且 {@code item} 不是 {@code smallItem} 的别名</b>（两个名字各自是独立常量池项）；</li>
     *         <li>{@code cdn.jsdelivr.net/gh/Snownee/Jade@forge-11.13.3}（与本工程
     *             {@code gtl-modular/libs/Jade-1.20.1-Forge-11.13.3.jar} 的<b>文件名版本号一致</b>；
     *             ⚠ 「文件名版本号 = jar 内字节码版本」这一层<b>未取证</b>）的
     *             {@code src/main/java/snownee/jade/api/ui/IElementHelper.java}
     *             原文（HTTP 200，逐字）：
     *             <pre>
     * IElement item(ItemStack itemStack);
     * IElement item(ItemStack itemStack, float scale);
     * IElement item(ItemStack itemStack, float scale, @Nullable String text);
     * IElement smallItem(ItemStack itemStack);</pre>
     *             ⇒ <b>「非 small 的那个版本」= {@code item(ItemStack)}</b>（本轮采用的那个）；</li>
     *         <li>同 tag 的 {@code src/main/java/snownee/jade/impl/ui/ElementHelper.java} 原文（逐字）：
     *             <pre>
     * public static final Vec2 SMALL_ITEM_SIZE = new Vec2(10, 10);
     * public IElement item(ItemStack stack) { return ItemStackElement.of(stack); }
     * public IElement smallItem(ItemStack stack) {
     *     return item(stack, 0.5F).size(SMALL_ITEM_SIZE).translate(SMALL_ITEM_OFFSET).message(null);
     * }</pre>
     *             与同 tag 的 {@code impl/ui/ItemStackElement.java}（逐字）：
     *             <pre>
     * public static ItemStackElement of(ItemStack stack) { return of(stack, 1); }
     * public Vec2 getSize() { int size = Mth.floor(18 * scale); return new Vec2(size, size); }</pre>
     *             ⇒ <b>{@code smallItem} = 0.5 倍 + 强制 10×10；{@code item(stack)} = scale 1 ⇒ 18×18</b>
     *             （尺寸差异是队长那句「图标也太小了吧」的直接成因）。</li>
     *       </ol></li>
     *   <li>{@code ITooltip.add(IElement)} / {@code ITooltip.append(Component)}：
     *       {@code NanochipJadeThreadRows.java:507}（{@code tooltip.add(<IElement>)}）与
     *       {@code NanochipJadeThreadRows.java:519}（{@code tooltip.append(Component.translatable(...))}）
     *       —— 都是<b>本包已经在跑的代码</b>；本轮调用形态<b>一字未改</b>，只换了那个图标构造方法。</li>
     * </ul>
     *
     * <h4>纪律（B21 教训）</h4>
     * 本方法<b>只用 {@code tooltip.add(Component)} / {@code tooltip.add(IElement)} /
     * {@code tooltip.append(Component)}</b>：<b>绝不</b> {@code tooltip.clear()}、绝不重建列表、
     * 绝不按 UID/文本删任何既有行（{@code _census/错误历史与工作经验.md} B21 —— Jade 的
     * {@code ITooltip} 每行由版式元素组构成，破坏性重建会把别人的行并成一行）。
     * <b>上游那行 {@code 配方输出} 一个字都不碰</b>（它不归我们管，改它 / 注掉它都是违纪）。
     *
     * <p>调用处包在本类 {@code appendTooltip} 的 {@code try/catch(Throwable)} 里 ⇒
     * 任何意外只表现为「这次没显示这一节」，不会掀翻整条准星提示。
     */
    private static void appendThreadOutputs(ITooltip tooltip, CompoundTag data) {
        // 边界①【无批次】：列表键不在（不是我们的机器 / 旧版服务端）/ 一条没跑 / 列表为空 ⇒ 整节不显示。
        if (!data.contains(NanochipJadeKeys.KEY_THREAD_LIST)) {
            return;
        }
        int running = data.getInt(NanochipJadeKeys.KEY_THREAD_RUNNING);
        if (running <= 0) {
            return;
        }
        ListTag list = data.getList(NanochipJadeKeys.KEY_THREAD_LIST, Tag.TAG_COMPOUND);
        // ⚠ 用 {@code size() <= 0} 而不是 {@code isEmpty()}：{@code ListTag} 这里只保证
        //   {@code size()} / {@code getCompound(int)}（同包既有用法见
        //   {@code NanochipJadeThreadRows:306,349}）。
        if (list.size() <= 0) {
            return;
        }
        // ★★ 2026-11-05【分割线 ②：在「各线程产物」这一节之前（队长指定）】★★ ——
        //   画法/取证见上面那个常量 GTCEU_DIVIDER_TEXT 的 javadoc。
        //   ★ 位置纪律：放在**上面四条早退全部走完之后**（键不在 / 一条没跑 / 列表空 ⇒ 早就 return）
        //     ⇒ 整节不显示时也不会单独留下一条孤零零的分割线。
        //   ★ 一行回退：删掉下面这一行 `appendGtceuDivider(tooltip);`（其余一个字不用动）。
        appendGtceuDivider(tooltip);
        tooltip.add(Component.translatable(LANG_OUTPUTS_HEADER));
        int listed = 0;
        for (int i = 0; i < list.size() && listed < NanochipJadeKeys.OUTPUT_LINES; i++) {
            CompoundTag entry = list.getCompound(i);
            // 边界②③④⑤【产物取不到 / 产物是流体 / ItemStack 为空 / Ingredient 未解析】：
            //   键缺失时 getString 给 ""、数量缺失给 0 ⇒ 任一不成立就走「未识别」那一行
            //   （服务端**只在真的取到物品产物时**才写这两个键 ⇒ "键在"就等于"真取到了"）。
            String outputId = entry.getString(KEY_OUTPUT_ID);
            long count = entry.getLong(KEY_OUTPUT_COUNT);
            if (outputId.isEmpty() || count <= 0L) {
                tooltip.add(Component.translatable(LANG_OUTPUTS_NONE, i));
            } else {
                // ★★ 2026-11-05【行首物品图标】／2026-11-17【换成非 small 的大图标】★★
                //   画法与上游先例同款（见方法注释「API 依据」）：
                //   ① tooltip.add(图标) 开这一行；② tooltip.append(文字) 续在**同一行**右侧
                //   ⇒ 「图标 + 文字」一行，**行数**与改动前完全相同（只是图标由 10×10 变 18×18）。
                //   ⚠ 图标拿不到（注册名解析失败 / 物品不存在 / 空气 / 重建抛异常）时 itemIconOf 返回
                //     null ⇒ **只少图标**，下面那行文字**照旧**显示（绝不因为画不出图标就吞掉整行）。
                IElement icon = itemIconOf(tooltip, outputId);
                if (icon != null) {
                    tooltip.add(icon);
                }
                // 数量口径：服务端写的就是「内容数量（**已含并行 p**）」，这里只做千分位格式化，
                // **绝不再乘并行**（那正是本轮修过的 ×p² 缺陷）。
                Component line = Component.translatable(LANG_OUTPUTS_LINE, i,
                        FormattingUtil.formatNumbers(count),
                        displayNameOf(outputId));
                if (icon != null) {
                    // 同一行续写：`add(IElement)` 起行 + `append(Component)` 续行 —— 见方法注释。
                    tooltip.append(line);
                } else {
                    // 退化成改动前的形态：纯文字独占一行（一个字都没变）。
                    tooltip.add(line);
                }
            }
            listed++;
        }
        // 边界⑥【超过列出上限】：最多列 OUTPUT_LINES 条（= 6，**本节的独立上限**；进度条那节是 DISPLAY_LINES = 3）；
        //   还有 M 条没列就用既有那一行交代（键与 M 的口径与逐条线程行完全同源：M = 在用总数 − 已列条数）。
        if (listed < running) {
            tooltip.add(Component.translatable(NanochipJadeThreadRows.LANG_MORE_UNLISTED, running - listed));
        }
    }

    /**
     * ★★ 2026-11-05【新增：物品注册名 → 该产物的物品图标元素】★★ —— {@link #appendThreadOutputs} 用。
     *
     * <h4>它干什么（三步，全部是 vanilla / Jade 的公共 API）</h4>
     * <ol>
     *   <li>{@code ResourceLocation.tryParse(id)} —— 解析服务端送来的<b>注册名</b>
     *       （{@code namespace:path}；本包同款解析先例 {@code NanochipConcurrentBatches.java:312}）；</li>
     *   <li>{@code BuiltInRegistries.ITEM.get(rl)} —— 静态注册表反查 {@code Item}
     *       （本包同款反查先例 {@code LazyPatternArchiveApi.java:394-398}，那里也是
     *       {@code get(...)} 后判 null 再兜底）；</li>
     *   <li>{@code IElementHelper.item(new ItemStack(item))} —— 建<b>行内</b>物品图标元素
     *       （签名 {@code (Lnet/minecraft/world/item/ItemStack;)Lsnownee/jade/api/ui/IElement;}
     *       逐字见 {@code _census/_probe_nacmat_evidence/classstr_gtceu-1.20.1-1.4.4.txt:50700-50701}
     *       与 {@code classstr_gtlcore-1.2.3.2-fix1.txt:36323-36324}）。
     *       ★ 2026-11-17：<b>不再用 {@code smallItem}</b>（它是 0.5 倍 + 强制 10×10 的「小物品」，
     *       队长嫌小），改用<b>同一接口上的非 small 重载 {@code item(ItemStack)}</b>（18×18）
     *       —— 取证与尺寸差异逐字见 {@link #appendThreadOutputs} 的「API 依据」。
     *       <b>helper 从传进来的那个 tooltip 上取</b>
     *       （{@code ITooltip.getElementHelper() → IElementHelper}，本包编译在跑的证据：
     *       {@code NanochipJadeThreadRows.java:507}/{@code :510}），不自己 new、不碰全局单例。</li>
     * </ol>
     *
     * <h4>边界（全部返回 {@code null} = 「这次不画图标」，<b>绝不抛</b>）</h4>
     * <ul>
     *   <li><b>注册名解析失败</b>（空串 / 含非法字符 / 非 {@code namespace:path}）⇒
     *       {@code tryParse} 给 {@code null} ⇒ 返回 {@code null}；</li>
     *   <li><b>物品不存在</b>（未注册 / 被数据包换掉）⇒ {@code ITEM.get(...)} 给 {@code null}
     *       ⇒ 返回 {@code null}（<b>绝不</b>退回 {@code Items.AIR} 画一个空图标 —— 那会让玩家
     *       以为这条线程产的是空气）；</li>
     *   <li><b>空气</b>⇒ {@code new ItemStack(Items.AIR).isEmpty()} 为真 ⇒ 返回 {@code null}；</li>
     *   <li><b>产物是流体</b>：根本走不到这里 —— 服务端 {@code firstItemOutputContent} 只读
     *       {@code ItemRecipeCapability.CAP}，纯流体产物的线程<b>不写</b> {@link #KEY_OUTPUT_ID}
     *       ⇒ 上面走的是 {@link #LANG_OUTPUTS_NONE} 那一行（没有图标可画，也不该画）；</li>
     *   <li><b>拿不到 {@code ItemStack}</b>（构造 / 元素工厂抛异常、Jade helper 取不到）⇒
     *       {@code catch (Throwable)} ⇒ {@code null}。</li>
     * </ul>
     *
     * <p><b>为什么整段吞异常</b>：本方法的调用者在 {@link #appendTooltip} 里，但那层
     * {@code catch(Throwable)} 是<b>整节</b>的兜底 —— 一旦图标这里抛出去，队长要的
     * 「每条线程的产物都得看到」会连文字一起丢。所以这里<b>就地</b>把「画不出图标」
     * 收敛成 {@code null}，让调用方退化成一个字的都不少的纯文字行。
     *
     * <p>⚠ <b>不做并发/缓存优化</b>：一次提示最多 {@link NanochipJadeKeys#OUTPUT_LINES} = 6 条线程
     * ⇒ 最多 6 次解析 + 6 次构造，
     * 且本方法<b>只在客户端渲染时</b>被调用，不进服务端 tick、不进存档。
     *
     * @param tooltip  当前这条准星提示（只为取 {@code getElementHelper()}，<b>不读不改</b>它的内容）
     * @param outputId 服务端送来的物品注册名（{@code namespace:path}）；{@code null} / 空串 ⇒ {@code null}
     * @return 该产物的行内物品图标元素；{@code null} = 画不出（见上「边界」）
     */
    private static IElement itemIconOf(ITooltip tooltip, String outputId) {
        if (outputId == null || outputId.isEmpty()) {
            return null;
        }
        try {
            // ① 注册名 → ResourceLocation（解析不了给 null，不抛）
            ResourceLocation id = ResourceLocation.tryParse(outputId);
            if (id == null) {
                return null;
            }
            // ② ResourceLocation → Item（静态注册表；查不到给 null）
            net.minecraft.world.item.Item item = BuiltInRegistries.ITEM.get(id);
            if (item == null) {
                return null;
            }
            // ③ Item → ItemStack（空气这一道也在这里被挡住；count = 1，
            //    数量由那一行文字里的「N ×」负责显示，图标只画「是哪个物品」）。
            ItemStack stack = new ItemStack(item);
            if (stack.isEmpty()) {
                return null;
            }
            // ④ ItemStack → 行内图标元素（行首那一格）
            //    ★ 2026-11-17：改用**非 small 重载** `item(ItemStack)`（= 18×18）。
            //      改前的 `smallItem(ItemStack)` 是 Jade 的「小物品」（0.5 倍 + 强制 10×10 尺寸），
            //      队长说「而且你这个各个物品的图标也太小了吧」⇒ 换成同一接口上那个大的。
            //      取证（本地字节码 + 同版本源码逐字）见 appendThreadOutputs 的「API 依据」；
            //      调用形态（add(IElement) 开行 + append(Component) 续行）**一字未改**。
            //    helper 从**同一个 tooltip** 上取（本包既有编译在跑的写法：
            //    NanochipJadeThreadRows.java:507/:510 的 `tooltip.getElementHelper().progress(...)`）。
            IElementHelper helper = tooltip.getElementHelper();
            return helper == null ? null : helper.item(stack);
        } catch (Throwable t) {
            // 任何意外（注册表异常 / 元素工厂异常）都只表现为「这一行没有图标」，绝不连累那行文字。
            return null;
        }
    }

    /**
     * item 的<b>本地化键</b> → <b>玩家语言</b>的显示名（{@link #appendThreadOutputs} 用）。
     *
     * <p>★ 2026-11-05【语义变更】：本方法的入参<b>仍然是「本地化键」</b>（一个字没改），
     * 变的是<b>调用链</b>——服务端现在送的是「物品注册名」（理由见 {@link #KEY_OUTPUT_ID} 与
     * {@link NanochipThreadedRecipeLogic#firstItemOutputId}），所以调用方
     * （{@link #appendThreadOutputs}）走的是与它<b>同名</b>的那个入口
     * {@link #displayNameOf(String)}：先把注册名换成
     * {@code stack.getDescriptionId()}（= 与改动前服务端送出的串<b>逐字相同</b>）再交给这里
     * ⇒ <b>实机文案一个字都不变</b>（队长只说「给现成产物加上图标」）。
     *
     * <p>具体到这里：本方法<b>只认本地化键</b>（不做任何注册名解析）——
     * 「注册名 → 本地化键」那一步由 {@link #descriptionIdOfRegistryName(String)} 负责，
     * 两条路分开是为了让失败语义各自清楚（见那个方法的 javadoc）。
     *
     * <p>为什么在客户端译而不是在服务端取名字：专用服务器上<b>没有</b>客户端的语言
     * （在服务端 {@code getHoverName().getString()} 只会得到 lang 键本身）⇒ 服务端只送注册名。
     *
     * <p><b>兜底</b>：键在本客户端语言里查不到、或该键带占位符（少数 GT 物品的名字带 {@code %s}，
     * 例如各种流体单元）⇒ 照实返回<b>键本身</b>，绝不编一个名字；<b>也绝不</b>让这一条把整段提示
     * 带崩（这里抛出去会被 {@code appendTooltip} 的 catch 吞掉 ⇒ 表现成"连进度条一起没了"，
     * 那是更差的结局）。
     */
    private static String translatedNameOf(String descriptionId) {
        try {
            return Component.translatable(descriptionId).getString();
        } catch (RuntimeException e) {
            return descriptionId;
        }
    }

    /**
     * ★★ 2026-11-05【产物译名入口：兼容「物品注册名」与「本地化键」两种入参】★★
     * —— {@link #appendThreadOutputs} 用的就是这个入口。
     *
     * <p><b>它怎么同时容忍两种入参</b>（为什么要容忍）：
     * <ul>
     *   <li>入参是<b>注册名</b>（本键的现行语义，{@code namespace:path}）⇒ 先
     *       {@link #descriptionIdOfRegistryName(String)} 把注册名换成该物品的
     *       {@code getDescriptionId()}（= 本地化键），再交给 {@link #translatedNameOf(String)}
     *       译名 ⇒ 与改动前<b>逐字相同</b>；</li>
     *   <li>入参是<b>旧版服务端</b>送来的本地化键（{@code item.gtlmodular.cc_crystal_processor}）
     *       ⇒ {@link #descriptionIdOfRegistryName(String)} 返回 {@code null}
     *       （{@code tryParse} 解析不出 {@code namespace:path}，或注册表里没有这个名字的物品）
     *       ⇒ 直接把它交给 {@link #translatedNameOf(String)} 译名
     *       ⇒ 跨版本（客户端新 / 服务端旧）也不会把译名显示成注册名。这条<b>只为向后兼容</b>，
     *       不是新口径。</li>
     * </ul>
     * <p>⚠ 两条支路都<b>先取字符串</b>再嵌进我们的文案（不把 Component 当 {@code %s} 参数），
     * 是为了让文案里的 {@code §} 颜色代码对译名一样生效（与 {@link #translatedNameOf(String)} 同款：
     * 不依赖"嵌套 Component 参数"的样式继承）。
     *
     * <p>⚠ 本方法与 {@link #translatedNameOf(String)} <b>是两条不同的方法</b>（曾经同名 ⇒ 编译失败
     * 「method displayNameOf(String) is already defined」，且那条同名版本还会**自递归**）：
     * 本方法 = **入口**（认注册名），{@code translatedNameOf} = **只做翻译**（认本地化键）。
     *
     * @param outputId 服务端送来的产物标识（现行 = 物品注册名；旧版服务端 = 本地化键）
     * @return 玩家语言的译名；取不到就照实回落（键本身 / 注册名），绝不编名字
     */
    private static String displayNameOf(String outputId) {
        String byRegistry = descriptionIdOfRegistryName(outputId);
        return translatedNameOf(byRegistry == null ? outputId : byRegistry);
    }

    /**
     * 见 {@link #displayNameOf(String)}：物品注册名 → 该物品的 {@code getDescriptionId()}
     * （= 本地化键）；<b>拿不到就返回 {@code null}</b>（调用方据此把入参当本地化键直接用）。
     *
     * <p>这条路径与 {@link #itemIconOf} 的 ① ② 两步逐字相同，只是第三步取的是描述 id 而不是图标；
     * 分开写而不是复用，是因为<b>两者的失败语义不同</b>：
     * 图标失败 = 只少画一个图标（那行文字照旧）；描述 id 失败 = 译名回落成入参本身（照实显示，不编）。
     */
    private static String descriptionIdOfRegistryName(String outputId) {
        if (outputId == null || outputId.isEmpty()) {
            return null;
        }
        try {
            ResourceLocation id = ResourceLocation.tryParse(outputId);
            if (id == null) {
                return null;
            }
            net.minecraft.world.item.Item item = BuiltInRegistries.ITEM.get(id);
            if (item == null) {
                return null;
            }
            ItemStack stack = new ItemStack(item);
            return stack.isEmpty() ? null : stack.getDescriptionId();
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 折减量 → 百分比字符串。例：{@code 0.8} ⇒ {@code "20.0％"}。
     *
     * <p>★ 全角百分号 {@code ％}（与本工程既有文案一致：见
     * {@code gtlmodular.nanochip.calibration.next} 等键）；{@code Locale.ROOT} 固定小数点，
     * 避免在逗号小数点的语言环境下显示成 {@code 20,0％}。
     */
    private static String percentOfReduction(double multiplier) {
        return String.format(java.util.Locale.ROOT, "%.1f％", (1.0D - multiplier) * 100.0D);
    }

    /*
     * ★ 2026-11-02：原来的本类私有 {@code appendThreadRow(...)} 已经搬进
     * {@link NanochipJadeThreadRows#appendThreadBar} —— 自绘形态（默认）与回退形态
     * （{@code MixinWorkableProgressBar} 在原生条后面追加）必须共用同一份渲染
     * （样式/文案/口径只有一处实现）。
     */

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    /**
     * 从方块实体上取本机的独立线程逻辑；不是本机（或不是新逻辑）就返回 {@code null}。
     *
     * <p>只认 {@link NanochipThreadedRecipeLogic} 这个<b>运行时类型</b>：
     * 旧逻辑机器（{@code NAC_INDEPENDENT_THREADS = false} 时的 gtladditions 配方逻辑）不是它
     * ⇒ 本 provider 全程不介入，原生进度条照旧。
     */
    private static NanochipThreadedRecipeLogic threadedLogic(BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof MetaMachineBlockEntity mbe)) {
            return null;
        }
        if (!(mbe.getMetaMachine() instanceof com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine machine)) {
            return null;
        }
        return machine.getRecipeLogic() instanceof NanochipThreadedRecipeLogic logic ? logic : null;
    }

    /**
     * ★★ 0.3.132【Jade 校准折减读数 · 服务端出口】★★ —— <b>只在服务端</b>跑（
     * {@link #appendServerData} 的调用者）：把本机校准当前的折减倍率写成两个 {@code double} 键。
     *
     * <p>取值路径（逐字照主机既有 API，<b>本类不自己算任何倍率</b>）：
     * {@code accessor.getBlockEntity()} → {@link MetaMachineBlockEntity} → {@code getMetaMachine()}；
     * 是 {@link NanochipAssemblyComplexMachine} 就调它那两个<b>纯只读</b>的显示用 getter
     * （{@code calibrationEuMultiplierForDisplay()} / {@code calibrationDurationMultiplierForDisplay()}）。
     *
     * <p><b>边界②「取不到机器 / 不是本机」</b>：两条早退 ⇒ <b>两个键都不写</b>
     * （客户端 {@link #appendCalibrationReduction} 见到键缺失就整行不显示 —— 宁可不说，绝不伪造）。
     * 这里<b>不</b>写 {@code 1.0} 兜底：写 1.0 就变成「我告诉你没折减」这种会撒谎的断言。
     */
    private static void writeCalibrationReduction(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof MetaMachineBlockEntity mbe)) {
            return;
        }
        if (!(mbe.getMetaMachine() instanceof NanochipAssemblyComplexMachine nac)) {
            return;
        }
        data.putDouble(KEY_CALIB_EU_MUL, nac.calibrationEuMultiplierForDisplay());
        data.putDouble(KEY_CALIB_DUR_MUL, nac.calibrationDurationMultiplierForDisplay());
    }

    /**
     * 线程明细的只读快照，<b>截到前 {@link NanochipJadeKeys#OUTPUT_LINES} 条（= 6）</b>。
     *
     * <p>★ 2026-11-17【<b>截断长度 = OUTPUT_LINES，不是 DISPLAY_LINES</b>】：
     * 进度条那一节只画 {@link NanochipJadeKeys#DISPLAY_LINES} = 3 条（队长原设定），
     * 而「各线程产物」那一节要列到 {@link NanochipJadeKeys#OUTPUT_LINES} = 6 条
     * ⇒ <b>列表本身必须按大（= 6）的那个截</b>：列表若被截到 3 条，产物节拿不到第 4~6 条的数据，
     * 它那边的上限再大也没用（这正是上一轮「6 个产物只显示一种」的一半根因）。
     * 「画几条」是客户端两节各自的循环上限（3 / 6），与这里的列表长度是两件事。
     *
     * <p>为什么要截：线程上限可到 256（{@code MAX_CROSS_RECIPE_THREADS = 1 &lt;&lt; 8}），
     * 而这个 NBT 是每次玩家准星指过来都要走一遍网络通道的载荷。截断掉的部分由
     * {@link NanochipThreadedRecipeLogic#LANG_THREADS_MORE} 一行如实交代条数，不假装只有这几条。
     */
    private static List<Map.Entry<Integer, NanochipThreadedRecipeLogic.ThreadRec>> snapshotThreads(
            NanochipThreadedRecipeLogic logic) {
        List<Map.Entry<Integer, NanochipThreadedRecipeLogic.ThreadRec>> out =
                new ArrayList<>(NanochipJadeKeys.OUTPUT_LINES);
        for (Map.Entry<Integer, NanochipThreadedRecipeLogic.ThreadRec> entry : logic.snapshot()) {
            if (out.size() >= NanochipJadeKeys.OUTPUT_LINES) {
                break;
            }
            if (entry != null && entry.getKey() != null && entry.getValue() != null) {
                out.add(entry);
            }
        }
        return out;
    }

    /**
     * 一条线程 → 一个 NBT 子 tag。
     *
     * <p><b>纪律（原口径，仍然成立）：不写配方对象</b> —— 配方本体（{@code GTRecipeSerializer.CODEC}
     * 那一坨）与 ItemStack 的全量 NBT（附魔 / 自定义名 / CompoundTag）才是"让载荷翻好几倍"的东西，
     * 本方法<b>一个都不写</b>。产物这一格写的是：
     * <ul>
     *   <li>{@link #KEY_OUTPUT_ID} = 产物 item 的<b>物品注册名</b>
     *       （{@code BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()}，例
     *       {@code gtlmodular:cc_crystal_processor}，≤ 约 60 字符的 ASCII 串）
     *       —— <b>★ 2026-11-05 语义变更</b>：改前写的是 {@code ItemStack#getDescriptionId()}
     *       （本地化键，形如 {@code item.gtlmodular.cc_crystal_processor}）。
     *       换的理由与逐字依据见 {@link #KEY_OUTPUT_ID}／{@link NanochipThreadedRecipeLogic#firstItemOutputId}：
     *       本地化键<b>反查不回 {@code Item}</b> ⇒ 客户端画不出图标（队长要的正是图标）。
     *       现在客户端既能由它重建 {@code ItemStack} 取译名（译名与改前逐字相同），又能建图标元素；</li>
     *   <li>{@link #KEY_OUTPUT_COUNT} = 数量（{@code long}；<b>内容数量，已含并行 p</b>，
     *       取值与记账同源，见 {@code NanochipThreadedRecipeLogic#firstItemOutputAmount} 的注释）。</li>
     * </ul>
     * <b>载荷估算（逐项，为什么加了产物也不会炸）</b>：条目数在 {@link #snapshotThreads} 已按
     * {@link NanochipJadeKeys#OUTPUT_LINES} = 6 截断；每条新增 =
     * String 键 ≈ <b>48 字节</b>（1 类型 + 2 名长 + {@code "OutputId"} 8 + 2 串长 + 35 串体）+
     * Long ≈ <b>22 字节</b>（1 + 2 + {@code "OutputCount"} 11 + 8）≈ <b>70 字节/条</b>
     * ⇒ 一次提示最多新增 ≈ <b>420 字节</b>（6 条），且<b>与机器实际在跑多少条线程无关</b>
     * （上限 256 也还是 6 条；★ 2026-11-17 由「3 条 / 210 字节」改为「6 条 / 420 字节」——
     * 翻倍后仍远小于整条配方的量级，见下面的对照）。对照：写整条配方 = 数千字节/条
     * （{@code NanochipConcurrentBatches:251-253} 存档时正是整条 {@code GTRecipeSerializer.CODEC}），
     * 写 ItemStack 全量 = 数百字节/条 ⇒ 用「短键 + 数字」把新增载荷钉死在 ~70 字节/条。
     * <p>★ 换成注册名后这个估算<b>不变</b>：注册名与本地化键长度同量级
     * （{@code gtlmodular:cc_crystal_processor} 30 字符 vs {@code item.gtlmodular.cc_crystal_processor} 35 字符）。
     *
     * <p>取不到就<b>一个键都不写</b>（客户端见到键缺失 ⇒ 照实写「无物品产物或未识别」，
     * <b>绝不</b>伪造一个产物名、<b>绝不</b>拿别的线程的产物顶上）。
     * 取数<b>纯只读</b>：{@code rec.recipe()} 是批次表里那条<b>已缩放</b>的配方（内容数量已含并行 p）。
     *
     * <p>槽位必须由调用方从 {@code snapshot()} 的 key 传进来 —— {@code ThreadRec} 自己不带槽位号。
     */
    private static CompoundTag threadTag(int slot, NanochipThreadedRecipeLogic.ThreadRec rec) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(NanochipJadeKeys.KEY_SLOT, slot);
        tag.putInt(NanochipJadeKeys.KEY_PROGRESS, rec.progress());
        tag.putInt(NanochipJadeKeys.KEY_DURATION, rec.duration());
        // ★ 2026-11-02【数字口径】：本条并行改走 {@code ThreadRec.parallels()}（= 与整机「同时处理 R 次」
        //   同一个函数、同一个字段）⇒ 逐条之和恒等于那一行 R，不会出现两处各读一个字段而打架。
        tag.putLong(NanochipJadeKeys.KEY_PARALLELS, rec.parallels());
        // ★★ 2026-11-04【逐线程产物】★★ 只写「物品注册名 + 数量」（见方法注释的载荷估算）。
        //   取不到（没有物品产物 / 纯流体 / 识别不出 / Ingredient 未解析 / 注册表里查不到该物品）
        //   ⇒ 两个键都不写。
        //   ★★ 2026-11-05【本键语义已从「本地化键」改成「物品注册名」】★★ —— 理由与取证见
        //     KEY_OUTPUT_ID 与 NanochipThreadedRecipeLogic#firstItemOutputId：
        //     本地化键反查不回 Item ⇒ 客户端画不出队长要的那个「产物图标」。
        //   ★ 一行回退：把下面 5 行删掉 ⇒ 服务端回到改动前（客户端那节随之只显示「未识别」；
        //     若连小节一起去掉，删掉 appendTooltip 里那一句 appendThreadOutputs(tooltip, data); 即可）。
        String outputId = NanochipThreadedRecipeLogic.firstItemOutputId(rec.recipe());
        if (outputId != null && !outputId.isEmpty()) {
            tag.putString(KEY_OUTPUT_ID, outputId);
            tag.putLong(KEY_OUTPUT_COUNT, NanochipThreadedRecipeLogic.firstItemOutputAmount(rec.recipe()));
        }
        return tag;
    }
}
