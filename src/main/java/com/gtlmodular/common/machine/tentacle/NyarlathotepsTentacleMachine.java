package com.gtlmodular.common.machine.tentacle;

import com.gregtechceu.gtceu.api.block.ICoilType;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.multiblock.CoilWorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gtlmodular.common.machine.engine.IThreadedRecipeMachine;
import com.gtlmodular.common.machine.engine.ThreadedRecipeLogic;

/**
 * 奈亚拉托提普之触（{@code gtlmodular:nyarlathoteps_tentacle}）—— GTO
 * {@code gtocore:nyarlathoteps_tentacle} 的移植体。
 *
 * <h2>GTO 原机是什么</h2>
 * <p>取证：{@code _gto_src/.../machines/MultiBlockD.java:420}
 * {@code .allRotation()} + {@code CoilCrossRecipeMultiblockMachine::createCoilParallel} +
 * {@code .recipeTypes(SUPRACHRONAL_ASSEMBLY_LINE_RECIPES, ASSEMBLY_LINE_RECIPES, CIRCUIT_ASSEMBLY_LINE_RECIPES)}
 * + {@code .laserTooltips()} + {@code .multipleRecipesTooltips()}。
 * 也就是：<b>一台能跑「超时空组装线 / 组装线 / 电路组装线」三张表的线圈多方块</b>，
 * 并行按线圈温度翻倍、能上激光仓、同时推进多条配方。
 *
 * <h2>本机口径（队长口径，2026-10-31）</h2>
 * <ul>
 *   <li><b>三张表照搬</b>：注册时按 id 现取 {@code gtceu:suprachronal_assembly_line}、
 *       {@code gtceu:assembly_line}、{@code gtceu:circuit_assembly_line}（本整合包三张都在；
 *       取不到的自动跳过并告警，见 {@link NyarlathotepsTentacleMachines}）。
 *       表间切换走 GTCEu 原生的 {@code getActiveRecipeType()/setActiveRecipeType(int)}。</li>
 *   <li><b>并行只看线圈温度</b>（GTO 的 {@code coilParallel}）：GTO 的文案是
 *       「线圈温度每高出 900K，并行 ×2」，基准取第一档线圈 1800K ⇒
 *       {@code 并行 = 2^((温度-1800)/900)}，封顶 {@link #MAX_PARALLEL}。
 *       本机 {@link #usesParallelHatch()} 返回 {@code false} ⇒ 结构里即使放并行仓也不乘。</li>
 *   <li><b>多条配方并发</b>：走本模组独立线程引擎 {@link ThreadedRecipeLogic}，
 *       {@link #THREADS} 条线程同时推进不同配方（GTO 的 {@code multipleRecipes}）。</li>
 *   <li><b>激光 / 光学</b>：结构里开放 {@code INPUT_LASER}（激光仓）与
 *       {@code OPTICAL_DATA_RECEPTION}（光口，恰好 1 个），与 GTO 原结构一致；
 *       激光仓的供能与线程加成由 gtladditions 自己的仓室实现。</li>
 * </ul>
 *
 * <p>⚠ 与原机的已知差异：GTO 的线程数由<b>激光仓</b>叠（{@code autoThreadLaserAbilities}），
 * 本机固定 {@link #THREADS}；要改成「激光仓叠线程」只需覆写 {@link #baseThreads()}
 * 去数结构里的 {@code INPUT_LASER} 仓（gtladditions 的 {@code IThreadModifierPart}，
 * 写法见 {@code NanochipAssemblyComplexMachine} 的 threadPartMachine 一段）。
 */
public class NyarlathotepsTentacleMachine extends CoilWorkableElectricMultiblockMachine
        implements IThreadedRecipeMachine {

    /** 并发线程数（GTO {@code multipleRecipesTooltips} 的等价物）。 */
    public static final int THREADS = 4;

    /** 并行基准温度 = 第一档线圈（白铜 1800K）。 */
    public static final int BASE_COIL_TEMPERATURE = 1800;

    /** 每高出 900K，并行 ×2（GTO 文案原文：「线圈温度每高出 900K，并行 x2」）。 */
    public static final int TEMPERATURE_STEP = 900;

    /** 并行封顶（防止高登记线圈把 int 乘爆）。 */
    public static final int MAX_PARALLEL = 4096;

    /**
     * GTCEu 1.4.4 的 {@code CoilWorkableElectricMultiblockMachine} 只有
     * {@code (IMachineBlockEntity)} 一个构造器（javap 现读，没有 varargs 版本），
     * 所以这里必须只透传 holder，不能写 {@code super(holder, args)}。
     */
    public NyarlathotepsTentacleMachine(IMachineBlockEntity holder, Object... args) {
        super(holder);
    }

    @Override
    protected RecipeLogic createRecipeLogic(Object... args) {
        return new ThreadedRecipeLogic(this);
    }

    @Override
    public int baseThreads() {
        return THREADS;
    }

    /** 只看线圈温度，不乘结构里的并行仓。 */
    @Override
    public boolean usesParallelHatch() {
        return false;
    }

    /**
     * 线圈温度 → 并行倍率：{@code 2^((温度-1800)/900)}，向下取整、封顶 {@link #MAX_PARALLEL}。
     *
     * <p>未成形（线圈为 null）时按基准温度算 ⇒ 返回 1，不会 NPE。
     */
    @Override
    public int parallelMultiplier() {
        ICoilType coil = getCoilType();
        int temperature = coil == null ? BASE_COIL_TEMPERATURE : coil.getCoilTemperature();
        int steps = Math.max(0, (temperature - BASE_COIL_TEMPERATURE) / TEMPERATURE_STEP);
        long parallel = 1L << Math.min(steps, 30);
        return (int) Math.min(MAX_PARALLEL, parallel);
    }

    /** 温度口径（Jade/工具提示用；未成形返回基准温度）。 */
    public int getCoilTemperature() {
        ICoilType coil = getCoilType();
        return coil == null ? BASE_COIL_TEMPERATURE : coil.getCoilTemperature();
    }
}
