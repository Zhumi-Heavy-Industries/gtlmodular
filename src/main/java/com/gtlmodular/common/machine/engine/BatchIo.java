package com.gtlmodular.common.machine.engine;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gtlmodular.GTLModular;
import org.gtlcore.gtlcore.api.recipe.RecipeRunnerHelper;

/**
 * 通用独立线程引擎 · <b>批次与机器的交接</b>。
 *
 * <p>引擎（{@link ConcurrentRecipeEngine}）只认 {@link ConcurrentRecipeEngine.Host} 的六个问题；
 * 本类回答其中三个「会碰世界」的：开批次时原子扣料、每 tick 输入、收尾出料。
 *
 * <p><b>写世界的边界（与旧实现逐字一致）</b>：
 * <ul>
 *   <li>{@link #consumeInputs}：{@code beforeWorking → preWorking → 扣输入}，扣不齐就
 *       {@code afterWorking} 收尾并拒绝 —— <b>绝不吞料</b>；</li>
 *   <li>{@link #tickInputs}：先 {@code matchTickRecipe} 只读模拟，通过才真扣；不够 ⇒ 该批次原地等；</li>
 *   <li>{@link #finishOutputs}：{@code afterWorking → postWorking → 出料}，收尾异常只打 WARN。</li>
 * </ul>
 */
final class BatchIo {

    /** 每 20 tick 最多打几条「开机/完成」INFO（其余降到 DEBUG，避免大单时刷屏拖慢服务器线程）。 */
    static final int BATCH_LOG_PER_WINDOW = 4;
    static final int BATCH_LOG_WINDOW_TICKS = 20;

    private final IRecipeLogicMachine machine;
    private final ThreadedRecipeLogic owner;
    private final BatchFinder finder;
    private final BatchDiagnostics diag;

    private long batchLogWindow = Long.MIN_VALUE;
    private int batchLogUsed;

    BatchIo(IRecipeLogicMachine machine, ThreadedRecipeLogic owner,
            BatchFinder finder, BatchDiagnostics diag) {
        this.machine = machine;
        this.owner = owner;
        this.finder = finder;
        this.diag = diag;
    }

    /**
     * 原子扣料：{@code beforeWorking} → {@code preWorking} → 扣输入。
     *
     * <p>扣不齐（{@code handleRecipeInput} 返回 false）时调 {@code afterWorking} 收尾并返回 false
     * ⇒ 引擎不收这条批次（<b>绝不吞料</b>）。
     */
    boolean consumeInputs(GTRecipe recipe) {
        if (!machine.beforeWorking(recipe)) {
            diag.reject("beforeWorking 拒绝（线圈温度/配方门槛）: " + recipe.getId(), null, null);
            return false;
        }
        recipe.preWorking(machine);
        if (!RecipeRunnerHelper.handleRecipeInput(machine, recipe)) {
            diag.reject("扣料失败(输入仓/ME样板仓不够): " + recipe.getId(), null, null);
            machine.afterWorking();
            return false;
        }
        // ★ 2026-11-07：日志限流（每 20 tick 最多 4 条 INFO，其余 DEBUG）——
        //   大单时 1 tick 开数条批次，逐条 INFO 同步写盘会放大服务器卡顿。
        if (takeBatchLogSlot()) {
            GTLModular.LOGGER.info("[gtlmodular] 线程引擎开机 @{}：{}", machine.self().getPos(), recipe.getId());
        } else {
            GTLModular.LOGGER.debug("[gtlmodular] 线程引擎开机 @{}：{}", machine.self().getPos(), recipe.getId());
        }
        finder.clearCooldowns();
        return true;
    }

    /**
     * 每 tick 的输入：先<b>模拟</b>匹配（{@code matchTickRecipe}，不写世界），
     * 匹配通过才真扣这一 tick 的输入。不够返回 false ⇒ 该批次原地等。
     */
    boolean tickInputs(GTRecipe recipe) {
        if (!recipe.matchTickRecipe(machine).isSuccess()) {
            return false;
        }
        return recipe.handleTickRecipeIO(IO.IN, machine, owner.chanceCaches());
    }

    /** 批次收尾：机器收尾 → {@code postWorking} → 出料。 */
    void finishOutputs(GTRecipe recipe) {
        boolean outputOk;
        try {
            machine.afterWorking();
            recipe.postWorking(machine);
            outputOk = RecipeRunnerHelper.handleRecipeOutput(machine, recipe);
        } catch (Throwable failure) {
            outputOk = false;
            GTLModular.LOGGER.warn("[gtlmodular] 线程批次收尾异常 @{}: {}",
                    machine.self().getPos(), failure.toString());
        }
        // ★ 2026-11-05（队长要求）：每条批次完成都留痕——“批次到底是跑完了还是被清掉了”再也不用猜。
        // ★ 2026-11-07：INFO 限流（每 20 tick 最多 4 条），其余降到 DEBUG；产物摘要只在 INFO 时算。
        if (takeBatchLogSlot()) {
            GTLModular.LOGGER.info("[gtlmodular] 线程批次完成 @{}：{}（输出处理={}，耗时={}t）产物={}",
                    machine.self().getPos(),
                    recipe == null || recipe.getId() == null ? "?" : recipe.getId(),
                    outputOk ? "成功" : "失败",
                    recipe == null ? "?" : recipe.duration,
                    BatchSupport.describeOutputs(recipe));
        } else if (GTLModular.LOGGER.isDebugEnabled()) {
            GTLModular.LOGGER.debug("[gtlmodular] 线程批次完成 @{}：{}（输出处理={}，耗时={}t）",
                    machine.self().getPos(),
                    recipe == null || recipe.getId() == null ? "?" : recipe.getId(),
                    outputOk ? "成功" : "失败",
                    recipe == null ? "?" : recipe.duration);
        }
    }

    /**
     * 取一个「开机/完成」INFO 名额（每 20 tick 最多 {@value #BATCH_LOG_PER_WINDOW} 条）。
     * 拿不到就降级成 DEBUG —— 大单时 1 tick 开 4 条批次也不会把主线程拖在同步日志 I/O 上。
     */
    private boolean takeBatchLogSlot() {
        long window = machine.self().getOffsetTimer() / BATCH_LOG_WINDOW_TICKS;
        if (window != batchLogWindow) {
            batchLogWindow = window;
            batchLogUsed = 0;
        }
        if (batchLogUsed >= BATCH_LOG_PER_WINDOW) {
            return false;
        }
        batchLogUsed++;
        return true;
    }
}
