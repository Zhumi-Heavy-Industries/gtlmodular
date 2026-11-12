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
 * 奈亚拉托提普之触的「零部件加工」配方表族（{@code gtlmodular:component_parts_1} … {@code _11}）。
 *
 * <p>一张表只放「产出 N 种零件」的配方，N 从 1 到 {@link #MAX_KINDS}。这样做的原因：
 * GTCEu 的配方界面按 {@code GTRecipeType.maxOutputs} <b>画满</b>固定格数（{@code GTRecipeTypeUI#addInventorySlotGroup}
 * 直接用 maxOutputs 定位每个槽），与实际产出种类无关 —— 一张表容纳多种产出数时，产出少的配方会拖着一串空槽，
 * 看起来像「还有东西没显示出来」。按产出数拆表后，每张表的格数正好等于该组配方的产出种类数，界面不留空位。</p>
 *
 * <h2>为什么自己 {@code new GTRecipeType}</h2>
 * 与 {@code NanochipRecipeTypes} / {@code DisposableToolPrintTypes} 同因：GTCEu 1.4.4 的
 * {@code GTRecipeTypes.register(...)} 把 id 硬编码成 {@code gtceu:} 命名空间。这里把同一个
 * {@link ResourceLocation} 登记进三个注册表：{@code BuiltInRegistries.RECIPE_TYPE}
 * （RecipeManager 的 byType 索引）、{@code BuiltInRegistries.RECIPE_SERIALIZER}（数据包 JSON 的 type 字段）、
 * {@code GTRegistries.RECIPE_TYPES}（{@code GTRecipe} codec）。</p>
 */
public final class ComponentPartsRecipeTypes {

    /** 一条零件配方最多产出的零件种类数（本整合包里最多的一档是 11 种）。 */
    public static final int MAX_KINDS = 11;

    private static final String GROUP_MULTIBLOCK = "multiblock";

    /** 下标 1..MAX_KINDS 各一张表；下标 0 不用。 */
    private static final GTRecipeType[] BY_KINDS = new GTRecipeType[MAX_KINDS + 1];

    private ComponentPartsRecipeTypes() {
    }

    /**
     * 取「产出 {@code kinds} 种零件」的配方表。
     *
     * @param kinds 产出种类数，1..{@link #MAX_KINDS}；越界时夹到范围内
     */
    public static GTRecipeType of(int kinds) {
        int k = Math.max(1, Math.min(MAX_KINDS, kinds));
        return BY_KINDS[k];
    }

    /** 已注册的配方表（1..MAX_KINDS，跳过未注册的）。 */
    public static java.util.List<GTRecipeType> all() {
        java.util.List<GTRecipeType> out = new java.util.ArrayList<>();
        for (int i = 1; i <= MAX_KINDS; i++) {
            if (BY_KINDS[i] != null) {
                out.add(BY_KINDS[i]);
            }
        }
        return out;
    }

    /** 配方类型 id（{@code gtlmodular:component_parts_<kinds>}）。 */
    public static ResourceLocation id(int kinds) {
        return new ResourceLocation(GTLModular.MOD_ID, "component_parts_" + kinds);
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
        if (BY_KINDS[1] != null) {
            GTLModular.LOGGER.info("[gtlmodular] ComponentPartsRecipeTypes 重复初始化，忽略");
            return;
        }
        for (int kinds = 1; kinds <= MAX_KINDS; kinds++) {
            GTRecipeType type = new GTRecipeType(id(kinds), GROUP_MULTIBLOCK, new RecipeType<?>[0]);
            type.setEUIO(IO.IN);
            // 输出格数 = 该组配方的产出种类数；输入只占 1 格（一种材料）。
            type.setMaxIOSize(1, kinds, 0, 0);
            type.setMaxTooltips(4);
            type.setXEIVisible(true);
            type.setProgressBar(GuiTextures.PROGRESS_BAR_ARROW, ProgressTexture.FillDirection.LEFT_TO_RIGHT);
            type.setSound(GTSoundEntries.ASSEMBLER);

            GTRegistries.register(BuiltInRegistries.RECIPE_TYPE, type.registryName, type);
            GTRegistries.register(BuiltInRegistries.RECIPE_SERIALIZER, type.registryName, new GTRecipeSerializer());
            GTRegistries.RECIPE_TYPES.register(type.registryName, type);

            BY_KINDS[kinds] = type;
        }
        GTLModular.LOGGER.info("[gtlmodular] 已注册 {} 张零部件加工配方表（component_parts_1 … _{}）",
                MAX_KINDS, MAX_KINDS);
    }
}
