package com.gtlmodular.common.machine.nanochip;

/** 独立于配方 tick 计时，暂停和搜索退订时也能留下停工证据。 */
final class NanochipStallWatch {
    private static final long DELAY = 200;
    private static final long INTERVAL = 1200;
    private long pendingSince = -1;
    private long lastReport = -1;
    private long lastTick = -1;
    private long opened;
    private String materials;

    boolean observe(long now, String stock, boolean present, boolean complete, long openedTotal) {
        if (now < lastTick) {
            pendingSince = -1;
            lastReport = -1;
            materials = null;
        }
        lastTick = now;
        boolean changed = !stock.equals(materials);
        materials = stock;
        if (openedTotal != opened || (!present && complete)) pendingSince = -1;
        // 连续到料不能不断推迟期限，否则持续推料的故障永远没有日志。
        if (present && pendingSince < 0) pendingSince = now;
        opened = openedTotal;
        return changed && present;
    }

    boolean reportDue(long now) {
        if (pendingSince < 0 || now - pendingSince < DELAY
                || (lastReport >= 0 && now - lastReport < INTERVAL)) return false;
        lastReport = now;
        return true;
    }

    long waitingTicks(long now) {
        return pendingSince < 0 ? 0 : Math.max(0, now - pendingSince);
    }
}
