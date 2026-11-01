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
 * GTO「部件组装机」的两个静态结构（不做 GTO 的可选模块/原地升级，直接拆成两台多方块）。
 *
 * <ul>
 *   <li><b>BASE</b>：GTO 本体 7×5×5；控制器在正面底部正中（GTO 的 {@code B}）。</li>
 *   <li><b>FULL</b>：本体 + ASSEMBLY 扩展合体，29×6×13。GTO 的 multiblockpro 把本体的
 *       {@code MODULE_OUT}（控制器、朝后）与扩展的 {@code MODULE_IN}（{@code @}、朝前）对接，
 *       本体的 {@code MODULE_OUT} 目标格 = 控制器格 + 朝向一步 ⇒ 本体正好嵌进扩展正面中央的凹槽
 *       （x 11..17、y 0..4、z 1..5，控制器在 z=1），扩展自身的包围盒仍是 29×6×13。</li>
 * </ul>
 *
 * <p>方块按 GTO 原结构的符号映射；GTO 专属方块里只有
 * {@code TITANIUM_NITRIDE_CERAMIC_IMPACT_RESISTANT_MECHANICAL_BLOCK}（符号 {@code l}）在包里没有，
 * 按需求替换成 GTCEu 的「激光安全机械方块」（{@code gtceu:laser_safe_engraving_casing}）。
 * 其余 GTO 方块（multifunctional / process / oxidation-resistant hastelloy N）在本包由 gtlcore 提供，
 * 按 id 取；取不到时回落到钢制机壳。
 */
final class ComponentAssemblerStructures {

    static final int BASE_WIDTH = 7;
    static final int BASE_HEIGHT = 5;
    static final int BASE_LENGTH = 5;
    static final int FULL_WIDTH = 29;
    static final int FULL_HEIGHT = 6;
    static final int FULL_LENGTH = 13;

    private static final String[] CAL_TIERS = {
            "lv", "mv", "hv", "ev", "iv", "luv", "zpm", "uv", "uhv", "uev", "uiv", "uxv", "opv", "max"
    };

    private ComponentAssemblerStructures() {}

    static BlockPattern buildBase(MultiblockMachineDefinition definition) {
        return build(definition, BASE_SLICES, false, null);
    }

    static BlockPattern buildFull(MultiblockMachineDefinition definition) {
        // 结构外壳仍接受 LV~MAX 任意档；IV 只约束「配方里的外壳输入」，不约束结构。
        return build(definition, FULL_SLICES, true, null);
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
                .or(Predicates.abilities(PartAbility.PARALLEL_HATCH).setMaxGlobalLimited(1));
        // ★ 2026-10-31：激光输入仓只允许放在 l 位（激光安全机械方块），a/q/A/d 都不再接受。
        TraceabilityPredicate hatchable = Predicates.blocks(GTBlocks.CASING_STEEL_SOLID.get()).or(io).or(extra);
        TraceabilityPredicate laserSafe = Predicates.blocks(GCyMBlocks.CASING_LASER_SAFE_ENGRAVING.get());
        TraceabilityPredicate steel = Predicates.blocks(GTBlocks.CASING_STEEL_SOLID.get());
        if (laser) {
            laserSafe = laserSafe.or(laserHatch);
        }

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

    private static final String[][] BASE_SLICES = {
        {
            "AaaaaaA",
            "ACDDDCA",
            "ACDDDCA",
            "ACDDDCA",
            "AAAAAAA",
        },
        {
            "aAEEEAa",
            "FG   GF",
            "FG   GF",
            "FG   GF",
            "AACACAA",
        },
        {
            "aAEEEAa",
            "FHI IHF",
            "FJI IJF",
            "FG   GF",
            "AACACAA",
        },
        {
            "aAEEEAa",
            "FG   GF",
            "FG   GF",
            "FG   GF",
            "AACACAA",
        },
        {
            "AaaSaaA",
            "ACDDDCA",
            "ACDDDCA",
            "ACDDDCA",
            "AAAAAAA",
        },
    };

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
            "qdddddddddddddddddddddddddddq",
            "qhmmmmmmmmhdhnnnhdhmmmmmmmmhq",
            "qhmmmmmmmmhdhnnnhdhmmmmmmmmhq",
            "qhmmmmmmmmhdhnnnhdhmmmmmmmmhq",
            "qdddddddddddddddddddddddddddq",
            " cffc  cffc cfffc cffc  cffc ",
        },
        {
            "qeeeeeeeeeedeeeeedeeeeeeeeeeq",
            "b                           b",
            "q          j     j          q",
            "b                           b",
            "qdhhddddhhdddhdhdddhhddddhhdq",
            " chhc  chhc chfhc chhc  chhc ",
        },
        {
            "qffffffffffdfffffdffffffffffq",
            "biiiiiiiiii  p p  iiiiiiiiiib",
            "q          j p p j          q",
            "bjjjjjjjjjj       jjjjjjjjjjb",
            "qdhhddddhhdddhdhdddhhddddhhdq",
            " chhc  chhc chfhc chhc  chhc ",
        },
        {
            "qggggggggggdgggggdggggggggggq",
            "b                           b",
            "qggggggggggjgggggjggggggggggq",
            "b                           b",
            "qdhhddddhhdddhdhdddhhddddhhdq",
            " chhc  chhc chfhc chhc  chhc ",
        },
        {
            "qffffffffffdfffffdffffffffffq",
            "biiiiiiiiii  p p  iiiiiiiiiib",
            "q          j p p j          q",
            "bjjjjjjjjjj       jjjjjjjjjjb",
            "qdhhddddhhdddhdhdddhhddddhhdq",
            " chhc  chhc chfhc chhc  chhc ",
        },
        {
            "qeeeeeeeeeedeeeeedeeeeeeeeeeq",
            "b                           b",
            "q          j     j          q",
            "b                           b",
            "qdhhddddhhdddhdhdddhhddddhhdq",
            " chhc  chhc chfhc chhc  chhc ",
        },
        {
            "qddddddddddAaaaaaAddddddddddq",
            "qhdo oo odhACDDDCAhdo oo odhq",
            "qhdo oo odhACDDDCAhdo oo odhq",
            "qhd      dhACDDDCAhd      dhq",
            "qddddddddddAAAAAAAddddddddddq",
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
            "  lo oo ol FHI IHF lo oo ol  ",
            "  lo oo ol FJI IJF lo oo ol  ",
            "  knnnnnnk FG   GF knnnnnnk  ",
            "  c      c AACACAA c      c  ",
            "                             ",
        },
        {
            " ckffffffkcaAEEEAackffffffkc ",
            "  lo oo ol FG   GF lo oo ol  ",
            "  lo oo ol FG   GF lo oo ol  ",
            "  knnnnnnk FG   GF knnnnnnk  ",
            "  c      c AACACAA c      c  ",
            "                             ",
        },
        {
            " ckffffffkcAaaSaaAckffffffkc ",
            "  l      l ACDDDCA l      l  ",
            "  l      l ACDDDCA l      l  ",
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
