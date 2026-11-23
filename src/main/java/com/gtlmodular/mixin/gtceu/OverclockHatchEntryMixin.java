package com.gtlmodular.mixin.gtceu;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.logic.OCParams;
import com.gregtechceu.gtceu.api.recipe.logic.OCResult;
import com.gregtechceu.gtceu.common.data.GTRecipeModifiers;
import com.gtlmodular.common.machine.fuel.OverclockHatchLogic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 超频仓生效链的<b>入口半个</b>：在配方修正入口记下当前机器。
 *
 * <p>另一半是 {@link OverclockHatchLogicMixin}（替换 {@code RecipeHelper#applyOverclock}
 * 收到的 {@code logic}）。拆成两个类是因为它们的目标类不同，而 Mixin 只认
 * {@code gtlmodular.mixins.json} 里点名的那一个类本身 —— 以前这两段写成同一个类里的
 * 嵌套类、配置里登记的却是外层容器，于是两个都没织上（日志报
 * {@code missing an @Mixin annotation}）。
 *
 * <h2>为什么需要这一层</h2>
 * <p>超频的公共方法 {@code RecipeHelper#applyOverclock(logic, recipe, maxVoltage, params, result)}
 * 拿不到机器，但 {@code OverclockingLogic} 在机器注册时就被写死。
 * {@code GTRecipeModifiers.ElectricOverclockModifier#apply(machine, ...)} 是能同时拿到
 * 机器与超频调用的最近一层，所以在这里把机器记进
 * {@link OverclockHatchLogic}（ThreadLocal），到真正执行超频时再据此换逻辑。
 */
@Mixin(value = GTRecipeModifiers.ElectricOverclockModifier.class, remap = false)
public abstract class OverclockHatchEntryMixin {

    @Inject(method = "apply", at = @At("HEAD"), remap = false)
    private void gtlmodular$enter(MetaMachine machine, GTRecipe recipe, OCParams params,
                                  OCResult result, CallbackInfoReturnable<GTRecipe> cir) {
        OverclockHatchLogic.enter(machine);
    }

    /** 正常返回（含提前 {@code return null}）时清理，避免串到别的机器。 */
    @Inject(method = "apply", at = @At("RETURN"), remap = false)
    private void gtlmodular$exit(MetaMachine machine, GTRecipe recipe, OCParams params,
                                 OCResult result, CallbackInfoReturnable<GTRecipe> cir) {
        OverclockHatchLogic.exit(machine);
    }
}
