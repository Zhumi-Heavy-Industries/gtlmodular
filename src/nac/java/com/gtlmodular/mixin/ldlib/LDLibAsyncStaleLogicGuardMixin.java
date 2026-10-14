package com.gtlmodular.mixin.ldlib;

import com.lowdragmc.lowdraglib.Platform;
import com.lowdragmc.lowdraglib.async.AsyncThreadData;
import com.lowdragmc.lowdraglib.async.IAsyncLogic;
import com.lowdragmc.lowdraglib.syncdata.blockentity.IAsyncAutoSyncBlockEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 重进世界以后，上一个存档的方块实体会留在 LDLib 的异步同步名单里，把服务器线程卡死。
 *
 * LDLib 只在 {@code setRemoved} / {@code clearRemoved} 里调 onInValid / onValid，而整个 level
 * 卸载时方块实体不会被逐个 setRemoved，于是旧实体一直留在 AsyncThreadData.asyncLogics 里。
 * 它们的 asyncTick 会把发包任务交给「当前」服务器执行，却拿**旧的 ServerLevel** 去 getBlockState；
 * 旧 ServerChunkCache 的主线程已经死掉，这次调用被当成非主线程、走 getChunkOffThread 并阻塞在
 * join() 上等一个永远不会完成的加载 —— 服务器线程从此出不来，退世界停在「保存世界」。
 *
 * 这里只做「跳过」，不改任何同步语义：名单属于已经作废的服务器就整条不跑；名单还活着但实体
 * 属于别的 level 就单条不跑。同一 level 的正常实体完全不受影响。
 */
@Mixin(value = AsyncThreadData.class, remap = false)
public abstract class LDLibAsyncStaleLogicGuardMixin {

    @Shadow
    @Final
    private ServerLevel serverLevel;

    @Redirect(method = "searchingTask", require = 1, at = @At(value = "INVOKE",
            target = "Lcom/lowdragmc/lowdraglib/async/IAsyncLogic;asyncTick(J)V"))
    private void gtlmodular$skipLogicFromDeadSession(IAsyncLogic logic, long periodID) {
        MinecraftServer current = Platform.getMinecraftServer();
        if (current == null || this.serverLevel.getServer() != current) {
            return;  // 这份名单属于已经换掉的那一局服务器，整条都别再跑
        }
        if (logic instanceof IAsyncAutoSyncBlockEntity blockEntity
                && blockEntity.getSelf().getLevel() != this.serverLevel) {
            return;  // 实体来自别的 level，用它的 level 去取方块会把服务器线程锁死
        }
        logic.asyncTick(periodID);
    }
}
