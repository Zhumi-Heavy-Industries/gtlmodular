package com.gtlmodular.common.data.tentacle;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.OverclockingLogic;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.common.data.GTRecipeModifiers;
import com.gtlmodular.GTLModular;
import com.gtlmodular.common.data.GTLModularMachines;
import com.gtlmodular.common.machine.tentacle.NyarlathotepsTentacleMachine;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/**
 * 奈亚拉托提普之触的注册（{@code gtlmodular:nyarlathoteps_tentacle}）—— GTO
 * {@code gtocore:nyarlathoteps_tentacle} 的移植体。
 *
 * <h2>三张配方表（GTO 原机的 {@code recipeTypes}）</h2>
 * <p>GTO：{@code SUPRACHRONAL_ASSEMBLY_LINE_RECIPES, ASSEMBLY_LINE_RECIPES, CIRCUIT_ASSEMBLY_LINE_RECIPES}。
 * 本整合包三张表都在（gtlcore 注册了前两张的 {@code gtceu:} 命名空间版本：
 * {@code gtceu:suprachronal_assembly_line} / {@code gtceu:circuit_assembly_line}，
 * {@code gtceu:assembly_line} 是 GTCEu 原版），按 id 现取；取不到的自动跳过并告警，
 * 一张都取不到时才放弃注册（不崩服）。
 *
 * <h2>与原机一致的注册口径</h2>
 * <ul>
 *   <li>{@code .allRotation()} ⇒ {@link RotationState#ALL}（六面可旋转，与 GTO 同）；</li>
 *   <li>{@code .overclock()} ⇒ {@link OverclockingLogic#PERFECT_OVERCLOCK}（完美超频）；</li>
 *   <li>控制器外壳 = 分子机械方块（{@code gtlcore:molecular_casing}，与 GTO 的
 *       {@code MOLECULAR_CASING} 对应）；正面覆盖层用 {@code gtceu:block/multiblock/fusion_reactor}
 *       （GTO 也是这张，因为它就是台聚变级巨构）；</li>
 *   <li>并行：见 {@link NyarlathotepsTentacleMachine#parallelMultiplier()}（线圈温度口径）；</li>
 *   <li>线程：见 {@link NyarlathotepsTentacleMachine#THREADS}。</li>
 * </ul>
 *
 * <p>注册调用点：{@link GTLModularMachines#init()}。
 */
public final class NyarlathotepsTentacleMachines {

    /** GTO 原机的三张配方表（按 id 现取，避免本模组硬依赖 gtlcore 的类）。 */
    private static final ResourceLocation[] RECIPE_TYPE_IDS = {
            new ResourceLocation("gtceu", "suprachronal_assembly_line"),
            new ResourceLocation("gtceu", "assembly_line"),
            new ResourceLocation("gtceu", "circuit_assembly_line"),
    };

    /** 控制器外壳贴图 = 分子机械方块（与 GTO 的 {@code workableCasingRenderer} 一致）。 */
    private static final ResourceLocation CASING_TEXTURE =
            new ResourceLocation("gtlcore", "block/molecular_casing");
    /** 正面覆盖层 = 聚变反应堆（GTO 同款）。 */
    private static final ResourceLocation OVERLAY_TEXTURE =
            new ResourceLocation("gtceu", "block/multiblock/fusion_reactor");

    public static MultiblockMachineDefinition NYARLATHOTEPS_TENTACLE;

    private NyarlathotepsTentacleMachines() {
    }

    public static void register() {
        List<GTRecipeType> types = new ArrayList<>();
        for (ResourceLocation id : RECIPE_TYPE_IDS) {
            GTRecipeType type = GTRegistries.RECIPE_TYPES.get(id);
            if (type == null) {
                GTLModular.LOGGER.warn("[gtlmodular] 奈亚拉托提普之触：配方表 {} 未注册，跳过", id);
            } else {
                types.add(type);
            }
        }
        if (types.isEmpty()) {
            GTLModular.LOGGER.warn("[gtlmodular] 跳过奈亚拉托提普之触：三张配方表一张都取不到");
            return;
        }

        // 4 件缺件方块已在 GTLModularMachines.initBlocks()（Forge 方块注册窗口之前）建好条目，
        // 这里只引用，不再触发注册。
        GTLModular.LOGGER.debug("[gtlmodular] 奈亚拉托提普之触：结构缺件方块 = {}", TentacleBlocks.class.getName());

        NYARLATHOTEPS_TENTACLE = GTLModularMachines.REGISTRATE
                .multiblock("nyarlathoteps_tentacle", NyarlathotepsTentacleMachine::new)
                .rotationState(RotationState.ALL)
                .tier(GTValues.UEV)
                .recipeTypes(types.toArray(new GTRecipeType[0]))
                .recipeModifier(GTRecipeModifiers.ELECTRIC_OVERCLOCK.apply(OverclockingLogic.PERFECT_OVERCLOCK))
                .appearanceBlock(NyarlathotepsTentacleMachines::appearanceBlock)
                .pattern(NyarlathotepsTentacleStructures::build)
                .workableCasingRenderer(CASING_TEXTURE, OVERLAY_TEXTURE)
                .langValue("奈亚拉托提普之触")
                .tooltips(tooltips())
                .register();

        GTLModular.LOGGER.info("[gtlmodular] 已注册奈亚拉托提普之触：{}（165×48×49 / {} 张配方表 / 每张表可切 / "
                        + "线圈并行 2^((温度-1800)/900) 封顶 4096 / 4 线程）",
                NYARLATHOTEPS_TENTACLE.getId(), types.size());
    }

    /**
     * 主机描述：<b>GTO 原机 18 行工具提示逐行照搬</b>，只在两处按队长口径改：
     * <ul>
     *   <li>「多线程」段里删掉「允许安装线程仓实现多线程配方并行处理。」一句
     *       —— 本机线程数固定（{@code THREADS = 4}），不收线程仓；</li>
     *   <li>末尾补一行「§7结构来源：§bGTO」。</li>
     * </ul>
     * 行序：8 行设定文案 → 可特殊并行(2) → 并行数(2) → 激光仓(2) → 多线程(2) → 配方类型(1) → 结构来源(1)。
     */
    private static Component[] tooltips() {
        Component[] lines = new Component[TOOLTIP_LINES];
        for (int i = 0; i < lines.length; i++) {
            lines[i] = Component.translatable("gtlmodular.nyarlathoteps_tentacle.tooltip." + i);
        }
        return lines;
    }

    /** 工具提示行数（与 lang 里的 {@code gtlmodular.nyarlathoteps_tentacle.tooltip.N} 对应）。 */
    private static final int TOOLTIP_LINES = 18;

    /** 控制器外观方块：分子机械方块；取不到时退回本模组自造的激光机械方块（不让注册期 NPE）。 */
    private static Block appearanceBlock() {
        Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("gtlcore", "molecular_casing"));
        if (block == null || block == Blocks.AIR) {
            GTLModular.LOGGER.warn("[gtlmodular] 奈亚拉托提普之触：gtlcore:molecular_casing 不存在，"
                    + "控制器外观退回 gtlmodular:laser_casing");
            return TentacleBlocks.LASER_CASING.get();
        }
        return block;
    }
}
