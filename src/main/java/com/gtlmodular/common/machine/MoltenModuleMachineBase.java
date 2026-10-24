package com.gtlmodular.common.machine;

import com.gregtechceu.gtceu.api.block.ICoilType;
import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.IMachineLife;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.misc.EnergyContainerList;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.utils.FormattingUtil;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;
import net.minecraft.ChatFormatting;
import com.gtlmodular.common.config.GTLModularConfig;
import com.gtlmodular.common.machine.fuel.DualFluidTax;
import com.gtlmodular.common.machine.independent.io.IndependentEnergyMultiplier;
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
import org.gtlcore.gtlcore.api.recipe.RecipeResult;
import org.gtlcore.gtlcore.common.machine.trait.MultipleRecipesLogic;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;

public abstract class MoltenModuleMachineBase<M extends MoltenModuleMachineBase<M>>
        extends CoilWorkableElectricMultipleRecipesMachine
        implements IModularMachineModule<MoltenModuleHostMachine, M>, IMachineLife, IndependentEnergyMultiplier {

    /** 自带同步字段必须挂到本类的 ManagedFieldHolder，客户端才能收到它们。 */
    public static final ManagedFieldHolder MANAGED_FIELD_HOLDER =
            new ManagedFieldHolder(MoltenModuleMachineBase.class,
                    WorkableMultiblockMachine.MANAGED_FIELD_HOLDER);

    /**
     * 机器 UI 右侧加「独立线程」页：线程条数（每台机器单独设）+「允许同一种配方占多条线程」开关。
     * 线程数存在独立线程控制器里、随机器存档保存；0 表示沿用机器自报的总线程数。
     */
    @Override
    public void attachConfigurators(com.gregtechceu.gtceu.api.gui.fancy.ConfiguratorPanel panel) {
        super.attachConfigurators(panel);
        try {
            panel.attachConfigurators(new com.gtlmodular.common.machine.independent.control.IndependentThreadsConfigurator(
                    this::independentThreads, this::setIndependentThreads,
                    this::allowsSameRecipeMultipleThreads, this::setAllowSameRecipeMultipleThreads));
        } catch (RuntimeException failure) {
            com.gtlmodular.common.diag.SwallowedErrors.report(
                    "MoltenModuleMachineBase#attachConfigurators.independentThreads", failure);
        }
    }

    /** 引擎给这台机器开几条独立线程（0 = 机器自报值）。 */
    public int independentThreads() {
        var controller = com.gtlmodular.common.machine.independent.control.IndependentLogicAccess
                .controller(getRecipeLogic());
        return controller == null ? 0 : controller.threadsOverride();
    }

    public void setIndependentThreads(int value) {
        var logic = getRecipeLogic();
        if (!(logic instanceof com.gtlmodular.common.machine.independent.control.IndependentLogicAccess access)) return;
        access.gtlmodular$controller(true).setThreadsOverride(value);
        markDirty();
    }

    /** 允许同一配方同时占多条独立线程（默认关闭）。 */
    public boolean allowsSameRecipeMultipleThreads() {
        var controller = com.gtlmodular.common.machine.independent.control.IndependentLogicAccess
                .controller(getRecipeLogic());
        return controller != null && controller.allowSameRecipeMultiple();
    }

    public void setAllowSameRecipeMultipleThreads(boolean value) {
        var logic = getRecipeLogic();
        if (!(logic instanceof com.gtlmodular.common.machine.independent.control.IndependentLogicAccess access)) return;
        access.gtlmodular$controller(true).setAllowSameRecipeMultiple(value);
        markDirty();
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    @DescSynced
    private long syncedRawEut;

    @DescSynced
    private long syncedPaidEut;

    public long getSyncedRawEut() {
        return syncedRawEut;
    }

    public long getSyncedPaidEut() {
        return syncedPaidEut;
    }

    @DescSynced
    private int lastTempFailRequired = Integer.MIN_VALUE;

    @DescSynced
    private int lastTempFailCoilTemp = Integer.MIN_VALUE;

    @DescSynced
    private int lastTempFailMax = Integer.MIN_VALUE;

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

    private static final int HOST_SCAN_R = 96;
    private static final int HOST_SCAN_VER = 64;

    private BlockPos hostPosition;
    private MoltenModuleHostMachine host;

    public MoltenModuleMachineBase(IMachineBlockEntity holder) {
        super(holder);
    }

    @Override
    protected RecipeLogic createRecipeLogic(Object... args) {
        return new MoltenModuleRecipeLogic(this, EBF_CHECK);
    }

    private boolean isHostReady() {
        MoltenModuleHostMachine h = getHost();
        return h != null && h.isFormed() && h.isWorkingEnabled();
    }

    /** 只有主机实际扣电成功才推进；供电失败保留配方等待恢复，避免凭预检余额推进。 */
    protected static class MoltenModuleRecipeLogic extends MultipleRecipesLogic {
        private final MoltenModuleMachineBase<?> module;

        protected MoltenModuleRecipeLogic(MoltenModuleMachineBase<?> module,
                                          BiPredicate<CompoundTag, IRecipeLogicMachine> dataCheck) {
            super(module, dataCheck);
            this.module = module;
        }

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
            MoltenModuleHostMachine host = module.getHost();
            long raw = RecipeHelper.getInputEUt(recipe);
            double discount = host == null ? 0.0D : host.getModuleEnergyDiscount();
            long eut = discount > 0.0D
                    ? Math.max(0L, Math.round(raw * (1.0D - discount)))
                    : raw;
            if (module.getLevel() != null && !module.getLevel().isClientSide) {
                module.syncedRawEut = raw;
                module.syncedPaidEut = eut;
            }
            boolean granted = host != null && host.requestEnergy(eut);
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
    }

    @Override
    public EnergyContainerList getEnergyContainer() {
        // EnergyContainerList 在构造时记录电压，每次重新包装才能看到主机供电变化。
        return new EnergyContainerList(List.of(new HostEnergyContainer(this)));
    }

    /** 基类界面读取缓存字段，需要随主机更新，否则更换能源仓后仍显示旧电压。 */
    @Override
    public void addDisplayText(List<Component> textList) {
        MoltenModuleHostMachine h = getHost();
        boolean hostLive = h != null && h.isFormed();
        EnergyContainerList live = hostLive
                ? getEnergyContainer()
                : null;
        this.energyContainer = live;
        this.tier = hostLive ? h.getTier() : 0;
        super.addDisplayText(textList);
        addHostPowerLine(textList);
    }

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
        if (getRecipeLogic() != null) {
            for (Component reason : getRecipeLogic().getFancyTooltip()) {
                textList.add(reason.copy().withStyle(ChatFormatting.RED));
            }
        }
    }

    /** 模块结构没有线圈，温度校验和降耗必须读取主机。 */
    @Override
    public ICoilType getCoilType() {
        ICoilType own = super.getCoilType();
        if (own != null) {
            return own;
        }
        MoltenModuleHostMachine h = getHost();
        return (h != null && h.isFormed()) ? h.getCoilType() : null;
    }

    public int getCoilTemperature() {
        ICoilType coil = getCoilType();
        return coil == null ? 0 : coil.getCoilTemperature();
    }

    /** 客户端结构缓存可能尚未更新，界面温度优先使用主机同步值。 */
    public int hostCoilTemperature() {
        MoltenModuleHostMachine h = getHost();
        if (h == null || !h.isFormed()) {
            return 0;
        }
        int synced = h.getSyncedCoilTemperature();
        return synced > 0 ? synced : h.getCoilTemperature();
    }

    public static final int MV_TIER = 2;

    public static final int TEMPERATURE_PER_VOLTAGE_TIER = 100;

    public int temperatureTierBonusLevels() {
        return Math.max(0, getTier() - MV_TIER);
    }

    public int temperatureTierBonus() {
        return temperatureTierBonusLevels() * TEMPERATURE_PER_VOLTAGE_TIER;
    }

    public int effectiveTemperature() {
        return hostCoilTemperature() + temperatureTierBonus();
    }

    public void onHostLinked() {}

    public static String trimNumber(double value) {
        if (!Double.isInfinite(value) && value == Math.floor(value)) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }

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
            return false; // 禁止旁路充能，能量只能由主机实际供给。
        }

        @Override
        public long acceptEnergyFromNetwork(Direction side, long amount, long voltage) {
            return 0;
        }

        @Override
        public long changeEnergy(long amount) {
            if (amount < 0) {
                long need = -amount;
                MoltenModuleHostMachine h = h();
                if (h != null && h.consumeEU(need)) {
                    return -need;
                }
                return 0;
            }
            return 0;
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
    public double independentEnergyMultiplier() {
        MoltenModuleHostMachine h = getHost();
        double discount = h == null ? 0.0D : h.getModuleEnergyDiscount();
        return Math.max(0.0D, 1.0D - discount);
    }

    @Override
    public void setHost(MoltenModuleHostMachine host) {
        this.host = host;
    }

    @Override
    public Class<MoltenModuleHostMachine> getHostType() {
        return MoltenModuleHostMachine.class;
    }

    @Override
    public BlockPos[] getHostScanPositions() {
        return new BlockPos[0];
    }

    /** 按已加载区块枚举方块实体，避免大范围逐格查表和强制加载区块。 */
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
        removeFromHost(getHost());
    }

    @Override
    public void onMachineRemoved() {
        removeFromHost(getHost());
    }
}
