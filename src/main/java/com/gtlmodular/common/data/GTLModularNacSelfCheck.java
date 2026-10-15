package com.gtlmodular.common.data;

import com.gtlmodular.common.data.nanochip.NanochipExpectedRecipes;
import java.util.List;

public final class GTLModularNacSelfCheck {

    private GTLModularNacSelfCheck() {
    }

    public static void register() {
        NanochipExpectedRecipes.include(List.of(
                "ev_me_pattern_assembly",
                "hv_me_pattern_assembly",
                "iv_me_pattern_assembly",
                "luv_me_pattern_assembly",
                "molten_blast_module",
                "molten_module_host",
                "molten_space_ore_module",
                "molten_stripper_module",
                "mv_me_pattern_assembly",
                "pattern_archive",
                "pattern_archive_cut",
                "super_me_pattern_assembly",
                "uev_me_pattern_assembly",
                "uhv_me_pattern_assembly",
                "uiv_me_pattern_assembly",
                "uv_me_pattern_assembly",
                "zpm_me_pattern_assembly"),
                "地心与样板总成的方块、机器和仓室，以及 NAC 配方及其结构、材料和耗材获取链");
    }
}
