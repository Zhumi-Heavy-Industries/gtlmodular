package com.gtlmodular.gametest.recipe;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;
import com.gregtechceu.gtceu.common.item.IntCircuitBehaviour;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongMaps;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.minecraft.world.item.crafting.Ingredient;
import org.gtlcore.gtlcore.integration.ae2.AEUtils;
import org.gtlcore.gtlcore.integration.ae2.handler.SlotCacheManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 使用上游实际候选和数量转换，避免把标签、NBT 或长数量按字符串猜成同一种原料。 */
public final class RecipeConflictInputs {
    private final Object2LongMap<Ingredient> items;
    private final Object2LongMap<FluidIngredient> fluids;
    private final List<Choice> choices = new ArrayList<>();
    private int circuit = -1;
    private final Supply maximum = new Supply();
    private final Supply unlimited;

    public RecipeConflictInputs(GTRecipe recipe) {
        items = AEUtils.ingredientsMapWithOutCircuit(recipe.getInputContents(ItemRecipeCapability.CAP).stream()
                .map(content -> (Ingredient) content.content).toList(), value -> circuit = value);
        fluids = AEUtils.fluidIngredientsMap(recipe.getInputContents(FluidRecipeCapability.CAP).stream()
                .map(content -> (FluidIngredient) content.content).toList());
        for (var entry : items.object2LongEntrySet()) {
            add(Arrays.stream(entry.getKey().getItems()).filter(stack -> !stack.isEmpty())
                    .map(AEItemKey::of).map(key -> (AEKey) key).distinct().toList(), entry.getLongValue());
        }
        for (var entry : fluids.object2LongEntrySet()) {
            // ME 的流体缓存实际只保留 Fluid，不保留 FluidIngredient 的 NBT。
            add(Arrays.stream(entry.getKey().getStacks()).filter(stack -> !stack.isEmpty())
                    .map(stack -> (AEKey) AEFluidKey.of(stack.getFluid())).distinct().toList(), entry.getLongValue());
        }
        choices.sort(java.util.Comparator.comparing(choice -> choice.keys.toString()));
        unlimited = maximum.unlimited();
    }

    private void add(List<AEKey> keys, long amount) {
        if (amount <= 0) return;
        choices.add(new Choice(keys, amount));
        for (AEKey key : keys) maximum.add(key, amount);
    }

    public int circuit() { return circuit; }

    public Set<AEKey> keys() {
        Set<AEKey> result = new java.util.HashSet<>(maximum.items.keySet());
        result.addAll(maximum.fluids.keySet());
        return result;
    }

    public List<List<AEKey>> alternatives() { return choices.stream().map(Choice::keys).toList(); }

    public boolean accepts(Supply supply, int suppliedCircuit) {
        if (circuit > 0 && circuit != suppliedCircuit) return false;
        SlotCacheManager cache = new SlotCacheManager();
        for (var entry : items.object2LongEntrySet()) {
            if (cache.getBestItemMatchSimulate(entry.getKey(), supply.items,
                    Object2LongMaps.emptyMap(), entry.getLongValue()) == null) return false;
        }
        for (var entry : fluids.object2LongEntrySet()) {
            if (cache.getBestFluidMatchSimulate(entry.getKey(), supply.fluids,
                    Object2LongMaps.emptyMap(), entry.getLongValue()) == null) return false;
        }
        return true;
    }

    public Search suppliedBy(RecipeConflictInputs target) {
        if (choices.isEmpty() || target.choices.isEmpty() || !accepts(target.unlimited, target.circuit)) {
            return new Search(null, false);
        }
        SearchState search = new SearchState(target);
        search.visit(0, new Supply());
        return new Search(search.witness, search.exhausted);
    }

    private final class SearchState {
        private final RecipeConflictInputs target;
        private int visited;
        private boolean exhausted;
        private Witness witness;

        private SearchState(RecipeConflictInputs target) { this.target = target; }

        private void visit(int index, Supply supply) {
            if (witness != null || exhausted) return;
            if (++visited > 4096) {
                exhausted = true;
                return;
            }
            if (index == target.choices.size()) {
                long multiplier = multiplier(supply);
                if (multiplier == 0) return;
                Supply scaled = supply.scaled(multiplier);
                if (accepts(scaled, target.circuit) && target.accepts(scaled, target.circuit)) {
                    witness = new Witness(scaled, multiplier, target.circuit);
                }
                return;
            }
            Choice choice = target.choices.get(index);
            for (AEKey key : choice.keys) {
                Supply next = supply.copy();
                next.add(key, choice.amount);
                visit(index + 1, next);
                if (witness != null || exhausted) return;
            }
        }
    }

    private long multiplier(Supply supply) {
        long multiplier = 1;
        for (var entry : items.object2LongEntrySet()) {
            long available = Arrays.stream(entry.getKey().getItems()).filter(stack -> !stack.isEmpty())
                    .map(AEItemKey::of).mapToLong(supply.items::getLong).max().orElse(0);
            if (available == 0) return 0;
            multiplier = Math.max(multiplier, 1 + (entry.getLongValue() - 1) / available);
        }
        for (var entry : fluids.object2LongEntrySet()) {
            long available = Arrays.stream(entry.getKey().getStacks()).filter(stack -> !stack.isEmpty())
                    .map(stack -> AEFluidKey.of(stack.getFluid())).mapToLong(supply.fluids::getLong).max().orElse(0);
            if (available == 0) return 0;
            multiplier = Math.max(multiplier, 1 + (entry.getLongValue() - 1) / available);
        }
        return multiplier;
    }

    public record Search(Witness witness, boolean exhausted) {}
    public record Witness(Supply supply, long targetBatches, int circuit) {}
    private record Choice(List<AEKey> keys, long amount) {}

    public static final class Supply {
        private final Object2LongOpenHashMap<AEItemKey> items = new Object2LongOpenHashMap<>();
        private final Object2LongOpenHashMap<AEFluidKey> fluids = new Object2LongOpenHashMap<>();

        private void add(AEKey key, long amount) {
            if (key instanceof AEItemKey item) items.put(item, Math.addExact(items.getLong(item), amount));
            if (key instanceof AEFluidKey fluid) fluids.put(fluid, Math.addExact(fluids.getLong(fluid), amount));
        }

        private Supply copy() { return scaled(1); }

        private Supply scaled(long multiplier) {
            Supply result = new Supply();
            items.forEach((key, amount) -> result.add(key, Math.multiplyExact(amount, multiplier)));
            fluids.forEach((key, amount) -> result.add(key, Math.multiplyExact(amount, multiplier)));
            return result;
        }

        private Supply unlimited() {
            Supply result = new Supply();
            items.keySet().forEach(key -> result.add(key, Long.MAX_VALUE));
            fluids.keySet().forEach(key -> result.add(key, Long.MAX_VALUE));
            return result;
        }

        public List<GenericStack> patternInputs(int circuit) {
            List<GenericStack> result = new ArrayList<>();
            items.forEach((key, amount) -> result.add(new GenericStack(key, amount)));
            fluids.forEach((key, amount) -> result.add(new GenericStack(key, amount)));
            if (circuit >= 0) result.add(new GenericStack(AEItemKey.of(IntCircuitBehaviour.stack(circuit)), 1));
            return result;
        }

        public Map<String, Long> describe() {
            Map<String, Long> result = new java.util.TreeMap<>();
            items.forEach((key, amount) -> result.put("item:" + key.getId() + ":" + key.getTag(), amount));
            fluids.forEach((key, amount) -> result.put("fluid:" + key.getId(), amount));
            return new LinkedHashMap<>(result);
        }
    }
}
