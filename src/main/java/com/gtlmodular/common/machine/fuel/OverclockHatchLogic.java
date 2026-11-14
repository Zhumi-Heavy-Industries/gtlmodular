package com.gtlmodular.common.machine.fuel;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gtlmodular.common.config.GTLModularConfig;

/**
 * 超频仓的<b>生效侧</b>（只针对增强版核心的机器）。
 *
 * <h2>超频仓在原生链上为什么不生效</h2>
 * <p>超频仓只是一张「耗时倍率」的表：{@link OverclockHatchPartMachine#getOverclockDurationFactor()}。
 * 真正执行超频的是 {@code GTRecipeModifiers.ELECTRIC_OVERCLOCK.apply(logic)}，
 * 而 {@code logic} 在机器注册时就是常量。运行期没人去问仓 ⇒ 装了不生效。
 *
 * <h2>本类的换算口径（对齐 GTO）</h2>
 * <p>GTCEu 的完美超频是「每档耗时 ×1/4、EUt ×4」。超频仓要表达的是「同样的 4 倍功率，耗时按仓的倍率」。
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
 * <h2>范围</h2>
 * <p>只对增强版核心的机器生效（见 {@link #shouldApply}），与线程仓同一口径。
 */
public final class OverclockHatchLogic {

    private OverclockHatchLogic() {
    }

    /** GTCEu 完美超频（亚 tick）的每档耗时倍率，作为「不装仓」的基准。 */
    private static final double DEFAULT_DURATION_FACTOR = 0.25D;

    /** 开关：线程仓 / 超频仓只装到增强版机器上，两者共用一个开关。 */
    public static boolean enabled() {
        return GTLModularConfig.isExtendHatchesEnabled();
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
     * 这台机器是否属于超频仓的作用范围：<b>增强版核心的机器</b>。
     *
     * <p>判据取方块注册 id 的命名空间：增强版用自己的 {@code GTRegistrate}（{@code gtl_extend}）
     * 注册机器，所以 id 直接就是证据，不依赖类加载或 instanceof（外层可能没装增强版）。
     */
    public static boolean shouldApply(MetaMachine machine) {
        if (machine == null) {
            return false;
        }
        var definition = machine.getDefinition();
        if (definition == null || definition.getId() == null) {
            return false;
        }
        return "gtl_extend".equals(definition.getId().getNamespace());
    }

    /**
     * 这台机器是否已经用过超频仓口径（避免重复换算）。
     *
     * <p>判定为「机器自己实现了 {@link ISpeedEnergyOverclockable}」：
     * 那类机器的 {@code recipeModifier} 已经通过 {@link OverclockProfiles#apply} 读过仓，
     * 我们再算一次就会叠加。增强版的机器都没实现该接口，所以不受影响。
     */
    public static boolean alreadyApplied(MetaMachine machine) {
        return machine instanceof ISpeedEnergyOverclockable;
    }

    /**
     * 按仓的倍率调整已算好的配方耗时。
     *
     * @return 调整后的配方；没有超频仓或倍率无效时原样返回
     */
    public static GTRecipe apply(MetaMachine machine, GTRecipe recipe) {
        if (recipe == null) {
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
        double ratio = factor / DEFAULT_DURATION_FACTOR;
        long adjusted = (long) Math.floor(recipe.duration * ratio);
        recipe.duration = (int) Math.max(1L, Math.min(Integer.MAX_VALUE, adjusted));
        return recipe;
    }

    /** 界面/Jade 用：当前生效的耗时倍率；没有超频仓返回 {@code -1}。 */
    public static double currentFactor(MetaMachine machine) {
        OverclockHatchPartMachine hatch = find(machine);
        return hatch == null ? -1.0D : hatch.getOverclockDurationFactor();
    }
}
