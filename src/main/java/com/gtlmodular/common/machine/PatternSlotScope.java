package com.gtlmodular.common.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;

/** 将维度、仓坐标和槽号编码进配方 ID，使槽级守卫能区分输入相同的不同样板槽。 */
public final class PatternSlotScope {

    /** 使用本模组的后缀，避免误认其他模组的作用域标记。 */
    public static final String SCOPE_PATH = "/__gtlmodular_pattern_slot/";

    private PatternSlotScope() {
    }

    public static GTRecipe scope(GTRecipe recipe, ResourceLocation dim, BlockPos pos, int slot) {
        if (recipe == null || dim == null || pos == null) {
            return recipe;
        }
        ResourceLocation id = recipe.getId();
        if (id == null || isScoped(recipe)) {
            return recipe;
        }
        ResourceLocation scoped = ResourceLocation.tryParse(
                id + SCOPE_PATH + dim + "|" + pos.asLong() + "|" + slot);
        if (scoped == null) {
            return recipe;
        }
        recipe.setId(scoped);
        return recipe;
    }

    public static boolean isScoped(GTRecipe recipe) {
        ResourceLocation id = recipe == null ? null : recipe.getId();
        return id != null && id.toString().contains(SCOPE_PATH);
    }

    public static boolean matchesSource(GTRecipe recipe, ResourceLocation dim, BlockPos pos, int slot) {
        int[] decoded = decodeScope(recipe);
        if (decoded == null || dim == null || pos == null) {
            return false;
        }
        return decoded[0] == dim.hashCode() && decoded[1] == pos.asLong() && decoded[2] == slot;
    }

    /** 维度哈希只用于运行期比对，不需要从标记还原维度名称。 */
    public static int[] decodeScope(GTRecipe recipe) {
        ResourceLocation id = recipe == null ? null : recipe.getId();
        if (id == null) {
            return null;
        }
        String s = id.toString();
        int at = s.indexOf(SCOPE_PATH);
        if (at < 0) {
            return null;
        }
        String tail = s.substring(at + SCOPE_PATH.length());
        String[] parts = tail.split("\\|");
        if (parts.length < 3) {
            return null;
        }
        try {
            return new int[]{parts[0].hashCode(), (int) Long.parseLong(parts[1]), Integer.parseInt(parts[2])};
        } catch (NumberFormatException bad) {
            return null;
        }
    }
}