package com.gtlmodular.common.event;

import com.gtlmodular.GTLModular;
import com.gtlmodular.common.machine.MoltenStripperModuleMachine;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 处理器主机嵌入钩子（只作用于「熔岩星核剥离单元」本体）。
 *
 * <p>目的：让"塞处理器主机"的体验与整合包里的「塞星阵」一致 —— 右键剥离单元就能塞、潜行同样生效、
 * 物品被消耗、拆机掉落归还。<b>只有点到剥离单元机器本身（控制器方块）才算</b>，
 * 点它的外壳/管道等结构方块不算。
 *
 * <p>为什么需要这个钩子：星阵是<b>物品自带行为</b>（{@code AstralArrayBehavior} 实现
 * {@code IInteractionItem}），物品侧即可拦截；而处理器主机是纯 KubeJS 物品、身上没有行为，
 * 只能由机器侧拦截。若只依赖 {@code IInteractedMachine.onUse}（方块 use 路径），
 * 潜行右键时原版会把交互优先交给物品、方块 use 不触发 → 塞不进去。
 * 这里改用 Forge 的 {@code RightClickBlock} 事件（在方块 use 之前触发，潜行同样触发）补齐这一点。
 */
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
        // 只有点到剥离单元本体才处理（外壳/管道等结构方块不算）
        MoltenStripperModuleMachine stripper = MoltenStripperModuleMachine.findStripperFor(level, event.getPos());
        if (stripper == null) {
            return;
        }
        // 与星阵一致：消费这次交互（不打开机器界面、不触发放置方块等）
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (!level.isClientSide) {
            stripper.embedFromHeld(event.getEntity(), stack);
        }
    }
}
