package com.gtlmodular.common.machine.tentacle;

import com.gregtechceu.gtceu.api.block.ICoilType;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.multiblock.CoilWorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.logic.OCParams;
import com.gregtechceu.gtceu.api.recipe.logic.OCResult;
import com.gtlmodular.common.machine.engine.IIndependentThreadHost;
import com.gtlmodular.common.machine.engine.IThreadedRecipeMachine;
import com.gtlmodular.common.machine.engine.ThreadedRecipeLogic;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 奈亚拉托提普之触（{@code gtlmodular:nyarlathoteps_tentacle}）—— GTO
 * {@code gtocore:nyarlathoteps_tentacle} 的移植体。
 *
 * <h2>GTO 原机是什么</h2>
 * <p>取证：{@code _gto_src/.../machines/MultiBlockD.java:420}
 * {@code .allRotation()} + {@code CoilCrossRecipeMultiblockMachine::createCoilParallel} +
 * {@code .recipeTypes(SUPRACHRONAL_ASSEMBLY_LINE_RECIPES, ASSEMBLY_LINE_RECIPES, CIRCUIT_ASSEMBLY_LINE_RECIPES)}
 * + {@code .laserTooltips()} + {@code .multipleRecipesTooltips()}。
 * 也就是：<b>一台能跑「超时空组装线 / 组装线 / 电路组装线」三张表的线圈多方块</b>，
 * 并行按线圈温度翻倍、能上激光仓、同时推进多条配方。
 *
 * <h2>本机口径</h2>
 * <ul>
 *   <li><b>三张表照搬</b>：注册时按 id 现取 {@code gtceu:suprachronal_assembly_line}、
 *       {@code gtceu:assembly_line}、{@code gtceu:circuit_assembly_line}（本整合包三张都在；
 *       取不到的自动跳过并告警，见 {@link com.gtlmodular.common.data.tentacle.NyarlathotepsTentacleMachines}）。
 *       表间切换走 GTCEu 原生的 {@code getActiveRecipeType()/setActiveRecipeType(int)}。</li>
 *   <li><b>并行只看线圈温度</b>（GTO 的 {@code coilParallel}）：GTO 的文案是
 *       「线圈温度每高出 900K，并行 ×2」，基准取第一档线圈 1800K ⇒
 *       {@code 并行 = 2^((温度-1800)/900)}，**再乘结构里并行仓的并行数**，
 *       乘积钳到 int 上限 21 亿（{@link #MAX_PARALLEL}）。</li>
 *   <li><b>多条配方并发</b>：走本模组独立线程引擎 {@link ThreadedRecipeLogic}；
 *       <b>2026-11-01（0.3.281）起线程数 = 结构里 GTO 线程仓的线程数之和</b>，
 *       没有线程仓时 = 1，见 {@link #baseThreads()}。</li>
 *   <li><b>线程仓交互（0.3.282，0.3.285 收敛界面行，0.3.286 去无线前置）</b>：与 GTO 一致 ——
 *       线程仓主页里可调 1~该档上限，左列配置页有两个开关「并行重复配方 / 独立线程」；
 *       本机在 {@link #fullModifyRecipe} 里落地这两个开关：<b>独立线程打开</b>（0.3.286 起不再要求
 *       无线能源仓）或关掉重复配方时，<b>同一配方不再被两条线程占用</b>，
 *       各线程改挑不同配方，取不到就留空线程。控制器面板<b>只显示</b>线程仓部件补的两行
 *       （GTO 原话「可为机器提供 N 线程的并行」+「当前设定 N / M 线程」），不再重复开关状态。</li>
 *   <li><b>激光 / 光学</b>：结构里开放 {@code INPUT_LASER}（激光仓）与
 *       {@code OPTICAL_DATA_RECEPTION}（光口，恰好 1 个），与 GTO 原结构一致。</li>
 * </ul>
 *
 * <p>线程仓接入方式：结构里的 {@code gtlmodular:thread_hatch_<tier>} 都实现
 * {@link IThreadHatchPart}，本机成形后遍历 {@code getParts()} 把它们的
 * {@link IThreadHatchPart#getThreads()} 加起来 —— <b>没有 gtladditions 依赖</b>。
 * GTO 奈亚只允许装 1 个线程仓（结构谓词 {@code setMaxGlobalLimited(1)}）。
 */
public class NyarlathotepsTentacleMachine extends CoilWorkableElectricMultiblockMachine
        implements IThreadedRecipeMachine, IIndependentThreadHost, org.gtlcore.gtlcore.api.machine.multiblock.ParallelMachine,
        com.gregtechceu.gtceu.api.machine.feature.IMachineLife {


    /**
     * 本机并行上限：<b>线圈温度倍率 × 结构里并行仓的并行数</b>（long 乘再钳到 int）。
     *
     * <p>★ 2026-11-08 修正：原来恒返回 1，Jade 的「同时处理至多 N 个配方」与 gtlcore
     * {@code BatchProcessing} 的常规并行上限都会读到 1（实际引擎按线圈倍率 × 并行仓在跑）。
     * 实际并行应用仍只在 {@link ThreadedRecipeLogic} 里乘一次，本方法只如实报同一口径
     * （与 gtlcore {@code WorkableElectricParallelHatchMultipleRecipesMachine} 同构）。
     */
    @Override
    public int getMaxParallel() {
        long hatch = org.gtlcore.gtlcore.common.data.GTLRecipeModifiers.getHatchParallel(this);
        long total = Math.max(1L, (long) this.parallelMultiplier()) * Math.max(1L, hatch);
        return (int) Math.min(Integer.MAX_VALUE, total);
    }


    /**
     * 没有线程仓时的并发线程数。
     *
     * <p>2026-11-01（0.3.281）队长口径：线程数改由结构里的<b>GTO 线程仓</b>
     * （{@code gtlmodular:thread_hatch_<tier>}，UV=4 … MAX=256）给出；
     * 一颗线程仓都不装时只有这 1 条。
     */
    public static final int BASE_THREADS = 1;

    /** 并行基准温度 = 第一档线圈（白铜 1800K）。 */
    public static final int BASE_COIL_TEMPERATURE = 1800;

    /** 每高出 900K，并行 ×2（GTO 文案原文：「线圈温度每高出 900K，并行 x2」）。 */
    public static final int TEMPERATURE_STEP = 900;

    /** 并行封顶：int 上限 2,147,483,647（21 亿）。线圈倍率与并行仓的乘积累加后由引擎钳到 int 上限。 */
    public static final int MAX_PARALLEL = Integer.MAX_VALUE;

    /**
     * GTCEu 1.4.4 的 {@code CoilWorkableElectricMultiblockMachine} 只有
     * {@code (IMachineBlockEntity)} 一个构造器（javap 现读，没有 varargs 版本），
     * 所以这里必须只透传 holder，不能写 {@code super(holder, args)}。
     */
    public NyarlathotepsTentacleMachine(IMachineBlockEntity holder, Object... args) {
        super(holder);
    }

    @Override
    protected RecipeLogic createRecipeLogic(Object... args) {
        return new ThreadedRecipeLogic(this);
    }

    /** ★ 2026-11-10：记录放置者 UUID —— 能源缓冲「从无线电网抽电」的身份。 */
    @Override
    public void onMachinePlaced(@org.jetbrains.annotations.Nullable net.minecraft.world.entity.LivingEntity player,
                                net.minecraft.world.item.ItemStack stack) {
        if (player instanceof net.minecraft.world.entity.player.Player p
                && getRecipeLogic() instanceof ThreadedRecipeLogic logic) {
            logic.setGridUser(p.getUUID());
        }
    }

    /**
     * ★ 2026-11-09：<b>引擎常驻</b>（不再「没装线程仓就交回原逻辑」）。
     *
     * <p>原因：gtlcore 1.2.3.3-fix3 下，gtladditions 给 {@code MultipleRecipesLogic} 的构造打了
     * mixin，会把机器强转成 {@code IWirelessThreadModifierParallelMachine}；本机只实现 gtlcore 的
     * {@code ParallelMachine} ⇒ 构造即 {@code ClassCastException}（0.3.358 实机崩溃就是这个）。
     * ⇒ 改为：<b>装不装线程仓都由引擎驱动</b>，没仓时线程数回落到 {@link #baseThreads()}（1 条）。
     */
    @Override
    public boolean engineRequiresThreadHatch() {
        return false;
    }

    // ------------------------------------------------------------------ 线程仓（IThreadHatchPart）

    /**
     * 没装线程仓时的固定线程数（GTO：1）。装了线程仓时由引擎统一读仓
     * （{@link IThreadedRecipeMachine#effectiveThreads()}），本方法不再自己扫部件。
     */
    @Override
    public int baseThreads() {
        return BASE_THREADS;
    }

    /** 本机跨配方线程总数（界面上用）：有效线程数 = 线程仓当前值，没仓 = 1。 */
    public int totalThreads() {
        return effectiveThreads();
    }

    // ------------------------------------------------------------------ 线程仓的两个开关（0.3.282）

    // 结构状态缓存：{@link #fullModifyRecipe} 是<b>热路径</b>（每 tick 每条候选配方调一次），
    // 而「收集各线程仓的开关状态」要遍历整张部件表 ⇒ 每 tick 只重算一次（key = 世界 tick）。
    // ★ 0.3.286：原来这里还缓存「结构里有没有无线能源仓」，用户要求去掉独立线程的无线前置后一并删除。
    private long threadStateTick = Long.MIN_VALUE;
    private boolean threadStateRepeated = true;
    private boolean threadStateIndependent;

    private void refreshThreadState() {
        if (getLevel() == null) {
            return;
        }
        long now = getOffsetTimer();
        if (now == threadStateTick) {
            return;
        }
        threadStateTick = now;
        boolean repeated = true;
        boolean independent = false;
        for (IMultiPart part : getParts()) {
            if (part instanceof IThreadHatchPart hatch) {
                if (!hatch.isRepeatedRecipes()) {
                    repeated = false;
                }
                if (hatch.isIndependentThread()) {
                    independent = true;
                }
            }
        }
        threadStateRepeated = repeated;
        threadStateIndependent = independent;
    }

    /** 结构里所有线程仓是否都允许「重复配方」（没有线程仓 ⇒ 允许，与历史行为一致）。 */
    public boolean isRepeatedRecipesAllowed() {
        refreshThreadState();
        return threadStateRepeated;
    }

    /** 有没有哪个线程仓打开了「独立线程」开关。 */
    public boolean isIndependentThreadRequested() {
        refreshThreadState();
        return threadStateIndependent;
    }

    /** 「独立线程」<b>是否真生效</b>：有仓打开即生效（0.3.286 起不再看无线能源仓）。 */
    public boolean isIndependentThreadActive() {
        refreshThreadState();
        return threadStateIndependent;
    }

    /**
     * 是否要<b>逐线程去重</b>（同一配方不允许被两条线程同时跑）。
     *
     * <p>★ 2026-11-03 起只有一个来源：「并行重复配方」被关 ⇒ 去重；
     * 开（默认）⇒ 同一配方可以填满所有线程。
     * 「独立线程」只负责「每条线程独立找配方（可跨全部配方表）」，<b>不</b>再顺带禁止重复。
     */
    public boolean isThreadDedupeActive() {
        refreshThreadState();
        // ★ 2026-11-03（玩家：「线程仓那两个按钮真的有用吗」）：
        //   去重只由「并行重复配方」决定 —— 关 = 同一配方只许占一条线程；
        //   独立线程只负责「跨配方表找配方」。旧逻辑写的是 OR（独立线程开也去重），
        //   默认两个开关都开 ⇒ 默认就在去重，单配方机器多余线程全空转，
        //   而且「并行重复配方」怎么点都看不出变化。
        return !threadStateRepeated;
    }

    // 去重查询的缓存：key = (server tick, 当前批次数)。同一 tick 内批次数只增不减
    // （引擎先摘后补），所以这个 key 足够精确，且把「每条候选配方都重建一次 id 集合」摊平成每 tick 一次。
    private long dedupeCacheTick = Long.MIN_VALUE;
    private int dedupeCacheSize = -1;
    private Set<ResourceLocation> dedupeCache = Set.of();

    /**
     * 这条配方是否<b>已经被别的线程占着</b>（去重判定的唯一读口）。
     *
     * <p>数据源是 {@link ThreadedRecipeLogic#snapshotBatches()}（引擎给 Jade/面板用的只读快照），
     * 所以这里<b>不需要</b>给共享引擎加任何新接口。
     */
    private boolean isRecipeOccupiedByThread(GTRecipe recipe) {
        if (recipe == null || recipe.getId() == null) {
            return false;
        }
        if (!(getRecipeLogic() instanceof ThreadedRecipeLogic logic)) {
            return false;
        }
        long now = getOffsetTimer();
        int size = logic.runningBatches();
        if (now != dedupeCacheTick || size != dedupeCacheSize) {
            Set<ResourceLocation> ids = new HashSet<>();
            for (ThreadedRecipeLogic.BatchView view : logic.snapshotBatches()) {
                GTRecipe running = view.recipe();
                if (running != null && running.getId() != null) {
                    ids.add(running.getId());
                }
            }
            this.dedupeCache = ids;
            this.dedupeCacheTick = now;
            this.dedupeCacheSize = size;
        }
        return dedupeCache.contains(recipe.getId());
    }

    /** 去重跳过次数（诊断用；只在 server 上累加）。 */
    private int dedupeSkips;

    /**
     * ★ 0.3.282 <b>两个开关的落地点</b>：引擎每「逐线程找一条配方」都会把候选先过
     * {@code fullModifyRecipe}（{@code ThreadedRecipeLogic.HostImpl#findBatch} 第 284 行），
     * 这里返回 {@code null} 就等于<b>把这个候选从本线程的匹配里排除</b>：
     * 引擎随即 {@code continue} 到下一个候选，取不到就留一条空线程。
     *
     * <p>为什么挂在机器侧而不是改共享引擎：{@link ThreadedRecipeLogic} 还被
     * 部件组装机 / 小型 PCB 工厂 / 一次性工具 3D 打印机 / 恒星炎炀锻炉共用，
     * 机器侧挂钩——<b>别的机器一个字都不用改</b>，也不新增任何引擎接口。
     *
     * <p>注意：返回 {@code null} 时引擎的诊断行会写成「配方修改器返回 null」，
     * 这里的跳过计数（{@link #dedupeSkips}）就是给日志/排查用的第二份账。
     */
    @Override
    public GTRecipe fullModifyRecipe(GTRecipe recipe, OCParams ocParams, OCResult ocResult) {
        if (isThreadDedupeActive() && isRecipeOccupiedByThread(recipe)) {
            dedupeSkips++;
            return null;
        }
        return super.fullModifyRecipe(recipe, ocParams, ocResult);
    }

    /**
     * ★ 0.3.285：<b>不再往控制器面板补任何自定义行</b>。
     *
     * <p>上一版这里补了「跨配方线程：N」「重复配方[开] · 独立线程[开]」和无线能源仓提示，
     * 而线程仓自己的 {@code addMultiText} 又会补一句同样的开关状态 ⇒ 面板上整行重复
     * （用户截图）。0.3.285 起控制器面板只留线程仓部件补的两行：
     * GTO 原话「可为机器提供 N 线程的并行」+「当前设定 N / M 线程」。
     * 开关实况只在<b>线程仓界面</b>的配置页里看；{@code dedupeSkips} 仍保留给日志/诊断
     * （{@link #threadStateSummary()}）。
     */
    @Override
    public void addDisplayText(List<Component> textList) {
        super.addDisplayText(textList);
    }

    /** 引擎/日志用的可读状态串（排查多线程调度时打一行就够）。 */
    public String threadStateSummary() {
        return "threads=" + totalThreads()
                + " repeated=" + isRepeatedRecipesAllowed()
                + " independentRequested=" + isIndependentThreadRequested()
                + " dedupe=" + isThreadDedupeActive()
                + " skips=" + dedupeSkips;
    }

    /**
     * 线圈并行 <b>× 结构里并行仓的并行数</b>（2026-10-31 队长口径：「让它会 × 结构内的并行仓」）。
     *
     * <p><b>本方法保持不变</b>（2026-11-01 队长口径：线程仓只改线程数，并行口径不动）。
     * 乘积由 {@code ThreadedRecipeLogic} 用 long 乘完再钳到 {@link Integer#MAX_VALUE}（21 亿），
     * 不会乘爆成负数；实际并行数仍由 {@code accurateParallel} 按原料量算，这里只是上限。
     */
    @Override
    public boolean usesParallelHatch() {
        return true;
    }

    /**
     * 线圈温度 → 并行倍率：{@code 2^((温度-1800)/900)}，向下取整、封顶 {@link #MAX_PARALLEL}。
     *
     * <p>未成形（线圈为 null）时按基准温度算 ⇒ 返回 1，不会 NPE。
     */
    @Override
    public int parallelMultiplier() {
        ICoilType coil = getCoilType();
        int temperature = coil == null ? BASE_COIL_TEMPERATURE : coil.getCoilTemperature();
        int steps = Math.max(0, (temperature - BASE_COIL_TEMPERATURE) / TEMPERATURE_STEP);
        long parallel = 1L << Math.min(steps, 30);
        return (int) Math.min(MAX_PARALLEL, parallel);
    }

    /** 温度口径（Jade/工具提示用；未成形返回基准温度）。 */
    public int getCoilTemperature() {
        ICoilType coil = getCoilType();
        return coil == null ? BASE_COIL_TEMPERATURE : coil.getCoilTemperature();
    }
}
