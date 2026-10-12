package com.gtlmodular.mixin.independent;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gtlmodular.common.machine.independent.control.IndependentMachineRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MetaMachine.class, remap = false)
public abstract class IndependentMachineLifecycleMixin {
    @Inject(method = "onUnload", at = @At("HEAD"), require = 1)
    private void gtlmodular$unload(CallbackInfo ci) {
        if (!((MetaMachine) (Object) this).isRemote() && (Object) this instanceof IRecipeLogicMachine machine) {
            IndependentMachineRegistry.unloaded(machine.getRecipeLogic());
        }
    }
}
