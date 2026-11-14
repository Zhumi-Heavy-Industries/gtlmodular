package com.gtlmodular.mixin.gtceu;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableMultiblockMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.logic.OCParams;
import com.gregtechceu.gtceu.api.recipe.logic.OCResult;
import com.gtlmodular.common.machine.fuel.OverclockHatchLogic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 让超频仓在<b>走原生超频链路</b>的增强版机器上真正生效。
 *
 * <h2>为什么超频仓原先「能装但没用」</h2>
 * <p>超频仓只提供 {@link com.gtlmodular.common.machine.fuel.IOverclockHatchPart#getOverclockDurationFactor()}，
 * 真正的超频执行在 {@code GTRecipeModifiers.ELECTRIC_OVERCLOCK.apply(logic)} 里，
 * 而 {@code logic} 是机器<b>注册时</b>写死的常量（增强版全部用 {@code PERFECT_OVERCLOCK_SUBTICK}）。
 * 运行期没有任何地方去问仓 ⇒ 装了也不变。
 *
 * <p>本工程原有的解法是让机器在 {@code recipeModifier} 里主动调 {@code OverclockProfiles.apply(...)}
 * （冷却塔、力场压模工厂两台），但增强版的结构与注册都在它自己的 jar 里改不了。
 * 所以改为在 {@code WorkableMultiblockMachine#doModifyRecipe} 返回前兜底：此时超频已算完、
 * 配方耗时已落定，按仓的倍率再换算一次，等价于「用仓的倍率重新超频」。
 *
 * <h2>注入点的选择依据</h2>
 * <p>{@code doModifyRecipe(GTRecipe, OCParams, OCResult)} 是原生链路里唯一把超频结果写回配方的出口，
 * 也是紧接着调用 {@code IMultiPart#modifyRecipe} 的地方（加速仓的原生存口）。
 * {@code getRealRecipe} 在该类里<b>只是参数名</b>，不是方法，不能作为注入点。
 *
 * <h2>生效范围 = 装了超频仓的机器</h2>
 * <p>{@link OverclockHatchLogic#shouldApply} 只看「结构里有没有超频仓」，
 * 不再按模组或名单限定：超频仓装在谁身上，就该在谁身上生效。
 * 没装超频仓的机器立即返回，一个字段都不碰。
 */
@Mixin(value = WorkableMultiblockMachine.class, remap = false)
public abstract class WorkableMultiblockOverclockMixin {

    @Inject(method = "doModifyRecipe", at = @At("RETURN"), cancellable = true, remap = false)
    private void gtlmodular$applyOverclockHatch(GTRecipe recipe, OCParams params, OCResult result,
                                                CallbackInfoReturnable<GTRecipe> cir) {
        GTRecipe modified = cir.getReturnValue();
        if (modified == null || !OverclockHatchLogic.enabled()) {
            return;
        }
        MetaMachine self = (MetaMachine) (Object) this;
        if (!OverclockHatchLogic.shouldApply(self) || OverclockHatchLogic.alreadyApplied(self)) {
            return;
        }
        GTRecipe adjusted = OverclockHatchLogic.apply(self, modified);
        if (adjusted != modified) {
            cir.setReturnValue(adjusted);
        }
    }
}
