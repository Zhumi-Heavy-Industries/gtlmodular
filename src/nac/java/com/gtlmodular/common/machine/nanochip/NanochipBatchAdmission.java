package com.gtlmodular.common.machine.nanochip;

import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.utils.FormattingUtil;
import com.gtlmodular.common.data.nanochip.NanochipModContext;
import net.minecraft.network.chat.Component;
import org.gtlcore.gtlcore.api.recipe.IGTRecipe;
import org.gtlcore.gtlcore.api.recipe.IParallelLogic;
import org.gtlcore.gtlcore.api.recipe.RecipeRunnerHelper;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.gtlmodular.common.machine.nanochip.NanochipThreadInputs.NO_SLOT_BOUND;
import static com.gtlmodular.common.machine.nanochip.NanochipThreadedRecipeLogic.PARALLEL_LIMIT_BOUNDED;

final class NanochipBatchAdmission {

    private final NanochipThreadedRecipeLogic logic;
    private final IRecipeLogicMachine machine;
    private final NanochipThreadInputs inputs;
    private final Function<GTRecipe, GTRecipe> modifyRecipe;
    private final Function<GTRecipe, GTRecipe> overlayWithReservations;

    Component waitReason;

    boolean admissionWaiting;

    String firstReject;

    private String fitFailure;

    String fitSampleRecipeId;

    long fitSampleInputMax;

    boolean fitSampleInputMaxOverBound;

    long fitSampleMatchable;

    long fitSampleProbeParallel = NO_PROBE;

    String fitSampleProbeSide;

    long fitSampleWholeEu;

    long fitSampleStoredEu;

    boolean fitSampleUnlimitedEnergy;

    boolean fitSampleArmed;

    /** 单看并行结果为零无法区分输入不可见与放大失败，需要保留原配方的匹配读数。 */
    boolean fitSampleProbed;

    int fitSampleInputContents;

    boolean fitSampleInputMatch;

    boolean fitSampleOutputMatch;

    String lastOpenRecipeId;

    long lastOpenInputMax = -1L;

    long lastOpenBound = -1L;

    long lastOpenParallel;

    String lastOpenBranch;

    private long fitInputMax = -1L;

    long fitUpstreamInputMax = NO_SLOT_BOUND;

    long fitSlotInputMax = NO_SLOT_BOUND;

    private long fitBound = -1L;

    private long fitParallelValue;

    private String fitBranch;

    /** 不能用 -1 表示未探测，因为它也可能是溢出后的观测值。 */
    static final long NO_PROBE = Long.MIN_VALUE;

    NanochipBatchAdmission(NanochipThreadedRecipeLogic logic, IRecipeLogicMachine machine, NanochipThreadInputs inputs,
                           Function<GTRecipe, GTRecipe> modifyRecipe,
                           Function<GTRecipe, GTRecipe> overlayWithReservations) {
        this.logic = logic;
        this.machine = machine;
        this.inputs = inputs;
        this.modifyRecipe = modifyRecipe;
        this.overlayWithReservations = overlayWithReservations;
    }

    GTRecipe prepareBatch(GTRecipe origin) {
        fitFailure = null;
        GTRecipe modified;
        try {
            modified = modifyRecipe.apply(origin.copy());
        } catch (RuntimeException e) {
            noteReject(origin, "配方修改器抛异常",
                    e.getClass().getSimpleName() + ": " + e.getMessage());
            NanochipModContext.logger().debug("[gtlmodular] NAC 独立线程：配方修改器抛异常，跳过该候选：{}", e.toString());
            return null;
        }
        if (modified == null) {
            noteReject(origin, "配方修改器判 null（无能源仓 / 超频被封 / 修改器拒绝）", null);
            return null;
        }
        long parallel = fitParallel(modified);
        if (parallel <= 0L) {
            noteReject(origin, "并行二分=0（这一刻连 1 份都放不下，或输入侧上限 ≤ 0）",
                    "输入上限=" + fitInputMax + "，搜索上界=" + fitBound + "，末次失败=" + fitFailure);
            return null;
        }
        GTRecipe scaled = ensureRuns(NanochipParallelFit.scalePrecisely(modified, parallel), parallel);
        if (scaled == null) {
            noteReject(origin, "精确缩放失败", null);
            return null;
        }
        if (!conditionsOk(scaled, false)) {
            noteReject(origin, "配方自带条件不满足（维度 / 清洁室 / 研究…）", null);
            return null;
        }
        if (!hostPrecharged(scaled)) {
            noteReject(origin, "主机预蓄电闸拦住（整条配方电量 > 已存 + 20t 可补）", null);
            return null;
        }
        if (!canAdmit(scaled)) {
            noteReject(origin, "准入闸拦住（本条 + 在跑预留 叠起来放不下）", fitFailure);
            return null;
        }
        lastOpenRecipeId = idOf(origin);
        lastOpenInputMax = fitInputMax;
        lastOpenBound = fitBound;
        lastOpenParallel = parallel;
        lastOpenBranch = fitBranch;
        return scaled;
    }

    private long fitParallel(GTRecipe recipe) {
        inputs.observeDiagnosticRecipe(recipe);
        long limitArgument = PARALLEL_LIMIT_BOUNDED
                ? NanochipParallelFit.MAX_PARALLEL_SEARCH_BOUND
                : Long.MAX_VALUE;
        long upstreamInputMax;
        try {
            upstreamInputMax = IParallelLogic.getMaxParallel(machine, recipe, limitArgument);
        } catch (RuntimeException e) {
            NanochipModContext.logger().debug("[gtlmodular] NAC 独立线程：并行上限读取失败，跳过该候选：{}", e.toString());
            fitInputMax = -1L;
            fitUpstreamInputMax = -1L;
            fitSlotInputMax = NO_SLOT_BOUND;
            fitBound = -1L;
            fitParallelValue = 0L;
            fitBranch = "输入侧上限读取抛异常";
            fitSampleInputMax = -1L;
            fitSampleInputMaxOverBound = false;
            fitSampleMatchable = 0L;
            fitSampleProbeSide = "输入侧上限读取抛异常";
            fitFailure = fitSampleProbeSide;
            recordProbe(0L);
            return 0L;
        }
        fitUpstreamInputMax = upstreamInputMax;
        fitSlotInputMax = logic.perSlotParallelBound()
                ? inputs.perSlotInputBound(recipe)
                : NO_SLOT_BOUND;
        long inputMax = fitSlotInputMax > upstreamInputMax ? fitSlotInputMax : upstreamInputMax;
        if (inputMax <= 0L) {
            fitInputMax = inputMax;
            fitBound = 0L;
            fitParallelValue = 0L;
            fitBranch = "输入侧上限 ≤ 0（这一刻连 1 份输入都凑不齐 / 电压侧判 0）";
            fitSampleInputMax = inputMax;
            fitSampleInputMaxOverBound = false;
            fitSampleMatchable = 0L;
            fitSampleProbeSide = "输入侧上限 ≤ 0（这一刻连 1 份输入都凑不齐 / 电压侧判 0）";
            fitFailure = fitSampleProbeSide;
            recordProbe(0L);
            return 0L;
        }
        fitInputMax = inputMax;
        fitBound = Math.min(inputMax, NanochipParallelFit.MAX_PARALLEL_SEARCH_BOUND);
        // 两种输入估算都须受直通上限约束；全流程不能被主机的 int 返回值截断。
        if (machine instanceof NanochipAssemblyComplexMachine host && !host.fullMode()) {
            fitBound = Math.min(fitBound, host.getMaxParallel());
        }
        fitSampleInputMax = inputMax;
        fitSampleInputMaxOverBound = inputMax > NanochipParallelFit.MAX_PARALLEL_SEARCH_BOUND;
        // 输入侧估算不代表输出也放得下，仍须探测实际可容纳的并行数。
        long matchable = NanochipParallelFit.findHighestMatchableInSafeRange(fitBound,
                candidate -> candidate > 0L && fitsParallel(recipe, candidate));
        fitParallelValue = matchable;
        fitBranch = parallelBranchText(matchable, fitBound);
        fitSampleMatchable = matchable;
        return matchable;
    }

    private static String parallelBranchText(long matchable, long bound) {
        if (matchable <= 0L) {
            return "二分=0（这一刻连 1 份都放不下）";
        }
        if (matchable >= bound) {
            return "第一刀命中（= 搜索上界，未经过二分）";
        }
        if (bound - matchable == 1L) {
            return "第二刀命中（上界-1；上界那一档真的放不下）";
        }
        return "二分收敛（谓词判真的最大档）";
    }

    private void recordProbe(long parallel) {
        if (fitSampleProbeParallel != NO_PROBE) {
            return;
        }
        fitSampleProbeParallel = parallel;
    }

    private boolean fitsParallel(GTRecipe recipe, long parallel) {
        GTRecipe candidate;
        try {
            candidate = NanochipParallelFit.scalePrecisely(recipe, parallel);
        } catch (RuntimeException e) {
            fitSampleProbeSide = "精确缩放抛异常: " + e.getClass().getSimpleName();
            fitFailure = fitSampleProbeSide;
            recordProbe(parallel);
            return false;
        }
        if (candidate == null) {
            fitSampleProbeSide = "精确缩放判 null";
            fitFailure = fitSampleProbeSide;
            recordProbe(parallel);
            return false;
        }
        GTRecipe probe;
        try {
            probe = overlayWithReservations.apply(candidate);
        } catch (RuntimeException e) {
            fitSampleProbeSide = "预留叠加抛异常: " + e.getClass().getSimpleName();
            fitFailure = fitSampleProbeSide;
            recordProbe(parallel);
            return false;
        }
        if (probe == null) {
            fitSampleProbeSide = "预留叠加判 null";
            fitFailure = fitSampleProbeSide;
            recordProbe(parallel);
            return false;
        }
        boolean inputFits;
        boolean outputFits;
        try {
            inputFits = RecipeRunnerHelper.matchRecipeInput(machine, probe);
            outputFits = inputFits && RecipeRunnerHelper.matchRecipeOutput(machine, probe);
        } catch (RuntimeException e) {
            fitSampleProbeSide = "容量匹配抛异常: " + e.getClass().getSimpleName();
            fitFailure = fitSampleProbeSide;
            recordProbe(parallel);
            return false;
        }
        if (inputFits && outputFits) {
            return true;
        }
        if (inputFits) {
            fitSampleProbeSide = "产出侧放不下（matchRecipeOutput=false）";
            fitFailure = fitSampleProbeSide;
        } else {
            fitSampleProbeSide = "输入侧放不下（matchRecipeInput=false）";
            fitFailure = fitSampleProbeSide;
        }
        recordProbe(parallel);
        return false;
    }

    private boolean canAdmit(GTRecipe scaled) {
        GTRecipe probe = overlayWithReservations.apply(scaled);
        if (probe == null) {
            fitFailure = "准入预留叠加判 null";
            return false;
        }
        if (!RecipeRunnerHelper.matchRecipeInput(machine, probe)) {
            fitFailure = "准入输入匹配失败";
            return false;
        }
        boolean outputFits = RecipeRunnerHelper.matchRecipeOutput(machine, probe);
        if (!outputFits) fitFailure = "准入输出空间不足";
        return outputFits;
    }

    /** 概率结算按运行份数掷骰，必须保证 realParallels 至少为一。 */
    private static GTRecipe ensureRuns(GTRecipe scaled, long parallel) {
        if (scaled == null) {
            return null;
        }
        try {
            IGTRecipe extension = IGTRecipe.of(scaled);
            if (extension.getRealParallels() <= 0L) {
                extension.setRealParallels(Math.max(1L, parallel));
            }
        } catch (RuntimeException e) {
            NanochipModContext.logger().debug("[gtlmodular] NAC 独立线程：运行份数补写失败（不影响开批次）：{}", e.toString());
        }
        return scaled;
    }

    boolean conditionsOk(GTRecipe recipe, boolean recordReason) {
        GTRecipe.ActionResult result;
        try {
            result = recipe.checkConditions(logic);
        } catch (RuntimeException e) {
            NanochipModContext.logger().debug("[gtlmodular] NAC 独立线程：条件检查抛异常（按不满足处理）：{}", e.toString());
            return false;
        }
        if (result != null && result.isSuccess()) {
            return true;
        }
        if (recordReason && waitReason == null) {
            waitReason = reasonOf(result);
        }
        return false;
    }

    /** 在扣料前沿用主机预蓄电检查；按需电源跳过整批缓存要求，运行时仍需真实支付。 */
    private boolean hostPrecharged(GTRecipe scaled) {
        if (!NanochipAssemblyComplexMachine.NAC_PRECHARGE_WHOLE_RECIPE || scaled == null) {
            return true;
        }
        if (!(machine instanceof NanochipAssemblyComplexMachine nac)) {
            return true;
        }
        long need = plannedWholeRecipeEu(scaled);
        if (need <= 0L) {
            return true;
        }
        if (nac.isEnergySourceEffectivelyUnlimited()) {
            return true;
        }
        long stored = Math.max(0L, nac.getObtainableEnergyEU());
        long supply = saturatingMul(Math.max(0L, nac.getMaxInputPower()),
                NanochipAssemblyComplexMachine.ENERGY_DISTRIBUTE_INTERVAL);
        long available = stored > Long.MAX_VALUE - supply ? Long.MAX_VALUE : stored + supply;
        if (available >= need) {
            return true;
        }
        if (logic.isSuspend()) {
            return true;
        }
        admissionWaiting = true;
        waitReason = Component.translatable("gtlmodular.nanochip.host.buffer",
                FormattingUtil.formatNumbers(need), FormattingUtil.formatNumbers(stored));
        nac.noteHostPrechargeWaiting(need, stored);
        return false;
    }

    private static long plannedWholeRecipeEu(GTRecipe recipe) {
        if (recipe == null) {
            return 0L;
        }
        long perTick = Math.max(1L, RecipeHelper.getInputEUt(recipe));
        int duration = Math.max(1, recipe.duration);
        return saturatingMul(perTick, (long) duration);
    }

    private static long saturatingMul(long a, long b) {
        if (a <= 0L || b <= 0L) {
            return 0L;
        }
        return a > Long.MAX_VALUE / b ? Long.MAX_VALUE : a * b;
    }

    void noteReject(GTRecipe candidate, String gate, String detail) {
        logic.stallDiagnostics().candidate(candidate, gate + (detail == null ? "" : "（" + detail + "）"));
        if (firstReject != null) {
            return;
        }
        firstReject = (candidate == null || candidate.getId() == null ? "?" : candidate.getId().toString())
                + " → " + gate + (detail == null ? "" : "（" + detail + "）");
    }

    void inspectFitFailure(GTRecipe candidate) {
        if (candidate == null) {
            return;
        }
        try {
            GTRecipe modified = modifyRecipe.apply(candidate.copy());
            if (modified == null) {
                fitSampleRecipeId = idOf(candidate);
                fitSampleProbeSide = "配方修改器判 null（no energy hatch / 超频被封 / 修改器拒绝）";
                fitFailure = fitSampleProbeSide;
                return;
            }
            sampleInputMatch(modified);
            long parallel = fitParallel(modified);
            fitSampleMatchable = parallel;
            fitSampleRecipeId = idOf(candidate);
            if (parallel > 0L) {
                GTRecipe scaled = ensureRuns(NanochipParallelFit.scalePrecisely(modified, parallel), parallel);
                fitSampleWholeEu = plannedWholeRecipeEu(scaled);
            } else {
                fitSampleWholeEu = plannedWholeRecipeEu(modified);
            }
            sampleEnergyReading();
        } catch (RuntimeException e) {
            fitSampleRecipeId = idOf(candidate);
            fitSampleProbeSide = "抽样抛异常: " + e.getClass().getSimpleName()
                    + (e.getMessage() == null ? "" : ": " + e.getMessage());
        }
    }

    private void sampleInputMatch(GTRecipe recipe) {
        fitSampleProbed = true;
        fitSampleInputContents = countContents(recipe.inputs) + countContents(recipe.tickInputs);
        try {
            fitSampleInputMatch = RecipeRunnerHelper.matchRecipeInput(machine, recipe);
            fitSampleOutputMatch = RecipeRunnerHelper.matchRecipeOutput(machine, recipe);
        } catch (RuntimeException e) {
            NanochipModContext.logger().debug("[gtlmodular] NAC 独立线程：原样配方匹配抽样失败（诊断用，忽略）：{}",
                    e.toString());
        }
    }

    private static int countContents(Map<RecipeCapability<?>, List<Content>> contents) {
        if (contents == null || contents.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (List<Content> list : contents.values()) {
            if (list != null) {
                total += list.size();
            }
        }
        return total;
    }

    private static String idOf(GTRecipe recipe) {
        if (recipe == null || recipe.getId() == null) {
            return "?";
        }
        return recipe.getId().toString();
    }

    private void sampleEnergyReading() {
        if (!(machine instanceof NanochipAssemblyComplexMachine nac)) {
            return;
        }
        fitSampleUnlimitedEnergy = nac.isEnergySourceEffectivelyUnlimited();
        try {
            fitSampleStoredEu = Math.max(0L, nac.getObtainableEnergyEU());
        } catch (RuntimeException e) {
            NanochipModContext.logger().debug("[gtlmodular] NAC 独立线程：抽样读取可用电量失败（诊断用，忽略）：{}", e.toString());
        }
    }

    static Component reasonOf(GTRecipe.ActionResult result) {
        if (result == null) {
            return null;
        }
        Supplier<Component> supplier = result.reason();
        return supplier == null ? null : supplier.get();
    }
}
