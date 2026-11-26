// probe cac endpoint EBMS can cho tab Xe buyt (v1.3)
const fs = require('fs');
const paths = [
  'businfo/getroutesforcombo',
  'businfo/getvarsbyroute/362',
  'businfo/getstopsbyvar/362/1',
  'businfo/getpathsbyvar/362/1',
  'businfo/getstopsinbounds/106.78/10.84/106.80/10.86',
  'businfo/getroutesthroughstop/3713',
  'businfo/getallstops',
  'businfo/getallroutes',
  'businfo/getroutebyid/362',
];
(async () => {
  fs.mkdirSync(__dirname + '/samples', { recursive: true });
  for (const p of paths) {
    const t = Date.now();
    try {
      const r = await fetch('https://apicms.ebms.vn/' + p, { headers: { 'User-Agent': 'UISPlus/1.3', Accept: 'application/json' } });
      const s = await r.text();
      fs.writeFileSync(__dirname + '/samples/' + p.replace(/[\/]/g, '_') + '.json', s);
      console.log('== ' + p + ' HTTP ' + r.status + ' ' + (Date.now() - t) + 'ms ' + s.length + 'B');
      console.log(s.slice(0, 420));
    } catch (e) { console.log('== ' + p + ' ERR ' + e.message); }
  }
})();
