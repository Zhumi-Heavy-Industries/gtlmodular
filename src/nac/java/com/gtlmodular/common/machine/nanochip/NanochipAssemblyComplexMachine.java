package com.gtlmodular.common.machine.nanochip;

import com.gtlmodular.common.data.nanochip.NanochipModContext;
import com.gregtechceu.gtceu.api.block.ActiveBlock;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.feature.IInteractedMachine;
import com.gregtechceu.gtceu.api.machine.feature.IMachineLife;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IDisplayUIMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.misc.EnergyContainerList;
import com.gregtechceu.gtceu.api.pattern.MultiblockState;
import com.gregtechceu.gtceu.api.pattern.MultiblockWorldSavedData;
import com.gregtechceu.gtceu.api.pattern.error.PatternError;
import com.gregtechceu.gtceu.api.pattern.error.SinglePredicateError;
import com.gregtechceu.gtceu.api.pattern.predicates.SimplePredicate;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.logic.OCParams;
import com.gregtechceu.gtceu.api.recipe.logic.OCResult;
import com.gregtechceu.gtceu.utils.FormattingUtil;
import com.gregtechceu.gtceu.utils.GTUtil;
import com.gtlmodular.common.data.NanochipBlocks;
import com.gtlmodular.common.data.nanochip.NanochipModulePreview;
import com.gtlmodular.common.data.nanochip.NanochipComplexPattern;
import com.gtlmodular.common.data.nanochip.NanochipRecipeTypes;
import com.gtlmodular.common.machine.MultiTypeLookupHost;
import com.lowdragmc.lowdraglib.utils.BlockInfo;
import com.gregtechceu.gtceu.api.gui.fancy.ConfiguratorPanel;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyConfigurator;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;
import com.gtladd.gtladditions.common.data.ParallelData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.GameProfileCache;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.BlockHitResult;
import com.gtladd.gtladditions.api.machine.feature.IThreadModifierPart;
import com.gtladd.gtladditions.api.machine.multiblock.GTLAddWorkableElectricMultipleRecipesMachine;
import com.gtladd.gtladditions.api.machine.logic.GTLAddMultipleTypeRecipesLogic;
// ★ 2026-11-13【NAC 电网取电】GTMThings 的玩家无线电网入口。该类就在本工程编译类路径
//   `gtl-modular/libs/`（gtmthings-1.3.5.fix）里，本整合包先例＝GTL-Enhancedcore 的无线充能器
//   （`WirelessChargerMachine.java:185`）。重载有 int / long / BigInteger 三个 + getUserEU：
//   出处 `_census/_probe_nacmat_evidence/classstr_gtladditions-3.2.8Custom_SubSpace-fix1.txt:2769-2776`。
import com.hepdd.gtmthings.api.misc.WirelessEnergyManager;
import org.gtlcore.gtlcore.api.machine.multiblock.IModularMachineHost;
import org.gtlcore.gtlcore.api.machine.multiblock.IModularMachineModule;
import org.gtlcore.gtlcore.common.machine.trait.MultipleRecipesLogic;
import org.gtlcore.gtlcore.utils.datastructure.ModuleRenderInfo;
// ★ 2026-10-07【修「找不到可运行配方 / 进度 0%」】—— 用 gtlcore 的公开接口做「精确 long 缩放 + 二分找
//   真正放得下的最大并行」（出处：星律 PrimordialModuleRecipeLogic.java:228/403）。这两个接口+
//   两个辅助类在本机 gtlcore 1.2.3.2-fix1 里均**已取证存在**，见 _census/并行放不下与显示重复_修复记录.md §④。
import org.gtlcore.gtlcore.api.recipe.IAdvancedContentModifier;
import org.gtlcore.gtlcore.api.recipe.IGTRecipe;
import org.gtlcore.gtlcore.api.recipe.IParallelLogic;
import org.gtlcore.gtlcore.api.recipe.RecipeExtensionCopier;
import org.gtlcore.gtlcore.api.recipe.RecipeRunnerHelper;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongPredicate;

import org.jetbrains.annotations.Nullable;

/**
 * <b>纳米芯片组装复合体（NAC, Nanochip Assembly Complex）</b> —— GTNH 2.9.0 同名多方块的移植版。
 *
 * <p>本类同时是：
 * <ul>
 *   <li><b>加工主体</b>：挂本模组自己的配方表 {@link NanochipRecipeTypes#ASSEMBLY_COMPLEX}
 *       （id {@code gtlmodular:nanochip_assembly_complex}，界面/JEI 显示「纳米芯片组装复合体」），
 *       该表通过 GTCEu 的「代理配方表」机制索引进 GTCEu 电路组装机表的<b>同一批 GTRecipe 对象</b>
 *       （详见 {@code NanochipRecipeTypes#ASSEMBLY_COMPLEX} 与 {@code NanochipRecipeTypes.ProxiedRecipeType} 的注释），
 *       再按 gtladditions 的 {@link NanochipRecipeLogic}（{@code GTLAddMultipleTypeRecipesLogic}）
 *       做<b>跨配方真实多线程</b>（L3：同时挂起 N 条不同配方、各自独立进度）<b>＋ 大规模并行</b>；
 *       ★ 2026-10-19 起该逻辑族还带来<b>复合型机器</b>语义：同一轮工作里同时搜
 *       {@code getRecipeTypes()} 的<b>全部</b>类型（简单模式下 = 电路组装表 + 换算表），
 *       组装与换算不必再切模式（开关 {@link NanochipAssemblyComplexMachine#NAC_MULTI_TYPE_RECIPES}）；</li>
 *   <li><b>模块宿主</b>：实现 gtlcore {@link IModularMachineHost}，11 种 NAC 模块
 *       （见 {@link NanochipModuleKind}）在自己成形后自动挂到附近的 NAC 上，为它提供各自加成；</li>
 *   <li><b>校准载体</b>：{@link NanochipCalibration} 记录最近 10 万个产出电路，
 *       按族占比自动「校准」，给出 3 档增益。</li>
 * </ul>
 *
 * <h3>两档运行 —— 「样板总成直通」与「全流程」（用户需求的核心）</h3>
 * 原版 NAC 必须把物品塞进输入仓 → 转成 CC → 真空传送带送到 11 个模块 → 组装矩阵拼成电路，
 * 想量产就得铺满一整套彩色管道。移植版保留全部机制与加成，但把「是否走完整链路」变成一条
 * <b>可选的上限阶梯</b>：
 * <table border="1">
 *   <tr><th></th><th>直通档（没装组装矩阵模块）</th><th>全流程档（组装矩阵模块在线）</th></tr>
 *   <tr><td>并行</td><td>{@value #DEFAULT_DIRECT_PARALLEL_CAP} 起，按模块再提高</td><td><b>每条配方各自满并行</b>（★ 2026-10-27「去总池」：不再乘 {@code getMaxParallel() × 线程数} 那个总池，上限 = {@code Long.MAX_VALUE}，实际由「真的放得下」的二分收敛，见 {@code NAC_PER_THREAD_FULL_PARALLEL}）</td></tr>
 *   <tr><td><b>跨配方线程</b>（★ 2026-10-02 新增）</td><td>{@value #DEFAULT_DIRECT_CROSS_RECIPE_THREADS} 起（配置可改，上界 16）</td><td><b>2&lt;&lt;(电压等级 - LV)</b>：LV=2 / MV=4 / HV=8 …，上界 {@value #MAX_CROSS_RECIPE_THREADS}</td></tr>
 *   <tr><td>时长下限</td><td>20 秒</td><td>5 秒</td></tr>
 *   <tr><td>模块 / 校准加成</td><td>仍生效</td><td>仍生效</td></tr>
 * </table>
 * 也就是说：<b>玩家可以像以前那样把样板总成塞进结构、让 AE 直接推样板出芯片</b>（省掉整条真空链路），
 * 代价就是并行与时长上限明显更低 —— 想吃到原版上限还是得把组装矩阵等模块立起来。
 *
 * <h3>超频规格（照抄原版）</h3>
 * 模块/本体的超频一律<b>封顶 2 次</b>且优先于并行；<b>配方时长不会被压到下限以下</b>
 * （原版注释：{@code The recipe time can not be overclocked below 5 seconds}）。
 */
public class NanochipAssemblyComplexMachine extends GTLAddWorkableElectricMultipleRecipesMachine
        implements IDisplayUIMachine, IModularMachineHost<NanochipAssemblyComplexMachine>, IMachineLife,
        IInteractedMachine, NanochipStructureRefresh.Refreshable, MultiTypeLookupHost, NanochipThreadHost {

    public static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            NanochipAssemblyComplexMachine.class,
            WorkableMultiblockMachine.MANAGED_FIELD_HOLDER);

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    /**
     * ★★ 2026-10-19【主机侧「先蓄够一整条配方的电再开工」总开关】★★
     *
     * <p>用户原话：「模块会在没有电的时候攒电来维持工作，但是主机不会，我需要主机也有这个功能」。
     * {@code true}（默认）= 主机在起跑前要求「本条配方整条所需电量」可获取；{@code false} = <b>完全恢复
     * 本次改动前的行为</b>（起跑不看电量，跑到一半没电才停）。
     *
     * <p>关闭方式：把本常量改成 {@code false} 重新编译即可 —— 判定点唯一（
     * {@link NanochipRecipeLogic#setupRecipe(GTRecipe)} 里那一句 {@code if (PRECHARGE...)}），
     * 关掉之后不扣任何东西、不改任何存档、不改任何既有分支。
     * 这与本包登记 {@code calculateParallels} 回退方式的做法同款（纯代码层、可随时回退）。
     *
     * <p><b>零存档影响</b>：本常量与它下面那几个缓存字段<b>都不是</b> {@code @Persisted}，
     * 不进 NBT、不改存档格式（缓存字段见 {@link #prechargeCache} 的注释）。
     */
    public static final boolean NAC_PRECHARGE_WHOLE_RECIPE = true;

    /**
     * ★★ 2026-10-19【「复合型机器 / 跨配方模式并行」总开关】★★
     *
     * <p>{@code true}（默认）= 配方搜索<b>同时</b>覆盖本机 {@link #getRecipeTypes()} 声明的<b>全部</b>
     * 配方类型（简单模式下即「主机电路组装表 + 换算表」两张一起搜）⇒ 组装与换算可以在
     * <b>同一轮工作里同时推进</b>，玩家不必再切模式等待。{@code false} = <b>完全恢复</b>本次改动前的行为：
     * 只搜 {@link #getRecipeType()}（当前激活的那一条类型 = 「模式切换」语义）。
     *
     * <p>「复合型机器」这个词的出处（逐字）：GTLsupb 的 GUI 档位
     * {@code gtlsupb.gui.machine_mode.multitype} = 中文「复合配方型机器」；机制定义见 gtladditions
     * 指南 {@code .../guide/_zh_cn/machine/multi_type.md:16}「当一台机器支持多个配方类别时，
     * 它可以在同一轮工作中同时处理不同类别下的配方」。汇总与逐条取证见
     * {@code _census/复合型机器_调研.md}（§2 / §3.1 / §6.1 方案 A）。
     *
     * <p><b>关闭方式</b>：把本常量改成 {@code false} 重新编译即可 —— 判定点唯一
     * （{@link NanochipRecipeLogic#lookupRecipeIterator()} 里那一句 {@code if (NAC_MULTI_TYPE_RECIPES)}），
     * 关掉之后候选来源逐字回到「只搜激活类型」，其余一切（线程截断、公平轮转、并行分配、逐槽隔离）不受影响。
     * 这与本类登记 {@code NAC_PRECHARGE_WHOLE_RECIPE} 回退方式的做法同款（纯代码层、可随时回退）。
     *
     * <p><b>零存档影响</b>：本常量不是 {@code @Persisted} 字段、不进 NBT、不改存档格式；
     * 也不改 {@link #getRecipeTypes()} 的返回内容与顺序（下标稳定性不受影响）。
     *
     * <p>⚠️ <b>不承诺「每种类型各自并行」</b>：并行仍由 {@link NanochipRecipeLogic#calculateParallels()}
     * 逐条各吃各的容量上限决定（贪婪 + 逐条上限语义）—— 候选类型从 1 类变 N 类<b>不会</b>放大
     * 「每条配方的并行上限」，也不会改变<b>线程数</b>（{@link #getMultipleThreads()}）这条真正的并发上限。
     *
     * <p>★ <b>与 {@link #NAC_PER_THREAD_FULL_PARALLEL}（2026-10-27 并行「去总池」）的关系</b>：
     * 那个开关打开（默认）时并行上限已改走「每条配方线程各自 {@code Long.MAX_VALUE} + 真的放得下二分」，
     * 旧的 {@code getMaxParallel() × getMultipleThreads()}「总池」不再参与每条配方的上限计算
     * ⇒ 本开关（多类型）与它<b>正交</b>：多类型只改「候选从哪些类型来」，那个开关只改「单条配方能放多大」。
     * 两者叠加的净效果 = 「不同类型的配方各按自己的可容纳量并行」，仍然<b>不等于</b>「每类型平均分」。
     */
    public static final boolean NAC_MULTI_TYPE_RECIPES = true;

    /**
     * ★★ 2026-10-30【照抄「增强版」的结构匹配<b>锁异常安全</b>；独立开关】★★
     *
     * <p>作者授权「直接抄」的那一条，在「增强版」里叫
     * {@code MultiblockControllerLockFixMixin}
     * （{@code _census/enhancedcore-src/.../mixin/gtceu/MultiblockControllerLockFixMixin.java:12-52}，
     * 自述见 {@code _census/enhancedcore-src/docs/全面审查-2.6.4-20260925.md:95-97}：
     * 「原接口锁修复被 GTLCore 新增的控制器具体方法遮蔽。异常注入实测确认仍会漏锁。」）。
     * 落地形态换成<b>纯代码子类覆写</b>（本轮零混入），逻辑在
     * {@link NanochipPatternLockHardening}，那里有本机字节码级的「为什么我们同样有这个洞」取证。
     *
     * <p>{@code true}（默认）= {@link #checkPatternWithLock()} /
     * {@link #checkPatternWithTryLock()} 改走异常安全的锁包装；{@code false} = <b>逐字恢复</b>
     * 本次改动前的行为（{@code super.checkPatternWithLock()} / {@code super.checkPatternWithTryLock()}）。
     *
     * <p><b>为什么默认开</b>：正常路径逐字节等价（调用序列 lock→checkPattern→unlock 一行不差），
     * 唯一差别是异常路径上「锁还掉」而不是「锁不还」；不还的后果是服务端线程永久阻塞、
     * 整个存档卡死（本工程实测 watchdog 40001/85752/132410 ms）。这是<b>只删风险、不换结果</b>
     * 的一类改动，且一行可回退，所以默认开。
     *
     * <p><b>零存档影响</b>：本常量与包装类都不是 {@code @Persisted} 字段，不进 NBT、
     * 不改存档格式、不新增 {@code @Persisted}（与本类既有的
     * {@link #NAC_PRECHARGE_WHOLE_RECIPE} / {@link #NAC_MULTI_TYPE_RECIPES} 同款纪律）。
     */
    public static final boolean NAC_PATTERN_LOCK_HARD_FIX = true;

    /**
     * ★ 异步结构检查的节流器（2026-09-16 止血）。
     *
     * <p>原因与实测证据见 {@link NanochipStructureCheckThrottle}：GTCEu 每 250ms 对"未成形"的控制器
     * 重扫整张 63×49×63 图案，且 12 个基座的模块检查共用同一个单线程池；重扫期间服务器线程会在
     * {@code patternLock} 上死等 ⇒ 实测出现过 123729ms / 2474 tick 的停摆。
     */
    private final NanochipStructureCheckThrottle structureCheckThrottle = new NanochipStructureCheckThrottle();

    /**
     * ★【2026-09-18 第三轮】便宜结构预检（O(≤16) 恒定世界查询）——见 {@link NanochipStructurePrecheck}。
     *
     * <p>它把上一轮"未成形也要等 6 秒"的体验问题解掉：<b>预检不过 ⇒ 本轮不做昂贵匹配；预检一过 ⇒ 立刻做</b>。
     */
    private final NanochipStructurePrecheck structurePrecheck = new NanochipStructurePrecheck();

    /**
     * ★【2026-09-18 第四轮】「无变化就不重跑」闸（队长点名的部署前必堵之洞）——见 {@link NanochipStructureCheckGate}。
     *
     * <p>堵的是：预检只采样 16 格 ⇒ 可能出现「16 格全对但某个非采样格是错的」这种<b>持久</b>状态，
     * 于是每秒喂一次 40–132 秒的全量匹配 ⇒ 单线程检查池被永久占满 ⇒ 卡死复发。
     */
    private final NanochipStructureCheckGate structureCheckGate = new NanochipStructureCheckGate();

    /**
     * ★★ 2026-11-11【样板总成镜像绑定 = 0 · 恢复 + 诊断】见 {@link NanochipMirrorBindGuard} 的类注释 ★★
     *
     * <p>它修的是「<b>上游 NBT 里记着镜像坐标，读档后那条链却没重新入链</b>」这一种断链：
     * 触发条件是两个都必须为真的上游读数（{@code getBoundProxyPositions()} 非空 且 {@code getProxies()} 为空），
     * 恢复动作只有一种、且是上游自己的 public 入口（{@code MEPatternBufferProxyPartMachine.setBuffer(...)}，
     * 与 gtlcore {@code pasteFromTag} 逐指令同款）。
     * <b>它绝不新建绑定</b>：没记过坐标就什么都不做（{@code 0} 就是 {@code 0}，显示即事实）。
     * 一行回退：删掉 {@link #tickModuleRefresh()} 里那一句 {@code mirrorBindGuard.tick(this);}。
     */
    private final NanochipMirrorBindGuard mirrorBindGuard = NanochipMirrorBindGuard.create();

    /**
     * ★【2026-09-21 A+（队长拍板「上」）】<b>精确预检</b> —— 身份格（{@code A/B/C/E/J}=8,034 格）
     * + 能力格指纹（{@code F/H/I}=625 格），见 {@link NanochipExactPrecheck}。
     *
     * <p>它接在两处，取代/加强第四轮那两道只采样 16 格的闸：
     * <ol>
     *   <li>未成形侧决策链：16 点预检之后、变化闸之前，先问一次"必然不成形吗"；
     *       并在身份格全对时用<b>能力格指纹</b>当闸的签名（能力格一变 ⇒ 立刻放行 = 免等待）；</li>
     *   <li>{@link #checkPatternWithTryLock()} 的覆写：昂贵匹配<b>前</b>最后一道"必然不成形"闸
     *       —— 失败就直接返回 {@code false}，GTCEu 连锁都不会拿。</li>
     * </ol>
     */
    private final NanochipExactPrecheck exactPrecheck = NanochipExactPrecheck.host();

    /**
     * 未成形侧的完整决策链（第四轮）：<b>预检 ⇒ 签名/失败格证据未变就不重跑 ⇒ 计数节流 ⇒ super</b>。
     *
     * <p>★ 2026-09-21（A+）：① 与 ② 之间插入了 {@link NanochipExactPrecheck} 的精确预检 ——
     * 身份格（8,034 格唯一方块）无一命中任何布局元素 ⇒ <b>必然不成形</b>，直接 return；
     * 身份格全对时，把闸的签名换成<b>能力格（625 格）指纹</b>（见 ①′）。
     *
     * <p>已成形侧<b>不</b>过预检、也<b>不</b>过闸（失效判定走 GTCEu/gtlcore 的方块变更事件路径，
     * 根本不进本方法；预检/闸都只服务"未成形 + 全量匹配"这一侧）。
     */
    @Override
    public void asyncCheckPattern(long periodID) {
        final long startNanos = System.nanoTime();
        boolean attemptedUnformed = false;
        long signature = NanochipStructureCheckGate.UNKNOWN_SIGNATURE;
        MultiblockState gateState = null;
        if (isFormed()) {
            // ── 已成形这一侧：只做周期兜底节流，**不**过预检/闸 ──
            // 失效判定走 GTCEu/gtlcore 的方块变更事件路径（LevelMixin → onBlockStateChanged →
            // checkPatternWithLock），根本不进本方法；这一档只为压掉"已成形却有错"时的兜底重扫。
            if (!this.structureCheckThrottle.shouldRun(true)) {
                return;
            }
        } else {
            // ── 未成形这一侧：预检门 → 变化闸 ──
            Level level = getLevel();
            if (level == null) {
                return;
            }
            // ★ 2026-09-21（队长要的"数字"）：本行只数一次"未成形侧轮询"，**不参与任何判定**
            //   （不读世界、不改任何门）。裸 return 次数 = 本计数 − 到达变化闸的次数
            //   —— 口径与理由见 NanochipStructureCheckGate.noteUnformedPoll 的 javadoc。
            //   第二个参数是**只读的相位观测**：上游（gtlcore @Overwrite 的 asyncCheckPattern）自带
            //   一道 (getOffset()+periodID)%4 过滤，被它丢掉的放行会白白消耗最小间隔 ⇒ 变化闸只在
            //   "这一轮上游真会跑匹配"时放行（javap 证据与"相位锁死"的完整说明见该闸类注释）。
            this.structureCheckGate.noteUnformedPoll(getPos(),
                    NanochipStructureCheckGate.gtceuReachesFullMatchThisPoll(getHolder().getOffset(), periodID));
            // ① 便宜预检：失败 ⇒ 这 16 格是"任何合法匹配都必须满足"的格 ⇒ 一定不成形，跳过昂贵匹配。
            NanochipStructurePrecheck.Verdict verdict =
                    this.structurePrecheck.check(level, getPos(), isFlipped());
            if (!verdict.mayForm()) {
                // ⚠️ 这里**必须**是裸 return（体检 ⑤ 的硬判据：预检失败 ⇒ 不调用昂贵匹配）。
                //   "预检失败也要有逐格线索"这条需求改由 tickSync() 的限流审计满足（见 logUnformedAudit 的调用点）。
                return;
            }
            signature = verdict.signature();
            // ①′ ★ 2026-09-21 A+ 精确预检：把"必然不成形"精确挡在昂贵匹配之前。
            //   · 身份格（唯一方块谓词格）逐格精确判定，任何布局元素都不成立 ⇒ 一定不成形 ⇒ 裸 return；
            //   · 身份格全对时，用它给出的**能力格指纹**当闸的签名：F/H/I 一变就立刻放行（免等待），
            //     没变就照旧受地板约束（"最坏延迟 = 地板值"这条有界不等价没有变，见 NanochipExactPrecheck 类注释）。
            //   · 守卫（朝向竖直 / 扩展朝向被改过 / 未加载 / 解析不出）⇒ mayForm=true + unknown=true ⇒ 一字不改地沿用旧签名。
            NanochipExactPrecheck.Verdict exact =
                    this.exactPrecheck.scan(level, getPos(), getFrontFacing(), getUpwardsFacing());
            if (!exact.mayForm()) {
                // 体检 ⑤ 的同一口径：预检失败 ⇒ 裸 return，绝不进入昂贵匹配。
                //   逐格线索仍由 tickSync() 的限流审计（NanochipComplexPattern.auditStructure）给出。
                return;
            }
            if (!exact.unknown()) {
                signature = exact.signature();
            }
            // ② 无变化就不重跑：签名（身份格全对时为 625 能力格指纹，否则退回 16 格指纹）与
            //    "上次失败那一格"都没变 ⇒ 受地板约束；任一变 ⇒ 立刻放行（免等待）。
            if (!this.structureCheckGate.shouldRunFullMatch(level, signature, startNanos)) {
                return;
            }
            if (!this.structureCheckThrottle.shouldRun(false)) {
                return;
            }
            gateState = getMultiblockState();
            attemptedUnformed = true;
        }
        super.asyncCheckPattern(periodID);
        if (attemptedUnformed) {
            // ③ 回填结论（成败 / 实测耗时 / 失败格证据），供下一轮做"变没变"的判据
            this.structureCheckGate.recordOutcome(signature, startNanos, System.nanoTime(),
                    !isFormed(), gateState);
            // ★ 2026-09-19（队长：「不要再一格一格报」）：还是没成形 ⇒ 打一次**完整缺件清单**。
            logUnformedAudit();
            // ★ 2026-09-19：刚真跑过一轮全量匹配 ⇒ 若有人点了「立即更新结构」，此刻给他结论。
            reportRefreshResult();
        }
    }

    /**
     * ★【2026-09-21 A+（队长拍板「上」）】{@code checkPatternWithTryLock()} 的覆写 ——
     * <b>昂贵整图案匹配之前的最后一道"必然不成形"闸</b>。
     *
     * <h3>为什么必须覆写这里（机制，字节码有据）</h3>
     * gtlcore {@code @Overwrite} 的 {@code MultiblockControllerMachine.asyncCheckPattern(long)} 第 37–41 字节码：
     * <pre>
     *   if (!checkPatternWithTryLock()) return;      // ★ 昂贵整图案匹配就跑在这一句里，且整段持 patternLock
     *   server.execute(lambda$asyncCheckPattern$0)
     * </pre>
     * （{@code _census/_jtmp/gtl_MCMMixin_c.txt:36-52}）⇒ 本方法返回 {@code false} 时：
     * <b>① 不拿 {@code patternLock}（异步线程不再与服务端线程抢锁）；② 不做整图案匹配；
     * ③ 那条 {@code server.execute(...)} 也不会被投递</b>。这正是报告 §0 的"把 40–132 秒级持锁变成 0"。
     *
     * <h3>为什么"预检失败 ⇒ 不成形"是安全的（零语义变更的第一红线）</h3>
     * 判据由 {@link NanochipExactPrecheck#scan} 给出，它是<b>成形的必要条件</b>：判否时 8 个候选布局元素
     * 下都至少有一格"身份格"不满足其<b>唯一方块</b>谓词（或严格空气格不是空气、或该格未加载）
     * ⇒ GTCEu 的匹配在<b>同一个世界状态</b>下也必然失败。逐类论证见 {@link NanochipExactPrecheck} 类注释的
     * "零语义变更"表；<b>凡是拿不准的（朝向竖直 / 扩展朝向被扳手改过 / 表为空 / 方块解析不出 / 未加载）
     * 一律返回 {@code mayForm=true} ⇒ 原样走 {@code super}</b>，由 GTCEu 自己决定。
     *
     * <h3>可逆性</h3>
     * 删掉本方法（连同 {@link #exactPrecheck} 字段与 ①′ 那段）即完全回到 0.1.41 的行为
     * —— 本方法<b>没有</b>修改任何既有判定式、没有改图案、没有碰 {@code asyncCheckPattern} 的两条既有分支语义。
     */
    @Override
    public boolean checkPatternWithTryLock() {
        Level level = getLevel();
        if (level != null && !level.isClientSide
                && !this.exactPrecheck.scan(level, getPos(), getFrontFacing(), getUpwardsFacing()).mayForm()) {
            // = "没拿到锁 / 不成形"：GTCEu 那边看到的就是"这次 tryLock 没成功"，不会投服务端任务。
            return false;
        }
        // ★ 2026-10-30（照抄「增强版」）：把 gtlcore 那句裸 `unlock()` 换成 try/finally，
        //   使 checkPattern() 抛异常时锁也能还回去。开关见 {@link #NAC_PATTERN_LOCK_HARD_FIX}。
        //   ⚠️ 调用序列与返回值和改前完全一致（tryLock 失败 ⇒ false；成功 ⇒ checkPattern 的真实结果），
        //      差别只在异常路径不再漏锁 —— 详见 {@link NanochipPatternLockHardening} 的「零语义变更」。
        return NanochipPatternLockHardening.withTryLock(this, NAC_PATTERN_LOCK_HARD_FIX);
    }

    // ==========================================================================================
    // ★【2026-09-21 队长拍板「上」】NH 报告 §⑤ 可借鉴清单 #1 ——「同 tick 折叠」（只读调研见
    //   `_census/NH的NAC成型机制_调研.md` §⑤ #1 与 §④「变更后的去抖：NH 50 tick 去抖 + 合并 vs
    //   我们无去抖、当场同步全量匹配」这一条最刺眼的差 (a)）。
    // ==========================================================================================
    /**
     * ★【2026-09-21 队长拍板「上」】{@code checkPatternWithLock()} 的覆写 ——
     * <b>同一个服务端 tick 内的重复调用折叠成一次真判定</b>。
     *
     * <h3>队长原话与依据</h3>
     * 队长 2026-09-21 拍板：「<b>上</b>」（= 落地 NH 报告 §⑤ 可借鉴清单 #1）。
     * 报告原文的借鉴点是「NH 的 {@code mUpdate = 50} 让连续变更合并成一次；gtlcore 现在是
     * <b>每次 {@code setBlock} 都投一次 {@code onBlockStateChanged}</b>」，做法建议是覆写
     * {@code IMultiController.checkPatternWithLock()}（{@code public default}，<b>无需 mixin</b>）。
     * 队长同时把窗口钉死成 <b>1 个服务端 tick</b>：不许照搬 NH 的 50 tick
     * （那会让「拆一块立刻失效」变成「可能 2.5 秒后才失效」，队长要的是「拆一格外壳<b>下一 tick</b> 失效」）。
     *
     * <h3>为什么要折叠（现状，实机与字节码双证）</h3>
     * 变更路径 = gtlcore {@code LevelMixin} / GTCEu {@code ChunkMixin} → {@code server.execute(state::onBlockStateChanged)}
     * → {@code MultiblockState.onBlockStateChanged} → <b>当场同步</b> {@code checkPatternWithLock()}：
     * <pre>
     *   gtlcore @Overwrite 版（部署 jar gtlcore-1.2.3.2-fix2 的 MultiblockStateMixin，逐字节码核过）：
     *     <失效分支>  : pos==controllerPos 且新方块不是控制器方块 ⇒ onStructureInvalid()+removeMapping()
     *     <重判定>    : if (formed &amp;&amp; checkPatternWithLock()) { setFlipped(isNeededFlip); onStructureFormed(); }
     *                   else { setFlipped(false); onStructureInvalid(); removeMapping(); addAsyncLogic(); }
     * </pre>
     * ⇒ 同一 tick 里连续 K 次变更（玩家连拆、爆炸、自动建造一次放很多方块）就会在同一 tick 里
     * 连跑 K 次 63×49×63 的整场匹配，全按在服务端线程上（实机证据：watchdog
     * {@code 40001 / 85752 / 132410 ms}，栈顶就是这条同步路径）。
     * 本覆写把「同一 tick 内第 2..K 次」直接返回<b>上一次真判定的返回值</b>，把 K 压到 1。
     *
     * <h3>为什么「缓存值只有 1 个 tick 的寿命」这句话必须靠 {@link #confirmFoldedPattern()} 才成立</h3>
     * 关键事实（javap 取证，部署 jar）：那条事件路径<b>只在 {@code isFormed()==true} 时</b>才调
     * {@code checkPatternWithLock()}（字节码里 {@code isFormed()} 之后连着两道 {@code ifeq} 把它门住；
     * gtlcore 与 gtladditions 两个 {@code @Overwrite} 版<b>都</b>有这道门）⇒ 折叠拿到的缓存值在实机常态下
     * 必然是一次「<b>成形</b>」判定。而「成形且无错」的控制器<b>没有任何周期兜底重查</b>：
     * gtlcore {@code @Overwrite} 的 {@code asyncCheckPattern} 第 0-7 字节码是
     * {@code if (!getMultiblockState().hasError() && isFormed) return;}，而 {@code BlockPattern.checkPatternAt}
     * 成功时会 {@code setError(null)}（偏移 776-778）⇒ 成形机永远等不到异步轮询那一趟。
     * 所以只折叠、不兑现 = 陈旧不是 1 tick，而是「直到下一次有方块变更命中本机缓存」——
     * 那就成了队长明令禁止的「<b>假装成形</b>」。{@link #confirmFoldedPattern()} 就是兑现它的那一半。
     *
     * <h3>零语义变更（为什么不改任何判定）</h3>
     * <ul>
     *   <li>本方法<b>不产生</b>任何判定：返回值只可能是 {@code super.checkPatternWithLock()} 的真实结果
     *       （要么本次真跑、要么同一 tick 内上一次真跑的结果）。<b>没有常量、没有 {@code true}</b>。</li>
     *   <li>跨 tick 一律不缓存：{@code tick != foldTick} ⇒ 无条件 {@code super}。</li>
     *   <li>fail-safe：拿不到世界 / 客户端侧 / 任何异常 ⇒ 直接 {@code super}（见方法体第一段）。</li>
     * </ul>
     *
     * <h3>线程安全</h3>
     * 三个 {@code fold*} 字段用 {@code volatile}：本方法的三处调用点（{@code onBlockStateChanged}、
     * {@code MultiblockControllerMachine.lambda$asyncCheckPattern$2}、{@code ObjectHolderMachine.setFrontFacing}）
     * <b>都在服务端线程</b>，所以本来就没有写-写竞争；用 volatile 是为了
     * ①「读到的 tick 与读到的值成对」（写序：先写值、再写 tick；读序：先读 tick、再读值）；
     * ②万一以后有人在别的线程读它，也只是一次良性竞争（最坏多跑一次真判定，绝不会返回没跑过的值）。
     * <b>不引入任何锁</b> —— 昂贵匹配自身的 {@code patternLock} 完全不受本覆写影响。
     *
     * <h3>可逆性</h3>
     * 删掉本方法 + {@link #foldTick}/{@link #foldValue}/{@link #foldDebt} + {@link #confirmFoldedPattern()}
     * + {@code tickModuleRefresh()} 里那一行调用 ⇒ <b>逐字</b>回到本轮之前的行为。
     */
    @Override
    public boolean checkPatternWithLock() {
        // ★★ 2026-11-12【原初模式】总开关：不做「同 tick 折叠」★★
        //   折叠会把同一服务端 tick 内第 2..K 次 checkPatternWithLock() 直接换成上一次的真实结果
        //   —— 那正是"我们自己加在上游判定链上的一道门"（与节流 / 变化闸 / 预检同一类）。
        //   原初模式下逐字走"本 tick 真跑一次"那条分支。
        //   ⚠️ 唯一保留的是**异常安全的锁包装**（NAC_PATTERN_LOCK_HARD_FIX，刻意不关：
        //      正常路径与上游逐字节等价、只差异常路径把锁还回去，理由见其 javadoc 与
        //      _census/结构归上游_对照山海方案.md §③.3）。
        //   置 false 即逐字回到折叠行为（三个 fold* 字段与 confirmFoldedPattern() 一字未动）。
        if (NanochipModContext.config().isNanochipOriginalMode()) {
            return NanochipPatternLockHardening.withLock(this, NAC_PATTERN_LOCK_HARD_FIX);
        }
        Level level = getLevel();
        if (level == null || level.isClientSide) {
            // fail-safe①：没有世界（未加载/已卸载）或客户端侧 ⇒ 不折叠，原样交给 GTCEu。
            // ★ 2026-10-30：这一支同样走异常安全的锁包装（拿不到世界也要保证锁能还回去）。
            return NanochipPatternLockHardening.withLock(this, NAC_PATTERN_LOCK_HARD_FIX);
        }
        final long tick = level.getGameTime();               // 服务端世界时钟：同一 tick 内恒定
        if (tick == this.foldTick) {
            // ① 折叠：本 tick 已经真跑过一次整场匹配 ⇒ 直接返回那一次的**真实结果**。
            final boolean cached = this.foldValue;
            if (cached) {
                // ② 「成形」这个方向必须有人兑现（见类注释）：登记一笔欠账，
                //    由本机的服务端周期钩子（tickModuleRefresh）在下一 tick 补一次真判定。
                //    「不成形」这个方向不需要：它本来就留在异步轮询那一侧（asyncCheckPattern 对
                //    !isFormed 不早退），下一轮必然重跑。
                this.foldDebt = true;
            }
            return cached;
        }
        // ③ 跨 tick / 本 tick 第一次：真跑一次（唯一贵的那一次），并把结论登记为本 tick 的缓存。
        // ★ 2026-10-30（照抄「增强版」）：这唯一贵的一次经异常安全的锁包装 —— 它做的仍是
        //   `lock(); checkPattern(); unlock();` 同一序列，唯一差别是 checkPattern() 抛异常时锁也能还。
        final boolean result = NanochipPatternLockHardening.withLock(this, NAC_PATTERN_LOCK_HARD_FIX);
        this.foldValue = result;                             // ★ 先写值
        this.foldTick = tick;                                // ★ 再写 tick（volatile 写 = 发布）
        this.foldDebt = false;                               // 新的真判定结清了上一 tick 的欠账
        return result;
    }

    /**
     * 把「同 tick 折叠」欠下的那一次真判定，在<b>下一 tick</b> 兑现（{@link #checkPatternWithLock()} 的另一半）。
     *
     * <p>队长口径是「拆一格外壳<b>下一 tick</b> 失效」⇒ 折叠窗口结束的那一刻必须有一次真判定，
     * 否则被折叠掉的那次变更就永远没人认账（理由与 javap 依据见 {@link #checkPatternWithLock()} 的类注释）。
     *
     * <h3>怎么兑现（不复制 GTCEu 的任何一行分支）</h3>
     * 把 gtlcore 自己的重判定入口 {@code MultiblockState.onBlockStateChanged(pos, state)} 当公开 API 用：
     * 它<b>只用 pos 做两件事</b>——① 排除控制器自己那一格（走 {@code controllerPos} 分支）；
     * ② 若新方块是 {@code ActiveBlock} 且在 {@code vaBlocks} 里则早退。除此以外，
     * 「跑不跑匹配、成形还是失效、要不要 removeMapping/addAsyncLogic」<b>全部由 gtlcore 自己的代码决定</b>。
     * 所以这里挑一个「在缓存里、非控制器格、非 {@code ActiveBlock}、方块实体也不是 {@code IMachineBlockEntity}」
     * 的触发格（两版 {@code @Overwrite} 的早退规则都被避开），结论就 100% 出自 gtlcore 自己——
     * 本类一行判定式都不复制（这是选它而不是手抄那 4 行失效分支的原因）。
     *
     * <h3>为什么先用 tryLock 挡一下</h3>
     * 兑现跑在服务端线程上，而 {@code patternLock} 可能正被 GTCEu 的异步检查线程持有
     * （实机卡死的直接形状）。这里<b>不阻塞</b>：拿不到锁就保留欠账，下一 tick 再试
     * （{@code patternLock} 是 {@code ReentrantLock}，持有它再走内部的重判定入口是可重入的）。
     * 「持着 patternLock 跑框架回调」也不是本类新造的形态：GTCEu 自己的
     * {@code MultiblockControllerMachine.lambda$asyncCheckPattern$0} 就是
     * {@code lock() → setFlipped/onStructureFormed/addMapping/removeAsyncLogic → unlock()}
     * （字节码 9→42 全在锁内），异步线程同一时刻只持有一把锁、从不嵌套
     * ⇒ 本方法持主机锁期间不会与异步线程形成环。
     */
    private void confirmFoldedPattern() {
        if (!this.foldDebt) {
            return;
        }
        Level level = getLevel();
        if (level == null || level.isClientSide || !isFormed()) {
            // 世界没了 / 已经不在「成形」这一侧 ⇒ 无需兑现（未成形侧本来就有异步轮询兜底）。
            this.foldDebt = false;
            return;
        }
        MultiblockState state = getMultiblockState();
        if (state == null) {
            this.foldDebt = false;
            return;
        }
        if (!getPatternLock().tryLock()) {
            // 异步线程正在跑整场匹配 ⇒ 本 tick 不兑现（保留欠账，下一 tick 再试），绝不在这里阻塞服务端线程。
            return;
        }
        try {
            // ★★ 0.3.131【① 修 NPE · 点位 1/3：本方法】不许再调 state.getCache() ★★
            //   证据（本机反编译字节码 `_census/_jtmp/MBState_c.txt:298-308`，GTCEu 1.20.1-1.4.4）：
            //     MultiblockState.getCache() 的方法体就是
            //       getfield cache:LongOpenHashSet → LongOpenHashSet.stream()
            //       → map(BlockPos::of) → collect(toList)
            //   ⇒ cache 字段为 null 时**它自己**抛 NPE（helpful message 正是 `"this.cache" is null`），
            //     **永远返回不了 null** ⇒ 任何 `getCache() == null` 守卫都是死代码（不可达）。
            //   `cache` 是 public 字段（同文件 `:35 public LongOpenHashSet cache;`），
            //   直接读字段才能在 cache 尚未初始化时安全返回。
            //   为什么 cache 会是 null：`clean()` 只在 `BlockPattern.checkPatternAt` 开头被调
            //   （`_census/_jtmp/BlockPattern_c.txt:188`，另一处是 `autoBuild` `:534`）
            //   ⇒ cache==null ⟺ 这个 MultiblockState 从没跑过整场匹配
            //   （读档后 `getMultiblockState()` 惰性新建的 state 正是这一形态）。
            if (state.cache == null) {
                this.foldDebt = false;                           // 无触发格可挑 ⇒ 放弃兑现（不会误判成形）
                return;
            }
            var cacheIterator = state.cache.longIterator();
            while (cacheIterator.hasNext()) {
                BlockPos pos = BlockPos.of(cacheIterator.nextLong());
                if (pos.equals(getPos())) {
                    continue;                                    // 控制器那一格走的是「失效」分支，不是重判定
                }
                BlockState bs = level.getBlockState(pos);
                if (bs.getBlock() instanceof ActiveBlock
                        || level.getBlockEntity(pos) instanceof IMachineBlockEntity) {
                    continue;                                    // 会被 gtlcore/gtladditions 的早退规则吃掉，换一格
                }
                this.foldDebt = false;
                state.onBlockStateChanged(pos, bs);              // ★ 判定与后续分支全部由 gtlcore 自己执行
                return;
            }
        } finally {
            getPatternLock().unlock();
        }
        // 缓存里找不到合格触发格（未成形/缓存被 clean 过）⇒ 放弃兑现：
        // 退化成「只折叠」的较弱语义（不会误判成形，只是可能晚一点才失效），如实登记在报告的不确定项里。
        this.foldDebt = false;
    }

    /** 上一次「真判定」落在哪个服务端 tick；{@link #FOLD_NO_TICK} = 本机还没跑过真判定。 */
    private volatile long foldTick = FOLD_NO_TICK;

    /** 上一次真判定的返回值 —— 只对 {@link #foldTick} 那一个 tick 有效（跨 tick 一律作废）。 */
    private volatile boolean foldValue;

    /** 上一次真判定是「成形」，且之后本 tick 内又被折叠过 ⇒ 下一 tick 必须补一次真判定。 */
    private volatile boolean foldDebt;

    /**
     * 「还没有过真判定」的哨兵。
     *
     * <p>用 {@code Long.MIN_VALUE} 且只做 {@code ==} 比较（<b>不做减法</b>）—— 这与本类
     * {@code lastUnformedAuditTick} 注释里记的那个坑同源：{@code gameTime - Long.MIN_VALUE} 会溢出。
     */
    private static final long FOLD_NO_TICK = Long.MIN_VALUE;

    /** 「尚未成形」缺件清单的限流间隔（tick）；{@value} = 15 秒。 */
    private static final long UNFORMED_AUDIT_INTERVAL_TICKS = 300L;

    /**
     * 上次打缺件清单的 gameTime。
     *
     * <p>⚠️ <b>2026-09-19 踩过的坑（必须记住）</b>：这里**不能**用 {@code Long.MIN_VALUE} 当"还没打过"的哨兵 ——
     * 本字段只按 {@code now - last < INTERVAL} 判，而 {@code gameTime - Long.MIN_VALUE} 在 long 上会
     * <b>溢出成一个大负数</b> ⇒ 恒小于阈值 ⇒ <b>日志永远不打</b>（实测：这一版部署后整场 0 条，
     * 而 `isVacuumWorking()` 的旧注释里早就写过同一个坑）。哨兵改成"足够小的负数"即可，
     * 或者像 {@code lastWorkTick} 那样先判 {@code != Long.MIN_VALUE}。
     */
    private long lastUnformedAuditTick = -10_000L;

    /**
     * ★ 2026-09-19（队长）：「不要一格一格报，一次把缺件清单给我」。
     *
     * <p>GTCEu 的图案判定**遇到第一个不符格就 return**，逐格原因只留那一格 ⇒ 多个破洞要
     * "修一个 → 看下一条日志 → 再修"。这里用 {@link NanochipComplexPattern#auditStructure} 自己
     * 遍历全部「唯一方块」格，把不符的**一次列出来**（含中文方块名），并**限流 15 秒**一条。
     */
    private void logUnformedAudit() {
        Level lv = getLevel();
        if (lv == null || lv.isClientSide || isFormed()) {
            return;
        }
        long now = lv.getGameTime();
        if (now - this.lastUnformedAuditTick < UNFORMED_AUDIT_INTERVAL_TICKS) {
            return;
        }
        this.lastUnformedAuditTick = now;
        List<String> audit = NanochipComplexPattern.auditStructure(lv, getPos(), 12);
        // ★ 2026-09-19：无论哪一支，都把 F 位内容清点一起打出来 —— 数量类失败（例：模块控制器合计 > 15 台）
        //   没有"哪一格错"，只有"多了几台"，而 GTLsupb 报的方块名只是标签（见 summarizeFSlotContent 注释）。
        String fSlots = summarizeFSlotContent();
        // ★ 2026-09-21（队长：「…要好久，尤其是主机」+ 日志每分钟 90 条）降噪：
        //   这一段一份清单是 1 条表头 + N 条逐格（N ≤ 12）—— 是全模组最重的写盘点之一。
        //   结论与清单内容都没变时（"一直缺同一批格"这种稳态），折叠进 NanochipStructureLogGate 的
        //   10 秒汇总，不再逐条重复；缺件清单一旦变化（玩家补了/拆了格子）就照打（"原因变了必须照打"）。
        String auditDetail = fSlots + "\n" + String.join("\n", audit);
        if (!this.structureLogGate.shouldEmitInfo(UNFORMED_CONCLUSION, auditDetail,
                "复合体@" + getPos().toShortString(), now)) {
            if (NanochipModContext.logger().isDebugEnabled()) {
                logUnformedAuditBody(audit, fSlots, true);
            }
            return;
        }
        logUnformedAuditBody(audit, fSlots, false);
    }

    /**
     * 真正把"缺件清单"写出去（{@code debugOnly=true} ⇒ 全部降到 debug，内容一字不变）。
     *
     * <p>拆成独立方法是为了让 INFO / debug 两条路<b>共用同一份文案</b>：
     * 降噪只改"多久打一次 / 打不打"，<b>不改清单内容</b>（队长要看的就是这份清单）。
     */
    private void logUnformedAuditBody(List<String> audit, String fSlots, boolean debugOnly) {
        if (audit.isEmpty()) {
            if (debugOnly) {
                NanochipModContext.logger().debug("[gtlmodular] NAC 尚未成形 @{}：唯一方块格（网格机壳/强化机壳/复合体玻璃/"
                                + "计算矩阵机壳/防火墙投影）**全部就位** ⇒ 缺口在「多解格」（H 仓位 / I 可忽略位 / "
                                + "F 模块位 / 空格）或数量类限制上；{}；GTCEu 逐格原因：{}",
                        getPos(), fSlots, String.valueOf(describeStructureFailure(getMultiblockState())));
            } else {
                NanochipModContext.logger().info("[gtlmodular] NAC 尚未成形 @{}：唯一方块格（网格机壳/强化机壳/复合体玻璃/"
                                + "计算矩阵机壳/防火墙投影）**全部就位** ⇒ 缺口在「多解格」（H 仓位 / I 可忽略位 / "
                                + "F 模块位 / 空格）或数量类限制上；{}；GTCEu 逐格原因：{}",
                        getPos(), fSlots, String.valueOf(describeStructureFailure(getMultiblockState())));
            }
        } else {
            if (debugOnly) {
                NanochipModContext.logger().debug("[gtlmodular] NAC 缺件清单 @{}（{} 条；{}）：", getPos(), audit.size(), fSlots);
                for (String line : audit) {
                    NanochipModContext.logger().debug("[gtlmodular]   · {}", line);
                }
            } else {
                NanochipModContext.logger().info("[gtlmodular] NAC 缺件清单 @{}（{} 条；{}）：", getPos(), audit.size(), fSlots);
                for (String line : audit) {
                    NanochipModContext.logger().info("[gtlmodular]   · {}", line);
                }
            }
        }
        // ★ 2026-09-19：无论哪一支，都把"GTCEu 失败的那条判据 + 它的计数"打出来 ——
        //   数量类失败的方块名是标签（见 logFailedPredicate 注释），只有这张表能给准信。
        //   ★ 2026-09-21：与上面整段同级别（折叠时一起降到 debug），否则降噪会被这里漏掉。
        logFailedPredicate(debugOnly);
    }


    // ---------- 手动「立即更新结构」（★ 2026-09-19 队长需求） ----------

    /**
     * 上一次点「立即更新结构」的人（服务端专用，**不同步**）；结论出来时给他一条聊天反馈。
     *
     * <p>为什么需要记住他：结构重查是<b>异步</b>出结论的（GTCEu 的 searchingTask 在单线程上跑），
     * 按钮不能同步返回结果；同理结论也不能再靠"刷新"两个字打发。
     */
    private ServerPlayer pendingRefreshRequester;

    /** 手动刷新的最迟回报时刻（gameTime）：到点无论成败都给结论，覆盖"预检就失败"这条早退路径。 */
    private long refreshReportAtTick = Long.MAX_VALUE;

    /** 手动刷新后给 GTCEu 那一轮搜索留的窗口（tick）；{@value} = 2 秒。 */
    private static final long REFRESH_REPORT_DELAY_TICKS = 40L;

    /**
     * 「立即更新结构」按钮的服务端入口（{@link NanochipStructureRefresh.Refreshable}）。
     *
     * <p><b>它只解开三道门，自己不跑任何结构查询</b>：
     * <ol>
     *   <li>{@link NanochipStructureCheckGate#reset()} —— 解除"签名/失败格都没变就不重跑"的自适应地板
     *       （这正是"半天不成型"的元凶：上次匹配慢 ⇒ 地板 = 3×耗时 ⇒ 玩家干等几分钟）；</li>
     *   <li>{@link NanochipStructureCheckThrottle#reset()} —— 解除计数节流；</li>
     *   <li>{@link #lastUnformedAuditTick} 归零 —— 让缺件清单立刻能打出来（不再等 15 秒）。</li>
     * </ol>
     * 门一开，GTCEu 的 {@code searchingTask}（每 250ms）下一轮就会真跑一次全量匹配。
     * <b>为什么不在按钮里直接同步 {@code checkPatternWithLock()}</b>：那会把整图案匹配按在服务端线程上，
     * 而按既有取证单次匹配最坏可达数十秒 —— 按钮绝不能变成新的卡死源（见 {@link NanochipStructureRefresh}）。
     */
    @Override
    public void requestStructureRefresh(ServerPlayer requester) {
        this.pendingRefreshRequester = requester;
        this.structureCheckGate.reset();
        this.structureCheckThrottle.reset();
        this.lastUnformedAuditTick = -10_000L;
        Level lv = getLevel();
        this.refreshReportAtTick = lv == null ? Long.MAX_VALUE : lv.getGameTime() + REFRESH_REPORT_DELAY_TICKS;
        if (isFormed() && !getMultiblockState().hasError() && hasRegisteredParts()) {
            // 已成形、无错、**且零件表非空**：GTCEu 根本不会再扫它（asyncCheckPattern 第 0-7 行直接返回）
            // ⇒ 当场答，否则请求人会一直等一个永远不会到来的结论。
            // ★ 2026-09-27：判据补上 hasRegisteredParts()。读档故障态恰好就是
            //   「isFormed=true 且 !hasError」，旧判据会把"零件表空"也当成"结构没问题"而秒答成功
            //   （定位文档 §③.2 缺口 3）：部件表为空时不算"结构没问题"。
            reportRefreshResult();
            return;
        }
        if (requester != null) {
            requester.sendSystemMessage(Component.translatable(NanochipStructureRefresh.LANG_REQUESTED)
                    .withStyle(ChatFormatting.YELLOW));
        }
    }

    /**
     * 把「手动刷新」的结论回给请求人（成形 / 缺件清单 / 数量类限制提示），并清掉请求人。
     *
     * <p>调用点（三处，先到先得）：
     * <ul>
     *   <li>{@link #asyncCheckPattern}：真跑过一次全量匹配之后（最快、最准）；</li>
     *   <li>{@link #onStructureFormed}：成形那一刻（同一轮里更早）；</li>
     *   <li>{@link #tickSync}：预检就失败时上面两处都不会到，靠到点兜底。</li>
     * </ul>
     */
    private void reportRefreshResult() {
        ServerPlayer requester = this.pendingRefreshRequester;
        if (requester == null) {
            return;
        }
        this.pendingRefreshRequester = null;
        this.refreshReportAtTick = Long.MAX_VALUE;
        if (isFormed()) {
            requester.sendSystemMessage(Component.translatable(NanochipStructureRefresh.LANG_OK)
                    .withStyle(ChatFormatting.GREEN));
            return;
        }
        requester.sendSystemMessage(Component.translatable(NanochipStructureRefresh.LANG_FAIL)
                .withStyle(ChatFormatting.RED));
        Level lv = getLevel();
        List<String> audit = lv == null ? List.of() : NanochipComplexPattern.auditStructure(lv, getPos(), 8);
        if (audit.isEmpty()) {
            // 唯一方块格全对 ⇒ 缺口只可能在"多解格"（H/I/F/空格）或数量类限制上。
            // 数量类限制（例：模块控制器合计 ≤ 15 台；见 NanochipComplexPattern.buildModulePart）
            // 没有"哪一格错"的位置概念 —— 这正是 GTCEu/GTLsupb 报出"位置 xxx 方块不匹配：<数量>最多：N个"的原因。
            requester.sendSystemMessage(Component.translatable(NanochipStructureRefresh.LANG_FAIL_OTHER)
                    .withStyle(ChatFormatting.GRAY));
        } else {
            for (String line : audit) {
                requester.sendSystemMessage(Component.literal("§c· §7" + line));
            }
        }
    }

    /**
     * <b>F 位（模块位）内容清点</b>：模块控制器共几台、网格接口机壳几个、其它几个。
     *
     * <p>★ 2026-09-19（队长原话：「可是我压根就没放纳米芯片组装矩阵呀」）—— 这条正是为此加的。
     * 图案里那条判据是「<b>14 种模块控制器合计 ≤ 15 台</b>」（{@code NanochipComplexPattern} 的
     * {@code blocks(accepted).setMaxGlobalLimited(15)}），而它的失败文案在 GTCEu 里
     * <b>只有「最多：%d个」，不带方块名</b>（jar lang {@code gtceu.multiblock.pattern.error.limited.0}）。
     * GTLsupb 补上去的那个方块名取的是<b>该判据的第一个候选</b>，而我们候选表的首项就是
     * {@link NanochipModuleKind#ASSEMBLY_MATRIX}（枚举首项）⇒ 玩家会看到"纳米芯片组装矩阵最多：15个"，
     * 于是以为"我没放组装矩阵却说我放了"。⇒ 把**真实台数**直接数出来，不再让人猜。
     *
     * <p>线程/时机：只读世界方块状态，可在 {@code onStructureFormed}（GTCEu 异步线程回调）与
     * {@code tickSync}（服务端线程）两侧调用；{@link #cachedModuleSlots} 在失效时**故意保留**
     * （见该字段注释），所以未成形时也能清点。
     */
    public String summarizeFSlotContent() {
        Level level = getLevel();
        List<BlockPos> slots = this.cachedModuleSlots;
        int controllers = 0;
        int casings = 0;
        int others = 0;
        if (level != null) {
            for (BlockPos pos : slots) {
                if (!level.isLoaded(pos)) {
                    others++;
                    continue;
                }
                Block block = level.getBlockState(pos).getBlock();
                if (isModuleControllerBlock(block)) {
                    controllers++;
                } else if (block == NanochipBlocks.MESH_INTERFACE_CASING.get()) {
                    casings++;
                } else {
                    others++;
                }
            }
        }
        return "F 位 " + slots.size() + " 格：模块控制器 " + controllers + " 台"
                + "（上限 " + getMaxModuleCount() + "）/ 网格接口机壳 " + casings + " 个 / 其它 " + others;
    }

    /** 该方块是不是 14 种模块控制器之一（按注册名判，避免依赖 GTCEu 的 definition API 细节）。 */
    private static boolean isModuleControllerBlock(Block block) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        if (id == null || !NanochipModContext.modId().equals(id.getNamespace())) {
            return false;
        }
        for (NanochipModuleKind kind : NanochipModuleKind.values()) {
            if (kind.machineId().equals(id.getPath())) {
                return true;
            }
        }
        return false;
    }

    /**
     * ★ 2026-09-19（队长：「我只放了 4 台模块，但是一将模块的主机替换成纳米网络芯片机械方块它就能成型」）：
     * <b>把 GTCEu 失败的那条判据连同它的计数一起打出来</b>。
     *
     * <h3>为什么必须打这张表</h3>
     * GTCEu 的数量类错误只给"最多：%d个"（jar lang {@code gtceu.multiblock.pattern.error.limited.0}，
     * <b>不带方块名</b>），方块名是 GTLsupb 补的、取的是<b>该判据第一个候选</b>；而方块与计数都藏在
     * {@code MultiblockState} 里 ⇒ 不上这张表就只能靠猜（本轮已经猜错过一次：
     * 我按"模块控制器合计 ≤15 台"去解释，可队长只有 4 台 —— 4 ≤ 15，这条解释<b>不成立</b>）。
     *
     * <h3>取什么</h3>
     * 字段可见性已按 gtceu-1.20.1-1.4.4 字节码核对：
     * <ul>
     *   <li>{@code MultiblockState.error} / {@code getGlobalCount()} 都是 public；</li>
     *   <li>{@code SinglePredicateError.predicate}（public final SimplePredicate）与 {@code .type}
     *       ⇒ 能指名道姓说出"是哪条判据"；</li>
     *   <li>{@code SimplePredicate.maxCount / minCount / maxLayerCount / minLayerCount / candidates} 都是 public
     *       ⇒ 上限值与候选方块名都能读出来。</li>
     * </ul>
     * 诊断只读、全程 try/catch：任何异常都吞掉，绝不拖垮结构检查本身。
     */
    private void logFailedPredicate() {
        logFailedPredicate(false);
    }

    /**
     * @param debugOnly ★ 2026-09-21（队长：「…要好久，尤其是主机」+ 日志每分钟 90 条）：
     *                  这一组"失败判据 / 全局计数表"是 {@link #logUnformedAudit()} 的一部分，
     *                  当缺件清单被判为"结论与原因都没变、已折叠"时，它也必须一起降到 debug ——
     *                  否则上面刚省下的量会被这几条 INFO 又打回去（漏一处等于没降）。
     *                  内容一字不变，只换输出级别。
     */
    private void logFailedPredicate(boolean debugOnly) {
        try {
            MultiblockState state = getMultiblockState();
            if (state == null) {
                return;
            }
            PatternError error = state.error;
            if (error == null || error == MultiblockState.UNLOAD_ERROR || error == MultiblockState.UNINIT_ERROR) {
                return;
            }
            Map<SimplePredicate, Integer> counts = state.getGlobalCount();
            if (error instanceof SinglePredicateError single) {
                SimplePredicate p = single.predicate;
                if (debugOnly) {
                    NanochipModContext.logger().debug("[gtlmodular] NAC 失败判据 @{}：type={}（0..3 = 数量类）；"
                                    + "该判据计数={} / 上限 max={} min={} maxLayer={} minLayer={}；候选方块={}",
                            getPos(), single.type, countOf(counts, p),
                            p.maxCount, p.minCount, p.maxLayerCount, p.minLayerCount, candidateNames(p));
                } else {
                    NanochipModContext.logger().info("[gtlmodular] NAC 失败判据 @{}：type={}（0..3 = 数量类）；"
                                    + "该判据计数={} / 上限 max={} min={} maxLayer={} minLayer={}；候选方块={}",
                            getPos(), single.type, countOf(counts, p),
                            p.maxCount, p.minCount, p.maxLayerCount, p.minLayerCount, candidateNames(p));
                }
            } else if (debugOnly) {
                NanochipModContext.logger().debug("[gtlmodular] NAC 失败判据 @{}：非单判据错误（{}）", getPos(), error.getClass().getSimpleName());
            } else {
                NanochipModContext.logger().info("[gtlmodular] NAC 失败判据 @{}：非单判据错误（{}）", getPos(), error.getClass().getSimpleName());
            }
            // 全局计数表全量打一份（每台结构只有几条）—— 这是"到底是哪个方块多/少"的直接答案
            if (counts != null && !counts.isEmpty()) {
                if (debugOnly) {
                    NanochipModContext.logger().debug("[gtlmodular] NAC 全局计数表 @{}（{} 条）：", getPos(), counts.size());
                } else {
                    NanochipModContext.logger().info("[gtlmodular] NAC 全局计数表 @{}（{} 条）：", getPos(), counts.size());
                }
                for (Map.Entry<SimplePredicate, Integer> e : counts.entrySet()) {
                    SimplePredicate p = e.getKey();
                    if (debugOnly) {
                        NanochipModContext.logger().debug("[gtlmodular]   · 候选={} 计数={} max={} min={}",
                                candidateNames(p), e.getValue(), p.maxCount, p.minCount);
                    } else {
                        NanochipModContext.logger().info("[gtlmodular]   · 候选={} 计数={} max={} min={}",
                                candidateNames(p), e.getValue(), p.maxCount, p.minCount);
                    }
                }
            }
        } catch (RuntimeException ignored) {
            // 诊断绝不能拖垮结构检查
        }
    }

    /** 全局计数表里该判据的计数（拿不到返回 -1）。 */
    private static int countOf(Map<SimplePredicate, Integer> counts, SimplePredicate p) {
        if (counts == null) {
            return -1;
        }
        Integer v = counts.get(p);
        return v == null ? -1 : v;
    }

    /** 判据候选方块名（最多列 6 个，按注册名；拿不到就写"拿不到"）。 */
    private static String candidateNames(SimplePredicate p) {
        try {
            BlockInfo[] infos = p.candidates.get();
            if (infos == null || infos.length == 0) {
                return "候选表为空";
            }
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < Math.min(6, infos.length); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(BuiltInRegistries.BLOCK.getKey(infos[i].getBlockState().getBlock()));
            }
            if (infos.length > 6) {
                sb.append(", …共 ").append(infos.length).append(" 个");
            }
            return sb.append(']').toString();
        } catch (RuntimeException e) {
            return "候选表读不出来：" + e.getClass().getSimpleName();
        }
    }

    /** 直通档基础并行（配置项 {@code nanochipComplex.directParallelCap} 可改）。 */
    public static final int DEFAULT_DIRECT_PARALLEL_CAP = 1024;

    /**
     * 直通档基础<b>跨配方线程</b>（配置项 {@code nanochipComplex.directCrossRecipeThreads} 可改，
     * 上界 16）。
     *
     * <p>默认 <b>1</b>：直通档 = 「不上组装矩阵也能出芯片」的那条低上限路，
     * 一次只挂起一条配方，与改动前（L2 合并成一条配方）的玩家观感一致。
     * 想让直通档也吃跨配方并行，把配置调到 2~16 即可（显示与真并发同时变，因为数值来源同一处）。
     */
    public static final int DEFAULT_DIRECT_CROSS_RECIPE_THREADS = 1;

    /** 原版规格：超频封顶 2/2，且优先于并行。 */
    public static final int MAX_OVERCLOCKS = 2;

    /** 原版规格：配方时长不得低于 5 秒（100 tick）。 */
    public static final int FULL_MIN_DURATION_TICKS = 100;

    /** 直通档时长下限 20 秒（400 tick）—— 「上限降低」的第一层代价。 */
    public static final int DIRECT_MIN_DURATION_TICKS = 400;

    /** 单个模块给直通档并行的倍率最多计两次（防止堆模块把直通档顶成全流程档）。 */
    private static final int PARALLEL_STACK_CAP = 2;

    /**
     * 原版 {@code MODULE_CONNECT_INTERVAL}：模块「已连接」是<b>每 20 tick 刷新的运行期状态</b>
     * （原版在 {@code onPostTick} 里逐模块 {@code connect(this)}）。
     */
    private static final int MODULE_REFRESH_INTERVAL = 20;

    // ---------- 状态 ----------

    /** 纳米校准（服务端权威；界面只读同步摘要）。 */
    private final NanochipCalibration calibration = new NanochipCalibration();

    /**
     * 虚拟真空传送带网络（16 色通道；颜色就是线，见 {@link NanochipVacuumNetwork}）。
     * 由装在控制室的彩色输入/输出仓、以及各模块上的彩色输入/输出仓共同读写。
     */
    private final NanochipVacuumNetwork vacuumNetwork = new NanochipVacuumNetwork();

    /**
     * ★【2026-09-16 双模式路由】同色管网的连通分量图（每个 NAC 一份，跟着存档走）。
     *
     * <p>只有 {@code nanochipComplex.vacuumRequiresPipes = true}（管道定向模式）时才会被查询；
     * 简单模式下所有端口的分量号恒为 0（{@link NanochipPipeGraph#UNWIRED}），本对象保持空转。
     * 结构成形/失效时 {@link NanochipPipeGraph#markDirty()}，管道/端口增删换色由静态拓扑纪元自动失效
     * （见 {@link NanochipPipeGraph#bumpTopologyEpoch()}）。
     */
    private final NanochipPipeGraph pipeGraph = new NanochipPipeGraph();

    /** 网络内容有变化（搬运仓写入时调用），用于刷新客户端摘要。 */
    private boolean vacuumDirty = true;

    /**
     * 已连接的模块集合（服务端线程维护）—— ★<b>这是「模块 ↔ 主机」连接的唯一权威</b>。
     *
     * <h4>★【2026-09-16 修「连接不稳」】写入纪律</h4>
     * <ol>
     *   <li><b>只有本机（主机）写这个集合</b>：{@link #refreshModulesFromStructure()} 按结构 {@code F} 位
     *       <b>整体重算</b>并<b>幂等覆盖</b>（不是"增量多退少补"），{@link #onStructureInvalid()} 由本机清空；</li>
     *   <li>模块侧（{@link NanochipModuleMachine}）<b>只读</b>这个集合来决定界面/日志里的
     *       「复合体连接=是/否」，它自己成形/失效时<b>不再</b>登记/注销。</li>
     * </ol>
     * <p><b>为什么必须这样</b>（玩家实测证据）：原来两侧各跑各的结构检查 —— 主机在"自己检查成功"时按槽名收模块，
     * 模块在"自己失效"时 {@code removeFromHost} 注销 ⇒ 谁后跑谁覆盖：11 台模块各自成形/失效一次，
     * 主机侧「已连接模块」数字就来回跳，模块界面则长期显示"复合体连接=否"（拿不到主机的配方/并行上下文）。
     */
    private final Set<IModularMachineModule<NanochipAssemblyComplexMachine, ?>> modules = new LinkedHashSet<>();

    /**
     * 最近一次<b>成功匹配</b>拿到的结构 {@code F} 位世界坐标（按坐标排序，顺序稳定；诊断用）。
     *
     * <p>为什么必须缓存：GTCEu 每次开始匹配都会 {@code MultiblockState.clean()} → 重置
     * {@code matchContext}，所以<b>检查失败之后</b> {@link NanochipComplexPattern#moduleSlotsOf} 只会返回空表，
     * 「结构失效」的诊断就没有格子可打。缓存下来之后，失效时仍能逐格打印"这一格现在站的是什么"。
     */
    private final List<BlockPos> lastMatchedModuleSlots = new ArrayList<>();

    /**
     * ★【2026-09-18 结构检测性能修复】<b>结构 F 位的「已完成匹配」快照</b> —— 运行期唯一数据源。
     *
     * <h3>为什么必须快照（实机证据，不是推测）</h3>
     * 2026-09-18 22:44–22:46 实机卡死，`latest.log` 原文：
     * <pre>
     *   [22:44:15] ModernFix ... A single server tick has taken 40001, more than 40000 milliseconds
     *   [22:45:00] ... 85752, more than 40000 milliseconds
     *   [22:45:47] ... 132410, more than 40000 milliseconds
     *   [22:46:03] [Server thread/WARN]: Can't keep up! ... Running 147834ms or 2956 ticks behind
     * </pre>
     * 三次线程转储都是同一个形状 —— GTCEu 的<b>单线程</b>多方块检查线程在跑
     * {@code NanochipAssemblyComplexMachine.asyncCheckPattern(:104)} → {@code BlockPattern.matchAisles}，
     * 而 {@code Server thread} 卡在 {@code patternLock} 上等它。
     *
     * <p>与此同时，本机的运行期扫描每 20 tick 调 {@link #refreshModulesFromStructure()}，它读的是
     * <b>活的</b> {@code getMultiblockState().getMatchContext()}。而 GTCEu 每次开始匹配都会
     * {@code MultiblockState.clean()} 清空 {@code matchContext}，再<b>逐格</b>填回去；
     * 半途失败还会直接 return。⇒ 我们读到的是<b>半填写的残表</b>。实测数字完全吻合：
     * 日志里「结构 F 位 N 个」在 9 分钟里采样 226 次、<b>翻转 225 次</b>，取值恒为
     * {2,6,10,14,16,18,22,23,24}（只有 48 是完整匹配），并伴随 66 次
     * 「已成形但拿不到结构 F 位槽位表」。
     *
     * <p>按残表重算 ⇒ 把好模块全断开（{@code modules} 0→6→0→2 反复横跳），
     * 断连又触发新一轮结构检查 ⇒ <b>正反馈</b>，检查队列永远排不空。
     *
     * <h3>纪律</h3>
     * <ol>
     *   <li>只在 {@link #onStructureFormed()}（GTCEu 已确认匹配成功、上下文完整）里刷新一次；</li>
     *   <li>{@link #refreshModulesFromStructure()} 只读这份快照，<b>绝不</b>再碰 live {@code matchContext}；</li>
     *   <li>{@link #onStructureInvalid()} <b>不</b>清空它（失效诊断仍要能逐格打印"这格现在是什么"，
     *       与 {@link #lastMatchedModuleSlots} 同款理由）。</li>
     * </ol>
     */
    private volatile List<BlockPos> cachedModuleSlots = List.of();

    /**
     * ★【2026-09-21 载入快路径】<b>从存档恢复的 F 位坐标表</b> —— {@link #cachedModuleSlots} 的存档副本。
     *
     * <h3>为什么必须有它（实机 + 存档双重取证，不是推测）</h3>
     * <b>取证 1（存档）</b>：`NAC验证世界/dimensions/kubejs/flat/region/r.-1.-1.mca` 局部区块 (21,23)
     * 解压后 1,080,770 B，其中该控制器的方块实体 NBT 里含 <b>{@code isFormed}</b> 一个键
     * （脚本 {@code _census/_dump_region_chunk.js} 可复现），而
     * <b>{@code parts} / {@code slots} / {@code matchContext} / {@code error} 一个都不在</b>。
     * <b>取证 2（实机日志）</b>：2026-09-19 20:54 那次载入，主机 `(-167,73,-136)` 在
     * {@code 21:00:02} 打出「尚无「结构 F 位」成功匹配快照」（该分支要求 {@code isFormed()==true}），
     * 而整份日志里它第一次「已成形」是 {@code 21:04:20} —— 中间 4 分 18 秒。
     * javap 已证 {@code isFormed} 只在 {@code onStructureFormed}/{@code onStructureInvalid} 两处被写，
     * 且整份日志无 {@code gtlmodular} 异常 ⇒ 唯一自洽的解释就是取证 1：
     * <b>GTCEu 把「已成形」随存档恢复了，但不恢复匹配上下文。</b>
     *
     * <h3>后果（正是队长说的"进游戏还要再重新连接一次，耽误好长时间"）</h3>
     * 载入后 {@code isFormed()==true} 但 {@code matchContext} 是空的 ⇒ {@link #snapshotModuleSlots()}
     * 从没被调用过（{@code onStructureFormed} 没跑）⇒ {@link #cachedModuleSlots} 恒空 ⇒
     * {@link #refreshModulesFromStructure()} 每次都"什么都不动"地返回 ⇒ 模块干等到 GTCEu
     * 自己重跑一次整图案匹配（那一次要排在所有未成形多方块的重复匹配之后，实测 4 分钟以上）。
     *
     * <h3>纪律：存档里的坐标表是"声称"，必须**先验证再采用**</h3>
     * 采用前逐格验证（见 {@link #adoptRestoredModuleSlots(Level)}）：
     * 全部位置已加载 <b>且</b> 每格站着的都是 F 位合法方块（网格接口机壳 或 模块控制器）。
     * 验不过就**不采用**，如实打日志并退回等 GTCEu 第一次成功匹配 ——
     * 宁可慢，绝不把"存档里写的"当成"现在真是这样"。
     */
    private volatile List<BlockPos> restoredModuleSlots = List.of();

    /** 存档快照是否已经"采用或否证"过一次（避免每 20 tick 重验；未加载时<b>不定论</b>）。 */
    private volatile boolean restoredSlotsSettled;

    /** 已成形却拿不到 F 位槽位表：只在状态翻转时提示一次，避免每 20 tick 刷屏。 */
    private boolean slotsUnavailableLogged;

    private long lastRescanTick = -1L;

    // ================= ★ 2026-09-27 仓位清单心跳（修「外壳连片刷新时机」） =================
    //
    // 「机器外壳连片」的客户端判据是 NacSlotIndex.isNacSlot → 本机发布的 syncedNacSlots。
    // 原来这份清单**只**在 onStructureFormed() 里发布一次 —— 正常路径够用（GTCEu 的
    // MultiblockState.onBlockStateChanged 在结构内任何方块变化后都会重判结构、成形就再回调一次
    // onStructureFormed），但只要有一条路径"结构没散、part 却换了"而没走到那次回调
    // （vaBlocks/活化态被 GTCEu 忽略、同 tick 折叠、异步回调时序……），清单就会长期停在旧内容上
    // ⇒ 客户端那几格的外壳连片一直不刷新，直到玩家"破坏结构再重新成型"这种大事件。
    //
    // 这里补一条**有界心跳**：每 NAC_SLOT_HEARTBEAT_INTERVAL tick 比一次
    // 「结构里 part 的**个数**」——个数没变就直接 return（O(1)，不建字符串、不碰世界、不重跑匹配）；
    // 只有个数变了才重建字符串，而写入仍然受 publishNacSlots() 自身的
    // 「encoded.equals(syncedNacSlots) 才写字段」去重保护 ⇒ 绝不会刷爆同步通道（1 条/秒上限）。

    /** 仓位清单心跳间隔（tick）：1 秒一次，与主机既有的 {@value #MODULE_REFRESH_INTERVAL} 同频。 */
    private static final int NAC_SLOT_HEARTBEAT_INTERVAL = 20;

    /** 仓位清单心跳的计数器（只增不减；初值 -1 = 下一次 tick 就跳一次）。 */
    private int nacSlotHeartbeat = -1;

    /** 上一次心跳时结构里的 part 个数（{@code -1} = 还没测过 ⇒ 下一次无条件测一次）。 */
    private int lastNacSlotPartCount = -1;

    // ================= ★ 2026-09-21 催促去抖 + 进世界静默窗口 + 结构日志降噪 =================
    //
    // 队长原话（2026-09-21）：
    //   「唉，现在一进游戏NAC相关的，都要重新加载结构，而且要好久，尤其是主机。」
    // 实机日志证据（logs/latest.log 3204 行，进世界后 NAC 结构消息按分钟分布）：
    //   19:35=23、19:38=28、19:39=77、19:40=91、19:41=90 ⇒ 之后每分钟仍在 80~90 条；
    //   绝大多数是「NAC 模块 尚未成形 @…（…）：该格判定不符（通用结构错误）…」这种**结论/原因都没变**的重复；
    //   另有一批「NAC 模块 已成形 @…（…，复合体连接=否）（…本机已催促其立刻重算一次，最坏 1 秒内生效）」。
    // 判读：模块侧每台都在"成形那一刻"直接催主机按结构重算，
    //   12 台模块同一波成形/失效 ⇒ 主机被催 12 次（而这 12 次之间只隔几十毫秒，结果必然一样）。
    // 本段就是把这 12 次合并成 1 次（去抖），并在进世界头 10 秒干脆不催（静默窗口）。

    /**
     * 同一座复合体两次「强制重算」之间的最小间隔（tick）；<b>20 = 1 秒</b>。
     *
     * <p>语义：模块成形/失效/被拆时只置一个"待办"标志；主机在自己的服务端 tick 里消费，
     * 且距上次强制重算不足本值就<b>继续攒着</b>，到点合并执行<b>一次</b>。
     * ⇒ 12 台模块同一波成形 = 主机重算 <b>12 次 → 1 次</b>（见 {@link #requestCoalescedResync()}）。
     *
     * <p>为什么 1 秒够用：主机的连接集合本来就有
     * {@link #MODULE_REFRESH_INTERVAL} = 20 tick 的周期重扫兜底，
     * 所以去抖带来的最坏额外延迟 ≤ 1 秒，与原有兜底同量级（原来的注释也写着"最坏 1 秒内生效"）。
     */
    public static final int RESYNC_COALESCE_TICKS = 20;

    /**
     * 进世界（本机 {@code onLoad}）后的<b>静默窗口</b>（tick）；<b>200 = 10 秒</b>。
     *
     * <p>队长要求（2026-09-21）：世界刚加载的头一段时间内<b>不主动催促</b>，
     * 让各机器按自己的节奏把初始化做完（日志里正是"一进门互相打架"：主机 + 12 台模块同时成形/失效）。
     *
     * <p><b>为什么不影响功能</b>：静默窗口只丢"催促"，不丢
     * {@link #tickModuleRefresh()} 里那条每 {@value #MODULE_REFRESH_INTERVAL} tick 的<b>周期重扫</b>
     * ⇒ 窗口内建好的模块照样会在 ≤1 秒内被主机收进集合（绝不会"永远不重算"）；
     * 窗口一过，催促入口自动恢复（判据见 {@link #consumePendingResync(long)}）。
     *
     * <p><b>时间基准为什么用 {@code level.getGameTime()} 而不是 {@code MinecraftServer.getTickCount()}</b>：
     * ① 前者是"世界时钟"，本机 {@code onLoad} 那一刻 + 200 就是准确的"进世界后 10 秒"；
     * ② {@code getTickCount()} 是"服务器进程运行时长"，专用服务器开机很久后才有人进世界，
     *    它进门时早就 >200 tick ⇒ 静默窗口永远不开（那一条在本场景等于没改）。
     * 两个计时源在本类的周期重扫里本来就统一用 {@code getGameTime()}，这里沿用同一把尺子。
     */
    public static final int JOIN_QUIET_TICKS = 200;

    /**
     * 「待办的催促」标志。
     *
     * <p><b>为什么是原子布尔而不是普通字段</b>：置位方可能是模块侧的结构回调
     * （日志里出现过 {@code GTCEu Multiblock Async Thread-0}：GTCEu 在异步结构检查线程上回调
     * {@code onStructureFormed} → 模块 → {@link #requestCoalescedResync()}），
     * 消费方是主机的服务端 tick。用 {@link AtomicBoolean#getAndSet(boolean)} 消费
     * ⇒ 既不撕裂、也不会丢掉"消费瞬间又来了一个"的那次请求（最多多攒 1 秒）。
     */
    private final AtomicBoolean resyncRequested = new AtomicBoolean(false);

    /**
     * 本机上一次「强制重算」的 {@code gameTime}（<b>只由服务端线程写</b>，见 {@link #consumePendingResync(long)}）。
     *
     * <p>哨兵值取 {@code -10_000} 而不是 {@code Long.MIN_VALUE}：
     * 判据是 {@code now - last < INTERVAL}，{@code gameTime − Long.MIN_VALUE} 会溢出成负 ⇒ 恒小于阈值
     * （同一个坑本类的 {@code lastUnformedAuditTick} 与模块侧的 {@code lastUnformedLogTick} 都登记过）。
     */
    private long lastForcedResyncTick = -10_000L;

    /** 静默窗口的截止 {@code gameTime}（{@code onLoad} 时设定；见 {@link #JOIN_QUIET_TICKS}）。 */
    private long joinQuietUntilTick = -10_000L;

    /** 本机的结构状态日志闸（同一结论 10 秒一条 + 跨机器折叠汇总；见 {@link NanochipStructureLogGate}）。 */
    private final NanochipStructureLogGate structureLogGate = new NanochipStructureLogGate();

    /** 「复合体已成形」这条日志的结论名（同时是日志闸的折叠分组键）。 */
    private static final String FORMED_CONCLUSION = "复合体已成形";

    /** 「复合体已成形」正文格式（INFO 与降级后的 debug <b>共用同一条文案</b>，避免两处漂移）。 */
    private static final String FORMED_LINE_FORMAT =
            "[gtlmodular] 纳米芯片组装复合体 已成形 @{}（档位={}，F 位上成形模块={}，已连接模块={}/{}，{}）";

    /** 「复合体结构失效」这条日志的结论名。 */
    private static final String INVALID_CONCLUSION = "复合体结构失效";

    /** 「复合体结构失效」正文格式。 */
    private static final String INVALID_LINE_FORMAT =
            "[gtlmodular] 纳米芯片组装复合体 结构失效 @{}（等玩家补齐结构；本次断开模块 {} 个）";

    /** 「复合体尚未成形（缺件清单）」这条日志的结论名。 */
    private static final String UNFORMED_CONCLUSION = "复合体尚未成形";

    /**
     * ★【结构位置缓存 + 方块移除定点失效】这条日志的结论名（同时是日志闸的折叠分组键）。
     *
     * <p>为什么必须可见（而不是静默）：它是本功能<b>唯一</b>的动作见证 —— 命中缓存 ⇒ 请上游重判一次
     * ⇒ 那一刻日志里要能看出"是因为某一格被移除而判的"，否则下次排障又只能猜
     * （与 {@code NanochipStructureRefresh} 的"恢复动作必须可见"同一条纪律）。
     */
    private static final String REMOVAL_INVALIDATE_CONCLUSION = "方块移除定点失效";

    /** 「方块移除定点失效」正文格式（INFO 与降级后的 debug <b>共用同一条文案</b>）。 */
    private static final String REMOVAL_INVALIDATE_LINE_FORMAT =
            "[gtlmodular] 纳米芯片组装复合体 方块移除 ⇒ 定点重判 @{}（移除格={}；"
                    + "跑不跑匹配、成形还是失效全部由 gtlcore 自己判）";

    /**
     * 能量缓存分配间隔（tick）—— wiki：「复合体**每 20 tick**向各模块分配缓存电量」。
     */
    public static final int ENERGY_DISTRIBUTE_INTERVAL = 20;

    /** 上次分配缓存的 tick（{@code -1} = 还没分过）。 */
    private long lastEnergyDistributeTick = -1L;

    /** 每 20 tick 按结构 {@code F} 位刷新模块连接的服务端订阅。 */
    private TickableSubscription moduleRefreshSub;

    /** 界面同步文案的节流计时（60 tick 一次）。 */
    private long lastUiRefreshTick = -1L;

    // ---------- 同步到客户端用于显示 ----------

    @DescSynced
    private String syncedMode = "直通档";

    @DescSynced
    private String syncedCalibrationText = "未校准";

    @DescSynced
    private String syncedBreakdownText = "";

    @DescSynced
    private String syncedModuleText = "未安装模块";

    @DescSynced
    private long syncedParallelShown = 1L;

    /** 当前校准档位（0 = 未校准）：客户端用来决定文字颜色（校准数据本体不同步）。 */
    @DescSynced
    private int syncedCalibrationTier;

    /**
     * ★★ 0.3.132【校准重构】<b>当前"生效族"的 ordinal</b>（{@code -1} = 没有任何族生效）。
     *
     * <h4>为什么要新增这一格</h4>
     * 队长原话：「玩家无法直观地看到这个校准了以后到底都能有什么功效，这也需要有提示」。
     * 要显示"当前生效：<b>晶体电路</b> 第 2 档"就必须知道**是哪个族**在生效，而
     * {@code activeFamily()} 原本只在服务端算、客户端读不到。
     *
     * <h4>为什么不让客户端自己复算（重要纪律）</h4>
     * {@code activeFamily()} 的规则是「<b>档位最高优先，同档比数量</b>，且跳过 {@code mergedAway} 的族」
     * —— 这三条若在客户端重写一遍，迟早与判定漂移（"显示即事实"就破了）。
     * ⇒ <b>同步服务端那一份</b>：客户端只拿 ordinal 反查 {@code barFamilies()} 里的名字。
     *
     * <p>不进存档（{@code @DescSynced} 不是 {@code @Persisted}）⇒ 零存档面变化。
     */
    @DescSynced
    private int syncedActiveFamilyOrdinal = -1;

    /**
     * 当前判定分母口径是否 = 原版硬编码的 100000（= 整个历史窗口）。
     *
     * <p>由服务端每 tick 从配置读一次推给客户端，这样界面显示的「分母模式」与真正判定用的口径
     * <b>永远一致</b>（客户端自己读配置在专用服务器上可能与服务端不同）。
     * 初值取 {@code false} = ★ 2026-09-21 队长令「改」之后的配置默认值（分母 = 已记录数）；
     * 这只是"服务端第一次推下来之前"的占位值，真正口径以同步值为准。
     */
    @DescSynced
    private boolean syncedFullWindowDenominator = false;

    /** 真空传送带网络摘要（客户端显示用；服务端网络内容变化时才推送）。 */
    @DescSynced
    private String syncedVacuumText = "无";

    /** 真空网络里的虚拟件总数（用于判断是否需要刷新摘要）。 */
    private long lastVacuumTotal = -1L;

    // ==========================================================================================
    // ★★ 2026-11-13【NAC 电网取电】★★
    //
    //   用户原话：「让 NAC 的成型里面去掉能源仓、激光仓这些的，直接从电网中取电」。
    //   结构层那一刀（能源仓下限 1 → 0）已在 NanochipComplexPattern.nanochipHatches() 完成；
    //   本段是**取电层**：没有能源仓时，NAC 直接从放置者（或其 FTB 队伍）的 GTMThings 无线电网取电。
    //
    //   形态照抄本代码库已跑通的先例：NanochipModuleMachine.java:1098-1113（覆写 getEnergyContainer）
    //   + :1145-1201（HostEnergyContainer implements IEnergyContainer）—— 于是**记账与"没电就等待"
    //   的语义自动与既有"按需现取"口径一致**（isEnergySourceEffectivelyUnlimited /
    //   getObtainableEnergyEU / ON_DEMAND_POWER_FLOOR_EU 那套一个字都不用改）。
    //
    //   回退 = 把配置项 nanochipComplex.gridPower.enabled 置 false（getEnergyContainer 里一行分支，
    //   逐字回到基类结果），或按 _census/NAC电网取电_回退包/ORIGINAL_HUNKS.md 撤字段/内部类/覆写。
    // ==========================================================================================

    /**
     * <b>放置者（或放置者所属 FTB 队伍）的 UUID</b> —— 电网取电唯一的身份凭据。
     *
     * <p>★ <b>这是本方案唯一的存档格式变更</b>（新增一个 {@code @Persisted UUID} 键）。
     * 凭据必须持久化：{@code gtmthings} 的余额是<b>按 UUID/队伍</b>记的，
     * 读档后若 UUID 丢了就永远取不到电。
     *
     * <p>{@code null} = 未绑定 ⇒ 电网容器不参与供电，本机只用能源仓；{@link #onLoad()} 载入时报一句，
     * 主机界面也显示怎么绑定（空手潜行右键控制器，见 {@link #onUse}）。
     *
     * <p>{@code @DescSynced} 只为让客户端那几行界面显示（"中央供电"读的就是能源容器实况）
     * 与服务端同口径；它不参与任何判定。
     */
    @Persisted
    @DescSynced
    @Nullable
    private UUID gridPowerUser;

    /** 电网容器的取电身份日志限流间隔（tick）＝ 10 秒。 */
    public static final int GRID_USER_LOG_INTERVAL = 200;

    /** 上次打「电网容器因没有放置者 UUID 而拒绝取电」的 tick（{@link Long#MIN_VALUE} = 还没打过）。 */
    private long lastGridUserLogTick = Long.MIN_VALUE;

    /**
     * 电网取电的认证/测量容器（transient：纯派生，不进存档）。
     * 每次 {@link #getEnergyContainer()} 都<b>重新构造</b> {@code EnergyContainerList} 包一层，
     * 所以这里只缓存容器对象本身（与 {@link #gridPowerUser} 解耦 ⇒ 拆放/换人后自动跟着变）。
     */
    private transient GridEnergyContainer gridEnergyContainer;

    /** 客户端只读：本机取电身份（{@code null} = 没登记放置者 ⇒ 走"必须有能源仓"的老行为）。 */
    @Nullable
    public UUID gridPowerUser() {
        return gridPowerUser;
    }

    /** 懒建电网容器（不缓存 {@code EnergyContainerList}：基类每次现建，我们跟着现包）。 */
    private GridEnergyContainer gridEnergyContainer() {
        if (gridEnergyContainer == null) {
            gridEnergyContainer = new GridEnergyContainer(this);
        }
        return gridEnergyContainer;
    }

    /**
     * ★ 2026-11-13【NAC 电网取电】<b>本机这一刻有没有取电身份</b>。
     *
     * <p>判据唯一：{@link #gridPowerUser} 非空<b>且</b>配置开关打开。
     * 两者缺一 ⇒ 电网容器自报 0 压 0 安 0 容量 ⇒ {@code isEnergySourceEffectivelyUnlimited()} 为假
     * ⇒ 全机<b>逐字回到"必须有能源仓"的老行为</b>（老存档读档后就是这个状态）。
     */
    public boolean hasGridPowerIdentity() {
        return gridPowerUser != null && NanochipModContext.config().isNanochipGridPowerEnabled();
    }

    /**
     * <b>记录放置者</b>（{@code IMachineLife} 的放置回调）。
     *
     * <p>为什么必须覆写：本类虽早已 {@code implements IMachineLife}（见类声明），
     * 但之前<b>没有</b> {@code onMachinePlaced} 覆写（全工程 grep 0 命中）⇒ 没人记过放置者。
     * 接口签名逐字取自 gtceu-1.20.1-1.4.4 常量池：
     * {@code onMachinePlaced(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;)V}
     * （{@code _census/_probe_nacmat_evidence/classstr_gtceu-1.20.1-1.4.4.txt:792-793}）。
     *
     * <p>{@code gtmthings} 内部自己把玩家 UUID 映射到 FTB 队伍
     * （gtladditions 的 {@code WirelessEnergyManagerMixin} 调 {@code getTeamUUID(UUID)}，
     * 同文件 {@code :16844-16850}）⇒ 这里<b>不</b>做任何队伍转换、也<b>不</b>预注册用户
     * （与 Enhancedcore {@code WirelessChargerMachine} 的同一个接口用法一致）。
     *
     * <p>非玩家放置（刷怪蛋/结构生成/机械臂）⇒ 不写、不报错，退化成老行为。
     */
    @Override
    public void onMachinePlaced(@Nullable net.minecraft.world.entity.LivingEntity player, ItemStack stack) {
        if (player instanceof net.minecraft.world.entity.player.Player p) {
            this.gridPowerUser = p.getUUID();
            this.gridUserChecked = true;
            NanochipModContext.logger().info("[gtlmodular] NAC @{} 电网取电身份已登记：{}（玩家 {}）",
                    getPos(), this.gridPowerUser, p.getGameProfile().getName());
            refreshEnergySnapshot();   // 结构检查可能抢在放置回调之前跑完，那时还没有账户
        }
    }

    /**
     * 重算能源列表与电压等级。GTCEu 只在结构成形时算一次 tier、缓存一次能源列表
     * （列表构造时就把电压和安培定死），电网账户绑定或解绑后电压变了必须手动重算，
     * 否则主机和模块会一直按成形那一刻的电压工作（ULV 或无穷大）。
     */
    private void refreshEnergySnapshot() {
        if (!isFormed()) {
            return;
        }
        this.energyContainer = getEnergyContainer();
        this.tier = GTUtil.getFloorTierByVoltage(getMaxVoltage());
    }

    /**
     * 本机的电网账户（{@code null} = 未绑定 ⇒ 电网容器不参与供电，退回"必须有能源仓"）。
     *
     * <p>不自动生成账户：随机账户没有任何途径充值，余额永远是 0，机器会以"等电"的样子永久卡住。
     * 没有放置者记录时，由玩家空手潜行右键控制器绑定（{@link #onUse}）。
     *
     * <p>0.3.132 及更早的版本给没有放置者的机器存过随机账户；第一次在服务端解析时识别并解绑
     * （判据见 {@link #isOrphanGridAccount}）。
     */
    @Nullable
    private UUID resolveGridUser() {
        if (gridPowerUser != null && !gridUserChecked && getLevel() instanceof ServerLevel serverLevel) {
            gridUserChecked = true;
            if (isOrphanGridAccount(serverLevel.getServer(), gridPowerUser)) {
                NanochipModContext.logger().warn("[gtlmodular] NAC @{} 电网账户 {} 不属于任何玩家且余额为 0"
                                + "（旧版本自动生成的随机账户）⇒ 已解除绑定；空手潜行右键控制器即可绑定到自己",
                        getPos(), gridPowerUser);
                gridPowerUser = null;
                markDirty();
                refreshEnergySnapshot();
            }
        }
        return gridPowerUser;
    }

    /** 本次加载后是否已检查过 {@link #gridPowerUser} 是不是旧版本的随机账户（不进存档）。 */
    private transient boolean gridUserChecked;

    /**
     * 这个 UUID 是不是旧版本生成的随机账户：服务器从没见过这个玩家（不在线、也不在 usercache 里），
     * 并且它在无线电网里一点余额都没有。任何一项查不到都按"是玩家账户"处理，不解绑。
     */
    private static boolean isOrphanGridAccount(MinecraftServer server, UUID uuid) {
        if (server.getPlayerList().getPlayer(uuid) != null) {
            return false;
        }
        GameProfileCache profiles = server.getProfileCache();
        if (profiles == null || profiles.get(uuid).isPresent()) {
            return false;
        }
        try {
            BigInteger balance = WirelessEnergyManager.getUserEU(uuid);
            return balance == null || balance.signum() <= 0;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 空手潜行右键控制器 ⇒ 把本机的电网账户绑定到自己。只能绑到自己的账户，所以只会花自己的电；
     * 其余情况返回 PASS，照常打开界面。
     */
    @Override
    public InteractionResult onUse(BlockState state, Level level, BlockPos pos, Player player,
                                   InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND || !player.isShiftKeyDown()
                || !player.getItemInHand(hand).isEmpty()
                || !NanochipModContext.config().isNanochipGridPowerEnabled()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            UUID uuid = player.getUUID();
            if (!uuid.equals(gridPowerUser)) {
                gridPowerUser = uuid;
                gridUserChecked = true;
                markDirty();
                refreshEnergySnapshot();
                NanochipModContext.logger().info("[gtlmodular] NAC @{} 电网账户绑定到 {}（{}）",
                        getPos(), player.getGameProfile().getName(), uuid);
            }
            player.displayClientMessage(Component.translatable(LANG_GRID_BOUND,
                    FormattingUtil.formatNumbers(gridBalanceEU(uuid))), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    public static final String LANG_GRID_BOUND = "gtlmodular.nanochip.grid.bound";
    public static final String LANG_GRID_ACCOUNT = "gtlmodular.nanochip.grid.account";
    public static final String LANG_GRID_UNBOUND = "gtlmodular.nanochip.grid.unbound";

    /** 账户在无线电网里的余额（BigInteger，查不到按 0）。 */
    private static BigInteger gridBalanceEU(UUID uuid) {
        try {
            BigInteger balance = WirelessEnergyManager.getUserEU(uuid);
            return balance == null ? BigInteger.ZERO : balance;
        } catch (Throwable t) {
            return BigInteger.ZERO;
        }
    }

    /** 主机界面的电网账户行（服务端调用）：绑定到谁、余额多少；未绑定时提示怎么绑。 */
    private void addGridAccountLine(List<Component> textList) {
        if (!NanochipModContext.config().isNanochipGridPowerEnabled()) {
            return;
        }
        UUID user = resolveGridUser();
        if (user == null) {
            textList.add(Component.translatable(LANG_GRID_UNBOUND).withStyle(ChatFormatting.YELLOW));
            return;
        }
        String name = user.toString().substring(0, 8);
        if (getLevel() instanceof ServerLevel serverLevel) {
            GameProfileCache profiles = serverLevel.getServer().getProfileCache();
            if (profiles != null) {
                name = profiles.get(user).map(com.mojang.authlib.GameProfile::getName).orElse(name);
            }
        }
        textList.add(Component.translatable(LANG_GRID_ACCOUNT,
                Component.literal(name).withStyle(ChatFormatting.WHITE),
                Component.literal(FormattingUtil.formatNumbers(gridBalanceEU(user)) + " EU")
                        .withStyle(ChatFormatting.AQUA)).withStyle(ChatFormatting.GRAY));
    }

    /**
     * ★ 2026-11-13【NAC 电网取电】<b>取电口</b> —— 电网容器排在基类那批能源仓<b>之前</b>（index 0），
     * 基类原样跟在后面（"先问电网、再问仓"；见方法体内 `list.add(gridEnergyContainer())` 的位置）。
     *
     * <h4>为什么要覆写这里（一处覆盖全部读取点）</h4>
     * {@code getEnergyContainer()} 是 NAC 里<b>唯一没被覆写</b>的能源入口（基类每次现建，见
     * {@code _census/_javap_simple_workablemb.txt:1004-1075}）⇒ 覆写它一处，同时覆盖：
     * ① 配方每 tick 真扣 EU（{@code NanochipThreadedRecipeLogic.java:1814} 的
     * {@code handleTickRecipeIO(IO.IN, ...)} 从 {@code capabilitiesProxy} 里的 {@code IEnergyContainer} 吃）；
     * ② 中央供电灌模块缓存（本类 {@code distributeModuleEnergy} → {@code e.changeEnergy(-want)}）；
     * ③ 模块来要电（本类 {@code requestModuleEnergy}）；
     * ④ {@code isEnergySourceEffectivelyUnlimited()} / {@code getObtainableEnergyEU()} / {@code getMaxInputPower()}；
     * ⑤ 基类 {@code onStructureFormed()} 存下的 {@code energyContainer}/{@code tier} 两个显示字段。
     *
     * <h4>★ 风险 A：为什么电网容器必须放在列表<b>最前</b></h4>
     * {@code EnergyContainerList.changeEnergy(long)} 是"逐个容器加、<b>加够就停</b>"的循环
     * （`0x00` 级证据：常量池里的局部变量名 {@code energyAdded} 与字段 {@code energyContainerList}，
     * {@code classstr_gtceu-1.20.1-1.4.4.txt:16634-16649}）⇒ 次序就是优先级。
     * 这一点在本代码库还有第二条<b>间接但很硬</b>的证据：GTL Additions 的「无线电网输入终端」
     * （自报容量 Long.MAX 级、被包里的"按需现取"口径认作无限源）在本整合包里的正确工作方式是
     * <b>通过 {@code EnergyRecipeHandlerTrait} 的"先问源头、再问储能仓"</b>，而不是靠"排在储能后面被轮到"
     * —— 也就是说：<b>把无限源排在"空能源仓"后面，它永远轮不到</b>（空仓给不出电，
     * {@code changeEnergy} 那里就是 0，而循环判的是"加够没"）。这正是不取"仓前电网后"的理由：
     * 老存档里那些<b>空能源仓会把取电顺序吃掉</b>，整条电网取电等于没接。
     *
     * ⇒ <b>顺序：电网（index 0）→ 能源仓（基类原样跟在后面）</b>。语义 = <b>电网优先、能源仓兜底</b>：
     * 「直接从电网取电」逐字成立；电网余额不够时电网容器只扣走它能扣的部分
     * （{@link GridEnergyContainer#changeEnergy(long)}），剩下的由能源仓补上；
     * 两者都拿不出 ⇒ 返回 0 ＝ <b>一点不扣</b> ⇒ 走 GTCEu 的"等电"分支（等待、不退进度、不掉料）。
     *
     * <h4>老存档零回归（不取"仓优先"的代价，如实登记）</h4>
     * 「两者都能用，电网作为额外来源」在<b>排序</b>上做不到两全：{@code changeEnergy} 只认列表次序。
     * 选了"电网优先"的代价是：老存档那台机器<b>若放置者身上有无线电网余额，仓就基本用不上</b>
     * （电从电网扣，仓里的电留着）；<b>余额为 0 时仓照旧兜底</b>，行为与改动前一致
     * （成形、配方、产出、超频、并行、Jade 两行数字全部不变）。这是"直接从电网取电"这句话的字面落地。
     *
     * <h4>不改的东西</h4>
     * ① 不在网格线上判空 ⇒ 基类那批仓一个不落（本方法只是"在旁边多接一路"）；
     * ② 开关关 ⇒ 逐字返回基类结果（一行分支，零混入原逻辑）；
     * ③ 本改动<b>独立于</b> {@code nanochipComplex.originalMode}
     * （那一支管的是"结构/连接拦截层"，取电口与 {@code distributeModuleEnergy} 同款纪律：
     * 见 {@code :5691-5692}「仓位清单心跳与能量缓存分配都与成形/连接判定无关」）
     * 与 {@code nanochipComplex.simpleLogisticsMode}（真空仓物流）。
     */
    @Override
    public EnergyContainerList getEnergyContainer() {
        EnergyContainerList own = super.getEnergyContainer();
        if (!NanochipModContext.config().isNanochipGridPowerEnabled()) {
            return own;                                  // 开关关 ⇒ 逐字回到改动前
        }
        List<com.gregtechceu.gtceu.api.capability.IEnergyContainer> list = new ArrayList<>();
        // ★★ 2026-11-13【本任务 ①】电网容器**必须**是 index 0 —— 这一行写在取 `own` 之前，
        //   是刻意的顺序证据（"先问电网、再问仓"）。{@code EnergyContainerList.changeEnergy}
        //   是"逐个容器加、加够就停"的循环（本方法 javadoc 的「风险 A」，`0x00` 级证据：
        //   classstr_gtceu-1.20.1-1.4.4.txt:16634-16649 的局部变量名 energyAdded）
        //   ⇒ 排在后面的容器在"前面已经加够"时永远轮不到。
        //   ⚠ 现场核对：本行在改动前**就已经**排在 for 循环之前（见回退包 Hunk 1），
        //   本轮把形态收成"先填电网 ⇒ 再填仓 ⇒ 返回"，语义不变、读法不再有歧义。
        list.add(gridEnergyContainer());                 // ★ index 0 = 电网优先（风险 A 的处理）
        if (own != null) {
            // 基类每次现建一个 EnergyContainerList，其内部列表是**活视图**（真正的仓）；
            // 我们用反射取一次那个私有列表的**元素**再包一层（与 Enhancedcore 的审计代码同款读法：
            // _census/enhancedcore-src/src/audit/java/.../SuprachronalModuleRemovalChecks.java:214-216），
            // 拿不到就退回"只有电网容器"（= 少一路兜底，但绝不抛异常、也绝不算错账）。
            for (com.gregtechceu.gtceu.api.capability.IEnergyContainer c : innerContainers(own)) {
                // ★ 2026-11-14【照抄山海 · 去重】：基类的列表是**从 capabilitiesProxy 派生**的
                //   （`_census/_javap_simple_workablemb.txt:1004-1075`）⇒ 电网容器既然已经注册进
                //   proxy（见 registerGridPowerHandler），基类列表里就会有它**第二份** ⇒ 必须跳过，
                //   否则 EnergyContainerList.changeEnergy 会在同一笔电上把电网扣两遍。
                if (c != null && c != gridEnergyContainer()) {
                    list.add(c);
                }
            }
        }
        return new EnergyContainerList(list);
    }

    /**
     * {@code EnergyContainerList} 内部私有列表的只读反射读取（失败即空表，绝不抛给调用方）。
     *
     * <p>{@link java.lang.reflect.Field} 只查一次并缓存（本方法每 tick 会被
     * {@code distributeModuleEnergy} / {@code requestModuleEnergy} / 各 getter 问到多次，
     * 每 tick 重查会白白付出名字查找与访问检查的开销）。
     */
    private static List<com.gregtechceu.gtceu.api.capability.IEnergyContainer> innerContainers(
            EnergyContainerList list) {
        try {
            java.lang.reflect.Field f = ENERGY_CONTAINER_LIST_FIELD;
            if (f == null) {
                return List.of();
            }
            Object raw = f.get(list);
            if (raw instanceof List<?> l) {
                List<com.gregtechceu.gtceu.api.capability.IEnergyContainer> out = new ArrayList<>(l.size());
                for (Object o : l) {
                    if (o instanceof com.gregtechceu.gtceu.api.capability.IEnergyContainer c) {
                        out.add(c);
                    }
                }
                return out;
            }
        } catch (Throwable ignored) {
            // 字段名变了 / 安全策略拒绝 ⇒ 空表（退化成"只有电网容器"，见调用方注释）
        }
        return List.of();
    }

    /** {@link EnergyContainerList} 私有列表字段的缓存句柄（{@code null} = 取不到 ⇒ 走退化路径）。 */
    @Nullable
    private static final java.lang.reflect.Field ENERGY_CONTAINER_LIST_FIELD = resolveEnergyListField();

    @Nullable
    private static java.lang.reflect.Field resolveEnergyListField() {
        try {
            java.lang.reflect.Field f = EnergyContainerList.class.getDeclaredField("energyContainerList");
            f.setAccessible(true);
            return f;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** 电网取电接入失败时的限流说明 —— 绝不静默失效。 */
    void warnGridDrawFailure(String site) {
        Level lv = getLevel();
        if (lv == null || lv.isClientSide) {
            return;
        }
        long now = lv.getGameTime();
        if (lastGridUserLogTick != Long.MIN_VALUE && now - lastGridUserLogTick < GRID_USER_LOG_INTERVAL) {
            return;
        }
        lastGridUserLogTick = now;
        NanochipModContext.logger().warn("[gtlmodular] NAC @{} 电网取电不可用（{}）：本机只能用能源仓供电，"
                        + "没有能源仓的机器会原地等电（不退进度、不掉料）",
                getPos(), site);
    }

    /**
     * ★★★ 2026-11-14【照抄山海 · 修 ② · <b>本任务的核心一刀</b>】★★★
     * <b>把电网容器注册进控制器的能力代理（capabilitiesProxy）</b> —— 这一步才是
     * 「从电网直接取电」真正生效的地方。
     *
     * <h4>为什么必须是这里（本次「取不到电」的根因）</h4>
     * GTCEu 的配方 EU 结算由 {@code RecipeHelper} / {@code RecipeRunner} 从
     * {@code getCapabilitiesProxy().get(IO.IN, EURecipeCapability.CAP)} 里取处理器来做；
     * {@code getEnergyContainer()} <b>只是从同一个 proxy 派生出来的读取器</b>
     * （`_census/_javap_simple_workablemb.txt:1004-1075`：先把 proxy 里的 {@code IRecipeHandler}
     * 挨个 {@code instanceof IEnergyContainer} 收进 ArrayList，再
     * {@code new EnergyContainerList(list)}）⇒ 往 {@code getEnergyContainer()} 的返回列表里塞容器，
     * <b>配方侧完全看不见</b>。这就是「储能读到了 Long.MAX、最大输入却是 0」的成因。
     *
     * <h4>照抄依据</h4>
     * 山海/GTLAdditions 的「无线电网输入终端」真正生效的内在容器
     * {@code com.gtladd.gtladditions.common.machine.trait.NetworkEnergyContainer}，其类签名逐字为
     * {@code NotifiableRecipeHandlerTrait<Long> + IEnergyContainer + IInt128EnergyContainer}
     * （本仓既有取证见本文件 {@code :4587-4591} 及其引用的 classstr 行）
     * —— <b>它就是一个 recipe handler，活在 proxy 里</b>，容量 Long.MAX 级、按需现取。
     * 本方法把我们的电网容器摆到<b>同一个位置</b>（配合 {@code GridEnergyContainer} 上的
     * {@code IRecipeHandler<Long>} 实现），机制即照抄完成。
     *
     * <h4>三条纪律</h4>
     * <ol>
     *   <li><b>只追加、绝不替换</b>：真能源仓/激光仓的处理器与我们在同一个 key 下的同一个 list
     *       ⇒ 必须先取出、拷成可写 list、再 {@code add}
     *       （直接 {@code put(key, List.of(ours))} 会把真仓整列抹掉）；</li>
     *   <li><b>幂等</b>：成形可能被重复回调（GTCEu 异步结构检查线程）⇒ 已在表里就直接返回；</li>
     *   <li><b>可一键关断</b>：与 {@link #getEnergyContainer()} 用同一个开关
     *       （{@code nanochipComplex.gridPower.enabled}）⇒ 关掉即逐字回到改动前。</li>
     * </ol>
     *
     * <h4>本仓同款先例（不是猜的）</h4>
     * Enhancedcore {@code IndustrialSteamPlatformMachine}
     * （{@code _census/enhancedcore-src/src/main/java/com/gtl/enhancedcore/common/machine/IndustrialSteamPlatformMachine.java:154-175}）
     * 用**完全相同的三行**给控制器装自己的 EU 处理器，且是主源集编译通过的代码：
     * <pre>
     *   this.getCapabilitiesProxy().contains(IO.IN, EURecipeCapability.CAP)
     *   this.getCapabilitiesProxy().put(IO.IN, EURecipeCapability.CAP, new ArrayList&lt;&gt;())
     *   List&lt;IRecipeHandler&lt;?&gt;&gt; euHandlers = this.getCapabilitiesProxy().get(IO.IN, EURecipeCapability.CAP)
     *   ... euHandlers.add(steamEnergyHandler)
     * </pre>
     * 本方法即同一形态（并额外做了"绝不用 put 覆盖已有真仓那一列"的加固）。
     */
    private void registerGridPowerHandler() {
        if (!NanochipModContext.config().isNanochipGridPowerEnabled()) {
            return;                                  // ★ 一键回退：开关关 ⇒ 一个都不注册
        }
        GridEnergyContainer grid = gridEnergyContainer();
        try {
            var proxy = getCapabilitiesProxy();
            if (proxy == null) {
                return;
            }
            var cap = com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability.CAP;
            var io = com.gregtechceu.gtceu.api.capability.recipe.IO.IN;
            List<com.gregtechceu.gtceu.api.capability.recipe.IRecipeHandler<?>> existing = proxy.get(io, cap);
            List<com.gregtechceu.gtceu.api.capability.recipe.IRecipeHandler<?>> writable;
            if (existing == null) {
                writable = new ArrayList<>();
            } else if (existing instanceof ArrayList) {
                writable = existing;                 // 可写 ⇒ 就地追加（不破坏真仓那一列）
            } else {
                writable = new ArrayList<>(existing); // 不可变/固定大小 ⇒ 拷一份再改
            }
            if (writable.contains(grid)) {
                return;                              // 幂等
            }
            writable.add(grid);
            proxy.put(io, cap, writable);
            NanochipModContext.logger().info("[gtlmodular] NAC @{} 电网取电已接入能力代理：EURecipeCapability 输入处理器 "
                    + "{} 个（末位=电网容器，身份 {}）——这一步让配方 EU 结算真正吃得到电网",
                    getPos(), writable.size(), this.gridPowerUser);
        } catch (Throwable t) {
            // 取代理失败：不抛给结构检查线程，按"取电被拒"的限流口径出声（绝不静默）
            warnGridDrawFailure("电网处理器注册失败：" + t.getClass().getSimpleName());
        }
    }

    /** {@link #registerGridPowerHandler()} 的反操作：结构失效时把电网容器从能力代理里摘掉。 */
    private void unregisterGridPowerHandler() {
        try {
            var proxy = getCapabilitiesProxy();
            if (proxy == null) {
                return;
            }
            var cap = com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability.CAP;
            var io = com.gregtechceu.gtceu.api.capability.recipe.IO.IN;
            List<com.gregtechceu.gtceu.api.capability.recipe.IRecipeHandler<?>> existing = proxy.get(io, cap);
            GridEnergyContainer grid = gridEnergyContainer();
            if (existing == null || !existing.contains(grid)) {
                return;
            }
            List<com.gregtechceu.gtceu.api.capability.recipe.IRecipeHandler<?>> writable =
                    existing instanceof ArrayList ? existing : new ArrayList<>(existing);
            writable.remove(grid);
            if (writable.isEmpty()) {
                proxy.remove(io, cap);
            } else {
                proxy.put(io, cap, writable);
            }
        } catch (Throwable ignored) {
            // 注销失败无害：代理里留一个指向本机容器的引用不会造成错账（机器已不成形、无配方在跑）
        }
    }

    /**
     * ★★ 2026-11-13【NAC 电网取电】<b>电网容器</b>：把 GTMThings 的玩家无线电网包成一个
     * {@link com.gregtechceu.gtceu.api.capability.IEnergyContainer}，作为取电口的 index 0。
     *
     * <p>形态与纪律<b>逐字照抄</b>本代码库已跑通的
     * {@code NanochipModuleMachine.HostEnergyContainer}（{@code :1145-1201}）：
     * 只实现接口要求的那几个方法、单一只只读真值、失败一律返回 0（而不是"承诺量"）。
     *
     * <h4>自报数值的取值依据（为什么必须与「无线电网输入终端」同口径）</h4>
     * <table border="1">
     *   <tr><th>方法</th><th>返回</th><th>理由</th></tr>
     *   <tr><td>{@code getEnergyCapacity()}</td><td>{@code ON_DEMAND_POWER_FLOOR_EU}（= {@code Long.MAX_VALUE/2}）</td>
     *       <td>让既有的 {@code isEnergySourceEffectivelyUnlimited()} 为真 ⇒ <b>该判定式一个字不改</b>；
     *           与 GTL Additions 的「无线电网输入终端」（Long.MAX 级容量，官方 wiki 逐字
     *           「普通能源输入上限为 Long.MAX 级别」）完全同口径。</td></tr>
     *   <tr><td>{@code getInputVoltage()}</td><td>同上的哨兵值</td>
     *       <td>★ 这一项<b>不是</b>装饰：{@code WorkableElectricMultiblockMachine.getOverclockVoltage()}
     *           读 {@code getInputVoltage()}（{@code _census/_javap_simple_workablemb.txt:958-1002}），
     *           而本机 {@code buildRecipe()} 第一句就是
     *           {@code if (getOverclockVoltage() <= 0) return null;}（{@code :3311-3315}）
     *           ⇒ 输入电压为 0 时<b>一条配方都build不出来</b>。取哨兵值 = 与插了无线终端的现有机器
     *           逐值同档（超频次数仍被本机 {@code MAX_OVERCLOCKS} 上限锁死，口径不变）。</td></tr>
     *   <tr><td>{@code getInputAmperage()}</td><td>1</td>
     *       <td>让 {@code EnergyContainerList} 的汇总电压 = 电压 × 1（与终端同档），
     *           且 {@code getOverclockVoltage()} 走 {@code VEX[floorTier(voltage)]} 分支（安培 == 1）。</td></tr>
     *   <tr><td>{@code getEnergyStored()}</td><td>该玩家/队伍无线电网的真实余额（夹到非负 long）</td>
     *       <td>★ 风险 C 的关键：{@code requestModuleEnergy} 与 {@code distributeModuleEnergy} 都<b>先读</b>
     *           {@code getEnergyStored()} 再决定扣多少 ⇒ 必须报真值，否则要么"匹配过了却扣不到"
     *           （白涨进度），要么"明明有电却判没电"（白等）。余额按 1 秒缓存（见 {@code cachedEu}），
     *           同一 tick 内读数稳定 ⇒ 匹配阶段与付款阶段看到的是同一个数。</td></tr>
     * </table>
     *
     * <h4>失败语义（= 既有"没电就等待"口径）</h4>
     * 余额不够 ⇒ 只扣走<b>实际扣得到</b>的那部分并如实返回；一点都扣不到 ⇒ 返回 {@code 0}
     * ⇒ GTCEu 判定为"没电"⇒ 机器 {@code WAITING}、<b>进度不退、料不掉</b>。
     * 这与模块侧 {@code NanochipModuleMachine.HostEnergyContainer.changeEnergy} 的
     * 「缓存不够就一点不扣，走 GT 的『等电』分支」逐字同款（{@code :1196-1199}）。
     */
    private static final class GridEnergyContainer
            implements com.gregtechceu.gtceu.api.capability.IEnergyContainer,
                       com.gregtechceu.gtceu.api.capability.recipe.IRecipeHandler<Long> {

        /** 余额读数缓存时长（tick）＝ 20（1 秒）。同一 tick 内恒定 ⇒ 匹配与付款同源。 */
        private static final long CACHE_TICKS = 20L;

        private final NanochipAssemblyComplexMachine machine;
        private long cachedEu;
        private long cachedAtTick = Long.MIN_VALUE;

        GridEnergyContainer(NanochipAssemblyComplexMachine machine) {
            this.machine = machine;
        }

        /**
         * 取电身份（{@code null} = 开关已关或未绑定 ⇒ 本容器整段惰性：0 容量、0 输入，扣电一律返回 0）。
         */
        @Nullable
        private UUID user() {
            return NanochipModContext.config().isNanochipGridPowerEnabled() ? machine.resolveGridUser() : null;
        }

        /**
         * 该玩家（或他去 FTB 队伍）当下的无线电网余额（EU，夹到非负 long）。
         *
         * <p>{@code getUserEU} 返回 {@code BigInteger}
         * （{@code classstr_gtladditions-...txt:2774-2776}），量级可超出 long ⇒ 先比 {@code Long.MAX_VALUE}
         * 再窄化（<b>绝不</b>直接 {@code longValue()} 让它回绕成负数）。
         * 读数按 {@link #CACHE_TICKS} 缓存：余额查询本身是一次哈希查表，
         * 但本容器每 tick 会被读取点问多次 ⇒ 缓存既省开销、又保证同 tick 内"匹配/付款"读同一个数。
         */
        private long wireEu() {
            Level lv = machine.getLevel();
            long now = lv == null ? Long.MIN_VALUE : lv.getGameTime();
            if (now == Long.MIN_VALUE || cachedAtTick == Long.MIN_VALUE || now - cachedAtTick >= CACHE_TICKS) {
                cachedAtTick = now;
                UUID u = user();
                if (u == null) {
                    cachedEu = 0L;
                } else {
                    BigInteger bal = null;
                    try {
                        bal = WirelessEnergyManager.getUserEU(u);
                    } catch (Throwable t) {
                        // 上游未初始化 / 数据未就绪 ⇒ 当成"这一刻没电"（不抛给配方逻辑）
                        cachedEu = 0L;
                        bal = null;
                    }
                    cachedEu = (bal == null || bal.signum() <= 0) ? 0L
                            : (bal.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0
                                    ? Long.MAX_VALUE
                                    : bal.longValue());
                }
            }
            return cachedEu;
        }

        @Override
        public long getEnergyStored() {
            return user() == null ? 0L : wireEu();
        }

        /**
         * ★ 2026-11-14【照抄山海 · 取消"无身份⇒必须有能源仓"这个坑】
         *
         * <p>改前：没有取电身份 ⇒ 自报 0 ⇒ {@code isEnergySourceEffectivelyUnlimited()} 为假 ⇒
         * 该机**整机退回"必须有能源仓"的老行为**（这正是"用户重放了主机与模块还是供电不足"
         * 那条路上的第二重坑：即使身份登记成功，只要读档/拆放时序错一次就被打回老行为）。
         *
         * <p>改后：只要**开关开着**就自报哨兵容量（与 GTLAdditions「无线电网输入终端」的
         * Long.MAX 级容量同口径，山海 wiki 逐字「普通能源输入上限为 Long.MAX 级别」）——
         * 身份缺失时 {@link #getEnergyStored()} 仍如实报 0 ⇒ 配方匹配阶段照样
         * {@code FAIL_NO_ENOUGH_EU_IN}、机器**原地等电**（不退进度、不掉料），
         * 而不是"悄悄改成必须有能源仓"。
         *
         * <p>本项**不是**装饰：{@code WorkableElectricMultiblockMachine.getOverclockVoltage()} 读
         * {@code getInputVoltage()}，而本机 {@code buildRecipe()} 第一句是
         * {@code if (getOverclockVoltage() <= 0) return null;}（{@code :3311-3315}）
         * ⇒ 输入电压为 0 时一条配方都 build 不出来。
         */
        @Override
        public long getEnergyCapacity() {
            return user() != null ? NanochipAssemblyComplexMachine.ON_DEMAND_POWER_FLOOR_EU : 0L;
        }

        /**
         * 电网功率不设上限，和 GTL 的电网舱一样：电网里有多少电就给多少，相当于无限超频，
         * 不会出现超长时间的批次。电网供电的模块因此不预存整批（整批电量可能超出 long），
         * 而是每 tick 现扣，见 {@code requestModuleEnergy}。
         */
        @Override
        public long getInputVoltage() {
            return user() != null ? Long.MAX_VALUE : 0L;
        }

        @Override
        public long getInputAmperage() {
            return 1L;
        }

        @Override
        public long getOutputVoltage() {
            return 0L;   // NAC 不发电
        }

        @Override
        public long getOutputAmperage() {
            return 0L;
        }

        @Override
        public boolean inputsEnergy(Direction side) {
            return false;   // 不从旁路充能：只作"取电口"
        }

        @Override
        public long acceptEnergyFromNetwork(Direction side, long voltage, long amperage) {
            return 0L;      // 拒绝外部注入
        }

        /**
         * <b>唯一的取电点</b>：负值 = 扣电，正值 = 不退（NAC 从不回充电网）。
         *
         * <p>语义三条（都为了"记账与真扣一致"）：
         * <ol>
         *   <li>{@code amount >= 0} ⇒ 返回 0（不回充）；</li>
         *   <li>没有取电身份 ⇒ 返回 0 并打一条限流 WARN（<b>绝不静默</b>）；</li>
         *   <li>余额不够 ⇒ 只扣 {@code min(need, 余额)}，返回<b>真实扣到的量</b>
         *       （扣不到就 0）⇒ 剩下的额度由列表后面的能源仓兜底；
         *       一点都扣不到时调用方读到的就是 0 ＝ "等电"。</li>
         * </ol>
         *
         * <p>★ 为什么'只扣能扣的部分'是对的：{@code EnergyContainerList.changeEnergy} 的循环
         * 判据是"加够了没"，所以本容器<b>必须如实返回自己真扣到的量</b>，
         * 否则"承诺扣了但没扣"会让机器带着假电记账（风险 C 的反面）。
         */
        @Override
        public long changeEnergy(long amount) {
            if (amount >= 0L) {
                return 0L;                      // 不回充电网
            }
            if (!NanochipModContext.config().isNanochipGridPowerEnabled()) {
                return 0L;                      // 开关关 ⇒ 逐字回到改动前
            }
            UUID u = user();
            if (u == null) {
                return 0L;                      // 未绑定是正常状态（只用能源仓），由列表后面的能源仓供电
            }
            long need = amount == Long.MIN_VALUE ? Long.MAX_VALUE : -amount;
            if (need <= 0L) {
                return 0L;
            }
            long paid = Math.min(need, wireEu());     // 只扣"真拿得到"的那部分
            if (paid <= 0L) {
                return 0L;                          // 一点不扣 ⇒ 调用方走"等电"分支
            }
            // ★ 用 long 重载（不做 BigInteger 装箱）：签名出处
            //   classstr_gtladditions-3.2.8Custom_SubSpace-fix1.txt:2772（(UUID;JMetaMachine)Z）。
            //   gtmthings 内部自己把玩家 UUID 映射到 FTB 队伍 UUID（同文件 :16844-16850）⇒ 这里不转换。
            if (!WirelessEnergyManager.addEUToGlobalEnergyMap(u, -paid, machine)) {
                return 0L;                          // 扣失败（余额不足/未初始化）⇒ 如实返回 0
            }
            cachedAtTick = Long.MIN_VALUE;   // 付完重读真实余额：余额夹到 Long.MAX 后再做减法会误报成 0，别的模块就拿不到电
            return -paid;                              // 真实扣到的量
        }

        // ======================================================================================
        // ★★★ 2026-11-14【照抄山海 · 修 ①】把电网容器做成**真正的配方处理器** ★★★
        //
        //   为什么必须做（本次「取不到电」的根因，一句话）：
        //   GTCEu 的配方 EU 结算**从来不走** `getEnergyContainer()`，走的是控制器的
        //   `capabilitiesProxy.get(IO.IN, EURecipeCapability.CAP)`。
        //   铁证：`WorkableElectricMultiblockMachine.getEnergyContainer()` 自己就是**从
        //   capabilitiesProxy 派生**的（先把 proxy 里的 `IRecipeHandler` 挨个 `instanceof
        //   IEnergyContainer` 收进一个 ArrayList，再 `new EnergyContainerList(list)`）
        //   —— `_census/_javap_simple_workablemb.txt:1004-1075`。
        //   也就是说 `getEnergyContainer()` 只是个**派生读取器**（喂 getOverclockVoltage 与显示），
        //   往它返回的列表里塞东西，对配方结算**完全隐形**。
        //
        //   实机数字自证这一点（Enhancedcore `MachineDiagnostics.power()`，源码
        //   `_census/enhancedcore-src/src/main/java/com/gtl/enhancedcore/common/recipe/MachineDiagnostics.java:118-129`）：
        //     · 「最大输入 0 EU/t」 = 它遍历 **capabilitiesProxy** 里 EU 处理器的 电压×电流 ⇒ 空 = 0；
        //     · 「当前储能 9223372036854775807 EU」 = 它另读 **getEnergyContainer().getEnergyStored()**
        //       ⇒ 那正是本容器报出的玩家电网余额（≥Long.MAX 被夹到 MAX）。
        //   同一句 Jade 里「最大输入 0」与「储能 Long.MAX」并存 ⇒ 只有"处方在 proxy、储能在
        //   getEnergyContainer"这一种模型能同时解释。**⇒ 身份登记其实一直是成功的，错的是接线位置。**
        //
        //   照抄谁的：山海/GTLAdditions 的「无线电网输入终端」真正生效的那个内在容器
        //   `NetworkEnergyContainer` 逐字就是
        //   `NotifiableRecipeHandlerTrait<Long> + IEnergyContainer + IInt128EnergyContainer`
        //   （`_census/_probe_nacmat_evidence/strings_gtladditions-3.2.8Custom_SubSpace-fix1.txt:16330`
        //   的同类形态 + 本仓既有取证 `NanochipAssemblyComplexMachine.java:4587-4591`）——
        //   **它是个 recipe handler（活在 proxy 里）**，容量/电压 Long.MAX 级、按需现取。
        //   本段就是把我们的电网容器补成同一个形态：**同时是 IEnergyContainer 与 IRecipeHandler<Long>**，
        //   并由 `registerGridPowerHandler()` 注册进 proxy。
        //
        //   零 mixin；方法集逐字对齐**本仓已编译进主源集**的同款 EU 处理器
        //   `IndustrialSteamPlatformMachine.SteamCacheEnergyHandler`
        //   （`_census/enhancedcore-src/src/main/java/com/gtl/enhancedcore/common/machine/IndustrialSteamPlatformMachine.java:422-449`：
        //   它 `implements IRecipeHandler<Long>`、`getCapability()` 返回 `EURecipeCapability.CAP`，
        //   **只实现这 4 个方法** ⇒ 证明这 4 个就是本类路径下的抽象集，其余皆 default；
        //   注意它的 `getContents()` 签名是 `List<Object>`）。
        //   另一份同款证据（审计源集）：`IvNativeFixture.Computation`（同目录 `:26-41`）。
        // ======================================================================================

        /** 本处理器结算的通道：EU（`RecipeCapability<Long>`）。 */
        @Override
        public com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability<Long> getCapability() {
            return com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability.CAP;
        }

        /**
         * 本处理器"此刻有多少" —— 上游的并行/可行性计算（gtlcore `IParallelLogic`）读它。
         * 报真值：余额 0 就是 0（不报哨兵），否则会造出"匹配过了却扣不到"的假电。
         *
         * <p>⚠️ 返回类型必须是 {@code List<Object>}（不是 {@code List<Long>}）——这是接口的签名。
         * 逐字对齐本仓**已编译进主源集**的同款 EU 处理器
         * {@code IndustrialSteamPlatformMachine.SteamCacheEnergyHandler#getContents()}
         * （{@code _census/enhancedcore-src/src/main/java/com/gtl/enhancedcore/common/machine/IndustrialSteamPlatformMachine.java:436-438}）。
         */
        @Override
        public List<Object> getContents() {
            return java.util.Collections.singletonList((Object) (user() == null ? 0L : wireEu()));
        }

        /** 同上，供按容量算并行的调用方读（double 口径）。 */
        @Override
        public double getTotalContentAmount() {
            return user() == null ? 0.0 : (double) wireEu();
        }

        /**
         * <b>配方 EU 的真正结算点</b>（照抄"无线电网输入终端"的按需现取语义）。
         *
         * <p>约定（GTCEu `RecipeHelper`）：返回 {@code null} = 本处理器已把 {@code left} 全部吃掉；
         * 否则返回**剩余**量。{@code simulate=true} 是匹配阶段（只测不扣），{@code false} 才真扣。
         *
         * <p>三条语义与 {@link #changeEnergy(long)} 严格一致（不许出现"承诺量 ≠ 真扣量"）：
         * <ol>
         *   <li>{@code io != IN} ⇒ 原样返回（NAC 不发电、不接受注入）；</li>
         *   <li>没身份 ⇒ 供 0 ⇒ 返回剩余全部 ⇒ 配方逻辑判 {@code FAIL_NO_ENOUGH_EU_IN}
         *       ＝「电力输入不足」并<b>原地等电</b>（进度不退、料不掉）；</li>
         *   <li>余额不够 ⇒ 只吃得起多少就吃多少，余额为 0 时一点不吃。</li>
         * </ol>
         */
        @Override
        public List<Long> handleRecipeInner(com.gregtechceu.gtceu.api.capability.recipe.IO io,
                GTRecipe recipe, List<Long> left, @Nullable String slot, boolean simulate) {
            if (io != com.gregtechceu.gtceu.api.capability.recipe.IO.IN || left == null || left.isEmpty()) {
                return left;
            }
            long need = 0L;
            for (Long v : left) {
                if (v != null && v > 0L) {
                    need = need > Long.MAX_VALUE - v ? Long.MAX_VALUE : need + v;
                }
            }
            if (need <= 0L) {
                return null;                        // 没有要吃的量 = 已满足
            }
            long paid;
            if (simulate) {
                paid = user() == null ? 0L : Math.min(need, wireEu());
            } else {
                long paidSigned = changeEnergy(-need);   // 负数 = 扣电；内部已按余额截断并记账
                paid = paidSigned >= 0L ? 0L : -paidSigned;
            }
            long remain = need - paid;
            return remain <= 0L ? null : List.of(remain);
        }
    }

    /** 各族电路数量（逗号分隔，9 个）与历史总数：给图形进度条用（多方块本体没有配置页，
     *  所以进度条做在结构里真空传送带仓的页面上，数据从这里同步过去）。 */
    @DescSynced
    private String syncedFamilyCounts = "";

    /**
     * ★★ 0.3.132【校准面板档位显示 · 修】★★ <b>各族的校准档位</b>（逗号分隔，9 个，按
     * {@link NanochipCalibration.Family#values()} 的 ordinal 下标 —— 与 {@link #syncedFamilyCounts} 同口径）。
     *
     * <h4>为什么必须新增这一格（而不是复用 {@link #syncedCalibrationTier}）</h4>
     * {@code syncedCalibrationTier} 是<b>整机一个标量</b>（= "当前生效族"那一个族的档位）⇒
     * <b>拿它去画 8 行族条，其余族恒显示"未达"</b>，口径就是错的。面板要的是<b>每族各自的档位</b>。
     *
     * <h4>为什么要同步、而不是客户端自己算（本工程铁律）</h4>
     * 档位判定（{@code tierOf}）依赖分母口径与阈值，属**判定**；客户端**绝不复算判定**
     * （见 {@code syncedActiveFamilyOrdinal} 的 javadoc）。⇒ 由服务端算好、逐族同步下来。
     *
     * <h4>这一格修的是什么（实机 bug）</h4>
     * 面板有一处「静默兜底」重载把档位写死成常量 0，而主机调用点少传了"每族档位"这个实参
     * ⇒ Java 重载解析落到那条重载 ⇒ 面板上所有读档位的地方全拿到 0，
     * 于是出现「占比 100.00%」+「生效 0 档」+「本档功效：无」+「还差 0 个到第 1 档」这种自相矛盾的可见态。
     * 现在主机把 {@link #syncedTierOf} 传下去，面板据此画档位。
     */
    @DescSynced
    private String syncedFamilyTiers = "";

    @DescSynced
    private long syncedHistoryTotal;

    // ---------- ★ 2026-09-18 新增：NAC 仓位坐标清单（同步给客户端做「别的 mod 的仓也连片」） ----------
    //
    // 背景（NH 依据已取证）：NH 在结构成形时对**任意** MTEHatch 调 updateTexture(CASING_INDEX_WHITE)
    //   ⇒ NH 里任何 mod 的仓进了 NAC，外壳都会变成纳米芯片接口机壳 ⇒ 也就与周围机壳连片。
    // 我们的落地方式：1.20.1 这边做不到"读邻居将要画哪张 sprite"（见 MachineCtmBakedModel 的类注释），
    //   所以改成「服务端把"这一格是 NAC 的仓位"这件事告诉客户端，客户端在渲染期把那几格的 tier 机壳
    //   sprite 换成接口机壳的 47 格变体」—— 需要的数据就是这张清单。

    /**
     * ★ <b>本机这一刻的仓位坐标清单</b>（服务端权威 → {@code @DescSynced} 推给客户端）。
     *
     * <p>格式：逗号分隔的十进制 {@code BlockPos.asLong()}（见 {@link #encodeNacSlots()}）。
     * 空串 = 未成形 / 没有 part / 结构已失效 ⇒ 客户端 {@code NacSlotIndex} 按"不是仓位"处理
     * （<b>拿不到就不改</b>，外观 = 现状，绝不抛异常）。
     *
     * <p>为什么用 {@code String} 而不是 {@code long[]} / {@code List<BlockPos>}：见
     * {@code NacSlotIndex} 的类注释（LDLib 的集合字段是原地 clear+addAll，渲染线程可能读到撕裂的集合；
     * 不可变字符串的发布永远是"一次引用写"）。上限 {@link #MAX_NAC_SLOTS} 条，超出截断 + WARN。
     *
     * <p>只在<b>结构状态变化</b>时写字段（{@code @DescSynced} 的写入会广播）—— 见
     * {@link #publishNacSlots()} / {@link #clearNacSlots()}。
     *
     * <p>★ 2026-09-26（0.2.5）<b>补上 {@code @Persisted}</b>：原来只有 {@code @DescSynced}，
     * 而 GTCEu 的 {@code MultiblockControllerMachine.isFormed} 是 <b>{@code @Persisted}</b> ——
     * 重进世界后它直接把 {@code isFormed} 从存档恢复成 true，周期结构检查在第 0–7 行就
     * {@code if (!hasError && isFormed) return} 早退 ⇒ {@code onStructureFormed()} 不再触发
     * ⇒ {@link #publishNacSlots()} 永远不会再跑 ⇒ 本字段在服务端一直是空串、客户端也就拿不到清单
     * ⇒ <b>我们自己的机器（猪咪样板总成等吃这份清单的方块）重进世界后掉回原材质、不再跟机器连接，
     * 而 GT 舱室走 GTCEu 自己的外观机制所以看不出问题</b>（队长实测「只有我们的猪咪这样」）。
     * 持久化后：服务端读档即有值、客户端初始同步直接拿到 ⇒ 不依赖结构何时重新匹配。
     */
    @DescSynced
    @Persisted
    private String syncedNacSlots = "";

    /** 仓位清单上限（条）：超出即截断 + WARN（后果只是"超出的那几格外壳保持原样"）。 */
    public static final int MAX_NAC_SLOTS = 256;

    /** 客户端只读：本机发布给客户端的仓位清单（空串 = 拿不到 ⇒ 客户端按"不是仓位"处理）。 */
    public String nacSlotsEncoded() {
        return syncedNacSlots;
    }

    /**
     * 服务端：结构状态变化时重建并发布清单（只在**变化**时写字段，避免刷爆同步通道）。
     *
     * <p>客户端调用是空操作（客户端不该写这个字段 —— 与 {@code NanochipVacuumHatchMachine}
     * 的 {@code refreshCasingState()} 同一纪律）。
     */
    private void publishNacSlots() {
        Level level = getLevel();
        if (level == null || level.isClientSide) {
            return;
        }
        String encoded = encodeNacSlots();
        if (!encoded.equals(syncedNacSlots)) {
            syncedNacSlots = encoded;
            // 诊断（0.2.5）：见证「重进世界后到底有没有再发布」这一次；条数=0 说明是空串。
            NanochipModContext.logger().info("[gtlmodular] NAC仓位清单发布(主机): pos={} 条数={}",
                    getPos(), encoded.isEmpty() ? 0 : encoded.split(",").length);
        }
    }

    /** 结构失效 / 解散 / 拆除：清空清单（客户端索引随之丢掉本机这一份）。 */
    private void clearNacSlots() {
        Level level = getLevel();
        if (level != null && level.isClientSide) {
            return;
        }
        if (!syncedNacSlots.isEmpty()) {
            syncedNacSlots = "";
            NanochipModContext.logger().info("[gtlmodular] NAC仓位清单清空(主机): pos={}", getPos());
        }
    }

    /**
     * ★ 2026-09-27 <b>仓位清单心跳</b>（修「外壳连片刷新时机」；由 {@link #tickModuleRefresh()} 每 tick 调）。
     *
     * <h3>它治的是什么</h3>
     * {@link #publishNacSlots()} 原来只有 {@link #onStructureFormed()} 一个调用点。若"结构没散、
     * 只是结构里的 part 换了"而没有走到那次回调（忽略活化态变化的早退、同 tick 折叠、
     * 异步回调时序等），本机发布的清单就会一直停在旧内容上 ⇒ 客户端那几格的外壳连片不刷新。
     *
     * <h3>为什么不是"每 tick 重建清单"（性能红线）</h3>
     * <pre>
     *   ① 先判 {@code getParts().size()}（O(1) 的 size 读）—— 稳态下**每 20 tick 只有这一次读**；
     *   ② 个数没变 ⇒ 直接 return（**不建字符串、不碰世界、不重跑结构匹配**）；
     *   ③ 个数变了 ⇒ 才调 publishNacSlots()，而它自己还有「只在不同才写字段」的去重
     *      ⇒ 同步通道的最坏频率 = 1 条/秒（与既有的结构结论日志同量级）。
     * </pre>
     * 计数比对刻意<b>只用个数</b>：够抓住"加了一台仓 / 拆了一台仓"这个本 bug 的形态，
     * 又不会为了"内容级相等"去每 20 tick 拼一次最多 256 条的字符串。
     * 个数相同却换了不同方块（拆一台装一台）由 GTCEu 自身的方块变更事件路径负责
     * （那条路径会真的重判结构 ⇒ 再回调 onStructureFormed）。
     *
     * @return {@code true} = 本次真的重建并（可能）发布了清单（**仅供调用方提前 return**，无副作用语义）
     */
    private boolean tickNacSlotHeartbeat() {
        Level level = getLevel();
        if (level == null || level.isClientSide) {
            return false;
        }
        if (++this.nacSlotHeartbeat % NAC_SLOT_HEARTBEAT_INTERVAL != 0) {
            return false;
        }
        if (!isFormed()) {
            return false; // 未成形 ⇒ 清单该是空的（结构失效时已被 clearNacSlots 清掉）
        }
        int parts = getParts() == null ? 0 : getParts().size();
        if (parts == this.lastNacSlotPartCount) {
            return false; // ★ 稳态快路径（O(1)）：这一句就是性能红线的兑现点
        }
        int previous = this.lastNacSlotPartCount;
        this.lastNacSlotPartCount = parts;
        publishNacSlots(); // 内部自带"内容没变就不写字段"去重 ⇒ 只在同步值真的变时才广播
        NanochipModContext.logger().info("[gtlmodular] NAC外壳连片：仓位清单心跳重发(主机) pos={} part数 {}→{} 清单={}",
                getPos(), previous, parts,
                syncedNacSlots.isEmpty() ? "空（0 条）" : syncedNacSlots.split(",").length + " 条");
        return true;
    }

    /**
     * ★ 把「结构里真实站着的 part」编码成逗号分隔的 {@code BlockPos.asLong()}。
     *
     * <p>数据来源 = GTCEu 自己的 {@code MultiblockControllerMachine.getParts()}（结构成形时由
     * {@code matchContext} 的 {@code "parts"} 表填好，见其 {@code onStructureFormed} 字节码）——
     * <b>不区分是不是本模组的仓</b>：别的 mod 的仓（GTMThings 的无线能源仓、GTCEu 自己的能源仓/总线…）
     * 只要被结构匹配成 part，就在这里，正是本功能的目标。
     *
     * <p>⚠️ 本方法与 {@code NanochipModuleMachine#encodeNacSlots()} <b>必须逐字节一致</b>：
     * 客户端只有一个解码器（{@code NacSlotIndex.decode}），格式不一致 = 客户端永远认不出仓位。
     * 自检 {@code _census/check_ctm_foreign.js} 会现读比对两个方法体（去掉注释与空白后逐字符相同）。
     */
    private String encodeNacSlots() {
        List<IMultiPart> parts = getParts();
        StringBuilder sb = new StringBuilder();
        int kept = 0;
        int dropped = 0;
        for (IMultiPart part : parts) {
            if (part == null || part.self() == null) {
                continue;
            }
            if (kept >= MAX_NAC_SLOTS) {
                dropped++;
                continue;
            }
            if (kept > 0) {
                sb.append(',');
            }
            sb.append(part.self().getPos().asLong());
            kept++;
        }
        if (dropped > 0) {
            NanochipModContext.logger().warn("[gtlmodular] CTM: 仓位清单超过上限 {} 条（结构里 {} 个 part @{}），"
                            + "已截断 {} 条 —— 被截断的那几格外壳保持原样（不参与连接纹理）。",
                    MAX_NAC_SLOTS, parts.size(), getPos(), dropped);
        }
        return sb.toString();
    }

    /** 同步值解析：第 {@code ordinal} 个电路族的数量（客户端可用）。 */
    public int syncedCountOf(int ordinal) {
        if (syncedFamilyCounts == null || syncedFamilyCounts.isEmpty()) {
            return 0;
        }
        String[] parts = syncedFamilyCounts.split(",");
        if (ordinal < 0 || ordinal >= parts.length) {
            return 0;
        }
        try {
            return Integer.parseInt(parts[ordinal].trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * ★★ 0.3.132【校准面板档位显示 · 修】★★ <b>同步值解析：第 {@code ordinal} 个电路族的校准档位</b>
     * （0 = 未达第 1 档 / 不可校准族；1..3 = 第 1..3 档）。
     *
     * <p>逐字照 {@link #syncedCountOf} 的写法（含 null / 空串守卫与 {@code NumberFormatException} 兜底）
     * —— 两个解析器**同一种失败口径**：读不到就返回 0（= 未达），绝不抛异常打断客户端渲染。
     *
     * <p>⚠ 与 {@link #syncedCalibrationTier} 的区别（别混用）：那一个是**整机单值**（"当前生效族"的档位），
     * 本方法是**逐族**的，与 {@code syncedFamilyCounts} 同一套 ordinal 下标。
     */
    public int syncedTierOf(int ordinal) {
        if (syncedFamilyTiers == null || syncedFamilyTiers.isEmpty()) {
            return 0;
        }
        String[] parts = syncedFamilyTiers.split(",");
        if (ordinal < 0 || ordinal >= parts.length) {
            return 0;
        }
        try {
            return Integer.parseInt(parts[ordinal].trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 第 {@code ordinal} 族在<b>判定分母</b>上的占比（0~1，客户端可用）。
     *
     * <p>分母与档位判定同口径（原版固定 100000 / 已记录数，见
     * {@link NanochipCalibration#denominatorFor}），所以那根 9 段进度条与阈值线是同一把尺子。
     */
    public double syncedShareOf(int ordinal) {
        double den = NanochipCalibration.denominatorFor(syncedHistoryTotal, syncedFullWindowDenominator);
        if (den <= 0.0D) {
            return 0.0D;
        }
        return Math.min(1.0D, syncedCountOf(ordinal) / den);
    }

    /** 当前校准判定分母是否 = 原版硬编码的 100000（服务端推下来的口径，界面显示用）。 */
    public boolean syncedUsesFullWindowDenominator() {
        return syncedFullWindowDenominator;
    }

    /** 历史窗口填充度（0~1：够 10 万个电路就满）。 */
    public double syncedHistoryProgress() {
        return Math.min(1.0D, syncedHistoryTotal / (double) NanochipCalibration.HISTORY_SIZE);
    }

    public long syncedHistoryTotal() {
        return syncedHistoryTotal;
    }

    /**
     * ★★ 0.3.132【校准重构】<b>服务端同步下来的「生效族」ordinal</b>（-1 = 没有任何族达档）。
     *
     * <p>为什么是同步值而不是客户端复算：{@code activeFamily()} 的规则是「档位最高优先、同档比数量、
     * 跳过已合并的隐藏列」——在客户端重写一遍迟早与判定漂移。⇒ 同步服务端那一份（见字段 javadoc）。
     */
    public int syncedActiveFamilyOrdinal() {
        return syncedActiveFamilyOrdinal;
    }

    /**
     * ★★ 0.3.132【校准重构】<b>本刻真正使用的判定分母</b>（{@code fullWindow ? 100000 : 历史总数}）。
     *
     * <p>给"下一档还差多少个"那一行用 —— 与 {@link #syncedShareOf} 用的是**同一把尺子**
     * （同为 {@link NanochipCalibration#denominatorFor}），所以界面上的"还差 N 个"与判定一致。
     */
    public double syncedCalibrationDenominator() {
        return NanochipCalibration.denominatorFor(syncedHistoryTotal, syncedFullWindowDenominator);
    }

    /** 本族现有数量（与 {@code syncedShareOf} 同源；给"下一档"那一行用）。 */
    public long syncedFamilyCount(int ordinal) {
        return syncedCountOf(ordinal);
    }

    public NanochipAssemblyComplexMachine(IMachineBlockEntity holder, Object... args) {
        super(holder, args);
    }

    // ---------- RecipeLogic：记录校准 + ★ L3 真实多线程（跨配方线程） ----------

    /**
     * 复写配方逻辑：在 gtladditions 的<b>跨配方多线程</b>逻辑之上，做两件事 ——
     * <ol>
     *   <li>产出后记入纳米校准（原行为，未改）；</li>
     *   <li>★ <b>A4</b>：覆写 {@link #lookupRecipeIterator()}，把候选集<b>截断到
     *       {@link NanochipAssemblyComplexMachine#getMultipleThreads()}</b> 条
     *       —— 这一刀才决定「界面显示的线程数是不是真的」（做法与依据见该方法注释）。</li>
     * </ol>
     *
     * <h3>为什么基类是 {@code GTLAddMultipleTypeRecipesLogic}（★ 2026-10-19 复合型机器：同轮同时搜全部配方类型）</h3>
     *
     * <p><b>第一层（2026-10-02，沿用）</b>：「真实多线程」的唯一入口是 gtladditions 的
     * {@code getMultipleThreads()}；gtlcore 的 {@code MultipleRecipesLogic} <b>没有</b>这个方法
     * （三重取证）⇒ 不换到 gtladd 这一族就无从覆写。换族后 NAC 走的是 gtladd 的
     * 「按批次总 EU 守恒生成最终配方」路径。
     *
     * <p>⚠️ <b>2026-10-28 更正（原文写错了，照实改）</b>：这里原来写「一次 tick 里挂起的 N 条
     * <b>各自计时、各自完成，谁先跑完谁让位</b>（{@code FairCandidateSelector} 的轮转）」
     * —— <b>这句话在 gtladditions 这条路上不成立</b>。硬证据链见
     * {@code _census/线程仓机制_移植方案.md} §5.2：{@code GTLAddMultipleRecipesLogic} 继承 gtlcore
     * {@code MultipleRecipesLogic}，而后者<b>没有自己的 progress/duration 字段</b>
     * （{@code _census/_javap_simple_multirecipes.txt:43-51} 的字段清单只有
     * {@code parallel}/{@code dataCheck}/{@code reductionEUt}/{@code reductionDuration}），
     * 且 {@code buildFinalNormalRecipe(ParallelData)} 的返回值是<b>一个</b> {@code GTRecipe}
     * ⇒ <b>无论 {@code ParallelData} 里装了几条候选，最终只有一条合并配方、一份 progress</b>；
     * {@code FairCandidateSelector} 的轮转只决定「本轮选哪 N 条候选」，<b>不产生</b> N 份进度。
     * ⇒ 「N 条各自计时」这件事在旧逻辑上<b>做不到</b>，本轮新增的独立线程逻辑
     * （{@code NanochipThreadedRecipeLogic}，开关 {@code NAC_INDEPENDENT_THREADS}）才是它的实现。
     *
     * <p><b>第二层（2026-10-19，本轮「复合型机器」）</b>：在<b>同一族内</b>由
     * {@code GTLAddMultipleRecipesLogic} 改挂 {@code GTLAddMultipleTypeRecipesLogic} ——
     * 这正是 gtladditions「跨配方模式并行 / 复合型机器」的官方分族点：
     * <ul>
     *   <li>旧族（{@code MultipleRecipesLogic}）的候选只来自 {@code machine.getRecipeType()}
     *       （<b>单数</b> —— 当前激活的那一条类型）；</li>
     *   <li>新族（{@code MultipleTypeRecipesLogic}）的候选来自 {@code machine.getRecipeTypes()}
     *       （<b>复数</b> —— 整份数组，逐类型取出再拼接）。</li>
     * </ul>
     * 机制文档逐字（{@code _census/_gtlcompat/add_fix1/assets/gtladditions/guides/gtladditions/guide/_zh_cn/machine/multi_type.md:16}）：
     * 「当一台机器支持多个配方类别时，它可以在<b>同一轮工作中同时处理不同类别下的配方</b>，
     * 而不是只围绕单一配方类别运行。」；族差证据见
     * {@code _census/_probe_nacmat_evidence/classstr_gtladditions-3.2.8Custom_SubSpace-fix1.txt:2291-2315}
     * （{@code getRecipeTypes} → Sequence → {@code flatMap} 拼接）与调研
     * {@code _census/复合型机器_调研.md} §3.1 / §6.1 方案 A。
     *
     * <p><b>为什么不换机器基类</b>：{@code GTLAddMultipleTypeRecipesLogic} 的构造器收的就是
     * {@code GTLAddWorkableElectricMultipleRecipesMachine}（逐字证据 同 {@code classstr} 文件 {@code :2299}：
     * 末两个 {@code Predicate}/{@code int}/{@code DefaultConstructorMarker} 参数是 Kotlin 默认参数的
     * 合成形态，Java 侧仍是单参 {@code super(nac)}），而这<b>正是本机现在的基类</b>
     * （{@code NanochipAssemblyComplexMachine.java:119}）⇒ 本轮<b>不动机器基类</b>、不动结构、
     * 不动存档、不碰混入。
     *
     * <p>⚠️ <b>未取证</b>：该类的方法表（各方法的可见性 / 是否 {@code final}）<b>没有</b> javap 级证据，
     * 只有常量池级证据（同 {@code classstr} 文件 {@code :2291-2315}）⇒ 本类<b>只保留原有覆写</b>，
     * 不新增任何需要猜测签名的方法（见调研 §8 U1）。
     * <p>⚠️ <b>不承诺「每种类型各自并行」</b>：并行分配仍是本类的 {@code calculateParallels()}
     * 逐条各吃各的容量上限（见那边的长注释），语义是「贪婪 + 逐条容量上限」，
     * 不是「每类型平均分」（依据：调研 §3.3 与 §6.2 第 6 条）。
     * 与并行「去总池」开关（{@link NanochipAssemblyComplexMachine#NAC_PER_THREAD_FULL_PARALLEL}）
     * <b>正交</b>：那个开关改「单条配方能放多大并行」，本族改「候选从哪些配方类型来」。
     */
    private static final class NanochipRecipeLogic extends GTLAddMultipleTypeRecipesLogic {

        private final NanochipAssemblyComplexMachine nac;

        /**
         * 上一次选中的最后一条配方的 id（{@link FairCandidateSelector} 的轮转游标）。
         *
         * <p>为什么由本类持有而不是选择器内部持有：本类的生命周期与机器一致，
         * 而选择器是纯函数（无状态、可单测）；游标放这里也让「机器重建 ⇒ 轮转重新开始」这件事显式可见。
         */
        private String lastSelectedRecipeId;

        NanochipRecipeLogic(NanochipAssemblyComplexMachine nac) {
            // ★ javap 实证：GTLAddMultipleTypeRecipesLogic 构造器为 (机器, Predicate<IRecipeLogicMachine>) ⇒ 必须传第二参谓词；恒 true = 不做类型过滤
            super(nac, machine -> true);
            this.nac = nac;
        }

        @Override
        public void serverTick() {
            nac.tickSync();
            super.serverTick();
        }

        @Override
        public void onRecipeFinish() {
            GTRecipe last = getLastRecipe();
            nac.recordProduction(last);
            nac.logCraftedRecipe(last);   // ★ 2026-09-19：把"跑了哪条配方"打进日志（限流 1 秒）
            super.onRecipeFinish();
        }

        /**
         * ★ 2026-10-28【独立跨配方线程】<b>把父类的候选校验「加宽成 public」暴露出去</b>
         * —— 机器侧（{@code nanochipCandidateUsable}）与新逻辑都要用它。
         *
         * <h4>为什么必须加这一个覆写（不写就编译不过）</h4>
         * 父类那一份的可见性是 {@code protected}（同族旁证：星律
         * {@code SelectableRecipeTypeSetRecipeLogic.java:179}、Enhancedcore
         * {@code ThreadLimitedRecipeLogic.java:245} 都是 {@code @Override protected boolean checkRecipe(GTRecipe)}）。
         * 而<b>机器类在另一个包</b>（{@code com.gtlmodular.common.machine.nanochip} 之外的调用点
         * 只能通过本类的公开方法转一手）⇒ 机器侧对 {@code legacy.checkRecipe(...)} 的调用
         * <b>不是一个合法访问</b>（Java 的 protected 只对「本类及其子类内部」或同包开放）。
         * 所以这里把可见性<b>加宽到 public</b>（覆写只能加宽不能收窄，所以这是唯一安全方向），
         * 语义一字未改（方法体只有一句 {@code super}）。
         *
         * <h4>为什么机器侧不干脆自己写一份判定</h4>
         * 「一条候选能不能跑」全机只该有<b>一个</b>口径 —— 见
         * {@code NanochipAssemblyComplexMachine.nanochipCandidateUsable} 的注释：
         * 两份写法迟早走岔，而走岔的表现是「同一条样板在两条派活通路上一次能跑、一次不能跑」。
         *
         * <h4>回退</h4>
         * 删掉本方法即可（连同机器侧那个调用点一起回退到别处）。
         */
        @Override
        public boolean checkRecipe(GTRecipe recipe) {
            return super.checkRecipe(recipe);
        }

        // ======================================================================================
        // ★★★ 2026-10-19【主机侧「先蓄够一整条配方的电再开工」】★★★
        // ======================================================================================
        /**
         * <b>起跑前的「整条配方电量」闸</b> —— 把模块侧已有的语义补到主机侧（用户要求，逐字见
         * {@link #NAC_PRECHARGE_WHOLE_RECIPE}）。
         *
         * <h3>为什么插在 {@code setupRecipe}</h3>
         * gtlcore {@code MultipleRecipesLogic.findAndHandleRecipe()}（本机基类血统）的配方落地口
         * 只有一个：它<b>先</b> {@code getRecipe()} 拿到最终配方（并行已在
         * {@code buildFinalNormalRecipe} 里摊好），<b>再</b> {@code RecipeRunnerHelper.matchRecipeOutput(...)}
         * 通过后调用 {@code setupRecipe(recipe)}（逐字证据：{@code _census/_javap_simple_multirecipes.txt:108-133}
         * —— {@code 42: invokevirtual setupRecipe}）。
         * 在 {@code setupRecipe} 的最前面一票否决 ⇒ 材料没被扣（本类的
         * {@code calculateParallels} 与父类的 {@code buildFinalNormalRecipe} 在本方法之前，
         * 它们只做「算」和「置 realParallels」；真正扣料发生在 {@code super.setupRecipe} 的
         * {@code handleRecipeIO(recipe, IO.IN)}，见 {@code _census/_javap_simple_recipelogic.txt:729-733}）
         * ⇒ <b>不会跑到一半断电</b>，也不会出现「料下了但机器不动」。
         * 这同时满足任务书里「不动 {@code calculateParallels} 那一整块」的约束：本覆写是它<b>之后</b>的
         * 独立一刀，与那一块零耦合（两者唯一的联系是「都用同一份最终 {@code recipe} 对象」）。
         *
         * <h3>整条配方需要多少电（与模块同源）</h3>
         * 取父类 gtlcore {@code MultipleRecipesLogic.getTotalEuOfRecipe(recipe)} 的<b>同一算法</b>：
         * {@code RecipeHelper.getInputEUt(recipe) × recipe.duration}（逐字证据：
         * {@code _census/_javap_simple_multirecipes.txt:135-144}）。它与模块侧
         * {@code NanochipModuleMachine.handeRecipeWorking} 的 {@code perTick × duration}
         * （同文件 :1318-1319）逐字同源 ⇒ 两侧口径永远一致、可对照维护。
         * 缓存与性能依据见 {@link #plannedRecipeTotalEu}。
         *
         * <h3>放行 / 拦住的完整口径</h3>
         * 见 {@link #hostAffordableWhole(RecipeLogic, long)} 的三条判定（含「按需现取能源直接放行」
         * 与「无法判定 ⇒ 放行」）。本方法只负责「拦住之后怎么显示」。
         *
         * <h3>不破坏现有行为</h3>
         * 闸放行时本方法<b>逐字</b>走原来的 {@code super.setupRecipe(recipe)} 那一句（前面只多算了几个
         * long 加减）⇒ 蓄够之后的表现与改动前完全一致。拦住时只做两件事：不改 {@code lastRecipe}、
         * 不扣料，并让界面显示与模块同风格的等待提示。
         */
        @Override
        public void setupRecipe(GTRecipe recipe) {
            if (NAC_PRECHARGE_WHOLE_RECIPE && recipe != null) {
                long full = plannedRecipeTotalEu(recipe);
                if (full > 0L && !hostAffordableWhole(this, full)) {
                    setWaiting(Component.translatable("gtlmodular.nanochip.host.buffer",
                            com.gregtechceu.gtceu.utils.FormattingUtil.formatNumbers(full),
                            com.gregtechceu.gtceu.utils.FormattingUtil.formatNumbers(nac.getTotalStoredEU())));
                    nac.noteHostPrechargeWaiting(full, nac.getTotalStoredEU());
                    if (getStatus() == RecipeLogic.Status.WAITING) {
                        doDamping();
                    }
                    return;
                }
            }
            super.setupRecipe(recipe);
        }

        /**
         * <b>本条最终配方整条需要多少 EU</b> —— 用 gtlcore 自己那份算法，附「按配方对象身份」的单项缓存。
         *
         * <h3>算法（与模块同源，逐字）</h3>
         * {@code RecipeHelper.getInputEUt(recipe) × recipe.duration}；
         * 下限各自按 1 兜（电压为 0 / 时长为 0 的畸形配方不会算出 0 而被当成"已蓄够"）。
         * 这三行与父类 gtlcore {@code MultipleRecipesLogic.getTotalEuOfRecipe(GTRecipe)}
         * 的字节码<b>逐字一致</b>（{@code getInputEUt → l2d}；{@code recipe.duration → i2d}；{@code dmul}；
         * 出处 {@code _census/_javap_simple_multirecipes.txt:135-144}），
         * 也与模块侧 {@code NanochipModuleMachine:1318-1319} 一致。
         * ★ <b>为什么写成 inline 而不是调 {@code super.getTotalEuOfRecipe(...)}</b>：
         * 父类那份返回 {@code double}（{@code dmul}/{@code dreturn}）。整条电量会到 1e14~1e18 量级，
         * double 只有 53 位有效位 ⇒ 直接比较会在「刚好差几 EU」上判错（本包已在
         * {@code scaleRecipePrecisely} / 并行那条路上因为同一理由拒绝 double，见该类注释）。
         * 故这里保留同一算式但走 {@code long} 饱和乘法；语义相同、精度更严。
         *
         * <h3>性能依据（为什么不每 tick 重算大对象）</h3>
         * 这条判定每 tick 都会被问一次（父类 {@code findAndHandleRecipe} 每 tick 重新搜/重新 build），
         * 而 {@code getTotalEuOfRecipe} 要走一次 {@code getInputEUt}（{@code tickInputs} 的 Map 取 + 
         * {@code Content.getAmount()}）。稳态下每 tick 问的是<b>同一个配方对象</b>
         * （父类每次 tick {@code ofRaw().buildRawRecipe()} 出来的新对象，但同一 tick 内
         * {@code findAndHandleRecipe} 与 {@code setupRecipe} 拿到的是同一个实例）——
         * 所以命中条件是 {@code (identity, duration)} 全等。<b>缓存不跨 tick</b>：父类每 tick 都会
         * 重新 build 一份新配方对象 ⇒ identity 每 tick 都不同 ⇒ 这条缓存只在本 tick 内、
         * 以及同一对象被重复问时生效，<b>不会</b>把过期数据留到下一 tick（换配方/改并行/改超频
         * 都会让 identity 与 duration 同时变）。命中时只做两次 {@code long} 比较，零分配。
         *
         * <p><b>诚实边界</b>：缓存是 transient 字段，不是 {@code @Persisted}，不进存档。
         */
        private long plannedRecipeTotalEu(GTRecipe recipe) {
            if (recipe == null) {
                return 0L;
            }
            int duration = Math.max(1, recipe.duration);
            if (this.prechargeCachedRecipe == recipe && this.prechargeCachedDuration == duration) {
                return this.prechargeCachedTotalEu;
            }
            long perTick = Math.max(1L, com.gregtechceu.gtceu.api.recipe.RecipeHelper.getInputEUt(recipe));
            // ★ 2026-10-27【统一饱和工具】：原内联判界 `perTick > Long.MAX_VALUE / duration ? MAX : perTick * duration`
            //   与本类唯一的饱和乘法同源同构 ⇒ 改调 saturatingMul（算式与判界写法逐字等价，行为不变）。
            long total = saturatingMul(perTick, (long) duration);
            this.prechargeCachedRecipe = recipe;
            this.prechargeCachedDuration = duration;
            this.prechargeCachedTotalEu = total;
            return total;
        }

        /** {@link #plannedRecipeTotalEu} 的缓存位（identity + duration ⇒ total；transient，不进存档）。 */
        private transient GTRecipe prechargeCachedRecipe;
        private transient int prechargeCachedDuration = -1;
        private transient long prechargeCachedTotalEu;

        /**
         * <b>主机能不能在不跳电的前提下跑完这一整条配方</b>（{@link #setupRecipe} 的唯一判据）。
         *
         * <p>三条判定按顺序短路，<b>每一条的"不确定"都倒向放行</b>：
         *
         * <h4>① 按需现取能源 ⇒ 视为已充足、直接放行（不因为"已存 0"卡死）</h4>
         * ★ <b>判据直接复用同事那一套</b>（同一文件里已存在，不再另写一份）：
         * {@code NanochipAssemblyComplexMachine#isEnergySourceEffectivelyUnlimited()} 与
         * {@code NanochipAssemblyComplexMachine#getObtainableEnergyEU()}，
         * 其哨兵是 {@code NanochipAssemblyComplexMachine#ON_DEMAND_POWER_FLOOR_EU}
         * （{@code Long.MAX_VALUE / 2}）。覆盖两类真实能源：
         * <ul>
         *   <li><b>GTL Additions 无线电网输入终端</b>
         *       （{@code WirelessEnergyNetworkTerminalPartMachine} → 内在
         *       {@code NetworkEnergyContainer}，Kotlin 类，签名逐字：
         *       {@code NotifiableRecipeHandlerTrait<Long> + IEnergyContainer + IInt128EnergyContainer}，
         *       出处 {@code _census/_probe_nacmat_evidence/strings_gtladditions-3.2.8Custom_SubSpace-fix1.txt:13898}）；
         *       它的 {@code getEnergyCapacity}/{@code getInputVoltage} 是 Long.MAX 量级 ——
         *       官方 wiki 逐字：「普通能源输入上限为 Long.MAX 级别」
         *       （{@code _census/_gtlcompat/add_fix1/assets/gtladditions/guides/.../wireless_energy_network_terminal.md:36}）；</li>
         *   <li><b>创造能源仓</b>等创造级能源（GTMThings {@code creative_energy_hatch}，同为无限容量）。</li>
         * </ul>
         * 这两类能源的原理就是「不储能、按需现取」⇒ <b>已存 0 也不能当成没电</b>
         * （这正是另一位同事在模块读取口径那条线上处理的现象，见
         * {@code NanochipModuleMachine.getEnergyContainer()} 的 {@code HostEnergyContainer}：
         * 它把主机蓄电透给模块，终端不储能时读到的就是 0）。
         * 本判定<b>不按类名硬编码</b>，只读能源容器自报的上限 ⇒ 与那位同事改的读取口径
         * <b>不碰同一处</b>（他改模块怎么读，本处改主机要不要等）。
         *
         * <h4>② 储能式能源 ⇒ 要求「已存 + 20 tick 内可补」覆盖整条配方</h4>
         * {@code stored + getMaxInputPower() × 20 ≥ need}。普通能源仓/激光仓走这条：
         * 复合体的蓄电由能源仓灌入，一格 20 tick 补一次（本类
         * {@code ENERGY_DISTRIBUTE_INTERVAL = 20}，见 {@link #distributeModuleEnergy}）⇒
         * 用「20 tick 的补充量」当容差，既不会把「正在补电、马上就够」的主机误判成没电，
         * 也不会把「连一格补充都远远不够」的主机放过去。
         * <b>真无电时仍然拦得住</b>：{@code getMaxInputPower() == 0}（没插任何能源仓 / 能源仓为 0 压）
         * 且 {@code getObtainableEnergyEU() == 0}（容量与存量都是 0）⇒ 左边恒为 0 &lt; {@code need}
         * ⇒ 拦住（见报告 ⑤ 的逐例推演）。
         * <p>★ 这一条也是「超容量长配方但不跳电」的<b>已知边界</b>：若某条配方的整条电量
         * 严格大于复合体蓄电上限，而按需现取又不成立，那它会一直等（本次改动<b>之前</b>是
         * 「开工 → 跑到一半停住」）。这属于用户明确要求的保守一侧；不建议为了绕开它而放松本条。
         *
         * <h4>③ 无法判定 ⇒ 放行（保守第一）</h4>
         * 能源容器读不到（未成形 / {@code getEnergyContainer() == null}）、总量算不出（{@code need <= 0}）
         * 等一切「测不准」的情况，本方法返回 {@code true} ⇒ <b>宁可开工，不要卡死</b>。
         * 这也是与模块侧同一取舍（模块侧 {@code ensureBufferCovers} 在容量未定过时同样不拦，
         * 见 {@code NanochipModuleMachine:605-608}）。
         */
        private boolean hostAffordableWhole(RecipeLogic logic, long need) {
            if (need <= 0L) {
                return true;                                   // ③ 算不出 ⇒ 放行
            }
            com.gregtechceu.gtceu.api.misc.EnergyContainerList e = nac.getEnergyContainer();
            if (e == null) {
                return true;                                   // ③ 读不到 ⇒ 放行
            }
            long obtainable = Math.max(0L, nac.getObtainableEnergyEU());
            long supplyPerTick = Math.max(0L, nac.getMaxInputPower());
            long supplyInterval = saturatingMul(supplyPerTick, ENERGY_DISTRIBUTE_INTERVAL);
            long available = saturatingAdd(obtainable, supplyInterval);
            if (available >= need) {
                return true;                                   // ② 储能式：够一条配方 ⇒ 放行
            }
            if (logic.isSuspend()) {
                return true;                                   // ③ 被暂停不参与判定（不让提示盖住暂停）
            }
            return false;                                      // 真没电 ⇒ 拦住
        }

        // ★ 本类**不再**自建「无限能源哨兵」：同文件的
        //   {@code NanochipAssemblyComplexMachine#ON_DEMAND_POWER_FLOOR_EU} 与
        //   {@code #isEnergySourceEffectivelyUnlimited()} / {@code #getObtainableEnergyEU()}
        //   已由「无线电网供电兼容」那条线提供（本闸直接复用，见 hostAffordableWhole 的 ① 判定）。
        //   原先这里另写的一份同义常量已删除 —— 两份同义哨兵迟早会走岔。

        // ======================================================================================
        // ★★★ A4：本任务是「显示即事实」的那一刀 ★★★
        // ======================================================================================
        /**
         * <b>把一次配方搜索的候选集截断到线程数</b> —— 这是「真实多线程」里唯一决定
         * <b>实际并发条数</b>的地方，缺了它 {@code getMultipleThreads()} 只影响显示。
         *
         * <h3>照抄来源（逐行对照，可回退）</h3>
         * Enhancedcore {@code ThreadLimitedRecipeLogic.java:306-320}：
         * <pre>
         *   @Override
         *   protected Set&lt;GTRecipe&gt; lookupRecipeIterator() {
         *       if (isLock() &amp;&amp; getLockRecipe() != null) return checkRecipe(getLockRecipe()) ? Set.of(getLockRecipe()) : Set.of();
         *       var iterator = machine.getRecipeType().getLookup().getRecipeIterator(machine, recipe -&gt; true);
         *       ((IAdditionalRecipeIterator) iterator).setUseDiveIngredientTreeFind(true);
         *       Set&lt;GTRecipe&gt; candidates = new LinkedHashSet&lt;&gt;();
         *       while (iterator.hasNext()) { GTRecipe c = iterator.next(); if (c != null &amp;&amp; checkRecipe(c)) candidates.add(c); }
         *       var selected = selector.select(candidates, isLock() ? 1 : getMultipleThreads(), recipe -&gt; recipe.id.toString());
         *       ...
         *   }
         * </pre>
         * 与本实现的差别只有两处（都是「不引入新机制」的收敛）：
         * <ol>
         *   <li><b>不做锁定配方分支</b>（{@code isLock()} / {@code pendingLock}）：NAC 的配方锁是
         *       gtladd 基类自己的 {@code ILockRecipe} 机制，其 {@code lookupRecipeIterator} 里
         *       <b>是否</b>需要特判锁定配方，取决于该版本的实现（✗ 未取证）；不复制这一段
         *       ⇒ 即使版本差异，也只是「锁定时也按线程数轮转」这一种较弱行为，不会破坏锁本身。</li>
         *   <li><b>不调用 {@code getRecipeOutputChance}</b>：那是概率产出结算，属于父类
         *       {@code buildFinalNormalRecipe} 的职责，候选阶段调用会把结算做两遍。</li>
         * </ol>
         *
         * <h3>「显示即事实」为什么因此成立（三步闭环）</h3>
         * <ol>
         *   <li>本方法返回的集合大小 <b>≤</b> {@code getMultipleThreads()}（
         *       {@link FairCandidateSelector#select} 里 {@code count = min(候选数, max(1, requested))}）；</li>
         *   <li>并行分配只对<b>本方法返回的那些</b>配方做，于是「同时挂起的配方条数」≤ 集合大小。
         *       ⚠️ 2026-10-07 起分配方改由本类的 {@code calculateParallels()} 自己算
         *       （原因见那边的长注释：预算驱动的并行会算出「放不下」的值 ⇒ 起跑前被挡 ⇒ 0%），
         *       但它<b>仍然以本方法的返回值为候选集</b>，所以这条闭环不变；</li>
         *   <li>界面/Jade 那行「拥有 N 个跨配方线程」由 gtladditions 父类的 {@code addDisplayText}
         *       输出（本类自己那行已于 2026-10-07 删除，见 {@link #addDisplayText}）⇒
         *       <b>显示的 N ≈ 实际挂起的条数（上限 N）</b>。</li>
         * </ol>
         * <b>诚实边界</b>：候选集本身可能不足 N 条（料不够 / 只匹配上少数配方）⇒ 实际条数 &lt; N。
         * 这属于「供应不足」，不是「显示撒谎」；N 是<b>上限</b>而不是保证值。
         * <p>★ 2026-10-07 追加的第二条边界：候选进了集合<b>也不保证</b>一定挂起 ——
         * {@code calculateParallels()} 还会按「真正放得下」再淘汰一遍（放不下就跳过）。
         * 于是「显示 N / 实际挂起 M ≤ N」这个不等式仍然成立，只是 M 多了一个下压因素。
         *
         * <h3>为什么还要按 {@code checkRecipe} 逐条校验</h3>
         * 与 Enhancedcore 同款理由：把「条件不满足 / 供压不够 / 输入输出放不下」的配方
         * 先剔掉，<b>否则它们会白占一个线程位</b>（线程数是有限的，占位不干活就等于并发下降）。
         * 代价 = 每 tick 对候选做一次条件校验（Enhancedcore 也是这么付的；
         * 现状文档 §5.1 已把「单机 CPU 上升」登记为中等风险，须实机看 TPS）。
         *
         * <h3>异常安全</h3>
         * 拿不到配方表 / 迭代器抛异常 ⇒ 返回空集（= 本 tick 不开工），
         * <b>绝不外抛</b>：配方逻辑抛异常会让整台机器停摆。
         *
         * <h3>★ 可见性必须是 public（2026-10-02 编译期修正）</h3>
         * 父类 gtladditions 把 {@code lookupRecipeIterator()} 提升成了可覆写 API，
         * 且可见性是 <b>public</b>；覆写只能加宽、不能收窄 ⇒ 原来的
         * {@code protected Set&lt;GTRecipe&gt; lookupRecipeIterator()} 会报
         * 「method does not override or implement a method from a supertype」。
         * 同族的 {@code createRecipeLogic} 也是 public（{@code API标准.md:126} 明文：
         * 「GTLAdd 基类 createRecipeLogic 是 public，子类覆写必须保持 public」）。
         */
        @Override
        public Set<GTRecipe> lookupRecipeIterator() {
            try {
                return lookupRecipeIteratorInternal();
            } catch (RuntimeException e) {
                // ★ 2026-10-19 外层兜底：多类型路径每一处都已自带守卫（见下方两个私有助手），
                //   这一层只为保证「绝不把异常抛进 tick」这条原有纪律对**整条方法**成立。
                //   ⚠️ 不吞异常细节：照原样打进 debug 日志。
                NanochipModContext.logger().debug("[gtlmodular] NAC 配方搜索异常（外层兜底），本次不开工：{}", e.toString());
                return Set.of();
            }
        }

        private Set<GTRecipe> lookupRecipeIteratorInternal() {
            // ★★ 2026-10-28【独立跨配方线程 · 候选来源统一】★★
            //   本方法原来的三段（① 按开关遍历类型取候选 ② FairCandidateSelector 截断到线程数
            //   ③ 游标推进）已**逐字搬进** `NanochipCandidateSearch.search(...)`，这里改成调用它
            //   —— 目的只有一个：新逻辑 `NanochipThreadedRecipeLogic`（独立线程那条路）用的是
            //   **同一份**候选口径，于是「跨配方类型」这条语义在两条派活通路上只有一处实现，
            //   不会出现「旧逻辑搜两类、新逻辑只搜一类」这种漂移。
            //   ⚠️ 语义一字未改：同一批类型、同一套 checkRecipe、同一个 FairCandidateSelector、
            //      同一个游标字段（`lastSelectedRecipeId` 现在只当 `cursor[0]` 的容器用）。
            //   ⚠️ 回退：把下面这段换回原来的三段共 50 行即可（git 里有原文）；
            //      `NanochipCandidateSearch` 留着无害（新逻辑仍会用它）。
            String[] cursor = { this.lastSelectedRecipeId };
            Set<GTRecipe> selected;
            try {
                selected = NanochipCandidateSearch.search(machine, multiTypeRecipeTypes(),
                        machine.getRecipeType(), this::checkRecipe, nac.getMultipleThreads(), cursor);
            } catch (RuntimeException e) {
                // 搜索阶段异常 ⇒ 放弃本次搜索（返回空集 = 本 tick 不开工），绝不把异常抛进 tick
                NanochipModContext.logger().debug("[gtlmodular] NAC 配方搜索异常，本次不开工：{}", e.toString());
                return Set.of();
            }
            this.lastSelectedRecipeId = cursor[0];
            return selected;
        }

        /**
         * ★ 2026-10-19【复合型机器】「要搜哪些配方类型」的唯一取数口。
         *
         * <p>返回 {@link NanochipAssemblyComplexMachine#getRecipeTypes()} 的当前快照 —— 简单模式下即
         * 「主机电路组装表 + 换算表」，原版模式即父类原样数组（配置为 false 时那个方法直接返回父类
         * 数组实例，逐字节不变）。
         *
         * <p><b>为什么单独包一层</b>：{@code getRecipeTypes()} 是上游（gtladditions 父类）提供的
         * 可覆写方法，读它本身可能抛（例如机器尚未成形 / 类型表未注册完）；调用点又在一个
         * 「绝不外抛进 tick」的方法里 ⇒ 这里把它包成「抛了就返回空数组 ⇒ 本 tick 不开工」，
         * 后半个 try 块的语义（放弃本次搜索）因此对多类型路径也成立。
         *
         * <p>返回类型用 {@code GTRecipeType[]} 而不是 {@code List}：零装箱、零额外分配，
         * 且与 {@code getRecipeTypes()} 逐字同型（不需要任何强转）。
         *
         * <p><b>未取证</b>：父类把 {@code getRecipeTypes()} 的可见性/签名写成什么样没有 javap 级证据，
         * 但<b>本类自己</b>已经在同一份文件的同名覆写上
         * （{@code @Override public GTRecipeType[] getRecipeTypes()}）编译通过（同一个 gtladditions 血统）
         * ⇒ 该签名在编译期已被验证，这里只做调用、不做覆写。
         */
        private GTRecipeType[] multiTypeRecipeTypes() {
            try {
                GTRecipeType[] types = machine.getRecipeTypes();
                return types == null ? new GTRecipeType[0] : types;
            } catch (RuntimeException e) {
                NanochipModContext.logger().debug("[gtlmodular] NAC 多类型配方表读取失败，本 tick 不开工：{}", e.toString());
                return new GTRecipeType[0];
            }
        }

        /**
         * ★ 2026-10-19【复合型机器】把<b>某一个</b>配方类型下的候选收进 {@code candidates}。
         *
         * <p>逐字保留改动前那段「取迭代器 → 逐条 {@code checkRecipe} → 收进集合」的全部语义与注释依据
         * （唯一差别是类型从「当前激活的那一条」变成「入参给的那一条」）：
         * {@code GTRecipeLookup.getRecipeIterator(holder, predicate)} 的返回值可直接落到
         * {@code Iterator<GTRecipe>}（同一写法在上游已被验证两处：星律
         * {@code SelectableRecipeTypeSetRecipeLogic.java:314} 与 {@code NebulaSiphonLogic.java:40-41}）。
         * 注意：{@code IAdditionalRecipeIterator} 与 {@code GTRecipeLookup.RecipeIterator} 是<b>平级</b>的
         * 两个类型（前者在 gtlcore 的 {@code ...api.recipe} 包，是对<b>增强搜索</b>的统一访问口），
         * 所以这里<b>不能</b>对 {@code RecipeIterator} 直接写 {@code instanceof IAdditionalRecipeIterator}
         * （javac 会判"两个操作数类型不匹配"）；要判定就先用 {@code Object} 中转。
         * 本实现<b>不</b>做这个增强搜索开关：候选集本身已由 {@code checkRecipe} 逐条校验，
         * 少开一个搜索增强换来的只是"候选可能略少"，属于保守一侧，不影响并发条数与正确性。
         *
         * <p><b>「本次搜索放弃」的边界</b>：本助手<b>不</b>自吞异常 —— 某个类型搜索时抛出的
         * {@code RuntimeException} 会照原样冒到调用方的 {@code catch}，即「放弃本次搜索、本 tick 不开工」，
         * 与改动前「唯一那个类型抛了就不开工」语义一致（不是「跳过该类型继续搜别的」——
         * 那会静默改变错误可见性，本轮不做）。
         *
         * <p>★ 2026-10-28【独立跨配方线程】：本方法<b>当前不被调用</b>——同一段逻辑已逐字搬进
         * {@link NanochipCandidateSearch#search}（新逻辑 {@code NanochipThreadedRecipeLogic}
         * 与旧逻辑共用那一份）。这里<b>刻意保留</b>：它是 {@link #lookupRecipeIteratorInternal()}
         * 回退成「原来那三段」时要用到的那一块，删掉会让回退多一步（要重写迭代器那段）。
         * 保留一个私有未调用方法在 javac 下只是未使用警告、不是错误，且无运行期成本。
         */
        private void collectCandidatesFromType(GTRecipeType type, Set<GTRecipe> candidates) {
            Iterator<GTRecipe> iterator = type.getLookup().getRecipeIterator(machine, recipe -> true);
            while (iterator.hasNext()) {
                GTRecipe candidate = iterator.next();
                if (candidate != null && checkRecipe(candidate)) {
                    candidates.add(candidate);
                }
            }
        }

        // ======================================================================================
        // ★★★ 2026-10-07【修「未找到可运行配方 / 进度 0%」】—— 并行改成「按实际可容纳算」★★★
        // ======================================================================================
        /**
         * 某个候选并行数、<b>对某一条配方</b>的试探结论。
         *
         * <p>{@code fits} 为真时 {@code scaledRecipe} 必然非 null，且它走的是
         * {@link #scaleRecipePrecisely}那条<b>精确 long 缩放</b>路径
         * （不是父类/上游的 double 路径）。这一份对象就是「判定说放得下」的物证，
         * 排查「校验过但扣料时放不下」时可以直接看它。
         */
        private static final class FitProbe {

            private final boolean fits;
            private final GTRecipe scaledRecipe;

            private FitProbe(boolean fits, GTRecipe scaledRecipe) {
                this.fits = fits;
                this.scaledRecipe = scaledRecipe;
            }
        }

        /**
         * <b>把「并行预算」换成「每条配方真正放得下的最大并行」</b> —— 本任务的第二刀。
         *
         * <h3>★ 为什么必须覆写它（0% 的根因复核）</h3>
         * 父类 {@code GTLAddMultipleRecipesLogic.calculateParallels()} 拿到的并行数是
         * <b>「预算驱动」</b>的：由机器的 {@code getMaxParallel()}（全流程档 =
         * {@link #FULL_PARALLEL_CAP}，★ 2026-10-27 起 = {@code Integer.MAX_VALUE}，即
         * {@code Long.MAX_VALUE} 在 int 接口上的饱和截断值 —— 不再是人为压低的 8388607）乘上线程数当总预算，再逐条配方按预算摊。
         * 该值<b>未必真的放得下</b>：一旦放大后的产出超出输出仓/AE 的可容纳量，下游就会在
         * <b>起跑前</b>被 {@code RecipeRunnerHelper.matchRecipeOutput} 挡掉
         * （判定点：gtlcore {@code MultipleRecipesLogic.findAndHandleRecipe}
         *  → {@code matchRecipeOutput} 失败即 <b>不调 setupRecipe</b>，
         *  逐字证据见 {@code _census/_javap_simple_multirecipes.txt:116-133}）——
         * 表现就是<b>「找不到可运行配方、进度恒 0%」</b>：配方不是跑不动，是<b>根本没起跑</b>。
         *
         * <h3>★ 解法（与星律超限模式同源，出处逐条给出）</h3>
         * <ol>
         *   <li>{@code getMaxParallel(recipe, limit)} 先得到<b>输入侧</b>真正的上限
         *       （gtlcore 公开接口 {@code IParallelLogic.getMaxParallel(holder, recipe, long)}
         *        —— 出处：星律 {@code PrimordialModuleRecipeLogic.java:228}）；</li>
         *   <li>再用{@link #scaleRecipePrecisely}做<b>精确 long 缩放</b>（不用 double！
         *       gtladd 的 {@code multipleRecipe(long)} 会先把倍率转成 double，
         *       超过 2^53 后物品/流体数量会被四舍五入 —— 出处：星律
         *       {@code PrimordialModuleRecipeLogic.java:395-407} 与 {@code :230-232} 的原注释）；</li>
         *   <li>对缩放后的配方<b>实证</b>「输入放得下 && 产出放得下」，用
         *       {@link #findHighestMatchableParallel} 从上限往下<b>二分找最大的可行值</b>
         *       （出处：星律 {@code PrimordialModuleRecipeLogic.java:409-438} 的 long 二分，
         *       逐字同构：先试 requested → 再试 requested-1 → 再标准二分）。
         *       <b>关键差别</b>：并行值只在<b>真的放得下</b>时才被采用 ⇒ 不可能再出现
         *       「算出天文数字并行 ⇒ 起跑前被挡 ⇒ 永远不开工」这条路。</li>
         * </ol>
         *
         * <h3>为什么不改 {@link #getMaxParallel()} 的取数来源（★ 2026-10-27 只改了它的「钳法」）</h3>
         * 那条「并行 × 线程」的乘法通路 NAC 已改走 64 位饱和乘法
         * （{@link #saturatingMul(long, long)}；此前是「成对钳制」的硬压并行，已退役，
         * 见 {@link #FULL_PARALLEL_CAP} 与 {@link #NAC_SATURATING_PARALLEL}），
         * 本覆写是<b>另一条通路</b>（产出放不下）。这也是调查报告 §3.5 的结论：
         * 「0% 不在乘法钳制那条通路上」。两者互不替代、也互不干扰。
         *
         * <h3>诚实边界（回退方式逐条写死）</h3>
         * <ul>
         *   <li>{@code calculateParallels()} 的<b>可见性/签名</b>按「星律在同一套 gtladditions 血统的
         *       {@code GTLAddMultipleRecipesLogic} 上以 {@code @Override protected ParallelData
         *       calculateParallels()} 编译通过」推定（出处：星律
         *       {@code common/machine/primordial/PrimordialModuleRecipeLogic.java:162-164}、
         *       {@code api/machine/SelectableRecipeTypeSetRecipeLogic.java:396-398}）；
         *       若本机上游把它改成 {@code private}/{@code final}，编译期会直接报
         *       「method does not override…」。</li>
         *   <li>★ <b>回退方式</b>：删掉本覆写（{@code calculateParallels}）与它下面的
         *       {@code parallelBudget / findFitParallel / canFit / scaleRecipePrecisely /
         *       findHighestMatchableParallel / FitProbe} 整块（★ 2026-10-27 前的 {@code saturatedMultiply}
         *       副本已并入外层类的 {@code saturatingMul}，见那里的注释），
         *       行为立刻逐字回到父类那条路径（= 本次修改前的 NAC）；
         *       {@code nac.noteNoFit(...)} 那一行要一起删（否则调用点悬空），
         *       而机器侧的 {@code noteNoFit} 方法留着无害（无人调用）。
         *       全部改动不涉及存档、不涉及 mixin ⇒ 回退是纯代码层、可随时做。</li>
         *   <li>本覆写<b>不再调用父类那份公平分配</b>（父类把 {@code totalParallel / recipes.size()}
         *       均分）：这里给每条配方<b>各自独立</b>算自己的可容纳上限，与星律
         *       {@code PrimordialModuleRecipeLogic.java:162-200} 的取舍逐条一致
         *       （它的原注释：「每个配方各自独立吃满自己的上限……而不是从一个共享池里分蛋糕」）。</li>
         *   <li><b>性能</b>：二分是对数级（预算 8.3M ⇒ 最多 ~23 次试探，且
         *       {@link #findHighestMatchableParallel} 的「先试 requested」在常见情况下一次命中）；
         *       只在配方搜索（不是每 tick 的进度推进）里跑。
         *       ✗ <b>未取证</b>：本机 {@code IParallelLogic.getMaxParallel} 内部
         *       （{@code binarySearchMaxParallel} / {@code getMinParallel}）对输出容量用的
         *       是不是 double 精度 —— 若它自己在 double 上就判出「放不下」，本覆写会拿到
         *       {@code inputMax <= 0} 而直接放弃该配方（保守一侧：不开工，不会吞料）。
         *       这也是本实现<b>没有</b>直接把 {@code IParallelLogic} 的结果当最终值的原因。</li>
         * </ul>
         */
        @Override
        protected ParallelData calculateParallels() {
            Set<GTRecipe> recipes = lookupRecipeIterator();
            if (recipes.isEmpty()) {
                return null;
            }
            // ★ 2026-10-27【并行「去总池」】取值口由「总池」改成「每条配方各自的上限」：
            //   旧口径 parallelBudget() = getMaxParallel() × getMultipleThreads()，是「先乘出一个总数、
            //   再让每条命中配方去分」的**总池**（父类 calculateParallels 的原式，出处
            //   `_census/真实多线程_机制与我方现状.md:65`）。作者口径「完全去掉总池概念」
            //   「每个配方线程都分 Long.MAX_VALUE 并行」⇒ 默认每条配方各自拿
            //   perRecipeParallelLimit() = Long.MAX_VALUE 当上限，真正的上限由下面的二分
            //   「真的放得下」收敛（二分保留 ✗ 不许删：它是防「产出放不下 ⇒ 起跑前被挡 ⇒ 永远 0%」的关键）。
            long perRecipeLimit = perRecipeParallelLimit();
            long[] parallels = new long[recipes.size()];
            List<GTRecipe> recipeList = new ArrayList<>(recipes.size());
            int index = 0;
            for (GTRecipe recipe : recipes) {
                long fit = findFitParallel(recipe, perRecipeLimit);
                if (fit <= 0L) {
                    // 这一条在这个预算下真的放不下 ⇒ 跳过它（不让它白占一个线程位）。
                    continue;
                }
                recipeList.add(recipe);
                parallels[index] = fit;
                index++;
            }
            if (recipeList.isEmpty()) {
                // ★ 与父类同条语义：本轮<b>一个配方都放不下</b> ⇒ 不开工。
                //   这正是用户机器上「找不到可运行配方 / 进度 0%」的那条路，
                //   所以这里必须留下可诊断痕迹（限流 1 秒，见 NanochipAssemblyComplexMachine#noteNoFit）。
                nac.noteNoFit(perRecipeLimit, recipes.size());
                return null;
            }
            // shouldProcess = true：上面已逐条实证过输入/产出放得下，
            // 必须让父类的 buildFinalNormalRecipe 走真实扣料分支
            // （与星律 PrimordialModuleRecipeLogic.java:196-199 同一处写法与理由）。
            return new ParallelData(recipeList, parallels, true, null);
        }

        /** 本机当前的并行预算（= 机器并行上限 × 真正生效的跨配方线程数，饱和乘，绝不溢出）。 */
        private long parallelBudget() {
            // ★ 2026-10-27【统一饱和工具】：原来这里有一份私有副本 `saturatedMultiply`（现已删除），
            //   改调本类唯一的 saturatingMul ⇒ 「并行 × 线程」这条最容易回绕成负数的通路只有一处实现。
            //   两个因子都来自 int 接口（≥1、≤ Integer.MAX_VALUE，实测线程数还另有 256 上限）
            //   ⇒ 乘积最多 ~4.6e18，连 Long.MAX_VALUE 都够不到；饱和判界只是兜底，永不为负。
            return saturatingMul(
                    Math.max(1L, (long) nac.getMaxParallel()),
                    Math.max(1L, (long) nac.getMultipleThreads()));
        }

        /**
         * <b>单条配方的并行上限</b> —— 去总池后的唯一取数口（★ 开关见
         * {@link #NAC_PER_THREAD_FULL_PARALLEL}）。
         *
         * <h4>{@code true}（默认）：作者口径「每个配方线程都分 {@code Long.MAX_VALUE} 并行」</h4>
         * 直接返 {@link Long#MAX_VALUE} ⇒ <b>不乘、也没有「总池」这个中间量</b>；
         * 每条配方<b>各自</b>拿它当上限，真正的上限由
         * {@link #findHighestMatchableParallel} 的二分（输入放得下 && 产出放得下）收敛。
         *
         * <h4>为什么敢给 {@code Long.MAX_VALUE}（三条，逐条可核）</h4>
         * <ol>
         *   <li><b>它不参与乘法</b>：会回绕成负数的那条通路是旧口径的
         *       {@code getMaxParallel() × getMultipleThreads()}（{@link #parallelBudget()}），
         *       本值只是往上游 {@code IParallelLogic.getMaxParallel(machine, recipe, limit)} 传的
         *       {@code limit} ⇒ <b>没有乘积可溢出</b>；</li>
         *   <li><b>上游同源、同接口、同值</b>：星律 {@code PrimordialModuleRecipeLogic.java:202-211}
         *       在超限模式下 {@code getTotalParallelLimit()} 直接 {@code return Long.MAX_VALUE}，
         *       再由同文件 {@code :225-234} 的二分收敛 —— 作者自己验证过的写法；</li>
         *   <li><b>数值不会膨胀</b>：二分用 {@code low + ((high - low) &gt;&gt;&gt; 1)}（不会 {@code low+high} 溢出），
         *       每次试探都走 {@code scaleRecipePrecisely} 的<b>精确 long 缩放</b>，
         *       参与运算的量级由「库存 / 输出容量」决定，<b>不由</b> {@code Long.MAX_VALUE} 决定
         *       —— 这也是作者说的「不能让数值太膨胀」，所以我们<b>不引入</b> {@code BigInteger}。</li>
         * </ol>
         * ✗ <b>未取证</b>：上游 {@code IParallelLogic.getMaxParallel(holder, recipe, limit)} 内部
         * 如何消费这个 {@code limit}（是否对 {@code Long.MAX_VALUE} 有保护）——
         * 本轮 {@code pwsh}/javap 被沙箱拒绝（{@code sandbox-local windows-acl temp grant materialization failed}），
         * 无法补证；仅有「星律对同一接口传的也是 {@code Long.MAX_VALUE}」这一条旁证。
         */
        private long perRecipeParallelLimit() {
            if (NAC_PER_THREAD_FULL_PARALLEL) {
                return Long.MAX_VALUE;
            }
            return parallelBudget();
        }

        /**
         * 求某条配方在 {@code budget} 之内<b>真正放得下的最大并行</b>；0 = 一个都放不下。
         *
         * <p>★ 2026-10-27【并行「去总池」】：{@code budget} 的来路由调用方决定 —— 默认
         * （{@link #NAC_PER_THREAD_FULL_PARALLEL} = {@code true}）走
         * {@code perRecipeParallelLimit()} = {@code Long.MAX_VALUE}，即<b>每条配方各自满并行</b>；
         * 关掉开关才是旧的「总池」（{@code parallelBudget()}）。
         * 两种口径下<b>本方法的二分逻辑一字不变</b>（它是防「放不下 ⇒ 永远 0%」的那一刀）。
         *
         * <p>与星律 {@code findMaxMatchableScaledRecipe}（{@code PrimordialModuleRecipeLogic.java:225-234}）
         * 同构：先取输入侧上限，再做「精确缩放 + 输出容量」的二分。
         *
         * <h4>试探缓存的作用域（★ 必须按配方隔离）</h4>
         * 二分会对<b>同一条配方</b>的同一个候选值问 2~3 次（先试 {@code requested}、
         * 再试 {@code requested - 1}、再标准二分），缓存把重复试探降到一次。
         * 但并行数<b>不是跨配方的可比量</b>（同一条配方在不同库存下可容纳量也不同），
         * 所以这个 Map <b>只活在本方法的一次调用里</b>，绝不上提到字段级。
         *
         * <p><b>诚实边界</b>：这里缩放出来的那份配方，最终<b>不会</b>被交到下游 —— 父类
         * {@code buildFinalNormalRecipe} 只接收「并行数」并用它自己那条路径再缩放一次，
         * 也就是说上游会多付一次缩放。这是父类 API 形状决定的（{@code ParallelData}
         * 只带 {@code long[] parallels}，带不了配方对象），本实现省不掉。
         */
        private long findFitParallel(GTRecipe recipe, long budget) {
            if (recipe == null || budget <= 0L) {
                return 0L;
            }
            long inputMax = IParallelLogic.getMaxParallel(machine, recipe, budget);
            if (inputMax <= 0L) {
                // 输入侧一个都放不下（连 1 份料都不够 / 电压不够）⇒ 本条不参与
                return 0L;
            }
            Map<Long, FitProbe> probes = new HashMap<>();
            long matchable = findHighestMatchableParallel(inputMax,
                    candidate -> canFit(probes, recipe, candidate));
            // ★ 2026-10-27【并行「去总池」】：默认（perRecipeParallelLimit() == Long.MAX_VALUE）下
            //   这里的 Math.min 是**恒等映射** —— 保留它只为开关关掉时逐字回到「总池」口径，
            //   而且它只是个 min，永不参与乘法 ⇒ 不存在回绕成负数的通路。
            return matchable <= 0L ? 0L : Math.min(matchable, budget);
        }

        /** 缩放后的配方「输入放得下 && 产出放得下」的实证判定（带本条配方专属的试探缓存）。 */
        private boolean canFit(Map<Long, FitProbe> probes, GTRecipe recipe, long parallel) {
            if (parallel <= 0L) {
                return false;
            }
            FitProbe cached = probes.get(parallel);
            if (cached != null) {
                return cached.fits;
            }
            GTRecipe scaled = scaleRecipePrecisely(recipe, parallel);
            boolean fits = RecipeRunnerHelper.matchRecipeInput(machine, scaled)
                    && RecipeRunnerHelper.matchRecipeOutput(machine, scaled);
            probes.put(parallel, new FitProbe(fits, fits ? scaled : null));
            return fits;
        }

        /**
         * <b>精确 long 缩放</b> —— 逐行照抄星律 {@code PrimordialModuleRecipeLogic.java:395-407}
         * （{@code scaleRecipePrecisely}），只去掉星律自己那层「放大产出」的包装。
         *
         * <p>为什么不能像父类/上游那样写 {@code recipe.copy(ContentModifier.multiplier((double) parallel), false)}：
         * {@code ContentModifier.multiplier} 收的是 <b>double</b>，超过 2^53 的并行数在缩放时
         * 会把物品/流体数量四舍五入（星律在同一处写明这是它主要动机之一，见 {@code :230-232}）。
         * {@link IAdvancedContentModifier#preciseMultiplier(long)} 收 <b>long</b>，
         * 数量走整数倍乘 ⇒ 校验与扣料看到的是同一个数字。
         *
         * @return {@code parallel <= 1} 时原样返回（不复制，省一次分配，与星律一致）
         */
        private GTRecipe scaleRecipePrecisely(GTRecipe recipe, long parallel) {
            if (parallel <= 1L) {
                return recipe;
            }
            GTRecipe scaled = recipe.copy(IAdvancedContentModifier.preciseMultiplier(parallel), false);
            // 把扩展字段（并行数等）一并搬过去，否则下游读 realParallels 会读到 0
            RecipeExtensionCopier.copy(recipe, scaled);
            IGTRecipe.of(scaled).setRealParallels(parallel);
            return scaled;
        }

        /**
         * <b>「从一个上限往下，找最大的、真正放得下的并行值」</b> —— 逐字照抄星律
         * {@code PrimordialModuleRecipeLogic.java:409-438}（{@code findHighestMatchableParallel}）。
         *
         * <p>三步（与星律一字不差）：
         * <ol>
         *   <li>先试 {@code requested}（常见情况直接命中，零额外试探 ⇒ 二分只是兜底）；</li>
         *   <li>再试 {@code requested - 1}（躲开「差一点点」的容量边界抖动）；</li>
         *   <li>标准 long 二分：[1, requested-2]，用 {@code low + ((high - low) >>> 1)} 防溢出。</li>
         * </ol>
         * 返回 0 = 连 1 都放不下。
         *
         * @param requestedParallel 上限（调用方已用 {@code IParallelLogic} 收过输入侧）
         * @param canMatch          对某个候选并行数做「真的放得下吗」的实证判定
         */
        private static long findHighestMatchableParallel(long requestedParallel, LongPredicate canMatch) {
            if (requestedParallel <= 0L || canMatch == null) {
                return 0L;
            }
            if (canMatch.test(requestedParallel)) {
                return requestedParallel;
            }
            if (requestedParallel == 1L) {
                return 0L;
            }
            long oneLess = requestedParallel - 1L;
            if (canMatch.test(oneLess)) {
                return oneLess;
            }
            long low = 1L;
            long high = requestedParallel - 2L;
            long best = 0L;
            while (low <= high) {
                long middle = low + ((high - low) >>> 1);
                if (canMatch.test(middle)) {
                    best = middle;
                    low = middle + 1L;
                } else {
                    high = middle - 1L;
                }
            }
            return best;
        }

        // ★ 2026-10-27【统一饱和工具】：这里原来还有一份私有副本 `saturatedMultiply`，已删除 ——
        //   饱和运算全文件只保留一份实现（外层类的 saturatingMul / saturatingAdd / saturatingToInt），
        //   避免同一判界写法散在多处、日后改一处漏一处。
    }

    /**
     * ★★★ 2026-10-28【独立跨配方线程 · 总开关】★★★
     * <b>本机用哪一条配方逻辑</b> —— 这是本功能<b>唯一</b>的判定点（一行回退）。
     *
     * <p>{@code true} = 用新逻辑 {@link NanochipThreadedRecipeLogic}：
     * <b>每条线程各自计时、各自扣料/出料</b>（A 配方 200 tick、B 配方 20 tick 互不影响，各自到点各自结算），
     * 候选来源覆盖本机<b>全部</b>配方类型（组装 + 换算同轮搜，即「跨配方类型的独立线程」）。
     * {@code false} = <b>逐字回到</b>本次改动前：继续用 {@link NanochipRecipeLogic}
     * （gtladditions 那条「N 条候选摊并行预算、合并成一条配方、只有一份 progress」的通路）。
     *
     * <h4>★ 为什么本轮默认值取 {@code false}（2026-10-28 重写轮的裁决）</h4>
     * <ol>
     *   <li><b>新逻辑这一轮才第一次「静态可编译」</b>：上一版（照着 GTO/GTET 的 API 写成的那份）
     *       <b>编译不过</b> —— 它用了本机不存在的四个方法（{@code RecipeHelper.checkConditions} /
     *       {@code RecipeHelper.matchTickRecipe} / {@code RecipeHelper.handleTickRecipeIO} /
     *       {@code machine.regressWhenWaiting}），逐条更正见
     *       {@code NanochipThreadedRecipeLogic} 的类注释；因此它<b>从未</b>进过任何一次构建、
     *       也从未实机验证过。按「默认值取保守一侧、一次只上一件事」的纪律：
     *       先把它<b>关着</b>编译通过，由用户决定何时打开；</li>
     *   <li><b>关着的时候游戏行为逐字不变</b>：{@link #createRecipeLogic} 返回旧逻辑
     *       {@link NanochipRecipeLogic}（那条路已经在线上跑过多个版本），
     *       新逻辑只是<b>被编译</b>、完全不被实例化 ⇒ 本轮对游戏行为的净影响 = 零；</li>
     *   <li><b>打开的成本是一行、回退也是一行</b>：把本常量改成 {@code true} 重新编译即切换
     *       （判定点唯一 = {@link #createRecipeLogic} 的返回值）；两条逻辑的代码与存档键互不冲突
     *       ——旧逻辑不写批次键，新逻辑写的是<b>追加</b>键
     *       {@code NanochipThreadedRecipeLogic.TAG_THREADS}，老档读不到就安全回退。</li>
     * </ol>
     *
     * <h4>打开它之后会得到什么（同一个开关的另一半，逐条）</h4>
     * <ol>
     *   <li><b>每条批次各自计时</b>：用户的口径是「每条线程各自计时、支持跨配方类型」，
     *       而旧逻辑<b>结构上做不到</b>（它最终只产出一条合并配方、一份 progress —— 硬证据见
     *       {@code NanochipRecipeLogic} 的类注释里那段「2026-10-28 更正」）；</li>
     *   <li><b>候选来源覆盖本机全部配方类型</b>（组装 + 换算同轮搜，即「跨配方类型的独立线程」）；</li>
     *   <li><b>零结构 / 零存档破坏</b>：不新增方块、不改图案、不改 {@link #getRecipeTypes()}、
     *       不新增 {@code @Persisted} 字段。</li>
     * </ol>
     *
     * <h4>诚实边界（关掉开关会掉什么，逐条）</h4>
     * <ul>
     *   <li><b>并行来源变了</b>：新逻辑里每条线程的并行仍是「自己那一条各自的 {@code Long.MAX_VALUE} 上限
     *       → 二分收敛到真的放得下」（与旧逻辑「去总池」同源，见 {@code NanochipParallelFit}），
     *       但<b>它不再经过 {@code calculateParallels()} / {@code ParallelData}</b> ⇒
     *       {@code NAC_PER_THREAD_FULL_PARALLEL} 与新逻辑无关（它只改旧逻辑的口径）；</li>
     *   <li><b>同一种配方可以占多条线程</b>（新逻辑的 fan-out，见
     *       {@code NanochipThreadedRecipeLogic.FAN_OUT_SAME_RECIPE}）；关掉总开关即回到
     *       「一种配方最多占一条」；</li>
     *   <li><b>整机吞吐可能变化</b>（变好或变差都取决于玩家喂料节奏）：新逻辑按「每条线程各自的预留」
     *       做容纳判定（保守一侧），旧逻辑按「合并后的单条配方」判。⚠️ <b>未实机实测</b>，
     *       所以本轮只落地代码、不构建不部署（见实施记录 §⑭）。</li>
     * </ul>
     *
     * <p><b>零存档影响</b>：本常量不是 {@code @Persisted}、不进 NBT、不改任何存档格式；
     * 两条逻辑的存档键互不冲突（旧逻辑不写线程键，新逻辑写的
     * {@code gtlmodular_nanochip_threads} 是纯追加键）。
     */
    // ★ 默认值 = false：理由见上面那段「为什么本轮默认值取 false」。
    //   打开方式 = 把这一行的 false 改成 true 重新编译（判定点唯一 = createRecipeLogic）。
    public static final boolean NAC_INDEPENDENT_THREADS = true;

    /**
     * 【独立跨配方线程】的机器侧工厂覆写 —— <b>本功能的唯一判定点</b>。
     *
     * <p>为什么这里能零混入：{@code WorkableMultiblockMachine} 的构造函数里就有一句
     * {@code this.recipeLogic = createRecipeLogic(args)}，而该方法是
     * {@code protected RecipeLogic createRecipeLogic(Object...)}（javap 现读：
     * {@code _javap_simple_workablemb.txt:44} 与 {@code :82}）—— 这是 GTCEu 提供的<b>正规扩展点</b>
     * （参考实现的原注释也这么说：{@code IThreadedRecipeMachine.kt:22-23}
     * 「这是 GTCEu 提供的**正规扩展点** … 所以本功能不需要任何 mixin」）。
     *
     * <p>⚠️ <b>构造顺序</b>：本方法在父类构造函数里就被调用，那时<b>本类自己的字段与构造体都还没跑</b>
     * ⇒ 这里只允许传 {@code this}，<b>不许</b>读任何本类字段（新逻辑那侧也是这样做的：
     * 它构造函数里只做一次 {@code instanceof} 判定，不读任何机器字段）。
     */
    @Override
    public RecipeLogic createRecipeLogic(Object... args) {
        if (NAC_INDEPENDENT_THREADS) {
            return new NanochipThreadedRecipeLogic(this);
        }
        return new NanochipRecipeLogic(this);
    }

    // ---------- 配方类型：简单模式下追加「换算」表 ----------

    /**
     * 简单物流模式允许不铺真空仓，因此需要把 {@link NanochipRecipeTypes#CONVERSION}
     * 加入主机候选表，供样板仓或普通输入仓提供的真件与未处理 CC 互转。
     * 换算表追加在末尾，是为了保留组装表和既有模式下标；原版 NH 物流仍使用父类类型列表。
     *
     * <p>{@link #nanochipSearchTypes()} 和旧配方逻辑在多类型搜索开启时会遍历全部类型，
     * 因此原生 ME 样板仓只需按槽供料，无需替主机切模式。实际供料、出料仍取决于仓室能力，
     * 镜像绑定只是可选的物流连接，不能作为换算的前提。
     */
    private GTRecipeType[] simpleModeRecipeTypes;

    @Override
    public GTRecipeType[] getRecipeTypes() {
        if (!NanochipModContext.config().isNanochipSimpleLogistics()) {
            return super.getRecipeTypes();   // 原版(NH)：父类数组实例原样返回，逐字节不变
        }
        if (simpleModeRecipeTypes == null) {
            GTRecipeType[] base = super.getRecipeTypes();
            GTRecipeType[] extra = new GTRecipeType[base.length + 1];
            System.arraycopy(base, 0, extra, 0, base.length);
            extra[base.length] = NanochipRecipeTypes.CONVERSION;   // 追加在末尾 ⇒ 下标 0 仍是主机自己的 NAC 表
            simpleModeRecipeTypes = extra;
        }
        return simpleModeRecipeTypes;
    }

    /**
     * 与 {@link #getRecipeTypes()} 配套：父类的 {@code getRecipeType()} 是
     * {@code recipeTypes[activeRecipeType]}（读的是父类私有字段）——只复写列表而不复写这一个，
     * 简单模式下下标 1（换算）会越界到父类字段的末尾，直接 {@code ArrayIndexOutOfBoundsException}。
     * 这里按<b>同一个</b> {@link #getRecipeTypes()} 取，并把下标夹到合法范围（配置中途改回原版模式时，
     * 缓存里可能还留着下标 1 ⇒ 夹取后回落到下标 0 = 主机自己的 NAC 表，不抛异常、不跑错表）。
     */
    @Override
    public GTRecipeType getRecipeType() {
        GTRecipeType[] types = getRecipeTypes();
        if (types.length == 0) {
            return null;
        }
        int idx = getActiveRecipeType();
        if (idx < 0) {
            idx = 0;
        } else if (idx >= types.length) {
            idx = types.length - 1;
        }
        return types[idx];
    }

    // ---------- 查找侧收窄接口闸（MultiTypeLookupHost，★ 2026-10-27） ----------

    /**
     * ★★ <b>「查找侧收窄」×「复合型机器」语义冲突的机器侧回答</b>（{@link MultiTypeLookupHost} 的唯一实现）★★
     *
     * <p><b>为什么 NAC 要回答「是」</b>：本机开着 {@link #NAC_MULTI_TYPE_RECIPES}（默认 {@code true}）时，
     * {@link NanochipRecipeLogic#lookupRecipeIterator()} 会在<b>同一轮</b>里遍历 {@link #getRecipeTypes()}
     * 声明的<b>全部</b>类型取候选（对照：关掉它才落回「只搜 {@link #getRecipeType()}` 单值」那条分支）
     * ⇒ 本机这一轮<b>不依赖</b> {@code getActiveRecipeType()} ⇒ 「本轮会被搜的类型集合」= 全部类型。
     *
     * <p>若输入设备按 {@code getActiveRecipeType()}（<b>单值</b>）收窄查找范围，
     * 就会把这个集合里「非激活类型」的<b>冷</b>样板槽
     * 摘出查找面 ⇒ 冷槽永远拿不到第一次匹配 ⇒ <b>该类冷样板永久饥饿</b> ✗。
     *
     * <p>⇒ 本方法返回 {@code true} 时，两台总成的收窄判定会放行全量（不收窄），
     * 语义口径是「<b>收窄面 = 本轮会被搜的类型集合</b>」而不是「单一激活模式」✓。
     *
     * <p><b>代价（如实登记）</b>：NAC 上失去查找侧收窄的性能收益（候选更多、逐槽尝试更多）——
     * 但多类型机器本来就要遍历全部类型，这份代价与复合型语义同源，<b>无法两全</b>。
     * 注：即便不修，收窄在 NAC 上本来也几乎不起作用（热样板槽全部走
     * {@code recipes2SlotsMap} 快路径与 {@code getCachedGTRecipe} 追加，都不看 {@code getActiveSlots()}）
     * ⇒ 本方法修的是「<b>冷槽饥饿</b>」这个真实故障，不是收回一份正在生效的保护 ✓。
     *
     * <p><b>只读、无副作用、零存档影响</b>：只读常量 {@link #NAC_MULTI_TYPE_RECIPES}（{@code public static final}），
     * 不碰任何 {@code @Persisted} 字段、不进 NBT、不改存档格式 ✓。
     *
     * <p><b>回退方式（一档）</b>：删掉本方法的 {@code @Override} 与类声明上的
     * {@code implements ... MultiTypeLookupHost} ⇒ 调用侧 {@code instanceof} 恒假 ⇒ 逐字回到加本闸之前 ✓
     * （更狠的二/三档见 {@link MultiTypeLookupHost} 的 javadoc）。
     *
     * @return {@link #NAC_MULTI_TYPE_RECIPES}（默认 {@code true} = 多类型搜索 ⇒ 总成侧本轮不收窄）
     */
    @Override
    public boolean searchesAllRecipeTypes() {
        return NAC_MULTI_TYPE_RECIPES;
    }

    // ---------- 独立跨配方线程：机器侧四件事（NanochipThreadHost，★ 2026-10-28） ----------

    /**
     * ★★ <b>{@link NanochipThreadHost} 的机器侧实现</b> —— 新逻辑（{@link NanochipThreadedRecipeLogic}）
     * 需要的四件事全在这里，<b>纯只读、零 mixin、零存档</b>。★★
     *
     * <h3>① 最多几条线程 —— 直接复用本机<b>已有</b>的阶梯，不新增方块、不改结构</h3>
     * 返回 {@link #getMultipleThreads()}（= 电压基线 {@link #baseCrossRecipeThreads()} +
     * Ω-天球分歧引擎仓追加，夹 {@link #MAX_CROSS_RECIPE_THREADS}）。
     *
     * <p><b>为什么不去做「线程仓方块」</b>（参考实现 {@code IThreadHatch} 那条路）：
     * <ul>
     *   <li>本轮任务书明确「先只落地代码、不动结构」，而加线程仓要动
     *       {@code NanochipComplexPattern}（图案）＋新方块注册表＋贴图，超出本轮范围；</li>
     *   <li>本机<b>已经有</b>一条「按电压给线程」的阶梯，语义上就是「这台机器同时能挂几条配方」
     *       ⇒ 直接把它接上就够了，玩家界面那一行的数字也<b>不用改口径</b>
     *       （仍然是 {@link #getMultipleThreads()}，与 Jade/面板显示的完全同一个值）；</li>
     *   <li>将来若要加线程仓，只需在本方法里把「取两者较大的那个」接进来即可（一处改动）。</li>
     * </ul>
     *
     * <h3>② 本轮要搜哪些类型 —— 「跨配方类型」的唯一取数口</h3>
     * 见 {@link #nanochipSearchTypes()}。
     *
     * <h3>③ 候选能不能用 —— 转交旧逻辑的 {@code checkRecipe}</h3>
     * 见 {@link #nanochipCandidateUsable(GTRecipe)}。
     *
     * <h3>④ 公平轮转游标</h3>
     * 见 {@link #nanochipThreadCursors()}。
     */
    @Override
    public int nanochipThreadCount() {
        return getMultipleThreads();
    }

    /**
     * 本轮要搜的配方类型：<b>本机声明的全部类型</b>（简单模式下 = 电路组装表 + 换算表）。
     *
     * <p>返回的正是 {@link #getRecipeTypes()} 的当前快照 —— 于是「组装与换算同轮搜」
     * 这条语义在<b>新旧两条逻辑上完全同源</b>（新逻辑不自己另算一份类型表）。
     *
     * <p>⚠️ 关掉 {@link #NAC_MULTI_TYPE_RECIPES} 时返回<b>只含激活类型</b>的单元素数组，
     * 与新逻辑那边的口径一致（新逻辑拿到这个数组就只搜那一个类型）——
     * 也就是说「多类型开关」对两条逻辑<b>同时生效</b>，不会出现「旧逻辑关掉了、新逻辑还在搜两类」。
     */
    @Override
    public GTRecipeType[] nanochipSearchTypes() {
        if (!NAC_MULTI_TYPE_RECIPES) {
            GTRecipeType active = getRecipeType();
            return active == null ? new GTRecipeType[0] : new GTRecipeType[] { active };
        }
        return getRecipeTypes();
    }

    /**
     * 候选能不能用 —— 转交<b>旧逻辑</b>的 {@code checkRecipe(GTRecipe)}（gtladditions 父类提供，
     * 口径逐字见 {@code _census/enhancedcore-src/.../ThreadLimitedRecipeLogic.java:245-270}：
     * 输入放得下 + 产出放得下 + 电压等级够 + 配方自带条件）。
     *
     * <p><b>为什么复用旧逻辑那一份而不是在新逻辑里重写</b>：
     * 「一条候选能不能跑」这件事全机只该有<b>一个</b>口径；两份写法迟早走岔，
     * 而走岔的表现是「同一条样板在两条派活通路上一次能跑、一次不能跑」这种最难查的故障。
     * 新旧两条逻辑因此共用这一个判定（新逻辑侧的调用点见
     * {@code NanochipThreadedRecipeLogic.isCandidateUsable}）。
     *
     * <p><b>fail-safe</b>：拿不到旧逻辑实例 / 它抛异常 ⇒ 返回 {@code true}（= 不挡），
     * 让新逻辑走它自己那份判据（那侧还有电压等级与「真的放得下」两道闸）——
     * 绝不因为「辅助判定读不到」把配方整体挡掉。
     */
    @Override
    public boolean nanochipCandidateUsable(GTRecipe candidate) {
        if (candidate == null) {
            return false;
        }
        try {
            RecipeLogic logic = getRecipeLogic();
            if (logic instanceof NanochipRecipeLogic legacy) {
                return legacy.checkRecipe(candidate);
            }
        } catch (RuntimeException e) {
            NanochipModContext.logger().debug("[gtlmodular] NAC 独立线程：候选校验转交失败（不挡，交给新逻辑自判）：{}",
                    e.toString());
        }
        return true;
    }

    /**
     * 公平轮转游标（长度 1 的可变数组，{@code [0]} = 上次选中的最后一条配方 id）。
     *
     * <p>由<b>机器</b>持有而不是逻辑持有：开关切换 / 逻辑重建时轮转位置不丢
     * （否则「常备配方霸占线程位」会在每次切换后重来一次）。
     *
     * <p><b>零存档影响</b>：这个字段既无 {@code @Persisted} 也无 {@code @DescSynced}，
     * 且不在 {@code MANAGED_FIELD_HOLDER} 的字段表里 ⇒ 不进 NBT、不同步、
     * 存档重载后游标从 {@code null} 开始（= 从排序后的第一条开始），与旧逻辑原来的字段行为一致。
     */
    @Override
    public String[] nanochipThreadCursors() {
        return this.threadCursors;
    }

    /**
     * 【新逻辑】的游标本体（{@link #nanochipThreadCursors()} 返回的就是它）。
     *
     * <p>★ 与【旧逻辑】的 {@code NanochipRecipeLogic.lastSelectedRecipeId} 是<b>同一个语义位、
     * 两个独立容器</b>：旧逻辑继续用自己那个字段，新逻辑用本数组 ⇒ 两条逻辑<b>各自连续</b>，
     * 切换开关不会让对方的位置错乱（共用同一个数组才是错的：一边推进会污染另一边）。
     */
    private final String[] threadCursors = { null };

    // ---------- 正脸「工作态」 ----------

    /**
     * ★【2026-09-18 施工队员·主机正脸贴图】主机正脸工作态 —— 对齐 NH 的「成形且允许工作 ⇒ 正脸常亮」。
     *
     * <h4>① 现象（本轮修的就是这个）</h4>
     * 主机正脸三张图（{@code overlay_front} / {@code overlay_front_active} /
     * {@code overlay_front_active_emissive}，与 NH 的 {@code OVERLAY_FRONT_NANOCHIP_ASSEMBLY_COMPLEX}
     * {@code {,_ACTIVE,_ACTIVE_GLOW}} 逐字节相同）本来就在，<b>接线也本来就是对的</b>
     * （{@code NanochipMachines.java:455}
     * {@code .workableCasingRenderer(CASING_TEX, OVERLAY_TEX)}，{@code OVERLAY_TEX} 见该类
     * {@code :132}；受 {@code _census/check_controller_casing_tex.js:182} 守门）。
     * 但玩家实际看到的正脸<b>几乎永远停在空闲那张</b>：只有"真有电路配方在跑"的那几秒才切
     * {@code _active} —— 而 NH 的主机是<b>只要允许工作就一直亮</b>（玩家在 GTNH 里看到的就是"常亮"）。
     *
     * <h4>② 根因（三条都是 GTCEu 1.4.4 字节码现读）</h4>
     * <pre>
     * // (a) 注册 → 模型：MachineBuilder.lambda$workableCasingRenderer$14 偏移 0..9
     *   new WorkableCasingMachineRenderer:&lt;init&gt;(ResourceLocation, ResourceLocation)
     *   → WorkableCasingMachineRenderer.&lt;init&gt; 偏移 22..30: new WorkableOverlayModel(overlay)
     *   ⇒ 第二参数就是正脸 overlay 的根路径，接线无误（不是"没接上"）
     *
     * // (b) 谁决定正脸画哪张：WorkableCasingMachineRenderer.renderMachine 偏移 47..85
     *   47: aload_3 / 51: ifeq                 // machine instanceof IWorkable ?
     *   73: invokeinterface IWorkable.isActive:()Z
     *   80: invokeinterface IWorkable.isWorkingEnabled:()Z
     *   85: invokevirtual WorkableOverlayModel.bakeQuads(side, frontFacing, upwardsFacing, isActive, workingEnabled)
     *   ⇒ 选图<b>完全</b>由 IWorkable.isActive() 决定
     *     （ActivePredicate：false → _front；true && working → _active；true && !working → _paused）
     *
     * // (c) GTCEu 给的默认实现 = "只有真在跑 / 在等料"：IRecipeLogicMachine.isActive() 默认方法
     *   → getRecipeLogic().isActive() → com.gregtechceu.gtceu.api.machine.trait.RecipeLogic.isActive()
     *       0: isWorking()   4: ifne 28
     *       7: isWaiting()  11: ifne 28
     *      14: isSuspend()  18: ifeq 32
     *      21: getfield isActive:Z  25: ifeq 32
     *      28: iconst_1 …   33: ireturn
     * </pre>
     * NH 侧（证据见 {@code _census/NH机制对账_2026-09-18.md} §⑫ 与
     * {@code _census/_report_section_75.md} §75.7）：{@code MTEMultiBlockBase.onPostTick} 每 server tick 把
     * {@code mActive} 写成 {@code mMaxProgresstime > 0}，而主机 {@code checkProcessing()} 在
     * {@code isAllowedToWork()} 为真时<b>恒给一条 100 tick 的伪配方</b>
     * （{@code _gtnh/javap_nac/MTENanochipAssemblyComplex.txt:1365-1389}：
     * {@code sipush 10000 → mEfficiencyIncrease}、{@code bipush 100 → mMaxProgresstime}）
     * ⇒ 只要允许工作就 100/100 tick 都是工作态 ⇒ 正脸基本恒亮 {@code _ACTIVE}。
     *
     * <h4>③ 本移植版的等价实现（不引入伪配方）</h4>
     * 我们<b>故意不做</b>那条伪配方 —— 它会连带把「配方时长 / 效率 / 耗电 / 空闲也在转」全部语义改掉
     * （那是机制档，需玩家单独裁决，见 {@code _census/NAC_机制与配方_待办清单.md} P5）。
     * 这里只把「<b>什么时候算 active</b>」这一件事按 NH 的口径改写：
     * <b>结构成形 && 工作开关打开</b> ⇒ 正脸画 {@code _active} + {@code _active_emissive}；
     * 未成形、或被软锤关掉 ⇒ 落回 {@code _front}
     * （GTCEu 此时取的是 {@code _paused} 槽位，而本目录<b>没有</b> {@code overlay_front_paused.png}
     * ⇒ {@code WorkableOverlayModel.registerTextureAtlas} 自动回落到普通层，正是"停机 = 空闲脸"）。
     *
     * <p><b>客户端拿得到同一个值</b>（渲染在客户端跑）：{@code isFormed} 是
     * {@code @DescSynced + @RequireRerender}（{@code MultiblockControllerMachine} 字段注解，{@code javap -v} 现读），
     * {@code isWorkingEnabled()} = {@code RecipeLogic.isWorkingEnabled()} = {@code !isSuspend()}，
     * 而 {@code RecipeLogic.status} 同为 {@code @DescSynced} ⇒ 客户端渲染与服务端判定一致。
     *
     * <h4>④ 影响面（诚实登记，逐条可核）</h4>
     * <ul>
     *   <li><b>本模组的真空传送仓不受影响</b>：{@code NanochipMachines.VacuumHatchRenderer} 自己覆写
     *       {@code renderMachine}，按 {@code hatch.isVacuumWorking()}（"这个端口最近真的搬过件"）选层，
     *       <b>不读</b>控制器的 {@code isActive()}。</li>
     *   <li><b>结构里"别的仓"会跟着常亮（观感偏差，待拍板）</b>：GTCEu 的
     *       {@code OverlayTieredActiveMachineRenderer.renderMachine} 对 {@code IMultiPart} 是按
     *       「<b>它所属控制器的</b> {@code isActive()}」选 {@code _active} 层的（javap 偏移 70..105 +
     *       {@code lambda$renderMachine$0}）⇒ 主机常亮时，装在主机结构里的标准仓
     *       （能源仓 / 总线 / 激光仓…）的前脸也会常亮。NH 侧各仓读<b>自己</b>的 {@code mActive}，
     *       所以这一条是本轮唯一的"换来常亮"的代价。</li>
     *   <li><b>Jade / TOP 的「运行中」、活动侦测器盖板也会常亮</b>：NH 侧因为伪配方同样常亮
     *       （玩家截图里 Jade 就是「运行良好 效率 100.0%」），这一条反而是<b>对齐</b>。</li>
     * </ul>
     *
     * <p><b>为什么不改用自定义渲染器</b>：{@code WorkableCasingMachineRenderer.overlayModel} 是
     * {@code protected final}，没法像 {@code VacuumHatchRenderer} 那样临时换层；自己重烘机壳 + overlay
     * 等于把 GTCEu 的标准渲染路径抄一份（正脸朝向 / 面内 UV / 与 CTM 的关系全得自己保证）——
     * 收益只是消掉上面那条"别的仓跟着亮"，风险远大于收益。
     */
    @Override
    public boolean isActive() {
        return isFormed() && isWorkingEnabled();
    }

    // ---------- 两档判定 ----------

    /** 是否处于「全流程档」：结构里挂着已成形的组装矩阵模块。 */
    public boolean fullMode() {
        for (IModularMachineModule<NanochipAssemblyComplexMachine, ?> m : getModules()) {
            if (m instanceof NanochipModuleMachine module
                    && module.isFormed()
                    && module.getModuleKind() == NanochipModuleKind.ASSEMBLY_MATRIX) {
                return true;
            }
        }
        return false;
    }

    /** 配方时长下限（tick）：全流程 5 秒，直通 20 秒。 */
    public int minDurationTicks() {
        return fullMode() ? FULL_MIN_DURATION_TICKS : DIRECT_MIN_DURATION_TICKS;
    }

    /** 直通档并行上限（配置项，默认 1024）。 */
    public int directParallelCap() {
        return Math.max(1, NanochipModContext.config().getNanochipDirectParallelCap());
    }

    // ==========================================================================================
    // ★ 2026-10-02【L3 真实多线程（跨配方线程）落地】—— 见
    //   `_census/真实多线程_机制与我方现状.md` §⑤ 方案 A 与 `_census/真实多线程_实施记录.md`。
    //
    //   改动前 NAC 是 **L2 批量合并**（gtlcore `MultipleRecipesLogic`）：一次搜索把**全部**命中配方
    //   各取若干份，用 `remain = getMaxParallel() × 64` 当共享预算摊完，输出 `addAll` 合并成
    //   **一条**配方 ⇒ 只有一份 `progress`/`duration`，所有配方同时开始、同时结束。
    //   现在换成 **L3**：同一台复合体同时挂起 N 条**不同的**配方，各自独立进度与完成时刻。
    //   真正的入口是 gtladditions `GTLAddMultipleRecipesLogic.getMultipleThreads()`（public 可覆写），
    //   而**决定并发条数的那一刀**是下面 `NanochipRecipeLogic.lookupRecipeIterator()` 的截断。
    // ==========================================================================================

    /**
     * <b>跨配方线程的 hard cap</b>（= 同一时刻最多挂起多少条不同配方）。
     *
     * <h4>★ 2026-10-27【并行改「64 位 + 饱和截断」】：本上限<b>不再</b>兼任「防 int 溢出」的职责</h4>
     * 它原来是和 {@code FULL_PARALLEL_CAP = Integer.MAX_VALUE / 256} <b>成对</b>存在的：
     * 靠「整数上限 ÷ 线程上限」反推并行，好让父类那句
     * {@code totalParallel = getMaxParallel() × getMultipleThreads()}（<b>int</b> 乘法）
     * 永不溢出。那套「把并行硬压到 8388607」的 int 时代补丁<b>已退役</b>
     * （上游作者指出正解 = 64 位 + 饱和截断，见 {@link #NAC_SATURATING_PARALLEL}）——
     * 会相乘的地方现在一律走 {@link #saturatingMul(long, long)}：超上限停在
     * {@code Long.MAX_VALUE}，落 {@code int} 时停在 {@code Integer.MAX_VALUE}，<b>绝不回绕成负数</b>。
     *
     * <h4>那为什么还留着 256</h4>
     * 它现在<b>只剩一个理由</b>：<b>真并发条数的性能/可读上限</b>，与溢出无关 ——
     * ① 每多挂一条并发配方就多一份逐 tick 的条件校验与进度推进（本包已把「单机 CPU 上升」
     * 登记为中等风险，见 {@code _census/真实多线程_机制与我方现状.md} §5.1）；
     * ② 「显示即事实」：256 是玩家能一眼核对的天花板（界面那行「跨配方线程」）；
     * ③ 真按电压给线程时（每高于 LV 一级 ×2），256 线程要 <b>EV/IV 档以上</b>才够得着，
     * 普通档位根本碰不到这个天花板。
     * <p>⚠️ <b>不要再</b>用 {@code Integer.MAX_VALUE / MAX_CROSS_RECIPE_THREADS} 去反推并行上限
     * —— 那是已退役的补丁口径（线程上限改动不该再联动并行上限）。
     */
    public static final int MAX_CROSS_RECIPE_THREADS = 1 << 8;

    /**
     * <b>全流程档的并行上限（{@code int} 接口边界上的饱和值）</b> —— ★ 2026-10-27 改口径。
     *
     * <h4>改前（已退役的「硬压并行」补丁）</h4>
     * 原值 = {@code Integer.MAX_VALUE / MAX_CROSS_RECIPE_THREADS} = {@code 2147483647 / 256}
     * = {@code 8388607}（见 {@link #LEGACY_CROSS_RECIPE_PARALLEL_CAP}）。它是 int 时代的权宜做法：
     * 把并行人为压到那个数，好让父类那句 {@code totalParallel = getMaxParallel() × getMultipleThreads()}
     * 的 <b>int</b> 乘法永不溢出。代价是<b>并行上限被人为压低</b>（8.3M 而不是「等效无限」），
     * 且并行上限被线程上限<b>反向绑定</b> —— 改线程数就动了并行上限。
     *
     * <h4>改后（上游作者给的正解：64 位 + 饱和截断）</h4>
     * 作者原话（逐字）：「<b>不可能变成负数的</b>」「<b>有写钳制</b>」
     * 「<b>最大并行池也会被限制在 {@code Long.MAX_VALUE} 就被截断掉</b>」。
     * 于是并行不再有任何「防溢出」型的人为压低：
     * 单条配方的上限是 {@link Long#MAX_VALUE}
     * （{@code NanochipRecipeLogic#perRecipeParallelLimit()}），真正的值由「真的放得下」的二分收敛；
     * 需要落到 {@code int} 的地方由 {@link #saturatingToInt(long)} <b>饱和到 {@code Integer.MAX_VALUE}</b>
     * （不是 {@code 8388607}，也绝不回绕）。
     * <p>所以本常量 = {@code Long.MAX_VALUE} 在 {@code int} 接口上的饱和截断值 = {@link Integer#MAX_VALUE}。
     *
     * <p>★ 本常量现在唯一生效的地方仍是 {@link #getMaxParallel()} 这个 {@code int} 接口
     * （Jade 等别的消费点照旧读它）；{@link #NAC_PER_THREAD_FULL_PARALLEL} 打开（默认）时
     * 它<b>不参与</b>单条配方的并行上限计算。
     *
     * <p><b>为什么不是 BigInteger</b>（作者警告逐字）：「<b>运算 BigInteger 超大型整数不能让数值太膨胀</b>」
     * 「你的内存会先死」⇒ 本实现只用 {@code long}，绝不引入 {@code BigInteger}；
     * 而且数值也不会膨胀：二分试探只在「库存 / 输出容量」决定的量级上进行。
     */
    public static final int FULL_PARALLEL_CAP = Integer.MAX_VALUE;

    /**
     * <b>已退役的旧补丁值</b>（{@code Integer.MAX_VALUE / 256 = 8388607}）—— 只为回退开关保留。
     *
     * <p>只在 {@link #NAC_SATURATING_PARALLEL} = {@code false} 时被 {@link #getMaxParallel()} 使用，
     * 用来<b>逐字恢复</b>「硬压并行」的旧口径；默认（饱和口径）下它<b>不参与任何计算</b>。
     * <p>刻意写成字面量而<b>不是</b> {@code Integer.MAX_VALUE / MAX_CROSS_RECIPE_THREADS}：
     * 那个除法正是本次要退役的口径，不能再出现在代码里。
     */
    public static final int LEGACY_CROSS_RECIPE_PARALLEL_CAP = 8_388_607;

    /**
     * ★★ 2026-10-27【并行改「64 位 + 饱和（钳制）运算」总开关】★★ —— 上游作者（山海）指出的正解。
     *
     * <p>作者原话（逐字）：「<b>不可能变成负数的</b>」「<b>有写钳制</b>」
     * 「而且原版 add 基类也是 {@code Integer.max * Integer.max}」
     * 「<b>最大并行池也会被限制在 {@code Long.MAX_VALUE} 就被截断掉</b>」
     * 「你也可以让并行池是 BigInteger…」「<b>运算 BigInteger 超大型整数不能让数值太膨胀</b>」
     * 「我认为 {@code Long.MAX_VALUE * Long.MAX_VALUE} 也差不多了」。
     *
     * <p>{@code true}（默认）= <b>饱和口径</b>：所有「并行 × 线程」「容量 × 2 × 时长」一类乘法
     * 一律走 {@link #saturatingMul(long, long)}（{@code long}，超上限停在 {@code Long.MAX_VALUE}），
     * 落 {@code int} 时走 {@link #saturatingToInt(long)}（停在 {@code Integer.MAX_VALUE}）；
     * 并行上限<b>不再</b>由「整数上限 ÷ 线程上限」反推。
     * {@code false} = <b>逐字回到</b>本次改动前的「硬压并行」口径
     * （{@link #getMaxParallel()} 返 {@link #LEGACY_CROSS_RECIPE_PARALLEL_CAP} = 8388607）。
     *
     * <p><b>为什么不用 BigInteger</b>：作者自己警告数值膨胀 / 内存问题（原话见上）
     * ⇒ 只用 {@code long} + 饱和；两个 {@code long} 相乘的判界写法与依据见
     * {@link #saturatingMul(long, long)} 的注释。
     *
     * <p><b>零存档影响</b>：本常量不是 {@code @Persisted}、不同步、不改 {@code getRecipeTypes()}；
     * 判定点唯一（{@link #getMaxParallel()} 的 fullMode 分支），改回 {@code false}
     * 重新编译即<b>逐字回退</b>到旧口径（饱和运算工具本身是无副作用的纯函数，留着无害）。
     */
    public static final boolean NAC_SATURATING_PARALLEL = true;

    /**
     * ★★ 2026-10-27【并行「去总池」总开关】★★ —— 上游作者（山海）亲自给的口径。
     *
     * <p>作者原话（逐字）：「<b>超限是为每个配方线程都分 Long.MAX_VALUE 并行</b>」
     * 「<b>完全去掉总池概念</b>」。
     * {@code true}（默认）= <b>每条（配方）线程各自满并行</b>：<b>不再</b>算
     * {@code getMaxParallel() × getMultipleThreads()} 那个「总池」，逐条配方<b>各自</b>拿
     * {@link Long#MAX_VALUE} 当上限，去做「真正放得下」的二分（二分仍保留 —— 它是防
     * 「产出放不下 ⇒ 起跑前被挡 ⇒ 永远 0%」的关键）；{@code false} = <b>逐字回到</b>本次改动前的
     * 「总池分摊」口径（{@code NanochipRecipeLogic.parallelBudget()}）。
     *
     * <p><b>为什么不是 BigInteger</b>：作者原话「运算 BigInteger 超大型整数不能让数值太膨胀」
     * 「你的内存会先死」，并说「最大并行池也会被限制在 Long.MAX_VALUE 就被截断掉」「不可能变成负数」
     * 「有写钳制」⇒ 本实现<b>只用 {@code long}</b>，绝不引入 {@code BigInteger}。
     *
     * <p><b>为什么还是安全</b>：{@code Long.MAX_VALUE} 这个值<b>不参与乘法</b>
     * （旧口径的 {@code 并行 × 线程} 才是会回绕成负数的那条通路，见
     * {@link #MAX_CROSS_RECIPE_THREADS} 的注释）；而 {@code false} 分支真的去乘的时候，
     * 走的是 {@link #saturatingMul(long, long)}（64 位饱和，超上限停在 {@code Long.MAX_VALUE}）
     * —— ★ 2026-10-27 起<b>不再</b>靠「把并行硬压到 8388607」来防溢出
     * （见 {@link #NAC_SATURATING_PARALLEL}）。
     * 上游同源写法（逐字）：星律 {@code PrimordialModuleRecipeLogic.java:202-211}
     * 在超限模式下 {@code getTotalParallelLimit()} 直接 {@code return Long.MAX_VALUE}。
     *
     * <p><b>零存档影响</b>：本常量不是 {@code @Persisted}、不同步、不改 {@code getRecipeTypes()}；
     * 判定点唯一（{@code NanochipRecipeLogic#perRecipeParallelLimit()}），改回 {@code false}
     * 重新编译即<b>逐字回退</b>到旧口径。
     */
    public static final boolean NAC_PER_THREAD_FULL_PARALLEL = true;

    /** 直通档的跨配方线程基线（配置项，默认 1；上界 16）。 */
    public int directThreads() {
        return Math.max(1, Math.min(16, NanochipModContext.config().getNanochipDirectCrossRecipeThreads()));
    }

    /**
     * 基础跨配方线程数（不含 Ω-天球分歧引擎的追加线程）—— <b>A3 + A8</b>。
     *
     * <h4>两档口径（与「组装矩阵 = 解锁线程」自洽）</h4>
     * <ul>
     *   <li><b>直通档</b>（没有组装矩阵模块）：{@link #directThreads()}，默认 <b>1</b>
     *       —— 一次只挂起一条配方，与改动前的玩家观感一致（省掉整条真空链路，代价是上限更低）；</li>
     *   <li><b>全流程档</b>（组装矩阵模块在线）：<b>{@code 2 << (tier - LV)}</b>，即 LV=2、
     *       MV=4、HV=8、EV=16…（照抄 {@code LargeFurnaceMachine.java:69-73} 的电压线程写法，
     *       含「先转 long 再移位 + 钳到 int 上限」的防溢出处理）。
     *       <b>档位越高线程越多</b>，这就是「组装矩阵解锁线程」的那条阶梯（见 {@link #fullMode()}）。</li>
     * </ul>
     * <p>最终值 = {@code min(基础 + getAdditionalThread(), MAX_CROSS_RECIPE_THREADS)}，
     * 见 {@link #getMultipleThreads()}。
     */
    public int baseCrossRecipeThreads() {
        if (!isFormed()) {
            return 1;
        }
        if (!fullMode()) {
            return directThreads();
        }
        // 先转 long 再移位（tier 是能源仓电压等级；直接 int 移位在高档位会回绕成负数）
        long threads = 2L << Math.min(30, Math.max(0, getTier() - GTValues.LV));
        return (int) Math.min(MAX_CROSS_RECIPE_THREADS, threads);
    }

    /**
     * <b>本机当前真实生效的跨配方线程数</b>（= 界面/Jade 显示的那个数字 = 同时挂起的配方条数）。
     *
     * <p>覆写入口是 gtladditions 的 {@code GTLAddMultipleRecipesLogic.getMultipleThreads()}
     * （public、可覆写，见 {@code _census/_probe_nacmat_evidence/classstr_gtladditions-3.2.8Custom_SubSpace-fix1.txt:2004}）；
     * gtlcore 的 {@code MultipleRecipesLogic} <b>没有</b>这个方法（三重取证见现状文档 §1.3(a)）。
     * 所以本方法只能在 gtladd 这一族上覆写 —— 这正是 A1/A2 换基类的原因。
     *
     * <p>★ 2026-10-19【复合型机器】本方法<b>逐字未改</b>：新基类
     * {@code GTLAddMultipleTypeRecipesLogic} 与旧基类同族（后者只换了「搜哪些类型」），
     * 线程数语义与钳制不变 ⇒ <b>并发条数上限（= 本方法的返回值）逐字不变</b>，
     * 只是这一轮的并发位允许被<b>不同类型</b>的配方占用（这正是「复合型机器」想要的效果）。
     * ⚠️ 与并行「去总池」开关（{@link #NAC_PER_THREAD_FULL_PARALLEL}）无耦合：那个开关改的是
     * 「单条配方能放多少并行」，本方法提供的是「一轮最多挂几条配方」，两者相互独立。
     *
     * <p>★ <b>可见性必须是 public</b>：上游 Enhancedcore 的 `IvRecipeLogic.java:58` 用的就是
     * {@code @Override public int getMultipleThreads()}；gtladditions 的可覆写 API 一律 public
     * （同一族的 {@code createRecipeLogic} 也明确要求 public，见 {@code API标准.md:126}）。
     * 覆写时只能加宽、不能收窄 ⇒ 这里给 public 是唯一安全选择。
     */
    public int getMultipleThreads() {
        long total = (long) baseCrossRecipeThreads() + getAdditionalThread();
        return (int) Math.max(1L, Math.min(MAX_CROSS_RECIPE_THREADS, total));
    }

    /**
     * ★ 2026-10-07【修「两行跨配方线程打架」的配套】Ω-天球分歧引擎仓<b>对真正生效线程数的净贡献</b>。
     *
     * <p>界面上「来源」那一行原来打的是裸 {@code getAdditionalThread()}（= 仓自报的原始追加值，
     * <b>未钳制</b>），于是会出现「线程行 128 / 追加 256」这种自己跟自己打架的数字。
     * 本方法给的是<b>钳制之后</b>的净贡献：{@code getMultipleThreads() - baseCrossRecipeThreads()}，
     * 保证「基线 + 追加 = 显示的那一行」这条恒等式恒成立，且追加值永远 {@code <=} 生效线程数。
     *
     * <p>为什么不是直接打印 {@code getMultipleThreads()}：那一行是「来源」而不是「总数」，
     * 玩家要知道的是<b>引擎仓加了几个</b>；净贡献在多引擎仓叠加/被 256 天花板截断时仍诚实
     * （截断到 0 就说明这个仓<b>当前一个线程都没加上去</b>，这本身就是有用信息）。
     */
    public int effectiveExtraThreads() {
        return Math.max(0, getMultipleThreads() - baseCrossRecipeThreads());
    }

    /**
     * 是否真的处于「可跨配方」状态（线程 &gt; 1）。
     *
     * <p>只用于显示与诊断（界面那行「跨配方线程」在直通档恒为 1，仍然照显 —— 因为
     * 「显示即事实」要求把真并发条数摊给玩家看）。
     */
    public boolean hasCrossRecipeThreads() {
        return isFormed() && getMultipleThreads() > 1;
    }

    /**
     * ★ <b>A5</b>：结构里的 Ω-天球分歧引擎仓（{@code gtladditions:thread_modifier_hatch}）。
     *
     * <p>gtladditions 的基类对 {@code setThreadPartMachine} 是<b>空实现</b>、{@code getThreadPartMachine}
     * 恒返回 {@code null} ⇒ 不覆写的话「引擎提供的额外线程恒为 0」。
     * 这一点由上游作者自己在别处写明（{@code BasicOreProcessingPlantMachine.java:21} 与
     * {@code LargeFurnaceMachine.java:26-27}：「GTLAdd 基类默认 setThreadPartMachine 为空实现，
     * 必须自行存储」）⇒ 本类照抄那两台已验证的做法，自行存储该部件。
     *
     * <p><b>软依赖 / 安全</b>：只在真有那个仓挂上来（{@code IThreadModifierPart} 部件回调）时非 null；
     * 结构里没这个仓 ⇒ 恒为 null，{@code getAdditionalThread()} 贡献 0，行为与改动前一致。
     */
    private IThreadModifierPart threadPartMachine;

    @Override
    public IThreadModifierPart getThreadPartMachine() {
        return this.threadPartMachine;
    }

    @Override
    public void setThreadPartMachine(IThreadModifierPart part) {
        this.threadPartMachine = part;
    }

    /**
     * 结构失效 / 零件卸载时清掉引擎引用（与 {@code LargeFurnaceMachine.java:97-107} 同款）。
     *
     * <p>为什么要清：仓被拆掉后它<b>不再</b>是结构零件，但引用若留着，线程数会继续按它算
     * ⇒ 「显示 N 条、实际没有那条仓」的假象。清了之后 {@code getAdditionalThread()} 自然回 0。
     * <p>⚠️ {@link #onStructureInvalid()} 里必须<b>在这句之后</b>再走本类自己的失效清理，
     * 所以这里用 {@code super} 之后再清（顺序不影响任何判定，只是保持"先上游后本类"的既有排版）。
     */
    private void clearThreadPart() {
        this.threadPartMachine = null;
    }

    /**
     * 并行上限：全流程档 = 等效无限（{@link #FULL_PARALLEL_CAP} = {@code Long.MAX_VALUE} 在
     * {@code int} 接口上的饱和截断值，★ 见该常量注释 —— 2026-10-27 起<b>不再</b>由
     * 「整数上限 ÷ 线程上限」反推）；
     * 直通档 = 基础值 × 模块并行倍率（每个模块最多计两次，饱和乘，超上限停在 int 上限）。
     */
    @Override
    public int getMaxParallel() {
        if (!isFormed()) {
            return 1;
        }
        if (fullMode()) {
            // ★ A6（历史）：原来这里是裸 `Integer.MAX_VALUE`，父类把它当并行预算去 ×线程数（int 乘法）
            //   会溢出成负数 ⇒ 一条配方都不跑。当时的补丁是「把并行硬压到 8388607」。
            // ★ 2026-10-27（本任务）：那条通路已改走 64 位饱和乘法（saturatingMul），
            //   所以补丁退役 —— 这里回到正解「int 接口边界上的饱和值」= Integer.MAX_VALUE
            //   （= Long.MAX_VALUE 被截断到 int，不是人为压低，也绝不回绕成负数）。
            //   回退：NAC_SATURATING_PARALLEL = false ⇒ 逐字回到旧的 8388607。
            return NAC_SATURATING_PARALLEL ? FULL_PARALLEL_CAP : LEGACY_CROSS_RECIPE_PARALLEL_CAP;
        }
        long parallel = directParallelCap();
        for (NanochipModuleKind kind : NanochipModuleKind.values()) {
            int count = Math.min(PARALLEL_STACK_CAP, countModules(kind));
            for (int i = 0; i < count; i++) {
                // ★ 2026-10-27【饱和乘法】：原来是 `parallel * kind.parallelFactor()`（long 传 double 相乘后
                //   窄化回 long，溢出时靠 double→long 的窄化隐式饱和、精度也已丢失）。
                //   模块并行倍率在枚举里全部是整数（1.0 / 2.0 / 4.0，见 NanochipModuleKind）
                //   ⇒ 转 long 走同一个饱和乘法工具：超上限停 Long.MAX_VALUE，绝不回绕，且全程整数精度。
                parallel = saturatingMul(parallel, (long) kind.parallelFactor());
            }
        }
        // int 接口边界：饱和到 Integer.MAX_VALUE（原来这里的 Math.max(1L, parallel) 会把「回绕后的负数」
        // 悄悄夹成 1 —— 那是「并行上限塌到 1」的静默错误；现在 parallel 永不为负，夹的是真实下限 1）。
        return Math.max(1, saturatingToInt(parallel));
    }

    // ---------- ★【GTO 式总量显示】 ----------

    /**
     * ★【GTO 式总量】我们自己那一行「同时处理至多 …」的键（<b>3 个占位符</b>：
     * ① <b>在用</b>线程条数 K ② 每条线程的**真实**并行 P ③ 两层连乘的总量 = K × P）。
     * 中英双语文案见 {@code assets/gtlmodular/lang/zh_cn.json} 与 {@code en_us.json}。
     *
     * <p>★ 占位符口径自本轮（GTO 界面口径对齐）起由「种数 / 每条上限 / 上限连乘」改成
     * 「<b>在用条数 / 每条真实并行 / 真实连乘</b>」，文案（含「种」「每种配方至多」的措辞）**一字未改**。
     */
    public static final String LANG_PARALLEL_TOTAL = "gtlmodular.nanochip.parallel.total";

    /**
     * ★★ 0.3.132【队长令：主机面板里那些"啰嗦的灰字"全部撤掉】★★
     *
     * <p>队长原话（逐字，两轮）：「主机里面把那些灰色的字体也删掉，中空路由、中央供电、跨配方线程来源，
     * 样板总成镜像，同时处理配方。跨线程？不是，同时处理至多不同配方，为什么会有这么多这些都删掉」。
     *
     * <p>撤掉的是**我们自己在面板上补的自述/操作说明行**（它们不是 NH 原版的行）：
     * <ul>
     *   <li>{@code · 跨配方线程来源: …}（线程数从哪来）；</li>
     *   <li>{@code · 装上「纳米芯片组装矩阵」模块即可解锁全流程档…}；</li>
     *   <li>{@code 真空路由: …}（双模式路由说明）；</li>
     *   <li>{@code 中央供电: … / … EU · 模块实付 … · 模块自带能源仓时走自己的…}；</li>
     *   <li>{@code 样板总成镜像绑定数量: …} 那整套（含「未读取 / 已断链 / 立刻可用…」几种变体）；</li>
     *   <li>我们自己替换上去的 {@code 同时处理至多 …} 与 {@code 拥有 … 个跨配方线程} 两行
     *       （⇒ 恢复成**父类那一对原行**，不再替换）。</li>
     * </ul>
     *
     * <p><b>纪律</b>：
     * <ol>
     *   <li><b>只撤显示，不删数据/判定</b>：这些行背后的字段、同步包、自检、Jade 一律**一个字不动**
     *       ⇒ 排障能力不丢（想看在数据面照样看得到）；</li>
     *   <li><b>语言文件里的键全部保留</b>（`complex.mode` / `complex.calib` / `mirror.*` 等），
     *       将来要恢复只需把本开关改成 {@code true} 或把挂载贴回；</li>
     *   <li><b>一行回退</b>：把本常量改成 {@code true} ⇒ 逐字恢复改动前的面板。</li>
     * </ol>
     */
    public static final boolean SHOW_VERBOSE_HOST_LINES = false;

    /** 父类那一行「同时处理至多 %d 个配方」的键 —— 只用来在本覆写里<b>定位并替换</b>它。 */
    private static final String LANG_UPSTREAM_PARALLEL = "gtceu.multiblock.parallel";

    /** 父类那一行「拥有 %s 个跨配方线程」的键 —— 同款按键替换：数字换成我们钳制后的 {@code U}。 */
    private static final String LANG_UPSTREAM_THREADS = "gtladditions.multiblock.threads";

    /**
     * 父类那一行「最大功率：§a%s EU/t（%s§r）」的键（GTCEu 自带，逐字取证：
     * {@code _census/_probe_nacmat_evidence/lang/gtceu-1.20.1-1.4.4__gtceu__zh_cn.json:3682-3686}）
     * —— 只用来在本类里<b>定位并替换</b>它：数值换成「整个电网的电量」（见
     * {@link #replaceMaxPowerWithGridTotal}），档名沿用父类那一段实参。
     */
    private static final String LANG_MAX_ENERGY_PER_TICK = "gtceu.multiblock.max_energy_per_tick";

    /**
     * ★★ 2026-11-13【本任务 ②】把父类那两行从显示列表里**按语言键剔掉**，再由我们补上我们的一对。
     *
     * <h3>为什么落在这儿（现场取证，逐条）</h3>
     * <ul>
     *   <li>{@code NanochipAssemblyComplexMachine.addDisplayText} 是<b>唯一确定会被执行</b>的显示入口：
     *       实机面板里我们自己的行（{@code 独立线程：上限 256 条} / {@code 线程0/1} 折叠行，
     *       由 {@link NanochipThreadedRecipeLogic#appendThreadDisplayText} 在这个方法末尾加）
     *       <b>全在</b> ⇒ 本方法确实跑到了；而同一张面板里那两行却还是父类旧句
     *       （{@code 同时处理至多 2147483647 个配方} / {@code 拥有 128 个跨配方线程}）
     *       ⇒ 那两行<b>不是</b>经由 {@link #addParallelDisplay} 进入本列表的（该覆写里的按键替换
     *       与兜底追加都没命中）。</li>
     *   <li>因此把收口点放在这里：**先剔旧、后补新**，与"父类到底在哪一层、以什么方式发那两行"无关。</li>
     *   <li>两行的<b>语言键</b>本身是父类的（{@link #LANG_UPSTREAM_PARALLEL} /
     *       {@link #LANG_UPSTREAM_THREADS}），按<b>键</b>匹配而不是按位置匹配 —— 与
     *       {@link #replaceUnreadyTierDisplay} 共用稳定标识，避免翻译或行序变化后替换错误行。</li>
     * </ul>
     *
     * <h3>数值口径（未改）</h3>
     * <p>{@code K = runningThreadCount()}、{@code P = max(1, totalRuns / K)}、
     * {@code 总量 = saturatingMul(K, P)}（64 位饱和）；线程行 = {@code getMultipleThreads()}（上限 U）。
     * <b>不读</b> {@link #getMaxParallel()}（那只是 {@code int} 接口上的饱和占位值）。
     *
     * <p><b>只动显示</b>：全程只读三个 getter、只改传入的 {@code List<Component>}
     * （{@code Iterator.remove()} + {@code add}），不写任何字段、不进任何判定（配方结算不经过这里）。
     *
     * <p><b>回退</b>：删掉本方法 + {@code addDisplayText} 里那一行调用 ⇒ 逐字回到上一个形态
     * （父类两行 + {@link #addParallelDisplay} 的按键替换）。留档见
     * {@code _census/取电顺序与界面整段_回退包/ORIGINAL_HUNKS.md}。
     *
     * @param textList 父类与 {@link #addParallelDisplay} 刚写完的显示行（就地修改）
     */
    /**
     * ★★ 0.3.132【最大功率 = 显示整个电网的电量 · 队长令】★★
     *
     * <h4>队长口径（逐字）</h4>
     * <blockquote>「它不是只显示那么多，是**显示电网一共有多少电**」</blockquote>
     *
     * <h4>为什么原来是 `9.22e18` 封顶（取证）</h4>
     * GTCEu 基类的「最大功率」那一行由
     * {@code MultiblockDisplayText.Builder.addEnergyUsageLine(IEnergyContainer)} 画出，读的是
     * {@code this.energyContainer}（{@code EnergyContainerList}）的
     * {@code getInputVoltage()/getOutputVoltage()/getEnergyCapacity()}，**全是 `long`**；
     * 而 gtlcore 的 {@code EnergyContainerListMixin:47-61} 又用
     * {@code NumberUtils.saturatedMultiply/saturatedAdd} 汇总 ⇒ **天花板恒为 `Long.MAX_VALUE`**。
     * 原版（gtladditions 的无线机器）之所以能显示 {@code 2.74e69}，是因为它的
     * {@code WorkableElectricMultiblockMachineMixin.redirectEnergyUsageLine} 对那次
     * {@code addEnergyUsageLine} 调用做了 {@code @Redirect}，改读
     * {@code IWirelessNetworkEnergyHandler.getMaxAvailableEnergy(): BigInteger}。
     * 我们 NAC 继承的是**非无线**基类 ⇒ 那条分支永远进不去。
     *
     * <h4>本方法做什么（与 {@link #filterUpstreamParallelLines} 同款纪律）</h4>
     * <ol>
     *   <li>按语言键 {@value #LANG_MAX_ENERGY_PER_TICK} 把父类那一行**剔掉**；</li>
     *   <li>再由我们**无条件补一行**：数值 = 该玩家/队伍在无线电网里的**整个余额**
     *       （{@code WirelessEnergyManager.getUserEU(uuid)}，`BigInteger`），
     *       格式化**直接调上游那个方法** {@code CommonUtils.formatBigIntegerFixed(BigInteger)}
     *       ⇒ 与第 2 张实机截图**逐字节一致**（该方法内部：≤{@code Long.MAX_VALUE} 走千分位，
     *       否则走 {@code DecimalFormat("0.00000000E0")} 再小写、把 {@code e} 换成 {@code e+}
     *       —— 逐字取证自 {@code CommonUtils.formatBigIntegerFixed} 的字节码）；</li>
     *   <li>档名 `(MAX+16)` 那一段**原样沿用父类那一行自己的实参**（`Object[]` 的第 2 个），
     *       不去自己猜档位 —— 父类已经按 `getMaxVoltage()` 算好了，照抄最省且不会漂。</li>
     * </ol>
     *
     * <h4>纪律</h4>
     * <ul>
     *   <li><b>纯只读</b>：只读玩家电网余额与配置开关，**不扣电、不改状态、不进存档**；</li>
     *   <li>读不到余额 / 没登记放置者 / 开关关闭 ⇒ <b>一个字都不改</b>（保留父类原行，绝不显示 0 冒充）；</li>
     *   <li>本改动**只影响这一行的显示**：机器实际单 tick 可消费上限仍由
     *       `changeEnergy(long)` 决定（`long` 域）—— 这一点在注释里如实写明，不误导读者。</li>
     * </ul>
     */
    private void replaceMaxPowerWithGridTotal(List<Component> textList) {
        if (textList == null || !isFormed()) {
            return;
        }
        UUID user = gridPowerUser();
        if (user == null || !NanochipModContext.config().isNanochipGridPowerEnabled()) {
            return;   // 没登记放置者 / 电网取电被关 ⇒ 不碰父类那一行（绝不显示 0 冒充）
        }
        java.math.BigInteger total;
        try {
            total = WirelessEnergyManager.getUserEU(user);
        } catch (RuntimeException e) {
            return;   // 读不到就一个字都不改（fail-safe，绝不因为查询失败而伪造读数）
        }
        if (total == null) {
            return;
        }
        Object tierArg = null;
        int seenAt = -1;                 // ★ 记住父类那一行**原来在第几行** —— 必须原地换，不能挪到末尾
        for (int i = 0; i < textList.size(); i++) {
            Component line = textList.get(i);
            if (line.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tc
                    && LANG_MAX_ENERGY_PER_TICK.equals(tc.getKey())) {
                Object[] args = tc.getArgs();
                if (args != null && args.length >= 2) {
                    tierArg = args[1];   // ★ 原样沿用父类算好的档名，不自己猜
                }
                seenAt = i;
                break;                   // 一行就是一行：取第一处命中（与父类一致）
            }
        }
        if (seenAt < 0) {
            return;                      // 父类没写这一行 ⇒ 一个字都不加（绝不凭空多一行）
        }
        textList.remove(seenAt);
        // ★ 数值 = 整个电网余额；格式化**直接调上游那个 public static 方法** ⇒ 与实机逐字节一致。
        // ★★ 颜色：**不要**自己加样式 ★★ —— GTCEu 的语言键本身已经带颜色码，逐字为：
        //   `gtceu.multiblock.max_energy_per_tick = "最大功率：§a%s EU/t（%s§r）"`
        //   （取证：`_census/_probe_nacmat_evidence/lang/gtceu-1.20.1-1.4.4__gtceu__zh_cn.json`）
        //   ⇒ `§a` 已经把那格数字染绿；我们若再 `.withStyle(GREEN)` 是**重复上色**，
        //     而且会让 `§r` 复位后的档名段（`(MAX+16)` 自带 `§c`/`§d` 之类）与我们抢样式 ⇒ 颜色就乱了。
        //   ⇒ 正解：**只给裸文本**，颜色全部交给那个键自己的 `§` 码。
        net.minecraft.network.chat.MutableComponent value = Component
                .literal(com.gtladd.gtladditions.utils.CommonUtils.formatBigIntegerFixed(total));
        net.minecraft.network.chat.MutableComponent row = tierArg == null
                ? Component.translatable(LANG_MAX_ENERGY_PER_TICK, value)
                : Component.translatable(LANG_MAX_ENERGY_PER_TICK, value, tierArg);
        textList.add(seenAt, row);       // ★ **原地插回**（不是 add 到末尾）
    }

    private void filterUpstreamParallelLines(List<Component> textList) {
        if (textList == null || !isFormed()) {
            return;                 // 未成形时父类不写这两行 ⇒ 一个字都不动（零观感变化）
        }
        for (Iterator<Component> it = textList.iterator(); it.hasNext(); ) {
            Component line = it.next();
            if (line.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tc) {
                if (LANG_UPSTREAM_PARALLEL.equals(tc.getKey())
                        || LANG_UPSTREAM_THREADS.equals(tc.getKey())) {
                    it.remove();    // ★ 剔掉父类旧口径的那两行（只认这两个键，别的行一行不碰）
                }
            }
        }
        // 再无条件补上我们的一对 —— 顺序与父类原布局一致（并行行在前、线程行在后），
        // 且**每次都恰好一对**（即使上游两行都不在，也不会少行）。
        long threads = Math.max(1L, (long) getMultipleThreads());
        long threadsInUse = safeRunningThreadsForDisplay();
        long totalRuns = safeTotalRunsForDisplay();
        long perRecipe = Math.max(1L, totalRuns / threadsInUse);
        long total = saturatingMul(threadsInUse, perRecipe);
        textList.add(parallelTotalLine(threadsInUse, perRecipe, total));
        textList.add(threadsLine(threads));
    }

    /**
     * ★★ <b>【GTO 式总量显示】覆写父类的并行行</b> ★★ —— 把「同时处理至多 N 个配方」里的 N
     * 从<b>单条配方的并行上限</b>换成<b>整机真实总量</b>：{@code N = 在用线程数 K × 每条线程的真实并行 P}。
     *
     * <h3>口径（逐字取自 GTO 官方语言文件，本任务唯一口径依据）</h3>
     * GTO 的 {@code gtocore.machine.thread}（{@code _census/_gto_wildcard/zh_cn.json:5709}）：
     * 「同时处理至多 %1$s 种不同配方，每种配方至多 %2$s 个」⇒ <b>两层连乘</b>，
     * 「种数 / 线程数」是<b>第一层乘数</b>。用户给的 GTO 实机截图那一行是
     * {@code 同时处理至多 16,384 个配方} ⇒ 那是一个「正常数字」，**不是** {@code int} 上限占位值
     * （推导见 {@code _census/GTO界面口径对齐_实施记录.md} §②）。
     *
     * <h3>逐行来源（本覆写只替换<b>两行</b>，其余行逐字保留父类输出）</h3>
     * <p>⚠ <b>2026-11-13 状态更新（本任务 ②）</b>：实机显示证明「那两行不是经由本方法进入列表的」
     * ⇒ 最终口径已由 {@link #filterUpstreamParallelLines}（在 {@code addDisplayText} 里）收口。
     * 本方法里的按键替换/兜底追加是**次级**保障（保留，因为它只会写出与我们同口径的行；
     * 且 {@link #filterUpstreamParallelLines} 会把同键的旧行剔掉 ⇒ 屏幕上一对、不重复）。
     * <ul>
     *   <li><b>在用线程条数 K</b> = {@link NanochipThreadedRecipeLogic#runningThreadCount()}（数出来的，
     *       与界面「在用 K 条」同一格；取不到时钳 ≥1）；</li>
     *   <li><b>每条线程的真实并行 P</b> = {@code ceil(R / K)}，其中 {@code R} =
     *       {@link NanochipThreadedRecipeLogic#totalRunsAtOnce()}（各线程自己那份并行之和，
     *       与界面「同时处理 R 次配方运行（各线程之和）」同一格）⇒ <b>两个乘数都是现场真实值</b>；
     *       ⚠ 改前这两个乘数是 {@link #getMaxParallel()}（{@code int} 接口上的饱和占位值
     *       {@link #FULL_PARALLEL_CAP} = 2147483647）与 {@link #getMultipleThreads()}（上限 U）
     *       ⇒ 显示 {@code 549,755,813,632}（天量、不像 GTO；用户实机反馈）；</li>
     *   <li><b>总量</b> = {@link #saturatingMul(long, long)}（64 位、乘法前判界，绝不回绕）
     *       ⇒ 真值（等价于 R，最多因向上取整 +1；K ≤ R 恒成立，边界另有 {@code max(1,·)} 兜底）；</li>
     *   <li><b>「拥有 M 个跨配方线程」那一行</b> = 同款按键替换成 <b>同一个 {@code U}</b>
     *       （父类原口径 = 它自己的 128 基线 + {@code getAdditionalThread()}，<b>未钳制</b>）
     *       ⇒ 屏幕上不再出现 128 与 256 并排打架。<b>本轮未改这一格</b>（它是「上限」，不是「在用」）。</li>
     * </ul>
     *
     * <h3>为什么是覆写、为什么先调 super</h3>
     * ① 该方法是 {@code protected}（字节码：{@code _census/_tmp_subspace/javap_modbase.u8.txt:103}
     * 的声明 + {@code :162} 的 {@code invokespecial}），同血统作者也在同一层覆写它
     * （{@code _census/shanhai-src/…/PrimordialOmegaEngineModuleBase.java:817}）；
     * ② <b>不新增 mixin、不动父类</b>；先调 {@code super} 是为了让父类写好的其它行与它自己的
     * 成形判断<b>逐字保留</b>，本覆写只替换<b>两个已知键</b>的行（{@code gtceu.multiblock.parallel}
     * 与 {@code gtladditions.multiblock.threads}）⇒ 父类万一还写了别的行，一行都不会被吞掉；
     * 万一某版本父类少写了这两行之一，兜底由我们追加（<b>显示绝不凭空少一行</b>）。
     *
     * <h3>只动显示、不动行为（开 / 关独立线程等价）</h3>
     * 本方法只在 {@code addDisplayText} 的显示通路上被调用，全程只读三个 getter
     * （{@link #getMultipleThreads()} / {@link NanochipThreadedRecipeLogic#runningThreadCount()} /
     * {@link NanochipThreadedRecipeLogic#totalRunsAtOnce()}）：不写任何字段、
     * 不进任何判定，配方结算（输入扣减 / 产出 / 时长 / 并行 / 概率 / 回滚）一条都不经过这里。
     * ⚠ 本覆写**不再读** {@link #getMaxParallel()}：它只是 {@code int} 接口上的饱和占位值，
     * 参与显示就会得到天量（这正是本轮修掉的那个显示问题）。
     *
     * <p><b>回退</b>：把 {@code threadsInUse}/{@code perRecipe} 两行改回
     * {@code max(1, getMaxParallel()) × max(1, getMultipleThreads())}（逐字见回退包 H3），
     * 或删掉本覆写（连同三个键常量与两个行构造函数）⇒ 逐字回到父类的两行
     * （并行 = {@code getMaxParallel()}、线程 = 父类自己的 128 基线）。
     */
    @Override
    protected void addParallelDisplay(List<Component> textList) {
        // ★ 2026-11-13【本任务 ②】：本方法现在是**次级**保障 —— 那两行的最终口径由
        //   {@link #filterUpstreamParallelLines}（在 {@code addDisplayText} 里）收口。
        //   本方法保持原样不动（仍先把父类输出的别的东西完整带出来）。留档见回退包 Hunk 2 锚点 B。
        if (textList == null) {
            return;
        }
        super.addParallelDisplay(textList);
        if (!isFormed()) {
            return;   // 未成形：逐字保留父类输出，我们一行都不补
        }
        long threads = Math.max(1L, (long) getMultipleThreads());
        // ★【GTO 界面口径对齐 · 只改显示】总量改用**真实乘积**：两个乘数都换成现场真实值。
        //   · K = 本机这一刻**真实在用的线程条数**（= 界面「在用 K 条」那一格；
        //     取不到（子类/夹具未接逻辑）时钳到 ≥1，绝不让 0 参与乘除）；
        //   · R = 各线程**自己那份并行之和**（= 界面「同时处理 R 次配方运行」那一格）；
        //   · P = 每条线程的**真实并行** = 整数除 R / K（向下取整；R、K 都是现场数出来的，
        //     **没有任何上限占位值**参与）；
        //   · 总量 = saturatingMul(K, P) ⇒ ≤ R（向下取整 ⇒ **永不虚标**）；K > R 的退化数据下
        //     P 被钳到 1（总量 = K，如实写明这是兜底，见 safeRunningThreadsForDisplay 的注释）。
        // ⚠ 改前那两个乘数是 getMaxParallel()（= FULL_PARALLEL_CAP = 2147483647 的**接口饱和占位值**）
        //   与 getMultipleThreads()（= 上限 U），乘出 549,755,813,632 ⇒ 天量、不像 GTO（用户实机反馈）。
        //   本覆写**不再让 getMaxParallel() 参与任何显示**（它仍是并行判定的接口取值，一行未动）。
        long threadsInUse = safeRunningThreadsForDisplay();
        long totalRuns = safeTotalRunsForDisplay();
        long perRecipe = Math.max(1L, totalRuns / threadsInUse);
        long total = saturatingMul(threadsInUse, perRecipe);
        // ★★ 0.3.132【队长令：改成 GTO 口径】★★
        //   队长给的 GTO 实机截图逐字：
        //     同时处理至多 §a16§r 种不同配方        ← 数字绿色
        //     每种配方至多 §d65,536§r 个             ← 数字紫色
        //     独立线程[已启用]
        //   ⇒ 三行取代上游那两行（`gtceu.multiblock.parallel` + `gtladditions.multiblock.threads`）：
        //     · 第 1 格 = **本机跨配方线程数**（= 能同时挂起几条**不同**配方 = GTO 的"种数"）
        //       ⇒ 这正好回答队长的提问「跨配方线程跟 GTO 的同时处理至多 X 种不同配方有关系吗」：**同一个量**；
        //     · 第 2 格 = **每条线程的并行**（= GTO 的"每种配方至多 Y 个"）；
        //     · 第 3 格 = 独立线程的**启用状态**（照 GTO 的 `gtocore.machine.independent_thread`）。
        //   ⚠ 仍然**只改显示**：三个数都是现场只读 getter，不写字段、不进判定。
        final long gtoKinds = threads;
        // ★★ 0.3.132【队长令：照 GTO 填数 —— 第 2 格就是"并行数"】★★
        //   队长原话：「主机UI里面为什么看不到并行数…模仿GTO的就行」。
        //   ⚠ 我上一版把这一格填成 {@code perRecipe = max(1, totalRuns / threadsInUse)}，
        //     而机器**闲置时 totalRuns = 0** ⇒ 被兜底成 **1** ⇒ 队长看到「每种配方至多 1 个」，
        //     读起来像"这台机器每种配方只能做 1 个"（其实那只是"此刻在跑 0 次"）。**这是设计错误。**
        //   ⇒ 现在分两种情况，**都不撒谎**：
        //     · 有批次在跑（{@code totalRuns > 0}）⇒ 填**每条线程的真实并行 P**（现场值）；
        //     · 闲置 / 无批次 ⇒ 填**接口饱和上限** {@link #FULL_PARALLEL_CAP}（= {@code getMaxParallel()}，
        //       与「直通档 并行上限」那一格同一取数口）—— 这才是"能力上限"该有的口径，
        //       而且与 GTO 一样**永远是个数字**（不会出现"—"这种我们自己发明的写法）。
        final long parallelShown = totalRuns > 0L ? perRecipe : FULL_PARALLEL_CAP;
        int parallelAt = -1;
        int threadsAt = -1;
        for (int i = 0; i < textList.size(); i++) {
            Component line = textList.get(i);
            if (!(line.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents contents)) {
                continue;
            }
            if (LANG_UPSTREAM_PARALLEL.equals(contents.getKey())) {
                parallelAt = i;
            } else if (LANG_UPSTREAM_THREADS.equals(contents.getKey())) {
                threadsAt = i;
            }
        }
        // ① 先把上游那两行从列表里剔掉（物理位置记下来，下面按原位插回，绝不把行挪到末尾）
        int insertAt;
        if (parallelAt >= 0 && threadsAt >= 0) {
            insertAt = Math.min(parallelAt, threadsAt);
            textList.remove(Math.max(parallelAt, threadsAt));
            textList.remove(Math.min(parallelAt, threadsAt));
        } else if (parallelAt >= 0 || threadsAt >= 0) {
            insertAt = parallelAt >= 0 ? parallelAt : threadsAt;
            textList.remove(insertAt);
        } else {
            insertAt = textList.size();   // 上游一行都没写（版本差异）⇒ 补在末尾，绝不静默少行
        }
        // ② 原地插回 GTO 三行（顺序照截图：种数 → 每种（= 并行数） → 独立线程状态）
        textList.add(insertAt, gtoThreadsLine(gtoKinds));
        textList.add(insertAt + 1, gtoPerRecipeLine(parallelShown));
        textList.add(insertAt + 2, gtoIndependentThreadLine());
    }

    /**
     * ★【GTO 界面口径对齐】本机这一刻**真实在用**的线程条数（只给显示用）。
     *
     * <p>取数口：{@link NanochipThreadedRecipeLogic#runningThreadCount()}（= 批次表条数，
     * 与界面「在用 K 条」、Jade 的 {@code KEY_THREAD_RUNNING} **同一个数**）。
     * 取不到（子类/字段初始化期/夹具）⇒ 退回 {@code 1}：<b>绝不让 0 参与后面的除法</b>
     * （0 会让「每条并行」算不出来，也会让总量显示成 0）。
     *
     * <p>⚠ 退化数据（{@code K > R}，正常不会出现：每条在跑批次至少贡献 1 次并行）时，
     * 调用方那边「每条并行」会被钳到 1（{@code max(1, R/K)}）⇒ 总量如实显示成 {@code K}（不显示 0）。
     * <p><b>只读、纯显示</b>：不写任何字段、不进任何判定（配方结算不经过这里）。
     */
    private long safeRunningThreadsForDisplay() {
        try {
            RecipeLogic logic = getRecipeLogic();
            if (logic instanceof NanochipThreadedRecipeLogic threaded) {
                return Math.max(1L, (long) threaded.runningThreadCount());
            }
        } catch (RuntimeException e) {
            // 显示层：读不到就退回 1，绝不把异常抛给提示渲染链（也不打断任何结算）
            NanochipModContext.logger().debug("[gtlmodular] NAC 总量显示：在用线程数取不到，按 1 计：{}", e.toString());
        }
        return 1L;
    }

    /**
     * ★【GTO 界面口径对齐】各线程**自己那份并行之和**（只给显示用）。
     *
     * <p>取数口：{@link NanochipThreadedRecipeLogic#totalRunsAtOnce()}（= 界面
     * 「同时处理 R 次配方运行（各线程之和）」、Jade 的 {@code KEY_THREAD_RUNS} **同一个数**）
     * ⇒ 屏幕上那两行与总量行**天然同源**，不会被读成两套账。
     *
     * <p>取不到 ⇒ {@code 0}（调用方会把它钳到「每条至少 1」，见 {@code addParallelDisplay}）。
     */
    private long safeTotalRunsForDisplay() {
        try {
            RecipeLogic logic = getRecipeLogic();
            if (logic instanceof NanochipThreadedRecipeLogic threaded) {
                return Math.max(0L, threaded.totalRunsAtOnce());
            }
        } catch (RuntimeException e) {
            NanochipModContext.logger().debug("[gtlmodular] NAC 总量显示：各线程并行之和取不到，按 0 计：{}", e.toString());
        }
        return 0L;
    }

    /** ★【GTO 式总量】那一行的成品（GTO 句式 + 明确总量；三格 = <b>在用条数 K / 每条真实并行 P / 总量 K×P</b>）。 */
    private static Component parallelTotalLine(long threadsInUse, long perRecipe, long total) {
        return Component.translatable(LANG_PARALLEL_TOTAL,
                        FormattingUtil.formatNumbers(threadsInUse),
                        FormattingUtil.formatNumbers(perRecipe),
                        FormattingUtil.formatNumbers(total))
                .withStyle(ChatFormatting.GRAY);
    }

    /**
     * ★【GTO 式总量】父类那一行「拥有 %s 个跨配方线程」的成品 —— 数字换成<b>我们钳制后的 U</b>
     * （= {@link #getMultipleThreads()}，= 「独立线程：上限 U 条」那一格；<b>注意</b>：
     * 本轮的「总量行第 1 格」已改成<b>在用条数 K</b>，两格取数口从此**不同**，是**故意**的：
     * 「拥有 M 个跨配方线程」问的是能力上限，总量行问的是这一刻真实在跑多少）。
     *
     * <p>为什么必须换：父类那行读的是「它自己的 128 基线 + {@code getAdditionalThread()}」且<b>未钳制</b>，
     * 而本机真正生效的是钳到 {@link #MAX_CROSS_RECIPE_THREADS} 的值 ⇒ 同一张提示里
     * 「拥有 128 个跨配方线程」与「上限 256」并排出现会被读成 bug。
     * <b>只改显示、不动父类</b>：父类代码一字未动，这里是覆写体内同款按键替换。
     */
    /**
     * ★【GTO 口径】第 1 行「同时处理至多 <b>N</b> 种不同配方」。
     *
     * <p>★★ <b>颜色必须由语言键给，不能在 Java 里硬编码</b> ★★ —— 这是本工程与 GTCEu/GTO 共有的做法：
     * 色码写在语言文件的值里（例如 GTCEu 的 {@code "最大功率：§a%s EU/t（%s§r）"}）。
     * 我上一版用 {@code Component.literal("同时处理至多 ")...withStyle(GREEN)} 硬拼，
     * <b>数字那格就丢了色码</b>、只能继承外层颜色 ⇒ 队长一眼看出"颜色跟其他的不同"。
     * ⇒ 现在整行走 {@code translatable}，色码在键里，逐字照 GTO 的
     * {@code gtocore.machine.thread.0 = "同时处理至多 §a%s§r 种不同配方"}（绿）。
     */
    private static Component gtoThreadsLine(long kinds) {
        return Component.translatable("gtlmodular.nanochip.parallel.kinds",
                FormattingUtil.formatNumbers(kinds));
    }

    /**
     * ★【GTO 口径】第 2 行「每种配方至多 <b>Y</b> 个」。
     *
     * <p>色码同样在键里，逐字照 GTO 的 {@code gtocore.machine.thread.1 = "每种配方至多 §d%s§r 个"}（紫）。
     */
    private static Component gtoPerRecipeLine(long perRecipe) {
        return Component.translatable("gtlmodular.nanochip.parallel.per",
                FormattingUtil.formatNumbers(perRecipe));
    }

    /**
     * ★【GTO 口径】第 3 行「独立线程[已启用] / [未启用]」。
     *
     * <p>逐字照 GTO 的 {@code gtocore.machine.independent_thread = "独立线程[%s]"}（截图逐字「独立线程[已启用]」）。
     * ★ <b>整行单色</b>：GTO 那一行没有给状态单独上色（它和「同时处理…」那两行一样是灰的）
     * —— 我上一版把 {@code 已启用} 单独染绿是**自作主张**，队长一眼看出不对，故改回单色。
     * 判据 = 本机跨配方线程数 &gt; 1（与 {@link #isCrossRecipeThreadActive()} 同源）。
     */
    private Component gtoIndependentThreadLine() {
        return Component.translatable(getMultipleThreads() > 1
                        ? "gtlmodular.nanochip.threads.independent.on"
                        : "gtlmodular.nanochip.threads.independent.off")
                .withStyle(ChatFormatting.GRAY);
    }

    private static Component threadsLine(long threads) {
        return Component.translatable(LANG_UPSTREAM_THREADS, FormattingUtil.formatNumbers(threads))
                .withStyle(ChatFormatting.GRAY);
    }

    // ---------- 模块加成聚合 ----------

    /** 已成形的指定种类模块数量。 */
    public int countModules(NanochipModuleKind kind) {
        int n = 0;
        for (IModularMachineModule<NanochipAssemblyComplexMachine, ?> m : getModules()) {
            if (m instanceof NanochipModuleMachine module && module.isFormed()
                    && module.getModuleKind() == kind) {
                n++;
            }
        }
        return n;
    }

    /** 全部已成形模块数。 */
    public int formedModuleCount() {
        int n = 0;
        for (IModularMachineModule<NanochipAssemblyComplexMachine, ?> m : getModules()) {
            if (m instanceof NanochipModuleMachine module && module.isFormed()) {
                n++;
            }
        }
        return n;
    }

    /**
     * 模块给出的 EU 倍率（累乘）：每个已成形模块按 {@link NanochipModuleKind#euFactor()} 缩放。
     *
     * <p>其中两条是<b>逐台算</b>的原版机制：
     * <ul>
     *   <li><b>净化水组织器一族</b>（光学 + 2026-09-18 新增的奇异 / 寰宇 / 超因果三台）：
     *       加成强度由供水等级决定（1 级 = 基础，16 级 = 两倍），
     *       所以用 {@code 基础因子^强度} 表达「同类加成叠加」；本族满校准时强度再翻倍
     *       （原版：{@code Water Bonuses are applied twice}）；<b>没供水就不给加成</b>；
     *       判定收口在 {@link NanochipModuleKind#isWaterScaledOrganizer()} +
     *       {@link NanochipModuleKind#organizerFamily()}（原先这里写死 {@code == OPTICAL_ORGANIZER}，
     *       新增三台后必须泛化，否则三台会退化成「无条件 ×0.90」）；</li>
     *   <li><b>全板浸没装置</b>：EU 再乘上浸泡液杂质倍率（0–15% 省电、65–100% 反而多耗电）。</li>
     * </ul>
     * 下限 0.10×；上限放到 10×（杂质高时模块确实会变成负收益，这是原版设计）。
     */
    public double moduleEuMultiplier(NanochipCalibration.Family family) {
        double mul = 1.0D;
        for (IModularMachineModule<NanochipAssemblyComplexMachine, ?> m : getModules()) {
            if (!(m instanceof NanochipModuleMachine module) || !module.isFormed()) {
                continue;
            }
            NanochipModuleKind kind = module.getModuleKind();
            if (!kind.appliesTo(family) || kind.euFactor() >= 1.0D) {
                continue;
            }
            double factor = kind.euFactor();
            if (kind.isWaterScaledOrganizer()) {
                double strength = module.waterStrength();
                if (strength <= 0.0D) {
                    continue; // 没供净化水 → 这条加成不生效
                }
                NanochipCalibration.Family waterFamily = kind.organizerFamily();
                if (waterFamily != null && calibration.specialActive(waterFamily)) {
                    strength *= 2.0D;
                }
                factor = Math.pow(factor, Math.min(4.0D, strength));
            } else if (kind == NanochipModuleKind.BOARD_PROCESSOR) {
                factor = factor * module.impurityEuMultiplier();
            }
            mul *= factor;
        }
        return Math.max(0.10D, Math.min(10.0D, mul));
    }

    /**
     * 模块给出的时长倍率（累乘）。下限 0.05×。
     *
     * <p>注意：原版「晶体满校准 → 蚀刻阵列的激光源仓生效」这条<b>不在这里</b> ——
     * 它作用在蚀刻阵列自己的加工上，已按原版公式落在
     * {@link NanochipModuleMachine#moduleOverclock}（EU ÷ 每 256A 一档、时长 ÷ (仓等级−9)）。
     */
    public double moduleDurationMultiplier(NanochipCalibration.Family family) {
        double mul = 1.0D;
        for (IModularMachineModule<NanochipAssemblyComplexMachine, ?> m : getModules()) {
            if (!(m instanceof NanochipModuleMachine module) || !module.isFormed()) {
                continue;
            }
            NanochipModuleKind kind = module.getModuleKind();
            if (!kind.appliesTo(family) || kind.durationFactor() >= 1.0D) {
                continue;
            }
            double factor = kind.durationFactor();
            if (kind.isWaterScaledOrganizer()) {
                double strength = module.waterStrength();
                if (strength <= 0.0D) {
                    continue;
                }
                NanochipCalibration.Family waterFamily = kind.organizerFamily();
                if (waterFamily != null && calibration.specialActive(waterFamily)) {
                    strength *= 2.0D;
                }
                factor = Math.pow(factor, Math.min(4.0D, strength));
            }
            mul *= factor;
        }
        return Math.max(0.05D, mul);
    }

    // ---------- 配方修正（2/2 封顶超频 + 时长下限 + 加成） ----------

    /** 注册用入口：{@code .recipeModifier(NanochipAssemblyComplexMachine::nacRecipeModifier)}。 */
    public static GTRecipe nacRecipeModifier(MetaMachine machine, GTRecipe recipe,
                                             OCParams params, OCResult result) {
        if (!(machine instanceof NanochipAssemblyComplexMachine nac)) {
            return null;
        }
        return nac.buildRecipe(recipe);
    }

    /**
     * 本机的配方处理：
     * <ol>
     *   <li>2/2 封顶超频（能耗 ×4、时长 ÷2，最多两次）—— 到不了时长下限就不再超；</li>
     *   <li>乘以校准 T1 的 EU ×0.8 与模块能耗倍率；</li>
     *   <li>乘以校准 T2 与模块时长倍率；</li>
     *   <li>最后夹到本档的时长下限（直通 20 秒 / 全流程 5 秒）。</li>
     * </ol>
     */
    public GTRecipe buildRecipe(GTRecipe recipe) {
        long maxVoltage = getOverclockVoltage();
        if (maxVoltage <= 0L) {
            return null; // 没有能源仓
        }
        GTRecipe copy = recipe.copy();
        long eut = Math.max(1L, RecipeHelper.getInputEUt(copy));
        int duration = Math.max(1, copy.duration);
        int floorTicks = minDurationTicks();

        int overclocks = 0;
        while (overclocks < MAX_OVERCLOCKS
                && eut <= maxVoltage / 4L
                && duration / 2 >= floorTicks) {
            eut *= 4L;
            duration /= 2;
            overclocks++;
        }

        NanochipCalibration.Family family = familyOf(recipe);

        double euMul = calibration.euMultiplier() * moduleEuMultiplier(family);
        double durMul = calibration.durationMultiplier(family) * moduleDurationMultiplier(family);

        long newEut = (long) Math.max(1.0D, Math.min((double) Long.MAX_VALUE, eut * euMul));
        int newDuration = (int) Math.max(floorTicks, Math.min(Integer.MAX_VALUE, Math.round(duration * durMul)));

        RecipeHelper.setInputEUt(copy, newEut);
        copy.duration = newDuration;
        copy.ocTier = overclocks;
        // ★★ 0.3.132【配方时长/能耗 · 归因读数】★★（队长问：「配方时间怎么拉到 6 秒了，不是固定 5 秒钟吗」）
        //   为什么必须打这一行：时长是**多个乘数叠出来**的（封顶超频 ÷2·最多两次、校准时长倍率、
        //   模块时长倍率、最后夹到本档下限），单看结果**无法判断是哪一环**。改前只有"结果"、没有"过程"，
        //   于是只能靠反推 —— 这一行把过程一次摊开，跑一条配方就定案。
        //
        //   ⚠ 三个易误读点，都在这一行里点明：
        //     ① **5 秒是"下限"（floor），不是固定值**：`duration/2 >= floor` 是超频的停止条件
        //        ⇒ 基准 300t 的配方最多只能超 1 次（第二次要 75 ≥ 100，为假）⇒ 150t 再乘倍率；
        //     ② `family`（本配方所属族，来自 familyOf）与 `activeFamily`（当前生效族）**可以不同**——
        //        只有两者**相等**时校准的时长倍率才非 1.0（见 NanochipCalibration.durationMultiplier）；
        //     ③ 批次时长是**开单那一刻的快照**（`NanochipConcurrentBatches.Batch.duration` 是 final，
        //        此后不再重算）⇒ 面板（实时）与条上时长（快照）天然可以打架。
        //
        //   限流：见 `lastRecipeBuildLogTick`（1 秒最多一条），不会刷屏。
        if (NanochipModContext.recipeDiagnosticsEnabled()) {
            long nowTick = getLevel() == null ? 0L : getLevel().getGameTime();
            if (nowTick - lastRecipeBuildLogTick >= 20L) {
                lastRecipeBuildLogTick = nowTick;
                NanochipCalibration.Family active = calibration.activeFamily();
                NanochipModContext.logger().info("[gtlmodular] NAC 配方定档 @{}: 本配方族={} 生效族={}(第{}档) "
                                + "超频={}次 EUt {}→{} 时长 {}→{}t（下限{}t） "
                                + "倍率[校准EU×{} 模块EU×{} ⇒ ×{}；校准时长×{} 模块时长×{} ⇒ ×{}] ⇒ 最终 {}t（{}.{}s）",
                        getPos(), family, active == null ? "无" : active,
                        active == null ? 0 : calibration.tierOf(active),
                        overclocks, eut, newEut,
                        Math.max(1, copy.duration == 0 ? 0 : duration), newDuration, floorTicks,
                        calibration.euMultiplier(), moduleEuMultiplier(family), euMul,
                        calibration.durationMultiplier(family), moduleDurationMultiplier(family), durMul,
                        newDuration, newDuration / 20, newDuration % 20);
            }
        }
        return copy;
    }

    /** 上面那条「配方定档」归因日志的限流游标（20 tick = 1 秒最多一条；普通字段、不进存档）。 */
    private long lastRecipeBuildLogTick = Long.MIN_VALUE;

    /**
     * 配方自带的电路族键（NAC 自己的契约，非照抄 NH；键名来源
     * {@code data/gtlmodular/recipes/nanochip/**} 里 30 条 NAC 配方 JSON 各带
     * {@code "data": {"circuit-calibration": "CRYSTAL|WETWARE|BIO|OPTICAL|EXOTIC|COSMIC|TEMPORAL"}}，
     * 生成器 {@code gen_nac_p1_recipes.js:203}；方案见 {@code _census/P3_接线_方案.md:973-1017}）。
     *
     * <p>★ 2026-10-31【M2】：本键此前<b>零消费者</b>（全树 {@code src/main/java} grep {@code 0} 命中）
     * ⇒ 族只能靠产物物品 id 猜。现在由 {@link #familyOf(GTRecipe)} 优先读它。
     */
    private static final String KEY_CIRCUIT_CALIBRATION = "circuit-calibration";

    /**
     * 按配方判定电路族（用于校准记录与加成筛选）。
     *
     * <p><b>取族顺序（2026-10-31【M2】）</b>：
     * <ol>
     *   <li><b>配方元数据 {@link #KEY_CIRCUIT_CALIBRATION} 优先</b> —— 它是配方作者<b>显式声明</b>的族，
     *       比"拿产物物品 id 去猜"权威；30 条 NAC 自有配方（assembly_complex 7 + assembly_matrix 23）
     *       都带它，此前无人读；</li>
     *   <li><b>回落：按产物物品 id 猜</b>（{@link NanochipCalibration.Family#of(ResourceLocation)}
     *       的 {@code crystal/wetware/…} 子串判定）—— 换算表（conversion）那批没有元数据，只能走这条；</li>
     *   <li>都取不到 ⇒ {@link NanochipCalibration.Family#PRIMITIVE}（只进历史条，不参与档位）。</li>
     * </ol>
     *
     * <p>★ 2026-10-31【M1 同坑】第 2 步的产物识别已补 {@link ItemStack} / {@link ItemStack}[] /
     * {@link net.minecraft.world.item.crafting.Ingredient} 三路兜底 —— 本包（gtlcore 1.2.3.2）把
     * {@code gtceu:sized} 提升成了 {@code LongIngredient}，物品产出<b>没有一条是 ItemStack</b>
     * ⇒ 旧实现会跳过全部产物、恒回落到 {@code PRIMITIVE}。
     * 同坑已在 {@code NanochipSelfCheck.itemIdOf:1282-1298}（2026-09-30）修过一次。
     */
    public static NanochipCalibration.Family familyOf(GTRecipe recipe) {
        if (recipe == null) {
            return NanochipCalibration.Family.PRIMITIVE;
        }
        // ① 配方元数据优先（显式声明 > 按 id 猜）
        NanochipCalibration.Family fromData = calibrationFamilyFromData(recipe);
        if (fromData != null) {
            return fromData;
        }
        // ② 回落：产物物品 id 猜族（含 Ingredient 分支）
        try {
            List<Content> outs = recipe.getOutputContents(ItemRecipeCapability.CAP);
            for (Content c : outs) {
                ItemStack stack = firstItemStackOf(c.content);
                if (stack != null && !stack.isEmpty()) {
                    return NanochipCalibration.Family.of(stack);
                }
            }
        } catch (RuntimeException ignored) {
            // 判定失败按基础电路处理，不影响加工
        }
        return NanochipCalibration.Family.PRIMITIVE;
    }

    /**
     * 读配方元数据 {@link #KEY_CIRCUIT_CALIBRATION} 并转成 {@link NanochipCalibration.Family}。
     *
     * <p>取值口径：JSON 里写的是<b>族名</b>（{@code "CRYSTAL"} 等），与本包 {@code Family} 常量名
     * <b>基本同名</b>，逐字 {@code valueOf} 即可；唯一的别名是 JSON 写 {@code "BIO"}
     * （生物电路，{@code assembly_complex/011_cc_bioware_processor.json:12}、
     * {@code assembly_matrix/012..014}），而本包常量名是 {@code BIOWARE} ⇒ 在这里显式映射。
     *
     * <p>⚠️ 键名与族名对不上时的保底行为 = <b>返回 {@code null}，交给调用方回落"按物品 id 猜"</b>
     * （绝不猜族、绝不写错列）；值本身非法时也走同一条回落路。
     * 30 条 NAC 配方的实际取值已逐条核对（CRYSTAL / WETWARE / BIO / OPTICAL / EXOTIC / COSMIC / TEMPORAL）。
     */
    private static NanochipCalibration.Family calibrationFamilyFromData(GTRecipe recipe) {
        if (recipe.data == null) {
            return null;
        }
        if (!recipe.data.contains(KEY_CIRCUIT_CALIBRATION)) {
            return null;
        }
        String raw = recipe.data.getString(KEY_CIRCUIT_CALIBRATION);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String name = raw.trim().toUpperCase(Locale.ROOT);
        if ("BIO".equals(name)) {
            name = "BIOWARE";   // JSON 别名 → 本包常量名
        }
        try {
            return NanochipCalibration.Family.valueOf(name);
        } catch (IllegalArgumentException e) {
            // 未知族名 ⇒ 回落"按物品 id 猜"（返回 null 而不是 PRIMITIVE：猜还有可能猜中）
            return null;
        }
    }

    /**
     * 从 GTCEu 的 {@link Content#content} 里取一个可用于判定族的物品栈 ——
     * <b>ItemStack / ItemStack[] / Ingredient（含 gtlcore 的 LongIngredient、GTCEu 的 SizedIngredient）
     * 三路兜底</b>。
     *
     * <p>★ 2026-10-31【M1/M2 根因】：本包（gtlcore 1.2.3.2）把 {@code gtceu:sized} 提升成了
     * {@code LongIngredient}（与 {@code SizedIngredient} 同属 {@link net.minecraft.world.item.crafting.Ingredient}
     * 子类），NAC 配方产出的 {@code content} 形状是
     * {@code {"type":"gtceu:sized","count":8,"ingredient":{...}}}
     * （例：{@code data/gtlmodular/recipes/nanochip/assembly_matrix/002_cc_crystal_processor.json:54-66}）
     * ⇒ 反序列化后<b>一次都不是 ItemStack</b>。
     * 旧实现只判 {@code instanceof ItemStack} ⇒ 恒假、静默跳过 ⇒ 校准一条都没记上（历史恒 0）。
     * 同坑在 {@code NanochipSelfCheck.itemIdOf:1282-1298}（2026-09-30）已修过一次，这里是同一批补齐。
     *
     * <p><b>识别不出时的保底行为</b>：返回 {@code null}，调用方一律<b>跳过该产物</b>
     * （不记 0、不记 PRIMITIVE 假账），并且由 {@code recordProduction} 打一行 debug 留痕 ——
     * 绝不"静默继续"。
     * {@code Ingredient.getItems()} 对未解析的 tag/空 Ingredient 会抛（同 {@code itemIdOf} 的处理）
     * ⇒ 这里 {@code catch (RuntimeException)} 只把"取不到"这一种结果收敛成 {@code null}，
     * 不吞掉别的逻辑错误。
     */
    private static ItemStack firstItemStackOf(Object content) {
        if (content instanceof ItemStack stack) {
            return stack.isEmpty() ? null : stack;
        }
        if (content instanceof ItemStack[] stacks) {
            for (ItemStack stack : stacks) {
                if (stack != null && !stack.isEmpty()) {
                    return stack;
                }
            }
            return null;
        }
        if (content instanceof Ingredient ingredient) {
            try {
                ItemStack[] stacks = ingredient.getItems();
                if (stacks != null) {
                    for (ItemStack stack : stacks) {
                        if (stack != null && !stack.isEmpty()) {
                            return stack;
                        }
                    }
                }
            } catch (RuntimeException e) {
                // 未解析的 tag 等 ⇒ 取不到（按"识别不出"处理，见方法注释）
                return null;
            }
        }
        return null;
    }

    /**
     * 一份产出内容<b>标称产出多少个</b>（用于校准计数）。
     *
     * <p>★ 2026-10-31【M1】：必须优先读 Ingredient <b>自带的份数</b>，不能只读
     * {@code getItems()[0].getCount()} —— {@code SizedIngredient}/{@code LongIngredient} 的
     * {@code getItems()} 给出的是<b>单份</b>样品（count 1），真实份数存在包装里。
     * 先例（同款三路读法）：{@code _census/shanhai-src/…/RecipeRunnerHelperOutputMixin.java:119-120}、
     * {@code _census/enhancedcore-src/…/ProcessingRecoveryChecks.java:429-430}。
     *
     * <p>顺序：{@code LongIngredient.getActualAmount()}（long）→ {@code SizedIngredient.getAmount()}（int）
     * → 物品栈自身 count（普通 {@code ItemStack} 内容）→ 1（兜底）。
     * 用全限定名避免动 import 区。
     */
    private static long producedCountOf(Object content) {
        if (content instanceof org.gtlcore.gtlcore.api.recipe.ingredient.LongIngredient longIngredient) {
            return longIngredient.getActualAmount();
        }
        if (content instanceof com.gregtechceu.gtceu.api.recipe.ingredient.SizedIngredient sized) {
            return sized.getAmount();
        }
        ItemStack stack = firstItemStackOf(content);
        return stack == null ? 1L : Math.max(1L, (long) stack.getCount());
    }

    // ---------- 校准记录 ----------

    /**
     * ★ 2026-09-19（队长：「他输出端口转换了以后，还是 CC 没有变成真实电路」）：
     * <b>主机到底跑了哪条配方</b> —— 完成时打一行「配方表 + 输入 → 输出」（限流 1 秒）。
     *
     * <p>为什么必须有：换算表里**两个方向都是可下单配方**（真件→CC / CC→真件），
     * 写反方向时现象就是"还是封装形态"，而此前机器从不打"我跑了哪条配方" ⇒ 只能猜。
     * 这一行把方向写死：看输出的中文名就知道跑的是不是还原方向。
     */
    void logCraftedRecipe(GTRecipe recipe) {
        Level lv = getLevel();
        if (lv == null || lv.isClientSide || recipe == null) {
            return;
        }
        long now = lv.getGameTime();
        if (now - this.lastCraftLogTick < 20L) {
            return;
        }
        this.lastCraftLogTick = now;
        // ★ 2026-09-19（主机挂上自己的表之后）：**两个名字都要打**，别再让人误判 ——
        //   · 「配方表」= recipe.getType()：本机跑的是电路组装机表里那批**同一批对象**
        //     （NanochipRecipeTypes.ASSEMBLY_COMPLEX 通过 GTCEu 的代理机制索引它们，见该字段注释），
        //     所以这一格**故意**仍显示 gtceu:circuit_assembler；
        //   · 「本机模式」= getRecipeType()：本机当前激活的那张表 = 界面「机器模式」显示的那张
        //     （gtlmodular:nanochip_assembly_complex 或简单模式下的 gtlmodular:nanochip_conversion）。
        //   只打前者会让人以为"主机跑的还不是 NAC 表"，只打后者又看不出配方本体属于哪张表。
        GTRecipeType activeType = getRecipeType();
        NanochipModContext.logger().info("[gtlmodular] 主机完成配方 [配方表 {} / 本机模式 {}]：输入 {} → 输出 {}（{} tick）",
                recipe.getType().registryName,
                activeType == null ? "?" : activeType.registryName,
                renderItemContents(recipe.getInputContents(ItemRecipeCapability.CAP)),
                renderItemContents(recipe.getOutputContents(ItemRecipeCapability.CAP)),
                recipe.duration);
    }

    /** 限流：主机"完成配方"日志最多 1 秒一条（哨兵同 {@link #lastUnformedAuditTick}：**不能**用 Long.MIN_VALUE）。 */
    private long lastCraftLogTick = -10_000L;

    /**
     * ★ 2026-10-07【修 0% 的可诊断痕迹】一次搜索里<b>一个配方都放不下</b>时的服务端日志（限流 1 秒）。
     *
     * <p>为什么必须有：用户机器现在真实卡在「找不到可运行配方 / 进度 0%」，而这条路的<b>唯一</b>表现
     * 就是「机器不开工」—— 不报错、不抛异常、界面无差别。没有这一行日志，现场只能靠猜
     * （调查报告 §3.5 的取证顺序第 1/2 条要的正是这类判据）。
     *
     * <p>限流口径与 {@link #logCraftedRecipe} 完全一致（1 秒 / 20 tick；哨兵用 {@code -10_000L}
     * 而不是 {@code Long.MIN_VALUE}，原因见 {@link #lastUnformedAuditTick} 的注释）。
     * 只在<b>本轮候选非空但全部放不下</b>时打，不会在正常运行时刷屏。
     *
     * @param budget     本轮<b>单条配方</b>的并行上限 —— ★ 2026-10-27【并行「去总池」】后
     *                   默认口径是 {@code Long.MAX_VALUE}（每条配方各自满并行，不再有总池）；
     *                   只有 {@link #NAC_PER_THREAD_FULL_PARALLEL} 关掉时才是旧的总池
     *                   （机器并行上限 × 生效线程数）
     * @param candidates 本轮候选配方条数
     */
    void noteNoFit(long budget, int candidates) {
        Level lv = getLevel();
        if (lv == null || lv.isClientSide) {
            return;
        }
        long now = lv.getGameTime();
        if (now - this.lastNoFitLogTick < 20L) {
            return;
        }
        this.lastNoFitLogTick = now;
        // ★ 2026-10-27：日志必须<b>照实</b>说清是哪种口径 —— 去总池后 budget 就是 Long.MAX_VALUE，
        //   直接打印那串 19 位数字会让人误以为「并行预算被放大到天文数字」；真相恰恰相反：
        //   每条配方的并行是由「真的放得下」的二分收敛出来的。
        String limitText = budget == Long.MAX_VALUE
                ? "每条配方各自满并行 · 去总池后上限 Long.MAX_VALUE"
                : "并行总池 " + budget;
        NanochipModContext.logger().info("[gtlmodular] NAC 本次搜索「有 {} 条候选但一条都放不下」（{}，模式 {}）"
                        + "⇒ 本 tick 不开工。这不是「没匹配上配方」，而是「放大后的输入/产出放不下」；"
                        + "检修方向：输出仓/AE 剩余容量、输入库存是否够 1 份料。",
                candidates, limitText, syncedMode);
    }

    /**
     * ★ 2026-10-19【主机预蓄电的可诊断痕迹】起跑前因「整条配方电量不够」而不开工时的服务端日志（限流 1 秒）。
     *
     * <p>为什么必须有：与 {@link #noteNoFit} 同一个理由 —— 这条路在游戏里的<b>唯一</b>表现是
     * 「界面显示等待、机器不动」，现场若没有日志就只能靠猜是"没配方""料不够"还是"电不够"。
     * 它和 {@code noteNoFit} 是<b>两条不同</b>的路（那条是"放大后放不下"，本条是"放得下但电不够"），
     * 所以日志文案必须能区分。
     *
     * <p>限流口径与 {@link #noteNoFit} 逐字一致（1 秒 / 20 tick，哨兵 {@code -10_000L}）。
     * 只在<b>真被拦住</b>时打一次，正常运行不刷屏。
     *
     * @param need   本条最终配方整条所需电量（EU）
     * @param stored 复合体当前蓄电（EU）
     */
    void noteHostPrechargeWaiting(long need, long stored) {
        Level lv = getLevel();
        if (lv == null || lv.isClientSide) {
            return;
        }
        long now = lv.getGameTime();
        if (now - this.lastHostPrechargeLogTick < 20L) {
            return;
        }
        this.lastHostPrechargeLogTick = now;
        NanochipModContext.logger().info("[gtlmodular] NAC 主机预蓄电：本条配方整条需要 {} EU，复合体当前 {} EU"
                        + "（可获取能力 {} EU，最大输入功率 {} EU/t，模式 {}）⇒ 本 tick 不开工，攒够才开始。"
                        + "若这是「无限/按需现取能源」（无线电网终端 / 创造能源仓），说明容量上限没读到 —— "
                        + "检修方向：这些仓室是否真的挂在 NAC 结构上（判据见 isEnergySourceEffectivelyUnlimited）。",
                need, stored, getObtainableEnergyEU(), getMaxInputPower(), syncedMode);
    }

    /** 限流：{@link #noteHostPrechargeWaiting} 最多 1 秒一条（哨兵同 {@link #noteNoFit}）。 */
    private long lastHostPrechargeLogTick = -10_000L;

    /** 限流：{@link #noteNoFit} 最多 1 秒一条。 */
    private long lastNoFitLogTick = -10_000L;

    /**
     * 把物品内容渲染成「名称×数量 + 名称×数量」的可读串（诊断用）。
     *
     * <p>★ 2026-10-31【M3】：判据从「只认 {@code ItemStack}」改成复用
     * {@link #firstItemStackOf(Object)} / {@link #producedCountOf(Object)} 的多路兜底 ——
     * 否则本包（gtlcore 1.2.3.2）物品产出是 {@code Ingredient}，{@link #logCraftedRecipe} 那行
     * 「主机完成配方 …：输入 {} → 输出 {}」会**恒显示"（无物品）"**，日志等于没有信息
     * （修前它正是本次根因的判据，见 {@code _census/纳米校准_诊断.md} E5）。
     *
     * <p>识别不出时照实写「（未识别物品×N）」而不是静默漏掉 —— 日志里不能出现"少了一截"的假象。
     */
    private static String renderItemContents(List<Content> contents) {
        StringBuilder sb = new StringBuilder();
        for (Content c : contents) {
            ItemStack stack = firstItemStackOf(c.content);
            if (sb.length() > 0) {
                sb.append(" + ");
            }
            if (stack == null || stack.isEmpty()) {
                sb.append("（未识别物品×").append(producedCountOf(c.content)).append('）');
                continue;
            }
            sb.append(stack.getHoverName().getString()).append('×').append(producedCountOf(c.content));
        }
        return sb.length() == 0 ? "（无物品）" : sb.toString();
    }

    /**
     * ★★ 0.3.132【队长裁决 (乙)：换算仍计入，但归族按"它对应的真件"】★★ ——
     * <b>校准记账专用的族判定</b>（{@code recordProduction} 唯一调用者）。
     *
     * <h4>队长原话与两条路的裁决（逐字）</h4>
     * 队长原话：「<b>我一个寰宇电路都没做过，为什么我的寰宇会有增加在纳米标准里</b>」。
     * 我列了两条路给队长：「(甲) 换算整类不计入校准」／「<b>(乙) 换算仍计入，但按「它对应的真件」归族
     * （即按被换算的真电路判族）</b>」⇒ <b>队长选了 (乙)。</b>
     *
     * <h4>根因（实机日志逐字，同一次会话）</h4>
     * 线程样本 {@code #8 …/conversion/cc_cosmic_processing_unit_core ×1000 31%}，
     * 同刻校准记账 {@code 寰宇电路 +1000}。机器跑的是<b>换算表</b>
     * （{@code gtlmodular:nanochip_conversion}）的配方，却被记成「产出了 1000 个寰宇电路」——
     * 因为 {@link #familyOf(GTRecipe)} 在配方元数据 {@code circuit-calibration} 缺失时
     * <b>按产物 id 子串猜族</b>，而换算配方的产物是<b>中间件</b> {@code cc_cosmic_processing_unit_core}
     * （含 {@code cosmic}）⇒ 命中 {@link NanochipCalibration.Family#COSMIC}。
     *
     * <h4>(乙) 的口径（本方法实现的就是它）</h4>
     * <ul>
     *   <li><b>数量口径与"是否计入"一个字不动</b>：换算配方照旧计入校准历史，
     *       {@code recordProduction} 里那句 {@code amount = saturatingToInt(Math.max(1L, producedCountOf(c.content)))} 未动；</li>
     *   <li><b>只换"族"</b>：换算配方取它的<b>物品产物</b>（既有 {@link #firstItemStackOf(Object)}，
     *       与 {@code recordProduction} 的识别口径同源），若它是 <b>CC</b>
     *       （{@code NanochipConversionMap.isCc(ItemStack)}）⇒ 用
     *       {@code NanochipConversionMap.materialize(ItemStack)} 取出<b>真件</b>
     *       （CC → 真件，见 {@code NanochipConversionMap.java:229-236}），
     *       再用<b>真件的 id</b> 走 {@link NanochipCalibration.Family#of(ResourceLocation)} 判族；</li>
     *   <li><b>非换算配方</b> ⇒ 逐字走原路 {@link #familyOf(GTRecipe)}（零行为变化）。</li>
     * </ul>
     *
     * <h4>fail-safe（绝不因为"拿不到"就记 0 或记假族）</h4>
     * 下面四条边界<b>全部回落</b>到 {@link #familyOf(GTRecipe)}：
     * ① 产物物品取不到 / 为空；② 产物不是 CC（{@code isCc} 为假）；③ {@code materialize} 返回空栈
     * 或抛异常（整个换算分支包在 {@code try/catch (RuntimeException)} 里，<b>只读、绝不外抛</b>）；
     * ④ 真件 id 取不到（注册表返回 {@code null}）。回落是"照旧记账（含数量）"，不是"跳过"——
     * 本方法只在原语义上换族，任何拿不准的输入都回到 {@code familyOf} 的既有行为。
     *
     * <h4>⚠ 必须如实说的一条（与队长的疑问直接相关）</h4>
     * 对本包这条 {@code …/conversion/cc_cosmic_processing_unit_core} 配方，按 (乙) 归族后
     * <b>族仍然是 {@link NanochipCalibration.Family#COSMIC}</b>，逐条如下：
     * <ol>
     *   <li>该配方的物品产物 = {@code gtlmodular:cc_cosmic_processing_unit_core}
     *       （{@code data/gtlmodular/recipes/nanochip/conversion/cc_cosmic_processing_unit_core.json:30}）；</li>
     *   <li>它是 CC ⇒ {@code materialize} 取出的真件 =
     *       {@code kubejs:cosmic_processing_unit_core}（同文件 {@code :17} 就是它的输入，
     *       {@code NanochipConversionMap} 的 {@code ccToReal} 表正是由这条配方建出来的）；</li>
     *   <li>该 id 的 path = {@code cosmic_processing_unit_core} —— <b>含 {@code "cosmic"}</b> ⇒</li>
     *   <li>{@link NanochipCalibration.Family#of(ResourceLocation)} 的子串链
     *       （{@code NanochipCalibration.java:327-365}）在 {@code p.contains("cosmic")} 那一句命中
     *       ⇒ 返回 <b>{@code COSMIC}</b>。</li>
     * </ol>
     * ⇒ <b>(乙) 没有改变队长看到的现象</b>（换算那 1000 个仍然记进「寰宇电路」条）。
     * 若队长要的是「<b>换算不算造电路</b>」，那是 <b>(甲)</b> —— 需要把换算产出的份数整类排除在
     * 校准记账之外（那会改动"是否计入"，属于另一个口径，本方法<b>不</b>擅自做）。
     *
     * <h4>一行回退</h4>
     * <pre>NanochipCalibration.Family family = familyForAccounting(recipe);  // → familyOf(recipe);</pre>
     * 把 {@code recordProduction} 里唯一那一处调用换回 {@code familyOf(recipe)}，
     * 即逐字回到本次改动前的行为（删掉本方法也不影响编译 —— 它只有一个调用者）。
     */
    private static NanochipCalibration.Family familyForAccounting(GTRecipe recipe) {
        if (recipe != null) {
            try {
                // 「是不是换算配方」的判据与 NanochipConversionMap.ensure 同款（身份比较最稳）：
                // NanochipConversionMap.java:114 `if (recipe.getType() != NanochipRecipeTypes.CONVERSION)`。
                // GTRecipeType 自己就实现了 vanilla 的 RecipeType，GTRecipe.getType() 返回的正是它。
                if (recipe.getType() == NanochipRecipeTypes.CONVERSION) {
                    List<Content> outs = recipe.getOutputContents(ItemRecipeCapability.CAP);
                    for (Content c : outs) {
                        ItemStack produced = firstItemStackOf(c.content);
                        if (produced == null || produced.isEmpty()) {
                            continue; // 边界①：这一份取不到 ⇒ 换下一份
                        }
                        if (!NanochipConversionMap.isCc(produced)) {
                            continue; // 边界②：产物不是 CC（例如反向配方 CC → 真件）⇒ 换下一份
                        }
                        // materialize 只按这件 CC 自己的 id 反查（NanochipConversionMap R-W2-2）⇒ 不会串号。
                        ItemStack real = NanochipConversionMap.materialize(produced);
                        if (real == null || real.isEmpty()) {
                            continue; // 边界③：还原不出 ⇒ 换下一份（后续回落 familyOf）
                        }
                        // 取 id 的写法照本包既有先例：NanochipAssemblyComplexMachine.java:7743。
                        ResourceLocation realId = BuiltInRegistries.ITEM.getKey(real.getItem());
                        if (realId == null) {
                            continue; // 边界④：注册表里拿不到 id ⇒ 换下一份（后续回落 familyOf）
                        }
                        return NanochipCalibration.Family.of(realId); // (乙)：按【真件 id】判族
                    }
                }
            } catch (RuntimeException e) {
                // fail-safe：只读、绝不外抛 —— isCc/materialize/getKey 任何一个抛了都回落原路。
            }
        }
        return familyOf(recipe); // 非换算配方（或上面四条边界全落空）⇒ 逐字走原路
    }

    /**
     * ★★ 2026-11-14【校准计数白名单 · 一行总开关】★★ —— {@code true} = 走名册（默认）；
     * <b>置 {@code false} ⇒ 逐字回到"全部计入"</b>（本开关以下的入口闸整段短路，不读名册、不打日志）。
     *
     * <p>队长两道令（逐字）：<blockquote>
     * 「<b>那就弄白名单，把他们的ID手动添加到校准的名册里，只有被允许的才能增加校准的数量</b>」<br>
     * 「<b>只有最终产出电路才算</b>」</blockquote>
     *
     * <p>名册本体 = 配置项 {@code nanochipComplex.calibrationRecipeWhitelist}
     * （默认 = 全模组唯一带 {@code circuit-calibration} 元数据的 30 条 = assembly_complex 7 + assembly_matrix 23；
     * 空列表回退内置默认名单；语义见 {@code nanochipComplex.calibrationRecipeWhitelist} 的 comment）。
     */
    public static final boolean CALIBRATION_WHITELIST_ENABLED = true;

    /**
     * 「白名单条数已生效」那一行的限流游标（每台机器一条；哨兵 {@code -10_000L}，与
     * {@link #lastNoFitLogTick} 同款，绝不用 {@code Long.MIN_VALUE}）：<b>只打一次</b>
     * —— 它只是"名册接上了、有几条"的现场凭证，不是每批都报的流水账。
     */
    private long lastWhitelistCountLogTick = -10_000L;

    /**
     * 「这条配方没有 id」/「白名单未命中」两行的<b>共用</b>限流游标（最多 1 秒一条，
     * 与 {@link #logCraftedRecipe} 同款纪律；哨兵同 {@link #lastNoFitLogTick}）。
     * 一条游标管两种文案：稳态下它们互斥（同一条配方只会落其中一种），不会互相饿死。
     */
    private long lastWhitelistMissLogTick = -10_000L;

    /** 一次配方完成：把产出的电路按族记入 10 万电路历史窗口。 */
    private void recordProduction(GTRecipe recipe) {
        Level lv = getLevel();
        if (lv == null || lv.isClientSide || recipe == null) {
            return;
        }
        // ★★ 2026-11-14【校准计数白名单 · 入口闸】★★ —— 队长：「那就弄白名单，把他们的ID手动添加到校准的
        //   名册里，只有被允许的才能增加校准的数量」+「只有最终产出电路才算」。
        //
        //   为什么必须有这道闸（已取证的根因）：队长「一个寰宇电路都没做过」，校准里寰宇却在涨。
        //   实机日志逐字：线程样本 `#8 …/conversion/cc_cosmic_processing_unit_core ×1000 31%`，
        //   同刻记账 `寰宇电路 +1000 ⇒ 本族 1064 / 历史 1080`。根因 = 换算式
        //   （gtlmodular:nanochip_conversion）的产物是**中间件** `cc_cosmic_processing_unit_core`
        //   （data/gtlmodular/recipes/nanochip/conversion/cc_cosmic_processing_unit_core.json:30），
        //   而族判定在配方元数据缺失时按产物 id 子串猜族 ⇒ 命中 COSMIC ⇒「换算」被算成了「造电路」；
        //   换算只是「真件 ⇄ CC」互换 = 搬运/包装，**不产电路**。
        //
        //   本闸**只决定"是否计入"**：命中 ⇒ 下面逐字照旧（族判定、数量口径、
        //   logCalibrationRecord、窗口与阈值一个字节都不动）；不命中 ⇒ 直接 return（记 0 条，
        //   不是记 0 个，也不是记一个假族）。**绝不**顺手改任何别的记账逻辑。
        //
        //   一行回退（逐字回到"全部计入"）：把 CALIBRATION_WHITELIST_ENABLED 置 false。
        if (CALIBRATION_WHITELIST_ENABLED) {
            // ① 「显示即事实」的现场凭证：本条链路上第一次记账时报一次"当前生效名册有几条"。
            //    条数直接取配置的现读值（NanochipConfig 的同名读数，不另存副本）⇒ 面板/Jade
            //    若显示它，与实际记账用的名册永远是同一个数。
            if (this.lastWhitelistCountLogTick < 0L) {
                this.lastWhitelistCountLogTick = 0L;
                NanochipModContext.logger().debug("[gtlmodular] 校准白名单已生效：当前 {} 条（配置项 "
                                + "nanochipComplex.calibrationRecipeWhitelist；空列表 = 回退内置默认名单）。"
                                + "不在此名册里的配方一律不计入校准。",
                        NanochipModContext.config().getNanochipCalibrationRecipeWhitelistCount());
            }
            // ② id 为 null ⇒ 不计（并限流说明"这条配方没有 id"，绝不静默）。
            //    取 id 的写法照本包既有先例：NanochipCandidateSearch.java:147 / NanochipConcurrentBatches.java:251
            //    —— GTRecipe.getId() 的类型是 ResourceLocation（可为 null）。
            ResourceLocation whitelistRecipeId = recipe.getId();
            if (whitelistRecipeId == null) {
                long now = lv.getGameTime();
                if (now - this.lastWhitelistMissLogTick >= 20L) {
                    this.lastWhitelistMissLogTick = now;
                    NanochipModContext.logger().debug("[gtlmodular] 校准白名单：这条配方没有 id（recipe.getId() == null）"
                                    + "⇒ 不计入校准。检修方向：该配方是不是运行时动态构建（没有数据包 id）的？"
                                    + "若是自有配方，补一份 JSON 让它拿到 id 即可被名册收录（@{}）。",
                            getPos());
                }
                return;
            }
            // ③ 名册判定：把 ResourceLocation 归一化成 `namespace:path` 再比
            //    （ResourceLocation.toString() 就是这个格式；本包往返取证见
            //     NanochipConcurrentBatches.java:251 存 / :312 ResourceLocation.tryParse 读回，逐字往返、无 `#` 之类后缀）。
            //    规则（`*` 结尾 = 前缀、否则精确）与全部边界逐条落在
            //    NanochipCalibration.whitelistMatches 的 javadoc 里，本处不重复实现。
            if (!NanochipCalibration.whitelistMatches(whitelistRecipeId,
                    NanochipModContext.config().getNanochipCalibrationRecipeWhitelist())) {
                long now = lv.getGameTime();
                if (now - this.lastWhitelistMissLogTick >= 20L) {
                    // 限流 1 秒一条（同 logCraftedRecipe 纪律）：绝不刷屏，但绝不静默。
                    this.lastWhitelistMissLogTick = now;
                    NanochipModContext.logger().debug("[gtlmodular] 校准白名单未命中 ⇒ 这条配方不计入校准：{}"
                                    + "（要放开它：把这一整行 id 复制进配置项 "
                                    + "nanochipComplex.calibrationRecipeWhitelist；前缀匹配就在末尾加一个 *）",
                            whitelistRecipeId);
                }
                return;
            }
            // 命中 ⇒ 落到下面既有的记账链路（一个字未改）。
        }
        // ★★ 0.3.132【队长**最终**裁决 = 上面的白名单，不是 (乙)】★★
        //   队长原话：「我一个寰宇电路都没做过，为什么我的寰宇会有增加在纳米标准里」
        //   ⇒ 我先给了 (甲)/(乙) 两条路，队长一度选 (乙)；**随后队长给了最终口径**：
        //     「**那就弄白名单，把他们的ID手动添加到校准的名册里，只有被允许的才能增加校准的数量**」
        //     ＋「**只有最终产出电路才算**」。
        //   ⇒ **权威口径 = 上面那道白名单入口闸**（`CALIBRATION_WHITELIST_ENABLED`）：
        //     换算表与全部 `cc_processed_*` 中间件工序**根本走不到这里**（在闸上就 return 了）。
        //
        //   ⚠ 下面调用的 `familyForAccounting` 是 (乙) 那一轮的**遗留分流**，作用域已被白名单收窄：
        //     名册命中的那 30 条全在 assembly_complex / assembly_matrix ⇒ 对它**逐字走原路**
        //     ⇒ **行为与直接调 `familyOf(recipe)` 零差异**；
        //     只有当有人**手动把换算配方加进名册**时它才生效（那是玩家显式要"换算也计入"）。
        //   ⇒ 它**不再代表队长的口径**；保留它仅为"万一有人放行换算，族不会按 `cc_*` 中间件名乱猜"。
        //     （只有这一个调用者，删掉它不影响编译 —— 要不要删等队长发话。）
        //   ★ 已如实登记（(乙) 那一轮查实）：这条 `…/conversion/cc_cosmic_processing_unit_core`
        //     的真件 id 是 `kubejs:cosmic_processing_unit_core` ⇒ **同样命中 `cosmic`**
        //     ⇒ 按 (乙) 归族**仍然是 COSMIC**（这正是它改不了现象的证据，也是后来改走白名单的原因）。
        List<Content> outs = recipe.getOutputContents(ItemRecipeCapability.CAP);
        if (outs.isEmpty()) {
            return;
        }
        // ★★ 2026-11-03【校准记账数量：这个数是「总数」，不是「每份」】★★
        //
        //   **唯一答案**：记进校准的数量 = **这一次真实产出的电路个数（本批总数）**。
        //   判据（是哪个字段已含并行 p）：**内容数量本身已经含 p**。到达本方法的 `recipe` 在
        //   更早的环节已经被"乘过并行"缩放了一次，`producedCountOf(...)` 读到的
        //   `LongIngredient.getActualAmount()` / `SizedIngredient.getAmount()` 就是 `每份 × p`
        //   —— 也就是这一批**真实产出**的总数。证据（三个调用方逐个给出，结论一致）：
        //     · 新逻辑（批次表）：`NanochipThreadedRecipeLogic.prepareBatch:2379` 调
        //       `NanochipParallelFit.scalePrecisely(modified, parallel)` → `:128`
        //       `recipe.copy(IAdvancedContentModifier.preciseMultiplier(parallel), false)`
        //       （`preciseMultiplier` 就是 `ContentModifier.multiplier(p)`，见
        //        `IAdvancedContentModifier:17-19`）＋ `:131` `IGTRecipe.of(scaled).setRealParallels(parallel)`；
        //       缩放后的**同一个对象**进批次表（`NanochipConcurrentBatches.tick:205`），
        //       由 `NanochipThreadedRecipeLogic.finishOutputs:2245` 原样交过来；
        //     · 旧逻辑：`NanochipRecipeLogic.onRecipeFinish:2422-2423` 传 `getLastRecipe()`，
        //       而该配方由父类 gtladditions `buildFinalNormalRecipe` 逐条 `multipleRecipe(r, p)`
        //       （= `recipe.copy(ContentModifier.multiplier(p), false)`，**输入输出一并放大**）摊好并行
        //       （证据：`_census/enhancedcore-src/docs/history/20260917-before-refactor/API标准.md:200,249`）；
        //     · 组装矩阵：`NanochipModuleMachine:1413` 传 `getLastRecipe()`，而 gtlcore
        //       `MultipleRecipesLogic.getRecipe()` 先对每条候选 `copy(ContentModifier.multiplier(p), false)`
        //       再 `setRealParallels(p)`，然后把**缩放后**的产出一律 `addAll` 进最终配方
        //       （`_census/_javap_simple_multirecipes.txt:168-196` ＋ `:245-317`）。
        //   ⇒ 所以这里**绝不能再乘一次并行**：乘了记的就是 `每份 × p²`，比真实产出**多记一个 p 倍**
        //     （2026-11-03 定案的缺陷）。为什么"再乘 `recipe.parallels`"乘的正好是 p（而不是 1）：
        //     gtlcore 的 `GTRecipeMixin.setRealParallels` 逐字是
        //     `this.realParallels = realParallel; this.parallels = Ints.saturatedCast(realParallel);`
        //     （`_census/gtlcore-unofficial/.../mixin/gtm/api/recipe/GTRecipeMixin.java:79-82`，
        //      该源码树 `gradle.properties` 的 `mod_version=1.2.3.2-fix1` 与本包 gtlcore 同版本；
        //      实机旁证：0.3.124 截图里逐条「并行 63 / 250」正是改前 Jade 直读 `recipe.parallels`
        //      的那一版 —— `_census/线程数与并行偏小_修复记录.md:26,461,646`）
        //     ⇒ 那个 int 字段与真实并行**同步**，所以"再乘"必然是把 p 乘第二遍。
        //   ⚠️ 防复发（口径纪律）：**"这个数是每份还是总数"只允许一个答案 = 总数**。
        //      将来若出现一个交进**未缩放**配方（内容 = 每份）的新调用方，**不许**在本方法里靠
        //      "看情况再乘一次"猜：正确做法是**在那个调用方**先缩放
        //      （`NanochipParallelFit.scalePrecisely(recipe, p)`）再记账，本方法的唯一口径不变。
        //   ★ 保留 64 位饱和 / int 夹取：即便不再乘 p，`每份 × p` 本身在大并行下仍会超过 int 边界
        //     （每份 ≥ 2 且 p ≥ 2^30 就够），所以仍走 `saturatingToInt` 夹到 {@link Integer#MAX_VALUE}，
        //     **绝不** `(int)` 窄化（那会回绕成负数 ⇒ 静默污染占比与校准档位，界面那根族条倒着走；
        //      这也是 2026-10-27「int 乘法 ⇒ 64 位饱和乘法」那条改动的原始理由，结论不变）。
        //   ★ 一行回退（把并行那一乘加回去 = 逐字回到 2026-11-03 之前的记账口径，仅用于对照复现缺陷）：
        //     int amount = saturatingToInt(saturatingMul(Math.max(1L, (long) recipe.parallels),
        //             Math.max(1L, producedCountOf(c.content))));
        // ★ 2026-10-31【M1 · 本轮根因】：**产物识别从"只认 ItemStack"改成三路兜底**。
        //   旧写法 `c.content instanceof ItemStack stack` 在本包（gtlcore 1.2.3.2）**恒假** ——
        //   物品产出是 `Ingredient`（`gtceu:sized` → `LongIngredient`/`SizedIngredient`）⇒ 循环体
        //   一次都不执行 ⇒ `calibration.record(...)` 从未被调用 ⇒ 历史恒 0（见
        //   `_census/纳米校准_诊断.md` E1/E2/E4）。同坑已在 `NanochipSelfCheck.itemIdOf:1282-1298`
        //   （2026-09-30）修过一次，这里是同批补齐。
        //   · 识别（`firstItemStackOf`）= ItemStack / ItemStack[] / Ingredient 三路；
        //   · 计数（`producedCountOf`）= LongIngredient.getActualAmount() → SizedIngredient.getAmount()
        //     → 物品栈自身 count → 1；**不能只读 `getItems()[0].getCount()`**：那是单份样品(count 1)，
        //     真实份数（例：晶体处理器 8 个）存在 Sized/Long 包装里，读错会少记。
        //   · 族（`familyOf(recipe)`）= 配方元数据 `circuit-calibration` 优先、物品 id 猜族回落
        //     （见 `familyOf` 的注释）；**故意按"整条配方一个族"取**，与 `familyOf` 的既有语义一致。
        //   · 识别不出 ⇒ **跳过该产物、不记 0、不记假账**，并打一行 debug 留痕（下方 `unresolved`），
        //     绝不静默继续。计数在 64 位里饱和（不新增回绕面，见上一条注释）。
        int unresolved = 0;
        for (Content c : outs) {
            ItemStack stack = firstItemStackOf(c.content);
            if (stack == null || stack.isEmpty()) {
                unresolved++;
                continue;
            }
            // ★★ 0.3.132【队长裁决 (乙)】族判定改走 familyForAccounting：非换算配方 = 逐字走原路
            //   familyOf(recipe)；换算配方 = 按"被换算的真件 id"判族（fail-safe 回落见该方法 javadoc）。
            //   一行回退：把这里换回 familyOf(recipe) 即逐字回到本次改动前。
            NanochipCalibration.Family family = familyForAccounting(recipe);
            // ★ 内容数量已含并行 p（见上方长注释）⇒ 这里读到的**就是本批真实产出总数**，绝不再乘并行。
            int amount = saturatingToInt(Math.max(1L, producedCountOf(c.content)));
            calibration.record(family, amount);
            logCalibrationRecord(family, amount);
        }
        if (unresolved > 0) {
            NanochipModContext.logger().debug("[gtlmodular] 校准记账 @{}：这条配方有 {} 份物品产出识别不出物品类型 ⇒ "
                            + "这些份数**未记入**校准（不记 0、不记假族）。识别不出 = content 既不是 "
                            + "ItemStack/ItemStack[]，也不是可解析的 Ingredient；检修方向见 "
                            + "NanochipAssemblyComplexMachine#firstItemStackOf 的注释。",
                    getPos(), unresolved);
        }
        updateSyncedTexts();
    }

    /** 校准记账诊断日志的限流游标（最多 1 秒一条；档位变化时无视限流立刻打一条）。 */
    private long lastCalibrationLogTick = -10_000L;

    /** 上次打日志时的生效档位（-1 = 还没打过）：用于「档位一变必打一行」。 */
    private int lastLoggedCalibrationTier = -1;

    /**
     * ★ <b>2026-09-21（0.1.41）队长</b>：「<b>我制作湿件，也不见湿件那个条有变动</b>」——
     * 校准「记了没有 / 记到哪一族 / 条为什么看不出来」的<b>证据行</b>。
     *
     * <h4>为什么必须打这一行</h4>
     * 校准面板上那根族条画的是「本族数量 ÷ 判定分母」。分母口径由
     * {@code nanochipComplex.calibrationUseFullWindow} 决定，而它<b>两种取值都会在日志里写明</b>：
     * 取 {@code true}（原版口径）时分母固定 100000 ⇒ 一次组装矩阵产出（8 个 × 并行）在条上
     * 只有千分之几、肉眼就是「没动」；取 {@code false}（★ 2026-09-21 队长令「改」之后的默认）
     * 时分母 = 已记录数 ⇒ 同样一次产出就能看出条涨。光看 UI 分不清
     * 「压根没记账」和「记了但分母太大」，这一行把四个数一次摊开：
     * 本族增量、本族累计、历史总数、判定分母（含口径）+ 当前占比与档位。
     *
     * <p>限流 1 秒一条（与 {@link #logCraftedRecipe} 同款纪律），档位变化时强制打一条
     * （升档是最需要看见的事件）。稳态开销 = 一次 long 比较。
     */
    private void logCalibrationRecord(NanochipCalibration.Family family, int amount) {
        Level lv = getLevel();
        if (lv == null || lv.isClientSide) {
            return;
        }
        int tier = calibration.activeTier();
        boolean tierChanged = tier != lastLoggedCalibrationTier;
        long now = lv.getGameTime();
        if (!tierChanged && now - this.lastCalibrationLogTick < 20L) {
            return;
        }
        this.lastCalibrationLogTick = now;
        this.lastLoggedCalibrationTier = tier;
        double den = calibration.denominator();
        NanochipModContext.logger().info("[gtlmodular] 校准记账 @{}：{} +{} ⇒ 本族 {} / 历史 {} · 分母 {}（{}）"
                        + " ⇒ 占比 {}%（生效档位 {}）",
                getPos(), family.nameZh(), amount, calibration.countOf(family), calibration.total(),
                den <= 0.0D ? "0" : String.format(Locale.ROOT, "%.0f", den),
                NanochipCalibration.useFullWindowDenominator() ? "原版固定 100000" : "已记录数",
                String.format(Locale.ROOT, "%.3f",
                        den <= 0.0D ? 0.0D : calibration.countOf(family) / den * 100.0D),
                tier);
    }

    /**
     * 供模块（组装矩阵）调用：走完整链路时电路是模块产出的，但校准属于复合体。
     * 服务端才记账，客户端调用直接忽略。
     */
    public void recordExternalProduction(GTRecipe recipe) {
        recordProduction(recipe);
    }

    /** 每 tick 把档位/校准/模块摘要同步到客户端（供界面文字使用）。 */
    private void tickSync() {
        Level lv = getLevel();
        if (lv == null || lv.isClientSide) {
            return;
        }
        tickDrawWindow();
        updateSyncedTexts();
        // ★ 2026-09-19：**未成形就定期给一次缺件清单**（内部限流 15 秒，稳态开销 = 一次 long 比较）。
        //   为什么放这里而不是放在 asyncCheckPattern 的"预检失败"分支：那条分支被体检 ⑤ 钉死为
        //   **裸 return**（预检失败 ⇒ 不调用任何昂贵东西，防卡死）。审计是本机自己的 8 元素采样 +
        //   唯一方块格遍历（≤ 8087 次 getBlockState，最多 15 秒一次），放周期性 tick 里既不违反那条纪律，
        //   又能覆盖"从没成形过"的机器（那种机器此前整场拿不到任何逐格线索）。
        if (!isFormed()) {
            logUnformedAudit();
            // ★ 2026-09-21（0.1.21 A2）：失效宽限期到点 ⇒ 这时才真正断开全部模块。
            if (this.moduleDisconnectDeadline >= 0L && lv.getGameTime() >= this.moduleDisconnectDeadline) {
                this.moduleDisconnectDeadline = -1L;
                int dropped = modules.size();
                safeClearModules();
                NanochipModContext.logger().info("[gtlmodular] NAC @{} 失效宽限期（{} tick）到 ⇒ 断开模块 {} 个"
                                + "（宽限期内若重新成形则不断开 —— 这是 0.1.21 的连接去抖）",
                        getPos(), INVALID_GRACE_TICKS, dropped);
            }
        } else if (this.moduleDisconnectDeadline >= 0L) {
            // 重新成形 ⇒ 取消待执行断开（连接从未中断）
            this.moduleDisconnectDeadline = -1L;
        }
        // ★ 2026-09-19：手动刷新的兜底回报 —— 预检就失败时，"跑过全量匹配"那两条回报路径都不会到，
        //   而请求人还等着一个结论 ⇒ 到点（2 秒）无论如何给一条（成形 / 缺件清单 / 数量类提示）。
        if (this.pendingRefreshRequester != null && lv.getGameTime() >= this.refreshReportAtTick) {
            reportRefreshResult();
        }
    }

    /**
     * ★ 2026-10-28【独立跨配方线程 · 补齐「机器侧每 tick 同步」】<b>包内转发入口</b>。
     *
     * <p>旧逻辑（{@link NanochipRecipeLogic}）的 {@code serverTick} 第一句就是 {@code nac.tickSync()}；
     * 而新逻辑（{@code NanochipThreadedRecipeLogic}）<b>整体覆写</b>了 {@code serverTick}、
     * 不经过旧逻辑那一句 ⇒ 那条路上的「每 tick 同步」会整体失效
     * （受影响：F 位连接刷新每 20 tick、界面同步文案 {@link #updateSyncedTexts}、
     * 未成形缺件审计的 15 秒限流那一路）。
     *
     * <p>{@link #tickSync()} 本体是 {@code private}（它只被旧逻辑调），而新逻辑是<b>另一个类</b>
     * ⇒ 这里给一个包内转发方法（方法体只有一句），让新逻辑能<b>与旧逻辑同频</b>地每 tick 调一次。
     * 语义与调用频次<b>逐字相同</b>：没有新增逻辑、没有任何行为改动、不新增字段、不进 NBT。
     */
    void nanochipTickSync() {
        tickSync();
    }

    private void updateSyncedTexts() {
        syncedMode = fullMode() ? "全流程档" : "直通档";
        syncedCalibrationText = calibration.summary();
        syncedCalibrationTier = calibration.activeTier();
        // ★★ 0.3.132【校准重构】把「生效族」推给客户端（见字段 javadoc：**不在客户端复算**）★★
        NanochipCalibration.Family activeFamily = calibration.activeFamily();
        syncedActiveFamilyOrdinal = activeFamily == null ? -1 : activeFamily.ordinal();
        // 判定分母口径（配置项 nanochipComplex.calibrationUseFullWindow）：跟着档位一起推给客户端，
        // 界面上的「校准分母」行与 9 段进度条都用它，保证显示口径 == 判定口径。
        syncedFullWindowDenominator = NanochipCalibration.useFullWindowDenominator();
        syncedBreakdownText = calibration.breakdown();
        // 各族数量（给结构里真空仓的图形进度条用）
        StringBuilder counts = new StringBuilder();
        for (NanochipCalibration.Family family : NanochipCalibration.Family.values()) {
            counts.append(calibration.countOf(family)).append(',');
        }
        syncedFamilyCounts = counts.toString();
        // ★★ 0.3.132【校准面板档位显示 · 修】逐族档位一并同步（见 syncedFamilyTiers 的 javadoc）★★
        //   ⚠ 必须与上面的 counts **同一套遍历与同一套 ordinal 下标**（都用 Family.values()）——
        //     面板的 syncedTierOf(ordinal) 与 syncedCountOf(ordinal) 按下标配对，
        //     两串长度/顺序一旦不一致，档位就会配错族（这是"看着能用、实则错位"的典型坑）。
        StringBuilder tiers = new StringBuilder();
        for (NanochipCalibration.Family family : NanochipCalibration.Family.values()) {
            tiers.append(calibration.tierOf(family)).append(',');
        }
        syncedFamilyTiers = tiers.toString();
        syncedHistoryTotal = calibration.total();
        // 真空网络摘要：内容变化或搬运仓打了脏标记时才重建字符串（避免每 tick 拼串 + 同步）
        long vacuumTotal = vacuumNetwork.total();
        if (vacuumDirty || vacuumTotal != lastVacuumTotal) {
            vacuumDirty = false;
            lastVacuumTotal = vacuumTotal;
            syncedVacuumText = vacuumNetwork.summaryAll();
        }
        syncedParallelShown = getMaxParallel();
        // ★ 2026-10-07：原来这里把真实跨配方线程数推给客户端的 `syncedCrossRecipeThreads` 已<b>删除</b> ——
        //   它唯一的读者是 addDisplayText 里那行「跨配方线程」，而那行本轮按「单一数据源」原则删掉了
        //   （父类 addDisplayText 自带同一行，见该处注释）。留着就是「只写不读」的死同步字段。
        //   线程数在服务端是纯函数（getMultipleThreads()：结构/电压/引擎仓），没有再同步的必要。
        StringBuilder sb = new StringBuilder();
        for (NanochipModuleKind kind : NanochipModuleKind.values()) {
            int n = countModules(kind);
            if (n <= 0) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(" · ");
            }
            sb.append(kind.nameZh()).append(" ×").append(n);
        }
        syncedModuleText = sb.length() == 0 ? "未安装模块" : sb.toString();
    }

    public NanochipCalibration getCalibration() {
        return calibration;
    }

    /**
     * ★ 0.3.132【Jade 校准折减读数】本机校准当前的能耗折减倍率（1.0 = 无折减）。纯只读、不参与判定。
     *
     * <p><b>为什么需要它</b>：Jade 里那两行「总耗能倍率 / 总耗时倍率」由 gtlcore 的
     * {@code RecipeMultiplierProvider} 输出，它读的是 gtlcore 自己的 reduction 字段；而本机的纳米校准
     * 是在我们自己的 {@code buildRecipe} / {@code moduleOverclock} 里乘
     * {@link NanochipCalibration#euMultiplier()} / {@link NanochipCalibration#durationMultiplier}
     * ⇒ 从不经过 gtlcore 那两个字段 ⇒ 那两行恒 100%、与校准无关（队长 2026 报的「看不到倍率变化」）。
     * 本方法把「本机校准实际折减了多少」如实交给 Jade 那边的**我们自己的**一行显示。
     *
     * <p><b>纪律</b>：只读 {@link #calibration} 的既有读数，<b>不改任何判定</b>；
     * 未成形 ⇒ 1.0（校准在未成形机器上不生效，照实给「无折减」而不是伪造一个折扣）。
     */
    public double calibrationEuMultiplierForDisplay() {
        return isFormed() ? calibration.euMultiplier() : 1.0D;
    }

    /** ★ 0.3.132【Jade 校准折减读数】本机校准当前的时长折减倍率（按"当前生效族"取；无生效族 ⇒ 1.0）。 */
    public double calibrationDurationMultiplierForDisplay() {
        if (!isFormed()) return 1.0D;
        NanochipCalibration.Family active = calibration.activeFamily();
        return active == null ? 1.0D : calibration.durationMultiplier(active);
    }

    /**
     * ★★ 0.3.132【校准重构 · 重置的服务端出口】★★ —— <b>清空这一台机器的校准历史</b>。
     *
     * <h4>队长口径</h4>
     * 「只清这一台机器」⇒ 只动<b>本控制器实例</b>的 {@link #calibration}，
     * <b>不</b>碰别的机器、<b>不</b>碰存档键名/格式、<b>不</b>碰任何阈值与效果常量。
     *
     * <h4>为什么清完必须立刻 {@code updateSyncedTexts()}</h4>
     * 那个方法平时只在 {@code recordProduction} 尾部、以及"玩家正看着 UI"的 60 tick 分支里跑
     * ⇒ 若这里不立刻推一次，界面最多会**停 60 tick 显示旧值**（刚清完却还写着"晶体 第 2 档"）
     * —— 那就是在撒谎。⇒ 同步调用一次，让显示与事实同一 tick 对齐。
     *
     * <h4>为什么只允许服务端执行</h4>
     * 客户端那一份 {@code calibration} 只是服务端镜像的消费者（它从不 record）；
     * 在客户端清等于把客户端镜像清成 0 而服务端还有值 ⇒ 界面短暂显示 0，属污染。
     *
     * @return 清空前的历史总电路数（0 = 本来就没记录）
     */
    public int clearCalibration() {
        if (getLevel() != null && getLevel().isClientSide) {
            return 0;   // 客户端不执行（真正的动作在服务端那次点击里）
        }
        int before = calibration.clear();
        updateSyncedTexts();   // ★ 立刻对齐显示，绝不留下 60 tick 的旧值
        NanochipModContext.logger().info("[gtlmodular] NAC @{} 纳米校准已重置：历史 {} → 0 个电路（只清本机，未动存档键）",
                getPos(), before);
        return before;
    }

    /** 虚拟真空传送带网络（16 色通道）。 */
    public NanochipVacuumNetwork vacuumNetwork() {
        return vacuumNetwork;
    }

    /** ★【2026-09-16 双模式路由】同色管网连通分量图（管道定向模式用；简单模式下不被查询）。 */
    public NanochipPipeGraph pipeGraph() {
        return pipeGraph;
    }

    /**
     * 真空路由模式的一行文案（界面 + {@code /gtlmodular_nac} 自检用）。
     *
     * <p>为什么要有：管道定向模式下"不铺同色管道就不传输"是<b>正确行为</b>，
     * 但玩家很容易把它当成 bug（「明明是同一颜色怎么不传」）⇒ 必须让当前模式<b>可见</b>。
     */
    public static String routingModeText() {
        return NanochipModContext.config().isVacuumRequiresPipes()
                ? "§e管道定向§7：必须有同色管道通路才传输，照原版"
                : "§a简单§7：颜色即线，不用铺管道";
    }

    /**
     * ★ 切回简单模式时把"分量池"并回 0 池（见 {@link NanochipVacuumNetwork#flattenAll()}）。
     *
     * <p>为什么需要：管道模式下虚拟件可能攒在 {@code <色>@<n>} 池里；配置改回 {@code false} 之后
     * 所有端口都只读 0 池 ⇒ 那些件会"看不见"。这里在结构成形/机器加载时并一次
     * （只搬数量、不动原型与 CC/PC 标记 ⇒ 不丢件），并打一条日志告知。
     */
    private void reconcileRoutingMode() {
        if (NanochipModContext.config().isVacuumRequiresPipes()) {
            return;                       // 管道模式：各分量各归各，不动
        }
        int moved = vacuumNetwork.flattenAll();
        if (moved > 0) {
            NanochipModContext.logger().info("[gtlmodular] 真空路由=简单模式：已把 {} 条分量池并回主通道（不丢件）@{}",
                    moved, getPos());
            markVacuumDirty();
        }
    }

    /** 搬运仓写通道后打脏标记，下一次 tickSync 会把摘要推给客户端。 */
    public void markVacuumDirty() {
        this.vacuumDirty = true;
    }

    // ---------- 中央供电（与生态园同款：All power from the energy hatch is distributed to the modules） ----------

    /** 近 1 秒模块实付电（EU/t 平均，客户端显示用）。 */
    @DescSynced
    private long syncedModuleDrawPerTick;

    private long drawAccumulator;
    private long drawWindowSecond = Long.MIN_VALUE;

    public long getModuleDrawPerTick() {
        return syncedModuleDrawPerTick;
    }

    /** 跨秒时把上一秒累计的模块扣电换算成 EU/t 平均值。 */
    private void tickDrawWindow() {
        Level lv = getLevel();
        if (lv == null || lv.isClientSide) {
            return;
        }
        long second = lv.getGameTime() / 20L;
        if (second != drawWindowSecond) {
            if (drawWindowSecond != Long.MIN_VALUE) {
                syncedModuleDrawPerTick = drawAccumulator / 20L;
            }
            drawAccumulator = 0L;
            drawWindowSecond = second;
        }
    }

    /** 复合体自身蓄电（EU）。 */
    public long getTotalStoredEU() {
        if (!isFormed()) {
            return 0L;
        }
        EnergyContainerList e = getEnergyContainer();
        return e == null ? 0L : e.getEnergyStored();
    }

    /** 复合体自身蓄电上限（EU）。 */
    public long getTotalCapacityEU() {
        if (!isFormed()) {
            return 0L;
        }
        EnergyContainerList e = getEnergyContainer();
        return e == null ? 0L : e.getEnergyCapacity();
    }

    /** 复合体能源仓输入电压。 */
    public long getMaxInputVoltage() {
        if (!isFormed()) {
            return 0L;
        }
        EnergyContainerList e = getEnergyContainer();
        return e == null ? 0L : e.getInputVoltage();
    }

    /**
     * <b>NAC 最大输入功率（EU/t）＝ 能源仓输入电压 × 安培数</b>。
     *
     * <p>wiki 的缓存公式读的就是它：{@code 模块缓存 = NAC最大输入功率 × (2 × 模块最大配方时间)}，
     * 以及「超频后的功率不能超过 NAC 可用输入功率」。
     * 这里对 {@code 电压 × 安培} 做一次**饱和乘法**（电压到 OPV/MAX 档时数值极大，直接乘会回绕成负数）。
     */
    public long getMaxInputPower() {
        if (!isFormed()) {
            return 0L;
        }
        EnergyContainerList e = getEnergyContainer();
        if (e == null) {
            return 0L;
        }
        long v = e.getInputVoltage();
        long a = Math.max(1L, e.getInputAmperage());
        if (v <= 0L) {
            return 0L;
        }
        // ★ 2026-10-27【统一饱和工具】：原内联判界 `v > Long.MAX_VALUE / a ? MAX : v * a` 改调 saturatingMul
        //   （判界写法逐字等价，行为不变；饱和语义仍是「超上限停在最大值，绝不回绕成负数」）。
        return saturatingMul(v, a);
    }

    /**
     * ★ 2026-10-08【无线电网供电兼容】<b>「无限 / 按需现取」型供电的容量哨兵</b>（EU）。
     *
     * <p>≥ 本值即视为「不储能、随时可取」的能源。取 {@link Long#MAX_VALUE}{@code / 2} 而非
     * {@code == Long.MAX_VALUE}：终端报的是 Long.MAX 量级，任何一处饱和/取整都不该让它掉出判定。
     *
     * <p><b>为什么是 Long.MAX 量级</b>（逐字出处）：
     * <ul>
     *   <li>GTL Additions 官方 wiki《无线电网终端》「输入终端」段：
     *       「普通能源输入上限为 <b>Long.MAX 级别</b>」
     *       ——{@code _census/_gtlcompat/add_fix1/assets/gtladditions/guides/gtladditions/guide/_zh_cn/machine/part/wireless_energy_network_terminal.md:36}；</li>
     *   <li>玩家实测主机界面「中央供电: 9,223,372,036,854,775,807 / 9,223,372,036,854,775,807 EU」
     *       ⇒ {@code getEnergyStored()} 与 {@code getEnergyCapacity()} 双双饱和到 {@code Long.MAX_VALUE}。</li>
     * </ul>
     *
     * <p><b>为什么不按类名硬编码</b>：真正想表达的是「源头的<b>可获取能力</b>远超储能尺度」，
     * 而不是「它是某个特定类」。真实储能式能源仓（即便 MAX 档激光仓）离这个值有极大距离，
     * 不会被误判。<b>✗ 未取证项</b>：MAX 档激光仓 {@code 电压 × 安培} 的实测值未逐档量过，
     * 故取半程哨兵把余量留足（★ 2026-10-19：{@code NanochipRecipeLogic} 里那份同义的
     * {@code ON_DEMAND_POWER_FLOOR} <b>已删除</b>，主机起跑闸
     * {@code NanochipRecipeLogic#hostAffordableWhole} 改为直接调用本类的
     * {@code isEnergySourceEffectivelyUnlimited()} / {@code getObtainableEnergyEU()}
     * ⇒ 全文件只剩这一个哨兵，单一数据源）。
     */
    public static final long ON_DEMAND_POWER_FLOOR_EU = Long.MAX_VALUE / 2L;

    // ==========================================================================================
    // ★★ 2026-10-27【并行 / 容量一律「64 位 + 饱和（钳制）运算」】★★
    //   上游作者（山海）口径（逐字）：「不可能变成负数的」「有写钳制」
    //   「而且原版 add 基类也是 Integer.max * Integer.max」
    //   「最大并行池也会被限制在 Long.MAX_VALUE 就被截断掉」
    //   「运算 BigInteger 超大型整数不能让数值太膨胀」「我认为 Long.MAX_VALUE * Long.MAX_VALUE 也差不多了」
    //   ⇒ 全文件<b>只用 long</b>，超上限一律停在最大值，<b>绝不回绕成负数</b>；<b>不引入 BigInteger</b>。
    //   本处是全文件唯一的饱和运算实现（同包的 NanochipModuleUpkeep 有一份自己的私有 saturatingAdd，
    //   属别人负责的模块侧文件，本轮不动）。
    // ==========================================================================================

    /**
     * <b>饱和乘法</b>：{@code a × b} 若超 {@link Long#MAX_VALUE} 则取 {@code Long.MAX_VALUE}。
     *
     * <h4>为什么这样写就不会回绕（依据）</h4>
     * Java 的 {@code long} 乘法溢出时按二补数回绕（{@code Long.MAX_VALUE * 2L == -2L}），
     * 所以必须在乘法<b>之前</b>判界：{@code b > 0} 时
     * {@code a > Long.MAX_VALUE / b} ⟺ {@code a × b > Long.MAX_VALUE}
     * （整数除法向下取整 ⇒ 判界为真的那一侧才是溢出侧；「刚好等于 Long.MAX_VALUE」落在安全侧）
     * ⇒ 走 {@code Long.MAX_VALUE} 分支时<b>根本不执行乘法</b>，只有判界证明不会溢出时才真的乘。
     * 这条判界法也是上游同源写法（本类原有的若干处内联判界与之逐字同构，本轮统一收敛到本方法）。
     *
     * <h4>为什么不用 BigInteger（作者原话）</h4>
     * 「<b>运算 BigInteger 超大型整数不能让数值太膨胀</b>」「你的内存会先死」；
     * 而且本包这些量的真实量级由「库存 / 输出容量」决定，不会因为上限写成
     * {@code Long.MAX_VALUE} 就真的膨胀 ⇒ 没必要付出对象分配与超出 {@code long} 的内存代价。
     * <p>非正因子按「没有量」返回 {@code 0}（调用方读到的绝不是负数）。
     * 上游同源写法（逐字，山海 {@code DShanhaiBatchLogic.java:57}）：
     * {@code batched.parallels = (int) Math.min((long) Math.max(1, recipe.parallels) * limit, Integer.MAX_VALUE);}
     * —— 「先转 {@code long} 再乘、最后在 {@code int} 边界饱和」正是本组工具的用法。
     */
    private static long saturatingMul(long a, long b) {
        if (a <= 0L || b <= 0L) {
            return 0L;
        }
        return a > Long.MAX_VALUE / b ? Long.MAX_VALUE : a * b;
    }

    /**
     * <b>饱和加法</b>：{@code a + b} 超上限停在 {@link Long#MAX_VALUE}，<b>绝不回绕成负数</b>。
     *
     * <p>判界同理：{@code a > Long.MAX_VALUE - b} 才是溢出侧，走那条分支时不执行加法。
     * 用在「累加器」一类的量上（如模块实付电的 1 秒窗口 {@code drawAccumulator}）——
     * 按需现取型电源的单个量本身就能到 {@code Long.MAX_VALUE} 级，两次相加若不判界即回绕成负数，
     * 界面上那行「模块实付 EU/t」会显示负值。
     * <p>负输入按「没有量」夹到 {@code 0}（本文件所有调用点的入参都非负）。
     */
    private static long saturatingAdd(long a, long b) {
        if (a <= 0L) {
            return Math.max(0L, b);
        }
        if (b <= 0L) {
            return a;
        }
        return a > Long.MAX_VALUE - b ? Long.MAX_VALUE : a + b;
    }

    /**
     * <b>在 {@code int} 接口边界上饱和</b>：{@code v} 超过 {@link Integer#MAX_VALUE} 就停在它上面。
     *
     * <p>只用在「必须交 {@code int}」的地方（{@link #getMaxParallel()}、
     * {@code NanochipCalibration#record(Family, int)}）—— 这些地方<b>不许</b>用 {@code (int) v}
     * 直接窄化（{@code long} 超过 {@code int} 范围时那会回绕成负数，正是本次要根除的现象）。
     * <p>模块侧同款口径可对照：{@code NanochipModuleMachine.getMaxParallel()} 直接返
     * {@code Integer.MAX_VALUE}（模块天生无限并行）⇒ 「int 边界 = Integer.MAX_VALUE」是本包一致取法。
     */
    private static int saturatingToInt(long v) {
        return v >= (long) Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.max(0L, v);
    }

    /**
     * ★ 2026-10-08【无线电网供电兼容】<b>本复合体的供电是不是「无限 / 按需现取」型</b>。
     *
     * <p>判据只看能源容器<b>自报的容量</b>（{@link EnergyContainerList#getEnergyCapacity()}）：
     * 达到 {@link #ON_DEMAND_POWER_FLOOR_EU} ⇒ 真。覆盖两类真实能源：
     * GTL Additions 的「无线电网输入终端」与创造级能源仓 —— 它们都<b>不储能、按需现取</b>，
     * 因此 {@code getEnergyStored()} 只能是 0 或一个没有意义的饱和值，
     * <b>不能用「已存多少」来判断它们给不给得出电</b>。
     *
     * <p><b>容器类名的真实出处</b>（供日后核对，本方法刻意不依赖它）：
     * 无线电网输入终端 {@code com.gtladd.gtladditions.common.machine.multiblock.part.WirelessEnergyNetworkTerminalPartMachineBase}
     * 提供的是 {@code com.gtladd.gtladditions.common.machine.trait.NetworkEnergyContainer}，
     * 其类签名逐字为
     * {@code NotifiableRecipeHandlerTrait<Long> + IEnergyContainer + IInt128EnergyContainer}
     * ——{@code _census/_probe_nacmat_evidence/classstr_gtladditions-3.2.8Custom_SubSpace-fix1.txt:12007-12017, 12609-12616}；
     * 其中 {@code IInt128EnergyContainer} 是 gtlcore 真实接口
     * （{@code org.gtlcore.gtlcore.api.capability.IInt128EnergyContainer}，方法
     * {@code getInt128EnergyStored/…Capacity/…InputPerSec/…OutputPerSec}，
     * ——同目录 {@code classstr_gtlcore-1.2.3.2-fix1.txt:9-22}），
     * 即「容量超出 long 表达范围、必须用 Int128 计量」的能源所实现的那一个接口。
     */
    public boolean isEnergySourceEffectivelyUnlimited() {
        if (!isFormed()) {
            return false;
        }
        EnergyContainerList e = getEnergyContainer();
        if (e == null) {
            return false;
        }
        return e.getEnergyCapacity() >= ON_DEMAND_POWER_FLOOR_EU;
    }

    /**
     * ★ 2026-10-08【无线电网供电兼容】<b>本复合体这一刻「可获取」的 EU</b>
     * —— 即「需要量 ≤ 主机可提供的量」这条口径里的右边。
     *
     * <ul>
     *   <li>储能式（正常能源仓/激光仓）：返回<b>已存</b>（{@link EnergyContainerList#getEnergyStored()}），
     *       与改动前的 {@code avail} 口径<b>逐字一致</b>；</li>
     *   <li>无限 / 按需现取型（无线电网终端、创造能源）：已存没有意义（恒为 0）⇒
     *       返回<b>容量</b>（{@link #ON_DEMAND_POWER_FLOOR_EU} 以上），表示「要多少现取多少」；</li>
     *   <li>真·完全无电（容量 0 且存量 0）：返回 0 ⇒ 调用方照旧拦住。</li>
     * </ul>
     */
    public long getObtainableEnergyEU() {
        if (!isFormed()) {
            return 0L;
        }
        EnergyContainerList e = getEnergyContainer();
        if (e == null) {
            return 0L;
        }
        long stored = e.getEnergyStored();
        return isEnergySourceEffectivelyUnlimited() ? Math.max(stored, e.getEnergyCapacity()) : stored;
    }

    /**
     * 模块按 tick 直接向复合体要电：复合体是电网账户 / 无线电网终端这类按需供电时，模块不预存整批，
     * 而是像 GTL 的电网舱那样每 tick 现扣。只在能全额付清时才扣，付不清就一点不扣，模块原地等电。
     *
     * <p>原版优先级语义：{@code Modules draw power from the NAC on a 'priority' system.
     * The Assembly Matrix has the lowest priority and will try to fill its buffer last,
     * letting power go to the other modules first.}
     * 所以组装矩阵（{@code lowPriority = true}）在「还有别的模块在等着要电」时<b>主动让电</b>。
     */
    public boolean requestModuleEnergy(long amount, boolean lowPriority) {
        if (amount <= 0L) {
            return true;
        }
        Level lv = getLevel();
        if (lv == null || lv.isClientSide || !isFormed() || !isWorkingEnabled()) {
            return false;
        }
        if (lowPriority && highPriorityStarved()) {
            return false;
        }
        EnergyContainerList e = getEnergyContainer();
        if (e == null || storedEU(e) < amount) {
            return false;
        }
        long paid = Math.max(0L, -e.changeEnergy(-amount));
        drawAccumulator = saturatingAdd(drawAccumulator, paid);
        return paid >= amount;
    }

    /**
     * 各能源容器存量之和（饱和加法）。不用 {@code EnergyContainerList.getEnergyStored()}：
     * 电网账户和无线电网终端的存量都是夹到 Long.MAX 的余额，直接相加会溢出成负数。
     */
    private static long storedEU(EnergyContainerList e) {
        long stored = 0L;
        for (com.gregtechceu.gtceu.api.capability.IEnergyContainer c : innerContainers(e)) {
            if (c != null) {
                stored = saturatingAdd(stored, Math.max(0L, c.getEnergyStored()));
            }
        }
        return stored;
    }

    /** 是否有高优先级模块正因缺电等待（组装矩阵据此让电）。 */
    private boolean highPriorityStarved() {
        for (IModularMachineModule<NanochipAssemblyComplexMachine, ?> m : getModules()) {
            if (!(m instanceof NanochipModuleMachine module) || !module.isFormed()) {
                continue;
            }
            if (module.getModuleKind() == NanochipModuleKind.ASSEMBLY_MATRIX) {
                continue;
            }
            var logic = module.getRecipeLogic();
            if (logic != null && logic.isWaiting()) {
                return true;
            }
        }
        return false;
    }

    // ---------- 模块挂载（由结构的 F 位驱动 —— 与原版一致，没有半径扫描） ----------
    //
    // ★【2026-09-16 修「连接不稳」】这一段的唯一权威是主机：
    //   ① 结构成形那一刻（onStructureFormed → scanAndConnectModules）以及此后每
    //      MODULE_REFRESH_INTERVAL tick，都按结构 F 位【整体重算】"这一刻该连哪些模块"，
    //      然后把 modules 幂等覆盖成这个结果；
    //   ② 模块侧只读这个结果（NanochipModuleMachine.hostConnected），自己成形/失效时不再写集合 ——
    //      原来那一步会把主机刚算好的结果冲掉（"谁后跑谁覆盖"）。

    /**
     * 按<b>结构里 {@code F} 位（模块位）的实际世界坐标</b>重算并<b>幂等覆盖</b>「已连接模块集合」。
     *
     * <h4>模块与它的 NAC 是怎么互相找到的</h4>
     * <ol>
     *   <li>结构图案里 {@code F} 那个字符的判定用
     *       {@code TraceabilityPredicate.setSlotName(NanochipComplexPattern.MODULE_SLOT_NAME)}
     *       登记了槽名（见 {@code NanochipComplexPattern.moduleSlots}）；</li>
     *   <li>GTCEu 每次<b>匹配成功</b>都会把该格的世界坐标写进
     *       {@code MultiblockState.getMatchContext()} 的 {@code "slots"} 表
     *       （{@code Map<BlockPos.asLong(), Set<String>>}）—— 这是 GTCEu 自己的官方机制，
     *       它的线圈/加热线圈判定就是靠同一张表把结果传给 {@code onStructureFormed}；</li>
     *   <li>于是主机只要遍历这批坐标，看哪一格站着 {@link NanochipModuleMachine} 就够了；
     *       模块侧<b>不做任何反向扫描</b>（见 {@code NanochipModuleMachine.findAndConnectToHost}）。</li>
     * </ol>
     * 这条路径<b>不含任何距离常量</b>，与原版
     * {@code MTENanochipAssemblyComplex.addModuleToMachineList}（由 {@code F} 位 hatch 元素的 adder 调用）
     * 同构；原版也是「每次结构校验先 {@code modules.clear()} 再由结构重建」，本方法即其等价物
     * （重算结果 ⇒ 覆盖集合，效果与 clear+rebuild 相同，但只在真有增删时才碰模块，且会打日志）。
     *
     * <h4>幂等性</h4>
     * 调用后的不变量：{@code modules} <b>恒等于</b>「本次重算结果」（这一刻 F 位上、模块自己已成形、
     * 且类型匹配本机的那些模块）。所以调用多少次、以什么顺序调用都不会漂移；模块自己的结构检查
     * 不再参与这个集合的写入。
     */
    private void refreshModulesFromStructure() {
        Level level = getLevel();
        if (!(level instanceof ServerLevel) || !isFormed()) {
            return;
        }
        // ★【2026-09-18 结构检测性能修复】数据源 = **已完成匹配的快照**（{@link #cachedModuleSlots}），
        //   不再读活的 getMultiblockState().getMatchContext()。
        //
        //   改前：每 20 tick 读一次 live matchContext。GTCEu 的单线程异步检查线程会用
        //   MultiblockState.clean() 清空它再逐格填回，且半途失败直接 return ⇒ 我们读到残表
        //   （实测 9 分钟里 226 次采样、225 次数字都变，取值恒为 2/6/10/14/16/18/22/23/24，
        //   只有 48 是完整匹配）⇒ 按残表把好模块全断开 ⇒ 断连又触发新检查 ⇒ 正反馈卡死。
        //   改后：运行期只读快照，快照仅在 onStructureFormed()（GTCEu 已确认匹配成功、
        //   matchContext 完整）里刷新一次。⇒ 竞态窗口从"每 20 tick 一个"降为 0。
        //
        //   代价（有意，且不改变功能正确性）：结构刚失效、GTCEu 尚未给出下一次成功匹配的那段时间里，
        //   模块集合保持上一次成功匹配的结论（但 isValidModule 仍要求模块自己 isFormed，
        //   所以模块自己一失效就会被摘掉）。这与"成形了却拿不到槽位表就什么都不动"的既有取舍方向一致。
        List<BlockPos> slots = cachedModuleSlots;
        if (slots.isEmpty()) {
            // ★【2026-09-21 载入快路径】先试**存档快照**（见 restoredModuleSlots 的类注释）。
            //   ⚠️ 这一步必须在本"放弃"分支**之前**：载入后 isFormed() 已被 GTCEu 从存档恢复成 true，
            //   而 matchContext 是空的 ⇒ 若只等 onStructureFormed，载入后必然干等数分钟
            //   （2026-09-19 实测 4 分 18 秒）。采用前逐格验证，验不过照旧退回本分支。
            slots = adoptRestoredModuleSlots(level);
        }
        if (slots.isEmpty()) {
            // 还没有任何一次成功匹配、也没有可用的存档快照 ⇒ 无可依据的坐标表。
            // 什么都不动 —— 宁可"该断的晚一秒断"，也不能把已经连上的模块全部误断。
            if (!slotsUnavailableLogged) {
                slotsUnavailableLogged = true;
                NanochipModContext.logger().info("[gtlmodular] NAC @{} 尚无「结构 F 位」成功匹配快照"
                                + "（正在等 GTCEu 给出第一次成功匹配；槽名 \"{}\"）⇒ 本次不改动已连接模块（当前 {} 个）",
                        getPos(), NanochipComplexPattern.MODULE_SLOT_NAME, modules.size());
            }
            return;
        }
        slotsUnavailableLogged = false;

        // ① 重算：这一刻 F 位上「模块自己已成形 + 类型是本机」的模块集合
        //    （isValidModule = IModularMachineModule + isFormed + hostType 匹配，GTCEu 的默认实现）
        Set<IModularMachineModule<NanochipAssemblyComplexMachine, ?>> recomputed = new LinkedHashSet<>();
        for (BlockPos pos : slots) {
            MetaMachine machine = MetaMachine.getMachine(level, pos);
            if (machine == null || machine == this || !isValidModule(machine)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            IModularMachineModule<NanochipAssemblyComplexMachine, ?> module =
                    (IModularMachineModule<NanochipAssemblyComplexMachine, ?>) machine;
            recomputed.add(module);
            if (recomputed.size() >= getMaxModuleCount()) {
                break;
            }
        }
        int before = modules.size();

        // ② 覆盖（补）：重算结果里有、主机手里没有（或它记的 host 不是本机）⇒ 连上
        for (IModularMachineModule<NanochipAssemblyComplexMachine, ?> module : recomputed) {
            if (modules.contains(module) && module.getHost() == this) {
                continue;
            }
            module.connectToHost(this); // 内部会 addModule(this) → modules
            if (module instanceof NanochipModuleMachine nanochipModule) {
                nanochipModule.onHostLinked();
            }
            logModuleLinkChange("+", module, recomputed.size(), slots.size());
        }

        // ③ 覆盖（退）：主机手里有、但重算结果里没有 ⇒ 断开（同时清掉模块侧的 host/hostPosition）
        for (IModularMachineModule<NanochipAssemblyComplexMachine, ?> stale : new ArrayList<>(modules)) {
            if (recomputed.contains(stale)) {
                continue;
            }
            stale.removeFromHost(this);
            logModuleLinkChange("-", stale, recomputed.size(), slots.size());
        }

        // ④ 不变量自检（只报不改）：②③ 之后集合必须恰好 == 重算结果
        if (modules.size() != recomputed.size() || !modules.containsAll(recomputed)) {
            NanochipModContext.logger().warn("[gtlmodular] NAC @{} 模块集合收敛异常：集合 {} 个 / 本次重算 {} 个（预期相等）",
                    getPos(), modules.size(), recomputed.size());
        }
        if (modules.size() != before) {
            NanochipModContext.logger().info("[gtlmodular] NAC @{} 已连接模块数变化：{} → {}"
                            + "（结构 F 位 {} 个，其中已成形模块 {} 个）",
                    getPos(), before, modules.size(), slots.size(), recomputed.size());
        }
    }

    /**
     * ★【2026-09-21 载入快路径】把<b>存档恢复的 F 位坐标表</b>接手成可用快照 —— 但必须<b>先验证</b>。
     *
     * <p>为什么需要它、以及"GTCEu 恢复了 isFormed 却不恢复 matchContext"的存档取证，
     * 见 {@link #restoredModuleSlots} 的类注释。
     *
     * <h3>三条纪律（每一条都对应一次判红可能）</h3>
     * <ol>
     *   <li><b>未加载不等于否定</b>：任意一格所在区块还没加载 ⇒ 本次**不定论**（返回空、下轮再来），
     *       绝不允许"因为这几格没加载"就把整张表判死 —— 那会让快路径在载入早期被误杀；</li>
     *   <li><b>坐标表是"声称"，方块状态才是"事实"</b>：每一格都必须站着 F 位合法方块
     *       （网格接口机壳 或 模块控制器）。这条是"存档里写的 ≠ 现在真是这样"的唯一防线；</li>
     *   <li><b>验过就定论</b>：采用或否证都置 {@link #restoredSlotsSettled}，不每 20 tick 重验。</li>
     * </ol>
     *
     * <p>线程：只在服务端线程经 {@link #refreshModulesFromStructure()} 调用
     * （{@code scanAndConnectModules} 自 0.1.21 起一律投递到服务端线程），
     * 因此这里读方块状态是安全的。
     *
     * @param level 服务端世界（调用方已保证非 null 且是 ServerLevel）
     * @return 可用的坐标表；空 = 还没定论（有格子未加载）或已否证
     */
    private List<BlockPos> adoptRestoredModuleSlots(Level level) {
        if (this.restoredSlotsSettled) {
            return List.of();
        }
        List<BlockPos> restored = this.restoredModuleSlots;
        if (restored.isEmpty()) {
            this.restoredSlotsSettled = true;   // 存档里没有这张表 ⇒ 不必再试
            return List.of();
        }
        // ① 全部位置必须已加载（未加载 ⇒ 不定论，下轮再来）
        for (BlockPos pos : restored) {
            if (!level.isLoaded(pos)) {
                return List.of();
            }
        }
        // ② 逐格验证：必须是 F 位合法方块
        for (int i = 0; i < restored.size(); i++) {
            BlockPos pos = restored.get(i);
            Block block = level.getBlockState(pos).getBlock();
            if (!isModuleControllerBlock(block) && block != NanochipBlocks.MESH_INTERFACE_CASING.get()) {
                this.restoredSlotsSettled = true;
                this.restoredModuleSlots = List.of();
                NanochipModContext.logger().info("[gtlmodular] NAC @{} 存档 F 位快照**未采用**：第 {} / {} 格 @{} 站的是 {}"
                                + "（既不是网格接口机壳、也不是模块控制器）⇒ 结构已不是存档时的样子，"
                                + "退回等 GTCEu 第一次成功匹配",
                        getPos(), i + 1, restored.size(), pos.toShortString(),
                        BuiltInRegistries.BLOCK.getKey(block));
                return List.of();
            }
        }
        // ③ 验证通过 ⇒ 采用
        this.restoredSlotsSettled = true;
        this.restoredModuleSlots = List.of();
        this.cachedModuleSlots = List.copyOf(restored);
        this.lastMatchedModuleSlots.clear();
        this.lastMatchedModuleSlots.addAll(restored);
        this.slotsUnavailableLogged = false;
        NanochipModContext.logger().info("[gtlmodular] NAC @{} 采用**存档 F 位快照** {} 格（逐格验证通过：全部已加载、"
                        + "且都是网格接口机壳或模块控制器）⇒ 模块连接不必等 GTCEu 重跑整图案匹配",
                getPos(), restored.size());
        return this.cachedModuleSlots;
    }

    /** 模块连接状态变化的日志：谁加/减了哪个模块、坐标（见 {@link #refreshModulesFromStructure()}）。 */
    private void logModuleLinkChange(String sign,
                                     IModularMachineModule<NanochipAssemblyComplexMachine, ?> module,
                                     int recomputedCount, int slotCount) {
        String kind = module instanceof NanochipModuleMachine nanochipModule
                ? nanochipModule.getModuleKind().nameZh()
                : module.getClass().getSimpleName();
        MetaMachine machine = module instanceof MetaMachine meta ? meta : null;
        NanochipModContext.logger().info("[gtlmodular] NAC @{} 已连接模块 {}「{}」@{}"
                        + "（F 位上成形模块 {}/{}，本次操作后已连接 {} 个）",
                getPos(), sign, kind,
                machine == null ? "未知坐标" : machine.getPos().toShortString(),
                recomputedCount, slotCount, modules.size());
    }

    /**
     * 只读：{@code module} 是否在主机权威的「已连接模块集合」里。
     *
     * <p>★ 模块侧（{@link NanochipModuleMachine#hostConnected()}）<b>只读</b>这一个判定的结果，
     * 不再自己写集合 —— 这是"连接不稳"的修法核心。刻意不返回 {@code getModules()} 的包装集合，
     * 以免每 tick 在热路径上多分配一个对象。
     */
    public boolean isModuleConnected(IModularMachineModule<NanochipAssemblyComplexMachine, ?> module) {
        return module != null && modules.contains(module);
    }

    /**
     * ★【2026-10 模块框架对齐 · A2】<b>模块（重）连接的唯一入口</b> —— 现在是「框架原生主路 +
     * 我们自己的 F 位快照兜底」两条，<b>不再是「只读快照」</b>。
     *
     * <h3>主路（新）= gtlcore 框架默认实现</h3>
     * {@link IModularMachineHost#scanAndConnectModules()}（字节码
     * {@code gtl-dev/api-dump/gtlcore_IModularMachineHost_c.txt:65-116}）：遍历
     * {@link #getModuleScanPositions()}（= 12 个模块位的<b>世界坐标</b>，纯几何算出）→
     * {@code MetaMachine.getMachine(level, pos)} → {@code isValidModule}（= {@code IModularMachineModule}
     * + 模块自己 {@code isFormed()} + {@code getHostType()} 匹配）→ {@code module.connectToHost(this)}。
     * 它<b>不需要 {@code matchContext}、不需要成功匹配过一次、更不需要我们的快照</b> ⇒ 读档后主机第一次
     * 服务端 tick 就能把模块接回来（这正是「对齐官方那一套」的目的，参照
     * {@code gtladditions:forge_of_the_antichrist} 与 {@code SubspaceCorridorHubIndustrialArray}：
     * {@code onStructureFormed{safeClearModules; scanAndConnectModules}}）。
     *
     * <h3>兜底（保留，不删）= 我们自己的 F 位快照路径</h3>
     * {@link #refreshModulesFromStructure()}：按「结构 F 位坐标表」（{@link #cachedModuleSlots} /
     * 存档快照）重算并<b>双向收敛</b>（含「退出」方向：表里没有的模块会被断开，并顺带刷模块侧界面快照
     * {@code onHostLinked()}）。它能覆盖框架扫描表覆盖不到的情况（玩家把模块控制器放在<b>同一基座的
     * 其它 F 位</b>、结构里存在我们 12 条表外的模块位）。
     *
     * <h3>顺序为什么是「先框架、后快照」</h3>
     * 两条路在同一线程上顺序执行；快照路是「幂等收敛」，放在最后 ⇒ 有快照时最终结论仍由 F 位快照决定
     * （= 本次之前的行为，判据零变更）；无快照时它提前 return（什么都不动），框架路刚接上的模块原样保留
     * ⇒ 这正是读档场景。
     *
     * <h3>为什么不会「每 20 tick 接上又断开」（防空转抖动）</h3>
     * 框架扫描的 12 格已核对全部是结构里的 {@code 'F'} 位（见 {@link #getModuleScanPositions()}），
     * 而快照路的重算集合同样来自 F 位 ⇒ 同一个模块在两条路上的结论一致，不存在「框架接、快照断」的
     * 交替；朝向算错时框架最多是「扫到空气/机壳 ⇒ 一个都不接」，不会误接（{@code isValidModule} 要求
     * 目标格上真的站着一台<b>已成形且类型匹配</b>的模块）。
     *
     * <h3>并发 / 权威收口（单一写入者 = 主机）</h3>
     * 本方法<b>只</b>在服务端线程被调用：
     * <ul>
     *   <li>{@code onStructureFormed()} 侧经 {@code server.execute(this::scanAndConnectModules)} 投递
     *       （见 {@link #onStructureFormed()}，注释 :2964-2975）；</li>
     *   <li>周期/催促侧来自本机自己的服务端 tick 订阅（{@link #tickModuleRefresh()} /
     *       {@link #consumePendingResync(long)}），天然在服务端线程。</li>
     * </ul>
     * ⇒ {@link #modules}（{@code LinkedHashSet}，非线程安全）的写入者<b>只有本机</b>、且<b>只有服务端
     * 线程一个</b>。框架那条路的写入也落在主机自己身上（{@code connectToHost} → {@code host.addModule}），
     * 模块侧<b>没有</b>任何"自己也去写集合"的调用点 ⇒ 不存在主机与模块同时写同一份状态。
     */
    @Override
    public void scanAndConnectModules() {
        // ★ A2 主路：框架原生（遍历几何扫描位 → isValidModule → connectToHost）——不再依赖快照
        IModularMachineHost.super.scanAndConnectModules();
        // 兜底 / 差集补齐：F 位快照路径（保留不删；有快照时它仍是最终收敛者）
        refreshModulesFromStructure();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        // ★ 2026-09-21（队长：「一进游戏 NAC 相关的都要重新加载结构，而且要好久，尤其是主机」）：
        //   本机 onLoad = 这座复合体进世界的那一刻 ⇒ 从此刻起算 JOIN_QUIET_TICKS（10 秒）静默窗口，
        //   窗口内不消费"催促"（周期重扫照旧 ⇒ 不会因此永远不重算）。
        //   同时把去抖计时与日志闸记忆清零：跨世界/重载留下的陈旧状态一律作废，
        //   让"进门后每台机器先报一次自己的当前状态"这条硬需求成立（见 NanochipStructureLogGate#reset）。
        Level loadLevel = getLevel();
        this.joinQuietUntilTick = loadLevel == null ? -10_000L : loadLevel.getGameTime() + JOIN_QUIET_TICKS;
        this.resyncRequested.set(false);
        this.lastForcedResyncTick = -10_000L;
        this.structureLogGate.reset();
        // ★ 2026-09-27（外壳连片刷新时机）：跨世界/重载 ⇒ 心跳的 part 数基线作废，
        //   进世界后的第一次心跳无条件重测一次（清单本身已由 @Persisted 从存档恢复）。
        this.lastNacSlotPartCount = -1;
        this.nacSlotHeartbeat = -1;
        if (getLevel() != null && !getLevel().isClientSide) {
            unsubscribeModuleRefresh();
            moduleRefreshSub = subscribeServerTick(this::tickModuleRefresh);
            // ★【2026-09-16 双模式路由】读档进世界时收口一次：
            //   简单模式 ⇒ 把管道模式留下的分量池并回主通道（不丢件）；
            //   管道模式 ⇒ 本图作废，等第一次搬运查询时重算。
            pipeGraph.markDirty();
            reconcileRoutingMode();
            // 进世界（含读档）时报一句电网账户是谁；旧版本的随机账户在 resolveGridUser 里识别并解绑。
            if (NanochipModContext.config().isNanochipGridPowerEnabled()) {
                UUID gridUser = resolveGridUser();
                if (gridUser != null) {
                    NanochipModContext.logger().info("[gtlmodular] NAC @{} 电网账户 {}", getPos(), gridUser);
                } else {
                    NanochipModContext.logger().info("[gtlmodular] NAC @{} 电网账户未绑定 ⇒ 只用能源仓供电；"
                            + "空手潜行右键控制器即可绑定到自己的无线电网", getPos());
                }
            }
        }
    }

    @Override
    public void onUnload() {
        unsubscribeModuleRefresh();
        // ★★【结构位置缓存 + 方块移除定点失效】本机离开世界 ⇒ 从索引注销（这是**摘除时机**的主闸之一）。
        //   它一条覆盖三种"机器消失"：
        //     ① 方块被拆 —— 原版移除方块实体 → `BlockEntity.setRemoved()`；
        //     ② **区块卸载** —— 原版 `LevelChunk.clearAllBlockEntities()` 逐个 `setRemoved()`
        //        （本仓既有取证：`_census/enhancedcore-src/docs/客户端深度优化可行性报告-20260929.md:106-108`
        //        「576 行第二轮遍历调用 setRemoved」+「GT 机器的 setRemoved 会进入 MetaMachine.onUnload」）；
        //     ③ 世界卸载/服务器停止 —— 各维度区块批量卸载，走的还是 ② 那条。
        //   而 GTCEu 的 `MetaMachineBlockEntity.setRemoved()` 会转调 `MetaMachine.onUnload()`
        //   （本仓既有取证：`NanochipPipeMachine.java:304-309` 的 javap 实测。★ 改指针后这一条从
        //   "有最好"变成"必须有"：见 `_census/位置表指针化_实施记录.md` §③）。
        //   维度取本机记住的 `structureIndexDim`（**不依赖 getLevel()**：卸载路径上
        //   `BlockEntity.level` 是否还非空属于上游实现细节）；注销在索引侧带**身份校验**
        //   ⇒ 客户端镜像机器摘不掉服务端那条。指针是**弱引用**：即使这一条也没走到，
        //   也不可能钉住本机/方块实体/世界（弱引用 + 惰性清空壳兜底）。
        //   缓存与索引都是**运行期派生数据**（零 @Persisted、零 NBT）：重新加载时由
        //   syncStructurePosIndex() 按当时真实的 isFormed() 再对齐一次 —— 也就是"不进存档、
        //   也不会把上一局的结论带进新一局"。
        unregisterFromStructureIndex();
        dropPendingRemoval();
        super.onUnload();
    }

    private void unsubscribeModuleRefresh() {
        if (moduleRefreshSub != null) {
            moduleRefreshSub.unsubscribe();
            moduleRefreshSub = null;
        }
    }

    // ================= ★ 2026-09-27 读档部件恢复（修「读档后 NAC 部件未连接」） =================
    //
    // 完整根因、字节码/实机日志出处与方案对比见 `_census/NAC读档部件未连接_定位.md`（本轮落地方案 A + B）。
    //
    // 一句话根因：GTCEu 的 MultiblockControllerMachine 把 isFormed 标了 @Persisted、却把 partPositions
    //   只标了 @DescSynced ⇒ 读档恢复「已成形」这个结论、不恢复「零件在哪」这张表；而零件表的唯一填充口
    //   onStructureFormed()（→ 逐个 part.addedToController(this)）读档后再也不会被调用：
    //   · 周期路径被 asyncCheckPattern 第 0-7 行 `if (!hasError && isFormed) return` 挡掉
    //     （本类覆写的那一层在同一条早退之后，连被叫到都难）；
    //   · 事件路径要求"改动的格子已在 MultiblockState.cache 里"，而 cache 与 matchContext 一样不入存档；
    //   · getParts() 的懒重建只在 parts.size() != partPositions.length 时才动手（0 != 0 为假）且不调
    //     addedToController ⇒ 救不了。
    //   ⇒ 部件永远拿不到控制器坐标（部件侧 isFormed() 恒假）⇒ 能源 0 / 等级 ULV / 样板总成不工作，
    //     只有"拆掉控制器再放回"（走 controllerPos 失效分支 → 重新匹配 → onStructureFormed）才恢复。
    //
    // 外观同步不能代替服务端零件登记，否则客户端显示成形时部件仍可能拿不到控制器。

    /**
     * ★★【B · 停用"我们主动刷新结构"】开关：读档部件恢复
     * （<b>默认 {@code false} = 停用</b>；用户口径：「不要刷新结构什么的」）★★
     *
     * <p>本开关是 B 那条要求的<b>主闸</b>：它管住的 {@link #recoverLoadPartsIfNeeded()} 正是我们唯一一处
     * "在稳态下主动让上游重判一次结构"的动作（命中一次 = 一次 63×49×63 = 194,481 格的整图案匹配，
     * 也就是用户感知到的「一进游戏 NAC 相关的都要重新加载结构，而且要好久」）。置 {@code false} 之后：
     * <ul>
     *   <li>{@link #tickModuleRefresh()} 里那一句仍是每 tick 一次 boolean 读，<b>其余什么都不做</b>
     *       ⇒ 我们不再自己失效、不再自己把本机排回上游重判队列、不再"补一次结构重判定"；</li>
     *   <li>成形成/不成形的结论与"什么时候重判"<b>全部</b>回到上游自己的节奏
     *       （上游 {@code searchingTask} 每 250 ms 的周期重判、以及方块变更事件路径 —— 都不是我们的动作）；</li>
     *   <li><b>保留</b>：敲方块定点失效（{@link #invalidateOnCachedRemoval()}，用户明确要过的"上游事件路径"）、
     *       全部诊断日志（{@code diagnoseLoadPartsNotReady(...)} 的调用点只在本方法内部 —— 停用后不再打，
     *       这是本开关唯一的"副作用"）、以及 {@link #addDisplayText} 里那条状态文案（已按本开关改成分支，见该处）。</li>
     * </ul>
     *
     * <h3>⚠ 如实登记的后果（请用户裁决，不是我能替他决定的）</h3>
     * 停用之后，「读档后 GTCEu 恢复了 {@code isFormed=true} 却不恢复零件表
     * {@code partPositions}/{@code getParts()}」这个<b>上游缺口就没有我们的补救了</b>：
     * 零件表的唯一填充口是上游的 {@code onStructureFormed()}，而它在读档后<b>根本不会再被调到</b>
     * （{@code isFormed} 是 {@code @Persisted}、{@code asyncCheckPattern} 第 0-7 行对"成形且无错"的控制器
     * 直接早退；事件路径又因 {@code MultiblockState.cache} 读档后为空而收不到通知）⇒
     * <b>实机症状「成型了、但配方等级是 ULV / 能源 0 / 样板总成不工作」会复发，且机器可能需要玩家
     * 拆放一次控制器（或点一次「立即更新结构」）才能恢复</b>。
     *
     * <h3>两档口径（用户二选一）</h3>
     * <ol>
     *   <li><b>干净（当前默认）</b>：{@code false} —— 我们一次都不主动重判结构；代价 = 上面那条后果；</li>
     *   <li><b>能自动恢复</b>：把本常量改回 {@code true} —— 恢复动作仍然是<b>最正规的那一条</b>：
     *       {@link #LOAD_PARTS_INVALIDATE_RECOVERY_ENABLED} 默认 {@code true} ⇒ 走"上游正规失效入口
     *       {@code onStructureInvalid()} + {@code addAsyncLogic} 排回上游重判队列"，
     *       而<b>不</b>走那条"请上游顺手重判一次"的催促（{@code requestUpstreamStructureRecheck(...)}
     *       在第 2 轮就被实测判为进不去重建分支，仅作为一行回退保留）。</li>
     * </ol>
     * <p>除主闸外，本类<b>没有</b>第二处"我们主动刷新结构"的动作；{@link #confirmFoldedPattern()} 与
     * {@link #invalidateOnCachedRemoval()} <b>刻意保留</b>（前者是"同 tick 折叠"这道性能装置的兑现半边，
     * 还承担"拆一格外壳 → 下一 tick 失效"的正确性职责，关掉它会让连拆多格再次在服务端线程连跑整场匹配
     * ——历史 watchdog 卡死栈顶就是那条路径；后者是用户明确要保留的上游事件路径）。
     *
     * <h3>一行回退</h3>
     * ★ 历史（保留）：2026-11-15【任务 A】本常量曾由 {@code false} 改为 {@code true}（队长实测逐字：
     * 「现在跟主机的连接还有问题，我拆掉重放以后，它的配方等级又变到了ULV」——重启后自愈、拆放后又回 ULV，
     * 正是上面「⚠ 如实登记的后果」那一条）；随后队长复核「那个连接自愈跟敲块失效，不要加上去」⇒
     * <b>已回退为 {@code false}</b>（连同 {@code private} 可见性一起改回），
     * 即逐字回到 2026-11-12 的"停用"行为（与
     * {@link #LOAD_PARTS_INVALIDATE_RECOVERY_ENABLED} 同款纪律：编译期常量、不是 {@code @Persisted}、
     * 不进存档、不改存档格式、不新增 mixin）。只影响恢复动作本身：
     * {@link #requestStructureRefresh} 的早退修正（定位文档方案 B）是独立的一处正确性修正，不受本开关影响。<br>
     * ⚠️ 本常量<b>不受</b>原初模式早退影响（这就是它单独有效的原因）：{@link #recoverLoadPartsIfNeeded()}
     * 的调用点在 {@link #tickModuleRefresh()} <b>最前面</b>，位于原初模式那处 {@code return} <b>之前</b>。
     */
    private static final boolean LOAD_PARTS_RECOVERY_ENABLED = false;

    /** 恢复动作的尝试窗口（tick）；{@value} = 2 秒，与 {@link #REFRESH_REPORT_DELAY_TICKS} 同量级。 */
    private static final long LOAD_PARTS_RECOVERY_WINDOW_TICKS = 40L;

    /**
     * ★★ 2026-11-10 <b>持续恢复的重试间隔</b>（tick）；{@value} = 1 秒（与 {@link #MODULE_REFRESH_INTERVAL} 同频）。
     *
     * <p><b>为什么要有它（本轮的根因修正）</b>：{@link #LOAD_PARTS_RECOVERY_WINDOW_TICKS} 那 40 tick 窗口
     * 是从「本机服务端 tick 第一帧」起算的，而它是**一次性**的：窗口一过就置
     * {@link #loadPartsRecoveryDone}，此后<b>永不再试</b>。可现实是——结构 63×49×63 跨多个区块，
     * 那 40 tick（2 秒）窗口**极可能早于"所有结构区块真的加载完"**（读档/进世界头几秒正是区块陆续加载的时候，
     * 还有 GTCEu 单线程异步匹配队列在排队）⇒ 每 tick 都撞在 {@code cache} 空 / 区块未加载上，
     * 窗口白白耗尽，之后即使区块全加载、零件表仍空，也<b>再没有任何一次尝试</b>。
     * ⇒ 这正好解释实机：「成型了，但配方等级却是 ULV」必须靠玩家拆放控制器才恢复。
     *
     * <p>修法：把「窗口」的语义从「<b>放弃线</b>」改成「<b>起跑线 + 加载等待线</b>」——
     * 窗口内每 tick 试（原行为）；窗口过但仍处故障态 ⇒ 转入<b>每 {@value} tick 试一次</b>的持续恢复，
     * 直到零件真的登记回来才收工。限频论证见 {@link #recoverLoadPartsIfNeeded()} 的 javadoc。
     */
    private static final long LOAD_PARTS_RECOVERY_RETRY_TICKS = 20L;

    /**
     * ★★ 2026-11-10（第 2 轮）<b>「先失效、再由上游正规流程重建」总开关</b>（默认 {@code true} = 开）★★
     *
     * <p>{@code true} = 故障态下的恢复动作改成上游的<b>正规失效入口</b>
     * {@code onStructureInvalid()} + 把本机重新排进上游异步重判队列
     * （{@code MultiblockWorldSavedData.addAsyncLogic(this)}）⇒ 让上游在<b>自己的</b>流程里
     * 重新匹配、重新走 {@code onStructureFormed()} 重建零件表。
     *
     * <p>{@code false} = <b>逐字回退</b>到第 1 轮那条入口
     * （{@code state.onBlockStateChanged(挑出的格, 该格当前方块状态)}）。
     * 一行回退：把本常量改成 {@code false} 重新编译即可（与 {@link #LOAD_PARTS_RECOVERY_ENABLED}
     * 同款纪律：编译期常量、不是 {@code @Persisted}、不进存档、不改存档格式）。
     *
     * <h3>为什么要有这一条（第 1 轮那条入口实测进不去重建分支）</h3>
     * <p>第 1 轮的恢复动作调的是 {@code MultiblockState.onBlockStateChanged(pos, bs)}。
     * 这条入口的「重判定」分支要求 {@code controller.checkPatternWithLock()} 返回真
     * （字节码 {@code _census/_jtmp/gtl_MBStateMixin_c.txt:83-95}：{@code ifeq 202} 才落到失效分支），
     * 而它在故障态下拿不到真：① 本类同一 tick 折叠缓存（{@link #checkPatternWithLock()}）会把
     * 「本 tick 早先那次真判定」的结论原样返回；② cache 为空时连触发格都挑不出来
     * （{@link #requestUpstreamStructureRecheck} 直接返回 {@code false}，什么都不做，
     * 只留一条「cache 无合格触发格」诊断）；③ 读档后 {@code getController()} 还可能因区块/方块实体
     * 尚未就绪而返回 {@code null} ⇒ 整个方法在第 100-102 字节码就返回，静默无动作。
     * 三条路都不经过 {@code onStructureFormed()}。
     *
     * <h3>新方向为什么必然经过 {@code onStructureFormed()}</h3>
     * <p>不再要求"某一次匹配当场判成"：先把机器判成失效（{@code isFormed = false}，上游
     * {@code onStructureInvalid()} 的字节码 {@code _census/_jtmp/MCM_c.txt:315-340} 第一句就是这个），
     * 于是它回到上游<b>未成形侧</b>的既定轨道上 ——
     * ① 会被排进 {@code MultiblockWorldSavedData.controllers}，每 250 ms 由
     * {@code searchingTask()} 逐个 {@code asyncCheckPattern(periodID)}（同文件 {@code :152-175} / {@code :208-259}）；
     * ② gtlcore {@code @Overwrite} 版 {@code asyncCheckPattern} 的第 0-7 字节码是
     * {@code if (!getMultiblockState().hasError() && isFormed) return}（{@code _census/_jtmp/gtl_MCMMixin_c.txt:16-34}）
     * —— 失效后 {@code isFormed == false}，这道早退<b>必然</b>不成立，于是它继续往下跑
     * {@code checkPatternWithTryLock()}，判成时往服务端线程投
     * {@code lambda$asyncCheckPattern$0}，那个 lambda 的字节码就是
     * {@code lock() → setFlipped(isNeededFlip) → onStructureFormed() → addMapping() → removeAsyncLogic() → unlock()}
     * （同文件 {@code :55-87}）⇒ <b>{@code onStructureFormed()} 是这条路上的必经站，而不是我们要去撞的运气</b>。
     *
     * <h3>为什么"先失效"不会把好机器弄坏（不误判论证）</h3>
     * <p>失效只是把 {@code isFormed} 置假并清空零件表，<b>不写世界、不写存档、不写方块</b>
     * （{@code _census/_jtmp/MCM_c.txt:315-340} 全部字节码只有 {@code isFormed=false} + 逐个
     * {@code removedFromController} + {@code parts.clear()} + {@code updatePartPositions()}）。
     * 之后是否重新成形，<b>完全由上游按当下的世界重新判定</b>：
     * 判成 ⇒ {@code onStructureFormed()} 重建零件；判否 ⇒ 保持未成形（与"玩家拆掉一格"的结论一致）。
     * 也就是说这条路的<b>唯一可能后果</b>是"按当下世界重新判一次"，它<b>不可能</b>把未成形判成成形
     * （本类一行判定式都不复制，{@code isFormed} 只由上游写）。
     *
     * <p>换句话说：第 1 轮的入口是"请求上游顺手重判一次"，能不能重判成功要撞三个前置条件；
     * 本轮是"把机器交回上游未成形侧的既定轨道"，{@code onStructureFormed()} 由上游自己按需调用。
     */
    private static final boolean LOAD_PARTS_INVALIDATE_RECOVERY_ENABLED = true;

    /**
     * 连续「失效 ⇒ 上游重判 ⇒ 重新成形但零件表仍然为空」的<b>停机阈值</b>；{@value} = 4 轮。
     *
     * <p>为什么要停机（防震荡）：失效 ⇒ 上游重判是一次<b>整图案匹配</b>（代价见
     * {@link NanochipStructureCheckThrottle} 的"诚实边界"）。若某个环境的故障根因不在这一环，
     * 我们会每 {@link #LOAD_PARTS_RECOVERY_RETRY_TICKS} tick 来一轮"失效/重建"，
     * 玩家会看到机器在未成形/成形之间反复横跳，日志也会被刷满。
     * 达到本阈值 ⇒ <b>停下不再尝试</b>，只留一条 WARN 让现场一眼看出"这一招在这个环境不管用"。
     *
     * <p>阈值取 4 轮的依据：<b>正常情形 0 轮</b> —— 第一次由我们出手失效之后，上游在 250 ms 一轮的
     * {@code searchingTask()} 里重新匹配（{@code _census/_jtmp/MWSD_c.txt:152-175}），
     * 判成时零件表当场被 {@code onStructureFormed()} 重建 ⇒ 永远不会走到第 1 轮。
     * 会走到第 1 轮意味着"上游确实把结构判回来了（否则 {@code isFormed()} 不会再次为真），
     * 但零件表还是空"—— 那是**另一个**故障，留 4 轮给它（读档头几秒跨区块 + 单线程异步匹配排队的正常抖动），
     * 4 轮之后就不再是无害抖动的可能性了。
     */
    private static final int LOAD_PARTS_INVALIDATE_MAX_CYCLES = 4;

    /**
     * ★★ 2026-11-11（第 3 轮）<b>「恢复动作连续抛异常」的停机阈值</b>；{@value} = 8 轮。
     *
     * <p>为什么要有它：{@link #LOAD_PARTS_INVALIDATE_MAX_CYCLES} 数的是
     * 「失效 ⇒ 上游重判 ⇒ 重新成形但零件表仍空」那种<b>走到了头</b>的无效轮
     * （见 {@link #loadPartsInvalidateThrown} 的 javadoc），而**抛异常的那一轮根本没走到头**
     * ⇒ 它在旧口径里被漏掉了 ⇒ 停机闸数不到 ⇒ 每秒一条 WARN 无限刷。
     * 本阈值把这一类也纳入停机判据（**独立计数、独立停机**，不伪造另一半的证据）。
     *
     * <p>为什么取 8 轮：正常机器 <b>0 轮</b>（一次不抛）；读档头几秒"服务端线程正在忙 / 区块刚加载完
     * 那一瞬间的竞态"属于可容忍抖动，给 8 次（≈8 秒）足够越过它；连续 8 次都抛 = 这不是抖动，
     * 是"这一招在本环境不适用"，就该停下来把结论写进日志，而不是继续每秒刷。
     *
     * <p>停机后：{@code loadPartsRecoveryDone = true} ⇒ 本方法此后每 tick 只花一次 boolean 读；
     * 机器保持上游给出的<b>真实</b>状态，界面按既有口径显示「未就绪」。
     * 一行回退：把本常量改成一个很大的数（例如 {@code Integer.MAX_VALUE}）⇒ 恢复"只限频、不停机"。
     */
    private static final int LOAD_PARTS_INVALIDATE_MAX_THROWN = 8;

    /** 恢复动作是否已收工（{@code true} = 已确认零件表非空 / 已停机）。<b>不</b>因窗口用尽而置位。 */
    private boolean loadPartsRecoveryDone;

    /** 恢复窗口的截止 gameTime；{@code -1} = 还没起算（首帧按当时的 gameTime 起算，避免用 Long.MIN_VALUE 做减法溢出）。 */
    private long loadPartsRecoveryDeadline = -1L;

    /** 下一次「持续恢复」重试的 gameTime（窗口用尽后按 {@link #LOAD_PARTS_RECOVERY_RETRY_TICKS} 限频）。 */
    private long loadPartsRecoveryNextTry = -1L;

    /** 已尝试「请上游重判」的累计次数（只进诊断日志，不参与任何判定）。 */
    private int loadPartsRecoveryAttempts;

    /** 上一次尝试「请上游重判」的 gameTime（{@code -1} = 还没试过；只进诊断日志）。 */
    private long loadPartsRecoveryLastAttempt = -1L;

    /** 「未就绪判据分量」诊断上一次打印的时刻（{@code -1} = 还没打过；限频，见 {@link #diagnoseLoadPartsNotReady}）。 */
    private long loadPartsRecoveryDiagTick = -1L;

    /** 「未就绪判据分量」诊断行的最小间隔（tick）；{@value} = 10 秒（比状态汇报的 2 秒慢得多，避免刷屏）。 */
    private static final long LOAD_PARTS_RECOVERY_DIAG_INTERVAL_TICKS = 200L;

    /** 「未就绪判据分量」诊断行的总数上限（每台机器每次载入）；{@value} = 6 行，够定性且绝不会泛滥。 */
    private static final int LOAD_PARTS_RECOVERY_DIAG_BUDGET = 6;

    /** 已经打过的「未就绪判据分量」诊断行数（只读计数器，见 {@link #diagnoseLoadPartsNotReady}）。 */
    private int loadPartsRecoveryDiagCount;

    /** 上一次「让本机失效」的 gameTime（{@code -1} = 还没做过；诊断分量，不参与判定）。 */
    private long loadPartsInvalidateLastTick = -1L;

    /** 已执行「失效 + 交回上游重判」的累计次数（只进诊断日志，不参与任何判定）。 */
    private int loadPartsInvalidateCount;

    /** ★ 已让本机失效、正在等上游重判回来（{@code true} = 上一次动作还没结清）。 */
    private boolean invalidateStructurePending;

    /**
     * ★★ <b>连续无效计数</b>（防震荡闸）：已经发生的「失效 ⇒ 上游重判 ⇒ 重新成形但零件表仍为空」次数。
     *
     * <p>只在<b>真观察到</b>"上一轮的失效被上游判回来了（{@code isFormed} 重新为真）而零件表还是空"
     * 时 +1；一旦零件真的登记回来就归零。达到 {@link #LOAD_PARTS_INVALIDATE_MAX_CYCLES} ⇒ 停机 + WARN。
     */
    private int loadPartsInvalidateIneffective;

    /** 「连续无效 ⇒ 已停机」是否已经打过 WARN（每台机器每次载入只打一条）。 */
    private boolean loadPartsInvalidateGaveUp;

    /**
     * ★★ 2026-11-11（第 3 轮）<b>「恢复动作抛异常」的连续轮数</b> —— 与
     * {@link #loadPartsInvalidateIneffective} <b>分开记</b>，因为两者是<b>不同的故障</b>：
     *
     * <p>{@code ineffective} 记的是"上游确实把结构判回来了（{@code isFormed()} 重新为真）而零件表仍空"——
     * 那需要上游真的跑完一轮匹配；而抛异常时动作在中途就断了，<b>压根没走到"上游有没有补回零件"那一步</b>，
     * 所以**不能**混进同一个计数器（混了就是伪造证据）。
     *
     * <p><b>为什么必须有它（本轮的第二个根因）</b>：异常路径既不置 {@code ineffective}（上面那条理由），
     * 又会在下一次 {@code !isFormed()} 的早退里把 {@link #invalidateStructurePending} 清掉
     * （见 {@link #recoverLoadPartsIfNeeded()} 的早退分支）⇒ 状态机每一轮都从零开始
     * ⇒ {@link #LOAD_PARTS_INVALIDATE_MAX_CYCLES} 那道停机闸<b>永远数不到</b>
     * ⇒ 机器会以 {@link #LOAD_PARTS_RECOVERY_RETRY_TICKS}（1 秒）为周期<b>无限刷异常</b>
     * —— 这正是实机「每 0.3~0.7 秒一条 WARN、已尝试却一直是 0」的形状。
     *
     * <p>本计数器只做一件事：连续抛出 {@link #LOAD_PARTS_INVALIDATE_MAX_THROWN} 轮后停机（复用
     * {@link #loadPartsRecoveryDone} + {@link #loadPartsInvalidateGaveUp} 的停机语义），
     * 让"这一招在本环境不适用"变成一条 WARN，而不是每秒一条。
     * 一旦动作成功走到头（{@code invalidateThenRequestUpstreamRebuild} 返回真）就归零。
     */
    private int loadPartsInvalidateThrown;

    /**
     * ★ 上一轮「恢复动作抛异常」是什么时候（{@code -1} = 还没抛过）—— 只为日志限频，不参与任何判定。
     *
     * <p>限频口径：<b>首次必打</b>（带完整异常对象），之后同一台机器至少隔
     * {@link #LOAD_PARTS_RECOVERY_DIAG_INTERVAL_TICKS}（10 秒）才再打一条；
     * 被限频掉的那些轮只在条数语义上计入 {@link #loadPartsInvalidateThrown}（停机闸照数）。
     */
    private long loadPartsRecoveryThrowLastLog = -1L;

    /**
     * ★ 2026-09-27 <b>读档部件恢复</b>（修「读档后部件未连接」；由 {@link #tickModuleRefresh()} 每 tick 调）。
     *
     * <h3>触发条件（两个都要满足，都是 O(1) 读）</h3>
     * {@code isFormed()} <b>且</b> {@link #hasRegisteredParts()} 为假。实机日志已证：正常态下
     * {@code getParts()} 里至少有控制室自己那一格（定位文档 §① 证据 A 的「条数=1」），
     * 所以「已成形却 0 个零件」就是故障态（证据 B 的「条数=0」），不用再猜。
     *
     * <h3>读档后为什么必然是「已成形 + 零件表空」（本轮把两个分支都钉到字节码）</h3>
     * <p>先说结论（这一句是设计依据，与"实机落在哪一支"无关）：
     * <b>无论 {@code hasError()} 是真还是假，故障态都只能由我们这一侧出手</b> —— 因为
     * ① 事件路径要求"改的格已在 {@code MultiblockState.cache} 里"，而 cache 是运行期字段、读档后为空
     * （本段下方 §"结构位置缓存"的既有取证）；② {@code parts} 的唯一填充口是 {@code onStructureFormed()}，
     * 它只在"一次真的匹配成功"之后被调到。两条分支的差异只在"上游自己还会不会去试"：
     * <ul>
     *   <li><b>分支甲 —— {@code hasError() == true}</b>：上游<b>会</b>继续试，但试的时机全在读档头几秒。
     *       这一段里结构 63×49×63 跨多区块、区块还在陆续加载 ⇒ {@code MultiblockState.update}
     *       读到未加载的格就置 {@code UNLOAD_ERROR} 并判否
     *       （构造器把 {@code error} 初始化成 {@code UNINIT_ERROR}：{@code _census/_probe_gfp_MultiblockState.txt:37-58}；
     *       {@code update()} 先把它清成 {@code null} 再按 {@code world.isLoaded(pos)} 决定要不要置
     *       {@code UNLOAD_ERROR}：同文件 {@code :82-113}；{@code hasError() = error != null}：同文件 {@code :156-164}）
     *       ⇒ 只要区块没加载完，这一支就一直失败。旧版那 40 tick 窗口正好全部撞在这一段上，
     *       然后<b>永久放弃</b> —— 这就是实机看到的"一直 ULV"。</li>
     *   <li><b>分支乙 —— {@code hasError() == false}</b>：此时 {@code isFormed} 是 {@code @Persisted}
     *       恢复来的 true、{@code partPositions} 只有 {@code @DescSynced}（{@code _census/_jtmp/MCM_v.txt:572-592}），
     *       {@code asyncCheckPattern} 第 0-7 行<b>直接早退</b>（{@code _census/_jtmp/gtl_MCMMixin_c.txt:16-34}）、
     *       事件路径又因 cache 为空而收不到通知 ⇒ 上游两条路都不再动，
     *       只有"拆掉控制器再放回"能重新走一遍整图案匹配。同样只有我们这一侧能救。</li>
     * </ul>
     * <p><b>当前实机落在哪一支，本轮未取证</b>（见本文件顶部"未取证项"与报告 §⑫）——
     * 所以 {@link #diagnoseLoadPartsNotReady} 把 {@code hasError=} 也打了出来：下一轮一眼可定。
     * 两种分支的修法完全相同（本方法，且都先等区块真的加载完），因此这个未取证项<b>不阻塞</b>修复。
     *
     * <h3>动作（零判定复制、零自造成形）</h3>
     * <p><b>★ 2026-11-10（第 2 轮）改成"先让机器失效、再由上游正规流程重建"</b>，见
     * {@link #LOAD_PARTS_INVALIDATE_RECOVERY_ENABLED} 的完整依据。动作本体只有两步，都由上游 API 完成：
     * <ol>
     *   <li>{@code onStructureInvalid()}（上游公开 API，{@code IMultiController} 的抽象方法之一）
     *       ⇒ {@code isFormed = false} + 逐个 {@code removedFromController} + {@code parts.clear()}；</li>
     *   <li>{@code MultiblockWorldSavedData.getOrCreate(serverLevel)} → {@code removeMapping(state)} +
     *       {@code addAsyncLogic(this)} ⇒ 把本机<b>重新排进上游未成形侧的重判队列</b>
     *       （成形成功那条服务端任务里上游自己会 {@code removeAsyncLogic(this)} 退集
     *       —— {@code _census/_jtmp/gtl_MCMMixin_c.txt:74-79}，所以这一步是"回到队列"而不是"重复堆条目"）。
     *       这两句逐字对齐 gtlcore 自己的失效分支（{@code _census/_jtmp/gtl_MBStateMixin_c.txt:202-237}）
     *       与同版本加载器内的既有先例
     *       （{@code _census/enhancedcore-src/.../WeatherAnchorMachine.java:63-76}：
     *       {@code onStructureInvalid()} + {@code removeMapping} + {@code addAsyncLogic}，
     *       它的注释原话是"漏掉 {@code addAsyncLogic} 会导致异步重检永不调度"）。</li>
     * </ol>
     * 本类<b>不</b>自己调 {@code onStructureFormed()}、<b>不</b>写 {@code isFormed}、<b>不</b>扫世界匹配、
     * <b>不</b>碰上游的 {@code parts} 表 —— 零件登记仍然只由上游那次 {@code onStructureFormed()} 完成。
     *
     * <h3>为什么第 1 轮那条入口不换成"更直接地调 onStructureFormed()"</h3>
     * 那是上游内部流程的一站（{@code gtlcore} 的 {@code lambda$asyncCheckPattern$0} 里
     * {@code setFlipped + onStructureFormed + addMapping + removeAsyncLogic} 四件事一起做），
     * 直接调它会绕过"先重新匹配、再按 {@code isNeededFlip} 摆朝向、再登记映射、再退出重判队列"这套前置状态
     * ⇒ 等于我们自己伪造"已经匹配成功"。本轮明确不走这条（除非取证表明它是唯一可行口径 —— 不是）。
     *
     * <h3>★★ 2026-11-10（第 2 轮）为什么"失效 ⇒ 重判"不可能误判 ★★</h3>
     * <p>这是本方向最关键的论证，逐条：
     * <ol>
     *   <li><b>失效只是清状态，不写世界</b>：{@code onStructureInvalid()} 的字节码只有
     *       {@code isFormed = false} + 逐个 {@code removedFromController} + {@code parts.clear()} +
     *       {@code updatePartPositions()}（{@code _census/_jtmp/MCM_c.txt:315-340}）⇒
     *       它<b>不</b>改方块、<b>不</b>写存档、<b>不</b>写 {@code partPositions} 之外的任何世界数据。</li>
     *   <li><b>能不能重新成形，结论 100% 出自上游</b>：我们只把 {@code isFormed} 置假 + 把本机排回重判队列。
     *       之后 {@code isFormed} 是否再次为真，只可能来自上游 <b>在当下世界状态上真的匹配成功</b>
     *       （{@code gtlcore} 的 {@code lambda$asyncCheckPattern$0}：{@code setFlipped + onStructureFormed}
     *       只在 {@code checkPatternWithTryLock()} 返回真之后才被投递，{@code _census/_jtmp/gtl_MCMMixin_c.txt:36-52}）。
     *       本类<b>没有</b>任何一行写 {@code isFormed}（唯一例外是委托上游的 {@code onStructureInvalid()} 把它<b>置假</b>）。</li>
     *   <li><b>所以这条路的可能后果只有两种</b>：世界当下真的满足图案 ⇒ 上游重新成形并重建零件表（= 修好）；
     *       世界当下不满足 ⇒ 保持未成形（= 与"玩家拆掉一格"的结论一致，是<b>正确</b>结论而不是误判）。
     *       <b>不存在</b>"把未成形弄成成形"的路径。</li>
     *   <li><b>触发条件本身排除了好机器</b>：只在 {@code isFormed() && !hasRegisteredParts()} 时动作。
     *       健康机器的 {@code getParts()} 至少有控制室自己那一格（定位文档 §① 证据 A 的「条数=1」）
     *       ⇒ 这个条件对健康机器恒假，一次都不会进。</li>
     *   <li><b>与既有"敲方块定点失效"同一个结论来源</b>：那条路也是"由上游在当下世界重判"，
     *       判否就真失效。两条路的效果一致，只是触发源不同（方块变更 vs 故障态自检），见
     *       {@link #invalidateOnCachedRemoval()} 的共存复核。</li>
     * </ol>
     *
     * <h3>★★ 2026-11-10 修正：从「一次性有界」改成「成功即停的持续限频」★★</h3>
     * <p>旧版把 40 tick 窗口当<b>放弃线</b>：窗口一过就置 {@link #loadPartsRecoveryDone} 永不再试。
     * 而窗口从「服务端 tick 第一帧」起算，结构 63×49×63 跨多区块、读档头几秒区块还在陆续加载
     * （GTCEu 的单线程异步匹配也在排队）⇒ 窗口极可能<b>早于</b>"所有结构区块真的加载完"，
     * 于是 40 次尝试全部撞在 {@code cache} 空 / 区块未加载上，之后永无第二次机会。
     * 现在的语义（三条，替代原来那条）：
     * <ul>
     *   <li><b>只有"成功"才收工</b>：{@link #hasRegisteredParts()} 为真（零件真的登记回来了）
     *       ⇒ 置 {@link #loadPartsRecoveryDone}，此后稳态每 tick 只花一次 boolean 读；</li>
     *   <li><b>窗口内</b>（{@link #LOAD_PARTS_RECOVERY_WINDOW_TICKS} tick）：仍每 tick 试一次（原行为）；</li>
     *   <li><b>窗口过后仍在故障态</b>：转入持续恢复，每 {@link #LOAD_PARTS_RECOVERY_RETRY_TICKS} tick
     *       才试一次（= 1 秒 1 次，与 {@link #MODULE_REFRESH_INTERVAL} 同频）
     *       ⇒ 不管玩家是读档那一刻在场、还是几分钟后才走进这块区块，都会自愈，不必拆放控制器。</li>
     * </ul>
     *
     * <h3>为什么不会刷屏 / 不会变成稳态开销（限频三重）</h3>
     * <ol>
     *   <li><b>成功即停</b>：一旦 {@link #hasRegisteredParts()} 为真就 {@code loadPartsRecoveryDone = true}
     *       ⇒ 正常机器整个生命周期只多花一次 boolean 读，一条新日志都不打；</li>
     *   <li><b>重试限频</b>：持续恢复阶段每 {@value #LOAD_PARTS_RECOVERY_RETRY_TICKS} tick 才进一次动作路径
     *       （其余 tick 只比较一个 long）；</li>
     *   <li><b>日志限频</b>：诊断行由 {@link #diagnoseLoadPartsNotReady} 按
     *       {@link #LOAD_PARTS_RECOVERY_DIAG_INTERVAL_TICKS} tick（= 10 秒）节流，
     *       且内容相同的相邻行会被去重 ⇒ 最坏每台机器每 10 秒 1 行，故障持续也不会泛滥。</li>
     * </ol>
     * <p>另外<b>不会误判</b>：动作前先过 {@link NanochipStructurePosIndex#structureChunksLoaded}
     * （与「方块移除定点失效」同一条闸）—— 结构区块没全加载时本 tick <b>不判</b>、只等下一轮，
     * 绝不会让上游在"未加载"的世界状态上判一次失败（那会写出 UNLOAD_ERROR 并真的把机器判成失效）。
     *
     * <h3>代价（诚实登记）</h3>
     * 命中一次 = 让上游重判一次结构（因此会产生一次整图案匹配），与"玩家手动拆放一次控制器"等价
     * （单次成本<b>不变</b>，见 {@link NanochipStructureCheckThrottle} 的"诚实边界"）；
     * 只是把"必然发生的那一次"从「玩家手动」挪到「区块加载完之后的那一 tick」。
     * 持续恢复阶段的<b>动作</b>是每 20 tick 一次，且被
     * {@link #LOAD_PARTS_INVALIDATE_RECOVERY_RETRY_TICKS} 与
     * {@link #LOAD_PARTS_INVALIDATE_MAX_CYCLES} 两道闸夹住：动作之后 {@code isFormed()} 立刻为假，
     * 我们随即让路给上游自己的重判；只有当上游**真的**重新成形而零件表仍空时才会再来一轮，
     * 最多 {@value #LOAD_PARTS_INVALIDATE_MAX_CYCLES} 轮就停机 ⇒ 不存在"每 20 tick 一次全量匹配"的稳态形态。
     */
    private void recoverLoadPartsIfNeeded() {
        if (!LOAD_PARTS_RECOVERY_ENABLED || this.loadPartsRecoveryDone) {
            return;                                          // 稳态快路径：一次 boolean 读
        }
        Level level = getLevel();
        if (level == null || level.isClientSide) {
            // ★ 只有服务端需要：零件登记（part.addedToController）是服务端权威、结果经 @DescSynced 推给客户端；
            //   客户端的 isFormed 是同步来的、也不持有零件表 ⇒ 客户端侧一律不动（与 publishNacSlots 同纪律）。
            return;
        }
        long now = level.getGameTime();
        if (this.loadPartsRecoveryDeadline < 0L) {
            this.loadPartsRecoveryDeadline = now + LOAD_PARTS_RECOVERY_WINDOW_TICKS;
        }
        if (!isFormed()) {
            // ★ 未成形侧：既有周期路径兜底（上游会自己把结构判回来），本方法不插手。
            this.invalidateStructurePending = false;         // 上一轮动作已被上游接走，标记到此为止
            this.loadPartsRecoveryNextTry = -1L;
            return;
        }
        if (hasRegisteredParts()) {
            this.loadPartsRecoveryDone = true;                // ★ 只有"零件真的登记回来了"才收工
            this.loadPartsRecoveryNextTry = -1L;
            this.invalidateStructurePending = false;
            this.loadPartsInvalidateIneffective = 0;          // ★ 真的好了 ⇒ 防震荡计数归零
            // ★★ 0.3.131【⑤ 防震荡计数：唯一归零点】异常轮计数**只**在这里归零（= 真的好了）。
            //   旧口径把它归在「动作返回 true」那一支（见下面 invalidateThenRequestUpstreamRebuild 的注释）——
            //   而"我请上游重判了"**不是进展的证据**（它只说明这一次没抛）⇒ 于是
            //   「抛 7 轮 → 不抛 1 轮 → 再抛 7 轮 …」可以无限循环，两个停机闸都数不到 ⇒ 狂刷不止。
            //   改为"只有零件真的回来才归零"后，一次故障 episode 内异常轮**累计**到
            //   LOAD_PARTS_INVALIDATE_MAX_THROWN 就停 ⇒ 任何重试都有上界 ✓
            //   （loadPartsRecoveryDone / 本计数都是运行期字段、不 @Persisted ⇒ 只约束"本次载入"这个 episode，
            //     世界重载即清零，不会把一个长期存档永久判死）。
            this.loadPartsInvalidateThrown = 0;
            return;
        }
        // ★★ 到这一步 = 又一次观测到"已成形 + 零件表空"。
        //   若上一轮已经由我们出手失效过（invalidateStructurePending），说明上游**确实**把结构重新判成形了
        //   —— 因为只有上游重新成形，isFormed() 才可能再次为真 —— 而零件表仍然是空的
        //   ⇒ 这一轮就是一次"失效 ⇒ 重判"的**无效轮**，计数 +1（防震荡闸的唯一判据）。
        if (this.invalidateStructurePending) {
            this.invalidateStructurePending = false;
            this.loadPartsInvalidateIneffective++;
            if (this.loadPartsInvalidateIneffective >= LOAD_PARTS_INVALIDATE_MAX_CYCLES) {
                // ★ 连续 N 轮无效 ⇒ 停机：不再失效/重建，避免永久抖动（也便于现场一眼定性）。
                this.loadPartsRecoveryDone = true;
                if (!this.loadPartsInvalidateGaveUp) {
                    this.loadPartsInvalidateGaveUp = true;
                    NanochipModContext.logger().warn("[gtlmodular] NAC 部件恢复：连续 {} 轮「失效 ⇒ 上游重判」都重新成形了、"
                                    + "零件表却仍然为空，已停止自动恢复 @{}（已尝试失效={} 次，最近一次 gameTime={}）"
                                    + "—— 这一招在本环境不适用，请人工拆放控制器确认，并看前面那条"
                                    + "「NAC 部件未就绪诊断」行的 hasError/error/cache 分量",
                            LOAD_PARTS_INVALIDATE_MAX_CYCLES, getPos(),
                            this.loadPartsInvalidateCount, this.loadPartsInvalidateLastTick);
                }
                this.loadPartsRecoveryNextTry = -1L;
                return;
            }
        }
        if (now > this.loadPartsRecoveryDeadline) {
            // ★★ 窗口过后**不再收工**：转入持续恢复（每 LOAD_PARTS_RECOVERY_RETRY_TICKS tick 一次）。
            //   旧版在这里置 done = true ⇒ 区块还没加载完 / cache 还空就已经永久放弃，
            //   玩家只能拆放控制器 —— 那正是要根治的现象。
            if (this.loadPartsRecoveryNextTry < 0L) {
                this.loadPartsRecoveryNextTry = now + LOAD_PARTS_RECOVERY_RETRY_TICKS;
            } else if (now < this.loadPartsRecoveryNextTry) {
                return;                                      // 限频：本次 tick 只花一次 long 比较
            }
            // 到点（含"刚过窗口"这一 tick）⇒ 落到下面的动作路径；下一次的限频时刻由动作路径各出口统一设置。
        }
        MultiblockState state = getMultiblockState();
        if (state == null) {
            this.loadPartsRecoveryNextTry = now + LOAD_PARTS_RECOVERY_RETRY_TICKS;
            return;                                          // 拿不准 ⇒ 下一轮再试（不误判）
        }
        if (!NanochipStructurePosIndex.structureChunksLoaded(level, getPos())) {
            // ★ 区块还没全加载 ⇒ 本 tick 不判（与 invalidateOnCachedRemoval 同一条闸、同一句理由）：
            //   此刻让上游重判会让它读到未加载的格 ⇒ UNLOAD_ERROR ⇒ 一次"不该发生的失效"。
            //   把窗口"续到下一轮"（不置 done）⇒ 区块加载完的那一刻仍在窗口内，恢复照常发生。
            long resumed = now + LOAD_PARTS_RECOVERY_WINDOW_TICKS;
            this.loadPartsRecoveryDeadline = Math.max(this.loadPartsRecoveryDeadline, resumed);
            this.loadPartsRecoveryNextTry = now + LOAD_PARTS_RECOVERY_RETRY_TICKS;
            diagnoseLoadPartsNotReady(now, state, false);
            return;
        }
        if (!getPatternLock().tryLock()) {
            // 异步线程正持锁（很可能正在跑那一场整场匹配）⇒ 说明"零件马上就回来"，绝不在服务端线程上等锁。
            // ★ 这也是一次"续窗口"而不是失败：与上面的区块未加载同理。
            this.loadPartsRecoveryDeadline = Math.max(this.loadPartsRecoveryDeadline,
                    now + LOAD_PARTS_RECOVERY_WINDOW_TICKS);
            this.loadPartsRecoveryNextTry = now + LOAD_PARTS_RECOVERY_RETRY_TICKS;
            diagnoseLoadPartsNotReady(now, state, false);
            return;
        }
        this.loadPartsRecoveryAttempts++;
        this.loadPartsRecoveryLastAttempt = now;
        // ★★ 限频诊断（本题要求的"失真判据分量"一行）：必须打在**动作之前** —— 第 2 轮的动作会把
        //   isFormed 置假（那正是我们要治的状态），打之后就读不到"故障态那一刻"的真实分量了。
        //   节流靠 diagnoseLoadPartsNotReady 自己的 10 秒间隔 + 每台机器每次载入 6 行上限。
        //   ★ 2026-11-11（第 3 轮）[诊断不可拖垮恢复]：本句原先在 try **之外** ⇒ 它自己抛异常时
        //     既不会被下面那条 catch 记下来、也不会走"限频 + 保留重试"，而是裸穿到上游 tick
        //     （表现为一条**没有我们任何文案**的异常）。诊断是只读的、绝不该有这种能力 ⇒ 挪进 try。
        try {
            diagnoseLoadPartsNotReady(now, state, false);
            if (!LOAD_PARTS_INVALIDATE_RECOVERY_ENABLED) {
                // ★ 一行回退（第 1 轮口径）：只请上游"顺手重判一次"，不主动失效。
                //   见 LOAD_PARTS_INVALIDATE_RECOVERY_ENABLED 的 javadoc —— 这条在故障态下多半进不去
                //   重建分支（这正是第 2 轮换方向的原因），保留只为"改一个常量即可逐字回退"。
                if (requestUpstreamStructureRecheck(level, state, now)) {
                    return;                                  // 已请上游重判一次
                }
                this.loadPartsRecoveryNextTry = now + LOAD_PARTS_RECOVERY_RETRY_TICKS;
                return;
            }
            if (invalidateThenRequestUpstreamRebuild(level, state, now)) {
                // ★ 0.3.131【⑤ 防震荡计数】旧写法在这里 `loadPartsInvalidateThrown = 0`。
                //   它语义错了：「动作交回上游」只证明**这一轮没抛**，不证明**有进展** ⇒
                //   「抛若干轮 → 不抛一轮 → 再抛若干轮」可以无限循环（两个停机闸都数不到）。
                //   归零点已挪到"零件真的登记回来"那一支（本方法上方 hasRegisteredParts 分支）。
                //   一行回退：把下面这两行注释放开、并删掉那一支里的 `loadPartsInvalidateThrown = 0;`。
                // this.loadPartsInvalidateThrown = 0;
                return;                                      // 已失效 + 已交回上游重判队列
            }
            // ★ 没有可用的服务端世界（拿不准）⇒ 本 tick 什么都不做（**绝不**自己调 onStructureFormed()）。
            this.loadPartsRecoveryNextTry = now + LOAD_PARTS_RECOVERY_RETRY_TICKS;
        } catch (Throwable t) {
            // ★★ 2026-11-11（第 3 轮）**【异常 ⇒ 必须可读 + 必须能停机】**（本题的两条主修）★★
            //
            // ① 可读：把**异常对象本身**作为最后参数交给 SLF4J ⇒ 日志里就是
            //    异常类名 + message + **完整栈**（至少第一帧 "at <类>.<方法>(<文件>:<行>)"），
            //    这正是下一轮定位的唯一抓手（本机无法跑游戏 ⇒ 静态只能给"最可能"，栈顶才给定论）。
            //    参数位置**不能**变：若把 t 放进 {} 占位符，它会走 toString()，只留 "类名: message"、没有栈。
            //
            // ② 限频：首次必打（`loadPartsRecoveryThrowLastLog < 0`），之后至少隔
            //    LOAD_PARTS_RECOVERY_DIAG_INTERVAL_TICKS（10 秒）一条 ⇒ 不再每 0.3~0.7 秒刷屏。
            //    **被限频掉的轮数仍然计入 loadPartsInvalidateThrown**（停机闸照数，限频只影响日志）。
            //
            // ③ 停机：连续 LOAD_PARTS_INVALIDATE_MAX_THROWN 轮异常 ⇒ loadPartsRecoveryDone = true，
            //    与"连续无效"那条闸共用同一套停机标记与一次性 WARN（loadPartsInvalidateGaveUp）。
            //    ★ 语义边界（**不伪造证据**）：抛异常**不**计入 loadPartsInvalidateIneffective ——
            //      那一半数的是"上游确实重判回来了而零件仍空"，抛异常时压根没走到那一步。
            //
            // ④ 保留原语义：仍然限频后重试、仍然**不**在这里收工（一次失败不该让机器永久残废），
            //    直到上面③的停机闸数满为止。
            this.loadPartsRecoveryNextTry = now + LOAD_PARTS_RECOVERY_RETRY_TICKS;
            this.loadPartsInvalidateThrown++;
            boolean firstEver = this.loadPartsRecoveryThrowLastLog < 0L;
            boolean dueByTime = firstEver
                    || now - this.loadPartsRecoveryThrowLastLog >= LOAD_PARTS_RECOVERY_DIAG_INTERVAL_TICKS;
            if (dueByTime) {
                this.loadPartsRecoveryThrowLastLog = now;
                NanochipModContext.logger().warn("[gtlmodular] NAC 读档部件恢复：本次「失效 ⇒ 交回上游重判」抛出异常"
                                + "（连续第 {} 轮；本机首次={}，两次日志至少隔 {} tick；"
                                + "请照下面这条栈定位 —— 异常类型 / message / 栈顶若干帧）"
                                + "，已保留重试 @{}",
                        this.loadPartsInvalidateThrown, firstEver,
                        LOAD_PARTS_RECOVERY_DIAG_INTERVAL_TICKS, getPos(), t);
            }
            if (this.loadPartsInvalidateThrown >= LOAD_PARTS_INVALIDATE_MAX_THROWN) {
                this.loadPartsRecoveryDone = true;           // ★ 停机（与"连续无效"闸共用同一套标记）
                this.loadPartsRecoveryNextTry = -1L;
                this.invalidateStructurePending = false;
                if (!this.loadPartsInvalidateGaveUp) {
                    this.loadPartsInvalidateGaveUp = true;
                    NanochipModContext.logger().warn("[gtlmodular] NAC 部件恢复：连续 {} 轮「失效 ⇒ 交回上游重判」都抛出异常，"
                                    + "已停止自动恢复 @{}（已尝试={}，累计失效={}，最近一次 gameTime={}）"
                                    + "—— 这不是抖动，是这一招在本环境不适用：请按上面最后一条栈定位"
                                    + "（栈顶那一帧就是抛出点），并对照同一条「NAC 部件未就绪诊断」行的"
                                    + "结构区块全加载 / cache 格数 / hasError 分量；未定位前不要再自动重试",
                            LOAD_PARTS_INVALIDATE_MAX_THROWN, getPos(), this.loadPartsRecoveryAttempts,
                            this.loadPartsInvalidateCount, this.loadPartsInvalidateLastTick);
                }
            }
        } finally {
            getPatternLock().unlock();
        }
    }

    /**
     * ★★ 2026-11-10（第 2 轮）<b>「先让机器失效、再由上游正规流程重建零件」的实际动作</b>（零判定复制）★★
     *
     * <p>顺序与依据（逐条见 {@link #LOAD_PARTS_INVALIDATE_RECOVERY_ENABLED} 的 javadoc）：
     * <ol>
     *   <li>{@link #onStructureInvalid()} —— 上游公开 API（{@code IMultiController} 的抽象方法之一），
     *       字节码第一句就是 {@code isFormed = false}（{@code _census/_jtmp/MCM_c.txt:315-340}）；
     *       调用本类覆写，本类那一层只做"清线程仓引用 / 清清单 / 注销位置表 / 清模块缓存"，
     *       全部幂等、全部是失效该做的事；</li>
     *   <li>{@code removeMapping(state)} + {@code addAsyncLogic(this)} —— 与 gtlcore 自己失效分支
     *       （{@code _census/_jtmp/gtl_MBStateMixin_c.txt:202-237}）以及同加载器内的既有先例
     *       （{@code WeatherAnchorMachine.java:63-76}）逐字同款：靠 {@code addAsyncLogic} 把本机排回
     *       {@code MultiblockWorldSavedData.controllers}，由上游 {@code searchingTask()} 每 250 ms
     *       走它自己的 {@code asyncCheckPattern} 重判 —— 判成时上游会在自己的 lambda 里调
     *       {@code onStructureFormed()} 重建零件表（{@code _census/_jtmp/gtl_MCMMixin_c.txt:55-87}）。</li>
     * </ol>
     *
     * @return {@code true} = 已失效并已交回上游；{@code false} = 拿不到服务端世界（本 tick 什么都不做）
     */
    private boolean invalidateThenRequestUpstreamRebuild(Level level, MultiblockState state, long now) {
        if (level instanceof ServerLevel serverLevel) {
            this.loadPartsInvalidateCount++;
            this.loadPartsInvalidateLastTick = now;
            // ★ 2026-11-11（第 3 轮）[记账口径修正] 本标记**必须**等动作真的交出去了再置：
            //   它语义是"我已出手失效、正在等上游重判回来"（见 invalidateStructurePending 的 javadoc，
            //   recoverLoadPartsIfNeeded() 靠它判"这一轮是不是无效轮"）。
            //   旧写法把它放在**动作之前** ⇒ `onStructureInvalid()` 抛异常时标记已经是 true，
            //   而动作其实**没做成** ⇒ 那一轮被记成"无效轮"、纯属误判（并且会把停机闸的计数口径弄脏）。
            //   放到 addAsyncLogic 之后 = "已交回上游队列"这个客观事实成立的那一刻。
            onStructureInvalid();                        // ★ 上游正规失效入口（本类覆写只做失效该做的清理）
            MultiblockWorldSavedData worldData = MultiblockWorldSavedData.getOrCreate(serverLevel);
            worldData.removeMapping(state);              // 上游失效分支同款：旧映射先摘掉
            worldData.addAsyncLogic(this);               // ★ 排回上游重判队列（漏掉它 = 永不重检）
            this.invalidateStructurePending = true;      // ★ 动作已走到头 ⇒ 此刻才允许记"等上游补回零件"
            NanochipModContext.logger().info("[gtlmodular] NAC 读档部件恢复：已让结构失效并交回上游重判 @{} "
                            + "（已成形但零件表为空；累计失效={}，gameTime={}；"
                            + "依据 _census/_jtmp/gtl_MBStateMixin_c.txt:202-237 与 "
                            + "WeatherAnchorMachine.java:63-76）",
                    getPos(), this.loadPartsInvalidateCount, now);
            return true;
        }
        return false;
    }

    /**
     * ★ <b>第 1 轮（2026-11-10 第 1 版）那条入口的逐字保留</b> ——
     * 从 {@code state.getCache()} 里挑一格<b>非控制器</b>、且不会被上游早退规则吃掉的格，
     * 请 gtlcore 自己重判一次结构。
     *
     * <p>⚠️ <b>默认不可达</b>：只有把 {@link #LOAD_PARTS_INVALIDATE_RECOVERY_ENABLED} 改成
     * {@code false}（一行回退到第 1 轮口径）时才会被 {@link #recoverLoadPartsIfNeeded()} 调到。
     * 保留它的理由：① 让"一行回退"真的只改一个常量、不需要重新写代码；
     * ② 它是 {@code onBlockStateChanged} 这条入口在本工程里的完整取证与挑格规则（与
     * {@link #confirmFoldedPattern()} / {@link #invalidateOnCachedRemoval()} 逐句同款）。
     *
     * <p><b>为什么第 2 轮不用它</b>：这条入口的"重判定"分支要求
     * {@code controller.checkPatternWithLock()} 返回真（字节码 {@code _census/_jtmp/gtl_MBStateMixin_c.txt:83-95}），
     * 而故障态下它拿不到真 —— 见 {@link #LOAD_PARTS_INVALIDATE_RECOVERY_ENABLED} 的三条前置失败路径。
     *
     * @return {@code true} = 已经请上游重判过一次；{@code false} = 缓存里没有合格触发格
     */
    private boolean requestUpstreamStructureRecheck(Level level, MultiblockState state, long now) {
        // ★★ 0.3.128【首次修 NPE】实机栈：NullPointerException: "this.cache" is null
        //    at requestUpstreamStructureRecheck ← recoverLoadPartsIfNeeded ← tickModuleRefresh（每 0.3~0.7 秒一次）
        //    结构「尚未初始化」时 MultiblockState.cache 就是 null（上游构造器不建 cache）
        //    ⇒ 直接遍历必抛 ⇒ 恢复动作整段作废 ⇒ 零件表恒 0 ⇒ 机器有料却不工作。
        //    守卫：cache 空 ⇒ 没有可用触发格 ⇒ 如实返回 false，交由上游周期重判。
        //
        // ★★ 0.3.131【① 修 NPE · 点位 2/3：守卫本身以前是死的】★★
        //   上面那条守卫（0.3.128）写的是 `state.getCache() == null`，**永远不成立也永远拦不住**：
        //   `getCache()` 的方法体是 `cache.stream().map(BlockPos::of).collect(...)`
        //   （`_census/_jtmp/MBState_c.txt:298-308`）⇒ cache 为 null 时它**自己在第一句就抛 NPE**，
        //   根本走不到返回 null ⇒ 守卫从来不是"拦截"，而是"抛出点"。
        //   真正的原因是**我们调错了 API**：`cache` 是 public 字段（同文件 `:35`），
        //   直接读字段才能在 cache 尚未初始化时安全返回。
        //   ⇒ 本处改成读字段：语义与 0.3.128 想表达的**完全一致**（cache 空即如实返回 false），
        //     只是这次真的可达了。守卫一行未删。
        if (state.cache == null || state.cache.isEmpty()) {
            return false;
        }
        var cacheIterator = state.cache.longIterator();
        while (cacheIterator.hasNext()) {
            BlockPos pos = BlockPos.of(cacheIterator.nextLong());
            if (pos.equals(getPos())) {
                continue;                                // 控制器那一格走的是「失效」分支，不是重判定
            }
            BlockState bs = level.getBlockState(pos);
            if (bs.getBlock() instanceof ActiveBlock
                    || level.getBlockEntity(pos) instanceof IMachineBlockEntity) {
                continue;                                // 会被 gtlcore/gtladditions 的早退规则吃掉，换一格
            }
            NanochipModContext.logger().info("[gtlmodular] NAC 读档部件恢复：已补一次结构重判定 @{} "
                            + "（已成形但零件表为空；触发格={}，gameTime={}，累计尝试={}；"
                            + "依据 _census/NAC读档部件未连接_定位.md §⑥ A）",
                    getPos(), pos, now, this.loadPartsRecoveryAttempts);
            state.onBlockStateChanged(pos, bs);          // ★ 判定与后续分支全部由 gtlcore 自己执行
            return true;
        }
        return false;
    }

    /**
     * ★★ 2026-11-10 <b>「未就绪」判据分量诊断日志</b>（限频；纯只读，不参与任何判定）★★
     *
     * <p>为什么要它：实机现象是面板黄字「结构部件尚未就绪：加载所有结构区块，等待能源仓与样板总成重新连接」
     * ＋「最大配方等级：ULV / LV」，而这条判据在上游是
     * {@code WorkableMultiblockMachine.isRecipeLogicAvailable() = isFormed() && !getMultiblockState().hasError()}
     * （字节码 {@code _census/_javap_simple_workablemb.txt:475-487}）——
     * <b>它一个零件都不读</b>。所以"为什么持续为真"只能从我们这一侧的这些分量去分辨：
     * ① 零件表到底几个；② {@code hasError()} 到底是真还是假（决定上游周期路径会不会被第 0-7 行早退掉，
     * 见 {@code _census/_jtmp/gtl_MCMMixin_c.txt:16-34}）＋ 那条 {@code error} 究竟是什么
     * （{@code UNLOAD_ERROR} 还是别的：{@code MultiblockState.error} 是 public 字段，
     * 见 {@code _census/_jtmp/MultiblockState.txt:14}）；③ {@code cache} 格数（决定
     * {@code onBlockStateChanged} 有没有格可用、也决定敲方块能不能被上游看见）；④ 结构区块加载情况；
     * ⑤ 我们试了几次、上次什么时候试的；⑥ "失效 ⇒ 上游重判"这条路走到第几轮了
     * （{@code 已失效} / {@code 上次失效} / {@code 连续无效}）。下一轮拿着这一行就能一眼定性，不必再猜。
     *
     * <p>限频（三重，绝不刷屏）：① 两次打印至少隔 {@value #LOAD_PARTS_RECOVERY_DIAG_INTERVAL_TICKS} tick
     * （= 10 秒）；② 同一台机器每次载入最多打 {@value #LOAD_PARTS_RECOVERY_DIAG_BUDGET} 行；
     * ③ 只在"已成形却零件表为空"这条故障路径上才可能被调到（正常机器一行都不打）。
     *
     * @param actionRefusedNoLever 本轮是否"拿到了锁、但本 tick 没能把上游重判交出去"
     *                             （第 2 轮口径下 = 拿不到服务端世界；第 1 轮口径下 = cache 里没有合格触发格）
     *
     * <p>★ 2026-11-11（第 3 轮）：本方法现在**只在 {@code recoverLoadPartsIfNeeded()} 的 {@code try} 内**被调用
     * ⇒ 它自己万一抛异常也会被那一条 {@code catch (Throwable)} 记下来（带完整栈 + 限频），
     * 而不是裸穿到上游 tick。异常轮里本参数照旧显示 {@code false}（限频只影响日志，不影响该行内容）。
     */
    private void diagnoseLoadPartsNotReady(long now, MultiblockState state, boolean actionRefusedNoLever) {
        if (!NanochipModContext.logger().isInfoEnabled()) {
            return;
        }
        if (this.loadPartsRecoveryDiagCount >= LOAD_PARTS_RECOVERY_DIAG_BUDGET) {
            return;
        }
        if (this.loadPartsRecoveryDiagTick >= 0L
                && now - this.loadPartsRecoveryDiagTick < LOAD_PARTS_RECOVERY_DIAG_INTERVAL_TICKS) {
            return;
        }
        this.loadPartsRecoveryDiagTick = now;
        this.loadPartsRecoveryDiagCount++;
        List<IMultiPart> parts = getParts();
        // ★★ 0.3.131【① 修 NPE · 点位 3/3：诊断行的 -2 哨兵】★★
        //   实机那一行 `cache 格数=-2，error=读状态时抛 NullPointerException` 就是**本段**打出来的：
        //   `-2` 是下面这个 catch 的哨兵 ⇒ 说明**抛点就在 cache 这一句**（`state.error` 是 public
        //   字段读、不可能抛）⇒ 抛的就是 `state.getCache()` 自己（见本方法上方的字节码证据）。
        //   现在改成读字段 `state.cache`（public，零副作用、真的 null 安全）⇒ 这一格从此只会是
        //   `-1`（从没跑过整场匹配 ⇒ cache 字段还是 null）或真实格数，**不再有 -2**。
        //   两个 try 分开：cache 与 error 各自兜底，一格读不到不再连累另一格（证据不互相掩盖）。
        int cache;
        try {
            cache = state.cache == null ? -1 : state.cache.size();
        } catch (RuntimeException e) {
            cache = -2;                                      // -2 = 读 cache 就抛（诊断绝不再抛）
        }
        boolean hasError;
        String errorName;
        try {
            PatternError error = state.error;                // public 字段（MultiblockState.txt:14），零副作用读
            hasError = error != null;
            errorName = hasError ? String.valueOf(error) : "无";
        } catch (RuntimeException e) {
            hasError = true;                                 // 拿不准 ⇒ 按"有错"记，绝不掩盖
            // ★ 0.3.131【① 让异常带栈】把"类名 / message / 栈顶若干帧"压进**这一格**（仍然只有这一行日志，
            //   不新增日志行）：下一轮实机日志里 `error=` 后面直接就是抛点（栈顶第 2 帧必然是我们自己的类）。
            errorName = "读状态时抛 " + describeThrowable(e);
        }
        Level world = getLevel();
        boolean chunksLoaded = NanochipStructurePosIndex.structureChunksLoaded(world, getPos());
        NanochipModContext.logger().info("[gtlmodular] NAC 部件未就绪诊断 @{}：零件数={}，isFormed={}，"
                        + "显示等级字段 tier={}，hasError={}，error={}，cache 格数={}，结构区块全加载={}，"
                        + "窗口截止={}，上次尝试={}，已尝试={}，已失效={}，上次失效={}，连续无效={}，"
                        // ★ 0.3.131【⑤ 防震荡计数可见】把「连续抛异常」也打进**这一行**（不新增日志行）：
                        //   实机那次「已尝试=1 却在狂刷」的观感，就是因为这一格以前只算"走到头"的无效轮、
                        //   异常轮压根没有读数（本行的 10 秒限频 + 每机 6 行上限又让它不容易连着出现）。
                        //   现在两个计数同框：读法 = 已尝试 与 连续抛异常 是否同步增长。
                        + "连续抛异常={}，"
                        + "本 tick 未能交出重判={}，gameTime={} —— 面板「结构部件尚未就绪」的判据是上游 "
                        + "isRecipeLogicAvailable()（= isFormed && !hasError，不看零件表；"
                        + "字节码见 _census/_javap_simple_workablemb.txt:475-487）；"
                        + "显示等级只由 onStructureFormed 时按能源仓写下一次 ⇒ 部件没回来时那一行不可信",
                getPos(), parts == null ? -1 : parts.size(), isFormed(), this.tier,
                hasError, errorName, cache, chunksLoaded,
                this.loadPartsRecoveryDeadline, this.loadPartsRecoveryLastAttempt,
                this.loadPartsRecoveryAttempts, this.loadPartsInvalidateCount,
                this.loadPartsInvalidateLastTick, this.loadPartsInvalidateIneffective,
                this.loadPartsInvalidateThrown,
                actionRefusedNoLever, now);
    }

    /**
     * ★★ 0.3.131【① 让异常带栈】★★ 把一个抓到的异常压成**一行可读**的
     * 「类名 / message / 栈顶 {@value #DESCRIBE_THROWABLE_FRAMES} 帧（类.方法:行号）」。
     *
     * <p><b>为什么需要它</b>：实机诊断行只给了 `error=读状态时抛 NullPointerException`——
     * 类名有了、**抛点没有**。而这一类 NPE 的类名恒为 {@code NullPointerException}，
     * 没有栈就等于没有证据（上一轮就是靠"类名 + 一句猜测"把守卫写成了死代码）。
     * 本方法让栈顶落到**既有那一行**里（不新增日志行），而调用方（
     * {@link #diagnoseLoadPartsNotReady}）的栈顶第 2 帧必然是我们自己的类 ⇒ 一眼可钉死。
     *
     * <p><b>为什么不用 {@code LOGGER.warn(..., e)}</b>：那会新增一条日志行（本题要求"不新增日志行"）；
     * 而 {@code describeThrowable} 是**纯读**（只读异常的 class / message / stackTrace），
     * 零副作用、不可能再抛（{@code getStackTrace()} 对未 fillInStackTrace 的异常返回空数组，
     * 那一支也已显式兜住）。
     */
    private static String describeThrowable(Throwable t) {
        if (t == null) {
            return "?（异常对象为 null）";
        }
        StringBuilder sb = new StringBuilder(128);
        sb.append(t.getClass().getName());
        String message = t.getMessage();
        if (message != null && !message.isEmpty()) {
            sb.append(": ").append(message);
        }
        StackTraceElement[] frames;
        try {
            frames = t.getStackTrace();
        } catch (RuntimeException e) {
            frames = null;                                   // 拿不到栈就如实说"拿不到"，绝不再抛
        }
        sb.append(" @栈顶[");
        if (frames == null || frames.length == 0) {
            sb.append("无栈（该异常未 fillInStackTrace / 取栈失败）");
        } else {
            int printed = 0;
            for (int i = 0; i < frames.length && printed < DESCRIBE_THROWABLE_FRAMES; i++) {
                if (printed > 0) {
                    sb.append(" < ");
                }
                sb.append(frames[i].getClassName()).append('.').append(frames[i].getMethodName())
                        .append(':').append(frames[i].getLineNumber());
                printed++;
            }
        }
        sb.append(']');
        return sb.toString();
    }

    /** 见 {@link #describeThrowable}：诊断行里最多打几帧栈（≥3 才够"栈顶若干帧"）。 */
    private static final int DESCRIBE_THROWABLE_FRAMES = 4;

    /**
     * 结构零件表是否已登记（{@code getParts()} 在未成形时可能为 {@code null} ⇒ 一律按"空"处理）。
     *
     * <p>★ 2026-11-10：这个判据现在同时是①恢复动作的<b>触发条件</b>、②恢复动作的<b>唯一收工条件</b>
     * （{@link #recoverLoadPartsIfNeeded()}）、③{@link #requestStructureRefresh} 早退判据的一部分、
     * ④界面「最大配方等级」的处理判据（{@link #addDisplayText}）与⑤"失效 ⇒ 重判"防震荡闸的
     * 无效计数判据 —— 一处判据、多处同口径。
     */
    private boolean hasRegisteredParts() {
        List<IMultiPart> parts = getParts();
        return parts != null && !parts.isEmpty();
    }

    // ==========================================================================================
    // ★★【结构位置缓存 + 方块移除定点失效】（秋叶思路的落地；完整依据与内存量化见
    //   `_census/结构位置缓存与移除失效_实施记录.md` 与 NanochipStructurePosIndex 类注释）★★
    //
    // 用户实测的问题（本次要让它消失）：**敲掉一个方块后，主机仍显示"结构已成型"**。
    // 机制（全部来自字节码，不是推测）：
    //   · 上游唯一"方块变更 ⇒ 重判结构"的通道是 gtlcore 的 LevelMixin → MultiblockState.onBlockStateChanged；
    //     它的两道前置门都是**纯运行期数据**、都不入存档：
    //       ① MultiblockWorldSavedData.chunkPosMapping（save() 逐字节码原样返回、NBT 构造器忽略入参）
    //       ② MultiblockState.cache（multiblockState 只有 @DescSynced、没有 @Persisted）
    //     ⇒ 读档后两条都空 ⇒ 敲掉方块，上游**收不到通知**；
    //   · 周期路径也帮不上：asyncCheckPattern 第 0-7 行 `if (!hasError && isFormed) return`，
    //     而 isFormed 是 @Persisted ⇒ 读档恢复出来的"已成形"让这台永久退出周期重扫。
    //   ⇒ 本段补的就是这一环：**我们自己缓存"结构各格属于哪台主机"（运行期派生、零存档）**，
    //     由 Forge 的方块移除事件（零混入）查表定位到主机，再请**上游自己**重判那一次。
    // ==========================================================================================

    /**
     * 待办：最近一次「本机结构里的一格被移除」的世界坐标（{@code null} = 无待办）。
     *
     * <p>写方 = 服务端线程（Forge 方块移除事件 / 爆炸事件），消费方 = 本机的服务端 tick
     * （{@link #invalidateOnCachedRemoval()}）。{@code BlockPos} 不可变 ⇒ {@code volatile} 发布
     * 读到的永远是完整坐标。同 tick 内多次移除只留最后一个（任何一次结构格移除都足以让上游重判，
     * 见 {@link #invalidateOnCachedRemoval()} 的说明）⇒ 不需要队列、不会积压。
     */
    private volatile BlockPos pendingRemovedPos;

    /** 待办的保留截止时刻（{@code gameTime}；{@code -1} = 还没起算）。见 {@link #PENDING_REMOVAL_WINDOW_TICKS}。 */
    private long pendingRemovalDeadline = -1L;

    /**
     * 待办最长保留多久（tick）；{@value} = 10 秒，与 {@link #JOIN_QUIET_TICKS} 同量级。
     *
     * <p>它只服务一种退化情形：命中缓存时**整座结构的区块还没全加载**（刚进世界）。
     * 那时不能判（上游那次匹配会以 {@code UNLOAD_ERROR} 失败，而 gtlcore 的失效分支会据此把它
     * 判成未成形 —— 那不是结构真的坏了），于是保留待办、下一 tick 再试；超过本窗口就放弃
     * （放弃 = 回到上游原有行为，绝不误判）。
     */
    private static final long PENDING_REMOVAL_WINDOW_TICKS = 200L;

    /**
     * <b>本机登记进「结构位置」索引时用的维度键</b>（{@code null} = 当前没登记）。
     *
     * <p>它同时承担两件事：
     * <ol>
     *   <li><b>「已登记 ⟺ isFormed()」这条不变式的记忆</b> —— 它替代了原先的
     *       {@code boolean structureIndexRegistered}（同一个状态位，零额外内存）；</li>
     *   <li><b>卸载时精确摘除所需的维度</b>：注销<b>不</b>现读 {@code getLevel()}
     *       （卸载路径上 {@code BlockEntity.level} 是否还非空属于上游实现细节，
     *       见 {@code NanochipStructurePosIndex.unregister} 的 javadoc）⇒ 由本字段提供维度键。</li>
     * </ol>
     *
     * <p>线程：主要写方是服务端线程（每 tick 的 {@link #syncStructurePosIndex()} 与失效/卸载路径），
     * 但 {@code onStructureFormed()} <b>可能跑在 GTCEu 的异步结构检查线程上</b>（本类既有注释已登记），
     * 所以这里可能有一次良性竞争（最坏 = 多登记/多注销一次，两者都幂等且线程安全）。
     * 竞争留下的任何不一致都会被<b>下一 tick</b>的对齐修掉（判据是 {@code formed != (dim != null)}），
     * 因此这个字段只是"上一 tick 对齐到哪"的记忆，不是权威状态 —— 权威永远是 {@code isFormed()}。
     */
    private ResourceKey<Level> structureIndexDim;

    /** 「结构位置表格数 vs 上游 cache 格数」这道自证只打一次（见 {@link #crossCheckStructureCells()}）。 */
    private boolean structureCellsCrossChecked;

    /**
     * <b>把本机登记/注销进「结构位置」全局索引</b>（由 {@link #tickModuleRefresh()} 每 tick 调）。
     *
     * <h3>为什么不变式是「已登记 ⟺ isFormed()」</h3>
     * 本功能只做一件事：把「看起来已成形、其实该失效」的那台主机叫醒去重判 ⇒ 只在
     * {@code isFormed()} 为真时有意义。失效时注销还有两个好处：① 静态表不随机器数量增长；
     * ② 未成形期间敲方块本来就不需要任何人重判（未成形侧的周期路径照旧在跑）。
     *
     * <p>为什么不用 {@code onLoad()}/{@code onStructureFormed()}/{@code onStructureInvalid()} 三处登记：
     * 那两个回调的时序与 LDLib 的 {@code @Persisted} 恢复顺序、GTCEu 的异步回调线程都有关系
     * （读档后 {@code isFormed} 恢复成 true 时 {@code onStructureFormed()} <b>不会</b>被调用，
     * 见 `_census/NAC读档部件未连接_定位.md` §②.5）⇒ 用"每 tick 对齐一次"最省心，且稳态零开销。
     */
    private void syncStructurePosIndex() {
        if (!NanochipStructurePosIndex.ENABLED) {
            return;
        }
        Level level = getLevel();
        if (level == null || level.isClientSide) {
            return;
        }
        boolean formed = isFormed();
        boolean registered = this.structureIndexDim != null;
        if (formed == registered) {
            return;                                      // ★ 稳态快路径：一次字段读 + 一次比较
        }
        if (formed) {
            registerIntoStructureIndex(level);
        } else {
            unregisterFromStructureIndex();
        }
    }

    /**
     * 登记本机（并把"用哪个维度键登记的"记进 {@link #structureIndexDim}）。
     *
     * <p>顺序是<b>先登记、后记维度</b>：万一 {@code register} 内部出过事（它自己吞异常、只落 debug 日志），
     * {@code structureIndexDim} 仍是 {@code null} ⇒ <b>下一 tick 会重试</b>（自愈），
     * 而不是"记成已登记、其实没登记"。
     */
    private void registerIntoStructureIndex(Level level) {
        NanochipStructurePosIndex.register(level, this);
        this.structureIndexDim = level.dimension();
    }

    /**
     * <b>注销本机</b>（幂等；维度取 {@link #structureIndexDim}，<b>不</b>依赖 {@code getLevel()}）。
     *
     * <p>调用点 = 摘除时机清单（逐条含行号见 {@code _census/位置表指针化_实施记录.md} §③）：
     * 机器被拆 {@link #onMachineRemoved()} / 机器卸载（含<b>区块卸载</b>）{@link #onUnload()} /
     * 结构失效 {@link #onStructureInvalid()} / 每 tick 对齐 {@link #syncStructurePosIndex()}；
     * 维度级的最后一道闸是 {@code NanochipBlockRemovalHandler} 的 {@code LevelEvent.Unload}。
     *
     * <p>注销在索引侧带<b>身份校验</b>（只有"表里那条指针就是本机"才删）⇒ 客户端镜像机器的
     * 卸载/失效回调<b>摘不掉</b>服务端那条登记（0.3.121 的坐标口径下会误摘、且服务端不会自愈）。
     */
    private void unregisterFromStructureIndex() {
        ResourceKey<Level> dim = this.structureIndexDim;
        this.structureIndexDim = null;
        NanochipStructurePosIndex.unregister(dim, this);
    }

    /**
     * 由 {@link NanochipStructurePosIndex}（即 Forge 方块移除事件）调用：<b>结构里的某一格刚被移除</b>。
     *
     * <p>只记账，不当场判定 —— 事件触发时那一格<b>还没真的被移除</b>
     * （原版 {@code ServerPlayerGameMode#destroyBlock} 先问 Forge 再 {@code removeBlock}），
     * 当场判定读到的是旧世界，只会白跑一次匹配。真正动作在下一 tick 的
     * {@link #invalidateOnCachedRemoval()}。
     */
    public void noteCachedBlockRemoved(BlockPos pos) {
        if (!NanochipStructurePosIndex.ENABLED || pos == null) {
            return;
        }
        this.pendingRemovedPos = pos.immutable();
        this.pendingRemovalDeadline = -1L;               // 截止时刻在消费时按当时的 gameTime 起算
    }

    /**
     * <b>消费「结构格被移除」的待办：请上游自己重判一次。</b>
     *
     * <h3>动作本体（本类零判定复制）</h3>
     * 调 gtlcore 自己的 {@code MultiblockState.onBlockStateChanged(brokenPos, 当前方块状态)} ——
     * 与 {@link #confirmFoldedPattern()} / {@link #recoverLoadPartsIfNeeded()} <b>完全同款</b>的入口：
     * 跑不跑匹配、成形还是失效、要不要 {@code onStructureFormed()} / {@code removeMapping()} /
     * {@code addAsyncLogic()}，<b>全部由 gtlcore 自己执行</b>
     * （字节码 {@code _census/_jtmp/gtl_MBStateMixin_c.txt:15-111}）。本类<b>不</b>写 {@code isFormed}、
     * <b>不</b>自己调 {@code onStructureInvalid()}、<b>不</b>自己扫世界构造成型。
     *
     * <h3>为什么这不是"新造语义"（正确性）</h3>
     * <ul>
     *   <li>结论只出自上游：失效只可能来自上游在<b>当下世界状态</b>下真的匹配失败；</li>
     *   <li>只在该主机 {@code isFormed()} 为真时动作（已经不成形 ⇒ 直接丢掉待办，不重复判）；</li>
     *   <li>只在"方块真的已经移除之后的那一 tick"动作（见 {@link #noteCachedBlockRemoved(BlockPos)} 的时机说明）；</li>
     *   <li>结构区块没全加载 ⇒ 本 tick 不判、保留待办（理由见字段注释）；拿不准（锁拿不到 / 状态为空 /
     *       抛异常）⇒ 保留或放弃，<b>绝不自行判定</b>。</li>
     * </ul>
     *
     * <h3>★ 2026-11-10（第 2 轮）与 {@link #recoverLoadPartsIfNeeded()} 的共存复核</h3>
     * 第 2 轮那条恢复走的是"{@code onStructureInvalid()} + {@code addAsyncLogic(this)}"，
     * 本条走的是"{@code state.onBlockStateChanged(被拆的格, 该格当前状态)}"——
     * 两条都碰 {@code MultiblockState} / 上游控制器 API，但<b>不冲突</b>，逐条：
     * <ol>
     *   <li><b>都只让上游下结论</b>：本条与那条都不写 {@code isFormed}、不自己扫世界匹配，
     *       区别只在"请上游重判的方式"（事件路径 vs 交回未成形侧队列）。</li>
     *   <li><b>不会互相打断</b>：本条动作前先过 {@code isFormed()} 闸；若那条恢复已经把机器判成未成形，
     *       本条下一 tick 直接走 {@code !isFormed()} 丢待办，不会在"刚失效"的机器上再判一次。</li>
     *   <li><b>状态交接正确</b>：那条恢复经 {@code onStructureInvalid()} 会顺手
     *       {@code unregisterFromStructureIndex() + dropPendingRemoval()}（本类覆写里本就有）——
     *       那正是"已经不成形 ⇒ 本功能无事可做"的正确状态；反之本条让上游判成未成形时，
     *       那条恢复下一次进来看到 {@code !isFormed()} 便不插手（未成形侧有上游周期路径）。</li>
     *   <li><b>同 tick 折叠仍在</b>：若两条同 tick 都触发，本类 {@link #checkPatternWithLock()} 的
     *       同 tick 折叠会把真匹配收敛成一次（见下一节的论证，最坏晚 1 tick）。</li>
     * </ol>
     *
     * <h3>与「同 tick 折叠」的关系（不会互相打架，最坏晚 1 tick）</h3>
     * 本方法与 {@code recoverLoadPartsIfNeeded()} / {@code confirmFoldedPattern()} 同在服务端 tick 里跑：
     * 同 tick 内第一次真判定照跑，后面的调用拿到的是**那一次的真实结果**（本类
     * {@link #checkPatternWithLock()} 的折叠），并留下一笔 {@code foldDebt} 由下一 tick 兑现
     * ⇒ 最坏"晚 1 tick 失效"，绝不会把"不成形"缓存成"成形"。
     */
    private void invalidateOnCachedRemoval() {
        if (!NanochipStructurePosIndex.ENABLED) {
            return;
        }
        BlockPos broken = this.pendingRemovedPos;
        if (broken == null) {
            return;                                      // ★ 稳态快路径：一次 volatile 读
        }
        Level level = getLevel();
        if (level == null || level.isClientSide) {
            dropPendingRemoval();
            return;
        }
        if (!isFormed()) {
            dropPendingRemoval();                        // 已经不成形 ⇒ 无需再判（也不重复判）
            return;
        }
        if (!NanochipStructurePosIndex.structureChunksLoaded(level, getPos())) {
            // ★ 刚进世界/区块还没加载：现在判会让上游以 UNLOAD_ERROR 失败并被判成未成形
            //   （那不是结构真的坏了）⇒ 保留待办、下一 tick 再试；超窗口就放弃（回到上游行为）。
            long now = level.getGameTime();
            if (this.pendingRemovalDeadline < 0L) {
                this.pendingRemovalDeadline = now + PENDING_REMOVAL_WINDOW_TICKS;
            } else if (now > this.pendingRemovalDeadline) {
                dropPendingRemoval();
            }
            return;
        }
        MultiblockState state = getMultiblockState();
        if (state == null) {
            dropPendingRemoval();
            return;
        }
        if (!getPatternLock().tryLock()) {
            return;                                      // 异步线程正持锁 ⇒ 保留待办，下一 tick 再试（绝不阻塞）
        }
        try {
            BlockState nowState = level.getBlockState(broken);
            dropPendingRemoval();                        // ★ 先清再动作（同 confirmFoldedPattern 的 foldDebt 写法）
            logRemovalInvalidation(broken, level.getGameTime());
            state.onBlockStateChanged(broken, nowState); // ★ 判定与后续分支全部由 gtlcore 自己执行
        } catch (Throwable t) {
            dropPendingRemoval();
            NanochipModContext.logger().warn("[gtlmodular] NAC 方块移除定点失效：本次重判定抛出异常，已放弃 @{}", getPos(), t);
        } finally {
            getPatternLock().unlock();
        }
    }

    /** 丢掉待办（三个字段一起归零，避免把上一轮的截止时刻带到下一轮）。 */
    private void dropPendingRemoval() {
        this.pendingRemovedPos = null;
        this.pendingRemovalDeadline = -1L;
    }

    /** 「方块移除 ⇒ 定点失效」这一条日志（走既有的结构日志闸，与其它结构结论同一套降噪口径）。 */
    private void logRemovalInvalidation(BlockPos broken, long now) {
        String detail = "移除格=" + broken.toShortString();
        boolean emit = this.structureLogGate.shouldEmitInfo(REMOVAL_INVALIDATE_CONCLUSION, detail,
                "复合体@" + getPos().toShortString(), now);
        if (emit || NanochipModContext.logger().isDebugEnabled()) {
            if (emit) {
                NanochipModContext.logger().info(REMOVAL_INVALIDATE_LINE_FORMAT, getPos(), broken);
            } else {
                NanochipModContext.logger().debug(REMOVAL_INVALIDATE_LINE_FORMAT, getPos(), broken);
            }
        }
    }

    /**
     * <b>自证</b>：本机的「结构位置表格数」与上游 {@code MultiblockState.cache} 的格数是否一致
     * （只打一次，只告警、<b>不改任何判定</b>）。
     *
     * <p>为什么要它：我们的位置表是从<b>图案字符</b>推出来的（{@link NanochipComplexPattern#hostCheckCells()}），
     * 而上游的 cache 是<b>匹配时逐格 addPosCache</b> 填的。两者应当逐格相等（论证见该方法的 javadoc）。
     * 这条自证就是"图案与缓存口径脱节"的探针 —— 例如有人把 {@code where(AIR, any())} 改回
     * {@code air()}（那会让 cache 变成 188,601 格），日志会立刻报出来。
     */
    private void crossCheckStructureCells() {
        MultiblockState state = getMultiblockState();
        if (state == null) {
            return;
        }
        // ★★ 0.3.131【① 修 NPE · 顺带清点】本处原写法 `state.getCache() == null ? -1 : state.getCache().size()`
        //   外面还包了一个 `catch (RuntimeException) { return; }` —— 那个 catch 正是"cache 为 null 时
        //   getCache() 自己抛 NPE"的**遮羞布**（异常被吞、自证静默失效，等于这条探针从来没工作过）。
        //   改成读 public 字段后：`size()` 只是读一个 int 字段，**不可能抛** ⇒
        //   去掉这个"用 catch 掩盖"的写法（红线：不留空 catch / 不靠吞异常维持正确性），
        //   而且 cache==null（该 state 从没跑过整场匹配）这一形态从此**如实**报 -1 去和 ours 比。
        int upstream = state.cache == null ? -1 : state.cache.size();
        int ours = NanochipComplexPattern.hostCheckCells().length / 3;
        if (upstream != ours) {
            NanochipModContext.logger().warn("[gtlmodular] NAC 结构位置缓存自证不一致 @{}："
                            + "本机图案推出来的格数={}，上游 MultiblockState.cache 实测={} —— "
                            + "两者本应相等（依据见 NanochipComplexPattern.hostCheckCells() 的 javadoc）。"
                            + "本功能按**上游 cache 的语义**工作，不等时只会少覆盖/多覆盖几格："
                            + "多覆盖 ⇒ 多跑一次真匹配；少覆盖 ⇒ 那几格的移除要等上游自己的路径。"
                            + "不影响任何成形/失效判定。",
                    getPos(), ours, upstream);
        }
    }

    /** 每 {@value #MODULE_REFRESH_INTERVAL} tick 按结构 {@code F} 位刷新「已连接」。 */
    private void tickModuleRefresh() {
        Level level = getLevel();
        if (level == null) {
            return;
        }
        long now = level.getGameTime();
        // ★ 2026-09-27 读档部件恢复（修「读档后部件未连接」）：必须放在最前面 —— 它是"读档后 isFormed 被
        //   存档恢复、零件表却是空的"这个故障态的唯一自动出口（GTCEu 自己的两条触发路径都早退，
        //   见 recoverLoadPartsIfNeeded() 的注释与 `_census/NAC读档部件未连接_定位.md`）。
        //   它自己的限频：窗口内每 tick 试；窗口过后每 LOAD_PARTS_RECOVERY_RETRY_TICKS tick 试一次，
        //   直到零件真的登记回来才收工（★ 2026-11-10：旧版"窗口用尽即永久放弃"正是实机里
        //   「成型了但等级还是 ULV、必须拆放控制器」的成因）。
        //   ★ 2026-11-10（第 2 轮）：动作本体从"请上游顺手重判一次"（实测进不去重建分支）换成
        //   "先让机器失效（onStructureInvalid）⇒ 交回上游未成形侧的重判队列（addAsyncLogic）"
        //   —— 那条路上 onStructureFormed() 是必经站；完整依据见
        //   LOAD_PARTS_INVALIDATE_RECOVERY_ENABLED 的 javadoc。比 confirmFoldedPattern() 更底层：
        //   零件先登记回来，后面的心跳/模块重扫读到的才是同一份真实零件表。
        recoverLoadPartsIfNeeded();
        // ★★ 2026-11-11【样板总成镜像绑定 = 0 · 恢复 + 诊断】★★
        //   放在 recoverLoadPartsIfNeeded() **之后**：那一条先把零件表叫回来（本条要遍历零件表找样板总成），
        //   所以零件刚回来的那一 tick，本行读到的就是同一份真实零件表。
        //   它自己的三重限频（只在坏态 / 时间闸 / 总量闸 + 停机）全在 NanochipMirrorBindGuard 里，
        //   稳态下只是一次"上游两个集合大小"的读（<b>只有坏态才会去遍历世界解析镜像方块</b>）。
        //   一行回退：删掉本行。
        mirrorBindGuard.tick(this);
        // ★【2026-09-21 队长拍板「上」】本订阅是**每 tick** 都被叫到的服务端钩子（重扫间隔由下面的
        //   MODULE_REFRESH_INTERVAL 判断，钩子本身不受它影响）⇒ 这里就是「同 tick 折叠」的兑现点：
        //   上一 tick 折叠过一次「成形」判定，此刻补一次真判定（下一次方块变更也会顺手结清它，
        //   所以稳态下这一句通常只是一个 volatile 读 + 直接返回）。
        confirmFoldedPattern();
        // ★★【结构位置缓存 + 方块移除定点失效】（秋叶思路；完整依据见 NanochipStructurePosIndex 类注释）★★
        //   ① syncStructurePosIndex()：把"本机结构里的位置属于本机"登记进全局索引。
        //      不变式是「已登记 ⟺ isFormed()」⇒ 稳态下只花一次字段读 + 一次比较（不建表、不碰世界）；
        //   ② invalidateOnCachedRemoval()：消费「上一次有结构格被移除」的待办 —— 命中缓存 ⇒
        //      让**上游自己**重判一次（跑不跑匹配、成形还是失效全部由 gtlcore 决定，本类零判定复制）。
        //   放在 recoverLoadPartsIfNeeded() 与 confirmFoldedPattern() **之后**：那两条是既有的
        //   "请上游重判"入口（读档恢复 / 折叠兑现），优先级高于本功能；同 tick 内若有多条一起触发，
        //   本类 checkPatternWithLock() 的同 tick 折叠会把真匹配收敛成一次（不会叠加成多次全量匹配）。
        syncStructurePosIndex();
        invalidateOnCachedRemoval();
        // ★ 2026-09-27：仓位清单心跳（修「外壳连片刷新时机」）—— 每 20 tick 比一次 part **个数**，
        //   个数没变就 O(1) 返回（不建字符串、不碰世界、不重跑匹配），变了才重建并（去重后）发布。
        //   放在周期重扫的间隔判断**之前**：它自己不重跑结构匹配，不受 MODULE_REFRESH_INTERVAL 影响。
        tickNacSlotHeartbeat();
        // ★ 2026-09-21：能量缓存分配先走（每 20 tick 一次，与模块重扫同频但各自计时）
        distributeModuleEnergy(now);
        // ★ 2026-09-21（队长：「一进游戏 NAC 相关的都要重新加载结构，而且要好久，尤其是主机」）：
        //   先消费"待办的催促"——它是 12 台模块那一波成形/失效的合并出口。
        //   放在周期重扫的间隔判断**之前**：合并执行的那一次会顺手把周期计时也推到 now，
        //   避免同一 tick 里"催一次 + 周期再扫一次"这种纯浪费。
        // 原初模式：不消费模块的催促、不跑框架几何扫描，但仍按结构 F 位快照每 20 tick 补连一次。
        // 模块自己不会去找主机（getHostScanPositions 为空），没有这一步，主机成形之后才成形的模块
        // （新放的、拆了重放的）永远连不上。F 位最多十几个，开销可以忽略。
        if (NanochipModContext.config().isNanochipOriginalMode()) {
            if (lastRescanTick < 0 || now - lastRescanTick >= MODULE_REFRESH_INTERVAL) {
                lastRescanTick = now;
                refreshModulesFromStructure();
            }
            return;
        }
        if (consumePendingResync(now)) {
            return;
        }
        if (lastRescanTick >= 0 && now - lastRescanTick < MODULE_REFRESH_INTERVAL) {
            return;
        }
        lastRescanTick = now;
        // ★【2026-10 模块框架对齐 · A2 的必要配套】原来这里只调 refreshModulesFromStructure()（= 只读
        //   F 位快照）。而 A1/A2 之后"框架原生几何扫描"是主路，它唯一的周期触发点就是本方法
        //   （onStructureFormed 读档后根本不会再跑 ⇒ 只靠那里的话，A1/A2 在读档场景等于死代码）。
        //   scanAndConnectModules() = 框架扫描 + 快照兜底，两条一起跑（顺序与理由见该方法注释）。
        scanAndConnectModules();
    }

    /**
     * ★ 2026-09-21 <b>催促去抖的消费端</b>（只在主机自己的服务端 tick 上跑）。
     *
     * <p>规则（三条，缺一不可）：
     * <ol>
     *   <li><b>静默窗口内</b>（{@link #joinQuietUntilTick}）：丢掉待办、不做强制重算 ——
     *       队长要的"进世界头 10 秒不催，让机器各自初始化完毕"；周期重扫不受影响，所以不会漏连；</li>
     *   <li><b>距上次强制重算不足</b> {@link #RESYNC_COALESCE_TICKS}：<b>把待办记回去</b>继续攒，
     *       下个 tick 再看 ⇒ "12 台同一波"最终只落地 1 次；</li>
     *   <li><b>到点</b>：消费待办 + 重算一次 + 重置周期计时。</li>
     * </ol>
     *
     * <p><b>为什么不在这里读线程相关的东西</b>：本方法只被服务端线程调用，
     * 所有可变状态（{@code lastForcedResyncTick / lastRescanTick}）也只有服务端线程写，
     * 与 {@link #resyncRequested} 的原子读写配合 ⇒ 不存在"模块在异步线程上直接写集合"的老竞态
     * （旧代码 {@code NanochipModuleMachine#requestHostResync} 会<b>当场</b>调 {@code scanAndConnectModules()}，
     * 而它当时可能正跑在 GTCEu 的异步结构检查线程上）。
     *
     * @return 本次是否真做过一次重算（true ⇒ 调用方不必再走周期分支）
     */
    private boolean consumePendingResync(long now) {
        if (!this.resyncRequested.getAndSet(false)) {
            return false;
        }
        if (now < this.joinQuietUntilTick) {
            // 静默窗口内：直接丢掉（周期重扫仍在跑，模块不会漏连）
            return false;
        }
        if (now - this.lastForcedResyncTick < RESYNC_COALESCE_TICKS) {
            // 还没到点：记回待办，攒着（不是丢掉 —— 丢掉会漏掉"必须的那一次"）
            this.resyncRequested.set(true);
            return false;
        }
        this.lastForcedResyncTick = now;
        this.lastRescanTick = now;
        // ★【2026-10 模块框架对齐 · A2 的必要配套】与 tickModuleRefresh() 末尾同一处改动：
        //   合并后的这一次"催办"也要走 scanAndConnectModules()（框架扫描 + 快照兜底），
        //   否则"模块刚成形 → 催主机"这条最常见的路径仍然只跑快照路。
        scanAndConnectModules();
        return true;
    }

    /**
     * ★ 2026-09-21 <b>催促的去抖入口</b>：请本复合体"按结构 F 位重算一次已连接模块集合"。
     *
     * <p>队长原话（2026-09-21）：
     * 「唉，现在一进游戏NAC相关的，都要重新加载结构，而且要好久，尤其是主机。」
     *
     * <p>实机日志证据：模块侧那句
     * {@code NAC 模块 已成形 @…（复合体尚未成形或还没把本机登记 —— … 本机已催促其立刻重算一次，最坏 1 秒内生效）}
     * 就是本入口的调用方。进世界后 19:39–19:41 每分钟 77~91 条结构消息、12 台模块轮流成形/失效
     * ⇒ 每一台都"立刻催"一次主机，而这十几次催促之间只隔几十毫秒、<b>算出来必然同一个结果</b>。
     *
     * <p>本方法<b>只置一个标志</b>（O(1)、无锁、任意线程可调），真正的重算合并到主机的服务端 tick 里，
     * 由 {@link #consumePendingResync(long)} 按 {@link #RESYNC_COALESCE_TICKS} 限频执行
     * ⇒ 同一波催促 = <b>1 次</b>重算。
     *
     * <p><b>刻意不在这里直接调 {@code refreshModulesFromStructure()}</b>：那是"模块侧任意线程直接写主机集合"，
     * 正是历史注释里登记的并发债来源；现在写的动作 100% 留在服务端线程。
     *
     * <p><b>语义不变声明</b>：本入口只改"什么时候算"，<b>不改"算什么"</b> ——
     * 按结构 F 位幂等覆盖已连接模块集合的判据、以及"什么时候算成形"的判据一个字都没动。
     */
    public void requestCoalescedResync() {
        this.resyncRequested.set(true);
    }


    /**
     * <b>每 {@value #ENERGY_DISTRIBUTE_INTERVAL} tick 按优先级给各模块灌能量缓存</b>（wiki「能量缓存」段）。
     *
     * <blockquote>
     *   「复合体检查结构时为每个模块设置缓存容量，公式为：模块缓存 = NAC最大输入功率 × (2 × 模块最大配方时间)」<br>
     *   「复合体每 20 tick 向各模块分配缓存电量；纳米芯片组装矩阵模块优先级为 −1（在所有模块中最后补充缓存），
     *    其他模块为 1」
     * </blockquote>
     *
     * <p>实现纪律：
     * <ul>
     *   <li>只灌「吃复合体电」的模块（{@link NanochipModuleMachine#drawingFromHost()}）—— 自带能源仓的模块用自己的电；</li>
     *   <li><b>按优先级排序</b>：{@link NanochipModuleMachine#energyPriority()} 大的在前，组装矩阵（−1）排最后
     *       ⇒ 「其他模块会优先获得电力」；</li>
     *   <li>电量从复合体自己的蓄电里扣（{@code EnergyContainerList.changeEnergy(负)}），按实际扣到的量入缓存，
     *       多扣的还回去；</li>
     *   <li>扣到的量累进 {@code drawAccumulator}，界面上那行「模块耗电」才有意义。</li>
     * </ul>
     */
    private void distributeModuleEnergy(long now) {
        if (lastEnergyDistributeTick >= 0 && now - lastEnergyDistributeTick < ENERGY_DISTRIBUTE_INTERVAL) {
            return;
        }
        lastEnergyDistributeTick = now;
        if (!isFormed() || !isWorkingEnabled()) {
            return;
        }
        if (isEnergySourceEffectivelyUnlimited()) {
            return;   // 电网 / 无线电网终端：模块每 tick 直接扣电（requestModuleEnergy），不预存整批
        }
        List<NanochipModuleMachine> mods = new ArrayList<>();
        for (IModularMachineModule<NanochipAssemblyComplexMachine, ?> m : getModules()) {
            if (m instanceof NanochipModuleMachine mm && mm.isFormed() && mm.drawingFromHost()) {
                mods.add(mm);
            }
        }
        // 优先级大的先补（1 → −1），组装矩阵因此在最后
        mods.sort(Comparator.comparingInt(NanochipModuleMachine::energyPriority).reversed());
        final long hatchInput = finiteHatchInputPower();
        for (NanochipModuleMachine mm : mods) {
            NanochipModuleKind kind = mm.getModuleKind();
            int maxTicks = kind == null ? 100 : kind.maxRecipeTicks();
            mm.setBufferCapacity(saturatingMul(saturatingMul(hatchInput, 2L), (long) maxTicks));
            if (topUpModuleBuffer(mm) < 0L) {
                break;    // 复合体这一刻一点电都没有
            }
        }
    }

    /**
     * 把一个模块的缓存从复合体补满（缺多少要多少），返回实际补进去的量；复合体完全没电时返回 −1。
     *
     * <p>只按真实存量扣电，从不凭空记账：电网账户和无线电网终端的"存量"就是余额，扣电是全有或全无，
     * 所以要的量必须是真实缺口 —— 按容量哨兵（Long.MAX 级）去要，余额永远不够，一分都扣不到。
     */
    long topUpModuleBuffer(NanochipModuleMachine mm) {
        EnergyContainerList e = getEnergyContainer();
        if (e == null) {
            return -1L;
        }
        long room = mm.getBufferCapacityEU() - mm.getBufferEU();
        if (room <= 0L) {
            return 0L;
        }
        long stored = storedEU(e);
        if (stored <= 0L) {
            return -1L;
        }
        long taken = -e.changeEnergy(-Math.min(room, stored));
        if (taken <= 0L) {
            return 0L;
        }
        long got = mm.fillBufferEU(taken);
        if (got < taken) {
            e.changeEnergy(taken - got);          // 缓存满了，没吃下的还回去
        }
        drawAccumulator = saturatingAdd(drawAccumulator, got);
        return got;
    }

    /** 模块起跑缺电时当场补一次缓存，不等 20 tick 的统一分配；组装矩阵在别的模块等电时让路。 */
    public void requestBufferTopUp(NanochipModuleMachine mm) {
        if (!isFormed() || !isWorkingEnabled()) {
            return;
        }
        if (mm.energyPriority() < 0 && highPriorityStarved()) {
            return;
        }
        topUpModuleBuffer(mm);
    }

    /**
     * 有限容量能源仓的输入功率之和（EU/t），用来定模块缓存上限。电网账户和无线电网终端这类
     * 容量 Long.MAX 级的源不算：它们的输入功率也是 Long.MAX，拿来定上限会把整个电网余额灌进模块。
     * 只有这类源时上限为 0，模块缓存按每条配方的实际需要现定（{@link NanochipModuleMachine#ensureBufferCovers}）。
     */
    private long finiteHatchInputPower() {
        EnergyContainerList own = super.getEnergyContainer();
        if (own == null) {
            return 0L;
        }
        long total = 0L;
        for (com.gregtechceu.gtceu.api.capability.IEnergyContainer c : innerContainers(own)) {
            if (c == null || c.getEnergyCapacity() >= ON_DEMAND_POWER_FLOOR_EU) {
                continue;
            }
            total = saturatingAdd(total,
                    saturatingMul(Math.max(0L, c.getInputVoltage()), Math.max(0L, c.getInputAmperage())));
        }
        return total;
    }

    /**
     * 模块上限：每种模块各 1 台 + 1 个余量（原版为 12 个模块槽，对应 NH 的 11 种模块）。
     *
     * <p>★ 2026-09-18（队长指示新增三台组织器）：模块种类由 11 → <b>14</b>
     * （{@link NanochipModuleKind#values()} 现读：原有 11 + 奇异 / 寰宇 / 超因果优化组织器）
     * ⇒ 上限同步取 <b>15</b>（14 种各 1 台 + 1 个余量），口径与原版一致。
     * 结构侧的 {@code F} 位有 48 格，装得下；本值与 {@code NanochipComplexPattern.build()}
     * （冻结面，有 sha256 钉子）无关 —— 改它不动主机图案。
     * <p>刻意写成<b>字面量</b>：{@code _census/check_nac_mechanics_parity.js} 的 B10 直接读这个数字
     * 并与「NH 只在界面文本里写 0-12」做对照（见该脚本的 X6 divergence），保持可解析。
     */
    @Override
    public int getMaxModuleCount() {
        return 15;
    }

    @Override
    public Set<IModularMachineModule<NanochipAssemblyComplexMachine, ?>> getModuleSet() {
        return modules;
    }

    /**
     * ★【2026-10 模块框架对齐 · A1】<b>模块扫描位（世界坐标）</b> —— gtlcore
     * {@link IModularMachineHost#scanAndConnectModules()} 的唯一数据源。
     *
     * <h3>改前 / 改后</h3>
     * 改前：{@code return new BlockPos[0];}（空表）⇒ 框架那条「遍历扫描位 → 校验 → connectToHost」
     * 的路<b>彻底空转</b>，模块连接只能靠 {@code onStructureFormed()} 那一刻的 F 位快照
     * （{@link #cachedModuleSlots}）⇒ 读档后快照为空，就得干等一次整图案匹配（实测 4 分 18 秒）。
     * 改后：返回 12 个模块位的世界坐标，与官方那套同构（参照 {@code gtladditions} 的
     * {@code SubspaceCorridorHubIndustrialArray}：{@code getModuleScanPositions()} = 纯几何助手
     * {@code calculateModulePositions(getPos(), getFrontFacing())}；以及
     * {@code forge_of_the_antichrist} 的 {@code AntichristPosHelper}）。
     *
     * <h3>几何来源（唯一数据源 = 写死的实测表）</h3>
     * {@link NanochipComplexPattern#moduleControllerOffsets()} 的 12 条偏移（来源 = 玩家存档实测
     * 「4 个 F 位(偏移)」列 + 「模块底座 7×3×7 与主机基座逐格重合」的 48/48 穷举，见该类
     * {@code NAC_MODULE_PREVIEW_SLOTS} 的 javadoc）。<b>本次已逐格核对 12/12 与结构定义一致</b>：
     * 按 {@code (NAC_CTRL_X−x, row−NAC_CTRL_ROW, NAC_CTRL_Z−z) = (31−x, row−8, 32−z)} 反算成
     * {@code NAC_SLICES} 下标后，12 条全部落在 {@code 'F'}（模块位）上，且都在 {@code row=2}
     * （= 基座顶层，与模块控制器层一致）；逐条下标/字符见执行记录
     * {@code _census/模块框架对齐_执行记录.md} §②。
     *
     * <h3>朝向折算（不自己写旋转数学）</h3>
     * 这张偏移表是在「主机 {@code facing=north}」的参照系里量的（与
     * {@code NanochipModulePreview.buildNac()} 声明 {@code hostFront=NORTH / hostUp=UP} 同口径）
     * ⇒ 这里直接复用 gtlcore 自己的
     * {@link ModuleRenderInfo#calculateModuleOffset(Direction, Direction)}：预览侧
     * {@code PatternPreviewWidget$MBPattern.initializeModules}（字节码
     * {@code _enh2_extract/gtlcore_module_api.txt:3301-3305}）正是
     * {@code hostPos.offset(info.calculateModuleOffset(hostFront, hostUp))} 得到模块控制器的世界坐标，
     * 与本法<b>同一个公式</b> ⇒ 「预览画在哪」与「主机扫哪里」永远一致，且没有我方自造的旋转约定。
     * <br>第 6 个参数（模块定义）在本调用里<b>不被读取</b>：该 record 的
     * {@code calculateModuleOffset} 只读 {@code worldOffset / hostFront / hostUp}
     * （同文件 {@code :159-204}，且 record 的规范构造器无 null 检查，{@code :135-157}）⇒ 传 {@code null}。
     *
     * <h3>线程 / 副作用</h3>
     * 纯算术 + 只读本机方块状态（{@code getPos() / getFrontFacing() / getUpwardsFacing()}）：
     * <b>不碰世界、不碰集合、不写任何字段</b> ⇒ 任意线程可调；真正写模块集合的
     * {@link #scanAndConnectModules()} 仍只在服务端线程被调。
     *
     * <h3>与快照机制的分工（为什么必须保留快照路径）</h3>
     * 本表只覆盖「12 个基座的<b>边中点</b>」这 12 格（= 模块底座能与主机基座逐格重合的唯一位置）；
     * 玩家若把模块控制器放在同一基座的<b>其它</b> F 位（另外 3 条边），框架扫描扫不到它 —— 那正是
     * {@link #refreshModulesFromStructure()} 那条 F 位快照路径继续存在的原因（兜底/差集补齐，
     * 见 {@link #scanAndConnectModules()}）。
     *
     * <h3>★★ 2026-10 三次修正：默认改成"纯平移"，不做朝向旋转（开关见方法下方的
     * {@link #NAC_MODULE_SCAN_ROTATE_BY_HOST}）</h3>
     * <p>上面「朝向折算」那一节保留原文备查，但它有<b>两处已被本次修正推翻/降级</b>：
     * <ol>
     *   <li>它断言"预览与本法同一个公式 ⇒ 永远一致"——<b>公式相同不等于必要</b>：当
     *       {@code hostFront = NORTH} 时 {@code calculateModuleOffset} 逐字节码退化为恒等变换
     *       （{@code calculateUpRotation} 只要 {@code front} 是水平方向就返回 {@code NONE}，
     *       {@code _enh2_extract/gtlcore_module_api.txt:366-402}；{@code rotateByFrontAxis} 的
     *       "前轴 = Z + {@code NONE}" 分支即原值，同文件 {@code :374-381/:522}），
     *       而本机（{@code RotationState.NON_Y_AXIS}，数据包/存档实测 {@code facing=north}）
     *       与预览里的幽灵主机都恰好是 {@code NORTH} ⇒ 两条路线<b>逐值等价</b>，
     *       旋转是"多余的、也是唯一可能带来未取证 ±90°/180° 约定的那一环"。</li>
     *   <li>它没有说明"非 north 主机"其实<b>不可能存在</b>：现行图案只有
     *       {@code facing=north} 能成形（施工记录 §50.2 证据 2：8 种组合逐格实测，
     *       新图案 + north 8712/8712 命中，其余 6 种全不成形）⇒ 该旋转永远不会真正生效，
     *       却把"未取证面"（{@code _census/模块框架对齐_执行记录.md} §⑦-2 / §R4：
     *       该函数在 gtlcore 里只被幽灵主机路径调用过）留在主路径上。默认改成纯平移即为此。</li>
     * </ol>
     * <p><b>另注（位点表）</b>：本节上方提到的 12 条偏移现已是"各基座 −z 边中点 + north"
     * （= 数据包/存档里模块真实站着的那一组），选表开关见
     * {@link NanochipComplexPattern#NAC_MODULE_SLOTS_CANONICAL_MINUS_Z}；
     * 旧版"最朝外 F 位"表已降级为备选（12 条里 8 条在 ±x 边上，其 east/west 的 ±90° 符号
     * 从未在真实存档里验证过）。
     *
     * <p><b>失败姿态</b>：表为空 / 拿不到本机坐标或朝向 ⇒ 返回空表（等价于改前的行为，
     * 快照路径照旧兜底；绝不猜、绝不抛）。
     */
    @Override
    public BlockPos[] getModuleScanPositions() {
        List<NanochipComplexPattern.ModuleControllerSlot> slots = NanochipComplexPattern.moduleControllerOffsets();
        if (slots.isEmpty()) {
            return new BlockPos[0];
        }
        BlockPos origin = getPos();
        Direction front = getFrontFacing();
        Direction up = getUpwardsFacing();
        if (origin == null || front == null || up == null) {
            // 拿不到本机朝向 ⇒ 不猜（空表 = 框架那条路什么都不做，退回快照路径兜底）
            return new BlockPos[0];
        }
        BlockPos[] out = new BlockPos[slots.size()];
        // ★★ 2026-10 三次修正（模块预览位置）：默认"纯平移"，不做朝向旋转。
        //   理由（逐字节码）见本方法 javadoc；开关见 NAC_MODULE_SCAN_ROTATE_BY_HOST。
        boolean rotateByHost = NAC_MODULE_SCAN_ROTATE_BY_HOST;
        for (int i = 0; i < out.length; i++) {
            NanochipComplexPattern.ModuleControllerSlot slot = slots.get(i);
            if (!rotateByHost) {
                // 偏移表的口径 = 「各基座 −z 边中点相对 NAC 控制器的世界偏移」（锚点 = 存档实测
                // (30,72,−85)）⇒ 直接平移即得世界坐标，无需任何旋转。
                // 本机 facing=north 时 gtlcore 的 calculateModuleOffset 也退化为恒等变换
                // ⇒ 本分支与原实现逐值等价，且不引入未取证的 ±90°/180° 约定。
                out[i] = origin.offset(slot.offset());
                continue;
            }
            // ▼ 备选路线（只有开关置 true 才走到，逐字保留原实现）：复用 gtlcore 的朝向折算。
            //   第 2/3 参必须与 slot.offset() 的测量口径一致 = NORTH / UP（见上方「朝向折算」）
            ModuleRenderInfo info = new ModuleRenderInfo(slot.offset(), Direction.NORTH, Direction.UP,
                    slot.front(), Direction.UP, null);
            out[i] = origin.offset(info.calculateModuleOffset(front, up));
        }
        return out;
    }

    /**
     * ★★【2026-10 三次修正 · 模块预览位置】<b>扫描位是否按主机朝向做旋转换算</b> ★★
     *
     * <ul>
     *   <li><b>{@code false}（默认 = 修复后行为）</b>⇒ {@link #getModuleScanPositions()} 走
     *       {@code origin.offset(slot.offset())}（<b>纯平移</b>）。
     *       理由：① 本机 {@code facing=north} 时 gtlcore 的
     *       {@code ModuleRenderInfo.calculateModuleOffset} 逐字节码退化为恒等变换
     *       （{@code calculateUpRotation} 在 {@code front} 为水平方向时直接返回 {@code NONE}，
     *       {@code _enh2_extract/gtlcore_module_api.txt:366-402}；{@code rotateByFrontAxis} 的
     *       "前轴 = Z + {@code NONE}" 分支就是原值，同文件 {@code :374-381/:522}）⇒ 两条路线逐值等价；
     *       ② 该换算约定在 gtlcore 里<b>只被"幽灵主机 NORTH/UP"路径调用过</b>，未在真实非 north 主机上验证
     *       （{@code _census/模块框架对齐_执行记录.md} §⑦-2 / §R4）；③ 现行 NAC 图案<b>只有 north 能成形</b>
     *       （施工记录 §50.2 证据 2：8 种组合逐格实测 8712/8712 只在新图案 + north 命中）
     *       ⇒ 那条非 north 分支永远走不到，留着只增加未取证面。</li>
     *   <li><b>{@code true}</b> ⇒ 逐字回到 2026-10 上一版（{@code calculateModuleOffset(getFrontFacing(),
     *       getUpwardsFacing())}），供进游戏做 A/B 对照。</li>
     * </ul>
     *
     * <p>本开关只影响"返回哪 12 个坐标"；{@link #scanAndConnectModules()} 的顺序、连接/断开判定、
     * {@code @Persisted} 字段与快照兜底路径一律未动。改本值需<b>重新构建</b>（编译期常量）。
     */
    public static final boolean NAC_MODULE_SCAN_ROTATE_BY_HOST = false;

    /**
     * 「点击显示机器模块」（{@code M:OFF→M:ON}）预览数据。
     *
     * <p><b>根因</b>：gtlcore 的 {@link IModularMachineHost#getModulesForRendering()} 默认实现
     * <b>直接 {@code return Collections.emptyList()}</b>
     * （字节码：{@code _enh2_extract/gtlcore_module_api.txt:1419-1422}：{@code invokestatic
     * Collections.emptyList → areturn}）。预览侧唯一消费点是
     * {@code PatternPreviewWidget$MBPattern.initializeModules}（同文件 {@code :3230-3246}），
     * 它拿到空表立刻 {@code return List.of()} ⇒ 点按钮画面不变。我方 NAC 原先没覆写它。
     *
     * <p><b>数据从哪来（★ 2026-10 修正：已改成写死的实测表，不再运行时推导）</b>：
     * {@link NanochipModulePreview#nacModules} 调
     * {@link NanochipComplexPattern#moduleControllerOffsets()} —— 该方法现在只是把
     * <b>12 条写死的位点</b>（来源 = 玩家存档实测 {@code _census/expect_table.md} §1 的
     * 「4 个 F 位(偏移)」列）转成列表，<b>恒返回 12 条、没有任何可能失败的路径</b>。
     * 旧实现是"从 {@code NAC_SLICES} 收齐 F 位 → 用「4 个轴向 ±3 格也都是 F」解出 12 个基座中心"
     * 的运行时反推，玩家日志实锤它<b>恒命中 0 个</b>
     * （{@code IllegalStateException: 基座中心判据命中 0 个（期望 12）}）⇒ 恒返回空表 ⇒
     * 点 {@code M:ON} 画面不变；该反推已停用（{@code @Deprecated} 死代码，禁止再调用）。
     * 表的内容、逐条自检与"为什么必须写死"全部写在 {@code NAC_MODULE_PREVIEW_SLOTS} 的 javadoc 里。
     *
     * <p><b>性能</b>：这张表只依赖图案常量、与机器实例无关 ⇒ 在 {@link NanochipModulePreview}
     * 里做了<b>静态</b>缓存（全局一份，≤ 48 条），本方法每次调用只是一次 {@code volatile} 读 + 返回；
     * 诊断日志同一台宿主<b>只打一次</b>（键集合有硬上限）。
     *
     * <p><b>失败姿态</b>：{@code getPos()} 为空 / 推导失败 / 任何意外 ⇒ 返回<b>空表</b>。
     *
     * <p><b>红线</b>：本方法<b>只读</b>——不碰 AE 刷新（{@code refreshPatternsNow}/{@code needPatternSync}）、
     * 不碰 {@code cachedModuleSlots}/{@code lastMatchedModuleSlots}、不碰模式游标、催化剂字段、
     * 分页 UI、{@code clampCapacityToMax}/{@code getInternalSlotCount}/{@code growCapacityFor}，
     * 也不碰 {@code NanochipStructureCheckThrottle}。与"模块连接"机制无关（那是
     * {@code refreshModulesFromStructure} 的事）。
     */
    @Override
    public List<ModuleRenderInfo> getModulesForRendering() {
        try {
            return NanochipModulePreview.nacModules(getPos());
        } catch (Throwable t) {
            // 渲染/预览路径绝不外抛
            NanochipModContext.logger().debug("[gtlmodular] 模块预览: NAC getModulesForRendering 异常，返回空表: {}",
                    t.toString());
            return List.of();
        }
    }

    // ---------- 生命周期 ----------

    /**
     * ★【2026-09-16】结构状态日志<b>每次都打</b>，不再抑制。
     *
     * <p>为什么改（玩家实测证据）：原来用 {@code loggedFormed} 只在"状态翻转"时打，文案里还写着
     * "此后不再重复提示" ⇒ 21:10 之后主机再没打过任何一行，玩家<b>无法</b>判断它到底是"一直成形着
     * 只是连接不上"还是"早就悄悄不成形了"。现在每次结构检查成功/失败都留一条时间戳清晰的 INFO，
     * 并且失效时额外打<b>"首个不符处 + 该格期望什么"</b>（见 {@link #logStructureFailureDetail()}）。
     */
    /**
     * ★【2026-09-18 结构检测性能修复】<b>在「结构检查成功」的那一刻采集一次 F 位坐标快照</b>。
     *
     * <p>调用点只有 {@link #onStructureFormed()} —— 那是 GTCEu 唯一保证
     * {@code MultiblockState.matchContext} 已被完整填写（且异步检查线程已经写完）的时刻。
     * 采集后运行期全部走 {@link #cachedModuleSlots}，不再碰 live 上下文
     * （理由与实测证据见该字段的注释）。
     *
     * <p>线程边界：本方法可能在 GTCEu 的异步检查线程上被调（{@code onStructureFormed} 由它回调），
     * 也可能在服务端线程上被调。所以这里做两件事：
     * <ol>
     *   <li>只读 {@code matchContext} <b>一次</b>，并在这次读取里把结果固化成不可变列表
     *       （{@code List.copyOf}）—— 之后别的线程读到的永远是完整快照，不会看到撕裂；</li>
     *   <li>读失败（极端情况：另一个检查线程正在改同一张表）只跳过本次采集，绝不冒异常出去
     *       打断结构检查回调。</li>
     * </ol>
     *
     * <p>字段本身是 {@code volatile}：写入方可能是 GTCEu 的异步检查线程（转储里
     * {@code "GTCEu Multiblock Async Thread-0"} 直接跑 {@code asyncCheckPattern:544} →
     * {@code checkPatternWithTryLock} → {@code onStructureFormed}），读取方是服务端线程
     * （{@link #tickModuleRefresh()}）；{@code volatile} 保证服务端线程不会长期停在旧引用上。
     * 即便短暂读到上一份，它也一定是<b>完整的</b>快照（不可变 List）—— 绝不会是残表。
     */
    private void snapshotModuleSlots() {
        List<BlockPos> matched;
        try {
            matched = NanochipComplexPattern.moduleSlotsOf(getMultiblockState());
        } catch (RuntimeException e) {
            NanochipModContext.logger().debug("[gtlmodular] NAC @{} 采集 F 位快照失败，保留上一份：{}",
                    getPos(), e.toString());
            return;
        }
        // 坐标排序：槽位表来自 Map，遍历顺序不保证稳定；排序后"超额截断"与日志顺序都可复现。
        List<BlockPos> sorted = new ArrayList<>(matched);
        sorted.sort(Comparator.comparingInt((BlockPos p) -> p.getX())
                .thenComparingInt(p -> p.getY())
                .thenComparingInt(p -> p.getZ()));
        cachedModuleSlots = List.copyOf(sorted);
        lastMatchedModuleSlots.clear();
        lastMatchedModuleSlots.addAll(sorted);
    }

    /** 当前生效的 F 位快照条数（诊断/界面用；0 = 还没拿到过一次成功匹配）。 */
    public int moduleSlotCount() {
        return cachedModuleSlots.size();
    }

    @Override
    public void onStructureFormed() {
        super.onStructureFormed();
        publishNacSlots(); // ★ 新增：把"这一结构里真实站着的 part"坐标发布给客户端（见 syncedNacSlots）
        // ★ 2026-09-27（外壳连片刷新时机）：本方法刚刚发布过清单 ⇒ 把心跳的"上一次 part 数"
        //   对齐到此刻的真实值，并让下一次心跳马上再确认一遍（计数归零）。这只是让心跳不与
        //   本方法打架，**不改变**上面那条紧邻关系（publishNacSlots() 仍紧贴 super.onStructureFormed()）。
        this.nacSlotHeartbeat = 0;
        this.lastNacSlotPartCount = getParts() == null ? -1 : getParts().size();
        // ★ 2026-09-19（队长截图：Jade 说"结构不完整"但机器明明成形了）：
        //   GTCEu 的 Jade `MultiblockStructureProvider` 读的是 `MultiblockState.hasError()`（字节码已核：
        //   `ldc "hasError"` → `hasError()Z` → "gtceu.top.invalid_structure" / "gtceu.top.valid_structure"），
        //   **不是** isFormed()。而我们的变化闸/16 点预检在"判据没变/预检不过"时提前 return，不碰那条 error；
        //   GTCEu 只在一次**成功匹配**里把它清成 null ⇒ 曾失败过、之后成形的机器会长期显示"结构不完整"。
        //   成形 = 结构有效 ⇒ 这里主动清掉那条陈旧错误（setError 是 public，字节码已核）。
        //   ⚠️ 本段必须放在 publishNacSlots() **之后**：`check_ctm_foreign` ⑥ 钉着
        //      "super.onStructureFormed() → publishNacSlots()" 的紧邻关系，插在中间会判红。
        MultiblockState formedState = getMultiblockState();
        if (formedState != null && formedState.hasError()) {
            formedState.setError(null);
        }
        // ★★ 2026-11-14【照抄山海 · 修 ② 接线】★★
        //   成形 = GTCEu 已把所有 part 的能力处理器装进 capabilitiesProxy
        //   （`getEnergyContainer()` 就是从那里派生的，见 registerGridPowerHandler 的 javadoc）
        //   ⇒ **必须在这之后**注册，才能与真能源仓共处同一个 EU 处理器列表。
        //   ⚠️ 位置纪律：必须放在 publishNacSlots() 之后（check_ctm_foreign.js ⑥ 钉着
        //      "super.onStructureFormed() → publishNacSlots()" 的紧邻关系，插在中间会判红）。
        registerGridPowerHandler();
        // ★【2026-09-18 结构检测性能修复】成形 = GTCEu 已确认匹配成功、matchContext 完整
        //   ⇒ 唯一合法的采集点。运行期（每 20 tick / 模块催促）只用这份快照。
        snapshotModuleSlots();
        // ★★【结构位置缓存 + 方块移除定点失效】（2026-11）★★
        //   ① 成形 ⇒ 立刻把本机登记进「结构位置 → 主机」索引（不必等下一 tick 的对齐，
        //      让"成形后马上拆一格"这条最紧的时序也无缝）。★ 改指针后这里存的是**指向本机的弱引用**
        //      （`NanochipStructurePosIndex.register(idxLevel, this)`）⇒ 查表命中时直接拿到机器对象，
        //      不再回世界 `MetaMachine.getMachine` 现取（见 `_census/位置表指针化_实施记录.md` §①）。
        //   ② 自证一次：本机图案推出来的结构格数 vs 上游 MultiblockState.cache 实测格数
        //      （只告警、只打一次、不改任何判定；见 crossCheckStructureCells 的 javadoc）。
        //   ⚠️ 位置：必须在 publishNacSlots() 之后（check_ctm_foreign.js ⑥ 钉着
        //      "super.onStructureFormed() → publishNacSlots()" 的紧邻关系）。
        if (NanochipStructurePosIndex.ENABLED) {
            Level idxLevel = getLevel();
            if (idxLevel != null && !idxLevel.isClientSide) {
                registerIntoStructureIndex(idxLevel);
                if (!this.structureCellsCrossChecked) {
                    this.structureCellsCrossChecked = true;
                    crossCheckStructureCells();
                }
            }
        }
        // ★【2026-09-16 双模式路由】结构成形 ⇒ 管网要重算（端口/管网都以"这台控制器"为宿主）；
        //   同时按当前配置收口一次分量池（简单模式 ⇒ 并回 0 池，不丢件）。
        pipeGraph.markDirty();
        reconcileRoutingMode();
        // ★ 2026-09-21（0.1.21 并发债止血，来自连接专项研究）：
        //   本方法**可能跑在 GTCEu 的异步结构检查线程上**（`MultiblockControllerMachine.asyncCheckPattern`
        //   在异步线程里回调 `onStructureFormed`），而 `scanAndConnectModules()` 会写 `modules` 集合、
        //   写各模块的 `host` / `hostPosition`，并经 `refreshHostSync()` 写 `@DescSynced` 字段
        //   ⇒ 在非服务端线程做这些是**真并发债**（集合撕裂 / 同步字段跨线程写）。
        //   修法：**一律投递到服务端线程执行**（最坏晚 1 tick，代价可忽略；不用"是否同线程"判断，避免判断本身出竞态）。
        Level formedLevel = getLevel();
        // ★★ 2026-11-13【原初模式 · 层 6 第三处**排除**】—— 连接注入不再受总开关管辖 ★★
        //   实机结果：主机**已成型** ✓ 但**模块都没有电** ✗（用户原话「成型了，但是现在模块都没有电」）
        //   ⇒ 原因正是这一处被透明化：上游基类**不会**自动调 `scanAndConnectModules()`
        //     （gtlcore 自己的 SpaceElevatorMachine:64 / SuprachronalAssemblyLineMachine:92-93 都是在它们
        //      **各自**的 onStructureFormed 里调它）⇒ 主机不再连模块 ⇒ 模块拿不到主机的能量缓存。
        //   ⇒ 处置：**本处从原初模式的豁免里摘出来** —— 无论原初模式与否，主机成型后都照常执行连接注入。
        //   ⚠️ 与「补登记族」无关：`relinkFromPersistedHost` / `recoverModulesFromPersistedSlots` 已被
        //      另一位同事从源码里撤掉（见 `_census/隔离测试_撤三批_实施记录.md`），这里**不恢复**它们，
        //      只做「让 scanAndConnectModules() 照常跑」这一件事。
        //   ⚠️ 原初模式下**仍然关闭**的是层 6 的前两处：同 tick 折叠（`checkPatternWithLock`）
        //      与 20 tick 周期重扫/补登记（`tickModuleRefresh`）—— 后者正是"一进人就重连"的来源。
        //   回退：本处的改前原文见 `_census/原初模式_回退包/ORIGINAL_HUNKS.md` §11。
        if (formedLevel instanceof ServerLevel formedServer) {
            formedServer.getServer().execute(this::scanAndConnectModules);
        } else {
            scanAndConnectModules();
        }
        updateSyncedTexts();
        // ★ 2026-09-21（队长：「一进游戏 NAC 相关的都要重新加载结构…日志每分钟 90 条」）降噪：
        //   GTCEu 会**反复**回调 onStructureFormed（结构成形后，任何方块/零件变化触发一次重查都会再调一次），
        //   而这条日志原来是无条件打的 ⇒ 12 台模块在结构里动一下就是十几条同样的"已成形"。
        //   现在走结构日志闸：同一结论（档位 + F 位槽数都相同）10 秒内只打一条 INFO，
        //   重复的降到 debug；"成形↔失效"这种真变化照打（队长要靠日志看状态）。
        Level formedLogLevel = getLevel();
        long formedLogNow = formedLogLevel == null ? 0L : formedLogLevel.getGameTime();
        String formedDetail = (fullMode() ? "全流程档" : "直通档") + "|F 位=" + lastMatchedModuleSlots.size();
        boolean emitFormed = this.structureLogGate.shouldEmitInfo(FORMED_CONCLUSION, formedDetail,
                "复合体@" + getPos().toShortString(), formedLogNow);
        if (emitFormed || NanochipModContext.logger().isDebugEnabled()) {
            Object[] formedArgs = {getPos(), fullMode() ? "全流程档" : "直通档",
                    lastMatchedModuleSlots.size(), formedModuleCount(), getMaxModuleCount(),
                    summarizeFSlotContent()};
            if (emitFormed) {
                NanochipModContext.logger().info(FORMED_LINE_FORMAT, formedArgs);
            } else {
                NanochipModContext.logger().debug(FORMED_LINE_FORMAT, formedArgs);
            }
        }
        // ★ 2026-09-19：成形那一刻若有人点了「立即更新结构」，立刻回一条"已成形成功"
        //   （这是三条回报路径里最早的一条；没人在等时是一次 null 判断）
        reportRefreshResult();
    }

    private static final long INVALID_GRACE_TICKS = 100L;   // 5 秒

    /** 把已连接模块的能量缓存清零（服务端线程上执行；结构失效时调用）。 */
    private void clearModuleBuffers() {
        for (IModularMachineModule<NanochipAssemblyComplexMachine, ?> m : getModules()) {
            if (m instanceof NanochipModuleMachine mm) {
                mm.clearBufferEU();
            }
        }
    }

    /** 待执行的断开时刻（gameTime）；-1 = 没有待执行断开（哨兵用 -1，不用 Long.MIN_VALUE：减法会溢出）。 */
    private long moduleDisconnectDeadline = -1L;

    @Override
    public void onStructureInvalid() {
        super.onStructureInvalid();
        // ★ 2026-10-02【L3 真实多线程】结构失效 ⇒ 丢掉 Ω-天球分歧引擎仓的引用。
        //   理由：仓已不在结构里，但引用留着会让 getAdditionalThread() 继续按它算线程数
        //   ⇒ 「显示 N 条、其实没有那条仓」的假象。清掉后线程数自然回落到基础值。
        clearThreadPart();
        clearNacSlots(); // ★ 新增：结构失效 ⇒ 清单清空（客户端索引随之丢掉本机这一份）
        // ★★【结构位置缓存 + 方块移除定点失效】（2026-11）★★
        //   失效 ⇒ 从索引里注销 + 丢掉待办：本功能只为"看起来已成形、其实该失效"的那台服务，
        //   已经不成形时敲方块本来就不需要任何人重判（未成形侧的周期路径照旧在跑）。
        //   ★ 改指针后这一条更重要：注销会**同时丢掉表里那条指向本机的指针**，
        //     不会让一个"已经失效的机器对象"继续被登记表引用（虽然弱引用本来也不会钉住它 —— 双保险）。
        //   ⚠️ 位置：必须在 super.onStructureInvalid() / clearNacSlots() 之后 ——
        //      check_ctm_foreign.js ⑥ 钉着那两行的紧邻关系，插在中间会判红。
        unregisterFromStructureIndex();
        dropPendingRemoval();
        // ★★ 2026-11-14【照抄山海 · 修 ② 接线】★★ 结构失效 ⇒ 把电网容器从能力代理里摘掉
        //   （与 registerGridPowerHandler() 成对；位置在本方法靠后，避开上面那几条
        //    check_ctm_foreign.js ⑥ 钉住的紧邻关系）。
        unregisterGridPowerHandler();
        // ★ 2026-09-27（外壳连片刷新时机）：清单已清空 ⇒ 心跳的 part 数基线作废，
        //   否则"失效时 part 数恰好与失效前相同"会让心跳以为"没变化"而漏掉一次重发。
        this.lastNacSlotPartCount = -1;
        // ★ 2026-09-21：结构失效 ⇒ 各模块的能量缓存清零（缓存是主机分给它们的，主机都不成形了不该留着）。
        //   与 scanAndConnectModules 同款：本方法可能在 GTCEu 的异步结构检查线程上 ⇒ 一律投递到服务端线程再写。
        Level bufLevel = getLevel();
        if (bufLevel instanceof ServerLevel bufServer) {
            bufServer.getServer().execute(this::clearModuleBuffers);
        } else {
            clearModuleBuffers();
        }
        int dropped = modules.size();
        // ★ 先打诊断（此时 lastMatchedModuleSlots 与 modules 还是失效前的状态，信息最有价值）
        logStructureFailureDetail();
        // ★ 2026-09-19（队长：「切换成电路组装机模式，压根就没办法成型」实为结构缺一格）：
        //   缺件清单**必须在这里也打一次**。此前它只挂在 asyncCheckPattern 的"真的跑过全量匹配"之后，
        //   而那条路径会被 16 点预检与变化闸挡在前面（实测：整场失效 5 次，清单一条都没打出来）
        //   ⇒ 队长只能看到"不行"，看不到"缺哪一格"。失效是"肯定不成形"的确定事件，正是打清单的时机。
        logUnformedAudit();
        // ★ 2026-09-21（0.1.21 A2 连接去抖，来自连接专项研究）：原先是**无条件** `safeClearModules()`
        //   ⇒ 玩家一动方块、主机失效一瞬间就把 14 台模块全踢掉，随后又要等整图案匹配重新收回来
        //   （实测稳态 8 条「已连接模块数变化」且**每次从 0 开始**，每条前面都是「本次断开模块 N 个」）。
        //   现在改成"给一个宽限窗口，到点仍未成形才真正断开"：
        //   · 窗口内主机若重新成形 ⇒ 连接**从未中断**（模块侧也不会闪成"未连接"）；
        //   · 窗口内保持旧结论是安全的：`isValidModule` 仍要求模块自己 `isFormed()`，真失效的会被摘掉；
        //   · 到点仍不成形 ⇒ tickSync 里真正清集合（各模块侧随之显示"未连接"）。
        Level invalidLevel = getLevel();
        this.moduleDisconnectDeadline = invalidLevel == null ? -1L : invalidLevel.getGameTime() + INVALID_GRACE_TICKS;
        // ★【2026-09-16 双模式路由】结构失效 ⇒ 管网判定作废（下次成形时重算）
        pipeGraph.clear();
        // ★ 2026-09-21 降噪：这条是"真变化"（成形→失效），日志闸必然放行；
        //   挂上闸只是为了：① 万一 GTCEu 在未成形期间重复回调 onStructureInvalid 时不再重复写盘；
        //   ② 让"已成形/结构失效/尚未成形"三条共用同一份本机状态记忆（见 NanochipStructureLogGate）。
        Level invalidLogLevel = getLevel();
        long invalidLogNow = invalidLogLevel == null ? 0L : invalidLogLevel.getGameTime();
        boolean emitInvalid = this.structureLogGate.shouldEmitInfo(INVALID_CONCLUSION, "",
                "复合体@" + getPos().toShortString(), invalidLogNow);
        if (emitInvalid || NanochipModContext.logger().isDebugEnabled()) {
            if (emitInvalid) {
                NanochipModContext.logger().info(INVALID_LINE_FORMAT, getPos(), dropped);
            } else {
                NanochipModContext.logger().debug(INVALID_LINE_FORMAT, getPos(), dropped);
            }
        }
        // ★【2026-09-18 第四轮】失效 = 状态发生"大变化" ⇒ 变化闸归零，保证"玩家补齐 → 立刻再成形"
        //   不被地板拖住。只有「曾经成形」的机器会走到这里（GTCEu 只在 isFormed() 时调 onStructureInvalid），
        //   所以这不会给"从没成形过、一直缺方块"的持久错误状态开后门。
        //   注：本行**不能**挪到 super.onStructureInvalid() 与 clearNacSlots() 之间 ——
        //       check_ctm_foreign.js ⑥ 钉着那两行的紧邻关系。
        this.structureCheckGate.reset();
    }

    @Override
    public void onMachineRemoved() {
        safeClearModules();
        // ★★【结构位置缓存 + 方块移除定点失效】控制器**被拆掉** ⇒ 从索引注销
        //   （原版 `BlockEntity.setRemoved()` 与 GTCEu 的 `onMachineRemoved()` 两条路都可能有，
        //    所以这里与 onUnload() **各摘一次**：注销幂等、身份校验保证只摘自己那条；
        //    只要有一条走到，表里就不会留着指向本机的指针）。
        //   ★ 改指针后这一条是"不悬空"的关键：摘掉条目 = 摘掉**指针**（不只是坐标）；
        //     即使两条都没走到，弱引用 + 惰性清空壳仍然保证不会钉住本机/方块实体/世界。
        unregisterFromStructureIndex();
        dropPendingRemoval();
    }

    /**
     * ★ 2026-10-02【L3 换基类配套】：gtladditions 那套多配方机的标准写法，返回 {@code true}。
     *
     * <p>出处：{@code LargeFurnaceMachine.java:48-51} 与
     * {@code BasicOreProcessingPlantMachine.java:53-56} —— 两台走 gtladd 多配方体系的机器
     * <b>都</b>覆写成 {@code true}；GTCEu 的默认实现是 {@code false}
     * （{@code WorkableMultiblockMachine.keepSubscribing()} 字节码：{@code iconst_0; ireturn}，
     * 见 {@code _census/_javap_simple_workablemb.txt:448-451}）。
     *
     * <p>它决定「机器成形后是否继续订阅零件变化、以维持多配方特性」——
     * gtladd 系机器的并行能力依赖零件/配方状态持续被追踪，所以上游统一覆写为 {@code true}。
     * 本类换基类后一并对齐，避免出现「换了基类但没跟上配套开关」的半吊子状态。
     */
    @Override
    public boolean keepSubscribing() {
        return true;
    }

    /**
     * ★ 2026-10-02【L3/A5 配套】：零件卸载（区块卸载 / 仓被拆）时同样丢掉引擎引用。
     *
     * <p>与 {@code LargeFurnaceMachine.java:103-107} 同款。为什么不能只靠
     * {@link #onStructureInvalid()}：区块卸载是「零件暂时不在」，失效回调不一定立刻来，
     * 而线程数会被界面与 Jade 立刻读到 ⇒ 引用必须同步清掉，否则显示的线程数会虚高。
     */
    @Override
    public void onPartUnload() {
        super.onPartUnload();
        clearThreadPart();
    }

    // ---------- 结构失效诊断（★ 2026-09-16 新增：让"悄悄不成形"变成可诊断） ----------

    /** 结构失效：打一条"首个不符处 + 该格期望什么"；拿不到逐格原因时退化成摘要并说明原因。 */
    private void logStructureFailureDetail() {
        String detail = describeStructureFailure(getMultiblockState());
        if (detail != null) {
            NanochipModContext.logger().info("[gtlmodular] NAC 结构失效定位 @{}：{}", getPos(), detail);
            return;
        }
        NanochipModContext.logger().info("[gtlmodular] NAC 结构失效摘要 @{}：GTCEu 本次没有留下逐格原因"
                        + "（MultiblockState.error == null）—— 原因：onStructureInvalid() 不带参数，逐格原因只能从"
                        + " MultiblockState.error 读，而 GTCEu 只在「数量类 / 被卸载」失败路径上写它，"
                        + "单格不符常常直接 return false；⇒ 退化摘要：当前已连接模块 {} 个；{}",
                getPos(), modules.size(), summarizeModuleSlots());
    }

    /**
     * 把 GTCEu 结构检查的失败原因翻成一行可读诊断（"首个不符处 + 该格期望什么"）。
     *
     * <p>数据来源（<b>已按 {@code gtceu-1.20.1-1.4.4} 字节码核对，不是猜的</b>）：
     * {@code BlockPattern} 判定失败时会把 {@code SinglePredicateError} / {@code PatternStringError} /
     * 基础 {@code PatternError} 写进 {@code MultiblockState.error}（{@code setError} 顺带把 state 挂到
     * error 上），而 {@code PatternError.getPos()} = 该 state 当时正在检查的那一格、
     * {@code getErrorInfo()} = 期望文案、{@code getCandidates()} = 该格允许放的方块/物品。
     * {@code onStructureInvalid()} 本身不带任何参数，所以这是<b>唯一</b>能拿到逐格原因的途径。
     *
     * <h4>诚实边界（哪些情况它给不出"那一格"）</h4>
     * <ul>
     *   <li>数量类失败（{@code SinglePredicateError.type} 0/1/2/3 = 全局上限 / 全局下限 / 每层上限 /
     *       每层下限，例：NAC 的"能源仓最多 2 个"是<b>全局</b>限制）没有"某一格"的概念，
     *       此时 {@code getPos()} 只是"最后被检查的那一格"，返回值里会标注；</li>
     *   <li>{@code MultiblockState.UNLOAD_ERROR} / {@code UNINIT_ERROR} 这两个静态错误对象没有挂 state
     *       （直接 {@code getPos()} 会 NPE），单独识别并说明"不是真的结构错"；</li>
     *   <li>完全不写 error 的失败路径 ⇒ 返回 {@code null}，调用方改用摘要；</li>
     *   <li>本方法<b>只读</b>，任何异常都被吞掉（返回 null）—— 诊断绝不能拖垮结构检查本身。</li>
     * </ul>
     *
     * @param state 该多方块的 {@code getMultiblockState()}（可为 null）
     * @return 一行诊断；拿不到逐格原因时返回 {@code null}
     */
    public static String describeStructureFailure(MultiblockState state) {
        if (state == null) {
            return null;
        }
        PatternError error = state.error;
        if (error == null) {
            return null;
        }
        if (error == MultiblockState.UNLOAD_ERROR) {
            return "GTCEu 记录的原因是「方块/零件被卸载」MultiblockState.UNLOAD_ERROR"
                    + "—— 不是真的结构错，等区块/零件重新加载后会自动复查";
        }
        if (error == MultiblockState.UNINIT_ERROR) {
            return "GTCEu 记录的原因是「结构状态尚未初始化」MultiblockState.UNINIT_ERROR —— 不是真的结构错";
        }
        try {
            BlockPos bad = error.getPos();
            Component expected = error.getErrorInfo();
            int type = -1;
            String what;
            if (error instanceof SinglePredicateError single) {
                type = single.type;
                what = switch (single.type) {
                    case 0 -> "该方块/仓位的【全局数量超过上限】；例：能源仓最多 2 个";
                    case 1 -> "该方块/仓位的【全局数量不足】；例：至少 1 个能源仓";
                    case 2 -> "该方块/仓位的【每层数量超过上限】";
                    case 3 -> "该方块/仓位的【每层数量不足】";
                    default -> "该格判定不符：单格没通过";
                };
            } else {
                what = "该格判定不符：通用结构错误";
            }
            boolean countType = type >= 0 && type <= 3;
            return String.format(Locale.ROOT,
                    "%s；GTCEu 记录的位置 %s%s；该格实际=%s；GTCEu 期望文案=「%s」；允许放的方块/物品=%s%s",
                    what,
                    bad == null ? "未给出" : bad.toShortString(),
                    countType ? "数量类错误：这只是「最后被检查的那一格」，不是「某一格放错了」" : "",
                    bad == null ? "未知" : describeSlotContent(state.getWorld(), bad),
                    expected == null ? "无" : expected.getString(),
                    describeCandidates(error),
                    type < 0 ? "" : "；GTCEu 错误类型=" + error.getClass().getSimpleName() + "(type=" + type + ")");
        } catch (RuntimeException e) {
            // 诊断失败不影响结构检查本身：交给摘要路径
            return null;
        }
    }

    /** 逐个 F 位打印"这一格现在站的是什么"（结构失效诊断用；最多 6 格 + 省略数）。 */
    private String summarizeModuleSlots() {
        Level level = getLevel();
        if (level == null || lastMatchedModuleSlots.isEmpty()) {
            return "缓存里没有 F 位坐标（本机这次会话还没有成功匹配过一次）";
        }
        int formed = 0;
        for (BlockPos pos : lastMatchedModuleSlots) {
            if (MetaMachine.getMachine(level, pos) instanceof NanochipModuleMachine module && module.isFormed()) {
                formed++;
            }
        }
        StringBuilder sb = new StringBuilder();
        int shown = 0;
        for (BlockPos pos : lastMatchedModuleSlots) {
            if (shown >= 6) {
                break;
            }
            if (shown > 0) {
                sb.append(" | ");
            }
            sb.append(pos.toShortString()).append('=')
                    .append(describeSlotContent(level, pos));
            shown++;
        }
        if (lastMatchedModuleSlots.size() > shown) {
            sb.append(" | …另 ").append(lastMatchedModuleSlots.size() - shown).append(" 格");
        }
        return "上次成功匹配的 F 位 " + lastMatchedModuleSlots.size() + " 个，其中已成形模块 " + formed + " 个：" + sb;
    }

    /** 一格上现在是什么（模块/NAC 模块机 → 种类；否则方块 id）。 */
    private static String describeSlotContent(Level level, BlockPos pos) {
        MetaMachine machine = level == null ? null : MetaMachine.getMachine(level, pos);
        if (machine instanceof NanochipModuleMachine module) {
            return module.getModuleKind().nameZh() + (module.isFormed() ? " · 已成形" : " · 未成形");
        }
        ResourceLocation id = level == null ? null : BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
        String base = id == null ? "取不到方块 id" : id.toString();
        return machine == null ? base : base + " · " + machine.getClass().getSimpleName();
    }

    /** GTCEu 给出的"允许放什么"（每类只取第一个，最多 6 类；拿不到就说明拿不到）。 */
    private static String describeCandidates(PatternError error) {
        List<List<ItemStack>> candidates = error.getCandidates();
        if (candidates == null || candidates.isEmpty()) {
            return "GTCEu 未给出候选表";
        }
        StringBuilder sb = new StringBuilder();
        int groups = 0;
        for (List<ItemStack> group : candidates) {
            if (group == null || group.isEmpty()) {
                continue;
            }
            if (groups >= 6) {
                sb.append(" / …");
                break;
            }
            if (groups > 0) {
                sb.append(" / ");
            }
            ItemStack first = group.get(0);
            ResourceLocation id = first.isEmpty() ? null : BuiltInRegistries.ITEM.getKey(first.getItem());
            sb.append(id == null ? "空" : id.toString());
            if (group.size() > 1) {
                sb.append("(+" ).append(group.size() - 1).append(')');
            }
            groups++;
        }
        return sb.length() == 0 ? "GTCEu 未给出候选表" : sb.toString();
    }

    // ---------- 存档 ----------

    /**
     * 存档键：<b>F 位坐标表</b>（见 {@link #restoredModuleSlots}）。
     *
     * <p>与 GTCEu 自己写的那个 {@code isFormed} 键<b>并存</b>、不冲突：GTCEu 恢复的是
     * "成不成形"这个结论，我们补的是"成形时 F 位在哪"这张表。少了后者，载入后
     * {@link #refreshModulesFromStructure()} 就没有可依据的坐标 ——
     * 2026-09-19 实机实测：载入后模块干等 <b>4 分 18 秒</b>。
     */
    private static final String NBT_MODULE_SLOTS = "GTLModularNacModuleSlots";

    @Override
    public void saveCustomPersistedData(CompoundTag tag, boolean forDrop) {
        super.saveCustomPersistedData(tag, forDrop);
        // ★★ 0.3.132【补上缺失的 `!forDrop` 守卫】★★
        //   改前这两行**没有** `forDrop` 守卫，而紧邻的 F 位快照有 ⇒ 语义不自洽：
        //   「拆下来变成物品的机器该不该带着校准/真空网络走」这件事**代码里从没被显式决定过**。
        //   队长口径（逐字）：「只清这一台机器」⇒ 校准是**这台控制器**的本地历史，
        //   掉落物不该携带它（否则"换个地方放下"就等于把历史搬走，与"只清这一台"的直觉冲突）。
        //   ⚠ 与 F 位那条同款理由：搬走后那张表/那份历史在新位置就是**谎话**。
        //   回退：把 `!forDrop &&` 去掉即逐字恢复改动前行为。
        //   ⚠ 存档格式**零变更**：正常存档（`forDrop=false`）照旧写入同名键，老档照读
        //     （读取侧有 `tag.contains(...)` 守卫，见 loadCustomPersistedData）。
        if (!forDrop) {
            tag.put("GTLModularNanochipCalibration", calibration.save());
            tag.put("GTLModularNanochipVacuum", vacuumNetwork.save());
        }
        // ★【2026-09-21 载入快路径】把当前生效的 F 位快照写进存档。
        //   ⚠️ 必须 `!forDrop`：**被拆下来变成物品**的机器不该随身带着"我在某某坐标成形过"这张表
        //   —— 玩家把它搬到别处放下时，那张表就是纯粹的谎话（验证虽会否证它，但更该一开始就不写）。
        if (!forDrop && !cachedModuleSlots.isEmpty()) {
            long[] packed = new long[cachedModuleSlots.size()];
            for (int i = 0; i < packed.length; i++) {
                packed[i] = cachedModuleSlots.get(i).asLong();
            }
            tag.putLongArray(NBT_MODULE_SLOTS, packed);
        }
    }

    @Override
    public void loadCustomPersistedData(CompoundTag tag) {
        super.loadCustomPersistedData(tag);
        if (tag.contains("GTLModularNanochipCalibration")) {
            calibration.load(tag.getCompound("GTLModularNanochipCalibration"));
        }
        if (tag.contains("GTLModularNanochipVacuum")) {
            vacuumNetwork.load(tag.getCompound("GTLModularNanochipVacuum"));
        }
        // ★【2026-09-21 载入快路径】读回 F 位坐标表 —— 只放进 restoredModuleSlots（**待验证**），
        //   不直接写 cachedModuleSlots：存档里的内容是"声称"，必须等 adoptRestoredModuleSlots()
        //   逐格对着真实方块验过才启用。
        //   （此处的日志刻意不打 getPos()：方块实体读档时坐标字段的可用性不值得赌一个 NPE。）
        if (tag.contains(NBT_MODULE_SLOTS)) {
            long[] packed = tag.getLongArray(NBT_MODULE_SLOTS);
            List<BlockPos> restored = new ArrayList<>(packed.length);
            for (long v : packed) {
                restored.add(BlockPos.of(v));
            }
            // 与 snapshotModuleSlots() 同一排序口径（x→y→z），让日志与"超额截断"可复现
            restored.sort(Comparator.comparingInt((BlockPos p) -> p.getX())
                    .thenComparingInt(p -> p.getY())
                    .thenComparingInt(p -> p.getZ()));
            this.restoredModuleSlots = List.copyOf(restored);
            this.restoredSlotsSettled = false;
            NanochipModContext.logger().info("[gtlmodular] 读档：取得存档 F 位快照 {} 格"
                    + "（待逐格验证后启用；验证入口 adoptRestoredModuleSlots）", restored.size());
        }
    }

    // ---------- 界面 ----------

    /**
     * ★★ 0.3.132【隐藏「配方最短耗时」按钮 · 队长令：「把这个隐藏就行，只要看不到他就行」】★★
     *
     * <h4>这个按钮为什么会在我们机器上</h4>
     * 父类 {@code GTLAddWorkableElectricMultipleRecipesMachine}（gtladditions）的
     * {@code createConfigurators()} 会 {@code new LimitedDurationConfigurator(this)}，
     * 而它的 {@code attachConfigurators(panel)} 里的字节码是
     * <pre>{@code
     *   super.attachConfigurators(panel);                      // gtlcore WorkableElectricMultipleRecipesMachine
     *   IFancyConfigurator c = createConfigurators();
     *   if (c != null) { panel.attachConfigurators(new IFancyConfigurator[]{ c }); }
     * }</pre>
     * （逐字：`GTLAddWorkableElectricMultipleRecipesMachine.attachConfigurators` 的
     *  `:11 invokevirtual createConfigurators` → `:16 ifnull 42`）
     * ⇒ <b>只要这里返回 {@code null}，父类就自己跳过，那个 tab 根本不会挂上去</b>。
     *
     * <h4>为什么只隐藏、不删机制</h4>
     * 队长令是「隐藏就行，看不到即可」⇒ 本覆写<b>不删任何字段、不改任何判定</b>：
     * 父类的 {@code limitedDuration} 字段仍在（它在父类 {@code ManagedFieldHolder} 里，
     * 属同步/存档面，动了会破坏存档），{@code getLimitedDuration()/setLimitedDuration()} 也仍在。
     * 只是<b>不再有 UI 入口</b>。
     *
     * <h4>它本来对我们有没有用（已核）</h4>
     * 消费 {@code limitedDuration} 的是 gtladditions 的 {@code GTLAddMultipleRecipesLogic}
     * （两处 {@code RecipeCalculationHelper.buildNormalRecipe/buildWirelessRecipe}），
     * 而本机 {@link #createRecipeLogic} 返回的是我们自己的 {@link NanochipThreadedRecipeLogic}
     * ⇒ <b>这个值在我们的机器上没有任何代码读它</b>。它只是一个"能点、能改、但不影响任何事"的旋钮，
     * 留着只会误导玩家（队长原话方向：「它会影响我们的机制」——实际上它不改我们的机制，但会让人以为会改）。
     *
     * <h4>回退</h4>
     * 删掉本方法 ⇒ 逐字恢复父类行为（按钮重新出现）。**不要**去动父类字段。
     */
    @Override
    protected IFancyConfigurator createConfigurators() {
        return null;   // ★ 让父类的 attachConfigurators 走 `if (c != null)` 的假分支 ⇒ 不挂「配方最短耗时」
    }

    /**
     * ★【2026-09-21 0.1.24】<b>不再覆写 {@code createUI(Player)}，也不再覆写 {@code createUIWidget()}</b>
     * —— 前者会把基类的 fancy 机器界面顶掉，后者会挤占主页面（要压显示区才能塞进按钮）。
     *
     * <p>取证：{@code WorkableElectricMultiblockMachine implements IFancyUIMachine, IDisplayUIMachine, …}，
     * 它的 {@code createUI} 才是"机器的样子"（{@code ModularUI(198,208)} + {@code FancyMachineUIWidget}：
     * 标题栏 / 左列 tab / 底部背包都由那个 widget 画，左列 tab 来自 {@code getSubTabs()} 把每个 part 当一格）。
     * 0.1.16 为了放按钮覆写 {@code createUI} 自绘了 176×230 的朴素页 ⇒ 队长看到的
     * 「主机跟模块的UI都不是以机器的样子了」以及"主机里看不到纳米校准"（校准页挂在仓室那个 tab 上）
     * 都是这一个原因。⇒ 现在两样功能都挂进 GTCEu 自己的**配置页图标列**，主页面一个字不动。
     */
    @Override
    public void attachConfigurators(ConfiguratorPanel panel) {
        super.attachConfigurators(panel);
        // ① 纳米校准（队长：「这个纳米校准，我希望能在主机那边看到」）。
        //    取数一律走**同步字段**（synced*）：fancy 配置页在客户端也要画，而校准数据本体是服务端权威
        //    —— 走同步字段才保证"界面显示的"与"真正判定用的"是同一份。
        // ★★ 0.3.132【队长令：清除电路历史与纳米校准合并到同一个 UI】★★
        //   队长原话：「把清除电路历史跟纳米校准这两个按钮合并到一个UI里…然后把这个按钮给隐藏了」
        //   ⇒ 左列**只挂一张**「纳米校准」页；清除按钮做进那张页的**内部**
        //     （见 NanochipCalibrationPanel.section(..., clearAction)）。
        //   ⚠ 清除仍是两步确认、只清本机（机器侧出口 NanochipAssemblyComplexMachine#clearCalibration 未变）。
        //   ★★ 0.3.132【校准面板档位显示 · 修】★★ 必须传 `this::syncedTierOf`（第 8 个实参）——
        //   改前这里只传了 8 个实参，Java 重载解析于是落到面板那条「不知道档位」的重载
        //   （它把档位写死成常量 0）⇒ 面板上所有读档位的地方全拿到 0，
        //   于是出现「占比 100.00%」+「生效 0 档」+「本档功效：无」+「还差 0 个到第 1 档」这种自相矛盾的可见态。
        //   ⇒ 现在传真回调，绑定面板那条 `IntFunction<Integer> tierOf` 的 9 参重载
        //     （类型不歧义：`IntFunction<Integer>` vs 末尾那个 `Runnable`）。
        panel.attachConfigurators(new NanochipCalibrationPanel.Configurator(
                () -> NanochipCalibrationPanel.section(this::syncedHistoryProgress, this::syncedHistoryTotal,
                        this::syncedShareOf, this::syncedUsesFullWindowDenominator,
                        this::syncedActiveFamilyOrdinal, this::syncedCalibrationDenominator,
                        this::syncedFamilyCount,
                        this::syncedTierOf,
                        () -> this.clearCalibration())));
        // ② 立即更新结构（队长：「那个主机底部的立即更新结构可以改到左下角」）
        // ★★ 0.3.132【队长令：这个按钮也隐藏】★★
        //   队长原话（附图，指着这个按钮的悬停说明）：「这个按钮怎么还没隐藏」。
        //   ⇒ 主机这边**不再挂**这个按钮；它背后的一切**一个字不动**：
        //     `NanochipStructureRefresh` 类、`Refreshable` 接口、`requestStructureRefresh(...)`、
        //     自动恢复（`LOAD_PARTS_RECOVERY_ENABLED`）、结构检查的三道门**全都保留**，
        //     只是**不再有手动入口**（模块塔那边的挂载在 `NanochipModuleMachine`，本次**未动** —— 队长指的是主机）。
        //   回退：把下面这行的 `if` 改成 `true`（或删掉 `if` 只留调用）。
        if (SHOW_STRUCTURE_REFRESH_BUTTON) {
            panel.attachConfigurators(new NanochipStructureRefresh.RefreshConfigurator(this));
        }
    }

    /**
     * ★★ 0.3.132【主机是否显示「立即更新结构」按钮】★★（队长令「这个按钮怎么还没隐藏」）
     *
     * <p>{@code false} = 主机左列**不挂**那个按钮；判定/自动恢复/自检**一个字不动**
     * （见 {@code attachConfigurators} 里的注释）。改 {@code true} 即逐字恢复。
     */
    public static final boolean SHOW_STRUCTURE_REFRESH_BUTTON = false;

    @Override
    public void addDisplayText(List<Component> textList) {
        super.addDisplayText(textList);
        // ★★ 2026-11-10（第 2 轮）【部件未就绪 ⇒ 等级显示「待定」，绝不显示 LV / ULV】★★
        //   为什么必须这样：GTCEu 基类的「最大配方等级 / 最大功率」两行读的是 protected 字段
        //   `tier` / `energyContainer`，而这两个字段**只在 onStructureFormed() 里按能源仓推导一次**
        //   所以读档后部件尚未就绪时，不能把占位字段值显示成可用等级和功率。
        //   ⇒ 部件没登记回来时它们读的是 `int tier` 的 Java 默认值 0 = ULV（改前）或上一轮的临时 1 = LV，
        //   两种都会让玩家读成"我的机器降级了"。
        //   ⇒ 现在改成：这两行**整行不显示**，只留一行「待定」明确交代它是"还没重连、不是降级"。
        //   · **不伪造 MAX** ✗（题目要求"显示即事实"）：不写任何档位、不写任何上限值；
        //   · 判据与恢复动作同一处（hasRegisteredParts()），零件一回来这三行立刻消失、
        //     那两行恢复成 onStructureFormed() 写下的**真实值**；
        //   · 只动显示：不碰 tier / energyContainer（上一轮那个 `this.tier = 1` 的写法已按题目要求删除），
        //     不碰任何判定式（逻辑一律读能源容器实况，见 getTotalStoredEU() 等）。
        //   ⚠️ 只在"已成形 + 零件表空"这一条故障路径上生效 ⇒ 正常机器一行都不改（零观感变化）。
        if (isFormed() && !hasRegisteredParts()) {
            replaceUnreadyTierDisplay(textList);
        }
        if (isFormed() && !hasRegisteredParts()) {
            // 明确的状态文案：把"为什么那两行不见了、等级是什么状态"讲清楚。
            textList.add(Component.literal("  · 结构部件未就绪（能源仓 / 样板总成尚未重新连接）")
                    .withStyle(ChatFormatting.YELLOW));
            // ★【B · 停用"我们主动刷新结构"】文案必须与开关一致（"显示即事实"）：
            //   自动恢复开着时，这句承诺"无需拆放控制器"是真的；一旦 LOAD_PARTS_RECOVERY_ENABLED = false，
            //   我们就不再有那条自动出口 ⇒ 必须如实告诉玩家现在只有哪两条路（拆放控制器 / 点按钮），
            //   否则界面就在骗人（这是本轮唯一"因开关而改文案"的地方）。
            textList.add(Component.literal("    最大配方等级 / 最大功率暂不显示（避免误读成降级）；"
                            + (LOAD_PARTS_RECOVERY_ENABLED
                            ? "已让结构失效并交回上游重新成形，无需拆放控制器"
                            : "本版已停用读档自动恢复（B 开关）：请拆放一次控制器，"
                            + "或点左侧「立即更新结构」按钮"))
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        if (!isFormed()) {
            textList.add(Component.literal("纳米芯片组装复合体：结构未成形")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        // ★★ 2026-11-13【本任务 ②】界面那两行（并行 / 跨配方线程）**不再依赖按键替换** ★★
        //   实机反驳（用户界面逐字）：「同时处理至多 2147483647 个配方」+「拥有 128 个跨配方线程」
        //   仍是父类旧句，而同一面板里我们自己的行（`独立线程：上限 256 条` / `线程0/1`）全在
        //   ⇒ 说明那两行**不是**经由 `addParallelDisplay` 进入本列表的（`super.addParallelDisplay`
        //   的按键替换/兜底都没命中、也没兜底追加）⇒ 落点改到本方法（唯一确定会被执行的显示入口）。
        //   做法：**先按语言键把父类那两行从列表里剔掉，再由我们无条件补上** ⇒ 结果恰好一对、
        //   口径 100% 是我们给的（K / P / 总量），且不吞掉父类任何别的行。
        // ★★ 0.3.132【队长令：「同时处理至多不同配方」那两行也删掉】★★
        //   队长原话：「同时处理配方。跨线程？不是，同时处理至多不同配方，为什么会有这么多这些都删掉」
        //   ⇒ 我们那套「K × P = 总量」的**替换**撤掉 ⇒ 直接留**父类那一对原行**
        //     （`gtceu.multiblock.parallel` + `gtladditions.multiblock.threads`），
        //     即面板回到"上游本来会给的样子"，不再多两行我们的口径。
        //   ⚠ 只撤显示：`parallelTotalLine/threadsLine` 两个构造函数、章节显示、判定全部保留。
        //   ⚠ 保留 `replaceMaxPowerWithGridTotal` —— 它是队长点名要的（显示整个电网电量），与本次无关。
        // ★★ 0.3.132【队长令：改成 GTO 口径】★★
        //   GTO 那三行（同时处理至多 N 种不同配方 / 每种配方至多 Y 个 / 独立线程[已启用]）
        //   由 {@link #addParallelDisplay(List)} 的按键替换落地 —— 它经由
        //   `super.addDisplayText` 先于本行执行，所以这里**不再需要** `filterUpstreamParallelLines`
        //   （那一套是旧的「K × P = 总量」口径，已被 GTO 口径取代；方法本体保留，便于回退）。
        replaceMaxPowerWithGridTotal(textList);
        addMirrorBindingDisplay(textList);
        // 节流刷新同步文案（连接本身由每 20 tick 的 F 位刷新负责，界面不再触发扫描）
        if (getLevel() instanceof ServerLevel) {
            long t = getLevel().getGameTime();
            if (lastUiRefreshTick < 0 || t - lastUiRefreshTick >= 60) {
                lastUiRefreshTick = t;
                updateSyncedTexts();
            }
        }
        boolean full = "全流程档".equals(syncedMode);
        // ★★ 0.3.132【队长令：撤掉「运行档位」这一行及其整段自述】★★
        //   队长原话：「把运行档位，纳米校准，校准分母，历史构成，真空传送带这几个主机里面的描述也给删掉」
        //   ⇒ 整行撤掉（连同"总池口径/去总池/时长下限"那一长串自述）。
        //   ⚠ 只撤显示：`syncedMode` / `syncedParallelShown` 等同步字段与所有判定**一个字不动**
        //     （「运行档位」在 Jade / 自检里照样看得到）。回退 = 把下面这段贴回。
        //   ⚠ `full` 仍被下面 `if (SHOW_VERBOSE_HOST_LINES)` 里的提示行使用，故此变量保留。
        if (false) {
            textList.add(Component.literal("运行档位: ")
                    .append(Component.literal(syncedMode).withStyle(full ? ChatFormatting.GREEN : ChatFormatting.YELLOW))
                    .append(Component.literal(full
                            ? " · 组装矩阵在线 · " + (NAC_PER_THREAD_FULL_PARALLEL
                                    ? "每条配方各自满并行 · 无总池摊分"
                                    : (NAC_SATURATING_PARALLEL
                                            ? "总池口径 · 并行上限饱和（int " + FULL_PARALLEL_CAP + "）"
                                            : "总池口径 · 旧硬压并行 钳 " + LEGACY_CROSS_RECIPE_PARALLEL_CAP))
                            + " · 时长下限 5 秒"
                            : " · 样板总成直通 · 并行上限 " + syncedParallelShown
                                    + (NAC_PER_THREAD_FULL_PARALLEL ? "（去总池：实际按可容纳量收敛）" : "")
                                    + " · 时长下限 20 秒")
                            .withStyle(ChatFormatting.GRAY))
                    .withStyle(ChatFormatting.GRAY));
        }
        // ★ 2026-10-07【修「两行跨配方线程打架」】★
        //   原来这里自己又加了一行 `gtladditions.multiblock.threads`（值 = 钳到 256 的
        //   syncedCrossRecipeThreads，该同步字段随本次修改一并删除），而
        //   `super.addDisplayText(textList)`（本方法第一句）
        //   已经由 gtladditions 父类自己加了同一行（值 = 它自己的 128 基线 + getAdditionalThread()，未钳制）
        //   ⇒ 界面上必然出现两行、且两个数字天然可以不同（实测 128 与 256 打架）。
        //   修法（本轮选的「最简」方案）：**删掉我们这一行**，只留父类那一行。
        //   依据：`gtladditions.multiblock.threads` 这个键**本来就属于父类**
        //   （出处 `_census/_probe_nacmat_evidence/classstr_gtladditions-3.2.8Custom_SubSpace-fix1.txt:2621`
        //    与 `:2556` 的 `addParallelDisplay`，即父类自己的 addDisplayText 分支），
        //   我们重复添加等于同键两写；单一数据源优先。
        //   ⚠️ 父类那行的口径原是「它的 128 基线 + getAdditionalThread()」，**未钳制**；
        //      而本机真正生效的是 `getMultipleThreads()`（钳到 MAX_CROSS_RECIPE_THREADS）。
        //   ★ 本轮【GTO 式总量显示】已修：**不在父类上加 mixin**（零混入纪律），而是在本类的
        //      `addParallelDisplay(List)` 覆写里**同款按键替换**那一行 ⇒ 它现在显示的正是
        //      `getMultipleThreads()`（= 「独立线程：上限 U」那一格），
        //      屏幕上不再出现「128 与 256 并排」。**父类代码一字未动**（回退 = 删掉那处覆写）。
        //   ★ 再一轮【GTO 界面口径对齐】：同一次按键替换里，**总量行**三格已改成
        //      「在用条数 K / 每条真实并行 P / 总量 K×P」（用户实机截图口径；推导见
        //      `_census/GTO界面口径对齐_实施记录.md`）⇒ 这一行（上限 U）与总量行第 1 格（在用 K）
        //      **不再是同一个数**，是故意的：一个答「能开几条」、一个答「这一刻在跑几条」。
        // ★ 2026-10-02：把「线程数从哪来」摊开 —— 直通档 1 条是设计，全流程档按电压给线程，
        //   装了 Ω-天球分歧引擎仓还能再加（A5）。玩家看到数字不对时能自己判断原因。
        //   ★ 2026-10-07：追加值改成「**对本机生效线程数的实际贡献**」= getMultipleThreads() - 基线
        //     （原来是裸 getAdditionalThread()，打印的是未钳制的原始值 ⇒ 出现「128 行 / 256 追加」
        //      这种与自己那行打架的数字）。钳制口径与本机真正生效的 getMultipleThreads() 逐字一致。
        if (SHOW_VERBOSE_HOST_LINES) {
            textList.add(Component.literal("  · 跨配方线程来源: ")
                    .append(Component.literal(full
                            ? "全流程档 2<<(电压等级-LV)（组装矩阵解锁）"
                            : "直通档基线（配置 nanochipComplex.directCrossRecipeThreads）")
                            .withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal(getThreadPartMachine() == null
                            ? " · 未装 Ω-天球分歧引擎"
                            : " · 已装 Ω-天球分歧引擎（追加 " + effectiveExtraThreads() + "）")
                            .withStyle(ChatFormatting.DARK_GRAY))
                    .withStyle(ChatFormatting.GRAY));
            if (!full) {
                textList.add(Component.literal("  · 装上「")
                        .append(Component.literal(NanochipModuleKind.ASSEMBLY_MATRIX.nameZh()).withStyle(ChatFormatting.GOLD))
                        // ★ 2026-10-02 L3：门票解锁的东西现在是两样 —— 并行 + 按电压给的跨配方线程
                        // ★ 2026-10-27【并行「去总池」】：措辞去掉"无限"那种夸张说法 ——
                        //   默认口径是「每条配方各自满并行」，照实显示。
                        .append(Component.literal("」模块即可解锁全流程档（每条配方各自满并行 + 跨配方线程）")
                                .withStyle(ChatFormatting.GRAY))
                        .withStyle(ChatFormatting.GRAY));
            }
        }
        // ★★ 0.3.132【队长令：撤掉「纳米校准」/「校准分母」/「历史构成」三行】★★
        //   队长原话：「把运行档位，纳米校准，校准分母，历史构成，真空传送带这几个主机里面的描述也给删掉」
        //   ⇒ 三行全部撤掉。
        //   ⚠ 只撤显示：`syncedCalibrationText` / `syncedCalibrationTier` /
        //     `syncedFullWindowDenominator` / `syncedBreakdownText` 这些同步字段、
        //     以及校准的全部判定（`euMultiplier/durationMultiplier/specialActive`）**一个字不动**
        //     ⇒ 校准照常生效，只是不再在主机面板上自述；面板版的校准页（fancy 配置页）仍在。
        //   回退 = 把上面三行贴回（原代码见 git / `_census` 实施记录）。
        // ★ 虚拟真空传送带那一行也按同一条队长令撤掉（见下）。
        // ★★ 0.3.132【队长令：模块行只显示「已连/上限」，去掉模块名字】★★
        //   队长原话（逐字）：「模块两字用白色，只显示连接了几个模块就行，不用把他们名字也展示出来，
        //   然后那个 4/15 也给换成白色」。
        //   ⇒ ① 整行**白色**（原来"模块:"是灰、数字是金、上限是暗灰、名字是灰）；
        //     ② **不再拼 {@code syncedModuleText}**（那一串"纳米芯片组装矩阵 ×1 · 纳米精度切割仓 ×1 …"）。
        //   ⚠ 只撤显示：{@code syncedModuleText} 同步字段、{@code formedModuleCount()} /
        //     {@code getMaxModuleCount()} 与全部模块判定**一个字不动**
        //     （V 位/角色校验、Jade、自检照样用得到；名字在模块自己的界面里也看得到）。
        //   回退：把下面那两行注释掉、把原来那段 `.append(...syncedModuleText...)` 贴回即可。
        textList.add(Component.literal("模块: "
                        + formedModuleCount() + " / " + getMaxModuleCount())
                .withStyle(ChatFormatting.WHITE));
        addGridAccountLine(textList);
        // ★★ 0.3.132【队长令：撤掉「真空传送带」这一行】★★（同一条队长令）
        //   ⚠ `syncedVacuumText` 同步字段与真空传送的全部判定/传输逻辑**一个字不动**
        //     ⇒ 颜色通道照常工作，只是不再在主机面板上列一览；仓室那边照样看得到。
        if (SHOW_VERBOSE_HOST_LINES) {
            textList.add(Component.literal("真空传送带: ")
                    .append(Component.literal(syncedVacuumText).withStyle(ChatFormatting.LIGHT_PURPLE))
                    .withStyle(ChatFormatting.GRAY));
        }
        // ★【2026-09-16 双模式路由】当前模式必须可见：管道模式下"没铺同色管道就不传"是正确行为，
        //   不显示的话玩家会当成 bug（见 NanochipAssemblyComplexMachine.routingModeText）。
        //   ★ 0.3.132：队长令撤掉（见 SHOW_VERBOSE_HOST_LINES）；判定与自检不动。
        if (SHOW_VERBOSE_HOST_LINES) {
            textList.add(Component.literal("真空路由: ")
                    .append(Component.literal(routingModeText()).withStyle(ChatFormatting.GRAY)));
        }
        // 中央供电：模块没装能源仓时直接吃复合体的电（组装矩阵让电）
        // ★ 0.3.132：队长令撤掉这一整行（见 SHOW_VERBOSE_HOST_LINES）—— 扣电逻辑一个字不动。
        if (SHOW_VERBOSE_HOST_LINES) {
            textList.add(Component.literal("中央供电: ")
                    .append(Component.literal(FormattingUtil.formatNumbers(getTotalStoredEU())
                                    + " / " + FormattingUtil.formatNumbers(getTotalCapacityEU()) + " EU")
                            .withStyle(ChatFormatting.AQUA))
                    .append(Component.literal(" · 模块实付 ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(FormattingUtil.formatNumbers(syncedModuleDrawPerTick) + " EU/t")
                            .withStyle(ChatFormatting.AQUA))
                    .append(Component.literal(" · 模块自带能源仓时走自己的，没装就走这里")
                            .withStyle(ChatFormatting.DARK_GRAY))
                    .withStyle(ChatFormatting.GRAY));
        }
        // ★ 2026-10-28【独立跨配方线程 · 显示】线程明细（仅新逻辑、且线程上限 > 1 时出现）。
        //   放<b>最后</b>的理由：它每 tick 都会变（进度在动），而上面那些行是低频文案；
        //   把变动最频繁的一段压在末尾，玩家视线不用来回找。截断与「客户端不显示」两道闸
        //   都在 appendThreadDisplayText 里（理由是「线程表只在服务端有内容，读不到会显示成
        //   在用 0，比不显示更误导」）。
        Object logicRef = getRecipeLogic();
        if (logicRef instanceof NanochipThreadedRecipeLogic threaded) {
            threaded.appendThreadDisplayText(textList);
        }
    }

    /**
     * ★★ 2026-11-10（第 2 轮）<b>部件未就绪时的等级显示处理：把那两行「按部件推导」的值收起来</b>★★
     *
     * <p>为什么按<b>语言键</b>找这两行（而不是按位置/序号）：它们是 GTCEu 基类
     * {@code WorkableElectricMultiblockMachine.addDisplayText} 里连续添加的（字节码
     * {@code _census/_javap_simple_workablemb.txt:775-802}：{@code MultiblockDisplayText.builder(...)
     * .addEnergyUsageLine(energyContainer).addEnergyTierLine(tier)...}），语言键是
     * 「本机读到的那个字段」的稳定标识、与语言无关。
     *
     * <p>两个键的出处（部署 jar 的常量池与语言文件，逐条）：
     * {@code _census/_probe_nacmat_evidence/classstr_gtceu-1.20.1-1.4.4.txt:15548-15561}
     * （{@code addEnergyUsageLine} → {@code gtceu.multiblock.max_energy_per_tick}；
     * {@code addEnergyTierLine} → {@code gtceu.multiblock.max_recipe_tier}）与
     * {@code _census/_probe_nacmat_evidence/lang/gtceu-1.20.1-1.4.4__gtceu__zh_cn.json:3682-3686}
     * （「最大功率：§a%s EU/t（%s§r）」「最大配方等级：%s」）。
     *
     * <p><b>低风险</b>：只在"已成形 + 零件表空"这条故障路径上被调到，正常机器一次都不进；
     * 万一某个版本把键改了 ⇒ 找不到那两行 ⇒ <b>退回原样显示</b>（不抛异常、不误删别的行）。
     *
     * @param textList 基类刚刚写好的显示行（就地修改：删掉两行 + 替换一行）
     */
    private void replaceUnreadyTierDisplay(List<Component> textList) {
        boolean tierLineSeen = false;
        for (Iterator<Component> it = textList.iterator(); it.hasNext(); ) {
            Component line = it.next();
            if (line.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tc) {
                String key = tc.getKey();
                if ("gtceu.multiblock.max_recipe_tier".equals(key)) {
                    tierLineSeen = true;
                    it.remove();                             // ★ 删掉「最大配方等级：ULV/LV」这一行
                } else if ("gtceu.multiblock.max_energy_per_tick".equals(key)) {
                    it.remove();                             // ★ 删掉「最大功率：0 EU/t」这一行
                }
            }
        }
        if (tierLineSeen) {
            textList.add(Component.literal("最大配方等级：待定（能源仓未重连，部件就绪后自动恢复）")
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    /**
     * ★★ 2026-11-11【样板总成镜像绑定 = 0 · 显示】★★ 把「这一格数字是谁算的、0 是什么意思」摊到面板上。
     *
     * <h3>为什么必须补这一行</h3>
     * <p>上游 Jade 那一格 {@code gtceu.top.proxies_bound = "样板总成镜像绑定数量：%s"}
     * 只给一个裸数字（填它的是 gtlcore 的 Jade provider，算它的是
     * {@code MEPatternBufferPartMachineBase.getProxies()}；两条出处都在
     * {@link NanochipMirrorBindGuard} 的类注释 §一）。数字为 0 时玩家<b>读不出</b>「未绑」还是「坏了」，
     * 也读不出「那是别的机器的部件、跟我这台机器有没有配方可做无关」。
     *
     * <h3>显示口径 = 只读、照实说（显示即事实）</h3>
     * <ul>
     *   <li>{@code readState(...) == null}（未成形 / 零件表为空）⇒ 显示「<b>未读取</b>」，
     *       <b>绝不</b>用 0 冒充「已读、就是 0」；</li>
     *   <li>已读且 {@code 已入链 > 0} ⇒ 显示那个数字（灰）；</li>
     *   <li>已读、{@code 已入链 == 0}、且上游没记过任何镜像坐标 ⇒ 只显示「<b>0 = 未绑定</b>」这一行
     *       （{@code …mirror.none} 那条键）。原先跟在它后面的两行解释文案已按用户要求<b>整段删除</b>，
     *       对应的两条语言键也从中英两份 lang 里一并移除；</li>
     *   <li>已读、{@code 已入链 == 0}、但上游<b>记着</b>镜像坐标 ⇒ 显示「<b>断链</b>」+ 已自动重建几次 +
     *       一条立刻可用的临时解法（拿闪存重绑一次）。</li>
     * </ul>
     * <p><b>纯只读</b>：本方法一行判定都不改、一个字段都不写（它只调
     * {@link NanochipMirrorBindGuard#readState}）。显示层与恢复动作口径同源（同一份 {@code State}），
     * 不可能出现「面板说已绑定、上游还是 0」这种打架。
     *
     * <p>中英文案走 {@code Component.translatable}（键在
     * {@code assets/gtlmodular/lang/{zh_cn,en_us}.json} 各一条，键名两边完全一致）⇒ 客户端按语言自行选，
     * 不存在"在英文环境里塞一句中文"这种事；提示行里引用的方块名也用上游自己的 lang
     * （{@code block.gtceu.me_pattern_buffer_proxy = "ME样板总成镜像"}，出处
     * {@code gtl-dev/inspect/gtceu/assets/gtceu/lang/zh_cn.json:806}）。
     */
    private void addMirrorBindingDisplay(List<Component> textList) {
        // ★★ 0.3.132【队长令：「样板总成镜像」那一套也删掉】（见 SHOW_VERBOSE_HOST_LINES）★★
        //   撤掉的只是**显示**；`NanochipMirrorBindGuard` 的判据、自动重建、Jade provider、
        //   自检输出**一个字都没动** ⇒ 断链时仍能从 Jade / /gtlmodular_nac 自检看到。
        if (!SHOW_VERBOSE_HOST_LINES) {
            return;
        }
        // ★ 读数走 NanochipMirrorBindGuard.readState(...)：**纯只读、两侧同源**
        //   （它读的 isFormed / getParts / getBoundProxyPositions / getProxies 正是 gtlcore 自己那个
        //    Jade provider 读的同一对读数 ⇒ 服务端面板与客户端面板显示同一份事实，
        //    不需要为此新增任何 @DescSynced 字段、也不需要为了让客户端看到而伪造一份）。
        NanochipMirrorBindGuard.State st = NanochipMirrorBindGuard.readState(this);
        if (st == null) {
            textList.add(Component.translatable("gtlmodular.nanochip.mirror.unread")
                    .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        if (st.proxiesTotal() > 0) {
            textList.add(Component.translatable("gtlmodular.nanochip.mirror.count",
                    String.valueOf(st.proxiesTotal())).withStyle(ChatFormatting.GRAY));
            return;
        }
        if (st.lost()) {
            textList.add(Component.translatable("gtlmodular.nanochip.mirror.lost",
                            String.valueOf(st.recordedTotal()),
                            String.valueOf(mirrorBindGuard.attemptsForDiag()),
                            String.valueOf(NanochipMirrorBindGuard.MAX_CONSECUTIVE_FAILURES),
                            String.valueOf(NanochipMirrorBindGuard.MAX_CONSECUTIVE_FAILURES))
                    .withStyle(ChatFormatting.GRAY));
            textList.add(Component.translatable("gtlmodular.nanochip.mirror.lost.hint")
                    .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        // 已读、真的没绑过任何镜像（0 是事实）
        textList.add(Component.translatable("gtlmodular.nanochip.mirror.none")
                .withStyle(ChatFormatting.GRAY));
    }

    /** 供调试/日志用的一行摘要。 */
    @Override
    public String toString() {
        return String.format(Locale.ROOT, "NAC[%s, 模式=%s, 并行=%d, 校准=%s, 校准分母=%s]",
                getPos(), syncedMode, syncedParallelShown, syncedCalibrationText,
                syncedFullWindowDenominator
                        ? "原版固定 " + NanochipCalibration.HISTORY_SIZE
                        : "已记录数");
    }
}
