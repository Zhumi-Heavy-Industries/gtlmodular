package com.gtlmodular.common.machine.nanochip.registration;

import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gtlmodular.common.machine.nanochip.NanochipCalibration;
import com.gtlmodular.common.machine.nanochip.NanochipModuleKind;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Supplier;

/** 规格只保存定义；配方表延迟求值，避免枚举、配方图标与机器注册互相触发初始化。 */
public final class NanochipModuleSpec {

    public record Display(String overlayPrefix, String nameZh, String nameEn, String mechanicZh) {
        public Display {
            Objects.requireNonNull(overlayPrefix, "overlayPrefix");
            Objects.requireNonNull(nameZh, "nameZh");
            Objects.requireNonNull(nameEn, "nameEn");
            Objects.requireNonNull(mechanicZh, "mechanicZh");
        }
    }

    public record Bonuses(double euFactor, double durationFactor, double parallelFactor,
                          boolean unlocksFullMode, List<NanochipCalibration.Family> familyFilter) {
        public Bonuses {
            if (!positiveFinite(euFactor) || !positiveFinite(durationFactor) || !positiveFinite(parallelFactor)) {
                throw new IllegalArgumentException("NAC module multipliers must be positive and finite");
            }
            familyFilter = List.copyOf(familyFilter);
        }

        public boolean appliesTo(NanochipCalibration.Family family) {
            return familyFilter.isEmpty() || family == null || familyFilter.contains(family);
        }
    }

    public record Operation(int maxRecipeTicks, boolean router, NanochipCalibration.Family organizerFamily,
                            String upkeepFluidId, int upkeepMbPerSecond) {
        public Operation {
            if (maxRecipeTicks <= 0) {
                throw new IllegalArgumentException("NAC module time budget must be positive");
            }
            if (upkeepMbPerSecond < 0 || (upkeepFluidId == null) != (upkeepMbPerSecond == 0)) {
                throw new IllegalArgumentException("NAC module upkeep fluid and rate must be supplied together");
            }
            if (upkeepFluidId != null) {
                new ResourceLocation(upkeepFluidId);
            }
        }
    }

    private final ResourceLocation id;
    private final NanochipModuleKind legacyKind;
    private final Display display;
    private final Supplier<GTRecipeType> recipeType;
    private final Bonuses bonuses;
    private final Operation operation;
    private final Features features;
    private final String template;
    private final ResourceLocation frame;
    private final ResourceLocation casing;
    private final ResourceLocation overlay;

    public record Features(boolean assembly, boolean board, boolean laser, boolean biologicalExemption,
                           int energyPriority) {
    }

    public static Builder opticalOrganizer(ResourceLocation id) {
        return new Builder(id);
    }

    public static Builder opticalOrganizer(String id) {
        return opticalOrganizer(NanochipModules.parseId(id));
    }

    public Features features() { return features; }
    public String template() { return template; }
    public ResourceLocation frame() { return frame; }
    public ResourceLocation casing() { return casing; }
    public ResourceLocation overlay() { return overlay; }
    public int energyPriority() { return features.energyPriority(); }

    /** 外部规格没有旧枚举身份，所有运行策略必须读取规格本身。 */
    private NanochipModuleSpec(Builder builder) {
        id = builder.id;
        legacyKind = null;
        display = builder.display;
        recipeType = builder.recipeType;
        bonuses = builder.bonuses;
        operation = builder.operation;
        features = builder.features;
        template = builder.template;
        frame = builder.frame;
        casing = builder.casing;
        overlay = builder.overlay;
    }

    public static final class Builder {
        private final ResourceLocation id;
        private Display display;
        private Supplier<GTRecipeType> recipeType = () ->
                com.gtlmodular.common.data.nanochip.NanochipRecipeTypes.OPTICAL_ORGANIZER;
        private Bonuses bonuses = new Bonuses(0.90D, 0.90D, 1.0D, false, List.of());
        private Operation operation = new Operation(200, false, NanochipCalibration.Family.OPTICAL,
                "gtceu:grade_1_purified_water", 100);
        private Features features = new Features(false, false, false, false, 1);
        private String template = "nanochip_optical_organizer";
        private ResourceLocation frame;
        private ResourceLocation casing = new ResourceLocation("gtlmodular", "block/casings/nanochip_mesh_interface_casing");
        private ResourceLocation overlay = new ResourceLocation("gtlmodular", "block/multiblock/nanochip_optical_organizer");

        private Builder(ResourceLocation id) {
            this.id = Objects.requireNonNull(id, "id");
            display = new Display("OPTICAL_ORGANIZER", id.toString(), id.toString(), "");
        }

        public Builder displayName(String name) {
            display = new Display(display.overlayPrefix(), name, name, display.mechanicZh());
            return this;
        }
        public Builder display(Display value) { display = Objects.requireNonNull(value); return this; }
        public Builder recipeType(Supplier<GTRecipeType> value) { recipeType = Objects.requireNonNull(value); return this; }
        public Builder recipeType(ResourceLocation value) {
            Objects.requireNonNull(value);
            return recipeType(() -> com.gregtechceu.gtceu.api.registry.GTRegistries.RECIPE_TYPES.get(value));
        }
        public Builder bonuses(Bonuses value) { bonuses = Objects.requireNonNull(value); return this; }
        public Builder operation(Operation value) { operation = Objects.requireNonNull(value); return this; }
        public Builder features(Features value) { features = Objects.requireNonNull(value); return this; }
        public Builder template(String value) { template = Objects.requireNonNull(value); return this; }
        public Builder frame(ResourceLocation value) { frame = Objects.requireNonNull(value); return this; }
        public Builder casing(ResourceLocation value) { casing = Objects.requireNonNull(value); return this; }
        public Builder overlay(ResourceLocation value) { overlay = Objects.requireNonNull(value); return this; }
        public NanochipModuleSpec build() { return new NanochipModuleSpec(this); }
    }

    NanochipModuleSpec(ResourceLocation id, NanochipModuleKind legacyKind, Display display,
                       Supplier<GTRecipeType> recipeType, Bonuses bonuses, Operation operation) {
        this.id = Objects.requireNonNull(id, "id");
        this.legacyKind = Objects.requireNonNull(legacyKind, "legacyKind");
        this.display = Objects.requireNonNull(display, "display");
        this.recipeType = Objects.requireNonNull(recipeType, "recipeType");
        this.bonuses = Objects.requireNonNull(bonuses, "bonuses");
        this.operation = Objects.requireNonNull(operation, "operation");
        this.features = NanochipBuiltinModules.features(legacyKind);
        this.template = id.getPath();
        this.frame = null;
        this.casing = com.gtlmodular.common.data.nanochip.NanochipModContext.assetId("block/casings/nanochip_mesh_interface_casing");
        this.overlay = com.gtlmodular.common.data.nanochip.NanochipModContext.assetId("block/multiblock/" + id.getPath());
        String expectDir = overlayDirOf(display.overlayPrefix());
        if (!expectDir.equals(id.getPath())) {
            throw new IllegalStateException("NanochipModuleKind." + legacyKind.name()
                    + ": NH 图标集名前缀 " + display.overlayPrefix() + " 推出的贴图目录是 " + expectDir
                    + "，而 machineId 声明为 " + id.getPath()
                    + " —— 两者必须一致（要加例外请改 overlayDirOf(String) 并同步注释与贴图目录）");
        }
    }

    private static boolean positiveFinite(double value) {
        return Double.isFinite(value) && value > 0;
    }

    private static String overlayDirOf(String overlayPrefix) {
        if ("BIOLOGICAL_COORDINATION".equals(overlayPrefix)) {
            return "nanochip_biological_coordinator";
        }
        return "nanochip_" + overlayPrefix.toLowerCase(Locale.ROOT);
    }

    public ResourceLocation id() {
        return id;
    }

    public NanochipModuleKind legacyKind() {
        return legacyKind;
    }

    public Display display() {
        return display;
    }

    public Bonuses bonuses() {
        return bonuses;
    }

    public Operation operation() {
        return operation;
    }

    public String machineId() {
        return id.getPath();
    }

    public String overlayPrefix() {
        return display.overlayPrefix();
    }

    public String nameZh() {
        return display.nameZh();
    }

    public String nameEn() {
        return display.nameEn();
    }

    public String mechanicZh() {
        return display.mechanicZh();
    }

    public String langKey(String suffix) {
        return legacyKind != null ? "gtlmodular.nanochip.module." + machineId() + "." + suffix
                : id.getNamespace() + ".nanochip.module." + machineId() + "." + suffix;
    }

    public GTRecipeType recipeType() {
        return recipeType.get();
    }

    public double euFactor() {
        return bonuses.euFactor();
    }

    public double durationFactor() {
        return bonuses.durationFactor();
    }

    public double parallelFactor() {
        return bonuses.parallelFactor();
    }

    public boolean unlocksFullMode() {
        return bonuses.unlocksFullMode();
    }

    public boolean appliesTo(NanochipCalibration.Family family) {
        return bonuses.appliesTo(family);
    }

    /** 此时间预算用于模块缓存容量，实际配方耗电更大时仍须扩容。 */
    public int maxRecipeTicks() {
        return operation.maxRecipeTicks();
    }

    public boolean isRouter() {
        return operation.router();
    }

    public NanochipCalibration.Family organizerFamily() {
        return operation.organizerFamily();
    }

    public boolean isWaterScaledOrganizer() {
        return organizerFamily() != null;
    }

    public String upkeepFluidId() {
        return operation.upkeepFluidId();
    }

    public int upkeepMbPerSecond() {
        return operation.upkeepMbPerSecond();
    }

    public boolean hasUpkeep() {
        return upkeepFluidId() != null && !upkeepFluidId().isBlank() && upkeepMbPerSecond() > 0;
    }
}
