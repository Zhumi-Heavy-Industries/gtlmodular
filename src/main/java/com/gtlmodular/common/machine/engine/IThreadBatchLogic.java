package com.gtlmodular.common.machine.engine;

import java.util.List;

/**
 * ★ 2026-11-05：<b>「能逐条报进度」的线程引擎逻辑</b>的公共读口。
 *
 * <p>原本 Jade 的通用线程显示只认 {@link ThreadedRecipeLogic} 这个<b>具体类</b>
 * （{@code NanochipThreadedJadeProvider#genericThreadedLogic}），于是地心三个附属模块
 * 自己的线程逻辑（{@code MoltenModuleMachineBase.MoltenModuleRecipeLogic}）接不进 Jade：
 * 玩家只能看到被镜像进基类字段的<b>第 0 条</b>进度，看起来像「只有一条线程」。
 *
 * <p>本接口只暴露三个<b>纯只读</b>读数，不参与任何调度/存档：
 * <ul>
 *   <li>{@link #threadLimit()} —— 线程上限（没装线程仓 / 不生效时为 1）；</li>
 *   <li>{@link #runningBatches()} —— 当前在用条数；</li>
 *   <li>{@link #snapshotBatches()} —— 每条批次的配方/进度/时长/并行快照。</li>
 * </ul>
 * 实现方：{@link ThreadedRecipeLogic}（通用引擎）与地心模块的线程逻辑。
 * Jade 侧（{@code NanochipThreadedJadeProvider#appendServerData}）只认本接口，
 * 因此不会误命中别人的多线程机器。
 */
public interface IThreadBatchLogic {

    /** 线程上限（&ge;1）。 */
    int threadLimit();

    /** 当前在跑的批次数。 */
    int runningBatches();

    /** 每条在跑批次的只读快照（服务端求值；客户端拿不到，读到的通常是空表）。 */
    List<ThreadedRecipeLogic.BatchView> snapshotBatches();
}
