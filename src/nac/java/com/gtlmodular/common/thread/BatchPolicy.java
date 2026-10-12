package com.gtlmodular.common.thread;

public interface BatchPolicy<P> {
    int slots();

    P findBatch();

    int duration(P payload);

    boolean consumeInputs(P payload);

    boolean tickInputs(P payload);

    void finishOutputs(P payload);
}
