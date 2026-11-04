# Satellite đã tích hợp vào VWing

Ngày thực hiện: 23/10/2026. Project: `E:\Android`. Package vẫn là `com.example.test_tablet`.

## Mở và sử dụng

Trong VWing, mở **Thêm → Vệ tinh & phân tích vườn** (tablet ngang có mục riêng ở thanh bên). Tìm nhanh cũng có mục **Vệ tinh & phân tích**.

1. Chọn một trong 7 chế độ: Tổng hợp vườn, NDVI, EVI, NDMI, Nước mặt Sentinel-1, Hạn hán khí tượng, Drone đa phổ/VRA.
2. Dùng ranh vườn đã lưu, chọn vùng mẫu, hoặc nhấn giữ Google Maps để thêm đỉnh. Có Vẽ lại và Bỏ đỉnh cuối. Đây là vùng phân tích tạm thời, không ghi đè ranh vườn trong hồ sơ.
3. Với tổng hợp/chỉ số/nước mặt, chọn 30/60/90 ngày hoặc nhập YYYY-MM-DD. Ngày kết thúc không được tính; một kỳ dài tối đa 93 ngày. Nước mặt có kỳ nền riêng và nút lấy kỳ liền trước.
4. Tổng hợp vườn có 9 nhóm cây và khoảng cách tùy chỉnh 0,6–25 m (chấp nhận dấu phẩy thập phân).
5. Cấu hình backend ở **Kết nối máy chủ**, kiểm tra kết nối rồi bấm Phân tích. Kết quả và ảnh phủ xuất hiện theo vùng đang chọn. Thanh trượt điều chỉnh độ trong suốt từ 0–100%.
6. Đọc nguồn, nhãn mô phỏng, chú giải, cảnh báo và hướng dẫn trước khi sử dụng kết quả.

**Mở công cụ vệ tinh nâng cao** giữ đường truy cập tới chuỗi thời gian, quét nhiều chỉ số, lưu NDVI, GIS và các công cụ VWing đã có.

## Đối chiếu với Satellite riêng

| Satellite riêng | Trong VWing |
|---|---|
| Bộ chọn 7 phương pháp | `SatelliteWorkspaceScreen`, luôn hiện cả 7 trong chế độ đơn giản |
| Tổng hợp, NDVI/EVI/NDMI, Sentinel-1 | Tận dụng `SatelliteClient` và các parser đang có |
| Hạn hán 2000–2025, đối chiếu Basra | `DroughtMonitoringClient` + `DroughtMonitoringHubCard` |
| Drone đa phổ/VRA, chỉ số và báo cáo | `DroneMultispectralClient` + `DroneMultispectralHubCard` |
| 9 cây trồng, khoảng cách | `SatCropKey` + kiểm tra khoảng cách trước khi gọi máy chủ |
| Ngày ảnh và kỳ nền tùy chọn | Ngày ISO kiểm tra nghiêm ngặt; preset tính động theo ngày thiết bị |
| Bảo Lộc mẫu và 7 vùng địa lý | 8 vùng: 6 vùng VN, Bảo Lộc 1,21 ha, Basra Iraq |
| Google Maps, đỉnh ranh, polygon, ảnh phủ | Bản đồ trong workspace; giữ nguyên màn Bản đồ vườn hiện có |
| Điểm tán và thông tin khi chạm | Tối đa 80 marker điểm tán; mã, trạng thái, NDVI/NDRE; báo cáo đầy đủ bên dưới |
| Độ trong suốt | Slider 0–100%, áp dụng đúng alpha cho GroundOverlay |
| URL/token và xác thực ảnh | Dùng cấu hình/client hiện có của VWing; giữ kiểm tra origin và token của client |
| 4 chủ đề hướng dẫn | GIS, photogrammetry, LiDAR, từ câu hỏi đến hành động |
| Khôi phục khi xoay máy | Giữ phương pháp, vùng tạm, ngày, cây và độ trong suốt bằng rememberSaveable |

Không sao chép các giá trị KPI đặt sẵn 87,5%/76%/90% của app riêng. Thay vùng, ngày, phương pháp, cây/khoảng cách hoặc cấu hình kết nối sẽ thay trạng thái báo cáo; phản hồi yêu cầu cũ không hiển thị dưới đầu vào mới. Đổi alpha không làm mất báo cáo.

## Phạm vi dữ liệu thật

- NDVI/EVI/NDMI/nước mặt và tổng hợp dùng backend Earth Engine hoặc mock theo cấu hình hiện có.
- Hạn hán và drone/VRA hiện có nhiều số liệu/lưới sinh bằng mô phỏng/công thức ngay cả khi backend ghi LIVE. Workspace hiển thị cảnh báo cố định và đánh dấu báo cáo ban đầu của hai chức năng này là mô phỏng. Chưa có kiểm chứng thực địa hoặc điều khiển drone/phun thật.
- Việc tích hợp không thay thế pipeline photogrammetry, hiệu chuẩn phổ, chứng thư bảo hiểm hoặc truy cập DJI chính thức.
- Khi backend không truy cập được, giao diện có lỗi kết nối và cho đổi cấu hình; không tạo kết quả giả để thay thế.

## Kiểm tra và bàn giao

Lệnh kiểm tra tập trung (PowerShell, lưu ý vị trí `--tests` và dấu nháy của `-P`):

```powershell
cd E:\Android
.\gradlew.bat :app:testDebugUnitTest --tests com.example.test_tablet.SatelliteWorkspaceTest --tests com.example.test_tablet.SatelliteWorkspaceBackendTest --tests com.example.test_tablet.ui.SatelliteWorkspaceUiTest --tests com.example.test_tablet.VWingSatelliteTest :app:assembleDebug '-Proborazzi.test.record=true' --offline --console=plain
```

Các suite: 13 kiểm tra model/ngày/khoảng cách/preset/identity; 7 kiểm tra UI gồm HTTP trả kết quả và xóa kết quả khi đổi ngày; 7 kiểm tra Sentinel với Python mock backend; 1 kiểm tra hạn hán/drone và PNG qua backend mock. Tổng **28 kiểm thử**, không bỏ qua trong lần chạy xác minh.

APK debug: `E:\Android\app\build\outputs\apk\debug\app-debug.apk`.

Bản bàn giao: `E:\Android\deliverables\VWing-Satellite-20261023.apk`. SHA-256: `89E079AD41908442CADD7A9F4A60098D0B9BCF0A6DB0EA38463BD8E33431C6D7`. Đã cập nhật bản cuối lên tablet bằng `adb install -r`.

Log: `E:\New folder\SmartFarm\logs\satellite-merge-verification-final.log`. Báo cáo JUnit/HTML trong `app/build/test-results/testDebugUnitTest` và `app/build/reports/tests/testDebugUnitTest`.

Ảnh JVM/Robolectric (không phải ảnh thiết bị): `app/build/outputs/roborazzi/satellite-workspace-phone-portrait.png`, `satellite-workspace-tablet-portrait.png`, `satellite-workspace-tablet-landscape.png`. Trong test, map native được thay bằng fallback để kiểm tra bố cục không phụ thuộc Google Play Services.

Tablet thật: Lenovo TB321FU / HA24RANR, Android 15. Đã kiểm tra chữ ký APK trùng bản cài, package `com.example.test_tablet`, versionCode 1; cập nhật bằng `adb install -r`. Đã mở workspace, danh sách 7 chế độ, bản đồ Google và cảnh báo mô phỏng; xem bố cục dọc/ngang, có lần kiểm tra font scale 1,3 rồi trả về giá trị gốc 0,85.

Ảnh máy thật: `E:\New folder\SmartFarm\logs\satellite-merge-tablet-portrait.png` và `satellite-merge-tablet-landscape-large-font.png`.

Trước cập nhật đã lưu APK cũ và dữ liệu riêng tại `E:\New folder\SmartFarm\backup-tablet-satellite-merge-20261023`. Sau cập nhật, SHA-256 nội dung `files/vwing_data.json` trùng bản sao lưu (42.177 byte), xác nhận hồ sơ vườn không đổi trong kiểm tra này. Không gỡ app Satellite riêng.

Chưa chạy phân tích GEE live hoặc thử điều khiển drone trong lần tích hợp này. Bộ test toàn app trước tích hợp có 9 lỗi Compose UI đã ghi nhận trong báo cáo audit; 28 test tập trung ở trên không phải tuyên bố toàn bộ test của VWing đã sạch.

## File nguồn

- `app/src/main/kotlin/com/example/test_tablet/ui/vwing/SatelliteWorkspaceScreen.kt`: màn tích hợp, quản lý trạng thái, bản đồ và báo cáo.
- `app/src/main/kotlin/com/example/test_tablet/data/vwing/SatelliteWorkspace.kt`: phương pháp, ngày, preset, hướng dẫn và identity đầu vào.
- `app/src/main/kotlin/com/example/test_tablet/ui/vwing/VWingApp.kt`: điều hướng vào workspace và màn nâng cao.
- `app/src/test/kotlin/com/example/test_tablet/SatelliteWorkspaceTest.kt`, `SatelliteWorkspaceBackendTest.kt`, `ui/SatelliteWorkspaceUiTest.kt`: kiểm tra mới.
