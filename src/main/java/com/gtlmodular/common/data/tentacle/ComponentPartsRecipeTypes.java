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
 * 奈亚拉托提普之触的「零部件加工」配方表。
 *
 * <p>这张表只承载「一锭出全套零件」的批量零件配方（杆/板/螺栓/螺丝/环/齿轮/小齿轮/转子/弹簧/小弹簧/机框/箔）。
 * 之所以不复用 gtlcore 的 {@code gtlcore:component_assembly_line}，是因为那张表同时被部件组装机使用，
 * 零件配方挂上去会连部件组装机一起被改写；本表只挂给奈亚机，互不干扰。</p>
 *
 * <h2>为什么自己 {@code new GTRecipeType}</h2>
 * 与 {@code NanochipRecipeTypes} / {@code DisposableToolPrintTypes} 同因：GTCEu 1.4.4 的
 * {@code GTRecipeTypes.register(...)} 把 id 硬编码成 {@code gtceu:} 命名空间，
 * 而本表要求 {@code gtlmodular:component_parts}。因此把同一个 {@link ResourceLocation}
 * 登记进三个注册表：{@code BuiltInRegistries.RECIPE_TYPE}（RecipeManager 的 byType 索引）、
 * {@code BuiltInRegistries.RECIPE_SERIALIZER}（数据包 JSON 的 type 字段）、
 * {@code GTRegistries.RECIPE_TYPES}（{@code GTRecipe} codec）。</p>
 *
 * <h2>尺寸</h2>
 * {@code setMaxIOSize(1, 16, 0, 0)}：输入 1 种材料（锭/宝石），输出最多 12 种零件（留 4 格余量），
 * 无流体。不设的话 GTCEu 默认上限是 0，任何配方都会被判超尺寸。
 */
public final class ComponentPartsRecipeTypes {

    /** 单条零件配方最多产出的零件种类数（最多的一档是 11 种）。 */
    private static final int MAX_OUTPUT_KINDS = 16;

    private static final String GROUP_MULTIBLOCK = "multiblock";

    private static GTRecipeType type;

    private ComponentPartsRecipeTypes() {
    }

    /** 配方表；未到注册窗口或注册失败时为 {@code null}。 */
    public static GTRecipeType COMPONENT_PARTS() {
        return type;
    }

    /** 配方类型 id（{@code gtlmodular:component_parts}）。 */
    public static ResourceLocation id(String path) {
        return new ResourceLocation(GTLModular.MOD_ID, path);
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
        type = new GTRecipeType(id("component_parts"), GROUP_MULTIBLOCK, new RecipeType<?>[0]);
        type.setEUIO(IO.IN);
        type.setMaxIOSize(1, MAX_OUTPUT_KINDS, 0, 0);
        type.setMaxTooltips(4);
        type.setXEIVisible(true);
        type.setProgressBar(GuiTextures.PROGRESS_BAR_ARROW, ProgressTexture.FillDirection.LEFT_TO_RIGHT);
        type.setSound(GTSoundEntries.ASSEMBLER);

        // ① vanilla/Forge 的 RecipeType 注册表（RecipeManager 建 byType 索引）
        GTRegistries.register(BuiltInRegistries.RECIPE_TYPE, type.registryName, type);
        // ② vanilla/Forge 的 RecipeSerializer 注册表（数据包 JSON 的 "type" 字段）
        GTRegistries.register(BuiltInRegistries.RECIPE_SERIALIZER, type.registryName, new GTRecipeSerializer());
        // ③ GTCEu 自己的 RL 注册表（GTRecipe 的 codec 解析 "type"）
        GTRegistries.RECIPE_TYPES.register(type.registryName, type);

        GTLModular.LOGGER.info("[gtlmodular] 已注册配方表 {}（零部件加工，输出上限 {} 种）",
                type.registryName, MAX_OUTPUT_KINDS);
    }
}
