package com.gtlmodular.common.machine.independent.persistence;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeSerializer;
import com.gtladd.gtladditions.api.recipe.WirelessGTRecipe;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import org.gtlcore.gtlcore.api.recipe.IGTRecipe;

import java.math.BigInteger;

import static com.gtlmodular.common.machine.independent.persistence.BatchRecipeExtensions.*;

final class BatchRecipeNbtCodec {
    CompoundTag encode(GTRecipe recipe, Power power) {
        if (recipe.getClass() != GTRecipe.class && recipe.getClass() != WirelessGTRecipe.class) {
            throw new IllegalArgumentException("Unsupported recipe subclass: " + recipe.getClass().getName());
        }
        // 上游 ingredient action 的编码端输出 JsonNull，不能用其解码成功冒充无损存档。
        if (!recipe.ingredientActions.isEmpty()) throw new IllegalArgumentException("Ingredient actions are not losslessly serializable");
        if (recipe.getId() == null || recipe.recipeType == null || recipe.duration < 1) {
            throw new IllegalArgumentException("Missing recipe identity or duration");
        }
        IGTRecipe extra = IGTRecipe.of(recipe);
        BigInteger wirelessEu = recipe instanceof WirelessGTRecipe wireless ? wireless.getWirelessEuTickInputs() : null;
        var extensions = new BatchRecipeExtensions(recipe instanceof WirelessGTRecipe ? Kind.WIRELESS : Kind.NORMAL,
                power, extra.getRealParallels(), recipe.parallels, extra.getBatchSize(), recipe.ocTier, extra.getEuTier(),
                extra.isBatchProcessed(), extra.isSubTickParallelized(), recipe.hasTick(), wirelessEu);
        if (extensions.euTier() != recipe.data.getInt("euTier")) {
            throw new IllegalArgumentException("Recipe has a stale EU tier cache");
        }
        if (wirelessEu != null) parseWireless(wirelessEu.toString());
        CompoundTag tag = new CompoundTag();
        tag.putString("kind", extensions.kind().name());
        tag.putString("power", power.name());
        tag.putString("id", recipe.getId().toString());
        tag.putString("type", recipe.recipeType.registryName.toString());
        tag.put("body", GTRecipeSerializer.CODEC.encodeStart(NbtOps.INSTANCE, recipe).getOrThrow(false, error -> {}));
        tag.putLong("realParallels", extensions.realParallels());
        tag.putInt("parallels", extensions.parallels());
        tag.putInt("batchSize", extensions.batchSize());
        tag.putInt("ocTier", extensions.ocTier());
        tag.putInt("euTier", extensions.euTier());
        tag.putBoolean("batchProcessed", extensions.batchProcessed());
        tag.putBoolean("subTickParallelized", extensions.subTickParallelized());
        tag.putBoolean("hasTick", extensions.hasTick());
        tag.putBoolean("wirelessPresent", wirelessEu != null);
        if (wirelessEu != null) tag.putString("wirelessEu", wirelessEu.toString());
        return tag;
    }

    Decoded decode(Tag tag) {
        var f = new ArchiveFields<>(NbtOps.INSTANCE, tag, "kind", "power", "id", "type", "body", "realParallels", "parallels",
                "batchSize", "ocTier", "euTier", "batchProcessed", "subTickParallelized", "hasTick", "wirelessPresent", "wirelessEu");
        boolean present = f.bool("wirelessPresent");
        if (present != f.has("wirelessEu")) throw new IllegalArgumentException("Inconsistent wireless presence");
        var extensions = new BatchRecipeExtensions(Kind.valueOf(f.string("kind")), Power.valueOf(f.string("power")),
                f.number("realParallels"), f.integer("parallels"), f.integer("batchSize"), f.integer("ocTier"), f.integer("euTier"),
                f.bool("batchProcessed"), f.bool("subTickParallelized"), f.bool("hasTick"), present ? parseWireless(f.string("wirelessEu")) : null);
        GTRecipe body = GTRecipeSerializer.CODEC.parse(NbtOps.INSTANCE, f.required("body").copy()).getOrThrow(false, error -> {});
        if (!body.recipeType.registryName.equals(exactId(f.string("type")))) throw new IllegalArgumentException("Recipe type mismatch");
        body.setId(exactId(f.string("id")));
        GTRecipe recipe = extensions.kind() == Kind.WIRELESS ? new WirelessGTRecipe(body, extensions.wirelessEu()) : body;
        IGTRecipe extra = IGTRecipe.of(recipe);
        extra.setRealParallels(extensions.realParallels());
        // setRealParallels 会同时覆盖 int parallels；两者都必须恢复各自的保存值。
        recipe.parallels = extensions.parallels();
        recipe.ocTier = extensions.ocTier();
        extra.setBatchSize(extensions.batchSize());
        extra.setBatchProcessed(extensions.batchProcessed());
        extra.setSubTickParallelized(extensions.subTickParallelized());
        extra.setHasTick(extensions.hasTick());
        if (extra.getEuTier() != extensions.euTier()) throw new IllegalArgumentException("EU tier mismatch");
        if (!encode(recipe, extensions.power()).equals(tag)) throw new IllegalArgumentException("Recipe payload did not round trip");
        return new Decoded(recipe, extensions.power());
    }

    private ResourceLocation exactId(String value) {
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id == null || !id.toString().equals(value)) throw new IllegalArgumentException("Invalid exact recipe ID");
        return id;
    }

    record Decoded(GTRecipe recipe, Power power) {}
}
