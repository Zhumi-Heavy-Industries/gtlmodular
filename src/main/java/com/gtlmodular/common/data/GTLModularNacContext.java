package com.gtlmodular.common.data;

import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate;
import com.gtlmodular.GTLModular;
import com.gtlmodular.common.config.GTLModularConfig;
import com.gtlmodular.common.data.nanochip.NanochipModContext;

public final class GTLModularNacContext {

    private static final NanochipModContext CONTEXT = new NanochipModContext(
            GTLModular.MOD_ID, GTLModular.MOD_ID,
            () -> GTLModularMachines.REGISTRATE, () -> AddonRegistrate.INSTANCE, GTLModular.LOGGER,
            () -> GTLModularConfig.NANOCHIP, GTLModularConfig::isPatternDebugLogsEnabled);

    private GTLModularNacContext() {
    }

    public static void bind() {
        NanochipModContext.bind(CONTEXT);
    }

    // 保持材料与机器使用各自的 Registrate，且不在 addon 身份扫描时创建它们。
    private static final class AddonRegistrate {
        private static final GTRegistrate INSTANCE = GTRegistrate.create(GTLModular.MOD_ID);
    }
}
