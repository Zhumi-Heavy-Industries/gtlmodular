package com.gtlmodular.common.machine.independent;

import com.gtlmodular.common.machine.independent.core.*;
import com.gtlmodular.common.machine.independent.io.CountedTransfer;
import com.gtlmodular.common.machine.independent.persistence.BatchExecutionStage;
import com.gtlmodular.common.machine.independent.runtime.IndependentBatchHost;
import com.gtlmodular.common.machine.independent.runtime.IndependentBatchPolicy;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;

import static com.gtlmodular.common.machine.independent.persistence.BatchExecutionStage.*;
import static com.gtlmodular.common.machine.independent.core.IndependentSessionSnapshot.Phase.*;
import static org.junit.jupiter.api.Assertions.*;

class IndependentBatchPolicyTest {
    @Test
    void repeatedStartConsumesItemsAndFluidOnce() {
        var host = new Resources();
        var policy = new IndependentBatchPolicy<>(host);
        var work = new Work();
        policy.beginTick();
        assertTrue(policy.consumeInputs(work));
        assertTrue(policy.consumeInputs(work));
        assertEquals(9, host.items);
        assertEquals(900, host.fluid);
        assertEquals(1, host.beforeCalls);
        assertEquals(Input.CONSUMED, work.stage.input());
    }

    @Test
    void failedPreflightDoesNotOwnOrDebitAnything() {
        var host = new Resources();
        host.fluid = 99;
        host.offer(new Work());
        var session = session();
        tick(session, host, true);
        assertFalse(session.hasOwnedWork());
        assertEquals(10, host.items);
        assertEquals(99, host.fluid);
        assertEquals(BigInteger.ZERO, session.occupiedBudget());
    }

    @Test
    void competingBatchesCannotBothSpendLastInput() {
        var host = new Resources();
        host.items = 1;
        host.offer(new Work(), new Work());
        var session = session();
        tick(session, host, true);
        assertEquals(1, session.batches().size());
        assertEquals(0, host.items);
        assertEquals(900, host.fluid);
    }

    @Test
    void partialInputRetainsOwnershipWithoutRefundOrReplay() {
        var host = new Resources();
        host.failAfterItem = true;
        var work = new Work();
        host.offer(work);
        var session = session();
        tick(session, host, true);
        assertEquals(INPUT_UNCERTAIN, session.batches().get(0).phase());
        assertEquals(Input.UNCERTAIN, work.stage.input());
        assertThrows(IllegalArgumentException.class, () -> work.stage.requireRetryable());
        assertEquals(9, host.items);
        assertEquals(1000, host.fluid);
        assertEquals(100, work.fluidRemaining);
        host.failAfterItem = false;
        tick(session, host, true);
        assertEquals(9, host.items);
        assertEquals(1000, host.fluid);
        assertTrue(session.hasOwnedWork());
    }

    @Test
    void hookFailureBeforeDebitWaitsWithoutAcquiringInputOwnership() {
        var host = new Resources();
        host.beforeThrows = true;
        var work = new Work();
        host.offer(work);
        var session = session();
        tick(session, host, true);
        assertFalse(session.hasOwnedWork());
        assertEquals(Input.PREPARED, work.stage.input());
        assertTrue(host.waitReason.contains("before callback"));
        assertEquals(10, host.items);
        assertEquals(1000, host.fluid);
        host.beforeThrows = false;
        host.offer(work);
        tick(session, host, true);
        assertEquals(2, host.beforeCalls);
        assertEquals(9, host.items);
        assertEquals(900, host.fluid);
        assertNull(session.batches().get(0).failure());
    }

    @Test
    void failedWaitReportingBeforeDebitDoesNotCreateAnUncertainArchive() {
        var host = new Resources();
        host.beforeThrows = true;
        host.waitThrows = true;
        var work = new Work();
        host.offer(work);
        var session = session();
        tick(session, host, true);
        assertFalse(session.hasOwnedWork());
        assertEquals(BigInteger.ZERO, session.occupiedBudget());
        assertEquals(Input.PREPARED, work.stage.input());
        assertEquals(10, host.items);
        assertEquals(1000, host.fluid);
        assertTrue(session.snapshot(Work::copy, raw -> raw).batches().isEmpty());
    }

    @Test
    void failureAfterCommittedInputKeepsRunningPhaseAndCanResumeWithoutReconsuming() {
        var host = new Resources();
        host.consumedStageThrows = true;
        host.offer(new Work());
        var session = session();
        tick(session, host, true);
        var batch = session.batches().get(0);
        assertEquals(RUNNING, batch.phase());
        assertNotNull(batch.failure());
        assertDoesNotThrow(() -> batch.payload().stage.validateOwned(batch.phase(), batch.failure()));
        assertEquals(9, host.items);
        assertEquals(900, host.fluid);
        tick(session, host, false);
        assertEquals(0, session.batches().get(0).progress());
        host.consumedStageThrows = false;
        session.retry(session.failedBatches().keySet().iterator().next());
        tick(session, host, false);
        tick(session, host, false);
        assertFalse(session.hasOwnedWork());
        assertEquals(9, host.items);
        assertEquals(900, host.fluid);
        assertEquals(3, host.delivered);
    }

    @Test
    void temporaryUnsupportedPreconditionWaitsAndResumesAcrossRestore() {
        var host = new Resources();
        host.offer(new Work());
        var session = session();
        tick(session, host, true);
        host.unsupported = true;
        tick(session, host, false);
        assertEquals(0, session.batches().get(0).progress());
        assertNull(session.batches().get(0).failure());
        assertEquals(10, host.energy);
        assertTrue(host.waitReason.contains("unsupported handler"));
        var restored = IndependentBatchSession.restore(session.snapshot(Work::copy, raw -> raw));
        host.unsupported = false;
        tick(restored, host, false);
        tick(restored, host, false);
        assertFalse(restored.hasOwnedWork());
        assertEquals(9, host.items);
        assertEquals(900, host.fluid);
        assertEquals(8, host.energy);
        assertEquals(3, host.delivered);
    }

    @Test
    void pendingPreconditionWaitDoesNotChargeEnergyOrRepeatCompletionHook() {
        var host = new Resources();
        host.capacity = 1;
        host.offer(new Work());
        var session = session();
        tick(session, host, true);
        tick(session, host, false);
        tick(session, host, false);
        host.unsupported = true;
        tick(session, host, false);
        assertNull(session.batches().get(0).failure());
        assertEquals(8, host.energy);
        assertEquals(1, host.afterCalls);
        assertEquals(1, host.delivered);
        host.unsupported = false;
        host.capacity = 2;
        tick(session, host, false);
        assertFalse(session.hasOwnedWork());
        assertEquals(1, host.afterCalls);
        assertEquals(3, host.delivered);
        assertEquals(8, host.energy);
    }

    @Test
    void lastEnergyGoesToOnlyOneBatchAndWorkingHookRunsOnce() {
        var host = new Resources();
        host.energy = 1;
        host.offer(new Work(), new Work());
        var session = session();
        tick(session, host, true);
        tick(session, host, false);
        assertEquals(List.of(1, 0), session.batches().stream().map(IndependentSessionSnapshot.Batch::progress).toList());
        assertEquals(0, host.energy);
        assertEquals(1, host.onCalls);
        host.energy = 3;
        tick(session, host, false);
        assertEquals(2, host.onCalls);
    }

    @Test
    void workingVetoKeepsPaidTickAndDoesNotPayAgainAfterResume() {
        var host = new Resources();
        var work = new Work();
        host.offer(work);
        var session = session();
        tick(session, host, true);
        host.allowWorking = false;
        tick(session, host, false);
        assertEquals(0, session.batches().get(0).progress());
        assertEquals(9, host.energy);
        tick(session, host, false);
        assertEquals(9, host.energy);
        host.allowWorking = true;
        tick(session, host, false);
        assertEquals(1, session.batches().get(0).progress());
        assertEquals(9, host.energy);
    }

    @Test
    void throwingWorkingHookIsNotRepeatedByOtherBatchesAndRetryKeepsPaidTick() {
        var host = new Resources();
        host.offer(new Work(), new Work());
        var session = session();
        tick(session, host, true);
        host.onThrows = true;
        tick(session, host, false);
        assertEquals(1, host.onCalls);
        assertEquals(9, host.energy);
        assertNotNull(session.batches().get(0).failure());
        assertNull(session.batches().get(1).failure());
        host.onThrows = false;
        tick(session, host, false);
        assertEquals(List.of(0, 1), session.batches().stream().map(IndependentSessionSnapshot.Batch::progress).toList());
        session.retry(session.failedBatches().keySet().iterator().next());
        tick(session, host, false);
        tick(session, host, false);
        assertFalse(session.hasOwnedWork());
        assertEquals(6, host.energy);
        assertEquals(6, host.delivered);
        assertEquals(8, host.items);
        assertEquals(800, host.fluid);
    }

    @Test
    void partialTickOutputDoesNotRepeatPowerDebit() {
        var host = new Resources();
        host.tickCapacity = 0;
        host.offer(new Work());
        var session = session();
        tick(session, host, true);
        tick(session, host, false);
        tick(session, host, false);
        assertEquals(9, host.energy);
        assertEquals(0, session.batches().get(0).progress());
        assertEquals(0, host.onCalls);
        host.tickCapacity = 1;
        tick(session, host, false);
        assertEquals(1, session.batches().get(0).progress());
        assertEquals(9, host.energy);
    }

    @Test
    void completedBatchesRetryOnlyRemainingOutputsAndRunHookOnce() {
        var host = new Resources();
        host.capacity = 1;
        var work = new Work();
        host.offer(work);
        var session = session();
        tick(session, host, true);
        tick(session, host, false);
        tick(session, host, false);
        assertEquals(PENDING_SETTLEMENT, session.batches().get(0).phase());
        assertEquals(1, host.delivered);
        assertEquals(2, work.outputRemaining);
        assertEquals(1, host.afterCalls);
        assertEquals(BigInteger.ONE, session.occupiedBudget());
        int paid = host.energy;
        tick(session, host, false);
        assertEquals(paid, host.energy);
        host.capacity = 10;
        tick(session, host, false);
        assertFalse(session.hasOwnedWork());
        assertEquals(3, host.delivered);
        assertEquals(1, host.afterCalls);
        assertEquals(BigInteger.ZERO, session.occupiedBudget());
        var policy = new IndependentBatchPolicy<>(host);
        policy.beginTick();
        assertTrue(policy.settleOutputs(work));
        assertEquals(3, host.delivered);
        assertEquals(1, host.afterCalls);
    }

    @Test
    void queueReleasesOnlyTheBatchThatActuallySettled() {
        var host = new Resources();
        host.capacity = 3;
        host.offer(new Work(), new Work());
        var session = session();
        tick(session, host, true);
        tick(session, host, false);
        tick(session, host, false);
        assertEquals(1, session.batches().size());
        assertEquals(PENDING_SETTLEMENT, session.batches().get(0).phase());
        assertEquals(BigInteger.ONE, session.occupiedBudget());
        assertEquals(3, host.delivered);
        assertEquals(2, host.afterCalls);
    }

    @Test
    void unknownOutputExceptionPreservesJournalAndCannotReplay() {
        var host = new Resources();
        host.outputThrows = true;
        var work = new Work();
        host.offer(work);
        var session = session();
        tick(session, host, true);
        tick(session, host, false);
        tick(session, host, false);
        assertNotNull(session.batches().get(0).failure());
        assertEquals(1, host.delivered);
        assertEquals(2, work.outputRemaining);
        host.outputThrows = false;
        tick(session, host, false);
        assertEquals(1, host.delivered);
        assertTrue(session.hasOwnedWork());
        work.stage.requireRetryable();
        session.retry(session.failedBatches().keySet().iterator().next());
        tick(session, host, false);
        assertFalse(session.hasOwnedWork());
        assertEquals(3, host.delivered);
        assertEquals(1, host.afterCalls);
        assertEquals(8, host.energy);
        assertEquals(9, host.items);
    }

    @Test
    void uncertainCompletionHookCannotRunTwice() {
        var host = new Resources();
        host.afterThrows = true;
        var work = new Work();
        host.offer(work);
        var session = session();
        tick(session, host, true);
        tick(session, host, false);
        tick(session, host, false);
        assertEquals(Hook.IN_FLIGHT, work.stage.afterWorking());
        assertThrows(IllegalArgumentException.class, () -> work.stage.requireRetryable());
        host.afterThrows = false;
        tick(session, host, false);
        assertEquals(1, host.afterCalls);
        assertEquals(0, host.delivered);
    }

    @Test
    void finishPauseStopsLaterSettlementAndNewCandidates() {
        var host = new Resources();
        host.suspendAfterFinish = true;
        host.offer(new Work(), new Work());
        var session = session();
        tick(session, host, true);
        tick(session, host, false);
        tick(session, host, false);
        assertEquals(1, session.batches().size());
        assertEquals(1, host.afterCalls);
        tick(session, host, false);
        assertEquals(1, host.afterCalls);
        host.canWork = true;
        host.suspendAfterFinish = false;
        tick(session, host, false);
        assertFalse(session.hasOwnedWork());
    }

    @Test
    void lostStructureOrPausedMachineDoesNotConsumeOrDiscardWork() {
        var host = new Resources();
        host.offer(new Work());
        var session = session();
        tick(session, host, true);
        host.canWork = false;
        tick(session, host, false);
        assertEquals(10, host.energy);
        assertEquals(0, session.batches().get(0).progress());
        var policy = new IndependentBatchPolicy<>(host);
        policy.beginTick();
        session.tick(new IndependentSessionControl(false, false, false, false, 2, 10), policy);
        assertEquals(10, host.energy);
        assertTrue(session.hasOwnedWork());
        host.canWork = true;
        tick(session, host, false);
        tick(session, host, false);
        assertFalse(session.hasOwnedWork());
    }

    @Test
    void disabledEmptySessionHasNoHostCalls() {
        var host = new Resources();
        var policy = new IndependentBatchPolicy<>(host);
        session().tick(new IndependentSessionControl(false, false, false, true, 2, 10), policy);
        assertEquals(0, host.calls);
    }

    private static IndependentBatchSession<Work, String> session() { return new IndependentBatchSession<>(); }
    private static void tick(IndependentBatchSession<Work, String> session, Resources host, boolean admit) {
        var policy = new IndependentBatchPolicy<>(host);
        policy.beginTick();
        session.tick(new IndependentSessionControl(admit, false, false, true, 2, 10), policy);
    }

    private static final class Work {
        BatchExecutionStage stage = new BatchExecutionStage(Input.PREPARED, Output.UNROLLED, Hook.NOT_RUN);
        long itemRemaining = 1, fluidRemaining = 100, outputRemaining = 3;
        boolean paidTick, tickOutput;

        Work copy() {
            var copy = new Work();
            copy.stage = stage;
            copy.itemRemaining = itemRemaining;
            copy.fluidRemaining = fluidRemaining;
            copy.outputRemaining = outputRemaining;
            copy.paidTick = paidTick;
            copy.tickOutput = tickOutput;
            return copy;
        }
    }

    private static final class Resources implements IndependentBatchHost<Work> {
        final Queue<Work> candidates = new ArrayDeque<>();
        long items = 10, fluid = 1000;
        int energy = 10, capacity = 100, tickCapacity = 100, delivered;
        int calls, beforeCalls, onCalls, afterCalls;
        boolean canWork = true, allowWorking = true, failAfterItem, beforeThrows, onThrows, afterThrows, outputThrows, suspendAfterFinish, unsupported;
        boolean waitThrows, consumedStageThrows;
        String waitReason;
        void offer(Work... work) { candidates.addAll(List.of(work)); }
        @Override public void beginTick() { calls++; }
        @Override public boolean canWork(Work work) {
            calls++;
            if (unsupported) throw new IllegalArgumentException("unsupported handler");
            return canWork;
        }
        @Override public boolean waitFor(Work work, String reason) {
            if (waitThrows) throw new IllegalStateException("wait reporting");
            waitReason = reason;
            return false;
        }
        @Override public boolean canAdmit() { calls++; return canWork; }
        @Override public BatchExecutionStage stage(Work work) { return work.stage; }
        @Override public void stage(Work work, BatchExecutionStage stage) {
            work.stage = stage;
            if (consumedStageThrows && stage.input() == Input.CONSUMED) throw new IllegalStateException("committed input callback");
        }
        @Override public IndependentBatchOperations.Candidate<Work> findBatch(int slots, long budget) {
            calls++;
            var next = candidates.poll();
            return next == null ? null : new IndependentBatchOperations.Candidate<>(next, 2, 1);
        }
        @Override public boolean prepareInputs(Work work) {
            beforeCalls++;
            if (beforeThrows) throw new IllegalStateException("before callback");
            if (items < work.itemRemaining || fluid < work.fluidRemaining) return false;
            work.stage = new BatchExecutionStage(Input.PREPARED, Output.READY, Hook.NOT_RUN);
            return true;
        }
        @Override public boolean consumeInputs(Work work) {
            CountedTransfer.execute(work.itemRemaining, requested -> { items -= requested; return requested; }, left -> work.itemRemaining = left);
            if (failAfterItem) throw new IllegalStateException("fluid handler failed after item debit");
            CountedTransfer.execute(work.fluidRemaining, requested -> { fluid -= requested; return requested; }, left -> work.fluidRemaining = left);
            return true;
        }
        @Override public boolean tickIo(Work work) {
            if (!work.paidTick) {
                if (energy == 0) return false;
                energy--;
                work.paidTick = true;
            }
            if (!work.tickOutput) {
                if (tickCapacity == 0) return false;
                tickCapacity--;
                work.tickOutput = true;
            }
            return true;
        }
        @Override public void completeTick(Work work) { work.paidTick = false; work.tickOutput = false; }
        @Override public boolean onWorking() {
            onCalls++;
            if (onThrows) throw new IllegalStateException("working callback");
            return allowWorking;
        }
        @Override public void afterWorking(Work work) { afterCalls++; if (afterThrows) throw new IllegalStateException("completion callback"); }
        @Override public boolean output(Work work) {
            CountedTransfer.execute(work.outputRemaining, requested -> {
                int amount = (int) Math.min(requested, outputThrows ? 1 : capacity);
                capacity -= amount;
                delivered += amount;
                return amount;
            }, left -> work.outputRemaining = left);
            if (outputThrows) throw new IllegalStateException("unknown downstream failure");
            work.stage = new BatchExecutionStage(Input.CONSUMED, work.outputRemaining == 0 ? Output.COMPLETE : Output.PARTIAL, Hook.DONE);
            return work.outputRemaining == 0;
        }
        @Override public boolean suspendAfterFinish() {
            if (suspendAfterFinish) canWork = false;
            return suspendAfterFinish;
        }
    }
}
