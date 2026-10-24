package com.gtlmodular.common.machine.nanochip;

import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gtlmodular.common.data.nanochip.NanochipModContext;
import com.gtlmodular.common.machine.MeWiringRebuild;
import com.gtlmodular.common.machine.nanochip.display.NanochipInputSnapshot;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.gtlcore.gtlcore.api.machine.trait.IRecipeCapabilityMachine;
import org.gtlcore.gtlcore.api.machine.trait.MEPatternRecipeHandlePart;

import java.util.List;

final class NanochipThreadInputs {

    private final IRecipeLogicMachine machine;
    private final NanochipConcurrentBatches batches;
    private final NanochipInputDiagnostics inputDiagnostics;

    /** 接线重建的限频/限次/证伪状态机与地心模块共用一份实现。 */
    private final MeWiringRebuild wiring = new MeWiringRebuild("");

    int diagHasProxies = -1;

    int diagWireParts = -1;

    int diagWireCached = -1;

    int lastSearchRaw = -1;

    boolean inputWiringRefreshedThisTick;

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

    void refreshInputWiringIfStale() {
        // 只在空闲（没有在跑批次、上一轮也没搜到候选）时重建，避免打断正在推进的批次。
        if (!batches.isEmpty() || lastSearchRaw != 0) {
            return;
        }
        var outcome = wiring.ensure(machine);
        inputWiringRefreshedThisTick = wiring.attemptedThisTick();
        if (outcome == MeWiringRebuild.Outcome.REBUILD_FAILED) {
            NanochipModContext.logger().debug("[gtlmodular] NAC 独立线程：输入侧接线重建失败（保持原样，不影响派活）：{}",
                    wiring.describe());
        }
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
            if (machine instanceof IRecipeCapabilityMachine capabilityMachine) {
                List<MEPatternRecipeHandlePart> parts = capabilityMachine.getMEPatternRecipeHandleParts();
                diagWireParts = parts == null ? -1 : parts.size();
                int cached = 0;
                if (parts != null) {
                    for (MEPatternRecipeHandlePart part : parts) {
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
        sb.append("；").append(wiring.describe());
        if (wiring.attemptedThisTick()) {
            sb.append("（本 tick 刚建过）");
        }
        return sb.toString();
    }
}
