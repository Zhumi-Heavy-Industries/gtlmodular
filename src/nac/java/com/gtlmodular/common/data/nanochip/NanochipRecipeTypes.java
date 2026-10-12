package com.gtlmodular.common.data.nanochip;

import com.gregtechceu.gtceu.api.GTCEuAPI;
import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeSerializer;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.common.data.GTSoundEntries;
import com.lowdragmc.lowdraglib.gui.texture.ProgressTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/** 主机和模块使用各自的 NAC 专用配方类型，主机 ASSEMBLY_COMPLEX 不代理电路组装机表。 */
public final class NanochipRecipeTypes {

    private NanochipRecipeTypes() {
    }

    public static final String GROUP_MULTIBLOCK = "multiblock";

    /** 类型构造会写入此索引，必须先初始化索引再声明各配方表。 */
    private static final Map<ResourceLocation, GTRecipeType> INDEX = new LinkedHashMap<>();

    private static boolean registered;

    public static final GTRecipeType CONVERSION =
            type("nanochip_conversion", 1, 1, 0, 0)
                    .setProgressBar(GuiTextures.PROGRESS_BAR_CIRCUIT, ProgressTexture.FillDirection.LEFT_TO_RIGHT)
                    .setSound(GTSoundEntries.COMPUTATION)
                    .setIconSupplier(machineIcon("nanochip_assembly_complex"));

    public static final GTRecipeType ASSEMBLY_MATRIX =
            type("nanochip_assembly_matrix", 16, 4, 4, 4)
                    .setProgressBar(GuiTextures.PROGRESS_BAR_CIRCUIT_ASSEMBLER, ProgressTexture.FillDirection.LEFT_TO_RIGHT)
                    .setSound(GTSoundEntries.ASSEMBLER)
                    .setIconSupplier(machineIcon("nanochip_assembly_matrix"));

    public static final GTRecipeType SMD_PROCESSOR =
            type("nanochip_smd_processor", 1, 1, 0, 0)
                    .setProgressBar(GuiTextures.PROGRESS_BAR_CIRCUIT_ASSEMBLER, ProgressTexture.FillDirection.LEFT_TO_RIGHT)
                    .setSound(GTSoundEntries.ASSEMBLER)
                    .setIconSupplier(machineIcon("nanochip_smd_processor"));

    public static final GTRecipeType BOARD_PROCESSOR =
            type("nanochip_board_processor", 1, 1, 1, 1)
                    .setProgressBar(GuiTextures.PROGRESS_BAR_BATH, ProgressTexture.FillDirection.LEFT_TO_RIGHT)
                    .setSound(GTSoundEntries.BATH)
                    .setIconSupplier(machineIcon("nanochip_board_processor"));

    public static final GTRecipeType ETCHING_ARRAY =
            type("nanochip_etching_array", 1, 1, 0, 0)
                    .setProgressBar(GuiTextures.PROGRESS_BAR_ARC_FURNACE, ProgressTexture.FillDirection.LEFT_TO_RIGHT)
                    .setSound(GTSoundEntries.ARC)
                    .setIconSupplier(machineIcon("nanochip_etching_array"));

    public static final GTRecipeType CUTTING_CHAMBER =
            type("nanochip_cutting_chamber", 1, 1, 1, 0)
                    .setProgressBar(GuiTextures.PROGRESS_BAR_SLICE, ProgressTexture.FillDirection.LEFT_TO_RIGHT)
                    .setSound(GTSoundEntries.CUT)
                    .setIconSupplier(machineIcon("nanochip_cutting_chamber"));

    public static final GTRecipeType WIRE_TRACER =
            type("nanochip_wire_tracer", 1, 1, 0, 0)
                    .setProgressBar(GuiTextures.PROGRESS_BAR_WIREMILL, ProgressTexture.FillDirection.LEFT_TO_RIGHT)
                    .setSound(GTSoundEntries.CUT)
                    .setIconSupplier(machineIcon("nanochip_wire_tracer"));

    public static final GTRecipeType SUPERCONDUCTOR_SPLITTER =
            type("nanochip_superconductor_splitter", 1, 1, 0, 0)
                    .setProgressBar(GuiTextures.PROGRESS_BAR_WIREMILL, ProgressTexture.FillDirection.LEFT_TO_RIGHT)
                    .setSound(GTSoundEntries.CUT)
                    .setIconSupplier(machineIcon("nanochip_superconductor_splitter"));

    public static final GTRecipeType OPTICAL_ORGANIZER =
            type("nanochip_optical_organizer", 1, 1, 0, 0)
                    .setProgressBar(GuiTextures.PROGRESS_BAR_CIRCUIT_ASSEMBLER, ProgressTexture.FillDirection.LEFT_TO_RIGHT)
                    .setSound(GTSoundEntries.COMPUTATION)
                    .setIconSupplier(machineIcon("nanochip_optical_organizer"));

    public static final GTRecipeType EXOTIC_ORGANIZER =
            type("nanochip_exotic_organizer", 1, 1, 0, 0)
                    .setProgressBar(GuiTextures.PROGRESS_BAR_CIRCUIT_ASSEMBLER, ProgressTexture.FillDirection.LEFT_TO_RIGHT)
                    .setSound(GTSoundEntries.COMPUTATION)
                    .setIconSupplier(machineIcon("nanochip_exotic_organizer"));

    public static final GTRecipeType COSMIC_ORGANIZER =
            type("nanochip_cosmic_organizer", 1, 1, 0, 0)
                    .setProgressBar(GuiTextures.PROGRESS_BAR_CIRCUIT_ASSEMBLER, ProgressTexture.FillDirection.LEFT_TO_RIGHT)
                    .setSound(GTSoundEntries.COMPUTATION)
                    .setIconSupplier(machineIcon("nanochip_cosmic_organizer"));

    public static final GTRecipeType SUPRACAUSAL_ORGANIZER =
            type("nanochip_supracausal_organizer", 1, 1, 0, 0)
                    .setProgressBar(GuiTextures.PROGRESS_BAR_CIRCUIT_ASSEMBLER, ProgressTexture.FillDirection.LEFT_TO_RIGHT)
                    .setSound(GTSoundEntries.COMPUTATION)
                    .setIconSupplier(machineIcon("nanochip_supracausal_organizer"));

    public static final GTRecipeType BIOLOGICAL_COORDINATOR =
            type("nanochip_biological_coordinator", 1, 1, 1, 0)
                    .setProgressBar(GuiTextures.PROGRESS_BAR_BATH, ProgressTexture.FillDirection.LEFT_TO_RIGHT)
                    .setSound(GTSoundEntries.BATH)
                    .setIconSupplier(machineIcon("nanochip_biological_coordinator"));

    public static final GTRecipeType ENCASEMENT_WRAPPER =
            type("nanochip_encasement_wrapper", 4, 1, 0, 0)
                    .setProgressBar(GuiTextures.PROGRESS_BAR_PACKER, ProgressTexture.FillDirection.LEFT_TO_RIGHT)
                    .setSound(GTSoundEntries.ASSEMBLER)
                    .setIconSupplier(machineIcon("nanochip_encasement_wrapper"));

    public static final GTRecipeType ASSEMBLY_COMPLEX =
            type("nanochip_assembly_complex", 6, 1, 1, 0)
                    .setProgressBar(GuiTextures.PROGRESS_BAR_CIRCUIT_ASSEMBLER, ProgressTexture.FillDirection.LEFT_TO_RIGHT)
                    .setSound(GTSoundEntries.ASSEMBLER)
                    .setIconSupplier(machineIcon("nanochip_assembly_complex"));

    /** 分流器只调度通道，独立空表用于模式命名，并隐藏没有配方的分类。 */
    public static final GTRecipeType SPLITTER_SCHEDULE =
            hiddenType("nanochip_splitter_schedule", 1, 0, 0, 0)
                    .setProgressBar(GuiTextures.PROGRESS_BAR_ARROW, ProgressTexture.FillDirection.LEFT_TO_RIGHT)
                    .setIconSupplier(machineIcon("nanochip_splitter"));

    public static final long RECIPE_IV = 7680L;

    public static final long RECIPE_ZPM = 122880L;

    public static final long RECIPE_LuV = 30720L;

    public static final long RECIPE_UV = 491520L;

    public static final long RECIPE_UHV = 1966080L;

    public static final long RECIPE_UEV = 7864320L;

    public static final long RECIPE_UIV = 31457280L;

    public static final long RECIPE_UMV = 125829120L;

    public static final long RECIPE_MAX = 2013265912L;

    /** 各注册表只能在 GTCEu 配方类型事件窗口写入，过早或重复注册会失败。 */
    public static void init() {
        if (registered) {
            NanochipModContext.logger().info("[gtlmodular] NanochipRecipeTypes.init() 重复调用 —— 已注册 {} 张表，忽略",
                    INDEX.size());
            return;
        }
        registered = true;

        for (GTRecipeType type : INDEX.values().toArray(new GTRecipeType[0])) {
            registerOne(type);
        }

        NanochipModContext.logger().info("[gtlmodular] 已注册 {} 张 NAC 专用配方表（命名空间 {}）：{}",
                INDEX.size(), NanochipModContext.modId(), INDEX.keySet());
    }

    public static void onRecipeTypeRegisterEvent(
            GTCEuAPI.RegisterEvent<ResourceLocation, GTRecipeType> event) {
        init();
    }

    public static Map<ResourceLocation, GTRecipeType> registered() {
        return java.util.Collections.unmodifiableMap(INDEX);
    }

    public static ResourceLocation id(String path) {
        return NanochipModContext.registryId(path);
    }

    private static void registerOne(GTRecipeType type) {
        // RecipeManager 按 RecipeType 索引，需注册同一类型实例。
        GTRegistries.register(BuiltInRegistries.RECIPE_TYPE, type.registryName, type);
        // 数据包用 type 字段寻找序列化器，因此还须登记 RecipeSerializer。
        GTRegistries.register(BuiltInRegistries.RECIPE_SERIALIZER, type.registryName, new GTRecipeSerializer());
        // GTRecipe 的 CODEC 从 GTCEu 类型表解析类型，不能漏掉这份登记。
        GTRegistries.RECIPE_TYPES.register(type.registryName, type);
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

    private static GTRecipeType hiddenType(String path, int itemIn, int itemOut, int fluidIn, int fluidOut) {
        GTRecipeType type = type(path, itemIn, itemOut, fluidIn, fluidOut);
        type.setXEIVisible(false);
        return type;
    }

    private static GTRecipeType proxiedType(String path, GTRecipeType parent) {
        GTRecipeType type = new ProxiedRecipeType(id(path), parent);
        type.setEUIO(IO.IN);
        type.setMaxIOSize(
                parent.getMaxInputs(ItemRecipeCapability.CAP),
                parent.getMaxOutputs(ItemRecipeCapability.CAP),
                parent.getMaxInputs(FluidRecipeCapability.CAP),
                parent.getMaxOutputs(FluidRecipeCapability.CAP));
        type.setMaxTooltips(4);
        type.setXEIVisible(true);
        INDEX.put(type.registryName, type);
        return type;
    }

    private static final class ProxiedRecipeType extends GTRecipeType {

        ProxiedRecipeType(ResourceLocation id, RecipeType<?> parent) {
            super(id, GROUP_MULTIBLOCK, parent);
        }

        /** 代理转换需复制后标记类型，避免直接改写源表中的配方对象。 */
        @Override
        public GTRecipe toGTrecipe(ResourceLocation id, Recipe<?> recipe) {
            if (!(recipe instanceof GTRecipe gt)) {
                return super.toGTrecipe(id, recipe);
            }
            try {
                GTRecipe copy = gtlmodular$copyRecipe(gt);
                if (copy == null) {
                    return gt;
                }
                if (gtlmodular$markType(copy, this)) {
                    return copy;
                }
                return gt;
            } catch (Throwable ignored) {
                return gt;
            }
        }

        private static GTRecipe gtlmodular$copyRecipe(GTRecipe gt) {
            Class<?> k = gt.getClass();
            for (Class<?> c = k; c != null; c = c.getSuperclass()) {
                try {
                    java.lang.reflect.Method m = c.getDeclaredMethod("copy");
                    m.setAccessible(true);
                    Object r = m.invoke(gt);
                    if (r instanceof GTRecipe out) {
                        return out;
                    }
                } catch (NoSuchMethodException ignored) {
                } catch (Throwable ignored) {
                }
            }
            for (Class<?> c = k; c != null; c = c.getSuperclass()) {
                for (java.lang.reflect.Method m : c.getDeclaredMethods()) {
                    if (!"copy".equals(m.getName()) || m.getParameterCount() != 1) {
                        continue;
                    }
                    try {
                        m.setAccessible(true);
                        Object r = m.invoke(gt, new Object[]{null});
                        if (r instanceof GTRecipe out) {
                            return out;
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }
            return null;
        }

        private static boolean gtlmodular$markType(GTRecipe target, GTRecipeType type) {
            try {
                for (Class<?> c = target.getClass(); c != null; c = c.getSuperclass()) {
                    try {
                        java.lang.reflect.Method m = c.getDeclaredMethod("setRecipeType", GTRecipeType.class);
                        m.setAccessible(true);
                        m.invoke(target, type);
                        return true;
                    } catch (NoSuchMethodException ignored) {
                    }
                }
            } catch (Throwable ignored) {
            }
            for (Class<?> c = target.getClass(); c != null; c = c.getSuperclass()) {
                try {
                    java.lang.reflect.Field fl = c.getDeclaredField("recipeType");
                    fl.setAccessible(true);
                    fl.set(target, type);
                    return true;
                } catch (NoSuchFieldException ignored) {
                } catch (Throwable ignored) {
                    return false;
                }
            }
            return false;
        }
    }

    /** 图标延迟取机器物品，避免配方表初始化早于机器注册时读取未就绪条目。 */
    private static Supplier<ItemStack> machineIcon(String machineId) {
        return () -> NanochipMachines.machineIconStack(machineId);
    }
}
