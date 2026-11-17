package com.gtlmodular.common.data;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.FactoryBlockPattern;
import com.gregtechceu.gtceu.api.pattern.MultiblockShapeInfo;
import com.gregtechceu.gtceu.api.pattern.Predicates;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.OverclockingLogic;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.api.recipe.logic.OCParams;
import com.gregtechceu.gtceu.api.recipe.logic.OCResult;
import com.gregtechceu.gtceu.api.recipe.modifier.RecipeModifier;
import com.gregtechceu.gtceu.api.recipe.modifier.RecipeModifierList;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.common.data.GTBlocks;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.data.GTRecipeModifiers;
import com.gtlmodular.GTLModular;
import com.gtlmodular.common.config.GTLModularConfig;
import com.gtlmodular.common.machine.multiblock.CoilLargeChemicalReactorMachine;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 大型化学反应釜「吃线圈加成」的收尾配置 —— 2026-11-09。
 *
 * <h2>背景（玩家口径：照抄 GTO 的大型化学反应釜）</h2>
 * 本包的 gtceu 是 <b>1.4.4</b>，{@code GTMachines.LARGE_CHEMICAL_REACTOR} 的三格结构里：
 * <pre>
 *   .where('C', Predicates.blocks(GTBlocks.COIL_CUPRONICKEL.get()).setExactLimit(1).or(abilities).or(casing))
 * </pre>
 * 只能放<b>白铜线圈</b>，而且机器类是 {@code WorkableElectricMultiblockMachine} ——
 * 线圈对机器没有任何作用，纯装饰。
 *
 * <p>GTO（gtocore 0.6）的 {@code LargeChemicalReactorMachine} 继承 gtolib 的
 * {@code CoilMultiblockMachine}，{@code getRealRecipe} 里按
 * {@code (getCoilTier() + 2 > recipe.tier) ? 0.25 : 0.5} 决定<b>每一次超频的耗时倍率</b>
 * （0.25 = 无损超频 / 0.5 = 普通超频），描述就是「线圈等级不低于运行的配方等级时启用无损超频」。
 *
 * <h2>本类做的五件事（全部走 definition setter，不动 gtceu 源码、不写 mixin）</h2>
 * <ol>
 *   <li><b>换机器类</b>：{@code setMachineSupplier(CoilLargeChemicalReactorMachine::new)} ——
 *       让机器是「线圈机」，并以结构里的实际方块读线圈等级（含换线圈后的自愈巡检）；</li>
 *   <li><b>线圈位收成「背面正中」一个</b>：{@code setPatternFactory(...)} ——
 *       3×3×3 形状不变，但线圈槽只在<b>背面正中心</b>（原版 + 我们 0.3.362 都是「背面中心 + 中层四个角」
 *       共 5 个位置能放线圈，终端/JEI 预览因此画出 5 个面都带线圈；玩家 2026-11-09 要求收成一个）；</li>
 *   <li><b>预览也收成一个</b>：{@code setShapes(...)} —— 终端/JEI 的多方块预览只保留「背面正中放线圈」
 *       那一种摆法，不再轮播 5 种；</li>
 *   <li><b>按线圈选超频档</b>：{@code setRecipeModifier(new RecipeModifierList(...))} ——
 *       保留原定义的环境要求，把原来的固定「完美超频」换成「按线圈等级选 0.25 / 0.5 的
 *       每次超频耗时倍率」（GTO 口径）；</li>
 *   <li><b>描述照抄 GTO</b>：用 GTO 的同一条文案替换 tooltip（
 *       {@code gtocore.machine.large_chemical_reactor.tooltip.0}：
 *       「线圈等级不低于运行的配方等级时启用无损超频」），并保留原本的环境要求那一行；
 *       gtlcore 之前追加的静态「完美超频」一行同时撤掉（现在超频档位取决于线圈，静态描述已不准确）。</li>
 * </ol>
 *
 * <h2>为什么可以用 setter 而不是 mixin</h2>
 * 机器类的同类先例：{@code StellarForgeDefinitionSetup}（本模组 0.3.328 起就在机器注册窗口换
 * {@code gtceu:stellar_forge} 的机器工厂）。gtceu 1.4.4 的 {@code MachineDefinition} 自带
 * {@code setMachineSupplier / setRecipeModifier / setPatternFactory / setTooltipBuilder}
 * （反编译核对），图案是惰性取的（{@code IMultiController#getPattern()} 每次
 * {@code definition.getPatternFactory().get()}）⇒ 注册窗口里换掉即可。
 *
 * <h2>调用时机</h2>
 * 主入口在 {@code GTLModularMachines.init()}（GTM 的机器注册窗口，gtceu 的 GTMachines 已注册完）；
 * 兜底在 {@code FMLLoadCompleteEvent}（若极端加载顺序下取不到定义）。两处都幂等。
 */
public final class LargeChemicalReactorCoilSetup {

    private static final ResourceLocation LCR_ID = GTCEu.id("large_chemical_reactor");

    /** GTO 的大化反描述键（原文照抄；数值口径见 {@link CoilLargeChemicalReactorMachine}）。 */
    private static final String TOOLTIP_KEY = "gtlmodular.machine.large_chemical_reactor.tooltip.0";

    /** 幂等闸门：早窗口配置成功（或确认关闭）后，FMLLoadComplete 的兜底不再重复换类/换图案。 */
    private static boolean configured;

    private LargeChemicalReactorCoilSetup() {
    }

    /**
     * 主入口：由 {@code GTLModularMachines.init()}（GTM 机器注册窗口内）调用。
     *
     * <p>此时 {@code GTMachines.LARGE_CHEMICAL_REACTOR} 一定已注册（gtceu 的 GTMachines.init
     * 先于该 RegisterEvent 跑完），用 {@link GTRegistries#MACHINES} 按键取定义比直接读字段更稳
     * （以后若 gtceu 升版把字段挪到 GTMultiMachines 也照样能取到）。
     */
    public static void configureNow() {
        if (configured) {
            return;
        }
        if (!GTLModularConfig.isLcrCoilEnabled()) {
            configured = true;
            GTLModular.LOGGER.info("[gtlmodular] 大型化学反应釜线圈加成：配置 lcrCoil.enabled=false ⇒ 保持 gtceu 原样（白铜线圈、无加成）");
            return;
        }
        MachineDefinition definition = GTRegistries.MACHINES.get(LCR_ID);
        if (!(definition instanceof MultiblockMachineDefinition lcr)) {
            GTLModular.LOGGER.error("[gtlmodular] 找不到 {}（gtceu 未加载或改了注册 id？）⇒ 大型化学反应釜线圈加成跳过", LCR_ID);
            return;
        }
        configured = true;

        // 1) 换机器类：线圈机（成型时读 "CoilType"，界面显示线圈等级）。
        lcr.setMachineSupplier(CoilLargeChemicalReactorMachine::new);

        // 2) 线圈位收成「背面正中」一个：形状不变，其他地方全部改回外壳/仓室。
        lcr.setPatternFactory(() -> buildPattern(lcr));
        // 2.5) JEI / 终端结构预览同步成一种摆法（线圈在背面正中），不再轮播 5 种。
        applyShapes(lcr);

        // 3) 配方修正器：环境要求照旧；原来的固定完美超频 → 按线圈等级选 0.25 / 0.5 的每次超频倍率。
        lcr.setRecipeModifier(new RecipeModifierList(
                GTRecipeModifiers.DEFAULT_ENVIRONMENT_REQUIREMENT,
                LargeChemicalReactorCoilSetup::applyCoilOverclock));

        // 4) 描述：整段换成 GTO 的（环境要求 + GTO 同一条线圈文案）。
        applyGtoTooltip(lcr);

        GTLModular.LOGGER.info(
                "[gtlmodular] 大型化学反应釜：已接入 GTO 口径的线圈加成（任意加热线圈；线圈等级 + {} > 配方等级时无损超频 ×{}，否则普通超频 ×{}）",
                GTLModularConfig.getLcrCoilTierAdvantage(),
                fmt(GTLModularConfig.getLcrCoilPerfectOverclockFactor()),
                fmt(GTLModularConfig.getLcrCoilNormalOverclockFactor()));
    }

    /**
     * 兜底：若机器注册窗口里没配上（极端加载顺序），在 FMLLoadComplete 再试一次；
     * 并且**无条件**重刷一次描述 —— 保证 gtlcore 等模组在注册窗口之后追加的旧文案不会残留。
     */
    @SubscribeEvent
    public static void onLoadComplete(FMLLoadCompleteEvent event) {
        event.enqueueWork(() -> {
            if (!configured) {
                GTLModular.LOGGER.warn("[gtlmodular] 大型化学反应釜：机器注册窗口没配上，改在 FMLLoadComplete 兜底");
                configureNow();
            }
            MachineDefinition definition = GTRegistries.MACHINES.get(LCR_ID);
            if (definition instanceof MultiblockMachineDefinition lcr && GTLModularConfig.isLcrCoilEnabled()) {
                applyGtoTooltip(lcr);
            }
        });
    }

    /**
     * 描述照抄 GTO：
     * <ol>
     *   <li>环境要求一行（gtceu 1.4.4 原定义自带，这里保留）；</li>
     *   <li>GTO 原文 —— 「线圈等级不低于运行的配方等级时启用无损超频」
     *       （{@code gtocore.machine.large_chemical_reactor.tooltip.0} 的逐字翻译）。</li>
     * </ol>
     * 直接 {@code setTooltipBuilder}（替换而非追加）：gtlcore 的 {@code TootipsModify} 之前
     * 在大化反描述里追加过静态的「完美超频」一行，现在超频档位取决于线圈，那行已经不准，撤掉。
     */
    private static void applyGtoTooltip(@NotNull MultiblockMachineDefinition lcr) {
        lcr.setTooltipBuilder((stack, tooltip) -> {
            tooltip.add(GTMachines.defaultEnvironmentRequirement());
            tooltip.add(Component.translatable(TOOLTIP_KEY));
        });
    }

    /**
     * 大型化学反应釜的 3×3×3 图案 —— <b>线圈槽只在背面正中一个</b>（2026-11-09 玩家要求）。
     *
     * <p>对照 gtceu 1.4.4 {@code GTMachines.LARGE_CHEMICAL_REACTOR}（反编译核对；GTCEu 26.x 同款）：
     * 原版是
     * <pre>
     *   aisle0: "XXX","XCX","XXX"      ← 背面：正中可放线圈
     *   aisle1: "XCX","CPC","XCX"      ← 中层：四个角也能放线圈
     *   aisle2: "XXX","XSX","XXX"      ← 正面：控制器
     * </pre>
     * 也就是说原来「背面正中 + 中层四个角」一共 5 个位置都能放线圈，结构预览因此有 5 种摆法
     * （玩家看到「5 个面都带线圈」）。这里收成一个：
     * <pre>
     *   aisle0: "XXX","XCX","XXX"      ← 背面：正中唯一线圈槽（必须是加热线圈）
     *   aisle1: "XXX","XPX","XXX"      ← 中层：四个角改回外壳/仓室
     *   aisle2: "XXX","XSX","XXX"      ← 正面：控制器
     * </pre>
     * 外壳/仓室/中心 PTFE 管道等其余约束与原版完全一致（外壳至少 10 个、控制器在正面中间）。
     */
    private static BlockPattern buildPattern(@NotNull MultiblockMachineDefinition definition) {
        TraceabilityPredicate casing = Predicates.blocks(GTBlocks.CASING_PTFE_INERT.get())
                .setMinGlobalLimited(10);
        TraceabilityPredicate abilities = Predicates.autoAbilities(definition.getRecipeTypes())
                .or(Predicates.autoAbilities(true, false, false));
        return FactoryBlockPattern.start()
                .aisle("XXX", "XCX", "XXX")
                .aisle("XXX", "XPX", "XXX")
                .aisle("XXX", "XSX", "XXX")
                .where('S', Predicates.controller(Predicates.blocks(definition.getBlock())))
                .where('X', casing.or(abilities))
                .where('P', Predicates.blocks(GTBlocks.CASING_POLYTETRAFLUOROETHYLENE_PIPE.get()))
                // ★ 背面正中：唯一线圈槽，必须是加热线圈（原版这里是白铜线圈 + 中层四角也是线圈位）
                .where('C', Predicates.heatingCoils().setExactLimit(1))
                .build();
    }

    /**
     * JEI / 终端结构预览：只保留「线圈在背面正中」这一种摆法（原版 5 种，玩家看到 5 个面都带线圈）。
     *
     * <p>对照原定义 {@code shapeInfos(...)} 的第 1 种：预览里控制器在近侧（第一个 aisle），
     * 线圈在远侧（最后一个 aisle）正中；其余仓室摆放照抄原定义。
     */
    private static void applyShapes(@NotNull MultiblockMachineDefinition lcr) {
        // ★ setShapes 收的是 Supplier（无参）；机器定义本身是 Supplier<IMachineBlock>，直接传 lcr。
        lcr.setShapes(() -> {
            MultiblockShapeInfo.ShapeInfoBuilder builder = MultiblockShapeInfo.builder()
                    .where('S', lcr, Direction.NORTH)
                    .where('X', GTBlocks.CASING_PTFE_INERT.getDefaultState())
                    .where('P', GTBlocks.CASING_POLYTETRAFLUOROETHYLENE_PIPE.getDefaultState())
                    .where('C', GTBlocks.COIL_CUPRONICKEL.getDefaultState())
                    .where('I', GTMachines.ITEM_IMPORT_BUS[3], Direction.NORTH)
                    .where('E', GTMachines.ENERGY_INPUT_HATCH[3], Direction.NORTH)
                    .where('O', GTMachines.ITEM_EXPORT_BUS[3], Direction.NORTH)
                    .where('F', GTMachines.FLUID_IMPORT_HATCH[3], Direction.NORTH)
                    .where('M', GTMachines.MAINTENANCE_HATCH, Direction.NORTH)
                    .where('H', GTMachines.FLUID_EXPORT_HATCH[3], Direction.NORTH);
            return List.of(builder
                    .aisle("IXO", "FSH", "XMX")
                    .aisle("XXX", "XPX", "XXX")
                    .aisle("XEX", "XCX", "XXX")
                    .build());
        });
    }

    /**
     * GTO 口径的超频：按线圈等级在「无损超频（每次 ×0.25）」与「普通超频（每次 ×0.5）」之间二选一。
     *
     * <p>对照 GTO 原式：
     * {@code RecipeModifier.overclocking(this, unit, recipe, false, 1, 1, (coilTier + 2 > recipe.tier) ? 0.25 : 0.5)}。
     * 本包 gtceu 1.4.4 没有 26.x 的那套 API，但 {@code OverclockingLogic}
     * 本身就是 {@code (durationFactor, voltageFactor, subtick)} 三件套：用 0.25/0.5 构造即可得到
     * 完全一致的每次超频倍率（电压倍率固定 4.0；subtick=true 与原来大化反的
     * {@code PERFECT_OVERCLOCK_SUBTICK} 一致，保留亚 1 tick 时转并行的行为）。
     *
     * @return 超频后的配方；超频不允许时返回 {@code null}（由 RecipeModifierList 中断，配方停摆，与原版一致）
     */
    private static GTRecipe applyCoilOverclock(@NotNull MetaMachine machine, @NotNull GTRecipe recipe,
                                               @NotNull OCParams params, @NotNull OCResult result) {
        if (!(machine instanceof CoilLargeChemicalReactorMachine lcr) || !lcr.isFormed()) {
            // 理论到不了：定义换机后必为本类；未成形时按本包原定义（完美超频）兜底，避免任何异常路径卡配方。
            return GTRecipeModifiers.ELECTRIC_OVERCLOCK
                    .apply(OverclockingLogic.PERFECT_OVERCLOCK_SUBTICK)
                    .apply(machine, recipe, params, result);
        }
        int recipeTier = RecipeHelper.getRecipeEUtTier(recipe);
        double durationFactor = lcr.getOverclockDurationFactor(recipeTier);
        OverclockingLogic logic = new OverclockingLogic(
                durationFactor, OverclockingLogic.STD_VOLTAGE_FACTOR, true);
        return GTRecipeModifiers.ELECTRIC_OVERCLOCK.apply(logic).apply(machine, recipe, params, result);
    }

    /** 倍率显示成两位小数（0.5 → "0.50"），避免日志里出现 0.30000000000000004 这种。 */
    private static String fmt(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }
}
