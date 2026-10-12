package com.gtlmodular.common.thread;

import java.util.Optional;

public interface BatchCodec<P, E> {
    // payload 的快照由适配器负责，内核无法假设外部对象的复制语义。
    E encode(P payload);

    // 无法恢复的记录可返回空值，避免一条坏记录阻止其余已扣料批次恢复。
    Optional<P> decode(E encoded);
}
