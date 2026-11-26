package com.gtlmodular.common.machine.engine;

/**
 * ★ 2026-11-26：<b>跨配方主机标记</b>（历史类名保留，语义已收敛）。
 *
 * <p>GTO 把线程挂在 {@code ICrossRecipeMachine} 上：线程是「跨配方」这台机器属性的一个读数，
 * 不是一台独立的功能机器。本工程与之对应的契约是 {@link IThreadedRecipeMachine}；
 * 本接口现在只回答一个问题——<b>这台多表机器允许空闲轮换配方表吗</b>（见 {@link IdleTableFollow}）。
 *
 * <p>历史背景：早期它带 {@code isIndependentThreadActive()} 方法，用来决定引擎要不要跨表找配方。
 * 2026-11-07 起跨表已由 {@link IBatchLogicOwner#crossTableScan()} 决定（与「独立线程」开关解耦），
 * 该方法再无调用点，2026-11-26 连同各实现方一起删除。保留接口仅作标记，避免触碰机器注册链。
 *
 * <p>实现方：奈亚之触 / 高维等离子聚变核心 / 力场压模零件工厂 / 木质生物质工厂 /
 * 熔岩模块主机（多表且允许样板总成带它轮换 activeRecipeType）。
 */
public interface IIndependentThreadHost {
}
