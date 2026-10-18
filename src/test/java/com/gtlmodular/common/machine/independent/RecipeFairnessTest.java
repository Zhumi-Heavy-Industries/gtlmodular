package com.gtlmodular.common.machine.independent;

import com.gtlmodular.common.machine.independent.adapter.RecipeFairness;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RecipeFairnessTest {

    @Test
    void everyRecipeIsServedWithinOneRotationInsteadOfHashOrderStarvation() {
        List<ResourceLocation> recipes = new ArrayList<>();
        for (int i = 0; i < 300; i++) recipes.add(new ResourceLocation("gtlmodular", "ore/" + i));
        RecipeFairness fairness = new RecipeFairness();
        Set<ResourceLocation> served = new HashSet<>();
        int slots = 4;

        for (long tick = 1; tick <= 75; tick++) {
            var order = fairness.orderIndices(recipes, id -> id, 0, Map.of());
            for (int n = 0; n < Math.min(slots, order.size()); n++) {
                ResourceLocation id = recipes.get(order.get(n));
                fairness.admitted(id, tick);
                served.add(id);
            }
        }

        assertEquals(300, served.size(), "每个配方都必须在一次轮转内被服务");
    }

    @Test
    void activeRecipeCapFiltersTheCandidateBeforeItCanTakeAnotherSlot() {
        List<ResourceLocation> recipes = List.of(
                new ResourceLocation("gtlmodular", "ore/iron"),
                new ResourceLocation("gtlmodular", "ore/copper"));
        RecipeFairness fairness = new RecipeFairness();

        var order = fairness.orderIndices(recipes, id -> id, 1,
                Map.of(new ResourceLocation("gtlmodular", "ore/iron"), 1));

        assertEquals(List.of(1), order);
    }

    @Test
    void neverServedRecipesComeBeforeRecentlyServedOnes() {
        List<ResourceLocation> recipes = List.of(
                new ResourceLocation("gtlmodular", "ore/a"),
                new ResourceLocation("gtlmodular", "ore/b"),
                new ResourceLocation("gtlmodular", "ore/c"));
        RecipeFairness fairness = new RecipeFairness();
        fairness.admitted(recipes.get(0), 900L);
        fairness.admitted(recipes.get(1), 100L);

        var order = fairness.orderIndices(recipes, id -> id, 0, Map.of());

        assertEquals(2, order.get(0));
    }
}
