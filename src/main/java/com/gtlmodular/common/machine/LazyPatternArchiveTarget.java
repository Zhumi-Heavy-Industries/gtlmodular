package com.gtlmodular.common.machine;

import com.lowdragmc.lowdraglib.misc.ItemStackTransfer;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * <b>片 C：{@link PatternArchiveTarget} 的「惰性猪咪样板总成」适配器。</b>
 *
 * <p><b>为什么用适配器而不是让惰性机直接 implement</b>：惰性机那条线刚被队长验收通过，
 * 本片的原则是"<b>能不动它就不动它</b>" —— 适配器把接口方法<b>一对一转发</b>到惰性机<b>已经有</b>的
 * 方法上（{@code getCapacity} / {@code getMaxPatternCount} / {@code highestUsedSlot} /
 * {@code buildArchivePayload} / {@code clearArchiveContent} / {@code writeArchived*} /
 * {@code afterArchiveBulkChange} / {@code growCapacityFor*}），
 * ⇒ 惰性机的<b>行为零变化</b>、那个文件<b>一个字都不改</b>。
 *
 * <p>本类在同一个包（{@code com.gtlmodular.common.machine}）里，所以能调到惰性机那些
 * 包内可见（{@code getPatternInventory()} / {@code clearArchiveContent()} / {@code writeArchived*}）的成员。
 *
 * <p>逐方法对照见 {@link PatternArchiveTarget} 的注释；这里只写"转发到谁"。
 */
public final class LazyPatternArchiveTarget implements PatternArchiveTarget {

    private final LazyPatternAssemblyPartMachine machine;

    public LazyPatternArchiveTarget(LazyPatternAssemblyPartMachine machine) {
        this.machine = machine;
    }

    /** 被包住的机器（诊断 / 兼容旧调用方用）。 */
    public LazyPatternAssemblyPartMachine machine() {
        return this.machine;
    }

    // ==================================================================================
    // 一、只读判据
    // ==================================================================================

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
        // 与惰性机既有的"硬上限"判据逐字相同（`needed > maxPatterns` 就整体拒绝，绝不越限扩容）
        return needed <= machine.getMaxPatternCount();
    }

    @Override
    public int highestUsedSlot() {
        return machine.highestUsedSlot();
    }

    /**
     * 只读一个槽的原始内容。
     * <p>★ 读异常<b>照原样外抛</b>（不在这里吞）：引擎会把它当成"这一槽有东西"⇒ 少写一条，
     * 绝不覆盖玩家已有的样板。
     */
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

    // ==================================================================================
    // 二、档案读取
    // ==================================================================================

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

    // ==================================================================================
    // 三、档案写入
    // ==================================================================================

    @Override
    public void writePattern(int slot, ItemStack stack) {
        // 惰性机既有的落盘口径：patternInventory.setStackInSlot（LDLib 自身**不**触发
        // onContentsChanged ⇒ 逐槽写不会连锁触发任何收尾；收尾由 afterBulkChange() 调一次）。
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

    // ==================================================================================
    // 四、容量准备 / 清源 / 收尾
    // ==================================================================================

    @Override
    public void growForBatchInsert(int count) {
        // 追加模式：惰性机的批量入口自带"highestUsedSlot()+1+count+RESERVE"的算式（一次扩到位）
        machine.growCapacityForBatchInsert(count);
    }

    @Override
    public void growToCapacity(int needed) {
        // 对齐模式：同一个单次扩容实现（惰性机只增不减、绝不越硬上限）
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
