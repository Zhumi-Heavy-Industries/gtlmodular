package com.gtlmodular.common.machine.independent.adapter;

import com.gtlmodular.common.machine.independent.recipe.SingleRecipeCandidate;

import java.util.List;

public interface IndependentRecipeAdapter {
    int threads();
    long totalBudget();
    boolean allowsNewBatches();
    boolean allowsRunningBatches();

    // 这里只预分配预算，不承诺输入仍然存在；会话必须在每批真正扣料前重新校验并预留。
    default List<SingleRecipeCandidate> candidates(int availableSlots, long availableBudget) {
        return candidates(availableSlots, availableBudget, Long.MAX_VALUE);
    }

    List<SingleRecipeCandidate> candidates(int availableSlots, long availableBudget, long availablePower);
}
