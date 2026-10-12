package com.gtlmodular.common.data.nanochip;

import com.tterrag.registrate.util.entry.ItemEntry;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * NAC（纳米芯片组装复合体）P1 缺件 —— <b>真实物品侧</b>：10 件自注册物品 + <b>14 件全量查询表</b>。
 *
 * <h2>为什么这 10 件必须自注册，而不是"找个近亲顶替"</h2>
 * 它们的 NH 原型要么<b>只属于别的 mod</b>（如 NH CoreMod 的物品），
 * 要么在 GTCEu 1.20.1 里<b>根本没有对应形态</b>
 * （{@code OrePrefixes.itemCasing} —— {@code _census/out/tagprefix_conditions.txt} 的 71 个 TagPrefix
 * 常量一览里没有它），要么是<b>整条阶梯缺失</b>（PIC 后 5 档、bioware 芯片）。
 * 用近亲顶替会造成「同一输入两个输出」（census §3 明确判 {@code missing} 的理由）⇒ 只能自造。
 * <p><b>★ 但"能对上的"一律不自造</b>：2026-09-18 玩家裁决后，包内确有同档真件的 3 件已退役改用包内 id
 * （{@code pico_pic_chip}→{@code kubejs:pm_chip}、{@code bio_processing_unit}→{@code kubejs:bioware_processing_core}、
 * {@code optical_fiber_cable}→{@code gtceu:normal_optical_pipe}）⇒ 17 → 14 件；另有 7 味自造超导材料整组退役。
 * 逐条依据见 {@link #addRows()} 的 javadoc 表。</p>
 *
 * <p><b>★ 2026-09-18 第二波（玩家裁决 E）：NAC「特殊系列」三档自造电路整组退役 ⇒ 14 → 10 件、18 → 14 行。</b>
 * 维基「特殊系列电路板」逐字：「作为<b>异星、寰宇、超时空三种电路板还未更新时的暂时性的 UMV-MAX 级电路替代品，
 * 后续可能会移除。</b>」（2.9.0 新增）⇒ NH 的皮米/量子/普朗克尺度电路只是给自己补档位空洞的过渡件；
 * 本包同档都有真件（{@code #gtceu:circuits/uxv|opv|max} 与 {@code kubejs:suprachronal_uxv|opv|max}）
 * ⇒ 这 4 件自注册物品（{@code pico_circuit}/{@code quantum_circuit}/{@code planck_circuit}/{@code planck_manifold}）
 * 连同它们的 CC/PC 层条目、换算配方、组装矩阵配方、资产与语言键一并退役。</p>
 *
 * <h2>与 CC 层的边界（★ 别混）</h2>
 * <ul>
 *   <li><b>CC 层</b> = 169 件 {@code gtlmodular:cc_*} 假栈（NH 原始 176 − 退役 7），由
 *       {@link NanochipCircuitComponents} 注册，<b>本类一个字都不碰</b>；</li>
 *   <li><b>真实物品层</b> = 本类的 10 件，id 全部<b>不带</b> {@code cc_} 前缀，与 CC 层零重叠
 *       （重复注册会让游戏起不来 ⇒ 已用 {@code cc_} 前缀区分 + {@link #init()} 断言数量）。</li>
 * </ul>
 *
 * <h2>14 件全量查询表（★ 下游配方层请用 {@link #itemForCc(String)} / {@link #targetIdOf(String)}，不要手写 id）</h2>
 * 表中 14 行的 {@code ccEnum} 逐字对齐 {@link NanochipCircuitComponents} 的 {@code enumName} 与
 * {@code _census/p1_script_hits.json} 的 {@code stillMissing}（原 29 条，先减 11 条改引用包内真件、
 * 再减本轮退役的 4 条「特殊系列」⇒ 14 条），{@code _census/P1_输入物解析表.md} §3。
 * 其中 4 件是<b>材料形态</b>（由 {@link NanochipMissingMaterials} 注册材料后 GTCEu 自动生成），
 * 10 件是<b>本类自注册的真实物品</b>。
 *
 * <h2>贴图来源（每件都记账：{@code _census/_p1_stage/_texture_provenance.json} 有 sha256）</h2>
 * <ul>
 *   <li>{@code NH}：逐字节取自 {@code _gtnh/jarfull/assets/gregtech/textures/items/**}
 *       —— PIC 后 5 档 / living crystal 这些取的是 NH 的
 *       {@code gt.circuitcomponent/processed/*.png}（= 真实物品图标 + 电路组件叠层的处理态图标；
 *       NH 的"未处理态真实物品图标"本身不在本机资源里，这是最接近的 NH 原件，如实记录）；</li>
 *   <li>{@code NH-dreamcraft}：{@code _nac_nh/dc/assets/dreamcraft/textures/items/*.png}
 *       —— NH 真实物品原图（最好的一档证据）；
 *       ★ 2026-09-18：其中 {@code itemPikoCircuit/itemQuantumCircuit/itemPlanckCircuit} 三张随
 *       「特殊系列」整组退役一起删掉了（退役记录见 {@code _p1_stage/_texture_provenance.json} 的
 *       {@code recordsRemoved}，sha256 仍留档）；</li>
 *   <li>{@code NH-generic}：4 件 itemCasing 用 NH 的中性母版 {@code materialicons/NONE/casingSmall.png}
 *       —— GT5U 靠材料色染色，本模组普通物品<b>没有染色入口</b> ⇒ 这几件呈现<b>灰底</b>
 *       （与 {@code NanochipBlocks} 对 sheetmetal 的已知偏差同源，如实记录）。</li>
 * </ul>
 */
public final class NanochipMissingItems {

    private NanochipMissingItems() {
    }

    /** 目标物类型。 */
    public enum Kind {
        /** 本模组自注册的真实物品（{@code gtlmodular:<id>}）。 */
        ITEM,
        /** 材料形态：由 {@link NanochipMissingMaterials} 注册材料后 GTCEu 自动生成（{@code gtceu:<id>}）。 */
        MATERIAL_FORM
    }

    /** 一条「NH CC → 本包目标物」的记录。 */
    public static final class Entry {
        /** NH 的 CC 枚举名（= {@link NanochipCircuitComponents} 的 {@code enumName}）。 */
        public final String ccEnum;
        /** NH 的 metaId（{@code CircuitComponent} 的 damage 值）。 */
        public final int metaId;
        /** 目标物类型。 */
        public final Kind kind;
        /** 目标物的<b>完整 id</b>（含命名空间）。 */
        public final String targetId;
        /** 形态说明（bolt / screw / plate / wireGt01 / item）。 */
        public final String form;
        /** 备忘：NH 原物 + 贴图来源。 */
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

        /** 本模组物品（只有 {@link Kind#ITEM} 有；注册后可用，否则 {@code null}）。 */
        public Item item() {
            return itemEntry == null ? null : itemEntry.get();
        }

        /** 本模组物品的 {@code ItemEntry}（只有 {@link Kind#ITEM} 有）。 */
        public ItemEntry<Item> entry() {
            return itemEntry;
        }

        /** 本模组物品 id 的路径部分（去命名空间）。 */
        public String path() {
            return targetId.substring(targetId.indexOf(':') + 1);
        }

        /** 是不是本类注册的物品。 */
        public boolean isItem() {
            return kind == Kind.ITEM;
        }
    }

    // ---------------------------------------------------------------- 表

    private static final List<Entry> ALL = new ArrayList<>(14);
    private static final Map<String, Entry> BY_CC = new LinkedHashMap<>();
    private static final Map<Integer, Entry> BY_META = new LinkedHashMap<>();
    private static final Map<String, Entry> BY_TARGET = new LinkedHashMap<>();

    /** 自注册物品的 id 前缀（{@link #init()} 会断言 10 件里没有一件带 {@code cc_} ⇒ 与 CC 层零重叠）。 */
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
        // 注册 10 件真实物品。与 NanochipCircuitComponents 同款做法：静态块里建 Registrate 条目，
        // 由 NanochipRegistration.initBlocks()（★ 必须在 Forge 的 RegisterEvent<Item> 之前）触发类加载。
        for (Entry e : ALL) {
            if (e.kind == Kind.ITEM) {
                e.itemEntry = NanochipModContext.machineRegistrate().item(e.path(), Item::new).register();
            }
        }
    }

    /**
     * 14 行全量表（顺序 = CC metaId 升序；分组见注释）。
     *
     * <p>★ 任务口径提醒：这里<b>只</b>造缺件，不造获得链（获得链清单见交付说明）。
     *
     * <h2>★ 2026-09-18 玩家裁决（第一波）⇒ 表从 29 行缩到 18 行（11 条改引用包内真件，不再自造）</h2>
     * 依据：{@code _census/冗余审查_2026-09-18.md} + {@code _census/dup_audit.json}（30 条逐条判定），
     * 玩家裁决后执行。下面每一组都写明「为什么不再自造」：
     * <table border="1">
     *   <caption>被移出本表的 11 条 CC → 改用的包内真件 id</caption>
     *   <tr><th>CC（metaId）</th><th>不再自造的东西</th><th>改用的包内 id</th><th>依据</th></tr>
     *   <tr><td>{@code SuperconductorLuV}(1200)</td><td>{@code gtceu:superconductor_luv_base_single_wire}</td>
     *       <td>{@code gtceu:indium_tin_barium_titanium_cuprate_single_wire}</td>
     *       <td>包内 LuV 超导线早已存在（V[6]/8A/超导），ARGB 同 0x994C00，化学式与 NH 逐元素一致</td></tr>
     *   <tr><td>{@code SuperconductorZPM}(1201)</td><td>{@code …zpm_base_single_wire}</td>
     *       <td>{@code gtceu:uranium_rhodium_dinaquadide_single_wire}</td><td>包内 ZPM 档超导线（V[7]）</td></tr>
     *   <tr><td>{@code SuperconductorUV}(1202)</td><td>{@code …uv_base_single_wire}</td>
     *       <td>{@code gtceu:enriched_naquadah_trinium_europium_duranide_single_wire}</td><td>包内 UV 档（V[8]）</td></tr>
     *   <tr><td>{@code SuperconductorUHV}(1203)</td><td>{@code …uhv_base_single_wire}</td>
     *       <td>{@code gtceu:ruthenium_trinium_americium_neutronate_single_wire}</td>
     *       <td>包内 UHV 档（V[9]）—— 本轮<b>撤销</b>给 {@code superconductor_uhv_base} 补的 cableProperties 后，
     *           那件线材不再生成，必须改引用包内这味</td></tr>
     *   <tr><td>{@code SuperconductorUEV}(1204)</td><td>{@code …uev_base_single_wire}</td>
     *       <td>{@code gtceu:enderite_single_wire}</td><td>包内 UEV 档（V[10]，gtlcore 材料；realids 未收录）</td></tr>
     *   <tr><td>{@code SuperconductorUIV}(1205)</td><td>{@code …uiv_base_single_wire}</td>
     *       <td>{@code gtceu:echoite_single_wire}</td><td>包内 UIV 档（V[11]，gtlcore；realids 未收录）</td></tr>
     *   <tr><td>{@code SuperconductorUMV}(1206)</td><td>{@code …umv_base_single_wire}</td>
     *       <td>{@code gtceu:legendarium_single_wire}</td>
     *       <td>GTCEu 1.4.4 无 UMV 档、本包也无；取包内最高档 UXV 位置等价（V[12]/64A）</td></tr>
     *   <tr><td>{@code BoltUMVSuperconductor}(1905)</td><td>{@code gtceu:superconductor_umv_base_bolt}</td>
     *       <td>{@code gtceu:yttrium_barium_cuprate_bolt}</td>
     *       <td>材料退役 ⇒ 该螺栓消失。同档（UXV/UIV/UEV）超导材料 legendarium/echoite/enderite
     *           <b>只有 GENERATE_FINE_WIRE、没有螺栓形态</b>；包内<b>唯一</b>带 GENERATE_BOLT_SCREW 的超导材料
     *           就是 {@code YttriumBariumCuprate}（GTCEu 的 EV 档超导体）⇒ 取它，见
     *           {@code _census/p1_input_map.json} 的 {@code BoltUMVSuperconductor.p1RealInput.source}</td></tr>
     *   <tr><td>{@code ChipPikoPIC}(605)</td><td>{@code gtlmodular:pico_pic_chip}</td><td>{@code kubejs:pm_chip}</td>
     *       <td>包内 zh「皮米功率集成电路」= NH Piko Power IC，同词根同档（{@code kubejs/startup_scripts/item.js:277}）</td></tr>
     *   <tr><td>{@code BioProcessingUnit}(405)</td><td>{@code gtlmodular:bio_processing_unit}</td>
     *       <td>{@code kubejs:bioware_processing_core}</td>
     *       <td>包内 zh「生物活性CPU」= NH BioCPU（{@code item.js:134}，配方见 {@code gtceu.js:1960}）</td></tr>
     *   <tr><td>{@code CableOpticalFiber}(5)</td><td>{@code gtlmodular:optical_fiber_cable}</td>
     *       <td>{@code gtceu:normal_optical_pipe}</td>
     *       <td>包内 zh 就叫「光缆」（管道方块形态）；realids.txt + kubejs gtceu.js:1060 双证</td></tr>
     * </table>
     * <p>这 11 条在 {@code _census/p1_input_map.json} 里带 {@code p1RealInput.kind = "in_pack"} 标记；
     * 自检按这个标记对账。
     *
     * <h2>★ 2026-09-18 玩家裁决 E（第二波）⇒ 18 行缩到 14 行（4 件「特殊系列」整组退役）</h2>
     * 退役：{@code PicoCircuit}(2116) / {@code QuantumCircuit}(2117) / {@code PlanckCircuit}(2118) /
     * {@code PlanckManifold}(2119) —— 它们的真实物品正是本类原先自注册的
     * {@code pico_circuit}/{@code quantum_circuit}/{@code planck_circuit}/{@code planck_manifold}。
     * <p>维基「特殊系列电路板」逐字：「作为<b>异星、寰宇、超时空三种电路板还未更新时的暂时性的 UMV-MAX 级
     * 电路替代品，后续可能会移除。</b>」（2.9.0 新增）⇒ NH 这三档（UMV/UXV/MAX）只是补档位空洞的过渡件；
     * 本包同档都有真件 {@code #gtceu:circuits/uxv|opv|max} 与 {@code kubejs:suprachronal_uxv|opv|max}。
     * <p>退役落点（逐处）：本表 4 行 + {@link NanochipCircuitComponents} 的 7 条（4 未处理 + 3 机箱）+
     * 8 条配方（4 换算 + 4 组装矩阵 R20..R23）+ 资产 + 语言键 + {@code _census/dup_audit.json} 的 retired 块。
     * 原始数据（{@code _census/_nac_cc_raw.json} / NH javap 转储）是取证件，<b>一个字不改</b>。
     * <p>★ <b>不要动</b> PIC 芯片阶梯（{@code ChipPikoPIC/QuantumPIC/AttoPIC/ZeptoPIC/YoctoPIC/PlanckPIC}
     * 与它们的 {@code ProcessedChip*}）—— 那是切割室的芯片阶梯，玩家明确保留。
     */
    private static void addRows() {
        // ---------------- G3 超导线材七档（CC 1200..1206）★ 本轮整组退役 ----------------
        // 玩家裁决（2026-09-18）：包内**每一档**超导线都早已存在（LuV/ZPM/UV/UHV/UEV/UIV/UXV 全档齐，
        // 见 _census/冗余审查_2026-09-18.md §2.2 的 12 味字节码实证表），我们新造的 7 味是同档重复
        // ⇒ 材料注册整组删除（NanochipMissingMaterials），换算配方改引用包内线材。
        // 7 个包内 id 的权威清单留在 NanochipMissingMaterials.IN_PACK_SUPERCONDUCTOR_WIRES（供自检 C5 对账）。
        //
        // ---------------- G1 材料本体（4 条）⇒ bolt / screw / plate ----------------
        addForm("BoltChromaticGlass", 1904, "chromatic_glass_bolt", "bolt",
                "NH MaterialsElements$STANDALONE.CHRONOMATIC_GLASS.getBolt(1)（GT++ 材料，本包无）"
                        + " ⇒ 新注册 chromatic_glass + GENERATE_BOLT_SCREW");
        // ★ 2026-09-18：原 `BoltUMVSuperconductor`(1905) → gtceu:superconductor_umv_base_bolt 已退役
        //   （材料 superconductor_umv_base 被删 ⇒ 那件螺栓不存在）。替代 = 包内真实螺栓
        //   `gtceu:yttrium_barium_cuprate_bolt`，依据见本方法 javadoc 表 + p1_input_map 的 p1RealInput.source。
        addForm("ScrewSixPhasedCopper", 1909, "six_phased_copper_screw", "screw",
                "NH Materials.SixPhasedCopper.getScrew(1) ⇒ 新注册 + GENERATE_BOLT_SCREW");
        addForm("PlateHexanite", 1913, "hexanite_plate", "plate",
                "NH Materials.Hexanite.getPlate(1) ⇒ 新注册 + GENERATE_PLATE");
        addForm("ScrewUniversium", 1916, "universium_screw", "screw",
                "NH Materials.Universium.getScrew(1) ⇒ 新注册 + GENERATE_BOLT_SCREW");

        // ---------------- G2 itemCasing 4 件 ⇒ 本模组自注册真实物品 ----------------
        // GTCEu 1.20.1 没有 itemCasing 这个 TagPrefix（census §3 G2 + tagprefix_conditions.txt 71 常量实测）
        // ⇒ 不能靠材料形态拿到，只能造物品。id 规则 gtlmodular:<材料>_item_casing。
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

        // ---------------- G4 PIC 六档 + living crystal（6 件）⇒ 自注册物品 ----------------
        // ★ 2026-09-18（玩家裁决 C）：第一档 `pico_pic_chip` 退役改用 `kubejs:pm_chip`（皮米功率集成电路）。
        //   ⚠️ 本包 PIC 阶梯只有 nm / pm / fm **三档**（kubejs/startup_scripts/item.js:274-279；
        //   kubejs_items.json 的 pic_ladder 家族 = nm_chip/nm_wafer/pm_chip/pm_wafer/fm_chip/fm_wafer）。
        //   ⇒ 改完之后形成「第 1 档用包内 pm_chip、后 5 档（quantum/atto/zepto/yocto/planck）仍自造」的
        //   **半吊子阶梯**。玩家已明确知悉并接受该风险（见交付说明）。其余 5 档包内 0 命中，保留自造。
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

        // ---------------- G5 只属于别的 mod 的 4 件 ⇒ 自注册物品 ----------------
        // ★ 2026-09-18（玩家裁决 C）：`CableOpticalFiber`(5) 退役改用包内 `gtceu:normal_optical_pipe`
        //   （包内 zh 就叫「光缆」，是**管道方块**形态）；`BioProcessingUnit`(405) 退役改用
        //   `kubejs:bioware_processing_core`（包内 zh「生物活性CPU」）。
        // ★ 2026-09-18（玩家裁决 E，第二波整组退役）：原 `PicoCircuit`(2116) / `QuantumCircuit`(2117) /
        //   `PlanckCircuit`(2118) / `PlanckManifold`(2119) 这 4 件自注册物品**整组删除**。
        //   依据：维基「特殊系列电路板」逐字「作为异星、寰宇、超时空三种电路板还未更新时的暂时性的
        //   UMV-MAX 级电路替代品，后续可能会移除。」（2.9.0 新增）⇒ NH 的皮米/量子/普朗克尺度电路只是
        //   补档位空洞的过渡件；本包同档都有真件 `#gtceu:circuits/uxv|opv|max` 与
        //   `kubejs:suprachronal_uxv|opv|max`（荒谬/过载/终焉超时空电路，证据 _census/kubejs_items.json）。
        //   退役记录：_census/dup_audit.json 的 retired 块 + _census/nac_cc_items.json 的 retired[] +
        //   _census/p1_input_map.json 的 retired 块 + _census/_p1_stage/manifest.json 的 retired 备注。
    }

    // ---------------------------------------------------------------- 生命周期

    /**
     * 强制类加载（接线用；注册已在静态块完成，重复调用无副作用）。
     *
     * <p>★ 必须在 {@code NanochipRegistration.initBlocks()} 里、Forge 的 {@code RegisterEvent<Item>}
     * <b>之前</b>调用（与 {@code NanochipCircuitComponents.init()} 同一个位置与同一个理由）。
     */
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

    // ---------------------------------------------------------------- 查询（供配方层 / 自检）

    /** 14 行全表（只读，CC metaId 升序）。 */
    public static List<Entry> all() {
        return Collections.unmodifiableList(ALL);
    }

    /** 只看本模组自注册的 10 件物品。 */
    public static List<Entry> items() {
        final List<Entry> out = new ArrayList<>(10);
        for (Entry e : ALL) {
            if (e.kind == Kind.ITEM) {
                out.add(e);
            }
        }
        return Collections.unmodifiableList(out);
    }

    /** CC 枚举名 → 记录。 */
    public static Entry entryForCc(String ccEnum) {
        return BY_CC.get(ccEnum);
    }

    /** CC metaId → 记录。 */
    public static Entry entryForMeta(int metaId) {
        return BY_META.get(metaId);
    }

    /** CC 枚举名 → 目标物完整 id（{@code gtlmodular:…} 或 {@code gtceu:…}）；查不到返回 {@code null}。 */
    public static String targetIdOf(String ccEnum) {
        final Entry e = BY_CC.get(ccEnum);
        return e == null ? null : e.targetId;
    }

    /** CC 枚举名 → 本模组物品；4 条材料形态返回 {@code null}。 */
    public static Item itemForCc(String ccEnum) {
        final Entry e = BY_CC.get(ccEnum);
        return e == null ? null : e.item();
    }

    /** CC 枚举名 → 物品栈；4 条材料形态返回 {@link ItemStack#EMPTY}。 */
    public static ItemStack stackForCc(String ccEnum, int count) {
        final Item it = itemForCc(ccEnum);
        return it == null ? ItemStack.EMPTY : new ItemStack(it, count);
    }

    /** 物品 id（去命名空间）→ 记录。 */
    public static Entry entryForId(String path) {
        return BY_TARGET.get(NanochipModContext.registryId(path).toString());
    }

    /** 本类的自检：进世界时打一条日志用的行数快照。 */
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
