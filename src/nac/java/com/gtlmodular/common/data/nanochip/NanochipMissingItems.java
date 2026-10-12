package com.gtlmodular.common.data.nanochip;

import com.tterrag.registrate.util.entry.ItemEntry;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 真实物品与 CC 包装件分开注册，避免配方获得链把包装件当作最终真件。 */
public final class NanochipMissingItems {

    private NanochipMissingItems() {
    }

    public enum Kind {
        ITEM,
        MATERIAL_FORM
    }

    public static final class Entry {
        public final String ccEnum;
        public final int metaId;
        public final Kind kind;
        public final String targetId;
        public final String form;
        public final String note;

        private ItemEntry<Item> itemEntry;

        private Entry(String ccEnum, int metaId, Kind kind, String targetId, String form, String note) {
            this.ccEnum = ccEnum;
            this.metaId = metaId;
            this.kind = kind;
            this.targetId = targetId;
            this.form = form;
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

        public boolean isItem() {
            return kind == Kind.ITEM;
        }
    }

    private static final List<Entry> ALL = new ArrayList<>(14);
    private static final Map<String, Entry> BY_CC = new LinkedHashMap<>();
    private static final Map<Integer, Entry> BY_META = new LinkedHashMap<>();
    private static final Map<String, Entry> BY_TARGET = new LinkedHashMap<>();

    private static final String CC_PREFIX = "cc_";

    private static void add(String ccEnum, int metaId, Kind kind, String targetId, String form, String note) {
        final Entry e = new Entry(ccEnum, metaId, kind, targetId, form, note);
        ALL.add(e);
        BY_CC.put(ccEnum, e);
        BY_META.put(metaId, e);
        BY_TARGET.put(targetId, e);
    }

    private static void addItem(String ccEnum, int metaId, String itemId, String note) {
        add(ccEnum, metaId, Kind.ITEM, NanochipModContext.registryId(itemId).toString(), "item", note);
    }

    private static void addForm(String ccEnum, int metaId, String gtId, String form, String note) {
        add(ccEnum, metaId, Kind.MATERIAL_FORM, "gtceu:" + gtId, form, note);
    }

    static {
        addRows();
        // 在方块物品注册事件前创建 Registrate 条目，避免静态初始化过晚漏注册。
        for (Entry e : ALL) {
            if (e.kind == Kind.ITEM) {
                e.itemEntry = NanochipModContext.machineRegistrate().item(e.path(), Item::new).register();
            }
        }
    }

    private static void addRows() {
        addForm("BoltChromaticGlass", 1904, "chromatic_glass_bolt", "bolt",
                "NH MaterialsElements$STANDALONE.CHRONOMATIC_GLASS.getBolt(1)（GT++ 材料，本包无）"
                        + " ⇒ 新注册 chromatic_glass + GENERATE_BOLT_SCREW");
        addForm("ScrewSixPhasedCopper", 1909, "six_phased_copper_screw", "screw",
                "NH Materials.SixPhasedCopper.getScrew(1) ⇒ 新注册 + GENERATE_BOLT_SCREW");
        addForm("PlateHexanite", 1913, "hexanite_plate", "plate",
                "NH Materials.Hexanite.getPlate(1) ⇒ 新注册 + GENERATE_PLATE");
        addForm("ScrewUniversium", 1916, "universium_screw", "screw",
                "NH Materials.Universium.getScrew(1) ⇒ 新注册 + GENERATE_BOLT_SCREW");

        addItem("CasingUEVSuperconductor", 1908, "superconductor_uev_base_item_casing",
                "NH GTOreDictUnificator.get(OrePrefixes.itemCasing, Materials.SuperconductorUEVBase, 1)；"
                        + "本包无 itemCasing 形态 ⇒ 自注册物品（贴图 = NH materialicons/NONE/casingSmall.png 中性母版）");
        addItem("CasingCreon", 1910, "creon_item_casing",
                "NH itemCasing × Materials.Creon（gtladditions 材料）；自注册物品；"
                        + "⚠️ Creon 本体是 gtladditions 的 gtceu:creon，本物品只是「同名 casing」的自造件");
        addItem("CasingBlackDwarfMatter", 1914, "black_dwarf_mtter_item_casing",
                "NH itemCasing × Materials.BlackDwarfMatter；★ 本包该材料的真实 id 是 gtceu:black_dwarf_mtter"
                        + "（GTCEu 侧拼写如此，langindex.json 实测）⇒ 物品 id 跟材料 id 对齐，写成 *_mtter_*");
        addItem("CasingEternity", 1915, "eternity_item_casing",
                "NH itemCasing × Materials.Eternity；贴图 = NH materialicons/CUSTOM/eternity/casingSmall.png（含动画 mcmeta）");

        addItem("ChipQuantumPIC", 606, "quantum_pic_chip", "NH ItemList.Circuit_Chip_QPIC；贴图 = NH processedchipquantumpic.png");
        addItem("ChipAttoPIC", 607, "atto_pic_chip", "NH ItemList.Circuit_Chip_APIC；贴图 = NH processedchipattopic.png");
        addItem("ChipZeptoPIC", 608, "zepto_pic_chip", "NH ItemList.Circuit_Chip_ZPIC；贴图 = NH processedchipzeptopic.png");
        addItem("ChipYoctoPIC", 609, "yocto_pic_chip", "NH ItemList.Circuit_Chip_YPIC；贴图 = NH processedchipyoctopic.png");
        addItem("ChipPlanckPIC", 610, "planck_pic_chip", "NH ItemList.Circuit_Chip_PlPIC；贴图 = NH processedchipplanckpic.png");
        addItem("ChipLivingCrystal", 802, "living_crystal_chip",
                "NH 真身 = ItemList.Circuit_Parts_Crystal_Chip_Wetware（湿件线路的活体晶体芯片；"
                        + "依据 _gtnh/javap_nac/notes_recipes.md:342 + _census/冗余审查_2026-09-18.md §2.1 第 7 条）；"
                        + "贴图 = NH gt.circuitcomponent/processed/processedchiplivingcrystal.png（+动画 mcmeta）"
                        + "；★ 2026-09-18 修正：原注释误记成 Circuit_Parts_Chip_Bioware（那是 metaId 406，"
                        + "本包已映射 kubejs:bioware_chip），贴图也误取了 processedlivingbiochip.png ⇒ 已纠正");

    }

    public static void init() {
        int items = 0;
        for (Entry e : ALL) {
            if (e.kind == Kind.ITEM) {
                items++;
                if (e.path().startsWith(CC_PREFIX)) {
                    throw new IllegalStateException("P1 缺件物品层 id 与 CC 层撞车：" + e.targetId);
                }
                if (e.itemEntry == null) {
                    throw new IllegalStateException("P1 缺件物品未注册：" + e.targetId);
                }
            }
        }
        if (ALL.size() != 14 || items != 10) {
            throw new IllegalStateException("P1 缺件表异常：应 14 行（10 物品 + 4 材料形态），实为 "
                    + ALL.size() + " 行（" + items + " 物品）—— "
                    + "2026-09-18 玩家裁决把 11 条改成引用包内真件、另把「特殊系列」4 件整组退役（见 addRows() javadoc），"
                    + "要改口径请同步 _census/check_p1_missing_stage.js 与 check_dup_audit.js");
        }
        NanochipModContext.logger().info("[gtlmodular] {}；全部 id 已登记进 NanochipMissingItems 查询表"
                + "（材料形态见 NanochipMissingMaterials）", summary());
    }

    public static List<Entry> all() {
        return Collections.unmodifiableList(ALL);
    }

    public static List<Entry> items() {
        final List<Entry> out = new ArrayList<>(10);
        for (Entry e : ALL) {
            if (e.kind == Kind.ITEM) {
                out.add(e);
            }
        }
        return Collections.unmodifiableList(out);
    }

    public static Entry entryForCc(String ccEnum) {
        return BY_CC.get(ccEnum);
    }

    public static Entry entryForMeta(int metaId) {
        return BY_META.get(metaId);
    }

    public static String targetIdOf(String ccEnum) {
        final Entry e = BY_CC.get(ccEnum);
        return e == null ? null : e.targetId;
    }

    public static Item itemForCc(String ccEnum) {
        final Entry e = BY_CC.get(ccEnum);
        return e == null ? null : e.item();
    }

    public static ItemStack stackForCc(String ccEnum, int count) {
        final Item it = itemForCc(ccEnum);
        return it == null ? ItemStack.EMPTY : new ItemStack(it, count);
    }

    public static Entry entryForId(String path) {
        return BY_TARGET.get(NanochipModContext.registryId(path).toString());
    }

    public static String summary() {
        int items = 0;
        int forms = 0;
        for (Entry e : ALL) {
            if (e.kind == Kind.ITEM) {
                items++;
            } else {
                forms++;
            }
        }
        return "P1 缺件物品层 " + items + " 件自注册 + " + forms + " 件材料形态（共 " + ALL.size() + " 条 CC 映射）";
    }
}
