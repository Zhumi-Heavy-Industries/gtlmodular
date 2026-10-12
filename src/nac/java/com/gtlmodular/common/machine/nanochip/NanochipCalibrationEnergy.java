package com.gtlmodular.common.machine.nanochip;

/** 合批会重新分配功率和时长，必须在原配方仍保留族信息时折算总耗能。 */
final class NanochipCalibrationEnergy {

    private NanochipCalibrationEnergy() {
    }

    static double totalEu(double originalEu, NanochipCalibration calibration,
                          NanochipCalibration.Family family) {
        return originalEu * calibration.euMultiplier() * calibration.durationMultiplier(family);
    }

    static double laserMultiplier(long amperage, int tier) {
        double eu = amperage >= 512L ? 1.0D / (amperage / 256L) : 1.0D;
        double duration = tier > 9 ? 1.0D / (tier - 9) : 1.0D;
        return eu * duration;
    }
}
