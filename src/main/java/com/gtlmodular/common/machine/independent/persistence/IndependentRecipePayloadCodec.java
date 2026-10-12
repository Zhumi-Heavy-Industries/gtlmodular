package com.gtlmodular.common.machine.independent.persistence;

import com.gtlmodular.common.machine.independent.core.IndependentSessionSnapshot.Batch;
import com.gtlmodular.common.machine.independent.persistence.IndependentRecipePayload.Channel;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class IndependentRecipePayloadCodec implements BatchPayloadCodec<IndependentRecipePayload, Tag> {
    private final BatchRecipeNbtCodec recipes = new BatchRecipeNbtCodec();
    private final BatchChanceCacheNbtCodec caches = new BatchChanceCacheNbtCodec();

    public GTRecipe withWirelessEnergy(IndependentRecipePayload payload, java.math.BigInteger energy) {
        var tag = recipes.encode(payload.recipe(), payload.power());
        tag.putString("wirelessEu", energy.toString());
        return recipes.decode(tag).recipe();
    }

    @Override
    public Tag encode(IndependentRecipePayload payload) {
        payload.validateJournal();
        CompoundTag tag = new CompoundTag();
        tag.put("recipe", recipes.encode(payload.recipe(), payload.power()));
        tag.put("chanceCaches", caches.encode(payload.chanceCaches()));
        tag.putString("inputStage", payload.stage().input().name());
        tag.putString("outputStage", payload.stage().output().name());
        tag.putString("afterWorking", payload.stage().afterWorking().name());
        if (payload.wirelessPaid()) tag.putBoolean("wirelessPaid", true);
        if (payload.resolvedOutputs() != null) tag.put("resolvedOutputs", encodeContents(payload.resolvedOutputs()));
        CompoundTag remainders = new CompoundTag();
        payload.remainders().forEach((channel, contents) -> remainders.put(channel.name(), encodeContents(contents)));
        tag.put("remainders", remainders);
        payload.preserve(tag);
        return tag.copy();
    }

    @Override
    public Optional<Tag> preserved(IndependentRecipePayload payload) {
        return Optional.ofNullable(payload.preserved());
    }

    @Override
    public IndependentRecipePayload decode(Tag encoded) {
        var f = new ArchiveFields<>(NbtOps.INSTANCE, encoded, "recipe", "chanceCaches", "inputStage", "outputStage",
                "afterWorking", "resolvedOutputs", "remainders", "wirelessPaid");
        var recipe = recipes.decode(f.required("recipe"));
        var stage = new BatchExecutionStage(BatchExecutionStage.Input.valueOf(f.string("inputStage")),
                BatchExecutionStage.Output.valueOf(f.string("outputStage")), BatchExecutionStage.Hook.valueOf(f.string("afterWorking")));
        Map<Channel, Map<RecipeCapability<?>, List<Content>>> remainders = new EnumMap<>(Channel.class);
        if (!(f.required("remainders") instanceof CompoundTag remainingTag)) throw new IllegalArgumentException("Invalid remainders");
        for (String key : remainingTag.getAllKeys()) remainders.put(Channel.valueOf(key), decodeContents(remainingTag.get(key)));
        var payload = new IndependentRecipePayload(recipe.recipe(), recipe.power(), caches.decode(f.required("chanceCaches")),
                f.has("resolvedOutputs") ? decodeContents(f.required("resolvedOutputs")) : null, remainders, stage);
        if (f.has("wirelessPaid")) payload.setWirelessPaid(f.bool("wirelessPaid"));
        if (!encode(payload).equals(encoded)) throw new IllegalArgumentException("Lossy recipe execution state");
        return payload;
    }

    @Override
    public void validate(Batch<IndependentRecipePayload> batch) {
        validateDuration(batch);
        batch.payload().stage().validateOwned(batch.phase(), batch.failure());
        if (batch.phase() != com.gtlmodular.common.machine.independent.core.IndependentSessionSnapshot.Phase.RUNNING &&
                batch.payload().remainders().containsKey(Channel.TICK_INPUT)) {
            throw new IllegalArgumentException("Tick IO on a non-running batch");
        }
    }

    @Override
    public void validateCandidate(Batch<IndependentRecipePayload> batch) {
        validateDuration(batch);
        if (batch.payload().stage().input() != BatchExecutionStage.Input.PREPARED ||
                !batch.payload().remainders().isEmpty() || batch.payload().resolvedOutputs() != null) {
            throw new IllegalArgumentException("Candidate already performed IO");
        }
    }

    private void validateDuration(Batch<IndependentRecipePayload> batch) {
        batch.payload().validateJournal();
        if (batch.duration() != batch.payload().recipe().duration) throw new IllegalArgumentException("Batch duration mismatch");
    }

    private Tag encodeContents(Map<RecipeCapability<?>, List<Content>> contents) {
        return RecipeCapability.CODEC.encodeStart(NbtOps.INSTANCE, contents).getOrThrow(false, error -> {}).copy();
    }

    private Map<RecipeCapability<?>, List<Content>> decodeContents(Tag encoded) {
        var contents = RecipeCapability.CODEC.parse(NbtOps.INSTANCE, encoded.copy()).getOrThrow(false, error -> {});
        if (!encodeContents(contents).equals(encoded)) throw new IllegalArgumentException("Lossy IO remainder");
        return contents;
    }
}
