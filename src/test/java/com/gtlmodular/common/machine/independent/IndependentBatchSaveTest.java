package com.gtlmodular.common.machine.independent;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.gtlmodular.common.machine.independent.core.IndependentBatchOperations;
import com.gtlmodular.common.machine.independent.core.IndependentBatchSession;
import com.gtlmodular.common.machine.independent.core.IndependentSessionControl;
import com.gtlmodular.common.machine.independent.core.IndependentSessionSnapshot;
import com.gtlmodular.common.machine.independent.core.IndependentSessionSnapshot.Batch;
import com.gtlmodular.common.machine.independent.core.IndependentSessionSnapshot.Phase;
import com.gtlmodular.common.machine.independent.persistence.BatchPayloadCodec;
import com.gtlmodular.common.machine.independent.persistence.IndependentBatchPersistence;
import com.gtlmodular.common.machine.independent.persistence.PreservedBatchData;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static com.gtlmodular.common.machine.independent.persistence.PreservedBatchData.Scope.ROOT;
import static org.junit.jupiter.api.Assertions.*;

class IndependentBatchSaveTest {
    private final JsonElement originalPayload = payload("broken", 5);
    private final List<RuntimeException> failures = new ArrayList<>();
    private boolean failEncoding;
    private boolean failValidation;
    private boolean failPreservation;
    private final IndependentBatchPersistence<JsonElement, JsonElement> persistence = new IndependentBatchPersistence<>(
            JsonOps.INSTANCE, JsonElement::deepCopy, new BatchPayloadCodec<>() {
                @Override public JsonElement encode(JsonElement payload) {
                    if (failEncoding && broken(payload)) throw new IllegalStateException("encoding unavailable");
                    return payload.deepCopy();
                }
                @Override public JsonElement decode(JsonElement encoded) { return encoded.deepCopy(); }
                @Override public Optional<JsonElement> preserved(JsonElement payload) {
                    if (failPreservation) throw new IllegalStateException("payload backup unavailable");
                    return Optional.of(originalPayload.deepCopy());
                }
                @Override public void validate(Batch<JsonElement> batch) {
                    if (failValidation && broken(batch.payload())) throw new IllegalArgumentException("inconsistent stage");
                }
            });

    @ParameterizedTest
    @EnumSource(Phase.class)
    void validationFailureRetainsCurrentRecordWithoutDiscardingHealthyBatches(Phase phase) {
        var broken = batch("broken", phase);
        var session = restore(List.of(broken, batch("healthy", Phase.RUNNING)));
        var expected = persistence.save(session).orElseThrow().getAsJsonObject()
                .getAsJsonArray(table(phase)).get(0).deepCopy();
        failValidation = true;
        var saved = assertDoesNotThrow(() -> persistence.save(session, Optional.empty(), failures::add)).orElseThrow();
        assertEquals(1, failures.size());
        assertEquals(2, session.batches().size());
        assertNull(session.batches().get(1).failure());
        var restored = persistence.load(Optional.of(saved));
        assertEquals(1, restored.batches().size());
        assertEquals("healthy", restored.batches().get(0).payload().getAsJsonObject().get("recipe").getAsString());
        assertEquals(expected, restored.retainedRecords().get(0).raw().value());
        assertTrue(restored.retainedRecords().get(0).reason().contains("人工核账"));
        assertEquals(saved, persistence.save(restored).orElseThrow());
    }

    @ParameterizedTest
    @EnumSource(Phase.class)
    void encodingFailureOnFirstSaveRetainsOriginalPayloadAndCurrentAccounting(Phase phase) {
        var session = restore(List.of(batch("broken", phase)));
        failEncoding = true;
        var saved = assertDoesNotThrow(() -> persistence.save(session, Optional.empty(), failures::add)).orElseThrow();
        var restored = persistence.load(Optional.of(saved));
        assertTrue(restored.batches().isEmpty());
        assertTrue(restored.hasOwnedWork());
        var raw = restored.retainedRecords().get(0).raw().value().getAsJsonObject();
        assertEquals(originalPayload, raw.get("payload"));
        assertEquals(phase.name(), raw.get("phase").getAsString());
        assertEquals(7, raw.get("budget").getAsLong());
        assertEquals(session.batches().get(0).progress(), raw.get("progress").getAsInt());
        assertEquals(1, failures.size());
        raw.getAsJsonObject("payload").addProperty("remaining", 99);
        assertEquals(5, originalPayload.getAsJsonObject().get("remaining").getAsInt());
    }

    @Test
    void recoveredSerializerUsesCurrentSessionInsteadOfKeepingStaleFallback() {
        var session = restore(List.of(batch("broken", Phase.PENDING_SETTLEMENT)));
        failEncoding = true;
        var failedSave = persistence.save(session).orElseThrow();
        failEncoding = false;
        var restored = persistence.load(persistence.save(session));
        assertTrue(restored.retainedRecords().isEmpty());
        assertEquals(2, restored.batches().get(0).payload().getAsJsonObject().get("remaining").getAsInt());
        assertEquals(5, failedSave.getAsJsonObject().getAsJsonArray("retained").get(0)
                .getAsJsonObject().getAsJsonObject("raw").getAsJsonObject("payload").get("remaining").getAsInt());
    }

    @Test
    void failedSaveDoesNotMutateOriginalRootAndReloadCannotReplayItsOldOutputRemainder() {
        var session = restore(List.of(batch("broken", Phase.PENDING_SETTLEMENT)));
        var original = persistence.save(session).orElseThrow();
        var before = original.deepCopy();
        failEncoding = true;
        var saved = persistence.save(session, Optional.of(original), failures::add);
        assertEquals(before, original);
        var restored = persistence.load(saved);
        var result = restored.tick(new IndependentSessionControl(true, false, false, true, 2, 100), new NoIo());
        assertTrue(result.cancelUpstreamTick());
        assertTrue(restored.hasOwnedWork());
        assertEquals(saved, persistence.save(restored));
    }

    @Test
    void reentrantSaveDuringInputKeepsRecoveryRecordWithoutInterruptingDebit() {
        var session = new IndependentBatchSession<JsonElement, PreservedBatchData<JsonElement>>();
        var candidate = persistence.prepare(payload("broken", 2), 9, 7);
        var saved = new AtomicReference<Optional<JsonElement>>();
        var operations = new NoIo() {
            @Override public Candidate<JsonElement> findBatch(int slots, long budget) { return candidate; }
            @Override public boolean consumeInputs(JsonElement payload) {
                saved.set(persistence.save(session, Optional.empty(), failures::add));
                return true;
            }
        };
        assertDoesNotThrow(() -> session.tick(new IndependentSessionControl(true, false, false, true, 1, 7), operations));
        assertEquals(1, session.batches().size());
        assertNull(session.batches().get(0).failure());
        assertEquals(1, failures.size());
        var restored = persistence.load(saved.get());
        assertTrue(restored.hasOwnedWork());
        assertTrue(restored.batches().isEmpty());
        assertEquals(originalPayload, restored.retainedRecords().get(0).raw().value().getAsJsonObject().get("payload"));
        assertTrue(restored.tick(new IndependentSessionControl(true, false, false, true, 1, 7), new NoIo()).cancelUpstreamTick());
    }

    @Test
    void malformedRootMixedWithOtherOwnershipPreservesBothWithoutThrowing() {
        var original = new JsonObject();
        original.addProperty("version", 99);
        var session = restore(List.of(batch("broken", Phase.RUNNING)));
        session.retain(new PreservedBatchData<>(ROOT, original), "unknown version");
        var saved = assertDoesNotThrow(() -> persistence.save(session, Optional.empty(), failures::add));
        var restored = persistence.load(saved);
        assertEquals(2, restored.retainedRecords().size());
        assertEquals(original, restored.retainedRecords().get(0).raw().value());
        assertEquals(originalPayload, restored.retainedRecords().get(1).raw().value().getAsJsonObject().get("payload"));
        assertTrue(restored.batches().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void lastResortPreservesLoadedOrSuppliedRootWithoutAutomaticallyReplayingIt(boolean suppliedRoot) {
        var original = persistence.save(restore(List.of(batch("broken", Phase.PENDING_SETTLEMENT)))).orElseThrow();
        var session = persistence.load(Optional.of(original));
        JsonElement supplied = original.deepCopy();
        supplied.getAsJsonObject().addProperty("future", "keep verbatim");
        failEncoding = true;
        failPreservation = true;
        var saved = assertDoesNotThrow(() -> persistence.save(session,
                suppliedRoot ? Optional.of(supplied) : Optional.empty(), failures::add));
        var restored = persistence.load(saved);
        assertEquals(suppliedRoot ? supplied : original, restored.retainedRecords().get(0).raw().value());
        assertTrue(restored.batches().isEmpty());
        assertTrue(restored.hasOwnedWork());
        assertTrue(restored.tick(new IndependentSessionControl(true, false, false, true, 1, 7), new NoIo()).cancelUpstreamTick());
        assertEquals(1, session.batches().size());
    }

    private static boolean broken(JsonElement payload) {
        return payload.getAsJsonObject().get("recipe").getAsString().equals("broken");
    }

    private static JsonElement payload(String recipe, int remaining) {
        var payload = new JsonObject();
        payload.addProperty("recipe", recipe);
        payload.addProperty("remaining", remaining);
        return payload;
    }

    private static Batch<JsonElement> batch(String recipe, Phase phase) {
        return new Batch<>(payload(recipe, 2), 9,
                phase == Phase.PENDING_SETTLEMENT ? 9 : phase == Phase.INPUT_UNCERTAIN ? 0 : 3,
                7, phase, phase == Phase.INPUT_UNCERTAIN ? "input transfer failed" : null);
    }

    private static IndependentBatchSession<JsonElement, PreservedBatchData<JsonElement>> restore(List<Batch<JsonElement>> batches) {
        return IndependentBatchSession.restore(new IndependentSessionSnapshot<>(false, batches, List.of()));
    }

    private static String table(Phase phase) {
        return switch (phase) {
            case RUNNING -> "running";
            case PENDING_SETTLEMENT -> "pending";
            case INPUT_UNCERTAIN -> "uncertain";
        };
    }

    private static class NoIo implements IndependentBatchOperations<JsonElement> {
        @Override public Candidate<JsonElement> findBatch(int slots, long budget) { return fail("Cannot admit while recovery is pending"); }
        @Override public boolean consumeInputs(JsonElement payload) { return fail("Cannot debit restored inputs"); }
        @Override public boolean tickInputs(JsonElement payload) { return fail("Cannot run a preserved record"); }
        @Override public boolean settleOutputs(JsonElement payload) { return fail("Cannot replay preserved outputs"); }
    }
}
