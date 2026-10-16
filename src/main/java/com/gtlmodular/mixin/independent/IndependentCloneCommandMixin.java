package com.gtlmodular.mixin.independent;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gtlmodular.common.machine.independent.control.IndependentBatchOwnership;
import com.gtlmodular.common.machine.independent.control.IndependentLogicAccess;
import com.gtlmodular.common.machine.independent.control.IndependentMovement;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.commands.CloneCommands;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Mixin(value = CloneCommands.class, remap = false)
public abstract class IndependentCloneCommandMixin {
    @WrapOperation(method = {"clone", "m_264087_"}, at = {
            @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/entity/BlockEntity;saveWithoutMetadata()Lnet/minecraft/nbt/CompoundTag;"),
            @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/entity/BlockEntity;m_187482_()Lnet/minecraft/nbt/CompoundTag;")}, require = 1)
    private static CompoundTag gtlmodular$rememberSources(BlockEntity entity, Operation<CompoundTag> original,
                                                          @Share("batchSources") LocalRef<Set<UUID>> sources) {
        CompoundTag tag = original.call(entity);
        if (tag.hasUUID(IndependentBatchOwnership.TRANSFER)) {
            if (sources.get() == null) sources.set(new HashSet<>());
            sources.get().add(tag.getUUID(IndependentBatchOwnership.TRANSFER));
        }
        return tag;
    }

    @WrapOperation(method = {"clone", "m_264087_"}, at = {
            @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"),
            @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;m_7731_(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z")}, require = 5)
    private static boolean gtlmodular$moveSource(ServerLevel level, BlockPos pos, BlockState state, int flags,
                                               Operation<Boolean> original,
                                               @Share("batchSources") LocalRef<Set<UUID>> sources) {
        var controller = IndependentLogicAccess.controller(MetaMachine.getMachine(level, pos));
        // force 的源/目标可以重叠；只抑制已进入快照的源所有者，覆盖其他机器仍须保住其批次。
        if (controller != null && sources.get() != null && sources.get().contains(controller.ownershipToken())) {
            return IndependentMovement.move(level, pos, () -> original.call(level, pos, state, flags));
        }
        return original.call(level, pos, state, flags);
    }
}
