package com.gtlmodular.common.data.woodfactory;

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

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 「集成木质生物质热解」配方表 —— GTO {@code gtceu:wood_distillation}（GTO 用
 * {@code RecipeTypeRegisterUtils.register("wood_distillation", "集成木质生物质热解", "multiblock")}
 * 注册，落进 {@code gtceu:} 命名空间）的等价表。
 *
 * <h2>照搬的四项参数（来自 GTO {@code GTORecipeTypes.java:243}）</h2>
 * <pre>
 * new RecipeType: id = wood_distillation, 中文名 = 集成木质生物质热解, group = multiblock
 * .setEUIO(IO.IN)                                  —— 只能耗电
 * .setMaxIOSize(2, 1, 1, 20)                       —— 物品 2 进 / 1 出；流体 1 进 / 20 出
 * .setProgressBar(GuiTextures.PROGRESS_BAR_ARROW, LEFT_TO_RIGHT)
 * .setSound(GTSoundEntries.BATH)
 * </pre>
 * 「流体 20 出」是这张表的硬指标：氮气路线一条配方就产 <b>15 种</b>副产流体
 * （苯/二甲苯/杂酚油/CO₂/甲醇/CO/苯酚/甲苯/乙酸/甲烷/丙酮/乙烯/氢气/乙酸甲酯/乙醇），
 * 输出上限写小了配方会被判超尺寸。
 *
 * <h2>为什么自己 {@code new GTRecipeType} 而不是 {@code GTRecipeTypes.register(...)}</h2>
 * 与 {@code NanochipRecipeTypes} / {@code DisposableToolPrintTypes} 同因：GTCEu 1.4.4 的
 * {@code GTRecipeTypes.register(...)} 把 id 硬编码成 {@code gtceu:} 命名空间，
 * 而本表要是 {@code gtlmodular:wood_distillation}（本模组自己的表，不与 GTO 抢 id）。
 * 同一个 {@link ResourceLocation} 同时登记进三个注册表：
 * <ol>
 *   <li>{@code BuiltInRegistries.RECIPE_TYPE}（vanilla {@code RecipeManager} 建 byType 索引）；</li>
 *   <li>{@code BuiltInRegistries.RECIPE_SERIALIZER}（数据包 JSON 的 {@code "type"} 字段解析）；</li>
 *   <li>{@code GTRegistries.RECIPE_TYPES}（GTCEu 自己的 RL 注册表，{@code GTRecipe} codec 用）。</li>
 * </ol>
 *
 * <h2>注册时机</h2>
 * 只能挂在 {@code GTCEuAPI.RegisterEvent<ResourceLocation, GTRecipeType>} 窗口里
 * （{@code GTRecipeTypes.init()} 末尾、{@code RECIPE_TYPES.freeze()} 之前），
 * 由 {@code GTLModular} 构造函数里那条 generic listener 触发 —— 见
 * {@code GTLModular.java} 里 {@code DisposableToolPrintTypes::onRecipeTypeRegisterEvent}
 * 旁边那一行。机器注册（{@link WoodFactoryMachines#register()}）在更晚的
 * {@code MachineDefinition} 窗口里按 id 现取本表。
 */
public final class WoodFactoryRecipeTypes {

    /** 配方表分组（与 GTCEu 多方块分组同名，JEI 里归在多方块配方类）。 */
    private static final String GROUP_MULTIBLOCK = "multiblock";

    /** 配方表 id：{@code gtlmodular:wood_distillation}。 */
    public static final ResourceLocation WOOD_DISTILLATION_ID =
            new ResourceLocation(GTLModular.MOD_ID, "wood_distillation");

    private static final Map<ResourceLocation, GTRecipeType> INDEX = new LinkedHashMap<>();
    private static boolean registered;

    /**
     * 集成木质生物质热解：木质纤维（{@code #minecraft:logs} 16 个）+ 1 种催化/反应流体 →
     * 灰烬 + 最多 15 种化工流体。
     */
    public static final GTRecipeType WOOD_DISTILLATION =
            type("wood_distillation", 2, 1, 1, 20)
                    .setEUIO(IO.IN)
                    .setProgressBar(GuiTextures.PROGRESS_BAR_ARROW,
                            ProgressTexture.FillDirection.LEFT_TO_RIGHT)
                    .setSound(GTSoundEntries.BATH);

    private WoodFactoryRecipeTypes() {
    }

    /** 在 GTCEu 的配方表注册窗口里登记本模组的表（幂等）。 */
    public static void init() {
        if (registered) {
            return;
        }
        registered = true;
        for (GTRecipeType type : INDEX.values()) {
            registerOne(type);
        }
        GTLModular.LOGGER.info("[gtlmodular] 已注册集成木质生物质热解配方表：{}（共 {} 张）",
                WOOD_DISTILLATION.registryName, INDEX.size());
    }

    /** 事件回调：{@code modBus.addGenericListener(GTRecipeType.class, WoodFactoryRecipeTypes::onRecipeTypeRegisterEvent)}。 */
    public static void onRecipeTypeRegisterEvent(
            GTCEuAPI.RegisterEvent<ResourceLocation, GTRecipeType> event) {
        init();
    }

    private static GTRecipeType type(String path, int itemIn, int itemOut, int fluidIn, int fluidOut) {
        GTRecipeType type = new GTRecipeType(new ResourceLocation(GTLModular.MOD_ID, path),
                GROUP_MULTIBLOCK, new RecipeType<?>[0]);
        type.setEUIO(IO.IN);
        type.setMaxIOSize(itemIn, itemOut, fluidIn, fluidOut);
        type.setMaxTooltips(4);
        type.setXEIVisible(true);
        INDEX.put(type.registryName, type);
        return type;
    }

    private static void registerOne(GTRecipeType type) {
        GTRegistries.register(BuiltInRegistries.RECIPE_TYPE, type.registryName, type);
        GTRegistries.register(BuiltInRegistries.RECIPE_SERIALIZER, type.registryName,
                new GTRecipeSerializer());
        GTRegistries.RECIPE_TYPES.register(type.registryName, type);
    }
}
