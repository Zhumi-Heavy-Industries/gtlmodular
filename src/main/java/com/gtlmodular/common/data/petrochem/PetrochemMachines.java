package com.gtlmodular.common.data.petrochem;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.common.data.GTRecipeModifiers;
import com.gtlmodular.GTLModular;
import com.gtlmodular.common.data.GTLModularMachines;
import com.gtlmodular.common.data.woodfactory.WoodFactoryBlocks;
import com.gtlmodular.common.machine.fuel.OverclockProfiles;
import com.gtlmodular.common.machine.petrochem.FuelRefiningComplexMachine;
import com.gtlmodular.common.machine.petrochem.PetrochemicalPlantMachine;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 石化线两台 GTO 机器的注册：
 * <ul>
 *   <li>{@code gtlmodular:petrochemical_plant}——<b>集成石油化工处理工厂</b>
 *       （GTO「石化工厂」{@code gtocore:petrochemical_plant} 的移植体，<b>按队长口径改名</b>）；</li>
 *   <li>{@code gtlmodular:fuel_refining_complex}——<b>燃料精炼复合体</b>
 *       （GTO 同名机器，<b>机器本体不做改动</b>）。</li>
 * </ul>
 *
 * <h2>配方表：都指向整合包现有的表，本模组不新造表、不复制配方</h2>
 * <table border="1">
 *   <caption>队长 2026-11-26 口径</caption>
 *   <tr><th>机器</th><th>配方表</th><th>为什么</th></tr>
 *   <tr><td>集成石油化工处理工厂</td><td>{@code gtceu:petrochemical_plant}</td>
 *       <td>包里 gtlcore 已注册该表，KubeJS {@code petrochemistry.js} 也注入了 4 条蒸汽配方
 *           （oil / oil_medium / oil_heavy / oil_light + 蒸汽 → 12 种产物）。本模组<b>只补 GTO 的
 *           4 条氢化配方</b>（{@code petrochemical_hydro1..4}），不重复造表、JEI 不出两页。</td></tr>
 *   <tr><td>燃料精炼复合体</td><td>{@code gtceu:fuel_refining}</td>
 *       <td>队长原话「它的配方，就用我们整合包中的燃料精炼」：包里 gtlcore 已注册该表，
 *           KubeJS {@code nuclear_rocket_fuels.js} 已有整套燃料精炼配方（含
 *           {@code stellar_energy_rocket_fuel}）。GTO 自己那份 {@code FuelRefining.java}
 *           大量引用 GTOCore 独有材料（Cetane / HmxExplosive / RocketFuelCn3h7o3 …），
 *           <b>不移植</b>。</td></tr>
 * </table>
 * <p>两张表都在 {@code gtceu:} 命名空间，由 gtlcore 在 {@code GTRecipeTypes.init()} 里注册，
 * 早于本类运行的 {@code MachineDefinition} 窗口 ⇒ 注册期按 id 现取即可；取不到时打 WARN 并跳过
 * 该机器（不让整包因为一张表缺失而崩）。
 *
 * <h2>超频口径：两台机器故意不同</h2>
 * <ul>
 *   <li>集成石油化工处理工厂：{@code OverclockProfiles.apply}（本模组统一入口，完美超频 + 亚 tick）。
 *       它的配方（{@code petrochemical_plant}）不带 {@code ebf_temp}，GTO 原机也是纯线圈并行、无温度闸。</li>
 *   <li>燃料精炼复合体：{@code GTRecipeModifiers.ebfOverclock} —— GTO 原机
 *       {@code createCheckedTemperatureParallel(true)} 的「温度闸」语义由它给出：
 *       {@code ebf_temp > 线圈温度 + 100×max(0, 档位-MV)} 时直接 return null（停机），
 *       与机器 tooltip 第一行「超过 MV 后的每一级电压，都会将温度提高 100K」逐字对应。</li>
 * </ul>
 *
 * <p>注册调用点：{@link GTLModularMachines#init()}。
 */
public final class PetrochemMachines {

    /** 控制器外观方块 = 高能机器外壳（GTO 两台机器都是 {@code .block(GTBlocks.HIGH_POWER_CASING)}）。 */
    public static final ResourceLocation CASING_TEXTURE =
            new ResourceLocation("gtceu", "block/casings/hpca/high_power_casing");
    /** 石化工厂正面覆盖层（GTO 原机的 {@code block/multiblock/fusion_reactor}）。 */
    public static final ResourceLocation PETROCHEM_OVERLAY =
            new ResourceLocation("gtceu", "block/multiblock/fusion_reactor");
    /** 燃料精炼复合体正面覆盖层（GTO 原机的 {@code block/multiblock/gcym/large_brewer}）。 */
    public static final ResourceLocation FUEL_OVERLAY =
            new ResourceLocation("gtceu", "block/multiblock/gcym/large_brewer");

    /** 集成石油化工处理工厂：GTO 石化工厂的移植体（改名）。 */
    public static MultiblockMachineDefinition PETROCHEMICAL_PLANT;
    /** 燃料精炼复合体：GTO 同名机器，本体不改。 */
    public static MultiblockMachineDefinition FUEL_REFINING_COMPLEX;

    /** 工具提示行数（与 lang 里的 {@code gtlmodular.petrochemical_plant.tooltip.N} 对应）。 */
    private static final int PETROCHEM_TOOLTIP_LINES = 8;
    /** 工具提示行数（与 lang 里的 {@code gtlmodular.fuel_refining_complex.tooltip.N} 对应）。 */
    private static final int FUEL_TOOLTIP_LINES = 3;

    private PetrochemMachines() {
    }

    public static void register() {
        registerPetrochemicalPlant();
        registerFuelRefiningComplex();
    }

    // ------------------------------------------------------------------ 集成石油化工处理工厂

    private static void registerPetrochemicalPlant() {
        GTRecipeType recipeType = GTRegistries.RECIPE_TYPES
                .get(new ResourceLocation("gtceu", "petrochemical_plant"));
        if (recipeType == null) {
            GTLModular.LOGGER.warn("[gtlmodular] 跳过集成石油化工处理工厂：配方表 gtceu:petrochemical_plant "
                    + "未注册（gtlcore 缺失？）");
            return;
        }

        PETROCHEMICAL_PLANT = GTLModularMachines.REGISTRATE
                .multiblock("petrochemical_plant", holder -> new PetrochemicalPlantMachine(holder,
                        () -> PetrochemicalPlantStructures.buildUpgraded(PETROCHEMICAL_PLANT),
                        PetrochemicalPlantStructures::upgradedSlices, "!#$%&()*123456789"))
                // GTO 的 .nonYAxisRotation()
                .rotationState(RotationState.NON_Y_AXIS)
                .tier(GTValues.UEV)
                .recipeType(recipeType)
                // 不挂 GTRecipeModifiers.PARALLEL_HATCH：本机由独立线程引擎接管，
                // 并行在引擎 findBatch() 里按「线圈并行 × 并行仓」算一次（与木化工厂同口径）。
                .recipeModifier((machine, recipe, params, result) ->
                        OverclockProfiles.apply(machine, recipe, params, result))
                .appearanceBlock(PetrochemMachines::appearanceBlock)
                .pattern(PetrochemicalPlantStructures::buildUpgraded)
                .workableCasingRenderer(CASING_TEXTURE, PETROCHEM_OVERLAY)
                .langValue("集成石油化工处理工厂")
                .tooltips(tooltips("petrochemical_plant", PETROCHEM_TOOLTIP_LINES))
                .register();

        GTLModular.LOGGER.info("[gtlmodular] 已注册集成石油化工处理工厂：{}（28×60×65 / 配方表 {} / "
                        + "线圈并行 2^(温度/900) / 激光仓 / H 面可装线程·超频·加速·并行仓）",
                PETROCHEMICAL_PLANT.getId(), recipeType.registryName);
    }

    // ------------------------------------------------------------------ 燃料精炼复合体

    private static void registerFuelRefiningComplex() {
        GTRecipeType recipeType = GTRegistries.RECIPE_TYPES
                .get(new ResourceLocation("gtceu", "fuel_refining"));
        if (recipeType == null) {
            GTLModular.LOGGER.warn("[gtlmodular] 跳过燃料精炼复合体：配方表 gtceu:fuel_refining "
                    + "未注册（gtlcore 缺失？）");
            return;
        }

        FUEL_REFINING_COMPLEX = GTLModularMachines.REGISTRATE
                .multiblock("fuel_refining_complex", holder -> new FuelRefiningComplexMachine(holder,
                        () -> FuelRefiningComplexStructures.buildUpgraded(FUEL_REFINING_COMPLEX),
                        FuelRefiningComplexStructures::upgradedSlices, "!#$013456789"))
                .rotationState(RotationState.NON_Y_AXIS)
                .tier(GTValues.UEV)
                .recipeType(recipeType)
                // GTO 的 createCheckedTemperatureParallel(true)：温度闸交给 GTCEu 自己的 ebfOverclock，
                // 并行由 FuelRefiningComplexMachine#parallelMultiplier 用同一温度式子给出。
                .recipeModifier(GTRecipeModifiers::ebfOverclock)
                .appearanceBlock(PetrochemMachines::appearanceBlock)
                .pattern(FuelRefiningComplexStructures::buildUpgraded)
                .workableCasingRenderer(CASING_TEXTURE, FUEL_OVERLAY)
                .langValue("燃料精炼复合体")
                .tooltips(tooltips("fuel_refining_complex", FUEL_TOOLTIP_LINES))
                .register();

        GTLModular.LOGGER.info("[gtlmodular] 已注册燃料精炼复合体：{}（31×27×63 / 配方表 {} / "
                        + "并行 2^(有效温度/900) / ebf_temp 温度闸 / ^ 面可装线程·超频·并行仓）",
                FUEL_REFINING_COMPLEX.getId(), recipeType.registryName);
    }

    // ------------------------------------------------------------------ 公共

    /** 工具提示：全部走 lang 键，注册期不拼字符串。 */
    private static Component[] tooltips(String machine, int lines) {
        Component[] result = new Component[lines];
        for (int i = 0; i < lines; i++) {
            result[i] = Component.translatable("gtlmodular." + machine + ".tooltip." + i);
        }
        return result;
    }

    /** 控制器外观方块：高能机器外壳；取不到时退回木化工厂那轮的耐压机壳（不让注册期 NPE）。 */
    private static Block appearanceBlock() {
        Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("gtceu", "high_power_casing"));
        if (block == null || block == Blocks.AIR) {
            GTLModular.LOGGER.warn("[gtlmodular] 石化线：gtceu:high_power_casing 不存在，"
                    + "控制器外观退回 gtlmodular:pressure_containment_casing");
            return WoodFactoryBlocks.PRESSURE_CONTAINMENT_CASING.get();
        }
        return block;
    }
}
