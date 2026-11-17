package com.gtlmodular.common.machine.fuel;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gtlmodular.common.config.GTLModularConfig;

/**
 * 超频仓的<b>生效侧</b>。
 *
 * <h2>超频仓在原生链上为什么不生效</h2>
 * <p>超频仓只是一张「耗时倍率」的表：{@link OverclockHatchPartMachine#getOverclockDurationFactor()}。
 * 真正执行超频的是 {@code GTRecipeModifiers.ELECTRIC_OVERCLOCK.apply(logic)}，
 * 而 {@code logic} 在机器注册时就是常量。运行期没人去问仓 ⇒ 装了不生效。
 *
 * <h2>本类的换算口径</h2>
 * <p>GTCEu 的完美超频是「每档耗时 ×1/4、EUt ×4」。超频仓表达的是「同样的 4 倍功率，耗时按仓的倍率」。
 * 因此把配方已经算好的耗时按<b>仓倍率与默认倍率之比</b>再缩一次：
 * <pre>
 *   ratio  = 仓倍率 / 默认倍率（PERFECT_OVERCLOCK_SUBTICK = 0.25）
 *   duration = max(1, duration × ratio)
 * </pre>
 * 仓倍率 0.25（UEV 无损）⇒ ratio = 1，与原口径一致，不会凭空变快；
 * 仓倍率 0.125（MAX）⇒ ratio = 0.5，耗时再减半；仓倍率 0.5（UV）⇒ ratio = 2，
 * 也就是「比默认的完美超频更慢」—— 这正是 UV 档的真实含义（它等价于有损超频）。
 * 这样换算不会重复乘超频档数，也不用去猜每台机器超频了几次。
 *
 * <h2>生效范围 = 装了超频仓的机器</h2>
 * <p>不再按模组/机器名单限定：结构里能放下超频仓、且走原生超频链路的机器都会被处理。
 * 没有超频仓时 {@link #apply} 直接返回原配方，全包行为不变。
 */
public final class OverclockHatchLogic {

    private OverclockHatchLogic() {
    }

    /** 总开关（与线程仓 / 加速仓的「任意多方块」口径分开）。 */
    public static boolean enabled() {
        return GTLModularConfig.isExtendHatchesEnabled();
    }

    /**
     * 当前正在算配方的机器（只在超频调用栈内有效）。
     *
     * <p>超频的公共入口 {@code RecipeHelper#applyOverclock(logic, recipe, maxVoltage, params, result)}
     * 拿不到机器，但 {@code OverclockingLogic} 在机器注册时就被写死。
     * 因此在 {@code RecipeModifier#apply(machine, ...)} 这一层记下机器，
     * 到真正执行超频时再据此替换倍率 —— 这正是 GTO 的做法
     * （它的超频仓只有一个 {@code getCurrentMultiplier()}，由超频 trait 现读）。
     */
    private static final ThreadLocal<MetaMachine> CURRENT_MACHINE = new ThreadLocal<>();

    /** 进入一次配方修正：记下机器。 */
    public static void enter(MetaMachine machine) {
        CURRENT_MACHINE.set(machine);
    }

    /** 离开一次配方修正。 */
    public static void exit() {
        CURRENT_MACHINE.remove();
    }

    /**
     * 把即将执行的超频逻辑替换成「按本仓倍率」的版本。
     *
     * <p>GTO 口径：仓替换的是<b>每次超频的耗时倍率</b>，而不是事后整体乘一次。
     * 电压倍率固定 4（{@code STD_VOLTAGE_FACTOR}）。
     *
     * @param original 机器注册时写死的逻辑
     * @return 装了超频仓时返回仓倍率的逻辑；否则原样返回
     */
    public static com.gregtechceu.gtceu.api.recipe.OverclockingLogic override(
            com.gregtechceu.gtceu.api.recipe.OverclockingLogic original) {
        MetaMachine machine = CURRENT_MACHINE.get();
        if (machine == null || !enabled()) {
            return original;
        }
        OverclockHatchPartMachine hatch = find(machine);
        if (hatch == null) {
            return original;
        }
        double factor = hatch.getOverclockDurationFactor();
        if (!(factor > 0.0D) || Double.isNaN(factor) || Double.isInfinite(factor)) {
            return original;
        }
        return new com.gregtechceu.gtceu.api.recipe.OverclockingLogic(
                factor,
                com.gregtechceu.gtceu.api.recipe.OverclockingLogic.STD_VOLTAGE_FACTOR,
                true);
    }

    /** 在多方块里找超频仓（没有 / 不成形 / 不是多方块 ⇒ {@code null}）。 */
    public static OverclockHatchPartMachine find(MetaMachine machine) {
        if (!(machine instanceof IMultiController controller) || !controller.isFormed()) {
            return null;
        }
        for (IMultiPart part : controller.getParts()) {
            if (part instanceof OverclockHatchPartMachine hatch) {
                return hatch;
            }
        }
        return null;
    }

    /**
     * 这台机器要不要处理 —— 只要装了超频仓就处理。
     *
     * <p>不再按模组名单过滤：超频仓装在谁身上，就该在谁身上生效。
     */
    public static boolean shouldApply(MetaMachine machine) {
        return find(machine) != null;
    }

    /**
     * 按超频档数把默认倍率换成仓倍率 —— 与 GTO 同口径。
     *
     * <h2>为什么需要档数</h2>
     * <p>GTO 的超频仓替换的是<b>每次超频</b>的耗时倍率。机器算完配方后
     * 时长 = 原始 × 默认倍率^档数，换成仓倍率后应为 原始 × 仓倍率^档数，
     * 即只需在当前时长上再乘 {@code (仓倍率 / 默认倍率)^档数}。
     *
     * <h2>档数怎么来</h2>
     * <p>不依赖 {@code OCResult}（它在 {@code RecipeModifierList} 结束时就被 reset），
     * 也不依赖 {@code recipe.ocTier}（gtlcore 自己组装配方时不写）。
     * 直接按电压反推：
     * <pre>
     *   档数 n = log4(机器可用的超频电压 / 配方 EUt)
     * </pre>
     * 因为每超一档功率 ×4、耗时 ×默认倍率，这两个量在同一条链上是自洽的。
     * 数值已验证：{@code 30 EUt} 配方跑在 {@code 7680} 的机器上 ⇒ n=4，
     * 与 {@code 0.25^4} 的结果完全一致。
     *
     * @param machine 正在处理配方的机器
     * @param recipe  已经按默认倍率超频过的配方（就地修改）
     * @param maxEUt  机器可用的超频电压上限
     * @param defaultFactor 机器实际使用的默认每档倍率
     * @return 同一个配方对象
     */
    public static GTRecipe applyForLevels(MetaMachine machine, GTRecipe recipe,
                                          long maxEUt, double defaultFactor) {
        if (recipe == null || machine == null || !enabled()) {
            return recipe;
        }
        if (!(defaultFactor > 0.0D) || Double.isNaN(defaultFactor) || Double.isInfinite(defaultFactor)) {
            return recipe;
        }
        OverclockHatchPartMachine hatch = find(machine);
        if (hatch == null) {
            return recipe;
        }
        double factor = hatch.getOverclockDurationFactor();
        if (!(factor > 0.0D) || Double.isNaN(factor) || Double.isInfinite(factor)) {
            return recipe;
        }
        int levels = overclockLevels(maxEUt, com.gregtechceu.gtceu.api.recipe.RecipeHelper.getInputEUt(recipe));
        if (levels <= 0) {
            return recipe;
        }
        double ratio = Math.pow(factor / defaultFactor, levels);
        if (!(ratio > 0.0D) || Double.isNaN(ratio) || Double.isInfinite(ratio)) {
            return recipe;
        }
        long adjusted = Math.max(1L, Math.min(Integer.MAX_VALUE, (long) Math.floor(recipe.duration * ratio)));
        recipe.duration = (int) adjusted;
        return recipe;
    }

    /**
     * 按「机器可用电压 / 配方 EUt」反推超频档数（每档 ×4）。
     *
     * @return 档数；无法判断时返回 0（等于不处理）
     */
    public static int overclockLevels(long maxEUt, long recipeEUt) {
        if (maxEUt <= 0L || recipeEUt <= 0L || maxEUt <= recipeEUt) {
            return 0;
        }
        double ratio = (double) maxEUt / (double) recipeEUt;
        int levels = (int) Math.round(Math.log(ratio) / Math.log(4.0D));
        return Math.max(0, levels);
    }

    /**
     * 已被标准超频链路（{@code RecipeHelper#applyOverclock}）处理过的配方。
     *
     * <p>那条链路上倍率已被替换成仓的倍率，不能再按档数补偿一次。
     * 这类机器（{@code isa_mill} 等走 {@code recipeModifier} 的）在这里打点，
     * 而那些自己组装配方、不调 {@code applyOverclock} 的机器（gtlcore 的
     * {@code MultipleRecipesLogic}）不会打点，由 {@code setupRecipe} 侧补算。
     *
     * <p>按对象身份记：{@code GTRecipe#equals} 是按 id 实现的，普通集合会把
     * id 相同但时长不同的副本当成同一个。表有上限，攒够一批整体清空。
     */
    private static final java.util.Map<GTRecipe, Boolean> HANDLED = new java.util.IdentityHashMap<>();
    private static final int HANDLED_MAX = 2048;

    /** 标记这条配方已经由标准超频链路处理过。 */
    public static void markHandled(GTRecipe recipe) {
        if (recipe == null) {
            return;
        }
        synchronized (HANDLED) {
            HANDLED.put(recipe, Boolean.TRUE);
            if (HANDLED.size() > HANDLED_MAX) {
                HANDLED.clear();
                HANDLED.put(recipe, Boolean.TRUE);
            }
        }
    }

    /** 这条配方是否已由标准超频链路处理过。 */
    public static boolean wasHandled(GTRecipe recipe) {
        if (recipe == null) {
            return false;
        }
        synchronized (HANDLED) {
            return HANDLED.containsKey(recipe);
        }
    }

    /**
     * 纯数值版：按超频档数把一段时长换成仓倍率下的值。
     *
     * <p>供不持有 {@code GTRecipe} 的链路使用（如增强版的 IV 原生引擎）。
     *
     * @param machine       机器（用来找仓）
     * @param duration      已按默认倍率超频过的时长
     * @param maxEUt        机器可用超频电压
     * @param recipeEUt     配方原始 EUt
     * @param defaultFactor 机器实际使用的默认每档倍率
     * @return 换算后的时长；没有仓或无法判断档数时原样返回
     */
    public static int applyToDuration(MetaMachine machine, int duration,
                                      long maxEUt, long recipeEUt, double defaultFactor) {
        if (duration <= 0 || machine == null || !enabled()) {
            return duration;
        }
        if (!(defaultFactor > 0.0D)) {
            return duration;
        }
        OverclockHatchPartMachine hatch = find(machine);
        if (hatch == null) {
            return duration;
        }
        double factor = hatch.getOverclockDurationFactor();
        if (!(factor > 0.0D) || Double.isNaN(factor) || Double.isInfinite(factor)) {
            return duration;
        }
        int levels = overclockLevels(maxEUt, recipeEUt);
        if (levels <= 0) {
            return duration;
        }
        double ratio = Math.pow(factor / defaultFactor, levels);
        if (!(ratio > 0.0D) || Double.isNaN(ratio) || Double.isInfinite(ratio)) {
            return duration;
        }
        return (int) Math.max(1L, Math.min(Integer.MAX_VALUE, (long) Math.floor(duration * ratio)));
    }

    /** 界面/Jade 用：当前生效的耗时倍率；没有超频仓返回 {@code -1}。 */
    public static double currentFactor(MetaMachine machine) {
        OverclockHatchPartMachine hatch = find(machine);
        return hatch == null ? -1.0D : hatch.getOverclockDurationFactor();
    }
}
