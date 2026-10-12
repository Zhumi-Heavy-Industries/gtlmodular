package com.gtlmodular.client.jade;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gtlmodular.common.machine.display.IndependentThreadDisplay;
import com.gtlmodular.common.machine.display.ThreadDisplaySnapshot;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import snownee.jade.api.BlockAccessor;

import java.util.ArrayList;
import java.util.List;

import static com.gtlmodular.client.jade.NanochipJadeKeys.*;
import static com.gtlmodular.client.jade.NanochipThreadedJadeProvider.*;

/** 新字段只用于显示传输；保留旧键，并将未发送的数值解码为未知。 */
public final class NanochipJadeThreadData {
    private NanochipJadeThreadData() {}

    public static IndependentThreadDisplay displayOf(BlockAccessor accessor) {
        if (accessor == null || !(accessor.getBlockEntity() instanceof MetaMachineBlockEntity blockEntity)
                || !(blockEntity.getMetaMachine() instanceof IRecipeLogicMachine machine)) {
            return null;
        }
        return machine.getRecipeLogic() instanceof IndependentThreadDisplay display ? display : null;
    }

    public static boolean isIndependentThreaded(BlockAccessor accessor) {
        return displayOf(accessor) != null && accessor.getServerData() != null
                && accessor.getServerData().getBoolean(KEY_INDEPENDENT_THREADS);
    }

    static void write(CompoundTag data, ThreadDisplaySnapshot snapshot) {
        putInt(data, KEY_THREAD_LIMIT, snapshot.limit());
        putInt(data, KEY_THREAD_RUNNING, snapshot.running());
        putLong(data, KEY_THREAD_RUNS, snapshot.totalRuns());
        putInt(data, KEY_DISTINCT_RECIPES, snapshot.distinctRecipes());
        putInt(data, KEY_MIRRORED_SLOT, snapshot.mirroredLabel());
        putLong(data, KEY_MIRRORED_PARALLELS, snapshot.mirroredParallels());
        ListTag list = new ListTag();
        for (ThreadDisplaySnapshot.Thread thread : snapshot.threads()) {
            if (list.size() >= OUTPUT_LINES) {
                break;
            }
            CompoundTag entry = new CompoundTag();
            putInt(entry, KEY_SLOT, thread.label());
            putInt(entry, KEY_PROGRESS, thread.progress());
            putInt(entry, KEY_DURATION, thread.duration());
            putLong(entry, KEY_PARALLELS, thread.parallels());
            putLong(entry, KEY_EU_PER_TICK, thread.euPerTick());
            entry.putString(KEY_STATE, thread.state().name());
            if (thread.waitingReason() != null) {
                entry.putString(KEY_WAITING_REASON, thread.waitingReason());
            }
            if (thread.outputId() != null) {
                entry.putString(KEY_OUTPUT_ID, thread.outputId());
                putLong(entry, KEY_OUTPUT_COUNT, thread.outputCount());
            }
            list.add(entry);
        }
        data.put(KEY_THREAD_LIST, list);
        data.putBoolean(KEY_INDEPENDENT_THREADS, true);
    }

    static ThreadDisplaySnapshot read(CompoundTag data) {
        List<ThreadDisplaySnapshot.Thread> rows = new ArrayList<>();
        ListTag list = data.getList(KEY_THREAD_LIST, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size() && i < OUTPUT_LINES; i++) {
            rows.add(readThread(list.getCompound(i)));
        }
        return new ThreadDisplaySnapshot(integer(data, KEY_THREAD_LIMIT), integer(data, KEY_THREAD_RUNNING),
                longValue(data, KEY_THREAD_RUNS), integer(data, KEY_DISTINCT_RECIPES),
                integer(data, KEY_MIRRORED_SLOT), longValue(data, KEY_MIRRORED_PARALLELS), rows);
    }

    static ThreadDisplaySnapshot.Thread readThread(CompoundTag entry) {
        ThreadDisplaySnapshot.State state;
        try {
            state = ThreadDisplaySnapshot.State.valueOf(entry.getString(KEY_STATE));
        } catch (IllegalArgumentException e) {
            state = ThreadDisplaySnapshot.State.UNKNOWN;
        }
        return new ThreadDisplaySnapshot.Thread(integer(entry, KEY_SLOT), longValue(entry, KEY_EU_PER_TICK),
                integer(entry, KEY_DURATION), integer(entry, KEY_PROGRESS), longValue(entry, KEY_PARALLELS),
                state, string(entry, KEY_WAITING_REASON), string(entry, KEY_OUTPUT_ID), longValue(entry, KEY_OUTPUT_COUNT));
    }

    private static Integer integer(CompoundTag tag, String key) {
        return tag.contains(key, Tag.TAG_INT) ? tag.getInt(key) : null;
    }

    private static Long longValue(CompoundTag tag, String key) {
        return tag.contains(key, Tag.TAG_LONG) ? tag.getLong(key) : null;
    }

    private static String string(CompoundTag tag, String key) {
        return tag.contains(key, Tag.TAG_STRING) && !tag.getString(key).isEmpty() ? tag.getString(key) : null;
    }

    private static void putInt(CompoundTag tag, String key, Integer value) {
        if (value != null) {
            tag.putInt(key, value);
        }
    }

    private static void putLong(CompoundTag tag, String key, Long value) {
        if (value != null) {
            tag.putLong(key, value);
        }
    }
}
