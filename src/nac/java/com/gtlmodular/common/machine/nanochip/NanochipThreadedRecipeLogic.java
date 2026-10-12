package com.gtlmodular.common.machine.nanochip;

import com.gtlmodular.common.data.nanochip.NanochipModContext;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.feature.ITieredMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;
import com.gregtechceu.gtceu.api.recipe.ingredient.SizedIngredient;
import com.gregtechceu.gtceu.utils.FormattingUtil;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongMaps;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import com.lowdragmc.lowdraglib.side.fluid.FluidStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.gtlcore.gtlcore.api.recipe.IGTRecipe;
import org.gtlcore.gtlcore.api.recipe.IParallelLogic;
import org.gtlcore.gtlcore.api.capability.IMERecipeHandler;
import org.gtlcore.gtlcore.api.machine.trait.IRecipeCapabilityMachine;
import org.gtlcore.gtlcore.api.machine.trait.MEPatternRecipeHandlePart;
import org.gtlcore.gtlcore.api.recipe.RecipeRunnerHelper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * ★★★ 2026-10-28【独立跨配方线程 · 机器侧接法】★★★
 * <b>把「<b>并发批次表</b>」({@link NanochipConcurrentBatches}) 接到本机上：机器该回答的六件事全部在这里，
 * 实现一律走<b>本机确实存在的</b>真实 API。</b>
 *
 * <h2>它是什么（一句话）</h2>
 * 本类是 GTCEu {@code RecipeLogic} 的<b>直接子类</b>，由机器侧既有的扩展点
 * {@code WorkableMultiblockMachine.createRecipeLogic(Object...)} 返回（判定点唯一 =
 * {@code NanochipAssemblyComplexMachine.createRecipeLogic}）⇒
 * <b>不需要任何 mixin</b>，机器基类 / 结构 / 图案 / 模块系统 / 样板总成一律不动。
 *
 * <p>每个 server tick 只做四件事（顺序与参考实现一致，<b>顺序错会出怪事</b>）：
 * <ol>
 *   <li>① ② ③ 全部在 {@link NanochipConcurrentBatches#tick} 里（逐条推进 / 摘掉涨满的 / 补新批次）；</li>
 *   <li>④ 展示回写：把<b>第 0 条</b>批次的进度写回基类的 {@code progress}/{@code duration} 字段
 *       （见 {@link #serverTick} 里标着「④」的那一段）⇒ GUI / Jade 才看得到在动。</li>
 * </ol>
 *
 * <h2>六件事各自用什么真实 API（逐个点名；括号里是取证出处）</h2>
 * <table border="1">
 *   <tr><th>{@link NanochipConcurrentBatches.Host} 六法</th><th>本类怎么实现</th></tr>
 *   <tr><td>{@code machine()}</td><td>基类字段 {@code RecipeLogic.machine}（{@code public final}，
 *       javap 现读 {@code _javap_simple_recipelogic.txt:10}）</td></tr>
 *   <tr><td>{@code slots()}</td><td>{@code NanochipThreadHost.nanochipThreadCount()} → 本机既有线程数阶梯
 *       {@code getMultipleThreads()}（夹 ≥1）</td></tr>
 *   <tr><td>{@code findBatch()}</td><td>候选来自 {@link NanochipCandidateSearch}（<b>跨全部配方类型</b>）
 *       → {@code machine.fullModifyRecipe(recipe.copy(), ocParams, ocResult)}（超频/时长下限/加成）
 *       → {@link NanochipParallelFit#findHighestMatchable}（<b>精确 long 缩放 + 二分「真的放得下」</b>，
 *       含与在跑批次预留的叠加）→ {@code checkConditions} / 预蓄电闸 / 准入闸</td></tr>
 *   <tr><td>{@code consumeInputs()}</td><td>{@code machine.beforeWorking(recipe)} →
 *       {@code recipe.preWorking(machine)} → {@code RecipeRunnerHelper.handleRecipeInput(machine, recipe)}
 *       （<b>本路径上唯一的写入点</b>；返回 false ⇒ 不收这条）</td></tr>
 *   <tr><td>{@code tickInputs()}</td><td>{@code recipe.matchTickRecipe(machine)}（模拟；不够 ⇒ 原地等）→
 *       {@code recipe.handleTickRecipeIO(IO.IN/OUT, machine, caches)}（真扣 EU / tick 流体；
 *       每条批次<b>自己一份</b>概率缓存 {@code makeChanceCaches()}）</td></tr>
 *   <tr><td>{@code finishOutputs()}</td><td>★ <b>搬迁点 H2</b>：{@code nac.recordExternalProduction(recipe)} +
 *       {@code nac.logCraftedRecipe(recipe)}（校准记账与配方日志，★ 2026-10-31【M4②】统一到<b>出料前</b>）
 *       → {@code machine.afterWorking()} → {@code recipe.postWorking(machine)}
 *       → {@code RecipeRunnerHelper.matchRecipeOutput} + {@code handleRecipeOutput}
 *       → 撤批次附注</td></tr>
 * </table>
 *
 * <h2>★ 与「编译不过的那一版」的差别（本轮就是来修它）</h2>
 * 上一版是照着 GTO/GTET 的 API 写的，用了<b>本机不存在</b>的四个方法
 * （{@code RecipeHelper.checkConditions(recipe, this)} / {@code RecipeHelper.matchTickRecipe(machine, recipe)} /
 * {@code RecipeHelper.handleTickRecipeIO(machine, recipe, IO.IN, caches)} /
 * {@code machine.regressWhenWaiting()}），另外还写了基类的 {@code private boolean isActive}
 * 与机器的 {@code private void recordProduction(...)}。本轮逐条改掉：
 * <ul>
 *   <li><b>「条件」是配方自己的实例方法</b>：{@code recipe.checkConditions(this)}
 *       （javap 现读：{@code RecipeLogic.handleRecipeWorking} 的偏移 {@code 31} 就是这条
 *       {@code invokevirtual GTRecipe.checkConditions:(Lcom/gregtechceu/gtceu/api/machine/trait/RecipeLogic;)...}，
 *       见 {@code _javap_simple_recipelogic.txt:397}；它是<b>跨包</b>调用 ⇒ 一定是 public）；</li>
 *   <li><b>「tick 匹配 / tick IO」同样是配方自己的实例方法</b>：
 *       {@code recipe.matchTickRecipe(machine)} 与 {@code recipe.handleTickRecipeIO(io, machine, caches)}
 *       （{@code RecipeLogic.handleTickRecipe} 的两条 {@code invokevirtual}，
 *       见 {@code _javap_simple_recipelogic.txt:681/:689/:694}；{@code GTRecipe} 侧的方法表见
 *       {@code _gtceu_GTRecipe_copy.javap.txt:411} 与 {@code :531}）——<b>这就是基类每 tick 走的那条路</b>；</li>
 *   <li><b>「等待时不退进度」而不是「退到 1」</b>：本机 GTCEu 1.20.1-1.4.4 的 {@code RecipeLogic}
 *       <b>没有</b> {@code regress*} 这一族（全仓 javap 转储 grep {@code regress} 零命中），
 *       基类 {@code handleRecipeWorking} 的做法就是「{@code setWaiting} + 不涨进度」
 *       ⇒ 本类照抄（这同时正是参考实现的语义：{@code 能量/输入不够 ⇒ 原地等，这条不涨进度}）；</li>
 *   <li><b>{@code isActive} 不能写</b>（基类是 {@code private boolean isActive}，
 *       javap 现读 {@code _javap_simple_recipelogic.txt:16}）⇒ 本类不碰它：
 *       {@code isActive()} 这个 public getter 由 {@code isWorking()/isWaiting()/isSuspend()} 自己算出来
 *       （同文件 {@code :902-919}），我们只要把 {@code setStatus(...)} 调对就行；</li>
 *   <li><b>校准记账改走 public 入口</b>：{@code recordProduction} 是机器的 {@code private}
 *       ⇒ 用机器上既有的 {@code public void recordExternalProduction(GTRecipe)}
 *       （{@code NanochipAssemblyComplexMachine.java:3269}，方法体只有一句转发）。</li>
 * </ul>
 *
 * <h2>★ 我们比参考实现强 / 多出来的三处（都是有意保留，不是走样）</h2>
 * <ol>
 *   <li><b>多类型候选</b>：参考实现只搜「当前那一个配方类型」（{@code machine.recipeType}），
 *       本类的候选来源是本机声明的<b>全部</b>类型（{@link NanochipThreadHost#nanochipSearchTypes()}，
 *       简单模式下 = 电路组装表 + 换算表），且与旧逻辑共用同一份 {@link NanochipCandidateSearch}
 *       ⇒ 「跨配方类型的独立线程」这条卖点不会被换逻辑弄丢；</li>
 *   <li><b>每条批次各自一份概率缓存 + 准入预留</b>（参考实现的 Host 把这层留给机器自己）：
 *       概率缓存是为了「各跑各的」不互相串概率（基类那份 {@code chanceCaches} 是「一条配方一份」的语义）；
 *       准入预留是为了「出料口容量 / tick 输入不会因为已经开过批次而变小」这件事在本类里<b>自己记账</b>
 *       （见 {@link #overlayWithReservations}）——有意偏保守：宁可少开一条，也不超发；</li>
 *   <li><b>存档多一格 {@code runs}</b>（见 {@link NanochipConcurrentBatches#save} 的表）：
 *       补回 CODEC 不编的 gtlcore 扩展字段 {@code realParallels}，否则读档后的批次在「概率产出掷骰」时
 *       会按 0 份算 ⇒ 带概率的产出会整体消失。</li>
 * </ol>
 *
 * <h2>开关与回退</h2>
 * 总开关在机器侧：{@code NanochipAssemblyComplexMachine.NAC_INDEPENDENT_THREADS}，
 * 判定点唯一 = {@code createRecipeLogic} 的返回值。关掉它 + 重新编译 ⇒ <b>逐字</b>回到旧逻辑
 * （{@code NanochipRecipeLogic}，一行未删、原封不动并存），本类在那条路上<b>完全不被实例化</b>。
 *
 * <h2>诚实边界（不承诺夸大数字）</h2>
 * <ul>
 *   <li><b>线程数是上限，不是保证值</b>：料不够 / 放不下 / 没电 / 没条件 ⇒ 实际在跑条数 &lt; 上限；
 *       界面就照实显示「上限 N 条 · 在用 k 条」，k 是<b>数出来的</b>；</li>
 *   <li><b>客户端只有一个进度条</b>：基类的单进度字段被镜像成「第 0 条在跑批次」
 *       （见 {@link #mirrorBatchZeroToBaseFields}）⇒ Jade/TOP 那套单进度照旧可用，
 *       但只反映<b>一条</b>批次；整机明细走服务端求值的文本行（{@link #appendThreadDisplayText}）；</li>
 *   <li><b>不做燃油机</b>：本类不处理「燃油配方类型」那一路（基类 {@code handleFuelRecipe}）。
 *       NAC 声明的每一个配方类型（电路组装 / 换算）都不是燃油类型 ⇒ 本条在本机上不可达；
 *       若将来把本类挪到燃油机上，需要补那一段（已登记为未取证项）；</li>
 *   <li><b>不做「同一条配方原地续跑」</b>：基类 {@code onRecipeFinish} 里
 *       {@code alwaysTryModifyRecipe} / {@code recipeDirty} 那条「续跑」尾巴本类不抄 ——
 *       第③步会<b>重新搜一遍并重新扣料</b>，语义上是「下一条」而不是「续跑一条」（NAC 未覆写
 *       {@code alwaysTryModifyRecipe}，两者对它的差别只是搜索频率）；</li>
 *   <li><b>机器级 {@code onWorking()} 每 tick 只在「有批次在跑」时调一次</b>
 *       （基类是「一条配方在跑时每 tick 一次」）—— 不放在每条批次的循环里，
 *       否则维护类回调会被按批次条数放大；</li>
 *   <li><b>单条坏档不影响整表</b>：某条批次的配方解析不出来 / 没有 id ⇒ 只跳过那一条；
 *       已经扣掉的料<b>不退回</b>（与基类 {@code resetRecipeLogic()} 的既有行为一致）。</li>
 * </ul>
 *
 * @see NanochipConcurrentBatches
 * @see NanochipThreadHost
 * @see NanochipCandidateSearch
 * @see NanochipParallelFit
 */
public final class NanochipThreadedRecipeLogic extends com.gtladd.gtladditions.api.machine.logic.GTLAddMultipleTypeRecipesLogic implements NanochipConcurrentBatches.Host {

    // ==========================================================================================
    // 常量
    // ==========================================================================================

    /**
     * 存档里批次表的 NBT 键（★ 追加键，键名沿用上一轮、一字未改；老档没有它 ⇒ 安全回退）。
     *
     * <p>真正的定义在 {@link NanochipConcurrentBatches#TAG_BATCHES}（保存/读取都在那边），
     * 这里只为「键名有一个稳定的引用点」而留一个同名常量（机器侧注释也引用它）。
     */
    public static final String TAG_THREADS = NanochipConcurrentBatches.TAG_BATCHES;

    /**
     * <b>同一条配方去吃「空闲槽位」</b>（fan-out）开关。
     *
     * <p>{@code true}（默认）：同一轮里同一条配方可以占多条批次
     * （整机处理次数上限 ≈ 槽位数 × 每条自己那份并行）。{@code false}：一种配方最多占一条批次
     * （{@link #findBatch} 会跳过已经在跑的候选）。
     *
     * <p>为什么默认开：「按电压给线程」这条阶梯本来就是按「同时挂几条」设计的，
     * 只放一种原料时若第二~N 条必须空着，那条阶梯在单配方场景下等于不存在
     * ——与「显示即事实」（界面写着 N 条却只用 1 条）冲突。关掉它只影响吞吐上限，不影响正确性。
     *
     * <p>★★ 0.3.132【**口径更正**：这一行**不该**关掉，已从「撤自造」名单里摘出】★★
     * <p><b>证据（本仓既有取证，非本轮推断）</b>：本开关是本模组对 <b>GTO 自己那层能力</b>
     * {@code com.gtolib.api.machine.feature.multiblock.ICrossRecipeMachine#isRepeatedRecipes()}
     * 的对应物（逐字对照见 {@code _census/独立跨配方线程_实施记录.md:29}；接口常量池实测见
     * {@code _census/线程定义_GTM与GTO_调查.md:87}）。
     * GTET 的同血统实现把 GTO 的语义写得很清楚（{@code ThreadedRecipeLogic.kt:55-58}）：
     * 「『同一种配方最多占一条线程』原本是 <b>GTO {@code duplicateCheck}</b> 的语义，
     * 代价是<b>只有一种原料时整机只用到 1 条线程</b>」⇒ GTO <b>原生就支持</b>"放开这条去重"，
     * 它就是 {@code isRepeatedRecipes} 这个开关。
     * <p>⇒ 所以：<b>把它关掉不是"对齐 GTO"，恰恰是砍掉一个 GTO 本来就有的能力</b>，
     * 并且会立刻触发上面那条注解写明的回归（单配方场景下"界面写 N 条却只用 1 条"）。
     * <p><b>真正的病根不在这里</b>：K（能开几条）与 pᵢ（每条并行）都只能从"AE 一次推进来的那一份料"里分，
     * 根因是<b>「输入侧上限」只读首槽</b>（{@code IParallelLogic.getMaxParallel} → {@code getStackMapFromFirstAvailableSlot}），
     * 见 {@code _census/NAC合成链路_原初对照与重构方案.md} §⑥-0 与 §⑤-C。治本要走 §⑤-C（把可用量扩到该配方的全部可用槽），
     * <b>不是</b>关这个开关。
     * <p><b>回退/复核提示</b>：本轮一度把它改成 {@code false}（方案 §⑤-B1），经上述取证复核后**已改回 {@code true}**；
     * 若日后再有人要关它，请先读 {@code _census/撤自造放大_回退包/ORIGINAL_HUNKS.md} 与本注释。
     *
     * <p>★★★ 0.3.132【队长裁决：**改为 {@code false}**】★★★
     * <p>队长原话（看到实机截图「一个配方被拆成 6 条线程」之后）：
     * 「<b>这么一个配方，不应该拆出来 6 条线程</b>」「<b>FAN_OUT_SAME_RECIPE 改 false</b>」。
     *
     * <p><b>这次改动的口径（与上面那段取证的关系要说清，不许含糊）</b>：
     * 上面那段说「关掉它 = 砍掉一个 GTO 本来就有的能力」<b>仍然成立</b> ——
     * 本开关**不是**"撤自造"（它不是我们自造的三层之一），关它是一次**有意的取舍**，
     * 不是"修 bug"。取舍的依据是<b>本机的并行口径</b>：
     * 本机现在是「<b>每条配方各自满并行</b>」（去总池，{@code NAC_PER_THREAD_FULL_PARALLEL}）
     * ⇒ <b>一条配方只占 1 条批次时，那条批次照样吃满并行</b> ⇒ 理论上**吞吐不降**，
     * 而 fan-out 在这种情况下**只剩下副作用**：把一个配方摊到多条线程上（与"线程 = 不同配方"的直觉不符）。
     *
     * <p><b>⚠ 已知风险（必须并列登记，别只写好的那面）</b>：
     * 本注释开头那条注解记着一个**曾经真实发生过的回归** ——
     * GTO 的 {@code duplicateCheck} 语义下「<b>只有一种原料时整机只用到 1 条线程</b>」；
     * 而 {@link #findBatch} 的 fan-out 修复（见其 javadoc「同配方 fan-out · 修复」，2026-11-02）
     * 正是为了修「<b>一种样板堆再多料也只开 1 条</b>」。
     * ⇒ 若实机出现**单配方吞吐下降**（同一条配方的产出速率明显变慢），
     * <b>那就是这条风险成真</b>，请立刻改回 {@code true}（一行回退，见下）。
     *
     * <p><b>验收判据（队长令，必须在实机上比一次）</b>：
     * <ol>
     *   <li>同一条配方的**产出速率不变**（用同一台机器、同一种料、同样的 ME 供给，比较单位时间产出）；</li>
     *   <li>界面/Jade 的「在用 N 条」**由多变成 1**（这正是本次要的效果）。</li>
     * </ol>
     * 两条都满足 ⇒ 保留 {@code false}；第 1 条不满足 ⇒ **回退**。
     *
     * <p><b>一行回退</b>：把下面的 {@code false} 改回 {@code true}（本开关是编译期常量，改完重编即可；
     * 不涉及存档、不涉及配置项）。
     */
    public static final boolean FAN_OUT_SAME_RECIPE = false;

    /**
     * ★ 2026-11-02【同配方 fan-out · 每 tick 上限】一个 server tick 里最多<b>新开</b>这么多条批次。
     *
     * <h3>为什么需要它（只有成本原因，不是语义限制）</h3>
     * 开一条批次的成本 = {@code fullModifyRecipe}（复制整条配方 + 超频）+ {@code IParallelLogic.getMaxParallel}
     * + 「精确缩放 + 两侧容量匹配」的二分（最坏 ~31 档，每档一次 {@code scalePrecisely} 复制 + 两次匹配）
     * ⇒ 单条是<b>百微秒级</b>。槽位上限是 256（{@code getMultipleThreads()}）⇒ 一个 tick 里补满 256 条
     * 就是几十毫秒的卡顿。
     *
     * <p><b>它不改变最终能开几条</b>：到达上限只是「这一 tick 先补这么多」，下一个 tick 接着补
     * （{@code NanochipConcurrentBatches.tick} 第③步每个 tick 都会重跑）⇒ 上限只影响<b>补满的速度</b>，
     * 不影响<b>稳态并发条数</b>（配方 100 tick，256 条按 8/tick 补 32 tick 就补满）。
     *
     * <p><b>一行回退</b>：改成 {@code Integer.MAX_VALUE} ⇒ 恢复「一个 tick 内不限量补」。
     */
    public static final int FAN_OUT_MAX_PER_TICK = 8;

    /**
     * ★ 2026-11-02【补料重试 · 每 tick 成本闸】「所有在跑批次都正常推进、且上一 tick 的补料尝试
     * 一条都没开出来」时，是否把<b>又一次注定失败的补料搜索</b>降到每 {@link #FILL_RETRY_INTERVAL} tick 一次。
     *
     * <h3>为什么这笔钱是白花的（也是「延迟」那个数的来源）</h3>
     * 补一条批次 = {@code fullModifyRecipe}（整条配方复制 + 修改器链）+ {@code IParallelLogic.getMaxParallel}
     * + 「精确缩放 + 两侧容量匹配」的二分（最坏 ~31 档，每档一次复制 + 两次匹配）⇒ 单次是<b>百微秒级</b>。
     * 当输入面这一刻的可得份数不够开第 k+1 条时，这一整套在<b>下一 tick 会一模一样地再跑一遍并同样失败</b>
     * —— 材料没变、没有批次跑完、电也没多 ⇒ 结果不可能变（{@link #prepareBatch} 全链只读，见类注释）。
     * 上游 gtlcore 的 Jade「延迟」行量的正是「本机每 tick 自身耗时」⇒ 这笔白跑会直接顶高那个读数。
     *
     * <h3>怎么保证不漏开工时机（硬约束）</h3>
     * <ol>
     *   <li>只有<b>上一 tick 的三件事同时成立</b>才节流：①批次表非空；②上一 tick 真的推进过（status = WORKING）；
     *       ③上一 tick 的补料尝试开了 0 条且<b>没有</b>批次被摘掉（没有跑完的批次腾出产出空间）；</li>
     *   <li>上一 tick 收尾是 WAITING / IDLE（有批次在等电等料 / 表空）⇒ <b>不节流</b>，照旧每 tick 试；</li>
     *   <li>{@link #updateTickSubscription()}（输入侧一变就被叫醒）把冷却清零 ⇒ AE 推料 / 写样板 / 换样板
     *       之后的下一个 tick <b>立刻</b>全量搜；</li>
     *   <li>最坏兜底：即使没有任何事件，也是每 {@link #FILL_RETRY_INTERVAL} tick 至少真试一次（默认 0.5 秒）。</li>
     * </ol>
     *
     * <p><b>它不改变吞吐</b>：被跳过的都是「按当前输入面必然失败」的尝试；真能开出来的那一刻要么输入面变了
     * （唤醒口）、要么有批次跑完（③），两条路都会立刻解除节流（实测判据见 {@link #fillThrottleText()}）。
     *
     * <p><b>一行回退</b>：改成 {@code false} ⇒ 逐字回到「每 tick 都完整试一遍补料」。
     */
    public static final boolean FILL_RETRY_THROTTLE = true;

    /** 见 {@link #FILL_RETRY_THROTTLE}：稳态（都在跑、补不上）时每这么多 tick 至少真试一次补料。 */
    public static final int FILL_RETRY_INTERVAL = 10;

    /**
     * ★ 2026-11-02【输入面读数 · 有界】{@link #inputFaceText()} 一次最多扫多少个槽（诊断用，纯只读）。
     *
     * <p>为什么要一个闸：样板仓可以有 640 槽 × 多个处理器，而「各槽可得量」这一读数是<b>诊断</b>，
     * 不是判定 ⇒ 绝不能让它成为一笔无界的开销。扫到上限就标一个 {@code +}（照实说没扫完）。
     */
    private static final int INPUT_FACE_SCAN_LIMIT = 64;

    /**
     * ★ 2026-11-02【准入预留口径 · 修复】是否把「已在跑批次的<b>非 tick 输入</b>」也叠进准入试算。
     *
     * <h3>为什么默认 {@code false}（= 不叠）—— 这是一处<b>重复计账</b></h3>
     * 非 tick 输入（物品/流体）在<b>开批次那一刻就已经真的扣掉了</b>
     * （{@link #consumeInputs} → {@code RecipeRunnerHelper.handleRecipeInput}，本类唯一的写世界点），
     * 于是机器当下的库存<b>已经</b>反映过这份扣减。若再把它叠进「本条 + 在跑预留」的临时配方，
     * 就等于要求机器<b>再拿一份</b>在跑批次已经吃掉过的料 ⇒ 试算恒偏保守。
     * 极端情形（库存恰好等于一条批次的量）下第二条批次永远算不出正并行 ⇒ <b>fan-out 直接失效</b>。
     *
     * <p>这正是参考实现那一族内核的原话（{@code _census/线程仓机制_移植方案.md:307}）：
     * 「非 tick 输入（物品/流体）开线程那一刻就真扣了 ⇒ <b>天然不会重复计</b>；
     * 但 tick 输入（EUt）与出料口容量在开线程那一刻<b>都还是满的</b> ⇒ k 条线程会各算一遍同一批额度」
     * ⇒ <b>只有 outputs / tickInputs / tickOutputs 需要预留</b>。
     *
     * <p>安全性：非 tick 输入的唯一写入点就是 {@code consumeInputs}，它<b>按次序</b>执行
     * （{@code findBatch} → {@code consumeInputs} → 入表，见 {@link NanochipConcurrentBatches#tick} 第③步）
     * ⇒ 第 k 条批次算并行时，前 k-1 条的料<b>已经</b>不在库存里了 ⇒ 「各自的检查都通过」仍然等价于
     * 「加起来不超过实际库存」，<b>不会超发</b>。
     *
     * <p><b>一行回退</b>：改成 {@code true} ⇒ 逐字回到改前的「四张表全叠」口径。
     */
    public static final boolean RESERVE_NON_TICK_INPUTS = false;

    /**
     * ★ 2026-11-02【并行上限读数口径】给 {@code IParallelLogic.getMaxParallel} 传的 {@code limit} 参数。
     *
     * <p>{@code true}（默认）= 传 {@link NanochipParallelFit#MAX_PARALLEL_SEARCH_BOUND}（{@code int} 边界）；
     * {@code false} = 传 {@code Long.MAX_VALUE}（改前的写法）。
     *
     * <h3>为什么这是一处「读数口径」修复而不是口味问题</h3>
     * 本类能用的并行<b>永远</b>不会超过 {@link NanochipParallelFit#MAX_PARALLEL_SEARCH_BOUND}
     * （下一句就把它钳到那一档，理由见该常量的完整推导）⇒ 传 {@code Long.MAX_VALUE} 与传这一档
     * 对<b>我们能用的值</b>完全等价；但 {@code Long.MAX_VALUE} 会把上游实现拖进
     * 「内容数量 × 天文数字」的算术区（那是它自己的二分/比例计算，我们看不到也管不着）
     * ⇒ 它<b>可能</b>回一个被溢出污染的小数字，而那个数字会被我们当「输入侧上限」原样采信。
     * 传有界值 = 把这个风险从根上掐掉，且<b>只会更准、不会更小</b>。
     *
     * <p>⚠ <b>诚实边界</b>：上游 {@code getMaxParallel} 内部如何消费 {@code limit}，
     * 工作区里只有常量池级证据（{@code _census/独立线程看不见料_修复记录.md:432} 的 U2，✗ 未取证）
     * ⇒ 本条**不宣称**「63 就是溢出造成的」，只宣称「有界传参在语义上无损失、在风险上严格更优」。
     *
     * <p><b>一行回退</b>：改成 {@code false} ⇒ 逐字回到 {@code Long.MAX_VALUE}。
     */
    public static final boolean PARALLEL_LIMIT_BOUNDED = true;

    /**
     * ★★ 0.3.132【按槽开批 · 并行上界】★★ 是否把"能开几份"的搜索上界从
     * <b>上游「第一个有货槽」那一个数</b> 放宽到 {@code max(上游首槽, 按槽最大)}。
     *
     * <p><b>为什么需要它</b>（取证链，逐层给文件）：
     * 上游取输入量的那一刀只读<b>一个</b>槽 ——
     * {@code IParallelLogic.getMaxParallel} → {@code MEPatternRecipeHandlePart.getMEContent}
     * → {@code MEPatternBufferRecipeHandlerTraitBase.getStackMapFromFirstAvailableSlot}
     * → {@code MEPatternBufferPartMachineBase.getFirstActiveInternalSlot}（**遇到第一个 active 槽就 return**）。
     * 而 {@code active} 的定义是"有样板 <b>且</b> 槽里有货"⇒ 槽被吃完就换下一个。
     * ⇒ 现场形态就是"首槽 625 ⇒ 单条并行恒被夹在 625、K 也只有 1~2"。
     * 完整机制与判据见 {@code _census/NAC合成链路_原初对照与重构方案.md} §⑥-0（U3）/ §⑤-C，
     * 以及 {@code _census/按槽开批_实施记录.md}（同款改造的完整规格与取证）。
     *
     * <p><b>为什么只放宽上界是安全的</b>：本开关<b>只改二分的起点</b>；
     * 准入与扣料那一整套（{@code fitsParallel} / {@code hostPrecharged} / {@code canAdmit} /
     * {@code consumeInputs} → 上游 {@code MEPatternRecipeHandlePart.handleRecipe} 的"单槽独立吃下整条需求"）
     * <b>一个字都不改</b> ⇒ 放不下的批次仍然一条都收不进来。
     * 其成立前提是消费侧"<b>一条批次只吃一个槽</b>"⇒"一条批次最多放几份"的正确答案 = 各槽能力的最大值。
     *
     * <p><b>为什么默认 {@code false}</b>：本项是方案 §⑤-C 的治本一步，
     * 方案明确<b>建议先拿实机判据 V2</b>（{@code 输入面[首可用槽可得量 / 全槽汇总可得量]} 两格，
     * 若"全槽汇总 ≫ 首槽"即确诊）<b>再铺开</b>。默认关 ⇒ <b>逐字等于改前</b>（零行为变化）。
     * 确诊后改成 {@code true} 重编即生效。
     *
     * <p><b>一行回退</b>：把本行改回 {@code false}（或保持默认）。
     */
    public static final boolean PER_SLOT_PARALLEL_BOUND = false;

    /** 见 {@link #perSlotInputBound}：{@code -1} = 本次不参与（没有可数的物品/流体输入、或没读到）。 */
    private static final long NO_SLOT_BOUND = -1L;

    /** 见 {@link #perSlotInputBound}：单次最多扫多少个有货槽（成本闸；到闸只按已扫到的算 ⇒ 偏小即保守）。 */
    private static final int PER_SLOT_SCAN_LIMIT = 256;

    /**
     * ★ 2026-11-02【空闲限频】整机 IDLE（没有在跑批次、也没有「等待原因」）时，是否把
     * 「跨全部配方类型枚举候选 + 逐候选试算/二分」降到每 {@link #IDLE_SEARCH_INTERVAL} tick 一次。
     *
     * <h3>为什么会有这笔白跑（逐条取证）</h3>
     * GTCEu 的 {@code RecipeLogic.serverTick()} <b>尾巴</b>有一句「空闲自退订」：
     * 当 {@code lastRecipe == null && isIdle() && !machine.keepSubscribing() && !recipeDirty
     * && lastFailedMatches == null} 时把 {@code subscription.unsubscribe()}
     * （javap 现读 {@code _census/_javap_simple_recipelogic.txt:288-324} 的偏移 {@code 226-246}）。
     * 本类<b>整体覆写了 {@code serverTick} 且从不调 {@code super.serverTick()}</b>
     * ⇒ 这句话永远不会执行 ⇒ tick 订阅<b>永不退订</b> ⇒ 空闲时也每 tick 被叫醒，
     * 白跑一整套「枚举 + 修改器 + 输入侧上限 + 二分 + 匹配」（旧分析实测 7000+ µs，
     * 本轮现场 2200~2600 µs 同源）。
     *
     * <h3>为什么不直接照抄那句退订（更彻底）</h3>
     * {@code subscription} 其实是 {@code protected}（同文件 {@code :42}，不是早前以为的 {@code private}）
     * ⇒ <b>技术上可以</b>照抄。不照抄的理由是<b>副作用面</b>：本类的 {@code serverTick} 第一句还兼着
     * 机器侧每 tick 簿记（{@code nac.nanochipTickSync()}：界面同步文案 / F 位连接刷新 / 未成形缺件审计）
     * ⇒ 一旦退订，这些既有改动会跟着停摆。限频则只砍「白跑的那部分」，其余每 tick 照旧。
     *
     * <h3>怎么保证不漏开工时机（硬约束）</h3>
     * <ol>
     *   <li>{@link #updateTickSubscription()} 被覆写：样板仓内容一变（AE 推料/写样板 → gtlcore
     *       {@code addBufferChangedListener} → 这个方法）就立刻把节流清零 ⇒ 下一个 tick 马上搜；</li>
     *   <li>只有<b>上一 tick 收尾是 IDLE</b> 才节流；上一 tick 是 WAITING（等电/等料/预蓄电闸拦住）
     *       一律<b>每 tick</b>照搜 ⇒ 「刚够电就开工」这类时机一条都不漏；</li>
     *   <li>最坏兜底：即使没有任何唤醒信号，也至少每 {@link #IDLE_SEARCH_INTERVAL} tick 全量搜一次
     *       （默认 10 tick = 0.5 秒）——这仍比上游「空闲多方块根本不轮询」更积极。</li>
     * </ol>
     *
     * <p><b>一行回退</b>：改成 {@code false} ⇒ 逐字回到「每 tick 都搜」。
     */
    public static final boolean IDLE_SEARCH_THROTTLE = true;

    /** 见 {@link #IDLE_SEARCH_THROTTLE}：整机 IDLE 时每隔这么多 tick 至少全量搜一次候选。 */
    public static final int IDLE_SEARCH_INTERVAL = 10;

    /** 「等待原因」文本最多几个 tick 重设一次（避免每 tick 都调 {@code machine.onWaiting()}）。 */
    private static final int WAIT_REASON_INTERVAL = 10;

    /** 「在跑批次条数」诊断日志的限频（tick）；只在数字<b>变化</b>时打一行，绝不刷屏。 */
    private static final int DIAG_INTERVAL = 100;

    /**
     * ★ 2026-11-01【候选为空 · 抓手】「候选为什么开不出批次」那一行的限频（tick）与<b>总量上限</b>。
     *
     * <p>两重限频：① 两次打印至少隔 {@value #CANDIDATE_DIAG_INTERVAL} tick；
     * ② 整局最多 {@value #CANDIDATE_DIAG_BUDGET} 行；③ 内容与上一行完全相同就<b>不打</b>
     * （稳态下通常只出现 1 行）。⇒ 既不刷屏，又能把「第一个拒绝点」留下来。
     */
    private static final int CANDIDATE_DIAG_INTERVAL = 100;

    /** 见 {@link #CANDIDATE_DIAG_INTERVAL}。 */
    private static final int CANDIDATE_DIAG_BUDGET = 12;

    /**
     * ★ 2026-11-01【输入侧接线 · 修复】「把样板仓内部槽 → 机器能力」那条接线<b>重新建一次</b>的最小间隔（tick）。
     *
     * <h3>为什么必须补这一步（本轮定位到的、唯一可断言的新旧差异）</h3>
     * 本机的「输入侧能力」不是 GTCEu 的能力代理，而是 <b>gtlcore</b> 那一套
     * {@code IRecipeCapabilityMachine} 结构（{@code normalCapabilities} /
     * {@code mePatternRecipeHandleParts} / {@code sharedInputRecipeHandlePart} / {@code recipeHandleMap}
     * / 「配方 → 槽」的 {@code restoreMachineCache} 快路径）。它由
     * {@code IRecipeCapabilityMachine.upDate()} → {@code rebuildRecipeHandleParts(...)} 建立，
     * 而 {@code upDate()} 的<b>运行期</b>触发点只有两处（全仓 javap 转储穷举）：
     * <ul>
     *   <li>{@code MEPatternBufferProxyPartMachine.updateIO()}（样板仓代理部件<b>重绑</b>缓冲时。证据：
     *       {@code _census/_javap_simple_gtlcore_proxy.txt:264} 的 {@code invokeinterface upDate:()V}）；</li>
     *   <li>GUI 的 distinct 开关（{@code _javap_simple_handleparts.txt:1249}）。</li>
     * </ul>
     * 因此，仅等待机器继续 tick 并不能保证输入接线会重新建立。
     *
     * <p>旧路径为什么看不出来：gtlcore {@code MultipleRecipesLogic.getRecipe()} 的候选循环里
     * <b>对每条候选都真的调一次扣料</b>（{@code _javap_simple_multirecipes.txt:212-218} 的
     * {@code RecipeRunnerHelper.handleRecipeInput}），而扣料链会把
     * {@code (配方 → 槽)} 记进机器的 {@code recipes2SlotsMap} / {@code setSlotCacheRecipe}
     * ⇒ <b>旧路径的「搜索」本身就在把接线写热</b>（这就是「旧路径靠父类的 tick 把接线驱动起来」这句话的落点）。
     * 本类的搜索阶段是<b>严格只读</b>的（只有 {@code matchRecipeInput} / {@code matchRecipeOutput}），
     * 唯一的写入点是 {@link #consumeInputs}，而它排在 {@link #canAdmit}（也是 {@code matchRecipeInput}）之后
     * ⇒ <b>输入侧一旦是冷的，本类就永远走不到那个写入点</b>（自锁）。
     *
     * <h3>为什么「重新建一次」是纯刷新、绝不涉及配方推进</h3>
     * {@code upDate()} 的全体是：{@code if (!isFormed) return;} →
     * {@code clear()}（只清 gtlcore 自己的标志位与三张表：{@code _javap_simple_handleparts.txt:1022-1075}，
     * <b>不含任何物料/配方写入</b>）→ 遍历部件重建 {@code RecipeHandlePart} →
     * {@code restoreMachineCache}（只<b>读</b>该槽既有的 {@code getSlot2RecipesCache()}）→ {@code sortMEOutput}。
     * <b>全程不出现</b> {@code findAndHandleRecipe} / {@code getRecipe} / {@code setupRecipe} /
     * {@code onRecipeFinish} / {@code handleRecipeInput} ⇒ 不可能双驱、不可能双扣料。
     */
    private static final int INPUT_WIRING_REFRESH_INTERVAL = 100;

    /**
     * ★★ 0.3.131【② 对策 · 重建判据收紧】总开关 ★★
     *
     * <p>{@code true}（<b>默认</b>）⇒ 两步收紧，都只在"能证明重建有益"时才花这笔钱：
     * <ol>
     *   <li><b>只在接线真的不在时才重建</b>：先读一次
     *       {@code getMEPatternRecipeHandleParts().size()}；{@code > 0} ⇒ <b>直接跳过</b>。
     *       依据（本机字节码，逐条）：{@code upDate()} = {@code if(!isFormed) return;}
     *       → {@code clear()} → 遍历部件重建 → {@code restoreMachineCache} → {@code sortMEOutput}
     *       （{@code _census/_javap_simple_handleparts.txt:611-627} / {@code :647-709}）。
     *       而 {@code restoreMachineCache} 是**只读**该部件既有的
     *       {@code getSlot2RecipesCache()}（同文件 {@code :1508-1576}）——
     *       对 gtlcore 具体样板仓，它就是部件机上的 {@code recipeMultipleCacheMap}，
     *       因此控制器重建接线并不等于清除部件已缓存的配方。
     *       {@code clear()} 清的是 gtlcore **自己**的 4 张表（{@code normalCapabilities} /
     *       {@code mePatternRecipeHandleParts} / {@code meOutputRecipeHandleParts} / {@code recipeHandleMap}，
     *       同文件 {@code :1022-1075}），**不含**上面那张"已缓存配方"表。
     *       ⇒ <b>结论：接线存在时重建既不能加热缓存、也不能把缓存清零</b>
     *         （"重建 ⇒ 缓存归零 ⇒ 越修越坏"这个机制<b>不成立</b>，见
     *         {@code _census/跑一次就停_修复记录.md} §②）。
     *       它真正的成本是实害：每次重建都会把样板仓的 buffer-changed 监听器再登记一遍
     *       （{@code addBufferChangedListener}，同文件 {@code :711-739}），而 {@code clear()}
     *       <b>不</b>清 {@code traitSubscriptions} ⇒ 每次 +2 个监听器；
     *       并且会把部件机上的 {@code removeSlotFromMap} 覆盖成新部件的 lambda（同文件 {@code :122-128}），
     *       使旧部件的 {@code recipes2SlotsMap} 成为孤儿。⇒ 接线在的时候，重建是<b>纯成本、零收益</b>。</li>
     *   <li><b>失败即停 + 被证伪即停</b>：{@code upDate()} 抛异常 ⇒ 预算直接清零（永久停手）；
     *       重建前后"接线数 + 已缓存配方数"一字未变 ⇒ 记一次证伪，连续
     *       {@value #INPUT_WIRING_FUTILE_MAX} 次即永久停手。这满足"一生最多 N 次 + 失败即停"。</li>
     * </ol>
     *
     * <p>{@code false} ⇒ <b>逐字退回</b> 0.3.130 口径（空池就重建，只受
     * {@link #INPUT_WIRING_REFRESH_INTERVAL} / {@link #INPUT_WIRING_REFRESH_BUDGET} 两闸）。
     *
     * <p><b>一行回退（逐字）</b>：把本行结尾的 {@code = true} 改成 {@code = false}。
     * 其它代码一个字都不用动（判据收紧全在这一个布尔后面）。
     */
    public static boolean INPUT_WIRING_REBUILD_GATED = true;

    /** 见 {@link #INPUT_WIRING_REBUILD_GATED}：重建"一字未变"连续几次即永久停手（{@value}）。 */
    private static final int INPUT_WIRING_FUTILE_MAX = 2;

    /**
     * 见 {@link #INPUT_WIRING_REFRESH_INTERVAL}：整台机器一生最多重建这么多次。
     *
     * <p><b>为什么要总量上限</b>：{@code rebuildRecipeHandleParts} 会把样板仓的 buffer-changed 监听器
     * 重新登记一次（{@code handleMETraitPart} 的 {@code addBufferChangedListener}，
     * {@code _javap_simple_handleparts.txt:711-739}），而 {@code clear()} <b>不</b>清
     * {@code traitSubscriptions} ⇒ 无节制的重建会累积监听器。上限把它钉死在可接受的量级上
     * （≪ 监听器泄漏阈值），并且只在「真的卡住」时才花这笔钱。
     */
    private static final int INPUT_WIRING_REFRESH_BUDGET = 32;

    /** 线程状态的中英文案键（中英各一条，见 {@code assets/gtlmodular/lang/{zh_cn,en_us}.json}）。 */
    public static final String LANG_THREADS = "gtlmodular.nanochip.threads.status";

    /** 「同时处理 N 次配方运行（各批次之和）」的键。 */
    public static final String LANG_THREADS_RUNS = "gtlmodular.nanochip.threads.runs";

    /** 一行批次明细（位置 / 进度 / 时长 / 并行）的键。 */
    public static final String LANG_THREADS_LINE = "gtlmodular.nanochip.threads.line";

    /** 明细被截断时的尾行（还有 N 条没显示）的键。 */
    public static final String LANG_THREADS_MORE = "gtlmodular.nanochip.threads.more";

    /** 机器面板最多列几条明细（这段文本由服务端每 tick 求值后整表同步给客户端，全列会打爆包）。 */
    public static final int DISPLAY_LINES = 8;

    // ==========================================================================================
    // 状态（全部运行期；批次表另走「追加 NBT 键」，不新增任何 @Persisted 字段）
    // ==========================================================================================

    /** 机器侧只读契约（拿不到 ⇒ 上限退化成 1、不派活；绝不会抛）。 */
    private final NanochipThreadHost host;

    /** ★ 并发批次表本体（数据结构与四步顺序都在它里面）。 */
    private final NanochipConcurrentBatches batches = new NanochipConcurrentBatches();

    /**
     * 每条批次的「附注」：<b>它自己一份</b>概率缓存 + 准入预留。
     *
     * <p><b>为什么按配方对象身份索引</b>（{@link IdentityHashMap}）而不是按下标：
     * 批次表会在遍历中<b>摘掉跑完的那条</b>（下标会整体前移）⇒ 按下标的并行表必然错位。
     * 每条批次的配方对象是开批次时新造出来的（{@code fullModifyRecipe(recipe.copy(), ...)}）
     * ⇒ 对象身份天然唯一、且与批次表同生共死。
     *
     * <p>生命周期：开批次（{@link #consumeInputs}）时写入；收尾（{@link #finishOutputs}）或
     * {@link #resetRecipeLogic} 时删除。读档恢复的批次没有这一格 ⇒ {@link #stateOf} 会按需补一份。
     */
    private final Map<GTRecipe, BatchState> batchStates = new IdentityHashMap<>();

    /** 本 tick 的候选池（惰性：一个 tick 里最多搜一轮全部类型；{@code null} = 还没搜过）。 */
    private List<GTRecipe> candidatePool;

    /** 候选池的游标（同一条配方可以被取第二次 = fan-out）。 */
    private int candidateCursor;

    /** 本 tick 记下的「为什么在等」（取不到就是 null ⇒ 界面只显示「等待」，不显示一个假原因）。 */
    private Component waitReason;

    /** 本 tick 是不是被「预蓄电闸」拦住了（拦住时整机没有批次在跑，但界面要照实显示等待原因）。 */
    private boolean admissionWaiting;

    /** 「等待原因」重设节流。 */
    private int waitReasonCooldown;

    /** 诊断日志节流。 */
    private int diagCooldown;

    /** 上次诊断时打印的「在跑条数」（只在<b>变化</b>时打一行）。 */
    private int diagLastRunning = -1;

    /** 「配方逻辑不可用」诊断只打一次（限频：一次进程最多一条）。 */
    private boolean diagnosedUnavailable;

    // ── ★ 2026-11-01【候选为空 · 抓手】本 tick 的候选读数（每 tick 由 candidateRecipes / 各闸门填） ──

    /** 各类型<b>原始枚举</b>到的候选条数（还没过资格闸）。 */
    private int candidateRawCount;

    /** 通过资格闸（{@link #isCandidateUsable}）的条数。 */
    private int candidateUsableCount;

    /** 本轮搜索的失败说明（逐类型隔离后收集；null = 本轮没有类型抛异常）。 */
    private String candidateSearchFailure;

    /** 本 tick<b>第一条</b>被拒的候选：「配方id → 哪道闸（细节）」。 */
    private String firstReject;

    /** 候选诊断的节流位 + 上一次打印的内容（内容不变就不打）。 */
    private int candidateDiagCooldown;
    private int candidateDiagBudget = CANDIDATE_DIAG_BUDGET;
    private String candidateDiagLast;

    // ── ★ 2026-11-01【并行二分=0 · 限频抽样】只对「批次表为空」的那个 tick 的**第一条**候选做一次
    //    几何细读（输入侧上限 / 二分结果 / 谓词在哪一档、哪一侧失败 / 整条电量） ——
    //    仍在同一行限频日志里，不新增日志行、不新增刷屏面。纯只读：不改任何判定。 ──

    /** 本 tick 抽样的那条候选的 id（null = 本 tick 还没抽样）。 */
    private String fitSampleRecipeId;

    /** 抽样：输入侧上限（{@code IParallelLogic.getMaxParallel} 的原样返回值）。 */
    private long fitSampleInputMax;

    /** 抽样：输入侧上限<b>超过</b>安全搜索上界时为 true（= 原逻辑会走 {@code Long.MAX_VALUE} 溢出区）。 */
    private boolean fitSampleInputMaxOverBound;

    /** 抽样：二分返回的「真的放得下」的并行值。 */
    private long fitSampleMatchable;

    /**
     * 抽样：谓词<b>第一次</b>被判假的并行值（{@link #NO_PROBE} = 一次都没假过 / 没走到）。
     *
     * <p>哨兵取 {@link Long#MIN_VALUE} 而不是 {@code -1}：{@code -1} 与「并行 {@code Long.MAX_VALUE}
     * + 1 的环绕值」撞车，而这里真正要表达的是「从来没发生过」这件事，用一个在并行语义上
     * <b>不可能取值</b>的数最省心。
     */
    private static final long NO_PROBE = Long.MIN_VALUE;

    /** 抽样：谓词第一次判假的那一档并行值（{@link #NO_PROBE} = 没记到）。 */
    private long fitSampleProbeParallel = NO_PROBE;

    /** 抽样：那一次判假的失败侧（{@code 输入} / {@code 产出} / {@code 缩放失败} / {@code 异常:…}）。 */
    private String fitSampleProbeSide;

    /** 抽样：整条配方的 EU 需求（{@code max(1,EUt) × max(1,duration)}）。 */
    private long fitSampleWholeEu;

    /** 抽样：抽样那一刻机器可用电量（{@code getObtainableEnergyEU()}）。 */
    private long fitSampleStoredEu;

    /** 抽样：按需现取（无线电网 / 创造能源仓）成立为 true。 */
    private boolean fitSampleUnlimitedEnergy;

    /** 抽样的预算闸（与候选诊断同一份预算，绝不刷屏）。 */
    private boolean fitSampleArmed;

    /**
     * ★ 2026-11-01【输入侧 · 决定性诊断】抽样时<b>真的问一遍</b>「这份配方现在匹配得上吗」的中间结果。
     *
     * <p>为什么必须单独记：{@code 并行二分=0} 有两种完全相反的成因 ——
     * ① <b>输入侧看不见</b>（{@code matchRecipeInput=false}：料/槽没接上）；
     * ② <b>产出侧放不下</b>（{@code matchRecipeInput=true, matchRecipeOutput=false}：出料口满了）。
     * 原来的几何细读只给「谓词首次判假@N/侧=…」，那是<b>缩放之后</b>的侧；这里补的是<b>原样配方</b>的两侧读数，
     * 于是「连 1 份都匹配不上」与「1 份放得下但放不大」也能一眼分开。
     */
    private boolean fitSampleProbed;

    /** 抽样：这条配方的输入项数（{@code inputs + tickInputs} 的内容条数之和）。 */
    private int fitSampleInputContents;

    /** 抽样：原样配方这一刻 {@code RecipeRunnerHelper.matchRecipeInput} 的结果。 */
    private boolean fitSampleInputMatch;

    /** 抽样：原样配方这一刻 {@code RecipeRunnerHelper.matchRecipeOutput} 的结果。 */
    private boolean fitSampleOutputMatch;

    // ── ★ 2026-11-01【输入侧接线 · 修复 + 决定性诊断】 ──
    //    诊断三格：机器能力代理有没有 / 样板仓接线了几个 / 接线里已经缓存了几条配方。
    //    这三格就是「输入侧是空的还是非空的」的最小可分辨集合（见 inputSideText()）。

    /** 诊断：{@code machine.hasProxies()}（-1 = 没读到）。 */
    private int diagHasProxies = -1;

    /** 诊断：{@code IRecipeCapabilityMachine.getMEPatternRecipeHandleParts().size()}（-1 = 没读到）。 */
    private int diagWireParts = -1;

    /** 诊断：全部样板仓接线 {@code getCachedGTRecipe()} 的条数之和（-1 = 没读到）。 */
    private int diagWireCached = -1;

    /** 上一次「真的跑过一轮候选搜索」时的 {@code 原始枚举} 条数（-1 = 还没搜过）。 */
    private int lastSearchRaw = -1;

    /** 上一 tick 有没有真的重建过一次输入侧接线（诊断用；决定本轮「原始枚举」是不是在刷新之后读的）。 */
    private boolean inputWiringRefreshedThisTick;

    /** 本轮已经重建过几次输入侧接线（累计；与预算一起限住总次数）。 */
    private int inputWiringRefreshes;

    /** 重建输入侧接线的剩余预算（见 {@link #INPUT_WIRING_REFRESH_BUDGET}）。 */
    private int inputWiringBudget = INPUT_WIRING_REFRESH_BUDGET;

    /** 重建输入侧接线的节流位（见 {@link #INPUT_WIRING_REFRESH_INTERVAL}）。 */
    private int inputWiringCooldown;

    // ── ★ 0.3.131【② 对策】重建"做了到底有没有用"的客观读数（纯诊断；只写本类字段，不参与判定） ──
    //    要求逐字：「缓存数 / 接线数 / 重建次数 / 上次重建时刻 / 本次重建是否清空了缓存（前后对比）」
    //    全部打进**既有那一行**（NAC 独立线程候选诊断，见 diagnoseCandidates）——不新增日志行。

    /** 上次重建输入侧接线时的 gameTime（{@code -1} = 还没重建过）。 */
    private long inputWiringLastRefreshTick = -1L;

    /** 上次重建**前 / 后**的样板仓接线数（{@code -1} = 没读到）——直接回答"重建有没有把接线建起来"。 */
    private int inputWiringPartsBefore = -1;
    private int inputWiringPartsAfter = -1;

    /** 上次重建**前 / 后**的已缓存配方数（{@code -1} = 没读到）——直接回答"重建有没有把缓存清零/加热"。 */
    private int inputWiringCachedBefore = -1;
    private int inputWiringCachedAfter = -1;

    /** 重建"前后一字未变"的连续次数（= 重建被证伪）；达到 {@link #INPUT_WIRING_FUTILE_MAX} 即永久停手。 */
    private int inputWiringFutile;

    /** 停手原因（诊断用；{@code null} = 还在允许重建）。 */
    private String inputWiringStopReason;

    /**
     * ★ 2026-11-01【候选为空 · 抓手】本机<b>累计开过多少条批次</b>。
     *
     * <p>存在的唯一理由：把「一条都没开出来」与「开出来了又被清掉」分开 ——
     * 前者是候选/闸门的问题，后者是 {@link #resetRecipeLogic()}
     * （结构失效 / 切模式 / 停机）把在跑批次整表丢掉了（连带已扣的料）。
     * 两种情况的现象都是「在用 0/N」，检修方向却完全相反。
     */
    private long batchesOpenedTotal;

    // ── ★ 2026-11-02【同配方 fan-out · 修复】 ──

    /** 本 tick 已经新开了几条批次（见 {@link #FAN_OUT_MAX_PER_TICK}；每 tick 在 serverTick 里清零）。 */
    private int fanOutOpenedThisTick;

    /**
     * 本 tick「自上次成功开批次以来，已经试过池子里几条候选」——
     * <b>fan-out 轮转的终止条件</b>：整池走完一遍一条都没开出来 ⇒ 这一刻真的没有新批次可开。
     */
    private int fanOutScannedSinceSuccess;

    // ── ★ 2026-11-02【补料重试 · 每 tick 成本闸】 ──

    /** 补料重试的剩余冷却（见 {@link #FILL_RETRY_THROTTLE}）。 */
    private int fillRetryCooldown;

    /** 上一 tick 的补料尝试是不是「一条都没开出来」（节流的前置条件之一，见 {@link #fillRetryThrottled()}）。 */
    private boolean fillOpenedNothingLastTick;

    /** 上一 tick 是不是有批次被摘掉（跑完腾出产出空间 ⇒ 下一 tick 必须真试，绝不节流）。 */
    private boolean fillLostBatchLastTick;

    /** 「补料重试」累计跳过的 tick 数（诊断用；只累加，不参与任何判定）。 */
    private long fillRetrySkippedTicks;

    /** 本类「每 tick 工作段（①②③）」最近一次自耗（µs；纯诊断，见 {@link #recordSelfCost}）。 */
    private long selfCostMicros;

    /** 同上：本进程见过的峰值（µs）。 */
    private long selfCostPeakMicros;

    // ── ★ 2026-11-02【空闲限频 + 唤醒】 ──

    /** 空闲搜索节流的剩余 tick 数（见 {@link #IDLE_SEARCH_THROTTLE}）。 */
    private int idleSearchCooldown;

    /** 本 tick 之前有没有收到「输入侧变化」的唤醒（{@link #updateTickSubscription()}；纯诊断 + 立即解除节流）。 */
    private boolean wokenByInputChange;

    /** 「空闲限频」累计跳过的 tick 数（诊断用；只累加，不参与任何判定）。 */
    private long idleSkippedTicks;

    // ── ★ 2026-11-02【开批次读数 · 决定性诊断】最近一次<b>成功开出</b>的批次的并行是怎么来的 ──
    //    为什么必须有：改前的诊断只覆盖「候选被拒」（fitSampleArmed 只在批次表为空时武装），
    //    而现场是「开出来了、在用 1 条、并行 63」⇒ 成功那条路径上的数字从来没有落过日志。

    /** 最近一次成功开批次的配方 id（null = 还没开过）。 */
    private String lastOpenRecipeId;

    /** 最近一次开批次时 {@code IParallelLogic.getMaxParallel} 的<b>原样</b>返回值。 */
    private long lastOpenInputMax = -1L;

    /** 最近一次开批次时我们真正允许搜索的上界（= {@code min(输入侧上限, 安全上界)}）。 */
    private long lastOpenBound = -1L;

    /** 最近一次开批次采用的并行值。 */
    private long lastOpenParallel;

    /** 最近一次开批次的「并行是从哪一步来的」（第一刀命中 / 第二刀命中 / 二分收敛 / 输入侧上限≤0）。 */
    private String lastOpenBranch;

    // ── 本次 fitParallel 调用的中间读数（每次调用覆盖；成功开批次时由 prepareBatch 抄进 lastOpen*） ──

    /** 本次 {@link #fitParallel} 读到的输入侧上限（原样）。★ 按槽开批后 = 搜索上界，见下两个分量。 */
    private long fitInputMax = -1L;

    // ── ★ 0.3.132【按槽开批】上界的两个分量（纯诊断；只在 PER_SLOT_PARALLEL_BOUND 为真时有意义） ──

    /** 上界的"上游首槽"分量（= 改前唯一口径，原样读数）。 */
    private long fitUpstreamInputMax = NO_SLOT_BOUND;

    /** 上界的"按槽最大"分量（{@code -1} = 不适用/未读）。 */
    private long fitSlotInputMax = NO_SLOT_BOUND;

    /** 本次 {@link #fitParallel} 实际使用的搜索上界（钳制之后）。 */
    private long fitBound = -1L;

    /** 本次 {@link #fitParallel} 采用的并行值。 */
    private long fitParallelValue;

    /** 本次 {@link #fitParallel} 的「值从哪来」说明。 */
    private String fitBranch;

    /** 一条批次的附注（概率缓存 + 准入预留）。 */
    private static final class BatchState {

        /** 这一条批次<b>自己的</b>概率缓存（基类那份是「一条配方一份」的语义，共用会串概率）。 */
        private Map<RecipeCapability<?>, Object2IntMap<?>> caches;

        /** 这一条批次「这一轮要吃的输入 / 要吐的产出」的预留（开批次时算好、跑完撤掉）。 */
        private GTRecipe reservation;

        /**
         * ★ 2026-11-02【多线程显示 · 稳定编号】这条批次对外显示的 {@code #N}
         * （开批次那一刻分配、跑完为止不变；理由与用法见 {@link #threadLabelOf}）。0 = 还没分配。
         */
        private long seq;
    }

    /** 下一批次的显示编号（单调递增；纯显示用，不参与任何判定）。 */
    private long batchSeqCounter;

    // ==========================================================================================
    // 构造 / 读取口
    // ==========================================================================================

    /**
     * @param candidateHost 期望是 {@link NanochipThreadHost} 的实现（本机 = NAC）
     */
    public NanochipThreadedRecipeLogic(NanochipAssemblyComplexMachine candidateHost) {
        // ★ 0.3.118：本类挂回上游"多配方"族（父链 WorkableElectricMultipleRecipesMachine.getRecipeLogic()
        //   会把返回值硬转成该族类型），因此 super 必须调该族构造器 —— 其签名为 (机器, Predicate<IRecipeLogicMachine>)。
        //   谓词给"全部接受"（不做类型过滤，配方类型集合由我们的 NanochipCandidateSearch 决定）。
        super(candidateHost, machine -> true);
        // ⚠️ 构造顺序：本构造函数在机器父类构造函数里就被调用，那时机器自己的字段还没跑
        //    ⇒ 这里只做一次 instanceof 判定，不读机器的任何字段。
        NanochipThreadHost resolved;
        try {
            resolved = candidateHost;   // ★ 0.3.118：入参类型已保证实现该接口，无需 instanceof（旧写法在 -source 下报"恒真模式"）
        } catch (RuntimeException e) {
            // 类加载/桥接阶段的任何异常都只降级，绝不外抛（本类跑在机器构造链上）
            NanochipModContext.logger().debug("[gtlmodular] NAC 独立线程：机器未实现 NanochipThreadHost ⇒ 不派活：{}",
                    e.toString());
            resolved = null;
        }
        this.host = resolved;
    }

    /** 本机这一刻的批次上限（= 界面那一行的数字；取不到 = 1 ⇒ 退化成「一次一条配方」）。 */
    public int getThreadLimit() {
        if (host == null) {
            return 1;
        }
        return Math.max(1, host.nanochipThreadCount());
    }

    /** 正在跑的批次条数（<b>数出来的</b>，不是上限）。 */
    public int runningThreadCount() {
        return batches.size();
    }

    /**
     * ★★ 0.3.132【① 读数：把「K 条」与「K 种」分开】★★
     * <b>本刻正在跑的批次里，按配方 id 去重之后有几种<b>不同</b>配方。</b>
     *
     * <h4>两个口径的区分（队长下一轮的判据，别把这两个数混成一个）</h4>
     * <ul>
     *   <li><b>「K 条」= 批次条数</b> = {@link #runningThreadCount()}（{@code batches.size()}）
     *       —— 只回答「这台机器这一刻挂着几条线程」；</li>
     *   <li><b>「K 种」= 本方法</b> —— 把上面那 K 条<b>按配方 id 去重</b>后剩下的不同配方种数。
     *       ⇒ {@code 种数 == 条数} 表示这 K 条线程跑的确实是 <b>K 条不同配方</b>；
     *       {@code 种数 &lt; 条数} 表示其中有<b>同一条配方占了多条</b>（fan-out，
     *       见 {@link #FAN_OUT_SAME_RECIPE}）。</li>
     * </ul>
     *
     * <h4>为什么要有这个读数（队长这条诉求的落点）</h4>
     * 队长要的效果（对齐 GTO 文案「同时处理至多 16 种不同配方，每种配方至多 65,536 个」）：
     * <b>一条线程 = 一个【不同】配方，多个不同配方同时跑</b>。
     * 而在此之前，界面/Jade 上能看到的只有「在用 K 条」这一个数 ⇒ <b>分辨不出</b>
     * 「K 条线程跑着 K 种不同配方」与「K 条线程全在同一条配方上（fan-out 摊开）」——
     * 两种情况的观感完全一样，判定只能靠猜。本读数就是为这条判定而加的：
     * {@code 种数 == 条数 且 条数 > 1} ⇒ 确实在并行跑不同的配方。
     *
     * <h4>去重口径（逐条；纯只读）</h4>
     * <ol>
     *   <li>取 {@code recipe.getId()}（类型 {@code ResourceLocation}）——类型与调用方式<b>照本包既有写法</b>
     *       （{@code NanochipConcurrentBatches.java:251} 用的就是同一个 {@code getId()}，
     *       并配 {@code setId(...)}，见同文件 {@code :294}）；</li>
     *   <li><b>id 非 null ⇒ 按 id 去重</b>（{@link HashSet}）：同一条配方的多份副本因此被算成<b>一种</b>
     *       —— 每次开批次都走 {@code fullModifyRecipe(recipe.copy(), ocParams, ocResult)}
     *       新建对象（见 {@link NanochipCandidateSearch} 那一链），对象各不相同而 id 相同；</li>
     *   <li><b>id 为 null ⇒ 退回对象引用去重</b>（{@link IdentityHashMap}）。
     *       <b>这里绝不能</b>改用 {@code GTRecipe.equals / hashCode} 兜底：本包已取证它<b>只比 id</b>
     *       （见本文件 {@code findBatch} 的 javadoc 与 {@code NanochipThreadedRecipeLogic.java:2032-2033}，
     *       依据 {@code _census/_gtceu_GTRecipe_copy.javap.txt:1178-1201}）
     *       ⇒ 两条 id 都为 null 的<b>不同</b>配方会被它判成同一个而<b>误合并</b>（种数偏小 = 少报）。
     *       引用去重是「同一份对象只算一次」，宁可把 null-id 的当两种，也绝不并错一种；</li>
     *   <li><b>整条批次为 null / 配方为 null</b> ⇒ 跳过那一条（既不加也不减）——
     *       与 {@link #realParallelsOf} 对 null 的处理同一风格（早退，绝不外抛）。</li>
     * </ol>
     *
     * <p><b>纯只读</b>：只遍历批次表 {@code batches} 与读配方 id，<b>不写任何字段</b>、不进 NBT、
     * 不参与任何判定（并行 / 准入 / 扣料 / 模式切换一律不碰）。
     * 没有批次 ⇒ 返回 {@code 0}（与 {@link #runningThreadCount()} 的 {@code 0} 并列显示，不撒谎）。
     *
     * <p><b>一行回退</b>：删掉下面整个方法，并删掉 {@code NanochipThreadedJadeProvider} 里那一句
     * {@code data.putInt(KEY_DISTINCT_RECIPES, ...)} 与 {@code appendTooltip} 里那一句
     * {@code appendDistinctRecipes(tooltip, data);}（外加两份 lang 的那一个键）⇒ 逐字回到改动前。
     *
     * @return 不同配方（按 id 去重，id 为 null 时按对象引用）的种数；{@code 0} = 没有批次
     */
    public int distinctRunningRecipeCount() {
        int size = batches.size();
        if (size <= 0) {
            return 0;   // 无批次 ⇒ 0 种（与 runningThreadCount() 同步为 0，界面照实显示）
        }
        Set<ResourceLocation> ids = new HashSet<>(size);
        Map<GTRecipe, Boolean> seenNoId = new IdentityHashMap<>();
        int distinct = 0;
        for (int i = 0; i < size; i++) {
            NanochipConcurrentBatches.Batch batch = batches.get(i);
            GTRecipe recipe = batch == null ? null : batch.recipe;
            if (recipe == null) {
                continue;   // 坏档兜底：这一条既没有对象也没有 id ⇒ 跳过（不虚增一种，也不因此少算别的）
            }
            ResourceLocation id = recipe.getId();
            if (id != null) {
                if (ids.add(id)) {
                    distinct++;
                }
            } else if (seenNoId.put(recipe, Boolean.TRUE) == null) {
                // id 为 null：按**对象引用**去重（IdentityHashMap 用的是 == / identityHashCode，
                // 不走 GTRecipe 那套「只比 id」的 equals ⇒ 不会把两条无 id 的不同配方并成一种）。
                distinct++;
            }
        }
        return distinct;
    }

    /**
     * ★ 2026-11-02【沿用原生进度条】原生那条进度条代表的线程编号
     * （= {@link #mirrorBatchZeroToBaseFields} 写回基类字段的那一条 = <b>批次表第 0 条</b>）。
     *
     * <p>用途：Jade 客户端据此把这一条从「追加的条」里排除（原生条已经画了它），
     * 并在原生条下面补一行说明（原生条本身不带我们的编号 / 并行）。
     *
     * @return 稳定编号；{@code -1} = 这一刻没有批次
     */
    public int mirroredThreadLabel() {
        return batches.isEmpty() ? -1 : threadLabelOf(batches.get(0).recipe);
    }

    /** ★ 见 {@link #mirroredThreadLabel()}：那一条的并行（与「同时处理 R 次」同一口径）。 */
    public long mirroredThreadParallels() {
        return batches.isEmpty() ? 0L : Math.max(1L, realParallelsOf(batches.get(0).recipe));
    }

    /** 第 {@code index} 条在跑批次（展示/联动读口；与 {@link NanochipConcurrentBatches#get} 同一份数据）。 */
    public NanochipConcurrentBatches.Batch batchAt(int index) {
        return batches.get(index);
    }

    /**
     * 批次表的只读快照（{@code 编号 → 记录}）—— <b>既有读口</b>（Jade provider 与机器面板都用它）。
     *
     * <p>与参考实现的读口（{@code size()} + {@code get(i)}）是同一份数据的两种写法：
     * 这里把它包成「编号 → {@link ThreadRec}」的条目列表，于是两条显示通路
     * （{@code NanochipThreadedJadeProvider} 写 serverData、机器面板拼面板文本）
     * <b>共用同一份顺序与同一份数字</b> ⇒ 不可能出现「面板与 Jade 打架」。
     *
     * <p>★ <b>2026-11-02 两处口径变更</b>（都在这一处，两条通路同时受益）：
     * <ol>
     *   <li>key 由「表内下标」改成<b>稳定编号</b>（{@link #threadLabelOf}）；</li>
     *   <li>顺序改成<b>进度降序</b>（同进度按编号升序），理由见方法内注释。</li>
     * </ol>
     *
     * <p>{@link ThreadRec} 是 {@link NanochipConcurrentBatches.Batch} 的<b>只读适配</b>
     * （不另存一份状态，读取即转发）⇒ 不会出现「两份进度不一致」。
     */
    public List<Map.Entry<Integer, ThreadRec>> snapshot() {
        List<Map.Entry<Integer, ThreadRec>> out = new ArrayList<>(batches.size());
        for (int i = 0; i < batches.size(); i++) {
            NanochipConcurrentBatches.Batch batch = batches.get(i);
            out.add(Map.entry(threadLabelOf(batch.recipe), new ThreadRec(batch)));
        }
        // ★ 2026-11-02【多线程显示 · 顺序】：**进度降序**（最接近完工的排最前），同进度按批次序号升序。
        //   理由（写死在报告 §「多线程显示设计与理由」）：玩家看 Jade/面板是为了「哪条快好了 / 机器是不是在动」
        //   ⇒ 把最近的完工点摆在最上面；同进度用**稳定序号**排序 ⇒ 同一 tick 内两次读取顺序一致（不抖动）。
        out.sort((a, b) -> {
            int pa = a.getValue().progress();
            int pb = b.getValue().progress();
            if (pa != pb) {
                return Integer.compare(pb, pa);
            }
            return Integer.compare(a.getKey(), b.getKey());
        });
        return out;
    }

    /**
     * ★ 2026-11-02【多线程显示 · 稳定编号】一条批次对外显示的编号（{@code #N}）。
     *
     * <p><b>为什么要换掉「表内下标」</b>：批次表会在遍历中<b>摘掉跑完的那条</b>
     * （见 {@code NanochipConcurrentBatches.tick} 第②步）⇒ 下标整体前移，同一批次的编号每 tick 都在变；
     * 而本轮之后<b>同一条配方可以同时占多条批次</b>（fan-out）⇒ 玩家更需要一个「认得出是哪条」的稳定编号。
     * 本编号在<b>开批次（{@code consumeInputs} 成功）那一刻分配</b>，到这条批次跑完为止不变；
     * 读档恢复的批次第一次被显示时按需补一个（{@link #stateOf}）。
     *
     * <p>纯显示用：不参与任何判定、不进 NBT、不同步。
     */
    private int threadLabelOf(GTRecipe recipe) {
        if (recipe == null) {
            return 0;
        }
        BatchState state = stateOf(recipe);
        if (state.seq <= 0L) {
            state.seq = ++batchSeqCounter;
        }
        return (int) (state.seq % Integer.MAX_VALUE);
    }

    /**
     * 一条在跑线程的<b>只读视图</b>（{@link NanochipConcurrentBatches.Batch} 的适配器）。
     *
     * <p>存在的理由：Jade 那条显示通路按「编号 + 进度/时长 + 本条并行」取数，
     * 它的入参类型是本类的 {@code ThreadRec} ⇒ 本类把批次表适配成这个类型，
     * <b>而不是</b>让两处各自持有一份进度（那才会出现「面板与 Jade 数字打架」）。
     */
    public static final class ThreadRec {

        private final NanochipConcurrentBatches.Batch batch;

        private ThreadRec(NanochipConcurrentBatches.Batch batch) {
            this.batch = batch;
        }

        /** 这条线程的配方。 */
        public GTRecipe recipe() {
            return batch.recipe;
        }

        /** 这条线程自己的进度（tick）——「各自计时」的那一格。 */
        public int progress() {
            return batch.progress;
        }

        /** 这条线程钉死的总时长（tick）。 */
        public int duration() {
            return batch.duration;
        }

        /**
         * 这条线程<b>本 tick 真实的那份并行</b>（显示口径 = {@link #realParallelsOf}）。
         *
         * <p><b>为什么不让显示层直接读 {@code recipe.parallels}</b>（改前就是这么读的）：
         * 整机那一行「同时处理 R 次配方运行」取自 {@code IGTRecipe.getRealParallels()}
         * （{@link #totalRunsAtOnce()}）⇒ 若逐条那列取另一个字段，两者就可能打架。
         * 这里统一到同一个口径 ⇒ <b>「逐条并行之和」恒等于「同时处理 R 次」</b>（数字口径纪律：显示即事实）。
         */
        public long parallels() {
            return Math.max(1L, realParallelsOf(batch.recipe));
        }
    }

    // ══════════════════ ★ 2026-11-04【Jade 逐线程产物 · 只读取值口】 ══════════════════
    // 队长原话（逐字）：「你看到的截图里面也有，同时跑 6 个不同的配方，但是那边能看到的产物就只显示一种，
    // **得让 6 个产物都显示呢**」／「**每条线程的产物都得看到**」。
    // 上游那行「配方输出: 250 × [封装 超因果RAM芯片]」读的是机器的「当前/上一次配方」⇒ 天生只反映一条，
    // 而「每条线程各产什么」只有批次表知道 ⇒ 由**我们自己的** Jade 小节列出（不去改上游）。
    //
    // 本段只加**两个纯只读取值口**（外加两个 private 口径助手）：值由
    // NanochipThreadedJadeProvider#appendServerData 写进 serverData；客户端只负责显示，
    // **绝不复算任何判定**（本工程铁律）。
    //
    // ⚠ 为什么是"镜像"而不是"复用"：识别/计数的权威实现在
    // NanochipAssemblyComplexMachine#firstItemStackOf:4569-4597 与 #producedCountOf:4612-4621，
    // 但两者都是 **private static**（同坑的 NanochipSelfCheck#itemIdOf:1282-1298 也是 private），
    // 本包取不到；而那个类又不在本轮允许改动的文件里 ⇒ 这里**逐字镜像**那两段：三路兜底
    // （ItemStack / ItemStack[] / Ingredient）＋计数顺序（LongIngredient.getActualAmount() →
    // SizedIngredient.getAmount() → 栈自身 count → 1）。口径必须与它们一致 ——
    // 本包（gtlcore 1.2.3.2）物品产出一律是 `gtceu:sized` ⇒ 反序列化后 content 一次都不是 ItemStack，
    // 只判 `instanceof ItemStack` 会恒假（M1 那个坑，见上面两处的注释）。

    /**
     * 只读：这条线程（批次）的第一个<b>可识别物品产物</b>的<b>物品注册名</b>
     * （{@code namespace:path}，例 {@code gtlmodular:cc_crystal_processor} / {@code gtceu:...}）。
     *
     * <h4>★★ 2026-11-05【语义变更：本地化键 → 物品注册名】（队长要「给现成产物加图标」）</h4>
     * 队长原话（逐字）：<b>「能不能给这些现成产物加上图标」</b>。
     * <p><b>改前的语义</b>：{@code ItemStack#getDescriptionId()}（形如
     * {@code item.gtlmodular.cc_crystal_processor}）= <b>本地化键</b>。
     * <b>它画不出图标</b>：这个串<b>只能</b>拿去做 {@code Component.translatable(...)} 取译名，
     * <b>反查不回 {@code Item}</b>（键→物品没有注册表映射）⇒ 客户端拿不到 {@code ItemStack}
     * ⇒ 拿不到 {@code IElement} 图标。<b>这就是必须先换载荷的原因，不是口味问题。</b>
     * <p><b>改后的语义</b>：{@code BuiltInRegistries.ITEM.getKey(stack.getItem())} 的
     * {@code ResourceLocation#toString()} = <b>注册名</b>（{@code namespace:path}）。
     * 客户端 {@code ResourceLocation.tryParse(id)} → {@code BuiltInRegistries.ITEM.get(...)}
     * → {@code new ItemStack(item)} ⇒ 既能取译名（{@code stack.getDescriptionId()}，
     * 与本方法改前送出的串<b>逐字相同</b> ⇒ 文案一个字不变），又能建<b>物品图标元素</b>。
     *
     * <p><b>为什么回传"注册名"而不是名字 / ItemStack / 配方对象</b>：服务端<b>没有</b>客户端的语言，
     * 在服务端取 {@code stack.getHoverName().getString()} 在专用服务器上只会得到 lang 键本身；
     * 而回传 ItemStack 全量 NBT 会把 Jade 的载荷翻好几倍（载荷纪律见
     * {@code NanochipThreadedJadeProvider#threadTag} 的注释）。⇒ 只送一个 ≤ 约 60 字符的 ASCII 串，
     * 客户端自己按<b>玩家自己的语言</b>渲染译名、自己重建图标。注册名与本地化键<b>长度同量级</b>
     * （例：{@code gtlmodular:cc_crystal_processor} 30 字符 / {@code item.gtlmodular.cc_crystal_processor}
     * 35 字符）⇒ 载荷量级不变（逐项估算见 {@code threadTag} 的 javadoc）。
     *
     * <p><b>纯只读</b>：不改配方、不改批次表、不进存档、不参与任何判定。
     *
     * <p>⚠ <b>方法名保留</b>（{@code ...OutputId}）：返回的就是"产物这件物品的 id"
     * —— 只是这个 id 从<b>翻译用 id</b>换成了<b>注册用 id</b>。不改名是为了不动调用点与既有注释锚点。
     *
     * @return 注册名（{@code namespace:path}）；{@code null} = <b>取不到</b>（没有物品产物 / 纯流体产物 /
     *         content 识别不出 / Ingredient 未解析 / <b>注册表里查不到这件物品</b>）——
     *         调用方据此<b>不写键</b>，客户端照实写「无物品产物或未识别」
     */
    public static String firstItemOutputId(GTRecipe recipe) {
        Content content = firstItemOutputContent(recipe);
        if (content == null) {
            return null;
        }
        ItemStack stack = itemStackOfContent(content.content);
        if (stack == null || stack.isEmpty()) {
            // 边界【空气】：itemStackOfContent 已经过滤过 isEmpty，这里是第二道闸（防御性，不改变口径）。
            return null;
        }
        // ★★ 语义变更点（本方法唯一一处实质改动）：本地化键 → 物品注册名。
        //   · 注册表命中：返回 `namespace:path`（客户端据此重建 ItemStack 画图标）。
        //   · 查不到（未注册的物品 / 物品被数据包换掉）：返回 null ⇒ 调用方不写键
        //     ⇒ 客户端照实写「无物品产物或未识别」，**绝不伪造**一个 id，也绝不画空图标。
        //   · BuiltInRegistries 是静态注册表，客户端/服务端查同一份 ⇒ 客户端重建必然与这里同解。
        ResourceLocation id;
        try {
            id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        } catch (RuntimeException e) {
            // 注册表自身异常（理论上不可达）⇒ 收敛成"取不到"，绝不掀翻 Jade 的写数据路径。
            return null;
        }
        return id == null ? null : id.toString();
    }

    /**
     * 只读：与 {@link #firstItemOutputId} <b>同一个产物</b>的数量。
     *
     * <p>★ <b>这个数已含并行 p，绝不再乘</b>：进批次表的配方是
     * {@code NanochipParallelFit.scalePrecisely(modified, parallel)} 缩放后的<b>同一个对象</b>
     * （本类开批次路径 {@code :2030} / {@code :2458} → {@code NanochipConcurrentBatches.tick:205}
     * 把它放进 {@link NanochipConcurrentBatches.Batch#recipe}），而 {@code scalePrecisely} 走
     * {@code recipe.copy(ContentModifier.multiplier(p), false)}（输入输出一并放大）
     * ⇒ {@code LongIngredient.getActualAmount()} / {@code SizedIngredient.getAmount()} 读到的
     * 就是「每份 × p」= 这一批的<b>真实产出总数</b>。证据链与结论逐字同
     * {@code NanochipAssemblyComplexMachine#recordProduction:4978-5016}（那里定案的缺陷正是
     * 「再乘一次并行 ⇒ 记成 每份 × p²」）⇒ 本方法<b>只读内容数量</b>。
     *
     * <p><b>纯只读</b>：与 {@link #firstItemOutputId} 同一份数据、同一顺序，两次调用各自遍历
     * 物品产物表（该表 1~3 项，成本可忽略）。
     *
     * @return 数量（≥ 1）；{@code 0} = 取不到（理由同 {@link #firstItemOutputId}）
     */
    public static long firstItemOutputAmount(GTRecipe recipe) {
        Content content = firstItemOutputContent(recipe);
        return content == null ? 0L : producedAmountOfContent(content.content);
    }

    /**
     * 见 {@link #firstItemOutputId}：第一个「能识别出物品」的产物内容；{@code null} = 取不到。
     *
     * <p>只认物品产物（{@code ItemRecipeCapability.CAP}）：纯流体产物的线程**没有**物品产物可显示
     * ⇒ 返回 {@code null}，由显示层照实写「无物品产物或未识别」，<b>不</b>拿别的键顶上、<b>不</b>编数量。
     * 与校准记账同款：{@code recordProduction:4974} 也只读这一个 capability，且不按 {@code chance} 过滤。
     */
    private static Content firstItemOutputContent(GTRecipe recipe) {
        if (recipe == null) {
            return null;
        }
        List<Content> outs;
        try {
            outs = recipe.getOutputContents(ItemRecipeCapability.CAP);
        } catch (RuntimeException e) {
            // 配方被数据包换掉 / 未解析 ⇒ 当作"取不到"。调用方是 Jade 的写数据路径，
            // 任何意外都只该表现为"这一条不显示产物"，绝不掀翻准星提示或服务端主线程。
            return null;
        }
        if (outs == null || outs.isEmpty()) {
            return null;
        }
        for (Content c : outs) {
            if (c != null && itemStackOfContent(c.content) != null) {
                return c;
            }
        }
        return null;
    }

    /**
     * 见 {@link #firstItemOutputId}：三路兜底取物品栈
     * （<b>逐字镜像</b> {@code NanochipAssemblyComplexMachine#firstItemStackOf:4569-4597}）。
     */
    private static ItemStack itemStackOfContent(Object content) {
        if (content instanceof ItemStack stack) {
            return stack.isEmpty() ? null : stack;
        }
        if (content instanceof ItemStack[] stacks) {
            for (ItemStack stack : stacks) {
                if (stack != null && !stack.isEmpty()) {
                    return stack;
                }
            }
            return null;
        }
        if (content instanceof Ingredient ingredient) {
            try {
                ItemStack[] stacks = ingredient.getItems();
                if (stacks != null) {
                    for (ItemStack stack : stacks) {
                        if (stack != null && !stack.isEmpty()) {
                            return stack;
                        }
                    }
                }
            } catch (RuntimeException e) {
                // 未解析的 tag / 空 Ingredient ⇒ 取不到（同 firstItemStackOf 的处理：
                // 只把"取不到"收敛成 null，不吞掉别的逻辑错误）
                return null;
            }
        }
        return null;
    }

    /**
     * 见 {@link #firstItemOutputId}：一份产出内容标称产出多少个
     * （<b>逐字镜像</b> {@code NanochipAssemblyComplexMachine#producedCountOf:4612-4621}）。
     *
     * <p>必须优先读 Ingredient <b>自带的份数</b>：{@code SizedIngredient} / {@code LongIngredient}
     * 的 {@code getItems()} 给的是<b>单份</b>样品（count 1），真实份数存在包装里（例：晶体处理器 8 个）。
     */
    private static long producedAmountOfContent(Object content) {
        if (content instanceof org.gtlcore.gtlcore.api.recipe.ingredient.LongIngredient longIngredient) {
            return longIngredient.getActualAmount();
        }
        if (content instanceof SizedIngredient sized) {
            return sized.getAmount();
        }
        ItemStack stack = itemStackOfContent(content);
        return stack == null ? 1L : Math.max(1L, (long) stack.getCount());
    }

    /**
     * 「整机此刻同时处理多少次配方运行（各批次之和）」。
     *
     * <p>取数口径：gtlcore 的 {@code IGTRecipe.getRealParallels(): long}
     * （javap 现读：{@code _census/_javap_simple_reciperunner.txt:174}；
     * 本包 {@link NanochipParallelFit#scalePrecisely} 也正是写它）—— 即
     * 「各批次自己那一份并行之和」，<b>不是</b>功率倍率、也不用它算耗电。
     * 取不到增强接口时退回 {@code recipe.parallels}（≥1）。
     */
    public long totalRunsAtOnce() {
        long total = 0L;
        for (int i = 0; i < batches.size(); i++) {
            long runs = Math.max(1L, realParallelsOf(batches.get(i).recipe));
            total = total > Long.MAX_VALUE - runs ? Long.MAX_VALUE : total + runs;
        }
        return total;
    }

    // ==========================================================================================
    // 每 tick：① ② ③ 在批次表里，④ 展示回写在下面
    // ==========================================================================================

    /**
     * 每 tick 的主循环。
     *
     * <p>顺序：
     * <ol>
     *   <li>停机 / 零件表不可用 ⇒ 直接返回（<b>不推进、也不清批次表</b>：不让「暂时不可用」变成「吞料」）；</li>
     *   <li>★ 补齐旧逻辑那一句 {@code nac.tickSync()}（与旧逻辑<b>同频</b>：每 tick 一次）；</li>
     *   <li>机器级 {@code onWorking()} 否决（有批次在跑时每 tick 问一次；问不到「不许」就谁都别推进）；</li>
     *   <li>①②③ = {@link NanochipConcurrentBatches#tick}；</li>
     *   <li><b>④ 展示回写</b>：把第 0 条的进度写进基类的同步字段；</li>
     *   <li>状态机 + 诊断日志。</li>
     * </ol>
     */
    @Override
    public void serverTick() {
        if (isSuspend()) {
            return;   // 与基类一致：玩家关掉机器 ⇒ 一条批次都不推进（不摘批次、不吞料）
        }
        // ★ 2026-10-28【补齐已知缺口】旧逻辑的 serverTick 第一句是 nac.tickSync()，而本类整体覆写了
        //   serverTick ⇒ 必须在这儿补一次（与旧逻辑同频：每 tick 一次）。它负责：F 位连接刷新（每 20 tick）、
        //   界面同步文案（updateSyncedTexts）、未成形缺件审计（内部 15 秒限流）。
        //   机器侧入口是包内转发方法（tickSync 本体是 private，见 NanochipAssemblyComplexMachine）。
        if (machine instanceof NanochipAssemblyComplexMachine nac) {
            nac.nanochipTickSync();
        }
        if (!machine.isRecipeLogicAvailable()) {
            diagnoseUnavailableOnce();
            return;   // 零件表被读档清空这类状态 ⇒ 不推进、也不清批次表
        }
        diagnosedUnavailable = false;

        // 本 tick 的候选池 / 等待原因：一律从零开始（候选搜索一个 tick 最多一轮）
        candidatePool = null;
        candidateCursor = 0;
        // ★ 2026-11-02【fan-out】：本 tick 的「新开条数」与「自上次成功以来扫过几条」一并清零
        //   —— 前者是每 tick 的成本闸（FAN_OUT_MAX_PER_TICK），后者是 fan-out 轮转的终止判据。
        fanOutOpenedThisTick = 0;
        fanOutScannedSinceSuccess = 0;
        admissionWaiting = false;
        waitReason = null;
        // ★ 2026-11-01【候选为空 · 抓手】：本 tick 的候选读数一并清零（诊断是「每 tick 快照」口径）
        candidateRawCount = 0;
        candidateUsableCount = 0;
        candidateSearchFailure = null;
        firstReject = null;
        // ★ 2026-11-01【并行二分=0 · 抽样】：本 tick 的抽样格一并清零；
        //   只有「批次表为空」且候选诊断预算还没用完时才武装（⇒ 整局最多 CANDIDATE_DIAG_BUDGET 次，
        //   与候选诊断同一份预算 ⇒ 不新增任何刷屏面）。
        fitSampleRecipeId = null;
        fitSampleInputMax = 0L;
        fitSampleInputMaxOverBound = false;
        fitSampleMatchable = 0L;
        fitSampleProbeParallel = NO_PROBE;
        fitSampleProbeSide = null;
        fitSampleWholeEu = 0L;
        fitSampleStoredEu = 0L;
        fitSampleUnlimitedEnergy = false;
        fitSampleArmed = batches.isEmpty() && candidateDiagBudget > 0;
        // ★ 2026-11-01【输入侧 · 决定性诊断】本 tick 的匹配抽样格与接线读数一并清零
        fitSampleProbed = false;
        fitSampleInputContents = 0;
        fitSampleInputMatch = false;
        fitSampleOutputMatch = false;
        diagHasProxies = -1;
        diagWireParts = -1;
        diagWireCached = -1;
        inputWiringRefreshedThisTick = false;

        // ★★ 2026-11-01【输入侧接线 · 修复】★★ 这一步必须在「本轮搜索」之前：
        //   它把「样板仓内部槽 → 机器能力」那条接线重建一次（纯刷新、零配方推进，见常量注释），
        //   于是同一 tick 的候选搜索与并行试算读到的是<b>刚建好的</b>接线。
        //   触发条件极窄（只在「批次表为空 + 上一轮真的枚举到 0 条」时、且三重限频）⇒ 稳态零成本。
        refreshInputWiringIfStale();

        int batchesBeforeTick = batches.size();
        boolean working = false;
        boolean machineAllowsWork = true;
        if (!batches.isEmpty()) {
            // 机器级否决（基类在 handleRecipeWorking 里也是「一条配方在跑时每 tick 问一次」）：
            // 放在<b>推进之前</b>，于是「不许」时这一 tick 不扣电、不涨进度、也不摘批次（原地等）。
            try {
                machineAllowsWork = machine.onWorking();
            } catch (RuntimeException e) {
                machineAllowsWork = false;
                NanochipModContext.logger().debug("[gtlmodular] NAC 独立线程：机器级 onWorking 抛异常，本 tick 不推进：{}",
                        e.toString());
            }
        }
        // ★ 2026-11-02【每 tick 自耗 · 自测读数】：本段（①②③，含补料搜索 + 逐档二分）就是本类每 tick 的
        //   主要成本 ⇒ 用两个 nanoTime 夹住它（纯诊断，不参与任何判定），与 gtlcore 那行「延迟」同刻可比
        //   （上游量的是本机每 tick 自身耗时，本类只是其中最大的一块）。
        long workStartNanos = System.nanoTime();
        if (machineAllowsWork) {
            // ★ ①②③：逐条推进 → 摘掉涨满的（先收尾再出料）→ 有空位就补新批次（原子扣料）
            working = batches.tick(this);
            if (working) {
                totalContinuousRunningTime++;   // 与基类 handleRecipeWorking 同一句（gtlcore 会读这个计数器）
            }
        }
        recordSelfCost(workStartNanos, working, batches.size() < batchesBeforeTick);
        // ★ 记下「这一轮搜索真的枚举到了几条」—— 供下一次判断「是不是该重建接线」（纯读）
        lastSearchRaw = candidateRawCount;

        // ★ ④ 展示回写：把第 0 条批次的进度写进基类的同步字段
        //   （lastRecipe/progress/duration 都是 @Persisted @DescSynced ⇒ 免费拿到 Jade/TOP/面板的进度条）
        mirrorBatchZeroToBaseFields();

        boolean waiting = (!batches.isEmpty() && !working) || admissionWaiting;
        syncStatus(working, waiting);
        diagnoseBatches();
        diagnoseCandidates();   // ★ 2026-11-01：候选/首个拒绝点（限频，见 CANDIDATE_DIAG_INTERVAL）
        // ★ 2026-11-02【空闲限频】：唤醒标记只服务「本 tick 要不要搜」这一个判断 ⇒ 收尾清掉，
        //   于是它表达的是「自上一 tick 收尾以来，输入侧有没有变过」。
        wokenByInputChange = false;
    }

    /**
     * ★ 2026-11-02【空闲限频 · 唤醒口】输入侧一变就解除节流（<b>只加一个标记，不改上游任何行为</b>）。
     *
     * <p>谁调它（既有链路，本轮一行未改）：gtlcore 样板仓把本机配方逻辑登记进
     * {@code addBufferChangedListener}（{@code _javap_simple_handleparts.txt:711-739}），
     * 于是「AE 推料 / 写样板 / 换样板」这些<b>输入面变化</b>会走到这里；
     * 基类自己的 {@code onMachineLoad} / 结构成形 / 暂停开关也都会调它
     * （{@code _javap_simple_recipelogic.txt:135/:143/:789}）。
     *
     * <p>为什么要覆写：GTCEu 的「空闲自退订」在本类上不生效（本类不调 {@code super.serverTick()}，
     * 见 {@link #IDLE_SEARCH_THROTTLE} 的推导）⇒ 上游那句「被叫醒」在本类里一直<b>白白发生</b>。
     * 本覆写让它重新有意义：<b>被叫醒 ⇒ 立刻全量搜</b>，其余空闲 tick 才走节流。
     *
     * <p><b>绝不改上游语义</b>：先置标记，再原样 {@code super.updateTickSubscription()}（订阅行为逐字不变）。
     * <b>一行回退</b>：删掉本方法即可（标记恒为 false ⇒ 退化成「每 N tick 搜一次」的兜底）。
     */
    @Override
    public void updateTickSubscription() {
        wokenByInputChange = true;
        idleSearchCooldown = 0;
        // ★ 2026-11-02【补料重试 · 唤醒口】：输入面一变 ⇒ 补料重试的冷却一并清零（下一个 tick 立刻真试一次）
        fillRetryCooldown = 0;
        super.updateTickSubscription();
    }

    /**
     * ★ 2026-11-02【空闲限频】本 tick 要不要<b>跳过</b>候选搜索（见 {@link #IDLE_SEARCH_THROTTLE}）。
     *
     * <p>判据（只有上一个 tick 收尾是 <b>IDLE</b> 才节流）：
     * <ol>
     *   <li>有在跑批次 / 刚被唤醒 / 上一 tick 收尾状态不是 IDLE（= 在等电等料）
     *       ⇒ <b>不节流</b>并清零冷却（保证「有活/有等待」时每 tick 都试着开工）；</li>
     *   <li>否则冷还没到 ⇒ 跳过本 tick 的搜索（只累加诊断计数）；到点 ⇒ 放行并重置冷却。</li>
     * </ol>
     *
     * <p><b>纯只读 + 只动自己的两个计数</b>：不碰批次表、不碰判定、不碰 NBT。
     */
    private boolean idleSearchThrottled() {
        if (!IDLE_SEARCH_THROTTLE) {
            return false;
        }
        if (!batches.isEmpty() || wokenByInputChange || getStatus() != RecipeLogic.Status.IDLE) {
            idleSearchCooldown = 0;   // 有活 / 被唤醒 / 上一 tick 在等待 ⇒ 马上搜（绝不因为节流漏掉开工时机）
            return false;
        }
        if (idleSearchCooldown > 0) {
            idleSearchCooldown--;
            idleSkippedTicks++;
            return true;
        }
        idleSearchCooldown = Math.max(1, IDLE_SEARCH_INTERVAL) - 1;   // 本 tick 搜过了，接下来 K-1 tick 跳过
        return false;
    }

    /**
     * ★ 2026-11-02【补料重试】本 tick 要不要<b>跳过</b>补料搜索（见 {@link #FILL_RETRY_THROTTLE} 的完整推导）。
     *
     * <p>判据（只有上一 tick 是「都在正常推进 + 补料必然失败」才节流）：
     * <ol>
     *   <li>被唤醒 / 批次表空 / 上一 tick 的补料尝试其实开出来过 / 上一 tick 有批次被摘掉
     *       / 上一 tick 收尾状态不是 {@code WORKING} ⇒ <b>不节流</b>并清零冷却；</li>
     *   <li>否则冷还没到 ⇒ 跳过本 tick 的补料搜索（只累加诊断计数）；到点 ⇒ 放行并重置冷却。</li>
     * </ol>
     *
     * <p><b>纯只读 + 只动自己的两个计数</b>：不碰批次表、不碰判定、不碰 NBT。
     */
    private boolean fillRetryThrottled() {
        if (!FILL_RETRY_THROTTLE) {
            return false;
        }
        if (wokenByInputChange || batches.isEmpty() || !fillOpenedNothingLastTick || fillLostBatchLastTick
                || getStatus() != RecipeLogic.Status.WORKING) {
            fillRetryCooldown = 0;   // 有变化 / 有腾地方 / 不在稳态 ⇒ 马上真试一次
            return false;
        }
        if (fillRetryCooldown > 0) {
            fillRetryCooldown--;
            fillRetrySkippedTicks++;
            return true;
        }
        fillRetryCooldown = Math.max(1, FILL_RETRY_INTERVAL) - 1;   // 本 tick 真试过，接下来 K-1 tick 跳过
        return false;
    }

    /**
     * ★ 2026-11-02【每 tick 自耗 · 自测 + 节流状态记账】把本 tick 工作段（①②③）的耗时与本 tick 的补料结果
     * 记下来（<b>纯诊断</b>：只写本类自己的诊断字段，不参与任何判定、不改返回值的走向）。
     *
     * @param startNanos    工作段开始前的 {@code System.nanoTime()}
     * @param working       本 tick 有没有批次真的推进（{@code batches.tick} 的返回值）
     * @param lostBatch     本 tick 有没有批次被摘掉（跑完 / 被丢）⇒ 下一 tick 必须真试补料，绝不节流
     */
    private void recordSelfCost(long startNanos, boolean working, boolean lostBatch) {
        long micros = Math.max(0L, (System.nanoTime() - startNanos) / 1000L);
        selfCostMicros = micros;
        if (micros > selfCostPeakMicros) {
            selfCostPeakMicros = micros;
        }
        fillOpenedNothingLastTick = working && !batches.isEmpty() && fanOutOpenedThisTick == 0;
        fillLostBatchLastTick = lostBatch;
    }

    /** ★ 见 {@link #FILL_RETRY_THROTTLE}：诊断文本（节流是否生效 / 粒度 / 累计跳过 tick 数）。 */
    private String fillThrottleText() {
        if (!FILL_RETRY_THROTTLE) {
            return "关闭（每 tick 都完整试一遍补料）";
        }
        return (fillOpenedNothingLastTick ? "生效" : "待命（上一 tick 开出过 / 有批次跑完 / 不在稳态）")
                + "（每 " + Math.max(1, FILL_RETRY_INTERVAL) + " tick 至少真试一次；累计跳过 "
                + fillRetrySkippedTicks + " tick）";
    }

    /** ★ 见 {@link #recordSelfCost}：诊断文本（本类「每 tick 工作段」自耗，µs）。 */
    private String selfCostText() {
        return "本类工作段自耗=" + selfCostMicros + " µs（最近）/ " + selfCostPeakMicros + " µs（峰值）";
    }

    /**
     * ★ 2026-11-02【输入面读数 · 决定性诊断】把「单条批次的并行被谁钉住」读成三个数：
     * <b>参与槽数</b>、<b>首个有货槽的可得量</b>、<b>全槽汇总可得量</b>（纯只读、限频调用）。
     *
     * <h3>为什么是这三个数（并行上不去的两条完全不同的病根）</h3>
     * gtlcore 给「一条配方」读存量只有一个口：{@code MEPatternRecipeHandlePart.getMEContent(cap, recipe)}
     * = {@code getStackMapFromFirstAvailableSlot(该配方的绑定槽集)}（javap 现读
     * {@code _census/_javap_simple_handleparts.txt:1578-1588}）—— 即
     * <b>「一个槽」的量</b>；{@code IMERecipeHandler} 那一族里只有 {@code getSingleSlotStackMap(int)}
     * 与 {@code getStackMapFromFirstAvailableSlot(IntCollection)} 两个读数量口，
     * <b>没有「全槽汇总」口</b>（同文件 {@code :34-44}）。
     * ⇒ 分辨方式：
     * <ul>
     *   <li>{@code 汇总 ≈ 首槽} ⇒ 这一刻真的只有一个槽有货 ⇒ 并行小是<b>供料</b>（与线程机制无关）；</li>
     *   <li>{@code 汇总 ≫ 首槽} ⇒ 还有别的槽有货 ⇒ 该靠 <b>fan-out 多开批次</b>把各槽各自吃掉
     *       （身份仍按槽、数量按槽各吃 —— 见报告「身份 vs 数量解耦」）。</li>
     * </ul>
     *
     * <h3>有界（绝不刷屏、绝不拖慢）</h3>
     * 只被 {@link #diagnoseBatches()} 调用（在跑条数<b>变化</b>时才打、≥{@value #DIAG_INTERVAL} tick 一次），
     * 且最多扫 {@value #INPUT_FACE_SCAN_LIMIT} 个槽；任何异常都退回「没读到」那一档，绝不影响判定。
     */
    private String inputFaceText() {
        StringBuilder sb = new StringBuilder(96);
        try {
            if (!(machine instanceof IRecipeCapabilityMachine capabilityMachine)) {
                return "（不是 gtlcore 能力机器）";
            }
            List<MEPatternRecipeHandlePart> parts = capabilityMachine.getMEPatternRecipeHandleParts();
            if (parts == null || parts.isEmpty()) {
                return "（没有样板仓接线）";
            }
            int scannedSlots = 0;
            int stockSlots = 0;
            long firstSlotAmount = 0L;
            long allSlotsAmount = 0L;
            for (int p = 0; p < parts.size(); p++) {
                IMERecipeHandler<?, ?>[] handlers = parts.get(p).getMERecipeHandlers();
                if (handlers == null) {
                    continue;
                }
                for (IMERecipeHandler<?, ?> handler : handlers) {
                    int[] active = handler.getActiveSlots();
                    if (active == null) {
                        continue;
                    }
                    for (int s = 0; s < active.length && scannedSlots < INPUT_FACE_SCAN_LIMIT; s++, scannedSlots++) {
                        long amount = stackMapAmount(handler.getSingleSlotStackMap(active[s]));
                        if (amount <= 0L) {
                            continue;
                        }
                        stockSlots++;
                        allSlotsAmount = allSlotsAmount > Long.MAX_VALUE - amount
                                ? Long.MAX_VALUE : allSlotsAmount + amount;
                        if (firstSlotAmount <= 0L) {
                            firstSlotAmount = amount;   // 与 gtlcore「首个可用槽」同口径（第一个有货的槽）
                        }
                    }
                }
            }
            sb.append("参与槽=").append(scannedSlots)
                    .append(scannedSlots >= INPUT_FACE_SCAN_LIMIT ? "+（扫到上限，未扫完）" : "")
                    .append("；有货槽=").append(stockSlots)
                    .append("；首可用槽可得量=").append(firstSlotAmount)
                    .append("；全槽汇总可得量=").append(allSlotsAmount);
        } catch (Throwable e) {
            // 读不到就说读不到（绝不当成 0，也绝不把异常/链接错误抛进 tick）
            sb.append("（读取失败：").append(e.getClass().getSimpleName()).append("）");
        }
        return sb.toString();
    }

    /** 一个「内容 → 数量」表的数量之和（{@code null} / 空 ⇒ 0；饱和加，绝不溢出）。 */
    private static long stackMapAmount(it.unimi.dsi.fastutil.objects.Object2LongMap<?> map) {
        if (map == null || map.isEmpty()) {
            return 0L;
        }
        long total = 0L;
        for (long amount : map.values()) {
            if (amount > 0L) {
                total = total > Long.MAX_VALUE - amount ? Long.MAX_VALUE : total + amount;
            }
        }
        return total;
    }

    // ══════════════════ ★ 0.3.132【按槽开批】「按槽最大」读数 ══════════════════
    // 与上游 `IParallelLogic.getInputItemParallel` / `getInputFluidParallel`（`:104-196`）**同口径**：
    //   ① 需求侧：`ItemRecipeCapability.CAP.of(content.content)` → `LongIngredient.getActualAmount()`
    //     / `SizedIngredient.getAmount()` / 兜底 1；流体 `FluidRecipeCapability.CAP.of(...).getAmount()`；
    //     两者都只取 `content.chance > 0` 的项（与上游 `:115` / `:163` 逐字一致）。
    //   ② 库存侧：**按 `getCapability()` 分派**后逐槽读 `IMERecipeHandler#getSingleSlotStackMap`
    //     （物品 Handler 的表里只有 `ItemStack`、流体 Handler 里只有 `FluidStack` ⇒ 物品需求绝不拿去问流体
    //      Handler，否则每个槽恒读 0），用**谓词**匹配后取**匹配上的最大一份**
    //     —— 上游 `IParallelLogic.calculate` 是「取第一条匹配的并移除」，这里取最大一份（只会偏大 ⇒ 安全方向）；
    //     上游另用 AllButCount/AllButAmount 哈希策略把同一物料并成一条。
    //   ③ 同一个槽下标：物品侧份数与流体侧份数**各算一次取 min**（消费侧 `MEPatternRecipeHandlePart.handleRecipe`
    //     `:106-107` 正是用同一个 `slot` 先后问物品/流体两个 Handler ⇒ 两侧都在这个槽里凑得齐，这一批才成事）；
    //     每侧份数 = 逐项 `库存 / 每份需求` 取**最小**；全槽取**最大**。
    // ⚠ 与上游的唯一差别（有意，见 PER_SLOT_PARALLEL_BOUND 的注释）：上游只算**第一个**有货槽，这里算**所有**有货槽取最大。

    /**
     * ★ 0.3.132【按槽开批】<b>「按槽最大」输入侧份数</b>：逐「有货槽」算它<b>单独</b>能吃下几份，取最大值。
     *
     * <p>为什么可以取「最大」而不是「求和」：消费侧
     * {@code MEPatternRecipeHandlePart.handleRecipe}（{@code :102-121}）的口径是"<b>逐个槽试、任一槽独立吃下整条需求</b>"
     * ⇒ 一条批次只能吃一个槽 ⇒"一条批次最多放几份"的正确答案就是各槽能力的最大值。
     *
     * <p>同一个槽下标的两侧（物品 / 流体）必须<b>取 min</b>：同文件 {@code :106-107} 是
     * {@code if (hasItem && !itemHandler.meHandleRecipe(recipe, simulate, slot)) continue;} 紧跟
     * {@code if (hasFluid && !fluidHandler.meHandleRecipe(recipe, simulate, slot)) continue;}
     * —— 两路用的是<b>同一个</b> {@code slot} ⇒ 两侧都在这个槽里凑得齐，这个槽才真的能开一批。
     *
     * <p>两路需求<b>分开送</b>：物品需求只问物品 Handler、流体需求只问流体 Handler（按 {@code getCapability()} 分派）
     * —— 两侧的槽库存表各装各的类型，混着问会恒读 0。
     *
     * @return 份数；{@code NO_SLOT_BOUND} = 本次不参与（没有可数的输入、读不到、或异常）
     */
    private long perSlotInputBound(GTRecipe recipe) {
        try {
            if (recipe == null || !(machine instanceof IRecipeCapabilityMachine capabilityMachine)) {
                return NO_SLOT_BOUND;
            }
            List<InputNeed<ItemStack>> itemNeeds = itemNeedsOf(recipe);
            List<InputNeed<FluidStack>> fluidNeeds = fluidNeedsOf(recipe);
            if (itemNeeds == null || fluidNeeds == null) {
                // 谓词读不到 / 出现 ≤ 0 的份数 ⇒ 交给上游那套自带 limit 保护的口径，本读数不掺和
                return NO_SLOT_BOUND;
            }
            if (itemNeeds.isEmpty() && fluidNeeds.isEmpty()) {
                return NO_SLOT_BOUND;   // 没有可数的物品/流体输入 ⇒ 不参与（退回上游读数）
            }
            List<MEPatternRecipeHandlePart> parts = capabilityMachine.getMEPatternRecipeHandleParts();
            if (parts == null || parts.isEmpty()) {
                return NO_SLOT_BOUND;
            }
            long best = 0L;
            int scanned = 0;
            for (MEPatternRecipeHandlePart part : parts) {
                IMERecipeHandler<?, ?>[] handlers = part.getMERecipeHandlers();
                if (handlers == null) {
                    continue;
                }
                int[] active = activeSlotsOf(handlers);
                if (active == null) {
                    continue;
                }
                for (int slot : active) {
                    if (scanned >= PER_SLOT_SCAN_LIMIT) {
                        // ★ 到闸：只按已扫到的算（偏小即保守，绝不因为没扫完而虚高）
                        return best;
                    }
                    scanned++;
                    long capacity = slotCraftCapacityAcrossSides(itemNeeds, fluidNeeds, handlers, slot);
                    if (capacity > best) {
                        best = capacity;
                    }
                }
            }
            // 有货槽一个都撑不起 1 份 ⇒ 如实返回 0（让上游那条短路照旧生效，并留下"按槽读数=0"的诊断）
            return best;
        } catch (Throwable e) {
            // 读不到就说读不到（绝不抛进 tick，也绝不当成一个大数）
            return NO_SLOT_BOUND;
        }
    }

    /**
     * ★ 0.3.132【按槽开批】<b>一个内部槽下标单独能吃下几份</b>：物品侧 / 流体侧各算一次，取 <b>min</b>。
     *
     * <p>依据 {@code MEPatternRecipeHandlePart.handleRecipe}（{@code :102-121}）：它用<b>同一个</b>
     * {@code slot} 依次问物品 Handler 与流体 Handler（{@code :106-107}）⇒「这一个槽能开几批」
     * = 两侧份数的较小者 —— 两侧都在这一个槽里凑得齐，这一批才真的成事。
     *
     * <p>按 {@code handler.getCapability()} 分派：物品 Handler 的槽库存表里只有 {@code ItemStack}、
     * 流体 Handler 里只有 {@code FluidStack} ⇒ 每一侧只拿<b>自己那一路</b>的 needs 去算，
     * 绝不用物品需求去问流体 Handler（那会让每个槽恒读 0 ⇒ 整个读数恒 0）。
     *
     * @return 该槽下标的份数（{@code 0} = 这个槽一份都撑不起 / 两侧一路都没读到）
     */
    private long slotCraftCapacityAcrossSides(List<InputNeed<ItemStack>> itemNeeds,
                                              List<InputNeed<FluidStack>> fluidNeeds,
                                              IMERecipeHandler<?, ?>[] handlers, int slot) {
        long best = Long.MAX_VALUE;
        boolean measured = false;
        for (IMERecipeHandler<?, ?> handler : handlers) {
            if (handler == null) {
                continue;
            }
            RecipeCapability<?> cap = handler.getCapability();
            if (cap == ItemRecipeCapability.CAP) {
                if (itemNeeds.isEmpty()) {
                    continue;   // 这一路没有需求 ⇒ 不受限（不参与 min）
                }
                best = Math.min(best, slotCraftCapacity(itemNeeds, handler.getSingleSlotStackMap(slot)));
                measured = true;
            } else if (cap == FluidRecipeCapability.CAP) {
                if (fluidNeeds.isEmpty()) {
                    continue;
                }
                best = Math.min(best, slotCraftCapacity(fluidNeeds, handler.getSingleSlotStackMap(slot)));
                measured = true;
            }
            // 别的 cap 不参与（与上游同口径：按槽开批只认物品 / 流体这两路）
        }
        return measured && best != Long.MAX_VALUE ? Math.max(0L, best) : 0L;
    }

    /**
     * ★ 0.3.132【按槽开批】<b>某一侧（物品或流体）在这一个槽里能吃下几份</b>：
     * 逐项取「该槽里<b>匹配上的最大一份</b>库存 ÷ 每份需求」，再对所有项取<b>最小</b>。
     *
     * <p>口径与上游 {@code IParallelLogic.calculate} 的「available / needed」一致，只有一处放宽：
     * 同一项内容在槽库存表里有多条匹配时取<b>最大的一份</b>（上游取第一条匹配的并移除）⇒ 只会偏大 ⇒ 安全方向。
     *
     * <p>⚠ 下面 {@code (T) entry.getKey()} 是<b>类型上无法避免</b>的一次未检查转换：
     * {@code getSingleSlotStackMap(slot)} 在 {@code IMERecipeHandler<?, ?>} 上取到的是通配符
     * {@code Object2LongMap<?>}，Java 泛型无法从通配符里取回类型参数 {@code S}；而调用方**已经**用
     * {@code getCapability()} 判定过这是物品侧（{@code T = ItemStack}）还是流体侧（{@code T = FluidStack}）
     * ⇒ 这次转换语义上恒真（先例：上游同一接口 {@code IMERecipeHandler.java:75-78} 的
     * {@code copyContent} 也是同一处「未检查转换」的抑制标注）。
     *
     * @param needs 这一侧的输入需求表（每个元素 = 一个谓词 + 每份需求）
     * @param stock 这个槽里<b>这一侧</b>的库存表（键类型与本侧 {@code T} 同源）
     * @return 这一侧能供的份数；{@code 0} = 这个槽连一份都凑不齐
     */
    @SuppressWarnings("unchecked")
    private static <T> long slotCraftCapacity(List<InputNeed<T>> needs, Object2LongMap<?> stock) {
        if (needs == null || needs.isEmpty() || stock == null || stock.isEmpty()) {
            return 0L;
        }
        long capacity = Long.MAX_VALUE;
        for (InputNeed<T> need : needs) {
            long available = 0L;
            for (Object2LongMap.Entry<?> entry : Object2LongMaps.fastIterable(stock)) {
                long amount = entry.getLongValue();
                if (amount > available && need.test.test((T) entry.getKey())) {
                    available = amount;   // ★ 取「匹配上的最大一份」，不是求和（与上游 calculate 同向）
                }
            }
            if (available < need.perCraft) {
                return 0L;   // 这个槽凑不齐这一项 ⇒ 这一侧供 0 份
            }
            capacity = Math.min(capacity, available / need.perCraft);
        }
        return capacity == Long.MAX_VALUE ? 0L : capacity;
    }

    /**
     * ★ 0.3.132【按槽开批】取该样板仓的「有货槽下标集」。
     *
     * <p>依据 {@code IMERecipeHandler#getActiveSlots()} 的 javadoc（{@code :34-35}）逐字：
     * 「因此同一MEPatternBuffer的任意Handler都应当返回相同结果」⇒ 逐 Handler 各扫一遍只会重复计数，
     * 这里只从第一个读到的 Handler 取一次，并据此认定<b>槽下标对两个 Handler 同义</b>。
     */
    private static int[] activeSlotsOf(IMERecipeHandler<?, ?>[] handlers) {
        for (IMERecipeHandler<?, ?> handler : handlers) {
            if (handler == null) {
                continue;
            }
            int[] active = handler.getActiveSlots();
            if (active != null) {
                return active;
            }
        }
        return null;
    }

    /**
     * ★ 0.3.132【按槽开批】这条配方的<b>物品</b>输入需求表（与上游 {@code IParallelLogic.getInputItemParallel}
     * 同口径：只算 {@code chance > 0} 的内容，份数优先读 {@code LongIngredient.getActualAmount()}，
     * 再 {@code SizedIngredient.getAmount()}，否则 1）。
     *
     * @return 需求表；{@code null} = <b>这次不参与</b>（谓词读不到 / 出现 {@code ≤ 0} 的份数 ⇒
     *         交给上游那套自带 {@code limitItemAmount} 保护的口径，本读数不掺和）
     */
    private static List<InputNeed<ItemStack>> itemNeedsOf(GTRecipe recipe) {
        List<InputNeed<ItemStack>> needs = new ArrayList<>(2);
        List<Content> contents = recipe.getInputContents(ItemRecipeCapability.CAP);
        if (contents == null || contents.isEmpty()) {
            return needs;
        }
        for (Content content : contents) {
            if (content == null || content.chance <= 0) {
                continue;
            }
            Ingredient ingredient = ItemRecipeCapability.CAP.of(content.content);
            if (ingredient == null) {
                return null;
            }
            long perCraft;
            if (ingredient instanceof org.gtlcore.gtlcore.api.recipe.ingredient.LongIngredient longIngredient) {
                perCraft = longIngredient.getActualAmount();
            } else if (ingredient instanceof SizedIngredient sized) {
                perCraft = sized.getAmount();
            } else {
                perCraft = 1L;
            }
            if (perCraft <= 0L) {
                return null;
            }
            // ★ `Ingredient implements Predicate<ItemStack>`（证据：`NotifiableMERecipeHandlerTrait<T extends Predicate<S>, S>`
            //   被 `MEPatternBufferRecipeHandlerTraitBase.java:61` 以 `<Ingredient, ItemStack>` 实例化）
            //   ⇒ `Ingredient` 本身就是本侧谓词，直接收进 `InputNeed<ItemStack>`
            //   （无方法引用降型、无强转、无需 `instanceof` 卫语句）。
            needs.add(new InputNeed<>(ingredient, perCraft));
        }
        return needs;
    }

    /**
     * 库存键必须使用 {@code FluidIngredient} 的泛型实参（LDLib 的 {@code FluidStack}），
     * 才能与流体输入谓词匹配；只计入 {@code chance > 0} 的需求。
     *
     * @return 需求表；{@code null} = 这次不参与（理由同 {@link #itemNeedsOf}）
     */
    private static List<InputNeed<FluidStack>> fluidNeedsOf(GTRecipe recipe) {
        List<InputNeed<FluidStack>> needs = new ArrayList<>(2);
        List<Content> contents = recipe.getInputContents(FluidRecipeCapability.CAP);
        if (contents == null || contents.isEmpty()) {
            return needs;
        }
        for (Content content : contents) {
            if (content == null || content.chance <= 0) {
                continue;
            }
            FluidIngredient fluid = FluidRecipeCapability.CAP.of(content.content);
            if (fluid == null) {
                return null;
            }
            long perCraft = fluid.getAmount();
            if (perCraft <= 0L) {
                return null;
            }
            // ★ 同上：`FluidIngredient implements Predicate<FluidStack>`，而它的 `FluidStack` 是
            //   **LDLib 的那个**（`com.lowdragmc.lowdraglib.side.fluid.FluidStack`）——
            //   编译实测为此（先写成 Forge 的会报 inference 冲突）⇒ `FluidIngredient` 本身就是本侧谓词。
            needs.add(new InputNeed<>(fluid, perCraft));
        }
        return needs;
    }

    /**
     * ★ 0.3.132【按槽开批】一条「输入需求」：{@code test} 判某个槽库存条目是不是这份内容，
     * {@code perCraft} = 一份配方要吃多少。{@code T} = <b>本侧</b>库存键的类型
     * （物品侧 {@code ItemStack}、流体侧 {@link FluidStack}）—— 由 {@link #slotCraftCapacity} 里
     * 那一处未检查转换把键收成 {@code T}（那里已说明：调用方先用 {@code getCapability()} 判过侧，转换恒真）。
     * （只服务按槽算份数，不参与任何其它判定。）
     */
    private static final class InputNeed<T> {

        private final Predicate<T> test;
        private final long perCraft;

        private InputNeed(Predicate<T> test, long perCraft) {
            this.test = test;
            this.perCraft = perCraft;
        }
    }

    /**
     * ★★ 2026-11-01【输入侧接线 · 修复】<b>把「样板仓内部槽 → 机器能力」那条接线重建一次</b>（纯刷新）。
     *
     * <h3>它补的是哪一步（本轮定位到的确切差异）</h3>
     * 见 {@link #INPUT_WIRING_REFRESH_INTERVAL} 的长注释：这条接线由 gtlcore
     * {@code IRecipeCapabilityMachine.upDate()} 建立，而它在<b>运行期</b>只有「代理重绑」与
     * 「GUI distinct 开关」两个触发点 ⇒ 结构成形之后<b>再没有任何入口</b>把它重建起来。
     * 旧路径的候选循环会顺带真的扣一次料（{@code _javap_simple_multirecipes.txt:212-218}），
     * 于是 {@code (配方 → 槽)} 被写热、接线自然就「活」了；本类的搜索阶段严格只读
     * ⇒ 接线一旦是冷的，就<b>永远</b>冷下去（自锁）。
     *
     * <h3>为什么它绝不涉及配方推进 / 绝不双扣料</h3>
     * {@code upDate()} 的全体是 {@code clear()}（只清 gtlcore 自己的标志位与 <b>四张</b>表 ——
     * {@code normalCapabilities} / {@code mePatternRecipeHandleParts} / {@code meOutputRecipeHandleParts}
     * / {@code recipeHandleMap}，{@code _javap_simple_handleparts.txt:1022-1075}，
     * <b>不含任何物料/配方写入</b>，也<b>不含</b>部件机上那张"已缓存配方"表
     * {@code recipeMultipleCacheMap}）→ 遍历部件重建 {@code RecipeHandlePart} →
     * {@code restoreMachineCache}（**只读**既有槽缓存，同文件 {@code :1508-1576}）→ {@code sortMEOutput}。
     * <b>全程不出现</b> {@code findAndHandleRecipe} / {@code getRecipe} / {@code setupRecipe} /
     * {@code onRecipeFinish} / {@code handleRecipeInput} ⇒ 与「谁扣料」这件事零交集。
     * 本类依旧<b>从不</b>调 {@code super.serverTick()}、{@code findAndHandleRecipe}、
     * {@code setupRecipe}、{@code onRecipeFinish}。
     *
     * <h3>★ 0.3.131 事实更正：{@code upDate()} 既加不了热、也清不了零</h3>
     * 因为 {@code restoreMachineCache} 的输入就是**同一个** {@code getSlot2RecipesCache()}
     * （部件机自己的 {@code recipeMultipleCacheMap} / 自研 trait 的 {@code slot2RecipesCache}），
     * 所以重建只会把「配方 → 槽」原样搬一遍：
     * <ul>
     *   <li>缓存本来是 0（冷槽）⇒ 重建后仍是 0（<b>它加热不了</b>）；</li>
     *   <li>缓存本来非 0 ⇒ 重建后仍在（<b>它清不了零</b>）。</li>
     * </ul>
     * ⇒ 0.3.130 那句"重建把配方缓存清零 ⇒ 越修越坏"<b>不成立</b>（更正依据见
     * {@code _census/跑一次就停_修复记录.md} §②）。它真正的代价是：每次重建都重复登记
     * buffer-changed 监听器（{@code clear()} 不清 {@code traitSubscriptions}）⇒ 见下面的判据 1。
     *
     * <h3>四重限频（稳态零成本、绝不刷屏）</h3>
     * <ol>
     *   <li><b>只在真的卡住时</b>：{@code batches.isEmpty()}（没有在跑批次 ⇒ 重建不会打扰任何预留）
     *       且 {@code lastSearchRaw == 0}（上一轮搜索真的枚举到 0 条）；</li>
     *   <li><b>时间闸</b>：两次之间至少隔 {@link #INPUT_WIRING_REFRESH_INTERVAL} tick；</li>
     *   <li><b>总量闸</b>：整机一生至多 {@link #INPUT_WIRING_REFRESH_BUDGET} 次
     *       （理由：{@code clear()} 不清 {@code traitSubscriptions} ⇒ 无节制重建会累积监听器）。</li>
     *   <li>★ 0.3.131 <b>必要性闸</b>（见 {@link #INPUT_WIRING_REBUILD_GATED}）：只有「接线表真的是空的」
     *       才重建；接线在（实机就是 {@code 样板仓接线=2}）⇒ 一律跳过。
     *       并且"抛异常"与"重建前后一字未变"都<b>立即永久停手</b>。</li>
     * </ol>
     * 任何异常都只记一行 debug（诊断/刷新绝不影响派活）。
     *
     * <h3>回退（一行、逐字）</h3>
     * ① <b>本轮的首选</b>：把 {@link #INPUT_WIRING_REBUILD_GATED} 改成 {@code false}
     * （⇒ 逐字退回 0.3.130 的"空池就重建"口径）；
     * ② 更彻底：把 {@code NanochipAssemblyComplexMachine.createRecipeLogic} 之外的本方法调用点
     * （{@code serverTick} 里那一句 {@code refreshInputWiringIfStale();}）删掉即可
     * —— 本方法及其常量/字段全是加法，删掉调用点后一行都不会被执行。
     */
    private void refreshInputWiringIfStale() {
        if (inputWiringCooldown > 0) {
            inputWiringCooldown--;
        }
        if (!batches.isEmpty() || lastSearchRaw != 0) {
            return;   // 有批次在跑 / 上一轮明明枚举到了东西 ⇒ 不需要这笔开销
        }
        if (inputWiringBudget <= 0 || inputWiringCooldown > 0) {
            return;   // 总量闸 / 时间闸
        }
        if (!(machine instanceof IRecipeCapabilityMachine capabilityMachine)) {
            return;   // 不是 gtlcore 那套机器（理论上不会）⇒ 不参与，宁可不动
        }
        // ★★ 0.3.131【② 判据 1：接线还在 ⇒ 一律不重建】（一行回退见 INPUT_WIRING_REBUILD_GATED）★★
        //   依据（本机字节码逐条，见该常量的 javadoc）：upDate() 只清 gtlcore 自己那 4 张表，
        //   再**只读**地从部件机既有的槽缓存把「配方 → 槽」搬回来 ⇒ 对"已缓存配方"这张表
        //   既加不了热、也清不了零。⇒ 接线 >0 时重建是**纯成本**（每次 +2 个 buffer-changed 监听器、
        //   并把 removeSlotFromMap 覆盖成新部件的 lambda）。
        if (INPUT_WIRING_REBUILD_GATED) {
            int partsNow = mePatternPartCount(capabilityMachine);
            if (partsNow > 0) {
                // 接线在（实机那一行就是"样板仓接线=2"）⇒ 重建被证伪，不花这笔钱。
                // 仍然占住节流位 ⇒ 这个探针最多每 INPUT_WIRING_REFRESH_INTERVAL tick 跑一次（稳态零成本）。
                inputWiringCooldown = INPUT_WIRING_REFRESH_INTERVAL;
                return;
            }
        }
        inputWiringCooldown = INPUT_WIRING_REFRESH_INTERVAL;
        inputWiringBudget--;
        inputWiringRefreshes++;
        inputWiringLastRefreshTick = currentGameTime();
        inputWiringPartsBefore = mePatternPartCount(capabilityMachine);
        inputWiringCachedBefore = mePatternCachedRecipeCount(capabilityMachine);
        // 本次还没采到"后" ⇒ 先清成"没读到"，避免抛异常时诊断行显示**上一次**的旧对比（伪造证据）。
        inputWiringPartsAfter = -1;
        inputWiringCachedAfter = -1;
        try {
            // ★ 纯刷新：重建 RecipeHandlePart 表 + 从各槽既有缓存恢复「配方 → 槽」快路径。
            //   它不碰物料、不碰批次表、不碰 lastRecipe/progress，也不可能扣料。
            capabilityMachine.upDate();
            inputWiringRefreshedThisTick = true;
        } catch (RuntimeException e) {
            // 刷新失败 ⇒ 保持原样（fail-open：不做任何补偿动作，也绝不影响派活）
            // ★ 0.3.131【② 判据 2：失败即停】它抛过一次就说明这条路在本环境不成立 ⇒ 永久停手，
            //   不再每 100 tick 重试（"一生最多 N 次 + 失败即停"里的后半句）。
            inputWiringBudget = 0;
            inputWiringStopReason = "重建抛异常 ⇒ 已永久停手（" + e.getClass().getSimpleName() + "）";
            NanochipModContext.logger().debug("[gtlmodular] NAC 独立线程：输入侧接线重建失败（保持原样，不影响派活）：{}",
                    e.toString());
            return;
        }
        // ★ 0.3.131【② 对策 · 前后对比】无论开关如何，这一对读数都要采（否则诊断行会显示半个数：
        //   回退模式下"→"右边恒为 -1，读起来像"重建把接线清空了"，那是伪造证据）。
        inputWiringPartsAfter = mePatternPartCount(capabilityMachine);
        inputWiringCachedAfter = mePatternCachedRecipeCount(capabilityMachine);
        if (!INPUT_WIRING_REBUILD_GATED) {
            return;   // 逐字退回 0.3.130 口径：只采证、不据此停手（保持旧行为一字不差）
        }
        // ★ 0.3.131【② 判据 3：被证伪即停】重建前后「接线数 + 已缓存配方数」一字未变 ⇒ 这次重建
        //   没有产生任何可观测变化 ⇒ 记一次证伪；连续 INPUT_WIRING_FUTILE_MAX 次 ⇒ 永久停手。
        //   这一格也正是题目要的"本次重建是否清空了缓存（前后对比）"：两个数**都**打进既有诊断行。
        if (inputWiringPartsAfter == inputWiringPartsBefore
                && inputWiringCachedAfter == inputWiringCachedBefore) {
            inputWiringFutile++;
            if (inputWiringFutile >= INPUT_WIRING_FUTILE_MAX) {
                inputWiringBudget = 0;
                inputWiringStopReason = "重建前后一字未变 ⇒ 已永久停手（接线数 "
                        + inputWiringPartsAfter + "、已缓存配方 " + inputWiringCachedAfter + "）";
            }
        } else {
            inputWiringFutile = 0;
        }
    }

    /**
     * ★ 0.3.131【② 对策】{@code getMEPatternRecipeHandleParts().size()}（= 「样板仓接线」那个数）。
     * <p>纯只读、绝不当成 0：读不到/抛异常 ⇒ {@code -1}（"没读到"）。
     */
    private int mePatternPartCount(IRecipeCapabilityMachine capabilityMachine) {
        try {
            List<MEPatternRecipeHandlePart> parts = capabilityMachine.getMEPatternRecipeHandleParts();
            return parts == null ? -1 : parts.size();
        } catch (RuntimeException e) {
            return -1;
        }
    }

    /**
     * ★ 0.3.131【② 对策】全部样板仓接线 {@code getCachedGTRecipe()} 的条数之和（= 「已缓存配方」那个数）。
     * <p>纯只读、绝不当成 0：读不到/抛异常 ⇒ {@code -1}（"没读到"）。
     */
    private int mePatternCachedRecipeCount(IRecipeCapabilityMachine capabilityMachine) {
        try {
            List<MEPatternRecipeHandlePart> parts = capabilityMachine.getMEPatternRecipeHandleParts();
            if (parts == null) {
                return -1;
            }
            int cached = 0;
            for (MEPatternRecipeHandlePart part : parts) {
                ObjectSet<GTRecipe> set = part.getCachedGTRecipe();
                if (set != null) {
                    cached += set.size();
                }
            }
            return cached;
        } catch (RuntimeException e) {
            return -1;
        }
    }

    /** 当前 gameTime（拿不到 ⇒ {@code -1}）；只为诊断"上次重建是什么时候"，绝不参与判定。 */
    private long currentGameTime() {
        try {
            // 用 var 拿 Level（本文件没 import Level；同款写法见本文件 serverTick 里的 machine.self().getLevel()）
            var level = machine.self().getLevel();
            return level == null ? -1L : level.getGameTime();
        } catch (RuntimeException e) {
            return -1L;
        }
    }

    /**
     * ★ 2026-11-01【输入侧 · 决定性诊断】<b>把输入侧读成三个最小可分辨的数</b>（纯只读）。
     *
     * <p>为什么是这三格：「输入侧是空的」这句话在日志里原本没有任何读数。三格合一之后，
     * 「能力代理不存在」「接线根本没建起来」「接线建了但一条配方都没缓存」三种情况一眼分开，
     * 而它们对应的检修方向完全不同（见报告 §②-⑶）：
     * <ol>
     *   <li>{@code 能力代理=否} ⇒ GTCEu 侧的能力代理没建起来（{@code hasProxies()} =
     *       {@code _javap_simple_multirecipes.txt:176}）⇒ 连 {@code getRecipe()} 的第一句都过不去；</li>
     *   <li>{@code 样板仓接线=0} ⇒ gtlcore 那套 {@code IRecipeCapabilityMachine} 结构是空的
     *       ⇒ 候选只能靠原料树 dive（{@code getCachedGTRecipe()} 追加的那一路没有东西可追加）；</li>
     *   <li>{@code 已缓存配方=0} ⇒ 接线在，但每个样板槽都还没有「这台机器能做这条配方」的记账
     *       ⇒ 冷槽 + 只读搜索 = 自锁（见 {@link #refreshInputWiringIfStale()}）。</li>
     * </ol>
     * 取数全部走已取证的真实 API（{@code getMEPatternRecipeHandleParts()} =
     * {@code _javap_simple_handleparts.txt:1130}；{@code getCachedGTRecipe()} = 同文件 {@code :1491}），
     * 一律 try/catch 兜到 debug；读不到就保持 {@code -1}（= 「没读到」，绝不当成 0）。
     */
    private String inputSideText() {
        StringBuilder sb = new StringBuilder(80);
        try {
            diagHasProxies = machine.hasProxies() ? 1 : 0;
        } catch (RuntimeException e) {
            diagHasProxies = -1;
        }
        sb.append("能力代理=").append(diagHasProxies < 0 ? "没读到" : (diagHasProxies == 1 ? "有" : "无"));
        try {
            if (machine instanceof IRecipeCapabilityMachine capabilityMachine) {
                List<MEPatternRecipeHandlePart> parts = capabilityMachine.getMEPatternRecipeHandleParts();
                diagWireParts = parts == null ? -1 : parts.size();
                int cached = 0;
                if (parts != null) {
                    for (MEPatternRecipeHandlePart part : parts) {
                        ObjectSet<GTRecipe> set = part.getCachedGTRecipe();
                        if (set != null) {
                            cached += set.size();
                        }
                    }
                }
                diagWireCached = cached;
            }
        } catch (RuntimeException e) {
            diagWireParts = -1;
            diagWireCached = -1;
        }
        sb.append("；样板仓接线=").append(diagWireParts < 0 ? "没读到" : String.valueOf(diagWireParts));
        sb.append("；已缓存配方=").append(diagWireCached < 0 ? "没读到" : String.valueOf(diagWireCached));
        sb.append("；接线重建=").append(inputWiringRefreshes).append("次")
                .append(inputWiringRefreshedThisTick ? "（本 tick 刚建过）" : "")
                .append("（余 ").append(Math.max(0, inputWiringBudget)).append(" 次）");
        // ★★ 0.3.131【② 对策 · 题目要的读数全部落在**这一行**里（不新增日志行）】★★
        //   ① 缓存数 / ② 接线数 = 本行前面的「已缓存配方=…」/「样板仓接线=…」（原样，口径未动）；
        //   ③ 重建次数 = 上面那一格；④ 上次重建时刻 = 下面这格 gameTime；
        //   ⑤ **本次重建前后对比** = 直接回答"重建到底把接线/缓存动没动"（若有"清空"，这里就是 0 的来处）。
        if (inputWiringLastRefreshTick >= 0L) {
            sb.append("；上次重建@").append(inputWiringLastRefreshTick);
            sb.append("；重建前后[接线 ").append(inputWiringPartsBefore)
                    .append("→").append(inputWiringPartsAfter)
                    .append("；已缓存配方 ").append(inputWiringCachedBefore)
                    .append("→").append(inputWiringCachedAfter).append(']');
        }
        if (inputWiringStopReason != null) {
            sb.append("；接线重建已停手（").append(inputWiringStopReason).append("）");
        } else if (inputWiringFutile > 0) {
            sb.append("；重建已证伪 ").append(inputWiringFutile).append(" 次（连续 ")
                    .append(INPUT_WIRING_FUTILE_MAX).append(" 次即停手）");
        }
        return sb.toString();
    }

    /**
     * 结构失效 / 配方逻辑重置 ⇒ 批次表与附注一起清（否则会留下跑不了的幽灵批次）。
     *
     * <p>⚠️ 已经扣掉的料不退回 —— 与基类 {@code resetRecipeLogic()} 的既有行为一致。
     */
    @Override
    public void resetRecipeLogic() {
        int lost = batches.size();   // ★ 2026-11-01：诊断「开出来了又被谁清掉了」（见字段注释）
        super.resetRecipeLogic();
        batches.clear();
        batchStates.clear();
        candidatePool = null;
        candidateCursor = 0;
        admissionWaiting = false;
        waitReason = null;
        waitReasonCooldown = 0;
        diagLastRunning = -1;
        candidateRawCount = 0;
        candidateUsableCount = 0;
        candidateSearchFailure = null;
        firstReject = null;
        candidateDiagCooldown = 0;
        candidateDiagLast = null;
        // ★ 2026-11-02【fan-out / 空闲限频】：重置的是「本 tick 级的游标与节流位」，
        //   累计诊断（idleSkippedTicks / lastOpen*）保留 —— 它们是「这台机器历史上发生过什么」的证据。
        fanOutOpenedThisTick = 0;
        fanOutScannedSinceSuccess = 0;
        idleSearchCooldown = 0;
        // ★ 2026-11-02【补料重试】：节流位与「上一 tick 的补料结果」一并清（累计跳过 tick 数是历史证据，保留）
        fillRetryCooldown = 0;
        fillOpenedNothingLastTick = false;
        fillLostBatchLastTick = false;
        wokenByInputChange = false;
        if (lost > 0) {
            // 结构失效 / 切模式 / 停机 ⇒ 整表丢掉（含已经扣掉的料，与基类 resetRecipeLogic 同口径）。
            // 这一行只在「真的丢了东西」时打 ⇒ 不会刷屏，但能把「在用 0」是"没开出来"还是"被清掉了"分开。
            NanochipModContext.logger().info("[gtlmodular] NAC 独立线程：配方逻辑被重置 ⇒ 丢弃 {} 条在跑批次"
                            + "（已扣的料不退回，与基类同口径）；累计开过 {} 条。",
                    lost, batchesOpenedTotal);
        }
    }

    // ==========================================================================================
    // Host 六法（参考实现的契约，逐个用真实 API 实现）
    // ==========================================================================================

    /** ① 机器：基类的 {@code public final IRecipeLogicMachine machine} 字段（javap 现读 :10）。 */
    @Override
    public IRecipeLogicMachine machine() {
        return this.machine;
    }

    /** ② 同时几条：本机既有线程数阶梯（拿不到 ⇒ 1 = 关掉并发）。 */
    @Override
    public int slots() {
        return getThreadLimit();
    }

    /**
     * ★ 2026-11-01【并行二分=0 · 抽样】<b>对「第一条被拒的候选」做一次几何细读</b>（可读形式：重新问一次同样的几个数）。
     *
     * <h3>为什么必须重新问一次（而不是在 {@link #prepareBatch} 里顺手记）</h3>
     * {@code prepareBatch} 里的判定链每一道都必须<b>逐字</b>保持原样（本轮的修复纪律是「只补上界、不动判定」）；
     * 把记录语句塞进那五道闸里会让「这道闸到底改没改」变得难以复核。这里的做法是：
     * 判定链一个字不动，被拒之后<b>再问一遍</b>同样的问题、只读地记下来
     * （{@code findBatch} 里本 tick 对该候选只会调用一次，所以成本是每条候选一次）。
     *
     * <h3>它回答什么（原诊断答不出的三件事）</h3>
     * <ol>
     *   <li>{@code inputMax=} 输入侧上限<b>原样值</b> —— 把「≤ 0」与「大到溢出」分开；</li>
     *   <li>{@code 二分=} 钳上界之后真的找到了几份并行（0 = 连 1 份都放不下 ⇒ 真的是料/位置不够）；</li>
     *   <li>{@code 谓词首次判假@N/侧=} 二分谓词<b>第一次</b>说「放不下」的那一档与<b>哪一侧</b>
     *       （输入侧 / 产出侧）—— 「看不见料」与「出料口满了」是两个完全相反的检修方向；</li>
     *   <li>{@code 整条电量=} / {@code 已存=} / {@code 按需现取=} —— 预蓄电闸那三个读数（限频 INFO，见
     *       {@code diagnoseCandidates}）。</li>
     * </ol>
     *
     * <p><b>纯只读</b>：只写本类自己的诊断字段；不扣料、不改批次表、不碰 NBT。
     * 任何一步抛异常都只记一行 debug（诊断绝不影响派活）。
     */
    private void inspectFitFailure(GTRecipe candidate) {
        if (candidate == null) {
            return;
        }
        try {
            GTRecipe modified = machine.fullModifyRecipe(candidate.copy(), ocParams, ocResult);
            if (modified == null) {
                fitSampleRecipeId = idOf(candidate);
                fitSampleProbeSide = "配方修改器判 null（no energy hatch / 超频被封 / 修改器拒绝）";
                return;
            }
            // ★ 2026-11-01【输入侧 · 决定性诊断】：先按「原样配方」问一次两侧匹配
            //   （这是「输入侧为空」与「产出侧满」之间的分界线；纯只读、不参与任何判定）
            sampleInputMatch(modified);
            long parallel = fitParallel(modified);
            fitSampleMatchable = parallel;
            fitSampleRecipeId = idOf(candidate);
            if (parallel > 0L) {
                GTRecipe scaled = ensureRuns(NanochipParallelFit.scalePrecisely(modified, parallel), parallel);
                fitSampleWholeEu = plannedWholeRecipeEu(scaled);
            } else {
                fitSampleWholeEu = plannedWholeRecipeEu(modified);
            }
            sampleEnergyReading();
        } catch (RuntimeException e) {
            fitSampleRecipeId = idOf(candidate);
            fitSampleProbeSide = "抽样抛异常: " + e.getClass().getSimpleName()
                    + (e.getMessage() == null ? "" : ": " + e.getMessage());
        }
    }

    /**
     * ★ 2026-11-01【输入侧 · 决定性诊断】按<b>原样配方</b>问一次「输入匹配得上吗 / 产出放得下吗」。
     *
     * <p>为什么必须在 {@link #fitParallel} <b>之前</b>问：{@code fitParallel} 先读
     * {@code IParallelLogic.getMaxParallel}，一读到 {@code ≤ 0} 就带着
     * 「输入侧上限 ≤ 0」这个结论返回 —— 那时日志里就再也分不出「连 1 份都匹配不上」
     * 与「1 份匹配得上、只是不能放大」。这里补的就是这两个数（{@code 输入项数} /
     * {@code 输入匹配} / {@code 产出匹配}）。
     *
     * <p><b>纯只读</b>：两个方法都是 gtlcore 的模拟匹配（{@code simulate=true} 那条路），
     * 不扣料、不改批次表；任何异常只记 debug 并把两格留在 {@code false}（= 「没读到好结果」，
     * 绝不把它当成判据 —— 本方法完全不参与派活）。
     */
    private void sampleInputMatch(GTRecipe recipe) {
        fitSampleProbed = true;
        fitSampleInputContents = countContents(recipe.inputs) + countContents(recipe.tickInputs);
        try {
            fitSampleInputMatch = RecipeRunnerHelper.matchRecipeInput(machine, recipe);
            fitSampleOutputMatch = RecipeRunnerHelper.matchRecipeOutput(machine, recipe);
        } catch (RuntimeException e) {
            NanochipModContext.logger().debug("[gtlmodular] NAC 独立线程：原样配方匹配抽样失败（诊断用，忽略）：{}",
                    e.toString());
        }
    }

    /** 一份内容表的条数（{@code null} / 空一律 0）——诊断计数用。 */
    private static int countContents(Map<RecipeCapability<?>, List<Content>> contents) {
        if (contents == null || contents.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (List<Content> list : contents.values()) {
            if (list != null) {
                total += list.size();
            }
        }
        return total;
    }

    /** 抽样用的配方 id（{@code null} → {@code "?"}；只读）。 */
    private static String idOf(GTRecipe recipe) {
        if (recipe == null || recipe.getId() == null) {
            return "?";
        }
        return recipe.getId().toString();
    }

    /** 采样预蓄电闸那三个读数（{@code 整条电量 / 已存 / 按需现取}）——纯只读、不参与判定。 */
    private void sampleEnergyReading() {
        if (!(machine instanceof NanochipAssemblyComplexMachine nac)) {
            return;
        }
        fitSampleUnlimitedEnergy = nac.isEnergySourceEffectivelyUnlimited();
        try {
            fitSampleStoredEu = Math.max(0L, nac.getObtainableEnergyEU());
        } catch (RuntimeException e) {
            NanochipModContext.logger().debug("[gtlmodular] NAC 独立线程：抽样读取可用电量失败（诊断用，忽略）：{}", e.toString());
        }
    }

    /**
     * ③ 找一条新批次：<b>一次调用只准备「一条」</b>（参考实现的语义），准备不出来的候选顺次跳过。
     *
     * <p>候选池一个 tick 只搜一轮（{@link #candidateRecipes}），本方法在池里往前推游标：
     * 同一条配方可以被取第二次（fan-out，见 {@link #FAN_OUT_SAME_RECIPE}），
     * 因为每次取出来都会重新走一遍 {@code fullModifyRecipe} + 并行二分（<b>新建对象、绝不共享</b>）。
     *
     * <h3>★ 2026-11-02【同配方 fan-out · 修复】为什么改前「一种样板堆再多料也只开 1 条」</h3>
     * 候选池是 {@code LinkedHashSet<GTRecipe>}，而 {@code GTRecipe.equals/hashCode} <b>只比 id</b>
     * （javap 现读 {@code _census/_gtceu_GTRecipe_copy.javap.txt:1178-1201}）⇒ <b>同一条配方在池里只可能出现一次</b>，
     * 而改前的循环是「游标单调往前」+「走到池尾就 {@code return null}」
     * ⇒ 第二次调用必然返回 null ⇒ {@code NanochipConcurrentBatches.tick} 第③步的
     * {@code while (batches.size() < slots)} 立刻 break ⇒ <b>永远只开 1 条</b>
     * （{@code FAN_OUT_SAME_RECIPE} 那个开关于是形同虚设：池里根本没有第二份可 fan-out）。
     *
     * <p>修法照参考实现作者原话「第二轮轮转 fan-out：每个在跑配方各加最多一条，
     * 一趟一条都没开出来就收手」：游标走到池尾时<b>绕回池头</b>继续取，直到
     * 「自上次成功以来已把整池走完一遍」（= 这一刻确实开不出新批次）才收手。
     * <b>终止性</b>：每次成功都要经过 {@link #consumeInputs} 真扣一次料/条件判定，
     * 同一 tick 内没有任何东西会凭空变多 ⇒ 成功次数有限 ⇒ 必然收敛到「整池一遍无成功」。
     */
    @Override
    public GTRecipe findBatch() {
        if (host == null) {
            return null;   // 拿不到机器契约 ⇒ 不派活（已在跑的批次照常跑完，绝不吞料）
        }
        // ★ 2026-11-02【空闲限频】：上一 tick 收尾是 IDLE（没批次、没等待）时不每 tick 重搜
        //   （见 IDLE_SEARCH_THROTTLE 的完整推导；唤醒口 = updateTickSubscription）。
        if (idleSearchThrottled()) {
            return null;
        }
        // ★ 2026-11-02【补料重试 · 每 tick 成本闸】：稳态（都在跑、且上一 tick 补料必然失败）时不每 tick
        //   重跑那一整套昂贵搜索（完整推导见 FILL_RETRY_THROTTLE；唤醒口 = updateTickSubscription）。
        if (fillRetryThrottled()) {
            return null;
        }
        // ★ 2026-11-02【每 tick 成本闸】：一个 tick 最多新开 FAN_OUT_MAX_PER_TICK 条
        //   （只影响补满速度，不影响稳态并发条数，理由见该常量注释）。
        if (fanOutOpenedThisTick >= FAN_OUT_MAX_PER_TICK) {
            return null;
        }
        if (candidatePool == null) {
            candidatePool = candidateRecipes();
            candidateCursor = 0;
            fanOutScannedSinceSuccess = 0;   // 新一池 ⇒ 轮转计数从零开始
            if (candidatePool.isEmpty() && firstReject == null) {
                // ★ 2026-11-01【抓手】：池子空 ⇒ 记下来（搜索失败 / 原始枚举 0 条 / 全被资格闸拒）
                firstReject = "候选池为空（原始枚举 " + candidateRawCount + " 条，通过资格闸 "
                        + candidateUsableCount + " 条）";
            }
        }
        while (true) {
            if (candidateCursor >= candidatePool.size()) {
                // ★ fan-out 轮转：整池走完一遍还没开出新的 ⇒ 收手（这就是「一趟一条都没开出来」）
                if (!FAN_OUT_SAME_RECIPE || candidatePool.isEmpty()
                        || fanOutScannedSinceSuccess >= candidatePool.size()) {
                    return null;   // 断档：本 tick 没有可开的新批次（不回退成「整机不跑」）
                }
                candidateCursor = 0;   // 绕回池头：同一条配方可以再占一条批次（各自新建对象、各自计时）
            }
            GTRecipe candidate = candidatePool.get(candidateCursor++);
            fanOutScannedSinceSuccess++;
            if (!FAN_OUT_SAME_RECIPE && isRunningElsewhere(candidate)) {
                continue;   // 关掉 fan-out 时：一种配方最多占一条批次
            }
            GTRecipe prepared = prepareBatch(candidate);
            if (prepared != null) {
                fanOutScannedSinceSuccess = 0;   // 开出新的 ⇒ 轮转重新计数（下一趟仍可继续补）
                fanOutOpenedThisTick++;
                return prepared;
            }
            if (fitSampleArmed && fitSampleRecipeId == null) {
                inspectFitFailure(candidate);   // ★ 2026-11-01：只对第一条被拒的候选做一次几何细读
            }
        }
    }

    /**
     * ④ <b>原子扣料</b>：这是整条路上<b>唯一</b>会写世界的 Host 方法。
     *
     * <p>顺序逐字对齐基类 {@code setupRecipe}：{@code machine.beforeWorking(recipe)}（唯一能否决开工的钩子）
     * → {@code recipe.preWorking(machine)}（开工前通知各 handler）→
     * {@code RecipeRunnerHelper.handleRecipeInput(machine, recipe)}（真扣非 tick 输入，ME 感知）。
     *
     * <p><b>扣不齐就不收这条</b>：直接返回 false，<b>不写任何状态</b>（不占槽位、不记预留、不进批次表）
     * ⇒ 不会吞料，也不会让机器一直报缺料；下个 tick 会重新走一遍第三步。
     */
    @Override
    public boolean consumeInputs(GTRecipe recipe) {
        if (recipe == null) {
            return false;
        }
        boolean allowed;
        try {
            allowed = machine.beforeWorking(recipe);
        } catch (RuntimeException e) {
            noteReject(recipe, "扣料前 beforeWorking 抛异常", e.getClass().getSimpleName() + ": " + e.getMessage());
            NanochipModContext.logger().debug("[gtlmodular] NAC 独立线程：beforeWorking 抛异常，不收这一条：{}", e.toString());
            return false;
        }
        if (!allowed) {
            noteReject(recipe, "机器级 beforeWorking 否决（维护/结构/模块未被允许开工）", null);
            return false;   // 机器说不许开工 ⇒ 没扣任何料
        }
        try {
            recipe.preWorking(machine);
        } catch (RuntimeException e) {
            noteReject(recipe, "preWorking 抛异常", e.getClass().getSimpleName() + ": " + e.getMessage());
            NanochipModContext.logger().debug("[gtlmodular] NAC 独立线程：preWorking 抛异常，不收这一条：{}", e.toString());
            return false;
        }
        boolean consumed;
        try {
            consumed = RecipeRunnerHelper.handleRecipeInput(machine, recipe);
        } catch (RuntimeException e) {
            consumed = false;
            noteReject(recipe, "扣料 handleRecipeInput 抛异常", e.getClass().getSimpleName() + ": " + e.getMessage());
            NanochipModContext.logger().debug("[gtlmodular] NAC 独立线程：扣料抛异常，不收这一条：{}", e.toString());
        }
        if (!consumed) {
            noteReject(recipe, "扣料不齐（输入侧拿不到料 / 身份闸拒了这个槽）", null);
            return false;
        }
        // 只在扣料成功之后才写附注（这样「失败」在这条路上不留任何痕迹）
        BatchState state = stateOf(recipe);
        state.caches = makeChanceCaches();
        state.reservation = receiptOf(recipe);
        state.seq = ++batchSeqCounter;   // ★ 2026-11-02【稳定编号】：显示用的 #N 在开批次这一刻钉死
        batchesOpenedTotal++;   // ★ 2026-11-01：诊断用「累计开过几条」（见字段注释）
        return true;
    }

    /**
     * ⑤ <b>每 tick 的输入</b>（EU / tick 流体）：不够返回 false ⇒ 这条批次原地等（不涨进度、也不被摘掉）。
     *
     * <p>三条与基类 {@code handleTickRecipe} 逐字同构（javap 现读 {@code _javap_simple_recipelogic.txt:673-700}）：
     * <ol>
     *   <li>{@code recipe.matchTickRecipe(machine)}：<b>模拟</b>；<b>没有 tick 内容的配方直接成功</b>
     *       （该方法内部就是 {@code hasTick() ? match : SUCCESS}，同文件 {@code GTRecipe} 侧
     *       {@code _gtceu_GTRecipe_copy.javap.txt:411-422}）⇒ 本类不需要自己判 {@code hasTick}；</li>
     *   <li>成功后 {@code recipe.handleTickRecipeIO(IO.IN, machine, caches)} 真扣；</li>
     *   <li>再 {@code IO.OUT}。<b>这两个返回值与基类一致地不看</b>——基类在同文件 {@code :689/:694}
     *       两条 {@code invokevirtual} 之后各跟一条 {@code pop} ⇒ 「够不够」在第一步已经判过；</li>
     * </ol>
     */
    @Override
    public boolean tickInputs(GTRecipe recipe) {
        if (recipe == null) {
            return false;
        }
        // ★ 第一步与基类 handleRecipeWorking 逐字同构：先过「配方自带条件」
        //   （javap 现读 _javap_simple_recipelogic.txt:397 —— 基类第一句就是 recipe.checkConditions(this)）。
        //   条件这一刻不满足 ⇒ 这条批次原地等（不涨进度、也不摘掉），绝不带着不满足的条件往下跑。
        if (!conditionsOk(recipe, true)) {
            return false;
        }
        GTRecipe.ActionResult match;
        try {
            match = recipe.matchTickRecipe(machine);
        } catch (RuntimeException e) {
            NanochipModContext.logger().debug("[gtlmodular] NAC 独立线程：tick 匹配抛异常，这条原地等：{}", e.toString());
            return false;
        }
        if (match == null || !match.isSuccess()) {
            if (waitReason == null) {
                waitReason = reasonOf(match);
            }
            return false;   // 原地等：不涨进度、也不摘掉这条
        }
        Map<RecipeCapability<?>, Object2IntMap<?>> caches = cachesOf(recipe);
        recipe.handleTickRecipeIO(IO.IN, machine, caches);
        recipe.handleTickRecipeIO(IO.OUT, machine, caches);
        return true;
    }

    /**
     * ⑥ <b>批次收尾</b>：★ <b>校准记账 + 配方日志（出料<b>前</b>）</b> → {@code afterWorking} →
     * {@code postWorking} → 出料 → 撤附注。
     *
     * <p>出料那一段的顺序照参考实现（<b>先 afterWorking/postWorking，再出料</b>），也照基类
     * {@code onRecipeFinish}（javap 现读 {@code _javap_simple_recipelogic.txt:927-945}：先
     * {@code machine.afterWorking()}，再 {@code lastRecipe.postWorking(machine)}，
     * 再 {@code handleRecipeIO(lastRecipe, IO.OUT)}）。
     *
     * <p>★ <b>搬迁点 H2</b>：旧逻辑在 {@code onRecipeFinish} 覆写里做「记纳米校准 + 打『跑了哪条配方』日志」
     * （{@code NanochipAssemblyComplexMachine.java:1505-1510}），而本类不经过父类的 {@code onRecipeFinish}
     * ⇒ 必须在这里补上，否则<b>纳米校准不再累积</b>（会连带影响校准档位与配方加成）。
     * 走的是机器侧既有入口：{@code recordExternalProduction}（{@code public}，
     * {@code NanochipAssemblyComplexMachine.java:3287}）与 {@code logCraftedRecipe}（包内可见，同文件 {@code :3085}）。
     *
     * <p>★ <b>2026-10-31【M4② 记账时机统一】</b>：这两句原来在<b>出料之后</b>
     * （{@code handleRecipeOutput} 后面），而另两条记账通路都是<b>出料前</b>：
     * <ul>
     *   <li>旧逻辑 {@code NanochipRecipeLogic.onRecipeFinish}：{@code nac.recordProduction(last)}
     *       （{@code :1507}）在 {@code super.onRecipeFinish()}（{@code :1509}，真正出料）之前；</li>
     *   <li>组装矩阵 {@code ModuleRecipeLogic.onRecipeFinish}：{@code host.recordExternalProduction(...)}
     *       （{@code :1278}）在 {@code super.onRecipeFinish()}（{@code :1282}）之前。</li>
     * </ul>
     * 本轮把本类也统一到<b>出料前</b>，理由两条（都不是口味问题）：
     * <ol>
     *   <li><b>消掉一个"记不上"的隐患</b>：出料走 {@code RecipeRunnerHelper.handleRecipeOutput}
     *       → 配方侧 IO 处理；若那条路对普通输出总线<b>就地改写/清空</b>产物流，
     *       那么"出料后才读 {@code getOutputContents(CAP)}"就会读到空 ⇒ 又变成"记不上"。
     *       出料前读，读的是配方原始产出，<b>与另外两条已验证通路同序同源</b>
     *       （该隐患的取证状态见诊断 §⑩-2，本轮按"与已验证写法同序"处理最稳）；</li>
     *   <li><b>语义一致</b>：记的是"这条配方产出了什么"，不是"出料口收下了多少" ——
     *       出料口满导致产物丢弃时（{@code handleRecipeOutput} 返回 false，基类行为就是丢弃），
     *       旧逻辑与模块侧<b>照样记账</b>；本类若留在出料后就会与它们口径不一致。</li>
     * </ol>
     * <b>刻意保留</b>的一点：记账仍在 {@code afterWorking()} <b>之后</b>（旧逻辑是先
     * {@code recordProduction} 再 {@code super.onRecipeFinish} → 基类里才 {@code afterWorking}，
     * 严格讲旧逻辑更早）；本轮只对齐"出料前/后"这一条分界，不动 {@code afterWorking} 的相对位置，
     * 把改动面压到最小。<b>回退</b>：把这两句挪回 {@code batchStates.remove(recipe)} 之后即可。
     */
    @Override
    public void finishOutputs(GTRecipe recipe) {
        if (recipe == null) {
            return;
        }
        // ★ 出料前记账（时机与旧逻辑 :1507 / 组装矩阵 :1278 统一；理由见方法注释）
        if (machine instanceof NanochipAssemblyComplexMachine nac) {
            nac.recordExternalProduction(recipe);   // ★ 校准记账（旧逻辑 onRecipeFinish 的第一件事）
            nac.logCraftedRecipe(recipe);           // ★ 配方日志（第二件事；内部自带 1 秒限流）
        }
        try {
            machine.afterWorking();
        } catch (RuntimeException e) {
            NanochipModContext.logger().debug("[gtlmodular] NAC 独立线程：afterWorking 抛异常（已忽略，不影响出料）：{}",
                    e.toString());
        }
        try {
            recipe.postWorking(machine);
        } catch (RuntimeException e) {
            NanochipModContext.logger().debug("[gtlmodular] NAC 独立线程：postWorking 抛异常（已忽略，不影响出料）：{}",
                    e.toString());
        }
        if (RecipeRunnerHelper.matchRecipeOutput(machine, recipe)) {
            if (!RecipeRunnerHelper.handleRecipeOutput(machine, recipe)) {
                NanochipModContext.logger().debug("[gtlmodular] NAC 独立线程：出料未全部成交（出料口容量不足），"
                        + "溢出部分与 GTCEu 基类行为一致地丢弃");
            }
        } else {
            NanochipModContext.logger().debug("[gtlmodular] NAC 独立线程：出料口放不下这一条的产出，产出丢弃"
                    + "（与 GTCEu 基类 onRecipeFinish 的行为一致）");
        }
        batchStates.remove(recipe);   // 撤掉这条批次的附注（概率缓存 + 预留）
    }

    // ==========================================================================================
    // findBatch 的「准备一条批次」= check 阶段（全程只读，绝不扣料）
    // ==========================================================================================

    /**
     * 搜一轮候选（<b>跨配方类型</b>）—— 见 {@link NanochipCandidateSearch}。
     *
     * <p>与旧逻辑共用同一份候选口径：同一批类型、同一个截断（到批次上限）、同一个公平轮转游标
     * ⇒ 不会出现「旧逻辑搜两类、新逻辑只搜一类」这种漂移。
     */
    private List<GTRecipe> candidateRecipes() {
        if (host == null) {
            return List.of();
        }
        List<String> failures = new ArrayList<>(2);
        int[] stats = new int[2];
        try {
            GTRecipeType[] types = host.nanochipSearchTypes();
            // ★ 2026-11-01【修复·候选为空】：改用逐类型隔离的那一份（与 search 同一实现、同口径），
            //   于是「某一个类型的 lookup 抛异常」不再把整池候选一起清空；失败说明记进 failures，
            //   由 diagnoseCandidates() 限频打出来（改前这些异常只走 logger.debug，默认日志等级看不到
            //   ⇒ 现场表现就是「在用 0/N」而日志里什么都没有）。
            Set<GTRecipe> found = NanochipCandidateSearch.searchIsolating(machine, types, machine.getRecipeType(),
                    this::isCandidateUsable, getThreadLimit(), host.nanochipThreadCursors(), failures, stats);
            candidateRawCount = stats[0];
            candidateUsableCount = stats[1];
            if (!failures.isEmpty()) {
                candidateSearchFailure = String.join("；", failures);
            }
            return List.copyOf(found);
        } catch (RuntimeException e) {
            // 与旧逻辑同款纪律：搜索阶段异常 ⇒ 放弃本次搜索（本 tick 不开工），绝不把异常抛进 tick。
            // ★ 2026-11-01：失败原因改成「记进 candidateSearchFailure + 由限频诊断打出」，
            //   不再只留一行 debug（那正是「候选恒空」在日志里看不见的原因）。
            candidateSearchFailure = e.getClass().getSimpleName()
                    + (e.getMessage() == null ? "" : ": " + e.getMessage());
            return List.of();
        }
    }

    /**
     * 候选校验（<b>只读</b>）：机器说能用 && 电压等级够。
     *
     * <p>「一条候选能不能跑」全机只该有<b>一个</b>口径 ⇒ 第一半转交机器侧
     * {@link NanochipThreadHost#nanochipCandidateUsable}（它转发旧逻辑的 {@code checkRecipe}，
     * 口径 = 输入放得下 + 产出放得下 + 电压够 + 配方自带条件）。
     *
     * <p>★ <b>诚实边界</b>：当总开关开着（本类就是当前逻辑）时，机器侧那个转交会拿不到
     * {@code NanochipRecipeLogic} 实例 ⇒ 它按 fail-safe 返回 {@code true}。
     * 于是本类自己在后面把这几条<b>逐条补齐</b>：{@code checkConditions}（配方自带条件）、
     * {@code matchRecipeInput}/{@code matchRecipeOutput}（真的放得下，见 {@link #canAdmit}）、
     * 以及这里的电压等级闸 ⇒ 判据集合与旧逻辑那份 {@code checkRecipe} <b>等价</b>。
     */
    private boolean isCandidateUsable(GTRecipe candidate) {
        if (candidate == null) {
            return false;
        }
        try {
            if (host != null && !host.nanochipCandidateUsable(candidate)) {
                return false;
            }
        } catch (RuntimeException e) {
            // 辅助判定读不到 ⇒ 不挡（后面还有本类自己那几道闸）
            NanochipModContext.logger().debug("[gtlmodular] NAC 独立线程：机器侧候选校验抛异常（不挡）：{}", e.toString());
        }
        return recipeEuTier(candidate) <= hostTier();
    }

    /**
     * 把一条候选<b>准备成</b>一条可入表的批次；准备不出来返回 {@code null}（顺次试下一个候选）。
     *
     * <p>全流程<b>只读</b>（唯一的写入点在 {@link #consumeInputs}）：
     * <ol>
     *   <li>{@code machine.fullModifyRecipe(recipe.copy(), ocParams, ocResult)} ——
     *       超频 / 时长下限 / 模块与校准加成（本机的 {@code nacRecipeModifier} 就在这条链上）。
     *       ⚠️ 用 {@code IRecipeLogicMachine.fullModifyRecipe}（javap 现读
     *       {@code _javap_simple_recipelogic.txt:338} 的 {@code invokeinterface}），
     *       不调机器自己的 {@code protected getRealRecipe}——跨包访问 protected 不合法；</li>
     *   <li>{@link #fitParallel} —— <b>本条批次自己的并行</b>（输入侧上限 → 二分收敛到「真的放得下」）；</li>
     *   <li>{@link NanochipParallelFit#scalePrecisely} —— <b>精确 long 缩放</b>（不用 double：
     *       超过 2^53 会把物品/流体数量四舍五入）；</li>
     *   <li>{@link #ensureRuns} —— 保证「运行份数 ≥ 1」（否则 gtlcore 掷骰时会按 0 份算）；</li>
     *   <li>{@code recipe.checkConditions(this)} —— 维度 / 清洁室 / 研究等配方自带条件；</li>
     *   <li>{@link #hostPrecharged} —— ★ <b>搬迁点 H1</b>：主机预蓄电 + 无线电网可获取能力；</li>
     *   <li>{@link #canAdmit} —— <b>最后一道闸</b>：把「本条 + 已在跑各条的预留」叠在一份临时配方上，
     *       用 {@code RecipeRunnerHelper.matchRecipeInput/matchRecipeOutput} 问「真的放得下吗」。</li>
     * </ol>
     */
    private GTRecipe prepareBatch(GTRecipe origin) {
        GTRecipe modified;
        try {
            modified = machine.fullModifyRecipe(origin.copy(), ocParams, ocResult);
        } catch (RuntimeException e) {
            noteReject(origin, "配方修改器抛异常",
                    e.getClass().getSimpleName() + ": " + e.getMessage());
            NanochipModContext.logger().debug("[gtlmodular] NAC 独立线程：配方修改器抛异常，跳过该候选：{}", e.toString());
            return null;
        }
        if (modified == null) {
            noteReject(origin, "配方修改器判 null（无能源仓 / 超频被封 / 修改器拒绝）", null);
            return null;   // 没有能源仓 / 修改器判 NULL ⇒ 这条候选开不出批次
        }
        long parallel = fitParallel(modified);
        if (parallel <= 0L) {
            noteReject(origin, "并行二分=0（这一刻连 1 份都放不下，或输入侧上限 ≤ 0）", null);
            return null;   // 这一刻一个都放不下（或输入侧就不够）⇒ 宁可少跑一条
        }
        GTRecipe scaled = ensureRuns(NanochipParallelFit.scalePrecisely(modified, parallel), parallel);
        if (scaled == null) {
            noteReject(origin, "精确缩放失败", null);
            return null;
        }
        if (!conditionsOk(scaled, false)) {
            noteReject(origin, "配方自带条件不满足（维度 / 清洁室 / 研究…）", null);
            return null;
        }
        if (!hostPrecharged(scaled)) {
            noteReject(origin, "主机预蓄电闸拦住（整条配方电量 > 已存 + 20t 可补）", null);
            return null;
        }
        if (!canAdmit(scaled)) {
            noteReject(origin, "准入闸拦住（本条 + 在跑预留 叠起来放不下）", null);
            return null;
        }
        // ★ 2026-11-02【开批次读数】：把「这条批次的并行是怎么来的」留下来（纯只读、只写自己的诊断字段）。
        //   为什么必须留：改前的诊断只在「候选被拒」时武装（fitSampleArmed 要求批次表为空），
        //   而现场的形态是「开出来了、在用 1 条、并行 63」⇒ 成功这条路上的数字一条都没落日志。
        lastOpenRecipeId = idOf(origin);
        lastOpenInputMax = fitInputMax;
        lastOpenBound = fitBound;
        lastOpenParallel = parallel;
        lastOpenBranch = fitBranch;
        return scaled;
    }

    /**
     * ★ 2026-11-01【候选为空 · 抓手】记下「这一 tick 里<b>第一条</b>候选为什么没开成批次」。
     *
     * <p>只记第一条（后面的同因拒绝没有新信息），由 {@link #diagnoseCandidates()} 限频打出
     * —— 这是「候选有、但一条都开不出来」与「候选池本身为空」在日志里的分界线。
     * 纯只读：不参与任何判定、不改返回值的走向。
     */
    private void noteReject(GTRecipe candidate, String gate, String detail) {
        if (firstReject != null) {
            return;
        }
        firstReject = (candidate == null || candidate.getId() == null ? "?" : candidate.getId().toString())
                + " → " + gate + (detail == null ? "" : "（" + detail + "）");
    }

    /**
     * <b>本条批次的并行上限</b>：输入侧上限（{@code IParallelLogic.getMaxParallel}）→
     * <b>先把上界钳到机器并行上限那一档（{@code int} 边界）</b> → 二分找
     * 「加上已在跑批次的预留也真的放得下」的最大值。
     *
     * <p>单条上限先给 {@code Long.MAX_VALUE}（<b>每条各自满并行</b>，没有「总池摊分」这个中间量），
     * 真正的值由二分收敛 —— 这是防「算出天文数字 ⇒ 起跑前被 {@code matchRecipeOutput} 挡掉 ⇒
     * 永远 0%」的关键（与旧逻辑「去总池」同源）。
     *
     * <p>★ <b>2026-11-01【并行二分=0 · 修复】为什么还要再钳一次</b>：上面那句
     * 「真正的值由二分收敛」只有在<b>搜索区间不越过机器能力</b>时才成立。二分是<b>单调假设</b>下的算法
     * （见 {@link NanochipParallelFit#MAX_PARALLEL_SEARCH_BOUND} 的完整推导）：当
     * {@code IParallelLogic.getMaxParallel} 给出 {@code Long.MAX_VALUE} 级的「无限输入」时，
     * 二分的中点全部落在「内容数量 × Long.MAX_VALUE」的<b>算术溢出区</b> ⇒ 谓词恒假 ⇒ 二分恒 0 ⇒
     * 现场表现就是「候选枚举正常、一份都放不下」。
     * 钳到 {@code int} 边界（= 本机 {@code getMaxParallel()} 的界面值）之后谓词恢复单调，
     * 二分重新在机器真实能力内收敛。
     *
     * @return 0 = 这条候选这一刻一个都放不下（或输入侧就不够）
     */
    private long fitParallel(GTRecipe recipe) {
        // ★ 2026-11-02【并行上限读数口径】：limit 参数取「我们自己能用的最大档」，而不是 Long.MAX_VALUE
        //   （理由与取证见 PARALLEL_LIMIT_BOUNDED 的注释 —— 只是把上游溢出风险掐掉，绝不会让读数变小）。
        long limitArgument = PARALLEL_LIMIT_BOUNDED
                ? NanochipParallelFit.MAX_PARALLEL_SEARCH_BOUND
                : Long.MAX_VALUE;
        // ★★ 0.3.132【按槽开批 · 上界取法】—— 恢复 `_census/按槽开批_实施记录.md` 的 H9（本轮重新落地）★★
        //   改前（= 本轮之前）只看上游那一个数 `inputMax`，而上游那个数**只读"第一个有货槽"**：
        //     `IParallelLogic.getMaxParallel` → `MEPatternRecipeHandlePart.getMEContent`
        //     → `MEPatternBufferRecipeHandlerTraitBase.getStackMapFromFirstAvailableSlot`
        //     → `MEPatternBufferPartMachineBase.getFirstActiveInternalSlot`（**遇第一个 active 槽就 return**）。
        //   ⇒ 本机实测形态就是"首槽 625 ⇒ 单条并行恒被夹在 625"，K 也只有 1~2（方案 §⑥-0 的 U3）。
        //   ★ 本轮的取法：`inputMax = max(上游首槽读数, 按槽最大读数)`，**只放宽二分的搜索上界**；
        //     `fitsParallel` / `hostPrecharged` / `canAdmit` / `consumeInputs`（= 准入与扣料）**一个字不改**
        //     ⇒ 放不下的批次仍然一条都收不进来（放宽的只是"从哪里开始找"，不是"要不要收"）。
        //   ⚠ 口径提醒（方案 §⑤-C4）：上游"只读首槽"与"单槽独立吃下整条需求"是**成对**的契约，
        //     所以这一步**不能只改读数**；本实现靠"一条批次仍然只吃一个槽"（消费侧一字未改）来保持自洽。
        //   ⚠ 默认值：`PER_SLOT_PARALLEL_BOUND` 为 **`false`** ⇒ 逐字回到改前（零行为变化、零风险红线）。
        //     要看效果（或做方案 §⑦-V2 确诊）时再改成 `true` 重编。
        long upstreamInputMax;
        try {
            upstreamInputMax = IParallelLogic.getMaxParallel(machine, recipe, limitArgument);
        } catch (RuntimeException e) {
            NanochipModContext.logger().debug("[gtlmodular] NAC 独立线程：并行上限读取失败，跳过该候选：{}", e.toString());
            fitInputMax = -1L;
            fitUpstreamInputMax = -1L;
            fitSlotInputMax = NO_SLOT_BOUND;
            fitBound = -1L;
            fitParallelValue = 0L;
            fitBranch = "输入侧上限读取抛异常";
            fitSampleInputMax = -1L;
            fitSampleInputMaxOverBound = false;
            fitSampleMatchable = 0L;
            fitSampleProbeSide = "输入侧上限读取抛异常";
            recordProbe(0L);
            return 0L;
        }
        fitUpstreamInputMax = upstreamInputMax;
        // ★ 0.3.132【按槽开批】这里直接送 `recipe`（本就是 `GTRecipe`）——
        //   `getInputContents(cap)` 真正定义在 GTCEu 的 `GTRecipe` 上
        //   （`_census/_gtceu_GTRecipe_copy.javap.txt:363`），gtlcore 的 `IGTRecipe` 上**没有**这个方法。
        fitSlotInputMax = PER_SLOT_PARALLEL_BOUND
                ? perSlotInputBound(recipe)
                : NO_SLOT_BOUND;
        long inputMax = fitSlotInputMax > upstreamInputMax ? fitSlotInputMax : upstreamInputMax;
        if (inputMax <= 0L) {
            // ★ 2026-11-01【并行二分=0 · 抽样】：这一支是「输入侧上限 ≤ 0」——
            //   与「上限正数但二分收敛到 0」是两条完全不同的故障，必须在日志里分开。
            fitInputMax = inputMax;
            fitBound = 0L;
            fitParallelValue = 0L;
            fitBranch = "输入侧上限 ≤ 0（这一刻连 1 份输入都凑不齐 / 电压侧判 0）";
            fitSampleInputMax = inputMax;
            fitSampleInputMaxOverBound = false;
            fitSampleMatchable = 0L;
            fitSampleProbeSide = "输入侧上限 ≤ 0（这一刻连 1 份输入都凑不齐 / 电压侧判 0）";
            recordProbe(0L);
            return 0L;
        }
        fitInputMax = inputMax;
        fitBound = Math.min(inputMax, NanochipParallelFit.MAX_PARALLEL_SEARCH_BOUND);
        fitSampleInputMax = inputMax;
        fitSampleInputMaxOverBound = inputMax > NanochipParallelFit.MAX_PARALLEL_SEARCH_BOUND;
        // ★ 2026-11-01【并行二分=0 · 修复】上界先钳到「机器并行上限」那一档（int 边界）再二分。
        //   原因（完整链条见 NanochipParallelFit.MAX_PARALLEL_SEARCH_BOUND 的注释）：
        //   ME 样板总成对「无限输入」会给出 Long.MAX_VALUE 级的输入侧上限，而原实现把它原样交给
        //   单调假设下的二分 ⇒ 每个中点都落在「内容 × Long.MAX_VALUE」的溢出区 ⇒ 谓词恒假 ⇒
        //   二分结果恒 0 ⇒ 现场就是「配方找得到、一份都放不下」。钳到 int 边界后谓词恢复单调。
        //   回退：把这一句换回 NanochipParallelFit.findHighestMatchable(inputMax, …) 即可。
        long matchable = NanochipParallelFit.findHighestMatchableInSafeRange(inputMax,
                candidate -> candidate > 0L && fitsParallel(recipe, candidate));
        fitParallelValue = matchable;
        // ★ 2026-11-02【开批次读数】零成本地把「这个数是从哪一步来的」判出来：
        //   findHighestMatchable 的第一刀就是上界本身（命中即原样返回），第二刀是上界-1，
        //   之后才是二分 ⇒ 比较返回值与上界即可反推分支（不需要任何额外试算）。
        fitBranch = parallelBranchText(matchable, fitBound);
        fitSampleMatchable = matchable;
        return matchable;
    }

    /**
     * ★ 2026-11-02【开批次读数】把「并行值是从哪一步来的」翻成一句可读文本（纯函数、零成本）。
     *
     * <p>依据 {@link NanochipParallelFit#findHighestMatchable(long, LongPredicate)} 的三步结构
     * （先试 {@code requested} → 再试 {@code requested - 1} → 标准二分）：
     * <ul>
     *   <li>{@code 值 == 上界} ⇒ <b>第一刀命中</b> ⇒ 这个数<b>就是输入侧上限的原样值</b>（没经过二分）；</li>
     *   <li>{@code 值 == 上界 - 1} ⇒ <b>第二刀命中</b> ⇒ 上界那一档真的放不下（差一份）；</li>
     *   <li>其它 ⇒ <b>二分收敛</b> ⇒ 上界比真实可容纳量大，是二分把它收到这个数的。</li>
     * </ul>
     * 这三种成因对应完全不同的检修方向（见报告 §③）。
     */
    private static String parallelBranchText(long matchable, long bound) {
        if (matchable <= 0L) {
            return "二分=0（这一刻连 1 份都放不下）";
        }
        if (matchable >= bound) {
            return "第一刀命中（= 输入侧上限原样，未经过二分）";
        }
        if (bound - matchable == 1L) {
            return "第二刀命中（上界-1；上界那一档真的放不下）";
        }
        return "二分收敛（谓词判真的最大档）";
    }

    /**
     * ★ 2026-11-01【并行二分=0 · 抽样】把「这一档并行、这一侧放不下」记进本 tick 的抽样格。
     *
     * <p>为什么必须有它：原来的诊断只给出一个 {@code 并行二分=0}，而「0」有四种成因
     * （输入侧上限 ≤ 0 / 谓词在输入侧假 / 谓词在产出侧假 / 谓词抛异常），检修方向完全不同。
     * 本方法把<b>第一次</b>判假的那一档与那一侧留下来（后续同因不再覆盖）。
     *
     * <p><b>只读、无副作用</b>：只写本类自己的诊断字段，不参与任何判定、不改返回值。
     */
    private void recordProbe(long parallel) {
        if (fitSampleProbeParallel != NO_PROBE) {
            return;   // 只记第一次（后面的同因没有新信息）
        }
        fitSampleProbeParallel = parallel;
    }

    /**
     * 「把配方按 {@code parallel} 放大之后，<b>加上已在跑批次的预留</b>，真的放得下吗」。
     *
     * <p>三件事一次判完（{@link #overlayWithReservations} 把几份料表叠成一份临时配方，
     * 用 gtlcore 自己的匹配器判，<b>不自己写任何容量算术</b>）：
     * ① 本条的输入能不能扣出来；② 本条的产出放不放得下；③ 已在跑的批次（{@link BatchState#reservation}）也一起放得下。
     *
     * <p>★ 2026-11-01【并行二分=0 · 抽样】：判定本身一字未改，只是把<b>两侧分开判</b>并把
     * 「第一次判假的那一档 / 那一侧」记进 {@link #fitSampleProbeSide}（原实现写成
     * {@code a && b} 一条式子 ⇒ 日志里分不出是输入侧还是产出侧）。异常也从「直接冒出去」
     * 改成「记一行 + 判否」，与同包其它诊断路径同款（不新增任何 catch 分支的语义）。
     */
    private boolean fitsParallel(GTRecipe recipe, long parallel) {
        GTRecipe candidate;
        try {
            candidate = NanochipParallelFit.scalePrecisely(recipe, parallel);
        } catch (RuntimeException e) {
            fitSampleProbeSide = "精确缩放抛异常: " + e.getClass().getSimpleName();
            recordProbe(parallel);
            return false;
        }
        if (candidate == null) {
            fitSampleProbeSide = "精确缩放判 null";
            recordProbe(parallel);
            return false;
        }
        GTRecipe probe;
        try {
            probe = overlayWithReservations(candidate);
        } catch (RuntimeException e) {
            fitSampleProbeSide = "预留叠加抛异常: " + e.getClass().getSimpleName();
            recordProbe(parallel);
            return false;
        }
        if (probe == null) {
            fitSampleProbeSide = "预留叠加判 null";
            recordProbe(parallel);
            return false;
        }
        boolean inputFits;
        boolean outputFits;
        try {
            inputFits = RecipeRunnerHelper.matchRecipeInput(machine, probe);
            outputFits = inputFits && RecipeRunnerHelper.matchRecipeOutput(machine, probe);
        } catch (RuntimeException e) {
            fitSampleProbeSide = "容量匹配抛异常: " + e.getClass().getSimpleName();
            recordProbe(parallel);
            return false;
        }
        if (inputFits && outputFits) {
            return true;
        }
        // ★ 只有「第一次判假」值得记（二分会对很多档问同一个问题）
        if (inputFits) {
            fitSampleProbeSide = "产出侧放不下（matchRecipeOutput=false）";
        } else {
            fitSampleProbeSide = "输入侧放不下（matchRecipeInput=false）";
        }
        recordProbe(parallel);
        return false;
    }

    /**
     * 把「入参这一条」与「已在跑批次的预留」叠成一份<b>临时</b>配方（零副作用：只在副本上加内容）。
     *
     * <p><b>为什么必须自己记账</b>：出料口容量与 tick 输入<b>不会</b>因为「已经开过批次」而变小
     * （每次去看都还是满的）⇒ 不叠预留，k 条批次会各算一遍同一份额度（超发 ⇒ 产物被丢 / 批次等电）。
     * <b>有意的保守侧</b>：宁可少开一条，也不超发。
     *
     * <p>这份临时配方<b>只用来问「放不放得下」</b>，绝不入表、绝不扣料。
     */
    private GTRecipe overlayWithReservations(GTRecipe candidate) {
        GTRecipe probe = candidate.copy();
        for (int i = 0; i < batches.size(); i++) {
            BatchState state = batchStates.get(batches.get(i).recipe);
            if (state == null || state.reservation == null) {
                continue;   // 算不出预留的批次（保守）不参与叠加，只按它自己的配方判
            }
            // ★ 2026-11-02【准入预留口径 · 修复】非 tick 输入默认<b>不叠</b>：
            //   它在开批次那一刻就被 consumeInputs 真扣了 ⇒ 再叠一次等于要求机器「把已经在跑那条吃掉的料再拿一份」
            //   ⇒ 试算恒偏保守、库存刚好等于一条批次量时第二条永远开不出来（fan-out 失效）。
            //   完整理由与参考实现原话见 RESERVE_NON_TICK_INPUTS 的注释。
            if (RESERVE_NON_TICK_INPUTS) {
                appendContents(probe.inputs, state.reservation.inputs);
            }
            // tick 输入（EUt）与出料口容量这两样「每次去看都还是满的」必须叠，否则 k 条会各算一遍同一份额度
            appendContents(probe.outputs, state.reservation.outputs);
            appendContents(probe.tickInputs, state.reservation.tickInputs);
            appendContents(probe.tickOutputs, state.reservation.tickOutputs);
        }
        return probe;
    }

    /** 把 {@code source} 的内容并进 {@code target}（按能力分桶追加；{@code null} / 空一律保守跳过）。 */
    private static void appendContents(Map<RecipeCapability<?>, List<Content>> target,
                                       Map<RecipeCapability<?>, List<Content>> source) {
        if (target == null || source == null || source.isEmpty()) {
            return;
        }
        source.forEach((capability, contents) -> {
            if (capability != null && contents != null && !contents.isEmpty()) {
                target.computeIfAbsent(capability, ignored -> new ArrayList<>()).addAll(contents);
            }
        });
    }

    /**
     * <b>一条批次的「预留回执」</b>：它的输入表 + 产出表（产出先过
     * {@code IParallelLogic.getRecipeOutputChance} 结算概率，好让容量比较与实际出料同口径）。
     *
     * <p>结算失败（上游抛异常）时退回未结算的那一份 —— <b>偏大即保守</b>，不会超发。
     */
    private GTRecipe receiptOf(GTRecipe recipe) {
        if (recipe == null) {
            return null;
        }
        try {
            return IParallelLogic.getRecipeOutputChance(machine, recipe.copy());
        } catch (RuntimeException e) {
            NanochipModContext.logger().debug("[gtlmodular] NAC 独立线程：概率产出结算失败，预留按未结算记（偏保守）：{}",
                    e.toString());
            return recipe.copy();
        }
    }

    /**
     * <b>check 的最后一道闸</b>：把「本条 + 已在跑各条的预留」叠在一份临时配方上，问「真的放得下吗」。
     *
     * <p>与 {@link #fitsParallel} 的唯一区别：入参已经是<b>缩放好</b>的最终配方（不再缩放）。
     * 两条路都走同一份 {@link #overlayWithReservations} ⇒ 口径不会漂移。
     */
    private boolean canAdmit(GTRecipe scaled) {
        GTRecipe probe = overlayWithReservations(scaled);
        return probe != null
                && RecipeRunnerHelper.matchRecipeInput(machine, probe)
                && RecipeRunnerHelper.matchRecipeOutput(machine, probe);
    }

    /**
     * ★ <b>我们比参考实现多的一句（有意）</b>：保证这条批次的「运行份数」≥ 1。
     *
     * <p>依据：gtlcore 的 {@code RecipeRunner.handle} 在给概率产出「掷骰」时把
     * {@code IGTRecipe.getRealParallels()} 当<b>运行份数</b>用
     * （javap 现读 {@code _census/_javap_simple_reciperunner.txt:174}：
     * {@code LongChanceLogic.roll(contents, boostFn, euTier, chanceTier, cacheMap, getRealParallels(), cap)}），
     * 而本机旧逻辑那条路是<b>显式</b> {@code setRealParallels(p)} 的
     * （{@code _census/_javap_simple_multirecipes.txt:263}）。本类绕开了那一句
     * （{@link NanochipParallelFit#scalePrecisely} 只在 {@code parallel > 1} 时写它）
     * ⇒ 并行 1 的批次可能读到 0 ⇒ 概率产出整体消失。这里补一次。
     *
     * <p><b>幂等</b>：上游已经写过（≥1）时本方法什么也不做。
     */
    private static GTRecipe ensureRuns(GTRecipe scaled, long parallel) {
        if (scaled == null) {
            return null;
        }
        try {
            IGTRecipe extension = IGTRecipe.of(scaled);
            if (extension.getRealParallels() <= 0L) {
                extension.setRealParallels(Math.max(1L, parallel));
            }
        } catch (RuntimeException e) {
            NanochipModContext.logger().debug("[gtlmodular] NAC 独立线程：运行份数补写失败（不影响开批次）：{}", e.toString());
        }
        return scaled;
    }

    /**
     * 配方自带条件（维度 / 清洁室 / 研究…）—— 走配方自己的实例方法 {@code checkConditions(this)}。
     *
     * <p>取证：基类 {@code RecipeLogic.handleRecipeWorking} 的第一句就是它
     * （javap 现读 {@code _javap_simple_recipelogic.txt:397} 的 {@code invokevirtual}
     * {@code GTRecipe.checkConditions:(Lcom/.../RecipeLogic;)...}）；它是<b>跨包</b>调用 ⇒ 一定是 public。
     *
     * @param recordReason true = 把失败原因记进 {@link #waitReason}（推进阶段用）；
     *                     false = 只当闸用（开批次阶段用，不污染界面文案）
     */
    private boolean conditionsOk(GTRecipe recipe, boolean recordReason) {
        GTRecipe.ActionResult result;
        try {
            result = recipe.checkConditions(this);
        } catch (RuntimeException e) {
            NanochipModContext.logger().debug("[gtlmodular] NAC 独立线程：条件检查抛异常（按不满足处理）：{}", e.toString());
            return false;
        }
        if (result != null && result.isSuccess()) {
            return true;
        }
        if (recordReason && waitReason == null) {
            waitReason = reasonOf(result);
        }
        return false;
    }

    // ==========================================================================================
    // ★ 搬迁点 H1：主机预蓄电 + 无线电网可获取能力
    // ==========================================================================================

    /**
     * ★ <b>搬迁点 H1</b>：主机侧「先蓄够一整条配方的电再开工」的闸。
     *
     * <h3>为什么要搬</h3>
     * 这条件原来在 {@code NanochipAssemblyComplexMachine.NanochipRecipeLogic.setupRecipe} 的<b>最前面</b>。
     * 本类<b>从不</b>调 {@code setupRecipe} ⇒ 不搬的话「先蓄够再开工」直接失效，
     * 退回「跑到一半没电才停」。搬迁落点在 <b>check 阶段</b>（{@link #canAdmit} 之前），
     * 也就是「还没扣任何料」的那一侧 —— 与原来「在扣料前的一票否决」语义<b>完全等价</b>。
     *
     * <h3>判定口径（与机器侧那一份逐条同源，不是新写一套）</h3>
     * 三段短路，每段的「不确定」都倒向放行：
     * <ol>
     *   <li><b>按需现取能源 ⇒ 直接放行</b>：{@code isEnergySourceEffectivelyUnlimited()}
     *       （无线电网输入终端 / 创造能源仓，容量 ≥ {@code ON_DEMAND_POWER_FLOOR_EU} = Long.MAX/2）
     *       ⇒ 已存 0 也<b>不能</b>当成没电（这正是「无线电网可获取能力」那条线要的语义）；</li>
     *   <li><b>储能式 ⇒ 要求「已存 + 20 tick 内可补」覆盖整条配方</b>：
     *       {@code getObtainableEnergyEU() + getMaxInputPower() × 20 ≥ need}
     *       （20 = {@code ENERGY_DISTRIBUTE_INTERVAL}，能源仓一格 20 tick 补一次）；</li>
     *   <li><b>测不准 ⇒ 放行</b>：{@code need <= 0} / 读不到能源容器 / 不是本机 ⇒ 返回 {@code true}。</li>
     * </ol>
     * 本方法是<b>纯只读</b>：不扣电、不改状态、不写 NBT；被拦时只记下等待提示与原因
     * （由 {@link #serverTick} 末尾的 {@link #syncStatus} 统一节流显示，避免每 tick 惊动
     * {@code machine.onWaiting()}）。
     *
     * <p><b>诚实边界</b>：超容量长配方（整条电量 > 复合体蓄电上限且按需现取不成立）会一直等 ——
     * 这是原实现的已有取舍，本类照搬不放松。
     *
     * @return true = 可以开工（蓄够了 / 按需现取 / 测不准）；false = 拦住（本 tick 不开这一条）
     */
    private boolean hostPrecharged(GTRecipe scaled) {
        if (!NanochipAssemblyComplexMachine.NAC_PRECHARGE_WHOLE_RECIPE || scaled == null) {
            return true;   // 开关关掉 ⇒ 逐字回到「起跑不看电量」的旧行为
        }
        if (!(machine instanceof NanochipAssemblyComplexMachine nac)) {
            return true;   // 不是本机（理论上不会）⇒ 不参与判定，宁可开工
        }
        long need = plannedWholeRecipeEu(scaled);
        if (need <= 0L) {
            return true;   // 算不出 ⇒ 放行
        }
        if (nac.isEnergySourceEffectivelyUnlimited()) {
            return true;   // ① 按需现取（无线电网终端 / 创造能源仓）
        }
        long stored = Math.max(0L, nac.getObtainableEnergyEU());
        long supply = saturatingMul(Math.max(0L, nac.getMaxInputPower()),
                NanochipAssemblyComplexMachine.ENERGY_DISTRIBUTE_INTERVAL);
        long available = stored > Long.MAX_VALUE - supply ? Long.MAX_VALUE : stored + supply;
        if (available >= need) {
            return true;   // ② 储能式：够一整条配方
        }
        if (isSuspend()) {
            return true;   // ③ 被暂停不参与判定（不让提示盖住暂停）
        }
        admissionWaiting = true;
        waitReason = Component.translatable("gtlmodular.nanochip.host.buffer",
                FormattingUtil.formatNumbers(need), FormattingUtil.formatNumbers(stored));
        nac.noteHostPrechargeWaiting(need, stored);
        return false;      // 真没电 ⇒ 拦住（料没扣、批次没开、批次表没动）
    }

    /**
     * 本条最终配方<b>整条需要多少 EU</b> —— 与机器侧 {@code plannedRecipeTotalEu} 同一算式：
     * {@code max(1, RecipeHelper.getInputEUt(recipe)) × max(1, duration)}，走 long 饱和乘法。
     *
     * <p>★ <b>为什么在两处各写一份而不再抽一个共用类</b>：机器侧那一份带着一个
     * 「按配方对象身份 + duration」的本 tick 内缓存（它每 tick 都会被问一次）；本类只在
     * <b>开批次那一刻</b>问一次（不是每 tick），缓存反而多一层状态；算式只有两行，两处逐字同源更好核对。
     * ⚠️ <b>已知的重复</b>：若将来改这条算式，<b>两处都要改</b>。
     */
    private static long plannedWholeRecipeEu(GTRecipe recipe) {
        if (recipe == null) {
            return 0L;
        }
        long perTick = Math.max(1L, RecipeHelper.getInputEUt(recipe));
        int duration = Math.max(1, recipe.duration);
        return saturatingMul(perTick, (long) duration);
    }

    /** 饱和乘法（本类唯一一份；与机器侧 {@code saturatingMul} 同源同构，但那边是 private 取不到）。 */
    private static long saturatingMul(long a, long b) {
        if (a <= 0L || b <= 0L) {
            return 0L;
        }
        return a > Long.MAX_VALUE / b ? Long.MAX_VALUE : a * b;
    }

    // ==========================================================================================
    // 小工具：等级 / 去重 / 附注 / 原因文本
    // ==========================================================================================

    /**
     * 配方自己声明的电压等级（gtlcore 增强接口；取不到 ⇒ 返回 0 = <b>不因为「判不出」把配方挡掉</b>）。
     *
     * <p>为什么候选阶段要判它：本机超频封顶 2 次，一条远高于本档的配方即使材料够也不该被挂起
     * —— 否则它会占着槽位永远等电。这与旧逻辑 {@code checkRecipe} 里那条
     * {@code IGTRecipe.of(recipe).getEuTier() > getMachine().getTier()} 同口径。
     */
    private static int recipeEuTier(GTRecipe recipe) {
        try {
            return IGTRecipe.of(recipe).getEuTier();
        } catch (RuntimeException e) {
            return 0;
        }
    }

    /**
     * 本机电压等级。走公开接口 {@code ITieredMachine.getTier()}
     * （{@code WorkableElectricMultiblockMachine implements ITieredMachine} 并声明
     * {@code public int getTier()}，javap 现读 {@code _javap_simple_workablemb.txt:692/:1138}）；
     * 机器没实现该接口 ⇒ 返回 {@link Integer#MAX_VALUE}（= 等级这条闸整体放行，保守一侧）。
     */
    private int hostTier() {
        return machine instanceof ITieredMachine tiered ? tiered.getTier() : Integer.MAX_VALUE;
    }

    /** 这条配方是不是已经有别的批次在跑（判等口径见 {@link #isSameRecipe}）。 */
    private boolean isRunningElsewhere(GTRecipe candidate) {
        for (int i = 0; i < batches.size(); i++) {
            if (isSameRecipe(batches.get(i).recipe, candidate)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 两条配方算不算「同一种」：两边都有 {@code id} 就比 {@code id}，否则退化成引用相等。
     *
     * <p><b>不</b>用现成的 {@code GTRecipe.equals}：它只比 {@code id}，且对 {@code id == null} 会 NPE。
     */
    private static boolean isSameRecipe(GTRecipe a, GTRecipe b) {
        if (a == null || b == null) {
            return false;
        }
        ResourceLocation idA = a.getId();
        ResourceLocation idB = b.getId();
        if (idA != null && idB != null) {
            return idA.equals(idB);
        }
        return a == b;
    }

    /** 取（必要时新建）一条批次的附注 —— 按配方对象身份索引，读档恢复的批次也会在这里按需补一份。 */
    private BatchState stateOf(GTRecipe recipe) {
        BatchState state = batchStates.get(recipe);
        if (state == null) {
            state = new BatchState();
            batchStates.put(recipe, state);
        }
        return state;
    }

    /**
     * 这条批次<b>自己的</b>概率缓存（缺就现造一份）。
     *
     * <p>走基类的 {@code protected makeChanceCaches()}：它每次都新建一份
     * {@code IdentityHashMap} + 逐能力 {@code makeChanceCache()}
     * （javap 现读 {@code _javap_simple_recipelogic.txt:1185-1210}）⇒ 可以安全地一条批次一份。
     * 返回类型与 {@code GTRecipe.handleTickRecipeIO(IO, holder, Map)} 的入参<b>逐字同型</b>
     * （都是 {@code Map<RecipeCapability<?>, Object2IntMap<?>>}）⇒ 不需要任何强制转换。
     */
    private Map<RecipeCapability<?>, Object2IntMap<?>> cachesOf(GTRecipe recipe) {
        BatchState state = stateOf(recipe);
        if (state.caches == null) {
            state.caches = makeChanceCaches();
        }
        return state.caches;
    }

    /**
     * 从 {@code ActionResult} 里取出「为什么不行」的文本。
     *
     * <p>{@code reason()} 返回 {@code Supplier<Component>}（{@code GTRecipe$ActionResult} 是
     * 带分量 {@code isSuccess; reason; expectingRate} 的 record，javap 现读
     * {@code _probe_nacmat_evidence/classstr_gtceu-1.20.1-1.4.4.txt:18391-18395}），
     * 且 {@code Supplier} 本身与它给的组件都可能为 null（上游 {@code FAIL_NO_REASON} 那条就是）
     * ⇒ 两层都判；取不到就返回 null（界面退化成「等待」而不显示一个假原因）。
     */
    private static Component reasonOf(GTRecipe.ActionResult result) {
        if (result == null) {
            return null;
        }
        Supplier<Component> supplier = result.reason();
        return supplier == null ? null : supplier.get();
    }

    // ==========================================================================================
    // 展示 / 状态 / 诊断
    // ==========================================================================================

    /**
     * ④ <b>展示回写</b>：把「第 0 条在跑批次」的进度写进基类的同步字段
     * （{@code lastRecipe/progress/duration}）。
     *
     * <p>这三个字段在基类里是 {@code @Persisted/@DescSynced}
     * （javap 现读 {@code _javap_simple_recipelogic.txt:20/:28/:30}）⇒ 写它们等于免费拿到
     * Jade / TOP / 机器面板那套「当前配方 + 进度条」显示。多条批次同时跑时只反映其中<b>一条</b>
     * （有意的简化，已在类注释的「诚实边界」里写明）；整机明细由 {@link #appendThreadDisplayText} 走。
     *
     * <p>★ <b>不写 {@code isActive}</b>：基类那个字段是 {@code private}
     * （javap 现读 {@code _javap_simple_recipelogic.txt:16}），而 public getter {@code isActive()}
     * 是「{@code isWorking() || isWaiting() || (isSuspend() && isActive)}」自己算出来的
     * （同文件 {@code :902-919}）⇒ 只要状态机调对，Jade 看到的就是对的。
     */
    private void mirrorBatchZeroToBaseFields() {
        if (batches.isEmpty()) {
            progress = 0;
            duration = 0;
            return;
        }
        NanochipConcurrentBatches.Batch primary = batches.get(0);
        lastRecipe = primary.recipe;
        progress = primary.progress;
        duration = primary.duration;
    }

    /**
     * 状态机：这一 tick 有批次真的推进 = WORKING；有批次但在等（或被预蓄电闸拦住）= WAITING；
     * 一条批次都没有 = IDLE。
     *
     * <p>{@code setWaiting} 每次都会调 {@code machine.onWaiting()}
     * （javap 现读 {@code _javap_simple_recipelogic.txt:799-810}）⇒ 只按
     * {@link #WAIT_REASON_INTERVAL} 的节流重设原因文本，避免每 tick 都惊动机器侧回调。
     */
    private void syncStatus(boolean working, boolean waiting) {
        if (working) {
            waitReasonCooldown = 0;
            if (getStatus() != RecipeLogic.Status.WORKING) {
                setStatus(RecipeLogic.Status.WORKING);
            }
            return;
        }
        if (waiting) {
            if (waitReasonCooldown > 0 && getStatus() == RecipeLogic.Status.WAITING) {
                waitReasonCooldown--;
                return;
            }
            waitReasonCooldown = WAIT_REASON_INTERVAL;
            setWaiting(waitReason);
            return;
        }
        if (getStatus() != RecipeLogic.Status.IDLE) {
            setStatus(RecipeLogic.Status.IDLE);
        }
    }

    /**
     * 把批次状态追加成机器 UI 的文本行（<b>只允许在服务端调用</b>）。
     *
     * <p>两道闸：① 客户端直接收手 —— 批次表只在服务端有内容，读不到会显示成「在用 0」，比不显示更误导；
     * ② 上限 ≤ 1 的机器不显示（那种机器退化成 GTCEu 原版的单配方机器，多两行只是噪音）。
     *
     * <p>显示口径 = <b>一条批次占一行</b>（编号 + 进度/时长 + 本条自己那份并行），
     * 另加一行「同时处理 N 次配方运行（各批次之和）」；明细截到 {@link #DISPLAY_LINES} 行
     * （这段文本由服务端每 tick 求值后整表同步给客户端，全列就是每 tick 一个几百组件的包）。
     *
     * <p>★ 2026-11-02【多线程显示】：明细走 {@link #snapshot()}（= 与 Jade 同一份、同一顺序、同一数字口径）
     * ⇒ 进度降序、编号稳定、逐条并行之和恒等于「同时处理 R 次」；截断只砍<b>显示</b>，
     * 「在用 k」与「R」永远是<b>全部</b>批次数出来的。
     *
     * <p>文案刻意<b>不夸口</b>：第一个数是<b>上限</b>、第二个数是<b>数出来的在用条数</b>。
     */
    public void appendThreadDisplayText(List<Component> textList) {
        if (textList == null
                || machine.self().getLevel() == null
                || machine.self().getLevel().isClientSide) {
            return;
        }
        int limit = getThreadLimit();
        if (limit <= 1) {
            return;
        }
        int running = batches.size();
        textList.add(Component.translatable(LANG_THREADS, limit, running));
        if (running <= 0) {
            return;
        }
        textList.add(Component.translatable(LANG_THREADS_RUNS,
                FormattingUtil.formatNumbers(totalRunsAtOnce())));
        int shown = 0;
        for (Map.Entry<Integer, ThreadRec> entry : snapshot()) {
            if (shown >= DISPLAY_LINES) {
                break;
            }
            ThreadRec rec = entry.getValue();
            textList.add(Component.translatable(LANG_THREADS_LINE,
                    entry.getKey(), rec.progress(), rec.duration(),
                    FormattingUtil.formatNumbers(rec.parallels())));
            shown++;
        }
        if (shown < running) {
            // ★ 2026-11-02：两个占位符 —— 「还有几条没列」+「上方列了几条」（= 只列了进度最快的 N 条）
            textList.add(Component.translatable(LANG_THREADS_MORE, running - shown, shown));
        }
    }

    /** 这条批次自己那一份并行（gtlcore 增强接口；取不到退回 {@code recipe.parallels}）。 */
    private static long realParallelsOf(GTRecipe recipe) {
        if (recipe == null) {
            return 0L;
        }
        try {
            long real = IGTRecipe.of(recipe).getRealParallels();
            return real > 0L ? real : Math.max(1L, recipe.parallels);
        } catch (RuntimeException e) {
            return Math.max(1L, recipe.parallels);
        }
    }

    /** 「零件表不可用」只打一次（限频到一次进程一条），并给足检修方向。 */
    private void diagnoseUnavailableOnce() {
        if (diagnosedUnavailable) {
            return;
        }
        diagnosedUnavailable = true;
        NanochipModContext.logger().info("[gtlmodular] NAC 独立线程逻辑：零件表当前不可用（未成形 / 读档未就绪），"
                + "本 tick 不推进也不清理批次表（在跑 {} 条）—— 若持续出现，看结构成形状态。",
                batches.size());
    }

    /**
     * <b>批次诊断</b>：只在「在跑条数」<b>变化</b>时限频打一行（{@value #DIAG_INTERVAL} tick 起）。
     *
     * <p>为什么要有它：批次表不进同步字段，光看「机器在跑」分不出「开了几条」。
     * 日志里搜 {@code NAC 独立线程} 就能拿到「在用 k/上限 N + 样本配方 id 与各自进度」，
     * 这是「各自计时到底有没有真的生效」最直接的证据（跨类型时样本里会出现<b>不同配方表</b>的 id）。
     */
    private void diagnoseBatches() {
        if (diagCooldown > 0) {
            diagCooldown--;
            return;
        }
        int running = batches.size();
        if (running == diagLastRunning) {
            return;
        }
        diagLastRunning = running;
        diagCooldown = DIAG_INTERVAL;
        NanochipModContext.logger().info("[gtlmodular] NAC 独立线程：在用 {}/{}（上限来源=机器声明的跨配方线程数"
                        + " NanochipThreadHost.nanochipThreadCount() → 本机 getMultipleThreads()）；样本 [{}]；"
                        + "上次开出 [{}]；输入面 [{}]；首个被拒 [{}]；补料重试 [{}]；{}；空闲节流 [{}]；候选类型 [{}]",
                running, getThreadLimit(), sampleText(), lastOpenText(), inputFaceText(),
                firstReject == null ? "（本 tick 没被拒过）" : firstReject,
                fillThrottleText(), selfCostText(), idleThrottleText(), typesText());
    }

    /**
     * ★ 2026-11-02【开批次读数】最近一次成功开出的批次「并行是从哪一步来的」（纯只读、拼诊断文本）。
     *
     * <p>读法（三种成因对应三种检修方向）：
     * <ul>
     *   <li>{@code 来源=第一刀命中} ⇒ 并行 = 输入侧上限的<b>原样读数</b> ⇒ 「吃得少」是<b>输入面</b>报得少
     *       （样板仓参与匹配的槽/存量就只有这么多），与二分、输出容量、机器上限都无关；</li>
     *   <li>{@code 来源=二分收敛} ⇒ 上游读数（上界）比真实可容纳量大 ⇒ 卡在<b>容量</b>那一侧
     *       （看同行的「几何细读」里的 输入匹配 / 产出匹配 是哪一侧）；</li>
     *   <li>{@code 来源=输入侧上限 ≤ 0} ⇒ 连一份都凑不齐（这一刻真的没料/没接上线）。</li>
     * </ul>
     */
    private String lastOpenText() {
        if (lastOpenRecipeId == null) {
            return "（本机还没开出过批次）";
        }
        return lastOpenRecipeId
                + "；并行=" + lastOpenParallel
                + "；输入侧上限=" + lastOpenInputMax
                + "；搜索上界=" + lastOpenBound
                + "；来源=" + (lastOpenBranch == null ? "?" : lastOpenBranch);
    }

    /** ★ 2026-11-02【空闲限频】诊断文本：节流开关 / 是否被唤醒 / 粒度 / 累计跳过 tick 数（纯只读）。 */
    private String idleThrottleText() {
        if (!IDLE_SEARCH_THROTTLE) {
            return "关闭（每 tick 都全量搜）";
        }
        // ★ 0.3.132【显示即事实】fan-out 关掉之后，`FAN_OUT_MAX_PER_TICK` 那个上限**已不起作用**
        //   （每种配方每 tick 最多新开 1 条，见 findBatch 的 `!FAN_OUT_SAME_RECIPE` 分支）
        //   ⇒ 文案必须跟着开关走，不能还写着"每 tick 最多新开 N 条"（那会让人以为仍在 fan-out）。
        //   回退：`FAN_OUT_SAME_RECIPE` 改回 true ⇒ 这段自动恢复成 fan-out 口径。
        String fanOut = FAN_OUT_SAME_RECIPE
                ? "fan-out 每 tick 最多新开 " + FAN_OUT_MAX_PER_TICK + " 条"
                : "fan-out 已关：每种配方最多占 1 条批次";
        return (wokenByInputChange ? "本 tick 被唤醒⇒立即全量搜" : "生效")
                + "（每 " + Math.max(1, IDLE_SEARCH_INTERVAL) + " tick 至少全量搜一次；累计跳过 "
                + idleSkippedTicks + " tick；" + fanOut + "）";
    }

    /**
     * ★ 2026-11-01【候选为空 · 抓手】把「这一轮候选为什么开不出批次」限频打成<b>一行</b>。
     *
     * <p>为什么必须有它：{@link #diagnoseBatches()} 只能说明「在用 0/N」，<b>说不出为什么是 0</b>
     * —— 而「0」有三种完全不同的成因，检修方向完全相反：
     * <ol>
     *   <li><b>候选池为空</b>：本轮一条候选都没枚举出来 ⇒ 看「原始枚举」那个数
     *       （=0 ⇒ 搜索侧：配方表没搜到 / 配料面没喂进来）；
     *   <li><b>候选有、资格闸全拒</b>：「原始枚举 &gt; 0、通过资格闸 = 0」⇒ 看
     *       {@link #isCandidateUsable}（电压等级 / 机器侧校验）；
     *   <li><b>候选有、开批次时被某道闸拒</b>：「通过资格闸 &gt; 0、开出 = 0」且「首个被拒」
     *       会点名是哪一道闸（修改器 / 并行二分 / 条件 / 预蓄电 / 准入闸 / 扣料）。
     * </ol>
     * 另外，若某个类型的 lookup 抛异常，{@link #candidateRecipes()} 会把它收进
     * {@link #candidateSearchFailure} 打在同一行里（逐类型隔离 ⇒ 其余类型照常出候选）。
     *
     * <p><b>限频（三重，绝不刷屏）</b>：① 两次打印至少隔 {@value #CANDIDATE_DIAG_INTERVAL} tick；
     * ② 内容与上一行完全相同 ⇒ 不打；③ 整局最多 {@value #CANDIDATE_DIAG_BUDGET} 行。
     * 有批次在跑时直接跳过（说明候选这条路是通的）。纯只读：不参与任何判定。
     */
    private void diagnoseCandidates() {
        if (candidateDiagBudget <= 0) {
            return;
        }
        if (candidateDiagCooldown > 0) {
            candidateDiagCooldown--;
            return;
        }
        if (!batches.isEmpty()) {
            candidateDiagCooldown = CANDIDATE_DIAG_INTERVAL;   // 有批次在跑 ⇒ 候选这条路通，不需要这行
            return;
        }
        // ★ 2026-11-01【输入侧 · 决定性诊断】无论这一行最后打不打，本方法都按同一节拍跑：
        //   下面要读「输入侧实例数」（要遍历样板仓接线与各槽配方缓存）⇒ 必须有时间闸兜住，
        //   否则「内容与上一行相同」那一支会每 tick 都去读一次这些集合（限频就失效了）。
        candidateDiagCooldown = CANDIDATE_DIAG_INTERVAL;
        String line = "原始枚举=" + candidateRawCount + " 条；通过资格闸=" + candidateUsableCount
                + " 条；开出批次=0；累计开过=" + batchesOpenedTotal + " 条；上限=" + getThreadLimit()
                // ★ 2026-11-01【输入侧 · 决定性诊断】：输入侧实例数量（能力代理 / 样板仓接线数 / 已缓存配方数
                //   / 接线重建次数与余额）——「输入侧是空的」这件事从此有读数（见 inputSideText 的注释）。
                + "；输入侧[" + inputSideText() + "]"
                + (candidateSearchFailure == null ? "" : "；搜索失败[" + candidateSearchFailure + "]")
                + (firstReject == null ? "；首个被拒=（本 tick 没走到候选）" : "；首个被拒[" + firstReject + "]")
                // ★ 2026-11-01【并行二分=0 · 几何细读】只对「第一条被拒的候选」记一次（见 inspectFitFailure）
                + (fitSampleRecipeId == null ? "" : "；几何细读[" + fitDetailText() + "]");
        if (line.equals(candidateDiagLast)) {
            return;   // 与上一行一字不差 ⇒ 不打（稳态下整局只出现一行）
        }
        candidateDiagLast = line;
        candidateDiagCooldown = CANDIDATE_DIAG_INTERVAL;
        candidateDiagBudget--;
        NanochipModContext.logger().info("[gtlmodular] NAC 独立线程候选诊断：{}（候选类型 [{}]；本行限频 "
                + CANDIDATE_DIAG_INTERVAL + " tick、整局至多 " + CANDIDATE_DIAG_BUDGET + " 行）",
                line, typesText());
    }

    /**
     * ★ 2026-11-01【并行二分=0 · 几何细读】把 {@link #inspectFitFailure} 的读数拼成一段可读文本。
     *
     * <p>读法（三档，对应三种完全不同的故障）：
     * <ul>
     *   <li>{@code 输入侧上限 ≤ 0} ⇒ 机器在<b>输入侧</b>就判「一份都凑不齐」（料没进来 / 电压侧判 0）；</li>
     *   <li>{@code 输入侧上限} 是个<b>天文数字</b>（且 {@code 越界=是}）⇒ 原来是
     *       {@code Long.MAX_VALUE} 那一档：<b>这才是本轮修的那条路</b>（二分区间溢出）；</li>
     *   <li>{@code 二分=0} 但 {@code 输入侧上限} 是个正常正数、且 {@code 谓词首次判假@1/侧=输入侧放不下}
     *       ⇒ 真的<b>没有料</b>（或没匹配到槽位）；{@code 侧=产出侧放不下} ⇒ <b>出料口满了</b>。</li>
     * </ul>
     */
    private String fitDetailText() {
        StringBuilder sb = new StringBuilder(160);
        sb.append("配方=").append(fitSampleRecipeId);
        sb.append("；输入侧上限=").append(fitSampleInputMax);
        // ★ 0.3.132【按槽开批】上界的两个分量：上游首槽（改前唯一口径）/ 按槽最大（本轮新增）。
        //   读法（= 方案 §⑦-V3）：`上游首槽上限 < 按槽上限` 且 `输入侧上限 == 按槽上限`
        //   ⇒ 这一条就是「靠按槽读数才开出来的」。`按槽上限=-1` ⇒ 本次不参与（开关关着 / 没有可数输入 / 读不到）。
        sb.append("；上游首槽上限=").append(fitUpstreamInputMax)
                .append("；按槽上限=").append(fitSlotInputMax)
                .append("（开关=").append(PER_SLOT_PARALLEL_BOUND ? "开" : "关").append("）");
        sb.append("（越界=").append(fitSampleInputMaxOverBound ? "是" : "否")
                .append("，安全上界=").append(NanochipParallelFit.MAX_PARALLEL_SEARCH_BOUND).append("）");
        sb.append("；二分=").append(fitSampleMatchable);
        if (fitSampleProbeParallel != NO_PROBE) {
            sb.append("；谓词首次判假@").append(fitSampleProbeParallel)
                    .append("/侧=").append(fitSampleProbeSide == null ? "?" : fitSampleProbeSide);
        } else if (fitSampleProbeSide != null) {
            sb.append("；谓词没判假（说明没走到二分）/说明=").append(fitSampleProbeSide);
        }
        sb.append("；整条电量=").append(fitSampleWholeEu);
        sb.append("；已存=").append(fitSampleStoredEu);
        sb.append("；按需现取=").append(fitSampleUnlimitedEnergy ? "是" : "否");
        // ★ 2026-11-01【输入侧 · 决定性诊断】：按「原样配方」问一次的两侧结果 + 输入项数。
        //   读法：输入匹配=否 ⇒ 输入侧真的看不见这份配方（料/槽没接上）；
        //         输入匹配=是 且 产出匹配=否 ⇒ 出料口满了；两者皆「是」而二分=0 ⇒ 只能放大不上去。
        if (fitSampleProbed) {
            sb.append("；输入项数=").append(fitSampleInputContents);
            sb.append("；输入匹配=").append(fitSampleInputMatch ? "是" : "否");
            sb.append("；产出匹配=").append(fitSampleOutputMatch ? "是" : "否");
        }
        return sb.toString();
    }

    /**
     * 诊断样本：最多列 6 条在跑批次（<b>稳定编号</b> + 配方 id + 本条并行 + 进度%）。
     *
     * <p>★ 2026-11-02：编号与并行都改成与界面/Jade <b>同一口径</b>（{@link #threadLabelOf} /
     * {@link #realParallelsOf}），于是日志、面板、Jade 三处不可能出现「同一条批次三个数」。
     */
    private String sampleText() {
        StringBuilder sb = new StringBuilder();
        int shown = 0;
        // ★ 0.3.125 修正：batches 是批次容器（NanochipConcurrentBatches）本身，不是集合
        //   ⇒ 必须按下标访问 size()/get(i)（容器 API 与参考实现一致）。
        for (int i = 0, n = batches.size(); i < n; i++) {
            NanochipConcurrentBatches.Batch batch = batches.get(i);
            if (shown >= 6) {
                break;
            }
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append('#').append(threadLabelOf(batch.recipe)).append(' ')
                    .append(batch.recipe.getId() == null ? "?" : batch.recipe.getId())
                    .append(" ×").append(Math.max(1L, realParallelsOf(batch.recipe)))
                    .append(' ').append(Math.round(batch.percent() * 100.0F)).append('%');
            shown++;
        }
        return sb.toString();
    }

    /** 诊断用：本轮要搜的类型（取不到就是「（未知）」）。 */
    private String typesText() {
        if (host == null) {
            return "（无宿主）";
        }
        try {
            return NanochipCandidateSearch.describeTypes(host.nanochipSearchTypes());
        } catch (RuntimeException e) {
            return "（读取失败）";
        }
    }

    // ==========================================================================================
    // 存档（★ 追加键，老档安全回退）
    // ==========================================================================================

    /**
     * 存档：把批次表写进<b>追加键</b> {@link #TAG_THREADS}
     * （{@code "gtlmodular_nanochip_threads"}，逐条格式与理由见 {@link NanochipConcurrentBatches#save}）。
     *
     * <p><b>★ 向后兼容</b>：本键是<b>纯追加</b> ——
     * ① 老存档里没有它 ⇒ 读档侧读到空表 ⇒ 逐字回落到「没有待恢复批次」的行为（不报错、不吞料）；
     * ② 把总开关关掉（回到旧逻辑）后，这个键<b>仍然留在 NBT 里但没人读</b> ⇒ 不报错、不被旧逻辑误用，
     *    重新打开开关就又能恢复；
     * ③ 不新增任何 {@code @Persisted} 字段 ⇒ 不改字段表、不动 {@code MANAGED_FIELD_HOLDER}。
     *
     * <p>掉落物形态（{@code forDrop}）不存批次表：机器都被拆了，恢复它没有意义且会误导。
     */
    @Override
    public void saveCustomPersistedData(CompoundTag tag, boolean forDrop) {
        super.saveCustomPersistedData(tag, forDrop);
        if (forDrop) {
            return;
        }
        batches.save(tag);
    }

    /**
     * 读档：把批次表整体读回来（配方整条走 CODEC + <b>id 单独补回</b> + 进度）。
     *
     * <p>恢复是<b>即时</b>的（不像上一版那样攒一个 {@code pendingRestore} 等第一个 tick）：
     * 因为恢复只用 NBT 里的东西，<b>不需要</b>配方管理器、不需要结构就绪、也不需要重算并行
     * —— 这正是「整条配方进 CODEC」这个做法带来的简化。
     */
    @Override
    public void loadCustomPersistedData(CompoundTag tag) {
        super.loadCustomPersistedData(tag);
        batches.load(tag);
        batchStates.clear();   // 附注是运行期状态：读档后按需现造（概率缓存不跨存档，见类注释）
    }
}
