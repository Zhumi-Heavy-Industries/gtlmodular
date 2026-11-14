#!/usr/bin/env node
/*
 * gen_nac_p1_recipes.js —— NAC（GTNH）→ gtl-modular 的 **P1 配方层**生成器（幂等）。
 *
 * 产出（逐字节可复现：固定键序 + 固定顺序 + LF）：
 *   data/gtlmodular/recipes/nanochip/conversion/*.json        恰好 95 个（真实物品×1 → CC×1，duration=100，EU/t=0）
 *   data/gtlmodular/recipes/nanochip/assembly_matrix/*.json   恰好 19 个（CC 输入 + tier 6..12 + EU/t + 流体 + tick）
 *   _census/p1_recipe_gen_report.json                         机器可读的生成报告（含顶替清单）
 *
 * ★ 2026-09-18：122 → 114（换算 99 → 95，组装矩阵 23 → 19）—— NAC「特殊系列」三档自造电路整组退役，
 *   8 条配方（4 换算 + 4 矩阵 R20/R21/R22/R23）不再生成；退役规则见下方 `RETIRED_CC`。
 *
 * 数据源（只读，全部现读，不手搓）：
 *   _census/p1_input_map.json     每条 conversion/assemblyMatrix 的 status/evidence/eut/eutField/tier/fluids
 *   _census/p1_script_hits.json   19 条「原本判 missing、其实整合包脚本里有」的真实 id（hits[].id，优先采用）
 *   _census/nac_cc_items.json     169 条 CC 物品权威对照表（records[].itemId，禁止自己算 id）
 *   _gtnh/javap_nac/GTValues.txt  NH GTValues 的 javap 转储 —— 用来**现算** TierEU.RECIPE_* 的实数
 *
 * ★ 输入物解析优先级（任务书冻结）：
 *   ① p1_script_hits.json 里 found:true 的 19 条 ⇒ 用 hits[].id（**不再用 substitute 候选**）
 *   ② 否则 p1_input_map.json 的 input.id（status=have / substitute 两种都算（表里已落定））
 *   ③ 否则 candidates 里**第一个形如 `namespace:path` 的合法 id**（candidates[0] 有 7 条是中文建议句，不是 id）
 *   ⇒ ①②③ 之外无解即抛错（宁可不出图，也不猜）
 */
'use strict';

const fs = require('fs');
const path = require('path');

const ROOT = __dirname;
const CENSUS = path.join(ROOT, '_census');
const RES = path.join(ROOT, 'gtl-modular', 'src', 'main', 'resources', 'data', 'gtlmodular', 'recipes', 'nanochip');
const NS = 'gtlmodular';

// ------------------------------------------------------------------ 读表
function loadJson(p) {
    return JSON.parse(fs.readFileSync(p, 'utf8'));
}
const inputMap = loadJson(path.join(CENSUS, 'p1_input_map.json'));
const scriptHits = loadJson(path.join(CENSUS, 'p1_script_hits.json'));
const ccTable = loadJson(path.join(CENSUS, 'nac_cc_items.json'));

// ------------------------------------------------------------------ ★ 退役名单（防复活闸门）
/**
 * NAC「特殊系列」三档自造电路 —— **整组退役**（2026-09-18 玩家裁决 E）。
 *
 * 维基逐字（`_census/wiki_抓取/raw/特殊系列电路板.wiki.txt`，只读语料）：
 *   `|电路板1-等级=UMV |电路板2-等级=UXV |电路板3-等级=MAX`；
 *   `|电路板3-名字=普朗克尺度电路`、`|电路板3-获取=普朗克尺度电路等效于任意MAX电路板`；
 *   总述「作为**异星、寰宇、超时空三种电路板还未更新时的暂时性的 UMV-MAX 级电路替代品，后续可能会移除。**」；
 *   历史：2.9.0 新增 ⇒ NH 这三档是给自己补档位空洞的**过渡件**。
 * 本包同档真件：`#gtceu:circuits/uxv|opv|max`（kubejs 分别引用 25/17/18 处）与
 * `kubejs:suprachronal_uxv|opv|max`（证据 `_census/kubejs_items.json`）。
 *
 * 退役落点（数据层在 `_census/p1_input_map.json`，本文件只在生成期做**闸门**）：
 *   · 4 条换算配方（真实物品 → CC）—— 真实物品本身也退役了
 *   · 4 条组装矩阵配方（R20 PicoCircuit / R21 QuantumCircuit / R22 PlanckManifold / R23 PlanckCircuit）
 * 生成器不会"猜"：只要 p1_input_map 里还残留任何一条退役 cc，就**当场抛错**（宁可不出图，也不复活）。
 * 删除陈旧产物由本文件保证 —— 见下方 `removeTree()`（本机沙箱下 `fs.rmSync` 是静默 no-op，必须自己实现）。
 */
const RETIRED_CC = [
    { cc: 'PicoCircuit', reason: 'NH 过渡件（维基 电路板1-等级=UMV）⇒ 本包真件 kubejs:suprachronal_uxv' },
    { cc: 'QuantumCircuit', reason: 'NH 过渡件（维基 电路板2-等级=UXV）⇒ 本包真件 kubejs:suprachronal_opv' },
    { cc: 'PlanckCircuit', reason: 'NH 过渡件（维基 电路板3-名字=普朗克尺度电路、等级=MAX）⇒ 本包真件 kubejs:suprachronal_max' },
    { cc: 'PlanckManifold', reason: '流形无对应真件，随普朗克电路一并退役（NH 侧只被 R23 消耗）' },
];
const RETIRED_CC_NAMES = new Set(RETIRED_CC.map(r => r.cc));

/** ★ 闸门：p1_input_map 里不得残留任何退役条目（残留 = 会重新生成退役配方）。 */
{
    const residual = [];
    for (const e of inputMap.conversion || []) if (RETIRED_CC_NAMES.has(e.cc)) residual.push('conversion/' + e.cc);
    for (const r of inputMap.assemblyMatrix || []) if (RETIRED_CC_NAMES.has(r.outputCC)) residual.push('assemblyMatrix#' + r.index + '/' + r.outputCC);
    if (residual.length) {
        throw new Error('p1_input_map.json 里仍有已退役的 NAC「特殊系列」条目（退役必须落在该表上，见本文件 RETIRED_CC）：'
            + residual.join(', '));
    }
    const declared = (inputMap.retired && inputMap.retired.ccs) || [];
    if (declared.length !== RETIRED_CC.length) {
        throw new Error(`p1_input_map.retired.ccs 应 ${RETIRED_CC.length} 条，实为 ${declared.length} —— 退役记录缺失，请复核`);
    }
    for (const r of RETIRED_CC) if (!declared.includes(r.cc)) throw new Error('p1_input_map.retired.ccs 缺 ' + r.cc);
}

const ccByEnum = new Map(ccTable.records.map((r) => [r.enumName, r]));
const ccIds = new Set(ccTable.records.map((r) => NS + ':' + r.itemId));
const hitByCc = new Map(scriptHits.hits.map((h) => [h.cc, h]));

// ------------------------------------------------------------------ TierEU.RECIPE_* 实数值（现读 NH 转储 + 现算）
/**
 * NH `gregtech.api.enums.TierEU` 的 `RECIPE_X = GTValues.VP[i]`，
 * `GTValues.VP = V.map(v -> v*30/32)`（BigInteger 乘 30 再整除 32）。
 * 这里不背数字：直接从 `_gtnh/javap_nac/GTValues.txt`（javap -c 转储）里
 * ① 抓出静态块构造的 V 数组 16 个字面量；② 校验 lambda$static$0 里确实是 *30 / 32。
 */
function readTierEuRecipeConstants() {
    const dump = fs.readFileSync(path.join(ROOT, '_gtnh', 'javap_nac', 'GTValues.txt'), 'utf8').split(/\r?\n/);
    // ① V 数组：从 `newarray long` 起，收集 `// long <N>l` 注释直到 `putstatic ... Field V:[J`
    const v = [];
    let inArray = false;
    for (const line of dump) {
        if (!inArray) {
            if (/newarray\s+long/.test(line)) inArray = true;
            continue;
        }
        const m = line.match(/\/\/\s*long\s+(-?\d+)l\s*$/);
        if (m) { v.push(Number(m[1])); continue; }
        if (/putstatic/.test(line) && /Field V:\[J/.test(line)) break;
    }
    if (v.length !== 16) {
        throw new Error('GTValues.txt 里 V 数组字面量应为 16 个，实读 ' + v.length + ' 个 —— 转储变了，请复核解析');
    }
    // ② 校验 lambda$static$0 = v*30/32
    const lambdaIdx = dump.findIndex((l) => /lambda\$static\$0\(long\)/.test(l));
    if (lambdaIdx < 0) throw new Error('GTValues.txt 里找不到 lambda$static$0(long) —— 无法确认 VP 公式');
    const body = dump.slice(lambdaIdx, lambdaIdx + 20).join('\n');
    if (!/BigInteger\.multiply/.test(body) || !/\/\/ long 30l/.test(body) || !/\/\/ long 32l/.test(body)) {
        throw new Error('lambda$static$0 不再是 v*30/32 —— 请重新核对 TierEU.RECIPE_* 的推导');
    }
    const idx = { ULV: 0, LV: 1, MV: 2, HV: 3, EV: 4, IV: 5, LuV: 6, ZPM: 7, UV: 8, UHV: 9, UEV: 10, UIV: 11, UMV: 12, UXV: 13, MAX: 14 };
    const out = {};
    for (const [name, i] of Object.entries(idx)) {
        out['RECIPE_' + name] = Math.floor((v[i] * 30) / 32); // BigInteger 整除 = 向零截断，V 全为正
    }
    return { v, constants: out };
}

const tierEu = readTierEuRecipeConstants();

// ------------------------------------------------------------------ 输入物解析
const ID_RE = /^[a-z0-9_.\-]+:[a-z0-9_./\-]+$/;
function isId(s) {
    return typeof s === 'string' && ID_RE.test(s);
}

/** 返回 { id, source, detail } —— source ∈ p1_real / script_hit / census_have / census_substitute / candidateN */
function resolveConversionInput(entry) {
    // ★ 2026-09-17 P1 缺件落地：`p1RealInput` 是本模组**真造出来**的缺件 id（材料形态 / 自注册物品），
    //   优先级最高 —— 缺件造好后由 _census/_apply_p1_realids.js 写进表里，重跑本生成器即自动替换掉
    //   那些临时顶替物（tritanium_single_wire / uhpic_chip 一类），从而消掉"多对一"的输入碰撞。
    if (entry.p1RealInput && isId(entry.p1RealInput.id)) {
        return {
            id: entry.p1RealInput.id,
            source: 'p1_real',
            detail: entry.p1RealInput.source || 'P1 缺件落地',
        };
    }
    const hit = hitByCc.get(entry.cc);
    if (hit && hit.found === true) {
        if (!isId(hit.id)) throw new Error('script_hits 的 id 不是合法 id: ' + entry.cc + ' -> ' + hit.id);
        return { id: hit.id, source: 'script_hit', detail: hit.evidence || '' };
    }
    if (entry.input && isId(entry.input.id)) {
        return {
            id: entry.input.id,
            source: entry.status === 'have' ? 'census_have' : 'census_substitute',
            detail: (entry.evidence || '').slice(0, 200),
        };
    }
    const cands = entry.candidates || [];
    for (let i = 0; i < cands.length; i++) {
        if (isId(cands[i])) {
            return {
                id: cands[i],
                source: 'candidate' + i,
                candidateIndex: i,
                detail: cands.join(' | '),
            };
        }
    }
    throw new Error('换算输入物无法解析（无 hit / 无 input.id / 候选里也没有合法 id）：' + entry.cc
        + ' status=' + entry.status + ' candidates=' + JSON.stringify(cands));
}

// ------------------------------------------------------------------ 组 JSON（键序固定）
function sized(id, count) {
    return { type: 'gtceu:sized', count: count, ingredient: { item: id } };
}
function itemIn(id, count) {
    return { content: sized(id, count) };
}
function fluidIn(id, amount) {
    return { content: { amount: amount, value: { fluid: id } } };
}
function euIn(value) {
    return { content: value };
}
const MOD_CONDITIONS = [{ type: 'forge:mod_loaded', modid: 'gtceu' }];

function conversionJson(typeId, inputId, outputId) {
    return {
        type: typeId,
        conditions: MOD_CONDITIONS,
        duration: 100,
        inputs: { item: [itemIn(inputId, 1)] },
        outputs: { item: [itemIn(outputId, 1)] },
        tickInputs: { eu: [euIn(0)] },
    };
}

function assemblyMatrixJson(typeId, row, outputId, calibration) {
    const data = { nanochip_assembly_matrix_tier: row.tier };
    if (calibration) data['circuit-calibration'] = calibration;
    const json = {
        type: typeId,
        conditions: MOD_CONDITIONS,
        duration: row.durationTicks,
        data: data,
        inputs: {
            item: row.items.map((it) => itemIn(it.ccId, it.count)),
        },
        outputs: { item: [itemIn(outputId, 1)] },
        tickInputs: { eu: [euIn(assemblyEut(row))] },
    };
    if (row.fluids.length > 0) {
        json.inputs.fluid = row.fluids.map((f) => fluidIn(f.id, f.amount));
    }
    return json;
}

/** 组装矩阵某一条的 EU/t：字面量直接用；档位常量换算成 NH 实数（含推导来源）。 */
function assemblyEut(row) {
    if (row.eutField) {
        const key = row.eutField; // 形如 RECIPE_LuV
        if (!(key in tierEu.constants)) {
            throw new Error('p1_input_map 里的 eutField 在 TierEU 常量表里找不到: ' + key);
        }
        return tierEu.constants[key];
    }
    if (typeof row.eut !== 'number') {
        throw new Error('assemblyMatrix #' + row.index + ' 既没有 eut 也没有 eutField');
    }
    return row.eut;
}

// ============================================================================================
// 双焊料配方（照 AD / gtladditions 的做法；见上方矩阵段的注释）
// ============================================================================================

/** 超级焊料版文件名后缀。 */
const SUPER_SUFFIX = '_super_solder';

/** 普通突变活性焊料（UV 阶段可得），用于 "普通版" 配方。 */
const SOLDER_SIMPLE = { id: 'gtceu:mutated_living_solder', amount: 18 };
/** 超级突变活性焊料（UEV 阶段可得），用于 "超级版" 配方；量翻倍、耗时减半、产出不变。 */
const SOLDER_SUPER = { id: 'gtceu:super_mutated_living_solder', amount: 36 };
/** 矩阵档位的档位内乘数（与磁盘现状一致：assembly 1x / computer 2x / mainframe 4x）。 */
const STAGE_SCALE = { processor: 1, assembly: 1, computer: 2, mainframe: 4 };

function fluidEntry(id, amount) {
    return { content: { amount: amount, value: { fluid: id } } };
}

/** 由输出 item id 判断它属于哪一档（processor/assembly/computer/mainframe）。 */
function stageOf(outId) {
    const body = String(outId);
    for (const st of ['assembly', 'computer', 'mainframe', 'processor']) {
        if (body.endsWith('_' + st)) return st;
    }
    return null;
}

/**
 * 给“普通版”配方写入流体（就地修改）。
 *
 * <p>口径（与 2026-11-12 磁盘现状逐条对过）：
 * <ul>
 *   <li>processor：只放焊料 18 mB（该表只有 1 个流体槽）；</li>
 *   <li>assembly/computer/mainframe：焊料 18×档位 + `pcb_coolant` 10000×档位。</li>
 * </ul>
 */
function applyDualSolder(json, row, outId) {
    const stage = stageOf(outId);
    const scale = STAGE_SCALE[stage] === undefined ? 1 : STAGE_SCALE[stage];
    const fluids = [fluidEntry(SOLDER_SIMPLE.id, SOLDER_SIMPLE.amount * scale)];
    if (stage !== 'processor') {
        fluids.push(fluidEntry('gtceu:pcb_coolant', 10000 * scale));
    }
    json.inputs = json.inputs || {};
    json.inputs.fluid = fluids;
    return json;
}

/**
 * 由“普通版”派生“超级版”：焊料换成超级突变活性焊料（量翻倍），**耗时减半**，其余一律不动。
 * 产量、输入物品、EU/t 全部保持原样 ⇒ 只是更省时间。
 */
function buildSuperSolderVersion(base) {
    const j = JSON.parse(JSON.stringify(base));
    const stage = (function () {
        const out = j.outputs && j.outputs.item && j.outputs.item[0]
            && j.outputs.item[0].content && j.outputs.item[0].content.ingredient
            && j.outputs.item[0].content.ingredient.item;
        return stageOf(out);
    })();
    const scale = STAGE_SCALE[stage] === undefined ? 1 : STAGE_SCALE[stage];
    const fluids = [fluidEntry(SOLDER_SUPER.id, SOLDER_SUPER.amount * scale)];
    if (stage !== 'processor') {
        fluids.push(fluidEntry('gtceu:pcb_coolant', 10000 * scale));
    }
    j.inputs = j.inputs || {};
    j.inputs.fluid = fluids;
    j.duration = Math.max(1, Math.floor(base.duration / 2));
    return j;
}

// ------------------------------------------------------------------ 生成
function writeJson(file, obj) {
    fs.writeFileSync(file, JSON.stringify(obj, null, 2) + '\n', 'utf8');
}

const conversionDir = path.join(RES, 'conversion');
const matrixDir = path.join(RES, 'assembly_matrix');

/**
 * 递归删除（**不用 `fs.rmSync`**：本机沙箱下 `rmSync` 是静默 no-op，实测 `unlinkSync`/`rmdirSync` 才真删）。
 * ★ 2026-09-18 加：退役 8 条配方后，上一版生成出来的 `cc_{pico,quantum,planck}_circuit.json` /
 * `cc_planck_manifold.json` / `02{0,1,2,3}_cc_*.json` 必须**真的从盘上消失** —— 否则旧的 122 条
 * 会与新的 114 条并存，`check_nac_p1_recipes.js` 的「目录 ⇔ 表」双向对账会红在陈旧文件上，
 * 更糟的是游戏里会加载一批指向已退役 id 的配方。
 *
 * ★★ 2026-11-12 **重大修正：不再整目录清空**。
 *
 * <p>旧实现在生成前 `removeTree(conversionDir)` + `removeTree(matrixDir)`，然后只重建自已表里的条目：
 * <ul>
 *   <li>`conversion/`：旧版只重建 **94** 条，而 CC 表有 **200** 个未处理件 ⇒ 每次跑都**静默删掉 106 条**
 *       （三系列的 RAM/SMD/PCB、PEEK 箔…）。2026-11-12 实测本次误删 123 个 JSON 即此因；</li>
 *   <li>`assembly_matrix/`：本表只有 **19** 条，而磁盘有 **23** 条 —— 多出的 9 条三系列矩阵
 *       （exotic/cosmic/supracausal）是本包自造，NH 无对应 ⇒ 一并被删。</li>
 * </ul>
 *
 * <p>现改为**只删“退役名单里的陈旧产物”**（`RETIRED_CC` 对应的文件名 + 旧版 Pico/Quantum/Planck 编号文件），
 * 其余文件一律不碰。这样重跑多次也不会伤到任何不在本表里的配方。
 */
function removeStaleRetired(dir, names) {
    if (!fs.existsSync(dir)) return 0;
    let n = 0;
    for (const name of names) {
        const p = path.join(dir, name);
        if (fs.existsSync(p)) { fs.unlinkSync(p); n++; }
    }
    return n;
}

// 退役件对应的陈旧文件名（换算目录：cc_<snake(enum)>.json；矩阵目录：旧 020/021/022/023）
const STALE_CONVERSION = RETIRED_CC.map((r) => 'cc_' + r.cc.replace(/([a-z0-9])([A-Z])/g, '$1_$2').toLowerCase() + '.json');
const STALE_MATRIX = ['020_cc_pico_circuit.json', '021_cc_quantum_circuit.json',
    '022_cc_planck_manifold.json', '023_cc_planck_circuit.json'];
fs.mkdirSync(conversionDir, { recursive: true });
fs.mkdirSync(matrixDir, { recursive: true });
const nStaleConv = removeStaleRetired(conversionDir, STALE_CONVERSION);
const nStaleMtx = removeStaleRetired(matrixDir, STALE_MATRIX);
if (nStaleConv || nStaleMtx) {
    console.log('  清理退役陈旧产物: 换算 ' + nStaleConv + ' 个, 矩阵 ' + nStaleMtx + ' 个');
}

// ---- 99 条换算
const sourceCount = {};
const resolved = [];
const conversionFiles = [];
// ---- 换算（= `nac_cc_items.json` 的**全部未处理件**；`p1_input_map` 只负责补证据/替代）
//
// ★ 2026-11-12：换算必须覆盖 CC 表里**每一个**未处理件（现 200 条），不能只覆盖 map 里登记的 94 条。
//   否则「封装 CC」这层会有 106 件无获取途径（三系列 RAM/SMD/PCB、PEEK 箔…），而它们在
//   `assembly_complex` / `assembly_matrix` / 各模块配方里都是真实输入 ⇒ 整条三系列链断路。
//   解析优先级：① map 条目（带证据）→ ② CC 表 `realRef`（`mat:` / `item:` / `itemns:`）→ ③ 报错不猜。
/** CC 表 `mat:<材料>:<形态>` 的形态词 → GTCEu 实际 id 里的形态后缀（大部分同名，少数需展开）。 */
const SHAPE_ALIAS = {
    wireGtDouble: 'double_wire',
    wireGtSingle: 'single_wire',
    wireGtQuadruple: 'quadruple_wire',
    wireGtOctal: 'octal_wire',
    wireGtHex: 'hex_wire',
    wireFine: 'fine_wire',
    plateDouble: 'double_plate',
    plateDense: 'dense_plate',
    gearSmall: 'small_gear',
    springSmall: 'small_spring',
};

function inputFromRealRef(cc) {
    const ref = cc.realRef;
    if (!ref) return null;
    let m;
    // ★ 2026-11-12 修正：GTCEu 的物品 id 是「材料_形态」（adamantine_plate / tungsten_steel_plate /
    // draconiumawakened_double_wire），不是「形态_材料」。旧写法顺序反了，生成出 gtceu:plate_adamantine
    // 这种游戏里不存在的 id（实测 41 条换算中招，与部署版 jar 逐条对比后确认）。
    if ((m = /^mat:([a-z0-9_]+):([a-z0-9_]+)$/.exec(ref))) {
        const form = SHAPE_ALIAS[m[2]] || m[2];
        return { id: 'gtceu:' + m[1] + '_' + form, source: 'realRef.mat' };
    }
    if ((m = /^itemns:([a-z0-9_]+):([a-z0-9_]+)$/.exec(ref))) return { id: m[1] + ':' + m[2], source: 'realRef.itemns' };
    if ((m = /^item:([a-z0-9_]+)$/.exec(ref))) return { id: 'gtceu:' + m[1], source: 'realRef.item' };
    return null;
}

const mappedEnums = new Set(inputMap.conversion.map((e) => e.cc));
const derivedConversion = ccTable.records
    .filter((r) => !r.isProcessed && !mappedEnums.has(r.enumName))
    .map((r) => {
        const got = inputFromRealRef(r);
        if (!got) {
            throw new Error('换算输入物无法从 realRef 推导，且 p1_input_map 未登记：' + r.enumName
                + '（realRef=' + r.realRef + '）—— 请补 p1_input_map.conversion，不猜');
        }
        return {
            cc: r.enumName, ccId: NS + ':' + r.itemId, metaId: r.metaId,
            input: { id: got.id, count: 1 }, status: 'derived',
            evidence: 'nac_cc_items.json realRef=' + r.realRef,
        };
    });
const conversionEntries = inputMap.conversion.concat(derivedConversion);

for (const entry of conversionEntries) {
    const cc = ccByEnum.get(entry.cc);
    if (!cc) throw new Error('p1_input_map 的 cc 不在 nac_cc_items 表里: ' + entry.cc);
    const outId = NS + ':' + cc.itemId;
    if (entry.ccId !== outId) {
        // 冻结契约：以 nac_cc_items.json 为准，但必须报告
        process.stderr.write('[WARN] ccId 不一致，以 nac_cc_items.json 为准: ' + entry.cc
            + ' map=' + entry.ccId + ' table=' + outId + '\n');
    }
    const r = resolveConversionInput(entry);
    sourceCount[r.source] = (sourceCount[r.source] || 0) + 1;
    const file = path.join(conversionDir, cc.itemId + '.json');
    writeJson(file, conversionJson(NS + ':nanochip_conversion', r.id, outId));
    conversionFiles.push(path.basename(file));
    resolved.push({
        metaId: entry.metaId,
        cc: entry.cc,
        ccItemId: cc.itemId,
        inputId: r.id,
        status: entry.status,
        source: r.source,
        candidateIndex: r.candidateIndex === undefined ? null : r.candidateIndex,
        nhExpression: entry.nhExpression,
        candidates: entry.candidates || [],
        evidence: r.detail,
    });
}

// ---- 23 条组装矩阵
const matrixFiles = [];
const matrixRows = [];
const CALIB_FALLBACK = { PlanckManifold: 'NONE' }; // 表里 calibration=null，NH 侧该常量显式用 CircuitCalibration.NONE（notes_recipes.md §5.1）

/**
 * 已在磁盘上的“输出 CC → 文件名”索引（现读，只读）。
 *
 * <p>★ 2026-11-12：`p1_input_map.assemblyMatrix` 的 `index` 是**独立连续编号 1..19**，
 * 而磁盘用的是**全局档位序列**（001/002/…/030，processor 与 assembly/computer/mainframe 交错），
 * 两者不是同一套编号。若直接用 `index` 当文件名前缀，会另写出一批重号文件，
 * 与磁盘原有文件并存（23 → 34 条，内容重复）。
 *
 * <p>现口径：**磁盘已有的沿用其文件名**（保证不重号），只有全新的才按表编号新建。
 */
const existingMatrixByName = new Map();
// ★ 2026-11-12 修正：**只扫 `assembly_matrix/` 一个目录**。
// 旧版把 `assembly_complex/` 也扫进来 ⇒ 只要某个 processor 输出同名，矩阵循环就会把
// 那条 processor 配方当矩阵配方重写（表型/时长/出量全变回 p1_input_map 的 NH 旧口径），
// 实测把手工调过的 7 条高产处理器（jar 0.3.385 版）全部回退成了“出 1 个”的旧版。
// processor 档由下方独立段处理，与矩阵互不干扰。
for (const dir of [matrixDir]) {
    if (!fs.existsSync(dir)) continue;
    for (const n of fs.readdirSync(dir)) {
        if (!n.endsWith('.json')) continue;
        try {
            const j = JSON.parse(fs.readFileSync(path.join(dir, n), 'utf8'));
            const out = j && j.outputs && j.outputs.item && j.outputs.item[0]
                && j.outputs.item[0].content && j.outputs.item[0].content.ingredient
                && j.outputs.item[0].content.ingredient.item;
            if (out && !existingMatrixByName.has(out)) existingMatrixByName.set(out, path.join(dir, n));
        } catch (e) { /* 不是配方 JSON 就跳过 */ }
    }
}

for (const row of inputMap.assemblyMatrix) {
    const outCc = ccByEnum.get(row.outputCC);
    if (!outCc) throw new Error('组装矩阵输出 CC 不在 nac_cc_items 表里: ' + row.outputCC);
    const outId = NS + ':' + outCc.itemId;
    for (const it of row.items) {
        if (!ccIds.has(it.ccId)) throw new Error('组装矩阵 #' + row.index + ' 的输入 ' + it.ccId + ' 不在 176 条里');
    }
    const calibration = outCc.calibration || CALIB_FALLBACK[row.outputCC] || null;
    // 文件名：磁盘上已有这个输出 ⇒ 沿用原路径（不重号）；否则按表内 index 新建。
    const existing = existingMatrixByName.get(outId);
    // processor 档（outputItem 以 `_processor` 结尾）不归矩阵循环管：它的配方实体在 `assembly_complex/`
    // （或磁盘上已存在于 `assembly_matrix/` 的同名文件，见下方 002/007 特例），
    // 由「processor 双焊料段」只补流体与超级版，**不重建物品输入/输出/EU**。
    // 这里绝不能用 NH 旧口径覆盖手工调好的高产处理器（实测会从“出 8”回退成“出 1”）。
    if (stageOf(outId) === 'processor') {
        continue;
    }
    const file = existing || path.join(matrixDir, String(row.index).padStart(3, '0') + '_' + outCc.itemId + '.json');

    // ==================================================================================
    // ★ 2026-11-12 新增：**双焊料配方**（照 AD / gtladditions 的做法）
    //
    // AD 的 `SocProcess$CircuitAssembler.addCircuitRecipe` 对每条处理器配方都生成两个版本：
    // ① `SuperMutatedLivingSolder`（超级突变活性焊料）② `MutatedLivingSolder`（突变活性焊料，用 copy() 复制①）。
    // 两者**产出完全相同**，只有焊料种类与耗时不同（超级版更快）。
    //
    // 我们的落地口径（与磁盘现状一致）：
    //   · 普通版  = 突变活性焊料，量固定，耗时保持原值；
    //   · 超级版  = 超级突变活性焊料，量加倍，**耗时减半**，产出不变 ⇒ 只是更省时间。
    //   · 矩阵档位（assembly/computer/mainframe）额外加 `pcb_coolant`（1x/2x/4x）。
    //   · 文件名：普通版沿用原名，超级版 `<stem>_super_solder.json`。
    //
    // 说明：processor 档由 `nanochip_assembly_complex` 表跑（1 个流体槽），
    // 它们在 `assembly_complex/` 目录，不在本循环管辖范围（见本文件末尾的 processor 段）。
    // ==================================================================================
    // ★ 2026-11-12：**磁盘已有的沿用磁盘内容**（只补流体），不再用 p1_input_map 的 NH 旧口径重建。
    // 原因：`assembly_matrix/` 里的配方早已被手工调过（产量 4/2/1、EU 档位、材料替代），
    // 与 NH 原文（出 1、EU 按表）**不一致**；用旧口径重建会把它们全部改回 NH 值（实测出 4→1、EU 也错）。
    // 磁盘上确实没有的，才按 p1_input_map 新建。
    let base;
    if (existing) {
        const onDisk = JSON.parse(fs.readFileSync(existing, 'utf8'));
        delete onDisk.inputs.fluid;      // 先清掉旧流体，保证幂等
        base = onDisk;
    } else {
        base = assemblyMatrixJson(NS + ':nanochip_assembly_matrix', row, outId, calibration);
    }
    applyDualSolder(base, row, outId);
    writeJson(file, base);
    matrixFiles.push(path.basename(file));

    // 超级焊料版（同名 + `_super_solder`）
    const superFile = file.replace(/\.json$/, SUPER_SUFFIX + '.json');
    writeJson(superFile, buildSuperSolderVersion(base));
    matrixFiles.push(path.basename(superFile));
    matrixRows.push({
        index: row.index,
        outputCC: row.outputCC,
        outputItemId: outId,
        tier: row.tier,
        durationTicks: row.durationTicks,
        eut: assemblyEut(row),
        eutField: row.eutField || null,
        calibration: calibration,
        itemCount: row.items.length,
        fluidCount: row.fluids.length,
        file: path.basename(file),
    });
}

// ============================================================================================
// 磁盘上存在、但 `p1_input_map` 未收录的矩阵配方（= 三系列 exotic/cosmic/supracausal 等自造段）
// ============================================================================================
// ★ 为什么不靠 p1_input_map：那张表只有 19 条（NH 原文口径，crystal..optical），
//   三系列的 9 条矩阵是**本包自己扩展的**（NH 没有完整四档），永远进不了那张表。
//   但它们同样要有“普通焊料 / 超级焊料”两个版本 ⇒ 这里单独扫磁盘补齐，只补流体与超级版，
//   配方内容（物品/产量/EU）一律沿用磁盘现状。
{
    let extra = 0;
    for (const n of fs.readdirSync(matrixDir).sort()) {
        if (!n.endsWith('.json') || n.endsWith(SUPER_SUFFIX + '.json')) continue;
        if (n.includes('_high')) continue; // 高产量版由后文「高产量配方」段统一生成（含 super 分支）
        const p = path.join(matrixDir, n);
        let j;
        try { j = JSON.parse(fs.readFileSync(p, 'utf8')); } catch (e) { continue; }
        if (j.type !== NS + ':nanochip_assembly_matrix') continue;

        const outId = j.outputs && j.outputs.item && j.outputs.item[0]
            && j.outputs.item[0].content && j.outputs.item[0].content.ingredient
            && j.outputs.item[0].content.ingredient.item;
        if (!outId) continue;

        // 已经被上面的循环处理过（= 在其输出集合里）则跳过
        if (matrixRows.some((r) => r.outputItemId === outId)) continue;

        const base = JSON.parse(fs.readFileSync(p, 'utf8'));
        delete base.inputs.fluid;
        applyDualSolder(base, null, outId);
        writeJson(p, base);
        matrixFiles.push(n);

        const superName = n.replace(/\.json$/, SUPER_SUFFIX + '.json');
        writeJson(path.join(matrixDir, superName), buildSuperSolderVersion(base));
        matrixFiles.push(superName);
        extra++;
    }
    if (extra) console.log('  矩阵补双焊料（磁盘自造段）: ' + extra + ' 条 → ' + (extra * 2) + ' 条');
}

// ============================================================================================
// processor 档（`assembly_complex/`）：只补流体 + 生成超级焊料版，**不重建物品配方**
// ============================================================================================
//
// ★ 为何不重建：`assembly_complex/` 里的 7 条处理器配方是**手工调过的**（带 HA SoC 的高产路线，
//   出 8 个；见 2026-11-12 与部署版 jar 0.3.385 的逐条对比），`p1_input_map.assemblyMatrix`
//   里的 NH 原文口径（出 1 个、老原料）**已不适用**。本段因此只做两件事：
//     ① 给现有配方补上“突变活性焊料 18 mB”（普通版）；
//     ② 派生 `_super_solder` 版（超级焊料 36 mB、耗时减半、产量不变）。
//   物品输入/输出/EU/档位一律不动。
const complexDir = path.join(RES, 'assembly_complex');
fs.mkdirSync(complexDir, { recursive: true });
const complexFiles = [];

{
    // ★ 除了 `assembly_complex/`，`assembly_matrix/` 里也有 2 条 processor 配方
    //   （002_cc_crystal_processor / 007_cc_wetware_processor，磁盘上本来就在那边）。
    //   两处一起扫，逐条只补流体 + 派生超级版，绝不重建物品输入/输出/EU。
    const targets = [];
    for (const dir of [complexDir, matrixDir]) {
        for (const n of fs.readdirSync(dir)) {
            if (!n.endsWith('.json') || n.endsWith(SUPER_SUFFIX + '.json')) continue;
            if (!/_processor\.json$/.test(n)) continue;
            targets.push({ dir, name: n });
        }
    }
    targets.sort((a, b) => (a.dir + a.name).localeCompare(b.dir + b.name));

    if (targets.length !== 9) {
        console.warn('  ⚠ processor 配方应为 9 条（complex 7 + matrix 2），实读 ' + targets.length + ' 条');
    }

    for (const t of targets) {
        const dir = t.dir, name = t.name;
        const p = path.join(dir, name);
        let j;
        try { j = JSON.parse(fs.readFileSync(p, 'utf8')); }
        catch (e) { console.warn('  ⚠ 跳过无法解析的 ' + name); continue; }
        if (j.type !== NS + ':nanochip_assembly_complex') continue;

        const outId = j.outputs && j.outputs.item && j.outputs.item[0]
            && j.outputs.item[0].content && j.outputs.item[0].content.ingredient
            && j.outputs.item[0].content.ingredient.item;
        const original = JSON.parse(JSON.stringify(j));
        // 去掉历史上可能已存在的流体，再按统一口径重写（幂等）
        delete original.inputs.fluid;
        original.duration = original.duration; // keep

        const plain = applyDualSolder(JSON.parse(JSON.stringify(original)), null, outId);
        writeJson(p, plain);
        complexFiles.push(name);

        const superName = name.replace(/\.json$/, SUPER_SUFFIX + '.json');
        writeJson(path.join(dir, superName), buildSuperSolderVersion(plain));
        complexFiles.push(superName);
    }
    console.log('  processor 双焊料: ' + targets.length + ' → ' + (targets.length * 2) + ' 条（普通 + 超级）');
}

// ============================================================================================
// 高产量配方（★ 2026-11-12）
// ============================================================================================
//
// 玩家口径：「每个系列都要有一条产量更高的配方」，阶梯为 **处理器 16 / 集群 8 / 超算 8 / 主机 8**
// （普通版为 8 / 4 / 2 / 1）。两套并存 ⇒ 玩家可按阶段二选一。
//
// 两条实现路线（按系列区分，都按 GTNH / AD 的真实结构来）：
//
// ① **精简路线**（晶体 / 湿件）：4 件输入 —— 电路板 + 系列 SoC + 导线×8 + 螺栓×8。
//    这两条在磁盘上本来就有（`assembly_matrix/002`、`007`），历史上被当作“第二条处理器配方”；
//    现统一口径为「高产量版」，产量抬到 16。
//
// ② **AD 路线**（生物 / 光学 / 奇异 / 寰宇 / 超因果）：电路板 + **专属 SoC** + 2 种专属材料。
//    照 `gtladditions` 的 `SocProcess$CircuitAssembler`：
//      生物→卓越 SoC、光学→超凡 SoC、奇异→混沌 SoC、寰宇→时空 SoC、超因果→原初 SoC。
//    这些配方落在 `assembly_complex/`，文件名 `NN_ad_<系列>_processor.json`。
//
// 集群 / 超算 / 主机：由普通版复制而来，**只改产量与时长**（产量→8，时长×2 作为代价），
// 文件名加 `_high` 后缀。晶体/湿件原本没有这三档的高产版，本段一并补上。
//
// ★ 这一段**只新增/覆写自己命名空间内的文件**（`*_ad_*`、`*_high.json`），不碰普通版内容。

/** 高产量档位的目标产量。 */
const HIGH_YIELD = { processor: 16, assembly: 8, computer: 8, mainframe: 8 };

/** NAC 处理过的 CC 件 id（`gtlmodular:cc_processed_<name>`）。 */
const CC = (name) => NS + ':cc_processed_' + name;

/** 精简路线需要抬产量的处理器（磁盘上已存在的 4 件版）。 */
const COMPACT_PROCESSORS = [
    path.join(matrixDir, '002_cc_crystal_processor.json'),
    path.join(matrixDir, '007_cc_wetware_processor.json'),
];

/**
 * 高产量处理器配方（5 条，产量 16）。
 *
 * ★ 2026-11-14 修正：**必须全部用 NAC 处理过的 CC 件（`gtlmodular:cc_processed_*`）**，
 *   不能用原始 GTCEu / gtladditions 物品。之前那版直接引用了
 *   `kubejs:*_printed_circuit_board`、`gtladditions:*_soc`、`gtceu:*_ingot` 等**未经过 NAC 加工**的物品，
 *   与 NAC「一切材料都要过 CC 层」的体系冲突（玩家实测指出）。
 *
 * 结构（精简版，件数比普通版少、产量翻倍）：
 *   电路板 + 系列 SoC + 系列核心 + 系列导线
 *
 * 每个输入 id 都已核实存在**产出配方**（cutting_chamber / smd_processor / board_processor /
 * *_organizer / wire_tracer 等），不存在死配方。
 */
const AD_PROCESSORS = [
    {
        fam: 'bioware', seq: 10, eu: 122880, tier: 7, cal: 'BIO',
        items: [
            [CC('board_bio_mutated'), 1],
            [CC('highly_advanced_soc'), 1],
            [CC('qbit_cpu_chip'), 2],
            [CC('fine_naquadah_wire'), 8],
        ],
        fluid: { id: 'gtceu:mutated_living_solder', amount: 144 },
    },
    {
        fam: 'optical', seq: 11, eu: 491520, tier: 8, cal: 'OPTICAL',
        items: [
            [CC('board_optical'), 1],
            [CC('optical_soc'), 1],
            [CC('chip_optical_cpu'), 1],
            [CC('fine_dubnium_wire'), 8],
        ],
        fluid: { id: 'gtceu:mutated_living_solder', amount: 144 },
    },
    {
        fam: 'exotic', seq: 12, eu: 1966080, tier: 8, cal: 'EXOTIC',
        items: [
            [CC('exotic_printed_circuit_board'), 1],
            [CC('highly_advanced_soc'), 1],
            [CC('exotic_processing_core'), 1],
            [CC('fine_cinobite_wire'), 8],
        ],
        fluid: { id: 'gtceu:mutated_living_solder', amount: 72 },
    },
    {
        fam: 'cosmic', seq: 13, eu: 7864320, tier: 9, cal: 'COSMIC',
        items: [
            [CC('cosmic_printed_circuit_board'), 1],
            [CC('highly_advanced_soc'), 1],
            [CC('cosmic_processing_core'), 1],
            [CC('fine_cinobite_wire'), 8],
        ],
        fluid: { id: 'gtceu:mutated_living_solder', amount: 288 },
    },
    {
        fam: 'supracausal', seq: 14, eu: 31457280, tier: 9, cal: 'TEMPORAL',
        items: [
            [CC('supracausal_printed_circuit_board'), 1],
            [CC('highly_advanced_soc'), 1],
            [CC('supracausal_processing_core'), 1],
            [CC('cosmic_processing_unit_core'), 4],
        ],
        fluid: { id: 'gtceu:mutated_living_solder', amount: 360 },
    },
];

{
    let raised = 0;
    // ---- ① 精简路线：把已有的 4 件处理器抬到 16（连同 super 版）
    for (const p of COMPACT_PROCESSORS) {
        if (!fs.existsSync(p)) { console.warn('  ⚠ 精简高产量配方不存在，跳过: ' + p); continue; }
        for (const scope of [p, p.replace(/\.json$/, SUPER_SUFFIX + '.json')]) {
            if (!fs.existsSync(scope)) continue;
            const j = JSON.parse(fs.readFileSync(scope, 'utf8'));
            j.outputs.item[0].content.count = HIGH_YIELD.processor;
            writeJson(scope, j);
        }
        raised++;
    }

    // ---- ② 新建五条处理器高产量配方（产量 16），输入全部为 NAC 处理过的 CC 件
    for (const a of AD_PROCESSORS) {
        const items = a.items.map(([id, n]) => itemIn(id, n));
        // 每个输入都必须是本模组的 CC 件（防止有人又塞原始物品进来）
        for (const [id] of a.items) {
            if (!String(id).startsWith(NS + ':cc_')) {
                throw new Error('高产量处理器 ' + a.fam + ' 的输入 ' + id
                    + ' 不是 NAC 处理过的 CC 件（必须是 ' + NS + ':cc_*）');
            }
        }
        const j = {
            type: NS + ':nanochip_assembly_complex',
            conditions: MOD_CONDITIONS,
            duration: 150,
            data: { nanochip_assembly_matrix_tier: a.tier, 'circuit-calibration': a.cal },
            inputs: { item: items, fluid: [fluidIn(a.fluid.id, a.fluid.amount)] },
            outputs: { item: [itemIn(NS + ':cc_' + a.fam + '_processor', HIGH_YIELD.processor)] },
            tickInputs: { eu: [euIn(a.eu)] },
        };
        const name = String(a.seq).padStart(2, '0') + '_ad_' + a.fam + '_processor.json';
        writeJson(path.join(complexDir, name), j);
        complexFiles.push(name);
        raised++;
    }

    // ---- ③ 集群 / 超算 / 主机：复制普通版 → `_high`（产量 8，时长 ×2）
    let highMatrix = 0;
    for (const fam of ['crystal', 'wetware', 'bioware', 'optical', 'exotic', 'cosmic', 'supracausal']) {
        for (const st of ['assembly', 'computer', 'mainframe']) {
            const cands = fs.readdirSync(matrixDir)
                .filter((n) => n.endsWith('_' + fam + '_' + st + '.json')
                    && !n.includes(SUPER_SUFFIX) && !n.includes('_high'));
            if (!cands.length) continue;
            const src = path.join(matrixDir, cands[0]);
            const j = JSON.parse(fs.readFileSync(src, 'utf8'));
            j.outputs.item[0].content.count = HIGH_YIELD[st];
            j.duration = j.duration * 2;
            const highBase = cands[0].replace(/\.json$/, '_high.json');
            writeJson(path.join(matrixDir, highBase), j);
            // 高产量版同样要有超级焊料分支（产量不变、耗时减半），保持与普通版一致的两条路线
            writeJson(path.join(matrixDir, highBase.replace(/\.json$/, SUPER_SUFFIX + '.json')),
                buildSuperSolderVersion(j));
            highMatrix++;
        }
    }
    console.log('  高产量配方: 处理器 ' + raised + ' 条（产量16）+ 矩阵 ' + highMatrix + ' 条 ×2 焊料（产量8）');
}

// ------------------------------------------------------------------ 报告（供人看；自检脚本不依赖它）
const substitutes = resolved.filter((r) => r.source.startsWith('candidate'));
const report = {
    generator: 'gen_nac_p1_recipes.js',
    generatedFrom: {
        inputMap: '_census/p1_input_map.json',
        scriptHits: '_census/p1_script_hits.json',
        ccItems: '_census/nac_cc_items.json',
        tierEuDump: '_gtnh/javap_nac/GTValues.txt',
    },
    counts: {
        conversion: conversionFiles.length,
        assemblyMatrix: matrixFiles.length,
        ccItemsInTable: ccTable.records.length,
    },
    inputResolution: sourceCount,
    tierEuConstants: tierEu.constants,
    tierEuDerivation: 'RECIPE_X = floor(V[x] * 30 / 32)；V 读自 _gtnh/javap_nac/GTValues.txt（16 个字面量），'
        + '公式校验自同文件 lambda$static$0（BigInteger multiply 30 / divide 32）。'
        + 'GTCEu 1.4.4 无 TierEU 类（四个 jar 类名扫描 0 命中）。',
    usedEutFields: [...new Set(matrixRows.map((r) => r.eutField).filter(Boolean))],
    substituteCount: substitutes.length,
    substitutes: substitutes,
    conversionRecipes: resolved,
    assemblyMatrixRecipes: matrixRows,
};
fs.writeFileSync(path.join(CENSUS, 'p1_recipe_gen_report.json'), JSON.stringify(report, null, 2) + '\n', 'utf8');

// ------------------------------------------------------------------ 控制台摘要
const pad = (s, n) => String(s).padEnd(n);
console.log('=== gen_nac_p1_recipes.js 完成 ===');
console.log('换算配方      : ' + conversionFiles.length + ' 条 -> ' + path.relative(ROOT, conversionDir).replace(/\\/g, '/'));
console.log('组装矩阵配方  : ' + matrixFiles.length + ' 条 -> ' + path.relative(ROOT, matrixDir).replace(/\\/g, '/'));
console.log('输入物来源分布: ' + JSON.stringify(sourceCount));
console.log('TierEU 实数   : ' + JSON.stringify(tierEu.constants));
console.log('顶替条数      : ' + substitutes.length + '（脚本零命中，暂用候选顶替）');
for (const s of substitutes) {
    console.log('  - ' + pad(s.cc, 30) + ' -> ' + pad(s.inputId, 46) + ' [' + s.source + '] status=' + s.status);
}
console.log('报告          : _census/p1_recipe_gen_report.json');
