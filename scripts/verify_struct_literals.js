// 只读校验：把 NanochipComplexPattern.java 里的 NAC_SLICES / MOD_LAYERS 字面量解回来，
// 与原版 main_structure.json / base_structure.json 逐字符比对。不改任何文件。
// 用法: node scripts/verify_struct_literals.js
const fs = require('fs');

const TARGET = 'E:/大鲸鱼工作区/gtl-modular/src/main/java/com/gtlmodular/common/data/nanochip/NanochipComplexPattern.java';
const MAIN = 'E:/大鲸鱼工作区/_gtnh/_tools/main_structure.json';
const BASE = 'E:/大鲸鱼工作区/_gtnh/_tools/base_structure.json';

const src = fs.readFileSync(TARGET, 'utf8');
let fail = 0;
function ok(cond, msg) { console.log((cond ? '  OK   ' : '  FAIL ') + msg); if (!cond) fail++; }

// ---------- NAC ----------
const m = src.match(/private static final String\[\] NAC_SLICES = \{([\s\S]*?)\n {4}\};/);
if (!m) throw new Error('找不到 NAC_SLICES 块');
const segments = m[1].split(/\/\/ z=\d+/).slice(0, -1);
const slices = segments.map(seg => [...seg.matchAll(/"([^"]*)"/g)].map(x => x[1]).join(''));
console.log('NAC_SLICES 剖面数 =', slices.length);
ok(slices.length === 63, '剖面数 = 63');

const j = JSON.parse(fs.readFileSync(MAIN, 'utf8'));
let bad = 0, n = 0, ctrl = null, entries = 0;
const counts = {};
for (let z = 0; z < 63; z++) {
  const grid = [];
  for (let r = 0; r < 49; r++) grid.push(new Array(63).fill(' '));
  if (slices[z].length) {
    for (const e of slices[z].split(';')) {
      const parts = e.split(',');
      if (parts.length !== 3) throw new Error(`z=${z} 条目格式错: "${e}"`);
      const [r, pad, content] = parts;
      if (+r < 0 || +r > 48) throw new Error(`z=${z} 行号越界: ${r}`);
      if (+pad + content.length > 63) throw new Error(`z=${z} 行超出宽度: pad=${pad} len=${content.length}`);
      entries++;
      for (let k = 0; k < content.length; k++) grid[+r][+pad + k] = content[k];
    }
  }
  for (let r = 0; r < 49; r++) for (let x = 0; x < 63; x++) {
    const want = j.rows[z][48 - r][x];
    if (grid[r][x] !== want) bad++;
    if (want !== ' ') { n++; counts[want] = (counts[want] || 0) + 1; if (want === '~') ctrl = [x, r, z]; }
  }
}
console.log('条目数 =', entries, ' 往返不一致格数 =', bad, ' 实体方块 =', n, ' 控制器(x,row,z) =', JSON.stringify(ctrl));
console.log('字符普查 =', JSON.stringify(counts));
ok(bad === 0, 'NAC 往返逐字符一致');
ok(n === 8713, 'NAC 实体方块数 = 8713');
ok(JSON.stringify(ctrl) === '[31,8,30]', 'NAC 控制器翻转后 = (31,8,30)');
ok(counts['A'] === 32 && counts['B'] === 1099 && counts['C'] === 3956 && counts['D'] === 53
  && counts['E'] === 2226 && counts['F'] === 48 && counts['H'] === 145 && counts['I'] === 432
  && counts['J'] === 721 && counts['~'] === 1, 'NAC 逐字符数量与原版一致');
ok(!counts['G'], 'NAC 没有 G');

// ---------- 模块 ----------
const mb = src.match(/private static final String\[\] MOD_LAYERS = \{([\s\S]*?)\n {4}\};/);
if (!mb) throw new Error('找不到 MOD_LAYERS 块');
const modBlock = mb[1].split('\n').map(l => l.replace(/\/\/.*$/, '')).join('\n');
const modLines = [...modBlock.matchAll(/"([^"]*)"/g)].map(x => x[1]);
console.log('MOD_LAYERS 字面量条数 =', modLines.length);
ok(modLines.length === 21, 'MOD_LAYERS = 7 片 × 3 层 = 21');
const b = JSON.parse(fs.readFileSync(BASE, 'utf8'));
console.log('base_structure dims =', JSON.stringify(b.dims));
let mbad = 0, mctrl = null, mn = 0;
let xRelax = 0;
for (let z = 0; z < 7; z++) for (let r = 0; r < 3; r++) {
  const got = modLines[z * 3 + r];            // r = 0 最底 ⇒ 源下标 Y = 2 - r
  const want = b.rows[z][2 - r];
  if (got.length !== want.length) { mbad++; console.log(`   模块长度不符 z=${z} row=${r}`); continue; }
  for (let k = 0; k < want.length; k++) {
    if (got[k] === want[k]) continue;
    if (got[k] === 'X' && want[k] === ' ') { xRelax++; continue; }  // 允许的空格→any() 放宽
    mbad++;
    console.log(`   模块不符 z=${z} row=${r} col=${k}: 源="${want[k]}" 制="${got[k]}"`);
  }
  for (let k = 0; k < want.length; k++) {
    if (want[k] !== ' ') { mn++; if (want[k] === '~') mctrl = [k, r, z]; }
  }
}
ok(mbad === 0, `模块往返一致（仅允许空格→X 的放宽，共 ${xRelax} 格）`);
ok(JSON.stringify(mctrl) === '[3,2,0]', '模块控制器翻转后 = (3,2,0)（顶层）');
ok(mn === 115, `模块实体方块数 = ${mn}（原版 45+25+45=115）`);

console.log(fail === 0 ? '\n全部通过 ✓' : `\n失败 ${fail} 项 ✗`);
process.exit(fail === 0 ? 0 : 1);
