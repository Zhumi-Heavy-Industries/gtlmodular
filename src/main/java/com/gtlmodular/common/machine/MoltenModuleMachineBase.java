package com.gtlmodular.common.machine;

import com.gregtechceu.gtceu.api.block.ICoilType;
import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.IMachineLife;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.misc.EnergyContainerList;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.content.ContentModifier;
import com.gregtechceu.gtceu.utils.FormattingUtil;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;
import net.minecraft.ChatFormatting;
import com.gtlmodular.GTLModular;
import com.gtlmodular.common.config.GTLModularConfig;
import com.gtlmodular.common.machine.engine.ConcurrentRecipeEngine;
import com.gtlmodular.common.machine.engine.IThreadBatchLogic;
import com.gtlmodular.common.machine.engine.ThreadedRecipeLogic;
import com.gtlmodular.common.machine.fuel.DualFluidTax;
import com.gtlmodular.common.machine.tentacle.IThreadHatchPart;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.gtlcore.gtlcore.api.machine.multiblock.CoilWorkableElectricMultipleRecipesMachine;
import org.gtlcore.gtlcore.api.machine.multiblock.IModularMachineModule;
import org.gtlcore.gtlcore.api.recipe.IGTRecipe;
import org.gtlcore.gtlcore.api.recipe.IParallelLogic;
import org.gtlcore.gtlcore.api.recipe.RecipeResult;
import org.gtlcore.gtlcore.api.recipe.RecipeRunnerHelper;
import org.gtlcore.gtlcore.common.machine.trait.MultipleRecipesLogic;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;

/**
 * 模块机公共基座（亚空间式改造）。
 *
 * <p>模块本体仍是独立的 GTM 线圈多配方多方块（自带配方表可独立运行），额外实现
 * gtlcore {@link IModularMachineModule}，按「地心涌动能量转换枢纽」的宿主语义：
 * <ul>
 *   <li>成形(onStructureFormed)后向附近主机 {@link #findAndConnectToHost()} 注册；</li>
 *   <li>结构失效/方块被移除时 {@code removeFromHost} 注销，主机侧计数自动减少；</li>
 *   <li>主机与模块谁后成形谁负责建立连接（主机成形时会主动扫描已有模块）。</li>
 * </ul>
 * 判定盒为模块自身的小多方块（约 13×17×13），不含大楼，不会出现整盒巨判定。
 */
public abstract class MoltenModuleMachineBase<M extends MoltenModuleMachineBase<M>>
        extends CoilWorkableElectricMultipleRecipesMachine
        implements IModularMachineModule<MoltenModuleHostMachine, M>, IMachineLife,
        // ★ 2026-11-09：必须显式实现这个 gtladditions 接口 —— 否则本类的
        //   MoltenModuleRecipeLogic（extends gtlcore MultipleRecipesLogic）在构造时，
        //   gtladditions 的 mixin 会把机器强转成它 ⇒ 加载熔融模块即 ClassCastException
        //   （与奈亚 0.3.358 崩溃同源）。该接口方法全是 default，父类已有 getMaxParallel。
        com.gtladd.gtladditions.api.machine.IWirelessThreadModifierParallelMachine {

    /** 同步字段（原价/实付 EUt）必须挂到自己的 ManagedFieldHolder 上才会同步到客户端。 */
    public static final ManagedFieldHolder MANAGED_FIELD_HOLDER =
            new ManagedFieldHolder(MoltenModuleMachineBase.class,
                    WorkableMultiblockMachine.MANAGED_FIELD_HOLDER);

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    /** 本配方向主机要电的<b>原价</b>（EU/t，未减免）。 */
    @DescSynced
    private long syncedRawEut;

    /** 实际向主机<b>实付</b>（EU/t，已按主机线圈减免）。 */
    @DescSynced
    private long syncedPaidEut;

    public long getSyncedRawEut() {
        return syncedRawEut;
    }

    public long getSyncedPaidEut() {
        return syncedPaidEut;
    }

    /**
     * 独立线程引擎的可读状态（服务端每 20 tick 重算，同步给客户端）：
     * 第一行 = 「上限 N · 在用 K」，其后每行 = 一条在跑批次的「配方 + 进度」。
     * 没装线程仓 / 没进引擎时为空串（界面不显示，与旧版逐字一致）。
     */
    @DescSynced
    private String syncedEngineSummary = "";

    public String getSyncedEngineSummary() {
        return syncedEngineSummary;
    }

    /** 服务端调用：写入引擎状态串（客户端只读）。 */
    public void setEngineSummary(String summary) {
        if (getLevel() == null || getLevel().isClientSide) {
            return;
        }
        this.syncedEngineSummary = summary == null ? "" : summary;
    }

    /** 诊断：最近一次「温度不足」时，配方需求温度 / 本机实读线圈温度 / 本机判定炉温（全部服务端口径）。 */
    @DescSynced
    private int lastTempFailRequired = Integer.MIN_VALUE;

    @DescSynced
    private int lastTempFailCoilTemp = Integer.MIN_VALUE;

    @DescSynced
    private int lastTempFailMax = Integer.MIN_VALUE;

    /** 服务端调用：记录一次温度不足。 */
    public void recordTemperatureFailure(int required, int coilTemp, int maxTemperature) {
        if (getLevel() == null || getLevel().isClientSide) {
            return;
        }
        lastTempFailRequired = required;
        lastTempFailCoilTemp = coilTemp;
        lastTempFailMax = maxTemperature;
    }

    public int getLastTempFailRequired() {
        return lastTempFailRequired;
    }

    public int getLastTempFailCoilTemp() {
        return lastTempFailCoilTemp;
    }

    public int getLastTempFailMax() {
        return lastTempFailMax;
    }

    /** 模块找主机：水平/垂直搜索半径（格）。成形事件才触发一次，量级可控。 */
    private static final int HOST_SCAN_R = 96;   // 覆盖整栋大楼（模块贴远墙也能找到主机）
    private static final int HOST_SCAN_VER = 64; // 楼体垂直跨度 ~56，留出余量

    private BlockPos hostPosition;
    private MoltenModuleHostMachine host;

    /** 惰性构建的"读取主机"能量容器列表（生态圈式中央供电；模块自身不需要能量仓）。 */
    private transient EnergyContainerList hostEnergyList;

    public MoltenModuleMachineBase(IMachineBlockEntity holder) {
        super(holder);
    }

    /** 生态圈式 RecipeLogic：模块工作不走 GT 常规"从自身结构能量仓扣 tick EU"，而是每 tick 直接向主机要电。 */
    @Override
    protected RecipeLogic createRecipeLogic(Object... args) {
        return new MoltenModuleRecipeLogic(this, EBF_CHECK);
    }

    // ---------- 线程仓 / 独立线程引擎（装仓才启用；没装 = 旧合并机制）----------

    /** 结构里线程仓的当前设定值之和（没装 / 未成形 ⇒ 0；结构里本模组线程仓限 1 个）。 */
    public int threadHatchThreads() {
        long total = 0L;
        for (IMultiPart part : getParts()) {
            if (part instanceof IThreadHatchPart hatch) {
                total += Math.max(0, hatch.getThreads());
                if (total >= Integer.MAX_VALUE) {
                    return Integer.MAX_VALUE;
                }
            }
        }
        return (int) total;
    }

    /** 线程仓「并行重复配方」：全部仓都允许才算允许；没有仓 ⇒ 允许（GTO 默认）。 */
    public boolean threadHatchRepeatedRecipes() {
        for (IMultiPart part : getParts()) {
            if (part instanceof IThreadHatchPart hatch && !hatch.isRepeatedRecipes()) {
                return false;
            }
        }
        return true;
    }

    /** 线程仓「独立线程」：任一仓打开即生效（每条线程在全部配方表里找配方）。 */
    public boolean threadHatchIndependentThread() {
        for (IMultiPart part : getParts()) {
            if (part instanceof IThreadHatchPart hatch && hatch.isIndependentThread()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 独立线程引擎的线程数（&le;0 ⇒ 不启用，继续走 gtlcore 的「合并成一条配方」机制）。
     *
     * <p>★ 2026-11-07：<b>装线程仓就直接用，模块类不再逐个适配</b> ——
     * 线程数 = 仓的当前设定值；没装仓 ⇒ 0（走旧合并机制）。
     */
    public int engineThreads() {
        return threadHatchThreads();
    }

    /** 引擎模式下每条线程自己的并行数（各模块覆写；这是机器的并行口径，不是线程仓适配）。 */
    public int engineParallelPerBatch() {
        return 1;
    }

    /** 引擎模式是否启用（服务端/客户端都现读结构部件，判定一致）。 */
    public final boolean engineActive() {
        return engineThreads() > 0;
    }

    /** 「并行重复配方」关 ⇒ 同一配方只占一条线程（去重）。 */
    public final boolean engineDedupe() {
        return !threadHatchRepeatedRecipes();
    }

    /** 多表机器一律全表找配方（与「独立线程」开关解耦，GTO 语义）。 */
    public final boolean engineCrossTable() {
        return true;
    }

    /**
     * 「独立线程」（GTO 语义）：开 = 每条线程各有一份完整并行额度；
     * 关 = 所有线程共用同一份额度（在跑批次并行之和 ≤ {@link #engineParallelPerBatch()}）。
     */
    public final boolean engineIndependent() {
        return threadHatchIndependentThread();
    }

    /** 引擎模式：配方每 tick 的原价 EU。 */
    public long engineRawEut(GTRecipe recipe) {
        return RecipeHelper.getInputEUt(recipe);
    }

    /** 引擎模式：每 tick 实付 EU（套用主机线圈减免，与旧路径同一口径）。 */
    public long enginePaidEut(GTRecipe recipe) {
        long raw = engineRawEut(recipe);
        MoltenModuleHostMachine h = getHost();
        double discount = h == null ? 0.0D : h.getModuleEnergyDiscount();
        return discount > 0.0D ? Math.max(0L, Math.round(raw * (1.0D - discount))) : raw;
    }

    /** 向主机要电（主机未连接/未成形/停机 ⇒ false）。 */
    public boolean requestHostEnergy(long amount) {
        MoltenModuleHostMachine h = getHost();
        return h != null && h.requestEnergy(amount);
    }

    /** 把最近一 tick 的原价/实付写进同步字段（界面用）。 */
    public void updateSyncedEut(long raw, long paid) {
        if (getLevel() == null || getLevel().isClientSide) {
            return;
        }
        syncedRawEut = raw;
        syncedPaidEut = paid;
    }

    /** 模块是否已连接、成形且处于运行状态的主机（每 tick 判定，能量是否可得）。 */
    public boolean isHostReady() {
        MoltenModuleHostMachine h = getHost();
        return h != null && h.isFormed() && h.isWorkingEnabled();
    }

    /**
     * 生态圈同款 RecipeLogic：每 tick 判定 host 是否 ready，是则向主机请求本配方每 tick 能耗；
     * 主机扣电成功才推进进度；失败则等待（不中断、不丢配方），主机来电/换仓/重组后自动恢复。
     *
     * <p><b>两条路径</b>（2026-11-05 加入第二条）：
     * <ul>
     *   <li><b>旧路径</b>（没装线程仓，{@code module.engineActive() == false}）：完全保留
     *       gtlcore {@link MultipleRecipesLogic} 的「一次把所有能跑的配方合并成一条、共用一个进度」；</li>
     *   <li><b>独立线程引擎</b>（装了线程仓）：改由 {@link ConcurrentRecipeEngine} 调度，
     *       <b>每条线程一条批次、各有各的进度/时长</b>，线程数 = {@code module.engineThreads()}；
     *       两条线程仓开关在这里生效（「并行重复配方」= 逐线程去重，「独立线程」= 跨配方表找配方）。</li>
     * </ul>
     * 两条路径的耗电都走「主机中央供电 + 主机线圈减免」，并行都按模块自己的口径折算。
     */
    protected static class MoltenModuleRecipeLogic extends MultipleRecipesLogic implements IThreadBatchLogic {

        /** 引擎空转时一轮最多扫多少条候选（与通用独立线程引擎一致）。 */
        private static final int ENGINE_SCAN_BUDGET = 24;

        /** 一轮没找到配方后的冷却 tick（避免空转时每 tick 全表扫）。 */
        private static final int ENGINE_IDLE_COOLDOWN_TICKS = 10;

        /** 每 tick 最多新开几条批次（大线程仓首轮填槽时避免一次扫几百条候选造成卡顿）。 */
        private static final int ENGINE_MAX_STARTS_PER_TICK = 8;

        /** 每张配方表各自的扫描游标与冷却。 */
        private static final class ScanState {
            int cursor;
            long cooldownUntil;
        }

        private final MoltenModuleMachineBase<?> module;

        /** 独立线程批次表（只在服务端有内容）。 */
        private final ConcurrentRecipeEngine engine = new ConcurrentRecipeEngine();

        private final Map<GTRecipeType, ScanState> scanStates = new IdentityHashMap<>();

        /** 跨配方表的轮转起点（上次成功那张表的下标）。 */
        private int lastTypeIndex;

        /** 状态串同步节流。 */
        private long lastSummaryTick = Long.MIN_VALUE;
        private int lastSummarySize = -1;

        protected MoltenModuleRecipeLogic(MoltenModuleMachineBase<?> module,
                                          BiPredicate<CompoundTag, IRecipeLogicMachine> dataCheck) {
            super(module, dataCheck);
            this.module = module;
        }

        /** 引擎里正在跑的批次数（Jade/面板读取用）。 */
        @Override
        public int runningBatches() {
            return engine.size();
        }

        /** 引擎线程上限（= 机器给的 engineThreads）。 */
        @Override
        public int threadLimit() {
            return Math.max(1, module.engineThreads());
        }

        /**
         * 每条在跑批次的只读快照 —— 供 Jade 的通用线程显示（{@code IThreadBatchLogic}）逐条画进度条。
         * 与通用引擎 {@code ThreadedRecipeLogic#snapshotBatches()} 同格式。
         */
        @Override
        public List<ThreadedRecipeLogic.BatchView> snapshotBatches() {
            List<ThreadedRecipeLogic.BatchView> list = new ArrayList<>(engine.size());
            for (int i = 0; i < engine.size(); i++) {
                ConcurrentRecipeEngine.Batch batch = engine.get(i);
                list.add(new ThreadedRecipeLogic.BatchView(batch.recipe, batch.progress, batch.duration,
                        Math.max(1, batch.recipe.parallels)));
            }
            return list;
        }

        /**
         * 当前在跑批次占用的并行之和 —— 「独立线程」关闭时用来算剩余共享额度。
         */
        private int runningParallelSum() {
            int sum = 0;
            for (int i = 0; i < engine.size(); i++) {
                sum += Math.max(1, engine.get(i).recipe.parallels);
                if (sum < 0) {
                    return Integer.MAX_VALUE;
                }
            }
            return sum;
        }

        @Override
        public void serverTick() {
            // 没装线程仓 / 线程仓不生效 ⇒ 逐字走旧路径
            if (!module.engineActive()) {
                if (engine.size() > 0) {
                    engine.clear();
                    scanStates.clear();
                    module.setEngineSummary("");
                }
                super.serverTick();
                return;
            }
            if (isSuspend()) {
                return;
            }
            // 部件级「每 tick 工作钩子」（双流体税/维护仓等）整机每 tick 只调一次，
            // 不能按批次数放大 —— 与通用引擎 ThreadedRecipeLogic 同一条纪律。
            if (engine.size() > 0 && !machine.onWorking()) {
                setWaiting(null);
                return;
            }
            EngineHost host = new EngineHost();
            engine.tick(host);
            if (engine.size() > 0) {
                // 展示回写：把第 0 条批次镜像进基类字段（GUI/Jade 的单进度条免费可用）
                ConcurrentRecipeEngine.Batch primary = engine.get(0);
                this.lastRecipe = primary.recipe;
                this.progress = primary.progress;
                this.duration = primary.duration;
                if (host.advanced || host.started) {
                    setStatus(Status.WORKING);
                    totalContinuousRunningTime++;
                } else if (host.waitReason != null) {
                    setWaiting(host.waitReason);
                }
            } else {
                this.progress = 0;
                this.duration = 0;
                if (!isIdle()) {
                    setStatus(Status.IDLE);
                }
            }
            updateEngineSummary();
        }

        /** 引擎：每 tick 的「原价/实付/去重/跨表」判定都转发给机器。 */
        private final class EngineHost implements ConcurrentRecipeEngine.Host {

            /** 本 tick 是否真的推动了至少一条批次（决定 WORKING / WAITING）。 */
            private boolean advanced;
            /** 本 tick 是否新开出一条批次。 */
            private boolean started;
            /** 本 tick 已经新开了几条（首轮填槽限速）。 */
            private int startsThisTick;
            /** 本 tick 的等待原因（电量/主机不可用）。 */
            private Component waitReason;

            @Override
            public IRecipeLogicMachine machine() {
                return MoltenModuleRecipeLogic.this.machine;
            }

            @Override
            public int slots() {
                return Math.max(1, module.engineThreads());
            }

            @Override
            public GTRecipe findBatch() {
                if (startsThisTick >= ENGINE_MAX_STARTS_PER_TICK) {
                    return null;
                }
                GTRecipeType current = MoltenModuleRecipeLogic.this.machine.getRecipeType();
                if (current == null) {
                    return null;
                }
                GTRecipeType[] types = MoltenModuleRecipeLogic.this.machine.getRecipeTypes();
                boolean crossTable = module.engineCrossTable() && types != null && types.length > 1;
                if (!crossTable) {
                    return findBatchForType(current);
                }
                int n = types.length;
                int start = lastTypeIndex % n;
                for (int i = 0; i < n; i++) {
                    int idx = (start + i) % n;
                    GTRecipe recipe = findBatchForType(types[idx]);
                    if (recipe != null) {
                        lastTypeIndex = idx;
                        return recipe;
                    }
                }
                return null;
            }

            /** 单张配方表的候选搜索：校验 → 逐线程并行 → 折算 EU/时长。 */
            private GTRecipe findBatchForType(GTRecipeType type) {
                long now = MoltenModuleRecipeLogic.this.machine.self().getOffsetTimer();
                ScanState state = scanStates.computeIfAbsent(type, t -> new ScanState());
                if (now < state.cooldownUntil) {
                    return null;
                }
                List<GTRecipe> candidates = type.getLookup().getLookup().getRecipes(true)
                        .filter(r -> !r.isFuel()).toList();
                int total = candidates.size();
                if (total == 0) {
                    state.cooldownUntil = now + ENGINE_IDLE_COOLDOWN_TICKS;
                    return null;
                }
                int start = state.cursor % total;
                int limit = Math.min(ENGINE_SCAN_BUDGET, total);
                for (int i = 0; i < limit; i++) {
                    GTRecipe raw = candidates.get((start + i) % total);
                    if (raw == null || isRunningElsewhere(raw)) {
                        continue;
                    }
                    if (!RecipeRunnerHelper.matchRecipe(MoltenModuleRecipeLogic.this.machine, raw)) {
                        continue;
                    }
                    if (IGTRecipe.of(raw).getEuTier() > getMachine().getTier()) {
                        continue;
                    }
                    if (!raw.checkConditions(MoltenModuleRecipeLogic.this).isSuccess()) {
                        continue;
                    }
                    BiPredicate<CompoundTag, IRecipeLogicMachine> check = getDataCheck();
                    if (check != null && !check.test(raw.data, MoltenModuleRecipeLogic.this.machine)) {
                        continue;
                    }
                    GTRecipe prepared;
                    try {
                        prepared = prepareBatch(raw);
                    } catch (RuntimeException e) {
                        // 单条候选的配方修改器/并行计算异常不应把服务端主线程掀翻：
                        // 记一条 debug（限频交给日志等级），跳过这条，继续扫下一条。
                        GTLModular.LOGGER.debug("[gtlmodular] 独立线程：候选 {} 折算失败，跳过：{}",
                                raw.getId(), e.toString());
                        continue;
                    }
                    if (prepared != null) {
                        state.cooldownUntil = 0;
                        state.cursor = (start + i + 1) % total;
                        startsThisTick++;
                        return prepared;
                    }
                }
                boolean fullPass = start + limit >= total;
                state.cursor = fullPass ? 0 : (start + limit) % total;
                if (fullPass) {
                    state.cooldownUntil = now + ENGINE_IDLE_COOLDOWN_TICKS;
                }
                return null;
            }

            /**
             * 把一条原始配方变成「一条线程的批次」：逐线程并行 → 输出概率 → EU/时长折算。
             *
             * <p>折算口径与 gtlcore 旧路径逐字一致：{@code totalEu = 配方总能耗 × getEuMultiplier()}，
             * {@code 时长 = max(totalEu / 超频电压, 20)}，{@code 每 tick EU = min(超频电压, …)}。
             * 旧路径把很多条配方加进同一条；这里换成了每条线程只装一条。
             */
            private GTRecipe prepareBatch(GTRecipe raw) {
                // 先走机器自己的配方修改器（超频/休闲降档/EBF 等）。候选来自「配方表原始表」，
                // 不经过这一步会拿到未超频的配方 —— 与通用引擎 ThreadedRecipeLogic 同一接法。
                GTRecipe modified = machine.fullModifyRecipe(raw.copy(), ocParams, ocResult);
                if (modified == null) {
                    return null;
                }
                long limit = Math.max(1, module.engineParallelPerBatch());
                // ★ 2026-11-07 GTO 口径「独立线程」：
                //   开 = 每条线程各自一份完整并行额度；关 = 所有线程共用同一份额度。
                if (!module.engineIndependent()) {
                    limit -= runningParallelSum();
                    if (limit <= 0) {
                        return null;
                    }
                }
                long parallel = IParallelLogic.getMaxParallel(machine, modified, limit);
                if (parallel <= 0) {
                    return null;
                }
                GTRecipe recipe = parallel > 1
                        ? modified.copy(ContentModifier.multiplier((double) parallel), false)
                        : modified;
                IGTRecipe.of(recipe).setRealParallels(parallel);
                recipe = IParallelLogic.getRecipeOutputChance(machine, recipe);
                if (!RecipeRunnerHelper.matchRecipeInput(machine, recipe)) {
                    return null;
                }
                long overclock = getMachine().getOverclockVoltage();
                if (overclock <= 0) {
                    return null;
                }
                double totalEu = getTotalEuOfRecipe(recipe) * getEuMultiplier();
                double ratio = totalEu / (double) overclock;
                long tickEu = ratio > 20.0D
                        ? overclock
                        : (long) ((double) overclock * ratio / 20.0D);
                recipe.duration = (int) Math.max(1.0D, Math.round(Math.max(ratio, 20.0D)));
                recipe.tickInputs.put(EURecipeCapability.CAP,
                        List.of(new Content(Math.max(1L, tickEu), 10000, 10000, 0, null, null)));
                IGTRecipe.of(recipe).setHasTick(true);
                return recipe;
            }

            /** 「并行重复配方」关时：同一条配方只许占一条线程。 */
            private boolean isRunningElsewhere(GTRecipe raw) {
                if (!module.engineDedupe() || raw.getId() == null) {
                    return false;
                }
                for (int i = 0; i < engine.size(); i++) {
                    GTRecipe running = engine.get(i).recipe;
                    if (running != null && raw.getId().equals(running.getId())) {
                        return true;
                    }
                }
                return false;
            }

            @Override
            public boolean consumeInputs(GTRecipe recipe) {
                if (!machine.beforeWorking(recipe)) {
                    return false;
                }
                recipe.preWorking(machine);
                if (!RecipeRunnerHelper.handleRecipeInput(machine, recipe)) {
                    machine.afterWorking();
                    return false;
                }
                started = true;
                return true;
            }

            @Override
            public boolean tickInputs(GTRecipe recipe) {
                if (!module.isHostReady()) {
                    waitReason = Component.translatable("gtlmodular.recipe.fail.host_unavailable");
                    return false;
                }
                long raw = module.engineRawEut(recipe);
                long paid = module.enginePaidEut(recipe);
                module.updateSyncedEut(raw, paid);
                if (!module.requestHostEnergy(paid)) {
                    waitReason = RecipeResult.FAIL_NO_ENOUGH_EU_IN.reason();
                    return false;
                }
                advanced = true;
                return true;
            }

            @Override
            public void finishOutputs(GTRecipe recipe) {
                machine.afterWorking();
                recipe.postWorking(machine);
                RecipeRunnerHelper.handleRecipeOutput(machine, recipe);
            }
        }

        /** 把引擎状态（上限/在用/每条批次进度）写进同步字段，界面/Jade 可见。 */
        private void updateEngineSummary() {
            long now = machine.self().getOffsetTimer();
            int size = engine.size();
            if (now == lastSummaryTick) {
                return;
            }
            if (size == lastSummarySize && now % 10L != 0L) {
                return;
            }
            lastSummaryTick = now;
            lastSummarySize = size;
            StringBuilder sb = new StringBuilder();
            sb.append("上限 ").append(threadLimit()).append(" · 在用 ").append(size);
            int shown = Math.min(4, size);
            for (int i = 0; i < shown; i++) {
                ConcurrentRecipeEngine.Batch batch = engine.get(i);
                sb.append('\n').append(i + 1).append(". ")
                        .append(shortRecipeName(batch.recipe)).append(' ')
                        .append(batch.duration <= 0 ? 0 : Math.min(100, batch.progress * 100 / batch.duration))
                        .append('%');
            }
            if (size > shown) {
                sb.append("\n…还有 ").append(size - shown).append(" 条");
            }
            module.setEngineSummary(sb.toString());
        }

        private static String shortRecipeName(GTRecipe recipe) {
            if (recipe == null || recipe.getId() == null) {
                return "?";
            }
            String path = recipe.getId().getPath();
            return path.length() > 28 ? path.substring(0, 28) + "…" : path;
        }

        /**
         * 旧路径（没装线程仓）的每 tick 耗电：与改造前逐字一致，只是把「原价/实付」抽成
         * {@link MoltenModuleMachineBase#engineRawEut}/{@link MoltenModuleMachineBase#enginePaidEut}，
         * 让引擎路径与旧路径共用同一份减免口径。
         */
        @Override
        public void handleRecipeWorking() {
            GTRecipe recipe = lastRecipe;
            if (recipe == null) {
                return;
            }
            if (!module.isHostReady()) {
                setWaiting(Component.translatable("gtlmodular.recipe.fail.host_unavailable"));
                if (getStatus() == RecipeLogic.Status.WAITING) {
                    doDamping();
                }
                return;
            }
            long raw = module.engineRawEut(recipe);
            long eut = module.enginePaidEut(recipe);
            module.updateSyncedEut(raw, eut);
            boolean granted = module.requestHostEnergy(eut);
            if (granted) {
                setStatus(RecipeLogic.Status.WORKING);
                if (!machine.onWorking()) {
                    interruptRecipe();
                    return;
                }
                progress++;
                totalContinuousRunningTime++;
            } else {
                setWaiting(org.gtlcore.gtlcore.api.recipe.RecipeResult.FAIL_NO_ENOUGH_EU_IN.reason());
                if (getStatus() == RecipeLogic.Status.WAITING) {
                    doDamping();
                }
            }
        }

        @Override
        public void resetRecipeLogic() {
            super.resetRecipeLogic();
            engine.clear();
            scanStates.clear();
        }

        @Override
        public void saveCustomPersistedData(CompoundTag tag, boolean forDrop) {
            super.saveCustomPersistedData(tag, forDrop);
            engine.save(tag);
        }

        @Override
        public void loadCustomPersistedData(CompoundTag tag) {
            super.loadCustomPersistedData(tag);
            scanStates.clear();
            // 读档时结构通常还没成形（getParts() 为空），这里不能按 engineActive() 判空清表，
            // 否则重进存档会把独立线程批次全丢掉；成形后第一个 serverTick 若没装线程仓会自动清。
            engine.load(tag);
        }
    }

    /** 覆盖能量容器：模块的可用电能读自主机（主机未连接/未成形/断电 → 0）。 */
    @Override
    public EnergyContainerList getEnergyContainer() {
        if (hostEnergyList == null) {
            hostEnergyList = new EnergyContainerList(List.of(new HostEnergyContainer(this)));
        }
        return hostEnergyList;
    }

    /**
     * 界面刷新前把 gtceu 基类显示用的两个<b>缓存字段</b>同步到主机实况：
     * gtceu 的 {@code addEnergyUsageLine}/{@code addEnergyTierLine} 读的是基类 protected 字段
     * {@code energyContainer} 与 {@code tier}。这两个字段在 {@code onStructureFormed} 时赋一次值，
     * 且 {@code EnergyContainerList.getInputVoltage()} 是构造时缓存的 —— 模块成形时主机往往尚未
     * 成形/连接，于是「最大功率」恒为 0、「最大配方等级」恒为 ULV，之后不再刷新。
     * 这里每帧按主机实况重建这两个字段（对照 Biosphere 模块 UI），让它们与主机侧实时一致。
     */
    @Override
    public void addDisplayText(List<Component> textList) {
        MoltenModuleHostMachine h = getHost();
        boolean hostLive = h != null && h.isFormed();
        EnergyContainerList live = hostLive
                ? new EnergyContainerList(List.of(new HostEnergyContainer(this)))
                : null;
        // 写入 gtceu 基类字段（protected，本类可访问），addEnergyUsageLine/addEnergyTierLine 才读得到新值
        this.energyContainer = live;
        this.tier = hostLive ? h.getTier() : 0;
        // 同步本类缓存，保证 getEnergyContainer() 与显示一致
        hostEnergyList = live;
        super.addDisplayText(textList);
        addHostPowerLine(textList);
        addEngineLine(textList);
    }

    /**
     * 独立线程引擎状态行（装线程仓时才有内容）。
     *
     * <p>状态串由服务端在 {@code MoltenModuleRecipeLogic#updateEngineSummary()} 里重算并
     * {@code @DescSynced} 同步过来；客户端直接读同步值 —— 批次表本身不参与网络同步，
     * 客户端读本地批次表永远是空的（与通用引擎同一条纪律）。
     */
    protected void addEngineLine(List<Component> textList) {
        String summary = syncedEngineSummary;
        if (summary == null || summary.isEmpty()) {
            return;
        }
        String[] lines = summary.split("\n");
        textList.add(Component.literal("独立线程: ")
                .append(Component.literal(lines[0]).withStyle(ChatFormatting.AQUA))
                .withStyle(ChatFormatting.GRAY));
        for (int i = 1; i < lines.length; i++) {
            textList.add(Component.literal("  " + lines[i]).withStyle(ChatFormatting.DARK_AQUA));
        }
    }

    /**
     * 中央供电实况行（排查"配方不走 / 减免没生效"用）：
     * 连接状态 · 主机蓄电 · 本配方原价 EU/t → 主机实付 EU/t（含线圈减免）。
     * 减免按主机同步过来的线圈温度在本地重算，所以换完线圈这一行会立刻变。
     */
    protected void addHostPowerLine(List<Component> textList) {
        MoltenModuleHostMachine h = getHost();
        boolean linked = h != null && h.isFormed();
        if (!linked) {
            textList.add(Component.literal("主机供电: ")
                    .append(Component.literal("未连接").withStyle(ChatFormatting.RED))
                    .append(Component.literal("（模块成形后自动连接；主机需先成形）").withStyle(ChatFormatting.GRAY))
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        int coilTemp = h.getSyncedCoilTemperature();
        int pct = MoltenModuleHostMachine.discountPercentForTemperature(coilTemp);
        Component line = Component.literal("主机供电: ")
                .append(Component.literal("已连接").withStyle(ChatFormatting.GREEN))
                .append(Component.literal(" · 蓄电 ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(FormattingUtil.formatNumbers(h.getTotalStoredEU())
                        + " / " + FormattingUtil.formatNumbers(h.getTotalCapacityEU()) + " EU")
                        .withStyle(ChatFormatting.AQUA))
                .withStyle(ChatFormatting.GRAY);
        textList.add(line);
        textList.add(Component.literal("耗电: ")
                .append(Component.literal(FormattingUtil.formatNumbers(syncedRawEut) + " EU/t").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(" → 主机实付 ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(FormattingUtil.formatNumbers(syncedPaidEut) + " EU/t")
                        .withStyle(ChatFormatting.AQUA))
                .append(Component.literal("（主机线圈 " + coilTemp + " K · 减免 " + pct + "%）")
                        .withStyle(pct > 0 ? ChatFormatting.AQUA : ChatFormatting.DARK_GRAY))
                .withStyle(ChatFormatting.GRAY));
        // 等待原因（能量不足 / 主机不可用等）直接抄 GT 的状态提示
        if (getRecipeLogic() != null) {
            for (Component reason : getRecipeLogic().getFancyTooltip()) {
                textList.add(reason.copy().withStyle(ChatFormatting.RED));
            }
        }
    }

    // ---------- 线圈（模块自己没有线圈，一律代理主机那栋楼的线圈）----------

    /**
     * 模块结构里没有线圈方块（线圈在「地心涌动能量转换站」大楼上），所以整条温度链路必须代理主机：
     * <ul>
     *   <li>gtceu 的 EBF 温度校验就是 {@code getCoilType().getCoilTemperature() >= recipe.data["ebf_temp"]}，
     *       不代理的话模块的线圈恒为 null → 高温配方永远匹配不上；</li>
     *   <li>界面「热容」行、线圈降耗（{@code CoilBoostLogic}）同样读这里。</li>
     * </ul>
     * 自身结构里真出现线圈（未来若有）时以自身的为准；否则取主机的；都没有则 null（= 无温度）。
     */
    @Override
    public ICoilType getCoilType() {
        ICoilType own = super.getCoilType();
        if (own != null) {
            return own;
        }
        MoltenModuleHostMachine h = getHost();
        return (h != null && h.isFormed()) ? h.getCoilType() : null;
    }

    /** 当前生效的线圈温度（K）；无线圈 / 主机未连接 = 0。 */
    public int getCoilTemperature() {
        ICoilType coil = getCoilType();
        return coil == null ? 0 : coil.getCoilTemperature();
    }

    /**
     * 主机线圈温度（K）—— <b>界面/公式一律用这个</b>：
     * 优先取主机同步过来的温度（客户端与服务端同一口径），没有同步值时才退回自己实时读主机。
     * 注意：{@link #getCoilType()} 在客户端可能还是旧结构判定留下的线圈，不能拿来显示。
     */
    public int hostCoilTemperature() {
        MoltenModuleHostMachine h = getHost();
        if (h == null || !h.isFormed()) {
            return 0;
        }
        int synced = h.getSyncedCoilTemperature();
        return synced > 0 ? synced : h.getCoilTemperature();
    }

    // ---------- 电压温度加成（电力高炉同款）----------

    /** MV 的电压等级序号（ULV=0、LV=1、MV=2）。 */
    public static final int MV_TIER = 2;

    /** 超过 MV 后，每高一级电压，炉温 +100K。 */
    public static final int TEMPERATURE_PER_VOLTAGE_TIER = 100;

    /** 电压温度加成的等级数 = max(0, 机器电压等级 − MV)。 */
    public int temperatureTierBonusLevels() {
        return Math.max(0, getTier() - MV_TIER);
    }

    /** 电压温度加成（K）= 等级数 × 100。 */
    public int temperatureTierBonus() {
        return temperatureTierBonusLevels() * TEMPERATURE_PER_VOLTAGE_TIER;
    }

    /**
     * <b>有效炉温（K）</b> = 主机线圈温度 + 电压加成 = 线圈温度 + 100 × max(0, 电压等级 − MV)。
     *
     * <p>与「电力高炉」口径一致（{@code gtceu.machine.electric_blast_furnace.tooltip.2}：
     * 超过 MV 后的每一级电压都会把温度提高 100K）。高炉单元的温度校验与界面都用这个值，
     * 定义级 {@code ebfOverclock} 修正器（gtlcore 改写版）用的是同一式子。
     */
    public int effectiveTemperature() {
        return hostCoilTemperature() + temperatureTierBonus();
    }

    /** 主机已连接/主机结构重建（含换线圈）后的回调；子类可覆写以刷新按线圈折算的数值。 */
    public void onHostLinked() {}

    // ---------- 物品说明共用片段（三个模块共用；数字按当前配置现算）----------

    /** 去掉浮点尾巴：0.1 → "0.1"，4.0 → "4"。 */
    public static String trimNumber(double value) {
        if (!Double.isInfinite(value) && value == Math.floor(value)) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }

    /**
     * 双流体税说明行（三个模块共用）：
     * 「每秒都需要 &lt;配置量&gt; mB &lt;流体1&gt; 与 &lt;配置量&gt; mB &lt;流体2&gt;；自 IV 起电压每提高一级，两者各 ×&lt;配置倍率&gt;」。
     * 数字全部读 {@code config/gtlmodular-common.toml}，流体名按配置的 id 现取。
     */
    public static void appendFluidTaxTooltip(List<Component> lines) {
        Component amount = Component.literal(String.valueOf(GTLModularConfig.getIvBaselineMbPerSecond()))
                .withStyle(ChatFormatting.GOLD);
        Component multiplier = Component.literal(trimNumber(GTLModularConfig.getPerTierMultiplier()))
                .withStyle(ChatFormatting.GOLD);
        lines.add(Component.translatable("gtlmodular.module.tooltip.tax",
                amount, DualFluidTax.lavaDisplayName(),
                amount.copy(), DualFluidTax.blazeDisplayName(), multiplier)
                .withStyle(ChatFormatting.GRAY));
    }

    /** 生态圈式：电压/等级直接代理主机（主机换仓/重组后模块自动同步）。 */
    @Override
    public int getTier() {
        MoltenModuleHostMachine h = getHost();
        return (h != null && h.isFormed()) ? h.getTier() : 0;
    }

    @Override
    public long getOverclockVoltage() {
        MoltenModuleHostMachine h = getHost();
        return (h != null && h.isFormed()) ? h.getOverclockVoltage() : 0;
    }

    /** 代理容器：能量存储与耗电都走主机。 */
    private static final class HostEnergyContainer implements IEnergyContainer {
        private final MoltenModuleMachineBase<?> module;

        HostEnergyContainer(MoltenModuleMachineBase<?> module) {
            this.module = module;
        }

        private MoltenModuleHostMachine h() {
            MoltenModuleHostMachine h = module.getHost();
            return (h != null && h.isFormed()) ? h : null;
        }

        @Override
        public long getEnergyStored() {
            MoltenModuleHostMachine h = h();
            return h == null ? 0 : h.getTotalStoredEU();
        }

        @Override
        public long getEnergyCapacity() {
            MoltenModuleHostMachine h = h();
            return h == null ? 0 : h.getTotalCapacityEU();
        }

        @Override
        public long getInputVoltage() {
            MoltenModuleHostMachine h = h();
            return h == null ? 0 : h.getMaxInputVoltage();
        }

        @Override
        public long getInputAmperage() {
            return 1;
        }

        @Override
        public boolean inputsEnergy(Direction side) {
            return false; // 不从旁路充能, 仅由主机供给
        }

        @Override
        public long acceptEnergyFromNetwork(Direction side, long amount, long voltage) {
            return 0; // 拒绝直接注入
        }

        @Override
        public long changeEnergy(long amount) {
            if (amount < 0) {
                long need = -amount;
                MoltenModuleHostMachine h = h();
                if (h != null && h.consumeEU(need)) {
                    return -need; // 从主机成功扣除
                }
                return 0; // 主机没电/断连 → 扣不动
            }
            return 0; // 模块不回充主机
        }
    }

    @Override
    public BlockPos getHostPosition() {
        return hostPosition;
    }

    @Override
    public void setHostPosition(BlockPos hostPosition) {
        this.hostPosition = hostPosition;
    }

    @Override
    public MoltenModuleHostMachine getHost() {
        return host;
    }

    @Override
    public void setHost(MoltenModuleHostMachine host) {
        this.host = host;
    }

    @Override
    public Class<MoltenModuleHostMachine> getHostType() {
        return MoltenModuleHostMachine.class;
    }

    /** 半径搜索由 {@link #findAndConnectToHost()} 覆盖实现，这里无需点位表。 */
    @Override
    public BlockPos[] getHostScanPositions() {
        return new BlockPos[0];
    }

    /**
     * 成形时查找附近已成形的主机并连接（仅服务器端执行）。
     *
     * <p><b>性能（2026-09-13 优化）</b>：旧实现按坐标三重循环遍历
     * {@code (2r+1)²(2ver+1)} 个点（r=96/ver=64 → <b>480 万</b> 次），每个点都做
     * {@code isLoaded + MetaMachine.getMachine} 的区块表查表。现改为<b>按区块枚举方块实体</b>
     * （箱内 14×14 个区块，每块的 BE 各访问一次）。
     *
     * <p>连接选择：旧实现按 {@code dx/dz/dy} 顺序返回<b>第一个</b>可用主机；现按<b>最近</b>主机连接
     * （距离平方最小，结果确定，不受区块/映射遍历顺序影响）。同一栋楼通常只有一台主机，行为一致。
     */
    @Override
    public boolean findAndConnectToHost() {
        Level lv = getLevel();
        if (!(lv instanceof ServerLevel serverLevel)) {
            return false;
        }
        if (isConnectedToHost() && getHost() != null && getHost().isFormed()) {
            return true;
        }
        BlockPos c = getPos();
        int cx = c.getX(), cy = c.getY(), cz = c.getZ();
        int x0 = cx - HOST_SCAN_R, x1 = cx + HOST_SCAN_R;
        int y0 = cy - HOST_SCAN_VER, y1 = cy + HOST_SCAN_VER;
        int z0 = cz - HOST_SCAN_R, z1 = cz + HOST_SCAN_R;
        MoltenModuleHostMachine best = null;
        double bestDist = Double.MAX_VALUE;
        for (int chx = x0 >> 4; chx <= x1 >> 4; chx++) {
            for (int chz = z0 >> 4; chz <= z1 >> 4; chz++) {
                LevelChunk chunk = serverLevel.getChunkSource().getChunkNow(chx, chz);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity be : new ArrayList<>(chunk.getBlockEntities().values())) {
                    BlockPos p = be.getBlockPos();
                    if (p.getX() < x0 || p.getX() > x1 || p.getY() < y0 || p.getY() > y1
                            || p.getZ() < z0 || p.getZ() > z1) {
                        continue;
                    }
                    if (!(MetaMachine.getMachine(lv, p) instanceof MoltenModuleHostMachine h)) {
                        continue;
                    }
                    if (!h.isFormed() || h.exceedsModuleLimit()) {
                        continue;
                    }
                    double dist = p.distSqr(c);
                    if (dist < bestDist) {
                        bestDist = dist;
                        best = h;
                    }
                }
            }
        }
        if (best == null) {
            return false;
        }
        connectToHost(best);
        onHostLinked();
        // 模块自连主机这条路径同样要触发 AE 无线广播（与主机侧 scanModules 的调用等价、幂等）
        best.onModuleConnected(this);
        return true;
    }

    @Override
    public void onStructureFormed() {
        super.onStructureFormed();
        findAndConnectToHost();
    }

    @Override
    public void onStructureInvalid() {
        super.onStructureInvalid();
        MoltenModuleHostMachine host = getHost();
        removeFromHost(host);
        if (host != null) {
            host.onModuleDisconnected(this);
        }
    }

    /** 方块被破坏/移除：断开主机链接。 */
    @Override
    public void onMachineRemoved() {
        MoltenModuleHostMachine host = getHost();
        removeFromHost(host);
        if (host != null) {
            host.onModuleDisconnected(this);
        }
    }
}
