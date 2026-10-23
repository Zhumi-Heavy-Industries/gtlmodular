package com.gtlmodular.common.machine.independent.control;

import com.gtlmodular.common.machine.independent.eligibility.ThreadEligibilitySettings;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public final class IndependentSettingsReload {
    private final AtomicBoolean pending = new AtomicBoolean();
    private volatile ThreadEligibilitySettings current = new ThreadEligibilitySettings(false, Set.of(), Set.of());
    private volatile long generation;

    public ThreadEligibilitySettings current() { return current; }

    /**
     * 设置代际号：每次成功发布 +1。机器侧可以用它判断「我缓存的资格判定还算不算数」，
     * 避免每台机器每 tick 都重新拼机器 ID、跑一遍规则求值（机器多时这部分是线性税）。
     */
    public long generation() { return generation; }

    public void request() { pending.set(true); }

    public boolean publishPending(Supplier<ThreadEligibilitySettings> source) {
        // 先原子取走通知，读取配置期间到达的下一次通知才能留到下个 tick。
        if (!pending.getAndSet(false)) return false;
        try {
            current = source.get();
            generation++;
            return true;
        } catch (RuntimeException failure) {
            pending.set(true);
            throw failure;
        }
    }

    public void serverStopped() {
        current = new ThreadEligibilitySettings(false, Set.of(), Set.of());
        generation++;
        request();
    }
}
