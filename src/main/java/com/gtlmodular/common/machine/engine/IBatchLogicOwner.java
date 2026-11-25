package com.gtlmodular.common.machine.engine;

import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import it.unimi.dsi.fastutil.objects.Object2IntMap;

import java.util.List;
import java.util.Map;

/**
 * ★ 2026-11-25：<b>批次引擎的「逻辑壳」契约</b> —— {@link BatchFinder} / {@link BatchIo}
 * 从这个接口取答案，不再硬依赖 {@link ThreadedRecipeLogic} 这个具体类。
 *
 * <p><b>为什么</b>：本模组有两个逻辑壳，它们必须各自保留（Javadoc 见 {@code PLAN.md}）：
 * <ul>
 *   <li>{@link ThreadedRecipeLogic}（通用引擎）—— {@code extends RecipeLogic}，8 台自研机器用；</li>
 *   <li>{@code MoltenModuleMachineBase.MoltenModuleRecipeLogic}（熔岩模块）——
 *       {@code extends MultipleRecipesLogic}，因为 gtlcore 的
 *       {@code CoilWorkableElectricMultipleRecipesMachine#getRecipeLogic()} 会把返回值强转成
 *       {@code MultipleRecipesLogic}，换不得。</li>
 * </ul>
 * 两个壳过去各写一份「扫描 / 开批 / 扣料」纪律（{@code SCAN_BUDGET=24}、冷却 10 tick 之类字面重复），
 * 一旦漂移就出现「同一份引擎在两种机器上行为不同」。本接口把它们能共用的部分接到同一批组件上。
 *
 * <p><b>不合并的部分</b>（刻意）：并行算法、耗电模型、时长公式。这两条路见
 * {@link #usesStandardParallel()} 与 {@link #prepareBatch(GTRecipe)}。
 */
public interface IBatchLogicOwner {

    /** 逻辑壳自身（{@link GTRecipe#checkConditions(RecipeLogic)} 要的就是它）。 */
    RecipeLogic asRecipeLogic();

    /** 每 tick 输入的 chance cache（{@link RecipeLogic#getChanceCaches()} 的转出）。 */
    Map<RecipeCapability<?>, Object2IntMap<?>> chanceCaches();

    /**
     * ME 样板仓里已解码的候选（猪咪样板总成）。返回空表 ⇒ 回落到整张配方表分片扫描。
     *
     * <p>没有 ME 样板通道的机器（熔岩模块）直接沿用默认空表。
     */
    default List<GTRecipe> meCachedCandidates(GTRecipeType type) {
        return List.of();
    }

    /**
     * 机器里是否<b>真的</b>有物品/流体输入（不是「挂了 ME 仓」）。
     *
     * <p>用来省掉「必然失败」的整表扫描；返回 {@code false} 时 {@link BatchFinder}
     * 直接给这张表上一段冷却，不扫。
     */
    boolean hasRealItemOrFluidInput();

    /**
     * 走机器自己的配方修改器（超频 / 降档 / EBF 等），不参与并行。
     *
     * <p>只有 {@link #usesStandardParallel()} 为 {@code true} 的壳会问这一步。
     */
    default GTRecipe modifyRecipe(GTRecipe raw) {
        return raw;
    }

    /**
     * 候选的额外准入判定（层级 / 配方条件 / EBF 温度之类的机器特有门槛）。
     *
     * <p>在「原配方输入匹配」之后、折算之前调用；返回 {@code false} ⇒ 跳过这条候选。
     * 通用引擎没有额外门槛（配方条件在折算后统一判），默认放行。
     */
    default boolean acceptsCandidate(GTRecipe raw) {
        return true;
    }

    /** 多配方表的机器是否跨表轮转扫描（单表机器恒 false 也无所谓）。 */
    default boolean crossTableScan() {
        return true;
    }

    /**
     * 是否使用引擎标准的「修改器 → accurateParallel → 条件」折算链。
     *
     * <p>{@code true} = 通用引擎口径（GTCEu 的 {@code GTRecipeModifiers.accurateParallel}）；
     * {@code false} = 由 {@link #prepareBatch(GTRecipe)} 自备折算（熔岩模块的 gtlcore 口径：
     * {@code IParallelLogic.getMaxParallel} + 把每 tick EU 烘进配方）。
     */
    default boolean usesStandardParallel() {
        return true;
    }

    /**
     * 自备折算：把一条原始配方变成「一条线程的批次」。
     *
     * <p>只在 {@link #usesStandardParallel()} 为 {@code false} 时被调用。返回 {@code null}
     * ⇒ 这条候选开不了（已扣的料不退，语义与标准链一致）。
     */
    default GTRecipe prepareBatch(GTRecipe raw) {
        return null;
    }
}
