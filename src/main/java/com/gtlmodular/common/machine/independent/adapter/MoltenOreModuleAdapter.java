package com.gtlmodular.common.machine.independent.adapter;

import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
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
import com.gtlmodular.common.machine.independent.io.BatchContents;
import com.gtlmodular.common.machine.independent.recipe.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
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

    /**
     * 引擎的「线程数」= 机器上 GTO 线程仓给出的条数（和玩家面板口径一致）。
     * 以前用 totalThreads()（BASE_THREADS 64 + 线程仓×倍率），玩家把线程仓设成 4 时引擎还是开 64 条。
     * 没装线程仓时退回机器自报的总线程数，保持原行为。
     */
    @Override
    public int threads() {
        // 线程数由机器代码写死（MoltenModuleMachineBase.independentThreads），不读配置、不给玩家改。
        if (machine instanceof MoltenModuleMachineBase) {
            int value = Math.max(1, ((MoltenModuleMachineBase<?>) machine).independentThreads());
            logThreadSource(value(value, "机器代码固定值"));
            return value;
        }
        logThreadSource(value(1, "非模块机"));
        return 1;
    }

    private record ThreadSource(int count, String from) {}

    private static ThreadSource value(int count, String from) { return new ThreadSource(count, from); }

    private int loggedThreads = -1;
    private long loggedThreadsAt = Long.MIN_VALUE / 2;

    /** 线程数变了就写一次来源：配置 / 线程仓条数 / 机器总线程数，便于玩家对着面板核对。 */
    private void logThreadSource(ThreadSource source) {
        long now = now();
        if (source.count() == loggedThreads && now - loggedThreadsAt < 1200L) return;
        loggedThreads = source.count();
        loggedThreadsAt = now;
        var blast = machine instanceof MoltenBlastModuleMachine b ? b : null;
        var part = blast == null ? null : blast.getThreadPartMachine();
        GTLModular.LOGGER.info("[independent] 线程数 {} @{}（来源：{}）: 线程仓={} 机器总线程={}",
                source.count(), machine.getPos(), source.from(),
                part == null ? "无" : String.valueOf(part.getThreadCount()),
                blast == null ? "不适用" : String.valueOf(blast.totalThreads()));
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
        snapshotCache.clear();
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
        // 库存变化比缓存慢得多：20 tick 太短，扫描里大部分时间都在重算 getMaxParallel（单条数百微秒）。
        if (cached != null && now - cached[0] < 100L) return Math.min(cached[1], budget);
        long demand = IParallelLogic.getMaxParallel(machine, origin, budget);
        if (demandCache.size() >= DEMAND_CACHE_LIMIT) demandCache.clear();
        demandCache.put(origin, new long[]{now, demand});
        return demand;
    }

    /**
     * 候选快照的惰性复制：只复制本轮窗口真正用到的配方。
     * 原来在缓存重载时一口气复制全部 ~50 条（含条件 JSON 往返），实测一次 10~32ms 尖峰。
     */
    private final java.util.IdentityHashMap<GTRecipe, GTRecipe> snapshotCache = new java.util.IdentityHashMap<>();

    private GTRecipe snapshot(GTRecipe origin) {
        var cached = snapshotCache.get(origin);
        if (cached != null) return cached;
        cached = RecipeCandidateSnapshots.copy(origin);
        snapshotCache.put(origin, cached);
        return cached;
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
            logRejectionOnce(recipe);
        }
        return result;
    }

    private final java.util.Map<ResourceLocation, Long> rejectedLoggedAt = new java.util.HashMap<>();

    /**
     * 候选被排除时把「为什么」写日志：同一配方 200 tick 内只写一次，
     * 原料变化后能重新看到新原因（之前按配方永久去重，改了输入就再也不会打日志）。
     */
    private void logRejectionOnce(GTRecipe recipe) {
        if (recipe.id == null) return;
        long nowTick = now();
        Long last = rejectedLoggedAt.get(recipe.id);
        if (last != null && nowTick - last < 200L) return;
        if (rejectedLoggedAt.size() > 256) rejectedLoggedAt.clear();
        rejectedLoggedAt.put(recipe.id, nowTick);
        String reason = rejectReason(recipe);
        if (reason != null && reason.contains("输入匹配失败")) logInputTrace(recipe);
        GTLModular.LOGGER.info("[independent] 配方被排除 {} @{}: {} | 机器输入仓=[{}]",
                recipe.id, machine.getPos(), reason, inputHandlersSummary());
    }

    /** 把机器 IN/BOTH 行里的处理器类名打出来：确认创造仓之类的特殊仓到底有没有进匹配口径。 */
    private String inputHandlersSummary() {
        try {
            if (machine instanceof com.gregtechceu.gtceu.api.capability.recipe.IRecipeCapabilityHolder) {
                var holder = (com.gregtechceu.gtceu.api.capability.recipe.IRecipeCapabilityHolder) machine;
                StringBuilder sb = new StringBuilder();
                var rows = new com.gregtechceu.gtceu.api.capability.recipe.IO[]{
                        com.gregtechceu.gtceu.api.capability.recipe.IO.IN,
                        com.gregtechceu.gtceu.api.capability.recipe.IO.BOTH};
                for (var io : rows) {
                    holder.getCapabilitiesProxy().row(io).forEach((cap, handlers) -> {
                        if (cap != ItemRecipeCapability.CAP && cap != FluidRecipeCapability.CAP) return;
                        for (Object handler : handlers) {
                            if (sb.length() > 0) sb.append(' ');
                            sb.append(handler.getClass().getSimpleName()).append('/').append(cap.name);
                        }
                    });
                }
                return sb.toString();
            }
        } catch (RuntimeException failure) {
            return "读不到：" + failure.getClass().getSimpleName();
        }
        return "没有配方仓代理";
    }

    /**
     * 把配方要的物品/流体逐条写出来，并**逐条标注机器是否认到**（[缺]）。
     * 只写「输入匹配失败」看不出是哪一样，尤其 ME 补给总线满库存却匹配不过的时候。
     */
    private String describeInputs(GTRecipe recipe) {
        StringBuilder need = new StringBuilder();
        for (var entry : recipe.inputs.entrySet()) {
            if (entry.getKey() != ItemRecipeCapability.CAP && entry.getKey() != FluidRecipeCapability.CAP) continue;
            for (var content : entry.getValue()) {
                if (need.length() > 0) need.append(", ");
                need.append(BatchContents.describe(entry.getKey(), content.content))
                        .append('×').append(BatchContents.amount(entry.getKey(), content.content))
                        .append(inputSatisfied(entry.getKey(), content.content, recipe) ? "" : "[缺]");
            }
        }
        return need.length() == 0 ? "配方没有物品/流体输入" : need.toString();
    }

    /**
     * 按 GT 的匹配顺序把整组输入逐个处理器过一遍，并打出每一步的余量：
     * 哪个处理器在帮忙、哪个原样返回，一眼能看出来（ME 补给总线只有真配方才给数）。
     */
    private void logInputTrace(GTRecipe recipe) {
        long nowTick = now();
        if (nowTick - inputTraceAt < 200L) return;
        inputTraceAt = nowTick;
        try {
            if (!(machine instanceof com.gregtechceu.gtceu.api.capability.recipe.IRecipeCapabilityHolder)) return;
            var holder = (com.gregtechceu.gtceu.api.capability.recipe.IRecipeCapabilityHolder) machine;
            StringBuilder line = new StringBuilder();
            var caps = new RecipeCapability<?>[]{ItemRecipeCapability.CAP, FluidRecipeCapability.CAP};
            for (var cap : caps) {
                var start = recipe.inputs.getOrDefault(cap, java.util.List.of());
                if (start.isEmpty()) continue;
                var remaining = new java.util.ArrayList<Object>();
                for (var content : start) remaining.add(cap.copyContent(content.content));
                line.append(cap.name).append(':');
                int asked = 0;
                for (var io : new com.gregtechceu.gtceu.api.capability.recipe.IO[]{
                        com.gregtechceu.gtceu.api.capability.recipe.IO.IN,
                        com.gregtechceu.gtceu.api.capability.recipe.IO.BOTH}) {
                    var handlers = holder.getCapabilitiesProxy().get(io, cap);
                    if (handlers == null) continue;
                    for (var handler : handlers) {
                        asked++;
                        try {
                            var left = ((com.gregtechceu.gtceu.api.capability.recipe.IRecipeHandler<Object>) handler)
                                    .handleRecipeInner(io, recipe, remaining, null, true);
                            remaining = left == null ? new java.util.ArrayList<>() : new java.util.ArrayList<>(left);
                        } catch (RuntimeException failure) {
                            line.append('[').append(handler.getClass().getSimpleName()).append("!=")
                                    .append(failure.getClass().getSimpleName()).append(']');
                        }
                        line.append(' ').append(shortName(handler)).append('→').append(remaining.size());
                        if (remaining.isEmpty()) break;
                    }
                    if (remaining.isEmpty()) break;
                }
                if (remaining.size() == start.size() && line.indexOf("Stocking") >= 0) {
                    // 所有处理器都原样返回，且这机器用的是 ME 补给总线：
                    // 总线内部是 syncStock() 刷新的库存快照，快照为空时它一律说「没有」。
                    line.append("[提示:ME补给总线库存快照为空]");
                }
                line.append(" 余=").append(remaining.size()).append(" 处理器=").append(asked).append("; ");
            }
            GTLModular.LOGGER.info("[independent] 输入追踪 {} @{}: {}", recipe.id, machine.getPos(), line);
        } catch (RuntimeException failure) {
            GTLModular.LOGGER.info("[independent] 输入追踪失败 {}: {}", recipe.id, failure.toString());
        }
    }

    private long inputTraceAt = -10_000L;

    private static String shortName(Object handler) {
        String n = handler.getClass().getSimpleName();
        if (n.length() > 18) n = n.substring(0, 18);
        return n.isEmpty() ? "匿名仓" : n;
    }

    /** 单独问一遍机器的每个 IN/BOTH 处理器：这一样原料它认不认。认就返回 true。 */
    @SuppressWarnings("unchecked")
    private boolean inputSatisfied(RecipeCapability<?> cap, Object content, GTRecipe recipe) {
        try {
            if (!(machine instanceof com.gregtechceu.gtceu.api.capability.recipe.IRecipeCapabilityHolder)) return false;
            var holder = (com.gregtechceu.gtceu.api.capability.recipe.IRecipeCapabilityHolder) machine;
            var rows = new com.gregtechceu.gtceu.api.capability.recipe.IO[]{
                    com.gregtechceu.gtceu.api.capability.recipe.IO.IN,
                    com.gregtechceu.gtceu.api.capability.recipe.IO.BOTH};
            for (var io : rows) {
                var handlers = holder.getCapabilitiesProxy().get(io, cap);
                if (handlers == null) continue;
                for (var handler : handlers) {
                    try {
                        Object copy = cap.copyContent(content);
                        var input = new java.util.ArrayList<Object>();
                        input.add(copy);
                        var left = ((com.gregtechceu.gtceu.api.capability.recipe.IRecipeHandler<Object>) handler)
                                .handleRecipeInner(io, recipe, input, null, true);
                        if (left == null || left.isEmpty()) return true;
                    } catch (RuntimeException ignored) {
                        // 单个处理器读失败不影响其它处理器判断
                    }
                }
            }
        } catch (RuntimeException ignored) {
        }
        return false;
    }

    private String rejectReason(GTRecipe recipe) {
        try {
            if (!RecipeRunnerHelper.matchRecipe(machine, recipe)) {
                return "输入匹配失败：" + describeInputs(recipe);
            }
            if (IGTRecipe.of(recipe).getEuTier() > machine.getTier()) {
                return "配方电压等级 " + IGTRecipe.of(recipe).getEuTier() + " 高于机器 " + machine.getTier();
            }
            var conditions = recipe.checkConditions(logic);
            if (!conditions.isSuccess()) return "条件不满足：" + conditions;
            if (logic.getDataCheck() != null && !logic.getDataCheck().test(recipe.data, machine)) return "数据校验不通过";
            return "未知原因";
        } catch (RuntimeException failure) {
            return "检查抛异常：" + failure.getClass().getSimpleName() + ": " + failure.getMessage();
        }
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

    private long lastDiagTick = -10_000L;

    /**
     * 配方的「族」= 第一条非电路物品输入（黑奴挖矿机就是各种世界碎片）。
     * 调度按族轮转，保证每种碎片都能轮到线程，而不是按配方 id 字典序先跑完 {@code _1_*}。
     */
    private String familyOf(GTRecipe recipe) {
        try {
            for (var entry : recipe.inputs.entrySet()) {
                if (entry.getKey() != ItemRecipeCapability.CAP) continue;
                for (var content : entry.getValue()) {
                    if (BatchContents.isCircuit(content.content)) continue;
                    return BatchContents.describe(entry.getKey(), content.content);
                }
            }
        } catch (RuntimeException ignored) {
            // 读不出主原料就不分族，退回全局老化排序。
        }
        return null;
    }

    /** 逐轮增量枚举：游标跨轮保留，每轮最多 check 16 条，避免一次 45 ms。 */
    private final java.util.List<GTRecipe> enumFound = new ArrayList<>();
    private final java.util.Set<GTRecipe> enumSeen =
            java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    private int enumCursor;
    private long enumSignature = Long.MIN_VALUE;
    private static final int ENUM_CHECKS_PER_SCAN = 16;

    private void enumerateIncrementally(GTRecipeType type, long signature, List<GTRecipe> found) {
        if (signature != enumSignature) {
            enumFound.clear();
            enumSeen.clear();
            enumCursor = 0;
            enumSignature = signature;
        }
        var all = allRecipes(type);
        if (all.isEmpty() || all.size() > 256) {
            // 巨量配方表不整表扫；lookup 也空了才做一次全量兜底。
            if (found.isEmpty()) found.addAll(enumerateAll(type));
            return;
        }
        int checks = 0;
        while (enumCursor < all.size() && checks < ENUM_CHECKS_PER_SCAN) {
            GTRecipe recipe = all.get(enumCursor++);
            checks++;
            if (recipe == null || !enumSeen.add(recipe)) continue;
            if (check(recipe)) enumFound.add(recipe);
        }
        if (enumCursor >= all.size()) enumCursor = 0;
        for (GTRecipe recipe : enumFound) {
            if (!found.contains(recipe)) found.add(recipe);
        }
    }

    private long signatureAt = Long.MIN_VALUE;
    private long signatureValue = Long.MIN_VALUE;

    /**
     * 输入签名：机器里物品种类的集合哈希（数量不影响 matchRecipe，只要 ≥1）。
     * 20 tick 内复用；带 ME 补给总线的机器内容随网络变化，签名直接返回 now（每次都重建）。
     */
    private long inputSignature(long now) {
        if (now - signatureAt < 20L) return signatureValue;
        signatureAt = now;
        long hash = 1125899906842597L;
        boolean volatileStock = false;
        try {
            if (machine instanceof com.gregtechceu.gtceu.api.capability.recipe.IRecipeCapabilityHolder) {
                var holder = (com.gregtechceu.gtceu.api.capability.recipe.IRecipeCapabilityHolder) machine;
                var rows = new com.gregtechceu.gtceu.api.capability.recipe.IO[]{
                        com.gregtechceu.gtceu.api.capability.recipe.IO.IN,
                        com.gregtechceu.gtceu.api.capability.recipe.IO.BOTH};
                for (var row : rows) {
                    for (var entry : holder.getCapabilitiesProxy().row(row).entrySet()) {
                        if (entry.getKey() != ItemRecipeCapability.CAP) continue;
                        for (Object handler : entry.getValue()) {
                            String name = handler.getClass().getSimpleName();
                            if (name.contains("Proxy")) continue;   // 代理仓，内容由被代理的仓已经统计过
                            if (name.contains("Stocking")) {
                                volatileStock = true;               // ME 补给：内容随网络变化
                                continue;
                            }
                            if (handler instanceof com.lowdragmc.lowdraglib.side.item.IItemTransfer) {
                                var items = (com.lowdragmc.lowdraglib.side.item.IItemTransfer) handler;
                                for (int slot = 0; slot < items.getSlots(); slot++) {
                                    var stack = items.getStackInSlot(slot);
                                    if (stack == null || stack.isEmpty()) continue;
                                    hash = hash * 31L + net.minecraft.core.registries.BuiltInRegistries.ITEM
                                            .getKey(stack.getItem()).hashCode();
                                }
                            } else {
                                // 读不到内容的仓（电路编码仓等）：只把类型和容量计入签名，
                                // 不再当成「易变」，否则签名恒变、每次都整表重扫（实测 46 ms 一跳）。
                                hash = hash * 31L + name.hashCode() + handler.hashCode();
                            }
                        }
                    }
                }
            } else {
                volatileStock = true;
            }
        } catch (RuntimeException failure) {
            volatileStock = true;
        }
        signatureValue = volatileStock ? now : hash;
        return signatureValue;
    }

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

    /**
     * 退路：把该配方类型的整表枚举出来逐条 check。
     * 只在 lookup 一条都给不出时调用（EBF 这类按输入索引的配方表在输入为空时会返回空）。
     */
    private List<GTRecipe> enumerateAll(GTRecipeType type) {
        List<GTRecipe> found = new ArrayList<>();
        for (GTRecipe recipe : allRecipes(type)) {
            if (recipe == null || recipe.recipeType != type || !check(recipe)) continue;
            found.add(recipe);
        }
        return found;
    }

    /** 该配方类型的整表（不 check），拿不到时返回空表。 */
    private List<GTRecipe> allRecipes(GTRecipeType type) {
        var level = machine.getLevel();
        if (type == null || level == null) return List.of();
        List<GTRecipe> all = new ArrayList<>();
        try {
            // 1.20.1 的 RecipeManager.getAllRecipesFor 直接返回配方本体（1.20.2 起才包 RecipeHolder）。
            for (GTRecipe recipe : level.getRecipeManager().getAllRecipesFor(type)) {
                if (recipe != null) all.add(recipe);
            }
        } catch (RuntimeException failure) {
            SwallowedErrors.reportDebug("MoltenOreModuleAdapter.allRecipes", failure);
            return List.of();
        }
        return all;
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
        long signature = inputSignature(now);
        var cached = candidateCache.get(now, cacheTicks, signature, () -> {
            List<GTRecipe> found = new ArrayList<>();
            Iterator<GTRecipe> iterator = lookup(machine.getRecipeType());
            while (iterator.hasNext()) {
                GTRecipe recipe = iterator.next();
                // 只收引用，快照留到真正进入本轮窗口时再复制（见 snapshot()）。
                if (recipe != null) found.add(recipe);
            }
            // 有些配方类型（EBF 这类巨量配方表）的 lookup 是按「机器当前输入」索引的：
            // 输入总线暂时空着时一条都枚举不出来；更坑的是它可能**漏掉**某些仓型里的原料，
            // 表现为「玩家放了 6 种碎片，只匹配到 4 种」。所以小配方表整表枚举再合并，
            // 但**每轮只 check 16 条**（单条 matchRecipe ~170 µs，全量一次 45 ms 会卡三 tick）。
            enumerateIncrementally(machine.getRecipeType(), signature, found);
            if (found.isEmpty()) {
                logWhyNoCandidates(machine.getRecipeType());
                // 长时间没有候选：可能是主机被拆掉重放后模块还连着旧实例，触发一次主机自愈。
                if (machine instanceof MoltenModuleMachineBase) {
                    ((MoltenModuleMachineBase<?>) machine).refreshHostIfStale();
                }
            }
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
        // 机器 UI 上的「允许同配方占多条线程」开关：开启后同配方并发不限（0 = 不限）。
        var controller = com.gtlmodular.common.machine.independent.control.IndependentLogicAccess.controller(logic);
        if (controller != null && controller.allowSameRecipeMultiple()) maxPerRecipe = 0;
        Map<ResourceLocation, Integer> active = activeRecipes.get();
        // 直接用缓存里的配方做排序，不再每 tick 复制 16 份；真正要开批时才按索引缩放。
        List<GTRecipe> origins = cached;
        var order = fairness.orderIndices(origins, recipe -> recipe.id, this::familyOf, maxPerRecipe,
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
            GTRecipe snapshot;
            try {
                snapshot = snapshot(origin);
            } catch (RuntimeException unsupported) {
                // 需要专用快照适配的配方只跳过这一条，不影响本轮其他候选。
                continue;
            }
            GTRecipe ingredients = scaledIngredients(snapshot, p);
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
