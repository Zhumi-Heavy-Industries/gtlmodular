package com.gtlmodular.common.machine.boost;

import com.gregtechceu.gtceu.api.block.ICoilType;
import com.gregtechceu.gtceu.api.machine.multiblock.CoilWorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gtlmodular.common.config.GTLModularConfig;

/**
 * 线圈温度降耗（模块 A 熔岩高炉单元）。
 *
 * <p>用户口径（已冻结）：
 * <ul>
 *   <li><b>并行数量一律由并行控制仓提供</b>（机器 getMaxParallel 读仓设定，无仓=1），
 *       线圈不再提供任何并行上限加成；</li>
 *   <li>换高级加热线圈只降低 <b>EU/t</b>（不动耗时）：温度档 =
 *       max(1, ⌊(线圈温度 − base) / temperaturePerLevel⌋ + 1)；
 *       能耗倍率 = clamp(factorPerLevel^(档−1), minFactor, 1.0)。</li>
 * </ul>
 * 默认数值：base=1800K（铜镍=第1档）、每 1800K +1 档、每档 ×0.95、封顶 ×0.5。
 */
public final class CoilBoostLogic {

    private CoilBoostLogic() {
    }

    public static boolean enabled() {
        return GTLModularConfig.isCoilDiscountEnabled();
    }

    /**
     * 取机器当前加热线圈；未成形 / 非线圈机返回 null。
     */
    public static ICoilType coilType(WorkableElectricMultiblockMachine machine) {
        if (machine instanceof CoilWorkableElectricMultiblockMachine coil) {
            return coil.getCoilType();
        }
        return null;
    }

    /** 线圈基准温（开尔文）；未成形或非线圈机返回 0。 */
    public static int coilTemperature(WorkableElectricMultiblockMachine machine) {
        ICoilType type = coilType(machine);
        return type == null ? 0 : type.getCoilTemperature();
    }

    /**
     * 由线圈温度折算温度档：base（默认 1800K）为第 1 档，每高 temperaturePerLevel K 升 1 档。
     */
    public static int temperatureLevel(int coilTemperature) {
        int base = GTLModularConfig.getCoilDiscountBaseTemperature();
        int perLevel = GTLModularConfig.getCoilDiscountTemperaturePerLevel();
        int level = (coilTemperature - base) / perLevel + 1;
        return Math.max(1, level);
    }

    /** EU/t 能耗倍率（0~1，越小越省电）；无开关时恒为 1.0。 */
    public static double euFactorForLevel(int level) {
        if (!enabled()) {
            return 1.0;
        }
        double raw = Math.pow(GTLModularConfig.getCoilDiscountFactorPerLevel(), Math.max(0, level - 1));
        return Math.max(GTLModularConfig.getCoilDiscountMinFactor(), Math.min(1.0, raw));
    }

    /** 机器当前生效的 EU/t 倍率（按成形后线圈温度折算；未成形/无开关 = 1.0）。 */
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

    /**
     * 直接按线圈温度算 EU/t 倍率（模块没有自己的线圈，温度来自主机：
     * 用主机同步过来的温度算，客户端界面与服务端口径才一致）。
     */
    public static double euFactorForTemperature(int temperature) {
        if (!enabled() || temperature <= 0) {
            return 1.0;
        }
        return euFactorForLevel(temperatureLevel(temperature));
    }
}
