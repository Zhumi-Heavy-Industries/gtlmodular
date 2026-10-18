package com.gtlmodular.common.data;

import com.gtlmodular.GTLModular;
import com.gtlmodular.common.data.nanochip.NanochipRegistration;
import com.gtlmodular.common.diag.SwallowedErrors;
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

/** 机器注册必须等待 GTCEu 的注册事件；构造期和 addon 初始化都不在 MACHINES 的可写窗口内。 */
public final class GTLModularMachines {

    private GTLModularMachines() {
    }

    public static final GTRegistrate REGISTRATE = GTRegistrate.create(GTLModular.MOD_ID);

    public static final RegistryEntry<CreativeModeTab> CREATIVE_TAB;

    static {
        // GTRegistrate 只把设置当前创造标签页之后的条目记入 TAB_LOOKUP，因此必须先绑定标签页。
        CREATIVE_TAB = REGISTRATE.defaultCreativeTab(GTLModular.MOD_ID, builder -> {
            builder.displayItems(
                    new GTCreativeModeTabs.RegistrateDisplayItemsGenerator(GTLModular.MOD_ID, REGISTRATE));
            builder.title(REGISTRATE.addLang("itemGroup",
                    new ResourceLocation(GTLModular.MOD_ID, "creative_tab"),
                    "GTL·熔岩模块化机组"));
            // 延迟取主机图标；类名限定访问避免静态初始化中的非法前向引用。
            builder.icon(() -> GTLModularMachines.MOLTEN_MODULE_HOST == null
                    ? new ItemStack(Items.LAVA_BUCKET)
                    : GTLModularMachines.MOLTEN_MODULE_HOST.asStack());
            builder.build();
        }).register();
        REGISTRATE.creativeModeTab(() -> CREATIVE_TAB);
    }

    private static boolean initialized = false;

    public static MultiblockMachineDefinition MOLTEN_BLAST_MODULE;

    public static MultiblockMachineDefinition MOLTEN_STRIPPER_MODULE;

    public static MultiblockMachineDefinition MOLTEN_SPACE_ORE_MODULE;

    public static MultiblockMachineDefinition MOLTEN_MODULE_HOST;

    public static final int PATTERN_ASSEMBLY_MIN_TIER = 2;
    public static final int PATTERN_ASSEMBLY_MAX_TIER = 11;

    public static final java.util.Map<Integer, MachineDefinition> PATTERN_ASSEMBLY = new java.util.LinkedHashMap<>();

    public static final int SUPER_PATTERN_ASSEMBLY_TIER = 10;
    public static final int SUPER_PATTERN_ASSEMBLY_PAGES = MoltenPatternAssemblyPartMachine.MAX_PAGES;
    public static MachineDefinition SUPER_PATTERN_ASSEMBLY;

    public static MachineDefinition LAZY_PATTERN_ASSEMBLY;
    public static final int LAZY_PATTERN_ASSEMBLY_TIER = PATTERN_ASSEMBLY_MIN_TIER;

    /** 使用具体事件类型的方法引用，避免泛型重载把监听器挂到 mod 总线不接受的基类事件。 */
    public static void registerEventListeners(IEventBus modBus) {
        REGISTRATE.registerEventListeners(modBus);
        modBus.addGenericListener(MachineDefinition.class, GTLModularMachines::onMachineRegisterEvent);
    }

    private static void onMachineRegisterEvent(
            GTCEuAPI.RegisterEvent<ResourceLocation, MachineDefinition> event) {
        init();
    }

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
        registerLazyPatternAssembly();
        registerBufferedOutputAssembly();
        NanochipRegistration.registerMachines();
    }

    /** Registrate 方块与物品条目必须在 Forge 注册事件前创建，不能与机器注册窗口互换。 */
    public static void initBlocks() {
        NanochipRegistration.initBlocks();
        com.gtlmodular.common.item.PatternArchiveItem.register();
    }

    private static void registerPatternAssemblies() {
        for (int tier = PATTERN_ASSEMBLY_MIN_TIER; tier <= PATTERN_ASSEMBLY_MAX_TIER; tier++) {
            registerPatternAssembly(tier);
        }
    }

    private static void registerPatternAssembly(int tier) {
        String tierName = MoltenPatternAssemblyPartMachine.tierName(tier);
        // 无线 ME 按路径识别目标，保留 _me_ 才能被上游判为可连接部件。
        String id = tierName.toLowerCase(java.util.Locale.ROOT) + "_me_pattern_assembly";
        int slots = MoltenPatternAssemblyPartMachine.slotsForTier(tier);
        PATTERN_ASSEMBLY.put(tier, REGISTRATE.machine(id,
                        holder -> new MoltenPatternAssemblyPartMachine(holder, tier, slots))
                .rotationState(RotationState.ALL)
                .abilities(PartAbility.IMPORT_ITEMS, PartAbility.EXPORT_ITEMS,
                        PartAbility.IMPORT_FLUIDS, PartAbility.EXPORT_FLUIDS)
                .tier(tier)
                .renderer(() -> new com.gregtechceu.gtceu.client.renderer.machine.OverlayTieredMachineRenderer(
                        tier, new ResourceLocation("gtceu", "block/machine/part/me_pattern_buffer")))
                .langValue(tierName + "猪咪样板总成")
                .tooltips(patternAssemblyTooltips(slots))
                .register());
        GTLModular.LOGGER.info("[gtlmodular] 已注册样板总成: {} 猪咪样板总成(tier={}, 样板槽={})", tierName, tier, slots);
    }

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

    private static void registerSuperPatternAssembly() {
        int slots = SUPER_PATTERN_ASSEMBLY_PAGES * MoltenPatternAssemblyPartMachine.PER_PAGE;
        SUPER_PATTERN_ASSEMBLY = REGISTRATE.machine("super_me_pattern_assembly",
                        holder -> new MoltenPatternAssemblyPartMachine(holder, SUPER_PATTERN_ASSEMBLY_TIER, slots))
                .rotationState(RotationState.ALL)
                .abilities(PartAbility.IMPORT_ITEMS, PartAbility.EXPORT_ITEMS,
                        PartAbility.IMPORT_FLUIDS, PartAbility.EXPORT_FLUIDS)
                .tier(SUPER_PATTERN_ASSEMBLY_TIER)
                .renderer(GTLModularMachines::superPatternRenderer)
                .langValue("超级猪咪样板总成")
                .tooltips(patternAssemblyTooltips(slots))
                .register();
        GTLModular.LOGGER.info("[gtlmodular] 已注册超级样板总成: 超级猪咪样板总成(tier={}, 样板槽={})",
                SUPER_PATTERN_ASSEMBLY_TIER, slots);
    }

    private static com.lowdragmc.lowdraglib.client.renderer.IRenderer superPatternRenderer() {
        ResourceLocation overlay = new ResourceLocation("gtceu", "block/machine/part/me_pattern_buffer");
        try {
            return new com.gtlmodular.client.render.BlendingPatternHullRenderer(
                    new ResourceLocation("gtladditions", "block/casings/infinity_input_dual_hatch_casing"),
                    overlay);
        } catch (Throwable ignored) {
            SwallowedErrors.report("GTLModularMachines#superPatternRenderer", ignored);
            return new com.gregtechceu.gtceu.client.renderer.machine.OverlayTieredMachineRenderer(
                    SUPER_PATTERN_ASSEMBLY_TIER, overlay);
        }
    }

    private static void registerLazyPatternAssembly() {
        int tier = LAZY_PATTERN_ASSEMBLY_TIER;
        int maxSlots = MoltenPatternAssemblyPartMachine.slotsForTier(tier);
        LAZY_PATTERN_ASSEMBLY = REGISTRATE.machine("lazy_me_pattern_assembly",
                        holder -> new LazyPatternAssemblyPartMachine(holder, tier, maxSlots))
                .rotationState(RotationState.ALL)
                .abilities(PartAbility.IMPORT_ITEMS, PartAbility.EXPORT_ITEMS,
                        PartAbility.IMPORT_FLUIDS, PartAbility.EXPORT_FLUIDS)
                .tier(tier)
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

    /** 复用 gtlcore 已有的 ME 扩展输出 overlay，不新增贴图资源。 */
    private static void registerBufferedOutputAssembly() {
        REGISTRATE.machine("buffered_me_output_buffer", com.gtlmodular.common.machine.output.BufferedMEOutputPartMachine::new)
                .rotationState(RotationState.ALL)
                .abilities(PartAbility.EXPORT_ITEMS, PartAbility.EXPORT_FLUIDS)
                .tier(9)
                .renderer(() -> new com.gregtechceu.gtceu.client.renderer.machine.OverlayTieredMachineRenderer(
                        9, new ResourceLocation("gtceu", "block/machine/part/me_extended_export_buffer")))
                .langValue("ME Buffered Export Buffer")
                .register();
        GTLModular.LOGGER.info("[gtlmodular] 已注册带合并的 ME 输出总成: id={} tier=9", "buffered_me_output_buffer");
    }

    private static void registerModuleHost() {
        ModuleKind kind = ModuleKind.HOST;
        MOLTEN_MODULE_HOST = REGISTRATE.multiblock(kind.machineId(), MoltenModuleHostMachine::new)
                .rotationState(RotationState.NON_Y_AXIS)
                // 使用空配方表取得 workable 界面，同时避免主机自身执行加工。
                .recipeType(GTRecipeTypes.DUMMY_RECIPES)
                .pattern(MoltenModuleHostPattern::build)
                .appearanceBlock(() -> blockOf("gtlcore:iridium_casing"))
                .workableCasingRenderer(
                        new ResourceLocation("gtlcore", "block/casings/iridium_casing"),
                        gtceuId("block/multiblock/data_bank"))
                .tooltips(
                        Component.translatable("block.gtlmodular.molten_module_host.desc"))
                .langValue(kind.displayNameZh())
                .register();
        GTLModular.LOGGER.info("[gtlmodular] M5 已注册机器: {} ({})",
                MOLTEN_MODULE_HOST.getId(), kind.displayNameZh());
    }

    /** 保留温度拒绝的读数，便于区分线圈读取失败与实际炉温不足。 */
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

    private static void registerModuleA() {
        ModuleKind kind = ModuleKind.BLAST;
        MOLTEN_BLAST_MODULE = REGISTRATE.multiblock(kind.machineId(), MoltenBlastModuleMachine::new)
                .rotationState(RotationState.ALL)
                .recipeType(GTRecipeTypes.BLAST_RECIPES)
                .recipeModifier(GTLModularMachines::moltenEbfOverclock)
                .appearanceBlock(GTBlocks.CASING_INVAR_HEATPROOF)
                .pattern(GTLModularMachines::blastShellPattern)
                .workableCasingRenderer(
                        gtceuId("block/casings/solid/machine_casing_heatproof"),
                        gtceuId("block/multiblock/electric_blast_furnace"))
                .beforeWorking(DualFluidTax::onBeforeWorking)
                .onWorking(DualFluidTax::onWorking)
                .langValue(kind.displayNameZh())
                .register();
        MOLTEN_BLAST_MODULE.setTooltipBuilder(MoltenBlastModuleMachine::appendConfiguredTooltips);

        GTLModular.LOGGER.info("[gtlmodular] M1 已注册机器: {} ({})",
                MOLTEN_BLAST_MODULE.getId(), kind.displayNameZh());
    }

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
        MOLTEN_STRIPPER_MODULE.setTooltipBuilder(MoltenStripperModuleMachine::appendConfiguredTooltips);
        GTLModular.LOGGER.info("[gtlmodular] M4 已注册机器: {} ({})",
                MOLTEN_STRIPPER_MODULE.getId(), kind.displayNameZh());
    }

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
        MOLTEN_SPACE_ORE_MODULE.setTooltipBuilder(MoltenSpaceOreModuleMachine::appendConfiguredTooltips);
        GTLModular.LOGGER.info("[gtlmodular] M4 已注册机器: {} ({})",
                MOLTEN_SPACE_ORE_MODULE.getId(), kind.displayNameZh());
    }

    /** IO 下限设为零以保持可选；预览复用轻量候选，避免实例化整套 ME 样板仓。 */
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

    private static com.gregtechceu.gtceu.api.pattern.BlockPattern stripperShellPattern(
            MultiblockMachineDefinition def) {
        TraceabilityPredicate casing =
                blocks(blockOf("gtlcore:hyper_mechanical_casing")).or(ioUnlimited(false));
        TraceabilityPredicate pipe = blocks(blockOf("gtceu:naquadah_huge_fluid_pipe"));
        FactoryBlockPattern f = FactoryBlockPattern.start()
                .aisle("CCCCC", "CCSCC", "CCCCC", "CCCCC", "HHHHH", "P.P.P", "P.P.P", "P.P.P");
        f = f.where('S', controller(blocks(def.get())));
        f = f.where('C', casing);
        f = f.where('H', blocks(blockOf("gtlcore:oxidation_resistant_hastelloy_n_mechanical_casing")));
        f = f.where('P', pipe);
        f = f.where('.', Predicates.any());
        return f.build();
    }

    private static com.gregtechceu.gtceu.api.pattern.BlockPattern spaceShellPattern(
            MultiblockMachineDefinition def) {
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
