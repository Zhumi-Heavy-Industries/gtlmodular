package com.gtlmodular.common.machine.fuel;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gtlmodular.common.config.GTLModularConfig;

/**
 * 加速仓的<b>生效侧</b>：把结构里那台加速仓的百分比套到配方耗时上。
 *
 * <h2>为什么需要这一层（结构放行不等于真的加速）</h2>
 * <p>加速仓原本唯一的入口是 {@link com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart#modifyRecipe}，
 * 它只从 {@code WorkableMultiblockMachine#doModifyRecipe} 被调用，而 {@code doModifyRecipe} 的上游是
 * {@code RecipeLogic#fullModifyRecipe}。按字节码核实：
 * <ul>
 *   <li>gtceu 原生 {@code RecipeLogic} —— 常量池里有 {@code fullModifyRecipe} ⇒ <b>会</b>走到加速仓；</li>
 *   <li>gtlcore {@code MultipleRecipesLogic}（GTL 核心的多数巨构、终端、本模组 NAC 主机等）——
 *       常量池里<b>没有</b> {@code fullModifyRecipe} / {@code doModifyRecipe} / {@code modifyRecipe}，
 *       它自己在 {@code setupRecipe} 里读写 {@code GTRecipe.duration}。</li>
 * </ul>
 * ⇒ 只放开结构的话，这些机器会「装得上、界面正常、速度一点没变」，比装不上更糟。
 * 因此凡是不走 {@code doModifyRecipe} 的配方逻辑，都改由本类在它拿到配方时补一次同样的换算。
 *
 * <h2>不重复计算</h2>
 * <p>走原生链路的机器已经由 {@code AccelerateHatchPartMachine#modifyRecipe} 改过一次，
 * 本类<b>只</b>被 gtlcore 那条链路调用（见 {@code MultipleRecipesLogic}'s mixin），
 * 两条链路互斥，同一台机器不会被加速两次。
 *
 * <h2>数值</h2>
 * <p>换算公式只有一份，就是 {@link AccelerateHatchPartMachine#applyTo}；本类只负责「找到仓」。
 * 没有任何加速仓时原样返回，不产生任何副作用。
 */
public final class AccelerateHatchLogic {

    private AccelerateHatchLogic() {
    }

    /**
     * 在多方块里找加速仓（没有 / 不成形 / 不是多方块 ⇒ {@code null}）。
     *
     * <p>结构谓词已经把加速仓限制为最多一台；万一旧存档里有多台，取第一台，不叠加
     * —— 与超频仓同一条口径（加速仓不是并行仓，叠加没有意义）。
     */
    public static AccelerateHatchPartMachine find(MetaMachine machine) {
        if (!(machine instanceof IMultiController controller) || !controller.isFormed()) {
            return null;
        }
        for (IMultiPart part : controller.getParts()) {
            if (part instanceof AccelerateHatchPartMachine hatch) {
                return hatch;
            }
        }
        return null;
    }

    /**
     * 给一条配方套上加速仓的耗时百分比。
     *
     * @param machine 正在处理配方的机器
     * @param recipe  它的配方
     * @return 换算后的配方；没有加速仓、开关关闭或参数不全时原样返回
     */
    public static GTRecipe apply(MetaMachine machine, GTRecipe recipe) {
        if (recipe == null || machine == null || !AccelerateHatchPredicate.enabled()) {
            return recipe;
        }
        AccelerateHatchPartMachine hatch = find(machine);
        if (hatch == null) {
            return recipe;
        }
        return AccelerateHatchPartMachine.applyTo(recipe, hatch.getPercentage(), hatch.getTier());
    }

    /** 界面/Jade 用：当前生效的加速百分比；没有加速仓返回 {@code -1}。 */
    public static int currentPercentage(MetaMachine machine) {
        AccelerateHatchPartMachine hatch = find(machine);
        return hatch == null ? -1 : hatch.getPercentage();
    }

    /** 开关现读一次，供 mixin 快速短路（避免每条配方都去遍历部件表）。 */
    public static boolean enabled() {
        return GTLModularConfig.isAccelerateHatchAnyMultiblock();
    }
}
