package com.gtlmodular.common.compat;

import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gtlmodular.GTLModular;
import com.gtlmodular.common.diag.SwallowedErrors;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.BooleanSupplier;

/**
 * 宿主机器是否已被增强版（GTL-Enhancedcore）的「跨配方 / 隔离 IV」模式接管。
 *
 * <p>增强版把登记过的样板仓当成隔离订单仓：宿主一旦有这种仓，配方逻辑就一次枚举全部配方表、
 * 按订单派工，机器上不再存在"当前模式"。这时切换模式改的变量没人读，重置配方逻辑却会打断
 * 外部调度，所以自动切换与推料锁模式都必须停手。
 *
 * <p>判据直接问增强版自己的 {@code IvMachineScope.crossRecipeEnabled}：它的名单随版本增减，
 * 照抄或自己拼条件都会漏。这个判据被我们的独立线程 mixin 截断是有意的——引擎接管时增强版
 * 跨配方确实关闭，这里要看到的正是生效值。
 *
 * <p>增强版是可选依赖：类名只能反射拿，找不到就恒为 false。判据每次要遍历零件表，按机器缓存。
 */
public final class EnhancedcoreIvModeCompat {

    private static final String SCOPE_CLASS = "com.gtl.enhancedcore.common.recipe.iv.IvMachineScope";
    private static final long CACHE_TICKS = 20L;

    private static final Map<Object, Cached> CACHE = new WeakHashMap<>();

    private static boolean resolved;
    private static Method crossRecipeEnabled;

    /** 单测替换判据用：正式环境恒为 null。 */
    static BooleanSupplier testProbe;

    private EnhancedcoreIvModeCompat() {
    }

    public static boolean suppressesModeSwitch(IRecipeLogicMachine controller, long gameTime) {
        if (controller == null) {
            return false;
        }
        Object machine;
        try {
            machine = controller.self();
        } catch (RuntimeException e) {
            SwallowedErrors.report("EnhancedcoreIvModeCompat#self", e);
            return false;
        }
        return crossRecipeOn(machine, gameTime);
    }

    static boolean crossRecipeOn(Object machine, long gameTime) {
        if (machine == null) {
            return false;
        }
        BooleanSupplier probe = testProbe;
        if (probe != null) {
            return cached(machine, gameTime, probe);
        }
        Method judge = judge();
        if (judge == null) {
            return false;
        }
        return cached(machine, gameTime, () -> invoke(judge, machine));
    }

    private static boolean cached(Object machine, long now, BooleanSupplier compute) {
        Cached previous = CACHE.get(machine);
        // 换世界时 gameTime 会倒退，直接当作过期，不能按差值判断。
        if (previous != null && now >= previous.tick && now - previous.tick < CACHE_TICKS) {
            return previous.value;
        }
        boolean value;
        try {
            value = compute.getAsBoolean();
        } catch (RuntimeException failed) {
            // 判据失败按"不算跨配方"处理：宁可多切一次，也不能让自动切换整条失效。
            SwallowedErrors.report("EnhancedcoreIvModeCompat#crossRecipeEnabled", failed);
            value = false;
        }
        CACHE.put(machine, new Cached(now, value));
        return value;
    }

    private static Method judge() {
        if (resolved) {
            return crossRecipeEnabled;
        }
        resolved = true;
        try {
            Class<?> scope = Class.forName(SCOPE_CLASS);
            try {
                crossRecipeEnabled = scope.getMethod("crossRecipeEnabled", Object.class);
            } catch (NoSuchMethodException objectOverloadMissing) {
                // 旧版只有具体类型重载；宁可绕这一下也不要退回"自己拼条件"。
                crossRecipeEnabled = scope.getMethod("crossRecipeEnabled",
                        WorkableElectricMultiblockMachine.class);
            }
            GTLModular.LOGGER.info("[gtlmodular] 已接入增强版跨配方判据（这些机器上自动切换会停手）");
        } catch (ClassNotFoundException notInstalled) {
            GTLModular.LOGGER.info("[gtlmodular] 未发现 GTL-Enhancedcore，跨配方判据不启用 ✓");
        } catch (ReflectiveOperationException | LinkageError failed) {
            SwallowedErrors.report("EnhancedcoreIvModeCompat#resolve", failed);
            GTLModular.LOGGER.info("[gtlmodular] 增强版跨配方判据解析失败，自动切换照常工作: {}", failed.toString());
        }
        return crossRecipeEnabled;
    }

    private static boolean invoke(Method judge, Object machine) {
        try {
            return Boolean.TRUE.equals(judge.invoke(null, machine));
        } catch (ReflectiveOperationException | LinkageError failed) {
            SwallowedErrors.report("EnhancedcoreIvModeCompat#invoke", failed);
            throw new IllegalStateException("增强版跨配方判据调用失败", failed);
        }
    }

    static void resetForTest() {
        resolved = false;
        crossRecipeEnabled = null;
        testProbe = null;
        CACHE.clear();
    }

    private record Cached(long tick, boolean value) {
    }
}
