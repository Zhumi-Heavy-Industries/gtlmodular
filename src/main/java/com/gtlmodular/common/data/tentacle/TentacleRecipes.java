package com.gtlmodular.common.data.tentacle;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.machine.multiblock.CleanroomType;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.lookup.GTRecipeLookup;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.data.recipe.builder.GTRecipeBuilder;
import com.gtlmodular.GTLModular;
import com.lowdragmc.lowdraglib.side.fluid.FluidStack;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.registries.ForgeRegistries;
import org.gtlcore.gtlcore.common.data.GTLRecipeTypes;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 奈亚拉托提普之触 · 配方里<b>数据包 JSON 表达不了</b>的那一批 —— 服务端运行期注入。
 *
 * <h2>为什么这两族必须走 Java（而不是 {@code data/gtlmodular/recipes/tentacle/*.json}）</h2>
 * <ul>
 *   <li><b>提取机（{@code gtceu:extractor}）+ 不消耗的 tag {@code #gtlmodular:spawn_eggs}</b>：
 *       JSON 的 {@code gtceu:sized} 内容里「不消耗（chance=0）」只能逐条写具体物品，
 *       而这里要的是「<b>任意生物蛋</b>」—— 一个由 tag 决定、且 tag 内容随整合包变化的多值集合。
 *       写成 Java 后 tag 在服务端启动时按真实注册表解析，装了什么模组就有多少生物蛋。</li>
 *   <li><b>装配线（{@code gtceu:assembly_line}）+ 研究门（station research）</b>：
 *       GTCEu 1.4.4 的研究门是 {@code ResearchCondition}，它由 {@code GTRecipeBuilder#stationResearch(...)}
 *       在<b>构建期</b>塞进 recipe 的 conditions，并且还要用同一个 researchId 往
 *       {@code GTRecipeType#addDataStickEntry(...)} 登记「数据棒能编码这份配方」——
 *       这两件事都在 Java 构建器里，数据包 JSON 只能写已经算好的 conditions，无法生成研究条目。</li>
 * </ul>
 *
 * <h2>注入时机与写法（照本工程既有套路）</h2>
 * 与 {@code com.gtlmodular.common.data.print.DisposableToolPrintRecipes}、
 * {@code com.gtlmodular.common.data.module.SpaceOreRecipeMirror} 同一套：
 * 在 <b>{@code ServerStartedEvent}</b>（Forge 事件总线，配方管理器已就绪）里构建
 * {@link GTRecipe} 并<b>双写</b>：
 * <ol>
 *   <li><b>lookup</b>：{@code GTRecipeType.getLookup().addRecipe(...)} —— 机器找配方走这里；</li>
 *   <li><b>byType</b>：{@code RecipeManager.byType} 对应类型那一格 —— JEI 分类与样板识别读这里。</li>
 * </ol>
 * 只写 lookup ⇒「机器能跑但 JEI 空」；只写 byType ⇒「JEI 有但机器不认」。
 * 全程 try/catch，失败只打日志，绝不影响启动。
 *
 * <h2>与 KubeJS 原脚本的对应关系</h2>
 * 原 {@code gtlmodular_tentacle_research.js} 的 8 组配方里：
 * <ul>
 *   <li>①氙等离子体 ②彩色玻璃等离子体 ③等离子冷凝 ④彩色玻璃板 ⑤硫等离子体
 *       ⑥安普洛硼玻璃（流体固化）/电子渗透涂层玻璃（装配机 + 无菌净室）——
 *       全部是纯 JSON（见 {@code data/gtlmodular/recipes/tentacle/}）；</li>
 *   <li>⑥超级大脑（化学反应釜）、⑦神经矩阵（电路组装机）—— 纯 JSON；</li>
 *   <li>⑦'提取机大脑 / ⑧'夸克管道（装配线 + 研究门）—— <b>本类</b>。</li>
 * </ul>
 * 复制的是原脚本的数值：提取机 EUt 30 / 200t；装配线 EUt 31457280 / 400t，
 * 研究 EUt 31457280、CWUt 2048/8192000，研究样本 {@code gtceu:heavy_quark_degenerate_matter_nonuple_fluid_pipe}，
 * 数据介质 {@code gtceu:data_module}。
 */
public final class TentacleRecipes {


    // ------------------------------------------------------------------ 常量（照抄 KubeJS 原脚本）

    /** 提取机：大脑。{@code gtlmodular:cerebrum}（KubeJS 原 EUt 30 / 200t）。 */
    private static final ResourceLocation CEREBRUM_ID = rl("cerebrum");
    private static final long CEREBRUM_EUT = 30L;
    private static final int CEREBRUM_DURATION = 200;

    /** 装配线：夸克管道。KubeJS 原 EUt 31457280 / 400t。 */
    private static final ResourceLocation QUARK_PIPE_ID = rl("quark_pipe");
    private static final long QUARK_PIPE_EUT = 31_457_280L;
    private static final int QUARK_PIPE_DURATION = 400;

    /** 研究门：研究机 EUt 31457280，计算量 2048 CWU/t、总计 8192000 CWU（KubeJS 原值）。 */
    private static final int RESEARCH_EUT = 31_457_280;
    private static final int RESEARCH_CWUT = 2048;
    private static final int RESEARCH_TOTAL_CWU = 8_192_000;

    /** 提取机不消耗的「任意生物蛋」tag（jar 内 {@code data/gtlmodular/tags/items/spawn_eggs.json}）。 */
    private static final TagKey<Item> SPAWN_EGGS = TagKey.create(Registries.ITEM, rl("spawn_eggs"));

    /** 装配线的两条电路 tag（KubeJS 写法 {@code 8x #gtceu:circuits/uev} / {@code 4x #gtceu:circuits/uiv}）。 */
    private static final TagKey<Item> CIRCUITS_UEV =
            TagKey.create(Registries.ITEM, new ResourceLocation("gtceu", "circuits/uev"));
    private static final TagKey<Item> CIRCUITS_UIV =
            TagKey.create(Registries.ITEM, new ResourceLocation("gtceu", "circuits/uiv"));

    // ------------------------------------------------------------------ 0.3.270 研究/计算件
    //
    // 出处（GTOCore 26.10.1 字节码，反编译后逐条核对）：
    //   ① 生物计算机外壳   com/gtocore/data/recipe/processing/NewResearchSystem.class :124
    //                     make_biocomputer_casing（ASSEMBLER，400t，EUt VA[8]=524288）
    //   ② NICH 空组件      NewResearchSystem.class :126  make_nich_empty_component（ASSEMBLY_LINE，200t，VA[9]）
    //   ③ NICH 计算组件    NewResearchSystem.class :127  make_nich_computing_components（同机同档）
    //   ④ GWCA 空组件      com/gtocore/data/recipe/classified/AssemblyLineA.class :111
    //                     gwca_empty_component（ASSEMBLY_LINE，323t，VA[11]，带 stationResearch CWUt 1024）
    //   ⑤ GWCA 计算组件    com/gtocore/data/recipe/classified/AssemblyLine.class :298
    //                     graviton_computer_casing1（2000mB 冰凝胶，300t，VA[11]）
    //   ⑥ 里德堡旋量集群   com/gtocore/data/recipe/classified/PrecisionAssembler.class :74
    //                     （PRECISION_ASSEMBLER，560t，7.86MEU，无菌净室，氙 10mB 不消耗）
    //   ⑦ 相对论旋量存储器 com/gtocore/data/recipe/classified/AssemblerModule.class :50
    //                     （ASSEMBLER_MODULE，800t，VA[UXV]，SEPMTier 4）
    //   ⑧ 黑曜石矩阵       AssemblerModule.class :62（1600t，62914560 EUt，SEPMTier 4）
    //
    // 本模组没有 GTOCore，材料一律换成整合包现有的等价物（详见每条 builder 的注释）；
    // 装配机/装配线两条用 JSON（data/gtlmodular/recipes/tentacle/），
    // 需要「不消耗流体 / 净室 / 研究门 / addData」的四条走本类运行期注入。

    /** 生物计算机外壳：装配机 400t，EUt 524288（GTValues.VA[8]）。 */
    private static final long BIOCOMPUTER_CASING_EUT = 524_288L;
    private static final int BIOCOMPUTER_CASING_DURATION = 400;

    /** NICH 空/计算组件：装配线 200t，EUt 2097152（VA[9]）。 */
    private static final long NICH_EUT = 2_097_152L;
    private static final int NICH_DURATION = 200;

    /** GWCA 空组件：装配线 323t，EUt 33554432（VA[11]）+ 研究门（VA[10] / 1024 CWU/t / 1843200 CWU）。 */
    private static final long GWCA_EMPTY_EUT = 33_554_432L;
    private static final int GWCA_EMPTY_DURATION = 323;
    private static final int GWCA_RESEARCH_EUT = 8_388_608;
    private static final int GWCA_RESEARCH_CWUT = 1024;
    private static final int GWCA_RESEARCH_TOTAL_CWU = 1_843_200;

    /** GWCA 计算组件：装配线 300t，EUt 33554432（VA[11]）。 */
    private static final long GWCA_COMPUTING_EUT = 33_554_432L;
    private static final int GWCA_COMPUTING_DURATION = 300;

    /** 里德堡旋量集群：精密装配机 560t，EUt 8388608（VA[UEV]）。 */
    private static final long RYDBERG_EUT = 8_388_608L;
    private static final int RYDBERG_DURATION = 560;

    /** 相对论旋量存储器：组装模块 800t，EUt 134217728（VA[UXV]），SEPMTier 4。 */
    private static final long RELATIVISTIC_EUT = 134_217_728L;
    private static final int RELATIVISTIC_DURATION = 800;

    /** 黑曜石矩阵：组装模块 1600t，EUt 62914560，SEPMTier 4（GTO 原值）。 */
    private static final long OBSIDIAN_MATRIX_EUT = 62_914_560L;
    private static final int OBSIDIAN_MATRIX_DURATION = 1600;

    /** 装配线的 12 个物品输入（顺序与 KubeJS 原脚本一致）。 */
    private static final String[][] QUARK_PIPE_ITEMS = {
            {"gtceu:naquadah_frame", "4"},
            {"gtceu:enriched_naquadah_frame", "4"},
            {"gtceu:naquadria_frame", "4"},
            {"gtceu:vibranium_frame", "4"},
            {"gtceu:heavy_quark_degenerate_matter_tiny_fluid_pipe", "16"},
            {"gtceu:fullerene_polymer_matrix_pulp_tiny_fluid_pipe", "16"},
            {"gtceu:neutronium_tiny_fluid_pipe", "16"},
            {"gtceu:enderium_tiny_fluid_pipe", "16"},
            {"gtceu:uiv_electric_pump", "2"},
            {"gtceu:chromatic_glass_plate", "8"},
    };

    /** 装配线的 4 个流体输入（顺序与 KubeJS 原脚本一致）。 */
    private static final String[][] QUARK_PIPE_FLUIDS = {
            {"gtceu:mutated_living_solder", "1152"},
            {"gtceu:super_mutated_living_solder", "1152"},
            {"gtceu:mithril", "2304"},
            {"gtceu:uruium", "2304"},
    };

    /** 研究样本 / 数据介质（gtceu 自带物品）。 */
    private static final String RESEARCH_STACK_ID = "gtceu:heavy_quark_degenerate_matter_nonuple_fluid_pipe";
    private static final String RESEARCH_DATA_ID = "gtceu:data_module";

    /** 解析出来的 {@code RecipeManager.byType}（每次 ServerStartedEvent 重新取，null 表示解析失败）。 */
    private static Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> byTypeMap;

    /** byType 反射字段缓存。 */
    private static Field byTypeField;
    private static boolean byTypeFailed;

    private TentacleRecipes() {
    }

    // ------------------------------------------------------------------ 挂载 / 事件

    /**
     * 在 {@code GTLModular} 的 {@code @Mod} 构造里调用一次：把监听器挂到 <b>Forge 事件总线</b>上。
     */
    public static void register() {
        // ServerStartedEvent 是 Forge 事件总线上的事件（不是 mod 总线）；与打印表/矿石镜像同一写法。
        MinecraftForge.EVENT_BUS.addListener(TentacleRecipes::onServerStarted);
        GTLModular.LOGGER.info("[gtlmodular] 奈亚之触运行期配方已挂载：提取机（任意生物蛋→大脑）"
                + " + 装配线（夸克管道，带研究门），在服务器启动完成后注入。");
        // JSON 里写不了注释，替代项统一在这里交代（0.3.270 研究/计算件）：
        GTLModular.LOGGER.info("[gtlmodular] 研究链替代口径（GTO→本整合包）："
                + "细线钛→gtceu:fine_niobium_titanium_wire（本包钛无 GENERATE_FINE_WIRE）；"
                + "gelid_cryotheum→gtceu:liquid_helium；graviton_computer_casing→gtceu:advanced_computer_casing；"
                + "atomic_archives→gtlmodular:neural_matrix；plateDense 硅岩塔兰→gtceu:naquadriatictaranium_plate；"
                + "haderoth→gtceu:neutronium；GTO 四色黑曜石方块→无对应（黑曜石矩阵配方不含）。");
        // 0.3.271 集控核心：材料前缀逐条按本包核对过；只交代「上游有、本包没有」的替代。
        // ★ 0.3.310：UV 档两处修正（计算单元按 GTO 改 data_module；研究样本改 super_cerebrum），
        //   UEV 档计算单元按 GTO 改回 neural_matrix。
        GTLModular.LOGGER.info("[gtlmodular] 集控核心替代口径（GTO→本整合包）："
                + "UV 计算单元＝gtceu:data_module（GTO 原值）、UV 研究样本＝gtlmodular:super_cerebrum"
                + "（GTO 原为 UV 核心自身，本包无任务奖励样本 ⇒ 改用研究链前置件）；"
                + "计算单元 GTO 的 atomic_archives→gtlmodular:obsidian_matrix（UIV/UXV/OpV 档）；"
                + "0.3.280 MAX 档配方已注入（部件装配 64 个/次），"
                + "缺的 ChaosInfinityAlloy / SpacetimeContinuum / 闭时曲线制导单元 / 黑曜石矩阵"
                + "按『不替代、不占位』跳过，见启动日志 WARN 的跳过清单。");
        // 研究门完整性的口径（2026-11-01 二次核对 GTCEu 1.4.4 字节码后补的）：
        GTLModular.LOGGER.info("[gtlmodular] 研究门口径：所有运行期注入的 stationResearch 配方"
                + "（夸克管道 / 奈亚主机 / GWCA空组件 / 集控核心各档）都会同时补一条『研究站配方』"
                + "（样本 + 空白数据介质 → 带 assembly_line_research NBT 的数据介质）——"
                + "GTCEu 只在 save() 路径生成它，buildRawRecipe() 不会，缺了研究门解不开。");
    }

    private static void onServerStarted(ServerStartedEvent event) {
        install(event.getServer());
    }

    /**
     * 构建并注入两批配方。幂等：重复调用只是把同一 id 的配方再写一遍
     * （lookup 对同 id 同内容重复添加返回 false，不会累加；byType 是 Map.put 覆盖）。
     */
    public static synchronized void install(MinecraftServer server) {
        if (server == null) {
            return;
        }
        try {
            RecipeManager manager = server.getRecipeManager();
            byTypeMap = byType(manager);
            int ok = 0;
            ok += inject(GTRecipeTypes.EXTRACTOR_RECIPES, buildCerebrum());
            ok += inject(GTRecipeTypes.ASSEMBLY_LINE_RECIPES, buildQuarkPipe());
            // 0.3.270 研究/计算件：四条「JSON 表达不了」的，逐条独立兜底
            ok += installSafe(GTRecipeTypes.ASSEMBLY_LINE_RECIPES, TentacleRecipes::buildGwcaEmptyComponent,
                    "GWCA空组件");
            ok += installSafe(GTLRecipeTypes.PRECISION_ASSEMBLER_RECIPES, TentacleRecipes::buildRydbergSpinorialAssembly,
                    "里德堡旋量集群");
            ok += installSafe(GTLRecipeTypes.ASSEMBLER_MODULE_RECIPES,
                    TentacleRecipes::buildRelativisticSpinorialMemorySystem, "相对论旋量存储器");
            ok += installSafe(GTLRecipeTypes.ASSEMBLER_MODULE_RECIPES, TentacleRecipes::buildObsidianMatrix,
                    "黑曜石矩阵");
            // 0.3.271 集控核心：6 档（UV..OpV）×2 条（装配线 + 部件装配线）
            int coreOk = installControlCores();
            // 0.3.278 奈亚主机：GTO 组装线原方（含研究门）
            ok += installSafe(GTRecipeTypes.ASSEMBLY_LINE_RECIPES, TentacleRecipes::buildNyarlathotepsTentacle,
                    "奈亚主机");
            GTLModular.LOGGER.info("[gtlmodular] 奈亚之触运行期配方注入完成：{} 条"
                    + "（提取机 + 装配线 + GWCA空组件 + 里德堡/相对论/黑曜石矩阵 + 集控核心 {} 条 + 奈亚主机）",
                    ok + coreOk, coreOk);
        } catch (Throwable t) {
            GTLModular.LOGGER.warn("[gtlmodular] 奈亚之触运行期配方注入失败（已忽略，不影响启动）：{}",
                    t.toString());
        }
    }

    // ------------------------------------------------------------------ 构建


    /** 提取机：任意生物蛋（不消耗）→ 大脑。 */
    private static GTRecipe buildCerebrum() {
        Item cerebrum = TentacleItems.CEREBRUM.get();
        if (cerebrum == null) {
            GTLModular.LOGGER.warn("[gtlmodular] 奈亚之触：gtlmodular:cerebrum 未注册 ⇒ 跳过提取机配方");
            return null;
        }
        Ingredient eggs = Ingredient.of(SPAWN_EGGS);
        if (eggs.getItems().length == 0) {
            GTLModular.LOGGER.warn("[gtlmodular] 奈亚之触：tag #gtlmodular:spawn_eggs 为空"
                    + "（数据包没加载？）—— 提取机配方仍会登记，但机器不会匹配任何生物蛋");
        }
        return GTRecipeTypes.EXTRACTOR_RECIPES.recipeBuilder(CEREBRUM_ID)
                .notConsumable(eggs)
                .outputItems(new ItemStack(cerebrum))
                .EUt(CEREBRUM_EUT)
                .duration(CEREBRUM_DURATION)
                .buildRawRecipe();
    }

    /** 装配线：12 物品 + 4 流体 → 夸克管道，带研究门。 */
    private static GTRecipe buildQuarkPipe() {
        Item output = item("gtlmodular:quark_pipe");
        if (output == null) {
            GTLModular.LOGGER.warn("[gtlmodular] 奈亚之触：gtlmodular:quark_pipe 未注册 ⇒ 跳过装配线配方");
            return null;
        }
        ItemStack researchStack = stack(RESEARCH_STACK_ID, 1);
        ItemStack dataStack = stack(RESEARCH_DATA_ID, 1);
        if (researchStack.isEmpty() || dataStack.isEmpty()) {
            GTLModular.LOGGER.warn("[gtlmodular] 奈亚之触：研究样本 {} / 数据介质 {} 缺失 ⇒ 跳过装配线配方",
                    RESEARCH_STACK_ID, RESEARCH_DATA_ID);
            return null;
        }

        List<ItemStack> items = new ArrayList<>();
        for (String[] entry : QUARK_PIPE_ITEMS) {
            ItemStack stack = stack(entry[0], Integer.parseInt(entry[1]));
            if (stack.isEmpty()) {
                GTLModular.LOGGER.warn("[gtlmodular] 奈亚之触：装配线输入 {} 不存在 ⇒ 跳过夸克管道配方", entry[0]);
                return null;
            }
            items.add(stack);
        }
        List<FluidStack> fluids = new ArrayList<>();
        for (String[] entry : QUARK_PIPE_FLUIDS) {
            FluidStack stack = fluid(entry[0], Integer.parseInt(entry[1]));
            if (stack == null) {
                GTLModular.LOGGER.warn("[gtlmodular] 奈亚之触：装配线流体 {} 不存在 ⇒ 跳过夸克管道配方", entry[0]);
                return null;
            }
            fluids.add(stack);
        }

        GTRecipeBuilder builder = GTRecipeTypes.ASSEMBLY_LINE_RECIPES.recipeBuilder(QUARK_PIPE_ID);
        for (ItemStack stack : items) {
            builder.inputItems(stack);
        }
        builder.inputItems(CIRCUITS_UEV, 8);
        builder.inputItems(CIRCUITS_UIV, 4);
        builder.inputFluids(fluids.toArray(new FluidStack[0]));
        builder.outputItems(new ItemStack(output));
        builder.EUt(QUARK_PIPE_EUT).duration(QUARK_PIPE_DURATION);
        // 研究门：等价于 KubeJS 的 .stationResearch(b => b.researchStack(...).dataStack(...).EUt(...).CWUt(2048, 8192000))
        builder.stationResearch(b -> b
                .researchStack(researchStack)
                .dataStack(dataStack)
                .EUt(RESEARCH_EUT)
                .CWUt(RESEARCH_CWUT, RESEARCH_TOTAL_CWU));

        GTRecipe recipe = builder.buildRawRecipe();
        // stationResearch 只把研究写进 ResearchCondition；数据棒要能编码这份配方，还得按同一个
        // researchId 往配方表登记（datagen 的 save() 里替我们做的那一步，运行期要自己补）。
        for (GTRecipeBuilder.ResearchRecipeEntry entry : builder.researchRecipeEntries()) {
            GTRecipeTypes.ASSEMBLY_LINE_RECIPES.addDataStickEntry(entry.researchId(), recipe);
            GTLModular.LOGGER.info("[gtlmodular] 奈亚之触：夸克管道研究条目已登记 researchId={}", entry.researchId());
            injectStationResearch("夸克管道", rl("quark_pipe_research_station"), entry);
        }
        return recipe;
    }

    // ------------------------------------------------------------------ 0.3.278 奈亚主机本体配方

    /** 奈亚主机：GTO 组装线原方参数（{@code AssemblyLine.class} 字节码：EUt 7864320 / 3200t）。 */
    private static final long TENTACLE_EUT = 7_864_320L;
    private static final int TENTACLE_DURATION = 3200;

    // ---------------------------------------------------------------- 0.3.284 研究门改「夸克管道」
    //
    // 要求原话：「把奈亚配方里面的研究改成用夸克管道研究以后得到奈亚」。
    // 改法：研究样本（researchStack）从 GTO 原方的 {@code gtceu:assembler_module}（组装模块）
    // 换成 {@code gtlmodular:quark_pipe} —— 就是奈亚结构符号 ] 那件机械方块（36 个一份，
    // 注册在 {@link TentacleBlocks#QUARK_PIPE}，带 BlockItem，所以能当研究样本）。
    // 于是获取链条变成：先按「夸克管道」的装配线配方（本类 buildQuarkPipe，自带研究门）
    // 造出夸克管道 → 把它送进研究站编码出数据介质 → 奈亚主机的装配线配方才解锁。
    // 数据介质仍是 {@code gtceu:data_module}（IDataItem，与文件里其它研究门一致）。

    /** 研究样本 = 夸克管道（0.3.284 前是 {@code gtceu:assembler_module}，即 GTO 原方）。 */
    private static final String TENTACLE_RESEARCH_STACK_ID = "gtlmodular:quark_pipe";

    /**
     * 研究门参数（0.3.284 定值，理由写在下面）。
     *
     * <p>EUt 与同文件「夸克管道」那条研究门<b>持平</b>：{@code 31457280}（≈UIV 档）。
     * 样本就是夸克管道那条配方做出来的东西，玩家要凑到夸克管道就已经具备这个研究站等级，
     * 奈亚这边不再额外抬高能源仓门槛。
     *
     * <p>CWU/t 同样是 {@code 2048}（与夸克管道研究同一速率），总 CWU 取 <b>两倍</b> {@code 16384000}
     * （= 2048×8000t ⇒ 400 秒），因为奈亚是终端主机、研究量级稍高于中间件。
     * 参考：夸克管道那条是 2048 CWU/t / 总 8192000（200 秒），本条即「速率持平、总量翻倍」。
     */
    private static final int TENTACLE_RESEARCH_EUT = 31_457_280;
    private static final int TENTACLE_RESEARCH_CWUT = 2048;
    private static final int TENTACLE_RESEARCH_TOTAL_CWU = 16_384_000;

    /**
     * 奈亚拉托提普之触本体 · 组装线配方（1 台/次）。
     *
     * <p><b>出处</b>：GTOCore 26.10.1 的 {@code com/gtocore/data/recipe/classified/AssemblyLine.class}
     * 字节码（{@code recipeBuilder("suprachronal_assembly_line")} 那一段），逐条抄数值：
     * <ul>
     *   <li>物品：无限单元组件×64（{@code gtocore:infinite_cell_component} → 本包
     *       {@code gtlcore:infinite_cell_component}）+ 进阶装配线×64 + 电路装配线×64 +
     *       超时空装配外壳×16 + 超时空装配单元×16 + 分子线圈×32 + UEV 发射器×32 +
     *       UEV 传感器×32 + 精密电路装配机器人MK3×64 + UIV 电路×64 + 终极电池×1 +
     *       达利塞尼特双层板×32；</li>
     *   <li>流体：硼硅玻璃 3204 / 硅橡胶 5760 / 铀三铂 1296 / 司太立100 5184；</li>
     *   <li>7,864,320 EU/t、3200t；</li>
     *   <li><b>研究门（0.3.284 改）</b>：样本 = <b>{@code gtlmodular:quark_pipe}（夸克管道）</b>，
     *       数据介质 = {@code gtceu:data_module}，EUt 31,457,280（与夸克管道那条研究门持平），
     *       CWU/t 2048、总 CWU 16,384,000（400 秒）。
     *       GTO 原方是样本 = 组装模块（{@code gtceu:assembler_module}）/ CWU/t 256 / EUt 7,864,320；
     *       按队长口径「把奈亚配方里面的研究改成用夸克管道研究以后得到奈亚」，整条换成夸克管道研究，
     *       其余物品/流体输入与产出<b>一字不动</b>。</li>
     * </ul>
     */
    private static GTRecipe buildNyarlathotepsTentacle() {
        Item output = item("gtlmodular:nyarlathoteps_tentacle");
        if (output == null) {
            GTLModular.LOGGER.warn("[gtlmodular] 奈亚主机：gtlmodular:nyarlathoteps_tentacle 未注册 ⇒ 跳过本体配方");
            return null;
        }
        List<ItemStack> items = inputStacks("奈亚主机", new String[][]{
                {"gtlcore:infinite_cell_component", "64"},
                {"gtceu:advanced_assembly_line", "64"},
                {"gtceu:circuit_assembly_line", "64"},
                {"kubejs:spacetime_assembly_line_casing", "16"},
                {"kubejs:spacetime_assembly_line_unit", "16"},
                {"kubejs:molecular_coil", "32"},
                {"gtceu:uev_emitter", "32"},
                {"gtceu:uev_sensor", "32"},
                {"kubejs:precision_circuit_assembly_robot_mk3", "64"},
                {"gtceu:max_battery", "1"},
                {"gtceu:double_dalisenite_plate", "32"},
        });
        if (items == null) {
            return null;
        }
        List<FluidStack> fluids = inputFluidStacks("奈亚主机", new String[][]{
                {"gtceu:borosilicate_glass", "3204"},
                {"gtceu:silicone_rubber", "5760"},
                {"gtceu:uranium_triplatinum", "1296"},
                {"gtceu:stellite_100", "5184"},
        });
        if (fluids == null) {
            return null;
        }
        ItemStack researchStack = stack(TENTACLE_RESEARCH_STACK_ID, 1);
        ItemStack dataStack = stack(DATA_MODULE_ID, 1);
        if (researchStack.isEmpty() || dataStack.isEmpty()) {
            GTLModular.LOGGER.warn("[gtlmodular] 奈亚主机：研究样本/数据介质缺失 ⇒ 跳过本体配方"
                    + "（研究样本 {}（夸克管道）/ 数据介质 {}）", TENTACLE_RESEARCH_STACK_ID, DATA_MODULE_ID);
            return null;
        }

        GTRecipeBuilder builder = GTRecipeTypes.ASSEMBLY_LINE_RECIPES.recipeBuilder(rl("nyarlathoteps_tentacle"));
        for (ItemStack stack : items) {
            builder.inputItems(stack);
        }
        TagKey<Item> circuits = TagKey.create(Registries.ITEM,
                new ResourceLocation("gtceu", "circuits/uiv"));
        builder.inputItems(circuits, 64);
        builder.inputFluids(fluids.toArray(new FluidStack[0]));
        builder.outputItems(new ItemStack(output));
        builder.EUt(TENTACLE_EUT).duration(TENTACLE_DURATION);
        // 0.3.284 研究门：样本 = 夸克管道（原来是组装模块），数据介质 = data_module
        builder.stationResearch(b -> b
                .researchStack(researchStack)
                .dataStack(dataStack)
                .EUt(TENTACLE_RESEARCH_EUT)
                .CWUt(TENTACLE_RESEARCH_CWUT, TENTACLE_RESEARCH_TOTAL_CWU));

        GTRecipe recipe = builder.buildRawRecipe();
        for (GTRecipeBuilder.ResearchRecipeEntry entry : builder.researchRecipeEntries()) {
            GTRecipeTypes.ASSEMBLY_LINE_RECIPES.addDataStickEntry(entry.researchId(), recipe);
            injectStationResearch("奈亚主机", rl("nyarlathoteps_tentacle_research_station"), entry);
        }
        GTLModular.LOGGER.info("[gtlmodular] 奈亚主机：本体组装线配方已注入（EUt {} / {}t，"
                        + "研究样本={}（夸克管道），研究 {} CWU/t，总 {} CWU，研究 EUt {}）",
                TENTACLE_EUT, TENTACLE_DURATION, TENTACLE_RESEARCH_STACK_ID,
                TENTACLE_RESEARCH_CWUT, TENTACLE_RESEARCH_TOTAL_CWU, TENTACLE_RESEARCH_EUT);
        return recipe;
    }

    // ------------------------------------------------------------------ 0.3.270 研究/计算件

    /**
     * GWCA 空组件：装配线 + 研究门。
     *
     * <p>GTO 原方（{@code AssemblyLineA.class:111}）：
     * {@code gtocore:graviton_computer_casing}×1 + {@code gtocore:atomic_archives}×4 +
     * {@code #gtceu:circuits/uev}×4 + {@code gtceu:uev_fluid_regulator}×1 +
     * {@code gtceu:enderium_large_fluid_pipe}×2 + {@code gtceu:network_switch}×4 + 2000mB
     * {@code gtocore:gelid_cryotheum}，
     * 323t / VA[11]，研究样本 {@code gtocore:nich_empty_component}（1800t / VA[10] / 1024 CWU/t）。
     *
     * <p>替代：引力计算机外壳 → {@code gtceu:advanced_computer_casing}；原子档案 →
     * {@code gtlmodular:neural_matrix}；冰凝胶 → {@code gtceu:liquid_helium}（整合包无 GTO 冰凝胶）。
     */
    private static GTRecipe buildGwcaEmptyComponent() {
        ItemStack output = stack("gtlmodular:gwca_empty_component", 1);
        ItemStack researchStack = stack("gtlmodular:nich_empty_component", 1);
        ItemStack dataStack = stack("gtceu:data_module", 1);
        if (output.isEmpty() || researchStack.isEmpty() || dataStack.isEmpty()) {
            GTLModular.LOGGER.warn("[gtlmodular] 奈亚之触：GWCA空组件/研究样本/数据介质缺失 ⇒ 跳过 GWCA 空组件配方");
            return null;
        }
        List<ItemStack> items = inputStacks("GWCA空组件", new String[][]{
                {"gtceu:advanced_computer_casing", "1"},
                {"gtlmodular:neural_matrix", "4"},
                {"gtceu:uev_fluid_regulator", "1"},
                {"gtceu:enderium_large_fluid_pipe", "2"},
                {"gtceu:network_switch", "4"},
        });
        if (items == null) {
            return null;
        }
        FluidStack cryotheum = fluid("gtceu:liquid_helium", 2000);
        if (cryotheum == null) {
            GTLModular.LOGGER.warn("[gtlmodular] 奈亚之触：流体 gtceu:liquid_helium 不存在 ⇒ 跳过 GWCA 空组件配方");
            return null;
        }

        GTRecipeBuilder builder = GTRecipeTypes.ASSEMBLY_LINE_RECIPES.recipeBuilder(rl("gwca_empty_component"));
        for (ItemStack stack : items) {
            builder.inputItems(stack);
        }
        builder.inputItems(CIRCUITS_UEV, 4);
        builder.inputFluids(cryotheum);
        builder.outputItems(output);
        builder.EUt(GWCA_EMPTY_EUT).duration(GWCA_EMPTY_DURATION);
        builder.stationResearch(b -> b
                .researchStack(researchStack)
                .dataStack(dataStack)
                .EUt(GWCA_RESEARCH_EUT)
                .CWUt(GWCA_RESEARCH_CWUT, GWCA_RESEARCH_TOTAL_CWU));

        GTRecipe recipe = builder.buildRawRecipe();
        for (GTRecipeBuilder.ResearchRecipeEntry entry : builder.researchRecipeEntries()) {
            GTRecipeTypes.ASSEMBLY_LINE_RECIPES.addDataStickEntry(entry.researchId(), recipe);
            GTLModular.LOGGER.info("[gtlmodular] 奈亚之触：GWCA空组件研究条目已登记 researchId={}", entry.researchId());
            injectStationResearch("GWCA空组件", rl("gwca_empty_component_research_station"), entry);
        }
        return recipe;
    }

    /**
     * 里德堡旋量集群：精密装配机 + 无菌净室 + 不消耗氙。
     *
     * <p>GTO 原方（{@code PrecisionAssembler.class:74}）：低温接口 + 旋转透明面 + 奇异芯片 +
     * X 射线波导 + 不消耗 {@code gtceu:xenon} 10mB + 量子点 10 / 赛隆 144 / 液氢 10，560t，
     * 净室。整合包里这四件正好都有 {@code kubejs:} 同名等价物（GTO 料的既有搬运版）。
     */
    private static GTRecipe buildRydbergSpinorialAssembly() {
        ItemStack output = stack("gtlmodular:rydberg_spinorial_assembly", 1);
        if (output.isEmpty()) {
            return null;
        }
        List<ItemStack> items = inputStacks("里德堡旋量集群", new String[][]{
                {"kubejs:cryogenic_interface", "1"},
                {"kubejs:rotating_transparent_surface", "1"},
                {"kubejs:exotic_chip", "1"},
                {"kubejs:x_ray_waveguide", "1"},
        });
        if (items == null) {
            return null;
        }
        List<FluidStack> fluids = inputFluidStacks("里德堡旋量集群", new String[][]{
                {"gtceu:quantum_dots", "10"},
                {"gtceu:zylon", "144"},
                {"gtceu:liquid_hydrogen", "10"},
        });
        if (fluids == null) {
            return null;
        }
        FluidStack xenon = fluid("gtceu:xenon", 10);
        if (xenon == null) {
            GTLModular.LOGGER.warn("[gtlmodular] 奈亚之触：流体 gtceu:xenon 不存在 ⇒ 跳过里德堡旋量集群配方");
            return null;
        }

        GTRecipeBuilder builder = GTLRecipeTypes.PRECISION_ASSEMBLER_RECIPES
                .recipeBuilder(rl("rydberg_spinorial_assembly"));
        for (ItemStack stack : items) {
            builder.inputItems(stack);
        }
        builder.notConsumableFluid(xenon);
        builder.inputFluids(fluids.toArray(new FluidStack[0]));
        builder.outputItems(output);
        builder.EUt(RYDBERG_EUT).duration(RYDBERG_DURATION);
        builder.cleanroom(CleanroomType.CLEANROOM);
        return builder.buildRawRecipe();
    }

    /**
     * 相对论旋量存储器：组装模块（{@code gtceu:assembler_module}），SEPMTier 4。
     *
     * <p>GTO 原方（{@code AssemblerModule.class:50}）：中子反射板×2 + 里德堡旋量集群 +
     * 带电轻子陷阱晶体×4 + 传说合金单线×2 + UHV 场发生器 + UIV 传感器 + 聚变线圈 +
     * UU 物质 10000 / 惰性气体 2000 / 宇宙计算混合物 1000 / 中子素 1296，800t，VA[UXV]。
     */
    private static GTRecipe buildRelativisticSpinorialMemorySystem() {
        ItemStack output = stack("gtlmodular:relativistic_spinorial_memory_system", 1);
        if (output.isEmpty()) {
            return null;
        }
        List<ItemStack> items = inputStacks("相对论旋量存储器", new String[][]{
                {"gtceu:neutron_reflector", "2"},
                {"gtlmodular:rydberg_spinorial_assembly", "1"},
                {"kubejs:charged_lepton_trap_crystal", "4"},
                {"gtceu:legendarium_single_wire", "2"},
                {"gtceu:uhv_field_generator", "1"},
                {"gtceu:uiv_sensor", "1"},
                {"gtceu:fusion_coil", "1"},
        });
        if (items == null) {
            return null;
        }
        List<FluidStack> fluids = inputFluidStacks("相对论旋量存储器", new String[][]{
                {"gtceu:uu_matter", "10000"},
                {"gtceu:noble_gas", "2000"},
                {"gtceu:cosmic_computing_mixture", "1000"},
                {"gtceu:neutronium", "1296"},
        });
        if (fluids == null) {
            return null;
        }

        GTRecipeBuilder builder = GTLRecipeTypes.ASSEMBLER_MODULE_RECIPES
                .recipeBuilder(rl("relativistic_spinorial_memory_system"));
        for (ItemStack stack : items) {
            builder.inputItems(stack);
        }
        builder.inputFluids(fluids.toArray(new FluidStack[0]));
        builder.outputItems(output);
        builder.EUt(RELATIVISTIC_EUT).duration(RELATIVISTIC_DURATION);
        builder.addData("SEPMTier", 4);
        return builder.buildRawRecipe();
    }

    /**
     * 黑曜石矩阵：组装模块（{@code gtceu:assembler_module}），SEPMTier 4。
     *
     * <p>GTO 原方（{@code AssemblerModule.class:62}）极其夸张：超因果印刷电路板×8、
     * 超因果处理核心×16、暗物质、原物质、HUI 五号电路、稳定虫洞发生器、相对论旋量存储器、
     * 核钟、硅岩塔兰致密板×32、富勒烯聚合物细管×64、黑曜石/哭泣黑曜石各 65536、
     * GTO 四种黑曜石方块各 65536；哈德罗斯 1152 / 宇宙计算混合物 16000 / 超突变活体焊锡 32000，
     * 1600t，EUt 62914560。
     *
     * <p>替代（整合包无 GTOCore）：暗物质/原物质/HUI 电路/虫洞/核钟/超因果板与核心/富勒烯细管
     * 用 {@code kubejs:} 同名等价物；GTO 的四色黑曜石方块无对应 → <b>本配方不含</b>（日志写明）；
     * 硅岩塔兰无致密板生成 → 同材料普通板×32；哈德罗斯 → 中子素。
     */
    private static GTRecipe buildObsidianMatrix() {
        ItemStack output = stack("gtlmodular:obsidian_matrix", 1);
        if (output.isEmpty()) {
            return null;
        }
        List<ItemStack> items = inputStacks("黑曜石矩阵", new String[][]{
                {"kubejs:supracausal_printed_circuit_board", "8"},
                {"kubejs:supracausal_processing_core", "16"},
                {"kubejs:dark_matter", "1"},
                {"kubejs:proto_matter", "1"},
                {"kubejs:hui_circuit_5", "1"},
                {"kubejs:stabilized_wormhole_generator", "1"},
                {"gtlmodular:relativistic_spinorial_memory_system", "1"},
                {"kubejs:nuclear_clock", "1"},
                {"gtceu:naquadriatictaranium_plate", "32"},
                {"kubejs:fullerene_polymer_matrix_fine_tubing", "64"},
                {"minecraft:obsidian", "65536"},
                {"minecraft:crying_obsidian", "65536"},
        });
        if (items == null) {
            return null;
        }
        List<FluidStack> fluids = inputFluidStacks("黑曜石矩阵", new String[][]{
                {"gtceu:neutronium", "1152"},
                {"gtceu:cosmic_computing_mixture", "16000"},
                {"gtceu:super_mutated_living_solder", "32000"},
        });
        if (fluids == null) {
            return null;
        }
        GTLModular.LOGGER.info("[gtlmodular] 奈亚之触：黑曜石矩阵按『四色黑曜石方块缺失』裁剪"
                + "（GTO 的 shining/ender/infused/reinforced obsidian 各 65536 未纳入）");

        GTRecipeBuilder builder = GTLRecipeTypes.ASSEMBLER_MODULE_RECIPES
                .recipeBuilder(rl("obsidian_matrix"));
        for (ItemStack stack : items) {
            builder.inputItems(stack);
        }
        builder.inputFluids(fluids.toArray(new FluidStack[0]));
        builder.outputItems(output);
        builder.EUt(OBSIDIAN_MATRIX_EUT).duration(OBSIDIAN_MATRIX_DURATION);
        builder.addData("SEPMTier", 4);
        return builder.buildRawRecipe();
    }

    // ------------------------------------------------------------------ 0.3.271 集控核心

    /** 部件装配线配方表（gtlcore 注册在 {@code gtceu} 命名空间下，与 ComponentAssemblerMachines 同源）。 */
    private static final ResourceLocation COMPONENT_ASSEMBLY_LINE_TYPE_ID =
            new ResourceLocation("gtceu", "component_assembly_line");

    /** 部件装配配方的外壳等级键（与 {@code ComponentAssemblerMachine.RECIPE_TIER_KEY} 一致）。 */
    private static final String CATIER_KEY = "CATier";

    /** 研究数据介质（GTO 原文的 dataStack）。 */
    private static final String DATA_MODULE_ID = "gtceu:data_module";

    /**
     * 每档集控核心的配方参数（GTO {@code ComponentRecipes.integrated_control_core} 的数值）。
     *
     * <p><b>为什么是一张字符串表，而不是 {@code Material} + TagPrefix</b>：GTO 用
     * {@code inputItems(TagPrefix.ring, material, ...)} 生成物品，但本整合包的材料前缀覆盖与 GTO
     * 并不一致（例：{@code americium} 有 fine wire 却没有 ring，{@code HastelloyX78} 的注册名是
     * {@code hastelloyx_78}）。表里存的是 2026-11-01 用启动日志 registrate 条目清单<b>逐条核对存在</b>
     * 的注册名；构建前任一 id 取不到，整条配方跳过并打日志，不会静默换成别的材料。
     *
     * <p>字段顺序：等级 / 名字 / 显示名 / 环[m0] / 细线[m1] / 单线[m2] / 单缆[m2] / 箔[m6] / 管道 /
     * 五个流体（m0、m3、m4、m5、m6）/ 电路 tag / 机器前缀 / 计算组件 / 计算单元 / 流体倍数 /
     * 产出 id / 研究样本 id。
     */
    private static final TierSpec[] CONTROL_CORE_TIERS = {
            // GTValues.UV = 8
            // ★ 2026-11-02 晚（玩家：「UV 的集控核心目前做不出来，需要改研究」）两处修正，均照 GTO 原数值：
            //   ① 计算单元：GTO 的 UV 档是 gtceu:data_module（不是 neural_matrix！）——
            //      旧表误填 neural_matrix，而本包 neural_matrix 的配方要 UHV 电路 ⇒ UV 档永远凑不齐；
            //   ② 研究样本：GTO 原样是「UV 集控核心自身」（1_x_gtocore_uv_integrated_control_core），
            //      本包没有任务奖励给样本 ⇒ 改用研究链里**UV 前就能做**、且全包只有本模组注册的
            //      gtlmodular:super_cerebrum（超级大脑），避免与其它模组的研究样本撞 researchId。
            new TierSpec(8, "uv", "UV",
                    "gtceu:tritanium_ring", "gtceu:fine_americium_wire",
                    "gtceu:yttrium_barium_cuprate_single_wire", "gtceu:yttrium_barium_cuprate_single_cable",
                    "gtceu:naquadria_foil", "gtceu:naquadah_large_fluid_pipe",
                    "gtceu:tritanium", "gtceu:soldering_alloy", "gtceu:lubricant",
                    "gtceu:styrene_butadiene_rubber", "gtceu:naquadria",
                    "gtceu:circuits/uv", "uv",
                    "gtceu:hpca_advanced_computation_component", "gtceu:data_module",
                    1, "gtlmodular:integrated_control_core_uv", "gtlmodular:super_cerebrum"),
            // GTValues.UHV = 9
            new TierSpec(9, "uhv", "UHV",
                    "gtceu:orichalcum_ring", "gtceu:fine_abyssalalloy_wire",
                    "gtceu:europium_single_wire", "gtceu:europium_single_cable",
                    "gtceu:styrene_butadiene_rubber_foil", "gtceu:neutronium_normal_fluid_pipe",
                    "gtceu:orichalcum", "gtceu:ruthenium_trinium_americium_neutronate",
                    "gtceu:mutated_living_solder", "gtceu:lubricant", "gtceu:styrene_butadiene_rubber",
                    "gtceu:circuits/uhv", "uhv",
                    "gtceu:hpca_advanced_computation_component", "gtlmodular:neural_matrix",
                    2, "gtlmodular:integrated_control_core_uhv", "gtlmodular:integrated_control_core_uv"),
            // GTValues.UEV = 10
            // ★ 2026-11-02 晚：计算单元按 GTO 原值改回 neural_matrix（GTO UEV = nich_computing_components ×2
            //   + neural_matrix ×1；旧表误填 obsidian_matrix）。
            new TierSpec(10, "uev", "UEV",
                    "gtceu:hastelloyx_78_ring", "gtceu:fine_titansteel_wire",
                    "gtceu:mithril_single_wire", "gtceu:mithril_single_cable",
                    "gtceu:styrene_butadiene_rubber_foil", "gtceu:enderium_normal_fluid_pipe",
                    "gtceu:hastelloyx_78", "gtceu:dalisenite", "gtceu:mutated_living_solder",
                    "gtceu:lubricant", "gtceu:styrene_butadiene_rubber",
                    "gtceu:circuits/uev", "uev",
                    "gtlmodular:nich_computing_component", "gtlmodular:neural_matrix",
                    4, "gtlmodular:integrated_control_core_uev", "gtlmodular:integrated_control_core_uhv"),
            // GTValues.UIV = 11
            new TierSpec(11, "uiv", "UIV",
                    "gtceu:infuscolium_ring", "gtceu:fine_adamantine_wire",
                    "gtceu:neutronium_single_wire", "gtceu:neutronium_single_cable",
                    "gtceu:styrene_butadiene_rubber_foil", "gtceu:fullerene_polymer_matrix_pulp_normal_fluid_pipe",
                    "gtceu:infuscolium", "gtceu:echoite", "gtceu:super_mutated_living_solder",
                    "gtceu:lubricant", "gtceu:styrene_butadiene_rubber",
                    "gtceu:circuits/uiv", "uiv",
                    "gtlmodular:nich_computing_component", "gtlmodular:obsidian_matrix",
                    8, "gtlmodular:integrated_control_core_uiv", "gtlmodular:integrated_control_core_uev"),
            // GTValues.UXV = 12
            new TierSpec(12, "uxv", "UXV",
                    "gtceu:hastelloyk_243_ring", "gtceu:fine_naquadriatictaranium_wire",
                    "gtceu:taranium_single_wire", "gtceu:taranium_single_cable",
                    "gtceu:styrene_butadiene_rubber_foil", "gtceu:heavy_quark_degenerate_matter_normal_fluid_pipe",
                    "gtceu:hastelloyk_243", "gtceu:legendarium", "gtceu:super_mutated_living_solder",
                    "gtceu:lubricant", "gtceu:styrene_butadiene_rubber",
                    "gtceu:circuits/uxv", "uxv",
                    "gtlmodular:gwca_computing_component", "gtlmodular:obsidian_matrix",
                    16, "gtlmodular:integrated_control_core_uxv", "gtlmodular:integrated_control_core_uiv"),
            // GTValues.OpV = 13（GTO 的 AwakenedDraconium 在本包是 gtceu:draconiumawakened）
            new TierSpec(13, "opv", "OpV",
                    "gtceu:vibramantium_ring", "gtceu:fine_starmetal_wire",
                    "gtceu:crystalmatrix_single_wire", "gtceu:crystalmatrix_single_cable",
                    "gtceu:styrene_butadiene_rubber_foil", "gtceu:heavy_quark_degenerate_matter_large_fluid_pipe",
                    "gtceu:vibramantium", "gtceu:draconiumawakened", "gtceu:super_mutated_living_solder",
                    "gtceu:lubricant", "gtceu:styrene_butadiene_rubber",
                    "gtceu:circuits/opv", "opv",
                    "gtlmodular:gwca_computing_component", "gtlmodular:obsidian_matrix",
                    32, "gtlmodular:integrated_control_core_opv", "gtlmodular:integrated_control_core_uxv"),
    };

    /** 一档集控核心的配方参数（字段含义见 {@link #CONTROL_CORE_TIERS} 的注释）。 */
    private static final class TierSpec {

        final int tier;
        final String name;
        final String label;
        final String ring;
        final String fineWire;
        final String singleWire;
        final String singleCable;
        final String foil;
        final String pipe;
        final String fluid0;
        final String fluid3;
        final String fluid4;
        final String fluid5;
        final String fluid6;
        final String circuitTag;
        final String machinePrefix;
        final String computationComponent;
        final String computationUnit;
        final int fluidMul;
        final String outputId;
        final String researchStackId;

        TierSpec(int tier, String name, String label,
                 String ring, String fineWire, String singleWire, String singleCable,
                 String foil, String pipe,
                 String fluid0, String fluid3, String fluid4, String fluid5, String fluid6,
                 String circuitTag, String machinePrefix,
                 String computationComponent, String computationUnit,
                 int fluidMul, String outputId, String researchStackId) {
            this.tier = tier;
            this.name = name;
            this.label = label;
            this.ring = ring;
            this.fineWire = fineWire;
            this.singleWire = singleWire;
            this.singleCable = singleCable;
            this.foil = foil;
            this.pipe = pipe;
            this.fluid0 = fluid0;
            this.fluid3 = fluid3;
            this.fluid4 = fluid4;
            this.fluid5 = fluid5;
            this.fluid6 = fluid6;
            this.circuitTag = circuitTag;
            this.machinePrefix = machinePrefix;
            this.computationComponent = computationComponent;
            this.computationUnit = computationUnit;
            this.fluidMul = fluidMul;
            this.outputId = outputId;
            this.researchStackId = researchStackId;
        }

        String fieldGenerator() {
            return "gtceu:" + machinePrefix + "_field_generator";
        }

        String sensor() {
            return "gtceu:" + machinePrefix + "_sensor";
        }

        String pump() {
            return "gtceu:" + machinePrefix + "_electric_pump";
        }
    }

    /**
     * 注入 6 档集控核心的 A/B 两条配方，外加 MAX 档的一条（0.3.280）；返回成功条数。
     *
     * <p>MAX 档（GTValues.MAX=14）<b>不走 {@link #CONTROL_CORE_TIERS} 的通用循环</b>：GTO 的 MAX 是
     * {@code ComponentRecipes.componentMAX()} 里手写的一条（circuitMeta 不同、材料整套不同），
     * 见 {@link #buildMaxIntegratedControlCore()}。其中本整合包缺失的材料按队长口径
     * 「不替代、不占位」逐条跳过，集中打一条告警。
     */
    private static int installControlCores() {
        GTRecipeType componentAssembly = GTRegistries.RECIPE_TYPES.get(COMPONENT_ASSEMBLY_LINE_TYPE_ID);
        if (componentAssembly == null) {
            GTLModular.LOGGER.warn("[gtlmodular] 集控核心：配方表 {} 未注册 ⇒ 配方 B（部件装配）全部跳过",
                    COMPONENT_ASSEMBLY_LINE_TYPE_ID);
        }
        int ok = 0;
        for (TierSpec spec : CONTROL_CORE_TIERS) {
            ok += installSafe(GTRecipeTypes.ASSEMBLY_LINE_RECIPES,
                    () -> buildIntegratedControlCore(spec), "集控核心A-" + spec.label);
            if (componentAssembly != null) {
                ok += installSafe(componentAssembly,
                        () -> buildIntegratedControlCoreBatch(spec), "集控核心B-" + spec.label);
            }
        }
        // 0.3.280 MAX 档：GTO 的手工配方（不走 TierSpec 循环）
        if (componentAssembly != null) {
            ok += installSafe(componentAssembly, TentacleRecipes::buildMaxIntegratedControlCore,
                    "集控核心MAX（B）");
        }
        GTLModular.LOGGER.info("[gtlmodular] 集控核心：已注入 {} 条（{} 档 ×2 条 + MAX 1 条）",
                ok, CONTROL_CORE_TIERS.length);
        return ok;
    }

    /**
     * 配方 A · 装配线（1 个/次）：GTO {@code ComponentRecipes.integrated_control_core} 的数值照抄。
     *
     * <p>环 m0×16 + 本档电路×2 + 场发生器 + 传感器 + 泵 + 计算组件×2 + 计算单元 +
     * 箔 m6×64 + 细线 m1×64 + 单线 m2×8 + 管道×4，
     * 流体 m3 2000×mult / m4 4000×mult / m5 1000×mult，1200t，EUt=VA[tier]；
     * 研究门：样本=上一档集控核心（UV 用 UV 档湿件电路）、数据介质=data_module、
     * CWUt=(1&lt;&lt;(tier-3))×1.5、EUt=VA[tier-1]（GTCEu 的 CWUt(int) 自动把总 CWU 记成 cwut×4000）。
     */
    private static GTRecipe buildIntegratedControlCore(TierSpec spec) {
        ItemStack output = stack(spec.outputId, 1);
        ItemStack researchStack = stack(spec.researchStackId, 1);
        ItemStack dataStack = stack(DATA_MODULE_ID, 1);
        if (output.isEmpty() || researchStack.isEmpty() || dataStack.isEmpty()) {
            GTLModular.LOGGER.warn("[gtlmodular] 集控核心{}（A）：产出/研究样本/数据介质缺失"
                    + "（{}/{}/{}）⇒ 跳过该配方", spec.label, spec.outputId, spec.researchStackId, DATA_MODULE_ID);
            return null;
        }
        String label = "集控核心" + spec.label + "（A）";
        List<ItemStack> items = inputStacks(label, new String[][]{
                {spec.ring, "16"},
                {spec.fieldGenerator(), "1"},
                {spec.sensor(), "1"},
                {spec.pump(), "1"},
                {spec.computationComponent, "2"},
                {spec.computationUnit, "1"},
                {spec.foil, "64"},
                {spec.fineWire, "64"},
                {spec.singleWire, "8"},
                {spec.pipe, "4"},
        });
        if (items == null) {
            return null;
        }
        List<FluidStack> fluids = inputFluidStacks(label, new String[][]{
                {spec.fluid3, Integer.toString(2000 * spec.fluidMul)},
                {spec.fluid4, Integer.toString(4000 * spec.fluidMul)},
                {spec.fluid5, Integer.toString(1000 * spec.fluidMul)},
        });
        if (fluids == null) {
            return null;
        }

        GTRecipeBuilder builder = GTRecipeTypes.ASSEMBLY_LINE_RECIPES
                .recipeBuilder(rl("integrated_control_core_" + spec.name));
        for (ItemStack stack : items) {
            builder.inputItems(stack);
        }
        TagKey<Item> circuit = TagKey.create(Registries.ITEM,
                new ResourceLocation(spec.circuitTag));
        builder.inputItems(circuit, 2);
        builder.inputFluids(fluids.toArray(new FluidStack[0]));
        builder.outputItems(output);
        builder.EUt(GTValues.VA[spec.tier]).duration(1200);

        int cwut = (int) ((1 << (spec.tier - 3)) * 1.5);
        builder.stationResearch(b -> b
                .researchStack(researchStack)
                .dataStack(dataStack)
                .EUt(GTValues.VA[spec.tier - 1])
                .CWUt(cwut));

        GTRecipe recipe = builder.buildRawRecipe();
        for (GTRecipeBuilder.ResearchRecipeEntry entry : builder.researchRecipeEntries()) {
            GTRecipeTypes.ASSEMBLY_LINE_RECIPES.addDataStickEntry(entry.researchId(), recipe);
            injectStationResearch("集控核心" + spec.label,
                    rl("integrated_control_core_" + spec.name + "_research_station"), entry);
        }
        GTLModular.LOGGER.info("[gtlmodular] 集控核心{}（A）：装配线配方已注入"
                        + "（EUt {} / 1200t，研究 {} CWU/t，研究样本 {}）",
                spec.label, GTValues.VA[spec.tier], cwut, spec.researchStackId);
        return recipe;
    }

    /**
     * 配方 B · 部件装配线（16 个/次）：GTO {@code COMPONENT_ASSEMBLY_RECIPES} 的数值照抄。
     *
     * <p>circuitMeta(9) + 场发生器×12 + 传感器×12 + 泵×12 + 计算单元×12 + 计算组件×24 +
     * 电路×48 + 单缆 m2×96，流体 m0 96000 / m3 24000×mult / m5 12000×mult / m6 24000，
     * 2400t，EUt=VA[tier]，数据 CATier=tier（部件组装机按它限档）。
     */
    private static GTRecipe buildIntegratedControlCoreBatch(TierSpec spec) {
        ItemStack output = stack(spec.outputId, 16);
        if (output.isEmpty()) {
            GTLModular.LOGGER.warn("[gtlmodular] 集控核心{}（B）：产出 {} 缺失 ⇒ 跳过该配方",
                    spec.label, spec.outputId);
            return null;
        }
        String label = "集控核心" + spec.label + "（B）";
        List<ItemStack> items = inputStacks(label, new String[][]{
                {spec.fieldGenerator(), "12"},
                {spec.sensor(), "12"},
                {spec.pump(), "12"},
                {spec.computationUnit, "12"},
                {spec.computationComponent, "24"},
                {spec.singleCable, "96"},
        });
        if (items == null) {
            return null;
        }
        List<FluidStack> fluids = inputFluidStacks(label, new String[][]{
                {spec.fluid0, "96000"},
                {spec.fluid3, Integer.toString(24000 * spec.fluidMul)},
                {spec.fluid5, Integer.toString(12000 * spec.fluidMul)},
                {spec.fluid6, "24000"},
        });
        if (fluids == null) {
            return null;
        }

        GTRecipeType componentAssembly = GTRegistries.RECIPE_TYPES.get(COMPONENT_ASSEMBLY_LINE_TYPE_ID);
        if (componentAssembly == null) {
            return null;
        }
        GTRecipeBuilder builder = componentAssembly
                .recipeBuilder(rl("integrated_control_core_" + spec.name));
        builder.circuitMeta(9);
        for (ItemStack stack : items) {
            builder.inputItems(stack);
        }
        TagKey<Item> circuit = TagKey.create(Registries.ITEM,
                new ResourceLocation(spec.circuitTag));
        builder.inputItems(circuit, 48);
        builder.inputFluids(fluids.toArray(new FluidStack[0]));
        builder.outputItems(output);
        builder.EUt(GTValues.VA[spec.tier]).duration(2400);
        builder.addData(CATIER_KEY, spec.tier);
        return builder.buildRawRecipe();
    }


    // ------------------------------------------------------------------ 0.3.280 MAX 集控核心

    /** MAX 集控核心产出（物品早在 0.3.271 就注册了，这里补的是配方）。 */
    private static final String MAX_CORE_OUTPUT_ID = "gtlmodular:integrated_control_core_max";

    /** MAX 档的电机器件：GTO {@code GTOItems.MAX_EMITTER}（本包由 gtlcore 注册）。 */
    private static final String MAX_CORE_EMITTER_ID = "gtlcore:max_emitter";

    /** MAX 档的六重线缆：GTO {@code cableGtHex × CosmicNeutronium}（本包 hex 前缀的线，不是缆）。 */
    private static final String MAX_CORE_HEX_WIRE_ID = "gtceu:cosmicneutronium_hex_wire";

    /** MAX 档电路 tag：GTO {@code CustomTags.MAX_CIRCUITS}（本包由 kubejs:supracausal_mainframe 填充）。 */
    private static final String MAX_CORE_CIRCUIT_TAG = "gtceu:circuits/max";

    /** GTO 原方 EUt {@code 0x78000000} = 2,013,265,920。 */
    private static final long MAX_CORE_EUT = 2_013_265_920L;
    private static final int MAX_CORE_DURATION = 12000;

    /** 部件装配外壳等级：GTO {@code addData(COMPONENT_ASSEMBLY_CASING_TIER, 14)}。 */
    private static final int MAX_CORE_CASING_TIER = 14;

    /**
     * 配方 B · MAX 档集控核心（部件装配线，64 个/次）。
     *
     * <p>数值出处：GTOCore 26.10.1 反编译 {@code _tentacle_dec/com/gtocore/data/recipe/misc/
     * ComponentRecipes.java:100} 的 {@code componentMAX()}：
     * {@code circuitMeta(9)} + 闭时曲线制导单元×48 + 黑曜石矩阵×96 + 混沌无尽合金双板×288 +
     * MAX 电路×96 + MAX 发射器×96 + 核星×192 + 宇宙细线×3072 + 宇宙中子素六重线×96，
     * 流体 超突变活体焊锡 1769472 / 时空连续体 3538944 / 黑矮星 110592 / 白矮星 110592 /
     * Shirabon 110592 / Infinity 27648，12000t，EUt 2013265920，CATier=14，<b>无研究门</b>。
     *
     * <p><b>缺件口径（队长口径：不替代、不占位）</b>：GTO 的
     * {@code CLOSED_TIMELIKE_CURVE_GUIDANCE_UNIT}（闭时曲线制导单元）、{@code OBSIDIAN_MATRIX}、
     * {@code ChaosInfinityAlloy}（混沌无尽合金）、{@code SpacetimeContinuum}（时空连续体）本包没有 ⇒
     * 不加、不占位，只进跳过清单；启动日志集中打一条 WARN 列出全部被跳过的项。
     * 核星按候选 {@code gtceu:nuclear_star → gtlcore:nuclear_star → kubejs:nuclear_star}
     * 取本包实际存在的那个（本包是最后一个，与 KubeJS 的 max_* 系列一致）。
     * 宇宙细线按队长口径「宇宙=永恒」优先取 {@code gtceu:eternity_fine_wire}，缺失则跳过并告警。
     *
     * <p>两个硬锚点（MAX 发射器、MAX 六重线）任一缺失时整条不做 —— 否则会出现
     * 「只交流体/电路就能出 MAX 核心」的半空配方，比不做还糟。
     */
    private static GTRecipe buildMaxIntegratedControlCore() {
        final String label = "集控核心MAX（B）";
        ItemStack output = stack(MAX_CORE_OUTPUT_ID, 64);
        if (output.isEmpty()) {
            GTLModular.LOGGER.warn("[gtlmodular] {}：产出 {} 未注册 ⇒ 跳过该配方", label, MAX_CORE_OUTPUT_ID);
            return null;
        }
        ItemStack emitter = stack(MAX_CORE_EMITTER_ID, 96);
        ItemStack hexWire = stack(MAX_CORE_HEX_WIRE_ID, 96);
        if (emitter.isEmpty() || hexWire.isEmpty()) {
            GTLModular.LOGGER.warn("[gtlmodular] {}：MAX 档锚点缺失（{} / {}）⇒ 跳过该配方",
                    label, MAX_CORE_EMITTER_ID, MAX_CORE_HEX_WIRE_ID);
            return null;
        }

        List<ItemStack> items = new ArrayList<>();
        List<FluidStack> fluids = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        items.add(emitter);
        items.add(hexWire);

        // 核星 ×192（GTO NUCLEAR_STAR；本包实际注册在 kubejs 命名空间）
        String star = addResolvedItem(items, skipped, "核星", 192,
                "gtceu:nuclear_star", "gtlcore:nuclear_star", "kubejs:nuclear_star");
        // 宇宙细线 ×3072：GTO 是 wireFine Cosmic；本包 eternity 无 fine 形态，
        // 用本包 KubeJS MAX 系列同一写法 gtceu:fine_cosmic_wire（cosmic 细线）
        String fineWire = addResolvedItem(items, skipped, "宇宙细线（GTO wireFine Cosmic）", 3072,
                "gtceu:fine_cosmic_wire", "gtceu:eternity_fine_wire");
        // 黑曜石矩阵 ×96（GTO OBSIDIAN_MATRIX，本包已实现为 gtlmodular:obsidian_matrix）
        addResolvedItem(items, skipped, "黑曜石矩阵", 96, "gtlmodular:obsidian_matrix");
        // 混沌无尽合金双板 ×288：本包无 ChaosInfinityAlloy ⇒ 不查表、不加，只进跳过清单
        skipped.add("混沌无尽合金双板 plateDouble ChaosInfinityAlloy ×288（本包无该材料）");

        addResolvedFluid(fluids, skipped, "超突变活体焊锡", 1_769_472, "gtceu:super_mutated_living_solder");
        // 时空连续体 ×3538944：本包无 SpacetimeContinuum ⇒ 不加
        skipped.add("时空连续体 SpacetimeContinuum ×3538944（本包无该流体）");
        addResolvedFluid(fluids, skipped, "黑矮星物质", 110_592,
                "gtceu:black_dwarf_mtter", "gtceu:black_dwarf_matter");
        addResolvedFluid(fluids, skipped, "白矮星物质", 110_592,
                "gtceu:white_dwarf_mtter", "gtceu:white_dwarf_matter");
        addResolvedFluid(fluids, skipped, "Shirabon", 110_592, "gtceu:shirabon");
        addResolvedFluid(fluids, skipped, "Infinity", 27_648, "gtceu:infinity");

        TagKey<Item> circuit = TagKey.create(Registries.ITEM, new ResourceLocation(MAX_CORE_CIRCUIT_TAG));
        if (Ingredient.of(circuit).getItems().length == 0) {
            GTLModular.LOGGER.warn("[gtlmodular] {}：电路 tag #{} 当前为空"
                    + "（本包应由 kubejs:supracausal_mainframe 填充）—— 配方会登记但机器匹配不到电路",
                    label, MAX_CORE_CIRCUIT_TAG);
        }
        if (!skipped.isEmpty()) {
            GTLModular.LOGGER.warn("[gtlmodular] {}：按『缺料不替代、不占位』跳过 {} 项 —— {}",
                    label, skipped.size(), String.join("；", skipped));
        }

        GTRecipeType componentAssembly = GTRegistries.RECIPE_TYPES.get(COMPONENT_ASSEMBLY_LINE_TYPE_ID);
        if (componentAssembly == null) {
            GTLModular.LOGGER.warn("[gtlmodular] {}：配方表 {} 未注册 ⇒ 跳过该配方",
                    label, COMPONENT_ASSEMBLY_LINE_TYPE_ID);
            return null;
        }
        GTRecipeBuilder builder = componentAssembly
                .recipeBuilder(rl("integrated_control_core_max"));
        builder.circuitMeta(9);
        for (ItemStack stack : items) {
            builder.inputItems(stack);
        }
        builder.inputItems(circuit, 96);
        builder.inputFluids(fluids.toArray(new FluidStack[0]));
        builder.outputItems(output);
        builder.EUt(MAX_CORE_EUT).duration(MAX_CORE_DURATION);
        builder.addData(CATIER_KEY, MAX_CORE_CASING_TIER);

        GTLModular.LOGGER.info("[gtlmodular] {}：配方已注入（EUt {} / {}t，CATier {}，核星 {}，"
                        + "永恒细线 {}，物品 {} 种 + 流体 {} 种）",
                label, MAX_CORE_EUT, MAX_CORE_DURATION, MAX_CORE_CASING_TIER,
                star == null ? "缺失" : star, fineWire == null ? "缺失" : fineWire,
                items.size(), fluids.size());
        return builder.buildRawRecipe();
    }


    // ------------------------------------------------------------------ 注入

    /**
     * 运行期补一条「研究站配方」（研究门能不能解开，全靠它）。
     *
     * <p><b>为什么必须自己补</b>：GTCEu 的 {@code stationResearch(...)} 只把 {@code ResearchCondition}
     * 塞进装配线配方；真正让玩家把研究写进数据介质的那条配方（样本 + 空白数据介质 →
     * 带 {@code assembly_line_research} NBT 的数据介质），是
     * {@code GTRecipeType.onRecipeBuild → ResearchManager.createDefaultResearchRecipe}
     * 在 <b>{@code GTRecipeBuilder.save()} 路径</b>里生成的 —— 数据包 datagen 和 KubeJS 都走 save()，
     * 而本类的运行期注入走 {@code buildRawRecipe()}，<b>不会触发这个钩子</b>。
     * 少了它，研究站没有可跑的配方，装配线的研究门永远解不开（配方在 JEI 里看得见、机器却永远缺研究数据）。
     *
     * <p>这里按 {@code createDefaultResearchRecipe} 的同一构造重放：
     * 输入 = 研究样本（1）+ 空白数据介质（1）；输出 = 同一数据介质 + NBT
     * {@code assembly_line_research{research_id, research_type}}；EUt / CWU/t / 总 CWU 取研究条目里的原值。
     *
     * @param label 日志用档名
     * @param id    研究站配方的 id（每档/每条研究门唯一）
     * @param entry 主配方 {@code stationResearch} 生成的研究条目
     * @return 成功条数（0/1）
     */
    private static int injectStationResearch(String label, ResourceLocation id,
                                             GTRecipeBuilder.ResearchRecipeEntry entry) {
        try {
            ItemStack researchStack = entry.researchStack();
            ItemStack dataStack = entry.dataStack();
            if (researchStack.isEmpty() || dataStack.isEmpty()) {
                GTLModular.LOGGER.warn("[gtlmodular] {}：研究站配方的样本/数据介质为空 ⇒ 跳过（研究门将无法解开）", label);
                return 0;
            }
            CompoundTag research = new CompoundTag();
            research.putString("research_id", entry.researchId());
            research.putString("research_type",
                    GTRecipeTypes.ASSEMBLY_LINE_RECIPES.registryName.toString());
            ItemStack encoded = dataStack.copy();
            encoded.setCount(1);
            encoded.getOrCreateTag().put("assembly_line_research", research);

            // ★ 2026-11-15：**顺序必须是「数据模块 → 研究样本」**，不能反过来。
            //   取证：GTCEu ResearchManager（javap 反汇编）生成研究站配方时是
            //     .inputItems(<dataStack>.getItem())  ← 先：数据模块
            //     .inputItems(<researchStack>)        ← 后：研究样本
            //   研究站界面「中心格」取第一个输入 ⇒ 数据模块在中心、样本在右上（JEI 里的正常形态）。
            //   原先写成「样本 → 数据」会让样本跑到中心，看起来就是「反了」。
            GTRecipeBuilder builder = GTRecipeTypes.RESEARCH_STATION_RECIPES.recipeBuilder(id)
                    .inputItems(dataStack)
                    .inputItems(researchStack.getItem())
                    .outputItems(encoded)
                    .EUt(entry.EUt())
                    .CWUt(entry.CWUt())
                    .totalCWU(entry.duration());
            int added = inject(GTRecipeTypes.RESEARCH_STATION_RECIPES, builder.buildRawRecipe());
            GTLModular.LOGGER.info("[gtlmodular] {}：研究站配方已注入 researchId={}（{} CWU/t，总 {} CWU）",
                    label, entry.researchId(), entry.CWUt(), entry.duration());
            return added;
        } catch (Throwable t) {
            GTLModular.LOGGER.warn("[gtlmodular] {}：补研究站配方失败（研究门可能无法解开）：{}",
                    label, t.toString());
            return 0;
        }
    }

    /** 一条配方同时写 lookup 与 byType；返回成功条数（0/1）。 */
    private static int inject(GTRecipeType type, GTRecipe recipe) {
        if (recipe == null || type == null) {
            return 0;
        }
        try {
            GTRecipeLookup lookup = type.getLookup();
            boolean added = lookup.addRecipe(recipe);
            if (!added) {
                // 同 id 重复添加（例如换存档 / 二次 ServerStartedEvent）会返回 false，不算失败
                GTLModular.LOGGER.debug("[gtlmodular] 奈亚之触：{} 已存在于 lookup（跳过重复登记）", recipe.id);
            }
            if (byTypeMap != null) {
                byTypeMap.computeIfAbsent(type, k -> new HashMap<>()).put(recipe.id, recipe);
            } else {
                GTLModular.LOGGER.warn("[gtlmodular] 奈亚之触：byType 不可用 ⇒ {} 只进 lookup（机器能跑，JEI 可能不显示）",
                        recipe.id);
            }
            return added ? 1 : 0;
        } catch (Throwable t) {
            GTLModular.LOGGER.warn("[gtlmodular] 奈亚之触：登记 {} 失败（已忽略）：{}", recipe.id, t.toString());
            return 0;
        }
    }

    /**
     * 构建 + 注入一条，构建期异常也单独兜底 —— 一条配方炸掉不影响同一批的其它条
     * （验收口径：注入失败只打日志，绝不崩服）。
     */
    private static int installSafe(GTRecipeType type, java.util.function.Supplier<GTRecipe> factory, String label) {
        try {
            return inject(type, factory.get());
        } catch (Throwable t) {
            GTLModular.LOGGER.warn("[gtlmodular] 奈亚之触：构建 {} 配方失败（已忽略，不影响启动）：{}",
                    label, t.toString());
            return 0;
        }
    }

    // ------------------------------------------------------------------ 取值小工具

    private static ResourceLocation rl(String path) {
        return new ResourceLocation(GTLModular.MOD_ID, path);
    }

    /** 按注册名取物品；不存在返回 {@code null}（本模组/整合包缺件时静默跳过）。 */
    private static Item item(String id) {
        return ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));
    }

    /** 按注册名取物品栈；不存在返回 {@link ItemStack#EMPTY}。 */
    private static ItemStack stack(String id, int count) {
        Item item = item(id);
        return item == null ? ItemStack.EMPTY : new ItemStack(item, count);
    }

    /** 按注册名取流体栈；不存在返回 {@code null}。 */
    private static FluidStack fluid(String id, int amount) {
        Fluid fluid = ForgeRegistries.FLUIDS.getValue(new ResourceLocation(id));
        return fluid == null ? null : FluidStack.create(fluid, amount);
    }

    /** 一批「注册名 × 数量」物品输入；任一缺失返回 {@code null}（调用方跳过整条配方）。 */
    private static List<ItemStack> inputStacks(String recipe, String[][] spec) {
        List<ItemStack> out = new ArrayList<>();
        for (String[] entry : spec) {
            ItemStack stack = stack(entry[0], Integer.parseInt(entry[1]));
            if (stack.isEmpty()) {
                GTLModular.LOGGER.warn("[gtlmodular] 奈亚之触：{} 的输入 {} 不存在 ⇒ 跳过该配方", recipe, entry[0]);
                return null;
            }
            out.add(stack);
        }
        return out;
    }

    /** 一批「注册名 × 数量」流体输入；任一缺失返回 {@code null}。 */
    private static List<FluidStack> inputFluidStacks(String recipe, String[][] spec) {
        List<FluidStack> out = new ArrayList<>();
        for (String[] entry : spec) {
            FluidStack stack = fluid(entry[0], Integer.parseInt(entry[1]));
            if (stack == null) {
                GTLModular.LOGGER.warn("[gtlmodular] 奈亚之触：{} 的流体 {} 不存在 ⇒ 跳过该配方", recipe, entry[0]);
                return null;
            }
            out.add(stack);
        }
        return out;
    }

    /**
     * 按候选顺序取第一个<b>已注册</b>的物品加进输入列表；全部缺失则记入 {@code skipped}
     * （不替代、不占位，调用方最后统一打告警）。
     *
     * @return 实际使用的注册名；全部缺失返回 {@code null}
     */
    private static String addResolvedItem(List<ItemStack> items, List<String> skipped,
                                          String note, int count, String... candidates) {
        for (String candidate : candidates) {
            ItemStack stack = stack(candidate, count);
            if (!stack.isEmpty()) {
                items.add(stack);
                return candidate;
            }
        }
        skipped.add(note + " ×" + count + "（" + String.join("/", candidates) + " 均未注册）");
        return null;
    }

    /** 流体版的 {@link #addResolvedItem}。 */
    private static String addResolvedFluid(List<FluidStack> fluids, List<String> skipped,
                                           String note, int amount, String... candidates) {
        for (String candidate : candidates) {
            FluidStack stack = fluid(candidate, amount);
            if (stack != null) {
                fluids.add(stack);
                return candidate;
            }
        }
        skipped.add(note + " ×" + amount + "（" + String.join("/", candidates) + " 均未注册）");
        return null;
    }

    // ------------------------------------------------------------------ byType 反射

    /**
     * 取 {@code RecipeManager.byType}：生产（SRG）字段名 {@code f_44007_}、开发（official 映射）{@code recipes}；
     * 两次都失败就放弃（只打一次 WARN），配方仍会进 lookup。
     */
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
                    GTLModular.LOGGER.warn("[gtlmodular] 奈亚之触：找不到 RecipeManager.byType 字段"
                            + "（f_44007_ / recipes）⇒ 运行期配方只会进 lookup，JEI 可能不显示");
                    return null;
                }
            }
            return (Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>>) byTypeField.get(manager);
        } catch (Throwable t) {
            byTypeFailed = true;
            GTLModular.LOGGER.warn("[gtlmodular] 奈亚之触：读取 RecipeManager.byType 失败（已停用 byType 注入）：{}",
                    t.toString());
            return null;
        }
    }
}
