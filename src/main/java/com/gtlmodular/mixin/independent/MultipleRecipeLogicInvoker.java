package com.gtlmodular.mixin.independent;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gtladd.gtladditions.api.machine.IGTLAddMultiRecipeMachine;
import com.gtladd.gtladditions.api.machine.logic.GTLAddMultipleRecipesLogic;
import com.gtladd.gtladditions.api.recipe.WirelessGTRecipe;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.math.BigInteger;
import java.util.Set;

@Mixin(value = GTLAddMultipleRecipesLogic.class, remap = false)
public interface MultipleRecipeLogicInvoker {
    @Invoker("lookupRecipeIterator") Set<GTRecipe> gtlmodular$lookupCandidates();
    @Invoker("checkRecipe") boolean gtlmodular$checkCandidate(GTRecipe recipe);
    @Invoker("checkBeforeWorking") boolean gtlmodular$checkBeforeWorking();
    @Invoker("getMaxParallel") long gtlmodular$maxParallel(GTRecipe recipe, long limit);
    @Invoker("getWirelessRecipeEut") long gtlmodular$wirelessEut(GTRecipe recipe);
    @Invoker("isEnergyConsumer") boolean gtlmodular$isEnergyConsumer();
    @Invoker("getLimited") IGTLAddMultiRecipeMachine gtlmodular$limited();
    @Invoker("buildWirelessRecipe") WirelessGTRecipe gtlmodular$buildWirelessRecipe(
            ObjectArrayList<Content> items, ObjectArrayList<Content> fluids, BigInteger totalEu);
}
