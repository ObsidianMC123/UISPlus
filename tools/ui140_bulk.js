// v1.4 UI: chuan hoa hang loat (chay 1 lan). node tools\ui140_bulk.js
const fs = require('fs');
const path = require('path');
const dir = 'D:\\mcproject\\UISApp\\app\\src\\main\\java\\vn\\uisplus\\ui';
const skip = new Set(['Effects.kt']);
const radius = (n) => (n <= 8 ? n : n <= 13 ? 12 : n <= 26 ? 16 : 24);
const emoji = / ?(?:\u{1F634}|\u{1F44B}|\u{1F389}|\u{1F345}|\u2728)/gu;
let report = [];
for (const f of fs.readdirSync(dir)) {
  if (!f.endsWith('.kt') || skip.has(f)) continue;
  const p = path.join(dir, f);
  let s = fs.readFileSync(p, 'utf8');
  const before = s;
  let nShadow = 0, nRad = 0, nGrad = 0, nEmo = 0;
  s = s.replace(/\.shadow\(/g, () => { nShadow++; return '.elev('; });
  s = s.replace(/RoundedCornerShape\((\d+)\.dp\)/g, (m, n) => {
    const r = radius(+n);
    if (r !== +n) nRad++;
    return `RoundedCornerShape(${r}.dp)`;
  });
  s = s.replace(/Brush\.linearGradient\(listOf\(Ui\.Sky, Ui\.Violet\)\)/g, () => { nGrad++; return 'androidx.compose.ui.graphics.SolidColor(Ui.Brand)'; });
  s = s.replace(emoji, () => { nEmo++; return ''; });
  if (s !== before) {
    fs.writeFileSync(p, s, 'utf8');
    report.push(`${f}: shadow=${nShadow} radius=${nRad} grad=${nGrad} emoji=${nEmo}`);
  }
}
console.log(report.join('\n'));
