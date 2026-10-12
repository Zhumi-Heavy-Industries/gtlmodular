package com.gtlmodular.common.machine.fuel;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.api.recipe.logic.OCParams;
import com.gregtechceu.gtceu.api.recipe.logic.OCResult;
import com.gregtechceu.gtceu.common.data.GTRecipeModifiers;
import com.gtlmodular.GTLModular;

/** 允许低电压慢跑高电压配方，以耗时补偿压低的 EU/t，避免标准超频直接拒绝降档配方。 */
public final class MoltenRelaxedElectricOverclock {

    private MoltenRelaxedElectricOverclock() {
    }

    public static GTRecipe apply(MetaMachine machine, GTRecipe recipe,
                                 OCParams params, OCResult result) {
        if (!(machine instanceof WorkableElectricMultiblockMachine workable)) {
            return null;
        }
        long maxVoltage = workable.getOverclockVoltage();
        if (maxVoltage <= 0) {
            return null;
        }
        long recipeEUt = RecipeHelper.getInputEUt(recipe);
        if (recipeEUt <= maxVoltage) {
            return GTRecipeModifiers.ELECTRIC_OVERCLOCK
                    .apply(com.gregtechceu.gtceu.api.recipe.OverclockingLogic.NON_PERFECT_OVERCLOCK_SUBTICK)
                    .apply(machine, recipe, params, result);
        }
        try {
            GTRecipe copy = recipe.copy();
            double ratio = recipeEUt / (double) maxVoltage;
            long newDuration = Math.max(1, Math.round(recipe.duration * ratio));
            RecipeHelper.setInputEUt(copy, maxVoltage);
            copy.duration = (int) Math.min(Integer.MAX_VALUE, newDuration);
            return GTRecipeModifiers.ELECTRIC_OVERCLOCK
                    .apply(com.gregtechceu.gtceu.api.recipe.OverclockingLogic.NON_PERFECT_OVERCLOCK_SUBTICK)
                    .apply(machine, copy, params, result);
        } catch (RuntimeException e) {
            GTLModular.LOGGER.warn("[gtlmodular] 休闲降档超频失败，退回原配方处理: {}", e.toString());
            return null;
        }
    }
}
