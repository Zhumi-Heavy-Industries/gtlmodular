package com.gtlmodular.common.thread;

public interface BatchPolicy<P> {
    int slots();

    P findBatch();

    int duration(P payload);

    boolean consumeInputs(P payload);

    boolean tickInputs(P payload);

    void finishOutputs(P payload);

    // 保留旧适配器的完成入口；需要背压的适配器只有全部输出成功才返回 true。
    default boolean tryFinishOutputs(P payload) {
        finishOutputs(payload);
        return true;
    }
}
