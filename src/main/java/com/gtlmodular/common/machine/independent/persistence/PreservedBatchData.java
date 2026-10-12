package com.gtlmodular.common.machine.independent.persistence;

import java.util.Objects;

public record PreservedBatchData<T>(Scope scope, T value) {
    public enum Scope { ROOT, RECORD }

    public PreservedBatchData {
        Objects.requireNonNull(scope);
        Objects.requireNonNull(value);
    }
}
