package com.gtlmodular.common.machine.independent;

import com.gtlmodular.common.machine.independent.io.EnergyDebitContext;
import com.gtlmodular.common.machine.independent.io.SharedTickPower;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class SharedTickPowerTest {
    @Test
    void legacyFullVoltageBatchesCannotMultiplyTheMachineDebit() {
        var power = new SharedTickPower();
        power.reset(BigInteger.valueOf(100));
        assertTrue(power.reserve(BigInteger.valueOf(100)));
        assertFalse(power.reserve(BigInteger.valueOf(100)));
        assertEquals(0, power.limit(Long.MAX_VALUE));
        power.reset(BigInteger.valueOf(100));
        assertTrue(power.reserve(BigInteger.valueOf(100)));
    }

    @Test
    void partiallyPaidTickSurvivesVoltageReductionAndReloadWithoutLosingOrRepeatingEnergy() {
        long remaining = 100;
        long stored = 1000;
        int progress = 0;
        for (int tick = 0; tick < 3; tick++) {
            var power = new SharedTickPower();
            power.reset(BigInteger.valueOf(40));
            long debit = power.limit(remaining);
            assertTrue(power.reserve(BigInteger.valueOf(debit)));
            var journal = new AtomicLong(remaining);
            Object container = new Object();
            long afterDebit = stored - debit;
            try (var context = EnergyDebitContext.open(container, stored, remaining, true, journal::set)) {
                context.execute(() -> {
                    EnergyDebitContext.changed(container, afterDebit);
                    return debit;
                });
            }
            remaining = journal.get();
            stored = afterDebit;
            if (remaining == 0) progress++;
            assertEquals(tick == 2 ? 1 : 0, progress);
        }
        assertEquals(900, stored);
        assertEquals(0, remaining);
    }

    @Test
    void notificationFailureKeepsBothThePaidJournalAndTheMachineLimit() {
        var power = new SharedTickPower();
        power.reset(BigInteger.valueOf(100));
        var journal = new AtomicLong(150);
        var container = new Object();
        assertTrue(power.reserve(BigInteger.valueOf(100)));
        try (var context = EnergyDebitContext.open(container, 1000, 150, true, journal::set)) {
            assertThrows(IllegalStateException.class, () -> context.execute(() -> {
                EnergyDebitContext.changed(container, 900);
                throw new IllegalStateException("notification failed");
            }));
        }
        assertEquals(50, journal.get());
        assertFalse(power.reserve(BigInteger.ONE));
    }

    @Test
    void topUpsUseOnlyUnreservedPowerAndDoNotOverflowOnOldOvercommit() {
        assertEquals(40, SharedTickPower.unreserved(100, List.of(BigInteger.valueOf(60))));
        assertEquals(0, SharedTickPower.unreserved(100, List.of(BigInteger.valueOf(100), BigInteger.valueOf(100))));
        assertEquals(0, SharedTickPower.unreserved(Long.MAX_VALUE, List.of(BigInteger.valueOf(Long.MAX_VALUE), BigInteger.ONE)));
        assertEquals(100, SharedTickPower.unreserved(100, List.of()));
    }

    @Test
    void wirelessBatchDebitsShareTheActualAccountBudgetBeyondLongRange() {
        var balance = BigInteger.valueOf(Long.MAX_VALUE).pow(2);
        var power = new SharedTickPower();
        power.reset(balance);
        assertTrue(power.reserve(balance.subtract(BigInteger.ONE)));
        assertFalse(power.reserve(BigInteger.TWO));
        assertTrue(power.reserve(BigInteger.ONE));
        assertEquals(BigInteger.ZERO, power.remaining());
    }

    @Test
    void invalidPowerCannotCreditTheMeter() {
        var power = new SharedTickPower();
        assertThrows(IllegalArgumentException.class, () -> power.reset(BigInteger.ONE.negate()));
        assertThrows(IllegalArgumentException.class, () -> power.reserve(BigInteger.ONE.negate()));
        assertThrows(IllegalArgumentException.class, () -> power.limit(-1));
    }
}
