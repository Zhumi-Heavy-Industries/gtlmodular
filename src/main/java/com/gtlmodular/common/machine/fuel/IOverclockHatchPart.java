package com.gtlmodular.common.machine.fuel;

/**
 * ★ <b>超频仓部件 · 能力接口</b> —— 结构里的超频仓对控制器开的口子。
 *
 * <p>对应 GTO {@code gtocore:overclock_hatch_*} 的语义：超频仓不改变「每升一档 4 倍功率」这个规则，
 * 只把「每升一档的时间倍率」从默认值替换成仓给的更激进值。
 *
 * <h2>GTO 语义（对齐）</h2>
 * <pre>
 *   不装仓：每使用 4 倍功率，机器耗时 ×55%（GTCEu 默认 ≈ ×50%）
 *   装仓后：每使用 4 倍功率，机器耗时 × durationFactor
 *           durationFactor = 100 / (tier - 6) / 100
 *           UV(8)=0.50 → UEV(10)=0.25(无损) → MAX(14)=0.125(越超越省电)
 * </pre>
 *
 * @see OverclockHatchPartMachine
 * @see OverclockProfiles
 * @author 小爱与大肥鱼
 */
public interface IOverclockHatchPart {

    /**
     * 每升一档超频（使用 4 倍功率）时，配方的<b>耗时倍率</b>。
     *
     * <p>例如 UV 仓返回 0.50（耗时减半），UEV 仓返回 0.25（耗时归四分之一 = 无损超频）。
     *
     * @return 耗时倍率，&gt; 0
     */
    double getOverclockDurationFactor();
}
