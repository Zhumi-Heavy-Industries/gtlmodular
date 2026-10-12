package com.gtlmodular.nac;

import com.gtlmodular.common.config.NanochipConfig;
import net.minecraftforge.common.ForgeConfigSpec;

public final class GTLNacConfig {

    public static final NanochipConfig NANOCHIP;
    public static final ForgeConfigSpec SPEC;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        NANOCHIP = new NanochipConfig(builder, GTLNac.MOD_ID);
        SPEC = builder.build();
    }

    private GTLNacConfig() {
    }
}
