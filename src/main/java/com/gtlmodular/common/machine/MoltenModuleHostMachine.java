package com.gtlmodular.common.machine;

import com.gregtechceu.gtceu.api.block.ICoilType;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.IMachineLife;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IDisplayUIMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.CoilWorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.misc.EnergyContainerList;
import com.gregtechceu.gtceu.utils.FormattingUtil;
import com.gtlmodular.GTLModular;
import com.gtlmodular.api.module.ModuleKind;
import com.gtlmodular.common.data.host.ModulePreviewSlots;
import com.gtlmodular.common.data.host.MoltenModuleHostPattern;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.registries.ForgeRegistries;
import org.gtlcore.gtlcore.api.machine.multiblock.IModularMachineHost;
import org.gtlcore.gtlcore.api.machine.multiblock.IModularMachineModule;
import org.gtlcore.gtlcore.utils.datastructure.ModuleRenderInfo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * M5 主控制器：地心涌动能量转换枢纽（亚空间式改造 + 生态圈式中央供电）。
 *
 * <p>判定盒：主机与生态圈一致以<b>整栋「地心涌动能量转换站」大楼</b>为成形结构
 * （97×48×99，模块机区域已在生成时挖空为 '.'，主机成形不依赖三模块单元），
 * 'S' 唯一 = 主机机器。本机实现 gtlcore {@link IModularMachineHost}。
 *
 * <p><b>UI/判定同步生态圈（Biosphere III）：</b>
 * <ul>
 *   <li><b>基类</b>：继承 {@link CoilWorkableElectricMultiblockMachine}，获得 GT 完整机器界面
 *       （能量/电压/状态行等）并支持读取大楼线圈；注册表补占位配方表使 {@code recipeTypes} 非空。</li>
 *   <li><b>可更换线圈</b>：大楼线圈格用 {@code Predicates.heatingCoils()} 判定，玩家可整楼替换线圈，
 *       主机 {@link #getCoilTemperature()} 实时反映当前线圈温度（模块并行加成读这里）。</li>
 *   <li><b>不显示「配方失败原因」</b>：生态圈用自定义 {@link RecipeLogic}（{@code serverTick} 不搜
 *       本机配方）避免 RecipeLogic 搜不到配方而弹失败原因。本机照做 —— 主机永不加工，只蓄电挂载。</li>
 *   <li><b>模块判定覆盖整栋大楼</b>：三个模块机器贴在大楼远墙（水平常 &gt;32 格），因此把互扫
 *       半径扩到覆盖 115×56×110 楼体（水平 ±96、垂直 ±64）：主机成形时全量扫一次，之后界面刷新
 *       做小范围快速补连（重载后机器会重新校验结构并自动重连，界面补扫仅兜底）。</li>
 * </ul>
 */
public class MoltenModuleHostMachine extends CoilWorkableElectricMultiblockMachine
        implements IDisplayUIMachine, IModularMachineHost<MoltenModuleHostMachine>, IMachineLife {

    /** 自带同步字段（线圈温度/线圈格数）必须挂到自己的 ManagedFieldHolder 上才会同步到客户端。 */
    public static final ManagedFieldHolder MANAGED_FIELD_HOLDER =
            new ManagedFieldHolder(MoltenModuleHostMachine.class,
                    WorkableMultiblockMachine.MANAGED_FIELD_HOLDER);

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    /**
     * 线圈温度（K）与线圈格总数：同步到客户端。
     * gtceu 的机器界面文字是<b>客户端</b>拼的（{@code IDisplayUIMachine.createUIWidget} 里按 isClientSide 分支），
     * 所以界面要显示的数必须同步过来，否则客户端一直读旧值/0，看起来就像"减免没生效"。
     */
    @DescSynced
    private int syncedCoilTemperature;

    @DescSynced
    private int syncedCoilCellCount;

    /** 线圈的原始名（ICoilType.getName()：KubeJS 线圈是方块路径，gtceu 线圈是材质名），客户端拿它反查中文名。 */
    @DescSynced
    private String syncedCoilName = "";

    /** 混放时的「线圈格构成」文案（服务端算好，客户端直接显示，避免两边口径不一致）。 */
    @DescSynced
    private String syncedCoilBreakdown = "";

    public int getSyncedCoilTemperature() {
        return syncedCoilTemperature;
    }

    public int getSyncedCoilCellCount() {
        return syncedCoilCellCount;
    }

    public String getSyncedCoilName() {
        return syncedCoilName;
    }

    public String getSyncedCoilBreakdown() {
        return syncedCoilBreakdown;
    }

    /** 大楼尺寸（投影实测 115×56×110）：全量互扫需覆盖整个楼体。 */
    private static final int FULL_R = 96;    // 水平半径（覆盖 192 宽）
    private static final int FULL_VER = 64;  // 垂直半径（覆盖 128 高）

    /** 界面刷新时的快速补连半径（避免每 3 秒全楼扫一次）。 */
    private static final int QUICK_R = 48;
    private static final int QUICK_VER = 32;

    /** 已连接的模块集合（服务器线程维护）。 */
    private final Set<IModularMachineModule<MoltenModuleHostMachine, ?>> modules = new LinkedHashSet<>();

    /** 上次界面补扫游戏刻。 */
    private long lastRescanTick = -1;

    public MoltenModuleHostMachine(IMachineBlockEntity holder) {
        super(holder);
    }

    // ---------- 自定义 RecipeLogic：主机不搜配方，避免「配方失败原因」 ----------

    /**
     * 生态圈同款：复写 serverTick 不再调用父类搜索逻辑。
     *
     * <p>状态这样给：有模块在加工 → {@code WORKING}，否则 {@code IDLE}（软锤停机则 {@code SUSPEND}）。
     * 之所以必须有 {@code WORKING}：gtceu 的 {@code WorkableMultiblockMachine.notifyStatusChanged} 只在
     * 状态切换进/出 {@code WORKING} 时才调用 {@code updateActiveBlocks}，从而把结构里的
     * {@code ActiveBlock}（大楼线圈、外壳）点亮/熄灭 —— 这就是"工作时线圈高亮"的来源。
     */
    private static final class HostRecipeLogic extends RecipeLogic {
        private final MoltenModuleHostMachine host;

        HostRecipeLogic(MoltenModuleHostMachine host) {
            super(host);
            this.host = host;
        }

        @Override
        public void serverTick() {
            host.tickDrawWindow();
            host.tickCoilRefresh();
            // 不做任何配方搜索/失败记录；仅同步状态让标准 UI 行与线圈活跃材质照常工作。
            RecipeLogic.Status target;
            if (!isWorkingEnabled()) {
                target = RecipeLogic.Status.SUSPEND;
            } else {
                target = host.hasActiveModules() ? RecipeLogic.Status.WORKING : RecipeLogic.Status.IDLE;
            }
            setStatus(target);
        }

        @Override
        public void updateSound() {
            // 主机无加工声音
        }
    }

    @Override
    protected RecipeLogic createRecipeLogic(Object... args) {
        return new HostRecipeLogic(this);
    }

    // ---------- 中央供电（生态圈式）----------

    private EnergyContainerList energy() {
        return isFormed() ? getEnergyContainer() : null;
    }

    /** 主机当前蓄电（EU）。 */
    public long getTotalStoredEU() {
        EnergyContainerList e = energy();
        return e == null ? 0 : e.getEnergyStored();
    }

    /** 主机最大蓄电（EU）。 */
    public long getTotalCapacityEU() {
        EnergyContainerList e = energy();
        return e == null ? 0 : e.getEnergyCapacity();
    }

    /** 主机能源仓输入电压。 */
    public long getMaxInputVoltage() {
        EnergyContainerList e = energy();
        return e == null ? 0 : e.getInputVoltage();
    }

    /** 近 1 秒模块实付电（EU/t 平均）—— 从本机真正扣掉的电，用来确认减免是否真的生效。 */
    @DescSynced
    private long syncedModuleDrawPerTick;

    private long drawAccumulator;
    private long drawWindowSecond = Long.MIN_VALUE;

    public long getModuleDrawPerTick() {
        return syncedModuleDrawPerTick;
    }

    /** 每个游戏刻调用一次：跨秒时把上一秒累计的扣电换算成 EU/t 平均值。 */
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

    /** 从主机扣电；不足返回 false。 */
    public boolean consumeEU(long amount) {
        if (amount <= 0) {
            return true;
        }
        EnergyContainerList e = energy();
        if (e == null || e.getEnergyStored() < amount) {
            return false;
        }
        e.changeEnergy(-amount);
        return true;
    }

    /** 生态圈式取电入口：模块每工作 tick 调用，主机从自身能量仓扣电（这里统计实际扣电量）。 */
    public boolean requestEnergy(long amount) {
        if (amount <= 0) {
            return true;
        }
        Level lv = getLevel();
        if (lv == null || lv.isClientSide || !isFormed() || !isWorkingEnabled()) {
            return false;
        }
        boolean ok = consumeEU(amount);
        if (ok && !lv.isClientSide) {
            drawAccumulator += amount;
        }
        return ok;
    }

    // ---------- 线圈（模块并行加成的来源）----------

    /** 判定为「线圈格」的世界坐标（成形时从判定上下文取出并持久化，客户端靠 NBT 拿到同一份）。 */
    private final List<BlockPos> coilCells = new ArrayList<>();

    /** 缓存的实时线圈类型与其格子数统计。 */
    private ICoilType liveCoilType;
    private long liveCoilTick = -1L;
    private String coilBreakdown = "";

    /** 成形时取走线圈格坐标。 */
    private void captureCoilCells() {
        coilCells.clear();
        Object o = getMultiblockState().getMatchContext()
                .get(MoltenModuleHostPattern.COIL_CELLS_KEY);
        if (o instanceof List<?> list) {
            for (Object p : list) {
                if (p instanceof BlockPos bp) {
                    coilCells.add(bp);
                }
            }
        }
        coilCells.sort(java.util.Comparator.comparingLong(BlockPos::asLong));
        liveCoilType = null;
        liveCoilTick = -1L;
    }

    /**
     * <b>实时</b>读线圈：直接看"判定为线圈格"的那些坐标上现在放的方块，取<b>格子数最多</b>的线圈类型
     * （并列时取更热的）。
     *
     * <p>为什么不用原版 {@code heatingCoils()} 存下来的那个值：那是<b>成形那一刻</b>的快照，
     * 而且原版要求整楼同一种线圈（混放直接报错不成形），玩家换线圈时很容易拿到旧温度。
     * 这里改成运行时读世界，换一格就跟着变，也不需要结构重判；整楼单独一种时结果与原版完全一致。
     */
    @Override
    public ICoilType getCoilType() {
        Level lv = getLevel();
        if (lv == null) {
            return super.getCoilType();
        }
        if (coilCells.isEmpty()) {
            if (!isFormed()) {
                return super.getCoilType();
            }
            // 兜底：读档/重载后可能没走到 onStructureFormed，从判定上下文再抓一次（抓到就不再来）
            captureCoilCells();
            if (coilCells.isEmpty()) {
                return super.getCoilType();
            }
        }
        long t = lv.getGameTime();
        if (liveCoilType != null && liveCoilTick >= 0 && t - liveCoilTick < 20L) {
            return liveCoilType;
        }
        liveCoilTick = t;
        Map<ICoilType, Integer> counts = new LinkedHashMap<>();
        for (BlockPos p : coilCells) {
            if (!lv.isLoaded(p)) {
                continue;
            }
            ICoilType c = MoltenModuleHostPattern.coilOf(lv.getBlockState(p));
            if (c != null) {
                counts.merge(c, 1, Integer::sum);
            }
        }
        ICoilType best = null;
        int bestCount = -1;
        for (Map.Entry<ICoilType, Integer> e : counts.entrySet()) {
            int n = e.getValue();
            if (n > bestCount || (n == bestCount && best != null
                    && e.getKey().getCoilTemperature() > best.getCoilTemperature())) {
                best = e.getKey();
                bestCount = n;
            }
        }
        if (best != null) {
            liveCoilType = best;
            StringBuilder sb = new StringBuilder();
            int shown = 0;
            for (Map.Entry<ICoilType, Integer> e : counts.entrySet()) {
                if (shown++ >= 3) {
                    sb.append("｜…");
                    break;
                }
                if (sb.length() > 0) {
                    sb.append("｜");
                }
                sb.append(coilLocalizedName(e.getKey())).append(' ').append(e.getValue()).append(" 格");
            }
            coilBreakdown = sb.toString();
            if (!lv.isClientSide) {
                syncedCoilTemperature = best.getCoilTemperature();
                syncedCoilCellCount = coilCells.size();
                syncedCoilName = best.getName() == null ? "" : best.getName();
                syncedCoilBreakdown = coilBreakdown;
            }
        }
        return liveCoilType != null ? liveCoilType : super.getCoilType();
    }

    /** 线圈格总数（换线圈时需要替换的格子数）。 */
    public int getCoilCellCount() {
        return coilCells.size();
    }

    // ---------- ★ 2026-09-21：换线圈自动刷新（不用拆主机重放） ----------

    /** 线圈格为空时的"补抓"节拍（tick）与补抓失败的告警哨兵。 */
    private long lastCoilRecoveryTick = -1L;
    private boolean coilCellsMissingWarned;

    /**
     * <b>2026-09-21 队长</b>：「<b>现在版本地心涌动能量转换枢纽，更换线圈以后，需要手动将主机拆了重放
     * 才能更新，不然还是默认之前的线圈</b>」—— 本方法给出<b>固定节拍的线圈重读</b>。
     *
     * <h4>为什么必须在主机自己的 serverTick 里刷（而不是像原来那样等着别人来问）</h4>
     * 原来的实时读只写在 {@link #getCoilType()} 里，而服务端调用它的只有<b>模块的配方逻辑</b>
     * （{@code MoltenModuleMachineBase.handleRecipeWorking} → {@code getModuleEnergyDiscount()}、
     * 以及并行/EBF 温度校验）。⇒ 没模块在跑配方时，一次都不会刷：界面显示的是同步字段里
     * 上一次推上来的旧线圈，模块加成也停在旧值上。现在挂到主机自己的每 tick 节拍（本类
     * {@code HostRecipeLogic.serverTick} 已经在每 tick 调 {@code tickDrawWindow()}），
     * 换完线圈 ≤1 秒内自动更新。
     *
     * <h4>为什么换线圈不会触发结构重判（GTCEu 侧的既有语义，不是我们的漏判）</h4>
     * 线圈格用 {@code <heating_coils>} 判定（{@code MoltenModuleHostPattern.modularHeatingCoils()}），
     * 而 {@code CoilBlock extends ActiveBlock}（javap 实证）——
     * GTCEu 的 {@code MultiblockState.onBlockStateChanged} 对"成形中机器 + 变更后方块是
     * {@code ActiveBlock} + 该坐标在 {@code vaBlocks} 里"的情形**直接 return**，
     * 不重判结构 ⇒ {@code onStructureFormed()} 不会再跑，GTCEu 基类那个
     * "成形那一刻的快照"（{@code CoilWorkableElectricMultiblockMachine.coilType}）也就一直是旧值。
     * 本机本来就是靠"实时读那 1715 个线圈格"绕开这个快照的，本轮只是把它的<b>节拍</b>补上。
     *
     * <h4>开销（守住 0.1.40 的性能红线）</h4>
     * {@link #getCoilType()} 内部有 20 tick 缓存 ⇒ 稳态就是 <b>1 次/秒 × 1715 格</b>（{@code getBlockState}），
     * 不碰 97×48×99 的全量结构匹配、不挂任何方块变更事件、不进每 tick 路径。
     * 线圈格列表为空时只尝试"从判定上下文补抓"（纯内存查表，20 tick 一次），
     * 连续抓不到就只打一条 WARN（见 {@link #warnCoilCellsMissingOnce()}），不再刷日志。
     */
    private void tickCoilRefresh() {
        Level lv = getLevel();
        if (lv == null || lv.isClientSide || !isFormed()) {
            return;
        }
        if (coilCells.isEmpty()) {
            long now = lv.getGameTime();
            if (lastCoilRecoveryTick < 0L || now - lastCoilRecoveryTick >= 20L) {
                lastCoilRecoveryTick = now;
                captureCoilCells();
                if (coilCells.isEmpty()) {
                    warnCoilCellsMissingOnce();
                }
            }
            return;
        }
        getCoilType();   // 内部 20 tick 缓存：真正读格 1 次/秒，并把 synced* 推给客户端
    }

    /** 线圈格抓不到时只告警一次（限流哨兵；这条一旦出现，界面上的「线圈格 N」必然显示 0）。 */
    private void warnCoilCellsMissingOnce() {
        if (coilCellsMissingWarned) {
            return;
        }
        coilCellsMissingWarned = true;
        GTLModular.LOGGER.warn("[gtlmodular] 地心涌动能量转换枢纽 @{} 成形但拿不到线圈格坐标"
                + "（判定上下文没有 {} 键）⇒ 线圈实时读失效，只能退回基类的成形快照。"
                + "请把这一行连同界面「加热线圈」行一起报给开发；换线圈将不会自动生效。",
                getPos(), MoltenModuleHostPattern.COIL_CELLS_KEY);
    }

    /** 各线圈类型占用的格子数（混放时用于排查"换错地方了"）。 */
    public String getCoilBreakdown() {
        return coilBreakdown;
    }

    @Override
    public void saveCustomPersistedData(CompoundTag tag, boolean forDrop) {
        super.saveCustomPersistedData(tag, forDrop);
        long[] packed = new long[coilCells.size()];
        for (int i = 0; i < packed.length; i++) {
            packed[i] = coilCells.get(i).asLong();
        }
        tag.putLongArray("GTLModularCoilCells", packed);
    }

    @Override
    public void loadCustomPersistedData(CompoundTag tag) {
        super.loadCustomPersistedData(tag);
        coilCells.clear();
        for (long l : tag.getLongArray("GTLModularCoilCells")) {
            coilCells.add(BlockPos.of(l));
        }
        liveCoilType = null;
        liveCoilTick = -1L;
    }

    /**
     * 主机当前线圈温度（K）。大楼线圈格由 {@code modularHeatingCoils()} 判定，实时读取，
     * 玩家可整楼替换线圈；星核剥离单元的并行加成按此温度计算（每高出基线 1200K → 并行 +300）。
     */
    public int getCoilTemperature() {
        ICoilType coil = getCoilType();
        return coil == null ? 0 : coil.getCoilTemperature();
    }

    // ---------- 主机线圈对模块的能耗减免 ----------

    /** 能耗减免起算温度：5400K（高速钢-G 线圈那档）以下不减免。 */
    public static final int DISCOUNT_BASE_TEMP = 5400;
    /** 起算温度之上，每 240K → 1 个百分点。 */
    public static final int DISCOUNT_STEP_TEMP = 240;
    /** 每档 1%（1 个百分点）。 */
    public static final double DISCOUNT_STEP = 0.01D;
    /** 减免上限 50%：17400K 起封顶，更高的线圈不再增加。 */
    public static final double DISCOUNT_CAP = 0.50D;

    /**
     * 由线圈温度算减免系数（0 = 不减免，0.5 = 减免 50% 上限）：
     * <pre>
     *   减免 = min(50%, (温度 − 5400K) / 240 × 1%)
     *   5400K 及以下 → 0%      7200K(硅岩) → 7.5%     10800K(三钛) → 22.5%
     *   17400K 起顶到 50%（超能硅岩-塔兰及以上 / 无尽 / 海珀珍 / 永恒 都是 50%）
     * </pre>
     * 纯函数，客户端 UI 也能按同步过来的温度自己算。
     */
    public static double discountForTemperature(int temp) {
        if (temp <= DISCOUNT_BASE_TEMP) {
            return 0.0D;
        }
        return Math.min(DISCOUNT_CAP,
                (temp - DISCOUNT_BASE_TEMP) / (double) DISCOUNT_STEP_TEMP * DISCOUNT_STEP);
    }

    /** 同上，取百分比整数（界面显示用）。 */
    public static int discountPercentForTemperature(int temp) {
        return (int) Math.round(discountForTemperature(temp) * 100.0D);
    }

    /** 三个模块共用的能耗减免系数（服务端用实时线圈；客户端 UI 用同步温度算）。 */
    public double getModuleEnergyDiscount() {
        return discountForTemperature(getCoilTemperature());
    }

    // ---------- IModularMachineHost ----------

    @Override
    public Set<IModularMachineModule<MoltenModuleHostMachine, ?>> getModuleSet() {
        return modules;
    }

    /**
     * 是否有已成形模块正在加工 —— 决定本机状态是否置 {@code WORKING}，
     * 从而让大楼线圈/外壳的活跃材质（ActiveBlock）随模块加工亮起。
     */
    public boolean hasActiveModules() {
        for (IModularMachineModule<MoltenModuleHostMachine, ?> m : modules) {
            if (!m.isFormed()) {
                continue;
            }
            if (m instanceof IRecipeLogicMachine rlm) {
                RecipeLogic logic = rlm.getRecipeLogic();
                if (logic != null && logic.isWorking()) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 半径搜索覆盖（见 {@link #scanAndConnectModules()}），无需静态点位表。 */
    @Override
    public BlockPos[] getModuleScanPositions() {
        return new BlockPos[0];
    }

    /**
     * 在主机周围 {@code r}/{@code ver} 范围内挂载已成形模块。
     *
     * <p><b>性能（2026-09-13 优化）</b>：旧实现按坐标三重循环遍历
     * {@code (2r+1)²(2ver+1)} 个点（r=96/ver=64 → <b>480 万</b> 次），且每个点都调一次
     * {@link #exceedsModuleLimit()}（gtlcore 默认实现要走模块集合/流判定）、再走
     * {@code isLoaded + MetaMachine.getMachine} 的区块表查表 —— spark 采样里本模组
     * 占服务器线程总时间的 3.91%（其中 {@code ObjectOpenHashSet$SetIterator.next} 单帧 1.41%）。
     * 现改为<b>按区块枚举方块实体</b>：只查箱内 14×14 个区块，每块的 BE 各访问一次，
     * 由 BE 自身坐标做箱内过滤。判定顺序、有效性判定（{@code isValidModule}）与连接行为完全不变，
     * 仅把 {@code exceedsModuleLimit()} 从 480 万次降到「入口 1 次 + 每次成功连接后 1 次」
     * （上限只能在连接成功后才变化，语义等价）。
     */
    private void scanModules(int r, int ver) {
        Level lv = getLevel();
        if (!(lv instanceof ServerLevel serverLevel)) {
            return;
        }
        if (exceedsModuleLimit()) {
            return;
        }
        BlockPos c = getPos();
        int cx = c.getX(), cy = c.getY(), cz = c.getZ();
        int x0 = cx - r, x1 = cx + r;
        int y0 = cy - ver, y1 = cy + ver;
        int z0 = cz - r, z1 = cz + r;
        for (int chx = x0 >> 4; chx <= x1 >> 4; chx++) {
            for (int chz = z0 >> 4; chz <= z1 >> 4; chz++) {
                LevelChunk chunk = serverLevel.getChunkSource().getChunkNow(chx, chz);
                if (chunk == null) {
                    continue;
                }
                // 复制一份再遍历：连接动作可能触碰区块实体表
                for (BlockEntity be : new ArrayList<>(chunk.getBlockEntities().values())) {
                    BlockPos p = be.getBlockPos();
                    if (p.getX() < x0 || p.getX() > x1 || p.getY() < y0 || p.getY() > y1
                            || p.getZ() < z0 || p.getZ() > z1) {
                        continue;
                    }
                    MetaMachine m = MetaMachine.getMachine(lv, p);
                    if (m == null || m == this || !(m instanceof IModularMachineModule<?, ?> im)) {
                        continue;
                    }
                    if (!isValidModule(m)) {
                        continue;
                    }
                    @SuppressWarnings("unchecked")
                    IModularMachineModule<MoltenModuleHostMachine, ?> mod =
                            (IModularMachineModule<MoltenModuleHostMachine, ?>) im;
                    mod.connectToHost(this);
                    if (mod instanceof MoltenModuleMachineBase<?> base) {
                        base.onHostLinked();
                    }
                    if (exceedsModuleLimit()) {
                        return;
                    }
                }
            }
        }
    }

    @Override
    public void scanAndConnectModules() {
        // 全楼一次全量挂载（成形时 / 手动触发）
        scanModules(FULL_R, FULL_VER);
    }

    /** 界面刷新调用的快速补连：只在小范围找新模块，避免每 3 秒全楼扫一次。 */
    private void quickScan() {
        scanModules(QUICK_R, QUICK_VER);
    }

    /** 模块上限：超额后不再接受新连接（避免 UI/场景过度膨胀）。 */
    @Override
    public int getMaxModuleCount() {
        return 12;
    }

    /**
     * 「点击显示机器模块」（{@code M:OFF→M:ON}）预览数据。
     *
     * <p><b>为什么必须有这个方法</b>：gtlcore 的 {@link IModularMachineHost#getModulesForRendering()}
     * 是默认方法，默认实现 <b>直接 {@code return Collections.emptyList()}</b>
     * （字节码：{@code _enh2_extract/gtlcore_module_api.txt:1419-1422}），而预览侧
     * {@code PatternPreviewWidget$MBPattern.initializeModules} 只认这一个数据源
     * （同文件 {@code :3230-3246}：拿到空表立刻 {@code return List.of()}）
     * ⇒ <b>不覆写 = 点了按钮什么都没发生</b>。这就是"我们造的两台大机器点了不显示模块"的根因。
     *
     * <p><b>数据从哪来</b>：三条 {@link ModuleRenderInfo} 由
     * {@link ModulePreviewSlots#moltenHostModules} 产出，内容 = ｛模块定义 + 该模块<b>相对主机的偏移</b> + 四个朝向参数｝。
     * <b>★ 2026-10 起坐标源 = 实机 F3 实测</b>（主机 {@code gtlmodular:molten_module_host} 世界坐标 (-218,66,375)，
     * 三台模块全 {@code facing=south}；原"现役投影 {@code 地心涌动能量转换站.litematic}
     * （{@code gtl-dev/api-dump/dump_full.txt}）解码 + 现役 {@code HostPatternLayers} 独立核对"的投影口径，
     * 因推算值系统性偏 (0,+6,+4) 已作废）。
     * 三条偏移的逐条依据与新旧对照写在 {@code ModulePreviewSlots.MOLTEN_MODULE_CONTROLLERS} 的注释里。
     * <b>没有手抄/编造坐标。</b>
     *
     * <p><b>性能</b>：本方法是"预览每次开界面/每次建 MBPattern"都会调的入口，所以它自己
     * <b>不重算</b> —— 只做一次坐标相等比较 + 返回缓存引用（缓存 = 每台宿主至多一份不可变列表，
     * 键 = 本机坐标；控制器被拆/换位置时键变化即整体替换，不累积）。派生与日志都在
     * {@link ModulePreviewSlots} 内，且全程 {@code try/catch(Throwable)}。
     *
     * <p><b>失败姿态</b>：拿不到模块定义 / 图案异常 / 任何意外 ⇒ 返回<b>空表</b>
     * （等价于 gtlcore 的默认行为，绝不外抛打断预览或服务端）。
     *
     * <p><b>红线</b>：本方法<b>只读</b>——不碰 {@code modules} 集合、不碰 AE 刷新、不碰线圈字段、
     * 不动结构检查节流；与"模块连接"机制完全无关（那是 {@code scanModules} 的事）。
     */
    @Override
    public List<ModuleRenderInfo> getModulesForRendering() {
        try {
            return ModulePreviewSlots.moltenHostModules(getPos(), moduleRenderCache);
        } catch (Throwable t) {
            // 渲染/预览路径绝不外抛
            GTLModular.LOGGER.debug("[gtlmodular] 模块预览: 地心枢纽 getModulesForRendering 异常，返回空表: {}",
                    t.toString());
            return List.of();
        }
    }

    /** {@link #getModulesForRendering()} 的有界缓存（每台宿主至多一份不可变列表）。 */
    private final ModulePreviewSlots.Cache moduleRenderCache = new ModulePreviewSlots.Cache();

    // ---------- 生命周期 ----------

    @Override
    public void onStructureFormed() {
        super.onStructureFormed();
        captureCoilCells();
        scanAndConnectModules();
    }

    @Override
    public void onStructureInvalid() {
        super.onStructureInvalid();
        safeClearModules();
    }

    /** 主机方块被移除：断开全部模块链接。 */
    @Override
    public void onMachineRemoved() {
        safeClearModules();
    }

    // ---------- 显示（基类已画 GT 标准行，这里只补模块/蓄电信息） ----------

    /** 线圈注册路径 → 方块（惰性扫描一次；兼容 gtceu / kubejs 等不同命名空间）。 */
    private static final Map<String, Block> COIL_BLOCKS_BY_PATH = new LinkedHashMap<>();
    private static boolean coilBlocksScanned;

    /**
     * 线圈的本地化显示名（任一线圈方块都显示中文名）：
     * <ul>
     *   <li>{@code ICoilType.getName()} 对 KubeJS 线圈给的是方块注册路径（{@code infinity_coil_block}），
     *       对 gtceu 原版线圈给的是材质名（{@code cupronickel}）；</li>
     *   <li>因此索引同时按「完整路径」和「去掉 _coil_block 的短名」登记，两种写法都能反查到方块，
     *       取方块的本地化名（如「无尽线圈方块」「白铜线圈块」）；</li>
     *   <li>查不到方块时退回材质本地化名，最后才退回原始字符串。</li>
     * </ul>
     */
    private static Component coilLocalizedName(ICoilType coil) {
        if (coil == null) {
            return Component.literal("无");
        }
        return coilLocalizedName(coil.getName());
    }

    /** 按 {@code ICoilType.getName()} 的原始字符串反查方块本地化名（客户端只有同步过来的字符串时走这条）。 */
    private static Component coilLocalizedName(String raw) {
        if (raw == null || raw.isEmpty()) {
            return Component.literal("无");
        }
        int colon = raw.indexOf(':');
        String key = colon >= 0 ? raw.substring(colon + 1) : raw;
        ensureCoilBlockIndex();
        Block block = COIL_BLOCKS_BY_PATH.get(key);
        if (block == null) {
            block = COIL_BLOCKS_BY_PATH.get(key + "_coil_block");
        }
        if (block != null) {
            return block.getName();
        }
        return Component.literal(key);
    }

    /**
     * 惰性扫描一次方块注册表，建立「线圈方块路径 / 短名 → 方块」索引。
     *
     * <p><b>线程安全</b>：这个索引会同时被服务端线程和客户端渲染线程用到（界面/物品提示都会读）。
     * 原来是无同步的惰性初始化，而且<b>先把 {@code coilBlocksScanned} 置位再填充</b> —— 另一个线程
     * 可能在这中间读到"已完成"而看到半填充的 LinkedHashMap（并发读写非并发容器，理论上会损坏链表）。
     * 现在给方法加锁，并且<b>填充完成之后</b>再置位标志。
     */
    private static synchronized void ensureCoilBlockIndex() {
        if (coilBlocksScanned) {
            return;
        }
        for (ResourceLocation id : ForgeRegistries.BLOCKS.getKeys()) {
            String path = id.getPath();
            if (!path.contains("coil")) {
                continue;
            }
            Block block = ForgeRegistries.BLOCKS.getValue(id);
            if (block == null) {
                continue;
            }
            COIL_BLOCKS_BY_PATH.putIfAbsent(path, block);
            if (path.endsWith("_coil_block")) {
                COIL_BLOCKS_BY_PATH.putIfAbsent(path.substring(0, path.length() - "_coil_block".length()), block);
            }
        }
        coilBlocksScanned = true;
    }

    @Override
    public void addDisplayText(List<Component> textList) {
        super.addDisplayText(textList);
        // 基类会把注册的占位配方表名（dummy/假人）拼成「机器模式」行，只把该行替换成站名文案。
        for (int i = 0; i < textList.size(); i++) {
            Component c = textList.get(i);
            if (c.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tc
                    && "gtceu.gui.machinemode".equals(tc.getKey())) {
                textList.set(i, Component.translatable("gtceu.gui.machinemode",
                        Component.literal("地心涌动能量转换站")).withStyle(ChatFormatting.AQUA));
            }
        }
        // 节流小范围补连：GUI/HUD 每次刷新都会走到这里；世界重载后首次打开界面自动补连
        if (isFormed() && getLevel() instanceof ServerLevel) {
            long t = getLevel().getGameTime();
            if (lastRescanTick < 0 || t - lastRescanTick >= 60) {
                lastRescanTick = t;
                quickScan();
            }
        }
        if (!isFormed()) {
            return;
        }
        // 蓄电横幅（基类能量行之上叠加余电/容量，便于确认中央供电是否进电）
        textList.add(Component.literal("中枢蓄电: ")
                .append(Component.literal(getTotalStoredEU() + " / " + getTotalCapacityEU() + " EU")
                        .withStyle(ChatFormatting.AQUA))
                .withStyle(ChatFormatting.GRAY));
        // 真实扣电读数（近 1 秒平均）：验证模块减免/供电是否真的生效，看这个而不是看蓄电（蓄电常满看不出来）
        textList.add(Component.literal("模块实付: ")
                .append(Component.literal(FormattingUtil.formatNumbers(syncedModuleDrawPerTick) + " EU/t")
                        .withStyle(ChatFormatting.AQUA))
                .append(Component.literal("（近 1 秒实际从本机扣掉的电）").withStyle(ChatFormatting.GRAY))
                .withStyle(ChatFormatting.GRAY));
        // 线圈行：模块并行加成按主机线圈温度计算；并显示由此得到的模块能耗减免
        // 名字/温度/格数一律用同步值（客户端自己读的那份线圈数据可能还是旧结构判定留下的）
        Component coilName = syncedCoilName != null && !syncedCoilName.isEmpty()
                ? coilLocalizedName(syncedCoilName)
                : coilLocalizedName(getCoilType());
        int coilTemp = syncedCoilTemperature > 0 ? syncedCoilTemperature : getCoilTemperature();
        int cellCount = syncedCoilCellCount > 0 ? syncedCoilCellCount : getCoilCellCount();
        int discountPct = discountPercentForTemperature(coilTemp);
        textList.add(Component.literal("加热线圈: ")
                .append(coilName.copy().withStyle(ChatFormatting.GOLD))
                .append(Component.literal(" · " + coilTemp + " K")
                        .withStyle(ChatFormatting.GRAY))
                .append(Component.literal(cellCount > 0
                        ? " · 线圈格 " + cellCount
                        : "").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(" · 模块能耗 -" + discountPct + "%")
                        .withStyle(ChatFormatting.AQUA))
                .withStyle(ChatFormatting.GRAY));
        // 混放时把各线圈占的格子数摊开（排查"换了线圈但温度没变"：说明换的不是判定格）
        if (cellCount > 0 && syncedCoilBreakdown != null && syncedCoilBreakdown.indexOf('｜') >= 0) {
            textList.add(Component.literal("  · 线圈格构成: ")
                    .append(Component.literal(syncedCoilBreakdown).withStyle(ChatFormatting.AQUA))
                    .withStyle(ChatFormatting.GRAY));
        }
        int blast = 0, strip = 0, ore = 0;
        for (IModularMachineModule<MoltenModuleHostMachine, ?> m : getModules()) {
            if (!m.isFormed()) {
                continue;
            }
            if (m instanceof MoltenStripperModuleMachine) {
                strip++;
            } else if (m instanceof MoltenSpaceOreModuleMachine) {
                ore++;
            } else if (m instanceof MoltenBlastModuleMachine) {
                blast++;
            }
        }
        int total = blast + strip + ore;
        textList.add(Component.literal("识别模块: ")
                .append(Component.literal(String.valueOf(total)).withStyle(ChatFormatting.GOLD))
                .withStyle(ChatFormatting.GRAY));
        if (total > 0) {
            textList.add(Component.literal("  · " + ModuleKind.BLAST.displayNameZh() + " ")
                    .append(Component.literal(String.valueOf(blast)).withStyle(ChatFormatting.GOLD))
                    .withStyle(ChatFormatting.GRAY));
            textList.add(Component.literal("  · " + ModuleKind.STAR_CORE_STRIPPER.displayNameZh() + " ")
                    .append(Component.literal(String.valueOf(strip)).withStyle(ChatFormatting.GOLD))
                    .withStyle(ChatFormatting.GRAY));
            textList.add(Component.literal("  · " + ModuleKind.SPACE_ORE_PROCESSOR.displayNameZh() + " ")
                    .append(Component.literal(String.valueOf(ore)).withStyle(ChatFormatting.GOLD))
                    .withStyle(ChatFormatting.GRAY));
        }
    }
}
