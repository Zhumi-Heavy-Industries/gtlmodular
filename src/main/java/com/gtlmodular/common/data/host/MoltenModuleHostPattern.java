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

/** 主机图案排除模块区域，使主机成形不依赖模块是否安装。 */
public final class MoltenModuleHostPattern {

    private MoltenModuleHostPattern() {
    }

    private static final String[] CHAR_MAP = {
            "T=kubejs:high_strength_concrete",
            "J=gtlcore:hsss_reinforced_borosilicate_glass",
            "K=kubejs:module_connector",
            "E=gtlcore:iridium_casing",
            "H=gtceu:advanced_computer_casing",
            "D=gtlcore:naquadah_alloy_casing",
            "A=gtlcore:oxidation_resistant_hastelloy_n_mechanical_casing",
            "O=gtlcore:iridium_casing",
            "G=gtceu:cleanroom_glass",
            "Q=gtceu:computer_casing",
            "C=gtceu:computer_heat_vent",
            "L=minecraft:sea_lantern",
            "I=gtlcore:iridium_casing",
            "V=gtlcore:naquadah_alloy_casing",
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

    public static final String COIL_CELLS_KEY = "GTLModularCoilCells";

    /** 服务端巡检和客户端均会读取此表，需完整构建后通过 volatile 发布，避免读到半填充状态。 */
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
                coilByBlock = m;
            }
            return coilByBlock;
        }
    }

    public static ICoilType coilOf(BlockState state) {
        return coilByBlock().get(state.getBlock());
    }

    /** 允许混放线圈，避免换装中间态使整栋结构失效；记录坐标供主机运行时重新读温。 */
    public static TraceabilityPredicate modularHeatingCoils() {
        return new TraceabilityPredicate((MultiblockState state) -> {
            ICoilType coil = coilOf(state.getBlockState());
            if (coil == null) {
                return false;
            }
            var ctx = state.getMatchContext();
            // 保留上游 CoilWorkableElectricMultiblockMachine 使用的线圈键，提供兜底温度。
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

    private static BlockInfo[] coilPreview() {
        return GTCEuAPI.HEATING_COILS.values().stream()
                .map(s -> BlockInfo.fromBlockState(s.get().defaultBlockState()))
                .toArray(BlockInfo[]::new);
    }

    public static BlockPattern build(MultiblockMachineDefinition def) {
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
