package com.gtlmodular.common.machine.independent.control;

public final class UpstreamRecipeSetup<R> {
    private R merged;

    public void clear() { merged = null; }

    public void remember(R recipe) { merged = recipe; }

    public boolean consume(R recipe) {
        // 合批返回时已扣料；只允许同一对象沿用该次放行，不能放行另一次开批。
        boolean allowed = recipe != null && recipe == merged;
        clear();
        return allowed;
    }
}
