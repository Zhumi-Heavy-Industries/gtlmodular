package com.gtlmodular.common.machine.engine;

import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.common.data.GTRecipeModifiers;
import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import net.minecraft.resources.ResourceLocation;
import org.gtlcore.gtlcore.api.recipe.RecipeRunnerHelper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * ★ 2026-11-26：<b>跨配方找线程</b>（GTO {@code CrossRecipeTrait.lookupRecipe} 的搜索内核）。
 *
 * <p>所有线程仓/并行仓读数都来自 {@link CrossRecipeTrait}，本类不再自己遍历部件表。
 * 入口 {@link #lookupRecipe(RecipeCharge)} 既负责挑配方，也通过 {@link RecipeCharge}
 * 完成资源预留：所有只读判定通过后 {@code check}，收下时 {@code commit}，失败 {@code rollback}。
 *
 * <h2>一条合格线程要过六关</h2>
 * <ol>
 *   <li>候选来源：ME 样板仓缓存优先；没有缓存才退回整表分片轮扫；</li>
 *   <li>原配方只读输入匹配（不够就不必 copy/超频/精确并行）；</li>
 *   <li>机器准入门槛 + 配方修改器；</li>
 *   <li>额度 = {@link CrossRecipeTrait#parallelBudget()}：独立线程开 = 每线程并行，
 *       关 = 每线程并行 × 线程数（GTO「单线程大并行」，产能守恒）；</li>
 *   <li>精确并行后的输入、每 tick 输入、配方条件三重匹配；</li>
 *   <li>{@link RecipeCharge#check(GTRecipe)} → {@link RecipeCharge#commit()}。</li>
 * </ol>
 */
public final class BatchFinder {

    static final int SCAN_BUDGET = 24;
    static final int IDLE_COOLDOWN_TICKS = 10;

    /** 每 tick 最多新开几条线程：256 槽机器首轮不把一整个 tick 吃掉。 */
    static final int MAX_STARTS_PER_TICK = 8;

    private final IRecipeLogicMachine machine;
    private final IBatchLogicOwner owner;
    private final ConcurrentRecipeEngine engine;
    private final BatchDiagnostics diag;
    private final CrossRecipeTrait trait;

    private int startsThisTick;

    private static final class TableScanState {
        int cursor;
        long cooldownUntil;
        int idlePasses;
    }

    private final Map<GTRecipeType, TableScanState> scanStates = new IdentityHashMap<>();
    private int lastSuccessfulTypeIdx;

    /**
     * ★ 0.3.528：从共享队列 poll 出来但不属于本表的候选，暂存到下次轮转（避免跨表时白丢）。
     * 只在主线程单 tick 内读写，无并发问题；beginTick 时不清，跨 tick 保留。
     */
    private final List<GTRecipe> prefetchPending = new ArrayList<>();

    /**
     * ★ 0.3.531：预取候选的复用暂存。{@link #findForType} 每 tick 会被跨表轮转调用多次，
     * 原先每次都 {@code new ArrayList<>()}；这里复用同一份，函数开头 clear、返回的只是 GTRecipe 引用，
     * 不存在跨调用持有 list 的路径（不匹配的候选逐个转存 {@link #prefetchPending}）。
     */
    private final List<GTRecipe> prefetchScratch = new ArrayList<>();

    /**
     * ★ 0.3.521：整表候选的<b>全局共享缓存</b>（0.3.517 的按机器 tick 缓存再进一步）。
     *
     * <p>0.3.517 的缓存是每台机器一份、每 tick 清空：机器多时同一 tick 仍有 N 次重复展开。
     * 配方表是全局只读的，改成 {@code static} 共享后所有机器只展开一次；用
     * {@code Branch} 引用 + 两个节点表的 size 做指纹，reload/换表下一 tick 自动重建。
     * ★ 0.3.528：WeakHashMap → ConcurrentHashMap。虚拟线程预取与主线程扫描会并发读写这张表，
     * WeakHashMap 非线程安全，并发 put 会破坏内部链。配方表是注册表全局对象、生命周期随游戏，
     * 用强引用不泄漏（条目数 = 配方表种类数，通常个位数）。
     */
    private static final class CachedTable {
        final Object branch;
        final int nodes;
        final int specialNodes;
        final List<GTRecipe> recipes;

        CachedTable(Object branch, int nodes, int specialNodes, List<GTRecipe> recipes) {
            this.branch = branch;
            this.nodes = nodes;
            this.specialNodes = specialNodes;
            this.recipes = recipes;
        }

        boolean matches(Object branch, int nodes, int specialNodes) {
            return this.branch == branch && this.nodes == nodes && this.specialNodes == specialNodes;
        }
    }

    private static final Map<GTRecipeType, CachedTable> TABLE_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    /** ★ 0.3.528：改为包可见，供虚拟线程预取队列读全局静态配方表（只读，安全）。 */
    static List<GTRecipe> recipesFor(GTRecipeType type) {
        var branch = type.getLookup().getLookup();
        int nodes = branch.getNodes().size();
        int special = branch.getSpecialNodes().size();
        CachedTable cached = TABLE_CACHE.get(type);
        if (cached != null && cached.matches(branch, nodes, special)) {
            return cached.recipes;
        }
        // compute：并发下同一张表只展开一次；返回的 recipes 是不可变 toList()，多线程读安全。
        CachedTable fresh = TABLE_CACHE.compute(type, (k, old) -> {
            if (old != null && old.matches(branch, nodes, special)) {
                return old;
            }
            List<GTRecipe> recipes = branch.getRecipes(true).filter(r -> !r.isFuel()).toList();
            return new CachedTable(branch, nodes, special, recipes);
        });
        return fresh.recipes;
    }

    public BatchFinder(IRecipeLogicMachine machine, IBatchLogicOwner owner,
                       ConcurrentRecipeEngine engine, BatchDiagnostics diag, CrossRecipeTrait trait) {
        this.machine = machine;
        this.owner = owner;
        this.engine = engine;
        this.diag = diag;
        this.trait = trait;
    }

    public void beginTick() {
        lastSuccessfulTypeIdx = 0;
        startsThisTick = 0;
    }

    public void clearCooldowns() {
        for (TableScanState s : scanStates.values()) {
            s.cooldownUntil = 0;
            s.idlePasses = 0;
        }
    }

    public void reset() {
        scanStates.clear();
        lastSuccessfulTypeIdx = 0;
        prefetchPending.clear();
    }

    private static long idleCooldown(TableScanState state) {
        state.idlePasses = Math.min(4, state.idlePasses + 1);
        return (long) IDLE_COOLDOWN_TICKS << (state.idlePasses - 1);
    }

    /** GTO {@code lookupRecipe(@Nullable RecipeCharge)} 的入口。 */
    @Nullable
    public GTRecipe lookupRecipe(@Nullable RecipeCharge charge) {
        if (startsThisTick >= MAX_STARTS_PER_TICK) {
            return null;
        }
        GTRecipeType current = machine.getRecipeType();
        if (current == null) {
            return null;
        }

        // 本轮只读快照：trait 已经按 tick 缓存了部件读数，这里只取一次标量。
        trait.refreshParallelStats();   // ★ 0.3.514：刷新 GTO 三个并行读数
        boolean repeated = trait.isRepeatedRecipes();

        // ★ 0.3.524：独立线程关 ⇒ 合并模式——扫出所有能跑的配方，合成一条、单进度条。
        //   判定等级 → 合并配方 → 算超频（对用户截图口径的实现）。
        if (!trait.isIndependentThread()) {
            return lookupMerged(charge, repeated);
        }

        // ★ GTO 口径：单条线程的并行额度由 trait 一次给出（独立线程开 = 每线程并行）。
        int parallelBudget = trait.parallelBudget();
        ObjectSet<ResourceLocation> runningIds = null;
        if (!repeated) {
            runningIds = trait.runningRecipeIds();
        }

        GTRecipeType[] types = machine.getRecipeTypes();
        boolean allTypes = owner.crossTableScan() && types != null && types.length > 1;
        if (!allTypes) {
            return findForType(current, charge, repeated, parallelBudget, runningIds);
        }
        int n = types.length;
        int startIdx = lastSuccessfulTypeIdx % n;
        int candidatesSum = 0;
        List<String> perTable = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            int idx = (startIdx + i) % n;
            diag.forgetScannedThisCall();
            GTRecipe recipe = findForType(types[idx], charge, repeated, parallelBudget, runningIds);
            if (recipe != null) {
                lastSuccessfulTypeIdx = idx;
                return recipe;
            }
            if (diag.scannedThisCall()) {
                candidatesSum += diag.candidates();
                perTable.add(String.valueOf(types[idx].registryName) + " → " + diag.detail());
                diag.clearRecipeContext();
            }
        }
        if (!perTable.isEmpty()) {
            diag.setCandidates(candidatesSum);
            diag.reject("全部配方表都无可用配方（共 " + n + " 张）｜" + String.join("；", perTable), null, null);
        }
        return null;
    }

    /**
     * ★ 0.3.524：独立线程关的合并模式。
     *
     * <p>判定等级 → 合并配方 → 算超频：
     * 扫出所有表的可跑配方（逐条输入匹配 + 机器准入门槛），
     * 用 {@link RecipeMerger} 合成一条（输入/产物/每 tick 输入全汇总），
     * 再对合并结果走一次机器配方修改器（超频/等级折算），最后按 charge 提交。
     */
    private GTRecipe lookupMerged(@Nullable RecipeCharge charge, boolean repeated) {
        List<GTRecipe> accepted = new ArrayList<>();
        GTRecipeType[] types = machine.getRecipeTypes();
        List<GTRecipeType> scanTypes = (owner.crossTableScan() && types != null && types.length > 1)
                ? List.of(types) : List.of(machine.getRecipeType());
        ObjectSet<ResourceLocation> seenIds = repeated ? null
                : new it.unimi.dsi.fastutil.objects.ObjectOpenHashSet<>();

        for (GTRecipeType type : scanTypes) {
            if (type == null) {
                continue;
            }
            diag.startTableScan(type);
            List<GTRecipe> candidates = owner.meCachedCandidates(type);
            if (candidates.isEmpty()) {
                if (!owner.hasRealItemOrFluidInput() && !BatchSupport.hasWaitingMePattern(machine)) {
                    continue;
                }
                candidates = recipesFor(type);
            }
            int limit = Math.min(SCAN_BUDGET, candidates.size());
            for (int i = 0; i < limit; i++) {
                GTRecipe raw = candidates.get(i);
                if (raw == null) {
                    continue;
                }
                diag.addCandidate();
                // 同配方关：合并里同一种配方只收一份。
                if (!repeated && raw.getId() != null && !seenIds.add(raw.getId())) {
                    continue;
                }
                if (!RecipeRunnerHelper.matchRecipeInput(machine, raw)) {
                    diag.noteScanReject(type, raw, "合并：原料不足", null);
                    continue;
                }
                if (!owner.acceptsCandidate(raw)) {
                    diag.noteScanReject(type, raw, "合并：机器准入门槛拒绝", null);
                    continue;
                }
                accepted.add(raw);
            }
        }

        GTRecipe merged = RecipeMerger.merge(accepted);
        if (merged == null) {
            diag.reject("合并模式：没有可合并的配方", null, null);
            return null;
        }
        // 算超频：对合并后的整条配方走一次机器修改器。
        GTRecipe selected = owner.modifyRecipe(merged);
        if (selected == null) {
            diag.reject("合并模式：合并配方修改器返回 null", null, null);
            return null;
        }
        if (!RecipeRunnerHelper.matchRecipeInput(machine, selected)) {
            diag.reject("合并模式：合并后输入不足", null, null);
            return null;
        }
        if (charge != null) {
            if (!charge.check(selected) || !charge.commit()) {
                charge.rollback();
                diag.reject("合并模式：资源预留失败", null, null);
                return null;
            }
        }
        startsThisTick++;
        diag.reject("合并配方已就绪（" + accepted.size() + " 条）", null, null);
        trait.noteThreadOpened(merged, selected);
        return selected;
    }

    private GTRecipe findForType(GTRecipeType type, @Nullable RecipeCharge charge,
                                 boolean repeated, int parallelBudget,
                                 @Nullable ObjectSet<ResourceLocation> runningIds) {
        long now = machine.self().getOffsetTimer();
        TableScanState state = scanStates.computeIfAbsent(type, t -> new TableScanState());
        if (now < state.cooldownUntil) {
            return null;
        }
        diag.startTableScan(type);

        List<GTRecipe> candidates = owner.meCachedCandidates(type);
        boolean fromMeCache = !candidates.isEmpty();
        int start = 0;
        int limit;
        // ★ 0.3.528：优先消费虚拟线程预取的候选（只含本表的）。它们来自全局静态配方表，
        //   主线程拿到后仍走下面完整的「匹配→折算→预留→commit」链，安全边界不变。
        //   不属本表的放进 prefetchPending，等轮到那张表时用，不白丢。
        List<GTRecipe> prefetched = null;
        if (!fromMeCache) {
            prefetched = prefetchScratch;
            prefetched.clear();
            for (int i = prefetchPending.size() - 1; i >= 0; i--) {
                GTRecipe held = prefetchPending.get(i);
                if (held.getType() == type) {
                    prefetched.add(held);
                    prefetchPending.remove(i);
                }
            }
            for (int p = 0; p < SCAN_BUDGET && prefetched.size() < SCAN_BUDGET; p++) {
                GTRecipe pre = owner.prefetchedCandidate();
                if (pre == null) {
                    break;
                }
                if (pre.getType() == type) {
                    prefetched.add(pre);
                } else {
                    prefetchPending.add(pre);
                }
            }
        }
        if (prefetched != null && !prefetched.isEmpty()) {
            candidates = prefetched;
            limit = candidates.size();
            diag.setCandidates(limit);
        } else if (fromMeCache) {
            limit = candidates.size();
        } else {
            if (!owner.hasRealItemOrFluidInput() && !BatchSupport.hasWaitingMePattern(machine)) {
                diag.reject("无实际物品/流体输入（ME 样板缓存为空、样板仓里也没有样板，跳过整表扫描）｜"
                        + BatchSupport.describeInputCaps(machine), null, null);
                state.cooldownUntil = now + idleCooldown(state);
                return null;
            }
            candidates = recipesFor(type);
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
            if (!repeated && raw != null && raw.getId() != null
                    && runningIds != null && runningIds.contains(raw.getId())) {
                diag.noteScanReject(type, raw, "并行重复配方关闭：该配方已在别的线程", null);
                continue;
            }
            if (!RecipeRunnerHelper.matchRecipeInput(machine, raw)) {
                diag.noteScanReject(type, raw, "原料不足(含ME样板仓)", null);
                continue;
            }
            if (!owner.acceptsCandidate(raw)) {
                diag.noteScanReject(type, raw, "机器准入门槛拒绝（层级/条件/温度）", null);
                continue;
            }
            GTRecipe selected;
            if (!owner.usesStandardParallel()) {
                try {
                    selected = owner.prepareBatch(raw);
                } catch (RuntimeException e) {
                    diag.noteScanReject(type, raw, "自备折算异常：" + e, null);
                    continue;
                }
                if (selected == null) {
                    diag.noteScanReject(type, raw, "自备折算返回 null", null);
                    continue;
                }
            } else {
                GTRecipe modified = owner.modifyRecipe(raw);
                if (modified == null) {
                    diag.noteScanReject(type, raw, "配方修改器返回 null（去重/门槛拒绝）", null);
                    continue;
                }
                Pair<GTRecipe, Integer> par = GTRecipeModifiers.accurateParallel(
                        machine.self(), modified, parallelBudget, false);
                selected = par.getFirst();
                if (selected == null) {
                    diag.noteScanReject(type, raw, "并行计算返回 null", null);
                    continue;
                }
                if (!RecipeRunnerHelper.matchRecipeInput(machine, selected)) {
                    diag.noteScanReject(type, selected, "物品/流体不匹配(含ME样板仓)", null);
                    continue;
                }
                GTRecipe.ActionResult tickResult = selected.matchTickRecipe(machine);
                if (!tickResult.isSuccess()) {
                    diag.noteScanReject(type, selected, "每tick输入不足(EU等)", BatchSupport.reasonText(tickResult));
                    continue;
                }
                GTRecipe.ActionResult conditionResult = selected.checkConditions(owner.asRecipeLogic());
                if (!conditionResult.isSuccess()) {
                    diag.noteScanReject(type, selected, "配方条件不满足", BatchSupport.reasonText(conditionResult));
                    continue;
                }
            }

            // ★ GTO RecipeCharge：只读判定全部通过后预留，提交失败立即回滚。
            if (charge != null) {
                if (!charge.check(selected)) {
                    diag.noteScanReject(type, selected, "资源预留检查失败", null);
                    continue;
                }
                if (!charge.commit()) {
                    charge.rollback();
                    diag.noteScanReject(type, selected, "资源预留提交失败（已回滚）", null);
                    continue;
                }
            }
            return acceptThread(raw, selected, fromMeCache, state, candidates, idx);
        }

        boolean fullPass;
        if (fromMeCache) {
            state.cooldownUntil = now + idleCooldown(state);
            fullPass = true;
        } else {
            int total = candidates.size();
            if (start + limit >= total) {
                state.cooldownUntil = now + idleCooldown(state);
                state.cursor = 0;
                fullPass = true;
            } else {
                state.cursor = (start + limit) % total;
                fullPass = false;
            }
        }
        if (diag.candidates() == 0) {
            diag.reject("候选=0（查表没命中：物品/流体/电路是否齐全）", null, null);
        }
        if (fullPass) {
            diag.dumpScanDetail(type);
        }
        return null;
    }

    /** 收下这条配方：清冷却、推进游标、限速计数，并记进 trait 的 lastRecipes。 */
    private GTRecipe acceptThread(GTRecipe raw, GTRecipe selected, boolean fromMeCache,
                                  TableScanState state, List<GTRecipe> candidates, int idx) {
        diag.reject("有配方可跑", raw, null);
        state.cooldownUntil = 0;
        state.idlePasses = 0;
        if (!fromMeCache && candidates != null && !candidates.isEmpty()) {
            state.cursor = (idx + 1) % candidates.size();
        }
        startsThisTick++;
        trait.noteThreadOpened(raw, selected);
        return selected;
    }
}
