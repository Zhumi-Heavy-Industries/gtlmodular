package com.gtlmodular.common.event;

import com.gtlmodular.GTLModular;
import com.gtlmodular.common.machine.MoltenStripperModuleMachine;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** 潜行时物品会抢先处理交互，因此在方块 use 之前的事件中处理主机嵌入。 */
@Mod.EventBusSubscriber(modid = GTLModular.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class StripperEmbedEventHandler {

    private StripperEmbedEventHandler() {
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        ItemStack stack = event.getItemStack();
        if (!MoltenStripperModuleMachine.isEmbeddable(stack)) {
            return;
        }
        Level level = event.getLevel();
        MoltenStripperModuleMachine stripper = MoltenStripperModuleMachine.findStripperFor(level, event.getPos());
        if (stripper == null) {
            return;
        }
        // 已消费物品，必须终止本次交互，避免继续开界面或放置方块。
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (!level.isClientSide) {
            stripper.embedFromHeld(event.getEntity(), stack);
        }
    }
}
