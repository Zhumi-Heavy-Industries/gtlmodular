package com.gtlmodular.common.machine.nanochip.registration;

import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate;
import com.gtlmodular.common.data.nanochip.NanochipModContext;
import com.gtlmodular.common.data.nanochip.NanochipMachines;
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
        event.enqueueWork(NanochipModuleRegistration::logPatternSizes);
    }

    /**
     * 一次性诊断：每台 NAC 多方块实际拿到的图案格数。JEI 预览就是按 definition 的图案画的，
     * 所以「矩阵的预览像复合体」时，这一行能直接分出是 definition 串了（数字=主机格数）
     * 还是 JEI 的 catalyst 映射串了（数字=模块自己的塔身格数）。定位完即可删除。
     */
    public static void logPatternSizes() {
        try {
            for (var def : NanochipMachines.moduleDefinitions()) {
                logPatternSize(def);
            }
            logPatternSize(NanochipMachines.NANOCHIP_ASSEMBLY_COMPLEX);
        } catch (RuntimeException | LinkageError failure) {
            NanochipModContext.logger().warn("[gtlmodular] NAC 图案格数诊断失败：{}", failure.toString());
        }
    }

    private static void logPatternSize(com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition def) {
        if (def == null) return;
        try {
            var pattern = def.getPatternFactory() == null ? null : def.getPatternFactory().get();
            int cells = -1;
            if (pattern != null) {
                int[] repetition = new int[pattern.aisleRepetitions.length];
                for (int i = 0; i < repetition.length; i++) {
                    repetition[i] = pattern.aisleRepetitions[i][0];
                }
                var preview = pattern.getPreview(repetition);
                cells = 0;
                for (var layer : preview) {
                    for (var row : layer) {
                        for (var cellInfo : row) {
                            if (cellInfo != null) cells++;
                        }
                    }
                }
            }
            NanochipModContext.logger().info("[gtlmodular] NAC 预览格数 {} = {}（重复页 {}）",
                    def.getId(), cells, pattern == null ? -1 : pattern.aisleRepetitions.length);
        } catch (RuntimeException | LinkageError failure) {
            NanochipModContext.logger().warn("[gtlmodular] NAC 图案格数 {} 构建失败：{}", def.getId(), failure.toString());
        }
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
