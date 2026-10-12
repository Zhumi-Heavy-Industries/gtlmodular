package com.gtlmodular.common.machine.independent.persistence;

import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.Map;

final class BatchChanceCacheNbtCodec {
    CompoundTag encode(Map<RecipeCapability<?>, Object2IntMap<?>> caches) {
        CompoundTag tag = new CompoundTag();
        caches.forEach((cap, cache) -> {
            if (GTRegistries.RECIPE_CAPABILITIES.get(cap.name) != cap) throw new IllegalArgumentException("Unregistered chance capability");
            CompoundTag values = new CompoundTag();
            values.putInt("default", cache.defaultReturnValue());
            var entries = new ArrayList<CompoundTag>();
            for (var entry : cache.object2IntEntrySet()) {
                CompoundTag row = new CompoundTag();
                row.put("entry", cap.serializer.toNbtGeneric(cap.of(entry.getKey())).copy());
                row.putInt("chance", entry.getIntValue());
                entries.add(row);
            }
            // hash/cache 的遍历顺序不是存档语义，固定顺序才能检查往返而不受重建顺序影响。
            entries.sort(Comparator.comparing(CompoundTag::toString));
            ListTag list = new ListTag();
            list.addAll(entries);
            values.put("entries", list);
            tag.put(cap.name, values);
        });
        return tag;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    Map<RecipeCapability<?>, Object2IntMap<?>> decode(Tag raw) {
        if (!(raw instanceof CompoundTag tag)) throw new IllegalArgumentException("Invalid chance caches");
        Map<RecipeCapability<?>, Object2IntMap<?>> result = new IdentityHashMap<>();
        for (String key : tag.getAllKeys()) {
            RecipeCapability<?> cap = GTRegistries.RECIPE_CAPABILITIES.get(key);
            if (cap == null) throw new IllegalArgumentException("Unknown chance capability: " + key);
            var fields = new ArchiveFields<>(NbtOps.INSTANCE, tag.get(key), "default", "entries");
            Object2IntMap cache = cap.makeChanceCache();
            cache.defaultReturnValue(fields.integer("default"));
            for (Tag entry : fields.list("entries")) {
                var row = new ArchiveFields<>(NbtOps.INSTANCE, entry, "entry", "chance");
                Object content = cap.serializer.fromNbt(row.required("entry").copy());
                if (content == null || cache.containsKey(content)) throw new IllegalArgumentException("Invalid or duplicate chance cache entry");
                cache.put(content, row.integer("chance"));
            }
            result.put(cap, cache);
        }
        if (!encode(result).equals(raw)) throw new IllegalArgumentException("Lossy chance cache");
        return result;
    }
}
