package com.gtlmodular.mixin.enhancedcore;

import com.gtlmodular.common.machine.LazyPatternAssemblyPartMachine;
import com.gtlmodular.common.machine.MoltenPatternAssemblyPartMachine;

import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachineBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 避免上游自动命名覆盖本模组样板总成的名称。 */
@Mixin(targets = "org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachineBase", remap = false)
public class SuperBufferAutoNameSuppressMixin {

    private static boolean ENABLED = true;

    private static boolean DIAGNOSTIC_LOG = false;

    private static int gtlmodular$diagBudget = 40;

    /** 避免诊断或异常处理再次进入命名拦截。 */
    private static final ThreadLocal<Boolean> GTLMODULAR_SUPPRESS_GUARD =
            ThreadLocal.withInitial(() -> Boolean.FALSE);

    @Inject(method = "setCustomName", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void gtlmodular$suppressUpstreamAutoName(String name, CallbackInfo ci) {
        try {
            if (!ENABLED || GTLMODULAR_SUPPRESS_GUARD.get()) {
                return;
            }
            // 惰性机直接继承抽象样板仓，不能只判断 Molten 子类。
            if (!(((Object) this) instanceof MoltenPatternAssemblyPartMachine)
                    && !(((Object) this) instanceof LazyPatternAssemblyPartMachine)) {
                return;
            }
            // 同值写入无需拦截，先返回以避免构建调用栈的成本。
            String current = ((MEPatternBufferPartMachineBase) (Object) this).getCustomName();
            if (current != null && current.equals(name)) {
                return;
            }
            boolean fromUpstreamAutoNaming = gtlmodular$isUpstreamAutoNaming();
            if (DIAGNOSTIC_LOG) {
                gtlmodular$log("setCustomName 判读：类型={} 值={} 来自上游自动命名={}",
                        ((Object) this).getClass().getSimpleName(), name, fromUpstreamAutoNaming);
            }
            if (!fromUpstreamAutoNaming) {
                return;
            }
            gtlmodular$log("已拦下 Enhancedcore 自动命名对我们的写入：{} 的名字不会变成「{}」",
                    ((Object) this).getClass().getSimpleName(), name);
            ci.cancel();
        } catch (Throwable ignored) {
        }
    }

    /** 用包前缀覆盖 Enhancedcore 的多个调用入口，避免绑定单一方法名漏判。 */
    private static boolean gtlmodular$isUpstreamAutoNaming() {
        GTLMODULAR_SUPPRESS_GUARD.set(Boolean.TRUE);
        try {
            StackTraceElement[] frames = new Throwable().getStackTrace();
            for (StackTraceElement frame : frames) {
                String cls = frame.getClassName();
                if (cls != null && cls.startsWith("com.gtl.enhancedcore.")) {
                    return true;
                }
            }
            return false;
        } catch (Throwable ignored) {
            return false;
        } finally {
            GTLMODULAR_SUPPRESS_GUARD.set(Boolean.FALSE);
        }
    }

    private static void gtlmodular$log(String format, Object... args) {
        try {
            if (gtlmodular$diagBudget <= 0) {
                return;
            }
            gtlmodular$diagBudget--;
            com.gtlmodular.GTLModular.LOGGER.info("[gtlmodular] " + format, args);
        } catch (Throwable ignored) {
        }
    }
}
