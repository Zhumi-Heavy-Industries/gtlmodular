package com.gtlmodular.mixin.order;

import appeng.api.stacks.GenericStack;

/**
 * {@code TerminalAutoBuildService} 的<b>反射桥接</b>。
 *
 * <h2>为什么必须反射（2026-11-14 实机崩溃）</h2>
 * <pre>
 * Caused by: org.spongepowered.asm.mixin.throwables.ClassMetadataNotFoundException:
 *     com.gtlmodular.common.item.terminal.TerminalAutoBuildService
 *   at MixinPreProcessorStandard.transformMethod
 *   at CraftingService.getCpus ← NEComputationCluster.getFakeCPU (neoecoae)
 * </pre>
 * {@link TerminalOrderCpuMixin} / {@link TerminalOrderEcoCpuMixin} 织入
 * {@code CraftingService} / {@code CraftingCpuLogic}，而 NeoSecoEcoAE 的
 * {@code getFakeCPU} 会在<b>极早的类加载窗口</b>触发 {@code CraftingService.getCpus}。
 * 此时 Mixin 在 {@code transformMethod} 阶段需要解析方法体里引用的类型元数据，
 * 若直接以 Java 引用方式写 {@code TerminalAutoBuildService.completeOrder(...)}，
 * 就会在这个窗口里解析失败并抛 {@code ClassMetadataNotFoundException}，直接崩服。
 *
 * <p>改为反射后，mixin 方法体的常量池里只有 {@code Class.forName} 的字符串，
 * 不再有该类型的直接引用，Mixin 预处理器无需解析它的元数据，崩溃消除。
 *
 * <p>调用语义与直接调用完全一致（同样的 {@code completeOrder(GenericStack)}）；
 * 反射失败时只吞异常，绝不影响 AE 主逻辑。
 */
public final class OrderBridge {

    private static final String SERVICE = "com.gtlmodular.common.item.terminal.TerminalAutoBuildService";
    private static final String MARKER = "com.gtlmodular.common.item.terminal.TerminalOrderMarker";

    /** 反射查找结果缓存（null 表示尚未查找；查找失败后不再重试，避免每 tick 抛异常）。 */
    private static java.lang.reflect.Method completeOrder;
    private static java.lang.reflect.Method isMarkerKey;
    private static java.lang.reflect.Method isMarkerStack;
    private static boolean lookupFailed;

    private OrderBridge() {
    }

    /**
     * 等价于 {@code TerminalAutoBuildService.completeOrder(output)}（反射版）。
     *
     * @param output 订单标记 GenericStack（可空，为空时直接返回）
     */
    public static void completeOrder(GenericStack output) {
        if (output == null) {
            return;
        }
        try {
            java.lang.reflect.Method method = method();
            if (method == null) {
                return;
            }
            method.invoke(null, output);
        } catch (Throwable ignored) {
            // 桥接失败不影响 AE 主流程
        }
    }

    /**
     * 等价于 {@code TerminalOrderMarker.isMarker(AEKey)}（反射版）。
     *
     * <p>同样走反射：{@code TerminalOrderMarker} 依赖 {@code GTLModularItems}，
     * 在 neoecoae 的早期类加载窗口里直接引用有同样的元数据解析风险。
     */
    public static boolean isMarker(appeng.api.stacks.AEKey key) {
        if (key == null) {
            return false;
        }
        try {
            java.lang.reflect.Method method = markerMethod(true);
            if (method == null) {
                return false;
            }
            Object result = method.invoke(null, key);
            return result instanceof Boolean && (Boolean) result;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * 等价于 {@code TerminalOrderMarker.isMarker(GenericStack)}（反射版）。
     */
    public static boolean isMarker(GenericStack stack) {
        if (stack == null) {
            return false;
        }
        try {
            java.lang.reflect.Method method = markerMethod(false);
            if (method == null) {
                return false;
            }
            Object result = method.invoke(null, stack);
            return result instanceof Boolean && (Boolean) result;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static synchronized java.lang.reflect.Method markerMethod(boolean forKey) {
        if (lookupFailed) {
            return forKey ? isMarkerKey : isMarkerStack;
        }
        try {
            Class<?> marker = Class.forName(MARKER);
            if (forKey) {
                if (isMarkerKey == null) {
                    isMarkerKey = marker.getDeclaredMethod("isMarker", appeng.api.stacks.AEKey.class);
                    isMarkerKey.setAccessible(true);
                }
                return isMarkerKey;
            }
            if (isMarkerStack == null) {
                isMarkerStack = marker.getDeclaredMethod("isMarker", GenericStack.class);
                isMarkerStack.setAccessible(true);
            }
            return isMarkerStack;
        } catch (Throwable t) {
            lookupFailed = true;
            return null;
        }
    }

    private static synchronized java.lang.reflect.Method method() {
        if (completeOrder != null || lookupFailed) {
            return completeOrder;
        }
        try {
            Class<?> service = Class.forName(SERVICE);
            java.lang.reflect.Method found = service.getDeclaredMethod("completeOrder", GenericStack.class);
            found.setAccessible(true);
            completeOrder = found;
        } catch (Throwable t) {
            lookupFailed = true;
        }
        return completeOrder;
    }
}
