package com.gtlmodular.gametest.resource;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gtlmodular.common.machine.nanochip.registration.NanochipModules;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;

/** 服务端不创建客户端 renderer，按注册族的渲染契约检查其资源输入。 */
final class MachineAssets {
    static void check(ResourceAudit audit, ResourceLocation id, MachineDefinition definition) throws IOException {
        audit.model("ldlib:block/renderer_model", Map.of());
        var spec = NanochipModules.all().stream().filter(module -> module.id().equals(id)).findFirst();
        if (spec.isPresent()) {
            audit.workable(spec.get().casing(), spec.get().overlay());
            return;
        }
        var casing = new ResourceLocation("gtlmodular:block/casings/nanochip_mesh_interface_casing");
        switch (id.getPath()) {
            case "nanochip_assembly_complex" -> audit.workable(casing,
                    new ResourceLocation("gtlmodular:block/multiblock/nanochip_assembly_complex"));
            case "nanochip_vacuum_input_hatch", "nanochip_vacuum_output_hatch" -> {
                String direction = id.getPath().contains("input") ? "input" : "output";
                audit.model("gtceu:block/machine/hull_machine", hull(casing.toString(), casing.toString(), casing.toString()));
                audit.model("gtlmodular:block/machine/part/nanochip_vacuum_" + direction, Map.of());
                audit.model("gtlmodular:block/machine/part/nanochip_vacuum_" + direction + "_active", Map.of());
            }
            case "nanochip_vacuum_pipe" -> audit.model("gtlmodular:block/machine/part/vacuum_pipe", Map.of());
            default -> fullProduct(audit, id, definition);
        }
    }

    private static void fullProduct(ResourceAudit audit, ResourceLocation id, MachineDefinition definition) throws IOException {
        if (!id.getNamespace().equals("gtlmodular")) throw new IOException("未覆盖的动态机器 " + id);
        if (id.getPath().endsWith("_me_pattern_assembly")) {
            String voltage = "gtceu:block/casings/voltage/" + GTValues.VN[definition.getTier()].toLowerCase(Locale.ROOT);
            Map<String, String> hull = id.getPath().equals("super_me_pattern_assembly")
                    ? hull("gtladditions:block/casings/infinity_input_dual_hatch_casing",
                           "gtladditions:block/casings/infinity_input_dual_hatch_casing",
                           "gtladditions:block/casings/infinity_input_dual_hatch_casing")
                    : hull(voltage + "/bottom", voltage + "/top", voltage + "/side");
            audit.model("gtceu:block/machine/hull_machine", hull);
            audit.model("gtceu:block/machine/part/me_pattern_buffer", Map.of());
            return;
        }
        switch (id.getPath()) {
            case "molten_module_host" -> audit.workable(new ResourceLocation("gtlcore:block/casings/iridium_casing"),
                    new ResourceLocation("gtceu:block/multiblock/data_bank"));
            case "molten_blast_module" -> audit.workable(new ResourceLocation("gtceu:block/casings/solid/machine_casing_heatproof"),
                    new ResourceLocation("gtceu:block/multiblock/electric_blast_furnace"));
            case "molten_stripper_module" -> audit.workable(new ResourceLocation("gtlcore:block/casings/hyper_mechanical_casing"),
                    new ResourceLocation("gtceu:block/multiblock/fusion_reactor"));
            case "molten_space_ore_module" -> audit.workable(new ResourceLocation("gtlcore:block/space_elevator_mechanical_casing"),
                    new ResourceLocation("gtceu:block/multiblock/data_bank"));
            case "buffered_me_output_buffer" -> {
                String voltage = "gtceu:block/casings/voltage/" + GTValues.VN[definition.getTier()].toLowerCase(Locale.ROOT);
                audit.model("gtceu:block/machine/hull_machine",
                        hull(voltage + "/bottom", voltage + "/top", voltage + "/side"));
                audit.model("gtceu:block/machine/part/me_extended_export_buffer", Map.of());
            }
            default -> throw new IOException("未覆盖的动态机器 " + id);
        }
    }

    private static Map<String, String> hull(String bottom, String top, String side) {
        return Map.of("bottom", bottom, "top", top, "side", side);
    }
}
