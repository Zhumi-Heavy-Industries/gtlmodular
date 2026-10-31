package com.gtlmodular.common.machine.fuel;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.api.recipe.logic.OCParams;
import com.gregtechceu.gtceu.api.recipe.logic.OCResult;
import com.gregtechceu.gtceu.common.data.GTRecipeModifiers;
import com.gtlmodular.GTLModular;

/**
 * 模块 B/C 的“休闲降档超频”修正器。
 *
 * <p>背景：星核剥离 / 天基矿石的配方大多带高电压档需求（UHV 乃至更高）。
 * 若直接挂 GTM 的 {@code ELECTRIC_OVERCLOCK}，机器电压低于配方电压档时修正器
 * 返回 null（配方被跳过），表现为“未找到配方 / JEI 电压不足”。
 *
 * <p>休闲语义（用户已确认）：<b>降档慢跑、保总能耗</b> —— 配方 EUt 高于机器可承受
 * 电压时，不拒绝配方，而是把 EUt 压到机器可用电压、耗时按能量等比拉长
 * （EUt×耗时 基本不变，仅“慢”不“废”）。电压足够时走标准电力超频。
 */
public final class MoltenRelaxedElectricOverclock {

    private MoltenRelaxedElectricOverclock() {
    }

    /** 供注册处使用：{@code .recipeModifier(MoltenRelaxedElectricOverclock::apply)}。 */
    public static GTRecipe apply(MetaMachine machine, GTRecipe recipe,
                                 OCParams params, OCResult result) {
        if (!(machine instanceof WorkableElectricMultiblockMachine workable)) {
            return null;
        }
        long maxVoltage = workable.getOverclockVoltage();
        if (maxVoltage <= 0) {
            return null; // 没接能源仓，不可运行
        }
        long recipeEUt = RecipeHelper.getInputEUt(recipe);
        if (recipeEUt <= maxVoltage) {
            // 电压足够：交给标准非完美电力超频
            return GTRecipeModifiers.ELECTRIC_OVERCLOCK
                    .apply(com.gregtechceu.gtceu.api.recipe.OverclockingLogic.NON_PERFECT_OVERCLOCK_SUBTICK)
                    .apply(machine, recipe, params, result);
        }
        // ---- 降档慢跑：EUt → 机器可用电压，耗时等比拉长（保总能耗） ----
        try {
            GTRecipe copy = recipe.copy();
            double ratio = recipeEUt / (double) maxVoltage;
            long newDuration = Math.max(1, Math.round(recipe.duration * ratio));
            RecipeHelper.setInputEUt(copy, maxVoltage);
            copy.duration = (int) Math.min(Integer.MAX_VALUE, newDuration);
            // 超频：以机器电压为上限再做一次非完美超频（通常已贴近，不会再有变化）
            return GTRecipeModifiers.ELECTRIC_OVERCLOCK
                    .apply(com.gregtechceu.gtceu.api.recipe.OverclockingLogic.NON_PERFECT_OVERCLOCK_SUBTICK)
                    .apply(machine, copy, params, result);
        } catch (RuntimeException e) {
            GTLModular.LOGGER.warn("[gtlmodular] 休闲降档超频失败，退回原配方处理: {}", e.toString());
            return null;
        }
    }
}
