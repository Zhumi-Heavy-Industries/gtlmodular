// 用最新生成的 _gen_nac_slices.java.txt 整体替换 NanochipComplexPattern.java 里的 NAC_SLICES 声明块，
// 并对 Java 字面量做「解回来 == 旋转后的源数据」的往返校验。
// ★ 2026-09-16：源数据口径改成**图案内 180° 旋转后**的图案（P'(x,row,z) = P(62−x, row, 62−z)），
//   控制器因此落在 (31, 8, 32)（Java 里 NAC_CTRL_Z = 32 必须与之同步）。
const fs = require('fs');
const TARGET = 'E:/大鲸鱼工作区/gtl-modular/src/main/java/com/gtlmodular/common/data/nanochip/NanochipComplexPattern.java';
const DATA = 'E:/大鲸鱼工作区/_gen_nac_slices.java.txt';

const data = fs.readFileSync(DATA, 'utf8').replace(/\s+$/, '');
const src = fs.readFileSync(TARGET, 'utf8');
const re = / {4}private static final String\[\] NAC_SLICES = \{[\s\S]*?\n {4}\};/;
if (!re.test(src)) throw new Error('找不到 NAC_SLICES 块');
fs.writeFileSync(TARGET, src.replace(re, data), 'utf8');
console.log('已替换 NAC_SLICES 块');

const out = fs.readFileSync(TARGET, 'utf8');
console.log('总行数', out.split('\n').length, '大小', out.length);
const m = out.match(/private static final String\[\] NAC_SLICES = \{([\s\S]*?)\n    \};/);
const segments = m[1].split(/\/\/ z=\d+/).slice(0, -1);
const slices = segments.map(seg => [...seg.matchAll(/"([^"]*)"/g)]
  .map(x => x[1]).join('').replace(/\s*\+\s*/g, ''));
console.log('解出的剖面数', slices.length);
if (slices.length !== 63) throw new Error('剖面数不是 63');

const j = JSON.parse(fs.readFileSync('E:/大鲸鱼工作区/_gtnh/_tools/main_structure.json', 'utf8'));
// 旋转后的源：P'(x, row, z) = P(62 − x, row, 62 − z)（row 已做上下翻转：row = 48 − Y）
const want = (x, row, z) => j.rows[62 - z][48 - row][62 - x];
let bad = 0, n = 0, ctrl = null, entries = 0;
for (let z = 0; z < 63; z++) {
  const grid = [];
  for (let r = 0; r < 49; r++) grid.push(new Array(63).fill(' '));
  if (slices[z].length) {
    for (const e of slices[z].split(';')) {
      const parts = e.split(',');
      if (parts.length !== 3) throw new Error(`z=${z} 条目格式错: "${e}"`);
      const [r, pad, content] = parts;
      entries++;
      for (let k = 0; k < content.length; k++) grid[+r][+pad + k] = content[k];
    }
  }
  for (let r = 0; r < 49; r++) for (let x = 0; x < 63; x++) {
    const w = want(x, r, z);
    if (grid[r][x] !== w) bad++;
    if (w !== ' ') { n++; if (w === '~') ctrl = [x, r, z]; }
  }
}
console.log('条目数', entries, '往返不一致格数 =', bad, '实体方块 =', n, '控制器(x,row,z) =', JSON.stringify(ctrl));
if (bad !== 0 || n !== 8713 || ctrl.join() !== '31,8,32') throw new Error('Java 字面量与源数据不一致');
// Java 常量必须与图案里的控制器下标一致（防止"改了图案忘了改常量"）
const ctrlConst = /NAC_CTRL_Z = (\d+)/.exec(out);
if (!ctrlConst || Number(ctrlConst[1]) !== ctrl[2]) {
  throw new Error(`NAC_CTRL_Z（${ctrlConst && ctrlConst[1]}）与图案里的控制器剖面下标（${ctrl[2]}）不一致`);
}
console.log(`NAC_CTRL_Z = ${ctrlConst[1]} 与图案一致 ✓`);
console.log('往返校验通过 ✓');
