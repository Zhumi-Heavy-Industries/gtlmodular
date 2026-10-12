package com.gtlmodular.common.machine.nanochip;

import com.gtlmodular.common.data.nanochip.NanochipModContext;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gtlmodular.common.data.nanochip.NanochipRecipeTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Locale;

import static com.gtlmodular.common.machine.nanochip.NanochipAssemblyComplexMachine.CALIBRATION_WHITELIST_ENABLED;

final class NanochipCalibrationAccounting {

    private final NanochipAssemblyComplexMachine host;
    private final NanochipCalibration calibration;
    private final Runnable updateSyncedTexts;

    private static final String KEY_CIRCUIT_CALIBRATION = "circuit-calibration";

    private long lastCraftLogTick = -10_000L;

    private long lastWhitelistCountLogTick = -10_000L;

    private long lastWhitelistMissLogTick = -10_000L;

    private long lastCalibrationLogTick = -10_000L;

    private int lastLoggedCalibrationTier = -1;

    NanochipCalibrationAccounting(NanochipAssemblyComplexMachine host, NanochipCalibration calibration,
                                  Runnable updateSyncedTexts) {
        this.host = host;
        this.calibration = calibration;
        this.updateSyncedTexts = updateSyncedTexts;
    }

    /** 配方显式声明的校准族优先于产物 ID 推断，避免自定义配方被错误归族。 */
    static NanochipCalibration.Family familyOf(GTRecipe recipe) {
        if (recipe == null) {
            return NanochipCalibration.Family.PRIMITIVE;
        }
        NanochipCalibration.Family fromData = calibrationFamilyFromData(recipe);
        if (fromData != null) {
            return fromData;
        }
        try {
            List<Content> outs = recipe.getOutputContents(ItemRecipeCapability.CAP);
            for (Content c : outs) {
                ItemStack stack = firstItemStackOf(c.content);
                if (stack != null && !stack.isEmpty()) {
                    return NanochipCalibration.Family.of(stack);
                }
            }
        } catch (RuntimeException ignored) {
        }
        return NanochipCalibration.Family.PRIMITIVE;
    }

    private static NanochipCalibration.Family calibrationFamilyFromData(GTRecipe recipe) {
        if (recipe.data == null) {
            return null;
        }
        if (!recipe.data.contains(KEY_CIRCUIT_CALIBRATION)) {
            return null;
        }
        String raw = recipe.data.getString(KEY_CIRCUIT_CALIBRATION);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String name = raw.trim().toUpperCase(Locale.ROOT);
        if ("BIO".equals(name)) {
            name = "BIOWARE";
        }
        try {
            return NanochipCalibration.Family.valueOf(name);
        } catch (IllegalArgumentException e) {
            // 未知元数据仍可按产物判断，不能提前定为基础族。
            return null;
        }
    }

    static ItemStack firstItemStackOf(Object content) {
        if (content instanceof ItemStack stack) {
            return stack.isEmpty() ? null : stack;
        }
        if (content instanceof ItemStack[] stacks) {
            for (ItemStack stack : stacks) {
                if (stack != null && !stack.isEmpty()) {
                    return stack;
                }
            }
            return null;
        }
        if (content instanceof Ingredient ingredient) {
            try {
                ItemStack[] stacks = ingredient.getItems();
                if (stacks != null) {
                    for (ItemStack stack : stacks) {
                        if (stack != null && !stack.isEmpty()) {
                            return stack;
                        }
                    }
                }
            } catch (RuntimeException e) {
                return null;
            }
        }
        return null;
    }

    /** Ingredient 的份数可能与样例 ItemStack 不同，必须先读其数量接口。 */
    static long producedCountOf(Object content) {
        if (content instanceof org.gtlcore.gtlcore.api.recipe.ingredient.LongIngredient longIngredient) {
            return longIngredient.getActualAmount();
        }
        if (content instanceof com.gregtechceu.gtceu.api.recipe.ingredient.SizedIngredient sized) {
            return sized.getAmount();
        }
        ItemStack stack = firstItemStackOf(content);
        return stack == null ? 1L : Math.max(1L, (long) stack.getCount());
    }

    void logCraftedRecipe(GTRecipe recipe) {
        Level lv = host.getLevel();
        if (lv == null || lv.isClientSide || recipe == null) {
            return;
        }
        long now = lv.getGameTime();
        if (now - this.lastCraftLogTick < 20L) {
            return;
        }
        this.lastCraftLogTick = now;
        // 机器活动表与配方原始表可能不同，诊断须同时显示才能判断候选来源。
        GTRecipeType activeType = host.getRecipeType();
        NanochipModContext.logger().info("[gtlmodular] 主机完成配方 [配方表 {} / 本机模式 {}]：输入 {} → 输出 {}（{} tick）",
                recipe.getType().registryName,
                activeType == null ? "?" : activeType.registryName,
                renderItemContents(recipe.getInputContents(ItemRecipeCapability.CAP)),
                renderItemContents(recipe.getOutputContents(ItemRecipeCapability.CAP)),
                recipe.duration);
    }

    private static String renderItemContents(List<Content> contents) {
        StringBuilder sb = new StringBuilder();
        for (Content c : contents) {
            ItemStack stack = firstItemStackOf(c.content);
            if (sb.length() > 0) {
                sb.append(" + ");
            }
            if (stack == null || stack.isEmpty()) {
                sb.append("（未识别物品×").append(producedCountOf(c.content)).append('）');
                continue;
            }
            sb.append(stack.getHoverName().getString()).append('×').append(producedCountOf(c.content));
        }
        return sb.length() == 0 ? "（无物品）" : sb.toString();
    }

    /** 换算产物可能是 CC，记账须先还原对应真件再判族，避免按包装名误归类。 */
    static NanochipCalibration.Family familyForAccounting(GTRecipe recipe) {
        if (recipe != null) {
            try {
                if (recipe.getType() == NanochipRecipeTypes.CONVERSION) {
                    List<Content> outs = recipe.getOutputContents(ItemRecipeCapability.CAP);
                    for (Content c : outs) {
                        ItemStack produced = firstItemStackOf(c.content);
                        if (produced == null || produced.isEmpty()) {
                            continue;
                        }
                        if (!NanochipConversionMap.isCc(produced)) {
                            continue;
                        }
                        ItemStack real = NanochipConversionMap.materialize(produced);
                        if (real == null || real.isEmpty()) {
                            continue;
                        }
                        ResourceLocation realId = BuiltInRegistries.ITEM.getKey(real.getItem());
                        if (realId == null) {
                            continue;
                        }
                        return NanochipCalibration.Family.of(realId);
                    }
                }
            } catch (RuntimeException e) {
            }
        }
        return NanochipAssemblyComplexMachine.familyOf(recipe);
    }

    void recordProduction(GTRecipe recipe) {
        Level lv = host.getLevel();
        if (lv == null || lv.isClientSide || recipe == null) {
            return;
        }
        if (NanochipCalibrationBatch.hasSources(recipe)) {
            for (NanochipCalibrationBatch.Production production : NanochipCalibrationBatch.sources(recipe)) {
                if (allowsRecipe(production.recipeId())) {
                    calibration.record(production.family(), production.amount());
                    logCalibrationRecord(production.family(), production.amount());
                }
            }
            updateSyncedTexts.run();
            return;
        }
        if (!allowsRecipe(recipe.getId())) return;
        recordOutputs(recipe);
    }

    private boolean allowsRecipe(ResourceLocation whitelistRecipeId) {
        Level lv = host.getLevel();
        if (CALIBRATION_WHITELIST_ENABLED) {
            if (this.lastWhitelistCountLogTick < 0L) {
                this.lastWhitelistCountLogTick = 0L;
                NanochipModContext.logger().debug("[gtlmodular] 校准白名单已生效：当前 {} 条（配置项 "
                                + "nanochipComplex.calibrationRecipeWhitelist；空列表 = 回退内置默认名单）。"
                                + "不在此名册里的配方一律不计入校准。",
                        NanochipModContext.config().getNanochipCalibrationRecipeWhitelistCount());
            }
            if (whitelistRecipeId == null) {
                long now = lv.getGameTime();
                if (now - this.lastWhitelistMissLogTick >= 20L) {
                    this.lastWhitelistMissLogTick = now;
                    NanochipModContext.logger().debug("[gtlmodular] 校准白名单：这条配方没有 id（recipe.getId() == null）"
                                    + "⇒ 不计入校准。检修方向：该配方是不是运行时动态构建（没有数据包 id）的？"
                                    + "若是自有配方，补一份 JSON 让它拿到 id 即可被名册收录（@{}）。",
                            host.getPos());
                }
                return false;
            }
            if (!NanochipCalibration.whitelistMatches(whitelistRecipeId,
                    NanochipModContext.config().getNanochipCalibrationRecipeWhitelist())) {
                long now = lv.getGameTime();
                if (now - this.lastWhitelistMissLogTick >= 20L) {
                    this.lastWhitelistMissLogTick = now;
                    NanochipModContext.logger().debug("[gtlmodular] 校准白名单未命中 ⇒ 这条配方不计入校准：{}"
                                    + "（要放开它：把这一整行 id 复制进配置项 "
                                    + "nanochipComplex.calibrationRecipeWhitelist；前缀匹配就在末尾加一个 *）",
                            whitelistRecipeId);
                }
                return false;
            }
        }
        return true;
    }

    private void recordOutputs(GTRecipe recipe) {
        List<Content> outs = recipe.getOutputContents(ItemRecipeCapability.CAP);
        if (outs.isEmpty()) {
            return;
        }
        // 产出 Content 已包含批次并行，校准计数不能再次乘并行数。
        int unresolved = 0;
        for (Content c : outs) {
            ItemStack stack = firstItemStackOf(c.content);
            if (stack == null || stack.isEmpty()) {
                unresolved++;
                continue;
            }
            NanochipCalibration.Family family = familyForAccounting(recipe);
            int amount = NanochipAssemblyComplexMachine.saturatingToInt(Math.max(1L, producedCountOf(c.content)));
            calibration.record(family, amount);
            logCalibrationRecord(family, amount);
        }
        if (unresolved > 0) {
            NanochipModContext.logger().debug("[gtlmodular] 校准记账 @{}：这条配方有 {} 份物品产出识别不出物品类型 ⇒ "
                            + "这些份数**未记入**校准（不记 0、不记假族）。识别不出 = content 既不是 "
                            + "ItemStack/ItemStack[]，也不是可解析的 Ingredient；检修方向见 "
                            + "NanochipAssemblyComplexMachine#firstItemStackOf 的注释。",
                    host.getPos(), unresolved);
        }
        updateSyncedTexts.run();
    }

    private void logCalibrationRecord(NanochipCalibration.Family family, int amount) {
        Level lv = host.getLevel();
        if (lv == null || lv.isClientSide) {
            return;
        }
        int tier = calibration.activeTier();
        boolean tierChanged = tier != lastLoggedCalibrationTier;
        long now = lv.getGameTime();
        if (!tierChanged && now - this.lastCalibrationLogTick < 20L) {
            return;
        }
        this.lastCalibrationLogTick = now;
        this.lastLoggedCalibrationTier = tier;
        double den = calibration.denominator();
        NanochipModContext.logger().info("[gtlmodular] 校准记账 @{}：{} +{} ⇒ 本族 {} / 历史 {} · 分母 {}（{}）"
                        + " ⇒ 占比 {}%（生效档位 {}）",
                host.getPos(), family.nameZh(), amount, calibration.countOf(family), calibration.total(),
                den <= 0.0D ? "0" : String.format(Locale.ROOT, "%.0f", den),
                NanochipCalibration.useFullWindowDenominator() ? "原版固定 100000" : "已记录数",
                String.format(Locale.ROOT, "%.3f",
                        den <= 0.0D ? 0.0D : calibration.countOf(family) / den * 100.0D),
                tier);
    }

    int clearCalibration() {
        if (host.getLevel() != null && host.getLevel().isClientSide) {
            return 0;
        }
        int before = calibration.clear();
        updateSyncedTexts.run();
        NanochipModContext.logger().info("[gtlmodular] NAC @{} 纳米校准已重置：历史 {} → 0 个电路（只清本机，未动存档键）",
                host.getPos(), before);
        return before;
    }
}
