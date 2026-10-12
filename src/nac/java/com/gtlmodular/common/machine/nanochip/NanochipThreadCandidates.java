package com.gtlmodular.common.machine.nanochip;

import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.feature.ITieredMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gtlmodular.common.data.nanochip.NanochipModContext;
import net.minecraft.resources.ResourceLocation;
import org.gtlcore.gtlcore.api.recipe.IGTRecipe;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.gtlmodular.common.machine.nanochip.NanochipThreadedRecipeLogic.FAN_OUT_MAX_PER_TICK;
import static com.gtlmodular.common.machine.nanochip.NanochipThreadedRecipeLogic.FAN_OUT_SAME_RECIPE;
import static com.gtlmodular.common.machine.nanochip.NanochipThreadedRecipeLogic.FILL_RETRY_INTERVAL;
import static com.gtlmodular.common.machine.nanochip.NanochipThreadedRecipeLogic.FILL_RETRY_THROTTLE;
import static com.gtlmodular.common.machine.nanochip.NanochipThreadedRecipeLogic.IDLE_SEARCH_INTERVAL;
import static com.gtlmodular.common.machine.nanochip.NanochipThreadedRecipeLogic.IDLE_SEARCH_THROTTLE;

final class NanochipThreadCandidates {

    private final NanochipThreadedRecipeLogic logic;
    private final IRecipeLogicMachine machine;
    private final NanochipThreadHost host;
    private final NanochipConcurrentBatches batches;
    private final NanochipBatchAdmission admission;

    List<GTRecipe> candidatePool;

    int candidateCursor;

    int candidateRawCount;

    int candidateUsableCount;

    String candidateSearchFailure;

    int fanOutOpenedThisTick;

    /** 整池遍历仍无成功时必须结束重试，避免同 tick 无限轮转。 */
    int fanOutScannedSinceSuccess;

    int fillRetryCooldown;

    boolean fillOpenedNothingLastTick;

    boolean fillLostBatchLastTick;

    long fillRetrySkippedTicks;

    int idleSearchCooldown;

    boolean wokenByInputChange;

    long idleSkippedTicks;

    NanochipThreadCandidates(NanochipThreadedRecipeLogic logic, IRecipeLogicMachine machine,
                             NanochipThreadHost host, NanochipConcurrentBatches batches,
                             NanochipBatchAdmission admission) {
        this.logic = logic;
        this.machine = machine;
        this.host = host;
        this.batches = batches;
        this.admission = admission;
    }

    GTRecipe findBatch() {
        if (host == null) {
            return null;
        }
        if (idleSearchThrottled()) {
            return null;
        }
        if (fillRetryThrottled()) {
            return null;
        }
        if (fanOutOpenedThisTick >= FAN_OUT_MAX_PER_TICK) {
            return null;
        }
        if (candidatePool == null) {
            candidatePool = candidateRecipes();
            candidateCursor = 0;
            fanOutScannedSinceSuccess = 0;
            if (candidatePool.isEmpty() && admission.firstReject == null) {
                admission.firstReject = "候选池为空（原始枚举 " + candidateRawCount + " 条，通过资格闸 "
                        + candidateUsableCount + " 条）";
            }
        }
        while (true) {
            if (candidateCursor >= candidatePool.size()) {
                if (!FAN_OUT_SAME_RECIPE || candidatePool.isEmpty()
                        || fanOutScannedSinceSuccess >= candidatePool.size()) {
                    return null;
                }
                candidateCursor = 0;
            }
            GTRecipe candidate = candidatePool.get(candidateCursor++);
            fanOutScannedSinceSuccess++;
            if (!FAN_OUT_SAME_RECIPE && isRunningElsewhere(candidate)) {
                continue;
            }
            GTRecipe prepared = admission.prepareBatch(candidate);
            if (prepared != null) {
                fanOutScannedSinceSuccess = 0;
                fanOutOpenedThisTick++;
                return prepared;
            }
            if (admission.fitSampleArmed && admission.fitSampleRecipeId == null) {
                admission.inspectFitFailure(candidate);
            }
        }
    }

    private List<GTRecipe> candidateRecipes() {
        if (host == null) {
            return List.of();
        }
        List<String> failures = new ArrayList<>(2);
        int[] stats = new int[2];
        try {
            GTRecipeType[] types = host.nanochipSearchTypes();
            // 各类型搜索隔离异常，避免一张表失败清空其他表的候选。
            Set<GTRecipe> found = NanochipCandidateSearch.searchIsolating(machine, types, machine.getRecipeType(),
                    this::isCandidateUsable, logic.getThreadLimit(), host.nanochipThreadCursors(), failures, stats);
            candidateRawCount = stats[0];
            candidateUsableCount = stats[1];
            if (!failures.isEmpty()) {
                candidateSearchFailure = String.join("；", failures);
            }
            return List.copyOf(found);
        } catch (RuntimeException e) {
            candidateSearchFailure = e.getClass().getSimpleName()
                    + (e.getMessage() == null ? "" : ": " + e.getMessage());
            return List.of();
        }
    }

    private boolean isCandidateUsable(GTRecipe candidate) {
        if (candidate == null) {
            return false;
        }
        try {
            if (host != null && !host.nanochipCandidateUsable(candidate)) {
                return false;
            }
        } catch (RuntimeException e) {
            NanochipModContext.logger().debug("[gtlmodular] NAC 独立线程：机器侧候选校验抛异常（不挡）：{}", e.toString());
        }
        return recipeEuTier(candidate) <= hostTier();
    }

    private static int recipeEuTier(GTRecipe recipe) {
        try {
            return IGTRecipe.of(recipe).getEuTier();
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private int hostTier() {
        return machine instanceof ITieredMachine tiered ? tiered.getTier() : Integer.MAX_VALUE;
    }

    private boolean isRunningElsewhere(GTRecipe candidate) {
        for (int i = 0; i < batches.size(); i++) {
            if (isSameRecipe(batches.get(i).recipe, candidate)) {
                return true;
            }
        }
        return false;
    }

    /** 有 ID 时按 ID 判断同种配方，无 ID 时退回引用比较，避免空 ID 引发异常。 */
    private static boolean isSameRecipe(GTRecipe a, GTRecipe b) {
        if (a == null || b == null) {
            return false;
        }
        ResourceLocation idA = a.getId();
        ResourceLocation idB = b.getId();
        if (idA != null && idB != null) {
            return idA.equals(idB);
        }
        return a == b;
    }

    private boolean idleSearchThrottled() {
        if (!IDLE_SEARCH_THROTTLE) {
            return false;
        }
        if (!batches.isEmpty() || wokenByInputChange || logic.getStatus() != RecipeLogic.Status.IDLE) {
            idleSearchCooldown = 0;
            return false;
        }
        if (idleSearchCooldown > 0) {
            idleSearchCooldown--;
            idleSkippedTicks++;
            return true;
        }
        idleSearchCooldown = Math.max(1, IDLE_SEARCH_INTERVAL) - 1;
        return false;
    }

    private boolean fillRetryThrottled() {
        if (!FILL_RETRY_THROTTLE) {
            return false;
        }
        if (wokenByInputChange || batches.isEmpty() || !fillOpenedNothingLastTick || fillLostBatchLastTick
                || logic.getStatus() != RecipeLogic.Status.WORKING) {
            fillRetryCooldown = 0;
            return false;
        }
        if (fillRetryCooldown > 0) {
            fillRetryCooldown--;
            fillRetrySkippedTicks++;
            return true;
        }
        fillRetryCooldown = Math.max(1, FILL_RETRY_INTERVAL) - 1;
        return false;
    }
}
