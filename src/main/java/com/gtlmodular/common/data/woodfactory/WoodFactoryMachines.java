package com.gtlmodular.common.data.woodfactory;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gtlmodular.GTLModular;
import com.gtlmodular.common.data.GTLModularMachines;
import com.gtlmodular.common.machine.woodfactory.WoodBiomassFactoryMachine;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 集成木质生物质工厂的注册（{@code gtlmodular:wood_biomass_factory}）—— GTO
 * {@code gtocore:wood_distillation}（中文名「木化工厂」，{@code MultiBlockB.java:74}）的移植体。
 *
 * <h2>与 GTO 原机的唯一差别：名字</h2>
 * <p>按队长口径：<b>机器运行模式、机器配方全部照搬</b>，只把机器名从「木化工厂」改成
 * <b>「集成木质生物质工厂」</b>。因此：
 * <ul>
 *   <li>结构 1:1（28×56×60，60 片 mbs 直转，见 {@link WoodFactoryStructures}）；</li>
 *   <li>配方表 = 本模组自建的 {@code gtlmodular:wood_distillation}（显示名「集成木质生物质热解」，
 *       与 GTO 同名同参数：2 物品进 / 1 物品出 / 1 流体进 / 20 流体出、EU 输入、箭头进度条、浴场音效）；</li>
 *   <li>三条配方逐项照搬（氮气 / 蒸汽 / 水，电路 1/2/3，EU/t 120，300 tick），
 *       见 {@code data/gtlmodular/recipes/wood_distillation_with_*.json}；</li>
 *   <li>运行模式 = 线圈并行 {@code 2^(⌊温度/900⌋)} + 激光仓 + 模块口仓室
 *       （{@link WoodBiomassFactoryMachine}）。</li>
 * </ul>
 *
 * <h2>为什么不再是 GTO 的 {@code gtceu:} 配方表</h2>
 * <p>GTO 的 {@code wood_distillation} 表注册在 {@code gtceu:} 命名空间（它魔改了 GTCEu 的
 * {@code RecipeTypeRegisterUtils}）。本包没用 GTO，直接照 gtlcore/gtladditions 的做法规避：
 * 本模组自建同参数的表，机器与配方都指向它，<b>不与任何上游抢 id</b>。
 *
 * <p>注册调用点：{@link GTLModularMachines#init()}。
 */
public final class WoodFactoryMachines {

    /** 控制器外观方块 = 高能机器外壳（GTO {@code .block(GTBlocks.HIGH_POWER_CASING)}）。 */
    public static final ResourceLocation CASING_TEXTURE =
            new ResourceLocation("gtceu", "block/casings/hpca/high_power_casing");
    /** 正面覆盖层 = {@code gtceu:block/multiblock/blast_furnace}（GTO 原机同一个覆盖层）。 */
    public static final ResourceLocation OVERLAY_TEXTURE =
            new ResourceLocation("gtceu", "block/multiblock/blast_furnace");

    public static MultiblockMachineDefinition WOOD_BIOMASS_FACTORY;

    /** 工具提示行数（与 lang 里的 {@code gtlmodular.wood_biomass_factory.tooltip.N} 对应）。 */
    private static final int TOOLTIP_LINES = 12;

    private WoodFactoryMachines() {
    }

    public static void register() {
        GTRecipeType recipeType = GTRegistries.RECIPE_TYPES.get(WoodFactoryRecipeTypes.WOOD_DISTILLATION_ID);
        if (recipeType == null) {
            GTLModular.LOGGER.warn("[gtlmodular] 跳过集成木质生物质工厂：配方表 {} 未注册",
                    WoodFactoryRecipeTypes.WOOD_DISTILLATION_ID);
            return;
        }

        WOOD_BIOMASS_FACTORY = GTLModularMachines.REGISTRATE
                .multiblock("wood_biomass_factory", WoodBiomassFactoryMachine::new)
                // GTO 的 .nonYAxisRotation()
                .rotationState(RotationState.NON_Y_AXIS)
                // 控制器外壳 = 高能机壳族（GTO 原机 .block(GTBlocks.HIGH_POWER_CASING)）
                .tier(GTValues.UEV)
                .recipeType(recipeType)
                // 不挂 GTRecipeModifiers.PARALLEL_HATCH：本机由独立线程引擎接管，
                // 并行在引擎 findBatch() 里按「线圈并行 × 并行仓」算一次（与力场压模零件工厂同口径）。
                // 超频走【通用超频口径入口】OverclockProfiles.apply：它内部读结构里的
                // 超频仓 / 加速仓，默认完美超频 + 亚 tick。
                .recipeModifier((machine, recipe, params, result) ->
                        com.gtlmodular.common.machine.fuel.OverclockProfiles.apply(machine, recipe, params, result))
                .appearanceBlock(WoodFactoryMachines::appearanceBlock)
                .pattern(WoodFactoryStructures::build)
                .workableCasingRenderer(CASING_TEXTURE, OVERLAY_TEXTURE)
                .langValue("集成木质生物质工厂")
                .tooltips(tooltips())
                .register();

        GTLModular.LOGGER.info("[gtlmodular] 已注册集成木质生物质工厂：{}（28×56×60 / 配方表 {} / "
                        + "线圈并行 2^(温度/900) / 激光仓 / 模块口可装线程·超频·加速·并行仓）",
                WOOD_BIOMASS_FACTORY.getId(), recipeType.registryName);
    }

    /**
     * 主机描述：<b>GTO 原机 5 行故事 + 1 行高亮逐字照搬</b>
     * （{@code GTOMachineStories.WoodDistillationTooltips}，lang 键
     * {@code gtocore.lang.wood_distillation.0..5}），其后补本机的运行模式 / 仓室 / 配方类型说明。
     */
    private static Component[] tooltips() {
        Component[] lines = new Component[TOOLTIP_LINES];
        for (int i = 0; i < lines.length; i++) {
            lines[i] = Component.translatable("gtlmodular.wood_biomass_factory.tooltip." + i);
        }
        return lines;
    }

    /** 控制器外观方块：高能机器外壳；取不到时退回本模组自造的耐压机壳（不让注册期 NPE）。 */
    private static Block appearanceBlock() {
        Block block = ForgeRegistries.BLOCKS.getValue(
                new ResourceLocation("gtceu", "high_power_casing"));
        if (block == null || block == Blocks.AIR) {
            GTLModular.LOGGER.warn("[gtlmodular] 集成木质生物质工厂：gtceu:high_power_casing 不存在，"
                    + "控制器外观退回 gtlmodular:pressure_containment_casing");
            return WoodFactoryBlocks.PRESSURE_CONTAINMENT_CASING.get();
        }
        return block;
    }
}
