package com.gtlmodular.common.machine.independent.control;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.function.BooleanSupplier;

public final class IndependentMovement {
    private record Location(Level level, BlockPos pos) {}
    private static final ThreadLocal<Location> CURRENT = new ThreadLocal<>();

    private IndependentMovement() {}

    public static boolean moving(Level level, BlockPos pos) {
        return new Location(level, pos).equals(CURRENT.get());
    }

    public static boolean move(Level level, BlockPos pos, BooleanSupplier operation) {
        Location previous = CURRENT.get();
        CURRENT.set(new Location(level, pos.immutable()));
        try {
            return operation.getAsBoolean();
        } finally {
            if (previous == null) CURRENT.remove();
            else CURRENT.set(previous);
        }
    }
}
