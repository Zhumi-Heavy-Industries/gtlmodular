package com.gtlmodular.common.data.nanochip;

import com.gtlmodular.common.data.GTLModularMachines;
import com.tterrag.registrate.util.entry.ItemEntry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * NAC（纳米芯片组装复合体）的 <b>CircuitComponent 物品层</b> —— NH 的 166 个
 * {@code gregtech.common.tileentities.machines.multi.nanochip.util.CircuitComponent} 枚举，
 * 在本包各落地为一件<b>真实物品</b>（NH 里它们是 {@code CircuitComponentFakeItem} + damage 的假栈）。
 *
 * <h2>权威依据（全部现读，脚本可复算）</h2>
 * <ul>
 *   <li>{@code _gtnh/dump3/CircuitComponent.txt}（javap 全量）→ 176 条
 *       (枚举名, metaId, nameKey, isProcessed, iconString, realComponent, calibration)；
 *       解析器 {@code _census/_parse_nac_cc.js}，结论表 {@code _census/nac_cc_items.json}。</li>
 *   <li>{@code _gtnh/javap_nac/CircuitComponentFakeItem.txt} → 显示名语义：
 *       未处理 = {@code gt.circuitcomponent.base}（"Packaged %s"）配真实物品名；
 *       已处理 = {@code nameKey} 的翻译原文。</li>
 *   <li>贴图：已处理件逐字节拷自 NH
 *       {@code assets/gregtech/textures/items/gt.circuitcomponent/processed/<名小写>.png}；
 *       未处理件 = 真实物品贴图 + NH {@code circuit_component_overlay.png} 叠加。</li>
 * </ul>
 *
 * <h2>硬性约定</h2>
 * <p>物品 id = {@code gtlmodular:cc_<snake_case(枚举名)>}，snake_case 为**缩写感知**规则（见 {@link #snake(String)}），
 * 连续大写被当作缩写整体保留（{@code AdvSMDResistor → adv_smd_resistor}、{@code ChipNanoCPU → chip_nano_cpu}）。
 * metaId 只作为<b>本包内部索引</b>保留（NH 的假栈靠 damage 区分），方便后续模块配方/校准表按 NH 的
 * metaId 直接查表：{@link #itemFor(int)} / {@link #stack(int, int)} / {@link #metaToName()}。
 *
 * <p><b>本类不含任何配方与机器逻辑</b>（那是队长集成时的事）。
 * 需要的一行接线：{@code GTLModularMachines.initBlocks()} 里 {@code NanochipBlocks.init();} 之后调用
 * {@link #init()}。
 *
 * <p>本文件由 {@code _census/gen_nac_cc_items.js} 生成；改数据请改生成器的字典（不要手改这里的 366 行）。
 * <p>★ 2026-09-19 例外：{@code ProcessedChipPlanckPIC}(710) 一行由**队长指示的残留清理**注销，
 * 注销注释（墓碑）由生成器的 {@code RETIRED[].javaNote} 逐字复现（见 {@link #addRows()} 与 {@code _census/残留物品清理_队员.md}）。
 * <p>★ 2026-09-21 例外：{@code WireSpacetime}(4) / {@code ProcessedWireSpacetime}(104) 两行由
 * **队长拍板退役「时空导线」链**注销（材料 OpV 超 NAC 最高档 UXV + 产物孤儿），
 * 退役登记见 {@code _census/nac_cc_items.json} 的 {@code retired[]} 与 {@code _census/check_retired_items.js}。
 */
public final class NanochipCircuitComponents {

    private NanochipCircuitComponents() {}

    // ---------------------------------------------------------------- 数据模型

    /** 一件 CC 的全部权威字段（各字段出处见 {@code _census/nac_cc_items.json} 的 {@code evidence}）。 */
    public static final class Entry {
        /** NH 枚举常量名，如 {@code ProcessedWireNiobiumTitanium}。 */
        public final String enumName;
        /** NH 的 metaId（假栈的 damage）。 */
        public final int metaId;
        /** NH 的 {@code isProcessed}。 */
        public final boolean processed;
        /** NH 的 {@code nameKey}（如 {@code gt.circuitcomponent.processed.wirenbti}）。 */
        public final String nameKey;
        /** 本包物品 id 的路径部分（{@code cc_xxx}）。 */
        public final String itemId;
        /** 真实物品引用：{@code item:<gtceu 物品 id>} 或 {@code mat:<gtceu 材质 id>:<tagprefix 键>}；null = 拿不到。 */
        public final String realRef;
        /** NH 的校准族（{@code CircuitCalibration}），无则 null。 */
        public final String calibration;

        private ItemEntry<CcItem> entry;

        Entry(String enumName, int metaId, boolean processed, String nameKey, String itemId, String realRef, String calibration) {
            this.enumName = enumName;
            this.metaId = metaId;
            this.processed = processed;
            this.nameKey = nameKey;
            this.itemId = itemId;
            this.realRef = realRef;
            this.calibration = calibration;
        }

        /** 本包物品（注册后可用）。 */
        public Item item() { return entry == null ? null : entry.get(); }

        /** 本包物品 id 的完整资源位置。 */
        public ResourceLocation id() { return new ResourceLocation("gtlmodular", itemId); }

        /** 显示名 lang 键（值就是最终显示名，含 "Packaged ..."/NH 原文）。 */
        public String langKey() { return "gtlmodular.cc." + itemId + ".name"; }

        /** 严格复刻 NH 的 {@code CircuitComponentFakeItem.func_77653_i}：未处理 = "Packaged <真实物品名>"。 */
        public Component displayName() {
            if (processed || realRef == null) {
                return Component.translatable(langKey());
            }
            Component real = realName();
            return real == null ? Component.translatable(langKey())
                    : Component.translatable("gt.circuitcomponent.base", real);
        }

        /** 真实物品显示名（NH 里是 {@code realComponent.get().getDisplayName()}；1.20.1 侧用 GTCEu 注册名/lange 键组合）。 */
        private Component realName() {
            if (realRef.startsWith("item:")) {
                Item it = ForgeRegistries.ITEMS.getValue(new ResourceLocation("gtceu", realRef.substring(5)));
                return it == null ? null : new ItemStack(it).getHoverName();
            }
            // ★ 波2 自造段：非 gtceu 命名空间的物品引用（如 kubejs 的族贴片/电路板/芯片）
            if (realRef.startsWith("itemns:")) {
                String[] p2 = realRef.substring(7).split(":", 2);
                Item it2 = ForgeRegistries.ITEMS.getValue(new ResourceLocation(p2[0], p2[1]));
                return it2 == null ? null : new ItemStack(it2).getHoverName();
            }
            String[] parts = realRef.substring(4).split(":", 2);
            return Component.translatable("tagprefix." + parts[1], Component.translatable("material.gtceu." + parts[0]));
        }
    }

    /** CC 物品：唯一覆写就是显示名（+ tooltip），行为与普通物品一致。 */
    public static final class CcItem extends Item {
        private final Entry entry;

        public CcItem(Properties properties, Entry entry) {
            super(properties);
            this.entry = entry;
        }

        public Entry circuitComponent() { return entry; }

        @Override
        public Component getName(ItemStack stack) { return entry.displayName(); }

        @Override
        public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
            // 语义照抄 NH CircuitComponentFakeItem.func_77624_a（base + cc.base / pc.base）
            tooltip.add(Component.translatable("gtlmodular.cc.tooltip.base"));
            tooltip.add(Component.translatable(entry.processed
                    ? "gtlmodular.cc.tooltip.pc.base" : "gtlmodular.cc.tooltip.cc.base"));
            // ★ 2026-09-18 玩家问「真的能生效吗」——补一行**怎么用**（原 tooltip 只说能/不能，没说前置条件：
            //   仓必须染色、封装在控制室输出侧每 5 秒一次、还原要用输入侧的仓）。
            tooltip.add(Component.translatable(entry.processed
                    ? "gtlmodular.cc.tooltip.pc.howto" : "gtlmodular.cc.tooltip.cc.howto"));
            if (flag.isAdvanced()) {
                tooltip.add(Component.literal("\u00a78" + entry.itemId + " / meta " + entry.metaId
                        + (entry.calibration == null ? "" : " / " + entry.calibration)));
            }
        }
    }

    // ---------------------------------------------------------------- 表
    /**
     * 按 metaId 升序的 366 条（NH 原始 176 − 退役 10 = 166；+ 本包自造段 200，metaId ≥ 2200）。
     *
     * <p>★ 2026-09-19 队长指示「注销残留注册的物品」⇒ 369 → <b>368</b>：注销
     * {@code gtlmodular:cc_processed_chip_planck_pic}（{@code ProcessedChipPlanckPIC} metaId 710）——
     * 该件无任何配方/代码引用且从来没有获取途径（证据三连见 {@link #addRows()} 里 709 行下方的注释）。
     * ⚠️ 未处理的 {@code ChipPlanckPIC}(610) **保留**，它的换算配方 {@code nanochip/conversion/cc_chip_planck_pic.json} 未动。
     *
     * <p>★ 2026-09-21 队长拍板「直接把时空导线这条链整个删掉」⇒ 368 → <b>366</b>：注销
     * {@code gtlmodular:cc_wire_spacetime}（{@code WireSpacetime} metaId 4）与
     * {@code gtlmodular:cc_processed_wire_spacetime}（{@code ProcessedWireSpacetime} metaId 104）——
     * ① 该链真件 {@code gtceu:spacetime_single_wire} 的材料属 <b>OpV</b> 档，超过 NAC 最高档 UXV，
     * 在 GTL 里根本做不出来；② 该链产物是全库公认的孤儿（作为配方输入 0 次）。
     * 同批删掉 3 条配方（正/反换算 + wire_tracer 006）、模型与贴图、4 条 lang 键。
     * ⚠️ metaId 4 / 104 从此是**空号，不补位**（metaId 是 NH 假栈的 damage，挪号会破坏「按 NH metaId 查表」）。
     */
    private static final List<Entry> ALL = new ArrayList<>(366);
    private static final Map<Integer, Entry> BY_META = new LinkedHashMap<>();
    private static final Map<String, Entry> BY_ENUM = new LinkedHashMap<>();
    private static final Map<String, ItemEntry<CcItem>> REGISTERED = new LinkedHashMap<>();

    private static void add(String enumName, int metaId, boolean processed, String nameKey, String realRef, String calibration) {
        Entry e = new Entry(enumName, metaId, processed, nameKey, "cc_" + snake(enumName), realRef, calibration);
        ALL.add(e);
        BY_META.put(metaId, e);
        BY_ENUM.put(enumName, e);
    }

    /**
     * 缩写感知的 snake_case（与 {@code _census/gen_nac_cc_items.js} 的 {@code snake()} 逐字符同构）：
     * 在相邻两字符之间插 {@code _}，当且仅当 (a) 前一字符是小写字母/数字而当前是大写，或
     * (b) 前一字符与当前都是大写、且再下一个是小写（缩写串词边界）。
     * 例：AdvSMDResistor→adv_smd_resistor、ChipNanoCPU→chip_nano_cpu、SuperconductorLuV→superconductor_lu_v。
     */
    private static String snake(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean upper = c >= 'A' && c <= 'Z';
            if (upper && i > 0) {
                char prev = s.charAt(i - 1);
                boolean prevLowerOrDigit = (prev >= 'a' && prev <= 'z') || (prev >= '0' && prev <= '9');
                boolean prevUpper = prev >= 'A' && prev <= 'Z';
                boolean nextLower = i + 1 < s.length() && s.charAt(i + 1) >= 'a' && s.charAt(i + 1) <= 'z';
                if (prevLowerOrDigit || (prevUpper && nextLower)) {
                    sb.append('_');
                }
            }
            sb.append(Character.toLowerCase(c));
        }
        return sb.toString();
    }

    static {
        addRows();
        // 注册（用本模组的 GTRegistrate；静态块会在接线调用 init() 时于 RegisterEvent<Item> 之前跑完）
        for (Entry e : ALL) {
            e.entry = GTLModularMachines.REGISTRATE.item(e.itemId, p -> new CcItem(p, e)).register();
            REGISTERED.put(e.itemId, e.entry);
        }
    }

    /** 强制类加载（接线用；注册已在静态块完成，重复调用无副作用）。 */
    public static void init() {
        // 触碰一下表，确保类已加载。
        if (ALL.size() != 366) {
            throw new IllegalStateException("NAC CC 物品层表异常：应为 366 条（NH 166 + 自造 200），实为 " + ALL.size());
        }
    }

    /** 全部 366 条（metaId 升序，只读）。 */
    public static List<Entry> all() { return Collections.unmodifiableList(ALL); }

    /** metaId → 物品。 */
    public static Item itemFor(int metaId) {
        Entry e = BY_META.get(metaId);
        return e == null ? null : e.item();
    }

    /** metaId → 物品栈。 */
    public static ItemStack stack(int metaId, int count) {
        Item it = itemFor(metaId);
        return it == null ? ItemStack.EMPTY : new ItemStack(it, count);
    }

    /** 枚举名 → 物品栈。 */
    public static ItemStack stack(String enumName, int count) {
        Entry e = BY_ENUM.get(enumName);
        return e == null || e.item() == null ? ItemStack.EMPTY : new ItemStack(e.item(), count);
    }

    /** 枚举名 → 物品 id 路径（{@code cc_xxx}）。 */
    public static String idFor(String enumName) {
        Entry e = BY_ENUM.get(enumName);
        return e == null ? null : e.itemId;
    }

    /** metaId → 枚举名（366 条全覆盖，未处理/已处理都能查到；注销件 4 / 104 / 710 不在表内 ⇒ 返回 null）。 */
    public static Map<Integer, String> metaToName() {
        Map<Integer, String> m = new LinkedHashMap<>();
        for (Entry e : ALL) m.put(e.metaId, e.enumName);
        return Collections.unmodifiableMap(m);
    }

    /** 枚举名 → 表项；未登记返回 null。 */
    public static Entry byEnumName(String enumName) { return BY_ENUM.get(enumName); }

    /** metaId → 表项；未登记返回 null。 */
    public static Entry byMetaId(int metaId) { return BY_META.get(metaId); }

    // ---------------------------------------------------------------- 366 行数据（生成，勿手改）
    private static void addRows() {

        add("WireNiobiumTitanium", 0, false, "gt.circuitcomponent.wirenbti", "mat:niobium_titanium:fine_wire", null);   // Packaged Fine Niobium Titanium Wire  [真实物品名由 GTCEu 1.20.1 资源组合（NH 侧该真实物品在 1.20.1 不存在）; 未处理件的「真实物品」在 1.20.1 不存在，显示名按降级规则给出]
        add("WireYttriumBariumCuprate", 1, false, "gt.circuitcomponent.wireybc", "mat:yttrium_barium_cuprate:fine_wire", null);   // Packaged Fine Yttrium Barium Cuprate Wire  [真实物品名由 GTCEu 1.20.1 资源组合（NH 侧该真实物品在 1.20.1 不存在）; 未处理件的「真实物品」在 1.20.1 不存在，显示名按降级规则给出]
        add("WireLumiium", 2, false, "gt.circuitcomponent.wirelumiium", null, null);   // Packaged Fine Lumiium Wire  [! 材质来自 GT++ 取件方法，未逐一解析（统一用 GTCEu DULL 母版）; ! 真实物品名拿不到 NH 资源（降级：自撰/退化）; 未处理件的「真实物品」在 1.20.1 不存在，显示名按降级规则给出; ! 显示名以**实际真件**为准（NH 原名：细流明导线）]
        add("WireProtoHalkonite", 3, false, "gt.circuitcomponent.wireprotohalkonite", null, null);   // Packaged Fine Proto-Halkonite Steel Wire  [! 真实物品名拿不到 NH 资源（降级：自撰/退化）; 未处理件的「真实物品」在 1.20.1 不存在，显示名按降级规则给出; ! 显示名以**实际真件**为准（NH 原名：细原初恒坚钢导线）]
        // ★ 2026-09-21 队长拍板「直接把时空导线这条链整个删掉」⇒ 本行整条注销
        //    （原位置在 `WireProtoHalkonite`(3) 与 `CableOpticalFiber`(5) 之间；metaId 4 从此是**空号，不补位**
        //     —— metaId 是 NH 假栈的 damage，挪号会破坏「按 NH metaId 直接查表」的约定）：
        //     原注册行 = WireSpacetime / metaId 4 / 未处理 / nameKey "gt.circuitcomponent.wirespacetime"
        //     （不在这里复写完整 `add(...)` 字面量 —— `_census/check_nac_cc_items.js` 规则②会把它当成注册行）。
        //     注销 id = `gtlmodular:cc_wire_spacetime`（汉名「封装 1x时空导线」）。两条理由：
        //       ① 材料超阶段：本链真件 = `gtceu:spacetime_single_wire`，材料 `spacetime` 属 **OpV** 档，
        //          超过 NAC 最高档 UXV（任务书科技等级第 13 章）⇒ 在 GTL 里根本做不出来；
        //       ② 产物孤儿：孪生已处理件 104 在全库配方里作为输入 **0 次**出现 ⇒ 整条链产而不耗、无下游用途。
        //     ⇒ 孪生件 ProcessedWireSpacetime(104) 与 3 条配方（正/反换算 + wire_tracer/006）同批注销，
        //       见下方 104 处（`ProcessedWireProtoHalkonite` 与 `ProcessedCableOpticalFiber` 之间）的注销注释。
        //     退役登记：`_census/nac_cc_items.json` 的 `retired[]`（第 9/10 条）+ `_census/check_retired_items.js`。
        add("CableOpticalFiber", 5, false, "gt.circuitcomponent.cable.opticalfiber", null, null);   // Packaged Optical Fiber Cable  [! 无可用真实物品贴图，退化自造底板; ! 真实物品名拿不到 NH 资源（降级：自撰/退化）; 未处理件的「真实物品」在 1.20.1 不存在，显示名按降级规则给出; ! NH 期望名（尾巴）不是「形状+材料」结构，未做材料词修正（原文 光纤线缆；解析出的材料 id=optical_fiber）; ! 显示名以**实际真件**为准（NH 原名：光纤线缆）]
        add("WireInfinity", 6, false, "gt.circuitcomponent.wireinfinity", null, null);   // Packaged 1x Infinity Wire  [! 真实物品名拿不到 NH 资源（降级：自撰/退化）; 未处理件的「真实物品」在 1.20.1 不存在，显示名按降级规则给出]
        add("WireHypogen", 7, false, "gt.circuitcomponent.wirehypogen", null, null);   // Packaged Fine Hypogen Wire  [! 材质来自 GT++ 取件方法，未逐一解析（统一用 GTCEu DULL 母版）; ! 真实物品名拿不到 NH 资源（降级：自撰/退化）; 未处理件的「真实物品」在 1.20.1 不存在，显示名按降级规则给出; ! 显示名以**实际真件**为准（NH 原名：细海珀珍导线）]
        add("WireMagMatter", 8, false, "gt.circuitcomponent.wiremagmatter", null, null);   // Packaged Fine Magmatter Wire  [! 真实物品名拿不到 NH 资源（降级：自撰/退化）; 未处理件的「真实物品」在 1.20.1 不存在，显示名按降级规则给出; ! 显示名以**实际真件**为准（NH 原名：细磁物质导线）]
        add("ProcessedWireNiobiumTitanium", 100, true, "gt.circuitcomponent.processed.wirenbti", null, null);   // Spooled Niobium-Titanium Wire
        add("ProcessedWireYttriumBariumCuprate", 101, true, "gt.circuitcomponent.processed.wireybc", null, null);   // Spooled Yttrium Barium Cuprate Wire
        add("ProcessedWireLumiium", 102, true, "gt.circuitcomponent.processed.wirelumiium", null, null);   // Spooled Lumiium Wire
        add("ProcessedWireProtoHalkonite", 103, true, "gt.circuitcomponent.processed.wireprotohalkonite", null, null);   // Spooled Proto-Halkonite Steel Wire
        // ★ 2026-09-21 同批注销（时空导线链）：本行原位置在 `ProcessedWireProtoHalkonite`(103) 与
        //    `ProcessedCableOpticalFiber`(105) 之间；metaId 104 从此是**空号，不补位**。
        //    原注册行 = ProcessedWireSpacetime / metaId 104 / 已处理 /
        //    nameKey "gt.circuitcomponent.processed.wirespacetime"（汉名「时空线轴」）
        //    （同样不在注释里复写完整 `add(...)` 字面量）。
        //    注销 id = `gtlmodular:cc_processed_wire_spacetime`。两条理由：
        //      ① 材料超阶段（继承其 CC 来料）：唯一来路 `wire_tracer/006` 吃 `gtceu:spacetime_single_wire`，
        //         材料 `spacetime` 属 **OpV** 档 > NAC 最高档 UXV ⇒ 在 GTL 里做不出来；
        //      ② 产物孤儿：459 个 `data/gtlmodular/recipes/**` JSON 里它作为**输出** 1 次、
        //         作为**输入** **0 次**（`_census/three_families_cc_plan.json` 的
        //         `stillOrphanAfterThisWave` 名单里就有它）⇒ 产而不耗。
        //    同批删除落点：3 条配方 `nanochip/{conversion,conversion_reverse}/cc_wire_spacetime.json`
        //    + `nanochip/wire_tracer/006_cc_processed_wire_spacetime.json`；模型 + 贴图（含 .mcmeta）；
        //    lang(zh_cn/en_us) 各 2 键 ⇒ 共 4 键。
        add("ProcessedCableOpticalFiber", 105, true, "gt.circuitcomponent.cable.processed.opticalfiber", null, null);   // Split Optical Fiber Cable
        add("ProcessedWireInfinity", 106, true, "gt.circuitcomponent.processed.wireinfinity", null, null);   // Spooled Infinity Wire
        add("ProcessedWireHypogen", 107, true, "gt.circuitcomponent.processed.wirehypogen", null, null);   // Spooled Hypogen Wire
        add("ProcessedWireMagMatter", 108, true, "gt.circuitcomponent.processed.wiremagmatter", null, null);   // Spooled Magmatter Wire
        add("AdvSMDResistor", 210, false, "gt.circuitcomponent.asmd.resistor", "item:advanced_smd_resistor", null);   // Packaged Advanced SMD Resistor  [真实物品名由 GTCEu 1.20.1 资源组合（NH 侧该真实物品在 1.20.1 不存在）; 未处理件的「真实物品」在 1.20.1 不存在，显示名按降级规则给出]
        add("AdvSMDTransistor", 211, false, "gt.circuitcomponent.asmd.transistor", "item:advanced_smd_transistor", null);   // Packaged Advanced SMD Transistor  [真实物品名由 GTCEu 1.20.1 资源组合（NH 侧该真实物品在 1.20.1 不存在）; 未处理件的「真实物品」在 1.20.1 不存在，显示名按降级规则给出]
        add("AdvSMDInductor", 212, false, "gt.circuitcomponent.asmd.inductor", "item:advanced_smd_inductor", null);   // Packaged Advanced SMD Inductor  [真实物品名由 GTCEu 1.20.1 资源组合（NH 侧该真实物品在 1.20.1 不存在）; 未处理件的「真实物品」在 1.20.1 不存在，显示名按降级规则给出]
        add("AdvSMDCapacitor", 213, false, "gt.circuitcomponent.asmd.capacitor", "item:advanced_smd_capacitor", null);   // Packaged Advanced SMD Capacitor  [真实物品名由 GTCEu 1.20.1 资源组合（NH 侧该真实物品在 1.20.1 不存在）; 未处理件的「真实物品」在 1.20.1 不存在，显示名按降级规则给出]
        add("AdvSMDDiode", 214, false, "gt.circuitcomponent.asmd.diode", "item:advanced_smd_diode", null);   // Packaged Advanced SMD Diode  [真实物品名由 GTCEu 1.20.1 资源组合（NH 侧该真实物品在 1.20.1 不存在）; 未处理件的「真实物品」在 1.20.1 不存在，显示名按降级规则给出]
        add("OpticalSMDResistor", 220, false, "gt.circuitcomponent.xsmd.resistor", null, null);   // Packaged Optical SMD Resistor  [! 无可用真实物品贴图，退化自造底板; ! 真实物品名拿不到 NH 资源（降级：自撰/退化）; 未处理件的「真实物品」在 1.20.1 不存在，显示名按降级规则给出; ! NH 期望名（尾巴）不是「形状+材料」结构，未做材料词修正（原文 光学贴片电阻；解析出的材料 id=optical_smd_resistor）; ! 显示名以**实际真件**为准（NH 原名：null）]
        add("OpticalSMDTransistor", 221, false, "gt.circuitcomponent.xsmd.transistor", null, null);   // Packaged Optical SMD Transistor  [! 无可用真实物品贴图，退化自造底板; ! 真实物品名拿不到 NH 资源（降级：自撰/退化）; 未处理件的「真实物品」在 1.20.1 不存在，显示名按降级规则给出; ! NH 期望名（尾巴）不是「形状+材料」结构，未做材料词修正（原文 光学贴片晶体管；解析出的材料 id=optical_smd_transistor）; ! 显示名以**实际真件**为准（NH 原名：null）]
        add("OpticalSMDInductor", 222, false, "gt.circuitcomponent.xsmd.inductor", null, null);   // Packaged Optical SMD Inductor  [! 无可用真实物品贴图，退化自造底板; ! 真实物品名拿不到 NH 资源（降级：自撰/退化）; 未处理件的「真实物品」在 1.20.1 不存在，显示名按降级规则给出; ! NH 期望名（尾巴）不是「形状+材料」结构，未做材料词修正（原文 光学贴片电感；解析出的材料 id=optical_smd_inductor）; ! 显示名以**实际真件**为准（NH 原名：null）]
        add("OpticalSMDCapacitor", 223, false, "gt.circuitcomponent.xsmd.capacitor", null, null);   // Packaged Optical SMD Capacitor  [! 无可用真实物品贴图，退化自造底板; ! 真实物品名拿不到 NH 资源（降级：自撰/退化）; 未处理件的「真实物品」在 1.20.1 不存在，显示名按降级规则给出; ! NH 期望名（尾巴）不是「形状+材料」结构，未做材料词修正（原文 光学贴片电容；解析出的材料 id=optical_smd_capacitor）; ! 显示名以**实际真件**为准（NH 原名：null）]
        add("OpticalSMDDiode", 224, false, "gt.circuitcomponent.xsmd.diode", null, null);   // Packaged Optical SMD Diode  [! 无可用真实物品贴图，退化自造底板; ! 真实物品名拿不到 NH 资源（降级：自撰/退化）; 未处理件的「真实物品」在 1.20.1 不存在，显示名按降级规则给出; ! NH 期望名（尾巴）不是「形状+材料」结构，未做材料词修正（原文 光学贴片二极管；解析出的材料 id=optical_smd_diode）; ! 显示名以**实际真件**为准（NH 原名：null）]
        add("ProcessedAdvSMDResistor", 310, true, "gt.circuitcomponent.processed.asmd.resistor", null, null);   // Prepared Advanced SMD Resistor
        add("ProcessedAdvSMDTransistor", 311, true, "gt.circuitcomponent.processed.asmd.transistor", null, null);   // Prepared Advanced SMD Transistor
        add("ProcessedAdvSMDInductor", 312, true, "gt.circuitcomponent.processed.asmd.inductor", null, null);   // Prepared Advanced SMD Inductor
        add("ProcessedAdvSMDCapacitor", 313, true, "gt.circuitcomponent.processed.asmd.capacitor", null, null);   // Prepared Advanced SMD Capacitor
        add("ProcessedAdvSMDDiode", 314, true, "gt.circuitcomponent.processed.asmd.diode", null, null);   // Prepared Advanced SMD Diode
        add("ProcessedOpticalSMDResistor", 320, true, "gt.circuitcomponent.processed.xsmd.resistor", null, null);   // Prepared Optical SMD Resistor
        add("ProcessedOpticalSMDTransistor", 321, true, "gt.circuitcomponent.processed.xsmd.transistor", null, null);   // Prepared Optical SMD Transistor
        add("ProcessedOpticalSMDInductor", 322, true, "gt.circuitcomponent.processed.xsmd.inductor", null, null);   // Prepared Optical SMD Inductor
        add("ProcessedOpticalSMDCapacitor", 323, true, "gt.circuitcomponent.processed.xsmd.capacitor", null, null);   // Prepared Optical SMD Capacitor
        add("ProcessedOpticalSMDDiode", 324, true, "gt.circuitcomponent.processed.xsmd.diode", null, null);   // Prepared Optical SMD Diode
        add("BoardMultifiberglassElite", 400, false, "gt.circuitcomponent.board.multifiberelite", "item:multilayer_fiber_reinforced_circuit_board", null);   // Packaged Multi-layer Fiber-Reinforced Circuit Board  [! 真实物品为近似映射（Circuit_Board_Multifiberglass_Elite → multilayer_fiber_reinforced_circuit_board）; 真实物品名由 GTCEu 1.20.1 资源组合（NH 侧该真实物品在 1.20.1 不存在）; 未处理件的「真实物品」在 1.20.1 不存在，显示名按降级规则给出]
        add("BoardWetwareLifesupport", 401, false, "gt.circuitcomponent.board.wetwarelifesupport", "item:wetware_circuit_board", null);   // Packaged Wetware Circuit Board  [真实物品名由 GTCEu 1.20.1 资源组合（NH 侧该真实物品在 1.20.1 不存在）; 未处理件的「真实物品」在 1.20.1 不存在，显示名按降级规则给出]
        add("BoardBioMutated", 402, false, "gt.circuitcomponent.board.biomutated", null, null);   // Packaged Ultra Bio Mutated Circuit Board  [! 无可用真实物品贴图，退化自造底板; ! 真实物品名拿不到 NH 资源（降级：自撰/退化）; 未处理件的「真实物品」在 1.20.1 不存在，显示名按降级规则给出; ! NH 期望名（尾巴）不是「形状+材料」结构，未做材料词修正（原文 超级生物突变电路板；解析出的材料 id=bio_mutated）; ! 显示名以**实际真件**为准（NH 原名：超级生物突变电路板）]
        add("BoardOptical", 403, false, "gt.circuitcomponent.board.optical", null, null);   // Packaged Optical Circuit Board  [! 无可用真实物品贴图，退化自造底板; ! 真实物品名拿不到 NH 资源（降级：自撰/退化）; 未处理件的「真实物品」在 1.20.1 不存在，显示名按降级规则给出; ! NH 期望名（尾巴）不是「形状+材料」结构，未做材料词修正（原文 光学电路板；解析出的材料 id=optical）; ! 显示名以**实际真件**为准（NH 原名：光学电路板）]
        add("NeuroProcessingUnit", 404, false, "gt.circuitcomponent.neuroprocessingunit", "item:neuro_processing_unit", null);   // Packaged Neuro Processing Unit  [真实物品名由 GTCEu 1.20.1 资源组合（NH 侧该真实物品在 1.20.1 不存在）; 未处理件的「真实物品」在 1.20.1 不存在，显示名按降级规则给出; ! NH 期望名（尾巴）不是「形状+材料」结构，未做材料词修正（原文 神经处理单元；解析出的材料 id=neuro_processing_unit）; ! 显示名以**实际真件**为准（NH 原名：null）]
        add("BioProcessingUnit", 405, false, "gt.circuitcomponent.bioprocessingunit", null, null);   // Packaged Bio Processing Unit  [! 无可用真实物品贴图，退化自造底板; ! 真实物品名拿不到 NH 资源（降级：自撰/退化）; 未处理件的「真实物品」在 1.20.1 不存在，显示名按降级规则给出; ! NH 期望名（尾巴）不是「形状+材料」结构，未做材料词修正（原文 生物处理单元；解析出的材料 id=bio_processing_unit）; ! 显示名以**实际真件**为准（NH 原名：生物处理单元）]
        add("LivingBioChip", 406, false, "gt.circuitcomponent.livingbiochip", null, null);   // Packaged Living Bio Chip  [! 无可用真实物品贴图，退化自造底板; ! 真实物品名拿不到 NH 资源（降级：自撰/退化）; 未处理件的「真实物品」在 1.20.1 不存在，显示名按降级规则给出; ! NH 期望名（尾巴）不是「形状+材料」结构，未做材料词修正（原文 活体生物芯片；解析出的材料 id=living_bio_chip）; ! 显示名以**实际真件**为准（NH 原名：活体生物芯片）]
        add("ProcessedBoardMultifiberglassElite", 500, true, "gt.circuitcomponent.processed.board.multifiberelite", null, null);   // Purified Elite Circuit Board
        add("ProcessedBoardWetwareLifesupport", 501, true, "gt.circuitcomponent.processed.board.wetwarelifesupport", null, null);   // Purified Extreme Wetware Lifesupport Circuit Board
        add("ProcessedBoardBioMutated", 502, true, "gt.circuitcomponent.processed.board.biomutated", null, null);   // Purified Ultra Bio Mutated Circuit Board
        add("ProcessedBoardOptical", 503, true, "gt.circuitcomponent.processed.board.optical", null, null);   // Purified Optical Circuit Board
        add("ProcessedNeuroProcessingUnit", 504, true, "gt.circuitcomponent.processed.neuroprocessingunit", null, null);   // Coordinated Neuro Processing Unit
        add("ProcessedBioProcessingUnit", 505, true, "gt.circuitcomponent.processed.board.bioprocessingunit", null, null);   // Coordinated Bio Processing Unit
        add("ProcessedLivingBioChip", 506, true, "gt.circuitcomponent.processed.board.livingbiochip", null, null);   // Coordinated Living Bio Chip
        add("ChipNanoCPU", 600, false, "gt.circuitcomponent.chipNanoCPU", "item:nano_cpu_chip", null);   // Packaged Nano CPU Chip  [真实物品名由 GTCEu 1.20.1 资源组合（NH 侧该真实物品在 1.20.1 不存在）; 未处理件的「真实物品」在 1.20.1 不存在，显示名按降级规则给出]
        add("ChipRAM", 601, false, "gt.circuitcomponent.chipram", "item:ram_chip", null);   // Packaged RAM Chip  [真实物品名由 GTCEu 1.20.1 资源组合（NH 侧该真实物品在 1.20.1 不存在）; 未处理件的「真实物品」在 1.20.1 不存在，显示名按降级规则给出]
        add("ChipNOR", 602, false, "gt.circuitcomponent.chipnor", "item:nor_memory_chip", null);   // Packaged NOR Memory Chip  [真实物品名由 GTCEu 1.20.1 资源组合（NH 侧该真实物品在 1.20.1 不存在）; 未处理件的「真实物品」在 1.20.1 不存在，显示名按降级规则给出]
        add("ChipNAND", 603, false, "gt.circuitcomponent.chipnand", "item:nand_memory_chip", null);   // Packaged NAND Memory Chip  [真实物品名由 GTCEu 1.20.1 资源组合（NH 侧该真实物品在 1.20.1 不存在）; 未处理件的「真实物品」在 1.20.1 不存在，显示名按降级规则给出]
        add("ChipASOC", 604, false, "gt.circuitcomponent.chipasoc", "item:advanced_soc", null);   // Packaged ASoC  [真实物品名由 GTCEu 1.20.1 资源组合（NH 侧该真实物品在 1.20.1 不存在）; 未处理件的「真实物品」在 1.20.1 不存在，显示名按降级规则给出]
        add("ChipPikoPIC", 605, false, "gt.circuitcomponent.chippikopic", null, null);   // Packaged Piko Power IC Chips  [! 无可用真实物品贴图，退化自造底板; ! 真实物品名拿不到 NH 资源（降级：自撰/退化）; 未处理件的「真实物品」在 1.20.1 不存在，显示名按降级规则给出; ! NH 期望名（尾巴）不是「形状+材料」结构，未做材料词修正（原文 Piko 电源芯片；解析出的材料 id=piko_pic）; ! 显示名以**实际真件**为准（NH 原名：Piko 电源芯片）]
        add("ChipQuantumPIC", 606, false, "gt.circuitcomponent.chipquantumpic", null, null);   // Packaged Quantum Power IC Chips  [! 无可用真实物品贴图，退化自造底板; ! 真实物品名拿不到 NH 资源（降级：自撰/退化）; 未处理件的「真实物品」在 1.20.1 不存在，显示名按降级规则给出; ! NH 期望名（尾巴）不是「形状+材料」结构，未做材料词修正（原文 量子电源芯片；解析出的材料 id=quantum_pic）; ! 显示名以**实际真件**为准（NH 原名：量子电源芯片）]
        add("ChipAttoPIC", 607, false, "gt.circuitcomponent.chipattopic", null, null);   // Packaged Atto Power IC Chips  [! 无可用真实物品贴图，退化自造底板; ! 真实物品名拿不到 NH 资源（降级：自撰/退化）; 未处理件的「真实物品」在 1.20.1 不存在，显示名按降级规则给出; ! NH 期望名（尾巴）不是「形状+材料」结构，未做材料词修正（原文 阿托电源芯片；解析出的材料 id=atto_pic）; ! 显示名以**实际真件**为准（NH 原名：阿托电源芯片）]
        add("ChipZeptoPIC", 608, false, "gt.circuitcomponent.chipzeptopic", null, null);   // Packaged Zepto Power IC Chips  [! 无可用真实物品贴图，退化自造底板; ! 真实物品名拿不到 NH 资源（降级：自撰/退化）; 未处理件的「真实物品」在 1.20.1 不存在，显示名按降级规则给出; ! NH 期望名（尾巴）不是「形状+材料」结构，未做材料词修正（原文 仄托电源芯片；解析出的材料 id=zepto_pic）; ! 显示名以**实际真件**为准（NH 原名：仄托电源芯片）]
        add("ChipYoctoPIC", 609, false, "gt.circuitcomponent.chipyoctopic", null, null);   // Packaged Yocto Power IC Chips  [! 无可用真实物品贴图，退化自造底板; ! 真实物品名拿不到 NH 资源（降级：自撰/退化）; 未处理件的「真实物品」在 1.20.1 不存在，显示名按降级规则给出; ! NH 期望名（尾巴）不是「形状+材料」结构，未做材料词修正（原文 幺托电源芯片；解析出的材料 id=yocto_pic）; ! 显示名以**实际真件**为准（NH 原名：幺托电源芯片）]
        add("ChipPlanckPIC", 610, false, "gt.circuitcomponent.chipplanckpic", null, null);   // Packaged Planck Power IC Chips  [! 无可用真实物品贴图，退化自造底板; ! 真实物品名拿不到 NH 资源（降级：自撰/退化）; 未处理件的「真实物品」在 1.20.1 不存在，显示名按降级规则给出; ! NH 期望名（尾巴）不是「形状+材料」结构，未做材料词修正（原文 普朗克电源芯片；解析出的材料 id=planck_pic）; ! 显示名以**实际真件**为准（NH 原名：普朗克电源芯片）]
        add("ProcessedChipNanoCPU", 700, true, "gt.circuitcomponent.processed.chipnanocpu", null, null);   // Cut Nanocomponent CPU Chips
        add("ProcessedChipRAM", 701, true, "gt.circuitcomponent.processed.chipram", null, null);   // Cut Random Access Memory Chips
        add("ProcessedChipNOR", 702, true, "gt.circuitcomponent.processed.chipnor", null, null);   // Cut NOR Memory Chips
        add("ProcessedChipNAND", 703, true, "gt.circuitcomponent.processed.chipnand", null, null);   // Cut NAND Memory Chips
        add("ProcessedChipASOC", 704, true, "gt.circuitcomponent.processed.chipasoc", null, null);   // Cut ASoC Chips
        add("ProcessedChipPikoPIC", 705, true, "gt.circuitcomponent.processed.chippikopic", null, null);   // Cut Piko Power IC Chips
        add("ProcessedChipQuantumPIC", 706, true, "gt.circuitcomponent.processed.chipquantumpic", null, null);   // Cut Quantum Power IC Chips
        add("ProcessedChipAttoPIC", 707, true, "gt.circuitcomponent.processed.chipattopic", null, null);   // Cut Atto Power IC Chips
        add("ProcessedChipZeptoPIC", 708, true, "gt.circuitcomponent.processed.chipzeptopic", null, null);   // Cut Zepto Power IC Chips
        add("ProcessedChipYoctoPIC", 709, true, "gt.circuitcomponent.processed.chipyoctopic", null, null);   // Cut Yocto Power IC Chips
        // ★ 2026-09-19 队长指示「把整合包里面残留注册的、合成根本用不到的物品注销掉」⇒ 本行整条注销：
        //    原注册行 = ProcessedChipPlanckPIC / metaId 710 / nameKey "gt.circuitcomponent.processed.chipplanckpic"
        //    （不在这里复写完整 `add(...)` 字面量 —— `_census/check_nac_cc_items.js` 规则②会把它当成第 369 条注册行）。
        //   注销 id = `gtlmodular:cc_processed_chip_planck_pic`。逐条「证据三连」（脚本 `_census/_probe_unused_items.js` 可复跑）：
        //     ① 无配方：459 个 `data/gtlmodular/recipes/**` JSON 里 0 次出现（既非 inputs 也非 outputs）；
        //        整合包实例 kubejs 283 个脚本里 `gtlmodular` 0 次命中；`NanochipSelfCheck.EXPECTED_RECIPES` 也没有它。
        //     ② 无代码引用：全 Java 树里除本行注册以外 0 处引用（`ProcessedChipPlanckPIC` / 该 id 字符串皆 0 命中）；
        //        `NanochipCircuitComponents.itemFor/stack/byMetaId` 与 `NanochipMissingItems.entryForMeta` 都没有 710 的调用方。
        //     ③ 从来拿不到：切割室目录只有 `006..010_cc_processed_chip_{piko,quantum,atto,zepto,yocto}_pic`，
        //        **没有 planck 那一条**；32 份 `_jar备份_gtlmodular-*.jar`（含 5 份「已部署版」）逐份解包确认
        //        `data/gtlmodular/recipes/nanochip/cutting_chamber/` 里**从来没有**产出它的配方 ⇒ 无配方 / 无掉落 / 无给予途径。
        //   归因：这是 NH 自己的空档 —— `ProcessedChipPlanckPIC` 在 NH 的 `ScriptNanochipRecipes.txt` 里 0 次命中
        //   （见 `_census/NAC_机制与配方_待办清单.md` §12.5 附注 + §12.8 第 5 条「需另裁」）；PIC 六档里
        //   piko/quantum/atto/zepto/yocto 五档在切割室都有加工配方，只有 planck 这一档是个注册出来却没人用的空壳。
        //   ⇒ 保留未处理的 `ChipPlanckPIC`(610) 与它的换算配方（本行注销不影响那条链），只注销这个已处理空壳。
        add("ChipCrystalCPU", 800, false, "gt.circuitcomponent.chipcrystalcpu", "item:crystal_cpu", null);   // Packaged Crystal CPU  [真实物品名由 GTCEu 1.20.1 资源组合（NH 侧该真实物品在 1.20.1 不存在）; 未处理件的「真实物品」在 1.20.1 不存在，显示名按降级规则给出]
        add("ChipCrystalSoC", 801, false, "gt.circuitcomponent.chipcrystalsoc", "item:crystal_soc", null);   // Packaged Crystal SoC  [真实物品名由 GTCEu 1.20.1 资源组合（NH 侧该真实物品在 1.20.1 不存在）; 未处理件的「真实物品」在 1.20.1 不存在，显示名按降级规则给出]
        add("ChipLivingCrystal", 802, false, "gt.circuitcomponent.chiplivingcrystal", null, null);   // Packaged Living Crystal Chip  [! 无可用真实物品贴图，退化自造底板; ! 真实物品名拿不到 NH 资源（降级：自撰/退化）; 未处理件的「真实物品」在 1.20.1 不存在，显示名按降级规则给出; ! NH 期望名（尾巴）不是「形状+材料」结构，未做材料词修正（原文 活体晶体芯片；解析出的材料 id=living_crystal）; ! 显示名以**实际真件**为准（NH 原名：活体晶体芯片）]
        add("ChipRawAdvancedCrystal", 803, false, "gt.circuitcomponent.chiprawadvancedcrystal", "item:raw_crystal_chip", null);   // Packaged Raw Crystal Chip  [! 真实物品为近似映射（Circuit_Chip_CrystalSoC2 → raw_crystal_chip）; 真实物品名由 GTCEu 1.20.1 资源组合（NH 侧该真实物品在 1.20.1 不存在）; 未处理件的「真实物品」在 1.20.1 不存在，显示名按降级规则给出; ! NH 期望名（尾巴）不是「形状+材料」结构，未做材料词修正（原文 晶体芯片原料；解析出的材料 id=raw_advanced_crystal）; ! 显示名以**实际真件**为准（NH 原名：null）]
        add("ProcessedChipCrystalCPU", 900, true, "gt.circuitcomponent.processed.chipcrystalcpu", null, null);   // Etched Crystal Processing Unit
        add("ProcessedChipCrystalSoC", 901, true, "gt.circuitcomponent.processed.chipcrystalsoc", null, null);   // Etched Crystal SoC
        add("ProcessedChipLivingCrystal", 902, true, "gt.circuitcomponent.processed.chiplivingcrystal", null, null);   // Coordinated Living Crystal Chip
        add("ProcessedChipRawAdvancedCrystal", 903, true, "gt.circuitcomponent.processed.chiprawadvancedcrystal", null, null);   // Coordinated Raw Advanced Crystal Chip
        add("ChipOpticalCPU", 1000, false, "gt.circuitcomponent.opticalcpu", null, null);   // Packaged Optically Perfected CPU  [! 无可用真实物品贴图，退化自造底板; ! 真实物品名拿不到 NH 资源（降级：自撰/退化）; 未处理件的「真实物品」在 1.20.1 不存在，显示名按降级规则给出; ! NH 期望名（尾巴）不是「形状+材料」结构，未做材料词修正（原文 光学完善处理器；解析出的材料 id=optical_cpu）; ! 显示名以**实际真件**为准（NH 原名：光学完善处理器）]
        add("OpticalRAM", 1001, false, "gt.circuitcomponent.opticalram", null, null);   // Packaged Optically Compatible Memory  [! 无可用真实物品贴图，退化自造底板; ! 真实物品名拿不到 NH 资源（降级：自撰/退化）; 未处理件的「真实物品」在 1.20.1 不存在，显示名按降级规则给出; ! NH 期望名（尾巴）不是「形状+材料」结构，未做材料词修正（原文 光学兼容内存；解析出的材料 id=optical_ram）; ! 显示名以**实际真件**为准（NH 原名：光学兼容内存）]
        add("ProcessedChipOpticalCPU", 1100, true, "gt.circuitcomponent.processed.opticalcpu", null, null);   // Organized Optically Perfected CPU
        add("ProcessedOpticalRAM", 1101, true, "gt.circuitcomponent.processed.opticalram", null, null);   // Organized Optically Compatible Memory
        add("SuperconductorLuV", 1200, false, "gt.circuitcomponent.superconductorluv", null, null);   // Packaged 1x Superconductor LuV Wire  [! 真实物品名拿不到 NH 资源（降级：自撰/退化）; 未处理件的「真实物品」在 1.20.1 不存在，显示名按降级规则给出; ! NH 期望名（尾巴）不是「形状+材料」结构，未做材料词修正（原文 1xLuV 超导体导线；解析出的材料 id=superconductor_lu_v）; ! 显示名以**实际真件**为准（NH 原名：1xLuV 超导体导线）]
        add("SuperconductorZPM", 1201, false, "gt.circuitcomponent.superconductorzpm", null, null);   // Packaged 1x Superconductor ZPM Wire  [! 真实物品名拿不到 NH 资源（降级：自撰/退化）; 未处理件的「真实物品」在 1.20.1 不存在，显示名按降级规则给出; ! NH 期望名（尾巴）不是「形状+材料」结构，未做材料词修正（原文 1xZPM 超导体导线；解析出的材料 id=superconductor_zpm）; ! 显示名以**实际真件**为准（NH 原名：1xZPM 超导体导线）]
        add("SuperconductorUV", 1202, false, "gt.circuitcomponent.superconductoruv", null, null);   // Packaged 1x Superconductor UV Wire  [! 真实物品名拿不到 NH 资源（降级：自撰/退化）; 未处理件的「真实物品」在 1.20.1 不存在，显示名按降级规则给出; ! NH 期望名（尾巴）不是「形状+材料」结构，未做材料词修正（原文 1xUV 超导体导线；解析出的材料 id=superconductor_uv）; ! 显示名以**实际真件**为准（NH 原名：1xUV 超导体导线）]
        add("SuperconductorUHV", 1203, false, "gt.circuitcomponent.superconductoruhv", null, null);   // Packaged 1x Superconductor UHV Wire  [! 真实物品名拿不到 NH 资源（降级：自撰/退化）; 未处理件的「真实物品」在 1.20.1 不存在，显示名按降级规则给出; ! NH 期望名（尾巴）不是「形状+材料」结构，未做材料词修正（原文 1xUHV 超导体导线；解析出的材料 id=superconductor_uhv）; ! 显示名以**实际真件**为准（NH 原名：1xUHV 超导体导线）]
        add("SuperconductorUEV", 1204, false, "gt.circuitcomponent.superconductoruev", null, null);   // Packaged 1x Superconductor UEV Wire  [! 真实物品名拿不到 NH 资源（降级：自撰/退化）; 未处理件的「真实物品」在 1.20.1 不存在，显示名按降级规则给出; ! NH 期望名（尾巴）不是「形状+材料」结构，未做材料词修正（原文 1xUEV 超导体导线；解析出的材料 id=superconductor_uev）; ! 显示名以**实际真件**为准（NH 原名：1xUEV 超导体导线）]
        add("SuperconductorUIV", 1205, false, "gt.circuitcomponent.superconductoruiv", null, null);   // Packaged 1x Superconductor UIV Wire  [! 真实物品名拿不到 NH 资源（降级：自撰/退化）; 未处理件的「真实物品」在 1.20.1 不存在，显示名按降级规则给出; ! NH 期望名（尾巴）不是「形状+材料」结构，未做材料词修正（原文 1xUIV 超导体导线；解析出的材料 id=superconductor_uiv）; ! 显示名以**实际真件**为准（NH 原名：1xUIV 超导体导线）]
        add("SuperconductorUMV", 1206, false, "gt.circuitcomponent.superconductorumv", null, null);   // Packaged 1x Superconductor UMV Wire  [! 真实物品名拿不到 NH 资源（降级：自撰/退化）; 未处理件的「真实物品」在 1.20.1 不存在，显示名按降级规则给出; ! NH 期望名（尾巴）不是「形状+材料」结构，未做材料词修正（原文 1xUMV 超导体导线；解析出的材料 id=superconductor_umv）; ! 显示名以**实际真件**为准（NH 原名：1xUMV 超导体导线）]
        add("ProcessedSuperconductorLuV", 1300, true, "gt.circuitcomponent.processed.superconductorluv", null, null);   // Attuned Superconductor LuV Wire
        add("ProcessedSuperconductorZPM", 1301, true, "gt.circuitcomponent.processed.superconductorzpm", null, null);   // Attuned Superconductor ZPM Wire
        add("ProcessedSuperconductorUV", 1302, true, "gt.circuitcomponent.processed.superconductoruv", null, null);   // Attuned Superconductor UV Wire
        add("ProcessedSuperconductorUHV", 1303, true, "gt.circuitcomponent.processed.superconductoruhv", null, null);   // Attuned Superconductor UHV Wire
        add("ProcessedSuperconductorUEV", 1304, true, "gt.circuitcomponent.processed.superconductoruev", null, null);   // Attuned Superconductor UEV Wire
        add("ProcessedSuperconductorUIV", 1305, true, "gt.circuitcomponent.processed.superconductoruiv", null, null);   // Attuned Superconductor UIV Wire
        add("ProcessedSuperconductorUMV", 1306, true, "gt.circuitcomponent.processed.superconductorumv", null, null);   // Attuned Superconductor UMV Wire
        add("FrameboxAluminium", 1400, false, "gt.circuitcomponent.frame.aluminium", "mat:aluminium:frame", null);   // Packaged Aluminium Frame  [真实物品名由 GTCEu 1.20.1 资源组合（NH 侧该真实物品在 1.20.1 不存在）; 未处理件的「真实物品」在 1.20.1 不存在，显示名按降级规则给出]
        add("FrameboxTritanium", 1401, false, "gt.circuitcomponent.frame.tritanium", "mat:tritanium:frame", null);   // Packaged Tritanium Frame  [真实物品名由 GTCEu 1.20.1 资源组合（NH 侧该真实物品在 1.20.1 不存在）; 未处理件的「真实物品」在 1.20.1 不存在，显示名按降级规则给出]
        add("FrameboxCelestialTungsten", 1402, false, "gt.circuitcomponent.frame.celestialtungsten", null, null);   // Packaged Celestial Tungsten Frame Box  [! 材质来自 GT++ 取件方法，未逐一解析（统一用 GTCEu DULL 母版）; ! 真实物品名拿不到 NH 资源（降级：自撰/退化）; 未处理件的「真实物品」在 1.20.1 不存在，显示名按降级规则给出; ! 显示名以**实际真件**为准（NH 原名：天体钨框架）]
        add("FrameboxHypogen", 1403, false, "gt.circuitcomponent.frame.hypogen", null, null);   // Packaged Hypogen Frame Box  [! 材质来自 GT++ 取件方法，未逐一解析（统一用 GTCEu DULL 母版）; ! 真实物品名拿不到 NH 资源（降级：自撰/退化）; 未处理件的「真实物品」在 1.20.1 不存在，显示名按降级规则给出; ! 显示名以**实际真件**为准（NH 原名：海珀珍框架）]
        add("FrameboxMagMatter", 1404, false, "gt.circuitcomponent.frame.magmatter", null, null);   // Packaged Magmatter Frame Box  [! 真实物品名拿不到 NH 资源（降级：自撰/退化）; 未处理件的「真实物品」在 1.20.1 不存在，显示名按降级规则给出; ! 显示名以**实际真件**为准（NH 原名：磁物质框架）]
        add("ProcessedFrameboxAluminium", 1500, true, "gt.circuitcomponent.processed.frame.aluminium", null, null);   // Spliced Aluminium Framebox
        add("ProcessedFrameboxTritanium", 1501, true, "gt.circuitcomponent.processed.frame.tritanium", null, null);   // Spliced Tritanium Framebox
        add("ProcessedFrameboxCelestialTungsten", 1502, true, "gt.circuitcomponent.processed.frame.celestialtungsten", null, null);   // Spliced Celestial Tungsten Framebox
        add("ProcessedFrameboxHypogen", 1503, true, "gt.circuitcomponent.processed.frame.hypogen", null, null);   // Spliced Hypogen Framebox
        add("ProcessedFrameboxMagMatter", 1504, true, "gt.circuitcomponent.processed.frame.magmatter", null, null);   // Spliced Magmatter Framebox
        add("FoilSiliconeRubber", 1600, false, "gt.circuitcomponent.sheet.siliconerubber", "mat:silicone_rubber:foil", null);   // Packaged Silicone Rubber Foil  [真实物品名由 GTCEu 1.20.1 资源组合（NH 侧该真实物品在 1.20.1 不存在）; 未处理件的「真实物品」在 1.20.1 不存在，显示名按降级规则给出]
        add("FoilPolybenzimidazole", 1601, false, "gt.circuitcomponent.sheet.polybenzimidazole", "mat:polybenzimidazole:foil", null);   // Packaged Polybenzimidazole Foil  [真实物品名由 GTCEu 1.20.1 资源组合（NH 侧该真实物品在 1.20.1 不存在）; 未处理件的「真实物品」在 1.20.1 不存在，显示名按降级规则给出]
        add("FoilRadoxPolymer", 1602, false, "gt.circuitcomponent.sheet.radoxpolymer", null, null);   // Packaged Radox Polymer Foil  [! 真实物品名拿不到 NH 资源（降级：自撰/退化）; 未处理件的「真实物品」在 1.20.1 不存在，显示名按降级规则给出]
        add("FoilShirabon", 1603, false, "gt.circuitcomponent.sheet.shirabon", null, null);   // Packaged Shirabon Foil  [! 材质来自 GT++ 取件方法，未逐一解析（统一用 GTCEu DULL 母版）; ! 真实物品名拿不到 NH 资源（降级：自撰/退化）; 未处理件的「真实物品」在 1.20.1 不存在，显示名按降级规则给出; ! 显示名以**实际真件**为准（NH 原名：调律源金箔）]
        add("FoilStyreneRubber", 1604, false, "gt.circuitcomponent.sheet.styrenebutadienerubber", "mat:styrene_butadiene_rubber:foil", null);   // Packaged Styrene Butadiene Rubber Foil  [真实物品名由 GTCEu 1.20.1 资源组合（NH 侧该真实物品在 1.20.1 不存在）; 未处理件的「真实物品」在 1.20.1 不存在，显示名按降级规则给出]
        add("FoilEternity", 1605, false, "gt.circuitcomponent.sheet.eternity", null, null);   // Packaged Eternity Foil  [! 真实物品名拿不到 NH 资源（降级：自撰/退化）; 未处理件的「真实物品」在 1.20.1 不存在，显示名按降级规则给出]
        add("ProcessedFoilSiliconeRubber", 1700, true, "gt.circuitcomponent.sheet.processed.siliconerubber", null, null);   // Silicone Rubber Wrapper Spool
        add("ProcessedFoilPolybenzimidazole", 1701, true, "gt.circuitcomponent.sheet.processed.polybenzimidazole", null, null);   // Polybenzimidazole Wrapper Spool
        add("ProcessedFoilRadoxPolymer", 1702, true, "gt.circuitcomponent.sheet.processed.radoxpolymer", null, null);   // Radox Polymer Wrapper Spool
        add("ProcessedFoilShirabon", 1703, true, "gt.circuitcomponent.sheet.processed.shirabon", null, null);   // Shirabon Wrapper Spool
        add("ProcessedFoilStyreneRubber", 1704, true, "gt.circuitcomponent.sheet.processed.styrenerubber", null, null);   // Styrene-Butadiene Rubber Wrapper Spool
        add("ProcessedFoilEternity", 1705, true, "gt.circuitcomponent.sheet.processed.eternity", null, null);   // Eternity Wrapper Spool
        add("ProcessedBasicMainframeCasing", 1800, true, "gt.circuitcomponent.casing.processed.basicmainframe", null, null);   // Basic Mainframe Circuit Encasement  [processed 无未处理件（NH componentForProcessed = null）]
        add("ProcessedAdvancedMainframeCasing", 1801, true, "gt.circuitcomponent.casing.processed.advancedmainframe", null, null);   // Advanced Mainframe Circuit Encasement  [processed 无未处理件（NH componentForProcessed = null）]
        add("ProcessedCoiledThermalSuperconductor", 1805, true, "gt.circuitcomponent.other.processed.coiledthermalsuperconductor", null, null);   // Coiled Thermal Superconductor  [processed 无未处理件（NH componentForProcessed = null）]
        add("BoltEnrichedHolmium", 1900, false, "gt.circuitcomponent.bolt.enrichedholmium", null, null);   // Packaged Enriched Holmium Bolt  [! 真实物品名拿不到 NH 资源（降级：自撰/退化）; 未处理件的「真实物品」在 1.20.1 不存在，显示名按降级规则给出; ! 显示名以**实际真件**为准（NH 原名：富集钬螺栓）]
        add("BoltTranscendentMetal", 1901, false, "gt.circuitcomponent.bolt.transcendentmetal", null, null);   // Packaged Transcendent Metal Bolt  [! 真实物品名拿不到 NH 资源（降级：自撰/退化）; 未处理件的「真实物品」在 1.20.1 不存在，显示名按降级规则给出; ! 显示名以**实际真件**为准（NH 原名：超时空金属螺栓）]
        add("BoltYttriumBariumCuprate", 1902, false, "gt.circuitcomponent.bolt.yttriumbariumcuprate", "mat:yttrium_barium_cuprate:bolt", null);   // Packaged Yttrium Barium Cuprate Bolt  [真实物品名由 GTCEu 1.20.1 资源组合（NH 侧该真实物品在 1.20.1 不存在）; 未处理件的「真实物品」在 1.20.1 不存在，显示名按降级规则给出]
        add("BoltCosmicNeutronium", 1903, false, "gt.circuitcomponent.bolt.cosmicneutronium", null, null);   // Packaged Cosmic Neutronium Bolt  [! 真实物品名拿不到 NH 资源（降级：自撰/退化）; 未处理件的「真实物品」在 1.20.1 不存在，显示名按降级规则给出; ! 显示名以**实际真件**为准（NH 原名：宇宙中子素螺栓）]
        add("BoltChromaticGlass", 1904, false, "gt.circuitcomponent.bolt.chromaticglass", null, null);   // Packaged Chromatic Glass Bolt  [! 材质来自 GT++ 取件方法，未逐一解析（统一用 GTCEu DULL 母版）; ! 真实物品名拿不到 NH 资源（降级：自撰/退化）; 未处理件的「真实物品」在 1.20.1 不存在，显示名按降级规则给出; ! 显示名以**实际真件**为准（NH 原名：null）]
        add("BoltUMVSuperconductor", 1905, false, "gt.circuitcomponent.bolt.umvsuperconductor", null, null);   // Packaged Superconductor Base UMV Bolt  [! 真实物品名拿不到 NH 资源（降级：自撰/退化）; 未处理件的「真实物品」在 1.20.1 不存在，显示名按降级规则给出; ! NH 期望名（尾巴）不是「形状+材料」结构，未做材料词修正（原文 UMV 超导体基底螺栓；解析出的材料 id=superconductor_umv_base）; ! 显示名以**实际真件**为准（NH 原名：UMV 超导体基底螺栓）]
        add("PlateMetastableOganesson", 1906, false, "gt.circuitcomponent.plate.metastableoganesson", null, null);   // Packaged Metastable Oganesson Plate  [! 材质来自 GT++ 取件方法，未逐一解析（统一用 GTCEu DULL 母版）; ! 真实物品名拿不到 NH 资源（降级：自撰/退化）; 未处理件的「真实物品」在 1.20.1 不存在，显示名按降级规则给出; ! NH 期望名（尾巴）不是「形状+材料」结构，未做材料词修正（原文 亚稳态鿫板；解析出的材料 id=metastable）; ! 显示名以**实际真件**为准（NH 原名：亚稳态鿫板）]
        add("ScrewAstralTitanium", 1907, false, "gt.circuitcomponent.screw.astraltitanium", null, null);   // Packaged Astral Titanium Screw  [! 材质来自 GT++ 取件方法，未逐一解析（统一用 GTCEu DULL 母版）; ! 真实物品名拿不到 NH 资源（降级：自撰/退化）; 未处理件的「真实物品」在 1.20.1 不存在，显示名按降级规则给出; ! 显示名以**实际真件**为准（NH 原名：星体钛螺丝）]
        add("CasingUEVSuperconductor", 1908, false, "gt.circuitcomponent.casing.uevsuperconductor", null, null);   // Packaged Superconductor Base UEV Casing  [! 无可用真实物品贴图，退化自造底板; ! 真实物品名拿不到 NH 资源（降级：自撰/退化）; 未处理件的「真实物品」在 1.20.1 不存在，显示名按降级规则给出; ! 显示名以**实际真件**为准（NH 原名：null）]
        add("ScrewSixPhasedCopper", 1909, false, "gt.circuitcomponent.screw.sixphasedcopper", null, null);   // Packaged Six-Phased Copper Screw  [! 真实物品名拿不到 NH 资源（降级：自撰/退化）; 未处理件的「真实物品」在 1.20.1 不存在，显示名按降级规则给出; ! 显示名以**实际真件**为准（NH 原名：null）]
        add("CasingCreon", 1910, false, "gt.circuitcomponent.casing.creon", null, null);   // Packaged Creon Casing  [! 无可用真实物品贴图，退化自造底板; ! 真实物品名拿不到 NH 资源（降级：自撰/退化）; 未处理件的「真实物品」在 1.20.1 不存在，显示名按降级规则给出; ! 显示名以**实际真件**为准（NH 原名：创律合金外壳）]
        add("PlateRhugnor", 1911, false, "gt.circuitcomponent.plate.rhugnor", null, null);   // Packaged Rhugnor Plate  [! 材质来自 GT++ 取件方法，未逐一解析（统一用 GTCEu DULL 母版）; ! 真实物品名拿不到 NH 资源（降级：自撰/退化）; 未处理件的「真实物品」在 1.20.1 不存在，显示名按降级规则给出; ! 显示名以**实际真件**为准（NH 原名：鲁格诺板）]
        add("BoltWhiteDwarfMatter", 1912, false, "gt.circuitcomponent.bolt.whitedwarfmatter", null, null);   // Packaged White Dwarf Matter Bolt  [! 真实物品名拿不到 NH 资源（降级：自撰/退化）; 未处理件的「真实物品」在 1.20.1 不存在，显示名按降级规则给出; ! NH 期望名（尾巴）不是「形状+材料」结构，未做材料词修正（原文 白矮星物质螺栓；解析出的材料 id=white_dwarf_matter）; ! 显示名以**实际真件**为准（NH 原名：白矮星物质螺栓）]
        add("PlateHexanite", 1913, false, "gt.circuitcomponent.plate.hexanite", null, null);   // Packaged Hexanite Plate  [! 真实物品名拿不到 NH 资源（降级：自撰/退化）; 未处理件的「真实物品」在 1.20.1 不存在，显示名按降级规则给出; ! 显示名以**实际真件**为准（NH 原名：null）]
        add("CasingBlackDwarfMatter", 1914, false, "gt.circuitcomponent.casing.blackdwarfmatter", null, null);   // Packaged Black Dwarf Matter Casing  [! 无可用真实物品贴图，退化自造底板; ! 真实物品名拿不到 NH 资源（降级：自撰/退化）; 未处理件的「真实物品」在 1.20.1 不存在，显示名按降级规则给出; ! NH 期望名（尾巴）不是「形状+材料」结构，未做材料词修正（原文 黑矮星物质外壳；解析出的材料 id=black_dwarf_matter）; ! 显示名以**实际真件**为准（NH 原名：null）]
        add("CasingEternity", 1915, false, "gt.circuitcomponent.casing.eternity", null, null);   // Packaged Eternity Casing  [! 无可用真实物品贴图，退化自造底板; ! 真实物品名拿不到 NH 资源（降级：自撰/退化）; 未处理件的「真实物品」在 1.20.1 不存在，显示名按降级规则给出; ! 显示名以**实际真件**为准（NH 原名：null）]
        add("ScrewUniversium", 1916, false, "gt.circuitcomponent.casing.universium", null, null);   // Packaged Universium Screw  [! 真实物品名拿不到 NH 资源（降级：自撰/退化）; 未处理件的「真实物品」在 1.20.1 不存在，显示名按降级规则给出; ! 显示名以**实际真件**为准（NH 原名：null）]
        add("ThermalSuperconductor", 1917, false, "gt.circuitcomponent.thermalsuperconductor", null, null);   // Packaged Thermal Superconductor  [! 无可用真实物品贴图，退化自造底板; ! 真实物品名拿不到 NH 资源（降级：自撰/退化）; 未处理件的「真实物品」在 1.20.1 不存在，显示名按降级规则给出]
        add("ProcessedBoltEnrichedHolmium", 2000, true, "gt.circuitcomponent.bolt.processed.enrichedholmium", null, null);   // Filed Enriched Holmium Bolt
        add("ProcessedBoltTranscendentMetal", 2001, true, "gt.circuitcomponent.bolt.processed.transcendentmetal", null, null);   // Filed Transcendent Metal Bolt
        add("ProcessedBoltYttriumBariumCuprate", 2002, true, "gt.circuitcomponent.bolt.processed.yttriumbariumcuprate", null, null);   // Filed Yttrium Barium Cuprate Bolt
        add("ProcessedBoltCosmicNeutronium", 2003, true, "gt.circuitcomponent.bolt.processed.cosmicneutronium", null, null);   // Filed Cosmic Neutronium Bolt
        add("ProcessedBoltChromaticGlass", 2004, true, "gt.circuitcomponent.bolt.processed.chromaticglass", null, null);   // Filed Chromatic Glass Bolt
        add("ProcessedBoltUMVSuperconductor", 2005, true, "gt.circuitcomponent.bolt.processed.umvsuperconductor", null, null);   // Filed UMV Superconductor Base Bolt
        add("ProcessedPlateMetastableOganesson", 2006, true, "gt.circuitcomponent.plate.processed.metastableoganesson", null, null);   // Stripped Metastable Oganesson Plate
        add("ProcessedPlateRhugnor", 2007, true, "gt.circuitcomponent.plate.processed.rhugnor", null, null);   // Stripped Rhugnor Plate
        add("ProcessedBoltWhiteDwarfMatter", 2008, true, "gt.circuitcomponent.bolt.processed.whitedwarfmatter", null, null);   // Filed White Dwarf Matter Bolt
        add("ProcessedPlateHexanite", 2009, true, "gt.circuitcomponent.plate.processed.hexanite", null, null);   // Stripped Hexanite Plate
        add("CrystalProcessor", 2100, false, "gt.circuitcomponent.crystalprocessor", "item:crystal_processor", "CRYSTAL");   // Packaged Crystal Processor  [真实物品名由 GTCEu 1.20.1 资源组合（NH 侧该真实物品在 1.20.1 不存在）; 校准族 CRYSTAL（本波只登记，不做配方）; 未处理件的「真实物品」在 1.20.1 不存在，显示名按降级规则给出]
        add("CrystalAssembly", 2101, false, "gt.circuitcomponent.crystalasssembly", "item:crystal_processor_assembly", "CRYSTAL");   // Packaged Crystal Processor Assembly  [真实物品名由 GTCEu 1.20.1 资源组合（NH 侧该真实物品在 1.20.1 不存在）; 校准族 CRYSTAL（本波只登记，不做配方）; 未处理件的「真实物品」在 1.20.1 不存在，显示名按降级规则给出]
        add("CrystalComputer", 2102, false, "gt.circuitcomponent.crystalcomputer", "item:crystal_processor_computer", "CRYSTAL");   // Packaged Crystal Processor Supercomputer  [真实物品名由 GTCEu 1.20.1 资源组合（NH 侧该真实物品在 1.20.1 不存在）; 校准族 CRYSTAL（本波只登记，不做配方）; 未处理件的「真实物品」在 1.20.1 不存在，显示名按降级规则给出]
        add("CrystalMainframe", 2103, false, "gt.circuitcomponent.crystalmainframe", "item:crystal_processor_mainframe", "CRYSTAL");   // Packaged Crystal Processor Mainframe  [真实物品名由 GTCEu 1.20.1 资源组合（NH 侧该真实物品在 1.20.1 不存在）; 校准族 CRYSTAL（本波只登记，不做配方）; 未处理件的「真实物品」在 1.20.1 不存在，显示名按降级规则给出]
        add("WetwareProcessor", 2104, false, "gt.circuitcomponent.wetwareprocessor", "item:wetware_processor", "WETWARE");   // Packaged Wetware Processor  [真实物品名由 GTCEu 1.20.1 资源组合（NH 侧该真实物品在 1.20.1 不存在）; 校准族 WETWARE（本波只登记，不做配方）; 未处理件的「真实物品」在 1.20.1 不存在，显示名按降级规则给出]
        add("WetwareAssembly", 2105, false, "gt.circuitcomponent.wetwareassembly", "item:wetware_processor_assembly", "WETWARE");   // Packaged Wetware Processor Assembly  [真实物品名由 GTCEu 1.20.1 资源组合（NH 侧该真实物品在 1.20.1 不存在）; 校准族 WETWARE（本波只登记，不做配方）; 未处理件的「真实物品」在 1.20.1 不存在，显示名按降级规则给出]
        add("WetwareComputer", 2106, false, "gt.circuitcomponent.wetwarecomputer", "item:wetware_processor_computer", "WETWARE");   // Packaged Wetware Processor Supercomputer  [真实物品名由 GTCEu 1.20.1 资源组合（NH 侧该真实物品在 1.20.1 不存在）; 校准族 WETWARE（本波只登记，不做配方）; 未处理件的「真实物品」在 1.20.1 不存在，显示名按降级规则给出]
        add("WetwareMainframe", 2107, false, "gt.circuitcomponent.wetwaremainframe", "item:wetware_processor_mainframe", "WETWARE");   // Packaged Wetware Processor Mainframe  [真实物品名由 GTCEu 1.20.1 资源组合（NH 侧该真实物品在 1.20.1 不存在）; 校准族 WETWARE（本波只登记，不做配方）; 未处理件的「真实物品」在 1.20.1 不存在，显示名按降级规则给出]
        add("BiowareProcessor", 2108, false, "gt.circuitcomponent.biowareprocessor", null, "BIO");   // Packaged Bioware Processor  [! 无可用真实物品贴图，退化自造底板; ! 真实物品名拿不到 NH 资源（降级：自撰/退化）; 校准族 BIO（本波只登记，不做配方）; 未处理件的「真实物品」在 1.20.1 不存在，显示名按降级规则给出; ! NH 期望名（尾巴）不是「形状+材料」结构，未做材料词修正（原文 生物处理器；解析出的材料 id=bioware_processor）; ! 显示名以**实际真件**为准（NH 原名：生物处理器）]
        add("BiowareAssembly", 2109, false, "gt.circuitcomponent.biowareassembly", null, "BIO");   // Packaged Bioware Assembly  [! 无可用真实物品贴图，退化自造底板; ! 真实物品名拿不到 NH 资源（降级：自撰/退化）; 校准族 BIO（本波只登记，不做配方）; 未处理件的「真实物品」在 1.20.1 不存在，显示名按降级规则给出; ! NH 期望名（尾巴）不是「形状+材料」结构，未做材料词修正（原文 生物组装电路；解析出的材料 id=bioware_assembly）; ! 显示名以**实际真件**为准（NH 原名：生物组装电路）]
        add("BiowareComputer", 2110, false, "gt.circuitcomponent.biowarecomputer", null, "BIO");   // Packaged Bioware Computer  [! 无可用真实物品贴图，退化自造底板; ! 真实物品名拿不到 NH 资源（降级：自撰/退化）; 校准族 BIO（本波只登记，不做配方）; 未处理件的「真实物品」在 1.20.1 不存在，显示名按降级规则给出; ! NH 期望名（尾巴）不是「形状+材料」结构，未做材料词修正（原文 生物计算机；解析出的材料 id=bioware_computer）; ! 显示名以**实际真件**为准（NH 原名：生物计算机）]
        add("BiowareMainframe", 2111, false, "gt.circuitcomponent.biowaremainframe", null, "BIO");   // Packaged Bioware Mainframe  [! 无可用真实物品贴图，退化自造底板; ! 真实物品名拿不到 NH 资源（降级：自撰/退化）; 校准族 BIO（本波只登记，不做配方）; 未处理件的「真实物品」在 1.20.1 不存在，显示名按降级规则给出; ! NH 期望名（尾巴）不是「形状+材料」结构，未做材料词修正（原文 生物主机；解析出的材料 id=bioware_mainframe）; ! 显示名以**实际真件**为准（NH 原名：生物主机）]
        add("OpticalProcessor", 2112, false, "gt.circuitcomponent.opticalprocessor", null, "OPTICAL");   // Packaged Optical Processor  [! 无可用真实物品贴图，退化自造底板; ! 真实物品名拿不到 NH 资源（降级：自撰/退化）; 校准族 OPTICAL（本波只登记，不做配方）; 未处理件的「真实物品」在 1.20.1 不存在，显示名按降级规则给出; ! NH 期望名（尾巴）不是「形状+材料」结构，未做材料词修正（原文 光学处理器；解析出的材料 id=optical_processor）; ! 显示名以**实际真件**为准（NH 原名：null）]
        add("OpticalAssembly", 2113, false, "gt.circuitcomponent.opticalassembly", null, "OPTICAL");   // Packaged Optical Assembly  [! 无可用真实物品贴图，退化自造底板; ! 真实物品名拿不到 NH 资源（降级：自撰/退化）; 校准族 OPTICAL（本波只登记，不做配方）; 未处理件的「真实物品」在 1.20.1 不存在，显示名按降级规则给出; ! NH 期望名（尾巴）不是「形状+材料」结构，未做材料词修正（原文 光学组装电路；解析出的材料 id=optical_assembly）; ! 显示名以**实际真件**为准（NH 原名：光学组装电路）]
        add("OpticalComputer", 2114, false, "gt.circuitcomponent.opticalcomputer", null, "OPTICAL");   // Packaged Optical Computer  [! 无可用真实物品贴图，退化自造底板; ! 真实物品名拿不到 NH 资源（降级：自撰/退化）; 校准族 OPTICAL（本波只登记，不做配方）; 未处理件的「真实物品」在 1.20.1 不存在，显示名按降级规则给出; ! NH 期望名（尾巴）不是「形状+材料」结构，未做材料词修正（原文 光学计算机；解析出的材料 id=optical_computer）; ! 显示名以**实际真件**为准（NH 原名：光学计算机）]
        add("OpticalMainframe", 2115, false, "gt.circuitcomponent.opticalmainframe", null, "OPTICAL");   // Packaged Optical Mainframe  [! 无可用真实物品贴图，退化自造底板; ! 真实物品名拿不到 NH 资源（降级：自撰/退化）; 校准族 OPTICAL（本波只登记，不做配方）; 未处理件的「真实物品」在 1.20.1 不存在，显示名按降级规则给出; ! NH 期望名（尾巴）不是「形状+材料」结构，未做材料词修正（原文 光学主机；解析出的材料 id=optical_mainframe）; ! 显示名以**实际真件**为准（NH 原名：光学主机）]
        add("PolyetheretherketoneFoil", 2200, false, "gtlmodular.cc.cc_polyetheretherketone_foil.name", "mat:polyetheretherketone:foil", null);   // Packaged Polyetheretherketone Foil  [wave2]
        add("ProcessedPolyetheretherketoneFoil", 2201, true, "gtlmodular.cc.cc_processed_polyetheretherketone_foil.name", "mat:polyetheretherketone:foil", null);   // Polyetheretherketone Wrapper Spool  [wave2]
        add("ExoticRamChip", 2202, false, "gtlmodular.cc.cc_exotic_ram_chip.name", "itemns:kubejs:exotic_ram_chip", "EXOTIC");   // Packaged Exotic RAM Chip  [wave2; ! 无书面 en 证据（kubejs 侧只有 zh_cn）]
        add("ProcessedExoticRamChip", 2203, true, "gtlmodular.cc.cc_processed_exotic_ram_chip.name", "itemns:kubejs:exotic_ram_chip", "EXOTIC");   // Cut Exotic RAM Chip  [wave2; ! 无书面 en 证据（kubejs 侧只有 zh_cn）]
        add("HighlyAdvancedSoc", 2204, false, "gtlmodular.cc.cc_highly_advanced_soc.name", "item:highly_advanced_soc", null);   // Packaged HASoC  [wave2]
        add("ProcessedHighlyAdvancedSoc", 2205, true, "gtlmodular.cc.cc_processed_highly_advanced_soc.name", "item:highly_advanced_soc", null);   // Cut HASoC  [wave2]
        add("SmdCapacitorBioware", 2206, false, "gtlmodular.cc.cc_smd_capacitor_bioware.name", "itemns:kubejs:smd_capacitor_bioware", "BIOWARE");   // Packaged SMD Capacitor Bioware  [wave2; ! 无书面 en 证据（kubejs 侧只有 zh_cn）]
        add("ProcessedSmdCapacitorBioware", 2207, true, "gtlmodular.cc.cc_processed_smd_capacitor_bioware.name", "itemns:kubejs:smd_capacitor_bioware", "BIOWARE");   // Prepared SMD Capacitor Bioware  [wave2; ! 无书面 en 证据（kubejs 侧只有 zh_cn）]
        add("SmdTransistorBioware", 2208, false, "gtlmodular.cc.cc_smd_transistor_bioware.name", "itemns:kubejs:smd_transistor_bioware", "BIOWARE");   // Packaged SMD Transistor Bioware  [wave2; ! 无书面 en 证据（kubejs 侧只有 zh_cn）]
        add("ProcessedSmdTransistorBioware", 2209, true, "gtlmodular.cc.cc_processed_smd_transistor_bioware.name", "itemns:kubejs:smd_transistor_bioware", "BIOWARE");   // Prepared SMD Transistor Bioware  [wave2; ! 无书面 en 证据（kubejs 侧只有 zh_cn）]
        add("SmdCapacitorExotic", 2210, false, "gtlmodular.cc.cc_smd_capacitor_exotic.name", "itemns:kubejs:smd_capacitor_exotic", "EXOTIC");   // Packaged SMD Capacitor Exotic  [wave2; ! 无书面 en 证据（kubejs 侧只有 zh_cn）]
        add("ProcessedSmdCapacitorExotic", 2211, true, "gtlmodular.cc.cc_processed_smd_capacitor_exotic.name", "itemns:kubejs:smd_capacitor_exotic", "EXOTIC");   // Prepared SMD Capacitor Exotic  [wave2; ! 无书面 en 证据（kubejs 侧只有 zh_cn）]
        add("SmdTransistorExotic", 2212, false, "gtlmodular.cc.cc_smd_transistor_exotic.name", "itemns:kubejs:smd_transistor_exotic", "EXOTIC");   // Packaged SMD Transistor Exotic  [wave2; ! 无书面 en 证据（kubejs 侧只有 zh_cn）]
        add("ProcessedSmdTransistorExotic", 2213, true, "gtlmodular.cc.cc_processed_smd_transistor_exotic.name", "itemns:kubejs:smd_transistor_exotic", "EXOTIC");   // Prepared SMD Transistor Exotic  [wave2; ! 无书面 en 证据（kubejs 侧只有 zh_cn）]
        add("FineCinobiteWire", 2214, false, "gtlmodular.cc.cc_fine_cinobite_wire.name", "mat:cinobite:fine_wire", null);   // Packaged Fine Cinobite Wire  [wave2]
        add("ProcessedFineCinobiteWire", 2215, true, "gtlmodular.cc.cc_processed_fine_cinobite_wire.name", "mat:cinobite:fine_wire", null);   // Spooled Cinobite Wire  [wave2]
        add("UnstableStar", 2216, false, "gtlmodular.cc.cc_unstable_star.name", "itemns:kubejs:unstable_star", null);   // Packaged Unstable Star  [wave2; ! 无书面 en 证据（kubejs 侧只有 zh_cn）]
        add("ProcessedUnstableStar", 2217, true, "gtlmodular.cc.cc_processed_unstable_star.name", "itemns:kubejs:unstable_star", null);   // Coordinated Unstable Star  [wave2; ! 无书面 en 证据（kubejs 侧只有 zh_cn）]
        add("CosmicRamChip", 2218, false, "gtlmodular.cc.cc_cosmic_ram_chip.name", "itemns:kubejs:cosmic_ram_chip", "COSMIC");   // Packaged Cosmic RAM Chip  [wave2; ! 无书面 en 证据（kubejs 侧只有 zh_cn）]
        add("ProcessedCosmicRamChip", 2219, true, "gtlmodular.cc.cc_processed_cosmic_ram_chip.name", "itemns:kubejs:cosmic_ram_chip", "COSMIC");   // Cut Cosmic RAM Chip  [wave2; ! 无书面 en 证据（kubejs 侧只有 zh_cn）]
        add("SmdCapacitorCosmic", 2220, false, "gtlmodular.cc.cc_smd_capacitor_cosmic.name", "itemns:kubejs:smd_capacitor_cosmic", "COSMIC");   // Packaged SMD Capacitor Cosmic  [wave2; ! 无书面 en 证据（kubejs 侧只有 zh_cn）]
        add("ProcessedSmdCapacitorCosmic", 2221, true, "gtlmodular.cc.cc_processed_smd_capacitor_cosmic.name", "itemns:kubejs:smd_capacitor_cosmic", "COSMIC");   // Prepared SMD Capacitor Cosmic  [wave2; ! 无书面 en 证据（kubejs 侧只有 zh_cn）]
        add("SmdTransistorCosmic", 2222, false, "gtlmodular.cc.cc_smd_transistor_cosmic.name", "itemns:kubejs:smd_transistor_cosmic", "COSMIC");   // Packaged SMD Transistor Cosmic  [wave2; ! 无书面 en 证据（kubejs 侧只有 zh_cn）]
        add("ProcessedSmdTransistorCosmic", 2223, true, "gtlmodular.cc.cc_processed_smd_transistor_cosmic.name", "itemns:kubejs:smd_transistor_cosmic", "COSMIC");   // Prepared SMD Transistor Cosmic  [wave2; ! 无书面 en 证据（kubejs 侧只有 zh_cn）]
        add("FineNaquadahWire", 2224, false, "gtlmodular.cc.cc_fine_naquadah_wire.name", "mat:naquadah:fine_wire", null);   // Packaged Fine Naquadah Wire  [wave2]
        add("ProcessedFineNaquadahWire", 2225, true, "gtlmodular.cc.cc_processed_fine_naquadah_wire.name", "mat:naquadah:fine_wire", null);   // Spooled Naquadah Wire  [wave2]
        add("SmdDiodeBioware", 2226, false, "gtlmodular.cc.cc_smd_diode_bioware.name", "itemns:kubejs:smd_diode_bioware", "BIOWARE");   // Packaged SMD Diode Bioware  [wave2; ! 无书面 en 证据（kubejs 侧只有 zh_cn）]
        add("ProcessedSmdDiodeBioware", 2227, true, "gtlmodular.cc.cc_processed_smd_diode_bioware.name", "itemns:kubejs:smd_diode_bioware", "BIOWARE");   // Prepared SMD Diode Bioware  [wave2; ! 无书面 en 证据（kubejs 侧只有 zh_cn）]
        add("SmdResistorBioware", 2228, false, "gtlmodular.cc.cc_smd_resistor_bioware.name", "itemns:kubejs:smd_resistor_bioware", "BIOWARE");   // Packaged SMD Resistor Bioware  [wave2; ! 无书面 en 证据（kubejs 侧只有 zh_cn）]
        add("ProcessedSmdResistorBioware", 2229, true, "gtlmodular.cc.cc_processed_smd_resistor_bioware.name", "itemns:kubejs:smd_resistor_bioware", "BIOWARE");   // Prepared SMD Resistor Bioware  [wave2; ! 无书面 en 证据（kubejs 侧只有 zh_cn）]
        add("SmdInductorBioware", 2230, false, "gtlmodular.cc.cc_smd_inductor_bioware.name", "itemns:kubejs:smd_inductor_bioware", "BIOWARE");   // Packaged SMD Inductor Bioware  [wave2; ! 无书面 en 证据（kubejs 侧只有 zh_cn）]
        add("ProcessedSmdInductorBioware", 2231, true, "gtlmodular.cc.cc_processed_smd_inductor_bioware.name", "itemns:kubejs:smd_inductor_bioware", "BIOWARE");   // Prepared SMD Inductor Bioware  [wave2; ! 无书面 en 证据（kubejs 侧只有 zh_cn）]
        add("GraviStar", 2232, false, "gtlmodular.cc.cc_gravi_star.name", "item:gravi_star", null);   // Packaged Gravi-Star  [wave2]
        add("ProcessedGraviStar", 2233, true, "gtlmodular.cc.cc_processed_gravi_star.name", "item:gravi_star", null);   // Coordinated Gravi-Star  [wave2]
        add("FineDubniumWire", 2234, false, "gtlmodular.cc.cc_fine_dubnium_wire.name", "mat:dubnium:fine_wire", null);   // Packaged Fine Dubnium Wire  [wave2]
        add("ProcessedFineDubniumWire", 2235, true, "gtlmodular.cc.cc_processed_fine_dubnium_wire.name", "mat:dubnium:fine_wire", null);   // Spooled Dubnium Wire  [wave2]
        add("SmdDiodeExotic", 2236, false, "gtlmodular.cc.cc_smd_diode_exotic.name", "itemns:kubejs:smd_diode_exotic", "EXOTIC");   // Packaged SMD Diode Exotic  [wave2; ! 无书面 en 证据（kubejs 侧只有 zh_cn）]
        add("ProcessedSmdDiodeExotic", 2237, true, "gtlmodular.cc.cc_processed_smd_diode_exotic.name", "itemns:kubejs:smd_diode_exotic", "EXOTIC");   // Prepared SMD Diode Exotic  [wave2; ! 无书面 en 证据（kubejs 侧只有 zh_cn）]
        add("SmdResistorExotic", 2238, false, "gtlmodular.cc.cc_smd_resistor_exotic.name", "itemns:kubejs:smd_resistor_exotic", "EXOTIC");   // Packaged SMD Resistor Exotic  [wave2; ! 无书面 en 证据（kubejs 侧只有 zh_cn）]
        add("ProcessedSmdResistorExotic", 2239, true, "gtlmodular.cc.cc_processed_smd_resistor_exotic.name", "itemns:kubejs:smd_resistor_exotic", "EXOTIC");   // Prepared SMD Resistor Exotic  [wave2; ! 无书面 en 证据（kubejs 侧只有 zh_cn）]
        add("SmdInductorExotic", 2240, false, "gtlmodular.cc.cc_smd_inductor_exotic.name", "itemns:kubejs:smd_inductor_exotic", "EXOTIC");   // Packaged SMD Inductor Exotic  [wave2; ! 无书面 en 证据（kubejs 侧只有 zh_cn）]
        add("ProcessedSmdInductorExotic", 2241, true, "gtlmodular.cc.cc_processed_smd_inductor_exotic.name", "itemns:kubejs:smd_inductor_exotic", "EXOTIC");   // Prepared SMD Inductor Exotic  [wave2; ! 无书面 en 证据（kubejs 侧只有 zh_cn）]
        add("SmdDiodeCosmic", 2242, false, "gtlmodular.cc.cc_smd_diode_cosmic.name", "itemns:kubejs:smd_diode_cosmic", "COSMIC");   // Packaged SMD Diode Cosmic  [wave2; ! 无书面 en 证据（kubejs 侧只有 zh_cn）]
        add("ProcessedSmdDiodeCosmic", 2243, true, "gtlmodular.cc.cc_processed_smd_diode_cosmic.name", "itemns:kubejs:smd_diode_cosmic", "COSMIC");   // Prepared SMD Diode Cosmic  [wave2; ! 无书面 en 证据（kubejs 侧只有 zh_cn）]
        add("SmdResistorCosmic", 2244, false, "gtlmodular.cc.cc_smd_resistor_cosmic.name", "itemns:kubejs:smd_resistor_cosmic", "COSMIC");   // Packaged SMD Resistor Cosmic  [wave2; ! 无书面 en 证据（kubejs 侧只有 zh_cn）]
        add("ProcessedSmdResistorCosmic", 2245, true, "gtlmodular.cc.cc_processed_smd_resistor_cosmic.name", "itemns:kubejs:smd_resistor_cosmic", "COSMIC");   // Prepared SMD Resistor Cosmic  [wave2; ! 无书面 en 证据（kubejs 侧只有 zh_cn）]
        add("SmdInductorCosmic", 2246, false, "gtlmodular.cc.cc_smd_inductor_cosmic.name", "itemns:kubejs:smd_inductor_cosmic", "COSMIC");   // Packaged SMD Inductor Cosmic  [wave2; ! 无书面 en 证据（kubejs 侧只有 zh_cn）]
        add("ProcessedSmdInductorCosmic", 2247, true, "gtlmodular.cc.cc_processed_smd_inductor_cosmic.name", "itemns:kubejs:smd_inductor_cosmic", "COSMIC");   // Prepared SMD Inductor Cosmic  [wave2; ! 无书面 en 证据（kubejs 侧只有 zh_cn）]
        add("SmdCapacitorSupracausal", 2248, false, "gtlmodular.cc.cc_smd_capacitor_supracausal.name", "itemns:kubejs:smd_capacitor_supracausal", "SUPRACAUSAL");   // Packaged SMD Capacitor Supracausal  [wave2; ! 无书面 en 证据（kubejs 侧只有 zh_cn）]
        add("ProcessedSmdCapacitorSupracausal", 2249, true, "gtlmodular.cc.cc_processed_smd_capacitor_supracausal.name", "itemns:kubejs:smd_capacitor_supracausal", "SUPRACAUSAL");   // Prepared SMD Capacitor Supracausal  [wave2; ! 无书面 en 证据（kubejs 侧只有 zh_cn）]
        add("SmdDiodeSupracausal", 2250, false, "gtlmodular.cc.cc_smd_diode_supracausal.name", "itemns:kubejs:smd_diode_supracausal", "SUPRACAUSAL");   // Packaged SMD Diode Supracausal  [wave2; ! 无书面 en 证据（kubejs 侧只有 zh_cn）]
        add("ProcessedSmdDiodeSupracausal", 2251, true, "gtlmodular.cc.cc_processed_smd_diode_supracausal.name", "itemns:kubejs:smd_diode_supracausal", "SUPRACAUSAL");   // Prepared SMD Diode Supracausal  [wave2; ! 无书面 en 证据（kubejs 侧只有 zh_cn）]
        add("SmdResistorSupracausal", 2252, false, "gtlmodular.cc.cc_smd_resistor_supracausal.name", "itemns:kubejs:smd_resistor_supracausal", "SUPRACAUSAL");   // Packaged SMD Resistor Supracausal  [wave2; ! 无书面 en 证据（kubejs 侧只有 zh_cn）]
        add("ProcessedSmdResistorSupracausal", 2253, true, "gtlmodular.cc.cc_processed_smd_resistor_supracausal.name", "itemns:kubejs:smd_resistor_supracausal", "SUPRACAUSAL");   // Prepared SMD Resistor Supracausal  [wave2; ! 无书面 en 证据（kubejs 侧只有 zh_cn）]
        add("SmdTransistorSupracausal", 2254, false, "gtlmodular.cc.cc_smd_transistor_supracausal.name", "itemns:kubejs:smd_transistor_supracausal", "SUPRACAUSAL");   // Packaged SMD Transistor Supracausal  [wave2; ! 无书面 en 证据（kubejs 侧只有 zh_cn）]
        add("ProcessedSmdTransistorSupracausal", 2255, true, "gtlmodular.cc.cc_processed_smd_transistor_supracausal.name", "itemns:kubejs:smd_transistor_supracausal", "SUPRACAUSAL");   // Prepared SMD Transistor Supracausal  [wave2; ! 无书面 en 证据（kubejs 侧只有 zh_cn）]
        add("SmdInductorSupracausal", 2256, false, "gtlmodular.cc.cc_smd_inductor_supracausal.name", "itemns:kubejs:smd_inductor_supracausal", "SUPRACAUSAL");   // Packaged SMD Inductor Supracausal  [wave2; ! 无书面 en 证据（kubejs 侧只有 zh_cn）]
        add("ProcessedSmdInductorSupracausal", 2257, true, "gtlmodular.cc.cc_processed_smd_inductor_supracausal.name", "itemns:kubejs:smd_inductor_supracausal", "SUPRACAUSAL");   // Prepared SMD Inductor Supracausal  [wave2; ! 无书面 en 证据（kubejs 侧只有 zh_cn）]
        add("UhpicChip", 2258, false, "gtlmodular.cc.cc_uhpic_chip.name", "item:uhpic_chip", null);   // Packaged UHPIC Chip  [wave2]
        add("ProcessedUhpicChip", 2259, true, "gtlmodular.cc.cc_processed_uhpic_chip.name", "item:uhpic_chip", null);   // Cut UHPIC Chip  [wave2]
        add("NmChip", 2260, false, "gtlmodular.cc.cc_nm_chip.name", "itemns:kubejs:nm_chip", null);   // Packaged Nm Chip  [wave2; ! 无书面 en 证据（kubejs 侧只有 zh_cn）]
        add("ProcessedNmChip", 2261, true, "gtlmodular.cc.cc_processed_nm_chip.name", "itemns:kubejs:nm_chip", null);   // Cut Nm Chip  [wave2; ! 无书面 en 证据（kubejs 侧只有 zh_cn）]
        add("ExoticPrintedCircuitBoard", 2262, false, "gtlmodular.cc.cc_exotic_printed_circuit_board.name", "itemns:kubejs:exotic_printed_circuit_board", "EXOTIC");   // Packaged Exotic Printed Circuit Board  [wave2; ! 无书面 en 证据（kubejs 侧只有 zh_cn）]
        add("ProcessedExoticPrintedCircuitBoard", 2263, true, "gtlmodular.cc.cc_processed_exotic_printed_circuit_board.name", "itemns:kubejs:exotic_printed_circuit_board", "EXOTIC");   // Purified Exotic Printed Circuit Board  [wave2; ! 无书面 en 证据（kubejs 侧只有 zh_cn）]
        add("CosmicPrintedCircuitBoard", 2264, false, "gtlmodular.cc.cc_cosmic_printed_circuit_board.name", "itemns:kubejs:cosmic_printed_circuit_board", "COSMIC");   // Packaged Cosmic Printed Circuit Board  [wave2; ! 无书面 en 证据（kubejs 侧只有 zh_cn）]
        add("ProcessedCosmicPrintedCircuitBoard", 2265, true, "gtlmodular.cc.cc_processed_cosmic_printed_circuit_board.name", "itemns:kubejs:cosmic_printed_circuit_board", "COSMIC");   // Purified Cosmic Printed Circuit Board  [wave2; ! 无书面 en 证据（kubejs 侧只有 zh_cn）]
        add("FmChip", 2266, false, "gtlmodular.cc.cc_fm_chip.name", "itemns:kubejs:fm_chip", null);   // Packaged Fm Chip  [wave2; ! 无书面 en 证据（kubejs 侧只有 zh_cn）]
        add("ProcessedFmChip", 2267, true, "gtlmodular.cc.cc_processed_fm_chip.name", "itemns:kubejs:fm_chip", null);   // Cut Fm Chip  [wave2; ! 无书面 en 证据（kubejs 侧只有 zh_cn）]
        add("FullerenePolymerMatrixPulpFoil", 2268, false, "gtlmodular.cc.cc_fullerene_polymer_matrix_pulp_foil.name", "mat:fullerene_polymer_matrix_pulp:foil", null);   // Packaged Fullerene Polymer Matrix Pulp Foil  [wave2]
        add("ProcessedFullerenePolymerMatrixPulpFoil", 2269, true, "gtlmodular.cc.cc_processed_fullerene_polymer_matrix_pulp_foil.name", "mat:fullerene_polymer_matrix_pulp:foil", null);   // Fullerene Polymer Matrix Pulp Wrapper Spool  [wave2]
        add("SupracausalRamChip", 2270, false, "gtlmodular.cc.cc_supracausal_ram_chip.name", "itemns:kubejs:supracausal_ram_chip", "SUPRACAUSAL");   // Packaged Supracausal RAM Chip  [wave2; ! 无书面 en 证据（kubejs 侧只有 zh_cn）]
        add("ProcessedSupracausalRamChip", 2271, true, "gtlmodular.cc.cc_processed_supracausal_ram_chip.name", "itemns:kubejs:supracausal_ram_chip", "SUPRACAUSAL");   // Cut Supracausal RAM Chip  [wave2; ! 无书面 en 证据（kubejs 侧只有 zh_cn）]
        add("CosmicProcessingUnitCore", 2272, false, "gtlmodular.cc.cc_cosmic_processing_unit_core.name", "itemns:kubejs:cosmic_processing_unit_core", "COSMIC");   // Packaged Cosmic Processing Unit Core  [wave2; ! 无书面 en 证据（kubejs 侧只有 zh_cn）]
        add("ProcessedCosmicProcessingUnitCore", 2273, true, "gtlmodular.cc.cc_processed_cosmic_processing_unit_core.name", "itemns:kubejs:cosmic_processing_unit_core", "COSMIC");   // Coordinated Cosmic Processing Unit Core  [wave2; ! 无书面 en 证据（kubejs 侧只有 zh_cn）]
        add("SupracausalPrintedCircuitBoard", 2274, false, "gtlmodular.cc.cc_supracausal_printed_circuit_board.name", "itemns:kubejs:supracausal_printed_circuit_board", "SUPRACAUSAL");   // Packaged Supracausal Printed Circuit Board  [wave2; ! 无书面 en 证据（kubejs 侧只有 zh_cn）]
        add("ProcessedSupracausalPrintedCircuitBoard", 2275, true, "gtlmodular.cc.cc_processed_supracausal_printed_circuit_board.name", "itemns:kubejs:supracausal_printed_circuit_board", "SUPRACAUSAL");   // Purified Supracausal Printed Circuit Board  [wave2; ! 无书面 en 证据（kubejs 侧只有 zh_cn）]
        add("TriniumTitaniumPlate", 2276, false, "gtlmodular.cc.cc_trinium_titanium_plate.name", "mat:trinium_titanium:plate", null);   // Packaged Trinium Titanium Plate  [wave2]
        add("ProcessedTriniumTitaniumPlate", 2277, true, "gtlmodular.cc.cc_processed_trinium_titanium_plate.name", "mat:trinium_titanium:plate", null);   // Stripped Trinium Titanium Plate  [wave2]
        add("NuclearStar", 2278, false, "gtlmodular.cc.cc_nuclear_star.name", "itemns:kubejs:nuclear_star", null);   // Packaged Nuclear Star  [wave2; ! 无书面 en 证据（kubejs 侧只有 zh_cn）]
        add("ProcessedNuclearStar", 2279, true, "gtlmodular.cc.cc_processed_nuclear_star.name", "itemns:kubejs:nuclear_star", null);   // Coordinated Nuclear Star  [wave2; ! 无书面 en 证据（kubejs 侧只有 zh_cn）]
        add("QbitCpuChip", 2280, false, "gtlmodular.cc.cc_qbit_cpu_chip.name", "item:qbit_cpu_chip", null);   // Packaged Qubit CPU Chip  [wave2]
        add("ProcessedQbitCpuChip", 2281, true, "gtlmodular.cc.cc_processed_qbit_cpu_chip.name", "item:qbit_cpu_chip", null);   // Cut Qubit CPU Chip  [wave2]
        add("DuraniumPlate", 2282, false, "gtlmodular.cc.cc_duranium_plate.name", "mat:duranium:plate", null);   // Packaged Duranium Plate  [wave2]
        add("ProcessedDuraniumPlate", 2283, true, "gtlmodular.cc.cc_processed_duranium_plate.name", "mat:duranium:plate", null);   // Stripped Duranium Plate  [wave2]
        add("NeutroniumPlate", 2284, false, "gtlmodular.cc.cc_neutronium_plate.name", "mat:neutronium:plate", null);   // Packaged Neutronium Plate  [wave2]
        add("ProcessedNeutroniumPlate", 2285, true, "gtlmodular.cc.cc_processed_neutronium_plate.name", "mat:neutronium:plate", null);   // Stripped Neutronium Plate  [wave2]
        add("RutheniumTriniumAmericiumNeutronateDoubleWire", 2286, false, "gtlmodular.cc.cc_ruthenium_trinium_americium_neutronate_double_wire.name", "mat:ruthenium_trinium_americium_neutronate:wire_gt_double", null);   // Packaged 2x Ruthenium Trinium Americium Neutronate Wire  [wave2]
        add("ProcessedRutheniumTriniumAmericiumNeutronateDoubleWire", 2287, true, "gtlmodular.cc.cc_processed_ruthenium_trinium_americium_neutronate_double_wire.name", "mat:ruthenium_trinium_americium_neutronate:wire_gt_double", null);   // Spooled Ruthenium Trinium Americium Neutronate Wire  [wave2]
        add("MithrilPlate", 2288, false, "gtlmodular.cc.cc_mithril_plate.name", "mat:mithril:plate", null);   // Packaged Mithril Plate  [wave2]
        add("ProcessedMithrilPlate", 2289, true, "gtlmodular.cc.cc_processed_mithril_plate.name", "mat:mithril:plate", null);   // Stripped Mithril Plate  [wave2]
        add("HighurabilityompoundteelPlate", 2290, false, "gtlmodular.cc.cc_highurabilityompoundteel_plate.name", "mat:highurabilityompoundteel:plate", null);   // Packaged High Durability Compound Steel Plate  [wave2]
        add("ProcessedHighurabilityompoundteelPlate", 2291, true, "gtlmodular.cc.cc_processed_highurabilityompoundteel_plate.name", "mat:highurabilityompoundteel:plate", null);   // Stripped High Durability Compound Steel Plate  [wave2]
        add("SeaborgiumPlate", 2292, false, "gtlmodular.cc.cc_seaborgium_plate.name", "mat:seaborgium:plate", null);   // Packaged Seaborgium Plate  [wave2]
        add("ProcessedSeaborgiumPlate", 2293, true, "gtlmodular.cc.cc_processed_seaborgium_plate.name", "mat:seaborgium:plate", null);   // Stripped Seaborgium Plate  [wave2]
        add("QuantaniumFrame", 2294, false, "gtlmodular.cc.cc_quantanium_frame.name", "mat:quantanium:frame", null);   // Packaged Quantanium Frame  [wave2]
        add("ProcessedQuantaniumFrame", 2295, true, "gtlmodular.cc.cc_processed_quantanium_frame.name", "mat:quantanium:frame", null);   // Spliced Quantanium Frame  [wave2]
        add("EnderiteDoubleWire", 2296, false, "gtlmodular.cc.cc_enderite_double_wire.name", "mat:enderite:wire_gt_double", null);   // Packaged 2x Enderite Wire  [wave2]
        add("ProcessedEnderiteDoubleWire", 2297, true, "gtlmodular.cc.cc_processed_enderite_double_wire.name", "mat:enderite:wire_gt_double", null);   // Spooled Enderite Wire  [wave2]
        add("TitansteelPlate", 2298, false, "gtlmodular.cc.cc_titansteel_plate.name", "mat:titansteel:plate", null);   // Packaged Titansteel Plate  [wave2]
        add("ProcessedTitansteelPlate", 2299, true, "gtlmodular.cc.cc_processed_titansteel_plate.name", "mat:titansteel:plate", null);   // Stripped Titansteel Plate  [wave2]
        add("ExoticProcessingCore", 2300, false, "gtlmodular.cc.cc_exotic_processing_core.name", "itemns:kubejs:exotic_processing_core", "EXOTIC");   // Packaged Exotic Processing Core  [wave2; ! 无书面 en 证据（kubejs 侧只有 zh_cn）]
        add("ProcessedExoticProcessingCore", 2301, true, "gtlmodular.cc.cc_processed_exotic_processing_core.name", "itemns:kubejs:exotic_processing_core", "EXOTIC");   // Coordinated Exotic Processing Core  [wave2; ! 无书面 en 证据（kubejs 侧只有 zh_cn）]
        add("ExoticProcessor", 2302, false, "gtlmodular.cc.cc_exotic_processor.name", "itemns:kubejs:exotic_processor", "EXOTIC");   // Packaged Exotic Processor  [wave2; ! 无书面 en 证据（kubejs 侧只有 zh_cn）]
        add("DraconicCore", 2303, false, "gtlmodular.cc.cc_draconic_core.name", "itemns:kubejs:draconic_core", null);   // Packaged Draconic Core  [wave2; ! 无书面 en 证据（kubejs 侧只有 zh_cn）]
        add("ProcessedDraconicCore", 2304, true, "gtlmodular.cc.cc_processed_draconic_core.name", "itemns:kubejs:draconic_core", null);   // Coordinated Draconic Core  [wave2; ! 无书面 en 证据（kubejs 侧只有 zh_cn）]
        add("InfuscoliumPlate", 2305, false, "gtlmodular.cc.cc_infuscolium_plate.name", "mat:infuscolium:plate", null);   // Packaged Infuscolium Plate  [wave2]
        add("ProcessedInfuscoliumPlate", 2306, true, "gtlmodular.cc.cc_processed_infuscolium_plate.name", "mat:infuscolium:plate", null);   // Stripped Infuscolium Plate  [wave2]
        add("ExoticAssembly", 2307, false, "gtlmodular.cc.cc_exotic_assembly.name", "itemns:kubejs:exotic_assembly", "EXOTIC");   // Packaged Exotic Assembly  [wave2; ! 无书面 en 证据（kubejs 侧只有 zh_cn）]
        add("AdamantinePlate", 2308, false, "gtlmodular.cc.cc_adamantine_plate.name", "mat:adamantine:plate", null);   // Packaged Adamantine Plate  [wave2]
        add("ProcessedAdamantinePlate", 2309, true, "gtlmodular.cc.cc_processed_adamantine_plate.name", "mat:adamantine:plate", null);   // Stripped Adamantine Plate  [wave2]
        add("AdamantiumFrame", 2310, false, "gtlmodular.cc.cc_adamantium_frame.name", "mat:adamantium:frame", null);   // Packaged Adamantium Frame  [wave2]
        add("ProcessedAdamantiumFrame", 2311, true, "gtlmodular.cc.cc_processed_adamantium_frame.name", "mat:adamantium:frame", null);   // Spliced Adamantium Frame  [wave2]
        add("ExoticComputer", 2312, false, "gtlmodular.cc.cc_exotic_computer.name", "itemns:kubejs:exotic_computer", "EXOTIC");   // Packaged Exotic Computer  [wave2; ! 无书面 en 证据（kubejs 侧只有 zh_cn）]
        add("DataStick", 2313, false, "gtlmodular.cc.cc_data_stick.name", "item:data_stick", null);   // Packaged Data Stick  [wave2]
        add("ProcessedDataStick", 2314, true, "gtlmodular.cc.cc_processed_data_stick.name", "item:data_stick", null);   // Coordinated Data Stick  [wave2]
        add("EchoiteDoubleWire", 2315, false, "gtlmodular.cc.cc_echoite_double_wire.name", "mat:echoite:wire_gt_double", null);   // Packaged 2x Echoite Wire  [wave2]
        add("ProcessedEchoiteDoubleWire", 2316, true, "gtlmodular.cc.cc_processed_echoite_double_wire.name", "mat:echoite:wire_gt_double", null);   // Spooled Echoite Wire  [wave2]
        add("BlackTitaniumPlate", 2317, false, "gtlmodular.cc.cc_black_titanium_plate.name", "mat:black_titanium:plate", null);   // Packaged Black Titanium Plate  [wave2]
        add("ProcessedBlackTitaniumPlate", 2318, true, "gtlmodular.cc.cc_processed_black_titanium_plate.name", "mat:black_titanium:plate", null);   // Stripped Black Titanium Plate  [wave2]
        add("DoubleTechnetiumPlate", 2319, false, "gtlmodular.cc.cc_double_technetium_plate.name", "mat:technetium:double_plate", null);   // Packaged Double Technetium Plate  [wave2]
        add("ProcessedDoubleTechnetiumPlate", 2320, true, "gtlmodular.cc.cc_processed_double_technetium_plate.name", "mat:technetium:double_plate", null);   // Stripped Double Technetium Plate  [wave2]
        add("CosmicProcessingCore", 2321, false, "gtlmodular.cc.cc_cosmic_processing_core.name", "itemns:kubejs:cosmic_processing_core", "COSMIC");   // Packaged Cosmic Processing Core  [wave2; ! 无书面 en 证据（kubejs 侧只有 zh_cn）]
        add("ProcessedCosmicProcessingCore", 2322, true, "gtlmodular.cc.cc_processed_cosmic_processing_core.name", "itemns:kubejs:cosmic_processing_core", "COSMIC");   // Coordinated Cosmic Processing Core  [wave2; ! 无书面 en 证据（kubejs 侧只有 zh_cn）]
        add("CosmicProcessor", 2323, false, "gtlmodular.cc.cc_cosmic_processor.name", "itemns:kubejs:cosmic_processor", "COSMIC");   // Packaged Cosmic Processor  [wave2; ! 无书面 en 证据（kubejs 侧只有 zh_cn）]
        add("WyvernCore", 2324, false, "gtlmodular.cc.cc_wyvern_core.name", "itemns:kubejs:wyvern_core", null);   // Packaged Wyvern Core  [wave2; ! 无书面 en 证据（kubejs 侧只有 zh_cn）]
        add("ProcessedWyvernCore", 2325, true, "gtlmodular.cc.cc_processed_wyvern_core.name", "itemns:kubejs:wyvern_core", null);   // Coordinated Wyvern Core  [wave2; ! 无书面 en 证据（kubejs 侧只有 zh_cn）]
        add("ZylonFoil", 2326, false, "gtlmodular.cc.cc_zylon_foil.name", "mat:zylon:foil", null);   // Packaged Zylon Foil  [wave2]
        add("ProcessedZylonFoil", 2327, true, "gtlmodular.cc.cc_processed_zylon_foil.name", "mat:zylon:foil", null);   // Zylon Wrapper Spool  [wave2]
        add("CinobitePlate", 2328, false, "gtlmodular.cc.cc_cinobite_plate.name", "mat:cinobite:plate", null);   // Packaged Cinobite Plate  [wave2]
        add("ProcessedCinobitePlate", 2329, true, "gtlmodular.cc.cc_processed_cinobite_plate.name", "mat:cinobite:plate", null);   // Stripped Cinobite Plate  [wave2]
        add("CosmicAssembly", 2330, false, "gtlmodular.cc.cc_cosmic_assembly.name", "itemns:kubejs:cosmic_assembly", "COSMIC");   // Packaged Cosmic Assembly  [wave2; ! 无书面 en 证据（kubejs 侧只有 zh_cn）]
        add("DoubleTriniumTitaniumPlate", 2331, false, "gtlmodular.cc.cc_double_trinium_titanium_plate.name", "mat:trinium_titanium:double_plate", null);   // Packaged Double Trinium Titanium Plate  [wave2]
        add("ProcessedDoubleTriniumTitaniumPlate", 2332, true, "gtlmodular.cc.cc_processed_double_trinium_titanium_plate.name", "mat:trinium_titanium:double_plate", null);   // Stripped Double Trinium Titanium Plate  [wave2]
        add("VibraniumFrame", 2333, false, "gtlmodular.cc.cc_vibranium_frame.name", "mat:vibranium:frame", null);   // Packaged Vibranium Frame  [wave2]
        add("ProcessedVibraniumFrame", 2334, true, "gtlmodular.cc.cc_processed_vibranium_frame.name", "mat:vibranium:frame", null);   // Spliced Vibranium Frame  [wave2]
        add("CosmicComputer", 2335, false, "gtlmodular.cc.cc_cosmic_computer.name", "itemns:kubejs:cosmic_computer", "COSMIC");   // Packaged Cosmic Computer  [wave2; ! 无书面 en 证据（kubejs 侧只有 zh_cn）]
        add("DataOrb", 2336, false, "gtlmodular.cc.cc_data_orb.name", "item:data_orb", null);   // Packaged Data Orb  [wave2]
        add("ProcessedDataOrb", 2337, true, "gtlmodular.cc.cc_processed_data_orb.name", "item:data_orb", null);   // Coordinated Data Orb  [wave2]
        add("LegendariumDoubleWire", 2338, false, "gtlmodular.cc.cc_legendarium_double_wire.name", "mat:legendarium:wire_gt_double", null);   // Packaged 2x Legendarium Wire  [wave2]
        add("ProcessedLegendariumDoubleWire", 2339, true, "gtlmodular.cc.cc_processed_legendarium_double_wire.name", "mat:legendarium:wire_gt_double", null);   // Spooled Legendarium Wire  [wave2]
        add("ProtoMatter", 2340, false, "gtlmodular.cc.cc_proto_matter.name", "itemns:kubejs:proto_matter", null);   // Packaged Proto Matter  [wave2; ! 无书面 en 证据（kubejs 侧只有 zh_cn）]
        add("ProcessedProtoMatter", 2341, true, "gtlmodular.cc.cc_processed_proto_matter.name", "itemns:kubejs:proto_matter", null);   // Coordinated Proto Matter  [wave2; ! 无书面 en 证据（kubejs 侧只有 zh_cn）]
        add("DoubleNaquadriatictaraniumPlate", 2342, false, "gtlmodular.cc.cc_double_naquadriatictaranium_plate.name", "mat:naquadriatictaranium:double_plate", null);   // Packaged Double Naquadria Taranium Alloy Plate  [wave2]
        add("ProcessedDoubleNaquadriatictaraniumPlate", 2343, true, "gtlmodular.cc.cc_processed_double_naquadriatictaranium_plate.name", "mat:naquadriatictaranium:double_plate", null);   // Stripped Double Naquadria Taranium Alloy Plate  [wave2]
        add("SupracausalProcessingCore", 2344, false, "gtlmodular.cc.cc_supracausal_processing_core.name", "itemns:kubejs:supracausal_processing_core", "SUPRACAUSAL");   // Packaged Supracausal Processing Core  [wave2; ! 无书面 en 证据（kubejs 侧只有 zh_cn）]
        add("ProcessedSupracausalProcessingCore", 2345, true, "gtlmodular.cc.cc_processed_supracausal_processing_core.name", "itemns:kubejs:supracausal_processing_core", "SUPRACAUSAL");   // Coordinated Supracausal Processing Core  [wave2; ! 无书面 en 证据（kubejs 侧只有 zh_cn）]
        add("MicrowormholeGenerator", 2346, false, "gtlmodular.cc.cc_microwormhole_generator.name", "itemns:kubejs:microwormhole_generator", null);   // Packaged Microwormhole Generator  [wave2; ! 无书面 en 证据（kubejs 侧只有 zh_cn）]
        add("ProcessedMicrowormholeGenerator", 2347, true, "gtlmodular.cc.cc_processed_microwormhole_generator.name", "itemns:kubejs:microwormhole_generator", null);   // Coordinated Microwormhole Generator  [wave2; ! 无书面 en 证据（kubejs 侧只有 zh_cn）]
        add("ManifoldOscillatoryPowerCell", 2348, false, "gtlmodular.cc.cc_manifold_oscillatory_power_cell.name", "itemns:kubejs:manifold_oscillatory_power_cell", null);   // Packaged Manifold Oscillatory Power Cell  [wave2; ! 无书面 en 证据（kubejs 侧只有 zh_cn）]
        add("ProcessedManifoldOscillatoryPowerCell", 2349, true, "gtlmodular.cc.cc_processed_manifold_oscillatory_power_cell.name", "itemns:kubejs:manifold_oscillatory_power_cell", null);   // Coordinated Manifold Oscillatory Power Cell  [wave2; ! 无书面 en 证据（kubejs 侧只有 zh_cn）]
        add("CrystalmatrixPlate", 2350, false, "gtlmodular.cc.cc_crystalmatrix_plate.name", "mat:crystalmatrix:plate", null);   // Packaged Crystal Matrix Plate  [wave2]
        add("ProcessedCrystalmatrixPlate", 2351, true, "gtlmodular.cc.cc_processed_crystalmatrix_plate.name", "mat:crystalmatrix:plate", null);   // Stripped Crystal Matrix Plate  [wave2]
        add("SupracausalProcessor", 2352, false, "gtlmodular.cc.cc_supracausal_processor.name", "itemns:kubejs:supracausal_processor", "SUPRACAUSAL");   // Packaged Supracausal Processor  [wave2; ! 无书面 en 证据（kubejs 侧只有 zh_cn）]
        add("AwakenedCore", 2353, false, "gtlmodular.cc.cc_awakened_core.name", "itemns:kubejs:awakened_core", null);   // Packaged Awakened Core  [wave2; ! 无书面 en 证据（kubejs 侧只有 zh_cn）]
        add("ProcessedAwakenedCore", 2354, true, "gtlmodular.cc.cc_processed_awakened_core.name", "itemns:kubejs:awakened_core", null);   // Coordinated Awakened Core  [wave2; ! 无书面 en 证据（kubejs 侧只有 zh_cn）]
        add("FineHeavyQuarkDegenerateMatterWire", 2355, false, "gtlmodular.cc.cc_fine_heavy_quark_degenerate_matter_wire.name", "mat:heavy_quark_degenerate_matter:fine_wire", null);   // Packaged Fine Heavy Quark Degenerate Matter Wire  [wave2]
        add("ProcessedFineHeavyQuarkDegenerateMatterWire", 2356, true, "gtlmodular.cc.cc_processed_fine_heavy_quark_degenerate_matter_wire.name", "mat:heavy_quark_degenerate_matter:fine_wire", null);   // Spooled Heavy Quark Degenerate Matter Wire  [wave2]
        add("OpticalSoc", 2357, false, "gtlmodular.cc.cc_optical_soc.name", "itemns:kubejs:optical_soc", "OPTICAL");   // Packaged Optical SoC  [wave2; ! 无书面 en 证据（kubejs 侧只有 zh_cn）]
        add("ProcessedOpticalSoc", 2358, true, "gtlmodular.cc.cc_processed_optical_soc.name", "itemns:kubejs:optical_soc", "OPTICAL");   // Cut Optical SoC  [wave2; ! 无书面 en 证据（kubejs 侧只有 zh_cn）]
        add("RecursivelyFoldedNegativeSpace", 2359, false, "gtlmodular.cc.cc_recursively_folded_negative_space.name", "itemns:kubejs:recursively_folded_negative_space", null);   // Packaged Recursively Folded Negative Space  [wave2; ! 无书面 en 证据（kubejs 侧只有 zh_cn）]
        add("ProcessedRecursivelyFoldedNegativeSpace", 2360, true, "gtlmodular.cc.cc_processed_recursively_folded_negative_space.name", "itemns:kubejs:recursively_folded_negative_space", null);   // Coordinated Recursively Folded Negative Space  [wave2; ! 无书面 en 证据（kubejs 侧只有 zh_cn）]
        add("ContainedExoticMatter", 2361, false, "gtlmodular.cc.cc_contained_exotic_matter.name", "itemns:kubejs:contained_exotic_matter", "EXOTIC");   // Packaged Contained Exotic Matter  [wave2; ! 无书面 en 证据（kubejs 侧只有 zh_cn）]
        add("ProcessedContainedExoticMatter", 2362, true, "gtlmodular.cc.cc_processed_contained_exotic_matter.name", "itemns:kubejs:contained_exotic_matter", "EXOTIC");   // Coordinated Contained Exotic Matter  [wave2; ! 无书面 en 证据（kubejs 侧只有 zh_cn）]
        add("SupracausalAssembly", 2363, false, "gtlmodular.cc.cc_supracausal_assembly.name", "itemns:kubejs:supracausal_assembly", "SUPRACAUSAL");   // Packaged Supracausal Assembly  [wave2; ! 无书面 en 证据（kubejs 侧只有 zh_cn）]
        add("DataModule", 2364, false, "gtlmodular.cc.cc_data_module.name", "item:data_module", null);   // Packaged Data Module  [wave2]
        add("ProcessedDataModule", 2365, true, "gtlmodular.cc.cc_processed_data_module.name", "item:data_module", null);   // Coordinated Data Module  [wave2]
        add("StarmetalPlate", 2366, false, "gtlmodular.cc.cc_starmetal_plate.name", "mat:starmetal:plate", null);   // Packaged Star Metal Plate  [wave2]
        add("ProcessedStarmetalPlate", 2367, true, "gtlmodular.cc.cc_processed_starmetal_plate.name", "mat:starmetal:plate", null);   // Stripped Star Metal Plate  [wave2]
        add("EigenfoldedKerrManifold", 2368, false, "gtlmodular.cc.cc_eigenfolded_kerr_manifold.name", "itemns:kubejs:eigenfolded_kerr_manifold", null);   // Packaged Eigenfolded Kerr Manifold  [wave2; ! 无书面 en 证据（kubejs 侧只有 zh_cn）]
        add("ProcessedEigenfoldedKerrManifold", 2369, true, "gtlmodular.cc.cc_processed_eigenfolded_kerr_manifold.name", "itemns:kubejs:eigenfolded_kerr_manifold", null);   // Coordinated Eigenfolded Kerr Manifold  [wave2; ! 无书面 en 证据（kubejs 侧只有 zh_cn）]
        add("CosmicneutroniumPlate", 2370, false, "gtlmodular.cc.cc_cosmicneutronium_plate.name", "mat:cosmicneutronium:plate", "COSMIC");   // Packaged Cosmic Neutronium Plate  [wave2]
        add("ProcessedCosmicneutroniumPlate", 2371, true, "gtlmodular.cc.cc_processed_cosmicneutronium_plate.name", "mat:cosmicneutronium:plate", "COSMIC");   // Stripped Cosmic Neutronium Plate  [wave2]
        add("SupracausalComputer", 2372, false, "gtlmodular.cc.cc_supracausal_computer.name", "itemns:kubejs:supracausal_computer", "SUPRACAUSAL");   // Packaged Supracausal Computer  [wave2; ! 无书面 en 证据（kubejs 侧只有 zh_cn）]
        add("RadoxPlate", 2373, false, "gtlmodular.cc.cc_radox_plate.name", "mat:radox:plate", null);   // Packaged Radox Plate  [wave2]
        add("ProcessedRadoxPlate", 2374, true, "gtlmodular.cc.cc_processed_radox_plate.name", "mat:radox:plate", null);   // Stripped Radox Plate  [wave2]
        add("DraconiumawakenedDoubleWire", 2375, false, "gtlmodular.cc.cc_draconiumawakened_double_wire.name", "mat:draconiumawakened:wire_gt_double", null);   // Packaged 2x Awakened Draconium Wire  [wave2]
        add("ProcessedDraconiumawakenedDoubleWire", 2376, true, "gtlmodular.cc.cc_processed_draconiumawakened_double_wire.name", "mat:draconiumawakened:wire_gt_double", null);   // Spooled Awakened Draconium Wire  [wave2]
        add("CosmicFabric", 2377, false, "gtlmodular.cc.cc_cosmic_fabric.name", "itemns:kubejs:cosmic_fabric", "COSMIC");   // Packaged Cosmic Fabric  [wave2; ! 无书面 en 证据（kubejs 侧只有 zh_cn）]
        add("ProcessedCosmicFabric", 2378, true, "gtlmodular.cc.cc_processed_cosmic_fabric.name", "itemns:kubejs:cosmic_fabric", "COSMIC");   // Coordinated Cosmic Fabric  [wave2; ! 无书面 en 证据（kubejs 侧只有 zh_cn）]
        add("UltrashortPulseLaser", 2379, false, "gtlmodular.cc.cc_ultrashort_pulse_laser.name", "itemns:kubejs:ultrashort_pulse_laser", null);   // Packaged Ultrashort Pulse Laser  [wave2; ! 无书面 en 证据（kubejs 侧只有 zh_cn）]
        add("ProcessedUltrashortPulseLaser", 2380, true, "gtlmodular.cc.cc_processed_ultrashort_pulse_laser.name", "itemns:kubejs:ultrashort_pulse_laser", null);   // Coordinated Ultrashort Pulse Laser  [wave2; ! 无书面 en 证据（kubejs 侧只有 zh_cn）]
        add("CtcComputationalUnit", 2381, false, "gtlmodular.cc.cc_ctc_computational_unit.name", "itemns:kubejs:ctc_computational_unit", null);   // Packaged CTC Computational Unit  [wave2; ! 无书面 en 证据（kubejs 侧只有 zh_cn）]
        add("ProcessedCtcComputationalUnit", 2382, true, "gtlmodular.cc.cc_processed_ctc_computational_unit.name", "itemns:kubejs:ctc_computational_unit", null);   // Coordinated CTC Computational Unit  [wave2; ! 无书面 en 证据（kubejs 侧只有 zh_cn）]
        add("QuantumchromodynamicallyConfinedMatterPlate", 2383, false, "gtlmodular.cc.cc_quantumchromodynamically_confined_matter_plate.name", "mat:quantumchromodynamically_confined_matter:plate", null);   // Packaged Quantum Chromodynamically Confined Matter Plate  [wave2]
        add("ProcessedQuantumchromodynamicallyConfinedMatterPlate", 2384, true, "gtlmodular.cc.cc_processed_quantumchromodynamically_confined_matter_plate.name", "mat:quantumchromodynamically_confined_matter:plate", null);   // Stripped Quantum Chromodynamically Confined Matter Plate  [wave2]
        add("HsseFrame", 2385, false, "gtlmodular.cc.cc_hsse_frame.name", "mat:hsse:frame", null);   // Packaged HSS-E Frame  [wave2]
        add("ProcessedHsseFrame", 2386, true, "gtlmodular.cc.cc_processed_hsse_frame.name", "mat:hsse:frame", null);   // Spliced HSS-E Frame  [wave2]
        add("HpicChip", 2387, false, "gtlmodular.cc.cc_hpic_chip.name", "item:hpic_chip", null);   // Packaged HPIC Chip  [wave2]
        add("ProcessedHpicChip", 2388, true, "gtlmodular.cc.cc_processed_hpic_chip.name", "item:hpic_chip", null);   // Cut HPIC Chip  [wave2]
        add("NiobiumTitaniumSingleWire", 2389, false, "gtlmodular.cc.cc_niobium_titanium_single_wire.name", "mat:niobium_titanium:wire_gt_single", null);   // Packaged 1x Niobium Titanium Wire  [wave2]
        add("ProcessedNiobiumTitaniumSingleWire", 2390, true, "gtlmodular.cc.cc_processed_niobium_titanium_single_wire.name", "mat:niobium_titanium:wire_gt_single", null);   // Spooled Niobium Titanium Wire  [wave2]
        add("NaquadahBolt", 2391, false, "gtlmodular.cc.cc_naquadah_bolt.name", "mat:naquadah:bolt", null);   // Packaged Naquadah Bolt  [wave2]
        add("ProcessedNaquadahBolt", 2392, true, "gtlmodular.cc.cc_processed_naquadah_bolt.name", "mat:naquadah:bolt", null);   // Filed Naquadah Bolt  [wave2]
        add("EuropiumPlate", 2393, false, "gtlmodular.cc.cc_europium_plate.name", "mat:europium:plate", null);   // Packaged Europium Plate  [wave2]
        add("ProcessedEuropiumPlate", 2394, true, "gtlmodular.cc.cc_processed_europium_plate.name", "mat:europium:plate", null);   // Stripped Europium Plate  [wave2]
        add("EnrichedNaquadahTriniumEuropiumDuranideDoubleWire", 2395, false, "gtlmodular.cc.cc_enriched_naquadah_trinium_europium_duranide_double_wire.name", "mat:enriched_naquadah_trinium_europium_duranide:wire_gt_double", null);   // Packaged 2x Enriched Naquadah Trinium Europium Duranide Wire  [wave2]
        add("ProcessedEnrichedNaquadahTriniumEuropiumDuranideDoubleWire", 2396, true, "gtlmodular.cc.cc_processed_enriched_naquadah_trinium_europium_duranide_double_wire.name", "mat:enriched_naquadah_trinium_europium_duranide:wire_gt_double", null);   // Spooled Enriched Naquadah Trinium Europium Duranide Wire  [wave2]
        add("ExoticMainframe", 2397, false, "gtlmodular.cc.cc_exotic_mainframe.name", "itemns:kubejs:exotic_mainframe", "EXOTIC");   // Packaged Exotic Mainframe  [wave2; ! 无书面 en 证据（kubejs 侧只有 zh_cn）]
        add("CosmicMainframe", 2398, false, "gtlmodular.cc.cc_cosmic_mainframe.name", "itemns:kubejs:cosmic_mainframe", "COSMIC");   // Packaged Cosmic Mainframe  [wave2; ! 无书面 en 证据（kubejs 侧只有 zh_cn）]
        add("SupracausalMainframe", 2399, false, "gtlmodular.cc.cc_supracausal_mainframe.name", "itemns:kubejs:supracausal_mainframe", "SUPRACAUSAL");   // Packaged Supracausal Mainframe  [wave2; ! 无书面 en 证据（kubejs 侧只有 zh_cn）]
    }
}
