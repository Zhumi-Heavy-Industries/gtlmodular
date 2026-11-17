package com.gtlmodular.common.data.coolingtower;

import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.common.data.GTRecipeModifiers;
import com.gtladd.gtladditions.common.recipe.GTLAddRecipesTypes;
import com.gtlmodular.GTLModular;
import com.gtlmodular.common.data.GTLModularMachines;
import com.gtlmodular.common.machine.coolingtower.CoolingTowerCondensationCenterMachine;
import com.gtlmodular.common.machine.fuel.OverclockProfiles;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 「冷却塔冷凝中枢」的注册（{@code gtlmodular:cooling_tower_condensation_center}）。
 *
 * <h2>口径（队长 2026-11-09 / 2026-11-10 两轮原话逐条落地）</h2>
 * <ul>
 *   <li>「把那个结构导出来变成多方块」→ 结构见
 *       {@link CoolingTowerCondensationCenterStructures}：存档 {@code 琪露诺} 里 13×16×16 的实建冷却塔
 *       1:1 导出（3328 格逐格比对全等）；</li>
 *   <li>「主机用冷却塔这个方块的贴图」→ 控制器正面覆层 = 冷却塔同款
 *       {@code gtceu:block/multiblock/vacuum_freezer}；<b>外壳改用塔身同款
 *       {@code gtlcore:block/cold_ice_casing}</b>（2026-11-10 修正：原来用霜冻机壳，塔身是寒冰机壳 +
 *       LDLib 连接纹理，控制器不连 ⇒ 队长「纹理连接有问题、没有成型为一体」）；</li>
 *   <li>「结构内允许替换舱室的方块为铝制防冻机械方块」→ 结构 'H' 位 =
 *       {@code gtceu:frostproof_machine_casing} <b>或</b> {@code gtlcore:cold_ice_casing} <b>或</b>
 *       任意仓室（想让整面墙连片时，把那 11 格换成寒冰机械方块即可）；</li>
 *   <li>「他的代码跟反伤（熵）冷凝中枢一样」→ 干冰消耗 / 相对论级热容 / 拆机返还 与
 *       gtladditions {@code AntientropyCondensationCenter} 同公式；</li>
 *   <li>「只是结构内允许使用我们的线程仓」+「把他的跨配方线程给删掉，不如我们的独立线程」
 *       → 机器类改走本模组 {@code ThreadedRecipeLogic} 独立线程引擎（线程数 = 线程仓当前设定值，
 *       没仓 1 条），不再有 gtladditions 的固定 128 条跨配方线程；</li>
 *   <li>「允许使用并行仓」→ 结构 'H' 位接受 {@code PARALLEL_HATCH}（限 1），
 *       引擎 {@code usesParallelHatch() = true}，单条批次并行 = 并行仓当前并行。</li>
 * </ul>
 *
 * <p>注册调用点：{@link GTLModularMachines#init()}（GTM 机器注册窗口内，排在
 * {@code ThreadHatchMachines.register()} 之后，保证线程仓谓词拿得到方块表）。
 */
public final class CoolingTowerCondensationCenterMachines {

    private CoolingTowerCondensationCenterMachines() {
    }

    /** 控制器外壳贴图（= 塔身同款寒冰机械方块；带 LDLib 连接纹理，会与塔身连片）。 */
    public static final ResourceLocation CASING_TEXTURE =
            new ResourceLocation("gtlcore", "block/cold_ice_casing");
    /** 控制器正面覆层（= 冷却塔同款）。 */
    public static final ResourceLocation OVERLAY_TEXTURE =
            new ResourceLocation("gtceu", "block/multiblock/vacuum_freezer");

    public static MultiblockMachineDefinition COOLING_TOWER_CONDENSATION_CENTER;

    /** 注册（仅在 GTM 机器注册窗口内调用）。 */
    public static void register() {
        GTRecipeType antientropy = GTLAddRecipesTypes.INSTANCE.getANTIENTROPY_CONDENSATION();

        COOLING_TOWER_CONDENSATION_CENTER = GTLModularMachines.REGISTRATE
                .multiblock("cooling_tower_condensation_center",
                        CoolingTowerCondensationCenterMachine::new)
                .langValue("超级大冰箱")
                .rotationState(RotationState.NON_Y_AXIS)
                .allowExtendedFacing(false)
                .recipeType(antientropy)
                .tooltips(tooltips())
                // ★ 2026-11-10：并行按「当前工作逻辑」分流——
                //   · 装了线程仓（引擎接管）：并行由引擎乘一次（usesParallelHatch=true），这里原样返回；
                //   · 没装线程仓（原版 RecipeLogic 代跑）：走 GTCEu 标准 PARALLEL_HATCH，并行仓照常生效。
                //   否则「修改器 × 引擎」会重复放大。
                .recipeModifier((machine, recipe, params, result) -> {
                    // ★ 2026-11-10：补上超频（时间减免）。原来只挂了并行，完全没有超频修改器
                    //   ⇒ 电压再高配方耗时也不变（队长：「吃不到超频的时间减免」）。
                    // ★ 2026-11-11：改走【通用超频口径入口】OverclockProfiles ——
                    //   默认仍是完美超频 + 亚 tick（与改前一致）；若机器实现了 ISpeedEnergyOverclockable，
                    //   自动改用它的 S/E 口径，不用在这里写死。
                    GTRecipe processed = OverclockProfiles.apply(machine, recipe, params, result);
                    if (processed == null) {
                        return null;
                    }
                    if (machine instanceof CoolingTowerCondensationCenterMachine tower) {
                        // ★ 相对论级热容：只压 tick 输入 EU（×0.09），物品/流体不动；
                        //   引擎与原版两条路都会先走本修改器，所以共用这一处。
                        if (tower.hasRelativisticHeatCapacitor()) {
                            processed = CoolingTowerCondensationCenterMachine.applyHeatCapacitorEu(processed);
                        }
                        // 引擎模式：并行由引擎乘一次
                        if (tower.threadHatchThreads() > 0) {
                            return processed;
                        }
                    }
                    // ★ 2026-11-10 实机踩坑：原版模式下，并行仓当前并行 = 0 时**绝不能**套
                    //   GTRecipeModifiers.PARALLEL_HATCH —— 它内部的 hatchParallel 会返回 null，
                    //   GTCEu 把 null 当成「这条配方不存在」⇒ 玩家明明有配方也显示「未找到配方」。
                    //   （玩家这台：ZPM 并行仓 currentParallel=0、没装线程仓 ⇒ 原版模式 ⇒ 全灭。）
                    //   口径：有并行仓但没设并行 ⇒ 按「无并行」原样跑；设了 >0 才乘并行。
                    if (machine instanceof com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController controller) {
                        boolean hasParallelHatch = false;
                        int maxParallel = 0;
                        for (com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart part : controller.getParts()) {
                            if (part instanceof com.gregtechceu.gtceu.api.capability.IParallelHatch hatch) {
                                hasParallelHatch = true;
                                maxParallel = Math.max(maxParallel, hatch.getCurrentParallel());
                            }
                        }
                        if (hasParallelHatch && maxParallel <= 0) {
                            return processed;
                        }
                    }
                    return GTRecipeModifiers.PARALLEL_HATCH.apply(machine, processed, params, result);
                })
                .appearanceBlock(() -> blockOr("gtlcore", "cold_ice_casing", Blocks.IRON_BLOCK))
                .pattern(CoolingTowerCondensationCenterStructures::build)
                // ★ 2026-11-10：自定义渲染器打开 LDLib 的 CTM 重烘焙（默认关闭）——
                //   控制器外壳与塔身（寒冰机械方块，连接纹理）连成一片。
                .renderer(() -> new com.gtlmodular.client.renderer.CoolingTowerCasingRenderer(
                        CASING_TEXTURE, OVERLAY_TEXTURE))
                .register();

        GTLModular.LOGGER.info("[gtlmodular] 已注册冷却塔冷凝中枢：{}（结构 16×16×13，存档「琪露诺」1:1 导出；"
                        + "配方表 = 反熵冷凝；外壳 = 寒冰机械方块（与塔身连片）；"
                        + "独立线程引擎：线程数 = 线程仓当前值 / 没仓 1 条；并行仓已接入）",
                COOLING_TOWER_CONDENSATION_CENTER.getId());
    }

    /** 与 lang 的 {@code gtlmodular.cooling_tower_condensation_center.tooltip.N} 对应。 */
    private static final int TOOLTIP_LINES = 8;

    private static Component[] tooltips() {
        Component[] lines = new Component[TOOLTIP_LINES];
        for (int i = 0; i < lines.length; i++) {
            lines[i] = Component.translatable("gtlmodular.cooling_tower_condensation_center.tooltip." + i);
        }
        return lines;
    }

    /** 按 id 取方块，取不到退回 fallback（注册期绝不 NPE）。 */
    private static Block blockOr(String namespace, String path, Block fallback) {
        Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(namespace, path));
        if (block == null || block == Blocks.AIR) {
            GTLModular.LOGGER.warn("[gtlmodular] 冷却塔冷凝中枢：外观方块 {}/{} 取不到，退回 {}",
                    namespace, path, ForgeRegistries.BLOCKS.getKey(fallback));
            return fallback;
        }
        return block;
    }
}
