package com.gtlmodular.common.machine.nanochip;

/** 合批会重新分配功率和时长，必须在原配方仍保留族信息时折算总耗能。 */
final class NanochipCalibrationEnergy {

    private NanochipCalibrationEnergy() {
    }

    static double totalEu(double originalEu, NanochipCalibration calibration,
                          NanochipCalibration.Family family) {
        return originalEu * calibration.euMultiplier() * calibration.durationMultiplier(family);
    }

    /** NH：EU 除数 = log4ceil(A) − 3 + c，c 只由三级晶体校准提供。 */
    static long laserEuDivisor(long amperage, boolean crystalTierThree) {
        if (amperage < 256L) {
            return 1L;
        }
        return Math.max(1L, log4Ceil(amperage) - 3L + (crystalTierThree ? 1L : 0L));
    }

    /** NH：耗时除数 = max(1, T − 9 + c)，T 为激光仓等级。 */
    static long laserDurationDivisor(int tier, boolean crystalTierThree) {
        return Math.max(1L, (long) tier - 9L + (crystalTierThree ? 1L : 0L));
    }

    /** 激光增益不设校准门槛，c 只是再各降一档。 */
    static double laserMultiplier(long amperage, int tier, boolean crystalTierThree) {
        return 1.0D / ((double) laserEuDivisor(amperage, crystalTierThree)
                * (double) laserDurationDivisor(tier, crystalTierThree));
    }

    /** 本机激光仓电流恒为 4 的幂（256 << 2(tier−6)），上取整与下取整结果相同。 */
    static long log4Ceil(long value) {
        long exponent = 0L;
        long power = 1L;
        while (power < value) {
            power <<= 2;
            exponent++;
        }
        return exponent;
    }
}
