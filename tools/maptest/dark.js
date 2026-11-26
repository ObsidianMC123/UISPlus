// Prototype "dark remap" cho tile OSM - cung thuat toan voi DarkTiles.kt
// node dark.js out.png tile1.png tile2.png ...
const fs = require('fs'), zlib = require('zlib');

function decodePng(buf) {
  let p = 8, w, h, bd, ct, pal = null, trns = null, idat = [];
  while (p < buf.length) {
    const len = buf.readUInt32BE(p), type = buf.toString('ascii', p + 4, p + 8), data = buf.slice(p + 8, p + 8 + len);
    if (type === 'IHDR') { w = data.readUInt32BE(0); h = data.readUInt32BE(4); bd = data[8]; ct = data[9]; }
    else if (type === 'PLTE') pal = data;
    else if (type === 'tRNS') trns = data;
    else if (type === 'IDAT') idat.push(data);
    p += 12 + len;
  }
  if (bd !== 8) throw new Error('bitdepth ' + bd);
  const bpp = { 0: 1, 2: 3, 3: 1, 4: 2, 6: 4 }[ct];
  const raw = zlib.inflateSync(Buffer.concat(idat));
  const stride = w * bpp, out = Buffer.alloc(w * h * 4), prev = Buffer.alloc(stride), cur = Buffer.alloc(stride);
  let q = 0;
  for (let y = 0; y < h; y++) {
    const f = raw[q++];
    for (let x = 0; x < stride; x++) {
      const a = x >= bpp ? cur[x - bpp] : 0, b = prev[x], c = x >= bpp ? prev[x - bpp] : 0;
      let v = raw[q++];
      if (f === 1) v += a; else if (f === 2) v += b; else if (f === 3) v += (a + b) >> 1;
      else if (f === 4) { const pp = a + b - c, pa = Math.abs(pp - a), pb = Math.abs(pp - b), pc = Math.abs(pp - c); v += (pa <= pb && pa <= pc) ? a : (pb <= pc ? b : c); }
      cur[x] = v & 255;
    }
    for (let x = 0; x < w; x++) {
      const o = (y * w + x) * 4; let r, g, bb, al = 255;
      if (ct === 3) { const i = cur[x]; r = pal[i * 3]; g = pal[i * 3 + 1]; bb = pal[i * 3 + 2]; if (trns && i < trns.length) al = trns[i]; }
      else if (ct === 2 || ct === 6) { r = cur[x * bpp]; g = cur[x * bpp + 1]; bb = cur[x * bpp + 2]; if (ct === 6) al = cur[x * 4 + 3]; }
      else { r = g = bb = cur[x * bpp]; if (ct === 4) al = cur[x * 2 + 1]; }
      out[o] = r; out[o + 1] = g; out[o + 2] = bb; out[o + 3] = al;
    }
    cur.copy(prev);
  }
  return { w, h, px: out };
}

const crcT = new Int32Array(256).map((_, n) => { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; return c; });
function crc(b) { let c = -1; for (const x of b) c = crcT[(c ^ x) & 255] ^ (c >>> 8); return (c ^ -1) >>> 0; }
function chunk(t, d) { const l = Buffer.alloc(4); l.writeUInt32BE(d.length); const td = Buffer.concat([Buffer.from(t), d]); const c = Buffer.alloc(4); c.writeUInt32BE(crc(td)); return Buffer.concat([l, td, c]); }
function encodePng(w, h, px) {
  const raw = Buffer.alloc((w * 4 + 1) * h);
  for (let y = 0; y < h; y++) { raw[y * (w * 4 + 1)] = 0; px.copy(raw, y * (w * 4 + 1) + 1, y * w * 4, (y + 1) * w * 4); }
  const ih = Buffer.alloc(13); ih.writeUInt32BE(w, 0); ih.writeUInt32BE(h, 4); ih[8] = 8; ih[9] = 6;
  return Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk('IHDR', ih), chunk('IDAT', zlib.deflateSync(raw)), chunk('IEND', Buffer.alloc(0))]);
}

// ---- thuat toan (giong Kotlin) ----
const K = +(process.env.K || 0.90), L0 = +(process.env.L0 || 0.86), L1 = +(process.env.L1 || 0.115), L2 = +(process.env.L2 || 0.25), SAT = +(process.env.SAT || 0.7), PL = +(process.env.PL || 0.72), PS = +(process.env.PS || 0.2), TB = +(process.env.TB || 7);
function hue2(p, q, t) { if (t < 0) t += 1; if (t > 1) t -= 1; if (t < 1 / 6) return p + (q - p) * 6 * t; if (t < 1 / 2) return q; if (t < 2 / 3) return p + (q - p) * (2 / 3 - t) * 6; return p; }
function darkPixel(r, g, b) {
  r /= 255; g /= 255; b /= 255;
  const mx = Math.max(r, g, b), mn = Math.min(r, g, b), l = (mx + mn) / 2, d = mx - mn;
  let h = 0, s = 0;
  if (d > 1e-6) {
    s = d / (1 - Math.abs(2 * l - 1));
    if (mx === r) h = ((g - b) / d + (g < b ? 6 : 0)) / 6; else if (mx === g) h = ((b - r) / d + 2) / 6; else h = ((r - g) / d + 4) / 6;
  }
  const l2 = l <= K ? L0 - (L0 - L1) * (l / K) : L1 + (l - K) / (1 - K) * (L2 - L1);
  // mau pastel sang (nen dat be, khu truong vang nhat...) -> giam bao hoa manh de khong bi "ua vang"
  const pastel = l > PL ? Math.min(1, (l - PL) / (1 - PL)) : 0;
  const s2 = Math.min(1, s * SAT * (1 - pastel * (1 - PS)));
  let R, G, B;
  if (s2 < 1e-6) R = G = B = l2; else {
    const q = l2 < 0.5 ? l2 * (1 + s2) : l2 + s2 - l2 * s2, p = 2 * l2 - q;
    R = hue2(p, q, h + 1 / 3); G = hue2(p, q, h); B = hue2(p, q, h - 1 / 3);
  }
  // ngam xanh lanh nhe cho hop tong toi cua app
  const cl = v => Math.max(0, Math.min(255, Math.round(v)));
  return [cl(R * 255 - TB * 0.5), cl(G * 255), cl(B * 255 + TB)];
}

const [out, ...ins] = process.argv.slice(2);
const tiles = ins.map(f => decodePng(fs.readFileSync(f)));
const W = tiles.reduce((a, t) => a + t.w, 0), H = Math.max(...tiles.map(t => t.h));
const all = Buffer.alloc(W * H * 2 * 4); // hang tren: goc, hang duoi: dark
let ox = 0;
for (const t of tiles) {
  for (let y = 0; y < t.h; y++) for (let x = 0; x < t.w; x++) {
    const i = (y * t.w + x) * 4, o1 = (y * W + ox + x) * 4, o2 = ((y + H) * W + ox + x) * 4;
    all[o1] = t.px[i]; all[o1 + 1] = t.px[i + 1]; all[o1 + 2] = t.px[i + 2]; all[o1 + 3] = 255;
    const [R, G, B] = darkPixel(t.px[i], t.px[i + 1], t.px[i + 2]);
    all[o2] = R; all[o2 + 1] = G; all[o2 + 2] = B; all[o2 + 3] = 255;
  }
  ox += t.w;
}
fs.writeFileSync(out, encodePng(W, H * 2, all));
console.log('OK', W, H * 2, out);
