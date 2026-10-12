package com.gtlmodular.common.machine.independent.recipe;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gtladd.gtladditions.api.recipe.WirelessGTRecipe;
import com.gtlmodular.common.machine.independent.persistence.BatchRecipeExtensions.Power;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public final class RecipeCandidateGroup {
    private RecipeCandidateGroup() {}

    public record Energy(double normal, BigInteger wireless, long powerLimit, int minimumDuration) {}

    public record Input(GTRecipe ingredients, ParallelAllocation allocation,
                        double energyReduction, double durationReduction) {
        private SingleRecipeCandidate finish(GTRecipe execution, Power power, Energy energy) {
            return new SingleRecipeCandidate(ingredients, execution, allocation, power, energyReduction, durationReduction, energy);
        }
    }

    public static List<SingleRecipeCandidate> normal(List<Input> inputs, List<Double> energies,
                                                    long availablePower, int minimumDuration) {
        if (availablePower <= 0 || inputs.isEmpty()) return List.of();
        var shares = SharedBatchEnergy.normal(energies, availablePower, minimumDuration);
        List<SingleRecipeCandidate> result = new ArrayList<>();
        for (int i = 0; i < inputs.size(); i++) {
            var input = inputs.get(i);
            result.add(input.finish(RecipeCandidateSnapshots.normal(input.ingredients(), shares.get(i)), Power.WIRED,
                    new Energy(energies.get(i), null, availablePower, minimumDuration)));
        }
        return List.copyOf(result);
    }

    public static List<SingleRecipeCandidate> wireless(List<Input> inputs, List<BigInteger> energies, Power power,
                                                      Function<BigInteger, WirelessGTRecipe> buildClock) {
        if (inputs.isEmpty()) return List.of();
        var clock = buildClock.apply(energies.stream().reduce(BigInteger.ZERO, BigInteger::add));
        var shares = SharedBatchEnergy.wireless(energies, clock.getWirelessEuTickInputs());
        List<SingleRecipeCandidate> result = new ArrayList<>();
        for (int i = 0; i < inputs.size(); i++) {
            var input = inputs.get(i);
            result.add(input.finish(RecipeCandidateSnapshots.wireless(input.ingredients(), clock, shares.get(i)), power,
                    new Energy(0, energies.get(i), 0, clock.duration)));
        }
        return List.copyOf(result);
    }
}
