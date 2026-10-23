package com.gtlmodular.common.machine.independent.adapter;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gtlmodular.common.machine.independent.recipe.SingleRecipeCandidate;

import java.util.List;

public interface IndependentRecipeAdapter {
    int threads();
    long totalBudget();

    /** 会话并行总量上限。默认沿用 totalBudget；独立线程语义下每线程自带并行，不共享池。 */
    default long sessionBudget() { return totalBudget(); }
    boolean allowsNewBatches();
    boolean allowsRunningBatches();

    /** 会话真正接下一条候选后回调，用于公平游标/老化；预分配阶段不能算作已服务。 */
    default void admitted(GTRecipe recipe) {}

    /** 机器自身 IO 或配置改变后丢弃候选缓存；外部输入变化仍由短 TTL 兜底。 */
    default void invalidateCandidates() {}

    // 这里只预分配预算，不承诺输入仍然存在；会话必须在每批真正扣料前重新校验并预留。
    default List<SingleRecipeCandidate> candidates(int availableSlots, long availableBudget) {
        return candidates(availableSlots, availableBudget, Long.MAX_VALUE);
    }

    List<SingleRecipeCandidate> candidates(int availableSlots, long availableBudget, long availablePower);
}
