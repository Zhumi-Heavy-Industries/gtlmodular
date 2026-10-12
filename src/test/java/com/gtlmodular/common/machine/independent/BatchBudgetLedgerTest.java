package com.gtlmodular.common.machine.independent;

import com.gtlmodular.common.machine.independent.core.BatchBudgetLedger;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.*;

class BatchBudgetLedgerTest {
    @Test
    void limitsApplyToAllReservationsAndShrinkingDoesNotEraseThem() {
        var ledger = new BatchBudgetLedger<Object>();
        var first = new Object();
        var second = new Object();
        assertTrue(ledger.reserve(first, 7, 10));
        assertFalse(ledger.reserve(second, 4, 10));
        assertEquals(3, ledger.available(10));
        assertTrue(ledger.reserve(second, 3, 10));

        assertEquals(0, ledger.available(2));
        assertFalse(ledger.reserve(new Object(), 0, 2));
        assertEquals(BigInteger.TEN, ledger.occupied());
        ledger.release(first);
        assertEquals(7, ledger.available(10));
        assertThrows(IllegalStateException.class, () -> ledger.release(first));
        ledger.release(second);
        assertEquals(BigInteger.ZERO, ledger.occupied());
    }

    @Test
    void restoringBeyondLongRangePreservesEveryReservation() {
        var ledger = new BatchBudgetLedger<Object>();
        var first = new Object();
        var second = new Object();
        ledger.restore(first, Long.MAX_VALUE);
        ledger.restore(second, Long.MAX_VALUE);

        assertEquals(BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.TWO), ledger.occupied());
        assertEquals(0, ledger.available(Long.MAX_VALUE));
        assertFalse(ledger.reserve(new Object(), 0, Long.MAX_VALUE));
        ledger.release(first);
        assertEquals(BigInteger.valueOf(Long.MAX_VALUE), ledger.occupied());
        ledger.release(second);
        assertEquals(Long.MAX_VALUE, ledger.available(Long.MAX_VALUE));
    }

    @Test
    void equalRecipesAreSeparateOwnersButAnOwnerCannotReserveTwice() {
        var ledger = new BatchBudgetLedger<String>();
        String first = new String("same recipe");
        String second = new String("same recipe");
        assertTrue(ledger.reserve(first, 1, 2));
        assertTrue(ledger.reserve(second, 1, 2));
        assertThrows(IllegalStateException.class, () -> ledger.restore(first, 0));
        assertThrows(IllegalArgumentException.class, () -> ledger.reserve(new Object().toString(), -1, 2));
        ledger.release(first);
        assertEquals(BigInteger.ONE, ledger.occupied());
        ledger.release(second);
        assertEquals(BigInteger.ZERO, ledger.occupied());
    }
}
