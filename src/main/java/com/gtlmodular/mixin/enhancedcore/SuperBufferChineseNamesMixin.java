package com.gtlmodular.mixin.enhancedcore;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

/** 服务端命名不能依赖客户端当前语言，使用固定中文映射补足类型名。 */
@Mixin(targets = "com.gtl.enhancedcore.common.recipe.iv.SuperBufferChineseNames", remap = false)
public class SuperBufferChineseNamesMixin {

    /** Mixin 的非 Shadow 可变静态字段必须保持私有，避免预处理拒绝加载。 */
    private static boolean ENABLED = true;

    private static int gtlmodular$diagBudget = 24;

    private static final Map<String, String> GTLMODULAR_NAMES = Map.ofEntries(
            Map.entry("nanochip_assembly_complex", "纳米芯片组装复合体"),
            Map.entry("nanochip_conversion", "纳米芯片换算"),
            Map.entry("nanochip_assembly_matrix", "纳米芯片组装矩阵"),
            Map.entry("nanochip_cutting_chamber", "纳米精度切割仓"),
            Map.entry("nanochip_wire_tracer", "纳米精度导线追踪器"),
            Map.entry("nanochip_encasement_wrapper", "纳米封装卷绕器"),
            Map.entry("nanochip_splitter", "纳米组件分流器"),
            Map.entry("nanochip_smd_processor", "元件预处理装置")
    );

    @Inject(method = "resolve", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void gtlmodular$resolveOwnTypeNames(String keyOrName, CallbackInfoReturnable<String> cir) {
        try {
            if (!ENABLED || keyOrName == null || keyOrName.isEmpty()) {
                return;
            }
            String k = keyOrName.toLowerCase(java.util.Locale.ROOT);
            for (Map.Entry<String, String> e : GTLMODULAR_NAMES.entrySet()) {
                if (k.contains(e.getKey())) {
                    if (gtlmodular$diagBudget > 0) {
                        gtlmodular$diagBudget--;
                        try {
                            com.gtlmodular.GTLModular.LOGGER.info(
                                    "[gtlmodular] 命名适配命中：{} ⇒ {}", keyOrName, e.getValue());
                        } catch (Throwable ignored) {
                        }
                    }
                    cir.setReturnValue(e.getValue());
                    return;
                }
            }
        } catch (Throwable ignored) {
        }
    }
}
