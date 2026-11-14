package com.gtlmodular.common.data.fieldforge;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.data.recipe.builder.GTRecipeBuilder;
import com.gtlmodular.GTLModular;
import com.lowdragmc.lowdraglib.side.fluid.FluidStack;
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
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 力场压模零件工厂（{@code gtlmodular:field_extruder_factory}）的<b>获取配方</b> ——
 * 数值<b>逐项照搬 GTO</b>（{@code _gto_src} 对应的装配线配方 / 本包 KubeJS
 * {@code nanotechnology.js:45} 的 {@code gtceu:field_extruder_factory} 那条），
 * 只把产出改成我们的 id。
 *
 * <h2>GTO 原配方（照搬）</h2>
 * <pre>
 * 装配线　UXV　{@code 134217728 EU/t}（= GTValues.V[UXV]）　{@code 800t}
 * 物品：4× uev_extruder（史诗压模器 II）/ 4× uev_compressor（史诗压缩机 II）
 *       <b>12× gtceu:field_extruder_factory（本包原机，改造对象）</b>
 *       8× uev_field_generator / 4× uev_emitter
 *       4× #gtceu:circuits/uev / 4× rhenium_nanoswarm / 64× stress_proof_casing / 64× dalisenite_plate
 * 流体：2304 mutated_living_solder / 4608 platinum / 4608 reinforced_epoxy_resin
 *       4608 uranium_rhodium_dinaquadide
 * 研究：样本 <b>gtceu:field_extruder_factory（本包原有的力场压模工厂）</b>，
 *       数据介质 data_module，UXV，{@code 256 CWU/t}
 *       —— GTO 原本用 large_extruder，按队长口径换成本包原机。
 * </pre>
 *
 * <h2>为什么是 Java 而不是 JSON</h2>
 * <p>这张配方带<b>研究门</b>。GTCEu 的研究门不是单个 JSON 字段能表达的：{@code stationResearch} 会
 * 同时生成
 * <ol>
 *   <li>装配线上的 {@code ResearchCondition}（引用 researchId + 数据介质）；</li>
 *   <li>一条配套的<b>研究站配方</b>（数据介质 ×1 + 研究样本 → 写入
 *       {@code assembly_line_research} NBT 的数据介质），研究门靠它才能「解开」。</li>
 * </ol>
 * 纯 JSON 写不出第 2 条（输出物要带 NBT，还要 CWU/t、总 CWU），所以按本模组已有的做法
 * （{@link com.gtlmodular.common.data.coolingtower.CoolingTowerRecipe} 超级大冰箱 /
 * {@code TentacleRecipes} 奈亚之触）在服务端启动后注入。
 *
 * <p>注：本包 KubeJS {@code nanotechnology.js:45} 那条输出的仍是 GTO 的
 * {@code gtceu:field_extruder_factory}（本整合包没装 gtocore，那个 id 不存在，等于空转）。
 * 本类补的是<b>我们这台机器自己的</b>获取配方，与那条互不影响。</p>
 */
public final class FieldExtruderFactoryRecipe {

    private FieldExtruderFactoryRecipe() {
    }

    /** 装配线物品输入（GTO 原样）。 */
    private static final String[][] ITEM_INPUTS = {
            // ★ 2026-11-15 队长口径：换成「史诗压模器 II」/「史诗压缩机 II」（gtceu:uev_*，UEV 档）
            {"gtceu:uev_extruder", "4"},
            {"gtceu:uev_compressor", "4"},
            {"gtceu:uev_field_generator", "8"},
            {"gtceu:uev_emitter", "4"},
            // ★ 2026-11-15 队长口径：装配线输入 12 台原机（gtceu:field_extruder_factory），
            //   用它们"改造"出本机的零件加工型；产出仍是 1 台。
            {"gtceu:field_extruder_factory", "12"},
            {"gtceu:rhenium_nanoswarm", "4"},
            {"gtceu:stress_proof_casing", "64"},
            {"gtceu:dalisenite_plate", "64"},
    };

    /** 装配线流体输入（GTO 原样）。 */
    private static final String[][] FLUID_INPUTS = {
            {"gtceu:mutated_living_solder", "2304"},
            {"gtceu:platinum", "4608"},
            {"gtceu:reinforced_epoxy_resin", "4608"},
            {"gtceu:uranium_rhodium_dinaquadide", "4608"},
    };

    /** ★ 2026-11-15 队长口径：UIV 电路升到 <b>UEV</b> 电路 ×4。 */
    private static final TagKey<Item> CIRCUITS_UEV =
            TagKey.create(net.minecraft.core.registries.Registries.ITEM,
                    new ResourceLocation("gtceu", "circuits/uev"));

    /**
     * ★ 2026-11-15 队长口径：<b>电压等级降到 UXV</b>。
     *
     * <p>原值 {@code GTValues.VA[UEV] = 251658240} 落在 UEV(8388608) 与 UIV(33554432)、
     * UXV(134217728)、OpV(536870912) 之间 ⇒ GTCEu 按「不超过 EUt 的最高档」判定为 <b>OpV</b>
     * （实测研究站界面右下角显示 OpV，与预期不符）。
     * 现改成 <b>{@code GTValues.V[UXV] = 134217728}</b>，正好落在 UXV 档。</p>
     */
    private static final int EUT = 134_217_728;
    private static final int DURATION = 800;


    /**
     * 研究样本 = <b>本整合包原有的那台「力场压模工厂」</b>（{@code gtceu:field_extruder_factory}）。
     *
     * <p>队长 2026-11-14 口径：「用我们包里原有的力场压模工厂研究得到力场压模零件工厂」。
     * 本包确实有这台机器（{@code logs/debug.log:31098} 可见 registrate 捕获了
     * {@code gtceu:field_extruder_factory} 的 block / item / block_entity），中文名也叫「力场压模工厂」。
     * <p>GTO 原配方的研究样本是 {@code large_extruder}（大型压模机），这里按队长要求换成上面的原机。 */
    private static final String RESEARCH_STACK_ID = "gtceu:field_extruder_factory";
    private static final String RESEARCH_DATA_ID = "gtceu:data_module";
    private static final int RESEARCH_CWUT = 256;
    /** GTO 的 {@code .CWUt(256)} 只给 CWU/t，总 CWU 按 GTCEu 默认（CWU/t × 时长）。 */
    private static final int RESEARCH_TOTAL_CWU = 256 * DURATION;

    private static Field byTypeField;
    private static boolean byTypeFailed;
    private static Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> byTypeMap;

    /** 在 {@code GTLModular} 构造里调用一次（Forge 事件总线）。 */
    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(FieldExtruderFactoryRecipe::onServerStarted);
        GTLModular.LOGGER.info("[gtlmodular] 力场压模零件工厂：装配线 + 研究门获取配方已挂载，服务端启动后注入");
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
            GTLModular.LOGGER.info("[gtlmodular] 力场压模零件工厂：获取配方注入完成（{} 条，含研究站配方）", ok);
        } catch (Throwable t) {
            GTLModular.LOGGER.warn("[gtlmodular] 力场压模零件工厂：获取配方注入失败（已忽略，不影响启动）：{}",
                    t.toString());
        }
    }

    private static GTRecipe buildRecipe() {
        Item output = item("gtlmodular:field_extruder_factory");
        if (output == null) {
            GTLModular.LOGGER.warn("[gtlmodular] 力场压模零件工厂：本体未注册 ⇒ 跳过获取配方");
            return null;
        }
        ItemStack researchStack = stack(RESEARCH_STACK_ID, 1);
        ItemStack dataStack = stack(RESEARCH_DATA_ID, 1);
        if (researchStack.isEmpty() || dataStack.isEmpty()) {
            GTLModular.LOGGER.warn("[gtlmodular] 力场压模零件工厂：研究样本 {} / 数据介质 {} 缺失 ⇒ 跳过获取配方",
                    RESEARCH_STACK_ID, RESEARCH_DATA_ID);
            return null;
        }

        GTRecipeBuilder builder = GTRecipeTypes.ASSEMBLY_LINE_RECIPES
                .recipeBuilder(new ResourceLocation(GTLModular.MOD_ID, "field_extruder_factory"));

        List<String> missing = new ArrayList<>();
        for (String[] entry : ITEM_INPUTS) {
            ItemStack stack = stack(entry[0], Integer.parseInt(entry[1]));
            if (stack.isEmpty()) {
                missing.add(entry[0] + "×" + entry[1]);
                continue;
            }
            builder.inputItems(stack);
        }
        builder.inputItems(CIRCUITS_UEV, 4);

        List<FluidStack> fluids = new ArrayList<>();
        for (String[] entry : FLUID_INPUTS) {
            FluidStack stack = fluid(entry[0], Integer.parseInt(entry[1]));
            if (stack == null) {
                missing.add("流体:" + entry[0] + "×" + entry[1]);
                continue;
            }
            fluids.add(stack);
        }
        if (!missing.isEmpty()) {
            GTLModular.LOGGER.warn("[gtlmodular] 力场压模零件工厂：缺少输入 {} ⇒ 跳过获取配方", missing);
            return null;
        }

        builder.inputFluids(fluids.toArray(new FluidStack[0]));
        builder.outputItems(new ItemStack(output));
        builder.EUt(EUT).duration(DURATION);
        // 研究门：等价于 KubeJS 的
        //   .stationResearch(b => b.researchStack(large_extruder).dataStack(data_module)
        //                          .EUt(VA[UEV]).CWUt(256))
        builder.stationResearch(b -> b
                .researchStack(researchStack)
                .dataStack(dataStack)
                .EUt(EUT)
                .CWUt(RESEARCH_CWUT, RESEARCH_TOTAL_CWU));

        GTRecipe recipe = builder.buildRawRecipe();
        for (GTRecipeBuilder.ResearchRecipeEntry entry : builder.researchRecipeEntries()) {
            GTRecipeTypes.ASSEMBLY_LINE_RECIPES.addDataStickEntry(entry.researchId(), recipe);
            GTLModular.LOGGER.info("[gtlmodular] 力场压模零件工厂：研究条目已登记 researchId={}", entry.researchId());
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

            // ★ 2026-11-15：**顺序必须是「数据模块 → 研究样本」**，不能反过来。
            //   取证：GTCEu ResearchManager（javap 反汇编）生成研究站配方时是
            //     .inputItems(<dataStack>.getItem())  ← 先：数据模块
            //     .inputItems(<researchStack>)        ← 后：研究样本
            //   研究站界面「中心格」取第一个输入 ⇒ 数据模块在中心、样本在右上（JEI 里的正常形态）。
            //   原先写成「样本 → 数据」会让样本跑到中心，看起来就是「反了」。
            GTRecipeBuilder builder = GTRecipeTypes.RESEARCH_STATION_RECIPES
                    .recipeBuilder(new ResourceLocation(GTLModular.MOD_ID, "field_extruder_factory_research"))
                    .inputItems(entry.dataStack())
                    .inputItems(entry.researchStack().getItem())
                    .outputItems(encoded)
                    .EUt(entry.EUt())
                    .CWUt(entry.CWUt())
                    .totalCWU(entry.duration());
            int added = inject(GTRecipeTypes.RESEARCH_STATION_RECIPES, builder.buildRawRecipe());
            GTLModular.LOGGER.info("[gtlmodular] 力场压模零件工厂：研究站配方已注入 researchId={}（{} CWU/t，总 {} CWU）",
                    entry.researchId(), entry.CWUt(), entry.duration());
            return added;
        } catch (Throwable t) {
            GTLModular.LOGGER.warn("[gtlmodular] 力场压模零件工厂：补研究站配方失败（研究门可能无法解开）：{}",
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
                GTLModular.LOGGER.warn("[gtlmodular] 力场压模零件工厂：byType 不可用 ⇒ {} 只进 lookup（机器能跑，JEI 可能不显示）",
                        recipe.id);
            }
            return added ? 1 : 0;
        } catch (Throwable t) {
            GTLModular.LOGGER.warn("[gtlmodular] 力场压模零件工厂：登记 {} 失败（已忽略）：{}", recipe.id, t.toString());
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
                        Field f = RecipeManager.class.getDeclaredField(name);
                        f.setAccessible(true);
                        byTypeField = f;
                        break;
                    } catch (NoSuchFieldException ignored) {
                        // 试下一个名字
                    }
                }
                if (byTypeField == null) {
                    byTypeFailed = true;
                    return null;
                }
            }
            return (Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>>) byTypeField.get(manager);
        } catch (Throwable t) {
            byTypeFailed = true;
            GTLModular.LOGGER.warn("[gtlmodular] 力场压模零件工厂：取 RecipeManager.byType 失败（已忽略）：{}", t.toString());
            return null;
        }
    }

    // ------------------------------------------------------------------ 小工具

    private static Item item(String id) {
        Item it = ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));
        return it;
    }

    private static ItemStack stack(String id, int count) {
        Item it = item(id);
        if (it == null) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(it, count);
        return stack.isEmpty() ? ItemStack.EMPTY : stack;
    }

    private static FluidStack fluid(String id, int amount) {
        Fluid f = ForgeRegistries.FLUIDS.getValue(new ResourceLocation(id));
        if (f == null) {
            return null;
        }
        return FluidStack.create(f, amount);
    }
}
