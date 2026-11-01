package com.gtlmodular.common.event;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.common.data.GTItems;
import com.gtlmodular.GTLModular;
import com.gtlmodular.common.machine.nanochip.NanochipPortPartMachine;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * <b>NAC 总成 + 闪存：潜行右键「复制频道」</b>。
 *
 * <h2>为什么需要这个事件钩子（而不是只写在机器的 {@code onUse} 里）</h2>
 * 非潜行右键走机器的 {@link com.gregtechceu.gtceu.api.machine.feature.IInteractedMachine#onUse}
 * （方块 use 一定被调用）；但<b>潜行右键</b>时原版会把交互优先交给手持物品，
 * 方块 {@code use} 不一定被调用 —— 项目里 {@code StripperEmbedEventHandler} 也是因为这个原因
 * 才用 {@link PlayerInteractEvent.RightClickBlock}。所以：
 * <ul>
 *   <li>本类只处理<b>潜行 + 闪存</b>这一种组合 ⇒ 复制频道；</li>
 *   <li>非潜行（粘贴/首次复制）仍由 {@link NanochipPortPartMachine#onUse} 处理，两条路不重叠。</li>
 * </ul>
 *
 * <p>闪存不新建物品：直接用 GTCEu 的 {@code GTItems.TOOL_DATA_STICK}（中文名就是「闪存」），
 * 频道存在它 NBT 的 {@link NanochipPortPartMachine#STICK_FREQUENCY} 键上，
 * 与 gtlcore 用 {@code "pos"} 存坐标的习惯同构、键名不冲突。
 */
@Mod.EventBusSubscriber(modid = GTLModular.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class NanochipPortFlashHandler {

    private NanochipPortFlashHandler() {
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        if (level.isClientSide) {
            return;
        }
        Player player = event.getEntity();
        if (player == null || !player.isShiftKeyDown()) {
            return;
        }
        ItemStack stack = event.getItemStack();
        if (!stack.is(GTItems.TOOL_DATA_STICK.get())) {
            return;
        }
        if (!(level.getBlockEntity(event.getPos()) instanceof MetaMachineBlockEntity be)) {
            return;
        }
        MetaMachine machine = be.getMetaMachine();
        if (!(machine instanceof NanochipPortPartMachine port)) {
            return;
        }
        stack.getOrCreateTag().putInt(NanochipPortPartMachine.STICK_FREQUENCY, port.frequency());
        // 消费掉这次交互：不打开界面、也不让闪存再跑它自己的逻辑
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        player.displayClientMessage(
                Component.literal("[NAC 总成] 已复制频道 → " + port.frequency()), true);
    }
}
