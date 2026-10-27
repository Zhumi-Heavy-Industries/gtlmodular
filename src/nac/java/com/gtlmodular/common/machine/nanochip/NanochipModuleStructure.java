package com.gtlmodular.common.machine.nanochip;

import com.gtlmodular.common.machine.nanochip.registration.NanochipModuleSpec;
import com.gtlmodular.common.data.nanochip.NanochipModContext;
import com.gregtechceu.gtceu.api.pattern.MultiblockState;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.function.BooleanSupplier;
import java.util.function.LongConsumer;

import static com.gtlmodular.common.machine.nanochip.NanochipModuleMachine.NAC_MODULE_ACTIVE_CONNECT;

/**
 * 模块结构层：成型判定完全交上游。
 *
 * <p>本类只做三件事：把三个 {@code checkPattern} 入口原样转给上游、状态翻转时打一行日志、
 * 把「还没成型」的逐格原因按限流报出来。它<b>不参与</b>成形/失效判定 ——
 * 结构定义（{@code NanochipComplexPattern}）是成形判据的唯一来源；
 * 运行条件（复合体在不在、外壳档够不够）走配方修饰器与运行前检查。
 *
 * <p>为什么不再自己判：结构预检/节流/变化闸会在上游真正匹配之前就 {@code return false}，
 * 表现是「结构摆对了机器也不成形」，而且玩家只能看到我们打的一行原因。
 */
final class NanochipModuleStructure {

    private final NanochipModuleMachine module;
    private final NanochipModuleSpec kind;
    private final BooleanSupplier upstreamTryLock;
    private final BooleanSupplier upstreamLock;
    private final LongConsumer upstreamAsyncCheck;

    private static final long UNFORMED_LOG_INTERVAL_TICKS = 200L;

    /** 时间差用于限流，不能以 Long.MIN_VALUE 作哨兵而让减法溢出。 */
    private long lastUnformedLogTick = -10_000L;

    private final NanochipStructureLogGate structureLogGate = new NanochipStructureLogGate();

    private static final String LOG_CONCLUSION_FORMED = "已成形";

    private static final String LOG_CONCLUSION_INVALID = "结构失效";

    private static final String LOG_CONCLUSION_UNFORMED = "尚未成形";

    private boolean loggedFormed;

    private ServerPlayer pendingRefreshRequester;

    NanochipModuleStructure(NanochipModuleMachine module, NanochipModuleSpec kind,
                            BooleanSupplier upstreamTryLock, BooleanSupplier upstreamLock,
                            LongConsumer upstreamAsyncCheck) {
        this.module = module;
        this.kind = kind;
        this.upstreamTryLock = upstreamTryLock;
        this.upstreamLock = upstreamLock;
        this.upstreamAsyncCheck = upstreamAsyncCheck;
    }

    boolean checkPatternWithTryLock() {
        return upstreamTryLock.getAsBoolean();
    }

    boolean checkPatternWithLock() {
        return upstreamLock.getAsBoolean();
    }

    void asyncCheckPattern(long periodID) {
        upstreamAsyncCheck.accept(periodID);
        if (!module.isFormed()) {
            logUnformedReason();
        }
    }

    /** 手动刷新按钮：只把本机排回上游异步检查队列，成形与否仍由上游判。 */
    void requestStructureRefresh(ServerPlayer requester) {
        this.pendingRefreshRequester = requester;
        this.lastUnformedLogTick = -10_000L;
        MultiblockState state = module.getMultiblockState();
        if (module.isFormed() && (state == null || !state.hasError())) {
            reportRefreshResult();
            return;
        }
        if (requester != null) {
            requester.sendSystemMessage(Component.translatable(NanochipStructureRefresh.LANG_REQUESTED)
                    .withStyle(ChatFormatting.YELLOW));
        }
    }

    private void reportRefreshResult() {
        ServerPlayer requester = this.pendingRefreshRequester;
        if (requester == null) {
            return;
        }
        this.pendingRefreshRequester = null;
        if (module.isFormed()) {
            requester.sendSystemMessage(Component.translatable(NanochipStructureRefresh.LANG_OK)
                    .withStyle(ChatFormatting.GREEN));
            return;
        }
        requester.sendSystemMessage(Component.translatable(NanochipStructureRefresh.LANG_FAIL)
                .withStyle(ChatFormatting.RED));
        String detail = NanochipAssemblyComplexMachine.describeStructureFailure(module.getMultiblockState());
        requester.sendSystemMessage(Component.literal("§c· §7" + kind.nameZh() + " @"
                + module.getPos().toShortString() + "：" + (detail == null
                ? "GTCEu 没留下逐格原因：数量类失败 / 区块未加载等路径不写 error"
                : detail)));
    }

    private void logUnformedReason() {
        Level lv = module.getLevel();
        if (lv == null || lv.isClientSide || module.isFormed()) {
            return;
        }
        long now = lv.getGameTime();
        if (now - this.lastUnformedLogTick < UNFORMED_LOG_INTERVAL_TICKS) {
            return;
        }
        this.lastUnformedLogTick = now;
        String detail = NanochipAssemblyComplexMachine.describeStructureFailure(module.getMultiblockState());
        String reason = detail == null
                ? "GTCEu 没留下逐格原因：数量类失败 / 区块未加载等路径不写 error"
                : detail;
        // 多台模块可能长期报告同一错误，合并日志以减少重复写盘。
        if (!this.structureLogGate.shouldEmitInfo(LOG_CONCLUSION_UNFORMED, reason,
                structureLogLabel(), now)) {
            if (NanochipModContext.logger().isDebugEnabled()) {
                NanochipModContext.logger().debug("[gtlmodular] NAC 模块 尚未成形 @{}（{}）：{}",
                        module.getPos(), kind.nameZh(), reason);
            }
            return;
        }
        NanochipModContext.logger().info("[gtlmodular] NAC 模块 尚未成形 @{}（{}）：{}",
                module.getPos(), kind.nameZh(), reason);
    }

    private String structureLogLabel() {
        return kind.nameZh() + "@" + module.getPos().toShortString();
    }

    void clearFormedError() {
        // 外部结构提示读取错误状态，成形后须清除残留错误以免仍显示结构不完整。
        MultiblockState formedState = module.getMultiblockState();
        if (formedState != null && formedState.hasError()) {
            formedState.setError(null);
        }
    }

    void logFormed(boolean linked) {
        if (!loggedFormed) {
            loggedFormed = true;
            Level formedLogLevel = module.getLevel();
            long formedLogNow = formedLogLevel == null ? 0L : formedLogLevel.getGameTime();
            if (this.structureLogGate.shouldEmitInfo(LOG_CONCLUSION_FORMED, "",
                    structureLogLabel(), formedLogNow)) {
                NanochipModContext.logger().info("[gtlmodular] NAC 模块 已成形 @{}（{}，复合体连接={}）"
                                + "{}",
                        module.getPos(), kind.nameZh(), linked ? "是" : "否",
                        linked ? "" : (NAC_MODULE_ACTIVE_CONNECT
                                ? "（复合体尚未成形或还没把本机登记 —— 连接由复合体按结构 F 位注入，"
                                + "本机已催促其立刻重算一次，最坏 1 秒内生效）"
                                : "（复合体尚未成形或还没把本机登记 —— 连接由复合体自己按结构 F 位注入，"
                                + "它每 20 tick 自查一次 ⇒ 最坏 1 秒内生效）"));
            }
        }
    }

    void resetLogGate() {
        this.structureLogGate.reset();
    }

    void onStructureInvalid() {
        if (loggedFormed) {
            loggedFormed = false;
            String detail = NanochipAssemblyComplexMachine.describeStructureFailure(module.getMultiblockState());
            String invalidReason = detail == null ? "" : detail;
            Level invalidLogLevel = module.getLevel();
            long invalidLogNow = invalidLogLevel == null ? 0L : invalidLogLevel.getGameTime();
            if (this.structureLogGate.shouldEmitInfo(LOG_CONCLUSION_INVALID, invalidReason,
                    structureLogLabel(), invalidLogNow)) {
                NanochipModContext.logger().info("[gtlmodular] NAC 模块 结构失效 @{}（{}，复合体连接={}；"
                                + "只在状态翻转时打这条，避免刷屏）{}",
                        module.getPos(), kind.nameZh(), module.hostConnected() ? "是" : "否",
                        detail == null ? "" : "；GTCEu 首个不符处：" + detail);
            }
        }
    }
}
