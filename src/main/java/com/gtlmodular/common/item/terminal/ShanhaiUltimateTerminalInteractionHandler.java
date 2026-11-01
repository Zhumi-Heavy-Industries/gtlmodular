// Ported from GT-Shanhai (https://github.com/dishanhai/gtl_shanhai-dishanhai),
// (c) 山海恒长在 (dishanhai), LGPL-3.0. Ported with the author's permission.
package com.gtlmodular.common.item.terminal;

import com.gtlmodular.GTLModular;
import com.gtlmodular.common.data.GTLModularItems;
import com.gtlmodular.common.item.ShanhaiUltimateTerminalBehavior;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = GTLModular.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ShanhaiUltimateTerminalInteractionHandler {

    private ShanhaiUltimateTerminalInteractionHandler() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        ItemStack terminal = event.getItemStack();
        if (!terminal.is(GTLModularItems.WHALE_TERMINAL.get())) return;
        if (!(MetaMachine.getMachine(event.getLevel(), event.getPos()) instanceof IMultiController)) return;

        event.setCanceled(true);
        // 一次左鍵還會收到停止／中止挖掘事件；全部阻止挖掘，僅起始事件檢查。
        if (event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START) return;
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            ShanhaiUltimateTerminalBehavior.INSTANCE.scanOnly(serverPlayer, terminal, event.getPos());
        }
    }
}
