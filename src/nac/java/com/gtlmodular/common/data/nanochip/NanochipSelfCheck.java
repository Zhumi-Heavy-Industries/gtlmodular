package com.gtlmodular.common.data.nanochip;

import com.gregtechceu.gtceu.api.GTCEuAPI;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.chemical.material.properties.PropertyKey;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gtlmodular.common.machine.nanochip.NanochipCalibration;
import com.gtlmodular.common.machine.nanochip.NanochipSplitterRules;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/** 资源文件存在不代表运行期注册和配方加载成功，需要在游戏中检查实际注册表与配方管理器。 */
public final class NanochipSelfCheck {

    private NanochipSelfCheck() {
    }

    private static final String[] CASING_IDS = {
            "nanochip_mesh_interface_casing",
            "nanochip_reinforcement_casing",
            "nanochip_computational_matrix_casing",
            "nanochip_firewall_projection_casing",
            "nanochip_complex_glass",
    };

    private static final String[] UPKEEP_FLUIDS = {
            "gtceu:grade_1_purified_water",
            "gtceu:super_coolant",
            "gtceu:raw_growth_medium",
    };

    public static List<String> collect(MinecraftServer server) {
        List<String> out = new ArrayList<>();
        try {
            blockChecks(out);
            machineChecks(out);
            recipeChecks(out, server);
            materialFormChecks(out);
            autoChainChecks(out, server);
            fluidChecks(out);
            configChecks(out);
        } catch (Throwable t) {
            out.add("✗ 自检过程本身出错: " + t);
            NanochipModContext.logger().warn("[gtlmodular] NAC 自检失败", t);
        }
        return out;
    }

    public static void runAndLog(MinecraftServer server) {
        List<String> lines = collect(server);
        NanochipModContext.logger().info("[gtlmodular] ===== NAC 自检开始（{} 项）=====", lines.size());
        for (String line : lines) {
            NanochipModContext.logger().info("[gtlmodular] NAC 自检 | {}", line);
        }
        long failed = lines.stream().filter(l -> l.startsWith("✗")).count();
        NanochipModContext.logger().info("[gtlmodular] ===== NAC 自检结束：{} 项失败 =====", failed);
    }

    private static void blockChecks(List<String> out) {
        int ok = 0;
        for (String id : CASING_IDS) {
            ResourceLocation rl = NanochipModContext.registryId(id);
            boolean block = ForgeRegistries.BLOCKS.containsKey(rl);
            boolean item = ForgeRegistries.ITEMS.containsKey(rl);
            if (block && item) {
                ok++;
            } else {
                out.add("✗ 方块 " + rl + " block=" + block + " item=" + item);
            }
        }
        out.add((ok == CASING_IDS.length ? "✓" : "✗") + " 结构方块注册: " + ok + "/" + CASING_IDS.length);
    }

    private static void machineChecks(List<String> out) {
        int ok = 0;
        int total = 0;
        total++;
        if (hasMachine("nanochip_assembly_complex")) {
            ok++;
        } else {
            out.add("✗ 机器未注册: " + NanochipModContext.registryId("nanochip_assembly_complex"));
        }
        for (var kind : com.gtlmodular.common.machine.nanochip.registration.NanochipModules.all()) {
            total++;
            if (hasMachine(kind.id().toString())) {
                ok++;
            } else {
                out.add("✗ 模块未注册: " + kind.id() + "（" + kind.nameZh() + "）");
            }
        }
        total += 2;
        if (hasMachine("nanochip_vacuum_input_hatch")) {
            ok++;
        } else {
            out.add("✗ 仓室未注册: " + NanochipModContext.registryId("nanochip_vacuum_input_hatch"));
        }
        if (hasMachine("nanochip_vacuum_output_hatch")) {
            ok++;
        } else {
            out.add("✗ 仓室未注册: " + NanochipModContext.registryId("nanochip_vacuum_output_hatch"));
        }
        out.add((ok == total ? "✓" : "✗") + " 机器注册: " + ok + "/" + total
                + "（NAC + " + com.gtlmodular.common.machine.nanochip.registration.NanochipModules.all().size() + " 模块 + 2 仓室）");
    }

    private static boolean hasMachine(String path) {
        ResourceLocation rl = com.gtlmodular.common.machine.nanochip.registration.NanochipModules.parseId(path);
        MachineDefinition def = GTRegistries.MACHINES.get(rl);
        return def != null;
    }

    private static void recipeChecks(List<String> out, MinecraftServer server) {
        if (server == null) {
            out.add("- 配方检查跳过（没有服务器实例）");
            return;
        }
        var recipes = server.getRecipeManager().getRecipes();
        long ours = 0;
        long circuits = 0;
        java.util.Set<String> loaded = new java.util.HashSet<>();
        for (Recipe<?> recipe : recipes) {
            if (NanochipModContext.modId().equals(recipe.getId().getNamespace())) {
                ours++;
                loaded.add(recipe.getId().getPath());
            }
            if (recipe.getType() == com.gregtechceu.gtceu.common.data.GTRecipeTypes.CIRCUIT_ASSEMBLER_RECIPES) {
                circuits++;
            }
        }
        List<String> expectedList = NanochipExpectedRecipes.paths();
        String breakdown = NanochipExpectedRecipes.description();
        int expected = expectedList.size();
        out.add((ours == expected ? "✓" : "✗") + " 本模组配方加载: " + ours + " / " + expected
                + "（" + breakdown + "）");
        int missing = 0;
        for (String id : expectedList) {
            if (!loaded.contains(id)) {
                missing++;
                out.add("✗ 配方缺失: " + NanochipModContext.registryId(id));
            }
        }
        out.add((missing == 0 ? "✓" : "✗") + " 配方清单齐全: " + (expected - missing) + "/" + expected
                + "（逐条点名，不只是总数）");
        int extra = 0;
        for (String path : loaded) {
            if (!expectedList.contains(path)) {
                extra++;
                out.add("✗ 配方未登记进 EXPECTED_RECIPES（清单漏了它）: " + NanochipModContext.registryId(path));
            }
        }
        out.add((extra == 0 ? "✓" : "✗") + " 配方清单无遗漏: 额外未登记 " + extra + " 条");
        out.add((circuits > 0 ? "✓" : "✗") + " 电路组装机配方总数（GTCEu 原生表；★ 2026-10-14 起 NAC 不再借用它，"
                + "仅作上游表非空的哨兵）: " + circuits);
    }

    private static void materialFormChecks(List<String> out) {
        if (GTCEuAPI.materialManager == null) {
            out.add("- 材料形态检查跳过（GTCEu 材料管理器未就绪）");
            return;
        }
        checkMaterialForm(out, "enriched_holmium", 3000);
        checkMaterialForm(out, "superconductor_uhv_base", 10800);
        checkMaterialForm(out, "superconductor_uev_base", 11800);
    }

    private static void checkMaterialForm(List<String> out, String name, int expectedBlastTemp) {
        Material material = GTCEuAPI.materialManager.getMaterial(name);
        if (material == null) {
            out.add("✗ 材料未注册: gtceu:" + name);
            return;
        }
        boolean dust = material.hasProperty(PropertyKey.DUST);
        boolean ingot = material.hasProperty(PropertyKey.INGOT);
        int blastTemp = material.getBlastTemperature();
        boolean ok = dust && ingot && blastTemp == expectedBlastTemp;
        out.add((ok ? "✓" : "✗") + " 材料形态 gtceu:" + name
                + " DUST=" + dust + " INGOT=" + ingot + " blastTemp=" + blastTemp
                + "（期望 " + expectedBlastTemp + "；EBF/VF 自动链的前提）");
    }

    /** 材料自动生成的冶炼与真空配方没有对应 JSON，须在运行期单独检查产物链。 */
    private static void autoChainChecks(List<String> out, MinecraftServer server) {
        if (server == null) {
            out.add("- EBF/VF 自动链检查跳过（没有服务器实例）");
            return;
        }
        final String[] neededItems = {
                "gtceu:enriched_holmium_dust",
                "gtceu:enriched_holmium_ingot",
                "gtceu:hot_enriched_holmium_ingot",
                "gtceu:superconductor_uhv_base_dust",
                "gtceu:superconductor_uhv_base_ingot",
                "gtceu:hot_superconductor_uhv_base_ingot",
                "gtceu:superconductor_uev_base_dust",
                "gtceu:superconductor_uev_base_ingot",
                "gtceu:hot_superconductor_uev_base_ingot",
        };
        for (String id : neededItems) {
            boolean present = ForgeRegistries.ITEMS.containsKey(new ResourceLocation(id));
            out.add((present ? "✓" : "✗") + " 形态物品已注册: " + id);
        }
        countAutoRecipes(out, server, "EBF(BLAST_RECIPES)", GTRecipeTypes.BLAST_RECIPES,
                "gtceu:hot_enriched_holmium_ingot", "gtceu:hot_superconductor_uhv_base_ingot",
                "gtceu:hot_superconductor_uev_base_ingot");
        countAutoRecipes(out, server, "VF(VACUUM_RECIPES)", GTRecipeTypes.VACUUM_RECIPES,
                "gtceu:enriched_holmium_ingot", "gtceu:superconductor_uhv_base_ingot",
                "gtceu:superconductor_uev_base_ingot");
    }

    private static void countAutoRecipes(List<String> out, MinecraftServer server, String label,
                                        GTRecipeType type, String... expectedOutputs) {
        java.util.Set<String> found = new java.util.HashSet<>();
        int total = 0;
        for (Recipe<?> recipe : server.getRecipeManager().getRecipes()) {
            if (recipe.getType() != type) {
                continue;
            }
            total++;
            if (!(recipe instanceof GTRecipe gtRecipe)) {
                continue;
            }
            for (Content content : gtRecipe.getOutputContents(ItemRecipeCapability.CAP)) {
                String id = itemIdOf(content.getContent());
                if (id != null) {
                    found.add(id);
                }
            }
        }
        for (String id : expectedOutputs) {
            out.add((found.contains(id) ? "✓" : "✗") + " " + label + " 自动配方产出: " + id
                    + "（该表共 " + total + " 条配方，已扫到不同产出 " + found.size() + " 种）");
        }
    }

    private static String itemIdOf(Object content) {
        if (content instanceof ItemStack stack) {
            return stack.isEmpty() ? null : String.valueOf(ForgeRegistries.ITEMS.getKey(stack.getItem()));
        }
        if (content instanceof ItemStack[] stacks) {
            return firstItemId(stacks);
        }
        if (content instanceof net.minecraft.world.item.crafting.Ingredient ingredient) {
            try {
                return firstItemId(ingredient.getItems());
            } catch (Throwable ignored) {
                // 未解析标签可能无法提供样例物品，诊断按未读取处理，避免中断自检。
                return null;
            }
        }
        return null;
    }

    private static String firstItemId(ItemStack[] stacks) {
        for (ItemStack stack : stacks) {
            if (stack != null && !stack.isEmpty()) {
                return String.valueOf(ForgeRegistries.ITEMS.getKey(stack.getItem()));
            }
        }
        return null;
    }

    private static void fluidChecks(List<String> out) {
        for (String id : UPKEEP_FLUIDS) {
            ResourceLocation rl = new ResourceLocation(id);
            boolean present = ForgeRegistries.FLUIDS.containsKey(rl);
            out.add((present ? "✓" : "✗") + " 模块耗材流体存在: " + id);
        }
        int grades = 0;
        for (int i = 1; i <= 16; i++) {
            if (ForgeRegistries.FLUIDS.containsKey(new ResourceLocation("gtceu", "grade_" + i + "_purified_water"))) {
                grades++;
            }
        }
        out.add((grades == 16 ? "✓" : "✗") + " 净化水等级 1~16: " + grades + "/16（光学整理器按等级给加成）");
    }

    private static void configChecks(List<String> out) {
        out.add("· NAC 配置: 直通档并行上限=" + NanochipModContext.config().getNanochipDirectParallelCap()
                + " 每配方杂质=" + NanochipModContext.config().getNanochipImpurityPerRecipe() + "%"
                + " 调试日志=" + NanochipModContext.config().isNanochipDebugLogsEnabled());
        // 显示当前路由模式，便于区分未接管道与物流故障。
        out.add("· 真空路由模式: " + com.gtlmodular.common.machine.nanochip.NanochipAssemblyComplexMachine.routingModeText()
                + "（配置项 nanochipComplex.vacuumRequiresPipes="
                + NanochipModContext.config().isVacuumRequiresPipes() + "）");
        String denominatorSpec;
        if (NanochipCalibration.useFullWindowDenominator()) {
            long tier1 = Math.round(NanochipCalibration.Family.CRYSTAL.threshold(1)
                    * NanochipCalibration.HISTORY_SIZE);
            denominatorSpec = "固定 " + NanochipCalibration.HISTORY_SIZE
                    + "（原版硬编码 · 照原版；第一档 20％ = " + tier1 + " 个对应电路）";
        } else {
            denominatorSpec = "已记录数（移植版早期行为，窗口没攒满时升档更快）";
        }
        out.add("· 校准规格: 历史窗口=" + NanochipCalibration.HISTORY_SIZE + " 个电路（"
                + NanochipCalibration.BATCH_COUNT + " 批 × " + NanochipCalibration.BATCH_SIZE + "）"
                + " 判定分母=" + denominatorSpec);
        out.add("· 超频/份数规格: 封顶 " + com.gtlmodular.common.machine.nanochip.NanochipAssemblyComplexMachine.MAX_OVERCLOCKS
                + " 次 · 时长下限 全流程 "
                + com.gtlmodular.common.machine.nanochip.NanochipAssemblyComplexMachine.FULL_MIN_DURATION_TICKS / 20
                + "s / 直通 "
                + com.gtlmodular.common.machine.nanochip.NanochipAssemblyComplexMachine.DIRECT_MIN_DURATION_TICKS / 20 + "s");
        out.add("· 分流器规则上限: " + NanochipSplitterRules.MAX_RULES + " 条，每次单规则搬运上限 "
                + NanochipSplitterRules.MAX_TRANSFER_PER_RULE);
        out.add("· 结构尺寸: NAC " + NanochipComplexPattern.NAC_W + "×" + NanochipComplexPattern.NAC_H
                + "×" + NanochipComplexPattern.NAC_D + " / 模块 " + NanochipComplexPattern.MOD_W + "×"
                + NanochipComplexPattern.MOD_H + "×" + NanochipComplexPattern.MOD_D);
    }
}
