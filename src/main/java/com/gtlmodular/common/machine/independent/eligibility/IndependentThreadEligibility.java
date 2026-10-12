package com.gtlmodular.common.machine.independent.eligibility;

import java.util.Set;

public final class IndependentThreadEligibility {

    private final Set<Class<?>> standardLogicClasses;

    public IndependentThreadEligibility(Set<Class<?>> standardLogicClasses) {
        this.standardLogicClasses = Set.copyOf(standardLogicClasses);
    }

    // 只判断配置资格，避免白名单越过适配能力、Mutable 模式或实际资源检查。
    public Decision evaluate(ThreadEligibilitySettings settings, String machineId, Class<?> actualLogicClass,
                             boolean hasOwnedWork) {
        boolean eligible = settings.enabled()
                && ThreadEligibilitySettings.isValidMachineId(machineId)
                && !settings.blacklist().contains(machineId)
                && actualLogicClass != null
                && (standardLogicClasses.contains(actualLogicClass) || settings.whitelist().contains(machineId));
        return new Decision(eligible, hasOwnedWork);
    }

    // 已扣料、待结算和无法解码的自有记录均有保留义务，不受新批准入资格约束。
    public record Decision(boolean eligibleForNewBatches, boolean mustRetainOwnedWork) {
    }
}
