package com.gtlmodular.common.machine.nanochip;

import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gtlmodular.common.data.nanochip.NanochipModContext;
import com.gtlmodular.common.machine.nanochip.display.NanochipInputSnapshot;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.gtlcore.gtlcore.api.machine.trait.IRecipeCapabilityMachine;
import org.gtlcore.gtlcore.api.machine.trait.MEPatternRecipeHandlePart;

import java.util.List;

import static com.gtlmodular.common.machine.nanochip.NanochipThreadedRecipeLogic.INPUT_WIRING_REBUILD_GATED;

final class NanochipThreadInputs {

    private final IRecipeLogicMachine machine;
    private final NanochipConcurrentBatches batches;
    private final NanochipInputDiagnostics inputDiagnostics;

    int diagHasProxies = -1;

    int diagWireParts = -1;

    int diagWireCached = -1;

    int lastSearchRaw = -1;

    boolean inputWiringRefreshedThisTick;

    private int inputWiringRefreshes;

    private int inputWiringBudget = INPUT_WIRING_REFRESH_BUDGET;

    private int inputWiringCooldown;

    private long inputWiringLastRefreshTick = -1L;

    private int inputWiringPartsBefore = -1;

    private int inputWiringPartsAfter = -1;

    private int inputWiringCachedBefore = -1;

    private int inputWiringCachedAfter = -1;

    private int inputWiringFutile;

    private String inputWiringStopReason;

    static final long NO_SLOT_BOUND = -1L;

    private static final int INPUT_WIRING_REFRESH_INTERVAL = 100;

    private static final int INPUT_WIRING_FUTILE_MAX = 2;

    /** 重建可能重复注册输入变化监听器，因此必须限制总次数。 */
    private static final int INPUT_WIRING_REFRESH_BUDGET = 32;

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
        if (inputWiringCooldown > 0) {
            inputWiringCooldown--;
        }
        if (!batches.isEmpty() || lastSearchRaw != 0) {
            return;
        }
        if (inputWiringBudget <= 0 || inputWiringCooldown > 0) {
            return;
        }
        if (!(machine instanceof IRecipeCapabilityMachine capabilityMachine)) {
            return;
        }
        if (INPUT_WIRING_REBUILD_GATED) {
            int partsNow = mePatternPartCount(capabilityMachine);
            if (partsNow > 0) {
                inputWiringCooldown = INPUT_WIRING_REFRESH_INTERVAL;
                return;
            }
        }
        inputWiringCooldown = INPUT_WIRING_REFRESH_INTERVAL;
        inputWiringBudget--;
        inputWiringRefreshes++;
        inputWiringLastRefreshTick = currentGameTime();
        inputWiringPartsBefore = mePatternPartCount(capabilityMachine);
        inputWiringCachedBefore = mePatternCachedRecipeCount(capabilityMachine);
        // 重建可能抛异常，先清掉结果读数，避免诊断误用上一次成功值。
        inputWiringPartsAfter = -1;
        inputWiringCachedAfter = -1;
        try {
            capabilityMachine.upDate();
            inputWiringRefreshedThisTick = true;
        } catch (RuntimeException e) {
            inputWiringBudget = 0;
            inputWiringStopReason = "重建抛异常 ⇒ 已永久停手（" + e.getClass().getSimpleName() + "）";
            NanochipModContext.logger().debug("[gtlmodular] NAC 独立线程：输入侧接线重建失败（保持原样，不影响派活）：{}",
                    e.toString());
            return;
        }
        inputWiringPartsAfter = mePatternPartCount(capabilityMachine);
        inputWiringCachedAfter = mePatternCachedRecipeCount(capabilityMachine);
        if (!INPUT_WIRING_REBUILD_GATED) {
            return;
        }
        if (inputWiringPartsAfter == inputWiringPartsBefore
                && inputWiringCachedAfter == inputWiringCachedBefore) {
            inputWiringFutile++;
            if (inputWiringFutile >= INPUT_WIRING_FUTILE_MAX) {
                inputWiringBudget = 0;
                inputWiringStopReason = "重建前后一字未变 ⇒ 已永久停手（接线数 "
                        + inputWiringPartsAfter + "、已缓存配方 " + inputWiringCachedAfter + "）";
            }
        } else {
            inputWiringFutile = 0;
        }
    }

    private int mePatternPartCount(IRecipeCapabilityMachine capabilityMachine) {
        try {
            List<MEPatternRecipeHandlePart> parts = capabilityMachine.getMEPatternRecipeHandleParts();
            return parts == null ? -1 : parts.size();
        } catch (RuntimeException e) {
            return -1;
        }
    }

    private int mePatternCachedRecipeCount(IRecipeCapabilityMachine capabilityMachine) {
        try {
            List<MEPatternRecipeHandlePart> parts = capabilityMachine.getMEPatternRecipeHandleParts();
            if (parts == null) {
                return -1;
            }
            int cached = 0;
            for (MEPatternRecipeHandlePart part : parts) {
                ObjectSet<GTRecipe> set = part.getCachedGTRecipe();
                if (set != null) {
                    cached += set.size();
                }
            }
            return cached;
        } catch (RuntimeException e) {
            return -1;
        }
    }

    private long currentGameTime() {
        try {
            var level = machine.self().getLevel();
            return level == null ? -1L : level.getGameTime();
        } catch (RuntimeException e) {
            return -1L;
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
        sb.append("；接线重建=").append(inputWiringRefreshes).append("次")
                .append(inputWiringRefreshedThisTick ? "（本 tick 刚建过）" : "")
                .append("（余 ").append(Math.max(0, inputWiringBudget)).append(" 次）");
        if (inputWiringLastRefreshTick >= 0L) {
            sb.append("；上次重建@").append(inputWiringLastRefreshTick);
            sb.append("；重建前后[接线 ").append(inputWiringPartsBefore)
                    .append("→").append(inputWiringPartsAfter)
                    .append("；已缓存配方 ").append(inputWiringCachedBefore)
                    .append("→").append(inputWiringCachedAfter).append(']');
        }
        if (inputWiringStopReason != null) {
            sb.append("；接线重建已停手（").append(inputWiringStopReason).append("）");
        } else if (inputWiringFutile > 0) {
            sb.append("；重建已证伪 ").append(inputWiringFutile).append(" 次（连续 ")
                    .append(INPUT_WIRING_FUTILE_MAX).append(" 次即停手）");
        }
        return sb.toString();
    }
}
