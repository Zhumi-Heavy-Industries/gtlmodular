package com.gtlmodular.common.machine.engine;

import com.gregtechceu.gtceu.api.capability.IParallelHatch;
import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.IRecipeHandler;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.common.data.GTRecipeModifiers;
import com.gtlmodular.GTLModular;
import org.gtlcore.gtlcore.api.machine.trait.IRecipeCapabilityMachine;
import org.gtlcore.gtlcore.api.machine.trait.MEPatternRecipeHandlePart;
import org.gtlcore.gtlcore.api.machine.trait.MEPart.IMEPatternPartMachine;
import org.gtlcore.gtlcore.api.recipe.RecipeRunnerHelper;
import com.google.common.collect.Table;
import com.lowdragmc.lowdraglib.side.fluid.FluidStack;
import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * ★ <b>独立线程引擎 · 机器侧接法</b>：把 {@link ConcurrentRecipeEngine} 接到 GTCEu 的
 * {@link RecipeLogic} 上，让一台机器<b>同时推进多条配方</b>。
 *
 * <h2>它只做三件事</h2>
 * <ol>
 *   <li><b>驱动</b>：{@link #serverTick} 每 tick 调一次 {@link ConcurrentRecipeEngine#tick}，
 *       内部类 {@link HostImpl} 回答引擎的六个问题（找配方／扣料／每 tick 输入／收尾出料）；</li>
 *   <li><b>展示回写</b>：批次表非空时把<b>第 0 条</b>的 {@code recipe}／{@code progress}／{@code duration}
 *       写进基类的同步字段，并把状态置为 {@code WORKING}；空表则清零并置 {@code IDLE}
 *       ⇒ GUI／Jade／TOP 那套单进度条免费可用（只反映一条批次）；</li>
 *   <li><b>存读档</b>：{@link #saveCustomPersistedData}／{@link #loadCustomPersistedData}
 *       转发给引擎（批次表是追加键，不新增任何 {@code @Persisted} 字段）。</li>
 * </ol>
 *
 * <h2>为什么不调 {@code super.serverTick()}（★ 关键）</h2>
 * GTCEu 基类的 {@code serverTick()} 会在空闲时自己找配方、自己扣料、自己推进
 * {@code progress}，并会在表尾「空闲自退订」把 tick 订阅摘掉。本类要独占配方的挑选与扣料
 * （扣料只允许发生在 {@link ConcurrentRecipeEngine#tick} 的第③步、一条只扣一次），
 * 所以<b>整体覆写且从不调 {@code super.serverTick()}</b>。
 *
 * <p>订阅本身由基类 {@code onMachineLoad()} → {@code updateTickSubscription()} 建立；
 * 本类<b>不主动取消订阅</b>。已验证 GTCEu 1.20.1-1.4.4 的「空闲自退订」只写在
 * {@code serverTick()} 表尾（本类不调它 ⇒ 不会触发），而 {@code setStatus(...)} 内部的
 * {@code updateTickSubscription()} 只在「暂停／配方逻辑不可用」时才退订 ⇒ 空闲时调
 * {@code setStatus(Status.IDLE)} <b>不会</b>把本逻辑停掉。
 *
 * <h2>与基类字段的一处已知差异</h2>
 * {@code RecipeLogic.isActive} 是 <b>private 且没有 setter</b>（javap 现读 1.4.4），
 * 子类无法写入 ⇒ 本类只写 {@code lastRecipe}／{@code progress}／{@code duration}
 * 并用 {@code setStatus(WORKING/IDLE)} 驱动 {@code isWorking()}（GUI 判定用的就是它）；
 * 基类那个只读口 {@link #isActive()} 则被覆写成「批次表非空」——
 * 语义与「把 {@code isActive} 写成 true」逐字等价，且即使读档后基类字段回卷也不会说谎。
 * 批次表不参与网络同步 ⇒ 客户端上 {@code isActive()} 与基线一致（恒 false）。
 *
 * <h2>并行怎么算</h2>
 * {@code max(1, parallelMultiplier()) × max(1, 并行仓并行数)} 一起交给
 * {@code GTRecipeModifiers.accurateParallel}；并行仓并行数取「已成形多方块所有
 * {@link IParallelHatch} 部件里最大的 {@code getCurrentParallel()}」，没有并行仓就是 1。
 *
 * @see IThreadedRecipeMachine
 * @see ConcurrentRecipeEngine
 */
public class ThreadedRecipeLogic extends RecipeLogic implements IThreadBatchLogic {

    /** 并发批次表（纯调度，不依赖任何机器类；见 {@link ConcurrentRecipeEngine}）。 */
    private final ConcurrentRecipeEngine engine = new ConcurrentRecipeEngine();

    /** 机器侧契约（构造时由传入的 machine 强转得到）。 */
    private final IThreadedRecipeMachine threaded;

    // ---------- 2026-10-30 诊断（限频；2026-11-03 改为「每个不同原因最多 12 行」） ----------
    private int diagLines;
    private int diagCandidates;
    /**
     * 上一次<b>真实</b>拒绝原因（2026-11-03 玩家要求可读）：
     * <ul>
     *   <li>扫描冷却（{@link #IDLE_COOLDOWN_TICKS}）<b>不再覆盖</b>它 —— 旧版每 100 tick 打诊断时
     *       恰好常落在冷却里，日志只会写「扫描冷却中（剩 N tick）」，看不到真正原因；</li>
     *   <li>原因变化时把 {@link #diagLines} 归零 ⇒ 同一个原因最多 12 行，换原因重新计数，
     *       长时间空转也不会刷屏、也不会「前 12 行之后永远没日志」。</li>
     * </ul>
     */
    private String diagReject = "（还没试）";
    private String diagLastLoggedReason;

    /** 上一次被判「物品/流体不匹配」的那条配方（诊断行据此算出缺的是物品还是流体）。 */
    private GTRecipe diagRecipe;

    /** 上一次失败的附加原因（每 tick 输入 / 配方条件的 {@code reason()} 文本）。 */
    private String diagExtra;

    /** 最后一次真正扫过的配方表 id（跨表时日志里能看到「这次扫的是哪张表」）。 */
    private String diagScannedType;

    /** 一条候选的失败记录（明细落盘用：原因 + 配方对象，配方对象只用来补「缺什么」）。 */
    private record ScanReject(String reason, GTRecipe recipe) {}

    /**
     * ★ 2026-11-03：<b>逐条扫描明细</b>。每张表先按 24 条分片轮扫；等整张表被覆盖完且一条都没跑起来时，
     * 把「每条候选为什么失败」一次性打到日志（每张表每次世界加载只打一次，≤40 行）。
     *
     * <p>为什么需要它：跨表诊断里每张表只保留<b>最后一条</b>候选的拒绝原因，
     * 而你真正在意的那条（比如 enderium_plasma）可能排在窗口中间 —— 只看最后一条会把原因张冠李戴。
     */
    private final Map<GTRecipeType, Map<String, ScanReject>> scanRejectsByType = new java.util.IdentityHashMap<>();

    /** 已经打过一次性明细的配方表（避免刷屏）。 */
    private final java.util.Set<GTRecipeType> scanDumpDone =
            java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

    /** 本次 {@link #findBatchForType} 是否真的扫了（冷却/跳过时为 false，不参与多表原因汇总）。 */
    private boolean diagScannedThisCall;

    // ---------- 2026-10-30 扫描节流（大配方表空转时不要每 tick 全表扫） ----------
    /** 没有 ME 样板缓存时，一次 findBatch 最多试多少条配方。 */
    private static final int SCAN_BUDGET = 24;
    /** 整轮没找到配方后的冷却 tick 数（期间 findBatch 直接返回 null）。 */
    private static final int IDLE_COOLDOWN_TICKS = 10;

    /**
     * ★ 2026-11-03：<b>每张配方表各自的扫描游标与冷却</b>。
     *
     * <p>旧版是全局单份 {@code scanCursor/scanCooldownUntil} —— 单表时没问题，但跨表轮转时
     * <b>第一张表一轮扫完就把整机冷却 10 tick</b>，后面的表在 {@code now < cooldownUntil}
     * 分支直接返回、<b>根本没被扫到</b>（日志里「跨表=true」却只有第一张表的拒绝原因）。
     * 改成每表一份后，一轮 findBatch 里每张表都能真正扫到自己的分片。
     */
    private static final class TableScanState {
        int cursor;
        long cooldownUntil;
    }

    private final Map<GTRecipeType, TableScanState> scanStates = new java.util.IdentityHashMap<>();

    // ---------- ★ 2026-11-07：批次日志限流 + 停滞看门狗（玩家「部件组装机第一次下单会卡」） ----------

    /** 每 20 tick 最多打几条「开机/完成」INFO（其余降到 DEBUG，避免大单时刷屏拖慢服务器线程）。 */
    private static final int BATCH_LOG_PER_WINDOW = 4;
    private static final int BATCH_LOG_WINDOW_TICKS = 20;

    /** 批次停滞多久（tick）打一次 WARN；同一条机器最多每 200 tick 一行。 */
    private static final int STUCK_WARN_TICKS = 200;

    private long batchLogWindow = Long.MIN_VALUE;
    private int batchLogUsed;

    /** 上次进度总和 / 对应 tick（用于停滞检测）。 */
    private int lastProgressSum = -1;
    private long lastProgressSumTick = Long.MIN_VALUE;
    private long lastStuckWarnTick = Long.MIN_VALUE;

    /**
     * 取一个「开机/完成」INFO 名额（每 20 tick 最多 {@value #BATCH_LOG_PER_WINDOW} 条）。
     * 拿不到就降级成 DEBUG —— 大单时 1 tick 开 4 条批次也不会把主线程拖在同步日志 I/O 上。
     */
    private boolean takeBatchLogSlot() {
        long window = machine.self().getOffsetTimer() / BATCH_LOG_WINDOW_TICKS;
        if (window != batchLogWindow) {
            batchLogWindow = window;
            batchLogUsed = 0;
        }
        if (batchLogUsed >= BATCH_LOG_PER_WINDOW) {
            return false;
        }
        batchLogUsed++;
        return true;
    }

    /** 当前所有批次进度之和（停滞检测用）。 */
    private int progressSum() {
        int sum = 0;
        for (int i = 0; i < engine.size(); i++) {
            sum += engine.get(i).progress;
        }
        return sum;
    }

    /** 当前在跑批次数（显示用）。 */
    public int runningThreadCount() {
        return engine.size();
    }

    /** 当前所有在跑批次的并行之和（显示用）。 */
    public long totalRunsAtOnce() {
        long sum = 0L;
        for (int i = 0; i < engine.size(); i++) {
            sum += Math.max(1, engine.get(i).recipe.parallels);
            if (sum < 0) {
                return Long.MAX_VALUE;
            }
        }
        return sum;
    }

    /** 当前在跑批次占用的并行之和（「独立线程」关闭时用来算剩余共享额度）。 */
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

    /** 「并行重复配方」关闭时：同一条配方是否已被别的线程占用。 */
    private boolean isRecipeRunningElsewhere(GTRecipe raw) {
        if (raw == null || raw.getId() == null) {
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

    /**
     * ★ 2026-11-07：<b>立刻清掉扫描冷却</b>（下一次 {@code findBatch} 马上重新扫）。
     *
     * <p>AE 推样板 / 换电路 / 换模式后调用：旧版要等 {@link #IDLE_COOLDOWN_TICKS} 才重扫，
     * 大单首件会被玩家感知成「下单卡住」；现在推送成功当 tick 就能开始找配方。
     */
    public void pokeScan() {
        for (TableScanState s : scanStates.values()) {
            s.cooldownUntil = 0;
        }
        diagLastLoggedReason = null;
        diagLines = 0;
    }

    /**
     * @param machine 必须同时实现 {@link IRecipeLogicMachine} 与 {@link IThreadedRecipeMachine}
     *                （否则构造时强转会抛 {@link ClassCastException}）
     */
    public ThreadedRecipeLogic(IRecipeLogicMachine machine) {
        super(machine);
        this.threaded = (IThreadedRecipeMachine) machine;
    }

    /**
     * 每个 server tick：<b>暂停就整 tick 不动 → 驱动引擎 → 展示回写</b>。
     *
     * <p>暂停（{@link #isSuspend()}）时直接返回：不推进、不摘批次、不吞料（与基类一致）。
     *
     * <p><b>绝不调 {@code super.serverTick()}</b>：原因见类注释（否则会抢配方、抢扣料、
     * 并且空闲自退订会摘掉订阅）。
     */
    @Override
    public void serverTick() {
        if (isSuspend()) {
            return;
        }
        // 多方块部件级的「每 tick 工作钩子」（维护仓/消声仓/模块等）：GTCEu 的单配方逻辑
        // 每 tick 在 handleRecipeWorking 里调一次 machine.onWorking()，返回 false ⇒ 整机等待。
        // 本引擎有 N 条批次，但部件钩子只能按机器每 tick 调一次（调 N 次会把部件 upkeep 放大 N 倍）。
        if (engine.size() > 0 && !machine.onWorking()) {
            setWaiting(null);
            return;
        }
        // ①②③：逐条推进 → 摘掉涨满的（先摘表再收尾出料）→ 有空位就找配方并原子扣料
        engine.tick(new HostImpl());

        // ③.4 停滞看门狗（★ 2026-11-07）：引擎里还有批次、却连续 200 tick 一点进度都没有 ⇒ 打 WARN。
        //   玩家报「部件组装机第一次下单会卡、调一个电路就不卡」：换模式/换电路会 resetRecipeLogic()
        //   把批次清掉，于是机器恢复；但旧日志里看不出批次是卡在「每 tick 输入不足」还是「输出/别的」。
        //   这里把第 0 条批次的配方/进度/每 tick 输入是否够 都写出来，下一次复现直接定位。
        int progressNow = progressSum();
        long nowTick = machine.self().getOffsetTimer();
        if (progressNow != lastProgressSum) {
            lastProgressSum = progressNow;
            lastProgressSumTick = nowTick;
        } else if (engine.size() > 0 && lastProgressSumTick != Long.MIN_VALUE
                && nowTick - lastProgressSumTick >= STUCK_WARN_TICKS
                && (lastStuckWarnTick == Long.MIN_VALUE || nowTick - lastStuckWarnTick >= STUCK_WARN_TICKS)) {
            lastStuckWarnTick = nowTick;
            ConcurrentRecipeEngine.Batch primary = engine.get(0);
            boolean tickInputOk;
            try {
                tickInputOk = primary.recipe.matchTickRecipe(machine).isSuccess();
            } catch (Throwable t) {
                tickInputOk = false;
            }
            GTLModular.LOGGER.warn("[gtlmodular] 线程引擎批次停滞 @{}：{} 条批次已 {} tick 没有进度；"
                            + "第0条 {} 进度 {}/{}，每tick输入={}。"
                            + "（换模式/换电路会 resetRecipeLogic 清空批次，已扣料不退）",
                    machine.self().getPos(), engine.size(), nowTick - lastProgressSumTick,
                    primary.recipe.getId(), primary.progress, primary.duration,
                    tickInputOk ? "够" : "不足");
        }
        // ③.5 诊断：空转时每 100 tick 打一行「为什么没配方」。
        //   ★ 2026-11-03：每个不同的真实原因最多 12 行；原因不变不重复刷，
        //   换原因重新计数；扫描冷却不再覆盖原因（见 diagReject 的注释）。
        if (engine.size() == 0 && machine.self().getOffsetTimer() % 100L == 0L) {
            if (!java.util.Objects.equals(diagReject, diagLastLoggedReason)) {
                diagLastLoggedReason = diagReject;
                diagLines = 0;
            }
            if (diagLines < 12) {
                diagLines++;
                GTLModular.LOGGER.info("[gtlmodular] 线程引擎诊断 {} @{}：成型={} 跨表={} 扫描={} 候选={} 拒绝={}",
                        machine.getRecipeType().registryName, machine.self().getPos(),
                        machine.self() instanceof com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController c
                                ? c.isFormed() : true,
                        isCrossTable(), diagScannedType,
                        diagCandidates, diagDetail());
            }
        }
        // ④ 展示回写：把第 0 条批次的进度写进基类的同步字段（GUI/Jade/TOP 由此看到进度条）
        if (engine.size() > 0) {
            ConcurrentRecipeEngine.Batch primary = engine.get(0);
            this.lastRecipe = primary.recipe;
            this.progress = primary.progress;
            this.duration = primary.duration;
            if (!isWorking()) {
                setStatus(Status.WORKING);
            }
        } else {
            this.progress = 0;
            this.duration = 0;
            if (!isIdle()) {
                setStatus(Status.IDLE);
            }
        }
    }

    /**
     * 只读「是否在干活」：基类字段 {@code isActive} 是 private 且无 setter，子类写不进去
     * ⇒ 覆写这个 public 读数口，用「批次表非空」回答同一个问题
     * （与「把 {@code isActive} 写成 true」语义一致，见类注释）。
     *
     * <p>批次表只在服务端有内容（不参与网络同步）⇒ 客户端上本方法恒为 false，与基线行为一致。
     */
    @Override
    public boolean isActive() {
        return engine.size() > 0;
    }

    /**
     * ★ 2026-11-05：当前线程引擎里还在跑的批次条数（含「等每 tick 输入」而暂停推进的批次）。
     *
     * <p>给样板总成换模式前的安全闸用：{@code isWorking()} 在本引擎上可能为 false
     * （批次等 EU / 等料时 {@link #serverTick()} 在 {@code onWorking()} 处提前 return，
     * 没走到写 {@code Status.WORKING} 那一步），但批次表非空 ⇒ 此时 {@code resetRecipeLogic()}
     * 会把批次连同已扣的料一起清掉（引擎明确「不退还」）。
     * 判「能不能换模式」必须看这个数，不能只看状态。
     */
    public int runningBatchCount() {
        return engine.size();
    }

    /**
     * 结构失形 / 手动复位时清空批次表。
     *
     * <p>GTCEu 的 {@code WorkableMultiblockMachine.onStructureInvalid()} 会调
     * {@link RecipeLogic#resetRecipeLogic()}；基类只清它自己的字段，<b>不会</b>知道本类的批次表，
     * 所以这里必须补一句清表，否则失形后旧批次还挂在表里（重形成后会用旧扣料继续跑）。
     * 已经扣掉的料<b>不退回</b>（与基类单配方语义一致）。
     */
    @Override
    public void resetRecipeLogic() {
        super.resetRecipeLogic();
        engine.clear();
        scanStates.clear();
        scanRejectsByType.clear();
        scanDumpDone.clear();
    }

    /**
     * 存自定义持久化数据：先让基类照旧存它自己的字段，再把批次表写成一个<b>追加键</b>
     * （{@link ConcurrentRecipeEngine#TAG_BATCHES}）⇒ 不新增任何 {@code @Persisted} 字段。
     */
    @Override
    public void saveCustomPersistedData(CompoundTag tag, boolean forDrop) {
        super.saveCustomPersistedData(tag, forDrop);
        engine.save(tag);
    }

    /**
     * 读自定义持久化数据：先让基类读回它自己的字段，再让引擎<b>清空并重建</b>批次表。
     *
     * <p>缺键／空表／单条坏档都由引擎内部安全处理（只跳过坏的那条，绝不外抛异常）。
     */
    @Override
    public void loadCustomPersistedData(CompoundTag tag) {
        super.loadCustomPersistedData(tag);
        scanStates.clear();
        scanRejectsByType.clear();
        scanDumpDone.clear();
        engine.load(tag);
    }

    /**
     * 引擎的六个问题在本类上的答案。
     *
     * <p>只做转发与配方试算：<b>唯一的写世界点</b>是 {@link #consumeInputs}（开批次时扣料），
     * {@link #tickInputs} 扣每 tick 的输入（EU／tick 流体），{@link #finishOutputs} 出料。
     */
    private final class HostImpl implements ConcurrentRecipeEngine.Host {

        /** 上次成功开出配方的配方表下标（独立线程跨表轮转的起点，保证公平）。 */
        private int lastSuccessfulTypeIdx = 0;

        /** 宿主机器：就是基类的 {@code machine} 字段（{@code IRecipeLogicMachine}）。 */
        @Override
        public IRecipeLogicMachine machine() {
            return ThreadedRecipeLogic.this.machine;
        }

        /** 同时能跑几条 = 线程仓当前设定值；没装线程仓才退回机器自己的固定值。 */
        @Override
        public int slots() {
            return Math.max(1, threaded.effectiveThreads());
        }

        /**
         * 找一条能开的新配方：<b>配方修改器 → 并行 → 输入/每 tick 输入/条件三重匹配</b>，
         * 全部通过才返回（此时<b>还没扣料</b>）。
         *
         * <p>并行 = {@code max(1, parallelMultiplier()) × max(1, 并行仓并行数)}。
         * 候选来自本机配方类型（{@code machine.getRecipeType()}）的查表迭代器，跳过燃油配方
         * （燃油走基类 {@code handleFuelRecipe} 那一路，本引擎不处理）。
         */
        /**
         * ★ 2026-11-02 队长口径（独立线程 = 每条线程一台虚拟机器）：
         * 独立线程开启时遍历机器的**全部配方表**（从上次成功的表开始轮转，保证公平），
         * 第一张能开的配方就返回；关闭时行为与旧版完全一致（只用当前选中表）。
         */
        @Override
        public GTRecipe findBatch() {
            GTRecipeType current = ThreadedRecipeLogic.this.machine.getRecipeType();
            if (current == null) {
                return null;
            }
            GTRecipeType[] types = ThreadedRecipeLogic.this.machine.getRecipeTypes();
            // ★ 2026-11-07 GTO 口径：线程仓的「独立线程」= 每条线程独立拉满功率，
            //   不再管「用哪张配方表」。装仓后只要机器有 2 张以上表，就自动全部扫（公平轮转）。
            boolean allTypes = types != null && types.length > 1;
            if (!allTypes) {
                return findBatchForType(current);
            }
            int n = types.length;
            int startIdx = lastSuccessfulTypeIdx % n;
            int candidatesSum = 0;
            List<String> perTable = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                int idx = (startIdx + i) % n;
                diagScannedThisCall = false;
                GTRecipe recipe = findBatchForType(types[idx]);
                if (recipe != null) {
                    lastSuccessfulTypeIdx = idx;
                    return recipe;
                }
                if (diagScannedThisCall) {
                    candidatesSum += diagCandidates;
                    // ★ 2026-11-03：每张表扫完就把它自己的「原因 + 配方 + 缺什么」定稿，
                    //   否则下一张表会把上一张的拒绝原因覆盖掉（旧版日志只剩最后一张表）。
                    perTable.add(String.valueOf(types[idx].registryName) + " → " + diagDetail());
                    diagRecipe = null;
                    diagExtra = null;
                }
            }
            if (!perTable.isEmpty()) {
                diagCandidates = candidatesSum;
                diagReject = "全部配方表都无可用配方（共 " + n + " 张）｜" + String.join("；", perTable);
                diagRecipe = null;
                diagExtra = null;
            }
            // 每张表都在冷却 / 都被跳过时：diagReject 保持上一次的真实原因，不覆盖、也不嵌套。
            return null;
        }

        /** 单张配方表的候选搜索（原 findBatch 的完整逻辑）。 */
        private GTRecipe findBatchForType(GTRecipeType type) {
            long now = machine.self().getOffsetTimer();
            TableScanState state = scanStates.computeIfAbsent(type, t -> new TableScanState());
            if (now < state.cooldownUntil) {
                // ★ 2026-11-03：**不覆盖** diagReject —— 冷却期间保留上一次真实原因，
                //   否则日志每 100 tick 只会打「扫描冷却中」，永远看不到为什么没配方。
                //   冷却也是**每张表各自一份**（见 TableScanState 的注释）。
                return null;
            }
            diagScannedType = String.valueOf(type.registryName);
            diagScannedThisCall = true;
            diagCandidates = 0;
            // 清掉上一张表的配方/附加原因，避免这一张表「跳过」时把别人的配方带进诊断。
            diagRecipe = null;
            diagExtra = null;

            // ① ME 样板仓（猪咪样板总成）已解码的配方：只含机器上样板对应的几条，
            //    直接拿来当候选，不再每次扫整张表。
            List<GTRecipe> candidates = meCachedCandidates(type);
            boolean fromMeCache = !candidates.isEmpty();
            int start = 0;
            int limit;
            if (fromMeCache) {
                limit = candidates.size();
            } else {
                // ② 没有任何物品/流体输入 ⇒ 全表扫也一定不匹配，直接跳过（空转延迟归零）。
                if (!hasAnyItemOrFluidInput()) {
                    reject("无物品/流体输入（跳过扫描）｜" + describeInputCaps(), null, null);
                    state.cooldownUntil = now + IDLE_COOLDOWN_TICKS;
                    return null;
                }
                // 没有 ME 样板仓（或缓存为空）才退回整表；分片扫描，避免大表每 tick 全扫。
                candidates = type.getLookup().getLookup().getRecipes(true)
                        .filter(r -> !r.isFuel()).toList();
                int total = candidates.size();
                if (total == 0) {
                    reject("候选=0（配方表为空）", null, null);
                    return null;
                }
                start = state.cursor % total;
                limit = Math.min(SCAN_BUDGET, total);
            }

            for (int i = 0; i < limit; i++) {
                int idx = fromMeCache ? i : (start + i) % candidates.size();
                GTRecipe raw = candidates.get(idx);
                diagCandidates++;
                // ★ 2026-11-07 GTO 口径：「并行重复配方」关 ⇒ 同一配方只许占一条线程。
                //   放到引擎里做，机器类不用再各写一份 fullModifyRecipe 去重。
                if (!threaded.threadHatchRepeatedRecipes() && isRecipeRunningElsewhere(raw)) {
                    noteScanReject(type, raw, "并行重复配方关闭：该配方已在别的线程", null);
                    continue;
                }
                // 先用「原配方」做一次只读匹配：物品/流体不够就不必再 copy/超频/精确并行
                // （并行只会放大输入，原配方都不够 ⇒ 放大后一定不够）。
                if (!RecipeRunnerHelper.matchRecipeInput(ThreadedRecipeLogic.this.machine, raw)) {
                    noteScanReject(type, raw, "原料不足(含ME样板仓)", null);
                    continue;
                }
                GTRecipe modified = ThreadedRecipeLogic.this.machine.fullModifyRecipe(
                        raw.copy(), ThreadedRecipeLogic.this.ocParams, ThreadedRecipeLogic.this.ocResult);
                if (modified == null) {
                    noteScanReject(type, raw, "配方修改器返回 null（去重/门槛拒绝）", null);
                    continue;
                }
                // 普通多方块 = 线程倍率 × 并行仓；巨构（usesParallelHatch=false）只看线程倍率。
                // long 乘再钳到 int：温度倍率可能很大，不能把 int 乘爆成负数。
                long multLong = Math.max(1, threaded.parallelMultiplier());
                if (threaded.usesParallelHatch()) {
                    multLong *= Math.max(1, parallelHatchCount());
                }
                int perThread = (int) Math.min(Integer.MAX_VALUE, multLong);
                // ★ 2026-11-07 GTO 口径「独立线程」：
                //   开 = 每条线程各自一份完整并行/功率额度（perThread）；
                //   关 = 所有线程共用同一份额度（在跑批次的并行之和不得超过 perThread）。
                int parallelBudget = perThread;
                if (!threaded.threadHatchIndependent()) {
                    int used = runningParallelSum();
                    parallelBudget = perThread - used;
                    if (parallelBudget <= 0) {
                        noteScanReject(type, raw, "独立线程关闭：并行额度已被在跑线程占满", null);
                        continue;
                    }
                }
                Pair<GTRecipe, Integer> par = GTRecipeModifiers.accurateParallel(
                        ThreadedRecipeLogic.this.machine.self(), modified, parallelBudget, false);
                GTRecipe scaled = par.getFirst();
                if (scaled == null) {
                    noteScanReject(type, raw, "并行计算返回 null", null);
                    continue;
                }
                // 只判输入（GTCEu 原生 RecipeLogic 也是「输入够就开跑」，输出空间在收尾时处理，
                // 否则没放输出仓的机器会永远开不了批次）。
                if (!RecipeRunnerHelper.matchRecipeInput(ThreadedRecipeLogic.this.machine, scaled)) {
                    noteScanReject(type, scaled, "物品/流体不匹配(含ME样板仓)", null);
                    continue;
                }
                GTRecipe.ActionResult tickResult = scaled.matchTickRecipe(ThreadedRecipeLogic.this.machine);
                if (!tickResult.isSuccess()) {
                    noteScanReject(type, scaled, "每tick输入不足(EU等)", reasonText(tickResult));
                    continue;
                }
                GTRecipe.ActionResult conditionResult = scaled.checkConditions(ThreadedRecipeLogic.this);
                if (!conditionResult.isSuccess()) {
                    noteScanReject(type, scaled, "配方条件不满足", reasonText(conditionResult));
                    continue;
                }
                diagReject = "有配方可跑";
                diagRecipe = null;
                diagExtra = null;
                state.cooldownUntil = 0;
                if (!fromMeCache) {
                    state.cursor = (idx + 1) % candidates.size();
                }
                return scaled;
            }

            // 本轮没有结果：ME 缓存/整表都冷却一会儿，别在空转时每 tick 重复扫。
            boolean fullPass;
            if (fromMeCache) {
                state.cooldownUntil = now + IDLE_COOLDOWN_TICKS;
                fullPass = true;      // ME 样板缓存本身每次都全扫
            } else {
                int total = candidates.size();
                if (start + limit >= total) {
                    state.cooldownUntil = now + IDLE_COOLDOWN_TICKS;
                    state.cursor = 0;
                    fullPass = true;  // 分片轮扫已经覆盖完整张表
                } else {
                    state.cursor = (start + limit) % total;
                    fullPass = false;
                }
            }
            if (diagCandidates == 0) {
                reject("候选=0（查表没命中：物品/流体/电路是否齐全）", null, null);
            }
            if (fullPass) {
                // 整张表覆盖完仍一条没跑起来 ⇒ 逐条原因一次性打出来（每表每次世界加载只一次）
                dumpScanDetail(type);
            }
            return null;
        }


        /**
         * 原子扣料：{@code beforeWorking} → {@code preWorking} → 扣输入。
         *
         * <p>扣不齐（{@code handleRecipeIO(IN)} 返回 false）时调 {@code afterWorking} 收尾并返回 false
         * ⇒ 引擎不收这条批次（<b>绝不吞料</b>）。
         */
        @Override
        public boolean consumeInputs(GTRecipe recipe) {
            if (!ThreadedRecipeLogic.this.machine.beforeWorking(recipe)) {
                reject("beforeWorking 拒绝（线圈温度/配方门槛）: " + recipe.getId(), null, null);
                return false;
            }
            recipe.preWorking(ThreadedRecipeLogic.this.machine);
            if (!RecipeRunnerHelper.handleRecipeInput(ThreadedRecipeLogic.this.machine, recipe)) {
                reject("扣料失败(输入仓/ME样板仓不够): " + recipe.getId(), null, null);
                ThreadedRecipeLogic.this.machine.afterWorking();
                return false;
            }
            // ★ 2026-11-07：日志限流（每 20 tick 最多 4 条 INFO，其余 DEBUG）——
            //   大单时 1 tick 开数条批次，逐条 INFO 同步写盘会放大服务器卡顿。
            if (takeBatchLogSlot()) {
                GTLModular.LOGGER.info("[gtlmodular] 线程引擎开机 @{}：{}", machine.self().getPos(), recipe.getId());
            } else {
                GTLModular.LOGGER.debug("[gtlmodular] 线程引擎开机 @{}：{}", machine.self().getPos(), recipe.getId());
            }
            for (TableScanState s : scanStates.values()) {
                s.cooldownUntil = 0;
            }
            return true;
        }

        /**
         * 每 tick 的输入：先<b>模拟</b>匹配（{@code matchTickRecipe}，不写世界），
         * 匹配通过才真扣这一 tick 的输入。不够返回 false ⇒ 该批次原地等。
         */
        @Override
        public boolean tickInputs(GTRecipe recipe) {
            if (!recipe.matchTickRecipe(ThreadedRecipeLogic.this.machine).isSuccess()) {
                return false;
            }
            return recipe.handleTickRecipeIO(IO.IN, ThreadedRecipeLogic.this.machine, getChanceCaches());
        }

        /** 批次收尾：机器收尾 → {@code postWorking} → 出料。 */
        @Override
        public void finishOutputs(GTRecipe recipe) {
            boolean outputOk;
            try {
                ThreadedRecipeLogic.this.machine.afterWorking();
                recipe.postWorking(ThreadedRecipeLogic.this.machine);
                outputOk = RecipeRunnerHelper.handleRecipeOutput(ThreadedRecipeLogic.this.machine, recipe);
            } catch (Throwable failure) {
                outputOk = false;
                GTLModular.LOGGER.warn("[gtlmodular] 线程批次收尾异常 @{}: {}",
                        machine.self().getPos(), failure.toString());
            }
            // ★ 2026-11-05（队长要求）：每条批次完成都留痕——“批次到底是跑完了还是被清掉了”再也不用猜。
            // ★ 2026-11-07：INFO 限流（每 20 tick 最多 4 条），其余降到 DEBUG；产物摘要只在 INFO 时算。
            if (takeBatchLogSlot()) {
                GTLModular.LOGGER.info("[gtlmodular] 线程批次完成 @{}：{}（输出处理={}，耗时={}t）产物={}",
                        machine.self().getPos(),
                        recipe == null || recipe.getId() == null ? "?" : recipe.getId(),
                        outputOk ? "成功" : "失败",
                        recipe == null ? "?" : recipe.duration,
                        describeOutputs(recipe));
            } else if (GTLModular.LOGGER.isDebugEnabled()) {
                GTLModular.LOGGER.debug("[gtlmodular] 线程批次完成 @{}：{}（输出处理={}，耗时={}t）",
                        machine.self().getPos(),
                        recipe == null || recipe.getId() == null ? "?" : recipe.getId(),
                        outputOk ? "成功" : "失败",
                        recipe == null ? "?" : recipe.duration);
            }
        }
    }

    // ================= ★ 2026-11-03 诊断增强（玩家：「为什么他说我输入材料不足」） =================

    /**
     * 记录一次<b>真实</b>拒绝：{@code reason} 进诊断行，{@code recipe}/{@code extra} 用来回答「缺什么」。
     *
     * <p>为什么单独存一份：扫描冷却（每轮空转 {@link #IDLE_COOLDOWN_TICKS} tick）期间
     * {@code findBatch} 会直接返回，旧版把原因改写成「扫描冷却中」⇒ 日志永远看不到真正的拒绝原因。
     */
    private void reject(String reason, GTRecipe recipe, String extra) {
        this.diagReject = reason;
        this.diagRecipe = recipe;
        this.diagExtra = extra;
    }

    /**
     * 扫描期拒绝：既更新「最近一次真实原因」，也把这条候选记进本表的逐条明细
     * （等整张表覆盖完仍无配方时一次性打日志，见 {@link #dumpScanDetail}）。
     *
     * <p>{@code extra} 有值（每 tick 输入 / 配方条件）时，不把配方塞进 {@code diagRecipe}
     * —— 那样诊断行会去做输入探测，答非所问；明细里仍按配方 id 记录。
     */
    private void noteScanReject(GTRecipeType type, GTRecipe recipe, String reason, String extra) {
        reject(reason, extra == null ? recipe : null, extra);
        if (type != null && recipe != null && recipe.getId() != null) {
            scanRejectsByType
                    .computeIfAbsent(type, t -> new java.util.LinkedHashMap<>())
                    .put(recipe.getId().toString(), new ScanReject(reason, recipe));
        }
    }

    /**
     * 一次性扫描明细：整张表覆盖完还是一条配方都没跑起来时，把每条候选的失败原因打进日志。
     *
     * <p>作用：跨表诊断里每张表正文只保留「最后一条」候选的原因，而你关心的那条
     * （例如 {@code enderium_plasma}）可能排在扫描窗口中间 —— 明细能逐条给出「原料不足 / 缺什么 /
     * 每 tick 输入 / 条件不满足」，不用再猜。每张表每次世界加载只打一次，最多 40 行。
     */
    private void dumpScanDetail(GTRecipeType type) {
        Map<String, ScanReject> rejects = scanRejectsByType.get(type);
        if (type == null || rejects == null || rejects.isEmpty() || !scanDumpDone.add(type)) {
            return;
        }
        GTLModular.LOGGER.info("[gtlmodular] 线程引擎扫描明细 {} @{}（整轮覆盖 {} 条，全部失败；每表仅首次打印）",
                type.registryName, machine.self().getPos(), rejects.size());
        int shown = 0;
        for (Map.Entry<String, ScanReject> entry : rejects.entrySet()) {
            if (shown >= 40) {
                GTLModular.LOGGER.info("[gtlmodular]   …（还有 {} 条未列出）", rejects.size() - 40);
                break;
            }
            shown++;
            ScanReject r = entry.getValue();
            String hint = "";
            if (r.reason() != null
                    && (r.reason().contains("原料不足") || r.reason().contains("物品/流体不匹配"))) {
                String missing = missingHint(r.recipe());
                if (missing != null && !missing.isEmpty()) {
                    hint = "｜缺=" + missing;
                }
            }
            GTLModular.LOGGER.info("[gtlmodular]   {} → {}{}", entry.getKey(), r.reason(), hint);
        }
        // 明细只打一次；清掉本表累积，避免换料后一直占内存。
        scanRejectsByType.remove(type);
    }

    /**
     * 跨配方表是否生效：★ 2026-11-07 起与「独立线程」开关解耦 ——
     * 线程本来就该各自挑不同配方，多表机器默认全部扫（GTO 同义）。
     */
    private boolean isCrossTable() {
        return true;
    }

    /** 诊断行正文：真实原因 +（输入不匹配时）配方 id + 缺物品还是缺流体。 */
    private String diagDetail() {
        StringBuilder sb = new StringBuilder(diagReject == null ? "?" : diagReject);
        if (diagRecipe != null) {
            sb.append("｜配方=").append(diagRecipe.getId());
            String hint = missingHint(diagRecipe);
            if (hint != null && !hint.isEmpty()) {
                sb.append("｜缺=").append(hint);
            }
        } else if (diagExtra != null && !diagExtra.isEmpty()) {
            sb.append("｜").append(diagExtra);
        }
        return sb.toString();
    }

    /** 产物摘要（前 4 项），给「线程批次完成」那行用。 */
    private static String describeOutputs(GTRecipe recipe) {
        if (recipe == null) {
            return "?";
        }
        try {
            List<String> out = new ArrayList<>();
            for (Content c : recipe.getOutputContents(ItemRecipeCapability.CAP)) {
                Object inner = ItemRecipeCapability.CAP.of(c.getContent());
                if (inner instanceof net.minecraft.world.item.crafting.Ingredient ingredient
                        && ingredient.getItems().length > 0) {
                    net.minecraft.world.item.ItemStack stack = ingredient.getItems()[0];
                    out.add(net.minecraft.core.registries.BuiltInRegistries.ITEM
                            .getKey(stack.getItem()).toString() + "×" + stack.getCount());
                }
            }
            for (Content c : recipe.getOutputContents(FluidRecipeCapability.CAP)) {
                Object inner = FluidRecipeCapability.CAP.of(c.getContent());
                if (inner instanceof com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient fluid
                        && fluid.getStacks().length > 0) {
                    out.add(net.minecraftforge.registries.ForgeRegistries.FLUIDS
                            .getKey(fluid.getStacks()[0].getFluid()).toString()
                            + "×" + fluid.getAmount() + "mB");
                }
            }
            if (out.isEmpty()) {
                return "无";
            }
            if (out.size() <= 4) {
                return String.join(" ", out);
            }
            return String.join(" ", out.subList(0, 4)) + " …(共 " + out.size() + " 项)";
        } catch (Throwable t) {
            return "?";
        }
    }

    /**
     * ★ 2026-11-05：**缺料明细** —— 把这条配方缺哪些物品/流体、各缺多少（id×数量）列出来。
     *
     * <p>做法：拿 GTCEu 的 IN handler 链做一次 <b>simulate</b> —— 每个 handler 依次尝试「吃掉」输入，
     * 最后剩下的就是<b>整机（含 ME 样板仓）拿不出来的部分</b>。只在打诊断时调用，不在找配方热路径。
     */
    private String describeMissingInputs(GTRecipe recipe) {
        try {
            List<String> missing = new ArrayList<>();
            collectMissing(recipe, ItemRecipeCapability.CAP, missing);
            collectMissing(recipe, FluidRecipeCapability.CAP, missing);
            if (missing.isEmpty()) {
                return "";
            }
            int limit = 8;
            if (missing.size() <= limit) {
                return String.join(" ", missing);
            }
            return String.join(" ", missing.subList(0, limit)) + " …(共 " + missing.size() + " 种)";
        } catch (Throwable t) {
            return "";
        }
    }

    private <T> void collectMissing(GTRecipe recipe, RecipeCapability<T> cap, List<String> out) {
        List<T> inputs = RecipeHelper.getInputContents(recipe, cap);
        if (inputs.isEmpty()) {
            return;
        }
        List<T> left = new ArrayList<>(inputs);
        List<IRecipeHandler<?>> handlers = machine.getCapabilitiesProxy().get(IO.IN, cap);
        if (handlers != null) {
            for (IRecipeHandler<?> handler : handlers) {
                if (left.isEmpty()) {
                    break;
                }
                @SuppressWarnings("unchecked")
                IRecipeHandler<T> typed = (IRecipeHandler<T>) handler;
                List<T> next = typed.handleRecipe(IO.IN, recipe, left, null, true);
                if (next != null) {
                    left = next;
                }
            }
        }
        for (T inner : left) {
            out.add(describeInput(inner));
        }
    }

    /** 把一个配方输入内容写成「物品:id×N」/「流体:id×NmB」。 */
    private static String describeInput(Object inner) {
        try {
            if (inner instanceof net.minecraft.world.item.crafting.Ingredient ingredient) {
                net.minecraft.world.item.ItemStack[] stacks = ingredient.getItems();
                String id = stacks.length == 0 ? "?"
                        : net.minecraft.core.registries.BuiltInRegistries.ITEM
                        .getKey(stacks[0].getItem()).toString();
                long count = ingredient instanceof com.gregtechceu.gtceu.api.recipe.ingredient.SizedIngredient sized
                        ? sized.getAmount()
                        : (stacks.length == 0 ? 1L : stacks[0].getCount());
                return "物品:" + id + "×" + count;
            }
            if (inner instanceof com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient fluid) {
                com.lowdragmc.lowdraglib.side.fluid.FluidStack[] stacks = fluid.getStacks();
                String id = stacks.length == 0 ? "?"
                        : net.minecraftforge.registries.ForgeRegistries.FLUIDS
                        .getKey(stacks[0].getFluid()).toString();
                return "流体:" + id + "×" + fluid.getAmount() + "mB";
            }
            return String.valueOf(inner);
        } catch (Throwable t) {
            return String.valueOf(inner);
        }
    }

    /**
     * 「缺什么」探测：用 GTCEu 原版 {@code matchRecipeContents} 再判一次输入，
     * 失败时取下它给的原因（格式 {@code 输入不足: item/fluid}，见 GTCEu 的
     * {@code gtceu.recipe_logic.insufficient_in} 文案）。
     *
     * <p>只在打诊断行时调用（每 100 tick 最多一次，且同一原因最多 12 行），不在找配方的热路径里。
     */
    private String missingHint(GTRecipe recipe) {
        // ★ 2026-11-05（队长要求）：先给**缺哪些物品/流体、各缺多少**（id + 数量），
        //   看不到缺什么就只能猜。探不出来才退回原版「输入不足: item/fluid」文案。
        String detailed = describeMissingInputs(recipe);
        if (!detailed.isEmpty()) {
            return detailed;
        }
        try {
            GTRecipe.ActionResult result = recipe.matchRecipeContents(
                    IO.IN, ThreadedRecipeLogic.this.machine, recipe.inputs, false);
            if (result.isSuccess()) {
                // 原版能过、gtlcore 通道过不去 ⇒ 多半是 ME 样板/库存总成这类自定义通道
                return "原版匹配通过（卡在 gtlcore 的 ME/样板通道）";
            }
            String text = reasonText(result);
            return text == null || text.isEmpty() ? "未知（原版匹配失败但不给原因）" : text;
        } catch (Throwable t) {
            return "探测异常:" + t.getClass().getSimpleName();
        }
    }

    /** {@code ActionResult.reason()} → 可读文本（取不到就给 {@code null}）。 */
    private static String reasonText(GTRecipe.ActionResult result) {
        try {
            if (result != null && result.reason() != null) {
                Component reason = result.reason().get();
                if (reason != null) {
                    // 服务端没有语言表：Component#getString() 直接回翻译键（例如
                    // "gtceu.recipe_logic.insufficient_in: item"）⇒ 这里换成中文再进日志，
                    // 免得玩家在日志里看到一坨键名。
                    return reason.getString()
                            .replace("gtceu.recipe_logic.insufficient_in", "输入不足")
                            .replace("gtceu.recipe_logic.insufficient_out", "输出不足")
                            .replace("gtceu.recipe_logic.insufficient", "不足")
                            .replace(": item", ": 物品")
                            .replace(": fluid", ": 流体");
                }
            }
        } catch (Throwable ignored) {
            // 原因本身取不到不影响诊断
        }
        return null;
    }

    /**
     * 并行仓并行数：<b>已成形多方块</b>的全部部件里，取最大的
     * {@link IParallelHatch#getCurrentParallel()}；没有并行仓（或未成形／不是多方块）就 1。
     */
    private int parallelHatchCount() {
        if (machine.self() instanceof IMultiController controller && controller.isFormed()) {
            int max = 1;
            List<IMultiPart> parts = controller.getParts();
            if (parts != null) {
                for (IMultiPart part : parts) {
                    if (part instanceof IParallelHatch hatch) {
                        max = Math.max(max, hatch.getCurrentParallel());
                    }
                }
            }
            return max;
        }
        return 1;
    }

    // ================= ★ 子类扩展点（接口 + 子类选择） =================

    /** 线程上限（线程仓当前设定值；没仓才用机器固定值；Jade/面板读取用）。 */
    public int threadLimit() {
        return Math.max(1, threaded.effectiveThreads());
    }

    /** 当前在跑的批次数（Jade/面板读取用）。 */
    public int runningBatches() {
        return engine.size();
    }

    /**
     * 每条在跑批次的只读快照（进度 / 已钉死的时长 / 并行份数），供 Jade 逐条画进度条。
     */
    public List<BatchView> snapshotBatches() {
        List<BatchView> list = new ArrayList<>(engine.size());
        for (int i = 0; i < engine.size(); i++) {
            ConcurrentRecipeEngine.Batch batch = engine.get(i);
            list.add(new BatchView(batch.recipe, batch.progress, batch.duration,
                    Math.max(1, batch.recipe.parallels)));
        }
        return list;
    }

    /** 一条批次快照：配方对象 / 进度 / 已钉死的时长 / 并行份数。 */
    public record BatchView(GTRecipe recipe, int progress, int duration, int parallels) {}

    /**
     * 本机可用的样板配方候选：<b>原版 gtlcore 的通道 + 槽级兜底</b>。
     *
     * <p>原版 {@code GTRecipeLookupMixin} 走的是
     * {@code IRecipeCapabilityMachine.getMEPatternRecipeHandleParts()} →
     * 每个 {@code MEPatternRecipeHandlePart.getCachedGTRecipe()}；这里照做。
     * 再用 {@code getSlot2RecipesCache()} 兜一层「{@code cacheRecipe[slot] && isActive()}
     * 门没置位」的槽（超级猪咪样板总成 640 槽 / 多页）。
     *
     * <p><b>子类可覆写</b>：某个总成/机器需要自定义候选来源时，覆写本方法；
     * 机器类在 {@code createRecipeLogic} 里返回对应的子类即可。
     */
    protected List<GTRecipe> meCachedCandidates(GTRecipeType type) {
        ObjectSet<GTRecipe> set = new ObjectOpenHashSet<>();
        // ① 原版通道：IRecipeCapabilityMachine.getMEPatternRecipeHandleParts()
        if (machine.self() instanceof IRecipeCapabilityMachine recipeMachine) {
            for (MEPatternRecipeHandlePart part : recipeMachine.getMEPatternRecipeHandleParts()) {
                if (part == null) {
                    continue;
                }
                ObjectSet<GTRecipe> cached = part.getCachedGTRecipe();
                if (cached != null) {
                    for (GTRecipe recipe : cached) {
                        addCandidate(set, recipe, type);
                    }
                }
            }
        }
        // ② 槽级兜底：直接遍历 IMEPatternPartMachine 的槽→配方缓存，绕过 cacheRecipe/isActive 门。
        if (machine.self() instanceof IMultiController controller && controller.isFormed()) {
            for (IMultiPart part : controller.getParts()) {
                if (part instanceof IMEPatternPartMachine me) {
                    var slotCache = me.getMETrait().getSlot2RecipesCache();
                    if (slotCache == null) {
                        continue;
                    }
                    for (ObjectSet<GTRecipe> recipes : slotCache.values()) {
                        if (recipes == null) {
                            continue;
                        }
                        for (GTRecipe recipe : recipes) {
                            addCandidate(set, recipe, type);
                        }
                    }
                }
            }
        }
        // ③ gtlcore「ME配方类型统合样板总成」：自动展开的配方不在库存槽缓存里，
        //    直接读它的已展开结果补进候选（见 AutoRecipePatternCompat）。
        if (machine.self() instanceof IMultiController controller) {
            for (GTRecipe recipe : AutoRecipePatternCompat.expandedRecipes(controller)) {
                addCandidate(set, recipe, type);
            }
        }
        return new ArrayList<>(set);
    }

    protected void addCandidate(ObjectSet<GTRecipe> set, GTRecipe recipe, GTRecipeType type) {
        if (recipe != null && !recipe.isFuel() && recipe.getType() == type) {
            set.add(recipe);
        }
    }

    /**
     * 机器当前是否有任何物品/流体输入（只看 {@code IO.IN} 的 item/fluid handler，不看 EU）。
     *
     * <p>空机器没必要每 tick 扫几百条配方——这是空转延迟的最后一块大头。
     * 只检查「有没有」，不检查「够不够」；够不够仍由后面的 {@code matchRecipeInput} 判。
     *
     * <p><b>子类可覆写</b>（某些总成把输入藏在自定义仓里）。
     */
    /** 机器结构里是否挂了 ME 样板仓（即使它的库存不在通用 handler 里）。 */
    private boolean hasMePatternPart() {
        try {
            if (machine.self() instanceof IMultiController controller && controller.isFormed()) {
                for (IMultiPart part : controller.getParts()) {
                    if (part instanceof IMEPatternPartMachine) {
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {
            // 拿不到部件表就不拦（宁可多扫）
        }
        return false;
    }

    /**
     * ★ 2026-11-05：「无物品/流体输入」时的定位信息 —— 列出这台机器到底有没有输入仓、
     * 仓里有没有东西（区分「没装输入仓」与「装了但是空的」）。
     */
    private String describeInputCaps() {
        try {
            Table<IO, RecipeCapability<?>, List<IRecipeHandler<?>>> proxy = machine.getCapabilitiesProxy();
            if (proxy == null) {
                return "能力表=null";
            }
            Map<RecipeCapability<?>, List<IRecipeHandler<?>>> in = proxy.row(IO.IN);
            if (in == null) {
                return "IN能力表=null";
            }
            int itemHandlers = 0;
            int fluidHandlers = 0;
            int itemContent = 0;
            int fluidContent = 0;
            for (Map.Entry<RecipeCapability<?>, List<IRecipeHandler<?>>> entry : in.entrySet()) {
                RecipeCapability<?> cap = entry.getKey();
                if (cap != ItemRecipeCapability.CAP && cap != FluidRecipeCapability.CAP) {
                    continue;
                }
                for (IRecipeHandler<?> handler : entry.getValue()) {
                    if (cap == ItemRecipeCapability.CAP) {
                        itemHandlers++;
                    } else {
                        fluidHandlers++;
                    }
                    for (Object content : handler.getContents()) {
                        if (content instanceof ItemStack stack && !stack.isEmpty()) {
                            itemContent++;
                        } else if (content instanceof FluidStack fluid && fluid.getAmount() > 0) {
                            fluidContent++;
                        }
                    }
                }
            }
            return "IN能力=" + in.size() + " 物品仓=" + itemHandlers + " 流体仓=" + fluidHandlers
                    + " 物品条目=" + itemContent + " 流体条目=" + fluidContent
                    + " 样板仓=" + (hasMePatternPart() ? "有" : "无");
        } catch (Throwable t) {
            return "输入仓探测异常:" + t.getClass().getSimpleName();
        }
    }

    protected boolean hasAnyItemOrFluidInput() {
        // ★ 2026-11-06（队长实机发现）：机器里挂了 ME 样板仓（猪咪超样/gtlcore 样板总成）时，
        //   仓里刚推来的料并不在通用 handler 的 {@code getContents()} 里
        //   ⇒ 只看 handler 会把「仓里有料」误判成「没有任何输入」而跳过扫描
        //   ⇒ 机器永远不接单。这里只要挂了样板仓就不跳过（ME 缓存命中时仍走快路径）。
        if (hasMePatternPart()) {
            return true;
        }
        Table<IO, RecipeCapability<?>, List<IRecipeHandler<?>>> proxy = machine.getCapabilitiesProxy();
        if (proxy == null) {
            return true; // 拿不到能力表就不跳过，宁可多扫也不卡死
        }
        Map<RecipeCapability<?>, List<IRecipeHandler<?>>> in = proxy.row(IO.IN);
        if (in == null || in.isEmpty()) {
            return false;
        }
        for (Map.Entry<RecipeCapability<?>, List<IRecipeHandler<?>>> entry : in.entrySet()) {
            RecipeCapability<?> cap = entry.getKey();
            if (cap != ItemRecipeCapability.CAP && cap != FluidRecipeCapability.CAP) {
                continue;
            }
            for (IRecipeHandler<?> handler : entry.getValue()) {
                for (Object content : handler.getContents()) {
                    if (content instanceof ItemStack stack) {
                        if (!stack.isEmpty()) {
                            return true;
                        }
                    } else if (content instanceof FluidStack fluid) {
                        if (fluid.getAmount() > 0) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }
}
