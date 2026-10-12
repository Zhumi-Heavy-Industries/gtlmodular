package com.gtlmodular.common.machine.independent.control;

import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gtl.enhancedcore.common.recipe.iv.IvBuffers;
import com.gtl.enhancedcore.common.recipe.iv.IvMachineScope;
import com.gtl.enhancedcore.common.recipe.iv.IvNativeAccess;

final class EnhancedcoreScheduler {
    private EnhancedcoreScheduler() {}

    static IndependentExternalScheduler inspect(RecipeLogic logic) {
        if (!(logic.getMachine() instanceof WorkableElectricMultiblockMachine machine) || !IvMachineScope.nativeTarget(machine)) {
            return new IndependentExternalScheduler(false, false);
        }
        boolean requested = false;
        boolean owned = logic instanceof IvNativeAccess access && access.iv$managed();
        // 不能调用被互斥闸屏蔽的 crossRecipeEnabled，否则本方永远看不到对方的交接请求。
        for (var buffer : IvBuffers.collect(machine)) {
            requested |= IvBuffers.isolated(buffer);
            var state = IvBuffers.state(buffer);
            owned |= state != null && (!state.healthy() || !state.jobs.isEmpty());
        }
        return new IndependentExternalScheduler(requested, owned);
    }
}
