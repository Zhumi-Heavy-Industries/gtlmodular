package com.gtlmodular.common.thread;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class ConcurrentBatchEngineTest {

    @ParameterizedTest
    @ValueSource(ints = {-3, 0, 1, 3})
    void admissionUsesAtLeastOneSlotAndDoesNotAdvanceNewBatches(int slots) {
        var engine = newEngine();
        var host = new TestHost(slots, job("a", 5), job("b", 5), job("c", 5), job("d", 5));

        assertFalse(engine.tick(host));

        int expected = Math.max(1, slots);
        assertEquals(expected, engine.size());
        assertEquals(expected, host.consumed.size());
        assertEquals(expected, host.findCalls);
        for (int i = 0; i < engine.size(); i++) {
            assertSame(host.consumed.get(i), engine.get(i).payload());
            assertEquals(0, engine.get(i).progress());
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {-5, 0, 1})
    void durationHasAMinimumOfOneTick(int duration) {
        var engine = newEngine();
        var host = new TestHost(1, job("a", duration));

        assertFalse(engine.tick(host));
        assertEquals(1, engine.get(0).duration());
        assertTrue(engine.tick(host));
        assertTrue(engine.isEmpty());
        assertEquals(List.of("a"), host.finished.stream().map(Job::id).toList());
    }

    @Test
    void failedConsumptionDoesNotAdmitTheBatchAndStopsFilling() {
        var engine = newEngine();
        var accepted = job("accepted", 5);
        var rejected = job("rejected", 5);
        var later = job("later", 5);
        var host = new TestHost(3, accepted, rejected, later);
        host.rejected.add(rejected);

        assertFalse(engine.tick(host));

        assertEquals(1, engine.size());
        assertSame(accepted, engine.get(0).payload());
        assertEquals(List.of(accepted), host.consumed);
        assertEquals(List.of(accepted, rejected), host.consumeAttempts);
        assertEquals(2, host.findCalls);
        assertSame(later, host.candidates.peek());
        assertTrue(host.finished.isEmpty());
    }

    @Test
    void missingCandidateStopsFillingWithoutConsumption() {
        var engine = newEngine();
        var host = new TestHost(3);

        assertFalse(engine.tick(host));
        assertTrue(engine.isEmpty());
        assertEquals(1, host.findCalls);
        assertTrue(host.consumeAttempts.isEmpty());
    }

    @Test
    void waitingBatchDoesNotStopOtherBatchesAndCanResume() {
        var engine = newEngine();
        var blocked = job("blocked", 8);
        var ready = job("ready", 8);
        var host = new TestHost(2, blocked, ready);
        engine.tick(host);
        host.waiting.add(blocked);

        assertTrue(engine.tick(host));
        assertEquals(0, engine.get(0).progress());
        assertEquals(1, engine.get(1).progress());
        assertTrue(engine.tick(host));
        assertEquals(0, engine.get(0).progress());
        assertEquals(2, engine.get(1).progress());

        host.waiting.add(ready);
        assertFalse(engine.tick(host));
        assertEquals(2, engine.size());
        assertEquals(2, engine.get(1).progress());

        host.waiting.remove(blocked);
        assertTrue(engine.tick(host));
        assertEquals(1, engine.get(0).progress());
        assertEquals(2, engine.get(1).progress());
        assertEquals(List.of(blocked, ready), host.consumed);
    }

    @Test
    void completionRemovesAfterSuccessfulCallbackAndRefillsAfterAllExistingBatches() {
        var engine = newEngine();
        var shortJob = job("short", 1);
        var longJob = job("long", 5);
        var replacement = job("replacement", 3);
        var host = new TestHost(2, shortJob, longJob, replacement);
        engine.tick(host);
        TestBatch survivingBatch = engine.get(1);
        host.onFinish = finished -> {
            assertSame(shortJob, finished);
            assertEquals(2, engine.size());
            assertSame(shortJob, engine.get(0).payload());
            assertSame(survivingBatch, engine.get(1));
            assertEquals(0, survivingBatch.progress());
        };
        host.events.clear();

        assertTrue(engine.tick(host));

        assertEquals(List.of("tick:short", "finish:short", "tick:long", "slots",
                "find:replacement", "consume:replacement"), host.events);
        assertSame(survivingBatch, engine.get(0));
        assertEquals(1, survivingBatch.progress());
        assertSame(replacement, engine.get(1).payload());
        assertEquals(0, engine.get(1).progress());
        assertEquals(List.of(shortJob), host.finished);

        assertTrue(engine.tick(host));
        assertEquals(2, survivingBatch.progress());
        assertEquals(1, engine.get(1).progress());
        assertEquals(List.of(shortJob), host.finished);
    }

    @Test
    void completionWithoutAdvancementDoesNotReportWorking() {
        var engine = newEngine();
        var completed = job("completed", 1);
        var host = new TestHost(1, completed, job("replacement", 2));
        engine.tick(host);
        engine.get(0).setProgress(1);
        host.waiting.add(completed);

        assertFalse(engine.tick(host));
        assertEquals(List.of(completed), host.finished);
        assertEquals("replacement", engine.get(0).payload().id());
        assertEquals(0, engine.get(0).progress());
        assertTrue(host.ticked.isEmpty());
    }

    @Test
    void fullOutputRetainsCompletedBatchWithoutFurtherEnergyOrInputConsumption() {
        var engine = newEngine();
        var completed = job("completed", 20);
        var replacement = job("replacement", 5);
        var host = new TestHost(1, completed, replacement);
        engine.tick(host);
        host.outputBlocked.add(completed);
        for (int tick = 0; tick < 20; tick++) {
            assertTrue(engine.tick(host));
        }

        for (int tick = 0; tick < 1000; tick++) {
            assertFalse(engine.tick(host));
            assertEquals(1, engine.size());
            assertEquals(20, engine.get(0).progress());
        }
        assertEquals(20, host.ticked.size());
        assertEquals(List.of(completed), host.consumed);
        assertTrue(host.finished.isEmpty());
        assertEquals(1, host.findCalls);

        host.outputBlocked.clear();
        assertFalse(engine.tick(host));
        assertEquals(List.of(completed), host.finished);
        assertEquals(20, host.ticked.size());
        assertEquals(List.of(completed, replacement), host.consumed);
        assertSame(replacement, engine.get(0).payload());
        assertEquals(0, engine.get(0).progress());
    }

    @Test
    void blockedCompletionKeepsItsSlotWhileOtherBatchesAdvanceAndFinish() {
        var engine = newEngine();
        var blocked = job("blocked", 1);
        var other = job("other", 3);
        var replacement = job("replacement", 5);
        var host = new TestHost(2, blocked, other, replacement);
        engine.tick(host);
        host.outputBlocked.add(blocked);

        for (int tick = 0; tick < 3; tick++) assertTrue(engine.tick(host));

        assertEquals(List.of(other), host.finished);
        assertSame(blocked, engine.get(0).payload());
        assertEquals(1, engine.get(0).progress());
        assertSame(replacement, engine.get(1).payload());
        assertEquals(0, engine.get(1).progress());
        assertEquals(1L, host.ticked.stream().filter(job -> job == blocked).count());
    }

    @Test
    void eachCompletedBatchChecksTheSpaceLeftByPreviousCompletions() {
        var engine = newEngine();
        var first = job("first", 1);
        var second = job("second", 1);
        var host = new TestHost(2, first, second);
        engine.tick(host);
        host.onFinish = job -> host.outputBlocked.add(second);

        assertTrue(engine.tick(host));
        assertEquals(List.of(first), host.finished);
        assertEquals(1, engine.size());
        assertSame(second, engine.get(0).payload());

        host.outputBlocked.clear();
        host.onFinish = ignored -> {};
        assertFalse(engine.tick(host));
        assertEquals(List.of(first, second), host.finished);
        assertTrue(engine.isEmpty());
        assertEquals(2, host.ticked.size());
    }

    @Test
    void failedCompletionCallbackDoesNotRemoveTheBatch() {
        var engine = newEngine();
        var job = job("job", 1);
        var host = new TestHost(1, job);
        engine.tick(host);
        host.onFinish = ignored -> { throw new IllegalStateException("output unavailable"); };

        assertThrows(IllegalStateException.class, () -> engine.tick(host));
        assertEquals(1, engine.size());
        assertEquals(1, engine.get(0).progress());
        assertTrue(host.finished.isEmpty());

        host.onFinish = ignored -> {};
        assertFalse(engine.tick(host));
        assertEquals(List.of(job), host.finished);
        assertEquals(1, host.ticked.size());
    }

    @Test
    void restoredMaximumProgressCannotOverflowOrDrawEnergyWhileOutputIsBlocked() {
        var engine = newEngine();
        var job = job("job", Integer.MAX_VALUE);
        engine.restoreBatch(new TestBatch(job, Integer.MAX_VALUE, Integer.MAX_VALUE));
        var host = new TestHost(1);
        host.outputBlocked.add(job);

        assertFalse(engine.tick(host));
        assertEquals(Integer.MAX_VALUE, engine.get(0).progress());
        assertTrue(host.ticked.isEmpty());
        assertTrue(host.consumed.isEmpty());
    }

    @Test
    void loweringSlotsKeepsAllExistingBatchesRunningUntilThereIsRoom() {
        var engine = newEngine();
        var a = job("a", 2);
        var b = job("b", 3);
        var c = job("c", 4);
        var replacement = job("replacement", 4);
        var host = new TestHost(3, a, b, c, replacement);
        engine.tick(host);
        host.slotCount = 1;

        assertTrue(engine.tick(host));
        assertEquals(3, engine.size());
        for (int i = 0; i < 3; i++) {
            assertEquals(1, engine.get(i).progress());
        }
        assertEquals(3, host.findCalls);

        assertTrue(engine.tick(host));
        assertEquals(2, engine.size());
        assertEquals(2, engine.get(0).progress());
        assertEquals(2, engine.get(1).progress());
        assertEquals(3, host.findCalls);

        assertTrue(engine.tick(host));
        assertEquals(1, engine.size());
        assertSame(c, engine.get(0).payload());
        assertEquals(3, engine.get(0).progress());
        assertEquals(3, host.findCalls);

        assertTrue(engine.tick(host));
        assertEquals(1, engine.size());
        assertSame(replacement, engine.get(0).payload());
        assertEquals(0, engine.get(0).progress());
        assertEquals(List.of(a, b, c), host.finished);
        assertEquals(4, host.findCalls);
    }

    @Test
    void increasingSlotsFillsNewCapacityAfterAdvancingExistingBatch() {
        var engine = newEngine();
        var host = new TestHost(1, job("a", 5), job("b", 5), job("c", 5), job("d", 5));
        engine.tick(host);
        host.slotCount = 3;

        assertTrue(engine.tick(host));
        assertEquals(3, engine.size());
        assertEquals(1, engine.get(0).progress());
        assertEquals(0, engine.get(1).progress());
        assertEquals(0, engine.get(2).progress());
        assertEquals(3, host.consumed.size());
        assertEquals(3, host.findCalls);
    }

    @Test
    void equalPayloadsStillHaveIndependentStateAndKeepObjectIdentity() {
        var first = job("same", 3);
        var second = job("same", 3);
        assertEquals(first, second);
        assertNotSame(first, second);
        var engine = newEngine();
        var host = new TestHost(2, first, second);
        engine.tick(host);
        host.waiting.add(first);

        assertTrue(engine.tick(host));
        assertSame(first, engine.get(0).payload());
        assertSame(second, engine.get(1).payload());
        assertNotSame(engine.get(0), engine.get(1));
        assertEquals(0, engine.get(0).progress());
        assertEquals(1, engine.get(1).progress());
    }

    @Test
    void clearDiscardsStateWithoutCompletingAndAllowsFreshAdmission() {
        var engine = newEngine();
        var host = new TestHost(2, job("a", 5), job("b", 5));
        engine.tick(host);
        engine.tick(host);

        engine.clear();
        engine.clear();

        assertTrue(engine.isEmpty());
        assertEquals(0, engine.size());
        assertTrue(host.finished.isEmpty());
        assertFalse(engine.tick(host));
        assertEquals(2, host.consumed.size());
        host.candidates.add(job("fresh", 2));
        assertFalse(engine.tick(host));
        assertEquals("fresh", engine.get(0).payload().id());
        assertEquals(0, engine.get(0).progress());
    }

    @Test
    void absentHostLeavesExistingBatchesUntouched() {
        var engine = newEngine();
        engine.tick(new TestHost(1, job("a", 2)));

        assertFalse(engine.tick(null));
        assertEquals(1, engine.size());
        assertEquals(0, engine.get(0).progress());
    }

    @Test
    void streamedRestoreKeepsOriginalStatesAndRunsAboveTheCurrentSlotLimit() {
        var engine = newEngine();
        var first = new TestBatch(job("first", 20), 2, 1);
        var second = new TestBatch(job("second", 20), 5, -3);
        var third = new TestBatch(job("third", 20), 7, 0);

        engine.restoreBatch(first);
        engine.restoreBatch(second);
        engine.restoreBatch(third);

        assertSame(first, engine.get(0));
        assertSame(second, engine.get(1));
        assertSame(third, engine.get(2));
        var host = new TestHost(1);
        assertTrue(engine.tick(host));
        assertEquals(List.of(first.payload()), host.finished);
        assertEquals(2, first.progress());
        assertEquals(-2, second.progress());
        assertEquals(1, third.progress());
        assertEquals(2, engine.size());
        assertSame(second, engine.get(0));
        assertSame(third, engine.get(1));
        assertTrue(host.consumeAttempts.isEmpty());
        assertEquals(0, host.findCalls);
    }

    @Test
    void archiveIterationExposesLiveOrderedStatesButCannotRemoveUnfinishedBatches() {
        var engine = newEngine();
        var host = new TestHost(2, job("first", 5), job("second", 5));
        engine.tick(host);
        var iterator = engine.iterator();
        var first = iterator.next();
        assertSame(engine.get(0), first);
        assertThrows(UnsupportedOperationException.class, iterator::remove);
        assertSame(engine.get(1), iterator.next());
        assertFalse(iterator.hasNext());

        engine.tick(host);

        assertEquals(1, first.progress());
        assertEquals(2, engine.size());
        assertTrue(host.finished.isEmpty());
    }

    static ConcurrentBatchEngine<Job, TestBatch> newEngine() {
        return new ConcurrentBatchEngine<>(TestBatch::new);
    }

    static Job job(String id, int duration) {
        return new Job(id, duration, 1L);
    }

    record Job(String id, int duration, long runs) {}

    static final class TestBatch implements BatchState<Job> {
        private final Job payload;
        private final int duration;
        private int progress;

        TestBatch(Job payload, int duration, int progress) {
            this.payload = payload;
            this.duration = duration;
            this.progress = progress;
        }

        @Override
        public Job payload() {
            return payload;
        }

        @Override
        public int duration() {
            return duration;
        }

        @Override
        public int progress() {
            return progress;
        }

        @Override
        public void setProgress(int progress) {
            this.progress = progress;
        }
    }

    static final class TestHost implements BatchPolicy<Job> {
        int slotCount;
        int findCalls;
        final Queue<Job> candidates = new ArrayDeque<>();
        final Set<Job> rejected = Collections.newSetFromMap(new IdentityHashMap<>());
        final Set<Job> waiting = Collections.newSetFromMap(new IdentityHashMap<>());
        final Set<Job> outputBlocked = Collections.newSetFromMap(new IdentityHashMap<>());
        final List<Job> consumeAttempts = new ArrayList<>();
        final List<Job> consumed = new ArrayList<>();
        final List<Job> finished = new ArrayList<>();
        final List<Job> ticked = new ArrayList<>();
        final List<String> events = new ArrayList<>();
        Consumer<Job> onFinish = ignored -> {};

        TestHost(int slots, Job... jobs) {
            slotCount = slots;
            candidates.addAll(List.of(jobs));
        }

        @Override
        public int slots() {
            events.add("slots");
            return slotCount;
        }

        @Override
        public Job findBatch() {
            findCalls++;
            Job candidate = candidates.poll();
            events.add("find:" + (candidate == null ? "empty" : candidate.id()));
            return candidate;
        }

        @Override
        public int duration(Job payload) {
            return payload.duration();
        }

        @Override
        public boolean consumeInputs(Job payload) {
            events.add("consume:" + payload.id());
            consumeAttempts.add(payload);
            if (rejected.contains(payload)) {
                return false;
            }
            consumed.add(payload);
            return true;
        }

        @Override
        public boolean tickInputs(Job payload) {
            events.add("tick:" + payload.id());
            ticked.add(payload);
            return !waiting.contains(payload);
        }

        @Override
        public boolean tryFinishOutputs(Job payload) {
            return !outputBlocked.contains(payload) && BatchPolicy.super.tryFinishOutputs(payload);
        }

        @Override
        public void finishOutputs(Job payload) {
            events.add("finish:" + payload.id());
            onFinish.accept(payload);
            finished.add(payload);
        }
    }
}
