package com.gtlmodular.common.machine.independent;

import com.gtlmodular.common.machine.independent.control.BatchOwnershipLedger;
import com.gtlmodular.common.machine.independent.control.BatchOwnershipLedger.Owner;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BatchOwnershipLedgerTest {
    private final Owner source = new Owner("minecraft:overworld", 12);
    private final Owner target = new Owner("minecraft:the_nether", 34);
    private final BatchOwnershipLedger ledger = new BatchOwnershipLedger(Map.of());

    @Test
    void cloneCannotClaimWhileSourceStillOwnsBatch() {
        UUID token = ledger.issue(source);
        assertNull(ledger.claim(token, target, owner -> false));
        assertEquals(source, ledger.entry(token).owner());
        assertFalse(ledger.entry(token).spent());
    }

    @Test
    void moveClaimsExactlyOnceAcrossDimensions() {
        UUID token = ledger.issue(source);
        UUID moved = ledger.claim(token, target, owner -> true);
        assertNotNull(moved);
        assertNotEquals(token, moved);
        assertEquals(target, ledger.entry(moved).owner());
        assertNull(ledger.claim(token, source, owner -> true));
        assertNull(ledger.claim(token, target, owner -> true));
    }

    @Test
    void replayRemainsSpentAfterRestartAndAfterDestinationIsRemoved() {
        UUID item = ledger.issue(source);
        UUID placed = ledger.claim(item, target, owner -> true);
        ledger.retire(placed);
        var restored = new BatchOwnershipLedger(ledger.snapshot());
        assertNull(restored.claim(item, target, owner -> true));
        assertNull(restored.claim(placed, source, owner -> true));
    }

    @Test
    void reloadRotatesTokenEvenAtSamePosition() {
        UUID beforeReload = ledger.issue(source);
        UUID afterReload = ledger.claim(beforeReload, source, owner -> fail("same owner needs no vacancy lookup"));
        assertNotNull(afterReload);
        assertNull(ledger.claim(beforeReload, source, owner -> true));
    }

    @Test
    void settledBatchCannotBeRevivedFromOldBlockSnapshot() {
        UUID token = ledger.issue(source);
        ledger.retire(token);
        assertNull(ledger.claim(token, target, owner -> true));
    }

    @Test
    void legacyItemIsAcceptedOnceAndSurvivesSaveReload() {
        UUID legacy = UUID.randomUUID();
        UUID placed = ledger.claim(legacy, source, owner -> fail("legacy item has no source"));
        assertNotNull(placed);
        var restored = new BatchOwnershipLedger(ledger.snapshot());
        assertNull(restored.claim(legacy, target, owner -> true));
        assertNotNull(restored.claim(placed, source, owner -> false));
    }

    @Test
    void failedSourceLookupDoesNotConsumeTheItemToken() {
        UUID token = ledger.issue(source);
        assertThrows(IllegalStateException.class, () -> ledger.claim(token, target, owner -> {
            throw new IllegalStateException("source unavailable");
        }));
        assertFalse(ledger.entry(token).spent());
        assertNotNull(ledger.claim(token, target, owner -> true));
    }

    @Test
    void identicalOwnersStillHaveIndependentTokens() {
        UUID first = ledger.issue(source);
        UUID second = ledger.issue(source);
        ledger.retire(first);
        assertNotEquals(first, second);
        assertNotNull(ledger.claim(second, target, owner -> true));
    }
}
