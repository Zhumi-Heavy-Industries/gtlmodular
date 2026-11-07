package com.gtlmodular.common.data.hpfc;

import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.OverclockingLogic;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.common.data.GTRecipeModifiers;
import com.gtlmodular.GTLModular;
import com.gtlmodular.common.data.GTLModularMachines;
import com.gtlmodular.common.machine.hpfc.HyperdimensionalPlasmaFusionCoreMachine;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/**
 * GTO「高维等离子聚变核心」的注册（{@code gtlmodular:hyperdimensional_plasma_fusion_core}）——
 * GTO {@code gtocore:hyperdimensional_plasma_fusion_core} 的移植体。
 *
 * <h2>与 GTO 原机对齐的注册口径</h2>
 * <ul>
 *   <li><b>结构</b>：{@link HpfcStructures}（63×83×47，1:1，控制器在底部凹腔 x=31/y=2/z=30）；</li>
 *   <li><b>配方表</b>：GTO 原机是 {@code DIMENSIONALLY_TRANSCENDENT_PLASMA_FORGE_RECIPES} +
 *       {@code STELLAR_FORGE_RECIPES}。本包 gtlcore 把它们注册在 {@code gtceu:} 命名空间
 *       （{@code gtceu:dimensionally_transcendent_plasma_forge} / {@code gtceu:stellar_forge}）⇒
 *       按 id 现取（不硬依赖 gtlcore 的类），取不到的跳过并告警；一张都取不到才放弃注册。</li>
 *   <li><b>线圈 / 温度</b>：照 GTO {@code DimensionallyTranscendentPlasmaForgeMachine} 与本包
 *       gtlcore DTPF 的同款口径 ——
 *       <ul>
 *         <li>线圈温度 = {@code getCoilType().getCoilTemperature()}；超级热熔线圈（273K，即
 *             {@code kubejs:uruium_coil_block}）按 <b>32000K</b> 计；</li>
 *         <li><b>2026-11-02 队长口径</b>：<b>两张配方表统一</b>按 {@code ebf_temp ≤ 线圈温度}
 *             判定 —— <b>恒星热能熔炼不再限定 32000K 超级热熔线圈</b>，任何加热线圈都能跑两张表
 *             （恒星表配方不带 {@code ebf_temp} 时任何线圈都可执行）；结构 P 位的线圈判定
 *             （{@code Predicates.heatingCoils()}）保持不变。</li>
 *       </ul></li>
 *   <li><b>超频 / 并行 / 激光 / 线程</b>：完美超频
 *       （{@link OverclockingLogic#PERFECT_OVERCLOCK_SUBTICK}）+ 并行仓（并行由独立线程引擎乘一次，
 *       注册时<b>不</b>挂 {@code PARALLEL_HATCH} 修改器，避免重复相乘）+ 结构 B 位接受物品/流体/
 *       能源/激光仓 + <b>1 个线程仓</b>（{@code gtlmodular:thread_hatch_<tier>}）。
 *       线程数由线程仓当前设定值给出，机器走独立线程引擎
 *       （{@link HyperdimensionalPlasmaFusionCoreMachine}），一台机器同时推进 N 条配方。</li>
 *   <li><b>外观</b>：控制器外壳 = {@code gtlcore:block/casings/dimensionally_transcendent_casing}，
 *       正面覆层 = {@code gtceu:block/multiblock/dimensionally_transcendent_plasma_forge}
 *       （gtlcore 已带这套覆层贴图；GTO 原机用的是自家 {@code DimensionallyTranscendentRenderer}，
 *       本包没有 GTOCore 的 TESR，故按 gtlcore DTPF 同款工作台渲染）。</li>
 * </ul>
 *
 * <p><b>为什么新注册一台而不是给 gtlcore DTPF 换壳</b>：队长 2026-11-02 裁决
 * 「新增独立机器 gtlmodular:hyperdimensional_plasma_fusion_core」—— 与 gtlcore 的
 * {@code gtceu:dimensionally_transcendent_plasma_forge} 并存，结构/名称按 GTO 原机。
 *
 * <p>注册调用点：{@link GTLModularMachines#init()}。
 */
public final class HpfcMachines {

    private HpfcMachines() {
    }

    /** GTO 原机的两张配方表（按 id 现取，避免本模组硬依赖 gtlcore 的类）。 */
    private static final ResourceLocation[] RECIPE_TYPE_IDS = {
            new ResourceLocation("gtceu", "dimensionally_transcendent_plasma_forge"),
            new ResourceLocation("gtceu", "stellar_forge"),
    };

    /** 控制器外壳贴图（gtlcore 的镜像方块）。 */
    public static final ResourceLocation CASING_TEXTURE =
            new ResourceLocation("gtlcore", "block/casings/dimensionally_transcendent_casing");
    /** 正面覆盖层（gtlcore 自带的 DTPF 覆层贴图，GTO 同款位置）。 */
    public static final ResourceLocation OVERLAY_TEXTURE =
            new ResourceLocation("gtceu", "block/multiblock/dimensionally_transcendent_plasma_forge");

    public static MultiblockMachineDefinition HYPERDIMENSIONAL_PLASMA_FUSION_CORE;

    /** 注册（仅在 GTM 机器注册窗口内调用）。 */
    public static void register() {
        List<GTRecipeType> types = new ArrayList<>();
        for (ResourceLocation id : RECIPE_TYPE_IDS) {
            GTRecipeType type = GTRegistries.RECIPE_TYPES.get(id);
            if (type == null) {
                GTLModular.LOGGER.warn("[gtlmodular] 高维等离子聚变核心：配方表 {} 未注册，跳过", id);
            } else {
                types.add(type);
            }
        }
        if (types.isEmpty()) {
            GTLModular.LOGGER.warn("[gtlmodular] 跳过高维等离子聚变核心：两张配方表一张都取不到");
            return;
        }

        // 结构缺件方块（16 色 ABS + 2 件陶瓷）已在 GTLModularMachines.initBlocks() 建好条目，
        // 这里只强制类加载，确保静态字段已就位。
        HpfcBlocks.init();

        HYPERDIMENSIONAL_PLASMA_FUSION_CORE = GTLModularMachines.REGISTRATE
                .multiblock("hyperdimensional_plasma_fusion_core",
                        HyperdimensionalPlasmaFusionCoreMachine::new)
                .langValue("高维等离子聚变核心")
                .rotationState(RotationState.NON_Y_AXIS)
                .allowExtendedFacing(false)
                .recipeTypes(types.toArray(new GTRecipeType[0]))
                .tooltips(tooltips())
                // ★ 2026-11-02：并行改由独立线程引擎统一乘一次（机器类 usesParallelHatch()=true），
                //   这里**不再**挂 GTRecipeModifiers.PARALLEL_HATCH —— 否则「修改器 × 引擎」会重复放大。
                .recipeModifier(GTRecipeModifiers.ELECTRIC_OVERCLOCK.apply(
                        OverclockingLogic.PERFECT_OVERCLOCK_SUBTICK))
                .appearanceBlock(() -> blockOr("gtlcore", "dimensionally_transcendent_casing",
                        com.gregtechceu.gtceu.common.data.GTBlocks.HIGH_POWER_CASING.get()))
                .pattern(HpfcStructures::build)
                // 线圈温度闸门在机器类 beforeWorking 里（两张表统一 ebf_temp ≤ 线圈温度）；
                // 这里只补一行「最高炉温」显示。
                .additionalDisplay((controller, components) -> {
                    if (controller.isFormed()
                            && controller instanceof HyperdimensionalPlasmaFusionCoreMachine core) {
                        components.add(Component.translatable("gtceu.multiblock.blast_furnace.max_temperature",
                                Component.literal(com.gregtechceu.gtceu.utils.FormattingUtil.formatNumbers(
                                                core.coilTemperature()) + "K")
                                        .withStyle(ChatFormatting.BLUE)));
                    }
                })
                .workableCasingRenderer(CASING_TEXTURE, OVERLAY_TEXTURE)
                .register();

        GTLModular.LOGGER.info("[gtlmodular] 已注册高维等离子聚变核心：{}（63×83×47 / {} 张配方表 / "
                        + "独立线程引擎：线程数由线程仓给出，无线程仓 1 条 / 两表统一 ebf_temp≤线圈温度，"
                        + "超级热熔线圈按 32000K）",
                HYPERDIMENSIONAL_PLASMA_FUSION_CORE.getId(), types.size());
    }

    /** 工具提示行数（与 lang 的 {@code gtlmodular.hyperdimensional_plasma_fusion_core.tooltip.N} 对应）。 */
    private static final int TOOLTIP_LINES = 9;

    /** 9 行工具提示：照 GTO 任务书「可并行 / 激光仓 / 多线程 / 配方类型」+ 结构来源。 */
    private static Component[] tooltips() {
        Component[] lines = new Component[TOOLTIP_LINES];
        for (int i = 0; i < lines.length; i++) {
            lines[i] = Component.translatable("gtlmodular.hyperdimensional_plasma_fusion_core.tooltip." + i);
        }
        return lines;
    }

    /** 按 id 取方块，取不到退回 fallback（注册期绝不 NPE）。 */
    private static Block blockOr(String namespace, String path, Block fallback) {
        Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(namespace, path));
        if (block == null) {
            GTLModular.LOGGER.warn("[gtlmodular] 高维等离子聚变核心：外观方块 {}/{} 取不到，退回 {}",
                    namespace, path, ForgeRegistries.BLOCKS.getKey(fallback));
            return fallback;
        }
        return block;
    }
}
