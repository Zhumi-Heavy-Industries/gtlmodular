package com.gtlmodular.nac;

import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate;
import com.gtlmodular.common.data.nanochip.NanochipModContext;

public final class GTLNacContext {

    private static final NanochipModContext CONTEXT = new NanochipModContext(
            GTLNac.MOD_ID, "gtlmodular",
            () -> GTLNacRegistration.REGISTRATE, () -> AddonRegistrate.INSTANCE, GTLNac.LOGGER,
            () -> GTLNacConfig.NANOCHIP, () -> GTLNacConfig.NANOCHIP.isNanochipDebugLogsEnabled());

    private GTLNacContext() {
    }

    public static void bind() {
        NanochipModContext.bind(CONTEXT);
    }

    // addon 身份扫描不应提前创建注册条目或配置 Spec。
    private static final class AddonRegistrate {
        private static final GTRegistrate INSTANCE = GTRegistrate.create(GTLNac.MOD_ID);
    }
}
