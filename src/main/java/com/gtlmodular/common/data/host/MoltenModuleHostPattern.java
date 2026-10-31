package com.gtlmodular.common.data.host;

import com.gregtechceu.gtceu.api.GTCEuAPI;
import com.gregtechceu.gtceu.api.block.ICoilType;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.FactoryBlockPattern;
import com.gregtechceu.gtceu.api.pattern.MultiblockState;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gregtechceu.gtceu.common.block.CoilBlock;
import com.lowdragmc.lowdraglib.utils.BlockInfo;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static com.gregtechceu.gtceu.api.pattern.Predicates.abilities;
import static com.gregtechceu.gtceu.api.pattern.Predicates.any;
import static com.gregtechceu.gtceu.api.pattern.Predicates.blocks;
import static com.gregtechceu.gtceu.api.pattern.Predicates.controller;

/**
 * 地心涌动能量转换枢纽（M5）—— 结构生成器（巨型大楼版）。
 *
 * <p>判定结构 = 「地心涌动能量转换站.litematic」整栋楼 97×48×99（X宽×Y高×Z深，
 * 层数据由投影自动生成，{@code HostPatternLayers.RAW_xxx}）。三个模块机区域已在生成时
 * 挖成 '.' —— 主机成形**不依赖**熔岩高炉/星核剥离/天基炼矿单元。
 * 参与判定字符见 {@link #CHAR_MAP}；其余字符一律 '.'（any，装饰可自由摆放）。
 * 'S' 唯一 = 主机机器(数据 (48,8,8))。
 *
 * <p>线圈格 '1' 用 {@code heatingCoils()} 判定（而非固定方块）：玩家可整楼替换线圈，
 * 主机 {@code getCoilTemperature()} 实时反映当前线圈温度，供模块并行加成读取。
 */
public final class MoltenModuleHostPattern {

    private MoltenModuleHostPattern() {
    }

    /** 字符表与 HostFromLitematic 生成器 ROLE 同步（勿重复/勿用 'S' 与 '.'）。 */
    private static final String[] CHAR_MAP = {
            "T=kubejs:high_strength_concrete",
            "J=gtlcore:hsss_reinforced_borosilicate_glass",      // 强化硼硅酸盐玻璃（纳入判定）
            "K=kubejs:module_connector",                         // 模块连接器（纳入判定）
            "E=gtlcore:iridium_casing",
            "H=gtceu:advanced_computer_casing",                 // 原 散热片 → 高级计算机外壳
            "D=gtlcore:naquadah_alloy_casing",                  // 原 磨制深板岩 → 硅岩合金机械外壳
            "A=gtlcore:oxidation_resistant_hastelloy_n_mechanical_casing", // 原 磨制安山岩 → 抗氧化哈斯特洛伊合金-N机械方块
            "O=gtlcore:iridium_casing",                         // 原 磨制闪长岩 → 铱强化机械方块
            "G=gtceu:cleanroom_glass",                          // 原 黑色染色玻璃 → 超净间玻璃
            "Q=gtceu:computer_casing",                          // 原 高级计算机外壳 → 计算机外壳
            "C=gtceu:computer_heat_vent",                       // 原 灰色混凝土 → 计算机散热口
            "L=minecraft:sea_lantern",
            "I=gtlcore:iridium_casing",                         // 原 铁块 → 铱强化机械方块
            "V=gtlcore:naquadah_alloy_casing",                  // 原 深板岩瓦 → 硅岩合金机械外壳
            "B=minecraft:polished_basalt",
            "1=<heating_coils>",
            "3=gtceu:copper_huge_fluid_pipe",
            "4=gtceu:iridium_huge_fluid_pipe",
            "5=gtceu:iridium_block",
            "N=gtceu:naquadah_block",
            "R=gtceu:naquadah_huge_fluid_pipe",
            "6=gtceu:lv_muffler_hatch",
    };

    private static Block blockOf(String id) {
        Block b = ForgeRegistries.BLOCKS.getValue(new net.minecraft.resources.ResourceLocation(id));
        if (b == null) {
            throw new IllegalStateException("host pattern missing block: " + id);
        }
        return b;
    }

    // ---------- 线圈格（自研判定，替代原版 heatingCoils）----------

    /** 判定上下文里存放「线圈格世界坐标」的键（主机成形时取走，用于运行时实时读温）。 */
    public static final String COIL_CELLS_KEY = "GTLModularCoilCells";

    /**
     * 线圈方块 → ICoilType 反查表（惰性构建；GTCEuAPI.HEATING_COILS 是唯一权威来源）。
     *
     * <p>★ <b>2026-09-21 并发加固</b>：本表会同时被<b>服务端线程</b>（主机每 20 tick 的线圈巡检，
     * 见 {@code MoltenModuleHostMachine.tickCoilRefresh()}，1 次/秒 × 1715 格）
     * 和<b>客户端渲染/界面线程</b>（{@code getCoilType()} 在客户端也会被调）读写。
     * 原写法是"先判断后填充"的无同步惰性初始化，另一线程可能读到**半填充的 LinkedHashMap**
     * （并发读写非并发容器，理论上会损坏链表 —— 与 {@code MoltenModuleHostMachine.ensureCoilBlockIndex}
     * 2026-09-13 修掉的是同一类 bug）。这里改成 {@code volatile + 双检锁}，
     * 命中后仍是**无锁快路径**（稳态不引入任何锁开销）。
     */
    private static volatile Map<Block, ICoilType> coilByBlock;

    private static Map<Block, ICoilType> coilByBlock() {
        Map<Block, ICoilType> cached = coilByBlock;
        if (cached != null) {
            return cached;
        }
        synchronized (MoltenModuleHostPattern.class) {
            if (coilByBlock == null) {
                Map<Block, ICoilType> m = new LinkedHashMap<>();
                for (Map.Entry<ICoilType, Supplier<CoilBlock>> e : GTCEuAPI.HEATING_COILS.entrySet()) {
                    CoilBlock block = e.getValue().get();
                    if (block != null) {
                        m.put(block, e.getKey());
                    }
                }
                coilByBlock = m;   // 填完再发布（volatile 写）
            }
            return coilByBlock;
        }
    }

    /** 由方块状态反查加热线圈类型；不是加热线圈返回 null。 */
    public static ICoilType coilOf(BlockState state) {
        return coilByBlock().get(state.getBlock());
    }

    /**
     * 熔岩枢纽专用线圈格判定：
     * <ul>
     *   <li><b>允许整楼混放不同线圈</b>（原版 {@code heatingCoils()} 混放会直接报错导致结构不成形，
     *       换线圈过程中间态会把大楼判失败）；</li>
     *   <li>把每个线圈格的<b>世界坐标</b>记进判定上下文，主机成形时取走，
     *       之后直接读那些坐标上"现在放的是什么线圈" —— <b>换线圈立即生效，不需要结构重新判定</b>。</li>
     * </ul>
     */
    public static TraceabilityPredicate modularHeatingCoils() {
        return new TraceabilityPredicate((MultiblockState state) -> {
            ICoilType coil = coilOf(state.getBlockState());
            if (coil == null) {
                return false;
            }
            var ctx = state.getMatchContext();
            // 顺手维护原版键（gtceu 的 CoilWorkableElectricMultiblockMachine 会读它）：取更热的那个当兜底值
            ICoilType prev = ctx.get("CoilType");
            if (prev == null || coil.getCoilTemperature() > prev.getCoilTemperature()) {
                ctx.set("CoilType", coil);
            }
            List<BlockPos> cells = ctx.getOrPut(COIL_CELLS_KEY, new ObjectArrayList<BlockPos>());
            cells.add(state.getPos().immutable());
            return true;
        }, MoltenModuleHostPattern::coilPreview)
                .addTooltips(Component.translatable("gtceu.multiblock.pattern.error.coils"));
    }

    /** 结构预览用的候选方块（JEI/投影里显示所有加热线圈）。 */
    private static BlockInfo[] coilPreview() {
        return GTCEuAPI.HEATING_COILS.values().stream()
                .map(s -> BlockInfo.fromBlockState(s.get().defaultBlockState()))
                .toArray(BlockInfo[]::new);
    }

    public static BlockPattern build(MultiblockMachineDefinition def) {
        // 铱强化机械方块格只允许替换成: 能源仓 / 激光仓 / 维护舱(其余舱室不行)
        TraceabilityPredicate evapOrHatch = blocks(blockOf("gtlcore:iridium_casing"))
                .or(abilities(PartAbility.INPUT_ENERGY))
                .or(abilities(PartAbility.INPUT_LASER))
                .or(abilities(PartAbility.MAINTENANCE));
        FactoryBlockPattern f = FactoryBlockPattern.start();
        for (int z = 0; z < HostPatternLayers.Z; z++) {
            f = f.aisle(HostPatternLayers.RAW_STRING[z].split("\n"));
        }
        f = f.where('S', controller(blocks(def.get())));
        for (String e : CHAR_MAP) {
            int i = e.indexOf('=');
            char c = e.charAt(0);
            String id = e.substring(i + 1);
            if (c == '1') {
                // 线圈格：任意加热线圈（允许混放），并把线圈格坐标写入判定上下文供主机实时读温
                f = f.where(c, modularHeatingCoils());
            } else if (c == 'E') {
                f = f.where(c, evapOrHatch);
            } else {
                f = f.where(c, blocks(blockOf(id)));
            }
        }
        f = f.where('.', any());
        return f.build();
    }
}
