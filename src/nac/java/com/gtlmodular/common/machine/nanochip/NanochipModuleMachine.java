package com.gtlmodular.common.machine.nanochip;

import com.gtlmodular.common.machine.nanochip.vacuum.NanochipModuleVacuum;
import com.gtlmodular.common.machine.nanochip.display.NanochipModuleDisplay;
import com.gtlmodular.common.machine.nanochip.vacuum.NanochipSplitterPanel;
import com.gtlmodular.common.machine.nanochip.registration.NanochipModuleSpec;
import com.gtlmodular.common.machine.nanochip.registration.NanochipModules;
import com.gtlmodular.common.data.nanochip.NanochipModContext;
import com.gtlmodular.common.diag.SwallowedErrors;
import com.gregtechceu.gtceu.api.gui.fancy.ConfiguratorPanel;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyConfigurator;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.feature.IMachineLife;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IDisplayUIMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.logic.OCParams;
import com.gregtechceu.gtceu.api.recipe.logic.OCResult;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.gtlcore.gtlcore.api.machine.multiblock.IModularMachineModule;
import org.gtlcore.gtlcore.api.recipe.RecipeResult;
import org.gtlcore.gtlcore.common.machine.multiblock.electric.WorkableElectricMultipleRecipesMachine;
import org.gtlcore.gtlcore.common.machine.trait.MultipleRecipesLogic;

import java.util.List;

/** 内置模块共用此实现；旧 kind 读口与规格读口指向同一份参数。 */
public class NanochipModuleMachine extends WorkableElectricMultipleRecipesMachine
        implements IModularMachineModule<NanochipAssemblyComplexMachine, NanochipModuleMachine>,
        IMachineLife, IDisplayUIMachine, NanochipStructureRefresh.Refreshable {

    public static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            NanochipModuleMachine.class,
            WorkableMultiblockMachine.MANAGED_FIELD_HOLDER);

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    /** 默认由上游负责成形判定，跳过模块侧附加的预检、节流和同 tick 折叠。 */
    public static final boolean NAC_MODULE_FORMATION_BY_UPSTREAM = true;

    @Override
    public boolean checkPatternWithTryLock() {
        return super.checkPatternWithTryLock();
    }

    @Override
    public boolean checkPatternWithLock() {
        return super.checkPatternWithLock();
    }

    @Override
    public void asyncCheckPattern(long periodID) {
        super.asyncCheckPattern(periodID);
    }

    @Override
    public void requestStructureRefresh(ServerPlayer requester) {
        if (getLevel() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            com.gregtechceu.gtceu.api.pattern.MultiblockWorldSavedData.getOrCreate(serverLevel).addAsyncLogic(this);
        }
    }

    public static final int MIN_DURATION_TICKS = 100;

    /** 倍率保持 1.0，产出数量由配方本身决定，避免结算时再次缩减。 */
    public static final double SIMPLE_LOGISTICS_OUTPUT_MULTIPLIER = 1.0D;

    private final NanochipModuleKind kind;
    private final NanochipModuleSpec spec;

    private final NanochipModuleStructure structure;

    private final NanochipModuleProcessing processing;

    private final NanochipModuleVacuum vacuum = new NanochipModuleVacuum(this);

    private TickableSubscription routerTickSub;

    /** 保留主机坐标用于框架重连；有效连接仍须由主机集合确认，不能只凭坐标判定。 */
    @Persisted
    private BlockPos hostPosition;

    private final NanochipModuleHostLink hostLink = new NanochipModuleHostLink(
            this, IModularMachineModule.super::findAndConnectToHost, this::refreshHostSync);

    // 客户端没有服务端的连接集合，界面须读取同步快照。

    @DescSynced
    private boolean syncedHostConnected;

    @DescSynced
    private String syncedHostInfo = "尚未被复合体登记；把它建在复合体基座的模块位上，或先让复合体成形";

    @DescSynced
    private boolean syncedDrawingFromHost;

    @DescSynced
    private int syncedHostTier;

    @DescSynced
    private long syncedBufferEU;

    @DescSynced
    private long bufferCapacityEU;

    private final NanochipModulePower modulePower = new NanochipModulePower(
            this, () -> this.syncedBufferEU, value -> this.syncedBufferEU = value,
            () -> this.bufferCapacityEU, value -> this.bufferCapacityEU = value);

    public long getBufferEU() {
        return syncedBufferEU;
    }

    public long getBufferCapacityEU() {
        return bufferCapacityEU;
    }

    /** 缓存上限只增不减，避免公式容量小于当前配方整批耗电时永远无法开工。 */
    public void setBufferCapacity(long capacity) {
        modulePower.setBufferCapacity(capacity);
    }

    /** 实际配方可能超出种类参数的时间预算，缓存必须至少容得下整批需求。 */
    public void ensureBufferCovers(long fullRecipeEU) {
        modulePower.ensureBufferCovers(fullRecipeEU);
    }

    /** 此处接收的电量已由主机扣除，返回实际接受量供主机退回余量。 */
    public long fillBufferEU(long amount) {
        return modulePower.fillBufferEU(amount);
    }

    public boolean consumeBufferEU(long amount) {
        return modulePower.consumeBufferEU(amount);
    }

    public void clearBufferEU() {
        modulePower.clearBufferEU();
    }

    public int energyPriority() {
        return modulePower.energyPriority(spec);
    }

    /** 客户端靠同步坐标识别结构仓室，不能自行按方块类型推断。 */
    @DescSynced
    @Persisted
    private String syncedNacSlots = "";

    public static final int MAX_NAC_SLOTS = 256;

    private final NanochipSlotPublisher slotPublisher = new NanochipSlotPublisher(
            this, () -> this.syncedNacSlots, slots -> this.syncedNacSlots = slots, MAX_NAC_SLOTS,
            new NanochipSlotPublisher.Messages(
                    "[gtlmodular] NAC仓位清单发布(模块): pos={} 条数={}",
                    "[gtlmodular] NAC仓位清单清空(模块): pos={}",
                    "[gtlmodular] NAC外壳连片：仓位清单心跳重发(模块) pos={} part数 {}→{} 清单={}"));

    public String nacSlotsEncoded() {
        return syncedNacSlots;
    }

    private TickableSubscription hostStateSub;

    private final NanochipModuleDisplay display = new NanochipModuleDisplay(
            this, () -> syncedHostConnected, () -> syncedHostInfo, () -> syncedDrawingFromHost);

    public NanochipModuleMachine(IMachineBlockEntity holder, NanochipModuleKind kind) {
        this(holder, NanochipModules.builtin(kind));
    }

    public NanochipModuleMachine(IMachineBlockEntity holder, NanochipModuleSpec spec) {
        super(holder);
        this.spec = java.util.Objects.requireNonNull(spec, "spec");
        this.kind = spec.legacyKind();
        // 父构造器会先创建配方逻辑；策略只能在种类和供电组件就绪后装配。
        this.structure = new NanochipModuleStructure(this, spec, super::checkPatternWithTryLock,
                super::checkPatternWithLock, super::asyncCheckPattern);
        this.processing = new NanochipModuleProcessing(this, spec, modulePower,
                () -> this.impurity, value -> this.impurity = value, () -> this.autoFlushThreshold);
    }

    public NanochipModuleKind getModuleKind() {
        return kind;
    }

    public NanochipModuleSpec getModuleSpec() {
        return spec;
    }

    public NanochipSplitterRules splitterRules() {
        return vacuum.splitterRules();
    }

    public static final double DEFAULT_IMPURITY_PER_RECIPE = 0.5D;

    @DescSynced
    private double impurity;

    @DescSynced
    private int autoFlushThreshold = 100;

    public double impurity() {
        return impurity;
    }

    public int autoFlushThreshold() {
        return autoFlushThreshold;
    }

    public void setAutoFlushThreshold(int threshold) {
        this.autoFlushThreshold = Math.max(1, Math.min(100, threshold));
    }

    public double impurityEuMultiplier() {
        return processing.impurityEuMultiplier();
    }

    private void onRecipeProduced() {
        processing.onRecipeProduced();
    }

    /**
     * 排空的唯一写回点：NH 在后处理里每 20 tick 复核一次，补上输出仓的存档最多 1 秒就能恢复。
     * 抽料或排料不成就不清零：宁可停在 100％ 卡住，也不能白拿一次含杂度归零。
     */
    void flushImpurity() {
        double flushed = impurity;
        if (!NanochipModuleImpurity.flushSolder(this)) {
            return;
        }
        impurity = 0.0D;
        if (getLevel() != null && !getLevel().isClientSide
                && NanochipModContext.config().isNanochipDebugLogsEnabled()) {
            NanochipModContext.logger().info(
                    "[gtlmodular] 全板浸没装置排空 @{}（阈值 {}%，排前 {}%）：抽超突变 {} mB → 排突变 {} mB",
                    getPos(), autoFlushThreshold, flushed,
                    NanochipModuleImpurity.SOLDER_PER_FLUSH, NanochipModuleImpurity.MUTATED_PER_FLUSH);
        }
    }

    public int highestPurifiedWaterGrade() {
        return processing.highestPurifiedWaterGrade();
    }

    public double waterStrength() {
        return processing.waterStrength();
    }

    public int laserHatchTier() {
        return processing.laserHatchTier();
    }

    public long laserAmperage() {
        return processing.laserAmperage();
    }

    // 无本地能源仓时，电压、等级和实际扣电都要采用同一主机来源，避免试算与付款不一致。

    private transient HostEnergyContainer hostEnergyContainer;

    private HostEnergyContainer hostEnergyContainer() {
        if (hostEnergyContainer == null) {
            hostEnergyContainer = new HostEnergyContainer(this);
        }
        return hostEnergyContainer;
    }

    @Override
    public com.gregtechceu.gtceu.api.misc.EnergyContainerList getEnergyContainer() {
        return modulePower.getEnergyContainer(super.getEnergyContainer(), this::hostEnergyContainer);
    }

    public boolean drawingFromHost() {
        return modulePower.drawingFromHost(super.getEnergyContainer());
    }

    @Override
    public int getTier() {
        return modulePower.getTier(super::getTier);
    }

    @Override
    public long getOverclockVoltage() {
        return modulePower.getOverclockVoltage(super::getOverclockVoltage);
    }

    private static final class HostEnergyContainer
            implements com.gregtechceu.gtceu.api.capability.IEnergyContainer {
        private final NanochipModuleMachine module;

        HostEnergyContainer(NanochipModuleMachine module) {
            this.module = module;
        }

        @Override
        public long getEnergyStored() {
            return module.modulePower.getEnergyStored();
        }

        @Override
        public long getEnergyCapacity() {
            return module.modulePower.getEnergyCapacity();
        }

        @Override
        public long getInputVoltage() {
            return module.modulePower.getInputVoltage();
        }

        @Override
        public long getInputAmperage() {
            return 1L;
        }

        @Override
        public boolean inputsEnergy(net.minecraft.core.Direction side) {
            return false;
        }

        @Override
        public long acceptEnergyFromNetwork(net.minecraft.core.Direction side, long amount, long voltage) {
            return 0L;
        }

        @Override
        public long changeEnergy(long amount) {
            return module.modulePower.changeEnergy(amount);
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        structure.resetLogGate();
        if (getLevel() != null && !getLevel().isClientSide) {
            slotPublisher.resetHeartbeat();
            slotPublisher.resetPartCount();
            unsubscribeHostStateTick();
            hostStateSub = subscribeServerTick(this::tickHostState);
            refreshHostSync();
        }
        if (spec.isRouter() && getLevel() != null && !getLevel().isClientSide) {
            unsubscribeRouterTick();
            routerTickSub = subscribeServerTick(this::tickRouter);
        }
    }

    @Override
    public void onUnload() {
        unsubscribeRouterTick();
        unsubscribeHostStateTick();
        super.onUnload();
    }

    private void unsubscribeHostStateTick() {
        if (hostStateSub != null) {
            hostStateSub.unsubscribe();
            hostStateSub = null;
        }
    }

    private void tickHostState() {
        Level level = getLevel();
        if (level == null || level.isClientSide) {
            return;
        }
        long now = level.getGameTime();
        // NH 的排空判断在后处理里每 20 tick 一次；只挂在配方结束上，满含杂度的设备补上输出仓也永远排不掉。
        if (now % 20L == 0L && spec.features().board() && NanochipModuleImpurity.shouldFlush(this)) {
            flushImpurity();
        }
        slotPublisher.tickNacSlotHeartbeat();
        hostLink.tickHostSync(now);
    }

    private void refreshHostSync() {
        Level level = getLevel();
        if (level == null || level.isClientSide) {
            return;
        }
        NanochipAssemblyComplexMachine h = hostLink.getHost();
        boolean connected = hostConnected();
        syncedHostConnected = connected;
        syncedDrawingFromHost = drawingFromHost();
        syncedHostTier = connected && h != null ? h.getTier() : 0;
        syncedHostInfo = NanochipModuleDisplay.hostInfo(h, connected);
    }

    private void unsubscribeRouterTick() {
        if (routerTickSub != null) {
            routerTickSub.unsubscribe();
            routerTickSub = null;
        }
    }

    private void tickRouter() {
        vacuum.tickRouter(spec);
    }

    @Override
    public int getMaxParallel() {
        return Integer.MAX_VALUE;
    }

    /** 只收集成功扣料的原配方；setup 时的合批已有来源，不能再按整批重复捕获。 */
    public void captureCalibrationProduction(GTRecipe recipe) {
        if (spec.features().assembly() && recipe != null && !NanochipCalibrationBatch.hasSources(recipe)
                && getRecipeLogic() instanceof ModuleRecipeLogic logic) {
            logic.calibrationBatch.capture(recipe);
        }
    }

    /** 组装矩阵的产出也需计入主机校准，因此完成回调统一交主机记账。 */
    private static final class ModuleRecipeLogic extends MultipleRecipesLogic {
        private final NanochipModuleMachine module;
        private final NanochipCalibrationBatch calibrationBatch = new NanochipCalibrationBatch();

        ModuleRecipeLogic(NanochipModuleMachine module) {
            super(module);
            this.module = module;
        }

        @Override
        public void findAndHandleRecipe() {
            calibrationBatch.clear();
            if (NanochipModuleImpurity.blocksStart(module)) {
                // 必须搜之前拦住：MultipleRecipesLogic 的搜索会真的扣输入，先搜再拒会吃掉材料。
                lastFailedMatches = null;
                Component reason = NanochipModuleImpurity.ceilingReason();
                RecipeResult.of(module, RecipeResult.fail(reason));
                setWaiting(reason);
                return;
            }
            super.findAndHandleRecipe();
        }

        @Override
        protected double getTotalEuOfRecipe(GTRecipe recipe) {
            return module.processing.calibratedTotalEu(recipe, super.getTotalEuOfRecipe(recipe));
        }

        /**
         * 批次开始时只做校准记账。产出策略（简单物流倍率）已收进 {@code moduleFlexibleOverclock}
         * 这个配方修饰器 —— 上游 {@code RecipeLogic.checkMatchedRecipeAvailable} 会把修饰结果原样交给
         * setupRecipe，没有必要在结算入口再开一条旁路。
         */
        @Override
        public void setupRecipe(GTRecipe recipe) {
            calibrationBatch.attach(recipe);
            super.setupRecipe(recipe);
        }

        @Override
        public void onRecipeFinish() {
            GTRecipe completed = getLastRecipe();
            if (completed != null && !NanochipRecipeOutputs.tryOutput(machine, completed)) {
                setWaiting(Component.translatable(NanochipRecipeOutputs.OUTPUT_FULL));
                return;
            }
            NanochipAssemblyComplexMachine host = module.getHost();
            if (host != null && module.hostConnected()
                    && module.spec.features().assembly()) {
                host.recordExternalProduction(getLastRecipe());
            }
            module.onRecipeProduced();
            calibrationBatch.clear();
            // 父类仍负责暂停和下一批调度，但本批已交付，不能再次输出。
            GTRecipe settled = completed == null ? null : completed.copy();
            if (settled != null) settled.outputs.clear();
            lastRecipe = settled;
            super.onRecipeFinish();
            if (lastRecipe == settled) lastRecipe = completed;
        }

        /** 中央供电绕过本机能源仓，必须在此真实扣电后才允许推进。 */
        @Override
        public void handleRecipeWorking() {
            GTRecipe recipe = getLastRecipe();
            if (recipe != null && !module.modulePower.localBatchReady(recipe, getProgress(), this::waitForEnergy)) {
                return;
            }
            if (recipe == null || !module.drawingFromHost()) {
                super.handleRecipeWorking();
                return;
            }
            if (!module.modulePower.drawRecipeEnergy(recipe, this::getProgress, this::waitForEnergy)) {
                return;
            }
            setStatus(RecipeLogic.Status.WORKING);
            if (!machine.onWorking()) {
                interruptRecipe();
                return;
            }
            progress++;
            totalContinuousRunningTime++;
        }

        private void waitForEnergy(Component reason) {
            setWaiting(reason);
            if (getStatus() == RecipeLogic.Status.WAITING) {
                doDamping();
            }
        }
    }

    @Override
    protected RecipeLogic createRecipeLogic(Object... args) {
        return new ModuleRecipeLogic(this);
    }

    public static GTRecipe moduleOverclock(MetaMachine machine, GTRecipe recipe,
                                           OCParams params, OCResult result) {
        if (!(machine instanceof NanochipModuleMachine module)) {
            return null;
        }
        return module.processing.moduleOverclock(recipe);
    }

    public static GTRecipe moduleFlexibleOverclock(MetaMachine machine, GTRecipe recipe,
                                                   OCParams params, OCResult result) {
        return NanochipModuleProcessing.moduleFlexibleOverclock(machine, recipe, params, result);
    }

    public static GTRecipe scaleOutputsForSimpleLogistics(MetaMachine machine, GTRecipe recipe) {
        return NanochipModuleProcessing.scaleOutputsForSimpleLogistics(machine, recipe);
    }

    /** 连接判定只读主机权威集合，避免查询触发连接变更。 */
    public boolean hostConnected() {
        return hostLink.hostConnected();
    }

    /** 默认不主动重连或催促主机，由主机扫描建立连接；启用后才调用模块侧连接路径。 */
    public static final boolean NAC_MODULE_ACTIVE_CONNECT = false;

    /** 允许主动连接时复用 IModularMachineModule#findAndConnectToHost，使已存主机坐标优先恢复。 */
    @Override
    public boolean findAndConnectToHost() {
        return hostLink.findAndConnectToHost();
    }

    @Override
    public void onConnected(NanochipAssemblyComplexMachine host) {
        refreshHostSync();
    }

    public void onHostLinked() {
        refreshHostSync();
    }

    @Override
    public void onDisconnected() {
        refreshHostSync();
    }

    /** 只请求主机合并重算，避免模块与主机各自修改集合造成双向状态分歧。 */
    private void requestHostResync() {
        hostLink.requestHostResync();
    }

    @Override
    public void onStructureFormed() {
        super.onStructureFormed();
        // 上游只在「异步检查成形」那条路里把控制器登记进 MultiblockWorldSavedData；
        // 走「方块变化 → checkPatternWithLock → onStructureFormed」成形时不会登记，
        // 之后结构里再被拆一格就不会有人通知本机 ⇒ 破了方块仍然显示成形。这里补上同一条登记。
        registerToUpstreamStructureMapping();
        slotPublisher.publishNacSlots();
        slotPublisher.recordFormedParts();
        structure.clearFormedError();
        requestHostResync();
        boolean linked = hostConnected();
        refreshHostSync();
        structure.logFormed(linked);
    }

    /** 把本机登记进上游的结构映射表（与 asyncCheckPattern 成形时做的是同一件事，幂等）。 */
    private void registerToUpstreamStructureMapping() {
        if (!(getLevel() instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
            return;
        }
        var state = getMultiblockState();
        if (state == null) {
            return;
        }
        var mwsd = com.gregtechceu.gtceu.api.pattern.MultiblockWorldSavedData.getOrCreate(serverLevel);
        mwsd.addMapping(state);
        mwsd.removeAsyncLogic(this);
    }

    @Override
    public void onStructureInvalid() {
        super.onStructureInvalid();
        slotPublisher.clearNacSlots(); // 清空后重置心跳基线，避免相同零件数掩盖应重发的清单。
        slotPublisher.resetPartCount();
        // 失效时由主机扫描收敛连接集合，避免多个模块回调相互覆盖连接状态。
        requestHostResync();
        refreshHostSync();
        structure.onStructureInvalid();
    }

    @Override
    public void onMachineRemoved() {
        hostLink.onMachineRemoved();
    }

    @Override
    public void saveCustomPersistedData(CompoundTag tag, boolean forDrop) {
        super.saveCustomPersistedData(tag, forDrop);
        vacuum.save(tag);
        tag.putDouble("GTLModularImpurity", impurity);
        tag.putInt("GTLModularFlushThreshold", autoFlushThreshold);
    }

    @Override
    public void loadCustomPersistedData(CompoundTag tag) {
        super.loadCustomPersistedData(tag);
        vacuum.load(tag);
        impurity = tag.getDouble("GTLModularImpurity");
        if (tag.contains("GTLModularFlushThreshold")) {
            autoFlushThreshold = Math.max(1, Math.min(100, tag.getInt("GTLModularFlushThreshold")));
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
    public NanochipAssemblyComplexMachine getHost() {
        return hostLink.getHost();
    }

    @Override
    public void setHost(NanochipAssemblyComplexMachine host) {
        hostLink.setHost(host);
    }

    @Override
    public Class<NanochipAssemblyComplexMachine> getHostType() {
        return NanochipAssemblyComplexMachine.class;
    }

    @Override
    public BlockPos[] getHostScanPositions() {
        return new BlockPos[0];
    }

    /** 附加配置页保留父类机器界面，避免覆盖模式和进度控件。 */
    @Override
    public void attachConfigurators(ConfiguratorPanel panel) {
        super.attachConfigurators(panel);
        display.attachConfigurators(panel);
        // 自动排空阈值页原本只挂在真空传送仓上，简单物流模式没有该仓就打不开。
        if (spec.features().board()) {
            panel.attachConfigurators(new NanochipVacuumHatchMachine.BoardProcessorConfigurator(this));
        }
    }

    @Override
    public void addDisplayText(List<Component> textList) {
        // 父类显示读取成形时的等级快照，主机供电变化后需用同步值更新以免长期显示旧等级。
        if (syncedDrawingFromHost) {
            this.tier = syncedHostTier;
            if (getHost() != null) {
                this.energyContainer = getEnergyContainer();
            }
        }
        super.addDisplayText(textList);
        display.addDisplayText(textList, spec);
    }

    static final class SplitterConfigurator implements IFancyConfigurator {
        private final NanochipSplitterPanel panel;

        SplitterConfigurator(NanochipModuleMachine machine) {
            this.panel = new NanochipSplitterPanel(machine);
        }

        @Override
        public Component getTitle() {
            return panel.getTitle();
        }

        @Override
        public IGuiTexture getIcon() {
            return panel.getIcon();
        }

        @Override
        public List<Component> getTooltips() {
            return panel.getTooltips();
        }

        @Override
        public Widget createConfigurator() {
            return panel.createConfigurator();
        }
    }
}
