package com.gtlmodular.common.machine.nanochip.vacuum;

import com.gtlmodular.common.machine.nanochip.NanochipPipeMachine;
import com.gtlmodular.common.machine.nanochip.NanochipColor;
import com.gtlmodular.common.machine.nanochip.NanochipVacuumHatchMachine;
import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

public final class NanochipPipeConnections {

    private NanochipPipeConnections() {
    }

    public static int bit(Direction side) {
        return 1 << side.ordinal();
    }

    public static int connectionMask(NanochipPipeMachine machine) {
        int mask = 0;
        for (Direction side : Direction.values()) {
            if (machine.connectsTo(side)) {
                mask |= bit(side);
            }
        }
        return mask;
    }

    public static boolean connectsTo(NanochipPipeMachine machine, Direction side) {
        Level level = machine.getLevel();
        if (level == null) {
            return false;
        }
        if (!machine.isPainted()) {
            return false;
        }
        BlockPos neighbour = machine.getPos().relative(side);
        // 渲染也会查询邻居，先检查加载状态，避免显示查询强制加载区块。
        if (!level.isLoaded(neighbour)) {
            return false;
        }
        NanochipColor mine = machine.color();
        if (level.getBlockEntity(neighbour) instanceof MetaMachineBlockEntity be) {
            MetaMachine other = be.getMetaMachine();
            if (other instanceof NanochipPipeMachine pipe) {
                return pipe.isPainted() && pipe.color() == mine;
            }
            if (other instanceof NanochipVacuumHatchMachine hatch) {
                return hatch.isPainted() && hatch.color() == mine
                        && hatch.canConnectOnSide(side.getOpposite());
            }
            return false;
        }
        return false;
    }
}
