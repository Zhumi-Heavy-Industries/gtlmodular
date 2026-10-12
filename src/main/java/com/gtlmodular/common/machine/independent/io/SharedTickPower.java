package com.gtlmodular.common.machine.independent.io;

import java.math.BigInteger;

public final class SharedTickPower {
    private BigInteger remaining = BigInteger.ZERO;

    public void reset(BigInteger limit) {
        if (limit.signum() < 0) throw new IllegalArgumentException("Negative machine power");
        remaining = limit;
    }

    public BigInteger remaining() { return remaining; }

    public long limit(long requested) {
        if (requested < 0) throw new IllegalArgumentException("Negative power request");
        return remaining.min(BigInteger.valueOf(requested)).longValueExact();
    }

    public boolean reserve(BigInteger amount) {
        if (amount.signum() < 0) throw new IllegalArgumentException("Negative power reservation");
        if (amount.compareTo(remaining) > 0) return false;
        remaining = remaining.subtract(amount);
        return true;
    }

    public static long unreserved(long limit, Iterable<BigInteger> occupied) {
        BigInteger remaining = BigInteger.valueOf(Math.max(0, limit));
        for (var power : occupied) {
            if (power.signum() < 0) throw new IllegalArgumentException("Negative occupied power");
            remaining = remaining.subtract(power);
        }
        return remaining.max(BigInteger.ZERO).longValueExact();
    }
}
