package com.gtlmodular.mixin.eco;

import net.minecraft.network.chat.Component;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * eco 的 ECOCraftingCPU.getName() 在服务端可能返回 null；AE2 原生提交/日志会因此出问题。
 * 只在返回 null 时补一个显示名，不改 CPU 逻辑。
 */
@Mixin(targets = "cn.dancingsnow.neoecoae.crafting.execution.ECOCraftingCPU", remap = false)
public abstract class EcoCpuNameMixin {

    @Inject(method = "getName", require = 1, at = @At("RETURN"), cancellable = true, remap = false)
    private void gtlmodular$fallbackName(CallbackInfoReturnable<Component> cir) {
        if (cir.getReturnValue() == null) {
            cir.setReturnValue(Component.literal("ECO CPU"));
        }
    }
}
