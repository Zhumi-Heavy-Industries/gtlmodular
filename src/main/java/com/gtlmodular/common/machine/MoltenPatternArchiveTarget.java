package com.gtlmodular.common.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** 通过机器的既有归档入口复用同一引擎，保留样板总成各自的存储和收尾方式。 */
public final class MoltenPatternArchiveTarget implements PatternArchiveTarget {

    private final MoltenPatternAssemblyPartMachine machine;

    public MoltenPatternArchiveTarget(MoltenPatternAssemblyPartMachine machine) {
        this.machine = machine;
    }

    public MoltenPatternAssemblyPartMachine machine() {
        return this.machine;
    }

    @Override
    public int capacity() {
        return machine.archiveCapacity();
    }

    @Override
    public int maxCapacity() {
        return machine.archiveMaxCapacity();
    }

    @Override
    public int reserve() {
        return machine.archiveReserve();
    }

    @Override
    public boolean canFit(int needed) {
        return machine.archiveCanFit(needed);
    }

    @Override
    public int highestUsedSlot() {
        return machine.archiveHighestUsedSlot();
    }

    /** 读异常须外抛，让引擎按已占用处理，避免覆盖已有样板。 */
    @Override
    public ItemStack patternAt(int slot) {
        return machine.archivePatternAt(slot);
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
        machine.archiveWritePattern(slot, stack);
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

    /** 容量预检已经完成；此处开启批量窗口，避免逐槽写入重复通知 AE。 */
    @Override
    public void growForBatchInsert(int count) {
        machine.beginArchiveBulkWrite();
    }

    /** 对齐粘贴也需要批量窗口，逐槽写入完成后统一通知 AE。 */
    @Override
    public void growToCapacity(int needed) {
        machine.beginArchiveBulkWrite();
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
