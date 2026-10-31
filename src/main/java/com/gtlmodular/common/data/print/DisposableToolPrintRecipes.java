package com.gtlmodular.common.data.print;

import com.gregtechceu.gtceu.api.GTCEuAPI;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.chemical.material.properties.PropertyKey;
import com.gregtechceu.gtceu.api.data.chemical.material.properties.ToolProperty;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.lookup.GTRecipeLookup;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gtlmodular.GTLModular;
import com.lowdragmc.lowdraglib.side.fluid.FluidStack;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 「一次性工具 3D 打印」与「一次性模具浇铸」两批配方的<b>运行期生成器</b>。
 *
 * <h2>配方语义（GTO gtocore {@code GTODisposableToolHandler} 的裁剪版）</h2>
 * <pre>
 * 3D 打印表： 一次性模具（不消耗）+ 1~6 号编程电路（不消耗）+ 72mB 白名单流体
 *            → 脆弱工具 × (材料耐久 / consume)，EUt 30，时长 = 材料质量
 * </pre>
 * 其中 consume 取 GTO 原表（锉4/扳手8/撬棍3/剪线钳9/锻造锤6/软锤6/螺丝刀4/锯4；
 * 刀/研钵为补齐的 10 种工具，按同族取 4）。出料数量 = GTO 原值，<b>不再封顶</b>。
 *
 * <h2>★ 2026-10-30 队长口径：只保留 6 种流体 + 电路额外识别</h2>
 * 只有这 6 种材料会生成打印配方（其余材料一律不生成）：
 * <pre>
 * 电路 1 = 液态铁(iron)      电路 2 = 液态钢(steel)       电路 3 = 液态钛(titanium)
 * 电路 4 = 液态硅岩合金(naquadah_alloy)  电路 5 = 液态中子素(neutronium)  电路 6 = 液态量子(quantanium)
 * </pre>
 * 模具<b>保留不删</b>（队长：模具不能删），电路是额外的识别维度；
 * 中子素不再替换成量子——两种流体现在各自独立成条。
 *
 * <h2>模具配方（直接套用 GTO）</h2>
 * 沿用 GTO 的流体固化：<b>576mB 钢 + 一把对应的工具（{@code forge:tools/<tag>}）→ 一次性模具</b>，
 * EUt 30、800 tick。这条配方由本类注入 GTCEu 现有的流体固化表
 * （{@code gtceu:fluid_solidification}），不新增机器。
 *
 * <h2>为什么在 {@link ServerStartedEvent} 注入而不是发数据包 JSON</h2>
 * 本工程既有纪律是「不跑 runData」，且这批配方要按<b>运行时真实存在的材料表</b>展开
 * （不同整合包/加料模组会多出材料）；照 {@code SpaceOreRecipeMirror} 的做法，
 * 在服务端配方管理器就绪后把 {@link GTRecipe} 同时写进两处：
 * <ol>
 *   <li><b>lookup</b>：{@code GTRecipeType.getLookup().addRecipe(...)} —— 机器找配方走这里；</li>
 *   <li><b>byType</b>：{@code RecipeManager.byType} 对应类型那一格 —— JEI 分类与样板识别读这里。</li>
 * </ol>
 * 只写其中一处会出现「机器能跑但 JEI 空」或「JEI 有但机器不认」。
 *
 * <h2>幂等 / 容错</h2>
 * 每次 {@link ServerStartedEvent} 重建一次；构建/注入全程 try/catch，失败只打日志，绝不影响启动。
 * 本模组这张表没有数据包 JSON，注入前会 {@code removeAllRecipes()} 清掉自己的旧对象，不会重复；
 * 流体固化表<b>绝不清空</b>（那会清掉 GT 自己的配方），只按键补缺。
 */
@Mod.EventBusSubscriber(modid = GTLModular.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DisposableToolPrintRecipes {

    /** 单次打印消耗的材料流体（mB），GTO 原值。 */
    public static final int FLUID_PER_TOOL = 72;

    /** 打印配方电压（GTO 原值 = LV 30 EU/t）。 */
    public static final long PRINT_EUT = 30L;

    /** 浇铸一次性模具：钢 576mB（GTO 原值）。 */
    public static final int MOLD_STEEL = 576;

    /** 浇铸模具电压/时长（GTO 原值）。 */
    public static final long MOLD_EUT = 30L;
    public static final int MOLD_DURATION = 800;

    /**
     * ★ 2026-10-30 队长口径：配方只保留这 6 种流体，且用「1~6 号编程电路」做额外识别：
     * <ol>
     *   <li>电路 1 = 液态铁（{@code iron}）</li>
     *   <li>电路 2 = 液态钢（{@code steel}）</li>
     *   <li>电路 3 = 液态钛（{@code titanium}）</li>
     *   <li>电路 4 = 液态硅岩合金（{@code naquadah_alloy}）</li>
     *   <li>电路 5 = 液态中子素（{@code neutronium}）</li>
     *   <li>电路 6 = 液态量子（{@code quantanium}，gtlcore 的「量子」）</li>
     * </ol>
     * 每条配方 = <b>对应一次性模具（不消耗）+ 1~6 号电路（不消耗）+ 72mB 该流体</b> → 脆弱工具。
     * 模具仍保留（队长：模具不能删），电路是额外的识别维度。
     */
    private static final java.util.List<PrintMaterial> ALLOWED_MATERIALS = java.util.List.of(
            new PrintMaterial("iron", 1),
            new PrintMaterial("steel", 2),
            new PrintMaterial("titanium", 3),
            new PrintMaterial("naquadah_alloy", 4),
            new PrintMaterial("neutronium", 5),
            new PrintMaterial("quantanium", 6));

    /** 一条允许的「材料 + 电路编号」映射。 */
    private record PrintMaterial(String materialName, int circuit) {
    }

    private static List<GTRecipe> toolRecipes;
    private static Map<ResourceLocation, GTRecipe> toolRecipeMap;
    private static List<GTRecipe> basicToolRecipes;
    private static Map<ResourceLocation, GTRecipe> basicToolRecipeMap;
    private static List<GTRecipe> moldRecipes;
    private static Map<ResourceLocation, GTRecipe> moldRecipeMap;
    private static boolean installed;

    /** 解析出来的 RecipeManager.byType（每个 ServerStartedEvent 重新写入，null 表示解析失败） */
    private static Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> byTypeMap;

    private DisposableToolPrintRecipes() {}

    // ------------------------------------------------------------------ 事件

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        install(event.getServer());
    }

    /**
     * 构建并注入两批配方（幂等：重复调用覆盖自己那一份，不会重复累加）。
     */
    public static synchronized void install(MinecraftServer server) {
        if (server == null) {
            return;
        }
        try {
            buildIfNeeded();
            if (toolRecipes == null || toolRecipes.isEmpty()) {
                GTLModular.LOGGER.warn("[gtlmodular] 一次性工具 3D 打印：没有生成任何配方"
                        + "（材料表为空或脆弱工具物品未注册？）");
                return;
            }
            byTypeMap = byType(server.getRecipeManager());
            injectToolRecipes(DisposableToolPrintTypes.DISPOSABLE_TOOL_PRINTING,
                    toolRecipes, toolRecipeMap);
            injectToolRecipes(DisposableToolPrintTypes.DISPOSABLE_TOOL_PRINTING_BASIC,
                    basicToolRecipes, basicToolRecipeMap);
            injectMoldRecipes(server.getRecipeManager());
            installed = true;
            GTLModular.LOGGER.info("[gtlmodular] 一次性工具 3D 打印配方已就绪：多方块(带电路) {} 条 / 单方块(原版) {} 条 / 模具 {} 条",
                    toolRecipes.size(), basicToolRecipes.size(), moldRecipes == null ? 0 : moldRecipes.size());
        } catch (Throwable t) {
            GTLModular.LOGGER.warn("[gtlmodular] 一次性工具 3D 打印配方注入失败（已忽略，不影响启动）：{}",
                    t.toString());
        }
    }

    public static boolean isInstalled() {
        return installed;
    }

    // ------------------------------------------------------------------ 构建

    private static void buildIfNeeded() {
        if (toolRecipes != null) {
            return;
        }
        List<GTRecipe> tools = new ArrayList<>();
        Map<ResourceLocation, GTRecipe> toolsById = new LinkedHashMap<>();
        List<GTRecipe> basicTools = new ArrayList<>();
        Map<ResourceLocation, GTRecipe> basicToolsById = new LinkedHashMap<>();
        List<GTRecipe> molds = new ArrayList<>();
        Map<ResourceLocation, GTRecipe> moldsById = new LinkedHashMap<>();

        for (DisposableTools tool : DisposableTools.values()) {
            Item fragile = fragileItem(tool);
            if (fragile == null) {
                // 本包没装脆弱工具（KubeJS 未注册）⇒ 该工具整体跳过，不生成引用空气的配方
                GTLModular.LOGGER.info("[gtlmodular] 跳过一次性工具 {}：物品 {} 不存在",
                        tool.path, tool.fragileItemId());
                continue;
            }
            ItemStack mold = DisposableToolPrintItems.mold(tool);
            if (mold.isEmpty()) {
                continue;
            }

            // ---- 模具浇铸：576mB 钢 + forge:tools/<tag> → 模具
            GTRecipe moldRecipe = GTRecipeTypes.FLUID_SOLIDFICATION_RECIPES
                    .recipeBuilder(new ResourceLocation(GTLModular.MOD_ID, "disposable_" + tool.path + "_mold"))
                    .inputItems(toolTag(tool))
                    .inputFluids(com.gregtechceu.gtceu.common.data.GTMaterials.Steel.getFluid(MOLD_STEEL))
                    .outputItems(mold.copy())
                    .duration(MOLD_DURATION)
                    .EUt(MOLD_EUT)
                    .buildRawRecipe();
            molds.add(moldRecipe);
            moldsById.put(moldRecipe.id, moldRecipe);

            // ---- 一次性工具打印：只做 6 种白名单流体，每条带「1~6 号电路」额外识别
            for (PrintMaterial spec : ALLOWED_MATERIALS) {
                Material material = GTCEuAPI.materialManager.getMaterial(spec.materialName());
                if (material == null) {
                    GTLModular.LOGGER.warn("[gtlmodular] 一次性工具：白名单材料 {} 不在本包，跳过",
                            spec.materialName());
                    continue;
                }
                if (!material.hasFluid()) {
                    GTLModular.LOGGER.warn("[gtlmodular] 一次性工具：材料 {} 没有流体，跳过",
                            spec.materialName());
                    continue;
                }
                ToolProperty toolProperty = material.getProperty(PropertyKey.TOOL);
                // ★ 液态量子（quantanium）在 gtlcore 里只有锭+流体，没有 TOOL 属性
                //   ⇒ 没有「材料耐久」可算。这里用中子素的工具耐久当同档替代；
                //   中子素也没有时退到 4096（保证液态量子一定有配方，不会整条消失）。
                int durability = toolProperty != null ? Math.max(1, toolProperty.getDurability())
                        : fallbackDurability();
                FluidStack fluid = material.getFluid(FLUID_PER_TOOL);
                if (fluid == null || fluid.getAmount() <= 0) {
                    continue;
                }
                // ★ 2026-10-30 队长口径：按 GTO 原值出，不封顶（原值 = 材料耐久 / consume，可能 > 64）。
                //   GTCEu 的输出处理会把超堆数量按槽位逐个摊开（NotifiableItemStackHandler.handleRecipe 的
                //   output 分支 for-slots 循环），槽位不够的余量由机器的输出仓容量决定。
                int count = Math.max(1, durability / tool.consume);
                int duration = (int) Math.max(1L, Math.min(Integer.MAX_VALUE, material.getMass()));
                // 多方块版：模具 + 1~6 号电路 + 流体
                GTRecipe recipe = DisposableToolPrintTypes.DISPOSABLE_TOOL_PRINTING
                        .recipeBuilder(new ResourceLocation(GTLModular.MOD_ID,
                                safePath(material.getName() + "_to_" + tool.path)))
                        .notConsumable(mold.copy())
                        .circuitMeta(spec.circuit())
                        .inputFluids(fluid.copy())
                        .outputItems(new ItemStack(fragile, count))
                        .duration(duration)
                        .EUt(PRINT_EUT)
                        .buildRawRecipe();
                tools.add(recipe);
                toolsById.put(recipe.id, recipe);

                // 单方块版（原版流程）：只有模具 + 流体，不带电路
                GTRecipe basic = DisposableToolPrintTypes.DISPOSABLE_TOOL_PRINTING_BASIC
                        .recipeBuilder(new ResourceLocation(GTLModular.MOD_ID,
                                "basic_" + safePath(material.getName() + "_to_" + tool.path)))
                        .notConsumable(mold.copy())
                        .inputFluids(fluid.copy())
                        .outputItems(new ItemStack(fragile, count))
                        .duration(duration)
                        .EUt(PRINT_EUT)
                        .buildRawRecipe();
                basicTools.add(basic);
                basicToolsById.put(basic.id, basic);
            }
        }

        toolRecipes = tools;
        toolRecipeMap = toolsById;
        basicToolRecipes = basicTools;
        basicToolRecipeMap = basicToolsById;
        moldRecipes = molds;
        moldRecipeMap = moldsById;
    }

    /** 无 TOOL 属性材料（目前只有液态量子）的耐久回退值：优先借中子素，其次 4096。 */
    private static int fallbackDurability() {
        Material neutronium = GTCEuAPI.materialManager.getMaterial("neutronium");
        if (neutronium != null && neutronium.hasProperty(PropertyKey.TOOL)) {
            ToolProperty property = neutronium.getProperty(PropertyKey.TOOL);
            if (property != null && property.getDurability() > 0) {
                return property.getDurability();
            }
        }
        return 4096;
    }

    /**
     * 配方 id 安全化：{@code ResourceLocation} 的 path 只允许 {@code [a-z0-9/._-]}。
     * GT 材料名一般是小写下划线，但第三方加料模组可能混入大写/其他字符；
     * 一旦构造 {@code ResourceLocation} 抛异常，整批注入会被外层 catch 掉（等于一条配方都没有），
     * 所以这里先归一化再拼 id。
     */
    private static String safePath(String raw) {
        return raw.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9/._-]", "_");
    }

    private static Item fragileItem(DisposableTools tool) {
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(tool.fragileItemId()));
        return item == null ? null : item;
    }

    private static TagKey<Item> toolTag(DisposableTools tool) {
        return TagKey.create(Registries.ITEM, new ResourceLocation("forge", "tools/" + tool.tagPath));
    }

    // ------------------------------------------------------------------ 注入

    private static void injectToolRecipes(GTRecipeType type, List<GTRecipe> recipes,
                                          Map<ResourceLocation, GTRecipe> byId) {
        GTRecipeLookup lookup = type.getLookup();
        // 本表没有数据包 JSON，清掉上一轮注入的对象再灌，天然幂等
        lookup.removeAllRecipes();
        int ok = 0;
        int rejected = 0;
        for (GTRecipe recipe : recipes) {
            if (lookup.addRecipe(recipe)) {
                ok++;
            } else {
                rejected++;
            }
        }
        if (rejected > 0) {
            GTLModular.LOGGER.warn("[gtlmodular] 一次性工具（{}）：{} 条配方被 lookup 拒绝（尺寸/冲突），成功 {} 条",
                    type.registryName, rejected, ok);
        }
        if (byTypeMap != null) {
            byTypeMap.computeIfAbsent(type, k -> new HashMap<>()).putAll(byId);
        }
    }

    private static void injectMoldRecipes(RecipeManager manager) {
        byTypeMap = byType(manager);
        if (moldRecipes == null || moldRecipes.isEmpty()) {
            return;
        }
        GTRecipeLookup lookup = GTRecipeTypes.FLUID_SOLIDFICATION_RECIPES.getLookup();
        for (GTRecipe recipe : moldRecipes) {
            // 只补缺：同一 id 已经在 lookup 里就不重复加（/reload 后 lookup 会被 GT 重建）
            lookup.addRecipe(recipe);
        }
        // JEI 读 byType：固化表按键合并，不动 GT 自己的配方
        if (byTypeMap != null) {
            Map<ResourceLocation, Recipe<?>> solid = byTypeMap.computeIfAbsent(
                    GTRecipeTypes.FLUID_SOLIDFICATION_RECIPES, k -> new HashMap<>());
            for (Map.Entry<ResourceLocation, GTRecipe> entry : moldRecipeMap.entrySet()) {
                solid.put(entry.getKey(), entry.getValue());
            }
        }
    }

    // ------------------------------------------------------------------ byType 反射

    private static Field byTypeField;
    private static boolean byTypeFailed;

    /**
     * 取 {@code RecipeManager.byType}：
     * 生产（SRG）字段名 {@code f_44007_}、开发（official 映射）{@code recipes}；两次都失败就放弃（只打一次 WARN）。
     */
    @SuppressWarnings("unchecked")
    private static Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> byType(RecipeManager manager) {
        if (byTypeFailed) {
            return null;
        }
        try {
            if (byTypeField == null) {
                for (String name : new String[]{"f_44007_", "recipes"}) {
                    try {
                        Field field = RecipeManager.class.getDeclaredField(name);
                        field.setAccessible(true);
                        byTypeField = field;
                        break;
                    } catch (NoSuchFieldException ignored) {
                        // 换下一个候选名
                    }
                }
                if (byTypeField == null) {
                    byTypeFailed = true;
                    GTLModular.LOGGER.warn("[gtlmodular] 找不到 RecipeManager.byType 字段"
                            + "（f_44007_ / recipes）⇒ 3D 打印配方只会进 lookup，JEI 可能不显示");
                    return null;
                }
            }
            return (Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>>) byTypeField.get(manager);
        } catch (Throwable t) {
            byTypeFailed = true;
            GTLModular.LOGGER.warn("[gtlmodular] 读取 RecipeManager.byType 失败（已停用 byType 注入）：{}",
                    t.toString());
            return null;
        }
    }
}
