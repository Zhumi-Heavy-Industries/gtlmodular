package com.gtlmodular.common.machine;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gtladd.gtladditions.utils.CommonUtils;
import com.gtlmodular.common.data.module.SpaceOreModulePattern;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.Comparator;
import java.util.List;

public class MoltenSpaceOreModuleMachine extends MoltenBlastModuleMachine {

    /** 自带同步字段必须挂到本类的 ManagedFieldHolder，客户端才能收到它们。 */
    public static final ManagedFieldHolder MANAGED_FIELD_HOLDER =
            new ManagedFieldHolder(MoltenSpaceOreModuleMachine.class,
                    MoltenModuleMachineBase.MANAGED_FIELD_HOLDER);

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    public static final int GTLCORE_THREADS = 64;

    public static final int HIDDEN_PARALLEL_TEMP = 16200;

    /** 界面在客户端组装，动力模块等级必须同步。 */
    @DescSynced
    private int syncedPowerTier = 1;

    @DescSynced
    private int syncedPowerModules;

    @DescSynced
    private int syncedThreads = GTLCORE_THREADS;

    private final List<BlockPos> powerCells = new ObjectArrayList<>();

    private int livePowerTier = -1;
    private int livePowerModules;
    private long livePowerTick = -1L;

    public MoltenSpaceOreModuleMachine(IMachineBlockEntity holder) {
        super(holder);
    }

    private void capturePowerCells() {
        powerCells.clear();
        Object o = getMultiblockState().getMatchContext().get(SpaceOreModulePattern.POWER_CELLS_KEY);
        if (o instanceof List<?> list) {
            for (Object p : list) {
                if (p instanceof BlockPos bp && !powerCells.contains(bp)) {
                    powerCells.add(bp);
                }
            }
        }
        powerCells.sort(Comparator.comparingLong(BlockPos::asLong));
        livePowerTier = -1;
        livePowerTick = -1L;
    }

    /** 运行时回读安装格，才能反映更换后的动力模块等级。 */
    private int readPowerModuleTier(Level lv) {
        if (powerCells.isEmpty()) {
            if (!isFormed()) {
                return 1;
            }
            // 读档后可能尚未经过成形回调，需要从判定上下文补取安装格。
            capturePowerCells();
            if (powerCells.isEmpty()) {
                return syncedPowerTier > 0 ? syncedPowerTier : 1;
            }
        }
        long t = lv.getGameTime();
        if (livePowerTier > 0 && livePowerTick >= 0 && t - livePowerTick < 20L) {
            return livePowerTier;
        }
        livePowerTick = t;
        int best = 0;
        int count = 0;
        for (BlockPos p : powerCells) {
            if (!lv.isLoaded(p)) {
                continue;
            }
            int tier = SpaceOreModulePattern.powerTierOf(lv.getBlockState(p));
            if (tier > 0) {
                count++;
                if (tier > best) {
                    best = tier;
                }
            }
        }
        livePowerTier = Math.max(1, best);
        livePowerModules = count;
        if (!lv.isClientSide) {
            syncedPowerTier = livePowerTier;
            syncedPowerModules = count;
            syncedThreads = threadsForTier(livePowerTier);
        }
        return livePowerTier;
    }

    public int powerModuleTier() {
        Level lv = getLevel();
        if (lv != null && !lv.isClientSide && isFormed()) {
            return readPowerModuleTier(lv);
        }
        return syncedPowerTier > 0 ? syncedPowerTier : 1;
    }

    public int powerModuleCount() {
        Level lv = getLevel();
        if (lv != null && !lv.isClientSide && isFormed()) {
            readPowerModuleTier(lv);
            return livePowerModules;
        }
        return syncedPowerModules;
    }

    public static int threadsForTier(int tier) {
        return SpaceOreModulePattern.threadsForTier(tier);
    }

    public int powerModuleThreads() {
        return threadsForTier(powerModuleTier());
    }

    public int powerCellCount() {
        return powerCells.size();
    }

    @Override
    public void onStructureFormed() {
        super.onStructureFormed();
        capturePowerCells();
        Level lv = getLevel();
        if (lv != null && !lv.isClientSide) {
            readPowerModuleTier(lv);
        }
    }

    @Override
    public void onStructureInvalid() {
        super.onStructureInvalid();
        powerCells.clear();
        livePowerTier = -1;
        livePowerTick = -1L;
        Level lv = getLevel();
        if (lv != null && !lv.isClientSide) {
            syncedPowerTier = 1;
            syncedPowerModules = 0;
            syncedThreads = GTLCORE_THREADS;
        }
    }

    @Override
    public void saveCustomPersistedData(CompoundTag tag, boolean forDrop) {
        super.saveCustomPersistedData(tag, forDrop);
        long[] packed = new long[powerCells.size()];
        for (int i = 0; i < packed.length; i++) {
            packed[i] = powerCells.get(i).asLong();
        }
        tag.putLongArray("GTLModularSpaceOrePowerCells", packed);
    }

    @Override
    public void loadCustomPersistedData(CompoundTag tag) {
        super.loadCustomPersistedData(tag);
        powerCells.clear();
        for (long l : tag.getLongArray("GTLModularSpaceOrePowerCells")) {
            powerCells.add(BlockPos.of(l));
        }
        powerCells.sort(Comparator.comparingLong(BlockPos::asLong));
        livePowerTier = -1;
        livePowerTick = -1L;
    }

    /** 外部显示会再加基础线程数，此处只返回额外部分，避免重复计算。 */
    @Override
    public int getAdditionalThread() {
        long bonus = (long) powerModuleThreads() - GTLCORE_THREADS + super.getAdditionalThread();
        return (int) Math.max(0L, Math.min(Integer.MAX_VALUE, bonus));
    }

    @Override
    public int totalThreads() {
        return (int) Math.min(Integer.MAX_VALUE, (long) GTLCORE_THREADS + getAdditionalThread());
    }

    /** 本机自行显示线程数，避免父类重复添加同一行。 */
    @Override
    protected boolean showsThreadLine() {
        return false;
    }

    @Override
    public int getMaxParallel() {
        if (isHiddenParallelActive()) {
            return Integer.MAX_VALUE;
        }
        return super.getMaxParallel();
    }

    public boolean isHiddenParallelActive() {
        return hostCoilTemperature() >= HIDDEN_PARALLEL_TEMP;
    }

    public static void appendConfiguredTooltips(ItemStack stack, List<Component> lines) {
        lines.add(Component.translatable("block.gtlmodular.molten_space_ore_module.desc")
                .withStyle(ChatFormatting.GRAY));
        appendFluidTaxTooltip(lines);
        lines.add(Component.translatable("gtlmodular.space.tooltip.power",
                Component.literal(String.valueOf(GTLCORE_THREADS)).withStyle(ChatFormatting.GOLD),
                Component.literal(String.valueOf(threadsForTier(SpaceOreModulePattern.MAX_POWER_TIER)))
                        .withStyle(ChatFormatting.GOLD))
                .withStyle(ChatFormatting.GRAY));
    }

    @Override
    protected void addHeatCapacityLine(List<Component> textList) {
    }

    @Override
    public void addDisplayText(List<Component> textList) {
        super.addDisplayText(textList);
        if (!isFormed()) {
            return;
        }
        int tier = powerModuleTier();
        int count = powerModuleCount();
        Component module = count > 0
                ? Component.literal(SpaceOreModulePattern.tierName(tier) + " ×" + count).withStyle(ChatFormatting.GOLD)
                : Component.translatable("gtlmodular.space.power.none").withStyle(ChatFormatting.RED);
        textList.add(Component.translatable("gtlmodular.space.power.line", module)
                .withStyle(ChatFormatting.GRAY));
        if (isHiddenParallelActive()) {
            for (int i = 0; i < textList.size(); i++) {
                Component c = textList.get(i);
                if (c.getContents() instanceof TranslatableContents tc
                        && "gtceu.multiblock.parallel".equals(tc.getKey())) {
                    textList.set(i, Component.translatable("gtceu.multiblock.parallel", rainbowParallel())
                            .withStyle(ChatFormatting.GRAY));
                    break;
                }
            }
        }
        textList.add(Component.translatable("gtladditions.multiblock.threads",
                Component.literal(String.valueOf(powerModuleThreads())).withStyle(ChatFormatting.GOLD))
                .withStyle(ChatFormatting.GRAY));
    }

    private static Component rainbowParallel() {
        return CommonUtils.INSTANCE.createLanguageRainbowComponentOnServer(
                Component.literal(String.valueOf(Integer.MAX_VALUE)));
    }
}
