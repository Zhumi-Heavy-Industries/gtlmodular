package com.gtlmodular.common.thread;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static com.gtlmodular.common.thread.ConcurrentBatchEngineTest.*;
import static org.junit.jupiter.api.Assertions.*;

class BatchArchiveTest {

    private final BatchCodec<Job, String> codec = new BatchCodec<>() {
        @Override
        public String encode(Job payload) {
            return Base64.getEncoder().encodeToString(payload.id().getBytes(StandardCharsets.UTF_8))
                    + ":" + payload.duration() + ":" + payload.runs();
        }

        @Override
        public Optional<Job> decode(String encoded) {
            if (encoded == null) {
                return Optional.empty();
            }
            String[] fields = encoded.split(":", -1);
            if (fields.length < 2 || fields.length > 3) {
                return Optional.empty();
            }
            try {
                var payload = job(new String(Base64.getDecoder().decode(fields[0]), StandardCharsets.UTF_8),
                        Integer.parseInt(fields[1]));
                long runs = fields.length == 3 ? Long.parseLong(fields[2]) : 0L;
                return Optional.of(runs > 0L ? new Job(payload.id(), payload.duration(), runs) : payload);
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
        }
    };

    @Test
    void encodedRoundTripRestoresOrderPayloadDurationAndProgressIntoNewObjects() {
        var source = newEngine();
        var first = new Job("配方/一", 9, 5_000_000_000L);
        var second = new Job("recipe/two", 7, 13L);
        var host = new TestHost(2, first, second);
        source.tick(host);
        host.waiting.add(first);
        source.tick(host);

        BatchArchive<String> archive = source.save(codec);

        assertEquals(List.of(new BatchArchive.Entry<>(codec.encode(first), 9, 0),
                new BatchArchive.Entry<>(codec.encode(second), 7, 1)), archive.entries());
        var restored = newEngine();
        restored.load(archive, codec);
        assertEquals(2, restored.size());
        for (int i = 0; i < source.size(); i++) {
            assertNotSame(source.get(i), restored.get(i));
            assertNotSame(source.get(i).payload(), restored.get(i).payload());
            assertEquals(source.get(i).payload(), restored.get(i).payload());
            assertEquals(source.get(i).duration(), restored.get(i).duration());
            assertEquals(source.get(i).progress(), restored.get(i).progress());
        }
        source.tick(host);
        assertEquals(1, archive.entries().get(1).progress());
        assertEquals(1, restored.get(1).progress());
        assertEquals(2, source.get(1).progress());
    }

    @Test
    void completedBlockedBatchSurvivesRepeatedReloadsAndOutputsExactlyOnceWithoutEnergy() {
        var engine = newEngine();
        var job = job("completed", 3);
        var host = new TestHost(1, job);
        engine.tick(host);
        host.outputBlocked.add(job);
        for (int tick = 0; tick < 3; tick++) engine.tick(host);
        assertEquals(3, host.ticked.size());
        assertTrue(host.finished.isEmpty());

        for (int reload = 0; reload < 3; reload++) {
            var restored = newEngine();
            restored.load(engine.save(codec), codec);
            var blocked = restored.get(0).payload();
            host = new TestHost(1);
            host.outputBlocked.add(blocked);
            for (int tick = 0; tick < 50; tick++) assertFalse(restored.tick(host));
            assertEquals(3, restored.get(0).progress());
            assertTrue(host.ticked.isEmpty());
            assertTrue(host.consumed.isEmpty());
            assertTrue(host.finished.isEmpty());
            engine = restored;
        }

        host.outputBlocked.clear();
        for (int tick = 0; tick < 80; tick++) assertFalse(engine.tick(host));
        assertTrue(engine.isEmpty());
        assertEquals(List.of(job), host.finished);
        assertTrue(host.ticked.isEmpty());
        assertTrue(host.consumed.isEmpty());
        var afterCompletion = newEngine();
        afterCompletion.load(engine.save(codec), codec);
        assertTrue(afterCompletion.isEmpty());
    }

    @Test
    void archiveCopiesTheEntryListAndExposesAnUnmodifiableSnapshot() {
        var entry = new BatchArchive.Entry<>("encoded", 4, 2);
        var entries = new ArrayList<>(List.of(entry));
        var archive = new BatchArchive<>(entries);

        entries.clear();

        assertEquals(List.of(entry), archive.entries());
        assertThrows(UnsupportedOperationException.class, () -> archive.entries().clear());
    }

    @Test
    void restoringMoreBatchesThanSlotsDoesNotTrimOrConsumeInputs() {
        var source = newEngine();
        var sourceHost = new TestHost(3, job("a", 5), job("b", 5), job("c", 5));
        source.tick(sourceHost);
        source.tick(sourceHost);
        var restored = newEngine();
        var targetHost = new TestHost(1, job("replacement", 5));
        restored.tick(targetHost);
        targetHost.consumeAttempts.clear();
        targetHost.consumed.clear();
        targetHost.events.clear();

        restored.load(source.save(codec), codec);

        assertEquals(3, restored.size());
        assertTrue(targetHost.events.isEmpty());
        assertTrue(restored.tick(targetHost));
        for (int i = 0; i < restored.size(); i++) {
            assertEquals(source.get(i).payload(), restored.get(i).payload());
            assertEquals(2, restored.get(i).progress());
        }
        assertTrue(targetHost.consumeAttempts.isEmpty());
        assertTrue(targetHost.finished.isEmpty());
        assertEquals(1, targetHost.findCalls);
    }

    @Test
    void emptyArchiveReplacesExistingBatchesWithoutCompletion() {
        var empty = newEngine().save(codec);
        assertTrue(empty.entries().isEmpty());
        var restored = newEngine();
        var host = new TestHost(1, job("old", 4));
        restored.tick(host);

        restored.load(empty, codec);

        assertTrue(restored.isEmpty());
        assertEquals(1, host.consumed.size());
        assertTrue(host.finished.isEmpty());
    }

    @Test
    void missingAndCorruptPayloadsDoNotDiscardOtherBatchesOrRepeatConsumption() {
        var archive = new BatchArchive<>(List.of(
                new BatchArchive.Entry<>(codec.encode(job("first", 9)), 9, -2),
                new BatchArchive.Entry<String>(null, 5, 0),
                new BatchArchive.Entry<>("broken", 5, 0),
                new BatchArchive.Entry<>("%%%:5:1", 5, 0),
                new BatchArchive.Entry<>("Yg==:invalid:1", 5, 0),
                new BatchArchive.Entry<>("Yg==:5:invalid", 5, 0),
                new BatchArchive.Entry<>(codec.encode(new Job("last", 7, 5_000_000_000L)), 7, 4)));
        var restored = newEngine();
        var host = new TestHost(1, job("previous", 10));
        restored.tick(host);
        host.events.clear();
        host.consumeAttempts.clear();
        host.consumed.clear();

        restored.load(archive, codec);

        assertEquals(2, restored.size());
        assertEquals(job("first", 9), restored.get(0).payload());
        assertEquals(-2, restored.get(0).progress());
        assertEquals(new Job("last", 7, 5_000_000_000L), restored.get(1).payload());
        assertEquals(4, restored.get(1).progress());
        assertTrue(host.events.isEmpty());
        assertTrue(restored.tick(host));
        assertEquals(-1, restored.get(0).progress());
        assertEquals(5, restored.get(1).progress());
        assertTrue(host.consumeAttempts.isEmpty());
        assertTrue(host.consumed.isEmpty());
        assertTrue(host.finished.isEmpty());
    }

    @Test
    void archiveWithOnlyUnreadablePayloadsClearsPreviousBatches() {
        var restored = newEngine();
        var host = new TestHost(1, job("previous", 10));
        restored.tick(host);
        host.events.clear();

        restored.load(new BatchArchive<>(List.of(
                new BatchArchive.Entry<String>(null, 5, 0),
                new BatchArchive.Entry<>("broken", 5, 0))), codec);

        assertTrue(restored.isEmpty());
        assertTrue(host.events.isEmpty());
        assertTrue(host.finished.isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", ":0", ":-7"})
    void missingOrNonPositiveRunsPreserveTheCodecDefault(String runsSuffix) {
        var payload = job("legacy", 7);
        String encoded = Base64.getEncoder().encodeToString(payload.id().getBytes(StandardCharsets.UTF_8))
                + ":" + payload.duration() + runsSuffix;
        var restored = newEngine();

        restored.load(new BatchArchive<>(List.of(new BatchArchive.Entry<>(encoded, 7, 3))), codec);

        assertEquals(1, restored.size());
        assertEquals(payload, restored.get(0).payload());
        assertEquals(7, restored.get(0).duration());
        assertEquals(3, restored.get(0).progress());
    }

    @Test
    void restorePreservesStoredDurationAndUnclampedProgress() {
        var archive = new BatchArchive<>(List.of(
                new BatchArchive.Entry<>(codec.encode(job("a", 50)), 7, -2),
                new BatchArchive.Entry<>(codec.encode(job("b", 50)), 3, 9)));
        var restored = newEngine();

        restored.load(archive, codec);

        assertEquals(7, restored.get(0).duration());
        assertEquals(-2, restored.get(0).progress());
        assertEquals(3, restored.get(1).duration());
        assertEquals(9, restored.get(1).progress());
    }

    @Test
    void restoredBranchMatchesUninterruptedProgressCompletionAndRefill() {
        var uninterrupted = newEngine();
        var firstHost = new TestHost(2, job("a", 3), job("b", 5), job("c", 2));
        uninterrupted.tick(firstHost);
        uninterrupted.tick(firstHost);
        var restored = newEngine();
        var secondHost = new TestHost(2, job("c", 2));
        restored.load(uninterrupted.save(codec), codec);
        assertTrue(secondHost.consumeAttempts.isEmpty());

        for (int tick = 0; tick < 7; tick++) {
            firstHost.events.clear();
            secondHost.events.clear();
            firstHost.waiting.clear();
            secondHost.waiting.clear();
            if (tick == 1) {
                firstHost.waiting.add(uninterrupted.get(0).payload());
                secondHost.waiting.add(restored.get(0).payload());
            }
            assertEquals(uninterrupted.tick(firstHost), restored.tick(secondHost), "working at tick " + tick);
            assertEquals(firstHost.events, secondHost.events, "callbacks at tick " + tick);
            assertEquals(uninterrupted.save(codec), restored.save(codec), "state at tick " + tick);
        }

        assertTrue(uninterrupted.isEmpty());
        assertTrue(restored.isEmpty());
        assertEquals(firstHost.finished, secondHost.finished);
        assertEquals(List.of("a", "b", "c"), firstHost.finished.stream().map(Job::id).toList());
        assertEquals(List.of("c"), secondHost.consumed.stream().map(Job::id).toList());
    }
}
