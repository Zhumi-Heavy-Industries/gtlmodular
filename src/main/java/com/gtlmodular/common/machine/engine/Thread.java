package com.gtlmodular.common.machine.engine;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;

/**
 * ★ 2026-11-26：<b>跨配方线程</b>（GTO {@code ICrossRecipeMachine.Thread} 的等价物）。
 *
 * <p>一台跨配方机器同时跑多条配方，每条就是一个 {@code Thread}：
 * <ul>
 *   <li>{@code recipe} —— 已经套过修改器/并行、由 {@link RecipeCharge} 提交过的最终配方；</li>
 *   <li>{@code duration} —— 开线程时钉死的总时长，不每 tick 回读，避免进度条跳；</li>
 *   <li>{@code progress} —— 本线程自己的进度；</li>
 *   <li>{@code use} —— 本线程实际吃到的并行额度（GTO 同名字段），Jade/Waila 的 R 读数只读它；</li>
 *   <li>{@code id} —— 稳定的槽位号，给 Jade/Waila 编号用（GTO 的 {@code getID}）。</li>
 * </ul>
 *
 * <p>各线程互不共享 {@code recipe} 对象。线程不自己订阅 tick，由
 * {@link ConcurrentRecipeEngine#tick(ConcurrentRecipeEngine.Host)} 统一推进。
 */
public final class Thread {

    public final GTRecipe recipe;
    public final int duration;
    public int progress;
    public final long use;
    public final int id;

    private static int nextId;

    public Thread(GTRecipe recipe, int id) {
        this.recipe = recipe;
        this.duration = Math.max(1, recipe.duration);
        this.use = Math.max(1L, recipe.parallels);
        this.id = id;
    }

    /** 读档恢复：id 重新分配，进度按存档恢复。 */
    static Thread restore(GTRecipe recipe, int progress, int id) {
        Thread thread = new Thread(recipe, id);
        thread.progress = Math.max(0, progress);
        return thread;
    }

    /** 分配一个进程内唯一的线程号（跨读档递增，仅用于显示编号）。 */
    public static int nextId() {
        return nextId++;
    }

    public boolean finished() {
        return progress >= duration;
    }

    public float getProgressPercent() {
        return Math.min(1.0F, (float) progress / (float) duration);
    }

    public int getID() {
        return id;
    }
}
