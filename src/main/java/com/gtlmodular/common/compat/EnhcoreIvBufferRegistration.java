package com.gtlmodular.common.compat;

import com.gtlmodular.GTLModular;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * 把我们的样板总成方块登记进 GTL-Enhancedcore 的「IV 样板仓注册表」。
 *
 * <p>背景（2026-10-02 实证）：Enhancedcore 2.9.4（要求 gtlcore &gt;= 1.2.3.2-fix3）把"谁能当 IV 样板仓"
 * 从「具体类 {@code MESuperPatternBufferPartMachine}」放宽成
 * 「基类 {@code MEPatternBufferPartMachine} + 一个方块 id 注册表」：
 * <ul>
 *   <li>运行期：{@code IvBufferRegistry.compatible(Object)} = {@code instanceof MEPatternBufferPartMachine}
 *       ⇒ 我们继承该基类，天然通过 ✓</li>
 *   <li>结构谓词：{@code IvBufferRegistry.predicate()} 遍历 {@code registeredIds()} 的方块 id
 *       ⇒ 默认只登记了 {@code gtladditions:me_super_pattern_buffer}，所以我们必须"登记一次"。</li>
 * </ul>
 *
 * <p>为什么用反射：Enhancedcore 是可选前置。直接 import 它的类，会在没有该模组的整合包里
 * 抛 {@code NoClassDefFoundError} 导致我们整个模组加载失败 ✗。反射 + try/catch 做到"有则登记、无则忽略"。
 *
 * <p>时机：{@code FMLCommonSetupEvent}（所有模组构造完成之后）⇒ 既晚于 Enhancedcore 的默认登记，
 * 又不占用游戏 tick；登记只是往一个 Set 里加一条 id，之后任何时候成型多方块都能读到 ✓
 */
public final class EnhcoreIvBufferRegistration {

    /** 我们自己的样板总成方块 id（与 GTLModularMachines 里注册的一致）。 */
    private static final ResourceLocation OUR_BUFFER = new ResourceLocation("gtlmodular", "super_me_pattern_assembly");

    /** Enhancedcore 的注册表类名（软依赖，完全不 import）。 */
    private static final String REGISTRY_CLASS = "com.gtl.enhancedcore.common.recipe.iv.IvBufferRegistry";

    private EnhcoreIvBufferRegistration() {
    }

    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(EnhcoreIvBufferRegistration::tryRegister);
    }

    /** 尝试登记；任何异常都只打一行日志，绝不影响启动。 */
    private static void tryRegister() {
        try {
            Class<?> registry = Class.forName(REGISTRY_CLASS);
            Object result = registry.getMethod("register", ResourceLocation.class).invoke(null, OUR_BUFFER);
            GTLModular.LOGGER.info("[gtlmodular] 已向 GTL-Enhancedcore 的 IV 样板仓注册表登记 {}（返回 {}）", OUR_BUFFER, result);
            try {
                Object ids = registry.getMethod("registeredIds").invoke(null);
                GTLModular.LOGGER.info("[gtlmodular] IV 样板仓注册表现有: {}", ids);
            } catch (Throwable ignored) {
                // registeredIds 只是诊断用，取不到无所谓
            }
        } catch (ClassNotFoundException e) {
            GTLModular.LOGGER.info("[gtlmodular] 未发现 GTL-Enhancedcore 的 IV 样板仓注册表（该模组未装或是旧版），跳过登记 ✓");
        } catch (Throwable t) {
            GTLModular.LOGGER.warn("[gtlmodular] IV 样板仓登记失败（已忽略，不影响启动）: {}", t.toString());
        }
    }
}