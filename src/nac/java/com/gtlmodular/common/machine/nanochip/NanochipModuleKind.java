package com.gtlmodular.common.machine.nanochip;

import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gtlmodular.common.machine.nanochip.registration.NanochipModuleSpec;
import com.gtlmodular.common.machine.nanochip.registration.NanochipModules;

import java.util.Locale;

/** 保留内置名称、顺序及旧调用口；参数统一从规格目录读取，避免两份定义漂移。 */
public enum NanochipModuleKind {

    ASSEMBLY_MATRIX,
    SMD_PROCESSOR,
    BOARD_PROCESSOR,
    ETCHING_ARRAY,
    CUTTING_CHAMBER,
    WIRE_TRACER,
    SUPERCONDUCTOR_SPLITTER,
    OPTICAL_ORGANIZER,
    EXOTIC_ORGANIZER,
    COSMIC_ORGANIZER,
    SUPRACAUSAL_ORGANIZER,
    BIOLOGICAL_COORDINATOR,
    ENCASEMENT_WRAPPER,
    SPLITTER;

    // 枚举常量构造期间不取规格，目录才能安全地反向保存 legacyKind。
    private NanochipModuleSpec spec() {
        return NanochipModules.builtin(this);
    }

    public String machineId() {
        return spec().machineId();
    }

    public int maxRecipeTicks() {
        return spec().maxRecipeTicks();
    }

    public String overlayPrefix() {
        return spec().overlayPrefix();
    }

    public String nameZh() {
        return spec().nameZh();
    }

    public String nameEn() {
        return spec().nameEn();
    }

    public GTRecipeType recipeType() {
        return spec().recipeType();
    }

    public double euFactor() {
        return spec().euFactor();
    }

    public double durationFactor() {
        return spec().durationFactor();
    }

    public double parallelFactor() {
        return spec().parallelFactor();
    }

    public boolean unlocksFullMode() {
        return spec().unlocksFullMode();
    }

    public boolean appliesTo(NanochipCalibration.Family family) {
        return spec().appliesTo(family);
    }

    public boolean isRouter() {
        return spec().isRouter();
    }

    public boolean isWaterScaledOrganizer() {
        return spec().isWaterScaledOrganizer();
    }

    public NanochipCalibration.Family organizerFamily() {
        return spec().organizerFamily();
    }

    public String upkeepFluidId() {
        return spec().upkeepFluidId();
    }

    public int upkeepMbPerSecond() {
        return spec().upkeepMbPerSecond();
    }

    public boolean hasUpkeep() {
        return spec().hasUpkeep();
    }

    public String mechanicZh() {
        return spec().mechanicZh();
    }

    public String langKey(String suffix) {
        return spec().langKey(suffix);
    }

    public static NanochipModuleKind byMachineId(String id) {
        if (id == null) {
            return null;
        }
        String key = id.toLowerCase(Locale.ROOT);
        int colon = key.indexOf(':');
        if (colon >= 0) {
            key = key.substring(colon + 1);
        }
        for (NanochipModuleKind kind : values()) {
            if (kind.machineId().equals(key)) {
                return kind;
            }
        }
        return null;
    }
}
