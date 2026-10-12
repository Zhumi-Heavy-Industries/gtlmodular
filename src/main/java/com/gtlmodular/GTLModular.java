package com.gtlmodular;

import com.gtlmodular.client.config.GTLModularConfigScreen;
import com.gtlmodular.common.compat.PatternAssemblyRecipeCacheInvalidator;
import com.gtlmodular.common.config.GTLModularConfig;
import com.gtlmodular.common.data.GTLModularMachines;
import com.gtlmodular.common.data.GTLModularNacContext;
import com.gtlmodular.common.data.GTLModularNacSelfCheck;
import com.gtlmodular.common.event.NanochipEventWiring;
import com.gtlmodular.common.data.nanochip.NanochipRegistration;
import com.gtlmodular.common.data.nanochip.NanochipRecipeCacheHooks;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(GTLModular.MOD_ID)
public final class GTLModular {

    public static final String MOD_ID = "gtlmodular";
    public static final Logger LOGGER = LoggerFactory.getLogger("GTL-Modular");

    public GTLModular() {
        GTLModularNacContext.bind();
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        NanochipRecipeCacheHooks.register(new PatternAssemblyRecipeCacheInvalidator());

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, GTLModularConfig.SPEC);

        DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> () -> ModLoadingContext.get().registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(GTLModularConfigScreen::create)));

        GTLModularMachines.registerEventListeners(modBus);

        // 配方表必须在 GTCEu 的注册事件内创建，冻结后注册会抛异常。
        NanochipRegistration.registerRecipeTypes(modBus);

        com.gtlmodular.common.data.module.SpaceOreRecipeMirror.register(modBus);
        modBus.addListener(com.gtlmodular.common.compat.EnhcoreIvBufferRegistration::onCommonSetup);

        // Registrate 条目必须在 Forge 方块注册事件前创建，机器则等待 GTCEu 的机器注册窗口。
        GTLModularMachines.initBlocks();

        // 材料走 MaterialEvent：GTCEu 的材料初始化入口不会调用 addon 的 registerMaterials。
        NanochipRegistration.registerMaterials(modBus);

        GTLModularNacSelfCheck.register();
        com.gtlmodular.common.command.GTLModularCommands.register();

        LOGGER.info("[gtlmodular] 加载中：机器注册委托给 GTM 机器注册事件窗口。");
        NanochipEventWiring.register(modBus);
    }

    private static final String ENHANCEDCORE_MIXIN_CONFIG = "gtlmodular.enhancedcore.mixins.json";

    /** 反射隔离可选兼容类，避免缺少依赖时影响本体加载。 */
    private static void registerEnhancedcoreMixins() {
        try {
            if (!net.minecraftforge.fml.ModList.get().isLoaded("gtl_enhancedcore")) {
                LOGGER.info("[gtlmodular] 未检测到 gtl_enhancedcore ⇒ 不加载 enhancedcore 兼容 mixin。");
                return;
            }
            String m = System.getProperty("mixin.env.ignoreRequired");
            String c = System.getProperty("mixin.checks");
            boolean tolerant = "true".equalsIgnoreCase(m) || "false".equalsIgnoreCase(c);
            if (!tolerant) {
                LOGGER.warn("[gtlmodular] 检测到 gtl_enhancedcore，但 {} 未启用「忽略缺失 mixin 目标」开关"
                                + "（需要 -Dmixin.env.ignoreRequired=true 或 -Dmixin.checks=false）"
                                + " ⇒ 本次不注入 enhancedcore 兼容 mixin（闸门①/②的放行不会生效）。",
                        ENHANCEDCORE_MIXIN_CONFIG);
                return;
            }
            Class<?> mixinConfigs = Class.forName("org.spongepowered.asm.mixin.MixinConfigs");
            for (java.lang.reflect.Method method : mixinConfigs.getMethods()) {
                if (!"register".equals(method.getName()) || method.getParameterCount() != 1) continue;
                if (method.getParameterTypes()[0] != String.class) continue;
                method.invoke(null, ENHANCEDCORE_MIXIN_CONFIG);
                LOGGER.info("[gtlmodular] 已注册 {}（闸门①/②放行 + 闸门③护栏）。", ENHANCEDCORE_MIXIN_CONFIG);
                return;
            }
            LOGGER.warn("[gtlmodular] 未找到 MixinConfigs.register(String) ⇒ {} 未加载。",
                    ENHANCEDCORE_MIXIN_CONFIG);
        } catch (Throwable t) {
            // 可选兼容失败不能阻止本体加载。
            LOGGER.warn("[gtlmodular] enhancedcore 兼容 mixin 注册失败（已忽略）：{}", t.toString());
        }
    }
}
