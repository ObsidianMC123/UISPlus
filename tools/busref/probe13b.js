const fs = require('fs');
const H = { headers: { 'User-Agent': 'UISPlus/1.3', Accept: 'application/json' } };
const get = async p => { const t = Date.now(); const r = await fetch('https://apicms.ebms.vn/' + p, H); const s = await r.text(); return { st: r.status, ms: Date.now() - t, s }; };
(async () => {
  let r = await get('businfo/getroutesforcombo');
  const combo = JSON.parse(r.s);
  console.log('combo', combo.length, JSON.stringify(combo.filter(x => /\[(162|76|19)\]/.test(x.lable))));
  r = await get('businfo/getstopsinbounds/106.3/10.3/107.1/11.2');
  console.log('city bounds', r.st, r.ms + 'ms', r.s.length + 'B', r.st == 200 ? JSON.parse(r.s).length + ' stops' : r.s.slice(0, 200));
  if (r.st == 200) fs.writeFileSync(__dirname + '/samples/allstops.json', r.s);
  r = await get('businfo/getstopsbyvar/362/1');
  const st = JSON.parse(r.s); const last = st[st.length - 1], mid = st[Math.floor(st.length / 2)];
  console.log('var1 stops', st.length, 'last', last.StopId, last.Name, 'mid', mid.StopId);
  for (const s of [last, mid]) {
    r = await get('prediction/predictbystopid/' + s.StopId);
    const p = JSON.parse(r.s);
    console.log('pred', s.StopId, r.ms + 'ms', JSON.stringify(p.map(x => ({ r: x.r, rNo: x.rNo, v: x.v, vN: x.vN, n: (x.arrs || []).length, d: (x.arrs || []).map(a => Math.round(a.d)) }))));
  }
  r = await get('businfo/getroutebyid/362');
  console.log('routebyid keys', Object.keys(JSON.parse(r.s)).join(','));
  r = await get('businfo/getpathsbyvar/362/1');
  const pa = JSON.parse(r.s); console.log('path keys', Object.keys(pa).join(','), pa.lat.length);
})();
