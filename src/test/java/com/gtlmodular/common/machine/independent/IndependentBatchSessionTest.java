package com.gtlmodular.common.machine.independent;

import com.gtlmodular.common.machine.independent.core.IndependentBatchOperations;
import com.gtlmodular.common.machine.independent.core.IndependentBatchSession;
import com.gtlmodular.common.machine.independent.core.IndependentSessionControl;
import com.gtlmodular.common.machine.independent.core.IndependentSessionSnapshot;
import com.gtlmodular.common.machine.independent.core.IndependentSessionSnapshot.Phase;
import com.gtlmodular.common.machine.independent.eligibility.IndependentThreadEligibility;
import com.gtlmodular.common.machine.independent.eligibility.ThreadEligibilitySettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.function.Function;

import static com.gtlmodular.common.machine.independent.core.IndependentSessionSnapshot.Phase.INPUT_UNCERTAIN;
import static com.gtlmodular.common.machine.independent.core.IndependentSessionSnapshot.Phase.PENDING_SETTLEMENT;
import static com.gtlmodular.common.machine.independent.core.IndependentSessionState.*;
import static org.junit.jupiter.api.Assertions.*;

class IndependentBatchSessionTest {
    private static final String MACHINE = "gtladditions:arcane_cache_vault";

    @Test
    void finalAdmissionClockPersistsAndDoesNotConsumeInputsAgain() {
        var session = session();
        var work = work("accepted", 200, 10, 1);
        var io = new Resources(work);
        session.tick(control(true, 2, 20), io);
        session.updateUnstartedDuration(work, 100);
        var snapshot = session.snapshot(Function.identity(), Function.identity());
        assertEquals(100, snapshot.batches().get(0).duration());
        var restored = IndependentBatchSession.restore(snapshot);
        for (int tick = 0; tick < 99; tick++) restored.tick(control(false, 2, 20), io);
        assertEquals(0, io.outputsDelivered);
        restored.tick(control(false, 2, 20), io);
        assertEquals(1, io.outputsDelivered);
        assertEquals(1, io.inputsConsumed);
        assertEquals(100, io.energyConsumed);
        assertFalse(restored.hasOwnedWork());
    }

    @Test
    void admissionClockCannotRewriteProgressOrOutputWait() {
        var session = session();
        var work = work("running", 2, 10, 1);
        var io = new Resources(work);
        io.outputCapacity = 0;
        session.tick(control(true, 1, 10), io);
        assertThrows(IllegalStateException.class, () -> session.updateUnstartedDuration(work, 0));
        session.tick(control(false, 1, 10), io);
        assertThrows(IllegalStateException.class, () -> session.updateUnstartedDuration(work, 1));
        session.tick(control(false, 1, 10), io);
        assertEquals(PENDING_SETTLEMENT, session.batches().get(0).phase());
        assertThrows(IllegalStateException.class, () -> session.updateUnstartedDuration(work, 1));
        assertEquals(2, session.batches().get(0).duration());
    }

    @Test
    void sameRecipeBatchesShareBudgetAndBlockedOutputsDoNotReleaseIt() {
        var session = session();
        var io = new Resources(work("same type:id", 1, 100, 1), work("same type:id", 4, 100, 1),
                work("same type:id", 2, 100, 1));
        io.outputCapacity = 0;
        session.tick(control(true, 2, 200), io);
        assertEquals(2, session.batches().size());
        assertNotSame(session.batches().get(0).payload(), session.batches().get(1).payload());
        session.tick(control(true, 2, 200), io);
        assertEquals(PENDING_SETTLEMENT, session.batches().get(0).phase());
        assertEquals(BigInteger.valueOf(200), session.occupiedBudget());
        session.tick(control(true, 1, 100), io);
        assertEquals(2, session.batches().size());
        assertEquals(2, io.inputsConsumed);
        io.outputCapacity = 1;
        session.tick(control(true, 2, 200), io);
        assertEquals(3, io.inputsConsumed);
        assertEquals(1, io.outputsDelivered);
        assertEquals(BigInteger.valueOf(200), session.occupiedBudget());
        assertEquals(List.of(4, 2), session.batches().stream().map(IndependentSessionSnapshot.Batch::duration).toList());
    }

    @Test
    void disabledEmptySessionAndUpstreamHandoffNeverTouchIndependentIO() {
        var session = session();
        var off = session.tick(control(false, 1, 10), null);
        assertEquals(UPSTREAM, off.state());
        assertFalse(off.cancelUpstreamTick());
        assertFalse(session.hasOwnedWork());

        for (int tick = 0; tick < 5; tick++) {
            var waiting = session.tick(new IndependentSessionControl(true, true, false, true, 2, 10), null);
            assertEquals(WAITING_FOR_UPSTREAM, waiting.state());
            assertFalse(waiting.cancelUpstreamTick());
        }

        var io = new Resources(work("new", 2, 3, 1));
        var started = session.tick(control(true, 2, 10), io);
        assertEquals(RUNNING, started.state());
        assertTrue(started.cancelUpstreamTick());
        assertFalse(started.progressed());
        assertEquals(1, io.inputsConsumed);
        assertEquals(0, session.batches().get(0).progress());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 3, 4})
    void closingAtDifferentProgressFinishesOnceAndDoesNotRefillOnLastTick(int elapsed) {
        var session = session();
        var io = new Resources(work("short", 2, 2, 1), work("long", 5, 3, 1));
        session.tick(control(true, 2, 10), io);
        for (int tick = 0; tick < elapsed; tick++) session.tick(control(true, 2, 10), io);
        io.candidates.add(work("must wait", 1, 1, 1));
        int finds = io.findCalls;

        while (session.hasOwnedWork()) {
            var result = session.tick(control(false, 2, 10), io);
            assertTrue(result.cancelUpstreamTick());
        }

        assertEquals(UPSTREAM, session.state());
        assertEquals(finds, io.findCalls);
        assertEquals(2, io.inputsConsumed);
        assertEquals(7, io.energyConsumed);
        assertEquals(2, io.outputsDelivered);
        assertEquals(List.of("short", "long"), io.finished);
        assertEquals(BigInteger.ZERO, session.occupiedBudget());
        assertFalse(session.tick(control(false, 2, 10), null).cancelUpstreamTick());
    }

    @ParameterizedTest
    @ValueSource(strings = {"disabled", "blacklisted", "removedWhitelist", "mutableSingleMode"})
    void losingAdmissionLatchesDrainEvenWhenEligibilityReturns(String reason) {
        var session = session();
        var io = new Resources(work("owned", 3, 2, 1), work("later", 1, 1, 1));
        var eligibility = new IndependentThreadEligibility(Set.of(Number.class));
        Class<?> logic = reason.equals("removedWhitelist") ? Integer.class : Number.class;
        var allowed = new ThreadEligibilitySettings(true, Set.of(MACHINE), Set.of());
        assertTrue(eligibility.evaluate(allowed, MACHINE, logic, false).eligibleForNewBatches());
        session.tick(control(true, 1, 10), io);

        var changed = switch (reason) {
            case "disabled" -> new ThreadEligibilitySettings(false, Set.of(MACHINE), Set.of());
            case "blacklisted" -> new ThreadEligibilitySettings(true, Set.of(MACHINE), Set.of(MACHINE));
            case "removedWhitelist" -> new ThreadEligibilitySettings(true, Set.of(), Set.of());
            default -> allowed;
        };
        boolean admission = eligibility.evaluate(changed, MACHINE, logic, true).eligibleForNewBatches()
                && !reason.equals("mutableSingleMode");
        assertFalse(admission);
        assertEquals(DRAINING, session.tick(control(admission, 1, 10), io).state());
        assertTrue(session.drainRequested());

        session.tick(control(true, 1, 10), io);
        assertEquals(DRAINING, session.state());
        var last = session.tick(control(true, 1, 10), io);
        assertEquals(UPSTREAM, last.state());
        assertTrue(last.cancelUpstreamTick());
        assertEquals(1, io.inputsConsumed);
        assertEquals(1, io.findCalls);

        session.tick(control(true, 1, 10), io);
        assertEquals(2, io.inputsConsumed);
        assertEquals("later", session.batches().get(0).payload().id);
    }

    @Test
    void snapshotRestoresUnderDisabledConfigWithoutReconsumptionOrTrimming() {
        var session = session();
        var io = new Resources(work("short", 2, 4, 3), work("long", 4, 6, 2));
        session.tick(control(true, 2, 10), io);
        session.tick(control(true, 2, 10), io);
        var saved = session.snapshot(Work::copy, Function.identity());
        assertEquals(2, session.batches().size());
        session.tick(control(true, 2, 10), io);
        assertEquals(1, saved.batches().get(0).progress());
        assertEquals(3, saved.batches().get(0).payload().remaining);

        var restored = IndependentBatchSession.restore(saved);
        var afterLoad = new Resources(work("must wait", 1, 1, 1));
        afterLoad.energy = 0;
        assertEquals(DRAINING, restored.tick(control(false, 0, 0), afterLoad).state());
        assertEquals(2, restored.batches().size());
        assertEquals(BigInteger.TEN, restored.occupiedBudget());
        var savedWhileDraining = restored.snapshot(Work::copy, Function.identity());
        var reopened = IndependentBatchSession.restore(savedWhileDraining);
        afterLoad.energy = 100;
        while (reopened.hasOwnedWork()) reopened.tick(control(true, 0, 0), afterLoad);

        assertEquals(0, afterLoad.inputsConsumed);
        assertEquals(0, afterLoad.findCalls);
        assertEquals(4, afterLoad.energyConsumed);
        assertEquals(5, afterLoad.outputsDelivered);
        assertEquals(List.of("short", "long"), afterLoad.finished);
        assertEquals(BigInteger.ZERO, reopened.occupiedBudget());
    }

    @Test
    void blockedAndPartialOutputsKeepSlotBudgetAndRemainingWorkAcrossRestore() {
        var session = session();
        var io = new Resources(work("batch", 1, 5, 5), work("later", 1, 5, 1));
        io.outputCapacity = 0;
        session.tick(control(true, 1, 5), io);
        session.tick(control(true, 1, 5), io);
        assertEquals(PENDING_SETTLEMENT, session.batches().get(0).phase());
        for (int tick = 0; tick < 5; tick++) {
            assertFalse(session.tick(control(true, 1, 5), io).progressed());
        }
        assertEquals(1, io.energyConsumed);
        assertEquals(1, io.findCalls);
        assertEquals(BigInteger.valueOf(5), session.occupiedBudget());

        io.outputCapacity = 2;
        session.tick(control(false, 1, 5), io);
        assertEquals(2, io.outputsDelivered);
        assertEquals(3, session.batches().get(0).payload().remaining);
        assertTrue(io.finished.isEmpty());
        var restored = IndependentBatchSession.restore(session.snapshot(Work::copy, Function.identity()));
        int attempts = io.settlementAttempts;
        io.outputCapacity = 3;
        var last = restored.tick(control(true, 1, 5), io);
        assertTrue(last.cancelUpstreamTick());
        assertFalse(last.progressed());
        assertFalse(restored.hasOwnedWork());
        assertEquals(attempts + 1, io.settlementAttempts);
        assertEquals(5, io.outputsDelivered);
        assertEquals(List.of("batch"), io.finished);
        assertEquals(1, io.inputsConsumed);
        assertEquals(1, io.energyConsumed);
        restored.tick(control(false, 1, 5), io);
        assertEquals(attempts + 1, io.settlementAttempts);
    }

    @Test
    void pendingOutputDoesNotBlockOtherBatchesAndReleasedBudgetCanRefill() {
        var session = session();
        var blocked = work("blocked", 1, 4, 2);
        var io = new Resources(blocked, work("other", 3, 6, 1), work("replacement", 2, 6, 1));
        io.blockedOutput = blocked;
        session.tick(control(true, 2, 10), io);
        for (int tick = 0; tick < 3; tick++) session.tick(control(true, 2, 10), io);

        assertEquals(List.of("other"), io.finished);
        assertEquals(4, io.energyConsumed);
        assertEquals(3, io.inputsConsumed);
        assertEquals(2, session.batches().size());
        assertEquals(PENDING_SETTLEMENT, session.batches().get(0).phase());
        assertEquals("replacement", session.batches().get(1).payload().id);
        assertEquals(0, session.batches().get(1).progress());
        assertEquals(BigInteger.TEN, session.occupiedBudget());
        assertEquals(6, io.lastAvailableBudget);
    }

    @Test
    void lowerSlotsAndBudgetKeepAllBatchesButStopNewAdmissions() {
        var session = session();
        var io = new Resources(work("one", 2, 6, 1), work("two", 4, 4, 1), work("later", 1, 1, 1));
        session.tick(control(true, 2, 10), io);
        session.tick(control(true, 1, 3), io);
        assertEquals(2, session.batches().size());
        assertEquals(BigInteger.TEN, session.occupiedBudget());
        session.tick(control(true, 1, 3), io);
        assertEquals(1, session.batches().size());
        assertEquals(BigInteger.valueOf(4), session.occupiedBudget());
        session.tick(control(true, 2, 3), io);
        assertEquals(2, io.findCalls);
        session.tick(control(true, 0, 3), io);
        assertFalse(session.hasOwnedWork());
        assertEquals(2, io.inputsConsumed);
        session.tick(control(true, 1, 3), io);
        assertEquals(3, io.inputsConsumed);
    }

    @Test
    void oversizeCandidateAndFailedInputDoNotAcquireOwnership() {
        var session = session();
        var io = new Resources(work("too large", 1, 11, 1), work("no input", 1, 10, 1));
        session.tick(control(true, 2, 10), io);
        assertEquals(0, io.inputAttempts);
        assertFalse(session.hasOwnedWork());
        io.inputs = 0;
        session.tick(control(true, 2, 10), io);
        assertFalse(session.hasOwnedWork());
        assertEquals(1, io.inputAttempts);
        assertEquals(BigInteger.ZERO, session.occupiedBudget());
        assertFalse(session.tick(control(false, 2, 10), null).cancelUpstreamTick());
    }

    @ParameterizedTest
    @ValueSource(strings = {"paused", "structure", "externalOwner"})
    void pauseAndInvalidStructurePreserveRunningAndPendingWork(String cause) {
        var session = session();
        var io = new Resources(work("pending", 1, 2, 1), work("running", 3, 3, 1));
        io.outputCapacity = 0;
        session.tick(control(true, 2, 10), io);
        session.tick(control(true, 2, 10), io);
        var paused = new IndependentSessionControl(false, cause.equals("externalOwner"),
                cause.equals("paused"), !cause.equals("structure"), 2, 10);
        int attempts = io.settlementAttempts;
        int energy = io.energyConsumed;
        var before = session.batches();

        for (int tick = 0; tick < 5; tick++) {
            var result = session.tick(paused, null);
            assertEquals(PAUSED, result.state());
            assertTrue(result.cancelUpstreamTick());
            assertFalse(result.progressed());
        }
        assertEquals(before, session.batches());
        assertTrue(session.drainRequested());
        assertEquals(attempts, io.settlementAttempts);
        assertEquals(energy, io.energyConsumed);
        io.outputCapacity = 100;
        while (session.hasOwnedWork()) session.tick(control(true, 2, 10), io);
        assertEquals(2, io.inputsConsumed);
        assertEquals(4, io.energyConsumed);
        assertEquals(2, io.outputsDelivered);
    }

    @Test
    void noEnergyIsNotAnEmptySessionAndCompletedRestoredWorkNeedsNoEnergy() {
        var session = session();
        var io = new Resources(work("batch", 2, 1, 1));
        session.tick(control(true, 1, 10), io);
        io.energy = 0;
        var waiting = session.tick(control(false, 1, 10), io);
        assertFalse(waiting.progressed());
        assertTrue(waiting.cancelUpstreamTick());
        assertTrue(session.hasOwnedWork());
        assertEquals(0, session.batches().get(0).progress());

        var completed = new IndependentSessionSnapshot.Batch<>(work("completed", 3, 1, 2),
                3, 3, 1, Phase.RUNNING, null);
        var restored = IndependentBatchSession.restore(new IndependentSessionSnapshot<Work, String>(
                true, List.of(completed), List.of()));
        assertFalse(restored.tick(control(false, 0, 0), io).progressed());
        assertFalse(restored.hasOwnedWork());
        assertEquals(0, io.energyConsumed);
        assertEquals(2, io.outputsDelivered);
    }

    @ParameterizedTest
    @ValueSource(strings = {"input", "tick", "settlement"})
    void uncertainIOKeepsOwnershipAndNeverAutomaticallyReplays(String failure) {
        var session = session();
        var io = new Resources(work("uncertain", 1, 4, 3), work("later", 2, 2, 1));
        io.failAt = failure;
        session.tick(control(true, 1, 10), io);
        if (!failure.equals("input")) session.tick(control(true, 1, 10), io);
        assertEquals(PAUSED, session.state());
        assertTrue(session.hasOwnedWork());
        assertEquals(BigInteger.valueOf(4), session.occupiedBudget());
        var batch = session.batches().get(0);
        assertTrue(batch.failure().startsWith(failure + ":"));
        assertEquals(switch (failure) {
            case "input" -> INPUT_UNCERTAIN;
            case "tick" -> Phase.RUNNING;
            default -> PENDING_SETTLEMENT;
        }, batch.phase());
        int input = io.inputsConsumed;
        int energy = io.energyConsumed;
        int output = io.outputsDelivered;
        int attempts = io.settlementAttempts;
        var restored = IndependentBatchSession.restore(session.snapshot(Work::copy, Function.identity()));
        for (int tick = 0; tick < 5; tick++) {
            assertTrue(restored.tick(control(false, 1, 10), null).cancelUpstreamTick());
            assertEquals(PAUSED, restored.state());
        }
        assertEquals(input, io.inputsConsumed);
        assertEquals(energy, io.energyConsumed);
        assertEquals(output, io.outputsDelivered);
        assertEquals(attempts, io.settlementAttempts);
        assertEquals(1, io.findCalls);
        assertEquals(batch.failure(), restored.batches().get(0).failure());
    }

    @Test
    void failedBatchDoesNotFreezeOtherOwnedWorkOrAllowNewAdmissions() {
        var session = session();
        var io = new Resources(work("healthy", 3, 6, 1), work("uncertain", 1, 4, 3), work("later", 1, 1, 1));
        session.tick(control(true, 2, 10), io);
        io.failAt = "settlement";
        session.tick(control(true, 2, 10), io);
        var token = session.failedBatches().keySet().iterator().next();
        io.failAt = null;
        session.tick(control(true, 2, 10), io);
        session.tick(control(true, 2, 10), io);
        assertEquals(List.of("healthy"), io.finished);
        assertEquals(2, io.inputsConsumed);
        assertEquals(4, io.energyConsumed);
        assertEquals(2, io.outputsDelivered);
        assertEquals(BigInteger.valueOf(4), session.occupiedBudget());
        assertEquals("uncertain", session.failedBatches().get(token).payload().id);

        session.retry(token);
        session.tick(control(false, 2, 10), io);
        assertFalse(session.hasOwnedWork());
        assertEquals(4, io.outputsDelivered);
        assertEquals(4, io.energyConsumed);
        assertEquals(2, io.inputsConsumed);
        assertEquals(BigInteger.ZERO, session.occupiedBudget());
        assertThrows(IllegalArgumentException.class, () -> session.retry(token));
    }

    @Test
    void restoredFailuresNeedFreshTokensAndManualSettlementNeverCreatesResources() {
        var session = session();
        var io = new Resources(work("input uncertain", 1, 4, 3));
        io.failAt = "input";
        session.tick(control(true, 1, 10), io);
        var stale = session.failedBatches().keySet().iterator().next();
        var restored = IndependentBatchSession.restore(session.snapshot(Work::copy, Function.identity()));
        assertThrows(IllegalArgumentException.class, () -> restored.acknowledgeSettlement(stale));
        var token = restored.failedBatches().keySet().iterator().next();
        assertThrows(IllegalArgumentException.class, () -> restored.retry(token));
        restored.acknowledgeSettlement(token);
        assertFalse(restored.hasOwnedWork());
        assertEquals(BigInteger.ZERO, restored.occupiedBudget());
        assertFalse(restored.tick(control(false, 1, 10), null).cancelUpstreamTick());
        assertEquals(1, io.inputsConsumed);
        assertEquals(0, io.energyConsumed);
        assertEquals(0, io.outputsDelivered);
        assertThrows(IllegalArgumentException.class, () -> restored.acknowledgeSettlement(token));
    }

    @Test
    void manualSettlementDetachesRunningFailureFromEngineAndPreservesOtherReservations() {
        var session = session();
        var io = new Resources(work("uncertain", 2, 4, 3), work("healthy", 3, 6, 1));
        session.tick(control(true, 2, 10), io);
        io.failAt = "tick";
        session.tick(control(true, 2, 10), io);
        var tokens = List.copyOf(session.failedBatches().keySet());
        session.acknowledgeSettlement(tokens.get(0));
        assertEquals(BigInteger.valueOf(6), session.occupiedBudget());
        session.retry(tokens.get(1));
        io.failAt = null;
        for (int i = 0; i < 3; i++) session.tick(control(false, 2, 10), io);
        assertFalse(session.hasOwnedWork());
        assertEquals(List.of("healthy"), io.finished);
        assertEquals(1, io.outputsDelivered);
        assertEquals(2, io.inputsConsumed);
    }

    @Test
    void retryRotatesTokenBeforeASecondFailureAndOpaqueRecordsRequireSettlement() {
        var session = session();
        var io = new Resources(work("failing", 2, 1, 1));
        session.tick(control(true, 1, 10), io);
        io.failAt = "tick";
        session.tick(control(true, 1, 10), io);
        var token = session.failedBatches().keySet().iterator().next();
        session.retry(token);
        session.tick(control(true, 1, 10), io);
        assertThrows(IllegalArgumentException.class, () -> session.retry(token));
        var current = session.failedBatches().keySet().iterator().next();
        session.retain("unreadable", "Unknown record");
        var rawToken = session.retainedRecoveryRecords().keySet().iterator().next();
        assertThrows(IllegalArgumentException.class, () -> session.retry(rawToken));
        session.acknowledgeSettlement(rawToken);
        assertEquals(1, session.batches().size());
        assertEquals(BigInteger.ONE, session.occupiedBudget());
        session.acknowledgeSettlement(current);
        assertFalse(session.hasOwnedWork());
    }

    @Test
    void unknownRecordsSurviveAndPreventUpstreamReturnEvenWithoutRunningBatches() {
        var session = session();
        session.retain("opaque future archive", "Unknown archive version");
        var restored = IndependentBatchSession.restore(session.snapshot(Work::copy, Function.identity()));
        assertTrue(restored.hasOwnedWork());
        assertTrue(restored.batches().isEmpty());
        assertEquals(session.retainedRecords(), restored.retainedRecords());
        for (boolean enabled : List.of(false, true)) {
            var result = restored.tick(control(enabled, 1, 10), null);
            assertEquals(PAUSED, result.state());
            assertTrue(result.cancelUpstreamTick());
        }
    }

    @Test
    void drainRequestedDuringFinalSettlementPreventsRefillInThatSameTick() {
        var session = session();
        var io = new Resources(work("last", 1, 1, 1), work("later", 1, 1, 1));
        session.tick(control(true, 1, 10), io);
        io.afterSettlement = session::requestDrain;
        var last = session.tick(control(true, 1, 10), io);
        assertTrue(last.cancelUpstreamTick());
        assertEquals(UPSTREAM, last.state());
        assertEquals(1, io.inputsConsumed);
        assertEquals(1, io.findCalls);
        assertFalse(session.hasOwnedWork());
    }

    private static IndependentBatchSession<Work, String> session() {
        return new IndependentBatchSession<>();
    }

    private static IndependentSessionControl control(boolean admission, int slots, long budget) {
        return new IndependentSessionControl(admission, false, false, true, slots, budget);
    }

    private static Work work(String id, int duration, long budget, int outputs) {
        return new Work(id, duration, budget, outputs);
    }

    private static final class Work {
        final String id;
        final int duration;
        final long budget;
        int remaining;

        Work(String id, int duration, long budget, int remaining) {
            this.id = id;
            this.duration = duration;
            this.budget = budget;
            this.remaining = remaining;
        }

        Work copy() {
            return new Work(id, duration, budget, remaining);
        }
    }

    private static final class Resources implements IndependentBatchOperations<Work> {
        final Queue<Work> candidates = new ArrayDeque<>();
        final List<String> finished = new ArrayList<>();
        int inputs = 100;
        int energy = 100;
        int outputCapacity = 100;
        int findCalls;
        int inputAttempts;
        int inputsConsumed;
        int energyConsumed;
        int outputsDelivered;
        int settlementAttempts;
        long lastAvailableBudget;
        Work blockedOutput;
        String failAt;
        Runnable afterSettlement = () -> {};

        Resources(Work... candidates) {
            this.candidates.addAll(List.of(candidates));
        }

        @Override
        public Candidate<Work> findBatch(int availableSlots, long availableBudget) {
            assertTrue(availableSlots > 0);
            assertTrue(availableBudget >= 0);
            findCalls++;
            lastAvailableBudget = availableBudget;
            var work = candidates.poll();
            return work == null ? null : new Candidate<>(work, work.duration, work.budget);
        }

        @Override
        public boolean consumeInputs(Work work) {
            inputAttempts++;
            if (inputs == 0) return false;
            inputs--;
            inputsConsumed++;
            if ("input".equals(failAt)) throw new IllegalStateException("Failed after debit");
            return true;
        }

        @Override
        public boolean tickInputs(Work work) {
            if (energy == 0) return false;
            energy--;
            energyConsumed++;
            if ("tick".equals(failAt)) throw new IllegalStateException("Failed after energy debit");
            return true;
        }

        @Override
        public boolean settleOutputs(Work work) {
            settlementAttempts++;
            if (work == blockedOutput) return false;
            int delivered = Math.min(work.remaining, outputCapacity);
            if ("settlement".equals(failAt)) delivered = Math.min(delivered, 1);
            work.remaining -= delivered;
            outputCapacity -= delivered;
            outputsDelivered += delivered;
            if ("settlement".equals(failAt)) throw new IllegalStateException("Failed after partial delivery");
            if (work.remaining != 0) return false;
            assertFalse(finished.contains(work.id));
            finished.add(work.id);
            afterSettlement.run();
            return true;
        }
    }
}
