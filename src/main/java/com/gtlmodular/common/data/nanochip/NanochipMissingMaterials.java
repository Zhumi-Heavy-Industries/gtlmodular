package com.gtlmodular.common.data.nanochip;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.chemical.material.info.MaterialFlags;
import com.gregtechceu.gtceu.api.data.chemical.material.info.MaterialIconSet;
import com.gtlmodular.GTLModular;

/**
 * NAC（纳米芯片组装复合体）P1 缺件 —— <b>材料侧</b>：注册 4 味包内没有的材料，
 * 并登记 <b>7 档超导线材的包内等价物</b>（本轮退役自造超导材料后的替代表）。
 *
 * <h2>★ 2026-09-18 玩家裁决：7 味自造超导材料整组退役</h2>
 * <p>本类原来注册 5 味超导粗胚（{@code superconductor_{luv,zpm,uv,uiv,umv}_base}）
 * 并给已有的 2 味（{@code superconductor_{uhv,uev}_base}）<b>补 cableProperties</b>，
 * 由此让 GTCEu 自动生成 7 件 {@code gtceu:superconductor_<tier>_base_single_wire}。
 *
 * <p><b>为什么整组删掉</b>：{@code _census/冗余审查_2026-09-18.md} §2.2 + {@code _census/dup_audit.json}
 * 用字节码逐档实证了「本包<b>每一档</b>超导线都早已存在」（12 味 {@code isSuperConductor=true} 的全档表）；
 * 我们新造 7 味是**逐档同档重复**，连 LuV 那味的 ARGB（0x994C00）与化学式都与包内同档件一致。
 * ⇒ 玩家裁决：换算配方改引用包内线材，自造材料整组删除。
 *
 * <h2>现在的两件事</h2>
 * <ol>
 *   <li><b>注册 4 味包内确实没有的材料</b>（{@link #NEW_PLAIN_MATERIALS}）：只给配方需要的最小形态
 *       （bolt / screw / plate）。</li>
 *   <li><b>登记 7 档超导线材的包内等价物</b>（{@link #IN_PACK_SUPERCONDUCTOR_WIRES}）——
 *       这是 {@code _census/p1_input_map.json} 里 CC 1200..1206 的 {@code p1RealInput} 的权威出处，
 *       也是自检 {@code _census/check_p1_missing_stage.js} C5 的对账表。</li>
 * </ol>
 *
 * <h2>7 档超导线材 → 包内等价物（逐档给证据）</h2>
 * <table border="1">
 *   <caption>包内全档超导表（{@code _census/冗余审查_2026-09-18.md} §2.2 的字节码实证摘录）</caption>
 *   <tr><th>NH CC（metaId）</th><th>档（{@code GTValues.V[n]}）</th><th>包内材料（{@code cableProperties} 实证）</th>
 *       <th>配方实际引用的线材 id</th><th>证据</th></tr>
 *   <tr><td>{@code SuperconductorLuV}(1200)</td><td>V[6] LuV / 8A / 0 损 / 超导</td>
 *       <td>{@code indium_tin_barium_titanium_cuprate}</td>
 *       <td>{@code gtceu:indium_tin_barium_titanium_cuprate_single_wire}</td>
 *       <td>{@code _census/out/javap_FirstDegreeMaterials.txt}；{@code realids.txt} 有该 id；
 *           NH 该味化学式 In4Sn2Ba2TiCu7O14 与包内逐元素一致、ARGB 同为 0x994C00</td></tr>
 *   <tr><td>{@code SuperconductorZPM}(1201)</td><td>V[7] ZPM / 8A / 超导</td>
 *       <td>{@code uranium_rhodium_dinaquadide}</td>
 *       <td>{@code gtceu:uranium_rhodium_dinaquadide_single_wire}</td>
 *       <td>同上 javap 文件；{@code realids.txt} 有该 id</td></tr>
 *   <tr><td>{@code SuperconductorUV}(1202)</td><td>V[8] UV / 16A / 超导</td>
 *       <td>{@code enriched_naquadah_trinium_europium_duranide}</td>
 *       <td>{@code gtceu:enriched_naquadah_trinium_europium_duranide_single_wire}</td>
 *       <td>同上；NH 侧该味化学名本身是占位符 {@code Longasssuperconductornameforuvwire} ⇒ 只按档位对齐</td></tr>
 *   <tr><td>{@code SuperconductorUHV}(1203)</td><td>V[9] UHV / 24A / 超导</td>
 *       <td>{@code ruthenium_trinium_americium_neutronate}</td>
 *       <td>{@code gtceu:ruthenium_trinium_americium_neutronate_single_wire}</td>
 *       <td>同上；{@code realids.txt} 有该 id；包内 blastTemp 10800 与 NH 一致</td></tr>
 *   <tr><td>{@code SuperconductorUEV}(1204)</td><td>V[10] UEV / 32A / 超导</td>
 *       <td>{@code enderite}（gtlcore）</td><td>{@code gtceu:enderite_single_wire}</td>
 *       <td>{@code _census/out/javap_GTLMaterialBuilder.txt}；<b>{@code realids.txt} 未收录</b>
 *           ⇒ 以 kubejs 脚本交叉（{@code kubejs/server_scripts/gtceu.js:1862/1884/3779/9059}）</td></tr>
 *   <tr><td>{@code SuperconductorUIV}(1205)</td><td>V[11] UIV / 32A / 超导</td>
 *       <td>{@code echoite}（gtlcore）</td><td>{@code gtceu:echoite_single_wire}</td>
 *       <td>同上 javap；realids 未收录 ⇒ kubejs {@code gtceu.js:9077} 交叉</td></tr>
 *   <tr><td>{@code SuperconductorUMV}(1206)</td><td>V[12] UXV / 64A / 超导（<b>位置等价</b>）</td>
 *       <td>{@code legendarium}（gtlcore）</td><td>{@code gtceu:legendarium_single_wire}</td>
 *       <td>同上 javap；realids 未收录 ⇒ kubejs {@code gtceu.js:2061/5904/9178/9200} 交叉。
 *           <b>GTCEu 1.4.4 的 {@code GTValues} 常量池里没有 UMV</b>（实测 Utf8 = ULV/LV/MV/HV/EV/IV/LuV/ZPM/
 *           UV/UHV/UEV/UIV/UXV/OpV/MAX），NH 的 UMV 正好落在 UXV 的位置（两边 index 都是 12）
 *           ⇒ 取包内最高档 UXV 作位置等价替代</td></tr>
 * </table>
 * <p>⚠️ {@code realids.txt} 是<b>不完备</b>索引（2284 条）：{@code enderite}/{@code echoite}/{@code legendarium}
 * 三档的 {@code _single_wire} 都不在里面，但在整合包 kubejs 脚本里真实被引用 ⇒ 这三档用
 * 「gtlcore 字节码 + kubejs 脚本」双源交叉，而不是 realids。
 *
 * <h2>uhv/uev 两味材料：保留，但<b>撤销</b>本轮补的 cableProperties</h2>
 * <p>{@code superconductor_uhv_base} / {@code superconductor_uev_base} 是 P1 之前就在
 * {@code GTLModularAddon} 注册的老材料（全 src 12 / 18 个文件、40 / 45 处引用，含既有模块与自造 casing），
 * <b>不能删</b>。但「给它们补 cableProperties」这个动作本身会造出同档第二件超导线
 * ⇒ 本轮<b>撤销</b>：回到 P1 之前的样子（只有 {@code .dust().ingot().blastTemp(...).flags(GENERATE_FRAME)}，
 * 没有 WIRE 属性 ⇒ 不再生成 {@code *_single_wire}）。两者的 {@code material.gtceu.*} lang 键与 tooltip 保留。
 * 见 {@link #RETAINED_MATERIALS}。
 *
 * <h2>拼写证据（★ 现读，不是猜的）</h2>
 * <ol>
 *   <li><b>{@code wireGtSingle} 的生成条件 = 「没有 lambda 条件」</b>：
 *       {@code _census/out/tagprefix_conditions.txt:188-189}——
 *       {@code ## 字段 wireGtSingle  name="wireGtSingle"  lang="null"  generateBlock=false}
 *       之后<b>没有</b> {@code condition:} 行（对比 {@code plate}=第 53-54 行 {@code [GENERATE_PLATE]}、
 *       {@code bolt}=第 68-69 行 {@code [GENERATE_BOLT_SCREW]}、{@code screw}=第 71-72 行同款）；
 *       {@code wireGtSingle} 由<b>材料的 {@code CableProperty/WireProperties}</b> 决定
 *       ⇒ 没有 {@code cableProperties(...)} 就<b>一定</b>没有 {@code *_single_wire} 物品。
 *       这条同时解释了「为什么撤销 cableProperties 就等于撤销那两件线材」。
 *       同文件 71 个 TagPrefix 常量一览里也<b>没有</b> {@code itemCasing}（⇒ 那 4 件必须自注册真实物品，见
 *       {@link NanochipMissingItems}）。</li>
 *   <li><b>物品 id 拼写 = {@code <材料名>_single_wire}</b>：运行时注册表实测
 *       ——{@code _census/out/registrate_registry.txt}（游戏日志里的 Registrate 注册行）里
 *       {@code _single_wire} 命中 <b>29</b> 条；
 *       {@code _census/_nh_avail/realids.txt} 里 {@code gtceu:*_single_wire} 命中 <b>42</b> 条
 *       （含 {@code gtceu:aluminium_single_wire} / {@code gtceu:copper_single_wire}）。</li>
 *   <li><b>{@code Material.Builder.cableProperties} 的签名 = {@code (JIIZ)}</b>
 *       （{@code long voltage, int amperage, int loss, boolean isSuperConductor}）——
 *       常量池字符串实测（{@code Material$Builder.class} 的 Utf8：
 *       {@code cableProperties} / {@code (JIIZ)} / {@code (JII)} / {@code voltage} / {@code amperage} /
 *       {@code loss} / {@code WIRE} / {@code WireProperties}）。
 *       本包内的现成用例：{@code gtlcore} 的 {@code PulsatingAlloy} = {@code cableProperties(8L,1,0,true)}、
 *       {@code Hypogen} = {@code cableProperties(2147483647L,32768,0,true)}
 *       （{@code _census/out/javap_GTLMaterialBuilder.txt:633-642 / 2485-2499}）。</li>
 * </ol>
 *
 * <h2>数值证据（4 味保留材料；NH 侧现读，逐条给出来源）</h2>
 * 全部取自 {@code _gtnh/javap_mix/MaterialsInit.txt}（NH/GT5U {@code MaterialsInit} 的 javap -c）。
 * 其中 {@code setARGB(int)} 的入参按 GT5U 口径取低 24 位（{@code & 0xFFFFFF}）：
 *
 * <table border="1">
 *   <tr><th>材料</th><th>NH loader（行号）</th><th>NH ARGB → 本类 color</th><th>NH 图标集</th>
 *       <th>NH blastTemp</th><th>是否推断</th></tr>
 *   <tr><td>{@code six_phased_copper}</td><td>{@code loadSixPhasedCopper}(31776)</td>
 *       <td>{@code 16742420 → 0xFF7814}</td><td>{@code SET_SHINY}</td><td>14000</td><td>否</td></tr>
 *   <tr><td>{@code universium}</td><td>{@code loadUniversium}</td>
 *       <td>{@code -14274235 → 0x263145}</td><td>{@code SET_UNIVERSIUM}</td><td>（NH 未设）</td>
 *       <td><b>图标集映射是推断</b>（GTCEu 无 {@code SET_UNIVERSIUM}，取同族 SHINY）</td></tr>
 *   <tr><td>{@code hexanite}</td><td>{@code loadHexanite}(32877)</td>
 *       <td>{@code 4173972 → 0x3FB094}</td><td>{@code SET_NUCLEAR}</td><td>（NH 未设）</td>
 *       <td><b>图标集映射是推断</b>（GTCEu 无 {@code NUCLEAR}，见下）</td></tr>
 *   <tr><td>{@code chromatic_glass}</td>
 *       <td>NH 侧是 <b>GT++</b> 的 {@code MaterialsElements$STANDALONE.CHRONOMATIC_GLASS}
 *           （{@code _gtnh/javap_mix/GTModItems.txt:1573} 实证引用），<b>不在</b> {@code MaterialsInit} 里
 *           ⇒ 取不到材料定义</td>
 *       <td>{@code 0x9397A1}</td><td>—</td><td>（NH 未设）</td>
 *       <td><b>是推断</b>：色值取自 NH 贴图
 *           {@code gregtech/textures/items/gt.circuitcomponent/processed/processedboltchromaticglass.png}
 *           的<b>高亮区均色</b>（{@code node _census/extract_material_color.js …} 现算；sha256
 *           {@code 79A3C15A…}）—— 与 {@code GTLModularAddon} 给 uhv/uev 取色的方法完全一致</td></tr>
 * </table>
 */
public final class NanochipMissingMaterials {

    private NanochipMissingMaterials() {
    }

    /** 本类是否已经跑过（幂等闸门；与 addon 的 {@code materialsRegistered} 互相独立，双保险）。 */
    private static boolean registered;

    // ---------------------------------------------------------------- 超导线材：包内等价物（★ 退役替代表）

    /**
     * 7 档超导线材的档位后缀（顺序 = NH CC metaId 升序 1200..1206）。
     *
     * <p>与 {@link #IN_PACK_SUPERCONDUCTOR_WIRES} <b>逐位对应</b>；两者长度必须一致（自检 C5 会断言）。
     */
    public static final String[] IN_PACK_SUPERCONDUCTOR_TIERS =
            { "luv", "zpm", "uv", "uhv", "uev", "uiv", "umv" };

    /**
     * 7 档超导线材的<b>包内等价物</b>（下一行注释给出该档的 {@code GTValues.V[n]} 与证据等级）。
     *
     * <p>这是 CC 1200..1206 的 {@code p1_input_map.json → conversion[].p1RealInput} 的权威出处；
     * 数组顺序与 {@link #IN_PACK_SUPERCONDUCTOR_TIERS} 一一对应，两者长度必须相等。
     * <p>⚠️ 第 7 项（UMV）是<b>位置等价替代</b>：GTCEu 1.4.4 无 UMV 档、本包也无，取最高档 UXV。
     */
    public static final String[] IN_PACK_SUPERCONDUCTOR_WIRES = {
            // LuV V[6] / 8A —— realids.txt 实证 + 字节码（ARGB 0x994C00 与 NH 同）
            "gtceu:indium_tin_barium_titanium_cuprate_single_wire",
            // ZPM V[7] / 8A —— realids.txt 实证 + 字节码
            "gtceu:uranium_rhodium_dinaquadide_single_wire",
            // UV  V[8] / 16A —— realids.txt 实证 + 字节码
            "gtceu:enriched_naquadah_trinium_europium_duranide_single_wire",
            // UHV V[9] / 24A —— realids.txt 实证 + 字节码
            "gtceu:ruthenium_trinium_americium_neutronate_single_wire",
            // UEV V[10] / 32A —— gtlcore 字节码 + kubejs（realids 未收录）
            "gtceu:enderite_single_wire",
            // UIV V[11] / 32A —— gtlcore 字节码 + kubejs（realids 未收录）
            "gtceu:echoite_single_wire",
            // UMV→UXV V[12] / 64A —— 位置等价替代（gtlcore 字节码 + kubejs；realids 未收录）
            "gtceu:legendarium_single_wire",
    };

    /** 档位后缀 ⇒ 该档的包内线材 id；不在 7 档内返回 {@code null}。 */
    public static String inPackSuperconductorWire(String tier) {
        for (int i = 0; i < IN_PACK_SUPERCONDUCTOR_TIERS.length; i++) {
            if (IN_PACK_SUPERCONDUCTOR_TIERS[i].equals(tier)) {
                return IN_PACK_SUPERCONDUCTOR_WIRES[i];
            }
        }
        return null;
    }

    /**
     * 保留（<b>不删</b>）但已<b>撤销</b>本轮补的 {@code cableProperties} 的两味已有材料。
     *
     * <p>为什么不能删：{@code superconductor_uhv_base} 全 src 12 个文件 40 处引用、
     * {@code superconductor_uev_base} 18 个文件 45 处（含既有模块结构判定与自造 casing），
     * 且它们的框架方块 / 锭 / 粉尘链已经进入玩家存档。
     * <p>为什么撤销 cableProperties：补完会生成 {@code gtceu:superconductor_<tier>_base_single_wire}，
     * 与包内同档超导线（{@code ruthenium_trinium_americium_neutronate} / {@code enderite}）重复；
     * 撤销后回到 P1 之前的形态。两者的 {@code material.gtceu.*} lang 键保留。
     */
    public static final String[] RETAINED_MATERIALS = { "superconductor_uhv_base", "superconductor_uev_base" };

    // ---------------------------------------------------------------- 注册入口

    /**
     * ★ <b>唯一入口</b>：在 GTCEu 的 {@code MaterialEvent} 窗口里调用
     * （由 {@code GTLModularAddon#registerNacMaterials()} 末尾转调，见 {@code _census/_p1_stage/WIRING.md}）。
     *
     * <p>做的事<b>只剩一件</b>：注册 4 味包内没有的材料（{@link #NEW_PLAIN_MATERIALS}）。
     * 超导那 7 味（5 味新料 + 2 味补属性）已按 2026-09-18 玩家裁决整组退役，见类注释。
     *
     * <p>幂等 + 防崩：{@code registered} 闸门保证只跑一次（绝不重复注册 —— 那会让游戏起不来）。
     */
    public static synchronized void registerMissingMaterials() {
        if (registered) {
            return;
        }
        registered = true;

        // ---- 4 味材料本体（只给配方需要的最小形态）----
        // BoltChromaticGlass(1904) → bolt
        //   NH 侧是 GT++ MaterialsElements$STANDALONE.CHRONOMATIC_GLASS（不在 GT5U MaterialsInit 里）；
        //   色值 0x9397A1 取自 NH processedboltchromaticglass.png 的高亮区均色（推断，见类注释）。
        newMaterial("chromatic_glass", 0x9397A1, MaterialIconSet.METALLIC)
                .flags(MaterialFlags.GENERATE_BOLT_SCREW)
                .buildAndRegister();

        // ScrewSixPhasedCopper(1909) → screw
        //   NH loadSixPhasedCopper：SET_SHINY / ARGB 16742420(=0xFF7814) / melting 1000 / blastTemp 14000。
        newMaterial("six_phased_copper", 0xFF7814, MaterialIconSet.SHINY)
                .blastTemp(14000)
                .flags(MaterialFlags.GENERATE_BOLT_SCREW)
                .buildAndRegister();

        // ScrewUniversium(1916) → screw
        //   NH loadUniversium：SET_UNIVERSIUM / ARGB -14274235(=0x263145)；NH 未设 blastTemp。
        newMaterial("universium", 0x263145, MaterialIconSet.SHINY)
                .flags(MaterialFlags.GENERATE_BOLT_SCREW)
                .buildAndRegister();

        // PlateHexanite(1913) → plate
        //   NH loadHexanite：SET_NUCLEAR / ARGB 4173972(=0x3FB094)；NH 未设 blastTemp。
        //   ⚠️ GTCEu 无 NUCLEAR 图标集（MaterialIconSet 常量池实测：DULL/METALLIC/MAGNETIC/SHINY/
        //      BRIGHT/DIAMOND/EMERALD/GEM_*/RUBY/OPAL/GLASS/NETHERSTAR/FINE/SAND/WOOD/ROUGH/FLINT/
        //      LIGNITE/QUARTZ/CERTUS/LAPIS/FLUID/RADIOACTIVE，无 NUCLEAR）⇒ 取语义最近的 RADIOACTIVE。
        //      RADIOACTIVE 目录里只有 overlay 层（实测），plate 本体会走 GTCEu 图标集父链回退到 DULL
        //      —— 与既有 4 味材料靠同一机制拿 frame 贴图同源；这条回退<b>没有实测验证</b>（见交付说明）。
        newMaterial("hexanite", 0x3FB094, MaterialIconSet.RADIOACTIVE)
                .flags(MaterialFlags.GENERATE_PLATE)
                .buildAndRegister();

        // ★ 2026-09-21（队长拍板）：超导线束分离器要的「超级冷却液」GTL 根本没有（四个 jar 的中文语言文件
        //   10437 个键里只有 material.gtceu.pcb_coolant = 多氯联苯冷却液，没有任何 super_coolant），
        //   而 NH 原文抽的就是 Materials.SuperCoolant ⇒ 本模组注册这一味**纯流体材料**。
        //   配方（队长给数）：冰粉（`gtceu:ice_dust`，GTL 汉名「碎冰」）+ 铅粉 + 2000 mB 多氯联苯冷却液
        //   → 2 碎冰 + 2000 mB 超级冷却液，480 EU/t(HV) × 200 tick = 96,000 EU，
        //   见 `data/gtlmodular/recipes/missing_acquisition/super_coolant.json`（化学反应釜）。
        new Material.Builder(GTCEu.id("super_coolant"))
                .color(0x4FC3F7)
                .secondaryColor(lighten(0x4FC3F7, 0.55F))
                .iconSet(MaterialIconSet.FLUID)
                .fluid()
                .buildAndRegister();

        // ★ 2026-09-21（队长拍板）：新流体「维度偏移超流体」`gtceu:dimensionally_offset_superfluid`
        //   —— 整包（gtceu / gtlcore / gtladditions / GTLsupb 四个 jar）**根本没有**这一味。取证方式：
        //   ① 四个 jar 的 assets/**/lang/zh_cn.json 全量扫描（gtceu 5941 + gtlcore 2650 + gtladditions 1264
        //      + gtlsupb 605 = 10460 个键，其中 material.gtceu.* 共 1451 条）：`superfluid` 命中 0 条
        //      —— 事实上连超级冷却液 super_coolant 也命中 0，那里只有 material.gtceu.pcb_coolant = 多氯联苯冷却液；
        //   ② 包内 kubejs 索引同样没有 `*superfluid*` 命中。
        //   ⇒ 所以必须由本模组注册这一味**纯流体材料**。
        //   产出方：超维度等离子锻炉（`gtceu:dimensionally_transcendent_plasma_forge`）的一条配方 ——
        //   duration 600、EUt 31457280、blastFurnaceTemp 10800；输入 400 mB 8级净化水 + 2000 mB 超重拉多X
        //   + 1000 mB 平凡超维度催化剂 + 1000 mB 超重H合金 + 1000 mB 重夸克简并物质 + 500 mB 宇宙超导体，
        //   输出 7500 mB 维度偏移超流体 + 250 mB 超维度残渣，配方文件已存在于
        //   `data/gtlmodular/recipes/coolant/dimensionally_offset_superfluid.json`（输出 id 与本处注册 id 逐字一致）。
        //   ── 2026-09-21 队长复查原话：「那个切割机要用的冷却液也没见你添加配方」。查清结论（只读取证）：
        //      ① 「那台切割机」= 纳米精度切割仓 `nanochip_cutting_chamber`；它要的冷却液 = **本味**。
        //         GTNH 维基 `_census/wiki_抓取/raw/纳米精度切割仓.wiki.txt`：简述行「不同配方会消耗润滑油、
        //         **维度偏移超流体**或分级净化水」，表内 7 行 = #8/#9/#10 阿米/仄米/幺米功率IC 10 L、
        //         #27 磁物质框架 100 L、#28/#29/#30 鲁格诺板 / 亚稳态鿫板 / 咒源玄晶板 50 L；
        //         `_census/P2_配方规格与输入解析.md` §3.2 的 `DimensionallyShiftedSuperfluid`（切割室 7 条）
        //         与本味是同一件（当时整包没有 ⇒ 只能标 substitute 用 `gtceu:spacetime` 替代）。
        //      ② **产出配方不缺**：本处注册 + `coolant/dimensionally_offset_superfluid.json` 两件都在
        //         **已部署的 `gtlmodular-0.1.44.jar`**（`mods/`）里（jar 内逐条命中）；机器配方类型
        //         `gtceu:dimensionally_transcendent_plasma_forge` 也**真实存在**（gtlcore 的
        //         `org/gtlcore/gtlcore/common/data/GTLRecipeTypes.class` 注册，jar 内字符串命中）；六味输入流体
        //         （8级净化水 / 超重拉多X / 平凡超维度催化剂 / 超重H合金 / 重夸克简并物质 / 宇宙超导体）按
        //         `_census/out/material_flags.json` 逐味都有 `fluid` 调用 ⇒ 配方可加载、玩家拿得到。
        //      ③ 真正的缺口（如实登记，**未擅自改**）：本味流体目前**没有任何配方消费它**（全模组配方里作
        //         为输入命中 0 次）—— 切割仓那 7 条仍吃「替代流体」（润滑油 50 mB / 一级净化水 500 mB）。
        //         是否把这 7 条接回本味属玩法+阶段决定（其中 #28 鲁格诺板按 NH 是 UEV 档、而本味产出在 UIV），
        //         等队长一句话；规格侧的改动点见 `_census/P2_配方规格与输入解析.md` §3.1 / §3.2。
        //   ⚠️ 色值 0x8E24AA（深紫）是**我自己挑的观感色，不是 NH 数据** —— NH 里没有这一味流体，无从照抄；
        //      取深紫/品红系是为了明显区别于紧邻上方的超级冷却液 0x4FC3F7（浅青蓝）。副色照旧由 lighten(...,0.55F) 派生。
        new Material.Builder(GTCEu.id("dimensionally_offset_superfluid"))
                .color(0x8E24AA)
                .secondaryColor(lighten(0x8E24AA, 0.55F))
                .iconSet(MaterialIconSet.FLUID)
                .fluid()
                .buildAndRegister();

        GTLModular.LOGGER.info("[gtlmodular] P1 缺件·材料侧：新增 4 味材料（chromatic_glass / six_phased_copper / "
                + "universium / hexanite）⇒ GTCEu 会生成 bolt/screw/plate 形态 ×4；"
                + "7 味自造超导粗胚已退役（换算配方改引用包内同级线材，见 IN_PACK_SUPERCONDUCTOR_WIRES）");
    }

    // ---------------------------------------------------------------- 工具

    /**
     * 统一的 {@code Material.Builder} 工厂：主色 + 派生副色 + 图标集 + {@code dust}。
     *
     * <p>{@code secondaryColor} 是<b>派生值</b>（把主色向白提亮 55%），不是 NH 数据
     * ——NH 材料只有一个颜色，GTCEu 要两个；已有的 {@code GTLModularAddon} 也是手填的副色。
     * 这一步是为省掉「副色=黑」导致的暗沉观感，与框架/锭的渲染走同一套。
     */
    private static Material.Builder newMaterial(String id, int color, MaterialIconSet iconSet) {
        return new Material.Builder(GTCEu.id(id))
                .color(color)
                .secondaryColor(lighten(color, 0.55F))
                .iconSet(iconSet)
                .dust();
    }

    /** 向白色方向线性提亮（{@code 0}=原色，{@code 1}=纯白）。 */
    private static int lighten(int rgb, float amount) {
        final float a = Math.max(0F, Math.min(1F, amount));
        final int r = (rgb >> 16) & 0xFF;
        final int g = (rgb >> 8) & 0xFF;
        final int b = rgb & 0xFF;
        final int nr = Math.round(r + (0xFF - r) * a);
        final int ng = Math.round(g + (0xFF - g) * a);
        final int nb = Math.round(b + (0xFF - b) * a);
        return (nr << 16) | (ng << 8) | nb;
    }

    /** 本类新增的 4 味材料 id（包内确实没有；超导线材的包内等价物见 {@link #IN_PACK_SUPERCONDUCTOR_WIRES}）。 */
    public static final String[] NEW_PLAIN_MATERIALS = { "chromatic_glass", "six_phased_copper", "universium", "hexanite" };
}
