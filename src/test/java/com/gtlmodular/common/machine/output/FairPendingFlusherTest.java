package com.gtlmodular.common.machine.output;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FairPendingFlusherTest {
    private static PendingProducts.Capacity cap() {
        return new PendingProducts.Capacity(8, Int128.of(1_000_000), Int128.of(1_000_000));
    }

    private static FairPendingFlusher.Budget budget(int keys, long perKey) {
        return new FairPendingFlusher.Budget(keys, perKey);
    }

    @Test
    void rotatesAcrossKeysSoFrontKeyCannotStarveOthers() {
        var pending = new PendingProducts<String>(cap());
        pending.acceptLong("k0", 100);
        pending.acceptLong("k1", 100);
        pending.acceptLong("k2", 100);
        var flusher = new FairPendingFlusher<String>();
        List<String> order = new ArrayList<>();

        for (int round = 0; round < 3; round++) {
            var result = flusher.flush(pending, new FairPendingFlusher.Sink<>() {
                @Override
                public long insert(String key, long amount) {
                    order.add(key);
                    return amount;
                }

                @Override
                public long simulate(String key, long amount) {
                    return amount;
                }
            }, budget(1, 1_000), false);
            assertEquals(100, result.moved().toLongClamped());
        }

        assertEquals(List.of("k0", "k1", "k2"), order);
        assertTrue(pending.isEmpty());
    }

    @Test
    void refusalKeepsRemainderAndCursorMovesOn() {
        var pending = new PendingProducts<String>(cap());
        pending.acceptLong("k0", 100);
        pending.acceptLong("k1", 100);
        var flusher = new FairPendingFlusher<String>();
        List<String> order = new ArrayList<>();
        var halfSink = new FairPendingFlusher.Sink<String>() {
            @Override
            public long insert(String key, long amount) {
                order.add(key);
                return amount / 2;
            }

            @Override
            public long simulate(String key, long amount) {
                return amount / 2;
            }
        };

        flusher.flush(pending, halfSink, budget(1, 1_000), false);
        flusher.flush(pending, halfSink, budget(1, 1_000), false);

        assertEquals(List.of("k0", "k1"), order, "拒收后下一轮必须换 key");
        assertEquals(50, pending.amount("k0").toLongClamped());
        assertEquals(50, pending.amount("k1").toLongClamped());
    }

    @Test
    void simulateReportsButDoesNotDrain() {
        var pending = new PendingProducts<String>(cap());
        pending.acceptLong("k0", 100);
        var flusher = new FairPendingFlusher<String>();
        var result = flusher.flush(pending, new FairPendingFlusher.Sink<>() {
            @Override
            public long insert(String key, long amount) {
                throw new AssertionError("simulate 模式不能走真实写入");
            }

            @Override
            public long simulate(String key, long amount) {
                return amount;
            }
        }, budget(8, 1_000), true);

        assertEquals(100, result.moved().toLongClamped());
        assertEquals(100, pending.amount("k0").toLongClamped(), "模拟不能扣 pending");
    }
}
