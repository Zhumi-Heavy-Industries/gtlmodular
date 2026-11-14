package com.gtlmodular.mixin.gtladditions;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gtladd.gtladditions.api.machine.logic.MutableRecipesLogic;
import com.gtlmodular.common.machine.fuel.AccelerateHatchLogic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 让加速仓在 gtladditions 的 Mutable 系机器上生效。
 *
 * <h2>为什么要与 {@code GTLAddMultipleRecipesLogic} 分开注</h2>
 * <p>{@code MutableRecipesLogic} <b>直接继承 {@code RecipeLogic}</b>，
 * 不是 {@code GTLAddMultipleRecipesLogic} 的子类（它的
 * {@code findAndHandleRecipe} 按 {@code useMultipleRecipes} 分支），
 * 所以给那条链打针不会命中它。{@code AddMutableRecipesLogic} 继承本类，一并覆盖。
 *
 * <h2>注入位置的取舍</h2>
 * <p>与多配方族同一条理由：{@code checkRecipe} 是查找过滤器、会被多次回调，
 * 在 {@code findAndHandleRecipe} 尾部改 {@code lastRecipe} 才只改一次，
 * 且发货拿的就是这一份。
 *
 * <p>没装加速仓时立即返回，零副作用。
 */
@Mixin(value = MutableRecipesLogic.class, remap = false, priority = 1300)
public abstract class MutableRecipesLogicAccelerateMixin {

    @Inject(method = "findAndHandleRecipe", at = @At("RETURN"), remap = false)
    private void gtlmodular$applyAccelerateHatch(CallbackInfo ci) {
        if (!AccelerateHatchLogic.enabled()) {
            return;
        }
        MutableRecipesLogic<?> self = (MutableRecipesLogic<?>) (Object) this;
        GTRecipe recipe = self.getLastRecipe();
        if (recipe == null) {
            return;
        }
        // getMachine() 有协变重载（WorkableElectricMultiblockMachine / MetaMachine），
        // 两者都是 MetaMachine，直接传入即可。
        AccelerateHatchLogic.apply(self.getMachine(), recipe);
    }
}
