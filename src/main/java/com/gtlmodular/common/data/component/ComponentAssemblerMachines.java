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
 * GTO「部件组装机」：基础机与大型机已合并为一台，靠 Add 3.6.2 的结构升级切换形态。
 *
 * <table border="1">
 *   <tr><th>结构等级</th><th>结构</th><th>线程</th><th>配方等级</th></tr>
 *   <tr><td>1 级</td><td>GTO 本体 7×5×5</td><td>4</td><td>≤ IV</td></tr>
 *   <tr><td>2 级</td><td>本体 + ASSEMBLY 扩展合体 29×6×13</td><td>8</td><td>≤ UV（允许激光仓）</td></tr>
 * </table>
 *
 * <p>二级解锁后需要在侧栏「结构升级」页激活，并把扩展段搭建完整。
 * 配方表仍按 id {@code gtceu:component_assembly_line} 现取，代码不 import gtlcore / gtladditions 的类。
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

    private ComponentAssemblerMachines() {}

    public static void register() {
        GTRecipeType recipeType = GTRegistries.RECIPE_TYPES.get(COMPONENT_ASSEMBLY_LINE_TYPE);
        if (recipeType == null) {
            GTLModular.LOGGER.warn("[gtlmodular] 跳过部件组装机：配方表 {} 未注册",
                    COMPONENT_ASSEMBLY_LINE_TYPE);
            return;
        }

        COMPONENT_ASSEMBLER = GTLModularMachines.REGISTRATE
                .multiblock("component_assembler", holder -> new ComponentAssemblerMachine(holder,
                        () -> ComponentAssemblerStructures.buildFull(COMPONENT_ASSEMBLER),
                        ComponentAssemblerStructures::fullSlices,
                        ComponentAssemblerStructures::isTier2Cell))
                .rotationState(RotationState.ALL)
                .tier(GTValues.IV)
                .recipeType(recipeType)
                .recipeModifier(GTRecipeModifiers.ELECTRIC_OVERCLOCK
                        .apply(OverclockingLogic.NON_PERFECT_OVERCLOCK))
                .appearanceBlock(GTBlocks.CASING_STEEL_SOLID)
                .pattern(ComponentAssemblerStructures::buildFull)
                .workableCasingRenderer(
                        CASING_TEXTURE,
                        new ResourceLocation(GTLModular.MOD_ID, "block/multiblock/component_assembler"))
                .langValue("部件组装机")
                .tooltips(java.util.stream.IntStream.range(0, 4)
                        .mapToObj(i -> Component.translatable("gtlmodular.component_assembler.tooltip." + i))
                        .toArray(Component[]::new))
                .register();

        GTLModular.LOGGER.info("[gtlmodular] 已注册部件组装机：{}（1级 IV/4线程 → 2级 UV/8线程/激光仓）",
                COMPONENT_ASSEMBLER.getId());
    }
}
