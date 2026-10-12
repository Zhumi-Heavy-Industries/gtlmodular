package com.gtlmodular.common.event;

import com.gtlmodular.common.data.nanochip.NanochipModContext;
import com.gtlmodular.common.machine.nanochip.NanochipStructurePosIndex;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** 挖掘事件发生在实际移除之前，只记坐标并延后重判，避免旧世界结果污染同 tick 检查。 */
public final class NanochipBlockRemovalHandler {

    private NanochipBlockRemovalHandler() {
    }

    /** 在较低优先级读取取消状态，避免把被保护插件阻止的挖掘计入待办。 */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        // 原版模式无需维护本地失效链，提前返回以省去索引处理。
        if (NanochipModContext.config().isNanochipOriginalMode()) {
            return;
        }
        if (event == null || event.isCanceled()) {
            return;
        }
        if (!(event.getLevel() instanceof Level level) || level.isClientSide) {
            return;
        }
        NanochipStructurePosIndex.noteBlockRemoved(level, event.getPos());
    }

    /** 玩家挖掘事件不覆盖爆炸，需另行登记即将移除的坐标。 */
    @SubscribeEvent
    public static void onExplosionDetonate(net.minecraftforge.event.level.ExplosionEvent.Detonate event) {
        if (NanochipModContext.config().isNanochipOriginalMode()) {
            return;
        }
        if (event == null || event.getLevel() == null || event.getLevel().isClientSide) {
            return;
        }
        var affected = event.getAffectedBlocks();
        if (affected == null || affected.isEmpty()) {
            return;
        }
        for (net.minecraft.core.BlockPos pos : affected) {
            NanochipStructurePosIndex.noteBlockRemoved(event.getLevel(), pos);
        }
    }

    /** 世界卸载时主动清除索引；只处理服务端，避免客户端卸载清掉同维度键的服务端数据。 */
    @SubscribeEvent
    public static void onLevelUnload(net.minecraftforge.event.level.LevelEvent.Unload event) {
        if (event == null || !(event.getLevel() instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
            return;
        }
        NanochipStructurePosIndex.forgetDimension(serverLevel.dimension());
    }
}
