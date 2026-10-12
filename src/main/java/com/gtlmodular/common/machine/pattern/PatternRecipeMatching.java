package com.gtlmodular.common.machine.pattern;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;
import com.gregtechceu.gtceu.common.item.IntCircuitBehaviour;
import com.lowdragmc.lowdraglib.side.fluid.FluidStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 让两种样板总成使用相同的配方身份判据。 */
public final class PatternRecipeMatching {

    private PatternRecipeMatching() {
    }

    /** 样板可能裁掉副产物，因此用输入覆盖和主产物判断身份。 */
    public static boolean patternIdentityMatches(GTRecipe recipe, IPatternDetails pattern) {
        if (recipe == null || pattern == null) {
            return true;
        }
        Set<Item> patternOutItems = new HashSet<>();
        Set<Fluid> patternOutFluids = new HashSet<>();
        collectFingerprint(pattern.getOutputs(), patternOutItems, patternOutFluids);

        Set<Item> patternInItems = new HashSet<>();
        Set<Fluid> patternInFluids = new HashSet<>();
        IPatternDetails.IInput[] inputs = pattern.getInputs();
        if (inputs != null) {
            for (IPatternDetails.IInput input : inputs) {
                if (input != null) {
                    collectFingerprint(input.getPossibleInputs(), patternInItems, patternInFluids);
                }
            }
        }

        if (!recipeInputItemsCovered(recipe, patternInItems) || !recipeInputFluidsCovered(recipe, patternInFluids)) {
            return false;
        }

        boolean itemMatches = !patternOutItems.isEmpty() && primaryItemOutputCovered(recipe, patternOutItems);
        boolean fluidMatches = !patternOutFluids.isEmpty() && primaryFluidOutputCovered(recipe, patternOutFluids);
        return itemMatches || fluidMatches;
    }

    /** 身份只取物品和流体种类，避免数量及 NBT 干扰比对。 */
    private static void collectFingerprint(GenericStack[] stacks, Set<Item> items, Set<Fluid> fluids) {
        if (stacks == null) {
            return;
        }
        for (GenericStack stack : stacks) {
            if (stack == null) {
                continue;
            }
            AEKey key = stack.what();
            if (key instanceof AEItemKey itemKey) {
                items.add(itemKey.getItem());
            } else if (key instanceof AEFluidKey fluidKey) {
                fluids.add(fluidKey.getFluid());
            }
        }
    }

    /** 样板已剥离编程电路，非消耗输入也不应限制身份。 */
    private static boolean recipeInputItemsCovered(GTRecipe recipe, Set<Item> patternItems) {
        List<Content> contents = recipe.getInputContents(ItemRecipeCapability.CAP);
        if (contents == null || contents.isEmpty()) {
            return true;
        }
        for (Content content : contents) {
            if (content == null || content.chance <= 0) {
                continue;
            }
            if (!(content.getContent() instanceof Ingredient ingredient)) {
                continue;
            }
            if (isCircuitIngredient(ingredient)) {
                continue;
            }
            if (!ingredientCoveredByItems(ingredient, patternItems)) {
                return false;
            }
        }
        return true;
    }

    /** 非消耗流体不应阻止样板身份匹配。 */
    private static boolean recipeInputFluidsCovered(GTRecipe recipe, Set<Fluid> patternFluids) {
        List<Content> contents = recipe.getInputContents(FluidRecipeCapability.CAP);
        if (contents == null || contents.isEmpty()) {
            return true;
        }
        for (Content content : contents) {
            if (content == null || content.chance <= 0) {
                continue;
            }
            if (!(content.getContent() instanceof FluidIngredient ingredient)) {
                continue;
            }
            if (!fluidIngredientCoveredByFluids(ingredient, patternFluids)) {
                return false;
            }
        }
        return true;
    }

    /** 样板可能裁掉副产物，因此只用首个物品产物判断。 */
    private static boolean primaryItemOutputCovered(GTRecipe recipe, Set<Item> patternItems) {
        List<Content> contents = recipe.getOutputContents(ItemRecipeCapability.CAP);
        if (contents == null) {
            return false;
        }
        for (Content content : contents) {
            if (content != null && content.getContent() instanceof Ingredient ingredient) {
                return ingredientCoveredByItems(ingredient, patternItems);
            }
        }
        return false;
    }

    /** 样板可能裁掉副产物，因此只用首个流体产物判断。 */
    private static boolean primaryFluidOutputCovered(GTRecipe recipe, Set<Fluid> patternFluids) {
        List<Content> contents = recipe.getOutputContents(FluidRecipeCapability.CAP);
        if (contents == null) {
            return false;
        }
        for (Content content : contents) {
            if (content != null && content.getContent() instanceof FluidIngredient ingredient) {
                return fluidIngredientCoveredByFluids(ingredient, patternFluids);
            }
        }
        return false;
    }

    /** 任一候选物品即可满足一路输入，空原料不构成约束。 */
    private static boolean ingredientCoveredByItems(Ingredient ingredient, Set<Item> patternItems) {
        ItemStack[] stacks = ingredient.getItems();
        if (stacks == null || stacks.length == 0) {
            return true;
        }
        for (ItemStack stack : stacks) {
            if (stack != null && !stack.isEmpty() && patternItems.contains(stack.getItem())) {
                return true;
            }
        }
        return false;
    }

    /** 任一候选流体即可满足一路输入，空原料不构成约束。 */
    private static boolean fluidIngredientCoveredByFluids(FluidIngredient ingredient, Set<Fluid> patternFluids) {
        FluidStack[] stacks = ingredient.getStacks();
        if (stacks == null || stacks.length == 0) {
            return true;
        }
        for (FluidStack stack : stacks) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            Fluid fluid = stack.getFluid();
            if (fluid != null && fluid != Fluids.EMPTY && patternFluids.contains(fluid)) {
                return true;
            }
        }
        return false;
    }

    /** 编程电路已从样板身份中剥离，匹配时也要跳过。 */
    private static boolean isCircuitIngredient(Ingredient ingredient) {
        ItemStack[] stacks = ingredient.getItems();
        if (stacks == null) {
            return false;
        }
        for (ItemStack stack : stacks) {
            if (stack != null && !stack.isEmpty() && IntCircuitBehaviour.isIntegratedCircuit(stack)) {
                return true;
            }
        }
        return false;
    }
}
