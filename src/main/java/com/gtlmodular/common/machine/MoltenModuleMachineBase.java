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
        implements IModularMachineModule<MoltenModuleHostMachine, M>, IMachineLife {

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

    /** 模块是否已连接、成形且处于运行状态的主机（每 tick 判定，能量是否可得）。 */
    private boolean isHostReady() {
        MoltenModuleHostMachine h = getHost();
        return h != null && h.isFormed() && h.isWorkingEnabled();
    }

    /**
     * 生态圈同款 RecipeLogic：每 tick 判定 host 是否 ready，是则向主机请求本配方每 tick 能耗；
     * 主机扣电成功才推进进度；失败则等待（不中断、不丢配方），主机来电/换仓/重组后自动恢复。
     *
     * <p>子类可覆写 {@link #findAndHandleRecipe()}/{@link #onRecipeFinish()} 定制"跨配方合并"策略
     * （如星核剥离单元的"一次执行所有配方、每个配方同并行数"）。
     */
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
            // 主机线圈能耗减免：从 0K 起算每 240K -1%，50% 封顶（详见主机 discountForTemperature）
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

    /** 方块被破坏/移除：断开主机链接。 */
    @Override
    public void onMachineRemoved() {
        removeFromHost(getHost());
    }
}
