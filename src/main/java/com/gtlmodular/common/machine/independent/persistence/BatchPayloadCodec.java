package com.gtlmodular.common.machine.independent.persistence;

import com.gtlmodular.common.machine.independent.core.IndependentSessionSnapshot.Batch;

import java.util.Optional;

public interface BatchPayloadCodec<P, T> {
    T encode(P payload);
    P decode(T encoded);

    // 旧编码只能供人工核账，失败后不能当作当前进度自动重放。
    default Optional<T> preserved(P payload) { return Optional.empty(); }

    default void validate(Batch<P> batch) {}
    default void validateCandidate(Batch<P> batch) { validate(batch); }
}
