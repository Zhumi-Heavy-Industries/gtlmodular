package com.gtlmodular.common.machine.requester;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import com.lowdragmc.lowdraglib.syncdata.ITagSerializable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

/**
 * 补货牌的<b>配置槽数据模型</b>：一个格子只放一个 key，但那个 key 可以是<b>物品也可以是流体</b>
 * （与 ME 输入总成同语义 —— 拖物品进去就变物品，拖流体进去就变流体，互相覆盖）。
 *
 * <h2>为什么不用 GTCEu 的 {@code ExportOnlyAEItemList/AEFluidList}</h2>
 * <p>那两个表把"物品槽"和"流体槽"拆成两个固定数组（物品 0..N、流体偏移 N..），
 * 一个下标只能是一种类型。本需求是<b>每格两用</b>，所以自己存 {@link AEKey}：
 * <ul>
 *   <li>{@code key}：物品或流体，{@code null} = 空槽；</li>
 *   <li>{@code target}：目标量（库存要维持到的数量）；</li>
 *   <li>{@code batch}：单次补货批量，0 = 用默认值；</li>
 *   <li>{@code stock}：界面显示用的"网络现有量"快照，不持久化意义（也存，便于重进界面立刻有数）。</li>
 * </ul>
 *
 * <p>序列化用 AE2 官方的 {@link AEKey#toTagGeneric()} / {@link AEKey#fromTagGeneric(CompoundTag)}，
 * 物品与流体统一处理，不需要区分。
 */
public final class RequesterSlot implements ITagSerializable<CompoundTag> {

    /** 空槽（不可变，避免共享可变对象被误改）。 */
    public static final RequesterSlot EMPTY = new RequesterSlot(null, 0L, 0L);

    /** 目标量上限：9.2e18（long 能装下的十进制最大值附近，够 "无限" 用）。 */
    public static final long MAX_AMOUNT = 9_200_000_000_000_000_000L;

    /** 一桶 = 1000 mB，用于流体数量的单位显示。 */
    public static final long BUCKET_VOLUME = 1000L;

    @Nullable
    private AEKey key;
    private long target;
    private long batch;
    private long stock;

    public RequesterSlot(@Nullable AEKey key, long target, long batch) {
        this.key = key;
        this.target = clamp(target);
        this.batch = clamp(batch);
    }

    public static RequesterSlot of(@Nullable GenericStack stack) {
        if (stack == null) return EMPTY;
        return new RequesterSlot(stack.what(), stack.amount(), 0L);
    }

    public static long clamp(long value) {
        if (value <= 0L) return 0L;
        return Math.min(value, MAX_AMOUNT);
    }

    @Nullable
    public AEKey key() {
        return key;
    }

    public boolean isEmpty() {
        return key == null;
    }

    public long target() {
        return target;
    }

    public long batch() {
        return batch;
    }

    public long stock() {
        return stock;
    }

    // ------------------------------------------------------------------
    // 变更（都返回"是否真的变了"，供 markDirty 判断）
    // ------------------------------------------------------------------

    /** 设置/替换 key。换 key 时保留原目标量（方便同类替换），换类型则重置批量。 */
    public boolean setKey(@Nullable AEKey newKey) {
        if (newKey == null) {
            boolean changed = key != null;
            key = null;
            target = 0L;
            batch = 0L;
            stock = 0L;
            return changed;
        }
        if (newKey.equals(key)) return false;
        boolean typeChanged = key != null && !key.getClass().equals(newKey.getClass());
        key = newKey;
        if (typeChanged) batch = 0L;
        stock = 0L;
        return true;
    }

    /**
     * 拖入一个 {@link GenericStack}：设置 key，并把目标量设为拖入数量
     * （与 GTCEu 的配置槽一致：拖一个 64 的堆就填 64）。
     */
    public boolean accept(@Nullable GenericStack stack) {
        if (stack == null) return setKey(null);
        boolean changed = setKey(stack.what());
        if (target != stack.amount()) {
            target = clamp(stack.amount());
            changed = true;
        }
        return changed;
    }

    public boolean setTarget(long value) {
        long next = clamp(value);
        if (next == target) return false;
        target = next;
        return true;
    }

    public boolean setBatch(long value) {
        long next = clamp(value);
        if (next == batch) return false;
        batch = next;
        return true;
    }

    public boolean setStock(long value) {
        long next = Math.max(0L, Math.min(value, MAX_AMOUNT));
        if (next == stock) return false;
        stock = next;
        return true;
    }

    /** 该格当前应使用的批量（未设置就用调用方给的默认值）。 */
    public long effectiveBatch(int fallback) {
        return batch > 0L ? batch : Math.max(1, fallback);
    }

    public GenericStack configStack() {
        return key == null ? null : new GenericStack(key, target);
    }

    public GenericStack stockStack() {
        return key == null || stock <= 0L ? null : new GenericStack(key, stock);
    }

    /** 用于同步比对的不可变快照（stock 也参与，因为界面要显示它）。 */
    public RequesterSlot snapshot() {
        return copy();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RequesterSlot other)) return false;
        if (target != other.target || batch != other.batch || stock != other.stock) return false;
        if (key == null) return other.key == null;
        return key.equals(other.key);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(key, target, batch, stock);
    }

    public RequesterSlot copy() {
        RequesterSlot copy = new RequesterSlot(key, target, batch);
        copy.stock = stock;
        return copy;
    }

    // ------------------------------------------------------------------
    // 序列化
    // ------------------------------------------------------------------

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        if (key != null) {
            tag.put("Key", key.toTagGeneric());
            tag.putLong("Target", target);
            tag.putLong("Batch", batch);
            tag.putLong("Stock", stock);
        }
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        if (tag == null || tag.isEmpty()) {
            key = null;
            target = 0L;
            batch = 0L;
            stock = 0L;
            return;
        }
        AEKey parsed = tag.contains("Key") ? AEKey.fromTagGeneric(tag.getCompound("Key")) : null;
        key = parsed;
        target = clamp(tag.getLong("Target"));
        batch = clamp(tag.getLong("Batch"));
        stock = Math.max(0L, tag.getLong("Stock"));
    }

    /**
     * 整张槽位表 ⇄ NBT。
     *
     * <p>用 ListTag 而不是 {@code Slot_i} 散键：格数会随设计变化（现在是 40），
     * 散键每次扩容都要改键名解析，列表天然自适应。
     */
    public static ListTag writeList(RequesterSlot[] slots) {
        ListTag list = new ListTag();
        for (int i = 0; i < slots.length; i++) {
            RequesterSlot slot = slots[i];
            CompoundTag tag = slot == null ? new CompoundTag() : slot.serializeNBT();
            tag.putInt("Slot", i);
            list.add(tag);
        }
        return list;
    }

    public static void readList(ListTag list, RequesterSlot[] slots, boolean legacyLayout) {
        for (int i = 0; i < slots.length; i++) {
            slots[i] = new RequesterSlot(null, 0L, 0L);
        }
        if (list == null) return;
        for (int i = 0; i < list.size(); i++) {
            Tag element = list.get(i);
            if (!(element instanceof CompoundTag tag)) continue;
            int index = tag.getInt("Slot");
            int mapped = legacyLayout ? mapLegacyIndex(index) : index;
            if (mapped < 0 || mapped >= slots.length) continue;
            RequesterSlot slot = new RequesterSlot(null, 0L, 0L);
            slot.deserializeNBT(tag);
            slots[mapped] = slot;
        }
    }

    /**
     * 旧布局 → 新布局的下标换算。
     *
     * <p>0.3.479 的布局是「每页 4 行 × 8 列 = 32 格，共 128 格」；
     * 现在照抄 ME 输入总成，改成「每页 2 行 × 8 列 = 16 格，共 64 格」。
     *
     * <p><b>按下标顺序压缩</b>（而不是按页保留）：玩家配格子通常是从第 0 格往后填，
     * 顺序压缩能保住前 64 格已经配好的内容；只丢弃 64 号以后的格子。
     * 若按页保留（每旧页只留前 2 行），会丢掉 16~31、48~63、80~95、112~127 这四整段，
     * 对“填满前两页”的玩家反而更差。
     */
    private static int mapLegacyIndex(int oldIndex) {
        return oldIndex >= 0 && oldIndex < MAX_LEGACY_SLOT_COUNT ? oldIndex : -1;
    }

    /** 新布局的槽位总数（与 {@code RequesterMachine.SLOT_COUNT} 一致）。 */
    static final int MAX_LEGACY_SLOT_COUNT = 64;
}
