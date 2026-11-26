// Tao assets/bus/stops.json + routes.json (dang gon) tu API EBMS - chay lai khi muon cap nhat du lieu goi kem APK
const fs = require('fs');
const path = require('path');
const H = { headers: { 'User-Agent': 'UISPlus/1.3', Accept: 'application/json' } };
const out = path.join(__dirname, '..', '..', 'app', 'src', 'main', 'assets', 'bus');
(async () => {
  fs.mkdirSync(out, { recursive: true });
  const stops = await (await fetch('https://apicms.ebms.vn/businfo/getstopsinbounds/106.3/10.3/107.1/11.2', H)).json();
  const seen = new Set();
  const compact = [];
  for (const s of stops) {
    if (seen.has(s.StopId)) continue; seen.add(s.StopId);
    const addr = [s.AddressNo, s.Street].filter(Boolean).join(', ');
    compact.push([s.StopId, s.Code || '', s.Name || '', +(+s.Lat).toFixed(6), +(+s.Lng).toFixed(6), addr, s.Routes || '']);
  }
  fs.writeFileSync(path.join(out, 'stops.json'), JSON.stringify(compact));
  const combo = await (await fetch('https://apicms.ebms.vn/businfo/getroutesforcombo', H)).json();
  const routes = combo.map(c => { const m = /^\[([^\]]+)\]\s*(.*)$/.exec(c.lable || ''); return [+c.value, m ? m[1] : '', m ? m[2] : c.lable]; });
  fs.writeFileSync(path.join(out, 'routes.json'), JSON.stringify(routes));
  console.log('stops', compact.length, fs.statSync(path.join(out, 'stops.json')).size + 'B', 'routes', routes.length, fs.statSync(path.join(out, 'routes.json')).size + 'B');
  console.log('sample', JSON.stringify(compact.find(x => x[0] == 3713)), JSON.stringify(routes.slice(0, 2)));
})();
