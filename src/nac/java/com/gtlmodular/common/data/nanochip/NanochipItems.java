package com.gtlmodular.common.data.nanochip;

import com.tterrag.registrate.util.entry.ItemEntry;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 核心件是建造配方的身份件：模块之间只在核心上区分，配方文本里因此只剩核心与机壳。 */
public final class NanochipItems {

    private NanochipItems() {
    }

    /** 名字走本类自己的语言键，物品 id 与语言键一一对应，不落进 GT 的 item.* 命名线。 */
    public static final class Entry {
        public final String itemId;

        private ItemEntry<CoreItem> itemEntry;

        private Entry(String itemId) {
            this.itemId = itemId;
        }

        public String langKey() {
            return "gtlmodular." + itemId + ".name";
        }

        public Item item() {
            return itemEntry == null ? null : itemEntry.get();
        }
    }

    /** 核心件只是身份件，没有加工状态，按物品 id 读写就够。 */
    public static final class CoreItem extends Item {
        private final Entry entry;

        CoreItem(Properties properties, Entry entry) {
            super(properties);
            this.entry = entry;
        }

        @Override
        public Component getName(ItemStack stack) {
            return Component.translatable(entry.langKey());
        }
    }

    private static final List<Entry> ALL = new ArrayList<>(16);
    private static final Map<String, Entry> BY_ID = new LinkedHashMap<>();

    private static void add(String itemId) {
        Entry e = new Entry(itemId);
        ALL.add(e);
        BY_ID.put(itemId, e);
    }

    static {
        addRows();
        // 必须在方块物品注册事件之前创建 Registrate 条目，否则会漏注册。
        for (Entry e : ALL) {
            e.itemEntry = NanochipModContext.machineRegistrate().item(e.itemId, p -> new CoreItem(p, e)).register();
        }
    }

    private static void addRows() {
        // 通用骨架给全部模块共用，其余 15 件与模块一一对应。
        add("nanochip_module_core");
        add("nanochip_core_assembly_complex");
        add("nanochip_core_assembly_matrix");
        add("nanochip_core_board_processor");
        add("nanochip_core_etching_array");
        add("nanochip_core_smd_processor");
        add("nanochip_core_cutting_chamber");
        add("nanochip_core_encasement_wrapper");
        add("nanochip_core_splitter");
        add("nanochip_core_superconductor_splitter");
        add("nanochip_core_wire_tracer");
        add("nanochip_core_biological_coordinator");
        add("nanochip_core_optical_organizer");
        add("nanochip_core_exotic_organizer");
        add("nanochip_core_cosmic_organizer");
        add("nanochip_core_supracausal_organizer");
    }

    public static void init() {
        if (ALL.size() != 16 || BY_ID.size() != 16) {
            throw new IllegalStateException("核心件表异常：应 16 条且 id 不重复，实为 "
                    + ALL.size() + " 条 / " + BY_ID.size() + " 个 id");
        }
        for (Entry e : ALL) {
            if (e.itemEntry == null) {
                throw new IllegalStateException("核心件未注册：" + e.itemId);
            }
        }
        NanochipModContext.logger().info("[gtlmodular] 核心件 {} 件已登记（物品 id 与语言键一一对应）", ALL.size());
    }

    public static List<Entry> all() {
        return Collections.unmodifiableList(ALL);
    }

    public static Entry entryForId(String itemId) {
        return BY_ID.get(itemId);
    }

    public static Item itemForId(String itemId) {
        Entry e = BY_ID.get(itemId);
        return e == null ? null : e.item();
    }
}
