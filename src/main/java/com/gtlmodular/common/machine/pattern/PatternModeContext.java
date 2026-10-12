package com.gtlmodular.common.machine.pattern;

import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gtlmodular.common.diag.SwallowedErrors;
import net.minecraft.resources.ResourceLocation;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

public final class PatternModeContext {

    public interface Host {
        List<GTRecipeType> availableRecipeTypes();
        int activeRecipeTypeIndex();
        int modeCursorIndex();
        boolean modeCursorTouched();
        void selectModeCursor(int index);
        void setRecipeType(int index);
        boolean isRemote();
        ResourceLocation boundModeType();
        boolean boundModePendingResolve();
        void storeBoundModeType(ResourceLocation typeId);
        void finishBoundModeResolve();
        void rememberBoundModeType();
        Map<Integer, ResourceLocation> manualSlotTypes();
        int manualTypeIndexOf(int slot, List<GTRecipeType> types);
        void prepareSlotRecipeTypes();
        int slotRecipeTypeCount();
        int cachedSlotRecipeType(int slot);
        void cacheSlotRecipeType(int slot, int type);
        int detectRecipeType(int slot, List<GTRecipeType> types);
        int manualAssignmentSlotCount();
        int maxPatternCount();
        void invalidateManualAssignment(int slot);
        void clearAutoSwitchBlocked();
        void refreshManualAssignDisplay();
        void syncCustomNameToMode(int index);
    }

    private final Host host;
    private IRecipeLogicMachine cachedTypesController;
    private GTRecipeType[] cachedTypesSource;
    private List<GTRecipeType> cachedTypes = List.of();

    public PatternModeContext(Host host) {
        this.host = host;
    }

    public List<GTRecipeType> availableRecipeTypes(IRecipeLogicMachine controller) {
        if (controller == null) {
            cachedTypesController = null;
            cachedTypesSource = null;
            cachedTypes = List.of();
            return cachedTypes;
        }
        GTRecipeType[] types = controller.getRecipeTypes();
        if (controller == cachedTypesController && types == cachedTypesSource) {
            return cachedTypes;
        }
        cachedTypesController = controller;
        cachedTypesSource = types;
        // 数组原地变化必须透过同一份 List 可见，因此只按引用身份缓存视图。
        cachedTypes = types == null ? List.of() : Arrays.asList(types);
        return cachedTypes;
    }

    public static IRecipeLogicMachine firstRecipeLogicController(Iterable<? extends IMultiController> controllers) {
        for (IMultiController controller : controllers) {
            if (controller instanceof IRecipeLogicMachine logicMachine) {
                return logicMachine;
            }
        }
        return null;
    }

    public GTRecipeType activeRecipeType() {
        List<GTRecipeType> types = host.availableRecipeTypes();
        if (host.activeRecipeTypeIndex() >= 0 && host.activeRecipeTypeIndex() < types.size()) {
            return types.get(host.activeRecipeTypeIndex());
        }
        return types.isEmpty() ? null : types.get(0);
    }

    public void cycleRecipeType(boolean forward) {
        List<GTRecipeType> types = host.availableRecipeTypes();
        if (types.isEmpty()) {
            return;
        }
        int size = types.size();
        int base = host.modeCursorIndex() >= 0 && host.modeCursorIndex() < size
                ? host.modeCursorIndex() : host.activeRecipeTypeIndex();
        host.setRecipeType(forward ? (base + 1) % size : (base - 1 + size) % size);
    }

    public void setRecipeType(int index) {
        if (index >= 0 && index < host.availableRecipeTypes().size()) {
            host.selectModeCursor(index);
            host.rememberBoundModeType();
        }
    }

    public void rememberBoundModeType() {
        if (host.isRemote()) {
            return;
        }
        if (!host.modeCursorTouched()) {
            return;
        }
        List<GTRecipeType> types = host.availableRecipeTypes();
        if (host.modeCursorIndex() < 0 || host.modeCursorIndex() >= types.size()) {
            return;
        }
        GTRecipeType type = types.get(host.modeCursorIndex());
        if (type == null || type.registryName == null) {
            return;
        }
        if (!type.registryName.equals(host.boundModeType())) {
            host.storeBoundModeType(type.registryName);
        }
        host.finishBoundModeResolve();
    }

    public void applyBoundModeTypeIfPossible() {
        if (host.boundModeType() == null || !host.boundModePendingResolve()) {
            return;
        }
        List<GTRecipeType> types = host.availableRecipeTypes();
        if (types.isEmpty()) {
            // 未成型时的空表无法区分失效绑定和控制器尚未就绪。
            return;
        }
        for (int i = 0; i < types.size(); i++) {
            GTRecipeType type = types.get(i);
            if (type != null && host.boundModeType().equals(type.registryName)) {
                host.selectModeCursor(i);
                host.finishBoundModeResolve();
                return;
            }
        }
        host.finishBoundModeResolve();
    }

    public int recipeTypeOfSlot(int slot, int unknownType) {
        List<GTRecipeType> types = host.availableRecipeTypes();
        if (types.isEmpty() || slot < 0) {
            return -1;
        }
        int manual = host.manualTypeIndexOf(slot, types);
        if (manual >= 0) {
            return manual;
        }
        host.prepareSlotRecipeTypes();
        if (slot >= host.slotRecipeTypeCount()) {
            return -1;
        }
        int cached = host.cachedSlotRecipeType(slot);
        if (cached >= 0 && cached < types.size()) {
            return cached;
        }
        if (cached == unknownType) {
            return -1;
        }
        int detected = host.detectRecipeType(slot, types);
        host.cacheSlotRecipeType(slot, detected >= 0 ? detected : unknownType);
        return detected;
    }

    public static int manualTypeIndexOf(int slot, List<GTRecipeType> types,
                                        Map<Integer, ResourceLocation> manualSlotTypes) {
        ResourceLocation id = manualSlotTypes.get(slot);
        if (id == null) {
            return -1;
        }
        for (int i = 0; i < types.size(); i++) {
            GTRecipeType type = types.get(i);
            if (type != null && id.equals(type.registryName)) {
                return i;
            }
        }
        return -1;
    }

    public static void setManualAssignSlot(int slot, IntSupplier slotLimit,
                                           IntConsumer selectedSlot, Runnable refreshDisplay) {
        selectedSlot.accept(Math.max(1, Math.min(slotLimit.getAsInt(), slot)));
        refreshDisplay.run();
    }

    public void assignManualType(int slotNumber, boolean clear) {
        List<GTRecipeType> types = host.availableRecipeTypes();
        int slot = slotNumber - 1;
        if (slot < 0 || slot >= host.manualAssignmentSlotCount()) {
            return;
        }
        if (clear) {
            host.manualSlotTypes().remove(slot);
        } else {
            if (types.isEmpty()) {
                return;
            }
            int index = Math.max(0, Math.min(types.size() - 1, host.modeCursorIndex()));
            GTRecipeType type = types.get(index);
            if (type == null || type.registryName == null) {
                return;
            }
            host.manualSlotTypes().put(slot, type.registryName);
        }
        // 主机也会读取 GT 配方缓存，只清类型下标会让旧模式配方继续参与查找。
        host.invalidateManualAssignment(slot);
        host.clearAutoSwitchBlocked();
        host.refreshManualAssignDisplay();
        host.rememberBoundModeType();
        host.syncCustomNameToMode(host.modeCursorIndex());
    }

    public void writeArchivedManualType(int slot, ResourceLocation typeId) {
        try {
            if (slot < 0 || slot >= host.maxPatternCount() || typeId == null) {
                return;
            }
            host.manualSlotTypes().put(slot, typeId);
            host.refreshManualAssignDisplay();
        } catch (Throwable ignored) {
            SwallowedErrors.report("PatternModeContext#writeArchivedManualType", ignored);
            // 手动指定写入失败不影响已落地的样板。
        }
    }
}
