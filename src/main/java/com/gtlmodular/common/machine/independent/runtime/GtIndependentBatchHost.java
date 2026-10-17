package com.gtlmodular.common.machine.independent.runtime;

import com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gtlmodular.common.machine.independent.adapter.IndependentRecipeAdapter;
import com.gtlmodular.common.machine.independent.core.IndependentBatchOperations.Candidate;
import com.gtlmodular.common.machine.independent.core.IndependentSessionSnapshot;
import com.gtlmodular.common.machine.independent.io.BatchContents;
import com.gtlmodular.common.machine.independent.io.IndependentRecipeIo;
import com.gtlmodular.common.machine.independent.io.IndependentWirelessPower;
import com.gtlmodular.common.machine.independent.io.SharedTickPower;
import com.gtlmodular.common.machine.independent.persistence.*;
import com.gtlmodular.common.machine.independent.recipe.SingleRecipeCandidate;
import org.gtlcore.gtlcore.api.machine.ISuspendableMachine;

import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
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
    private final SharedTickPower wirelessPower = new SharedTickPower();
    private final AdmissionPower admissions = new AdmissionPower();
    private boolean searched;

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

    public void finishAdmissions(java.util.function.ObjIntConsumer<IndependentRecipePayload> updateDuration) {
        admissions.finish(updateDuration);
    }

    @Override
    public void beginTick() {
        runtime.beginTick();
        candidates.clear();
        searched = false;
        admissions.clear();
        wiredPower.reset(BigInteger.valueOf(wiredVoltage()));
        wirelessPower.reset(IndependentWirelessPower.available(machine));
    }

    private long wiredVoltage() {
        return machine instanceof WorkableElectricMultiblockMachine multiblock
                ? Math.max(0, multiblock.getOverclockVoltage()) : 0;
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
        if (!canAdmit()) return null;
        if (!searched) {
            searched = true;
            candidates.addAll(adapter.candidates(slots, budget, availablePower()));
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

    @Override
    public boolean canWork(IndependentRecipePayload payload) {
        if (!machineReady() || !adapter.allowsRunningBatches()) return waitFor(payload, "机器暂停、结构或宿主条件未满足");
        if (!payload.recipe().checkConditions(logic).isSuccess()) return waitFor(payload, "配方运行条件未满足");
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
        return runtime.waitFor(payload, reason);
    }

    @Override
    public boolean prepareInputs(IndependentRecipePayload payload) {
        var prepared = runtime.prepared(payload);
        io.requireSupported(payload.recipe());
        prepared.inputs = io.selectInput(prepared.source.ingredients(), prepared.source.ingredients().inputs);
        if (prepared.inputs == null) return waitFor(payload, "开工输入不足");
        var beforeHook = codec.encode(payload);
        if (!machine.beforeWorking(payload.recipe())) return waitFor(payload, "beforeWorking 否决");
        if (!beforeHook.equals(codec.encode(payload))) throw new IllegalArgumentException("需要改变配方的开工钩子专用适配");
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
        // 概率结果和实际余量也必须能保存，不能只验证未抽签的配方本体。
        codec.decode(codec.encode(payload));
        prepared.beforeInputs = BatchContents.copy(payload.remainders().get(INPUT));
        return io.outputFits(payload.resolvedOutputs()) && prepared.inputs.fits(payload.remainders().get(INPUT), IO.IN)
                || waitFor(payload, "开工输入或输出容量不足");
    }

    @Override
    public boolean consumeInputs(IndependentRecipePayload payload) {
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
        return true;
    }

    @Override
    public boolean tickIo(IndependentRecipePayload payload) {
        TickRoutes routes;
        try {
            routes = prepareTick(payload);
        } catch (RuntimeException exception) {
            return waitFor(payload, exception.getClass().getSimpleName() + ": " + exception.getMessage());
        }
        if (routes == null) return false;
        if (!IndependentWirelessPower.pay(machine, payload, wirelessPower)) return runtime.waitFor(payload, "等待整机无线功率或账户/终端实际扣电");
        if (!routes.input().transfer(payload.remainders().get(TICK_INPUT), IO.IN, false, wiredPower)) return runtime.waitFor(payload, "等待整机功率或本 tick 剩余输入");
        if (!routes.output().transfer(payload.remainders().get(TICK_OUTPUT), IO.OUT, false)) return runtime.waitFor(payload, "tick 输出部分完成");
        return true;
    }

    private TickRoutes prepareTick(IndependentRecipePayload payload) {
        io.requireSupported(payload.recipe());
        var in = io.tickRoute(IO.IN);
        var out = io.tickRoute(IO.OUT);
        if (!payload.remainders().containsKey(TICK_INPUT)) {
            if (!in.fitsTickInput(payload.recipe().tickInputs, wiredPower) || !out.fits(payload.recipe().tickOutputs, IO.OUT) ||
                    !IndependentWirelessPower.ready(machine, payload)) {
                runtime.waitFor(payload, "tick 输入、电量或输出容量不足");
                return null;
            }
            var inputs = BatchChanceResolver.resolve(payload.recipe(), payload, machine, IO.IN, true);
            var outputs = BatchChanceResolver.resolve(payload.recipe(), payload, machine, IO.OUT, true);
            payload.remainders().put(TICK_INPUT, inputs);
            payload.remainders().put(TICK_OUTPUT, outputs);
        }
        // 检查失败后也必须在下次真实 IO 前重新验证已确定的概率和余量，不能重抽或绕过。
        codec.decode(codec.encode(payload));
        var input = payload.remainders().get(TICK_INPUT);
        var output = payload.remainders().get(TICK_OUTPUT);
        if (!in.fitsTickInput(input, wiredPower) || !out.fits(output, IO.OUT) || !IndependentWirelessPower.ready(machine, payload)) {
            runtime.waitFor(payload, "等待完成本 tick 的剩余 IO");
            return null;
        }
        return new TickRoutes(in, out);
    }

    private record TickRoutes(IndependentRecipeIo.Route input, IndependentRecipeIo.Route output) {}

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
        if (complete) payload.remainders().remove(OUTPUT);
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
