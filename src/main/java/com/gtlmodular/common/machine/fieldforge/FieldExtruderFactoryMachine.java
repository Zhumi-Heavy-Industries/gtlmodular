package com.gtlmodular.common.machine.fieldforge;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gtlmodular.common.machine.engine.IIndependentThreadHost;
import com.gtlmodular.common.machine.engine.IThreadedRecipeMachine;
import com.gtlmodular.common.machine.engine.ThreadedRecipeLogic;

/**
 * 力场压模零件工厂（{@code gtlmodular:field_extruder_factory}）—— GTO
 * {@code gtocore:field_extruder_factory} 的移植体。
 *
 * <h2>与 GTO 原机的差异：配方表</h2>
 * <p>GTO 原机跑 {@code GTRecipeTypes.EXTRUDER_RECIPES}（压模机表）
 * （{@code _gto_src/.../machines/MultiBlockF.java:255}）。
 * <b>本机按队长 2026-11-14 口径改跑本模组自建的「零部件加工」表</b>
 * {@code gtlmodular:field_extruder_parts}（{@code FieldExtruderPartsRecipeTypes}），不是压模机表。</p>
 * <p>{@code 2026-11-25}：该表已与奈亚之触彻底解耦（包/类名/id 全部改掉），配方由
 * {@code FieldExtruderPartsRecipes} 按材料注册表自动生成。</p>
 *
 * <h2>机器本身（照搬 GTO）</h2>
 * <ul>
 *   <li>{@code nonYAxisRotation} ⇒ {@code RotationState.NON_Y_AXIS}（只能水平朝向）；</li>
 *   <li>可并行 + 可接激光仓 + 多配方并发（GTO {@code CrossRecipeMultiblockMachine::createHatchParallel}）；</li>
 *   <li>并行：<b>本结构没有线圈位</b>（GTO 也没给本机挂 {@code .overclock()} 或线圈），
 *       所以并行完全来自结构里的并行仓（{@code PARALLEL_HATCH}，GTO 原谓词
 *       {@code setMaxGlobalLimited(1)}）。见 {@link #parallelMultiplier()}。</li>
 * </ul>
 *
 * <h2>★ 独立线程引擎接入（2026-11-15 补）</h2>
 * <p><b>此前本类只继承了 {@link WorkableElectricMultiblockMachine}，没有接引擎</b> ——
 * 结构里允许放线程仓，但线程仓的线程数、独立线程、并行重复配方三个开关<b>一个都没吃到</b>：
 * 机器仍然按 GTCEu 原生 {@code RecipeLogic} 单条推进。
 * 现在的做法与奈亚拉托提普之触（{@code NyarlathotepsTentacleMachine}）一致：
 * <ul>
 *   <li>{@link #createRecipeLogic} 换成 {@link ThreadedRecipeLogic}；</li>
 *   <li>实现 {@link IThreadedRecipeMachine}：线程数由结构里线程仓现读
 *       （{@link IThreadedRecipeMachine#effectiveThreads()}），没装仓回落到
 *       {@link #BASE_THREADS}；</li>
 *   <li>实现 {@link IIndependentThreadHost}（引擎的宿主标记）。</li>
 * </ul>
 *
 * <p>{@link #engineRequiresThreadHatch()} 返回 {@code false} —— 与奈亚同一口径：
 * 装不装线程仓都由引擎驱动。原因见奈亚那边记录的实机崩溃（gtladditions 给
 * {@code MultipleRecipesLogic} 打了 mixin，会把机器强转成
 * {@code IWirelessThreadModifierParallelMachine}），所以不走「没仓就交回原逻辑」那条路。</p>
 *
 * <p><b>并行口径</b>：{@link #usesParallelHatch()} 保持默认 {@code true} ——
 * 本机并行只来自并行仓，{@link #parallelMultiplier()} 恒为 1（没有线圈倍率）。
 * 实际并行 = 1 × 并行仓并行数，与 GTO 的 {@code createHatchParallel} 同义。</p>
 */
public class FieldExtruderFactoryMachine extends WorkableElectricMultiblockMachine
        implements IThreadedRecipeMachine, IIndependentThreadHost {

    /** 没有线程仓时的并发线程数（与奈亚同口径：一颗线程仓都不装只有 1 条）。 */
    public static final int BASE_THREADS = 1;

    public FieldExtruderFactoryMachine(IMachineBlockEntity holder) {
        super(holder);
    }

    @Override
    protected RecipeLogic createRecipeLogic(Object... args) {
        return new ThreadedRecipeLogic(this);
    }

    /** 没装线程仓时的并发线程数；装了线程仓时由 {@link IThreadedRecipeMachine#effectiveThreads()} 覆盖。 */
    @Override
    public int baseThreads() {
        return BASE_THREADS;
    }

    /**
     * 本机没有线圈 ⇒ 并行倍率恒为 1。
     *
     * <p>并行数完全由结构里的并行仓给出（{@link #usesParallelHatch()} 为 {@code true}，
     * 引擎会把本倍率与并行仓并行数相乘）。</p>
     */
    @Override
    public int parallelMultiplier() {
        return 1;
    }

    /**
     * 并行是否再乘结构里的标准并行仓 —— <b>是</b>（默认值，此处显式写出以示口径）。
     *
     * <p>与 GTO 的 {@code CrossRecipeMultiblockMachine::createHatchParallel} 同义：
     * 结构里并行仓的并行数即整机并行。</p>
     */
    @Override
    public boolean usesParallelHatch() {
        return true;
    }

    /**
     * 引擎常驻（不要求先装线程仓）。
     *
     * <p>与奈亚拉托提普之触同口径，理由见奈亚机器类注释：gtladditions 给
     * {@code MultipleRecipesLogic} 的构造打了 mixin，会把机器强转成
     * {@code IWirelessThreadModifierParallelMachine}，本机没实现该接口 ⇒ 走那条路会
     * {@code ClassCastException}。所以「装不装线程仓都由引擎驱动」，
     * 没仓时线程数回落到 {@link #BASE_THREADS}（1 条）。</p>
     */
    @Override
    public boolean engineRequiresThreadHatch() {
        return false;
    }
}
