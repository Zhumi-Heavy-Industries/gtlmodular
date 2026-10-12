package com.gtlmodular.common.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** 两种样板总成共用容量预检和防覆盖逻辑，机器差异由适配器处理。 */
public interface PatternArchiveTarget {

    int capacity();

    int maxCapacity();

    int reserve();

    /** 容量预检必须只读，才能在装不下时保持目标仓原样。 */
    boolean canFit(int needed);

    int highestUsedSlot();

    /** 读异常须外抛，让引擎按已占用处理；返回空栈会导致覆盖已有样板。查询不能建槽或扩容。 */
    ItemStack patternAt(int slot);

    boolean isRemote();

    BlockPos pos();

    boolean includeSettingsInArchive();

    boolean includeNameInArchive();

    LazyPatternArchiveCodec.PlaceMode placeMode();

    /** 复制和剪切共用只读快照，避免在档案写入成功前改变源仓。 */
    LazyPatternArchiveCodec.Payload buildArchive(boolean includeSettings, boolean includeName);

    void writePattern(int slot, ItemStack stack);

    void writeRecipeType(int slot, int recipeType);

    void writeManualType(int slot, ResourceLocation typeId);

    boolean writeCatalyst(int slot, CompoundTag catalyst);

    void writeSettings(CompoundTag settings);

    void writeName(String name);

    /** 每批只准备一次容量，避免逐槽增长；适配器也可在此开启批量写入窗口。 */
    void growForBatchInsert(int count);

    /** 对齐模式按最高目标槽准备容量，不能按档案条目数推断所需空间。 */
    void growToCapacity(int needed);

    /** 只有档案写入成功才可清源；只清已归档内容，保留容量、共享催化剂、共享电路和镜像绑定。 */
    void clearAll();

    /** 批量写入和清源统一收尾，避免逐槽刷新导致 AE 重复读取整仓。 */
    void afterBulkChange();
}
