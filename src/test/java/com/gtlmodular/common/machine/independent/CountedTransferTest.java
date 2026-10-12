package com.gtlmodular.common.machine.independent;

import com.gtlmodular.common.machine.independent.io.CountedTransfer;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class CountedTransferTest {
    @Test
    void partialMovesAndRepeatedCompletionConserveLongQuantities() {
        var left = new AtomicLong(Long.MAX_VALUE);
        var delivered = new AtomicLong();
        for (long capacity : new long[]{3, Long.MAX_VALUE - 5, 2, 100}) {
            CountedTransfer.execute(left.get(), wanted -> {
                long actual = Math.min(wanted, capacity);
                delivered.addAndGet(actual);
                return actual;
            }, left::set);
        }
        assertEquals(0, left.get());
        assertEquals(Long.MAX_VALUE, delivered.get());
    }

    @Test
    void failedNotificationLeavesActualQuantityRecordedForRetry() {
        var left = new AtomicLong(10);
        var delivered = new AtomicLong();
        CountedTransfer.execute(left.get(), wanted -> { delivered.addAndGet(4); return 4; }, left::set);
        assertThrows(CountedTransfer.RecordedNotificationFailure.class,
                () -> CountedTransfer.notifyRecordedChange(() -> { throw new IllegalStateException("listener"); }));
        assertEquals(6, left.get());
        CountedTransfer.execute(left.get(), wanted -> { delivered.addAndGet(wanted); return wanted; }, left::set);
        assertEquals(10, delivered.get());
        assertEquals(0, left.get());
    }

    @Test
    void unknownFailureDoesNotFabricateAReceipt() {
        var left = new AtomicLong(10);
        assertThrows(IllegalStateException.class,
                () -> CountedTransfer.execute(10, wanted -> { throw new IllegalStateException("unknown"); }, left::set));
        assertEquals(10, left.get());
    }

    @Test
    void invalidHandlerAmountsCannotBeCredited() {
        for (long result : new long[]{-1, 11, Long.MAX_VALUE}) {
            var left = new AtomicLong(10);
            assertThrows(IllegalStateException.class, () -> CountedTransfer.execute(10, wanted -> result, left::set));
            assertEquals(10, left.get());
        }
    }

    @Test
    void emptyWorkDoesNotInvokeResourceOrNotification() {
        assertEquals(0, CountedTransfer.execute(0, wanted -> { fail(); return 0; }, left -> fail()));
        assertThrows(IllegalArgumentException.class, () -> CountedTransfer.execute(-1, wanted -> wanted, left -> fail()));
    }
}
