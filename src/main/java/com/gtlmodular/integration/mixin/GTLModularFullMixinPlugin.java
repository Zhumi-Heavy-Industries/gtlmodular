package com.gtlmodular.integration.mixin;

import org.apache.logging.log4j.LogManager;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.extensibility.*;
import org.spongepowered.asm.service.MixinService;

import java.util.List;
import java.util.Set;

public final class GTLModularFullMixinPlugin implements IMixinConfigPlugin, IMixinErrorHandler {
    private final GTLModularMixinPlugin shared = new GTLModularMixinPlugin();

    @Override public void onLoad(String mixinPackage) {
        shared.onLoad(mixinPackage);
        Mixins.registerErrorHandlerClass(getClass().getName());
    }

    @Override public String getRefMapperConfig() { return null; }
    @Override public List<String> getMixins() { return null; }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) {}
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}

    @Override
    public boolean shouldApplyMixin(String target, String mixin) {
        if (!mixin.startsWith("com.gtlmodular.mixin.independent.Enhancedcore")) return true;
        try {
            MixinService.getService().getBytecodeProvider().getClassNode(target);
            return true;
        } catch (ClassNotFoundException absent) {
            return false;
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("Cannot inspect optional scheduler compatibility", failure);
        }
    }

    @Override
    public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {
        if (mixin.startsWith("com.gtlmodular.mixin.independent.")) {
            LogManager.getLogger("GTL-Modular/Mixin").info("独立批次注入已应用：{} -> {}", mixin, target);
        }
    }

    @Override
    public ErrorAction onPrepareError(IMixinConfig config, Throwable error, IMixinInfo mixin, ErrorAction action) {
        return independentError(error, mixin, shared.onPrepareError(config, error, mixin, action));
    }

    @Override
    public ErrorAction onApplyError(String target, Throwable error, IMixinInfo mixin, ErrorAction action) {
        return independentError(error, mixin, shared.onApplyError(target, error, mixin, action));
    }

    private static ErrorAction independentError(Throwable error, IMixinInfo mixin, ErrorAction action) {
        if (mixin != null && mixin.getClassName().startsWith("com.gtlmodular.mixin.independent.")) {
            LogManager.getLogger("GTL-Modular/Mixin").error("独立批次保护未完整加载，停止启动：{}", mixin.getClassName(), error);
            return ErrorAction.ERROR;
        }
        return action;
    }
}
