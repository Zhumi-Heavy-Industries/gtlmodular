package com.gtlmodular.common.machine.nanochip;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;

/** 将机器的候选与线程策略显式提供给配方逻辑，避免直接访问机器内部字段。 */
public interface NanochipThreadHost {

    int nanochipThreadCount();

    GTRecipeType[] nanochipSearchTypes();

    /** 候选校验可能重复执行，必须只读，实际扣料由开批入口完成。 */
    boolean nanochipCandidateUsable(GTRecipe candidate);

    /** 机器持有可变游标，逻辑重建后仍可延续该机器的轮转位置。 */
    String[] nanochipThreadCursors();
}
