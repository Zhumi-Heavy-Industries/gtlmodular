package com.gtlmodular.common.machine.independent.control;

import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import net.minecraftforge.fml.ModList;

public record IndependentExternalScheduler(boolean requested, boolean owned) {
    public static IndependentExternalScheduler inspect(RecipeLogic logic) {
        if (ModList.get().isLoaded("gtl_enhancedcore")) return EnhancedcoreScheduler.inspect(logic);
        return new IndependentExternalScheduler(false, false);
    }
}
