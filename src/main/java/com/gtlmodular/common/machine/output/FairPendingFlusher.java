package com.gtlmodular.common.machine.output;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 按 key 轮转刷 {@link PendingProducts}，支持 simulate 与真实刷两种模式。
 *
 * <p>为什么不能“固定前 64 条”：前面的 key 一旦被拒收就会反复占住额度，后面的 key 永远轮不到。
 * 这里每轮从队首取 key、无论成功还是拒收都放回队尾，拒收的 key 自然轮到队尾。</p>
 */
public final class FairPendingFlusher<K> {
    /** 下游写入与模拟；simulate 必须反映真实可用量，不能默认全收。 */
    public interface Sink<K> {
        long insert(K key, long amount);

        long simulate(K key, long amount);
    }

    public record Budget(int maxKeys, long maxAmountPerKey) {
        public Budget {
            if (maxKeys <= 0) {
                throw new IllegalArgumentException("maxKeys 必须为正");
            }
            if (maxAmountPerKey <= 0) {
                throw new IllegalArgumentException("maxAmountPerKey 必须为正");
            }
        }
    }

    public record Result(int triedKeys, int movedKeys, Int128 moved, boolean hasPending) {}

    private final ArrayDeque<K> rotation = new ArrayDeque<>();

    public Result flush(PendingProducts<K> pending, Sink<K> sink, Budget budget, boolean simulate) {
        syncRotation(pending);
        Int128 moved = Int128.ZERO;
        int tried = 0;
        int movedKeys = 0;
        int candidates = Math.min(budget.maxKeys(), rotation.size());

        for (int i = 0; i < candidates; i++) {
            K key = rotation.pollFirst();
            if (key == null) {
                break;
            }
            Int128 amount = pending.amount(key);
            if (amount.isZero()) {
                continue;
            }
            long chunk = Math.min(amount.toLongClamped(), budget.maxAmountPerKey());
            if (chunk <= 0) {
                rotation.addLast(key);
                break;
            }
            tried++;
            long accepted = simulate ? sink.simulate(key, chunk) : sink.insert(key, chunk);
            if (accepted < 0 || accepted > chunk) {
                throw new IllegalStateException(
                        "下游返回了非法接受量: key=" + key + " 请求=" + chunk + " 接受=" + accepted);
            }
            if (accepted > 0) {
                if (!simulate) {
                    pending.drain(key, accepted);
                }
                moved = moved.add(Int128.of(accepted));
                movedKeys++;
            }
            if (pending.amount(key).isZero()) {
                // 已清空：不再排队
                continue;
            }
            rotation.addLast(key);
            if (accepted < chunk) {
                break; // 拒收：本轮停手，下一轮从下一个 key 起
            }
        }

        if (simulate) {
            // 模拟不改变 pending，但也不能让轮转队列被永久打乱：重新按当前表同步。
            syncRotation(pending);
        }
        return new Result(tried, movedKeys, moved, !pending.isEmpty());
    }

    public int queueSize() {
        return rotation.size();
    }

    private void syncRotation(PendingProducts<K> pending) {
        if (pending.isEmpty()) {
            rotation.clear();
            return;
        }
        List<K> keys = pending.keys();
        Set<K> present = new HashSet<>(keys.size());
        for (K key : keys) {
            present.add(key);
        }
        rotation.removeIf(key -> !present.contains(key));
        Set<K> queued = new HashSet<>(rotation);
        for (K key : keys) {
            if (queued.add(key)) {
                rotation.addLast(key);
            }
        }
    }
}
