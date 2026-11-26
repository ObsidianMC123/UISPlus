// node probe.js <path>   -> GET https://apicms.ebms.vn/<path>, in JSON rut gon
const H = { 'User-Agent': 'UISPlus/1.2 (Android; vn.uisplus.app)', Accept: 'application/json', 'Cache-Control': 'no-cache' };
const fs = require('fs');
(async () => {
  const p = process.argv[2];
  const t0 = Date.now();
  const r = await fetch('https://apicms.ebms.vn/' + p, { headers: H });
  const txt = await r.text();
  const out = process.argv[3];
  if (out) fs.writeFileSync(out, txt);
  console.log('HTTP', r.status, (Date.now() - t0) + 'ms', txt.length + 'B');
  if (!out) console.log(txt.slice(0, 3000));
})().catch(e => console.log('ERR', e.message));
