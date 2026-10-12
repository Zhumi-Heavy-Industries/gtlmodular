package com.gtlmodular.common.machine.pattern;

import appeng.api.crafting.IPatternDetails;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
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

    public static Result scan(List<GTRecipeType> types, IPatternDetails pattern, Level level,
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
            for (GTRecipe recipe : level.getRecipeManager().getAllRecipesFor(type)) {
                if (PatternRecipeMatching.patternIdentityMatches(recipe, pattern)) {
                    if (matched >= 0 && matched != i) {
                        matchedRecipe.accept(null);
                        return new Result(-1, null);
                    }
                    matched = i;
                    lastMatch = recipe;
                    // 后续类型扫描可能抛异常，仍须保留本次已经命中的中转值。
                    matchedRecipe.accept(recipe);
                    break;
                }
            }
        }
        return new Result(matched, lastMatch);
    }

    public static void supplyIfAbsent(int slot, GTRecipe recipe, Supplier<IMEPatternTrait> trait) {
        try {
            if (trait.get().hasCacheInSlot(slot)) {
                return;
            }
            trait.get().setSlotCacheRecipe(slot, recipe);
        } catch (Throwable ignored) {
            // 直供失败时机器仍可自行搜索配方，不能影响扣料。
        }
    }
}
