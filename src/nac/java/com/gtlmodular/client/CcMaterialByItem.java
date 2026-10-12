package com.gtlmodular.client;

import java.util.Map;

/** 无 realRef 的灰度 CC 按换算配方的真实输入材料染色，避免按 CC 名称猜材料。 */
final class CcMaterialByItem {

    static final Map<String, String> MATERIALS = Map.ofEntries(
            Map.entry("cc_bolt_chromatic_glass", "chromatic_glass"),
            Map.entry("cc_bolt_cosmic_neutronium", "neutronium"),
            Map.entry("cc_bolt_enriched_holmium", "tritanium"),
            Map.entry("cc_bolt_transcendent_metal", "darmstadtium"),
            Map.entry("cc_bolt_umv_superconductor", "legendarium"),
            Map.entry("cc_bolt_white_dwarf_matter", "tungsten_steel"),
            Map.entry("cc_foil_eternity", "cosmicneutronium"),
            Map.entry("cc_foil_radox_polymer", "radox"),
            Map.entry("cc_foil_shirabon", "tritanium"),
            Map.entry("cc_framebox_celestial_tungsten", "infinity"),
            Map.entry("cc_framebox_hypogen", "starmetal"),
            Map.entry("cc_framebox_mag_matter", "neutronium"),
            Map.entry("cc_plate_hexanite", "hexanite"),
            Map.entry("cc_plate_metastable_oganesson", "hypogen"),
            Map.entry("cc_plate_rhugnor", "tritanium"),
            Map.entry("cc_screw_astral_titanium", "tritanium"),
            Map.entry("cc_screw_six_phased_copper", "six_phased_copper"),
            Map.entry("cc_screw_universium", "universium"),
            Map.entry("cc_superconductor_lu_v", "indium_tin_barium_titanium_cuprate"),
            Map.entry("cc_superconductor_uev", "enderite"),
            Map.entry("cc_superconductor_uhv", "ruthenium_trinium_americium_neutronate"),
            Map.entry("cc_superconductor_uiv", "echoite"),
            Map.entry("cc_superconductor_umv", "legendarium"),
            Map.entry("cc_superconductor_uv", "enriched_naquadah_trinium_europium_duranide"),
            Map.entry("cc_superconductor_zpm", "uranium_rhodium_dinaquadide"),
            Map.entry("cc_wire_hypogen", "hypogen"),
            Map.entry("cc_wire_infinity", "infinity"),
            Map.entry("cc_wire_lumiium", "cosmicneutronium"),
            Map.entry("cc_wire_mag_matter", "tritanium"),
            Map.entry("cc_wire_proto_halkonite", "white_dwarf_mtter")
    );

    private CcMaterialByItem() {
    }
}
