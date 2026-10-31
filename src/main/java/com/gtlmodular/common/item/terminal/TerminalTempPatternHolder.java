package com.gtlmodular.common.item.terminal;

import appeng.api.crafting.IPatternDetails;

/** 由 mixin 实现在 AE CraftingService 上，给订单标记挂一个临时样板。 */
public interface TerminalTempPatternHolder {

    void gtlmodular$setTempPattern(IPatternDetails pattern);

    IPatternDetails gtlmodular$getTempPattern();
}
