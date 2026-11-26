# UIS PTIT HCM – API thời khóa biểu (đã bắt + verify 25/11/2026)

Base: `https://uis.ptithcm.edu.vn` (SPA Angular, hash route `#/tkb-tuan`, `#/tkb-hocky`)

## Auth
- `POST /api/auth/login` — `Content-Type: application/x-www-form-urlencoded`
  - Đăng nhập: `grant_type=password&username=<MSSV>&password=<pass>&uri=https://uis.ptithcm.edu.vn/#/home&vaitro=&mgr=1`
  - Làm mới: `grant_type=refresh_token&refresh_token=<rt>` → trả access_token mới (đã test OK)
  - Trả: `access_token` (JWE), `expires_in: 28799` (~8h), `refresh_token`, `name`, `userName`, `roles: SINHVIEN`...
- Mọi API khác: header `Authorization: Bearer <access_token>` + `Content-Type: application/json`.
  **KHÔNG cần cookie** (ASP.NET_SessionId/xsrf-*), đã test `credentials: omit` và Node fetch trần.
- ⚠️ Thiếu/sai token: HTTP vẫn **200** nhưng body `{"result":false,"code":401,"message":"notallowed-"}` → phải check `result`/`code` trong body, không check HTTP status.

## 1. Danh sách học kỳ
`POST /api/sch/w-locdshockytkbuser`
```json
{"filter":{"is_tieng_anh":null},"additional":{"paging":{"limit":100,"page":1},"ordering":[{"name":"hoc_ky","order_type":1}]}}
```
→ `data.hoc_ky_theo_ngay_hien_tai` (vd 20261), `data.ds_hoc_ky[]`: `hoc_ky`, `ten_hoc_ky`, `ngay_bat_dau_hk`, `ngay_ket_thuc_hk` (dd/MM/yyyy).

## 2. TKB dạng tuần (API chính cho app)
`POST /api/sch/w-locdstkbtuanusertheohocky`
```json
{"filter":{"hoc_ky":20261,"ten_hoc_ky":""},"additional":{"paging":{"limit":100,"page":1},"ordering":[{"name":null,"order_type":null}]}}
```
→ `data`:
- `ds_tiet_trong_ngay[]`: `tiet`, `gio_bat_dau`, `gio_ket_thuc`, `so_phut` (14 tiết, 50 phút; sáng 6 / chiều 4 / tối 4)
- `ds_tuan_tkb[]`: `tuan_hoc_ky`, `tuan_tuyet_doi`, `thong_tin_tuan`, `ngay_bat_dau`, `ngay_ket_thuc`, `ds_thoi_khoa_bieu[]`
- mỗi buổi: `ngay_hoc` (ISO), `thu_kieu_so` (2=T2…8=CN), `tiet_bat_dau`, `so_tiet`, `ma_mon`, `ten_mon`, `ten_mon_eg`, `so_tin_chi`, `ma_nhom`, `ma_to_th`, `ma_lop`, `ma_phong`, `ma_co_so`, `ten_giang_vien`, `is_day_bu`, `is_nghi_day`, `id_tkb`, `id_to_hoc`
- `ds_lich_hoan_thi`, `thong_bao`, `loai_thoi_gian`

## 3. TKB dạng học kỳ (theo nhóm/tổ)
`POST /api/sch/w-locdstkbhockytheodoituong`
```json
{"hoc_ky":20261,"loai_doi_tuong":1,"id_du_lieu":null}
```
→ `data.ds_nhom_to[]`: `ma_mon`, `ten_mon`, `so_tc`, `nhom_to`, `lop`, `thu`, `tbd`, `so_tiet`, `phong`, `gv`, `tkb` ("dd/MM/yy đến dd/MM/yy"), `sl_dk`...

## Phụ
- `POST /api/sch/w-locdsdoituongthoikhoabieu` → loại TKB: 1 cá nhân, 2 lớp SV, 3 lớp, 4 môn học, 6 khoa QLSV
- `POST /api/sch/w-locdsdoituongthoikhoabieuhocky` (bản cho trang học kỳ)
- `GET /api/hsba/w-gettimeserver` (giờ server)

Script kiểm chứng: `probe_tkb.js` (đọc `UIS_USER`/`UIS_PASS` từ env).
