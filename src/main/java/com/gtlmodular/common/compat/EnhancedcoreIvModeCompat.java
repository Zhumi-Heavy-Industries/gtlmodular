package com.gtlmodular.common.compat;

import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gtlmodular.GTLModular;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 宿主机器是否已被增强版（GTL-Enhancedcore）的「跨配方 / 隔离 IV」模式接管。
 *
 * <p>增强版把登记过的样板仓当成隔离订单仓：宿主一旦有这种仓，配方逻辑就一次枚举全部配方表、
 * 按订单派工，机器上不再存在"当前模式"。这时切换模式改的变量没人读，重置配方逻辑却会打断
 * 外部调度，自动切换/推料锁模式/自动命名都必须停手。
 *
 * <p>判据直接问增强版自己的 {@code IvMachineScope.crossRecipeEnabled}：它的名单随版本增减，
 * 照抄或自己拼条件都会漏。判据每次要遍历零件表，按机器缓存 20 tick。
 *
 * <p>增强版是可选依赖：类名只能反射拿，找不到就恒为 false。
 *
 * <p>★ 这是从仓库线（{@code D:\gtlmodular}，提交 {@code 3919c19}）回移到线上树的同一份修复：
 * 线上树 0.3.206 及以前<b>没有</b>这段，而 0.3.0 起我们又把
 * {@code gtlmodular:super_me_pattern_assembly} 登记进了增强版的 IV 样板仓注册表
 * （{@link EnhcoreIvBufferRegistration}）⇒ 它在那 17 台原生巨构/4 台 Tiered IV 机器上会被当作
 * 隔离仓接管，而我们的自动切模式还在抢控制器 ⇒ 这就是"对增强版依然有地方没适配到"的一条。
 */
public final class EnhancedcoreIvModeCompat {

    private static final String SCOPE_CLASS = "com.gtl.enhancedcore.common.recipe.iv.IvMachineScope";
    private static final long CACHE_TICKS = 20L;

    private static final Map<Object, Cached> CACHE = new WeakHashMap<>();

    private static boolean resolved;
    private static Method crossRecipeEnabled;

    private EnhancedcoreIvModeCompat() {
    }

    /** 控制器所在机器是否处于增强版外部调度（跨配方 / 隔离 IV）。 */
    public static boolean suppressesModeSwitch(IRecipeLogicMachine controller, long gameTime) {
        if (controller == null) {
            return false;
        }
        Object machine;
        try {
            machine = controller.self();
        } catch (RuntimeException failed) {
            return false;
        }
        return crossRecipeOn(machine, gameTime);
    }

    static boolean crossRecipeOn(Object machine, long gameTime) {
        if (machine == null) {
            return false;
        }
        Method judge = judge();
        if (judge == null) {
            return false;
        }
        Cached previous = CACHE.get(machine);
        // 换世界时 gameTime 会倒退，直接当作过期，不能按差值判断。
        if (previous != null && gameTime >= previous.tick && gameTime - previous.tick < CACHE_TICKS) {
            return previous.value;
        }
        boolean value;
        try {
            value = Boolean.TRUE.equals(judge.invoke(null, machine));
        } catch (ReflectiveOperationException | LinkageError failed) {
            // 判据失败按"不算跨配方"处理：宁可多切一次，也不能让自动切换整条失效。
            value = false;
        }
        CACHE.put(machine, new Cached(gameTime, value));
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
            GTLModular.LOGGER.info("[gtlmodular] 已接入增强版跨配方判据（IV 机器上自动切换/锁模式/自动命名会停手）");
        } catch (ClassNotFoundException notInstalled) {
            GTLModular.LOGGER.info("[gtlmodular] 未发现 GTL-Enhancedcore，跨配方判据不启用 ✓");
        } catch (ReflectiveOperationException | LinkageError failed) {
            GTLModular.LOGGER.info("[gtlmodular] 增强版跨配方判据解析失败，自动切换照常工作: {}", failed.toString());
        }
        return crossRecipeEnabled;
    }

    private record Cached(long tick, boolean value) {
    }
}
