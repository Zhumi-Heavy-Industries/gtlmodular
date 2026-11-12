package com.gtlmodular.common.machine.engine;

import com.gregtechceu.gtceu.api.capability.IParallelHatch;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.common.data.GTRecipeModifiers;
import com.mojang.datafixers.util.Pair;
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
final class BatchFinder {

    /** 没有 ME 样板缓存时，一次 findBatch 最多试多少条配方。 */
    static final int SCAN_BUDGET = 24;

    /** 整轮没找到配方后的冷却 tick 数（期间 findBatch 直接返回 null）。 */
    static final int IDLE_COOLDOWN_TICKS = 10;

    private final IRecipeLogicMachine machine;
    private final IThreadedRecipeMachine threaded;
    private final ThreadedRecipeLogic owner;
    private final ConcurrentRecipeEngine engine;
    private final BatchDiagnostics diag;

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

    private final Map<GTRecipeType, TableScanState> scanStates = new IdentityHashMap<>();

    /** 上次成功开出配方的配方表下标（跨表轮转的起点）。 */
    private int lastSuccessfulTypeIdx;

    BatchFinder(IRecipeLogicMachine machine, IThreadedRecipeMachine threaded,
                ThreadedRecipeLogic owner, ConcurrentRecipeEngine engine, BatchDiagnostics diag) {
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
    void beginTick() {
        lastSuccessfulTypeIdx = 0;
    }

    /** 清掉所有表的扫描冷却（{@code pokeScan} / 扣料成功后调用）。 */
    void clearCooldowns() {
        for (TableScanState s : scanStates.values()) {
            s.cooldownUntil = 0;
            s.idlePasses = 0;
        }
    }

    /** 结构失形 / 读档：本世界的扫描游标与冷却全部作废。 */
    void reset() {
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
    GTRecipe findBatch() {
        GTRecipeType current = machine.getRecipeType();
        if (current == null) {
            return null;
        }
        GTRecipeType[] types = machine.getRecipeTypes();
        // ★ 2026-11-07 GTO 口径：线程仓的「独立线程」= 每条线程独立拉满功率，
        //   不再管「用哪张配方表」。装仓后只要机器有 2 张以上表，就自动全部扫（公平轮转）。
        boolean allTypes = types != null && types.length > 1;
        if (!allTypes) {
            return findBatchForType(current);
        }
        int n = types.length;
        int startIdx = lastSuccessfulTypeIdx % n;
        int candidatesSum = 0;
        List<String> perTable = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            int idx = (startIdx + i) % n;
            diag.forgetScannedThisCall();
            GTRecipe recipe = findBatchForType(types[idx]);
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

    /** 单张配方表的候选搜索。 */
    private GTRecipe findBatchForType(GTRecipeType type) {
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
            if (!owner.hasRealItemOrFluidInput()) {
                diag.reject("无实际物品/流体输入（ME 样板缓存为空，跳过整表扫描）｜"
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
            if (!threaded.threadHatchRepeatedRecipes() && isRecipeRunningElsewhere(raw)) {
                diag.noteScanReject(type, raw, "并行重复配方关闭：该配方已在别的线程", null);
                continue;
            }
            // 先用「原配方」做一次只读匹配：物品/流体不够就不必再 copy/超频/精确并行
            // （并行只会放大输入，原配方都不够 ⇒ 放大后一定不够）。
            if (!RecipeRunnerHelper.matchRecipeInput(machine, raw)) {
                diag.noteScanReject(type, raw, "原料不足(含ME样板仓)", null);
                continue;
            }
            GTRecipe modified = owner.modifyRecipe(raw);
            if (modified == null) {
                diag.noteScanReject(type, raw, "配方修改器返回 null（去重/门槛拒绝）", null);
                continue;
            }
            // 普通多方块 = 线程倍率 × 并行仓；巨构（usesParallelHatch=false）只看线程倍率。
            // long 乘再钳到 int：温度倍率可能很大，不能把 int 乘爆成负数。
            long multLong = Math.max(1, threaded.parallelMultiplier());
            if (threaded.usesParallelHatch()) {
                multLong *= Math.max(1, parallelHatchCount());
            }
            int perThread = (int) Math.min(Integer.MAX_VALUE, multLong);
            // ★ 2026-11-07 GTO 口径「独立线程」：
            //   开 = 每条线程各自一份完整并行/功率额度（perThread）；
            //   关 = 所有线程共用同一份额度（在跑批次的并行之和不得超过 perThread）。
            int parallelBudget = perThread;
            if (!threaded.threadHatchIndependent()) {
                int used = runningParallelSum();
                parallelBudget = perThread - used;
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
            GTRecipe.ActionResult conditionResult = scaled.checkConditions(owner);
            if (!conditionResult.isSuccess()) {
                diag.noteScanReject(type, scaled, "配方条件不满足", BatchSupport.reasonText(conditionResult));
                continue;
            }
            diag.reject("有配方可跑", null, null);
            state.cooldownUntil = 0;
            state.idlePasses = 0;
            if (!fromMeCache) {
                state.cursor = (idx + 1) % candidates.size();
            }
            return scaled;
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

    /** 「并行重复配方」关闭时：同一条配方是否已被别的线程占用。 */
    private boolean isRecipeRunningElsewhere(GTRecipe raw) {
        if (raw == null || raw.getId() == null) {
            return false;
        }
        for (int i = 0; i < engine.size(); i++) {
            GTRecipe running = engine.get(i).recipe;
            if (running != null && raw.getId().equals(running.getId())) {
                return true;
            }
        }
        return false;
    }

    /** 当前在跑批次占用的并行之和（「独立线程」关闭时用来算剩余共享额度）。 */
    private int runningParallelSum() {
        int sum = 0;
        for (int i = 0; i < engine.size(); i++) {
            sum += Math.max(1, engine.get(i).recipe.parallels);
            if (sum < 0) {
                return Integer.MAX_VALUE;
            }
        }
        return sum;
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
