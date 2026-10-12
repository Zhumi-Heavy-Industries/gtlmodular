package com.gtlmodular.common.machine.independent.recipe;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

public final class SharedBatchEnergy {
    private SharedBatchEnergy() {}

    public static List<SingleBatchEnergy.Normal> normal(List<Double> energies, long voltage, int minimumDuration) {
        if (energies.isEmpty()) return List.of();
        double total = 0;
        List<BigDecimal> weights = new ArrayList<>();
        for (double energy : energies) {
            if (!Double.isFinite(energy) || energy < 0) throw new IllegalArgumentException("Invalid shared energy");
            total += energy;
            weights.add(BigDecimal.valueOf(energy));
        }
        var clock = SingleBatchEnergy.normal(total, 1, 1, voltage, minimumDuration);
        return split(BigInteger.valueOf(clock.eut()), weights).stream()
                .map(eut -> new SingleBatchEnergy.Normal(clock.duration(), eut.longValueExact())).toList();
    }

    public static List<BigInteger> wireless(List<BigInteger> energies, BigInteger mergedTick) {
        int sign = 0;
        for (var energy : energies) {
            if (sign != 0 && energy.signum() != 0 && sign != energy.signum()) {
                throw new IllegalArgumentException("Mixed wireless consumption and generation needs a dedicated adapter");
            }
            if (energy.signum() != 0) sign = energy.signum();
        }
        return split(mergedTick, energies.stream().map(energy -> new BigDecimal(energy.abs())).toList());
    }

    private static List<BigInteger> split(BigInteger power, List<BigDecimal> weights) {
        var total = weights.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        var cumulative = BigDecimal.ZERO;
        var assigned = BigInteger.ZERO;
        List<BigInteger> shares = new ArrayList<>();
        for (var weight : weights) {
            cumulative = cumulative.add(weight);
            // 对整机取整一次再分配尾数，避免每批取整后凭空少扣电或放大功率。
            var next = total.signum() == 0 ? BigInteger.ZERO : new BigDecimal(power)
                    .multiply(cumulative).divide(total, 0, RoundingMode.DOWN).toBigIntegerExact();
            shares.add(next.subtract(assigned));
            assigned = next;
        }
        if (!assigned.equals(power)) throw new IllegalArgumentException("Power without batch energy");
        return List.copyOf(shares);
    }
}
