package com.gtlmodular.mixin.gtlcore;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gtlmodular.common.machine.fuel.AccelerateHatchLogic;
import org.gtlcore.gtlcore.common.machine.trait.MultipleRecipesLogic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 让加速仓在 gtlcore 的配方逻辑上真正生效。
 *
 * <h2>为什么需要单独注入</h2>
 * <p>gtlcore 的 {@code MultipleRecipesLogic} 自己走 {@code findAndHandleRecipe → getRecipe
 * → setupRecipe} 这条链，按字节码核实它<b>从不调用</b>
 * {@code fullModifyRecipe} / {@code doModifyRecipe}，而加速仓唯一的原生存口就是
 * {@code doModifyRecipe → IMultiPart#modifyRecipe} ⇒ 在这类机器上装了加速仓也不会加速。
 *
 * <h2>为什么注在 findAndHandleRecipe 的尾部</h2>
 * <ul>
 *   <li>{@code setupRecipe} 是 gtlcore <b>没有覆盖</b>的方法（继承自 {@code RecipeLogic}），
 *       对它注入会落到父类方法上、在本类上挂空；</li>
 *   <li>{@code checkRecipe} 被注册成 {@code lookupRecipeIterator} 的过滤器，
 *       每条候选配方都会被问 <b>N 次</b> —— 在那里改 {@code duration} 会把同一条配方
 *       压缩 N 次，是错的；</li>
 *   <li>{@code findAndHandleRecipe} 每次尝试只跑一次，且它结束时
 *       {@code lastRecipe} 已经定下来、发货（{@code handleRecipeOutput}）就是拿这一份。
 *       在尾部对 {@code lastRecipe} 缩时长 = 只缩一次，且一定作用到实际发的那份。</li>
 * </ul>
 *
 * <h2>与原生链路互斥</h2>
 * <p>只有走本类这条链的机器会命中，同一台机器不会既走 {@code doModifyRecipe} 又走这里
 * ⇒ 配方不会被加速两次。没有加速仓时立即返回，零副作用。
 */
@Mixin(value = MultipleRecipesLogic.class, remap = false)
public abstract class MultipleRecipesLogicAccelerateMixin {

    @Inject(method = "findAndHandleRecipe", at = @At("RETURN"), remap = false)
    private void gtlmodular$applyAccelerateHatch(CallbackInfo ci) {
        if (!AccelerateHatchLogic.enabled()) {
            return;
        }
        MultipleRecipesLogic self = (MultipleRecipesLogic) (Object) this;
        GTRecipe recipe = self.getLastRecipe();
        if (recipe == null) {
            return;
        }
        AccelerateHatchLogic.apply(self.getMachine(), recipe);
    }
}
