package com.gtlmodular.common;

/**
 * 只在 GameTest 任务里由 -Dgtlmodular.testHost=true 打开的测试接缝。
 * 正式游戏不会设置该属性，所有放行都返回 false。
 */
public final class GameTestHooks {
    public static final String PROPERTY = "gtlmodular.testHost";

    private GameTestHooks() {
    }

    public static boolean active() {
        return Boolean.getBoolean(PROPERTY);
    }

    public static boolean isOreModule(String machineId) {
        return active() && machineId != null
                && machineId.startsWith("gtlmodular:molten_space_ore_module");
    }
}
