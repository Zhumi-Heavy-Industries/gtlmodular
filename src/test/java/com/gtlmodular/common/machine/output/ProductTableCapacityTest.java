package com.gtlmodular.common.machine.output;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProductTableCapacityTest {
    @Test
    void rejectsNewKeysWhenEntryCapacityIsFull() {
        var table = new ProductTable<String>(new ProductTable.Capacity(2, 100));
        assertEquals(50, table.add("a", 50));
        assertEquals(50, table.add("b", 50));
        assertEquals(0, table.add("c", 10), "满了两格，新 key 必须被拒收");
        assertTrue(table.isFull());
        assertEquals(2, table.size());
    }

    @Test
    void capsPerKeyAmountAndReportsAcceptedPart() {
        var table = new ProductTable<String>(new ProductTable.Capacity(2, 100));
        assertEquals(100, table.add("a", 120), "只能接受容量内的部分");
        assertEquals(100, table.amount("a"));
        assertEquals(0, table.add("a", 5), "已到单 key 上限，后续拒收");
    }

    @Test
    void existingKeyStillAllowedWhenOtherKeysFull() {
        var table = new ProductTable<String>(new ProductTable.Capacity(2, 10));
        table.add("a", 5);
        table.add("b", 5);
        assertEquals(5, table.add("a", 5), "已有 key 不占新格子，仍可继续灌");
    }

    @Test
    void unlimitedTableNeverRejects() {
        var table = new ProductTable<String>();
        assertEquals(1_000_000, table.add("a", 1_000_000));
        assertFalse(table.isFull());
        assertNull(table.capacity());
    }
}
