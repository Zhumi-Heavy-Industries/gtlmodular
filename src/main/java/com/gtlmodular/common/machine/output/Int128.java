package com.gtlmodular.common.machine.output;

/**
 * 无符号 128 位计数：应付“单 key 待交付量超过 {@link Long#MAX_VALUE}”的情况。
 *
 * <p>不给饱和语义——溢出直接抛 {@link ArithmeticException}。静默饱和会把“少交付”伪装成“已交付”，
 * 比崩一次更难查。</p>
 */
public final class Int128 implements Comparable<Int128> {
    public static final Int128 ZERO = new Int128(0L, 0L);

    private final long high;
    private final long low;

    private Int128(long high, long low) {
        this.high = high;
        this.low = low;
    }

    public static Int128 of(long value) {
        if (value < 0) {
            throw new IllegalArgumentException("Int128 不接受负数: " + value);
        }
        return new Int128(0L, value);
    }

    public static Int128 ofUnsigned(long high, long low) {
        return new Int128(high, low);
    }

    public boolean isZero() {
        return high == 0L && low == 0L;
    }

    public long high() {
        return high;
    }

    public long low() {
        return low;
    }

    /** 加；超过 2^128-1 抛异常（不饱和）。 */
    public Int128 add(Int128 other) {
        long lowSum = low + other.low;
        long carry = Long.compareUnsigned(lowSum, low) < 0 ? 1L : 0L;
        long highSum = high + other.high;
        long highCarry = Long.compareUnsigned(highSum, high) < 0 ? 1L : 0L;
        long newHigh = highSum + carry;
        if (highCarry != 0 || Long.compareUnsigned(newHigh, highSum) < 0) {
            throw new ArithmeticException("Int128 溢出");
        }
        return new Int128(newHigh, lowSum);
    }

    /** 减；不足时抛异常（调用方必须先比较）。 */
    public Int128 subtract(Int128 other) {
        if (compareTo(other) < 0) {
            throw new ArithmeticException("Int128 下溢: " + this + " - " + other);
        }
        long lowDiff = low - other.low;
        long borrow = Long.compareUnsigned(low, other.low) < 0 ? 1L : 0L;
        return new Int128(high - other.high - borrow, lowDiff);
    }

    /** 取不超过它的最大 long（分片交付时用）；超过 Long.MAX 时返回 Long.MAX_VALUE。 */
    public long toLongClamped() {
        if (high != 0L || low < 0L) {
            return Long.MAX_VALUE;
        }
        return low;
    }

    public boolean exceedsLong() {
        return high != 0L || low < 0L;
    }

    @Override
    public int compareTo(Int128 other) {
        int h = Long.compareUnsigned(high, other.high);
        return h != 0 ? h : Long.compareUnsigned(low, other.low);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Int128 other && high == other.high && low == other.low;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(high) * 31 + Long.hashCode(low);
    }

    @Override
    public String toString() {
        if (high == 0L && low >= 0L) {
            return Long.toString(low);
        }
        // 只用于日志/调试：按 2^64 进制手拼，避免引入 BigInteger 依赖风格差异
        java.math.BigInteger value = java.math.BigInteger.valueOf(high).shiftLeft(64)
                .add(new java.math.BigInteger(Long.toUnsignedString(low)));
        return value.toString();
    }
}
