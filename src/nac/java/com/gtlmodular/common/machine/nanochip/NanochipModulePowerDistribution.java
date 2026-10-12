package com.gtlmodular.common.machine.nanochip;

import com.gtlmodular.common.machine.nanochip.registration.NanochipModuleSpec;
import com.gregtechceu.gtceu.api.misc.EnergyContainerList;
import net.minecraft.world.level.Level;
import org.gtlcore.gtlcore.api.machine.multiblock.IModularMachineModule;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.LongConsumer;
import java.util.function.Supplier;

final class NanochipModulePowerDistribution {

    private final NanochipAssemblyComplexMachine host;
    private final Supplier<EnergyContainerList> ownEnergy;
    private final LongConsumer publishDraw;

    private long lastEnergyDistributeTick = -1L;

    private long drawAccumulator;

    private long drawWindowSecond = Long.MIN_VALUE;

    NanochipModulePowerDistribution(NanochipAssemblyComplexMachine host,
                                    Supplier<EnergyContainerList> ownEnergy, LongConsumer publishDraw) {
        this.host = host;
        this.ownEnergy = ownEnergy;
        this.publishDraw = publishDraw;
    }

    void tickDrawWindow() {
        Level lv = host.getLevel();
        if (lv == null || lv.isClientSide) {
            return;
        }
        long second = lv.getGameTime() / 20L;
        if (second != drawWindowSecond) {
            if (drawWindowSecond != Long.MIN_VALUE) {
                publishDraw.accept(drawAccumulator / 20L);
            }
            drawAccumulator = 0L;
            drawWindowSecond = second;
        }
    }

    long getTotalStoredEU() {
        if (!host.isFormed()) {
            return 0L;
        }
        EnergyContainerList e = host.getEnergyContainer();
        return e == null ? 0L : e.getEnergyStored();
    }

    long getTotalCapacityEU() {
        if (!host.isFormed()) {
            return 0L;
        }
        EnergyContainerList e = host.getEnergyContainer();
        return e == null ? 0L : e.getEnergyCapacity();
    }

    long getMaxInputVoltage() {
        if (!host.isFormed()) {
            return 0L;
        }
        EnergyContainerList e = host.getEnergyContainer();
        return e == null ? 0L : e.getInputVoltage();
    }

    long getMaxInputPower() {
        if (!host.isFormed()) {
            return 0L;
        }
        EnergyContainerList e = host.getEnergyContainer();
        if (e == null) {
            return 0L;
        }
        long v = e.getInputVoltage();
        long a = Math.max(1L, e.getInputAmperage());
        if (v <= 0L) {
            return 0L;
        }
        return saturatingMul(v, a);
    }

    static long saturatingMul(long a, long b) {
        if (a <= 0L || b <= 0L) {
            return 0L;
        }
        return a > Long.MAX_VALUE / b ? Long.MAX_VALUE : a * b;
    }

    static long saturatingAdd(long a, long b) {
        if (a <= 0L) {
            return Math.max(0L, b);
        }
        if (b <= 0L) {
            return a;
        }
        return a > Long.MAX_VALUE - b ? Long.MAX_VALUE : a + b;
    }

    boolean isEnergySourceEffectivelyUnlimited() {
        if (!host.isFormed()) {
            return false;
        }
        EnergyContainerList e = host.getEnergyContainer();
        if (e == null) {
            return false;
        }
        return e.getEnergyCapacity() >= NanochipAssemblyComplexMachine.ON_DEMAND_POWER_FLOOR_EU;
    }

    long getObtainableEnergyEU() {
        if (!host.isFormed()) {
            return 0L;
        }
        EnergyContainerList e = host.getEnergyContainer();
        if (e == null) {
            return 0L;
        }
        long stored = e.getEnergyStored();
        return host.isEnergySourceEffectivelyUnlimited() ? Math.max(stored, e.getEnergyCapacity()) : stored;
    }

    boolean requestModuleEnergy(long amount, boolean lowPriority) {
        if (amount <= 0L) {
            return true;
        }
        Level lv = host.getLevel();
        if (lv == null || lv.isClientSide || !host.isFormed() || !host.isWorkingEnabled()) {
            return false;
        }
        if (lowPriority && highPriorityStarved()) {
            return false;
        }
        EnergyContainerList e = host.getEnergyContainer();
        if (e == null || storedEU(e) < amount) {
            return false;
        }
        long paid = Math.max(0L, -e.changeEnergy(-amount));
        drawAccumulator = saturatingAdd(drawAccumulator, paid);
        return paid >= amount;
    }

    /** 各来源余额都可能已截到 long 上限，求和必须饱和，避免误报负存量。 */
    private static long storedEU(EnergyContainerList e) {
        long stored = 0L;
        for (com.gregtechceu.gtceu.api.capability.IEnergyContainer c : NanochipGridPower.innerContainers(e)) {
            if (c != null) {
                stored = saturatingAdd(stored, Math.max(0L, c.getEnergyStored()));
            }
        }
        return stored;
    }

    private boolean highPriorityStarved() {
        for (IModularMachineModule<NanochipAssemblyComplexMachine, ?> m : host.getModules()) {
            if (!(m instanceof NanochipModuleMachine module) || !module.isFormed()) {
                continue;
            }
            if (module.energyPriority() < 0) {
                continue;
            }
            var logic = module.getRecipeLogic();
            if (logic != null && logic.isWaiting()) {
                return true;
            }
        }
        return false;
    }

    void distributeModuleEnergy(long now) {
        if (lastEnergyDistributeTick >= 0
                && now - lastEnergyDistributeTick < NanochipAssemblyComplexMachine.ENERGY_DISTRIBUTE_INTERVAL) {
            return;
        }
        lastEnergyDistributeTick = now;
        if (!host.isFormed() || !host.isWorkingEnabled()) {
            return;
        }
        if (host.isEnergySourceEffectivelyUnlimited()) {
            return;   // 按需电源走 requestModuleEnergy 逐 tick 扣电，不填充整批缓存。
        }
        List<NanochipModuleMachine> mods = new ArrayList<>();
        for (IModularMachineModule<NanochipAssemblyComplexMachine, ?> m : host.getModules()) {
            if (m instanceof NanochipModuleMachine mm && mm.isFormed() && mm.drawingFromHost()) {
                mods.add(mm);
            }
        }
        mods.sort(Comparator.comparingInt(NanochipModuleMachine::energyPriority).reversed());
        final long hatchInput = finiteHatchInputPower();
        for (NanochipModuleMachine mm : mods) {
            NanochipModuleSpec kind = mm.getModuleSpec();
            int maxTicks = kind == null ? 100 : kind.maxRecipeTicks();
            mm.setBufferCapacity(saturatingMul(saturatingMul(hatchInput, 2L), (long) maxTicks));
            if (host.topUpModuleBuffer(mm) < 0L) {
                break;
            }
        }
    }

    long topUpModuleBuffer(NanochipModuleMachine mm) {
        EnergyContainerList e = host.getEnergyContainer();
        if (e == null) {
            return -1L;
        }
        long room = mm.getBufferCapacityEU() - mm.getBufferEU();
        if (room <= 0L) {
            return 0L;
        }
        long stored = storedEU(e);
        if (stored <= 0L) {
            return -1L;
        }
        long taken = -e.changeEnergy(-Math.min(room, stored));
        if (taken <= 0L) {
            return 0L;
        }
        long got = mm.fillBufferEU(taken);
        if (got < taken) {
            e.changeEnergy(taken - got);          // 缓存未接受的电量必须退回主机，避免扣电与入账不一致。
        }
        drawAccumulator = saturatingAdd(drawAccumulator, got);
        return got;
    }

    void requestBufferTopUp(NanochipModuleMachine mm) {
        if (!host.isFormed() || !host.isWorkingEnabled()) {
            return;
        }
        if (mm.energyPriority() < 0 && highPriorityStarved()) {
            return;
        }
        host.topUpModuleBuffer(mm);
    }

    /** 按需电源的哨兵功率不能用于设置有限缓存上限，否则可能把电网余额全灌入模块。 */
    private long finiteHatchInputPower() {
        EnergyContainerList own = ownEnergy.get();
        if (own == null) {
            return 0L;
        }
        long total = 0L;
        for (com.gregtechceu.gtceu.api.capability.IEnergyContainer c : NanochipGridPower.innerContainers(own)) {
            if (c == null || c.getEnergyCapacity() >= NanochipAssemblyComplexMachine.ON_DEMAND_POWER_FLOOR_EU) {
                continue;
            }
            total = saturatingAdd(total,
                    saturatingMul(Math.max(0L, c.getInputVoltage()), Math.max(0L, c.getInputAmperage())));
        }
        return total;
    }

    void clearModuleBuffers() {
        for (IModularMachineModule<NanochipAssemblyComplexMachine, ?> m : host.getModules()) {
            if (m instanceof NanochipModuleMachine mm) {
                mm.clearBufferEU();
            }
        }
    }
}
