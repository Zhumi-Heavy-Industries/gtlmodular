package com.gtlmodular.mixin.gtlcore;

import com.gtlmodular.common.machine.PatternSlotIsolationGuard;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 上游处理器共享匹配入口，具体作用范围交给 PatternSlotIsolationGuard 收窄，避免跨槽取料。 */
@Mixin(targets = {
        "org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferRecipeHandlerTraitBase$MEItemInputHandlerBase",
        "org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferRecipeHandlerTraitBase$MEFluidHandlerBase"
}, remap = false)
public abstract class PatternBufferSlotIsolationMixin {

    public abstract org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachineBase getMachine();

    @Inject(method = "meHandleRecipeInner", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void gtlmodular$rejectCrossSlot(GTRecipe recipe, Object2LongMap<?> contents,
                                            boolean simulate, int[] activeSlots,
                                            CallbackInfoReturnable<Boolean> cir) {
        try {
            if (PatternSlotIsolationGuard.rejects(getMachine(), recipe)) {
                cir.setReturnValue(false);
            }
        } catch (Throwable ignored) {
            // 隔离判定异常时放行，避免兼容故障阻塞配方处理。
        }
    }
}
