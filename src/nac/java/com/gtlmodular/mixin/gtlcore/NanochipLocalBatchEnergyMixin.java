package com.gtlmodular.mixin.gtlcore;

import com.gregtechceu.gtceu.api.capability.recipe.IRecipeCapabilityHolder;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gtlmodular.common.machine.nanochip.NanochipLocalBatchEnergy;
import com.gtlmodular.common.machine.nanochip.NanochipModuleMachine;
import org.gtlcore.gtlcore.api.recipe.IParallelLogic;
import org.gtlcore.gtlcore.api.recipe.RecipeRunnerHelper;
import org.gtlcore.gtlcore.common.machine.trait.MultipleRecipesLogic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// 上游以默认优先级覆盖合批方法，内部调用注入必须拥有更高优先级。
// gtladditions 以 1000 重写 getRecipe，Enhancedcore 以 1200 在同一个 getMaxParallel 调用上做 @ModifyArg（require=1）。
// 必须排在两者之后：既能注入被合并的方法，又不会先把调用替换掉、让 Enhancedcore 找不到目标而启动失败。
@Mixin(value = MultipleRecipesLogic.class, remap = false, priority = 1300)
public abstract class NanochipLocalBatchEnergyMixin {
    @Shadow public abstract WorkableElectricMultiblockMachine getMachine();
    @Shadow protected abstract double getTotalEuOfRecipe(GTRecipe recipe);
    @Shadow protected abstract double getEuMultiplier();

    @Unique private NanochipLocalBatchEnergy gtlmodular$localBatchEnergy;

    // 完成回调也会直接调用私有合批方法，预算必须在每次合批入口重置。
    @Inject(method = "getRecipe", at = @At("HEAD"), require = 1)
    private void gtlmodular$beginLocalBatch(CallbackInfoReturnable<GTRecipe> cir) {
        gtlmodular$localBatchEnergy = null;
        if (getMachine() instanceof NanochipModuleMachine module) {
            var energy = NanochipLocalBatchEnergy.finiteLocalContainer(module);
            if (energy != null) {
                gtlmodular$localBatchEnergy = new NanochipLocalBatchEnergy(
                        energy.getEnergyCapacity(), module.getOverclockVoltage());
            }
        }
    }

    @Redirect(method = "getRecipe", at = @At(value = "INVOKE", target =
            "Lorg/gtlcore/gtlcore/api/recipe/IParallelLogic;getMaxParallel(Lcom/gregtechceu/gtceu/api/capability/recipe/IRecipeCapabilityHolder;Lcom/gregtechceu/gtceu/api/recipe/GTRecipe;J)J"), require = 1)
    private long gtlmodular$fitLocalBatch(IRecipeCapabilityHolder holder, GTRecipe recipe, long limit) {
        long available = IParallelLogic.getMaxParallel(holder, recipe, limit);
        return gtlmodular$localBatchEnergy == null ? available
                : gtlmodular$localBatchEnergy.limitParallel(available, getTotalEuOfRecipe(recipe), getEuMultiplier());
    }

    @Redirect(method = "getRecipe", at = @At(value = "INVOKE", target =
            "Lorg/gtlcore/gtlcore/api/recipe/RecipeRunnerHelper;handleRecipeInput(Lcom/gregtechceu/gtceu/api/machine/feature/IRecipeLogicMachine;Lcom/gregtechceu/gtceu/api/recipe/GTRecipe;)Z"), require = 1)
    private boolean gtlmodular$accountConsumedBatch(IRecipeLogicMachine machine, GTRecipe recipe) {
        boolean consumed = RecipeRunnerHelper.handleRecipeInput(machine, recipe);
        if (gtlmodular$localBatchEnergy != null) gtlmodular$localBatchEnergy.recordConsumption(consumed);
        return consumed;
    }
}
