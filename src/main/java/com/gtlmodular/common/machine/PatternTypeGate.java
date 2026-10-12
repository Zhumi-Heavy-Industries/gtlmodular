package com.gtlmodular.common.machine;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import net.minecraft.world.item.crafting.RecipeType;

import java.util.List;
import java.util.Map;

/**
 * 槽样板识别出的表与候选配方的表不同时，只有槽所在表的代理表收录了候选所属的表或这条配方才放行。
 * 代理表中的配方可能仍标记被代理表的类型，仅按类型引用相等会误拒绝；其余情况一律拒绝，
 * 因为身份闸只看输入种类和主产物，挡不住其他表里输入、产物相同的配方吃掉本槽原料。
 */
public final class PatternTypeGate {

    private PatternTypeGate() {
    }

    public static boolean allows(GTRecipeType expected, GTRecipeType actual) {
        if (expected == null || actual == null) {
            return false;
        }
        if (actual == expected) {
            return true;
        }
        Map<RecipeType<?>, List<GTRecipe>> proxies = expected.getProxyRecipes();
        if (proxies == null || proxies.isEmpty()) {
            return false;
        }
        if (proxies.containsKey(actual)) {
            return true;
        }
        for (List<GTRecipe> recipes : proxies.values()) {
            if (recipes == null) {
                continue;
            }
            for (GTRecipe recipe : recipes) {
                if (recipe != null && recipe.recipeType == actual) {
                    return true;
                }
            }
        }
        return false;
    }
}
