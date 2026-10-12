package com.gtlmodular.gametest.recipe;

import net.minecraft.resources.ResourceLocation;

import java.util.Set;

/** 只豁免已核对的配方家族；新配方或其他表的冲突仍是普通失败。 */
public final class KnownRecipeConflicts {
    private static final Set<String> baseModules = Set.of("nanochip_assembly_matrix", "nanochip_board_processor",
            "nanochip_etching_array", "nanochip_smd_processor");
    private static final Set<String> advancedModules = Set.of("nanochip_biological_coordinator",
            "nanochip_cosmic_organizer", "nanochip_cutting_chamber", "nanochip_encasement_wrapper",
            "nanochip_exotic_organizer", "nanochip_optical_organizer", "nanochip_splitter",
            "nanochip_superconductor_splitter", "nanochip_supracausal_organizer", "nanochip_wire_tracer");

    private KnownRecipeConflicts() {}

    public static String issue(RecipeConflictScanner.Finding finding) {
        if (!finding.owned() || !finding.status().equals("CONFLICT")) return "";
        ResourceLocation candidate = new ResourceLocation(finding.candidate());
        ResourceLocation target = new ResourceLocation(finding.target());
        String a = candidate.getPath();
        String b = target.getPath();
        boolean sameProduct = candidate.getNamespace().equals(target.getNamespace())
                && Set.of("gtlmodular", "gtl_nac").contains(candidate.getNamespace());
        if (finding.type().equals("gtceu:assembly_line") && sameProduct && finding.circuit() == -1
                && ((baseModules.contains(a) && (baseModules.contains(b) || b.equals("nanochip_assembly_complex")))
                || (advancedModules.contains(a) && advancedModules.contains(b)))) {
            return "NAC_ASSEMBLY_LINE_INPUT_COLLISION";
        }
        if (finding.type().equals("gtceu:assembler")) {
            if (sameProduct && finding.circuit() == -1
                    && Set.of("nanochip_mesh_interface_casing", "nanochip_vacuum_pipe").contains(a)
                    && Set.of("nanochip_mesh_interface_casing", "nanochip_vacuum_pipe",
                    "nanochip_vacuum_input_hatch", "pattern_archive").contains(b)) {
                return "NAC_ASSEMBLER_INPUT_COLLISION";
            }
            if (a.equals("naquadah_alloy_sheetmetal")
                    && target.toString().equals("gtmthings:assembler/wireless_energy_receive_cover_zpm")) {
                return "NAC_ASSEMBLER_INPUT_COLLISION";
            }
        }
        if (finding.type().equals("gtceu:vacuum_freezer") && a.equals("superconductor_uhv_base_vf")
                && target.toString().equals("gtceu:vacuum_freezer/cool_hot_superconductor_uhv_base_ingot")
                && finding.circuit() == -1 && finding.targetBatches() == 1) {
            return "UHV_FREEZER_INPUT_COLLISION";
        }
        return "";
    }
}
