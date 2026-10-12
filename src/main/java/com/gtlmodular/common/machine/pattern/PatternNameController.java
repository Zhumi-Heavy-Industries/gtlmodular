package com.gtlmodular.common.machine.pattern;

import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gtlmodular.common.diag.SwallowedErrors;

import java.util.List;
import java.util.Map;

public final class PatternNameController {

    public interface Host {
        List<GTRecipeType> availableRecipeTypes();
        String customName();
        void setCustomName(String name);
        void markDirty();
        boolean isRemote();
        boolean activeCustomNamingEnabled();
        boolean boundModePendingResolve();
        boolean modeCursorTouched();
        int modeCursorIndex();
        int activeRecipeTypeIndex();
        IRecipeLogicMachine firstRecipeLogicController();
        int namingModeIndexOf(boolean bound, int hint, List<GTRecipeType> types);
        int currentModeIndexForNaming(int hint);
        Map<String, String> modeNames();
        String lockedName();
        void setLockedName(String name);
        boolean allowDefaultName(IRecipeLogicMachine controller, List<GTRecipeType> types, int mode);
    }

    private final Host host;
    private String observedName;
    private String appliedModeKey;
    private IRecipeLogicMachine lastNamingController;

    public PatternNameController(Host host) {
        this.host = host;
    }

    public void syncCustomNameToMode(int modeIndexHint) {
        List<GTRecipeType> types = host.availableRecipeTypes();
        String current = host.customName();
        boolean upstreamPlaceholder = PatternModeNaming.isUpstreamPlaceholderName(current);
        // 空名和上游占位名不代表玩家选择，不能因此锁住自动命名。
        if (observedName != null && current != null && !current.isEmpty()
                && !current.equals(observedName) && !upstreamPlaceholder) {
            host.setLockedName(current);
            if (appliedModeKey != null) {
                host.modeNames().put(appliedModeKey, current);
            }
            host.markDirty();
        }
        if (!host.activeCustomNamingEnabled()) {
            if (current == null || current.isEmpty()) {
                int legacy = host.currentModeIndexForNaming(modeIndexHint);
                if (legacy >= 0 && legacy < types.size()) {
                    String legacyName = PatternModeNaming.modeNameForNaming(types.get(legacy));
                    if (legacyName != null) {
                        host.setCustomName(legacyName);
                    }
                }
            }
            observedName = host.customName();
            return;
        }
        // 绑定尚未恢复时写名字表，会把名字错误地归到默认下标。
        if (host.boundModePendingResolve()) {
            observedName = current;
            return;
        }
        boolean bound = host.modeCursorTouched();
        int mode = host.namingModeIndexOf(bound, modeIndexHint, types);
        IRecipeLogicMachine controller = host.firstRecipeLogicController();
        boolean movedToAnotherController = controller != null && lastNamingController != null
                && lastNamingController != controller;
        lastNamingController = controller;
        String key = mode < 0 ? null : PatternModeNaming.modeKeyOf(types.get(mode));
        if (key == null) {
            observedName = current;
            return;
        }
        if (!host.lockedName().isEmpty()) {
            // 上游占位名也不能遮住已保存的玩家名；同值不重写，保留玩家主动使用占位串的选择。
            if ((current == null || current.isEmpty() || upstreamPlaceholder)
                    && !host.lockedName().equals(current)) {
                host.setCustomName(host.lockedName());
                observedName = host.lockedName();
            } else {
                observedName = current;
            }
            appliedModeKey = key;
            return;
        }
        String target = host.modeNames().get(key);
        if (target == null || target.isEmpty()) {
            // 上游可能先于总成 tick 写入占位名，它不能阻止首次生成模式名。
            if (!bound && current != null && !current.isEmpty() && !upstreamPlaceholder
                    && !movedToAnotherController) {
                observedName = current;
                appliedModeKey = key;
                return;
            }
            // 插入机器检查只约束默认名生成，不能提前影响已有名字或手填锁名。
            if (!host.allowDefaultName(controller, types, mode)) {
                observedName = current;
                return;
            }
            String display = PatternModeNaming.modeNameForNaming(types.get(mode));
            if (display == null) {
                observedName = current;
                return;
            }
            target = display;
            host.modeNames().put(key, target);
            host.markDirty();
        }
        // 上游 setter 会更新手动命名标记，避免同值写入重复触发它。
        if (!target.equals(current)) {
            host.setCustomName(target);
        }
        observedName = target;
        appliedModeKey = key;
    }

    public void onPlayerConfirmedCustomName(String name) {
        if (name == null) {
            return;
        }
        if (!host.isRemote()) {
            host.setLockedName(name);
            String key = appliedModeKey;
            if (key == null) {
                List<GTRecipeType> types = host.availableRecipeTypes();
                int mode = host.namingModeIndexOf(host.modeCursorTouched(), host.activeRecipeTypeIndex(), types);
                key = mode < 0 ? null : PatternModeNaming.modeKeyOf(types.get(mode));
            }
            if (key != null) {
                host.modeNames().put(key, name);
            }
            host.markDirty();
        }
        host.setCustomName(name);
        observedName = name;
    }

    public int namingModeIndexOf(boolean bound, int modeIndexHint, List<GTRecipeType> types) {
        int size = types.size();
        if (size <= 0) {
            return -1;
        }
        if (bound && host.modeCursorIndex() >= 0 && host.modeCursorIndex() < size) {
            return host.modeCursorIndex();
        }
        IRecipeLogicMachine controller = host.firstRecipeLogicController();
        if (controller != null) {
            int live = controller.getActiveRecipeType();
            if (live >= 0 && live < size) {
                return live;
            }
        }
        if (modeIndexHint >= 0 && modeIndexHint < size) {
            return modeIndexHint;
        }
        if (host.activeRecipeTypeIndex() >= 0 && host.activeRecipeTypeIndex() < size) {
            return host.activeRecipeTypeIndex();
        }
        return host.modeCursorIndex() >= 0 && host.modeCursorIndex() < size ? host.modeCursorIndex() : -1;
    }

    public int currentModeIndexForNaming(int modeIndexHint) {
        List<GTRecipeType> types = host.availableRecipeTypes();
        int size = types.size();
        if (size <= 0) {
            return -1;
        }
        if (host.modeCursorIndex() >= 0 && host.modeCursorIndex() < size) {
            return host.modeCursorIndex();
        }
        if (modeIndexHint >= 0 && modeIndexHint < size) {
            return modeIndexHint;
        }
        if (host.activeRecipeTypeIndex() >= 0 && host.activeRecipeTypeIndex() < size) {
            return host.activeRecipeTypeIndex();
        }
        IRecipeLogicMachine controller = host.firstRecipeLogicController();
        if (controller != null) {
            int live = controller.getActiveRecipeType();
            if (live >= 0 && live < size) {
                return live;
            }
        }
        return 0;
    }

    public void writeArchivedName(String name) {
        try {
            if (name == null || name.isEmpty()) {
                return;
            }
            host.setCustomName(name);
        } catch (Throwable ignored) {
            SwallowedErrors.report("PatternNameController#writeArchivedName", ignored);
            // 改名失败不影响样板。
        }
    }

    public void reset() {
        // 读档恢复的名字不代表玩家刚改名，必须清除旧的观察记录。
        observedName = null;
        appliedModeKey = null;
        lastNamingController = null;
    }
}
