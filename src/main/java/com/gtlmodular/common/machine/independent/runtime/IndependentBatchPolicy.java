package com.gtlmodular.common.machine.independent.runtime;

import com.gtlmodular.common.machine.independent.core.IndependentBatchOperations;
import com.gtlmodular.common.machine.independent.persistence.BatchExecutionStage;

import java.util.Objects;
import java.util.function.BooleanSupplier;

import static com.gtlmodular.common.machine.independent.persistence.BatchExecutionStage.*;

public final class IndependentBatchPolicy<P> implements IndependentBatchOperations<P> {
    private final IndependentBatchHost<P> host;
    private Boolean workingAllowed;
    private boolean stopped;

    public IndependentBatchPolicy(IndependentBatchHost<P> host) {
        this.host = Objects.requireNonNull(host);
    }

    public void beginTick() {
        workingAllowed = null;
        stopped = false;
        host.beginTick();
    }

    @Override
    public Candidate<P> findBatch(int availableSlots, long availableBudget) {
        return stopped || Boolean.FALSE.equals(workingAllowed) || !host.canAdmit()
                ? null : host.findBatch(availableSlots, availableBudget);
    }

    @Override
    public boolean consumeInputs(P payload) {
        if (host.stage(payload).input() == Input.CONSUMED) return true;
        if (host.stage(payload).input() != Input.PREPARED) throw new IllegalStateException("Uncertain input cannot replay");
        if (stopped || !preflight(payload, () -> host.canAdmit() && host.canWork(payload) && host.prepareInputs(payload))) return false;
        // 所有检查和准备均已完成；只有实际扣料结果不明才需要保留输入所有权。
        setInput(payload, Input.UNCERTAIN);
        if (!host.consumeInputs(payload)) {
            setInput(payload, Input.PREPARED);
            return false;
        }
        setInput(payload, Input.CONSUMED);
        return true;
    }

    @Override
    public InputOwnership inputOwnership(P payload) {
        return switch (host.stage(payload).input()) {
            case PREPARED -> InputOwnership.NONE;
            case CONSUMED -> InputOwnership.CONSUMED;
            case UNCERTAIN -> InputOwnership.UNCERTAIN;
        };
    }

    @Override
    public boolean tickInputs(P payload) {
        if (host.stage(payload).input() != Input.CONSUMED) throw new IllegalStateException("Unpaid running batch");
        if (stopped || Boolean.FALSE.equals(workingAllowed) || !preflight(payload, () -> host.canWork(payload)) || !host.tickIo(payload)) return false;
        // 上游先执行 tick IO 再询问机器；否决时保留已付的这一 tick，恢复后不能再扣一次。
        if (workingAllowed == null) {
            // 钩子抛异常也占用本 tick 的调用机会，不能让后续批次重复触发同一机器钩子。
            workingAllowed = false;
            workingAllowed = host.onWorking();
        }
        if (!workingAllowed) return false;
        host.completeTick(payload);
        return true;
    }

    @Override
    public boolean settleOutputs(P payload) {
        if (stopped || Boolean.FALSE.equals(workingAllowed) || !preflight(payload, () -> host.canWork(payload))) return false;
        var stage = host.stage(payload);
        if (stage.input() != Input.CONSUMED) throw new IllegalStateException("Unpaid settlement");
        if (stage.afterWorking() == Hook.IN_FLIGHT) throw new IllegalStateException("Uncertain completion hook cannot replay");
        if (stage.afterWorking() == Hook.NOT_RUN) {
            host.stage(payload, new BatchExecutionStage(stage.input(), stage.output(), Hook.IN_FLIGHT));
            host.afterWorking(payload);
            host.stage(payload, new BatchExecutionStage(stage.input(), stage.output(), Hook.DONE));
        }
        if (host.stage(payload).output() != Output.COMPLETE && !host.output(payload)) return false;
        stopped = host.suspendAfterFinish();
        return true;
    }

    private void setInput(P payload, Input input) {
        var stage = host.stage(payload);
        host.stage(payload, new BatchExecutionStage(input, stage.output(), stage.afterWorking()));
    }

    private boolean preflight(P payload, BooleanSupplier check) {
        try {
            return check.getAsBoolean();
        } catch (RuntimeException exception) {
            return host.waitFor(payload, exception.getClass().getSimpleName() + ": " + exception.getMessage());
        }
    }

    @Override
    public void rejectAdmission(String reason) {
        host.rejectAdmission(reason);
    }
}
