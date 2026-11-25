package com.gtlmodular.common.data.fieldforge;

import com.gregtechceu.gtceu.api.GTCEuAPI;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.recipe.GTRecipeSerializer;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.common.data.GTSoundEntries;
import com.gtlmodular.GTLModular;
import com.lowdragmc.lowdraglib.gui.texture.ProgressTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeType;

/**
 * 力场压模零件工厂（{@code gtlmodular:field_extruder_factory}）<b>专属</b>的零部件加工配方表
 * —— {@code gtlmodular:field_extruder_parts}。
 *
 * <h2>与奈亚之触彻底无关（2026-11-25）</h2>
 * 这张表前身是 {@code com.gtlmodular.common.data.tentacle.ComponentPartsRecipeTypes}，
 * 注册在 {@code tentacle} 包下、id 叫 {@code gtlmodular:component_parts}，注释里写着
 * 「奈亚拉托提普之触的『零部件加工』配方表」。实际上奈亚机（{@code NyarlathotepsTentacleMachines}）
 * 早已不再挂这张表，唯一的使用者是本包这台力场压模零件工厂。
 * 本次把它整包搬到 {@code fieldforge}、改名、换 id，去掉与奈亚的一切命名与注释牵连：
 * <ul>
 *   <li>包：{@code data.tentacle} → {@code data.fieldforge}；</li>
 *   <li>类：{@code ComponentPartsRecipeTypes} → {@code FieldExtruderPartsRecipeTypes}；</li>
 *   <li>id：{@code gtlmodular:component_parts} → {@code gtlmodular:field_extruder_parts}
 *       （lang 键同步换成 {@code gtlmodular.field_extruder_parts}）。</li>
 * </ul>
 * 换 id 是安全的：自定义配方表不进存档，机器侧是 {@code .recipeTypes(...)} 代码引用，
 * 唯一影响是 JEI 类目 id 变化。
 *
 * <h2>配方由代码生成，不再是数据包 JSON</h2>
 * 原先 112 条配方是 {@code data/gtlmodular/recipes/component_parts/} 下的手写 JSON
 * （由已丢失的 KubeJS 脚本经 {@code scripts/gen_component_parts_recipes.py} 生成）。
 * 现改由 {@link FieldExtruderPartsRecipes} 在 GTCEu 的配方注册阶段
 * （{@code IGTAddon#addRecipes}）按材料注册表现场生成，覆盖全部有零件形态的材料，
 * 且输入/输出按材料量配平。JSON 已全部删除，避免又一次「Java + JSON 两份」。
 *
 * <h2>为什么自己 {@code new GTRecipeType}</h2>
 * GTCEu 1.4.4 的 {@code GTRecipeTypes.register(...)} 把 id 硬编码成 {@code gtceu:} 命名空间。
 * 这里把同一个 {@link ResourceLocation} 登记进三个注册表：
 * {@code BuiltInRegistries.RECIPE_TYPE}（RecipeManager 的 byType 索引）、
 * {@code BuiltInRegistries.RECIPE_SERIALIZER}（数据包 JSON 的 type 字段）、
 * {@code GTRegistries.RECIPE_TYPES}（{@code GTRecipe} codec）。
 */
public final class FieldExtruderPartsRecipeTypes {

    /**
     * 输出格数。零件形态一共 12 种（杆/板/螺栓/螺丝/环/齿轮/小齿轮/弹簧/小弹簧/箔/框/转子），
     * 12 是 3 的整数倍，界面按 3 列排布时正好排满 4 行，不会留下半行。
     */
    public static final int MAX_OUTPUTS = 12;

    /**
     * 流体输入槽数。末影珍珠 / 末影之眼 / echo shard 在本整合包里只有流体形态、没有物品形态，
     * 这几条配方以液体作为输入，因此需要 1 个流体输入槽。
     */
    public static final int MAX_FLUID_INPUTS = 1;

    private static final String GROUP_MULTIBLOCK = "multiblock";

    private static GTRecipeType type;

    private FieldExtruderPartsRecipeTypes() {
    }

    /** 配方表；未到注册窗口或注册失败时为 {@code null}。 */
    public static GTRecipeType RECIPES() {
        return type;
    }

    /** 配方类型 id（{@code gtlmodular:field_extruder_parts}）。 */
    public static ResourceLocation id() {
        return new ResourceLocation(GTLModular.MOD_ID, "field_extruder_parts");
    }

    /**
     * 接线：在 {@code @Mod} 构造里
     * {@code modBus.addGenericListener(GTRecipeType.class, FieldExtruderPartsRecipeTypes::onRecipeTypeRegisterEvent)}。
     *
     * <p>必须是具体事件类型的方法引用，不能写成 {@code IGenericEvent} 的 lambda ——
     * mod 总线只接受 IModBusEvent 的具体子类。</p>
     */
    public static void onRecipeTypeRegisterEvent(
            GTCEuAPI.RegisterEvent<ResourceLocation, GTRecipeType> event) {
        if (type != null) {
            GTLModular.LOGGER.info("[gtlmodular] FieldExtruderPartsRecipeTypes 重复初始化，忽略");
            return;
        }
        type = new GTRecipeType(id(), GROUP_MULTIBLOCK, new RecipeType<?>[0]);
        type.setEUIO(IO.IN);
        type.setMaxIOSize(1, MAX_OUTPUTS, MAX_FLUID_INPUTS, 0);
        type.setMaxTooltips(4);
        type.setXEIVisible(true);
        type.setProgressBar(GuiTextures.PROGRESS_BAR_ARROW, ProgressTexture.FillDirection.LEFT_TO_RIGHT);
        type.setSound(GTSoundEntries.ASSEMBLER);

        GTRegistries.register(BuiltInRegistries.RECIPE_TYPE, type.registryName, type);
        GTRegistries.register(BuiltInRegistries.RECIPE_SERIALIZER, type.registryName, new GTRecipeSerializer());
        GTRegistries.RECIPE_TYPES.register(type.registryName, type);

        GTLModular.LOGGER.info("[gtlmodular] 已注册配方表 {}（零部件加工，输出 {} 格）",
                type.registryName, MAX_OUTPUTS);
    }
}
