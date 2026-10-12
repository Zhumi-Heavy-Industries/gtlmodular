package com.gtlmodular.common.machine.independent.io;

import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

public final class EnergyDebitContext implements AutoCloseable {
    public interface ObservedContainer {}
    private static final ThreadLocal<EnergyDebitContext> CURRENT = new ThreadLocal<>();
    private final Object container;
    private final long before;
    private final long requested;
    private final boolean input;
    private final LongConsumer record;
    private boolean recorded;

    private EnergyDebitContext(Object container, long before, long requested, boolean input, LongConsumer record) {
        this.container = container;
        this.before = before;
        this.requested = requested;
        this.input = input;
        this.record = record;
    }

    public static EnergyDebitContext open(Object container, long before, long requested, boolean input, LongConsumer record) {
        if (CURRENT.get() != null) throw new IllegalStateException("Nested energy IO");
        var context = new EnergyDebitContext(container, before, requested, input, record);
        CURRENT.set(context);
        return context;
    }

    public static void changed(Object container, long stored) {
        var context = CURRENT.get();
        if (context == null || context.container != container || context.recorded) return;
        long moved = context.input ? Math.subtractExact(context.before, stored) : Math.subtractExact(stored, context.before);
        if (moved < 0 || moved > context.requested) throw new IllegalStateException("Invalid observed energy debit");
        context.record.accept(context.requested - moved);
        context.recorded = true;
    }

    public long execute(LongSupplier operation) {
        long moved = operation.getAsLong();
        if (moved < 0 || moved > requested) throw new IllegalStateException("Invalid energy receipt");
        if (!recorded) {
            if (moved != 0) throw new IllegalStateException("Energy observer did not run");
            record.accept(requested);
        }
        return moved;
    }

    @Override public void close() { CURRENT.remove(); }
}
