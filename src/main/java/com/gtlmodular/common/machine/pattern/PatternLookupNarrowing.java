package com.gtlmodular.common.machine.pattern;

import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gtlmodular.common.diag.SwallowedErrors;
import com.gtlmodular.common.machine.MultiTypeLookupHost;

import java.util.Arrays;
import java.util.List;
import java.util.function.IntPredicate;
import java.util.function.IntSupplier;
import java.util.function.IntUnaryOperator;
import java.util.function.Supplier;

public final class PatternLookupNarrowing {

    private PatternLookupNarrowing() {
    }

    @FunctionalInterface
    public interface ManualTypeLookup {
        int typeIndex(int slot, List<GTRecipeType> types);
    }

    public static int ownModeIndex(Supplier<List<GTRecipeType>> availableTypes,
                                   IntSupplier modeCursor, IntSupplier activeType) {
        try {
            List<GTRecipeType> types = availableTypes.get();
            int size = types == null ? 0 : types.size();
            if (size <= 0) {
                return -1;
            }
            if (modeCursor.getAsInt() >= 0 && modeCursor.getAsInt() < size) {
                return modeCursor.getAsInt();
            }
            if (activeType.getAsInt() >= 0 && activeType.getAsInt() < size) {
                return activeType.getAsInt();
            }
            return -1;
        } catch (Throwable failure) {
            SwallowedErrors.report("PatternLookupNarrowing#ownModeIndex", failure);
            // 无法确认总成自己的模式时，收窄可能挡住本该参与查找的槽。
            return -1;
        }
    }

    public static int lookupModeIndex(IntSupplier ownMode, Supplier<List<GTRecipeType>> availableTypes,
                                      Supplier<IRecipeLogicMachine> firstController) {
        int own = ownMode.getAsInt();
        if (own < 0) {
            return -1;
        }
        List<GTRecipeType> types = availableTypes.get();
        IRecipeLogicMachine controller = firstController.get();
        if (controller == null || types == null || types.isEmpty()) {
            return -1;
        }
        int live = controller.getActiveRecipeType();
        if (live < 0 || live >= types.size()) {
            return -1;
        }
        if (live != own) {
            return -1;
        }
        if (controller instanceof MultiTypeLookupHost multiTypeHost && multiTypeHost.searchesAllRecipeTypes()) {
            // 多类型搜索还需要非激活类型的冷槽，不能按单一模式排除它们。
            return -1;
        }
        return own;
    }

    public static int[] narrowSlots(int[] base, IntSupplier narrowingMode, IntPredicate participates) {
        if (base == null || base.length == 0) {
            return base;
        }
        if (narrowingMode.getAsInt() < 0) {
            return base;
        }
        int kept = 0;
        try {
            for (int i = 0; i < base.length; i++) {
                if (participates.test(base[i])) {
                    base[kept++] = base[i];
                }
            }
        } catch (Throwable failure) {
            SwallowedErrors.report("PatternLookupNarrowing#narrowSlots", failure);
            // 调用方持有同一个数组，异常回退也必须保留已经发生的原地覆盖。
            return base;
        }
        if (kept == base.length) {
            return base;
        }
        return Arrays.copyOf(base, kept);
    }

    public static boolean slotParticipates(int slot, Supplier<List<GTRecipeType>> availableTypes,
                                           ManualTypeLookup manualType, IntUnaryOperator cachedType,
                                           IntSupplier narrowingMode) {
        try {
            List<GTRecipeType> types = availableTypes.get();
            if (types == null || types.isEmpty()) {
                return true;
            }
            int manual = manualType.typeIndex(slot, types);
            if (manual >= 0) {
                return manual == narrowingMode.getAsInt();
            }
            int cached = cachedType.applyAsInt(slot);
            if (cached < 0 || cached >= types.size()) {
                return true;
            }
            return cached == narrowingMode.getAsInt();
        } catch (Throwable failure) {
            SwallowedErrors.report("PatternLookupNarrowing#slotParticipates", failure);
            // 缓存不足以确定类型时，不能在查找热路径触发识别或建槽。
            return true;
        }
    }
}
