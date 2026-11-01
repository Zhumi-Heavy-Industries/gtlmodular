package com.gtlmodular.api.module;

import net.minecraft.resources.ResourceLocation;

/**
 * 模块种类。每个模块对应“沿用配方来源机器 + 配方表”的映射（设计文档 §2.2）。
 *
 * <p>注意：配方表与机器 id 均为字符串/ResourceLocation，运行时经
 * {@code GTRegistries.MACHINES / RECIPE_TYPES} 弱引用解析；来源机器缺失时对应模块不注册。
 */
public enum ModuleKind {

    /**
     * 模块A：猪咪愤怒选矿机 —— 沿用 <b>浮游选矿</b>（{@code gtceu:flotating_beneficiation}，gtlcore 注册）配方。
     *
     * <p>★ 2026-10-24（玩家要求，仓库提交 442b38d + 0fdcbe7）：
     * 原来沿用电力高炉配方表（{@code gtceu:electric_blast_furnace}），改成浮游选矿；
     * <b>机器 ID 保持 {@code molten_blast_module} 不变</b>，否则已摆放的方块会因为注册名变化失效。
     * 温度检查保留：{@code BLAST_TEMPERATURE_CHECK} 对没有 {@code ebf_temp} 的配方直接放行，
     * 浮游选矿配方不受影响。
     */
    BLAST(
            "molten_blast_module",
            "猪咪愤怒选矿机",
            new ResourceLocation("gtceu", "electric_blast_furnace"),
            new ResourceLocation("gtceu", "flotating_beneficiation")),

    /** 模块B：猪咪黑奴挖矿机 —— 沿用 星核剥离(star_core_stripper) 配方。 */
    STAR_CORE_STRIPPER(
            "molten_stripper_module",
            "猪咪黑奴挖矿机",
            new ResourceLocation("gtladditions", "macro_atomic_resonant_fragment_stripper"),
            new ResourceLocation("gtceu", "star_core_stripper")),

    /** 模块C：猪咪撕咬炼矿机 —— 沿用 天基矿石处理(space_ore_processor) 配方。 */
    SPACE_ORE_PROCESSOR(
            "molten_space_ore_module",
            "猪咪撕咬炼矿机",
            new ResourceLocation("gtladditions", "space_infinity_integrated_ore_processor"),
            new ResourceLocation("gtceu", "space_ore_processor")),

    /** 主控制器：地心涌动能量转换枢纽 —— 无配方，模块汇总识别（M5）。 */
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

    /** 本模组内注册的多方块 id（命名空间 gtlmodular，见 {@code machineId()} 不含命名空间）。 */
    public String machineId() {
        return machineId;
    }

    public ResourceLocation machineRl() {
        // MC 1.20.1：尚无 ResourceLocation.fromNamespaceAndPath 静态方法，用构造器
        return new ResourceLocation("gtlmodular", machineId);
    }

    public String displayNameZh() {
        return displayNameZh;
    }

    /** 配方“内容来源”的现有机器（仅用于核对/提示，不直接引用其类）。 */
    public ResourceLocation sourceMachineId() {
        return sourceMachineId;
    }

    /** 要复用的配方表（RecipeType）ResourceLocation。 */
    public ResourceLocation recipeTypeId() {
        return recipeTypeId;
    }
}
