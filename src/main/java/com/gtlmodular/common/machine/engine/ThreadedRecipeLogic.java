package com.gtlmodular.common.machine.engine;

import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gtlmodular.GTLModular;
import com.gtlmodular.common.config.GTLModularConfig;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import net.minecraft.nbt.CompoundTag;
import org.gtlcore.gtlcore.api.machine.trait.IRecipeCapabilityMachine;
import org.gtlcore.gtlcore.api.machine.trait.MEPatternRecipeHandlePart;
import org.gtlcore.gtlcore.api.machine.trait.MEPart.IMEPatternPartMachine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * ★ <b>独立线程引擎 · 机器侧接法</b>：把 {@link ConcurrentRecipeEngine} 接到 GTCEu 的
 * {@link RecipeLogic} 上，让一台机器<b>同时推进多条配方</b>。
 *
 * <h2>本类只做四件事（其余按职责拆到同包的小类里）</h2>
 * <ol>
 *   <li><b>驱动</b>：{@link #serverTick} 每 tick 调一次 {@link ConcurrentRecipeEngine#tick}，
 *       内部类 {@link HostImpl} 回答引擎的六个问题；</li>
 *   <li><b>展示回写</b>：批次表非空时把<b>第 0 条</b>的 {@code recipe}／{@code progress}／{@code duration}
 *       写进基类的同步字段，并把状态置为 {@code WORKING}；空表则清零并置 {@code IDLE}
 *       ⇒ GUI／Jade／TOP 那套单进度条免费可用（只反映一条批次）；</li>
 *   <li><b>订阅</b>：按需订阅（有批次 / 有料 / 有 ME 样板仓 / {@code pokeScan} 强制时才挂 tick），
 *       由 {@link ThreadWakeSweeper} 兜底补订阅；</li>
 *   <li><b>存读档</b>：{@link #saveCustomPersistedData}／{@link #loadCustomPersistedData}
 *       转发给引擎（批次表是追加键，不新增任何 {@code @Persisted} 字段）。</li>
 * </ol>
 *
 * <h2>拆出去的四个协作者（形状对齐参考实现）</h2>
 * <table border="1">
 *   <tr><th>类</th><th>职责</th><th>对应旧实现</th></tr>
 *   <tr><td>{@link BatchFinder}</td><td>跨表找配方、分片扫描、并行额度换算</td><td>{@code HostImpl#findBatch}</td></tr>
 *   <tr><td>{@link BatchIo}</td><td>扣料 / 每 tick 输入 / 收尾出料 / 批次日志限流</td><td>{@code HostImpl#consumeInputs/tickInputs/finishOutputs}</td></tr>
 *   <tr><td>{@link BatchDiagnostics}</td><td>拒绝原因、逐条扫描明细、缺料探测</td><td>本类里成片的 diag 字段与方法</td></tr>
 *   <tr><td>{@link BatchWatchdog}</td><td>批次停滞 WARN（200 tick 无进度）</td><td>{@code serverTick} 里的看门狗段</td></tr>
 * </table>
 * <p>拆分只搬家、不换逻辑：字段、判断顺序、日志文本、限频规则与 0.3.344 逐字一致。
 *
 * <h2>为什么不调 {@code super.serverTick()}（★ 关键）</h2>
 * GTCEu 基类的 {@code serverTick()} 会在空闲时自己找配方、自己扣料、自己推进
 * {@code progress}，并会在表尾「空闲自退订」把 tick 订阅摘掉。本类要独占配方的挑选与扣料
 * （扣料只允许发生在 {@link ConcurrentRecipeEngine#tick} 的第③步、一条只扣一次），
 * 所以<b>整体覆写且从不调 {@code super.serverTick()}</b>。
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
 * {@link com.gregtechceu.gtceu.api.capability.IParallelHatch} 部件里最大的
 * {@code getCurrentParallel()}」，没有并行仓就是 1（见 {@link BatchFinder#parallelHatchCount()}）。
 *
 * @see IThreadedRecipeMachine
 * @see ConcurrentRecipeEngine
 */
public class ThreadedRecipeLogic extends RecipeLogic implements IThreadBatchLogic {

    /** 并发批次表（纯调度，不依赖任何机器类；见 {@link ConcurrentRecipeEngine}）。 */
    private final ConcurrentRecipeEngine engine = new ConcurrentRecipeEngine();

    /** 机器侧契约（构造时由传入的 machine 强转得到）。 */
    private final IThreadedRecipeMachine threaded;

    /** 找配方（跨表 / 分片 / 并行额度）。 */
    private final BatchFinder finder;

    /** 诊断（拒绝原因 / 扫描明细 / 缺料探测）。 */
    private final BatchDiagnostics diag;

    /** 批次与机器的交接（扣料 / 每 tick 输入 / 出料）。 */
    private final BatchIo io;

    /** 批次停滞看门狗。 */
    private final BatchWatchdog watchdog;

    /**
     * ★ 2026-11-10：<b>EU 能源缓冲</b>——配方每 tick 要的电先由它支付，每 N tick 才穿一次能源仓。
     * 配置 {@code threading.energyBuffer=false} 时为 {@code null}（逐 tick 直扣，旧行为）。
     */
    private final EnergyBufferHandler energyBuffer;

    /** ★ 2026-11-08：{@link #pokeScan()} 置位 ⇒ 下一次调度立即扫描（不等扫描冷却）。 */
    private boolean scanForced;

    /** ★ 2026-11-10：<b>电网取电身份</b>（放置者 UUID；能源缓冲优先从无线电网扣电）。 */
    private java.util.UUID gridUser;

    /** 电网身份存档键（追加键，不新增 @Persisted 字段）。 */
    private static final String TAG_GRID_USER = "gtlmodular_grid_user";

    /** 机器放置/右键时由机器类或事件转发（{@code IMachineLife.onMachinePlaced} / 右键）。 */
    public void setGridUser(java.util.UUID user) {
        if (user != null) {
            this.gridUser = user;
            try {
                machine.self().markDirty();
            } catch (Throwable ignored) {
                // 落盘失败只影响重启后是否保留电网身份
            }
        }
    }

    /** 当前电网取电身份（可能为 null ⇒ 只有能源仓可用）。 */
    public java.util.UUID getGridUser() {
        return this.gridUser;
    }

    /**
     * ★ 2026-11-08：<b>GTO 移植机的「原逻辑」</b>——只有
     * {@link IThreadedRecipeMachine#engineRequiresThreadHatch()} 为 true 的机器才会创建它。
     *
     * <p>没装线程仓时由它代跑（gtlcore 的 {@code MultipleRecipesLogic}，= 移植前的多方块逻辑）；
     * 装了线程仓才轮到本引擎接管。见 {@link #serverTick()} 的闸门与 {@link #tickOriginal()}。
     */
    private RecipeLogic original;

    /** 当前是不是引擎接管：true = 引擎；false = 原逻辑在跑（仅 {@link #original} != null 时可能为 false）。 */
    private boolean engineMode = true;

    /** ★ 2026-11-09：原逻辑的「允许工作」开关是否已同步过（构造期不能读，改到第一次代跑时同步）。 */
    private boolean originalWorkingSynced;

    /** ★ 2026-11-09：原逻辑创建失败（跨版本 mixin 强转类崩溃）⇒ 永不再试，留在引擎模式。 */
    private boolean originalCreateFailed;

    /**
     * @param machine 必须同时实现 {@link IRecipeLogicMachine} 与 {@link IThreadedRecipeMachine}
     *                （否则构造时强转会抛 {@link ClassCastException}）
     */
    public ThreadedRecipeLogic(IRecipeLogicMachine machine) {
        super(machine);
        this.threaded = (IThreadedRecipeMachine) machine;
        this.diag = new BatchDiagnostics(machine);
        this.finder = new BatchFinder(machine, threaded, this, engine, diag);
        this.io = new BatchIo(machine, this, finder, diag);
        this.watchdog = new BatchWatchdog();
        // ★ 2026-11-10：EU 能源缓冲（每 N tick 才调一次能源仓）。
        this.energyBuffer = GTLModularConfig.isThreadingEnergyBuffer()
                ? new EnergyBufferHandler(machine, GTLModularConfig.getThreadingEnergyBufferTicks(), this::getGridUser)
                : null;
        // ★ 2026-11-09：原逻辑改为「懒创建」——构造期机器还没初始化完，而且跨版本的
        //   gtlcore/gtladditions mixin 会在 MultipleRecipesLogic 构造里做强转
        //   （奈亚 0.3.358 的 ClassCastException）；放这里会把整个机器实例化打断。
        //   现在由 {@link #updateEngineGate()} 在「第一次真的需要它」时创建，失败就留在引擎模式。
        // ★ 2026-11-09：【不在构造期读 isWorkingEnabled()】
        //   机器此时还没初始化完（Enhancedcore 的 IV mixin 会经 RecipeLogic.isWorkingEnabled()
        //   去碰 IvNativeAccess，构造期它是 null ⇒ NPE 直接把游戏崩掉）。
        //   工作开关改为第一次由原逻辑代跑时同步（见 tickOriginal）。
        // ★ 2026-11-08（按需订阅）：登记到唤醒巡检器（WeakHashMap ⇒ 不阻止机器被回收）。
        ThreadWakeSweeper.register(this);
    }

    /**
     * ★ 2026-11-08：<b>「装线程仓才接管」的闸门</b>。
     *
     * <p>适用对象：{@link IThreadedRecipeMachine#engineRequiresThreadHatch()} 为 true 的
     * GTO 移植机（奈亚 / 高维等离子聚变核心）。结构里有线程仓 ⇒ 引擎接管；
     * 拆掉 / 没装 ⇒ 交回移植前的原逻辑（gtlcore {@code MultipleRecipesLogic}），
     * 引擎只把它的进度镜像到本类的同步字段（GUI/Jade/原生进度条照常）。
     *
     * <p>切换时清空另一方状态：交回原逻辑时清批次（已扣料不退，与 {@code resetRecipeLogic} 同义）；
     * 交回引擎时清原逻辑状态。检测每 tick 一次（部件表只读，成本与 {@code effectiveThreads()} 同级）。
     */
    private void updateEngineGate() {
        if (!threaded.engineRequiresThreadHatch()) {
            return;
        }
        boolean hatch = threaded.threadHatchThreads() > 0;
        if (hatch) {
            if (!engineMode) {
                engineMode = true;
                if (original != null) {
                    original.resetRecipeLogic();   // 交回引擎：原逻辑的进度/配方作废
                }
            }
            return;
        }
        // 没装仓 ⇒ 需要原逻辑：第一次真的要用时才创建（构造期不能建，见构造函数注释）。
        if (original == null && !originalCreateFailed) {
            try {
                original = threaded.createEngineFallbackLogic();
                if (original == null
                        && machine instanceof org.gtlcore.gtlcore.api.machine.multiblock.ParallelMachine parallelMachine) {
                    original = new OriginalRecipesLogic(parallelMachine);
                }
                if (original == null) {
                    // 机器既没自带原逻辑、也不是 ParallelMachine ⇒ 没有可回退的东西：
                    // 记下「已尝试」，别每 tick 再试一次。
                    originalCreateFailed = true;
                }
            } catch (Throwable t) {
                original = null;
                originalCreateFailed = true;
                GTLModular.LOGGER.warn(
                        "[gtlmodular] {} 的原逻辑无法创建（跨版本 mixin 强转），本机保持引擎模式：{}",
                        machine.self().getPos(), t.toString());
            }
        }
        if (original != null && engineMode) {
            engineMode = false;
            originalWorkingSynced = false;
            // ★ 2026-11-10：交回原逻辑前把引擎缓存的电退回能源仓（原逻辑不走缓冲）
            if (energyBuffer != null) {
                energyBuffer.flushBack();
            }
            engine.resetRecipeLogic();     // 交回原逻辑：批次清空（已扣料不退）
        }
    }

    /** 原逻辑是否正在代跑（只有「需要装仓才接管 + 当前没仓」时为 true）。 */
    private boolean originalActive() {
        return original != null && !engineMode;
    }

    /**
     * 原逻辑代跑一 tick，并把它的<b>公开读数</b>镜像进本类的同步字段。
     *
     * <p>为什么镜像而不是让 GUI 直接读它：本类才是 {@code machine.getRecipeLogic()}，
     * GTCEu 的进度条、Jade、TOP 读的都是本类字段；镜像后客户端拿到的也是同步字段（原逻辑不参与同步）。
     */
    private void tickOriginal() {
        // 第一次代跑时把「允许工作」开关同步给原逻辑（构造期不能碰，见构造函数注释）。
        if (!originalWorkingSynced) {
            originalWorkingSynced = true;
            try {
                original.setWorkingEnabled(isWorkingEnabled());
            } catch (Throwable t) {
                // 上游 mixin 偶发抛错也不能让机器停摆：保持原逻辑默认（允许工作）。
            }
        }
        original.serverTick();
        this.progress = original.getProgress();
        this.duration = original.getDuration();
        this.lastRecipe = original.getLastRecipe();
        this.totalContinuousRunningTime = original.getTotalContinuousRunningTime();
        Status status = original.getStatus();
        if (status != getStatus()) {
            setStatus(status);
        }
    }

    /**
     * 原逻辑外壳：gtlcore {@code MultipleRecipesLogic}，但<b>绝不自己订阅 tick</b>
     * —— 驱动权统一在 {@link ThreadedRecipeLogic#serverTick()}（否则同一个逻辑会被 tick 两次）。
     */
    private static final class OriginalRecipesLogic
            extends org.gtlcore.gtlcore.common.machine.trait.MultipleRecipesLogic {

        OriginalRecipesLogic(org.gtlcore.gtlcore.api.machine.multiblock.ParallelMachine machine) {
            super(machine);
        }

        @Override
        public void updateTickSubscription() {
            // 由通用引擎统一驱动，这里必须是空实现（双推会把进度/扣料翻倍）。
        }
    }

    /**
     * 每个 server tick：<b>推进引擎 → 停滞看门狗 → 空转诊断 → 展示回写 → 闲时退订</b>。
     *
     * <p>入口从「全局调度器统一调」改回 GTO 形状：<b>每台机器自己的 {@code serverTick}</b>，
     * 但订阅由 {@link #updateTickSubscription()} 按需管理 —— 没批次、没料、没 ME 样板仓时
     * 直接退订，不再每 tick 进回调。
     *
     * <p><b>绝不调 {@code super.serverTick()}</b>：原因见类注释（否则会抢配方、抢扣料、
     * 并且空闲自退订会摘掉订阅）。
     */
    @Override
    public void serverTick() {
        if (isSuspend()) {
            return;
        }
        // ★ 2026-11-08：先看闸门——没装线程仓的 GTO 移植机，这一 tick 由原逻辑代跑。
        updateEngineGate();
        if (originalActive()) {
            scanForced = false;
            tickOriginal();
            maybeIdleUnsubscribe();
            return;
        }
        scanForced = false;
        // ★ 2026-11-10：能源缓冲——安装检查 + 到点批量补能（每 N tick 才穿一次能源仓）。
        if (energyBuffer != null) {
            energyBuffer.tick();
        }
        // 多方块部件级的「每 tick 工作钩子」（维护仓/消声仓/模块等）：GTCEu 的单配方逻辑
        // 每 tick 在 handleRecipeWorking 里调一次 machine.onWorking()，返回 false ⇒ 整机等待。
        // 本引擎有 N 条批次，但部件钩子只能按机器每 tick 调一次（调 N 次会把部件 upkeep 放大 N 倍）。
        if (engine.size() > 0 && !machine.onWorking()) {
            setWaiting(null);
            return;
        }
        // ①②③：逐条推进 → 摘掉涨满的（先摘表再收尾出料）→ 有空位就找配方并原子扣料
        finder.beginTick();
        engine.tick(new HostImpl());

        // ③.4 停滞看门狗（★ 2026-11-07）：引擎里还有批次、却连续 200 tick 一点进度都没有 ⇒ 打 WARN。
        watchdog.tick(engine, machine);
        // ③.5 诊断：空转时每 100 tick 打一行「为什么没配方」。
        diag.logIdle(isCrossTable(), engine.size());
        // ④ 展示回写：把第 0 条批次的进度写进基类的同步字段（GUI/Jade/TOP 由此看到进度条）
        writeDisplay();
        // ★ 2026-11-08（按需订阅）：本 tick 收尾时，空表 + 无料 + 没被强制 ⇒ 退订。
        maybeIdleUnsubscribe();
    }

    /** ④ 展示回写：第 0 条批次 → 基类同步字段；空表 → 清零并置 IDLE。 */
    private void writeDisplay() {
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
     * ★ 2026-11-08（GTO 口径）：<b>按需订阅</b> —— 只在「有活 / 可能有活」时挂在服务器 tick 上。
     *
     * <p>规则（{@link #wantsTick()}）：
     * <ul>
     *   <li>暂停 / 配方逻辑不可用 ⇒ 不订阅；</li>
     *   <li>引擎里有批次 / {@link #pokeScan()} 强制 ⇒ 订阅（推进与首件不能断）；</li>
     *   <li>空表但有物品/流体输入（含 ME 样板仓） ⇒ 订阅（可能接单，要继续扫）；</li>
     *   <li>空表且无料 ⇒ <b>退订</b>（“只调度用到的线程”就是这一步）。</li>
     * </ul>
     *
     * <p>唤醒口（实证，见 `_census/_javap_simple_recipelogic.txt` / `_javap_simple_handleparts.txt`）：
     * 基类 {@code onMachineLoad()} / {@code resetRecipeLogic()} / {@code setStatus()} 会调本方法；
     * GTCEu 多方块在 {@code onStructureFormed()} 里给每个配方 handler 注册了
     * {@code recipeLogic::updateTickSubscription} 的 changed-listener ⇒ <b>玩家放料/抽料会叫醒</b>；
     * gtlcore 样板仓的 buffer-changed listener 也指向同一个方法 ⇒ <b>AE 推料/写样板会叫醒</b>；
     * 切配方表（gtlcore {@code afterSetActiveRecipeType}）同样会调。
     *
     * <p>兜底：{@link ThreadWakeSweeper} 每 N tick 巡检一次“没订阅但该订阅”的机器，
     * 防漏唤醒导致永久不接单。
     */
    @Override
    public void updateTickSubscription() {
        // 只有“机器自己的 recipeLogic 就是本对象”时才由我们管理订阅。
        // NAC 的类型外壳（NACThreadedRecipeLogic）把本对象当 delegate：机器订阅的是外壳，
        // 外壳的 serverTick() 再调 delegate.serverTick() —— 这里绝不能给 delegate 再挂一份，
        // 否则同一条逻辑会被 tick 两次。
        if (!ownsTickSubscription()) {
            return;
        }
        if (wantsTick()) {
            if (subscription == null || !subscription.isStillSubscribed()) {
                subscription = machine.self().subscribeServerTick(this::serverTick);
                // ★ 2026-11-08：重新挂上 tick = 刚有料/刚被唤醒（放料、推样板、换模式、结构成形…
                //   基类这些路径都会调本方法）⇒ 立刻清空扫描退避，保证首件不被空转退避拖慢。
                finder.clearCooldowns();
            }
        } else if (subscription != null) {
            subscription.unsubscribe();
            subscription = null;
        }
    }

    /** 机器自己的 {@code getRecipeLogic()} 是不是本对象（NAC 外壳的 delegate 返回 false）。 */
    private boolean ownsTickSubscription() {
        return machine.getRecipeLogic() == this;
    }

    /** 本 tick 要不要被 tick（= 要不要订阅）。 */
    private boolean wantsTick() {
        if (isSuspend() || !machine.isRecipeLogicAvailable()) {
            return false;
        }
        if (!GTLModularConfig.isThreadingDemandSubscribe()) {
            return true;   // 关掉按需订阅 ⇒ 与旧版一致：常开
        }
        if (engine.size() > 0 || scanForced) {
            return true;
        }
        // ★ 2026-11-08：原逻辑代跑时，只要它在工作/等待，就必须保持订阅（否则半途停摆）。
        if (originalActive() && (original.isWorking() || original.isWaiting() || original.isActive())) {
            return true;
        }
        return hasAnyItemOrFluidInput();
    }

    /** 巡检器用：当前没订阅、但按规则应该订阅。 */
    boolean needsWake() {
        if (!GTLModularConfig.isThreadingDemandSubscribe() || !ownsTickSubscription()) {
            return false;
        }
        if (subscription != null && subscription.isStillSubscribed()) {
            return false;
        }
        return isServerAlive() && wantsTick();
    }

    /** 空闲收尾：空表 + 无料 + 没被强制 ⇒ 交给 {@link #updateTickSubscription()} 退订。 */
    private void maybeIdleUnsubscribe() {
        if (!GTLModularConfig.isThreadingDemandSubscribe() || !ownsTickSubscription()) {
            return;
        }
        // ★ 2026-11-08：原逻辑还在跑 ⇒ 绝不退订（它没有自己的 tick 订阅）。
        if (originalActive() && (original.isWorking() || original.isWaiting())) {
            return;
        }
        if (engine.size() == 0 && !scanForced && !hasAnyItemOrFluidInput()) {
            updateTickSubscription();
        }
    }

    /** 巡检器用：机器还在服务端世界里（空指针/客户端都不碰）。 */
    boolean isServerAlive() {
        var self = machine.self();
        return self != null && self.getLevel() != null && !self.getLevel().isClientSide;
    }

    /** 巡检日志用：机器坐标（拿不到就写 unknown，绝不再抛异常）。 */
    Object sweepPos() {
        try {
            var self = machine.self();
            return self == null ? "unknown" : self.getPos();
        } catch (Throwable t) {
            return "unknown";
        }
    }

    /**
     * ★ 2026-11-07：<b>立刻清掉扫描冷却</b>（下一次 {@code findBatch} 马上重新扫）。
     *
     * <p>AE 推样板 / 换电路 / 换模式后调用：旧版要等 {@code IDLE_COOLDOWN_TICKS} 才重扫，
     * 大单首件会被玩家感知成「下单卡住」；现在推送成功当 tick 就能开始找配方。
     */
    public void pokeScan() {
        finder.clearCooldowns();
        diag.onPoke();
        // ★ 2026-11-08：原逻辑代跑时，AE 推样板 / 换电路同样要叫醒它（它不是本引擎的批次表）
        if (originalActive()) {
            original.markLastRecipeDirty();
        }
        // ★ 2026-11-08（按需订阅）：强制本 tick 扫描 + 立即重订 —— AE 推样板 / 换电路后要当 tick 开扫，
        //   不能等巡检器（默认 10 tick）。updateTickSubscription() 内部按 wantsTick() 决定订阅。
        scanForced = true;
        updateTickSubscription();
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
        // ★ 2026-11-08：原逻辑代跑时，它的「在干活」同样要如实反映（原生进度条/机器面板读这里）。
        return engine.size() > 0 || (originalActive() && original.isActive());
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
        // ★ 2026-11-08：原逻辑代跑时没有「引擎批次」，返回 0（换模式安全闸看的是引擎批次，不误判）。
        return originalActive() ? 0 : engine.size();
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
        // ★ 2026-11-10：复位时把缓存的电退回能源仓（不能凭空留/丢）
        if (energyBuffer != null) {
            energyBuffer.flushBack();
        }
        // ★ 2026-11-08：原逻辑也要一起复位（否则失形/换模式后它还挂着旧进度）
        if (original != null) {
            original.resetRecipeLogic();
        }
        finder.reset();
        diag.resetForReload();
    }

    /**
     * 存自定义持久化数据：先让基类照旧存它自己的字段，再把批次表写成一个<b>追加键</b>
     * （{@link ConcurrentRecipeEngine#TAG_BATCHES}）⇒ 不新增任何 {@code @Persisted} 字段。
     */
    @Override
    public void saveCustomPersistedData(CompoundTag tag, boolean forDrop) {
        // ★ 2026-11-10：存档/卸载前把缓存的电退回能源仓（缓存不跨读档）
        if (energyBuffer != null) {
            energyBuffer.flushBack();
        }
        // ★ 2026-11-08：先让原逻辑写它自己的字段（原逻辑代跑时与本类镜像值相同），
        //   再让本类写权威值覆盖（引擎接管时以批次表为准）。
        if (original != null) {
            original.saveCustomPersistedData(tag, forDrop);
        }
        super.saveCustomPersistedData(tag, forDrop);
        engine.save(tag);
        // ★ 2026-11-10：电网取电身份（放置者 UUID）随逻辑一起存，重启后电网取电不丢
        if (gridUser != null) {
            tag.putString(TAG_GRID_USER, gridUser.toString());
        }
    }

    /**
     * 读自定义持久化数据：先让基类读回它自己的字段，再让引擎<b>清空并重建</b>批次表。
     *
     * <p>缺键／空表／单条坏档都由引擎内部安全处理（只跳过坏的那条，绝不外抛异常）。
     */
    @Override
    public void loadCustomPersistedData(CompoundTag tag) {
        // ★ 2026-11-10：读档前先清掉旧缓存（回到能源仓），避免跨档残电
        if (energyBuffer != null) {
            energyBuffer.flushBack();
        }
        // ★ 2026-11-08：原逻辑先读同一份 tag（读的是同一批字段），再由本类读权威值
        if (original != null) {
            original.loadCustomPersistedData(tag);
        }
        super.loadCustomPersistedData(tag);
        finder.reset();
        diag.resetForReload();
        engine.load(tag);
        // ★ 2026-11-10：读回电网取电身份（缺键 ⇒ 保持 null，只有能源仓可用）
        try {
            String raw = tag.getString(TAG_GRID_USER);
            if (!raw.isEmpty()) {
                gridUser = java.util.UUID.fromString(raw);
            }
        } catch (Throwable ignored) {
            // 坏 UUID 只丢电网身份，不影响机器
        }
    }

    // ================= 引擎 ↔ 机器的小桥（把 protected 字段收在子类里） =================

    /**
     * 配方修改器：等价于旧实现里的
     * {@code machine.fullModifyRecipe(raw.copy(), ocParams, ocResult)}。
     *
     * <p>单独成方法是因为 {@code ocParams}／{@code ocResult} 是 {@link RecipeLogic} 的
     * protected 字段，只有子类自己能碰；{@link BatchFinder} 只负责编排。
     */
    GTRecipe modifyRecipe(GTRecipe raw) {
        return machine.fullModifyRecipe(raw.copy(), ocParams, ocResult);
    }

    /** 每 tick 输入的 chance cache（{@link RecipeLogic#getChanceCaches()} 的子类读口）。 */
    Map<RecipeCapability<?>, Object2IntMap<?>> chanceCaches() {
        return getChanceCaches();
    }

    /**
     * 引擎的六个问题在本类上的答案（薄壳：真正的活都在 {@link BatchFinder} / {@link BatchIo}）。
     */
    private final class HostImpl implements ConcurrentRecipeEngine.Host {

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

        /** 找一条能开的新配方（见 {@link BatchFinder#findBatch()}）。 */
        @Override
        public GTRecipe findBatch() {
            return finder.findBatch();
        }

        /** 原子扣料（见 {@link BatchIo#consumeInputs}）。 */
        @Override
        public boolean consumeInputs(GTRecipe recipe) {
            return io.consumeInputs(recipe);
        }

        /** 每 tick 输入（见 {@link BatchIo#tickInputs}）。 */
        @Override
        public boolean tickInputs(GTRecipe recipe) {
            return io.tickInputs(recipe);
        }

        /** 批次收尾出料（见 {@link BatchIo#finishOutputs}）。 */
        @Override
        public void finishOutputs(GTRecipe recipe) {
            // ★ 2026-11-09：出料前给子类一次「按配方记账/改产出」的机会（NAC 模块用它记校准与杂质）。
            onBatchFinished(recipe);
            io.finishOutputs(recipe);
        }
    }

    /**
     * 批次完成回调（<b>出料之前</b>）：子类可在这里记账、或改这条配方的产出。
     * 默认空实现 ⇒ 其它机器行为逐字不变。
     */
    protected void onBatchFinished(GTRecipe recipe) {
    }

    // ================= 只读读数（Jade / 面板 / 子类） =================

    /** 当前在跑批次数（显示用）。 */
    public int runningThreadCount() {
        return originalActive() ? 0 : engine.size();
    }

    /** 当前所有在跑批次的并行之和（显示用）。 */
    public long totalRunsAtOnce() {
        if (originalActive()) {
            return 0L;   // 原逻辑代跑：没有「引擎批次」，线程显示整节不出现
        }
        long sum = 0L;
        for (int i = 0; i < engine.size(); i++) {
            sum += Math.max(1, engine.get(i).recipe.parallels);
            if (sum < 0) {
                return Long.MAX_VALUE;
            }
        }
        return sum;
    }

    /** 线程上限（线程仓当前设定值；没仓才用机器固定值；Jade/面板读取用）。 */
    public int threadLimit() {
        // ★ 2026-11-08：原逻辑代跑 ⇒ 报 1（Jade 的线程显示判据是「limit>1 或 有批次」，
        //   报 1 + runningBatches=0 就不会画出「独立线程」那一节）。
        return originalActive() ? 1 : Math.max(1, threaded.effectiveThreads());
    }

    /** 当前在跑的批次数（Jade/面板读取用）。 */
    public int runningBatches() {
        return originalActive() ? 0 : engine.size();
    }

    /**
     * ★ 2026-11-10：<b>最近一次找配方失败的真实原因</b>（含「缺哪些物品/流体、各缺多少」），
     * 给机器自己的 UI 显示用（{@code BatchDiagnostics.detail()} 的只读转出）。
     *
     * <p>原来这份明细只写进日志（每 100 tick 最多一行），玩家在界面里只能看到
     * Enhancedcore 那句笼统的「未找到可运行的配方，请检查模式、原料和电路」。
     * 转出到界面后，机器会直接写「拒绝=…｜配方=…｜缺=物品:xxx×1 流体:yyy×144mB」。
     */
    public String diagnosticDetail() {
        return diag.detail();
    }

    /**
     * 每条在跑批次的只读快照（进度 / 已钉死的时长 / 并行份数），供 Jade 逐条画进度条。
     */
    public List<BatchView> snapshotBatches() {
        if (originalActive()) {
            return List.of();
        }
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
     * 跨配方表是否生效：★ 2026-11-07 起与「独立线程」开关解耦 ——
     * 线程本来就该各自挑不同配方，多表机器默认全部扫（GTO 同义）。
     */
    private boolean isCrossTable() {
        return true;
    }

    // ================= 子类扩展点 =================

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
     * <p>机器里挂了 ME 样板仓（猪咪超样/gtlcore 样板总成）时，仓里刚推来的料并不在通用
     * handler 的 {@code getContents()} 里 ⇒ 只要挂了样板仓就不跳过（ME 缓存命中时仍走快路径）。
     *
     * <p><b>子类可覆写</b>（某些总成把输入藏在自定义仓里）。
     */
    protected boolean hasAnyItemOrFluidInput() {
        if (BatchSupport.hasMePatternPart(machine)) {
            return true;
        }
        return hasRealItemOrFluidInput();
    }

    /**
     * ★ 2026-11-08：<b>「真的有东西可喂」</b>——忽略「挂了 ME 样板仓」这条捷径，只按能力表
     * 逐仓看有没有非空物品/流体。
     *
     * <p>为什么要拆出来：{@link #hasAnyItemOrFluidInput()} 对「挂了 ME 样板仓」的机器恒
     * {@code true}（因为 AE 推来的料不在通用 handler 里，见上面注释）。这条保守规则会让
     * **既没有样板、也没有任何实物输入**的空机器仍旧每轮去扫整张配方表
     * （spark 实测：44 条候选 × 全量输入匹配 ≈ 6~7ms/轮），也就是玩家看到的「空扫、空闲延迟高」。
     *
     * <p>用途：{@code BatchFinder} 在「ME 样板缓存为空」时用本方法决定要不要回落到整表扫描；
     * 真正的唤醒仍由「AE 推样板 → {@code pokeScan}/内容变化 → 重新订阅」负责，不会漏单。
     */
    protected boolean hasRealItemOrFluidInput() {
        var proxy = machine.getCapabilitiesProxy();
        if (proxy == null) {
            return true; // 拿不到能力表就不跳过，宁可多扫也不卡死
        }
        var in = proxy.row(com.gregtechceu.gtceu.api.capability.recipe.IO.IN);
        if (in == null || in.isEmpty()) {
            return false;
        }
        for (var entry : in.entrySet()) {
            var cap = entry.getKey();
            if (cap != com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability.CAP
                    && cap != com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability.CAP) {
                continue;
            }
            for (var handler : entry.getValue()) {
                for (Object content : handler.getContents()) {
                    if (content instanceof net.minecraft.world.item.ItemStack stack) {
                        if (!stack.isEmpty()) {
                            return true;
                        }
                    } else if (content instanceof com.lowdragmc.lowdraglib.side.fluid.FluidStack fluid) {
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
