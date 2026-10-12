package com.gtlmodular.common.machine.independent;

import com.gtlmodular.common.machine.independent.io.EnergyDebitContext;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class EnergyDebitContextTest {
    @Test
    void changedWithoutContextDoesNothing() {
        assertDoesNotThrow(() -> EnergyDebitContext.changed(new Object(), Long.MIN_VALUE));
    }

    @Test
    void debitIsRecordedBeforeANotificationCanThrow() {
        var container = new Object();
        var left = new AtomicLong(10);
        try (var context = EnergyDebitContext.open(container, 20, 10, true, left::set)) {
            assertThrows(IllegalStateException.class, () -> context.execute(() -> {
                EnergyDebitContext.changed(container, 14);
                throw new IllegalStateException("energy listener");
            }));
            assertEquals(4, left.get());
        }
        EnergyDebitContext.changed(container, 0);
        assertEquals(4, left.get());
    }

    @Test
    void nestedListenerChangesCannotBeChargedToTheSameBatch() {
        var container = new Object();
        var left = new AtomicLong(10);
        try (var context = EnergyDebitContext.open(container, 20, 10, true, left::set)) {
            context.execute(() -> {
                EnergyDebitContext.changed(new Object(), 1);
                assertEquals(10, left.get());
                EnergyDebitContext.changed(container, 15);
                EnergyDebitContext.changed(container, 0);
                return 5;
            });
        }
        assertEquals(5, left.get());
    }

    @Test
    void outputEnergyUsesActualIncreaseAndDoesNotOverflow() {
        var container = new Object();
        var left = new AtomicLong(Long.MAX_VALUE);
        try (var context = EnergyDebitContext.open(container, 0, Long.MAX_VALUE, false, left::set)) {
            assertEquals(Long.MAX_VALUE, context.execute(() -> {
                EnergyDebitContext.changed(container, Long.MAX_VALUE);
                return Long.MAX_VALUE;
            }));
        }
        assertEquals(0, left.get());
    }

    @Test
    void absentObserverCannotPretendPositivePaymentSucceeded() {
        var left = new AtomicLong(10);
        try (var context = EnergyDebitContext.open(new Object(), 20, 10, true, left::set)) {
            assertThrows(IllegalStateException.class, () -> context.execute(() -> 5));
        }
        assertEquals(10, left.get());
    }

    @Test
    void failedPaymentAndReentrantIoDoNotOverwriteOwner() {
        var container = new Object();
        var left = new AtomicLong(10);
        try (var context = EnergyDebitContext.open(container, 20, 10, true, left::set)) {
            assertThrows(IllegalStateException.class, () -> EnergyDebitContext.open(new Object(), 1, 1, true, ignored -> {}));
            assertEquals(0, context.execute(() -> 0));
            assertEquals(10, left.get());
        }
        try (var next = EnergyDebitContext.open(container, 20, 10, true, left::set)) {
            next.execute(() -> { EnergyDebitContext.changed(container, 10); return 10; });
        }
        assertEquals(0, left.get());
    }
}
