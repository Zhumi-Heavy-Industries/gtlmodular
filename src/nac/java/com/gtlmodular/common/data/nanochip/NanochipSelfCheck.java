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
import com.gtlmodular.common.machine.nanochip.NanochipModuleKind;
import com.gtlmodular.common.machine.nanochip.NanochipSplitterRules;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/**
 * NAC 运行时自检 —— 把「只在游戏里才能确认的接线」一次性打出来。
 *
 * <p>为什么需要它：静态检查（{@code verify_nac.js}）只能证明资源与配方文件自洽，
 * 证明不了「方块真的注册进去了 / 机器真的进了 GT 注册表 / 配方真的被数据包加载了 /
 * 配置里引用的流体真的存在」。这些只有跑起来才知道。
 *
 * <p>触发方式：
 * <ul>
 *   <li><b>进世界自动跑一次</b>（{@code ServerStartedEvent}）—— 日志里搜 {@code NAC 自检} 即可；</li>
 *   <li>游戏内随时重跑：命令 {@code /gtlmodular_nac}。</li>
 * </ul>
 *
 * <p>每一行都是「项目 + 结论」，失败项以 {@code ✗} 开头，方便扫日志。
 * 整段包在 try/catch 里：自检本身出任何问题都只记一条 warn，绝不影响服务器启动。
 */
public final class NanochipSelfCheck {

    private NanochipSelfCheck() {
    }

    /** 5 种结构方块。 */
    private static final String[] CASING_IDS = {
            "nanochip_mesh_interface_casing",
            "nanochip_reinforcement_casing",
            "nanochip_computational_matrix_casing",
            "nanochip_firewall_projection_casing",
            "nanochip_complex_glass",
    };

    /** 4 个带耗材的模块用到的流体。 */
    private static final String[] UPKEEP_FLUIDS = {
            "gtceu:grade_1_purified_water",
            "gtceu:super_coolant",
            "gtceu:raw_growth_medium",
    };

    /** 收集自检结果（每行一条）。任何异常都在内部吞掉并记成一行。 */
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

    /** 进世界自动跑：直接写进 latest.log。 */
    public static void runAndLog(MinecraftServer server) {
        List<String> lines = collect(server);
        NanochipModContext.logger().info("[gtlmodular] ===== NAC 自检开始（{} 项）=====", lines.size());
        for (String line : lines) {
            NanochipModContext.logger().info("[gtlmodular] NAC 自检 | {}", line);
        }
        long failed = lines.stream().filter(l -> l.startsWith("✗")).count();
        NanochipModContext.logger().info("[gtlmodular] ===== NAC 自检结束：{} 项失败 =====", failed);
    }

    // ---------- 各项检查 ----------

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
        // 形态/贴图链路的静态部分已由 verify_nac.js 覆盖，这里只确认注册表
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
        for (NanochipModuleKind kind : NanochipModuleKind.values()) {
            total++;
            if (hasMachine(kind.machineId())) {
                ok++;
            } else {
                out.add("✗ 模块未注册: " + NanochipModContext.registryId(kind.machineId()) + "（" + kind.nameZh() + "）");
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
                + "（NAC + " + NanochipModuleKind.values().length + " 模块 + 2 仓室）");
    }

    private static boolean hasMachine(String path) {
        ResourceLocation rl = NanochipModContext.registryId(path);
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
            // GTRecipeType 自己就实现了 vanilla 的 RecipeType，所以直接身份比较最稳
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
        // 反向检查：加载了、却没登记进 EXPECTED_RECIPES ⇒ 清单漏了一条。
        // 与上面那段合起来 = 「清单 ⇔ 目录」双射（光有正向会漏掉"清单忘了登记"这种情况）。
        int extra = 0;
        for (String path : loaded) {
            if (!expectedList.contains(path)) {
                extra++;
                out.add("✗ 配方未登记进 EXPECTED_RECIPES（清单漏了它）: " + NanochipModContext.registryId(path));
            }
        }
        out.add((extra == 0 ? "✓" : "✗") + " 配方清单无遗漏: 额外未登记 " + extra + " 条");
        // ★ 2026-10-14（代理剪断）：判据（circuits > 0）一行未动，只改括号里的说明文字 ——
        //   改前写的是「NAC 加工用的配方表」，但主机表已在 2026-10-14 剪断对电路组装机的代理
        //   ⇒ NAC 不再借用这张表（它现在只吃自己那 7 条 JSON）；这行留作「上游表本身还在、非空」的哨兵。
        out.add((circuits > 0 ? "✓" : "✗") + " 电路组装机配方总数（GTCEu 原生表；★ 2026-10-14 起 NAC 不再借用它，"
                + "仅作上游表非空的哨兵）: " + circuits);
    }

    /**
     * ★ 2026-09-17（第三轮）：<b>材料形态断言</b> —— 这两味材料必须带 {@code INGOT} + 正确的
     * {@code blastTemp}，否则 GTCEu <b>不会</b>自动生成 EBF/VF 自动链。
     *
     * <p>判据是 javap 读出来的（`MaterialRecipeHandler.processDust` 偏移 582 / 685 / 721）：
     * 先要 {@code hasProperty(PropertyKey.INGOT)}，再取 {@code getBlastTemperature()}，
     * {@code > 0} 才走 {@code processEBFRecipe}。
     * ⇒ 只要有人把 `GTLModularAddon` 里的 {@code .ingot()} 或 {@code .blastTemp(...)} 删掉，
     * 这一行立刻报红（这就是负向验证里"去掉 blastFurnaceTemperature ⇒ 报红"的抓手）。
     */
    private static void materialFormChecks(List<String> out) {
        if (GTCEuAPI.materialManager == null) {
            out.add("- 材料形态检查跳过（GTCEu 材料管理器未就绪）");
            return;
        }
        checkMaterialForm(out, "enriched_holmium", 3000);
        checkMaterialForm(out, "superconductor_uhv_base", 10800);
        // ★ 2026-09-17（第五轮）：UEV 粗胚也补了 `.ingot() + .blastTemp(11800)`
        //   （NH `loadSuperconductorUEVBase`：meltingPoint / blastFurnaceTemp 都是 11800；
        //    NH 的 `BlastFurnaceRecipes` 里 UEV 那条 COIL_HEAT 正是 11800）
        //   ⇒ 一并钉在这里：谁把 `.ingot()` 或 `.blastTemp(11800)` 删了，进游戏立刻报红。
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

    /**
     * ★ 2026-09-17（第三轮）：<b>自动链的运行时段</b> —— EBF / VF 的自动配方由 GTCEu 按材料属性生成，
     * <b>没有 JSON 文件</b>，所以"清单 ⇔ 目录双射"那套检查天然看不到它们（这也是为什么必须在这里查）。
     *
     * <p>查法：遍历 {@code RecipeManager} 里该配方类型的所有 {@code GTRecipe}，
     * 收集它们的<b>物品输出 id</b>，看期望的那几个是否真的在里面。
     * <ul>
     *   <li>{@code enriched_holmium}：BLAST 里应有产出 {@code gtceu:hot_enriched_holmium_ingot}；
     *       VACUUM 里应有产出 {@code gtceu:enriched_holmium_ingot}；</li>
     *   <li>{@code superconductor_uhv_base}：BLAST 里应有产出 {@code gtceu:hot_superconductor_uhv_base_ingot}；
     *       VACUUM 里应有产出 {@code gtceu:superconductor_uhv_base_ingot}
     *       （这条既可能是 GTCEu 自动那条"热锭+液氦"，也可能是我们手写的 NH 原版 —— 两条输出同名）。</li>
     * </ul>
     * ⚠️ 诚实标注：这一段只有在<b>真的进游戏</b>后才会有结论；本轮没有部署、没有进游戏
     * ⇒ 本轮的证据级别是"字节码 + 静态 JSON + 构建通过"，实机结论请看这条日志行。
     */
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
                // ★ 2026-09-17（第五轮）：UEV 粗胚补齐形态后的同名三件
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

    /** 统计某个 GT 配方表里，哪些期望的物品 id 真的作为输出出现过。 */
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

    /**
     * 从 GTCEu 的 Content 里取物品 id（输出侧是 ItemStack / ItemStack[] / Ingredient 三种都兜住）。
     *
     * <p>2026-09-30 修复：本包（gtlcore 1.2.3.2）把 {@code gtceu:sized} 提升成了
     * {@code LongIngredient}（与 {@code SizedIngredient} 一样都是 {@code Ingredient} 的子类），
     * 因此 BLAST_RECIPES / VACUUM_RECIPES 里<strong>没有一条</strong>产出是 ItemStack ⇒
     * 旧实现只会走最后一行 {@code return null}，导致 found 恒空、自检误报
     * 「已扫到不同产出 0 种」并把 EBF×3 + VF×3 全判为 ✗。这里补上 Ingredient 分支。
     * （用全限定名，避免动 import 区。）
     */
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
                // 某些 Ingredient 实现 getItems() 会抛（例如未解析的 tag），此时按"取不到 id"处理
                return null;
            }
        }
        return null;
    }

    /** 从若干候选里取第一个非空物品的注册名。 */
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
        // ★ 真空路由模式（2026-09-16 双模式）：管道模式下"不铺同色管道就不传输"是**正确行为**，
        //   这里显示出来，免得玩家把它当 bug（同一个字符串也用在同一台机器的界面提示里）。
        out.add("· 真空路由模式: " + com.gtlmodular.common.machine.nanochip.NanochipAssemblyComplexMachine.routingModeText()
                + "（配置项 nanochipComplex.vacuumRequiresPipes="
                + NanochipModContext.config().isVacuumRequiresPipes() + "）");
        // 校准判定分母口径（配置项 nanochipComplex.calibrationUseFullWindow）：
        // true = 原版硬编码的 100000（整窗口，第一档 20％ 要攒够 20000 个对应电路）；false = 已记录数。
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
