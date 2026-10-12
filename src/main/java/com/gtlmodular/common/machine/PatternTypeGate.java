package com.gtlmodular.common.machine;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;

/** 代理表中的配方可能仍标记父表类型，仅按类型引用相等会误拒绝；信息不足时交给后续身份校验。 */
public final class PatternTypeGate {

    private PatternTypeGate() {
    }

    public static boolean allows(GTRecipeType expected, GTRecipeType actual) {
        if (expected == null || actual == null) {
            return true;
        }
        if (actual == expected) {
            return true;
        }
        try {
            Object proxies = null;
            for (java.lang.reflect.Method m : expected.getClass().getMethods()) {
                if (m.getParameterCount() == 0 && "getProxyRecipes".equals(m.getName())) {
                    m.setAccessible(true);
                    proxies = m.invoke(expected);
                    break;
                }
            }
            if (proxies instanceof java.util.Collection<?> col) {
                for (Object o : col) {
                    if (o instanceof GTRecipe r && r.recipeType == actual) {
                        return true;
                    }
                }
                return false;
            }
        } catch (Throwable ignored) {
        }
        return true;
    }
}