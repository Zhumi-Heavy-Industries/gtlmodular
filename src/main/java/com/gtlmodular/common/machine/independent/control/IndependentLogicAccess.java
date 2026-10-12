package com.gtlmodular.common.machine.independent.control;

import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;

public interface IndependentLogicAccess {
    IndependentMachineController gtlmodular$controller();
    IndependentMachineController gtlmodular$controller(boolean create);
    void gtlmodular$clearUpstreamCache();
    void gtlmodular$subscribeIndependent();
    UpstreamRecipeSetup<GTRecipe> gtlmodular$upstreamSetup();

    static IndependentMachineController controller(Object machine) {
        return machine instanceof IRecipeLogicMachine workable ? controller(workable.getRecipeLogic()) : null;
    }

    static IndependentMachineController controller(RecipeLogic logic) {
        return logic instanceof IndependentLogicAccess access ? access.gtlmodular$controller() : null;
    }

    static boolean owns(Object machine) {
        var controller = controller(machine);
        return controller != null && controller.ownsWork();
    }

    static boolean blocksExternal(Object machine) {
        var controller = controller(machine);
        return controller != null && controller.blocksExternal();
    }

    static boolean blockStarts(RecipeLogic logic) {
        if (!(logic instanceof IndependentLogicAccess access)) return false;
        var controller = access.gtlmodular$controller(IndependentMachineRegistry.eligible(logic));
        return controller != null && controller.blockUpstreamStarts();
    }

    static boolean blockMerge(RecipeLogic logic) {
        if (logic instanceof IndependentLogicAccess access) access.gtlmodular$upstreamSetup().clear();
        return blockStarts(logic);
    }

    static void rememberMerge(RecipeLogic logic, GTRecipe recipe) {
        if (logic instanceof IndependentLogicAccess access) access.gtlmodular$upstreamSetup().remember(recipe);
    }
}
