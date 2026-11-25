package com.gtlmodular.common.machine.fuel;

import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.OverclockingLogic;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.api.recipe.logic.OCParams;
import com.gregtechceu.gtceu.api.recipe.logic.OCResult;
import com.gtlmodular.common.config.GTLModularConfig;

/**
 * ★ <b>超频仓 · 越界超频（MAX 之上）</b> —— 让装了超频仓的机器继续往上超频。
 *
 * <h2>为什么要这一层（超频仓对高价配方必然 0 档）</h2>
 * <p>GTCEu 的档位函数对电压是<b>饱和</b>的：
 * <pre>
 *   GTUtil.getTierByVoltage(v) = v &gt; 2^31-1 ? 14 : getOCTierByVoltage(v)
 * </pre>
 * 而 {@code RecipeHelper.performOverclocking} 的档数 =
 * {@code maximumTier - recipeTier}，两个数都用这个函数算：
 * <ul>
 *   <li>配方 EUt 超过 2^31-1（也就是按 {@code n × VA[MAX]} 定价的超维度熔炼那批）⇒ recipeTier 恒 14；</li>
 *   <li>机器可超频电压来自能源仓输入电压（MAX 档 = 2^31），⇒ maximumTier 也恒 14；</li>
 *   <li>⇒ <b>档数 = 0，超频根本没开始</b>，仓的耗时倍率无处可乘。</li>
 * </ul>
 * 所以「超频仓没生效」不是链路断了，是 GTCEu 的超频体系<b>到 MAX 就结束了</b>。
 *
 * <h2>本类的口径</h2>
 * <p>不动 GTCEu 的档位函数（它是全包公用的：电缆、仓、机器等级都靠它），只在
 * 「机器装了超频仓」时，<b>按每 tick 真正买得起的 4 倍功率档继续超频</b>：
 * <pre>
 *   budget = 能源仓每 tick 可输入功率（getInputVoltage() × getInputAmperage()），
 *            配置 extendedOverclock.powerCap &gt; 0 时直接用它覆盖
 *   levels = 满足 EUt × 4^levels ≤ budget 的最大档数，再夹到 extendedOverclock.maxLevels
 *   然后交给 GTCEu 自己的 standardOverclockingLogic 跑（公式、取整、1 tick 底线全部照旧）
 * </pre>
 * 也就是说：<b>只换「能超几档」这个数，不换超频公式</b>。
 *
 * <h2>三个必须守住的点</h2>
 * <ol>
 *   <li><b>用 long，不用 int</b>：{@code GTValues.VA[MAX]} 本身就贴着 int 上限
 *       （2,013,265,920），任何 {@code 1024 * VA[MAX]} 用 int 算都会溢出；本类全程 long，
 *       档数循环写成「先除后乘」（{@code probe <= budget / 4}）避免连乘越界。</li>
 *   <li><b>不转并行</b>：用 {@code standardOverclockingLogic} 而不是 {@code subTickParallelOC}。
 *       后者在时长 &lt; 1 tick 时会把余档折算成并行，和引擎的 {@code accurateParallel}
 *       相乘会打架；标准版只停在 1 tick 底线，行为可预测。</li>
 *   <li><b>写回走 RecipeHelper</b>：EUt 存在 tick 输入的 {@code Content} 里，
 *       直接改字段无效，必须 {@code RecipeHelper.setInputEUt}。</li>
 * </ol>
 *
 * <h2>幂等</h2>
 * <p>调用点是 {@code ThreadedRecipeLogic#modifyRecipe}，每次都是
 * {@code fullModifyRecipe(raw.copy(), ...)} 出来的<b>新副本</b> ⇒ 每条候选只算一次，
 * 不会像 {@code setupRecipe} 那条路那样对同一对象重复相乘。
 *
 * @see OverclockHatchPartMachine#getOverclockDurationFactor()
 * @see OverclockHatchLogic
 */
public final class ExtendedOverclockLogic {

    private ExtendedOverclockLogic() {
    }

    /** 档数硬上限，防配置写飞（4^16 = 4.3e9 已远超任何 long 级功率）。 */
    private static final int HARD_MAX_LEVELS = 16;

    /** 开关现读一次。 */
    public static boolean enabled() {
        return GTLModularConfig.isExtendedOverclockEnabled();
    }

    /**
     * 给一条配方补上「MAX 之上」的超频档 —— 没装超频仓 / 没电 / 算不出档时原样返回。
     *
     * @param machine 正在处理配方的机器
     * @param recipe  已经过 {@code fullModifyRecipe}（GTCEu 原生超频也已在里面做完）的副本
     * @return 同一个配方对象（就地改 EUt 与 duration）
     */
    public static GTRecipe apply(MetaMachine machine, GTRecipe recipe) {
        if (recipe == null || machine == null || !enabled()) {
            return recipe;
        }
        OverclockHatchPartMachine hatch = OverclockHatchLogic.find(machine);
        if (hatch == null) {
            return recipe;   // 只有装了超频仓的机器才处理
        }
        long eut = RecipeHelper.getInputEUt(recipe);
        if (eut <= 0L) {
            return recipe;   // 不耗电的配方（发电机 / 特殊配方）不碰
        }
        long budget = powerBudget(machine);
        if (budget <= eut) {
            return recipe;   // 一档都买不起
        }

        int limit = Math.min(HARD_MAX_LEVELS, Math.max(0, GTLModularConfig.getExtendedOverclockMaxLevels()));
        int levels = 0;
        for (long probe = eut; levels < limit && probe <= budget / 4L; probe *= 4L) {
            levels++;
        }
        if (levels <= 0) {
            return recipe;
        }

        // 公式照搬 GTCEu：每档 EUt ×4、耗时 ×仓倍率，时长保 1 tick 底线，绝不转并行。
        OCParams params = new OCParams();
        params.initialize(eut, recipe.duration, levels);
        OCResult result = new OCResult();
        OverclockingLogic.standardOverclockingLogic(
                params, result, budget, hatch.getOverclockDurationFactor(), OverclockingLogic.STD_VOLTAGE_FACTOR);

        int applied = result.getOcLevel();
        int duration = result.getDuration();
        long newEut = result.getEut();
        if (applied <= 0 || duration < 1 || newEut <= 0L) {
            return recipe;
        }

        RecipeHelper.setInputEUt(recipe, newEut);
        recipe.duration = duration;
        // ocTier 参与 RecipeHelper#getPreOCRecipeEuTier（原始档位反推），补上保持口径自洽。
        recipe.ocTier += applied;
        return recipe;
    }

    /**
     * 这台机器每 tick 最多能买多少 EU —— 即它<b>能持续吃下去</b>的功率。
     *
     * <p>默认取能源仓的「输入电压 × 输入电流」：{@code EnergyContainerList} 会把总量归一成
     * 一对电压/电流，乘积就是每 tick 上限（能源仓不够就付不出，配方会卡在每 tick 输入不足）。
     * 无线电网供电、能源仓只是过路的机器，可用配置 {@code extendedOverclock.powerCap} 调大。
     *
     * @return 功率上限；拿不到（不是电力多方块 / 没成形 / 没仓）返回 0 ⇒ 不处理
     */
    private static long powerBudget(MetaMachine machine) {
        long cap = GTLModularConfig.getExtendedOverclockPowerCap();
        if (cap > 0L) {
            return cap;
        }
        if (!(machine instanceof WorkableElectricMultiblockMachine workable)) {
            return 0L;
        }
        IEnergyContainer container = workable.getEnergyContainer();
        if (container == null) {
            return 0L;
        }
        long voltage = Math.max(0L, container.getInputVoltage());
        long amperage = Math.max(0L, container.getInputAmperage());
        if (voltage <= 0L || amperage <= 0L) {
            return 0L;
        }
        return saturatedMultiply(voltage, amperage);
    }

    /** 饱和乘（避免电压 × 电流越界成负数）。 */
    private static long saturatedMultiply(long a, long b) {
        if (a <= 0L || b <= 0L) {
            return 0L;
        }
        return b > 0L && a > Long.MAX_VALUE / b ? Long.MAX_VALUE : a * b;
    }
}
