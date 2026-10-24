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
    private long signature = Long.MIN_VALUE;
    private boolean valid;

    /** 无签名版本：到点就重建（保持老语义，测试也锁着这条）。 */
    public List<T> get(long now, int ttlTicks, Supplier<List<T>> loader) {
        if (ttlTicks <= 0) return List.copyOf(loader.get());
        if (!valid || now >= expiresAt) {
            snapshot = List.copyOf(loader.get());
            expiresAt = now + ttlTicks;
            valid = true;
        }
        return snapshot;
    }

    /**
     * 带输入签名的缓存：签名变了才重建；只是 TTL 到期而输入没变，就延长有效期。
     * <p>
     * 整表枚举一次可能到 45 ms（60+ 条配方逐条 matchRecipe），而机器里的原料种类一分钟才变几次，
     * 所以不能按固定 TTL 无条件重扫。
     */
    public List<T> get(long now, int ttlTicks, long signature, Supplier<List<T>> loader) {
        if (ttlTicks <= 0) return List.copyOf(loader.get());
        if (!valid || signature != this.signature) {
            snapshot = List.copyOf(loader.get());
            this.signature = signature;
            expiresAt = now + ttlTicks;
            valid = true;
        } else if (now >= expiresAt) {
            expiresAt = now + ttlTicks;
        }
        return snapshot;
    }

    public void invalidate() {
        valid = false;
    }
}
