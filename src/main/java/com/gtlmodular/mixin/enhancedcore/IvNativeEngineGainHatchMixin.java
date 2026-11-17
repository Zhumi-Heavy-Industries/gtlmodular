package com.gtlmodular.mixin.enhancedcore;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.IOverclockMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gtlmodular.common.machine.fuel.AccelerateHatchLogic;
import com.gtlmodular.common.machine.fuel.OverclockHatchLogic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.lang.reflect.Field;

/**
 * 让加速仓 / 超频仓在增强版<b>原生引擎（IV Native）</b>上生效。
 *
 * <h2>为什么原生引擎要单独处理</h2>
 * <p>增强版的 {@code IvNativeEngine} 开一批时不走 {@code RecipeLogic#setupRecipe}，
 * 而是在 {@code prepare(...)} 里自己算时间片并写进 {@code job.duration}：
 * <pre>
 *   timing = buildNormalRecipe(List.of(), List.of(), cost, power, 20);
 *   ...
 *   tick.duration = timing.duration;
 *   job.duration  = timing.duration;
 * </pre>
 * 所以 {@code RecipeLogic#duration} 上做的任何换算都不会被它采用
 * （它每 tick 用 job 的字段覆写那个值）。
 *
 * <h2>为什么 Redirect 的是「读」而不是「写」</h2>
 * <p>{@code prepare} 里有 <b>多处</b> 读 {@code GTRecipe.duration}：批量档的能耗计算、
 * 总量计算、以及最后的 {@code tick.duration = timing.duration}。
 * 逐个 Redirect 写点既脆弱又容易漏。这里在 {@code PUTFIELD} 之前的<b>读取</b>处统一拦截
 * （{@code GETFIELD GTRecipe.duration}），返回按仓换算后的值 —— 所有下游读取
 * （含最终赋值）自然拿到同一个数，不需要关心它被读了几次。
 *
 * <h2>为什么机器要靠反射拿</h2>
 * <p>{@code IvNativeEngine} 的机器字段是 {@code private}，且本模组对增强版是<b>可选依赖</b>
 * —— 直接 {@code @Shadow} 会在独立版（没装增强版）上于类加载期抛
 * {@code NoClassDefFoundError}。反射拿不到就当作「没有机器」，直接返回原值。
 *
 * @see IvTotalEuMixin 另一条链：{@code getTotalEuOfRecipe}（整机总能耗）
 */
@Mixin(targets = "com.gtl.enhancedcore.common.recipe.iv.IvNativeEngine", remap = false)
public abstract class IvNativeEngineGainHatchMixin {

    @Redirect(
            method = "prepare(Lcom/gtl/enhancedcore/common/recipe/iv/IvNativeEngine$Ref;JJLjava/lang/String;)V",
            at = @At(value = "FIELD",
                    target = "Lcom/gregtechceu/gtceu/api/recipe/GTRecipe;duration:I",
                    opcode = 180),
            remap = false)
    private int gtlmodular$adjustDuration(GTRecipe timing) {
        int duration = timing.duration;
        MetaMachine machine = gtlmodular$machine();
        if (machine == null || duration <= 0) {
            return duration;
        }
        int adjusted = AccelerateHatchLogic.applyToDuration(
                machine, duration, RecipeHelper.getRecipeEUtTier(timing));
        long maxEUt = machine instanceof IOverclockMachine oc ? oc.getOverclockVoltage() : 0L;
        adjusted = OverclockHatchLogic.applyToDuration(
                machine, adjusted, maxEUt, RecipeHelper.getInputEUt(timing), 0.25D);
        return adjusted;
    }

    /** 反射读本引擎实例的机器字段；拿不到就返回 {@code null}（= 不处理）。 */
    private MetaMachine gtlmodular$machine() {
        try {
            Field field = getClass().getDeclaredField("machine");
            field.setAccessible(true);
            Object value = field.get(this);
            if (value instanceof MetaMachine meta) {
                return meta;
            }
        } catch (ReflectiveOperationException | RuntimeException absent) {
            return null;
        }
        return null;
    }
}
