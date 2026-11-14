package com.gtlmodular.common.data.stellar;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.data.recipe.builder.GTRecipeBuilder;
import com.gtlmodular.GTLModular;
import com.lowdragmc.lowdraglib.side.fluid.FluidStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * GTO「恒星炎炀锻炉」移植的 <b>31 条配方</b> —— 从 KubeJS 脚本迁入模组本体。
 *
 * <h2>迁移缘由（2026-11-14）</h2>
 * 原脚本 {@code kubejs/server_scripts/gtlmodular_stellar_forge_materials.js}（14,593 B）
 * 在实例里<b>被删除</b>后，材料仍在（{@link StellarForgeMaterials} 是 Java 注册），
 * 但三条合金熔炼与形态链、两套陶瓷链、六个机壳配方<b>全部消失</b> ⇒ 玩家报
 * 「巴氏合金锭 / 贫铀合金锭 / 碳化凯金硅岩合金锭 合不出来」。
 * 迁移进 jar 后配方不再依赖 KubeJS 脚本的存亡。
 *
 * <h2>与 KubeJS 原脚本的对应关系（数值逐项一致）</h2>
 * <ul>
 *   <li><b>三味合金熔炼</b>（GTO 原为 {@code alloy_blast_smelter} 出熔融流体；本包无该多方块、
 *       且新材料熔融流体未注册 ⇒ 按原裁决改 {@code electric_blast_furnace} 直接出锭，
 *       每条产 8 锭 = GTO 的 1152mB / 144mB）；</li>
 *   <li><b>三味形态链</b> 12 条（bender 板/双板、extruder 杆、assembler 框）；</li>
 *   <li><b>两套陶瓷链</b> 10 条（sifter → compressor → EBF → cutter → cutter）；</li>
 *   <li><b>六个机壳</b> 6 条（assembler）。</li>
 * </ul>
 *
 * <h2>注入时机与写法</h2>
 * 与 {@code TentacleRecipes} / {@code CoolingTowerRecipe} 同一套：挂 Forge 事件总线
 * {@code ServerStartedEvent}，构建 {@link GTRecipe} 后<b>双写</b>
 * <ol>
 *   <li>lookup —— {@code GTRecipeType.getLookup().addRecipe(...)}：机器找配方走这里；</li>
 *   <li>byType —— {@code RecipeManager.byType}：JEI 分类与样板识别读这里。</li>
 * </ol>
 * 每条独立 try/catch：一条失败不影响其它条，也绝不崩服。
 *
 * <h2>缺件处理</h2>
 * 输入/输出物品（或流体）在运行期注册表里不存在时，该条配方<b>跳过并 WARN</b>，
 * 而不是写入一条永远跑不动的空配方。启动日志会给出逐条明细。
 */
public final class StellarForgeRecipes {

    /** 配方 id 统一前缀：{@code gtlmodular:stellar/...}（与原 KubeJS 脚本一致，便于对账）。 */
    private static final String PREFIX = "stellar/";

    private static Field byTypeField;
    private static boolean byTypeFailed;
    private static Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> byTypeMap;

    private StellarForgeRecipes() {
    }

    /** 在 {@code GTLModular} 构造里调用一次（Forge 事件总线）。 */
    public static void register() {
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(StellarForgeRecipes::onServerStarted);
        GTLModular.LOGGER.info("[gtlmodular] 恒星炎炀锻炉：31 条配方（合金/形态链/陶瓷/机壳）已挂载，"
                + "服务端启动完成后注入（不再依赖 KubeJS 脚本）");
    }

    private static void onServerStarted(ServerStartedEvent event) {
        install(event.getServer());
    }

    /** 幂等：重复调用只是把同一 id 的配方再写一遍（lookup 对同 id 同内容返回 false，byType 是 put 覆盖）。 */
    public static synchronized void install(MinecraftServer server) {
        if (server == null) {
            return;
        }
        try {
            byTypeMap = byType(server.getRecipeManager());
            List<String> skipped = new ArrayList<>();
            int ok = 0;

            ok += installAlloys(skipped);
            ok += installAlloyShapes(skipped);
            ok += installCeramics(skipped);
            ok += installCasings(skipped);

            GTLModular.LOGGER.info("[gtlmodular] 恒星炎炀锻炉：配方注入完成 {} / 31 条（跳过 {} 条）",
                    ok, skipped.size());
            if (!skipped.isEmpty()) {
                GTLModular.LOGGER.warn("[gtlmodular] 恒星炎炀锻炉：被跳过的配方（依赖物品/流体缺失）：{}", skipped);
            }
        } catch (Throwable t) {
            GTLModular.LOGGER.warn("[gtlmodular] 恒星炎炀锻炉：配方注入失败（已忽略，不影响启动）：{}", t.toString());
        }
    }

    // =========================================================================
    // 1. 三味合金熔炼（EBF；GTO 原为合金高炉，见类注释）
    // =========================================================================

    private static int installAlloys(List<String> skipped) {
        int ok = 0;

        // 巴氏合金：5 锡粉 + 36 铅粉 + 8 锑粉 + 1 砹粉 + 编程电路 → 8 锭
        // 依据 gtceu:alloy_blast_smelter/babbitt_alloy（120 EU/t / 8625t；GTO 7200mB = 8 锭）
        ok += installSimple(skipped, GTRecipeTypes.BLAST_RECIPES, "babbitt_alloy",
                120L, 8625, 800,
                new String[]{"gtceu:tin_dust", "5", "gtceu:lead_dust", "36",
                        "gtceu:antimony_dust", "8", "gtceu:astatine_dust", "1",
                        "gtceu:programmed_circuit", "1"},
                new String[]{"gtceu:babbitt_alloy_ingot", "8"},
                null, 0);

        // 贫铀合金：9 铀粉 + 1 钛粉 + 编程电路 → 8 锭
        // 依据 gtceu:alloy_blast_smelter/depleted_uranium_alloy（1920 EU/t / 3465t）
        ok += installSimple(skipped, GTRecipeTypes.BLAST_RECIPES, "depleted_uranium_alloy",
                1920L, 3465, 3450,
                new String[]{"gtceu:uranium_dust", "9", "gtceu:titanium_dust", "1",
                        "gtceu:programmed_circuit", "1"},
                new String[]{"gtceu:depleted_uranium_alloy_ingot", "8"},
                null, 0);

        // 碳化凯金硅岩合金：6 凯金粉 + 2 硅岩粉 + 1 碳粉 + 编程电路 → 8 锭
        // 依据 gtceu:alloy_blast_smelter/trinaquadalloy（122880 EU/t / 8100t）
        ok += installSimple(skipped, GTRecipeTypes.BLAST_RECIPES, "trinaquadalloy",
                122880L, 8100, 8747,
                new String[]{"gtceu:trinium_dust", "6", "gtceu:naquadah_dust", "2",
                        "gtceu:carbon_dust", "1", "gtceu:programmed_circuit", "1"},
                new String[]{"gtceu:trinaquadalloy_ingot", "8"},
                null, 0);

        return ok;
    }

    // =========================================================================
    // 2. 三味合金形态链（GTCEu 1.4.4 不会给新材料自动配板/杆/框，手写）
    // =========================================================================

    private static final String[] ALLOY_SHAPES = {
            "babbitt_alloy", "depleted_uranium_alloy", "trinaquadalloy"
    };

    private static int installAlloyShapes(List<String> skipped) {
        int ok = 0;
        for (String material : ALLOY_SHAPES) {
            // 2.1 板：1 锭 → 1 板（bender，EUt 120 / 100t）
            ok += installSimple(skipped, GTRecipeTypes.BENDER_RECIPES, material + "_plate",
                    120L, 100, 0,
                    new String[]{"gtceu:" + material + "_ingot", "1"},
                    new String[]{"gtceu:" + material + "_plate", "1"},
                    null, 0);

            // 2.2 双板：2 板 → 1 双板（bender，EUt 120 / 100t）
            ok += installSimple(skipped, GTRecipeTypes.BENDER_RECIPES, "double_" + material + "_plate",
                    120L, 100, 0,
                    new String[]{"gtceu:" + material + "_plate", "2"},
                    new String[]{"gtceu:double_" + material + "_plate", "1"},
                    null, 0);

            // 2.3 杆：1 锭 + 杆挤压模具（不消耗） → 2 杆（extruder，EUt 120 / 100t）
            ok += installSimple(skipped, GTRecipeTypes.EXTRUDER_RECIPES, material + "_rod",
                    120L, 100, 0,
                    new String[]{"gtceu:" + material + "_ingot", "1"},
                    new String[]{"gtceu:" + material + "_rod", "2"},
                    "gtceu:rod_extruder_mold", 0);

            // 2.4 框：4 杆 + 编程电路 → 1 框（assembler，EUt 30 / 100t）
            ok += installSimple(skipped, GTRecipeTypes.ASSEMBLER_RECIPES, material + "_frame",
                    30L, 100, 0,
                    new String[]{"gtceu:" + material + "_rod", "4", "gtceu:programmed_circuit", "1"},
                    new String[]{"gtceu:" + material + "_frame", "1"},
                    null, 0);
        }
        return ok;
    }

    // =========================================================================
    // 3. 两套陶瓷链（碳化硼陶瓷 / 氧化镁陶瓷，各 5 条）
    //    机器口径（原裁决）：等静压成型 → compressor；烧结炉 → EBF + 炉温 1200
    // =========================================================================

    private static int installCeramics(List<String> skipped) {
        int ok = 0;
        ok += installCeramicChain(skipped, "boron_carbide_ceramics",
                "gtceu:boron_carbide_dust", 100, "gtceu:epoxy", 1000);
        ok += installCeramicChain(skipped, "magnesium_oxide_ceramic",
                "gtceu:magnesia_dust", 140, "gtceu:glue", 1000);
        return ok;
    }

    private static int installCeramicChain(List<String> skipped, String name,
                                          String sourceDust, int siftDuration,
                                          String binderFluid, int binderAmount) {
        int ok = 0;

        // 3.1 筛选机：2 原料粉 → 1 陶瓷粉
        ok += installSimple(skipped, GTRecipeTypes.SIFTER_RECIPES, name + "_dust",
                240L, siftDuration, 0,
                new String[]{sourceDust, "2"},
                new String[]{"gtceu:" + name + "_dust", "1"},
                null, 0);

        // 3.2 压缩机（替代 GTO 等静压成型）：9 陶瓷粉 + 1000mB 结合剂 → 1 毛坯
        ok += installSimple(skipped, GTRecipeTypes.COMPRESSOR_RECIPES, name + "_rough_blank",
                480L, 200, 0,
                new String[]{"gtceu:" + name + "_dust", "9"},
                new String[]{"gtlmodular:" + name + "_rough_blank", "1"},
                null, 0, binderFluid, binderAmount);

        // 3.3 电力高炉（替代 GTO 烧结炉）：毛坯 → 陶瓷块，炉温 1200
        ok += installSimple(skipped, GTRecipeTypes.BLAST_RECIPES, name + "_block",
                120L, 400, 1200,
                new String[]{"gtlmodular:" + name + "_rough_blank", "1"},
                new String[]{"gtlmodular:" + name + "_block", "1"},
                null, 0);

        // 3.4 切割机：陶瓷块 + 12mB 润滑剂 → 9 砖
        ok += installSimple(skipped, GTRecipeTypes.CUTTER_RECIPES, name + "_brick",
                120L, 300, 0,
                new String[]{"gtlmodular:" + name + "_block", "1"},
                new String[]{"gtlmodular:" + name + "_brick", "9"},
                null, 0, "gtceu:lubricant", 12);

        // 3.5 切割机：砖 + 100mB 水 → 4 薄片
        ok += installSimple(skipped, GTRecipeTypes.CUTTER_RECIPES, name + "_flake",
                30L, 200, 0,
                new String[]{"gtlmodular:" + name + "_brick", "1"},
                new String[]{"gtlmodular:" + name + "_flake", "4"},
                null, 0, "minecraft:water", 100);

        return ok;
    }

    // =========================================================================
    // 4. 六个机壳（装配机；产物为 StellarForgeBlocks 注册的方块）
    // =========================================================================

    private static int installCasings(List<String> skipped) {
        int ok = 0;

        // 4.1 辐射吸收机械方块（GTO radiation_absorbent_casing：120 / 200t，ultimet 288mB）
        ok += installSimple(skipped, GTRecipeTypes.ASSEMBLER_RECIPES, "radiation_absorbent_casing",
                120L, 200, 0,
                new String[]{"gtceu:depleted_uranium_alloy_frame", "1",
                        "gtceu:double_babbitt_alloy_plate", "2",
                        "gtceu:double_rhodium_plated_palladium_plate", "4"},
                new String[]{"gtlmodular:radiation_absorbent_casing", "1"},
                null, 0, "gtceu:ultimet", 288);

        // 4.2 碳化硼陶瓷耐辐射机械方块（GTO：30 / 200t）
        ok += installSimple(skipped, GTRecipeTypes.ASSEMBLER_RECIPES,
                "boron_carbide_ceramic_radiation_resistant_mechanical_cube",
                30L, 200, 0,
                new String[]{"gtceu:ruridit_frame", "1",
                        "gtceu:titanium_tungsten_carbide_plate", "2",
                        "gtlmodular:boron_carbide_ceramics_flake", "16"},
                new String[]{"gtlmodular:boron_carbide_ceramic_radiation_resistant_mechanical_cube", "1"},
                null, 0);

        // 4.3 氧化镁陶瓷高温绝缘机械方块（GTO：30 / 200t）
        ok += installSimple(skipped, GTRecipeTypes.ASSEMBLER_RECIPES,
                "magnesium_oxide_ceramic_high_temperature_insulation_mechanical_block",
                30L, 200, 0,
                new String[]{"gtceu:hsla_steel_frame", "1",
                        "gtceu:tungsten_carbide_plate", "2",
                        "gtlmodular:magnesium_oxide_ceramic_flake", "16"},
                new String[]{"gtlmodular:magnesium_oxide_ceramic_high_temperature_insulation_mechanical_block", "1"},
                null, 0);

        // 4.4 磁力机械方块（GTO magtech_casing：16 / 50t）
        ok += installSimple(skipped, GTRecipeTypes.ASSEMBLER_RECIPES, "magtech_casing",
                16L, 50, 0,
                new String[]{"gtceu:tungsten_frame", "1",
                        "gtceu:nichrome_plate", "2",
                        "gtceu:indium_tin_barium_titanium_cuprate_plate", "4",
                        "gtceu:hsss_plate", "2",
                        "gtceu:programmed_circuit", "1"},
                new String[]{"gtlmodular:magtech_casing", "1"},
                null, 0);

        // 4.5 等离子加热机械方块（GTO:120 / 200t，osmiridium 288mB）
        ok += installSimple(skipped, GTRecipeTypes.ASSEMBLER_RECIPES, "plasma_heater_casing",
                120L, 200, 0,
                new String[]{"gtceu:trinium_frame", "1",
                        "gtceu:luv_voltage_coil", "2",
                        "gtceu:rhenium_plate", "2",
                        "gtceu:tungsten_screw", "8",
                        "gtceu:trinium_plate", "4"},
                new String[]{"gtlmodular:plasma_heater_casing", "1"},
                null, 0, "gtceu:osmiridium", 288);

        // 4.6 中子稳定机械方块（GTO:480 / 200t）
        //     两处与 GTO 原配方的必要偏差（原脚本注释已交代，此处保留）：
        //       a) GTO 输入 4x gtocore:long_amprosium_rod → 本包无 amprosium ⇒ 4x gtceu:long_neutronium_rod；
        //       b) GTO 流体 576mB gtocore:depleted_uranium_alloy（未注册）⇒ 等价换成
        //          4x gtceu:depleted_uranium_alloy_ingot（144mB/锭）。
        ok += installSimple(skipped, GTRecipeTypes.ASSEMBLER_RECIPES, "neutronium_stable_casing",
                480L, 200, 0,
                new String[]{"gtceu:trinaquadalloy_frame", "1",
                        "gtceu:long_neutronium_rod", "4",
                        "gtceu:enriched_naquadah_trinium_europium_duranide_plate", "4",
                        "gtceu:double_naquadria_plate", "4",
                        "gtceu:depleted_uranium_alloy_ingot", "4"},
                new String[]{"gtlmodular:neutronium_stable_casing", "1"},
                null, 0);

        return ok;
    }

    // =========================================================================
    // 构建 / 注入
    // =========================================================================

    private static int installSimple(List<String> skipped, GTRecipeType type, String id,
                                     long eut, int duration, int blastTemp,
                                     String[] items, String[] output,
                                     String notConsumableId, int notConsumableCount) {
        return installSimple(skipped, type, id, eut, duration, blastTemp,
                items, output, notConsumableId, notConsumableCount, null, 0);
    }

    /**
     * 构建并注入一条配方。
     *
     * @param items             平铺的「id, 数量, id, 数量…」物品输入
     * @param output            平铺的「id, 数量」物品输出
     * @param notConsumableId   不消耗输入（可为 null）
     * @param fluidId           流体输入（可为 null）
     * @param blastTemp         &gt;0 时写入炉温（仅 EBF）
     * @return 成功条数（0/1）
     */
    private static int installSimple(List<String> skipped, GTRecipeType type, String id,
                                     long eut, int duration, int blastTemp,
                                     String[] items, String[] output,
                                     String notConsumableId, int notConsumableCount,
                                     String fluidId, int fluidAmount) {
        try {
            // 先解析全部输入输出：任何一件缺失就整条跳过（避免写入跑不动的空配方）
            Map<Item, Integer> inputStacks = new LinkedHashMap<>();
            for (int i = 0; i + 1 < items.length; i += 2) {
                ItemStack stack = stack(items[i], Integer.parseInt(items[i + 1]));
                if (stack.isEmpty()) {
                    skipped.add(id + "（缺输入 " + items[i] + "）");
                    return 0;
                }
                inputStacks.merge(stack.getItem(), stack.getCount(), Integer::sum);
            }
            ItemStack out = stack(output[0], Integer.parseInt(output[1]));
            if (out.isEmpty()) {
                skipped.add(id + "（缺产物 " + output[0] + "）");
                return 0;
            }
            ItemStack notConsumable = ItemStack.EMPTY;
            if (notConsumableId != null) {
                notConsumable = stack(notConsumableId, Math.max(1, notConsumableCount));
                if (notConsumable.isEmpty()) {
                    skipped.add(id + "（缺不消耗输入 " + notConsumableId + "）");
                    return 0;
                }
            }
            FluidStack fluidStack = null;
            if (fluidId != null) {
                fluidStack = fluid(fluidId, fluidAmount);
                if (fluidStack == null) {
                    skipped.add(id + "（缺流体 " + fluidId + "）");
                    return 0;
                }
            }

            GTRecipeBuilder builder = type.recipeBuilder(new ResourceLocation(GTLModular.MOD_ID, PREFIX + id));
            for (Map.Entry<Item, Integer> e : inputStacks.entrySet()) {
                builder.inputItems(new ItemStack(e.getKey(), e.getValue()));
            }
            if (!notConsumable.isEmpty()) {
                builder.notConsumable(notConsumable);
            }
            if (fluidStack != null) {
                builder.inputFluids(fluidStack);
            }
            builder.outputItems(out);
            builder.EUt(eut).duration(duration);
            if (blastTemp > 0) {
                builder.blastFurnaceTemp(blastTemp);
            }
            return inject(type, builder.buildRawRecipe());
        } catch (Throwable t) {
            GTLModular.LOGGER.warn("[gtlmodular] 恒星炎炀锻炉：构建配方 {} 失败（已跳过）：{}", id, t.toString());
            skipped.add(id + "（构建异常）");
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
                GTLModular.LOGGER.warn("[gtlmodular] 恒星炎炀锻炉：byType 不可用 ⇒ {} 只进 lookup（机器能跑，JEI 可能不显示）",
                        recipe.id);
            }
            return added ? 1 : 0;
        } catch (Throwable t) {
            GTLModular.LOGGER.warn("[gtlmodular] 恒星炎炀锻炉：登记 {} 失败（已忽略）：{}", recipe.id, t.toString());
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
                    GTLModular.LOGGER.warn("[gtlmodular] 恒星炎炀锻炉：找不到 RecipeManager.byType 字段"
                            + "（f_44007_ / recipes）⇒ 运行期配方只会进 lookup，JEI 可能不显示");
                    return null;
                }
            }
            return (Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>>) byTypeField.get(manager);
        } catch (Throwable t) {
            byTypeFailed = true;
            GTLModular.LOGGER.warn("[gtlmodular] 恒星炎炀锻炉：读取 RecipeManager.byType 失败：{}", t.toString());
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
