package com.gtlmodular.common.machine.independent.recipe;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gtlmodular.common.machine.independent.persistence.BatchRecipeExtensions.Power;

import java.util.Objects;

// 原配方类型控制概率语义，执行模板的类型则可能来自上游无线构建器；不能提前合并两者。
public record SingleRecipeCandidate(GTRecipe ingredients, GTRecipe execution, ParallelAllocation allocation,
                                    Power power, double energyReduction, double durationReduction,
                                    RecipeCandidateGroup.Energy energy) {
    public SingleRecipeCandidate(GTRecipe ingredients, GTRecipe execution, ParallelAllocation allocation,
                                 Power power, double energyReduction, double durationReduction) {
        this(ingredients, execution, allocation, power, energyReduction, durationReduction, null);
    }

    public SingleRecipeCandidate {
        Objects.requireNonNull(ingredients);
        Objects.requireNonNull(execution);
        Objects.requireNonNull(allocation);
        Objects.requireNonNull(power);
        if (ingredients == execution || allocation.parallels() < 1 || execution.duration < 1) {
            throw new IllegalArgumentException("Invalid independent candidate");
        }
    }
}
