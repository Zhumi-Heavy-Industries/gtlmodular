package com.gtlmodular.common.machine.hpfc;

import com.gregtechceu.gtceu.api.block.ICoilType;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.multiblock.CoilWorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.logic.OCParams;
import com.gregtechceu.gtceu.api.recipe.logic.OCResult;
import com.gtlmodular.common.machine.engine.IIndependentThreadHost;
import com.gtlmodular.common.machine.engine.IThreadedRecipeMachine;
import com.gtlmodular.common.machine.engine.ThreadedRecipeLogic;
import com.gtlmodular.common.machine.tentacle.IThreadHatchPart;
import net.minecraft.resources.ResourceLocation;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 高维等离子聚变核心（{@code gtlmodular:hyperdimensional_plasma_fusion_core}）的机器类 ——
 * GTO {@code gtocore:hyperdimensional_plasma_fusion_core}（机器类
 * {@code DimensionallyTranscendentPlasmaForgeMachine extends CoilCrossRecipeMultiblockMachine}）的移植体。
 *
 * <h2>本类只补两件 GTO 原机有、0.3.309 首版没有的能力</h2>
 * <ol>
 *   <li><b>线程仓（多线程配方并行）</b>：结构 B 位接受本模组的
 *       {@code gtlmodular:thread_hatch_<tier>}（UV=4 … MAX=256 线程，界面里 1~上限可调）。
 *       成形后遍历 {@link #getParts()}，把每个 {@link IThreadHatchPart#getThreads()}
 *       求和作为<b>跨配方线程数</b>（没有线程仓时 = 1），交给本模组独立线程引擎
 *       {@link ThreadedRecipeLogic} ⇒ <b>一台机器同时推进 N 条配方</b>，
 *       每条配方各有各的 progress / duration。与 GTO 的
 *       {@code GTOPredicates.autoThreadLaserAbilities(...)} + {@code CoilCrossRecipeMultiblockMachine}
 *       同一语义（本包没有 GTO 的 {@code THREAD_HATCH} 枚举，故用同名自定义 PartAbility，
 *       见 {@code ThreadHatchMachines}）。</li>
 *   <li><b>线圈温度闸门口径调整（2026-11-02 队长口径）</b>：<b>不再</b>要求恒星热能熔炼必须
 *       用超级热熔线圈（32000K）—— 超维度熔炼 / 恒星热能熔炼<b>两张表统一按</b>
 *       {@code ebf_temp ≤ 线圈温度} 判定，<b>任何加热线圈都能跑两张表</b>
 *       （恒星表配方若不带 {@code ebf_temp}，即任何线圈都可执行）。结构 P 位的线圈判定
 *       （{@code Predicates.heatingCoils()}）<b>保持不变</b>。</li>
 * </ol>
 *
 * <h2>线程数从哪来</h2>
 * <p>GTO 口径：结构里最多 1 个线程仓（{@code setMaxGlobalLimited(1)}），单仓线程数 =
 * 玩家在仓界面里设的当前值（{@code 1 << (tier - LuV)} 上限）。
 * 这里仍按「求和」写，结构谓词以后即使放开数量也自然成立；未成形时 {@code getParts()} 为空
 * ⇒ 返回 1，不会 NPE。
 *
 * <h2>并行怎么算</h2>
 * <p>本机<b>不</b>注册 {@code GTRecipeModifiers.PARALLEL_HATCH} 配方修改器（会与引擎重复相乘）：
 * 并行由 {@link ThreadedRecipeLogic} 统一算 —— 每条批次并行数 =
 * {@code max(1, parallelMultiplier()) × max(1, 并行仓并行数)}。本类
 * {@link #parallelMultiplier()} 返回 1（本机并行只看并行仓，与 GTO 的
 * {@code MachineUtils::getHatchParallel} 一致），{@link #usesParallelHatch()} 返回 true。
 */
public class HyperdimensionalPlasmaFusionCoreMachine extends CoilWorkableElectricMultiblockMachine
        implements IThreadedRecipeMachine, IIndependentThreadHost, org.gtlcore.gtlcore.api.machine.multiblock.ParallelMachine {


    @Override
    public int getMaxParallel() {
        return 1;
    }


    /** 没有线程仓时的并发线程数（GTO：单线程）。 */
    public static final int BASE_THREADS = 1;

    /** 超级热熔线圈（uruium）在本机里的等效炉温 —— 与 GTO / 上版口径一致。 */
    public static final int URUIUM_TEMPERATURE = 32000;

    /** kubejs 超级热熔线圈的原始炉温（GTCEu CoilType 上写的 273K）。 */
    private static final int URUIUM_RAW_TEMPERATURE = 273;

    /**
     * GTCEu 1.4.4 的 {@code CoilWorkableElectricMultiblockMachine} 只有
     * {@code (IMachineBlockEntity)} 一个构造器（javap 现读），所以这里必须只透传 holder。
     */
    public HyperdimensionalPlasmaFusionCoreMachine(IMachineBlockEntity holder, Object... args) {
        super(holder);
    }

    /** 换成独立线程引擎（多配方并发）。 */
    @Override
    protected RecipeLogic createRecipeLogic(Object... args) {
        return new ThreadedRecipeLogic(this);
    }

    // ------------------------------------------------------------------ 线程仓（IThreadHatchPart）

    /**
     * 没装线程仓时的固定线程数（GTO：1）。装了线程仓时由引擎统一读仓
     * （{@link IThreadedRecipeMachine#effectiveThreads()}），本方法不再自己扫部件。
     */
    @Override
    public int baseThreads() {
        return BASE_THREADS;
    }

    /** 本机跨配方线程总数（界面上用）：有效线程数 = 线程仓当前值，没仓 = 1。 */
    public int totalThreads() {
        return effectiveThreads();
    }

    /** 并行倍率：本机只看并行仓（GTO 的 {@code getHatchParallel} 口径），故恒 1。 */
    @Override
    public int parallelMultiplier() {
        return 1;
    }

    /**
     * 并行数再乘结构里的标准并行仓 —— 与 GTO 的 {@code getHatchParallel} 一致。
     *
     * <p>注意：本机注册时<b>没有</b>挂 {@code GTRecipeModifiers.PARALLEL_HATCH}，
     * 并行只由引擎在这里乘一次，避免「修改器 × 引擎」重复放大。
     */
    @Override
    public boolean usesParallelHatch() {
        return true;
    }

    // ------------------------------------------------------------------ 线圈温度 / 两张表闸门

    /**
     * 线圈温度：超级热熔线圈 273K → 32000K（GTO {@code getTemperature()} 同值），其余线圈取原值。
     * 未成形（线圈为 null）返回 0。
     */
    public int coilTemperature() {
        ICoilType coil = getCoilType();
        if (coil == null) {
            return 0;
        }
        int temperature = coil.getCoilTemperature();
        return temperature == URUIUM_RAW_TEMPERATURE ? URUIUM_TEMPERATURE : temperature;
    }

    /**
     * ★ 2026-11-02 队长口径：<b>两张配方表统一按 {@code ebf_temp ≤ 线圈温度} 判定</b>，
     * 恒星热能熔炼不再限定 32000K 的超级热熔线圈。
     *
     * <p>不在这里调 {@code interruptRecipe()}：本机走线程引擎，可能有别的批次正在跑，
     * {@code interruptRecipe()} 会对 {@code lastRecipe} 触发 {@code postWorking}，
     * 把别人的批次误收尾。返回 {@code false} 即让引擎跳过这条候选（并记诊断行）。
     */
    @Override
    public boolean beforeWorking(GTRecipe recipe) {
        if (recipe.data.getInt("ebf_temp") > coilTemperature()) {
            return false;
        }
        return super.beforeWorking(recipe);
    }

    // ------------------------------------------------------------------ 线程仓的两个开关（与奈亚同口径）

    private long threadStateTick = Long.MIN_VALUE;
    private boolean threadStateRepeated = true;
    private boolean threadStateIndependent;

    private void refreshThreadState() {
        if (getLevel() == null) {
            return;
        }
        long now = getOffsetTimer();
        if (now == threadStateTick) {
            return;
        }
        threadStateTick = now;
        boolean repeated = true;
        boolean independent = false;
        for (IMultiPart part : getParts()) {
            if (part instanceof IThreadHatchPart hatch) {
                if (!hatch.isRepeatedRecipes()) {
                    repeated = false;
                }
                if (hatch.isIndependentThread()) {
                    independent = true;
                }
            }
        }
        threadStateRepeated = repeated;
        threadStateIndependent = independent;
    }

    /** 结构里所有线程仓是否都允许「重复配方」（无线程仓 ⇒ 允许）。 */
    public boolean isRepeatedRecipesAllowed() {
        refreshThreadState();
        return threadStateRepeated;
    }

    /** 有没有线程仓打开了「独立线程」开关。 */
    public boolean isIndependentThreadRequested() {
        refreshThreadState();
        return threadStateIndependent;
    }

    /**
     * 「独立线程」是否真生效（有仓打开即生效；与奈亚同口径，不看无线能源仓）。
     *
     * <p>★ 2026-11-03：实现 {@code IIndependentThreadHost} —— 引擎据此把「找配方」扩到
     * 本机的全部配方表（超维度熔炼 + 恒星热能熔炼），不再是「只用当前选中那张」。
     */
    @Override
    public boolean isIndependentThreadActive() {
        refreshThreadState();
        return threadStateIndependent;
    }

    /**
     * 是否逐线程去重：只由「并行重复配方」决定 —— 关 ⇒ 同一配方只允许占一条线程；
     * 开（默认）⇒ 同一配方可以填满所有线程。独立线程只管跨配方表找配方。
     * （与 {@code NyarlathotepsTentacleMachine#isThreadDedupeActive()} 同一口径。）
     */
    public boolean isThreadDedupeActive() {
        refreshThreadState();
        // ★ 2026-11-03（玩家：「线程仓那两个按钮真的有用吗」）：
        //   去重只由「并行重复配方」决定 —— 关 = 同一配方只许占一条线程；
        //   独立线程只负责「跨配方表找配方」。旧逻辑写的是 OR（独立线程开也去重），
        //   默认两个开关都开 ⇒ 默认就在去重，单配方机器多余线程全空转，
        //   而且「并行重复配方」怎么点都看不出变化。
        return !threadStateRepeated;
    }

    private long dedupeCacheTick = Long.MIN_VALUE;
    private int dedupeCacheSize = -1;
    private Set<ResourceLocation> dedupeCache = Set.of();

    private boolean isRecipeOccupiedByThread(GTRecipe recipe) {
        if (recipe == null || recipe.getId() == null) {
            return false;
        }
        if (!(getRecipeLogic() instanceof ThreadedRecipeLogic logic)) {
            return false;
        }
        long now = getOffsetTimer();
        int size = logic.runningBatches();
        if (now != dedupeCacheTick || size != dedupeCacheSize) {
            Set<ResourceLocation> ids = new HashSet<>();
            for (ThreadedRecipeLogic.BatchView view : logic.snapshotBatches()) {
                GTRecipe running = view.recipe();
                if (running != null && running.getId() != null) {
                    ids.add(running.getId());
                }
            }
            this.dedupeCache = ids;
            this.dedupeCacheTick = now;
            this.dedupeCacheSize = size;
        }
        return dedupeCache.contains(recipe.getId());
    }

    @Override
    public GTRecipe fullModifyRecipe(GTRecipe recipe, OCParams ocParams, OCResult ocResult) {
        if (isThreadDedupeActive() && isRecipeOccupiedByThread(recipe)) {
            return null;
        }
        return super.fullModifyRecipe(recipe, ocParams, ocResult);
    }

    /** 引擎/日志用的可读状态串。 */
    public String threadStateSummary() {
        return "threads=" + totalThreads()
                + " repeated=" + isRepeatedRecipesAllowed()
                + " independentRequested=" + isIndependentThreadRequested()
                + " dedupe=" + isThreadDedupeActive();
    }
}
