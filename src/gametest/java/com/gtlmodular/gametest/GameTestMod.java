package com.gtlmodular.gametest;

import com.gtlmodular.gametest.fixture.MoltenOreTestLayout;
import com.gtlmodular.gametest.fixture.NacStructureLayout;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterGameTestsEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod("gtl_gametest")
public final class GameTestMod {

    private static boolean startupLoaded;

    public GameTestMod() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::registerTests);
        MinecraftForge.EVENT_BUS.addListener(NacStructureLayout::prepareTemplate);
        MinecraftForge.EVENT_BUS.addListener(MoltenOreTestLayout::prepareTemplate);
        MinecraftForge.EVENT_BUS.addListener(GameTestReport::install);
    }

    private void registerTests(RegisterGameTestsEvent event) {
        event.register(ProductSmokeTests.class);
        event.register(MixinApplicationTests.class);
        event.register(ResourceIntegrityTests.class);
        event.register(NacStructureTests.class);
        event.register(NacProcessingTests.class);
        event.register(NacCalibrationTests.class);
        event.register(NacCalibrationTierTests.class);
        event.register(NacPersistenceTests.class);
        event.register(NacRestartTests.class);
        event.register(PatternAssemblyTests.class);
        event.register(BufferedOutputBufferTests.class);
        event.register(MoltenOreModuleTests.class);
        event.register(IndependentOwnershipTests.class);
        event.register(RecipeConflictTests.class);
    }

    public static void markStartupLoaded() {
        startupLoaded = true;
    }

    public static boolean startupLoaded() {
        return startupLoaded;
    }
}
