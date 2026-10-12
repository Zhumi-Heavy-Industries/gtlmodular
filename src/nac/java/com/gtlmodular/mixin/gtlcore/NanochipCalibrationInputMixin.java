package com.gtlmodular.mixin.gtlcore;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gtlmodular.common.machine.nanochip.NanochipCalibrationFluids;
import com.gtlmodular.common.machine.nanochip.NanochipModuleMachine;
import org.gtlcore.gtlcore.api.recipe.RecipeCacheStrategy;
import org.gtlcore.gtlcore.api.recipe.RecipeRunnerHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 合批方法会被上游覆盖；共用扣料入口同时保留免耗缓存隔离和实际产出来源。 */
@Mixin(value = RecipeRunnerHelper.class, remap = false)
public abstract class NanochipCalibrationInputMixin {

    @Inject(method = "handleRecipeInput", at = @At("HEAD"), cancellable = true, require = 1)
    private static void gtlmodular$consumeWithoutCachingExemption(IRecipeLogicMachine machine, GTRecipe recipe,
                                                                 CallbackInfoReturnable<Boolean> cir) {
        if (!(machine instanceof NanochipModuleMachine module)) return;
        boolean exempt = NanochipCalibrationFluids.exemptionFor(module) != null;
        if (!exempt && !module.getModuleSpec().features().assembly()) return;
        boolean consumed = RecipeRunnerHelper.handleRecipe(IO.IN, machine, recipe.inputs,
                machine.getRecipeLogic().getChanceCaches(), true, recipe, false,
                exempt ? RecipeCacheStrategy.HANDLE_PART_CACHE_ONLY : RecipeCacheStrategy.FULL_CACHE).isSuccess();
        // 此处的配方已完成并行缩放和概率结算；耗电回调可能只收到未缩放的原配方。
        if (consumed) module.captureCalibrationProduction(recipe);
        cir.setReturnValue(consumed);
    }
}
