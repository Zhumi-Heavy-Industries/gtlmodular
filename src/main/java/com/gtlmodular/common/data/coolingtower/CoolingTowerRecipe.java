package com.gtlmodular.common.data.coolingtower;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.data.recipe.builder.GTRecipeBuilder;
import com.gtlmodular.GTLModular;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartedEvent;
import com.lowdragmc.lowdraglib.side.fluid.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 * 「超级大冰箱」（{@code gtlmodular:cooling_tower_condensation_center}）的获取配方。
 *
 * <h2>为什么是 Java 而不是 JSON</h2>
 * <p>这张配方带 <b>研究门</b>（研究样本 = 寒冰冷冻机，数据介质 = 数据模块）。GTCEu 的研究门不是
 * 单个 JSON 字段能表达的：{@code stationResearch} 会同时生成
 * <ol>
 *   <li>装配线上的 {@code ResearchCondition}（引用 researchId + 数据介质）；</li>
 *   <li>一条配套的<b>研究站配方</b>（数据模块 ×1 + 研究样本 → 写入
 *       {@code assembly_line_research} NBT 的数据模块），研究门靠它才能“解开”。</li>
 * </ol>
 * 纯 JSON 写不出第 2 条（输出物要带 NBT，还要 CWU/t、总 CWU），所以按本模组已有的做法
 * （{@code TentacleRecipes} 的奈亚之触 / 集控核心 / 夸克管道）在服务端启动后注入。
 *
 * <h2>数值（队长 2026-11-10 截图）</h2>
 * <ul>
 *   <li>装配线：UXV，{@code 125829120 EU/t}，{@code 240t}（12 秒），总计 30.2G EU；</li>
 *   <li>研究：样本 {@code gtceu:cold_ice_freezer}，数据介质 {@code gtceu:data_module}，
 *       {@code 640 CWU/t}，总计 {@code 2560000 CWU}；</li>
 *   <li>材料按截图第二条（更全的那条），「双层钌钛合金板」按队长确认 = <b>钌铱合金</b>
 *       {@code gtceu:double_ruridit_plate}。</li>
 * </ul>
 */
public final class CoolingTowerRecipe {

    private CoolingTowerRecipe() {
    }

    /** 装配线：总输入 12 物品 + 4 流体 → 超级大冰箱 ×1。 */
    private static final String[][] ITEM_INPUTS = {
            {"gtceu:cold_ice_freezer", "64"},
            {"kubejs:cryogenic_interface", "64"},
            {"kubejs:laser_cooling_unit", "64"},
            {"gtceu:mithril_frame", "16"},
            {"gtceu:sapphire_block", "16"},
            {"gtlmodular:quark_pipe", "2"},
            {"gtceu:crystalmatrix_double_wire", "16"},
            {"gtceu:double_ruridit_plate", "64"},
            {"gtceu:double_crystalmatrix_plate", "64"},
    };

    private static final String[][] FLUID_INPUTS = {
            {"gtceu:mithril", "2304"},
            {"gtceu:uruium", "2304"},
            {"gtceu:crystalmatrix", "4608"},
            {"gtceu:liquid_starlight", "4608"},
    };

    /** UVX 电路 ×16（KubeJS 写法 {@code 16x #gtceu:circuits/uxv}）。 */
    private static final TagKey<Item> CIRCUITS_UXV =
            TagKey.create(net.minecraft.core.registries.Registries.ITEM,
                    new ResourceLocation("gtceu", "circuits/uxv"));

    private static final int EUT = 125_829_120;
    private static final int DURATION = 240;

    private static final String RESEARCH_STACK_ID = "gtceu:cold_ice_freezer";
    private static final String RESEARCH_DATA_ID = "gtceu:data_module";
    private static final int RESEARCH_CWUT = 640;
    private static final int RESEARCH_TOTAL_CWU = 2_560_000;

    private static Field byTypeField;
    private static boolean byTypeFailed;
    private static Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> byTypeMap;

    /** 在 {@code GTLModular} 构造里调用一次（Forge 事件总线）。 */
    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(CoolingTowerRecipe::onServerStarted);
        GTLModular.LOGGER.info("[gtlmodular] 超级大冰箱：装配线 + 研究门配方已挂载，服务端启动后注入");
    }

    private static void onServerStarted(ServerStartedEvent event) {
        install(event.getServer());
    }

    public static synchronized void install(MinecraftServer server) {
        if (server == null) {
            return;
        }
        try {
            byTypeMap = byType(server.getRecipeManager());
            int ok = inject(GTRecipeTypes.ASSEMBLY_LINE_RECIPES, buildRecipe());
            GTLModular.LOGGER.info("[gtlmodular] 超级大冰箱：获取配方注入完成（{} 条，含研究站配方）", ok);
        } catch (Throwable t) {
            GTLModular.LOGGER.warn("[gtlmodular] 超级大冰箱：获取配方注入失败（已忽略，不影响启动）：{}",
                    t.toString());
        }
    }

    private static GTRecipe buildRecipe() {
        Item output = item("gtlmodular:cooling_tower_condensation_center");
        if (output == null) {
            GTLModular.LOGGER.warn("[gtlmodular] 超级大冰箱：本体未注册 ⇒ 跳过获取配方");
            return null;
        }
        ItemStack researchStack = stack(RESEARCH_STACK_ID, 1);
        ItemStack dataStack = stack(RESEARCH_DATA_ID, 1);
        if (researchStack.isEmpty() || dataStack.isEmpty()) {
            GTLModular.LOGGER.warn("[gtlmodular] 超级大冰箱：研究样本 {} / 数据介质 {} 缺失 ⇒ 跳过获取配方",
                    RESEARCH_STACK_ID, RESEARCH_DATA_ID);
            return null;
        }

        GTRecipeBuilder builder = GTRecipeTypes.ASSEMBLY_LINE_RECIPES
                .recipeBuilder(new ResourceLocation(GTLModular.MOD_ID, "super_big_refrigerator"));
        java.util.List<String> missing = new java.util.ArrayList<>();
        for (String[] entry : ITEM_INPUTS) {
            ItemStack stack = stack(entry[0], Integer.parseInt(entry[1]));
            if (stack.isEmpty()) {
                missing.add(entry[0] + "×" + entry[1]);
                continue;
            }
            builder.inputItems(stack);
        }
        builder.inputItems(CIRCUITS_UXV, 16);
        java.util.List<FluidStack> fluids = new java.util.ArrayList<>();
        for (String[] entry : FLUID_INPUTS) {
            FluidStack stack = fluid(entry[0], Integer.parseInt(entry[1]));
            if (stack == null) {
                missing.add("流体:" + entry[0] + "×" + entry[1]);
                continue;
            }
            fluids.add(stack);
        }
        if (!missing.isEmpty()) {
            GTLModular.LOGGER.warn("[gtlmodular] 超级大冰箱：缺少输入 {} ⇒ 跳过获取配方", missing);
            return null;
        }
        builder.inputFluids(fluids.toArray(new FluidStack[0]));
        builder.outputItems(new ItemStack(output));
        builder.EUt(EUT).duration(DURATION);
        builder.stationResearch(b -> b
                .researchStack(researchStack)
                .dataStack(dataStack)
                .EUt(EUT)
                .CWUt(RESEARCH_CWUT, RESEARCH_TOTAL_CWU));

        GTRecipe recipe = builder.buildRawRecipe();
        for (GTRecipeBuilder.ResearchRecipeEntry entry : builder.researchRecipeEntries()) {
            GTRecipeTypes.ASSEMBLY_LINE_RECIPES.addDataStickEntry(entry.researchId(), recipe);
            GTLModular.LOGGER.info("[gtlmodular] 超级大冰箱：研究条目已登记 researchId={}", entry.researchId());
            injectStationResearch(entry);
        }
        return recipe;
    }

    private static int injectStationResearch(GTRecipeBuilder.ResearchRecipeEntry entry) {
        try {
            CompoundTag research = new CompoundTag();
            research.putString("research_id", entry.researchId());
            research.putString("research_type",
                    GTRecipeTypes.ASSEMBLY_LINE_RECIPES.registryName.toString());
            ItemStack encoded = entry.dataStack().copy();
            encoded.setCount(1);
            encoded.getOrCreateTag().put("assembly_line_research", research);

            GTRecipeBuilder builder = GTRecipeTypes.RESEARCH_STATION_RECIPES
                    .recipeBuilder(new ResourceLocation(GTLModular.MOD_ID, "super_big_refrigerator_research"))
                    .inputItems(entry.researchStack().getItem())
                    .inputItems(entry.dataStack())
                    .outputItems(encoded)
                    .EUt(entry.EUt())
                    .CWUt(entry.CWUt())
                    .totalCWU(entry.duration());
            int added = inject(GTRecipeTypes.RESEARCH_STATION_RECIPES, builder.buildRawRecipe());
            GTLModular.LOGGER.info("[gtlmodular] 超级大冰箱：研究站配方已注入 researchId={}（{} CWU/t，总 {} CWU）",
                    entry.researchId(), entry.CWUt(), entry.duration());
            return added;
        } catch (Throwable t) {
            GTLModular.LOGGER.warn("[gtlmodular] 超级大冰箱：补研究站配方失败（研究门可能无法解开）：{}",
                    t.toString());
            return 0;
        }
    }

    /** 一条配方同时写 lookup 与 byType；返回成功条数（0/1）。 */
    private static int inject(GTRecipeType type, GTRecipe recipe) {
        if (recipe == null || type == null) {
            return 0;
        }
        try {
            boolean added = type.getLookup().addRecipe(recipe);
            if (byTypeMap != null) {
                byTypeMap.computeIfAbsent(type, k -> new HashMap<>()).put(recipe.id, recipe);
            } else {
                GTLModular.LOGGER.warn("[gtlmodular] 超级大冰箱：byType 不可用 ⇒ {} 只进 lookup（机器能跑，JEI 可能不显示）",
                        recipe.id);
            }
            return added ? 1 : 0;
        } catch (Throwable t) {
            GTLModular.LOGGER.warn("[gtlmodular] 超级大冰箱：登记 {} 失败（已忽略）：{}", recipe.id, t.toString());
            return 0;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> byType(RecipeManager manager) {
        if (manager == null || byTypeFailed) {
            return null;
        }
        try {
            if (byTypeField == null) {
                for (String name : new String[]{"f_44007_", "recipes"}) {
                    try {
                        Field field = RecipeManager.class.getDeclaredField(name);
                        if (!Map.class.isAssignableFrom(field.getType())) {
                            continue;
                        }
                        field.setAccessible(true);
                        byTypeField = field;
                        break;
                    } catch (NoSuchFieldException ignored) {
                        // 换下一个候选名
                    }
                }
                if (byTypeField == null) {
                    byTypeFailed = true;
                    GTLModular.LOGGER.warn("[gtlmodular] 超级大冰箱：找不到 RecipeManager.byType 字段"
                            + "（f_44007_ / recipes）⇒ 运行期配方只会进 lookup，JEI 可能不显示");
                    return null;
                }
            }
            return (Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>>) byTypeField.get(manager);
        } catch (Throwable t) {
            byTypeFailed = true;
            GTLModular.LOGGER.warn("[gtlmodular] 超级大冰箱：读取 RecipeManager.byType 失败：{}", t.toString());
            return null;
        }
    }

    private static Item item(String id) {
        return ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));
    }

    private static ItemStack stack(String id, int count) {
        Item item = item(id);
        return item == null ? ItemStack.EMPTY : new ItemStack(item, count);
    }

    private static FluidStack fluid(String id, int amount) {
        Fluid fluid = ForgeRegistries.FLUIDS.getValue(new ResourceLocation(id));
        return fluid == null ? null : FluidStack.create(fluid, amount);
    }
}
