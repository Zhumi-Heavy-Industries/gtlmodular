package com.gtlmodular.mixin.independent;

import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gtlmodular.common.machine.independent.control.IndependentLogicAccess;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.gtl.enhancedcore.common.recipe.iv.IvNativeEngine", remap = false)
public abstract class EnhancedcoreEngineMixin {
    @Shadow @Final private WorkableElectricMultiblockMachine machine;

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true, require = 1)
    private void gtlmodular$deferExternal(CallbackInfo ci) {
        if (IndependentLogicAccess.blocksExternal(machine)) ci.cancel();
    }
}
