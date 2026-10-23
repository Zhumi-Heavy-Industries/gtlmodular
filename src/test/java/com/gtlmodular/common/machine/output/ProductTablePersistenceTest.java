package com.gtlmodular.common.machine.output;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProductTablePersistenceTest {
    private static final ProductTablePersistence.KeyCodec<String> CODEC = new ProductTablePersistence.KeyCodec<>() {
        @Override
        public CompoundTag write(String key) {
            CompoundTag tag = new CompoundTag();
            tag.putString("id", key);
            return tag;
        }

        @Override
        public String read(CompoundTag tag) {
            return tag.contains("id") ? tag.getString("id") : null;
        }
    };

    @Test
    void roundTripsAmountsAndOverflow() {
        var table = new ProductTable<String>();
        table.add("iron", 10);
        table.add("copper", Long.MAX_VALUE);
        table.add("copper", 3);

        CompoundTag saved = ProductTablePersistence.save(table, CODEC);
        var restored = new ProductTable<String>();
        assertTrue(ProductTablePersistence.load(restored, saved, CODEC));

        assertEquals(10, restored.amount("iron"));
        assertEquals(Long.MAX_VALUE, restored.amount("copper"));
        assertEquals(3, restored.overflow("copper"));
    }

    @Test
    void versionMismatchDiscardsWholeTable() {
        var table = new ProductTable<String>();
        table.add("iron", 10);
        CompoundTag saved = ProductTablePersistence.save(table, CODEC);
        saved.putInt(ProductTablePersistence.TAG_VERSION, ProductTablePersistence.VERSION + 1);

        var target = new ProductTable<String>();
        target.add("stale", 5);
        assertFalse(ProductTablePersistence.load(target, saved, CODEC));
        assertTrue(target.isEmpty(), "版本不匹配必须整体丢弃，不能留下旧表");
    }

    @Test
    void negativeAmountDiscardsWholeTable() {
        var table = new ProductTable<String>();
        table.add("iron", 10);
        CompoundTag saved = ProductTablePersistence.save(table, CODEC);
        saved.getList(ProductTablePersistence.TAG_ENTRIES, Tag.TAG_COMPOUND)
                .getCompound(0)
                .putLong(ProductTablePersistence.TAG_AMOUNT, -1);

        var target = new ProductTable<String>();
        assertFalse(ProductTablePersistence.load(target, saved, CODEC));
        assertTrue(target.isEmpty());
    }

    @Test
    void unknownKeyIsSkippedNotFatal() {
        var table = new ProductTable<String>();
        table.add("ghost", 7);
        CompoundTag saved = ProductTablePersistence.save(table, CODEC);
        saved.getList(ProductTablePersistence.TAG_ENTRIES, Tag.TAG_COMPOUND)
                .getCompound(0)
                .getCompound(ProductTablePersistence.TAG_KEY)
                .remove("id");

        var target = new ProductTable<String>();
        assertTrue(ProductTablePersistence.load(target, saved, CODEC));
        assertTrue(target.isEmpty(), "认不出的 key 跳过即可");
    }
}
