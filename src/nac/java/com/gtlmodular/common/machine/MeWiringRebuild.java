package com.gtlmodular.common.machine;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.gtlcore.gtlcore.api.machine.trait.IRecipeCapabilityMachine;
import org.gtlcore.gtlcore.api.machine.trait.MEPart.IMEFilterIOPartMachine;
import org.gtlcore.gtlcore.api.machine.trait.MEPatternRecipeHandlePart;

import java.util.List;

/**
 * gtlcore 只把 ME 部件登记进 {@code upDate()} 重建的两张表，而 {@code upDate()} 的唯一触发点是
 * 结构成型的 TAIL 注入；{@code isFormed} 却是持久化字段，读档不会重新成型。
 * 于是读档后（或换仓触发 {@code onPartUnload} 的 clear() 后）表一直是空的：结构里明明有 ME 部件，
 * 机器却按「没有输入」去匹配，表现为「仓里有货、配方 0 候选」。这里按需重建接线，
 * 限频/限次由 {@link MeWiringBudget} 负责：每次重建都会重复注册输入变化监听器。
 */
public final class MeWiringRebuild {

    public enum Outcome {
        /** 结构里没有 ME 部件，不需要接线。 */
        NOT_APPLICABLE,
        /** 接线已可用（表里有部件、且已有缓存配方）。 */
        HEALTHY,
        /** 这轮不该重建（冷却中或次数用完）。 */
        WAITING,
        /** 刚刚重建过。 */
        REBUILT,
        /** 重建抛异常，已永久停手。 */
        REBUILD_FAILED,
        /** 连续重建无效，已永久停手。 */
        GAVE_UP
    }

    private final String label;
    private final MeWiringBudget budget = new MeWiringBudget();

    private int partsBefore = -1;
    private int partsAfter = -1;
    private int cachedBefore = -1;
    private int cachedAfter = -1;
    private boolean attemptedThisTick;

    public MeWiringRebuild(String label) {
        this.label = label;
    }

    /**
     * 每次搜索/派活前调用。只有「结构里有 ME 部件、但接线表或缓存配方为空」时才动。
     * 纯读分支不产生副作用，调用点可以每 tick 调用。
     */
    public Outcome ensure(Object machine) {
        attemptedThisTick = false;
        if (!(machine instanceof IRecipeCapabilityMachine capability)) return Outcome.NOT_APPLICABLE;
        if (!(machine instanceof MultiblockControllerMachine controller)) return Outcome.NOT_APPLICABLE;
        if (!controller.isFormed()) return Outcome.NOT_APPLICABLE;
        if (controller.getLevel() == null || controller.getLevel().isClientSide) return Outcome.NOT_APPLICABLE;
        if (countMeParts(controller) <= 0) return Outcome.NOT_APPLICABLE;

        int wired = registeredPartCount(capability);
        int cached = cachedRecipeCount(capability);
        if (wired > 0 && cached > 0) return Outcome.HEALTHY;

        long now = controller.getLevel().getGameTime();
        return switch (budget.peek(now)) {
            case STOPPED -> Outcome.WAITING;
            case WAITING -> Outcome.WAITING;
            case ALLOWED -> rebuild(capability, now, wired, cached);
        };
    }

    private Outcome rebuild(IRecipeCapabilityMachine capability, long now, int wired, int cached) {
        budget.start(now);
        partsBefore = wired;
        cachedBefore = cached;
        partsAfter = -1;
        cachedAfter = -1;
        try {
            capability.upDate();
            attemptedThisTick = true;
        } catch (RuntimeException | LinkageError failure) {
            budget.fail("重建抛 " + failure.getClass().getSimpleName());
            return Outcome.REBUILD_FAILED;
        }
        partsAfter = registeredPartCount(capability);
        cachedAfter = cachedRecipeCount(capability);
        budget.record(wired + cached, partsAfter + cachedAfter);
        return budget.stopped() ? Outcome.GAVE_UP : Outcome.REBUILT;
    }

    /** 本 tick 是否真的重建过（调用方用它决定要不要作废自己的候选/兼容性缓存）。 */
    public boolean attemptedThisTick() {
        return attemptedThisTick;
    }

    /** 结构里还有 ME 部件、但接线表仍是空的：此时不能当成「没有 ME 输入」静默退化成逐仓搬运。 */
    public static boolean wiringStillEmpty(Object machine) {
        if (!(machine instanceof IRecipeCapabilityMachine capability)) return false;
        if (!(machine instanceof MultiblockControllerMachine controller)) return false;
        if (!controller.isFormed()) return false;
        if (countMeParts(controller) <= 0) return false;
        return registeredPartCount(capability) <= 0;
    }

    /** 结构里有几个 ME 部件（用来判断这台机器到底有没有 ME 输入/输出件）。 */
    public static int mePartCount(Object machine) {
        if (!(machine instanceof MultiblockControllerMachine controller)) return 0;
        return countMeParts(controller);
    }

    /** 已登记的 ME 接线数（样板部件 + 输出部件）。 */
    public static int registeredWiring(Object machine) {
        if (!(machine instanceof IRecipeCapabilityMachine capability)) return 0;
        return registeredPartCount(capability);
    }

    /** 已缓存到样板槽的配方数：为 0 说明样板槽还没被成功匹配过。 */
    public static int cachedRecipes(Object machine) {
        if (!(machine instanceof IRecipeCapabilityMachine capability)) return 0;
        return cachedRecipeCount(capability);
    }

    public String describe() {
        StringBuilder text = new StringBuilder(112);
        text.append(label).append("接线重建=").append(budget.attempts()).append("次（余 ")
                .append(budget.remaining()).append(" 次）");
        if (budget.lastAttemptAt() >= 0L) {
            text.append("；上次@").append(budget.lastAttemptAt());
            text.append("；前后[接线 ").append(partsBefore).append('→').append(partsAfter)
                    .append("；缓存配方 ").append(cachedBefore).append('→').append(cachedAfter).append(']');
        }
        if (budget.stopReason() != null) text.append("；已停手（").append(budget.stopReason()).append('）');
        return text.toString();
    }

    private static int countMeParts(MultiblockControllerMachine controller) {
        List<IMultiPart> parts = controller.getParts();
        if (parts == null || parts.isEmpty()) return 0;
        int count = 0;
        for (IMultiPart part : parts) {
            if (part instanceof IMEFilterIOPartMachine) count++;
        }
        return count;
    }

    private static int registeredPartCount(IRecipeCapabilityMachine capability) {
        try {
            List<MEPatternRecipeHandlePart> parts = capability.getMEPatternRecipeHandleParts();
            int pattern = parts == null ? 0 : parts.size();
            var output = capability.getMEOutputRecipeHandleParts();
            return pattern + (output == null ? 0 : output.size());
        } catch (RuntimeException failure) {
            return 0;
        }
    }

    private static int cachedRecipeCount(IRecipeCapabilityMachine capability) {
        try {
            List<MEPatternRecipeHandlePart> parts = capability.getMEPatternRecipeHandleParts();
            if (parts == null) return 0;
            int cached = 0;
            for (MEPatternRecipeHandlePart part : parts) {
                ObjectSet<GTRecipe> recipes = part.getCachedGTRecipe();
                if (recipes != null) cached += recipes.size();
            }
            return cached;
        } catch (RuntimeException failure) {
            return 0;
        }
    }
}
