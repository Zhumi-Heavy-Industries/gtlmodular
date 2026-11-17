package com.gtlmodular.common.machine.fuel;

import com.gregtechceu.gtceu.api.recipe.OverclockingLogic;
import org.jetbrains.annotations.NotNull;

/**
 * ★ <b>S/E 超频 · 机器侧可选契约</b>。
 *
 * <p>任何机器（不论是不是大冰箱）实现本接口即可让
 * {@link OverclockProfiles#apply} 改用「倍速 / 能效」超频（{@link SpeedEnergyOverclock}），
 * 无需改注册处、也不影响其它机器。
 *
 * <h2>与线程引擎同构</h2>
 * 就像 {@code IThreadedRecipeMachine} 只回答「几条线程 / 并行倍率」两个问题，
 * 本接口只回答「用哪套 {@link OverclockingLogic}」。超频执行本体
 * （电压上限、亚 tick、配方 tier 校验）仍然全部交给 GTCEu 的 {@code ELECTRIC_OVERCLOCK}，
 * 因此本接口<b>不重复实现</b>任何超频细节，只做「口径选择」。
 *
 * <h2>默认口径</h2>
 * {@link #overclockLogic()} 默认返回 {@link OverclockProfiles#DEFAULT_LOGIC}
 * —— 即「实现了接口但还没定制」时行为与改造前完全一致，可安全地先接线、后调参。
 *
 * <h2>典型实现</h2>
 * <pre>{@code
 * @Override
 * public OverclockingLogic overclockLogic() {
 *     return SpeedEnergyOverclock.of(16, 0.5); // 耗时÷16、电×8
 * }
 * }</pre>
 *
 * @see OverclockProfiles
 * @see SpeedEnergyOverclock
 * @author 小爱与大肥鱼
 */
public interface ISpeedEnergyOverclockable {

    /**
     * 本机器当前该用的超频口径。
     *
     * <p>返回值会被 {@link OverclockProfiles#apply} 直接交给
     * {@code GTRecipeModifiers.ELECTRIC_OVERCLOCK.apply(...)}，因此它必须是非空的合法
     * {@link OverclockingLogic}。默认口径与改造前一致。
     *
     * @return 超频口径；<b>默认</b> {@link OverclockProfiles#DEFAULT_LOGIC}
     */
    @NotNull
    default OverclockingLogic overclockLogic() {
        return OverclockProfiles.DEFAULT_LOGIC;
    }
}
