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

/**
 * NAC（纳米芯片组装复合体）的 <b>14 张专用配方表</b> —— NH 的 {@code RecipeMaps.nanochip*} 在 1.20.1 的对应物。
 *
 * <p><b>契约（P1 冻结，P3 直接引用这些常量名）</b>：
 * {@link #CONVERSION} / {@link #ASSEMBLY_MATRIX} / {@link #SMD_PROCESSOR} / {@link #BOARD_PROCESSOR} /
 * {@link #ETCHING_ARRAY} / {@link #CUTTING_CHAMBER} / {@link #WIRE_TRACER} / {@link #SUPERCONDUCTOR_SPLITTER} /
 * {@link #OPTICAL_ORGANIZER} / {@link #EXOTIC_ORGANIZER} / {@link #COSMIC_ORGANIZER} /
 * {@link #SUPRACAUSAL_ORGANIZER} / {@link #BIOLOGICAL_COORDINATOR} / {@link #ENCASEMENT_WRAPPER}。
 *
 * <p>★ 2026-09-19（队长：「给主机注册本模组自己的类型（显示「纳米芯片组装复合体」/「纳米芯片换算」）」）：
 * 除上面这 14 张「模块加工表」与 {@link #SPLITTER_SCHEDULE}（空表、JEI 隐藏）之外，本类还多了
 * <b>1 张主机表</b> {@link #ASSEMBLY_COMPLEX}（{@code gtlmodular:nanochip_assembly_complex}）。
 * ⇒ 本文里凡写「14 张表」的地方，指的仍是<b>模块加工表</b>那 14 张，不含主机这张表。
 *
 * <p>★★ 2026-10-14（队长：「<b>剪断 NAC 对「电路组装机」的代理关系</b>」）★★ ——
 * 上面那段里"它没有自己的数据包配方 / 靠 GTCEu「代理配方表」复用电路组装机表那批对象"的写法
 * 是<b>改前状态，现已不成立</b>：本表已剪断代理，成为<b>完全独立的普通配方类型</b>（与其余 14 张表同一写法：本类的
 * {@link #type}，不带任何 {@code proxies}），配方全部来自本模组自己的 7 条 JSON
 * （{@code data/gtlmodular/recipes/nanochip/assembly_complex/*.json}，其 {@code "type"} 就是本表 id）。
 * 「为什么剪 / 为什么安全 / 旧样板受不受影响 / 怎么回退」逐条见 {@link #ASSEMBLY_COMPLEX} 字段注释顶部的
 * 2026-10-14 段。
 *
 * <p>★ 2026-09-18（队长指示）：<b>新增 3 张</b>（奇异 / 寰宇 / 超因果优化组织器）。
 * 队长的原话是「他们结构都跟光学优化组织器一样，只是不同的框架…里面的配方也要有改变，这需要你自己注册」，
 * ⇒ 三台是光学那台的<b>同构镜像</b>：同样是「1 CC 进 → 2 CC 出」的一进一出模块表，
 * 只是各自服务本族的处理器核心 / RAM 晶片（族标记见 {@link com.gtlmodular.common.data.nanochip.NanochipCircuitComponents}）。
 * NH 侧没有这三台（原版 NAC 只有 11 个模块）⇒ 三张表是<b>本移植的扩展</b>，不在 NH 取证范围内。
 *
 * <h3>一、类型 id 全部落在本模组命名空间</h3>
 * 14 个 id 依次是 {@code gtlmodular:nanochip_conversion}、{@code gtlmodular:nanochip_assembly_matrix}、
 * {@code gtlmodular:nanochip_smd_processor}、{@code gtlmodular:nanochip_board_processor}、
 * {@code gtlmodular:nanochip_etching_array}、{@code gtlmodular:nanochip_cutting_chamber}、
 * {@code gtlmodular:nanochip_wire_tracer}、{@code gtlmodular:nanochip_superconductor_splitter}、
 * {@code gtlmodular:nanochip_optical_organizer}、{@code gtlmodular:nanochip_exotic_organizer}、
 * {@code gtlmodular:nanochip_cosmic_organizer}、{@code gtlmodular:nanochip_supracausal_organizer}、
 * {@code gtlmodular:nanochip_biological_coordinator}、{@code gtlmodular:nanochip_encasement_wrapper}。
 *
 * <h3>二、★ 为什么不能用 GTCEu 自带的 {@code GTRecipeTypes.register(...)} 助手</h3>
 * {@code com.gregtechceu.gtceu.common.data.GTRecipeTypes.register(String,String,RecipeType...)} 内部第一句是
 * {@code new GTRecipeType(GTCEu.id(name), group, proxies)} —— <b>id 被硬编码成 {@code gtceu:} 命名空间</b>
 * （javap -c 实测，见 {@code GTRecipeTypes.register} 偏移 5：{@code invokestatic GTCEu.id}）。
 * 兄弟 addon {@code gtlcore}（{@code GTLRecipeTypes}）正是走这条路，所以它的表 id 全是 {@code gtceu:xxx}。
 * 我们的契约要求 {@code gtlmodular:}，因此<b>照抄 gtmthings 的写法</b>
 * （{@code com.hepdd.gtmthings.data.GTMTRecipeTypes.register}，javap -c 实测）：自己 {@code new GTRecipeType(...)}
 * 并把同一个 {@code ResourceLocation} 注册进<b>三个</b>注册表（顺序不可省）：
 * <ol>
 *   <li>{@code BuiltInRegistries.RECIPE_TYPE}（{@code Registry.RECIPE_TYPE}）—— vanilla RecipeManager 建 byType 索引要用；</li>
 *   <li>{@code BuiltInRegistries.RECIPE_SERIALIZER} —— 数据包 JSON 的 {@code "type"} 字段靠它解析；</li>
 *   <li>{@code GTRegistries.RECIPE_TYPES} —— GTCEu 自己的 RL 注册表，{@code GTRecipe} 的 codec 用它解析 {@code "type"}。</li>
 * </ol>
 *
 * <h3>三、★★ 注册时机（唯一正确的窗口，别改）</h3>
 * {@code GTRecipeTypes.init()} 的字节码（javap -c）末尾是：
 * <pre>
 *   107: ModLoader.get()
 *   110: new GTCEuAPI$RegisterEvent(GTRegistries.RECIPE_TYPES, GTRecipeType.class)
 *   122: ModLoader.postEvent(...)          ← ★ addon 的注册窗口
 *   125: GTRegistries.RECIPE_TYPES.freeze() ← ★ 之后一律 IllegalStateException("registry ... has been frozen")
 * </pre>
 * 而 {@code GTRecipeTypes.init()} 由 {@code CommonProxy} 的 {@code modConstruct} 经
 * {@code FMLConstructModEvent.enqueueWork(CommonProxy::init)} 调用（javap -v 的 BootstrapMethods #3 实证）
 * ⇒ 它在所有 mod 构造完成<b>之后</b>的并行队列里跑 ⇒ <b>在 @Mod 构造里挂
 * {@code modBus.addGenericListener(GTRecipeType.class, ...)} 一定来得及</b>
 * （本项目的机器注册用的就是同一个机制，已成事实）。
 * <p>{@code GTRegistries.RECIPE_TYPES.register} 对「已冻结」和「key 已存在」都<b>抛异常</b>
 * （{@code GTRegistry.register} 字节码：frozen → IllegalStateException，containKey → IllegalStateException）
 * ⇒ {@link #init()} 必须是幂等的（下面用 {@code registered} 闸门保证）。
 *
 * <h3>四、JEI 分类：GTCEu 1.4.4 <b>没有</b> {@code GTRecipeCategory} 这个类</h3>
 * 全 jar 类名检索 {@code GTRecipeCategory} = <b>0 命中</b>；{@code GTRegistries} 里也<b>没有</b>
 * {@code RECIPE_CATEGORIES}（只有 RECIPE_TYPES / RECIPE_CAPABILITIES / RECIPE_CONDITIONS …）。
 * JEI 分类是 GTCEu 的 JEI 插件<b>自动</b>为每个「注册进 {@code BuiltInRegistries.RECIPE_TYPE} 的
 * {@code GTRecipeType}」建的：{@code GTJEIPlugin.registerCategories} 遍历
 * {@code BuiltInRegistries.RECIPE_TYPE} → {@code instanceof GTRecipeType} → {@code getRecipeUI().isXEIVisible()}
 * → {@code new GTRecipeTypeCategory(jeiHelpers, type)}（javap -c 实证，偏移 159/188/203/227）。
 * {@code GTRecipeTypeUI} 构造器把 {@code XEIVisible} 默认置为 {@code true}（javap -c：偏移 70-73 {@code iconst_1}）。
 * <p>⇒ <b>「JEI 分类 id」就是配方类型 id 本身</b>（{@code gtlmodular:nanochip_*}），分类标题取
 * {@code registryName.toLanguageKey()} = {@code gtlmodular.nanochip_*}（{@code GTRecipeTypeCategory.getTitle}
 * 字节码：{@code Component.translatable(recipeType.registryName.toLanguageKey())}）
 * ⇒ 所以本类<b>不需要</b>（也无法）单独建 JEI 分类对象，只需保证上面三处注册 + lang 键存在。
 * 图标由 {@code GTRecipeType.setIconSupplier} 提供（{@code GTRecipeTypeCategory} 构造器对 null 有兜底，
 * 但我们仍给真实 CC 物品，避免 JEI 里显示空白图标）。
 *
 * <h3>五、EU 档位常量：GTCEu 1.4.4 <b>没有</b> {@code TierEU}</h3>
 * 对 {@code gtceu-1.20.1-1.4.4.jar} / {@code gtlcore} / {@code gtladditions} / {@code gtmthings} 四个 jar
 * 做类名字符串扫描，{@code TierEU} <b>0 命中</b> ⇒ NH 的 {@code TierEU.RECIPE_*} 在本包没有同名对应物。
 * 组装矩阵 23 条里有 5 条写的是档位常量（{@code RECIPE_LuV/UEV/UIV/UMV/MAX}），
 * 本次按 <b>NH jar 的实数</b>固化（证据链见 {@link #RECIPE_LuV} 的注释），
 * 剩下的 18 条本来就是字面量（9600 / 153600 / 614400 / 2457600 / 4800000，逐条来自
 * {@code _census/p1_input_map.json} 的 {@code assemblyMatrix[].eut}）。
 *
 * <p>★ 2026-09-18（P2 模块加工层，裁决 ADJ-4）：**补 {@link #RECIPE_UV} = 491520 与
 * {@link #RECIPE_UHV} = 1966080**。P2 第一次用到 NH 的 {@code LowTier}（←{@code RECIPE_UV}，15 条）
 * 与 {@code MediumTier}（←{@code RECIPE_UHV}，17 条），两个值由**与上面五条完全相同的证据链**推出
 * （{@code ModuleRecipeInfo} 的 static 块给出「档位名 → TierEU 字段」，
 * {@code GTValues.V[]} 给出字面量，{@code lambda$static$0} 给出 {@code v*30/32}）；
 * 细节与「无独立字面量旁证」的如实标注见这两个常量各自的注释。
 */
public final class NanochipRecipeTypes {

    private NanochipRecipeTypes() {
    }

    /** 配方表分组（GTCEu 的 {@code GTRecipeType.group} 只是个公开字符串字段；11 张表都是多方块模块表）。 */
    public static final String GROUP_MULTIBLOCK = "multiblock";

    /**
     * 已建好的表（键 = 类型 id，顺序 = 下面的声明顺序）：
     * <b>14 张模块加工表</b> + ★主机表 {@link #ASSEMBLY_COMPLEX} + 隐藏空表 {@link #SPLITTER_SCHEDULE}。
     *
     * <p>★ 必须声明在 14 个 {@code GTRecipeType} 字段<b>之前</b>：Java 按文本顺序跑静态初始化，
     * 而下面每个字段的初始值都调用 {@link #type(String, int, int, int, int)}，那里要往本表登记。
     */
    private static final Map<ResourceLocation, GTRecipeType> INDEX = new LinkedHashMap<>();

    /** 幂等闸门（{@code GTRegistry.register} 遇到 key 已存在会抛异常，所以注册只能跑一次）。 */
    private static boolean registered;

    // ------------------------------------------------------------------ 表（构造在这里完成，注册在 init()）

    /**
     * 控制室换算：NH {@code RecipeMaps.nanochipConversionRecipes}，99 条 `真实物品×1 → CC 假栈×1`，100 t，EU/t=0。
     *
     * <p>形状证据：{@code registerConversionRecipes} 字节码（{@code _gtnh/javap_nac/notes_recipes.md} §3.1）
     * —— 遍历 {@code CircuitComponent.VALUES}、跳过 {@code isProcessed==true}（77 个）⇒ 99 条；
     * {@code stdBuilder().itemInputs(realComponent.get()).itemOutputs(getFakeStack(1)).duration(100).eut(0)}。
     */
    public static final GTRecipeType CONVERSION =
            type("nanochip_conversion", 1, 1, 0, 0)
                    .setProgressBar(GuiTextures.PROGRESS_BAR_CIRCUIT, ProgressTexture.FillDirection.LEFT_TO_RIGHT)
                    .setSound(GTSoundEntries.COMPUTATION)
                    .setIconSupplier(machineIcon("nanochip_assembly_complex"));

    /**
     * 组装矩阵：NH {@code RecipeMaps.nanochipAssemblyMatrixRecipes}，23 条（输入全是 CC，输出 1 个 CC，带 tier 6..12）。
     *
     * <p>尺寸上限取实测最大值再留一档余量：条目最多 14 个物品输入（第 10 条）、4 种流体输入（第 17/18/19/22/23 条）。
     */
    public static final GTRecipeType ASSEMBLY_MATRIX =
            type("nanochip_assembly_matrix", 16, 4, 4, 4)
                    .setProgressBar(GuiTextures.PROGRESS_BAR_CIRCUIT_ASSEMBLER, ProgressTexture.FillDirection.LEFT_TO_RIGHT)
                    .setSound(GTSoundEntries.ASSEMBLER)
                    .setIconSupplier(machineIcon("nanochip_assembly_matrix"));

    /** 元件预处理装置（NH {@code nanochipSMDProcessorRecipes}，10 条：1 CC 进 1 CC 出）。 */
    public static final GTRecipeType SMD_PROCESSOR =
            type("nanochip_smd_processor", 1, 1, 0, 0)
                    .setProgressBar(GuiTextures.PROGRESS_BAR_CIRCUIT_ASSEMBLER, ProgressTexture.FillDirection.LEFT_TO_RIGHT)
                    .setSound(GTSoundEntries.ASSEMBLER)
                    .setIconSupplier(machineIcon("nanochip_smd_processor"));

    /** 全板浸没装置（NH {@code nanochipBoardProcessorRecipes}，8 条：1 CC + 流体进 → 1 CC + 流体出）。 */
    public static final GTRecipeType BOARD_PROCESSOR =
            type("nanochip_board_processor", 1, 1, 1, 1)
                    .setProgressBar(GuiTextures.PROGRESS_BAR_BATH, ProgressTexture.FillDirection.LEFT_TO_RIGHT)
                    .setSound(GTSoundEntries.BATH)
                    .setIconSupplier(machineIcon("nanochip_board_processor"));

    /** 超高能蚀刻阵列（NH {@code nanochipEtchingArray}，3 条：1 CC 进 1 CC 出，无流体）。 */
    public static final GTRecipeType ETCHING_ARRAY =
            type("nanochip_etching_array", 1, 1, 0, 0)
                    .setProgressBar(GuiTextures.PROGRESS_BAR_ARC_FURNACE, ProgressTexture.FillDirection.LEFT_TO_RIGHT)
                    .setSound(GTSoundEntries.ARC)
                    .setIconSupplier(machineIcon("nanochip_etching_array"));

    /** 纳米精度切割室（NH {@code nanochipCuttingChamber}，30 条：1 CC + 1 流体 → 1 CC）。 */
    public static final GTRecipeType CUTTING_CHAMBER =
            type("nanochip_cutting_chamber", 1, 1, 1, 0)
                    .setProgressBar(GuiTextures.PROGRESS_BAR_SLICE, ProgressTexture.FillDirection.LEFT_TO_RIGHT)
                    .setSound(GTSoundEntries.CUT)
                    .setIconSupplier(machineIcon("nanochip_cutting_chamber"));

    /** 纳米精度线材描绘器（NH {@code nanochipWireTracer}，9 条：1 CC 进 1 CC 出）。 */
    public static final GTRecipeType WIRE_TRACER =
            type("nanochip_wire_tracer", 1, 1, 0, 0)
                    .setProgressBar(GuiTextures.PROGRESS_BAR_WIREMILL, ProgressTexture.FillDirection.LEFT_TO_RIGHT)
                    .setSound(GTSoundEntries.CUT)
                    .setIconSupplier(machineIcon("nanochip_wire_tracer"));

    /** 超导绞线分离器（NH {@code nanochipSuperconductorSplitter}，7 条：1 CC 进 1 CC 出）。 */
    public static final GTRecipeType SUPERCONDUCTOR_SPLITTER =
            type("nanochip_superconductor_splitter", 1, 1, 0, 0)
                    .setProgressBar(GuiTextures.PROGRESS_BAR_WIREMILL, ProgressTexture.FillDirection.LEFT_TO_RIGHT)
                    .setSound(GTSoundEntries.CUT)
                    .setIconSupplier(machineIcon("nanochip_superconductor_splitter"));

    /** 光学优化整理器（NH {@code nanochipOpticalOrganizer}，2 条：1 CC 进 1 CC 出）。 */
    public static final GTRecipeType OPTICAL_ORGANIZER =
            type("nanochip_optical_organizer", 1, 1, 0, 0)
                    .setProgressBar(GuiTextures.PROGRESS_BAR_CIRCUIT_ASSEMBLER, ProgressTexture.FillDirection.LEFT_TO_RIGHT)
                    .setSound(GTSoundEntries.COMPUTATION)
                    .setIconSupplier(machineIcon("nanochip_optical_organizer"));

    /**
     * 奇异优化组织器（{@code nanochip_exotic_organizer}）—— 2026-09-18 队长指示新增。
     *
     * <p>形状与 {@link #OPTICAL_ORGANIZER} <b>同构</b>（1 CC 进 1 CC 出、无流体），
     * 区别只在「服务哪一族」：光学那台管光学芯片，这台管<b>奇异族</b>
     * （奇异处理核心 / 奇异 RAM 晶片；族标记来自 {@code NanochipCircuitComponents} 的 {@code "EXOTIC"}）。
     */
    public static final GTRecipeType EXOTIC_ORGANIZER =
            type("nanochip_exotic_organizer", 1, 1, 0, 0)
                    .setProgressBar(GuiTextures.PROGRESS_BAR_CIRCUIT_ASSEMBLER, ProgressTexture.FillDirection.LEFT_TO_RIGHT)
                    .setSound(GTSoundEntries.COMPUTATION)
                    .setIconSupplier(machineIcon("nanochip_exotic_organizer"));

    /**
     * 寰宇优化组织器（{@code nanochip_cosmic_organizer}）—— 2026-09-18 队长指示新增。
     *
     * <p>同 {@link #EXOTIC_ORGANIZER}，服务的是<b>寰宇族</b>
     * （寰宇处理核心 / 寰宇 RAM 晶片；族标记 {@code "COSMIC"}）。
     */
    public static final GTRecipeType COSMIC_ORGANIZER =
            type("nanochip_cosmic_organizer", 1, 1, 0, 0)
                    .setProgressBar(GuiTextures.PROGRESS_BAR_CIRCUIT_ASSEMBLER, ProgressTexture.FillDirection.LEFT_TO_RIGHT)
                    .setSound(GTSoundEntries.COMPUTATION)
                    .setIconSupplier(machineIcon("nanochip_cosmic_organizer"));

    /**
     * 超因果优化组织器（{@code nanochip_supracausal_organizer}）—— 2026-09-18 队长指示新增。
     *
     * <p>同 {@link #EXOTIC_ORGANIZER}，服务的是<b>超因果族</b>
     * （超因果处理核心 / 超因果 RAM 晶片；族标记 {@code "SUPRACAUSAL"}）。
     * <p>★ 2026-09-21 队长两道令之后的现状：该族在校准条上归
     * {@code NanochipCalibration.Family.TEMPORAL} —— 显示名「<b>超因果电路</b>」，
     * 且原「特殊」（specials）那一段已并入它（队长：「第5个是合并成一条」）；
     * 阈值按队长令设为 <b>1%/5%/20%</b>、效果走 SPECIAL 那套曲线（{@code specialStyleEffects()}）。
     * ⇒ 本台的「满校准翻倍」<b>可达</b>（但原版「同时只能存在一个校准」不变 ⇒ 四台组织器里
     * 同一时刻最多只有一台能翻倍）。见 {@code NanochipModuleKind#organizerFamily()}。
     */
    public static final GTRecipeType SUPRACAUSAL_ORGANIZER =
            type("nanochip_supracausal_organizer", 1, 1, 0, 0)
                    .setProgressBar(GuiTextures.PROGRESS_BAR_CIRCUIT_ASSEMBLER, ProgressTexture.FillDirection.LEFT_TO_RIGHT)
                    .setSound(GTSoundEntries.COMPUTATION)
                    .setIconSupplier(machineIcon("nanochip_supracausal_organizer"));

    /** 加速生物协调器（NH {@code nanochipBiologicalCoordinator}，4 条：1 CC + 1 流体 → 1 CC）。 */
    public static final GTRecipeType BIOLOGICAL_COORDINATOR =
            type("nanochip_biological_coordinator", 1, 1, 1, 0)
                    .setProgressBar(GuiTextures.PROGRESS_BAR_BATH, ProgressTexture.FillDirection.LEFT_TO_RIGHT)
                    .setSound(GTSoundEntries.BATH)
                    .setIconSupplier(machineIcon("nanochip_biological_coordinator"));

    /** 纳米封装包装器（NH {@code nanochipEncasementWrapper}，13 条，最多 4 个物品输入 → 1 CC）。 */
    public static final GTRecipeType ENCASEMENT_WRAPPER =
            type("nanochip_encasement_wrapper", 4, 1, 0, 0)
                    .setProgressBar(GuiTextures.PROGRESS_BAR_PACKER, ProgressTexture.FillDirection.LEFT_TO_RIGHT)
                    .setSound(GTSoundEntries.ASSEMBLER)
                    .setIconSupplier(machineIcon("nanochip_encasement_wrapper"));

    // ------------------------------------------------------------------ ★ 主机（NAC）自己的表

    /**
     * ★★★ 2026-10-14（队长：「<b>剪断 NAC 对「电路组装机」的代理关系</b>，让 NAC 成为完全独立的配方类型」）★★★
     *
     * <h4>现状（改后）</h4>
     * 本表 = <b>不带任何代理</b>的普通配方类型，注册写法与同文件其余表<b>完全一致</b>：
     * <pre>type("nanochip_assembly_complex", 6, 1, 1, 0)</pre>
     * 尺寸四元数沿用电路组装机表登记过的实数（{@code setMaxIOSize(6, 1, 1, 0)}，javap 实证见本文件
     * {@link #proxiedType} 的注释）：改前 {@code proxiedType} 是<b>现读</b> parent 的这四个值，
     * 现在把它们<b>写成常量</b> ⇒ 本表自身的尺寸口径与改前<b>逐项相同</b>（7 条自有配方最多 6 个物品输入、
     * 0 个流体输入，见 {@code assembly_complex/001_cc_crystal_processor.json} 等 7 个文件）。
     * <p>配方 = <b>本模组自己的 7 条 JSON</b>（{@code data/gtlmodular/recipes/nanochip/assembly_complex/}
     * 的 001/006/011/015/019/023/027，2026-09-22 从组装矩阵表逐条剪来，输入输出/时长/EU 未动），
     * 每条 {@code "type"} = {@code gtlmodular:nanochip_assembly_complex}。
     *
     * <h4>为什么剪</h4>
     * 改前本表是<b>代理子表</b>（{@code proxiedType("nanochip_assembly_complex",
     * GTRecipeTypes.CIRCUIT_ASSEMBLER_RECIPES)}）。上游 GTLCore 的「样板快速上传」会把代理关系<b>摊开</b>
     * ⇒ 从 JEI 的<b>电路组装机</b>页面转送出来的样板会被写成<b>「纳米芯片组装复合体」</b>，队长不要这个行为。
     * 剪断后：从电路组装机页面转送的样板<b>只写电路组装机</b>；上游代理展开里<b>不再出现本表</b>。
     *
     * <h4>为什么安全（机器照样拿得到这 7 条）</h4>
     * <ol>
     *   <li><b>本表有自己的 JSON</b> ⇒ vanilla {@code RecipeManager.byType} 的键只来自「已加载配方的
     *       {@code getType()}」（vanilla {@code apply} 与 KubeJS {@code post} 都是
     *       {@code computeIfAbsent(recipe.getType(), …)}）⇒ byType 里本表<b>天然</b>有一格、装这 7 条
     *       （改前"本表在 byType 里恒为空"的结论只对 2026-09-22 之前的空表成立）；</li>
     *   <li>GTCEu 装载端 {@code RecipeManagerMixin.gtceu$cloneVanillaRecipes}（TAIL 注入）遍历
     *       {@code BuiltInRegistries.RECIPE_TYPE} 里每个 {@code GTRecipeType}，把 vanilla byType 里该类型的
     *       {@code GTRecipe} 灌进它<b>自己的 lookup</b>（{@code lambda$gtceu$cloneVanillaRecipes$2} =
     *       {@code getLookup().addRecipe(recipe)}；旁证转储 {@code _census/P3_接线_方案.md:138-171}）
     *       ⇒ 本表 lookup 有这 7 条；<b>这与 14 张模块表走的是同一条路</b>（它们都没有代理、只看自己的 JSON，
     *       且全部能被搜到），也是队长实测「这 7 条能跑通」的那条路；</li>
     *   <li><b>机器侧一个字没改</b>：{@code NanochipMachines.java:502} 的
     *       {@code .recipeType(NanochipRecipeTypes.ASSEMBLY_COMPLEX)}、{@code NanochipAssemblyComplexMachine}
     *       的 {@code getRecipeTypes()}（下标 0 = 本表）与 {@code getRecipeType()} 全部保持原样；
     *       按类型枚举配方时，{@code level.getRecipeManager().getAllRecipesFor(type)}
     *       读到的也是本表自有配方，因此无需额外的代理别名。</li>
     * </ol>
     *
     * <h4>连带停用的两处（代码全部保留，各一行开关可恢复）</h4>
     * <ul>
     *   <li>{@link NanochipProxyTableByTypeAlias}（服务端往 {@code byType} 塞「本表 → 电路组装机」别名键）：
     *       它是 2026-09-21 为「本表在 byType 里恒为空」打的补丁；本表 2026-09-22 有了自己的 7 条 JSON 之后
     *       它<b>本来就已多余</b> ⇒ 本轮把它的总开关置 {@code false}（早退，服务端 tick / 区块加载事件里
     *       什么都不做）；</li>
     *   <li>{@code GTLModularJeiPlugin.registerAssemblyComplexRecipes}（把电路组装机那批配方手工灌进本表
     *       JEI 分类）：剪断后这些配方<b>本表已经跑不了</b>，还登记进本表分类就是误导；而本表分类的内容
     *       现在由 GTCEu 按 {@code getAllRecipesFor(本表)} 自动登记（= 这 7 条真配方）⇒ 该钩子置 {@code false} 早退。</li>
     * </ul>
     *
     * <h4>旧样板受不受影响（明确回答）</h4>
     * <b>物品与存档格式零改动</b>（不碰任何 {@code @Persisted}、不加存档键、不动 mixin）：
     * <ul>
     *   <li><b>本表类型（{@code gtlmodular:nanochip_assembly_complex}）的样板</b>：类型 id 一个字未动
     *       ⇒ 照旧识别、照旧能跑；</li>
     *   <li><b>改前从 JEI <u>电路组装机</u>页面转送、被当成"本表"用的那批样板</b>（= 队长要消灭的那种）：
     *       本表不再索引电路组装机配方 ⇒ 放到 NAC 上<b>不再被识别、不再能跑</b>。样板本身<b>没有被改也没有被删</b>
     *       （它仍是普通电路组装机样板，拿到真的电路组装机机器上照旧可用）；要在 NAC 上用，从 NAC 自己的
     *       JEI 分类重新上传即可。⇒ <b>这是本次剪断的必要代价，也正是队长要的效果。</b></li>
     * </ul>
     *
     * <h4>怎么回退</h4>
     * 把本字段那一行改回
     * {@code proxiedType("nanochip_assembly_complex", GTRecipeTypes.CIRCUIT_ASSEMBLER_RECIPES)}，
     * 并把 {@link NanochipProxyTableByTypeAlias} 与 {@code GTLModularJeiPlugin} 的两个开关改回 {@code true}
     * （四处逐行 diff 见 {@code _census/剪断代理关系_执行记录.md}）。
     *
     * <hr>
     * <p>⚠️ 以下从 2026-09-19 起的段落是「剪断前」的历史取证，原样保留供追溯
     * —— 其中「没有自己的数据包配方」「靠代理机制复用电路组装机那批对象」「本表在 byType 里恒为空」
     * 等描述<b>已经不再成立</b>（保留是为了留住 2026-09-21 那次根因分析的证据链）。
     *
     * <p>★★ 2026-09-19（队长：「给主机注册本模组自己的类型（显示「纳米芯片组装复合体」/「纳米芯片换算」）」）★★
     * <b>主机（纳米芯片组装复合体）自己的配方表</b> —— id {@code gtlmodular:nanochip_assembly_complex}。
     *
     * <h4>它解决的显示问题（为什么"改显示"必须落到一个真类型上）</h4>
     * 机器界面「机器模式」那一行、JEI 分类标题、Jade/TOP 的模式名、猪咪样板总成的模式页，
     * <b>四处都取</b> {@code Component.translatable(type.registryName.toLanguageKey())}
     * （机器界面那一处：{@code MultiblockDisplayText$Builder.addMachineModeLine} 字节码偏移 13/23/26
     * —— {@code translatable("gtceu.gui.machinemode", translatable(type.registryName.toLanguageKey()))}；
     * JEI 那一处：{@code GTRecipeTypeCategory.getTitle} 偏移 4/7/10）。
     * 改前主机挂的是 {@code GTRecipeTypes.CIRCUIT_ASSEMBLER_RECIPES}（{@code gtceu:circuit_assembler}）
     * ⇒ 四处都显示 GTCEu 那条官方译名<b>「电路组装机」</b>，而 wiki / NH 里 NAC 的配方种类是
     * <b>「纳米芯片组装复合体」</b>。⇒ 给主机一张自己 id 的表，这四个名字自然全对
     * （本表 lang 键 = {@code gtlmodular.nanochip_assembly_complex}）。
     *
     * <h4>为什么不能"只改显示"（覆写 addDisplayText，或只覆写 searchRecipe）</h4>
     * gtlcore 的 {@code MultipleRecipesLogic.lookupRecipeIterator()}（javap -c 偏移 74-99）是
     * {@code machine.getRecipeType().getLookup().getRecipeIterator(holder, this::checkRecipe)}
     * —— <b>直接要 lookup，根本不走 {@code GTRecipeType.searchRecipe()}</b>。
     * 所以本表的 {@code lookup} 必须<b>真的</b>装着那批配方，否则主机一条配方都搜不到（功能归零）。
     *
     * <h4>做法：GTCEu 的「代理配方表」（= 构造器第 3 个可变参数 {@code RecipeType<?>... proxies}）+ 恒等覆写</h4>
     * <ol>
     *   <li>{@code new GTRecipeType(ResourceLocation, String group, RecipeType<?>... proxies)}
     *       （javap -c 偏移 141-201：proxies 只是被建成 {@code proxyRecipes} 里每项一个空 List）；</li>
     *   <li>数据包每次重载，{@code RecipeManagerMixin.gtceu$cloneVanillaRecipes}
     *       （{@code @Inject(method="apply(...)", at=@At("TAIL"))}，javap -v 实证）会遍历
     *       {@code BuiltInRegistries.RECIPE_TYPE}（字节码里的 {@code f_256990_}，与
     *       {@code GTJEIPlugin.registerRecipes} 同一个字段），对每个 GTRecipeType：
     *       <b>①</b> {@code getLookup().removeAllRecipes()}；
     *       <b>②</b> 对 {@code getProxyRecipes()} 每一项：{@code list.clear()}，再把 {@code byType} 里
     *       该代理表的每条配方经 {@code toGTrecipe(id, recipe)} 填进 list；
     *       <b>③</b> 本表在 {@code byType} 里没有条目（没有任何 JSON 声明本类型）⇒ 走"代理表非空"那一支
     *       （偏移 342-381），把 list 里的配方 {@code getLookup().addRecipe(...)} 进<b>本表</b>的 lookup。</li>
     *   <li>★★ <b>2026-09-21 补正（重要）</b>：上面这一步<b>只填 lookup，不会在 vanilla
     *       {@code RecipeManager.byType} 里给我们建条目</b>。0.1.36「主机机器模式完全用不了」的根因就在这
     *       —— 详见下面新增的「★★ 2026-09-21 根因与修法」一节与
     *       {@link NanochipProxyTableByTypeAlias}。</li>
     *   <li>KubeJS 的 {@code GregTechKubeJSPlugin.injectRuntimeRecipes} 是<b>同一套形状的第二处填充点</b>
     *       （偏移 67/880/940 一致）⇒ 原版数据包与 KJS 脚本加的电路组装机配方<b>两条路都进得来</b>。</li>
     *   <li>★ 第 ② 步的 {@code toGTrecipe} 是 {@code invokevirtual}（RecipeManagerMixin 偏移 118、
     *       KJS 偏移 940）⇒ <b>可覆写</b>，见 {@link ProxiedRecipeType}：代理进来的 {@code GTRecipe}
     *       <b>原样返回</b>。默认实现是"原版配方 → GT 配方"的<b>重建</b>
     *       （javap -c {@code GTRecipeType.toGTrecipe}：只搬 {@code getIngredients()} 与
     *       {@code getResultItem()}，然后 {@code GTRecipeBuilder} 默认 EU/t 与时长 + 序列化往返）
     *       ⇒ 若不覆写，几百条电路组装机配方会<b>丢掉电压/时长/流体/概率</b>（= 偷偷改数）。
     *       恒等覆写之后，两张表里装的是<b>同一个 GTRecipe 对象</b>：不复制、不重建、不改任何 JSON。</li>
     * </ol>
     *
     * <h4>副作用说明（如实标注）</h4>
     * 因为进本表的是同一批对象，{@code recipe.getType()} 仍是 {@code gtceu:circuit_assembler}
     * （{@code GTRecipe.recipeType} 是 final，且改它没有意义）。全包读 {@code recipe.getType()} 的地方只有
     * 5 处（javap 实测：gtceu {@code RecipeRunner} 与 gtlcore {@code RecipeRunner} / {@code IParallelLogic}
     * 取 {@code getChanceFunction()}；{@code GTRecipeWidget} 取配方 UI；gtlcore 的 AE2 冲突分析
     * {@code ConflictAnalysisResult}）—— 它们在改前读到的也是同一个值 ⇒ <b>逐项零行为漂移</b>；
     * 尺寸闸门也逐项取代理表实数（见 {@link #proxiedType}），所以"能跑的配方集合"与改前完全一致。
     *
     * <h3>★★ 2026-09-21 根因与修法（队长选 B：名字保留「纳米芯片组装复合体」+ 真能跑）★★</h3>
     *
     * <h4>根因一句话</h4>
     * GTCEu 的代理机制<b>确实</b>会把电路组装机的配方克隆进本表 —— 但<b>只克隆进本表的
     * {@code GTRecipeLookup}，不会在 vanilla {@code RecipeManager.byType} 里给本表建条目</b>；
     * 而主机「吃不吃得到样板」这条链路的判据读的恰恰是 {@code byType}
     * ⇒ 本表在 byType 里恒为空 ⇒ 主机的下标 0 永远识别不出样板类型 ⇒ 该模式<b>完全用不了</b>。
     *
     * <h4>证据 A：装载端两处填充点，最后一跳都只写 lookup（javap -c）</h4>
     * <ol>
     *   <li>数据包侧 {@code com/gregtechceu/gtceu/core/mixins/RecipeManagerMixin.class}
     *       （{@code @Inject(method="apply(...)", at=@At("TAIL"))}，javap -v 实证）：
     *       偏移 47-52 {@code getLookup().removeAllRecipes()}；偏移 122-224 用 {@code f_44007_}
     *       （= byType）里<b>代理表</b>那一格经 {@code toGTrecipe} 重填代理 list；
     *       偏移 230 判 {@code byType.containsKey(本表)}（本表不在 ⇒ 落到偏移 342）；
     *       偏移 342-381 {@code forEach(lambda$gtceu$cloneVanillaRecipes$1)} = 偏移 0-5
     *       <b>{@code gtType.getLookup().addRecipe(recipe)}</b>。全流程<b>没有一次
     *       {@code byType.put(...)}</b>。</li>
     *   <li>KubeJS 侧 {@code GregTechKubeJSPlugin.injectRuntimeRecipes} 同形状：偏移 57-62 清 lookup、
     *       偏移 132-235 按 {@code lambda$…$43}（{@code entry.getValue().getType() == key}）重填代理 list、
     *       偏移 241-332 {@code forEach(lambda$…$46)} = <b>{@code getLookup().addRecipe(recipe)}</b>。</li>
     * </ol>
     * <p>⇒ 队长问的「GTCEu 到底会不会克隆到共享同一原版配方类型的第二张 GT 表」的答案是<b>会</b>；
     * 坏掉的不是 lookup，是 byType。
     *
     * <h4>证据 B：本表在 byType 里不可能有条目</h4>
     * byType 的键只来自「已加载配方的 {@code getType()}」（vanilla {@code apply} 与 KubeJS {@code post}
     * 都是 {@code computeIfAbsent(recipe.getType(), …)}）；而没有任何配方属于本表 ——
     * 本模组 591 条 JSON 里 {@code "type": "gtlmodular:nanochip_assembly_complex"} 出现 <b>0 次</b>，
     * 整合包 {@code kubejs/}（含脚本备份）里搜同一 id 也 <b>0 命中</b>。
     *
     * <h4>证据 C：主机喂料链路读的就是 byType（这是"完全用不了"的直接原因）</h4>
     * 按类型匹配样板时，需要 {@code level.getRecipeManager().getAllRecipesFor(type)}
     * 能枚举该类型的配方；
     * 它扫的是控制器 {@code getRecipeTypes()} 数组（简单模式 = 下标 0 本表 + 下标 1 换算表），
     * 下标 0 恒为 0 条 ⇒ 电路组装机样板识别不出类型 ⇒ {@code recipeTypeOfSlot} 恒 -1 ⇒
     * 自动切模式（{@code findModeWithWaitingStock}：{@code type < 0} 直接 skip，第 896 行）永远选不到下标 0，
     * {@code lockRecipeTypeForSlot} 只能保持当前模式 ⇒ 玩家看到的就是"这个模式找不到/跑不了配方"。
     *
     * <p><b>运行时实证（队长 0.1.36 实机日志）</b>：
     * {@code …\profiles\GTL推荐新手玩版（较难）_versions_GTL\logs\latest.log}
     * <pre>
     * [17:01:35] [gtlmodular] 自动切模式检查: 当前=0/2 状态=IDLE 找到目标=-1 样板=1 类型已知=[] 有料槽=[]
     * [17:22:28] [gtlmodular] 自动切模式检查: 当前=0/2 状态=IDLE 找到目标=-1 样板=3 类型已知=[] 有料槽=[3]
     * </pre>
     * （{@code 当前=0/2} = 正停在<b>下标 0 = 本表</b>；{@code 样板=1/3} = 仓里有样板；
     * {@code 类型已知=[]} = 一条都没识别出类型；{@code 有料槽=[3]} = 料已推进来。
     * 同一份日志里换算样板（下标 1）能识别出来 —— 因为换算表<b>有</b>自己的 JSON、byType 有条目。）
     *
     * <h4>修法（最小、稳、不动父表）</h4>
     * 服务端每次配方重载后，往 {@code RecipeManager.byType} 给本表补一个<b>只读别名键</b>：
     * {@code byType.put(本表, byType.get(电路组装机表))}（值 = 父表那张内层 Map 的<b>同一个实例</b>）；
     * 幂等、自愈（扛得住 KubeJS 晚于装载端整张替换 byType）、只在服务端装
     * （客户端故意不装：本模组 JEI 插件已手工登记 141 条，客户端再装会让 JEI 分类出现重复条目）。
     * 实现与全部取证细节见 {@link NanochipProxyTableByTypeAlias}。
     * <p>本表的 id / lang 键一个字未动（{@code gtlmodular:nanochip_assembly_complex} /
     * {@code gtlmodular.nanochip_assembly_complex} = 「纳米芯片组装复合体」），
     * 父表（电路组装机）的配方与数据<b>一个字节未改</b>，{@code toGTrecipe} 的恒等语义保持不变。
     */
    public static final GTRecipeType ASSEMBLY_COMPLEX =
            // ★ 2026-10-14（代理剪断）：本行改前是
            //   proxiedType("nanochip_assembly_complex", GTRecipeTypes.CIRCUIT_ASSEMBLER_RECIPES)
            //   ⇒ 现在照抄本文件其余表的写法：**不带任何代理**的普通类型注册。
            //   尺寸四元数 6/1/1/0 = 改前 proxiedType 从电路组装机表现读到的同一组实数（口径逐项相同）。
            type("nanochip_assembly_complex", 6, 1, 1, 0)
                    .setProgressBar(GuiTextures.PROGRESS_BAR_CIRCUIT_ASSEMBLER, ProgressTexture.FillDirection.LEFT_TO_RIGHT)
                    .setSound(GTSoundEntries.ASSEMBLER)
                    .setIconSupplier(machineIcon("nanochip_assembly_complex"));

    // ------------------------------------------------------------------ 空表（JEI 隐藏）

    /**
     * ★ 2026-09-19（队长：「不用管地心，那个纳米组件分流器改一下」）：
     * <b>纳米组件分流器专用的空表</b>——分流器本来就没有配方（它不是加工机，只按
     * {@code NanochipSplitterRules} 的 6 条规则把虚拟件在**颜色通道**之间搬运），
     * 改前借用 {@code GTRecipeTypes.DUMMY_RECIPES} 占位，于是机器界面上的「机器模式」显示成
     * GTCEu 的官方译名<b>「假人」</b>（{@code gtceu.dummy}，实读 lang）——队长点名要改掉。
     *
     * <p>做法：给它一张**自己的、没有任何配方**的表（语义与 DUMMY 逐项相同：RecipeLogic 永远搜不到配方，
     * 只提供 UI/机器模式），模式名走本表的 lang 键 ⇒ 显示中文「虚拟件调度」。
     * <ul>
     *   <li><b>JEI 隐藏</b>（{@code setXEIVisible(false)}）：空分类没有意义，不该在 JEI 里占一格；</li>
     *   <li>图标仍指向分流器自己（与其余表同一口径，见 {@link #machineIcon});</li>
     *   <li>⚠️ 与 {@code GTRecipeTypes.DUMMY_RECIPES} <b>不是同一张表</b> ⇒ 体检
     *       {@code check_nac_mechanics_parity.js} 里那条 {@code SPLITTER: 'GTRecipeTypes.DUMMY_RECIPES'}
     *       随之更新（理由：队长要求模式名可读；"没有配方表"这个语义由本表继续承担）。</li>
     * </ul>
     */
    public static final GTRecipeType SPLITTER_SCHEDULE =
            hiddenType("nanochip_splitter_schedule", 1, 0, 0, 0)
                    .setProgressBar(GuiTextures.PROGRESS_BAR_ARROW, ProgressTexture.FillDirection.LEFT_TO_RIGHT)
                    .setIconSupplier(machineIcon("nanochip_splitter"));

    // ------------------------------------------------------------------ EU 档位常量（NH TierEU.RECIPE_* 的实数，本包无 TierEU）

    /**
     * NH {@code TierEU.RECIPE_IV} 的实数 = {@code floor(8192*30/32) = 7680} EU/t。
     *
     * <p><b>为什么补这条（2026-09-18 队长指示「降低一些配方的电压，有些根本达不到电压标准」）</b>：
     * 模块加工层按「服务档」降压后出现了 IV/LuV/ZPM 这些**比 NH 四档更低**的档位
     * （NH 的 {@code ModuleRecipeInfo} 只有 LowTier=UV / MediumTier=UHV / HighTier=UEV / ExtremeTier=UIV），
     * 而体检要求「配方用到的 EU/t 必须在 Java 里有同值常量」。补上它，而不是放宽检查。
     * <p>证据链与 {@link #RECIPE_LuV} 同一条（{@code TierEU.RECIPE_*} 取 {@code GTValues.VP[i]}，
     * {@code VP = V*30/32}；{@code V[5]=8192}）⇒ {@code floor(8192*30/32) = 7680}。
     */
    public static final long RECIPE_IV = 7680L;

    /**
     * NH {@code TierEU.RECIPE_ZPM} 的实数 = {@code floor(131072*30/32) = 122880} EU/t。
     *
     * <p>同 {@link #RECIPE_IV}：降压后 ZPM 档配方需要这个常量（证据链同上，{@code V[7]=131072}）。
     */
    public static final long RECIPE_ZPM = 122880L;

    /**
     * NH {@code TierEU.RECIPE_LuV} 的实数 = {@code 30720} EU/t。
     *
     * <p><b>证据链（现读，不是背的）</b>：{@code gregtech/api/enums/TierEU.class} 的 {@code static {}} 里
     * {@code RECIPE_ULV..RECIPE_MAX} 依次取 {@code GTValues.VP[0..14]}（javap -c 偏移 129-255）；
     * 而 {@code GTValues.VP = V.map(lambda$static$0)}，{@code lambda$static$0(long v) = v*30/32}
     * （BigInteger 乘 30 再除 32，截断）—— javap -c {@code gregtech/api/enums/GTValues} 偏移 144-165 + 行 247-267。
     * {@code V} 字面量：8/32/128/512/2048/8192/32768/131072/524288/2097152/8388608/33554432/134217728/536870912/2147483640
     * ⇒ {@code floor(32768*30/32) = 30720}。
     * <p>旁证：{@code _gtnh/javap_mix/gg_RecipeLoader2.txt:422} 把 {@code 30720} 直接当 EU/t 传进配方添加器。
     */
    public static final long RECIPE_LuV = 30720L;

    /**
     * NH {@code TierEU.RECIPE_UV} = {@code floor(524288*30/32) = 491520} EU/t。
     *
     * <p><b>证据链（与上面 {@link #RECIPE_LuV} 完全同一条，现读，不是背的）</b>：
     * <ol>
     *   <li><b>档位 → TierEU 字段</b>：{@code ModuleRecipeInfo} 的 {@code static {}} 依次
     *       {@code getstatic TierEU.RECIPE_UIV → putstatic ExtremeTier}、
     *       {@code RECIPE_UEV → HighTier}、{@code RECIPE_UHV → MediumTier}、
     *       {@code RECIPE_UV → LowTier}（javap -c 偏移 4/10、17/23、30/36、43/49）——
     *       NAC 的模块配方带着「LowTier」这个档位名，LowTier 就是 {@code RECIPE_UV}。</li>
     *   <li><b>TierEU 字段 → 实数</b>：{@code gregtech/api/enums/TierEU} 的 {@code static {}} 里
     *       {@code RECIPE_ULV..RECIPE_MAX} 依次取 {@code GTValues.VP[0..14]}；
     *       {@code GTValues.VP = V.map(lambda$static$0)}，{@code lambda$static$0(long v) = v*30/32}
     *       （BigInteger 乘 30 再整除 32）。</li>
     *   <li><b>V 字面量</b>：{@code V[8] = 524288} ⇒ {@code floor(524288*30/32) = 491520}。</li>
     * </ol>
     * <p><b>旁证</b>：P2 的 {@code LowTier} 配方共 <b>15</b> 条（切割仓 #11..#17 共 7 + SMD #1..#5 共 5 +
     * 封装卷绕器 #8..#10 共 3），其 EU/t 由 {@code _census/p2_input_map.json} 给出；
     * 生成器 {@code gen_nac_p2_recipes.js} 会**独立重算**这个常量并要求逐条相等
     * （见该生成器的 {@code readTierEuConstants()} + {@code MODULE_TIER_MAP}）。
     * <p>⚠️ <b>如实标注</b>：与 {@link #RECIPE_UMV} / {@link #RECIPE_MAX} 一样，UV 档在 NH 转储里
     * <b>没有</b>独立的字面量旁证，只有「公式 + 同链其余值已被 P1 验证」这一条推导链。
     */
    public static final long RECIPE_UV = 491520L;

    /**
     * NH {@code TierEU.RECIPE_UHV} = {@code floor(2097152*30/32) = 1966080} EU/t。
     *
     * <p>证据链同 {@link #RECIPE_UV}：{@code ModuleRecipeInfo.MediumTier ← TierEU.RECIPE_UHV}
     * （javap -c 偏移 30/36），而 {@code GTValues.V[9] = 2097152} ⇒ {@code floor(2097152*30/32) = 1966080}。
     * <p>⚠️ 同上：UHV 档也<b>没有</b>独立字面量旁证，仅有公式推导。P2 的 {@code MediumTier} 配方共 <b>17</b> 条
     * （蚀刻阵列 #1 共 1 + 切割仓 #1..#4 共 4 + 导线追踪器 #1/#2/#3/#7 共 4 + SMD #6..#10 共 5 +
     * 超导分离器 #1/#2 共 2 + 封装卷绕器 #1 共 1）。
     */
    public static final long RECIPE_UHV = 1966080L;

    /**
     * NH {@code TierEU.RECIPE_UEV} = {@code floor(8388608*30/32) = 7864320} EU/t。
     * 旁证：{@code _gtnh/javap_mix/gg_RecipeLoader2.txt:531/546/561} 与 {@code MaterialsInit.txt} 均出现该字面量。
     */
    public static final long RECIPE_UEV = 7864320L;

    /**
     * NH {@code TierEU.RECIPE_UIV} = {@code floor(33554432*30/32) = 31457280} EU/t。
     * 旁证：{@code _gtnh/javap_mix/gg_RecipeLoader2.txt:576}（{@code ldc int 31457280}）明文出现。
     */
    public static final long RECIPE_UIV = 31457280L;

    /**
     * NH {@code TierEU.RECIPE_UMV} = {@code floor(134217728*30/32) = 125829120} EU/t。
     * <p>⚠️ 该值<b>没有</b>在 NH 转储里找到独立字面量旁证（UMV 档极少被内联），
     * 只由上面的 {@code VP} 公式 + {@code V[12]} 推出 —— 如实标注。
     */
    public static final long RECIPE_UMV = 125829120L;

    /**
     * NH {@code TierEU.RECIPE_MAX} = {@code floor(2147483640*30/32) = 2013265912} EU/t。
     * <p>⚠️ 同上：无独立字面量旁证，由 {@code VP} 公式 + {@code V[14]=2147483640} 推出（整除截断，非四舍五入）——
     * 如实标注。
     */
    public static final long RECIPE_MAX = 2013265912L;

    // ------------------------------------------------------------------ 注册

    /**
     * <b>把 14 张表注册进三个注册表</b> —— 只允许在 GTCEu 的
     * {@code GTCEuAPI.RegisterEvent<ResourceLocation, GTRecipeType>} 窗口内调用一次
     * （见类注释 §三；窗口之外调用要么太早（Forge 注册表还没开）要么太晚（GTRegistry 已 freeze））。
     *
     * <p>本方法幂等；重复调用只打一条日志。
     */
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

    /**
     * 接线用的回调：在 {@code @Mod} 构造里
     * {@code modBus.addGenericListener(GTRecipeType.class, NanochipRecipeTypes::onRecipeTypeRegisterEvent)}。
     *
     * <p>必须是「具体事件类型」的方法引用（不能写成 IGenericEvent 的 lambda），否则 javac 会选到按基类
     * {@code GenericEvent} 挂载的重载，mod 总线只接受 IModBusEvent 具体子类 ⇒ 运行时
     * IllegalArgumentException —— 这条纪律本项目的机器注册已经踩过。
     */
    public static void onRecipeTypeRegisterEvent(
            GTCEuAPI.RegisterEvent<ResourceLocation, GTRecipeType> event) {
        init();
    }

    /**
     * 已注册的类型（只读，顺序 = 声明顺序）。未注册时返回空表。
     */
    public static Map<ResourceLocation, GTRecipeType> registered() {
        return java.util.Collections.unmodifiableMap(INDEX);
    }

    /** 配方类型 id（{@code gtlmodular:nanochip_xxx}）。 */
    public static ResourceLocation id(String path) {
        return NanochipModContext.registryId(path);
    }

    // ------------------------------------------------------------------ 内部

    private static void registerOne(GTRecipeType type) {
        // ① vanilla/Forge 的 RecipeType 注册表（RecipeManager 建 byType 索引用）
        GTRegistries.register(BuiltInRegistries.RECIPE_TYPE, type.registryName, type);
        // ② vanilla/Forge 的 RecipeSerializer 注册表（数据包 JSON 的 "type" 字段靠它解析）
        GTRegistries.register(BuiltInRegistries.RECIPE_SERIALIZER, type.registryName, new GTRecipeSerializer());
        // ③ GTCEu 自己的 RL 注册表（GTRecipe 的 codec 用它解析 "type"）
        GTRegistries.RECIPE_TYPES.register(type.registryName, type);
    }

    /**
     * 建一张表并配好尺寸/EU 方向。
     *
     * <p>{@code setMaxIOSize(int itemIn, int itemOut, int fluidIn, int fluidOut)}（javap -c 实证：
     * 参数依次喂给 ItemRecipeCapability.IN / FluidRecipeCapability.IN / ItemRecipeCapability.OUT /
     * FluidRecipeCapability.OUT）。
     * <p>★ 不设的话 {@code getMaxInputs/getMaxOutputs} 默认返回 <b>0</b>
     * （javap -c：{@code maxInputs.getOrDefault(cap, 0)}）⇒ 任何配方都会被判超尺寸，所以每张表都必须设。
     */
    private static GTRecipeType type(String path, int itemIn, int itemOut, int fluidIn, int fluidOut) {
        GTRecipeType type = new GTRecipeType(id(path), GROUP_MULTIBLOCK, new RecipeType<?>[0]);
        type.setEUIO(IO.IN);
        type.setMaxIOSize(itemIn, itemOut, fluidIn, fluidOut);
        type.setMaxTooltips(4);
        // 显式写明：JEI/REI 分类要可见（GTRecipeTypeUI 构造器本来就默认 true，这里只是把意图落到代码里）
        type.setXEIVisible(true);
        INDEX.put(type.registryName, type);
        return type;
    }

    /**
     * 与 {@link #type} 同构，但 <b>JEI/REI 隐藏</b>（{@code setXEIVisible(false)}）。
     *
     * <p>用途：**永远不会有任何配方**的占位表（目前只有 {@link #SPLITTER_SCHEDULE}）——
     * 这类表存在的唯一理由是"给机器一个自己的配方类型，好让机器模式/进度条有可读的中文名"，
     * 在 JEI 里显示一个空分类只会误导玩家。
     */
    private static GTRecipeType hiddenType(String path, int itemIn, int itemOut, int fluidIn, int fluidOut) {
        GTRecipeType type = type(path, itemIn, itemOut, fluidIn, fluidOut);
        type.setXEIVisible(false);
        return type;
    }

    /**
     * ★ 与 {@link #type} 同构，但把 GTCEu 的<b>代理配方表</b>机制接上（宿主表 = {@code parent}），
     * 且尺寸闸门<b>逐项现读 parent 的实数</b>。目前只有主机表 {@link #ASSEMBLY_COMPLEX} 用它。
     *
     * <p>★★ 2026-10-14：<b>已停用（零调用者）</b> —— 唯一的调用者主机表 {@link #ASSEMBLY_COMPLEX}
     * 已按队长令<b>剪断代理</b>（改成不带代理的 {@link #type}）⇒ 本方法现在没有任何调用点。
     * 按队长令<b>保留不删</b>（只加这条注释）：日后若要给某张表重新接代理，改回一行即可；
     * 若确定长期不用，可连同下面的 {@link ProxiedRecipeType}（它是本方法唯一的引用者）一起整段清理。
     * <p>⚠️ 保留它<b>不影响</b>运行时：本方法不被调用 ⇒ 不会建出任何带代理的表。
     *
     * <h4>为什么尺寸取 parent 的实数（而不是写常量）</h4>
     * 先说清 GTCEu 里 {@code getMaxInputs/getMaxOutputs} 的**实际消费点**（javap 全包扫描，只有这些）：
     * {@code GTRecipeWidget}（按 {@code recipe.recipeType} 决定画几个槽）、{@code Predicates}
     * （单方块控制器要不要 EU 仓）、{@code WorkableTieredMachine} / {@code SimpleSteamMachine}
     * （单方块机的输入输出槽数）、以及各机器的 UI 布局。
     * ⇒ 对**多方块**主机而言它不是"配方准入门槛"（准入门槛在 {@code RecipeHelper.matchRecipe} /
     * gtlcore {@code MultipleRecipesLogic.checkRecipe}，两者都<b>不读</b> maxIO），
     * 但凡是"按本表读尺寸"的地方都必须与电路组装机**同口径** —— 本表若比 parent 小，
     * UI/工具视图就会画得比 parent 少、与"主机其实能跑 parent 的配方"自相矛盾。
     * ⇒ 这里直接现读 parent 的四个值，从构造上保证逐项相同（`setMaxIOSize` 的参数依次是
     * ItemIn / ItemOut / FluidIn / FluidOut，javap -c 实证；不设的话 {@code getMaxInputs} 默认返回 0）。
     *
     * <h4>为什么可以在静态初始化里读 {@code GTRecipeTypes.*}（加载顺序）</h4>
     * GTCEu 的 {@code CommonProxy.init()} 字节码顺序是 … {@code GTRecipeTypes.init()}（偏移 94）
     * ⇒ {@code GTMachines.init()}（偏移 97）；而 {@link #init()}（本类的注册入口）正是被
     * {@code GTRecipeTypes.init()} 末尾那条 {@code GTCEuAPI.RegisterEvent} 回调进来的
     * （见类注释 §三）⇒ 本类静态初始化最晚也不晚于那一刻，而那一刻 {@code GTRecipeTypes} 早已完成
     * 静态初始化（{@code CIRCUIT_ASSEMBLER_RECIPES} 非空）⇒ 无 NullPointer / 无提前注册风险。
     * 另外电路组装机表自己的注册实参 {@code GTRecipeTypes.register("circuit_assembler", "electric")}
     * + {@code setMaxIOSize(6, 1, 1, 0)} 已在 {@code GTRecipeTypes} 静态块（偏移 2296-2313）核对过，
     * 现读只是让口径自动跟随上游。
     */
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

    /**
     * ★★ 主机表用的 {@code GTRecipeType} 子类：<b>把"代理进来的配方"恒等返回</b>。
     *
     * <p>★★ 2026-10-14：<b>已停用</b>（构造器只有 {@link #proxiedType} 一处调用，而该方法现已零调用者）
     * —— 本类的行为（代理建表 / 尺寸现读 parent / {@code toGTrecipe} 只换皮）<b>现在都不再被触发</b>，
     * 保留仅供追溯与"一行复启用"，详见 {@link #proxiedType}。
     *
     * <p>GTCEu 的代理机制在填充代理表时会对 {@code byType} 里代理表的每一条配方调用
     * {@code toGTrecipe(id, recipe)}（{@code RecipeManagerMixin} 偏移 118 / KubeJS 偏移 940，
     * 都是 {@code invokevirtual} ⇒ 可覆写）。父类的实现是"<b>原版配方 → GT 配方</b>的重建"：
     * <pre>
     *   recipeBuilder(id).inputItems(recipe.getIngredients()...)   // 只搬物品输入
     *                   .outputItems(recipe.getResultItem(ITEM))    // 只搬第一个产物
     *                   + 默认 EU/t / 默认时长（只有 SmeltingRecipe 才搬熔炼时长）
     *   → GTRecipeSerializer.fromJson(id, builder.build().serializeRecipe())
     * </pre>
     * 对<b>已经</b>是 {@code GTRecipe} 的电路组装机配方来说，这条转换会丢掉 EU/t、时长、流体输入输出、
     * 概率与条件 —— 等于"主机跑的配方被改数"。所以这里恒等返回：代理表里装的就是原对象本身
     * （<b>不复制、不重建、不动数据包</b>），两张表共享同一批 {@code GTRecipe}。
     *
     * <p>非 {@code GTRecipe}（理论上不会出现，因为代理表是 GT 表；但若哪天接了原版表）才回落到父类转换。
     */
    private static final class ProxiedRecipeType extends GTRecipeType {

        ProxiedRecipeType(ResourceLocation id, RecipeType<?> parent) {
            super(id, GROUP_MULTIBLOCK, parent);
        }

        /**
         * ★ 0.3.94：把"恒等返回"✗ 改成"**只换皮**"✓ —— 复制一份配方、把类型标成本代理表 ✓
         * 输入输出 / 电压 / 时长 / 流体 / 概率一字不动 ✓。
         * 依据用户实测「样板放进任何总成都不在 AE 显示」✓（_census\NAC命名与样板无效_定位.md ✓）：
         * 机器宣告类型 = NAC，而配方真实类型 = 电路组装机 ✗ ⇒ 两套口径不一致 ⇒ 匹配不上 ✓。
         *
         * <p>实现全部走**反射**（不依赖上游编译期签名 ✓）：找不到 copy / 字段 ⇒ 退回原对象（= 旧行为 ✓ 零风险 ✓）。
         */
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

        /** 反射找一个可用的 copy：优先无参 copy()，其次 copy(ContentModifier 或 null 之类)。失败返 null ✓。 */
        private static GTRecipe gtlmodular$copyRecipe(GTRecipe gt) {
            Class<?> k = gt.getClass();
            // ① 无参 copy()
            for (Class<?> c = k; c != null; c = c.getSuperclass()) {
                try {
                    java.lang.reflect.Method m = c.getDeclaredMethod("copy");
                    m.setAccessible(true);
                    Object r = m.invoke(gt);
                    if (r instanceof GTRecipe out) {
                        return out;
                    }
                } catch (NoSuchMethodException ignored) {
                    // 继续往父类找 ✓
                } catch (Throwable ignored) {
                    // 调用失败 ⇒ 试下一个办法 ✓
                }
            }
            // ② 单参 copy(某类型)：找一个只吃一个参数、名字是 copy 的方法，参数传 null
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
                        // 参数不兼容 ⇒ 放弃这条路 ✓
                    }
                }
            }
            return null;
        }

        /** 把复制品的 recipeType 改成给定类型（先 setter 再字段 ✓）。成功返 true ✓。 */
        private static boolean gtlmodular$markType(GTRecipe target, GTRecipeType type) {
            try {
                for (Class<?> c = target.getClass(); c != null; c = c.getSuperclass()) {
                    try {
                        java.lang.reflect.Method m = c.getDeclaredMethod("setRecipeType", GTRecipeType.class);
                        m.setAccessible(true);
                        m.invoke(target, type);
                        return true;
                    } catch (NoSuchMethodException ignored) {
                        // 试字段 ✓
                    }
                }
            } catch (Throwable ignored) {
                // 落到字段 ✓
            }
            for (Class<?> c = target.getClass(); c != null; c = c.getSuperclass()) {
                try {
                    java.lang.reflect.Field fl = c.getDeclaredField("recipeType");
                    fl.setAccessible(true);
                    fl.set(target, type);
                    return true;
                } catch (NoSuchFieldException ignored) {
                    // 继续往上 ✓
                } catch (Throwable ignored) {
                    return false;
                }
            }
            return false;
        }
    }

    /**
     * ★ 2026-09-19（队长：「JEI 里模块的配方应当显示它自己的图标，不显示别的」）：
     * 图标 = <b>该配方表对应那台机器自己的方块物品</b>（{@code gtlmodular:nanochip_<模块>}）。
     *
     * <p>改前这里发的是「本模组 CC 物品层里的某个真件」（例如 {@code WireNiobiumTitanium} /
     * {@code CrystalProcessor}），结果是：
     * <ul>
     *   <li>14 张表的图标彼此无关，玩家在 JEI 里按图标找"这台模块干什么"根本对不上；</li>
     *   <li>{@code nanochip_conversion} 与 {@code nanochip_wire_tracer} 撞成同一张图；</li>
     *   <li>队长原话：「JEI 里模块的配方应当显示它自己的图标，不显示别的」。</li>
     * </ul>
     *
     * <p>换成机器方块物品后，JEI 分类的催化剂/左上角图标就等于"这台机器本身"，
     * 与 GTCEu 自带多方块（分类图标 = 机器物品）的口径一致。
     *
     * <p>⚠️ 只是<b>读</b>机器定义（{@link NanochipMachines#machineIconStack(String)}），不碰配方匹配；
     * 而且查表发生在 <b>JEI 建分类时</b>（{@code GTRecipeType.setIconSupplier} 存的是 Supplier，
     * {@code GTRecipeTypeCategory} 渲染时才 get()），此时注册窗口早已结束 ⇒ 不存在注册顺序风险；
     * <b>不是运行时轮询</b>（每次都返回同一个方块物品，无 tick、无缓存失效问题）。
     */
    private static Supplier<ItemStack> machineIcon(String machineId) {
        return () -> NanochipMachines.machineIconStack(machineId);
    }
}
