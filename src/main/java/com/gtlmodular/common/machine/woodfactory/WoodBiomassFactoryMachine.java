package com.gtlmodular.common.machine.woodfactory;

import com.gregtechceu.gtceu.api.block.ICoilType;
import com.gregtechceu.gtceu.api.gui.fancy.TabsWidget;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.multiblock.CoilWorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gtladd.gtladditions.api.machine.feature.IStructureTierMachine;
import com.gtladd.gtladditions.api.machine.feature.TierRequirement;
import com.gtladd.gtladditions.common.machine.trait.StructureTierTrait;
import com.gtlmodular.common.machine.engine.IIndependentThreadHost;
import com.gtlmodular.common.machine.engine.IThreadedRecipeMachine;
import com.gtlmodular.common.machine.engine.ThreadedRecipeLogic;
import com.gtlmodular.common.machine.upgrade.StructureUpgradeSupport;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 集成木质生物质工厂（{@code gtlmodular:wood_biomass_factory}）—— GTO「木化工厂」
 * （{@code gtocore:wood_distillation}）的移植体。
 *
 * <h2>运行模式（照搬 GTO 的 {@code CoilCrossRecipeMultiblockMachine::createCoilParallel}）</h2>
 * <ul>
 *   <li><b>线圈并行</b>：并行 = <b>2^(⌊线圈温度 / 900⌋)</b>。
 *       GTO 的公式在自家 jar 里逐字可见（{@code MultiBlockA.java:180}：
 *       {@code return 1L << (int) ((double) m.getTemperature() / 900.0);}），
 *       机器 tooltip 也把同一句写死了：「线圈温度每高出 900K，并行数 ×2；
 *       公式：2^(向下取整(温度 / 900))」。铜镍线圈 1800K ⇒ 并行 4；
 *       坎塔尔 2700K ⇒ 8；HSS-G 5400K ⇒ 64；最高档 12600K ⇒ 16384。</li>
 *   <li><b>自带并行、不强制装并行仓</b>（GTO tooltip：「机器自带并行机制，无需安装并行控制仓」）。
 *       结构里若装了并行仓，按本模组引擎口径<b>再乘一次</b>（{@link #usesParallelHatch()} 为
 *       {@code true}）；不装也照跑。</li>
 *   <li><b>激光仓</b>：结构 {@code X} 位开放 {@code INPUT_LASER}（≤4），GTO 的
 *       {@code .laserTooltips()} 语义。</li>
 *   <li><b>附属模块口</b>：{@code Z=58/59} 那两片控制舱的 71 格开放线程仓（≤1）/ 超频仓 /
 *       加速仓 / 并行仓（≤1）—— 见 {@code WoodFactoryStructures} 的类注释。</li>
 *   <li><b>超频口径</b>：挂 {@code OverclockProfiles.apply}（默认完美超频 + 亚 tick），
 *       它内部读结构里的<b>超频仓 / 加速仓</b>；与力场压模零件工厂 / 冷却塔同一口径。</li>
 * </ul>
 *
 * <h2>线程引擎</h2>
 * <p>与力场压模零件工厂 / 奈亚拉托提普之触同一套：{@link ThreadedRecipeLogic} 接管，
 * 线程数由结构里的线程仓现读（没装 ⇒ {@link #BASE_THREADS} = 1 条），
 * {@link #engineRequiresThreadHatch()} 恒 {@code false}（本机就是按引擎设计的）。
 *
 * <h2>线圈温度数据来源</h2>
 * <p>继承 {@link CoilWorkableElectricMultiblockMachine}：GTCEu 的
 * {@code Predicates.heatingCoils()} 在结构检查时把命中的 {@link ICoilType} 写进
 * {@code MatchContext} 的 {@code "CoilType"}，{@code onStructureFormed()} 再读出来 ——
 * 所以本机<b>必须</b>在结构里保留 {@code K} 位（192 格加热线圈），一换线圈并行立刻变。
 */
public class WoodBiomassFactoryMachine extends CoilWorkableElectricMultiblockMachine
        implements IThreadedRecipeMachine, IIndependentThreadHost, IStructureTierMachine, com.gregtechceu.gtceu.api.machine.feature.IDropSaveMachine {

    /** 没有线程仓时的并发线程数（一颗线程仓都不装只有 1 条）。 */
    public static final int BASE_THREADS = 1;

    /** GTO 并行公式的分母：线圈温度每高 900K，并行 ×2。 */
    public static final int PARALLEL_TEMPERATURE_STEP = 900;

    @Persisted
    @DescSynced
    private final StructureTierTrait structureTierTrait;
    private static ManagedFieldHolder FIELD_HOLDER;

    public WoodBiomassFactoryMachine(IMachineBlockEntity holder,
                                     Supplier<BlockPattern> pattern, Supplier<String[][]> slices,
                                     String tierChars) {
        super(holder);
        // GTO wood_dist_crossrecipe 的模块舱面（a/b/t/d）作为二级结构格；老结构不强制这一位。
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
                // GTO wood_dist_crossrecipe（跨配方模块）：解锁线程仓 / 超频仓。
                StructureUpgradeSupport.cost("gtlcore:oxidation_resistant_hastelloy_n_mechanical_casing", 64),
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
            FIELD_HOLDER = new ManagedFieldHolder(WoodBiomassFactoryMachine.class, super.getFieldHolder());
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
        textList.add(Component.translatable("gtlmodular.structure_upgrade.wood_biomass_factory"));
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
     * <p>未成形 / 没有线圈时 GT 给的是铜镍 1800K（{@link CoilWorkableElectricMultiblockMachine}
     * 的字段初值），所以这里不会因为「没成形」而除以零；温度 ≤ 0 时才退回 1。</p>
     *
     * <p>上限按 {@code int} 收口：温度/900 ≥ 30 时直接给 {@link Integer#MAX_VALUE}
     * （GTCEu 的并行是 {@code int}，再大也表达不了）。</p>
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

    // ------------------------------------------------------------------ 独立线程（IIndependentThreadHost）

    private int threadStateTick = -1;
    private boolean threadStateIndependent;

    /**
     * 结构里是否至少有一个线程仓把「独立线程」开关打开。
     *
     * <p>语义照 {@link IIndependentThreadHost}：<b>没有线程仓 ⇒ {@code false}</b>
     * （退回「只用当前选中那张配方表」的行为）。按 {@code getOffsetTimer()} 每 tick 最多重算一次
     * （与力场压模零件工厂 / 高维等离子聚变核心同款做法）。</p>
     */
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
        for (com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart part : getParts()) {
            if (part instanceof com.gtlmodular.common.machine.thread.IThreadHatchPart hatch
                    && hatch.isIndependentThread()) {
                independent = true;
                break;
            }
        }
        threadStateIndependent = independent;
        return independent;
    }

    /**
     * 引擎常驻（不要求先装线程仓）—— 与奈亚拉托提普之触 / 力场压模零件工厂同口径
     * （gtladditions 给 {@code MultipleRecipesLogic} 打了 mixin，走「没仓就交回原逻辑」那条路会崩，
     * 所以装不装线程仓都由引擎驱动，没仓时线程数回落到 {@link #BASE_THREADS}）。
     */
    @Override
    public boolean engineRequiresThreadHatch() {
        return false;
    }
}
