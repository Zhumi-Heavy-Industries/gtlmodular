package com.gtlmodular.common.machine.output;

import java.util.List;

/**
 * 把 {@link ProductTable} 里的积压批量刷进下游（一般是 ME 网络）。
 *
 * <p>三条硬规矩，全部来自实际踩坑：</p>
 * <ol>
 *   <li>按下游<b>实际接受量</b>扣减，绝不假设全成功；</li>
 *   <li>一次刷写有<b>条目数上限</b>和<b>总量上限</b>，避免一个大 key 占满整 tick；</li>
 *   <li>一旦遇到拒收就停手，交给退避（{@link FlushBackoff}）下一轮再试，不做无谓重试。</li>
 * </ol>
 *
 * <p>下游用函数式接口注入，纯逻辑可单测，不依赖 AE2 类型。</p>
 */
public final class ProductFlusher {
    private ProductFlusher() {}

    /** 一次刷写的上限。 */
    public record Budget(int maxEntries, long maxAmount) {
        public Budget {
            if (maxEntries <= 0) {
                throw new IllegalArgumentException("maxEntries 必须为正");
            }
            if (maxAmount <= 0) {
                throw new IllegalArgumentException("maxAmount 必须为正");
            }
        }
    }

    /** 一次刷写的结果。 */
    public record Result(int attempted, int flushedEntries, long flushedAmount, boolean hasLeftover) {}

    /** 下游写入；返回真正接受的数量（0 到 amount 之间）。 */
    @FunctionalInterface
    public interface Sink<K> {
        long insert(K key, long amount);
    }

    public static <K> Result flush(ProductTable<K> table, Sink<K> sink, Budget budget) {
        List<ProductTable.Entry<K>> batch = table.peek(budget.maxEntries());
        long amountLeft = budget.maxAmount();
        int attempted = 0;
        int flushedEntries = 0;
        long flushedAmount = 0;

        for (ProductTable.Entry<K> entry : batch) {
            if (amountLeft <= 0) {
                break;
            }
            long want = Math.min(entry.amount(), amountLeft);
            attempted++;
            long accepted = sink.insert(entry.key(), want);
            if (accepted < 0 || accepted > want) {
                throw new IllegalStateException(
                        "下游返回了非法接受量: key=" + entry.key() + " 请求=" + want + " 接受=" + accepted);
            }
            if (accepted > 0) {
                table.subtract(entry.key(), accepted);
                amountLeft -= accepted;
                flushedAmount += accepted;
                flushedEntries++;
            }
            if (accepted < want) {
                // 下游拒收（满了/断网），本轮到此为止，剩下的靠退避再来。
                break;
            }
        }

        return new Result(attempted, flushedEntries, flushedAmount, !table.isEmpty());
    }
}
