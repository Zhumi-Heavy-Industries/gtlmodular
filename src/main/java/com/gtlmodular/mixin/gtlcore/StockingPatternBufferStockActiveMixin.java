package com.gtlmodular.mixin.gtlcore;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachineBase;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEStockingPatternBufferPartMachine;
import org.spongepowered.asm.mixin.Mixin;

/**
 * 库存样板总成的可用槽判定放宽一档：槽里有样板、库存配置里也有货，就算可用。
 * <p>
 * gtlcore 原本只认「槽内实物缓冲非空」，而库存型的料是匹配时现从 ME 抽的、从不落槽，
 * 于是读档后没人往槽里推料时永远 0 候选（玩家只能拆放主机逼 AE 推一次）。
 * 真正能不能供料仍由 {@code isItemActive/isFluidActive}（含库存配置判断）决定，
 * 这里只让这个槽有机会被匹配到；没有库存配置时不改变任何行为。
 */
@Mixin(value = MEStockingPatternBufferPartMachine.class, remap = false)
public abstract class StockingPatternBufferStockActiveMixin extends MEPatternBufferPartMachineBase {

    protected StockingPatternBufferStockActiveMixin(IMachineBlockEntity holder, int patternCount, IO io) {
        super(holder, io);
    }

    @Override
    protected int[] getActiveSlots() {
        int[] active = super.getActiveSlots();
        if (!hasStockedItems()) return active;
        return mergePatternSlots(active, false);
    }

    @Override
    protected int[] getActiveAndUnCachedSlots() {
        int[] active = super.getActiveAndUnCachedSlots();
        if (!hasStockedItems()) return active;
        return mergePatternSlots(active, true);
    }

    /** 库存配置里至少有一项真的在 ME 网络里：没有就不放宽，避免让空配置的槽参与匹配。 */
    private boolean hasStockedItems() {
        try {
            return !((MEStockingPatternBufferPartMachine) (Object) this).getStockingItemMap().isEmpty();
        } catch (RuntimeException | LinkageError failure) {
            return false;
        }
    }

    private int[] mergePatternSlots(int[] active, boolean onlyUncached) {
        IntSet slots = new IntOpenHashSet(active == null ? new int[0] : active);
        int count = getInternalSlotCount();
        for (int slot = 0; slot < count; slot++) {
            if (!hasPatternInSlot(slot)) continue;
            if (onlyUncached && hasRecipeCacheInSlot(slot)) continue;
            slots.add(slot);
        }
        return slots.toIntArray();
    }
}
