package com.gtlmodular.common.data.component;

import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.FactoryBlockPattern;
import com.gregtechceu.gtceu.api.pattern.Predicates;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gregtechceu.gtceu.common.data.GCyMBlocks;
import com.gregtechceu.gtceu.common.data.GTBlocks;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;

/**
 * GTO「部件组装机」合并后的唯一结构：本体 + ASSEMBLY 扩展合体，29×6×13。
 *
 * <p>控制器在正面底部正中；BASE 本体（原基础机的 7×5×5）嵌在扩展正面中央的凹槽
 * （x 11..17、y 0..4、z 1..5，控制器在 z=1）。结构升级 1 级只检查 BASE 格
 * （大写 A~J / S，其余格被 Add 替换成 any()），2 级才检查扩展段格（小写）。
 *
 * <p>扩展段的舱面位 {@code Q} 与激光位 {@code L} 用<b>无计数</b>谓词，
 * 以满足 Add 3.6.2 对二级格 "不能是控制器或计数部件" 的硬约束。
 *
 * <p>方块按 GTO 原结构的符号映射；GTO 专属方块里只有
 * {@code TITANIUM_NITRIDE_CERAMIC_IMPACT_RESISTANT_MECHANICAL_BLOCK}（符号 {@code l}）在包里没有，
 * 按需求替换成 GTCEu 的「激光安全机械方块」（{@code gtceu:laser_safe_engraving_casing}）。
 * 其余 GTO 方块（multifunctional / process / oxidation-resistant hastelloy N）在本包由 gtlcore 提供，
 * 按 id 取；取不到时回落到钢制机壳。
 */
final class ComponentAssemblerStructures {

    static final int FULL_WIDTH = 29;
    static final int FULL_HEIGHT = 6;
    static final int FULL_LENGTH = 13;

    private static final String[] CAL_TIERS = {
            "lv", "mv", "hv", "ev", "iv", "luv", "zpm", "uv", "uhv", "uev", "uiv", "uxv", "opv", "max"
    };

    private ComponentAssemblerStructures() {}

    static BlockPattern buildFull(MultiblockMachineDefinition definition) {
        // 结构外壳仍接受 LV~MAX 任意档；IV 只约束「配方里的外壳输入」，不约束结构。
        return build(definition, FULL_SLICES, true, null);
    }

    /** 结构升级分类用：完整型的切片表。 */
    public static String[][] fullSlices() {
        return FULL_SLICES;
    }

    /**
     * 一级形态（原基础机）占用的符号：BASE 本体用大写 A~J 与控制器 S。
     * 扩展段全用小写，二者无交集，因此按字符即可给 Add 的 StructureTierLayout 分类。
     */
    private static final String TIER1_CHARS = "AaCD EFGHIJS";

    /** Add 结构升级的分类器：BASE 格 = 1 级，扩展段格 = 2 级。 */
    public static boolean isTier2Cell(char ch) {
        return TIER1_CHARS.indexOf(ch) < 0;
    }

    private static BlockPattern build(MultiblockMachineDefinition definition, String[][] slices, boolean laser,
                                      String casingTier) {
        FactoryBlockPattern pattern = FactoryBlockPattern.start();
        for (String[] slice : slices) {
            pattern.aisle(slice);
        }

        TraceabilityPredicate io = Predicates.autoAbilities(definition.getRecipeTypes());
        TraceabilityPredicate laserHatch = Predicates.abilities(PartAbility.INPUT_LASER).setMaxGlobalLimited(2);
        TraceabilityPredicate extra = Predicates.abilities(PartAbility.MAINTENANCE).setExactLimit(1)
                .or(Predicates.abilities(PartAbility.PARALLEL_HATCH).setMaxGlobalLimited(1))
                // ★ 2026-11-05：线程仓（自定义 PartAbility，autoAbilities 不带它）。
                //   装仓后引擎线程数 = 仓的当前设定值；没装时保持 v1=4 / v2=8（见机器 baseThreads）。
                .or(Predicates.abilities(
                        com.gtlmodular.common.data.thread.ThreadHatchMachines.THREAD_HATCH)
                        .setMaxGlobalLimited(1))
                // 超频仓：凡可插线程仓的机器都同样可插
                .or(com.gtlmodular.common.data.fuel.OverclockHatches.predicate())
                .or(com.gtlmodular.common.data.fuel.AccelerateHatches.predicate());
        // ★ 2026-10-31：激光输入仓只允许放在 l 位（激光安全机械方块），a/q/A/d 都不再接受。
        TraceabilityPredicate hatchable = Predicates.blocks(GTBlocks.CASING_STEEL_SOLID.get()).or(io).or(extra);
        TraceabilityPredicate laserSafe = Predicates.blocks(GCyMBlocks.CASING_LASER_SAFE_ENGRAVING.get());
        TraceabilityPredicate steel = Predicates.blocks(GTBlocks.CASING_STEEL_SOLID.get());
        if (laser) {
            laserSafe = laserSafe.or(laserHatch);
        }

        // ★ 结构升级二级格的舱面/激光位必须不带计数限制：Add 3.6.2 的
        //   StructureTierLayout.collect() 对二级格强制 predicate.limited.isEmpty()。
        //   这里只做能力识别（scan 仍能认出舱面），数量约束交给一级 BASE 的 hatchable。
        TraceabilityPredicate ioNoLimit = Predicates.abilities(PartAbility.INPUT_ENERGY)
                .or(Predicates.abilities(PartAbility.OUTPUT_ENERGY))
                .or(Predicates.abilities(PartAbility.IMPORT_ITEMS))
                .or(Predicates.abilities(PartAbility.EXPORT_ITEMS))
                .or(Predicates.abilities(PartAbility.IMPORT_FLUIDS))
                .or(Predicates.abilities(PartAbility.EXPORT_FLUIDS));
        TraceabilityPredicate extraNoLimit = Predicates.abilities(PartAbility.MAINTENANCE)
                .or(Predicates.abilities(PartAbility.PARALLEL_HATCH))
                .or(Predicates.abilities(com.gtlmodular.common.data.thread.ThreadHatchMachines.THREAD_HATCH))
                .or(Predicates.abilities(com.gtlmodular.common.data.fuel.OverclockHatches.OVERCLOCK_HATCH))
                .or(Predicates.abilities(com.gtlmodular.common.data.fuel.AccelerateHatches.ACCELERATE_HATCH));
        TraceabilityPredicate hatchableNoLimit = Predicates.blocks(GTBlocks.CASING_STEEL_SOLID.get())
                .or(ioNoLimit).or(extraNoLimit);
        TraceabilityPredicate laserSafeNoLimit = Predicates.blocks(GCyMBlocks.CASING_LASER_SAFE_ENGRAVING.get())
                .or(Predicates.abilities(PartAbility.INPUT_LASER));

        TraceabilityPredicate calCasings = calCasings(casingTier);
        Block multiFunctional = block("gtlcore:multi_functional_casing", GTBlocks.CASING_STEEL_SOLID.get());
        Block processCasing = block("gtlcore:process_machine_casing", GTBlocks.CASING_STEEL_SOLID.get());
        Block oxidation = block("gtlcore:oxidation_resistant_hastelloy_n_mechanical_casing",
                GTBlocks.CASING_STEEL_SOLID.get());

        return pattern
                .where('S', Predicates.controller(Predicates.blocks(definition.get())))
                // GTO 本体
                .where('A', steel)
                .where('a', hatchable)
                .where('C', Predicates.blocks(GTBlocks.CASING_GRATE.get()))
                .where('D', Predicates.blocks(GTBlocks.CASING_TEMPERED_GLASS.get()))
                .where('E', Predicates.blocks(GTBlocks.STEEL_HULL.get()))
                .where('F', calCasings)
                .where('G', Predicates.blocks(multiFunctional))
                .where('H', Predicates.blocks(ChemicalHelper.getBlock(TagPrefix.frameGt, GTMaterials.Steel)))
                .where('I', Predicates.blocks(Blocks.IRON_BARS))
                .where('J', Predicates.blocks(GTBlocks.CASING_STEEL_GEARBOX.get()))
                // ASSEMBLY 扩展
                .where('q', hatchable)
                .where('b', calCasings)
                .where('c', Predicates.blocks(GCyMBlocks.CASING_NONCONDUCTING.get()))
                .where('d', steel)
                .where('e', Predicates.blocks(GTBlocks.CASING_ASSEMBLY_CONTROL.get()))
                .where('f', Predicates.blocks(GTBlocks.STEEL_HULL.get()))
                .where('g', Predicates.blocks(GTBlocks.CASING_ASSEMBLY_LINE.get()))
                .where('h', Predicates.blocks(GTBlocks.CASING_GRATE.get()))
                .where('i', Predicates.blocks(ChemicalHelper.getBlock(TagPrefix.frameGt, GTMaterials.Trinium)))
                .where('j', Predicates.blocks(processCasing))
                .where('k', Predicates.blocks(oxidation))
                .where('l', laserSafe)
                .where('m', Predicates.blocks(GTBlocks.CASING_LAMINATED_GLASS.get()))
                .where('n', Predicates.blocks(GTBlocks.CASING_TEMPERED_GLASS.get()))
                .where('o', Predicates.blocks(GTBlocks.CASING_POLYTETRAFLUOROETHYLENE_PIPE.get()))
                .where('p', Predicates.blocks(Blocks.IRON_BARS))
                // 二级扩展段舱面/激光位（无计数版本，见上面的说明）
                .where('Q', hatchableNoLimit)
                .where('L', laserSafeNoLimit)
                .build();
    }

    /** gtlcore 的部件装配线外壳；{@code minTier == null} 接受 LV~MAX 任意档，否则接受“该档及以上”。 */
    private static TraceabilityPredicate calCasings(String minTier) {
        int start = 0;
        if (minTier != null) {
            for (int i = 0; i < CAL_TIERS.length; i++) {
                if (CAL_TIERS[i].equals(minTier)) {
                    start = i;
                    break;
                }
            }
        }
        List<Block> list = new ArrayList<>(CAL_TIERS.length - start);
        for (int i = start; i < CAL_TIERS.length; i++) {
            Block block = BuiltInRegistries.BLOCK.get(
                    new ResourceLocation("gtlcore", "component_assembly_line_casing_" + CAL_TIERS[i]));
            if (block != Blocks.AIR) {
                list.add(block);
            }
        }
        if (list.isEmpty()) {
            // gtlcore 缺失 / 方块未注册：回落到钢制机壳，避免整台机器无法成形。
            list.add(GTBlocks.CASING_STEEL_SOLID.get());
        }
        return Predicates.blocks(list.toArray(new Block[0]));
    }

    private static Block block(String id, Block fallback) {
        Block block = BuiltInRegistries.BLOCK.get(new ResourceLocation(id));
        return block == Blocks.AIR ? fallback : block;
    }



    private static final String[][] FULL_SLICES = {
        {
            " ccccccccccccccccccccccccccc ",
            " c         c     c         c ",
            " c         c     c         c ",
            " c         c     c         c ",
            " c         c     c         c ",
            " ccccccccccccccccccccccccccc ",
        },
        {
            "QdddddddddddddddddddddddddddQ",
            "QhmmmmmmmmhdhnnnhdhmmmmmmmmhQ",
            "QhmmmmmmmmhdhnnnhdhmmmmmmmmhQ",
            "QhmmmmmmmmhdhnnnhdhmmmmmmmmhQ",
            "QdddddddddddddddddddddddddddQ",
            " cffc  cffc cfffc cffc  cffc ",
        },
        {
            "QeeeeeeeeeedeeeeedeeeeeeeeeeQ",
            "b                           b",
            "Q          j     j          Q",
            "b                           b",
            "QdhhddddhhdddhdhdddhhddddhhdQ",
            " chhc  chhc chfhc chhc  chhc ",
        },
        {
            "QffffffffffdfffffdffffffffffQ",
            "biiiiiiiiii  p p  iiiiiiiiiib",
            "Q          j p p j          Q",
            "bjjjjjjjjjj       jjjjjjjjjjb",
            "QdhhddddhhdddhdhdddhhddddhhdQ",
            " chhc  chhc chfhc chhc  chhc ",
        },
        {
            "QggggggggggdgggggdggggggggggQ",
            "b                           b",
            "QggggggggggjgggggjggggggggggQ",
            "b                           b",
            "QdhhddddhhdddhdhdddhhddddhhdQ",
            " chhc  chhc chfhc chhc  chhc ",
        },
        {
            "QffffffffffdfffffdffffffffffQ",
            "biiiiiiiiii  p p  iiiiiiiiiib",
            "Q          j p p j          Q",
            "bjjjjjjjjjj       jjjjjjjjjjb",
            "QdhhddddhhdddhdhdddhhddddhhdQ",
            " chhc  chhc chfhc chhc  chhc ",
        },
        {
            "QeeeeeeeeeedeeeeedeeeeeeeeeeQ",
            "b                           b",
            "Q          j     j          Q",
            "b                           b",
            "QdhhddddhhdddhdhdddhhddddhhdQ",
            " chhc  chhc chfhc chhc  chhc ",
        },
        {
            "QddddddddddAaaaaaAddddddddddQ",
            "Qhdo oo odhACDDDCAhdo oo odhQ",
            "Qhdo oo odhACDDDCAhdo oo odhQ",
            "Qhd      dhACDDDCAhd      dhQ",
            "QddddddddddAAAAAAAddddddddddQ",
            " cffc  cffc cfffc cffc  cffc ",
        },
        {
            " ckkkkkkkkcaAEEEAackkkkkkkkc ",
            " cko oo okcFG   GFcko oo okc ",
            " cko oo okcFG   GFcko oo okc ",
            " ckkkkkkkkcFG   GFckkkkkkkkc ",
            " cc      ccAACACAAcc      cc ",
            " ccccccccccccccccccccccccccc ",
        },
        {
            " ckffffffkcaAEEEAackffffffkc ",
            "  Lo oo oL FHI IHF Lo oo oL  ",
            "  Lo oo oL FJI IJF Lo oo oL  ",
            "  knnnnnnk FG   GF knnnnnnk  ",
            "  c      c AACACAA c      c  ",
            "                             ",
        },
        {
            " ckffffffkcaAEEEAackffffffkc ",
            "  Lo oo oL FG   GF Lo oo oL  ",
            "  Lo oo oL FG   GF Lo oo oL  ",
            "  knnnnnnk FG   GF knnnnnnk  ",
            "  c      c AACACAA c      c  ",
            "                             ",
        },
        {
            " ckffffffkcAaaSaaAckffffffkc ",
            "  L      L ACDDDCA L      L  ",
            "  L      L ACDDDCA L      L  ",
            "  knnnnnnk ACDDDCA knnnnnnk  ",
            "  c      c AAAAAAA c      c  ",
            "                             ",
        },
        {
            " ckkkkkkkkc       ckkkkkkkkc ",
            "  knnnnnnk         knnnnnnk  ",
            "  knnnnnnk         knnnnnnk  ",
            "  kkkkkkkk         kkkkkkkk  ",
            "                             ",
            "                             ",
        },
    };
}
