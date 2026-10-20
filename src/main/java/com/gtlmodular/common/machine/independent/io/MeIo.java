package com.gtlmodular.common.machine.independent.io;

import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gtlmodular.common.config.GTLModularConfig;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import org.gtlcore.gtlcore.api.machine.trait.IRecipeCapabilityMachine;
import org.gtlcore.gtlcore.api.machine.trait.MEIORecipeHandlePart;
import org.gtlcore.gtlcore.api.recipe.RecipeRunnerHelper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ME/长数量仓储的批量 IO。ME 处理器本来就用 long 计数；这里把配方内容转成它们的对象形态，
 * 模拟一次、真实转移一次，并把剩余量写回同一张 Content 表，避免大数量被 int 截断或重复交付。
 * 普通总线仍由 IndependentRecipeIo 负责；本类只在配置开启且机器确实装了 ME 处理器时启用。
 */
public final class MeIo {

    /** 只依赖"对象 → 对象"的搬运，便于单测长数量与部分余量，不绑定 GT 能力注册表。 */
    @FunctionalInterface
    public interface OutputPart {
        Map<Object, List<Object>> transfer(Map<Object, List<Object>> contents, boolean simulate);
    }

    private MeIo() {}

    public static boolean enabled(Object machine) {
        if (!GTLModularConfig.OTHER_MACHINE_THREADS.allowMeIo()
                && !Boolean.getBoolean("gtlmodular.testHost")) return false;
        return machine instanceof IRecipeCapabilityMachine parts && hasMeParts(parts);
    }

    public static boolean hasMeParts(IRecipeCapabilityMachine machine) {
        return !machine.getMEPatternRecipeHandleParts().isEmpty() || !machine.getMEOutputRecipeHandleParts().isEmpty();
    }

    public static boolean inputFits(Object machine, GTRecipe recipe) {
        return machine instanceof com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine logic
                && RecipeRunnerHelper.matchRecipeInput(logic, recipe);
    }

    public static boolean consumeInputs(Object machine, GTRecipe recipe) {
        return machine instanceof com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine logic
                && RecipeRunnerHelper.handleRecipeInput(logic, recipe);
    }

    public static boolean outputFits(Object machine, Map<RecipeCapability<?>, List<Content>> contents) {
        return transferOutput(machine, contents, true);
    }

    public static boolean transferOutput(Object machine, Map<RecipeCapability<?>, List<Content>> contents, boolean simulate) {
        if (!(machine instanceof IRecipeCapabilityMachine parts) || parts.getMEOutputRecipeHandleParts().isEmpty()) {
            return false;
        }
        var typed = toObjects(contents);
        var remaining = transferMeParts(parts.getMEOutputRecipeHandleParts(), typed, simulate);
        if (!simulate) writeBack(contents, remaining);
        return remaining.isEmpty();
    }

    public static boolean hasTickItems(GTRecipe recipe) {
        return hasItemOrFluid(recipe.tickInputs) || hasItemOrFluid(recipe.tickOutputs);
    }

    /** 单测入口：只做按顺序搬运，不碰 GT 机器。 */
    public static Map<Object, List<Object>> transferParts(List<? extends OutputPart> parts,
                                                          Map<Object, List<Object>> contents, boolean simulate) {
        Map<Object, List<Object>> remaining = contents;
        for (OutputPart part : parts) {
            if (remaining.isEmpty()) break;
            remaining = part.transfer(remaining, simulate);
        }
        return remaining;
    }

    private static Map<Object, List<Object>> toObjects(Map<RecipeCapability<?>, List<Content>> contents) {
        Map<Object, List<Object>> result = new LinkedHashMap<>();
        for (var entry : contents.entrySet()) {
            List<Object> values = new ArrayList<>();
            for (Content content : entry.getValue()) values.add(entry.getKey().copyContent(content.content));
            result.put(entry.getKey(), values);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<Object, List<Object>> transferMeParts(List<MEIORecipeHandlePart<?>> parts,
                                                              Map<Object, List<Object>> contents, boolean simulate) {
        List<OutputPart> adapters = new ArrayList<>();
        for (MEIORecipeHandlePart<?> part : parts) {
            adapters.add((values, dry) -> {
                var typed = new Reference2ObjectOpenHashMap<RecipeCapability<?>, List<Object>>();
                values.forEach((key, list) -> typed.put((RecipeCapability<?>) key, list));
                var result = part.meHandleOutput(typed, dry);
                Map<Object, List<Object>> out = new LinkedHashMap<>();
                result.forEach(out::put);
                return out;
            });
        }
        return transferParts(adapters, contents, simulate);
    }

    private static void writeBack(Map<RecipeCapability<?>, List<Content>> contents, Map<Object, List<Object>> remaining) {
        contents.clear();
        for (var entry : remaining.entrySet()) {
            if (!(entry.getKey() instanceof RecipeCapability<?> capability)) continue;
            List<Content> values = new ArrayList<>();
            for (Object value : entry.getValue()) {
                values.add(new Content(capability.copyContent(value), 10000, 10000, 0, null, null));
            }
            if (!values.isEmpty()) contents.put(capability, values);
        }
    }

    private static boolean hasItemOrFluid(Map<RecipeCapability<?>, List<Content>> contents) {
        return contents.containsKey(com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability.CAP)
                || contents.containsKey(com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability.CAP);
    }
}
