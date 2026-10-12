package com.gtlmodular.common.data;

import com.gtlmodular.common.data.nanochip.NanochipModContext;
import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTCEuAPI;
import com.gregtechceu.gtceu.api.addon.IGTAddon;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.chemical.material.event.MaterialEvent;
import com.gregtechceu.gtceu.api.data.chemical.material.info.MaterialFlags;
import com.gregtechceu.gtceu.api.data.chemical.material.info.MaterialIconSet;
import com.gregtechceu.gtceu.api.data.chemical.material.properties.DustProperty;
import com.gregtechceu.gtceu.api.data.chemical.material.properties.PropertyKey;
import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate;

/** 在材料注册窗口补齐结构与配方所需形态，让 GTCEu 的材料生成链统一生成物品、方块与配方。 */
public class GTLModularAddon implements IGTAddon {

    // 保留兼容字段，让材料注册与外部读取仍指向同一对象。
    public static GTRegistrate registrate;

    private static boolean materialsRegistered;

    @Override
    public GTRegistrate getRegistrate() {
        registrate = NanochipModContext.addonRegistrate();
        return registrate;
    }

    @Override
    public void initializeAddon() {
    }

    @Override
    public String addonModId() {
        return NanochipModContext.modId();
    }

    public static void onMaterialEvent(MaterialEvent event) {
        registerNacMaterials();
    }

    /** 兼容钩子与事件入口共用幂等实现，避免双入口重复登记材料。 */
    @Override
    public void registerMaterials() {
        NanochipModContext.logger().warn("[gtlmodular] IGTAddon.registerMaterials() 被调用了 —— 当前 GTCEu 版本正常应该走 MaterialEvent，"
                + "这条只是兼容路径（幂等，不会重复注册）。如果你看到它，请把日志给我。");
        registerNacMaterials();
    }

    /** 同步与幂等闸共同保护注册，避免重复调用向 GTCEu 写入同名材料。 */
    private static synchronized void registerNacMaterials() {
        if (materialsRegistered) {
            return;
        }
        materialsRegistered = true;

        new Material.Builder(GTCEu.id("enriched_holmium"))
                .color(0xE2E88F)
                .secondaryColor(0xA3A3A3)
                .iconSet(MaterialIconSet.METALLIC)
                .dust()
                .ingot()
                .blastTemp(3000)
                .flags(MaterialFlags.GENERATE_FRAME)
                .buildAndRegister();

        new Material.Builder(GTCEu.id("octiron"))
                .color(0x3C3A44)
                .secondaryColor(0x8A8A96)
                .iconSet(MaterialIconSet.METALLIC)
                .dust()
                .flags(MaterialFlags.GENERATE_FRAME)
                .buildAndRegister();

        new Material.Builder(GTCEu.id("superconductor_uhv_base"))
                .color(0x5292C5)
                .secondaryColor(0xE0E8F0)
                .iconSet(MaterialIconSet.METALLIC)
                .dust()
                .ingot()
                .blastTemp(10800)
                .flags(MaterialFlags.GENERATE_FRAME)
                .buildAndRegister();

        new Material.Builder(GTCEu.id("superconductor_uev_base"))
                .color(0x7E869F)
                .secondaryColor(0xEDEFF5)
                .iconSet(MaterialIconSet.METALLIC)
                .dust()
                .ingot()
                .blastTemp(11800)
                .flags(MaterialFlags.GENERATE_FRAME, MaterialFlags.FORCE_GENERATE_BLOCK)
                .buildAndRegister();

        NanochipModContext.logger().info("[gtlmodular] 已注册 4 味补充材料（GTCEu 自动生成框架方块）："
                + "enriched_holmium / octiron / superconductor_uhv_base / superconductor_uev_base");

        // 框架生成要求 DUST 属性和 GENERATE_FRAME 标志，需在生成方块前补齐。
        upgradeExistingMaterial("radox");
        upgradeExistingMaterial("kevlar");
        upgradeExistingMaterial("draconiumawakened");

        upgradeExistingMaterial("degenerate_rhenium");

        upgradeExistingMaterial("orichalcum");
        upgradeExistingMaterial("infuscolium");
        upgradeExistingMaterial("taranium");

        upgradeExistingMaterial("starmetal");

        // CC 螺栓需要对应真件，补齐杆和螺栓形态以避免不同材料共用替代物。
        upgradeSuperconductorBoltForm("enriched_naquadah_trinium_europium_duranide");
        upgradeSuperconductorBoltForm("ruthenium_trinium_americium_neutronate");
        upgradeSuperconductorBoltForm("enderite");
        upgradeSuperconductorBoltForm("echoite");
        upgradeSuperconductorBoltForm("legendarium");

        upgradeExistingMaterialFoil("rhodium_plated_palladium");

        com.gtlmodular.common.data.nanochip.NanochipMissingMaterials.registerMissingMaterials();
    }

    private static void upgradeExistingMaterial(String name) {
        if (GTCEuAPI.materialManager == null) {
            NanochipModContext.logger().warn("[gtlmodular] 想给 {} 补框架，但 GTCEu 的材料管理器还没就绪 ⇒ 跳过", name);
            return;
        }
        Material material = GTCEuAPI.materialManager.getMaterial(name);
        if (material == null) {
            NanochipModContext.logger().warn("[gtlmodular] 想给 {} 补框架，但本包**没有**这味材料 ⇒ 跳过"
                    + "（模块塔身会退回同材料实心块）；请把这条日志告诉施工者", name);
            return;
        }
        boolean addedDust = false;
        if (!material.hasProperty(PropertyKey.DUST)) {
            material.setProperty(PropertyKey.DUST, new DustProperty());
            addedDust = true;
        }
        boolean addedFlag = !material.hasFlags(MaterialFlags.GENERATE_FRAME);
        if (addedFlag) {
            material.addFlags(MaterialFlags.GENERATE_FRAME);
        }
        NanochipModContext.logger().info("[gtlmodular] 已给已有材料 {} 补框架生成条件（DUST={} GENERATE_FRAME={}）"
                + "⇒ GTCEu 会生成 gtceu:{}_frame", name, addedDust ? "新增" : "已有", addedFlag ? "新增" : "已有", name);
    }

    /** 配方引用的箔必须有生成标志，否则物品缺失会使配方无法加载。 */
    private static void upgradeExistingMaterialFoil(String name) {
        if (GTCEuAPI.materialManager == null) {
            NanochipModContext.logger().warn("[gtlmodular] 想给 {} 补箔，但 GTCEu 的材料管理器还没就绪 ⇒ 跳过", name);
            return;
        }
        Material material = GTCEuAPI.materialManager.getMaterial(name);
        if (material == null) {
            NanochipModContext.logger().warn("[gtlmodular] 想给 {} 补箔，但本包**没有**这味材料 ⇒ 跳过；请把这条日志告诉施工者", name);
            return;
        }
        boolean addedFlag = !material.hasFlags(MaterialFlags.GENERATE_FOIL);
        if (addedFlag) {
            material.addFlags(MaterialFlags.GENERATE_FOIL);
        }
        NanochipModContext.logger().info("[gtlmodular] 已给已有材料 {} 补箔生成条件（GENERATE_FOIL={}）"
                + "⇒ GTCEu 会生成 gtceu:{}_foil 并自动出箔配方", name, addedFlag ? "新增" : "已有", name);
    }

    private static void upgradeSuperconductorBoltForm(String name) {
        if (GTCEuAPI.materialManager == null) {
            NanochipModContext.logger().warn("[gtlmodular] 想给 {} 补杆/螺栓，但 GTCEu 的材料管理器还没就绪 ⇒ 跳过", name);
            return;
        }
        Material material = GTCEuAPI.materialManager.getMaterial(name);
        if (material == null) {
            NanochipModContext.logger().warn("[gtlmodular] 想给 {} 补杆/螺栓，但本包**没有**这味材料 ⇒ 跳过", name);
            return;
        }
        boolean addedRod = !material.hasFlags(MaterialFlags.GENERATE_ROD);
        boolean addedBolt = !material.hasFlags(MaterialFlags.GENERATE_BOLT_SCREW);
        if (addedRod) {
            material.addFlags(MaterialFlags.GENERATE_ROD);
        }
        if (addedBolt) {
            material.addFlags(MaterialFlags.GENERATE_BOLT_SCREW);
        }
        NanochipModContext.logger().info("[gtlmodular] 已给超导材料 {} 补形态（GENERATE_ROD={} GENERATE_BOLT_SCREW={}）"
                + "⇒ GTCEu 会生成 gtceu:{}_rod 与 gtceu:{}_bolt", name,
                addedRod ? "新增" : "已有", addedBolt ? "新增" : "已有", name, name);
    }
}
