package com.gtlmodular.common.machine.engine;

import com.gregtechceu.gtceu.api.capability.IParallelHatch;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.common.data.GTRecipeModifiers;
import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import net.minecraft.resources.ResourceLocation;
import org.gtlcore.gtlcore.api.recipe.RecipeRunnerHelper;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * 通用独立线程引擎 · <b>找批次</b>。
 *
 * <p>形状对齐参考实现（gtl_extend 的 {@code MultipleRecipesLogic} / {@code TimeSpaceBreakerMultipleRecipesLogic}）：
 * 「找配方」是一段可以被单独看懂、单独核对的算法，入口 {@link #findBatch()} 只做编排，
 * 单表搜索 {@link #findBatchForType} 与并行额度换算各自成段。
 *
 * <h2>一条合格批次要过五关</h2>
 * <ol>
 *   <li>候选来源：ME 样板仓缓存优先；没有缓存才退回整张配方表（分片轮扫，
 *       每次最多 {@link #SCAN_BUDGET} 条）；</li>
 *   <li>原配方先做一次只读输入匹配（不够就不必再 copy/超频/精确并行）；</li>
 *   <li>机器配方修改器（{@code fullModifyRecipe}，去重/门槛）；</li>
 *   <li>并行 = 线程倍率 × 并行仓，再走 {@code accurateParallel}；</li>
 *   <li>精确并行后的输入、每 tick 输入、配方条件三重匹配。</li>
 * </ol>
 *
 * <h2>行为约束（重构时逐条保留）</h2>
 * <ul>
 *   <li>每张表各有游标与冷却（跨表轮转时后面的表不能被第一张表的冷却挡住）；</li>
 *   <li>扫描冷却<b>不覆盖</b>最后一次真实拒绝原因（诊断靠它）；</li>
 *   <li>「跨表轮转起点」与旧实现一样<b>每 tick 归零</b>（旧实现每 tick {@code new HostImpl()}）。</li>
 * </ul>
 */
public final class BatchFinder {

    /** 没有 ME 样板缓存时，一次 findBatch 最多试多少条配方。 */
    static final int SCAN_BUDGET = 24;

    /** 整轮没找到配方后的冷却 tick 数（期间 findBatch 直接返回 null）。 */
    static final int IDLE_COOLDOWN_TICKS = 10;

    /**
     * ★ 2026-11-25：<b>每 tick 最多新开几条批次</b>。
     *
     * <p>熔岩模块那份引擎一直有这个限速（{@code ENGINE_MAX_STARTS_PER_TICK = 8}），通用引擎没有
     * ⇒ 256 槽的机器首轮填槽会一次扫出几百条候选，把一整个 tick 吃掉。合并两套引擎时按较严的
     * 一边统一：**首轮填槽最多 8 条/tick**，空出来的槽下一个 tick 立刻补（引擎每 tick 都会调
     * {@link #findBatch()} 直到返回 null，槽位填满只是慢几 tick，吞吐不变）。
     */
    static final int MAX_STARTS_PER_TICK = 8;

    private final IRecipeLogicMachine machine;
    private final IThreadedRecipeMachine threaded;
    private final IBatchLogicOwner owner;
    private final ConcurrentRecipeEngine engine;
    private final BatchDiagnostics diag;

    /** 本 tick 已经新开出几条（{@link #beginTick()} 归零）。 */
    private int startsThisTick;

    /**
     * 每张配方表各自的扫描游标与冷却。
     *
     * <p>旧版是全局单份 {@code scanCursor/scanCooldownUntil} —— 单表时没问题，但跨表轮转时
     * <b>第一张表一轮扫完就把整机冷却 10 tick</b>，后面的表在 {@code now < cooldownUntil}
     * 分支直接返回、<b>根本没被扫到</b>（日志里「跨表=true」却只有第一张表的拒绝原因）。
     * 每表一份后，一轮 findBatch 里每张表都能真正扫到自己的分片。
     */
    private static final class TableScanState {
        int cursor;
        long cooldownUntil;
        /**
         * ★ 2026-11-08：<b>连续「整轮扫完也没找到配方」的次数</b>，用于空转退避。
         *
         * <p>实测（spark 30s 采样，0.3.346）：空闲机器每次整轮扫描要 6~7ms，而扫描间隔只有
         * 10 tick（0.5s）⇒ 单台机器平均就要 0.6~0.9ms/tick，正好对上 gtlcore 性能监控器
         * 显示的「空闲 925µs」。连续扫不到东西时按 10→20→40→80 tick 退避，把空闲成本降到
         * 1/8；任何真实唤醒（AE 推样板 / 重新挂上 tick / 扣料成功）都会
         * {@link BatchFinder#clearCooldowns()} 立即清零，不会拖慢首件。
         */
        int idlePasses;
    }

    /**
     * ★ 2026-11-26：<b>一轮 findBatch 的只读快照</b>（GTO 的 {@code CrossRecipeTrait} 在结构扫描时
     * 就把线程仓/并行仓/无线能源仓的引用收进 trait 里，不逐候选重新遍历部件表）。
     *
     * <p>这里不做跨 tick 缓存，只在<b>一次 findBatch 调用内</b>把同 tick 不会变的值算一次：
     * 部件表、线程仓两个开关、在跑批次的并行占用与配方 id。扫描途中引擎不会收新批次
     * （{@link ConcurrentRecipeEngine#tick} 是先扫完再扣料入表），所以语义与逐候选现算完全一致；
     * 收益是把「候选数 × 部件数」的重复遍历压成「1 × 部件数」。
     */
    private static final class ScanContext {
        final boolean repeatedAllowed;
        final boolean independent;
        final int parallelPerThread;
        final int usedParallel;
        final ObjectSet<ResourceLocation> runningIds;

        ScanContext(boolean repeatedAllowed, boolean independent, int parallelPerThread,
                    int usedParallel, ObjectSet<ResourceLocation> runningIds) {
            this.repeatedAllowed = repeatedAllowed;
            this.independent = independent;
            this.parallelPerThread = parallelPerThread;
            this.usedParallel = usedParallel;
            this.runningIds = runningIds;
        }
    }

    private final Map<GTRecipeType, TableScanState> scanStates = new IdentityHashMap<>();

    /** 上次成功开出配方的配方表下标（跨表轮转的起点）。 */
    private int lastSuccessfulTypeIdx;

    public BatchFinder(IRecipeLogicMachine machine, IThreadedRecipeMachine threaded,
                       IBatchLogicOwner owner, ConcurrentRecipeEngine engine, BatchDiagnostics diag) {
        this.machine = machine;
        this.threaded = threaded;
        this.owner = owner;
        this.engine = engine;
        this.diag = diag;
    }

    /**
     * 每个 server tick（{@code engine.tick} 之前）调一次：跨表轮转起点归零。
     *
     * <p>旧实现的 {@code HostImpl} 是每 tick {@code new} 一次，字段天然归零；现在
     * {@link BatchFinder} 常驻，所以显式补这一句，保证「每 tick 都从第一张表开始找」的旧语义。
     */
    public void beginTick() {
        lastSuccessfulTypeIdx = 0;
        startsThisTick = 0;
    }

    /** 清掉所有表的扫描冷却（{@code pokeScan} / 扣料成功后调用）。 */
    public void clearCooldowns() {
        for (TableScanState s : scanStates.values()) {
            s.cooldownUntil = 0;
            s.idlePasses = 0;
        }
    }

    /** 结构失形 / 读档：本世界的扫描游标与冷却全部作废。 */
    public void reset() {
        scanStates.clear();
        lastSuccessfulTypeIdx = 0;
    }

    /**
     * ★ 2026-11-08：空转退避冷却 —— 连续整轮扫不到配方时 10 → 20 → 40 → 80 tick 递增。
     *
     * <p>代价：真正「材料刚到、但既没有 AE 推样板、也没有重新订阅」的极端情况下，
     * 最多多等 4 秒才再扫一次；有真实唤醒（{@link #clearCooldowns()}）时立刻清零。
     */
    private static long idleCooldown(TableScanState state) {
        state.idlePasses = Math.min(4, state.idlePasses + 1);
        return (long) IDLE_COOLDOWN_TICKS << (state.idlePasses - 1);
    }

    /**
     * 找一条能开的新配方（<b>此时还没扣料</b>）。
     *
     * <p>跨表机器从上次成功的表开始轮转（<b>每 tick 归零</b>，见 {@link #beginTick()}），
     * 第一张能开的配方就返回；单表机器只扫当前选中表。
     */
    public GTRecipe findBatch() {
        // ★ 2026-11-25：每 tick 新开批次的上限（见 MAX_STARTS_PER_TICK）。到顶就交还控制权，
        //   剩下的空槽下一个 tick 继续填 —— 引擎每 tick 都会重试到 null，吞吐不受影响。
        if (startsThisTick >= MAX_STARTS_PER_TICK) {
            return null;
        }
        GTRecipeType current = machine.getRecipeType();
        if (current == null) {
            return null;
        }
        GTRecipeType[] types = machine.getRecipeTypes();
        // ★ 2026-11-07 GTO 口径：线程仓的「独立线程」= 每条线程独立拉满功率，
        //   不再管「用哪张配方表」。装仓后只要机器有 2 张以上表，就自动全部扫（公平轮转）。
        // ★ 2026-11-25：是否跨表由逻辑壳决定（owner.crossTableScan()）—— 熔岩模块按
        //   engineCrossTable() 开关走，通用引擎恒 true，两边判定口径由此统一到一处。
        boolean allTypes = owner.crossTableScan() && types != null && types.length > 1;
        ScanContext ctx = newScanContext();
        if (!allTypes) {
            return findBatchForType(current, ctx);
        }
        int n = types.length;
        int startIdx = lastSuccessfulTypeIdx % n;
        int candidatesSum = 0;
        List<String> perTable = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            int idx = (startIdx + i) % n;
            diag.forgetScannedThisCall();
            GTRecipe recipe = findBatchForType(types[idx], ctx);
            if (recipe != null) {
                lastSuccessfulTypeIdx = idx;
                return recipe;
            }
            if (diag.scannedThisCall()) {
                candidatesSum += diag.candidates();
                // 每张表扫完就把它自己的「原因 + 配方 + 缺什么」定稿，
                // 否则下一张表会把上一张的拒绝原因覆盖掉（旧版日志只剩最后一张表）。
                perTable.add(String.valueOf(types[idx].registryName) + " → " + diag.detail());
                diag.clearRecipeContext();
            }
        }
        if (!perTable.isEmpty()) {
            diag.setCandidates(candidatesSum);
            diag.reject("全部配方表都无可用配方（共 " + n + " 张）｜" + String.join("；", perTable), null, null);
        }
        // 每张表都在冷却 / 都被跳过时：diagReject 保持上一次的真实原因，不覆盖、也不嵌套。
        return null;
    }

    /**
     * 生成本轮扫描快照：线程仓开关、并行仓并行数、在跑批次的占用与配方 id 各读一次。
     */
    private ScanContext newScanContext() {
        boolean repeated = threaded.threadHatchRepeatedRecipes();
        boolean independent = threaded.threadHatchIndependent();
        long mult = Math.max(1, threaded.parallelMultiplier());
        if (threaded.usesParallelHatch()) {
            mult *= Math.max(1, parallelHatchCount());
        }
        int perThread = (int) Math.min(Integer.MAX_VALUE, mult);
        ObjectSet<ResourceLocation> runningIds = null;
        if (!repeated) {
            runningIds = new ObjectOpenHashSet<>();
            for (int i = 0; i < engine.size(); i++) {
                GTRecipe recipe = engine.get(i).recipe;
                if (recipe != null && recipe.getId() != null) {
                    runningIds.add(recipe.getId());
                }
            }
        }
        return new ScanContext(repeated, independent, perThread,
                independent ? 0 : runningParallelSum(), runningIds);
    }

    /** 单张配方表的候选搜索。 */
    private GTRecipe findBatchForType(GTRecipeType type, ScanContext ctx) {
        long now = machine.self().getOffsetTimer();
        TableScanState state = scanStates.computeIfAbsent(type, t -> new TableScanState());
        if (now < state.cooldownUntil) {
            // 冷却期间**不覆盖** diagReject —— 保留上一次真实原因，
            // 否则日志每 100 tick 只会打「扫描冷却中」，永远看不到为什么没配方。
            return null;
        }
        diag.startTableScan(type);

        // ① ME 样板仓（猪咪样板总成）已解码的配方：只含机器上样板对应的几条，
        //    直接拿来当候选，不再每次扫整张表。
        List<GTRecipe> candidates = owner.meCachedCandidates(type);
        boolean fromMeCache = !candidates.isEmpty();
        int start = 0;
        int limit;
        if (fromMeCache) {
            limit = candidates.size();
        } else {
            // ② ME 样板缓存为空时的回落判断：**按「真的有没有东西」判断，而不是「有没有挂 ME 仓」**。
            //
            //   ★ 2026-11-08（玩家问：「输入仓没东西为什么还扫」）：旧判断用 hasAnyItemOrFluidInput()，
            //   它对挂了 ME 样板仓的机器恒 true（AE 推来的料不在通用 handler 里），于是
            //   「没样板 + 没料」的空机器仍然每轮去扫整张配方表（实测 44 条候选 × 全量输入匹配
            //   ≈ 6~7ms/轮），这就是空闲延迟的来源。这里改用 hasRealItemOrFluidInput()：
            //   仓里真的没东西 ⇒ 直接跳过整表扫描（不扣料、不改状态，只省掉必然失败的匹配）。
            //   来料/推样板仍会由「内容变化 → 重新订阅」或 pokeScan 叫醒，不会漏单。
            //
            //   ★★ 2026-11-25（队长：「高维等离子聚变核心只能挂猪咪样板总成才能工作」）：
            //   **上面这条 11-08 的收紧会误杀 ME 样板仓驱动的一切机器**，必须补第二道豁免。
            //   取证：gtlcore 的 ME 样板仓把 AE 推来的料放在自己的槽里，它的 handler
            //   （gtlcore `IMERecipeHandler`）继承 `IFilteredHandler`，**不是** GTCEu 的
            //   `IRecipeHandler` ⇒ 不进 capabilitiesProxy、没有 getContents() ⇒
            //   hasRealItemOrFluidInput() 恒 false（哪怕料已经推进槽里）。
            //   ⇒ 冷槽（还没配上过配方的样板）阶段：ME 缓存空 + 看不到料 = 跳过整表扫描 = 永久 IDLE。
            //   而猪咪两台总成有「直供配方」（setSlotCacheRecipe）先把冷槽热起来，正好绕过这道闸
            //   ⇒ 表象就是「只有猪咪能用」。这里补上「样板仓里真有样板 ⇒ 也扫」，
            //   任何家的样板总成（gtlcore 原生 / 统合 / gtladditions 超级）都吃得下。
            //   成本：只在 ME 缓存为空 + 看不到料时才问一次，且受上面 state.cooldownUntil 退避限流。
            //   ★ 2026-11-25 合并 refactor/unify-batch-engine 后：owner 的静态类型变成 IBatchLogicOwner，
            //     这条判据直接问 BatchSupport（按宿主机器探测，两个逻辑壳共用同一份实现）。
            if (!owner.hasRealItemOrFluidInput() && !BatchSupport.hasWaitingMePattern(machine)) {
                diag.reject("无实际物品/流体输入（ME 样板缓存为空、样板仓里也没有样板，跳过整表扫描）｜"
                        + BatchSupport.describeInputCaps(machine), null, null);
                state.cooldownUntil = now + idleCooldown(state);
                return null;
            }
            // 没有 ME 样板仓（或缓存为空）才退回整表；分片扫描，避免大表每 tick 全扫。
            candidates = type.getLookup().getLookup().getRecipes(true)
                    .filter(r -> !r.isFuel()).toList();
            int total = candidates.size();
            if (total == 0) {
                diag.reject("候选=0（配方表为空）", null, null);
                return null;
            }
            start = state.cursor % total;
            limit = Math.min(SCAN_BUDGET, total);
        }

        for (int i = 0; i < limit; i++) {
            int idx = fromMeCache ? i : (start + i) % candidates.size();
            GTRecipe raw = candidates.get(idx);
            diag.addCandidate();
            // ★ 2026-11-07 GTO 口径：「并行重复配方」关 ⇒ 同一配方只许占一条线程。
            //   放到引擎里做，机器类不用再各写一份 fullModifyRecipe 去重。
            if (!ctx.repeatedAllowed && raw != null && raw.getId() != null
                    && ctx.runningIds != null && ctx.runningIds.contains(raw.getId())) {
                diag.noteScanReject(type, raw, "并行重复配方关闭：该配方已在别的线程", null);
                continue;
            }
            // 先用「原配方」做一次只读匹配：物品/流体不够就不必再 copy/超频/精确并行
            // （并行只会放大输入，原配方都不够 ⇒ 放大后一定不够）。
            if (!RecipeRunnerHelper.matchRecipeInput(machine, raw)) {
                diag.noteScanReject(type, raw, "原料不足(含ME样板仓)", null);
                continue;
            }
            // ★ 2026-11-25：机器自己的准入门槛（层级 / 配方条件 / EBF 温度…）。
            //   熔岩模块过去在它自己的扫描里判这几条，现在统一挂到这里，引擎只问一句。
            if (!owner.acceptsCandidate(raw)) {
                diag.noteScanReject(type, raw, "机器准入门槛拒绝（层级/条件/温度）", null);
                continue;
            }
            // ★ 2026-11-25：自备折算分支（熔岩模块的 gtlcore 口径）。
            //   引擎只负责「挑候选 + 限速 + 记账」，并行算法与耗电模型由逻辑壳自己决定。
            if (!owner.usesStandardParallel()) {
                GTRecipe prepared;
                try {
                    prepared = owner.prepareBatch(raw);
                } catch (RuntimeException e) {
                    // 单条候选的折算异常不能掀翻服务端主线程：跳过这条，继续扫下一条。
                    diag.noteScanReject(type, raw, "自备折算异常：" + e, null);
                    continue;
                }
                if (prepared == null) {
                    diag.noteScanReject(type, raw, "自备折算返回 null", null);
                    continue;
                }
                return acceptBatch(raw, prepared, fromMeCache, state, candidates, idx);
            }
            GTRecipe modified = owner.modifyRecipe(raw);
            if (modified == null) {
                diag.noteScanReject(type, raw, "配方修改器返回 null（去重/门槛拒绝）", null);
                continue;
            }
            // 普通多方块 = 线程倍率 × 并行仓；巨构（usesParallelHatch=false）只看线程倍率。
            // perThread 由 newScanContext() 在整轮扫描开头算一次（long 乘再钳到 int）。
            // ★ 2026-11-07 GTO 口径「独立线程」：
            //   开 = 每条线程各自一份完整并行/功率额度（perThread）；
            //   关 = 所有线程共用同一份额度（在跑批次的并行之和不得超过 perThread）。
            //   usedParallel / runningIds 同样取自本轮快照：扫描途中引擎不会收新批次。
            int parallelBudget = ctx.parallelPerThread;
            if (!ctx.independent) {
                parallelBudget = ctx.parallelPerThread - ctx.usedParallel;
                if (parallelBudget <= 0) {
                    diag.noteScanReject(type, raw, "独立线程关闭：并行额度已被在跑线程占满", null);
                    continue;
                }
            }
            Pair<GTRecipe, Integer> par = GTRecipeModifiers.accurateParallel(
                    machine.self(), modified, parallelBudget, false);
            GTRecipe scaled = par.getFirst();
            if (scaled == null) {
                diag.noteScanReject(type, raw, "并行计算返回 null", null);
                continue;
            }
            // 只判输入（GTCEu 原生 RecipeLogic 也是「输入够就开跑」，输出空间在收尾时处理，
            // 否则没放输出仓的机器会永远开不了批次）。
            if (!RecipeRunnerHelper.matchRecipeInput(machine, scaled)) {
                diag.noteScanReject(type, scaled, "物品/流体不匹配(含ME样板仓)", null);
                continue;
            }
            GTRecipe.ActionResult tickResult = scaled.matchTickRecipe(machine);
            if (!tickResult.isSuccess()) {
                diag.noteScanReject(type, scaled, "每tick输入不足(EU等)", BatchSupport.reasonText(tickResult));
                continue;
            }
            GTRecipe.ActionResult conditionResult = scaled.checkConditions(owner.asRecipeLogic());
            if (!conditionResult.isSuccess()) {
                diag.noteScanReject(type, scaled, "配方条件不满足", BatchSupport.reasonText(conditionResult));
                continue;
            }
            return acceptBatch(raw, scaled, fromMeCache, state, candidates, idx);
        }

        // 本轮没有结果：ME 缓存/整表都冷却一会儿，别在空转时每 tick 重复扫。
        boolean fullPass;
        if (fromMeCache) {
            state.cooldownUntil = now + idleCooldown(state);
            fullPass = true;      // ME 样板缓存本身每次都全扫
        } else {
            int total = candidates.size();
            if (start + limit >= total) {
                state.cooldownUntil = now + idleCooldown(state);
                state.cursor = 0;
                fullPass = true;  // 分片轮扫已经覆盖完整张表
            } else {
                state.cursor = (start + limit) % total;
                fullPass = false;
            }
        }
        if (diag.candidates() == 0) {
            diag.reject("候选=0（查表没命中：物品/流体/电路是否齐全）", null, null);
        }
        if (fullPass) {
            // 整张表覆盖完仍一条没跑起来 ⇒ 逐条原因一次性打出来（每表每次世界加载只一次）
            diag.dumpScanDetail(type);
        }
        return null;
    }

    /**
     * ★ 2026-11-25：两条折算分支（标准链 / 自备链）共用的「收下这条候选」收尾。
     *
     * <p>清冷却、推进本表游标、记一次开批额度、写诊断。两条分支过去各写一遍，
     * 合并后只留这一处 —— 游标推进或限速口径要改时只改这里。
     *
     * @param raw      原始候选（保留给后续诊断扩展，当前只作上下文）
     * @param selected 折算完成、可以直接进批次表的配方
     */
    private GTRecipe acceptBatch(GTRecipe raw, GTRecipe selected, boolean fromMeCache,
                                 TableScanState state, List<GTRecipe> candidates, int idx) {
        diag.reject("有配方可跑", raw, null);
        state.cooldownUntil = 0;
        state.idlePasses = 0;
        if (!fromMeCache && candidates != null && !candidates.isEmpty()) {
            state.cursor = (idx + 1) % candidates.size();
        }
        startsThisTick++;
        return selected;
    }

    /** 当前在跑批次占用的并行之和（「独立线程」关闭时用来算剩余共享额度）。 */
    private int runningParallelSum() {
        long sum = 0L;
        for (int i = 0; i < engine.size(); i++) {
            sum += engine.get(i).use;   // 开批时钉死的占用量，不再回读可能被改过的 recipe
            if (sum >= Integer.MAX_VALUE) {
                return Integer.MAX_VALUE;
            }
        }
        return (int) sum;
    }

    /**
     * 并行仓并行数：<b>已成形多方块</b>的全部部件里，取最大的
     * {@link IParallelHatch#getCurrentParallel()}；没有并行仓（或未成形／不是多方块）就 1。
     */
    int parallelHatchCount() {
        if (machine.self() instanceof IMultiController controller && controller.isFormed()) {
            int max = 1;
            List<IMultiPart> parts = controller.getParts();
            if (parts != null) {
                for (IMultiPart part : parts) {
                    if (part instanceof IParallelHatch hatch) {
                        max = Math.max(max, hatch.getCurrentParallel());
                    }
                }
            }
            return max;
        }
        return 1;
    }
}
