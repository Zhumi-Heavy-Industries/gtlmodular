package com.gtlmodular.common.machine.pattern;

import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import net.minecraft.network.chat.Component;

/** 让两种样板总成对模式名称和上游兜底名使用相同判据。 */
public final class PatternModeNaming {

    private static final String AUTO_NAME_FALLBACK_UPSTREAM = "\u672a\u547d\u540d\u52a0\u5de5\u6a21\u5f0f";

    private PatternModeNaming() {
    }

    /** 用注册名保存模式命名，避免显示语言变化影响识别。 */
    public static String modeKeyOf(GTRecipeType type) {
        return type == null || type.registryName == null ? null : type.registryName.toString();
    }

    /** 缺少翻译时仍需提供可读名称，避免把语言键写进自定义名。 */
    public static String modeNameForNaming(GTRecipeType type) {
        if (type == null || type.registryName == null) {
            return null;
        }
        String overridden = nameOverride(type, null);
        if (overridden != null && !overridden.isEmpty()) {
            return overridden;
        }
        try {
            String key = type.registryName.toLanguageKey();
            String translated = Component.translatable(key).getString();
            if (translated == null || translated.isEmpty() || translated.equals(key)) {
                String path = type.registryName.getPath();
                return path == null || path.isEmpty() ? null : path;
            }
            return translated;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    /** 只覆盖确需缩短的模式名，避免统一删字误伤其他名称。 */
    private static String nameOverride(GTRecipeType type, String fallback) {
        if (type != null && type.registryName != null) {
            String path = type.registryName.getPath();
            if ("circuit_assembly".equals(path) || "circuit_assembler".equals(path)) {
                return "电路组装";
            }
        }
        return fallback;
    }

    /** 上游兜底名不代表玩家命名，允许自动命名覆盖。 */
    public static boolean isUpstreamPlaceholderName(String name) {
        return name != null && AUTO_NAME_FALLBACK_UPSTREAM.equals(name);
    }

    /** 日志在缺少译文时仍需显示可辨认的配方表名。 */
    public static String tableNameOf(GTRecipeType type) {
        if (type == null || type.registryName == null) {
            return "?";
        }
        try {
            String key = type.registryName.toLanguageKey();
            String translated = Component.translatable(key).getString();
            return (!translated.isEmpty() && !translated.equals(key)) ? translated : type.registryName.getPath();
        } catch (RuntimeException ignored) {
            return type.registryName.getPath();
        }
    }
}
