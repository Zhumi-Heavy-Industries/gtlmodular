package com.gtlmodular.common.machine.requester;

import com.lowdragmc.lowdraglib.syncdata.IContentChangeAware;
import com.lowdragmc.lowdraglib.syncdata.ITagSerializable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.function.IntFunction;

/**
 * 槽位表容器：把 {@code RequesterSlot[]} 包成<b>一个</b> LDLib 可托管的字段。
 *
 * <h2>为什么必须包一层（实机崩服教训）</h2>
 * <p>最初把 {@code RequesterSlot[]} 直接标 {@code @Persisted}，进游戏放方块立刻崩：
 * <pre>
 * java.lang.IllegalStateException: Failed to create ref of slots with type:
 *     com.gtlmodular.common.machine.requester.RequesterSlot[]
 *   at com.lowdragmc.lowdraglib.syncdata.field.ManagedKey.createRef(ManagedKey.java:123)
 * </pre>
 * 原因：LDLib 的 {@code ReadonlyArrayRef#init} 要求数组元素是 {@code IManaged} 或
 * {@code IContentChangeAware}，两者都不是就直接抛
 * {@code "complex sync field must be an IContentChangeAware if not lazy"}。
 *
 * <p>修法照抄 GTCEu 自己的 {@code ExportOnlyAESlot}：让<b>容器</b>实现
 * {@link IContentChangeAware}（数组字段只是持有它的引用，不再需要逐元素托管），
 * 并用 {@link ITagSerializable} 负责整表读写。
 *
 * <p>附带好处：槽位内容我们只在自己的 setter 里改，可以精确控制"什么时候标脏"，
 * 不会因为引擎每轮把 stock 快照写进槽里就触发一次落盘。
 */
public final class RequesterSlots implements ITagSerializable<CompoundTag>, IContentChangeAware {

    private final RequesterSlot[] slots;
    private Runnable onContentsChanged = () -> {};

    public RequesterSlots(int size) {
        this.slots = new RequesterSlot[size];
        for (int i = 0; i < size; i++) {
            slots[i] = new RequesterSlot(null, 0L, 0L);
        }
    }

    public int size() {
        return slots.length;
    }

    /** 只读访问：改内容请走 {@link #mutate(int, java.util.function.Consumer)}。 */
    public RequesterSlot slot(int index) {
        return index >= 0 && index < slots.length ? slots[index] : RequesterSlot.EMPTY;
    }

    public RequesterSlot[] raw() {
        return slots;
    }

    /**
     * 修改某一格，并在真的变了时通知 LDLib 标脏。
     *
     * @param action 返回 true 表示本次修改生效
     */
    public boolean mutate(int index, IntFunction<Boolean> action) {
        if (index < 0 || index >= slots.length) return false;
        if (!Boolean.TRUE.equals(action.apply(index))) return false;
        markDirty();
        return true;
    }

    /** 静默写入（引擎刷新 stock 用）：不标脏，避免每轮轮询都触发存档。 */
    public void setStockSilently(int index, long stock) {
        if (index >= 0 && index < slots.length) {
            slots[index].setStock(stock);
        }
    }

    public int configuredCount() {
        int n = 0;
        for (RequesterSlot slot : slots) {
            if (!slot.isEmpty()) n++;
        }
        return n;
    }

    public void clearAll() {
        boolean any = false;
        for (RequesterSlot slot : slots) {
            if (!slot.isEmpty()) {
                slot.setKey(null);
                any = true;
            }
        }
        if (any) markDirty();
    }

    public void markDirty() {
        if (onContentsChanged != null) onContentsChanged.run();
    }

    // ------------------------------------------------------------------
    // LDLib 托管
    // ------------------------------------------------------------------

    @Override
    public Runnable getOnContentsChanged() {
        return onContentsChanged;
    }

    @Override
    public void setOnContentsChanged(Runnable onContentsChanged) {
        this.onContentsChanged = onContentsChanged;
    }

    // ------------------------------------------------------------------
    // 序列化：整表 <-> ListTag
    // ------------------------------------------------------------------

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.put("List", RequesterSlot.writeList(slots));
        // 标记新布局：下次读档不会再把下标当旧布局换算。
        tag.putBoolean("LayoutV2", true);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        if (tag == null) return;
        ListTag list = tag.contains("List") ? tag.getList("List", Tag.TAG_COMPOUND) : null;
        // 旧存档（0.3.479 之前）是 32 格/页的布局，读的时候按下标换算一次。
        boolean legacy = !tag.getBoolean("LayoutV2");
        RequesterSlot.readList(list, slots, legacy);
    }
}
