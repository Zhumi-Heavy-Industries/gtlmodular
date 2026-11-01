package com.gtlmodular.common.data.pcb;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.OverclockingLogic;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.common.data.GCyMBlocks;
import com.gregtechceu.gtceu.common.data.GTRecipeModifiers;
import com.gtlmodular.GTLModular;
import com.gtlmodular.common.data.GTLModularMachines;
import com.gtlmodular.common.machine.pcb.SmallPcbFactoryMachine;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * 小型 PCB 工厂的注册（{@code gtlmodular:small_pcb_factory}）。
 *
 * <p>定位：增强版核心把 {@code gtceu:pcb_factory} 换成了 77×34×45 的巨构之后，玩家要很晚才能碰 PCB。
 * 这台机器用 gtlcore <b>原始</b>那版 17×10×15 结构，让 EV 期就能开 PCB 产线。
 *
 * <p>配方表按 id {@code gtceu:pcb_factory} 现取（本机不 import gtlcore 的类）；取不到就跳过注册并告警。
 *
 * <p>能力集（队长口径）：
 * <ul>
 *   <li>两条独立线程（{@link SmallPcbFactoryMachine#THREADS}）；</li>
 *   <li>自带并行 1024（不乘并行仓）；</li>
 *   <li>结构不开放激光仓、不开放并行仓；</li>
 *   <li>LuV 及以上配方能耗 ×4（{@link SmallPcbFactoryMachine#HIGH_TIER_EUT}）。</li>
 * </ul>
 *
 * <p>注册调用点：{@link GTLModularMachines#init()}。
 */
public final class SmallPcbFactoryMachines {

    /** 复用 GTL 的 PCB 工厂配方表（KubeJS 里 {@code event.recipes.gtceu.pcb_factory(...)} 写的就是这张表）。 */
    private static final ResourceLocation PCB_FACTORY_RECIPES =
            new ResourceLocation("gtceu", "pcb_factory");

    /** 外壳贴图与控制器正面覆盖层：与 gtlcore 原版 PCB 工厂同一套（水密外壳 + 大型研磨塔覆盖层）。 */
    private static final ResourceLocation CASING_TEXTURE =
            new ResourceLocation("gtceu", "block/casings/gcym/watertight_casing");
    private static final ResourceLocation OVERLAY_TEXTURE =
            new ResourceLocation("gtceu", "block/multiblock/gcym/large_maceration_tower");

    public static MultiblockMachineDefinition SMALL_PCB_FACTORY;

    private SmallPcbFactoryMachines() {
    }

    public static void register() {
        GTRecipeType recipeType = GTRegistries.RECIPE_TYPES.get(PCB_FACTORY_RECIPES);
        if (recipeType == null) {
            GTLModular.LOGGER.warn("[gtlmodular] 跳过小型PCB工厂：配方表 {} 未注册", PCB_FACTORY_RECIPES);
            return;
        }

        SMALL_PCB_FACTORY = GTLModularMachines.REGISTRATE
                .multiblock("small_pcb_factory", SmallPcbFactoryMachine::new)
                .rotationState(RotationState.NON_Y_AXIS)
                .allowExtendedFacing(false)
                .tier(GTValues.EV)
                .recipeType(recipeType)
                .recipeModifier(GTRecipeModifiers.ELECTRIC_OVERCLOCK
                        .apply(OverclockingLogic.NON_PERFECT_OVERCLOCK))
                .appearanceBlock(GCyMBlocks.CASING_WATERTIGHT)
                .pattern(SmallPcbFactoryStructures::build)
                .workableCasingRenderer(CASING_TEXTURE, OVERLAY_TEXTURE)
                .langValue("小型PCB工厂")
                .tooltips(
                        Component.translatable("gtlmodular.small_pcb_factory.tooltip.0"),
                        Component.translatable("gtlmodular.small_pcb_factory.tooltip.1"),
                        Component.translatable("gtlmodular.small_pcb_factory.tooltip.2"))
                .register();

        GTLModular.LOGGER.info("[gtlmodular] 已注册小型PCB工厂：{}（EV / 2 线程 / 自带并行 1024 / 无激光仓）",
                SMALL_PCB_FACTORY.getId());
    }
}
