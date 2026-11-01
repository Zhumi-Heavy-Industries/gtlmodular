package com.gtlmodular.mixin.eco;

import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.menu.me.crafting.CraftConfirmMenu;
import appeng.menu.me.crafting.CraftingPlanSummary;

import com.gtlmodular.GTLModular;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.util.List;

/**
 * 临时安全诊断：不调用可能为 null 的 getName()，只打印 plan/result/CPU 类名/CPU 列表大小/网格状态。
 */
@Mixin(value = CraftConfirmMenu.class, remap = false)
public abstract class EcoCraftDebugMixin {

    @Shadow private ICraftingPlan result;
    @Shadow private ICraftingCPU selectedCpu;
    @Shadow private CraftingPlanSummary plan;
    @Shadow public boolean noCPU;
    @Shadow public CraftConfirmMenu.SyncableSubmitResult submitError;

    private static String gtlmodular$lastState = "";

    @Inject(method = "m_38946_", require = 0, at = @At("HEAD"), remap = false)
    private void gtlmodular$onBroadcast(CallbackInfo ci) {
        log("broadcast");
    }

    @Inject(method = "startJob", require = 0, at = @At("HEAD"), remap = false)
    private void gtlmodular$onStartJob(CallbackInfo ci) {
        log("startJob");
    }

    @Inject(method = "setPlan", require = 0, at = @At("HEAD"), remap = false)
    private void gtlmodular$onSetPlan(CraftingPlanSummary summary, CallbackInfo ci) {
        log("setPlan");
    }

    private void log(String where) {
        String cpuName;
        try {
            cpuName = selectedCpu == null || selectedCpu.getName() == null
                    ? "null"
                    : selectedCpu.getName().getString();
        } catch (Throwable t) {
            cpuName = "ERR:" + t.getClass().getSimpleName();
        }
        int cpuCount = -1;
        try {
            Object cycler = field(this, "cpuCycler");
            Object cpus = field(cycler, "cpus");
            if (cpus instanceof List<?> list) cpuCount = list.size();
        } catch (Throwable ignored) {
        }
        String state = "plan=" + (plan == null ? "null" : "present")
                + " result=" + (result == null ? "null" : result.getClass().getSimpleName())
                + " cpu=" + (selectedCpu == null ? "null" : selectedCpu.getClass().getSimpleName())
                + " cpuName=" + cpuName
                + " cpuList=" + cpuCount
                + " noCPU=" + noCPU
                + " submitError=" + submitError;
        if (state.equals(gtlmodular$lastState)) return;
        gtlmodular$lastState = state;
        GTLModular.LOGGER.info("[哦鲸鲸终端/诊断] {}: {}", where, state);
    }

    private static Object field(Object target, String name) throws ReflectiveOperationException {
        if (target == null) return null;
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field f = type.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(target);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }
        return null;
    }
}
