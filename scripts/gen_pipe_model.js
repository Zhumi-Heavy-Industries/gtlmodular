#!/usr/bin/env node
/**
 * 生成真空传送管道的**物品图标**模型 json（只影响图标；世界渲染由 NanochipPipeRenderer 现算）。
 *
 * 用法：node gtl-modular/scripts/gen_pipe_model.js
 * 输出：gtl-modular/src/main/resources/assets/gtlmodular/models/block/machine/part/vacuum_pipe.json
 *
 * 为什么用脚本生成而不是手写：这个模型有 6 个几何体 × 两层 ≈ 60 个面，
 * 每个面的 `uv` 都要与 Java 渲染器里的「十字带 UV 表」逐条对齐（见 NanochipPipeRenderer 类注释）。
 * 手写迟早会对不上；脚本里带自检，UV 表只写一遍。
 *
 * 几何必须与 java 渲染器**同口径**（否则"手里的和放下去的不一样"）：
 *   ★ 管芯 = PIPE_CORE_MIN/MAX（**5..11 = 6 像素 = 0.375 格**）—— 等于 NH 的
 *     `MTEVacuumConveyorPipe.getCollisionThickness()`（= 0.375f，正常路径），且
 *     NH 的 `MetaPipeEntity.renderInWorld` 对管芯与每根管臂用的是**同一对** min/max
 *     ⇒ 2026-09-17 第六轮起 **管臂与管芯同粗**（玩家：「管道的芯跟管道也要一样粗啊」）。
 *     取证见 `NanochipPipeRenderer.PIPE_CORE_MIN` 的长注释（类+方法+字节码行）。
 *   ★ 管臂横截面 = PIPE_ARM_MIN/MAX（**5..11 = 6 像素**，与管芯**相等**）——
 *     旧版本这里是 6..10（4 像素、比芯细），那是移植版自己发明的口径，NH 没有，已废弃。
 *   管臂沿轴仍从**芯的外表面**伸到 16−ARM_END_INSET（0.002）—— 不是从芯中心出发（那会共面打架）。
 *   ★ 物品图标 = **一根沿 Z 轴的直细管**（芯 + 朝北/朝南两根管臂），**不是**六向管臂 ——
 *     玩家 2026-09-15 实测反馈"六向版看起来像一个方块伸出 6 个面"，故图标只留一轴；
 *     世界里的管臂由 NanochipPipeRenderer 按连接位掩码现算，与这个 json 无关。
 *   管芯所有面（含已连接方向）→ 5,5..11,11；朝上/北的臂长面 → 5,0..11,5；朝下/南 → 5,11..11,16；
 *   朝西 → 0,5..5,11；朝东 → 11,5..16,11；所有臂端面 → 5,5..11,11
 *   第二层（叠加条 #bar，tintindex 3）整体外扩 0.001，与渲染器的 OVERLAY_INFLATE 一致。
 */
'use strict';
const fs = require('fs');
const path = require('path');

const CORE_MIN = 5, CORE_MAX = 11;                       // 管芯截面（= Java PIPE_CORE_MIN/MAX；6 像素）
const ARM_MIN = 5, ARM_MAX = 11;                         // ★ 管臂截面（= Java PIPE_ARM_MIN/MAX；与芯同粗，NH 口径）
const ARM_INSET = 0.002, OVERLAY_INFLATE = 0.001;
const UV_CENTRE = [5, 5, 11, 11];
const BAND_MIN = 5, BAND_MAX = 11;                       // = Java BAND_MIN/BAND_MAX
/** 图标用的固定小段带（只影响图标：图标只有一根短直管，不需要跨方块续接） */
const BAND = {
    up: [BAND_MIN, 0, BAND_MAX, BAND_MIN],               // 5,0 .. 11,5
    down: [BAND_MIN, BAND_MAX, BAND_MAX, 16],            // 5,11 .. 11,16
    west: [0, BAND_MIN, BAND_MIN, BAND_MAX],             // 0,5 .. 5,11
    east: [BAND_MAX, BAND_MIN, 16, BAND_MAX],            // 11,5 .. 16,11
};
const FACES = ['down', 'up', 'north', 'south', 'west', 'east'];
const OPPOSITE = { up: 'down', down: 'up', north: 'south', south: 'north', west: 'east', east: 'west' };

/** 造一组几何体，返回 [{box, uv:{面:uv}}]；inflate = 该层的外扩量（格）
 *
 *  ★ 2026-09-15 第二轮（玩家指示）：**物品图标 = 一根直的细管**，只沿 Z 轴贯穿
 *    （芯 + 朝北/−Z 与朝南/+Z 两根管臂），**不再画六向管臂** ——
 *    六向版在物品栏里看像"一个方块伸出六个面"，玩家明确否决。
 *    世界里的几何与此无关：`NanochipPipeRenderer` 按实时连接位掩码现算（该有几根臂就几根臂）。
 *
 *  ★ 2026-09-16 第三轮 / 2026-09-17 第六轮：管臂横截面用 ARM_MIN/ARM_MAX（**与 @link CORE 相同**），
 *    并且**管芯的 6 个面全都声明**（含朝北/朝南这两个"已连接方向"）——
 *    芯臂同粗之后那两个芯面正好是管臂的内端盖（被臂包住、不可见），声明它们无害；
 *    与 Java `appendLayer` 里"芯的 6 个面全都画"逐条对应。
 *    第四轮曾经把臂改细到 4 像素（参照物是真空仓正脸那个黑方块），第六轮按 NH 改回与芯同粗。
 */
function iconElements(inflate) {
    const lo = CORE_MIN - inflate;
    const hi = CORE_MAX + inflate;
    const armLo = ARM_MIN - inflate;             // ★ 臂的横截面（第六轮起与芯相同：NH 口径）
    const armHi = ARM_MAX + inflate;
    const outer = 16 - (ARM_INSET - inflate);   // 管臂外端（在方块边界里侧一点）
    const inner = 16 - outer;                    // 反向臂的外端（0.002 / 0.001）
    const out = [];
    // 管芯：所有面都取贴图正中那块（未连接方向 = "管口"；已连接方向 = 臂根那圈台阶）
    out.push({ box: [lo, lo, lo, hi, hi, hi], uv: Object.fromEntries(FACES.map((f) => [f, UV_CENTRE])) });
    // 两根管臂：north（−Z）与 south（+Z）；沿轴从**芯的外表面**起算
    const arms = [
        ['north', [armLo, armLo, inner, armHi, armHi, lo], BAND.up],
        ['south', [armLo, armLo, hi, armHi, armHi, outer], BAND.down],
    ];
    for (const [capFace, box, band] of arms) {
        const uv = {};
        for (const f of FACES) {
            if (f === capFace) uv[f] = UV_CENTRE;              // 端面 = 管口
            else if (f === OPPOSITE[capFace]) continue;        // 内端面不声明（与管芯共面）
            else uv[f] = band;                                  // 4 个长面
        }
        out.push({ box, uv });
    }
    return out;
}

function elementOf(box, uv, texture, tint) {
    const faces = {};
    for (const f of FACES) {
        if (!uv[f]) continue;
        faces[f] = { uv: uv[f], texture };
        if (tint !== undefined) faces[f].tintindex = tint;
    }
    return { from: box.slice(0, 3), to: box.slice(3, 6), faces };
}

const TEX_PIPE = 'gtlmodular:block/overlay/vacuum_pipe';
const TEX_BAR = 'gtlmodular:block/overlay/vacuum_pipe_bar';
const elements = [
    ...iconElements(0).map((e) => elementOf(e.box, e.uv, '#pipe')),
    ...iconElements(OVERLAY_INFLATE).map((e) => elementOf(e.box, e.uv, '#bar', 3)),
];

const model = {
    parent: 'block/block',
    render_type: 'minecraft:cutout',
    textures: { particle: TEX_PIPE, pipe: TEX_PIPE, bar: TEX_BAR },
    elements,
};

const JAVA = fs.readFileSync(path.join(__dirname, '../src/main/java/com/gtlmodular/client/renderer/NanochipPipeRenderer.java'), 'utf8');
let bad = 0;
function javaConst(name) {
    const m = new RegExp(name + '\\s*=\\s*([0-9.]+)F?\\s*;').exec(JAVA);
    if (!m) { console.error(`自检失败：Java 里找不到 ${name}`); bad++; return NaN; }
    return Number(m[1]);
}
function fail(m) { console.error('自检失败：' + m); bad++; }

// ---- 自检 1：UV 表必须与 Java 渲染器一致 ----
//   Java 侧 2026-09-16 已改成"沿轴 1:1 + 横向钉十字带"（不再有 UV_BAND_* 固定常量），
//   所以两边共有的、必须逐项一致的是：UV_CENTRE 与十字带 BAND_MIN/BAND_MAX；
//   图标用的固定小段带必须**由这两个带常量推出来**（改 Java 的带 ⇒ 这里必须跟着改，否则报错）。
{
    const m = /UV_CENTRE\s*=\s*\{([^}]*)\}/.exec(JAVA);
    if (!m) fail('Java 里找不到 UV_CENTRE');
    else {
        const got = m[1].split(',').map((s) => Number(s.replace(/[Ff]/g, '').trim()));
        if (String(got) !== String(UV_CENTRE)) fail(`UV_CENTRE Java=${got} 脚本=${UV_CENTRE}`);
    }
    const jBandMin = javaConst('BAND_MIN'), jBandMax = javaConst('BAND_MAX');
    if (jBandMin !== BAND_MIN || jBandMax !== BAND_MAX) fail(`十字带 Java=${jBandMin}..${jBandMax} 脚本=${BAND_MIN}..${BAND_MAX}`);
    for (const [name, v] of Object.entries(BAND)) {
        const uPinned = v[0] === jBandMin && v[2] === jBandMax;
        const vPinned = v[1] === jBandMin && v[3] === jBandMax;
        if (!uPinned && !vPinned) fail(`UV_BAND_${name} = ${v} 没有把横截那一维钉在十字带 ${jBandMin}..${jBandMax}`);
        const pinnedLo = uPinned ? v[1] : v[0], pinnedHi = uPinned ? v[3] : v[2];
        if (!(pinnedHi - pinnedLo === 5 && (pinnedLo === 0 || pinnedHi === 16))) {
            fail(`UV_BAND_${name} = ${v} 的自由维不在贴图半边（0..5 / 11..16）`);
        }
    }
}

// ---- 自检 2：几何常量必须与 Java 一致 ----
for (const [name, v] of [['PIPE_CORE_MIN', CORE_MIN], ['PIPE_CORE_MAX', CORE_MAX],
    ['PIPE_ARM_MIN', ARM_MIN], ['PIPE_ARM_MAX', ARM_MAX],
    ['ARM_END_INSET', ARM_INSET], ['OVERLAY_INFLATE', OVERLAY_INFLATE]]) {
    const got = javaConst(name);
    if (!Number.isNaN(got) && got !== v) fail(`${name} Java=${got} 脚本=${v}`);
}

// ---- 自检 3：所有 uv 必须落在十字带里（不许碰到四角蓝灰底色） ----
for (const el of elements) {
    for (const [f, d] of Object.entries(el.faces)) {
        const [u0, v0, u1, v1] = d.uv;
        const inBand = (u0 >= 5 && u1 <= 11) || (v0 >= 5 && v1 <= 11);
        if (!inBand) fail(`面 ${f} 的 uv ${d.uv} 会取到贴图四角底色`);
    }
}

// ---- 自检 4：★ 不变量「管臂截面 == 芯截面」（2026-09-17 第六轮按 NH 改）----
//   依据（NH 字节码，详见 Java 侧 NanochipPipeRenderer.PIPE_CORE_MIN 的长注释）：
//     MetaPipeEntity.renderInWorld 对管芯与每一根管臂用的都是同一对 min/max
//     （fload 5 = (1 - getThickness())/2、fload 6 = 1 - fload 5），且
//     getThickness() → MTEVacuumConveyorPipe.getCollisionThickness() = 0.375f（6 像素）。
//   ⇒ 旧断言"管臂 < 芯"与旧数值 6..10 都是移植版自己发明的口径，与 NH 不符，已改正。
//   （"管臂要小于仓室正脸黑方块"那条历史约束仍成立，但已降级为附带关系；
//     量贴图像素的硬断言在 `_census/check_pipe_uv.js` 里，这里不重复写数值以免两处手抄漂移。）
{
    const coreSpan = CORE_MAX - CORE_MIN, armSpan = ARM_MAX - ARM_MIN;
    if (armSpan !== coreSpan) fail(`管臂截面 ${armSpan} 与芯截面 ${coreSpan} 不相等（NH：芯臂同粗）`);
    if (ARM_MIN !== CORE_MIN || ARM_MAX !== CORE_MAX) {
        fail(`管臂截面 ${ARM_MIN}..${ARM_MAX} 与芯截面 ${CORE_MIN}..${CORE_MAX} 不是同一组（NH：芯臂同粗）`);
    }
    // NH 数值本身：0.375 格 = 6 像素（MTEVacuumConveyorPipe.getCollisionThickness 的 ldc 0.375f）
    if (coreSpan !== 6) fail(`芯截面 ${coreSpan} 像素 ≠ NH 的 0.375 格 = 6 像素`);
    // Java 侧再算一遍（防止只改了脚本一边）
    const jc0 = javaConst('PIPE_CORE_MIN'), jc1 = javaConst('PIPE_CORE_MAX');
    const ja0 = javaConst('PIPE_ARM_MIN'), ja1 = javaConst('PIPE_ARM_MAX');
    if (!Number.isNaN(jc0 + jc1 + ja0 + ja1)) {
        if (ja0 !== jc0 || ja1 !== jc1) fail(`Java 侧不满足"芯臂同粗"：臂 ${ja0}..${ja1}、芯 ${jc0}..${jc1}`);
        if (jc1 - jc0 !== 6) fail(`Java 侧芯截面 ${jc0}..${jc1} ≠ NH 的 6 像素`);
    }
    // 生成出来的元素盒也要满足（逐元素核对，容差 0.02 像素 = 外扩量量级）
    const TOL = 0.02;
    let arms = 0;
    for (const el of elements) {
        // 沿轴 = 这个盒子唯一一个"伸出芯截面之外"的轴（管芯三轴都在芯截面内 ⇒ 跳过）
        const grown = [0, 1, 2].filter((a) => el.from[a] < CORE_MIN - TOL || el.to[a] > CORE_MAX + TOL);
        if (grown.length === 0) continue;                       // 管芯
        if (grown.length !== 1) { fail(`几何体 ${JSON.stringify(el.from)}..${JSON.stringify(el.to)} 不止一维伸出芯截面`); continue; }
        arms++;
        const axial = grown[0];
        for (const a of [0, 1, 2]) {
            if (a === axial) continue;
            const span = el.to[a] - el.from[a];
            if (Math.abs(span - coreSpan) > TOL) fail(`管臂横截面 ${span} 与芯截面 ${coreSpan} 不相等（NH：芯臂同粗）`);
            if (Math.abs(el.from[a] - CORE_MIN) > TOL || Math.abs(el.to[a] - CORE_MAX) > TOL) {
                fail(`管臂横截面 ${el.from[a]}..${el.to[a]} 没有与芯截面 ${CORE_MIN}..${CORE_MAX} 重合`);
            }
        }
        // 沿轴起点必须在**芯的外表面**上（回归到"从芯中心出发"会差 3 像素 ⇒ 这里会报错）
        const positive = el.to[axial] > CORE_MAX + TOL;
        const inner = positive ? el.from[axial] : el.to[axial];
        const surface = positive ? CORE_MAX : CORE_MIN;
        if (Math.abs(inner - surface) > TOL + OVERLAY_INFLATE) {
            fail(`管臂沿轴起点 ${inner} 不在芯的外表面 ${surface} 上（容差 ${TOL + OVERLAY_INFLATE}）`);
        }
    }
    if (arms !== 4) fail(`管臂几何体数不是 4（两层各 2 根），实际 ${arms}`);
    console.log(`✓ 自检 4：管臂截面 ${armSpan} 像素 == 芯截面 ${coreSpan} 像素（= NH 的 0.375 格，芯臂同粗），${arms} 根管臂逐元素核对通过`);
}

if (bad) { console.error(`✗ 自检失败 ${bad} 项，未写出模型`); process.exit(1); }

const out = path.join(__dirname, '../src/main/resources/assets/gtlmodular/models/block/machine/part/vacuum_pipe.json');
fs.writeFileSync(out, JSON.stringify(model, null, 2) + '\n', 'utf8');
console.log('✓ 自检通过：UV_CENTRE + 十字带 2 项 + 几何常量 6 项与 Java 一致；所有 uv 都在十字带内');
console.log(`✓ 写出 ${out}（${elements.length} 个几何体，${fs.statSync(out).size} 字节）`);
