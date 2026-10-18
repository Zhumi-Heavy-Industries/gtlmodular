package com.gtlmodular.common.machine.independent.adapter;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 候选配方的老化顺序。按“最久没被开过”排序，而不是按 lookup 的哈希顺序从队首截断；
 * 这样几千种矿物不会因为前几种一直有料就永远轮不到。并发上限由调用方按每配方活跃批次数过滤。
 */
public final class RecipeFairness {
    private final Map<ResourceLocation, Long> lastAdmitted = new HashMap<>();

    public <T> List<Integer> orderIndices(List<T> candidates, Function<T, ResourceLocation> id,
                                          int maxPerRecipe, Map<ResourceLocation, Integer> active) {
        List<Integer> indices = new ArrayList<>(candidates.size());
        for (int i = 0; i < candidates.size(); i++) {
            ResourceLocation key = id.apply(candidates.get(i));
            if (maxPerRecipe > 0 && active.getOrDefault(key, 0) >= maxPerRecipe) continue;
            indices.add(i);
        }
        indices.sort(Comparator
                .comparingLong((Integer i) -> lastAdmitted.getOrDefault(id.apply(candidates.get(i)), Long.MIN_VALUE))
                .thenComparing(i -> id.apply(candidates.get(i)).toString()));
        return indices;
    }

    public void admitted(ResourceLocation id, long now) {
        lastAdmitted.put(id, now);
    }

    public void clear() {
        lastAdmitted.clear();
    }
}
