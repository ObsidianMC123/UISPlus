# UIS Plus

App Android không chính thức cho sinh viên **PTIT HCM**, xây trên cổng UIS (`uis.ptithcm.edu.vn`),
kèm xe buýt TP.HCM, nhạc/video và hẹn giờ tập trung.

> Không liên kết với Học viện Công nghệ Bưu chính Viễn thông hay YouTube/Google.

## Tính năng (v1.8.0)

- **Lịch học:** Hôm nay, lịch tuần/học kỳ, widget thời khóa biểu, nhắc lịch, đồng bộ nền và báo khi lịch đổi, xuất ICS.
- **Xe buýt TP.HCM:** bản đồ toàn thành phố (6.088 trạm, 182 tuyến), xe sắp tới theo thời gian thực (EBMS),
  danh sách xe đang tới từng trạm, các trạm chính xe sẽ đi qua, tìm địa điểm, gợi ý đi thẳng và đổi 1 lần tuyến,
  thông báo "xe sắp tới", widget trạm ghim.
- **Nhạc:** tìm bài/playlist YouTube, phát nền, hàng chờ, trộn bài, hẹn giờ tắt, nhập playlist YouTube,
  tạo danh sách phát riêng (thêm/bớt/đổi chỗ/đổi tên).
- **Video:** xem video tới 1080p, toàn màn hình, chuyển sang nghe nền, video liên quan.
- **Tập trung:** Pomodoro với thông báo, tùy chọn bật Không làm phiền.
- Giao diện sáng/tối/AMOLED, Material 3, màu đỏ PTIT.

## Cài đặt

Tải `UISPlus-v1.8.0.apk` ở mục [Releases](../../releases). Yêu cầu Android 8.0 (API 26) trở lên.

## Build

```bash
# JDK 17+, Android SDK (compileSdk 37)
./gradlew assembleDebug
./gradlew testDebugUnitTest   # một số test gọi mạng thật (YouTube, EBMS)
```

Không bắt buộc:
- `keystore.properties` (storeFile, storePassword, keyAlias, keyPassword) để ký bản release; không có thì dùng key debug.
- `local.properties`: `carto.key=...` để dùng tile CARTO; để trống thì dùng tile OpenStreetMap.

## Cấu trúc

```
app/src/main/java/vn/uisplus/
  data/    API UIS, đăng nhập, lưu trữ, parser TKB, ICS
  bus/     EBMS, dữ liệu trạm/tuyến, tìm đường, theo dõi xe nền
  music/   NewPipeExtractor, MusicService (Media3), thư viện playlist
  ui/      Compose: Hôm nay, Lịch, Xe buýt, Nhạc, Video, Tập trung, Cá nhân
  widget/  Glance widget TKB + xe buýt
  work/    WorkManager đồng bộ, nhắc lịch
tools/     script dò API, tạo dữ liệu bus, test trên máy thật (adb)
```

Tài liệu API UIS: [API_TKB.md](API_TKB.md), [API_DATA.md](API_DATA.md).

## Credits & giấy phép

Phát hành theo **GNU General Public License v3.0** — xem [LICENSE](LICENSE).
App dùng [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor) (GPL-3.0) của Team NewPipe,
osmdroid, AndroidX Media3, font Inter (OFL 1.1), dữ liệu © OpenStreetMap contributors và API EBMS.
Tab Xe buýt lấy ý tưởng từ [hcmc-bus-tracker](https://github.com/thuanmazda/hcmc-bus-tracker).
Danh sách đầy đủ: [CREDITS.md](CREDITS.md).
