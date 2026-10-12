package com.gtlmodular.common.machine.independent.adapter;

import java.lang.reflect.Modifier;
import java.util.Set;

public final class RecipeAdapterSupport {
    private RecipeAdapterSupport() {}

    // 白名单只授予资格；绕过含副作用的覆写会丢失机器语义，必须在 lookup 之前拒绝。
    public static String unsupportedOverride(Class<?> actual, Class<?> family, Set<Class<?>> standardClasses,
                                             Set<String> forwardedMethods) {
        for (Class<?> type = actual; type != family; type = type.getSuperclass()) {
            if (type == null) return "不属于可适配逻辑族：" + actual.getName();
            if (standardClasses.contains(type)) continue;
            for (var method : type.getDeclaredMethods()) {
                if (method.isSynthetic() || Modifier.isStatic(method.getModifiers()) || Modifier.isPrivate(method.getModifiers())) continue;
                if (!forwardedMethods.contains(method.getName())) {
                    return "需要专用单批适配：" + type.getName() + "#" + method.getName();
                }
            }
        }
        return null;
    }
}
