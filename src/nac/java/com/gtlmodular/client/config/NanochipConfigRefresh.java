package com.gtlmodular.client.config;

import com.gtlmodular.common.diag.SwallowedErrors;

import java.util.Objects;

public final class NanochipConfigRefresh {

    private static Runnable callback = () -> {};

    private NanochipConfigRefresh() {
    }

    public static void register(Runnable refresh) {
        callback = Objects.requireNonNull(refresh, "refresh");
    }

    public static void refresh() {
        try {
            callback.run();
        } catch (Throwable ignored) {
            SwallowedErrors.report("NanochipConfigRefresh#refresh", ignored);
            // 可选显示插件失败不能阻止配置保存，也不要求配置屏主动加载插件类。
        }
    }
}
