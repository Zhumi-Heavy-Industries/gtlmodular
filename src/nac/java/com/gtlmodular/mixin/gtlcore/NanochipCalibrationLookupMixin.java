package com.gtlmodular.mixin.gtlcore;

import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.capability.recipe.IRecipeCapabilityHolder;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gtlmodular.common.machine.nanochip.NanochipCalibrationFluids;
import com.gtlmodular.common.machine.nanochip.NanochipModuleMachine;
import org.gtlcore.gtlcore.api.machine.trait.ILockRecipe;
import org.gtlcore.gtlcore.api.recipe.RecipeRunnerHelper;
import org.gtlcore.gtlcore.common.machine.trait.MultipleRecipesLogic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Iterator;

/** 上游搜索为私有方法；仅替换获免耗的 NAC 候选，继续复用原检查、合批和扣料。 */
@Mixin(value = MultipleRecipesLogic.class, remap = false)
public abstract class NanochipCalibrationLookupMixin {

    @Shadow
    public abstract WorkableElectricMultiblockMachine getMachine();

    @Shadow
    private boolean checkRecipe(GTRecipe recipe) {
        throw new AssertionError();
    }

    @Inject(method = "lookupRecipeIterator", at = @At("HEAD"), cancellable = true, require = 1)
    private void gtlmodular$calibratedInputs(CallbackInfoReturnable<Iterator<GTRecipe>> cir) {
        if (getMachine() instanceof NanochipModuleMachine module) {
            Iterator<GTRecipe> candidates = NanochipCalibrationFluids.lookup(
                    module, (ILockRecipe) this, this::checkRecipe);
            if (candidates != null) cir.setReturnValue(candidates);
        }
    }

    // 临时免耗配方不能写入样板仓的永久候选缓存，否则换族后仍会跳过培养基。
    @Redirect(method = "checkRecipe", at = @At(value = "INVOKE", target =
            "Lorg/gtlcore/gtlcore/api/recipe/RecipeRunnerHelper;matchRecipe(Lcom/gregtechceu/gtceu/api/capability/recipe/IRecipeCapabilityHolder;Lcom/gregtechceu/gtceu/api/recipe/GTRecipe;)Z"), require = 1)
    private boolean gtlmodular$matchWithoutCachingExemption(IRecipeCapabilityHolder holder, GTRecipe recipe) {
        if (holder instanceof NanochipModuleMachine module && NanochipCalibrationFluids.exemptionFor(module) != null) {
            return RecipeRunnerHelper.matchRecipeInputNoMEInnerCache(holder, recipe)
                    && RecipeRunnerHelper.matchRecipeOutput(holder, recipe);
        }
        return RecipeRunnerHelper.matchRecipe(holder, recipe);
    }
}
