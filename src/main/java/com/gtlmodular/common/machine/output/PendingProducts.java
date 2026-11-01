package com.gtlmodular.common.machine.output;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;

import java.util.ArrayList;
import java.util.List;

/**
 * 有界待交付表：按 key 聚合 {@link Int128} 数量，超出容量就少接受（不静默饱和、不覆盖语义）。
 *
 * <p>这是“引擎侧 sink”的 pending：</p>
 * <ul>
 *   <li>{@link #simulateAccept} 必须反映容量与 ME 可用量，不能默认全收；</li>
 *   <li>{@link #accept} 返回<b>实际接受量</b>，差额由调用方作为余量带回；</li>
 *   <li>容量必须显式给出（条目数 / 单 key / 总量），满了就是背压；</li>
 *   <li>数量用 Int128，超过 long 不截断。</li>
 * </ul>
 */
public final class PendingProducts<K> {
    public record Capacity(int maxEntries, Int128 maxPerKey, Int128 maxTotal) {
        public Capacity {
            if (maxEntries <= 0) {
                throw new IllegalArgumentException("maxEntries 必须为正");
            }
            if (maxPerKey == null || maxPerKey.isZero()) {
                throw new IllegalArgumentException("maxPerKey 必须为正");
            }
            if (maxTotal == null || maxTotal.isZero()) {
                throw new IllegalArgumentException("maxTotal 必须为正");
            }
        }
    }

    private final Object2ObjectLinkedOpenHashMap<K, Int128> pending = new Object2ObjectLinkedOpenHashMap<>();
    private final Capacity capacity;
    private Int128 total = Int128.ZERO;

    public PendingProducts(Capacity capacity) {
        if (capacity == null) {
            throw new IllegalArgumentException("pending 必须有界");
        }
        this.capacity = capacity;
    }

    /** 模拟接受量：不改表。 */
    public Int128 simulateAccept(K key, Int128 amount) {
        if (amount == null || amount.isZero()) {
            return Int128.ZERO;
        }
        Int128 room = roomFor(key);
        return amount.compareTo(room) < 0 ? amount : room;
    }

    /** 真实接受；返回实际接受的量（差额即余量）。 */
    public Int128 accept(K key, Int128 amount) {
        if (key == null) {
            throw new IllegalArgumentException("key 不能为 null");
        }
        Int128 accepted = simulateAccept(key, amount);
        if (accepted.isZero()) {
            return Int128.ZERO;
        }
        Int128 current = pending.getOrDefault(key, Int128.ZERO);
        pending.put(key, current.add(accepted));
        total = total.add(accepted);
        return accepted;
    }

    public long acceptLong(K key, long amount) {
        Int128 accepted = accept(key, Int128.of(amount));
        return accepted.exceedsLong() ? Long.MAX_VALUE : accepted.low();
    }

    /** 从 key 里取走不超过 wanted 的量（分片交付）；返回实际取走的量。 */
    public long drain(K key, long wanted) {
        if (wanted <= 0) {
            return 0;
        }
        Int128 current = pending.get(key);
        if (current == null || current.isZero()) {
            return 0;
        }
        long chunk = Math.min(wanted, current.toLongClamped());
        if (chunk <= 0) {
            return 0;
        }
        Int128 rest = current.subtract(Int128.of(chunk));
        if (rest.isZero()) {
            pending.remove(key);
        } else {
            pending.put(key, rest);
        }
        total = total.subtract(Int128.of(chunk));
        return chunk;
    }

    public Int128 amount(K key) {
        return pending.getOrDefault(key, Int128.ZERO);
    }

    public Int128 total() {
        return total;
    }

    public boolean isEmpty() {
        return pending.isEmpty();
    }

    public int size() {
        return pending.size();
    }

    public Capacity capacity() {
        return capacity;
    }

    /** 插入顺序快照（轮转刷写用）。 */
    public List<K> keys() {
        return new ArrayList<>(pending.keySet());
    }

    public void clear() {
        pending.clear();
        total = Int128.ZERO;
    }

    private Int128 roomFor(K key) {
        Int128 current = pending.getOrDefault(key, Int128.ZERO);
        Int128 perKeyRoom = capacity.maxPerKey().subtract(current.compareTo(capacity.maxPerKey()) < 0
                ? current : capacity.maxPerKey());
        Int128 totalRoom = capacity.maxTotal().subtract(total.compareTo(capacity.maxTotal()) < 0
                ? total : capacity.maxTotal());
        if (current.isZero() && pending.size() >= capacity.maxEntries()) {
            return Int128.ZERO;
        }
        return perKeyRoom.compareTo(totalRoom) < 0 ? perKeyRoom : totalRoom;
    }
}
