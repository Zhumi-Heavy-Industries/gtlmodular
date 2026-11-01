package com.gtlmodular.common.machine.engine;

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
}
