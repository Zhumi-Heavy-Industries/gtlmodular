package com.gtlmodular.mixin.gtceu;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.OverclockingLogic;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.api.recipe.logic.OCParams;
import com.gregtechceu.gtceu.api.recipe.logic.OCResult;
import com.gtlmodular.common.machine.fuel.OverclockHatchLogic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 让超频仓替换「每次超频的耗时倍率」—— 与 GTO 同口径。
 *
 * <h2>为什么不能事后乘一次</h2>
 * <p>GTO 的 {@code OverclockPartMachine} 只有一个 {@code getCurrentMultiplier()}，
 * 由超频 trait 现读，用于<b>每次超频</b>的耗时倍率。若改成配方算完后再整体乘一次，
 * 超频 2 档就只乘 1 次倍率，结果与 GTO 不一致（仓越高级差得越多）。
 *
 * <h2>两个注入点（分属两个类）</h2>
 * <ul>
 *   <li>{@link OverclockHatchEntryMixin}：超频入口能拿到机器，在那里记下它
 *       （超频的公共方法 {@code applyOverclock} 本身拿不到机器）；</li>
 *   <li>本类：真正执行超频时，把 {@code logic} 换成仓倍率的版本。</li>
 * </ul>
 *
 * <p><b>为什么必须拆成两个顶层类</b>：Mixin 只处理 {@code gtlmodular.mixins.json}
 * 里点名的那个类本身。0.3.434 起这两段写成同一个类的嵌套类、配置里登记的却是
 * 外层容器（容器本身没有 {@code @Mixin}），于是两个注入点都没织上，日志报
 * {@code missing an @Mixin annotation}。
 *
 * <h2>为什么用 ThreadLocal</h2>
 * <p>两个注入点之间隔着 {@code RecipeHelper} 的静态调用，没有可传递的上下文参数。
 * 配方修正在服务端线程串行执行，ThreadLocal 足够且不会串味；用完立即清理。
 */
@Mixin(value = RecipeHelper.class, remap = false)
public abstract class OverclockHatchLogicMixin {

    /**
     * 真正执行超频时把「机器注册时写死的逻辑」换成按仓倍率的版本。
     *
     * <p>{@code performOverclocking} 会把档数写进 {@code OCResult}，但
     * {@code RecipeModifierList.apply} 结束时立刻 {@code reset()}。所以在返回处
     * 标记这条配方已由标准超频链路处理，供那些不走本方法的机器补算时跳过。
     */
    @ModifyVariable(method = "applyOverclock", at = @At("HEAD"), argsOnly = true, remap = false)
    private static OverclockingLogic gtlmodular$overrideLogic(OverclockingLogic logic) {
        return OverclockHatchLogic.override(logic);
    }

    @Inject(method = "applyOverclock", at = @At("RETURN"), remap = false)
    private static void gtlmodular$rememberLevels(OverclockingLogic logic, GTRecipe recipe,
                                                  long maxOverclockVoltage, OCParams params,
                                                  OCResult result,
                                                  CallbackInfoReturnable<GTRecipe> cir) {
        // 走到这里说明这台机器用标准超频链路，倍率已在上面被替换过，
        // 不需要再按档数补偿。
        OverclockHatchLogic.markHandled(recipe);
    }
}
