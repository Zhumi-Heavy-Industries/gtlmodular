package com.gtlmodular.common.machine.pattern;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gtlmodular.common.diag.SwallowedErrors;
import net.minecraft.world.level.Level;
import org.gtlcore.gtlcore.api.machine.trait.MEPart.IMEPatternTrait;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class PatternRecipeTypeSearch {

    private static final int MATCH_NONE = -2;

    private PatternRecipeTypeSearch() {
    }

    public record Result(int typeIndex, GTRecipe recipe) {
    }

    /**
     * 中转配方会被直供进槽缓存，而上游新搜索跳过已缓存槽，选错就再也搜不到正确配方。
     * 因此同表有多条配方符合身份时只认定类型，不给出配方，交给上游按真实扣料搜索后自行缓存。
     */
    public static Result scan(List<GTRecipeType> types, PatternRecipeMatching.DecodedPattern pattern, Level level,
                              boolean skipHostTable, Predicate<GTRecipeType> isHostTable,
                              Consumer<GTRecipe> matchedRecipe) {
        int matched = MATCH_NONE;
        GTRecipe lastMatch = null;
        for (int i = 0; i < types.size(); i++) {
            GTRecipeType type = types.get(i);
            if (type == null) {
                continue;
            }
            if (isHostTable.test(type) == skipHostTable) {
                continue;
            }
            TypeMatch match = matchIn(type, pattern, level);
            if (!match.any()) {
                continue;
            }
            if (matched >= 0 && matched != i) {
                matchedRecipe.accept(null);
                return new Result(-1, null);
            }
            matched = i;
            lastMatch = match.unique();
            // 后续类型扫描可能抛异常，仍须保留本次已经命中的中转值。
            matchedRecipe.accept(lastMatch);
        }
        return new Result(matched, lastMatch);
    }

    private record TypeMatch(boolean any, GTRecipe unique) {
    }

    /** 样板带电路时电路完全一致的配方优先，其次才是不要求电路的配方。 */
    private static TypeMatch matchIn(GTRecipeType type, PatternRecipeMatching.DecodedPattern pattern, Level level) {
        GTRecipe first = null;
        GTRecipe exact = null;
        int matches = 0;
        int exactMatches = 0;
        for (GTRecipe recipe : level.getRecipeManager().getAllRecipesFor(type)) {
            if (!PatternRecipeMatching.patternIdentityMatches(recipe, pattern)) {
                continue;
            }
            if (matches++ == 0) {
                first = recipe;
            }
            if (pattern.circuit() >= 0 && PatternRecipeMatching.recipeCircuit(recipe) == pattern.circuit()) {
                if (exactMatches++ == 0) {
                    exact = recipe;
                }
            }
        }
        GTRecipe unique = exactMatches == 1 ? exact : (exactMatches == 0 && matches == 1 ? first : null);
        return new TypeMatch(matches > 0, unique);
    }

    public static void supplyIfAbsent(int slot, GTRecipe recipe, Supplier<IMEPatternTrait> trait) {
        if (recipe == null) {
            return;
        }
        try {
            if (trait.get().hasCacheInSlot(slot)) {
                return;
            }
            trait.get().setSlotCacheRecipe(slot, recipe);
        } catch (Throwable ignored) {
            SwallowedErrors.report("PatternRecipeTypeSearch#supplyIfAbsent", ignored);
            // 直供失败时机器仍可自行搜索配方，不能影响扣料。
        }
    }
}
