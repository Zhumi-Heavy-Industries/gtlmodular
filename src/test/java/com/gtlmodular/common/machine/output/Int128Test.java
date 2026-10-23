package com.gtlmodular.common.machine.output;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class Int128Test {
    @Test
    void addsPastLongWithoutLosingMagnitude() {
        Int128 big = Int128.of(Long.MAX_VALUE).add(Int128.of(2));
        assertTrue(big.exceedsLong());
        assertEquals(Long.MAX_VALUE, big.toLongClamped());
        assertEquals(big, Int128.ofUnsigned(0L, Long.MIN_VALUE | 1L)); // 2^63 + 1 的无符号表示
    }

    @Test
    void subtractsAcrossTheLongBoundary() {
        Int128 big = Int128.ofUnsigned(1L, 0L); // 2^64
        Int128 rest = big.subtract(Int128.of(1000));
        assertEquals(Int128.ofUnsigned(0L, -1000L), rest); // 2^64 - 1000
    }

    @Test
    void overflowThrowsInsteadOfSaturating() {
        Int128 max = Int128.ofUnsigned(-1L, -1L); // 2^128 - 1
        assertThrows(ArithmeticException.class, () -> max.add(Int128.of(1)));
    }

    @Test
    void underflowThrows() {
        assertThrows(ArithmeticException.class, () -> Int128.of(1).subtract(Int128.of(2)));
    }

    @Test
    void unsignedCompareHandlesHighBit() {
        assertTrue(Int128.ofUnsigned(1L, 0L).compareTo(Int128.ofUnsigned(0L, -1L)) > 0);
        assertTrue(Int128.ofUnsigned(0L, -1L).compareTo(Int128.of(1)) > 0); // 2^64-1 > 1
    }
}
