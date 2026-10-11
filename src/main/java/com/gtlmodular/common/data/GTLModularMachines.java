package com.gtlmodular.common.data;

import com.gtlmodular.GTLModular;
import com.gtlmodular.api.module.ModuleKind;
import com.gtlmodular.common.machine.LazyPatternAssemblyPartMachine;
import com.gtlmodular.common.machine.MoltenModuleHostMachine;
import com.gtlmodular.common.machine.MoltenBlastModuleMachine;
import com.gtlmodular.common.machine.MoltenModuleMachineBase;
import com.gtlmodular.common.machine.MoltenPatternAssemblyPartMachine;
import com.gtlmodular.common.machine.MoltenSpaceOreModuleMachine;
import com.gtlmodular.common.machine.MoltenStripperModuleMachine;
import com.gtlmodular.common.machine.fuel.DualFluidTax;
import com.gtlmodular.common.machine.fuel.MoltenRelaxedElectricOverclock;
import com.gtlmodular.common.data.host.MoltenModuleHostPattern;
import com.gtlmodular.common.data.module.SpaceOreModulePattern;
import com.gtlmodular.common.data.module.SpaceOrePatternLayers;
import com.gregtechceu.gtceu.api.GTCEuAPI;
import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.pattern.FactoryBlockPattern;
import com.gregtechceu.gtceu.api.pattern.Predicates;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate;
import com.gregtechceu.gtceu.common.data.GTCreativeModeTabs;
import com.gregtechceu.gtceu.common.data.GTBlocks;
import com.gregtechceu.gtceu.common.data.GTRecipeModifiers;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.tterrag.registrate.util.entry.RegistryEntry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.ForgeRegistries;

import static com.gregtechceu.gtceu.api.pattern.Predicates.abilities;
import static com.gregtechceu.gtceu.api.pattern.Predicates.blocks;
import static com.gregtechceu.gtceu.api.pattern.Predicates.controller;

/**
 * 机器注册（M1：熔岩高炉单元，结构/机制与 GTM 电力高炉同族）。
 *
 * <p>注册 API 依据 GTM 1.4.4（gtceu jar 字节码核对）：
 * GTRegistrate.multiblock → MultiblockMachineBuilder：recipeType / recipeModifier /
 * appearanceBlock / pattern / workableCasingRenderer / tooltips / register。
 *
 * <p><b>注册时机（关键，依据 gtlcore/gtladditions 同款做法的字节码核对）：</b><br>
 * 不要在任何“类加载即注册”或 IGTAddon.initializeAddon 阶段调用 REGISTRATE.multiblock。
 * GTM 在 {@code GTMachines.init()}（gtceu CommonProxy.init 的延迟队列内）末尾执行：
 * {@code MACHINES.unfreeze → ModLoader.postEvent(new GTCEuAPI.RegisterEvent<>(MACHINES,
 * MachineDefinition.class)) → MACHINES.freeze()}。<br>
 * 机器只有在那个 RegisterEvent 窗口内才可写入 {@code gtceu:machine}（其后即冻结，
 * initializeAddon 阶段已晚）。因此仿照 gtlcore：在 @Mod 构造里对 mod 事件总线注册
 * {@code addGenericListener(MachineDefinition.class, e -> init())}，由该回调触发注册链。
 */
public final class GTLModularMachines {

    private GTLModularMachines() {
    }

    /** 本模组的 Registrate 实例（addon 各自的 registrate，勿用 GT 的 REGISTRATE 注册本模组机器）。 */
    public static final GTRegistrate REGISTRATE = GTRegistrate.create(GTLModular.MOD_ID);

    /** 本模组创造标签页：显示生成器自动收录本 REGISTRATE 上注册的全部方块/物品。 */
    public static final RegistryEntry<CreativeModeTab> CREATIVE_TAB;

    static {
        // 依据 gtlcore GTLCreativeModeTabs 同款写法：自带默认标签页。
        // 关键点二：GTM 显示生成器(RegistrateDisplayItemsGenerator)只收录被记入
        // TAB_LOOKUP 的条目，而 TAB_LOOKUP 仅在注册条目前调用过
        // REGISTRATE.creativeModeTab(tab)（把 currentTab 设为本标签页）时才会填充。
        // 因此建完标签页后必须立刻把它设为“当前标签页”，后续机器条目才会出现在创造/JEI。
        CREATIVE_TAB = REGISTRATE.defaultCreativeTab(GTLModular.MOD_ID, builder -> {
            builder.displayItems(
                    new GTCreativeModeTabs.RegistrateDisplayItemsGenerator(GTLModular.MOD_ID, REGISTRATE));
            builder.title(REGISTRATE.addLang("itemGroup",
                    new ResourceLocation(GTLModular.MOD_ID, "creative_tab"),
                    "GTL·熔岩模块化机组"));
            // 标签页图标：地心涌动能量转换枢纽（主机还没注册好时退回岩浆桶兜底）
            // 用类名限定：静态初始化块里直接写简单名属于 Java 的"非法前向引用"
            builder.icon(() -> GTLModularMachines.MOLTEN_MODULE_HOST == null
                    ? new ItemStack(Items.LAVA_BUCKET)
                    : GTLModularMachines.MOLTEN_MODULE_HOST.asStack());
            builder.build();
        }).register();
        REGISTRATE.creativeModeTab(() -> CREATIVE_TAB);
    }

    private static boolean initialized = false;

    /** M1 模块A定义：熔岩高炉单元 —— 直接复用电力高炉(BLAST)配方表。 */
    public static MultiblockMachineDefinition MOLTEN_BLAST_MODULE;

    /** M4 模块B定义：熔岩星核剥离单元 —— 复用 gtladditions star_core_stripper 配方表。 */
    public static MultiblockMachineDefinition MOLTEN_STRIPPER_MODULE;

    /** M4 模块C定义：熔岩天基炼矿单元 —— 复用 gtladditions space_ore_processor 配方表。 */
    public static MultiblockMachineDefinition MOLTEN_SPACE_ORE_MODULE;

    /** M5 主控制器定义：地心涌动能量转换枢纽（整栋「地心涌动能量转换站」大楼成形，模块自动挂载）。 */
    public static MultiblockMachineDefinition MOLTEN_MODULE_HOST;

    /** 样板总成的电压范围：MV(2) ~ UIV(11)。 */
    public static final int PATTERN_ASSEMBLY_MIN_TIER = 2;
    public static final int PATTERN_ASSEMBLY_MAX_TIER = 11;

    /** 各级「猪咪样板总成」定义（key = 电压等级）。 */
    public static final java.util.Map<Integer, MachineDefinition> PATTERN_ASSEMBLY = new java.util.LinkedHashMap<>();

    /** 超级猪咪样板总成：UEV 级、{@value MoltenPatternAssemblyPartMachine#MAX_PAGES} 页 = 640 槽（原 16 页/2048 槽已按队长裁决封顶），贴图与 GTL Add 的超级样板总成一致。 */
    public static final int SUPER_PATTERN_ASSEMBLY_TIER = 10;   // GTValues.UEV
    public static final int SUPER_PATTERN_ASSEMBLY_PAGES = MoltenPatternAssemblyPartMachine.MAX_PAGES;
    public static MachineDefinition SUPER_PATTERN_ASSEMBLY;

    /**
     * ★ 2026-09-26（P0 旁路试验）<b>惰性猪咪样板总成</b>：全新方块 id
     * {@code gtlmodular:lazy_me_pattern_assembly}，继承 gtlcore <b>抽象基类</b>
     * {@code MEPatternBufferPartMachineBase}（不是具体类），做「按需建槽」。
     *
     * <p><b>现有 {@code *_me_pattern_assembly} 一个字都没改</b>——本机只用于验证惰性实现，
     * 测试通过后才会考虑替换现役 26 台（见 {@code _自研猪咪兼容清单.md} 第九节队长裁决）。
     * 实现见 {@link LazyPatternAssemblyPartMachine}。
     */
    public static MachineDefinition LAZY_PATTERN_ASSEMBLY;
    /** 惰性版电压等级：与现有最低一档对齐（MV = 2）。 */
    public static final int LAZY_PATTERN_ASSEMBLY_TIER = PATTERN_ASSEMBLY_MIN_TIER;

    /**
     * ★ 2026-09-22 晚 <b>退役</b>：本模组自制的「ME通配符样板总成」已删除注册。
     *
     * <p>原因（队长裁决）：真正需要的那条路走不通，而整合包里本来就有现成实现 ——
     * 队长提供了第三方 mod <b>{@code wildcard_pattern-0.1.2-gtl.jar}</b>（LeoDreamer，"Wildcard Pattern"），
     * 并且 <b>gtlcore 自带按"是否装了该 mod"开关的兼容层</b>（{@code WildcardPatternCompat.isLoaded()} →
     * {@code registerMachines()}，注册 {@code ME_WILDCARD_PATTERN_BUFFER} 与
     * {@code ME_STOCKING_WILDCARD_PATTERN_BUFFER} 两台上位机器，内部调该 mod 的
     * {@code expandPatterns(ItemStack, Level)}）。队长令：「直接删掉我们那台」。
     *
     * <p>于是本文件的注册链里 <b>不再有</b> {@code registerWildcardPatternBuffer()}；
     * {@code common/machine/wildcard/**} 与 {@code client/jei/WildcardTagPrefixJEI} 也一并删除
     * （它们与 gtlcore 官方机器同名，留着只会让人看混）。
     *
     * <p>⚠️ 存档提示：已放置的那台方块会随注册消失（队长已同意）。lang 里
     * {@code gtlmodular.wildcard*} 那批键暂时保留（纯冗余，不影响任何东西）。
     */

    /**
     * @Mod 构造中调用：把本模组 Registrate 事件挂到 mod 事件总线，并订阅 GTM 的
     * 机器注册窗口（{@code GTCEuAPI.RegisterEvent<RL, MachineDefinition>}）。
     *
     * <p>注意：必须用“具体事件类型”的方法引用（而非 IGenericEvent lambda），否则 javac
     * 会选中按基类 GenericEvent 挂载的重载，而 mod 总线只接受 IModBusEvent 具体子类，
     * 运行时会抛 IllegalArgumentException（依据 gtlcore CommonProxy 同款写法）。
     */
    public static void registerEventListeners(IEventBus modBus) {
        REGISTRATE.registerEventListeners(modBus);
        modBus.addGenericListener(MachineDefinition.class, GTLModularMachines::onMachineRegisterEvent);
    }

    /** GTM 投递机器 RegisterEvent（MACHINES 未冻结窗口）时的回调。 */
    private static void onMachineRegisterEvent(
            GTCEuAPI.RegisterEvent<ResourceLocation, MachineDefinition> event) {
        init();
    }

    /** 仅在 GTM 机器注册窗口回调：触发本类初始化并执行注册链。 */
    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;

        registerModuleA();
        registerModuleB();
        registerModuleC();
        registerModuleHost();
        registerPatternAssemblies();
        registerSuperPatternAssembly();
        // ★ 2026-09-26（P0）：惰性猪咪样板总成 —— 新方块 id，旁路试验，不动现有机器
        registerLazyPatternAssembly();
        // ★ 2026-09-22 晚：自制通配符样板总成已退役（见上方注释）——改用第三方 mod
        //   `wildcard_pattern` + gtlcore 的官方兼容机器，这里不再注册任何通配符机器。
        // NAC（纳米芯片组装复合体，GTNH 移植）：控制器 + 11 个模块
        com.gtlmodular.common.data.nanochip.NanochipMachines.register();
    }

    /**
     * 结构方块的创建入口 —— <b>必须在 @Mod 构造里、Forge 的 RegisterEvent&lt;Block&gt; 之前调用</b>。
     *
     * <p>原因：Registrate 的方块条目要在 {@code RegisterEvent<Block>} 窗口落库，而 GTM 的
     * 机器注册窗口（{@code GTCEuAPI.RegisterEvent<...MachineDefinition>}）发生在
     * {@code FMLCommonSetupEvent} 里，<b>晚于</b>方块注册。若在机器注册窗口里才创建方块条目，
     * 它们就永远进不了方块注册表。
     */
    public static void initBlocks() {
        com.gtlmodular.common.data.NanochipBlocks.init();
        // NAC 的电路组件（CC）真实物品层：176 件（99 未处理 + 77 已处理），
        // 同样必须在 Forge 的 RegisterEvent<Item> 之前建好 Registrate 条目，
        // 否则这些物品永远进不了物品注册表（与上面方块的时序要求同一原因）。
        com.gtlmodular.common.data.nanochip.NanochipCircuitComponents.init();
        // NAC 缺件层的 17 件自注册真实物品（PIC 六档 / 活晶体芯片 / 生物处理单元 / 光缆 /
        // Piko·Quantum·Planck 电路 / Planck 流形 / 四种 itemCasing）—— 时序要求同上。
        com.gtlmodular.common.data.nanochip.NanochipMissingItems.init();
        // ★ 2026-09-19（队长批准新造晶圆 / 还原 NH 两步链）：NAC 缺件层的 **5 件新造 PIC 晶圆**
        //   （量子/阿米/仄米/幺米/普朗克功率IC晶圆）—— 时序要求同上，且必须与缺件物品层同一个窗口。
        //   独立成类的原因见 NanochipMissingWafers 的 javadoc（缺件表 14 行/10 件被两处体检钉死）。
        com.gtlmodular.common.data.nanochip.NanochipMissingWafers.init();
        // ★ P2：样板仓「归档」工具物品（gtlmodular:pattern_archive）—— 时序要求同上：
        //   必须在 Forge 的 RegisterEvent<Item> **之前**建好 Registrate 条目，否则它永远进不了物品注册表。
        com.gtlmodular.common.item.PatternArchiveItem.register();
    }

    /**
     * 猪咪样板总成：MV ~ UIV 共 10 级（每级一台，正面是样板总成贴图，其余面是对应电压的机器外壳）。
     *
     * <p>做法见 {@link MoltenPatternAssemblyPartMachine}：仓室继承 gtlcore 的 ME 样板总成 +
     * 配方模式自动识别/切换 + 样板倍率 + 神锻样板模式。
     */
    private static void registerPatternAssemblies() {
        for (int tier = PATTERN_ASSEMBLY_MIN_TIER; tier <= PATTERN_ASSEMBLY_MAX_TIER; tier++) {
            registerPatternAssembly(tier);
        }
    }

    private static void registerPatternAssembly(int tier) {
        String tierName = MoltenPatternAssemblyPartMachine.tierName(tier);
        // id 用 ME 风格（含 _me_）：gtlcore 的无线 ME 网络按方块 id 路径识别可无线连接的目标，
        // me_ / _me_ / ae_ / _ae_ / appeng / ae2 开头的路径才算（gtladditions 的 me_super_pattern_buffer 同理）。
        String id = tierName.toLowerCase(java.util.Locale.ROOT) + "_me_pattern_assembly";
        int slots = MoltenPatternAssemblyPartMachine.slotsForTier(tier);
        PATTERN_ASSEMBLY.put(tier, REGISTRATE.machine(id,
                        holder -> new MoltenPatternAssemblyPartMachine(holder, tier, slots))
                .rotationState(RotationState.ALL)
                // 与样板总成一致：四个 IO 能力（所以能放进我们三个模块的外壳格）
                .abilities(PartAbility.IMPORT_ITEMS, PartAbility.EXPORT_ITEMS,
                        PartAbility.IMPORT_FLUIDS, PartAbility.EXPORT_FLUIDS)
                .tier(tier)
                // 正面 = 样板总成覆盖层，其余面 = 该电压的机器外壳
                .renderer(() -> new com.gregtechceu.gtceu.client.renderer.machine.OverlayTieredMachineRenderer(
                        tier, new ResourceLocation("gtceu", "block/machine/part/me_pattern_buffer")))
                .langValue(tierName + "猪咪样板总成")
                .tooltips(patternAssemblyTooltips(slots))
                .register());
        GTLModular.LOGGER.info("[gtlmodular] 已注册样板总成: {} 猪咪样板总成(tier={}, 样板槽={})", tierName, tier, slots);
    }

    /**
     * 样板总成 / 超级样板总成的统一描述（顺序固定）：
     * <ol>
     *   <li>集成式 AE2 样板供应器（自动读控制器配方类型、可接无线 ME）</li>
     *   <li>样板槽位数量（%s）</li>
     *   <li>按样板自动调节机器模式</li>
     *   <li>不串配方</li>
     *   <li>样板倍率（输入侧/输出侧）</li>
     *   <li>神锻样板模式</li>
     *   <li>专属催化剂槽</li>
     *   <li>每个样板槽位可单独定制模式</li>
     *   <li>闪存绑定 ME 样板总成镜像</li>
     * </ol>
     */
    private static Component[] patternAssemblyTooltips(int slots) {
        return new Component[]{
                Component.translatable("gtlmodular.pattern_assembly.tooltip.0"),
                Component.translatable("gtlmodular.pattern_assembly.tooltip.slots", slots),
                Component.translatable("gtlmodular.pattern_assembly.tooltip.1"),
                Component.translatable("gtlmodular.pattern_assembly.tooltip.2"),
                Component.translatable("gtlmodular.pattern_assembly.tooltip.3"),
                Component.translatable("gtlmodular.pattern_assembly.tooltip.4"),
                Component.translatable("gtlmodular.pattern_assembly.tooltip.5"),
                Component.translatable("gtlmodular.pattern_assembly.tooltip.6"),
                Component.translatable("gtlmodular.pattern_assembly.tooltip.7"),
        };
    }

    /**
     * 超级猪咪样板总成：UIV 级、{@value #SUPER_PATTERN_ASSEMBLY_PAGES} 页（每页 128）= 640 个样板槽
     * （队长 2026-09-25 裁决「最多 5 页就够」，原先的 16 页/2048 槽已封顶），
     * <b>贴图与 GTL Add 的「超级样板总成」一致</b>（无限输入总线外壳 + ME 样板总成覆盖层），
     * 功能与普通猪咪样板总成完全一样，只是槽位更多。
     */
    private static void registerSuperPatternAssembly() {
        int slots = SUPER_PATTERN_ASSEMBLY_PAGES * MoltenPatternAssemblyPartMachine.PER_PAGE;
        SUPER_PATTERN_ASSEMBLY = REGISTRATE.machine("super_me_pattern_assembly",
                        holder -> new MoltenPatternAssemblyPartMachine(holder, SUPER_PATTERN_ASSEMBLY_TIER, slots))
                .rotationState(RotationState.ALL)
                .abilities(PartAbility.IMPORT_ITEMS, PartAbility.EXPORT_ITEMS,
                        PartAbility.IMPORT_FLUIDS, PartAbility.EXPORT_FLUIDS)
                .tier(SUPER_PATTERN_ASSEMBLY_TIER)
                // ★ 0.3.68：恢复自定义渲染器 —— 0.3.63 去掉后**变成紫黑方块**（缺贴图）✗ 实测反馈 ✓
                .renderer(GTLModularMachines::superPatternRenderer)
                .langValue("超级猪咪样板总成")
                .tooltips(patternAssemblyTooltips(slots))
                .register();
        GTLModular.LOGGER.info("[gtlmodular] 已注册超级样板总成: 超级猪咪样板总成(tier={}, 样板槽={})",
                SUPER_PATTERN_ASSEMBLY_TIER, slots);
    }

    /**
     * 超级版贴图：与 gtladditions 的「超级样板总成」一致
     * （外壳 {@code gtladditions:block/casings/infinity_input_dual_hatch_casing} + ME 样板总成覆盖层）。
     *
     * <p>这个 supplier 只在客户端初始化渲染器时求值，所以没装 gtladditions 时也不会崩，
     * 会退回 UIV 外壳 + 同样覆盖层的渲染，功能不受影响。
     */
    private static com.lowdragmc.lowdraglib.client.renderer.IRenderer superPatternRenderer() {
        ResourceLocation overlay = new ResourceLocation("gtceu", "block/machine/part/me_pattern_buffer");
        try {
            return new com.gtlmodular.client.render.BlendingPatternHullRenderer(
                    new ResourceLocation("gtladditions", "block/casings/infinity_input_dual_hatch_casing"),
                    overlay);
        } catch (Throwable ignored) {
            return new com.gregtechceu.gtceu.client.renderer.machine.OverlayTieredMachineRenderer(
                    SUPER_PATTERN_ASSEMBLY_TIER, overlay);
        }
    }

    /**
     * ★ 2026-09-26（P0 旁路试验）<b>惰性猪咪样板总成</b>：新方块 id
     * {@code gtlmodular:lazy_me_pattern_assembly}（MV 档 tier=2），显示名「惰性猪咪样板总成」。
     *
     * <p>与现役猪咪完全<b>平级并存</b>：abilities / 渲染方式与现有猪咪一致，
     * 但仓室换成 {@link LazyPatternAssemblyPartMachine}（继承 gtlcore 抽象基类、按需建槽）。
     * 现有 {@code *_me_pattern_assembly} 的注册、类与行为<b>一个字都没改</b>。
     */
    private static void registerLazyPatternAssembly() {
        int tier = LAZY_PATTERN_ASSEMBLY_TIER;
        // 硬上限沿用现有 MV 档的槽数（1 页 = 128）；实际容量是惰性的（已用 + 预留）。
        int maxSlots = MoltenPatternAssemblyPartMachine.slotsForTier(tier);
        LAZY_PATTERN_ASSEMBLY = REGISTRATE.machine("lazy_me_pattern_assembly",
                        holder -> new LazyPatternAssemblyPartMachine(holder, tier, maxSlots))
                .rotationState(RotationState.ALL)
                // 与现有猪咪一致：四个 IO 能力
                .abilities(PartAbility.IMPORT_ITEMS, PartAbility.EXPORT_ITEMS,
                        PartAbility.IMPORT_FLUIDS, PartAbility.EXPORT_FLUIDS)
                .tier(tier)
                // 渲染方式复用现有猪咪（ME 样板总成覆盖层 + 对应电压外壳）
                .renderer(() -> new com.gregtechceu.gtceu.client.renderer.machine.OverlayTieredMachineRenderer(
                        tier, new ResourceLocation("gtceu", "block/machine/part/me_pattern_buffer")))
                .langValue("惰性猪咪样板总成")
                .tooltips(
                        Component.translatable("gtlmodular.lazy_pattern_assembly.tooltip.0"),
                        Component.translatable("gtlmodular.lazy_pattern_assembly.tooltip.1"),
                        Component.translatable("gtlmodular.lazy_pattern_assembly.tooltip.2"),
                        Component.translatable("gtlmodular.lazy_pattern_assembly.tooltip.3"))
                .register();
        GTLModular.LOGGER.info("[gtlmodular] 已注册惰性猪咪样板总成: id={} tier={} 槽位硬上限={}（常态容量=已用+{}）",
                "lazy_me_pattern_assembly", tier, maxSlots, LazyPatternAssemblyPartMachine.RESERVE);
    }

    /** M5 主控制器：地心涌动能量转换枢纽（整栋大楼成形 + 大范围模块挂载）。 */
    private static void registerModuleHost() {
        ModuleKind kind = ModuleKind.HOST;
        MOLTEN_MODULE_HOST = REGISTRATE.multiblock(kind.machineId(), MoltenModuleHostMachine::new)
                // 主机只允许水平(左右)旋转，不可上下翻转
                .rotationState(RotationState.NON_Y_AXIS)
                // 占位空配方表：主机须走 workable 基类以获得与生态圈一致的完整 GT 机器 UI，
                // 但主机自身不加工——DUMMY_RECIPES 无任何配方，RecipeLogic 永不匹配、只蓄电挂模块
                .recipeType(GTRecipeTypes.DUMMY_RECIPES)
                .pattern(MoltenModuleHostPattern::build)
                .appearanceBlock(() -> blockOf("gtlcore:iridium_casing"))
                .workableCasingRenderer(
                        // 外壳（结构方块 + 挂在其上的舱室连接纹理）换成铱强化机械方块
                        new ResourceLocation("gtlcore", "block/casings/iridium_casing"),
                        // 正脸/机器面板保持原来的 data_bank 贴图不变
                        gtceuId("block/multiblock/data_bank"))
                .tooltips(
                        Component.translatable("block.gtlmodular.molten_module_host.desc"))
                .langValue(kind.displayNameZh())
                .register();
        GTLModular.LOGGER.info("[gtlmodular] M5 已注册机器: {} ({})",
                MOLTEN_MODULE_HOST.getId(), kind.displayNameZh());
    }

    // ---------- 注册实现 ----------

    /**
     * 模块A/B 的 EBF 配方修正器：沿用 gtceu（被 gtlcore 改写过公式）的 {@code ebfOverclock}，
     * 额外在"温度不足"被拒时把三个关键数记到机器上（配方需求 / 本机实读线圈 / 本机判定炉温），
     * 供界面显示 —— 用来区分「模块没读到主机线圈」还是「配方确实需要更热的线圈」。
     */
    public static com.gregtechceu.gtceu.api.recipe.GTRecipe moltenEbfOverclock(
            com.gregtechceu.gtceu.api.machine.MetaMachine machine,
            com.gregtechceu.gtceu.api.recipe.GTRecipe recipe,
            com.gregtechceu.gtceu.api.recipe.logic.OCParams ocParams,
            com.gregtechceu.gtceu.api.recipe.logic.OCResult ocResult) {
        com.gregtechceu.gtceu.api.recipe.GTRecipe result =
                GTRecipeModifiers.ebfOverclock(machine, recipe, ocParams, ocResult);
        if (result == null && machine instanceof MoltenModuleMachineBase<?> module) {
            int required = recipe.data.contains("ebf_temp") ? recipe.data.getInt("ebf_temp") : -1;
            int coilTemp = module.getCoilTemperature();
            int maxTemp = coilTemp + 100 * Math.max(0, module.getTier() - 2);
            module.recordTemperatureFailure(required, coilTemp, maxTemp);
        }
        return result;
    }

    /** M1 模块A：熔岩高炉单元（EBF 温度语义 + 双流体税）。 */
    private static void registerModuleA() {
        ModuleKind kind = ModuleKind.BLAST;
        MOLTEN_BLAST_MODULE = REGISTRATE.multiblock(kind.machineId(), MoltenBlastModuleMachine::new)
                .rotationState(RotationState.ALL)
                .recipeType(GTRecipeTypes.BLAST_RECIPES)          // 复用电力高炉配方表
                .recipeModifier(GTLModularMachines::moltenEbfOverclock) // 沿用 EBF 线圈温度/无损超频语义 + 温度排查
                .appearanceBlock(GTBlocks.CASING_INVAR_HEATPROOF) // 与 EBF 一致的外观方块
                .pattern(GTLModularMachines::blastShellPattern)
                // M1 占位：渲染直接借用 GTM EBF 的贴图（后续换成自有贴图/模型）
                .workableCasingRenderer(
                        gtceuId("block/casings/solid/machine_casing_heatproof"),
                        gtceuId("block/multiblock/electric_blast_furnace"))
                // 说明文字全部由 ModuleTooltipHandler 按当前配置动态生成（每秒扣量/每档倍率可在配置里改）
                // M2：双流体税（熔岩+液态烈焰，每秒各扣，基准 IV、每高一档×4）
                .beforeWorking(DualFluidTax::onBeforeWorking)
                .onWorking(DualFluidTax::onWorking)
                .langValue(kind.displayNameZh())
                .register();
        // 物品说明按当前配置动态生成（每秒扣量 / 每档倍率 / 耗时倍率都能在配置里改）
        MOLTEN_BLAST_MODULE.setTooltipBuilder(MoltenBlastModuleMachine::appendConfiguredTooltips);

        GTLModular.LOGGER.info("[gtlmodular] M1 已注册机器: {} ({})",
                MOLTEN_BLAST_MODULE.getId(), kind.displayNameZh());
    }

    /**
     * M4 模块B：熔岩星核剥离单元（star_core_stripper 表；原机火箭燃料税不沿用，
     * 只吃本模组双流体税；超频用普通电力超频，不要求配方带温度）。
     */
    private static void registerModuleB() {
        ModuleKind kind = ModuleKind.STAR_CORE_STRIPPER;
        GTRecipeType recipeType = GTRegistries.RECIPE_TYPES.get(kind.recipeTypeId());
        if (recipeType == null) {
            GTLModular.LOGGER.warn("[gtlmodular] 跳过模块B {}：配方表 {} 未注册",
                    kind.machineId(), kind.recipeTypeId());
            return;
        }
        MOLTEN_STRIPPER_MODULE = REGISTRATE.multiblock(kind.machineId(), MoltenStripperModuleMachine::new)
                .rotationState(RotationState.ALL)
                .recipeType(recipeType)
                // 休闲电压：配方电压高于机器时降档慢跑(保总能耗)，不出现“未找到配方”
                .recipeModifier(MoltenRelaxedElectricOverclock::apply)
                .appearanceBlock(() -> blockOf("gtlcore:hyper_mechanical_casing"))
                .pattern(GTLModularMachines::stripperShellPattern)
                .workableCasingRenderer(
                        new ResourceLocation("gtlcore", "block/casings/hyper_mechanical_casing"),
                        gtceuId("block/multiblock/fusion_reactor"))
                .beforeWorking(DualFluidTax::onBeforeWorking)
                .onWorking(DualFluidTax::onWorking)
                .langValue(kind.displayNameZh())
                .register();
        // 说明按当前配置/常量动态生成（耗能倍数读 stripper.energyMultiplier，嵌入上限与公式读代码常量）
        MOLTEN_STRIPPER_MODULE.setTooltipBuilder(MoltenStripperModuleMachine::appendConfiguredTooltips);
        GTLModular.LOGGER.info("[gtlmodular] M4 已注册机器: {} ({})",
                MOLTEN_STRIPPER_MODULE.getId(), kind.displayNameZh());
    }

    /** M4 模块C：熔岩天基炼矿单元（space_ore_processor 表，不吃火箭燃料税）。 */
    private static void registerModuleC() {
        ModuleKind kind = ModuleKind.SPACE_ORE_PROCESSOR;
        GTRecipeType recipeType = GTRegistries.RECIPE_TYPES.get(kind.recipeTypeId());
        if (recipeType == null) {
            GTLModular.LOGGER.warn("[gtlmodular] 跳过模块C {}：配方表 {} 未注册",
                    kind.machineId(), kind.recipeTypeId());
            return;
        }
        MOLTEN_SPACE_ORE_MODULE = REGISTRATE.multiblock(kind.machineId(), MoltenSpaceOreModuleMachine::new)
                .rotationState(RotationState.ALL)
                .recipeType(recipeType)
                // 休闲电压：配方电压高于机器时降档慢跑(保总能耗)，不出现“未找到配方”
                .recipeModifier(MoltenRelaxedElectricOverclock::apply)
                .appearanceBlock(() -> blockOf("gtlcore:space_elevator_mechanical_casing"))
                .pattern(GTLModularMachines::spaceShellPattern)
                .workableCasingRenderer(
                        new ResourceLocation("gtlcore", "block/space_elevator_mechanical_casing"),
                        gtceuId("block/multiblock/data_bank"))
                .beforeWorking(DualFluidTax::onBeforeWorking)
                .onWorking(DualFluidTax::onWorking)
                .langValue(kind.displayNameZh())
                .register();
        // 静态说明 + 双流体税行（数字读配置）
        MOLTEN_SPACE_ORE_MODULE.setTooltipBuilder(MoltenSpaceOreModuleMachine::appendConfiguredTooltips);
        GTLModular.LOGGER.info("[gtlmodular] M4 已注册机器: {} ({})",
                MOLTEN_SPACE_ORE_MODULE.getId(), kind.displayNameZh());
    }

    /**
     * 壳格可换的 IO 能力(物品进出+流体进出)，<b>不限数量</b>（结构方块全部可换）+ 可选并行仓(最多1)。
     * 高炉单元 / 星核剥离单元 / 天基炼矿单元都用这套（下限 0，避免变成必需部件）。
     * 注意：禁止用不带下限的 abilities() —— 会把消声/维护/IO 变成必需。
     *
     * <p>改用 {@link com.gtlmodular.common.data.HatchPredicates#ioUnlimited}：匹配语义与
     * {@code Predicates.abilities(...)} 一致，但预览候选每种能力只实例化一台最便宜的仓，
     * 避免在结构预览里把 ME 样板总成整套实例化（那会让服务器 tick 卡几十秒，实测过）。
     */
    private static TraceabilityPredicate ioUnlimited(boolean allowParallel) {
        return com.gtlmodular.common.data.HatchPredicates.ioUnlimited(allowParallel);
    }

    private static Block blockOf(String id) {
        Block b = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(id));
        if (b == null) {
            throw new IllegalStateException("module shell missing block: " + id);
        }
        return b;
    }

    /**
     * 猪咪愤怒烧矿机：殷钢隔热墙 5×4 + 抗氧化哈斯特洛伊合金-N 一层 + 上方铱管道 3 列×3 高
     * （<b>按投影「猪咪愤怒烧矿机.litematic」逐格核对</b>：外壳第 1~4 层，控制器在第 2 层正中；
     * 第 5 层是抗氧化哈斯特洛伊合金-N机械方块；第 6~8 层是管道）。
     * <ul>
     *   <li><b>抗氧化哈斯特洛伊合金-N机械方块为必须</b>：该层 {@code gtlcore:oxidation_resistant_hastelloy_n_mechanical_casing}
     *       不可替换成舱室。</li>
     *   <li><b>铱管道为必须</b>：管道格只能放 {@code gtceu:iridium_huge_fluid_pipe}，不可替换成舱室。</li>
     *   <li><b>所有隔热墙方块都可替换为输入/输出仓</b>（物品/流体进出，不限数量）。</li>
     *   <li><b>可换 {@code gtladditions:thread_modifier_hatch}（Ω-天球分歧引擎）</b>：
     *       该仓里的星阵按 ×20 线程倍率折进本机的跨配方线程预算。</li>
     * </ul>
     */
    private static com.gregtechceu.gtceu.api.pattern.BlockPattern blastShellPattern(
            MultiblockMachineDefinition def) {
        TraceabilityPredicate casing =
                blocks(blockOf("gtceu:heatproof_machine_casing")).or(ioUnlimited(true));
        Block threadHatch = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("gtladditions", "thread_modifier_hatch"));
        if (threadHatch != null) {
            casing = casing.or(blocks(threadHatch));
        }
        FactoryBlockPattern f = FactoryBlockPattern.start()
                .aisle("CCCCC", "CCSCC", "CCCCC", "CCCCC", "HHHHH", "P.P.P", "P.P.P", "P.P.P");
        f = f.where('S', controller(blocks(def.get())));
        f = f.where('C', casing);
        f = f.where('H', blocks(blockOf("gtlcore:oxidation_resistant_hastelloy_n_mechanical_casing")));
        f = f.where('P', blocks(blockOf("gtceu:iridium_huge_fluid_pipe")));
        f = f.where('.', Predicates.any());
        return f.build();
    }

    /**
     * 猪咪黑奴挖矿机：超能机械墙 5×4 + 抗氧化哈斯特洛伊合金-N 一层 + 上方硅岩管道 3 列×3 高
     * （<b>按投影「猪咪黑奴挖矿机.litematic」逐格核对</b>，形状与烧矿机一致）。
     *
     * <ul>
     *   <li><b>不允许并行控制仓</b> —— 并行由本机公式（基础1536 + 主机线圈加成 + 嵌入处理器主机倍率）给出。</li>
     *   <li><b>抗氧化哈斯特洛伊合金-N机械方块为必须</b>：该层不可替换成舱室。</li>
     *   <li><b>硅岩管道为必须</b>：管道格只能放 {@code gtceu:naquadah_huge_fluid_pipe}，不可替换成舱室。</li>
     *   <li><b>所有超能机械方块都可替换为输入/输出仓</b>（物品/流体进出，不限数量）。</li>
     * </ul>
     */
    private static com.gregtechceu.gtceu.api.pattern.BlockPattern stripperShellPattern(
            MultiblockMachineDefinition def) {
        // 壳格：任意一格都能换成 输入/输出 物品或流体仓（下限 0、不限上限）
        TraceabilityPredicate casing =
                blocks(blockOf("gtlcore:hyper_mechanical_casing")).or(ioUnlimited(false));
        // 管道格：必须为硅岩管道，不可替换
        TraceabilityPredicate pipe = blocks(blockOf("gtceu:naquadah_huge_fluid_pipe"));
        // 单个切片、8 行高（5宽 × 8高 × 1深）
        FactoryBlockPattern f = FactoryBlockPattern.start()
                .aisle("CCCCC", "CCSCC", "CCCCC", "CCCCC", "HHHHH", "P.P.P", "P.P.P", "P.P.P");
        f = f.where('S', controller(blocks(def.get())));
        f = f.where('C', casing);
        f = f.where('H', blocks(blockOf("gtlcore:oxidation_resistant_hastelloy_n_mechanical_casing")));
        f = f.where('P', pipe);
        f = f.where('.', Predicates.any());
        return f.build();
    }

    /**
     * 猪咪撕咬炼矿机（天基炼矿单元）：<b>投影里的那根太空电梯塔</b>
     * （15宽 × 31高 × 11深，控制器在塔身 (48,22,58)，机器 facing=south）。
     *
     * <p>结构由 {@link SpaceOrePatternLayers} 自动生成，逐格规则：
     * <ul>
     *   <li>'C' 太空电梯机械方块 —— <b>可换</b>输入/输出仓与并行仓（不限数量）；</li>
     *   <li>'I' 太空电梯内部支撑结构 —— <b>必须</b>，不可替换；</li>
     *   <li>'B' 太空电梯模块基座 —— <b>必须</b>，不可替换；</li>
     *   <li>'P' 太空电梯动力模块槽 —— <b>必须</b>放动力模块，MK1~MK5 可互换（换级立即生效）；</li>
     *   <li>'.' 其余方块（混凝土 / 铱外壳 / 太空电梯模块连接器 / 空气…）不限，装饰自由摆放。</li>
     * </ul>
     * 跨配方线程数 = 结构里最高等级的动力模块（MK1 = 64，每升一级 ×4）。
     */
    private static com.gregtechceu.gtceu.api.pattern.BlockPattern spaceShellPattern(
            MultiblockMachineDefinition def) {
        // 机械方块格：可换输入/输出仓与并行仓
        TraceabilityPredicate casing = SpaceOreModulePattern.casing().or(ioUnlimited(true));
        FactoryBlockPattern f = FactoryBlockPattern.start();
        for (String[] aisle : SpaceOrePatternLayers.AISLES) {
            f = f.aisle(aisle);
        }
        f = f.where('S', controller(blocks(def.get())));
        f = f.where('C', casing);
        f = f.where('I', SpaceOreModulePattern.internalSupport());
        f = f.where('B', SpaceOreModulePattern.moduleBase());
        f = f.where('P', SpaceOreModulePattern.powerSlot());
        f = f.where('.', Predicates.any());
        return f.build();
    }

    private static ResourceLocation gtceuId(String path) {
        return new ResourceLocation("gtceu", path);
    }
}
