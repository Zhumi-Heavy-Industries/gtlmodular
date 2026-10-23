package com.gtlmodular.common.machine.independent.core;

import java.util.Objects;

public interface IndependentBatchOperations<P> {
    // 候选阶段不能有 IO，否则账本还没接管就可能失去已扣料工作的记录。
    Candidate<P> findBatch(int availableSlots, long availableBudget);

    /** 准入被拒时回传原因，供显示/日志定位；默认忽略。 */
    default void rejectAdmission(String reason) {}

    // false 必须表示未发生实际扣料；无法确定部分执行结果时抛异常，由会话保留候选并暂停。
    boolean consumeInputs(P payload);

    enum InputOwnership { NONE, CONSUMED, UNCERTAIN }

    // 没有阶段日志的接入方仍须保守保留；有日志时以真实扣料阶段为准。
    default InputOwnership inputOwnership(P payload) { return InputOwnership.UNCERTAIN; }

    boolean tickInputs(P payload);

    // 部分出料和完成钩子的阶段必须回写 payload，重试和保存才不会重放已经成功的部分。
    // 只有产物和钩子均已结清才能返回 true；结果不明的异常不能当成可安全重试的 false。
    boolean settleOutputs(P payload);

    record Candidate<P>(P payload, int duration, long budget) {
        public Candidate {
            Objects.requireNonNull(payload);
            if (duration < 1 || budget < 0) throw new IllegalArgumentException("Invalid candidate accounting");
        }
    }
}
