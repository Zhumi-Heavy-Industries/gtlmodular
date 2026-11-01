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

/**
 * 猪咪撕咬炼矿机（天基炼矿单元）结构判定 —— <b>投影里那根太空电梯塔的「太空方块」</b>。
 *
 * <p>逐格规则（字符见 {@link SpaceOrePatternLayers}）：
 * <ul>
 *   <li>'C' 太空电梯机械方块 → {@link #casing()}：<b>可换</b>输入/输出仓与并行仓
 *       （由构建方 {@code GTLModularMachines#ioUnlimited} 追加）；</li>
 *   <li>'I' 太空电梯内部支撑结构 → {@link #internalSupport()}：<b>必须</b>，不可替换；</li>
 *   <li>'B' 太空电梯模块基座 → {@link #moduleBase()}：<b>必须</b>，不可替换；</li>
 *   <li>'P' 太空电梯动力模块槽 → {@link #powerSlot()}：<b>必须</b>放动力模块，MK1~MK5 可互换
 *       （换级立即生效），但不能换成别的东西。</li>
 * </ul>
 * 投影里的其它方块（混凝土 / 铱外壳 / 太空电梯模块连接器 / 空气…）在图案里是 '.'，不限。
 *
 * <p>动力模块槽与主机线圈格同一套做法：判定时把槽位<b>世界坐标</b>记进判定上下文，
 * 机器成形时取走；之后直接读那些坐标上「现在放的是哪一级动力模块」。
 */
public final class SpaceOreModulePattern {

    private SpaceOreModulePattern() {
    }

    /** 判定上下文里存放「动力模块槽世界坐标」的键（机器成形时取走，运行时读动力模块等级）。 */
    public static final String POWER_CELLS_KEY = "GTLModularSpaceOrePowerCells";

    /** 动力模块最高等级（MKΩ = 第 7 级）。 */
    public static final int MAX_POWER_TIER = 7;

    /** 三个太空结构方块。 */
    private static final String CASING_ID = "gtlcore:space_elevator_mechanical_casing";
    private static final String INTERNAL_SUPPORT_ID = "kubejs:space_elevator_internal_support";
    private static final String MODULE_BASE_ID = "kubejs:module_base";

    /**
     * 动力模块方块 id，下标 = 等级 − 1：
     * <ul>
     *   <li>MK1 用基名 {@code gtlcore:power_module}（贴图才是 power_module_1），MK2~MK5 带数字后缀；</li>
     *   <li><b>MK6 / MKΩ</b>（{@code power_module_6} / {@code power_module_7}）由 gtladditions 后加，
     *       注册命名空间可能是 gtlcore 也可能是 gtladditions，两个都收（谁存在用谁）。</li>
     * </ul>
     */
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

    /**
     * 动力模块方块，下标 = 等级 - 1（MK1 在 [0]），每级可能有多个候选 id（MK6/MKΩ 的命名空间不确定）；
     * 某个 id 不存在时该位为 null，但等级映射不会错位。
     */
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

    /** 方块 → 动力模块等级（1..7）；不是动力模块返回 0。 */
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

    /** 等级（1..7）→ 该级的首选动力模块方块；越界返回 null。 */
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

    /** 等级 → 显示名（第 7 级是 MKΩ）。 */
    public static String tierName(int tier) {
        int t = Math.max(1, Math.min(MAX_POWER_TIER, tier));
        return t >= 7 ? "MKΩ" : "MK" + t;
    }

    /**
     * 等级（1..7）→ 跨配方线程数：MK1 = 64，每升一级翻 4 倍
     * （64 / 256 / 1024 / 4096 / 16384 / 65536 / 262144）。
     */
    public static int threadsForTier(int tier) {
        int t = Math.max(1, Math.min(MAX_POWER_TIER, tier));
        long value = (long) 64 << (2 * (t - 1));
        return (int) Math.min(Integer.MAX_VALUE, value);
    }

    /** 太空电梯机械方块格：构建方再 {@code .or(ioUnlimited(true))} 放行输入/输出仓与并行仓。 */
    public static TraceabilityPredicate casing() {
        return blocks(casingBlock());
    }

    /** 太空电梯内部支撑结构格：必须，不可替换。 */
    public static TraceabilityPredicate internalSupport() {
        return blocks(internalSupportBlock());
    }

    /** 太空电梯模块基座格：必须，不可替换。 */
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

    /**
     * 动力模块槽判定：接受 MK1~MKΩ（{@code power_module} ~ {@code power_module_7}）任意一级
     * （<b>可互换</b>），并把槽位世界坐标记进判定上下文，供运行时实时读等级。不能换成别的东西。
     */
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
