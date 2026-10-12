package com.gtlmodular.common.data.nanochip;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.chemical.material.info.MaterialFlags;
import com.gregtechceu.gtceu.api.data.chemical.material.info.MaterialIconSet;

/** 材料形态由 GTCEu 生成，超导线材使用包内映射，避免为同档用途重复注册材料。 */
public final class NanochipMissingMaterials {

    private NanochipMissingMaterials() {
    }

    private static boolean registered;

    /** 档位与线材数组必须逐项对齐，查询使用相同下标。 */
    public static final String[] IN_PACK_SUPERCONDUCTOR_TIERS =
            { "luv", "zpm", "uv", "uhv", "uev", "uiv", "umv" };

    public static final String[] IN_PACK_SUPERCONDUCTOR_WIRES = {
            "gtceu:indium_tin_barium_titanium_cuprate_single_wire",
            "gtceu:uranium_rhodium_dinaquadide_single_wire",
            "gtceu:enriched_naquadah_trinium_europium_duranide_single_wire",
            "gtceu:ruthenium_trinium_americium_neutronate_single_wire",
            "gtceu:enderite_single_wire",
            "gtceu:echoite_single_wire",
            "gtceu:legendarium_single_wire",
    };

    public static String inPackSuperconductorWire(String tier) {
        for (int i = 0; i < IN_PACK_SUPERCONDUCTOR_TIERS.length; i++) {
            if (IN_PACK_SUPERCONDUCTOR_TIERS[i].equals(tier)) {
                return IN_PACK_SUPERCONDUCTOR_WIRES[i];
            }
        }
        return null;
    }

    /** 这两种材料仍服务其他材料形态，不能因线材改用包内映射就删除材料本体。 */
    public static final String[] RETAINED_MATERIALS = { "superconductor_uhv_base", "superconductor_uev_base" };

    /** 材料及其派生形态需在 GTCEu 材料注册窗口创建，避免错过后续生成阶段。 */
    public static synchronized void registerMissingMaterials() {
        if (registered) {
            return;
        }
        registered = true;

        newMaterial("chromatic_glass", 0x9397A1, MaterialIconSet.METALLIC)
                .flags(MaterialFlags.GENERATE_BOLT_SCREW)
                .buildAndRegister();

        newMaterial("six_phased_copper", 0xFF7814, MaterialIconSet.SHINY)
                .blastTemp(14000)
                .flags(MaterialFlags.GENERATE_BOLT_SCREW)
                .buildAndRegister();

        newMaterial("universium", 0x263145, MaterialIconSet.SHINY)
                .flags(MaterialFlags.GENERATE_BOLT_SCREW)
                .buildAndRegister();

        newMaterial("hexanite", 0x3FB094, MaterialIconSet.RADIOACTIVE)
                .flags(MaterialFlags.GENERATE_PLATE)
                .buildAndRegister();

        new Material.Builder(GTCEu.id("super_coolant"))
                .color(0x4FC3F7)
                .secondaryColor(lighten(0x4FC3F7, 0.55F))
                .iconSet(MaterialIconSet.FLUID)
                .fluid()
                .buildAndRegister();

        new Material.Builder(GTCEu.id("dimensionally_offset_superfluid"))
                .color(0x8E24AA)
                .secondaryColor(lighten(0x8E24AA, 0.55F))
                .iconSet(MaterialIconSet.FLUID)
                .fluid()
                .buildAndRegister();

        NanochipModContext.logger().info("[gtlmodular] P1 缺件·材料侧：新增 4 味材料（chromatic_glass / six_phased_copper / "
                + "universium / hexanite）⇒ GTCEu 会生成 bolt/screw/plate 形态 ×4；"
                + "7 味自造超导粗胚已退役（换算配方改引用包内同级线材，见 IN_PACK_SUPERCONDUCTOR_WIRES）");
    }

    private static Material.Builder newMaterial(String id, int color, MaterialIconSet iconSet) {
        return new Material.Builder(GTCEu.id(id))
                .color(color)
                .secondaryColor(lighten(color, 0.55F))
                .iconSet(iconSet)
                .dust();
    }

    private static int lighten(int rgb, float amount) {
        final float a = Math.max(0F, Math.min(1F, amount));
        final int r = (rgb >> 16) & 0xFF;
        final int g = (rgb >> 8) & 0xFF;
        final int b = rgb & 0xFF;
        final int nr = Math.round(r + (0xFF - r) * a);
        final int ng = Math.round(g + (0xFF - g) * a);
        final int nb = Math.round(b + (0xFF - b) * a);
        return (nr << 16) | (ng << 8) | nb;
    }

    public static final String[] NEW_PLAIN_MATERIALS = { "chromatic_glass", "six_phased_copper", "universium", "hexanite" };
}
