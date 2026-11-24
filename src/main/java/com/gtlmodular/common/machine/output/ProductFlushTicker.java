package com.gtlmodular.common.machine.output;

import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.stacks.AEKey;

import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * 把聚合表的刷写挂到 AE2 的自适应心跳上（对照 AE2 {@code TickManagerService} 与 gtlcore 的 {@code MEExtended*$Ticker}）。
 *
 * <p>约定：</p>
 * <ul>
 *   <li>空表 → {@link TickRateModulation#SLEEP}，完全不占 tick；</li>
 *   <li>有积压且允许尝试 → 刷一批，刷不完就 {@link TickRateModulation#SAME}，刷空 → {@link TickRateModulation#IDLE}；</li>
 *   <li>下游拒收 → 交给 {@link FlushBackoff}，下一次尝试前返回 {@link TickRateModulation#IDLE}。</li>
 * </ul>
 *
 * <p>用法：机器在节点上 {@code node.addService(IGridTickable.class, new ProductFlushTicker(...))}。</p>
 */
public final class ProductFlushTicker implements IGridTickable {
    private final ProductTable<AEKey> table;
    private final Supplier<ProductFlusher.Sink<AEKey>> sink;
    private final LongSupplier gameTime;
    private final ProductFlusher.Budget budget;
    private final FlushBackoff backoff = new FlushBackoff();

    public ProductFlushTicker(ProductTable<AEKey> table,
                              Supplier<ProductFlusher.Sink<AEKey>> sink,
                              LongSupplier gameTime,
                              ProductFlusher.Budget budget) {
        if (table == null || sink == null || gameTime == null || budget == null) {
            throw new IllegalArgumentException("ProductFlushTicker 的参数不能为 null");
        }
        this.table = table;
        this.sink = sink;
        this.gameTime = gameTime;
        this.budget = budget;
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        // AE2 15.x（1.20.1）的 TickingRequest 是四元 record：min、max、是否睡眠、是否允许被唤醒。
        // 允许唤醒：入表后机器调用 ITickManager#alertDevice(node) 即可把睡着的刷写叫起来。
        return new TickingRequest(2, 40, table.isEmpty(), true);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        if (table.isEmpty()) {
            return TickRateModulation.SLEEP;
        }
        long now = gameTime.getAsLong();
        if (!backoff.shouldAttempt(now)) {
            return TickRateModulation.IDLE;
        }
        ProductFlusher.Sink<AEKey> currentSink = sink.get();
        if (currentSink == null) {
            backoff.onFailure(now);
            return TickRateModulation.SLOWER;
        }
        ProductFlusher.Result result = ProductFlusher.flush(table, currentSink, budget);
        if (result.flushedAmount() > 0) {
            backoff.onSuccess();
            return result.hasLeftover() ? TickRateModulation.SAME : TickRateModulation.IDLE;
        }
        backoff.onFailure(now);
        return TickRateModulation.SLOWER;
    }
}
