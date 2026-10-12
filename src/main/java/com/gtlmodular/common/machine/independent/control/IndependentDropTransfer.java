package com.gtlmodular.common.machine.independent.control;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gtlmodular.common.machine.independent.persistence.IndependentBatchPersistence;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

public final class IndependentDropTransfer {
    private static final String PAUSED = "gtlmodular_independent_paused";

    private IndependentDropTransfer() {}

    public static void remove(MetaMachine machine, Block block) {
        var controller = IndependentLogicAccess.controller(machine);
        if (controller == null || !controller.ownsWork() || controller.detached() || machine.isRemote()) return;
        var level = machine.getLevel();
        if (level.captureBlockSnapshots || level.restoringBlockSnapshots
                || IndependentMovement.moving(level, machine.getPos())) return;
        if (!controller.resolveOwnership() && !controller.ownsWork()) return;
        ItemStack item = new ItemStack(block);
        machine.holder.saveManagedPersistentData(item.getOrCreateTag(), true);
        controller.save(item.getOrCreateTag(), true);
        item.getOrCreateTag().putBoolean(PAUSED, !((IRecipeLogicMachine) machine).isWorkingEnabled());
        // 所有权只在实际移除方块时转移；getDrops 可被第三方用于模拟，不能在那里清表或发放批次副本。
        var pos = machine.getPos();
        var entity = new ItemEntity(machine.getLevel(), pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, item);
        entity.setDefaultPickUpDelay();
        if (machine.getLevel().addFreshEntity(entity)) controller.detach();
    }

    public static void place(MetaMachine machine, ItemStack item) {
        var tag = item.getTag();
        if (machine == null || machine.isRemote() || tag == null || !tag.contains(IndependentBatchPersistence.ROOT_KEY)) return;
        if (!(machine instanceof IRecipeLogicMachine workable) || !(workable.getRecipeLogic() instanceof IndependentLogicAccess access)) {
            throw new IllegalStateException("Independent batch item requires its original recipe controller");
        }
        var controller = access.gtlmodular$controller(true);
        controller.load(tag);
        controller.placed(item);
        workable.getRecipeLogic().setWorkingEnabled(!tag.getBoolean(PAUSED));
        workable.getRecipeLogic().updateTickSubscription();
        machine.markDirty();
        // 此处仍在 Forge 放置事务中；由下一次服务端 tick 确认令牌和清理物品。
    }

    public static void strip(ItemStack item) {
        if (item.getTag() == null) return;
        item.getTag().remove(IndependentBatchPersistence.ROOT_KEY);
        item.getTag().remove(PAUSED);
        item.getTag().remove(IndependentBatchOwnership.TRANSFER);
    }
}
