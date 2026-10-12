package com.gtlmodular.common.machine.nanochip;

import com.gtlmodular.common.diag.SwallowedErrors;
import com.gtlmodular.common.machine.nanochip.vacuum.NanochipHostVacuum;
import com.gtlmodular.common.machine.nanochip.display.NanochipHostDisplay;
import com.gtlmodular.common.machine.nanochip.display.NanochipModuleBonusSnapshot;
import com.gtlmodular.common.data.nanochip.NanochipModContext;
import com.gtlmodular.common.machine.nanochip.structure.NanochipHostStructure;
import com.gtlmodular.common.machine.nanochip.structure.NanochipHostStructureIndex;
import com.gtlmodular.common.machine.nanochip.structure.NanochipLoadRecovery;
import com.gtlmodular.common.machine.nanochip.structure.NanochipStructureDiagnostics;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.feature.IInteractedMachine;
import com.gregtechceu.gtceu.api.machine.feature.IMachineLife;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IDisplayUIMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.misc.EnergyContainerList;
import com.gregtechceu.gtceu.api.pattern.MultiblockState;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.logic.OCParams;
import com.gregtechceu.gtceu.api.recipe.logic.OCResult;
import com.gregtechceu.gtceu.utils.FormattingUtil;
import com.gregtechceu.gtceu.utils.GTUtil;
import com.gtlmodular.common.data.nanochip.NanochipModulePreview;
import com.gtlmodular.common.machine.MultiTypeLookupHost;
import com.gregtechceu.gtceu.api.gui.fancy.ConfiguratorPanel;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyConfigurator;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;
import com.gtladd.gtladditions.common.data.ParallelData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import com.gtladd.gtladditions.api.machine.feature.IThreadModifierPart;
import com.gtladd.gtladditions.api.machine.multiblock.GTLAddWorkableElectricMultipleRecipesMachine;
import com.gtladd.gtladditions.api.machine.logic.GTLAddMultipleTypeRecipesLogic;
import org.gtlcore.gtlcore.api.machine.multiblock.IModularMachineHost;
import org.gtlcore.gtlcore.api.machine.multiblock.IModularMachineModule;
import org.gtlcore.gtlcore.utils.datastructure.ModuleRenderInfo;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

/** 模块连接与校准由主机统一维护，避免各模块独立修改主机状态。 */
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

    /** 默认启用起跑前电量检查；按需电源仍由实际处理器逐 tick 扣电。 */
    public static final boolean NAC_PRECHARGE_WHOLE_RECIPE = true;

    /** 默认搜索所有声明的配方类型，使组装与换算能在同一轮参与候选选择。 */
    public static final boolean NAC_MULTI_TYPE_RECIPES = true;

    /** 默认用 finally 释放结构匹配锁，避免匹配异常使后续检查永久阻塞。 */
    public static final boolean NAC_PATTERN_LOCK_HARD_FIX = true;

    /** 存档坐标与运行期镜像链可能不同步，恢复前必须同时核对两者。 */
    private final NanochipMirrorBindGuard mirrorBindGuard = NanochipMirrorBindGuard.create();

    @Override
    public void asyncCheckPattern(long periodID) {
        hostStructure.asyncCheckPattern(periodID);
    }

    @Override
    public boolean checkPatternWithTryLock() {
        return hostStructure.checkPatternWithTryLock();
    }

    @Override
    public boolean checkPatternWithLock() {
        return hostStructure.checkPatternWithLock();
    }

    @Override
    public void requestStructureRefresh(ServerPlayer requester) {
        hostStructure.requestStructureRefresh(requester);
    }

    /** 数量错误的候选名只是标签；必须清点全部 F 位才能区分哪种控制器超额。 */
    public String summarizeFSlotContent() {
        return moduleLinks.summarizeFSlotContent();
    }

    public static final int DEFAULT_DIRECT_PARALLEL_CAP = 1024;

    public static final int DEFAULT_DIRECT_CROSS_RECIPE_THREADS = 1;

    public static final int MAX_OVERCLOCKS = 2;

    public static final int FULL_MIN_DURATION_TICKS = 100;

    public static final int DIRECT_MIN_DURATION_TICKS = 400;

    /** 校准数据以服务端为准，客户端只消费同步摘要。 */
    private final NanochipCalibration calibration = new NanochipCalibration();

    // 上游构造器先创建配方逻辑；这里只捕获引用，实际读数仍留到原调用点。
    private final NanochipHostRecipePolicy recipePolicy = new NanochipHostRecipePolicy(
            this, calibration, super::getRecipeTypes);

    private final NanochipCalibrationAccounting calibrationAccounting = new NanochipCalibrationAccounting(
            this, calibration, this::updateSyncedTexts);

    private final NanochipHostVacuum vacuum = new NanochipHostVacuum(
            this, text -> this.syncedVacuumText = text);

    /** 同一波模块回调合并执行，避免每个模块都触发一次主机重算。 */
    public static final int RESYNC_COALESCE_TICKS = 20;

    /** 载入时暂缓主动催促，避免主机与模块反复触发彼此；周期连接检查仍继续运行。 */
    public static final int JOIN_QUIET_TICKS = 200;

    private final NanochipModuleLinks moduleLinks = new NanochipModuleLinks(this);

    private final NanochipStructureLogGate structureLogGate = new NanochipStructureLogGate();

    private final NanochipStructureDiagnostics structureDiagnostics = new NanochipStructureDiagnostics(
            this, structureLogGate, moduleLinks::summarizeModuleSlots, () -> moduleLinks.getModuleSet().size());

    // 回调只在结构检查时执行，避免组件构造期间读取尚未初始化的上游状态。
    private final NanochipHostStructure hostStructure = new NanochipHostStructure(
            this, super::asyncCheckPattern, structureDiagnostics);

    private final NanochipLoadRecovery loadRecovery = new NanochipLoadRecovery(this, () -> this.tier);

    private final NanochipHostStructureIndex hostStructureIndex = new NanochipHostStructureIndex(
            this, structureLogGate);

    private static final String FORMED_CONCLUSION = "复合体已成形";

    private static final String FORMED_LINE_FORMAT =
            "[gtlmodular] 纳米芯片组装复合体 已成形 @{}（档位={}，F 位上成形模块={}，已连接模块={}/{}，{}）";

    private static final String INVALID_CONCLUSION = "复合体结构失效";

    private static final String INVALID_LINE_FORMAT =
            "[gtlmodular] 纳米芯片组装复合体 结构失效 @{}（等玩家补齐结构；本次断开模块 {} 个）";

    public static final int ENERGY_DISTRIBUTE_INTERVAL = 20;

    private final NanochipModulePowerDistribution powerDistribution = new NanochipModulePowerDistribution(
            this, super::getEnergyContainer, draw -> this.syncedModuleDrawPerTick = draw);

    private TickableSubscription moduleRefreshSub;

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

    @DescSynced
    private int syncedCalibrationTier;

    /** 生效族由服务端同步，避免客户端复算的优先规则与实际校准判定漂移。 */
    @DescSynced
    private int syncedActiveFamilyOrdinal = -1;

    /** 客户端配置可能不同，显示分母必须采用服务端同步的判定口径。 */
    @DescSynced
    private boolean syncedFullWindowDenominator = false;

    @DescSynced
    private String syncedVacuumText = "无";

    /** 电网按玩家或队伍 UUID 记账，必须持久化身份才能在读档后继续访问同一账户。 */
    @Persisted
    @DescSynced
    @Nullable
    private UUID gridPowerUser;

    public static final int GRID_USER_LOG_INTERVAL = 200;

    private transient GridEnergyContainer gridEnergyContainer;

    // 身份仍归原字段管理；回调推迟到运行期，避免组件初始化时读取机器状态。
    private final NanochipGridPower gridPower = new NanochipGridPower(
            this, () -> this.gridPowerUser, user -> this.gridPowerUser = user, this::refreshEnergySnapshot);

    @Nullable
    public UUID gridPowerUser() {
        return gridPowerUser;
    }

    private GridEnergyContainer gridEnergyContainer() {
        if (gridEnergyContainer == null) {
            gridEnergyContainer = new GridEnergyContainer(this);
        }
        return gridEnergyContainer;
    }

    public boolean hasGridPowerIdentity() {
        return gridPower.hasGridPowerIdentity();
    }

    @Override
    public void onMachinePlaced(@Nullable net.minecraft.world.entity.LivingEntity player, ItemStack stack) {
        gridPower.onMachinePlaced(player, stack);
    }

    /** 能源列表与等级含成形时的快照，绑定状态改变后必须重算，避免沿用过期电压。 */
    private void refreshEnergySnapshot() {
        if (!isFormed()) {
            return;
        }
        this.energyContainer = getEnergyContainer();
        this.tier = GTUtil.getFloorTierByVoltage(getMaxVoltage());
    }

    @Override
    public InteractionResult onUse(BlockState state, Level level, BlockPos pos, Player player,
                                   InteractionHand hand, BlockHitResult hit) {
        return gridPower.onUse(state, level, pos, player, hand, hit);
    }

    public static final String LANG_GRID_BOUND = "gtlmodular.nanochip.grid.bound";
    public static final String LANG_GRID_ACCOUNT = "gtlmodular.nanochip.grid.account";
    public static final String LANG_GRID_UNBOUND = "gtlmodular.nanochip.grid.unbound";

    @Override
    public EnergyContainerList getEnergyContainer() {
        return gridPower.getEnergyContainer(super.getEnergyContainer(), this::gridEnergyContainer);
    }

    void warnGridDrawFailure(String site) {
        gridPower.warnGridDrawFailure(site);
    }

    /** 容量用于标记按需电源；配方可用量与支付必须读取真实余额，不能把容量当成已付电量。 */
    private static final class GridEnergyContainer
            implements com.gregtechceu.gtceu.api.capability.IEnergyContainer,
                       com.gregtechceu.gtceu.api.capability.recipe.IRecipeHandler<Long> {

        private final NanochipAssemblyComplexMachine machine;

        GridEnergyContainer(NanochipAssemblyComplexMachine machine) {
            this.machine = machine;
        }

        @Override
        public long getEnergyStored() {
            return machine.gridPower.getEnergyStored();
        }

        @Override
        public long getEnergyCapacity() {
            return machine.gridPower.getEnergyCapacity();
        }

        /** 电网按余额供电，模块逐 tick 现扣；整批电量可能超出 long，不能要求预存整批。 */
        @Override
        public long getInputVoltage() {
            return machine.gridPower.getInputVoltage();
        }

        @Override
        public long getInputAmperage() {
            return 1L;
        }

        @Override
        public long getOutputVoltage() {
            return 0L;
        }

        @Override
        public long getOutputAmperage() {
            return 0L;
        }

        @Override
        public boolean inputsEnergy(Direction side) {
            return false;
        }

        @Override
        public long acceptEnergyFromNetwork(Direction side, long voltage, long amperage) {
            return 0L;
        }

        /** 返回真实扣到的量，余额不足的剩余需求交给后续能源仓处理。 */
        @Override
        public long changeEnergy(long amount) {
            return machine.gridPower.changeEnergy(amount);
        }

        @Override
        public com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability<Long> getCapability() {
            return com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability.CAP;
        }

        /** 可行性计算读真实余额；容量哨兵不能充当配方已经拥有的电量。 */
        @Override
        public List<Object> getContents() {
            return machine.gridPower.getContents();
        }

        @Override
        public double getTotalContentAmount() {
            return machine.gridPower.getTotalContentAmount();
        }

        /** 匹配阶段只模拟，执行阶段才扣电；返回未满足需求，让后续处理器继续结算。 */
        @Override
        public List<Long> handleRecipeInner(com.gregtechceu.gtceu.api.capability.recipe.IO io,
                GTRecipe recipe, List<Long> left, @Nullable String slot, boolean simulate) {
            return machine.gridPower.handleRecipeInner(io, recipe, left, slot, simulate);
        }
    }

    @DescSynced
    private String syncedFamilyCounts = "";

    /** 逐族档位由服务端同步，避免客户端缺少计数或配置而自行算出不同结果。 */
    @DescSynced
    private String syncedFamilyTiers = "";

    @DescSynced
    private long syncedHistoryTotal;

    /** 客户端靠这份坐标识别结构仓室；空串代表没有可替换外观的仓位。 */
    @DescSynced
    @Persisted
    private String syncedNacSlots = "";

    public static final int MAX_NAC_SLOTS = 256;

    private final NanochipSlotPublisher slotPublisher = new NanochipSlotPublisher(
            this, () -> this.syncedNacSlots, slots -> this.syncedNacSlots = slots, MAX_NAC_SLOTS,
            new NanochipSlotPublisher.Messages(
                    "[gtlmodular] NAC仓位清单发布(主机): pos={} 条数={}",
                    "[gtlmodular] NAC仓位清单清空(主机): pos={}",
                    "[gtlmodular] NAC外壳连片：仓位清单心跳重发(主机) pos={} part数 {}→{} 清单={}"));

    public String nacSlotsEncoded() {
        return syncedNacSlots;
    }

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

    /** 进度条与档位判定使用同一分母，避免显示达标却没有对应加成。 */
    public double syncedShareOf(int ordinal) {
        double den = NanochipCalibration.denominatorFor(syncedHistoryTotal, syncedFullWindowDenominator);
        if (den <= 0.0D) {
            return 0.0D;
        }
        return Math.min(1.0D, syncedCountOf(ordinal) / den);
    }

    public boolean syncedUsesFullWindowDenominator() {
        return syncedFullWindowDenominator;
    }

    public double syncedHistoryProgress() {
        return Math.min(1.0D, syncedHistoryTotal / (double) NanochipCalibration.HISTORY_SIZE);
    }

    public long syncedHistoryTotal() {
        return syncedHistoryTotal;
    }

    public int syncedActiveFamilyOrdinal() {
        return syncedActiveFamilyOrdinal;
    }

    public double syncedCalibrationDenominator() {
        return NanochipCalibration.denominatorFor(syncedHistoryTotal, syncedFullWindowDenominator);
    }

    public long syncedFamilyCount(int ordinal) {
        return syncedCountOf(ordinal);
    }

    // 延迟读取原同步字段，避免组件持有过期副本或在父类构造期间读取机器状态。
    private final NanochipHostDisplay display = new NanochipHostDisplay(
            this, mirrorBindGuard, this::hasRegisteredParts, this::updateSyncedTexts,
            gridPower::addGridAccountLine, NanochipAssemblyComplexMachine::saturatingMul,
            () -> syncedMode, () -> syncedParallelShown, () -> syncedVacuumText);

    public NanochipAssemblyComplexMachine(IMachineBlockEntity holder, Object... args) {
        super(holder, args);
    }

    /** 关闭独立线程时仍需候选限额、实际可容纳并行和完成记账，保留此逻辑入口。 */
    private static final class NanochipRecipeLogic extends GTLAddMultipleTypeRecipesLogic {

        private final NanochipAssemblyComplexMachine nac;
        private final NanochipLegacyRecipePolicy<FitProbe> policy;

        NanochipRecipeLogic(NanochipAssemblyComplexMachine nac) {
            super(nac, machine -> true);
            this.nac = nac;
            this.policy = new NanochipLegacyRecipePolicy<>(nac, this.machine,
                    this::checkRecipe, this::lookupRecipeIterator, FitProbe::new, probe -> probe.fits);
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
            nac.logCraftedRecipe(last);
            super.onRecipeFinish();
        }

        /** 机器侧候选判断也需调用父类校验，因此将这个入口公开。 */
        @Override
        public boolean checkRecipe(GTRecipe recipe) {
            return super.checkRecipe(recipe);
        }

        /** 在 setupRecipe 扣料前检查预蓄电，避免已消耗输入才发现无法起跑。 */
        @Override
        public void setupRecipe(GTRecipe recipe) {
            if (NAC_PRECHARGE_WHOLE_RECIPE && recipe != null) {
                long full = policy.plannedRecipeTotalEu(recipe);
                if (full > 0L && !policy.hostAffordableWhole(this, full)) {
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

        /** 候选先校验再按线程数轮转，避免放不下的配方占用有限候选名额。 */
        @Override
        public Set<GTRecipe> lookupRecipeIterator() {
            return policy.lookupRecipeIterator();
        }

        private static final class FitProbe {

            private final boolean fits;
            private final GTRecipe scaledRecipe;

            private FitProbe(boolean fits, GTRecipe scaledRecipe) {
                this.fits = fits;
                this.scaledRecipe = scaledRecipe;
            }
        }

        /** 预算只是上限，必须探测真实输入与输出容量，才能得到可启动的并行数。 */
        @Override
        protected ParallelData calculateParallels() {
            return policy.calculateParallels();
        }

    }

    /** 默认启用独立批次计时和结算；关闭后由 NanochipRecipeLogic 接管。 */
    public static final boolean NAC_INDEPENDENT_THREADS = true;

    @Override
    public RecipeLogic createRecipeLogic(Object... args) {
        if (NAC_INDEPENDENT_THREADS) {
            return new NanochipThreadedRecipeLogic(this);
        }
        return new NanochipRecipeLogic(this);
    }

    @Override
    public GTRecipeType[] getRecipeTypes() {
        return recipePolicy.getRecipeTypes();
    }

    /** 父类按私有类型数组取当前模式；扩展列表后必须同步覆写此入口，避免下标越界。 */
    @Override
    public GTRecipeType getRecipeType() {
        return recipePolicy.getRecipeType();
    }

    /** 跨类型候选搜索需要完整类型集合，不能被只针对当前模式的查找收窄。 */
    @Override
    public boolean searchesAllRecipeTypes() {
        return NAC_MULTI_TYPE_RECIPES;
    }

    @Override
    public int nanochipThreadCount() {
        return getMultipleThreads();
    }

    @Override
    public GTRecipeType[] nanochipSearchTypes() {
        return recipePolicy.nanochipSearchTypes();
    }

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

    /** 机器持有轮转游标，逻辑重建时仍可延续公平选择的位置。 */
    @Override
    public String[] nanochipThreadCursors() {
        return this.threadCursors;
    }

    private final String[] threadCursors = { null };

    /** 前脸以成形且允许工作作为激活条件，让空闲待料的主机仍显示可工作的结构状态。 */
    @Override
    public boolean isActive() {
        return isFormed() && isWorkingEnabled();
    }

    public boolean fullMode() {
        return recipePolicy.fullMode();
    }

    public int minDurationTicks() {
        return recipePolicy.minDurationTicks();
    }

    public int directParallelCap() {
        return recipePolicy.directParallelCap();
    }

    /** 线程数量需要独立上限，不能由大数并行预算反推。 */
    public static final int MAX_CROSS_RECIPE_THREADS = 1 << 8;

    public static final int FULL_PARALLEL_CAP = Integer.MAX_VALUE;

    public static final int LEGACY_CROSS_RECIPE_PARALLEL_CAP = 8_388_607;

    /** 默认用 long 饱和运算，再在 int 接口截断，避免并行与线程相乘溢出。 */
    public static final boolean NAC_SATURATING_PARALLEL = true;

    /** 默认每条线程独立探测可容纳并行；关闭后使用整机预算分摊口径。 */
    public static final boolean NAC_PER_THREAD_FULL_PARALLEL = true;

    public int directThreads() {
        return recipePolicy.directThreads();
    }

    public int baseCrossRecipeThreads() {
        return recipePolicy.baseCrossRecipeThreads();
    }

    public int getMultipleThreads() {
        return recipePolicy.getMultipleThreads();
    }

    /** 展示追加仓的净贡献必须先应用整机上限，避免来源行与总线程数矛盾。 */
    public int effectiveExtraThreads() {
        return Math.max(0, getMultipleThreads() - baseCrossRecipeThreads());
    }

    public boolean hasCrossRecipeThreads() {
        return isFormed() && getMultipleThreads() > 1;
    }

    private IThreadModifierPart threadPartMachine;

    @Override
    public IThreadModifierPart getThreadPartMachine() {
        return this.threadPartMachine;
    }

    @Override
    public void setThreadPartMachine(IThreadModifierPart part) {
        this.threadPartMachine = part;
    }

    /** 仓离开结构后必须清掉引用，否则线程数仍会计入已经不存在的仓。 */
    private void clearThreadPart() {
        this.threadPartMachine = null;
    }

    @Override
    public int getMaxParallel() {
        return recipePolicy.getMaxParallel();
    }

    public static final String LANG_PARALLEL_TOTAL = "gtlmodular.nanochip.parallel.total";

    /** 默认隐藏补充说明行，减少主机面板的重复信息。 */
    public static final boolean SHOW_VERBOSE_HOST_LINES = false;

    @Override
    protected void addParallelDisplay(List<Component> textList) {
        if (textList == null) {
            return;
        }
        super.addParallelDisplay(textList);
        display.addParallelDisplay(textList);
    }

    public int countModules(NanochipModuleKind kind) {
        return kind == null ? 0 : countModulesById(
                com.gtlmodular.common.machine.nanochip.registration.NanochipModules.builtin(kind).id());
    }

    public int countModulesById(net.minecraft.resources.ResourceLocation id) {
        int n = 0;
        for (IModularMachineModule<NanochipAssemblyComplexMachine, ?> m : getModules()) {
            if (m instanceof NanochipModuleMachine module && module.isFormed()
                    && module.getModuleSpec().id().equals(id)) {
                n++;
            }
        }
        return n;
    }

    public int formedModuleCount() {
        int n = 0;
        for (IModularMachineModule<NanochipAssemblyComplexMachine, ?> m : getModules()) {
            if (m instanceof NanochipModuleMachine module && module.isFormed()) {
                n++;
            }
        }
        return n;
    }

    public NanochipModuleBonusSnapshot moduleBonusSnapshot() {
        return recipePolicy.moduleBonusSnapshot();
    }

    public double moduleEuMultiplier(NanochipCalibration.Family family) {
        return recipePolicy.moduleEuMultiplier(family);
    }

    /** 激光加成作用于蚀刻模块自身加工，不能在主机倍率里再计算一次。 */
    public double moduleDurationMultiplier(NanochipCalibration.Family family) {
        return recipePolicy.moduleDurationMultiplier(family);
    }

    public static GTRecipe nacRecipeModifier(MetaMachine machine, GTRecipe recipe,
                                             OCParams params, OCResult result) {
        if (!(machine instanceof NanochipAssemblyComplexMachine nac)) {
            return null;
        }
        return nac.buildRecipe(recipe);
    }

    public GTRecipe buildRecipe(GTRecipe recipe) {
        return recipePolicy.buildRecipe(recipe);
    }

    /** 配方显式声明的校准族优先于产物 ID 推断，避免自定义配方被错误归族。 */
    public static NanochipCalibration.Family familyOf(GTRecipe recipe) {
        return NanochipCalibrationAccounting.familyOf(recipe);
    }

    private static ItemStack firstItemStackOf(Object content) {
        return NanochipCalibrationAccounting.firstItemStackOf(content);
    }

    void logCraftedRecipe(GTRecipe recipe) {
        calibrationAccounting.logCraftedRecipe(recipe);
    }

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
        String limitText = budget == Long.MAX_VALUE
                ? "每条配方各自满并行 · 去总池后上限 Long.MAX_VALUE"
                : "并行总池 " + budget;
        NanochipModContext.logger().info("[gtlmodular] NAC 本次搜索「有 {} 条候选但一条都放不下」（{}，模式 {}）"
                        + "⇒ 本 tick 不开工。这不是「没匹配上配方」，而是「放大后的输入/产出放不下」；"
                        + "检修方向：输出仓/AE 剩余容量、输入库存是否够 1 份料。",
                candidates, limitText, syncedMode);
    }

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

    private long lastHostPrechargeLogTick = -10_000L;

    private long lastNoFitLogTick = -10_000L;

    /** 默认只有配置白名单中的配方能增加校准；关闭后所有配方都可进入记账链路。 */
    public static final boolean CALIBRATION_WHITELIST_ENABLED = true;

    private void recordProduction(GTRecipe recipe) {
        calibrationAccounting.recordProduction(recipe);
    }

    /** 模块完成的电路仍属于主机校准，必须通过同一入口记录。 */
    public void recordExternalProduction(GTRecipe recipe) {
        recordProduction(recipe);
    }

    private void tickSync() {
        Level lv = getLevel();
        if (lv == null || lv.isClientSide) {
            return;
        }
        powerDistribution.tickDrawWindow();
        updateSyncedTexts();
        // 预检失败会提前返回，缺件审计放在服务端 tick 才能覆盖这些失败路径。
        if (!isFormed()) {
            structureDiagnostics.logUnformedAudit();
            moduleLinks.disconnectIfExpired(lv);
        } else {
            moduleLinks.cancelDisconnect();
        }
        hostStructure.tickRefreshReport(lv);
    }

    void nanochipTickSync() {
        tickSync();
    }

    private void updateSyncedTexts() {
        syncedMode = display.modeText(fullMode());
        syncedCalibrationText = calibration.summary();
        syncedCalibrationTier = calibration.activeTier();
        NanochipCalibration.Family activeFamily = calibration.activeFamily();
        syncedActiveFamilyOrdinal = activeFamily == null ? -1 : activeFamily.ordinal();
        syncedFullWindowDenominator = NanochipCalibration.useFullWindowDenominator();
        syncedBreakdownText = calibration.breakdown();
        syncedFamilyCounts = display.familyCounts(calibration);
        syncedFamilyTiers = display.familyTiers(calibration);
        syncedHistoryTotal = calibration.total();
        // 网络摘要只在内容变化时重建，避免每 tick 拼接和同步。
        vacuum.updateSummary();
        syncedParallelShown = getMaxParallel();
        syncedModuleText = display.moduleText();
    }

    public NanochipCalibration getCalibration() {
        return calibration;
    }

    public double calibrationEuMultiplierForDisplay() {
        return isFormed() ? calibration.euMultiplier() : 1.0D;
    }

    public double calibrationDurationMultiplierForDisplay() {
        if (!isFormed()) return 1.0D;
        NanochipCalibration.Family active = calibration.activeFamily();
        return active == null ? 1.0D : calibration.durationMultiplier(active);
    }

    public int clearCalibration() {
        return calibrationAccounting.clearCalibration();
    }

    public NanochipVacuumNetwork vacuumNetwork() {
        return vacuum.vacuumNetwork();
    }

    public NanochipPipeGraph pipeGraph() {
        return vacuum.pipeGraph();
    }

    public static String routingModeText() {
        return NanochipHostVacuum.routingModeText();
    }

    /** 切回简单模式时合并分量池，避免管道模式留下的物品变得不可访问。 */
    private void reconcileRoutingMode() {
        vacuum.reconcileRoutingMode();
    }

    public void markVacuumDirty() {
        vacuum.markVacuumDirty();
    }

    @DescSynced
    private long syncedModuleDrawPerTick;

    public long getModuleDrawPerTick() {
        return syncedModuleDrawPerTick;
    }

    public long getTotalStoredEU() {
        return powerDistribution.getTotalStoredEU();
    }

    public long getTotalCapacityEU() {
        return powerDistribution.getTotalCapacityEU();
    }

    public long getMaxInputVoltage() {
        return powerDistribution.getMaxInputVoltage();
    }

    public long getMaxInputPower() {
        return powerDistribution.getMaxInputPower();
    }

    public static final long ON_DEMAND_POWER_FLOOR_EU = Long.MAX_VALUE / 2L;

    /** 先判界再乘，避免溢出后的负值被误当成缺料或缺电。 */
    private static long saturatingMul(long a, long b) {
        return NanochipModulePowerDistribution.saturatingMul(a, b);
    }

    private static long saturatingAdd(long a, long b) {
        return NanochipModulePowerDistribution.saturatingAdd(a, b);
    }

    static int saturatingToInt(long v) {
        return v >= (long) Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.max(0L, v);
    }

    public boolean isEnergySourceEffectivelyUnlimited() {
        return powerDistribution.isEnergySourceEffectivelyUnlimited();
    }

    public long getObtainableEnergyEU() {
        return powerDistribution.getObtainableEnergyEU();
    }

    /** 模块逐 tick 请求真实电量；必须全额支付才能推进，低优先级模块还须为缺电模块让路。 */
    public boolean requestModuleEnergy(long amount, boolean lowPriority) {
        return powerDistribution.requestModuleEnergy(amount, lowPriority);
    }

    /** 查询只读主机权威集合，避免 UI 或模块读数触发连接变更。 */
    public boolean isModuleConnected(IModularMachineModule<NanochipAssemblyComplexMachine, ?> module) {
        return moduleLinks.isModuleConnected(module);
    }

    /**
     * 区块重载或读档后，同一坐标会换成新的主机对象，而旧对象仍带着成形标记，
     * 它的延迟成形回调还可能执行。只有世界里当前这台主机才能连接模块，否则模块会被旧对象抢走、有料也不开工。
     */
    public boolean isLiveHost() {
        Level level = getLevel();
        return level != null && !isInValid() && MetaMachine.getMachine(level, getPos()) == this;
    }

    /** 先走框架扫描，再以 F 位快照收敛差集，兼顾无快照的载入阶段。 */
    @Override
    public void scanAndConnectModules() {
        if (!isLiveHost()) {
            return;
        }
        IModularMachineHost.super.scanAndConnectModules();
        moduleLinks.refreshModulesFromStructure();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        moduleLinks.onLoad();
        this.structureLogGate.reset();
        slotPublisher.resetPartCount();
        slotPublisher.resetHeartbeat();
        if (getLevel() != null && !getLevel().isClientSide) {
            unsubscribeModuleRefresh();
            moduleRefreshSub = subscribeServerTick(this::tickModuleRefresh);
            vacuum.markTopologyDirty();
            reconcileRoutingMode();
            gridPower.logLoadedAccount();
        }
    }

    @Override
    public void onUnload() {
        unsubscribeModuleRefresh();
        hostStructureIndex.unregisterFromStructureIndex();
        hostStructureIndex.dropPendingRemoval();
        super.onUnload();
    }

    private void unsubscribeModuleRefresh() {
        if (moduleRefreshSub != null) {
            moduleRefreshSub.unsubscribe();
            moduleRefreshSub = null;
        }
    }

    /** 默认关闭主动读档恢复，避免自动发起昂贵的结构失效与重建。 */
    private static final boolean LOAD_PARTS_RECOVERY_ENABLED = false;

    /** 启用恢复时默认先失效再交上游重建；单纯重判可能保留已成形但零件为空的状态。 */
    private static final boolean LOAD_PARTS_INVALIDATE_RECOVERY_ENABLED = true;

    private boolean hasRegisteredParts() {
        return NanochipLoadRecovery.hasRegisteredParts(this);
    }

    public void noteCachedBlockRemoved(BlockPos pos) {
        hostStructureIndex.noteCachedBlockRemoved(pos);
    }

    private void tickModuleRefresh() {
        Level level = getLevel();
        if (level == null) {
            return;
        }
        long now = level.getGameTime();
        loadRecovery.recoverLoadPartsIfNeeded(LOAD_PARTS_RECOVERY_ENABLED, LOAD_PARTS_INVALIDATE_RECOVERY_ENABLED);
        // 镜像恢复依赖零件表，因此必须排在部件恢复之后。
        mirrorBindGuard.tick(this);
        hostStructure.confirmFoldedPattern();
        hostStructureIndex.syncStructurePosIndex();
        hostStructureIndex.invalidateOnCachedRemoval();
        slotPublisher.tickNacSlotHeartbeat();
        powerDistribution.distributeModuleEnergy(now);
        moduleLinks.tickModuleRefresh(now);
    }

    public void requestCoalescedResync() {
        moduleLinks.requestCoalescedResync();
    }

    /** 只按实际缺口与真实存量补缓存；容量哨兵不能当成可扣电量。 */
    long topUpModuleBuffer(NanochipModuleMachine mm) {
        return powerDistribution.topUpModuleBuffer(mm);
    }

    public void requestBufferTopUp(NanochipModuleMachine mm) {
        powerDistribution.requestBufferTopUp(mm);
    }

    /** 按 14 种内置模块各一台加一个余量限制连接数，与结构谓词保持一致。 */
    @Override
    public int getMaxModuleCount() {
        return 15;
    }

    @Override
    public Set<IModularMachineModule<NanochipAssemblyComplexMachine, ?>> getModuleSet() {
        return moduleLinks.getModuleSet();
    }

    @Override
    public BlockPos[] getModuleScanPositions() {
        return moduleLinks.getModuleScanPositions();
    }

    /** 默认直接使用固定槽位偏移；启用后才按主机朝向旋转扫描位置。 */
    public static final boolean NAC_MODULE_SCAN_ROTATE_BY_HOST = false;

    /** 框架默认不提供预览条目，必须返回模块模板才能显示模块预览。 */
    @Override
    public List<ModuleRenderInfo> getModulesForRendering() {
        try {
            return NanochipModulePreview.nacModules(getPos());
        } catch (Throwable t) {
            SwallowedErrors.report("NanochipAssemblyComplexMachine#getModulesForRendering", t);
            NanochipModContext.logger().debug("[gtlmodular] 模块预览: NAC getModulesForRendering 异常，返回空表: {}",
                    t.toString());
            return List.of();
        }
    }

    public int moduleSlotCount() {
        return moduleLinks.moduleSlotCount();
    }

    @Override
    public void onStructureFormed() {
        super.onStructureFormed();
        slotPublisher.publishNacSlots();
        slotPublisher.recordFormedParts();
        // Jade 读取结构错误状态；成形时清除残留错误，避免外部显示仍为不完整。
        MultiblockState formedState = getMultiblockState();
        if (formedState != null && formedState.hasError()) {
            formedState.setError(null);
        }
        // 成形后能力代理已经建立，此时注册电网处理器才能接入配方扣电。
        gridPower.registerGridPowerHandler(this::gridEnergyContainer);
        moduleLinks.snapshotModuleSlots();
        hostStructureIndex.onStructureFormed();
        vacuum.markTopologyDirty();
        reconcileRoutingMode();
        Level formedLevel = getLevel();
        // 连接变更涉及普通集合，统一投递到服务端线程执行。
        if (formedLevel instanceof ServerLevel formedServer) {
            formedServer.getServer().execute(this::scanAndConnectModules);
        } else {
            scanAndConnectModules();
        }
        updateSyncedTexts();
        Level formedLogLevel = getLevel();
        long formedLogNow = formedLogLevel == null ? 0L : formedLogLevel.getGameTime();
        String formedDetail = (fullMode() ? "全流程档" : "直通档") + "|F 位=" + moduleLinks.lastMatchedSlotCount();
        boolean emitFormed = this.structureLogGate.shouldEmitInfo(FORMED_CONCLUSION, formedDetail,
                "复合体@" + getPos().toShortString(), formedLogNow);
        if (emitFormed || NanochipModContext.logger().isDebugEnabled()) {
            Object[] formedArgs = {getPos(), fullMode() ? "全流程档" : "直通档",
                    moduleLinks.lastMatchedSlotCount(), formedModuleCount(), getMaxModuleCount(),
                    summarizeFSlotContent()};
            if (emitFormed) {
                NanochipModContext.logger().info(FORMED_LINE_FORMAT, formedArgs);
            } else {
                NanochipModContext.logger().debug(FORMED_LINE_FORMAT, formedArgs);
            }
        }
        hostStructure.reportRefreshResult();
    }

    @Override
    public void onStructureInvalid() {
        super.onStructureInvalid();
        clearThreadPart();
        slotPublisher.clearNacSlots();
        hostStructureIndex.unregisterFromStructureIndex();
        hostStructureIndex.dropPendingRemoval();
        gridPower.unregisterGridPowerHandler(this::gridEnergyContainer);
        // 清空后重置计数基线，避免相同零件数掩盖下一次应补发的清单。
        slotPublisher.resetPartCount();
        Level bufLevel = getLevel();
        if (bufLevel instanceof ServerLevel bufServer) {
            bufServer.getServer().execute(powerDistribution::clearModuleBuffers);
        } else {
            powerDistribution.clearModuleBuffers();
        }
        int dropped = moduleLinks.getModuleSet().size();
        // 先记录失效前的模块与槽位状态，再做连接清理，保留诊断证据。
        structureDiagnostics.logStructureFailureDetail();
        structureDiagnostics.logUnformedAudit();
        // 短暂结构失效先进入宽限期，避免一次局部变更立即断开全部模块。
        moduleLinks.scheduleDisconnect();
        vacuum.clearTopology();
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
        // 失效意味着结构证据变化，重置变化闸才能及时检查玩家补齐后的结构。
        hostStructure.resetCheckGate();
    }

    @Override
    public void onMachineRemoved() {
        safeClearModules();
        hostStructureIndex.unregisterFromStructureIndex();
        hostStructureIndex.dropPendingRemoval();
    }

    @Override
    public boolean keepSubscribing() {
        return true;
    }

    /** 零件卸载不一定经过结构失效，需在此处也清除线程仓引用。 */
    @Override
    public void onPartUnload() {
        super.onPartUnload();
        clearThreadPart();
    }

    public static String describeStructureFailure(MultiblockState state) {
        return NanochipStructureDiagnostics.describeStructureFailure(state);
    }

    @Override
    public void saveCustomPersistedData(CompoundTag tag, boolean forDrop) {
        super.saveCustomPersistedData(tag, forDrop);
        // 校准与真空库存仅随世界存档保存，掉落物不携带这些运行数据。
        if (!forDrop) {
            tag.put("GTLModularNanochipCalibration", calibration.save());
            vacuum.save(tag);
        }
        moduleLinks.saveModuleSlots(tag, forDrop);
    }

    @Override
    public void loadCustomPersistedData(CompoundTag tag) {
        super.loadCustomPersistedData(tag);
        if (tag.contains("GTLModularNanochipCalibration")) {
            calibration.load(tag.getCompound("GTLModularNanochipCalibration"));
        }
        vacuum.load(tag);
        moduleLinks.loadModuleSlots(tag);
    }

    /** 最短耗时由 NAC 的配方策略决定，隐藏父类调节按钮以免界面暗示它能覆盖该策略。 */
    @Override
    protected IFancyConfigurator createConfigurators() {
        return null;
    }

    /** 附加配置页保留父类机器界面，避免覆盖主页面的进度与模式控件。 */
    @Override
    public void attachConfigurators(ConfiguratorPanel panel) {
        super.attachConfigurators(panel);
        display.attachConfigurators(panel);
    }

    /** 默认隐藏主机手动刷新按钮，避免面板出现额外结构操作。 */
    public static final boolean SHOW_STRUCTURE_REFRESH_BUTTON = false;

    @Override
    public void addDisplayText(List<Component> textList) {
        super.addDisplayText(textList);
        display.addDisplayText(textList, LOAD_PARTS_RECOVERY_ENABLED);
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "NAC[%s, 模式=%s, 并行=%d, 校准=%s, 校准分母=%s]",
                getPos(), syncedMode, syncedParallelShown, syncedCalibrationText,
                syncedFullWindowDenominator
                        ? "原版固定 " + NanochipCalibration.HISTORY_SIZE
                        : "已记录数");
    }
}
