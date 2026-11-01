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
import com.gtlmodular.GTLModular;
import com.gtlmodular.common.config.GTLModularConfig;
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
            GTLModular.LOGGER.warn("[gtlmodular] NAC 自检失败", t);
        }
        return out;
    }

    /** 进世界自动跑：直接写进 latest.log。 */
    public static void runAndLog(MinecraftServer server) {
        List<String> lines = collect(server);
        GTLModular.LOGGER.info("[gtlmodular] ===== NAC 自检开始（{} 项）=====", lines.size());
        for (String line : lines) {
            GTLModular.LOGGER.info("[gtlmodular] NAC 自检 | {}", line);
        }
        long failed = lines.stream().filter(l -> l.startsWith("✗")).count();
        GTLModular.LOGGER.info("[gtlmodular] ===== NAC 自检结束：{} 项失败 =====", failed);
    }

    // ---------- 各项检查 ----------

    private static void blockChecks(List<String> out) {
        int ok = 0;
        for (String id : CASING_IDS) {
            ResourceLocation rl = new ResourceLocation(GTLModular.MOD_ID, id);
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
            out.add("✗ 机器未注册: gtlmodular:nanochip_assembly_complex");
        }
        for (NanochipModuleKind kind : NanochipModuleKind.values()) {
            total++;
            if (hasMachine(kind.machineId())) {
                ok++;
            } else {
                out.add("✗ 模块未注册: gtlmodular:" + kind.machineId() + "（" + kind.nameZh() + "）");
            }
        }
        total += 2;
        if (hasMachine("nanochip_vacuum_input_hatch")) {
            ok++;
        } else {
            out.add("✗ 仓室未注册: gtlmodular:nanochip_vacuum_input_hatch");
        }
        if (hasMachine("nanochip_vacuum_output_hatch")) {
            ok++;
        } else {
            out.add("✗ 仓室未注册: gtlmodular:nanochip_vacuum_output_hatch");
        }
        out.add((ok == total ? "✓" : "✗") + " 机器注册: " + ok + "/" + total
                + "（NAC + " + NanochipModuleKind.values().length + " 模块 + 2 仓室）");
    }

    private static boolean hasMachine(String path) {
        ResourceLocation rl = new ResourceLocation(GTLModular.MOD_ID, path);
        MachineDefinition def = GTRegistries.MACHINES.get(rl);
        return def != null;
    }

    /**
     * 本模组**必须全部加载**的配方 id（`gtlmodular:<文件名>`，由 `gen_nac_recipes.js` 与既有脚本产出）。
     *
     * <p>为什么要有这张表：原来只断言"配方**总数** == 41" —— 那是**数量**而不是**清单**，
     * 删掉一条、又误加一条，总数照样 41，自检却是绿的（本文件 {@code ✗} 漏报）。
     * 这里按"每条配方齐全"逐条点名断言 ⇒ 少一条当场报红并**报出是哪一条**；
     * 并且 {@link #recipeChecks} 里还做了**反向**检查（加载了但不在清单里的 = 清单漏登记），
     * 两侧合起来保证「清单 ⇔ 目录」是**双射**，不会退化成"只比总数"。
     *
     * <p>分类计数（= 52，与 {@code data/gtlmodular/recipes/*.json} 实测一致）：
     * <ul>
     *   <li><b>5</b> 结构方块（组装机）：reinforcement / mesh_interface / computational_matrix /
     *       firewall_projection 四种机壳 + complex_glass；</li>
     *   <li><b>15</b> 机器（装配线）：NAC 主控制器 assembly_complex + <b>14 个模块</b>
     *       （11 个 NH 模块 + 2026-09-18 队长指示新增的奇异 / 寰宇 / 超因果优化组织器）；</li>
     *   <li><b>2</b> 仓室：vacuum_input_hatch / vacuum_output_hatch；</li>
     *   <li><b>15</b> 既有（非 {@code gen_nac_recipes.js} 产出）：11 个 {@code *_me_pattern_assembly}
     *       + 4 个 {@code molten_*_module}；</li>
     *   <li><b>4</b> 补充材料框架（玩家 2026-09-16 要求"缺的框架自己做"；NH 侧无对应配方）；</li>
     *   <li><b>2</b> 既有材料补框架（radox / kevlar；玩家要求新增，NH 侧无对应配方）；
 *       ★ 2026-09-21 队长再拍板：<b>degenerate_rhenium（简并态铼）也补一条框架配方</b>
 *       （JEI 里它没配方 ⇒ 光学优化组织器塔身搭不起来；塔身不动、只补配方）⇒ 本桶变 <b>3</b>。</li>
     *   <li><b>1</b> 富集钬链头 {@code enriched_holmium_mix}（NH {@code MixerRecipes} 原文）；</li>
     *   <li><b>2</b> UHV 超导粗胚链（★ 2026-09-17 第三轮新增）：
     *       {@code superconductor_uhv_base_dust_mix}（混合机产粉，**玩家指定规格**，NH 无手写配方）
     *       + {@code superconductor_uhv_base_vf}（真空冷冻机，NH {@code VacuumFreezerRecipes} 原文）。</li>
     *   <li><b>7</b> 缺件方块配方（★ 2026-09-17 第四轮新增，见下方第四轮说明）：7 件 NH 同款新方块
     *       （2 个螺线管超导线圈 / 水厂外壳 / 屏蔽加速器外壳 / 非光子玻璃 / 2 块板金属）各一条配方。</li>
     *   <li><b>1</b> UEV 粗胚链头 {@code superconductor_uev_base_dust_mix}（★ 2026-09-17 第五轮·追加：
     *       NH {@code MixerRecipes} 原文，混合机 · 出 12 粉 · 200 t · UEV · 144 mB 铁等离子体）。</li>
     *   <li><b>1</b> 真空管道 {@code nanochip_vacuum_pipe}（★ 第五轮·追加，玩家给的规格：
     *       组装机 · 4 钨钢板 + 1 高级贴片电容 → 8 个 · 480 EU/t · 200 t；NH 侧**没有**产出管道的配方）。</li>
     * </ul>
     * <p>⚠️ <b>不在这张清单里、但游戏里确实存在</b>的配方（GTCEu 按材料属性**自动生成**，没有 JSON 文件）：
     * <ul>
     *   <li>{@code enriched_holmium}：EBF {@code dust → hot ingot @3000K}、VF {@code hot ingot → ingot}；</li>
     *   <li>{@code superconductor_uhv_base}：EBF {@code dust → hot ingot @10800K}、
     *       VF {@code hot ingot + 500L 液氦 → ingot}（GTCEu 自动那条）；
     *       另加我们手写的 NH 原版 {@code superconductor_uhv_base_vf}（裸热锭 → 锭，1600t，UHV）。</li>
     *   <li>{@code superconductor_uev_base}（★ 第五轮·追加）：EBF {@code dust → hot ingot @11800K}、
     *       VF {@code hot ingot + 500L 液氦 → ingot}（GTCEu 自动，**我们刻意不手写** ——
     *       GTCEu 1.4.4 没有关自动的 API，手写会让 JEI 里同一材料出现两条 VF）。</li>
     * </ul>
     * ⇒ 这些**不能**靠"清单 ⇔ 目录双射"检查（它们本来就不在目录里）；
     * 由 {@link #materialFormChecks} 与 {@link #autoChainChecks} 在运行时单独盯（见下）。
     *
     * <p>★ 2026-09-17（第一轮）：清单与目录实测一致；改动 = **删**
     * {@code draconiumawakened_frame_crafting}（NH 无此手写配方，且本包
     * {@code draconiumawakened_rod} 存在 ⇒ GTCEu 会自动生成标准框架配方，手写那条是多余的自造输入）
     * + **加** {@code enriched_holmium_mix}（NH {@code MixerRecipes} 的富集钬原文）
     * ⇒ 总数仍为 41。
     *
     * <p>★ 2026-09-17（第二轮）：**没有增删任何配方文件**（清单 41 条与名字**逐条不变**），
     * 只把 {@code enriched_holmium_mix} **内部的输入补齐**到与 NH 逐字段一致 ——
     * NH 那条是 `4× NaquadahEnriched.getDust + 1× Holmium.getDust + circuit(4) → 5× EnrichedHolmium.getDust`，
     * 原来少了 {@code circuit(4)}；现在 JSON 里加了
     * {@code {"type":"gtceu:circuit","configuration":4}} 且 {@code "chance":0}（非消耗品）。
     *
     * <p>★ 2026-09-17（第三轮）：材料形态补齐（玩家裁决）后**新增 2 条配方**
     * ⇒ 41 → **43**，清单同步登记；并新增两个运行时检查
     * （{@link #materialFormChecks}：两味材料必须带 {@code INGOT} + 正确的 {@code blastTemp}；
     * {@link #autoChainChecks}：EBF/VF 自动链真的在 {@code RecipeManager} 里）。
     *
     * <p>★ 2026-09-17（第四轮，本次）：<b>7 件缺件方块补生存获取配方 ⇒ 43 → 50</b>，清单同步登记 7 条：
     * {@code uhv_solenoid_superconductor_coil} / {@code uev_solenoid_superconductor_coil} /
     * {@code naquadria_reinforced_water_plant_casing} / {@code shielded_accelerator_casing} /
     * {@code non_photonic_matter_exclusion_glass} / {@code naquadah_alloy_sheetmetal} /
     * {@code superconductor_uev_base_sheetmetal}（生成器 {@code gen_nac_recipes.js} 第四轮段落）。
     * <p>NH 出处口径（**逐件**，取证过程写在生成器注释里）：
     * <ul>
     *   <li>只有 {@code shielded_accelerator_casing} 找到 NH 原文 —— gtnhlanth
     *       {@code RecipeLoader#loadAccelerator()}`（组装机 · 电路 6 · 144 mB 焊锡 · 800 t · IV 8192 EU/t ·
     *       1× 辐射防护机壳 + 6× Mu-Metal 致密板 → 1 个）；两项输入本包不存在（注册表 dump 实测
     *       {@code mu_metal} / {@code radiation} 各 0 命中）⇒ 同档替代（{@code assembly_line_casing} +
     *       {@code double_tritanium_plate}），**其余数值逐项照抄**。</li>
     *   <li>另外 6 条 **NH 未找到产出配方 ⇒ 本包自定**（口径 = 照既有 5 件 NAC 方块写法：组装机 ·
     *       4× 同族板 + 1× 元件 · 144 mB 焊锡 · 8 个/次 · 480 EU/t · 200 t；两件线圈按各自档位给电压）。
     *       取证：在 gregtech-5.09.54.20 全量 7040 个 class 的常量池里搜这 6 件对应方块/物品名，
     *       命中集合全部只落在 {注册类、方块类、结构工具、使用方} —— 无任何"产出"配方。</li>
     * </ul>
     * <p>★ 2026-09-17（第五轮，本次）：**把 6 条"本包自定"尽量换成 NH 真值**（本轮用本地 javap 读
     * dreamcraft，不再"上网取不到就自定"）—— 7 件缺件方块里 ①② 改为 **NH 原版装配线**
     * （出 1 个 / 400 t / UHV·UEV）、③ 改为 **NH 原版组装机**（出 1 个 / ZPM / 200 t）、
     * ⑤ 由组装机改为 **NH 原版流体固化机**（出 1 个 / UEV / 100 t）、④ 只把 Mu-Metal 那一格
     * 换成同形态 dense plate；⑥⑦ 经全 244 jar 常量池穷举确认 NH 无产出配方 ⇒ 保留自定
     * （**文件没有增删**，条数不变）。随后**追加 +2**：{@code superconductor_uev_base_dust_mix}
     * （NH {@code MixerRecipes} 原文）+ {@code nanochip_vacuum_pipe}（自定）⇒ **50 → 52**。
     * <p>⚠️ 本清单仍与目录**双向**核对（{@link #recipeChecks} 的正向逐条 + 反向"加载了却没登记"），
     * 目录实测 **166** 条 ⇒ 期望值 166 条。
     * <p>★ 2026-09-18（玩家裁决 E，第二波）：**174 → 166**（−8）—— NAC「特殊系列」三档自造电路整组退役，
     * 删掉 4 条换算（{@code cc_pico_circuit}/{@code cc_quantum_circuit}/{@code cc_planck_circuit}/
     * {@code cc_planck_manifold}）+ 4 条组装矩阵（{@code 020/021/022/023}）。
     * 依据：维基「特殊系列电路板」逐字「作为异星、寰宇、超时空三种电路板还未更新时的暂时性的 UMV-MAX 级
     * 电路替代品，后续可能会移除。」（2.9.0 新增）⇒ 过渡件退役，本包同档有真件
     * {@code #gtceu:circuits/uxv|opv|max} 与 {@code kubejs:suprachronal_uxv|opv|max}。
     * <p>于是换算 **99 → 95**、组装矩阵 **23 → 19**（分类计数注释同步改，{@link #recipeChecks} 会现读求和核对）。
     * <p>★ 2026-09-18（P2 模块加工层）：**166 → 245****（+79）—— NAC 的 **P2 模块加工层**落地：
     * 9 张模块表（蚀刻阵列 3 / 切割仓 30 / 导线追踪器 9 / 板处理器 4 / SMD 处理器 10 /
     * 超导分离器 7 / 光学组织器 2 / 生物调谐器 4 / 封装卷绕器 10），语义 = `未处理 CC → 已处理 PC`。
     * 生成器 `gen_nac_p2_recipes.js`（幂等 + `--verify`），自检 `_census/check_nac_p2_recipes.js`（含 `--negative` 11 例）。
     * <p>★ **条数口径要说清**：`_census/p2_input_map.json` 的清单是 **83 条**；按裁决 **ADJ-1**
     * 「板处理器只搬 4 条 hidden、4 条 fake 不搬」（fake 的唯一差异是量 = 0 的流体，
     * GTCEu 里 0 mB 的 SizedFluidIngredient 语义未取证 ⇒ 可能与同输入的 hidden 双胞胎撞车），
     * **实际落地 79 条** ⇒ 清单 166 + 79 = **245**。
     * <p>★ 2026-09-18（波2 自造段换算队列）：**245 → 348**（+103）—— 五族 + 晶体/湿件 NAC 改造所需的 103 件自造段 CC 的换算配方（`nanochip/conversion/cc_*.json`，真件 = `nac_wave2_spec.json` 的 sourceItem，经 `_census/verify_recipe_inputs.js` 四档证据判定确实存在）。生成器 `_census/gen_nac_w2_conversion.js`。
     * <p>三个模块目录/编号口径：路径 = `nanochip/<模块>/<NH 原始 index 3 位补零>_<输出 PC>.json`，
     * 编号**不重排** ⇒ 封装卷绕器的 #4/#5/#7（随「特殊系列」退役）在目录里是**看得见的缺口**。
     * <p>★ 2026-09-19（缺件获取链）：**459 → 469**（+10）—— 残留物品清理报告 §8.2 点名了 10 件
     * 「被我方配方引用、但整合包里没有任何途径产出它们」的自注册真件（4 件 `*_item_casing` +
     * 5 件 PIC 芯片 + `living_crystal_chip`）⇒ 逐条补上获得配方，新目录
     * `data/gtlmodular/recipes/missing_acquisition/`（10 条，机器/输入/输出/EUt/时长逐条给出模板出处，
     * 全部 EUt ≤ UHV 口径）。守卫体检 = `_census/check_missing_acquisition_chain.js`
     * （凡我方配方引用的 `gtlmodular:*` 物品都必须有产出者；白名单为空）。交付说明见 `_census/缺件获取链_队员.md`。
     * <p>★ 2026-09-19（新造晶圆 / 还原两步链）：**469 → 474**（+5）—— 上一轮「全量晶圆普查」
     * （`_census/孤儿隐藏与晶圆普查_队员.md` §B3）实证本包**没有**量子/阿米/仄米/幺米/普朗克这 5 档晶圆，
     * 于是那 5 条 `pic_chip_*` 只能把 NH 的两步链压成**一步**（母晶圆 → 芯片）。队长 2026-09-19 批准新造：
     * <ul>
     *   <li>新增 5 件自注册物品 {@link NanochipMissingWafers}（`gtlmodular:<档>_pic_wafer`，含贴图/模型/双语键）；</li>
     *   <li>新增 5 条**第一步**配方 `missing_acquisition/pic_wafer_<档>.json`
     *       （`gtceu:laser_engraver`：`kubejs:taranium_wafer` + `#forge:lenses/orange`(NC) → 晶圆，400 t / 1,966,080 EUt）；</li>
     *   <li>把原 5 条 `pic_chip_<档>.json` **就地改写**为**第二步**（`gtceu:cutter`：晶圆 → 芯片，400 t / 1,966,080 EUt）。</li>
     * </ul>
     * 净增 **5** 条（5 条新文件；5 条旧文件被覆盖，不是新增）。机器锚点 = GTCEu 原生
     * `engrave_*`（全在 `LASER_ENGRAVER_RECIPES`）/ `cut_*`（全在 `CUTTER_RECIPES`），javap 第一手。
     * 守卫体检 `_census/check_missing_acquisition_chain.js` 追加 **W1..W5** 五项专钉
     * （两步链闭合 / 机器 / 注册行+模型+贴图非空 / 双语键 / 晶圆↔芯片配对）。
     * 交付说明见 `_census/新造晶圆还原两步链_队员.md`。
     * <p>★ 2026-09-19（简单模式换算上 AE · 队长方案 A）：**474 → 675**（+201）—— 队长原话「我希望开启简单模式以后，
     * 能够通过使用样板总成直接完成电路的制作」。根因：换算在 NH 里是**控制室仓位行为**（不是配方）⇒
     * AE 样板总成永远推不动它，简单模式（不铺真空仓）下玩家有 CC 却没法用 AE 批量换回真电路。
     * 做法：为 `nanochip/conversion/` 里 201 条「真件 → CC」**逐条生成对偶**「CC → 真件」
     * （目录 `nanochip/conversion_reverse/`，生成器 `_census/gen_nac_conversion_reverse.js`），
     * 并由 `NanochipAssemblyComplexMachine#getRecipeTypes()` 在**简单模式下**把换算表追加进主机的配方类型
     * （原版 NH 模式返回父类数组实例，逐字节不变）。守卫体检：`_census/check_conversion_reverse.js`（第 43 项）
     * 与 `_census/check_simple_conversion_ordering.js`（第 44 项）。
     */
    private static final String[] EXPECTED_RECIPES = {
            "coolant/dimensionally_offset_superfluid",
            "coolant/super_coolant",
            // ★ 2026-09-21（队长拍板）：光学优化组织器塔身 `A=FRAME:DegenerateRhenium` 要的
            //   `gtceu:degenerate_rhenium_frame` 在 JEI 里**没有配方**（简并态铼无锭 ⇒ GTCEu 的
            //   `processStick` 入口守卫 `GEM||INGOT` 直接 return ⇒ 不生成杆配方 ⇒ 框架物品在、配方不在）
            //   ⇒ 按队长口径「补一条框架配方」，塔身一格不动（先例：kevlar / octiron 那几条）。
            "degenerate_rhenium_frame_crafting",
            "enriched_holmium_frame_crafting",
            "enriched_holmium_mix",
            "ev_me_pattern_assembly",
            "hv_me_pattern_assembly",
            "iv_me_pattern_assembly",
            "kevlar_frame_crafting",
            "luv_me_pattern_assembly",
            "missing_acquisition/casing_black_dwarf_mtter",
            "missing_acquisition/casing_creon",
            "missing_acquisition/casing_eternity",
            "missing_acquisition/casing_superconductor_uev_base",
            "missing_acquisition/living_crystal_chip",
            "missing_acquisition/pic_chip_atto",
            "missing_acquisition/pic_chip_planck",
            "missing_acquisition/pic_chip_quantum",
            "missing_acquisition/pic_chip_yocto",
            "missing_acquisition/pic_chip_zepto",
            "missing_acquisition/pic_wafer_atto",
            "missing_acquisition/pic_wafer_planck",
            "missing_acquisition/pic_wafer_quantum",
            "missing_acquisition/pic_wafer_yocto",
            "missing_acquisition/pic_wafer_zepto",
            "molten_blast_module",
            "molten_module_host",
            "molten_space_ore_module",
            "molten_stripper_module",
            "mv_me_pattern_assembly",
            // ★ 2026-09-22（文件级剪切，非复制）：七族 processor 层配方**从矩阵表移到主机自己的表**
            //   —— 目录 `nanochip/assembly_complex/`（新建 7 条），type 由
            //   `gtlmodular:nanochip_assembly_matrix` 改为 `gtlmodular:nanochip_assembly_complex`
            //   （输入/输出/时长/EU 逐字节未动）。
            //   ⚠️ 本清单已按"7 条处理器归属主机表"同步（矩阵侧只留 assembly/computer/mainframe = 23 条，
            //   主机侧 = 7 条；**总数 245 不变**）。但矩阵目录里那 7 个原文件
            //   （`nanochip/assembly_matrix/` 的 001/006/011/015/019/023/027）**尚未删除**：
            //   本次执行时 shell/`pwsh` 被沙箱拒绝（`sandbox-local windows-acl temp grant materialization failed`），
            //   删除需另跑一条命令（见 `_census/NAC配方剪切_执行记录.md` §2）。
            //   ⇒ 在那 7 个文件真正删除前，矩阵表是 30 条（23 + 7 条重复的处理器配方），
            //      本清单则会报 7 条「未登记进 EXPECTED_RECIPES」+ 7 条「配方缺失」，
            //      即"清单已剪、目录未剪"的**中间态**，属预期、不是新引入的 bug。
            "nanochip/assembly_complex/001_cc_crystal_processor",
            "nanochip/assembly_complex/006_cc_wetware_processor",
            "nanochip/assembly_complex/011_cc_bioware_processor",
            "nanochip/assembly_complex/015_cc_optical_processor",
            "nanochip/assembly_complex/019_cc_exotic_processor",
            "nanochip/assembly_complex/023_cc_cosmic_processor",
            "nanochip/assembly_complex/027_cc_supracausal_processor",
            "nanochip/assembly_matrix/002_cc_crystal_processor",
            "nanochip/assembly_matrix/003_cc_crystal_assembly",
            "nanochip/assembly_matrix/004_cc_crystal_computer",
            "nanochip/assembly_matrix/005_cc_crystal_mainframe",
            "nanochip/assembly_matrix/007_cc_wetware_processor",
            "nanochip/assembly_matrix/008_cc_wetware_assembly",
            "nanochip/assembly_matrix/009_cc_wetware_computer",
            "nanochip/assembly_matrix/010_cc_wetware_mainframe",
            "nanochip/assembly_matrix/012_cc_bioware_assembly",
            "nanochip/assembly_matrix/013_cc_bioware_computer",
            "nanochip/assembly_matrix/014_cc_bioware_mainframe",
            "nanochip/assembly_matrix/016_cc_optical_assembly",
            "nanochip/assembly_matrix/017_cc_optical_computer",
            "nanochip/assembly_matrix/018_cc_optical_mainframe",
            "nanochip/assembly_matrix/020_cc_exotic_assembly",
            "nanochip/assembly_matrix/021_cc_exotic_computer",
            "nanochip/assembly_matrix/022_cc_exotic_mainframe",
            "nanochip/assembly_matrix/024_cc_cosmic_assembly",
            "nanochip/assembly_matrix/025_cc_cosmic_computer",
            "nanochip/assembly_matrix/026_cc_cosmic_mainframe",
            "nanochip/assembly_matrix/028_cc_supracausal_assembly",
            "nanochip/assembly_matrix/029_cc_supracausal_computer",
            "nanochip/assembly_matrix/030_cc_supracausal_mainframe",
            "nanochip/biological_coordinator/001_cc_processed_neuro_processing_unit",
            "nanochip/biological_coordinator/002_cc_processed_chip_living_crystal",
            "nanochip/biological_coordinator/003_cc_processed_bio_processing_unit",
            "nanochip/biological_coordinator/004_cc_processed_living_bio_chip",
            "nanochip/biological_coordinator/005_cc_processed_unstable_star",
            "nanochip/biological_coordinator/006_cc_processed_gravi_star",
            "nanochip/biological_coordinator/007_cc_processed_cosmic_processing_unit_core",
            "nanochip/biological_coordinator/008_cc_processed_nuclear_star",
            "nanochip/biological_coordinator/010_cc_processed_draconic_core",
            "nanochip/biological_coordinator/011_cc_processed_data_stick",
            "nanochip/biological_coordinator/013_cc_processed_wyvern_core",
            "nanochip/biological_coordinator/014_cc_processed_data_orb",
            "nanochip/biological_coordinator/015_cc_processed_proto_matter",
            "nanochip/biological_coordinator/017_cc_processed_microwormhole_generator",
            "nanochip/biological_coordinator/018_cc_processed_manifold_oscillatory_power_cell",
            "nanochip/biological_coordinator/019_cc_processed_awakened_core",
            "nanochip/biological_coordinator/020_cc_processed_recursively_folded_negative_space",
            "nanochip/biological_coordinator/021_cc_processed_contained_exotic_matter",
            "nanochip/biological_coordinator/022_cc_processed_data_module",
            "nanochip/biological_coordinator/023_cc_processed_eigenfolded_kerr_manifold",
            "nanochip/biological_coordinator/024_cc_processed_cosmic_fabric",
            "nanochip/biological_coordinator/025_cc_processed_ultrashort_pulse_laser",
            "nanochip/biological_coordinator/026_cc_processed_ctc_computational_unit",
            "nanochip/board_processor/001_cc_processed_board_multifiberglass_elite",
            "nanochip/board_processor/003_cc_processed_board_wetware_lifesupport",
            "nanochip/board_processor/005_cc_processed_board_bio_mutated",
            "nanochip/board_processor/007_cc_processed_board_optical",
            "nanochip/board_processor/008_cc_processed_exotic_printed_circuit_board",
            "nanochip/board_processor/009_cc_processed_cosmic_printed_circuit_board",
            "nanochip/board_processor/010_cc_processed_supracausal_printed_circuit_board",
            "nanochip/conversion/cc_adamantine_plate",
            "nanochip/conversion/cc_adamantium_frame",
            "nanochip/conversion/cc_adv_smd_capacitor",
            "nanochip/conversion/cc_adv_smd_diode",
            "nanochip/conversion/cc_adv_smd_inductor",
            "nanochip/conversion/cc_adv_smd_resistor",
            "nanochip/conversion/cc_adv_smd_transistor",
            "nanochip/conversion/cc_awakened_core",
            "nanochip/conversion/cc_bio_processing_unit",
            "nanochip/conversion/cc_bioware_assembly",
            "nanochip/conversion/cc_bioware_computer",
            "nanochip/conversion/cc_bioware_mainframe",
            "nanochip/conversion/cc_bioware_processor",
            "nanochip/conversion/cc_black_titanium_plate",
            "nanochip/conversion/cc_board_bio_mutated",
            "nanochip/conversion/cc_board_multifiberglass_elite",
            "nanochip/conversion/cc_board_optical",
            "nanochip/conversion/cc_board_wetware_lifesupport",
            "nanochip/conversion/cc_bolt_chromatic_glass",
            "nanochip/conversion/cc_bolt_cosmic_neutronium",
            "nanochip/conversion/cc_bolt_enriched_holmium",
            "nanochip/conversion/cc_bolt_transcendent_metal",
            "nanochip/conversion/cc_bolt_umv_superconductor",
            "nanochip/conversion/cc_bolt_white_dwarf_matter",
            "nanochip/conversion/cc_bolt_yttrium_barium_cuprate",
            "nanochip/conversion/cc_cable_optical_fiber",
            "nanochip/conversion/cc_casing_black_dwarf_matter",
            "nanochip/conversion/cc_casing_creon",
            "nanochip/conversion/cc_casing_eternity",
            "nanochip/conversion/cc_casing_uev_superconductor",
            "nanochip/conversion/cc_chip_asoc",
            "nanochip/conversion/cc_chip_atto_pic",
            "nanochip/conversion/cc_chip_crystal_cpu",
            "nanochip/conversion/cc_chip_crystal_so_c",
            "nanochip/conversion/cc_chip_living_crystal",
            "nanochip/conversion/cc_chip_nand",
            "nanochip/conversion/cc_chip_nano_cpu",
            "nanochip/conversion/cc_chip_nor",
            "nanochip/conversion/cc_chip_optical_cpu",
            "nanochip/conversion/cc_chip_piko_pic",
            "nanochip/conversion/cc_chip_planck_pic",
            "nanochip/conversion/cc_chip_quantum_pic",
            "nanochip/conversion/cc_chip_ram",
            "nanochip/conversion/cc_chip_raw_advanced_crystal",
            "nanochip/conversion/cc_chip_yocto_pic",
            "nanochip/conversion/cc_chip_zepto_pic",
            "nanochip/conversion/cc_cinobite_plate",
            "nanochip/conversion/cc_contained_exotic_matter",
            "nanochip/conversion/cc_cosmic_assembly",
            "nanochip/conversion/cc_cosmic_computer",
            "nanochip/conversion/cc_cosmic_fabric",
            "nanochip/conversion/cc_cosmic_mainframe",
            "nanochip/conversion/cc_cosmic_printed_circuit_board",
            "nanochip/conversion/cc_cosmic_processing_core",
            "nanochip/conversion/cc_cosmic_processing_unit_core",
            "nanochip/conversion/cc_cosmic_processor",
            "nanochip/conversion/cc_cosmic_ram_chip",
            "nanochip/conversion/cc_cosmicneutronium_plate",
            "nanochip/conversion/cc_crystal_assembly",
            "nanochip/conversion/cc_crystal_computer",
            "nanochip/conversion/cc_crystal_mainframe",
            "nanochip/conversion/cc_crystal_processor",
            "nanochip/conversion/cc_crystalmatrix_plate",
            "nanochip/conversion/cc_ctc_computational_unit",
            "nanochip/conversion/cc_data_module",
            "nanochip/conversion/cc_data_orb",
            "nanochip/conversion/cc_data_stick",
            "nanochip/conversion/cc_double_naquadriatictaranium_plate",
            "nanochip/conversion/cc_double_technetium_plate",
            "nanochip/conversion/cc_double_trinium_titanium_plate",
            "nanochip/conversion/cc_draconic_core",
            "nanochip/conversion/cc_draconiumawakened_double_wire",
            "nanochip/conversion/cc_duranium_plate",
            "nanochip/conversion/cc_echoite_double_wire",
            "nanochip/conversion/cc_eigenfolded_kerr_manifold",
            "nanochip/conversion/cc_enderite_double_wire",
            "nanochip/conversion/cc_enriched_naquadah_trinium_europium_duranide_double_wire",
            "nanochip/conversion/cc_europium_plate",
            "nanochip/conversion/cc_exotic_assembly",
            "nanochip/conversion/cc_exotic_computer",
            "nanochip/conversion/cc_exotic_mainframe",
            "nanochip/conversion/cc_exotic_printed_circuit_board",
            "nanochip/conversion/cc_exotic_processing_core",
            "nanochip/conversion/cc_exotic_processor",
            "nanochip/conversion/cc_exotic_ram_chip",
            "nanochip/conversion/cc_fine_cinobite_wire",
            "nanochip/conversion/cc_fine_dubnium_wire",
            "nanochip/conversion/cc_fine_heavy_quark_degenerate_matter_wire",
            "nanochip/conversion/cc_fine_naquadah_wire",
            "nanochip/conversion/cc_fm_chip",
            "nanochip/conversion/cc_foil_eternity",
            "nanochip/conversion/cc_foil_polybenzimidazole",
            "nanochip/conversion/cc_foil_radox_polymer",
            "nanochip/conversion/cc_foil_shirabon",
            "nanochip/conversion/cc_foil_silicone_rubber",
            "nanochip/conversion/cc_foil_styrene_rubber",
            "nanochip/conversion/cc_framebox_aluminium",
            "nanochip/conversion/cc_framebox_celestial_tungsten",
            "nanochip/conversion/cc_framebox_hypogen",
            "nanochip/conversion/cc_framebox_mag_matter",
            "nanochip/conversion/cc_framebox_tritanium",
            "nanochip/conversion/cc_fullerene_polymer_matrix_pulp_foil",
            "nanochip/conversion/cc_gravi_star",
            "nanochip/conversion/cc_highly_advanced_soc",
            "nanochip/conversion/cc_highurabilityompoundteel_plate",
            "nanochip/conversion/cc_hpic_chip",
            "nanochip/conversion/cc_hsse_frame",
            "nanochip/conversion/cc_infuscolium_plate",
            "nanochip/conversion/cc_legendarium_double_wire",
            "nanochip/conversion/cc_living_bio_chip",
            "nanochip/conversion/cc_manifold_oscillatory_power_cell",
            "nanochip/conversion/cc_microwormhole_generator",
            "nanochip/conversion/cc_mithril_plate",
            "nanochip/conversion/cc_naquadah_bolt",
            "nanochip/conversion/cc_neuro_processing_unit",
            "nanochip/conversion/cc_neutronium_plate",
            "nanochip/conversion/cc_niobium_titanium_single_wire",
            "nanochip/conversion/cc_nm_chip",
            "nanochip/conversion/cc_nuclear_star",
            "nanochip/conversion/cc_optical_assembly",
            "nanochip/conversion/cc_optical_computer",
            "nanochip/conversion/cc_optical_mainframe",
            "nanochip/conversion/cc_optical_processor",
            "nanochip/conversion/cc_optical_ram",
            "nanochip/conversion/cc_optical_smd_capacitor",
            "nanochip/conversion/cc_optical_smd_diode",
            "nanochip/conversion/cc_optical_smd_inductor",
            "nanochip/conversion/cc_optical_smd_resistor",
            "nanochip/conversion/cc_optical_smd_transistor",
            "nanochip/conversion/cc_optical_soc",
            "nanochip/conversion/cc_plate_hexanite",
            "nanochip/conversion/cc_plate_metastable_oganesson",
            "nanochip/conversion/cc_plate_rhugnor",
            "nanochip/conversion/cc_polyetheretherketone_foil",
            "nanochip/conversion/cc_proto_matter",
            "nanochip/conversion/cc_qbit_cpu_chip",
            "nanochip/conversion/cc_quantanium_frame",
            "nanochip/conversion/cc_quantumchromodynamically_confined_matter_plate",
            "nanochip/conversion/cc_radox_plate",
            "nanochip/conversion/cc_recursively_folded_negative_space",
            "nanochip/conversion/cc_ruthenium_trinium_americium_neutronate_double_wire",
            "nanochip/conversion/cc_screw_astral_titanium",
            "nanochip/conversion/cc_screw_six_phased_copper",
            "nanochip/conversion/cc_screw_universium",
            "nanochip/conversion/cc_seaborgium_plate",
            "nanochip/conversion/cc_smd_capacitor_bioware",
            "nanochip/conversion/cc_smd_capacitor_cosmic",
            "nanochip/conversion/cc_smd_capacitor_exotic",
            "nanochip/conversion/cc_smd_capacitor_supracausal",
            "nanochip/conversion/cc_smd_diode_bioware",
            "nanochip/conversion/cc_smd_diode_cosmic",
            "nanochip/conversion/cc_smd_diode_exotic",
            "nanochip/conversion/cc_smd_diode_supracausal",
            "nanochip/conversion/cc_smd_inductor_bioware",
            "nanochip/conversion/cc_smd_inductor_cosmic",
            "nanochip/conversion/cc_smd_inductor_exotic",
            "nanochip/conversion/cc_smd_inductor_supracausal",
            "nanochip/conversion/cc_smd_resistor_bioware",
            "nanochip/conversion/cc_smd_resistor_cosmic",
            "nanochip/conversion/cc_smd_resistor_exotic",
            "nanochip/conversion/cc_smd_resistor_supracausal",
            "nanochip/conversion/cc_smd_transistor_bioware",
            "nanochip/conversion/cc_smd_transistor_cosmic",
            "nanochip/conversion/cc_smd_transistor_exotic",
            "nanochip/conversion/cc_smd_transistor_supracausal",
            "nanochip/conversion/cc_starmetal_plate",
            "nanochip/conversion/cc_superconductor_lu_v",
            "nanochip/conversion/cc_superconductor_uev",
            "nanochip/conversion/cc_superconductor_uhv",
            "nanochip/conversion/cc_superconductor_uiv",
            "nanochip/conversion/cc_superconductor_umv",
            "nanochip/conversion/cc_superconductor_uv",
            "nanochip/conversion/cc_superconductor_zpm",
            "nanochip/conversion/cc_supracausal_assembly",
            "nanochip/conversion/cc_supracausal_computer",
            "nanochip/conversion/cc_supracausal_mainframe",
            "nanochip/conversion/cc_supracausal_printed_circuit_board",
            "nanochip/conversion/cc_supracausal_processing_core",
            "nanochip/conversion/cc_supracausal_processor",
            "nanochip/conversion/cc_supracausal_ram_chip",
            "nanochip/conversion/cc_thermal_superconductor",
            "nanochip/conversion/cc_titansteel_plate",
            "nanochip/conversion/cc_trinium_titanium_plate",
            "nanochip/conversion/cc_uhpic_chip",
            "nanochip/conversion/cc_ultrashort_pulse_laser",
            "nanochip/conversion/cc_unstable_star",
            "nanochip/conversion/cc_vibranium_frame",
            "nanochip/conversion/cc_wetware_assembly",
            "nanochip/conversion/cc_wetware_computer",
            "nanochip/conversion/cc_wetware_mainframe",
            "nanochip/conversion/cc_wetware_processor",
            "nanochip/conversion/cc_wire_hypogen",
            "nanochip/conversion/cc_wire_infinity",
            "nanochip/conversion/cc_wire_lumiium",
            "nanochip/conversion/cc_wire_mag_matter",
            "nanochip/conversion/cc_wire_niobium_titanium",
            "nanochip/conversion/cc_wire_proto_halkonite",
            "nanochip/conversion/cc_wire_yttrium_barium_cuprate",
            "nanochip/conversion/cc_wyvern_core",
            "nanochip/conversion/cc_zylon_foil",
            "nanochip/conversion_reverse/cc_adamantine_plate",
            "nanochip/conversion_reverse/cc_adamantium_frame",
            "nanochip/conversion_reverse/cc_adv_smd_capacitor",
            "nanochip/conversion_reverse/cc_adv_smd_diode",
            "nanochip/conversion_reverse/cc_adv_smd_inductor",
            "nanochip/conversion_reverse/cc_adv_smd_resistor",
            "nanochip/conversion_reverse/cc_adv_smd_transistor",
            "nanochip/conversion_reverse/cc_awakened_core",
            "nanochip/conversion_reverse/cc_bio_processing_unit",
            "nanochip/conversion_reverse/cc_bioware_assembly",
            "nanochip/conversion_reverse/cc_bioware_computer",
            "nanochip/conversion_reverse/cc_bioware_mainframe",
            "nanochip/conversion_reverse/cc_bioware_processor",
            "nanochip/conversion_reverse/cc_black_titanium_plate",
            "nanochip/conversion_reverse/cc_board_bio_mutated",
            "nanochip/conversion_reverse/cc_board_multifiberglass_elite",
            "nanochip/conversion_reverse/cc_board_optical",
            "nanochip/conversion_reverse/cc_board_wetware_lifesupport",
            "nanochip/conversion_reverse/cc_bolt_chromatic_glass",
            "nanochip/conversion_reverse/cc_bolt_cosmic_neutronium",
            "nanochip/conversion_reverse/cc_bolt_enriched_holmium",
            "nanochip/conversion_reverse/cc_bolt_transcendent_metal",
            "nanochip/conversion_reverse/cc_bolt_umv_superconductor",
            "nanochip/conversion_reverse/cc_bolt_white_dwarf_matter",
            "nanochip/conversion_reverse/cc_bolt_yttrium_barium_cuprate",
            "nanochip/conversion_reverse/cc_cable_optical_fiber",
            "nanochip/conversion_reverse/cc_casing_black_dwarf_matter",
            "nanochip/conversion_reverse/cc_casing_creon",
            "nanochip/conversion_reverse/cc_casing_eternity",
            "nanochip/conversion_reverse/cc_casing_uev_superconductor",
            "nanochip/conversion_reverse/cc_chip_asoc",
            "nanochip/conversion_reverse/cc_chip_atto_pic",
            "nanochip/conversion_reverse/cc_chip_crystal_cpu",
            "nanochip/conversion_reverse/cc_chip_crystal_so_c",
            "nanochip/conversion_reverse/cc_chip_living_crystal",
            "nanochip/conversion_reverse/cc_chip_nand",
            "nanochip/conversion_reverse/cc_chip_nano_cpu",
            "nanochip/conversion_reverse/cc_chip_nor",
            "nanochip/conversion_reverse/cc_chip_optical_cpu",
            "nanochip/conversion_reverse/cc_chip_piko_pic",
            "nanochip/conversion_reverse/cc_chip_planck_pic",
            "nanochip/conversion_reverse/cc_chip_quantum_pic",
            "nanochip/conversion_reverse/cc_chip_ram",
            "nanochip/conversion_reverse/cc_chip_raw_advanced_crystal",
            "nanochip/conversion_reverse/cc_chip_yocto_pic",
            "nanochip/conversion_reverse/cc_chip_zepto_pic",
            "nanochip/conversion_reverse/cc_cinobite_plate",
            "nanochip/conversion_reverse/cc_contained_exotic_matter",
            "nanochip/conversion_reverse/cc_cosmic_assembly",
            "nanochip/conversion_reverse/cc_cosmic_computer",
            "nanochip/conversion_reverse/cc_cosmic_fabric",
            "nanochip/conversion_reverse/cc_cosmic_mainframe",
            "nanochip/conversion_reverse/cc_cosmic_printed_circuit_board",
            "nanochip/conversion_reverse/cc_cosmic_processing_core",
            "nanochip/conversion_reverse/cc_cosmic_processing_unit_core",
            "nanochip/conversion_reverse/cc_cosmic_processor",
            "nanochip/conversion_reverse/cc_cosmic_ram_chip",
            "nanochip/conversion_reverse/cc_cosmicneutronium_plate",
            "nanochip/conversion_reverse/cc_crystal_assembly",
            "nanochip/conversion_reverse/cc_crystal_computer",
            "nanochip/conversion_reverse/cc_crystal_mainframe",
            "nanochip/conversion_reverse/cc_crystal_processor",
            "nanochip/conversion_reverse/cc_crystalmatrix_plate",
            "nanochip/conversion_reverse/cc_ctc_computational_unit",
            "nanochip/conversion_reverse/cc_data_module",
            "nanochip/conversion_reverse/cc_data_orb",
            "nanochip/conversion_reverse/cc_data_stick",
            "nanochip/conversion_reverse/cc_double_naquadriatictaranium_plate",
            "nanochip/conversion_reverse/cc_double_technetium_plate",
            "nanochip/conversion_reverse/cc_double_trinium_titanium_plate",
            "nanochip/conversion_reverse/cc_draconic_core",
            "nanochip/conversion_reverse/cc_draconiumawakened_double_wire",
            "nanochip/conversion_reverse/cc_duranium_plate",
            "nanochip/conversion_reverse/cc_echoite_double_wire",
            "nanochip/conversion_reverse/cc_eigenfolded_kerr_manifold",
            "nanochip/conversion_reverse/cc_enderite_double_wire",
            "nanochip/conversion_reverse/cc_enriched_naquadah_trinium_europium_duranide_double_wire",
            "nanochip/conversion_reverse/cc_europium_plate",
            "nanochip/conversion_reverse/cc_exotic_assembly",
            "nanochip/conversion_reverse/cc_exotic_computer",
            "nanochip/conversion_reverse/cc_exotic_mainframe",
            "nanochip/conversion_reverse/cc_exotic_printed_circuit_board",
            "nanochip/conversion_reverse/cc_exotic_processing_core",
            "nanochip/conversion_reverse/cc_exotic_processor",
            "nanochip/conversion_reverse/cc_exotic_ram_chip",
            "nanochip/conversion_reverse/cc_fine_cinobite_wire",
            "nanochip/conversion_reverse/cc_fine_dubnium_wire",
            "nanochip/conversion_reverse/cc_fine_heavy_quark_degenerate_matter_wire",
            "nanochip/conversion_reverse/cc_fine_naquadah_wire",
            "nanochip/conversion_reverse/cc_fm_chip",
            "nanochip/conversion_reverse/cc_foil_eternity",
            "nanochip/conversion_reverse/cc_foil_polybenzimidazole",
            "nanochip/conversion_reverse/cc_foil_radox_polymer",
            "nanochip/conversion_reverse/cc_foil_shirabon",
            "nanochip/conversion_reverse/cc_foil_silicone_rubber",
            "nanochip/conversion_reverse/cc_foil_styrene_rubber",
            "nanochip/conversion_reverse/cc_framebox_aluminium",
            "nanochip/conversion_reverse/cc_framebox_celestial_tungsten",
            "nanochip/conversion_reverse/cc_framebox_hypogen",
            "nanochip/conversion_reverse/cc_framebox_mag_matter",
            "nanochip/conversion_reverse/cc_framebox_tritanium",
            "nanochip/conversion_reverse/cc_fullerene_polymer_matrix_pulp_foil",
            "nanochip/conversion_reverse/cc_gravi_star",
            "nanochip/conversion_reverse/cc_highly_advanced_soc",
            "nanochip/conversion_reverse/cc_highurabilityompoundteel_plate",
            "nanochip/conversion_reverse/cc_hpic_chip",
            "nanochip/conversion_reverse/cc_hsse_frame",
            "nanochip/conversion_reverse/cc_infuscolium_plate",
            "nanochip/conversion_reverse/cc_legendarium_double_wire",
            "nanochip/conversion_reverse/cc_living_bio_chip",
            "nanochip/conversion_reverse/cc_manifold_oscillatory_power_cell",
            "nanochip/conversion_reverse/cc_microwormhole_generator",
            "nanochip/conversion_reverse/cc_mithril_plate",
            "nanochip/conversion_reverse/cc_naquadah_bolt",
            "nanochip/conversion_reverse/cc_neuro_processing_unit",
            "nanochip/conversion_reverse/cc_neutronium_plate",
            "nanochip/conversion_reverse/cc_niobium_titanium_single_wire",
            "nanochip/conversion_reverse/cc_nm_chip",
            "nanochip/conversion_reverse/cc_nuclear_star",
            "nanochip/conversion_reverse/cc_optical_assembly",
            "nanochip/conversion_reverse/cc_optical_computer",
            "nanochip/conversion_reverse/cc_optical_mainframe",
            "nanochip/conversion_reverse/cc_optical_processor",
            "nanochip/conversion_reverse/cc_optical_ram",
            "nanochip/conversion_reverse/cc_optical_smd_capacitor",
            "nanochip/conversion_reverse/cc_optical_smd_diode",
            "nanochip/conversion_reverse/cc_optical_smd_inductor",
            "nanochip/conversion_reverse/cc_optical_smd_resistor",
            "nanochip/conversion_reverse/cc_optical_smd_transistor",
            "nanochip/conversion_reverse/cc_optical_soc",
            "nanochip/conversion_reverse/cc_plate_hexanite",
            "nanochip/conversion_reverse/cc_plate_metastable_oganesson",
            "nanochip/conversion_reverse/cc_plate_rhugnor",
            "nanochip/conversion_reverse/cc_polyetheretherketone_foil",
            "nanochip/conversion_reverse/cc_proto_matter",
            "nanochip/conversion_reverse/cc_qbit_cpu_chip",
            "nanochip/conversion_reverse/cc_quantanium_frame",
            "nanochip/conversion_reverse/cc_quantumchromodynamically_confined_matter_plate",
            "nanochip/conversion_reverse/cc_radox_plate",
            "nanochip/conversion_reverse/cc_recursively_folded_negative_space",
            "nanochip/conversion_reverse/cc_ruthenium_trinium_americium_neutronate_double_wire",
            "nanochip/conversion_reverse/cc_screw_astral_titanium",
            "nanochip/conversion_reverse/cc_screw_six_phased_copper",
            "nanochip/conversion_reverse/cc_screw_universium",
            "nanochip/conversion_reverse/cc_seaborgium_plate",
            "nanochip/conversion_reverse/cc_smd_capacitor_bioware",
            "nanochip/conversion_reverse/cc_smd_capacitor_cosmic",
            "nanochip/conversion_reverse/cc_smd_capacitor_exotic",
            "nanochip/conversion_reverse/cc_smd_capacitor_supracausal",
            "nanochip/conversion_reverse/cc_smd_diode_bioware",
            "nanochip/conversion_reverse/cc_smd_diode_cosmic",
            "nanochip/conversion_reverse/cc_smd_diode_exotic",
            "nanochip/conversion_reverse/cc_smd_diode_supracausal",
            "nanochip/conversion_reverse/cc_smd_inductor_bioware",
            "nanochip/conversion_reverse/cc_smd_inductor_cosmic",
            "nanochip/conversion_reverse/cc_smd_inductor_exotic",
            "nanochip/conversion_reverse/cc_smd_inductor_supracausal",
            "nanochip/conversion_reverse/cc_smd_resistor_bioware",
            "nanochip/conversion_reverse/cc_smd_resistor_cosmic",
            "nanochip/conversion_reverse/cc_smd_resistor_exotic",
            "nanochip/conversion_reverse/cc_smd_resistor_supracausal",
            "nanochip/conversion_reverse/cc_smd_transistor_bioware",
            "nanochip/conversion_reverse/cc_smd_transistor_cosmic",
            "nanochip/conversion_reverse/cc_smd_transistor_exotic",
            "nanochip/conversion_reverse/cc_smd_transistor_supracausal",
            "nanochip/conversion_reverse/cc_starmetal_plate",
            "nanochip/conversion_reverse/cc_superconductor_lu_v",
            "nanochip/conversion_reverse/cc_superconductor_uev",
            "nanochip/conversion_reverse/cc_superconductor_uhv",
            "nanochip/conversion_reverse/cc_superconductor_uiv",
            "nanochip/conversion_reverse/cc_superconductor_umv",
            "nanochip/conversion_reverse/cc_superconductor_uv",
            "nanochip/conversion_reverse/cc_superconductor_zpm",
            "nanochip/conversion_reverse/cc_supracausal_assembly",
            "nanochip/conversion_reverse/cc_supracausal_computer",
            "nanochip/conversion_reverse/cc_supracausal_mainframe",
            "nanochip/conversion_reverse/cc_supracausal_printed_circuit_board",
            "nanochip/conversion_reverse/cc_supracausal_processing_core",
            "nanochip/conversion_reverse/cc_supracausal_processor",
            "nanochip/conversion_reverse/cc_supracausal_ram_chip",
            "nanochip/conversion_reverse/cc_thermal_superconductor",
            "nanochip/conversion_reverse/cc_titansteel_plate",
            "nanochip/conversion_reverse/cc_trinium_titanium_plate",
            "nanochip/conversion_reverse/cc_uhpic_chip",
            "nanochip/conversion_reverse/cc_ultrashort_pulse_laser",
            "nanochip/conversion_reverse/cc_unstable_star",
            "nanochip/conversion_reverse/cc_vibranium_frame",
            "nanochip/conversion_reverse/cc_wetware_assembly",
            "nanochip/conversion_reverse/cc_wetware_computer",
            "nanochip/conversion_reverse/cc_wetware_mainframe",
            "nanochip/conversion_reverse/cc_wetware_processor",
            "nanochip/conversion_reverse/cc_wire_hypogen",
            "nanochip/conversion_reverse/cc_wire_infinity",
            "nanochip/conversion_reverse/cc_wire_lumiium",
            "nanochip/conversion_reverse/cc_wire_mag_matter",
            "nanochip/conversion_reverse/cc_wire_niobium_titanium",
            "nanochip/conversion_reverse/cc_wire_proto_halkonite",
            "nanochip/conversion_reverse/cc_wire_yttrium_barium_cuprate",
            "nanochip/conversion_reverse/cc_wyvern_core",
            "nanochip/conversion_reverse/cc_zylon_foil",
            "nanochip/cosmic_organizer/001_cc_processed_cosmic_processing_core",
            "nanochip/cosmic_organizer/002_cc_processed_cosmic_ram_chip",
            "nanochip/cutting_chamber/001_cc_processed_chip_nano_cpu",
            "nanochip/cutting_chamber/002_cc_processed_chip_ram",
            "nanochip/cutting_chamber/003_cc_processed_chip_nor",
            "nanochip/cutting_chamber/004_cc_processed_chip_nand",
            "nanochip/cutting_chamber/005_cc_processed_chip_asoc",
            "nanochip/cutting_chamber/006_cc_processed_chip_piko_pic",
            "nanochip/cutting_chamber/007_cc_processed_chip_quantum_pic",
            "nanochip/cutting_chamber/008_cc_processed_chip_atto_pic",
            "nanochip/cutting_chamber/009_cc_processed_chip_zepto_pic",
            "nanochip/cutting_chamber/010_cc_processed_chip_yocto_pic",
            "nanochip/cutting_chamber/011_cc_processed_bolt_enriched_holmium",
            "nanochip/cutting_chamber/012_cc_processed_bolt_transcendent_metal",
            "nanochip/cutting_chamber/013_cc_processed_bolt_yttrium_barium_cuprate",
            "nanochip/cutting_chamber/014_cc_processed_bolt_cosmic_neutronium",
            "nanochip/cutting_chamber/015_cc_processed_bolt_chromatic_glass",
            "nanochip/cutting_chamber/016_cc_processed_bolt_umv_superconductor",
            "nanochip/cutting_chamber/017_cc_processed_bolt_white_dwarf_matter",
            "nanochip/cutting_chamber/018_cc_processed_framebox_aluminium",
            "nanochip/cutting_chamber/019_cc_processed_framebox_aluminium",
            "nanochip/cutting_chamber/020_cc_processed_framebox_tritanium",
            "nanochip/cutting_chamber/021_cc_processed_framebox_tritanium",
            "nanochip/cutting_chamber/022_cc_processed_framebox_celestial_tungsten",
            "nanochip/cutting_chamber/023_cc_processed_framebox_celestial_tungsten",
            "nanochip/cutting_chamber/024_cc_processed_framebox_hypogen",
            "nanochip/cutting_chamber/025_cc_processed_framebox_hypogen",
            "nanochip/cutting_chamber/026_cc_processed_framebox_mag_matter",
            "nanochip/cutting_chamber/027_cc_processed_framebox_mag_matter",
            "nanochip/cutting_chamber/028_cc_processed_plate_rhugnor",
            "nanochip/cutting_chamber/029_cc_processed_plate_metastable_oganesson",
            "nanochip/cutting_chamber/030_cc_processed_plate_hexanite",
            "nanochip/cutting_chamber/032_cc_processed_highly_advanced_soc",
            "nanochip/cutting_chamber/034_cc_processed_uhpic_chip",
            "nanochip/cutting_chamber/035_cc_processed_nm_chip",
            "nanochip/cutting_chamber/036_cc_processed_fm_chip",
            "nanochip/cutting_chamber/038_cc_processed_trinium_titanium_plate",
            "nanochip/cutting_chamber/039_cc_processed_qbit_cpu_chip",
            "nanochip/cutting_chamber/040_cc_processed_duranium_plate",
            "nanochip/cutting_chamber/041_cc_processed_neutronium_plate",
            "nanochip/cutting_chamber/042_cc_processed_mithril_plate",
            "nanochip/cutting_chamber/043_cc_processed_highurabilityompoundteel_plate",
            "nanochip/cutting_chamber/044_cc_processed_seaborgium_plate",
            "nanochip/cutting_chamber/045_cc_processed_quantanium_frame",
            "nanochip/cutting_chamber/046_cc_processed_titansteel_plate",
            "nanochip/cutting_chamber/047_cc_processed_infuscolium_plate",
            "nanochip/cutting_chamber/048_cc_processed_adamantine_plate",
            "nanochip/cutting_chamber/049_cc_processed_adamantium_frame",
            "nanochip/cutting_chamber/050_cc_processed_black_titanium_plate",
            "nanochip/cutting_chamber/051_cc_processed_double_technetium_plate",
            "nanochip/cutting_chamber/052_cc_processed_cinobite_plate",
            "nanochip/cutting_chamber/053_cc_processed_double_trinium_titanium_plate",
            "nanochip/cutting_chamber/054_cc_processed_vibranium_frame",
            "nanochip/cutting_chamber/055_cc_processed_double_naquadriatictaranium_plate",
            "nanochip/cutting_chamber/056_cc_processed_crystalmatrix_plate",
            "nanochip/cutting_chamber/057_cc_processed_optical_soc",
            "nanochip/cutting_chamber/058_cc_processed_starmetal_plate",
            "nanochip/cutting_chamber/059_cc_processed_cosmicneutronium_plate",
            "nanochip/cutting_chamber/060_cc_processed_radox_plate",
            "nanochip/cutting_chamber/061_cc_processed_quantumchromodynamically_confined_matter_plate",
            "nanochip/cutting_chamber/062_cc_processed_hsse_frame",
            "nanochip/cutting_chamber/063_cc_processed_hpic_chip",
            "nanochip/cutting_chamber/064_cc_processed_naquadah_bolt",
            "nanochip/cutting_chamber/065_cc_processed_europium_plate",
            // ★ 2026-09-21 队长令「纳米精度切割仓：给现有配方都加上使用维度偏移超流体的分支，减少配方时间、耗电」：
            //   57 条加速分支 = 62 条基准按**产物**去重（5 个框装件各有「低等级净化水慢 / 高等级净化水快」两条基准，
            //   只取快的那条派生；否则同一产物两条分支只差时长 ⇒ 机器选谁取决于加载顺序，快的会被压住）。
            //   时长与耗电各 ×1/2；超流体量按品类：芯片 10 L / 螺栓 5 L / 板材 50 L / 框架 100 L
            //   （芯片·板材·框架三档直接取自 GTNH 维基的 7 条锚点，见规格 nhAnchor）。
            //   规格 _census/nac_cc_superfluid_spec.json；生成器 _census/gen_cc_superfluid.js；
            //   独立体检 _census/check_cc_superfluid.js（基准指纹冻结 + 负向验证 12/12）。
            "nanochip/cutting_chamber_superfluid/001_cc_processed_chip_nano_cpu",
            "nanochip/cutting_chamber_superfluid/002_cc_processed_chip_ram",
            "nanochip/cutting_chamber_superfluid/003_cc_processed_chip_nor",
            "nanochip/cutting_chamber_superfluid/004_cc_processed_chip_nand",
            "nanochip/cutting_chamber_superfluid/005_cc_processed_chip_asoc",
            "nanochip/cutting_chamber_superfluid/006_cc_processed_chip_piko_pic",
            "nanochip/cutting_chamber_superfluid/007_cc_processed_chip_quantum_pic",
            "nanochip/cutting_chamber_superfluid/008_cc_processed_chip_atto_pic",
            "nanochip/cutting_chamber_superfluid/009_cc_processed_chip_zepto_pic",
            "nanochip/cutting_chamber_superfluid/010_cc_processed_chip_yocto_pic",
            "nanochip/cutting_chamber_superfluid/011_cc_processed_bolt_enriched_holmium",
            "nanochip/cutting_chamber_superfluid/012_cc_processed_bolt_transcendent_metal",
            "nanochip/cutting_chamber_superfluid/013_cc_processed_bolt_yttrium_barium_cuprate",
            "nanochip/cutting_chamber_superfluid/014_cc_processed_bolt_cosmic_neutronium",
            "nanochip/cutting_chamber_superfluid/015_cc_processed_bolt_chromatic_glass",
            "nanochip/cutting_chamber_superfluid/016_cc_processed_bolt_umv_superconductor",
            "nanochip/cutting_chamber_superfluid/017_cc_processed_bolt_white_dwarf_matter",
            "nanochip/cutting_chamber_superfluid/019_cc_processed_framebox_aluminium",
            "nanochip/cutting_chamber_superfluid/021_cc_processed_framebox_tritanium",
            "nanochip/cutting_chamber_superfluid/023_cc_processed_framebox_celestial_tungsten",
            "nanochip/cutting_chamber_superfluid/025_cc_processed_framebox_hypogen",
            "nanochip/cutting_chamber_superfluid/027_cc_processed_framebox_mag_matter",
            "nanochip/cutting_chamber_superfluid/028_cc_processed_plate_rhugnor",
            "nanochip/cutting_chamber_superfluid/029_cc_processed_plate_metastable_oganesson",
            "nanochip/cutting_chamber_superfluid/030_cc_processed_plate_hexanite",
            "nanochip/cutting_chamber_superfluid/032_cc_processed_highly_advanced_soc",
            "nanochip/cutting_chamber_superfluid/034_cc_processed_uhpic_chip",
            "nanochip/cutting_chamber_superfluid/035_cc_processed_nm_chip",
            "nanochip/cutting_chamber_superfluid/036_cc_processed_fm_chip",
            "nanochip/cutting_chamber_superfluid/038_cc_processed_trinium_titanium_plate",
            "nanochip/cutting_chamber_superfluid/039_cc_processed_qbit_cpu_chip",
            "nanochip/cutting_chamber_superfluid/040_cc_processed_duranium_plate",
            "nanochip/cutting_chamber_superfluid/041_cc_processed_neutronium_plate",
            "nanochip/cutting_chamber_superfluid/042_cc_processed_mithril_plate",
            "nanochip/cutting_chamber_superfluid/043_cc_processed_highurabilityompoundteel_plate",
            "nanochip/cutting_chamber_superfluid/044_cc_processed_seaborgium_plate",
            "nanochip/cutting_chamber_superfluid/045_cc_processed_quantanium_frame",
            "nanochip/cutting_chamber_superfluid/046_cc_processed_titansteel_plate",
            "nanochip/cutting_chamber_superfluid/047_cc_processed_infuscolium_plate",
            "nanochip/cutting_chamber_superfluid/048_cc_processed_adamantine_plate",
            "nanochip/cutting_chamber_superfluid/049_cc_processed_adamantium_frame",
            "nanochip/cutting_chamber_superfluid/050_cc_processed_black_titanium_plate",
            "nanochip/cutting_chamber_superfluid/051_cc_processed_double_technetium_plate",
            "nanochip/cutting_chamber_superfluid/052_cc_processed_cinobite_plate",
            "nanochip/cutting_chamber_superfluid/053_cc_processed_double_trinium_titanium_plate",
            "nanochip/cutting_chamber_superfluid/054_cc_processed_vibranium_frame",
            "nanochip/cutting_chamber_superfluid/055_cc_processed_double_naquadriatictaranium_plate",
            "nanochip/cutting_chamber_superfluid/056_cc_processed_crystalmatrix_plate",
            "nanochip/cutting_chamber_superfluid/057_cc_processed_optical_soc",
            "nanochip/cutting_chamber_superfluid/058_cc_processed_starmetal_plate",
            "nanochip/cutting_chamber_superfluid/059_cc_processed_cosmicneutronium_plate",
            "nanochip/cutting_chamber_superfluid/060_cc_processed_radox_plate",
            "nanochip/cutting_chamber_superfluid/061_cc_processed_quantumchromodynamically_confined_matter_plate",
            "nanochip/cutting_chamber_superfluid/062_cc_processed_hsse_frame",
            "nanochip/cutting_chamber_superfluid/063_cc_processed_hpic_chip",
            "nanochip/cutting_chamber_superfluid/064_cc_processed_naquadah_bolt",
            "nanochip/cutting_chamber_superfluid/065_cc_processed_europium_plate",
            "nanochip/encasement_wrapper/001_cc_processed_basic_mainframe_casing",
            "nanochip/encasement_wrapper/002_cc_processed_advanced_mainframe_casing",
            "nanochip/encasement_wrapper/003_cc_processed_advanced_mainframe_casing",
            "nanochip/encasement_wrapper/006_cc_processed_coiled_thermal_superconductor",
            "nanochip/encasement_wrapper/008_cc_processed_foil_polybenzimidazole",
            "nanochip/encasement_wrapper/009_cc_processed_foil_silicone_rubber",
            "nanochip/encasement_wrapper/010_cc_processed_foil_styrene_rubber",
            "nanochip/encasement_wrapper/011_cc_processed_foil_radox_polymer",
            "nanochip/encasement_wrapper/012_cc_processed_foil_shirabon",
            "nanochip/encasement_wrapper/013_cc_processed_foil_eternity",
            "nanochip/encasement_wrapper/014_cc_processed_polyetheretherketone_foil",
            "nanochip/encasement_wrapper/015_cc_processed_fullerene_polymer_matrix_pulp_foil",
            "nanochip/encasement_wrapper/016_cc_processed_zylon_foil",
            "nanochip/etching_array/001_cc_processed_chip_crystal_cpu",
            "nanochip/etching_array/002_cc_processed_chip_raw_advanced_crystal",
            "nanochip/etching_array/003_cc_processed_chip_crystal_so_c",
            "nanochip/exotic_organizer/001_cc_processed_exotic_processing_core",
            "nanochip/exotic_organizer/002_cc_processed_exotic_ram_chip",
            "nanochip/optical_organizer/001_cc_processed_chip_optical_cpu",
            "nanochip/optical_organizer/002_cc_processed_optical_ram",
            "nanochip/smd_processor/001_cc_processed_adv_smd_resistor",
            "nanochip/smd_processor/002_cc_processed_adv_smd_transistor",
            "nanochip/smd_processor/003_cc_processed_adv_smd_inductor",
            "nanochip/smd_processor/004_cc_processed_adv_smd_capacitor",
            "nanochip/smd_processor/005_cc_processed_adv_smd_diode",
            "nanochip/smd_processor/006_cc_processed_optical_smd_resistor",
            "nanochip/smd_processor/007_cc_processed_optical_smd_transistor",
            "nanochip/smd_processor/008_cc_processed_optical_smd_inductor",
            "nanochip/smd_processor/009_cc_processed_optical_smd_capacitor",
            "nanochip/smd_processor/010_cc_processed_optical_smd_diode",
            "nanochip/smd_processor/011_cc_processed_smd_capacitor_bioware",
            "nanochip/smd_processor/012_cc_processed_smd_transistor_bioware",
            "nanochip/smd_processor/013_cc_processed_smd_capacitor_exotic",
            "nanochip/smd_processor/014_cc_processed_smd_transistor_exotic",
            "nanochip/smd_processor/015_cc_processed_smd_capacitor_cosmic",
            "nanochip/smd_processor/016_cc_processed_smd_transistor_cosmic",
            "nanochip/smd_processor/017_cc_processed_smd_diode_bioware",
            "nanochip/smd_processor/018_cc_processed_smd_resistor_bioware",
            "nanochip/smd_processor/019_cc_processed_smd_inductor_bioware",
            "nanochip/smd_processor/020_cc_processed_smd_diode_exotic",
            "nanochip/smd_processor/021_cc_processed_smd_resistor_exotic",
            "nanochip/smd_processor/022_cc_processed_smd_inductor_exotic",
            "nanochip/smd_processor/023_cc_processed_smd_diode_cosmic",
            "nanochip/smd_processor/024_cc_processed_smd_resistor_cosmic",
            "nanochip/smd_processor/025_cc_processed_smd_inductor_cosmic",
            "nanochip/smd_processor/026_cc_processed_smd_capacitor_supracausal",
            "nanochip/smd_processor/027_cc_processed_smd_diode_supracausal",
            "nanochip/smd_processor/028_cc_processed_smd_resistor_supracausal",
            "nanochip/smd_processor/029_cc_processed_smd_transistor_supracausal",
            "nanochip/smd_processor/030_cc_processed_smd_inductor_supracausal",
            "nanochip/superconductor_splitter/001_cc_processed_superconductor_lu_v",
            "nanochip/superconductor_splitter/002_cc_processed_superconductor_zpm",
            "nanochip/superconductor_splitter/003_cc_processed_superconductor_uv",
            "nanochip/superconductor_splitter/004_cc_processed_superconductor_uhv",
            "nanochip/superconductor_splitter/005_cc_processed_superconductor_uev",
            "nanochip/superconductor_splitter/006_cc_processed_superconductor_uiv",
            "nanochip/superconductor_splitter/007_cc_processed_superconductor_umv",
            "nanochip/supracausal_organizer/001_cc_processed_supracausal_processing_core",
            "nanochip/supracausal_organizer/002_cc_processed_supracausal_ram_chip",
            "nanochip/wire_tracer/001_cc_processed_wire_niobium_titanium",
            "nanochip/wire_tracer/002_cc_processed_wire_yttrium_barium_cuprate",
            "nanochip/wire_tracer/003_cc_processed_wire_lumiium",
            "nanochip/wire_tracer/004_cc_processed_wire_proto_halkonite",
            "nanochip/wire_tracer/005_cc_processed_wire_infinity",
            "nanochip/wire_tracer/007_cc_processed_cable_optical_fiber",
            "nanochip/wire_tracer/008_cc_processed_wire_hypogen",
            "nanochip/wire_tracer/009_cc_processed_wire_mag_matter",
            "nanochip/wire_tracer/010_cc_processed_fine_cinobite_wire",
            "nanochip/wire_tracer/011_cc_processed_fine_naquadah_wire",
            "nanochip/wire_tracer/012_cc_processed_fine_dubnium_wire",
            "nanochip/wire_tracer/013_cc_processed_ruthenium_trinium_americium_neutronate_double_wire",
            "nanochip/wire_tracer/014_cc_processed_enderite_double_wire",
            "nanochip/wire_tracer/015_cc_processed_echoite_double_wire",
            "nanochip/wire_tracer/016_cc_processed_legendarium_double_wire",
            "nanochip/wire_tracer/017_cc_processed_fine_heavy_quark_degenerate_matter_wire",
            "nanochip/wire_tracer/018_cc_processed_draconiumawakened_double_wire",
            "nanochip/wire_tracer/019_cc_processed_niobium_titanium_single_wire",
            "nanochip/wire_tracer/020_cc_processed_enriched_naquadah_trinium_europium_duranide_double_wire",
            "nanochip_assembly_complex",
            "nanochip_assembly_matrix",
            "nanochip_biological_coordinator",
            "nanochip_board_processor",
            "nanochip_complex_glass",
            "nanochip_computational_matrix_casing",
            "nanochip_computational_matrix_casing_eco",
            "nanochip_cosmic_organizer",
            "nanochip_cutting_chamber",
            "nanochip_encasement_wrapper",
            "nanochip_etching_array",
            "nanochip_exotic_organizer",
            "nanochip_firewall_projection_casing",
            "nanochip_firewall_projection_casing_eco",
            "nanochip_mesh_interface_casing",
            "nanochip_optical_organizer",
            "nanochip_reinforcement_casing",
            "nanochip_smd_processor",
            "nanochip_splitter",
            "nanochip_superconductor_splitter",
            "nanochip_supracausal_organizer",
            "nanochip_vacuum_input_hatch",
            "nanochip_vacuum_output_hatch",
            "nanochip_vacuum_pipe",
            "nanochip_wire_tracer",
            "naquadah_alloy_sheetmetal",
            "naquadria_reinforced_water_plant_casing",
            "non_photonic_matter_exclusion_glass",
            "octiron_frame_crafting",
            "radox_frame_crafting",
            "shielded_accelerator_casing",
            "super_me_pattern_assembly",
            "superconductor_uev_base_dust_mix",
            "superconductor_uev_base_frame_crafting",
            "superconductor_uev_base_sheetmetal",
            "superconductor_uhv_base_dust_mix",
            "superconductor_uhv_base_frame_crafting",
            "superconductor_uhv_base_vf",
            "uev_me_pattern_assembly",
            "uev_solenoid_superconductor_coil",
            "uhv_me_pattern_assembly",
            "uhv_solenoid_superconductor_coil",
            "uiv_me_pattern_assembly",
            "uv_me_pattern_assembly",
            "zpm_me_pattern_assembly",
    };

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
            if (GTLModular.MOD_ID.equals(recipe.getId().getNamespace())) {
                ours++;
                loaded.add(recipe.getId().getPath());
            }
            // GTRecipeType 自己就实现了 vanilla 的 RecipeType，所以直接身份比较最稳
            if (recipe.getType() == com.gregtechceu.gtceu.common.data.GTRecipeTypes.CIRCUIT_ASSEMBLER_RECIPES) {
                circuits++;
            }
        }
        // ★ 2026-09-16：期望值 34 → **38**（+4 条"补充材料框架"）→ **41**（+3 条"已有材料补框架"：
        //   radox / kevlar / draconiumawakened，见 gen_nac_recipes.js）。
        // ★ 2026-09-17（第一轮 NH 对齐）：-1（删 draconiumawakened_frame_crafting）+1（加 enriched_holmium_mix）
        //   ⇒ 仍然是 **41**；同时改成**逐条清单断言**（见 EXPECTED_RECIPES），不再只看总数。
        // ★ 2026-09-17（第二轮）：清单**逐条不变**（无增无删），只把 enriched_holmium_mix 的输入
        //   补到与 NH 一致（多了 4 号非消耗程序电路）⇒ 总数仍为 **41**。
        // ★ 2026-09-17（第三轮）：**+2** —— superconductor_uhv_base_dust_mix（混合机产粉，玩家指定）
        //   + superconductor_uhv_base_vf（NH VacuumFreezerRecipes 原文）⇒ **43**。
        // ★ 2026-09-17（第四轮）：**+7** —— 7 件缺件方块的生存获取配方（见 EXPECTED_RECIPES 的
        //   第四轮说明与 gen_nac_recipes.js 第四轮段落）⇒ **50**。
        // ★ 2026-09-17（第五轮，本次）：**+2** —— UEV 粗胚链头 `superconductor_uev_base_dust_mix`
        //   （NH MixerRecipes 原文）+ 真空管道 `nanochip_vacuum_pipe`（NH 无产出配方 ⇒ 本包自定）。
        //   同时第五轮把 7 件缺件方块里的 ①②③⑤ 改成 NH 真值（装配线 / 组装机 / 流体固化机，出 1 个），
        //   并**没有**增删文件 ⇒ 条数变化只来自这 +2 ⇒ **52**。
        // ★ 2026-09-17（第六轮，P1 配方层）：**+122** —— NAC 换算表 99 条
        //   （`nanochip/conversion/cc_*.json`：真实物品×1 → CC×1，100 t / EU 0；输入物按
        //   census_have 35 + census_substitute 16 + 脚本命中 19 + P1 缺件真件 29 = 99）
        //   + 组装矩阵 23 条（`nanochip/assembly_matrix/*.json`：输入全是 CC 假栈、带 tier 6..12 与
        //   校准元数据，EU/时长逐条抄 NH）⇒ **52 → 174**。生成器 `gen_nac_p1_recipes.js`（幂等）。
        //   ⚠️ UEV 的 EBF/VF **不在**清单里：GTCEu 1.4.4 无法关闭自动 EBF/VF（全 jar 常量池实证），
        //      故按玩家规则"不手写同一条"，链子由材料属性（`.ingot()+blastTemp(11800)`）自动生成。
        // ★ 2026-09-18（玩家裁决 E，第二波）：**174 → 166**（−8）= 换算 99 → 95（−4）+ 组装矩阵 23 → 19（−4）。
        //   NAC「特殊系列」三档自造电路整组退役（皮米/量子/普朗克尺度电路 = NH 的 UMV/UXV/MAX 过渡件，
        //   维基逐字「…后续可能会移除。」）；退役记录见 `_census/dup_audit.json` 的 retired 块与
        //   `_census/nac_cc_items.json` 的 retired[]。生成器 `gen_nac_p1_recipes.js` 的 `RETIRED_CC` 是防复活闸门。
        // ★ 2026-09-18（P2 模块加工层）：**166 → 245**（+79）
        //   = P2 模块加工层 9 张表 79 条（`nanochip/<模块>/<NNN>_cc_processed_*.json`；
        //   未处理 CC × 1 + 可选流体 → 已处理 PC × 1，时长/EU 逐条抄 NH）。
        //   ★ 清单是 83 条、落地 79 条：裁决 ADJ-1 扣留板处理器 4 条 fake（0 mB 流体语义未取证）。
        //   ⚠️ 与 P1 不同点：这 79 条**一条真实物品槽位都没有**（P2 的槽位全是 CC 假栈），
        //      且**没有**不消耗标记（NH 这 9 张表的槽位全是消耗品）。
        // ★ 2026-09-18（队长指示·新增三台组织器）：**总数不变（456）**，只是 6 条配方换了模块目录 ——
        //   把三族（奇异 / 寰宇 / 超因果）的「处理核心 + RAM 晶片」从它们原先挂着的模块
        //   搬到各自族的新组织器里（「同族归同族组织器」，光学那台早就是这么分的）：
        //     · 处理核心 3 条：`biological_coordinator/009|012|016` → `<族>_organizer/001`（生物调谐器本来就是湿件/生物专属）；
        //     · RAM 晶片 3 条：`cutting_chamber/031|033|037`   → `<族>_organizer/002`（光学 RAM 也在光学组织器里）。
        //   只改 `type` 字段，**时长 / EU / 输入 / 输出一字不动**（不碰上一轮落地的电压上限）。
        //   搬迁脚本（可复跑）：`_census/move_family_recipes_to_organizers.js`；
        //   波2 规格 `_census/nac_wave2_p2_spec.json` 的 `module` 字段同步跟着走。
        final String breakdown = "5 方块 + 15 机器 + 2 仓室 + 15 既有"
                + " + 4 补充材料框架 + 3 既有材料补框架（radox / kevlar / octiron；★ 2026-09-21 再加 degenerate_rhenium："
                + "队长在 JEI 里确认「简并态铼框架没配方」，故补一条框架配方 `degenerate_rhenium_frame_crafting.json`，塔身一格不动） + 1 富集钬链头 + 2 UHV 粗胚链"
                + " + 7 缺件方块配方 + 1 UEV 粗胚链 + 1 真空管道 + 1 超级冷却液（2026-09-21 队长拍板：GTL 无此流体，本模组注册并给化学反应釜配方）"
                + " + 1 维度偏移超流体（2026-09-21 队长给数：超维度等离子锻炉 7500 mB，本模组注册该流体）"
                + " + 94 纳米芯片换算 + 10 组装矩阵（仅晶体/湿件两族沿用 NH 段移植；原为 99 与 23，2026-09-18 退役「特殊系列」8 条，2026-09-21 再退役「时空导线」链 1 条）"
                + " + 78 纳米芯片模块加工（P2 的 9 张模块表；清单 82 条 − 裁决 ADJ-1 扣留板处理器 4 条 fake）"
                + " + 103 波2 自造段纳米芯片换算（NH 段 94 与波2 103，共 197 条换算；真件=源物品，5 s / EU 0）"
                + " + 88 波2 模块加工（P2 的 9 张模块表；未处理 CC 变已处理 PC，档位照包内 tag.js 电路档位）"
                + " + 57 纳米精度切割仓「维度偏移超流体」加速分支（2026-09-21 队长令：现有配方都加超流体分支、减少时长与耗电；"
                + "62 条基准按产物去重成 57 条，时长与耗电各 ×1/2，超流体量按品类 芯片 10 / 螺栓 5 / 板材 50 / 框架 100 L；"
                + "目录 data/gtlmodular/recipes/nanochip/cutting_chamber_superfluid/，规格 _census/nac_cc_superfluid_spec.json）"
                + " + 6 三台新组织器模块加工（2026-09-18 队长指示：三族「处理核心与 RAM 晶片」分别归奇异/寰宇/超因果优化组织器）"
                + " + 23 GTL 官方配方重推（3 条成品 mainframe 换算与 20 条五族组装矩阵；规格 nac_matrix_spec.json）"
                + " + 10 缺件获取链（2026-09-19：残留物品清理报告 §8.2 的 10 件「被我方配方引用但游戏里拿不到」"
                + "逐条补上获得配方 —— 4 件外壳走切割机切板、5 件功率IC走激光蚀刻机蚀刻、1 件活性晶体走生物调谐器；"
                + "目录 data/gtlmodular/recipes/missing_acquisition/，交付说明见 _census/缺件获取链_队员.md）"
                + " + 5 新造PIC晶圆两步链（2026-09-19 队长「那就新造还原吧」：新注册 5 件 PIC 晶圆"
                + "（量子/阿米/仄米/幺米/普朗克功率IC晶圆，NanochipMissingWafers）把 NH 的两步链还原 ——"
                + "第一步 激光蚀刻机 母晶圆与橙色透镜(不消耗) 造出同级晶圆，第二步 板材切割机 同级晶圆切出芯片；"
                + "原 5 条一步链（激光蚀刻机 母晶圆直接到芯片）就地改写成第二步，净增 5 条；"
                + "交付说明见 _census/新造晶圆还原两步链_队员.md）"
                + " + 200 反向换算（2026-09-19 队长方案A「简单模式下换算也要能用样板总成下单」："
                + "为 conversion/ 里 200 条「真件 → CC」逐条生成对偶「CC → 真件」，落独立目录 conversion_reverse/；"
                + "生成器 _census/gen_nac_conversion_reverse.js（正向表是唯一真相源），"
                + "守卫体检 _census/check_conversion_reverse.js）"
                + " + 2 机壳装配线版（★ 2026-09-21 队长给数：计算矩阵 / 防火墙投影机壳各一条装配线配方，"
                + "用 Eco 模组的 CE9 闪存晶阵 / FT9 并行核心 / 合成子系统结构散热器；包内没有 Eco 时由原组装机版生效"
                + "——两条原配方已加 forge:not 条件，两份配方互斥不共存）";
        int expected = EXPECTED_RECIPES.length;
        out.add((ours == expected ? "✓" : "✗") + " 本模组配方加载: " + ours + " / " + expected
                + "（" + breakdown + "）");
        int missing = 0;
        for (String id : EXPECTED_RECIPES) {
            if (!loaded.contains(id)) {
                missing++;
                out.add("✗ 配方缺失: gtlmodular:" + id);
            }
        }
        out.add((missing == 0 ? "✓" : "✗") + " 配方清单齐全: " + (expected - missing) + "/" + expected
                + "（逐条点名，不只是总数）");
        // 反向检查：加载了、却没登记进 EXPECTED_RECIPES ⇒ 清单漏了一条。
        // 与上面那段合起来 = 「清单 ⇔ 目录」双射（光有正向会漏掉"清单忘了登记"这种情况）。
        final java.util.List<String> expectedList = java.util.Arrays.asList(EXPECTED_RECIPES);
        int extra = 0;
        for (String path : loaded) {
            if (!expectedList.contains(path)) {
                extra++;
                out.add("✗ 配方未登记进 EXPECTED_RECIPES（清单漏了它）: gtlmodular:" + path);
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
        out.add("· NAC 配置: 直通档并行上限=" + GTLModularConfig.getNanochipDirectParallelCap()
                + " 每配方杂质=" + GTLModularConfig.getNanochipImpurityPerRecipe() + "%"
                + " 调试日志=" + GTLModularConfig.isNanochipDebugLogsEnabled());
        // ★ 真空路由模式（2026-09-16 双模式）：管道模式下"不铺同色管道就不传输"是**正确行为**，
        //   这里显示出来，免得玩家把它当 bug（同一个字符串也用在同一台机器的界面提示里）。
        out.add("· 真空路由模式: " + com.gtlmodular.common.machine.nanochip.NanochipAssemblyComplexMachine.routingModeText()
                + "（配置项 nanochipComplex.vacuumRequiresPipes="
                + GTLModularConfig.isVacuumRequiresPipes() + "）");
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
