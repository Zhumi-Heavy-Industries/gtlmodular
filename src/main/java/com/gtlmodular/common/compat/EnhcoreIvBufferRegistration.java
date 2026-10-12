package com.gtlmodular.common.compat;

import com.gtlmodular.GTLModular;
import com.gtlmodular.common.diag.SwallowedErrors;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

/** 用反射登记可选 Enhancedcore 的方块白名单，避免缺少该依赖时触发类加载错误。 */
public final class EnhcoreIvBufferRegistration {

    private static final ResourceLocation OUR_BUFFER = new ResourceLocation("gtlmodular", "super_me_pattern_assembly");

    private static final String REGISTRY_CLASS = "com.gtl.enhancedcore.common.recipe.iv.IvBufferRegistry";

    private EnhcoreIvBufferRegistration() {
    }

    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(EnhcoreIvBufferRegistration::tryRegister);
    }

    private static void tryRegister() {
        try {
            Class<?> registry = Class.forName(REGISTRY_CLASS);
            Object result = registry.getMethod("register", ResourceLocation.class).invoke(null, OUR_BUFFER);
            GTLModular.LOGGER.info("[gtlmodular] 已向 GTL-Enhancedcore 的 IV 样板仓注册表登记 {}（返回 {}）", OUR_BUFFER, result);
            try {
                Object ids = registry.getMethod("registeredIds").invoke(null);
                GTLModular.LOGGER.info("[gtlmodular] IV 样板仓注册表现有: {}", ids);
            } catch (Throwable ignored) {
                SwallowedErrors.report("EnhcoreIvBufferRegistration#tryRegister-readRegisteredIds", ignored);
                // 白名单读取仅用于诊断，失败不影响已完成的登记。
            }
        } catch (ClassNotFoundException e) {
            GTLModular.LOGGER.info("[gtlmodular] 未发现 GTL-Enhancedcore 的 IV 样板仓注册表（该模组未装或是旧版），跳过登记 ✓");
        } catch (Throwable t) {
            SwallowedErrors.report("EnhcoreIvBufferRegistration#tryRegister", t);
            GTLModular.LOGGER.warn("[gtlmodular] IV 样板仓登记失败（已忽略，不影响启动）: {}", t.toString());
        }
    }
}