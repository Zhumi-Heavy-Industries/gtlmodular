package com.gtlmodular.nac;

import com.gtlmodular.common.command.GTLModularCommands;
import com.gtlmodular.common.data.nanochip.NanochipExpectedRecipes;
import com.gtlmodular.common.data.nanochip.NanochipRegistration;
import com.gtlmodular.common.event.NanochipEventWiring;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(GTLNac.MOD_ID)
public final class GTLNac {

    public static final String MOD_ID = "gtl_nac";
    public static final Logger LOGGER = LoggerFactory.getLogger("GTL-NAC");

    public GTLNac() {
        GTLNacContext.bind();
        NanochipExpectedRecipes.loadGenerated(MOD_ID);
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON,
                GTLNacConfig.SPEC, GTLNacConfig.NANOCHIP.fileName());
        DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> GTLNacClient::registerConfigScreen);
        GTLNacRegistration.registerEventListeners(modBus);
        NanochipRegistration.registerRecipeTypes(modBus);
        NanochipRegistration.initBlocks();
        NanochipRegistration.registerMaterials(modBus);
        GTLModularCommands.register(MinecraftForge.EVENT_BUS, "gtl_nac");
        NanochipEventWiring.register(modBus);
    }
}
