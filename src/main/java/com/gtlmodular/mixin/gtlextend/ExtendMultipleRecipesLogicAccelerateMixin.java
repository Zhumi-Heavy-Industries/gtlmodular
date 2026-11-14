package com.gtlmodular.mixin.gtlextend;

import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gtlmodular.common.machine.fuel.AccelerateHatchLogic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 让加速仓在增强版核心自带的配方逻辑上生效（黑物质分解机用这一份）。
 *
 * <h2>为什么必须单独注入</h2>
 * <p>增强版核心的 {@code cn.qiuye.gtlextend...MultipleRecipesLogic} 自己重写了
 * {@code findAndHandleRecipe}：拿 {@code getRecipe()} 的结果直接 {@code setupRecipe}，
 * 全程<b>不调</b> {@code fullModifyRecipe}（该类里这个方法出现 0 次）。
 * 而加速仓的原生存口就是 {@code fullModifyRecipe → doModifyRecipe → IMultiPart#modifyRecipe}
 * ⇒ 不加这一针，这台机器会「装得上、界面正常、速度不变」。
 *
 * <h2>为什么注在 findAndHandleRecipe 的尾部</h2>
 * <p>该类的 {@code checkRecipe} 是配方查找的过滤器（{@code getRecipeType().getLookup()}
 * 里反复回调），在那里改时长会把同一条配方压缩多次；{@code setupRecipe} 是继承来的、
 * 本类没覆盖，注入会挂空。{@code findAndHandleRecipe} 每次尝试只跑一次，
 * 结束时 {@code lastRecipe} 已定下来、发货拿的正是这一份 ⇒ 在尾部缩时长只缩一次。
 *
 * <h2>为什么用 targets 字符串而不是 import</h2>
 * <p>增强版核心不在本工程编译依赖里（可选依赖），写 {@code import} 会直接编译失败。
 * 用类名字符串后，本类常量池里只有字符串，没装增强版时这一针自然不生效。
 */
@Mixin(targets = "cn.qiuye.gtlextend.api.machine.logic.MultipleRecipesLogic", remap = false)
public abstract class ExtendMultipleRecipesLogicAccelerateMixin {

    @Inject(method = "findAndHandleRecipe", at = @At("RETURN"), remap = false)
    private void gtlmodular$applyAccelerateHatch(CallbackInfo ci) {
        if (!AccelerateHatchLogic.enabled()) {
            return;
        }
        if (!(this instanceof IRecipeLogicMachine logicMachine)) {
            return;
        }
        // getLastRecipe() 来自共同父类 RecipeLogic；不写 @Shadow，避免 targets 模式下
        // 对继承来的 getter 做影子声明（不同版本方法签名变动时更容易失效）。
        if (this instanceof com.gregtechceu.gtceu.api.machine.trait.RecipeLogic logic) {
            GTRecipe recipe = logic.getLastRecipe();
            if (recipe == null) {
                return;
            }
            AccelerateHatchLogic.apply(logicMachine.self(), recipe);
        }
    }
}
