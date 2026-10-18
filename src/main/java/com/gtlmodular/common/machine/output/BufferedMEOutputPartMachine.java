package com.gtlmodular.common.machine.output;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.MEStorage;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;
import com.lowdragmc.lowdraglib.side.fluid.FluidStack;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEExtendedOutputPartMachine;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.NotifiableMERecipeHandlerTrait;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongIngredient;

import java.util.List;

/**
 * 带合并的 ME 输出总成（sink 补丁，模式 A：产物先真进 ME，批次才算完成）。
 *
 * <p><b>未在本仓库编译验证</b>：接口签名取自 {@code deps-src/gtlcore-1.2.3.2-fix4} 的反编译源码，
 * 若上游更新导致签名变化，需按报错修。</p>
 *
 * <p>语义（对应联合讨论稿 §四 / §8）：</p>
 * <ul>
 *   <li>simulate：沿用上游过滤语义，只回答“哪些会被拒收”，不改状态；</li>
 *   <li>真实交付：同一 capability 下按 {@link AEKey} 合并 long 数量；每个 key 先
 *       {@link Actionable#SIMULATE} 问一次，能全收才 MODULATE，做不到就整组留在余量里——</li>
 *   <li>因此不跨 tick 持有 pending：余量由调用方（引擎 payload）持有，
 *       “批次完成 = 产物已在 ME”，不存在“完成但只在内存里”的窗口；</li>
 *   <li>过滤在合并前完成，未通过过滤的内容原样留在余量里，由上游决定丢弃或另走通道。</li>
 * </ul>
 */
public class BufferedMEOutputPartMachine extends MEExtendedOutputPartMachine {
    public BufferedMEOutputPartMachine(IMachineBlockEntity holder) {
        super(holder);
    }

    private boolean allowedItem(Ingredient ingredient) {
        if (!filterHandler.isHasItemFilter()) return true;
        var probe = new ObjectArrayList<Ingredient>();
        probe.add(ingredient);
        return !filterHandler.testIngredient(probe).isEmpty();
    }

    private boolean allowedFluid(FluidIngredient ingredient) {
        if (!filterHandler.isHasFluidFilter()) return true;
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
                    return filterHandler.testIngredient(left);
                }
                mergeAndInsertItems(left);
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
                    return filterHandler.testFluidIngredient(left);
                }
                mergeAndInsertFluids(left);
                return left;
            }
        };
    }

    /** 逐条真实插入，按实际接收量保留余量；不先模拟再插，避免“插一半、余量整条留下”的重复。 */
    private void mergeAndInsertItems(List<Ingredient> left) {
        ifPresentStorage(storage -> insertItems(storage, left));
    }

    private void mergeAndInsertFluids(List<FluidIngredient> left) {
        ifPresentStorage(storage -> insertFluids(storage, left));
    }

    private void insertItems(MEStorage storage, List<Ingredient> left) {
        var iterator = left.listIterator();
        while (iterator.hasNext()) {
            Ingredient ingredient = iterator.next();
            ItemStack stack = firstStack(ingredient);
            if (stack == null || stack.isEmpty() || !allowedItem(ingredient)) continue;
            long amount = ingredient instanceof LongIngredient longIngredient
                    ? longIngredient.getActualAmount() : stack.getCount();
            if (amount <= 0L) continue;
            long accepted = storage.insert(AEItemKey.of(stack), amount, Actionable.MODULATE, actionSource());
            if (accepted >= amount) {
                iterator.remove();
            } else if (accepted > 0L) {
                iterator.set(remaining(ingredient, stack, amount - accepted));
            }
        }
    }

    private void insertFluids(MEStorage storage, List<FluidIngredient> left) {
        var iterator = left.listIterator();
        while (iterator.hasNext()) {
            FluidIngredient ingredient = iterator.next();
            FluidStack stack = firstFluid(ingredient);
            if (stack == null || stack.isEmpty() || !allowedFluid(ingredient)) continue;
            long amount = ingredient.getAmount();
            if (amount <= 0L) continue;
            long accepted = storage.insert(AEFluidKey.of(stack.getFluid(), stack.getTag()), amount,
                    Actionable.MODULATE, actionSource());
            if (accepted >= amount) {
                iterator.remove();
            } else if (accepted > 0L) {
                FluidIngredient rest = ingredient.copy();
                rest.setAmount(amount - accepted);
                iterator.set(rest);
            }
        }
    }

    private static Ingredient remaining(Ingredient ingredient, ItemStack stack, long amount) {
        if (ingredient instanceof LongIngredient longIngredient) {
            return LongIngredient.create(longIngredient.getInner(), amount);
        }
        return LongIngredient.create(Ingredient.of(stack), amount);
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
}
