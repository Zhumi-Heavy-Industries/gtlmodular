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
 *   <li>额度 = {@link CrossRecipeTrait#getParallel()}，非独立线程再扣掉在跑占用；</li>
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
        boolean repeated = trait.isRepeatedRecipes();
        boolean independent = trait.isIndependentThread();
        int perThread = trait.getParallel();
        int usedParallel = 0;
        ObjectSet<ResourceLocation> runningIds = null;
        if (!independent) {
            long sum = 0L;
            for (int i = 0; i < engine.size(); i++) {
                sum += engine.get(i).use;
            }
            usedParallel = (int) Math.min(Integer.MAX_VALUE, sum);
        }
        if (!repeated) {
            runningIds = trait.runningRecipeIds();
        }

        GTRecipeType[] types = machine.getRecipeTypes();
        boolean allTypes = owner.crossTableScan() && types != null && types.length > 1;
        if (!allTypes) {
            return findForType(current, charge, repeated, independent, perThread, usedParallel, runningIds);
        }
        int n = types.length;
        int startIdx = lastSuccessfulTypeIdx % n;
        int candidatesSum = 0;
        List<String> perTable = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            int idx = (startIdx + i) % n;
            diag.forgetScannedThisCall();
            GTRecipe recipe = findForType(types[idx], charge, repeated, independent,
                    perThread, usedParallel, runningIds);
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

    private GTRecipe findForType(GTRecipeType type, @Nullable RecipeCharge charge,
                                 boolean repeated, boolean independent, int perThread,
                                 int usedParallel, @Nullable ObjectSet<ResourceLocation> runningIds) {
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
        if (fromMeCache) {
            limit = candidates.size();
        } else {
            if (!owner.hasRealItemOrFluidInput() && !BatchSupport.hasWaitingMePattern(machine)) {
                diag.reject("无实际物品/流体输入（ME 样板缓存为空、样板仓里也没有样板，跳过整表扫描）｜"
                        + BatchSupport.describeInputCaps(machine), null, null);
                state.cooldownUntil = now + idleCooldown(state);
                return null;
            }
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
                int parallelBudget = perThread;
                if (!independent) {
                    parallelBudget = perThread - usedParallel;
                    if (parallelBudget <= 0) {
                        diag.noteScanReject(type, raw, "独立线程关闭：并行额度已被在跑线程占满", null);
                        continue;
                    }
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
        trait.noteThreadOpened(selected);
        return selected;
    }
}
