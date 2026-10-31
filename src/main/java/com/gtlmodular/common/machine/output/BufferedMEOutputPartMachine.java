package com.gtlmodular.common.machine.output;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;
import com.gtlmodular.GTLModular;
import com.gtlmodular.common.config.GTLModularConfig;
import com.lowdragmc.lowdraglib.side.fluid.FluidStack;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongIngredient;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEExtendedOutputPartMachine;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.NotifiableMERecipeHandlerTrait;

import java.util.ArrayList;
import java.util.List;

/**
 * ME 数据预处理输出总成：产物先按 key 合并，再一次性写进 ME。
 *
 * <p>模式 A：只有真正写进 ME 的产物才从余量里消失，写不进去的原样退回；
 * 因此“批次完成”永远等价于“产物已在 ME”，不存在完成但只在内存里的窗口。</p>
 *
 * <p>合并只在单次交付内做（局部表），不跨 tick 持有 pending，所以不需要额外存档，
 * 也没有“已刷未存”的重复窗口。</p>
 */
public class BufferedMEOutputPartMachine extends MEExtendedOutputPartMachine {
    private long voidedItems;
    private long voidedFluids;
    private boolean voidedLogged;

    private long statBatches;
    private long statItems;
    private long statMergedKeys;
    private long statInserts;
    private long statInsertNanos;

    public BufferedMEOutputPartMachine(IMachineBlockEntity holder) {
        super(holder);
    }

    private boolean allowedItem(Ingredient ingredient) {
        if (!filterHandler.isHasItemFilter()) {
            return true;
        }
        var probe = new ObjectArrayList<Ingredient>();
        probe.add(ingredient);
        return !filterHandler.testIngredient(probe).isEmpty();
    }

    private boolean allowedFluid(FluidIngredient ingredient) {
        if (!filterHandler.isHasFluidFilter()) {
            return true;
        }
        var probe = new ObjectArrayList<FluidIngredient>();
        probe.add(ingredient);
        return !filterHandler.testFluidIngredient(probe).isEmpty();
    }

    @Override
    protected NotifiableMERecipeHandlerTrait<Ingredient, ItemStack> createItemOutputHandler() {
        return new MEItemOutputHandler(this) {
            @Override
            public boolean outputHasFilter() {
                return filterHandler.isHasItemFilter();
            }

            @Override
            public List<Ingredient> meHandleRecipeOutput(List<?> leftContents, boolean simulate) {
                // 上游默认在“无过滤 + simulate”时直接回答全收；本机要按真实可用量回答，避免开出永远出不了料的批次。
                if (leftContents.isEmpty()) {
                    return List.of();
                }
                var typed = new ObjectArrayList<Ingredient>(leftContents.size());
                for (Object content : leftContents) {
                    typed.add(copyContent(content));
                }
                return meHandleRecipeOutputInner(typed, simulate);
            }

            @Override
            public List<Ingredient> meHandleRecipeOutputInner(List<Ingredient> left, boolean simulate) {
                if (simulate) {
                    return simulateItems(left);
                }
                deliverItems(left);
                return left;
            }
        };
    }

    @Override
    protected NotifiableMERecipeHandlerTrait<FluidIngredient, FluidStack> createFluidOutputHandler() {
        return new MEFluidOutputHandler(this) {
            @Override
            public boolean outputHasFilter() {
                return filterHandler.isHasFluidFilter();
            }

            @Override
            public List<FluidIngredient> meHandleRecipeOutput(List<?> leftContents, boolean simulate) {
                if (leftContents.isEmpty()) {
                    return List.of();
                }
                var typed = new ObjectArrayList<FluidIngredient>(leftContents.size());
                for (Object content : leftContents) {
                    typed.add(copyContent(content));
                }
                return meHandleRecipeOutputInner(typed, simulate);
            }

            @Override
            public List<FluidIngredient> meHandleRecipeOutputInner(List<FluidIngredient> left, boolean simulate) {
                if (simulate) {
                    return simulateFluids(left);
                }
                deliverFluids(left);
                return left;
            }
        };
    }

    /** 过滤命中先处理，其余按 key 合并后一次插入；没插进去的按原顺序重建余量。 */
    private void deliverItems(List<Ingredient> left) {
        MEStorage storage = storageOrNull();
        if (storage == null) {
            return;
        }
        statBatches++;
        boolean voidFiltered = GTLModularConfig.isOutputAssemblyVoidFiltered();
        var groups = new Object2ObjectOpenHashMap<AEKey, ObjectArrayList<Ingredient>>();
        var keyOrder = new ObjectArrayList<AEKey>();
        var totals = new Object2LongOpenHashMap<AEKey>();
        var filtered = new ArrayList<Ingredient>();
        var deferred = new ArrayList<Ingredient>();

        for (Ingredient ingredient : left) {
            if (!allowedItem(ingredient)) {
                filtered.add(ingredient);
                continue;
            }
            ItemStack stack = firstStack(ingredient);
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            long amount = amountOf(ingredient, stack);
            if (amount <= 0) {
                continue;
            }
            AEKey key = AEItemKey.of(stack);
            long current = totals.getLong(key);
            if (current + amount < 0) {
                // 单次交付内同 key 超过 long：整条退回，不做错误合并。
                deferred.add(ingredient);
                continue;
            }
            if (current == 0) {
                keyOrder.add(key);
                groups.put(key, new ObjectArrayList<>());
            }
            totals.put(key, current + amount);
            groups.get(key).add(ingredient);
        }
        statMergedKeys += keyOrder.size();

        var rebuilt = new ArrayList<Ingredient>();
        long start = System.nanoTime();
        for (AEKey key : keyOrder) {
            long total = totals.getLong(key);
            long accepted = storage.insert(key, total, Actionable.MODULATE, actionSource());
            statInserts++;
            statItems += accepted;
            if (accepted < total) {
                rebuildItemRemainder(groups.get(key), total - accepted, rebuilt);
            }
        }
        statInsertNanos += System.nanoTime() - start;
        rebuilt.addAll(deferred);

        if (voidFiltered) {
            for (Ingredient ingredient : filtered) {
                voidedItems += Math.max(1, amountOf(ingredient, firstStack(ingredient)));
            }
        } else {
            rebuilt.addAll(filtered);
        }
        left.clear();
        left.addAll(rebuilt);
        logVoidedOnce();
    }

    private void deliverFluids(List<FluidIngredient> left) {
        MEStorage storage = storageOrNull();
        if (storage == null) {
            return;
        }
        statBatches++;
        boolean voidFiltered = GTLModularConfig.isOutputAssemblyVoidFiltered();
        var groups = new Object2ObjectOpenHashMap<AEKey, ObjectArrayList<FluidIngredient>>();
        var keyOrder = new ObjectArrayList<AEKey>();
        var totals = new Object2LongOpenHashMap<AEKey>();
        var filtered = new ArrayList<FluidIngredient>();
        var deferred = new ArrayList<FluidIngredient>();

        for (FluidIngredient ingredient : left) {
            if (!allowedFluid(ingredient)) {
                filtered.add(ingredient);
                continue;
            }
            FluidStack stack = firstFluid(ingredient);
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            long amount = ingredient.getAmount();
            if (amount <= 0) {
                continue;
            }
            AEKey key = AEFluidKey.of(stack.getFluid(), stack.getTag());
            long current = totals.getLong(key);
            if (current + amount < 0) {
                deferred.add(ingredient);
                continue;
            }
            if (current == 0) {
                keyOrder.add(key);
                groups.put(key, new ObjectArrayList<>());
            }
            totals.put(key, current + amount);
            groups.get(key).add(ingredient);
        }
        statMergedKeys += keyOrder.size();

        var rebuilt = new ArrayList<FluidIngredient>();
        long start = System.nanoTime();
        for (AEKey key : keyOrder) {
            long total = totals.getLong(key);
            long accepted = storage.insert(key, total, Actionable.MODULATE, actionSource());
            statInserts++;
            statItems += accepted;
            if (accepted < total) {
                rebuildFluidRemainder(groups.get(key), total - accepted, rebuilt);
            }
        }
        statInsertNanos += System.nanoTime() - start;
        rebuilt.addAll(deferred);

        if (voidFiltered) {
            for (FluidIngredient ingredient : filtered) {
                voidedFluids += Math.max(1, ingredient.getAmount());
            }
        } else {
            rebuilt.addAll(filtered);
        }
        left.clear();
        left.addAll(rebuilt);
        logVoidedOnce();
    }

    private void rebuildItemRemainder(ObjectArrayList<Ingredient> group, long missing, List<Ingredient> out) {
        for (Ingredient ingredient : group) {
            if (missing <= 0) {
                break;
            }
            ItemStack stack = firstStack(ingredient);
            long amount = amountOf(ingredient, stack);
            long take = Math.min(amount, missing);
            out.add(remaining(ingredient, stack, take));
            missing -= take;
        }
    }

    private void rebuildFluidRemainder(ObjectArrayList<FluidIngredient> group, long missing, List<FluidIngredient> out) {
        for (FluidIngredient ingredient : group) {
            if (missing <= 0) {
                break;
            }
            long take = Math.min(ingredient.getAmount(), missing);
            FluidIngredient rest = ingredient.copy();
            rest.setAmount(take);
            out.add(rest);
            missing -= take;
        }
    }

    private List<Ingredient> simulateItems(List<Ingredient> left) {
        boolean voidFiltered = GTLModularConfig.isOutputAssemblyVoidFiltered();
        MEStorage storage = storageOrNull();
        List<Ingredient> remainder = new ArrayList<>();
        for (Ingredient ingredient : left) {
            if (!allowedItem(ingredient)) {
                if (!voidFiltered) {
                    remainder.add(ingredient);
                }
                continue;
            }
            ItemStack stack = firstStack(ingredient);
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            long amount = amountOf(ingredient, stack);
            if (amount <= 0) {
                continue;
            }
            long accepted = storage == null ? 0
                    : storage.insert(AEItemKey.of(stack), amount, Actionable.SIMULATE, actionSource());
            if (accepted < amount) {
                remainder.add(remaining(ingredient, stack, amount - accepted));
            }
        }
        return remainder;
    }

    private List<FluidIngredient> simulateFluids(List<FluidIngredient> left) {
        boolean voidFiltered = GTLModularConfig.isOutputAssemblyVoidFiltered();
        MEStorage storage = storageOrNull();
        List<FluidIngredient> remainder = new ArrayList<>();
        for (FluidIngredient ingredient : left) {
            if (!allowedFluid(ingredient)) {
                if (!voidFiltered) {
                    remainder.add(ingredient);
                }
                continue;
            }
            FluidStack stack = firstFluid(ingredient);
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            long amount = ingredient.getAmount();
            if (amount <= 0) {
                continue;
            }
            long accepted = storage == null ? 0
                    : storage.insert(AEFluidKey.of(stack.getFluid(), stack.getTag()), amount,
                    Actionable.SIMULATE, actionSource());
            if (accepted < amount) {
                FluidIngredient rest = ingredient.copy();
                rest.setAmount(amount - accepted);
                remainder.add(rest);
            }
        }
        return remainder;
    }

    /** 作废统计只报一次，避免每 tick 刷日志；数量本身是玩家可见行为，不能静默。 */
    private void logVoidedOnce() {
        if (voidedLogged || (voidedItems == 0 && voidedFluids == 0)) {
            return;
        }
        voidedLogged = true;
        GTLModular.LOGGER.info("[gtlmodular] ME 数据预处理输出总成已作废被过滤的产物: 物品={} 流体={} @{}",
                voidedItems, voidedFluids, getPos());
    }

    /** 网络不在时返回 null：simulate 要如实回答“ME 现在收不了”。 */
    private MEStorage storageOrNull() {
        final MEStorage[] holder = new MEStorage[1];
        getMainNode().ifPresent((grid, node) -> holder[0] = grid.getStorageService().getInventory());
        return holder[0];
    }

    private IActionSource actionSource() {
        return IActionSource.ofMachine(getMainNode()::getNode);
    }

    private static long amountOf(Ingredient ingredient, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }
        return ingredient instanceof LongIngredient longIngredient
                ? longIngredient.getActualAmount() : stack.getCount();
    }

    private static ItemStack firstStack(Ingredient ingredient) {
        ItemStack[] stacks = ingredient.getItems();
        return stacks.length == 0 ? null : stacks[0];
    }

    private static FluidStack firstFluid(FluidIngredient ingredient) {
        FluidStack[] stacks = ingredient.getStacks();
        return stacks.length == 0 ? null : stacks[0];
    }

    private static Ingredient remaining(Ingredient ingredient, ItemStack stack, long amount) {
        if (ingredient instanceof LongIngredient longIngredient) {
            return LongIngredient.create(longIngredient.getInner(), amount);
        }
        return LongIngredient.create(Ingredient.of(stack), amount);
    }

    /** 性能计数：批次、实际写入量、合并后的 key 数、插入调用数、插入耗时。 */
    public long statBatches() {
        return statBatches;
    }

    public long statItems() {
        return statItems;
    }

    public long statMergedKeys() {
        return statMergedKeys;
    }

    public long statInserts() {
        return statInserts;
    }

    public long statInsertNanos() {
        return statInsertNanos;
    }
}
