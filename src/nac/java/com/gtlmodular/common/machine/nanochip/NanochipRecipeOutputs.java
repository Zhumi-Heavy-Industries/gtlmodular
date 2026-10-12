package com.gtlmodular.common.machine.nanochip;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import org.gtlcore.gtlcore.api.machine.trait.IRecipeCapabilityMachine;
import org.gtlcore.gtlcore.api.machine.trait.RecipeHandlePart;
import org.gtlcore.gtlcore.api.recipe.IParallelLogic;

import java.util.ArrayList;
import java.util.List;

/** 完成点的产物仍归批次所有，只有实际交付后才能释放批次。 */
final class NanochipRecipeOutputs {
    static final String OUTPUT_FULL = "gtceu.recipe.fail.Output";

    private NanochipRecipeOutputs() {}

    static boolean tryOutput(IRecipeLogicMachine machine, GTRecipe recipe) {
        if (!(machine instanceof IRecipeCapabilityMachine holder)) {
            return false;
        }
        if (recipe.outputs.values().stream().flatMap(List::stream)
                .anyMatch(content -> content.chance < content.maxChance)) {
            preserveAccounting(recipe);
            GTRecipe resolved = IParallelLogic.getRecipeOutputChance(machine, recipe.copy());
            // 概率只结算一次；确定的产物随已有配方 CODEC 保存，重试和读档都不能重掷。
            recipe.outputs.clear();
            recipe.outputs.putAll(resolved.outputs);
            machine.self().markDirty();
        }

        // 模拟通过仍须核对实际余量，不能把允许作废或 ME 的乐观匹配当成交付成功。
        if (!transfer(holder, recipe, contentsOf(recipe), true).isEmpty()) {
            return false;
        }
        var remaining = transfer(holder, recipe, contentsOf(recipe), false);
        if (remaining.isEmpty()) {
            return true;
        }

        preserveAccounting(recipe);
        // handler 可能实际只接收一部分；旧配方存档只留下余量，避免重试重复交付。
        recipe.outputs.clear();
        remaining.forEach((capability, contents) -> {
            List<Content> pending = new ArrayList<>();
            for (Object content : contents) {
                pending.add(new Content(capability.copyContent(content), 10000, 10000, 0, null, null));
            }
            recipe.outputs.put(capability, pending);
        });
        machine.self().markDirty();
        return false;
    }

    private static void preserveAccounting(GTRecipe recipe) {
        if (!NanochipCalibrationBatch.hasSources(recipe)) {
            // 余量会变小，校准仍须在整批交付后按原批次记一次。
            NanochipCalibrationBatch accounting = new NanochipCalibrationBatch();
            accounting.capture(recipe);
            accounting.attach(recipe);
        }
    }

    private static Reference2ObjectOpenHashMap<RecipeCapability<?>, List<Object>> contentsOf(GTRecipe recipe) {
        var contents = new Reference2ObjectOpenHashMap<RecipeCapability<?>, List<Object>>();
        recipe.outputs.forEach((capability, outputs) -> {
            if (capability.doMatchInRecipe() && !outputs.isEmpty()) {
                List<Object> values = new ArrayList<>();
                for (Content output : outputs) {
                    values.add(capability.copyContent(output.content));
                }
                contents.put(capability, values);
            }
        });
        return contents;
    }

    private static Reference2ObjectOpenHashMap<RecipeCapability<?>, List<Object>> transfer(
            IRecipeCapabilityMachine machine, GTRecipe recipe,
            Reference2ObjectOpenHashMap<RecipeCapability<?>, List<Object>> contents, boolean simulate) {
        // 沿用上游 ME 优先、普通仓按优先级出料的次序，并保留各 handler 返回的余量。
        for (var handler : machine.getMEOutputRecipeHandleParts()) {
            // 此入口同时通知 ME 缓冲区变更，逐 capability 调用会漏掉通知。
            contents = handler.meHandleOutput(contents, simulate);
            contents.values().removeIf(List::isEmpty);
            if (contents.isEmpty()) return contents;
        }
        var handlers = new ArrayList<>(machine.getNormalRecipeHandlePart(IO.OUT));
        handlers.sort(RecipeHandlePart.COMPARATOR.reversed());
        for (var handler : handlers) {
            contents = handler.handleRecipe(IO.OUT, recipe, contents, simulate);
            contents.values().removeIf(List::isEmpty);
            if (contents.isEmpty()) break;
        }
        return contents;
    }
}
