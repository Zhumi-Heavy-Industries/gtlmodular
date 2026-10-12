package com.gtlmodular.common.machine;

import com.lowdragmc.lowdraglib.misc.ItemStackTransfer;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** 通过适配器复用机器的归档入口，避免复制防覆盖和容量预检逻辑。 */
public final class LazyPatternArchiveTarget implements PatternArchiveTarget {

    private final LazyPatternAssemblyPartMachine machine;

    public LazyPatternArchiveTarget(LazyPatternAssemblyPartMachine machine) {
        this.machine = machine;
    }

    public LazyPatternAssemblyPartMachine machine() {
        return this.machine;
    }

    @Override
    public int capacity() {
        return machine.getCapacity();
    }

    @Override
    public int maxCapacity() {
        return machine.getMaxPatternCount();
    }

    @Override
    public int reserve() {
        return LazyPatternAssemblyPartMachine.RESERVE;
    }

    @Override
    public boolean canFit(int needed) {
        return needed <= machine.getMaxPatternCount();
    }

    @Override
    public int highestUsedSlot() {
        return machine.highestUsedSlot();
    }

    /** 读异常须外抛，让引擎按已占用处理；返回空栈会导致覆盖已有样板。 */
    @Override
    public ItemStack patternAt(int slot) {
        ItemStackTransfer inventory = machine.getPatternInventory();
        return inventory == null ? ItemStack.EMPTY : inventory.getStackInSlot(slot);
    }

    @Override
    public boolean isRemote() {
        return machine.isRemote();
    }

    @Override
    public BlockPos pos() {
        return machine.getPos();
    }

    @Override
    public boolean includeSettingsInArchive() {
        return machine.isArchiveIncludeSettings();
    }

    @Override
    public boolean includeNameInArchive() {
        return machine.isArchiveIncludeName();
    }

    @Override
    public LazyPatternArchiveCodec.PlaceMode placeMode() {
        return machine.archivePlaceMode();
    }

    @Override
    public LazyPatternArchiveCodec.Payload buildArchive(boolean includeSettings, boolean includeName) {
        return machine.buildArchivePayload(includeSettings, includeName);
    }

    @Override
    public void writePattern(int slot, ItemStack stack) {
        // ItemStackTransfer.setStackInSlot 不触发内容回调，批量收尾由 afterBulkChange 合并执行。
        machine.getPatternInventory().setStackInSlot(slot, stack);
    }

    @Override
    public void writeRecipeType(int slot, int recipeType) {
        machine.writeArchivedRecipeType(slot, recipeType);
    }

    @Override
    public void writeManualType(int slot, ResourceLocation typeId) {
        machine.writeArchivedManualType(slot, typeId);
    }

    @Override
    public boolean writeCatalyst(int slot, CompoundTag catalyst) {
        return machine.writeArchivedCatalyst(slot, catalyst);
    }

    @Override
    public void writeSettings(CompoundTag settings) {
        machine.writeArchivedSettings(settings);
    }

    @Override
    public void writeName(String name) {
        machine.writeArchivedName(name);
    }

    @Override
    public void growForBatchInsert(int count) {
        machine.growCapacityForBatchInsert(count);
    }

    @Override
    public void growToCapacity(int needed) {
        machine.growCapacityFor(needed);
    }

    @Override
    public void clearAll() {
        machine.clearArchiveContent();
    }

    @Override
    public void afterBulkChange() {
        machine.afterArchiveBulkChange();
    }
}
