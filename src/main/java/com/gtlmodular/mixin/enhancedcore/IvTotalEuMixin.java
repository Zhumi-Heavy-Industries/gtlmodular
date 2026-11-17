package com.gtlmodular.mixin.enhancedcore;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gtlmodular.common.machine.fuel.AccelerateHatchLogic;
import com.gtlmodular.common.machine.fuel.OverclockHatchLogic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 让加速仓 / 超频仓在增强版机器上生效 —— 通过缩放「这批的总能耗」。
 *
 * <h2>为什么必须改这里</h2>
 * <p>增强版 {@code IvRecipeLogic#prepare} 的时长是<b>纯能量换算</b>算出来的：
 * <pre>
 *   double totalEu = getTotalEuOfRecipe(job.recipe) * parallel * getEuMultiplier();
 *   GTRecipe timing = buildNormalRecipe(List.of(), List.of(), totalEu, inputPower, ...);
 *   // timing.duration = totalEu / inputPower
 * </pre>
 * 它<b>不读</b> {@code recipe.duration} 作为时长，也不调用 {@code IMultiPart#modifyRecipe}、
 * 不经过 {@code RecipeLogic.duration}。所以：
 * <ul>
 *   <li>改 {@code recipe.duration} ⇒ 只影响 {@code totalEu}（等于同时省电）；</li>
 *   <li>改 {@code RecipeLogic.duration} ⇒ 每 tick 被 {@code tickTasks} 用 job 覆写，白改；</li>
 *   <li>超频仓没有作用点 —— 这里根本没有超频环节。</li>
 * </ul>
 *
 * <h2>为什么缩放 totalEu 等价于加速</h2>
 * <p>{@code duration = totalEu / inputPower}，把 {@code totalEu} 按比例缩小，
 * 时长就按同样比例缩短，<b>耗电也同比减少</b> —— 这正是 GTO 加速仓的语义
 * （「无条件加速，不额外计费」）。超频仓按同样方式把「功率×4」折算成能耗降低。
 *
 * <h2>为什么注入 getTotalEuOfRecipe，且目标是父类</h2>
 * <p>它是 {@code MultipleRecipesLogic} 的 protected 方法（{@code IvRecipeLogic}
 * <b>没有覆写</b>它，只是调用），参数是 {@code GTRecipe} —— 本模组能引用，
 * 不像 {@code IvNativeEngine$Ref} 那样是内部 record。
 * 每开一批只调一次，不会被逐 tick 重复放大。
 *
 * <p>注入目标必须是声明它的父类：写成 {@code IvRecipeLogic} 时 mixin 找不到该方法，
 * 静默不生效（实测日志里一条诊断都没有）。
 */
@Mixin(targets = "org.gtlcore.gtlcore.common.machine.trait.MultipleRecipesLogic", remap = false)
public abstract class IvTotalEuMixin {

    @Inject(method = "getTotalEuOfRecipe", at = @At("RETURN"), cancellable = true, remap = false)
    private void gtlmodular$scaleByHatches(GTRecipe recipe, CallbackInfoReturnable<Double> cir) {
        Double totalEu = cir.getReturnValue();
        if (totalEu == null || !(totalEu > 0.0D) || recipe == null) {
            return;
        }
        MetaMachine machine = ((RecipeLogic) (Object) this).getMachine();
        if (machine == null) {
            return;
        }
        int original = recipe.duration;
        if (original <= 0) {
            return;
        }

        // 加速仓：按玩家设定的百分比缩短。
        int adjusted = AccelerateHatchLogic.applyToDuration(
                machine, original, com.gregtechceu.gtceu.api.recipe.RecipeHelper.getRecipeEUtTier(recipe));
        // 超频仓：按超频档数把默认倍率换成仓倍率。
        long maxEUt = machine instanceof com.gregtechceu.gtceu.api.machine.feature.IOverclockMachine oc
                ? oc.getOverclockVoltage() : 0L;
        adjusted = OverclockHatchLogic.applyToDuration(
                machine, adjusted, maxEUt,
                com.gregtechceu.gtceu.api.recipe.RecipeHelper.getInputEUt(recipe), 0.25D);

        if (adjusted <= 0 || adjusted == original) {
            return;
        }
        // duration 与 totalEu 是线性关系 ⇒ 按同一比例缩放即可。
        cir.setReturnValue(totalEu * ((double) adjusted / (double) original));
    }
}
