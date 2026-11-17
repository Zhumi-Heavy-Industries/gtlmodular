package com.gtlmodular.client.gui.terminal;

import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.integration.jei.multipage.MultiblockInfoCategory;
import com.gregtechceu.gtceu.integration.jei.multipage.MultiblockInfoWrapper;

import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.common.Internal;

import java.util.List;

/** 直接打开 JEI 的 GTCEu 多方块预览页（之前的稳定路径）。 */
public final class TerminalPreviewOpener {

    private TerminalPreviewOpener() {}

    public static boolean open(MultiblockMachineDefinition definition) {
        if (definition == null) return false;
        var registered = GTRegistries.MACHINES.get(definition.getId());
        if (registered instanceof MultiblockMachineDefinition canonical) definition = canonical;
        try {
            IJeiRuntime runtime = Internal.getJeiRuntime();
            if (runtime == null) return false;
            IRecipeCategory<MultiblockInfoWrapper> category =
                    runtime.getRecipeManager().getRecipeCategory(MultiblockInfoCategory.RECIPE_TYPE);
            if (category == null) return false;
            runtime.getRecipesGui().showRecipes(category,
                    List.of(new MultiblockInfoWrapper(definition)), List.of());
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
