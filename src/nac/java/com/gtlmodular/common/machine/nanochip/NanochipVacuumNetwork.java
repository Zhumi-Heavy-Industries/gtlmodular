package com.gtlmodular.common.machine.nanochip;

import com.gtlmodular.common.machine.nanochip.vacuum.NanochipVacuumInventory;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/** 真空库存由主机集中持有，按颜色和管网分量隔离，避免各端口维护重复库存。 */
public final class NanochipVacuumNetwork {

    /** 限制单条数量，避免累加库存时溢出。 */
    public static final long MAX_PER_ENTRY = 1_000_000_000L;

    public static final int MAX_KINDS_PER_CHANNEL = 16;

    public static final class Part {
        private final ItemStack prototype;
        private final boolean materializable;
        private long amount;

        Part(ItemStack prototype, boolean materializable, long amount) {
            this.prototype = prototype;
            this.materializable = materializable;
            this.amount = amount;
        }

        public ItemStack prototype() {
            return prototype;
        }

        public boolean materializable() {
            return materializable;
        }

        public long amount() {
            return amount;
        }
    }

    // 库存仍操作原 Part 对象；工厂与写口避免为迁移实现而公开其构造和数量修改权限。
    private final NanochipVacuumInventory inventory = new NanochipVacuumInventory(
            Part::new, (part, amount) -> part.amount = amount);

    public NanochipVacuumNetwork() {
    }

    public int clearChannel(NanochipColor color) {
        return inventory.clearChannel(color);
    }

    public long insert(NanochipColor color, int component, ItemStack stack, boolean materializable) {
        return inventory.insert(color, component, stack, materializable);
    }

    public ItemStack extract(NanochipColor color, int component, int maxAmount, Boolean materializableFilter) {
        return inventory.extract(color, component, maxAmount, materializableFilter);
    }

    public int componentCount(NanochipColor color) {
        return inventory.componentCount(color);
    }

    public int flatten(NanochipColor color) {
        return inventory.flatten(color);
    }

    public int flattenAll() {
        return inventory.flattenAll();
    }

    public java.util.List<ItemStack> prototypesOf(NanochipColor color) {
        return inventory.prototypesOf(color);
    }

    public long transfer(NanochipColor from, NanochipColor to, String itemFilter, int maxAmount) {
        return inventory.transfer(from, to, itemFilter, maxAmount);
    }

    public long totalOf(NanochipColor color) {
        return inventory.totalOf(color);
    }

    public long totalOf(NanochipColor color, Boolean materializableFilter) {
        return inventory.totalOf(color, materializableFilter);
    }

    public long total() {
        return inventory.total();
    }

    public String detail(NanochipColor color) {
        return inventory.detail(color);
    }

    public String summary(NanochipColor color) {
        return inventory.summary(color);
    }

    public String summaryAll() {
        return inventory.summaryAll();
    }

    public boolean isEmpty() {
        return inventory.isEmpty();
    }

    public CompoundTag save() {
        return inventory.save();
    }

    public void load(CompoundTag tag) {
        inventory.load(tag);
    }
}
