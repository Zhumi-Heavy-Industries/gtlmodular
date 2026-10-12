package com.gtlmodular.common.data.module;

import com.gregtechceu.gtceu.api.pattern.MultiblockState;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.lowdragmc.lowdraglib.utils.BlockInfo;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static com.gregtechceu.gtceu.api.pattern.Predicates.blocks;

/** 记录动力模块坐标供运行时读等级，使换装无需依赖结构重新成形。 */
public final class SpaceOreModulePattern {

    private SpaceOreModulePattern() {
    }

    public static final String POWER_CELLS_KEY = "GTLModularSpaceOrePowerCells";

    public static final int MAX_POWER_TIER = 7;

    private static final String CASING_ID = "gtlcore:space_elevator_mechanical_casing";
    private static final String INTERNAL_SUPPORT_ID = "kubejs:space_elevator_internal_support";
    private static final String MODULE_BASE_ID = "kubejs:module_base";

    private static final String[][] POWER_MODULE_IDS = {
            {"gtlcore:power_module"},
            {"gtlcore:power_module_2"},
            {"gtlcore:power_module_3"},
            {"gtlcore:power_module_4"},
            {"gtlcore:power_module_5"},
            {"gtlcore:power_module_6", "gtladditions:power_module_6"},
            {"gtlcore:power_module_7", "gtladditions:power_module_7"},
    };

    private static Block casingBlock;
    private static Block internalSupportBlock;
    private static Block moduleBaseBlock;
    private static Block[][] powerBlocks;
    private static BlockInfo[] powerPreview;

    private static Block require(String id) {
        Block b = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(id));
        if (b == null) {
            throw new IllegalStateException("space ore pattern missing block: " + id);
        }
        return b;
    }

    public static Block casingBlock() {
        if (casingBlock == null) {
            casingBlock = require(CASING_ID);
        }
        return casingBlock;
    }

    public static Block internalSupportBlock() {
        if (internalSupportBlock == null) {
            internalSupportBlock = require(INTERNAL_SUPPORT_ID);
        }
        return internalSupportBlock;
    }

    public static Block moduleBaseBlock() {
        if (moduleBaseBlock == null) {
            moduleBaseBlock = require(MODULE_BASE_ID);
        }
        return moduleBaseBlock;
    }

    /** 缺失候选保留空位，避免后续等级与数组下标错位。 */
    public static Block[][] powerBlocks() {
        if (powerBlocks == null) {
            Block[][] arr = new Block[POWER_MODULE_IDS.length][];
            for (int i = 0; i < arr.length; i++) {
                String[] ids = POWER_MODULE_IDS[i];
                Block[] tier = new Block[ids.length];
                for (int j = 0; j < ids.length; j++) {
                    tier[j] = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(ids[j]));
                }
                arr[i] = tier;
            }
            powerBlocks = arr;
        }
        return powerBlocks;
    }

    public static int powerTierOf(BlockState state) {
        if (state == null) {
            return 0;
        }
        Block b = state.getBlock();
        Block[][] arr = powerBlocks();
        for (int i = 0; i < arr.length; i++) {
            for (Block candidate : arr[i]) {
                if (candidate != null && candidate == b) {
                    return i + 1;
                }
            }
        }
        return 0;
    }

    public static Block powerBlock(int tier) {
        Block[][] arr = powerBlocks();
        if (tier < 1 || tier > arr.length) {
            return null;
        }
        for (Block b : arr[tier - 1]) {
            if (b != null) {
                return b;
            }
        }
        return null;
    }

    public static String tierName(int tier) {
        int t = Math.max(1, Math.min(MAX_POWER_TIER, tier));
        return t >= 7 ? "MKΩ" : "MK" + t;
    }

    public static int threadsForTier(int tier) {
        int t = Math.max(1, Math.min(MAX_POWER_TIER, tier));
        long value = (long) 64 << (2 * (t - 1));
        return (int) Math.min(Integer.MAX_VALUE, value);
    }

    public static TraceabilityPredicate casing() {
        return blocks(casingBlock());
    }

    public static TraceabilityPredicate internalSupport() {
        return blocks(internalSupportBlock());
    }

    public static TraceabilityPredicate moduleBase() {
        return blocks(moduleBaseBlock());
    }

    private static BlockInfo[] powerPreview() {
        if (powerPreview == null) {
            List<BlockInfo> infos = new ArrayList<>();
            for (Block[] tier : powerBlocks()) {
                for (Block b : tier) {
                    if (b != null) {
                        infos.add(BlockInfo.fromBlockState(b.defaultBlockState()));
                    }
                }
            }
            powerPreview = infos.toArray(new BlockInfo[0]);
        }
        return powerPreview;
    }

    public static TraceabilityPredicate powerSlot() {
        return new TraceabilityPredicate(SpaceOreModulePattern::testPowerSlot,
                (Supplier<BlockInfo[]>) SpaceOreModulePattern::powerPreview)
                .addTooltips(Component.translatable("gtlmodular.space.tooltip.power_slot"));
    }

    private static boolean testPowerSlot(MultiblockState state) {
        int tier = powerTierOf(state.getBlockState());
        if (tier <= 0) {
            return false;
        }
        List<BlockPos> cells = state.getMatchContext().getOrPut(POWER_CELLS_KEY, new ObjectArrayList<BlockPos>());
        cells.add(state.getPos().immutable());
        return true;
    }
}
