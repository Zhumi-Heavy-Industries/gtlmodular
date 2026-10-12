package com.gtlmodular.common.machine.independent.runtime;

import com.gtlmodular.common.machine.independent.core.IndependentBatchOperations.Candidate;
import com.gtlmodular.common.machine.independent.persistence.BatchExecutionStage;

public interface IndependentBatchHost<P> {
    void beginTick();
    Candidate<P> findBatch(int slots, long budget);
    boolean canWork(P payload);
    boolean canAdmit();
    boolean waitFor(P payload, String reason);
    BatchExecutionStage stage(P payload);
    void stage(P payload, BatchExecutionStage stage);
    boolean prepareInputs(P payload);
    // false 只能表示零扣料；部分成功或结果不明必须抛异常并保留余量。
    boolean consumeInputs(P payload);
    boolean tickIo(P payload);
    void completeTick(P payload);
    boolean onWorking();
    boolean output(P payload);
    void afterWorking(P payload);
    boolean suspendAfterFinish();
}
