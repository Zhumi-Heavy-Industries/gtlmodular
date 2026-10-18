package com.gtlmodular.common.machine.output;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MEOutputBufferDrainTest {
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

    private static MEOutputBuffer<String, V> buffer(long perKey) {
        return new MEOutputBuffer<>(
                new PendingProducts<>(new PendingProducts.Capacity(4, Int128.of(perKey), Int128.of(perKey * 4))),
                CODEC, value -> true);
    }

    @Test
    void drainTurnsPendingBackIntoRemaindersAndClears() {
        var buf = buffer(100);
        var accepted = buf.accept(List.of(new V("iron", 150)), false);
        assertEquals(50, accepted.remainder().get(0).amount());
        assertEquals(100, buf.pendingAmount().toLongClamped());

        var drained = buf.drainRemainders();
        assertEquals(1, drained.size());
        assertEquals(100, drained.get(0).amount());
        assertFalse(buf.hasPending(), "交还余量后不能留下 pending");
    }

    @Test
    void drainAfterPartialFlushKeepsOnlyUnflushedPart() {
        var buf = buffer(100);
        buf.accept(List.of(new V("iron", 100)), false);
        var sink = new FairPendingFlusher.Sink<String>() {
            @Override
            public long insert(String key, long amount) {
                return amount / 2;
            }

            @Override
            public long simulate(String key, long amount) {
                return amount / 2;
            }
        };
        buf.flush(sink, new FairPendingFlusher.Budget(8, 1_000), false);
        var drained = buf.drainRemainders();
        assertEquals(1, drained.size());
        assertEquals(50, drained.get(0).amount());
        assertFalse(buf.hasPending());
    }
}
