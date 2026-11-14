#!/usr/bin/env node
/**
 * NAC CircuitComponent 物品层生成器（本波只做「物品层」：注册 / 模型 / 贴图 / 显示名 / 对照表）。
 *
 * 输入（全部现读，唯一权威）：
 *   - `_gtnh/dump3/CircuitComponent.txt`      → 经 `_parse_nac_cc.js` 变成 `_nac_cc_raw.json`（176 条）
 *   - `_gtnh/javap_nac/CircuitComponentFakeItem.txt` → 显示名语义（Packaged %s / nameKey 翻译）
 *   - `_gtnh/jarfull/assets/gregtech/lang/en_US.lang` → 81 条 `gt.circuitcomponent.*`（processed 英文名原文）
 *   - `_gtnh/jarfull/assets/gregtech/textures/items/gt.circuitcomponent/` → NH 贴图
 *   - `gtl-dev/inspect/gtceu/assets/gtceu/**`  → GTCEu 1.4.4 的贴图/lang（真实物品的贴图与名字）
 *
 * 产出：
 *   - java : common/data/nanochip/NanochipCircuitComponents.java
 *   - model: assets/gtlmodular/models/item/cc_*.json          （366；NH 原始 176 − 退役 10 + 自造段 200）
 *   - tex  : assets/gtlmodular/textures/item/cc/*.png         （processed 逐字节拷 NH + overlay + 内容物真图）
 *   - lang : assets/gtlmodular/lang/{en_us,zh_cn}.json        （只新增/清理本波自己的键）
 *   - 表   : _census/nac_cc_items.json / .md
 *
 * 用法：node _census/gen_nac_cc_items.js            # 生成
 *       node _census/gen_nac_cc_items.js --dry      # 只打印计划，不写盘
 *       node _census/gen_nac_cc_items.js --out <目录>   # 影子写出：所有写盘改到 <目录>，读仍走真实树
 */
'use strict';
const fs = require('fs');
const path = require('path');
const zlib = require('zlib');

const ROOT = path.resolve(__dirname, '..');
const MOD = path.join(ROOT, 'gtl-modular');
const RES = path.join(MOD, 'src', 'main', 'resources');
const JAVA_DIR = path.join(MOD, 'src', 'main', 'java', 'com', 'gtlmodular', 'common', 'data', 'nanochip');
const NH_TEX = path.join(ROOT, '_gtnh', 'jarfull', 'assets', 'gregtech', 'textures', 'items', 'gt.circuitcomponent');
const NH_LANG = path.join(ROOT, '_gtnh', 'jarfull', 'assets', 'gregtech', 'lang', 'en_US.lang');
const GTCEU = path.join(ROOT, 'gtl-dev', 'inspect', 'gtceu', 'assets', 'gtceu');
const DRY = process.argv.includes('--dry');

/**
 * ★ 2026-09-21 新增：**影子写出根**（`--out <目录>`）。
 *
 * 为什么需要它：本生成器会「**清空并重写** `models/item` 与 `textures/item/cc` 两个目录、重写 Java、
 * 重写 lang」—— 直接跑会动到别的队员正在编辑的文件（尤其 `lang/*.json`）。有了 `--out`：
 *   · 所有**读**（证据 dump、GTCEu/NH 资源、现存的 models/textures/lang）仍走真实工作区树，一个字节不变；
 *   · 所有**写**改到 `<目录>/<真实路径相对 ROOT 的部分>`（目录不存在自动建）。
 * 于是可以「**真跑一次生成器** + 与真实落地态逐文件比对」，零风险地验证「重跑不会复活退役件」。
 *
 * 用法：node _census/gen_nac_cc_items.js --out <临时目录>
 */
const OUT_ROOT = (() => {
    const i = process.argv.indexOf('--out');
    return i >= 0 && process.argv[i + 1] ? path.resolve(process.argv[i + 1]) : null;
})();
/** 真实路径 → 实际写出路径（没有 `--out` 时恒等）。 */
const W = (p) => (OUT_ROOT ? path.join(OUT_ROOT, path.relative(ROOT, p)) : p);
/** 写文件前确保父目录存在（影子根下要现建目录）。 */
const writeOut = (p, data) => { wmkdir(p); fs.writeFileSync(W(p), data); };
const wmkdir = (p) => { if (OUT_ROOT) fs.mkdirSync(path.dirname(W(p)), { recursive: true }); };
if (OUT_ROOT) console.log('★ 影子写出模式：写盘全部改到 ' + OUT_ROOT + '（读仍走 ' + ROOT + '）');

const raw = JSON.parse(fs.readFileSync(path.join(__dirname, '_nac_cc_raw.json'), 'utf8'));
const ALL_RECORDS = raw.records;

// ---------------------------------------------------------------- ★ 退役名单（唯一的"删除发生地"）
/**
 * NAC「特殊系列」三档自造电路 —— **整组退役**（2026-09-18 玩家裁决 E）。
 *
 * <h3>为什么退役（维基逐字依据）</h3>
 * 语料：{@code _census/wiki_抓取/raw/特殊系列电路板.wiki.txt}（离线维基抓取，只读）：
 * <ul>
 *   <li>{@code |电路板1-等级=UMV |电路板2-等级=UXV |电路板3-等级=MAX}</li>
 *   <li>{@code |电路板3-名字=普朗克尺度电路}、{@code |电路板3-获取=普朗克尺度电路等效于任意MAX电路板}</li>
 *   <li>总述逐字：「作为<b>异星、寰宇、超时空三种电路板还未更新时的暂时性的 UMV-MAX 级电路替代品，后续可能会移除。</b>」</li>
 *   <li>历史：2.9.0 新增。</li>
 * </ul>
 * ⇒ NH 这三档是给自己补档位空洞的**过渡件**。本包（GTL）三条线都有，且对应档位都有真件：
 * {@code #gtceu:circuits/uxv}（本包 kubejs 引用 25 处）、{@code #gtceu:circuits/opv}（17 处）、
 * {@code #gtceu:circuits/max}（18 处）；{@code kubejs:suprachronal_uxv|opv|max} =
 * 荒谬/过载/终焉超时空电路（证据 {@code _census/kubejs_items.json}，逐条带 file+line+tags）。
 * （档位错位已实证：GTCEu 没有 UMV 档，NH 的 UMV 正好落在 UXV 位 —— 见
 * {@code NanochipMissingMaterials.java} 类注释里的 javap 实证。）
 *
 * <h3>退役范围（7 条 CC / PC 层条目）</h3>
 * 4 件未处理 CC（2116..2119）= 4 件自注册真实物品；3 件已处理 CC（1802..1804）= 那三件电路的机箱。
 * <h3>⚠️ 明确**不**退役</h3>
 * PIC 芯片阶梯（{@code ChipPikoPIC/ChipQuantumPIC/ChipAttoPIC/ChipZeptoPIC/ChipYoctoPIC/ChipPlanckPIC}
 * 与它们的 {@code ProcessedChip*}）是切割室的芯片阶梯，玩家明确保留；其它电路族（晶体/湿件/生物/光学/
 * 纳米/集成/逻辑/量子系列/寰宇…）一个字不动。
 *
 * <p>★ 纪律：{@code _nac_cc_raw.json} 是从 NH javap dump 解析出来的**取证件**，退役只在本名单里发生，
 * 原始数据一个字不改（否则就丢了"NH 原本有这 7 条"的证据）。
 */
const RETIRED = [
    // ★ 名单唯一真源：退役只在这里发生；`_nac_cc_raw.json`（NH javap 取证件）一个字不改。
    //   字段：enumName / metaId / processed / why 必填；
    //        维基型给 `wikiSource`（依据=维基语料）；
    //        队长拍板型给 `how`（退役落点）/ `probe`（可复跑的取证脚本）/ `javaNote`（Java 里的墓碑注释，逐字）。
    //   ★ 2026-09-21：7 条 → 10 条（补 `ProcessedChipPlanckPIC` 710 与「时空导线」链 `WireSpacetime` 4 /
    //     `ProcessedWireSpacetime` 104）—— 这 3 条此前只落在落地表 retired[] 里，重跑生成器会把它们写回来。
    {
        enumName: "ProcessedPicoCircuitCasing", metaId: 1802, processed: true,
        why: "皮米电路机箱 = PicoCircuit(2116) 的已处理态；随整组退役（维基「特殊系列电路板」总述：过渡件，后续可能会移除）",
        // 维基型退役：依据 = 离线维基抓取语料（只读）
        wikiSource: "_census/wiki_抓取/raw/特殊系列电路板.wiki.txt",
    },
    {
        enumName: "ProcessedQuantumCircuitCasing", metaId: 1803, processed: true,
        why: "量子电路机箱 = QuantumCircuit(2117) 的已处理态；随整组退役",
        // 维基型退役：依据 = 离线维基抓取语料（只读）
        wikiSource: "_census/wiki_抓取/raw/特殊系列电路板.wiki.txt",
    },
    {
        enumName: "ProcessedPlanckCircuitCasing", metaId: 1804, processed: true,
        why: "普朗克尺度电路机箱 = PlanckCircuit(2118) 的已处理态；随整组退役",
        // 维基型退役：依据 = 离线维基抓取语料（只读）
        wikiSource: "_census/wiki_抓取/raw/特殊系列电路板.wiki.txt",
    },
    {
        enumName: "PicoCircuit", metaId: 2116, processed: false,
        why: "NH 过渡件（维基：电路板1-等级=UMV）。本包同档真件 = kubejs:suprachronal_uxv（荒谬超时空电路，tag gtceu:circuits/uxv）",
        // 维基型退役：依据 = 离线维基抓取语料（只读）
        wikiSource: "_census/wiki_抓取/raw/特殊系列电路板.wiki.txt",
    },
    {
        enumName: "QuantumCircuit", metaId: 2117, processed: false,
        why: "NH 过渡件（维基：电路板2-等级=UXV）。本包同档真件 = kubejs:suprachronal_opv（过载超时空电路，tag gtceu:circuits/opv）",
        // 维基型退役：依据 = 离线维基抓取语料（只读）
        wikiSource: "_census/wiki_抓取/raw/特殊系列电路板.wiki.txt",
    },
    {
        enumName: "PlanckCircuit", metaId: 2118, processed: false,
        why: "NH 过渡件（维基：电路板3-名字=普朗克尺度电路、等级=MAX）。本包同档真件 = kubejs:suprachronal_max（终焉超时空电路，tag gtceu:circuits/max）",
        // 维基型退役：依据 = 离线维基抓取语料（只读）
        wikiSource: "_census/wiki_抓取/raw/特殊系列电路板.wiki.txt",
    },
    {
        enumName: "PlanckManifold", metaId: 2119, processed: false,
        why: "流形没有对应真件 ⇒ 随普朗克电路一并退役（它在 NH 侧只被 PlanckCircuit 的组装矩阵消耗，见 _census/javap_nac/notes_recipes.md §2.1 R22/R23）",
        // 维基型退役：依据 = 离线维基抓取语料（只读）
        wikiSource: "_census/wiki_抓取/raw/特殊系列电路板.wiki.txt",
    },
    {
        enumName: "ProcessedChipPlanckPIC", metaId: 710, processed: true,
        why: "队长 2026-09-19 指示「把整合包里面残留注册的、合成根本用不到的物品注销掉」⇒ 注销。证据三连：① 459 个 data/gtlmodular/recipes/** JSON 里 0 次出现（既非 inputs 也非 outputs），整合包实例 kubejs 283 个脚本里 gtlmodular 0 次命中；② 全 Java 树除注册行本身 0 处引用；③ 切割室目录只有 piko/quantum/atto/zepto/yocto 五档，没有 planck 那条，且 32 份 _jar备份_gtlmodular-*.jar（含 5 份「已部署版」）逐份解包确认从来没有产出它的配方 ⇒ 从来拿不到。归因：NH 自己的空档（ProcessedChipPlanckPIC 在 NH ScriptNanochipRecipes.txt 里 0 次命中），见 _census/NAC_机制与配方_待办清单.md §12.5 附注 + §12.8 第 5 条「需另裁」。",
        // 队长拍板型退役：依据 = 队长指示 + 证据（非维基）
        how: "注销落点：NanochipCircuitComponents.addRows() 删 1 行 + models/item/<id>.json + textures/item/cc/<id>.png + lang(zh_cn/en_us) 各 1 键；未处理的 cc_chip_planck_pic(610) 与其换算配方**保留**。",
        probe: "_census/_probe_unused_items.js（可复跑）",
        // Java 里的墓碑注释（逐字，生成器要用它复现注销现场）
        javaNote: [
            "        // ★ 2026-09-19 队长指示「把整合包里面残留注册的、合成根本用不到的物品注销掉」⇒ 本行整条注销：",
            "        //    原注册行 = ProcessedChipPlanckPIC / metaId 710 / nameKey \"gt.circuitcomponent.processed.chipplanckpic\"",
            "        //    （不在这里复写完整 `add(...)` 字面量 —— `_census/check_nac_cc_items.js` 规则②会把它当成第 369 条注册行）。",
            "        //   注销 id = `gtlmodular:cc_processed_chip_planck_pic`。逐条「证据三连」（脚本 `_census/_probe_unused_items.js` 可复跑）：",
            "        //     ① 无配方：459 个 `data/gtlmodular/recipes/**` JSON 里 0 次出现（既非 inputs 也非 outputs）；",
            "        //        整合包实例 kubejs 283 个脚本里 `gtlmodular` 0 次命中；`NanochipSelfCheck.EXPECTED_RECIPES` 也没有它。",
            "        //     ② 无代码引用：全 Java 树里除本行注册以外 0 处引用（`ProcessedChipPlanckPIC` / 该 id 字符串皆 0 命中）；",
            "        //        `NanochipCircuitComponents.itemFor/stack/byMetaId` 与 `NanochipMissingItems.entryForMeta` 都没有 710 的调用方。",
            "        //     ③ 从来拿不到：切割室目录只有 `006..010_cc_processed_chip_{piko,quantum,atto,zepto,yocto}_pic`，",
            "        //        **没有 planck 那一条**；32 份 `_jar备份_gtlmodular-*.jar`（含 5 份「已部署版」）逐份解包确认",
            "        //        `data/gtlmodular/recipes/nanochip/cutting_chamber/` 里**从来没有**产出它的配方 ⇒ 无配方 / 无掉落 / 无给予途径。",
            "        //   归因：这是 NH 自己的空档 —— `ProcessedChipPlanckPIC` 在 NH 的 `ScriptNanochipRecipes.txt` 里 0 次命中",
            "        //   （见 `_census/NAC_机制与配方_待办清单.md` §12.5 附注 + §12.8 第 5 条「需另裁」）；PIC 六档里",
            "        //   piko/quantum/atto/zepto/yocto 五档在切割室都有加工配方，只有 planck 这一档是个注册出来却没人用的空壳。",
            "        //   ⇒ 保留未处理的 `ChipPlanckPIC`(610) 与它的换算配方（本行注销不影响那条链），只注销这个已处理空壳。",
        ],
    },
    {
        enumName: "WireSpacetime", metaId: 4, processed: false,
        why: "队长 2026-09-21 拍板「直接把时空导线这条链整个删掉」⇒ 退役。两条理由：① **材料超阶段**：本链真件 = `gtceu:spacetime_single_wire`（材料 `spacetime` 属 **OpV** 档），超过 NAC 最高档 UXV（任务书科技等级第 13 章「科技等级」）⇒ 在 GTL 里根本做不出来；② **产物孤儿**：本链产物 `ProcessedWireSpacetime` 没有任何配方消费它（NH 组装矩阵 R21 那条我方未落）⇒ CC/PC 两件都拿不到下游用途。归因：整条链（2 件 CC + 3 条配方）一并退役，见 `_census/_log_append_O84.md` 第 1 条与 `_census/NAC_机制与配方_待办清单.md`。",
        // 队长拍板型退役：依据 = 队长指示 + 证据（非维基）
        how: "退役落点（逐处同步）：① 本表 `records[]` → `retired[]`（本文件）；② `_census/check_nac_cc_items.js` 的 `EXPECTED_RETIRED` +2、`BASE.rows` 168→166、`BASE.unprocessed` 95→94、`BASE.processed` 73→72（`RETIRED_ENUMS` 随 EXPECTED_RETIRED 自动 +2）；③ `NanochipCircuitComponents.java` 删 `add(\"WireSpacetime\", 4, false, …)` 注册行；④ 删 `data/gtlmodular/recipes/nanochip/conversion/cc_wire_spacetime.json`；⑤ 删模型 `assets/gtlmodular/models/item/cc_wire_spacetime.json`；⑥ 删 lang(zh_cn/en_us) 的 `gtlmodular.cc.cc_wire_spacetime.name` + `item.gtlmodular.cc_wire_spacetime`；⑦ `_census/check_retired_items.js` 的 `RETIRED` +1（注销件守卫）；⑧ `_nac_cc_plan.json` 同步删本条（规则 ⑧「计划 ≡ 落地表」双射）。★ 孪生件 `cc_processed_wire_spacetime` 的退役落点见其自身条目。",
        probe: "_census/_apply_retire_spacetime_cc.js（本文件，幂等可复跑）；阶段合规证据 `_census/阶段合规复核_队员.md`；孤儿证据 `_census/NAC对账表_CC与配方.md`；只读复核 `node _census/check_retired_items.js` + `node _census/check_nac_cc_items.js`。",
        // Java 里的墓碑注释（逐字，生成器要用它复现注销现场）
        javaNote: [
            "        // ★ 2026-09-21 队长拍板「直接把时空导线这条链整个删掉」⇒ 本行整条注销",
            "        //    （原位置在 `WireProtoHalkonite`(3) 与 `CableOpticalFiber`(5) 之间；metaId 4 从此是**空号，不补位**",
            "        //     —— metaId 是 NH 假栈的 damage，挪号会破坏「按 NH metaId 直接查表」的约定）：",
            "        //     原注册行 = WireSpacetime / metaId 4 / 未处理 / nameKey \"gt.circuitcomponent.wirespacetime\"",
            "        //     （不在这里复写完整 `add(...)` 字面量 —— `_census/check_nac_cc_items.js` 规则②会把它当成注册行）。",
            "        //     注销 id = `gtlmodular:cc_wire_spacetime`（汉名「封装 1x时空导线」）。两条理由：",
            "        //       ① 材料超阶段：本链真件 = `gtceu:spacetime_single_wire`，材料 `spacetime` 属 **OpV** 档，",
            "        //          超过 NAC 最高档 UXV（任务书科技等级第 13 章）⇒ 在 GTL 里根本做不出来；",
            "        //       ② 产物孤儿：孪生已处理件 104 在全库配方里作为输入 **0 次**出现 ⇒ 整条链产而不耗、无下游用途。",
            "        //     ⇒ 孪生件 ProcessedWireSpacetime(104) 与 3 条配方（正/反换算 + wire_tracer/006）同批注销，",
            "        //       见下方 104 处（`ProcessedWireProtoHalkonite` 与 `ProcessedCableOpticalFiber` 之间）的注销注释。",
            "        //     退役登记：`_census/nac_cc_items.json` 的 `retired[]`（第 9/10 条）+ `_census/check_retired_items.js`。",
        ],
    },
    {
        enumName: "ProcessedWireSpacetime", metaId: 104, processed: true,
        why: "队长 2026-09-21 拍板「直接把时空导线这条链整个删掉」⇒ 随孪生件 `cc_wire_spacetime`（WireSpacetime 4）一并退役。两条理由：① **材料超阶段**（继承自其 CC 来料）：唯一来路是 `wire_tracer/006` 吃 `gtceu:spacetime_single_wire`，材料 `spacetime` 属 **OpV** 档 > NAC 最高档 UXV ⇒ 在 GTL 里做不出来；② **产物孤儿**：本件在 459 个 `data/gtlmodular/recipes/**` JSON 里作为 **输出** 出现 1 次、作为 **输入** 出现 **0 次**（全库没有任何配方消费它）⇒ 产而不耗，登记为孤儿（见 `_census/three_families_cc_plan.json` 的 `stillOrphanAfterThisWave` 名单）。",
        // 队长拍板型退役：依据 = 队长指示 + 证据（非维基）
        how: "退役落点（逐处同步）：① 本表 `records[]` → `retired[]`（本文件）；② `_census/check_nac_cc_items.js` 的 `EXPECTED_RETIRED` +1（`BASE.processed` 73→72，`KIND_HIST_*` 的 `nhProcessed` 73→72）；③ `NanochipCircuitComponents.java` 删 `add(\"ProcessedWireSpacetime\", 104, true, …)` 注册行；④ 删 `data/gtlmodular/recipes/nanochip/wire_tracer/006_cc_processed_wire_spacetime.json`；⑤ 删模型 `assets/gtlmodular/models/item/cc_processed_wire_spacetime.json`；⑥ 删贴图 `assets/gtlmodular/textures/item/cc/cc_processed_wire_spacetime.png`（+.mcmeta，删前 sha256 已登记）；⑦ 删 lang(zh_cn/en_us) 的 `gtlmodular.cc.cc_processed_wire_spacetime.name` + `item.gtlmodular.cc_processed_wire_spacetime`；⑧ `_census/check_retired_items.js` 的 `RETIRED` +1（注销件守卫）；⑨ `_nac_cc_plan.json` 同步删本条（规则 ⑧「计划 ≡ 落地表」双射）。★ 未处理的 `WireSpacetime` 是它的孪生 CC，两件同批退役。",
        probe: "_census/_apply_retire_spacetime_cc.js（本文件，幂等可复跑）；孤儿证据 `_census/three_families_cc_plan.json` + `_census/NAC对账表_CC与配方.md`；只读复核 `node _census/check_retired_items.js` + `node _census/check_nac_cc_items.js`。",
        // Java 里的墓碑注释（逐字，生成器要用它复现注销现场）
        javaNote: [
            "        // ★ 2026-09-21 同批注销（时空导线链）：本行原位置在 `ProcessedWireProtoHalkonite`(103) 与",
            "        //    `ProcessedCableOpticalFiber`(105) 之间；metaId 104 从此是**空号，不补位**。",
            "        //    原注册行 = ProcessedWireSpacetime / metaId 104 / 已处理 /",
            "        //    nameKey \"gt.circuitcomponent.processed.wirespacetime\"（汉名「时空线轴」）",
            "        //    （同样不在注释里复写完整 `add(...)` 字面量）。",
            "        //    注销 id = `gtlmodular:cc_processed_wire_spacetime`。两条理由：",
            "        //      ① 材料超阶段（继承其 CC 来料）：唯一来路 `wire_tracer/006` 吃 `gtceu:spacetime_single_wire`，",
            "        //         材料 `spacetime` 属 **OpV** 档 > NAC 最高档 UXV ⇒ 在 GTL 里做不出来；",
            "        //      ② 产物孤儿：459 个 `data/gtlmodular/recipes/**` JSON 里它作为**输出** 1 次、",
            "        //         作为**输入** **0 次**（`_census/three_families_cc_plan.json` 的",
            "        //         `stillOrphanAfterThisWave` 名单里就有它）⇒ 产而不耗。",
            "        //    同批删除落点：3 条配方 `nanochip/{conversion,conversion_reverse}/cc_wire_spacetime.json`",
            "        //    + `nanochip/wire_tracer/006_cc_processed_wire_spacetime.json`；模型 + 贴图（含 .mcmeta）；",
            "        //    lang(zh_cn/en_us) 各 2 键 ⇒ 共 4 键。",
        ],
    },
];
const RETIRED_BY_ENUM = new Map(RETIRED.map(r => [r.enumName, r]));

/** 退役名单必须逐条命中原始表的同名同 metaId 条目 —— 名单写错/原始表变了就当场停。 */
for (const r of RETIRED) {
    const hit = ALL_RECORDS.find(x => x.enumName === r.enumName);
    if (!hit) { console.error(`退役名单里的 ${r.enumName} 在 _nac_cc_raw.json 里找不到（名单写错或原始表已变）`); process.exit(1); }
    if (hit.metaId !== r.metaId) { console.error(`退役名单 ${r.enumName} 的 metaId=${r.metaId} ≠ 原始表 ${hit.metaId}`); process.exit(1); }
    if (!!hit.isProcessed !== r.processed) { console.error(`退役名单 ${r.enumName} 的 processed=${r.processed} ≠ 原始表 ${hit.isProcessed}`); process.exit(1); }
}
if (ALL_RECORDS.length !== 176) {
    console.error(`_nac_cc_raw.json 应有 176 条 NH CC（权威），实读 ${ALL_RECORDS.length} 条 —— 证据变了，请复核`);
    process.exit(1);
}
if (RETIRED.length !== 10) {
    console.error(`退役名单应 10 条（7 条「特殊系列」+ 1 条残留死件 ProcessedChipPlanckPIC(710)`
        + ` + 2 条「时空导线」链 WireSpacetime(4)/ProcessedWireSpacetime(104)），实为 ${RETIRED.length}`);
    process.exit(1);
}

/** ★ 过滤后的生成输入：169 条 = 176 - 7（退役只发生在这里）。 */
const RECORDS = ALL_RECORDS.filter(r => !RETIRED_BY_ENUM.has(r.enumName));

// ---------------------------------------------------------------- ★★ 波2 EXTRA（本包自造件）
// 2026-09-18：五族 + 晶体/湿件 的 NAC 改造需要 103 种新材料的 CC（197 件：94 元件 ×2 + 9 电路 ×1）。
// 全部走**本包自造段 metaId 2200..**（NH 原表只到 2119），与 NH 段隔开，避免同号异义。
// 数据源 `_census/nac_wave2_spec.json`（由 `_probe_wave2_spec.js` 生成）。
const EXTRA_FILE = path.join(__dirname, 'nac_wave2_spec.json');
/** 整合包实例根（读 kubejs 资源用；只读）。 */
// ★ 2026-11-14：Windows 盘符路径在 WSL 下读不到，改成 /mnt/d 形式。
//   真实整合包实例（含 kubejs 语言与贴图源）。
const GTL_PACK = '/mnt/d/Users/DELL/AppData/Roaming/red.ghs.axolotl/profiles/GTL推荐新手玩版（较难）_versions_GTL';
let EXTRA = [];
if (fs.existsSync(EXTRA_FILE)) {
    EXTRA = JSON.parse(fs.readFileSync(EXTRA_FILE, 'utf8')).rows.map((r) => ({
        enumName: r.enumName, metaId: r.metaId, isProcessed: r.isProcessed,
        nameKey: 'gtlmodular.cc.' + r.itemId.replace(/^gtlmodular:/, '') + '.name',
        iconString: r.itemId.replace(/^gtlmodular:/, ''),
        itemId: r.itemId.replace(/^gtlmodular:/, ''),   // ★ 生成器要的是路径（cc_xxx），不是完整 id
        calibration: r.family ? String(r.family).toUpperCase() : null,
        realComponent: r.realRef, realComponentKind: 'Wave2', realComponentLine: null,
        evidence: { dump: null, decl: null, lambda: null, nhLang: null },
        texture: r.texture, textureSource: r.texture.note || (r.realRefKind + ' ' + r.realRef),
        textureNote: r.textureNote || null,
        realRef: r.realRef, realRefBasis: 'wave2:' + r.realRefKind,
        en: r.en || null, zh: r.zh || null,
        enSource: '波2规格（源物品 ' + r.sourceItem + '）', zhSource: r.zhSource || '波2规格',
        flags: ['wave2'],
        _wave2: { sourceItem: r.sourceItem, usedIn: r.usedIn, kind: r.kind },
    }));
}
/** ★ 计划总数（NH 段 + 自造段）。 */
const PLAN_TOTAL = RECORDS.length + EXTRA.length;
if (RECORDS.length !== 166) {
    console.error(`过滤退役后应剩 166 条（= 176 − 退役 10），实为 ${RECORDS.length} —— 退役名单与原始表不匹配`);
    process.exit(1);
}
console.log(`退役过滤：NH 原始 ${ALL_RECORDS.length} 条 → 生成 ${RECORDS.length} 条（退役 ${RETIRED.length} 条：${RETIRED.map(r => r.enumName).join(', ')}）`);

// ---------------------------------------------------------------- 证据：NH lang / GTCEu lang
const nhLang = new Map();
for (const line of fs.readFileSync(NH_LANG, 'utf8').split(/\r?\n/)) {
    const i = line.indexOf('=');
    if (i > 0 && !line.startsWith('#')) nhLang.set(line.slice(0, i), line.slice(i + 1));
}
const gtEn = JSON.parse(fs.readFileSync(path.join(GTCEU, 'lang', 'en_us.json'), 'utf8'));
const gtZh = JSON.parse(fs.readFileSync(path.join(GTCEU, 'lang', 'zh_cn.json'), 'utf8'));
/**
 * ★ 2026-09-21 修复（父代理裁决 · 收口 O94 点名的既有漂移）：**zh 源必须是「包层覆盖 jar 层」**。
 *
 * 病根（`_census/check_superheavy_names.js` 头部已写明）：语言层是**层叠**的 ——
 *   GTCEu jar（mod 层）       : `material.gtceu.dubnium = 钅杜`  ← **拆字**（两个字拼一个字）
 *   整合包 kubejs（资源包层） : `material.gtceu.dubnium = 𬭊`    ← 官方简化单字，**覆盖 jar**
 * 游戏里生效的是**包那一层**。而本生成器原先只读 jar ⇒ 拼出拆字名（`封装 细钅杜导线`），
 * 与三处既定口径不一致：`_census/nac_wave2_spec.json`（`细𬭊导线`）、落地态、
 * 以及 `check_superheavy_names.js` 的 **R1（我方 `gtlmodular.cc.*.name` 不得出现拆字）**。
 * ⇒ 这里把**包层证据表**合并进来（后者覆盖前者）。
 * 证据文件：`_census/_pack_gtceu_zh_cn.json`（从实例 `kubejs/assets/gtceu/lang/zh_cn.json` 拷来；
 *   与实例逐字节同源，12 条超重元素键，见 `check_superheavy_names.js` 判据 E）。
 */
const PACK_ZH_FILE = path.join(__dirname, '_pack_gtceu_zh_cn.json');
let packZhOverrides = 0;
if (fs.existsSync(PACK_ZH_FILE)) {
    const packZh = JSON.parse(fs.readFileSync(PACK_ZH_FILE, 'utf8'));
    for (const [k, v] of Object.entries(packZh)) if (gtZh[k] !== v) { gtZh[k] = v; packZhOverrides++; }
    console.log(`zh 源合并：包层覆盖 jar 层 ${packZhOverrides} 键（来源 ${path.basename(PACK_ZH_FILE)}）`);
} else {
    console.error('缺少包层 zh 证据表 ' + path.relative(__dirname, PACK_ZH_FILE)
        + ' —— 缺它会把超重元素拼成拆字（check_superheavy_names.js R1 判红）。请先补齐该证据文件。');
    process.exit(1);
}

// ★ 波2：材料/贴图的 key 不只在 gtceu 自己的 lang 里 —— gtlcore / gtladditions / GTLsupb 也注册了
//   大量 `material.gtceu.*` 与 `tagprefix.*` 键（例：polyetheretherketone / cinobite 是 gtlcore 的）。
//   ⇒ 自造段的 key 检查要合并所有**证据 lang**（en_us）来判，否则会把好件误判成缺 key。
const gtEnAll = new Set(Object.keys(gtEn));
/** ★ 合并后的英文名映射（键→值）：GTCEu 本体 + 4 个 GTL jar 的 en_us。
 *  `gtEnAll` 只有键（用来判「键在不在」），拼英文名要用值，故此处另存一份 Map。 */
const gtEnMap = new Map(Object.entries(gtEn));
{
    const evLangDir = path.join(__dirname, '_probe_nacmat_evidence/lang');
    if (fs.existsSync(evLangDir)) {
        for (const f of fs.readdirSync(evLangDir).filter((x) => /__en_us\.json$/.test(x))) {
            try {
                // ★ 证据 lang 文件带 BOM ⇒ 必须先剥，否则 JSON.parse 抛错会被 catch 吞掉（本处踩过一次）
                const txt = fs.readFileSync(path.join(evLangDir, f), 'utf8').replace(/^\uFEFF/, '');
                for (const [k, v] of Object.entries(JSON.parse(txt))) {
                    gtEnAll.add(k);
                    if (!gtEnMap.has(k) && typeof v === 'string') gtEnMap.set(k, v);
                }
            } catch { /* 忽略坏文件 */ }
        }
    }
}

// ---------------------------------------------------------------- 命名规则
/**
 * 缩写感知的 snake_case（2026-09-17 队长冻结规格修正）：
 * 在相邻两字符之间插 `_`，当且仅当：
 *   (a) 前一字符是小写字母或数字，当前字符是大写字母；或
 *   (b) 前一字符是大写字母、当前字符是大写字母，且**再下一个**字符是小写字母（缩写串词边界）。
 * 其余不插；最后整体小写；首字符前不插。
 * 例：AdvSMDResistor→adv_smd_resistor、ChipNanoCPU→chip_nano_cpu、SuperconductorLuV→superconductor_lu_v、
 *     ChipCrystalSoC→chip_crystal_so_c、PikoPIC→piko_pic。
 */
function snake(s) {
    let out = '';
    for (let i = 0; i < s.length; i++) {
        const c = s[i];
        const upper = c >= 'A' && c <= 'Z';
        if (upper && i > 0) {
            const prev = s[i - 1], next = s[i + 1];
            const prevLowerOrDigit = (prev >= 'a' && prev <= 'z') || (prev >= '0' && prev <= '9');
            const prevUpper = prev >= 'A' && prev <= 'Z';
            const nextLower = next !== undefined && next >= 'a' && next <= 'z';
            if (prevLowerOrDigit || (prevUpper && nextLower)) out += '_';
        }
        out += c.toLowerCase();
    }
    return out;
}
/** 旧的「逐字母切」实现（**仅供对照 / 负向验证**，不再用于任何生成）。 */
function snakeLegacy(s) {
    return s.replace(/([A-Z])/g, '_$1').toLowerCase().replace(/^_/, '');
}
const itemIdOf = enumName => 'cc_' + snake(enumName);

// ---------- 规则锚点（逐条断言，任一不符直接中止） ----------
const SNAKE_CASES = [
    ['WireNiobiumTitanium', 'wire_niobium_titanium'],
    ['AdvSMDResistor', 'adv_smd_resistor'],
    ['OpticalSMDCapacitor', 'optical_smd_capacitor'],
    ['ChipNanoCPU', 'chip_nano_cpu'],
    ['ProcessedChipCrystalCPU', 'processed_chip_crystal_cpu'],
    ['SuperconductorLuV', 'superconductor_lu_v'],
    ['ChipCrystalSoC', 'chip_crystal_so_c'],
    ['PikoPIC', 'piko_pic'],
    ['CableOpticalFiber', 'cable_optical_fiber'],
];
for (const [inp, want] of SNAKE_CASES) {
    if (snake(inp) !== want) {
        console.error(`snake() 规则锚点不符：snake("${inp}") = "${snake(inp)}"，期望 "${want}"`);
        process.exit(1);
    }
}

// ---------------------------------------------------------------- 真实物品：贴图基底 + 名字引用
/** NH ItemList 常量 → GTCEu 1.20.1 物品 id（贴图 + 名字都走 GTCEu 自己的资源）。 */
const ITEM_MAP = {
    Circuit_Parts_ResistorASMD: ['advanced_smd_resistor', 'exact'],
    Circuit_Parts_TransistorASMD: ['advanced_smd_transistor', 'exact'],
    Circuit_Parts_InductorASMD: ['advanced_smd_inductor', 'exact'],
    Circuit_Parts_CapacitorASMD: ['advanced_smd_capacitor', 'exact'],
    Circuit_Parts_DiodeASMD: ['advanced_smd_diode', 'exact'],
    Circuit_Board_Multifiberglass_Elite: ['multilayer_fiber_reinforced_circuit_board', '近似'],
    Circuit_Board_Wetware_Extreme: ['wetware_circuit_board', 'exact'],
    Circuit_Chip_NeuroCPU: ['neuro_processing_unit', 'exact'],
    Circuit_Chip_NanoCPU: ['nano_cpu_chip', 'exact'],
    Circuit_Chip_Ram: ['ram_chip', 'exact'],
    Circuit_Chip_NOR: ['nor_memory_chip', 'exact'],
    Circuit_Chip_NAND: ['nand_memory_chip', 'exact'],
    Circuit_Chip_SoC2: ['advanced_soc', 'exact'],
    Circuit_Chip_CrystalCPU: ['crystal_cpu', 'exact'],
    Circuit_Chip_CrystalSoC: ['crystal_soc', 'exact'],
    Circuit_Chip_CrystalSoC2: ['raw_crystal_chip', '近似'],
    Circuit_Crystalprocessor: ['crystal_processor', 'exact'],
    Circuit_Crystalcomputer: ['crystal_processor_assembly', 'exact'],
    Circuit_Ultimatecrystalcomputer: ['crystal_processor_computer', 'exact'],
    Circuit_Crystalmainframe: ['crystal_processor_mainframe', 'exact'],
    Circuit_Neuroprocessor: ['wetware_processor', 'exact'],
    Circuit_Wetwarecomputer: ['wetware_processor_assembly', 'exact'],
    Circuit_Wetwaresupercomputer: ['wetware_processor_computer', 'exact'],
    Circuit_Wetwaremainframe: ['wetware_processor_mainframe', 'exact'],
};
/** NH Materials 常量 → GTCEu 1.4.4 材质 id（以 GTCEu 自带 lang 键 material.gtceu.<id> 存在为准）。 */
const MAT_MAP = {
    Aluminium: 'aluminium',
    NiobiumTitanium: 'niobium_titanium',
    Polybenzimidazole: 'polybenzimidazole',
    RubberSilicone: 'silicone_rubber',            // NH 名 ↔ GTCEu 名不同，显式映射
    StyreneButadieneRubber: 'styrene_butadiene_rubber',
    Tritanium: 'tritanium',
    YttriumBariumCuprate: 'yttrium_barium_cuprate',
};
/** NH OrePrefixes 常量 → { tag: GTCEu tagprefix lang 键后缀, tex: GTCEu 贴图路径（null=无） } */
const PREFIX_MAP = {
    wireFine: { tag: 'fine_wire', tex: 'gtceu:item/material_sets/dull/wire_fine', word: 'Wire' },
    wireGt01: { tag: 'wire_gt_single', tex: 'gtceu:item/material_sets/dull/wire_fine', word: 'Wire' },
    bolt: { tag: 'bolt', tex: 'gtceu:item/material_sets/dull/bolt', word: 'Bolt' },
    foil: { tag: 'foil', tex: 'gtceu:item/material_sets/dull/foil', word: 'Foil' },
    plate: { tag: 'plate', tex: 'gtceu:item/material_sets/dull/plate', word: 'Plate' },
    screw: { tag: 'screw', tex: 'gtceu:item/material_sets/dull/screw', word: 'Screw' },
    frameGt: { tag: 'frame', tex: 'gtceu:block/material_sets/dull/frame_gt', word: 'Framebox' },
    itemCasing: { tag: null, tex: null, word: 'Casing' },
};
/** GT++ Material 取件方法 → 等价的 NH 前缀（`_gtnh` dump 里的 Other 类 6 条）。 */
const GTPP_PREFIX = { getFineWire: 'wireFine', getFrameBox: 'frameGt', getBolt: 'bolt', getScrew: 'screw', getPlate: 'plate' };
/** 材质英文/中文名（**自撰**；en 能引 NH processed 名的已在注释里注明出处）。 */
const MAT_NAME = {
    ProtoHalkonite: ['Proto-Halkonite Steel', '原初哈氏钢'],       // en ← NH gt.circuitcomponent.processed.wireprotohalkonite
    SpaceTime: ['Spacetime', '时空'],                              // en ← NH …processed.wirespacetime
    Infinity: ['Infinity', '无尽'],                                // en ← NH …processed.wireinfinity
    MagMatter: ['Magmatter', '磁物质'],                            // en ← NH …processed.wiremagmatter / frame.magmatter
    SuperconductorLuV: ['Superconductor LuV', 'LuV 超导体'],
    SuperconductorZPM: ['Superconductor ZPM', 'ZPM 超导体'],
    SuperconductorUV: ['Superconductor UV', 'UV 超导体'],
    SuperconductorUHV: ['Superconductor UHV', 'UHV 超导体'],
    SuperconductorUEV: ['Superconductor UEV', 'UEV 超导体'],
    SuperconductorUIV: ['Superconductor UIV', 'UIV 超导体'],
    SuperconductorUMV: ['Superconductor UMV', 'UMV 超导体'],
    SuperconductorUMVBase: ['Superconductor Base UMV', 'UMV 超导体基底'],
    SuperconductorUEVBase: ['Superconductor Base UEV', 'UEV 超导体基底'],
    Aluminium: ['Aluminium', '铝'],
    Tritanium: ['Tritanium', '三钛'],
    YttriumBariumCuprate: ['Yttrium Barium Cuprate', '钇钡铜氧化物'],
    EnrichedHolmium: ['Enriched Holmium', '富集钬'],
    TranscendentMetal: ['Transcendent Metal', '超越金属'],
    CosmicNeutronium: ['Cosmic Neutronium', '宇宙中子素'],
    SixPhasedCopper: ['Six-Phased Copper', '六相铜'],
    Creon: ['Creon', '克利昂'],
    WhiteDwarfMatter: ['White Dwarf Matter', '白矮星物质'],
    Hexanite: ['Hexanite', '六方石'],
    BlackDwarfMatter: ['Black Dwarf Matter', '黑矮星物质'],
    Universium: ['Universium', '宇宙素'],
    Eternity: ['Eternity', '永恒'],
    RubberSilicone: ['Silicone Rubber', '硅橡胶'],
    Polybenzimidazole: ['Polybenzimidazole', '聚苯并咪唑'],
    StyreneButadieneRubber: ['Styrene Butadiene Rubber', '丁苯橡胶'],
    RadoxPolymer: ['Radox Polymer', 'Radox 聚合物'],
    Shirabon: ['Shirabon', '希拉邦'],
    Lumiium: ['Lumiium', '流明合金'],
    MetastableOganesson: ['Metastable Oganesson', '亚稳态鿫'],
    Hypogen: ['Hypogen', '海伯根'],
    CelestialTungsten: ['Celestial Tungsten', '天钨'],
    ChromaticGlass: ['Chromatic Glass', '彩光玻璃'],
    AstralTitanium: ['Astral Titanium', '星辉钛'],
    Rhugnor: ['Rhugnor', '鲁格诺'],
};
/** 枚举名里的材质 token 与 NH Materials 常量名不一致时的别名（`CasingUEVSuperconductor` 等）。 */
const MAT_ALIAS = { UEVSuperconductor: 'SuperconductorUEVBase', UMVSuperconductor: 'SuperconductorUMVBase', Spacetime: 'SpaceTime' };
/** 非「前缀+材质」的真实物品名（**自撰**；能引 NH processed 名的已注出）。 */
const ITEM_NAME = {
    Circuit_Parts_ResistorXSMD: ['Optical SMD Resistor', '光学贴片电阻'],          // NH …processed.xsmd.resistor 去动词
    Circuit_Parts_TransistorXSMD: ['Optical SMD Transistor', '光学贴片晶体管'],
    Circuit_Parts_InductorXSMD: ['Optical SMD Inductor', '光学贴片电感'],
    Circuit_Parts_CapacitorXSMD: ['Optical SMD Capacitor', '光学贴片电容'],
    Circuit_Parts_DiodeXSMD: ['Optical SMD Diode', '光学贴片二极管'],
    Circuit_Board_Bio_Ultra: ['Ultra Bio Mutated Circuit Board', '超级生物突变电路板'], // NH …processed.board.biomutated
    Circuit_Board_Optical: ['Optical Circuit Board', '光学电路板'],                  // NH …processed.board.optical
    Circuit_Chip_BioCPU: ['Bio Processing Unit', '生物处理单元'],                    // NH …processed.board.bioprocessingunit
    Circuit_Parts_Chip_Bioware: ['Living Bio Chip', '活体生物芯片'],                 // NH …processed.board.livingbiochip
    Circuit_Chip_PPIC: ['Piko Power IC Chip', 'Piko 电源芯片'],                      // NH …processed.chippikopic
    Circuit_Chip_QPIC: ['Quantum Power IC Chip', '量子电源芯片'],                    // NH …processed.chipquantumpic
    Circuit_Chip_APIC: ['Atto Power IC Chip', '阿托电源芯片'],                       // NH …processed.chipattopic
    Circuit_Chip_ZPIC: ['Zepto Power IC Chip', '仄托电源芯片'],                      // NH …processed.chipzeptopic
    Circuit_Chip_YPIC: ['Yocto Power IC Chip', '幺托电源芯片'],                      // NH …processed.chipyoctopic
    Circuit_Chip_PlPIC: ['Planck Power IC Chip', '普朗克电源芯片'],                  // NH …processed.chipplanckpic
    Optically_Perfected_CPU: ['Optically Perfected CPU', '光学完善处理器'],           // NH …processed.opticalcpu
    Optically_Compatible_Memory: ['Optically Compatible Memory', '光学兼容内存'],   // NH …processed.opticalram
    Circuit_Bioprocessor: ['Bioware Processor', '生物处理器'],
    Circuit_Biowarecomputer: ['Bioware Assembly', '生物组装电路'],
    Circuit_Biowaresupercomputer: ['Bioware Computer', '生物计算机'],
    Circuit_Biomainframe: ['Bioware Mainframe', '生物主机'],
    Circuit_OpticalProcessor: ['Optical Processor', '光学处理器'],
    Circuit_OpticalAssembly: ['Optical Assembly', '光学组装电路'],
    Circuit_OpticalComputer: ['Optical Computer', '光学计算机'],
    Circuit_OpticalMainframe: ['Optical Mainframe', '光学主机'],
    Circuit_Parts_Crystal_Chip_Wetware: ['Living Crystal Chip', '活体晶体芯片'],     // NH …processed.chiplivingcrystal
    Thermal_Superconductor: ['Thermal Superconductor', '热超导体'],                  // NH …other.processed.coiledthermalsuperconductor
    Planck_Manifold: ['Planck Manifold', '普朗克流形'],
    DATApipe: ['Optical Fiber Cable', '光纤线缆'],                                   // NH …cable.processed.opticalfiber
    PikoCircuit: ['Pico Circuit', 'Pico 电路'],                                      // NH getModItem 的物品名（注意拼写是 Piko）
    QuantumCircuit: ['Quantum Circuit', '量子电路'],
    PlanckCircuit: ['Planck Circuit', '普朗克电路'],
};
/** processed 英文名去「处理动词」→ 未处理件英文名（NH lang 自身可反推 ⇒ NH 口径）。 */
const VERB = ['Spooled ', 'Prepared ', 'Purified ', 'Coordinated ', 'Cut ', 'Etched ', 'Organized ',
    'Attuned ', 'Spliced ', 'Filed ', 'Stripped ', 'Split '];
/** 已处理件的**自译**中文（逐条，交付说明里单列）。 */
const ZH_PROCESSED = {
    ProcessedWireNiobiumTitanium: '铌钛合金线轴',
    ProcessedWireYttriumBariumCuprate: '钇钡铜氧化物线轴',
    ProcessedWireLumiium: '流明合金线轴',
    ProcessedWireProtoHalkonite: '原初哈氏钢线轴',
    // ★ 2026-09-21 移除 `ProcessedWireSpacetime: '时空线轴'` —— 该件已进入 RETIRED（时空导线链退役）。
    //   本表只对**在 plan 里的**记录生效，退役件根本不查表；移除只为不留"看起来还会产出"的死数据。
    ProcessedCableOpticalFiber: '分股光纤线缆',
    ProcessedWireInfinity: '无尽线轴',
    ProcessedWireHypogen: '海伯根线轴',
    ProcessedWireMagMatter: '磁物质线轴',
    ProcessedAdvSMDResistor: '制好的高级贴片电阻',
    ProcessedAdvSMDTransistor: '制好的高级贴片晶体管',
    ProcessedAdvSMDInductor: '制好的高级贴片电感',
    ProcessedAdvSMDCapacitor: '制好的高级贴片电容',
    ProcessedAdvSMDDiode: '制好的高级贴片二极管',
    ProcessedOpticalSMDResistor: '制好的光学贴片电阻',
    ProcessedOpticalSMDTransistor: '制好的光学贴片晶体管',
    ProcessedOpticalSMDInductor: '制好的光学贴片电感',
    ProcessedOpticalSMDCapacitor: '制好的光学贴片电容',
    ProcessedOpticalSMDDiode: '制好的光学贴片二极管',
    ProcessedBoardMultifiberglassElite: '提纯的精英电路基板',
    ProcessedBoardWetwareLifesupport: '提纯的极限湿件生命维持电路基板',
    ProcessedBoardBioMutated: '提纯的超级生物突变电路基板',
    ProcessedBoardOptical: '提纯的光学电路基板',
    ProcessedNeuroProcessingUnit: '协调的神经处理单元',
    ProcessedBioProcessingUnit: '协调的生物处理单元',
    ProcessedLivingBioChip: '协调的活体生物芯片',
    ProcessedChipNanoCPU: '切割的纳米元件 CPU 芯片',
    ProcessedChipRAM: '切割的随机存取存储器芯片',
    ProcessedChipNOR: '切割的 NOR 存储芯片',
    ProcessedChipNAND: '切割的 NAND 存储芯片',
    ProcessedChipASOC: '切割的 ASoC 芯片',
    ProcessedChipPikoPIC: '切割的 Piko 电源芯片',
    ProcessedChipQuantumPIC: '切割的量子电源芯片',
    ProcessedChipAttoPIC: '切割的阿托电源芯片',
    ProcessedChipZeptoPIC: '切割的仄托电源芯片',
    ProcessedChipYoctoPIC: '切割的幺托电源芯片',
    ProcessedChipPlanckPIC: '切割的普朗克电源芯片',
    ProcessedChipCrystalCPU: '蚀刻的晶体处理单元',
    ProcessedChipCrystalSoC: '蚀刻的晶体 SoC',
    ProcessedChipLivingCrystal: '协调的活体晶体芯片',
    ProcessedChipRawAdvancedCrystal: '协调的粗制高级晶体芯片',
    ProcessedChipOpticalCPU: '整理的光学完善 CPU',
    ProcessedOpticalRAM: '整理的光学兼容内存',
    ProcessedSuperconductorLuV: '调谐的 LuV 超导线',
    ProcessedSuperconductorZPM: '调谐的 ZPM 超导线',
    ProcessedSuperconductorUV: '调谐的 UV 超导线',
    ProcessedSuperconductorUHV: '调谐的 UHV 超导线',
    ProcessedSuperconductorUEV: '调谐的 UEV 超导线',
    ProcessedSuperconductorUIV: '调谐的 UIV 超导线',
    ProcessedSuperconductorUMV: '调谐的 UMV 超导线',
    ProcessedFrameboxAluminium: '拼接的铝框架',
    ProcessedFrameboxTritanium: '拼接的三钛框架',
    ProcessedFrameboxCelestialTungsten: '拼接的天钨框架',
    ProcessedFrameboxHypogen: '拼接的海伯根框架',
    ProcessedFrameboxMagMatter: '拼接的磁物质框架',
    ProcessedFoilSiliconeRubber: '硅橡胶包裹线轴',
    ProcessedFoilPolybenzimidazole: '聚苯并咪唑包裹线轴',
    ProcessedFoilRadoxPolymer: 'Radox 聚合物包裹线轴',
    ProcessedFoilShirabon: '希拉邦包裹线轴',
    ProcessedFoilStyreneRubber: '丁苯橡胶包裹线轴',
    ProcessedFoilEternity: '永恒包裹线轴',
    ProcessedBoltEnrichedHolmium: '锉制的富集钬螺栓',
    ProcessedBoltTranscendentMetal: '锉制的超越金属螺栓',
    ProcessedBoltYttriumBariumCuprate: '锉制的钇钡铜氧化物螺栓',
    ProcessedBoltCosmicNeutronium: '锉制的宇宙中子素螺栓',
    ProcessedBoltChromaticGlass: '锉制的彩光玻璃螺栓',
    ProcessedBoltUMVSuperconductor: '锉制的 UMV 超导体基底螺栓',
    ProcessedBoltWhiteDwarfMatter: '锉制的白矮星物质螺栓',
    ProcessedPlateRhugnor: '剥离的鲁格诺板',
    ProcessedPlateMetastableOganesson: '剥离的亚稳态鿫板',
    ProcessedPlateHexanite: '剥离的六方石板',
    ProcessedBasicMainframeCasing: '基础主机电路封装',
    ProcessedAdvancedMainframeCasing: '高级主机电路封装',
    ProcessedPicoCircuitCasing: 'Pico 电路封装',
    ProcessedQuantumCircuitCasing: '量子电路封装',
    ProcessedPlanckCircuitCasing: '普朗克尺度电路封装',
    ProcessedCoiledThermalSuperconductor: '盘绕的热超导体',
};

// ---------------------------------------------------------------- 组装 166 条 NH 段计划（= NH 原始 176 − 退役 10）
const siblingOf = new Map();      // 未处理 enumName → 对应的「已处理」record（NH componentForProcessed）
// ★ 2026-09-21：本索引**必须用原始全集 `ALL_RECORDS`(176) 建，不能用过滤后的 RECORDS(166)** ——
//   它只被用作「命名反推」的证据（读 `sib.nameKey` 去查 NH en_US.lang），不参与产出。
//   反例（本轮实测踩到）：`ChipPlanckPIC`(610) 的未处理英文名靠**已退役的**孪生件
//   `ProcessedChipPlanckPIC`(710) 的 NH lang 键 `gt.circuitcomponent.processed.chipplanckpic`
//   （"Cut Planck Power IC Chips"）去掉处理动词才能得到 "Packaged Planck Power IC Chips"；
//   一旦兄弟在过滤后的集合里找不到，就会掉到 GTCEu 物品名的**单数** "…Power IC Chip"，
//   让未退役的 610 号件显示名悄悄退化（生成器输出与落地态出现 1 行差异）。
for (const r of ALL_RECORDS) {
    if (!r.isProcessed || !r.componentFor) continue;
    const m = /CircuitComponent\.([A-Za-z0-9_]+)$/.exec(r.componentFor);
    if (m) siblingOf.set(m[1], r);
}
const kindKey = r => r.realComponentKind === 'ItemList' ? r.itemListItem
    : r.realComponentKind === 'CustomItemList' ? String(r.customListItem).split('.').pop()
        : r.realComponentKind === 'ModItem' ? r.modItemName : null;
const prefixOf = r => r.orePrefix || (r.realComponentKind === 'Other'
    ? GTPP_PREFIX[/\.(get[A-Za-z]+)$/.exec(r.realComponent || '') ? /\.(get[A-Za-z]+)$/.exec(r.realComponent)[1] : ''] || null
    : null);
const materialOf = (r, prefix) => {
    if (r.material) return r.material;
    const word = prefix && PREFIX_MAP[prefix] ? PREFIX_MAP[prefix].word : null;
    if (word && r.enumName.startsWith(word)) {
        const token = r.enumName.slice(word.length);
        return MAT_ALIAS[token] || token;
    }
    return null;
};
const humanize = n => n.replace(/([a-z0-9])([A-Z])/g, '$1 $2').replace(/([A-Z]+)([A-Z][a-z])/g, '$1 $2');

const plan = [];
for (const r of RECORDS) {
    const f = [];
    const p = {
        enumName: r.enumName, metaId: r.metaId, isProcessed: r.isProcessed,
        nameKey: r.nameKey,
        iconString: r.isProcessed ? 'processed/' + r.enumName.toLowerCase() : r.enumName.toLowerCase(),
        itemId: itemIdOf(r.enumName),
        calibration: r.calibration && r.calibration !== 'NONE' ? r.calibration : null,
        realComponent: r.realComponent, realComponentKind: r.realComponentKind,
        realComponentLine: r.lambdaLine ? `_gtnh/dump3/CircuitComponent.txt:${r.lambdaLine}` : null,
        evidence: {
            dump: `_gtnh/dump3/CircuitComponent.txt:${r.clinitLine}-${r.clinitEndLine}`,
            decl: `_gtnh/dump3/CircuitComponent.txt:${r.declLine}`,
            lambda: r.lambdaLine ? `_gtnh/dump3/CircuitComponent.txt:${r.lambdaLine}` : null,
            nhLang: nhLang.has(r.nameKey) ? `_gtnh/jarfull/assets/gregtech/lang/en_US.lang → ${r.nameKey}` : null,
        },
        texture: null, textureSource: null, textureNote: null,
        realRef: null, realRefBasis: null,
        en: null, zh: null, enSource: null, zhSource: null, flags: [],
    };

    if (r.isProcessed) {
        const fname = r.enumName.toLowerCase() + '.png';
        const srcRel = `_gtnh/jarfull/assets/gregtech/textures/items/gt.circuitcomponent/processed/${fname}`;
        // ★ 2026-11-14：`_gtnh/jarfull` 已不在本机 ⇒ NH 源贴图查不到。
        //   但**成品贴图早就拷进本仓库了**（`assets/gtlmodular/textures/item/cc/<itemId>.png`），
        //   这里改为两处都看：NH 源在就用源，不在就用仓库里已拷好的成品图。
        const ownPng = path.join(ROOT, 'gtl-modular/src/main/resources/assets/gtlmodular/textures/item/cc', p.itemId + '.png');
        if (fs.existsSync(path.join(NH_TEX, 'processed', fname)) || fs.existsSync(ownPng)) {
            p.texture = { kind: 'nhProcessed', nhFile: srcRel, png: `assets/gtlmodular/textures/item/cc/${p.itemId}.png` };
            p.textureSource = fs.existsSync(path.join(NH_TEX, 'processed', fname)) ? `NH ${srcRel}` : `仓库已有成品图 ${p.itemId}.png`;
        } else {
            p.texture = { kind: 'blank', png: `assets/gtlmodular/textures/item/cc/packaged_blank.png`, overlay: true };
            p.textureSource = '自造底板 + NH overlay';
            f.push(`! 缺 NH processed 贴图（期望 processed/${fname}）`);
        }
        const nhEn = nhLang.get(r.nameKey);
        if (nhEn) { p.en = nhEn; p.enSource = `NH en_US.lang（${r.nameKey}）原文`; }
        else { p.en = humanize(r.enumName.replace(/^Processed/, '')); p.enSource = '自撰（NH lang 无此键）'; f.push(`! NH lang 无键 ${r.nameKey}`); }
        const zh = ZH_PROCESSED[r.enumName];
        if (zh) { p.zh = zh; p.zhSource = '自译'; }
        else { p.zh = p.en; p.zhSource = '自撰（缺自译条目，暂置英文）'; f.push('! 缺自译中文名'); }
        if (!r.componentFor) f.push('processed 无未处理件（NH componentForProcessed = null）');
    } else {
        const prefix = prefixOf(r);
        const pm = prefix ? PREFIX_MAP[prefix] : null;
        const matNH = materialOf(r, prefix);
        const matId = matNH ? MAT_MAP[matNH] : null;
        const key = kindKey(r);

        // ---- 贴图基底
        if (key && ITEM_MAP[key]) {
            const [gid, basis] = ITEM_MAP[key];
            p.texture = { kind: 'gtceuItem', base: `gtceu:item/${gid}`, overlay: true };
            p.textureSource = `GTCEu 1.20.1 物品贴图 gtceu:item/${gid}.png`;
            p.realRef = `item:${gid}`; p.realRefBasis = basis;
            if (basis !== 'exact') { p.textureNote = `近似映射：NH ${key} ↔ GTCEu ${gid}`; f.push(`! 真实物品为近似映射（${key} → ${gid}）`); }
        } else if (pm && pm.tex) {
            p.texture = { kind: 'gtceuMaterial', base: pm.tex, overlay: true };
            p.textureSource = `GTCEu 1.20.1 材质母版 ${pm.tex}.png`;
            if (!r.material) f.push('! 材质来自 GT++ 取件方法，未逐一解析（统一用 GTCEu DULL 母版）');
        } else {
            p.texture = { kind: 'blank', png: `assets/gtlmodular/textures/item/cc/packaged_blank.png`, overlay: true };
            p.textureSource = '自造底板（自造灰底圆角板）+ NH overlay';
            f.push('! 无可用真实物品贴图，退化自造底板');
        }
        if (!p.realRef && matId && pm && pm.tag) { p.realRef = `mat:${matId}:${pm.tag}`; p.realRefBasis = 'exact'; }

        // ---- 显示名
        if (p.realRef) {
            const compose = (lang, wrap) => p.realRef.startsWith('item:')
                ? wrap + lang['item.gtceu.' + p.realRef.slice(5)]
                : wrap + lang['tagprefix.' + pm.tag].replace('%s', lang['material.gtceu.' + matId]);
            p.en = compose(gtEn, 'Packaged ');
            p.zh = compose(gtZh, '封装 ');
            p.enSource = 'GTCEu lang 组合（NH 的 gt.circuitcomponent.base 模板 + GTCEu 物品/材质名）';
            p.zhSource = 'GTCEu lang 组合（GTCEu zh_cn.json）';
            f.push('真实物品名由 GTCEu 1.20.1 资源组合（NH 侧该真实物品在 1.20.1 不存在）');
        } else {
            const sib = siblingOf.get(r.enumName);
            let realEn = null, enSrc = null;
            // 优先级 1：NH 自己的 oreprefix 命名规则（真实物品名 = gt.oreprefix.<前缀> 模板 + 材质名）
            if (prefix && matNH && MAT_NAME[matNH]) {
                const pe = { bolt: '%s Bolt', foil: '%s Foil', plate: '%s Plate', screw: '%s Screw', frameGt: '%s Frame Box', itemCasing: '%s Casing', wireFine: 'Fine %s Wire', wireGt01: '1x %s Wire' }[prefix];
                realEn = pe.replace('%s', MAT_NAME[matNH][0]);
                enSrc = `NH gt.oreprefix 模板 + 自撰材质名（${matNH}）`;
            }
            // 优先级 2：NH processed 名去处理动词（NH lang 自身可反推）
            if (!realEn && sib) {
                const nhEn = nhLang.get(sib.nameKey);
                if (nhEn) {
                    let s = nhEn;
                    for (const v of VERB) if (s.startsWith(v)) { s = s.slice(v.length); break; }
                    if (s !== nhEn) { realEn = s; enSrc = `NH en_US.lang（${sib.nameKey}）去处理动词`; }
                }
            }
            if (!realEn) {
                if (key && ITEM_NAME[key]) { realEn = ITEM_NAME[key][0]; enSrc = '自撰（真实物品拿不到 NH 资源）'; }
                else { realEn = humanize(r.enumName); enSrc = '自撰（真实物品拿不到 NH 资源）'; }
            }
            let realZh = null, zhSrc = '自译';
            if (prefix && matNH && MAT_NAME[matNH]) {
                const pz = { bolt: '%s螺栓', foil: '%s箔', plate: '%s板', screw: '%s螺丝', frameGt: '%s框架', itemCasing: '%s外壳', wireFine: '细%s导线', wireGt01: '1x%s导线' }[prefix];
                realZh = pz.replace('%s', MAT_NAME[matNH][1]);
            } else if (key && ITEM_NAME[key]) realZh = ITEM_NAME[key][1];
            if (!realZh) { realZh = 'CC'; zhSrc = '自撰（拿不到真实物品中文名 ⇒ 退化「封装 CC」）'; f.push('! 中文名退化为「封装 CC」'); }
            p.en = `Packaged ${realEn}`; p.enSource = enSrc;
            p.zh = `封装 ${realZh}`; p.zhSource = zhSrc;
            f.push('! 真实物品名拿不到 NH 资源（降级：自撰/退化）');
        }
    }
    if (p.calibration) f.push(`校准族 ${p.calibration}（本波只登记，不做配方）`);
    if (!p.isProcessed) f.push('未处理件的「真实物品」在 1.20.1 不存在，显示名按降级规则给出');
    p.flags = [...new Set(f)];
    plan.push(p);
}

// ---------------------------------------------------------------- ★★ 波2 命名规范化 + 并入 plan（2026-09-18）
/**
 * 玩家诉求（2026-09-18）：中文名录必须「无误」，且在游戏里必须能**分清未处理件(CC)/已处理件(PC)**。
 * 上一版波2 的 197 件是「裸真件名」——94 对 CC/PC **中文名完全相同**、且 0 条带「封装」前缀，
 * 与 NH 段 166 件的实证范式不符（在 NAC 里看名字根本分不出哪件是封装、哪件是已处理）。
 *
 * 现读 NH 段 166 件（`_census/nac_cc_items.json`）得到的**冻结范式**：
 *   CC（未处理）= 「封装 <真件名>」/「Packaged <Real Name>」
 *      —— NH 自己就是这么定的：`gt.circuitcomponent.base` = "Packaged %s"，NH 段 166/166 全带「封装 」；
 *   PC（已处理）= 「<品类动词><真件名>」，动词**逐类从 NH 段取证**（下表 `from` 即取证条目）：
 *     smd  制好的/Prepared   ← NH ProcessedSMDAdvancedResistor「制好的高级贴片电阻」
 *     chip 切割的/Cut        ← NH ProcessedChipNanoCPU「切割的纳米元件 CPU 芯片」
 *     board 提纯的/Purified  ← NH ProcessedBoardMultifiberglassElite「提纯的精英电路基板」
 *     frame 拼接的/Spliced   ← NH ProcessedFrameboxAluminium「拼接的铝框架」
 *     bolt/screw 锉制的/Filed ← NH ProcessedBoltEnrichedHolmium「锉制的富集钬螺栓」（螺丝无 PC 先例，取同形最近的螺栓）
 *     plate 剥离的/Stripped  ← NH ProcessedPlateMetastableOganesson「剥离的亚稳态鿫板」
 *     part  协调的/Coordinated ← NH ProcessedNeuroProcessingUnit「协调的神经处理单元」（族专属核心/单元/星等归此类）
 *     wire  <材料>线轴/Spooled <Mat> Wire       ← NH ProcessedWireNiobiumTitanium「铌钛合金线轴」
 *     foil  <材料>包裹线轴/<Mat> Wrapper Spool  ← NH ProcessedFoilSiliconeRubber「硅橡胶包裹线轴」
 * ⚠️ NH 表里**没有** exotic/cosmic/supracausal 的 CC（只有已退役的 Pico/Quantum/Planck 4 件），
 *    所以自造段的 PC 动词只能是「按品类照最接近的 NH 先例」，每类的取证条目写在上表的 `from` 里。
 * ⚠️ 英文名证据边界：kubejs 命名空间**只有 zh_cn、没有 en_us**（KubeJS 运行时按 id 自动生成，未落文件）
 *    ⇒ 这些行的 en 按 id 词序标题化，`enSource` 如实标注「无书面 en 证据」。
 */
const W2_VERB = {
    smd: { zh: '制好的', en: 'Prepared', from: 'NH ProcessedSMDAdvancedResistor = 制好的高级贴片电阻 / Prepared Advanced SMD Resistor' },
    chip: { zh: '切割的', en: 'Cut', from: 'NH ProcessedChipNanoCPU = 切割的纳米元件 CPU 芯片 / Cut Nanocomponent CPU Chips' },
    board: { zh: '提纯的', en: 'Purified', from: 'NH ProcessedBoardMultifiberglassElite = 提纯的精英电路基板 / Purified Elite Circuit Board' },
    frame: { zh: '拼接的', en: 'Spliced', from: 'NH ProcessedFrameboxAluminium = 拼接的铝框架 / Spliced Aluminium Framebox' },
    bolt: { zh: '锉制的', en: 'Filed', from: 'NH ProcessedBoltEnrichedHolmium = 锉制的富集钬螺栓 / Filed Enriched Holmium Bolt' },
    screw: { zh: '锉制的', en: 'Filed', from: '与螺栓同形，取最近先例 NH ProcessedBoltEnrichedHolmium（NH 无螺丝 PC）' },
    plate: { zh: '剥离的', en: 'Stripped', from: 'NH ProcessedPlateMetastableOganesson = 剥离的亚稳态鿫板 / Stripped Metastable Oganesson Plate' },
    part: { zh: '协调的', en: 'Coordinated', from: 'NH ProcessedNeuroProcessingUnit = 协调的神经处理单元 / Coordinated Neuro Processing Unit' },
    wire: { zh: '%s线轴', en: 'Spooled %s Wire', from: 'NH ProcessedWireNiobiumTitanium = 铌钛合金线轴 / Spooled Niobium-Titanium Wire' },
    foil: { zh: '%s包裹线轴', en: '%s Wrapper Spool', from: 'NH ProcessedFoilSiliconeRubber = 硅橡胶包裹线轴 / Silicone Rubber Wrapper Spool' },
};
/** 品类判定（只看形状词；与 `_probe_wave2_spec.js` 的 classify() 同源）。
 *  ★ 两种命名都要认：GTL 材料派生物是**后缀式**（`polyetheretherketone_foil`），
 *    NH 枚举名转来的 id 是**前缀式**（`FoilShirabon` → `foil_shirabon`）；漏了前缀式会全归 part。 */
function w2Cat(body) {
    if (/_foil$/.test(body) || /^foil_/.test(body)) return 'foil';
    if (/_wire$/.test(body) || /_cable$/.test(body) || /^wire_/.test(body) || /^cable_/.test(body)) return 'wire';
    if (/(^|_)smd_/.test(body)) return 'smd';
    if (/_board$/.test(body) || /^board_/.test(body)) return 'board';
    if (/_frame$/.test(body) || /^framebox_/.test(body) || /^frame_/.test(body)) return 'frame';
    if (/_bolt$/.test(body) || /^bolt_/.test(body)) return 'bolt';
    if (/_screw$/.test(body) || /^screw_/.test(body)) return 'screw';
    if (/_plate$/.test(body) || /^plate_/.test(body)) return 'plate';
    if (/_soc$/.test(body) || /_chip$/.test(body) || /_pic$/.test(body) || /^chip_/.test(body)) return 'chip';
    return 'part';
}
/** 无书面 en 证据时的标题化（保留已知缩写）。 */
const ACR = { smd: 'SMD', cpu: 'CPU', ram: 'RAM', soc: 'SoC', pic: 'PIC', hpic: 'HPIC', uhpic: 'UHPIC', ctc: 'CTC', nor: 'NOR', nand: 'NAND', hasoc: 'HASoC' };
const titleize = (s) => s.split(/\s+/).filter(Boolean).map((w) => ACR[w.toLowerCase()] || (w[0].toUpperCase() + w.slice(1))).join(' ');
for (const p of EXTRA) {
    const src = (p._wave2 && p._wave2.sourceItem) || '';
    const body = String(src).split(':')[1] || '';
    const cat = w2Cat(body);
    const baseZh = p.zh;                       // 规格侧已逐条解析并记明出处
    const zhSrcBase = p.zhSource || '波2规格';
    // ---- 真件英文名：优先用合并后的证据 lang；kubejs 无证据则按 id 标题化并如实标注
    let baseEn = null, enSrc = null;
    if (p.realRef.startsWith('item:')) {
        const gid = p.realRef.slice(5);
        if (gtEnMap.get('item.gtceu.' + gid)) { baseEn = gtEnMap.get('item.gtceu.' + gid); enSrc = 'GTCEu/GTL 证据 lang（item.gtceu.' + gid + '）'; }
    } else if (p.realRef.startsWith('mat:')) {
        const [, mat, tag] = p.realRef.split(':');
        const mt = gtEnMap.get('tagprefix.' + tag), mn = gtEnMap.get('material.gtceu.' + mat);
        if (mt && mn) { baseEn = mt.replace('%s', mn); enSrc = 'GTCEu/GTL 证据 lang（tagprefix.' + tag + ' + material.gtceu.' + mat + '）'; }
    }
    let enKube = false;
    if (!baseEn) {
        baseEn = titleize(body.replace(/_/g, ' '));
        enKube = true;
        enSrc = p.realRef.startsWith('itemns:')
            ? '无书面 en 证据（kubejs 命名空间只提供 zh_cn，无 en_us；按源 id 词序标题化）'
            : '无书面 en 证据（按源 id 词序标题化）';
    }
    // ---- 套范式
    const v = W2_VERB[cat];
    let zh, en, verbLabel;
    if (!p.isProcessed) {
        zh = '封装 ' + baseZh; en = 'Packaged ' + baseEn; verbLabel = '封装 ';
    } else if (cat === 'wire') {
        const mz = baseZh.replace(/^(?:细|1x|2x|4x|8x|16x)/, '').replace(/导线$/, '');
        const me = baseEn.replace(/^(?:Fine|1x|2x|4x|8x|16x)\s*/i, '').replace(/\s*Wire$/i, '').trim();
        zh = mz + '线轴'; en = 'Spooled ' + me + ' Wire'; verbLabel = '<材料>线轴 ';
    } else if (cat === 'foil') {
        const mz = baseZh.replace(/箔$/, ''), me = baseEn.replace(/\s*Foil$/i, '').trim();
        zh = mz + '包裹线轴'; en = me + ' Wrapper Spool'; verbLabel = '<材料>包裹线轴 ';
    } else {
        zh = v.zh + baseZh; en = v.en + ' ' + baseEn; verbLabel = v.zh;
    }
    p.zh = zh; p.en = en;
    p.zhSource = `波2 命名规范（CC「封装 %s」/ PC「${verbLabel}%s」，动词品类 ${cat} 取证：${v.from}）+ 真件名出处：${zhSrcBase}`;
    p.enSource = enSrc;
    if (enKube && !p.flags.includes('! 无书面 en 证据（kubejs 侧只有 zh_cn）')) p.flags.push('! 无书面 en 证据（kubejs 侧只有 zh_cn）');
    p.iconString = p.itemId;
    plan.push(p);
}

// ---------------------------------------------------------------- ★★ 显示名归一（2026-09-18 玩家连点两处）
/**
 * 玩家原话：①「海伯根的正确名字是海珀珍，Radox 聚合物应该是拉多X聚合物，希拉邦箔这是个啥？为什么我根本查不到」
 *           ②「GTL 里面没有流明合金」。
 *
 * 两条都指向同一个根因与同一条修法：
 *   · 根因 A：NH 段的显示名只读 **gtceu 本体 jar** 的语言文件，而 hypogen/radox/shirabon 等材料是
 *     **gtlcore jar** 注册的（语言键同样在 `material.gtceu.*` 下）⇒ 查不到就退回前同事手写的旧译名表，
 *     于是出现「海伯根」「Radox 聚合物」「希拉邦」这类**在包里根本查不到**的名字。
 *   · 根因 B：NH 段有 16 条**替身**（NH 的真件在 GTL 不存在，例：流明合金→细宇宙中子素导线、
 *     亚稳态鿫板→海珀珍板），可显示名一直写着 NH 的原名 ⇒ 玩家照名字去 JEI 里找**根本找不到**。
 *
 * 修法（与已冻结的命名规则「封装 <真件名>」完全一致，只是把「真件名」落到**实际采用的真件**上）：
 *   ① 真件 = 换算配方的输入（磁盘上的 `conversion/*.json`，**操作真相**；realRef 为空的那批也覆盖到）；
 *   ② 名字在**合并语言**里查：4 个 GTL jar 的 zh_cn + kubejs zh + **我方 jar 自己**的 zh；
 *   ③ 显示名 = 「封装 <真件名>」；若与 NH 期望名不同（替身/自造件），补 **「（NH：<NH 名>）」**，
 *      这样玩家既能照游戏里拿得到的东西放料，又能拿 NH 原名去维基对号。
 */
{
    const MERGED_ZH = new Map();
    {
        const files = [];
        // ★★ 2026-09-21 修复（父代理裁决 · 收口 O94 点名的既有漂移）★★
        //   `MERGED_ZH` 是**先到先得**，而游戏里的 zh 是**层叠**的：
        //     GTCEu jar（mod 层）       : material.gtceu.dubnium = 钅杜  ← 拆字
        //     整合包 kubejs（资源包层） : material.gtceu.dubnium = 𬭊    ← 官方单字，**覆盖 jar**
        //   原实现只喂了 jar 层证据（`_probe_nacmat_evidence/lang/*__zh_cn.json`）⇒ 拼出拆字，
        //   与 `nac_wave2_spec.json`、落地态、`check_superheavy_names.js` R1（不得出现拆字）三处打架。
        //   ⇒ **把包层覆盖表排在最前**（先到先得 ⇒ 它赢）。证据与说明见 check_superheavy_names.js 头部。
        const packZhFile = path.join(__dirname, '_pack_gtceu_zh_cn.json');
        if (!fs.existsSync(packZhFile)) {
            console.error('缺少包层 zh 证据表 ' + path.basename(packZhFile)
                + ' —— 缺它会把超重元素拼成拆字（check_superheavy_names.js R1 判红）。请先补齐该证据文件。');
            process.exit(1);
        }
        files.push(packZhFile);
        const evDir = path.join(__dirname, '_probe_nacmat_evidence/lang');
        if (fs.existsSync(evDir)) for (const f of fs.readdirSync(evDir)) if (/__zh_cn\.json$/.test(f)) files.push(path.join(evDir, f));
        const kj = path.join(GTL_PACK, 'kubejs/assets/kubejs/lang/zh_cn.json');
        if (fs.existsSync(kj)) files.push(kj);
        const ownLang = path.join(RES, 'assets', 'gtlmodular', 'lang', 'zh_cn.json');
        if (fs.existsSync(ownLang)) files.push(ownLang);
        for (const f of files) {
            let j; try { j = JSON.parse(fs.readFileSync(f, 'utf8').replace(/^\uFEFF/, '')); } catch { continue; }
            for (const [k, v] of Object.entries(j)) if (typeof v === 'string' && !MERGED_ZH.has(k)) MERGED_ZH.set(k, v.replace(/§./g, ''));
        }
    }
    const LANG_TAG2 = {
        fine_wire: 'fine_wire', wire_gt_single: 'wire_gt_single', wire_gt_double: 'wire_gt_double',
        wire_gt_quadruple: 'wire_gt_quadruple', wire_gt_octal: 'wire_gt_octal', wire_gt_hex: 'wire_gt_hex',
        foil: 'foil', bolt: 'bolt', screw: 'screw', plate: 'plate', double_plate: 'double_plate',
        frame: 'frame', rod: 'rod', long_rod: 'long_rod', gear: 'gear', ring: 'ring',
        spring: 'spring', item_casing: 'item_casing',
    };
    const splitMaterial2 = (body) => {
        const out = [];
        let m;
        if ((m = /^fine_(.+)_wire$/.exec(body))) out.push({ tag: 'fine_wire', mat: m[1] });
        if ((m = /^(.+)_single_wire$/.exec(body))) out.push({ tag: 'wire_gt_single', mat: m[1] });
        if ((m = /^(.+)_double_wire$/.exec(body))) out.push({ tag: 'wire_gt_double', mat: m[1] });
        if ((m = /^(.+)_quadruple_wire$/.exec(body))) out.push({ tag: 'wire_gt_quadruple', mat: m[1] });
        if ((m = /^(.+)_octal_wire$/.exec(body))) out.push({ tag: 'wire_gt_octal', mat: m[1] });
        if ((m = /^(.+)_hex_wire$/.exec(body))) out.push({ tag: 'wire_gt_hex', mat: m[1] });
        if ((m = /^double_(.+)_plate$/.exec(body))) out.push({ tag: 'double_plate', mat: m[1] });
        for (const s of ['foil', 'bolt', 'screw', 'plate', 'frame', 'rod', 'gear', 'ring', 'spring', 'item_casing']) {
            if (body.endsWith('_' + s)) out.push({ tag: LANG_TAG2[s] || s, mat: body.slice(0, -(s.length + 1)) });
        }
        return out;
    };
    /** 真件 id → 中文名（查不到返回 null）。 */
    const nameOfReal = (id) => {
        if (!id) return null;
        if (id.startsWith('gtceu:')) {
            const body = id.slice(6);
            for (const c of splitMaterial2(body)) {
                const kt = 'tagprefix.' + c.tag, km = 'material.gtceu.' + c.mat;
                if (MERGED_ZH.has(kt) && MERGED_ZH.has(km)) return MERGED_ZH.get(kt).replace('%s', MERGED_ZH.get(km));
            }
            if (MERGED_ZH.has('item.gtceu.' + body)) return MERGED_ZH.get('item.gtceu.' + body);
            // ★ 方块类真件（例 `gtceu:normal_optical_pipe` = 光缆，键在 `block.gtceu.*`）
            if (MERGED_ZH.has('block.gtceu.' + body)) return MERGED_ZH.get('block.gtceu.' + body);
            return null;
        }
        const ci = id.indexOf(':');
        if (ci < 0) return null;
        const k = 'item.' + id.slice(0, ci) + '.' + id.slice(ci + 1);
        if (MERGED_ZH.has(k)) return MERGED_ZH.get(k);
        // ★ 有些真件是**方块**（例 `gtceu:normal_optical_pipe` 光缆，键在 `block.gtceu.*` 下）
        const kb = 'block.' + id.slice(0, ci) + '.' + id.slice(ci + 1);
        return MERGED_ZH.has(kb) ? MERGED_ZH.get(kb) : null;
    };
    // 换算配方给出「实际采用的真件」——只读磁盘（生成器自己写过的那批 + 前几轮的补丁结果）
    const CONV_DIR = path.join(RES, 'data', 'gtlmodular', 'recipes', 'nanochip', 'conversion');
    const adopted = new Map();
    if (fs.existsSync(CONV_DIR)) {
        for (const f of fs.readdirSync(CONV_DIR).filter((x) => x.endsWith('.json'))) {
            try {
                const j = JSON.parse(fs.readFileSync(path.join(CONV_DIR, f), 'utf8'));
                const o = ((j.outputs.item || [])[0] || {}).content.ingredient.item;
                const i = ((j.inputs.item || [])[0] || {}).content.ingredient.item;
                if (o && i) adopted.set(o, i);
            } catch { /* 忽略 */ }
        }
    }
    const VERB = /^(封装|切割的|制好的|提纯的|拼接的|锉制的|剥离的|协调的)\s?/;
    /** NH 期望名（材料词用**包内真名**修正）：前缀模板取 `tagprefix.<tag>`、材料名取 `material.gtceu.<材料>`。
     *  这一步专门修「海伯根 → 海珀珍」这类：NH 的形状/前缀没错，错的是旧手写译名表里的材料词。
     *  查不到（材料本包没有，例 lumiium 流明合金）⇒ 返回 null，调用处保留旧译名并打标记。 */
    const NH_PREFIX2TAG = {
        wireFine: 'fine_wire', wireGt01: 'wire_gt_single', wireGt02: 'wire_gt_double',
        bolt: 'bolt', screw: 'screw', foil: 'foil', plate: 'plate', frameGt: 'frame',
        stick: 'rod', gearGt: 'gear', ring: 'ring', spring: 'spring', itemCasing: 'item_casing',
    };
    const snakeName = (s) => String(s).replace(/([a-z0-9])([A-Z])/g, '$1_$2').replace(/([A-Z]+)([A-Z][a-z])/g, '$1_$2').toLowerCase();
    /**
     * 材料中文名落库（★ 2026-09-18 中文名复核队员纠正的**方法错误**）：
     * 只查 `material.gtceu.<NH 拼写>` 会漏——NH 的拼写常带下划线而包内不带，
     * 例 `mag_matter`（NH）vs `material.gtceu.magmatter=磁物质`、`celestial_tungsten` vs `celestialtungsten=天体钨`；
     * 而 `proto_halkonite` 在 **gtladditions** 命名空间下（`material.gtladditions.proto_halkonite=原初恒坚钢`）。
     * 所以：① 四个命名空间逐个精确查；② 再按**去下划线**归一化查一遍。
     */
    const MAT_NS = ['gtceu', 'gtlcore', 'gtladditions', 'GTLsupb'];
    const MAT_NORM = new Map();
    for (const [k, v] of MERGED_ZH) {
        const m = /^material\.([^.]+)\.(.+)$/.exec(k);
        if (m) {
            const nk = 'material.' + m[1] + '.' + m[2].replace(/_/g, '');
            if (!MAT_NORM.has(nk)) MAT_NORM.set(nk, v);
        }
    }
    const matZh = (id) => {
        if (!id) return null;
        for (const ns of MAT_NS) { const k = 'material.' + ns + '.' + id; if (MERGED_ZH.has(k)) return MERGED_ZH.get(k); }
        const flat = String(id).replace(/_/g, '');
        for (const ns of MAT_NS) { const k = 'material.' + ns + '.' + flat; if (MAT_NORM.has(k)) return MAT_NORM.get(k); }
        return null;
    };
    const nhExpect = (p, oldName) => {
        // ① NH 材料名：参数里 `Materials.<X>` → 注释里 `GGMaterial.<x>` → 枚举名去掉形状词
        let nhMat = null;
        let m = /Materials\.([A-Za-z0-9_]+)/.exec(String(p.realComponent || ''));
        if (m) nhMat = snakeName(m[1]);
        if (!nhMat) {
            m = /GGMaterial\.([a-z0-9_]+)/.exec(String(p.realComponent || ''));
            if (m) nhMat = m[1];
        }
        if (!nhMat) {
            // 例如 `WireHypogen` → hypogen、`FrameboxHypogen` → hypogen、`CasingCreon` → creon
            // ★ 2026-09-18 修：原先按数组顺序 `startsWith` 取第一个，`Framebox*` 会被 `Frame` 截成 `box*`
            //   （⇒ 材料词变成 box_hypogen / box_celestial_tungsten，查不到就退回旧错译名）。改成**最长前缀**优先。
            const shapeWords = ['Framebox', 'Frame', 'Wire', 'Foil', 'Bolt', 'Screw', 'Plate', 'Rod', 'Gear',
                'Ring', 'Spring', 'Cable', 'Chip', 'Casing', 'Board', 'Lens'];
            let e = String(p.enumName || '');
            const hit = shapeWords.filter((w) => e.startsWith(w)).sort((a, b) => b.length - a.length)[0];
            if (hit) e = e.slice(hit.length);
            nhMat = snakeName(e);
        }
        // ② 用**形状模板**把旧名切成 前缀/材料词/后缀（模板来自合并语言，逐个试；
        //    `item_casing` 这种包内没有 `tagprefix.*` 键的，用兜底后缀「外壳」）
        const CAND = ['fine_wire', 'wire_gt_single', 'wire_gt_double', 'foil', 'bolt', 'screw', 'plate', 'frame', 'rod', 'long_rod', 'gear', 'ring', 'spring', 'item_casing'];
        const SUF_FALLBACK = { item_casing: '外壳' };
        for (const tag of CAND) {
            const tpl = MERGED_ZH.get('tagprefix.' + tag) || (SUF_FALLBACK[tag] ? '%s' + SUF_FALLBACK[tag] : null);
            if (!tpl || !tpl.includes('%s')) continue;
            const [pre, suf] = tpl.split('%s');
            if (!oldName || !oldName.startsWith(pre) || !oldName.endsWith(suf)) continue;
            const oldMat = oldName.slice(pre.length, oldName.length - suf.length);
            const fixedMat = matZh(nhMat);
            return { name: fixedMat ? (pre + fixedMat + suf) : oldName, mat: nhMat, tag, fixed: !!fixedMat, oldMat, fixedMatName: fixedMat || null };
        }
        return { name: oldName, mat: nhMat, fixed: false, oldMat: null, fixedMatName: null };
    };
    /** NH 段 P1 判定（have / substitute / missing）—— 决定要不要补「（NH：…）」。 */
    const p1StatusByCc = new Map();
    {
        const mapFile = path.join(__dirname, 'p1_input_map.json');
        if (fs.existsSync(mapFile)) {
            try {
                const mp = JSON.parse(fs.readFileSync(mapFile, 'utf8').replace(/^\uFEFF/, ''));
                for (const c of mp.conversion || []) if (c.ccId) p1StatusByCc.set(c.ccId, c.status);
            } catch { /* 忽略 */ }
        }
    }
    let renamed = 0, kept = 0, unresolved = [], nhFixed = 0;
    for (const p of plan) {
        const realId = adopted.get('gtlmodular:' + p.itemId);
        if (!realId) continue;                                   // 已处理件没有换算，名字由 CC 派生，跳过
        const base = nameOfReal(realId);
        if (!base) { unresolved.push(p.itemId + '（真件 ' + realId + ' 查不到语言键）'); continue; }
        const nhName = String(p.zh || '').replace(VERB, '');     // NH 期望名（可能是手写旧译名）
        const st = p1StatusByCc.get('gtlmodular:' + p.itemId);
        const isSub = st === 'substitute' || st === 'missing';
        let want = '封装 ' + base;
        let nhShown = null;
        if (isSub) {
            const fx = nhExpect(p, nhName);
            nhShown = fx.name;
            if (fx.fixed && fx.name !== nhName) nhFixed++;
            // NH 期望名与真件名**相同**时不补括号（避免「封装 咒源玄晶板（NH：咒源玄晶板）」这种废话）
            if (nhShown !== base) want += '（NH：' + nhShown + '）';
            else nhShown = null;
            if (!fx.fixed && nhShown !== base) {
                // 只对**真的会显示在玩家界面上的尾巴**报这个标记。多数命中它的 NH 名本来就不是「形状+材料」结构
                // （例「生物处理器」「1xLuV 超导体导线」「光学兼容内存」），所以措辞按实际含义写：
                // 不是"本包没这个材料"，而是"尾巴不是材料形态、没法逐字修材料词"。
                const tag = '! NH 期望名（尾巴）不是「形状+材料」结构，未做材料词修正（原文 ' + nhName
                    + '；解析出的材料 id=' + (fx.mat || '?') + '）';
                if (!(p.flags || []).includes(tag)) p.flags = [...(p.flags || []), tag];
            }
        }
        if (p.zh === want) { kept++; }
        else { renamed++; p.zhBefore = p.zh; }
        p.zh = want;
        p.nhExpectedZh = nhShown;
        p.realItemIdForNaming = realId;
        if (isSub) {
            const tag = '! 显示名以**实际真件**为准（NH 原名：' + nhShown + '）';
            if (!(p.flags || []).includes(tag)) p.flags = [...(p.flags || []), tag];
        }
    }
    console.log(`显示名归一：改名 ${renamed} 条 / 原本就对 ${kept} 条 / 查不到语言键 ${unresolved.length} 条 / NH 期望名材料词修正 ${nhFixed} 条`);
    if (unresolved.length) console.log('  查不到语言键的行：' + unresolved.slice(0, 8).join('、'));

    // ---- ★ 加工件（PC）名字跟着 CC 一起改 ----
    // 换了真件的那些 CC，其 PC 名字里往往还留着旧材料词（例 `海伯根线轴`）⇒ 用**同一套品类动词**从
    // CC 的新基名重算 PC 名（未换名的 PC 保持 NH 自己的命名，不一律重写）。
    const VERB_ZH = {
        smd: '制好的', chip: '切割的', board: '提纯的', frame: '拼接的', bolt: '锉制的', screw: '锉制的',
        plate: '剥离的', part: '协调的',
    };
    let pcRenamed = 0;
    for (const p of plan) {
        if (!p.isProcessed) continue;
        const ccId = 'cc_' + String(p.itemId).replace(/^cc_processed_/, '');
        const ccRow = plan.find((x) => x.itemId === ccId);
        if (!ccRow || !ccRow.zhBefore) continue;              // CC 没换名 ⇒ PC 不动（保住 NH 的命名）
        const base = String(ccRow.zh).replace(/^封装\s?/, '').replace(/（NH：[^）]*）$/, '');
        const body = snake(String(p.enumName || '').replace(/^Processed/, ''));
        const cat = w2Cat(body);
        let want;
        if (cat === 'wire') want = base.replace(/^(?:细|1x|2x|4x|8x|16x)/, '').replace(/导线$/, '') + '线轴';
        else if (cat === 'foil') want = base.replace(/箔$/, '') + '包裹线轴';
        else if (VERB_ZH[cat]) want = VERB_ZH[cat] + base;
        else { want = null; }
        if (want && p.zh !== want) { p.zhBefore = p.zh; p.zh = want; pcRenamed++; }
    }
    console.log(`  加工件名同步：${pcRenamed} 条`);
}

// ---------------------------------------------------------------- ★★ 贴图补全（2026-09-18 玩家实测：不许再出现「只有包装、里面没东西」）
// 纪律：每一件封装件的 layer0 必须是**内容物真图**——真件 = 换算配方的输入（磁盘上的操作真相）。
// 解析顺序（按真件命名空间）：
//   ① kubejs:<body>     → kubejs:item/<body>            （实例 kubejs 资源根）
//   ② gtlmodular:<body> → gtlmodular:item/missing/<body> → gtlmodular:item/<body>（我方补图目录）
//   ③ gtceu:<body>      → gtceu:item/<body> → gtceu:block/<body> → 方块图递归按关键词唯一命中
//   ④ 加工件（PC）：NH 的 78 张 processed 图**逐条试过 0 命中**（波2 是本包自造族，NH 根本没有对应件）
//      ⇒ 用**同一真件**的图（内容物一致，CC/PC 靠物品名区分），不再用灰底板。
// 显式例外表：自动解析不了、但真图确实存在的少数几条，逐条写死并注明证据出处。
{
    const CONV_DIR2 = path.join(RES, 'data', 'gtlmodular', 'recipes', 'nanochip', 'conversion');
    const realOf = new Map();
    for (const f of fs.readdirSync(CONV_DIR2).filter((x) => x.endsWith('.json'))) {
        const j = JSON.parse(fs.readFileSync(path.join(CONV_DIR2, f), 'utf8'));
        const o = ((j.outputs.item || [])[0] || {}).content.ingredient.item;
        const i = ((j.inputs.item || [])[0] || {}).content.ingredient.item;
        if (o && i) realOf.set(o, i);
    }
    const ownTexRoot = path.join(RES, 'assets', 'gtlmodular', 'textures');
    const kjTexRoot = path.join(GTL_PACK, 'kubejs', 'assets', 'kubejs', 'textures');
    const noExt = (n) => n.replace(/\.png$/, '');
    /** 递归在 gtceu 方块图里按关键词唯一命中（例 normal_optical_pipe → block/pipe/pipe_optical_in）。 */
    const findBlockTex = (body) => {
        const toks = body.split('_').filter((t) => !['normal', 'block', 'item', 'gt', 'the'].includes(t));
        if (!toks.length) return null;
        const hits = [];
        const walk = (d) => {
            for (const e of fs.readdirSync(d, { withFileTypes: true })) {
                const fp = path.join(d, e.name);
                if (e.isDirectory()) walk(fp);
                else if (e.name.endsWith('.png') && toks.every((t) => noExt(e.name).includes(t))) hits.push(path.relative(path.join(GTCEU, 'textures', 'block'), fp).split(path.sep).join('/'));
            }
        };
        const root = path.join(GTCEU, 'textures', 'block');
        if (fs.existsSync(root)) walk(root);
        return hits.length === 1 ? hits[0] : null;
    };
    const TEX_EXCEPTION = {
        'gtceu:normal_optical_pipe': {
            kind: 'gtceuBlock', base: 'gtceu:block/pipe/pipe_optical_in',
            ev: 'GTCEu 1.20.1 `assets/gtceu/textures/block/pipe/pipe_optical_in.png`；normal_optical_pipe 是方块（lang 键 block.gtceu.normal_optical_pipe），其外观即管道贴图',
        },
        'gtladditions:thermal_superconductor': {
            kind: 'modItem', base: 'gtladditions:item/thermal_superconductor',
            ev: '实例 `mods/gtladditions-3.2.8Custom_SubSpace-fix1.jar` → `assets/gtladditions/textures/item/thermal_superconductor.png`（同 jar 的 models/item/thermal_superconductor.json 引用之）',
        },
    };
    const resolveReal = (src) => {
        if (!src) return null;
        const ns = src.split(':')[0], body = src.split(':')[1];
        if (ns === 'kubejs') {
            return fs.existsSync(path.join(kjTexRoot, 'item', body + '.png')) ? { kind: 'kubejsItem', base: `kubejs:item/${body}` } : null;
        }
        if (ns === 'gtlmodular') {
            if (fs.existsSync(path.join(ownTexRoot, 'item', 'missing', body + '.png'))) return { kind: 'ownItem', base: `gtlmodular:item/missing/${body}` };
            if (fs.existsSync(path.join(ownTexRoot, 'item', body + '.png'))) return { kind: 'ownItem', base: `gtlmodular:item/${body}` };
            return null;
        }
        if (ns === 'gtceu') {
            if (fs.existsSync(path.join(GTCEU, 'textures', 'item', body + '.png'))) return { kind: 'gtceuItem', base: `gtceu:item/${body}` };
            if (fs.existsSync(path.join(GTCEU, 'textures', 'block', body + '.png'))) return { kind: 'gtceuBlock', base: `gtceu:block/${body}` };
            const f = findBlockTex(body);
            return f ? { kind: 'gtceuBlock', base: `gtceu:block/${f}` } : null;
        }
        return null;
    };
    /** CC 的真件：换算配方输入；PC 没有换算配方 ⇒ 取它自己那件 CC 的真件。 */
    const realIdFor = (p) => realOf.get('gtlmodular:' + p.itemId)
        || realOf.get('gtlmodular:cc_' + String(p.itemId).replace(/^cc_processed_/, '')) || null;
    const apply = (p, r, extraNote) => {
        p.texture = r.kind === 'modItem'
            ? { kind: 'modItem', base: r.base, overlay: true, evidence: r.ev }
            : { kind: r.kind, base: r.base, overlay: true };
        p.textureSource = r.ev ? `${r.base} —— ${r.ev}` : `内容物真图 ${r.base}`;
        p.textureNote = (p.textureNote ? p.textureNote + '；' : '') + extraNote;
    };
    let fixedCc = 0, fixedPc = 0;
    const stillBlank = [];
    for (const p of plan) {                                     // ① 先补未处理件（PC 的图随后从它继承）
        if (p.texture.kind !== 'blank' || p.isProcessed) continue;
        const realId = realIdFor(p);
        const r = realId ? (TEX_EXCEPTION[realId] || resolveReal(realId)) : null;
        if (!r) { stillBlank.push(p.itemId); continue; }
        apply(p, r, `贴图补全：改用内容物真图（真件 ${realId}），原自造灰底板已废`);
        fixedCc++;
    }
    for (const p of plan) {                                     // ② 加工件
        if (p.texture.kind !== 'blank') continue;
        const ccRow = plan.find((x) => x.itemId === 'cc_' + String(p.itemId).replace(/^cc_processed_/, ''));
        if (!ccRow || ccRow.texture.kind === 'blank') { stillBlank.push(p.itemId); continue; }
        apply(p, { kind: ccRow.texture.kind, base: ccRow.texture.base, ev: ccRow.texture.evidence }, `贴图补全：与其封装件 ${ccRow.itemId} 同图（同一真件内容物；NH 无对应 processed 图），原自造灰底板已废`);
        fixedPc++;
    }
    console.log(`贴图补全：未处理件 ${fixedCc} 条 / 加工件 ${fixedPc} 条改用内容物真图；仍无图 ${stillBlank.length} 条`);
    if (stillBlank.length) {
        console.error('仍有空包装（封装的包装里没有内容物图）：\n  ' + stillBlank.join('\n  '));
        process.exit(1);
    }
}

// ---------------------------------------------------------------- 计划自检（不写盘也要过）
const problems = [];
const isWave2 = (p) => (p.flags || []).includes('wave2');
const nhPlan = plan.filter((p) => !isWave2(p));
// ★ 2026-09-18：176 → 169（未处理 99 → 95，已处理 77 → 74）—— 退役「特殊系列」7 条，见 RETIRED。
// ★ 2026-09-21：169 → 166（未处理 95 → 94，已处理 74 → 72）—— 再退役「时空导线」链 2 件。
// ★★ 同日晚 波2：追加本包自造段 EXTRA（默认 0 条；接上 `nac_wave2_spec.json` 后为 197 条）⇒ 计划总数 = PLAN_TOTAL。
if (plan.length !== PLAN_TOTAL) problems.push(`计划条数 ${plan.length} ≠ ${PLAN_TOTAL}（NH ${RECORDS.length} + 自造 ${EXTRA.length}）`);
if (nhPlan.filter(p => !p.isProcessed).length !== 94) problems.push(`NH 未处理条数 ≠ 94（${nhPlan.filter(p => !p.isProcessed).length}）`);
if (nhPlan.filter(p => p.isProcessed).length !== 72) problems.push(`NH 已处理条数 ≠ 72（${nhPlan.filter(p => p.isProcessed).length}）`);
// ★ 自造段纪律（本轮新增）：metaId 必须与 NH 段（0..2119）隔开，且不得与 NH 段撞号。
const nhMetas = new Set(nhPlan.map((p) => p.metaId));
for (const p of EXTRA) {
    if (p.metaId < 2200) problems.push(`自造段 metaId ${p.metaId} < 2200（应与 NH 段 0..2119 隔开）`);
    if (nhMetas.has(p.metaId)) problems.push(`自造段 metaId ${p.metaId} 与 NH 段撞号`);
}
const ids = new Set();
for (const p of plan) {
    if (ids.has(p.itemId)) problems.push(`重复 itemId ${p.itemId}`);
    ids.add(p.itemId);
    if (p.itemId !== 'cc_' + snake(p.enumName)) problems.push(`id 规则不符 ${p.itemId}`);
    if (p.texture.kind === 'blank') problems.push(`空包装 ${p.itemId}：封装件必须显示内容物真图（自造灰底板已废）`);
    if (!p.texture.base && p.texture.kind !== 'nhProcessed') problems.push(`贴图缺 base ${p.itemId}`);
    if (p.texture.kind === 'gtceuItem' || p.texture.kind === 'gtceuMaterial') {
        const rel = p.texture.base.replace('gtceu:', '').split('/').join(path.sep) + '.png';
        if (!fs.existsSync(path.join(GTCEU, 'textures', rel))) problems.push(`缺 GTCEu 贴图 ${p.texture.base}`);
    }
    if (p.texture.kind === 'gtceuBlock') {
        const rel = p.texture.base.replace('gtceu:', '').split('/').join(path.sep) + '.png';
        if (!fs.existsSync(path.join(GTCEU, 'textures', rel))) problems.push(`缺 GTCEu 方块贴图 ${p.texture.base}`);
    }
    if (p.texture.kind === 'ownItem') {
        const rel = p.texture.base.replace('gtlmodular:', '').split('/').join(path.sep) + '.png';
        if (!fs.existsSync(path.join(RES, 'assets', 'gtlmodular', 'textures', rel))) problems.push(`缺我方物品贴图 ${p.texture.base}`);
    }
    if (p.texture.kind === 'modItem' && !p.texture.evidence) problems.push(`modItem 缺证据出处 ${p.itemId}`);
    // ★ 校验放宽：NH 源贴图可能不在本机，但仓库成品图在就算合格
    if (p.texture.kind === 'nhProcessed'
        && !fs.existsSync(path.join(ROOT, p.texture.nhFile.split('/').join(path.sep)))
        && !fs.existsSync(path.join(ROOT, 'gtl-modular/src/main/resources', p.texture.png))) {
        problems.push(`缺 NH 贴图 ${p.texture.nhFile}`);
    }
    if (p.texture.kind === 'kubejsItem') {
        // kubejs 的资源根是 <实例>/kubejs/assets/...（不是 <实例>/assets/...）
        const body = p.texture.base.replace('kubejs:item/', '');
        const rel = 'kubejs/assets/kubejs/textures/item/' + body + '.png';
        if (!fs.existsSync(path.join(GTL_PACK, rel.split('/').join(path.sep)))) problems.push(`缺 kubejs 贴图 ${p.texture.base}（期望 ${rel}）`);
    }
    // ★ 自造段用**合并后的 en 键集**（gtceu + gtlcore + gtladditions + GTLsupb）；
    //   NH 段保持原样只查 gtceu jar（那是既有口径，不放松）。
    if (p.realRef && p.realRef.startsWith('item:') && !(('item.gtceu.' + p.realRef.slice(5)) in gtEn) && !(isWave2(p) && gtEnAll.has('item.gtceu.' + p.realRef.slice(5)))) problems.push(`GTCEu lang 缺 item.gtceu.${p.realRef.slice(5)}`);
    if (p.realRef && p.realRef.startsWith('mat:')) {
        const [m, tag] = p.realRef.slice(4).split(':');
        const okMat = (('material.gtceu.' + m) in gtEn) || (isWave2(p) && gtEnAll.has('material.gtceu.' + m));
        const okTag = (('tagprefix.' + tag) in gtEn) || (isWave2(p) && gtEnAll.has('tagprefix.' + tag));
        if (!okMat) problems.push(`GTCEu lang 缺 material.gtceu.${m}`);
        if (!okTag) problems.push(`GTCEu lang 缺 tagprefix.${tag}`);
    }
    if (!p.en || !p.zh) problems.push(`缺名字 ${p.itemId}`);
}
if (problems.length) { console.error('计划自检失败:\n' + problems.join('\n')); process.exit(1); }

console.log(`计划 ${plan.length} 条：未处理 ${plan.filter(p => !p.isProcessed).length} / 已处理 ${plan.filter(p => p.isProcessed).length}`);
console.log('贴图来源分布:', plan.reduce((m, p) => (m[p.texture.kind] = (m[p.texture.kind] || 0) + 1, m), {}));
console.log('英文名来源分布:', plan.reduce((m, p) => (m[p.enSource] = (m[p.enSource] || 0) + 1, m), {}));
console.log('中文名来源分布:', plan.reduce((m, p) => (m[p.zhSource] = (m[p.zhSource] || 0) + 1, m), {}));
console.log('realRef 条数:', plan.filter(p => p.realRef).length, '| 带降级标记的:', plan.filter(p => p.flags.some(t => t.startsWith('!') || t.startsWith('真实物品名由'))).length);
console.log('OK: 贴图与 GTCEu lang 引用全部存在');

writeOut(path.join(__dirname, '_nac_cc_plan.json'), JSON.stringify(plan, null, 2));
console.log('已写 _census/_nac_cc_plan.json');
if (DRY) process.exit(0);

// ================================================================ 1) Java 注册类
const J = [];
const esc = s => String(s).replace(/\\/g, '\\\\').replace(/"/g, '\\"');
J.push(`package com.gtlmodular.common.data.nanochip;

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
                tooltip.add(Component.literal("\\u00a78" + entry.itemId + " / meta " + entry.metaId
                        + (entry.calibration == null ? "" : " / " + entry.calibration)));
            }
        }
    }

    // ---------------------------------------------------------------- 表
    /**
     * 按 metaId 升序的 ${PLAN_TOTAL} 条（NH 原始 176 − 退役 ${RETIRED.length} = ${RECORDS.length}；+ 本包自造段 ${EXTRA.length}，metaId ≥ 2200）。
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
    private static final List<Entry> ALL = new ArrayList<>(${PLAN_TOTAL});
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
        if (ALL.size() != ${PLAN_TOTAL}) {
            throw new IllegalStateException("NAC CC 物品层表异常：应为 ${PLAN_TOTAL} 条（NH ${RECORDS.length} + 自造 ${EXTRA.length}），实为 " + ALL.size());
        }
    }

    /** 全部 ${PLAN_TOTAL} 条（metaId 升序，只读）。 */
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

    /** metaId → 枚举名（${PLAN_TOTAL} 条全覆盖，未处理/已处理都能查到；注销件 ${RETIRED.filter(r => r.javaNote).map(r => r.metaId).sort((a, b) => a - b).join(' / ')} 不在表内 ⇒ 返回 null）。 */
    public static Map<Integer, String> metaToName() {
        Map<Integer, String> m = new LinkedHashMap<>();
        for (Entry e : ALL) m.put(e.metaId, e.enumName);
        return Collections.unmodifiableMap(m);
    }

    /** 枚举名 → 表项；未登记返回 null。 */
    public static Entry byEnumName(String enumName) { return BY_ENUM.get(enumName); }

    /** metaId → 表项；未登记返回 null。 */
    public static Entry byMetaId(int metaId) { return BY_META.get(metaId); }

    // ---------------------------------------------------------------- ${PLAN_TOTAL} 行数据（生成，勿手改）
    private static void addRows() {
`);
// ★ 2026-09-21：注销件的**墓碑注释**也要由生成器复现，否则「生成器输出 ≡ 落地态」不成立
//   （重跑会把墓碑抹掉，看起来像"注销没发生过"）。规则：按 metaId 升序，把每段墓碑插在
//   「第一条 metaId 大于它的存活件」之前 —— 正好是它原来在 NH 枚举里的位置。
const JAVA_NOTES = RETIRED.filter((r) => Array.isArray(r.javaNote) && r.javaNote.length)
    .sort((a, b) => a.metaId - b.metaId);
let noteIdx = 0;
const flushNotesBefore = (metaId) => {
    while (noteIdx < JAVA_NOTES.length && JAVA_NOTES[noteIdx].metaId < metaId) {
        for (const line of JAVA_NOTES[noteIdx].javaNote) J.push(line);
        noteIdx++;
    }
};
for (const p of plan) {
    flushNotesBefore(p.metaId);
    J.push(`        add("${esc(p.enumName)}", ${p.metaId}, ${p.isProcessed}, "${esc(p.nameKey)}", `
        + `${p.realRef ? '"' + esc(p.realRef) + '"' : 'null'}, ${p.calibration ? '"' + esc(p.calibration) + '"' : 'null'});`
        + `   // ${esc(p.en)}` + (p.flags.length ? `  [${esc(p.flags.join('; '))}]` : ''));
}
// 收尾：metaId 比最后一条存活件还大的墓碑（本例没有，留作保险）
while (noteIdx < JAVA_NOTES.length) { for (const line of JAVA_NOTES[noteIdx].javaNote) J.push(line); noteIdx++; }
J.push(`    }
}
`);
const JAVA_OUT = W(path.join(JAVA_DIR, 'NanochipCircuitComponents.java'));
fs.mkdirSync(path.dirname(JAVA_OUT), { recursive: true });
fs.writeFileSync(JAVA_OUT, J.join('\n'), 'utf8');
console.log('已写 ' + path.relative(MOD, JAVA_OUT));

// ================================================================ 2) 模型 JSON
const MODEL_DIR = W(path.join(RES, 'assets', 'gtlmodular', 'models', 'item'));
fs.mkdirSync(MODEL_DIR, { recursive: true });
// 先清掉本波上一版（旧 id 规则）留下的 cc_*.json 模型，避免新旧 id 并存成孤儿文件
let staleModel = 0;
for (const f of fs.readdirSync(MODEL_DIR)) {
    if (/^cc_.*\.json$/.test(f)) { fs.unlinkSync(path.join(MODEL_DIR, f)); staleModel++; }
}
const OVERLAY = 'gtlmodular:item/cc/circuit_component_overlay';
/**
 * ★★ 2026-09-21 口径变更（队长授权，与 `_census/check_nac_cc_items.js` 的 `FAMILY_TEXTURE` **同源同表**）★★
 * 三族（奇异/寰宇/超因果）这 25 件走**单层族专属贴图** `layer0 = gtlmodular:item/cc/<id>`，不再叠 overlay、
 * 也不再引用 kubejs 内容物真图 —— 依据：队长「给奇异寰宇超因果也补上对应的贴图」= 必须显示出来；
 * 代价：这 25 件不再显示 kubejs 真图 + overlay（族贴图本身就是按本族主机色重着色过的成品图）。
 * **只这 25 件**，其余 wave2 件仍走两层。
 * ⚠️ 本表必须与检查脚本的 `FAMILY_TEXTURE` 保持一致，否则「重跑生成器 = 零 diff」与规则 ⑦ 会互相打架。
 */
const FAMILY_SINGLE_LAYER = new Set([
    'cc_processed_exotic_printed_circuit_board', 'cc_processed_exotic_processing_core', 'cc_processed_exotic_ram_chip',
    'cc_processed_smd_resistor_exotic', 'cc_processed_smd_transistor_exotic', 'cc_processed_smd_inductor_exotic',
    'cc_processed_smd_capacitor_exotic', 'cc_processed_smd_diode_exotic',
    'cc_processed_cosmic_printed_circuit_board', 'cc_processed_cosmic_processing_core', 'cc_processed_cosmic_processing_unit_core',
    'cc_processed_cosmic_ram_chip', 'cc_processed_smd_resistor_cosmic', 'cc_processed_smd_transistor_cosmic',
    'cc_processed_smd_inductor_cosmic', 'cc_processed_smd_capacitor_cosmic', 'cc_processed_smd_diode_cosmic',
    'cc_processed_supracausal_printed_circuit_board', 'cc_processed_supracausal_processing_core', 'cc_processed_supracausal_ram_chip',
    'cc_processed_smd_resistor_supracausal', 'cc_processed_smd_transistor_supracausal', 'cc_processed_smd_inductor_supracausal',
    'cc_processed_smd_capacitor_supracausal', 'cc_processed_smd_diode_supracausal',
]);
if (FAMILY_SINGLE_LAYER.size !== 25) { console.error('FAMILY_SINGLE_LAYER 应 25 件，实为 ' + FAMILY_SINGLE_LAYER.size); process.exit(1); }
let modelN = 0;
for (const p of plan) {
    let tex;
    if (FAMILY_SINGLE_LAYER.has(p.itemId)) tex = { layer0: `gtlmodular:item/cc/${p.itemId}` };
    else if (p.texture.kind === 'nhProcessed') tex = { layer0: `gtlmodular:item/cc/${p.itemId}` };
    else if (p.texture.base) tex = { layer0: p.texture.base, layer1: OVERLAY };
    else { console.error(`模型写盘被拦：${p.itemId} 没有内容物贴图（空包装）`); process.exit(1); }
    const json = { parent: 'item/generated', textures: tex };
    fs.writeFileSync(path.join(MODEL_DIR, p.itemId + '.json'), JSON.stringify(json, null, 2) + '\n', 'utf8');
    modelN++;
}
console.log(`已清掉旧的 cc_*.json 模型 ${staleModel} 个，写入 ${modelN} 个 item 模型 → assets/gtlmodular/models/item/cc_*.json`);

// ================================================================ 3) 贴图
const TEX_DIR = W(path.join(RES, 'assets', 'gtlmodular', 'textures', 'item', 'cc'));
fs.mkdirSync(TEX_DIR, { recursive: true });
// 同上：清掉旧 id 命名的贴图（保留 overlay 与自造底板）
let staleTex = 0;
for (const f of fs.readdirSync(TEX_DIR)) {
    if (/^cc_.*\.png(\.mcmeta)?$/.test(f)) { fs.unlinkSync(path.join(TEX_DIR, f)); staleTex++; }
}

// 3a) NH overlay（逐字节）
const overlaySrc = path.join(NH_TEX, 'circuit_component_overlay.png');
const overlayBytes = fs.readFileSync(overlaySrc);
fs.writeFileSync(path.join(TEX_DIR, 'circuit_component_overlay.png'), overlayBytes);
console.log(`已拷 NH overlay ← ${path.relative(ROOT, overlaySrc)} (${overlayBytes.length} B)`);

// 3b) processed：NH 贴图逐字节拷贝（含 .mcmeta 动画元数据）
let copied = 0, mcn = 0;
for (const p of plan) {
    if (p.texture.kind !== 'nhProcessed') continue;
    const src = path.join(ROOT, p.texture.nhFile.split('/').join(path.sep));
    fs.copyFileSync(src, path.join(TEX_DIR, p.itemId + '.png'));
    copied++;
    const mc = src + '.mcmeta';
    if (fs.existsSync(mc)) { fs.copyFileSync(mc, path.join(TEX_DIR, p.itemId + '.png.mcmeta')); mcn++; }
}
console.log(`已逐字节拷 NH processed 贴图 ${copied} 张（另 ${mcn} 个 .mcmeta；清掉旧 id 贴图 ${staleTex} 个）`);

// 3c) 自造灰底板已**废弃**（2026-09-18 玩家实测：装进封装包装里空无一物）。
//     任何一件封装件都必须显示内容物真图 ⇒ 这里只负责**删掉**上一版遗留的 packaged_blank.png。
{
    const old = path.join(TEX_DIR, 'packaged_blank.png');
    if (fs.existsSync(old)) { fs.unlinkSync(old); console.log('已删除废弃的自造灰底板 packaged_blank.png（禁止空包装）'); }
    const oldMc = old + '.mcmeta';
    if (fs.existsSync(oldMc)) fs.unlinkSync(oldMc);
}

// ================================================================ 4) lang（只新增键）
const TOOLTIP = {
    en_us: {
        // 只借 NH 这一个键名，值逐字相同（NH en_US.lang: gt.circuitcomponent.base=Packaged %s）
        'gt.circuitcomponent.base': 'Packaged %s',
        'gtlmodular.cc.tooltip.base': 'Item in the \u00a7eNAC\u00a77 Vacuum Conveyor System',
        'gtlmodular.cc.tooltip.cc.base': 'This item \u00a7a\u00a7lcan\u00a7r\u00a77 be converted to/from its real stack equivalent in the \u00a7eNAC',
        'gtlmodular.cc.tooltip.pc.base': 'This item \u00a7c\u00a7lcan not\u00a7r\u00a77 be converted to its un-processed real stack equivalent in the \u00a7eNAC',
        'gtlmodular.cc.tooltip.cc.howto': '\u00a77In the \u00a7econtrol room\u00a77: dye a \u00a7bVacuum Conveyor Hatch\u00a77 first. Output-side hatch packs a real item into this CC (\u00a7fonce per 5 s\u00a77); put this CC back into an input-side hatch to get the real item out (it lands in that hatch\u0027s own slots)',
        'gtlmodular.cc.tooltip.pc.howto': '\u00a77A processed item only goes forward - it cannot be turned back into a real item',
    },
    zh_cn: {
        'gt.circuitcomponent.base': '\u5c01\u88c5 %s',
        'gtlmodular.cc.tooltip.base': '\u00a77\u7528\u4e8e \u00a7eNAC\u00a77 \u771f\u7a7a\u4f20\u9001\u7cfb\u7edf\u7684\u7269\u54c1',
        'gtlmodular.cc.tooltip.cc.base': '\u00a77\u8be5\u7269\u54c1\u00a7a\u00a7l\u53ef\u4ee5\u00a7r\u00a77\u5728 \u00a7eNAC\u00a77 \u4e2d\u4e0e\u5b83\u7684\u771f\u5b9e\u7269\u54c1\u5f62\u6001\u4e92\u76f8\u8f6c\u6362',
        'gtlmodular.cc.tooltip.pc.base': '\u00a77\u8be5\u7269\u54c1\u00a7c\u00a7l\u65e0\u6cd5\u00a7r\u00a77\u5728 \u00a7eNAC\u00a77 \u4e2d\u8f6c\u6362\u56de\u5b83\u7684\u672a\u5904\u7406\u771f\u5b9e\u7269\u54c1\u5f62\u6001',
        // 「怎么用」那一行：把前置条件写清楚（仓必须染色 / 输出侧封装每 5 秒一次 / 还原用输入侧的仓）
        'gtlmodular.cc.tooltip.cc.howto': '\u00a77在\u00a7e\u63a7\u5236\u5ba4\u00a77\u7528\u00a7b\u771f\u7a7a\u4f20\u9001\u5e26\u4ed3\u00a77\uff1a\u5148\u7ed9\u4ed3\u00a7d\u67d3\u8272\u00a77\u3002'
            + '\u8f93\u51fa\u4fa7\u7684\u4ed3\u628a\u771f\u7269\u54c1\u5305\u6210\u672c\u4ef6\uff08\u00a7f\u6bcf 5 \u79d2\u4e00\u6b21\u00a77\uff09\uff1b'
            + '\u628a\u672c\u4ef6\u585e\u8fdb\u8f93\u5165\u4fa7\u7684\u4ed3\u5373\u53ef\u6362\u56de\u771f\u7269\u54c1\uff08\u771f\u7269\u54c1\u843d\u5728\u90a3\u4e2a\u4ed3\u81ea\u5df1\u7684\u683c\u5b50\u91cc\uff09',
        'gtlmodular.cc.tooltip.pc.howto': '\u00a77\u5df2\u5904\u7406\u4ef6\u53ea\u80fd\u5411\u524d\u52a0\u5de5\uff0c\u4e0d\u80fd\u8fd8\u539f\u6210\u771f\u7269\u54c1',
    },
};
for (const loc of ['en_us', 'zh_cn']) {
    const f = path.join(RES, 'assets', 'gtlmodular', 'lang', loc + '.json');
    const obj = JSON.parse(fs.readFileSync(f, 'utf8'));
    // 清掉本波上一版（旧 id 规则）留下的物品名键 —— 只匹配本波自己的命名空间，绝不碰项目既有键
    const valid = new Set(plan.map(p => 'gtlmodular.cc.' + p.itemId + '.name'));
    let pruned = 0;
    for (const k of Object.keys(obj)) {
        if (/^gtlmodular\.cc\.cc_.*\.name$/.test(k) && !valid.has(k)) { delete obj[k]; pruned++; }
    }
    let n = 0;
    for (const p of plan) {
        const k = 'gtlmodular.cc.' + p.itemId + '.name';
        const v = loc === 'en_us' ? p.en : p.zh;
        if (obj[k] !== v) { obj[k] = v; n++; }
    }
    for (const [k, v] of Object.entries(TOOLTIP[loc])) if (obj[k] !== v) { obj[k] = v; n++; }
    // 保持「原有键顺序在前、新增键在后」
    writeOut(f, JSON.stringify(obj, null, 2) + '\n');
    console.log(`已更新 lang ${loc}.json（新增/更新 ${n} 键，清掉旧 id 键 ${pruned} 个，其它键未删未改）` + (OUT_ROOT ? ` → 写到影子 ${path.relative(ROOT, W(f))}` : ''));
}

// ================================================================ 5) 对照表（census）
const census = {
    title: 'NAC CircuitComponent → gtlmodular 真实物品对照表',
    authoritativeSource: '_gtnh/dump3/CircuitComponent.txt（javap -p -c 全量 4164 行）',
    parsedBy: '_census/_parse_nac_cc.js → _census/_nac_cc_raw.json',
    generatedBy: '_census/gen_nac_cc_items.js',
    counts: { total: plan.length, unprocessed: plan.filter(p => !p.isProcessed).length, processed: plan.filter(p => p.isProcessed).length },
    // ★ 2026-09-18：退役名单进对照表 —— 自检（check_nac_cc_items.js）靠它做「dump 全集 = 表 ∪ 退役」的对账，
    //   而不是把 dump 的 176 硬改成 166（那样就丢了"NH 原本有这 10 条"的证据）。
    // ★ 2026-09-21：`wikiSource` 不再写死 —— 维基型（7 条）给 `wikiSource`，队长拍板型（3 条）给
    //   `how`/`probe`。键的**顺序与集合逐条固定**，因为 `nac_cc_items.json` 要与落地态逐字节相同。
    retired: RETIRED.map(r => {
        const e = {
            enumName: r.enumName, metaId: r.metaId, isProcessed: r.processed,
            itemId: itemIdOf(r.enumName), why: r.why,
        };
        if (r.wikiSource) e.wikiSource = r.wikiSource;                       // 维基型：依据 = 离线维基抓取语料
        else { if (r.how) e.how = r.how; if (r.probe) e.probe = r.probe; }   // 队长拍板型：退役落点 + 可复跑取证
        return e;
    }),
    retiredCount: RETIRED.length,
    retiredPolicy: 'NAC「特殊系列」三档自造电路整组退役（玩家裁决 2026-09-18）。维基逐字：「作为异星、寰宇、'
        + '超时空三种电路板还未更新时的暂时性的 UMV-MAX 级电路替代品，后续可能会移除。」本包同档真件：'
        + '#gtceu:circuits/uxv|opv|max 与 kubejs:suprachronal_uxv|opv|max。'
        + '★ 退役只在生成器的 RETIRED 名单里发生；_nac_cc_raw.json（NH javap 取证件）一个字不改。'
        + '★ 2026-09-21 追加：队长拍板退役「时空导线」链 2 件（`cc_wire_spacetime` / `cc_processed_wire_spacetime`，'
        + '材料 `spacetime` 属 OpV、超 NAC 最高档 UXV，且产物孤儿无配方消费）⇒ 退役 8 → 10，NH 段 168 → 166（95/73 → 94/72）。',
    idRule: 'itemId = "cc_" + snake_case(enumName)；snake_case = 每个大写字母前插下划线并整体小写（连续大写逐字母切：SMD→s_m_d、CPU→c_p_u、PIC→p_i_c）',
    textureRule: [
        'processed ⇒ 逐字节拷贝 NH assets/gregtech/textures/items/gt.circuitcomponent/processed/<枚举名小写>.png',
        'unprocessed 且真实物品是 GTCEu 1.20.1 物品 ⇒ layer0 = gtceu:item/<id>，layer1 = NH circuit_component_overlay.png',
        'unprocessed 且真实物品是材质件 ⇒ layer0 = gtceu:item/material_sets/dull/<前缀>（frameGt 用 gtceu:block/material_sets/dull/frame_gt），layer1 = NH overlay',
        'unprocessed 且拿不到真实物品贴图 ⇒ layer0 = 自造 packaged_blank.png（16×16 灰度圆角板），layer1 = NH overlay',
    ],
    nameRule: [
        'processed ⇒ 本包 lang 键 gtlmodular.cc.<id>.name 的值 = NH en_US.lang 的 nameKey 原文（zh 为自译）',
        'unprocessed ⇒ Java 运行时返回 Component.translatable("gt.circuitcomponent.base", <真实物品名>)（严格复刻 NH CircuitComponentFakeItem）',
        'unprocessed 但真实物品拿不到 ⇒ 退化为本包 lang 键的值（"Packaged <自撰真实物品名>"），census flags 里带 "!"',
    ],
    records: plan,
};
writeOut(path.join(__dirname, 'nac_cc_items.json'), JSON.stringify(census, null, 2) + '\n');

const md = [];
md.push('# NAC CircuitComponent → `gtlmodular` 真实物品对照表（169 条，权威；NH 原始 176 条 − 退役 7 条）');
md.push('');
md.push('> 唯一权威：`_gtnh/dump3/CircuitComponent.txt`（javap `-p -c` 全量）。本表由 `_census/_parse_nac_cc.js`');
md.push('> 解析该 dump、再由 `_census/gen_nac_cc_items.js` 生成；`_census/check_nac_cc_items.js` 逐条只读复核。');
md.push('');
md.push(`- 总 **${plan.length}** 条 = 未处理 **${plan.filter(p => !p.isProcessed).length}** + 已处理 **${plan.filter(p => p.isProcessed).length}**（= NH 枚举全集 176 条 − **退役 7 条**，退役清单见本节末尾）`);
md.push('- 物品 id 规则（2026-09-17 队长冻结规格修正）：`cc_` + **缩写感知** snake_case(枚举名) —— 在相邻两字符间插 `_`');
md.push('  当且仅当 (a) 前一小写字母/数字而当前大写，或 (b) 前一大写、当前大写且再下一个小写；整体小写。');
md.push('  锚点：`WireNiobiumTitanium`→`cc_wire_niobium_titanium`、`AdvSMDResistor`→`cc_adv_smd_resistor`、');
md.push('  `ChipNanoCPU`→`cc_chip_nano_cpu`、`SuperconductorLuV`→`cc_superconductor_lu_v`、`PikoPIC`→`cc_piko_pic`、');
md.push('  `ChipCrystalSoC`→`cc_chip_crystal_so_c`（全部由生成器与自检逐条断言）');
md.push('- 贴图来源分布：' + JSON.stringify(plan.reduce((m, p) => (m[p.texture.kind] = (m[p.texture.kind] || 0) + 1, m), {})));
const groupBy = list => list.reduce((m, p) => {
    const k = p.replace(/（[^）]*）/g, '').trim();
    m[k] = (m[k] || 0) + 1;
    return m;
}, {});
md.push('- 英文名来源分布（按类别归并）：');
for (const [k, v] of Object.entries(groupBy(plan.map(p => p.enSource)))) md.push(`  - ${v} × ${k}`);
md.push('- 中文名来源分布（按类别归并）：');
for (const [k, v] of Object.entries(groupBy(plan.map(p => p.zhSource)))) md.push(`  - ${v} × ${k}`);
md.push('');
md.push('## 退役清单（7 条 —— NAC「特殊系列」三档自造电路，2026-09-18 玩家裁决整组退役）');
md.push('');
md.push('维基逐字依据（`_census/wiki_抓取/raw/特殊系列电路板.wiki.txt`，只读语料）：');
md.push('');
md.push('- `|电路板1-等级=UMV |电路板2-等级=UXV |电路板3-等级=MAX`；`|电路板3-名字=普朗克尺度电路`');
md.push('- 总述：「作为**异星、寰宇、超时空三种电路板还未更新时的暂时性的 UMV-MAX 级电路替代品，后续可能会移除。**」');
md.push('- 历史：2.9.0 新增 ⇒ 这是 NH 给自己补档位空洞的**过渡件**，不是原生设计。');
md.push('');
md.push('本包同档真件（`#gtceu:circuits/uxv`（kubejs 引用 25 处）/ `opv`（17 处）/ `max`（18 处）；'
    + '`kubejs:suprachronal_uxv|opv|max` = 荒谬/过载/终焉超时空电路，证据 `_census/kubejs_items.json`）：');
md.push('');
md.push('| metaId | 枚举名 | itemId（已删） | 理由 |');
md.push('|---|---|---|---|');
for (const r of RETIRED) md.push(`| ${r.metaId} | \`${r.enumName}\` | \`gtlmodular:${itemIdOf(r.enumName)}\` | ${r.why} |`);
md.push('');
md.push('> ⚠️ **不**退役：PIC 芯片阶梯（`ChipPikoPIC`/`ChipQuantumPIC`/`ChipAttoPIC`/`ChipZeptoPIC`/`ChipYoctoPIC`/`ChipPlanckPIC`');
md.push('> 与它们的 `ProcessedChip*`）—— 那是切割室的芯片阶梯，玩家明确保留；其它电路族（晶体/湿件/生物/光学/纳米/集成/逻辑/量子系列/寰宇）一字不动。');
md.push('> ★ 退役只在生成器的 `RETIRED` 名单里发生；`_nac_cc_raw.json`（NH javap 取证件）一个字不改。');
md.push('');
md.push('## 169 行');
md.push('');
md.push('| # | metaId | 类 | 枚举名 | nameKey | iconString | itemId | 贴图来源 | 中文名来源 | realComponent（NH 表达式 / 字节码位置） | calibration | 证据（dump 偏移） | 标记 |');
md.push('|---|---|---|---|---|---|---|---|---|---|---|---|---|');
plan.forEach((p, i) => {
    md.push(`| ${i + 1} | ${p.metaId} | ${p.isProcessed ? '已处理' : '未处理'} | \`${p.enumName}\` | \`${p.nameKey}\` | \`${p.iconString}\` | \`gtlmodular:${p.itemId}\` | ${p.textureSource} | ${p.zhSource} | \`${String(p.realComponent).replace(/\|/g, '\\|')}\`<br>${p.realComponentLine || '—'} | ${p.calibration || '—'} | \`${p.evidence.dump}\`；声明 \`${p.evidence.decl}\`${p.evidence.lambda ? '；λ `' + p.evidence.lambda + '`' : ''} | ${p.flags.length ? p.flags.join('<br>') : '—'} |`);
});
md.push('');
md.push('## 显示名');
md.push('');
md.push('| metaId | itemId | 英文显示名 | 中文显示名 |');
md.push('|---|---|---|---|');
for (const p of plan) md.push(`| ${p.metaId} | \`${p.itemId}\` | ${p.en.replace(/\|/g, '\\|')} | ${p.zh.replace(/\|/g, '\\|')} |`);

md.push('');
md.push('## 自造 / 合成贴图清单（逐类列明「哪一类 · 为什么 · 源是哪张 NH 图」）');
md.push('');
md.push('| 类别 | 件数 | 底板（layer0） | 为什么 | 源 |');
md.push('|---|---|---|---|---|');
md.push(`| 拿不到真实物品贴图（itemCasing 无 GTCEu 母版 / NH-only 电路与芯片 / GT++ 取件） | ${plan.filter(p => !p.isProcessed && p.texture.kind === 'blank').length} | \`gtlmodular:item/cc/packaged_blank\`（**自造**：16×16 灰度圆角板，生成器用 zlib 现写 PNG） | GTCEu 1.20.1 没有对应真实物品的贴图，按规格「退化用 overlay 合成一张自造底图」 | 叠加层仍是 NH 原图 \`gt.circuitcomponent/circuit_component_overlay.png\`（逐字节，1675 B） |`);
md.push(`| 已处理件 | ${plan.filter(p => p.isProcessed).length} | NH \`processed/<枚举名小写>.png\` | NH 原图就是完整成品图 | 逐字节拷贝（自检按 sha256 比对） |`);
md.push('');
md.push('> 除上面两类外，其余未处理件的 layer0 直接引用 **GTCEu 1.20.1 自带贴图**（`gtceu:item/...`、`gtceu:block/material_sets/...`），');
md.push('> layer1 一律是 NH 的 `circuit_component_overlay.png`。**没有任何一件是紫黑方块**（自检 ③ 逐条核对文件存在）。');
md.push('');
md.push('## 未处理件的材质母版「为什么是 DULL 灰底」');
md.push('');
md.push('GTCEu 1.20.1 的 `material_sets/<set>/<前缀>.png` 是**灰度母版**，运行时由物品自带的');
md.push('`ItemColor` 处理器按材质颜色染色。CC 物品不是 GTCEu 的材质物品 ⇒ 没有染色处理器 ⇒ 呈灰底。');
md.push('这与本包既有「板金属方块未染色」是同一类已知偏差（见 `NanochipBlocks` 的 javadoc）。');
md.push('已处理件（74 件）用的是 NH 彩色成品图，不受影响。');
md.push('');
md.push('## 自译中文名清单（凡不在 NH 资源里的中文名，全部在此；**不是 NH 原文**）');
md.push('');
const selfZh = plan.filter(p => p.zhSource === '自译');
md.push(`共 ${selfZh.length} 条 = 已处理件 74 条（NH 只有英文）+ 未处理件中「自撰真实物品中文名」的 ${selfZh.length - 74} 条。`);
md.push('');
md.push('| metaId | itemId | 英文（NH 口径） | 自译中文 |');
md.push('|---|---|---|---|');
for (const p of selfZh) md.push(`| ${p.metaId} | \`${p.itemId}\` | ${p.en.replace(/\|/g, '\\|')} | ${p.zh.replace(/\|/g, '\\|')} |`);
md.push('');
md.push(`另有 ${plan.filter(p => p.zhSource.startsWith('GTCEu')).length} 条未处理件的中文名**不是我译的**：`);
md.push('由 Java 运行时用 GTCEu 自己的 lang 键组合（`tagprefix.*` + `material.gtceu.*` / `item.gtceu.*`），');
md.push('即 GTCEu `zh_cn.json` 的原文。');
md.push('');
md.push('## 降级 / 需仲裁项（`!` 标记）');
md.push('');
const flagged = plan.filter(p => p.flags.some(t => t.startsWith('!')));
md.push(`共 ${flagged.length} 条带 \`!\`（规格允许「不判失败」，但必须逐条可见）：`);
md.push('');
md.push('| metaId | itemId | 标记 |');
md.push('|---|---|---|');
for (const p of flagged) md.push(`| ${p.metaId} | \`${p.itemId}\` | ${p.flags.filter(t => t.startsWith('!')).join('<br>')} |`);
md.push('');
writeOut(path.join(__dirname, 'nac_cc_items.md'), md.join('\n'));
console.log('已写 _census/nac_cc_items.json / nac_cc_items.md');
console.log('（自检脚本 _census/check_nac_cc_items.js 是独立交付物，不由本生成器覆写）');

// ================================================================ 6) 新旧 id 规则差异报告
{
    const diff = plan.map(p => ({ enumName: p.enumName, oldId: 'cc_' + snakeLegacy(p.enumName), newId: p.itemId }))
        .filter(d => d.oldId !== d.newId);
    writeOut(path.join(__dirname, '_id_rule_change.json'), JSON.stringify({
        rule: '缩进感知 snake_case（2026-09-17 队长冻结规格修正）',
        oldRule: '每个大写字母前插下划线（连续大写逐字母切）',
        newRule: '插 _ 当且仅当 (a) 前一小写字母/数字而当前大写，或 (b) 前一大写、当前大写且再下一个是小写',
        total: plan.length, changed: diff.length, unchanged: plan.length - diff.length, diff,
    }, null, 2));
    console.log(`新旧 id 差异：${diff.length} 条改变 / ${plan.length - diff.length} 条不变（清单 _census/_id_rule_change.json）`);
    for (const d of diff.slice(0, 12)) console.log(`   ${d.oldId}  →  ${d.newId}`);
    if (diff.length > 12) console.log(`   …（共 ${diff.length} 条）`);
}

