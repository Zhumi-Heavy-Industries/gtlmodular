package com.gtlmodular.integration.mixin;

import org.apache.logging.log4j.LogManager;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.extensibility.IMixinConfig;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinErrorHandler;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/** 校准与容量保护必须完整生效，避免扣料后才发现批次无法处理。 */
public final class GTLModularMixinPlugin implements IMixinConfigPlugin, IMixinErrorHandler {

    @Override
    public void onLoad(String mixinPackage) {
        Mixins.registerErrorHandlerClass(getClass().getName());
    }

    @Override
    public ErrorAction onPrepareError(IMixinConfig config, Throwable th, IMixinInfo mixin, ErrorAction action) {
        return report(config.getName(), th, mixin, action);
    }

    @Override
    public ErrorAction onApplyError(String targetClassName, Throwable th, IMixinInfo mixin, ErrorAction action) {
        return report(targetClassName, th, mixin, action);
    }

    private static ErrorAction report(String target, Throwable failure, IMixinInfo mixin, ErrorAction action) {
        if (mixin == null || !mixin.getClassName().startsWith("com.gtlmodular.mixin.")) return action;
        String name = mixin.getClassName();
        boolean calibration = name.equals("com.gtlmodular.mixin.gtlcore.NanochipCalibrationLookupMixin")
                || name.equals("com.gtlmodular.mixin.gtlcore.NanochipCalibrationInputMixin");
        boolean required = calibration || name.equals("com.gtlmodular.mixin.gtlcore.NanochipLocalBatchEnergyMixin");
        // 此时入口尚未初始化，不能通过产品上下文取日志器。
        LogManager.getLogger("GTL-Modular/Mixin").error(
                "[gtlmodular/gtl_nac] 自有 mixin 失败：{} -> {}。{}", name, target,
                required ? "NAC 校准或合批容量保护不完整，停止加载；请检查依赖兼容性。"
                        : "对应功能未能应用，请检查依赖兼容性。", failure);
        return required ? ErrorAction.ERROR : action;
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
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
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
