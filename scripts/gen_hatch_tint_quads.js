#!/usr/bin/env node
/**
 * ★ 真空仓前脸"染色范围"收窄器（2026-09-17 第五轮，玩家投诉"粉色/淡粉色像素也被染色了"）
 *
 * ── 玩家定的规格（"之前说好的那一部分"）──
 * 出处三处，逐字相同：
 *   · `_结构照搬_施工记录.md` §49.1（L2578-2580）：
 *       「染色的时候，**外壳颜色不变**，只是那个**白色的正脸**那个会变，就是**被黑色像素包围到内侧的那一圈**，
 *        **白色像素**会变」「正脸黑框里**只有白色像素**会改变」「外壳不染、黑像素不变、透明不动」
 *   · `NanochipVacuumHatchMachine.java` L226-228（同一句话写在代码里）
 *   · `_census/hatch_tint_diagnosis.md` §跳5（逐像素合成图：能吃染料的只有那圈"纯白像素"）
 *
 * ── 现象与根因 ──
 * 4 个前脸模型 json 把**整张** `#overlay` 面（uv 0,0..16,16 满铺）标了 `tintindex: 2`
 * ⇒ 贴图上**所有**可见像素都被染料乘了一遍，包括黑框内侧那圈里的**粉色 (255,219,224)** 与
 * **淡粉色 (255,232,235)** 像素 —— 玩家看到的"粉色也被染了"就是它。
 * 而约定要染的只有"圈里的**白/浅灰**像素"（输入仓 44 个、输出仓 36 个）。
 *
 * ── 改法（本脚本做的事）──
 * 把吃染料的那一层从"整面"换成**只覆盖约定像素区间的小面**：
 *   · 原 `#overlay` 整面层**去掉 tintindex**（仍画满整面 ⇒ 粉色/黑框/透明照旧，原色）；
 *   · 另外生成 N 个小面（north 面，`tintindex: 2`，z 比整面层更靠外 0.001 ⇒ 不共面、不 z-fighting），
 *     每个小面的 **uv 区间 = 约定像素区间**，且这些小面的并集**恰好等于**约定区域。
 * ⇒ 染色只落在约定区域；粉色/淡粉色像素不在任何吃染料的小面里 ⇒ 保持原色。
 * 贴图像素 ↔ 面坐标的换算用 north 面的标准约定（从正面看：u↔−x、v↔−y），
 * 并且断言"约定区域在 x→15−x、y→15−y 两种镜像下不变"⇒ 该换算的镜像歧义不影响结果。
 *
 * 数值**全部从贴图现算**（不许手抄两份）：`_census/check_pipe_uv.js` 的 Ⅷ 节会独立再算一遍并比对。
 *
 * 用法：node gtl-modular/scripts/gen_hatch_tint_quads.js [--dry]
 *   --dry：只打印诊断（分类计数、小面清单、自检结果），不写盘
 */
'use strict';
const fs = require('fs');
const path = require('path');
const zlib = require('zlib');

const DRY = process.argv.includes('--dry');
const RES = path.resolve(__dirname, '../src/main/resources/assets/gtlmodular');
const TEX_DIR = path.join(RES, 'textures/block/overlay');
const MODEL_DIR = path.join(RES, 'models/block/machine/part');

// ---------- 与"约定区域"有关的判据（唯一一份；check 脚本独立重算，不共用本文件的常量） ----------
const RING_MIN = 180;   // 圈像素的最暗通道下限：把黑框(≤#10)、黑外框(18,18,18)、透明全排除
const PINK_RG = 12;     // r − g ≥ 12 ⇒ 判"粉 / 淡粉"（实测：粉 36、淡粉 23；白/灰 ≤ 0）
const TINT_Z_OUT = -0.003;   // 吃染料小面的外表面 z（整面层是 −0.002 ⇒ 靠外 0.001）
const TINT_Z_IN = -0.001;    // 小面的里表面 z（只声明 north 面，这个值只用于满足 from<to）
const TINT_OVERLAY = 2;      // = NanochipVacuumHatchMachine.TINT_OVERLAY

const KINDS = [
    { name: 'input', idle: 'vacuum_hatch_input.png', active: 'vacuum_hatch_input_active.png', models: ['nanochip_vacuum_input.json', 'nanochip_vacuum_input_active.json'] },
    { name: 'output', idle: 'vacuum_hatch_output.png', active: 'vacuum_hatch_output_active.png', models: ['nanochip_vacuum_output.json', 'nanochip_vacuum_output_active.json'] },
];

let bad = 0;
const fail = (m) => { console.error('✗ 自检失败：' + m); bad++; };

/** 极简 PNG 解码（8bit、非隔行）：{w,h,px}，px[y][x] = [r,g,b,a] */
function decodePng(buf) {
    if (buf.readUInt32BE(0) !== 0x89504e47) throw new Error('不是 PNG');
    let off = 8, w = 0, h = 0, colorType = 0, interlace = 0;
    const idat = [];
    let palette = null, trns = null;
    while (off + 8 <= buf.length) {
        const len = buf.readUInt32BE(off);
        const type = buf.toString('ascii', off + 4, off + 8);
        const data = buf.subarray(off + 8, off + 8 + len);
        if (type === 'IHDR') { w = data.readUInt32BE(0); h = data.readUInt32BE(4); colorType = data[9]; interlace = data[12]; }
        else if (type === 'PLTE') palette = Buffer.from(data);
        else if (type === 'tRNS') trns = Buffer.from(data);
        else if (type === 'IDAT') idat.push(Buffer.from(data));
        else if (type === 'IEND') break;
        off += 12 + len;
    }
    if (interlace) throw new Error('不支持隔行 PNG');
    const raw = zlib.inflateSync(Buffer.concat(idat));
    const channels = { 0: 1, 2: 3, 3: 1, 4: 2, 6: 4 }[colorType];
    if (!channels) throw new Error('不支持的 colorType ' + colorType);
    const stride = w * channels;
    const out = Buffer.alloc(h * stride);
    let p = 0;
    for (let y = 0; y < h; y++) {
        const ft = raw[p++];
        const line = raw.subarray(p, p + stride); p += stride;
        const cur = out.subarray(y * stride, (y + 1) * stride);
        const prev = y > 0 ? out.subarray((y - 1) * stride, y * stride) : Buffer.alloc(stride);
        for (let i = 0; i < stride; i++) {
            const a = i >= channels ? cur[i - channels] : 0, b = prev[i], c = i >= channels ? prev[i - channels] : 0;
            let v = line[i];
            if (ft === 1) v += a; else if (ft === 2) v += b; else if (ft === 3) v += (a + b) >> 1;
            else if (ft === 4) {
                const pa = Math.abs(b - c), pb = Math.abs(a - c), pc = Math.abs(a + b - 2 * c);
                v += (pa <= pb && pa <= pc) ? a : (pb <= pc ? b : c);
            }
            cur[i] = v & 0xff;
        }
    }
    const px = [];
    for (let y = 0; y < h; y++) {
        const row = [];
        for (let x = 0; x < w; x++) {
            let r, g, b, a = 255;
            if (colorType === 6) { const o = y * stride + x * 4; r = out[o]; g = out[o + 1]; b = out[o + 2]; a = out[o + 3]; }
            else if (colorType === 2) { const o = y * stride + x * 3; r = out[o]; g = out[o + 1]; b = out[o + 2]; }
            else if (colorType === 0) { const o = y * stride + x; r = g = b = out[o]; }
            else if (colorType === 4) { const o = y * stride + x * 2; r = g = b = out[o]; a = out[o + 1]; }
            else { const i = out[y * stride + x]; r = palette[i * 3]; g = palette[i * 3 + 1]; b = palette[i * 3 + 2]; if (trns && i < trns.length) a = trns[i]; }
            row.push([r, g, b, a]);
        }
        px.push(row);
    }
    return { w, h, px };
}

const load = (f) => decodePng(fs.readFileSync(path.join(TEX_DIR, f)));

/** 圈像素：不透明 + 最暗通道 ≥ RING_MIN（= 黑框内侧那圈浅色，排除黑框/柔边/黑外框/透明） */
const isRing = (c) => c[3] >= 250 && Math.min(c[0], c[1], c[2]) >= RING_MIN;
/** 粉 / 淡粉：圈像素里"红比绿明显高"的那些 */
const isPink = (c) => isRing(c) && (c[0] - c[1]) >= PINK_RG;

/**
 * 从**空闲层**（静态 16×16）现算约定区域。
 * 返回 { ring, pink, neutral }，每个都是 "x,y" 字符串集合（第一帧 / 16×16）。
 */
function agreedRegion(idleImg) {
    const ring = new Set(), pink = new Set(), neutral = new Set();
    for (let y = 0; y < 16; y++) {
        for (let x = 0; x < 16; x++) {
            const c = idleImg.px[y][x];
            if (!isRing(c)) continue;
            const k = `${x},${y}`;
            ring.add(k);
            (isPink(c) ? pink : neutral).add(k);
        }
    }
    return { ring, pink, neutral };
}

/** 约定区域必须在这两种镜像下不变（⇒ north 面 u/v 取反的歧义不影响最终像素） */
function assertSymmetric(set, label) {
    for (const k of set) {
        const [x, y] = k.split(',').map(Number);
        for (const [nx, ny, how] of [[15 - x, y, 'x→15−x'], [x, 15 - y, 'y→15−y'], [15 - x, 15 - y, '两者']]) {
            if (!set.has(`${nx},${ny}`)) fail(`${label} 在镜像 ${how} 下不对称：(${x},${y}) 的像 (${nx},${ny}) 不在集合里`);
        }
    }
}

/**
 * 把 neutral 像素集拆成"恰好覆盖它"的矩形集合（贪心：每找到未覆盖的最上左像素就向右、向下尽量扩）。
 * 每个矩形 = {x0, y0, x1, y1}（贴图像素，右/下开区间）。
 */
function rectangleCover(neutral) {
    const left = new Set(neutral);
    const rects = [];
    for (let y = 0; y < 16; y++) {
        for (let x = 0; x < 16; x++) {
            if (!left.has(`${x},${y}`)) continue;
            let x1 = x + 1;
            while (x1 < 16 && left.has(`${x1},${y}`)) x1++;
            let y1 = y + 1;
            outer: while (y1 < 16) {
                for (let xx = x; xx < x1; xx++) if (!left.has(`${xx},${y1}`)) break outer;
                y1++;
            }
            for (let yy = y; yy < y1; yy++) for (let xx = x; xx < x1; xx++) left.delete(`${xx},${yy}`);
            rects.push({ x0: x, y0: y, x1, y1 });
        }
    }
    if (left.size) fail(`矩形覆盖没盖完（剩 ${left.size} 个像素）：${[...left].join(' ')}`);
    return rects;
}

/** 贴图像素矩形 → 模型 json 的小面（north 面；从正面看 u↔−x、v↔−y） */
function quadOf(r) {
    return {
        from: [16 - r.x1, 16 - r.y1, TINT_Z_OUT],
        to: [16 - r.x0, 16 - r.y0, TINT_Z_IN],
        faces: {
            north: {
                uv: [r.x0, r.y0, r.x1, r.y1],
                texture: '#overlay',
                cullface: 'north',
                tintindex: TINT_OVERLAY,
            },
        },
    };
}

/** 反算：模型 json 的一个小面覆盖哪些贴图像素（供写盘后自证与 check 脚本对照用） */
function pixelsOfQuad(el) {
    const uv = el.faces.north.uv;
    const out = new Set();
    for (let x = uv[0]; x < uv[2]; x++) for (let y = uv[1]; y < uv[3]; y++) out.add(`${x},${y}`);
    return out;
}

const overlays = {};
for (const kind of KINDS) {
    console.log(`\n===== ${kind.name} =====`);
    const idle = load(kind.idle);
    const active = load(kind.active);
    if (idle.w !== 16 || idle.h !== 16) fail(`${kind.idle} 尺寸 ${idle.w}×${idle.h} ≠ 16×16（换算前提失效）`);
    if (active.w !== 16 || active.h % 16 !== 0) fail(`${kind.active} 尺寸 ${active.w}×${active.h} 异常`);

    const { ring, pink, neutral } = agreedRegion(idle);
    const frames = active.h / 16;
    console.log(`  空闲层 ${kind.idle}：圈像素 ${ring.size} = 粉/淡粉 ${pink.size} + 白/浅灰(约定要染) ${neutral.size}；动画层 ${frames} 帧`);
    if (!neutral.size) fail(`${kind.idle} 算不出"白像素圈"（约定区域为空）`);
    if (!pink.size) fail(`${kind.idle} 算不出粉色像素（贴图或判据变了，请复核约定）`);

    // ① 约定区域在两种镜像下不变 ⇒ north 面 u/v 取反的歧义不影响结果
    assertSymmetric(ring, `圈像素(${kind.name})`);
    assertSymmetric(pink, `粉色像素(${kind.name})`);
    assertSymmetric(neutral, `白像素圈(${kind.name})`);

    // ② 空闲层与动画层必须"位置一致"：粉还是粉、白圈还在原位（否则同一套小面对不上动画层）
    //    （判据与色相无关：`max > 0x40` 把黑框/柔边/黑外框(18,18,18)全排除，剩下的就是那圈浅色）
    const activeRing = (f) => {
        const s = new Set();
        for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
            const c = active.px[f * 16 + y][x];
            if (c[3] >= 250 && Math.max(c[0], c[1], c[2]) > 0x40) s.add(`${x},${y}`);
        }
        return s;
    };
    for (let f = 0; f < frames; f++) {
        const ar = activeRing(f);
        if (ar.size !== ring.size || [...ring].some((k) => !ar.has(k))) {
            fail(`${kind.active} 第 ${f} 帧：浅色圈的位置与空闲层不一致（空闲 ${ring.size} 个 / 该帧 ${ar.size} 个）`
                + ' —— 同一套小面会盖错位置，请复核贴图');
        }
        for (const k of ring) {
            const [x, y] = k.split(',').map(Number);
            const c = active.px[f * 16 + y][x];
            if (c[3] < 250) fail(`${kind.active} 第 ${f} 帧：圈位置 (${x},${y}) 变透明（空闲层不透明）`);
            if (Math.max(c[0], c[1], c[2]) <= 0x40) fail(`${kind.active} 第 ${f} 帧：圈位置 (${x},${y}) 变成黑（空闲层是浅色）`);
        }
        for (const k of pink) {
            const [x, y] = k.split(',').map(Number);
            const c = active.px[f * 16 + y][x];
            if ((c[0] - c[1]) < PINK_RG) fail(`${kind.active} 第 ${f} 帧：粉色位置 (${x},${y}) 不再是粉（r−g=${c[0] - c[1]} < ${PINK_RG}）`);
        }
        for (const k of neutral) {
            const [x, y] = k.split(',').map(Number);
            const c = active.px[f * 16 + y][x];
            if ((c[0] - c[1]) >= PINK_RG) fail(`${kind.active} 第 ${f} 帧：约定要染的位置 (${x},${y}) 竟然是粉色（r−g=${c[0] - c[1]}）`);
        }
    }
    console.log(`  ⇒ 逐帧核对：${frames} 帧里"粉位置仍是粉、约定位置不是粉、圈位置都还在"✓`);

    const rects = rectangleCover(neutral);
    for (const r of rects) {
        if (r.x0 < 0 || r.y0 < 0 || r.x1 > 16 || r.y1 > 16) fail(`小面越界：${JSON.stringify(r)}`);
        for (let x = r.x0; x < r.x1; x++) for (let y = r.y0; y < r.y1; y++) {
            if (pink.has(`${x},${y}`)) fail(`小面 ${JSON.stringify(r)} 覆盖了粉色像素 (${x},${y})`);
            if (!neutral.has(`${x},${y}`)) fail(`小面 ${JSON.stringify(r)} 覆盖了非约定像素 (${x},${y})`);
        }
    }
    console.log(`  ⇒ 约定区域拆成 ${rects.length} 个恰好覆盖的小面：`
        + rects.map((r) => `[${r.x0}..${r.x1})×[${r.y0}..${r.y1})`).join(' '));
    overlays[kind.name] = { rects, neutral, pink, ring };

    // ③ 四个模型 json：整面层去掉 tintindex + 换上新小面
    for (const mf of kind.models) {
        const p = path.join(MODEL_DIR, mf);
        const model = JSON.parse(fs.readFileSync(p, 'utf8'));
        const northFull = (el) => el.faces && el.faces.north && el.faces.north.texture === '#overlay';
        const base = (model.elements || []).find((el) => northFull(el) && (el.faces.north.uv || [0, 0, 16, 16]).join(',') === '0,0,16,16');
        if (!base) { fail(`${mf}：找不到"整面 #overlay 层"（uv 0,0..16,16）`); continue; }
        if (base.faces.north.tintindex !== undefined) { console.log(`   ${mf}：整面层去掉 tintindex ${base.faces.north.tintindex}（改由小面吃染料）`); delete base.faces.north.tintindex; }
        const before = model.elements.length;
        model.elements = model.elements.filter((el) => el === base || !northFull(el));
        if (before !== model.elements.length) console.log(`   ${mf}：清掉旧的 ${before - model.elements.length} 个小面（可重复运行）`);
        // 小面插在整面层之后（渲染顺序无关，只为可读）
        const at = model.elements.indexOf(base) + 1;
        model.elements.splice(at, 0, ...rects.map(quadOf));
        // 写盘前自证：新小面的并集 == 约定区域
        const covered = new Set();
        for (const el of model.elements) {
            if (!northFull(el) || el === base) continue;
            for (const k of pixelsOfQuad(el)) { if (covered.has(k)) fail(`${mf}：小面互相重叠于 (${k})`); covered.add(k); }
        }
        if (covered.size !== neutral.size || [...neutral].some((k) => !covered.has(k))) {
            fail(`${mf}：小面并集（${covered.size} 像素）≠ 约定区域（${neutral.size} 像素）`);
        }
        for (const k of pink) if (covered.has(k)) fail(`${mf}：小面覆盖了粉色像素 (${k})`);
        if (!DRY) fs.writeFileSync(p, JSON.stringify(model, null, 2) + '\n', 'utf8');
        console.log(`   ${mf}：整面层（不吃染料）1 个 + 小面 ${rects.length} 个 ⇒ ${model.elements.length} 个几何体${DRY ? '（--dry 未写盘）' : ' ✓ 已写盘'}`);
    }
}

if (bad) { console.error(`\n✗ 自检失败 ${bad} 项`); process.exit(1); }
console.log('\n✓ 自检通过：约定区域（圈内非粉像素）从贴图现算、逐帧与动画层对齐、镜像对称、'
    + '小面并集恰好等于它且不含任何粉色像素；整面层已退出染色。');
