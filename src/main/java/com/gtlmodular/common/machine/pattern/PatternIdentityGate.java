package com.gtlmodular.common.machine.pattern;

import appeng.api.crafting.IPatternDetails;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gtlmodular.GTLModular;
import com.gtlmodular.common.machine.PatternTypeGate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class PatternIdentityGate {

    private PatternIdentityGate() {
    }

    public static boolean allows(int slot, GTRecipe recipe, BooleanSupplier enabled,
                                 IntSupplier slotType, Supplier<List<GTRecipeType>> availableTypes,
                                 Supplier<ItemStack> patternStack, Supplier<Level> level,
                                 BiFunction<ItemStack, Level, IPatternDetails> decodePattern) {
        if (recipe == null || slot < 0) {
            return true;
        }
        if (!enabled.getAsBoolean()) {
            return true;
        }
        try {
            int detected = slotType.getAsInt();
            if (detected >= 0) {
                List<GTRecipeType> types = availableTypes.get();
                if (detected < types.size()) {
                    GTRecipeType expected = types.get(detected);
                    if (expected != null && recipe.recipeType != expected && !PatternTypeGate.allows(expected, recipe.recipeType)) {
                        return false;
                    }
                }
            } else {
                // 类型未知时没有可靠依据拦截，不能继续解码并执行身份判断。
                return true;
            }

            ItemStack stack = patternStack.get();
            if (stack.isEmpty()) {
                return true;
            }
            Level currentLevel = level.get();
            if (currentLevel == null) {
                return true;
            }
            IPatternDetails pattern = decodePattern.apply(stack, currentLevel);
            if (pattern == null) {
                return true;
            }
            return PatternRecipeMatching.patternIdentityMatches(recipe, pattern);
        } catch (Throwable ignored) {
            // 额外身份闸缺少可靠信息时，仍交给上游原有判据。
            return true;
        }
    }

    public static void logReject(int slot, GTRecipe recipe, String side,
                                 Supplier<List<GTRecipeType>> availableTypes, IntSupplier slotType) {
        List<GTRecipeType> types = availableTypes.get();
        int detectedIndex = slotType.getAsInt();
        String detectedName = (detectedIndex >= 0 && detectedIndex < types.size())
                ? PatternModeNaming.tableNameOf(types.get(detectedIndex))
                : "识别不出来";
        GTLModular.LOGGER.info("[gtlmodular] 身份闸拒绝配方（{}侧）槽位[{}]: 该槽样板标识=『{}』(下标{})，"
                        + "而这条配方属于『{}』({}) —— 若这台机器本该跑它，就是标识判错了："
                        + "Shift+右键该样板槽可手动指定正确标识（模式页也能填槽号指定）",
                side, slot + 1, detectedName, detectedIndex,
                PatternModeNaming.tableNameOf(recipe == null ? null : recipe.recipeType),
                recipe == null ? "null" : String.valueOf(recipe.recipeType));
    }

    public static void noteIfHostTableMisidentified(int slot, GTRecipe recipe,
                                                   Supplier<List<GTRecipeType>> availableTypes,
                                                   IntSupplier slotType, Predicate<GTRecipeType> isHostTable,
                                                   Set<String> notes, int noteLimit, Supplier<BlockPos> position) {
        if (recipe == null) {
            return;
        }
        try {
            List<GTRecipeType> types = availableTypes.get();
            int detected = slotType.getAsInt();
            if (detected < 0 || detected >= types.size()) {
                return;
            }
            GTRecipeType detectedType = types.get(detected);
            if (detectedType == null || detectedType == recipe.recipeType) {
                return;
            }
            if (!isHostTable.test(detectedType)) {
                return;
            }
            if (notes.size() >= noteLimit) {
                return;
            }
            String key = slot + "->" + recipe.recipeType;
            if (!notes.add(key)) {
                return;
            }
            GTLModular.LOGGER.warn("[gtlmodular] 【只读判读】槽位[{}] 的样板标识被认成 **『{}』**，"
                            + "但正在跑的配方属于『{}』 ⇒ 二选一：① 该槽样板本就属于别的机器"
                            + "（闸门在干正事，可忽略本行）；② 标识判错了（潜在缺陷，请把本行与"
                            + "紧跟其后的『身份闸拒绝配方』一起发回）；也可 Shift+右键该样板槽手动指定正确标识。"
                            + "本行只读、不改任何判定 @{}",
                    slot + 1, PatternModeNaming.tableNameOf(detectedType), PatternModeNaming.tableNameOf(recipe.recipeType), position.get());
        } catch (Throwable ignored) {
            // 诊断代码绝不允许影响游戏。
        }
    }
}
