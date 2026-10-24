package com.gtlmodular.common.machine.independent.adapter;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * 候选配方的老化顺序。按“最久没被开过”排序，而不是按 lookup 的哈希顺序从队首截断；
 * 这样几千种矿物不会因为前几种一直有料就永远轮不到。并发上限由调用方按每配方活跃批次数过滤。
 */
public final class RecipeFairness {
    private final Map<ResourceLocation, Long> lastAdmitted = new HashMap<>();

    public <T> List<Integer> orderIndices(List<T> candidates, Function<T, ResourceLocation> id,
                                          int maxPerRecipe, Map<ResourceLocation, Integer> active) {
        return orderIndices(candidates, id, item -> null, maxPerRecipe, active, Map.of());
    }

    /**
     * 按「族」轮转的排序（族 = 配方的主原料，比如不同种类的世界碎片）。
     * <p>
     * 先在该族内部按「最久没开过」排序，再在族之间轮转取第一条、第二条……
     * 这样 16 种碎片都会在一轮里各拿到线程，而不是先把 {@code _1_*} 那一段全跑完才轮到别族。
     * family 返回 null 时退化为原来的全局老化排序。
     */
    public <T> List<Integer> orderIndices(List<T> candidates, Function<T, ResourceLocation> id,
                                          Function<T, String> family,
                                          int maxPerRecipe, Map<ResourceLocation, Integer> active) {
        return orderIndices(candidates, id, family, maxPerRecipe, active, Map.of());
    }

    /**
     * 带「线程亲和」的排序：刚结算的配方排最前（让它回到自己那条线程），
     * 正在跑的排最后（只有别的都没料时才允许同配方再占一条）。
     * 同一种矿的多种电路处理因此各占一条线程，而不是每轮换一遍。
     */
    public <T> List<Integer> orderIndices(List<T> candidates, Function<T, ResourceLocation> id,
                                          Function<T, String> family,
                                          int maxPerRecipe, Map<ResourceLocation, Integer> active,
                                          Map<ResourceLocation, Long> settledAt) {
        List<Integer> eligible = new ArrayList<>(candidates.size());
        for (int i = 0; i < candidates.size(); i++) {
            ResourceLocation key = id.apply(candidates.get(i));
            if (maxPerRecipe > 0 && active.getOrDefault(key, 0) >= maxPerRecipe) continue;
            eligible.add(i);
        }
        // 没有分族信息就保持老行为（全局老化 + id 兜底）。
        Map<String, List<Integer>> groups = new java.util.LinkedHashMap<>();
        boolean grouped = false;
        for (int i : eligible) {
            String key = family.apply(candidates.get(i));
            if (key == null) {
                groups.clear();
                break;
            }
            if (!groups.isEmpty()) grouped = true;
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(i);
        }
        List<Integer> base;
        if (!grouped) {
            eligible.sort(Comparator
                    .comparingLong((Integer i) -> lastAdmitted.getOrDefault(id.apply(candidates.get(i)), Long.MIN_VALUE))
                    .thenComparing(i -> id.apply(candidates.get(i)).toString()));
            base = eligible;
        } else {
            Comparator<Integer> within = Comparator
                    .comparingLong((Integer i) -> lastAdmitted.getOrDefault(id.apply(candidates.get(i)), Long.MIN_VALUE))
                    .thenComparing(i -> id.apply(candidates.get(i)).toString());
            for (List<Integer> group : groups.values()) group.sort(within);
            // 族之间：最久没服务的族排前面。
            List<List<Integer>> ordered = new ArrayList<>(groups.values());
            ordered.sort(Comparator.comparingLong(group -> lastAdmitted.getOrDefault(
                    id.apply(candidates.get(group.get(0))), Long.MIN_VALUE)));
            List<Integer> result = new ArrayList<>(eligible.size());
            for (int round = 0; ; round++) {
                boolean any = false;
                for (List<Integer> group : ordered) {
                    if (round < group.size()) {
                        result.add(group.get(round));
                        any = true;
                    }
                }
                if (!any) break;
            }
            base = result;
        }
        if (settledAt.isEmpty() && active.isEmpty()) return base;
        // 稳定排序：同一优先级内保持「族轮转 + 老化」的相对顺序。
        List<Integer> affinity = new ArrayList<>(base);
        affinity.sort(Comparator.comparingInt((Integer i) -> priority(id.apply(candidates.get(i)), active, settledAt)));
        return affinity;
    }

    /** 0 = 刚结算（回到自己的线程）；1 = 空闲；2 = 正在跑（同配方补位，最后才用）。 */
    private static int priority(ResourceLocation id, Map<ResourceLocation, Integer> active,
                                Map<ResourceLocation, Long> settledAt) {
        if (active.getOrDefault(id, 0) > 0) return 2;
        return settledAt.containsKey(id) ? 0 : 1;
    }

    /**
     * 从候选队列里挑下一条：优先挑「没在跑」的；全都在跑时才允许同配方补位。
     * 返回要取的下标，空表返回 -1。抽成纯函数是为了能单测。
     */
    public static int pickPreferredIndex(List<ResourceLocation> ids, Predicate<ResourceLocation> running) {
        int duplicate = -1;
        for (int i = 0; i < ids.size(); i++) {
            if (!running.test(ids.get(i))) return i;
            if (duplicate < 0) duplicate = i;
        }
        return duplicate;
    }

    public void admitted(ResourceLocation id, long now) {
        lastAdmitted.put(id, now);
    }

    public void clear() {
        lastAdmitted.clear();
    }
}
