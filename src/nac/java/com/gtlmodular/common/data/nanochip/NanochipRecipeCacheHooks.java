package com.gtlmodular.common.data.nanochip;

import net.minecraft.world.level.chunk.LevelChunk;

import java.util.Objects;
import java.util.function.BooleanSupplier;

/** 外部设备可能缓存识别失败结果，由设备所属模组决定如何清理。 */
public final class NanochipRecipeCacheHooks {

    @FunctionalInterface
    public interface Invalidator {
        void invalidateStaleCaches(LevelChunk chunk, BooleanSupplier claimFailureLog);
    }

    // 独立产物没有外部设备适配器，不能因此触碰其他设备或解析其字段。
    private static volatile Invalidator invalidator = (chunk, claimFailureLog) -> {};

    private NanochipRecipeCacheHooks() {
    }

    public static void register(Invalidator adapter) {
        invalidator = Objects.requireNonNull(adapter);
    }

    public static void invalidateStaleCaches(LevelChunk chunk, BooleanSupplier claimFailureLog) {
        invalidator.invalidateStaleCaches(chunk, claimFailureLog);
    }
}
