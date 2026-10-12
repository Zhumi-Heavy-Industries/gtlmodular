package com.gtlmodular.common.machine.nanochip;

import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gtlmodular.common.data.nanochip.NanochipModContext;
import com.gtlmodular.common.machine.display.IndependentThreadDisplay;
import com.gtlmodular.common.machine.display.ThreadDisplaySnapshot;
import com.gtlmodular.common.machine.nanochip.display.NanochipInputSnapshot;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static com.gtlmodular.common.machine.nanochip.NanochipBatchAdmission.NO_PROBE;

/** 通过 NanochipConcurrentBatches 复用批次状态机，在本层接入 NAC 候选、供电和结算策略。 */
public final class NanochipThreadedRecipeLogic extends com.gtladd.gtladditions.api.machine.logic.GTLAddMultipleTypeRecipesLogic implements NanochipConcurrentBatches.Host, IndependentThreadDisplay {

    public static final String TAG_THREADS = NanochipConcurrentBatches.TAG_BATCHES;

    /** 保留默认常量供配置定义与外部引用；运行分支使用服务端每轮读取的选项。 */
    public static final boolean FAN_OUT_SAME_RECIPE = false;

    /** 开批需要修改配方、探测并行和扣料，限制每 tick 新开数量以控制服务端开销。 */
    public static final int FAN_OUT_MAX_PER_TICK = 8;

    /** 默认对稳态且补料失败的搜索限频；输入变化或批次结束后立即重试。 */
    public static final boolean FILL_RETRY_THROTTLE = true;

    public static final int FILL_RETRY_INTERVAL = 10;

    /** 默认不预留非 tick 输入：它们在开批时已扣除，再叠加会重复占用物料额度。 */
    public static final boolean RESERVE_NON_TICK_INPUTS = false;

    /** 默认让上游估算与最终匹配共用同一并行上界，避免两处搜索范围分歧。 */
    public static final boolean PARALLEL_LIMIT_BOUNDED = true;

    /** 默认沿用上游估算，避免未选择实验选项时改变并行搜索。 */
    public static final boolean PER_SLOT_PARALLEL_BOUND = false;

    /** 默认限制空闲搜索频率，避免无料时反复枚举所有配方类型；输入变化会解除冷却。 */
    public static final boolean IDLE_SEARCH_THROTTLE = true;

    public static final int IDLE_SEARCH_INTERVAL = 10;

    private static final int WAIT_REASON_INTERVAL = 10;

    /** 默认只在接线缺失时尝试重建，并在连续无效后停止，避免反复重建没有收益。 */
    public static boolean INPUT_WIRING_REBUILD_GATED = true;

    public static final String LANG_THREADS = "gtlmodular.nanochip.threads.status";

    public static final String LANG_THREADS_RUNS = "gtlmodular.nanochip.threads.runs";

    public static final String LANG_THREADS_LINE = "gtlmodular.nanochip.threads.line";

    public static final String LANG_THREADS_MORE = "gtlmodular.nanochip.threads.more";

    /** 面板文本需同步到客户端，限制明细数量以控制数据包大小。 */
    public static final int DISPLAY_LINES = 8;

    private final NanochipThreadHost host;

    private final NanochipConcurrentBatches batches = new NanochipConcurrentBatches();

    private final NanochipBatchRuntime batchRuntime = new NanochipBatchRuntime(machine, this::makeChanceCaches);

    private final NanochipThreadInputs inputs = new NanochipThreadInputs(machine, batches);

    // 父类构造期间机器还未初始化，配方修改与预留读取必须延迟到实际准入。
    private final NanochipBatchAdmission admission = new NanochipBatchAdmission(this, machine, inputs,
            recipe -> machine.fullModifyRecipe(recipe, ocParams, ocResult), this::overlayWithReservations);

    private final NanochipThreadCandidates candidates;

    private final NanochipThreadDisplay display;

    private final NanochipThreadDiagnostics diagnostics;

    private final NanochipBatchLifecycle lifecycle;

    private final NanochipStallDiagnostics stallDiagnostics;

    private int waitReasonCooldown;

    private record SchedulingOptions(boolean fanOutSameRecipe, boolean perSlotParallelBound, long tick) {}

    private SchedulingOptions schedulingOptions = new SchedulingOptions(FAN_OUT_SAME_RECIPE,
            PER_SLOT_PARALLEL_BOUND, -1L);

    boolean fanOutSameRecipe() {
        return schedulingOptions.fanOutSameRecipe();
    }

    boolean perSlotParallelBound() {
        return schedulingOptions.perSlotParallelBound();
    }

    public NanochipInputSnapshot inputDiagnosticSnapshot() {
        return new NanochipInputSnapshot(
                schedulingOptions.tick(), fanOutSameRecipe(), perSlotParallelBound(), inputs.diagnosticSample());
    }

    // 保留嵌套类型的全限定名；附注的生命周期由独立组件管理。
    static final class BatchState {
        Map<RecipeCapability<?>, Object2IntMap<?>> caches;
        GTRecipe reservation;
        long seq;
    }

    public NanochipThreadedRecipeLogic(NanochipAssemblyComplexMachine candidateHost) {
        super(candidateHost, machine -> true);
        // 逻辑在机器父类构造时创建，此时机器自身字段尚未初始化，不能读取其状态。
        NanochipThreadHost resolved;
        try {
            resolved = candidateHost;
        } catch (RuntimeException e) {
            NanochipModContext.logger().debug("[gtlmodular] NAC 独立线程：机器未实现 NanochipThreadHost ⇒ 不派活：{}",
                    e.toString());
            resolved = null;
        }
        this.host = resolved;
        this.stallDiagnostics = new NanochipStallDiagnostics(this, candidateHost);
        this.candidates = new NanochipThreadCandidates(this, machine, host, batches, admission);
        this.display = new NanochipThreadDisplay(this, machine, batches, batchRuntime, ThreadRec::new);
        this.diagnostics = new NanochipThreadDiagnostics(this, host, batches, inputs, admission, candidates, display);
        this.lifecycle = new NanochipBatchLifecycle(machine, admission, batchRuntime, diagnostics);
    }

    @Override
    public void onMachineLoad() {
        super.onMachineLoad();
        stallDiagnostics.load();
    }

    @Override
    public void onMachineUnLoad() {
        stallDiagnostics.unload();
        super.onMachineUnLoad();
    }

    NanochipStallDiagnostics stallDiagnostics() {
        return stallDiagnostics;
    }

    boolean recipeTickSubscribed() {
        return subscription != null && subscription.isStillSubscribed();
    }

    void diagnosticsInputChanged() {
        diagnostics.inputChanged();
    }

    public int getThreadLimit() {
        if (host == null) {
            return 1;
        }
        return Math.max(1, host.nanochipThreadCount());
    }

    public int runningThreadCount() {
        return batches.size();
    }

    public int distinctRunningRecipeCount() {
        return display.distinctRunningRecipeCount();
    }

    /** Jade 需识别原生进度条对应的批次，避免附加条重复显示同一批次。 */
    public int mirroredThreadLabel() {
        return display.mirroredThreadLabel();
    }

    public long mirroredThreadParallels() {
        return display.mirroredThreadParallels();
    }

    public NanochipConcurrentBatches.Batch batchAt(int index) {
        return batches.get(index);
    }

    @Override
    public ThreadDisplaySnapshot threadDisplaySnapshot() {
        return display.displaySnapshot();
    }

    public List<Map.Entry<Integer, ThreadRec>> snapshot() {
        return display.snapshot();
    }

    /** 展示层读取现有批次的视图，避免维护第二份进度状态。 */
    public static final class ThreadRec {

        private final NanochipConcurrentBatches.Batch batch;

        private ThreadRec(NanochipConcurrentBatches.Batch batch) {
            this.batch = batch;
        }

        public GTRecipe recipe() {
            return batch.recipe;
        }

        public int progress() {
            return batch.progress;
        }

        public int duration() {
            return batch.duration;
        }

        /** 显示使用 realParallels，与整机合计口径一致，避免只读 int 并行字段而截断。 */
        public long parallels() {
            return Math.max(1L, realParallelsOf(batch.recipe));
        }
    }

    public static String firstItemOutputId(GTRecipe recipe) {
        return NanochipThreadDisplay.firstItemOutputId(recipe);
    }

    /** 产出内容已含批次并行，显示数量不能再乘一次并行数。 */
    public static long firstItemOutputAmount(GTRecipe recipe) {
        return NanochipThreadDisplay.firstItemOutputAmount(recipe);
    }

    public long totalRunsAtOnce() {
        return display.totalRunsAtOnce();
    }

    @Override
    public void serverTick() {
        stallDiagnostics.logicTick();
        var config = NanochipModContext.config();
        // 同一轮候选、准入和日志必须使用一致的选项，不能在候选之间重新读取配置。
        schedulingOptions = new SchedulingOptions(config.isFanOutSameRecipe(), config.isPerSlotParallelBound(),
                machine.self().getLevel().getGameTime());
        display.beginTick();
        if (isSuspend()) {
            stallDiagnostics.scheduling("工作已暂停");
            return;   // 暂停只停止推进，保留已经扣料的批次，避免丢失投入。
        }
        if (machine instanceof NanochipAssemblyComplexMachine nac) {
            nac.nanochipTickSync();
        }
        if (!machine.isRecipeLogicAvailable()) {
            stallDiagnostics.scheduling("配方逻辑不可用/零件未就绪");
            diagnostics.diagnoseUnavailableOnce();
            return;   // 零件暂不可用时保留批次，避免读档过程被当成需要清空任务。
        }
        diagnostics.available();

        candidates.candidatePool = null;
        candidates.candidateCursor = 0;
        candidates.fanOutOpenedThisTick = 0;
        candidates.fanOutScannedSinceSuccess = 0;
        admission.admissionWaiting = false;
        admission.waitReason = null;
        candidates.candidateRawCount = 0;
        candidates.candidateUsableCount = 0;
        candidates.candidateSearchFailure = null;
        admission.firstReject = null;
        admission.fitSampleRecipeId = null;
        admission.fitSampleInputMax = 0L;
        admission.fitSampleInputMaxOverBound = false;
        admission.fitSampleMatchable = 0L;
        admission.fitSampleProbeParallel = NO_PROBE;
        admission.fitSampleProbeSide = null;
        admission.fitSampleWholeEu = 0L;
        admission.fitSampleStoredEu = 0L;
        admission.fitSampleUnlimitedEnergy = false;
        admission.fitSampleArmed = batches.isEmpty() && diagnostics.canProbeCandidates();
        admission.fitSampleProbed = false;
        admission.fitSampleInputContents = 0;
        admission.fitSampleInputMatch = false;
        admission.fitSampleOutputMatch = false;
        inputs.diagHasProxies = -1;
        inputs.diagWireParts = -1;
        inputs.diagWireCached = -1;
        inputs.inputWiringRefreshedThisTick = false;

        // 接线刷新必须先于本轮搜索，才能让候选和并行试算读取同一份输入连接。
        inputs.refreshInputWiringIfStale();

        int batchesBeforeTick = batches.size();
        boolean working = false;
        boolean machineAllowsWork = true;
        if (!batches.isEmpty()) {
            // 机器级否决在批次推进之前处理，防止禁止工作时仍扣电或增加进度。
            try {
                machineAllowsWork = machine.onWorking();
            } catch (RuntimeException e) {
                machineAllowsWork = false;
                NanochipModContext.logger().debug("[gtlmodular] NAC 独立线程：机器级 onWorking 抛异常，本 tick 不推进：{}",
                        e.toString());
            }
        }
        if (!machineAllowsWork) {
            stallDiagnostics.scheduling("机器级 onWorking 否决/异常");
            display.machineBlocked();
        }
        long workStartNanos = System.nanoTime();
        if (machineAllowsWork) {
            working = batches.tick(this);
            if (working) {
                totalContinuousRunningTime++;
            }
        }
        diagnostics.recordSelfCost(workStartNanos, working, batches.size() < batchesBeforeTick);
        inputs.lastSearchRaw = candidates.candidateRawCount;

        // 镜像首条批次到基类字段，供原生进度条与外部显示读取同一份进度。
        mirrorBatchZeroToBaseFields();

        boolean waiting = (!batches.isEmpty() && !working) || admission.admissionWaiting;
        syncStatus(working, waiting);
        diagnostics.diagnoseBatches();
        diagnostics.diagnoseCandidates();
        candidates.wokenByInputChange = false;
    }

    /** 输入变化解除空闲和补料冷却，避免已有新物料仍等待整个节流窗口。 */
    @Override
    public void updateTickSubscription() {
        candidates.wokenByInputChange = true;
        candidates.idleSearchCooldown = 0;
        candidates.fillRetryCooldown = 0;
        super.updateTickSubscription();
    }

    static final class InputNeed<T> {

        final Predicate<T> test;
        final long perCraft;

        InputNeed(Predicate<T> test, long perCraft) {
            this.test = test;
            this.perCraft = perCraft;
        }
    }

    @Override
    public void resetRecipeLogic() {
        int lost = batches.size();
        super.resetRecipeLogic();
        batches.clear();
        inputs.clearDiagnostic();
        display.beginTick();
        batchRuntime.clear();
        candidates.candidatePool = null;
        candidates.candidateCursor = 0;
        admission.admissionWaiting = false;
        admission.waitReason = null;
        waitReasonCooldown = 0;
        diagnostics.resetRunningCount();
        candidates.candidateRawCount = 0;
        candidates.candidateUsableCount = 0;
        candidates.candidateSearchFailure = null;
        admission.firstReject = null;
        diagnostics.resetCandidates();
        candidates.fanOutOpenedThisTick = 0;
        candidates.fanOutScannedSinceSuccess = 0;
        candidates.idleSearchCooldown = 0;
        candidates.fillRetryCooldown = 0;
        candidates.fillOpenedNothingLastTick = false;
        candidates.fillLostBatchLastTick = false;
        candidates.wokenByInputChange = false;
        diagnostics.recordReset(lost);
    }

    @Override
    public IRecipeLogicMachine machine() {
        return this.machine;
    }

    @Override
    public int slots() {
        return getThreadLimit();
    }

    @Override
    public GTRecipe findBatch() {
        return candidates.findBatch();
    }

    @Override
    public boolean consumeInputs(GTRecipe recipe) {
        return lifecycle.consumeInputs(recipe);
    }

    @Override
    public boolean tickInputs(GTRecipe recipe) {
        boolean advanced = lifecycle.tickInputs(recipe);
        display.recordTick(recipe, advanced);
        return advanced;
    }

    @Override
    public void finishOutputs(GTRecipe recipe) {
        tryFinishOutputs(recipe);
    }

    @Override
    public boolean tryFinishOutputs(GTRecipe recipe) {
        boolean finished = lifecycle.tryFinishOutputs(recipe);
        if (!finished) {
            display.recordTick(recipe, false);
        }
        return finished;
    }

    /** 在跑批次尚未消耗的 tick 输入和输出空间须合并模拟，避免多个批次占用同一额度。 */
    private GTRecipe overlayWithReservations(GTRecipe candidate) {
        return batchRuntime.overlayWithReservations(candidate, batches, RESERVE_NON_TICK_INPUTS);
    }

    /** 基类字段只镜像首条批次，批次表仍是各线程进度的唯一来源。 */
    private void mirrorBatchZeroToBaseFields() {
        if (batches.isEmpty()) {
            progress = 0;
            duration = 0;
            return;
        }
        NanochipConcurrentBatches.Batch primary = batches.get(0);
        lastRecipe = primary.recipe;
        progress = primary.progress;
        duration = primary.duration;
    }

    private void syncStatus(boolean working, boolean waiting) {
        if (working) {
            waitReasonCooldown = 0;
            if (getStatus() != RecipeLogic.Status.WORKING) {
                setStatus(RecipeLogic.Status.WORKING);
            }
            return;
        }
        if (waiting) {
            if (waitReasonCooldown > 0 && getStatus() == RecipeLogic.Status.WAITING) {
                waitReasonCooldown--;
                return;
            }
            waitReasonCooldown = WAIT_REASON_INTERVAL;
            setWaiting(admission.waitReason);
            return;
        }
        if (getStatus() != RecipeLogic.Status.IDLE) {
            setStatus(RecipeLogic.Status.IDLE);
        }
    }

    /** 批次表由服务端维护，客户端不能从空的本地表推导运行数量。 */
    public void appendThreadDisplayText(List<Component> textList) {
        display.appendThreadDisplayText(textList);
    }

    private static long realParallelsOf(GTRecipe recipe) {
        return NanochipThreadDisplay.realParallelsOf(recipe);
    }

    @Override
    public void saveCustomPersistedData(CompoundTag tag, boolean forDrop) {
        super.saveCustomPersistedData(tag, forDrop);
        if (forDrop) {
            return;
        }
        batches.save(tag);
    }

    /** 批次从 NBT 即时恢复，临时零件未就绪只暂停推进，不能借机删除已扣料任务。 */
    @Override
    public void loadCustomPersistedData(CompoundTag tag) {
        super.loadCustomPersistedData(tag);
        batches.load(tag);
        inputs.clearDiagnostic();
        display.beginTick();
        batchRuntime.clear();   // 概率缓存和预留属于运行期附注，读档后按需重建。
    }
}
