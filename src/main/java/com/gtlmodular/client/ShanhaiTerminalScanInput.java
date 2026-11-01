// Ported from GT-Shanhai (https://github.com/dishanhai/gtl_shanhai-dishanhai),
// (c) 山海恒长在 (dishanhai), LGPL-3.0. Ported with the author's permission.
package com.gtlmodular.client;

import com.gtlmodular.GTLModular;
import com.gtlmodular.common.data.GTLModularItems;
import com.gtlmodular.common.item.ShanhaiUltimateTerminalConfig;
import com.gtlmodular.network.GTLModularNetwork;
import com.gtlmodular.network.OpenEcoSubsystemPacket;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/** 山海：取消挖掘後原版仍會持續嘗試 START；在送出前限制每次按下只掃描一次。 */
@Mod.EventBusSubscriber(modid = GTLModular.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ShanhaiTerminalScanInput {

    private static final PressState PRESS = new PressState();

    private ShanhaiTerminalScanInput() {}

    @SubscribeEvent
    public static void onAttack(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isAttack()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.screen != null) return;
        if (!minecraft.player.getMainHandItem().is(GTLModularItems.WHALE_TERMINAL.get())) return;

        boolean onController = minecraft.hitResult instanceof BlockHitResult hit
                && hit.getType() == HitResult.Type.BLOCK
                && MetaMachine.getMachine(minecraft.level, hit.getBlockPos()) instanceof IMultiController;

        // 左键：绑定过计算通讯接口时，除 GT 多方块控制器（保留扫描）外，都直接打开绑定子系统 UI
        if (!onController && ShanhaiUltimateTerminalConfig.getBoundEco(minecraft.player.getMainHandItem()) != null) {
            if (!PRESS.begin()) {
                event.setCanceled(true);
                event.setSwingHand(false);
                return;
            }
            GTLModularNetwork.CHANNEL.sendToServer(new OpenEcoSubsystemPacket(net.minecraft.world.InteractionHand.MAIN_HAND));
            event.setCanceled(true);
            event.setSwingHand(false);
            return;
        }

        if (!(minecraft.hitResult instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK
                || !(MetaMachine.getMachine(minecraft.level, hit.getBlockPos()) instanceof IMultiController)) return;

        if (!PRESS.begin()) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    @SubscribeEvent
    public static void onMouseButton(InputEvent.MouseButton.Post event) {
        if (event.getAction() == GLFW.GLFW_RELEASE
                && Minecraft.getInstance().options.keyAttack.matchesMouse(event.getButton())) PRESS.release();
    }

    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {
        if (event.getAction() == GLFW.GLFW_RELEASE
                && Minecraft.getInstance().options.keyAttack.matches(event.getKey(), event.getScanCode())) PRESS.release();
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null || !minecraft.isWindowActive()
                || !minecraft.options.keyAttack.isDown()) PRESS.release();
    }

    static final class PressState {
        private boolean held;

        boolean begin() {
            if (held) return false;
            held = true;
            return true;
        }

        void release() {
            held = false;
        }
    }
}
