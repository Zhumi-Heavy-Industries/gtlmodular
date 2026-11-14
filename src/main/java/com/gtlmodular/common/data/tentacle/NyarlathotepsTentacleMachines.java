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
 * <h2>配方表（GTO 原机的 {@code recipeTypes}，外加本包增补）</h2>
 * <p>GTO：{@code SUPRACHRONAL_ASSEMBLY_LINE_RECIPES, ASSEMBLY_LINE_RECIPES, CIRCUIT_ASSEMBLY_LINE_RECIPES}。
 * 本整合包这几张表都在（gtlcore 注册了前两张的 {@code gtceu:} 命名空间版本：
 * {@code gtceu:suprachronal_assembly_line} / {@code gtceu:circuit_assembly_line}，
 * {@code gtceu:assembly_line} 是 GTCEu 原版），按 id 现取；取不到的自动跳过并告警，
 * 一张都取不到时才放弃注册（不崩服）。
 * <p>另外挂上了本模组自建的 {@code gtlmodular:component_parts}，
 * 使它多出一个「零部件加工」模式（一锭出全套零件的批量零件配方）。
 *
 * <h2>与原机一致的注册口径</h2>
 * <ul>
 *   <li>{@code .allRotation()} ⇒ {@link RotationState#ALL}（六面可旋转，与 GTO 同）；</li>
 *   <li>{@code .overclock()} ⇒ {@link OverclockingLogic#PERFECT_OVERCLOCK}（完美超频）；</li>
 *   <li>控制器外壳 = 分子机械方块（{@code gtlcore:molecular_casing}，与 GTO 的
 *       {@code MOLECULAR_CASING} 对应）；正面覆盖层用 {@code gtceu:block/multiblock/fusion_reactor}
 *       （GTO 也是这张，因为它就是台聚变级巨构）；</li>
 *   <li>并行：见 {@link NyarlathotepsTentacleMachine#parallelMultiplier()}（线圈温度口径）；</li>
 *   <li>线程：不再固定 —— 结构外壳位接受 {@code gtladditions:thread_modifier_hatch}
 *       （Ω-天球分歧引擎），线程数由该仓给出；无线程仓时 1 条
 *       （见 {@link NyarlathotepsTentacleMachine#totalThreads()}）。</li>
 * </ul>
 *
 * <p>注册调用点：{@link GTLModularMachines#init()}。
 */
public final class NyarlathotepsTentacleMachines {

    /**
     * GTO 原机的三张配方表。
     *
     * <p>按 id 现取而不是直接引用 {@code GTLRecipeTypes}，是为了在本模组未与 gtlcore 同时加载时
     * 也能正常注册（取不到的那张会在 {@link #register()} 里被跳过）。</p>
     */
    private static final ResourceLocation[] RECIPE_TYPE_IDS = {
            new ResourceLocation("gtceu", "suprachronal_assembly_line"),
            new ResourceLocation("gtceu", "assembly_line"),
            new ResourceLocation("gtceu", "circuit_assembly_line"),
    };

    /** 控制器外壳贴图 = 分子机械方块（与 GTO 的 {@code workableCasingRenderer} 一致）。 */
    public static final ResourceLocation CASING_TEXTURE =
            new ResourceLocation("gtlcore", "block/molecular_casing");
    /** 正面覆盖层 = 聚变反应堆（GTO 同款）。 */
    public static final ResourceLocation OVERLAY_TEXTURE =
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
        // 「零部件加工」：本模组自建的单一配方表，不共用 gtlcore:component_assembly_line
        // （那张表同时被部件组装机使用，挂上去会连带改写部件组装机）。
        GTRecipeType partsType = ComponentPartsRecipeTypes.COMPONENT_PARTS();
        if (partsType != null) {
            types.add(partsType);
        }
        if (types.isEmpty()) {
            GTLModular.LOGGER.warn("[gtlmodular] 跳过奈亚拉托提普之触：配方表一张都取不到");
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
                .recipeModifier(GTRecipeModifiers.ELECTRIC_OVERCLOCK.apply(
                        // ★ 2026-11-02 队长口径：改为「无损超频」——EUt 不随超频上涨（电压系数 1.0），
                        //   只缩短时间（沿用完美超频的 duration 系数 0.25/级）。
                        //   GTCEu 1.4.4 的 OverclockingLogic(double voltageFactor, double durationFactor, boolean subTick)
                        //   正好能表达该语义；本包 GTLcore/GTLAdditions/gtmthings 均无现成 Lossless 实现（已全 jar 检索）。
                        new OverclockingLogic(1.0, OverclockingLogic.PERFECT_DURATION_FACTOR, false)))
                .appearanceBlock(NyarlathotepsTentacleMachines::appearanceBlock)
                .pattern(NyarlathotepsTentacleStructures::build)
                .workableCasingRenderer(CASING_TEXTURE, OVERLAY_TEXTURE)
                .langValue("奈亚拉托提普之触")
                .tooltips(tooltips())
                .register();

        // ★ 2026-11-02（0.3.328）：不再替换机器外观渲染器 —— 黑日由客户端扫描 +
        //   RenderLevelStageEvent 绘制，机器本体继续用上面 .workableCasingRenderer(...) 的原样。
        GTLModular.LOGGER.info("[gtlmodular] 已注册奈亚拉托提普之触：{}（165×48×49 / {} 张配方表 / 每张表可切 / "
                        + "线圈并行 2^((温度-1800)/900) 封顶 21 亿 / 线程由线程仓驱动，无线程仓 1 条）",
                NYARLATHOTEPS_TENTACLE.getId(), types.size());
    }

    /**
     * 主机描述：<b>GTO 原机 19 行工具提示逐行照搬</b>（2026-11-01 把此前删掉的
     * 「允许安装线程仓实现多线程配方并行处理。」一行补回 —— 本机重新支持线程仓），
     * 只在末尾补一行「§7结构来源：§bGTO」。
     *
     * <p>行序：8 行设定文案 → 可特殊并行(2) → 并行数(2) → 激光仓(2) → 多线程(3) → 配方类型(1) → 结构来源(1)。
     */
    private static Component[] tooltips() {
        Component[] lines = new Component[TOOLTIP_LINES];
        for (int i = 0; i < lines.length; i++) {
            lines[i] = Component.translatable("gtlmodular.nyarlathoteps_tentacle.tooltip." + i);
        }
        return lines;
    }

    /** 工具提示行数（与 lang 里的 {@code gtlmodular.nyarlathoteps_tentacle.tooltip.N} 对应）。 */
    private static final int TOOLTIP_LINES = 20;

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
