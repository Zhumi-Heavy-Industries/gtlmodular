package com.gtlmodular.common.machine.output;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 公平版批量刷写：内部维护一个 key 轮转队列，每轮从队首取 key、刷完放回队尾，
 * 避免“排在前面的 key 吃饱、后面的饿死”。
 *
 * <p>为什么不用下标游标：表里的 key 会被刷空删除，下标会整体前移，游标就会跳过或重复。
 * 队列按 key 本身轮转，删除/新增只影响队列成员，不影响“下一个是谁”。</p>
 */
public final class FairProductFlusher {
    private final ArrayDeque<Object> rotation = new ArrayDeque<>();

    @SuppressWarnings("unchecked")
    public <K> ProductFlusher.Result flush(ProductTable<K> table,
                                           ProductFlusher.Sink<K> sink,
                                           ProductFlusher.Budget budget) {
        syncRotation(table);

        long amountLeft = budget.maxAmount();
        int attempted = 0;
        int flushedEntries = 0;
        long flushedAmount = 0;
        int candidates = Math.min(budget.maxEntries(), rotation.size());

        for (int i = 0; i < candidates && amountLeft > 0; i++) {
            K key = (K) rotation.pollFirst();
            if (key == null) {
                break;
            }
            long available = table.amount(key);
            if (available <= 0) {
                continue; // 已被刷空，队列里不再留它
            }
            long want = Math.min(available, amountLeft);
            attempted++;
            long accepted = sink.insert(key, want);
            if (accepted < 0 || accepted > want) {
                throw new IllegalStateException(
                        "下游返回了非法接受量: key=" + key + " 请求=" + want + " 接受=" + accepted);
            }
            if (accepted > 0) {
                table.subtract(key, accepted);
                amountLeft -= accepted;
                flushedAmount += accepted;
                flushedEntries++;
            }
            if (table.amount(key) > 0) {
                // 还有剩（拒收或只收了一部分）：放回队尾，下一轮轮到它时再试。
                rotation.addLast(key);
            }
            if (accepted < want) {
                break; // 本轮到此为止，别让同一只 key 反复撞墙
            }
        }

        return new ProductFlusher.Result(attempted, flushedEntries, flushedAmount, !table.isEmpty());
    }

    /** 让轮转队列与表同步：丢掉已不存在的 key，把新 key 追到队尾。 */
    private <K> void syncRotation(ProductTable<K> table) {
        if (table.isEmpty()) {
            rotation.clear();
            return;
        }
        List<ProductTable.Entry<K>> entries = table.snapshot();
        Set<K> present = new HashSet<>(entries.size());
        for (ProductTable.Entry<K> entry : entries) {
            present.add(entry.key());
        }
        rotation.removeIf(key -> !present.contains(key));
        Set<Object> queued = new HashSet<>(rotation);
        for (ProductTable.Entry<K> entry : entries) {
            if (queued.add(entry.key())) {
                rotation.addLast(entry.key());
            }
        }
    }

    /** 供调试/GUI 显示当前还剩多少个 key 在轮转。 */
    public int queueSize() {
        return rotation.size();
    }
}
