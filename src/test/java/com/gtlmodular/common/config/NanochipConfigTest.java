package com.gtlmodular.common.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import net.minecraftforge.common.ForgeConfigSpec;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NanochipConfigTest {

    @Test
    void unreadableValuesUseTheirDeclaredDefaults() {
        NanochipConfig config = new NanochipConfig(new ForgeConfigSpec.Builder(), "gtlmodular");
        assertTrue(config.isNanochipSimpleLogistics());
        assertTrue(config.isNanochipOriginalMode());
        assertTrue(config.isNanochipComplexEnabled());
        assertTrue(config.isNanochipGridPowerEnabled());
        assertTrue(config.isNanochipVerboseStructureLogs());
        assertTrue(config.isHideOrphanItemsEnabled());
        assertFalse(config.isVacuumRequiresPipes());
        assertFalse(config.isNanochipCalibrationUseFullWindow());
        assertFalse(config.isNanochipDebugLogsEnabled());
        assertEquals(1024, config.getNanochipDirectParallelCap());
        assertEquals(1, config.getNanochipDirectCrossRecipeThreads());
        assertEquals(0.5D, config.getNanochipImpurityPerRecipe());
        assertEquals(config.defaultCalibrationRecipeWhitelist(), config.getNanochipCalibrationRecipeWhitelist());
    }

    @Test
    void eachProductBindsEveryValueToItsOwnBuiltSpec() throws ReflectiveOperationException {
        var fullBuilder = new ForgeConfigSpec.Builder();
        var full = new NanochipConfig(fullBuilder, "gtlmodular");
        var fullSpec = fullBuilder.build();
        var standaloneBuilder = new ForgeConfigSpec.Builder();
        var standalone = new NanochipConfig(standaloneBuilder, "gtl_nac");
        var standaloneSpec = standaloneBuilder.build();
        Field owner = ForgeConfigSpec.ConfigValue.class.getDeclaredField("spec");
        owner.setAccessible(true);
        for (Field field : NanochipConfig.class.getDeclaredFields()) {
            if (!ForgeConfigSpec.ConfigValue.class.isAssignableFrom(field.getType())) continue;
            field.setAccessible(true);
            assertSame(fullSpec, owner.get(field.get(full)), field.getName());
            assertSame(standaloneSpec, owner.get(field.get(standalone)), field.getName());
            assertNotSame(field.get(full), field.get(standalone), field.getName());
        }
        assertEquals(List.of("gtlmodular:nanochip/assembly_complex/*", "gtlmodular:nanochip/assembly_matrix/*"),
                full.getNanochipCalibrationRecipeWhitelist());
        assertEquals(List.of("gtl_nac:nanochip/assembly_complex/*", "gtl_nac:nanochip/assembly_matrix/*"),
                standalone.getNanochipCalibrationRecipeWhitelist());
    }

    @Test
    void loadedOverridesAndCustomRecipeIdsArePreserved() {
        var builder = new ForgeConfigSpec.Builder();
        var config = new NanochipConfig(builder, "gtl_nac");
        var spec = builder.build();
        var values = CommentedConfig.inMemory();
        values.set("nanochipComplex.simpleLogisticsMode", false);
        values.set("nanochipComplex.originalMode", false);
        values.set("nanochipComplex.calibrationRecipeWhitelist", List.of("pack:custom/circuit"));
        spec.setConfig(values);
        assertFalse(config.isNanochipSimpleLogistics());
        assertFalse(config.isNanochipOriginalMode());
        assertEquals(List.of("pack:custom/circuit"), config.getNanochipCalibrationRecipeWhitelist());
        values.set("nanochipComplex.calibrationRecipeWhitelist", List.of());
        spec.afterReload();
        assertEquals(config.defaultCalibrationRecipeWhitelist(), config.getNanochipCalibrationRecipeWhitelist());
    }
}
