package com.gtlmodular.common.machine.independent.recipe;

import java.math.BigDecimal;
import java.math.BigInteger;

public final class SingleBatchEnergy {
    private SingleBatchEnergy() {}

    public static Normal normal(double originEnergy, long parallels, double multiplier, long voltage, int minimumDuration) {
        validate(parallels, multiplier, minimumDuration);
        if (voltage <= 0 || !Double.isFinite(originEnergy)) throw new IllegalArgumentException("Invalid normal energy");
        double energy = total(originEnergy, parallels, multiplier);
        double ticks = energy / voltage;
        // Kotlin roundToInt 对溢出饱和，half 向正无穷取整；不能换成 ceil 或先转 long 再乘。
        int duration = (int) Math.min(Integer.MAX_VALUE, Math.round(Math.max(ticks, minimumDuration)));
        long eut = ticks > minimumDuration ? voltage : (long) (energy / minimumDuration);
        return new Normal(duration, eut);
    }

    public static double total(double originEnergy, long parallels, double multiplier) {
        validate(parallels, multiplier, 1);
        double energy = originEnergy * parallels * multiplier;
        if (!Double.isFinite(energy)) throw new IllegalArgumentException("Non-finite batch energy");
        return energy;
    }

    public static BigInteger wirelessTotal(long eut, int originDuration, long parallels, double multiplier,
                                          boolean consumer) {
        validate(parallels, multiplier, originDuration);
        double durationFactor = (double) originDuration * multiplier;
        if (!Double.isFinite(durationFactor)) throw new IllegalArgumentException("Non-finite wireless duration factor");
        BigInteger energy = BigDecimal.valueOf(durationFactor)
                .multiply(new BigDecimal(BigInteger.valueOf(eut).multiply(BigInteger.valueOf(parallels))))
                .toBigInteger();
        return consumer ? energy : energy.negate();
    }

    public static BigInteger wirelessTick(BigInteger total, int duration) {
        if (duration < 1) throw new IllegalArgumentException("Invalid duration");
        return total.divide(BigInteger.valueOf(duration)).negate();
    }

    private static void validate(long parallels, double multiplier, int duration) {
        if (parallels < 1 || duration < 1 || !Double.isFinite(multiplier) || multiplier < 0) {
            throw new IllegalArgumentException("Invalid batch energy parameters");
        }
    }

    public record Normal(int duration, long eut) {}
}
