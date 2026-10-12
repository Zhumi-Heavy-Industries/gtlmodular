package com.gtlmodular.common.machine.independent;

import com.gtlmodular.common.machine.independent.control.*;
import com.gtlmodular.common.machine.independent.core.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class IndependentHandoffTest {
    @Test
    void disabledAndEmptyNeverInterceptsOrCreatesWork() {
        for (boolean upstream : new boolean[]{false, true}) {
            var decision = IndependentHandoff.decide(false, false, true, true, upstream, false, false);
            assertFalse(decision.runIndependent());
            assertFalse(decision.admit());
            assertFalse(decision.blockUpstreamStarts());
        }
    }

    @Test
    void enableFinishesLegacyBatchBeforeTakingControlAndGatesItsSameTickRefill() {
        var session = new IndependentBatchSession<Object, String>();
        var resources = new Resources();
        for (int progress = 0; progress < 3; progress++) {
            var decision = IndependentHandoff.decide(false, true, true, true, true, false, false);
            assertFalse(decision.runIndependent());
            assertTrue(decision.blockUpstreamStarts());
        }
        assertEquals(0, resources.input);
        var decision = IndependentHandoff.decide(false, true, true, true, false, false, false);
        advance(session, decision, resources);
        assertEquals(1, resources.input);
        assertEquals(0, resources.energy);
        assertTrue(session.hasOwnedWork());
    }

    @ParameterizedTest
    @ValueSource(strings = {"config", "blacklist", "whitelist", "mode", "external"})
    void restoredBatchesDrainAcrossChangesWithoutNewInput(String change) {
        var original = new IndependentBatchSession<Object, String>();
        var resources = new Resources();
        advance(original, IndependentHandoff.decide(false, true, true, true, false, false, false), resources);
        var session = IndependentBatchSession.restore(original.snapshot(value -> value, value -> value));
        boolean eligible = change.equals("mode") || change.equals("external");
        var closing = IndependentHandoff.decide(true, eligible, true, !change.equals("mode"),
                false, change.equals("external"), false);
        assertTrue(closing.runIndependent());
        assertTrue(closing.blockUpstreamStarts());
        assertFalse(closing.admit());
        advance(session, closing, resources);
        assertTrue(session.drainRequested());
        // 重新开启也不能撤销此前的排空承诺。
        advance(session, IndependentHandoff.decide(true, true, true, true, false, false, false), resources);
        assertFalse(session.hasOwnedWork());
        assertEquals(1, resources.input);
        assertEquals(2, resources.energy);
        assertEquals(1, resources.output);
    }

    @Test
    void externalOwnershipAndUnreadableWorkPreventTakeover() {
        for (boolean wants : new boolean[]{false, true}) {
            var decision = IndependentHandoff.decide(false, true, true, true, false, wants, true);
            assertFalse(decision.runIndependent());
            assertFalse(decision.blockUpstreamStarts());
        }
        var session = new IndependentBatchSession<Object, String>();
        session.retain("unknown payload", "future version");
        var decision = IndependentHandoff.decide(true, false, true, false, false, false, false);
        assertTrue(decision.runIndependent());
        var resources = new Resources();
        advance(session, decision, resources);
        assertTrue(session.hasOwnedWork());
        assertEquals(0, resources.input);
        assertEquals(0, resources.energy);
        assertEquals("unknown payload", session.retainedRecords().get(0).raw());
    }

    @Test
    void simultaneousOwnershipPausesBothInsteadOfRunningEitherLedger() {
        var session = new IndependentBatchSession<Object, String>();
        var resources = new Resources();
        advance(session, IndependentHandoff.decide(false, true, true, true, false, false, false), resources);
        var conflict = IndependentHandoff.decide(true, true, true, true, false, true, true);
        assertTrue(conflict.externalConflict());
        assertFalse(conflict.admit());
        assertTrue(conflict.blockUpstreamStarts());
        var result = session.tick(new IndependentSessionControl(false, true, true, true, 1, 1), null);
        assertTrue(result.cancelUpstreamTick());
        assertEquals(0, session.batches().get(0).progress());
        assertEquals(1, resources.input);
        assertEquals(0, resources.energy);
    }

    @Test
    void outputBlockedHandoffKeepsOwnershipAndBudgetUntilDelivery() {
        var session = new IndependentBatchSession<Object, String>();
        var resources = new Resources();
        advance(session, IndependentHandoff.decide(false, true, true, true, false, false, false), resources);
        resources.space = false;
        for (int tick = 0; tick < 6; tick++) {
            var decision = IndependentHandoff.decide(true, true, true, true, false, true, false);
            advance(session, decision, resources);
            assertTrue(session.hasOwnedWork());
            assertEquals(1, session.occupiedBudget().intValueExact());
        }
        assertEquals(2, resources.energy);
        resources.space = true;
        advance(session, IndependentHandoff.decide(true, true, true, true, false, true, false), resources);
        assertEquals(1, resources.output);
        assertFalse(session.hasOwnedWork());
        assertFalse(IndependentHandoff.decide(false, true, true, true, false, true, false).runIndependent());
    }

    @Test
    void unknownSubclassCannotAdmitAndMutableSingleModePassesThrough() {
        var unsupported = IndependentHandoff.decide(false, true, false, true, false, false, false);
        assertFalse(unsupported.runIndependent());
        assertFalse(unsupported.admit());
        assertFalse(unsupported.blockUpstreamStarts());
        var single = IndependentHandoff.decide(false, true, true, false, false, false, false);
        assertFalse(single.runIndependent());
        assertFalse(single.blockUpstreamStarts());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void unsupportedEmptyMachineAllowsBothLegacyProgressAndRefill(boolean upstreamPending) {
        var decision = IndependentHandoff.decide(false, true, false, true, upstreamPending, false, false);
        assertFalse(decision.runIndependent());
        assertFalse(decision.admit());
        assertFalse(decision.blockUpstreamStarts());
        assertFalse(decision.externalConflict());
    }

    @Test
    void losingSupportCannotHandPaidOrUnreadableWorkToUpstream() {
        var decision = IndependentHandoff.decide(true, true, false, true, false, false, false);
        assertTrue(decision.runIndependent());
        assertTrue(decision.blockUpstreamStarts());
        assertFalse(decision.admit());
        var session = new IndependentBatchSession<Object, String>();
        session.retain("paid unsupported batch", "unsupported handler");
        var resources = new Resources();
        advance(session, decision, resources);
        assertTrue(session.hasOwnedWork());
        assertEquals("paid unsupported batch", session.retainedRecords().get(0).raw());
        assertEquals(0, resources.input);
        assertEquals(0, resources.energy);
        assertEquals(0, resources.output);
    }

    @Test
    void legacyProgressCanPassWhileItsRefillWaitsForHandoff() {
        var guard = new IndependentTickGuard();
        var decision = IndependentHandoff.decide(false, true, true, true, true, false, false);
        guard.begin(10);
        guard.blockStarts(decision.blockUpstreamStarts());
        assertFalse(guard.cancelRepeated());
        assertTrue(guard.blocksStarts());
        guard.begin(11);
        assertFalse(guard.blocksStarts());
    }

    @Test
    void emptyAttemptAllowsAllUpstreamEntriesForTheRestOfTheTick() {
        var guard = new IndependentTickGuard();
        guard.begin(10);
        guard.enter();
        assertTrue(guard.blocksStarts());
        guard.passThrough();
        assertTrue(guard.blocksStarts());
        assertTrue(guard.cancelRepeated());
        guard.exit();
        assertTrue(guard.repeated(10));
        assertFalse(guard.cancelRepeated());
        assertFalse(guard.blocksStarts());
        guard.begin(11);
        guard.enter();
        guard.exit();
        assertTrue(guard.blocksStarts());
        assertTrue(guard.cancelRepeated());
    }

    @Test
    void reentrantAndRepeatedTickCannotAdvanceAgainIncludingFinalDrainTick() {
        var guard = new IndependentTickGuard();
        assertFalse(guard.repeated(10));
        guard.begin(10);
        guard.enter();
        assertTrue(guard.repeated(10));
        assertTrue(guard.repeated(11));
        assertTrue(guard.cancelRepeated());
        guard.exit();
        assertTrue(guard.repeated(10));
        assertTrue(guard.cancelRepeated());
        assertTrue(guard.blocksStarts());
        assertFalse(guard.repeated(11));
        guard.begin(11);
        assertFalse(guard.cancelRepeated());
        assertFalse(guard.blocksStarts());
    }

    @Test
    void reloadWakesPreviouslyIdleLoadedMachinesAndRemovesUnloadedWorlds() {
        var index = new LoadedMachineIndex<String>();
        String idle = new String("idle");
        String owned = new String("owned");
        String unloaded = new String("unloaded");
        index.add(idle);
        index.add(owned);
        index.add(unloaded);
        index.remove(unloaded);
        Set<String> wake = new HashSet<>();
        index.visit(wake::add);
        assertEquals(Set.of(idle, owned), wake);
        index.clear();
        AtomicInteger callbacks = new AtomicInteger();
        index.visit(machine -> callbacks.incrementAndGet());
        assertEquals(0, callbacks.get());
    }

    private static void advance(IndependentBatchSession<Object, String> session, IndependentHandoff decision, Resources resources) {
        assertTrue(decision.runIndependent());
        assertFalse(decision.externalConflict());
        session.tick(new IndependentSessionControl(decision.admit(), false, false, true, 1, 1), resources);
    }

    private static final class Resources implements IndependentBatchOperations<Object> {
        int input;
        int energy;
        int output;
        boolean space = true;

        @Override public Candidate<Object> findBatch(int slots, long budget) { return new Candidate<>(new Object(), 2, 1); }
        @Override public boolean consumeInputs(Object work) { input++; return true; }
        @Override public boolean tickInputs(Object work) { energy++; return true; }
        @Override public boolean settleOutputs(Object work) {
            if (!space) return false;
            output++;
            return true;
        }
    }
}
