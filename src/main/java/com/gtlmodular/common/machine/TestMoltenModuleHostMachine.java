package com.gtlmodular.common.machine;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.block.ICoilType;

/**
 * GameTest 专用炼矿主机：结构只有 3×3×3，供电走本地缓冲，用来单独验证炼矿模块的
 * 候选/批次/输出路径。只在 -Dgtlmodular.testHost=true 时注册，不进正式产物。
 */
public class TestMoltenModuleHostMachine extends MoltenModuleHostMachine {
    private static final long CAPACITY = 1_000_000_000L;
    private long stored = CAPACITY;

    public TestMoltenModuleHostMachine(IMachineBlockEntity holder) {
        super(holder);
    }

    @Override
    public long getTotalStoredEU() {
        return stored;
    }

    @Override
    public long getTotalCapacityEU() {
        return CAPACITY;
    }

    @Override
    public long getMaxInputVoltage() {
        return 2_000_000_000L;
    }

    @Override
    public boolean requestEnergy(long amount) {
        if (amount <= 0L) return true;
        if (stored < amount) return false;
        stored -= amount;
        return true;
    }

    @Override
    public boolean consumeEU(long amount) {
        if (amount <= 0L) return true;
        if (stored < amount) return false;
        stored -= amount;
        return true;
    }

    @Override
    public long getOverclockVoltage() {
        return 2_000_000_000L;
    }

    @Override
    public double getModuleEnergyDiscount() {
        return 0.2D;
    }

    @Override
    public int getTier() {
        return 9;
    }

    @Override
    public int getCoilTemperature() {
        return 9_000;
    }

    @Override
    public ICoilType getCoilType() {
        return null;
    }
}
