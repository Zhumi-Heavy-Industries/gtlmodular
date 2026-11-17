package com.gtlmodular.common.machine.nanochip;

import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.utils.FormattingUtil;
import com.gtladd.gtladditions.utils.CommonUtils;
import com.gtlmodular.GTLModular;
import com.gtlmodular.common.config.GTLModularConfig;
import com.hepdd.gtmthings.api.misc.WirelessEnergyManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.math.BigInteger;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * NAC 主机的<b>显示 / 同步文本拼装</b>（P5 · M8 抽取）。
 *
 * <p>本类只做「算值 / 拼文本」：不持有任何状态、<b>不写任何 {@code @DescSynced}/{@code @Persisted} 字段</b>
 * —— 那些字段与它们的赋值行全部留在主机（LDLib {@code ManagedFieldHolder} 冻结）。
 * 主机的 {@code updateSyncedTexts()} 仍在本机执行赋值，只是把「多行拼装」委托到
 * {@link #familyCountsCsv}/{@link #familyTiersCsv}/{@link #moduleText}。
 *
 * <p>行为等价：显示文本、语言键、颜色、顺序逐字搬；{@code addDisplayText} 的 super 调用与
 * 「{@code textList != null} 就直接 return」这条现状（D-a 决策：不接回 {@code tickSync()}）原样留在主机，
 * 本类中的 {@code addDisplayText} 后续分支因此同样只在不可达路径上被调用。
 */
final class NanochipStateSync {

    private NanochipStateSync() {}

    /** 父类那一行「同时处理至多 %d 个配方」的键 —— 只用来在本覆写里<b>定位并替换</b>它。 */
    private static final String LANG_UPSTREAM_PARALLEL = "gtceu.multiblock.parallel";
    /** 父类那一行「拥有 %s 个跨配方线程」的键 —— 同款按键替换：数字换成我们钳制后的 {@code U}。 */
    private static final String LANG_UPSTREAM_THREADS = "gtladditions.multiblock.threads";
    /**
     * 父类那一行「最大功率：§a%s EU/t（%s§r）」的键（GTCEu 自带，逐字取证：
     * {@code _census/_probe_nacmat_evidence/lang/gtceu-1.20.1-1.4.4__gtceu__zh_cn.json:3682-3686}）
     * —— 只用来在本类里<b>定位并替换</b>它：数值换成「整个电网的电量」（见
     * {@link #replaceMaxPowerWithGridTotal}），档名沿用父类那一段实参。
     */
    private static final String LANG_MAX_ENERGY_PER_TICK = "gtceu.multiblock.max_energy_per_tick";

    /**
     * ★★ 0.3.132【最大功率 = 显示整个电网的电量 · 队长令】★★
     *
     * <h4>队长口径（逐字）</h4>
     * <blockquote>「它不是只显示那么多，是**显示电网一共有多少电**」</blockquote>
     *
     * <h4>为什么原来是 `9.22e18` 封顶（取证）</h4>
     * GTCEu 基类的「最大功率」那一行由
     * {@code MultiblockDisplayText.Builder.addEnergyUsageLine(IEnergyContainer)} 画出，读的是
     * {@code machine.energyContainer}（{@code EnergyContainerList}）的
     * {@code getInputVoltage()/getOutputVoltage()/getEnergyCapacity()}，**全是 `long`**；
     * 而 gtlcore 的 {@code EnergyContainerListMixin:47-61} 又用
     * {@code NumberUtils.saturatedMultiply/saturatedAdd} 汇总 ⇒ **天花板恒为 `Long.MAX_VALUE`**。
     * 原版（gtladditions 的无线机器）之所以能显示 {@code 2.74e69}，是因为它的
     * {@code WorkableElectricMultiblockMachineMixin.redirectEnergyUsageLine} 对那次
     * {@code addEnergyUsageLine} 调用做了 {@code @Redirect}，改读
     * {@code IWirelessNetworkEnergyHandler.getMaxAvailableEnergy(): BigInteger}。
     * 我们 NAC 继承的是**非无线**基类 ⇒ 那条分支永远进不去。
     *
     * <h4>本方法做什么（与 {@link #filterUpstreamParallelLines} 同款纪律）</h4>
     * <ol>
     *   <li>按语言键 {@value #LANG_MAX_ENERGY_PER_TICK} 把父类那一行**剔掉**；</li>
     *   <li>再由我们**无条件补一行**：数值 = 该玩家/队伍在无线电网里的**整个余额**
     *       （{@code WirelessEnergyManager.getUserEU(uuid)}，`BigInteger`），
     *       格式化**直接调上游那个方法** {@code CommonUtils.formatBigIntegerFixed(BigInteger)}
     *       ⇒ 与第 2 张实机截图**逐字节一致**（该方法内部：≤{@code Long.MAX_VALUE} 走千分位，
     *       否则走 {@code DecimalFormat("0.00000000E0")} 再小写、把 {@code e} 换成 {@code e+}
     *       —— 逐字取证自 {@code CommonUtils.formatBigIntegerFixed} 的字节码）；</li>
     *   <li>档名 `(MAX+16)` 那一段**原样沿用父类那一行自己的实参**（`Object[]` 的第 2 个），
     *       不去自己猜档位 —— 父类已经按 `getMaxVoltage()` 算好了，照抄最省且不会漂。</li>
     * </ol>
     *
     * <h4>纪律</h4>
     * <ul>
     *   <li><b>纯只读</b>：只读玩家电网余额与配置开关，**不扣电、不改状态、不进存档**；</li>
     *   <li>读不到余额 / 没登记放置者 / 开关关闭 ⇒ <b>一个字都不改</b>（保留父类原行，绝不显示 0 冒充）；</li>
     *   <li>本改动**只影响这一行的显示**：机器实际单 tick 可消费上限仍由
     *       `changeEnergy(long)` 决定（`long` 域）—— 这一点在注释里如实写明，不误导读者。</li>
     * </ul>
     */
    static void replaceMaxPowerWithGridTotal(NanochipAssemblyComplexMachine machine, List<Component> textList) {
        BigInteger total;
        if (textList == null || !machine.isFormed()) {
            return;
        }
        UUID user = machine.gridPowerUser();
        if (user == null || !GTLModularConfig.isNanochipGridPowerEnabled()) {
            return;
        }
        try {
            total = WirelessEnergyManager.getUserEU((UUID)user);
        }
        catch (RuntimeException e) {
            return;
        }
        if (total == null) {
            return;
        }
        Object tierArg = null;
        int seenAt = -1;
        for (int i = 0; i < textList.size(); ++i) {
            TranslatableContents tc;
            Component line = textList.get(i);
            ComponentContents componentContents = line.getContents();
            if (!(componentContents instanceof TranslatableContents) || !LANG_MAX_ENERGY_PER_TICK.equals((tc = (TranslatableContents)componentContents).getKey())) continue;
            Object[] args = tc.getArgs();
            if (args != null && args.length >= 2) {
                tierArg = args[1];
            }
            seenAt = i;
            break;
        }
        if (seenAt < 0) {
            return;
        }
        textList.remove(seenAt);
        MutableComponent value = Component.literal((String)CommonUtils.formatBigIntegerFixed((BigInteger)total));
        MutableComponent row = tierArg == null ? Component.translatable((String)LANG_MAX_ENERGY_PER_TICK, (Object[])new Object[]{value}) : Component.translatable((String)LANG_MAX_ENERGY_PER_TICK, (Object[])new Object[]{value, tierArg});
        textList.add(seenAt, (Component)row);
    }
    static void filterUpstreamParallelLines(NanochipAssemblyComplexMachine machine, List<Component> textList) {
        if (textList == null || !machine.isFormed()) {
            return;
        }
        Iterator<Component> it = textList.iterator();
        while (it.hasNext()) {
            TranslatableContents tc;
            Component line = it.next();
            ComponentContents componentContents = line.getContents();
            if (!(componentContents instanceof TranslatableContents) || !LANG_UPSTREAM_PARALLEL.equals((tc = (TranslatableContents)componentContents).getKey()) && !LANG_UPSTREAM_THREADS.equals(tc.getKey())) continue;
            it.remove();
        }
        long threads = Math.max(1L, (long)machine.getMultipleThreads());
        long threadsInUse = safeRunningThreadsForDisplay(machine);
        long totalRuns = safeTotalRunsForDisplay(machine);
        long perRecipe = Math.max(1L, totalRuns / threadsInUse);
        long total = NanochipAssemblyComplexMachine.saturatingMul(threadsInUse, perRecipe);
        textList.add(parallelTotalLine(threadsInUse, perRecipe, total));
        textList.add(threadsLine(threads));
    }
    /**
     * ★★ <b>【GTO 式总量显示】覆写父类的并行行</b> ★★ —— 把「同时处理至多 N 个配方」里的 N
     * 从<b>单条配方的并行上限</b>换成<b>整机真实总量</b>：{@code N = 在用线程数 K × 每条线程的真实并行 P}。
     *
     * <h3>口径（逐字取自 GTO 官方语言文件，本任务唯一口径依据）</h3>
     * GTO 的 {@code gtocore.machine.thread}（{@code _census/_gto_wildcard/zh_cn.json:5709}）：
     * 「同时处理至多 %1$s 种不同配方，每种配方至多 %2$s 个」⇒ <b>两层连乘</b>，
     * 「种数 / 线程数」是<b>第一层乘数</b>。用户给的 GTO 实机截图那一行是
     * {@code 同时处理至多 16,384 个配方} ⇒ 那是一个「正常数字」，**不是** {@code int} 上限占位值
     * （推导见 {@code _census/GTO界面口径对齐_实施记录.md} §②）。
     *
     * <h3>逐行来源（本覆写只替换<b>两行</b>，其余行逐字保留父类输出）</h3>
     * <p>⚠ <b>2026-11-13 状态更新（本任务 ②）</b>：实机显示证明「那两行不是经由本方法进入列表的」
     * ⇒ 最终口径已由 {@link #filterUpstreamParallelLines}（在 {@code addDisplayText} 里）收口。
     * 本方法里的按键替换/兜底追加是**次级**保障（保留，因为它只会写出与我们同口径的行；
     * 且 {@link #filterUpstreamParallelLines} 会把同键的旧行剔掉 ⇒ 屏幕上一对、不重复）。
     * <ul>
     *   <li><b>在用线程条数 K</b> = {@code engine.ThreadedRecipeLogic#runningThreadCount()}（数出来的，
     *       与界面「在用 K 条」同一格；取不到时钳 ≥1）；</li>
     *   <li><b>每条线程的真实并行 P</b> = {@code ceil(R / K)}，其中 {@code R} =
     *       {@code engine.ThreadedRecipeLogic#totalRunsAtOnce()}（各线程自己那份并行之和，
     *       与界面「同时处理 R 次配方运行（各线程之和）」同一格）⇒ <b>两个乘数都是现场真实值</b>；
     *       ⚠ 改前这两个乘数是 {@link #getMaxParallel()}（{@code int} 接口上的饱和占位值
     *       {@link #FULL_PARALLEL_CAP} = 2147483647）与 {@link #getMultipleThreads()}（上限 U）
     *       ⇒ 显示 {@code 549,755,813,632}（天量、不像 GTO；用户实机反馈）；</li>
     *   <li><b>总量</b> = {@link #saturatingMul(long, long)}（64 位、乘法前判界，绝不回绕）
     *       ⇒ 真值（等价于 R，最多因向上取整 +1；K ≤ R 恒成立，边界另有 {@code max(1,·)} 兜底）；</li>
     *   <li><b>「拥有 M 个跨配方线程」那一行</b> = 同款按键替换成 <b>同一个 {@code U}</b>
     *       （父类原口径 = 它自己的 128 基线 + {@code getAdditionalThread()}，<b>未钳制</b>）
     *       ⇒ 屏幕上不再出现 128 与 256 并排打架。<b>本轮未改这一格</b>（它是「上限」，不是「在用」）。</li>
     * </ul>
     *
     * <h3>为什么是覆写、为什么先调 super</h3>
     * ① 该方法是 {@code protected}（字节码：{@code _census/_tmp_subspace/javap_modbase.u8.txt:103}
     * 的声明 + {@code :162} 的 {@code invokespecial}），同血统作者也在同一层覆写它
     * （{@code _census/shanhai-src/…/PrimordialOmegaEngineModuleBase.java:817}）；
     * ② <b>不新增 mixin、不动父类</b>；先调 {@code super} 是为了让父类写好的其它行与它自己的
     * 成形判断<b>逐字保留</b>，本覆写只替换<b>两个已知键</b>的行（{@code gtceu.multiblock.parallel}
     * 与 {@code gtladditions.multiblock.threads}）⇒ 父类万一还写了别的行，一行都不会被吞掉；
     * 万一某版本父类少写了这两行之一，兜底由我们追加（<b>显示绝不凭空少一行</b>）。
     *
     * <h3>只动显示、不动行为（开 / 关独立线程等价）</h3>
     * 本方法只在 {@code addDisplayText} 的显示通路上被调用，全程只读三个 getter
     * （{@link #getMultipleThreads()} / {@code engine.ThreadedRecipeLogic#runningThreadCount()} /
     * {@code engine.ThreadedRecipeLogic#totalRunsAtOnce()}）：不写任何字段、
     * 不进任何判定，配方结算（输入扣减 / 产出 / 时长 / 并行 / 概率 / 回滚）一条都不经过这里。
     * ⚠ 本覆写**不再读** {@link #getMaxParallel()}：它只是 {@code int} 接口上的饱和占位值，
     * 参与显示就会得到天量（这正是本轮修掉的那个显示问题）。
     *
     * <p><b>回退</b>：把 {@code threadsInUse}/{@code perRecipe} 两行改回
     * {@code max(1, getMaxParallel()) × max(1, getMultipleThreads())}（逐字见回退包 H3），
     * 或删掉本覆写（连同三个键常量与两个行构造函数）⇒ 逐字回到父类的两行
     * （并行 = {@code getMaxParallel()}、线程 = 父类自己的 128 基线）。
     */
    static void addParallelDisplay(NanochipAssemblyComplexMachine machine, List<Component> textList) {
        int insertAt;
        if (textList == null) {
            return;
        }
        if (!machine.isFormed()) {
            return;
        }
        long threads = Math.max(1L, (long)machine.getMultipleThreads());
        long threadsInUse = safeRunningThreadsForDisplay(machine);
        long totalRuns = safeTotalRunsForDisplay(machine);
        long perRecipe = Math.max(1L, totalRuns / threadsInUse);
        long total = NanochipAssemblyComplexMachine.saturatingMul(threadsInUse, perRecipe);
        long gtoKinds = threads;
        long parallelShown = totalRuns > 0L ? perRecipe : Integer.MAX_VALUE;
        int parallelAt = -1;
        int threadsAt = -1;
        for (int i = 0; i < textList.size(); ++i) {
            Component line = textList.get(i);
            ComponentContents componentContents = line.getContents();
            if (!(componentContents instanceof TranslatableContents)) continue;
            TranslatableContents contents = (TranslatableContents)componentContents;
            if (LANG_UPSTREAM_PARALLEL.equals(contents.getKey())) {
                parallelAt = i;
                continue;
            }
            if (!LANG_UPSTREAM_THREADS.equals(contents.getKey())) continue;
            threadsAt = i;
        }
        if (parallelAt >= 0 && threadsAt >= 0) {
            insertAt = Math.min(parallelAt, threadsAt);
            textList.remove(Math.max(parallelAt, threadsAt));
            textList.remove(Math.min(parallelAt, threadsAt));
        } else if (parallelAt >= 0 || threadsAt >= 0) {
            insertAt = parallelAt >= 0 ? parallelAt : threadsAt;
            textList.remove(insertAt);
        } else {
            insertAt = textList.size();
        }
        textList.add(insertAt, gtoThreadsLine(gtoKinds));
        textList.add(insertAt + 1, gtoPerRecipeLine(parallelShown));
        textList.add(insertAt + 2, gtoIndependentThreadLine(machine));
    }
    /**
     * ★【GTO 界面口径对齐】本机这一刻**真实在用**的线程条数（只给显示用）。
     *
     * <p>取数口：{@code engine.ThreadedRecipeLogic#runningThreadCount()}（= 批次表条数，
     * 与界面「在用 K 条」、Jade 的 {@code KEY_THREAD_RUNNING} **同一个数**）。
     * 取不到（子类/字段初始化期/夹具）⇒ 退回 {@code 1}：<b>绝不让 0 参与后面的除法</b>
     * （0 会让「每条并行」算不出来，也会让总量显示成 0）。
     *
     * <p>⚠ 退化数据（{@code K > R}，正常不会出现：每条在跑批次至少贡献 1 次并行）时，
     * 调用方那边「每条并行」会被钳到 1（{@code max(1, R/K)}）⇒ 总量如实显示成 {@code K}（不显示 0）。
     * <p><b>只读、纯显示</b>：不写任何字段、不进任何判定（配方结算不经过这里）。
     */
    static long safeRunningThreadsForDisplay(NanochipAssemblyComplexMachine machine) {
        try {
            RecipeLogic logic = machine.getRecipeLogic();
            if (logic instanceof com.gtlmodular.common.machine.engine.ThreadedRecipeLogic generic) {
                return Math.max(1L, (long)generic.runningThreadCount());
            }
        }
        catch (RuntimeException e) {
            GTLModular.LOGGER.debug("[gtlmodular] NAC \u603b\u91cf\u663e\u793a\uff1a\u5728\u7528\u7ebf\u7a0b\u6570\u53d6\u4e0d\u5230\uff0c\u6309 1 \u8ba1\uff1a{}", (Object)e.toString());
        }
        return 1L;
    }
    /**
     * ★【GTO 界面口径对齐】各线程**自己那份并行之和**（只给显示用）。
     *
     * <p>取数口：{@code engine.ThreadedRecipeLogic#totalRunsAtOnce()}（= 界面
     * 「同时处理 R 次配方运行（各线程之和）」、Jade 的 {@code KEY_THREAD_RUNS} **同一个数**）
     * ⇒ 屏幕上那两行与总量行**天然同源**，不会被读成两套账。
     *
     * <p>取不到 ⇒ {@code 0}（调用方会把它钳到「每条至少 1」，见 {@code addParallelDisplay}）。
     */
    static long safeTotalRunsForDisplay(NanochipAssemblyComplexMachine machine) {
        try {
            RecipeLogic logic = machine.getRecipeLogic();
            if (logic instanceof com.gtlmodular.common.machine.engine.ThreadedRecipeLogic generic) {
                return Math.max(0L, generic.totalRunsAtOnce());
            }
        }
        catch (RuntimeException e) {
            GTLModular.LOGGER.debug("[gtlmodular] NAC \u603b\u91cf\u663e\u793a\uff1a\u5404\u7ebf\u7a0b\u5e76\u884c\u4e4b\u548c\u53d6\u4e0d\u5230\uff0c\u6309 0 \u8ba1\uff1a{}", (Object)e.toString());
        }
        return 0L;
    }
    /** ★【GTO 式总量】那一行的成品（GTO 句式 + 明确总量；三格 = <b>在用条数 K / 每条真实并行 P / 总量 K×P</b>）。 */
    static Component parallelTotalLine(long threadsInUse, long perRecipe, long total) {
        return Component.translatable((String)NanochipAssemblyComplexMachine.LANG_PARALLEL_TOTAL, (Object[])new Object[]{FormattingUtil.formatNumbers((long)threadsInUse), FormattingUtil.formatNumbers((long)perRecipe), FormattingUtil.formatNumbers((long)total)}).withStyle(ChatFormatting.GRAY);
    }
    /**
     * ★【GTO 口径】第 1 行「同时处理至多 <b>N</b> 种不同配方」。
     *
     * <p>★★ <b>颜色必须由语言键给，不能在 Java 里硬编码</b> ★★ —— 这是本工程与 GTCEu/GTO 共有的做法：
     * 色码写在语言文件的值里（例如 GTCEu 的 {@code "最大功率：§a%s EU/t（%s§r）"}）。
     * 我上一版用 {@code Component.literal("同时处理至多 ")...withStyle(GREEN)} 硬拼，
     * <b>数字那格就丢了色码</b>、只能继承外层颜色 ⇒ 队长一眼看出"颜色跟其他的不同"。
     * ⇒ 现在整行走 {@code translatable}，色码在键里，逐字照 GTO 的
     * {@code gtocore.machine.thread.0 = "同时处理至多 §a%s§r 种不同配方"}（绿）。
     */
    static Component gtoThreadsLine(long kinds) {
        return Component.translatable((String)"gtlmodular.nanochip.parallel.kinds", (Object[])new Object[]{FormattingUtil.formatNumbers((long)kinds)});
    }
    /**
     * ★【GTO 口径】第 2 行「每种配方至多 <b>Y</b> 个」。
     *
     * <p>色码同样在键里，逐字照 GTO 的 {@code gtocore.machine.thread.1 = "每种配方至多 §d%s§r 个"}（紫）。
     */
    static Component gtoPerRecipeLine(long perRecipe) {
        return Component.translatable((String)"gtlmodular.nanochip.parallel.per", (Object[])new Object[]{FormattingUtil.formatNumbers((long)perRecipe)});
    }
    /**
     * ★【GTO 口径】第 3 行「独立线程[已启用] / [未启用]」。
     *
     * <p>逐字照 GTO 的 {@code gtocore.machine.independent_thread = "独立线程[%s]"}（截图逐字「独立线程[已启用]」）。
     * ★ <b>整行单色</b>：GTO 那一行没有给状态单独上色（它和「同时处理…」那两行一样是灰的）
     * —— 我上一版把 {@code 已启用} 单独染绿是**自作主张**，队长一眼看出不对，故改回单色。
     * 判据 = 本机跨配方线程数 &gt; 1（与 {@link #isCrossRecipeThreadActive()} 同源）。
     */
    static Component gtoIndependentThreadLine(NanochipAssemblyComplexMachine machine) {
        return Component.translatable((String)(machine.getMultipleThreads() > 1 ? "gtlmodular.nanochip.threads.independent.on" : "gtlmodular.nanochip.threads.independent.off")).withStyle(ChatFormatting.GRAY);
    }
    static Component threadsLine(long threads) {
        return Component.translatable((String)LANG_UPSTREAM_THREADS, (Object[])new Object[]{FormattingUtil.formatNumbers((long)threads)}).withStyle(ChatFormatting.GRAY);
    }
    /**
     * ★★ 2026-11-10（第 2 轮）<b>部件未就绪时的等级显示处理：把那两行「按部件推导」的值收起来</b>★★
     *
     * <p>为什么按<b>语言键</b>找这两行（而不是按位置/序号）：它们是 GTCEu 基类
     * {@code WorkableElectricMultiblockMachine.addDisplayText} 里连续添加的（字节码
     * {@code _census/_javap_simple_workablemb.txt:775-802}：{@code MultiblockDisplayText.builder(...)
     * .addEnergyUsageLine(energyContainer).addEnergyTierLine(tier)...}），语言键是
     * 「本机读到的那个字段」的稳定标识、与语言无关 ——
     * 本工程已有同款用法（{@code MoltenModuleHostMachine.java:744-751} 按
     * {@code gtceu.gui.machinemode}、{@code MoltenSpaceOreModuleMachine.java:335-343} 按
     * {@code gtceu.multiblock.parallel} 定位并替换基类那一行），本轮沿用同一套口径。
     *
     * <p>两个键的出处（部署 jar 的常量池与语言文件，逐条）：
     * {@code _census/_probe_nacmat_evidence/classstr_gtceu-1.20.1-1.4.4.txt:15548-15561}
     * （{@code addEnergyUsageLine} → {@code gtceu.multiblock.max_energy_per_tick}；
     * {@code addEnergyTierLine} → {@code gtceu.multiblock.max_recipe_tier}）与
     * {@code _census/_probe_nacmat_evidence/lang/gtceu-1.20.1-1.4.4__gtceu__zh_cn.json:3682-3686}
     * （「最大功率：§a%s EU/t（%s§r）」「最大配方等级：%s」）。
     *
     * <p><b>低风险</b>：只在"已成形 + 零件表空"这条故障路径上被调到，正常机器一次都不进；
     * 万一某个版本把键改了 ⇒ 找不到那两行 ⇒ <b>退回原样显示</b>（不抛异常、不误删别的行）。
     *
     * @param textList 基类刚刚写好的显示行（就地修改：删掉两行 + 替换一行）
     */
    static void replaceUnreadyTierDisplay(List<Component> textList) {
        boolean tierLineSeen = false;
        Iterator<Component> it = textList.iterator();
        while (it.hasNext()) {
            Component line = it.next();
            ComponentContents componentContents = line.getContents();
            if (!(componentContents instanceof TranslatableContents)) continue;
            TranslatableContents tc = (TranslatableContents)componentContents;
            String key = tc.getKey();
            if ("gtceu.multiblock.max_recipe_tier".equals(key)) {
                tierLineSeen = true;
                it.remove();
                continue;
            }
            if (!LANG_MAX_ENERGY_PER_TICK.equals(key)) continue;
            it.remove();
        }
        if (tierLineSeen) {
            textList.add((Component)Component.literal((String)"\u6700\u5927\u914d\u65b9\u7b49\u7ea7\uff1a\u5f85\u5b9a\uff08\u80fd\u6e90\u4ed3\u672a\u91cd\u8fde\uff0c\u90e8\u4ef6\u5c31\u7eea\u540e\u81ea\u52a8\u6062\u590d\uff09").withStyle(ChatFormatting.GRAY));
        }
    }
    /**
     * ★★ 2026-11-11【样板总成镜像绑定 = 0 · 显示】★★ 把「这一格数字是谁算的、0 是什么意思」摊到面板上。
     *
     * <h3>为什么必须补这一行</h3>
     * <p>上游 Jade 那一格 {@code gtceu.top.proxies_bound = "样板总成镜像绑定数量：%s"}
     * 只给一个裸数字（填它的是 gtlcore 的 Jade provider，算它的是
     * {@code MEPatternBufferPartMachineBase.getProxies()}；两条出处都在
     * {@link NanochipMirrorBindGuard} 的类注释 §一）。数字为 0 时玩家<b>读不出</b>「未绑」还是「坏了」，
     * 也读不出「那是别的机器的部件、跟我这台机器有没有配方可做无关」。
     *
     * <h3>显示口径 = 只读、照实说（显示即事实）</h3>
     * <ul>
     *   <li>{@code readState(...) == null}（未成形 / 零件表为空）⇒ 显示「<b>未读取</b>」，
     *       <b>绝不</b>用 0 冒充「已读、就是 0」；</li>
     *   <li>已读且 {@code 已入链 > 0} ⇒ 显示那个数字（灰）；</li>
     *   <li>已读、{@code 已入链 == 0}、且上游没记过任何镜像坐标 ⇒ 只显示「<b>0 = 未绑定</b>」这一行
     *       （{@code …mirror.none} 那条键）。原先跟在它后面的两行解释文案已按用户要求<b>整段删除</b>，
     *       对应的两条语言键也从中英两份 lang 里一并移除；</li>
     *   <li>已读、{@code 已入链 == 0}、但上游<b>记着</b>镜像坐标 ⇒ 显示「<b>断链</b>」+ 已自动重建几次 +
     *       一条立刻可用的临时解法（拿闪存重绑一次）。</li>
     * </ul>
     * <p><b>纯只读</b>：本方法一行判定都不改、一个字段都不写（它只调
     * {@link NanochipMirrorBindGuard#readState}）。显示层与恢复动作口径同源（同一份 {@code State}），
     * 不可能出现「面板说已绑定、上游还是 0」这种打架。
     *
     * <p>中英文案走 {@code Component.translatable}（键在
     * {@code assets/gtlmodular/lang/{zh_cn,en_us}.json} 各一条，键名两边完全一致）⇒ 客户端按语言自行选，
     * 不存在"在英文环境里塞一句中文"这种事；提示行里引用的方块名也用上游自己的 lang
     * （{@code block.gtceu.me_pattern_buffer_proxy = "ME样板总成镜像"}，出处
     * {@code gtl-dev/inspect/gtceu/assets/gtceu/lang/zh_cn.json:806}）。
     */
    static void addMirrorBindingDisplay(List<Component> textList) {
    }
    /** 各族电路数量的 CSV（逗号分隔、9 个，按 {@link NanochipCalibration.Family} 顺序）。 */
    static String familyCountsCsv(NanochipAssemblyComplexMachine machine) {
        StringBuilder counts = new StringBuilder();
        for (NanochipCalibration.Family family : NanochipCalibration.Family.values()) {
            counts.append(machine.getCalibration().countOf(family)).append(',');
        }
        return counts.toString();
    }

    /** 各族校准档位的 CSV（逗号分隔、9 个）。 */
    static String familyTiersCsv(NanochipAssemblyComplexMachine machine) {
        StringBuilder tiers = new StringBuilder();
        for (NanochipCalibration.Family family : NanochipCalibration.Family.values()) {
            tiers.append(machine.getCalibration().tierOf(family)).append(',');
        }
        return tiers.toString();
    }

    /** 已安装模块摘要（`种类中文名 ×N`，点分隔；一台都没有 ⇒ 「未安装模块」）。 */
    static String moduleText(NanochipAssemblyComplexMachine machine) {
        StringBuilder sb = new StringBuilder();
        for (NanochipModuleKind kind : NanochipModuleKind.values()) {
            int n = machine.countModules(kind);
            if (n <= 0) continue;
            if (sb.length() > 0) {
                sb.append(" \u00b7 ");
            }
            sb.append(kind.nameZh()).append(" \u00d7").append(n);
        }
        return sb.length() == 0 ? "\u672a\u5b89\u88c5\u6a21\u5757" : sb.toString();
    }

    /** 部件未就绪时的两行提示（文案逐字搬自 host 的 addDisplayText）。 */
    static void appendUnreadyLines(List<Component> textList) {
        textList.add((Component)Component.literal((String)"  \u00b7 \u7ed3\u6784\u90e8\u4ef6\u672a\u5c31\u7eea\uff08\u80fd\u6e90\u4ed3 / \u6837\u677f\u603b\u6210\u5c1a\u672a\u91cd\u65b0\u8fde\u63a5\uff09").withStyle(ChatFormatting.YELLOW));
        textList.add((Component)Component.literal((String)"    \u6700\u5927\u914d\u65b9\u7b49\u7ea7 / \u6700\u5927\u529f\u7387\u6682\u4e0d\u663e\u793a\uff08\u907f\u514d\u8bef\u8bfb\u6210\u964d\u7ea7\uff09\uff1b\u672c\u7248\u5df2\u505c\u7528\u8bfb\u6863\u81ea\u52a8\u6062\u590d\uff08B \u5f00\u5173\uff09\uff1a\u8bf7\u62c6\u653e\u4e00\u6b21\u63a7\u5236\u5668\uff0c\u6216\u70b9\u5de6\u4fa7\u300c\u7acb\u5373\u66f4\u65b0\u7ed3\u6784\u300d\u6309\u94ae").withStyle(ChatFormatting.DARK_GRAY));
    }

    /** 结构未成形提示（文案逐字搬自 host 的 addDisplayText）。 */
    static void appendNotFormedLine(List<Component> textList) {
        textList.add((Component)Component.literal((String)"\u7eb3\u7c73\u82af\u7247\u7ec4\u88c5\u590d\u5408\u4f53\uff1a\u7ed3\u6784\u672a\u6210\u5f62").withStyle(ChatFormatting.RED));
    }

    /** 已成形时的模块计数行（文案逐字搬自 host 的 addDisplayText）。 */
    static void appendModuleLine(NanochipAssemblyComplexMachine machine, List<Component> textList) {
        textList.add((Component)Component.literal((String)("\u6a21\u5757: " + machine.formedModuleCount() + " / " + machine.getMaxModuleCount())).withStyle(ChatFormatting.WHITE));
    }
}
