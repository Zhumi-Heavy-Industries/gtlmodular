package com.gtlmodular.common.data.nanochip;

import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate;
import com.gtlmodular.common.config.NanochipConfig;
import com.gtlmodular.common.diag.SwallowedErrors;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public final class NanochipModContext {

    private static volatile NanochipModContext current;
    private static String earlyModId;

    private final String modId;
    private final String assetNamespace;
    private final Supplier<GTRegistrate> machineRegistrate;
    private final Supplier<GTRegistrate> addonRegistrate;
    private final Logger logger;
    private final Supplier<NanochipConfig> config;
    private final BooleanSupplier recipeDiagnostics;

    public NanochipModContext(String modId, String assetNamespace,
                              Supplier<GTRegistrate> machineRegistrate,
                              Supplier<GTRegistrate> addonRegistrate, Logger logger) {
        this(modId, assetNamespace, machineRegistrate, addonRegistrate, logger,
                () -> { throw new IllegalStateException("NAC config supplier is not bound"); }, () -> false);
    }

    public NanochipModContext(String modId, String assetNamespace,
                              Supplier<GTRegistrate> machineRegistrate,
                              Supplier<GTRegistrate> addonRegistrate, Logger logger,
                              Supplier<NanochipConfig> config, BooleanSupplier recipeDiagnostics) {
        this.modId = new ResourceLocation(modId, "context").getNamespace();
        this.assetNamespace = new ResourceLocation(assetNamespace, "context").getNamespace();
        this.machineRegistrate = Objects.requireNonNull(machineRegistrate, "machineRegistrate");
        this.addonRegistrate = Objects.requireNonNull(addonRegistrate, "addonRegistrate");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.config = Objects.requireNonNull(config, "config");
        this.recipeDiagnostics = Objects.requireNonNull(recipeDiagnostics, "recipeDiagnostics");
    }

    // 两个入口可能先后被 Forge 与 GTAddon 扫描触达，只允许重复绑定同一个对象。
    public static synchronized void bind(NanochipModContext context) {
        Objects.requireNonNull(context, "context");
        if (current != null && current != context) {
            throw new IllegalStateException("NAC context already bound to " + current.modId);
        }
        if (earlyModId != null && !earlyModId.equals(context.modId)) {
            throw new IllegalStateException("NAC context does not match owning mod file " + earlyModId);
        }
        current = context;
        SwallowedErrors.bind(context.modId, context.logger);
    }

    private static NanochipModContext current() {
        NanochipModContext context = current;
        if (context == null) {
            throw new IllegalStateException("NAC context is not bound; bind it before addon discovery or registration");
        }
        return context;
    }

    public static String modId() {
        return current().modId;
    }

    public static ResourceLocation registryId(String path) {
        return new ResourceLocation(current().modId, path);
    }

    public static synchronized ResourceLocation earlyRegistryId(String path) {
        if (current != null) return registryId(path);
        if (earlyModId == null) {
            earlyModId = NanochipProductIdentity.fromScanData(net.minecraftforge.fml.ModList.get().getAllScanData());
        }
        return new ResourceLocation(earlyModId, path);
    }

    // 注册空间随产物变化，但共享模型和贴图仍来自同一份 assets。
    public static ResourceLocation assetId(String path) {
        return new ResourceLocation(current().assetNamespace, path);
    }

    public static String assetNamespace() {
        return current().assetNamespace;
    }

    // GTAddon 扫描只需要身份；延迟取值避免此时加载机器表并启动注册链。
    public static GTRegistrate machineRegistrate() {
        return Objects.requireNonNull(current().machineRegistrate.get(), "NAC machine Registrate");
    }

    public static GTRegistrate addonRegistrate() {
        return Objects.requireNonNull(current().addonRegistrate.get(), "NAC addon Registrate");
    }

    public static Logger logger() {
        return current().logger;
    }

    // 延迟获取配置，使 addon 身份扫描不必提前构建或加载 COMMON Spec。
    public static NanochipConfig config() {
        return Objects.requireNonNull(current().config.get(), "NAC config");
    }

    // 旧版的配方诊断跟随样板调试键，独立版无需定义样板配置。
    public static boolean recipeDiagnosticsEnabled() {
        return current().recipeDiagnostics.getAsBoolean();
    }
}
