// Thu nguon tim dia diem (Photon / Nominatim) cho tinh nang tim dia diem trong tab Xe buyt.
const UA = 'UISPlus/1.5 (vn.uisplus.app; geo probe)';
const qs = process.argv.slice(2);
const queries = qs.length ? qs : ['cho ben thanh', 'Chợ Bến Thành', 'vincom thu duc', 'đại học bách khoa', 'landmark 81', 'sân bay tân sơn nhất'];
async function j(url) {
  const t = Date.now();
  const r = await fetch(url, { headers: { 'User-Agent': UA, 'Accept-Language': 'vi' } });
  const txt = await r.text();
  return { status: r.status, ms: Date.now() - t, body: txt };
}
(async () => {
  for (const q of queries) {
    const p = await j('https://photon.komoot.io/api/?limit=6&lat=10.8481&lon=106.7871&bbox=106.3,10.3,107.1,11.2&q=' + encodeURIComponent(q));
    let names = [];
    try { names = JSON.parse(p.body).features.map(f => (f.properties.name || '') + ' | ' + [f.properties.street, f.properties.district, f.properties.city].filter(Boolean).join(', ') + ' | ' + f.properties.osm_key + '=' + f.properties.osm_value); } catch (e) { names = [p.body.slice(0, 200)]; }
    console.log(`PHOTON "${q}" HTTP ${p.status} ${p.ms}ms`); names.forEach(n => console.log('   ' + n));
    await new Promise(r => setTimeout(r, 1100));
    const n = await j('https://nominatim.openstreetmap.org/search?format=jsonv2&limit=6&countrycodes=vn&accept-language=vi&viewbox=106.3,11.2,107.1,10.3&bounded=1&q=' + encodeURIComponent(q));
    try { names = JSON.parse(n.body).map(x => x.display_name.slice(0, 110)); } catch (e) { names = [n.body.slice(0, 200)]; }
    console.log(`NOMINATIM "${q}" HTTP ${n.status} ${n.ms}ms`); names.forEach(x => console.log('   ' + x));
    await new Promise(r => setTimeout(r, 1100));
  }
  const rv = await j('https://nominatim.openstreetmap.org/reverse?format=jsonv2&accept-language=vi&zoom=18&lat=10.848164&lon=106.787147');
  console.log('REVERSE HTTP', rv.status, rv.ms + 'ms', (JSON.parse(rv.body).display_name || rv.body).slice(0, 160));
  console.log('[DONE-MARKER]');
})().catch(e => { console.log('ERR', e); console.log('[DONE-MARKER]'); });
