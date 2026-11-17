package com.gtlmodular.common.event;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gtlmodular.common.machine.engine.ThreadedRecipeLogic;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

/**
 * ★ 2026-11-10：<b>把「右键机器的玩家」记录成该机器的电网取电身份</b>。
 *
 * <p>为什么还需要这一手：放置回调（{@code IMachineLife.onMachinePlaced}）只覆盖
 * <b>本次更新之后新放的机器</b>；老存档里的机器在放置时没有记录 ⇒ 拿不到电网身份。
 * 右键一次（开 GUI / 点机器都算）就把当前玩家写进去，随机器存档持久化。
 *
 * <p>安全边界：只在服务端、只对 {@link MetaMachineBlockEntity}、只对
 * {@code getRecipeLogic() instanceof ThreadedRecipeLogic} 的本模组机器生效；
 * 其它方块/机器原样放行（不改事件结果、不取消）。
 */
public final class GridUserBinding {

    private GridUserBinding() {}

    /** {@code PlayerInteractEvent.RightClickBlock}：服务端把右键玩家登记成电网身份。 */
    public static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        try {
            if (event.getLevel() == null || event.getLevel().isClientSide || event.getEntity() == null) {
                return;
            }
            if (!(event.getLevel().getBlockEntity(event.getPos()) instanceof MetaMachineBlockEntity mbe)) {
                return;
            }
            MetaMachine machine = mbe.getMetaMachine();
            if (machine instanceof IRecipeLogicMachine recipeMachine
                    && recipeMachine.getRecipeLogic() instanceof ThreadedRecipeLogic logic) {
                logic.setGridUser(event.getEntity().getUUID());
            }
        } catch (Throwable ignored) {
            // 右键路径的兜底：任何意外都不该影响玩家交互
        }
    }
}
