package com.gtlmodular.common.machine.independent.core;

import com.gtlmodular.common.thread.BatchPolicy;
import com.gtlmodular.common.thread.BatchState;
import com.gtlmodular.common.thread.ConcurrentBatchEngine;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

import static com.gtlmodular.common.machine.independent.core.IndependentSessionSnapshot.Phase;
import static com.gtlmodular.common.machine.independent.core.IndependentSessionState.*;

public final class IndependentBatchSession<P, U> {
    private ConcurrentBatchEngine<OwnedBatch, OwnedBatch> engine;
    private final List<OwnedBatch> owned = new ArrayList<>();
    private final List<IndependentSessionSnapshot.Retained<U>> retained = new ArrayList<>();
    private final Map<UUID, IndependentSessionSnapshot.Retained<U>> retainedRecovery = new LinkedHashMap<>();
    private final BatchBudgetLedger<OwnedBatch> budget = new BatchBudgetLedger<>();
    private boolean drainRequested;
    private boolean ticking;
    private IndependentSessionState state = UPSTREAM;

    public IndependentSessionState state() {
        return state;
    }

    public boolean hasOwnedWork() {
        return !owned.isEmpty() || !retained.isEmpty();
    }

    public boolean drainRequested() {
        return drainRequested;
    }

    public BigInteger occupiedBudget() {
        return budget.occupied();
    }

    public List<IndependentSessionSnapshot.Batch<P>> batches() {
        return owned.stream().map(batch -> batch.snapshot(Function.identity())).toList();
    }

    public void updateUnstartedDuration(P payload, int duration) {
        requireOutsideTick();
        var batch = owned.stream().filter(entry -> entry.work == payload).findFirst().orElseThrow();
        if (duration < 1 || batch.progress != 0 || batch.phase != Phase.RUNNING || batch.failure != null) {
            throw new IllegalStateException("Cannot change a running batch clock");
        }
        batch.duration = duration;
    }

    public List<IndependentSessionSnapshot.Retained<U>> retainedRecords() {
        return List.copyOf(retained);
    }

    public void requestDrain() {
        if (hasOwnedWork()) drainRequested = true;
    }

    public void retain(U raw, String reason) {
        var entry = new IndependentSessionSnapshot.Retained<>(raw, reason);
        retained.add(entry);
        retainedRecovery.put(UUID.randomUUID(), entry);
        state = PAUSED;
    }

    // 临时令牌随加载重新生成，避免管理员看过的序号因其他批次完成而指向另一批。
    public Map<UUID, IndependentSessionSnapshot.Batch<P>> failedBatches() {
        Map<UUID, IndependentSessionSnapshot.Batch<P>> result = new LinkedHashMap<>();
        for (var batch : owned) if (batch.failure != null) result.put(batch.recoveryId, batch.snapshot(Function.identity()));
        return result;
    }

    public Map<UUID, IndependentSessionSnapshot.Retained<U>> retainedRecoveryRecords() {
        return Map.copyOf(retainedRecovery);
    }

    public void retry(UUID token) {
        requireOutsideTick();
        var batch = failedBatch(token);
        if (batch.phase == Phase.INPUT_UNCERTAIN) throw new IllegalArgumentException("输入扣料结果不明，不能自动重扣或视为已扣清");
        batch.recoveryId = UUID.randomUUID();
        batch.failure = null;
        refreshState();
    }

    // 只供管理员在外部核账结清后确认；此处不能自动补偿、退款或再次产出。
    public void acknowledgeSettlement(UUID token) {
        requireOutsideTick();
        var raw = retainedRecovery.remove(token);
        if (raw != null) retained.removeIf(entry -> entry == raw);
        else {
            release(failedBatch(token));
            if (engine != null) {
                engine.clear();
                for (var batch : owned) if (batch.phase == Phase.RUNNING) engine.restoreBatch(batch);
            }
        }
        refreshState();
    }

    private OwnedBatch failedBatch(UUID token) {
        return owned.stream().filter(batch -> batch.recoveryId.equals(token) && batch.failure != null)
                .findFirst().orElseThrow(() -> new IllegalArgumentException("没有对应的故障批次；请重新查看 status"));
    }

    // 编码器必须复制可变配方、余量和原始记录，避免保存后的快照被后续 IO 改写。
    public <E, V> IndependentSessionSnapshot<E, V> snapshot(Function<? super P, E> encodePayload,
                                                           Function<? super U, V> encodeRaw) {
        requireOutsideTick();
        return new IndependentSessionSnapshot<>(drainRequested,
                owned.stream().map(batch -> batch.snapshot(encodePayload)).toList(),
                retained.stream().map(entry -> new IndependentSessionSnapshot.Retained<>(
                        encodeRaw.apply(entry.raw()), entry.reason())).toList());
    }

    // 只接收已经解码、独立持有的 payload；恢复不能重新扣料、裁剪槽数或跳过坏记录。
    public static <P, U> IndependentBatchSession<P, U> restore(IndependentSessionSnapshot<P, U> snapshot) {
        var session = new IndependentBatchSession<P, U>();
        session.drainRequested = snapshot.drainRequested();
        for (var entry : snapshot.batches()) {
            session.requireDistinctPayload(entry.payload());
            var batch = session.new OwnedBatch(entry);
            session.owned.add(batch);
            session.budget.restore(batch, batch.reservation);
            if (batch.phase == Phase.RUNNING) session.engine().restoreBatch(batch);
        }
        for (var entry : snapshot.retained()) session.retain(entry.raw(), entry.reason());
        session.refreshState();
        return session;
    }

    public TickResult tick(IndependentSessionControl control, IndependentBatchOperations<P> operations) {
        Objects.requireNonNull(control);
        requireOutsideTick();
        if (!control.admissionAllowed()) requestDrain();
        if (!hasOwnedWork()) {
            drainRequested = false;
            if (!control.admissionAllowed()) return passThrough(UPSTREAM);
            if (control.upstreamOwnsWork()) return passThrough(WAITING_FOR_UPSTREAM);
            if (!control.canRun()) return passThrough(PAUSED);
        }
        if (!control.canRun() || (hasFailure() && !hasRunnableBatch())) {
            state = PAUSED;
            return new TickResult(state, true, false);
        }

        Objects.requireNonNull(operations);
        ticking = true;
        state = drainRequested ? DRAINING : RUNNING;
        boolean progressed;
        try {
            progressed = engine().tick(new Policy(control, operations));
        } finally {
            ticking = false;
            refreshState();
        }
        // 收尾完成的这一 tick 仍属于本会话，上游只能在之后的 tick 接手。
        return new TickResult(state, true, progressed);
    }

    private TickResult passThrough(IndependentSessionState next) {
        state = next;
        return new TickResult(state, false, false);
    }

    private ConcurrentBatchEngine<OwnedBatch, OwnedBatch> engine() {
        if (engine == null) engine = new ConcurrentBatchEngine<OwnedBatch, OwnedBatch>(
                (batch, duration, progress) -> batch);
        return engine;
    }

    private boolean hasFailure() {
        return !retained.isEmpty() || owned.stream().anyMatch(batch -> batch.failure != null);
    }

    private boolean hasRunnableBatch() {
        return owned.stream().anyMatch(batch -> batch.failure == null && batch.phase != Phase.INPUT_UNCERTAIN);
    }

    private void refreshState() {
        if (hasFailure() && !hasRunnableBatch()) {
            state = PAUSED;
        } else if (drainRequested && hasOwnedWork()) {
            state = DRAINING;
        } else if (hasOwnedWork()) {
            state = RUNNING;
        } else if (drainRequested || state != RUNNING) {
            drainRequested = false;
            state = UPSTREAM;
        }
    }

    private void requireOutsideTick() {
        if (ticking) throw new IllegalStateException("Session operation during batch IO");
    }

    private void requireDistinctPayload(P payload) {
        if (owned.stream().anyMatch(batch -> batch.work == payload)) {
            throw new IllegalArgumentException("Each batch must own a distinct payload");
        }
    }

    private void release(OwnedBatch batch) {
        budget.release(batch);
        owned.remove(batch);
    }

    public record TickResult(IndependentSessionState state, boolean cancelUpstreamTick, boolean progressed) {}

    private final class Policy implements BatchPolicy<OwnedBatch> {
        private final IndependentSessionControl control;
        private final IndependentBatchOperations<P> operations;

        private Policy(IndependentSessionControl control, IndependentBatchOperations<P> operations) {
            this.control = control;
            this.operations = operations;
        }

        @Override
        public int slots() {
            // 引擎在全部运行批次处理后、补位前询问槽数；在这里结算才能及时释放额度，且每 tick 只试一次。
            for (var batch : List.copyOf(owned)) {
                if (batch.failure != null) continue;
                if (batch.phase != Phase.PENDING_SETTLEMENT) continue;
                try {
                    if (operations.settleOutputs(batch.work)) release(batch);
                } catch (RuntimeException exception) {
                    batch.fail("settlement", exception);
                }
            }
            return control.slots();
        }

        @Override
        public OwnedBatch findBatch() {
            // 引擎会把零槽夹成一槽，且待结算已离开引擎；必须用全部所有权记录独立把守补位。
            if (drainRequested || hasFailure() || !control.admissionAllowed() || owned.size() >= control.slots()) {
                return null;
            }

            if (budget.occupied().compareTo(BigInteger.valueOf(control.budgetLimit())) > 0) {
                operations.rejectAdmission("并行预算已占满");
                return null;
            }
            long available = budget.available(control.budgetLimit());
            var candidate = operations.findBatch(control.slots() - owned.size(), available);
            if (candidate == null) {
                operations.rejectAdmission("没有候选配方");
                return null;
            }
            if (candidate.budget() > available) {
                operations.rejectAdmission("剩余并行预算不足: 需要 " + candidate.budget() + " 剩余 " + available);
                return null;
            }
            requireDistinctPayload(candidate.payload());
            return new OwnedBatch(new IndependentSessionSnapshot.Batch<>(candidate.payload(), candidate.duration(),
                    0, candidate.budget(), Phase.RUNNING, null));
        }

        @Override
        public int duration(OwnedBatch batch) {
            return batch.duration;
        }

        @Override
        public boolean consumeInputs(OwnedBatch batch) {
            if (drainRequested || hasFailure()) return false;
            if (!budget.reserve(batch, batch.reservation, control.budgetLimit())) return false;
            owned.add(batch);
            try {
                if (operations.consumeInputs(batch.work)) return true;
                release(batch);
            } catch (RuntimeException exception) {
                var input = operations.inputOwnership(batch.work);
                if (input == IndependentBatchOperations.InputOwnership.NONE) {
                    release(batch);
                    return false;
                }
                if (input == IndependentBatchOperations.InputOwnership.UNCERTAIN) batch.phase = Phase.INPUT_UNCERTAIN;
                batch.fail("input", exception);
                // 已扣清的批次仍须加入引擎，管理员重试后才能继续，不能再次扣料。
                return input == IndependentBatchOperations.InputOwnership.CONSUMED;
            }
            return false;
        }

        @Override
        public boolean tickInputs(OwnedBatch batch) {
            if (batch.failure != null) return false;
            try {
                return operations.tickInputs(batch.work);
            } catch (RuntimeException exception) {
                batch.fail("tick", exception);
                return false;
            }
        }

        @Override
        public void finishOutputs(OwnedBatch batch) {
            batch.phase = Phase.PENDING_SETTLEMENT;
        }

        @Override
        public boolean tryFinishOutputs(OwnedBatch batch) {
            if (batch.failure != null) return false;
            finishOutputs(batch);
            return true;
        }
    }

    private final class OwnedBatch implements BatchState<OwnedBatch> {
        private UUID recoveryId = UUID.randomUUID();
        private final P work;
        private int duration;
        private final long reservation;
        private int progress;
        private Phase phase;
        private String failure;

        private OwnedBatch(IndependentSessionSnapshot.Batch<P> entry) {
            work = entry.payload();
            duration = entry.duration();
            progress = entry.progress();
            reservation = entry.budget();
            phase = entry.phase();
            failure = entry.failure();
        }

        @Override public OwnedBatch payload() { return this; }
        @Override public int duration() { return duration; }
        @Override public int progress() { return progress; }
        @Override public void setProgress(int progress) { this.progress = progress; }

        private void fail(String operation, RuntimeException exception) {
            failure = operation + ": " + exception.getClass().getName() + ": " + exception.getMessage();
        }

        private <E> IndependentSessionSnapshot.Batch<E> snapshot(Function<? super P, E> encode) {
            return new IndependentSessionSnapshot.Batch<>(encode.apply(work), duration, progress,
                    reservation, phase, failure);
        }
    }
}
