package com.gtlmodular.common.machine.output;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProductTableTest {
    @Test
    void accumulatesSameKeyAndIgnoresNonPositive() {
        var table = new ProductTable<String>();
        table.add("iron", 3);
        table.add("iron", 5);
        table.add("iron", 0);
        table.add("iron", -2);
        assertEquals(8, table.amount("iron"));
        assertEquals(1, table.size());
        assertFalse(table.isEmpty());
    }

    @Test
    void saturatesInsteadOfWrapping() {
        var table = new ProductTable<String>();
        table.add("iron", Long.MAX_VALUE);
        table.add("iron", 10);
        assertEquals(Long.MAX_VALUE, table.amount("iron"));
        assertEquals(10, table.overflow("iron"));
        assertTrue(table.isSaturated("iron"));
        // 再多也只是继续进溢出表，主表不回绕成负数
        table.add("iron", 5);
        assertEquals(Long.MAX_VALUE, table.amount("iron"));
        assertEquals(15, table.overflow("iron"));
    }

    @Test
    void subtractRejectsOverdrawAndRemovesEmptyKeys() {
        var table = new ProductTable<String>();
        table.add("copper", 7);
        table.subtract("copper", 3);
        assertEquals(4, table.amount("copper"));
        assertThrows(IllegalStateException.class, () -> table.subtract("copper", 5));
        table.subtract("copper", 4);
        assertEquals(0, table.amount("copper"));
        assertTrue(table.isEmpty());
    }

    @Test
    void peekIsBoundedAndDoesNotConsume() {
        var table = new ProductTable<String>();
        for (int i = 0; i < 10; i++) {
            table.add("k" + i, i + 1);
        }
        var batch = table.peek(3);
        assertEquals(3, batch.size());
        assertEquals(10, table.size(), "peek 不能消费表内容");
        assertEquals(10, table.peek(100).size());
        assertTrue(table.peek(0).isEmpty());
    }

    @Test
    void nullKeyIsRejected() {
        var table = new ProductTable<String>();
        assertThrows(IllegalArgumentException.class, () -> table.add(null, 1));
    }
}
