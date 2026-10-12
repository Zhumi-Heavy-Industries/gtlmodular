package com.gtlmodular.common.machine.nanochip;

import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 两种配方逻辑共用候选搜索与轮转规则，避免跨类型搜索口径分歧。 */
public final class NanochipCandidateSearch {

    private NanochipCandidateSearch() {
    }

    /** 候选校验会多次执行，只能试算，实际扣料留给开批阶段。 */
    @FunctionalInterface
    public interface Eligibility {

        boolean isUsable(GTRecipe candidate);
    }

    public static Set<GTRecipe> search(IRecipeLogicMachine machine,
                                       GTRecipeType[] types,
                                       GTRecipeType single,
                                       Eligibility eligibility,
                                       int threads,
                                       String[] cursor) {
        return searchIsolating(machine, types, single, eligibility, threads, cursor, null, null);
    }

    /** 传入 failures 时隔离各类型的异常；不传时保留异常向调用方传播的契约。 */
    public static Set<GTRecipe> searchIsolating(IRecipeLogicMachine machine,
                                                GTRecipeType[] types,
                                                GTRecipeType single,
                                                Eligibility eligibility,
                                                int threads,
                                                String[] cursor,
                                                List<String> failures,
                                                int[] stats) {
        if (machine == null || eligibility == null) {
            return Set.of();
        }
        Set<GTRecipe> candidates = new LinkedHashSet<>();
        int raw = 0;
        if (types != null && types.length > 0) {
            for (GTRecipeType type : types) {
                if (type == null) {
                    continue;
                }
                raw += collectFromTypeOrFail(machine, type, eligibility, candidates, failures);
            }
        } else if (single != null) {
            raw += collectFromTypeOrFail(machine, single, eligibility, candidates, failures);
        }
        if (stats != null && stats.length >= 2) {
            stats[0] = raw;
            stats[1] = candidates.size();
        }
        if (candidates.isEmpty()) {
            return Set.of();
        }
        String last = (cursor == null || cursor.length == 0) ? null : cursor[0];
        Set<GTRecipe> selected = FairCandidateSelector.select(candidates, threads, last,
                recipe -> recipe.getId() == null ? null : recipe.getId().toString());
        if (selected.isEmpty() || cursor == null || cursor.length == 0) {
            return selected;
        }
        // 从本次最后一条继续轮转，避免常备配方长期独占候选名额。
        for (GTRecipe recipe : selected) {
            if (recipe.getId() != null) {
                cursor[0] = recipe.getId().toString();
            }
        }
        return selected;
    }

    private static int collectFromTypeOrFail(IRecipeLogicMachine machine, GTRecipeType type,
                                             Eligibility eligibility, Set<GTRecipe> candidates,
                                             List<String> failures) {
        int raw = 0;
        try {
            Iterator<GTRecipe> iterator = type.getLookup().getRecipeIterator(machine, recipe -> true);
            while (iterator.hasNext()) {
                GTRecipe candidate = iterator.next();
                raw++;
                if (candidate != null && eligibility.isUsable(candidate)) {
                    candidates.add(candidate);
                }
            }
        } catch (RuntimeException e) {
            if (failures == null) {
                throw e;
            }
            failures.add((type.registryName == null ? "?" : type.registryName.toString())
                    + " → " + e.getClass().getSimpleName()
                    + (e.getMessage() == null ? "" : ": " + e.getMessage()));
        }
        return raw;
    }

    public static String describeTypes(GTRecipeType[] types) {
        if (types == null || types.length == 0) {
            return "（无）";
        }
        StringBuilder sb = new StringBuilder();
        for (GTRecipeType type : types) {
            if (sb.length() > 0) {
                sb.append(" + ");
            }
            sb.append(type == null || type.registryName == null ? "?" : type.registryName.toString());
        }
        return sb.toString();
    }
}
