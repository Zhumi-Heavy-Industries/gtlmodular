package com.gtlmodular.common.machine.nanochip.vacuum;

import com.gtlmodular.common.machine.nanochip.NanochipColor;
import com.gtlmodular.common.machine.nanochip.NanochipVacuumNetwork.Part;
import static com.gtlmodular.common.machine.nanochip.NanochipVacuumNetwork.MAX_PER_ENTRY;
import static com.gtlmodular.common.machine.nanochip.NanochipVacuumNetwork.MAX_KINDS_PER_CHANNEL;
import com.gtlmodular.common.data.nanochip.NanochipModContext;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.EnumMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public final class NanochipVacuumInventory {

    @FunctionalInterface
    public interface PartFactory {
        Part create(ItemStack prototype, boolean materializable, long amount);
    }

    private final PartFactory partFactory;
    private final java.util.function.ObjLongConsumer<Part> writeAmount;

    private final Map<NanochipColor, Map<Integer, Map<String, Part>>> channels = new EnumMap<>(NanochipColor.class);

    private static int comp(int component) {
        return component < 0 ? 0 : component;
    }

    public NanochipVacuumInventory(PartFactory partFactory, java.util.function.ObjLongConsumer<Part> writeAmount) {
        this.partFactory = partFactory;
        this.writeAmount = writeAmount;
        for (NanochipColor color : NanochipColor.values()) {
            channels.put(color, new java.util.TreeMap<>());
        }
    }

    private Map<String, Part> channel(NanochipColor color, int component) {
        return channels.computeIfAbsent(color, c -> new java.util.TreeMap<>())
                .computeIfAbsent(comp(component), k -> new LinkedHashMap<>());
    }

    private Map<String, Part> channel(NanochipColor color) {
        return channel(color, 0);
    }

    private static String keyOf(ItemStack stack, boolean materializable) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        CompoundTag tag = stack.getTag();
        return (id == null ? "minecraft:air" : id.toString())
                + '|' + (tag == null ? "" : tag.toString())
                + '|' + (materializable ? 'C' : 'P');
    }

    public int clearChannel(NanochipColor color) {
        if (color == null) {
            return 0;
        }
        int kinds = 0;
        Map<Integer, Map<String, Part>> byComp = channels.computeIfAbsent(color, c -> new java.util.TreeMap<>());
        for (Map<String, Part> ch : byComp.values()) {
            kinds += ch.size();
            ch.clear();
        }
        return kinds;
    }

    public long insert(NanochipColor color, int component, ItemStack stack, boolean materializable) {
        if (color == null || stack.isEmpty()) {
            return 0L;
        }
        Map<String, Part> ch = channel(color, component);
        String key = keyOf(stack, materializable);
        Part part = ch.get(key);
        if (part == null) {
            if (ch.size() >= MAX_KINDS_PER_CHANNEL) {
                return 0L;
            }
            ItemStack prototype = stack.copy();
            prototype.setCount(1);
            part = partFactory.create(prototype, materializable, 0L);
            ch.put(key, part);
        }
        long space = MAX_PER_ENTRY - part.amount();
        if (space <= 0L) {
            return 0L;
        }
        long accepted = Math.min(space, stack.getCount());
        writeAmount.accept(part, part.amount() + accepted);
        return accepted;
    }

    public ItemStack extract(NanochipColor color, int component, int maxAmount, Boolean materializableFilter) {
        if (color == null || maxAmount <= 0) {
            return ItemStack.EMPTY;
        }
        return extractFrom(channel(color, component), maxAmount, materializableFilter);
    }

    private ItemStack extractFrom(Map<String, Part> ch, int maxAmount, Boolean materializableFilter) {
        Iterator<Map.Entry<String, Part>> it = ch.entrySet().iterator();
        while (it.hasNext()) {
            Part part = it.next().getValue();
            if (part.amount() <= 0L) {
                it.remove();
                continue;
            }
            if (materializableFilter != null && part.materializable() != materializableFilter) {
                continue;
            }
            int take = (int) Math.min(Math.min(part.amount(), maxAmount), part.prototype().getMaxStackSize());
            if (take <= 0) {
                continue;
            }
            writeAmount.accept(part, part.amount() - take);
            ItemStack out = part.prototype().copy();
            out.setCount(take);
            if (part.amount() <= 0L) {
                it.remove();
            }
            return out;
        }
        return ItemStack.EMPTY;
    }

    public int componentCount(NanochipColor color) {
        Map<Integer, Map<String, Part>> byComp = channels.get(color);
        if (byComp == null) {
            return 0;
        }
        int n = 0;
        for (Map<String, Part> ch : byComp.values()) {
            if (!ch.isEmpty()) {
                n++;
            }
        }
        return n;
    }

    public int flatten(NanochipColor color) {
        Map<Integer, Map<String, Part>> byComp = channels.get(color);
        if (byComp == null || byComp.size() <= 1) {
            return 0;
        }
        Map<String, Part> main = channel(color, 0);
        int moved = 0;
        for (Map.Entry<Integer, Map<String, Part>> e : byComp.entrySet()) {
            if (e.getKey() == 0) {
                continue;
            }
            for (Map.Entry<String, Part> pe : e.getValue().entrySet()) {
                Part src = pe.getValue();
                if (src.amount() <= 0L) {
                    continue;
                }
                Part dst = main.get(pe.getKey());
                if (dst == null) {
                    main.put(pe.getKey(), src);
                } else if (dst.materializable() == src.materializable()) {
                    // 合并达到单条上限时保留分量中的余量，避免切换物流模式丢件。
                    long space = MAX_PER_ENTRY - dst.amount();
                    long take = Math.min(space, src.amount());
                    writeAmount.accept(dst, dst.amount() + take);
                    writeAmount.accept(src, src.amount() - take);
                }
                moved++;
            }
            e.getValue().entrySet().removeIf(en -> en.getValue().amount() <= 0L);
        }
        byComp.entrySet().removeIf(en -> en.getKey() != 0 && en.getValue().isEmpty());
        return moved;
    }

    public int flattenAll() {
        int moved = 0;
        for (NanochipColor color : NanochipColor.values()) {
            moved += flatten(color);
        }
        return moved;
    }

    public java.util.List<ItemStack> prototypesOf(NanochipColor color) {
        java.util.List<ItemStack> out = new java.util.ArrayList<>();
        Map<Integer, Map<String, Part>> byComp = channels.get(color);
        if (byComp == null) {
            return out;
        }
        for (Map<String, Part> ch : byComp.values()) {
            for (Part p : ch.values()) {
                if (p.amount() > 0L && !out.contains(p.prototype())) {
                    out.add(p.prototype());
                }
            }
        }
        return out;
    }

    public long transfer(NanochipColor from, NanochipColor to, String itemFilter, int maxAmount) {
        if (from == null || to == null || from == to || maxAmount <= 0) {
            return 0L;
        }
        Map<Integer, Map<String, Part>> sourceMap = channels.get(from);
        if (sourceMap == null) {
            return 0L;
        }
        long moved = 0L;
        for (Map<String, Part> source : sourceMap.values()) {
            if (moved >= maxAmount) {
                break;
            }
            Iterator<Map.Entry<String, Part>> it = source.entrySet().iterator();
            while (it.hasNext() && moved < maxAmount) {
                Part part = it.next().getValue();
                if (part.amount() <= 0L) {
                    it.remove();
                    continue;
                }
                if (!matchesFilter(part.prototype(), itemFilter)) {
                    continue;
                }
                int take = (int) Math.min(part.amount(), maxAmount - moved);
                if (take <= 0) {
                    break;
                }
                ItemStack move = part.prototype().copy();
                move.setCount(take);
                long accepted = insert(to, 0, move, part.materializable());
                if (accepted <= 0L) {
                    break;
                }
                writeAmount.accept(part, part.amount() - accepted);
                moved += accepted;
                if (part.amount() <= 0L) {
                    it.remove();
                }
            }
        }
        return moved;
    }

    private static boolean matchesFilter(ItemStack prototype, String itemFilter) {
        if (itemFilter == null || itemFilter.isBlank()) {
            return true;
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(prototype.getItem());
        if (id == null) {
            return false;
        }
        return id.toString().toLowerCase(java.util.Locale.ROOT)
                .contains(itemFilter.trim().toLowerCase(java.util.Locale.ROOT));
    }

    public long totalOf(NanochipColor color) {
        Map<Integer, Map<String, Part>> byComp = channels.get(color);
        if (byComp == null) {
            return 0L;
        }
        long sum = 0L;
        for (Map<String, Part> ch : byComp.values()) {
            for (Part p : ch.values()) {
                sum += p.amount();
            }
        }
        return sum;
    }

    public long totalOf(NanochipColor color, Boolean materializableFilter) {
        if (materializableFilter == null) {
            return totalOf(color);
        }
        Map<Integer, Map<String, Part>> byComp = channels.get(color);
        if (byComp == null) {
            return 0L;
        }
        long sum = 0L;
        for (Map<String, Part> ch : byComp.values()) {
            for (Part p : ch.values()) {
                if (p.materializable() == materializableFilter) {
                    sum += p.amount();
                }
            }
        }
        return sum;
    }

    public long total() {
        long sum = 0L;
        for (NanochipColor c : NanochipColor.values()) {
            sum += totalOf(c);
        }
        return sum;
    }

    public String detail(NanochipColor color) {
        StringBuilder sb = new StringBuilder();
        int shown = 0;
        Map<Integer, Map<String, Part>> byComp = channels.get(color);
        if (byComp == null) {
            return "";
        }
        for (Map<String, Part> ch : byComp.values()) {
            for (Part p : ch.values()) {
                if (p.amount() <= 0L) {
                    continue;
                }
                if (shown++ >= 4) {
                    sb.append(" …");
                    return sb.toString();
                }
                if (sb.length() > 0) {
                    sb.append(" · ");
                }
                sb.append(p.prototype().getHoverName().getString())
                        .append('×').append(p.amount())
                        .append(p.materializable() ? "(CC)" : "(PC)");
            }
        }
        return sb.toString();
    }

    public String summary(NanochipColor color) {
        long cc = totalOf(color, Boolean.TRUE);
        long pc = totalOf(color, Boolean.FALSE);
        if (cc == 0L && pc == 0L) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        if (cc > 0L) {
            sb.append(cc).append(" CC");
        }
        if (pc > 0L) {
            if (sb.length() > 0) {
                sb.append(" / ");
            }
            sb.append(pc).append(" PC");
        }
        return sb.toString();
    }

    public String summaryAll() {
        StringBuilder sb = new StringBuilder();
        for (NanochipColor color : NanochipColor.values()) {
            String s = summary(color);
            if (s.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append("  ");
            }
            sb.append(color.nameZh()).append(' ').append(s);
        }
        return sb.length() == 0 ? "（空）" : sb.toString();
    }

    public boolean isEmpty() {
        return total() <= 0L;
    }

    /** 颜色枚举名不含 @，可用它区分颜色与分量后缀，同时兼容没有后缀的旧键。 */
    private static final char COMP_SEP = '@';

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        for (NanochipColor color : NanochipColor.values()) {
            Map<Integer, Map<String, Part>> byComp = channels.get(color);
            if (byComp == null) {
                continue;
            }
            for (Map.Entry<Integer, Map<String, Part>> e : byComp.entrySet()) {
                ListTag list = new ListTag();
                for (Part p : e.getValue().values()) {
                    if (p.amount() <= 0L) {
                        continue;
                    }
                    CompoundTag entry = new CompoundTag();
                    entry.put("Item", p.prototype().save(new CompoundTag()));
                    entry.putLong("Amount", p.amount());
                    entry.putBoolean("CC", p.materializable());
                    list.add(entry);
                }
                if (list.isEmpty()) {
                    continue;
                }
                tag.put(e.getKey() == 0 ? color.name() : color.name() + COMP_SEP + e.getKey(), list);
            }
        }
        return tag;
    }

    public void load(CompoundTag tag) {
        for (Map<Integer, Map<String, Part>> byComp : channels.values()) {
            byComp.clear();
        }
        for (NanochipColor color : NanochipColor.values()) {
            for (String key : new java.util.ArrayList<>(tag.getAllKeys())) {
                String head = key;
                int component = 0;
                int at = key.indexOf(COMP_SEP);
                if (at >= 0) {
                    head = key.substring(0, at);
                    try {
                        component = Math.max(0, Integer.parseInt(key.substring(at + 1)));
                    } catch (NumberFormatException ex) {
                        NanochipModContext.logger().warn(
                                "[gtlmodular] 真空网络存档里出现非法分量键 {}（已忽略）", key);
                        continue;
                    }
                }
                if (!head.equals(color.name())) {
                    continue;
                }
                ListTag list = tag.getList(key, CompoundTag.TAG_COMPOUND);
                Map<String, Part> ch = channel(color, component);
                for (int i = 0; i < list.size(); i++) {
                    CompoundTag entry = list.getCompound(i);
                    ItemStack prototype = ItemStack.of(entry.getCompound("Item"));
                    if (prototype.isEmpty()) {
                        continue;
                    }
                    long amount = entry.getLong("Amount");
                    boolean cc = entry.getBoolean("CC");
                    ch.put(keyOf(prototype, cc), partFactory.create(prototype, cc, amount));
                }
            }
        }
    }
}
