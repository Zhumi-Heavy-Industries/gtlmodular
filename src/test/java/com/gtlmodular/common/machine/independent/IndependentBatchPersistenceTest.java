package com.gtlmodular.common.machine.independent;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.gtlmodular.common.machine.independent.core.IndependentBatchSession;
import com.gtlmodular.common.machine.independent.core.IndependentBatchOperations;
import com.gtlmodular.common.machine.independent.core.IndependentSessionControl;
import com.gtlmodular.common.machine.independent.core.IndependentSessionSnapshot;
import com.gtlmodular.common.machine.independent.core.IndependentSessionSnapshot.Batch;
import com.gtlmodular.common.machine.independent.core.IndependentSessionSnapshot.Phase;
import com.gtlmodular.common.machine.independent.core.IndependentSessionSnapshot.Retained;
import com.gtlmodular.common.machine.independent.core.IndependentSessionState;
import com.gtlmodular.common.machine.independent.persistence.BatchPayloadCodec;
import com.gtlmodular.common.machine.independent.persistence.IndependentBatchPersistence;
import com.gtlmodular.common.machine.independent.persistence.PreservedBatchData;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class IndependentBatchPersistenceTest {
    private final IndependentBatchPersistence<JsonElement, JsonElement> codec = new IndependentBatchPersistence<>(
            JsonOps.INSTANCE, JsonElement::deepCopy, new BatchPayloadCodec<>() {
                @Override public JsonElement encode(JsonElement payload) { return payload.deepCopy(); }
                @Override public JsonElement decode(JsonElement payload) {
                    if (!payload.isJsonObject() || !payload.getAsJsonObject().has("recipe")) throw new IllegalArgumentException("Missing recipe");
                    return payload.deepCopy();
                }
            });

    @Test
    void emptySessionDoesNotAcquireAKey() {
        var session = codec.load(Optional.empty());
        assertFalse(session.hasOwnedWork());
        assertEquals(Optional.empty(), codec.save(session));
    }

    @Test
    void restoresAllTablesAndOwnershipWithoutCurrentSlotOrBudgetLimits() {
        var snapshot = snapshot(List.of(batch(Phase.RUNNING, 3, Long.MAX_VALUE),
                batch(Phase.PENDING_SETTLEMENT, 9, Long.MAX_VALUE), batch(Phase.INPUT_UNCERTAIN, 0, 1)));
        var session = codec.load(codec.encode(snapshot));
        assertTrue(session.drainRequested());
        assertEquals(3, session.batches().size());
        assertEquals(BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.TWO).add(BigInteger.ONE), session.occupiedBudget());
        assertEquals(List.of(3, 9, 0), session.batches().stream().map(Batch::progress).toList());
        var attempts = new AtomicInteger();
        var result = session.tick(new IndependentSessionControl(false, false, false, true, 0, 0),
                new IndependentBatchOperations<>() {
                    @Override public Candidate<JsonElement> findBatch(int slots, long budget) { return fail("Cannot refill"); }
                    @Override public boolean consumeInputs(JsonElement payload) { return fail("Cannot debit restored inputs"); }
                    @Override public boolean tickInputs(JsonElement payload) { attempts.incrementAndGet(); return false; }
                    @Override public boolean settleOutputs(JsonElement payload) { attempts.incrementAndGet(); return false; }
                });
        assertEquals(IndependentSessionState.DRAINING, result.state());
        assertEquals(2, attempts.get());
        assertTrue(result.cancelUpstreamTick());
        assertEquals(3, session.batches().size());
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "0", "2", "2147483648", "1.5", "\"1\""})
    void unknownOrMalformedVersionPreservesWholeRoot(String version) {
        var root = root();
        root.add("version", JsonParser.parseString(version));
        assertWholeRootPreserved(root);
    }

    @ParameterizedTest
    @ValueSource(strings = {"version", "owned", "drain", "running", "pending", "uncertain", "retained"})
    void missingEnvelopeFieldsNeverDefaultToEmptyOrUpstream(String missing) {
        var root = root();
        root.remove(missing);
        assertWholeRootPreserved(root);
    }

    @Test
    void malformedRootTypeAndUnknownFieldsArePreserved() {
        assertWholeRootPreserved(new JsonPrimitive("future format"));
        var root = root();
        root.addProperty("future", 7);
        assertWholeRootPreserved(root);
        root = root();
        root.addProperty("owned", false);
        assertWholeRootPreserved(root);
        root = root();
        root.addProperty("drain", 2);
        assertWholeRootPreserved(root);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"duration\":-1}", "{\"duration\":0}", "{\"duration\":2147483648}", "{\"duration\":1.5}",
            "{\"progress\":-1}", "{\"progress\":10}", "{\"budget\":-1}", "{\"budget\":9223372036854775808}",
            "{\"phase\":\"PENDING_SETTLEMENT\"}", "{\"phase\":\"FUTURE\"}", "{\"payload\":{}}", "{\"failure\":\"\"}"})
    void malformedRecordIsQuarantinedWithoutLosingOtherBatches(String changes) {
        var root = root();
        var row = root.getAsJsonArray("running").get(0).getAsJsonObject();
        JsonParser.parseString(changes).getAsJsonObject().entrySet().forEach(entry -> row.add(entry.getKey(), entry.getValue()));
        assertRecordPreserved(root, row);
    }

    @ParameterizedTest
    @ValueSource(strings = {"duration", "progress", "budget", "phase", "payload"})
    void missingRecordFieldsAreRetained(String missing) {
        var root = root();
        var row = root.getAsJsonArray("running").get(0).getAsJsonObject();
        row.remove(missing);
        assertRecordPreserved(root, row);
    }

    @Test
    void oversizedTableIsPreservedWithoutTruncation() {
        var root = root();
        var rows = root.getAsJsonArray("running");
        for (int i = 1; i <= IndependentBatchPersistence.MAX_RECORDS; i++) rows.add(new JsonPrimitive(i));
        assertWholeRootPreserved(root);
    }

    @Test
    void pendingAndUncertainStagesCannotBeReinterpretedAsRunning() {
        var root = root();
        var pending = root.getAsJsonArray("pending").get(0).getAsJsonObject();
        pending.addProperty("progress", 8);
        var session = codec.load(Optional.of(root));
        assertEquals(1, session.batches().size());
        assertEquals(1, session.retainedRecords().size());
        var uncertainRoot = codec.encode(snapshot(List.of(batch(Phase.INPUT_UNCERTAIN, 0, 1)))).orElseThrow().getAsJsonObject();
        uncertainRoot.getAsJsonArray("uncertain").get(0).getAsJsonObject().remove("failure");
        assertTrue(codec.load(Optional.of(uncertainRoot)).batches().isEmpty());
        assertTrue(codec.load(Optional.of(uncertainRoot)).hasOwnedWork());
    }

    @Test
    void savesAndReloadsAreIndependentSnapshotsAndDoNotClearSession() {
        var session = codec.load(Optional.of(root()));
        JsonElement first = codec.save(session).orElseThrow();
        JsonElement second = codec.save(session).orElseThrow();
        assertEquals(first, second);
        assertTrue(session.hasOwnedWork());
        session.batches().get(0).payload().getAsJsonObject().addProperty("recipe", "changed");
        assertNotEquals(first, codec.save(session).orElseThrow());
        assertEquals(first, second);
        var restored = codec.load(Optional.of(first));
        first.getAsJsonObject().getAsJsonArray("running").get(0).getAsJsonObject().getAsJsonObject("payload").addProperty("recipe", "external edit");
        assertEquals(second, codec.save(restored).orElseThrow());
        assertEquals(second, codec.save(codec.load(Optional.of(second))).orElseThrow());
    }

    @Test
    void rawRetentionDoesNotAliasInputOrOutput() {
        var root = root();
        root.addProperty("version", 99);
        JsonElement before = root.deepCopy();
        var session = codec.load(Optional.of(root));
        root.addProperty("version", 100);
        assertEquals(before, codec.save(session).orElseThrow());
        var saved = codec.save(session).orElseThrow().getAsJsonObject();
        saved.remove("running");
        assertEquals(before, codec.save(session).orElseThrow());
    }

    @Test
    void preflightMakesAnIndependentCandidateAndRejectsUnserializablePayload() {
        JsonElement payload = payload();
        var candidate = codec.prepare(payload, 9, 8);
        payload.getAsJsonObject().remove("recipe");
        assertTrue(candidate.payload().getAsJsonObject().has("recipe"));
        assertEquals(9, candidate.duration());
        assertEquals(8, candidate.budget());
        assertThrows(IllegalArgumentException.class, () -> codec.prepare(payload, 9, 8));
    }

    @Test
    void restoredWorkDrainsWithoutReconsumingInputsOrChargingCompletedWork() {
        var session = codec.load(codec.encode(snapshot(List.of(batch(Phase.RUNNING, 8, 2), batch(Phase.PENDING_SETTLEMENT, 9, 4)))));
        AtomicInteger ticks = new AtomicInteger();
        AtomicInteger outputs = new AtomicInteger();
        var operations = new IndependentBatchOperations<JsonElement>() {
            @Override public Candidate<JsonElement> findBatch(int slots, long budget) { throw new AssertionError("New candidate during drain"); }
            @Override public boolean consumeInputs(JsonElement payload) { throw new AssertionError("Restored input charged twice"); }
            @Override public boolean tickInputs(JsonElement payload) { ticks.incrementAndGet(); return true; }
            @Override public boolean settleOutputs(JsonElement payload) { outputs.incrementAndGet(); return true; }
        };
        var control = new IndependentSessionControl(false, false, false, true, 0, 0);
        var result = session.tick(control, operations);
        assertTrue(result.cancelUpstreamTick());
        assertEquals(1, ticks.get());
        assertEquals(2, outputs.get());
        assertFalse(session.hasOwnedWork());
        assertTrue(codec.save(session).isEmpty());
        assertFalse(session.tick(control, operations).cancelUpstreamTick());
        assertEquals(2, outputs.get());
    }

    @Test
    void rootRetentionCannotBeMergedAndSilentlyLoseOwnership() {
        var snapshot = new IndependentSessionSnapshot<JsonElement, PreservedBatchData<JsonElement>>(true,
                List.of(batch(Phase.RUNNING, 1, 1)), List.of(new Retained<>(
                new PreservedBatchData<>(PreservedBatchData.Scope.ROOT, new JsonPrimitive("opaque")), "unknown")));
        assertThrows(IllegalArgumentException.class, () -> codec.encode(snapshot));
    }

    private void assertWholeRootPreserved(JsonElement raw) {
        var session = codec.load(Optional.of(raw));
        assertEquals(IndependentSessionState.PAUSED, session.state());
        assertTrue(session.hasOwnedWork());
        assertFalse(session.retainedRecords().get(0).reason().isBlank());
        assertEquals(raw, codec.save(session).orElseThrow());
        assertEquals(raw, codec.save(codec.load(codec.save(session))).orElseThrow());
    }

    private void assertRecordPreserved(JsonObject root, JsonElement row) {
        var session = codec.load(Optional.of(root));
        assertEquals(1, session.batches().size());
        assertEquals(Phase.PENDING_SETTLEMENT, session.batches().get(0).phase());
        assertEquals(IndependentSessionState.DRAINING, session.state());
        assertEquals(row, session.retainedRecords().get(0).raw().value());
        var again = codec.load(codec.save(session));
        assertEquals(row, again.retainedRecords().get(0).raw().value());
        assertEquals(1, again.batches().size());
        assertEquals(session.retainedRecords().get(0).reason(), again.retainedRecords().get(0).reason());
    }

    private JsonObject root() {
        return codec.encode(snapshot(List.of(batch(Phase.RUNNING, 3, 2), batch(Phase.PENDING_SETTLEMENT, 9, 4))))
                .orElseThrow().getAsJsonObject();
    }

    private IndependentSessionSnapshot<JsonElement, PreservedBatchData<JsonElement>> snapshot(List<Batch<JsonElement>> batches) {
        return new IndependentSessionSnapshot<>(true, batches, List.of());
    }

    private Batch<JsonElement> batch(Phase phase, int progress, long budget) {
        return new Batch<>(payload(), 9, progress, budget, phase, phase == Phase.INPUT_UNCERTAIN ? "input: unknown" : null);
    }

    private JsonElement payload() {
        JsonObject payload = new JsonObject();
        payload.addProperty("recipe", "test:paid_work");
        return payload;
    }
}
