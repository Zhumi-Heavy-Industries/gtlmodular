package com.gtlmodular.common.machine.nanochip;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.utils.GTUtil;
import com.gtlmodular.common.data.nanochip.NanochipComplexPattern;
import com.gtlmodular.common.data.nanochip.NanochipModContext;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 组装矩阵的部装外壳档：解析 20 个装配格外壳的档位、同级判定、EU/t 折扣与配方要求档。
 * 结构与配方规则集中在这里，避免继续堆进上千行的机器类。
 *
 * <p>NH 的组装矩阵用结构 'B' 位的部件装配线外壳元数据算出 {@code machineTier}，
 * 配方侧用量 {@code NanochipAssemblyMatrixTierKey}（默认 1）；GTCEu 1.4.4 没有
 * 自定义配方元数据类型，配方的同一个键放在 {@code recipe.data} 里，语义相同。
 */
final class NanochipAssemblyMatrixTier {

    private NanochipAssemblyMatrixTier() {
    }

    /** 与结构谓词同一份档位表：下标 + 1 即 GT 档位（lv=1 … max=14）。 */
    private static final String[] CASING_TIERS = {
            "lv", "mv", "hv", "ev", "iv", "luv", "zpm", "uv", "uhv", "uev", "uiv", "uxv", "opv", "max"};

    private static final String CASING_PREFIX = "gtlcore:component_assembly_line_casing_";

    /** gtlcore 的 14 级外壳全缺时的回落方块，只能是 1 档。 */
    private static final String CASING_FALLBACK = "gtceu:assembly_line_casing";

    private static final String METADATA_KEY = "nanochip_assembly_matrix_tier";

    /** NH 的元数据缺失时默认档（NanochipAssemblyMatrixTierKey.INSTANCE 的默认值 1）。 */
    private static final int DEFAULT_RECIPE_TIER = 1;

    private static final int MAX_CASING_TIER = CASING_TIERS.length;

    /**
     * 元数据缺失时是否按配方电压档兜底。NH 的默认值是 1 档，按电压兜底会把
     * 没有元数据的第三方配方一并卡在「外壳档不够」上，所以默认关掉。
     */
    public static final boolean FALLBACK_BY_RECIPE_VOLTAGE = false;

    /**
     * 严格同级：不同级的组装矩阵不成形，对齐 NH 的「20 格必须同级」。
     * 关掉则改为取 20 格里最高的一档（不破坏老存档，但与 NH 不一致）。
     */
    public static final boolean STRICT_SAME_TIER = true;

    private static final Block[] CASING_BLOCKS = buildCasingBlocks();

    private static final Block FALLBACK_BLOCK =
            BuiltInRegistries.BLOCK.get(new ResourceLocation(CASING_FALLBACK));

    private static final Map<NanochipModuleMachine, Need> NEEDS =
            Collections.synchronizedMap(new WeakHashMap<>());

    /** 不同级的模块会一直抢成形，告警必须限流，否则刷满日志。 */
    private static final long LOG_INTERVAL_TICKS = 200L;

    private static long lastMismatchLogTick = -10_000L;

    /** 20 格扫描的缓存：档位、齐不齐、每档多少格。 */
    static final class Need {

        /** 只有整盒都在加载区里才算扫到；否则 tier 保持 0，避免误判成「不成形」。 */
        boolean scanned;
        int tier;
        boolean uniform = true;
        String tally = "";
    }

    private static Block[] buildCasingBlocks() {
        Block[] out = new Block[CASING_TIERS.length];
        for (int i = 0; i < CASING_TIERS.length; i++) {
            out[i] = BuiltInRegistries.BLOCK.get(new ResourceLocation(CASING_PREFIX + CASING_TIERS[i]));
        }
        return out;
    }

    private static boolean registered(Block block) {
        return block != null && block != Blocks.AIR;
    }

    private static int indexOf(Block block) {
        for (int i = 0; i < CASING_BLOCKS.length; i++) {
            if (CASING_BLOCKS[i] == block) {
                return i;
            }
        }
        return -1;
    }

    /** 一台机器只缓存最近一次扫描：结构失效与手动刷新时清掉。 */
    private static Need needOf(NanochipModuleMachine module) {
        Need need = NEEDS.get(module);
        if (need != null) {
            return need;
        }
        synchronized (NEEDS) {
            need = NEEDS.get(module);
            if (need == null) {
                need = new Need();
                NEEDS.put(module, need);
            }
            return need;
        }
    }

    static void forget(NanochipModuleMachine module) {
        if (module != null) {
            NEEDS.remove(module);
        }
    }

    /** 0 = 不是装配线外壳；否则是 1..14 档。 */
    private static int tierOf(Block block) {
        int idx = indexOf(block);
        if (idx >= 0) {
            return idx + 1;
        }
        return registered(block) && block == FALLBACK_BLOCK ? 1 : 0;
    }

    /**
     * 成形判定闸：按结构表登记的 20 个装配格逐格读外壳档（塔身比控制器的 ±3 盒更大，
     * 不能用包围盒猜坐标）。只有同级（严格档）才放行；不同级时给出可读的一行原因。
     */
    static boolean checkPattern(NanochipModuleMachine module) {
        Level level = module.getLevel();
        if (level == null || level.isClientSide) {
            return true;
        }
        var definition = module.getDefinition();
        if (definition == null || definition.getId() == null) {
            return true;
        }
        int[] cells = NanochipComplexPattern.moduleAssemblyCells(definition.getId().toString());
        if (cells == null || cells.length == 0) {
            return true;
        }
        Need need = needOf(module);
        int cellCount = cells.length / 3;
        int[] counts = new int[MAX_CASING_TIER + 1];
        int[] offset = new int[3];
        BlockPos origin = module.getPos();
        for (int element = 0; element < NanochipExactPrecheck.ELEMENTS; element++) {
            java.util.Arrays.fill(counts, 0);
            boolean complete = true;
            for (int i = 0; i < cellCount; i++) {
                int base = i * 3;
                NanochipComplexPattern.elementOffset(element,
                        NanochipComplexPattern.MOD_CTRL_X - cells[base],
                        cells[base + 1] - NanochipComplexPattern.MOD_CTRL_ROW,
                        NanochipComplexPattern.MOD_CTRL_Z - cells[base + 2], offset);
                BlockPos pos = origin.offset(offset[0], offset[1], offset[2]);
                if (!level.isLoaded(pos)) {
                    // 区块没加载完不能当成结构错误，交上游完整匹配去判。
                    return true;
                }
                int tier = tierOf(level.getBlockState(pos).getBlock());
                if (tier <= 0) {
                    complete = false;
                    break;
                }
                counts[tier]++;
            }
            if (!complete) {
                continue;
            }
            int distinct = 0;
            int highest = 0;
            int lowest = 0;
            StringBuilder tally = new StringBuilder();
            for (int tier = 1; tier <= MAX_CASING_TIER; tier++) {
                if (counts[tier] <= 0) {
                    continue;
                }
                distinct++;
                lowest = lowest == 0 ? tier : lowest;
                highest = tier;
                if (tally.length() > 0) {
                    tally.append('+');
                }
                tally.append(CASING_TIERS[tier - 1]).append('×').append(counts[tier]);
            }
            need.tally = tally.toString();
            need.scanned = true;
            need.tier = STRICT_SAME_TIER ? lowest : highest;
            need.uniform = distinct <= 1;
            if (STRICT_SAME_TIER && !need.uniform) {
                long now = level.getGameTime();
                if (now - lastMismatchLogTick >= LOG_INTERVAL_TICKS) {
                    lastMismatchLogTick = now;
                    NanochipModContext.logger().warn("[gtlmodular] NAC 组装矩阵 @{} 的 20 格部装外壳不同级（{}）"
                            + "⇒ 不成形；请把 20 格换成同一档外壳", origin, need.tally);
                }
                return false;
            }
            return true;
        }
        // 20 格没凑齐装配外壳：结构本身还没搭好，交上游完整匹配判，不在这里拒绝。
        need.scanned = false;
        need.tier = 0;
        need.uniform = false;
        return true;
    }

    /**
     * 已扫描到的机器档。读档后模块可能直接就是成形态、没再跑成形闸，这里补扫一次；
     * 补扫拿不到 20 格（区块没加载等）时返回 -1，调用方按「不参与判定」处理，不会误拒配方。
     */
    static int machineTier(NanochipModuleMachine module) {
        Need need = NEEDS.get(module);
        if ((need == null || !need.scanned) && module.isFormed()) {
            checkPattern(module);
            need = NEEDS.get(module);
        }
        return need == null || need.tier <= 0 ? -1 : need.tier;
    }

    /** 配方要求的外壳档：先读配方元数据，缺失时按兜底策略算。 */
    static int recipeTier(GTRecipe recipe) {
        if (recipe == null) {
            return DEFAULT_RECIPE_TIER;
        }
        var data = recipe.data;
        if (data != null && data.contains(METADATA_KEY)) {
            return clamp(data.getInt(METADATA_KEY));
        }
        if (FALLBACK_BY_RECIPE_VOLTAGE) {
            return clamp(GTUtil.getTierByVoltage(Math.max(1L, RecipeHelper.getInputEUt(recipe))));
        }
        return DEFAULT_RECIPE_TIER;
    }

    private static int clamp(int tier) {
        return Math.max(1, Math.min(MAX_CASING_TIER, tier));
    }

    /**
     * 组装矩阵把「配方等级」重定义为部装外壳档：外壳档低于配方要求时不取该配方。
     * 返回 -1 表示该配方对这台矩阵不可用。
     */
    static int gatedRecipeTier(NanochipModuleMachine module, GTRecipe recipe) {
        int machine = machineTier(module);
        int required = recipeTier(recipe);
        if (machine > 0 && machine < required) {
            NanochipModContext.logger().debug("[gtlmodular] NAC 组装矩阵 @{} 外壳 {} 档 < 配方要求 {} 档"
                    + "⇒ 不取该配方", module.getPos(), machine, required);
            return -1;
        }
        return required;
    }

    /**
     * 超频后的额外吃电：EU/t 乘 {@code 0.95^(machineTier − recipeTier)}。
     * 同级时倍率恰为 1，不做任何改动。
     */
    static double euDiscount(int machineTier, int recipeTier) {
        int diff = machineTier - recipeTier;
        if (diff <= 0) {
            return 1.0D;
        }
        double factor = Math.pow(0.95D, diff);
        return factor >= 1.0D ? 1.0D : factor;
    }

    /** 只有 20 格同级（或非严格档）才给折扣；未扫描到档位一律不打折。 */
    static double euDiscountFor(NanochipModuleMachine module, GTRecipe recipe) {
        return euDiscount(machineTier(module), recipeTier(recipe));
    }
}
