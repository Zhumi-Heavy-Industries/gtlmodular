package com.gtlmodular.common.machine.output;

import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;

import java.util.ArrayList;
import java.util.List;

/**
 * 产物聚合表：把“每件产物立刻写进网络”改成“先累加到一张表里，再批量刷”。
 *
 * <p>主表存每个 key 的待刷数量；单 key 超过 {@link Long#MAX_VALUE} 的部分进入溢出表，
 * 绝不回绕成负数（负库存比少刷更糟）。表本身不碰世界、不碰网络，可脱离游戏单测。</p>
 *
 * <p>可以带 {@link Capacity}：满了之后 {@link #add} 返回 0，由调用方把它当成“输出满”处理
 * （这就是背压——缓冲容量本身是机器的真实输出容量，不是凭空多出来的一层账）。</p>
 *
 * <p>顺序不保证：底层是开放寻址哈希表，刷写时不要依赖遍历顺序；需要公平请用
 * {@link FairProductFlusher}。</p>
 */
public final class ProductTable<K> {
    private final Object2LongLinkedOpenHashMap<K> amounts = new Object2LongLinkedOpenHashMap<>();
    private final Object2LongLinkedOpenHashMap<K> overflow = new Object2LongLinkedOpenHashMap<>();
    private final Capacity capacity;

    /** 一条待刷记录。 */
    public record Entry<K>(K key, long amount) {}

    /** 有界容量：条目数与单 key 总量上限。 */
    public record Capacity(int maxEntries, long maxAmount) {
        public Capacity {
            if (maxEntries <= 0) {
                throw new IllegalArgumentException("maxEntries 必须为正");
            }
            if (maxAmount <= 0) {
                throw new IllegalArgumentException("maxAmount 必须为正");
            }
        }
    }

    /** 不限容量（用于验证阶段；上线建议给容量）。 */
    public ProductTable() {
        this(null);
    }

    public ProductTable(Capacity capacity) {
        this.capacity = capacity;
    }

    /**
     * 累加。
     *
     * @return 实际接受的量；0 表示被背压拒收（表满或单 key 已到上限）
     */
    public long add(K key, long amount) {
        if (key == null) {
            throw new IllegalArgumentException("产物 key 不能为 null");
        }
        if (amount <= 0) {
            return 0;
        }
        long current = amounts.getLong(key);
        if (capacity != null) {
            // 新 key 需要占一格；已有 key 只受单 key 总量限制。
            if (current == 0 && amounts.size() >= capacity.maxEntries()) {
                return 0;
            }
            long roomByCapacity = capacity.maxAmount() - current;
            if (roomByCapacity <= 0) {
                return 0;
            }
            amount = Math.min(amount, roomByCapacity);
            if (amount <= 0) {
                return 0;
            }
        }
        long room = Long.MAX_VALUE - current;
        if (amount > room) {
            if (room > 0) {
                amounts.put(key, Long.MAX_VALUE);
            }
            overflow.addTo(key, amount - room);
            return amount;
        }
        amounts.addTo(key, amount);
        return amount;
    }

    /** 主表数量（不含溢出部分）。 */
    public long amount(K key) {
        return amounts.getLong(key);
    }

    /** 溢出部分数量；为 0 表示没到 long 上限。 */
    public long overflow(K key) {
        return overflow.getLong(key);
    }

    /** 该 key 是否已经顶到 long 上限。 */
    public boolean isSaturated(K key) {
        return overflow.getLong(key) > 0;
    }

    public boolean isEmpty() {
        return amounts.isEmpty();
    }

    public int size() {
        return amounts.size();
    }

    /** 是否已经无法再接受新 key（背压信号）。 */
    public boolean isFull() {
        return capacity != null && amounts.size() >= capacity.maxEntries();
    }

    public Capacity capacity() {
        return capacity;
    }

    /** 全表快照（存档用）。 */
    public List<Entry<K>> snapshot() {
        return peek(Integer.MAX_VALUE);
    }

    /** 从存档恢复一条记录；数量必须非负，溢出单独记。 */
    public void restore(K key, long amount, long overflow) {
        if (key == null) {
            throw new IllegalArgumentException("产物 key 不能为 null");
        }
        if (amount < 0 || overflow < 0) {
            throw new IllegalArgumentException("恢复的数量不能为负: " + amount + "/" + overflow);
        }
        if (amount > 0) {
            amounts.put(key, amount);
        }
        if (overflow > 0) {
            this.overflow.put(key, overflow);
        }
    }

    /** 取最多 maxEntries 条快照，供刷写使用；不消费、不改变表内容。 */
    public List<Entry<K>> peek(int maxEntries) {
        if (maxEntries <= 0 || amounts.isEmpty()) {
            return List.of();
        }
        List<Entry<K>> result = new ArrayList<>(Math.min(maxEntries, amounts.size()));
        for (var it = amounts.object2LongEntrySet().fastIterator(); it.hasNext(); ) {
            if (result.size() >= maxEntries) {
                break;
            }
            var entry = it.next();
            result.add(new Entry<>(entry.getKey(), entry.getLongValue()));
        }
        return result;
    }

    /**
     * 扣减已刷出的数量。
     *
     * @throws IllegalStateException 扣减量超过当前主表数量（说明刷写逻辑重复扣了）
     */
    public void subtract(K key, long amount) {
        if (amount <= 0) {
            return;
        }
        long current = amounts.getLong(key);
        if (amount > current) {
            throw new IllegalStateException("扣减超过待刷数量: key=" + key + " 现有=" + current + " 扣减=" + amount);
        }
        long left = current - amount;
        if (left == 0) {
            amounts.removeLong(key);
        } else {
            amounts.put(key, left);
        }
    }

    /** 清空主表与溢出表（整体作废时使用，例如版本不匹配的旧存档）。 */
    public void clear() {
        amounts.clear();
        overflow.clear();
    }
}
