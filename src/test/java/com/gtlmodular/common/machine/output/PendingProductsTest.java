package com.gtlmodular.common.machine.output;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PendingProductsTest {
    private static PendingProducts.Capacity cap(int entries, long perKey, long total) {
        return new PendingProducts.Capacity(entries, Int128.of(perKey), Int128.of(total));
    }

    @Test
    void boundedByPerKeyAndTotalAndEntries() {
        var pending = new PendingProducts<String>(cap(2, 100, 150));
        assertEquals(100, pending.acceptLong("a", 120), "单 key 上限 100");
        assertEquals(50, pending.acceptLong("b", 100), "总量只剩 50");
        assertEquals(0, pending.acceptLong("c", 10), "条目已满，新 key 拒收");
        assertEquals(150, pending.total().toLongClamped());
    }

    @Test
    void simulateDoesNotMutate() {
        var pending = new PendingProducts<String>(cap(2, 100, 150));
        pending.acceptLong("a", 10);
        Int128 simulated = pending.simulateAccept("a", Int128.of(1000));
        assertEquals(90, simulated.toLongClamped());
        assertEquals(10, pending.amount("a").toLongClamped(), "模拟不能改表");
    }

    @Test
    void acceptsBeyondLong() {
        Int128 two64 = Int128.ofUnsigned(1L, 0L);
        var pending = new PendingProducts<String>(new PendingProducts.Capacity(1, two64, two64));
        Int128 accepted = pending.accept("a", two64);
        assertEquals(two64, accepted);
        assertTrue(pending.amount("a").exceedsLong());

        assertEquals(1000, pending.drain("a", 1000));
        assertEquals(two64.subtract(Int128.of(1000)), pending.amount("a"));
    }

    @Test
    void drainRemovesEmptyKeysAndRejectsBadWant() {
        var pending = new PendingProducts<String>(cap(1, 10, 10));
        pending.acceptLong("a", 5);
        assertEquals(0, pending.drain("a", 0));
        assertEquals(5, pending.drain("a", 100));
        assertTrue(pending.isEmpty());
        assertEquals(0, pending.drain("a", 1));
    }
}
