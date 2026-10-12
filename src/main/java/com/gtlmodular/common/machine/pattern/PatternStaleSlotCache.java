package com.gtlmodular.common.machine.pattern;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gtlmodular.GTLModular;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import net.minecraft.core.BlockPos;

import java.util.Arrays;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

/**
 * 上游新搜索跳过已缓存的槽，槽缓存里若是本槽身份闸拒绝的配方（例如电路与样板不符），
 * 这个槽就永远开不了工，必须作废后让上游重新搜索。
 */
public final class PatternStaleSlotCache {

    private static final long LOG_INTERVAL_MS = 30_000L;

    private final IntSet pending = new IntOpenHashSet();

    private long lastLogMs;

    public void noteRejected(int slot, GTRecipe recipe, Int2ReferenceMap<ObjectSet<GTRecipe>> cache) {
        if (slot < 0 || recipe == null || cache == null) {
            return;
        }
        ObjectSet<GTRecipe> cached = cache.get(slot);
        if (cached != null && cached.contains(recipe)) {
            pending.add(slot);
        }
    }

    /** 拒绝发生在上游遍历槽缓存的过程中，当场改缓存会破坏遍历，只能留到部件自己的 tick 作废。 */
    public void drain(IntConsumer invalidate, Supplier<BlockPos> position) {
        if (pending.isEmpty()) {
            return;
        }
        int[] slots = pending.toIntArray();
        pending.clear();
        for (int slot : slots) {
            invalidate.accept(slot);
        }
        long now = System.currentTimeMillis();
        if (now - lastLogMs >= LOG_INTERVAL_MS) {
            lastLogMs = now;
            GTLModular.LOGGER.info("[gtlmodular] 槽 {} 缓存的配方被本槽身份闸拒绝，已作废该槽配方缓存并重新搜索 @{}",
                    Arrays.toString(Arrays.stream(slots).map(slot -> slot + 1).toArray()),
                    position.get());
        }
    }
}
