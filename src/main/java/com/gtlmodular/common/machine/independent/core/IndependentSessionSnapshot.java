package com.gtlmodular.common.machine.independent.core;

import java.util.List;
import java.util.Objects;

public record IndependentSessionSnapshot<P, U>(boolean drainRequested, List<Batch<P>> batches,
                                                List<Retained<U>> retained) {
    public IndependentSessionSnapshot {
        batches = List.copyOf(batches);
        retained = List.copyOf(retained);
    }

    public enum Phase {
        RUNNING,
        PENDING_SETTLEMENT,
        INPUT_UNCERTAIN
    }

    public record Batch<P>(P payload, int duration, int progress, long budget, Phase phase, String failure) {
        public Batch {
            Objects.requireNonNull(payload);
            Objects.requireNonNull(phase);
            if (duration < 1 || progress < 0 || progress > duration || budget < 0) {
                throw new IllegalArgumentException("Invalid batch accounting");
            }
            if (phase == Phase.PENDING_SETTLEMENT && progress != duration) {
                throw new IllegalArgumentException("Unfinished settlement");
            }
            if (phase == Phase.INPUT_UNCERTAIN && (progress != 0 || failure == null)) {
                throw new IllegalArgumentException("Uncertain input must retain its failure");
            }
            if (failure != null && failure.isBlank()) throw new IllegalArgumentException("Empty failure reason");
        }
    }

    public record Retained<U>(U raw, String reason) {
        public Retained {
            Objects.requireNonNull(raw);
            Objects.requireNonNull(reason);
            if (reason.isBlank()) throw new IllegalArgumentException("Empty retention reason");
        }
    }
}
