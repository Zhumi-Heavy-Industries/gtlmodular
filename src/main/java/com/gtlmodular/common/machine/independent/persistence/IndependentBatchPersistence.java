package com.gtlmodular.common.machine.independent.persistence;

import com.gtlmodular.common.machine.independent.core.IndependentBatchOperations;
import com.gtlmodular.common.machine.independent.core.IndependentBatchSession;
import com.gtlmodular.common.machine.independent.core.IndependentSessionSnapshot;
import com.gtlmodular.common.machine.independent.core.IndependentSessionSnapshot.Batch;
import com.gtlmodular.common.machine.independent.core.IndependentSessionSnapshot.Phase;
import com.gtlmodular.common.machine.independent.core.IndependentSessionSnapshot.Retained;
import com.mojang.serialization.DynamicOps;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.UnaryOperator;
import java.util.function.Consumer;

import static com.gtlmodular.common.machine.independent.persistence.PreservedBatchData.Scope.*;

public final class IndependentBatchPersistence<P, T> {
    public static final String ROOT_KEY = "gtlmodular_independent_threads";
    public static final int VERSION = 1;
    // 超限只暂停并保留整份原文，不能裁剪已经扣料的工作。
    public static final int MAX_RECORDS = 65_536;
    private final DynamicOps<T> ops;
    private final UnaryOperator<T> copy;
    private final BatchPayloadCodec<P, T> payloadCodec;
    private Optional<T> previousRoot = Optional.empty();

    public IndependentBatchPersistence(DynamicOps<T> ops, UnaryOperator<T> copy, BatchPayloadCodec<P, T> payloadCodec) {
        this.ops = Objects.requireNonNull(ops);
        this.copy = Objects.requireNonNull(copy);
        this.payloadCodec = Objects.requireNonNull(payloadCodec);
    }

    // 返回独占且验证过的候选；调用者必须在任何真实输入 IO 之前使用它。
    public IndependentBatchOperations.Candidate<P> prepare(P payload, int duration, long budget) {
        var batch = new Batch<>(payload, duration, 0, budget, Phase.RUNNING, null);
        payloadCodec.validateCandidate(batch);
        T encoded = copy.apply(payloadCodec.encode(payload));
        P restored = payloadCodec.decode(copy.apply(encoded));
        if (!encoded.equals(payloadCodec.encode(restored))) throw new IllegalArgumentException("Lossy batch payload");
        payloadCodec.validateCandidate(new Batch<>(restored, duration, 0, budget, Phase.RUNNING, null));
        return new IndependentBatchOperations.Candidate<>(restored, duration, budget);
    }

    public IndependentBatchSession<P, PreservedBatchData<T>> load(Optional<T> root) {
        previousRoot = root.map(copy);
        return IndependentBatchSession.restore(decode(root));
    }

    public IndependentSessionSnapshot<P, PreservedBatchData<T>> decode(Optional<T> root) {
        if (root.isEmpty()) return new IndependentSessionSnapshot<>(false, List.of(), List.of());
        T original = copy.apply(root.get());
        try {
            var fields = new ArchiveFields<>(ops, original, "version", "owned", "drain", "running", "pending", "uncertain", "retained");
            if (fields.integer("version") != VERSION) throw new IllegalArgumentException("Unsupported batch version");
            boolean owned = fields.bool("owned");
            boolean drain = fields.bool("drain");
            List<Batch<P>> batches = new ArrayList<>();
            List<Retained<PreservedBatchData<T>>> retained = new ArrayList<>();
            readBatches(fields.list("running"), Phase.RUNNING, batches, retained);
            readBatches(fields.list("pending"), Phase.PENDING_SETTLEMENT, batches, retained);
            readBatches(fields.list("uncertain"), Phase.INPUT_UNCERTAIN, batches, retained);
            for (T raw : fields.list("retained")) {
                var entry = new ArchiveFields<>(ops, raw, "raw", "reason");
                retained.add(new Retained<>(new PreservedBatchData<>(RECORD, copy.apply(entry.required("raw"))), entry.string("reason")));
            }
            if (batches.size() + retained.size() > MAX_RECORDS) throw new IllegalArgumentException("Too many batch records");
            if (owned != (!batches.isEmpty() || !retained.isEmpty()) || (drain && !owned)) {
                throw new IllegalArgumentException("Inconsistent batch ownership");
            }
            return new IndependentSessionSnapshot<>(drain, batches, retained);
        } catch (RuntimeException exception) {
            return new IndependentSessionSnapshot<>(true, List.of(),
                    List.of(new Retained<>(new PreservedBatchData<>(ROOT, original), reason(exception))));
        }
    }

    public Optional<T> save(IndependentBatchSession<P, PreservedBatchData<T>> session) {
        return save(session, Optional.empty(), exception -> {});
    }

    public Optional<T> save(IndependentBatchSession<P, PreservedBatchData<T>> session,
                            Optional<T> original, Consumer<RuntimeException> failure) {
        try {
            Optional<T> result = saveSnapshot(session, failure);
            previousRoot = result.map(copy);
            return result;
        } catch (RuntimeException exception) {
            failure.accept(exception);
            // 新建的保存目标没有旧键，仍须保留加载或上次保存的原文；旧进度只能人工核账。
            return original.or(() -> previousRoot).map(raw -> encode(new IndependentSessionSnapshot<P, PreservedBatchData<T>>(
                    true, List.of(), List.of(new Retained<>(new PreservedBatchData<>(RECORD, copy.apply(raw)),
                    saveReason(exception))))).orElseThrow());
        }
    }

    private Optional<T> saveSnapshot(IndependentBatchSession<P, PreservedBatchData<T>> session,
                                     Consumer<RuntimeException> failure) {
        try {
            return encode(session.snapshot(payload -> payload, raw -> raw), failure);
        } catch (RuntimeException exception) {
            failure.accept(exception);
            // 保存可能从物料回调重入；只能保留人工核账记录，不能发布半次 IO 的可运行快照。
            var retained = new ArrayList<Retained<PreservedBatchData<T>>>();
            for (var entry : session.retainedRecords()) retained.add(new Retained<>(
                    new PreservedBatchData<>(RECORD, entry.raw().value()), entry.reason()));
            for (var batch : session.batches()) retained.add(preserve(batch, exception));
            return encode(new IndependentSessionSnapshot<>(session.drainRequested(), List.of(), retained), failure);
        }
    }

    public Optional<T> encode(IndependentSessionSnapshot<P, PreservedBatchData<T>> snapshot) {
        return encode(snapshot, null);
    }

    private Optional<T> encode(IndependentSessionSnapshot<P, PreservedBatchData<T>> snapshot,
                                Consumer<RuntimeException> failure) {
        var roots = snapshot.retained().stream().filter(entry -> entry.raw().scope() == ROOT).toList();
        if (!roots.isEmpty()) {
            if (roots.size() != 1 || snapshot.retained().size() != 1 || !snapshot.batches().isEmpty()) {
                throw new IllegalArgumentException("Opaque root cannot be merged with other ownership");
            }
            return Optional.of(copy.apply(roots.get(0).raw().value()));
        }
        if (snapshot.batches().isEmpty() && snapshot.retained().isEmpty()) return Optional.empty();
        if (failure == null && snapshot.batches().size() + snapshot.retained().size() > MAX_RECORDS) {
            throw new IllegalArgumentException("Too many batch records");
        }
        Map<String, T> root = new LinkedHashMap<>();
        root.put("version", ops.createInt(VERSION));
        root.put("owned", ops.createBoolean(true));
        root.put("drain", ops.createBoolean(snapshot.drainRequested()));
        var retained = new ArrayList<>(snapshot.retained());
        root.put("running", writeBatches(snapshot.batches(), Phase.RUNNING, retained, failure));
        root.put("pending", writeBatches(snapshot.batches(), Phase.PENDING_SETTLEMENT, retained, failure));
        root.put("uncertain", writeBatches(snapshot.batches(), Phase.INPUT_UNCERTAIN, retained, failure));
        root.put("retained", ops.createList(retained.stream().map(entry -> object(Map.of(
                "raw", copy.apply(entry.raw().value()), "reason", ops.createString(entry.reason()))))));
        return Optional.of(copy.apply(object(root)));
    }

    private T writeBatches(List<Batch<P>> batches, Phase phase, List<Retained<PreservedBatchData<T>>> retained,
                            Consumer<RuntimeException> failure) {
        List<T> rows = new ArrayList<>();
        for (var batch : batches) {
            if (batch.phase() != phase) continue;
            T payload = null;
            try {
                payload = copy.apply(payloadCodec.encode(batch.payload()));
                payloadCodec.validate(batch);
                rows.add(writeBatch(batch, payload));
            } catch (RuntimeException exception) {
                if (failure == null) throw exception;
                failure.accept(exception);
                retained.add(payload == null ? preserve(batch, exception) :
                        new Retained<>(new PreservedBatchData<>(RECORD, writeBatch(batch, payload)), saveReason(exception)));
            }
        }
        return ops.createList(rows.stream());
    }

    private Retained<PreservedBatchData<T>> preserve(Batch<P> batch, RuntimeException exception) {
        T payload = payloadCodec.preserved(batch.payload()).map(copy).orElseGet(ops::emptyMap);
        return new Retained<>(new PreservedBatchData<>(RECORD, writeBatch(batch, payload)), saveReason(exception));
    }

    private T writeBatch(Batch<P> batch, T payload) {
        Map<String, T> entry = new LinkedHashMap<>();
        entry.put("payload", payload);
        entry.put("duration", ops.createInt(batch.duration()));
        entry.put("progress", ops.createInt(batch.progress()));
        entry.put("budget", ops.createLong(batch.budget()));
        entry.put("phase", ops.createString(batch.phase().name()));
        if (batch.failure() != null) entry.put("failure", ops.createString(batch.failure()));
        return object(entry);
    }

    private static String saveReason(RuntimeException exception) {
        return "保存失败，原始编码仅供人工核账，不能重放：" + reason(exception);
    }

    private void readBatches(List<T> records, Phase table, List<Batch<P>> batches, List<Retained<PreservedBatchData<T>>> retained) {
        for (T raw : records) {
            try {
                var fields = new ArchiveFields<>(ops, raw, "payload", "duration", "progress", "budget", "phase", "failure");
                Phase phase = Phase.valueOf(fields.string("phase"));
                if (phase != table) throw new IllegalArgumentException("Batch in wrong phase table");
                var batch = new Batch<>(payloadCodec.decode(copy.apply(fields.required("payload"))), fields.integer("duration"),
                        fields.integer("progress"), fields.number("budget"), phase,
                        fields.has("failure") ? fields.string("failure") : null);
                payloadCodec.validate(batch);
                batches.add(batch);
            } catch (RuntimeException exception) {
                retained.add(new Retained<>(new PreservedBatchData<>(RECORD, copy.apply(raw)), table + ": " + reason(exception)));
            }
        }
    }

    private T object(Map<String, T> values) {
        Map<T, T> result = new LinkedHashMap<>();
        values.forEach((key, value) -> result.put(ops.createString(key), value));
        return ops.createMap(result);
    }

    private static String reason(RuntimeException exception) {
        return exception.getClass().getSimpleName() + ": " + exception.getMessage();
    }
}
