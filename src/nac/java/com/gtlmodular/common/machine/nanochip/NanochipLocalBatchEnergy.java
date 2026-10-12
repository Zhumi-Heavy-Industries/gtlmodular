package com.gtlmodular.common.machine.nanochip;

import com.gregtechceu.gtceu.api.misc.EnergyContainerList;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gtladd.gtladditions.api.machine.IWirelessElectricMultiblockMachine;
import com.gtladd.gtladditions.utils.RecipeCalculationHelper;

import java.util.List;

/** 合批在扣料时就确定产物，必须先限制容量，不能在 setup 后拆分已扣料的批次。 */
public final class NanochipLocalBatchEnergy {
    private final long capacity;
    private final long voltage;
    private double admittedEU;
    private double candidateEU;

    public NanochipLocalBatchEnergy(long capacity, long voltage) {
        this.capacity = capacity;
        this.voltage = voltage;
    }

    public static EnergyContainerList finiteLocalContainer(NanochipModuleMachine module) {
        if (module.drawingFromHost()
                || ((IWirelessElectricMultiblockMachine) module).getWirelessNetworkEnergyHandler() != null) {
            return null;
        }
        EnergyContainerList energy = module.getEnergyContainer();
        return energy != null && energy.getEnergyCapacity() > 0L
                && energy.getEnergyCapacity() < NanochipAssemblyComplexMachine.ON_DEMAND_POWER_FLOOR_EU
                ? energy : null;
    }

    public long limitParallel(long available, double singleEU, double multiplier) {
        candidateEU = admittedEU;
        if (available <= 0 || voltage <= 0 || !Double.isFinite(singleEU) || singleEU < 0
                || !Double.isFinite(multiplier) || multiplier < 0) return 0;
        long low = 0;
        long high = available;
        while (low < high) {
            long mid = low + (high - low) / 2 + 1;
            if (fits(admittedEU + singleEU * mid * multiplier)) low = mid;
            else high = mid - 1;
        }
        if (low == 0 && admittedEU == 0) {
            // 单份就超过能源仓容量时永远存不够整批；这时像普通 GT 机器一样只开这一份，逐 tick 扣电。
            low = 1;
        }
        candidateEU = admittedEU + singleEU * low * multiplier;
        return low;
    }

    public void recordConsumption(boolean consumed) {
        if (consumed) admittedEU = candidateEU;
    }

    private boolean fits(double totalEU) {
        if (!Double.isFinite(totalEU)) return false;
        // 上游会对时长取整，直接复用最终结算，避免估算放得下却在取整后超出容量。
        GTRecipe batch = RecipeCalculationHelper.INSTANCE.buildNormalRecipe(
                List.of(), List.of(), totalEU, voltage, 20);
        return covers(batch, capacity);
    }

    /** 存得下整批就要求存够整批，开工后不会中途断电；单份就超过容量的批次只能等能源仓充满后逐 tick 扣电。 */
    public static boolean readyToStart(GTRecipe recipe, long stored, long capacity) {
        long eut = Math.max(0L, RecipeHelper.getInputEUt(recipe));
        long duration = Math.max(1, recipe.duration);
        long total = eut > Long.MAX_VALUE / duration ? Long.MAX_VALUE : eut * duration;
        return stored >= Math.min(total, Math.max(0L, capacity));
    }

    public static boolean covers(GTRecipe recipe, long stored) {
        long eut = Math.max(0L, RecipeHelper.getInputEUt(recipe));
        return stored >= 0L && eut <= stored / Math.max(1, recipe.duration);
    }
}
