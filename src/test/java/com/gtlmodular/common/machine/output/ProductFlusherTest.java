package com.gtlmodular.common.machine.output;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ProductFlusherTest {
    private static ProductFlusher.Budget budget(int entries, long amount) {
        return new ProductFlusher.Budget(entries, amount);
    }

    @Test
    void drainsEverythingWhenSinkAcceptsAll() {
        var table = new ProductTable<String>();
        table.add("iron", 10);
        table.add("copper", 5);
        Map<String, Long> stored = new HashMap<>();
        var result = ProductFlusher.flush(table, (k, n) -> {
            stored.merge(k, n, Long::sum);
            return n;
        }, budget(64, 1_000));
        assertEquals(15, result.flushedAmount());
        assertEquals(2, result.flushedEntries());
        assertFalse(result.hasLeftover());
        assertTrue(table.isEmpty());
    }

    @Test
    void partialAcceptKeepsLeftoverAndStopsOnRefusal() {
        var table = new ProductTable<String>();
        table.add("iron", 10);
        // 下游只收一半：剩余必须留在表里，且本轮不再尝试后续 key
        var result = ProductFlusher.flush(table, (k, n) -> n / 2, budget(64, 1_000));
        assertEquals(5, result.flushedAmount());
        assertEquals(5, table.amount("iron"));
        assertTrue(result.hasLeftover());
    }

    @Test
    void amountBudgetCapsEachEntry() {
        var table = new ProductTable<String>();
        table.add("iron", 100);
        var accepted = new long[]{0};
        var result = ProductFlusher.flush(table, (k, n) -> {
            accepted[0] += n;
            return n;
        }, budget(64, 30));
        assertEquals(30, result.flushedAmount());
        assertEquals(30, accepted[0]);
        assertEquals(70, table.amount("iron"));
    }

    @Test
    void illegalSinkResultIsRejected() {
        var table = new ProductTable<String>();
        table.add("iron", 10);
        assertThrows(IllegalStateException.class,
                () -> ProductFlusher.flush(table, (k, n) -> n + 1, budget(8, 100)));
        assertThrows(IllegalStateException.class,
                () -> ProductFlusher.flush(table, (k, n) -> -1, budget(8, 100)));
    }

    @Test
    void emptyTableIsNoOp() {
        var table = new ProductTable<String>();
        var result = ProductFlusher.flush(table, (k, n) -> {
            throw new AssertionError("空表不应触发下游写入");
        }, budget(8, 100));
        assertEquals(0, result.flushedAmount());
        assertFalse(result.hasLeftover());
    }
}
