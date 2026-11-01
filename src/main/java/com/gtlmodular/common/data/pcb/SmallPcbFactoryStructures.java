package com.gtlmodular.common.data.pcb;

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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 小型 PCB 工厂的结构：**1:1 照搬 gtlcore 原始 PCB 工厂**（增强版核心替换之前的那版）。
 *
 * <p>取证：{@code gtlcore-1.2.3.2-fix4.jar} →
 * {@code org.gtlcore.gtlcore.common.data.machines.structure.AdvancedMultiBlockMachineStructureA.PCB_FACTORY}；
 * 谓词绑定逐字取自 {@code AdvancedMultiBlockMachineA.lambda$static$25}。
 *
 * <p>尺寸 <b>17 (X) × 10 (Y) × 15 (Z)</b>；控制器 {@code a} 在 X=9 / Y=0 / Z=12。
 * 增强版核心（EnhancedCore 3.9.4）的 {@code upgrades_20261013.json} 里同一台机器的
 * {@code originalSize=[17,10,15]}，与本结构逐格一致（11 个采样点全部命中）。
 *
 * <p>与原始机器的唯一差别：<b>不开放并行仓与激光仓</b>（本机自带并行 1024、两条独立线程），
 * 其余符号/方块/水密外壳下限 60 全部保留。
 */
final class SmallPcbFactoryStructures {

    static final int WIDTH = 17;
    static final int HEIGHT = 10;
    static final int LENGTH = 15;

    private SmallPcbFactoryStructures() {}

    /** 15 个 Z 层，每层 10 行（Y=0 在前），行宽 17。 */
    private static final String[][] SLICES = {
            { // Z=0
                    "ibbbi  clllc     ", // Y=0
                    "ieeei  c   c     ", // Y=1
                    "ieeei  c   c     ", // Y=2
                    "ieeei  c   c     ", // Y=3
                    "i   i  c   c     ", // Y=4
                    "       c   c     ", // Y=5
                    "       c   c     ", // Y=6
                    "       c   c     ", // Y=7
                    "       c   c     ", // Y=8
                    "       clllc     ", // Y=9
            },
            { // Z=1
                    "bbbbb  lllll     ", // Y=0
                    "e   e   lll      ", // Y=1
                    "e   e   lll      ", // Y=2
                    "e   e   lll      ", // Y=3
                    " bbb    lll      ", // Y=4
                    "        lll      ", // Y=5
                    "        lll      ", // Y=6
                    "        lll      ", // Y=7
                    "        lll      ", // Y=8
                    "       lllll     ", // Y=9
            },
            { // Z=2
                    "bbbbb  lllll     ", // Y=0
                    "e   e   l l      ", // Y=1
                    "e   e   l l      ", // Y=2
                    "e   e   l l      ", // Y=3
                    " bbb    l l      ", // Y=4
                    "  b     l l      ", // Y=5
                    "  b     l l      ", // Y=6
                    "        l l      ", // Y=7
                    "        l l      ", // Y=8
                    "       lllll     ", // Y=9
            },
            { // Z=3
                    "bbbbb  lllllg    ", // Y=0
                    "e   e   lll      ", // Y=1
                    "e   e   lll      ", // Y=2
                    "e   e   lll      ", // Y=3
                    " bbb    lll      ", // Y=4
                    "        lll      ", // Y=5
                    "  b     lll      ", // Y=6
                    "        lll      ", // Y=7
                    "        lll      ", // Y=8
                    "       lllll     ", // Y=9
            },
            { // Z=4
                    "ibbbi  clllcghddh", // Y=0
                    "ieeei  c   c hddh", // Y=1
                    "ieeei  c   c hddh", // Y=2
                    "ieeei  c   c     ", // Y=3
                    "i   i  c   c     ", // Y=4
                    "       c   c     ", // Y=5
                    "  b    c   c     ", // Y=6
                    "       c   c     ", // Y=7
                    "       c   c     ", // Y=8
                    "       clllc     ", // Y=9
            },
            { // Z=5
                    "            gdddd", // Y=0
                    "             d  d", // Y=1
                    "             d  d", // Y=2
                    "             hddh", // Y=3
                    "             hddh", // Y=4
                    "                 ", // Y=5
                    "  b              ", // Y=6
                    "                 ", // Y=7
                    "                 ", // Y=8
                    "                 ", // Y=9
            },
            { // Z=6
                    "      ijjjjjidddd", // Y=0
                    "      ijjjjjid  d", // Y=1
                    "      ijjjjjid  d", // Y=2
                    "      ijjjjjid  d", // Y=3
                    "      i     id  d", // Y=4
                    "             hddh", // Y=5
                    "  b              ", // Y=6
                    "                 ", // Y=7
                    "                 ", // Y=8
                    "                 ", // Y=9
            },
            { // Z=7
                    "ibbbi jkkkkkjdddd", // Y=0
                    "ieeei j     jd  d", // Y=1
                    "ieeei j     jd  d", // Y=2
                    "ieeei j     jd  d", // Y=3
                    "i   i jjjjjjjd  d", // Y=4
                    "      i     idddd", // Y=5
                    "  b          hddh", // Y=6
                    "                 ", // Y=7
                    "                 ", // Y=8
                    "                 ", // Y=9
            },
            { // Z=8
                    "bbbbb jkkkkkjdddd", // Y=0
                    "e   e j     jd  d", // Y=1
                    "e   e j     jd  d", // Y=2
                    "e   e j     jd  d", // Y=3
                    " bbb  jjjjjjjd  d", // Y=4
                    "      i     idddd", // Y=5
                    "  b          hddh", // Y=6
                    "                 ", // Y=7
                    "                 ", // Y=8
                    "                 ", // Y=9
            },
            { // Z=9
                    "bbbbbgjkkkkkjdddd", // Y=0
                    "e   e j     jd  d", // Y=1
                    "e   e j     jd  d", // Y=2
                    "e   e j     jd  d", // Y=3
                    " bbb  jjjjjjjd  d", // Y=4
                    "  b   iiiiiiidddd", // Y=5
                    "  b          hddh", // Y=6
                    "                 ", // Y=7
                    "                 ", // Y=8
                    "                 ", // Y=9
            },
            { // Z=10
                    "bbbbb jkkkkkjdddd", // Y=0
                    "e   e j     jd  d", // Y=1
                    "e   e j     jd  d", // Y=2
                    "e   e j     jd  d", // Y=3
                    " bbb  jeeeeejd  d", // Y=4
                    "      i     idddd", // Y=5
                    "             hddh", // Y=6
                    "                 ", // Y=7
                    "                 ", // Y=8
                    "                 ", // Y=9
            },
            { // Z=11
                    "ibbbi jkkkkkjdddd", // Y=0
                    "ieeei j     jd  d", // Y=1
                    "ieeei j     jd  d", // Y=2
                    "ieeei j     jd  d", // Y=3
                    "i   i jeeeeejd  d", // Y=4
                    "      i     idddd", // Y=5
                    "             hddh", // Y=6
                    "                 ", // Y=7
                    "                 ", // Y=8
                    "                 ", // Y=9
            },
            { // Z=12
                    "      ijjajjidddd", // Y=0
                    "      ieeeeeid  d", // Y=1
                    "      ieeeeeid  d", // Y=2
                    "      ieeeeeid  d", // Y=3
                    "      iiiiiiid  d", // Y=4
                    "             hddh", // Y=5
                    "                 ", // Y=6
                    "                 ", // Y=7
                    "                 ", // Y=8
                    "                 ", // Y=9
            },
            { // Z=13
                    "             dddd", // Y=0
                    "             d  d", // Y=1
                    "             d  d", // Y=2
                    "             hddh", // Y=3
                    "             hddh", // Y=4
                    "                 ", // Y=5
                    "                 ", // Y=6
                    "                 ", // Y=7
                    "                 ", // Y=8
                    "                 ", // Y=9
            },
            { // Z=14
                    "             hddh", // Y=0
                    "             hddh", // Y=1
                    "             hddh", // Y=2
                    "                 ", // Y=3
                    "                 ", // Y=4
                    "                 ", // Y=5
                    "                 ", // Y=6
                    "                 ", // Y=7
                    "                 ", // Y=8
                    "                 ", // Y=9
            },
    };

    static BlockPattern build(MultiblockMachineDefinition definition) {
        FactoryBlockPattern pattern = FactoryBlockPattern.start();
        for (String[] slice : SLICES) {
            pattern.aisle(slice);
        }

        // j = 水密外壳：全局至少 60 个；同时承担 IO 仓 / 能源输入仓 / 维护仓。
        //
        // ★ 这里刻意【不用】Predicates.autoAbilities(recipeTypes)：
        //   那个重载虽然当前版本（GTCEu 1.4.4 字节码核对）只展开 energyIn/Out + itemsIn/Out + fluidsIn/Out，
        //   由 6 个布尔位决定；一旦上游改了默认位、或包里别的模组 mixin 进去，
        //   并行仓（PARALLEL_HATCH）就会静默变成合法方块。所以这里写成**显式白名单**：
        //   只认 5 类仓 —— 物品输入/输出、流体输入/输出、能源输入；
        //   并行仓（PARALLEL_HATCH）与激光输入仓（INPUT_LASER）**不在列表里 ⇒ 放上去必失形**。
        TraceabilityPredicate shell = Predicates.blocks(GCyMBlocks.CASING_WATERTIGHT.get())
                .setMinGlobalLimited(60)
                .or(Predicates.abilities(
                        PartAbility.IMPORT_ITEMS,
                        PartAbility.EXPORT_ITEMS,
                        PartAbility.IMPORT_FLUIDS,
                        PartAbility.EXPORT_FLUIDS,
                        PartAbility.INPUT_ENERGY))
                .or(Predicates.abilities(PartAbility.MAINTENANCE).setExactLimit(1));

        pattern.where('a', Predicates.controller(Predicates.blocks(definition.get())));
        pattern.where('j', shell);
        pattern.where('b', Predicates.blocks(GTBlocks.CASING_STAINLESS_CLEAN.get()));
        pattern.where('c', Predicates.blocks(ChemicalHelper.getBlock(TagPrefix.frameGt, GTMaterials.BlueSteel)));
        pattern.where('d', Predicates.blocks(byId("gtlcore", "antifreeze_heatproof_machine_casing",
                GTBlocks.CASING_STAINLESS_CLEAN.get())));
        pattern.where('e', Predicates.blocks(GTBlocks.CASING_LAMINATED_GLASS.get()));
        pattern.where('g', Predicates.blocks(GTBlocks.CASING_POLYTETRAFLUOROETHYLENE_PIPE.get()));
        pattern.where('h', Predicates.blocks(ChemicalHelper.getBlock(TagPrefix.frameGt, GTMaterials.Ultimet)));
        pattern.where('i', Predicates.blocks(ChemicalHelper.getBlock(TagPrefix.frameGt, GTMaterials.HSLASteel)));
        pattern.where('k', Predicates.blocks(GCyMBlocks.CASING_STRESS_PROOF.get()));
        pattern.where('l', Predicates.blocks(GTBlocks.CASING_STAINLESS_EVAPORATION.get()));
        pattern.where(' ', Predicates.any());
        return pattern.build();
    }

    /** 按 id 取方块；未注册 / 是空气时回落到 fallback（保证结构不会永远不成形）。 */
    private static Block byId(String namespace, String path, Block fallback) {
        Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(namespace, path));
        return block == null || block == Blocks.AIR ? fallback : block;
    }
}
