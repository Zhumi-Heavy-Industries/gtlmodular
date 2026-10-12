package com.gtlmodular.common.thread;

public interface BatchState<P> {
    P payload();

    int duration();

    int progress();

    void setProgress(int progress);

    // 允许适配器沿用自己的批次对象，避免兼容门面另存一份会漂移的进度。
    @FunctionalInterface
    interface Factory<P, B extends BatchState<P>> {
        B create(P payload, int duration, int progress);
    }
}
