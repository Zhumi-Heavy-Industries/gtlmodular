/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.common.collect.Table
 *  com.gregtechceu.gtceu.api.block.ActiveBlock
 *  com.gregtechceu.gtceu.api.capability.IEnergyContainer
 *  com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability
 *  com.gregtechceu.gtceu.api.capability.recipe.IO
 *  com.gregtechceu.gtceu.api.capability.recipe.IRecipeCapabilityHolder
 *  com.gregtechceu.gtceu.api.capability.recipe.IRecipeHandler
 *  com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability
 *  com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability
 *  com.gregtechceu.gtceu.api.gui.fancy.ConfiguratorPanel
 *  com.gregtechceu.gtceu.api.gui.fancy.IFancyConfigurator
 *  com.gregtechceu.gtceu.api.machine.IMachineBlockEntity
 *  com.gregtechceu.gtceu.api.machine.MetaMachine
 *  com.gregtechceu.gtceu.api.machine.TickableSubscription
 *  com.gregtechceu.gtceu.api.machine.feature.IMachineLife
 *  com.gregtechceu.gtceu.api.machine.feature.multiblock.IDisplayUIMachine
 *  com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController
 *  com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart
 *  com.gregtechceu.gtceu.api.machine.multiblock.WorkableMultiblockMachine
 *  com.gregtechceu.gtceu.api.machine.trait.RecipeLogic
 *  com.gregtechceu.gtceu.api.machine.trait.RecipeLogic$Status
 *  com.gregtechceu.gtceu.api.misc.EnergyContainerList
 *  com.gregtechceu.gtceu.api.pattern.MultiblockState
 *  com.gregtechceu.gtceu.api.pattern.MultiblockWorldSavedData
 *  com.gregtechceu.gtceu.api.pattern.error.PatternError
 *  com.gregtechceu.gtceu.api.pattern.error.SinglePredicateError
 *  com.gregtechceu.gtceu.api.pattern.predicates.SimplePredicate
 *  com.gregtechceu.gtceu.api.recipe.GTRecipe
 *  com.gregtechceu.gtceu.api.recipe.GTRecipeType
 *  com.gregtechceu.gtceu.api.recipe.RecipeHelper
 *  com.gregtechceu.gtceu.api.recipe.content.Content
 *  com.gregtechceu.gtceu.api.recipe.ingredient.SizedIngredient
 *  com.gregtechceu.gtceu.api.recipe.logic.OCParams
 *  com.gregtechceu.gtceu.api.recipe.logic.OCResult
 *  com.gregtechceu.gtceu.api.recipe.lookup.RecipeIterator
 *  com.gregtechceu.gtceu.utils.FormattingUtil
 *  com.gtladd.gtladditions.api.machine.feature.IThreadModifierPart
 *  com.gtladd.gtladditions.api.machine.logic.GTLAddMultipleRecipesLogic
 *  com.gtladd.gtladditions.api.machine.logic.GTLAddMultipleTypeRecipesLogic
 *  com.gtladd.gtladditions.api.machine.multiblock.GTLAddWorkableElectricMultipleRecipesMachine
 *  com.gtladd.gtladditions.common.data.ParallelData
 *  com.gtladd.gtladditions.utils.CommonUtils
 *  com.hepdd.gtmthings.api.misc.WirelessEnergyManager
 *  com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced
 *  com.lowdragmc.lowdraglib.syncdata.annotation.Persisted
 *  com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder
 *  com.lowdragmc.lowdraglib.utils.BlockInfo
 *  it.unimi.dsi.fastutil.longs.LongIterator
 *  net.minecraft.ChatFormatting
 *  net.minecraft.core.BlockPos
 *  net.minecraft.core.Direction
 *  net.minecraft.core.Vec3i
 *  net.minecraft.core.registries.BuiltInRegistries
 *  net.minecraft.nbt.CompoundTag
 *  net.minecraft.nbt.Tag
 *  net.minecraft.network.chat.Component
 *  net.minecraft.network.chat.ComponentContents
 *  net.minecraft.network.chat.MutableComponent
 *  net.minecraft.network.chat.contents.TranslatableContents
 *  net.minecraft.resources.ResourceLocation
 *  net.minecraft.server.level.ServerLevel
 *  net.minecraft.server.level.ServerPlayer
 *  net.minecraft.world.entity.LivingEntity
 *  net.minecraft.world.entity.player.Player
 *  net.minecraft.world.item.ItemStack
 *  net.minecraft.world.item.crafting.Ingredient
 *  net.minecraft.world.level.BlockGetter
 *  net.minecraft.world.level.Level
 *  net.minecraft.world.level.block.Block
 *  net.minecraft.world.level.block.state.BlockState
 *  org.jetbrains.annotations.Nullable
 */
package com.gtlmodular.common.machine.nanochip;

import com.google.common.collect.Table;
import com.gregtechceu.gtceu.api.block.ActiveBlock;
import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.IRecipeHandler;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.gui.fancy.ConfiguratorPanel;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyConfigurator;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.feature.IMachineLife;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IDisplayUIMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
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
import com.gregtechceu.gtceu.api.recipe.ingredient.SizedIngredient;
import com.gregtechceu.gtceu.api.recipe.logic.OCParams;
import com.gregtechceu.gtceu.api.recipe.logic.OCResult;
import com.gregtechceu.gtceu.utils.FormattingUtil;
import com.gtladd.gtladditions.api.machine.feature.IThreadModifierPart;
import com.gtladd.gtladditions.api.machine.multiblock.GTLAddWorkableElectricMultipleRecipesMachine;
import com.gtladd.gtladditions.utils.CommonUtils;
import com.gtlmodular.GTLModular;
import com.gtlmodular.common.config.GTLModularConfig;
import com.gtlmodular.common.data.NanochipBlocks;
import com.gtlmodular.common.data.nanochip.NanochipComplexPattern;
import com.gtlmodular.common.data.nanochip.NanochipRecipeTypes;
import com.gtlmodular.common.machine.MultiTypeLookupHost;
import com.gtlmodular.common.machine.nanochip.NanochipCalibration;
import com.gtlmodular.common.machine.nanochip.NanochipConversionMap;
import com.gtlmodular.common.machine.nanochip.NanochipMirrorBindGuard;
import com.gtlmodular.common.machine.nanochip.NanochipModuleKind;
import com.gtlmodular.common.machine.nanochip.NanochipModuleMachine;
import com.gtlmodular.common.machine.nanochip.NanochipPipeGraph;
import com.gtlmodular.common.machine.nanochip.NanochipStructureLogGate;
import com.gtlmodular.common.machine.nanochip.NanochipStructureRefresh;
import com.gtlmodular.common.machine.nanochip.NanochipVacuumNetwork;
import com.gtlmodular.common.machine.tentacle.IThreadHatchPart;
import com.hepdd.gtmthings.api.misc.WirelessEnergyManager;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;
import com.lowdragmc.lowdraglib.utils.BlockInfo;
import it.unimi.dsi.fastutil.longs.LongIterator;
import java.lang.reflect.Field;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.gtlcore.gtlcore.api.machine.multiblock.IModularMachineHost;
import org.gtlcore.gtlcore.api.machine.multiblock.IModularMachineModule;
import org.gtlcore.gtlcore.api.recipe.RecipeRunnerHelper;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongIngredient;
import org.gtlcore.gtlcore.utils.datastructure.ModuleRenderInfo;
import org.jetbrains.annotations.Nullable;

public class NanochipAssemblyComplexMachine
extends GTLAddWorkableElectricMultipleRecipesMachine
implements IDisplayUIMachine, com.gtlmodular.common.machine.engine.IThreadedRecipeMachine,
IModularMachineHost<NanochipAssemblyComplexMachine>,
IMachineLife,
NanochipStructureRefresh.Refreshable,
MultiTypeLookupHost {
    public static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(NanochipAssemblyComplexMachine.class, WorkableMultiblockMachine.MANAGED_FIELD_HOLDER);
    /**
     * ★★ 2026-10-19【主机侧「先蓄够一整条配方的电再开工」总开关】★★
     *
     * <p>用户原话：「模块会在没有电的时候攒电来维持工作，但是主机不会，我需要主机也有这个功能」。
     * {@code true}（默认）= 主机在起跑前要求「本条配方整条所需电量」可获取；{@code false} = <b>完全恢复
     * 本次改动前的行为</b>（起跑不看电量，跑到一半没电才停）。
     *
     * <p>关闭方式：把本常量改成 {@code false} 重新编译即可 —— 判定点唯一（
     * {@code 旧派活实现#setupRecipe(GTRecipe)} 里那一句 {@code if (PRECHARGE...)}），
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
     * （{@code 旧派活实现#lookupRecipeIterator()} 里那一句 {@code if (NAC_MULTI_TYPE_RECIPES)}），
     * 关掉之后候选来源逐字回到「只搜激活类型」，其余一切（线程截断、公平轮转、并行分配、逐槽隔离）不受影响。
     * 这与本类登记 {@code NAC_PRECHARGE_WHOLE_RECIPE} 回退方式的做法同款（纯代码层、可随时回退）。
     *
     * <p><b>零存档影响</b>：本常量不是 {@code @Persisted} 字段、不进 NBT、不改存档格式；
     * 也不改 {@link #getRecipeTypes()} 的返回内容与顺序（下标稳定性不受影响）。
     *
     * <p>⚠️ <b>不承诺「每种类型各自并行」</b>：并行仍由 {@code 旧派活实现#calculateParallels()}
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
    /** 样板总成镜像绑定恢复/诊断（与结构层无关，保留）。 */
    private final NanochipMirrorBindGuard mirrorBindGuard = NanochipMirrorBindGuard.create();
    /** 上一次「真判定」落在哪个服务端 tick；{@link #FOLD_NO_TICK} = 本机还没跑过真判定。 */
    private volatile long foldTick = Long.MIN_VALUE;
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
    private long lastUnformedAuditTick = -10000L;
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
    private final Set<IModularMachineModule<NanochipAssemblyComplexMachine, ?>> modules = new LinkedHashSet();
    /**
     * 最近一次<b>成功匹配</b>拿到的结构 {@code F} 位世界坐标（按坐标排序，顺序稳定；诊断用）。
     *
     * <p>为什么必须缓存：GTCEu 每次开始匹配都会 {@code MultiblockState.clean()} → 重置
     * {@code matchContext}，所以<b>检查失败之后</b> {@link NanochipComplexPattern#moduleSlotsOf} 只会返回空表，
     * 「结构失效」的诊断就没有格子可打。缓存下来之后，失效时仍能逐格打印"这一格现在站的是什么"。
     */
    private final List<BlockPos> lastMatchedModuleSlots = new ArrayList<BlockPos>();
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
    /** 仓位清单心跳间隔（tick）：1 秒一次，与主机既有的 {@value #MODULE_REFRESH_INTERVAL} 同频。 */
    private static final int NAC_SLOT_HEARTBEAT_INTERVAL = 20;
    /** 仓位清单心跳的计数器（只增不减；初值 -1 = 下一次 tick 就跳一次）。 */
    private int nacSlotHeartbeat = -1;
    /** 上一次心跳时结构里的 part 个数（{@code -1} = 还没测过 ⇒ 下一次无条件测一次）。 */
    private int lastNacSlotPartCount = -1;
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
    private long lastForcedResyncTick = -10000L;
    /** 静默窗口的截止 {@code gameTime}（{@code onLoad} 时设定；见 {@link #JOIN_QUIET_TICKS}）。 */
    private long joinQuietUntilTick = -10000L;
    /** 本机的结构状态日志闸（同一结论 10 秒一条 + 跨机器折叠汇总；见 {@link NanochipStructureLogGate}）。 */
    private final NanochipStructureLogGate structureLogGate = new NanochipStructureLogGate();
    /** 「复合体已成形」这条日志的结论名（同时是日志闸的折叠分组键）。 */
    private static final String FORMED_CONCLUSION = "\u590d\u5408\u4f53\u5df2\u6210\u5f62";
    /** 「复合体已成形」正文格式（INFO 与降级后的 debug <b>共用同一条文案</b>，避免两处漂移）。 */
    private static final String FORMED_LINE_FORMAT = "[gtlmodular] \u7eb3\u7c73\u82af\u7247\u7ec4\u88c5\u590d\u5408\u4f53 \u5df2\u6210\u5f62 @{}\uff08\u6863\u4f4d={}\uff0cF \u4f4d\u4e0a\u6210\u5f62\u6a21\u5757={}\uff0c\u5df2\u8fde\u63a5\u6a21\u5757={}/{}\uff0c{}\uff09";
    /** 「复合体结构失效」这条日志的结论名。 */
    private static final String INVALID_CONCLUSION = "\u590d\u5408\u4f53\u7ed3\u6784\u5931\u6548";
    /** 「复合体结构失效」正文格式。 */
    private static final String INVALID_LINE_FORMAT = "[gtlmodular] \u7eb3\u7c73\u82af\u7247\u7ec4\u88c5\u590d\u5408\u4f53 \u7ed3\u6784\u5931\u6548 @{}\uff08\u7b49\u73a9\u5bb6\u8865\u9f50\u7ed3\u6784\uff1b\u672c\u6b21\u65ad\u5f00\u6a21\u5757 {} \u4e2a\uff09";
    /** 「复合体尚未成形（缺件清单）」这条日志的结论名。 */
    private static final String UNFORMED_CONCLUSION = "\u590d\u5408\u4f53\u5c1a\u672a\u6210\u5f62";
    /**
     * ★【结构位置缓存 + 方块移除定点失效】这条日志的结论名（同时是日志闸的折叠分组键）。
     *
     * <p>为什么必须可见（而不是静默）：它是本功能<b>唯一</b>的动作见证 —— 命中缓存 ⇒ 请上游重判一次
     * ⇒ 那一刻日志里要能看出"是因为某一格被移除而判的"，否则下次排障又只能猜
     * （与 {@code NanochipStructureRefresh} 的"恢复动作必须可见"同一条纪律）。
     */
    private static final String REMOVAL_INVALIDATE_CONCLUSION = "\u65b9\u5757\u79fb\u9664\u5b9a\u70b9\u5931\u6548";
    /** 「方块移除定点失效」正文格式（INFO 与降级后的 debug <b>共用同一条文案</b>）。 */
    private static final String REMOVAL_INVALIDATE_LINE_FORMAT = "[gtlmodular] \u7eb3\u7c73\u82af\u7247\u7ec4\u88c5\u590d\u5408\u4f53 \u65b9\u5757\u79fb\u9664 \u21d2 \u5b9a\u70b9\u91cd\u5224 @{}\uff08\u79fb\u9664\u683c={}\uff1b\u8dd1\u4e0d\u8dd1\u5339\u914d\u3001\u6210\u5f62\u8fd8\u662f\u5931\u6548\u5168\u90e8\u7531 gtlcore \u81ea\u5df1\u5224\uff09";
    /**
     * 能量缓存分配间隔（tick）—— wiki：「复合体**每 20 tick**向各模块分配缓存电量」。
     */
    public static final int ENERGY_DISTRIBUTE_INTERVAL = 20;
    /** 上次分配缓存的 tick（{@code -1} = 还没分过）。 */
    private long lastEnergyDistributeTick = -1L;
    /**
     * 「按可获取能力放行」这条诊断日志的限流间隔（tick）＝ 10 秒。
     * 见 {@link #logUnlimitedEnergyAssist(String, long, long)}。
     */
    public static final int UNLIMITED_ASSIST_LOG_INTERVAL = 200;
    /**
     * 上次打「按可获取能力放行」的 tick（{@link Long#MIN_VALUE} = 还没打过）。
     *
     * <p>★ <b>刻意不是 {@code @Persisted} / {@code @DescSynced}</b>：它只用于日志限流，
     * 不进存档、不进网络包 ⇒ <b>零存档格式变更</b>（与 {@link #lastEnergyDistributeTick} 同款，
     * 不属于 {@link #MANAGED_FIELD_HOLDER} 管的同步/持久字段）。
     */
    private long lastUnlimitedAssistLogTick = Long.MIN_VALUE;
    /** 每 20 tick 按结构 {@code F} 位刷新模块连接的服务端订阅。 */
    private TickableSubscription moduleRefreshSub;
    /** 界面同步文案的节流计时（60 tick 一次）。 */
    private long lastUiRefreshTick = -1L;
    @DescSynced
    private String syncedMode = "\u76f4\u901a\u6863";
    @DescSynced
    private String syncedCalibrationText = "\u672a\u6821\u51c6";
    @DescSynced
    private String syncedBreakdownText = "";
    @DescSynced
    private String syncedModuleText = "\u672a\u5b89\u88c5\u6a21\u5757";
    @DescSynced
    private long syncedParallelShown = 1L;
    @DescSynced
    /** 当前校准档位（0 = 未校准）：客户端用来决定文字颜色（校准数据本体不同步）。 */
    private int syncedCalibrationTier;
    @DescSynced
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
    private int syncedActiveFamilyOrdinal = -1;
    @DescSynced
    /**
     * 当前判定分母口径是否 = 原版硬编码的 100000（= 整个历史窗口）。
     *
     * <p>由服务端每 tick 从配置读一次推给客户端，这样界面显示的「分母模式」与真正判定用的口径
     * <b>永远一致</b>（客户端自己读配置在专用服务器上可能与服务端不同）。
     * 初值取 {@code false} = ★ 2026-09-21 队长令「改」之后的配置默认值（分母 = 已记录数）；
     * 这只是"服务端第一次推下来之前"的占位值，真正口径以同步值为准。
     */
    private boolean syncedFullWindowDenominator = false;
    @DescSynced
    /** 真空传送带网络摘要（客户端显示用；服务端网络内容变化时才推送）。 */
    private String syncedVacuumText = "\u65e0";
    /** 真空网络里的虚拟件总数（用于判断是否需要刷新摘要）。 */
    private long lastVacuumTotal = -1L;
    @Persisted
    @DescSynced
    @Nullable
    /**
     * <b>放置者（或放置者所属 FTB 队伍）的 UUID</b> —— 电网取电唯一的身份凭据。
     *
     * <p>★ <b>这是本方案唯一的存档格式变更</b>（新增一个 {@code @Persisted UUID} 键）。
     * 凭据必须持久化：{@code gtmthings} 的余额是<b>按 UUID/队伍</b>记的，
     * 读档后若 UUID 丢了就永远取不到电。
     *
     * <p>★ <b>旧存档兜底（绝不静默失效）</b>：旧档没有这个键 ⇒ 读档后为 {@code null}
     * ⇒ {@link #gridEnergyContainer()} 对本机<b>自动退回"必须有能源仓"的老行为</b>
     * （逐字等于本改动之前），并且：① {@link #onLoad()} 载入时打一条 WARN；
     * ② 电网容器取电被拒时按 {@link #GRID_USER_LOG_INTERVAL} 限流再报一次（含恢复办法）。
     * <b>不是</b>"静默不取电"。
     *
     * <p>{@code @DescSynced} 只为让客户端那几行界面显示（"中央供电"读的就是能源容器实况）
     * 与服务端同口径；它不参与任何判定。
     */
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
    private transient NanochipEnergy.GridEnergyContainer gridEnergyContainer;
    @DescSynced
    /** 各族电路数量（逗号分隔，9 个）与历史总数：给图形进度条用（多方块本体没有配置页，
     *  所以进度条做在结构里真空传送带仓的页面上，数据从这里同步过去）。 */
    private String syncedFamilyCounts = "";

    /** 服务端算出的线程仓线程数，同步给客户端显示（客户端 getParts() 不一定有部件）。 */
    @DescSynced
    private int syncedThreadHatchThreads = 1;
    @DescSynced
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
    private String syncedFamilyTiers = "";
    @DescSynced
    private long syncedHistoryTotal;
    @DescSynced
    @Persisted
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
    private String syncedNacSlots = "";
    /** 仓位清单上限（条）：超出即截断 + WARN（后果只是"超出的那几格外壳保持原样"）。 */
    public static final int MAX_NAC_SLOTS = 256;
    public static final boolean NAC_INDEPENDENT_THREADS = true;
    /**
     * ★★ 2026-09-19（队长方案 A）★★
     * <blockquote>「我希望开启简单模式以后，能够通过使用样板总成直接完成电路的制作」</blockquote>
     *
     * <h4>要解决的问题</h4>
     * 换算（{@code 真件 ⇄ CC}）在 NH 里是<b>控制室的仓位行为</b>（{@code roomEncapsulate} /
     * {@code roomMaterialize}），<b>不是配方</b> ⇒ AE 样板总成永远推不动它。于是简单模式（不铺真空仓）
     * 下玩家有 CC 却没法用 AE 批量换回真电路 —— 只能靠真空输出仓/输入仓手工搬运。
     *
     * <h4>做法（最小改动、且原版模式零变化）</h4>
     * {@code nanochipComplex.nanochipConversionRecipes} 那张表（{@link NanochipRecipeTypes#CONVERSION}）
     * 本来就有 201 条「真件 → CC」（NH 的 99 条 + 自造段），本轮再补 201 条对偶
     * 「CC → 真件」（{@code data/gtlmodular/recipes/nanochip/conversion_reverse/}）。
     * 但配方表要在<b>机器的配方类型列表里</b>才会被 {@code RecipeLogic.searchRecipe()} 搜到
     * （{@code searchRecipe()} 用的是 {@code machine.getRecipeType()}，不是遍历所有表）——
     * 所以这里把换算表<b>追加</b>进主机的类型列表。
     *
     * <h4>为什么必须门控在简单模式</h4>
     * 「原版(NH)模式行为逐字节不变」是本包硬纪律（{@link GTLModularConfig#isNanochipSimpleLogistics()}）：
     * 配置为 {@code false} 时本方法<b>直接返回父类那个数组实例</b>（不复制、不新建、不改下标），
     * 主机配方类型与改动前逐项一致；只有简单模式才多出一项，且<b>追加在末尾</b>
     * ⇒ 下标 0 仍是**主机自己的那张表**（{@code NanochipRecipeTypes.ASSEMBLY_COMPLEX}，
     * 它索引的就是电路组装机那批配方对象 —— 证据见该字段的注释），既有槽位识别/模式下标全部不受影响。
     *
     * <h4>为什么能和「直通档」共存而不串</h4>
     * 猪咪样板总成按样板<b>逐槽识别</b>配方类型（{@code MoltenPatternAssemblyPartMachine.detectRecipeType}：
     * 用「非电路输入被样板输入覆盖 + 配方主产物落在样板输出里」的身份匹配）：
     * 换算样板的身份只在换算表里命中，电路组装机样板只在电路组装机表里命中
     * ⇒ 不会出现「切错模式把料吃掉」的串档。
     * ⚠️ gtlcore 原生 {@code me_pattern_buffer} <b>没有</b>切模式能力（javap：基类只有
     * {@code pushPattern}，无 {@code setActiveRecipeType}）⇒ 换算这类多模式用法要用本模组的
     * 「猪咪样板总成」。
     *
     * <p>缓存：只在首次调用时拼一次数组；配置需要重启才改结构判定，但本表随配置即时生效
     * （配置为 false 时走父类分支，连缓存都不看）。
     */
    private GTRecipeType[] simpleModeRecipeTypes;
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
    public static final int MAX_CROSS_RECIPE_THREADS = 256;
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
     * （{@code 旧派活实现#perRecipeParallelLimit()}），真正的值由「真的放得下」的二分收敛；
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
    public static final int LEGACY_CROSS_RECIPE_PARALLEL_CAP = 0x7FFFFF;
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
     * 「总池分摊」口径（{@code 旧派活实现.parallelBudget()}）。
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
     * 判定点唯一（{@code 旧派活实现#perRecipeParallelLimit()}），改回 {@code false}
     * 重新编译即<b>逐字回退</b>到旧口径。
     */
    public static final boolean NAC_PER_THREAD_FULL_PARALLEL = true;
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
    /** 限流：主机"完成配方"日志最多 1 秒一条（哨兵同 {@link #lastUnformedAuditTick}：**不能**用 Long.MIN_VALUE）。 */
    private long lastCraftLogTick = -10000L;
    /** 限流：{@link #noteHostPrechargeWaiting} 最多 1 秒一条（哨兵同 {@link #noteNoFit}）。 */
    private long lastHostPrechargeLogTick = -10000L;
    /** 限流：{@link #noteNoFit} 最多 1 秒一条。 */
    private long lastNoFitLogTick = -10000L;
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
     * 空列表回退内置默认名单；语义见 {@code GTLModularConfig.NANOCHIP_CALIBRATION_RECIPE_WHITELIST} 的 comment）。
     */
    public static final boolean CALIBRATION_WHITELIST_ENABLED = true;
    /**
     * 「白名单条数已生效」那一行的限流游标（每台机器一条；哨兵 {@code -10_000L}，与
     * {@link #lastNoFitLogTick} 同款，绝不用 {@code Long.MIN_VALUE}）：<b>只打一次</b>
     * —— 它只是"名册接上了、有几条"的现场凭证，不是每批都报的流水账。
     */
    private long lastWhitelistCountLogTick = -10000L;
    /**
     * 「这条配方没有 id」/「白名单未命中」两行的<b>共用</b>限流游标（最多 1 秒一条，
     * 与 {@link #logCraftedRecipe} 同款纪律；哨兵同 {@link #lastNoFitLogTick}）。
     * 一条游标管两种文案：稳态下它们互斥（同一条配方只会落其中一种），不会互相饿死。
     */
    private long lastWhitelistMissLogTick = -10000L;
    /** 校准记账诊断日志的限流游标（最多 1 秒一条；档位变化时无视限流立刻打一条）。 */
    private long lastCalibrationLogTick = -10000L;
    /** 上次打日志时的生效档位（-1 = 还没打过）：用于「档位一变必打一行」。 */
    private int lastLoggedCalibrationTier = -1;
    @DescSynced
    /** 近 1 秒模块实付电（EU/t 平均，客户端显示用）。 */
    private long syncedModuleDrawPerTick;
    private long drawAccumulator;
    private long drawWindowSecond = Long.MIN_VALUE;
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
     * 故取半程哨兵把余量留足（★ 2026-10-19：{@code 旧派活实现} 里那份同义的
     * {@code ON_DEMAND_POWER_FLOOR} <b>已删除</b>，主机起跑闸
     * {@code 旧派活实现#hostAffordableWhole} 改为直接调用本类的
     * {@code isEnergySourceEffectivelyUnlimited()} / {@code getObtainableEnergyEU()}
     * ⇒ 全文件只剩这一个哨兵，单一数据源）。
     */
    public static final long ON_DEMAND_POWER_FLOOR_EU = 0x3FFFFFFFFFFFFFFFL;
    /** 结构所在区块的探测半径/步长：未加载时不能把上游的 UNLOAD_ERROR 当成结构失效。 */
    private static final int LOADED_PROBE_RADIUS = 32;
    private static final int LOADED_PROBE_STEP = 16;
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
    /** 「结构位置表格数 vs 上游 cache 格数」这道自证只打一次（见 {@link #crossCheckStructureCells()}）。 */
    private boolean structureCellsCrossChecked;
    /** 扫描位是否按主机朝向做旋转换算（开关值见 {@link NanochipModuleLinkHost}；现行值 {@code false} = 纯平移）。 */
    public static final boolean NAC_MODULE_SCAN_ROTATE_BY_HOST = NanochipModuleLinkHost.NAC_MODULE_SCAN_ROTATE_BY_HOST;

    private static final long INVALID_GRACE_TICKS = 100L;
    /** 待执行的断开时刻（gameTime）；-1 = 没有待执行断开（哨兵用 -1，不用 Long.MIN_VALUE：减法会溢出）。 */
    private long moduleDisconnectDeadline = -1L;
    /**
     * 存档键：<b>F 位坐标表</b>（见 {@link #restoredModuleSlots}）。
     *
     * <p>与 GTCEu 自己写的那个 {@code isFormed} 键<b>并存</b>、不冲突：GTCEu 恢复的是
     * "成不成形"这个结论，我们补的是"成形时 F 位在哪"这张表。少了后者，载入后
     * {@link #refreshModulesFromStructure()} 就没有可依据的坐标 ——
     * 2026-09-19 实机实测：载入后模块干等 <b>4 分 18 秒</b>。
     */
    private static final String NBT_MODULE_SLOTS = "GTLModularNacModuleSlots";

    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

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
    public void asyncCheckPattern(long periodID) {
        super.asyncCheckPattern(periodID);
        if (!this.isFormed()) {
            this.logUnformedAudit();
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
    public boolean checkPatternWithTryLock() {
        return super.checkPatternWithTryLock();
    }

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
    public boolean checkPatternWithLock() {
        return super.checkPatternWithLock();
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private void confirmFoldedPattern() {
        if (!this.foldDebt) {
            return;
        }
        Level level = this.getLevel();
        if (level == null || level.isClientSide || !this.isFormed()) {
            this.foldDebt = false;
            return;
        }
        MultiblockState state = this.getMultiblockState();
        if (state == null) {
            this.foldDebt = false;
            return;
        }
        if (!this.getPatternLock().tryLock()) {
            return;
        }
        try {
            if (state.cache == null) {
                this.foldDebt = false;
                return;
            }
            LongIterator cacheIterator = state.cache.longIterator();
            while (cacheIterator.hasNext()) {
                BlockState bs;
                BlockPos pos = BlockPos.of((long)cacheIterator.nextLong());
                if (pos.equals((Object)this.getPos()) || (bs = level.getBlockState(pos)).getBlock() instanceof ActiveBlock || level.getBlockEntity(pos) instanceof IMachineBlockEntity) continue;
                this.foldDebt = false;
                state.onBlockStateChanged(pos, bs);
                return;
            }
        }
        finally {
            this.getPatternLock().unlock();
        }
        this.foldDebt = false;
    }

    /**
     * ★ 2026-09-19（队长）：「不要一格一格报，一次把缺件清单给我」。
     *
     * <p>GTCEu 的图案判定**遇到第一个不符格就 return**，逐格原因只留那一格 ⇒ 多个破洞要
     * "修一个 → 看下一条日志 → 再修"。这里用 {@link NanochipComplexPattern#auditStructure} 自己
     * 遍历全部「唯一方块」格，把不符的**一次列出来**（含中文方块名），并**限流 15 秒**一条。
     */
    private void logUnformedAudit() {
        Level lv = this.getLevel();
        if (lv == null || lv.isClientSide || this.isFormed()) {
            return;
        }
        long now = lv.getGameTime();
        if (now - this.lastUnformedAuditTick < 300L) {
            return;
        }
        this.lastUnformedAuditTick = now;
        List<String> audit = NanochipComplexPattern.auditStructure(lv, this.getPos(), 12);
        String fSlots = NanochipDiagnostics.summarizeFSlotContent(this, this.cachedModuleSlots);
        String auditDetail = fSlots + "\n" + String.join((CharSequence)"\n", audit);
        if (!this.structureLogGate.shouldEmitInfo(UNFORMED_CONCLUSION, auditDetail, "\u590d\u5408\u4f53@" + this.getPos().toShortString(), now)) {
            if (GTLModular.LOGGER.isDebugEnabled()) {
                NanochipDiagnostics.logUnformedAuditBody(this, audit, fSlots, true);
            }
            return;
        }
        NanochipDiagnostics.logUnformedAuditBody(this, audit, fSlots, false);
    }

    @Override
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
    public void requestStructureRefresh(ServerPlayer requester) {
        this.pendingRefreshRequester = requester;
        Level level = this.getLevel();
        if (level instanceof ServerLevel) {
            ServerLevel serverLevel = (ServerLevel)level;
            MultiblockWorldSavedData.getOrCreate((ServerLevel)serverLevel).addAsyncLogic((IMultiController)this);
        }
        this.lastUnformedAuditTick = -10000L;
        Level lv = this.getLevel();
        long l = this.refreshReportAtTick = lv == null ? Long.MAX_VALUE : lv.getGameTime() + 40L;
        if (this.isFormed() && !this.getMultiblockState().hasError() && this.hasRegisteredParts()) {
            this.reportRefreshResult();
            return;
        }
        if (requester != null) {
            requester.sendSystemMessage((Component)Component.translatable((String)"gtlmodular.nanochip.structure.refresh.requested").withStyle(ChatFormatting.YELLOW));
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
        List<String> audit;
        ServerPlayer requester = this.pendingRefreshRequester;
        if (requester == null) {
            return;
        }
        this.pendingRefreshRequester = null;
        this.refreshReportAtTick = Long.MAX_VALUE;
        if (this.isFormed()) {
            requester.sendSystemMessage((Component)Component.translatable((String)"gtlmodular.nanochip.structure.refresh.ok").withStyle(ChatFormatting.GREEN));
            return;
        }
        requester.sendSystemMessage((Component)Component.translatable((String)"gtlmodular.nanochip.structure.refresh.fail").withStyle(ChatFormatting.RED));
        Level lv = this.getLevel();
        List<String> list = audit = lv == null ? List.of() : NanochipComplexPattern.auditStructure(lv, this.getPos(), 8);
        if (audit.isEmpty()) {
            requester.sendSystemMessage((Component)Component.translatable((String)"gtlmodular.nanochip.structure.refresh.other").withStyle(ChatFormatting.GRAY));
        } else {
            for (String line : audit) {
                requester.sendSystemMessage((Component)Component.literal((String)("\u00a7c\u00b7 \u00a77" + line)));
            }
        }
    }

    /** 客户端只读：本机取电身份（{@code null} = 没登记放置者 ⇒ 走"必须有能源仓"的老行为）。 */
    public UUID gridPowerUser() {
        return this.gridPowerUser;
    }

    /** 懒建电网容器（不缓存 {@code EnergyContainerList}：基类每次现建，我们跟着现包）。 */
    private NanochipEnergy.GridEnergyContainer gridEnergyContainer() {
        if (this.gridEnergyContainer == null) {
            this.gridEnergyContainer = new NanochipEnergy.GridEnergyContainer(this);
        }
        return this.gridEnergyContainer;
    }

    /** 本机这一刻有没有电网取电身份（实现在 {@link NanochipEnergy}）。 */
    public boolean hasGridPowerIdentity() {
        return NanochipEnergy.hasGridPowerIdentity(this);
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
    public void onMachinePlaced(@Nullable LivingEntity player, ItemStack stack) {
        if (player instanceof Player) {
            Player p = (Player)player;
            this.gridPowerUser = p.getUUID();
            GTLModular.LOGGER.info("[gtlmodular] NAC @{} \u7535\u7f51\u53d6\u7535\u8eab\u4efd\u5df2\u767b\u8bb0\uff1a{}\uff08\u73a9\u5bb6 {}\uff09", new Object[]{this.getPos(), this.gridPowerUser, p.getGameProfile().getName()});
        }
    }

    @Nullable
    /**
     * ★ 2026-11-14【照抄山海 · 身份兜底】<b>本机的电网账户 UUID；实在没有就现生成一个并持久化。</b>
     *
     * <p>逐字照抄山海 {@code MatterCopierMachine.ensureWirelessUuid()}
     * （{@code _census/shanhai-src/com/dishanhai/gt_shanhai/common/machine/misc/MatterCopierMachine.java:461-465}）：
     * <pre>
     *   private void ensureWirelessUuid() {
     *       if (getUuid() == null) {
     *           setUuid(UUID.randomUUID());
     *       }
     *   }
     * </pre>
     * 山海每一个对外入口（{@code :96/117/132/134/231}）都先调它 ⇒ <b>机器永远有一个自己的账户</b>。
     * 山海的"登记制"因此**不会被"没登记"卡死**：放置者 UUID 只是**优先**的账户来源，不是唯一生路。
     *
     * <p>本机同款两条纪律：
     * <ol>
     *   <li>玩家放置 ⇒ {@link #onMachinePlaced} 已写入放置者 UUID（优先用它，直接吃放置者的电网）；</li>
     *   <li>没写进去（旧存档 / 非玩家放置）⇒ 这里随机生成并 {@code markDirty()} 持久化
     *       ⇒ 电网通道始终在线，余额为 0 时表现为<b>「等电」</b>而不是「必须有能源仓」。</li>
     * </ol>
     *
     * <p>客户端**绝不**生成（{@link #gridPowerUser} 是 {@code @DescSynced}，客户端只读服务端镜像）。
     * 读取路径（Jade / 中央供电）随时可能问到这里，故必须无副作用可重入。
     */
    UUID resolveGridUser() {
        if (this.gridPowerUser != null) {
            return this.gridPowerUser;
        }
        Level lv = this.getLevel();
        if (lv == null || lv.isClientSide || !GTLModularConfig.isNanochipGridPowerEnabled()) {
            return null;
        }
        this.gridPowerUser = UUID.randomUUID();
        this.markDirty();
        GTLModular.LOGGER.info("[gtlmodular] NAC @{} \u7535\u7f51\u53d6\u7535\u8eab\u4efd\uff1a\u672c\u673a\u6ca1\u6709\u653e\u7f6e\u8005\u8bb0\u5f55 \u21d2 \u6309\u5c71\u6d77 ensureWirelessUuid \u53e3\u5f84\u73b0\u751f\u6210\u673a\u5668\u81ea\u6709\u8d26\u6237 {} \u5e76\u6301\u4e45\u5316\uff08\u7535\u7f51\u4f59\u989d\u4e3a 0 \u65f6\u672c\u673a\u539f\u5730\u7b49\u7535\uff09", (Object)this.getPos(), (Object)this.gridPowerUser);
        return this.gridPowerUser;
    }

    /**
     * ★ 2026-11-13【NAC 电网取电】<b>取电口</b> —— 电网容器排在基类那批能源仓<b>之前</b>（index 0），
     * 基类原样跟在后面（"先问电网、再问仓"；见方法体内 `list.add(gridEnergyContainer())` 的位置）。
     *
     * <h4>为什么要覆写这里（一处覆盖全部读取点）</h4>
     * {@code getEnergyContainer()} 是 NAC 里<b>唯一没被覆写</b>的能源入口（基类每次现建，见
     * {@code _census/_javap_simple_workablemb.txt:1004-1075}）⇒ 覆写它一处，同时覆盖：
     * ① 配方每 tick 真扣 EU（旧派活实现里的
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
     * （{@link NanochipEnergy.GridEnergyContainer#changeEnergy(long)}），剩下的由能源仓补上；
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
    public EnergyContainerList getEnergyContainer() {
        EnergyContainerList own = super.getEnergyContainer();
        if (!GTLModularConfig.isNanochipGridPowerEnabled() && !GTLModularConfig.isNanochipFreeEnergyEnabled()) {
            return own;
        }
        ArrayList<IEnergyContainer> list = new ArrayList<IEnergyContainer>();
        list.add(this.gridEnergyContainer());
        if (own != null) {
            for (IEnergyContainer c : NanochipEnergy.innerContainers(own)) {
                if (c == null || c == this.gridEnergyContainer()) continue;
                list.add(c);
            }
        }
        return new EnergyContainerList(list);
    }

    /** 电网取电被拒（没有放置者身份）时的限流说明 —— 绝不静默失效。 */
    void warnGridDrawWithoutUser(String site) {
        Level lv = this.getLevel();
        if (lv == null || lv.isClientSide) {
            return;
        }
        long now = lv.getGameTime();
        if (this.lastGridUserLogTick != Long.MIN_VALUE && now - this.lastGridUserLogTick < 200L) {
            return;
        }
        this.lastGridUserLogTick = now;
        GTLModular.LOGGER.warn("[gtlmodular] NAC @{} \u7535\u7f51\u53d6\u7535\u4e0d\u53ef\u7528\uff08{}\uff09\uff1a\u672c\u673a\u6ca1\u6709\u8bb0\u5f55\u5230\u653e\u7f6e\u8005 UUID\uff08\u65e7\u5b58\u6863\u8bfb\u6863 / \u975e\u73a9\u5bb6\u653e\u7f6e\uff09\u3002\u2605 \u672c\u673a\u5df2\u81ea\u52a8\u9000\u56de\u3010\u5fc5\u987b\u6709\u80fd\u6e90\u4ed3\u3011\u7684\u8001\u884c\u4e3a\uff0c\u4e0d\u4f1a\u9759\u9ed8\u4e0d\u53d6\u7535\u3002\u6062\u590d\u529e\u6cd5\uff1a\u62c6\u653e\u4e00\u6b21\u63a7\u5236\u5668\u628a\u653e\u7f6e\u8005\u91cd\u65b0\u767b\u8bb0\uff0c\u5e76\u786e\u8ba4 config \u91cc nanochipComplex.gridPower.enabled = true\u3002\uff08\u6ca1\u6709\u80fd\u6e90\u4ed3\u7684\u673a\u5668\u5728\u6062\u590d\u524d\u4f1a\u539f\u5730\u7b49\u7535\uff0c\u4e0d\u9000\u8fdb\u5ea6\u3001\u4e0d\u6389\u6599\uff09", (Object)this.getPos(), (Object)site);
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
     * 本方法把我们的电网容器摆到<b>同一个位置</b>（配合 {@code NanochipEnergy.GridEnergyContainer} 上的
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
        if (!GTLModularConfig.isNanochipGridPowerEnabled() && !GTLModularConfig.isNanochipFreeEnergyEnabled()) {
            return;
        }
        NanochipEnergy.GridEnergyContainer grid = this.gridEnergyContainer();
        try {
            Table proxy = this.getCapabilitiesProxy();
            if (proxy == null) {
                return;
            }
            IO io = IO.IN;
            EURecipeCapability cap = EURecipeCapability.CAP;
            List existing = (List)proxy.get((Object)io, (Object)cap);
            List writable = existing == null ? new ArrayList() : (existing instanceof ArrayList ? existing : new ArrayList(existing));
            if (writable.contains(grid)) {
                return;
            }
            writable.add(grid);
            proxy.put((Object)io, (Object)cap, writable);
            GTLModular.LOGGER.info("[gtlmodular] NAC @{} \u7535\u7f51\u53d6\u7535\u5df2\u63a5\u5165\u80fd\u529b\u4ee3\u7406\uff1aEURecipeCapability \u8f93\u5165\u5904\u7406\u5668 {} \u4e2a\uff08\u672b\u4f4d=\u7535\u7f51\u5bb9\u5668\uff0c\u8eab\u4efd {}\uff09\u2014\u2014\u8fd9\u4e00\u6b65\u8ba9\u914d\u65b9 EU \u7ed3\u7b97\u771f\u6b63\u5403\u5f97\u5230\u7535\u7f51", new Object[]{this.getPos(), writable.size(), this.gridPowerUser});
        }
        catch (Throwable t) {
            this.warnGridDrawWithoutUser("\u7535\u7f51\u5904\u7406\u5668\u6ce8\u518c\u5931\u8d25\uff1a" + t.getClass().getSimpleName());
        }
    }

    /** {@link #registerGridPowerHandler()} 的反操作：结构失效时把电网容器从能力代理里摘掉。 */
    private void unregisterGridPowerHandler() {
        try {
            Table proxy = this.getCapabilitiesProxy();
            if (proxy == null) {
                return;
            }
            EURecipeCapability cap = EURecipeCapability.CAP;
            IO io = IO.IN;
            List existing = (List)proxy.get((Object)io, (Object)cap);
            NanochipEnergy.GridEnergyContainer grid = this.gridEnergyContainer();
            if (existing == null || !existing.contains(grid)) {
                return;
            }
            List writable = existing instanceof ArrayList ? existing : new ArrayList(existing);
            writable.remove(grid);
            if (writable.isEmpty()) {
                proxy.remove((Object)io, (Object)cap);
            } else {
                proxy.put((Object)io, (Object)cap, (Object)writable);
            }
        }
        catch (Throwable throwable) {
            // empty catch block
        }
    }

    /** 客户端只读：本机发布给客户端的仓位清单（空串 = 拿不到 ⇒ 客户端按"不是仓位"处理）。 */
    public String nacSlotsEncoded() {
        return this.syncedNacSlots;
    }

    /**
     * 服务端：结构状态变化时重建并发布清单（只在**变化**时写字段，避免刷爆同步通道）。
     *
     * <p>客户端调用是空操作（客户端不该写这个字段 —— 与 {@code NanochipVacuumHatchMachine}
     * 的 {@code refreshCasingState()} 同一纪律）。
     */
    private void publishNacSlots() {
        Level level = this.getLevel();
        if (level == null || level.isClientSide) {
            return;
        }
        String encoded = this.encodeNacSlots();
        if (!encoded.equals(this.syncedNacSlots)) {
            this.syncedNacSlots = encoded;
            GTLModular.LOGGER.info("[gtlmodular] NAC\u4ed3\u4f4d\u6e05\u5355\u53d1\u5e03(\u4e3b\u673a): pos={} \u6761\u6570={}", (Object)this.getPos(), (Object)(encoded.isEmpty() ? 0 : encoded.split(",").length));
        }
    }

    /** 结构失效 / 解散 / 拆除：清空清单（客户端索引随之丢掉本机这一份）。 */
    private void clearNacSlots() {
        Level level = this.getLevel();
        if (level != null && level.isClientSide) {
            return;
        }
        if (!this.syncedNacSlots.isEmpty()) {
            this.syncedNacSlots = "";
            GTLModular.LOGGER.info("[gtlmodular] NAC\u4ed3\u4f4d\u6e05\u5355\u6e05\u7a7a(\u4e3b\u673a): pos={}", (Object)this.getPos());
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
        int parts;
        Level level = this.getLevel();
        if (level == null || level.isClientSide) {
            return false;
        }
        if (++this.nacSlotHeartbeat % 20 != 0) {
            return false;
        }
        if (!this.isFormed()) {
            return false;
        }
        int n = parts = this.getParts() == null ? 0 : this.getParts().size();
        if (parts == this.lastNacSlotPartCount) {
            return false;
        }
        int previous = this.lastNacSlotPartCount;
        this.lastNacSlotPartCount = parts;
        this.publishNacSlots();
        GTLModular.LOGGER.info("[gtlmodular] NAC\u5916\u58f3\u8fde\u7247\uff1a\u4ed3\u4f4d\u6e05\u5355\u5fc3\u8df3\u91cd\u53d1(\u4e3b\u673a) pos={} part\u6570 {}\u2192{} \u6e05\u5355={}", new Object[]{this.getPos(), previous, parts, this.syncedNacSlots.isEmpty() ? "\u7a7a\uff080 \u6761\uff09" : this.syncedNacSlots.split(",").length + " \u6761"});
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
        List parts = this.getParts();
        StringBuilder sb = new StringBuilder();
        int kept = 0;
        int dropped = 0;
        for (IMultiPart part : (List<IMultiPart>)parts) {
            if (part == null || part.self() == null) continue;
            if (kept >= 256) {
                ++dropped;
                continue;
            }
            if (kept > 0) {
                sb.append(',');
            }
            sb.append(part.self().getPos().toShortString());
            ++kept;
        }
        if (dropped > 0) {
            GTLModular.LOGGER.warn("[gtlmodular] CTM: \u4ed3\u4f4d\u6e05\u5355\u8d85\u8fc7\u4e0a\u9650 {} \u6761\uff08\u7ed3\u6784\u91cc {} \u4e2a part @{}\uff09\uff0c\u5df2\u622a\u65ad {} \u6761 \u2014\u2014 \u88ab\u622a\u65ad\u7684\u90a3\u51e0\u683c\u5916\u58f3\u4fdd\u6301\u539f\u6837\uff08\u4e0d\u53c2\u4e0e\u8fde\u63a5\u7eb9\u7406\uff09\u3002", new Object[]{256, parts.size(), this.getPos(), dropped});
        }
        return sb.toString();
    }

    /** 同步值解析：第 {@code ordinal} 个电路族的数量（客户端可用）。 */
    public int syncedCountOf(int ordinal) {
        if (this.syncedFamilyCounts == null || this.syncedFamilyCounts.isEmpty()) {
            return 0;
        }
        String[] parts = this.syncedFamilyCounts.split(",");
        if (ordinal < 0 || ordinal >= parts.length) {
            return 0;
        }
        try {
            return Integer.parseInt(parts[ordinal].trim());
        }
        catch (NumberFormatException e) {
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
        if (this.syncedFamilyTiers == null || this.syncedFamilyTiers.isEmpty()) {
            return 0;
        }
        String[] parts = this.syncedFamilyTiers.split(",");
        if (ordinal < 0 || ordinal >= parts.length) {
            return 0;
        }
        try {
            return Integer.parseInt(parts[ordinal].trim());
        }
        catch (NumberFormatException e) {
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
        double den = NanochipCalibration.denominatorFor(this.syncedHistoryTotal, this.syncedFullWindowDenominator);
        if (den <= 0.0) {
            return 0.0;
        }
        return Math.min(1.0, (double)this.syncedCountOf(ordinal) / den);
    }

    /** 当前校准判定分母是否 = 原版硬编码的 100000（服务端推下来的口径，界面显示用）。 */
    public boolean syncedUsesFullWindowDenominator() {
        return this.syncedFullWindowDenominator;
    }

    /** 历史窗口填充度（0~1：够 10 万个电路就满）。 */
    public double syncedHistoryProgress() {
        return Math.min(1.0, (double)this.syncedHistoryTotal / 100000.0);
    }

    public long syncedHistoryTotal() {
        return this.syncedHistoryTotal;
    }

    /**
     * ★★ 0.3.132【校准重构】<b>服务端同步下来的「生效族」ordinal</b>（-1 = 没有任何族达档）。
     *
     * <p>为什么是同步值而不是客户端复算：{@code activeFamily()} 的规则是「档位最高优先、同档比数量、
     * 跳过已合并的隐藏列」——在客户端重写一遍迟早与判定漂移。⇒ 同步服务端那一份（见字段 javadoc）。
     */
    public int syncedActiveFamilyOrdinal() {
        return this.syncedActiveFamilyOrdinal;
    }

    /**
     * ★★ 0.3.132【校准重构】<b>本刻真正使用的判定分母</b>（{@code fullWindow ? 100000 : 历史总数}）。
     *
     * <p>给"下一档还差多少个"那一行用 —— 与 {@link #syncedShareOf} 用的是**同一把尺子**
     * （同为 {@link NanochipCalibration#denominatorFor}），所以界面上的"还差 N 个"与判定一致。
     */
    public double syncedCalibrationDenominator() {
        return NanochipCalibration.denominatorFor(this.syncedHistoryTotal, this.syncedFullWindowDenominator);
    }

    /** 本族现有数量（与 {@code syncedShareOf} 同源；给"下一档"那一行用）。 */
    public long syncedFamilyCount(int ordinal) {
        return this.syncedCountOf(ordinal);
    }

    public NanochipAssemblyComplexMachine(IMachineBlockEntity holder, Object ... args) {
        super(holder, args);
    }

    /**
     * 统一使用通用独立线程引擎。
     *
     * <p>NAC 不再维护专用派活类（已删除）；所有机器共用
     * {@link com.gtlmodular.common.machine.engine.ThreadedRecipeLogic} 与
     * {@link com.gtlmodular.common.machine.engine.ConcurrentRecipeEngine}。
     * 自动样板总成、线程仓、候选来源等兼容只需改通用引擎一处。
     *
     * <p>本方法在父类构造函数里调用，此时本类字段尚未初始化 ⇒ 只传 {@code this}，
     * 不读任何本类字段。
     */
    public RecipeLogic createRecipeLogic(Object ... args) {
        return new NACThreadedRecipeLogic(this);
    }

    public GTRecipeType[] getRecipeTypes() {
        if (!GTLModularConfig.isNanochipSimpleLogistics()) {
            return super.getRecipeTypes();
        }
        if (this.simpleModeRecipeTypes == null) {
            GTRecipeType[] base = super.getRecipeTypes();
            GTRecipeType[] extra = new GTRecipeType[base.length + 1];
            System.arraycopy(base, 0, extra, 0, base.length);
            extra[base.length] = NanochipRecipeTypes.CONVERSION;
            this.simpleModeRecipeTypes = extra;
        }
        return this.simpleModeRecipeTypes;
    }

    /**
     * 与 {@link #getRecipeTypes()} 配套：父类的 {@code getRecipeType()} 是
     * {@code recipeTypes[activeRecipeType]}（读的是父类私有字段）——只复写列表而不复写这一个，
     * 简单模式下下标 1（换算）会越界到父类字段的末尾，直接 {@code ArrayIndexOutOfBoundsException}。
     * 这里按<b>同一个</b> {@link #getRecipeTypes()} 取，并把下标夹到合法范围（配置中途改回原版模式时，
     * 缓存里可能还留着下标 1 ⇒ 夹取后回落到下标 0 = 主机自己的 NAC 表，不抛异常、不跑错表）。
     */
    public GTRecipeType getRecipeType() {
        GTRecipeType[] types = this.getRecipeTypes();
        if (types.length == 0) {
            return null;
        }
        int idx = this.getActiveRecipeType();
        if (idx < 0) {
            idx = 0;
        } else if (idx >= types.length) {
            idx = types.length - 1;
        }
        return types[idx];
    }

    @Override
    /**
     * ★★ <b>「查找侧收窄」×「复合型机器」语义冲突的机器侧回答</b>（{@link MultiTypeLookupHost} 的唯一实现）★★
     *
     * <p><b>为什么 NAC 要回答「是」</b>：本机开着 {@link #NAC_MULTI_TYPE_RECIPES}（默认 {@code true}）时，
     * {@code 旧派活实现#lookupRecipeIterator()} 会在<b>同一轮</b>里遍历 {@link #getRecipeTypes()}
     * 声明的<b>全部</b>类型取候选（对照：关掉它才落回「只搜 {@link #getRecipeType()}` 单值」那条分支）
     * ⇒ 本机这一轮<b>不依赖</b> {@code getActiveRecipeType()} ⇒ 「本轮会被搜的类型集合」= 全部类型。
     *
     * <p>而样板总成侧的查找侧收窄
     * （{@link com.gtlmodular.common.machine.MoltenPatternAssemblyPartMachine#LOOKUP_SIDE_SLOT_NARROWING}、
     * {@link com.gtlmodular.common.machine.LazyPatternAssemblyPartMachine#LOOKUP_SIDE_SLOT_NARROWING}）
     * 是按 {@code getActiveRecipeType()}（<b>单值</b>）收的 ⇒ 会把这个集合里「非激活类型」的<b>冷</b>样板槽
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
    public boolean searchesAllRecipeTypes() {
        return true;
    }

    /** 通用引擎的固定线程基线；NAC 当前由线程仓/1 条决定，见 {@link #getMultipleThreads()}。 */
    @Override
    public int baseThreads() {
        return this.getMultipleThreads();
    }

    /** 每条线程的并行倍率；NAC 用满并行上限，实际仍由“放得下”收敛。 */
    @Override
    public int parallelMultiplier() {
        return Math.max(1, this.getMaxParallel());
    }

    /** NAC 不使用普通并行仓。 */
    @Override
    public boolean usesParallelHatch() {
        return false;
    }

    private int countThreadHatchThreads() {
        long total = 0L;
        for (IMultiPart part : this.getParts()) {
            IThreadHatchPart hatch;
            if (!(part instanceof IThreadHatchPart) || (total += (long)Math.max(0, (hatch = (IThreadHatchPart)part).getThreads())) < Integer.MAX_VALUE) continue;
            return Integer.MAX_VALUE;
        }
        return (int)total;
    }

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
    public boolean isActive() {
        return this.isFormed() && this.isWorkingEnabled();
    }

    /** 是否处于「全流程档」：结构里挂着已成形的组装矩阵模块。 */
    public boolean fullMode() {
        for (IModularMachineModule m : this.getModules()) {
            NanochipModuleMachine module;
            if (!(m instanceof NanochipModuleMachine) || !(module = (NanochipModuleMachine)m).isFormed() || module.getModuleKind() != NanochipModuleKind.ASSEMBLY_MATRIX) continue;
            return true;
        }
        return false;
    }

    /** 配方时长下限（tick）：全流程 5 秒，直通 20 秒。 */
    public int minDurationTicks() {
        return this.fullMode() ? 100 : 400;
    }

    /** 直通档并行上限（配置项，默认 1024）。 */
    public int directParallelCap() {
        return Math.max(1, GTLModularConfig.getNanochipDirectParallelCap());
    }

    /** 直通档的跨配方线程基线（配置项，默认 1；上界 16）。 */
    public int directThreads() {
        return Math.max(1, Math.min(16, GTLModularConfig.getNanochipDirectCrossRecipeThreads()));
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
        if (!this.isFormed()) {
            return 1;
        }
        if (!this.fullMode()) {
            return this.directThreads();
        }
        long threads = 2L << Math.min(30, Math.max(0, this.getTier() - 1));
        return (int)Math.min(256L, threads);
    }

    /**
     * 本机当前生效的跨配方线程数。
     *
     * <p>口径：结构里有线程仓 ⇒ 按线程仓设定值（上限 256）；没有线程仓 ⇒ 1 条。
     * 不再回落到电压基线 / Ω-天球分歧引擎仓，避免“没放线程仓却显示 128/256”。
     *
     * <p>通用引擎通过 {@link com.gtlmodular.common.machine.engine.IThreadedRecipeMachine#effectiveThreads()}
     * 读这个口径（历史接口 {@code NanochipThreadHost} 已随 P1-f 删除）。
     */
    public int getMultipleThreads() {
        if (this.getLevel() != null && this.getLevel().isClientSide) {
            return Math.max(1, this.syncedThreadHatchThreads);
        }
        int hatch = this.countThreadHatchThreads();
        if (hatch > 0) {
            return Math.max(1, Math.min(256, hatch));
        }
        return 1;
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
        return Math.max(0, this.getMultipleThreads() - this.baseCrossRecipeThreads());
    }

    /**
     * 是否真的处于「可跨配方」状态（线程 &gt; 1）。
     *
     * <p>只用于显示与诊断（界面那行「跨配方线程」在直通档恒为 1，仍然照显 —— 因为
     * 「显示即事实」要求把真并发条数摊给玩家看）。
     */
    public boolean hasCrossRecipeThreads() {
        return this.isFormed() && this.getMultipleThreads() > 1;
    }

    public IThreadModifierPart getThreadPartMachine() {
        return this.threadPartMachine;
    }

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
    public int getMaxParallel() {
        if (!this.isFormed()) {
            return 1;
        }
        if (this.fullMode()) {
            return Integer.MAX_VALUE;
        }
        long parallel = this.directParallelCap();
        for (NanochipModuleKind kind : NanochipModuleKind.values()) {
            int count = Math.min(2, this.countModules(kind));
            for (int i = 0; i < count; ++i) {
                parallel = NanochipAssemblyComplexMachine.saturatingMul(parallel, (long)kind.parallelFactor());
            }
        }
        return Math.max(1, NanochipAssemblyComplexMachine.saturatingToInt(parallel));
    }

    protected void addParallelDisplay(List<Component> textList) {
        if (textList == null) {
            return;
        }
        super.addParallelDisplay(textList);
        NanochipStateSync.addParallelDisplay(this, textList);
    }

    /** 已成形的指定种类模块数量（实现在 {@link NanochipModuleLinkHost}）。 */
    public int countModules(NanochipModuleKind kind) {
        return NanochipModuleLinkHost.countModules(this, kind);
    }

    /** 全部已成形模块数（实现在 {@link NanochipModuleLinkHost}）。 */
    public int formedModuleCount() {
        return NanochipModuleLinkHost.formedModuleCount(this);
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
        double mul = 1.0;
        for (IModularMachineModule m : this.getModules()) {
            NanochipModuleKind kind;
            NanochipModuleMachine module;
            if (!(m instanceof NanochipModuleMachine) || !(module = (NanochipModuleMachine)m).isFormed() || !(kind = module.getModuleKind()).appliesTo(family) || kind.euFactor() >= 1.0) continue;
            double factor = kind.euFactor();
            if (kind.isWaterScaledOrganizer()) {
                double strength = module.waterStrength();
                if (strength <= 0.0) continue;
                NanochipCalibration.Family waterFamily = kind.organizerFamily();
                if (waterFamily != null && this.calibration.specialActive(waterFamily)) {
                    strength *= 2.0;
                }
                factor = Math.pow(factor, Math.min(4.0, strength));
            } else if (kind == NanochipModuleKind.BOARD_PROCESSOR) {
                factor *= module.impurityEuMultiplier();
            }
            mul *= factor;
        }
        return Math.max(0.1, Math.min(10.0, mul));
    }

    /**
     * 模块给出的时长倍率（累乘）。下限 0.05×。
     *
     * <p>注意：原版「晶体满校准 → 蚀刻阵列的激光源仓生效」这条<b>不在这里</b> ——
     * 它作用在蚀刻阵列自己的加工上，已按原版公式落在
     * {@link NanochipModuleMachine#moduleOverclock}（EU ÷ 每 256A 一档、时长 ÷ (仓等级−9)）。
     */
    public double moduleDurationMultiplier(NanochipCalibration.Family family) {
        double mul = 1.0;
        for (IModularMachineModule m : this.getModules()) {
            NanochipModuleKind kind;
            NanochipModuleMachine module;
            if (!(m instanceof NanochipModuleMachine) || !(module = (NanochipModuleMachine)m).isFormed() || !(kind = module.getModuleKind()).appliesTo(family) || kind.durationFactor() >= 1.0) continue;
            double factor = kind.durationFactor();
            if (kind.isWaterScaledOrganizer()) {
                double strength = module.waterStrength();
                if (strength <= 0.0) continue;
                NanochipCalibration.Family waterFamily = kind.organizerFamily();
                if (waterFamily != null && this.calibration.specialActive(waterFamily)) {
                    strength *= 2.0;
                }
                factor = Math.pow(factor, Math.min(4.0, strength));
            }
            mul *= factor;
        }
        return Math.max(0.05, mul);
    }

    public static GTRecipe nacRecipeModifier(MetaMachine machine, GTRecipe recipe, OCParams params, OCResult result) {
        if (!(machine instanceof NanochipAssemblyComplexMachine)) {
            return null;
        }
        NanochipAssemblyComplexMachine nac = (NanochipAssemblyComplexMachine)machine;
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
        int overclocks;
        long maxVoltage = this.getOverclockVoltage();
        if (maxVoltage <= 0L) {
            return null;
        }
        GTRecipe copy = recipe.copy();
        long eut = Math.max(1L, RecipeHelper.getInputEUt((GTRecipe)copy));
        int duration = Math.max(1, copy.duration);
        int floorTicks = this.minDurationTicks();
        for (overclocks = 0; overclocks < 2 && eut <= maxVoltage / 4L && duration / 2 >= floorTicks; ++overclocks) {
            eut *= 4L;
            duration /= 2;
        }
        NanochipCalibration.Family family = NanochipAssemblyComplexMachine.familyOf(recipe);
        double euMul = this.calibration.euMultiplier() * this.moduleEuMultiplier(family);
        double durMul = this.calibration.durationMultiplier(family) * this.moduleDurationMultiplier(family);
        long newEut = (long)Math.max(1.0, Math.min(9.223372036854776E18, (double)eut * euMul));
        int newDuration = (int)Math.max((long)floorTicks, Math.min(Integer.MAX_VALUE, Math.round((double)duration * durMul)));
        RecipeHelper.setInputEUt((GTRecipe)copy, (long)newEut);
        copy.duration = newDuration;
        copy.ocTier = overclocks;
        if (GTLModularConfig.isPatternDebugLogsEnabled()) {
            long nowTick;
            long l = nowTick = this.getLevel() == null ? 0L : this.getLevel().getGameTime();
            if (nowTick - this.lastRecipeBuildLogTick >= 20L) {
                this.lastRecipeBuildLogTick = nowTick;
                NanochipCalibration.Family active = this.calibration.activeFamily();
                GTLModular.LOGGER.info("[gtlmodular] NAC \u914d\u65b9\u5b9a\u6863 @{}: \u672c\u914d\u65b9\u65cf={} \u751f\u6548\u65cf={}(\u7b2c{}\u6863) \u8d85\u9891={}\u6b21 EUt {}\u2192{} \u65f6\u957f {}\u2192{}t\uff08\u4e0b\u9650{}t\uff09 \u500d\u7387[\u6821\u51c6EU\u00d7{} \u6a21\u5757EU\u00d7{} \u21d2 \u00d7{}\uff1b\u6821\u51c6\u65f6\u957f\u00d7{} \u6a21\u5757\u65f6\u957f\u00d7{} \u21d2 \u00d7{}] \u21d2 \u6700\u7ec8 {}t\uff08{}.{}s\uff09", new Object[]{this.getPos(), family, active == null ? "\u65e0" : active, active == null ? 0 : this.calibration.tierOf(active), overclocks, eut, newEut, Math.max(1, copy.duration == 0 ? 0 : duration), newDuration, floorTicks, this.calibration.euMultiplier(), this.moduleEuMultiplier(family), euMul, this.calibration.durationMultiplier(family), this.moduleDurationMultiplier(family), durMul, newDuration, newDuration / 20, newDuration % 20});
            }
        }
        return copy;
    }

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
        NanochipCalibration.Family fromData = NanochipAssemblyComplexMachine.calibrationFamilyFromData(recipe);
        if (fromData != null) {
            return fromData;
        }
        try {
            List outs = recipe.getOutputContents((RecipeCapability)ItemRecipeCapability.CAP);
            for (Content c : (List<Content>)outs) {
                ItemStack stack = NanochipAssemblyComplexMachine.firstItemStackOf(c.content);
                if (stack == null || stack.isEmpty()) continue;
                return NanochipCalibration.Family.of(stack);
            }
        }
        catch (RuntimeException runtimeException) {
            // empty catch block
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
            name = "BIOWARE";
        }
        try {
            return NanochipCalibration.Family.valueOf(name);
        }
        catch (IllegalArgumentException e) {
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
    static ItemStack firstItemStackOf(Object content) {
        if (content instanceof ItemStack) {
            ItemStack stack = (ItemStack)content;
            return stack.isEmpty() ? null : stack;
        }
        if (content instanceof ItemStack[]) {
            ItemStack[] stacks;
            for (ItemStack stack : stacks = (ItemStack[])content) {
                if (stack == null || stack.isEmpty()) continue;
                return stack;
            }
            return null;
        }
        if (content instanceof Ingredient) {
            Ingredient ingredient = (Ingredient)content;
            try {
                ItemStack[] stacks = ingredient.getItems();
                if (stacks != null) {
                    for (ItemStack stack : stacks) {
                        if (stack == null || stack.isEmpty()) continue;
                        return stack;
                    }
                }
            }
            catch (RuntimeException e) {
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
    static long producedCountOf(Object content) {
        if (content instanceof LongIngredient) {
            LongIngredient longIngredient = (LongIngredient)((Object)content);
            return longIngredient.getActualAmount();
        }
        if (content instanceof SizedIngredient) {
            SizedIngredient sized = (SizedIngredient)content;
            return sized.getAmount();
        }
        ItemStack stack = NanochipAssemblyComplexMachine.firstItemStackOf(content);
        return stack == null ? 1L : Math.max(1L, (long)stack.getCount());
    }

    /**
     * ★ 2026-09-19（队长：「他输出端口转换了以后，还是 CC 没有变成真实电路」）：
     * <b>主机到底跑了哪条配方</b> —— 完成时打一行「配方表 + 输入 → 输出」（限流 1 秒）。
     *
     * <p>为什么必须有：换算表里**两个方向都是可下单配方**（真件→CC / CC→真件），
     * 写反方向时现象就是"还是封装形态"，而此前机器从不打"我跑了哪条配方" ⇒ 只能猜。
     * 这一行把方向写死：看输出的中文名就知道跑的是不是还原方向。
     */
    void logCraftedRecipe(GTRecipe recipe) {
        Level lv = this.getLevel();
        if (lv == null || lv.isClientSide || recipe == null) {
            return;
        }
        long now = lv.getGameTime();
        if (now - this.lastCraftLogTick < 20L) {
            return;
        }
        this.lastCraftLogTick = now;
        GTRecipeType activeType = this.getRecipeType();
        GTLModular.LOGGER.info("[gtlmodular] \u4e3b\u673a\u5b8c\u6210\u914d\u65b9 [\u914d\u65b9\u8868 {} / \u672c\u673a\u6a21\u5f0f {}]\uff1a\u8f93\u5165 {} \u2192 \u8f93\u51fa {}\uff08{} tick\uff09", new Object[]{recipe.getType().registryName, activeType == null ? "?" : activeType.registryName, NanochipDiagnostics.renderItemContents(recipe.getInputContents((RecipeCapability)ItemRecipeCapability.CAP)), NanochipDiagnostics.renderItemContents(recipe.getOutputContents((RecipeCapability)ItemRecipeCapability.CAP)), recipe.duration});
    }

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
        Level lv = this.getLevel();
        if (lv == null || lv.isClientSide) {
            return;
        }
        long now = lv.getGameTime();
        if (now - this.lastNoFitLogTick < 20L) {
            return;
        }
        this.lastNoFitLogTick = now;
        Object limitText = budget == Long.MAX_VALUE ? "\u6bcf\u6761\u914d\u65b9\u5404\u81ea\u6ee1\u5e76\u884c \u00b7 \u53bb\u603b\u6c60\u540e\u4e0a\u9650 Long.MAX_VALUE" : "\u5e76\u884c\u603b\u6c60 " + budget;
        GTLModular.LOGGER.info("[gtlmodular] NAC \u672c\u6b21\u641c\u7d22\u300c\u6709 {} \u6761\u5019\u9009\u4f46\u4e00\u6761\u90fd\u653e\u4e0d\u4e0b\u300d\uff08{}\uff0c\u6a21\u5f0f {}\uff09\u21d2 \u672c tick \u4e0d\u5f00\u5de5\u3002\u8fd9\u4e0d\u662f\u300c\u6ca1\u5339\u914d\u4e0a\u914d\u65b9\u300d\uff0c\u800c\u662f\u300c\u653e\u5927\u540e\u7684\u8f93\u5165/\u4ea7\u51fa\u653e\u4e0d\u4e0b\u300d\uff1b\u68c0\u4fee\u65b9\u5411\uff1a\u8f93\u51fa\u4ed3/AE \u5269\u4f59\u5bb9\u91cf\u3001\u8f93\u5165\u5e93\u5b58\u662f\u5426\u591f 1 \u4efd\u6599\u3002", new Object[]{candidates, limitText, this.syncedMode});
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
        Level lv = this.getLevel();
        if (lv == null || lv.isClientSide) {
            return;
        }
        long now = lv.getGameTime();
        if (now - this.lastHostPrechargeLogTick < 20L) {
            return;
        }
        this.lastHostPrechargeLogTick = now;
        GTLModular.LOGGER.info("[gtlmodular] NAC \u4e3b\u673a\u9884\u84c4\u7535\uff1a\u672c\u6761\u914d\u65b9\u6574\u6761\u9700\u8981 {} EU\uff0c\u590d\u5408\u4f53\u5f53\u524d {} EU\uff08\u53ef\u83b7\u53d6\u80fd\u529b {} EU\uff0c\u6700\u5927\u8f93\u5165\u529f\u7387 {} EU/t\uff0c\u6a21\u5f0f {}\uff09\u21d2 \u672c tick \u4e0d\u5f00\u5de5\uff0c\u6512\u591f\u624d\u5f00\u59cb\u3002\u82e5\u8fd9\u662f\u300c\u65e0\u9650/\u6309\u9700\u73b0\u53d6\u80fd\u6e90\u300d\uff08\u65e0\u7ebf\u7535\u7f51\u7ec8\u7aef / \u521b\u9020\u80fd\u6e90\u4ed3\uff09\uff0c\u8bf4\u660e\u5bb9\u91cf\u4e0a\u9650\u6ca1\u8bfb\u5230 \u2014\u2014 \u68c0\u4fee\u65b9\u5411\uff1a\u8fd9\u4e9b\u4ed3\u5ba4\u662f\u5426\u771f\u7684\u6302\u5728 NAC \u7ed3\u6784\u4e0a\uff08\u5224\u636e\u89c1 isEnergySourceEffectivelyUnlimited\uff09\u3002", new Object[]{need, stored, this.getObtainableEnergyEU(), this.getMaxInputPower(), this.syncedMode});
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
                if (recipe.getType() == NanochipRecipeTypes.CONVERSION) {
                    List outs = recipe.getOutputContents((RecipeCapability)ItemRecipeCapability.CAP);
                    for (Content c : (List<Content>)outs) {
                        ResourceLocation realId;
                        ItemStack real;
                        ItemStack produced = NanochipAssemblyComplexMachine.firstItemStackOf(c.content);
                        if (produced == null || produced.isEmpty() || !NanochipConversionMap.isCc(produced) || (real = NanochipConversionMap.materialize(produced)) == null || real.isEmpty() || (realId = BuiltInRegistries.ITEM.getKey(real.getItem())) == null) continue;
                        return NanochipCalibration.Family.of(realId);
                    }
                }
            }
            catch (RuntimeException runtimeException) {
                // empty catch block
            }
        }
        return NanochipAssemblyComplexMachine.familyOf(recipe);
    }

    /** 一次配方完成：把产出的电路按族记入 10 万电路历史窗口。 */
    private void recordProduction(GTRecipe recipe) {
        ResourceLocation whitelistRecipeId;
        Level lv = this.getLevel();
        if (lv == null || lv.isClientSide || recipe == null) {
            return;
        }
        if (this.lastWhitelistCountLogTick < 0L) {
            this.lastWhitelistCountLogTick = 0L;
            GTLModular.LOGGER.debug("[gtlmodular] \u6821\u51c6\u767d\u540d\u5355\u5df2\u751f\u6548\uff1a\u5f53\u524d {} \u6761\uff08\u914d\u7f6e\u9879 nanochipComplex.calibrationRecipeWhitelist\uff1b\u7a7a\u5217\u8868 = \u56de\u9000\u5185\u7f6e\u9ed8\u8ba4\u540d\u5355\uff09\u3002\u4e0d\u5728\u6b64\u540d\u518c\u91cc\u7684\u914d\u65b9\u4e00\u5f8b\u4e0d\u8ba1\u5165\u6821\u51c6\u3002", (Object)GTLModularConfig.getNanochipCalibrationRecipeWhitelistCount());
        }
        if ((whitelistRecipeId = recipe.getId()) == null) {
            long now = lv.getGameTime();
            if (now - this.lastWhitelistMissLogTick >= 20L) {
                this.lastWhitelistMissLogTick = now;
                GTLModular.LOGGER.debug("[gtlmodular] \u6821\u51c6\u767d\u540d\u5355\uff1a\u8fd9\u6761\u914d\u65b9\u6ca1\u6709 id\uff08recipe.getId() == null\uff09\u21d2 \u4e0d\u8ba1\u5165\u6821\u51c6\u3002\u68c0\u4fee\u65b9\u5411\uff1a\u8be5\u914d\u65b9\u662f\u4e0d\u662f\u8fd0\u884c\u65f6\u52a8\u6001\u6784\u5efa\uff08\u6ca1\u6709\u6570\u636e\u5305 id\uff09\u7684\uff1f\u82e5\u662f\u81ea\u6709\u914d\u65b9\uff0c\u8865\u4e00\u4efd JSON \u8ba9\u5b83\u62ff\u5230 id \u5373\u53ef\u88ab\u540d\u518c\u6536\u5f55\uff08@{}\uff09\u3002", (Object)this.getPos());
            }
            return;
        }
        if (!NanochipCalibration.whitelistMatches(whitelistRecipeId, GTLModularConfig.getNanochipCalibrationRecipeWhitelist())) {
            long now = lv.getGameTime();
            if (now - this.lastWhitelistMissLogTick >= 20L) {
                this.lastWhitelistMissLogTick = now;
                GTLModular.LOGGER.debug("[gtlmodular] \u6821\u51c6\u767d\u540d\u5355\u672a\u547d\u4e2d \u21d2 \u8fd9\u6761\u914d\u65b9\u4e0d\u8ba1\u5165\u6821\u51c6\uff1a{}\uff08\u8981\u653e\u5f00\u5b83\uff1a\u628a\u8fd9\u4e00\u6574\u884c id \u590d\u5236\u8fdb\u914d\u7f6e\u9879 nanochipComplex.calibrationRecipeWhitelist\uff1b\u524d\u7f00\u5339\u914d\u5c31\u5728\u672b\u5c3e\u52a0\u4e00\u4e2a *\uff09", (Object)whitelistRecipeId);
            }
            return;
        }
        List outs = recipe.getOutputContents((RecipeCapability)ItemRecipeCapability.CAP);
        if (outs.isEmpty()) {
            return;
        }
        int unresolved = 0;
        for (Content c : (List<Content>)outs) {
            ItemStack stack = NanochipAssemblyComplexMachine.firstItemStackOf(c.content);
            if (stack == null || stack.isEmpty()) {
                ++unresolved;
                continue;
            }
            NanochipCalibration.Family family = NanochipAssemblyComplexMachine.familyForAccounting(recipe);
            int amount = NanochipAssemblyComplexMachine.saturatingToInt(Math.max(1L, NanochipAssemblyComplexMachine.producedCountOf(c.content)));
            this.calibration.record(family, amount);
            this.logCalibrationRecord(family, amount);
        }
        if (unresolved > 0) {
            GTLModular.LOGGER.debug("[gtlmodular] \u6821\u51c6\u8bb0\u8d26 @{}\uff1a\u8fd9\u6761\u914d\u65b9\u6709 {} \u4efd\u7269\u54c1\u4ea7\u51fa\u8bc6\u522b\u4e0d\u51fa\u7269\u54c1\u7c7b\u578b \u21d2 \u8fd9\u4e9b\u4efd\u6570**\u672a\u8bb0\u5165**\u6821\u51c6\uff08\u4e0d\u8bb0 0\u3001\u4e0d\u8bb0\u5047\u65cf\uff09\u3002\u8bc6\u522b\u4e0d\u51fa = content \u65e2\u4e0d\u662f ItemStack/ItemStack[]\uff0c\u4e5f\u4e0d\u662f\u53ef\u89e3\u6790\u7684 Ingredient\uff1b\u68c0\u4fee\u65b9\u5411\u89c1 NanochipAssemblyComplexMachine#firstItemStackOf \u7684\u6ce8\u91ca\u3002", (Object)this.getPos(), (Object)unresolved);
        }
        this.updateSyncedTexts();
    }

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
        Level lv = this.getLevel();
        if (lv == null || lv.isClientSide) {
            return;
        }
        int tier = this.calibration.activeTier();
        boolean tierChanged = tier != this.lastLoggedCalibrationTier;
        long now = lv.getGameTime();
        if (!tierChanged && now - this.lastCalibrationLogTick < 20L) {
            return;
        }
        this.lastCalibrationLogTick = now;
        this.lastLoggedCalibrationTier = tier;
        double den = this.calibration.denominator();
        GTLModular.LOGGER.info("[gtlmodular] \u6821\u51c6\u8bb0\u8d26 @{}\uff1a{} +{} \u21d2 \u672c\u65cf {} / \u5386\u53f2 {} \u00b7 \u5206\u6bcd {}\uff08{}\uff09 \u21d2 \u5360\u6bd4 {}%\uff08\u751f\u6548\u6863\u4f4d {}\uff09", new Object[]{this.getPos(), family.nameZh(), amount, this.calibration.countOf(family), this.calibration.total(), den <= 0.0 ? "0" : String.format(Locale.ROOT, "%.0f", den), NanochipCalibration.useFullWindowDenominator() ? "\u539f\u7248\u56fa\u5b9a 100000" : "\u5df2\u8bb0\u5f55\u6570", String.format(Locale.ROOT, "%.3f", den <= 0.0 ? 0.0 : (double)this.calibration.countOf(family) / den * 100.0), tier});
    }

    /**
     * 供模块（组装矩阵）调用：走完整链路时电路是模块产出的，但校准属于复合体。
     * 服务端才记账，客户端调用直接忽略。
     */
    public void recordExternalProduction(GTRecipe recipe) {
        this.recordProduction(recipe);
    }

    /** 每 tick 把档位/校准/模块摘要同步到客户端（供界面文字使用）。 */
    private void tickSync() {
        Level lv = this.getLevel();
        if (lv == null || lv.isClientSide) {
            return;
        }
        this.tickDrawWindow();
        this.updateSyncedTexts();
        if (!this.isFormed()) {
            this.logUnformedAudit();
            if (this.moduleDisconnectDeadline >= 0L && lv.getGameTime() >= this.moduleDisconnectDeadline) {
                this.moduleDisconnectDeadline = -1L;
                int dropped = this.modules.size();
                this.safeClearModules();
                GTLModular.LOGGER.info("[gtlmodular] NAC @{} \u5931\u6548\u5bbd\u9650\u671f\uff08{} tick\uff09\u5230 \u21d2 \u65ad\u5f00\u6a21\u5757 {} \u4e2a\uff08\u5bbd\u9650\u671f\u5185\u82e5\u91cd\u65b0\u6210\u5f62\u5219\u4e0d\u65ad\u5f00 \u2014\u2014 \u8fd9\u662f 0.1.21 \u7684\u8fde\u63a5\u53bb\u6296\uff09", new Object[]{this.getPos(), 100L, dropped});
            }
        } else if (this.moduleDisconnectDeadline >= 0L) {
            this.moduleDisconnectDeadline = -1L;
        }
        if (this.pendingRefreshRequester != null && lv.getGameTime() >= this.refreshReportAtTick) {
            this.reportRefreshResult();
        }
    }

    /**
     * ★ 2026-10-28【独立跨配方线程 · 补齐「机器侧每 tick 同步」】<b>包内转发入口</b>。
     *
     * <p>旧派活实现（已删除的内嵌类）的 {@code serverTick} 第一句就是 {@code nac.tickSync()}；
     * 后来替换它的派活类<b>整体覆写</b>了 {@code serverTick}、不经过那一句
     * ⇒ 那条路上的「每 tick 同步」会整体失效
     * （受影响：F 位连接刷新每 20 tick、界面同步文案 {@link #updateSyncedTexts}、
     * 未成形缺件审计的 15 秒限流那一路）。
     *
     * <p>{@link #tickSync()} 本体是 {@code private}（它只被派活类调），而派活类是<b>另一个类</b>
     * ⇒ 这里给一个包内转发方法（方法体只有一句），让派活类能<b>同频</b>地每 tick 调一次。
     * 语义与调用频次<b>逐字相同</b>：没有新增逻辑、没有任何行为改动、不新增字段、不进 NBT。
     * <p>注：当前唯一调用方是仍未删除的旧派活实现（全树无实例化）；保留本转发方法，是为了
     * 让后续删除那个文件时不必改动 {@code tickSync()} 的可见性。
     */
    void nanochipTickSync() {
        this.tickSync();
    }

    private void updateSyncedTexts() {
        this.syncedMode = this.fullMode() ? "\u5168\u6d41\u7a0b\u6863" : "\u76f4\u901a\u6863";
        this.syncedCalibrationText = this.calibration.summary();
        this.syncedCalibrationTier = this.calibration.activeTier();
        NanochipCalibration.Family activeFamily = this.calibration.activeFamily();
        this.syncedActiveFamilyOrdinal = activeFamily == null ? -1 : activeFamily.ordinal();
        this.syncedFullWindowDenominator = NanochipCalibration.useFullWindowDenominator();
        this.syncedBreakdownText = this.calibration.breakdown();
        this.syncedFamilyCounts = NanochipStateSync.familyCountsCsv(this);
        this.syncedFamilyTiers = NanochipStateSync.familyTiersCsv(this);
        this.syncedHistoryTotal = this.calibration.total();
        long vacuumTotal = this.vacuumNetwork.total();
        if (this.vacuumDirty || vacuumTotal != this.lastVacuumTotal) {
            this.vacuumDirty = false;
            this.lastVacuumTotal = vacuumTotal;
            this.syncedVacuumText = this.vacuumNetwork.summaryAll();
        }
        this.syncedParallelShown = this.getMaxParallel();
        this.syncedModuleText = NanochipStateSync.moduleText(this);
    }

    public NanochipCalibration getCalibration() {
        return this.calibration;
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
        return this.isFormed() ? this.calibration.euMultiplier() : 1.0;
    }

    /** ★ 0.3.132【Jade 校准折减读数】本机校准当前的时长折减倍率（按"当前生效族"取；无生效族 ⇒ 1.0）。 */
    public double calibrationDurationMultiplierForDisplay() {
        if (!this.isFormed()) {
            return 1.0;
        }
        NanochipCalibration.Family active = this.calibration.activeFamily();
        return active == null ? 1.0 : this.calibration.durationMultiplier(active);
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
        if (this.getLevel() != null && this.getLevel().isClientSide) {
            return 0;
        }
        int before = this.calibration.clear();
        this.updateSyncedTexts();
        GTLModular.LOGGER.info("[gtlmodular] NAC @{} \u7eb3\u7c73\u6821\u51c6\u5df2\u91cd\u7f6e\uff1a\u5386\u53f2 {} \u2192 0 \u4e2a\u7535\u8def\uff08\u53ea\u6e05\u672c\u673a\uff0c\u672a\u52a8\u5b58\u6863\u952e\uff09", (Object)this.getPos(), (Object)before);
        return before;
    }

    /** 虚拟真空传送带网络（16 色通道）。 */
    public NanochipVacuumNetwork vacuumNetwork() {
        return this.vacuumNetwork;
    }

    /** ★【2026-09-16 双模式路由】同色管网连通分量图（管道定向模式用；简单模式下不被查询）。 */
    public NanochipPipeGraph pipeGraph() {
        return this.pipeGraph;
    }

    /**
     * 真空路由模式的一行文案（界面 + {@code /gtlmodular_nac} 自检用）。
     *
     * <p>为什么要有：管道定向模式下"不铺同色管道就不传输"是<b>正确行为</b>，
     * 但玩家很容易把它当成 bug（「明明是同一颜色怎么不传」）⇒ 必须让当前模式<b>可见</b>。
     */
    public static String routingModeText() {
        return GTLModularConfig.isVacuumRequiresPipes() ? "\u00a7e\u7ba1\u9053\u5b9a\u5411\u00a77\uff1a\u5fc5\u987b\u6709\u540c\u8272\u7ba1\u9053\u901a\u8def\u624d\u4f20\u8f93\uff0c\u7167\u539f\u7248" : "\u00a7a\u7b80\u5355\u00a77\uff1a\u989c\u8272\u5373\u7ebf\uff0c\u4e0d\u7528\u94fa\u7ba1\u9053";
    }

    /**
     * ★ 切回简单模式时把"分量池"并回 0 池（见 {@link NanochipVacuumNetwork#flattenAll()}）。
     *
     * <p>为什么需要：管道模式下虚拟件可能攒在 {@code <色>@<n>} 池里；配置改回 {@code false} 之后
     * 所有端口都只读 0 池 ⇒ 那些件会"看不见"。这里在结构成形/机器加载时并一次
     * （只搬数量、不动原型与 CC/PC 标记 ⇒ 不丢件），并打一条日志告知。
     */
    private void reconcileRoutingMode() {
        if (GTLModularConfig.isVacuumRequiresPipes()) {
            return;
        }
        int moved = this.vacuumNetwork.flattenAll();
        if (moved > 0) {
            GTLModular.LOGGER.info("[gtlmodular] \u771f\u7a7a\u8def\u7531=\u7b80\u5355\u6a21\u5f0f\uff1a\u5df2\u628a {} \u6761\u5206\u91cf\u6c60\u5e76\u56de\u4e3b\u901a\u9053\uff08\u4e0d\u4e22\u4ef6\uff09@{}", (Object)moved, (Object)this.getPos());
            this.markVacuumDirty();
        }
    }

    /** 搬运仓写通道后打脏标记，下一次 tickSync 会把摘要推给客户端。 */
    public void markVacuumDirty() {
        this.vacuumDirty = true;
    }

    public long getModuleDrawPerTick() {
        return this.syncedModuleDrawPerTick;
    }

    /** 跨秒时把上一秒累计的模块扣电换算成 EU/t 平均值。 */
    private void tickDrawWindow() {
        Level lv = this.getLevel();
        if (lv == null || lv.isClientSide) {
            return;
        }
        long second = lv.getGameTime() / 20L;
        if (second != this.drawWindowSecond) {
            if (this.drawWindowSecond != Long.MIN_VALUE) {
                this.syncedModuleDrawPerTick = this.drawAccumulator / 20L;
            }
            this.drawAccumulator = 0L;
            this.drawWindowSecond = second;
        }
    }

    /** 复合体自身蓄电（EU；实现在 {@link NanochipEnergy}）。 */
    public long getTotalStoredEU() {
        return NanochipEnergy.getTotalStoredEU(this);
    }

    /**
     * ★ 2026-11-08【参考伪神之锻炉（GTLAdditions {@code ForgeOfTheAntichrist#refreshTier}）】：
     * <b>无线电网机器的「档位」由账户余额折算出「假电压档」</b>，而不是恒 0（ULV）或写死 MAX。
     *
     * <p>参考实现口径（{@code GTLAddWirelessWorkableElectricMultipleRecipesMachine.kt:100-108}）：
     * <pre>
     *   val totalEu = WirelessEnergyManager.getUserEU(uuid)
     *   val longEu = NumberUtils.getLongValue(totalEu)
     *   tier = if (longEu == Long.MAX_VALUE) GTValues.MAX_TRUE else NumberUtils.getFakeVoltageTier(longEu)
     * </pre>
     * 本方法照同一口径取数；免费电模式直接给 {@code MAX_TRUE}。
     * <p>只读、无副作用（真正写 {@code tier} 的是 {@link #refreshWirelessTier()}）。
     */
    public int wirelessTier() {
        if (!GTLModularConfig.isNanochipGridPowerEnabled() && !GTLModularConfig.isNanochipFreeEnergyEnabled()) {
            return this.tier;
        }
        if (GTLModularConfig.isNanochipFreeEnergyEnabled()) {
            return com.gregtechceu.gtceu.api.GTValues.MAX_TRUE;
        }
        UUID u = this.resolveGridUser();
        if (u == null) {
            return this.tier;
        }
        BigInteger bal;
        try {
            bal = WirelessEnergyManager.getUserEU(u);
        } catch (Throwable t) {
            return this.tier;
        }
        long longEu = bal == null || bal.signum() <= 0
                ? 0L
                : (bal.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0 ? Long.MAX_VALUE : bal.longValue());
        return longEu == Long.MAX_VALUE
                ? com.gregtechceu.gtceu.api.GTValues.MAX_TRUE
                : org.gtlcore.gtlcore.utils.NumberUtils.getFakeVoltageTier(longEu);
    }

    /** 把 {@link #wirelessTier()} 的结果写进基类的 {@code tier}（界面/并行/模块档位都读它）。 */
    public void refreshWirelessTier() {
        if (!GTLModularConfig.isNanochipGridPowerEnabled() && !GTLModularConfig.isNanochipFreeEnergyEnabled()) {
            return;
        }
        this.tier = this.wirelessTier();
    }

    /** 复合体自身蓄电上限（EU；实现在 {@link NanochipEnergy}）。 */
    public long getTotalCapacityEU() {
        return NanochipEnergy.getTotalCapacityEU(this);
    }

    /** 复合体能源仓输入电压（实现在 {@link NanochipEnergy}）。 */
    public long getMaxInputVoltage() {
        return NanochipEnergy.getMaxInputVoltage(this);
    }

    /** NAC 最大输入功率 = 输入电压 × 安培（EU/t；实现在 {@link NanochipEnergy}）。 */
    public long getMaxInputPower() {
        return NanochipEnergy.getMaxInputPower(this);
    }

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
    static long saturatingMul(long a, long b) {
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
        return v >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int)Math.max(0L, v);
    }

    /** 供电是不是「无限 / 按需现取」型（实现在 {@link NanochipEnergy}）。 */
    public boolean isEnergySourceEffectivelyUnlimited() {
        return NanochipEnergy.isEnergySourceEffectivelyUnlimited(this);
    }

    /** 本复合体这一刻「可获取」的 EU（实现在 {@link NanochipEnergy}）。 */
    public long getObtainableEnergyEU() {
        return NanochipEnergy.getObtainableEnergyEU(this);
    }

    /**
     * ★ 2026-10-08【无线电网供电兼容】<b>限流诊断日志：一行说明「按可获取能力放行」</b>。
     *
     * <p>触发时机＝「本条需要量 &gt; 已存」但源头是无限/按需现取型（容量 Long.MAX 级）时，
     * 由模块起跑判定、模块逐 tick 扣电、主机灌缓存三处调用。
     *
     * <p><b>限流</b>：{@value #UNLIMITED_ASSIST_LOG_INTERVAL} tick（10 秒）最多一条 ——
     * 判定每 tick 都会被问，不限流会刷屏。计数器 {@link #lastUnlimitedAssistLogTick} 是普通字段，
     * 不进存档。
     *
     * @param site   触发点（写清是哪一处判定放的行，便于下次照日志定位）
     * @param need   本条判定需要的 EU
     * @param stored 该处读到的「已存」（模块侧是模块自己的缓存，主机侧是复合体蓄电）
     */
    public void logUnlimitedEnergyAssist(String site, long need, long stored) {
        Level lv = this.getLevel();
        if (lv == null || lv.isClientSide) {
            return;
        }
        long now = lv.getGameTime();
        if (this.lastUnlimitedAssistLogTick != Long.MIN_VALUE && now - this.lastUnlimitedAssistLogTick < 200L) {
            return;
        }
        this.lastUnlimitedAssistLogTick = now;
        EnergyContainerList e = this.getEnergyContainer();
        GTLModular.LOGGER.info("[gtlmodular] NAC @{} \u3010\u6309\u53ef\u83b7\u53d6\u80fd\u529b\u653e\u884c\u3011\uff08{}\uff09\uff1a\u9700\u8981 {} EU > \u5df2\u5b58 {} EU\uff0c\u4f46\u590d\u5408\u4f53\u5bb9\u91cf {} EU \u2265 \u54e8\u5175 {} EU\uff08\u4e0d\u50a8\u80fd\u3001\u6309\u9700\u73b0\u53d6\u578b\u4f9b\u7535\uff1aGTL Additions\u300c\u65e0\u7ebf\u7535\u7f51\u8f93\u5165\u7ec8\u7aef\u300d/ \u521b\u9020\u80fd\u6e90\u7b49\uff09\u21d2 \u89c6\u4e3a\u5df2\u5145\u8db3\uff0c\u653e\u884c\u4e0d\u62e6", new Object[]{this.getPos(), site, need, stored, e == null ? 0L : e.getEnergyCapacity(), 0x3FFFFFFFFFFFFFFFL});
    }

    /**
     * 模块取电入口（模块自己没装能源仓时走这里）。
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
        Level lv = this.getLevel();
        if (lv == null || lv.isClientSide || !this.isFormed() || !this.isWorkingEnabled()) {
            return false;
        }
        if (lowPriority && this.highPriorityStarved()) {
            return false;
        }
        EnergyContainerList e = this.getEnergyContainer();
        if (e == null || e.getEnergyStored() < amount) {
            return false;
        }
        e.changeEnergy(-amount);
        this.drawAccumulator = NanochipAssemblyComplexMachine.saturatingAdd(this.drawAccumulator, amount);
        return true;
    }

    /** 是否有高优先级模块正因缺电等待（组装矩阵据此让电）。 */
    private boolean highPriorityStarved() {
        for (IModularMachineModule m : this.getModules()) {
            RecipeLogic logic;
            NanochipModuleMachine module;
            if (!(m instanceof NanochipModuleMachine) || !(module = (NanochipModuleMachine)m).isFormed() || module.getModuleKind() == NanochipModuleKind.ASSEMBLY_MATRIX || (logic = module.getRecipeLogic()) == null || !logic.isWaiting()) continue;
            return true;
        }
        return false;
    }

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
        Level level = this.getLevel();
        if (!(level instanceof ServerLevel) || !this.isFormed()) {
            return;
        }
        List<BlockPos> slots = this.cachedModuleSlots;
        if (slots.isEmpty()) {
            slots = this.adoptRestoredModuleSlots(level);
        }
        if (slots.isEmpty()) {
            if (!this.slotsUnavailableLogged) {
                this.slotsUnavailableLogged = true;
                GTLModular.LOGGER.info("[gtlmodular] NAC @{} \u5c1a\u65e0\u300c\u7ed3\u6784 F \u4f4d\u300d\u6210\u529f\u5339\u914d\u5feb\u7167\uff08\u6b63\u5728\u7b49 GTCEu \u7ed9\u51fa\u7b2c\u4e00\u6b21\u6210\u529f\u5339\u914d\uff1b\u69fd\u540d \"{}\"\uff09\u21d2 \u672c\u6b21\u4e0d\u6539\u52a8\u5df2\u8fde\u63a5\u6a21\u5757\uff08\u5f53\u524d {} \u4e2a\uff09", new Object[]{this.getPos(), "gtlmodular_nanochip_module_slot", this.modules.size()});
            }
            return;
        }
        this.slotsUnavailableLogged = false;
        LinkedHashSet<IModularMachineModule> recomputed = new LinkedHashSet<IModularMachineModule>();
        for (BlockPos pos : slots) {
            MetaMachine metaMachine = MetaMachine.getMachine((BlockGetter)level, (BlockPos)pos);
            if (metaMachine == null || metaMachine == this || !this.isValidModule(metaMachine)) continue;
            IModularMachineModule module = (IModularMachineModule)metaMachine;
            recomputed.add(module);
            if (recomputed.size() < this.getMaxModuleCount()) continue;
            break;
        }
        int before = this.modules.size();
        for (IModularMachineModule iModularMachineModule : recomputed) {
            if (this.modules.contains(iModularMachineModule) && iModularMachineModule.getHost() == this) continue;
            iModularMachineModule.connectToHost(this);
            if (iModularMachineModule instanceof NanochipModuleMachine) {
                NanochipModuleMachine nanochipModule = (NanochipModuleMachine)iModularMachineModule;
                nanochipModule.onHostLinked();
            }
            NanochipDiagnostics.logModuleLinkChange(this, "+", iModularMachineModule, recomputed.size(), slots.size());
        }
        for (IModularMachineModule iModularMachineModule : new ArrayList<IModularMachineModule>(this.modules)) {
            if (recomputed.contains(iModularMachineModule)) continue;
            iModularMachineModule.removeFromHost(this);
            NanochipDiagnostics.logModuleLinkChange(this, "-", iModularMachineModule, recomputed.size(), slots.size());
        }
        if (this.modules.size() != recomputed.size() || !this.modules.containsAll(recomputed)) {
            GTLModular.LOGGER.warn("[gtlmodular] NAC @{} \u6a21\u5757\u96c6\u5408\u6536\u655b\u5f02\u5e38\uff1a\u96c6\u5408 {} \u4e2a / \u672c\u6b21\u91cd\u7b97 {} \u4e2a\uff08\u9884\u671f\u76f8\u7b49\uff09", new Object[]{this.getPos(), this.modules.size(), recomputed.size()});
        }
        if (this.modules.size() != before) {
            GTLModular.LOGGER.info("[gtlmodular] NAC @{} \u5df2\u8fde\u63a5\u6a21\u5757\u6570\u53d8\u5316\uff1a{} \u2192 {}\uff08\u7ed3\u6784 F \u4f4d {} \u4e2a\uff0c\u5176\u4e2d\u5df2\u6210\u5f62\u6a21\u5757 {} \u4e2a\uff09", new Object[]{this.getPos(), before, this.modules.size(), slots.size(), recomputed.size()});
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
            this.restoredSlotsSettled = true;
            return List.of();
        }
        for (BlockPos pos : restored) {
            if (level.isLoaded(pos)) continue;
            return List.of();
        }
        for (int i = 0; i < restored.size(); ++i) {
            BlockPos pos;
            pos = restored.get(i);
            Block block = level.getBlockState(pos).getBlock();
            if (NanochipDiagnostics.isModuleControllerBlock(block) || block == NanochipBlocks.MESH_INTERFACE_CASING.get()) continue;
            this.restoredSlotsSettled = true;
            this.restoredModuleSlots = List.of();
            GTLModular.LOGGER.info("[gtlmodular] NAC @{} \u5b58\u6863 F \u4f4d\u5feb\u7167**\u672a\u91c7\u7528**\uff1a\u7b2c {} / {} \u683c @{} \u7ad9\u7684\u662f {}\uff08\u65e2\u4e0d\u662f\u7f51\u683c\u63a5\u53e3\u673a\u58f3\u3001\u4e5f\u4e0d\u662f\u6a21\u5757\u63a7\u5236\u5668\uff09\u21d2 \u7ed3\u6784\u5df2\u4e0d\u662f\u5b58\u6863\u65f6\u7684\u6837\u5b50\uff0c\u9000\u56de\u7b49 GTCEu \u7b2c\u4e00\u6b21\u6210\u529f\u5339\u914d", new Object[]{this.getPos(), i + 1, restored.size(), pos.toShortString(), BuiltInRegistries.BLOCK.getKey(block)});
            return List.of();
        }
        this.restoredSlotsSettled = true;
        this.restoredModuleSlots = List.of();
        this.cachedModuleSlots = List.copyOf(restored);
        this.lastMatchedModuleSlots.clear();
        this.lastMatchedModuleSlots.addAll(restored);
        this.slotsUnavailableLogged = false;
        GTLModular.LOGGER.info("[gtlmodular] NAC @{} \u91c7\u7528**\u5b58\u6863 F \u4f4d\u5feb\u7167** {} \u683c\uff08\u9010\u683c\u9a8c\u8bc1\u901a\u8fc7\uff1a\u5168\u90e8\u5df2\u52a0\u8f7d\u3001\u4e14\u90fd\u662f\u7f51\u683c\u63a5\u53e3\u673a\u58f3\u6216\u6a21\u5757\u63a7\u5236\u5668\uff09\u21d2 \u6a21\u5757\u8fde\u63a5\u4e0d\u5fc5\u7b49 GTCEu \u91cd\u8dd1\u6574\u56fe\u6848\u5339\u914d", (Object)this.getPos(), (Object)restored.size());
        return this.cachedModuleSlots;
    }

    /** 只读：{@code module} 是否在主机权威的「已连接模块集合」里（实现在 {@link NanochipModuleLinkHost}）。 */
    public boolean isModuleConnected(IModularMachineModule<NanochipAssemblyComplexMachine, ?> module) {
        return NanochipModuleLinkHost.isModuleConnected(this, module);
    }

    @Override
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
    public void scanAndConnectModules() {
        IModularMachineHost.super.scanAndConnectModules();
        this.refreshModulesFromStructure();
    }

    public void onLoad() {
        Level loadLevel;
        super.onLoad();
        if (!this.isRemote() && this.isFormed() && this.getParts().isEmpty()) {
            this.getMultiblockState().setError(MultiblockState.UNLOAD_ERROR);
        }
        this.joinQuietUntilTick = (loadLevel = this.getLevel()) == null ? -10000L : loadLevel.getGameTime() + 200L;
        this.resyncRequested.set(false);
        this.lastForcedResyncTick = -10000L;
        this.structureLogGate.reset();
        this.lastNacSlotPartCount = -1;
        this.nacSlotHeartbeat = -1;
        if (this.getLevel() != null && !this.getLevel().isClientSide) {
            this.unsubscribeModuleRefresh();
            this.moduleRefreshSub = this.subscribeServerTick(this::tickModuleRefresh);
            this.pipeGraph.markDirty();
            this.reconcileRoutingMode();
            if (GTLModularConfig.isNanochipGridPowerEnabled()) {
                UUID gridUser = this.resolveGridUser();
                GTLModular.LOGGER.info("[gtlmodular] NAC @{} \u7535\u7f51\u53d6\u7535\u5df2\u5c31\u7eea\uff1a\u53d6\u7535\u8eab\u4efd {}\uff08\u8bfb\u6863\u5f80\u8fd4\u6b63\u5e38\uff1b\u7535\u7f51\u4f59\u989d\u4e3a 0 \u65f6\u672c\u673a\u539f\u5730\u7b49\u7535\uff0c\u4e0d\u518d\u9000\u5316\u6210\u300c\u5fc5\u987b\u6709\u80fd\u6e90\u4ed3\u300d\uff09", (Object)this.getPos(), (Object)gridUser);
            }
        }
    }

    public void onUnload() {
        this.unsubscribeModuleRefresh();
        super.onUnload();
    }

    private void unsubscribeModuleRefresh() {
        if (this.moduleRefreshSub != null) {
            this.moduleRefreshSub.unsubscribe();
            this.moduleRefreshSub = null;
        }
    }

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
        if (level instanceof ServerLevel) {
            ServerLevel serverLevel = (ServerLevel)level;
            ++this.loadPartsInvalidateCount;
            this.loadPartsInvalidateLastTick = now;
            this.onStructureInvalid();
            MultiblockWorldSavedData worldData = MultiblockWorldSavedData.getOrCreate((ServerLevel)serverLevel);
            worldData.removeMapping(state);
            worldData.addAsyncLogic((IMultiController)this);
            this.invalidateStructurePending = true;
            GTLModular.LOGGER.info("[gtlmodular] NAC \u8bfb\u6863\u90e8\u4ef6\u6062\u590d\uff1a\u5df2\u8ba9\u7ed3\u6784\u5931\u6548\u5e76\u4ea4\u56de\u4e0a\u6e38\u91cd\u5224 @{} \uff08\u5df2\u6210\u5f62\u4f46\u96f6\u4ef6\u8868\u4e3a\u7a7a\uff1b\u7d2f\u8ba1\u5931\u6548={}\uff0cgameTime={}\uff1b\u4f9d\u636e _census/_jtmp/gtl_MBStateMixin_c.txt:202-237 \u4e0e WeatherAnchorMachine.java:63-76\uff09", new Object[]{this.getPos(), this.loadPartsInvalidateCount, now});
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
        if (state.cache == null || state.cache.isEmpty()) {
            return false;
        }
        LongIterator cacheIterator = state.cache.longIterator();
        while (cacheIterator.hasNext()) {
            BlockState bs;
            BlockPos pos = BlockPos.of((long)cacheIterator.nextLong());
            if (pos.equals((Object)this.getPos()) || (bs = level.getBlockState(pos)).getBlock() instanceof ActiveBlock || level.getBlockEntity(pos) instanceof IMachineBlockEntity) continue;
            GTLModular.LOGGER.info("[gtlmodular] NAC \u8bfb\u6863\u90e8\u4ef6\u6062\u590d\uff1a\u5df2\u8865\u4e00\u6b21\u7ed3\u6784\u91cd\u5224\u5b9a @{} \uff08\u5df2\u6210\u5f62\u4f46\u96f6\u4ef6\u8868\u4e3a\u7a7a\uff1b\u89e6\u53d1\u683c={}\uff0cgameTime={}\uff0c\u7d2f\u8ba1\u5c1d\u8bd5={}\uff1b\u4f9d\u636e _census/NAC\u8bfb\u6863\u90e8\u4ef6\u672a\u8fde\u63a5_\u5b9a\u4f4d.md \u00a7\u2465 A\uff09", new Object[]{this.getPos(), pos, now, this.loadPartsRecoveryAttempts});
            state.onBlockStateChanged(pos, bs);
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
        Object errorName;
        boolean hasError;
        int cache;
        if (!GTLModular.LOGGER.isInfoEnabled()) {
            return;
        }
        if (this.loadPartsRecoveryDiagCount >= 6) {
            return;
        }
        if (this.loadPartsRecoveryDiagTick >= 0L && now - this.loadPartsRecoveryDiagTick < 200L) {
            return;
        }
        this.loadPartsRecoveryDiagTick = now;
        ++this.loadPartsRecoveryDiagCount;
        List parts = this.getParts();
        try {
            cache = state.cache == null ? -1 : state.cache.size();
        }
        catch (RuntimeException e) {
            cache = -2;
        }
        try {
            PatternError error = state.error;
            hasError = error != null;
            errorName = hasError ? String.valueOf(error) : "\u65e0";
        }
        catch (RuntimeException e) {
            hasError = true;
            errorName = "\u8bfb\u72b6\u6001\u65f6\u629b " + NanochipDiagnostics.describeThrowable(e);
        }
        Level world = this.getLevel();
        boolean chunksLoaded = NanochipModuleLinkHost.structureChunksLoaded(world, this.getPos());
        GTLModular.LOGGER.info("[gtlmodular] NAC \u90e8\u4ef6\u672a\u5c31\u7eea\u8bca\u65ad @{}\uff1a\u96f6\u4ef6\u6570={}\uff0cisFormed={}\uff0c\u663e\u793a\u7b49\u7ea7\u5b57\u6bb5 tier={}\uff0chasError={}\uff0cerror={}\uff0ccache \u683c\u6570={}\uff0c\u7ed3\u6784\u533a\u5757\u5168\u52a0\u8f7d={}\uff0c\u7a97\u53e3\u622a\u6b62={}\uff0c\u4e0a\u6b21\u5c1d\u8bd5={}\uff0c\u5df2\u5c1d\u8bd5={}\uff0c\u5df2\u5931\u6548={}\uff0c\u4e0a\u6b21\u5931\u6548={}\uff0c\u8fde\u7eed\u65e0\u6548={}\uff0c\u8fde\u7eed\u629b\u5f02\u5e38={}\uff0c\u672c tick \u672a\u80fd\u4ea4\u51fa\u91cd\u5224={}\uff0cgameTime={} \u2014\u2014 \u9762\u677f\u300c\u7ed3\u6784\u90e8\u4ef6\u5c1a\u672a\u5c31\u7eea\u300d\u7684\u5224\u636e\u662f\u4e0a\u6e38 isRecipeLogicAvailable()\uff08= isFormed && !hasError\uff0c\u4e0d\u770b\u96f6\u4ef6\u8868\uff1b\u5b57\u8282\u7801\u89c1 _census/_javap_simple_workablemb.txt:475-487\uff09\uff1b\u663e\u793a\u7b49\u7ea7\u53ea\u7531 onStructureFormed \u65f6\u6309\u80fd\u6e90\u4ed3\u5199\u4e0b\u4e00\u6b21 \u21d2 \u90e8\u4ef6\u6ca1\u56de\u6765\u65f6\u90a3\u4e00\u884c\u4e0d\u53ef\u4fe1", new Object[]{this.getPos(), parts == null ? -1 : parts.size(), this.isFormed(), this.tier, hasError, errorName, cache, chunksLoaded, this.loadPartsRecoveryDeadline, this.loadPartsRecoveryLastAttempt, this.loadPartsRecoveryAttempts, this.loadPartsInvalidateCount, this.loadPartsInvalidateLastTick, this.loadPartsInvalidateIneffective, this.loadPartsInvalidateThrown, actionRefusedNoLever, now});
    }

    /**
     * 结构零件表是否已登记（{@code getParts()} 在未成形时可能为 {@code null} ⇒ 一律按"空"处理）。
     *
     * <p>★ 2026-11-10：这个判据现在同时是①恢复动作的<b>触发条件</b>、②恢复动作的<b>唯一收工条件</b>
     * （{@link #recoverLoadPartsIfNeeded()}）、③{@link #requestStructureRefresh} 早退判据的一部分、
     * ④界面「最大配方等级」的处理判据（{@link #addDisplayText}）与⑤"失效 ⇒ 重判"防震荡闸的
     * 无效计数判据 —— 一处判据、多处同口径。
     */
    private boolean hasRegisteredParts() {
        List parts = this.getParts();
        return parts != null && !parts.isEmpty();
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
        int ours;
        MultiblockState state = this.getMultiblockState();
        if (state == null) {
            return;
        }
        int upstream = state.cache == null ? -1 : state.cache.size();
        if (upstream != (ours = NanochipComplexPattern.hostCheckCells().length / 3)) {
            GTLModular.LOGGER.warn("[gtlmodular] NAC \u7ed3\u6784\u4f4d\u7f6e\u7f13\u5b58\u81ea\u8bc1\u4e0d\u4e00\u81f4 @{}\uff1a\u672c\u673a\u56fe\u6848\u63a8\u51fa\u6765\u7684\u683c\u6570={}\uff0c\u4e0a\u6e38 MultiblockState.cache \u5b9e\u6d4b={} \u2014\u2014 \u4e24\u8005\u672c\u5e94\u76f8\u7b49\uff08\u4f9d\u636e\u89c1 NanochipComplexPattern.hostCheckCells() \u7684 javadoc\uff09\u3002\u672c\u529f\u80fd\u6309**\u4e0a\u6e38 cache \u7684\u8bed\u4e49**\u5de5\u4f5c\uff0c\u4e0d\u7b49\u65f6\u53ea\u4f1a\u5c11\u8986\u76d6/\u591a\u8986\u76d6\u51e0\u683c\uff1a\u591a\u8986\u76d6 \u21d2 \u591a\u8dd1\u4e00\u6b21\u771f\u5339\u914d\uff1b\u5c11\u8986\u76d6 \u21d2 \u90a3\u51e0\u683c\u7684\u79fb\u9664\u8981\u7b49\u4e0a\u6e38\u81ea\u5df1\u7684\u8def\u5f84\u3002\u4e0d\u5f71\u54cd\u4efb\u4f55\u6210\u5f62/\u5931\u6548\u5224\u5b9a\u3002", new Object[]{this.getPos(), ours, upstream});
        }
    }

    /**
     * 统一扣电（净化水厂模式）：模块只暴露 {@code getExposedEuPerTick()}，
     * 主机把所有正在工作的模块耗电加总后，从自己的能源容器/无线电网一次性扣。
     */
    private void payModuleEnergy() {
        Level level = this.getLevel();
        if (level == null || level.isClientSide
                || GTLModularConfig.isNanochipFreeEnergyEnabled()) {
            return;
        }
        long demand = 0L;
        for (IModularMachineModule m : this.getModules()) {
            if (!(m instanceof NanochipModuleMachine module)
                    || !module.isFormed() || !module.drawingFromHost()) {
                continue;
            }
            demand = saturatingAdd(demand, module.getExposedEuPerTick());
        }
        if (demand <= 0L) {
            return;
        }
        EnergyContainerList e = this.getEnergyContainer();
        if (e != null) {
            // tickModuleRefresh 每 tick 调一次 ⇒ 按“EU/t”扣一次，不乘 20。
            e.changeEnergy(-demand);
        }
    }

    /** 每 {@value #MODULE_REFRESH_INTERVAL} tick 按结构 {@code F} 位刷新「已连接」。 */
    private void tickModuleRefresh() {
        Level level = this.getLevel();
        if (level == null) {
            return;
        }
        long now = level.getGameTime();
        if (!level.isClientSide && now % 20L == 0L) {
            int hatch = Math.max(1, this.countThreadHatchThreads());
            if (hatch != this.syncedThreadHatchThreads) {
                this.syncedThreadHatchThreads = hatch;
            }
            // ★ 2026-11-08：参考伪神之锻炉 —— 每 20 tick 按无线电网余额刷新档位（余额变动后档位跟着变）。
            this.refreshWirelessTier();
        }
        this.recoverLoadPartsIfNeeded();
        this.mirrorBindGuard.tick(this);
        this.confirmFoldedPattern();
        this.tickNacSlotHeartbeat();
        this.payModuleEnergy();
        this.distributeModuleEnergy(now);
        if (GTLModularConfig.isNanochipOriginalMode()) {
            return;
        }
        if (this.consumePendingResync(now)) {
            return;
        }
        if (this.lastRescanTick >= 0L && now - this.lastRescanTick < 20L) {
            return;
        }
        this.lastRescanTick = now;
        this.scanAndConnectModules();
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
            return false;
        }
        if (now - this.lastForcedResyncTick < 20L) {
            this.resyncRequested.set(true);
            return false;
        }
        this.lastForcedResyncTick = now;
        this.lastRescanTick = now;
        this.scanAndConnectModules();
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
        if (this.lastEnergyDistributeTick >= 0L && now - this.lastEnergyDistributeTick < 20L) {
            return;
        }
        this.lastEnergyDistributeTick = now;
        if (!this.isFormed() || !this.isWorkingEnabled()) {
            return;
        }
        // 电网/免费电由 payModuleEnergy() 统一按暴露耗电扣，不再往模块缓存灌电，
        // 否则同一份电会扣两次。
        if (GTLModularConfig.isNanochipFreeEnergyEnabled()
                || GTLModularConfig.isNanochipGridPowerEnabled()) {
            return;
        }
        EnergyContainerList e = this.getEnergyContainer();
        if (e == null) {
            return;
        }
        long unitCapacity = this.getMaxInputPower();
        boolean onDemand = this.isEnergySourceEffectivelyUnlimited();
        long obtainable = this.getObtainableEnergyEU();
        long storedForLog = e.getEnergyStored();
        ArrayList<NanochipModuleMachine> mods = new ArrayList<NanochipModuleMachine>();
        for (IModularMachineModule m : this.getModules()) {
            NanochipModuleMachine mm;
            if (!(m instanceof NanochipModuleMachine) || !(mm = (NanochipModuleMachine)m).isFormed() || !mm.drawingFromHost()) continue;
            mods.add(mm);
        }
        mods.sort(Comparator.comparingInt(NanochipModuleMachine::energyPriority).reversed());
        for (NanochipModuleMachine mm : mods) {
            NanochipModuleKind kind = mm.getModuleKind();
            int maxTicks = kind == null ? 100 : kind.maxRecipeTicks();
            mm.setBufferCapacity(NanochipAssemblyComplexMachine.saturatingMul(NanochipAssemblyComplexMachine.saturatingMul(unitCapacity, 2L), maxTicks));
            long room = mm.getBufferCapacityEU() - mm.getBufferEU();
            if (room <= 0L) continue;
            long avail = obtainable;
            if (avail <= 0L) break;
            long want = Math.min(room, avail);
            long taken = -e.changeEnergy(-want);
            boolean synthesized = false;
            if (taken <= 0L) {
                if (!onDemand) continue;
                taken = want;
                synthesized = true;
                this.logUnlimitedEnergyAssist("\u4e3b\u673a\u704c\u6a21\u5757\u7f13\u5b58", want, storedForLog);
            }
            long got = mm.fillBufferEU(taken);
            if (!synthesized && got < taken) {
                e.changeEnergy(taken - got);
            }
            this.drawAccumulator = NanochipAssemblyComplexMachine.saturatingAdd(this.drawAccumulator, got);
        }
    }

    @Override
    /** 模块上限（实现在 {@link NanochipModuleLinkHost}）。 */
    public int getMaxModuleCount() {
        return NanochipModuleLinkHost.getMaxModuleCount();
    }

    @Override
    public Set<IModularMachineModule<NanochipAssemblyComplexMachine, ?>> getModuleSet() {
        return this.modules;
    }

    @Override
    public BlockPos[] getModuleScanPositions() {
        return NanochipModuleLinkHost.getModuleScanPositions(this);
    }

    @Override
    public List<ModuleRenderInfo> getModulesForRendering() {
        return NanochipModuleLinkHost.getModulesForRendering(this);
    }

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
            matched = NanochipComplexPattern.moduleSlotsOf(this.getMultiblockState());
        }
        catch (RuntimeException e) {
            GTLModular.LOGGER.debug("[gtlmodular] NAC @{} \u91c7\u96c6 F \u4f4d\u5feb\u7167\u5931\u8d25\uff0c\u4fdd\u7559\u4e0a\u4e00\u4efd\uff1a{}", (Object)this.getPos(), (Object)e.toString());
            return;
        }
        ArrayList<BlockPos> sorted = new ArrayList<BlockPos>(matched);
        sorted.sort(Comparator.comparingInt((BlockPos p) -> p.getX()).thenComparingInt((BlockPos p) -> p.getY()).thenComparingInt((BlockPos p) -> p.getZ()));
        this.cachedModuleSlots = List.copyOf(sorted);
        this.lastMatchedModuleSlots.clear();
        this.lastMatchedModuleSlots.addAll(sorted);
    }

    /** 当前生效的 F 位快照条数（实现在 {@link NanochipModuleLinkHost}）。 */
    public int moduleSlotCount() {
        return NanochipModuleLinkHost.moduleSlotCount(this.cachedModuleSlots);
    }

    public void onStructureFormed() {
        Level level;
        super.onStructureFormed();
        this.publishNacSlots();
        this.nacSlotHeartbeat = 0;
        this.lastNacSlotPartCount = this.getParts() == null ? -1 : this.getParts().size();
        MultiblockState formedState = this.getMultiblockState();
        if (formedState != null && formedState.hasError()) {
            formedState.setError(null);
        }
        if ((level = this.getLevel()) instanceof ServerLevel) {
            ServerLevel formedServerLevel = (ServerLevel)level;
            if (formedState != null) {
                MultiblockWorldSavedData.getOrCreate((ServerLevel)formedServerLevel).addMapping(formedState);
            }
        }
        this.registerGridPowerHandler();
        // ★ 2026-11-08：参考伪神之锻炉 —— 成形时按无线电网余额刷一次「假电压档」。
        this.refreshWirelessTier();
        this.snapshotModuleSlots();
        this.pipeGraph.markDirty();
        this.reconcileRoutingMode();
        Level formedLevel = this.getLevel();
        if (formedLevel instanceof ServerLevel) {
            ServerLevel formedServer = (ServerLevel)formedLevel;
            formedServer.getServer().execute(this::scanAndConnectModules);
        } else {
            this.scanAndConnectModules();
        }
        this.updateSyncedTexts();
        Level formedLogLevel = this.getLevel();
        long formedLogNow = formedLogLevel == null ? 0L : formedLogLevel.getGameTime();
        String formedDetail = (this.fullMode() ? "\u5168\u6d41\u7a0b\u6863" : "\u76f4\u901a\u6863") + "|F \u4f4d=" + this.lastMatchedModuleSlots.size();
        boolean emitFormed = this.structureLogGate.shouldEmitInfo(FORMED_CONCLUSION, formedDetail, "\u590d\u5408\u4f53@" + this.getPos().toShortString(), formedLogNow);
        if (emitFormed || GTLModular.LOGGER.isDebugEnabled()) {
            Object[] formedArgs = new Object[]{this.getPos(), this.fullMode() ? "\u5168\u6d41\u7a0b\u6863" : "\u76f4\u901a\u6863", this.lastMatchedModuleSlots.size(), this.formedModuleCount(), this.getMaxModuleCount(), NanochipDiagnostics.summarizeFSlotContent(this, this.cachedModuleSlots)};
            if (emitFormed) {
                GTLModular.LOGGER.info(FORMED_LINE_FORMAT, formedArgs);
            } else {
                GTLModular.LOGGER.debug(FORMED_LINE_FORMAT, formedArgs);
            }
        }
        this.reportRefreshResult();
    }

    /** 把已连接模块的能量缓存清零（服务端线程上执行；结构失效时调用）。 */
    private void clearModuleBuffers() {
        for (IModularMachineModule m : this.getModules()) {
            if (!(m instanceof NanochipModuleMachine)) continue;
            NanochipModuleMachine mm = (NanochipModuleMachine)m;
            mm.clearBufferEU();
        }
    }

    public void onStructureInvalid() {
        super.onStructureInvalid();
        this.clearThreadPart();
        this.clearNacSlots();
        this.unregisterGridPowerHandler();
        this.lastNacSlotPartCount = -1;
        Level bufLevel = this.getLevel();
        if (bufLevel instanceof ServerLevel) {
            ServerLevel bufServer = (ServerLevel)bufLevel;
            bufServer.getServer().execute(this::clearModuleBuffers);
        } else {
            this.clearModuleBuffers();
        }
        int dropped = this.modules.size();
        NanochipDiagnostics.logStructureFailureDetail(this, this.lastMatchedModuleSlots);
        this.logUnformedAudit();
        Level invalidLevel = this.getLevel();
        this.moduleDisconnectDeadline = invalidLevel == null ? -1L : invalidLevel.getGameTime() + 100L;
        this.pipeGraph.clear();
        Level invalidLogLevel = this.getLevel();
        long invalidLogNow = invalidLogLevel == null ? 0L : invalidLogLevel.getGameTime();
        boolean emitInvalid = this.structureLogGate.shouldEmitInfo(INVALID_CONCLUSION, "", "\u590d\u5408\u4f53@" + this.getPos().toShortString(), invalidLogNow);
        if (emitInvalid || GTLModular.LOGGER.isDebugEnabled()) {
            if (emitInvalid) {
                GTLModular.LOGGER.info(INVALID_LINE_FORMAT, (Object)this.getPos(), (Object)dropped);
            } else {
                GTLModular.LOGGER.debug(INVALID_LINE_FORMAT, (Object)this.getPos(), (Object)dropped);
            }
        }
    }

    public void onMachineRemoved() {
        this.safeClearModules();
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
    public void onPartUnload() {
        super.onPartUnload();
        this.clearThreadPart();
    }

    /**
     * 结构失败原因的一行可读诊断（实现见 {@link NanochipDiagnostics}；保留本方法给模块侧既有调用）。
     */
    public static String describeStructureFailure(MultiblockState state) {
        return NanochipDiagnostics.describeStructureFailure(state);
    }

    public void saveCustomPersistedData(CompoundTag tag, boolean forDrop) {
        super.saveCustomPersistedData(tag, forDrop);
        if (!forDrop) {
            tag.put("GTLModularNanochipCalibration", this.calibration.save());
            tag.put("GTLModularNanochipVacuum", this.vacuumNetwork.save());
        }
        if (!forDrop && !this.cachedModuleSlots.isEmpty()) {
            long[] packed = new long[this.cachedModuleSlots.size()];
            for (int i = 0; i < packed.length; ++i) {
                packed[i] = this.cachedModuleSlots.get(i).asLong();
            }
            tag.putLongArray(NBT_MODULE_SLOTS, packed);
        }
    }

    public void loadCustomPersistedData(CompoundTag tag) {
        super.loadCustomPersistedData(tag);
        if (tag.contains("GTLModularNanochipCalibration")) {
            this.calibration.load(tag.getCompound("GTLModularNanochipCalibration"));
        }
        if (tag.contains("GTLModularNanochipVacuum")) {
            this.vacuumNetwork.load(tag.getCompound("GTLModularNanochipVacuum"));
        }
        if (tag.contains(NBT_MODULE_SLOTS)) {
            long[] packed = tag.getLongArray(NBT_MODULE_SLOTS);
            ArrayList<BlockPos> restored = new ArrayList<BlockPos>(packed.length);
            for (long v : packed) {
                restored.add(BlockPos.of((long)v));
            }
            restored.sort(Comparator.comparingInt((BlockPos p) -> p.getX()).thenComparingInt((BlockPos p) -> p.getY()).thenComparingInt((BlockPos p) -> p.getZ()));
            this.restoredModuleSlots = List.copyOf(restored);
            this.restoredSlotsSettled = false;
            GTLModular.LOGGER.info("[gtlmodular] \u8bfb\u6863\uff1a\u53d6\u5f97\u5b58\u6863 F \u4f4d\u5feb\u7167 {} \u683c\uff08\u5f85\u9010\u683c\u9a8c\u8bc1\u540e\u542f\u7528\uff1b\u9a8c\u8bc1\u5165\u53e3 adoptRestoredModuleSlots\uff09", (Object)restored.size());
        }
    }

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
     * 而本机 {@link #createRecipeLogic} 返回的是我们自己的 {@link NACThreadedRecipeLogic}
     * ⇒ <b>这个值在我们的机器上没有任何代码读它</b>。它只是一个"能点、能改、但不影响任何事"的旋钮，
     * 留着只会误导玩家（队长原话方向：「它会影响我们的机制」——实际上它不改我们的机制，但会让人以为会改）。
     *
     * <h4>回退</h4>
     * 删掉本方法 ⇒ 逐字恢复父类行为（按钮重新出现）。**不要**去动父类字段。
     */
    protected IFancyConfigurator createConfigurators() {
        return null;
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
    public void attachConfigurators(ConfiguratorPanel panel) {
        // 回归原版 UI：不挂本模组的校准页，只保留 GTCEu 基类配置。
        super.attachConfigurators(panel);
        if (panel != null) {
            return;
        }
    }

    public void addDisplayText(List<Component> textList) {
        // 回归原版 UI：只显示 GTCEu 自己的机器信息，不再追加本模组的说明文字。
        super.addDisplayText(textList);
        // 完全回归 GTCEu 基类显示：不再补自定义“最大功率=电网总电量”那一行。
        if (textList != null) {
            return;
        }
        if (this.isFormed() && !this.hasRegisteredParts()) {
            NanochipStateSync.replaceUnreadyTierDisplay(textList);
        }
        if (this.isFormed() && !this.hasRegisteredParts()) {
            NanochipStateSync.appendUnreadyLines(textList);
        }
        if (!this.isFormed()) {
            NanochipStateSync.appendNotFormedLine(textList);
            return;
        }
        NanochipStateSync.replaceMaxPowerWithGridTotal(this, textList);
        NanochipStateSync.addMirrorBindingDisplay(textList);
        if (this.getLevel() instanceof ServerLevel) {
            long t = this.getLevel().getGameTime();
            if (this.lastUiRefreshTick < 0L || t - this.lastUiRefreshTick >= 60L) {
                this.lastUiRefreshTick = t;
                this.updateSyncedTexts();
            }
        }
        boolean full = "\u5168\u6d41\u7a0b\u6863".equals(this.syncedMode);
        NanochipStateSync.appendModuleLine(this, textList);
    }

    /** 供调试/日志用的一行摘要。 */
    public String toString() {
        return String.format(Locale.ROOT, "NAC[%s, \u6a21\u5f0f=%s, \u5e76\u884c=%d, \u6821\u51c6=%s, \u6821\u51c6\u5206\u6bcd=%s]", this.getPos(), this.syncedMode, this.syncedParallelShown, this.syncedCalibrationText, this.syncedFullWindowDenominator ? "\u539f\u7248\u56fa\u5b9a 100000" : "\u5df2\u8bb0\u5f55\u6570");
    }

}

