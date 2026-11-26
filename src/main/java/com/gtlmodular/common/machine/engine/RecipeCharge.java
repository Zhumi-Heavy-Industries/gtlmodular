package com.gtlmodular.common.machine.engine;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;

/**
 * ★ 2026-11-26：<b>跨配方资源预留契约</b>（对齐 GTO
 * {@code CrossRecipeTrait.RecipeCharge}）。
 *
 * <p>GTO 的线程在真正开跑前先走一遍 {@code check → commit}，失败则 {@code rollback}，
 * 保证「找配方」看到的库存与「开线程」扣掉的库存是同一份。本工程把这一步显式化：
 * {@link BatchFinder#lookupRecipe(RecipeCharge)} 里所有只读判定通过后调用
 * {@link #check(GTRecipe)}，真正收下配方时调用 {@link #commit()}，任何后续失败都回滚。
 *
 * <p>本工程的 {@code commit()} 之前没有副作用（只读匹配），所以 {@code rollback()} 默认是空操作；
 * 它存在的意义是把「预留点」固定在接口上，避免以后有人把扣料提前到扫描阶段却忘了回滚路径。
 */
public interface RecipeCharge {

    /** 只读检查：这条配方的资源是否还能被这条线程占用。 */
    boolean check(GTRecipe recipe);

    /** 提交预留（真正扣料）；失败返回 false。 */
    boolean commit();

    /** 回滚本次预留（提交前无副作用则为空操作）。 */
    void rollback();
}
