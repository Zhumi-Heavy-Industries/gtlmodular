package com.gtlmodular.common.machine.independent.io;

import com.gregtechceu.gtceu.api.capability.recipe.*;
import com.gregtechceu.gtceu.integration.ae2.slot.ExportOnlyAEFluidList;
import com.gregtechceu.gtceu.integration.ae2.slot.ExportOnlyAEItemList;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableEnergyContainer;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableFluidTank;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.api.misc.FluidRecipeHandler;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;
import com.lowdragmc.lowdraglib.misc.FluidStorage;
import com.lowdragmc.lowdraglib.misc.ItemStackTransfer;
import org.gtlcore.gtlcore.integration.gtmt.InfinityEnergyContainer;
import net.minecraft.world.item.crafting.Ingredient;
import org.gtlcore.gtlcore.api.machine.trait.NotifiableCircuitItemStackHandler;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

final class StorageTransfers {
    private final boolean simulate;
    private final SharedTickPower power;
    private final Map<Object, Object> copies = new IdentityHashMap<>();

    StorageTransfers(boolean simulate) { this(simulate, null); }

    StorageTransfers(boolean simulate, SharedTickPower power) {
        this.simulate = simulate;
        this.power = power;
    }

    static void requireSupported(IRecipeHandler<?> handler) {
        if (handler instanceof InfinityEnergyContainer) return;
        // AE 只输出仓：容量由 ME 网络决定，真实数量由上游 handleRecipeInner 计数（模拟/真实都返回余量）。
        if (handler instanceof ExportOnlyAEItemList || handler instanceof ExportOnlyAEFluidList) return;
        if (handler.getClass() == NotifiableCircuitItemStackHandler.class) return;
        // 子类可能加入隔离、催化或无限库存语义；这里只允许已核实的具体处理器和存储。
        if (handler.getClass() == NotifiableItemStackHandler.class) {
            if (((NotifiableItemStackHandler) handler).storage.getClass() == ItemStackTransfer.class) return;
        } else if (handler.getClass() == NotifiableFluidTank.class || handler.getClass() == FluidRecipeHandler.class) {
            for (var storage : fluids(handler)) {
                if (storage.getClass() != FluidStorage.class) throw unsupported(storage);
            }
            return;
        } else if (handler instanceof NotifiableEnergyContainer && handler instanceof EnergyDebitContext.ObservedContainer &&
                inheritsEnergyIo(handler.getClass())) {
            return;
        }
        throw unsupported(handler);
    }

    private static boolean inheritsEnergyIo(Class<?> type) {
        try {
            return type.getMethod("changeEnergy", long.class).getDeclaringClass() == NotifiableEnergyContainer.class &&
                    type.getMethod("getEnergyStored").getDeclaringClass() == NotifiableEnergyContainer.class &&
                    type.getMethod("getEnergyCapacity").getDeclaringClass() == NotifiableEnergyContainer.class &&
                    type.getMethod("setEnergyStored", long.class).getDeclaringClass() == NotifiableEnergyContainer.class &&
                    type.getMethod("handleRecipeInner", IO.class, com.gregtechceu.gtceu.api.recipe.GTRecipe.class,
                            List.class, String.class, boolean.class).getDeclaringClass() == NotifiableEnergyContainer.class;
        } catch (ReflectiveOperationException exception) {
            return false;
        }
    }

    static IllegalArgumentException unsupported(Object object) {
        return new IllegalArgumentException("需要真实数量 IO 适配：" + object.getClass().getName());
    }

    void move(IRecipeHandler<?> handler, IO io, Content content) {
        requireSupported(handler);
        if (handler instanceof ExportOnlyAEItemList || handler instanceof ExportOnlyAEFluidList) {
            moveViaHandler(handler, io, content);
            return;
        }
        if (handler instanceof InfinityEnergyContainer) {
            // 创造能源仓本身无上限，也不走 EnergyDebitContext；视为本 tick 已足额供能。
            if (io == IO.IN) content.content = 0L;
            return;
        }
        if (handler.getClass() == NotifiableCircuitItemStackHandler.class) {
            // 虚拟电路只参与上游模拟匹配，绝不能当作实际可扣除的物品。
            if (simulate && io == IO.IN && handler.handleRecipe(io, null, List.of(content.content), null, true) == null) {
                BatchContents.amount(ItemRecipeCapability.CAP, content, 0);
            }
            return;
        }
        if (handler instanceof NotifiableItemStackHandler items) {
            if (items.handlerIO != io) return;
            var storage = simulate ? (ItemStackTransfer) copies.computeIfAbsent(items.storage, key -> items.storage.copy()) : items.storage;
            item(storage, io, content);
        } else if (handler instanceof NotifiableEnergyContainer energy) {
            if (energy.getHandlerIO() != io) return;
            energy(energy, io, content);
        } else {
            IO handlerIo = handler instanceof NotifiableFluidTank tank ? tank.handlerIO : ((FluidRecipeHandler) handler).handlerIO;
            if (handlerIo != io) return;
            for (var original : fluids(handler)) {
                var storage = simulate ? (FluidStorage) copies.computeIfAbsent(original, key -> original.copy()) : original;
                fluid(storage, io, content);
            }
        }
    }

    /**
     * AE 只输出仓没有可直接搬运的 FluidStorage/ItemStackTransfer：它们的真实去向是 ME 网络。
     * 这里直接用上游的 handleRecipeInner 计数：它消费多少、返回多少余量，我们都按返回值回写。
     */
    @SuppressWarnings("unchecked")
    private void moveViaHandler(IRecipeHandler<?> handler, IO io, Content content) {
        if (io != IO.OUT) return;
        var cap = (RecipeCapability<Object>) handler.getCapability();
        if (BatchContents.amount(cap, content.content) <= 0) return;
        var input = new java.util.ArrayList<Object>();
        input.add(content.content);
        IRecipeHandler<Object> raw = (IRecipeHandler<Object>) handler;
        List<Object> left = raw.handleRecipeInner(io, null, input, null, simulate);
        long remaining = left == null || left.isEmpty() ? 0 : BatchContents.amount(cap, left.get(0));
        BatchContents.amount(cap, content, remaining);
    }

    private void item(ItemStackTransfer storage, IO io, Content content) {
        var cap = ItemRecipeCapability.CAP;
        var ingredient = (Ingredient) content.content;
        for (int slot = 0; slot < storage.getSlots() && BatchContents.amount(cap, content.content) > 0; slot++) {
            final int index = slot;
            long moved;
            do {
                long requested = BatchContents.amount(cap, content.content);
                if (requested == 0) break;
                moved = CountedTransfer.execute(requested, amount -> {
                    if (io == IO.IN) {
                        if (!ingredient.test(storage.getStackInSlot(index))) return 0;
                        return storage.extractItem(index, (int) Math.min(amount, Integer.MAX_VALUE), false, false).getCount();
                    }
                    var templates = ingredient.getItems();
                    if (templates.length == 0) return 0;
                    int count = (int) Math.min(amount, Integer.MAX_VALUE);
                    return count - storage.insertItem(index, templates[0].copyWithCount(count), false, false).getCount();
                }, left -> BatchContents.amount(cap, content, left));
                if (moved > 0 && !simulate) CountedTransfer.notifyRecordedChange(storage.getOnContentsChanged());
            } while (moved > 0);
        }
    }

    private void fluid(FluidStorage storage, IO io, Content content) {
        var ingredient = (FluidIngredient) content.content;
        long moved = CountedTransfer.execute(ingredient.getAmount(), amount -> {
            if (io == IO.IN) {
                if (!ingredient.test(storage.getFluid())) return 0;
                return storage.drain(0, storage.getFluid().copy(amount), false, false).getAmount();
            }
            var templates = ingredient.getStacks();
            return templates.length == 0 ? 0 : storage.fill(0, templates[0].copy(amount), false, false);
        }, ingredient::setAmount);
        if (moved > 0 && !simulate) CountedTransfer.notifyRecordedChange(storage::onContentsChanged);
    }

    private void energy(NotifiableEnergyContainer container, IO io, Content content) {
        long stored = simulate ? (long) copies.computeIfAbsent(container, key -> container.getEnergyStored()) : container.getEnergyStored();
        if (!simulate) {
            long requested = (Long) content.content;
            long available = io == IO.IN ? stored : container.getEnergyCapacity() - stored;
            long offered = Math.min(requested, available);
            if (power != null && io == IO.IN) offered = power.limit(offered);
            if (offered == 0) return;
            // 先占本 tick 的额度，通知回调或异常不能让后续批次再次使用；这不代替真实扣电。
            if (power != null && io == IO.IN) power.reserve(java.math.BigInteger.valueOf(offered));
            final long debit = offered;
            try (var context = EnergyDebitContext.open(container, stored, requested, io == IO.IN, left -> content.content = left)) {
                context.execute(() -> {
                    long delta = container.changeEnergy(io == IO.IN ? -debit : debit);
                    return io == IO.IN ? Math.negateExact(delta) : delta;
                });
            }
            return;
        }
        long moved = CountedTransfer.execute((Long) content.content, amount -> {
            long available = io == IO.IN ? stored : container.getEnergyCapacity() - stored;
            return Math.min(amount, available);
        }, left -> content.content = left);
        copies.put(container, io == IO.IN ? stored - moved : stored + moved);
    }

    private static FluidStorage[] fluids(IRecipeHandler<?> handler) {
        return handler instanceof NotifiableFluidTank tank ? tank.getStorages() : ((FluidRecipeHandler) handler).getStorages();
    }
}
