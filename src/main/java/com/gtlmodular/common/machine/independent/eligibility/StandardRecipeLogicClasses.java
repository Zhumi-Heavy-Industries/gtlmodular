package com.gtlmodular.common.machine.independent.eligibility;

import com.gtladd.gtladditions.api.machine.logic.GTLAddMultipleRecipesLogic;
import com.gtladd.gtladditions.api.machine.logic.GTLAddMultipleTypeRecipesLogic;
import com.gtladd.gtladditions.api.machine.logic.GTLAddMultipleTypeWirelessRecipesLogic;
import com.gtladd.gtladditions.api.machine.logic.GTLAddMultipleWirelessRecipesLogic;
import com.gtladd.gtladditions.api.machine.logic.MutableRecipesLogic;

import java.util.Set;

// 上游类绑定与纯判断分开，避免测试准入规则时加载机器及其注册环境。
public final class StandardRecipeLogicClasses {

    public static final IndependentThreadEligibility ELIGIBILITY = new IndependentThreadEligibility(Set.of(
            GTLAddMultipleRecipesLogic.class,
            GTLAddMultipleTypeRecipesLogic.class,
            GTLAddMultipleWirelessRecipesLogic.class,
            GTLAddMultipleTypeWirelessRecipesLogic.class,
            MutableRecipesLogic.class));

    private StandardRecipeLogicClasses() {
    }
}
