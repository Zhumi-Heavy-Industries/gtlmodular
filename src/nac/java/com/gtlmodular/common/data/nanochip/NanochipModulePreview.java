package com.gtlmodular.common.data.nanochip;

import com.gtlmodular.common.diag.SwallowedErrors;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.gtlcore.gtlcore.utils.datastructure.ModuleRenderInfo;

import java.util.ArrayList;
import java.util.List;

public final class NanochipModulePreview {

    // 预览仅依赖图案常量，跨主机共享可避免反复构造重型预览数据。
    private static volatile List<ModuleRenderInfo> NAC_MODULES;

    private NanochipModulePreview() {
    }

    public static List<ModuleRenderInfo> nacModules(BlockPos hostPos) {
        if (hostPos == null) {
            return List.of();
        }
        List<ModuleRenderInfo> cached = NAC_MODULES;
        if (cached == null) {
            synchronized (NanochipModulePreview.class) {
                if (NAC_MODULES == null) {
                    List<ModuleRenderInfo> built;
                    try {
                        built = buildNac();
                    } catch (Throwable t) {
                        SwallowedErrors.report("NanochipModulePreview#nacModules", t);
                        NanochipModContext.logger().debug("[gtlmodular] 模块预览: NAC 模块表构建失败，返回空表: {}", t.toString());
                        built = List.of();
                    }
                    NAC_MODULES = built;
                    cached = built;
                } else {
                    cached = NAC_MODULES;
                }
            }
        }
        ModulePreviewSupport.logOnce("nac@" + hostPos.asLong(), "纳米芯片组装复合体", hostPos, cached);
        return cached;
    }

    private static List<ModuleRenderInfo> buildNac() {
        List<NanochipComplexPattern.ModuleControllerSlot> slots =
                NanochipComplexPattern.moduleControllerOffsets();
        if (slots.isEmpty()) {
            return List.of();
        }

        MultiblockMachineDefinition anyDef = firstNacDefinition();
        if (anyDef == null) {
            return List.of();
        }
        List<ModuleRenderInfo> out = new ArrayList<>(slots.size());
        for (NanochipComplexPattern.ModuleControllerSlot slot : slots) {
            MultiblockMachineDefinition def = nacDefinition(slot.moduleId());
            if (def == null) {
                // 缺少一种模块定义时仍要保留该基座的预览位置。
                def = anyDef;
            }

            out.add(new ModuleRenderInfo(slot.offset(), Direction.NORTH, Direction.UP,
                    slot.front(), Direction.UP, def));
        }
        return List.copyOf(out);
    }

    private static MultiblockMachineDefinition firstNacDefinition() {
        return NanochipMachines.moduleDefinitions().stream().filter(java.util.Objects::nonNull).findFirst().orElse(null);
    }

    private static MultiblockMachineDefinition nacDefinition(String moduleId) {
        if (moduleId == null) return null;
        var id = com.gtlmodular.common.machine.nanochip.registration.NanochipModules.parseId(moduleId);
        var definition = NanochipMachines.moduleDefinition(id);
        return definition != null ? definition : ModulePreviewSupport.lookupDefinition(id.getNamespace(), id.getPath());
    }
}
