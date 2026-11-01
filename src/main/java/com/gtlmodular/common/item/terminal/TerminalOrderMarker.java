package com.gtlmodular.common.item.terminal;

import com.gtlmodular.common.data.GTLModularItems;

import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

/** GTO 式订单标记：藏在终端物品 NBT 里，不让玩家看到额外物品。 */
public final class TerminalOrderMarker {

    private static final String MARKER = "gtlmodular_order_marker";
    private static final String TARGET = "gtlmodular_order_target";
    private static final String FINGERPRINT = "gtlmodular_order_fp";

    private TerminalOrderMarker() {}

    public static ItemStack create(GlobalPos target, String fingerprint) {
        ItemStack stack = new ItemStack(GTLModularItems.WHALE_TERMINAL.get());
        CompoundTag tag = stack.getOrCreateTag();
        tag.putBoolean(MARKER, true);
        tag.putLong(TARGET, target.pos().asLong());
        tag.putString(FINGERPRINT, fingerprint);
        return stack;
    }

    public static boolean isMarker(AEKey key) {
        if (!(key instanceof AEItemKey itemKey)
                || itemKey.getItem() != GTLModularItems.WHALE_TERMINAL.get()) return false;
        CompoundTag tag = itemKey.toStack().getTag();
        return tag != null && tag.getBoolean(MARKER);
    }

    public static boolean isMarker(GenericStack stack) {
        return stack != null && isMarker(stack.what());
    }
}
