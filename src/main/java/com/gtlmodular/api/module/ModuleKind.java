package com.gtlmodular.api.module;

import net.minecraft.resources.ResourceLocation;

/** 用注册 ID 延迟解析配方表，避免枚举初始化时读取尚未注册的对象。 */
public enum ModuleKind {

    BLAST(
            "molten_blast_module",
            "猪咪愤怒烧矿机",
            new ResourceLocation("gtceu", "electric_blast_furnace"),
            new ResourceLocation("gtceu", "electric_blast_furnace")),

    STAR_CORE_STRIPPER(
            "molten_stripper_module",
            "猪咪黑奴挖矿机",
            new ResourceLocation("gtladditions", "macro_atomic_resonant_fragment_stripper"),
            new ResourceLocation("gtceu", "star_core_stripper")),

    SPACE_ORE_PROCESSOR(
            "molten_space_ore_module",
            "猪咪撕咬炼矿机",
            new ResourceLocation("gtladditions", "space_infinity_integrated_ore_processor"),
            new ResourceLocation("gtceu", "space_ore_processor")),

    HOST(
            "molten_module_host",
            "地心涌动能量转换枢纽",
            new ResourceLocation("gtlmodular", "molten_module_host"),
            new ResourceLocation("gtlmodular", "molten_module_host"));

    private final String machineId;
    private final String displayNameZh;
    private final ResourceLocation sourceMachineId;
    private final ResourceLocation recipeTypeId;

    ModuleKind(String machineId, String displayNameZh,
               ResourceLocation sourceMachineId, ResourceLocation recipeTypeId) {
        this.machineId = machineId;
        this.displayNameZh = displayNameZh;
        this.sourceMachineId = sourceMachineId;
        this.recipeTypeId = recipeTypeId;
    }

    public String machineId() {
        return machineId;
    }

    public ResourceLocation machineRl() {
        return new ResourceLocation("gtlmodular", machineId);
    }

    public String displayNameZh() {
        return displayNameZh;
    }

    public ResourceLocation sourceMachineId() {
        return sourceMachineId;
    }

    public ResourceLocation recipeTypeId() {
        return recipeTypeId;
    }
}
