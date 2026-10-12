package com.gtlmodular.common.machine.independent.runtime;

import com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gtlmodular.common.machine.independent.persistence.IndependentRecipePayload;
import com.gtlmodular.common.machine.independent.persistence.IndependentRecipePayloadCodec;
import com.gtlmodular.common.machine.independent.recipe.SharedBatchEnergy;
import com.gtlmodular.common.machine.independent.recipe.SingleBatchEnergy;
import com.gtlmodular.common.machine.independent.recipe.SingleRecipeCandidate;
import org.gtlcore.gtlcore.api.recipe.RecipeExtensionCopier;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ObjIntConsumer;

final class AdmissionPower {
    private final List<Admitted> admitted = new ArrayList<>();

    void clear() { admitted.clear(); }

    void add(IndependentRecipePayload payload, SingleRecipeCandidate source) {
        if (source.energy() != null) admitted.add(new Admitted(payload, source));
    }

    void finish(ObjIntConsumer<IndependentRecipePayload> updateDuration) {
        if (admitted.isEmpty()) return;
        var energy = admitted.get(0).source().energy();
        List<GTRecipe> replacements = new ArrayList<>();
        if (energy.wireless() == null) {
            var shares = SharedBatchEnergy.normal(admitted.stream().map(entry -> entry.source().energy().normal()).toList(),
                    energy.powerLimit(), energy.minimumDuration());
            for (int i = 0; i < admitted.size(); i++) {
                var recipe = admitted.get(i).payload().recipe().copy();
                var original = admitted.get(i).payload().recipe();
                RecipeExtensionCopier.copy(original, recipe);
                recipe.parallels = original.parallels;
                recipe.ocTier = original.ocTier;
                recipe.duration = shares.get(i).duration();
                recipe.tickInputs.get(EURecipeCapability.CAP).get(0).content = shares.get(i).eut();
                replacements.add(recipe);
            }
        } else {
            var energies = admitted.stream().map(entry -> entry.source().energy().wireless()).toList();
            var total = energies.stream().reduce(BigInteger.ZERO, BigInteger::add);
            var shares = SharedBatchEnergy.wireless(energies, SingleBatchEnergy.wirelessTick(total, energy.minimumDuration()));
            for (int i = 0; i < admitted.size(); i++) {
                replacements.add(new IndependentRecipePayloadCodec().withWirelessEnergy(admitted.get(i).payload(), shares.get(i)));
            }
        }
        // 扣料失败的候选不能拖长已接纳批次；所有新批次尚未进行 tick IO，此时才确定最终合批时钟。
        for (int i = 0; i < admitted.size(); i++) {
            var payload = admitted.get(i).payload();
            updateDuration.accept(payload, replacements.get(i).duration);
            payload.replaceUnstartedRecipe(replacements.get(i));
        }
        admitted.clear();
    }

    private record Admitted(IndependentRecipePayload payload, SingleRecipeCandidate source) {}
}
