package com.gtlmodular.common.machine;

import appeng.api.inventories.InternalInventory;
import com.gtlmodular.GTLModular;
import com.gtlmodular.common.diag.SwallowedErrors;
import com.lowdragmc.lowdraglib.misc.ItemStackTransfer;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachine;

/** 通过上游公开入口搬运样板；不搬运的催化剂、共享电路、镜像绑定和机器设置必须留在原机。 */
public final class VanillaPatternArchiveTarget implements PatternArchiveTarget {

    private final MEPatternBufferPartMachine machine;

    public VanillaPatternArchiveTarget(MEPatternBufferPartMachine machine) {
        this.machine = machine;
    }

    public MEPatternBufferPartMachine machine() {
        return this.machine;
    }

    private ItemStackTransfer inventory() {
        try {
            return machine.getPatternInventory();
        } catch (Throwable ignored) {
            SwallowedErrors.report("VanillaPatternArchiveTarget#inventory", ignored);
            return null;
        }
    }

    private InternalInventory terminalInventory() {
        try {
            return machine.getTerminalPatternInventory();
        } catch (Throwable ignored) {
            SwallowedErrors.report("VanillaPatternArchiveTarget#terminalInventory", ignored);
            return null;
        }
    }

    @Override
    public int capacity() {
        ItemStackTransfer inventory = inventory();
        return inventory == null ? 0 : inventory.getSlots();
    }

    @Override
    public int maxCapacity() {
        return capacity();
    }

    @Override
    public int reserve() {
        return 0;
    }

    /** 容量固定，必须在写入任何样板前整体拒绝装不下的档案。 */
    @Override
    public boolean canFit(int needed) {
        return needed <= capacity();
    }

    @Override
    public int highestUsedSlot() {
        ItemStackTransfer inventory = inventory();
        if (inventory == null) {
            return -1;
        }
        int slots;
        try {
            slots = inventory.getSlots();
        } catch (Throwable ignored) {
            SwallowedErrors.report("VanillaPatternArchiveTarget#highestUsedSlot.slotCount", ignored);
            return -1;
        }
        for (int i = slots - 1; i >= 0; i--) {
            try {
                ItemStack stack = inventory.getStackInSlot(i);
                if (stack != null && !stack.isEmpty()) {
                    return i;
                }
            } catch (Throwable ignored) {
                SwallowedErrors.report("VanillaPatternArchiveTarget#highestUsedSlot.readSlot", ignored);
            }
        }
        return -1;
    }

    /** 读异常须外抛，让引擎按已占用处理，避免覆盖已有样板。 */
    @Override
    public ItemStack patternAt(int slot) {
        ItemStackTransfer inventory = inventory();
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
        return false;
    }

    @Override
    public boolean includeNameInArchive() {
        return false;
    }

    @Override
    public LazyPatternArchiveCodec.PlaceMode placeMode() {
        return LazyPatternArchiveCodec.PlaceMode.APPEND;
    }

    /** 直接读仓，避免导出依赖可能尚未刷新的样板缓存。 */
    @Override
    public LazyPatternArchiveCodec.Payload buildArchive(boolean includeSettings, boolean includeName) {
        LazyPatternArchiveCodec.Payload payload = new LazyPatternArchiveCodec.Payload();
        int capacity = capacity();
        payload.capacity = capacity;
        payload.includeSettings = false;
        payload.includeName = false;
        payload.name = "";
        payload.dimension = dimensionId();
        try {
            BlockPos pos = machine.getPos();
            payload.pos = pos == null ? 0L : pos.asLong();
        } catch (Throwable ignored) {
            SwallowedErrors.report("VanillaPatternArchiveTarget#buildArchive.position", ignored);
        }

        ItemStackTransfer inventory = inventory();
        if (inventory == null) {
            payload.slotCount = 0;
            payload.patternCount = 0;
            return payload;
        }
        int highest = -1;
        for (int i = 0; i < capacity; i++) {
            ItemStack stack;
            try {
                stack = inventory.getStackInSlot(i);
            } catch (Throwable failure) {
                SwallowedErrors.report("VanillaPatternArchiveTarget#buildArchive.readSlot", failure);
                GTLModular.LOGGER.warn("[gtlmodular] 样板仓归档：读原版样板总成第 {} 槽失败（跳过该槽）pos={} 原因={}",
                        i + 1, pos(), failure.toString());
                continue;
            }
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            payload.entries.add(new LazyPatternArchiveCodec.Entry(i, stack.copy(), null, null, null));
            highest = i;
        }
        payload.slotCount = highest < 0 ? 0 : highest + 1;
        payload.patternCount = payload.entries.size();
        return payload;
    }

    private String dimensionId() {
        try {
            net.minecraft.world.level.Level level = machine.getLevel();
            return level == null ? "" : level.dimension().location().toString();
        } catch (Throwable ignored) {
            SwallowedErrors.report("VanillaPatternArchiveTarget#dimensionId", ignored);
            return "";
        }
    }

    /** 终端入口会触发逐槽收尾，直接写底层仓会漏掉样板缓存和 AE 通知。 */
    @Override
    public void writePattern(int slot, ItemStack stack) {
        InternalInventory terminal = terminalInventory();
        if (terminal == null) {
            throw new IllegalStateException("gtlcore 原版样板总成的样板仓不可用（getTerminalPatternInventory() == null）");
        }
        terminal.setItemDirect(slot, stack);
    }

    /** 原版机没有本模组的逐槽类型缓存入口，因此不写这类元数据。 */
    @Override
    public void writeRecipeType(int slot, int recipeType) {
    }

    /** 手动指定模式属于本模组机器，原版机没有对应状态。 */
    @Override
    public void writeManualType(int slot, ResourceLocation typeId) {
    }

    /** 上游逐槽催化剂没有可用的公开写口，不能把未写入的内容计为成功。 */
    @Override
    public boolean writeCatalyst(int slot, CompoundTag catalyst) {
        return false;
    }

    /** 机器设置不在此适配器的搬运范围内，粘贴不能覆盖它们。 */
    @Override
    public void writeSettings(CompoundTag settings) {
    }

    /** 名字不在搬运范围内，粘贴不能覆盖目标机器的名字。 */
    @Override
    public void writeName(String name) {
    }

    /** 固定容量已由 canFit 预检，无需扩容。 */
    @Override
    public void growForBatchInsert(int count) {
    }

    /** 固定容量已由 canFit 预检，无需扩容。 */
    @Override
    public void growToCapacity(int needed) {
    }

    /** 只清已写进档案的样板；催化剂等未搬走的内容必须保留。 */
    @Override
    public void clearAll() {
        int capacity = capacity();
        if (capacity <= 0) {
            return;
        }
        InternalInventory terminal = terminalInventory();
        if (terminal == null) {
            throw new IllegalStateException("gtlcore 原版样板总成的样板仓不可用（getTerminalPatternInventory() == null）");
        }
        for (int i = 0; i < capacity; i++) {
            try {
                ItemStack current = terminal.getStackInSlot(i);
                if (current == null || current.isEmpty()) {
                    continue;
                }
                terminal.setItemDirect(i, ItemStack.EMPTY);
            } catch (Throwable failure) {
                SwallowedErrors.report("VanillaPatternArchiveTarget#clearAll", failure);
                GTLModular.LOGGER.warn("[gtlmodular] 样板仓归档：清空原版样板总成第 {} 槽失败（该槽保持原样）pos={} 原因={}",
                        i + 1, pos(), failure.toString());
            }
        }
    }

    /** 终端逐槽写入已触发上游收尾，额外全仓刷新会重复读取样板。 */
    @Override
    public void afterBulkChange() {
    }
}
