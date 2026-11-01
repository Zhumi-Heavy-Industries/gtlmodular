package com.gtlmodular.common.data.nanochip;

import com.gtlmodular.GTLModular;
import com.gtlmodular.common.data.GTLModularMachines;
import com.tterrag.registrate.util.entry.ItemEntry;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * NAC（纳米芯片组装复合体）缺件 —— <b>5 件新造 PIC 晶圆</b>（量子 / 阿米 / 仄米 / 幺米 / 普朗克）。
 *
 * <h2>为什么新造这 5 件（队长 2026-09-19 批准）</h2>
 * 上一轮「全量晶圆普查」（{@code _census/孤儿隐藏与晶圆普查_队员.md} §B3 + {@code _census/out/wafer_census.md}）
 * 实证本包**没有**这 5 档晶圆（严格判据 9 个检索面 0 命中）⇒ 上一轮只能把 NH 的两步链
 * （{@code 母晶圆 → 同级晶圆 → 芯片}）压成**一步**（{@code 母晶圆 → 芯片}）。
 * 队长 2026-09-19 原话「晶圆你没有找到吗？那就新造还原吧」⇒ 本类新注册这 5 件，把两步链还原。
 *
 * <h2>命名 / 语言键 / 贴图风格（照本包既有范式，不是自创一套）</h2>
 * <ul>
 *   <li><b>id</b>：{@code gtlmodular:<档位>_pic_wafer} —— 与 {@link NanochipMissingItems} 里那 5 件
 *       {@code gtlmodular:<档位>_pic_chip}（量子功率IC芯片 … 普朗克功率IC芯片，CC 606..610）**逐档对齐**，
 *       晶圆与芯片只差最后一段 {@code _wafer}/{@code _chip}。
 *       同族参照物：GTCEu 原生 {@code gtceu:{lpic,ulpic,mpic,hpic,uhpic}_wafer} 与 kubejs
 *       {@code kubejs:{nm,pm,fm}_wafer}（{@code _census/out/wafer_census.md} 第 4/6/7/16/19/42/47/51 行）。</li>
 *   <li><b>中文名</b>：{@code item.gtlmodular.<path>} = 「量子功率IC晶圆」…「普朗克功率IC晶圆」
 *       —— 与同档芯片的「量子功率IC芯片」只差末字，逐条写进
 *       {@code assets/gtlmodular/lang/zh_cn.json} 与 {@code en_us.json}（英文镜像 = {@code Quantum Power IC Wafer} …）。</li>
 *   <li><b>贴图</b>：{@code assets/gtlmodular/textures/item/wafer/<path>.png}
 *       —— 母版取 GTCEu 同族入口档 {@code assets/gtceu/textures/item/lpic_wafer.png}，
 *       按 GTCEu 电压档标记色 {@code GTValues.VC[tier]} 重着色（alpha 逐像素沿用母版）。
 *       生成器（可复跑 + {@code --check}）：{@code _census/gen_wafer_textures_20260919.js}；
 *       逐张 sha256 见 {@code _census/新造晶圆还原两步链_队员.md}。
 *       <b>不留空图</b>：生成器硬断言「不透明像素 ≥ 100」「alpha 与母版逐像素一致」「5 张 sha256 互不相同」。</li>
 *   <li><b>模型</b>：{@code assets/gtlmodular/models/item/<path>.json}（{@code item/generated} + layer0）。</li>
 * </ul>
 *
 * <h2>为什么**另开一个类**，不塞进 {@link NanochipMissingItems}</h2>
 * {@link NanochipMissingItems} 的 14 行（10 物品 + 4 材料形态）与 10 件物品数是**被两处体检钉死**的
 * （{@code NanochipMissingItems.init()} 的运行时断言 + {@code _census/check_p1_missing_stage.js} 的
 * {@code BASE.rows=14 / BASE.items=10}、以及它的 C3「10 件物品各有模型、贴图在 {@code item/missing/}」）。
 * 晶圆不是「CC 缺件映射」而是**新造物品**，硬塞进去会把那两处体检改红/被迫放松口径 ——
 * 本类因此独立注册、独立断言，**一个字都不动**缺件表。
 *
 * <h2>本类的硬断言（{@link #init()}）</h2>
 * <ul>
 *   <li>恰好 {@link #TIER_COUNT} 件，且每件都真的注册成功（{@code itemEntry != null}）；</li>
 *   <li>每件 id 都以 {@code _pic_wafer} 结尾、都不带 {@code cc_} 前缀（与 CC 层零重叠）；</li>
 *   <li>每档都能在 {@link NanochipMissingItems} 里找到**同档**的 {@code <档位>_pic_chip}
 *       —— 晶圆是为芯片服务而造的，芯片没了它就没有意义（漏一件立刻抛异常）。</li>
 * </ul>
 *
 * <p>两步链配方见 {@code data/gtlmodular/recipes/missing_acquisition/}
 * （第一步 {@code pic_wafer_<档位>.json} = 激光蚀刻机 母晶圆+透镜 → 晶圆；
 * 第二步 {@code pic_chip_<档位>.json} = 切割机 晶圆 → 芯片）；
 * 守卫体检 = {@code _census/check_missing_acquisition_chain.js}（W1..W4 专钉这 5 件）。
 */
public final class NanochipMissingWafers {

    private NanochipMissingWafers() {
    }

    /** NH 真身（{@code ItemList.Circuit_Wafer_*}）—— 只作备忘，不参与任何逻辑。 */
    public static final String NH_NOTE =
            "NH ItemList.Circuit_Wafer_{QPIC,APIC,ZPIC,YPIC,PlPIC}"
                    + "（_census/_nh_avail/nh_ItemList_javap.txt:3635/3653/3657/3661/3665 逐字）";

    /** 档位数（硬断言，改口径请连 javadoc 一起改）。 */
    public static final int TIER_COUNT = 5;

    /** 一条「档位 → 新造晶圆」的记录。 */
    public static final class Entry {
        /** 档位词（{@code quantum} / {@code atto} / {@code zepto} / {@code yocto} / {@code planck}）。 */
        public final String tier;
        /** GTCEu 电压档名（贴图档位色的来源；只影响外观）。 */
        public final String voltTier;
        /** 完整 id（{@code gtlmodular:<tier>_pic_wafer}）。 */
        public final String targetId;
        /** 它与哪一件芯片配对（{@code gtlmodular:<tier>_pic_chip}）。 */
        public final String chipId;
        /** 备忘：NH 原物 / 贴图来源。 */
        public final String note;

        private ItemEntry<Item> itemEntry;

        private Entry(String tier, String voltTier, String targetId, String chipId, String note) {
            this.tier = tier;
            this.voltTier = voltTier;
            this.targetId = targetId;
            this.chipId = chipId;
            this.note = note;
        }

        /** 本模组物品（注册后可用）。 */
        public Item item() {
            return itemEntry == null ? null : itemEntry.get();
        }

        /** 本模组物品的 {@code ItemEntry}。 */
        public ItemEntry<Item> entry() {
            return itemEntry;
        }

        /** 物品 id 的路径部分（去命名空间）。 */
        public String path() {
            return targetId.substring(targetId.indexOf(':') + 1);
        }

        /** 物品栈。 */
        public ItemStack stack(int count) {
            final Item it = item();
            return it == null ? ItemStack.EMPTY : new ItemStack(it, count);
        }
    }

    // ---------------------------------------------------------------- 表

    private static final List<Entry> ALL = new ArrayList<>(TIER_COUNT);
    private static final Map<String, Entry> BY_TIER = new LinkedHashMap<>();
    private static final Map<String, Entry> BY_ID = new LinkedHashMap<>();

    /** 与 CC 层/缺件物品层区分的硬前缀检查（见 {@link #init()}）。 */
    private static final String CC_PREFIX = "cc_";

    /**
     * 加一行（{@code itemId} **逐字写死**，与 {@link NanochipMissingItems} 的 `addItem(...)` 同款：
     * 表里的 id 必须是**可被静态审计的字符串字面量**，不许靠拼接 —— 体检
     * `_census/check_missing_acquisition_chain.js` 的 W3 会逐档现读这些字面量）。
     */
    private static void addWafer(String tier, String voltTier, String itemId, String note) {
        final Entry e = new Entry(tier, voltTier, "gtlmodular:" + itemId, "gtlmodular:" + tier + "_pic_chip", note);
        ALL.add(e);
        BY_TIER.put(tier, e);
        BY_ID.put(e.targetId, e);
    }

    static {
        addRows();
        // 与 NanochipMissingItems 同款做法：静态块里建 Registrate 条目，
        // 由 GTLModularMachines.initBlocks()（★ 必须在 Forge 的 RegisterEvent<Item> 之前）触发类加载。
        for (Entry e : ALL) {
            e.itemEntry = GTLModularMachines.REGISTRATE.item(e.path(), Item::new).register();
        }
    }

    /**
     * 5 行全表（顺序 = 档位升序，与 CC 606..610 的芯片阶梯同向）。
     *
     * <p>贴图档位色（{@code GTValues.VC[tier]}，javap 第一手读自整合包的
     * {@code gtceu-1.20.1-1.4.4.jar}；复算脚本 {@code _census/_dump_gtceu_tier_tables.js}）：
     * 起点 UHV(9) 逐档升到 OpV(13)。括号里给**十进制原值**，免得手抄十六进制出岔子。
     * 起点依据：GTCEu 原生 PIC 阶梯最高只到 LuV 档（{@code engrave_ulpic_*} / {@code cut_uhpic}
     * 的 EUt 是 {@code VA[5]=7680} / {@code VA[6]=30720}），我们的 5 件芯片都在它之上；UHV 又是我方电压口径的上限档。
     */
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

    // ---------------------------------------------------------------- 生命周期

    /**
     * 强制类加载（接线用；注册已在静态块完成，重复调用无副作用）。
     *
     * <p>★ 必须在 {@code GTLModularMachines.initBlocks()} 里、Forge 的 {@code RegisterEvent<Item>}
     * <b>之前</b>调用（与 {@link NanochipMissingItems#init()} 同一个位置与同一个理由）。
     */
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
                    + (problems.isEmpty() ? "" : "；问题：" + problems)
                    + " —— 要改口径请同步 _census/check_missing_acquisition_chain.js 的 WAFER_SPEC");
        }
        GTLModular.LOGGER.info("[gtlmodular] {}；全部 id 已登记进 NanochipMissingWafers 查询表（{}）",
                summary(), NH_NOTE);
    }

    // ---------------------------------------------------------------- 查询（供配方层 / 自检）

    /** 5 行全表（只读，档位升序）。 */
    public static List<Entry> all() {
        return Collections.unmodifiableList(ALL);
    }

    /** 档位词 → 记录。 */
    public static Entry entryForTier(String tier) {
        return BY_TIER.get(tier);
    }

    /** 完整 id → 记录。 */
    public static Entry entryForId(String targetId) {
        return BY_ID.get(targetId);
    }

    /** 档位词 → 完整 id；查不到返回 {@code null}。 */
    public static String idOf(String tier) {
        final Entry e = BY_TIER.get(tier);
        return e == null ? null : e.targetId;
    }

    /** 档位词 → 物品；查不到返回 {@code null}。 */
    public static Item itemOf(String tier) {
        final Entry e = BY_TIER.get(tier);
        return e == null ? null : e.item();
    }

    /** 本类的自检：进世界时打一条日志用的快照。 */
    public static String summary() {
        return "新造 PIC 晶圆 " + ALL.size() + " 件（"
                + String.join(" / ", ALL.stream().map(x -> x.path()).toArray(String[]::new)) + "）";
    }
}
