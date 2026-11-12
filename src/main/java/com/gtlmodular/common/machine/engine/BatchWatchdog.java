package com.gtlmodular.common.machine.engine;

import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gtlmodular.GTLModular;

/**
 * 通用独立线程引擎 · <b>停滞看门狗</b>。
 *
 * <p>玩家报「部件组装机第一次下单会卡、调一个电路就不卡」：换模式/换电路会
 * {@code resetRecipeLogic()} 把批次清掉，于是机器恢复；但旧日志里看不出批次是卡在
 * 「每 tick 输入不足」还是「输出/别的」。本类只做一件事：引擎里还有批次、却连续
 * {@link #STUCK_WARN_TICKS} tick 一点进度都没有 ⇒ 打一行 WARN，把第 0 条批次的
 * 配方/进度/每 tick 输入是否够都写出来，下一次复现直接定位。
 *
 * <p><b>只读</b>：不改批次、不碰库存、不参与调度；日志限频（同一条机器每
 * {@link #STUCK_WARN_TICKS} tick 最多一行）。
 */
final class BatchWatchdog {

    /** 批次停滞多久（tick）打一次 WARN；同一条机器最多每 200 tick 一行。 */
    static final int STUCK_WARN_TICKS = 200;

    /** 上次进度总和 / 对应 tick（用于停滞检测）。 */
    private int lastProgressSum = -1;
    private long lastProgressSumTick = Long.MIN_VALUE;
    private long lastStuckWarnTick = Long.MIN_VALUE;

    /**
     * 每个 server tick（引擎推进之后）调一次。
     *
     * <p>调用点必须与旧实现一致：在 {@link ConcurrentRecipeEngine#tick} 之后、展示回写之前。
     */
    void tick(ConcurrentRecipeEngine engine, IRecipeLogicMachine machine) {
        int progressNow = progressSum(engine);
        long nowTick = machine.self().getOffsetTimer();
        if (progressNow != lastProgressSum) {
            lastProgressSum = progressNow;
            lastProgressSumTick = nowTick;
        } else if (engine.size() > 0 && lastProgressSumTick != Long.MIN_VALUE
                && nowTick - lastProgressSumTick >= STUCK_WARN_TICKS
                && (lastStuckWarnTick == Long.MIN_VALUE || nowTick - lastStuckWarnTick >= STUCK_WARN_TICKS)) {
            lastStuckWarnTick = nowTick;
            ConcurrentRecipeEngine.Batch primary = engine.get(0);
            boolean tickInputOk;
            try {
                tickInputOk = primary.recipe.matchTickRecipe(machine).isSuccess();
            } catch (Throwable t) {
                tickInputOk = false;
            }
            GTLModular.LOGGER.warn("[gtlmodular] 线程引擎批次停滞 @{}：{} 条批次已 {} tick 没有进度；"
                            + "第0条 {} 进度 {}/{}，每tick输入={}。"
                            + "（换模式/换电路会 resetRecipeLogic 清空批次，已扣料不退）",
                    machine.self().getPos(), engine.size(), nowTick - lastProgressSumTick,
                    primary.recipe.getId(), primary.progress, primary.duration,
                    tickInputOk ? "够" : "不足");
        }
    }

    /** 当前所有批次进度之和（停滞检测用）。 */
    private static int progressSum(ConcurrentRecipeEngine engine) {
        int sum = 0;
        for (int i = 0; i < engine.size(); i++) {
            sum += engine.get(i).progress;
        }
        return sum;
    }
}
