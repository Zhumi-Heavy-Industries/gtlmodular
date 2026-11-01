package com.gtlmodular.common.machine.pcb;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.api.recipe.logic.OCParams;
import com.gregtechceu.gtceu.api.recipe.logic.OCResult;
import com.gtlmodular.common.machine.engine.IThreadedRecipeMachine;
import com.gtlmodular.common.machine.engine.ThreadedRecipeLogic;
import org.jetbrains.annotations.Nullable;

/**
 * 小型 PCB 工厂（{@code gtlmodular:small_pcb_factory}）。
 *
 * <p>结构 1:1 用 gtlcore 原始 PCB 工厂那版 17×10×15；机器逻辑按队长口径：
 *
 * <ul>
 *   <li><b>两条独立线程</b> —— 同一台机器同时推进 2 条<b>不同</b>配方，
 *       由本模组的独立线程引擎 {@link ThreadedRecipeLogic} 调度；</li>
 *   <li><b>天生自带并行 1024</b> —— {@link #parallelMultiplier()} 固定 1024，
 *       且 {@link #usesParallelHatch()} 返回 {@code false} ⇒ 结构里即使放了并行仓也不乘；</li>
 *   <li><b>不能装激光仓</b> —— 结构谓词里不开放 {@code INPUT_LASER}（见 SmallPcbFactoryStructures）；</li>
 *   <li><b>LuV 及以上配方能耗 ×4</b> —— 在 {@link #fullModifyRecipe} 里先把配方 EUt 乘 4，
 *       再交给标准非完美超频 ⇒ 高登记配方在这台机器上明显更贵，不会白嫖掉原版 PCB 工厂的活。</li>
 * </ul>
 *
 * <p>能耗闸放在 {@code fullModifyRecipe}（与部件组装机的等级闸同一位置）：返回 {@code null}
 * 会让引擎跳过这条候选；放在 {@code beforeWorking} 会让超档配方每 tick 被选中又被拒。
 */
public class SmallPcbFactoryMachine extends WorkableElectricMultiblockMachine
        implements IThreadedRecipeMachine {

    /** 两条独立线程（跨配方并行）。 */
    public static final int THREADS = 2;

    /** 自带并行：不依赖并行仓。 */
    public static final int PARALLEL = 1024;

    /** 高登记门槛：LuV（8192 EU/t）及以上。 */
    public static final long HIGH_TIER_EUT = GTValues.V[GTValues.LuV];

    /** 高登记能耗倍率。 */
    public static final int HIGH_TIER_ENERGY_MULTIPLIER = 4;

    public SmallPcbFactoryMachine(IMachineBlockEntity holder, Object... args) {
        super(holder, args);
    }

    @Override
    protected RecipeLogic createRecipeLogic(Object... args) {
        return new ThreadedRecipeLogic(this);
    }

    @Override
    public int baseThreads() {
        return THREADS;
    }

    @Override
    public int parallelMultiplier() {
        return PARALLEL;
    }

    /** 只看自身倍率，不乘结构并行仓（结构里也不开放并行仓）。 */
    @Override
    public boolean usesParallelHatch() {
        return false;
    }

    /**
     * LuV 及以上配方：先把 EUt ×4，再走标准配方修改器。
     *
     * <p>乘在<b>超频之前</b>：4 倍 EUt 会直接抬高超频起点，等效于同电压仓下少超一次。
     */
    @Nullable
    @Override
    public GTRecipe fullModifyRecipe(GTRecipe recipe, OCParams params, OCResult result) {
        GTRecipe effective = recipe;
        long eut = RecipeHelper.getInputEUt(recipe);
        if (eut >= HIGH_TIER_EUT) {
            GTRecipe copy = recipe.copy();
            long scaled = eut > Long.MAX_VALUE / HIGH_TIER_ENERGY_MULTIPLIER
                    ? Long.MAX_VALUE
                    : eut * HIGH_TIER_ENERGY_MULTIPLIER;
            RecipeHelper.setInputEUt(copy, scaled);
            effective = copy;
        }
        return super.fullModifyRecipe(effective, params, result);
    }
}
