package com.gtlmodular.common.thread;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

public final class ConcurrentBatchEngine<P, B extends BatchState<P>> implements Iterable<B> {
    private final List<B> batches = new ArrayList<>();
    private final BatchState.Factory<P, B> factory;

    public ConcurrentBatchEngine(BatchState.Factory<P, B> factory) {
        this.factory = Objects.requireNonNull(factory);
    }

    public int size() {
        return batches.size();
    }

    public B get(int index) {
        return batches.get(index);
    }

    public boolean isEmpty() {
        return batches.isEmpty();
    }

    public void clear() {
        batches.clear();
    }

    @Override
    public Iterator<B> iterator() {
        // 存档适配器需要原批次及顺序，但不能绕过完成回调删除批次。
        return Collections.unmodifiableList(batches).iterator();
    }

    // 外部存档适配器已解码的批次必须原样入表，不能重扣料或按当前槽数裁剪。
    public void restoreBatch(B batch) {
        batches.add(Objects.requireNonNull(batch));
    }

    public boolean tick(BatchPolicy<P> policy) {
        if (policy == null) {
            return false;
        }
        boolean working = false;
        Iterator<B> iterator = batches.iterator();
        while (iterator.hasNext()) {
            B batch = iterator.next();
            if (batch.progress() < batch.duration() && policy.tickInputs(batch.payload())) {
                batch.setProgress(batch.progress() + 1);
                working = true;
            }
            if (batch.progress() >= batch.duration() && policy.tryFinishOutputs(batch.payload())) {
                iterator.remove();
            }
        }

        // 槽数只限制补位，降槽后已扣料的批次仍须继续运行；新批次留到下一 tick 推进。
        int slots = Math.max(1, policy.slots());
        while (batches.size() < slots) {
            P payload = policy.findBatch();
            if (payload == null || !policy.consumeInputs(payload)) {
                break;
            }
            batches.add(factory.create(payload, Math.max(1, policy.duration(payload)), 0));
        }
        return working;
    }

    public <E> BatchArchive<E> save(BatchCodec<P, E> codec) {
        List<BatchArchive.Entry<E>> entries = new ArrayList<>(batches.size());
        for (B batch : batches) {
            entries.add(new BatchArchive.Entry<>(codec.encode(batch.payload()), batch.duration(), batch.progress()));
        }
        return new BatchArchive<>(entries);
    }

    public <E> void load(BatchArchive<E> archive, BatchCodec<P, E> codec) {
        batches.clear();
        // 存档里的批次已经扣过料，恢复不能走准入流程或按当前槽数裁剪。
        for (BatchArchive.Entry<E> entry : archive.entries()) {
            codec.decode(entry.payload()).ifPresent(payload ->
                    batches.add(factory.create(payload, entry.duration(), entry.progress())));
        }
    }
}
