package com.gtlmodular.common.machine.nanochip;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gtlmodular.common.data.nanochip.NanochipModContext;

import static com.gtlmodular.common.machine.nanochip.NanochipBatchAdmission.NO_PROBE;
import static com.gtlmodular.common.machine.nanochip.NanochipThreadedRecipeLogic.FAN_OUT_MAX_PER_TICK;
import static com.gtlmodular.common.machine.nanochip.NanochipThreadedRecipeLogic.FILL_RETRY_THROTTLE;
import static com.gtlmodular.common.machine.nanochip.NanochipThreadedRecipeLogic.FILL_RETRY_INTERVAL;
import static com.gtlmodular.common.machine.nanochip.NanochipThreadedRecipeLogic.IDLE_SEARCH_THROTTLE;
import static com.gtlmodular.common.machine.nanochip.NanochipThreadedRecipeLogic.IDLE_SEARCH_INTERVAL;

final class NanochipThreadDiagnostics {

    private final NanochipThreadedRecipeLogic logic;
    private final NanochipThreadHost host;
    private final NanochipConcurrentBatches batches;
    private final NanochipThreadInputs inputs;
    private final NanochipBatchAdmission admission;
    private final NanochipThreadCandidates candidates;
    private final NanochipThreadDisplay display;

    private static final int DIAG_INTERVAL = 100;

    private static final int CANDIDATE_DIAG_INTERVAL = 100;

    private static final int CANDIDATE_DIAG_BUDGET = 12;

    private int diagCooldown;

    private int diagLastRunning = -1;

    private boolean diagnosedUnavailable;

    private int candidateDiagCooldown;

    private int candidateDiagBudget = CANDIDATE_DIAG_BUDGET;

    // 日志可因到料恢复，但不能因此增加旧细读对匹配器的调用次数。
    private int candidateProbeBudget = CANDIDATE_DIAG_BUDGET;

    private String candidateDiagLast;

    /** 累计开批数用于区分从未开工与开工后被清空两类故障。 */
    private long batchesOpenedTotal;

    private long selfCostMicros;

    private long selfCostPeakMicros;

    NanochipThreadDiagnostics(NanochipThreadedRecipeLogic logic, NanochipThreadHost host,
                             NanochipConcurrentBatches batches, NanochipThreadInputs inputs,
                             NanochipBatchAdmission admission, NanochipThreadCandidates candidates,
                             NanochipThreadDisplay display) {
        this.logic = logic;
        this.host = host;
        this.batches = batches;
        this.inputs = inputs;
        this.admission = admission;
        this.candidates = candidates;
        this.display = display;
    }

    void available() {
        diagnosedUnavailable = false;
    }

    boolean canProbeCandidates() {
        return candidateProbeBudget > 0;
    }

    void batchOpened(GTRecipe recipe) {
        batchesOpenedTotal++;
        logic.stallDiagnostics().opened();
        logic.stallDiagnostics().candidate(recipe, "已真实扣料开批");
    }

    void runningWait(GTRecipe recipe, String reason) {
        logic.stallDiagnostics().runningWait(recipe, reason);
    }

    void resetRunningCount() {
        diagLastRunning = -1;
    }

    void inputChanged() {
        candidateDiagBudget = CANDIDATE_DIAG_BUDGET;
        candidateDiagLast = null;
    }

    void resetCandidates() {
        candidateDiagCooldown = 0;
        candidateDiagLast = null;
    }

    void recordReset(int lost) {
        if (lost > 0) {
            // 记录实际丢弃的批次，便于区分从未开工与被重置清空。
            NanochipModContext.logger().info("[gtlmodular] NAC 独立线程：配方逻辑被重置 ⇒ 丢弃 {} 条在跑批次"
                            + "（已扣的料不退回，与基类同口径）；累计开过 {} 条。",
                    lost, batchesOpenedTotal);
        }
    }

    void recordSelfCost(long startNanos, boolean working, boolean lostBatch) {
        long micros = Math.max(0L, (System.nanoTime() - startNanos) / 1000L);
        selfCostMicros = micros;
        if (micros > selfCostPeakMicros) {
            selfCostPeakMicros = micros;
        }
        candidates.fillOpenedNothingLastTick = working && !batches.isEmpty() && candidates.fanOutOpenedThisTick == 0;
        candidates.fillLostBatchLastTick = lostBatch;
    }

    private String fillThrottleText() {
        if (!FILL_RETRY_THROTTLE) {
            return "关闭（每 tick 都完整试一遍补料）";
        }
        return (candidates.fillOpenedNothingLastTick ? "生效" : "待命（上一 tick 开出过 / 有批次跑完 / 不在稳态）")
                + "（每 " + Math.max(1, FILL_RETRY_INTERVAL) + " tick 至少真试一次；累计跳过 "
                + candidates.fillRetrySkippedTicks + " tick）";
    }

    private String selfCostText() {
        return "本类工作段自耗=" + selfCostMicros + " µs（最近）/ " + selfCostPeakMicros + " µs（峰值）";
    }

    void diagnoseUnavailableOnce() {
        if (diagnosedUnavailable) {
            return;
        }
        diagnosedUnavailable = true;
        NanochipModContext.logger().info("[gtlmodular] NAC 独立线程逻辑：零件表当前不可用（未成形 / 读档未就绪），"
                + "本 tick 不推进也不清理批次表（在跑 {} 条）—— 若持续出现，看结构成形状态。",
                batches.size());
    }

    void diagnoseBatches() {
        if (diagCooldown > 0) {
            diagCooldown--;
            return;
        }
        int running = batches.size();
        if (running == diagLastRunning) {
            return;
        }
        diagLastRunning = running;
        diagCooldown = DIAG_INTERVAL;
        NanochipModContext.logger().info("[gtlmodular] NAC 独立线程：在用 {}/{}（上限来源=机器声明的跨配方线程数"
                        + " NanochipThreadHost.nanochipThreadCount() → 本机 getMultipleThreads()）；样本 [{}]；"
                        + "上次开出 [{}]；输入面 [{}]；首个被拒 [{}]；补料重试 [{}]；{}；空闲节流 [{}]；候选类型 [{}]",
                running, logic.getThreadLimit(), sampleText(), lastOpenText(), inputs.inputFaceText(),
                admission.firstReject == null ? "（本 tick 没被拒过）" : admission.firstReject,
                fillThrottleText(), selfCostText(), idleThrottleText(), typesText());
    }

    private String lastOpenText() {
        if (admission.lastOpenRecipeId == null) {
            return "（本机还没开出过批次）";
        }
        return admission.lastOpenRecipeId
                + "；并行=" + admission.lastOpenParallel
                + "；输入侧上限=" + admission.lastOpenInputMax
                + "；搜索上界=" + admission.lastOpenBound
                + "；来源=" + (admission.lastOpenBranch == null ? "?" : admission.lastOpenBranch);
    }

    private String idleThrottleText() {
        if (!IDLE_SEARCH_THROTTLE) {
            return "关闭（每 tick 都全量搜）";
        }
        String fanOut = logic.fanOutSameRecipe()
                ? "fan-out 每 tick 最多新开 " + FAN_OUT_MAX_PER_TICK + " 条"
                : "fan-out 已关：每种配方最多占 1 条批次";
        return (candidates.wokenByInputChange ? "本 tick 被唤醒⇒立即全量搜" : "生效")
                + "（每 " + Math.max(1, IDLE_SEARCH_INTERVAL) + " tick 至少全量搜一次；累计跳过 "
                + candidates.idleSkippedTicks + " tick；" + fanOut + "）";
    }

    void diagnoseCandidates() {
        if (candidateDiagBudget <= 0) {
            return;
        }
        if (candidateDiagCooldown > 0) {
            candidateDiagCooldown--;
            return;
        }
        if (!batches.isEmpty()) {
            candidateDiagCooldown = CANDIDATE_DIAG_INTERVAL;
            return;
        }
        // 即使文案重复也要先设置冷却，避免每 tick 遍历接线与缓存做同样的诊断。
        candidateDiagCooldown = CANDIDATE_DIAG_INTERVAL;
        String line = "原始枚举=" + candidates.candidateRawCount + " 条；通过资格闸=" + candidates.candidateUsableCount
                + " 条；开出批次=0；累计开过=" + batchesOpenedTotal + " 条；上限=" + logic.getThreadLimit()
                + "；输入侧[" + inputs.inputSideText() + "]"
                + (candidates.candidateSearchFailure == null ? "" : "；搜索失败[" + candidates.candidateSearchFailure + "]")
                + (admission.firstReject == null ? "；首个被拒=（本 tick 没走到候选）" : "；首个被拒[" + admission.firstReject + "]")
                + (admission.fitSampleRecipeId == null ? "" : "；几何细读[" + fitDetailText() + "]");
        if (line.equals(candidateDiagLast)) {
            return;
        }
        candidateDiagLast = line;
        candidateDiagCooldown = CANDIDATE_DIAG_INTERVAL;
        candidateDiagBudget--;
        if (candidateProbeBudget > 0) candidateProbeBudget--;
        NanochipModContext.logger().info("[gtlmodular] NAC 独立线程候选诊断 @{} {}：{}（候选类型 [{}]；本行限频 "
                + CANDIDATE_DIAG_INTERVAL + " tick、每次物料变化后至多 " + CANDIDATE_DIAG_BUDGET + " 行）",
                logic.machine().self().getLevel().dimension().location(), logic.machine().self().getPos(),
                line, typesText());
    }

    private String fitDetailText() {
        StringBuilder sb = new StringBuilder(160);
        sb.append("配方=").append(admission.fitSampleRecipeId);
        sb.append("；输入侧上限=").append(admission.fitSampleInputMax);
        sb.append("；上游首槽上限=").append(admission.fitUpstreamInputMax)
                .append("；按槽上限=").append(admission.fitSlotInputMax)
                .append("（开关=").append(logic.perSlotParallelBound() ? "开" : "关").append("）");
        sb.append("（越界=").append(admission.fitSampleInputMaxOverBound ? "是" : "否")
                .append("，安全上界=").append(NanochipParallelFit.MAX_PARALLEL_SEARCH_BOUND).append("）");
        sb.append("；二分=").append(admission.fitSampleMatchable);
        if (admission.fitSampleProbeParallel != NO_PROBE) {
            sb.append("；谓词首次判假@").append(admission.fitSampleProbeParallel)
                    .append("/侧=").append(admission.fitSampleProbeSide == null ? "?" : admission.fitSampleProbeSide);
        } else if (admission.fitSampleProbeSide != null) {
            sb.append("；谓词没判假（说明没走到二分）/说明=").append(admission.fitSampleProbeSide);
        }
        sb.append("；整条电量=").append(admission.fitSampleWholeEu);
        sb.append("；已存=").append(admission.fitSampleStoredEu);
        sb.append("；按需现取=").append(admission.fitSampleUnlimitedEnergy ? "是" : "否");
        if (admission.fitSampleProbed) {
            sb.append("；输入项数=").append(admission.fitSampleInputContents);
            sb.append("；输入匹配=").append(admission.fitSampleInputMatch ? "是" : "否");
            sb.append("；产出匹配=").append(admission.fitSampleOutputMatch ? "是" : "否");
        }
        return sb.toString();
    }

    private String sampleText() {
        StringBuilder sb = new StringBuilder();
        int shown = 0;
        for (int i = 0, n = batches.size(); i < n; i++) {
            NanochipConcurrentBatches.Batch batch = batches.get(i);
            if (shown >= 6) {
                break;
            }
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append('#').append(display.threadLabelOf(batch.recipe)).append(' ')
                    .append(batch.recipe.getId() == null ? "?" : batch.recipe.getId())
                    .append(" ×").append(Math.max(1L, NanochipThreadDisplay.realParallelsOf(batch.recipe)))
                    .append(' ').append(Math.round(batch.percent() * 100.0F)).append('%');
            shown++;
        }
        return sb.toString();
    }

    private String typesText() {
        if (host == null) {
            return "（无宿主）";
        }
        try {
            return NanochipCandidateSearch.describeTypes(host.nanochipSearchTypes());
        } catch (RuntimeException e) {
            return "（读取失败）";
        }
    }
}
