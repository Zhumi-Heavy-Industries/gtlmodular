package com.gtlmodular.client;

import java.util.Map;

/**
 * 【自动生成 · 不要手改】由 {@code _census/gen_cc_material_table.js} 从**配方图**现推。
 *
 * <p>规则（与自检共用 {@code _census/cc_material_rule.js}）：取本模组换算配方
 * {@code gtlmodular:nanochip_conversion} 的**正向边**（outputs 恰好一个本模组 CC、inputs 恰好一个非 CC 物品）
 * ⇒ 输入真件 = 该 CC 的料；再从真件 id 里用"从注册表学到的产出型残差模式"切出材料名。
 * <b>零手写映射</b>：本文件每一行都能对上 `_census/cc_material_table.md` 的同名行，
 * 那份 md 又逐条引用配方 JSON 与真件 id ⇒ 三方（配方 / md / 本表）由自检逐条互校。
 *
 * <p>生成物规模：<b>30</b> 条（只覆盖注册行 {@code realRef} 为空的件；
 * {@code realRef} 非空的 86 件由 {@link CcItemColorSetup} 走注册行原路，不进本表）。
 * 仍不能上色：<b>0</b> 条 —— 
 * （明细见 {@code _census/cc_material_table.md} §二）。
 *
 * <p>★ <b>改配方必须重跑生成器</b>：{@code node _census/gen_cc_material_table.js}；
 * 自检 {@code _census/check_item_colors.js} 会断言"本表 == md == 配方推导"。</p>
 */
final class CcMaterialByItem {

    /** 物品 id（不含命名空间）→ GTCEu 材料名。键只在 {@code realRef} 为空的件里出现。 */
    static final Map<String, String> MATERIALS = Map.ofEntries(
            // ← gtceu:chromatic_glass_bolt  [#_bolt]  cc_bolt_chromatic_glass.json
            Map.entry("cc_bolt_chromatic_glass", "chromatic_glass"),
            // ← gtceu:neutronium_bolt  [#_bolt]  cc_bolt_cosmic_neutronium.json
            Map.entry("cc_bolt_cosmic_neutronium", "neutronium"),
            // ← gtceu:tritanium_bolt  [#_bolt]  cc_bolt_enriched_holmium.json
            Map.entry("cc_bolt_enriched_holmium", "tritanium"),
            // ← gtceu:darmstadtium_bolt  [#_bolt]  cc_bolt_transcendent_metal.json
            Map.entry("cc_bolt_transcendent_metal", "darmstadtium"),
            // ← gtceu:legendarium_bolt  [#_bolt]  cc_bolt_umv_superconductor.json
            Map.entry("cc_bolt_umv_superconductor", "legendarium"),
            // ← gtceu:tungsten_steel_bolt  [#_bolt]  cc_bolt_white_dwarf_matter.json
            Map.entry("cc_bolt_white_dwarf_matter", "tungsten_steel"),
            // ← gtceu:cosmicneutronium_foil  [#_foil]  cc_foil_eternity.json
            Map.entry("cc_foil_eternity", "cosmicneutronium"),
            // ← gtceu:radox_foil  [#_foil]  cc_foil_radox_polymer.json
            Map.entry("cc_foil_radox_polymer", "radox"),
            // ← gtceu:tritanium_foil  [#_foil]  cc_foil_shirabon.json
            Map.entry("cc_foil_shirabon", "tritanium"),
            // ← gtceu:infinity_frame  [#_frame]  cc_framebox_celestial_tungsten.json
            Map.entry("cc_framebox_celestial_tungsten", "infinity"),
            // ← gtceu:starmetal_frame  [#_frame]  cc_framebox_hypogen.json
            Map.entry("cc_framebox_hypogen", "starmetal"),
            // ← gtceu:neutronium_frame  [#_frame]  cc_framebox_mag_matter.json
            Map.entry("cc_framebox_mag_matter", "neutronium"),
            // ← gtceu:hexanite_plate  [#_plate]  cc_plate_hexanite.json
            Map.entry("cc_plate_hexanite", "hexanite"),
            // ← gtceu:hypogen_plate  [#_plate]  cc_plate_metastable_oganesson.json
            Map.entry("cc_plate_metastable_oganesson", "hypogen"),
            // ← gtceu:tritanium_plate  [#_plate]  cc_plate_rhugnor.json
            Map.entry("cc_plate_rhugnor", "tritanium"),
            // ← gtceu:tritanium_screw  [#_screw]  cc_screw_astral_titanium.json
            Map.entry("cc_screw_astral_titanium", "tritanium"),
            // ← gtceu:six_phased_copper_screw  [#_screw]  cc_screw_six_phased_copper.json
            Map.entry("cc_screw_six_phased_copper", "six_phased_copper"),
            // ← gtceu:universium_screw  [#_screw]  cc_screw_universium.json
            Map.entry("cc_screw_universium", "universium"),
            // ← gtceu:indium_tin_barium_titanium_cuprate_single_wire  [#_single_wire]  cc_superconductor_lu_v.json
            Map.entry("cc_superconductor_lu_v", "indium_tin_barium_titanium_cuprate"),
            // ← gtceu:enderite_single_wire  [#_single_wire]  cc_superconductor_uev.json
            Map.entry("cc_superconductor_uev", "enderite"),
            // ← gtceu:ruthenium_trinium_americium_neutronate_single_wire  [#_single_wire]  cc_superconductor_uhv.json
            Map.entry("cc_superconductor_uhv", "ruthenium_trinium_americium_neutronate"),
            // ← gtceu:echoite_single_wire  [#_single_wire]  cc_superconductor_uiv.json
            Map.entry("cc_superconductor_uiv", "echoite"),
            // ← gtceu:legendarium_single_wire  [#_single_wire]  cc_superconductor_umv.json
            Map.entry("cc_superconductor_umv", "legendarium"),
            // ← gtceu:enriched_naquadah_trinium_europium_duranide_single_wire  [#_single_wire]  cc_superconductor_uv.json
            Map.entry("cc_superconductor_uv", "enriched_naquadah_trinium_europium_duranide"),
            // ← gtceu:uranium_rhodium_dinaquadide_single_wire  [#_single_wire]  cc_superconductor_zpm.json
            Map.entry("cc_superconductor_zpm", "uranium_rhodium_dinaquadide"),
            // ← gtceu:hypogen_single_wire  [#_single_wire]  cc_wire_hypogen.json
            Map.entry("cc_wire_hypogen", "hypogen"),
            // ← gtceu:infinity_single_wire  [#_single_wire]  cc_wire_infinity.json
            Map.entry("cc_wire_infinity", "infinity"),
            // ← gtceu:fine_cosmicneutronium_wire  [fine_#_wire]  cc_wire_lumiium.json
            Map.entry("cc_wire_lumiium", "cosmicneutronium"),
            // ← gtceu:fine_tritanium_wire  [fine_#_wire]  cc_wire_mag_matter.json
            Map.entry("cc_wire_mag_matter", "tritanium"),
            // ← gtceu:fine_white_dwarf_mtter_wire  [fine_#_wire]  cc_wire_proto_halkonite.json
            Map.entry("cc_wire_proto_halkonite", "white_dwarf_mtter")
    );

    private CcMaterialByItem() {
    }
}
