package com.gtlmodular.common.machine.independent.control;

import com.gtlmodular.common.machine.independent.eligibility.ThreadEligibilitySettings;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public final class IndependentSettingsReload {
    private final AtomicBoolean pending = new AtomicBoolean();
    private volatile ThreadEligibilitySettings current = new ThreadEligibilitySettings(false, Set.of(), Set.of());

    public ThreadEligibilitySettings current() { return current; }

    public void request() { pending.set(true); }

    public boolean publishPending(Supplier<ThreadEligibilitySettings> source) {
        // 先原子取走通知，读取配置期间到达的下一次通知才能留到下个 tick。
        if (!pending.getAndSet(false)) return false;
        try {
            current = source.get();
            return true;
        } catch (RuntimeException failure) {
            pending.set(true);
            throw failure;
        }
    }

    public void serverStopped() {
        current = new ThreadEligibilitySettings(false, Set.of(), Set.of());
        request();
    }
}
