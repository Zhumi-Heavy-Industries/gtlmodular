package com.gtlmodular.common.machine.independent.control;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

public final class BatchOwnershipLedger {
    public record Owner(String dimension, long position) {}
    public record Entry(Owner owner, boolean spent) {}

    private final Map<UUID, Entry> entries = new HashMap<>();

    public BatchOwnershipLedger(Map<UUID, Entry> saved) { entries.putAll(saved); }

    public Map<UUID, Entry> snapshot() { return Map.copyOf(entries); }
    public Entry entry(UUID token) { return entries.get(token); }

    public UUID issue(Owner owner) {
        UUID token;
        do { token = UUID.randomUUID(); } while (entries.containsKey(token));
        entries.put(token, new Entry(owner, false));
        return token;
    }

    public UUID claim(UUID token, Owner destination, Predicate<Owner> sourceVacant) {
        Entry entry = entries.get(token);
        if (entry != null && (entry.spent()
                || !entry.owner().equals(destination) && !sourceVacant.test(entry.owner()))) return null;
        // 旧掉落物已有 UUID 但尚未进入世界账本，也只能兑换一次；换新 UUID 防止原坐标重放。
        retire(token);
        return issue(destination);
    }

    public void retire(UUID token) {
        // 已兑现的令牌必须留作墓碑，否则旧物品或旧方块 NBT 会再次变成可领用的旧版记录。
        entries.put(token, new Entry(null, true));
    }
}
