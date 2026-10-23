package com.gtlmodular.common.machine.independent.adapter;

import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gtladd.gtladditions.api.machine.logic.GTLAddMultipleRecipesLogic;
import com.gtladd.gtladditions.api.machine.logic.GTLAddMultipleTypeRecipesLogic;
import com.gtladd.gtladditions.api.machine.logic.GTLAddMultipleTypeWirelessRecipesLogic;
import com.gtladd.gtladditions.api.machine.logic.GTLAddMultipleWirelessRecipesLogic;
import com.gtladd.gtladditions.api.machine.logic.MutableRecipesLogic;
import com.gtlmodular.common.machine.MoltenModuleMachineBase;
import com.gtlmodular.mixin.independent.MultipleRecipeEnergyInvoker;
import com.gtlmodular.mixin.independent.MultipleRecipeLogicInvoker;
import com.gtlmodular.mixin.independent.MutableRecipeLogicInvoker;
import net.minecraft.resources.ResourceLocation;
import org.gtlcore.gtlcore.common.machine.trait.MultipleRecipesLogic;

import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

public final class IndependentRecipeAdapters {
    private IndependentRecipeAdapters() {}

    public static Selection select(RecipeLogic logic, Supplier<Map<ResourceLocation, Integer>> activeRecipes) {
        if (logic instanceof MultipleRecipesLogic multiple && logic.getMachine() instanceof MoltenModuleMachineBase<?> module) {
            return new Selection(new MoltenOreModuleAdapter(multiple, module, activeRecipes), null);
        }
        if (logic instanceof GTLAddMultipleRecipesLogic multiple) {
            String reason = RecipeAdapterSupport.unsupportedOverride(logic.getClass(), GTLAddMultipleRecipesLogic.class,
                    Set.of(GTLAddMultipleTypeRecipesLogic.class, GTLAddMultipleWirelessRecipesLogic.class,
                            GTLAddMultipleTypeWirelessRecipesLogic.class),
                    Set.of("getMachine", "getMultipleThreads", "lookupRecipeIterator", "checkRecipe", "checkBeforeWorking",
                            "getMaxParallel", "getTotalEuOfRecipe", "getEuMultiplier", "getWirelessRecipeEut", "isEnergyConsumer"));
            if (reason != null) return new Selection(null, reason);
            if (!(logic instanceof MultipleRecipeLogicInvoker) || !(logic instanceof MultipleRecipeEnergyInvoker)) {
                return new Selection(null, "普通族只读访问器未完整注入");
            }
            return new Selection(new MultipleRecipeAdapter(multiple, activeRecipes), null);
        }
        if (logic instanceof MutableRecipesLogic<?> mutable) {
            String reason = RecipeAdapterSupport.unsupportedOverride(logic.getClass(), MutableRecipesLogic.class, Set.of(),
                    Set.of("getMachine", "getMultipleThreads", "lookupRecipeSet", "checkRecipe", "checkBeforeWorking",
                            "calculateParallel", "getEuMultiplier", "getEnergyReductionMultiplier", "getDurationReductionMultiplier",
                            "getRecipeEut", "getWirelessMachine"));
            if (reason != null) return new Selection(null, reason);
            if (!(logic instanceof MutableRecipeLogicInvoker)) return new Selection(null, "Mutable 族只读访问器未注入");
            return new Selection(new MutableRecipeAdapter(mutable, activeRecipes), null);
        }
        return new Selection(null, "需要专用逻辑族适配：" + logic.getClass().getName());
    }

    public record Selection(IndependentRecipeAdapter adapter, String unsupportedReason) {
        public boolean supported() { return adapter != null; }
    }
}
