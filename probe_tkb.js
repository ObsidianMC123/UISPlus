// Probe UIS PTIT HCM: login -> danh sach hoc ky -> TKB tuan -> TKB hoc ky -> refresh token
// Chay: set UIS_USER=... & set UIS_PASS=... & node probe_tkb.js
// KHONG hard-code mat khau vao file.
const BASE = 'https://uis.ptithcm.edu.vn';
const USER = process.env.UIS_USER, PASS = process.env.UIS_PASS;
if (!USER || !PASS) { console.error('Thieu UIS_USER / UIS_PASS'); process.exit(2); }

async function login(form) {
  const r = await fetch(BASE + '/api/auth/login', {
    method: 'POST',
    headers: { 'content-type': 'application/x-www-form-urlencoded', 'Referer': BASE + '/' },
    body: new URLSearchParams(form).toString(),
  });
  const t = await r.text();
  let j; try { j = JSON.parse(t); } catch { j = { raw: t.slice(0, 300) }; }
  return { status: r.status, j };
}

async function api(token, path, body) {
  const r = await fetch(BASE + path, {
    method: 'POST',
    headers: { 'Authorization': 'Bearer ' + token, 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  const j = await r.json();
  if (j.result === false || (j.code && j.code !== 200)) throw new Error(path + ' -> ' + JSON.stringify(j).slice(0, 200));
  return j;
}

(async () => {
  const lg = await login({ grant_type: 'password', username: USER, password: PASS, uri: BASE + '/#/home', vaitro: '', mgr: '1' });
  console.log('LOGIN', lg.status, lg.j.result, lg.j.name, 'expires_in=', lg.j.expires_in);
  const tok = lg.j.access_token;

  const hk = await api(tok, '/api/sch/w-locdshockytkbuser', { filter: { is_tieng_anh: null }, additional: { paging: { limit: 100, page: 1 }, ordering: [{ name: 'hoc_ky', order_type: 1 }] } });
  console.log('HOC KY hien tai =', hk.data.hoc_ky_theo_ngay_hien_tai, hk.data.ds_hoc_ky.map(x => x.hoc_ky + ' ' + x.ten_hoc_ky));
  const curHk = hk.data.hoc_ky_theo_ngay_hien_tai;

  const tuan = await api(tok, '/api/sch/w-locdstkbtuanusertheohocky', { filter: { hoc_ky: curHk, ten_hoc_ky: '' }, additional: { paging: { limit: 100, page: 1 }, ordering: [{ name: null, order_type: null }] } });
  const weeks = tuan.data.ds_tuan_tkb;
  const n = weeks.reduce((s, w) => s + w.ds_thoi_khoa_bieu.length, 0);
  console.log('TKB TUAN: tuan=', weeks.length, 'buoi=', n, 'tiet/ngay=', tuan.data.ds_tiet_trong_ngay.length);
  const today = new Date();
  const wk = weeks.find(w => { const [d1, m1, y1] = w.ngay_bat_dau.split('/'); const [d2, m2, y2] = w.ngay_ket_thuc.split('/'); return today >= new Date(y1, m1 - 1, d1) && today <= new Date(y2, m2 - 1, d2, 23, 59); });
  if (wk) { console.log('Tuan nay:', wk.thong_tin_tuan); for (const b of wk.ds_thoi_khoa_bieu) console.log('  ', b.ngay_hoc.slice(0, 10), 'thu', b.thu_kieu_so, 'tiet', b.tiet_bat_dau + '+' + b.so_tiet, b.ma_mon, b.ten_mon, '|', b.ma_phong, '|', b.ten_giang_vien); }

  const hkv = await api(tok, '/api/sch/w-locdstkbhockytheodoituong', { hoc_ky: curHk, loai_doi_tuong: 1, id_du_lieu: null });
  console.log('TKB HOC KY: nhom_to=', hkv.data.ds_nhom_to.length);
  for (const x of hkv.data.ds_nhom_to) console.log('  ', x.ma_mon, x.ten_mon, 'nhom', x.nhom_to, 'TC', x.so_tc, '|', x.tkb);

  const rf = await login({ grant_type: 'refresh_token', refresh_token: lg.j.refresh_token });
  console.log('REFRESH', rf.status, rf.j.result, rf.j.access_token ? 'co access_token moi' : JSON.stringify(rf.j).slice(0, 200));
})().catch(e => { console.error('LOI', e.message); process.exit(1); });
