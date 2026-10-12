package com.gtlmodular.gametest.resource;

import com.gtlmodular.common.data.nanochip.NanochipCircuitComponents.CcItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

public final class KnownResourceFailures {
    public static final String AIR_NAME = "NAC_CC_MISSING_REFERENCE_NAME";
    public static final String PACK_TEXTURES = "NAC_CC_PACK_TEXTURES";

    public static String issue(ResourceAudit.Finding finding) {
        if (!finding.owner().startsWith("item:")) return "";
        var item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(finding.owner().substring(5)));
        if (!(item instanceof CcItem cc)) return "";
        var entry = cc.circuitComponent();
        if (finding.kind().equals("fallback_name") && !entry.processed && entry.realRef != null
                && entry.realRef.equals(finding.resource()) && entry.realRef.startsWith("itemns:kubejs:")) {
            var target = new ResourceLocation(entry.realRef.substring("itemns:".length()));
            if (!ForgeRegistries.ITEMS.containsKey(target) && ForgeRegistries.ITEMS.getValue(target) == Items.AIR) {
                return AIR_NAME;
            }
        }
        // CC 名称引用整合包 KubeJS 物品的翻译；精简服的测试脚本只注册了物品、没有整合包语言文件。
        if (finding.kind().equals("translation") && finding.resource().matches("(zh_cn|en_us):item\\.kubejs\\..+")
                && entry.realRef != null && entry.realRef.startsWith("itemns:kubejs:")) {
            return AIR_NAME;
        }
        // CC 模型复用整合包贴图，精简服未安装正式 KubeJS 资源；文件损坏和自有资源缺失不在此列。
        if (finding.kind().equals("texture") && finding.resource().startsWith("kubejs:item/")
                && finding.detail().equals("java.io.IOException: 缺少 PNG")) {
            var target = new ResourceLocation(finding.resource().replace(":item/", ":"));
            // 精简服的测试脚本可能注册了同名 KubeJS 物品，但不会带整合包贴图，所以不以是否注册来区分。
            if ((entry.realRef == null ? legacyTexture(entry.itemId).equals(target.getPath())
                            : entry.realRef.equals("itemns:" + target))) return PACK_TEXTURES;
        }
        return "";
    }

    private static String legacyTexture(String item) {
        if (item.matches("cc_(bioware|optical)_(processor|assembly|computer|mainframe)")) return item.substring(3);
        if (item.matches("cc_optical_smd_(capacitor|diode|inductor|resistor|transistor)")) {
            return item.substring("cc_optical_".length()) + "_optical";
        }
        return switch (item) {
            case "cc_bio_processing_unit" -> "bioware_processing_core";
            case "cc_board_bio_mutated" -> "bioware_printed_circuit_board";
            case "cc_board_optical" -> "optical_printed_circuit_board";
            case "cc_chip_optical_cpu" -> "optical_processing_core";
            case "cc_chip_piko_pic" -> "pm_chip";
            case "cc_living_bio_chip" -> "bioware_chip";
            case "cc_optical_ram" -> "optical_ram_chip";
            default -> "";
        };
    }

    private KnownResourceFailures() {}
}
