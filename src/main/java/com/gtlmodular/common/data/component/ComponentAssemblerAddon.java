package com.gtlmodular.common.data.component;

import com.gregtechceu.gtceu.api.addon.GTAddon;
import com.gregtechceu.gtceu.api.addon.IGTAddon;
import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate;
import com.gregtechceu.gtceu.common.data.GTItems;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.data.recipe.CustomTags;
import com.gtlmodular.GTLModular;
import com.gtlmodular.common.data.GTLModularMachines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.function.Consumer;

/**
 * ★ 2026-10-31：大型部件组装机的装配线配方走 GTCEu 原版「扫描研究」机制。
 *
 * <p>为什么不在 JSON 里写：装配线的 research 需要
 * {@code GTRecipeBuilder.scannerResearch(...)} 同时生成
 * <ul>
 *   <li>装配线配方上的 {@code ResearchCondition}；</li>
 *   <li>扫描仪侧的 research 配方（数据球写入 {@code assembly_line_research} NBT）。</li>
 * </ul>
 * 这套只由 {@code ResearchManager.createDefaultResearchRecipe} 在
 * {@code GTRecipeType.onRecipeBuild} 里生成，JSON 不会触发；所以这里用
 * {@link IGTAddon#addRecipes} 在 GTCEu 的配方注册阶段直接构建。
 *
 * <p>扫描目标 = 普通部件组装机（v1）；装配线本体仍需要另一台 v1 作为输入
 * ⇒ 要做大型机，需要两台 v1：一台拿去扫成数据球、一台进装配线。
 */
@GTAddon
public class ComponentAssemblerAddon implements IGTAddon {

    @Override
    public GTRegistrate getRegistrate() {
        return GTLModularMachines.REGISTRATE;
    }

    @Override
    public void initializeAddon() {
    }

    @Override
    public String addonModId() {
        return GTLModular.MOD_ID;
    }

    @Override
    public void addRecipes(Consumer<FinishedRecipe> provider) {
        ItemStack casingIv = new ItemStack(BuiltInRegistries.BLOCK.get(
                new ResourceLocation("gtlcore", "component_assembly_line_casing_iv")), 4);

        GTRecipeTypes.ASSEMBLY_LINE_RECIPES.recipeBuilder("large_component_assembler")
                // 普通部件组装机（同时也是扫描研究的对象，见下面的 scannerResearch）
                .inputItems(ComponentAssemblerMachines.COMPONENT_ASSEMBLER.asStack())
                // IV 部件装配线外壳
                .inputItems(casingIv)
                // IV 部件
                .inputItems(GTItems.CONVEYOR_MODULE_IV.get(), 4)
                .inputItems(GTItems.ROBOT_ARM_IV.get(), 8)
                .inputItems(GTItems.EMITTER_IV.get(), 2)
                .inputItems(GTItems.SENSOR_IV.get(), 2)
                .inputItems(CustomTags.IV_CIRCUITS, 4)
                // 替换后的材料
                .inputItems(ChemicalHelper.get(TagPrefix.rod, GTMaterials.HSSS, 8))
                .inputItems(ChemicalHelper.get(TagPrefix.bolt, GTMaterials.Neodymium, 32))
                .inputItems(ChemicalHelper.get(TagPrefix.plateDouble, GTMaterials.HSSG, 16))
                .outputItems(ComponentAssemblerMachines.LARGE_COMPONENT_ASSEMBLER.asStack())
                // ★ 原版研究机制：扫描 v1 → 数据球（默认 data_orb），装配线数据舱认它
                .scannerResearch(b -> b
                        .researchStack(ComponentAssemblerMachines.COMPONENT_ASSEMBLER.asStack())
                        .duration(1200)
                        .EUt(480))
                .duration(1600)
                .EUt(7680)
                .save(provider);
    }
}
