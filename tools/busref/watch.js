// Mo phong logic BusTracker: node watch.js <so vong> -> in ETA va su kien "sap toi"/"vua qua"
const H = { 'User-Agent': 'UISPlus/1.2 (Android; vn.uisplus.app)', Accept: 'application/json', 'Cache-Control': 'no-cache' };
const STOPS = [3713, 3746], WANT = new Set(['74', '162', '76']);
const seen = new Map();
const sleep = ms => new Promise(r => setTimeout(r, ms));
(async () => {
  const n = +process.argv[2] || 10;
  for (let i = 0; i < n; i++) {
    const now = Date.now(), cur = new Map(), ok = new Set(), lines = [];
    for (const s of STOPS) {
      try {
        const a = await (await fetch('https://apicms.ebms.vn/prediction/predictbystopid/' + s, { headers: H })).json();
        ok.add(s);
        for (const p of a) {
          if (!WANT.has(String(p.rNo).trim())) continue;
          for (const x of p.arrs || []) {
            cur.set(`${s}|${p.r}|${p.v}|${x.v}`, { no: p.rNo, dest: p.vN, plate: x.v, eta: x.t, dist: Math.round(x.d), at: now, s });
            lines.push(`${p.rNo}->${p.vN}@${s}: ${x.t}s ${Math.round(x.d)}m ${x.v}`);
          }
        }
      } catch (e) { console.log('ERR', s, e.message); }
    }
    const ev = [];
    for (const [k, o] of seen) if (ok.has(o.s) && !cur.has(k) && now - o.at < 240000 && (o.eta <= 240 || o.dist <= 800)) ev.push(`VUA QUA: ${o.no}->${o.dest} ${o.plate} (lan cuoi ${o.eta}s/${o.dist}m)`);
    for (const k of [...seen.keys()]) if (ok.has(seen.get(k).s)) seen.delete(k);
    for (const [k, v] of cur) seen.set(k, v);
    console.log(new Date(now).toLocaleTimeString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh' }), '|', lines.join(' ; '), ev.length ? '\n   >>> ' + ev.join(' | ') : '');
    if (i < n - 1) await sleep(20000);
  }
})();
