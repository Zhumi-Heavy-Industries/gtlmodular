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

    /**
     * 电路（编程电路）是「配置」不是「材料」：并行和长批次都不能放大它的数量。
     * 上游配方里它永远是 1 份；一旦被放大成 p 份，任何仓都凑不出来（黑奴挖矿机就是这样卡住的）。
     */
    public static boolean isCircuit(Object value) {
        Object current = value;
        while (current instanceof SizedIngredient sized) current = sized.getInner();
        return current instanceof com.gregtechceu.gtceu.api.recipe.ingredient.IntCircuitIngredient;
    }

    /**
     * 长批次：把一整份物料按次数放大（开批一次扣 p×N、结算一次出 p×N）。
     * EU/t 由能量层单独计算，不在这里乘；超出 long 直接拒绝，不能静默饱和成假数量。
     */
    public static void multiplyBy(Map<RecipeCapability<?>, List<Content>> contents, int factor) {
        if (factor <= 1 || contents == null) return;
        for (var entry : contents.entrySet()) {
            if (entry.getKey() == EURecipeCapability.CAP) continue;
            for (Content content : entry.getValue()) {
                if (isCircuit(content.content)) continue;
                // chance=0 是非消耗输入（GTRecipeBuilder.notConsumable 就是写成 chance=0），数量恒为 1。
                if (content.chance <= 0) continue;
                long per = amount(entry.getKey(), content.content);
                if (per <= 0L) continue;
                long scaled;
                try {
                    scaled = Math.multiplyExact(per, (long) factor);
                } catch (ArithmeticException overflow) {
                    throw new IllegalArgumentException("Batch content overflow", overflow);
                }
                amount(entry.getKey(), content, scaled);
            }
        }
    }
}
