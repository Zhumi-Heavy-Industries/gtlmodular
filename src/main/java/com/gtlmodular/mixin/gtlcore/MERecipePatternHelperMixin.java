package com.gtlmodular.mixin.gtlcore;

import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import org.gtlcore.gtlcore.integration.ae2.handler.MERecipePatternHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 让 gtlcore 的「ME配方类型统合样板总成」对<b>本模组所有机器</b>展开全部配方类型。
 *
 * <p>gtlcore 的 {@code getActiveRecipeTypes} 默认只取控制器的当前激活类型；
 * gtladditions 只对 {@code GTLAddMultipleTypeRecipesLogic} 做了同样的展开。
 * 本模组的机器使用自己的 recipe logic（通用独立线程 / NAC 适配外壳 / 熔融模块逻辑等），
 * 不在 gtladditions 的判定里，因此自动样板总成只会看到一个模式。
 *
 * <p>这里在方法返回后，对包名属于 {@code com.gtlmodular} 的控制器追加
 * {@code getRecipeTypes()} 的全部类型。只读、只影响自动样板总成的“可展开模式”列表，
 * 不改配方、不改结构、不碰其它模组的机器。
 */
@Mixin(value = MERecipePatternHelper.class, remap = false)
public abstract class MERecipePatternHelperMixin {

    @Inject(method = "getActiveRecipeTypes", at = @At("RETURN"), cancellable = true, remap = false)
    private static void gtlmodular$expandAllOurRecipeTypes(List<IMultiController> controllers,
                                                           CallbackInfoReturnable<Set<GTRecipeType>> cir) {
        Set<GTRecipeType> original = cir.getReturnValue();
        Set<GTRecipeType> result = original == null
                ? new LinkedHashSet<>()
                : new LinkedHashSet<>(original);
        if (controllers == null) {
            cir.setReturnValue(result);
            return;
        }
        for (IMultiController controller : controllers) {
            if (controller == null || !controller.isFormed()) {
                continue;
            }
            if (!(controller.self() instanceof IRecipeLogicMachine machine)) {
                continue;
            }
            String className = machine.self().getClass().getName();
            if (!className.startsWith("com.gtlmodular.")) {
                continue;
            }
            GTRecipeType[] types = machine.getRecipeTypes();
            if (types == null) {
                continue;
            }
            for (GTRecipeType type : types) {
                if (type != null) {
                    result.add(type);
                }
            }
        }
        cir.setReturnValue(result);
    }
}
