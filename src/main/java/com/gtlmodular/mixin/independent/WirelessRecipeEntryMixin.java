package com.gtlmodular.mixin.independent;

import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gtladd.gtladditions.api.machine.logic.GTLAddMultipleWirelessRecipesLogic;
import com.gtlmodular.common.machine.independent.control.IndependentLogicAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = GTLAddMultipleWirelessRecipesLogic.class, remap = false)
public abstract class WirelessRecipeEntryMixin {
    @Inject(method = "getGTRecipe", at = @At("HEAD"), cancellable = true, require = 1)
    private void gtlmodular$stopMerge(CallbackInfoReturnable<GTRecipe> cir) {
        if (IndependentLogicAccess.blockMerge((RecipeLogic) (Object) this)) cir.setReturnValue(null);
    }

    @Inject(method = "getGTRecipe", at = @At("RETURN"), require = 1)
    private void gtlmodular$rememberMerge(CallbackInfoReturnable<GTRecipe> cir) {
        IndependentLogicAccess.rememberMerge((RecipeLogic) (Object) this, cir.getReturnValue());
    }
}
