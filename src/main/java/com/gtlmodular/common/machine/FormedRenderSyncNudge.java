package com.gtlmodular.common.machine;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gtlmodular.GTLModular;

/** 舱室外观依赖控制器名单；名单同步晚于区块烘焙时，需要在有界窗口内补发同步和重绘。 */
final class FormedRenderSyncNudge {

    /** 窗口覆盖控制器重成形、名单同步和客户端烘焙，避免长期补发占用带宽。 */
    static final int WINDOW_TICKS = 160;

    static final int INTERVAL = 20;

    private FormedRenderSyncNudge() {
    }

    static int tick(MetaMachine machine, int ticksLeft, String who) {
        if (ticksLeft <= 0 || machine.isRemote()) {
            return 0;
        }
        if (!(machine instanceof IMultiPart part)) {
            return 0;
        }
        if (part.getControllers().isEmpty()) {
            return ticksLeft - 1;
        }
        int left = ticksLeft - 1;
        if (left % INTERVAL == 0) {
            machine.getSyncStorage().markAllDirty();
            machine.scheduleRenderUpdate();
            GTLModular.LOGGER.info("[gtlmodular] 成型同步补丁: {} pos={} 控制器数={} 剩余窗口={}",
                    who, machine.getPos(), part.getControllers().size(), left);
        }
        return left;
    }
}
