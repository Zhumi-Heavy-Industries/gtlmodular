package com.gtlmodular.common.machine.petrochem;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.block.ICoilType;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.multiblock.CoilWorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gtlmodular.common.machine.engine.IIndependentThreadHost;
import com.gtlmodular.common.machine.engine.IThreadedRecipeMachine;
import com.gtlmodular.common.machine.engine.ThreadedRecipeLogic;
import com.gregtechceu.gtceu.api.gui.fancy.TabsWidget;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gtladd.gtladditions.api.machine.feature.IStructureTierMachine;
import com.gtladd.gtladditions.api.machine.feature.TierRequirement;
import com.gtladd.gtladditions.common.machine.trait.StructureTierTrait;
import com.gtlmodular.common.machine.upgrade.StructureUpgradeSupport;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import com.gtlmodular.common.machine.thread.IThreadHatchPart;

/**
 * 燃料精炼复合体（{@code gtlmodular:fuel_refining_complex}）—— GTO 同名机器
 * （{@code gtocore:fuel_refining_complex}）的移植体，<b>机器本体照搬、不改名</b>。
 *
 * <h2>运行模式（照搬 GTO 的 {@code createCheckedTemperatureParallel(true)}）</h2>
 * <p>GTO 的两个口径在工具提示里都写死了，本类逐条落实：
 * <ul>
 *   <li><b>有效温度</b>（机器 tooltip 第一行 {@code gtceu.machine.electric_blast_furnace.tooltip.2}：
 *       「超过 MV 后的每一级电压，都会将温度提高 100K」）：
 *       {@code 有效温度 = 线圈温度 + 100 × max(0, 电压档 - MV)}。
 *       这正是 GTCEu 自己的电力高炉口径 —— {@code GTRecipeModifiers.ebfOverclock} 第 135 行
 *       {@code coilMachine.getCoilType().getCoilTemperature() + 100 * Math.max(0, coilMachine.getTier() - 2)}，
 *       {@code tier 2 = MV}。本类算并行时用<b>同一个式子</b>，不做第二套温度定义。</li>
 *   <li><b>线圈并行</b>：{@code 2^(⌊有效温度 / 900⌋)}（GTO tooltip：「线圈温度每高出 900K，并行数 ×2；
 *       公式：2^(向下取整(温度 / 900))」）。</li>
 *   <li><b>温度闸</b>：配方带 {@code ebf_temp} 且高于有效温度 ⇒ 停机 —— 由
 *       {@code GTRecipeModifiers.ebfOverclock} 直接判 {@code null}，
 *       与 {@code GTLModularMachines.moltenEbfOverclock} / 高维等离子聚变核心同一口径。</li>
 *   <li><b>激光仓</b>：{@code ^} 面开放 {@code INPUT_LASER}（≤4），GTO 的 {@code .laserTooltips()}。</li>
 *   <li><b>附属模块口</b>：{@code ^} 那 26 格舱面开放 线程仓（≤1）/ 超频仓 / 并行仓（≤1）——
 *       GTO 原机 {@code fuel_refining_crossrecipe} 模块的语义，见
 *       {@link com.gtlmodular.common.data.petrochem.FuelRefiningComplexStructures} 类注释。</li>
 * </ul>
 *
 * <h2>线程引擎</h2>
 * <p>与木化工厂 / 集成石油化工处理工厂 / 奈亚拉托提普之触同一套：{@link ThreadedRecipeLogic}
 * 接管，线程数由结构里的线程仓现读（没装 ⇒ {@link #BASE_THREADS} = 1 条），
 * {@link #engineRequiresThreadHatch()} 恒 {@code false}。
 */
public class FuelRefiningComplexMachine extends CoilWorkableElectricMultiblockMachine
        implements IThreadedRecipeMachine, IIndependentThreadHost, IStructureTierMachine, com.gregtechceu.gtceu.api.machine.feature.IDropSaveMachine {

    @Persisted
    @DescSynced
    private final StructureTierTrait structureTierTrait;
    private static ManagedFieldHolder FIELD_HOLDER;

    /** 没有线程仓时的并发线程数（一颗线程仓都不装只有 1 条）。 */
    public static final int BASE_THREADS = 1;

    /** GTO 并行公式的分母：有效温度每高 900K，并行 ×2。 */
    public static final int PARALLEL_TEMPERATURE_STEP = 900;

    /** 温度奖励的起点档位：MV（GTCEu 的 {@code GTValues.MV = 2}，与 {@code ebfOverclock} 的 {@code -2} 同源）。 */
    public static final int TEMPERATURE_BONUS_BASE_TIER = GTValues.MV;

    /** 超过 MV 后每高一级电压，有效温度 +100K。 */
    public static final int TEMPERATURE_BONUS_PER_TIER = 100;

    public FuelRefiningComplexMachine(IMachineBlockEntity holder,
                 Supplier<BlockPattern> pattern, Supplier<String[][]> slices,
                                     String tierChars) {
        super(holder);
        this.structureTierTrait = new StructureTierTrait(this,
                StructureUpgradeSupport.layout(pattern, slices,
                        ch -> tierChars.indexOf(ch) >= 0),
                null, Set.of("CoilType"), StructureUpgradeSupport.noOpSync());
    }

    @Override
    public StructureTierTrait getStructureTierTrait() {
        return structureTierTrait;
    }

    @Override
    public TierRequirement tierRequirement(int tier) {
        if (tier != 2) {
            return null;
        }
        return StructureUpgradeSupport.requirement(
                // GTO fuel_refining_crossrecipe（跨配方模块）：开放线程仓 / 超频仓。
                StructureUpgradeSupport.cost("gtlcore:iridium_casing", 64),
                StructureUpgradeSupport.cost("gtlmodular:thread_hatch_uv", 1),
                StructureUpgradeSupport.cost("gtlmodular:overclock_hatch_uv", 1));
    }

    @Override
    public boolean checkPattern() {
        return structureTierTrait.checkPattern();
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        if (FIELD_HOLDER == null) {
            FIELD_HOLDER = new ManagedFieldHolder(FuelRefiningComplexMachine.class, super.getFieldHolder());
        }
        return FIELD_HOLDER;
    }

    @Override
    public void onStructureFormed() {
        super.onStructureFormed();
        structureTierTrait.onStructureFormed();
    }

    @Override
    public void onStructureInvalid() {
        super.onStructureInvalid();
        structureTierTrait.onStructureInvalid();
    }

    @Override
    public boolean saveBreak() {
        return structureTierTrait.getHasDropData()
                || com.gregtechceu.gtceu.api.machine.feature.IDropSaveMachine.super.saveBreak();
    }

    @Override
    public void attachSideTabs(TabsWidget tabs) {
        super.attachSideTabs(tabs);
        StructureUpgradeSupport.attachTab(tabs, this);
    }

    @Override
    public void addDisplayText(List<Component> textList) {
        super.addDisplayText(textList);
        textList.add(StructureUpgradeSupport.display(structureTierTrait));
        textList.add(Component.translatable("gtlmodular.structure_upgrade.fuel_refining_complex"));
    }

    @Override
    protected RecipeLogic createRecipeLogic(Object... args) {
        return new ThreadedRecipeLogic(this);
    }

    @Override
    public int baseThreads() {
        return BASE_THREADS;
    }

    /**
     * 有效温度 = 线圈温度 + 100 × max(0, 电压档 - MV)。
     *
     * <p>与 {@code GTRecipeModifiers.ebfOverclock}（GTCEu 1.4.4 第 135 行）完全同一式子，
     * 所以「能不能跑这条配方」与「并行给多少」用的是同一个温度，不会自相矛盾。
     */
    public int effectiveTemperature() {
        ICoilType coil = getCoilType();
        int coilTemperature = coil == null ? 0 : coil.getCoilTemperature();
        int bonus = TEMPERATURE_BONUS_PER_TIER
                * Math.max(0, getTier() - TEMPERATURE_BONUS_BASE_TIER);
        return coilTemperature + bonus;
    }

    /**
     * 线圈并行倍率：<b>2^(⌊有效温度 / 900⌋)</b>。
     *
     * <p>有效温度 ≤ 0 时退回 1；上限按 {@code int} 收口（指数 ≥ 30 直接给 {@link Integer#MAX_VALUE}）。
     */
    @Override
    public int parallelMultiplier() {
        int temperature = effectiveTemperature();
        if (temperature <= 0) {
            return 1;
        }
        int exponent = temperature / PARALLEL_TEMPERATURE_STEP;
        if (exponent <= 0) {
            return 1;
        }
        if (exponent >= 30) {
            return Integer.MAX_VALUE;
        }
        return 1 << exponent;
    }

    /** 结构里若装了并行仓，在本机并行上再乘一次（与本模组引擎口径一致）。 */
    @Override
    public boolean usesParallelHatch() {
        return true;
    }

    // ------------------------------------------------------------------ 独立线程（IIndependentThreadHost）

    private int threadStateTick = -1;
    private boolean threadStateIndependent;

    /** 同 {@link PetrochemicalPlantMachine#isIndependentThreadActive()}：至少一个线程仓的开关打开才生效。 */
    @Override
    public boolean isIndependentThreadActive() {
        if (getLevel() == null) {
            return false;
        }
        long now = getOffsetTimer();
        if (now == threadStateTick) {
            return threadStateIndependent;
        }
        threadStateTick = (int) now;
        boolean independent = false;
        for (IMultiPart part : getParts()) {
            if (part instanceof IThreadHatchPart hatch && hatch.isIndependentThread()) {
                independent = true;
                break;
            }
        }
        threadStateIndependent = independent;
        return independent;
    }

    /** 引擎常驻（不要求先装线程仓），同 {@link PetrochemicalPlantMachine}。 */
    @Override
    public boolean engineRequiresThreadHatch() {
        return false;
    }
}
