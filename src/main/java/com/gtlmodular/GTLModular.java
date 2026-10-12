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
        com.gtlmodular.common.machine.independent.control.IndependentMachineRegistry.register(modBus);

        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ModLoadingContext.get().registerExtensionPoint(
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
        com.gtlmodular.common.command.IndependentBatchCommands.register();

        LOGGER.info("[gtlmodular] 加载中：机器注册委托给 GTM 机器注册事件窗口。");
        NanochipEventWiring.register(modBus);
    }
}
