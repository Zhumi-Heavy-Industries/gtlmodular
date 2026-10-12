package com.gtlmodular.mixin.independent;

import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gtlmodular.common.machine.independent.control.IndependentLogicAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.gtl.enhancedcore.common.recipe.iv.IvMachineScope", remap = false)
public abstract class EnhancedcoreScopeMixin {
    @Inject(method = "crossRecipeEnabled(Lcom/gregtechceu/gtceu/api/machine/multiblock/WorkableElectricMultiblockMachine;)Z",
            at = @At("HEAD"), cancellable = true, require = 1)
    private static void gtlmodular$deferExternal(WorkableElectricMultiblockMachine machine, CallbackInfoReturnable<Boolean> cir) {
        if (IndependentLogicAccess.blocksExternal(machine)) cir.setReturnValue(false);
    }
}
