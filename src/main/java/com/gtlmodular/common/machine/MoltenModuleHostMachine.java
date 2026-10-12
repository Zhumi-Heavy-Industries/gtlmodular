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

public class MoltenModuleHostMachine extends CoilWorkableElectricMultiblockMachine
        implements IDisplayUIMachine, IModularMachineHost<MoltenModuleHostMachine>, IMachineLife {

    /** 自带同步字段必须挂到本类的 ManagedFieldHolder，客户端才能收到它们。 */
    public static final ManagedFieldHolder MANAGED_FIELD_HOLDER =
            new ManagedFieldHolder(MoltenModuleHostMachine.class,
                    WorkableMultiblockMachine.MANAGED_FIELD_HOLDER);

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    /** 界面在客户端组装，温度和格数必须同步，否则会显示旧值或零。 */
    @DescSynced
    private int syncedCoilTemperature;

    @DescSynced
    private int syncedCoilCellCount;

    @DescSynced
    private String syncedCoilName = "";

    /** 构成文案在服务端计算，避免两侧统计不一致。 */
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

    /** 模块可能位于大楼远墙，互扫半径必须覆盖楼体。 */
    private static final int FULL_R = 96;
    private static final int FULL_VER = 64;

    /** 界面刷新频繁，只作小范围补连以避免反复全楼扫描。 */
    private static final int QUICK_R = 48;
    private static final int QUICK_VER = 32;

    private final Set<IModularMachineModule<MoltenModuleHostMachine, ?>> modules = new LinkedHashSet<>();

    private long lastRescanTick = -1;

    public MoltenModuleHostMachine(IMachineBlockEntity holder) {
        super(holder);
    }

    /** 主机不加工，避免搜索占位配方产生失败提示；工作状态仍须跟随模块以驱动线圈活跃材质。 */
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
        }
    }

    @Override
    protected RecipeLogic createRecipeLogic(Object... args) {
        return new HostRecipeLogic(this);
    }

    private EnergyContainerList energy() {
        return isFormed() ? getEnergyContainer() : null;
    }

    public long getTotalStoredEU() {
        EnergyContainerList e = energy();
        return e == null ? 0 : e.getEnergyStored();
    }

    public long getTotalCapacityEU() {
        EnergyContainerList e = energy();
        return e == null ? 0 : e.getEnergyCapacity();
    }

    public long getMaxInputVoltage() {
        EnergyContainerList e = energy();
        return e == null ? 0 : e.getInputVoltage();
    }

    @DescSynced
    private long syncedModuleDrawPerTick;

    private long drawAccumulator;
    private long drawWindowSecond = Long.MIN_VALUE;

    public long getModuleDrawPerTick() {
        return syncedModuleDrawPerTick;
    }

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

    private final List<BlockPos> coilCells = new ArrayList<>();

    private ICoilType liveCoilType;
    private long liveCoilTick = -1L;
    private String coilBreakdown = "";

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

    /** 结构判定缓存只代表成形时的线圈，运行时回读才能反映替换后的温度。 */
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
            // 读档后可能尚未经过成形回调，需要从判定上下文补取线圈坐标。
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

    public int getCoilCellCount() {
        return coilCells.size();
    }

    private long lastCoilRecoveryTick = -1L;
    private boolean coilCellsMissingWarned;

    /** 主机自行定期回读线圈，避免没有模块加工时温度和界面一直停在旧值。 */
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
        getCoilType();
    }

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

    public int getCoilTemperature() {
        ICoilType coil = getCoilType();
        return coil == null ? 0 : coil.getCoilTemperature();
    }

    public static final int DISCOUNT_BASE_TEMP = 5400;
    public static final int DISCOUNT_STEP_TEMP = 240;
    public static final double DISCOUNT_STEP = 0.01D;
    public static final double DISCOUNT_CAP = 0.50D;

    public static double discountForTemperature(int temp) {
        if (temp <= DISCOUNT_BASE_TEMP) {
            return 0.0D;
        }
        return Math.min(DISCOUNT_CAP,
                (temp - DISCOUNT_BASE_TEMP) / (double) DISCOUNT_STEP_TEMP * DISCOUNT_STEP);
    }

    public static int discountPercentForTemperature(int temp) {
        return (int) Math.round(discountForTemperature(temp) * 100.0D);
    }

    public double getModuleEnergyDiscount() {
        return discountForTemperature(getCoilTemperature());
    }

    @Override
    public Set<IModularMachineModule<MoltenModuleHostMachine, ?>> getModuleSet() {
        return modules;
    }

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

    @Override
    public BlockPos[] getModuleScanPositions() {
        return new BlockPos[0];
    }

    /** 按已加载区块枚举方块实体，避免大范围逐格查表和强制加载区块。 */
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
                // 连接动作可能修改区块实体表，遍历前取快照以免并发修改异常。
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
        scanModules(FULL_R, FULL_VER);
    }

    private void quickScan() {
        scanModules(QUICK_R, QUICK_VER);
    }

    @Override
    public int getMaxModuleCount() {
        return 12;
    }

    /** 上游默认预览列表为空，必须提供模块定义才能显示模块预览。 */
    @Override
    public List<ModuleRenderInfo> getModulesForRendering() {
        try {
            return ModulePreviewSlots.moltenHostModules(getPos(), moduleRenderCache);
        } catch (Throwable t) {
            GTLModular.LOGGER.debug("[gtlmodular] 模块预览: 地心枢纽 getModulesForRendering 异常，返回空表: {}",
                    t.toString());
            return List.of();
        }
    }

    private final ModulePreviewSlots.Cache moduleRenderCache = new ModulePreviewSlots.Cache();

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

    @Override
    public void onMachineRemoved() {
        safeClearModules();
    }

    private static final Map<String, Block> COIL_BLOCKS_BY_PATH = new LinkedHashMap<>();
    private static boolean coilBlocksScanned;

    private static Component coilLocalizedName(ICoilType coil) {
        if (coil == null) {
            return Component.literal("无");
        }
        return coilLocalizedName(coil.getName());
    }

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

    /** 服务端与渲染线程共用索引，填充完毕后才能发布完成标志，避免读取半成品。 */
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
        // 基类会显示占位配方表名，仅替换这一行以免玩家看到无意义的机器模式。
        for (int i = 0; i < textList.size(); i++) {
            Component c = textList.get(i);
            if (c.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tc
                    && "gtceu.gui.machinemode".equals(tc.getKey())) {
                textList.set(i, Component.translatable("gtceu.gui.machinemode",
                        Component.literal("地心涌动能量转换站")).withStyle(ChatFormatting.AQUA));
            }
        }
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
        textList.add(Component.literal("中枢蓄电: ")
                .append(Component.literal(getTotalStoredEU() + " / " + getTotalCapacityEU() + " EU")
                        .withStyle(ChatFormatting.AQUA))
                .withStyle(ChatFormatting.GRAY));
        textList.add(Component.literal("模块实付: ")
                .append(Component.literal(FormattingUtil.formatNumbers(syncedModuleDrawPerTick) + " EU/t")
                        .withStyle(ChatFormatting.AQUA))
                .append(Component.literal("（近 1 秒实际从本机扣掉的电）").withStyle(ChatFormatting.GRAY))
                .withStyle(ChatFormatting.GRAY));
        // 客户端结构缓存可能尚未更新，名字、温度和格数须使用同步值。
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
