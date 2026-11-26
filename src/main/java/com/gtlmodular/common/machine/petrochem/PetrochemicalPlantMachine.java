package com.gtlmodular.common.machine.petrochem;

import com.gregtechceu.gtceu.api.block.ICoilType;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
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


/**
 * 集成石油化工处理工厂（{@code gtlmodular:petrochemical_plant}）—— GTO「石化工厂」
 * （{@code gtocore:petrochemical_plant}）的移植体。
 *
 * <h2>运行模式（照搬 GTO 的 {@code CoilCrossRecipeMultiblockMachine::createCoilParallel}）</h2>
 * <ul>
 *   <li><b>线圈并行</b>：并行 = <b>2^(⌊线圈温度 / 900⌋)</b>。GTO 的公式在木化工厂那轮已按
 *       {@code MultiBlockA.java:180} 逐字取证（{@code return 1L << (int) ((double) m.getTemperature() / 900.0);}），
 *       机器 tooltip 也写死了同一句：「线圈温度每高出 900K，并行数 ×2；
 *       公式：2^(向下取整(温度 / 900))」。铜镍 1800K ⇒ 并行 4；坎塔尔 2700K ⇒ 8；
 *       HSS-G 5400K ⇒ 64；最高档 12600K ⇒ 16384。</li>
 *   <li><b>自带并行、不强制装并行仓</b>（GTO tooltip：「机器自带并行机制，无需安装并行控制仓」）。
 *       结构 {@code H} 面若装了并行仓，按本模组引擎口径<b>再乘一次</b>
 *       （{@link #usesParallelHatch()} 为 {@code true}）；不装也照跑。</li>
 *   <li><b>激光仓</b>：{@code H} 面开放 {@code INPUT_LASER}（≤4），即 GTO 的 {@code .laserTooltips()}。</li>
 *   <li><b>附属模块口</b>：{@code H} 那 26 格舱面开放 线程仓（≤1）/ 超频仓 / 加速仓 / 并行仓（≤1）——
 *       GTO 原机的 {@code petrochem_crossrecipe} 模块语义，见
 *       {@link com.gtlmodular.common.data.petrochem.PetrochemicalPlantStructures} 类注释。</li>
 *   <li><b>超频口径</b>：挂 {@code OverclockProfiles.apply}（默认完美超频 + 亚 tick），
 *       它内部读结构里的<b>超频仓 / 加速仓</b>；与木化工厂 / 力场压模零件工厂 / 冷却塔同一口径。
 *       本机配方（{@code gtceu:petrochemical_plant}）不带 {@code ebf_temp}，所以不做温度闸。</li>
 * </ul>
 *
 * <h2>线程引擎</h2>
 * <p>与木化工厂 / 力场压模零件工厂 / 奈亚拉托提普之触同一套：{@link ThreadedRecipeLogic} 接管，
 * 线程数由结构里的线程仓现读（没装 ⇒ {@link #BASE_THREADS} = 1 条），
 * {@link #engineRequiresThreadHatch()} 恒 {@code false}（本机就是按引擎设计的）。
 *
 * <h2>线圈温度数据来源</h2>
 * <p>继承 {@link CoilWorkableElectricMultiblockMachine}：GTCEu 的 {@code Predicates.heatingCoils()}
 * 在结构检查时把命中的 {@link ICoilType} 写进 {@code MatchContext} 的 {@code "CoilType"}，
 * {@code onStructureFormed()} 再读出来 —— 所以本机<b>必须</b>在结构里保留 {@code M} 位
 * （614 格加热线圈），一换线圈并行立刻变。
 */
public class PetrochemicalPlantMachine extends CoilWorkableElectricMultiblockMachine
        implements IThreadedRecipeMachine, IIndependentThreadHost, IStructureTierMachine, com.gregtechceu.gtceu.api.machine.feature.IDropSaveMachine {

    @Persisted
    @DescSynced
    private final StructureTierTrait structureTierTrait;
    private static ManagedFieldHolder FIELD_HOLDER;

    /** 没有线程仓时的并发线程数（一颗线程仓都不装只有 1 条）。 */
    public static final int BASE_THREADS = 1;

    /** GTO 并行公式的分母：线圈温度每高 900K，并行 ×2。 */
    public static final int PARALLEL_TEMPERATURE_STEP = 900;

    public PetrochemicalPlantMachine(IMachineBlockEntity holder,
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
                // 结构用的基础外壳；不收舱室。
                StructureUpgradeSupport.cost("gtceu:clean_machine_casing", 32),
                StructureUpgradeSupport.cost("gtceu:high_power_casing", 32),
                StructureUpgradeSupport.cost("gtceu:stress_proof_casing", 32));
    }

    @Override
    public boolean checkPattern() {
        return structureTierTrait.checkPattern();
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        if (FIELD_HOLDER == null) {
            FIELD_HOLDER = new ManagedFieldHolder(PetrochemicalPlantMachine.class, super.getFieldHolder());
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
        textList.add(Component.translatable("gtlmodular.structure_upgrade.petrochemical_plant"));
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
     * 线圈并行倍率：<b>2^(⌊线圈温度 / 900⌋)</b>（GTO 原式）。
     *
     * <p>未成形 / 没有线圈时 GT 给的是铜镍 1800K（父类字段初值），所以这里不会因为「没成形」而除以零；
     * 温度 ≤ 0 时才退回 1。上限按 {@code int} 收口：温度/900 ≥ 30 时直接给 {@link Integer#MAX_VALUE}。
     */
    @Override
    public int parallelMultiplier() {
        ICoilType coil = getCoilType();
        int temperature = coil == null ? 0 : coil.getCoilTemperature();
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

    /** 结构里若装了并行仓，在本机并行上再乘一次（GTO 原机不强制装，本机照本模组引擎口径可选乘）。 */
    @Override
    public boolean usesParallelHatch() {
        return true;
    }

    /**
     * 引擎常驻（不要求先装线程仓）—— 与木化工厂 / 奈亚拉托提普之触 / 力场压模零件工厂同口径
     * （gtladditions 给 {@code MultipleRecipesLogic} 打了 mixin，走「没仓就交回原逻辑」那条路会崩，
     * 所以装不装线程仓都由引擎驱动，没仓时线程数回落到 {@link #BASE_THREADS}）。
     */
    @Override
    public boolean engineRequiresThreadHatch() {
        return false;
    }
}
