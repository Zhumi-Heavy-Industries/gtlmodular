package com.gtlmodular.common.machine.fuel;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.OverclockingLogic;
import com.gregtechceu.gtceu.api.recipe.logic.OCParams;
import com.gregtechceu.gtceu.api.recipe.logic.OCResult;
import com.gregtechceu.gtceu.common.data.GTRecipeModifiers;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * ★ <b>通用超频口径入口</b> —— 所有机器的超频都从这里走，<b>不绑定任何具体机器</b>。
 *
 * <h2>为什么需要这一层</h2>
 * 本工程原先有十几处直接写 {@code GTRecipeModifiers.ELECTRIC_OVERCLOCK.apply(<某个常量>)}，
 * 每处各自挑一个 {@link OverclockingLogic}（完美 / 非完美 / 亚 tick …）。
 * 结果：想换一种超频口径，得逐台机器找、逐处改，且极易漏改。
 *
 * <p>本类把这些收敛成<b>一个统一入口</b>：
 * <ul>
 *   <li>机器默认走 {@link #DEFAULT_LOGIC}（GTCEu 完美超频 + 亚 tick，与既有手感一致）；</li>
 *   <li>机器若实现了 {@link ISpeedEnergyOverclockable}，则改走它给的 S/E 口径；</li>
 *   <li>任何一处调用都只是 {@code OverclockProfiles.apply(machine, recipe, params, result)}。</li>
 * </ul>
 *
 * <h2>和线程引擎的关系</h2>
 * 与 {@code ThreadedRecipeLogic} 同一条设计哲学：<b>入口不依赖机器类</b>，
 * 只通过 {@link ISpeedEnergyOverclockable} 这个可选契约向机器提问。
 * 机器不实现它 ⇒ 用默认口径，一行都不用改。
 *
 * <h2>和 gtlcore / gtceu 原有超频的关系</h2>
 * 本类只是<b>选择用哪个 {@link OverclockingLogic}</b>，最终仍交给
 * {@code GTRecipeModifiers.ELECTRIC_OVERCLOCK} 执行 ⇒ 电压上限（{@code maxVoltage}）、
 * 亚 tick 转并行、配方 tier 校验等既有行为<b>全部沿用</b>，不另起炉灶。
 *
 * @see SpeedEnergyOverclock
 * @see ISpeedEnergyOverclockable
 * @author 小爱与大肥鱼
 */
public final class OverclockProfiles {

    private OverclockProfiles() {
    }

    /**
     * 默认超频口径：GTCEu 完美超频（亚 tick 版）。
     *
     * <p>与改造前本工程大型多方块的既有一致口径（每档耗时 ×1/4、EUt ×4，亚 1 tick 时转并行）。
     */
    public static final OverclockingLogic DEFAULT_LOGIC = OverclockingLogic.PERFECT_OVERCLOCK_SUBTICK;

    /**
     * 通用超频执行入口。
     *
     * <p>取口径顺序：机器实现 {@link ISpeedEnergyOverclockable} ⇒ 用它；否则用 {@link #DEFAULT_LOGIC}。
     *
     * @param machine 正在算配方的机器
     * @param recipe  原始配方
     * @param params  超频输入（EUt / 耗时 / 可超频档数）
     * @param result  超频输出
     * @return 超频后的配方；口径判定不允许时返回 {@code null}（与原版一致：配方停摆）
     */
    @Nullable
    public static GTRecipe apply(@NotNull MetaMachine machine, @NotNull GTRecipe recipe,
                                 @NotNull OCParams params, @NotNull OCResult result) {
        return apply(machine, recipe, params, result, logicFor(machine));
    }

    /**
     * 用<b>显式指定</b>的口径执行（供需要按线圈温度等动态选择口径的机器用）。
     *
     * @param logic 这次要用的超频口径
     */
    @Nullable
    public static GTRecipe apply(@NotNull MetaMachine machine, @NotNull GTRecipe recipe,
                                 @NotNull OCParams params, @NotNull OCResult result,
                                 @NotNull OverclockingLogic logic) {
        return GTRecipeModifiers.ELECTRIC_OVERCLOCK.apply(logic).apply(machine, recipe, params, result);
    }

    /**
     * 按机器当前状态挑口径。
     *
     * <p><b>优先级</b>（前者优先）：
     * <ol>
     *   <li>结构里装了<b>超频仓</b>（{@link IOverclockHatchPart}）⇒ 现读它的 S/E；</li>
     *   <li>机器自己实现了 {@link ISpeedEnergyOverclockable} ⇒ 用它给的口径；</li>
     *   <li>都没有 ⇒ {@link #DEFAULT_LOGIC}（与改造前一致）。</li>
     * </ol>
     *
     * <p>「现读」意味着超频仓换档、拆装都立即生效，不需要机器类写死任何 S/E，
     * 也不需要重启 —— 与线程仓现读线程数是同一条设计。
     *
     * @param machine 机器
     * @return 该机器该用的 {@link OverclockingLogic}
     */
    @NotNull
    public static OverclockingLogic logicFor(@NotNull MetaMachine machine) {
        IOverclockHatchPart hatch = findOverclockHatch(machine);
        if (hatch != null) {
            // GTO 口径：只调「每档 4 倍功率」下的耗时倍率，电压倍率固定 4。
            return new OverclockingLogic(hatch.getOverclockDurationFactor(),
                    OverclockingLogic.STD_VOLTAGE_FACTOR, true);
        }
        if (machine instanceof ISpeedEnergyOverclockable se) {
            return se.overclockLogic();
        }
        return DEFAULT_LOGIC;
    }

    /**
     * 在多方块结构里找超频仓（没有 / 不成形 / 不是多方块 ⇒ {@code null}）。
     *
     * <p>结构谓词已经把超频仓限制为最多 1 台，所以取第一个即可；
     * 万一存在多台（旧存档 / 结构自定义），以<b>最后找到的那台</b>为准并只算一次，
     * 不叠加（超频仓不是并行仓，叠加没有意义）。
     *
     * @param machine 机器
     * @return 找到的超频仓部件；没有则 {@code null}
     */
    @Nullable
    public static IOverclockHatchPart findOverclockHatch(@NotNull MetaMachine machine) {
        if (!(machine instanceof IMultiController controller) || !controller.isFormed()) {
            return null;
        }
        IOverclockHatchPart found = null;
        for (IMultiPart part : controller.getParts()) {
            if (part instanceof IOverclockHatchPart hatch) {
                found = hatch;
            }
        }
        return found;
    }

    /**
     * 往机器界面补一行「当前超频口径」，供各机器的 {@code addDisplayText} 调用。
     *
     * <p>装了超频仓但界面看不出来，玩家无法确认是否生效。本方法把当前口径写清楚。
     *
     * @param machine 机器
     * @param lines   界面行列表
     */
    public static void addOverclockDisplayLine(@NotNull MetaMachine machine, @NotNull List<Component> lines) {
        IOverclockHatchPart hatch = findOverclockHatch(machine);
        if (hatch != null) {
            double pct = hatch.getOverclockDurationFactor() * 100.0;
            lines.add(Component.translatable("gtlmodular.overclock_hatch.display", num(pct))
                    .withStyle(ChatFormatting.AQUA));
        } else {
            lines.add(Component.translatable("gtlmodular.overclock_hatch.display.default")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    /** 32.0 → "32"，0.5 → "0.5"（仅显示）。 */
    private static String num(double value) {
        if (value == Math.floor(value) && !Double.isInfinite(value)) {
            return Long.toString((long) value);
        }
        return Double.toString(value);
    }
}
