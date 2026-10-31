package com.gtlmodular.mixin.eco;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 临时绕过 eco 的 big-order 提交分支：确认界面的“开始”强制走 AE2 原生 startJob。
 *
 * <p>诊断已确认：服务端 plan/result 正常、CPU 也能选中，但 eco 的写单分支会静默拒绝；
 * 强制走原生提交可以恢复下单。等 eco 修复后再移除。
 */
@Mixin(targets = "cn.dancingsnow.neoecoae.client.gui.crafting.ECOCraftConfirmScreen", remap = false)
public abstract class EcoForceNativeCraftMixin {

    @Inject(method = "usesBigOrderSubmission", require = 1,
            at = @At("HEAD"), cancellable = true, remap = false)
    private void gtlmodular$forceNativeSubmission(boolean forced, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }
}
