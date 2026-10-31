package com.gtlmodular.common.machine.output;

/**
 * 刷写失败后的指数退避（1,2,4,8,16,32,64 tick，封顶 64）。
 *
 * <p>为什么要退避：ME 满了或断网时，每 tick 重试只是空转；而等下游恢复后又必须尽快恢复，
 * 所以成功一次就整体归零。</p>
 */
public final class FlushBackoff {
    private static final int MAX_SHIFT = 6;

    private int attempt;
    private long readyAt;

    /** 现在是否允许尝试刷写。 */
    public boolean shouldAttempt(long gameTime) {
        return gameTime >= readyAt;
    }

    /** 刷成功（哪怕只刷出一部分）：清零，下一轮立刻可再试。 */
    public void onSuccess() {
        attempt = 0;
        readyAt = 0;
    }

    /** 完全刷不动（下游拒收）：按 2^n tick 推迟下一次尝试。 */
    public void onFailure(long gameTime) {
        long delay = 1L << Math.min(attempt, MAX_SHIFT);
        readyAt = gameTime + delay;
        if (attempt <= MAX_SHIFT) {
            attempt++;
        }
    }

    /** 当前退避指数（0 表示没在退避），主要用于显示与调试。 */
    public int attempt() {
        return attempt;
    }
}
