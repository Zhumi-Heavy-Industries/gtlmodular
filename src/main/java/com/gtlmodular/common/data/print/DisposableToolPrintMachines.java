package com.gtlmodular.common.data.print;

import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.SimpleTieredMachine;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.FactoryBlockPattern;
import com.gregtechceu.gtceu.api.pattern.Predicates;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gregtechceu.gtceu.api.recipe.OverclockingLogic;
import com.gregtechceu.gtceu.common.data.GTBlocks;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.common.data.GTRecipeModifiers;
import com.gtlmodular.GTLModular;
import com.gtlmodular.common.data.GTLModularMachines;
import com.gtlmodular.common.machine.print.DisposableToolPrinterMachine;
import com.gtlmodular.common.machine.print.DisposableToolPrinterMultiblockMachine;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * 一次性工具 3D 打印的机器（两小 + 一多方块）。<b>巨构版已按队长口径删除</b>（2026-10-30）。
 *
 * <table border="1">
 *   <tr><th>id</th><th>形态</th><th>说明</th></tr>
 *   <tr><td>{@code lv_disposable_tool_printer}</td><td>单方块 LV</td><td>基础3D工具打印机，30 EU/t 原速</td></tr>
 *   <tr><td>{@code mv_disposable_tool_printer}</td><td>单方块 MV</td><td>进阶3D工具打印机，MV 电压（GTCEu 标准超频）</td></tr>
 *   <tr><td>{@code disposable_tool_printing_array}</td><td>多方块 5×8×7</td><td>8 线程；结构 = GTO 3D 打印机原结构；并行数由标准并行仓决定</td></tr>
 * </table>
 *
 * <p>注册调用点：{@link GTLModularMachines#init()} 末尾（GTM 机器注册窗口内）。
 * 结构与贴图约定：
 * <ul>
 *   <li>单方块：正面 {@code gtlmodular:block/machines/disposable_tool_printer}（自绘 3D 打印机脸 +
 *       工作动画），其余面自动用 LV/MV 电压机壳；</li>
 *   <li>多方块：GTO gtocore 的 {@code three_dimensional_printer} 原结构（5 宽 × 8 高 × 7 深），
 *       正面覆盖层 {@code gtlmodular:block/multiblock/disposable_tool_printer}。</li>
 * </ul>
 *
 * <p><b>机制独立性</b>：机器配方处理不使用 gtlcore / gtladditions 的任何类；
 * 多方块走本模组自己的 {@code ThreadedRecipeLogic} 独立线程引擎。
 */
public final class DisposableToolPrintMachines {

    public static MachineDefinition LV_PRINTER;
    public static MachineDefinition MV_PRINTER;
    public static MultiblockMachineDefinition PRINTING_ARRAY;

    private DisposableToolPrintMachines() {}

    public static void register() {
        registerSingleBlocks();
        registerArray();
        GTLModular.LOGGER.info("[gtlmodular] 已注册一次性工具 3D 打印机：基础={}(LV) / 进阶={}(MV) / 多方块={}(8线程)",
                LV_PRINTER.getId(), MV_PRINTER.getId(), PRINTING_ARRAY.getId());
    }

    // ------------------------------------------------------------------ 单方块

    private static void registerSingleBlocks() {
        LV_PRINTER = GTLModularMachines.REGISTRATE
                .machine("lv_disposable_tool_printer", holder -> new DisposableToolPrinterMachine(holder, 1))
                .rotationState(RotationState.NON_Y_AXIS)
                .recipeType(DisposableToolPrintTypes.DISPOSABLE_TOOL_PRINTING_BASIC)
                .recipeModifier(GTRecipeModifiers.ELECTRIC_OVERCLOCK
                        .apply(OverclockingLogic.NON_PERFECT_OVERCLOCK))
                .editableUI(SimpleTieredMachine.EDITABLE_UI_CREATOR.apply(
                        DisposableToolPrintTypes.id("lv_disposable_tool_printer"),
                        DisposableToolPrintTypes.DISPOSABLE_TOOL_PRINTING_BASIC))
                .workableTieredHullRenderer(DisposableToolPrintTypes.id("block/machines/disposable_tool_printer"))
                .tier(1)
                .langValue("基础3D工具打印机")
                .tooltips(singleTooltips(1))
                .register();

        MV_PRINTER = GTLModularMachines.REGISTRATE
                .machine("mv_disposable_tool_printer", holder -> new DisposableToolPrinterMachine(holder, 2))
                .rotationState(RotationState.NON_Y_AXIS)
                .recipeType(DisposableToolPrintTypes.DISPOSABLE_TOOL_PRINTING_BASIC)
                .recipeModifier(GTRecipeModifiers.ELECTRIC_OVERCLOCK
                        .apply(OverclockingLogic.NON_PERFECT_OVERCLOCK))
                .editableUI(SimpleTieredMachine.EDITABLE_UI_CREATOR.apply(
                        DisposableToolPrintTypes.id("mv_disposable_tool_printer"),
                        DisposableToolPrintTypes.DISPOSABLE_TOOL_PRINTING_BASIC))
                .workableTieredHullRenderer(DisposableToolPrintTypes.id("block/machines/disposable_tool_printer_mv"))
                .tier(2)
                .langValue("进阶3D工具打印机")
                .tooltips(singleTooltips(2))
                .register();
    }

    /**
     * ★ 2026-10-31 队长口径：单方块描述按 GT 原版单方块机器样式显示
     * （输入电压 / 能量缓存 / 流体容量），并在最前面加一行灰色「NBT去死！」。
     */
    private static Component[] singleTooltips(int tier) {
        Component[] standard = GTMachines.workableTiered(
                tier,
                GTValues.V[tier],
                GTValues.V[tier] * 64L,
                DisposableToolPrintTypes.DISPOSABLE_TOOL_PRINTING_BASIC,
                DisposableToolPrinterMachine.TANK_SIZE.apply(tier),
                true);
        Component[] out = new Component[standard.length + 1];
        out[0] = Component.literal("§7NBT去死！");
        System.arraycopy(standard, 0, out, 1, standard.length);
        return out;
    }

    // ------------------------------------------------------------------ 多方块 ③

    private static void registerArray() {
        PRINTING_ARRAY = GTLModularMachines.REGISTRATE
                .multiblock("disposable_tool_printing_array",
                        holder -> new DisposableToolPrinterMultiblockMachine(holder))
                .rotationState(RotationState.ALL)
                .recipeType(DisposableToolPrintTypes.DISPOSABLE_TOOL_PRINTING)
                .recipeModifier(GTRecipeModifiers.ELECTRIC_OVERCLOCK
                        .apply(OverclockingLogic.NON_PERFECT_OVERCLOCK))
                .appearanceBlock(GTBlocks.CASING_TITANIUM_STABLE)
                .pattern(DisposableToolPrintMachines::arrayPattern)
                .workableCasingRenderer(
                        new ResourceLocation("gtceu", "block/casings/solid/machine_casing_stable_titanium"),
                        DisposableToolPrintTypes.id("block/multiblock/disposable_tool_printer"))
                .langValue("3D打印阵列")
                .tooltips(
                        Component.translatable("gtlmodular.print.tooltip.array.0",
                                DisposableToolPrinterMultiblockMachine.THREADS),
                        Component.translatable("gtlmodular.print.tooltip.array.1"),
                        Component.translatable("gtlmodular.print.tooltip.structure_source"))
                .register();
    }

    /**
     * ★ 2026-10-30：改用 <b>GTO 自己的 3D 打印机结构</b>（gtocore 的
     * {@code pattern/three_dimensional_printer.mbs}，5 宽 × 8 高 × 7 深）。
     *
     * <p>原文件解码后的逐层内容（与 GTO {@code MultiBlockC.THREE_DIMENSIONAL_PRINTER} 的
     * 符号表一一对应；第 1 个字符串 = 最底层、第 1 个 aisle = 背面）：
     * <pre>
     * 层0(背面): AAAAA / ABBBA / AAAAA / CEEEC / CEEEC / CEEEC / CCCCC / (空)
     * 层1:       ACCCA / BBBBB / B   B / C   C / E   E / E   E / CFFFC /  D D
     * 层2:       ACCCA / BBBBB / BFFFB / CG GC / E G E / E G E / CFIFC /  D D
     * 层3:       ACCCA / BBBBB / BFFFB / CGGGC / E   E / E   E / CFIFC /  D D
     * 层4:       ACCCA / CBBBC / CBBBC / CCCCC / C   C / C   C / CCICC /  D D
     * 层5:       ACCCA / DCCCD / DCICD / DCICD / DCICD / DCICD / DCICD / DD DD
     * 层6(正面): AAAAA /  AHA  /  AAA  /  CCC  /  CCC  /  CCC  /  CCC  / (空)
     * </pre>
     *
     * <p>符号映射（GTO → 本包）：
     * <ul>
     *   <li>A 钛稳定机壳（可换 IO/能量/维护/并行仓，GTO 的 {@code wherePart} 同义）；</li>
     *   <li>B 不锈钢清洁机壳；C 钛稳定机壳（固定）；</li>
     *   <li>D 不锈钢框架（{@code Predicates.frames(StainlessSteel)}）；</li>
     *   <li>E 钢化玻璃；G 铁栏杆（GTO 原结构就是 {@code Blocks.IRON_BARS}）；</li>
     *   <li>F GTO 的「多功能机械方块」在本包不存在 ⇒ 用不锈钢清洁机壳平替；</li>
     *   <li>H 控制器；I 钛管道机壳。</li>
     * </ul>
     */
    private static BlockPattern arrayPattern(MultiblockMachineDefinition definition) {
        TraceabilityPredicate casing = casing(definition);
        FactoryBlockPattern pattern = FactoryBlockPattern.start()
                // 层0（背面）
                .aisle("AAAAA", "ABBBA", "AAAAA", "CEEEC", "CEEEC", "CEEEC", "CCCCC", "     ")
                // 层1
                .aisle("ACCCA", "BBBBB", "B   B", "C   C", "E   E", "E   E", "CFFFC", " D D ")
                // 层2
                .aisle("ACCCA", "BBBBB", "BFFFB", "CG GC", "E G E", "E G E", "CFIFC", " D D ")
                // 层3
                .aisle("ACCCA", "BBBBB", "BFFFB", "CGGGC", "E   E", "E   E", "CFIFC", " D D ")
                // 层4
                .aisle("ACCCA", "CBBBC", "CBBBC", "CCCCC", "C   C", "C   C", "CCICC", " D D ")
                // 层5
                .aisle("ACCCA", "DCCCD", "DCICD", "DCICD", "DCICD", "DCICD", "DCICD", "DD DD")
                // 层6（正面）：控制器在第 2 行（从底数）
                .aisle("AAAAA", " AHA ", " AAA ", " CCC ", " CCC ", " CCC ", " CCC ", "     ");
        pattern = pattern.where('H', Predicates.controller(Predicates.blocks(definition.get())));
        pattern = pattern.where('A', casing);
        pattern = pattern.where('B', Predicates.blocks(GTBlocks.CASING_STAINLESS_CLEAN.get()));
        pattern = pattern.where('C', Predicates.blocks(GTBlocks.CASING_TITANIUM_STABLE.get()));
        pattern = pattern.where('D', Predicates.frames(com.gregtechceu.gtceu.common.data.GTMaterials.StainlessSteel));
        pattern = pattern.where('E', Predicates.blocks(GTBlocks.CASING_TEMPERED_GLASS.get()));
        pattern = pattern.where('F', Predicates.blocks(GTBlocks.CASING_STAINLESS_CLEAN.get()));
        pattern = pattern.where('G', Predicates.blocks(net.minecraft.world.level.block.Blocks.IRON_BARS));
        pattern = pattern.where('I', Predicates.blocks(GTBlocks.CASING_TITANIUM_PIPE.get()));
        pattern = pattern.where(' ', Predicates.air());
        return pattern.build();
    }

    // ------------------------------------------------------------------ 公共

    /**
     * 机壳格：钛稳定机壳 + 配方 IO 能力 + 维护仓/并行仓（并行仓最多 1 个）。
     */
    private static TraceabilityPredicate casing(MultiblockMachineDefinition definition) {
        return Predicates.blocks(GTBlocks.CASING_TITANIUM_STABLE.get())
                .or(Predicates.autoAbilities(definition.getRecipeTypes()))
                .or(Predicates.autoAbilities(true, false, true));
    }
}
