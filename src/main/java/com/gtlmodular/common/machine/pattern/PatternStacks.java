package com.gtlmodular.common.machine.pattern;

import appeng.api.stacks.GenericStack;
import com.lowdragmc.lowdraglib.misc.FluidTransferList;
import com.lowdragmc.lowdraglib.misc.ItemStackTransfer;

/** 让样板缩放和催化剂存档判断在两种总成中保持一致。 */
public final class PatternStacks {

    private PatternStacks() {
    }

    /** 缩放后至少保留一个单位，避免产生零数量的样板堆叠。 */
    public static GenericStack[] scaleStacks(GenericStack[] source, int mul, int div) {
        if (source == null) {
            return null;
        }
        GenericStack[] result = new GenericStack[source.length];
        for (int i = 0; i < source.length; i++) {
            GenericStack stack = source[i];
            if (stack == null) {
                continue;
            }
            long amount = stack.amount() * (long) mul / Math.max(1, div);
            result[i] = new GenericStack(stack.what(), Math.max(1, amount));
        }
        return result;
    }

    /** 空催化剂物品仓无需写入存档。 */
    public static boolean hasAnyCatalystItem(ItemStackTransfer transfer) {
        if (transfer == null) {
            return false;
        }
        for (int i = 0; i < transfer.getSlots(); i++) {
            if (!transfer.getStackInSlot(i).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** 空催化剂流体仓无需写入存档。 */
    public static boolean hasAnyCatalystFluid(FluidTransferList fluids) {
        if (fluids == null) {
            return false;
        }
        for (int i = 0; i < fluids.getTanks(); i++) {
            if (!fluids.getFluidInTank(i).isEmpty()) {
                return true;
            }
        }
        return false;
    }
}
