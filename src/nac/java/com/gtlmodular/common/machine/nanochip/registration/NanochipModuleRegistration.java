package com.gtlmodular.common.machine.nanochip.registration;

import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate;
import com.gtlmodular.common.data.nanochip.NanochipModContext;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** 外部命名空间的 Registrate 也挂到注入的产品总线，避免读取机器事件的临时加载上下文。 */
public final class NanochipModuleRegistration {
    private static final Map<String, GTRegistrate> registrates = new LinkedHashMap<>();
    private static IEventBus modBus;

    private NanochipModuleRegistration() {}

    public static synchronized void bind(IEventBus bus) {
        Objects.requireNonNull(bus, "NAC registration event bus");
        if (modBus == bus) return;
        if (modBus != null) throw new IllegalStateException("NAC registration event bus already bound");
        modBus = bus;
        bus.addListener(NanochipModuleRegistration::commonSetup);
    }

    public static GTRegistrate registrate(NanochipModuleSpec spec) {
        String namespace = spec.id().getNamespace();
        if (namespace.equals(NanochipModContext.modId())) return NanochipModContext.machineRegistrate();
        return registrates.computeIfAbsent(namespace, key -> {
            GTRegistrate result = GTRegistrate.create(key);
            result.registerEventListeners(Objects.requireNonNull(modBus, "NAC registration event bus"));
            return result;
        });
    }

    private static void commonSetup(FMLCommonSetupEvent event) {
        // GT 机器窗口早于 Forge 流体和方块落地；在全部注册完成后验证，不能误拒绝合法的延迟条目。
        event.enqueueWork(NanochipModuleRegistration::validateResources);
    }

    public static void validateResources() {
        for (NanochipModuleSpec spec : NanochipModules.all()) {
            if (spec.legacyKind() != null) continue;
            if (spec.frame() != null && (!ForgeRegistries.BLOCKS.containsKey(spec.frame())
                    || ForgeRegistries.BLOCKS.getValue(spec.frame()) == Blocks.AIR)) {
                throw new IllegalStateException("NAC module frame block is not registered: " + spec.id() + " -> " + spec.frame());
            }
            if (spec.hasUpkeep()) {
                ResourceLocation fluid = new ResourceLocation(spec.upkeepFluidId());
                if (!ForgeRegistries.FLUIDS.containsKey(fluid) || ForgeRegistries.FLUIDS.getValue(fluid) == Fluids.EMPTY) {
                    throw new IllegalStateException("NAC module upkeep fluid is not registered: " + spec.id() + " -> " + fluid);
                }
            }
        }
    }
}
