package com.gtlmodular.common.machine.nanochip;

import com.gtlmodular.common.data.nanochip.NanochipModContext;

/** 重复全量匹配会长期占用结构锁，因此对周期兜底检查限频。 */
public final class NanochipStructureCheckThrottle {

    /** 未成形侧已有预检和变化闸，这层不再增加等待。 */
    public static final int UNFORMED_SKIP = 1;

    /**
     * 已成形的方块变更走事件入口，此处只限制周期兜底扫描。
     * 必须与上游 asyncCheckPattern 的 4 相位过滤互质，否则一部分机器的周期检查永远落不到相位上。
     */
    public static final int FORMED_SKIP = 5;

    private int seen;

    public boolean shouldRun(boolean formed) {
        if (NanochipModContext.config().isNanochipOriginalMode()) {
            return true;
        }
        int skip = formed ? FORMED_SKIP : UNFORMED_SKIP;
        return (++this.seen % skip) == 0;
    }

    public void reset() {
        this.seen = 0;
    }
}
