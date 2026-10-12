package com.gtlmodular.common.machine.independent.persistence;

import com.gtlmodular.common.machine.independent.core.IndependentSessionSnapshot.Phase;

import java.util.Objects;

public record BatchExecutionStage(Input input, Output output, Hook afterWorking) {
    public enum Input { PREPARED, CONSUMED, UNCERTAIN }
    public enum Output { UNROLLED, READY, PARTIAL, COMPLETE }
    public enum Hook { NOT_RUN, IN_FLIGHT, DONE }

    public BatchExecutionStage {
        Objects.requireNonNull(input);
        Objects.requireNonNull(output);
        Objects.requireNonNull(afterWorking);
        if (input != Input.CONSUMED && (output == Output.PARTIAL || output == Output.COMPLETE || afterWorking != Hook.NOT_RUN)) {
            throw new IllegalArgumentException("Settlement before input ownership");
        }
    }

    public void validateOwned(Phase phase, String failure) {
        if (input == Input.PREPARED || (phase == Phase.INPUT_UNCERTAIN) != (input == Input.UNCERTAIN)) {
            throw new IllegalArgumentException("Inconsistent input ownership");
        }
        if (phase != Phase.PENDING_SETTLEMENT &&
                (output == Output.PARTIAL || output == Output.COMPLETE || afterWorking != Hook.NOT_RUN)) {
            throw new IllegalArgumentException("Settlement on an unfinished batch");
        }
        // 无法确认钩子是否已经产生副作用时，只能保留并等待处理，不能自动再调用一次。
        if (afterWorking == Hook.IN_FLIGHT && failure == null) throw new IllegalArgumentException("Uncertain completion hook");
    }

    public void validateOutputs(boolean resolved, boolean hasRemainder) {
        if ((output != Output.UNROLLED) != resolved || (!resolved && hasRemainder) ||
                (output == Output.COMPLETE && hasRemainder) ||
                ((output == Output.READY || output == Output.PARTIAL) && !hasRemainder)) {
            throw new IllegalArgumentException("Inconsistent output journal");
        }
    }

    public void validateTickJournal(boolean inputPresent, boolean outputPresent, boolean wirelessPaid, boolean wireless) {
        if (inputPresent != outputPresent || (inputPresent && input != Input.CONSUMED) ||
                (wirelessPaid && (!wireless || !inputPresent))) {
            throw new IllegalArgumentException("Inconsistent tick IO journal");
        }
    }

    public void requireRetryable() {
        if (input != Input.CONSUMED) throw new IllegalArgumentException("输入扣料结果不明，请核账后人工结清");
        if (afterWorking == Hook.IN_FLIGHT) throw new IllegalArgumentException("收尾钩子结果不明，不能重放，请核账后人工结清");
    }
}
