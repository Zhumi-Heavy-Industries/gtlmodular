package com.gtlmodular.common.machine.pattern.ui;

import appeng.crafting.pattern.ProcessingPatternItem;
import com.gtlmodular.GTLModular;
import com.gtlmodular.common.diag.SwallowedErrors;
import com.lowdragmc.lowdraglib.misc.FluidTransferList;
import com.lowdragmc.lowdraglib.side.fluid.FluidStack;
import com.lowdragmc.lowdraglib.side.fluid.IFluidTransfer;
import com.lowdragmc.lowdraglib.side.item.IItemTransfer;
import com.lowdragmc.lowdraglib.side.item.ItemTransferHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * 专属催化剂页的控件绑定与内容同步。
 *
 * <p>为什么不用原版菜单槽位同步：页面槽控件是展开时才建的。服务端第一次 broadcastChanges 就把当前值
 * 当成"已发送"记进 remoteSlots，而客户端那一刻还没有对应槽控件 ⇒ 包被丢弃；原版之后只在值变化时重发，
 * 于是页面一直空着，直到关掉重开界面（重开时控件在容器整包下发之前就建好了）。</p>
 *
 * <p>这里改成：槽控件只建一次、切槽只换代理指向；内容走机器 {@code @DescSynced String} 字段点到点同步，
 * 与菜单槽位表是否已经建好完全无关。</p>
 */
public final class CatalystPageSync {

    private static final String KEY_ITEMS = "items";
    private static final String KEY_FLUIDS = "fluids";
    private static final String KEY_SLOT = "slot";
    private static final int MAX_CACHE_RECIPE_COUNT = 127;

    private final CatalystSlotAccess access;
    private final int itemSlots;
    private final int fluidTanks;
    private final long tankCapacity;
    private final String logTag;

    private final ItemView itemView;
    private final FluidView[] fluidViews;
    private final FluidTransferList fluidList;
    private final byte[] cacheCountBuffer = new byte[1];
    private final IntConsumer cacheCountListener;

    private final ItemStack[] lastSentItems;
    private final FluidStack[] lastSentFluids;
    private int lastSentIndex = Integer.MIN_VALUE;
    private String lastAppliedPayload = "";
    private boolean loggedApply;
    private boolean targetInitialized;
    private int target;

    public CatalystPageSync(CatalystSlotAccess access, int itemSlots, int fluidTanks, long tankCapacity,
                            int defaultCacheCount, String logTag) {
        this.access = access;
        this.itemSlots = Math.max(1, itemSlots);
        this.fluidTanks = Math.max(1, fluidTanks);
        this.tankCapacity = tankCapacity;
        this.logTag = logTag;
        this.itemView = new ItemView();
        this.fluidViews = new FluidView[this.fluidTanks];
        List<IFluidTransfer> tanks = new ArrayList<>(this.fluidTanks);
        for (int i = 0; i < this.fluidTanks; i++) {
            this.fluidViews[i] = new FluidView(i);
            tanks.add(this.fluidViews[i]);
        }
        this.fluidList = new FluidTransferList(tanks);
        this.cacheCountBuffer[0] = (byte) clampCacheCount(defaultCacheCount);
        this.lastSentItems = new ItemStack[this.itemSlots];
        Arrays.fill(this.lastSentItems, ItemStack.EMPTY);
        this.lastSentFluids = new FluidStack[this.fluidTanks];
        Arrays.fill(this.lastSentFluids, FluidStack.empty());
        this.cacheCountListener = ignored -> access.onCatalystCacheCountChanged(target, cacheCountBuffer[0]);
    }

    /** UI 管理器要求"一个元素一个仓"：物品一支、流体列表里每罐一支。 */
    public IItemTransfer[] itemTransfers() {
        return new IItemTransfer[]{itemView};
    }

    public FluidTransferList[] fluidTransfers() {
        return new FluidTransferList[]{fluidList};
    }

    public byte[] cacheCountBuffer() {
        return cacheCountBuffer;
    }

    public IntConsumer cacheCountListener() {
        return cacheCountListener;
    }

    /** 切槽只改代理指向；服务端拿到的仍是那一槽的真仓。 */
    public void setTarget(int index) {
        int next = Math.max(0, index);
        boolean changed = !targetInitialized || next != target;
        target = next;
        itemView.setTarget(next);
        for (FluidView view : fluidViews) {
            view.setTarget(next);
        }
        if (changed) {
            targetInitialized = true;
            cacheCountBuffer[0] = (byte) clampCacheCount(access.catalystCacheCount(next));
        }
    }

    /** 服务端每 tick 调：槽号或内容变了就返回新的 SNBT，没变返回 null。 */
    public String publishIfChanged() {
        boolean changed = target != lastSentIndex;
        for (int i = 0; i < itemSlots && !changed; i++) {
            if (!sameItem(itemView.getStackInSlot(i), lastSentItems[i])) {
                changed = true;
            }
        }
        for (int i = 0; i < fluidTanks && !changed; i++) {
            FluidStack now = fluidViews[i].getFluidInTank(0);
            if (!now.isFluidEqual(lastSentFluids[i]) || now.getAmount() != lastSentFluids[i].getAmount()) {
                changed = true;
            }
        }
        if (!changed) {
            return null;
        }
        CompoundTag payload = new CompoundTag();
        payload.putInt(KEY_SLOT, target);
        ListTag items = new ListTag();
        for (int i = 0; i < itemSlots; i++) {
            ItemStack stack = itemView.getStackInSlot(i).copy();
            lastSentItems[i] = stack.copy();
            items.add(stack.serializeNBT());
        }
        payload.put(KEY_ITEMS, items);
        ListTag fluids = new ListTag();
        for (int i = 0; i < fluidTanks; i++) {
            FluidStack fluid = fluidViews[i].getFluidInTank(0).copy();
            lastSentFluids[i] = fluid.copy();
            fluids.add(fluid.saveToTag(new CompoundTag()));
        }
        payload.put(KEY_FLUIDS, fluids);
        lastSentIndex = target;
        return payload.toString();
    }

    /** 客户端每帧调：字符串变了才解析；payload 里的槽号与本侧当前页不一致就先不用（等槽号同步）。 */
    public boolean apply(String payload) {
        if (payload == null || payload.isEmpty() || payload.equals(lastAppliedPayload)) {
            return false;
        }
        try {
            CompoundTag tag = TagParser.parseTag(payload);
            if (tag.getInt(KEY_SLOT) != target) {
                return false;
            }
            lastAppliedPayload = payload;
            ListTag items = tag.getList(KEY_ITEMS, Tag.TAG_COMPOUND);
            int nonEmpty = 0;
            for (int i = 0; i < itemSlots && i < items.size(); i++) {
                ItemStack stack = ItemStack.of(items.getCompound(i));
                if (!stack.isEmpty()) {
                    nonEmpty++;
                }
                itemView.setStackInSlot(i, stack);
            }
            ListTag fluids = tag.getList(KEY_FLUIDS, Tag.TAG_COMPOUND);
            for (int i = 0; i < fluidTanks && i < fluids.size(); i++) {
                fluidViews[i].setFluidInTank(0, FluidStack.loadFromTag(fluids.getCompound(i)));
            }
            if (!loggedApply) {
                loggedApply = true;
                GTLModular.LOGGER.info("[gtlmodular] {}: 客户端收到内容同步 非空物品={}/{} 槽={}",
                        logTag, nonEmpty, itemSlots, target + 1);
            }
            return true;
        } catch (Exception invalid) {
            SwallowedErrors.report("CatalystPageSync#apply", invalid);
            return false;
        }
    }

    private static int clampCacheCount(int value) {
        return Math.max(1, Math.min(MAX_CACHE_RECIPE_COUNT, value));
    }

    /** ItemStack.matches 不比数量：往已有催化剂上再叠几个也必须算变化。 */
    private static boolean sameItem(ItemStack left, ItemStack right) {
        return ItemStack.matches(left, right) && left.getCount() == right.getCount();
    }

    private final class ItemView implements IItemTransfer {

        private int viewTarget;

        /** 客户端真仓是空壳，渲染和点击预测只能读同步下来的本地缓存。 */
        private final ItemStack[] clientCache = new ItemStack[itemSlots];

        ItemView() {
            Arrays.fill(this.clientCache, ItemStack.EMPTY);
        }

        void setTarget(int index) {
            this.viewTarget = index;
        }

        private IItemTransfer serverTransfer() {
            if (access.isRemote()) {
                return null;
            }
            return access.catalystItemTransfer(viewTarget);
        }

        @Override
        public int getSlots() {
            // 代理槽数两侧必须恒定，不能随尚未同步的本侧容量变化。
            return itemSlots;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            if (access.isRemote()) {
                return slot >= 0 && slot < clientCache.length ? clientCache[slot] : ItemStack.EMPTY;
            }
            IItemTransfer transfer = serverTransfer();
            return transfer != null ? transfer.getStackInSlot(slot) : ItemStack.EMPTY;
        }

        @Override
        public void setStackInSlot(int slot, ItemStack stack) {
            ItemStack value = stack == null ? ItemStack.EMPTY : stack;
            if (access.isRemote()) {
                if (slot >= 0 && slot < clientCache.length) {
                    clientCache[slot] = value;
                }
                return;
            }
            if (slot < 0 || slot >= itemSlots) {
                return;
            }
            IItemTransfer transfer = serverTransfer();
            if (transfer == null) {
                return;
            }
            transfer.setStackInSlot(slot, value);
            access.onCatalystItemChanged(viewTarget);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate, boolean notify) {
            if (access.isRemote()) {
                // 客户端模拟插入供槽位预测查询，不能因没有服务端真仓就一律拒绝点击。
                return simulatedInsert(slot, stack, simulate);
            }
            if (slot < 0 || slot >= itemSlots) {
                return stack;
            }
            IItemTransfer transfer = serverTransfer();
            if (transfer == null) {
                return stack;
            }
            ItemStack remainder = transfer.insertItem(slot, stack, simulate, notify);
            if (!simulate) {
                access.onCatalystItemChanged(viewTarget);
            }
            return remainder;
        }

        private ItemStack simulatedInsert(int slot, ItemStack stack, boolean simulate) {
            if (stack == null || stack.isEmpty()) {
                return ItemStack.EMPTY;
            }
            if (slot < 0 || slot >= clientCache.length || !isItemValid(slot, stack)) {
                return stack;
            }
            ItemStack cached = clientCache[slot];
            int limit = Math.min(getSlotLimit(slot), stack.getMaxStackSize());
            if (!cached.isEmpty()) {
                if (!ItemTransferHelper.canItemStacksStack(stack, cached)) {
                    return stack;
                }
                limit -= cached.getCount();
            }
            if (limit <= 0) {
                return stack;
            }
            int moved = Math.min(limit, stack.getCount());
            if (!simulate) {
                ItemStack grown = cached.isEmpty() ? stack.copy() : cached.copy();
                grown.setCount(cached.isEmpty() ? moved : cached.getCount() + moved);
                clientCache[slot] = grown;
            }
            if (moved >= stack.getCount()) {
                return ItemStack.EMPTY;
            }
            ItemStack remainder = stack.copy();
            remainder.setCount(stack.getCount() - moved);
            return remainder;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate, boolean notify) {
            if (access.isRemote()) {
                // 客户端提取预测须根据同步缓存回答，否则服务端有物品时仍会拒绝点击。
                if (slot < 0 || slot >= clientCache.length || amount <= 0) {
                    return ItemStack.EMPTY;
                }
                ItemStack cached = clientCache[slot];
                if (cached.isEmpty()) {
                    return ItemStack.EMPTY;
                }
                ItemStack taken = cached.copy();
                taken.setCount(Math.min(amount, cached.getCount()));
                return taken;
            }
            if (slot < 0 || slot >= itemSlots) {
                return ItemStack.EMPTY;
            }
            IItemTransfer transfer = serverTransfer();
            if (transfer == null) {
                return ItemStack.EMPTY;
            }
            ItemStack extracted = transfer.extractItem(slot, amount, simulate, notify);
            if (!simulate) {
                access.onCatalystItemChanged(viewTarget);
            }
            return extracted;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            // 催化剂仓与样板仓分开：处理样板放进来了会被当成催化剂算进配方输入。
            // 页收起时控件还在菜单里，这里必须拒绝，否则快捷键能把物品塞进看不见的槽。
            return access.isCatalystPageOpen()
                    && (stack == null || !(stack.getItem() instanceof ProcessingPatternItem));
        }

        @Override
        public void onContentsChanged() {
            if (!access.isRemote()) {
                access.onCatalystItemChanged(viewTarget);
            }
        }

        @Override
        public Object createSnapshot() {
            ItemStack[] copy = new ItemStack[itemSlots];
            for (int i = 0; i < copy.length; i++) {
                copy[i] = getStackInSlot(i).copy();
            }
            return copy;
        }

        @Override
        public void restoreFromSnapshot(Object snapshot) {
            if (!(snapshot instanceof ItemStack[] stacks) || stacks.length != itemSlots) {
                return;
            }
            for (int i = 0; i < stacks.length; i++) {
                setStackInSlot(i, stacks[i] == null ? ItemStack.EMPTY : stacks[i].copy());
            }
        }
    }

    /** 上游 UI 为 FluidTransferList 的每个元素建一个罐控件，因此需一罐一个代理。 */
    private final class FluidView implements IFluidTransfer {

        private final int tankIndex;

        private int viewTarget;

        /** FluidStack.empty 是共享单例，返回副本并整体替换缓存，避免原地修改污染其他空罐。 */
        private FluidStack clientFluid = FluidStack.empty();

        FluidView(int tankIndex) {
            this.tankIndex = tankIndex;
        }

        void setTarget(int index) {
            this.viewTarget = index;
        }

        private FluidTransferList serverFluids() {
            if (access.isRemote()) {
                return null;
            }
            return access.catalystFluidTransfer(viewTarget);
        }

        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            if (access.isRemote()) {
                return clientFluid.copy();
            }
            FluidTransferList fluids = serverFluids();
            return fluids != null ? fluids.getFluidInTank(tankIndex) : FluidStack.empty();
        }

        @Override
        public void setFluidInTank(int tank, FluidStack stack) {
            FluidStack value = stack == null ? FluidStack.empty() : stack.copy();
            if (access.isRemote()) {
                clientFluid = value;
                return;
            }
            FluidTransferList fluids = serverFluids();
            if (fluids == null) {
                return;
            }
            fluids.setFluidInTank(tankIndex, value);
            access.onCatalystFluidChanged(viewTarget);
        }

        @Override
        public long getTankCapacity(int tank) {
            return tankCapacity;
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return true;
        }

        @Override
        public long fill(int tank, FluidStack resource, boolean simulate, boolean notify) {
            if (access.isRemote()) {
                return 0L;
            }
            FluidTransferList fluids = serverFluids();
            if (fluids == null) {
                return 0L;
            }
            long filled = fluids.fill(tankIndex, resource, simulate, notify);
            if (!simulate) {
                access.onCatalystFluidChanged(viewTarget);
            }
            return filled;
        }

        @Override
        public boolean supportsFill(int tank) {
            return true;
        }

        @Override
        public FluidStack drain(int tank, FluidStack resource, boolean simulate, boolean notify) {
            if (access.isRemote()) {
                return FluidStack.empty();
            }
            FluidTransferList fluids = serverFluids();
            if (fluids == null) {
                return FluidStack.empty();
            }
            FluidStack drained = fluids.drain(tankIndex, resource, simulate, notify);
            if (!simulate) {
                access.onCatalystFluidChanged(viewTarget);
            }
            return drained;
        }

        @Override
        public boolean supportsDrain(int tank) {
            return true;
        }

        @Override
        public void onContentsChanged() {
            if (!access.isRemote()) {
                access.onCatalystFluidChanged(viewTarget);
            }
        }

        @Override
        public Object createSnapshot() {
            return new FluidStack[]{getFluidInTank(0).copy()};
        }

        @Override
        public void restoreFromSnapshot(Object snapshot) {
            if (!(snapshot instanceof FluidStack[] stacks) || stacks.length != 1) {
                return;
            }
            setFluidInTank(0, stacks[0] == null ? FluidStack.empty() : stacks[0].copy());
        }
    }
}
