package com.gtlmodular.common.machine.output;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * {@link ProductTable} 的存档编解码。
 *
 * <p>带版本号是硬要求：这张表一旦写进存档就变成“数据”，版本对不上必须整体丢弃，
 * 不能让旧格式静默反序列化（参考本仓库 {@code PatternAssemblyRecipeCacheInvalidator} 的教训）。</p>
 *
 * <p>单条记录坏了（负数量）整张表作废；单个 key 认不出只跳过该条，不让一张表拖垮整台机器。</p>
 */
public final class ProductTablePersistence {
    public static final int VERSION = 1;

    static final String TAG_VERSION = "v";
    static final String TAG_ENTRIES = "e";
    static final String TAG_KEY = "k";
    static final String TAG_AMOUNT = "n";
    static final String TAG_OVERFLOW = "o";

    /** key 的存档编解码；读不出来时返回 null（调用方会跳过该条）。 */
    public interface KeyCodec<K> {
        CompoundTag write(K key);

        K read(CompoundTag tag);
    }

    private ProductTablePersistence() {}

    public static <K> CompoundTag save(ProductTable<K> table, KeyCodec<K> codec) {
        CompoundTag root = new CompoundTag();
        root.putInt(TAG_VERSION, VERSION);
        ListTag entries = new ListTag();
        for (ProductTable.Entry<K> entry : table.snapshot()) {
            CompoundTag item = new CompoundTag();
            item.put(TAG_KEY, codec.write(entry.key()));
            item.putLong(TAG_AMOUNT, entry.amount());
            long overflow = table.overflow(entry.key());
            if (overflow > 0) {
                item.putLong(TAG_OVERFLOW, overflow);
            }
            entries.add(item);
        }
        root.put(TAG_ENTRIES, entries);
        return root;
    }

    /**
     * 恢复；返回 false 表示版本不匹配或数据损坏，此时表已被清空。
     */
    public static <K> boolean load(ProductTable<K> table, CompoundTag root, KeyCodec<K> codec) {
        table.clear();
        int version = root.contains(TAG_VERSION) ? root.getInt(TAG_VERSION) : -1;
        if (version != VERSION) {
            return false;
        }
        ListTag entries = root.getList(TAG_ENTRIES, Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag item = entries.getCompound(i);
            K key = codec.read(item.getCompound(TAG_KEY));
            if (key == null) {
                continue;
            }
            long amount = item.getLong(TAG_AMOUNT);
            long overflow = item.getLong(TAG_OVERFLOW);
            if (amount < 0 || overflow < 0) {
                table.clear();
                return false;
            }
            table.restore(key, amount, overflow);
        }
        return true;
    }
}
