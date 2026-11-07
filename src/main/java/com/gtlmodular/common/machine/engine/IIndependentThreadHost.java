package com.gtlmodular.common.machine.engine;

/**
 * 线程引擎的「独立线程宿主」契约：由装了线程仓（{@code IThreadHatchPart}）的机器实现。
 *
 * <p><b>为什么抽这一层</b>：独立线程开关（线程仓左列第二个开关）的语义是
 * 「每条线程各自找配方，可以跨机器当前选中的那张配方表」，这不只奈亚之触需要 ——
 * 高维等离子聚变核心（氢原子也在用同一个 {@link ThreadedRecipeLogic}）同样有两张配方表。
 * 引擎只认这个接口，不再 {@code instanceof} 某台具体机器。
 *
 * <p>实现方（2026-11-03）：
 * <ul>
 *   <li>{@code NyarlathotepsTentacleMachine#isIndependentThreadActive()}；</li>
 *   <li>{@code HyperdimensionalPlasmaFusionCoreMachine#isIndependentThreadActive()}。</li>
 * </ul>
 *
 * <p>返回 {@code true} ⇒ 引擎在 {@code findBatch()} 里遍历该机的<b>全部</b>配方表
 * （从上次成功的那张开始轮转）；返回 {@code false} ⇒ 行为与旧版一致，只用当前选中的表。
 */
public interface IIndependentThreadHost {

    /**
     * 独立线程当前是否生效（结构里至少一个线程仓的「独立线程」开关打开）。
     *
     * <p>未成形 / 没有线程仓时实现方应返回 {@code false}（退回单表行为）。
     */
    boolean isIndependentThreadActive();
}
