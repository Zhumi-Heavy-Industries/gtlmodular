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
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongIngredient;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEExtendedOutputPartMachine;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.NotifiableMERecipeHandlerTrait;

import java.util.ArrayList;
import java.util.List;

/**
 * 带合并的 ME 输出总成（模式 A：产物先真进 ME，批次才算完成）。
 *
 * <p>交付路径：同一批内容先按 {@link AEKey} 合并 long（{@link MEOutputBuffer}），再逐 key 直接
 * {@link MEStorage#insert}，按实际接受量扣 pending；刷不出去的余量立刻用
 * {@link MEOutputBuffer#drainRemainders()} 交还调用方，因此不跨 tick 持有 pending，
 * “批次完成”永远等价于“产物已在 ME”。</p>
 *
 * <p>过滤在合并前完成。被过滤的内容和没插进去的余量一样交回调用方：引擎会把它转给普通输出仓，
 * 只做 ME 输出的机器需要另配普通仓或作废手段，否则批次会一直等待。</p>
 */
public class BufferedMEOutputPartMachine extends MEExtendedOutputPartMachine {
    private static final int PENDING_ENTRIES = 512;
    private static final Int128 PENDING_LIMIT = Int128.ofUnsigned(1L, 0L);
    private static final FairPendingFlusher.Budget FLUSH_BUDGET =
            new FairPendingFlusher.Budget(PENDING_ENTRIES, Long.MAX_VALUE);

    private long voidedItems;
    private long voidedFluids;
    private boolean voidedLogged;

    private static final MEOutputBuffer.Codec<AEKey, Ingredient> ITEM_CODEC = new MEOutputBuffer.Codec<>() {
        @Override
        public AEKey keyOf(Ingredient value) {
            return AEItemKey.of(firstStack(value));
        }

        @Override
        public long amountOf(Ingredient value) {
            ItemStack stack = firstStack(value);
            if (stack == null || stack.isEmpty()) {
                return 0;
            }
            return value instanceof LongIngredient longIngredient ? longIngredient.getActualAmount() : stack.getCount();
        }

        @Override
        public Ingredient withAmount(Ingredient value, long amount) {
            return remaining(value, firstStack(value), amount);
        }
    };

    private static final MEOutputBuffer.Codec<AEKey, FluidIngredient> FLUID_CODEC = new MEOutputBuffer.Codec<>() {
        @Override
        public AEKey keyOf(FluidIngredient value) {
            FluidStack stack = firstFluid(value);
            return AEFluidKey.of(stack.getFluid(), stack.getTag());
        }

        @Override
        public long amountOf(FluidIngredient value) {
            return value.getAmount();
        }

        @Override
        public FluidIngredient withAmount(FluidIngredient value, long amount) {
            FluidIngredient rest = value.copy();
            rest.setAmount(amount);
            return rest;
        }
    };

    private final MEOutputBuffer<AEKey, Ingredient> itemBuffer = new MEOutputBuffer<>(
            new PendingProducts<>(new PendingProducts.Capacity(PENDING_ENTRIES, PENDING_LIMIT, PENDING_LIMIT)),
            ITEM_CODEC, this::allowedItem);

    private final MEOutputBuffer<AEKey, FluidIngredient> fluidBuffer = new MEOutputBuffer<>(
            new PendingProducts<>(new PendingProducts.Capacity(PENDING_ENTRIES, PENDING_LIMIT, PENDING_LIMIT)),
            FLUID_CODEC, this::allowedFluid);

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
            public List<FluidIngredient> meHandleRecipeOutputInner(List<FluidIngredient> left, boolean simulate) {
                if (simulate) {
                    return simulateFluids(left);
                }
                deliverFluids(left);
                return left;
            }
        };
    }

    private void deliverItems(List<Ingredient> left) {
        ifPresentStorage(storage -> {
            var accepted = itemBuffer.accept(left, false);
            itemBuffer.flush(sinkFor(storage), FLUSH_BUDGET, false);
            List<Ingredient> leftover = new ArrayList<>(accepted.remainder());
            leftover.addAll(itemBuffer.drainRemainders());
            if (GTLModularConfig.isOutputAssemblyVoidFiltered()) {
                accepted.filtered().forEach(value -> voidedItems += Math.max(1, ITEM_CODEC.amountOf(value)));
            } else {
                leftover.addAll(accepted.filtered());
            }
            left.clear();
            left.addAll(leftover);
            logVoidedOnce();
        });
    }

    private void deliverFluids(List<FluidIngredient> left) {
        ifPresentStorage(storage -> {
            var accepted = fluidBuffer.accept(left, false);
            fluidBuffer.flush(sinkFor(storage), FLUSH_BUDGET, false);
            List<FluidIngredient> leftover = new ArrayList<>(accepted.remainder());
            leftover.addAll(fluidBuffer.drainRemainders());
            if (GTLModularConfig.isOutputAssemblyVoidFiltered()) {
                accepted.filtered().forEach(value -> voidedFluids += Math.max(1, FLUID_CODEC.amountOf(value)));
            } else {
                leftover.addAll(accepted.filtered());
            }
            left.clear();
            left.addAll(leftover);
            logVoidedOnce();
        });
    }

    /** 作废统计只报一次，避免每 tick 刷日志；数量本身是玩家可见行为，不能静默。 */
    private void logVoidedOnce() {
        if (voidedLogged || (voidedItems == 0 && voidedFluids == 0)) {
            return;
        }
        voidedLogged = true;
        GTLModular.LOGGER.info("[gtlmodular] 带合并的 ME 输出总成已作废被过滤的产物: 物品={} 流体={} @{}",
                voidedItems, voidedFluids, getPos());
    }

    private List<Ingredient> simulateItems(List<Ingredient> left) {
        boolean voidFiltered = GTLModularConfig.isOutputAssemblyVoidFiltered();
        List<Ingredient> remainder = new ArrayList<>();
        for (Ingredient ingredient : left) {
            if (!allowedItem(ingredient)) {
                if (!voidFiltered) {
                    remainder.add(ingredient);
                }
                continue;
            }
            long amount = ITEM_CODEC.amountOf(ingredient);
            if (amount <= 0) {
                continue;
            }
            Int128 wouldAccept = itemBuffer.simulateAccept(ITEM_CODEC.keyOf(ingredient), Int128.of(amount));
            long rest = amount - wouldAccept.toLongClamped();
            if (rest > 0) {
                remainder.add(remaining(ingredient, firstStack(ingredient), rest));
            }
        }
        return remainder;
    }

    private List<FluidIngredient> simulateFluids(List<FluidIngredient> left) {
        boolean voidFiltered = GTLModularConfig.isOutputAssemblyVoidFiltered();
        List<FluidIngredient> remainder = new ArrayList<>();
        for (FluidIngredient ingredient : left) {
            if (!allowedFluid(ingredient)) {
                if (!voidFiltered) {
                    remainder.add(ingredient);
                }
                continue;
            }
            long amount = FLUID_CODEC.amountOf(ingredient);
            if (amount <= 0) {
                continue;
            }
            Int128 wouldAccept = fluidBuffer.simulateAccept(FLUID_CODEC.keyOf(ingredient), Int128.of(amount));
            long rest = amount - wouldAccept.toLongClamped();
            if (rest > 0) {
                remainder.add(FLUID_CODEC.withAmount(ingredient, rest));
            }
        }
        return remainder;
    }

    private FairPendingFlusher.Sink<AEKey> sinkFor(MEStorage storage) {
        return new FairPendingFlusher.Sink<>() {
            @Override
            public long insert(AEKey key, long amount) {
                return storage.insert(key, amount, Actionable.MODULATE, actionSource());
            }

            @Override
            public long simulate(AEKey key, long amount) {
                return storage.insert(key, amount, Actionable.SIMULATE, actionSource());
            }
        };
    }

    private void ifPresentStorage(java.util.function.Consumer<MEStorage> consumer) {
        getMainNode().ifPresent((grid, node) ->
                consumer.accept(grid.getStorageService().getInventory()));
    }

    private IActionSource actionSource() {
        return IActionSource.ofMachine(getMainNode()::getNode);
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
}
