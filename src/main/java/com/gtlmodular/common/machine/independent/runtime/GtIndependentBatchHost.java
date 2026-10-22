package com.gtlmodular.common.machine.independent.runtime;

import com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gtlmodular.GTLModular;
import com.gtlmodular.common.config.GTLModularConfig;
import com.gtlmodular.common.machine.independent.adapter.IndependentRecipeAdapter;
import com.gtlmodular.common.machine.independent.core.IndependentBatchOperations.Candidate;
import com.gtlmodular.common.machine.independent.core.IndependentSessionSnapshot;
import com.gtlmodular.common.machine.independent.io.BatchContents;
import com.gtlmodular.common.machine.independent.io.IndependentEnergyMultiplier;
import com.gtlmodular.common.machine.independent.io.IndependentRecipeIo;
import com.gtlmodular.common.machine.independent.io.IndependentWirelessPower;
import com.gtlmodular.common.machine.independent.io.SharedTickPower;
import com.gtlmodular.common.machine.independent.persistence.*;
import com.gtlmodular.common.machine.independent.recipe.SingleRecipeCandidate;
import org.gtlcore.gtlcore.api.machine.ISuspendableMachine;

import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import static com.gtlmodular.common.machine.independent.persistence.BatchExecutionStage.*;
import static com.gtlmodular.common.machine.independent.persistence.IndependentRecipePayload.Channel.*;

public final class GtIndependentBatchHost implements IndependentBatchHost<IndependentRecipePayload> {
    private final RecipeLogic logic;
    private final IRecipeLogicMachine machine;
    private final IndependentRecipeAdapter adapter;
    private final IndependentRecipeIo io;
    private final IndependentBatchNbt archive = new IndependentBatchNbt();
    private final IndependentRecipePayloadCodec codec = new IndependentRecipePayloadCodec();
    private final IndependentBatchRuntime runtime = new IndependentBatchRuntime();
    private final ArrayDeque<SingleRecipeCandidate> candidates = new ArrayDeque<>();
    private final Supplier<List<IndependentSessionSnapshot.Batch<IndependentRecipePayload>>> batches;
    private final SharedTickPower wiredPower = new SharedTickPower();
    private final Set<net.minecraft.resources.ResourceLocation> missingInputLogged = new HashSet<>();
    private final Set<String> loggedWaits = new HashSet<>();
    private long lastStatsLog = Long.MIN_VALUE / 2;
    private final Set<String> tickMissLogged = new HashSet<>();
    private final SharedTickPower wirelessPower = new SharedTickPower();
    private final AdmissionPower admissions = new AdmissionPower();
    private final IndependentWorkStats stats = new IndependentWorkStats(GTLModularConfig.OTHER_MACHINE_THREADS.statsEnabled());
    private long lastCandidateScan = Long.MIN_VALUE / 2;
    private boolean lastScanHadCandidates;
    private long scanWindowNanos, scanWindowTicks;

    public GtIndependentBatchHost(RecipeLogic logic, IndependentRecipeAdapter adapter) {
        this(logic, adapter, List::of);
    }

    public GtIndependentBatchHost(RecipeLogic logic, IndependentRecipeAdapter adapter,
                                Supplier<List<IndependentSessionSnapshot.Batch<IndependentRecipePayload>>> batches) {
        this.logic = logic;
        this.machine = logic.machine;
        this.adapter = adapter;
        this.batches = batches;
        io = new IndependentRecipeIo(machine);
    }

    public IndependentBatchRuntime runtime() { return runtime; }

    private String lastReject;
    private long lastRejectTick = Long.MIN_VALUE;
    private int rejectCount;

    /** 每 40 tick 最多写一条，避免日志刷屏；同时记录累计次数便于判断是否持续被拒。 */
    public void rejectAdmission(String reason) {
        rejectCount++;
        long now = logic.getMachine().getLevel() == null ? 0L : logic.getMachine().getLevel().getGameTime();
        if (reason.equals(lastReject) && now - lastRejectTick < 40L) return;
        lastReject = reason;
        lastRejectTick = now;
        GTLModular.LOGGER.info("[independent] 准入被拒 {} {}: {}（累计 {} 次）",
                logic.getMachine().getClass().getSimpleName(), logic.getMachine().getPos(), reason, rejectCount);
    }

    public void finishAdmissions(java.util.function.ObjIntConsumer<IndependentRecipePayload> updateDuration) {
        admissions.finish(updateDuration);
    }

    public String statsSummary() {
        return stats.summary();
    }

    @Override
    public void beginTick() {
        if (stats.enabled()) {
            long now = logic.getMachine().getLevel() == null ? 0L : logic.getMachine().getLevel().getGameTime();
            if (now - lastStatsLog >= 200L) {
                lastStatsLog = now;
                String admit = admitCalls == 0 ? "0"
                        : String.format(java.util.Locale.ROOT, "%.1f", admitNanos / 1000.0D / admitCalls);
                String admitMaxText = String.format(java.util.Locale.ROOT, "%.1f", admitMax / 1000.0D);
                GTLModular.LOGGER.info("[independent] 统计 {} {}: {} | 分段总 prep={}µs（首tick={}µs/{}次） pay={}µs in={}µs out={}µs（{} 次） 快路径={} 慢={} 活跃批次={} 准入={}µs/次（{} 次 最大 {}µs）",
                        logic.getMachine().getClass().getSimpleName(), logic.getMachine().getPos(), stats.summary(),
                        profile(profPrep), profile(profFirst), profFirstCalls,
                        profile(profPay), profile(profIn), profile(profOut), profCalls, fastTicks, slowTicks,
                        batches.get() == null ? "?" : batches.get().size(), admit, admitCalls, admitMaxText
                        + String.format(java.util.Locale.ROOT, " 扫描/tick=%sµs 扣料=%sµs/次 结算=%sµs/次",
                        scanWindowTicks == 0 ? "0" : String.format(java.util.Locale.ROOT, "%.1f",
                                scanWindowNanos / 1000.0D / scanWindowTicks),
                        perCall(consumeNanos, consumeCalls), perCall(settleNanos, settleCalls)));
                profPrep = profPay = profIn = profOut = profCalls = profFirst = profFirstCalls = 0L;
                fastTicks = slowTicks = 0L;
                admitNanos = admitCalls = admitMax = 0L;
                consumeNanos = consumeCalls = consumeMax = 0L;
                settleNanos = settleCalls = settleMax = 0L;
                scanWindowNanos = scanWindowTicks = 0L;
            }
        }
        runtime.beginTick();
        admissions.clear();
        wiredPower.reset(BigInteger.valueOf(wiredVoltage()));
        wirelessPower.reset(IndependentWirelessPower.available(machine));
    }

    private long wiredVoltage() {
        if (!(machine instanceof WorkableElectricMultiblockMachine multiblock)) return 0;
        // 模块机的电存在主机电池里：允许「跨 tick 攒够再爆发」，和 NAC 模块的蓄电行为一致；
        // 只按电压×安培当每 tick 预算会让大并行批次永远开不动。
        if (machine instanceof com.gtlmodular.common.machine.MoltenModuleMachineBase<?> module) {
            var host = module.getHost();
            if (host != null && host.isFormed()) {
                long stored = Math.max(0L, host.getTotalStoredEU());
                if (stored > 0L) return stored;
            }
        }
        long voltage = Math.max(0, multiblock.getOverclockVoltage());
        long amperage = 1L;
        var container = multiblock.getEnergyContainer();
        if (container != null) amperage = Math.max(1L, container.getInputAmperage());
        // 模块机的电由主机供给：按主机能量仓的输入能力算，否则只按电压会把预算算小几个数量级。
        if (machine instanceof com.gtlmodular.common.machine.MoltenModuleMachineBase<?> module) {
            var host = module.getHost();
            if (host != null && host.isFormed()) {
                var hostContainer = host.getEnergyContainer();
                if (hostContainer != null) {
                    voltage = Math.max(voltage, hostContainer.getInputVoltage());
                    amperage = Math.max(amperage, hostContainer.getInputAmperage());
                }
            }
        }
        logPowerOnce(voltage, amperage, container);
        if (voltage <= 0L || amperage <= 0L) return 0L;
        try {
            return Math.multiplyExact(voltage, amperage);
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }

    private boolean powerLogged;

    /** 每台机器只打一次供电能力，方便对「电不够」的现场直接看数。 */
    private void logPowerOnce(long voltage, long amperage, com.gregtechceu.gtceu.api.misc.EnergyContainerList container) {
        if (powerLogged) return;
        powerLogged = true;
        long stored = 0L;
        long capacity = 0L;
        if (machine instanceof WorkableElectricMultiblockMachine multiblock) {
            var ec = multiblock.getEnergyContainer();
            if (ec != null) {
                stored = ec.getEnergyStored();
                capacity = ec.getEnergyCapacity();
            }
        }
        GTLModular.LOGGER.info("[independent] 供电 machine={} voltage={} amperage={} budget={} stored={} capacity={} pos={}",
                machine.getClass().getSimpleName(), voltage, amperage,
                amperage <= 0 ? 0 : voltage * amperage, stored, capacity, logic.getMachine().getPos());
    }

    private long availablePower() {
        return SharedTickPower.unreserved(wiredVoltage(), batches.get().stream()
                .filter(batch -> batch.phase() == IndependentSessionSnapshot.Phase.RUNNING && batch.progress() < batch.duration())
                .map(batch -> batch.payload().recipe().tickInputs.getOrDefault(EURecipeCapability.CAP, List.of()).stream()
                        .map(content -> BigInteger.valueOf((Long) content.content)).reduce(BigInteger.ZERO, BigInteger::add))
                .toList());
    }

    @Override
    public Candidate<IndependentRecipePayload> findBatch(int slots, long budget) {
        if (!canAdmit()) {
            logAdmitBlocked();
            return null;
        }
        long nowTick = logic.getMachine().getLevel() == null ? 0L : logic.getMachine().getLevel().getGameTime();
        // 候选队列跨 tick 复用；空了才重扫，且最多每 5 tick 一次（山海是每配方周期一次）。
        // 队列吃空且上次确实扫到了候选，就立刻补扫（不然会出现“停机几秒再开”的空窗）；
        // 但同一 tick 内只扫一次，且两次扫描至少隔 5 tick，避免刷屏扫描。
        boolean urgent = lastScanHadCandidates;
        if (candidates.isEmpty()
                && (urgent || nowTick - lastCandidateScan >= 20L)
                && nowTick - lastCandidateScan >= 5L) {
            lastCandidateScan = nowTick;
            long start = stats.enabled() ? System.nanoTime() : 0L;
            var found = adapter.candidates(slots, budget, availablePower());
            if (start != 0L) {
                long spent = System.nanoTime() - start;
                stats.scan(spent, found.size());
                scanWindowNanos += spent;
                scanWindowTicks++;
            }
            lastScanHadCandidates = !found.isEmpty();
            candidates.addAll(found);
        }
        while (!candidates.isEmpty()) {
            var source = candidates.removeFirst();
            if (source.allocation().budget() > budget) continue;
            try {
                io.requireSupported(source.ingredients());
                io.requireSupported(source.execution());
                IndependentWirelessPower.handler(machine, source.power());
                var payload = new IndependentRecipePayload(source.execution(), source.power(), new IdentityHashMap<>(),
                        null, new EnumMap<>(IndependentRecipePayload.Channel.class),
                        new BatchExecutionStage(Input.PREPARED, Output.UNROLLED, Hook.NOT_RUN));
                var candidate = archive.persistence().prepare(payload, source.execution().duration, source.allocation().budget());
                runtime.prepare(candidate.payload(), source);
                return candidate;
            } catch (IllegalArgumentException exception) {
                runtime.reject(exception.getMessage());
            }
        }
        return null;
    }

    @Override
    public boolean canAdmit() { return machineReady() && adapter.allowsNewBatches(); }

    private String lastBlocked;
    private long lastBlockedAt = Long.MIN_VALUE;

    /** 准入被前置条件挡住时说清是哪一项：机器就绪 / 模块成形 / 主机成形 / 主机开关。 */
    private void logAdmitBlocked() {
        long now = logic.getMachine().getLevel() == null ? 0L : logic.getMachine().getLevel().getGameTime();
        String detail = "machineReady=" + machineReady()
                + " moduleFormed=" + (machine instanceof com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine mb && mb.isFormed())
                + " host=" + hostDetail()
                + " newBatchesAllowed=" + adapter.allowsNewBatches();
        if (detail.equals(lastBlocked) && now - lastBlockedAt < 40L) return;
        lastBlocked = detail;
        lastBlockedAt = now;
        GTLModular.LOGGER.info("[independent] 准入被前置条件挡住 {} {}: {}",
                logic.getMachine().getClass().getSimpleName(), logic.getMachine().getPos(), detail);
    }

    private String hostDetail() {
        if (!(machine instanceof com.gtlmodular.common.machine.MoltenModuleMachineBase<?> module)) return "n/a";
        var host = module.getHost();
        if (host == null) return "null";
        return "formed=" + host.isFormed() + " working=" + host.isWorkingEnabled();
    }

    private final java.util.IdentityHashMap<IndependentRecipePayload, Long> canWorkTrueAt = new java.util.IdentityHashMap<>();

    public boolean canWork(IndependentRecipePayload payload) {
        if (!machineReady() || !adapter.allowsRunningBatches()) return waitFor(payload, "机器暂停、结构或宿主条件未满足");
        long now = logic.getMachine().getLevel() == null ? 0L : logic.getMachine().getLevel().getGameTime();
        Long cached = canWorkTrueAt.get(payload);
        // 条件通过的结果缓存 5 tick：每秒 50 条批次 × 每 tick 都验条件，是现在剩下的大头。
        if (cached != null && now - cached < 5L) return true;
        if (!payload.recipe().checkConditions(logic).isSuccess()) {
            canWorkTrueAt.remove(payload);
            return waitFor(payload, "配方运行条件未满足");
        }
        canWorkTrueAt.put(payload, now);
        return true;
    }

    private boolean machineReady() {
        return machine.isWorkingEnabled() && machine.isRecipeLogicAvailable() &&
                machine instanceof WorkableElectricMultiblockMachine multiblock && multiblock.isFormed();
    }

    @Override public BatchExecutionStage stage(IndependentRecipePayload payload) { return payload.stage(); }
    @Override public void stage(IndependentRecipePayload payload, BatchExecutionStage stage) { payload.setStage(stage); }

    @Override
    public boolean waitFor(IndependentRecipePayload payload, String reason) {
        // 未扣料候选会被会话释放，仍需让机器面板显示本轮未开工的原因。
        if (payload.stage().input() == Input.PREPARED) runtime.reject(reason);
        logWaitOnce(payload, reason);
        return runtime.waitFor(payload, reason);
    }

    /** 每种等待原因首次出现时写一行服务端日志，面板上的字和日志里的字能对上。 */
    private void logWaitOnce(IndependentRecipePayload payload, String reason) {
        if (reason == null || !loggedWaits.add(reason)) return;
        var pos = logic.getMachine().getPos();
        var level = logic.getMachine().getLevel();
        GTLModular.LOGGER.info("[independent] 等待 {} {}: {} ({})",
                level == null ? "?" : level.dimension().location(), pos, reason, payload.recipe().id);
    }

    /** 选不到输入路线时，把配方真实要的输入和机器现有输入写给服务端日志；每条配方只写一次。 */
    private void logMissingInputs(IndependentRecipePayload payload,
                                  com.gregtechceu.gtceu.api.recipe.GTRecipe ingredients) {
        try {
            if (!missingInputLogged.add(payload.recipe().id)) return;
            StringBuilder need = new StringBuilder();
            for (var entry : ingredients.inputs.entrySet()) {
                for (var content : entry.getValue()) {
                    need.append(entry.getKey().getClass().getSimpleName())
                            .append('x').append(BatchContents.amount(entry.getKey(), content.content))
                            .append(' ');
                }
            }
            StringBuilder have = new StringBuilder();
            for (var row : new IO[]{IO.IN, IO.BOTH}) {
                machine.getCapabilitiesProxy().row(row).forEach((cap, handlers) -> {
                    for (var handler : handlers) {
                        have.append(handler.getClass().getSimpleName())
                                .append('/').append(cap.getClass().getSimpleName())
                                .append(' ');
                    }
                });
            }
            GTLModular.LOGGER.info("[independent] 开工输入不足 recipe={} 需要=[{}] 机器输入=[{}]",
                    payload.recipe().id, need, have);
        } catch (RuntimeException ignored) {
        }
    }

    private long admitNanos, admitCalls, admitMax;
    private record CachedRoute(long tick, IndependentRecipeIo.InputRoute route) {}
    private final java.util.Map<String, CachedRoute> inputRouteCache = new java.util.HashMap<>();

    /** 钩子前后比的轻量签名：id + 时长 + 并行 + 四组内容条数。 */
    private static String recipeSignature(com.gregtechceu.gtceu.api.recipe.GTRecipe recipe) {
        return recipe.id + "|" + recipe.duration + "|" + recipe.parallels
                + "|" + recipe.inputs.size() + "|" + recipe.outputs.size()
                + "|" + recipe.tickInputs.size() + "|" + recipe.tickOutputs.size();
    }

    public boolean prepareInputs(IndependentRecipePayload payload) {
        long t0 = stats.enabled() ? System.nanoTime() : 0L;
        try {
            return prepareInputsInternal(payload);
        } finally {
            if (t0 != 0L) {
                long spent = System.nanoTime() - t0;
                admitNanos += spent;
                admitCalls++;
                admitMax = Math.max(admitMax, spent);
            }
        }
    }

    private boolean prepareInputsInternal(IndependentRecipePayload payload) {
        var prepared = runtime.prepared(payload);
        // 准入里的输入路线要物化 ME 库存（实测 600~700 µs/次）；同一配方 + 同并行短缓存复用。
        String routeKey = payload.recipe().id + "|" + payload.recipe().parallels;
        long nowTick = logic.getMachine().getLevel() == null ? 0L : logic.getMachine().getLevel().getGameTime();
        var cachedRoute = inputRouteCache.get(routeKey);
        if (cachedRoute != null && nowTick - cachedRoute.tick() < 40L) {
            prepared.inputs = cachedRoute.route();
        } else {
            io.requireSupported(payload.recipe());
            prepared.inputs = io.selectInput(prepared.source.ingredients(), prepared.source.ingredients().inputs);
            if (prepared.inputs != null) inputRouteCache.put(routeKey, new CachedRoute(nowTick, prepared.inputs));
        }
        if (prepared.inputs == null) {
            logMissingInputs(payload, prepared.source.ingredients());
            return waitFor(payload, "开工输入不足");
        }
        // 完整 NBT 前后比对每次要几百微秒；改用轻量签名只防「钩子换掉配方/内容数量」这类越界改写。
        String beforeHook = recipeSignature(payload.recipe());
        if (!machine.beforeWorking(payload.recipe())) return waitFor(payload, "beforeWorking 否决");
        if (!beforeHook.equals(recipeSignature(payload.recipe()))) {
            throw new IllegalArgumentException("需要改变配方的开工钩子专用适配");
        }
        if (!canWork(payload)) return false;
        // 钩子可能触发库存监听或结构变化，不能把前一次模拟当成扣料承诺。
        io.requireSupported(payload.recipe());
        prepared.inputs = io.selectInput(prepared.source.ingredients(), prepared.source.ingredients().inputs);
        if (prepared.inputs == null) return waitFor(payload, "开工钩子后输入不足");
        if (payload.stage().output() == Output.UNROLLED) {
            var output = BatchChanceResolver.resolve(prepared.source.ingredients(), payload, machine, IO.OUT, false);
            payload.setResolvedOutputs(output);
            payload.remainders().put(OUTPUT, BatchContents.copy(output));
            payload.setStage(new BatchExecutionStage(Input.PREPARED, Output.READY, Hook.NOT_RUN));
            payload.remainders().put(INPUT, BatchChanceResolver.resolve(prepared.source.ingredients(), payload, machine, IO.IN, false));
        }
        prepared.beforeInputs = BatchContents.copy(payload.remainders().get(INPUT));
        boolean outputFits = io.outputFits(payload.resolvedOutputs());
        boolean inputFits = prepared.inputs.fits(payload.remainders().get(INPUT), IO.IN);
        return outputFits && inputFits
                || waitFor(payload, "开工输入或输出容量不足: out=" + outputFits + " in=" + inputFits);
    }

    private long settleNanos, settleCalls, settleMax;
    private long consumeNanos, consumeCalls, consumeMax;

    @Override
    public boolean consumeInputs(IndependentRecipePayload payload) {
        long t0 = stats.enabled() ? System.nanoTime() : 0L;
        try {
            return consumeInputsInternal(payload);
        } finally {
            if (t0 != 0L) {
                long spent = System.nanoTime() - t0;
                consumeNanos += spent;
                consumeCalls++;
                consumeMax = Math.max(consumeMax, spent);
            }
        }
    }

    private boolean consumeInputsInternal(IndependentRecipePayload payload) {
        var remaining = payload.remainders().get(INPUT);
        var prepared = runtime.prepared(payload);
        var before = prepared.beforeInputs;
        var route = prepared.inputs;
        if (!route.transfer(remaining, IO.IN, false)) {
            // 比较序列化内容之外的数量即可判断是否已有真实扣料；未知异常由会话保留。
            for (var entry : before.entrySet()) {
                var after = remaining.get(entry.getKey());
                for (int i = 0; i < entry.getValue().size(); i++) {
                    if (BatchContents.amount(entry.getKey(), entry.getValue().get(i).content) !=
                            BatchContents.amount(entry.getKey(), after.get(i).content)) {
                        throw new IllegalStateException("开工输入部分成功，保留余量等待人工处理");
                    }
                }
            }
            return false;
        }
        payload.remainders().remove(INPUT);
        admissions.add(payload, prepared.source);
        runtime.consumed(payload);
        adapter.admitted(payload.recipe());
        return true;
    }

    private long profPrep, profPay, profIn, profOut, profCalls, profFirst, profFirstCalls;
    private long fastTicks, slowTicks;

    /** 模块机 + 纯 EU tick：直接扣主机电池，跳过内容表复制、路由和处理器遍历。 */
    private boolean payModuleTick(IndependentRecipePayload payload,
                                  com.gtlmodular.common.machine.MoltenModuleMachineBase<?> module,
                                  java.util.Map<com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability<?>,
                                          java.util.List<com.gregtechceu.gtceu.api.recipe.content.Content>> input) {
        long cost = 0L;
        for (var content : input.getOrDefault(EURecipeCapability.CAP, java.util.List.of())) {
            if (content.content instanceof Long value) cost += Math.max(0L, value);
        }
        if (cost <= 0L) return true;
        var host = module.getHost();
        long stored = host == null ? 0L : Math.max(0L, host.getTotalStoredEU());
        if (stored < cost) {
            runtime.waitFor(payload, "蓄电中（" + com.gregtechceu.gtceu.utils.FormattingUtil.formatNumbers(stored)
                    + " / " + com.gregtechceu.gtceu.utils.FormattingUtil.formatNumbers(cost) + " EU）");
            return false;
        }
        if (!wiredPower.reserve(java.math.BigInteger.valueOf(cost)) || !host.consumeEU(cost)) {
            runtime.waitFor(payload, "等待整机功率或本 tick 剩余输入");
            return false;
        }
        for (var content : input.get(EURecipeCapability.CAP)) content.content = 0L;
        return true;
    }

    private static boolean euOnlyTickInput(java.util.Map<com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability<?>,
            java.util.List<com.gregtechceu.gtceu.api.recipe.content.Content>> input) {
        if (input == null || input.isEmpty()) return true;
        return input.size() == 1 && input.containsKey(EURecipeCapability.CAP);
    }

    @Override
    public boolean tickIo(IndependentRecipePayload payload) {
        long start = stats.enabled() ? System.nanoTime() : 0L;
        long p0 = start;
        try {
            TickRoutes routes;
            try {
                routes = prepareTick(payload);
            } catch (RuntimeException exception) {
                return waitFor(payload, exception.getClass().getSimpleName() + ": " + exception.getMessage());
            }
            if (routes == null) return false;
            // 纯 EU tick：prepare 已把本 tick 内容算好，直接扣主机电池，跳过路由遍历与内容搬运。
            var fastIn = payload.remainders().get(TICK_INPUT);
            var fastOut = payload.remainders().get(TICK_OUTPUT);
            if (machine instanceof com.gtlmodular.common.machine.MoltenModuleMachineBase<?> module
                    && euOnlyTickInput(fastIn) && (fastOut == null || fastOut.isEmpty())) {
                fastTicks++;
                boolean paid = payModuleTick(payload, module, fastIn);
                if (stats.enabled()) {
                    long done = System.nanoTime();
                    profIn += done - (stats.enabled() ? System.nanoTime() : 0L);
                    profCalls++;
                }
                return paid;
            }
            if (fastIn != null) {
                slowTicks++;
                if (slowTicks == 1) {
                    GTLModular.LOGGER.info("[independent] tick 布局: in={} out={}（快路径未命中）",
                            fastIn.keySet(), fastOut == null ? "null" : fastOut.keySet());
                }
            }
            long p1 = stats.enabled() ? System.nanoTime() : 0L;
            if (!IndependentWirelessPower.pay(machine, payload, wirelessPower)) return runtime.waitFor(payload, "等待整机无线功率或账户/终端实际扣电");
            long p2 = stats.enabled() ? System.nanoTime() : 0L;
            if (!routes.input().transfer(payload.remainders().get(TICK_INPUT), IO.IN, false, wiredPower)) return runtime.waitFor(payload, "等待整机功率或本 tick 剩余输入");
            long p3 = stats.enabled() ? System.nanoTime() : 0L;
            if (!routes.output().transfer(payload.remainders().get(TICK_OUTPUT), IO.OUT, false)) return runtime.waitFor(payload, "tick 输出部分完成");
            long p4 = stats.enabled() ? System.nanoTime() : 0L;
            profPrep += p1 - p0;
            profPay += p2 - p1;
            profIn += p3 - p2;
            profOut += p4 - p3;
            profCalls++;
            return true;
        } finally {
            if (start != 0L) stats.tick(System.nanoTime() - start);
        }
    }

    private IndependentRecipeIo.Route cachedTickIn;
    private IndependentRecipeIo.Route cachedTickOut;
    private long cachedTickRoutesAt = Long.MIN_VALUE;

    /** 路由要遍历全部仓室并排序，之前每条批次每 tick 重建一次；同一台机 20 tick 内复用。 */
    private IndependentRecipeIo.Route tickRoute(IO io) {
        long now = logic.getMachine().getLevel() == null ? 0L : logic.getMachine().getLevel().getGameTime();
        if (now - cachedTickRoutesAt >= 20L || cachedTickIn == null || cachedTickOut == null) {
            cachedTickIn = this.io.tickRoute(IO.IN);
            cachedTickOut = this.io.tickRoute(IO.OUT);
            cachedTickRoutesAt = now;
        }
        return io == IO.IN ? cachedTickIn : cachedTickOut;
    }

    private TickRoutes prepareTick(IndependentRecipePayload payload) {
        // 配方准入时已经做过一次完整校验；每 tick 再遍历全部内容纯属浪费。
        var in = tickRoute(IO.IN);
        var out = tickRoute(IO.OUT);
        long f0 = stats.enabled() ? System.nanoTime() : 0L;
        if (!payload.remainders().containsKey(TICK_INPUT)) {
            var cached = tickCache.get(payload);
            if (cached != null) {
                payload.remainders().put(TICK_INPUT, BatchContents.copy((java.util.Map) cached[0]));
                payload.remainders().put(TICK_OUTPUT, BatchContents.copy((java.util.Map) cached[1]));
            }
        }
        if (!payload.remainders().containsKey(TICK_INPUT)) {
            var tickInputs = scaledTickInputs(payload.recipe().tickInputs);
            // 纯 EU tick 不走 Route 模拟：tickIo 的快路径会按主机电池复核并实际扣电。
            boolean tickIn = euOnlyTickInput(tickInputs) || in.fitsTickInput(tickInputs, wiredPower);
            boolean tickOut = out.fits(payload.recipe().tickOutputs, IO.OUT);
            boolean wireless = IndependentWirelessPower.ready(machine, payload);
            if (!tickIn || !tickOut || !wireless) {
                logTickMissOnce(payload, tickIn, tickOut, wireless, tickInputs);
                runtime.waitFor(payload, tickMissReason(tickIn, tickOut, wireless, tickInputs));
                return null;
            }
            var inputs = BatchChanceResolver.resolve(payload.recipe(), payload, machine, IO.IN, true);
            scaleEnergy(inputs);
            var outputs = BatchChanceResolver.resolve(payload.recipe(), payload, machine, IO.OUT, true);
            payload.remainders().put(TICK_INPUT, inputs);
            payload.remainders().put(TICK_OUTPUT, outputs);
            // tick 内容是确定的就缓存下来：每一 tick 都重抽/重缩放是现在单批 10µs 的主要来源。
            if (deterministicTick(payload.recipe())) {
                tickCache.put(payload, new java.util.Map[]{BatchContents.copy(inputs), BatchContents.copy(outputs)});
            }
            // 存档时归档编码本身会校验；tick 路径不再做整份 encode/decode。
            if (stats.enabled()) {
                profFirst += System.nanoTime() - f0;
                profFirstCalls++;
            }
        }
        var input = payload.remainders().get(TICK_INPUT);
        var output = payload.remainders().get(TICK_OUTPUT);
        if (machine instanceof com.gtlmodular.common.machine.MoltenModuleMachineBase<?>
                && euOnlyTickInput(input) && (output == null || output.isEmpty())) {
            // 纯 EU：交给 tickIo 的快路径直接扣电池，不再做一次 Route 模拟。
            return new TickRoutes(in, out);
        }
        if (!in.fitsTickInput(input, wiredPower) || !out.fits(output, IO.OUT) || !IndependentWirelessPower.ready(machine, payload)) {
            runtime.waitFor(payload, "等待完成本 tick 的剩余 IO");
            return null;
        }
        return new TickRoutes(in, out);
    }

    /** tick 准入失败时把三项分别写清楚：输入/电、输出、无线，一次一条日志。 */
    private void logTickMissOnce(IndependentRecipePayload payload, boolean tickIn, boolean tickOut, boolean wireless,
                                 java.util.Map<com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability<?>,
                                         java.util.List<com.gregtechceu.gtceu.api.recipe.content.Content>> tickInputs) {
        String key = payload.recipe().id + "|" + tickIn + tickOut + wireless;
        if (!tickMissLogged.add(key)) return;
        StringBuilder need = new StringBuilder();
        for (var entry : tickInputs.entrySet()) {
            for (var content : entry.getValue()) {
                need.append(entry.getKey().getClass().getSimpleName()).append('x')
                        .append(BatchContents.amount(entry.getKey(), content.content)).append(' ');
            }
        }
        GTLModular.LOGGER.info("[independent] tick 不足 in={} out={} wireless={} recipe={} 需要=[{}] 位置={}",
                tickIn, tickOut, wireless, payload.recipe().id, need, logic.getMachine().getPos());
    }

    /** 模块机电不够时给出「蓄电中 x/y EU」而不是笼统的失败，玩家能看出是在攒电。 */
    private String tickMissReason(boolean tickIn, boolean tickOut, boolean wireless,
                                  java.util.Map<com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability<?>,
                                          java.util.List<com.gregtechceu.gtceu.api.recipe.content.Content>> tickInputs) {
        if (tickIn && tickOut && wireless) return "";
        var energy = tickInputs.get(EURecipeCapability.CAP);
        boolean onlyEnergy = tickInputs.size() <= 1 && energy != null;
        if (!tickIn && onlyEnergy && machine instanceof com.gtlmodular.common.machine.MoltenModuleMachineBase<?> module) {
            var host = module.getHost();
            long stored = host == null ? 0L : Math.max(0L, host.getTotalStoredEU());
            long need = 0L;
            for (var content : energy) {
                if (content.content instanceof Long value) need += Math.max(0L, value);
            }
            if (need > stored) {
                return "蓄电中（" + com.gregtechceu.gtceu.utils.FormattingUtil.formatNumbers(stored) + " / "
                        + com.gregtechceu.gtceu.utils.FormattingUtil.formatNumbers(need) + " EU）";
            }
        }
        return "tick 不足[" + (tickIn ? "" : "输入/电") + (tickOut ? "" : "输出") + (wireless ? "" : "无线") + "]";
    }

    private static String perCall(long nanos, long calls) {
        return calls == 0L ? "0" : String.format(java.util.Locale.ROOT, "%.1f", nanos / 1000.0D / calls);
    }

    private static String profile(long nanos) {
        return String.format(java.util.Locale.ROOT, "%.1f", nanos / 1000.0D);
    }

    private record TickRoutes(IndependentRecipeIo.Route input, IndependentRecipeIo.Route output) {}

    private java.util.Map<com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability<?>,
            java.util.List<com.gregtechceu.gtceu.api.recipe.content.Content>> scaledTickInputs(
            java.util.Map<com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability<?>,
                    java.util.List<com.gregtechceu.gtceu.api.recipe.content.Content>> contents) {
        var copy = BatchContents.copy(contents);
        scaleEnergy(copy);
        return copy;
    }

    private void scaleEnergy(java.util.Map<com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability<?>,
            java.util.List<com.gregtechceu.gtceu.api.recipe.content.Content>> contents) {
        if (!(machine instanceof IndependentEnergyMultiplier discount)) return;
        double multiplier = Math.max(0.0D, discount.independentEnergyMultiplier());
        if (multiplier == 1.0D) return;
        var energy = contents.get(EURecipeCapability.CAP);
        if (energy == null) return;
        for (var content : energy) {
            if (content.content instanceof Long value) {
                content.content = Math.max(0L, Math.round(value * multiplier));
            }
        }
    }

    private final java.util.IdentityHashMap<IndependentRecipePayload, java.util.Map[]> tickCache = new java.util.IdentityHashMap<>();

    /** 只有内容全是确定概率（chance=max）时才可缓存；带概率的 tick 必须每 tick 重抽。 */
    private static boolean deterministicTick(com.gregtechceu.gtceu.api.recipe.GTRecipe recipe) {
        for (var contents : java.util.List.of(recipe.tickInputs, recipe.tickOutputs)) {
            for (var list : contents.values()) {
                for (var content : list) {
                    if (content.chance < 10000) return false;
                }
            }
        }
        return true;
    }

    @Override
    public void completeTick(IndependentRecipePayload payload) {
        payload.remainders().remove(TICK_INPUT);
        payload.remainders().remove(TICK_OUTPUT);
        payload.setWirelessPaid(false);
    }

    @Override public boolean onWorking() { return machine.onWorking(); }
    @Override public void afterWorking(IndependentRecipePayload payload) { machine.afterWorking(); }

    @Override
    public boolean output(IndependentRecipePayload payload) {
        long t0 = stats.enabled() ? System.nanoTime() : 0L;
        try {
            return outputInternal(payload);
        } finally {
            if (t0 != 0L) {
                long spent = System.nanoTime() - t0;
                settleNanos += spent;
                settleCalls++;
                settleMax = Math.max(settleMax, spent);
            }
        }
    }

    private boolean outputInternal(IndependentRecipePayload payload) {
        if (payload.resolvedOutputs() == null) throw new IllegalStateException("Missing determined batch outputs");
        var remaining = payload.remainders().get(OUTPUT);
        if (remaining == null) throw new IllegalStateException("Missing output remainder");
        boolean complete;
        try {
            io.requireSupported(payload.recipe());
            complete = io.transferOutput(remaining, false);
        } catch (RuntimeException exception) {
            return waitFor(payload, exception.getClass().getSimpleName() + ": " + exception.getMessage());
        }
        if (complete) {
            payload.remainders().remove(OUTPUT);
            tickCache.remove(payload);
        }
        payload.setStage(new BatchExecutionStage(Input.CONSUMED, complete ? Output.COMPLETE : Output.PARTIAL, payload.stage().afterWorking()));
        return complete || runtime.waitFor(payload, "输出未结清，等待剩余容量");
    }

    @Override
    public boolean suspendAfterFinish() {
        if (!(machine instanceof ISuspendableMachine suspendable) || !suspendable.gtlcore$isSuspendAfterFinish()) return false;
        logic.setStatus(RecipeLogic.Status.SUSPEND);
        suspendable.gtlcore$setSuspendAfterFinish(false);
        return true;
    }
}
