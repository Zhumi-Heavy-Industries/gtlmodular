package com.gtlmodular.mixin.independent;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import org.gtlcore.gtlcore.common.machine.trait.MultipleRecipesLogic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = MultipleRecipesLogic.class, remap = false)
public interface MultipleRecipeEnergyInvoker {
    @Invoker("getTotalEuOfRecipe") double gtlmodular$totalRecipeEu(GTRecipe recipe);
    @Invoker("getEuMultiplier") double gtlmodular$euMultiplier();
}
