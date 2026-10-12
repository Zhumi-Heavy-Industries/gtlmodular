package com.gtlmodular.mixin.enhancedcore;

import com.gtlmodular.GTLModular;
import com.gtlmodular.common.machine.LazyPatternAssemblyPartMachine;
import com.gtlmodular.common.machine.MoltenPatternAssemblyPartMachine;

import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachineBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 避免缺少 IV 路由标记的样板被隔离过滤；返回非隔离也会跳过上游共享催化剂收集。 */
@Mixin(targets = "com.gtl.enhancedcore.common.recipe.iv.IvBuffers", remap = false)
public class IvBuffersIsolatedMixin {

    /** Mixin 的非 Shadow 可变静态字段必须保持私有，避免预处理拒绝加载。 */
    private static boolean BYPASS_ENABLED = true;

    @Inject(method = "isolated", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void gtlmodular$notIsolatedForOurBuffer(MEPatternBufferPartMachineBase buffer,
                                                          CallbackInfoReturnable<Boolean> cir) {
        try {
            if (!BYPASS_ENABLED || buffer == null) {
                return;
            }
            if (buffer instanceof MoltenPatternAssemblyPartMachine
                    || buffer instanceof LazyPatternAssemblyPartMachine) {
                GTLModular.LOGGER.info(
                        "[gtlmodular] 自适配补丁生效：IvBuffers.isolated({}) ⇒ false（跳过类型白名单过滤）",
                        buffer.getClass().getSimpleName());
                cir.setReturnValue(false);
            }
        } catch (Throwable ignored) {
            // 兼容判定失败时让上游继续处理。
        }
    }
}
