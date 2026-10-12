package com.gtlmodular.common.machine.nanochip;

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

/**
 * 纳米芯片组装复合体的<b>虚拟真空传送带网络</b>。
 *
 * <p>原版 GTNH NAC 让玩家在 63×63 的平台上铺几百根彩色「真空传送带管道」，
 * 把 CC（Circuit Component）从控制室送到各个模块、再把 PC（Processed Component）送回组装矩阵。
 * 移植版按用户要求保留这套机制的核心（<b>颜色 = 线路、一对一、CC/PC 分级</b>），
 * 但把「铺管子」这一层去掉：颜色本身就构成通道，同色的输出仓与输入仓自动对接。
 *
 * <h3>物品形态</h3>
 * 通道里存的是<b>虚拟件</b>（不新增任何物品）：每一条记录 = 一个真实物品原型 + 数量 + 一个分级标志。
 * <ul>
 *   <li><b>CC</b>（{@code materializable = true}）：控制室的彩色输入仓每 5 秒把真实物品「封装」进来，
 *       并且<b>可以</b>被彩色输出仓还原回真实物品 —— 对应原版
 *       {@code gt.circuitcomponent.tooltip.cc.base = This item can be converted to/from its real stack equivalent}；</li>
 *   <li><b>PC</b>（{@code materializable = false}）：模块加工完的产物推回通道时打上此标记，
 *       <b>不能</b>被还原成真实物品 —— 对应原版
 *       {@code gt.circuitcomponent.tooltip.pc.base = This item can not be converted to its un-processed real stack equivalent}。</li>
 * </ul>
 * 而组装矩阵只接受 PC（原版：{@code CCs must be processed into a refined version by modules before they
 * can be assembled into a circuit}），它产出的电路按 CC 记，才能被控制室的输出仓还原成真电路。
 *
 * <p>数据全部挂在 NAC 控制器上（跟着多方块存档走），进通道的物品不再占用真实库存，
 * 所以控制室不会因为「中转站堆满」而卡住 —— 这也是原版管道系统的痛点之一。
 */
public final class NanochipVacuumNetwork {

    /** 单条目数量上限（防止数值溢出/存档膨胀；1e9 已经远超实际需求）。 */
    public static final long MAX_PER_ENTRY = 1_000_000_000L;

    /**
     * 单通道最多记录的<b>种类</b>数 = 16。
     *
     * <p>原版规则（任务书《端口、颜色与管道》原文 + {@code MTEHatchVacuumConveyor} 的
     * {@code getRowCount()/getColumnCount()} 都是 4 ⇒ 4×4 = 16 格）：
     * {@code 每个端口最多记录 16 种电路组件，端口存储或传输电路组件没有数量上限(long类型)}。
     * 移植版把「端口内的 16 格」映射成「该颜色通道最多 16 种」，数量上限仍是 long。
     */
    public static final int MAX_KINDS_PER_CHANNEL = 16;

    /** 虚拟件：真实物品原型 + 数量 + 分级标志。 */
    public static final class Part {
        private final ItemStack prototype;
        private final boolean materializable;
        private long amount;

        Part(ItemStack prototype, boolean materializable, long amount) {
            this.prototype = prototype;
            this.materializable = materializable;
            this.amount = amount;
        }

        public ItemStack prototype() {
            return prototype;
        }

        /** true = CC（可还原成真实物品）；false = PC（已加工，不可还原）。 */
        public boolean materializable() {
            return materializable;
        }

        public long amount() {
            return amount;
        }
    }

    /**
     * 每个颜色的通道再按<b>分量号</b>细分（2026-09-16 双模式路由）：
     * <pre>
     * Map&lt;颜色, Map&lt;分量号, Map&lt;虚拟件键, Part&gt;&gt;&gt;
     *                    ^^^^^^ 0 = "没连到任何同色管道"（= 简单模式的唯一池，= 老存档的池）
     * </pre>
     * <ul>
     *   <li><b>简单模式</b>（{@code vacuumRequiresPipes=false}，默认）：所有端口都用分量 0
     *       ⇒ 与 0.1.13 的"同色全局对接"逐项一致（零回归）；</li>
     *   <li><b>管道模式</b>（{@code true}）：分量号由 {@link NanochipPipeGraph} 按"同色管道连通分量"给出
     *       ⇒ 只有<b>同色且同分量</b>的端口才看得见同一批虚拟件（原版"必须铺同色管道通路"的等价物）。</li>
     * </ul>
     * 两条路共用同一份存储：切换模式不搬数据、不丢件（切回简单模式时由 {@link #flattenAll()} 并回 0 池）。
     */
    private final Map<NanochipColor, Map<Integer, Map<String, Part>>> channels = new EnumMap<>(NanochipColor.class);

    /** 分量号的归一：负数/异常值一律当 0（= 主池）。 */
    private static int comp(int component) {
        return component < 0 ? 0 : component;
    }

    public NanochipVacuumNetwork() {
        for (NanochipColor color : NanochipColor.values()) {
            channels.put(color, new java.util.TreeMap<>());
        }
    }

    /** 取（颜色, 分量）的那只池；不存在就建。 */
    private Map<String, Part> channel(NanochipColor color, int component) {
        return channels.computeIfAbsent(color, c -> new java.util.TreeMap<>())
                .computeIfAbsent(comp(component), k -> new LinkedHashMap<>());
    }

    /** 该颜色的 0 号池（= 简单模式唯一池；兼容旧调用）。 */
    private Map<String, Part> channel(NanochipColor color) {
        return channel(color, 0);
    }

    /** 虚拟件的键：物品注册名 + NBT + 分级标志。 */
    private static String keyOf(ItemStack stack, boolean materializable) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        CompoundTag tag = stack.getTag();
        return (id == null ? "minecraft:air" : id.toString())
                + '|' + (tag == null ? "" : tag.toString())
                + '|' + (materializable ? 'C' : 'P');
    }

    // ---------- 写入 / 取出 ----------

    /**
     * 清空一条颜色的通道（<b>所有分量</b>；对应原版端口界面的「直接清空销毁」）。
     *
     * @return 被清掉的虚拟件种类数
     */
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

    /**
     * 把真实物品封装进指定颜色的通道。
     *
     * @param component 分量号（简单模式传 0；管道模式传 {@link NanochipPipeGraph} 算出来的号）
     * @return 实际接收的数量（0 表示通道满了）
     */
    public long insert(NanochipColor color, int component, ItemStack stack, boolean materializable) {
        if (color == null || stack.isEmpty()) {
            return 0L;
        }
        Map<String, Part> ch = channel(color, component);
        String key = keyOf(stack, materializable);
        Part part = ch.get(key);
        if (part == null) {
            // 原版：每个端口最多记录 16 种电路组件（新的种类放不进来就整批拒绝，不半收）
            if (ch.size() >= MAX_KINDS_PER_CHANNEL) {
                return 0L;
            }
            ItemStack prototype = stack.copy();
            prototype.setCount(1);
            part = new Part(prototype, materializable, 0L);
            ch.put(key, part);
        }
        long space = MAX_PER_ENTRY - part.amount;
        if (space <= 0L) {
            return 0L;
        }
        long accepted = Math.min(space, stack.getCount());
        part.amount += accepted;
        return accepted;
    }

    /**
     * 从指定颜色的通道取出虚拟件（还原成真实物品栈）。
     *
     * @param component             分量号（同 {@link #insert}）
     * @param maxAmount             最多取多少
     * @param materializableFilter  {@code null} = 任意；{@code TRUE} = 只要 CC；{@code FALSE} = 只要 PC
     * @return 取到的物品栈（{@link ItemStack#EMPTY} 表示通道里没有符合条件的）
     */
    public ItemStack extract(NanochipColor color, int component, int maxAmount, Boolean materializableFilter) {
        if (color == null || maxAmount <= 0) {
            return ItemStack.EMPTY;
        }
        return extractFrom(channel(color, component), maxAmount, materializableFilter);
    }

    private static ItemStack extractFrom(Map<String, Part> ch, int maxAmount, Boolean materializableFilter) {
        Iterator<Map.Entry<String, Part>> it = ch.entrySet().iterator();
        while (it.hasNext()) {
            Part part = it.next().getValue();
            if (part.amount <= 0L) {
                it.remove();
                continue;
            }
            if (materializableFilter != null && part.materializable != materializableFilter) {
                continue;
            }
            int take = (int) Math.min(Math.min(part.amount, maxAmount), part.prototype.getMaxStackSize());
            if (take <= 0) {
                continue;
            }
            part.amount -= take;
            ItemStack out = part.prototype.copy();
            out.setCount(take);
            if (part.amount <= 0L) {
                it.remove();
            }
            return out;
        }
        return ItemStack.EMPTY;
    }

    // ---------- 分量维护 ----------

    /** 该颜色当前有几个非空分量（界面/自检显示用）。 */
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

    /**
     * ★ 把某颜色的<b>非 0 分量并回 0 池</b>（切回简单模式时调用）。
     *
     * <p>只搬数量、不动 {@code Part}（原型 / CC-PC 标记 / NBT 全部保留）⇒ <b>不丢件、不改变任何物品</b>；
     * 合并后 {@code <色>@<n>} 这些存档键会消失，存档回到与 0.1.13 同构的状态。
     *
     * @return 合并的条目数（0 = 本来就只有 0 池，什么都没做）
     */
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
                if (src.amount <= 0L) {
                    continue;
                }
                Part dst = main.get(pe.getKey());
                if (dst == null) {
                    // 0 池里没有同款 ⇒ 直接把这一个 Part 挪过去（原型 / CC-PC 标记 / NBT 原样，不复制、不改数量）
                    main.put(pe.getKey(), src);
                } else if (dst.materializable == src.materializable) {
                    // 同款同分级 ⇒ 数量相加（1e9 上限内不会溢出；到顶就留一点在分量里，绝不丢件）
                    long space = MAX_PER_ENTRY - dst.amount;
                    long take = Math.min(space, src.amount);
                    dst.amount += take;
                    src.amount -= take;
                }
                moved++;
            }
            e.getValue().entrySet().removeIf(en -> en.getValue().amount <= 0L);
        }
        byComp.entrySet().removeIf(en -> en.getKey() != 0 && en.getValue().isEmpty());
        return moved;
    }

    /** 全部颜色一起并回 0 池（{@link #flatten} 的批量版）。 */
    public int flattenAll() {
        int moved = 0;
        for (NanochipColor color : NanochipColor.values()) {
            moved += flatten(color);
        }
        return moved;
    }

    // ---------- 查询 ----------

    /** 该颜色通道里出现过的物品原型（分流器过滤按钮用；<b>聚合所有分量</b>）。 */
    public java.util.List<ItemStack> prototypesOf(NanochipColor color) {
        java.util.List<ItemStack> out = new java.util.ArrayList<>();
        Map<Integer, Map<String, Part>> byComp = channels.get(color);
        if (byComp == null) {
            return out;
        }
        for (Map<String, Part> ch : byComp.values()) {
            for (Part p : ch.values()) {
                if (p.amount > 0L && !out.contains(p.prototype)) {
                    out.add(p.prototype);
                }
            }
        }
        return out;
    }

    /**
     * 分流器：把虚拟件从一条颜色通道搬到另一条（<b>保留 CC/PC 分级</b>）。
     *
     * <p>这就是原版 {@code Nanopart Splitter} 的核心职能 ——
     * {@code The Splitter allows filtering by color input type, color output type, item type,
     * and supplied redstone level}。颜色筛选由 {@code from}/{@code to} 表达，
     * 物品筛选由 {@code itemFilter}（物品注册名子串）表达，红石筛选在分流器机器侧判定。
     *
     * <p><b>分量语义（2026-09-16）</b>：分流器是<b>机器</b>不是端口，没有"站在管网哪个位置"这回事
     * ⇒ 它按<b>颜色聚合所有分量</b>读写（两种模式下行为一致，也不会出现"件在分量里看不见"）。
     * 需要精确定向时，用对应分量的端口去收发。
     *
     * @return 实际搬运的数量
     */
    public long transfer(NanochipColor from, NanochipColor to, String itemFilter, int maxAmount) {
        if (from == null || to == null || from == to || maxAmount <= 0) {
            return 0L;
        }
        Map<Integer, Map<String, Part>> sourceMap = channels.get(from);
        if (sourceMap == null) {
            return 0L;
        }
        long moved = 0L;
        // 目标：优先写 0 池（沿用旧语义），0 池满则退到第一个已有分量
        for (Map<String, Part> source : sourceMap.values()) {
            if (moved >= maxAmount) {
                break;
            }
            Iterator<Map.Entry<String, Part>> it = source.entrySet().iterator();
            while (it.hasNext() && moved < maxAmount) {
                Part part = it.next().getValue();
                if (part.amount <= 0L) {
                    it.remove();
                    continue;
                }
                if (!matchesFilter(part.prototype, itemFilter)) {
                    continue;
                }
                int take = (int) Math.min(part.amount, maxAmount - moved);
                if (take <= 0) {
                    break;
                }
                ItemStack move = part.prototype.copy();
                move.setCount(take);
                long accepted = insert(to, 0, move, part.materializable);
                if (accepted <= 0L) {
                    break; // 目标通道满了
                }
                part.amount -= accepted;
                moved += accepted;
                if (part.amount <= 0L) {
                    it.remove();
                }
            }
        }
        return moved;
    }

    /** 物品过滤：空 = 不限；否则按物品注册名子串匹配（大小写不敏感）。 */
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

    /** 该颜色的虚拟件总量（<b>聚合所有分量</b>）。 */
    public long totalOf(NanochipColor color) {
        Map<Integer, Map<String, Part>> byComp = channels.get(color);
        if (byComp == null) {
            return 0L;
        }
        long sum = 0L;
        for (Map<String, Part> ch : byComp.values()) {
            for (Part p : ch.values()) {
                sum += p.amount;
            }
        }
        return sum;
    }

    /** 指定的分级标志（null = 全部）在该颜色下的总量（<b>聚合所有分量</b>）。 */
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
                if (p.materializable == materializableFilter) {
                    sum += p.amount;
                }
            }
        }
        return sum;
    }

    /** 全网络总量。 */
    public long total() {
        long sum = 0L;
        for (NanochipColor c : NanochipColor.values()) {
            sum += totalOf(c);
        }
        return sum;
    }

    /** 该颜色的明细文案（界面用，最多列 4 条；<b>聚合所有分量</b>）。 */
    public String detail(NanochipColor color) {
        StringBuilder sb = new StringBuilder();
        int shown = 0;
        Map<Integer, Map<String, Part>> byComp = channels.get(color);
        if (byComp == null) {
            return "";
        }
        for (Map<String, Part> ch : byComp.values()) {
            for (Part p : ch.values()) {
                if (p.amount <= 0L) {
                    continue;
                }
                if (shown++ >= 4) {
                    sb.append(" …");
                    return sb.toString();
                }
                if (sb.length() > 0) {
                    sb.append(" · ");
                }
                sb.append(p.prototype.getHoverName().getString())
                        .append('×').append(p.amount)
                        .append(p.materializable ? "(CC)" : "(PC)");
            }
        }
        return sb.toString();
    }

    /** 单色摘要（例如「红 32(CC) / 8(PC)」）。 */
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

    /** 全网络摘要（界面用，只列非空颜色）。 */
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

    /** 是否有任何内容（空网络不参与同步/显示）。 */
    public boolean isEmpty() {
        return total() <= 0L;
    }

    // ---------- 存档 ----------
    //
    // ★ 格式（2026-09-16 双模式路由；**向后兼容是硬要求**）：
    //     `<颜色>`      = 分量 0 的池   ← **老格式一字不改**，老存档直接读成 0 池 ✓
    //     `<颜色>@<n>`  = 分量 n 的池   ← 只有管道模式才会出现；切回简单模式时被 flatten 并掉
    //   简单模式（默认）下产出的存档与 0.1.13 **逐键同构**（不会多出任何 @n 键）。

    /** 分量键后缀分隔符（颜色名里不可能出现 '@'，`NanochipColor` 的 name() 全是 [A-Z_]）。 */
    private static final char COMP_SEP = '@';

    /** 写入 NBT（只写非空分量）。 */
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
                    if (p.amount <= 0L) {
                        continue;
                    }
                    CompoundTag entry = new CompoundTag();
                    entry.put("Item", p.prototype.save(new CompoundTag()));
                    entry.putLong("Amount", p.amount);
                    entry.putBoolean("CC", p.materializable);
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

    /** 从 NBT 读取（老键 = 分量 0；`<色>@<n>` = 分量 n）。 */
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
                    ch.put(keyOf(prototype, cc), new Part(prototype, cc, amount));
                }
            }
        }
    }
}
