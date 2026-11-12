package com.gtlmodular.common.machine.engine;

import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.utils.GTUtil;
import com.gtlmodular.common.machine.nanochip.NanochipRecipeOutputs;
import com.lowdragmc.lowdraglib.side.fluid.FluidStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ★ 2026-11-08：<b>把「各线程产物」求和后写回 GTCEu 的 Jade「配方输出」数据</b>。
 *
 * <h2>问题（队长原话）</h2>
 * <blockquote>「它这个上面的配方输出应该是各线程产物输出的总和呀」</blockquote>
 * GTCEu 的 Jade 提供者 {@code com.gregtechceu.gtceu.integration.jade.provider.RecipeOutputProvider}
 * 读的是 {@code RecipeLogic.getLastRecipe()} —— 单配方字段；本引擎把
 * <b>第 0 条批次</b>写进去（展示回写），于是那行永远只显示<b>一条线程</b>的产物
 * （截图里 9 条线程各 7,372,800 mB，而那行只写 7372B）。
 *
 * <h2>本类怎么做</h2>
 * 由 {@code com.gtlmodular.mixin.gtceu.MixinRecipeOutputProvider} 在
 * {@code RecipeOutputProvider#write} 的末尾调用：把 {@link IThreadBatchLogic#snapshotBatches()}
 * 里<b>每一条在跑批次</b>的产物求和，覆盖 {@code serverData} 里的
 * {@code OutputItems} / {@code OutputFluids}。
 * <ul>
 *   <li>求和口径与「各线程产物」那一节<b>同一个来源</b>
 *       （{@link NanochipRecipeOutputs#itemOutputs} / {@link NanochipRecipeOutputs#fluidOutputs}，
 *       数量已含并行、不重复乘）⇒ 两处数字天然对得上；</li>
 *   <li>★ 2026-11-08：每条产物都带着<b>配方里的原始对象</b>（{@link NanochipRecipeOutputs.OutputView#content()}，
 *       物品含 NBT / 流体含 tag）⇒ 写出去的是<b>原贴图 + 原译名</b>，不是按注册名重建的通用版本；</li>
 *   <li>求和键 = 注册名 + <b>NBT 串</b>：同一注册名但材质/NBT 不同的产物不会被错误合并；</li>
 *   <li>只改 Jade 的 serverData，<b>不动</b> {@code getLastRecipe()}／{@code lastRecipe} 字段
 *       ⇒ 别的读取方（配方锁、NAC 记账、其它模组）看到的仍是原来那条真实配方；</li>
 *   <li>只对实现了 {@link IThreadBatchLogic} 的机器生效（通用引擎 / NAC 外壳 / 地心模块），
 *       别的机器原样交回上游。</li>
 * </ul>
 *
 * <h2>边界</h2>
 * 没有批次 / 求和抛异常 / 一个产物都取不到 ⇒ <b>什么都不改</b>（保持上游原样），
 * 绝不把「配方输出」整行抹掉、也绝不抛到 Jade 的写数据路径上。
 */
public final class ThreadedJadeOutputs {

    /** GTCEu {@code RecipeOutputProvider} 的 serverData 键（客户端 {@code addTooltip} 读的就是这两个）。 */
    private static final String KEY_OUTPUT_ITEMS = "OutputItems";
    private static final String KEY_OUTPUT_FLUIDS = "OutputFluids";

    private ThreadedJadeOutputs() {}

    /** 一个「注册名 + NBT」分组的求和结果（{@code sample} 用来保留原对象的贴图/译名信息）。 */
    private static final class Total {
        long amount;
        final String id;
        final Object sample;

        Total(long amount, String id, Object sample) {
            this.amount = amount;
            this.id = id;
            this.sample = sample;
        }
    }

    /**
     * 如果 {@code recipeLogic} 是线程引擎，就把 {@code data} 里的产物列表换成「各线程之和」。
     *
     * @param data        GTCEu Jade 提供者刚写好的 serverData（允许为 {@code null}）
     * @param recipeLogic 当前机器的配方逻辑（Jade 的 {@code getCapability} 读到的那个）
     */
    public static void rewrite(CompoundTag data, RecipeLogic recipeLogic) {
        if (data == null || !(recipeLogic instanceof IThreadBatchLogic batchLogic)) {
            return;
        }
        if (batchLogic.runningBatches() <= 0) {
            return;
        }

        Map<String, Total> items = new LinkedHashMap<>();
        Map<String, Total> fluids = new LinkedHashMap<>();
        try {
            for (ThreadedRecipeLogic.BatchView view : batchLogic.snapshotBatches()) {
                GTRecipe recipe = view == null ? null : view.recipe();
                if (recipe == null) {
                    continue;
                }
                for (NanochipRecipeOutputs.OutputView out : NanochipRecipeOutputs.itemOutputs(recipe)) {
                    merge(items, out, out.content());
                }
                for (NanochipRecipeOutputs.OutputView out : NanochipRecipeOutputs.fluidOutputs(recipe)) {
                    merge(fluids, out, out.content());
                }
            }
        } catch (Throwable t) {
            // 显示路径：求不出来就保持上游原样，绝不掀翻 Jade 的写数据。
            return;
        }
        if (items.isEmpty() && fluids.isEmpty()) {
            return;
        }

        ListTag itemTags = itemTags(items);
        ListTag fluidTags = fluidTags(fluids);
        data.remove(KEY_OUTPUT_ITEMS);
        data.remove(KEY_OUTPUT_FLUIDS);
        if (!itemTags.isEmpty()) {
            data.put(KEY_OUTPUT_ITEMS, itemTags);
        }
        if (!fluidTags.isEmpty()) {
            data.put(KEY_OUTPUT_FLUIDS, fluidTags);
        }
    }

    /** 同「注册名 + NBT」的多份产物相加；数量溢出饱和到 {@link Long#MAX_VALUE}。 */
    private static void merge(Map<String, Total> map, NanochipRecipeOutputs.OutputView out, Object content) {
        if (out == null || out.id() == null || out.id().isEmpty() || out.amount() <= 0L) {
            return;
        }
        String key = out.id() + '|' + contentKey(content);
        Total total = map.get(key);
        if (total == null) {
            map.put(key, new Total(out.amount(), out.id(), content));
            return;
        }
        long sum = total.amount + out.amount();
        total.amount = sum < 0L ? Long.MAX_VALUE : sum;
    }

    /** NBT 串（没有就是空串）—— 用来区分「同注册名、不同材质/NBT」的产物。 */
    private static String contentKey(Object content) {
        try {
            if (content instanceof ItemStack stack) {
                return String.valueOf(stack.getTag());
            }
            if (content instanceof FluidStack fluid) {
                return String.valueOf(fluid.getTag());
            }
        } catch (Throwable ignored) {
            // 取不到 NBT 就按「无 NBT」归组
        }
        return "";
    }

    /**
     * 物品总和 → {@code OutputItems} 列表。
     *
     * <p>优先用原 {@link ItemStack}（含 NBT）复制出显示栈：数量沿用上游的
     * {@code ItemStack#getCount()}（客户端就用它显示「N × 名」），超过 {@code int} 上限时饱和。
     * 原对象取不到时才退回「按注册名重建」的兜底。
     */
    private static ListTag itemTags(Map<String, Total> items) {
        ListTag tags = new ListTag();
        for (Total total : items.values()) {
            try {
                ItemStack stack = null;
                if (total.sample instanceof ItemStack sample && !sample.isEmpty()) {
                    stack = sample.copy();
                } else {
                    ResourceLocation id = ResourceLocation.tryParse(total.id);
                    Item item = id == null ? null : BuiltInRegistries.ITEM.get(id);
                    if (item != null) {
                        stack = new ItemStack(item);
                        if (stack.isEmpty()) {
                            stack = null;
                        }
                    }
                }
                if (stack == null || stack.isEmpty()) {
                    continue;
                }
                stack.setCount((int) Math.max(1L, Math.min((long) Integer.MAX_VALUE, total.amount)));
                CompoundTag tag = new CompoundTag();
                GTUtil.saveItemStack(stack, tag);
                tags.add(tag);
            } catch (Throwable ignored) {
                // 单条坏掉只跳过那一条（与引擎读档同款纪律）
            }
        }
        return tags;
    }

    /** 流体总和 → {@code OutputFluids} 列表（数量是 long，不截断；保留原流体 tag）。 */
    private static ListTag fluidTags(Map<String, Total> fluids) {
        ListTag tags = new ListTag();
        for (Total total : fluids.values()) {
            try {
                long amount = Math.max(1L, total.amount);
                FluidStack stack = null;
                if (total.sample instanceof FluidStack sample && !sample.isEmpty()) {
                    stack = FluidStack.create(sample.getRawFluid(), amount, sample.getTag());
                } else {
                    ResourceLocation id = ResourceLocation.tryParse(total.id);
                    Fluid fluid = id == null ? null : BuiltInRegistries.FLUID.get(id);
                    if (fluid != null && fluid != Fluids.EMPTY) {
                        stack = FluidStack.create(fluid, amount);
                    }
                }
                if (stack == null || stack.isEmpty()) {
                    continue;
                }
                tags.add(stack.saveToTag(new CompoundTag()));
            } catch (Throwable ignored) {
                // 同上：单条坏掉只跳过那一条
            }
        }
        return tags;
    }
}
