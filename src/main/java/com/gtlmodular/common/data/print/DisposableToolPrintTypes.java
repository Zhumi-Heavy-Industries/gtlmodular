package com.gtlmodular.common.data.print;

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
 * 「一次性工具 3D 打印」专用配方表（{@code gtlmodular:disposable_tool_printing}）。
 *
 * <h2>★ 2026-11-21：单方块 / 多方块合并为一张表</h2>
 * <p>此前是两张表：带 1~6 号编程电路的 {@code disposable_tool_printing}（多方块用）和不带电路的
 * {@code disposable_tool_printing_basic}（单方块用）。同一把工具因此在 JEI 里出现两份、
 * 两个分类，玩家看不出该用哪张。现在按队长口径合并成<b>一张</b>：
 * <b>单方块（LV/MV 打印机）与多方块（3D 打印阵列）共用 {@code disposable_tool_printing}</b>，
 * 配方统一为「一次性模具（不消耗）+ 72mB 材料流体 → 脆弱工具」，<b>不再带编程电路</b>
 * —— 材料本来就由流体区分，电路只是当初额外加的一维识别。</p>
 *
 * <h2>为什么自己 {@code new GTRecipeType} 而不是用 {@code GTRecipeTypes.register(...)}</h2>
 * 与 {@code NanochipRecipeTypes} 同因：GTCEu 1.4.4 的
 * {@code GTRecipeTypes.register(...)} 内部把 id 硬编码成 {@code gtceu:} 命名空间，
 * 而本表要求 id 为 {@code gtlmodular:disposable_tool_printing}。因此沿用本工程既有写法，
 * 把同一个 {@link ResourceLocation} 同时登记进三个注册表：
 * <ol>
 *   <li>{@code BuiltInRegistries.RECIPE_TYPE}（vanilla {@code RecipeManager} 建 byType 索引）；</li>
 *   <li>{@code BuiltInRegistries.RECIPE_SERIALIZER}（数据包 JSON 的 {@code "type"} 字段解析）；</li>
 *   <li>{@code GTRegistries.RECIPE_TYPES}（GTCEu 自己的 RL 注册表，{@code GTRecipe} codec 用）。</li>
 * </ol>
 *
 * <h2>注册时机</h2>
 * 只能挂在 {@code GTCEuAPI.RegisterEvent<ResourceLocation, GTRecipeType>} 窗口里
 * （{@code GTRecipeTypes.init()} 末尾、{@code RECIPE_TYPES.freeze()} 之前），
 * 由 {@code GTLModular} 构造函数里那条 generic listener 触发。
 *
 * <h2>尺寸</h2>
 * {@code setMaxIOSize(1, 1, 1, 0)}：1 个物品输入（模具，notConsumable）→ 1 个物品输出，
 * 1 个流体输入。不设的话 GTCEu 默认上限是 0，任何配方都会被判超尺寸。
 */
public final class DisposableToolPrintTypes {

    /** 配方表分组（与 GTCEu 多方块分组同名，JEI 里归在多方块配方类）。 */
    private static final String GROUP_MULTIBLOCK = "multiblock";

    private static final Map<ResourceLocation, GTRecipeType> INDEX = new LinkedHashMap<>();
    private static boolean registered;

    /**
     * 一次性工具 3D 打印：一次性模具（不消耗）+ 72mB 材料流体 → 脆弱工具。
     *
     * <p>★ 2026-11-21：单方块与多方块<b>共用本表</b>（原来的 basic 表已删除，
     * 见类注释）。物品输入上限随之收回 <b>1</b>（只有模具）。</p>
     */
    public static final GTRecipeType DISPOSABLE_TOOL_PRINTING =
            type("disposable_tool_printing", 1, 1, 1, 0)
                    .setProgressBar(GuiTextures.PROGRESS_BAR_ARROW, ProgressTexture.FillDirection.LEFT_TO_RIGHT)
                    .setSound(GTSoundEntries.ARC)
                    .setIconSupplier(() -> DisposableToolPrintItems.iconStack());

    private DisposableToolPrintTypes() {}

    /**
     * 在 GTCEu 的配方表注册窗口里登记本模组的表（幂等）。
     */
    public static void init() {
        if (registered) {
            return;
        }
        registered = true;
        for (GTRecipeType type : INDEX.values()) {
            registerOne(type);
        }
        GTLModular.LOGGER.info("[gtlmodular] 已注册一次性工具 3D 打印配方表：{}（共 {} 张）",
                DISPOSABLE_TOOL_PRINTING.registryName, INDEX.size());
    }

    /** 事件回调：{@code modBus.addGenericListener(GTRecipeType.class, DisposableToolPrintTypes::onRecipeTypeRegisterEvent)}。 */
    public static void onRecipeTypeRegisterEvent(
            GTCEuAPI.RegisterEvent<ResourceLocation, GTRecipeType> event) {
        init();
    }

    /** 配方表 id（{@code gtlmodular:disposable_tool_printing}）。 */
    public static ResourceLocation id(String path) {
        return new ResourceLocation(GTLModular.MOD_ID, path);
    }

    private static GTRecipeType type(String path, int itemIn, int itemOut, int fluidIn, int fluidOut) {
        GTRecipeType type = new GTRecipeType(id(path), GROUP_MULTIBLOCK, new RecipeType<?>[0]);
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
