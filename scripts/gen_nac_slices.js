// 把 _gtnh/_tools/main_structure.json（cls_arr.js 精确重建的原版 MAIN_STRUCTURE，[Z][Y] 且 Y=0 最顶）
// 转成 NanochipComplexPattern.java 里的结构字面量（自底向上的 FactoryBlockPattern 行序）。
//
// 用法: node gen_nac_slices.js
// 输出: E:\大鲸鱼工作区\_gen_nac_slices.java.txt （内容 = NAC_SLICES 声明）
//
// 编码格式（每个剖面一个字符串）：
//   剖面 = 若干 "行号,左填充,内容" 以 ';' 分隔；行号 = 0 是最底层（已上下翻转）
//   内容里只出现结构字符（A B C D E F H I J ~），不含 ';' 与 ','，所以安全。
//   没有出现的行 = 整行空气（原版该层该剖面全是空格）。
//
// ★ 2026-09-16 追加：**图案内 180° 旋转**（修"控制器脸朝背面才成形"）
//   做法：每行内容 reverse()、左填充重算 pad' = 63 − pad − len、片序反转 z → 62 − z。
//   旋转中心 = 图案中心 (31, 31)，所以**控制器那一格从 (31,8,30) 移到 (31,8,32)**
//   （Java 里的 NAC_CTRL_Z 同步改成 32）。
//   这样做的效果（已用玩家存档逐格验证，见施工记录 §50）：
//     外壳在世界上**一格都不用动**，但"能成形的控制器朝向"由 south 变成 north，
//     于是控制器的正脸从"贴着 z=31 那面机壳墙"转成"正对那条 32 格通向外面的走廊" ⇒ 脸朝外。
//   旋转**不是镜像**：手性不变（X 轴镜像问题见 §11，仍未处理）。
const fs = require('fs');
const path = require('path');

const SRC = 'E:/大鲸鱼工作区/_gtnh/_tools/main_structure.json';
const OUT = 'E:/大鲸鱼工作区/_gen_nac_slices.java.txt';

const j = JSON.parse(fs.readFileSync(SRC, 'utf8'));
const rows = j.rows;                 // [Z][Y]，Y=0 最顶
const NZ = rows.length;              // 63
const W = rows[0][0].length;         // 63
const YTOP = 0, YBOT = 48;           // 实际用到的 Y 范围
const H = YBOT - YTOP + 1;           // 49

// ---------- 上下翻转：GTCEu 的 aisle 第 0 行 = 最底层 ----------
// 原版内层下标 Y=0 是【最顶】；翻转后 row = (YBOT - Y)，于是 row=0 对应 Y=48（最底）。
function flippedRowOf(y) { return YBOT - y; }

// ---------- ★ 图案内 180° 旋转（绕图案中心 31,31）----------
//   旋转后的图案 P'(x, row, z) = P(62 - x, row, 62 - z)
const ROT180 = true;
function src(x, row, z) {
  const zz = ROT180 ? (NZ - 1 - z) : z;
  const xx = ROT180 ? (W - 1 - x) : x;
  return rows[zz][YBOT - row][xx];
}

// ---------- 自检 ①：控制器位置（旋转后应在 z=32） ----------
let ctrl = null;
for (let z = 0; z < NZ; z++) for (let row = 0; row < H; row++) for (let x = 0; x < W; x++) {
  if (src(x, row, z) === '~') ctrl = { x, row, z };
}
const CTRL_EXPECT = ROT180 ? { x: 31, row: 8, z: 32 } : { x: 31, row: 8, z: 30 };
console.log(`控制器（翻转${ROT180 ? ' + 图案内 180° 旋转' : ''}后）(x,row,z) =`,
  ctrl.x, ctrl.row, ctrl.z, `（期望 ${CTRL_EXPECT.x},${CTRL_EXPECT.row},${CTRL_EXPECT.z}）`);
if (!ctrl) throw new Error('找不到控制器 ~');
if (ctrl.x !== CTRL_EXPECT.x || ctrl.row !== CTRL_EXPECT.row || ctrl.z !== CTRL_EXPECT.z) {
  throw new Error('控制器坐标与原版 OFFSET 不符');
}

// ---------- 抽取每个剖面的非空行 ----------
const slices = [];
const counts = {};
for (let z = 0; z < NZ; z++) {
  const entries = [];
  for (let r = 0; r < H; r++) {
    let s = '';
    for (let x = 0; x < W; x++) s += src(x, r, z);
    const t = s.trim();
    if (!t.length) continue;
    const pad = s.length - s.trimStart().length;   // ★ 已按旋转后的内容重算
    if (pad + t.length > W) throw new Error(`z=${z} row=${r} 左填充+内容超出宽度`);
    entries.push(`${r},${pad},${t}`);
    for (const c of t) counts[c] = (counts[c] || 0) + 1;
  }
  slices.push(entries.join(';'));
}

// ---------- 自检 ②：往返解码必须与（旋转后的）源数据逐字符相同 ----------
function decode() {
  const grid = [];               // [z][row][x]
  for (let z = 0; z < NZ; z++) {
    const layer = [];
    for (let r = 0; r < H; r++) layer.push(new Array(W).fill(' '));
    if (slices[z].length) {
      for (const e of slices[z].split(';')) {
        const [r, pad, content] = e.split(',');
        for (let k = 0; k < content.length; k++) layer[+r][+pad + k] = content[k];
      }
    }
    grid.push(layer);
  }
  return grid;
}
const grid = decode();
let mismatch = 0, nonSpace = 0, ctrlFound = null;
for (let z = 0; z < NZ; z++) for (let r = 0; r < H; r++) for (let x = 0; x < W; x++) {
  const want = src(x, r, z);
  if (grid[z][r][x] !== want) mismatch++;
  if (want !== ' ') { nonSpace++; if (want === '~') ctrlFound = [x, r, z]; }
}
console.log('往返比对不一致格数 =', mismatch, '（必须为 0）');
console.log('非空方块总数 =', nonSpace, '（原版 8713，旋转不改变总数）');
console.log('解码后控制器 =', JSON.stringify(ctrlFound));
if (mismatch !== 0) throw new Error('往返解码不一致');
if (nonSpace !== 8713) throw new Error('非空方块数不对');
console.log('字符普查 =', JSON.stringify(counts));

// ---------- 自检 ③：★ 旋转的两个不变量 ----------
//   ① 旋转是"纯旋转"：原图案里 (x,z) 的字符必须出现在旋转后图案的 (62−x, 62−z)；
//   ② 12 个基座中心（F 位十字的中心）在旋转后**集合不变** ——
//      这正是 openTowerSpace() 不用改的原因（它是从图案现算的，不依赖任何硬编码坐标）。
function centres(pat) {
  const out = new Set();
  for (let z = 0; z < NZ; z++) for (let r = 0; r < H; r++) for (let x = 0; x < W; x++) {
    if (pat(x, r, z) !== 'F') continue;
    for (const [dx, dz] of [[3, 0], [-3, 0], [0, 3], [0, -3]]) {
      const bx = x - dx, bz = z - dz;
      if (bx < 0 || bz < 0 || bx >= W || bz >= NZ) continue;
      let ok = true;
      for (const [ex, ez] of [[3, 0], [-3, 0], [0, 3], [0, -3]]) {
        const nx = bx + ex, nz = bz + ez;
        ok = ok && nx >= 0 && nz >= 0 && nx < W && nz < NZ && pat(nx, r, nz) === 'F';
      }
      if (ok) out.add(`${bx},${r},${bz}`);
    }
  }
  return [...out].sort();
}
const patOld = (x, r, z) => rows[z][YBOT - r][x];
const cOld = centres(patOld), cNew = centres(src);
console.log('12 个基座中心集合（原图案） =', cOld.join(' | '));
console.log('12 个基座中心集合（旋转后） =', cNew.join(' | '));
if (cOld.length !== 12 || cNew.length !== 12) throw new Error(`基座中心数不是 12（${cOld.length} / ${cNew.length}）`);
if (ROT180 && JSON.stringify(cOld) !== JSON.stringify(cNew)) {
  throw new Error('180° 旋转后基座中心集合变了 —— openTowerSpace() 的"塔身让位"必须重新核对');
}
let rotBad = 0;
for (let z = 0; z < NZ; z++) for (let r = 0; r < H; r++) for (let x = 0; x < W; x++) {
  if (patOld(x, r, z) !== src(W - 1 - x, r, NZ - 1 - z)) rotBad++;
}
console.log('纯旋转比对（不应镜像）不一致格数 =', rotBad);
if (rotBad !== 0) throw new Error('旋转实现有误（疑似镜像）');

// ---------- 输出 Java 字面量 ----------
function emitArray(name, arr, indent) {
  const pad = ' '.repeat(indent);
  const lines = [`${pad}private static final String[] ${name} = {`];
  for (let i = 0; i < arr.length; i++) {
    const s = arr[i];
    if (!s.length) { lines.push(`${pad}        "", // ${i}`); continue; }
    // 按 ';' 边界折行，避免单行过长（注意：折行处必须保留 ';' 分隔符）
    const parts = [];
    let cur = '';
    for (const e of s.split(';')) {
      const piece = cur.length ? cur + ';' + e : e;
      if (piece.length > 110 && cur.length) { parts.push(cur + ';'); cur = e; }
      else { cur = piece; }
    }
    if (cur.length) parts.push(cur);
    // 折行不能改变语义：拼回去必须与原串逐字符相同
    if (parts.join('') !== s) {
      throw new Error(`第 ${i} 片折行后语义改变:\n${parts.join('')}\n${s}`);
    }
    const body = parts.map((p, k) => (k === 0 ? '"' + p + '"' : '+ "' + p + '"')).join('\n' + pad + '        ');
    for (const b of body.split('\n')) {
      const q = (b.match(/"/g) || []).length;
      if (q !== 2) throw new Error(`第 ${i} 片某行引号数不是 2（${q}）: ${b}`);
    }
    lines.push(`${pad}        ${body},// z=${i}`);
  }
  lines.push(`${pad}};`);
  return lines.join('\n');
}

const head = `// 【自动生成 · 不要手改】由 scripts/gen_nac_slices.js 从
// _gtnh/_tools/main_structure.json（GTNH 原版 AssemblyComplexStructureString.MAIN_STRUCTURE
// 的逐字节重建）生成。
//
// 轴序纪律（见 _研究_NH_NAC_D_结构与GUI.md §1.2 / §6.2(2)）：
//   原版：MAIN_STRUCTURE[Z][Y].charAt(X)，内层下标 Y=0 是【最顶】。
//   本模组：FactoryBlockPattern 的 aisle 第 0 行 = 【最底层】。
//   ⇒ 生成时整体上下翻转：row = 48 - Y。下面每一行的 "行号" 就是这个翻转过后的 row。
//
// ★ 图案内 180° 旋转（2026-09-16 加入，见施工记录 §50）：
//   P'(x, row, z) = P(62 - x, row, 62 - z) —— 每行内容 reverse + 片序反转，
//   控制器因此从 (x=31, row=8, z=30) 移到 **(x=31, row=8, z=32)**。
//   目的：让"能成形的控制器朝向"从 south 变成 north，使控制器正脸朝向结构外那条 32 格走廊。
//   这是**纯旋转**（不是镜像）：手性不变，X 轴镜像问题（§11）仍未处理。
//
// 行内编码：'行号,左填充,内容'（内容已去掉左右空白；未列出的行 = 整行空气）。
// 原版字符：A 防火墙投影 / B 网格接口 / C 强化 / D 硅岩框架 / E 复合体玻璃 /
//          F 模块位 / H 仓位 / I 可忽略位 / J 计算矩阵 / ~ 控制器 / 空格 空气
`;

const out = [
  head,
  emitArray('NAC_SLICES', slices, 4),
  '',
].join('\n');

fs.writeFileSync(OUT, out, 'utf8');
console.log('已写出', OUT, fs.statSync(OUT).size, 'bytes');
console.log('剖面数 =', slices.length);
