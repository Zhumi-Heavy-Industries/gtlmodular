package com.gtlmodular.mixin.independent;

import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gtlmodular.common.machine.independent.control.IndependentLogicAccess;
import org.gtlcore.gtlcore.common.machine.trait.MultipleRecipesLogic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// handleRecipeWorking 由 gtladditions（优先级 1000）合并进来；同优先级不能注入别人合并的方法，整合包里会注入失败。
@Mixin(value = MultipleRecipesLogic.class, remap = false, priority = 1300)
public abstract class MultipleRecipeWorkingMixin {
    @Inject(method = "handleRecipeWorking", at = @At("HEAD"), cancellable = true, require = 1)
    private void gtlmodular$isolate(CallbackInfo ci) {
        var controller = IndependentLogicAccess.controller((RecipeLogic) (Object) this);
        if (controller != null && controller.ownsWork()) ci.cancel();
    }
}
