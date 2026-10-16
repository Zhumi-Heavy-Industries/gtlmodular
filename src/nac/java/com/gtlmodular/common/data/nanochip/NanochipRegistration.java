package com.gtlmodular.common.data.nanochip;

import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gtlmodular.common.data.GTLModularAddon;
import com.gtlmodular.common.data.NanochipBlocks;
import net.minecraftforge.eventbus.api.IEventBus;

public final class NanochipRegistration {

    private NanochipRegistration() {
    }

    public static void registerRecipeTypes(IEventBus modBus) {
        com.gtlmodular.common.machine.nanochip.registration.NanochipModuleRegistration.bind(modBus);
        modBus.addGenericListener(GTRecipeType.class, NanochipRecipeTypes::onRecipeTypeRegisterEvent);
    }

    public static void registerMaterials(IEventBus modBus) {
        modBus.addListener(GTLModularAddon::onMaterialEvent);
        GTLModularAddon.registrate = NanochipModContext.addonRegistrate();
    }

    // 这些条目必须在 Forge 的方块、物品 RegisterEvent 之前创建。
    public static void initBlocks() {
        NanochipBlocks.init();
        NanochipCircuitComponents.init();
        NanochipMissingItems.init();
        NanochipItems.init();
        NanochipMissingWafers.init();
    }

    // 机器只能在 GTCEu 的 MACHINES 注册表冻结之前写入。
    public static void registerMachines() {
        NanochipMachines.register();
    }
}
