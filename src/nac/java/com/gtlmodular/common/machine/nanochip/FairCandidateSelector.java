package com.gtlmodular.common.machine.nanochip;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/** 按稳定顺序轮转候选，避免常备配方长期占满有限的线程名额。 */
public final class FairCandidateSelector {

    private FairCandidateSelector() {
    }

    public static <T> Set<T> select(java.util.Collection<T> candidates,
                                    int requested,
                                    String lastSelected,
                                    Function<T, ? extends CharSequence> id) {
        if (candidates == null || candidates.isEmpty()) {
            return Set.of();
        }
        Function<T, String> keyOf = candidate -> {
            CharSequence key = candidate == null ? null : id.apply(candidate);
            return key == null ? "" : key.toString();
        };
        List<T> sorted = new ArrayList<>(candidates);
        // 候选先排序，轮转游标才不受集合遍历顺序影响。
        sorted.sort(Comparator.comparing(keyOf));
        int start = 0;
        if (lastSelected != null) {
            while (start < sorted.size() && keyOf.apply(sorted.get(start)).compareTo(lastSelected) <= 0) {
                start++;
            }
            if (start == sorted.size()) {
                start = 0;
            }
        }
        int count = Math.min(sorted.size(), Math.max(1, requested));
        Set<T> selected = new LinkedHashSet<>(Math.max(4, count * 2));
        for (int offset = 0; offset < count; offset++) {
            selected.add(sorted.get((start + offset) % sorted.size()));
        }
        return Set.copyOf(selected);
    }
}
