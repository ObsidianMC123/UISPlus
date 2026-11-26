# Credits & giấy phép bên thứ ba

UIS Plus được phát hành theo **GNU GPL v3.0** (xem [LICENSE](LICENSE)) vì app dùng
NewPipeExtractor (GPL-3.0). Cảm ơn các dự án và nguồn dữ liệu dưới đây.

## Thư viện chính

| Dự án | Dùng cho | Giấy phép |
|---|---|---|
| [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor) — © Team NewPipe | Tìm kiếm, playlist, luồng nhạc/video YouTube (tab Nhạc, Video) | GPL-3.0-or-later |
| [osmdroid](https://github.com/osmdroid/osmdroid) | Bản đồ tab Xe buýt | Apache-2.0 ([licenses/Apache-2.0.txt](licenses/Apache-2.0.txt)) |
| [AndroidX Media3 / ExoPlayer](https://github.com/androidx/media) | Phát nhạc nền, xem video, MediaSession | Apache-2.0 |
| [Jetpack Compose, Glance, WorkManager, AndroidX](https://developer.android.com/jetpack) | Giao diện, widget, đồng bộ nền | Apache-2.0 |
| [OkHttp](https://github.com/square/okhttp) — © Square | Gọi mạng | Apache-2.0 |
| [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization) — © JetBrains | Đọc JSON | Apache-2.0 |
| [desugar_jdk_libs](https://github.com/google/desugar_jdk_libs) | `java.nio` cho Android < 13 | GPL-2.0 with Classpath Exception |
| [Robolectric](https://github.com/robolectric/robolectric), JUnit 4 | Test | MIT / EPL-1.0 |

## Font

- **Inter** — Copyright (c) 2016 The Inter Project Authors (https://github.com/rsms/inter).
  Đóng gói trong `app/src/main/res/font/`, giấy phép SIL Open Font License 1.1
  ([licenses/OFL-Inter.txt](licenses/OFL-Inter.txt)).

## Dữ liệu & dịch vụ

- **Xe buýt TP.HCM:** API EBMS (`apicms.ebms.vn`) – Trung tâm Quản lý Giao thông công cộng TP.HCM.
  Dữ liệu trạm/tuyến trong `app/src/main/assets/bus/` được tạo từ API này.
- **Bản đồ:** © [OpenStreetMap contributors](https://www.openstreetmap.org/copyright) (ODbL), tile OSM Mapnik.
- **Tìm địa điểm:** [Photon](https://photon.komoot.io) của komoot và [Nominatim](https://nominatim.org) (dữ liệu OSM).
- **UIS PTIT HCM** (`uis.ptithcm.edu.vn`): app là client không chính thức, không liên kết với Học viện.
- **Chứng chỉ** `res/raw/globalsign_root_r46.pem`: chứng chỉ gốc công khai GlobalSign Root R46.

## Tham khảo / cảm hứng

- [thuanmazda/hcmc-bus-tracker](https://github.com/thuanmazda/hcmc-bus-tracker) — app theo dõi xe buýt
  TP.HCM; tab Xe buýt lấy ý tưởng và cách gọi các endpoint EBMS từ dự án này. Repo đó **chưa có
  giấy phép**, nên repo này **không** chứa lại code của họ; phần code bus trong app được viết riêng.
- [youtubedl-android](https://github.com/yausername/youtubedl-android) — đã cân nhắc làm phương án dự phòng (hiện chưa dùng).

## Lưu ý

App không liên kết hay được YouTube/Google bảo trợ. Phần nhạc/video chỉ dành cho dùng cá nhân;
hãy tôn trọng Điều khoản dịch vụ của YouTube và bản quyền của nội dung.
