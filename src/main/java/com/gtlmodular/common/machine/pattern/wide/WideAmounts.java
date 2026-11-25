package com.gtlmodular.common.machine.pattern.wide;

import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongIngredient;
import com.lowdragmc.lowdraglib.side.fluid.FluidStack;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.gtlcore.gtlcore.integration.ae2.AEUtils;
import org.gtlcore.gtlcore.utils.datastructure.Int128;

import java.util.List;

/**
 * ★ 2026-11-25：<b>宽表（{@link Int128}）存取小工具</b> —— 把样板槽的库存从
 * {@code long} 提升到 128 位，语义对齐 gtladditions「超级样板总成」的
 * {@code WideBufferTools}。
 *
 * <h2>为什么自研而不是直接用它</h2>
 * <p>上游那套 {@code com.gtladd.gtladditions.utils.wideBuffer.WideBufferTools} 属于
 * <b>gtladditions</b>，而本模组在 {@code mods.toml} 里把 gtladditions 声明为
 * {@code mandatory=false}（可选）。硬链接它的类 = 没装 gtladditions 就 NoClassDefFoundError。
 * 而 {@link Int128} 本身来自 <b>gtlcore</b>（必装依赖）⇒ 这里只依赖 Int128 + AE2 + GTCEu 自实现，
 * 语义逐条照上游：
 * <ul>
 *   <li>库存表 {@code Map<AEKey, Int128>}：物品与流体混存，按 {@link AEKey} 分派；</li>
 *   <li>按 {@code long} 批量刷网时<b>分块</b>（每键每次最多 {@code limit}），超出部分留到下一轮；</li>
 *   <li>匹配（{@code bestItemMatch} 系列）与 gtlcore {@code SlotCacheManager} 同口径：
 *       逐个 {@code Ingredient.getItems()} 尝试，<b>够数才算命中</b>；</li>
 *   <li>退款/产物回流：先落 gtlcore 的 {@code buffer}（它有自己的 Ticker 负责推网），
 *       与替换前完全同一条出口。</li>
 * </ul>
 *
 * <p><b>纪律</b>：本类全部只读或只动调用方传进来的表，不持有任何全局状态；任何异常都当"不满足"处理。
 */
public final class WideAmounts {

    private WideAmounts() {
    }

    /** 槽内宽表在 NBT 里的键名（与上游同名，便于互导）。 */
    public static final String TAG_WIDE_INPUT = "wideInput";

    // ------------------------------------------------------------------ 基本算术

    /** 累加（饱和：Int128 自己会溢出回绕，这里先比 MAX 再决定是否夹住）。 */
    public static void add(Object2ObjectMap<AEKey, Int128> map, AEKey key, long amount) {
        if (map == null || key == null || amount <= 0L) {
            return;
        }
        Int128 cur = map.get(key);
        if (cur == null) {
            map.put(key, new Int128(amount));
            return;
        }
        Int128 sum = Int128.sum(cur, new Int128(amount));
        // 回绕检测：正数 + 正数却变小 ⇒ 溢出 ⇒ 夹到 MAX_VALUE
        if (sum.compareTo(cur) < 0) {
            sum = Int128.MAX_VALUE;
        }
        map.put(key, sum);
    }

    public static Int128 get(Object2ObjectMap<AEKey, Int128> map, AEKey key) {
        return map == null || key == null ? null : map.get(key);
    }

    /** 表里的量够不够 {@code need}（need <= 0 一律算够）。 */
    public static boolean covers(Int128 have, long need) {
        if (need <= 0L) {
            return true;
        }
        return have != null && have.compareTo(new Int128(need)) >= 0;
    }

    /** 扣减（扣到 0 即删键）。 */
    public static void subtract(Object2ObjectMap<AEKey, Int128> map, AEKey key, long amount) {
        if (map == null || key == null || amount <= 0L) {
            return;
        }
        Int128 cur = map.get(key);
        if (cur == null) {
            return;
        }
        Int128 left = cur.subtract(new Int128(amount));
        if (left.compareTo(Int128.ZERO()) <= 0) {
            map.remove(key);
        } else {
            map.put(key, left);
        }
    }

    // ------------------------------------------------------------------ long 视图（给 gtlcore 既有读口用）

    public static Object2LongOpenHashMap<AEItemKey> itemLongs(Object2ObjectMap<AEKey, Int128> map) {
        Object2LongOpenHashMap<AEItemKey> out = new Object2LongOpenHashMap<>();
        if (map == null) {
            return out;
        }
        for (var entry : map.entrySet()) {
            if (entry.getKey() instanceof AEItemKey itemKey && entry.getValue() != null) {
                long amount = entry.getValue().longValue();
                if (amount > 0L) {
                    out.put(itemKey, amount);
                }
            }
        }
        return out;
    }

    public static Object2LongOpenHashMap<AEFluidKey> fluidLongs(Object2ObjectMap<AEKey, Int128> map) {
        Object2LongOpenHashMap<AEFluidKey> out = new Object2LongOpenHashMap<>();
        if (map == null) {
            return out;
        }
        for (var entry : map.entrySet()) {
            if (entry.getKey() instanceof AEFluidKey fluidKey && entry.getValue() != null) {
                long amount = entry.getValue().longValue();
                if (amount > 0L) {
                    out.put(fluidKey, amount);
                }
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ 存档

    /** 宽表 → {@code ListTag}（每项：key / high / low）。 */
    public static ListTag write(Object2ObjectMap<AEKey, Int128> map) {
        ListTag list = new ListTag();
        if (map == null) {
            return list;
        }
        for (var entry : map.entrySet()) {
            Int128 value = entry.getValue();
            if (entry.getKey() == null || value == null || value.compareTo(Int128.ZERO()) <= 0) {
                continue;
            }
            CompoundTag tag = new CompoundTag();
            tag.put("key", entry.getKey().toTag());
            tag.putLong("high", value.getHigh());
            tag.putLong("low", value.getLow());
            list.add(tag);
        }
        return list;
    }

    /** {@code ListTag} → 宽表（解不出来的项直接跳过，绝不抛）。 */
    public static Object2ObjectOpenHashMap<AEKey, Int128> read(ListTag list) {
        Object2ObjectOpenHashMap<AEKey, Int128> map = new Object2ObjectOpenHashMap<>();
        if (list == null) {
            return map;
        }
        for (Tag element : list) {
            try {
                if (!(element instanceof CompoundTag tag) || !tag.contains("key")) {
                    continue;
                }
                AEKey key = AEKey.fromTagGeneric(tag.getCompound("key"));
                if (key == null) {
                    continue;
                }
                map.put(key, new Int128(tag.getLong("high"), tag.getLong("low")));
            } catch (Throwable ignored) {
                // 单条坏了不影响其余
            }
        }
        return map;
    }

    // ------------------------------------------------------------------ 刷网

    /**
     * 把宽表里的一部分量推回 ME 网络（<b>逐键每次最多 {@code limitPerKey}</b>）。
     *
     * <p>用 gtlcore 的 {@link AEUtils#reFunds} 做真正的插入：它会把自己塞不进去的量<b>留在传入的表里</b>
     * （本仓库既有的 Ticker 就依赖这个语义），因此这里按"推前 − 推后"算实际推走量，再从宽表里扣。
     *
     * @return 是否还有剩余没推完（true = 下轮继续）
     */
    public static boolean flushToNetwork(Object2ObjectMap<AEKey, Int128> pending, IGrid grid,
                                         IActionSource source, int limitPerKey) {
        if (pending == null || pending.isEmpty()) {
            return false;
        }
        Object2LongOpenHashMap<AEKey> batch = new Object2LongOpenHashMap<>();
        for (var entry : pending.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            long want = entry.getValue().longValue();
            if (want <= 0L) {
                continue;
            }
            batch.put(entry.getKey(), limitPerKey > 0 ? Math.min(want, limitPerKey) : want);
        }
        if (batch.isEmpty()) {
            pending.clear();
            return false;
        }
        boolean[] pushed = new boolean[batch.size()];
        try {
            AEUtils.reFunds(batch, grid, source);
        } catch (Throwable ignored) {
            // 推不动就原样留着，下一轮再来
            return true;
        }
        for (Object2LongMap.Entry<AEKey> entry : batch.object2LongEntrySet()) {
            long left = entry.getLongValue();
            long wanted = limitPerKey > 0
                    ? Math.min(longValueOf(pending, entry.getKey()), limitPerKey)
                    : longValueOf(pending, entry.getKey());
            long moved = wanted - Math.max(0L, left);
            if (moved > 0L) {
                subtract(pending, entry.getKey(), moved);
            }
        }
        return !pending.isEmpty();
    }

    private static long longValueOf(Object2ObjectMap<AEKey, Int128> map, AEKey key) {
        Int128 value = map.get(key);
        return value == null ? 0L : value.longValue();
    }

    // ------------------------------------------------------------------ 退款

    /** 把该槽宽表里的料整体退到机器的 {@code buffer}（gtlcore 的 Ticker 会负责推网）。 */
    public static void refundSlot(Object2LongOpenHashMap<AEKey> buffer, Object2ObjectMap<AEKey, Int128> inputs) {
        if (buffer == null || inputs == null || inputs.isEmpty()) {
            return;
        }
        for (var entry : inputs.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            long amount = entry.getValue().longValue();
            if (amount > 0L) {
                buffer.addTo(entry.getKey(), amount);
            }
        }
        inputs.clear();
    }

    // ------------------------------------------------------------------ 匹配（与 gtlcore SlotCacheManager 同口径）

    /** 物品：在宽表里找一条"够数"的 {@link AEItemKey}；找不到返回 {@code null}。 */
    public static AEItemKey bestItemMatch(Ingredient ingredient, Object2ObjectMap<AEKey, Int128> inputs, long need) {
        if (ingredient == null || inputs == null || inputs.isEmpty()) {
            return null;
        }
        for (ItemStack stack : ingredient.getItems()) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            AEItemKey key = AEItemKey.of(stack);
            if (key != null && covers(inputs.get(key), need)) {
                return key;
            }
        }
        return null;
    }

    /** 物品（模拟）：先看宽表，再看该槽催化剂表。 */
    public static AEItemKey bestItemMatchSimulate(Ingredient ingredient, Object2ObjectMap<AEKey, Int128> inputs,
                                                  Object2LongMap<AEItemKey> catalyst, long need) {
        AEItemKey fromInputs = bestItemMatch(ingredient, inputs, need);
        if (fromInputs != null || catalyst == null || catalyst.isEmpty()) {
            return fromInputs;
        }
        for (ItemStack stack : ingredient.getItems()) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            AEItemKey key = AEItemKey.of(stack);
            if (key != null && catalyst.getLong(key) >= need) {
                return key;
            }
        }
        return null;
    }

    /** 流体：在宽表里找一条"够数"的 {@link AEFluidKey}。 */
    public static AEFluidKey bestFluidMatch(FluidIngredient ingredient, Object2ObjectMap<AEKey, Int128> inputs, long need) {
        if (ingredient == null || inputs == null || inputs.isEmpty()) {
            return null;
        }
        for (FluidStack stack : ingredient.getStacks()) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            AEFluidKey key = AEFluidKey.of(stack.getFluid());
            if (key != null && covers(inputs.get(key), need)) {
                return key;
            }
        }
        return null;
    }

    /** 流体（模拟）：先看宽表，再看该槽催化剂表。 */
    public static AEFluidKey bestFluidMatchSimulate(FluidIngredient ingredient, Object2ObjectMap<AEKey, Int128> inputs,
                                                    Object2LongMap<AEFluidKey> catalyst, long need) {
        AEFluidKey fromInputs = bestFluidMatch(ingredient, inputs, need);
        if (fromInputs != null || catalyst == null || catalyst.isEmpty()) {
            return fromInputs;
        }
        for (FluidStack stack : ingredient.getStacks()) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            AEFluidKey key = AEFluidKey.of(stack.getFluid());
            if (key != null && catalyst.getLong(key) >= need) {
                return key;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ 索引侧（配方查找用的"看得见的料"）

    /** 宽表 → {@code List<ItemStack>}（数量夹到 int，与 gtlcore 同款）。 */
    public static ObjectList<ItemStack> limitItems(Object2ObjectMap<AEKey, Int128> inputs) {
        ObjectList<ItemStack> out = new ObjectArrayList<>();
        if (inputs == null) {
            return out;
        }
        for (var entry : inputs.entrySet()) {
            if (entry.getKey() instanceof AEItemKey itemKey && entry.getValue() != null) {
                long amount = entry.getValue().longValue();
                if (amount > 0L) {
                    out.add(itemKey.toStack((int) Math.min(amount, Integer.MAX_VALUE)));
                }
            }
        }
        return out;
    }

    /** 宽表 → {@code List<FluidStack>}。 */
    public static ObjectList<FluidStack> limitFluids(Object2ObjectMap<AEKey, Int128> inputs) {
        ObjectList<FluidStack> out = new ObjectArrayList<>();
        if (inputs == null) {
            return out;
        }
        for (var entry : inputs.entrySet()) {
            if (entry.getKey() instanceof AEFluidKey fluidKey && entry.getValue() != null) {
                long amount = entry.getValue().longValue();
                if (amount > 0L) {
                    out.add(FluidStack.create(fluidKey.getFluid(), Math.max(1L, amount)));
                }
            }
        }
        return out;
    }

    /** 该 Ingredient 在一长表里的"名义数量"（输出侧用；解不出就给 1）。 */
    public static long outputAmount(Ingredient ingredient, ItemStack stack) {
        try {
            if (ingredient instanceof LongIngredient longIngredient) {
                return longIngredient.getActualAmount();
            }
        } catch (Throwable ignored) {
            // 退回到栈自带数量
        }
        return stack == null ? 1L : Math.max(1, stack.getCount());
    }

    /** 工具：把一个 {@code Ingredient} 的产物写进产物池（正在跑的输出先攒在这里，再批量刷网）。 */
    public static void collectItemOutput(Object2ObjectMap<AEKey, Int128> out, List<? extends Ingredient> left) {
        if (out == null || left == null) {
            return;
        }
        for (Ingredient ingredient : left) {
            if (ingredient == null) {
                continue;
            }
            ItemStack[] items = ingredient.getItems();
            if (items.length == 0 || items[0].isEmpty()) {
                continue;
            }
            AEItemKey key = AEItemKey.of(items[0]);
            if (key != null) {
                add(out, key, outputAmount(ingredient, items[0]));
            }
        }
    }
}
