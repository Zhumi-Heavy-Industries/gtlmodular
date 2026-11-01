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
public class ThreadedRecipeLogic extends RecipeLogic {

    /** 并发批次表（纯调度，不依赖任何机器类；见 {@link ConcurrentRecipeEngine}）。 */
    private final ConcurrentRecipeEngine engine = new ConcurrentRecipeEngine();

    /** 机器侧契约（构造时由传入的 machine 强转得到）。 */
    private final IThreadedRecipeMachine threaded;

    // ---------- 2026-10-30 诊断（限频，最多 12 行/机器） ----------
    private int diagLines;
    private int diagCandidates;
    private String diagReject = "（还没试）";

    // ---------- 2026-10-30 扫描节流（大配方表空转时不要每 tick 全表扫） ----------
    /** 没有 ME 样板缓存时，一次 findBatch 最多试多少条配方。 */
    private static final int SCAN_BUDGET = 24;
    /** 整轮没找到配方后的冷却 tick 数（期间 findBatch 直接返回 null）。 */
    private static final int IDLE_COOLDOWN_TICKS = 10;
    private int scanCursor;
    private long scanCooldownUntil;

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
        // ③.5 诊断：空转时每 100 tick 打一行「为什么没配方」，最多 12 行
        if (engine.size() == 0 && diagLines < 12 && machine.self().getOffsetTimer() % 100L == 0L) {
            diagLines++;
            GTLModular.LOGGER.info("[gtlmodular] 线程引擎诊断 {} @{}：成型={} 候选={} 拒绝={}",
                    machine.getRecipeType().registryName, machine.self().getPos(),
                    machine.self() instanceof com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController c
                            ? c.isFormed() : true,
                    diagCandidates, diagReject);
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
        scanCursor = 0;
        scanCooldownUntil = 0;
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
        scanCursor = 0;
        scanCooldownUntil = 0;
        engine.load(tag);
    }

    /**
     * 引擎的六个问题在本类上的答案。
     *
     * <p>只做转发与配方试算：<b>唯一的写世界点</b>是 {@link #consumeInputs}（开批次时扣料），
     * {@link #tickInputs} 扣每 tick 的输入（EU／tick 流体），{@link #finishOutputs} 出料。
     */
    private final class HostImpl implements ConcurrentRecipeEngine.Host {

        /** 宿主机器：就是基类的 {@code machine} 字段（{@code IRecipeLogicMachine}）。 */
        @Override
        public IRecipeLogicMachine machine() {
            return ThreadedRecipeLogic.this.machine;
        }

        /** 同时能跑几条 = 机器给的线程数（至少 1）。 */
        @Override
        public int slots() {
            return Math.max(1, threaded.baseThreads());
        }

        /**
         * 找一条能开的新配方：<b>配方修改器 → 并行 → 输入/每 tick 输入/条件三重匹配</b>，
         * 全部通过才返回（此时<b>还没扣料</b>）。
         *
         * <p>并行 = {@code max(1, parallelMultiplier()) × max(1, 并行仓并行数)}。
         * 候选来自本机配方类型（{@code machine.getRecipeType()}）的查表迭代器，跳过燃油配方
         * （燃油走基类 {@code handleFuelRecipe} 那一路，本引擎不处理）。
         */
        @Override
        public GTRecipe findBatch() {
            GTRecipeType type = ThreadedRecipeLogic.this.machine.getRecipeType();
            if (type == null) {
                return null;
            }
            long now = machine.self().getOffsetTimer();
            if (now < scanCooldownUntil) {
                diagReject = "扫描冷却中（剩 " + (scanCooldownUntil - now) + " tick）";
                return null;
            }
            diagCandidates = 0;

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
                    diagReject = "无物品/流体输入（跳过扫描）";
                    scanCooldownUntil = now + IDLE_COOLDOWN_TICKS;
                    return null;
                }
                // 没有 ME 样板仓（或缓存为空）才退回整表；分片扫描，避免大表每 tick 全扫。
                candidates = type.getLookup().getLookup().getRecipes(true)
                        .filter(r -> !r.isFuel()).toList();
                int total = candidates.size();
                if (total == 0) {
                    diagReject = "候选=0（配方表为空）";
                    return null;
                }
                start = scanCursor % total;
                limit = Math.min(SCAN_BUDGET, total);
            }

            for (int i = 0; i < limit; i++) {
                int idx = fromMeCache ? i : (start + i) % candidates.size();
                GTRecipe raw = candidates.get(idx);
                diagCandidates++;
                // 先用「原配方」做一次只读匹配：物品/流体不够就不必再 copy/超频/精确并行
                // （并行只会放大输入，原配方都不够 ⇒ 放大后一定不够）。
                if (!RecipeRunnerHelper.matchRecipeInput(ThreadedRecipeLogic.this.machine, raw)) {
                    diagReject = "原料不足(含ME样板仓): " + raw.getId();
                    continue;
                }
                GTRecipe modified = ThreadedRecipeLogic.this.machine.fullModifyRecipe(
                        raw.copy(), ThreadedRecipeLogic.this.ocParams, ThreadedRecipeLogic.this.ocResult);
                if (modified == null) {
                    diagReject = "配方修改器返回 null: " + raw.getId();
                    continue;
                }
                // 普通多方块 = 线程倍率 × 并行仓；巨构（usesParallelHatch=false）只看线程倍率。
                // long 乘再钳到 int：温度倍率可能很大，不能把 int 乘爆成负数。
                long multLong = Math.max(1, threaded.parallelMultiplier());
                if (threaded.usesParallelHatch()) {
                    multLong *= Math.max(1, parallelHatchCount());
                }
                int mult = (int) Math.min(Integer.MAX_VALUE, multLong);
                Pair<GTRecipe, Integer> par = GTRecipeModifiers.accurateParallel(
                        ThreadedRecipeLogic.this.machine.self(), modified, mult, false);
                GTRecipe scaled = par.getFirst();
                if (scaled == null) {
                    continue;
                }
                // 只判输入（GTCEu 原生 RecipeLogic 也是「输入够就开跑」，输出空间在收尾时处理，
                // 否则没放输出仓的机器会永远开不了批次）。
                if (!RecipeRunnerHelper.matchRecipeInput(ThreadedRecipeLogic.this.machine, scaled)) {
                    diagReject = "物品/流体不匹配(含ME样板仓): " + scaled.getId();
                    continue;
                }
                if (!scaled.matchTickRecipe(ThreadedRecipeLogic.this.machine).isSuccess()) {
                    diagReject = "每tick输入不足(EU等): " + scaled.getId();
                    continue;
                }
                if (!scaled.checkConditions(ThreadedRecipeLogic.this).isSuccess()) {
                    diagReject = "配方条件不满足: " + scaled.getId();
                    continue;
                }
                diagReject = "有配方可跑";
                scanCooldownUntil = 0;
                if (!fromMeCache) {
                    scanCursor = (idx + 1) % candidates.size();
                }
                return scaled;
            }

            // 本轮没有结果：ME 缓存/整表都冷却一会儿，别在空转时每 tick 重复扫。
            if (fromMeCache) {
                scanCooldownUntil = now + IDLE_COOLDOWN_TICKS;
            } else {
                int total = candidates.size();
                if (start + limit >= total) {
                    scanCooldownUntil = now + IDLE_COOLDOWN_TICKS;
                    scanCursor = 0;
                } else {
                    scanCursor = (start + limit) % total;
                }
            }
            if (diagCandidates == 0) {
                diagReject = "候选=0（查表没命中：物品/流体/电路是否齐全）";
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
                diagReject = "beforeWorking 拒绝: " + recipe.getId();
                return false;
            }
            recipe.preWorking(ThreadedRecipeLogic.this.machine);
            if (!RecipeRunnerHelper.handleRecipeInput(ThreadedRecipeLogic.this.machine, recipe)) {
                diagReject = "扣料失败(输入仓/ME样板仓不够): " + recipe.getId();
                ThreadedRecipeLogic.this.machine.afterWorking();
                return false;
            }
            GTLModular.LOGGER.info("[gtlmodular] 线程引擎开机 @{}：{}", machine.self().getPos(), recipe.getId());
            scanCooldownUntil = 0;
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
            ThreadedRecipeLogic.this.machine.afterWorking();
            recipe.postWorking(ThreadedRecipeLogic.this.machine);
            RecipeRunnerHelper.handleRecipeOutput(ThreadedRecipeLogic.this.machine, recipe);
        }
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

    /** 线程上限（= 机器给的 {@code baseThreads()}；Jade/面板读取用）。 */
    public int threadLimit() {
        return Math.max(1, threaded.baseThreads());
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
    protected boolean hasAnyItemOrFluidInput() {
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
