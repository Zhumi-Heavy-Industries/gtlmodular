package com.gtlmodular.common.machine.boost;

import com.gregtechceu.gtceu.api.block.ICoilType;
import com.gregtechceu.gtceu.api.machine.multiblock.CoilWorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gtlmodular.common.config.GTLModularConfig;

public final class CoilBoostLogic {

    private CoilBoostLogic() {
    }

    public static boolean enabled() {
        return GTLModularConfig.isCoilDiscountEnabled();
    }

    public static ICoilType coilType(WorkableElectricMultiblockMachine machine) {
        if (machine instanceof CoilWorkableElectricMultiblockMachine coil) {
            return coil.getCoilType();
        }
        return null;
    }

    public static int coilTemperature(WorkableElectricMultiblockMachine machine) {
        ICoilType type = coilType(machine);
        return type == null ? 0 : type.getCoilTemperature();
    }

    public static int temperatureLevel(int coilTemperature) {
        int base = GTLModularConfig.getCoilDiscountBaseTemperature();
        int perLevel = GTLModularConfig.getCoilDiscountTemperaturePerLevel();
        int level = (coilTemperature - base) / perLevel + 1;
        return Math.max(1, level);
    }

    public static double euFactorForLevel(int level) {
        if (!enabled()) {
            return 1.0;
        }
        double raw = Math.pow(GTLModularConfig.getCoilDiscountFactorPerLevel(), Math.max(0, level - 1));
        return Math.max(GTLModularConfig.getCoilDiscountMinFactor(), Math.min(1.0, raw));
    }

    public static double currentEuFactor(WorkableElectricMultiblockMachine machine) {
        if (!enabled()) {
            return 1.0;
        }
        int temp = coilTemperature(machine);
        if (temp <= 0) {
            return 1.0;
        }
        return euFactorForLevel(temperatureLevel(temp));
    }

    /** 模块没有自己的线圈，使用主机同步温度才能使客户端显示与服务端计算一致。 */
    public static double euFactorForTemperature(int temperature) {
        if (!enabled() || temperature <= 0) {
            return 1.0;
        }
        return euFactorForLevel(temperatureLevel(temperature));
    }
}
