package com.gtlmodular.common.data.tentacle;

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
 * 奈亚拉托提普之触的「零部件加工」配方表（{@code gtlmodular:component_parts}）。
 *
 * <p>承载「一锭出全套零件」的批量零件配方：输入 1 种材料，输出 1~11 种零件。
 * 输出格固定 {@link #MAX_OUTPUTS} 个（3 的整数倍，界面按 3 列排布时刚好排满整行），
 * 产出种类少于该值的配方，靠后的格子留空。</p>
 *
 * <h2>为什么自己 {@code new GTRecipeType}</h2>
 * 与 {@code NanochipRecipeTypes} / {@code DisposableToolPrintTypes} 同因：GTCEu 1.4.4 的
 * {@code GTRecipeTypes.register(...)} 把 id 硬编码成 {@code gtceu:} 命名空间。这里把同一个
 * {@link ResourceLocation} 登记进三个注册表：{@code BuiltInRegistries.RECIPE_TYPE}
 * （RecipeManager 的 byType 索引）、{@code BuiltInRegistries.RECIPE_SERIALIZER}（数据包 JSON 的 type 字段）、
 * {@code GTRegistries.RECIPE_TYPES}（{@code GTRecipe} codec）。</p>
 */
public final class ComponentPartsRecipeTypes {

    /**
     * 输出格数。本整合包里一条零件配方最多产出 11 种零件；12 是 3 的整数倍，
     * 界面按 3 列排布时正好排满 4 行，不会留下半行。
     */
    public static final int MAX_OUTPUTS = 12;

    /**
     * 流体输入槽数。末影珍珠 / 末影之眼 / echo shard 在本整合包里只有流体形态、没有宝石物品，
     * 这三条配方以液体作为输入，因此需要 1 个流体输入槽。
     */
    public static final int MAX_FLUID_INPUTS = 1;

    private static final String GROUP_MULTIBLOCK = "multiblock";

    private static GTRecipeType type;

    private ComponentPartsRecipeTypes() {
    }

    /** 配方表；未到注册窗口或注册失败时为 {@code null}。 */
    public static GTRecipeType COMPONENT_PARTS() {
        return type;
    }

    /** 配方类型 id（{@code gtlmodular:component_parts}）。 */
    public static ResourceLocation id() {
        return new ResourceLocation(GTLModular.MOD_ID, "component_parts");
    }

    /**
     * 接线：在 {@code @Mod} 构造里
     * {@code modBus.addGenericListener(GTRecipeType.class, ComponentPartsRecipeTypes::onRecipeTypeRegisterEvent)}。
     *
     * <p>必须是具体事件类型的方法引用，不能写成 {@code IGenericEvent} 的 lambda ——
     * mod 总线只接受 IModBusEvent 的具体子类。</p>
     */
    public static void onRecipeTypeRegisterEvent(
            GTCEuAPI.RegisterEvent<ResourceLocation, GTRecipeType> event) {
        if (type != null) {
            GTLModular.LOGGER.info("[gtlmodular] ComponentPartsRecipeTypes 重复初始化，忽略");
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
