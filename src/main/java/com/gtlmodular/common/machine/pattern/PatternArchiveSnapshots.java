package com.gtlmodular.common.machine.pattern;

import com.gtlmodular.common.machine.LazyPatternArchiveCodec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

public final class PatternArchiveSnapshots {

    private PatternArchiveSnapshots() {
    }

    // 元信息和设置先由机器取快照，避免改变容量读取与类型缓存初始化的先后关系。
    public static LazyPatternArchiveCodec.Payload buildPayload(
            LazyPatternArchiveCodec.Payload payload, int capacity,
            IntFunction<ItemStack> patternAt, IntFunction<Integer> recipeTypeAt,
            IntFunction<ResourceLocation> manualTypeAt, IntFunction<CompoundTag> catalystAt) {
        int highest = -1;
        for (int i = 0; i < capacity; i++) {
            ItemStack stack;
            try {
                stack = patternAt.apply(i);
            } catch (Throwable ignored) {
                // 单槽读取失败不应丢掉其他槽的样板。
                continue;
            }
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            Integer recipeType = recipeTypeAt.apply(i);
            ResourceLocation manual = manualTypeAt.apply(i);
            CompoundTag catalyst = catalystAt.apply(i);
            payload.entries.add(new LazyPatternArchiveCodec.Entry(i, stack.copy(), recipeType, manual, catalyst));
            if (i > highest) {
                highest = i;
            }
        }
        payload.slotCount = highest + 1;
        payload.patternCount = payload.entries.size();
        return payload;
    }

    // 延迟读取可保留两种仓的访问方式，以及前一步失败后不再读取后续内容的边界。
    public static CompoundTag buildCatalyst(Consumer<CompoundTag> contentsSnapshot,
                                           IntSupplier cacheCount, int defaultCount) {
        CompoundTag entry = new CompoundTag();
        contentsSnapshot.accept(entry);
        int count = cacheCount.getAsInt();
        if (count != defaultCount) {
            entry.putByte("count", (byte) count);
        }
        return entry.isEmpty() ? null : entry;
    }

    public static int[] summary(int capacity, IntFunction<ItemStack> patternAt) {
        int count = 0;
        int min = -1;
        int max = -1;
        for (int i = 0; i < capacity; i++) {
            ItemStack stack;
            try {
                stack = patternAt.apply(i);
            } catch (Throwable ignored) {
                continue;
            }
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            count++;
            if (min < 0) {
                min = i;
            }
            max = i;
        }
        return new int[]{count, min + 1, max + 1};
    }

    public static String dimensionId(Supplier<Level> levelSource) {
        try {
            Level level = levelSource.get();
            return level == null ? "" : level.dimension().location().toString();
        } catch (Throwable ignored) {
            return "";
        }
    }
}
