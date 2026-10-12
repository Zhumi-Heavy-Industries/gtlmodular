package com.gtlmodular.common.machine.independent.persistence;

import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.nbt.Tag;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class IndependentRecipePayload {
    public enum Channel { INPUT, TICK_INPUT, TICK_OUTPUT, OUTPUT }
    private GTRecipe recipe;
    private final BatchRecipeExtensions.Power power;
    private final Map<RecipeCapability<?>, Object2IntMap<?>> chanceCaches;
    private Map<RecipeCapability<?>, List<Content>> resolvedOutputs;
    private final Map<Channel, Map<RecipeCapability<?>, List<Content>>> remainders;
    private BatchExecutionStage stage;
    private boolean wirelessPaid;
    private Tag preserved;

    public IndependentRecipePayload(GTRecipe recipe, BatchRecipeExtensions.Power power,
                                    Map<RecipeCapability<?>, Object2IntMap<?>> chanceCaches,
                                    Map<RecipeCapability<?>, List<Content>> resolvedOutputs,
                                    Map<Channel, Map<RecipeCapability<?>, List<Content>>> remainders,
                                    BatchExecutionStage stage) {
        this.recipe = Objects.requireNonNull(recipe);
        this.power = Objects.requireNonNull(power);
        this.chanceCaches = Objects.requireNonNull(chanceCaches);
        this.resolvedOutputs = resolvedOutputs;
        this.remainders = Objects.requireNonNull(remainders);
        this.stage = Objects.requireNonNull(stage);
        validateJournal();
    }

    public GTRecipe recipe() { return recipe; }
    public void replaceUnstartedRecipe(GTRecipe recipe) {
        if (stage.input() != BatchExecutionStage.Input.CONSUMED || remainders.containsKey(Channel.TICK_INPUT)) {
            throw new IllegalStateException("Cannot replace a recipe after tick IO");
        }
        this.recipe = Objects.requireNonNull(recipe);
    }
    public BatchRecipeExtensions.Power power() { return power; }
    public Map<RecipeCapability<?>, Object2IntMap<?>> chanceCaches() { return chanceCaches; }
    public Map<RecipeCapability<?>, List<Content>> resolvedOutputs() { return resolvedOutputs; }
    public Map<Channel, Map<RecipeCapability<?>, List<Content>>> remainders() { return remainders; }
    public BatchExecutionStage stage() { return stage; }
    public boolean wirelessPaid() { return wirelessPaid; }
    public void setWirelessPaid(boolean paid) { wirelessPaid = paid; }

    public void setStage(BatchExecutionStage stage) { this.stage = Objects.requireNonNull(stage); }
    public void setResolvedOutputs(Map<RecipeCapability<?>, List<Content>> outputs) { resolvedOutputs = outputs; }

    Tag preserved() { return preserved == null ? null : preserved.copy(); }
    void preserve(Tag encoded) { preserved = encoded.copy(); }

    public void validateJournal() {
        stage.validateOutputs(resolvedOutputs != null, remainders.containsKey(Channel.OUTPUT));
        stage.validateTickJournal(remainders.containsKey(Channel.TICK_INPUT), remainders.containsKey(Channel.TICK_OUTPUT),
                wirelessPaid, power != BatchRecipeExtensions.Power.WIRED);
        if (stage.input() == BatchExecutionStage.Input.CONSUMED && remainders.containsKey(Channel.INPUT)) {
            throw new IllegalArgumentException("Consumed inputs still have a remainder");
        }
    }
}
