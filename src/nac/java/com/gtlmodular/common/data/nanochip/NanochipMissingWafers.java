package com.gtlmodular.common.data.nanochip;

import com.tterrag.registrate.util.entry.ItemEntry;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 晶圆与芯片分别注册，使蚀刻与切割两步使用独立物品，避免跳过中间加工环节。 */
public final class NanochipMissingWafers {

    private NanochipMissingWafers() {
    }

    public static final String NH_NOTE =
            "NH ItemList.Circuit_Wafer_{QPIC,APIC,ZPIC,YPIC,PlPIC}";

    public static final int TIER_COUNT = 5;

    public static final class Entry {
        public final String tier;
        public final String voltTier;
        public final String targetId;
        public final String chipId;
        public final String note;

        private ItemEntry<Item> itemEntry;

        private Entry(String tier, String voltTier, String targetId, String chipId, String note) {
            this.tier = tier;
            this.voltTier = voltTier;
            this.targetId = targetId;
            this.chipId = chipId;
            this.note = note;
        }

        public Item item() {
            return itemEntry == null ? null : itemEntry.get();
        }

        public ItemEntry<Item> entry() {
            return itemEntry;
        }

        public String path() {
            return targetId.substring(targetId.indexOf(':') + 1);
        }

        public ItemStack stack(int count) {
            final Item it = item();
            return it == null ? ItemStack.EMPTY : new ItemStack(it, count);
        }
    }

    private static final List<Entry> ALL = new ArrayList<>(TIER_COUNT);
    private static final Map<String, Entry> BY_TIER = new LinkedHashMap<>();
    private static final Map<String, Entry> BY_ID = new LinkedHashMap<>();

    private static final String CC_PREFIX = "cc_";

    private static void addWafer(String tier, String voltTier, String itemId, String note) {
        final Entry e = new Entry(tier, voltTier, NanochipModContext.registryId(itemId).toString(),
                NanochipModContext.registryId(tier + "_pic_chip").toString(), note);
        ALL.add(e);
        BY_TIER.put(tier, e);
        BY_ID.put(e.targetId, e);
    }

    static {
        addRows();
        // 在 Forge 方块物品注册事件前创建条目，避免静态初始化过晚漏注册。
        for (Entry e : ALL) {
            e.itemEntry = NanochipModContext.machineRegistrate().item(e.path(), Item::new).register();
        }
    }

    private static void addRows() {
        addWafer("quantum", "UHV", "quantum_pic_wafer",
                NH_NOTE + "；贴图 = gtceu lpic_wafer 母版 × GTValues.VC[UHV]=12547264(#BF74C0)");
        addWafer("atto", "UEV", "atto_pic_wafer",
                NH_NOTE + "；贴图 = gtceu lpic_wafer 母版 × GTValues.VC[UEV]=744702(#0B5CFE)");
        addWafer("zepto", "UIV", "zepto_pic_wafer",
                NH_NOTE + "；贴图 = gtceu lpic_wafer 母版 × GTValues.VC[UIV]=9522833(#914E91)");
        addWafer("yocto", "UXV", "yocto_pic_wafer",
                NH_NOTE + "；贴图 = gtceu lpic_wafer 母版 × GTValues.VC[UXV]=4753224(#488748)");
        addWafer("planck", "OpV", "planck_pic_wafer",
                NH_NOTE + "；贴图 = gtceu lpic_wafer 母版 × GTValues.VC[OpV]=9175040(#8C0000)"
                        + "；★ NH/维基对普朗克档的晶圆也**没有**来源（维基逐字「普朗克功率IC晶圆暂无获取方式」）"
                        + "⇒ 这一档的链是**补位**，非移植，如实登记");
    }

    public static void init() {
        int items = 0;
        final List<String> problems = new ArrayList<>();
        for (Entry e : ALL) {
            items++;
            if (e.itemEntry == null) {
                problems.add(e.targetId + " 未注册");
            }
            if (!e.path().endsWith("_pic_wafer")) {
                problems.add(e.targetId + " 的 id 不以 _pic_wafer 结尾");
            }
            if (e.path().startsWith(CC_PREFIX)) {
                problems.add(e.targetId + " 带 cc_ 前缀（会与 CC 层撞车）");
            }
            if (NanochipMissingItems.entryForId(e.tier + "_pic_chip") == null) {
                problems.add(e.targetId + " 找不到同档芯片 " + e.chipId + "（晶圆就没有意义了）");
            }
        }
        if (ALL.size() != TIER_COUNT || items != TIER_COUNT || !problems.isEmpty()) {
            throw new IllegalStateException("新造 PIC 晶圆表异常：应 " + TIER_COUNT + " 件，实为 "
                    + ALL.size() + " 行（" + items + " 物品）"
                    + (problems.isEmpty() ? "" : "；问题：" + problems));
        }
        NanochipModContext.logger().info("[gtlmodular] {}；全部 id 已登记进 NanochipMissingWafers 查询表（{}）",
                summary(), NH_NOTE);
    }

    public static List<Entry> all() {
        return Collections.unmodifiableList(ALL);
    }

    public static Entry entryForTier(String tier) {
        return BY_TIER.get(tier);
    }

    public static Entry entryForId(String targetId) {
        return BY_ID.get(targetId);
    }

    public static String idOf(String tier) {
        final Entry e = BY_TIER.get(tier);
        return e == null ? null : e.targetId;
    }

    public static Item itemOf(String tier) {
        final Entry e = BY_TIER.get(tier);
        return e == null ? null : e.item();
    }

    public static String summary() {
        return "新造 PIC 晶圆 " + ALL.size() + " 件（"
                + String.join(" / ", ALL.stream().map(x -> x.path()).toArray(String[]::new)) + "）";
    }
}
