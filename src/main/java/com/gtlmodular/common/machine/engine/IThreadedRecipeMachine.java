package com.gtlmodular.common.machine.engine;

import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gtlmodular.common.machine.tentacle.IThreadHatchPart;

/**
 * ★ <b>独立线程引擎 · 机器侧契约</b>。
 *
 * <p>「独立线程引擎」指的是<b>同一台机器上同时推进多条配方</b>：每条配方各有各的
 * {@code progress}／{@code duration}，互不排队，由 {@link ConcurrentRecipeEngine} 统一调度，
 * 由 {@link ThreadedRecipeLogic} 接到 GTCEu 的 {@code RecipeLogic} 上。
 *
 * <p>本接口只回答两个「机器相关」的问题；引擎本体（{@link ConcurrentRecipeEngine}）
 * <b>不依赖任何机器类</b>，只认这两个数字 ⇒ 调度逻辑可以被单独看懂、单独核对。
 *
 * <p><b>实现方约定</b>：实现本接口的类必须同时实现
 * {@link com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine}
 * —— {@link ThreadedRecipeLogic} 在构造时会把传入的 machine 直接强转成本接口。
 *
 * @see ConcurrentRecipeEngine
 * @see ThreadedRecipeLogic
 */
public interface IThreadedRecipeMachine {

    /**
     * 同时并发跑几条配方（线程数），机器返回 8。
     *
     * <p>槽位满了不再收新配方；有批次跑完空出位置，下一个 server tick 立刻补一条。
     *
     * @return 并发槽位数；小于 1 时引擎按 1 处理
     */
    int baseThreads();

    /**
     * 在标准并行仓给出的并行数上再乘的倍率；普通多方块返回 1，巨构按线圈温度返回 2^n。
     *
     * <p>乘算关系见 {@link ThreadedRecipeLogic} 的 {@code findBatch}：
     * 当 {@link #usesParallelHatch()} 为 {@code true} 时，{@code 本倍率 × 并行仓并行数}
     * 一起交给 {@code GTRecipeModifiers.accurateParallel}；为 {@code false} 时只看本倍率。
     *
     * @return 并行倍率；小于 1 时引擎按 1 处理
     */
    int parallelMultiplier();

    /**
     * 并行数是否再乘结构里的标准并行仓。
     *
     * <p>默认 {@code true}（普通多方块：8 线程 × 并行仓）。
     * 巨构按队长口径<b>只看线圈温度</b>，覆写成 {@code false} ⇒ 结构里即使放了并行仓也不乘。
     */
    default boolean usesParallelHatch() {
        return true;
    }

    /**
     * ★ 2026-11-05：<b>结构里线程仓的当前设定值之和</b>（没装 / 未成形 ⇒ 0）。
     *
     * <p>「通用引擎的线程数跟线程仓相关」的统一读口：所有实现本接口的机器都可以写成
     * <pre>{@code
     * int hatch = threadHatchThreads();
     * return hatch > 0 ? hatch : 原本的固定线程数;
     * }</pre>
     * ⇒ <b>装了线程仓就完全按仓里的设定值</b>（仓 4 线程 = 引擎 4 线程），没装时保持原值。
     * 结构里线程仓数量由各结构谓词自己限制（本模组一律 {@code setMaxGlobalLimited(1)}）。
     */
    default int threadHatchThreads() {
        if (!(this instanceof IMultiController controller)) {
            return 0;
        }
        long total = 0L;
        for (IMultiPart part : controller.getParts()) {
            if (part instanceof IThreadHatchPart hatch) {
                total += Math.max(0, hatch.getThreads());
                if (total >= Integer.MAX_VALUE) {
                    return Integer.MAX_VALUE;
                }
            }
        }
        return (int) total;
    }

    /**
     * ★ 2026-11-07：<b>引擎实际使用的线程数</b>——装线程仓就完全按仓，没装才用机器自己的固定值。
     *
     * <p>这是「机器只要在结构里允许线程仓，就直接能用、不用再改机器类」的统一读口：
     * 机器只需要把 {@code THREAD_HATCH} 加进结构谓词，线程数/开关全部由引擎从部件上现读。
     */
    default int effectiveThreads() {
        int hatch = threadHatchThreads();
        return Math.max(1, hatch > 0 ? hatch : baseThreads());
    }

    /** 线程仓「并行重复配方」：全部仓都允许才算允许；没装仓 ⇒ 允许（GTO 默认）。 */
    default boolean threadHatchRepeatedRecipes() {
        if (!(this instanceof IMultiController controller)) {
            return true;
        }
        for (IMultiPart part : controller.getParts()) {
            if (part instanceof IThreadHatchPart hatch && !hatch.isRepeatedRecipes()) {
                return false;
            }
        }
        return true;
    }

    /**
     * 线程仓「独立线程」（GTO 语义）：全部仓都打开才算独立；没装仓 ⇒ 独立。
     *
     * <p>开 = 每条线程各自有一份完整的并行/功率额度（GTO：「每个线程都能独立拉取机器最大功率」）；
     * 关 = 所有线程共用同一份额度（整机并行不再随线程数翻倍）。
     */
    default boolean threadHatchIndependent() {
        if (!(this instanceof IMultiController controller)) {
            return true;
        }
        boolean found = false;
        for (IMultiPart part : controller.getParts()) {
            if (part instanceof IThreadHatchPart hatch) {
                found = true;
                if (!hatch.isIndependentThread()) {
                    return false;
                }
            }
        }
        return true; // 没装仓：按“独立”处理，保持机器原行为
    }
}
