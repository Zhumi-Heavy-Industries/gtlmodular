package com.gtlmodular.common.data;

import com.gregtechceu.gtceu.api.addon.GTAddon;

// GTCEu 构造时就可能扫描 addon，不能等本模组构造器运行后才提供身份。
@GTAddon
public final class GTLModularNacAddon extends GTLModularAddon {

    public GTLModularNacAddon() {
        GTLModularNacContext.bind();
    }
}
