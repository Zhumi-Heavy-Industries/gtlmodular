package com.gtlmodular.common.machine.nanochip;

import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gtlmodular.common.machine.nanochip.display.NanochipInputSnapshot;
import it.unimi.dsi.fastutil.objects.ObjectSet;

final class NanochipThreadInputs {

    private final IRecipeLogicMachine machine;
    private final NanochipConcurrentBatches batches;
    private final NanochipInputDiagnostics inputDiagnostics;

    int diagHasProxies = -1;

    int diagWireParts = -1;

    int diagWireCached = -1;

    int lastSearchRaw = -1;

    static final long NO_SLOT_BOUND = -1L;

    NanochipThreadInputs(IRecipeLogicMachine machine, NanochipConcurrentBatches batches) {
        this.machine = machine;
        this.batches = batches;
        this.inputDiagnostics = new NanochipInputDiagnostics(machine);
    }

    String inputFaceText() {
        // 日志只读已有样本，不能为每次线程变化再扫一轮库存。
        return inputDiagnostics.cachedDescription();
    }

    long perSlotInputBound(GTRecipe recipe) {
        return inputDiagnostics.probeBound(recipe);
    }

    void observeDiagnosticRecipe(GTRecipe recipe) {
        inputDiagnostics.observe(recipe);
    }

    NanochipInputSnapshot.Sample diagnosticSample() {
        return inputDiagnostics.sample();
    }

    void clearDiagnostic() {
        inputDiagnostics.clear();
    }

    String inputSideText() {
        StringBuilder sb = new StringBuilder(80);
        try {
            diagHasProxies = machine.hasProxies() ? 1 : 0;
        } catch (RuntimeException e) {
            diagHasProxies = -1;
        }
        sb.append("能力代理=").append(diagHasProxies < 0 ? "没读到" : (diagHasProxies == 1 ? "有" : "无"));
        try {
            if (machine instanceof org.gtlcore.gtlcore.api.machine.trait.IRecipeCapabilityMachine capabilityMachine) {
                var parts = capabilityMachine.getMEPatternRecipeHandleParts();
                diagWireParts = parts == null ? -1 : parts.size();
                int cached = 0;
                if (parts != null) {
                    for (var part : parts) {
                        ObjectSet<GTRecipe> set = part.getCachedGTRecipe();
                        if (set != null) {
                            cached += set.size();
                        }
                    }
                }
                diagWireCached = cached;
            }
        } catch (RuntimeException e) {
            diagWireParts = -1;
            diagWireCached = -1;
        }
        sb.append("；样板仓接线=").append(diagWireParts < 0 ? "没读到" : String.valueOf(diagWireParts));
        sb.append("；已缓存配方=").append(diagWireCached < 0 ? "没读到" : String.valueOf(diagWireCached));
        return sb.toString();
    }
}
