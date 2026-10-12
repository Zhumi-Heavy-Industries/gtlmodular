package com.gtlmodular.nac;

import com.gregtechceu.gtceu.api.addon.GTAddon;
import com.gtlmodular.common.data.GTLModularAddon;

@GTAddon
public final class GTLNacAddon extends GTLModularAddon {

    public GTLNacAddon() {
        // GTCEu 可能先扫描 addon，再调用本模组构造器。
        GTLNacContext.bind();
    }
}
