package com.gtlmodular.common.machine.fuel;

import com.gregtechceu.gtceu.api.recipe.OverclockingLogic;
import com.gregtechceu.gtceu.api.recipe.logic.OCParams;
import com.gregtechceu.gtceu.api.recipe.logic.OCResult;
import org.jetbrains.annotations.NotNull;

/**
 * 「倍速 / 能效」超频算法（S/E 模型）—— 本模组自己的独立实现。
 *
 * <h2>这是什么 / 为什么不是原版超频</h2>
 * GTCEu 原版标准超频每升 1 档固定是「耗时 ×1/4、EUt ×4」：
 * <ul>
 *   <li>想更快就只能继续 ×4 电，很快撞到机器可承受电压上限（{@code maxVoltage}）超不动；</li>
 *   <li>档数又被「机器等级 − 配方等级」卡死，速度和电费无法分开调。</li>
 * </ul>
 * 本算法的思路是把「速度」和「电费」拆成两个独立参数：
 * <pre>
 *   每消耗 1 档超频：
 *     耗时 × (1 / S)          // S = 速度倍率（speed）
 *     EUt  × (E × S)          // E = 能效系数（energyFactor）
 * </pre>
 * 于是：
 * <ul>
 *   <li>{@code S=4, E=1}   ⇒ 耗时÷4、电×4，等价原版 perfect；</li>
 *   <li>{@code S=16, E=0.5} ⇒ 耗时÷16、电×8，<b>越超越省电</b>；</li>
 *   <li>{@code S=4, E=2}   ⇒ 耗时÷4、电×8，损能型。</li>
 * </ul>
 *
 * <h2>循环终止条件（先判电、再判时间）</h2>
 * <ol>
 *   <li>{@code eut × (E×S) > maxVoltage} ⇒ 停（不许超出机器可承受电压）；</li>
 *   <li>{@code duration × (1/S) < 1} ⇒ 停（耗时不许低于 1 tick，且这一档整体不生效）。</li>
 * </ol>
 * 这两条与 GTCEu {@link OverclockingLogic#standardOverclockingLogic} 的语义保持一致。
 *
 * <h2>并行</h2>
 * 本算法<b>只改速度</b>，{@code parallel} 恒为 1（并行仍由并行仓 / 线程引擎负责），
 * 因此走 3 参 {@link OCResult#init(long, int, int)}，不触碰 parallel 字段。
 *
 * <h2>实现来源</h2>
 * 算法思路参考自 GTET（GregTech Eternal Time，作者 rain fox，LGPL-3.0）公开的 S/E 超频模型，
 * <b>本类为独立重写</b>（未复制其源码）。数学公式本身不受著作权保护；
 * 若需追溯，见 {@code NOTICE_第三方素材_GTO.md} 中的「致谢」一节。
 *
 * <p>用法：{@code GTRecipeModifiers.ELECTRIC_OVERCLOCK.apply(SpeedEnergyOverclock.of(16, 0.5))}。
 *
 * @author 小爱与大肥鱼
 */
public final class SpeedEnergyOverclock {

    private SpeedEnergyOverclock() {
    }

    /**
     * 造一个「S 倍速 / E 能效」的 {@link OverclockingLogic}。
     *
     * @param speed        速度倍率 S（每档耗时 ÷S），必须 ≥ 1
     * @param energyFactor 能效系数 E（每档 EUt × E×S），必须 &gt; 0
     * @return 可直接交给 {@code GTRecipeModifiers.ELECTRIC_OVERCLOCK.apply(...)} 的超频逻辑
     * @throws IllegalArgumentException 参数越界
     */
    public static OverclockingLogic of(int speed, double energyFactor) {
        if (speed < 1) {
            throw new IllegalArgumentException("speed 必须 >= 1，收到 " + speed);
        }
        if (!(energyFactor > 0.0) || Double.isNaN(energyFactor) || Double.isInfinite(energyFactor)) {
            throw new IllegalArgumentException("energyFactor 必须是正有限数，收到 " + energyFactor);
        }
        final double durationFactor = 1.0 / speed;
        final double eutFactor = energyFactor * speed;
        return new OverclockingLogic((params, result, maxVoltage) ->
                runOverclock(params, result, maxVoltage, durationFactor, eutFactor));
    }

    /**
     * 核心算法：把 {@code params}（配方原始 EUt / 耗时 / 可用档数）按 S/E 逐档推进，结果写入 {@code result}。
     *
     * <p>这是 {@link OverclockingLogic.Logic} 的实现体，单独抽成静态方法便于单测与复用。
     *
     * @param params         配方超频输入（原始 EUt、原始耗时、可超频档数）
     * @param result         超频输出，写回 EUt / 耗时 / 实际用掉的档数
     * @param maxVoltage     机器可承受的最高电压（EUt 上限）
     * @param durationFactor 每档耗时倍率（= 1/S）
     * @param eutFactor      每档 EUt 倍率（= E×S）
     */
    public static void runOverclock(@NotNull OCParams params, @NotNull OCResult result, long maxVoltage,
                                    double durationFactor, double eutFactor) {
        double eut = params.getEut();
        double duration = params.getDuration();
        int remaining = params.getOcAmount();
        int ocLevel = 0;

        while (remaining-- > 0) {
            // ① 先判电：这一档加上去会不会超过机器可承受电压
            double nextEut = eut * eutFactor;
            if (nextEut > (double) maxVoltage) {
                break;
            }
            // ② 再判时间：耗时不许降到 1 tick 以下；不满足则整档作废
            double nextDuration = duration * durationFactor;
            if (nextDuration < 1.0) {
                break;
            }
            eut = nextEut;
            duration = nextDuration;
            ocLevel++;
        }

        result.init((long) eut, (int) duration, ocLevel);
    }
}
