package com.gtlmodular.common.data.fieldforge;

import com.gregtechceu.gtceu.api.GTCEuAPI;
import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.data.recipe.builder.GTRecipeBuilder;
import com.gtlmodular.GTLModular;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.world.item.ItemStack;

import java.util.function.Consumer;

/**
 * 力场压模零件工厂「零部件加工」配方的<b>自动生成器</b> —— GTCEu 自家的写法。
 *
 * <h2>为什么这么改（2026-11-25）</h2>
 * 原先这 112 条配方是数据包 JSON（{@code data/gtlmodular/recipes/component_parts/}），
 * 由一份已经丢失的 KubeJS 脚本经 {@code scripts/gen_component_parts_recipes.py} 生成。
 * 手工维护有两个实测问题：
 * <ol>
 *   <li>覆盖不全：只有脚本里点名的那 112 个材料，其它材料做不了零件；</li>
 *   <li><b>输入输出不对等</b>：旧口径是「每种零件形态收 4 锭」，但环的批量被写成 32 个
 *       （32 环 = 8 锭的材料量），小齿轮被记成 4 个「单位」（16 锭），于是同一台机器
 *       对含环/小齿轮的材料净亏 —— 玩家反馈的「输入跟输出不对等」就是这两处。</li>
 * </ol>
 *
 * <h2>现在的规则（按材料量配平，一个形态正好 4 锭进、4 锭出）</h2>
 * <pre>
 * 对每个已注册材料：
 *   ingotAmount = TagPrefix.ingot.getMaterialAmount(material)      // 该材料 1 锭的材料量
 *   对 12 种零件形态里的每一条：
 *     若 ChemicalHelper.get(prefix, material) 为空 ⇒ 该材料没有这个形态，跳过
 *     partAmount = prefix.getMaterialAmount(material)
 *     产出数量 = 4 * ingotAmount / partAmount        // 只出正好值 4 锭的个数
 *   输入 = 4 锭 × 形态种类数                       // 与产出材料量严格相等
 * </pre>
 * 换算出来的数量：杆 8 / 板 4 / 螺栓 32 / 螺丝 36 / 环 16 / 齿轮 1 / 小齿轮 4 /
 * 弹簧 4 / 小弹簧 16 / 箔 16 / 框 2 / 转子 1 —— 每种都正好等于 4 锭的材料量。
 *
 * <p>与旧 JSON 相比只有两处数值变化，且都是把「少给」补回去：
 * <b>环 32 → 16</b>（8 锭材料量，不再多收）、<b>螺丝 32 → 36</b>（4 锭材料量，不再少给）。
 *
 * <h2>调用点</h2>
 * {@code IGTAddon#addRecipes(Consumer<FinishedRecipe>)} —— GTCEu 在数据包配方生成阶段
 * 通过 {@code GTRecipes} 遍历 {@code AddonFinder.getAddons()} 调到，是本模组
 * 本模组装配线配方已在用的同一套写法。不走服务端启动注入，
 * 也就不会再有「注册晚了 / 反射取 byType / 与数据包重复」这类老坑。
 */
public final class FieldExtruderPartsRecipes {

    /** 12 种零件形态，顺序即界面里的输出顺序。 */
    private static final TagPrefix[] PART_FORMS = {
            TagPrefix.rod,
            TagPrefix.plate,
            TagPrefix.bolt,
            TagPrefix.screw,
            TagPrefix.ring,
            TagPrefix.gear,
            TagPrefix.gearSmall,
            TagPrefix.spring,
            TagPrefix.springSmall,
            TagPrefix.foil,
            TagPrefix.frameGt,
            TagPrefix.rotor,
    };

    /** 每种形态收、且只收 4 锭的材料量（4 进 4 出，严格配平）。 */
    private static final long INGOTS_PER_FORM = 4L;

    /** 与旧配方一致：800 tick。 */
    private static final int DURATION = 800;

    /** 与旧配方一致：491520 EU/t。 */
    private static final long EUT = 491520L;

    /** GT 里 1 锭 = 144 mB。 */
    private static final long MB_PER_INGOT = 144L;

    private FieldExtruderPartsRecipes() {
    }

    /**
     * 为全部已注册材料生成「零部件加工」配方。
     *
     * @param provider GTCEu 传进来的数据包配方出口
     */
    public static void addRecipes(Consumer<FinishedRecipe> provider) {
        GTRecipeType type = FieldExtruderPartsRecipeTypes.RECIPES();
        if (type == null) {
            GTLModular.LOGGER.warn("[gtlmodular] 零部件加工：配方表尚未注册，跳过配方生成");
            return;
        }

        int generated = 0;
        int noInput = 0;
        int noForm = 0;

        for (Material material : GTCEuAPI.materialManager.getRegisteredMaterials()) {
            if (material == null) {
                continue;
            }
            long ingotAmount = TagPrefix.ingot.getMaterialAmount(material);
            if (ingotAmount <= 0L) {
                continue;
            }

            // ---- 1. 收集该材料真实存在的零件形态，并按材料量算出「正好 4 锭」的个数 ----
            ItemStack[] outputs = new ItemStack[PART_FORMS.length];
            int formCount = 0;
            for (TagPrefix prefix : PART_FORMS) {
                ItemStack part = ChemicalHelper.get(prefix, material);
                if (part.isEmpty()) {
                    continue; // 该材料没有这个形态
                }
                long partAmount = prefix.getMaterialAmount(material);
                if (partAmount <= 0L) {
                    continue;
                }
                long count = INGOTS_PER_FORM * ingotAmount / partAmount;
                if (count <= 0L) {
                    continue;
                }
                outputs[formCount++] = part.copyWithCount((int) Math.min(count, 64L));
            }
            if (formCount == 0) {
                noForm++;
                continue;
            }

            // ---- 2. 输入：锭优先，其次宝石，最后流体（与旧 JSON 同一优先级） ----
            long ingots = INGOTS_PER_FORM * formCount;
            ItemStack inputItem = ItemStack.EMPTY;
            long inputFluid = 0L;

            ItemStack ingot = ChemicalHelper.get(TagPrefix.ingot, material);
            if (!ingot.isEmpty()) {
                inputItem = ingot.copyWithCount((int) Math.min(ingots, 64L));
            } else {
                ItemStack gem = ChemicalHelper.get(TagPrefix.gem, material);
                long gemAmount = TagPrefix.gem.getMaterialAmount(material);
                if (!gem.isEmpty() && gemAmount > 0L) {
                    long gemCount = ingots * ingotAmount / gemAmount;
                    if (gemCount > 0L) {
                        inputItem = gem.copyWithCount((int) Math.min(gemCount, 64L));
                    }
                }
            }
            if (inputItem.isEmpty()) {
                // 没有物品形态 ⇒ 用流体（末影珍珠 / 末影之眼 / echo shard 这几个）
                if (material.getFluid() == null) {
                    noInput++;
                    continue;
                }
                inputFluid = ingots * MB_PER_INGOT;
            }

            // ---- 3. 出配方 ----
            try {
                GTRecipeBuilder builder = type.recipeBuilder("parts_" + material.getName());
                if (inputFluid > 0L) {
                    builder.inputFluids(material.getFluid(inputFluid));
                } else {
                    builder.inputItems(inputItem);
                }
                for (int i = 0; i < formCount; i++) {
                    builder.outputItems(outputs[i]);
                }
                builder.EUt(EUT).duration(DURATION).save(provider);
                generated++;
            } catch (Throwable t) {
                GTLModular.LOGGER.warn("[gtlmodular] 零部件加工：生成 {} 的配方失败（已跳过）：{}",
                        material.getName(), t.toString());
            }
        }

        GTLModular.LOGGER.info("[gtlmodular] 零部件加工：自动生成 {} 条配方"
                        + "（无零件形态跳过 {}，无可用输入跳过 {}）",
                generated, noForm, noInput);
    }
}
