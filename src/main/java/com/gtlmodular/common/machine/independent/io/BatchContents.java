package com.gtlmodular.common.machine.independent.io;

import com.gregtechceu.gtceu.api.capability.recipe.*;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;
import com.gregtechceu.gtceu.api.recipe.ingredient.IntProviderIngredient;
import com.gregtechceu.gtceu.api.recipe.ingredient.SizedIngredient;
import net.minecraft.world.item.crafting.Ingredient;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongIngredient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BatchContents {
    private BatchContents() {}

    public static Map<RecipeCapability<?>, List<Content>> copy(Map<RecipeCapability<?>, List<Content>> source) {
        Map<RecipeCapability<?>, List<Content>> result = new LinkedHashMap<>();
        source.forEach((cap, contents) -> {
            List<Content> copied = new ArrayList<>();
            for (var content : contents) copied.add(new Content(cap.copyContent(content.content), content.chance,
                    content.maxChance, content.tierChanceBoost, content.slotName, content.uiName));
            result.put(cap, copied);
        });
        return result;
    }

    public static void requireSupported(Map<RecipeCapability<?>, List<Content>> contents, boolean tick) {
        contents.forEach((cap, entries) -> {
            if (cap != ItemRecipeCapability.CAP && cap != FluidRecipeCapability.CAP && !(tick && cap == EURecipeCapability.CAP)) {
                throw new IllegalArgumentException("需要专用能力适配：" + cap.name);
            }
            for (Content entry : entries) {
                if (entry.slotName != null) throw new IllegalArgumentException("需要具名槽位适配");
                if (entry.content instanceof Ingredient ingredient) {
                    while (ingredient instanceof SizedIngredient sized) ingredient = sized.getInner();
                    if (ingredient instanceof IntProviderIngredient) throw new IllegalArgumentException("需要随机数量原料适配");
                }
                if (amount(cap, entry.content) < 0) throw new IllegalArgumentException("Negative IO amount");
            }
        });
    }

    public static long amount(RecipeCapability<?> cap, Object value) {
        if (cap == EURecipeCapability.CAP) return (Long) value;
        if (cap == FluidRecipeCapability.CAP) return ((FluidIngredient) value).getAmount();
        if (value instanceof LongIngredient ingredient) return ingredient.getActualAmount();
        if (value instanceof SizedIngredient ingredient) return ingredient.getAmount();
        var stacks = ((Ingredient) value).getItems();
        return stacks.length == 0 ? 0 : stacks[0].getCount();
    }

    public static void amount(RecipeCapability<?> cap, Content content, long remaining) {
        if (cap == EURecipeCapability.CAP) content.content = remaining;
        else if (cap == FluidRecipeCapability.CAP) ((FluidIngredient) content.content).setAmount(remaining);
        else if (content.content instanceof LongIngredient ingredient) ingredient.setActualAmount(remaining);
        else content.content = LongIngredient.create((Ingredient) content.content, remaining);
    }

    public static boolean empty(Map<RecipeCapability<?>, List<Content>> contents) {
        return contents.entrySet().stream().allMatch(e -> e.getValue().stream().allMatch(c -> amount(e.getKey(), c.content) == 0));
    }
}
