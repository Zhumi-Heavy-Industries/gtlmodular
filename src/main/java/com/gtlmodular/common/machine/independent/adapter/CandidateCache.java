package com.gtlmodular.common.machine.independent.adapter;

import java.util.List;
import java.util.function.Supplier;

/**
 * 候选查询的跨 tick 缓存。lookup 本身可能很贵，但上游输入变化没有统一 revision，
 * 所以只做短 TTL 缓存：过期后重查，真实扣料仍然逐条校验，缓存不会绕过输入检查。
 */
public final class CandidateCache<T> {
    private List<T> snapshot = List.of();
    private long expiresAt;
    private boolean valid;

    public List<T> get(long now, int ttlTicks, Supplier<List<T>> loader) {
        if (ttlTicks <= 0) return List.copyOf(loader.get());
        if (!valid || now >= expiresAt) {
            snapshot = List.copyOf(loader.get());
            expiresAt = now + ttlTicks;
            valid = true;
        }
        return snapshot;
    }

    public void invalidate() {
        valid = false;
    }
}
