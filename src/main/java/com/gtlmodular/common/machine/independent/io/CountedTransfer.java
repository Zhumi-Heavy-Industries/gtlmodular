package com.gtlmodular.common.machine.independent.io;

import java.util.function.LongConsumer;
import java.util.function.LongUnaryOperator;

public final class CountedTransfer {
    private CountedTransfer() {}

    public static long execute(long requested, LongUnaryOperator transfer, LongConsumer recordRemaining) {
        if (requested < 0) throw new IllegalArgumentException("Negative transfer");
        if (requested == 0) return 0;
        long moved = transfer.applyAsLong(requested);
        if (moved < 0 || moved > requested) throw new IllegalStateException("Handler returned an invalid transferred amount");
        // 先记录真实数量，再通知库存监听器；监听器失败也不能让下一次重放整笔。
        recordRemaining.accept(requested - moved);
        return moved;
    }

    public static void notifyRecordedChange(Runnable notification) {
        try {
            notification.run();
        } catch (RuntimeException exception) {
            throw new RecordedNotificationFailure(exception);
        }
    }

    public static final class RecordedNotificationFailure extends RuntimeException {
        private RecordedNotificationFailure(RuntimeException cause) { super(cause); }
    }
}
