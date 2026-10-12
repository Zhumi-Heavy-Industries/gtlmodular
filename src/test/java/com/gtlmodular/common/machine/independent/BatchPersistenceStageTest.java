package com.gtlmodular.common.machine.independent;

import com.gtlmodular.common.machine.independent.core.IndependentSessionSnapshot.Phase;
import com.gtlmodular.common.machine.independent.persistence.BatchExecutionStage;
import com.gtlmodular.common.machine.independent.persistence.BatchRecipeExtensions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigInteger;

import static com.gtlmodular.common.machine.independent.persistence.BatchExecutionStage.*;
import static com.gtlmodular.common.machine.independent.persistence.BatchRecipeExtensions.*;
import static org.junit.jupiter.api.Assertions.*;

class BatchPersistenceStageTest {
    @Test
    void wirelessZeroAndMissingEnergyRemainDifferent() {
        assertEquals(BigInteger.ZERO, extensions(Power.WIRELESS_ACCOUNT, BigInteger.ZERO).wirelessEu());
        assertNull(extensions(Power.WIRED, null).wirelessEu());
        assertThrows(IllegalArgumentException.class, () -> extensions(Power.WIRELESS_ACCOUNT, null));
        assertThrows(IllegalArgumentException.class, () -> extensions(Power.WIRELESS_TERMINAL, null));
        assertThrows(IllegalArgumentException.class, () -> extensions(Power.WIRED, BigInteger.ZERO));
    }

    @Test
    void wirelessSignsAndAmountsBeyondLongArePreserved() {
        for (String value : new String[]{"0", "-1", "1", "-1234567890123456789012345678901234567890"}) {
            assertEquals(value, BatchRecipeExtensions.parseWireless(value).toString());
        }
        assertThrows(IllegalArgumentException.class, () -> BatchRecipeExtensions.parseWireless("1".repeat(4097)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "-0", "+1", "01", " 1", "1.0", "1e3", "null"})
    void invalidWirelessNumbersAreRejectedWithoutZeroFallback(String value) {
        assertThrows(IllegalArgumentException.class, () -> BatchRecipeExtensions.parseWireless(value));
    }

    @Test
    void numericRecipeExtensionsCannotBeClampedOnLoad() {
        assertThrows(IllegalArgumentException.class, () -> new BatchRecipeExtensions(Kind.NORMAL, Power.WIRED,
                -1, 1, 1, 0, 0, false, false, false, null));
        assertThrows(IllegalArgumentException.class, () -> new BatchRecipeExtensions(Kind.NORMAL, Power.WIRED,
                1, 1, 0, 0, 0, false, false, false, null));
        assertThrows(IllegalArgumentException.class, () -> new BatchRecipeExtensions(Kind.NORMAL, Power.WIRED,
                1, 1, 1, -1, 0, false, false, false, null));
    }

    @Test
    void partiallyDeliveredOutputsAndCompletedHooksCanResumeWithoutReplayingHook() {
        var stage = new BatchExecutionStage(Input.CONSUMED, Output.PARTIAL, Hook.DONE);
        assertDoesNotThrow(() -> stage.validateOwned(Phase.PENDING_SETTLEMENT, null));
        assertDoesNotThrow(() -> stage.validateOutputs(true, true));
        assertThrows(IllegalArgumentException.class, () -> stage.validateOwned(Phase.RUNNING, null));
    }

    @Test
    void uncertainHookAndUnpaidInputsCannotSilentlyResume() {
        var hook = new BatchExecutionStage(Input.CONSUMED, Output.COMPLETE, Hook.IN_FLIGHT);
        assertThrows(IllegalArgumentException.class, () -> hook.validateOwned(Phase.PENDING_SETTLEMENT, null));
        assertDoesNotThrow(() -> hook.validateOwned(Phase.PENDING_SETTLEMENT, "hook failed"));
        var prepared = new BatchExecutionStage(Input.PREPARED, Output.UNROLLED, Hook.NOT_RUN);
        assertThrows(IllegalArgumentException.class, () -> prepared.validateOwned(Phase.RUNNING, null));
        var uncertain = new BatchExecutionStage(Input.UNCERTAIN, Output.UNROLLED, Hook.NOT_RUN);
        assertDoesNotThrow(() -> uncertain.validateOwned(Phase.INPUT_UNCERTAIN, "input failed"));
        assertThrows(IllegalArgumentException.class, () -> uncertain.validateOwned(Phase.RUNNING, "input failed"));
    }

    @Test
    void outputMarkersCannotLoseResolvedResultsOrRemainders() {
        var unrolled = new BatchExecutionStage(Input.CONSUMED, Output.UNROLLED, Hook.NOT_RUN);
        assertThrows(IllegalArgumentException.class, () -> unrolled.validateOutputs(true, false));
        var ready = new BatchExecutionStage(Input.CONSUMED, Output.READY, Hook.NOT_RUN);
        assertThrows(IllegalArgumentException.class, () -> ready.validateOutputs(false, true));
        assertThrows(IllegalArgumentException.class, () -> ready.validateOutputs(true, false));
        var complete = new BatchExecutionStage(Input.CONSUMED, Output.COMPLETE, Hook.DONE);
        assertDoesNotThrow(() -> complete.validateOutputs(true, false));
        assertThrows(IllegalArgumentException.class, () -> complete.validateOutputs(true, true));
    }

    private BatchRecipeExtensions extensions(Power power, BigInteger eu) {
        return new BatchRecipeExtensions(Kind.WIRELESS, power, Long.MAX_VALUE, Integer.MAX_VALUE, 2,
                3, 4, true, true, false, eu);
    }

    @Test
    void paidOrPartiallyExecutedTickCanBePreservedWithoutMarkingItFailed() {
        var stage = new BatchExecutionStage(Input.CONSUMED, Output.READY, Hook.NOT_RUN);
        assertDoesNotThrow(() -> stage.validateTickJournal(true, true, true, true));
        assertDoesNotThrow(() -> stage.validateTickJournal(true, true, false, false));
        assertDoesNotThrow(() -> stage.validateOwned(Phase.RUNNING, null));
        assertThrows(IllegalArgumentException.class, () -> stage.validateTickJournal(false, true, false, true));
        assertThrows(IllegalArgumentException.class, () -> stage.validateTickJournal(true, false, true, true));
        assertThrows(IllegalArgumentException.class, () -> stage.validateTickJournal(false, false, true, true));
        assertThrows(IllegalArgumentException.class, () -> stage.validateTickJournal(true, true, true, false));
        var unpaid = new BatchExecutionStage(Input.PREPARED, Output.UNROLLED, Hook.NOT_RUN);
        assertThrows(IllegalArgumentException.class, () -> unpaid.validateTickJournal(true, true, false, false));
    }
}
