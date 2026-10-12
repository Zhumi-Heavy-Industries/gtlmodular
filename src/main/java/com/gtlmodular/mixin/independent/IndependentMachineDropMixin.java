package com.gtlmodular.mixin.independent;

import com.gregtechceu.gtceu.api.block.MetaMachineBlock;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gtlmodular.common.machine.independent.control.IndependentDropTransfer;
import com.gtlmodular.common.machine.independent.control.IndependentLogicAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(value = MetaMachineBlock.class, remap = false)
public abstract class IndependentMachineDropMixin {
    @Inject(method = {"getDrops", "m_49635_"}, at = @At("RETURN"), cancellable = true, require = 1)
    private void gtlmodular$singleOwnerDrop(BlockState state, LootParams.Builder builder, CallbackInfoReturnable<List<ItemStack>> cir) {
        if (!(builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof IMachineBlockEntity holder)
                || !IndependentLogicAccess.owns(holder.getMetaMachine())) return;
        var drops = new ArrayList<>(cir.getReturnValue());
        drops.removeIf(stack -> stack.is(((Block) (Object) this).asItem()));
        cir.setReturnValue(drops);
    }

    @Inject(method = {"onRemove", "m_6810_"}, at = @At("HEAD"), require = 1)
    private void gtlmodular$transferOnRemoval(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving, CallbackInfo ci) {
        if (!level.isClientSide && !moving && !level.captureBlockSnapshots && !level.restoringBlockSnapshots
                && state.getBlock() != next.getBlock()) {
            IndependentDropTransfer.remove(MetaMachine.getMachine(level, pos), (Block) (Object) this);
        }
    }

    @Inject(method = {"setPlacedBy", "m_6402_"}, at = @At("TAIL"), require = 1)
    private void gtlmodular$restoreDrop(Level level, BlockPos pos, BlockState state, LivingEntity player, ItemStack stack, CallbackInfo ci) {
        if (!level.isClientSide) IndependentDropTransfer.place(MetaMachine.getMachine(level, pos), stack);
    }

    @Inject(method = {"getCloneItemStack", "m_7397_"}, at = @At("RETURN"), require = 1)
    private void gtlmodular$stripClone(BlockGetter level, BlockPos pos, BlockState state, CallbackInfoReturnable<ItemStack> cir) {
        IndependentDropTransfer.strip(cir.getReturnValue());
    }
}
