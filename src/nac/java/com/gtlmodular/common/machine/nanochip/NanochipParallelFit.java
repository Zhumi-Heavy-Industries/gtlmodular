package com.gtlmodular.common.machine.nanochip;

import com.gtlmodular.common.data.nanochip.NanochipModContext;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.ingredient.SizedIngredient;
import org.gtlcore.gtlcore.api.recipe.IAdvancedContentModifier;
import org.gtlcore.gtlcore.api.recipe.IGTRecipe;
import org.gtlcore.gtlcore.api.recipe.RecipeExtensionCopier;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongIngredient;

import java.util.List;
import java.util.Map;
import java.util.function.LongPredicate;

/**
 * ★ 2026-10-28【独立跨配方线程】<b>「精确 long 缩放 + 从上限往下二分找真正放得下的并行」</b>
 * —— 本包唯一一份实现，供<b>两条</b>配方通路共用。
 *
 * <h3>为什么单独抽一个类（而不是留在一处、另一处照抄）</h3>
 * 这一段逻辑原来是 {@code NanochipAssemblyComplexMachine.NanochipRecipeLogic} 的私有静态/实例方法
 * （{@code scaleRecipePrecisely} / {@code findHighestMatchableParallel}，出自星律
 * {@code PrimordialModuleRecipeLogic.java:395-438}）。本轮的「独立线程」新逻辑
 * （{@link NanochipThreadedRecipeLogic}）也要按「每条线程各自算并行」用同一套算法 ⇒
 * 若照抄一份，就是<b>同一条判界写法散在两处</b>（本包已经为「统一饱和工具」吃过一次这个亏，
 * 见 {@code NanochipAssemblyComplexMachine.saturatingMul} 的注释）⇒ 抽成本类，两边同源。
 *
 * <h3>两类方法各做什么</h3>
 * <ol>
 *   <li>{@link #scalePrecisely(GTRecipe, long)}：<b>精确 long 缩放</b>。不用
 *       {@code ContentModifier.multiplier((double) parallel)} —— 它收 double，超过 {@code 2^53}
 *       的并行数会把物品/流体数量四舍五入；{@link IAdvancedContentModifier#preciseMultiplier(long)}
 *       收 {@code long}，数量走整数倍乘 ⇒ <b>校验与扣料看到的是同一个数字</b>
 *       （星律 {@code PrimordialModuleRecipeLogic.java:395-407} 的原注释；也见本包
 *       {@code NanochipAssemblyComplexMachine.java} 那段注释）。</li>
 *   <li>{@link #findHighestMatchable(long, LongPredicate)}：<b>从一个上限往下二分</b>，
 *       返回最大的、{@code canMatch} 说「真的放得下」的值；0 = 连 1 都放不下。
 *       三步与星律 {@code PrimordialModuleRecipeLogic.java:409-438} 逐字同构：
 *       先试 {@code requested}（常见情况一次命中）→ 再试 {@code requested - 1}
 *       （躲开容量边界抖动）→ 标准 long 二分（{@code low + ((high - low) &gt;&gt;&gt; 1)} 防溢出）。</li>
 * </ol>
 *
 * <p><b>为什么二分这一刀不许删</b>：它是防「按预算放大的产出塞不下 ⇒ 起跑前被
 * {@code matchRecipeOutput} 挡掉 ⇒ 永远不开工（界面表现为「找不到可运行配方 / 进度 0%」）」
 * 的关键。本类的两个调用方（旧逻辑的 {@code calculateParallels}、新逻辑的每条线程）都保留它。
 *
 * <h3>纯函数 / 零状态 / 零存档</h3>
 * 全部方法都不读任何实例字段、不写任何状态、不碰 NBT（★ 2026-11-01 新增的
 * {@link #MAX_PARALLEL_SEARCH_BOUND} 与 {@link #findHighestMatchableInSafeRange(long, LongPredicate)}
 * 同样是「常量 + 纯函数」）；{@link #findHighestMatchable} 连世界都不读
 * （判定由调用方给的 {@link LongPredicate} 完成）。
 *
 * <p>★ 2026-11-14【队长裁决「甲」】{@link #scalePrecisely(GTRecipe, long)} 现在多了一件事 ——
 * 在<b>它自己新复制出来的那一份</b> {@code scaled} 上，把<b>四张内容表</b>
 * （{@code inputs} / {@code outputs} / {@code tickInputs} / {@code tickOutputs}）
 * 的数量用 <b>{@code long} 直乘</b>校正成精确的「每份 × 实际并行」（乘法前判界饱和，见
 * {@link #rescaleContentsExactly}）—— 因为 {@code preciseMultiplier(long)} 内部仍是 {@code double}，
 * 超过 {@code 2^53} 会四舍五入。它<b>仍然零状态</b>
 * （没有任何字段 / 静态可变状态 / 存档读写），只是不再对入参配方「只读」：
 * 它读入参、写副本 —— 入参 {@code recipe} 一个字段都不改（{@code recipe.copy(...)} 是深拷贝副本）。
 *
 * <p>★ 上一轮那件事（把<b>产出量</b>强行顶到 {@code Long.MAX_VALUE}）已被队长否决并<b>删除</b>：
 * 见 {@link #NAC_OUTPUT_AMOUNT_TO_CEILING}（墓碑常量）。本轮只抬<b>天花板</b>，
 * <b>绝不</b>把产出量强行顶满 —— 实际值该多少还是多少。
 */
public final class NanochipParallelFit {

    /**
     * ★★ 2026-11-14【队长裁决「甲」· 抬天花板】★★ <b>一行回退开关</b>。
     *
     * <p>{@code true}（默认）⇒ 并行天花板 {@link #MAX_PARALLEL_SEARCH_BOUND} 取
     * {@link Long#MAX_VALUE}（{@code 9.223372036854775807e18}），并把内容量缩放从
     * {@code preciseMultiplier(long)} 的 {@code double} 口径补成
     * <b>{@code long} 直乘 + 乘法前判界饱和</b>（见 {@link #scalePrecisely}）。
     *
     * <p>{@code false} ⇒ <b>逐字回到改动前</b>：天花板仍是 {@code Integer.MAX_VALUE}
     * （{@code 2.147483647e9}，即实机截图里那个数），内容量只走
     * {@code recipe.copy(IAdvancedContentModifier.preciseMultiplier(parallel), false)}
     * —— 连一次 {@code long} 直乘都不做。
     *
     * <h3>为什么两件事共用一个开关（而不是给两个开关）</h3>
     * 「抬天花板」与「内容量走 {@code long} 直乘」是<b>同一件事的两半</b>，必须同开同关：
     * 天花板抬到 {@code 9.2e18} 之后，若内容量仍走 {@code double}
     * （{@code ContentModifier} 是 {@code record(double, double)}，超过 {@code 2^53} 就四舍五入，
     * 见 {@code IAdvancedContentModifier:5-9} 的作者原注释），
     * 那么二分的中点虽然落在合法区间里，<b>缩放出来的数量却已不是「每份 × 并行」</b>
     * ⇒ 判定与实产对不上（2026-11-01「并行二分=0」那一类事故的温床）。
     * 留两个开关就一定会被后来人只开一个 ⇒ 干脆只给<b>一行</b>。
     */
    public static final boolean NAC_PARALLEL_CEILING_TO_LONG = true;

    /**
     * ★★ 2026-11-14【(甲) 天花板抬到 {@code long}】★★（原 2026-11-01 为 {@code int} 边界）
     * <b>二分搜索的上界硬上限 = 并行天花板</b>。
     *
     * <h3>它现在是什么</h3>
     * 默认 {@link Long#MAX_VALUE}（{@code 9.223372036854775807e18}），由一行开关
     * {@link #NAC_PARALLEL_CEILING_TO_LONG} 控制（置 {@code false} = 逐字回到
     * {@code Integer.MAX_VALUE} = {@code 2.147483647e9}）。
     *
     * <p><b>★ 它是「天花板」，不是「实际值」</b>：它只决定二分<b>从哪里开始往下找</b>。
     * 真正的并行 = 「输入侧真的扣得出来」与「产出侧真的放得下」两项实证判定的交集上限，
     * 由 {@link #findHighestMatchableInSafeRange} → {@link #findHighestMatchable} 收敛
     * ⇒ <b>有料才高，没料就还是原来那个小数字</b>
     * （正常生产零影响：见下方「为什么对正常生产零影响」）。
     *
     * <h3>为什么必须有这一个上限（2026-11-01 那次事故的推导，仍然有效）</h3>
     * {@link #findHighestMatchable} 是<b>单调假设</b>下的二分：它默认「{@code canMatch(N)} 假 ⇒
     * 所有 {@code > N} 的值也假」。这个假设在 {@code requestedParallel = Long.MAX_VALUE} 时
     * <b>不成立</b>：
     * <ol>
     *   <li>{@code canMatch} 的第一步是 {@code scalePrecisely(recipe, N)}，即
     *       {@code recipe.copy(IAdvancedContentModifier.preciseMultiplier(N), false)} ——
     *       把物品/流体数量乘 {@code N}。{@code N} 取 {@code Long.MAX_VALUE} 量级时，
     *       内容数量、EUt、总电量全部溢出成负数/垃圾值；</li>
     *   <li>于是 {@code matchRecipeInput/matchRecipeOutput} 在<b>巨大 N</b> 上返回「放不下」
     *       —— 这是<b>算术溢出</b>，不是真实容量；</li>
     *   <li>而二分的前两刀恰好就是 {@code requested = Long.MAX_VALUE} 与
     *       {@code requested - 1}，两刀都「假」⇒ 区间塌到 {@code [1, Long.MAX_VALUE - 2]}；
     *       之后每刀的中点仍是 {@code ~2^62} 量级的溢出值 ⇒ 中点恒假 ⇒ {@code high} 一路左移，
     *       <b>{@code best} 永远是 0</b> ⇒ 返回 0 ⇒ 上层一律记「并行二分=0」⇒
     *       候选枚举正常（6/6）但<b>一条批次都开不出来</b>。</li>
     * </ol>
     * <h3>为什么现在可以越过 {@code int} 边界（本轮 <b>(甲)</b> 的技术前提）</h3>
     * 原来钳在 {@code int} 边界，理由是这个类当时唯一能用的缩放入口
     * {@code preciseMultiplier(long)} 内部仍走 {@code double}：{@code N} 取 {@code 2^62} 量级时
     * 缩放出来的数量是「四舍五入后的天文数字」，{@code matchRecipeInput/matchRecipeOutput}
     * 的单调性无从保证 ⇒ 索性不越过 {@code int} 边界。
     * 本轮把<b>内容量</b>改成 <b>{@code long} 直乘 + 乘法前判界饱和</b>
     * （{@link #scalePrecisely} → {@link #rescaleContentsExactly}）⇒
     * 无论 {@code N} 多大，缩放出来的每一个数量都<b>恒非负</b>、且随 {@code N} 单调不减
     * （到 {@link Long#MAX_VALUE} 为止饱和）⇒ 谓词重新单调，二分在 {@code long} 全域有效。
     *
     * <h3>★ 全部使用点审计（抬高后逐个确认「只会放宽、不会出错」）</h3>
     * <ol>
     *   <li>{@code NanochipParallelFit:191} {@code Math.min(requestedParallel, BOUND)} ——
     *       {@code long} 口径，抬高只把搜索区间放宽；</li>
     *   <li>{@code NanochipThreadedRecipeLogic:2716}
     *       {@code limitArgument = PARALLEL_LIMIT_BOUNDED ? BOUND : Long.MAX_VALUE} ——
     *       这是传给上游 {@code IParallelLogic.getMaxParallel(holder, recipe, long limit)} 的
     *       {@code limit}。上游实现逐字（{@code _census/gtlcore-unofficial/.../IParallelLogic.java:284-303}）是
     *       {@code parallelAmount = Math.min(parallelAmount, Long.MAX_VALUE / amount)}
     *       （{@code limitItemAmount} / {@code limitFluidAmount}，**这个方法存在的唯一目的就是接住
     *       无界请求**），之后只做 {@code Math.min(parallelLimit, available / needed)}：
     *       <b>既不窄化 {@code int}、也不拿 {@code limit} 做乘法</b>
     *       ⇒ 传 {@code Long.MAX_VALUE} 安全；抬高后该开关两个分支取到同一个值（不再有差别）；</li>
     *   <li>{@code NanochipThreadedRecipeLogic:2772} {@code fitBound = Math.min(inputMax, BOUND)} ——
     *       {@code long} 口径，只喂「这一档是从哪一步来的」那行文字（{@code parallelBranchText}）
     *       与日志；抬高后 {@code fitBound == inputMax}，<b>读数更如实</b>；</li>
     *   <li>{@code NanochipThreadedRecipeLogic:2774}
     *       {@code fitSampleInputMaxOverBound = inputMax > BOUND} —— 纯诊断格；抬高后该比较恒假
     *       ⇒ 抽样里「越界=是」这一格不再出现（<b>只少一个诊断信号，不进任何判定</b>）。
     *       该文件不在本轮白名单 ⇒ <b>只报告、不修改</b>；</li>
     *   <li>{@code NanochipThreadedRecipeLogic:3496} 日志文本 —— 只影响打印内容。</li>
     * </ol>
     * <b>没有任何使用点需要 {@code int}</b>：没有一处把它当数组长度 / 下标 / {@code int} 形参，
     * 全库也不存在 {@code (int)} 窄化或 {@code Math.min(..., Integer.MAX_VALUE)} 之类的隐藏钳制
     * ⇒ <b>不需要另设 int 钳制常量</b>（放宽上界 ≠ 到处都塞 {@code long}）。
     * ⚠「机器并行上限」那个 {@code int} 是<b>另一个东西</b>
     * （{@code NanochipAssemblyComplexMachine.FULL_PARALLEL_CAP}、上游
     * {@code ParallelMachine.getMaxParallel(): int}、上游 {@code GTRecipe.parallels:int}），
     * 本轮<b>一个字都不动</b> —— 界面「同时处理至多 N 个配方」那一格仍是它的接口饱和占位值。
     *
     * <h3>为什么对正常生产零影响</h3>
     * 二分只在 {@code inputMax} 超过 {@code 2.147483647e9} 时才可能走到那一档以上 ——
     * 也就是<b>机器真的握有 20 亿份以上的料</b>时（此时旧值才是那道假的拦路墙）。
     * {@code inputMax} 小的时候（例如上游「首个有货槽」给的 625），
     * {@code Math.min(inputMax, BOUND)} 恒等于 {@code inputMax} ⇒ <b>改前改后逐字相同</b>。
     *
     * <p><b>唯一代价</b>：上界变大 ⇒ 二分的最坏档数从 {@code ~31} 档变成 {@code ~63} 档
     * （每档一次配方复制 + 两次容量匹配）。而这多出来的档只在「上界真的很大」时才有意义，
     * 那种机器通常<b>第一刀就命中</b>（{@code canMatch(requested)} 为真 ⇒ 一次都不二分）。
     */
    public static final long MAX_PARALLEL_SEARCH_BOUND =
            NAC_PARALLEL_CEILING_TO_LONG ? Long.MAX_VALUE : Integer.MAX_VALUE;

    /**
     * ★★ 2026-11-14【已废止 · 队长否决】★★<b>墓碑常量（禁止再接线）</b> —— 值恒为 {@code false}。
     *
     * <p>它上一轮做的是「把<b>产出量</b>强行顶到 {@code Long.MAX_VALUE}」。队长逐字否决：
     * 「<b>我不是要他最后产物变那么多</b>」⇒ 本轮口径改为 <b>(甲)</b>：
     * <b>只抬天花板，实际值该多少还是多少</b>（见 {@link #NAC_PARALLEL_CEILING_TO_LONG}）。
     *
     * <p>⇒ 它的<b>唯一执行体</b>（原 {@code amplifyOutputAmountsToCeiling}：按
     * {@code Long.MAX_VALUE / maxAmount} 求「统一倍率」再把产出顶到天花板）连同调用点
     * <b>已在本轮删除</b>，只留本常量作可追溯用途 —— 目的正是<b>不留两套同义助手</b>
     * （「顶到天花板」与「每份 × 实际并行」两套并存，后来人必然用错那一套）。
     * <b>不得</b>把它再挂回任何判定 / 缩放链路；本轮已核：全库不再有任何
     * {@code if (NAC_OUTPUT_AMOUNT_TO_CEILING)}。
     *
     * <h3>【历史存档】以下是上一轮（已废止）的原注释，逐字保留仅供追溯</h3>
     * （原）它解决什么（队长口径「怎么突破 2G 上限达到 9.2e18」）
     * 实机读数（截图逐字）：{@code 同时处理至多 2147483647 个配方} / {@code 并行 2147483647} /
     * {@code 线程0：2147483647 × [超因果处理器]} ⇒ {@code 2147483647 = 2^31-1 = Integer.MAX_VALUE}
     * ⇒ 这个数<b>不是算出来的，是被 {@code int} 钳住的</b>。钳它的那一条通道是
     * {@code GTRecipe.parallels}（上游 {@code int} 字段）与
     * {@link #MAX_PARALLEL_SEARCH_BOUND}（= {@code Integer.MAX_VALUE}）。
     * <b>本开关不动那两条通道</b>（并行照旧 ≤ 2.15e9，界面上那一行读数不变），
     * 改的是<b>另一条本来就是 {@code long} 的通道</b>：
     * 产出内容量 {@code LongIngredient.actualAmount}（{@code long}，天花板 {@code 9.223372036854775807e18}）。
     *
     * <h3>为什么必须是「{@code long} 直乘 + 饱和」而不是「抬并行 / 用 double 乘数」</h3>
     * <ol>
     *   <li><b>不许抬并行</b>：{@code GTRecipe.parallels} 是上游 {@code int} 字段
     *       （gtlcore {@code GTRecipeMixin.setRealParallels} 逐字
     *       {@code this.parallels = Ints.saturatedCast(realParallel);}，出处见
     *       {@code NanochipAssemblyComplexMachine.java:5002-5004} 的引证），
     *       到 {@code 2^31-1} 就顶死 ⇒ 2.15e9 之后就再也上不去；</li>
     *   <li><b>不许用 double 乘数逼近</b>：本包现有的缩放入口
     *       {@link IAdvancedContentModifier#preciseMultiplier(long)}
     *       逐字是 {@code return ContentModifier.multiplier(multiplier);}，而
     *       {@code ContentModifier.multiplier} 收的是 <b>{@code double}</b>
     *       （{@code ContentModifier} 是 {@code record(double multiplier, double addition)}，
     *       javap 逐字 {@code >(DD)L…/ContentModifier;}，出处
     *       {@code _census/_probe_nacmat_evidence/classstr_gtceu-1.20.1-1.4.4.txt:18086}）⇒
     *       超过 {@code 2^53} 就有精度损失（{@code IAdvancedContentModifier:5-9} 的作者原注释也写明
     *       「超过 {@code 2^53} 的 long 值会有精度损失」）；</li>
     *   <li>⇒ 本开关走的是第三条路：把
     *       {@code LongIngredient} 的 {@code actualAmount} 用<b>{@code long} 整数算术</b>直乘，
     *       乘法前判界、超上限停在 {@link Long#MAX_VALUE}（<b>绝不回绕成负数</b>）。</li>
     * </ol>
     *
     * <h3>挂在哪一步（★ 唯一挂点 = {@link #scalePrecisely(GTRecipe, long)}）</h3>
     * 那条「精确 long 缩放」是<b>两条配方通路共用</b>的唯一缩放入口：本机现役逻辑
     * {@code NanochipThreadedRecipeLogic} 的「二分试探谓词」（{@code fitsParallel:2848}）与
     * 「开批次」（{@code prepareBatch:2649}）都调它 ⇒ 两边看到<b>同一个数</b>
     * （这正是本类存在的理由，见类注释）。把放大挂在这里 ⇒
     * <b>容量判定看到的就是真实会产出的量</b>（不会「校验时说放得下、出料时才发现放不下」），
     * 也<b>不需要</b>去动本包之外任何一个文件。
     *
     * <h3>为什么不挂在「配方修改器 / 内容 modifier」上（即为什么不用山海的写法）</h3>
     * 山海 {@code PrimordialRecipeOutputAmplifier.apply(GTRecipe, int)} （{@code :14}）逐字用的是
     * {@code ContentModifier modifier = ContentModifier.multiplier(multiplier);}（{@code :24}），
     * 而它的倍率入参是 {@code int multiplier}（{@code PrimordialModuleRecipeLogic:328}
     * {@code int multiplier = module.getHostOutputMultiplier();}）⇒ 那条路<b>到不了</b> {@code 9.2e18}
     * （它自己也在注释里写明「必须保留 long 数量路径，不能把大配方输出钳回 {@code Integer.MAX_VALUE}」，
     * 但它仍然是 {@code double} 口径）。本轮照它的<b>做法</b>（在配方内容量上放大、与并行解耦、饱和），
     * <b>不照它的倍率口径</b>（{@code int}/{@code double}）。
     *
     * <h3>（原）一行回退 —— <b>已随执行体一并废止</b></h3>
     * 原话是「把本常量改成 {@code false} ⇒ {@link #scalePrecisely(GTRecipe, long)} 里那一句调用被跳过」；
     * 本轮那个调用点与方法都已删除 ⇒ <b>再改本常量不会有任何行为变化</b>（它已不参与任何逻辑）。
     * 现在<b>唯一</b>的回退开关是 {@link #NAC_PARALLEL_CEILING_TO_LONG}。
     *
     * <h3>★★ 为什么默认是 {@code false}（父代理定案，2026-11-14）★★</h3>
     * 本功能挂在<b>唯一缩放入口</b>上 ⇒ <b>二分试探谓词也会看到放大后的产出</b>。
     * 于是：<b>出料侧若是【普通输出总线/仓】（有容量上限），{@code matchRecipeOutput} 会判"放不下"
     * ⇒ 二分收敛到 0 ⇒ 机器直接不开工</b>（不刷屏、不崩，但不干活）。
     * 要真正拿到 9.2e18，出料侧<b>必须</b>是 <b>ME 扩展输出仓</b>（9.2e18 这个量级，
     * 任何有限容量仓都装不下 —— 这是量级的机械必然，不是实现选择）。
     * ⇒ <b>默认 {@code false} = 不打断任何现有生产</b>；要用时把它置 {@code true} 重新编译
     * （队长若确认出料侧就是 ME 扩展输出仓，可让父代理一行改成 {@code true}）。
     */
    public static final boolean NAC_OUTPUT_AMOUNT_TO_CEILING = false;

    private NanochipParallelFit() {
    }

    /**
     * ★ 2026-11-01【并行二分=0 · 修复】<b>「先钳上界、再单调二分」</b> —— 与
     * {@link #findHighestMatchable(long, LongPredicate)} <b>逐字同构，只多一件事</b>：
     * 在开搜之前把 {@code requested} 钳到 {@link #MAX_PARALLEL_SEARCH_BOUND}。
     *
     * <h3>为什么必须钳（而不是让二分自己去收敛）</h3>
     * 见 {@link #MAX_PARALLEL_SEARCH_BOUND} 的推导：{@code requested} 一旦是
     * {@code Long.MAX_VALUE}（gtlcore 的「无限输入」在 ME 样板总成上就是这一档），
     * 二分的<b>每一个中点</b>都落在「内容数量 × 天文数字」的算术区里。
     * 钳上界 + <b>内容量走 {@code long} 直乘（乘法前判界饱和）</b>之后，
     * 中点的量级始终是「每份 × 档位」且<b>恒非负</b> ⇒ <b>谓词重新单调，二分恢复有效</b>。
     *
     * <p>★ 2026-11-14【(甲)】：这个「钳」现在是 {@link Long#MAX_VALUE} 的<b>恒等钳制</b>
     * （天花板抬到 {@code long} 全域）—— 它保留的意义只剩「显式化这一步 + 一行开关回退」，
     * 不再是把区间压回 {@code int} 边界（那正是 2.15e9 那道墙的由来）。
     *
     * <h3>为什么**不**直接改成 {@code findHighestMatchable(min(requested, BOUND), ...)}</h3>
     * 那样会悄悄改掉 {@link #findHighestMatchable} 的既有语义（旧逻辑也在用同一个方法），
     * 本方法把「钳」这一步单独显式化 ⇒ 调用方自己决定要不要钳，共用方法一个字不动。
     *
     * @param requestedParallel 未钳的上限（可能来自 {@code IParallelLogic.getMaxParallel}，即可能是 {@code Long.MAX_VALUE}）
     * @param canMatch          对某个候选并行数做「真的放得下吗」的实证判定
     * @return 最大的可行值（≤ {@link #MAX_PARALLEL_SEARCH_BOUND}）；{@code 0} = 连 1 都放不下
     */
    public static long findHighestMatchableInSafeRange(long requestedParallel, LongPredicate canMatch) {
        if (requestedParallel <= 0L || canMatch == null) {
            return 0L;
        }
        long bounded = Math.min(requestedParallel, MAX_PARALLEL_SEARCH_BOUND);
        return findHighestMatchable(bounded, canMatch);
    }

    /**
     * 把配方按 {@code parallel} 倍<b>精确</b>放大，并把 gtlcore 的扩展字段（并行数等）一并搬过去。
     *
     * <p><b>两步放大，缺一不可</b>：① {@code recipe.copy(preciseMultiplier(parallel), false)}
     * 按上游语义复制四张内容表（物品/流体的包装方式、概率、{@code IntProviderIngredient} 都由上游决定）；
     * ② {@link #rescaleContentsExactly} 在<b>同一个副本</b>上把每一份数量换成
     * 「每份 × 并行」的 <b>{@code long} 整数积（超上限饱和）</b> —— 因为 ① 里那条通道是
     * {@code double}，超过 {@code 2^53} 会四舍五入（见 {@link #NAC_PARALLEL_CEILING_TO_LONG}）。
     * ② 的落点是「四张表」而不只是产出：判定（输入侧扣不扣得出来）与实产必须是同一个数。
     *
     * @return {@code parallel <= 1} 时<b>原样返回入参</b>（不复制、不做任何校正，省一次分配，与星律一致）
     */
    public static GTRecipe scalePrecisely(GTRecipe recipe, long parallel) {
        if (recipe == null) {
            return null;
        }
        if (parallel <= 1L) {
            return recipe;
        }
        GTRecipe scaled = recipe.copy(IAdvancedContentModifier.preciseMultiplier(parallel), false);
        // 扩展字段（realParallels 等）必须一起搬，否则下游读 realParallels 会读到 0
        RecipeExtensionCopier.copy(recipe, scaled);
        IGTRecipe.of(scaled).setRealParallels(parallel);
        // ★★ 2026-11-14【(甲) 内容量走 long 直乘 · 精确到「每份 × 实际并行」】★★
        //   上面那一句 `recipe.copy(preciseMultiplier(parallel), false)` 已经把四张内容表
        //   （inputs / outputs / tickInputs / tickOutputs）按**同一个倍率**复制好了，但那条通道
        //   内部是 `double`（`ContentModifier` 是 `record(double, double)`）⇒ 超过 2^53 会四舍五入。
        //   ⇒ 这里在**同一个副本**上把每一份数量校正成精确的「每份（入参配方）× 实际并行」：
        //     long 整数乘、乘法前判界、超上限停在 Long.MAX_VALUE（只饱和，**绝不**强行顶满）。
        //   为什么必须挂在 `recipe.copy(...)` **之后**、而不是替掉它：见 NAC_PARALLEL_CEILING_TO_LONG 的注释
        //   （替掉它就得自己重做「哪些内容类型怎么包」——那是上游 `copyWithModifier` 的职责，本轮不碰）。
        //   为什么必须在这一步同时被「二分试探谓词」看到：判定与实产必须是**同一个对象同一个数**，
        //   否则会出现「校验时说放得下、出料时才发现放不下」。
        if (NAC_PARALLEL_CEILING_TO_LONG) {
            rescaleContentsExactly(recipe, scaled, parallel);
        }
        return scaled;
    }

    /**
     * ★★ 2026-11-14【(甲) · 内容量 <b>{@code long} 直乘</b>】★★
     * <b>把 {@code scaled} 的四张内容表校正成精确的「每份 × 实际并行」</b>。
     *
     * <h3>它解决什么</h3>
     * {@link #scalePrecisely} 里那一句 {@code recipe.copy(preciseMultiplier(parallel), false)}
     * 已经把四张表按同一个倍率复制好了，但 {@code preciseMultiplier(long)} 内部逐字是
     * {@code ContentModifier.multiplier(multiplier)}（{@code IAdvancedContentModifier:17-19}），
     * 而 {@code ContentModifier} 是 {@code record(double multiplier, double addition)}
     * （javap 逐字 {@code >(DD)L.../ContentModifier;}）⇒ <b>超过 {@code 2^53} 就会四舍五入</b>。
     * 本轮并行天花板抬到 {@code 9.223372036854775807e18}（{@link #NAC_PARALLEL_CEILING_TO_LONG}）
     * ⇒ 那条通道<b>必然算错</b>。本方法用 {@code long} 整数算术把每一份数量<b>重算一遍</b>：
     * {@code 每份 × 并行}，乘法<b>之前</b>判界，越界停在 {@link Long#MAX_VALUE}
     * —— <b>只饱和，绝不强行顶满</b>（上一轮「顶到天花板」的写法已被队长否决并删除，
     * 见 {@link #NAC_OUTPUT_AMOUNT_TO_CEILING}）。
     *
     * <h3>为什么【四张表都改】（而不是只改产出）</h3>
     * {@code preciseMultiplier} 对四张表<b>一视同仁</b>：{@code GTRecipe.copy(ContentModifier, boolean)}
     * 逐字把同一个 modifier 传给 {@code copyContents(inputs / outputs / tickInputs / tickOutputs)}
     * （出处 {@code _census/_gtceu_GTRecipe_copy.javap.txt:253-330}）⇒ 只改产出就会让
     * <b>两张表两个口径</b>：输入侧仍是四舍五入后的数（「扣不扣得出来」判的是它），输出侧却是精确数。
     * 本机 EUt 也在这四张表里（{@code EURecipeCapability} 的内容量是 {@code Long}）
     * ⇒ 四张一起校正，口径才唯一（判定与实产是同一个对象、同一个数）。
     *
     * <h3>为什么是「校正副本」而不是「替掉 {@code copy}」</h3>
     * {@code copy} 管的是「哪些内容类型怎么包」：物品一律被
     * {@code ItemRecipeCapabilityMixin.copyWithModifier:77-87} 包成 {@code LongIngredient}、
     * 流体走 {@code FluidRecipeCapabilityMixin.copyWithModifier:47-56}、
     * {@code IntProviderIngredient} 走 {@code (float) multiplier}……那是上游的职责，本轮<b>一个字不碰</b>
     * （最保守做法：保留上游的复制语义，只把「数量」这一格换成精确值）。
     * 认得出来的类型才写；认不出来的（例如 {@code IntProviderIngredient}：数量是随机分布）
     * 一律<b>保留原值</b> —— 宁可少精确一种，也不猜一个会静默改变产量分布的写法。
     *
     * <h3>纪律：只 {@code set}，绝不 {@code clear()}</h3>
     * 一律「逐位替换」：<b>不重建列表、不清空、不动键集合</b>，也不动
     * {@code chance} / {@code maxChance} / {@code perTick} / {@code slotName} / {@code uiName}
     * （教训见 {@code _census/错误历史与工作经验.md} 的 B21：破坏性重建会合并掉别人写的行）。
     *
     * <p><b>零状态 / 不改入参</b>：只读 {@code original}、只写 {@code scaled}；不读任何实例字段、
     * 不写任何静态状态、不碰 NBT（见类注释）。
     *
     * @param original 入参配方 —— <b>「每份」的权威来源</b>（本方法对它<b>只读</b>，一个字段都不改）
     * @param scaled   {@code original.copy(...)} 出来的副本（本方法就地校正它的内容量）
     * @param parallel 这一次的实际并行（只会在 {@code > 1} 时被调用）
     */
    private static void rescaleContentsExactly(GTRecipe original, GTRecipe scaled, long parallel) {
        if (original == null || scaled == null || parallel <= 1L) {
            return;
        }
        rescaleMapExactly(original.inputs, scaled.inputs, parallel);
        rescaleMapExactly(original.outputs, scaled.outputs, parallel);
        rescaleMapExactly(original.tickInputs, scaled.tickInputs, parallel);
        rescaleMapExactly(original.tickOutputs, scaled.tickOutputs, parallel);
    }

    /**
     * 对<b>一张</b>内容表做「每份 × 并行」的精确 {@code long} 校正
     * （{@link #rescaleContentsExactly} 的逐表白）。
     *
     * <h3>怎么把「副本里的一格」对上「入参里的那一份」</h3>
     * 先按能力键（{@code RecipeCapability}）取同一张列表，再<b>按下标一一对应</b>：
     * {@code GTRecipe.copyContents} 逐字是「{@code new HashMap} → 按原 {@code List} 顺序遍历 →
     * {@code new ArrayList} 逐个 {@code Content.copy(...)} → {@code put(同一个 key, 新 list)}」
     * （{@code _census/_gtceu_GTRecipe_copy.javap.txt:115-176}）⇒
     * <b>键是同一批对象、列表的顺序与个数逐一对应</b>。
     * 仍然加两道保险：<b>键取不到</b>、或<b>两边长度不等</b> ⇒ 这张表<b>整条不碰</b>
     * （对不齐就一个数都不改，绝不猜着配对）。
     *
     * <h3>写回的口径</h3>
     * <ul>
     *   <li>{@code 每份} = {@link #contentAmountOf}(入参那一格)；{@code <= 0}（=「没有量」）⇒ <b>保留原值</b>；</li>
     *   <li>{@code 精确值} = {@link #saturatingMultiply}(每份, parallel) —— 判界在乘法<b>之前</b>
     *       ⇒ <b>没有任何一次乘法真正溢出</b>，结果恒非负；</li>
     *   <li>与副本现状<b>已经相等</b>（{@code double} 口径在 {@code <= 2^53} 时本来就精确）
     *       ⇒ <b>连字段都不写</b>（正常小并行下本方法因此几乎什么都不做）；</li>
     *   <li>写回失败 / 类型认不出 ⇒ {@link #applyContentAmount} 返回 {@code false}，<b>保留原值</b>，
     *       绝不外抛进 tick。</li>
     * </ul>
     */
    private static void rescaleMapExactly(Map<RecipeCapability<?>, List<Content>> originalMap,
                                          Map<RecipeCapability<?>, List<Content>> scaledMap,
                                          long parallel) {
        if (originalMap == null || originalMap.isEmpty() || scaledMap == null || scaledMap.isEmpty()) {
            return;
        }
        for (Map.Entry<RecipeCapability<?>, List<Content>> entry : scaledMap.entrySet()) {
            List<Content> scaledContents = entry.getValue();
            if (scaledContents == null || scaledContents.isEmpty()) {
                continue;
            }
            List<Content> originalContents = originalMap.get(entry.getKey());
            if (originalContents == null || originalContents.size() != scaledContents.size()) {
                continue;   // 对不齐（理论上不会，见 javadoc）⇒ 这张表整个不碰
            }
            for (int i = 0; i < scaledContents.size(); i++) {
                Content scaledContent = scaledContents.get(i);
                if (scaledContent == null) {
                    continue;
                }
                long per = contentAmountOf(originalContents.get(i));
                if (per <= 0L) {
                    continue;   // 「没有量」的那一份保持原样（不放大、也不清零）
                }
                long exact = saturatingMultiply(per, parallel);
                if (exact == contentAmountOf(scaledContent)) {
                    continue;   // 已经精确相等 ⇒ 连字段都不用写
                }
                applyContentAmount(scaledContent, exact);
            }
        }
    }

    /**
     * 读一份内容（输入 / 产出 / tick 都走它）的<b>数量</b>
     * （口径与 {@code NanochipAssemblyComplexMachine.producedCountOf} 同源）：
     * {@code LongIngredient.getActualAmount()}（{@code long}，首选）→
     * {@code SizedIngredient.getAmount()}（{@code int}）→
     * {@code Long}（**数值型能力**：本例就是 {@code EURecipeCapability} 的 EUt，
     * 见 {@code classstr_gtceu-1.20.1-1.4.4.txt:2409} 的
     * {@code (Long, ContentModifier) -> Long}；先例：山海
     * {@code EternalGregTechWorkshopModuleRecipeLogic.java:156} 逐字
     * {@code content.content = Math.max(1L, (long) Math.ceil(eut * multiplier));}）
     * → {@code 0}（认不出的类型不猜、不放大、也不清零）。
     *
     * <p>★ 名字由 {@code outputAmountOf} 改成 {@code contentAmountOf}：本轮它不再只服务「产出」，
     * 而是四张表共用（旧名会让后来人以为只能用在 outputs 上 —— 这正是「同义/误导命名」的坑）。
     *
     * @return {@code long}，恒 {@code >= 0}
     */
    private static long contentAmountOf(Content content) {
        if (content == null) {
            return 0L;
        }
        Object inner = content.content;
        if (inner instanceof LongIngredient longIngredient) {
            return longIngredient.getActualAmount();
        }
        if (inner instanceof SizedIngredient sizedIngredient) {
            return Math.max(0L, (long) sizedIngredient.getAmount());
        }
        // ★ 数值型能力（EUt 这类）：内容本身就是个 Long。
        //   ⚠️ 只认 Long，**不认 Integer**：本工程里「数量是 Integer」的能力【未取证】，
        //      猜错会把一个「档位/编号」当数量乘（宁可不精确一种，也不猜，见类注释的纪律）。
        if (inner instanceof Long longValue) {
            return Math.max(0L, longValue.longValue());
        }
        // 既不是 Long / Sized，也不是数值 Long（理论上不会：gtlcore 把任何 Ingredient 都提升成
        // LongIngredient，见 ItemRecipeCapabilityMixin.copyWithModifier:77-87）
        // ⇒ 返回 0 =「不参与校正」，绝不猜。
        return 0L;
    }

    /**
     * 把一份内容的数量就地换成 {@code amount}（<b>只改 {@code content.content} 这一个字段</b>）。
     *
     * <p>做法（三类，全部只换「数量」这一格）：</p>
     * <ul>
     *   <li>{@code LongIngredient} / {@code SizedIngredient}：
     *       {@code content.content = LongIngredient.create(inner, amount)} —— 用 gtlcore 自己的
     *       {@code LongIngredient.create(Ingredient, long)}（{@code LongIngredient:65-67}）取 {@code long}
     *       数量口径（先例：山海 {@code EternalGregTechWorkshopModuleMachine.java:590}）；
     *       {@code getInner()} 取回的仍是同一种物品；</li>
     *   <li>数值型能力（内容本身是 {@code Long}，本例 = {@code EURecipeCapability} 的 EUt）：
     *       {@code content.content = amount}（自动装箱成 {@code Long}）——
     *       先例逐字见 {@code contentAmountOf} 的注释；</li>
     *   <li>其余（例如 {@code IntProviderIngredient}：数量是随机分布，{@code FluidIngredient}：
     *       工作区里没有它 {@code long} 口径的取证）⇒ <b>不换</b>，保留
     *       {@code recipe.copy(modifier)} 给出的原值。</li>
     * </ul>
     *
     * <p>{@code Content} 的 {@code chance} / {@code maxChance} / {@code perTick} / {@code slotName}
     * / {@code uiName} <b>一个都不碰</b>（连 {@code Content} 对象都不换）。
     *
     * <p>纪律：绝不 {@code clear()}、绝不重建列表、绝不动内容表的键集合
     * （教训 {@code _census/错误历史与工作经验.md} B21）；写不进去只记一行 debug 并保留原值。
     *
     * @return 真的改了返回 {@code true}；跳过 / 失败返回 {@code false}
     */
    private static boolean applyContentAmount(Content content, long amount) {
        if (content == null || amount <= 0L) {
            return false;
        }
        Object inner = content.content;
        try {
            if (inner instanceof LongIngredient longIngredient) {
                content.content = LongIngredient.create(longIngredient.getInner(), amount);
                return true;
            }
            if (inner instanceof SizedIngredient sizedIngredient) {
                content.content = LongIngredient.create(sizedIngredient.getInner(), amount);
                return true;
            }
            if (inner instanceof Long) {
                content.content = Long.valueOf(amount);   // 数值型能力（EUt）：直接换这一格
                return true;
            }
            return false;   // 认不出的内容类型 ⇒ 不猜、不换（保留原值）
        } catch (RuntimeException e) {
            NanochipModContext.logger().debug("[gtlmodular] NAC 内容量 long 直乘：这一份内容换不出 long 数量（{}），保留原值",
                    e.toString());
            return false;
        }
    }

    /**
     * <b>饱和乘法</b>：{@code a × b} 超上限停在 {@link Long#MAX_VALUE}，<b>绝不回绕成负数</b>。
     *
     * <p>判界写法与 {@code NanochipAssemblyComplexMachine.saturatingMul}（{@code :5460}）
     * <b>逐字同构</b>（{@code a > Long.MAX_VALUE / b ? Long.MAX_VALUE : a * b}）——
     * 本类刻意不去调那一个：它是机器类的 {@code private static}，且本类要保持
     * 「纯函数、零依赖同包机器类」（见类注释）。
     *
     * <p>非正因子按「没有量」返回 {@code 0}（调用方读到的绝不是负数）。
     */
    private static long saturatingMultiply(long a, long b) {
        if (a <= 0L || b <= 0L) {
            return 0L;
        }
        return a > Long.MAX_VALUE / b ? Long.MAX_VALUE : a * b;
    }

    /**
     * <b>「从一个上限往下，找最大的、真正放得下的并行值」</b>。
     *
     * @param requestedParallel 上限（调用方已用 {@code IParallelLogic} 收过输入侧）
     * @param canMatch          对某个候选并行数做「真的放得下吗」的实证判定
     * @return 最大的可行值；{@code 0} = 连 1 都放不下（或入参非法）
     */
    public static long findHighestMatchable(long requestedParallel, LongPredicate canMatch) {
        if (requestedParallel <= 0L || canMatch == null) {
            return 0L;
        }
        if (canMatch.test(requestedParallel)) {
            return requestedParallel;
        }
        if (requestedParallel == 1L) {
            return 0L;
        }
        long oneLess = requestedParallel - 1L;
        if (canMatch.test(oneLess)) {
            return oneLess;
        }
        long low = 1L;
        long high = requestedParallel - 2L;
        long best = 0L;
        while (low <= high) {
            long middle = low + ((high - low) >>> 1);
            if (canMatch.test(middle)) {
                best = middle;
                low = middle + 1L;
            } else {
                high = middle - 1L;
            }
        }
        return best;
    }
}
