package com.gtlmodular.common.machine.nanochip;

import com.gtlmodular.common.diag.SwallowedErrors;
import com.gtlmodular.common.machine.nanochip.vacuum.NanochipPipeConnections;
import com.gtlmodular.common.machine.nanochip.display.NanochipLogisticsText;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

/** 连接几何依据邻居即时计算，避免把派生的管臂状态另存为一份可能过期的数据。 */
public class NanochipPipeMachine extends MetaMachine {

    public static final ManagedFieldHolder MANAGED_FIELD_HOLDER =
            new ManagedFieldHolder(NanochipPipeMachine.class, MetaMachine.MANAGED_FIELD_HOLDER);

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    public static final int TINT_BAR = 3;

    @DescSynced
    private int colorIndex = NanochipColor.INDEX_UNPAINTED;

    public NanochipPipeMachine(IMachineBlockEntity holder) {
        super(holder);
    }

    /** 未染色与白色通道必须区分，否则未喷漆的管道会意外接入白色网络。 */
    public NanochipColor color() {
        return NanochipColor.fromIndex(colorIndex);
    }

    public boolean isPainted() {
        return colorIndex >= 0;
    }

    public void setColor(NanochipColor color) {
        setColor(color, true);
    }

    public void setColor(NanochipColor color, boolean manual) {
        int before = colorIndex;
        int next = color == null ? NanochipColor.INDEX_UNPAINTED : color.ordinal();
        this.colorIndex = next;
        syncPaintingColor();
        if (before != next) {
            scheduleRenderUpdate();
            // 换色改变同色连通关系，必须使管网缓存失效。
            NanochipPipeGraph.bumpTopologyEpoch();
        }
    }

    @Override
    public int tintColor(int index) {
        if (index == TINT_BAR) {
            NanochipColor c = color();
            return c == null ? -1 : c.paintArgb();
        }
        return -1;
    }

    public static int bit(Direction side) {
        return NanochipPipeConnections.bit(side);
    }

    public int connectionMask() {
        return NanochipPipeConnections.connectionMask(this);
    }

    public boolean connectsTo(Direction side) {
        return NanochipPipeConnections.connectsTo(this, side);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        Level level = getLevel();
        if (level != null && !level.isClientSide) {
            syncPaintingColor();
            NanochipPipeGraph.bumpTopologyEpoch();
        }
    }

    /** 同步喷漆显示字段，使外部提示与实际通道颜色一致。 */
    private void syncPaintingColor() {
        try {
            NanochipColor c = color();
            super.setPaintingColor(c == null ? 0xFFFFFFFF : c.paintArgb());
        } catch (Throwable ignored) {
            SwallowedErrors.report("NanochipPipeMachine#syncPaintingColor", ignored);
        }
    }

    @Override
    public void onUnload() {
        // 管道卸载可能改变连通分量，需使图缓存失效。
        NanochipPipeGraph.bumpTopologyEpoch();
        super.onUnload();
    }

    @Override
    public InteractionResult onScrewdriverClick(Player player, InteractionHand hand,
                                               Direction gridSide, BlockHitResult hitResult) {
        Level level = getLevel();
        if (level != null && !level.isClientSide) {
            NanochipColor current = color();
            NanochipColor next = current == null ? NanochipColor.WHITE : current.next();
            setColor(next, true);
            if (player != null) {
                player.displayClientMessage(NanochipLogisticsText.colorChanged(next), true);
            }
        }
        return InteractionResult.sidedSuccess(level != null && level.isClientSide);
    }

    /** 读取时同时看颜色和手工染色标记，以区分有效染色与旧存档中的自动颜色。 */
    @Override
    public void saveCustomPersistedData(net.minecraft.nbt.CompoundTag tag, boolean forDrop) {
        super.saveCustomPersistedData(tag, forDrop);
        tag.putInt("GTLModularPipeColor", colorIndex);
        tag.putBoolean("GTLModularPipeManual", isPainted());
    }

    @Override
    public void loadCustomPersistedData(net.minecraft.nbt.CompoundTag tag) {
        super.loadCustomPersistedData(tag);
        boolean painted = tag.getBoolean("GTLModularPipeManual");
        int stored = tag.getInt("GTLModularPipeColor");
        colorIndex = (painted && stored >= 0 && stored < NanochipColor.values().length)
                ? stored
                : NanochipColor.INDEX_UNPAINTED;
        syncPaintingColor();
    }

    @Override
    public void setPaintingColor(int argb) {
        NanochipColor mapped = NanochipColor.ofArgb(argb);
        if (mapped != null) {
            setColor(mapped, true);
        }
        syncPaintingColor();
    }
}
