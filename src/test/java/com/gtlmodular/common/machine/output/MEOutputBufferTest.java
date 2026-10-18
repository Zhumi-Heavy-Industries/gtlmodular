package com.gtlmodular.common.machine.output;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MEOutputBufferTest {
    private record V(String key, long amount) {}

    private static final MEOutputBuffer.Codec<String, V> CODEC = new MEOutputBuffer.Codec<>() {
        @Override
        public String keyOf(V value) {
            return value.key();
        }

        @Override
        public long amountOf(V value) {
            return value.amount();
        }

        @Override
        public V withAmount(V value, long amount) {
            return new V(value.key(), amount);
        }
    };

    private static MEOutputBuffer<String, V> buffer(int entries, long perKey, long total) {
        return new MEOutputBuffer<>(
                new PendingProducts<>(new PendingProducts.Capacity(entries, Int128.of(perKey), Int128.of(total))),
                CODEC, value -> !value.key().startsWith("void:"));
    }

    @Test
    void simulateReportsCapacityWithoutMutating() {
        var buf = buffer(4, 100, 1_000);
        var result = buf.accept(List.of(new V("iron", 150)), true);
        assertEquals(50, result.remainder().get(0).amount());
        assertEquals(0, buf.pendingEntries(), "simulate 不能写 pending");
    }

    @Test
    void partialAcceptReturnsRemainderAndKeepsPending() {
        var buf = buffer(4, 100, 1_000);
        var result = buf.accept(List.of(new V("iron", 150)), false);
        assertEquals(50, result.remainder().get(0).amount());
        assertEquals(100, buf.pendingAmount().toLongClamped());
        assertFalse(result.fullyAccepted());
    }

    @Test
    void filteredContentGoesToFilteredListNotPending() {
        var buf = buffer(4, 100, 1_000);
        var result = buf.accept(List.of(new V("void:sand", 10), new V("iron", 10)), false);
        assertEquals(1, result.filtered().size());
        assertEquals("void:sand", result.filtered().get(0).key());
        assertEquals(0, result.remainder().size());
        assertEquals(10, buf.pendingAmount().toLongClamped());
    }

    @Test
    void aggregatesPastLongWithoutSaturation() {
        var big = Int128.ofUnsigned(1L, 0L); // 2^64
        var buf = new MEOutputBuffer<>(
                new PendingProducts<>(new PendingProducts.Capacity(4, big, big)),
                CODEC, value -> true);
        assertTrue(buf.accept(List.of(new V("iron", Long.MAX_VALUE)), false).fullyAccepted());
        assertTrue(buf.accept(List.of(new V("iron", Long.MAX_VALUE)), false).fullyAccepted());
        assertTrue(buf.pendingAmount().exceedsLong(), "两次 long 上限必须合成 Int128，而不是截断");
    }

    @Test
    void flushDelegatesToFairRotation() {
        var buf = buffer(4, 1_000, 1_000);
        buf.accept(List.of(new V("a", 10), new V("b", 10)), false);
        var budget = new FairPendingFlusher.Budget(8, 1_000);
        var result = buf.flush(new FairPendingFlusher.Sink<>() {
            @Override
            public long insert(String key, long amount) {
                return amount;
            }

            @Override
            public long simulate(String key, long amount) {
                return amount;
            }
        }, budget, false);
        assertEquals(20, result.moved().toLongClamped());
        assertFalse(buf.hasPending());
    }
}
