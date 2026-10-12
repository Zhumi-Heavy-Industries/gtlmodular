package com.gtlmodular.common.machine.display;

import java.util.List;

/** 不持有活配方或批次引用，避免传输和绘制期间读到不同 tick 的字段。 */
public record ThreadDisplaySnapshot(Integer limit, Integer running, Long totalRuns, Integer distinctRecipes,
                                    Integer mirroredLabel, Long mirroredParallels, List<Thread> threads) {
    public ThreadDisplaySnapshot {
        threads = List.copyOf(threads);
    }

    public enum State { WORKING, WAITING, UNKNOWN }

    /** 可空读数表示服务端未提供，不能把缺数据解释成零耗能或零进度。 */
    public record Thread(Integer label, Long euPerTick, Integer duration, Integer progress, Long parallels,
                         State state, String waitingReason, String outputId, Long outputCount) {
        public Thread {
            state = state == null ? State.UNKNOWN : state;
        }
    }
}
