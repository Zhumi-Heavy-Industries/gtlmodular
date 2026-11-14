package com.gtlmodular.common.data.fieldforge;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.common.data.GTRecipeModifiers;
import com.gtlmodular.GTLModular;
import com.gtlmodular.common.data.GTLModularMachines;
import com.gtlmodular.common.data.tentacle.ComponentPartsRecipeTypes;
import com.gtlmodular.common.machine.fieldforge.FieldExtruderFactoryMachine;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/**
 * 力场压模零件工厂的注册（{@code gtlmodular:field_extruder_factory}）—— GTO
 * {@code gtocore:field_extruder_factory} 的移植体。
 *
 * <h2>与 GTO 原机的差异（唯一一处实质改动）</h2>
 * <p>GTO 原机：{@code .recipeTypes(GTRecipeTypes.EXTRUDER_RECIPES)}（压模机表）。
 * <b>本机按队长 2026-11-14 口径改跑本模组自建的「零部件加工」表</b>
 * {@code gtlmodular:component_parts} —— 即奈亚拉托提普之触新增的那个模式所用的同一张表，
 * <b>不再是 GTO 的压模机表</b>。</p>
 *
 * <h2>与原机一致的注册口径</h2>
 * <ul>
 *   <li>{@code nonYAxisRotation} ⇒ {@link RotationState#NON_Y_AXIS}（只能水平朝向）；</li>
 *   <li>并行：GTO 用 {@code CrossRecipeMultiblockMachine::createHatchParallel} ⇒
 *       结构里并行仓的并行数即整机并行数（本机用 {@code GTRecipeModifiers.PARALLEL_HATCH} 表达同一语义）；</li>
 *   <li>激光仓 / 多配方并发：见 {@code .laserTooltips()} 的等价物（结构 B 位已开放 INPUT_LASER）；</li>
 *   <li>控制器外观：{@code gtceu:block/casings/hpca/high_power_casing} +
 *       {@code gtocore:block/multiblock/general1}（GTO 同款；覆盖层贴图本模组已内置，见
 *       {@code assets/gtlmodular/textures/block/multiblock/general1/}）。</li>
 * </ul>
 *
 * <h2>仓室（队长口径：线程 / 超频 / 加速 / 并行 四类都要能放）</h2>
 * <p>B 位（{@code gtceu:high_power_casing}，88 格）开放：物品/流体 进出、能源输入（≤8）、
 * 激光输入（≤4）、<b>线程仓</b>（≤1）、<b>超频仓</b>、<b>加速仓</b>、<b>并行仓</b>（≤1）、维护仓（恰好 1）。
 * 谓词写在 {@link FieldExtruderFactoryStructures#build}。</p>
 *
 * <p>注册调用点：{@link GTLModularMachines#init()}。</p>
 */
public final class FieldExtruderFactoryMachines {

    /** 控制器外观方块 = 高能机器外壳（GTO {@code .block(GTBlocks.HIGH_POWER_CASING)}）。 */
    public static final ResourceLocation CASING_TEXTURE =
            new ResourceLocation("gtceu", "block/casings/hpca/high_power_casing");
    /** 正面覆盖层 = GTO {@code gtocore:block/multiblock/general1}（本模组已镜像该贴图）。 */
    public static final ResourceLocation OVERLAY_TEXTURE =
            new ResourceLocation(GTLModular.MOD_ID, "block/multiblock/general1");

    public static MultiblockMachineDefinition FIELD_EXTRUDER_FACTORY;

    /** 工具提示行数（与 lang 里的 {@code gtlmodular.field_extruder_factory.tooltip.N} 对应）。 */
    private static final int TOOLTIP_LINES = 14;

    private FieldExtruderFactoryMachines() {
    }

    public static void register() {
        List<GTRecipeType> types = new ArrayList<>();
        // ★ 队长口径：跑「零部件加工」表，不跑 GTO 的压模机表。
        GTRecipeType partsType = ComponentPartsRecipeTypes.COMPONENT_PARTS();
        if (partsType == null) {
            GTLModular.LOGGER.warn("[gtlmodular] 跳过力场压模零件工厂：零部件加工配方表未注册");
            return;
        }
        types.add(partsType);

        GTLModular.LOGGER.debug("[gtlmodular] 力场压模零件工厂：结构 = {}", FieldExtruderFactoryStructures.class.getName());

        FIELD_EXTRUDER_FACTORY = GTLModularMachines.REGISTRATE
                .multiblock("field_extruder_factory", FieldExtruderFactoryMachine::new)
                // GTO 的 .nonYAxisRotation()
                .rotationState(RotationState.NON_Y_AXIS)
                .tier(GTValues.UEV)
                .recipeTypes(types.toArray(new GTRecipeType[0]))
                // ★ 2026-11-15：**不再挂 GTRecipeModifiers.PARALLEL_HATCH**。
                //   本机已由独立线程引擎（ThreadedRecipeLogic）接管，并行在引擎的
                //   findBatch() 里按「parallelMultiplier() × 并行仓并行数」乘一次；
                //   再挂 PARALLEL_HATCH 会**重复乘一遍**（并行被平方）。
                //   对照：奈亚拉托提普之触 / 高维等离子聚变核心两台引擎机都只挂超频、不挂并行仓修饰。
                //   并行口径见 FieldExtruderFactoryMachine#parallelMultiplier() / usesParallelHatch()。
                // ★ 2026-11-15：改走【通用超频口径入口】OverclockProfiles.apply ——
                //   它内部会读结构里的 **超频仓 / 加速仓**（OverclockHatchPartMachine 的时间倍率），
                //   默认口径 = 完美超频 + 亚 tick（OverclockingLogic.PERFECT_OVERCLOCK_SUBTICK）。
                //   若继续写死 ELECTRIC_OVERCLOCK.apply(PERFECT_OVERCLOCK)，
                //   超频仓/加速仓会「装得上但不生效」（与冷却塔 2026-11-10 修过的同一类问题）。
                .recipeModifier((machine, recipe, params, result) ->
                        com.gtlmodular.common.machine.fuel.OverclockProfiles.apply(machine, recipe, params, result))
                .appearanceBlock(FieldExtruderFactoryMachines::appearanceBlock)
                .pattern(FieldExtruderFactoryStructures::build)
                .workableCasingRenderer(CASING_TEXTURE, OVERLAY_TEXTURE)
                .langValue("力场压模零件工厂")
                .tooltips(tooltips())
                .register();

        GTLModular.LOGGER.info("[gtlmodular] 已注册力场压模零件工厂：{}（29×15×15 / 配方表 {} / "
                        + "线程仓·超频仓·加速仓·并行仓 均可放）",
                FIELD_EXTRUDER_FACTORY.getId(), types.get(0).registryName);
    }

    /**
     * 主机描述：<b>GTO 原机 6 行逐字照搬</b>（{@code GTOMachineStories.FieldExtruderFactoryTooltips}），
     * 其后补本机的仓室/配方类型说明 —— 因为改了配方表，必须让玩家一眼看到。
     */
    private static Component[] tooltips() {
        Component[] lines = new Component[TOOLTIP_LINES];
        for (int i = 0; i < lines.length; i++) {
            lines[i] = Component.translatable("gtlmodular.field_extruder_factory.tooltip." + i);
        }
        return lines;
    }

    /** 控制器外观方块：高能机器外壳；取不到时退回本模组自造的激光机械方块（不让注册期 NPE）。 */
    private static Block appearanceBlock() {
        Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("gtceu", "high_power_casing"));
        if (block == null || block == Blocks.AIR) {
            GTLModular.LOGGER.warn("[gtlmodular] 力场压模零件工厂：gtceu:high_power_casing 不存在，"
                    + "控制器外观退回 gtlmodular:laser_casing");
            return com.gtlmodular.common.data.tentacle.TentacleBlocks.LASER_CASING.get();
        }
        return block;
    }
}
