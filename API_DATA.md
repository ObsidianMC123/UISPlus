# UIS PTIT HCM – Bản đồ API dữ liệu cho app (quét 25/11/2026, tài khoản SV thật, đã ẩn MSSV)

Chung: `POST https://uis.ptithcm.edu.vn/api/...`, header `Authorization: Bearer <access_token>`, `Content-Type: application/json`.
Lỗi auth vẫn HTTP 200 → check `result` / `code` trong body. Login/refresh xem API_TKB.md.
`P(n)` = `"additional":{"paging":{"limit":n,"page":1},"ordering":[{"name":null,"order_type":null}]}`

| # | Dữ liệu | Endpoint | Body | Trường chính | Trạng thái 25/11 |
|---|---|---|---|---|---|
| 1 | Hồ sơ SV | `dkmh/w-locsinhvieninfo` | `""` | 102 trường: ma_sv, ten_day_du, lop, nganh, khoa, nien_khoa, ho_ten_cvht, email_cvht, dien_thoai_cvht, hien_dien_sv, ds_menu_cam_xem... (có cả CCCD, địa chỉ → KHÔNG cache) | OK |
| 2 | DS học kỳ TKB | `sch/w-locdshockytkbuser` | `{filter:{is_tieng_anh:null}, P(100)}` | hoc_ky_theo_ngay_hien_tai, ds_hoc_ky[] | OK, 2 HK |
| 3 | TKB tuần | `sch/w-locdstkbtuanusertheohocky` | `{filter:{hoc_ky,ten_hoc_ky:""}, P(100)}` | ds_tiet_trong_ngay (14 tiết), ds_tuan_tkb[].ds_thoi_khoa_bieu[]: ngay_hoc, thu_kieu_so, tiet_bat_dau, so_tiet, ma_mon, ten_mon, ma_phong, ten_giang_vien, is_nghi_day, is_day_bu | OK, 25 tuần / 63 buổi |
| 4 | TKB học kỳ | `sch/w-locdstkbhockytheodoituong` | `{hoc_ky, loai_doi_tuong:1, id_du_lieu:null}` | ds_nhom_to[] (18 dòng) | OK |
| 5 | Điểm | `srm/w-locdsdiemsinhvien?hien_thi_mon_theo_hkdk=false` | `{}` | ds_diem_hocky[]: dtb_hk_he10/he4, dtb_tich_luy_he_10/4, so_tin_chi_dat_*, xep_loai, canh_cao_hoc_tap, ds_diem_mon_hoc[]: ma_mon, ten_mon, so_tin_chi, diem_giua_ky, diem_thi, diem_tk, diem_tk_so, diem_tk_chu, ket_qua, ds_diem_thanh_phan | 1 HK, 7 môn, CHƯA có điểm (ket_qua=-1) |
| 6 | Lịch thi | `epm/w-locdslichthisvtheohocky` | `{filter:{hoc_ky, is_giua_ky:false|true}, P(100)}` | ds_lich_thi (rỗng nên chưa thấy field con), ds_lich_hoan_thi | 0 lịch (cả cuối kỳ & giữa kỳ) → cần quét lại khi trường xếp lịch |
| 6b | HK có lịch thi | `report/w-locdshockylichthisinhvien` | `{filter:{is_tieng_anh:null}, P(100)}` | | |
| 7 | Học phí | `rms/w-locdstonghophocphisv` | `{}` | ds_hoc_phi_hoc_ky[] (5 dòng): nhhk, ten_nhom_ct, hoc_phi, mien_giam, phai_thu, da_thu, con_no, don_gia; ds_hoc_bong_mg | OK |
| 8 | Hóa đơn điện tử | `rms/w-locdshoadondientu` | `{filter:{hoc_ky:0,tim_kiem:""}, paging 10, ordering hoc_ky/so_hoa_don}` | so_hoa_don, tien_phieu_thu, ngay_thu_chi, code_hoa_don, ghi_chu (nhà cung cấp: MISA) | OK, 5 hóa đơn |
| 9 | CTĐT | `sch/w-locdsctdtsinhvien` | `{filter:{loai_chuong_trinh_dao_tao:1}, P(500)}` | ds_CTDT_hocky[].ds_CTDT_mon_hoc[]: ma_mon, ten_mon, so_tin_chi, mon_bat_buoc, ly_thuyet, thuc_hanh, mon_da_hoc, mon_da_dat | Mới trả 2 HK (4+1 môn) – CTĐT đang công bố dần |
| 10 | Môn tiên quyết | `rms/w-locdsmontienquyet` | `{loai_tien_quyet:1, P(40)}` | ma_mon_dang_ky ↔ ma_mon_yeu_cau | OK, 5 cặp |
| 11 | Kết quả ĐKMH | `dkmh/w-locdskqdkmhsinhvien` | `{is_CVHT:false,is_Clear:false}` | ds_kqdkmh[]: ngay_dang_ky, to_hoc{ma_mon, nhom_to, tkb, sl_dk...} | OK, 7 môn |
| 12 | Thông báo | `web/w-locdsthongbao` | `{filter:{id:null,is_noi_dung:true,is_web:true}, paging 100, ordering ngay_gui desc}` | ds_thong_bao[], notification | 0 thông báo |
| 13 | Giờ server | `GET hsba/w-gettimeserver` | | | |

KHÔNG đụng (ghi dữ liệu / tiền): đăng ký/hủy môn, đóng học phí, gửi ý kiến, cập nhật thường trú, `w-luu*`.
Có trong bundle nhưng chưa thấy menu cho SV: `w-locthongtintrangthaidiemdanh` (điểm danh QR), `w-locthongtinhocbongsinhvien`, `w-locthongtinbhytsinhvien`.
`scan_shapes.json` = bản chụp cấu trúc response, CÓ dữ liệu cá nhân → không commit lên git.
