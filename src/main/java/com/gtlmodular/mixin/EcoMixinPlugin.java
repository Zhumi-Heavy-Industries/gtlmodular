package com.gtlmodular.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * 可选 mixin 插件：只有装了 Neo ECO AE（eco）时才应用 eco 相关 mixin，
 * 避免没装 eco 时因为找不到目标类导致启动崩溃。
 */
public final class EcoMixinPlugin implements IMixinConfigPlugin {

    private static boolean ecoPresent;

    @Override
    public void onLoad(String mixinPackage) {
        try {
            ecoPresent = net.minecraftforge.fml.loading.LoadingModList.get()
                    .getModFileById("neoecoae") != null;
        } catch (Throwable ignored) {
            ecoPresent = false;
        }
        if (!ecoPresent) {
            try {
                Class.forName("cn.dancingsnow.neoecoae.NeoECOAE", false,
                        EcoMixinPlugin.class.getClassLoader());
                ecoPresent = true;
            } catch (Throwable ignored) {
                ecoPresent = false;
            }
        }
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        // 只对 eco 包下的 mixin 做门控，本模组其它 mixin 不受“没装 eco”影响
        if (mixinClassName != null && mixinClassName.endsWith("EcoCpuNameMixin")) {
            return ecoPresent;
        }
        if (mixinClassName != null && mixinClassName.endsWith("EcoForceNativeCraftMixin")) {
            return ecoPresent;
        }
        if (mixinClassName != null && mixinClassName.endsWith("EcoCraftDebugMixin")) {
            return ecoPresent;
        }
        if (mixinClassName != null && mixinClassName.contains(".eco.")) {
            // 2026-10-27 临时停用：先确认 AE 下单卡住是否由我们的 eco mixin 引起
            return false;
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass,
                         String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass,
                          String mixinClassName, IMixinInfo mixinInfo) {
    }
}
