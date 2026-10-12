package com.gtlmodular.common.machine.nanochip;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gtlmodular.common.data.nanochip.NanochipModContext;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.network.chat.Component;
import org.gtlcore.gtlcore.api.recipe.RecipeRunnerHelper;

import java.util.Map;

final class NanochipBatchLifecycle {

    private final IRecipeLogicMachine machine;
    private final NanochipBatchAdmission admission;
    private final NanochipBatchRuntime batchRuntime;
    private final NanochipThreadDiagnostics diagnostics;

    NanochipBatchLifecycle(IRecipeLogicMachine machine, NanochipBatchAdmission admission,
                           NanochipBatchRuntime batchRuntime, NanochipThreadDiagnostics diagnostics) {
        this.machine = machine;
        this.admission = admission;
        this.batchRuntime = batchRuntime;
        this.diagnostics = diagnostics;
    }

    boolean consumeInputs(GTRecipe recipe) {
        if (recipe == null) {
            return false;
        }
        boolean allowed;
        try {
            allowed = machine.beforeWorking(recipe);
        } catch (RuntimeException e) {
            admission.noteReject(recipe, "扣料前 beforeWorking 抛异常", e.getClass().getSimpleName() + ": " + e.getMessage());
            NanochipModContext.logger().debug("[gtlmodular] NAC 独立线程：beforeWorking 抛异常，不收这一条：{}", e.toString());
            return false;
        }
        if (!allowed) {
            admission.noteReject(recipe, "机器级 beforeWorking 否决（维护/结构/模块未被允许开工）", null);
            return false;
        }
        try {
            recipe.preWorking(machine);
        } catch (RuntimeException e) {
            admission.noteReject(recipe, "preWorking 抛异常", e.getClass().getSimpleName() + ": " + e.getMessage());
            NanochipModContext.logger().debug("[gtlmodular] NAC 独立线程：preWorking 抛异常，不收这一条：{}", e.toString());
            return false;
        }
        boolean consumed;
        try {
            consumed = RecipeRunnerHelper.handleRecipeInput(machine, recipe);
        } catch (RuntimeException e) {
            consumed = false;
            admission.noteReject(recipe, "扣料 handleRecipeInput 抛异常", e.getClass().getSimpleName() + ": " + e.getMessage());
            NanochipModContext.logger().debug("[gtlmodular] NAC 独立线程：扣料抛异常，不收这一条：{}", e.toString());
        }
        if (!consumed) {
            admission.noteReject(recipe, "扣料不齐（输入侧拿不到料 / 身份闸拒了这个槽）", null);
            return false;
        }
        // 只有扣料成功才登记附注，避免失败尝试留下预留或概率状态。
        batchRuntime.opened(recipe);
        diagnostics.batchOpened(recipe);
        return true;
    }

    boolean tickInputs(GTRecipe recipe) {
        if (recipe == null) {
            return false;
        }
        if (!admission.conditionsOk(recipe, true)) {
            diagnostics.runningWait(recipe, "运行条件未满足/异常；全局首个等待原因=" + admission.waitReason);
            return false;
        }
        GTRecipe.ActionResult match;
        try {
            match = recipe.matchTickRecipe(machine);
        } catch (RuntimeException e) {
            diagnostics.runningWait(recipe, "tick 匹配异常：" + e.getClass().getSimpleName());
            NanochipModContext.logger().debug("[gtlmodular] NAC 独立线程：tick 匹配抛异常，这条原地等：{}", e.toString());
            return false;
        }
        if (match == null || !match.isSuccess()) {
            if (admission.waitReason == null) {
                admission.waitReason = NanochipBatchAdmission.reasonOf(match);
            }
            diagnostics.runningWait(recipe, "tick 输入/输出匹配失败；全局首个等待原因=" + admission.waitReason);
            return false;
        }
        Map<RecipeCapability<?>, Object2IntMap<?>> caches = batchRuntime.cachesOf(recipe);
        recipe.handleTickRecipeIO(IO.IN, machine, caches);
        recipe.handleTickRecipeIO(IO.OUT, machine, caches);
        return true;
    }

    /** 收尾钩子与校准只能在实际交付后执行一次，等待期间保留本批次的附注。 */
    boolean tryFinishOutputs(GTRecipe recipe) {
        if (recipe == null) {
            return false;
        }
        if (!NanochipRecipeOutputs.tryOutput(machine, recipe)) {
            admission.waitReason = Component.translatable(NanochipRecipeOutputs.OUTPUT_FULL);
            diagnostics.runningWait(recipe, "输出已满，保留完成批次等待出料");
            return false;
        }
        if (machine instanceof NanochipAssemblyComplexMachine nac) {
            nac.recordExternalProduction(recipe);
            nac.logCraftedRecipe(recipe);
        }
        try {
            machine.afterWorking();
        } catch (RuntimeException e) {
            NanochipModContext.logger().debug("[gtlmodular] NAC 独立线程：afterWorking 抛异常（已忽略，不影响出料）：{}",
                    e.toString());
        }
        try {
            recipe.postWorking(machine);
        } catch (RuntimeException e) {
            NanochipModContext.logger().debug("[gtlmodular] NAC 独立线程：postWorking 抛异常（已忽略，不影响出料）：{}",
                    e.toString());
        }
        batchRuntime.remove(recipe);
        return true;
    }
}
