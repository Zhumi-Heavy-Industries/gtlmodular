package com.gtlmodular.common.machine.output;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FairProductFlusherTest {
    private static ProductFlusher.Budget budget(int entries, long amount) {
        return new ProductFlusher.Budget(entries, amount);
    }

    @Test
    void eachFlushTakesTheNextKeySoBigKeysCannotStarveOthers() {
        var table = new ProductTable<String>();
        table.add("k0", 100);
        table.add("k1", 100);
        table.add("k2", 100);
        var flusher = new FairProductFlusher();
        List<String> order = new ArrayList<>();

        for (int round = 0; round < 3; round++) {
            var result = flusher.flush(table, (k, n) -> {
                order.add(k);
                return n;
            }, budget(1, 1_000));
            assertEquals(100, result.flushedAmount());
        }

        assertEquals(List.of("k0", "k1", "k2"), order, "每轮必须换一个 key");
        assertTrue(table.isEmpty());
    }

    @Test
    void refusalAdvancesCursorSoTheSameKeyIsNotRetriedFirst() {
        var table = new ProductTable<String>();
        table.add("k0", 100);
        table.add("k1", 100);
        var flusher = new FairProductFlusher();
        List<String> order = new ArrayList<>();
        ProductFlusher.Sink<String> half = (k, n) -> {
            order.add(k);
            return n / 2; // 永远只收一半：每轮都会"拒收"
        };

        flusher.flush(table, half, budget(1, 1_000));
        flusher.flush(table, half, budget(1, 1_000));

        assertEquals(List.of("k0", "k1"), order, "拒收后下一轮必须从下一个 key 开始");
        assertEquals(50, table.amount("k0"));
        assertEquals(50, table.amount("k1"));
    }
}
