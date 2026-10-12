package com.gtlmodular.common.machine.nanochip.kubejs;

import com.gtlmodular.common.machine.nanochip.NanochipCalibration;
import com.gtlmodular.common.machine.nanochip.registration.NanochipModuleSpec;
import com.gtlmodular.common.machine.nanochip.registration.NanochipModules;
import net.minecraft.resources.ResourceLocation;

import java.util.Arrays;
import java.util.Locale;

/** Rhino 只负责基本类型转换；默认值和校验仍由 Java 规格维护。 */
public final class NanochipModuleBuilderJS {

    private final NanochipModuleSpec.Builder builder;
    private final Runnable requireOpen;

    NanochipModuleBuilderJS(ResourceLocation id, Runnable requireOpen) {
        builder = NanochipModuleSpec.opticalOrganizer(id);
        this.requireOpen = requireOpen;
    }

    public NanochipModuleBuilderJS opticalOrganizer() {
        requireOpen.run();
        return this;
    }

    public NanochipModuleBuilderJS displayName(String name) {
        requireOpen.run();
        builder.displayName(name);
        return this;
    }

    public NanochipModuleBuilderJS display(String overlayPrefix, String nameZh, String nameEn, String mechanicZh) {
        requireOpen.run();
        builder.display(new NanochipModuleSpec.Display(overlayPrefix, nameZh, nameEn, mechanicZh));
        return this;
    }

    public NanochipModuleBuilderJS recipeType(String id) {
        requireOpen.run();
        builder.recipeType(NanochipModules.parseId(id));
        return this;
    }

    public NanochipModuleBuilderJS bonuses(double euFactor, double durationFactor, double parallelFactor,
                                           boolean unlocksFullMode, String[] familyFilter) {
        requireOpen.run();
        builder.bonuses(new NanochipModuleSpec.Bonuses(euFactor, durationFactor, parallelFactor,
                unlocksFullMode, Arrays.stream(familyFilter).map(NanochipModuleBuilderJS::family).toList()));
        return this;
    }

    public NanochipModuleBuilderJS operation(int maxRecipeTicks, boolean router, String organizerFamily,
                                             String upkeepFluidId, int upkeepMbPerSecond) {
        requireOpen.run();
        builder.operation(new NanochipModuleSpec.Operation(maxRecipeTicks, router,
                organizerFamily == null ? null : family(organizerFamily), upkeepFluidId, upkeepMbPerSecond));
        return this;
    }

    public NanochipModuleBuilderJS features(boolean assembly, boolean board, boolean laser,
                                            boolean biologicalExemption, int energyPriority) {
        requireOpen.run();
        builder.features(new NanochipModuleSpec.Features(assembly, board, laser, biologicalExemption, energyPriority));
        return this;
    }

    public NanochipModuleBuilderJS template(String name) {
        requireOpen.run();
        builder.template(name);
        return this;
    }

    public NanochipModuleBuilderJS frame(String id) {
        requireOpen.run();
        builder.frame(new ResourceLocation(id));
        return this;
    }

    public NanochipModuleBuilderJS casing(String id) {
        requireOpen.run();
        builder.casing(new ResourceLocation(id));
        return this;
    }

    public NanochipModuleBuilderJS overlay(String id) {
        requireOpen.run();
        builder.overlay(new ResourceLocation(id));
        return this;
    }

    private static NanochipCalibration.Family family(String name) {
        return NanochipCalibration.Family.valueOf(name.toUpperCase(Locale.ROOT));
    }

    NanochipModuleSpec build() {
        return builder.build();
    }
}
