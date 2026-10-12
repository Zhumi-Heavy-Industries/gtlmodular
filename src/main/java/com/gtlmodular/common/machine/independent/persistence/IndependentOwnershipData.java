package com.gtlmodular.common.machine.independent.persistence;

import com.gtlmodular.common.machine.independent.control.BatchOwnershipLedger;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.UUID;
import java.util.function.Predicate;

public final class IndependentOwnershipData extends SavedData {
    private final BatchOwnershipLedger ledger;

    private IndependentOwnershipData() { ledger = new BatchOwnershipLedger(java.util.Map.of()); }

    private IndependentOwnershipData(CompoundTag tag) {
        var entries = new HashMap<UUID, BatchOwnershipLedger.Entry>();
        for (Tag value : tag.getList("tokens", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) value;
            boolean spent = entry.getBoolean("spent");
            entries.put(entry.getUUID("token"), new BatchOwnershipLedger.Entry(spent ? null
                    : new BatchOwnershipLedger.Owner(entry.getString("dimension"), entry.getLong("position")), spent));
        }
        ledger = new BatchOwnershipLedger(entries);
    }

    public static IndependentOwnershipData get(ServerLevel level) {
        // 物品和 BG 都能跨维度移动，所有维度必须共用一次性令牌账本。
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                IndependentOwnershipData::new, IndependentOwnershipData::new, "gtlmodular_independent_ownership");
    }

    public UUID issue(BatchOwnershipLedger.Owner owner) {
        UUID token = ledger.issue(owner);
        setDirty();
        return token;
    }

    public BatchOwnershipLedger.Entry entry(UUID token) { return ledger.entry(token); }

    public UUID claim(UUID token, BatchOwnershipLedger.Owner owner, Predicate<BatchOwnershipLedger.Owner> sourceVacant) {
        UUID replacement = ledger.claim(token, owner, sourceVacant);
        if (replacement != null) setDirty();
        return replacement;
    }

    public void retire(UUID token) { ledger.retire(token); setDirty(); }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag entries = new ListTag();
        ledger.snapshot().forEach((token, record) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("token", token);
            entry.putBoolean("spent", record.spent());
            if (!record.spent()) {
                entry.putString("dimension", record.owner().dimension());
                entry.putLong("position", record.owner().position());
            }
            entries.add(entry);
        });
        tag.put("tokens", entries);
        return tag;
    }
}
