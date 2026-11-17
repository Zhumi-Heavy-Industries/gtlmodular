package com.gtlmodular.client;

import com.gtlmodular.GTLModular;
import com.gtlmodular.common.data.GTLModularItems;
import com.gtlmodular.network.GTLModularNetwork;
import com.gtlmodular.network.TerminalStructureDetectPacket;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 哦鲸鲸终端：按住 Ctrl 右击多方块主机 = 结构检测。
 *
 * <p>在「使用物品」事件里先把这次右击吃掉（不挥手臂、不触发原版 use），再发检测包；
 * 服务端会重新校验手持物、距离与目标，客户端这里只做输入识别。
 *
 * <p>用 {@link Screen#hasControlDown()} 而不是可改键的「疾跑键」：玩家按字面 Ctrl 就会生效，
 * 改键后也不会变成别的功能；潜行时让位给施工/绑定的既有操作。
 */
@Mod.EventBusSubscriber(modid = GTLModular.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WhaleTerminalDetectInput {

    private WhaleTerminalDetectInput() {}

    @SubscribeEvent
    public static void onUseItem(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isUseItem() || event.getHand() != InteractionHand.MAIN_HAND) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.screen != null) return;
        if (!minecraft.player.getMainHandItem().is(GTLModularItems.WHALE_TERMINAL.get())) return;
        if (!Screen.hasControlDown() || minecraft.player.isShiftKeyDown()) return;
        if (!(minecraft.hitResult instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK
                || !(MetaMachine.getMachine(minecraft.level, hit.getBlockPos()) instanceof IMultiController)) return;

        event.setCanceled(true);
        event.setSwingHand(false);
        GTLModularNetwork.CHANNEL.sendToServer(new TerminalStructureDetectPacket(hit.getBlockPos()));
    }
}
