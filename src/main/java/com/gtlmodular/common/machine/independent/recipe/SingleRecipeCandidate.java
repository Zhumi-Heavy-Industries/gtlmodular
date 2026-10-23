package com.gtlmodular.common.machine.independent.recipe;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gtlmodular.common.machine.independent.persistence.BatchRecipeExtensions.Power;

import java.util.Objects;

// 原配方类型控制概率语义，执行模板的类型则可能来自上游无线构建器；不能提前合并两者。
// runs 是长批次的份数（1 = 原行为）：开批扣 p×runs、结算出 p×runs，每 tick 仍只付 p 的电。
public record SingleRecipeCandidate(GTRecipe ingredients, GTRecipe execution, ParallelAllocation allocation,
                                    Power power, double energyReduction, double durationReduction,
                                    RecipeCandidateGroup.Energy energy, int runs) {
    public SingleRecipeCandidate(GTRecipe ingredients, GTRecipe execution, ParallelAllocation allocation,
                                 Power power, double energyReduction, double durationReduction) {
        this(ingredients, execution, allocation, power, energyReduction, durationReduction, null, 1);
    }

    public SingleRecipeCandidate(GTRecipe ingredients, GTRecipe execution, ParallelAllocation allocation,
                                 Power power, double energyReduction, double durationReduction,
                                 RecipeCandidateGroup.Energy energy) {
        this(ingredients, execution, allocation, power, energyReduction, durationReduction, energy, 1);
    }

    public SingleRecipeCandidate {
        Objects.requireNonNull(ingredients);
        Objects.requireNonNull(execution);
        Objects.requireNonNull(allocation);
        Objects.requireNonNull(power);
        if (ingredients == execution || allocation.parallels() < 1 || execution.duration < 1 || runs < 1) {
            throw new IllegalArgumentException("Invalid independent candidate");
        }
    }
}
