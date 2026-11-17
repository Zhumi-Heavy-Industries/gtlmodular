// Ported from GT-Shanhai (https://github.com/dishanhai/gtl_shanhai-dishanhai),
// (c) 山海恒长在 (dishanhai), LGPL-3.0. Ported with the author's permission.
package com.gtlmodular.common.item.terminal;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import com.gtlmodular.common.item.ShanhaiUltimateTerminalConfig;
import com.gtlmodular.network.ShanhaiStructureHighlightPacket;
import com.gregtechceu.gtceu.api.block.MetaMachineBlock;
import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.MultiblockState;
import com.gregtechceu.gtceu.api.pattern.error.PatternError;
import com.gregtechceu.gtceu.api.pattern.error.PatternStringError;
import com.gregtechceu.gtceu.api.pattern.error.SinglePredicateError;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * 哦鲸鲸终端「按住 Ctrl 右击多方块主机 = 检测结构成型失败原因」。
 *
 * <p>判定口径与上游 gtlcore 1.2.3.3 的 {@code StructureDetection} 一致：按控制器朝向逐个
 * {@code checkPatternAt}，报出第一处不合法位置与它需要的候选方块。镜像模式与重复层数取自
 * 终端自身设置（与扫描/施工同一份配置），因此「检测报的错」和「施工会补的位置」是同一套标准。
 *
 * <p><b>为什么重复层数走反射</b>：把重复层数喂给 GTCEu 结构匹配的钩子是 gtlcore 1.2.3.3 才有的
 * {@code StructureDetectionScope}（ThreadLocal 作用域，匹配期间生效）。旧版 gtlcore 没有这个类，
 * 直接引用会在加载期抛 {@code NoClassDefFoundError}，所以这里只在类存在时才套作用域；
 * 没有时退化成「按结构固定层数检测」，不会崩。
 */
public final class ShanhaiStructureDetector {

    /** 与上游一致：超过 8 格不检测，避免隔空逐面重扫巨型结构。 */
    private static final double MAX_DISTANCE_SQR = 64.0;
    private static final int MAX_HIGHLIGHTS = 256;
    private static final int MAX_CHAT_LINES = 8;
    private static final int HIGHLIGHT_COLOR = 0xFF5555;
    private static final long HIGHLIGHT_MILLIS = 15000L;

    /** 全局同时只允许一次检测：逐面重扫大结构会长时间占住服务端线程。 */
    private static boolean running;

    private static final String SCOPE_CLASS = "org.gtlcore.gtlcore.api.pattern.StructureDetectionScope";
    private static Method scopeBegin;
    private static Method scopeClose;
    private static boolean scopeResolved;

    private ShanhaiStructureDetector() {}

    private static Component tr(String key, Object... args) {
        return Component.translatable("message.gtlmodular.whale_terminal.detect." + key, args);
    }

    /** 服务端入口：校验距离与成型状态后，把真正重扫排进服务端线程。 */
    public static void detect(ServerPlayer player, IMultiController controller, ItemStack terminal) {
        if (controller.isFormed()) {
            player.sendSystemMessage(tr("formed"));
            return;
        }
        Level level = controller.self().getLevel();
        if (!(level instanceof ServerLevel serverLevel) || serverLevel.getServer() == null) return;
        BlockPos pos = controller.self().getPos();
        if (player.position().distanceToSqr(Vec3.atCenterOf(pos)) > MAX_DISTANCE_SQR) {
            player.sendSystemMessage(tr("too_far"));
            return;
        }
        if (running) {
            player.sendSystemMessage(tr("running"));
            return;
        }
        running = true;
        boolean mirrored = ShanhaiUltimateTerminalConfig.isMirrored(terminal);
        int repeatCount = ShanhaiUltimateTerminalConfig.getRepeatCount(terminal);
        serverLevel.getServer().execute(() -> {
            try {
                report(player, controller, check(controller, mirrored, repeatCount), mirrored);
            } catch (RuntimeException e) {
                player.sendSystemMessage(Component.literal(
                        "§c[哦鲸鲸终端] 结构检测失败: " + e.getMessage()));
            } finally {
                running = false;
            }
        });
    }

    /** 按控制器朝向逐面试匹配；每面只保留第一处错误（与上游相同）。 */
    private static List<PatternError> check(IMultiController controller, boolean mirrored, int repeatCount) {
        List<PatternError> errors = new ArrayList<>();
        BlockPattern pattern = controller.getPattern();
        if (pattern == null) {
            errors.add(new PatternStringError("no pattern found"));
            return errors;
        }
        Direction front = controller.self().getFrontFacing();
        Direction[] facings = controller.hasFrontFacing()
                ? new Direction[] { front }
                : new Direction[] { Direction.SOUTH, Direction.NORTH, Direction.EAST, Direction.WEST };
        BlockState controllerState = controller.self().getBlockState();
        if (controllerState.getBlock() instanceof MetaMachineBlock machineBlock
                && machineBlock.getRotationState() == RotationState.NONE) {
            facings = new Direction[] { front };
        }
        Direction upwards = controller.self().getUpwardsFacing();
        BlockPos center = controller.self().getPos();
        for (Direction facing : facings) {
            MultiblockState state = new MultiblockState(controller.self().getLevel(), center);
            AutoCloseable scope = openRepeatScope(state, repeatCount);
            try {
                pattern.checkPatternAt(state, center, facing, upwards, mirrored, false);
            } finally {
                closeQuietly(scope);
            }
            if (state.hasError()) {
                errors.add(state.error);
            }
        }
        return errors;
    }

    /**
     * 把「重复层数」套进结构匹配：仅当 gtlcore 提供 {@code StructureDetectionScope} 时生效。
     *
     * <p>作用域是 ThreadLocal 且按 {@code state} 身份比对，所以必须把同一个 MultiblockState
     * 传进去，并在匹配结束后立刻 close。
     */
    private static AutoCloseable openRepeatScope(Object state, int repeatCount) {
        if (repeatCount <= 0) return null;
        resolveScope();
        if (scopeBegin == null || scopeClose == null) return null;
        try {
            Object scope = scopeBegin.invoke(null, state, repeatCount);
            return () -> {
                try {
                    scopeClose.invoke(scope);
                } catch (ReflectiveOperationException | LinkageError ignored) {
                    // 作用域关闭失败不影响检测结果，下一轮会重新 begin
                }
            };
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    private static synchronized void resolveScope() {
        if (scopeResolved) return;
        scopeResolved = true;
        try {
            Class<?> type = Class.forName(SCOPE_CLASS);
            scopeBegin = type.getMethod("begin", Object.class, int.class);
            scopeClose = type.getMethod("close");
        } catch (ReflectiveOperationException | LinkageError ignored) {
            scopeBegin = null;
            scopeClose = null;
        }
    }

    private static void closeQuietly(AutoCloseable scope) {
        if (scope == null) return;
        try {
            scope.close();
        } catch (Exception ignored) {
            // close 内部已吞反射异常；这里只是兜底
        }
    }

    /** 聊天栏逐条报错（最多 {@link #MAX_CHAT_LINES} 行）+ 世界内红色高亮。 */
    private static void report(ServerPlayer player, IMultiController controller,
                               List<PatternError> errors, boolean mirrored) {
        if (errors.isEmpty()) {
            player.sendSystemMessage(tr("clean"));
            return;
        }
        Component mode = tr(mirrored ? "mode.mirrored" : "mode.normal");
        List<ShanhaiStructureHighlightPacket.Marker> markers = new ArrayList<>();
        int shown = 0;
        for (PatternError error : errors) {
            // PatternStringError 没绑到 MultiblockState（getPos 会 NPE），原样转述它的说明即可
            if (error instanceof PatternStringError) {
                player.sendSystemMessage(error.getErrorInfo());
                continue;
            }
            BlockPos pos = error.getPos();
            if (pos == null) {
                player.sendSystemMessage(error.getErrorInfo());
                continue;
            }
            if (markers.size() < MAX_HIGHLIGHTS) {
                markers.add(new ShanhaiStructureHighlightPacket.Marker(pos, HIGHLIGHT_COLOR));
            }
            if (shown < MAX_CHAT_LINES) {
                player.sendSystemMessage(tr("error", pos.toShortString(), describe(error), mode));
            }
            shown++;
        }
        ShanhaiStructureHighlightPacket.sendTo(player, controller.self().getLevel().dimension(),
                System.currentTimeMillis() + HIGHLIGHT_MILLIS, markers);
        player.sendSystemMessage(tr("summary", shown).copy().withStyle(ChatFormatting.AQUA));
    }

    /** 拼接「该位置需要什么」：优先用错误自带的候选表，取不到就退回 GTCEu 的错误描述。 */
    private static Component describe(PatternError error) {
        MutableComponent names = Component.empty();
        boolean first = true;
        // 通用 PatternError 在 worldState.predicate 为空时 getCandidates 会 NPE，按「没有候选」处理
        List<List<ItemStack>> groups;
        try {
            groups = error.getCandidates();
        } catch (RuntimeException e) {
            groups = null;
        }
        if (groups != null) {
            for (List<ItemStack> group : groups) {
                if (group == null) continue;
                for (ItemStack stack : group) {
                    if (stack == null || stack.isEmpty()) continue;
                    if (!first) names.append(Component.literal(" / "));
                    names.append(stack.getHoverName());
                    first = false;
                }
            }
        }
        if (!first) return names;
        if (error instanceof SinglePredicateError single
                && single.predicate != null && single.predicate.getCandidates() != null) {
            for (ItemStack stack : single.predicate.getCandidates()) {
                if (stack == null || stack.isEmpty()) continue;
                if (!first) names.append(Component.literal(" / "));
                names.append(stack.getHoverName());
                first = false;
            }
            if (!first) return names;
        }
        // 连候选都拿不到（例如「该位置不允许任何方块」）：直接转述 GTCEu 的说明
        Component info = error.getErrorInfo();
        return info == null ? Component.empty() : info.copy().withStyle(ChatFormatting.GRAY);
    }
}
