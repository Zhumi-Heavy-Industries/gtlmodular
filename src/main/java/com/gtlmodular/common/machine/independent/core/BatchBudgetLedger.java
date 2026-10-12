package com.gtlmodular.common.machine.independent.core;

import java.math.BigInteger;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;

public final class BatchBudgetLedger<K> {
    private final Map<K, Long> reservations = new IdentityHashMap<>();
    // 恢复时不能因当前限额或 long 求和溢出丢掉已扣料批次的占用。
    private BigInteger occupied = BigInteger.ZERO;

    public BigInteger occupied() {
        return occupied;
    }

    public long available(long limit) {
        if (limit < 0) throw new IllegalArgumentException("Negative budget limit");
        return BigInteger.valueOf(limit).subtract(occupied).max(BigInteger.ZERO).longValueExact();
    }

    public boolean reserve(K owner, long amount, long limit) {
        validateNewReservation(owner, amount);
        if (amount > available(limit) || occupied.compareTo(BigInteger.valueOf(limit)) > 0) return false;
        restore(owner, amount);
        return true;
    }

    public void restore(K owner, long amount) {
        validateNewReservation(owner, amount);
        reservations.put(owner, amount);
        occupied = occupied.add(BigInteger.valueOf(amount));
    }

    public void release(K owner) {
        Long amount = reservations.remove(owner);
        if (amount == null) throw new IllegalStateException("No reservation for this batch");
        occupied = occupied.subtract(BigInteger.valueOf(amount));
    }

    private void validateNewReservation(K owner, long amount) {
        Objects.requireNonNull(owner);
        if (amount < 0) throw new IllegalArgumentException("Negative reservation");
        if (reservations.containsKey(owner)) throw new IllegalStateException("Batch already owns a reservation");
    }
}
