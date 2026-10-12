package com.gtlmodular.client;

import com.gtlmodular.common.data.nanochip.NanochipModContext;
import com.gregtechceu.gtceu.api.GTCEuAPI;
import com.gregtechceu.gtceu.api.data.chemical.material.IMaterialRegistryManager;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gtlmodular.common.data.nanochip.NanochipCircuitComponents;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** 自注册 CC 物品不会自动继承材料染色；仅为有材料映射的灰度底层登记颜色处理器。 */
public final class CcItemColorSetup {

    private static final String MATERIAL_REF_PREFIX = "mat:";

    private static final int NO_TINT = -1;

    private static final Map<Item, Material> MATERIAL_BY_ITEM = new IdentityHashMap<>();

    private static final List<String> UNRESOLVED = new ArrayList<>();

    private static boolean resolved;

    private CcItemColorSetup() {
    }

    @SubscribeEvent
    public static void onRegisterItemColors(RegisterColorHandlersEvent.Item event) {
        resolveMaterials();
        if (MATERIAL_BY_ITEM.isEmpty()) {
            return;
        }
        Item[] items = MATERIAL_BY_ITEM.keySet().toArray(new Item[0]);
        event.register(CcItemColorSetup::colorOf, items);
        NanochipModContext.logger().info("[gtlmodular] CC ItemColor: 已为 {} 件「灰度材料母版」贴图的 CC 件注册物品颜色"
                + "（layer0 = 材料色，layer1 = 原样；其余 CC 件一律不染）。", items.length);
    }

    /** 只染底层，避免共用的 CC 印章随材料次色变化。 */
    private static int colorOf(ItemStack stack, int tintIndex) {
        if (tintIndex != 0) {
            return NO_TINT;
        }
        Material material = MATERIAL_BY_ITEM.get(stack.getItem());
        return material == null ? NO_TINT : material.getLayerARGB(0);
    }

    private static void resolveMaterials() {
        if (resolved) {
            return;
        }
        resolved = true;
        // 材料表依赖 CC 注册条目，必须先完成其静态初始化。
        IMaterialRegistryManager manager = GTCEuAPI.materialManager;
        if (manager == null) {
            NanochipModContext.logger().warn("[gtlmodular] CC ItemColor: GTCEuAPI.materialManager 还是 null（材料注册表未就绪）"
                    + " ⇒ 本轮不注册任何物品颜色；CC 件维持现状（全白），不会误染。");
            return;
        }
        int byRegistry = 0;
        int byRecipe = 0;
        for (NanochipCircuitComponents.Entry entry : NanochipCircuitComponents.all()) {
            Material material = materialOfEntry(manager, entry);
            if (material == null) {
                continue;
            }
            Item item = entry.item();
            if (item == null) {
                UNRESOLVED.add(entry.itemId + "(物品未注册)");
                continue;
            }
            MATERIAL_BY_ITEM.put(item, material);
            if (entry.realRef != null && entry.realRef.startsWith(MATERIAL_REF_PREFIX)) {
                byRegistry++;
            } else {
                byRecipe++;
            }
        }
        NanochipModContext.logger().info("[gtlmodular] CC ItemColor: 材料现推完成 —— 注册行 {} 件 + 配方图生成表 {} 件 = {} 件"
                + "（只染 layer0；其余 CC 件一律不碰）。", byRegistry, byRecipe, MATERIAL_BY_ITEM.size());
        if (!UNRESOLVED.isEmpty()) {
            NanochipModContext.logger().warn("[gtlmodular] CC ItemColor: {} 件**本轮打算染但没染成**（不会返回白/黑，保持原样）：{}",
                    UNRESOLVED.size(), String.join(", ", UNRESOLVED));
        }
    }

    private static Material materialOfEntry(IMaterialRegistryManager manager, NanochipCircuitComponents.Entry entry) {
        String realRef = entry.realRef;
        if (realRef != null && realRef.startsWith(MATERIAL_REF_PREFIX)) {
            Material byRegistry = materialOf(manager, realRef);
            if (byRegistry == null) {
                UNRESOLVED.add(entry.itemId + "(" + realRef + "：材料查不到)");
            }
            return byRegistry;
        }
        String byRecipeName = CcMaterialByItem.MATERIALS.get(entry.itemId);
        if (byRecipeName == null) {
            return null;
        }
        Material byRecipe = manager.getMaterial(byRecipeName);
        if (byRecipe == null) {
            UNRESOLVED.add(entry.itemId + "(配方表材料查不到：" + byRecipeName + ")");
        }
        return byRecipe;
    }

    private static Material materialOf(IMaterialRegistryManager manager, String realRef) {
        String body = realRef.substring(MATERIAL_REF_PREFIX.length());
        int separator = body.indexOf(':');
        if (separator <= 0) {
            return null;
        }
        String materialName = body.substring(0, separator);
        return manager.getMaterial(materialName);
    }
}
