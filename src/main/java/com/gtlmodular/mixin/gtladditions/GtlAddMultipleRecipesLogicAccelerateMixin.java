package com.gtlmodular.mixin.gtladditions;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gtladd.gtladditions.api.machine.logic.GTLAddMultipleRecipesLogic;
import com.gtlmodular.common.machine.fuel.AccelerateHatchLogic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 让加速仓在 gtladditions 的多配方机器上生效。
 *
 * <h2>为什么必须单独注入</h2>
 * <p>gtladditions 的 {@code GTLAddMultipleRecipesLogic} 自己走
 * {@code findAndHandleRecipe → lookupRecipeIterator → onRecipeFinish} 这条链，
 * 不走 {@code fullModifyRecipe} / {@code doModifyRecipe}（加速仓唯一的原生存口）。
 * 它下面还有 {@code GTLAddMultipleTypeRecipesLogic} / 两个 Wireless 变体，
 * 都继承这个类、不覆写 {@code findAndHandleRecipe} ⇒ 注这一处即可覆盖全部。
 *
 * <h2>为什么注在 findAndHandleRecipe 尾部</h2>
 * <p>{@code checkRecipe} 是配方查找的过滤器，同一条配方会被回调多次，
 * 在那里改时长会压缩 N 次；{@code findAndHandleRecipe} 每次尝试只跑一次，
 * 结束时 {@code lastRecipe} 已经定下来、发货拿的正是它 ⇒ 只缩一次。
 *
 * <h2>与「增强版核心」共存</h2>
 * <p>增强版对 {@code findAndHandleRecipe} 也有注入，但它只在
 * {@code iv$noSharedExecution} 条件下 {@code cancel}（共享执行闸门），
 * 与这里在 {@code RETURN} 上追加的处理互斥不冲突：被它取消时本注不会执行，
 * 机器本轮本来就没跑配方，也就没有需要加速的时长。
 *
 * <p>没装加速仓时立即返回，零副作用。
 */
@Mixin(value = GTLAddMultipleRecipesLogic.class, remap = false, priority = 1300)
public abstract class GtlAddMultipleRecipesLogicAccelerateMixin {

    @Inject(method = "findAndHandleRecipe", at = @At("RETURN"), remap = false)
    private void gtlmodular$applyAccelerateHatch(CallbackInfo ci) {
        if (!AccelerateHatchLogic.enabled()) {
            return;
        }
        GTLAddMultipleRecipesLogic self = (GTLAddMultipleRecipesLogic) (Object) this;
        GTRecipe recipe = self.getLastRecipe();
        if (recipe == null) {
            return;
        }
        AccelerateHatchLogic.apply(self.getMachine(), recipe);
    }
}
