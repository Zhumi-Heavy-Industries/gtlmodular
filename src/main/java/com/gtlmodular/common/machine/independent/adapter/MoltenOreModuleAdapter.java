package com.gtlmodular.common.machine.independent.adapter;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gtlmodular.GTLModular;
import com.gtlmodular.common.GameTestHooks;
import com.gtlmodular.common.config.GTLModularConfig;
import com.gtlmodular.common.diag.SwallowedErrors;
import com.gtlmodular.common.machine.MoltenBlastModuleMachine;
import com.gtlmodular.common.machine.MoltenModuleHostMachine;
import com.gtlmodular.common.machine.MoltenModuleMachineBase;
import com.gtlmodular.common.machine.MoltenSpaceOreModuleMachine;
import com.gtlmodular.common.machine.independent.recipe.*;
import net.minecraft.resources.ResourceLocation;
import org.gtlcore.gtlcore.api.machine.trait.ILockRecipe;
import org.gtlcore.gtlcore.api.recipe.IGTRecipe;
import org.gtlcore.gtlcore.api.recipe.IParallelLogic;
import org.gtlcore.gtlcore.api.recipe.RecipeRunnerHelper;
import org.gtlcore.gtlcore.common.machine.trait.MultipleRecipesLogic;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 地心炼矿模块专用适配器。只读候选、不调用上游 getRecipe()（那会真实扣料），
 * 把 MultipleRecipesLogic 的合批拆成独立批次；能量折扣由机器侧的
 * IndependentEnergyMultiplier 提供，真正扣电仍走宿主枢纽。
 */
final class MoltenOreModuleAdapter implements IndependentRecipeAdapter {
    /** 单批最小时长：上游聚合配方 20 tick 保底，长批次以此为「一份」的时长基准。 */
    private static final int MINIMUM_BATCH_TICKS = 20;

    private final MultipleRecipesLogic logic;
    private final MoltenModuleMachineBase<?> machine;
    private final Supplier<Map<ResourceLocation, Integer>> activeRecipes;
    private final RecipeFairness fairness = new RecipeFairness();
    private final CandidateCache<GTRecipe> candidateCache = new CandidateCache<>();

    MoltenOreModuleAdapter(MultipleRecipesLogic logic, MoltenModuleMachineBase<?> machine,
                           Supplier<Map<ResourceLocation, Integer>> activeRecipes) {
        this.logic = logic;
        this.machine = machine;
        this.activeRecipes = activeRecipes;
    }

    @Override
    public int threads() {
        if (machine instanceof MoltenBlastModuleMachine blast) return Math.max(1, blast.totalThreads());
        if (machine instanceof MoltenSpaceOreModuleMachine ore) return Math.max(1, ore.totalThreads());
        return 1;
    }

    /** 保留接口口径（上游「最大并行 × 线程数」）；模块的候选阶段不再用它分摊，见 candidates 里的独立并行口径。 */
    @Override
    public long totalBudget() {
        return ParallelAllocation.totalBudget(machine.getMaxParallel(), threads());
    }

    /** 独立线程：每批并行已按机器上限夹，线程数由 slots 控制，不再用共享池卡准入。 */
    @Override
    public long sessionBudget() {
        return Long.MAX_VALUE;
    }

    @Override
    public boolean allowsNewBatches() {
        return hostReady();
    }

    @Override
    public boolean allowsRunningBatches() {
        return hostReady();
    }

    private boolean hostReady() {
        MoltenModuleHostMachine host = machine.getHost();
        return machine.isFormed() && host != null && host.isFormed() && host.isWorkingEnabled();
    }

    private double multiplier() {
        return machine.independentEnergyMultiplier();
    }

    @Override
    public void admitted(GTRecipe recipe) {
        fairness.admitted(recipe.id, now());
        // 不要在这里失效配方集合：一批结算就会重搜 999 条配方（实测 1~1.4 ms/次）。
        // 集合只按 TTL 与配置/材料变化失效；准入本身仍逐条重新校验输入。
    }

    @Override
    public void invalidateCandidates() {
        candidateCache.invalidate();
        matchTrueAt.clear();
        matchFalseAt.clear();
        scaledCache.clear();
        demandCache.clear();
    }

    private long now() {
        return machine.getLevel() == null ? 0L : machine.getLevel().getGameTime();
    }

    /** 空结果暂停搜索：40 tick 起退避，连续为空翻倍到 200 tick（对齐山海「没匹配就暂停/缓存」）。 */
    private long emptyRetryAt = Long.MIN_VALUE;
    private int emptyStreak;

    /** 缩放结果缓存：同配方同并行只缩一次，取用时再复制，避免批次之间共享可变对象。 */
    private final java.util.IdentityHashMap<GTRecipe, long[]> demandCache = new java.util.IdentityHashMap<>();
    private static final int DEMAND_CACHE_LIMIT = 256;

    private long cachedDemand(GTRecipe origin, long now, long budget) {
        long[] cached = demandCache.get(origin);
        if (cached != null && now - cached[0] < 20L) return Math.min(cached[1], budget);
        long demand = IParallelLogic.getMaxParallel(machine, origin, budget);
        if (demandCache.size() >= DEMAND_CACHE_LIMIT) demandCache.clear();
        demandCache.put(origin, new long[]{now, demand});
        return demand;
    }

    private List<SingleRecipeCandidate> candidateSnapshot;
    private long candidateSnapshotAt = Long.MIN_VALUE;
    private String candidateSnapshotSignature = "";

    private final java.util.Map<Long, GTRecipe> scaledCache = new java.util.LinkedHashMap<>(32, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(java.util.Map.Entry<Long, GTRecipe> eldest) {
            return size() > 512;
        }
    };

    private GTRecipe scaledIngredients(GTRecipe origin, long parallels) {
        long key = ((long) System.identityHashCode(origin) << 32) ^ parallels;
        var cached = scaledCache.get(key);
        if (cached == null) {
            cached = RecipeCandidateSnapshots.scale(origin, parallels);
            scaledCache.put(key, cached);
        }
        // 只读共享：解析 tick 内容时已经把 Content 复制出来，批次不会改到这份缓存。
        return cached;
    }

    private final java.util.IdentityHashMap<GTRecipe, Long> matchTrueAt = new java.util.IdentityHashMap<>();
    private final java.util.IdentityHashMap<GTRecipe, Long> matchFalseAt = new java.util.IdentityHashMap<>();

    private boolean check(GTRecipe recipe) {
        long nowTick = now();
        Long fresh = matchTrueAt.get(recipe);
        if (fresh != null && nowTick - fresh < 20L) return true;
        Long stale = matchFalseAt.get(recipe);
        if (stale != null && nowTick - stale < 20L) return false;
        boolean result = checkUncached(recipe);
        if (result) {
            matchFalseAt.remove(recipe);
            matchTrueAt.put(recipe, nowTick);
        } else {
            matchTrueAt.remove(recipe);
            matchFalseAt.put(recipe, nowTick);
        }
        return result;
    }

    private boolean checkUncached(GTRecipe recipe) {
        try {
            return RecipeRunnerHelper.matchRecipe(machine, recipe)
                    && IGTRecipe.of(recipe).getEuTier() <= machine.getTier()
                    && recipe.checkConditions(logic).isSuccess()
                    && (logic.getDataCheck() == null || logic.getDataCheck().test(recipe.data, machine));
        } catch (RuntimeException ignored) {
            SwallowedErrors.reportDebug("MoltenOreModuleAdapter.check", ignored);
            return false;
        }
    }

    private int lastLoggedCandidates = Integer.MIN_VALUE;

    /** 匹配数量变化时列出本轮配方 id，方便对「16 格总线为什么只开 N 条」。 */
    private void logCandidateCount(List<GTRecipe> found) {
        if (found.size() == lastLoggedCandidates) return;
        lastLoggedCandidates = found.size();
        StringBuilder ids = new StringBuilder();
        for (int i = 0; i < Math.min(24, found.size()); i++) {
            if (i > 0) ids.append(", ");
            ids.append(found.get(i).id.getPath());
        }
        if (found.size() > 24) ids.append(", …");
        GTLModular.LOGGER.info("[independent] 本轮匹配到 {} 种配方: [{}]", found.size(), ids);
    }

    /** 候选为空时，抽样一条配方逐项说清是哪道门卡住，免得只看到「未找到配方」。 */
    private void logWhyNoCandidates(GTRecipeType type) {
        if (type == null || !noCandidateLogged.add(type)) return;
        try {
            Iterator<GTRecipe> all = type.getLookup().getRecipeIterator(machine, recipe -> true);
            if (!all.hasNext()) {
                GTLModular.LOGGER.info("[independent] 候选为空：配方表本身就是空的 type={}", type.registryName);
                return;
            }
            GTRecipe recipe = all.next();
            boolean match = RecipeRunnerHelper.matchRecipe(machine, recipe);
            boolean tier = IGTRecipe.of(recipe).getEuTier() <= machine.getTier();
            boolean condition = recipe.checkConditions(logic).isSuccess();
            boolean data = logic.getDataCheck() == null || logic.getDataCheck().test(recipe.data, machine);
            GTLModular.LOGGER.info("[independent] 候选为空 type={} 抽样配方={} match={} tier={} condition={} data={}",
                    type.registryName, recipe.id, match, tier, condition, data);
        } catch (RuntimeException exception) {
            GTLModular.LOGGER.info("[independent] 候选为空排查失败: {}", exception.toString());
        }
    }

    private long lastDiagTick = Long.MIN_VALUE;

    /** 诊断行限流：同一台机器 40 tick 一条。 */
    private void diag(String message) {
        long now = now();
        if (now - lastDiagTick < 40L) return;
        lastDiagTick = now;
        GTLModular.LOGGER.info("[independent] 候选诊断 {}: {}", machine.getPos(), message);
    }

    private final java.util.Set<GTRecipeType> noCandidateLogged = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

    private Iterator<GTRecipe> lookup(GTRecipeType type) {
        ILockRecipe lock = logic instanceof ILockRecipe ? (ILockRecipe) logic : null;
        if (lock != null && lock.isLock()) {
            GTRecipe locked = lock.getLockRecipe();
            if (locked == null) {
                locked = type.getLookup().find(machine, this::check);
                lock.setLockRecipe(locked);
            }
            if (locked == null || !check(locked)) return List.<GTRecipe>of().iterator();
            return List.of(locked).iterator();
        }
        return type.getLookup().getRecipeIterator(machine, this::check);
    }

    @Override
    public List<SingleRecipeCandidate> candidates(int availableSlots, long availableBudget, long availablePower) {
        if (availableSlots <= 0) {
            diag("槽位不足 slots=" + availableSlots);
            return List.of();
        }
        if (availableBudget <= 0L) {
            diag("预算不足 budget=" + availableBudget);
            return List.of();
        }
        if (!allowsNewBatches()) {
            var host = machine.getHost();
            diag("宿主未就绪 machineFormed=" + machine.isFormed()
                    + " host=" + (host == null ? "null" : host.isFormed() + "/" + host.isWorkingEnabled()));
            return List.of();
        }
        // 独立线程：每条批次各自吃满「本配方自己的上限」，不与其他候选分摊一份总池。
        // 对齐上游 MultipleRecipesLogic.calculateParallels 的覆写：同时选中多个配方类型时，
        // 每个配方各自独立吃满自己的 getMaxParallel(recipe, totalParallel)，互不挤占。
        long perBatch = Math.max(1L, machine.getMaxParallel());
        if (availableBudget <= 0L) return List.of();
        int maxBatchTicks = GTLModularConfig.OTHER_MACHINE_THREADS.maxBatchTicks();
        // 长批次：目标时长由配置给（0 = 关闭），单批时长不能被 maxBatchTicks 安全阀放大。
        int longBatchTicks = GTLModularConfig.OTHER_MACHINE_THREADS.longBatchTicks();
        // GameTest 断言的是「一条批次一次配方」的旧口径（精确产物数量、20 tick 时长），
        // 测试里固定关闭长批次；它只在游戏内由面板「第 k/N 次」和产量对账验收。
        if (GameTestHooks.active()) longBatchTicks = 0;
        if (maxBatchTicks > 0 && longBatchTicks > maxBatchTicks) longBatchTicks = maxBatchTicks;
        int targetRuns = longBatchTicks > 0 ? Math.max(1, longBatchTicks / MINIMUM_BATCH_TICKS) : 1;
        long demandBound = targetRuns > 1 ? saturatingMultiply(perBatch, targetRuns) : perBatch;
        long now = now();
        if (now < emptyRetryAt) return List.of();
        int cacheTicks = GTLModularConfig.OTHER_MACHINE_THREADS.candidateCacheTicks();
        var cached = candidateCache.get(now, cacheTicks, () -> {
            List<GTRecipe> found = new ArrayList<>();
            Iterator<GTRecipe> iterator = lookup(machine.getRecipeType());
            while (iterator.hasNext()) {
                GTRecipe recipe = iterator.next();
                if (recipe != null) found.add(RecipeCandidateSnapshots.copy(recipe));
            }
            if (found.isEmpty()) logWhyNoCandidates(machine.getRecipeType());
            else logCandidateCount(found);
            return found;
        });
        if (cached.isEmpty()) {
            emptyStreak = Math.min(3, emptyStreak + 1);
            emptyRetryAt = now + (40L << (emptyStreak - 1));
            return List.of();
        }
        emptyStreak = 0;
        int maxPerRecipe = GTLModularConfig.OTHER_MACHINE_THREADS.maxBatchesPerRecipe();
        Map<ResourceLocation, Integer> active = activeRecipes.get();
        // 直接用缓存里的配方做排序，不再每 tick 复制 16 份；真正要开批时才按索引缩放。
        List<GTRecipe> origins = cached;
        var order = fairness.orderIndices(origins, recipe -> recipe.id, maxPerRecipe,
                active == null ? Map.of() : active);
        if (now - lastDiagTick >= 200L) {
            diag("管线 cached=" + cached.size() + " active=" + (active == null ? 0 : active.size())
                    + " maxPerRecipe=" + maxPerRecipe + " order=" + order.size()
                    + " slots=" + availableSlots + " 单批上限=" + perBatch + " 份数上限=" + targetRuns);
        }
        if (order.isEmpty()) {
            diag("候选被活跃计数全部过滤 active=" + active + " maxPerRecipe=" + maxPerRecipe + " cached=" + cached.size());
            return List.of();
        }
        int scanBudget = GTLModularConfig.OTHER_MACHINE_THREADS.candidateScanBudget();
        // 每轮只处理最久没被服务的 8 条候选，而不是把所有匹配配方（可能 70+）都缩放一遍；
        // 轮转靠公平排序在后续 tick 覆盖其余配方。scanBudget 显式配置时优先。
        // 扫描改成 5 tick 一次后，单次要补更多候选才能跟上结算速度（每 tick ~2.4 条）。
        int window = scanBudget > 0
                ? Math.min(order.size(), scanBudget)
                : Math.min(order.size(), 16);
        long[] demands = new long[window];
        long[] headrooms = new long[window];
        long powerCap = Math.min(availablePower, machine.getOverclockVoltage());
        long maxBatchRuns = GTLModularConfig.OTHER_MACHINE_THREADS.maxBatchRuns();
        double multiplier = multiplier();
        for (int n = 0; n < window; n++) {
            GTRecipe origin = origins.get(order.get(n));
            // getMaxParallel 要按配方核对 ME 库存，单条 100+ µs；同一配方 20 tick 内复用上次的并行上限。
            // 长批次要 p×N 份原料，所以这里按「目标份数」问一次真实库存上限，再夹回本批预算。
            long headroom = cachedDemand(origin, now, demandBound);
            long demand = Math.min(headroom, perBatch);
            headrooms[n] = headroom;
            if (demand < 0L || demand > perBatch) throw new IllegalArgumentException("Parallel override exceeds the machine limit");
            long maxAmount = BatchRunLimits.maxContentAmount(origin);
            double energyPerRun = RecipeHelper.getInputEUt(origin) * (double) origin.duration * multiplier;
            long capped = BatchRunLimits.cap(demand, energyPerRun, powerCap, maxBatchTicks, maxBatchRuns, maxAmount);
            // 再按「每 tick EU/t ≤ 机器功率」夹一次：否则 p 会把 tick 耗电放大到开不了工。
            demands[n] = BatchRunLimits.tickCap(capped, RecipeHelper.getInputEUt(origin) * multiplier, powerCap);
        }
        // 独立线程不做跨候选的并行分摊：每条候选各自用自己算出来的 p（已过库存/功率/时长安全阀）。
        long allocatedSum = 0L;
        for (long value : demands) allocatedSum += value;
        if (allocatedSum <= 0L) {
            diag("并行上限为 0 perBatch=" + perBatch + " window=" + window + " demands=" + java.util.Arrays.toString(demands));
            return List.of();
        }
        List<RecipeCandidateGroup.Input> inputs = new ArrayList<>();
        List<Double> energy = new ArrayList<>();
        for (int n = 0; n < window; n++) {
            long p = demands[n];
            if (p <= 0L) continue;
            GTRecipe origin = origins.get(order.get(n));
            GTRecipe ingredients = scaledIngredients(origin, p);
            // 库存撑不起 p×N 就缩 N，缩到 1 就是原来的「一条批次一次配方」。
            int maxRuns = targetRuns > 1
                    ? (int) Math.max(1L, Math.min(targetRuns, headrooms[n] / Math.max(1L, p)))
                    : 1;
            inputs.add(new RecipeCandidateGroup.Input(ingredients, new ParallelAllocation(p, p), 1, 1, maxRuns));
            energy.add(SingleBatchEnergy.total((double) RecipeHelper.getInputEUt(origin) * origin.duration, p, multiplier));
        }
        return RecipeCandidateGroup.normal(inputs, energy,
                Math.min(availablePower, machine.getOverclockVoltage()), MINIMUM_BATCH_TICKS, targetRuns > 1 ? longBatchTicks : 0);
    }

    /** 目标份数乘预算时可能溢出：饱和到 long 上限，真正的库存上限由 getMaxParallel 自己夹。 */
    private static long saturatingMultiply(long value, int factor) {
        try {
            return Math.multiplyExact(value, (long) factor);
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }
}
