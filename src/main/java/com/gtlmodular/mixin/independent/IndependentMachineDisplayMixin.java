package com.gtlmodular.mixin.independent;

import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gtladd.gtladditions.api.machine.multiblock.GTLAddWorkableElectricMultipleRecipesMachine;
import com.gtlmodular.common.machine.independent.control.IndependentLogicAccess;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(value = {WorkableElectricMultiblockMachine.class, GTLAddWorkableElectricMultipleRecipesMachine.class}, remap = false)
public abstract class IndependentMachineDisplayMixin {
    @Inject(method = "addDisplayText", at = @At("HEAD"), cancellable = true, require = 1)
    private void gtlmodular$ownedDisplay(List<Component> text, CallbackInfo ci) {
        var controller = IndependentLogicAccess.controller(this);
        if (controller != null && controller.ownsWork()) {
            text.addAll(controller.display());
            ci.cancel();
        }
    }

    @Inject(method = "addDisplayText", at = @At("TAIL"), require = 1)
    private void gtlmodular$admissionDisplay(List<Component> text, CallbackInfo ci) {
        var controller = IndependentLogicAccess.controller(this);
        if (controller != null && !controller.ownsWork()) text.addAll(controller.display());
    }
}
