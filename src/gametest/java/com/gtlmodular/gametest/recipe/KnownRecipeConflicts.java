package com.gtlmodular.gametest.recipe;

import net.minecraft.resources.ResourceLocation;

/** 只豁免已核对的配方家族；新配方或其他表的冲突仍是普通失败。 */
public final class KnownRecipeConflicts {
    private KnownRecipeConflicts() {}

    public static String issue(RecipeConflictScanner.Finding finding) {
        if (!finding.owned() || !finding.status().equals("CONFLICT")) return "";
        ResourceLocation candidate = new ResourceLocation(finding.candidate());
        ResourceLocation target = new ResourceLocation(finding.target());
        String a = candidate.getPath();
        if (finding.type().equals("gtceu:vacuum_freezer") && a.equals("superconductor_uhv_base_vf")
                && target.toString().equals("gtceu:vacuum_freezer/cool_hot_superconductor_uhv_base_ingot")
                && finding.circuit() == -1 && finding.targetBatches() == 1) {
            return "UHV_FREEZER_INPUT_COLLISION";
        }
        return "";
    }
}
