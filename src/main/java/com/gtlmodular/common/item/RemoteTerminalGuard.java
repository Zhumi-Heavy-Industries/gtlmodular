package com.gtlmodular.common.item;

import com.gtlmodular.common.data.GTLModularItems;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 「持有哦鲸鲸终端 = 视为在 eco UI 的 8 格交互范围内」判定。
 *
 * <p>只给 eco 的 BlockUIHolder 距离校验用；不改变方块本身、也不改变其它模组。
 */
public final class RemoteTerminalGuard {

    private RemoteTerminalGuard() {}

    public static boolean hasTerminal(Player player) {
        if (player == null) return false;
        if (GTLModularItems.WHALE_TERMINAL == null) return false;
        if (player.getMainHandItem().is(GTLModularItems.WHALE_TERMINAL.get())) return true;
        if (player.getOffhandItem().is(GTLModularItems.WHALE_TERMINAL.get())) return true;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(GTLModularItems.WHALE_TERMINAL.get())) return true;
        }
        return false;
    }
}
