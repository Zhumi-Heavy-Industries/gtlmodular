package com.gtlmodular.common.data.component;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.OverclockingLogic;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.common.data.GTBlocks;
import com.gregtechceu.gtceu.common.data.GTRecipeModifiers;
import com.gtlmodular.GTLModular;
import com.gtlmodular.common.data.GTLModularMachines;
import com.gtlmodular.common.machine.component.ComponentAssemblerMachine;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * GTO「部件组装机」的两台静态多方块（不做 GTO 的可选模块/原地升级）。
 *
 * <table border="1">
 *   <tr><th>id</th><th>结构</th><th>线程</th><th>配方等级</th></tr>
 *   <tr><td>{@code component_assembler}</td><td>GTO 本体 7×5×5</td><td>4</td><td>≤ IV</td></tr>
 *   <tr><td>{@code large_component_assembler}</td><td>本体 + ASSEMBLY 扩展合体 29×6×18</td><td>8</td><td>≤ UV（允许激光仓）</td></tr>
 * </table>
 *
 * <p>配方表仍按 id {@code gtceu:component_assembly_line} 现取，代码里不 import gtlcore / gtladditions 的类；
 * 结构里需要的 gtlcore 方块（CAL 外壳、多功能机壳、处理机壳、抗氧化哈氏合金机壳）也按 id 取，取不到时回落钢制机壳。
 *
 * <p>注册调用点：{@link GTLModularMachines#init()}。
 */
public final class ComponentAssemblerMachines {

    private static final ResourceLocation COMPONENT_ASSEMBLY_LINE_TYPE =
            new ResourceLocation("gtceu", "component_assembly_line");

    /** 钢制机壳底材贴图（GTO 原结构的主壳）。 */
    private static final ResourceLocation CASING_TEXTURE =
            new ResourceLocation("gtceu", "block/casings/solid/machine_casing_solid_steel");

    public static MultiblockMachineDefinition COMPONENT_ASSEMBLER;
    public static MultiblockMachineDefinition LARGE_COMPONENT_ASSEMBLER;

    private ComponentAssemblerMachines() {}

    public static void register() {
        GTRecipeType recipeType = GTRegistries.RECIPE_TYPES.get(COMPONENT_ASSEMBLY_LINE_TYPE);
        if (recipeType == null) {
            GTLModular.LOGGER.warn("[gtlmodular] 跳过部件组装机：配方表 {} 未注册",
                    COMPONENT_ASSEMBLY_LINE_TYPE);
            return;
        }

        COMPONENT_ASSEMBLER = GTLModularMachines.REGISTRATE
                .multiblock("component_assembler", holder -> new ComponentAssemblerMachine(holder, 4, GTValues.IV))
                .rotationState(RotationState.ALL)
                .tier(GTValues.IV)
                .recipeType(recipeType)
                .recipeModifier(GTRecipeModifiers.ELECTRIC_OVERCLOCK
                        .apply(OverclockingLogic.NON_PERFECT_OVERCLOCK))
                .appearanceBlock(GTBlocks.CASING_STEEL_SOLID)
                .pattern(ComponentAssemblerStructures::buildBase)
                .workableCasingRenderer(
                        CASING_TEXTURE,
                        new ResourceLocation(GTLModular.MOD_ID, "block/multiblock/component_assembler"))
                .langValue("部件组装机")
                .tooltips(
                        Component.translatable("gtlmodular.component_assembler.tooltip.0"),
                        Component.translatable("gtlmodular.component_assembler.tooltip.1"),
                        Component.translatable("gtlmodular.component_assembler.tooltip.2"),
                        Component.translatable("gtlmodular.component_assembler.tooltip.3"))
                .register();

        LARGE_COMPONENT_ASSEMBLER = GTLModularMachines.REGISTRATE
                .multiblock("large_component_assembler",
                        holder -> new ComponentAssemblerMachine(holder, 8, GTValues.UV))
                .rotationState(RotationState.ALL)
                .tier(GTValues.UV)
                .recipeType(recipeType)
                .recipeModifier(GTRecipeModifiers.ELECTRIC_OVERCLOCK
                        .apply(OverclockingLogic.NON_PERFECT_OVERCLOCK))
                .appearanceBlock(GTBlocks.CASING_STEEL_SOLID)
                .pattern(ComponentAssemblerStructures::buildFull)
                .workableCasingRenderer(
                        CASING_TEXTURE,
                        new ResourceLocation(GTLModular.MOD_ID, "block/multiblock/large_component_assembler"))
                .langValue("大型部件组装机")
                .tooltips(
                        Component.translatable("gtlmodular.large_component_assembler.tooltip.0"),
                        Component.translatable("gtlmodular.large_component_assembler.tooltip.1"),
                        Component.translatable("gtlmodular.large_component_assembler.tooltip.2"),
                        Component.translatable("gtlmodular.large_component_assembler.tooltip.3"),
                        Component.translatable("gtlmodular.large_component_assembler.tooltip.4"))
                .register();

        GTLModular.LOGGER.info("[gtlmodular] 已注册部件组装机：{}(IV/4线程) / {}(UV/8线程/激光仓)",
                COMPONENT_ASSEMBLER.getId(), LARGE_COMPONENT_ASSEMBLER.getId());
    }
}
