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

    @Test
    void settledRecipeComesFirstSoItReclaimsItsOwnThread() {
        List<ResourceLocation> recipes = List.of(
                new ResourceLocation("gtlmodular", "space_ore_1"),
                new ResourceLocation("gtlmodular", "space_ore_2"),
                new ResourceLocation("gtlmodular", "space_ore_3"),
                new ResourceLocation("gtlmodular", "space_ore_4"));
        RecipeFairness fairness = new RecipeFairness();
        // 四种处理同族、按老化顺序本来会先跑第 1 种。
        var settled = Map.of(recipes.get(3), 500L);

        var order = fairness.orderIndices(recipes, id -> id, id -> "同一种矿", 0,
                Map.of(recipes.get(0), 1), settled);

        assertEquals(3, order.get(0), "刚结算的第 4 种要排最前，回到自己那条线程");
    }

    @Test
    void runningRecipeGoesLastSoItCannotTakeASecondThread() {
        List<ResourceLocation> recipes = List.of(
                new ResourceLocation("gtlmodular", "space_ore_1"),
                new ResourceLocation("gtlmodular", "space_ore_2"));
        RecipeFairness fairness = new RecipeFairness();

        var order = fairness.orderIndices(recipes, id -> id, id -> "同一种矿", 0,
                Map.of(recipes.get(0), 1), Map.of());

        assertEquals(1, order.get(0), "正在跑的第 1 种要排到最后，先让没在跑的第 2 种占线程");
        assertEquals(0, order.get(order.size() - 1));
    }

    @Test
    void pickPreferredIndexSkipsRecipesThatAreAlreadyRunning() {
        ResourceLocation running = new ResourceLocation("gtlmodular", "space_ore_1");
        ResourceLocation idle = new ResourceLocation("gtlmodular", "space_ore_2");

        int index = RecipeFairness.pickPreferredIndex(List.of(running, idle), id -> id.equals(running));

        assertEquals(1, index);
    }

    @Test
    void pickPreferredIndexFallsBackToDuplicateWhenEverythingRuns() {
        ResourceLocation a = new ResourceLocation("gtlmodular", "space_ore_1");
        ResourceLocation b = new ResourceLocation("gtlmodular", "space_ore_2");

        int index = RecipeFairness.pickPreferredIndex(List.of(a, b), id -> true);

        assertEquals(0, index, "全都占满时仍要能补位，否则空槽永远补不上");
        assertEquals(-1, RecipeFairness.pickPreferredIndex(List.of(), id -> false));
    }
}
