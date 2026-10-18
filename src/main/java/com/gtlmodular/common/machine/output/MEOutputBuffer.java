package com.gtlmodular.common.machine.output;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * 引擎输出入口的适配层：把一批 {@code Ingredient / FluidIngredient} 并入有界 pending，并返回余量。
 *
 * <p>对应联合讨论稿 §8.2：调用方负责把数量装进 long 载体，inner 按运行时类型取 long；
 * 本类只处理“规范化 key → 合并 → 有界接受 → 余量”这段纯逻辑，可脱离游戏单测。</p>
 *
 * <p>规则（逐条对应 §四）：</p>
 * <ol>
 *   <li>simulate 只回答容量，不改状态；</li>
 *   <li>真实 accept 返回余量，不“写成功就清表”；</li>
 *   <li>pending 有界，满了返回余量形成背压；</li>
 *   <li>单 key 累计用 Int128，不静默饱和；</li>
 *   <li>过滤在入表前完成，被过滤的内容单独返回，由调用方决定丢弃或另走通道。</li>
 * </ol>
 */
public final class MEOutputBuffer<K, S> {
    /** 载体适配：Ingredient / FluidIngredient 的 key、long 数量、按数量重建余量。 */
    public interface Codec<K, S> {
        K keyOf(S value);

        long amountOf(S value);

        S withAmount(S value, long amount);
    }

    /** accept 的结果：没被接受的余量 + 被过滤掉的内容。 */
    public record AcceptResult<S>(List<S> remainder, List<S> filtered) {
        public boolean fullyAccepted() {
            return remainder.isEmpty();
        }
    }

    private final PendingProducts<K> pending;
    private final Codec<K, S> codec;
    private final FairPendingFlusher<K> flusher = new FairPendingFlusher<>();
    private final Predicate<S> allowed;

    public MEOutputBuffer(PendingProducts<K> pending, Codec<K, S> codec, Predicate<S> allowed) {
        if (pending == null || codec == null) {
            throw new IllegalArgumentException("pending / codec 不能为 null");
        }
        this.pending = pending;
        this.codec = codec;
        this.allowed = allowed == null ? value -> true : allowed;
    }

    public AcceptResult<S> accept(List<S> contents, boolean simulate) {
        List<S> remainder = new ArrayList<>();
        List<S> filtered = new ArrayList<>();
        if (contents == null) {
            return new AcceptResult<>(remainder, filtered);
        }
        for (S value : contents) {
            if (value == null) {
                continue;
            }
            long amount = codec.amountOf(value);
            if (amount <= 0) {
                continue;
            }
            if (!allowed.test(value)) {
                filtered.add(value);
                continue;
            }
            K key = codec.keyOf(value);
            Int128 accepted = simulate
                    ? pending.simulateAccept(key, Int128.of(amount))
                    : pending.accept(key, Int128.of(amount));
            long acceptedLong = accepted.toLongClamped();
            long left = amount - acceptedLong;
            if (left > 0) {
                remainder.add(codec.withAmount(value, left));
            }
        }
        return new AcceptResult<>(remainder, filtered);
    }

    public FairPendingFlusher.Result flush(FairPendingFlusher.Sink<K> sink,
                                           FairPendingFlusher.Budget budget,
                                           boolean simulate) {
        return flusher.flush(pending, sink, budget, simulate);
    }

    public Int128 pendingAmount() {
        return pending.total();
    }

    public int pendingEntries() {
        return pending.size();
    }

    public boolean hasPending() {
        return !pending.isEmpty();
    }
}
