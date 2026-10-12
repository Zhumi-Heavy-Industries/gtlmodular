package com.gtlmodular.mixin.gtceu;

import com.gtlmodular.client.jade.NanochipJadeKeys;
import com.gtlmodular.common.data.nanochip.NanochipModContext;
import com.gtlmodular.common.diag.SwallowedErrors;
import com.gtlmodular.client.jade.NanochipJadeThreadRows;
import com.gtlmodular.client.jade.NanochipJadeThreadData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 原生进度仅代表一个批次，自绘模式需取消该 provider，避免与逐线程显示重复。 */
@Mixin(targets = "com.gregtechceu.gtceu.integration.jade.provider.WorkableBlockProvider", remap = false)
public class MixinWorkableProgressBar {

    private static int diagBudget = 3;

    /** 总量限制不能防止日志瞬间集中输出，因此再用时间闸限制频率。 */
    private static long diagNextAt;

    private static final long DIAG_INTERVAL_MS = 30_000L;

    private static final String KEY_PROGRESS = "Progress";

    private static final String KEY_MAX_PROGRESS = "MaxProgress";

    /** 先判断显示模式，保留使用原生条时的入口；判定失败不取消，避免提示完全消失。 */
    @Inject(method = "addTooltip", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void gtlmodular$hideSingleRecipeBar(CompoundTag capData, snownee.jade.api.ITooltip tooltip,
                                                net.minecraft.world.entity.player.Player player,
                                                snownee.jade.api.BlockAccessor accessor,
                                                BlockEntity blockEntity,
                                                snownee.jade.api.config.IPluginConfig config,
                                                CallbackInfo ci) {
        try {
            if (!NanochipJadeThreadRows.OUR_OWN_BARS || accessor == null) {
                return;
            }
            if (!isOurThreadedMachine(accessor)) {
                return;
            }
            CompoundTag data = accessor.getServerData();
            if (data == null || !data.contains(NanochipJadeKeys.KEY_THREAD_LIMIT)) {
                return;
            }
            ci.cancel();
            diagLog("[gtlmodular] Jade 显示：自绘形态 —— 已取消 GTCEu 原生单配方进度条，"
                    + "多条进度条由 NanochipThreadedJadeProvider 逐条线程画出（原生条已取消={}）", true);
        } catch (Throwable ignored) {
            SwallowedErrors.report("MixinWorkableProgressBar#gtlmodular$hideSingleRecipeBar", ignored);
        }
    }

    @Inject(method = "addTooltip", at = @At("TAIL"), require = 0, remap = false)
    private void gtlmodular$appendOtherThreadBars(CompoundTag capData, snownee.jade.api.ITooltip tooltip,
                                                 net.minecraft.world.entity.player.Player player,
                                                 snownee.jade.api.BlockAccessor accessor,
                                                 BlockEntity blockEntity,
                                                 snownee.jade.api.config.IPluginConfig config,
                                                 CallbackInfo ci) {
        try {
            if (NanochipJadeThreadRows.OUR_OWN_BARS
                    || !NanochipJadeThreadRows.NATIVE_BAR_FOR_FIRST_THREAD
                    || tooltip == null || accessor == null) {
                return;   // 自绘路径已显示全部线程，此处不能再次追加。
            }
            if (!isOurThreadedMachine(accessor)) {
                return;
            }
            CompoundTag data = accessor.getServerData();
            if (data == null || !data.contains(NanochipJadeKeys.KEY_THREAD_LIMIT)) {
                return;
            }
            boolean nativeBarDrawn = hasProgressKeys(capData) || hasProgressKeys(data);
            NanochipJadeThreadRows.appendAfterNativeBar(tooltip, data, nativeBarDrawn);
            diagLog("[gtlmodular] Jade 显示：回退形态 —— 原生条保留，其余线程的条已逐行追加在其下方"
                    + "（原生条是否已画={}）", nativeBarDrawn);
        } catch (Throwable ignored) {
            SwallowedErrors.report("MixinWorkableProgressBar#gtlmodular$appendOtherThreadBars", ignored);
        }
    }

    private static void diagLog(String message, boolean nativeBarDrawn) {
        if (diagBudget <= 0) {
            return;
        }
        long nowMs = System.currentTimeMillis();
        if (nowMs < diagNextAt) {
            return;
        }
        diagBudget--;
        diagNextAt = nowMs + DIAG_INTERVAL_MS;
        try {
            NanochipModContext.logger().info(message, nativeBarDrawn);
        } catch (Throwable ignored) {
            SwallowedErrors.report("MixinWorkableProgressBar#diagLog", ignored);
            // 日志失败不能改变已完成的取消或追加行为。
        }
    }

    private static boolean hasProgressKeys(CompoundTag tag) {
        return tag != null && (tag.contains(KEY_PROGRESS) || tag.contains(KEY_MAX_PROGRESS));
    }

    /** 服务端明确发布独立批次后才替换原生条，未接入模块及首帧保留上游显示。 */
    private static boolean isOurThreadedMachine(snownee.jade.api.BlockAccessor accessor) {
        return NanochipJadeThreadData.isIndependentThreaded(accessor);
    }
}
