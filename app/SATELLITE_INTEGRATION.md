# VWING Satellite trong module app

Thư mục `app/src/main` là mã giải biên dịch DJI SmartFarm. Biến thể mới `satellite` dùng mã Java tại `src/satellite`, không biên dịch 11.104 file Java giải biên dịch. Các file gốc còn nguyên và được ánh xạ sang biến thể `legacy`; khả năng build legacy chưa được xác minh. Đây là màn hình vệ tinh độc lập, chưa ghép vào giao diện DJI cũ hoặc VWING ở E:\Android.

## Chạy Android

Mở project `E:\New folder\SmartFarm` trong Android Studio, chọn Build Variant **satelliteDebug**. Hoặc:

```powershell
cd 'E:\New folder\SmartFarm'
.\gradlew.bat :app:assembleSatelliteDebug
```

APK: `app\build\outputs\apk\satellite\debug\app-satellite-debug.apk`.

Application ID `com.example.test_tablet.satellite` (từ 20/10/2026; trước đó dùng chung `com.example.test_tablet` với app VWing ở `E:\Android` nên cài cái này sẽ ghi đè cái kia). Hai app giờ cùng tồn tại trên thiết bị. **Key Maps hiện có đang giới hạn theo `com.example.test_tablet` + SHA-1 debug `29:42:DD:D9:00:21:C9:04:02:A8:2E:A0:DD:6D:7C:A0:40:4B:4B:2D`: bản đồ Google trong app Satellite sẽ không tải cho tới khi thêm gói `com.example.test_tablet.satellite` với cùng SHA-1 vào giới hạn của key trong Google Cloud Console (Credentials → API key → Application restrictions → Android apps).**

`MAPS_API_KEY` được đọc từ `local.properties` ở project root; file này đã nằm trong .gitignore. Key Android có thể trích xuất từ APK, vì vậy phải giữ giới hạn API + package + SHA-1. Release cần chữ ký và giới hạn key riêng. Google Maps cần billing liên kết đúng project AIVES; chỉ bật SDK và có key chưa bảo đảm bản đồ tải được.

## Chạy Earth Engine backend

```powershell
cd 'E:\New folder\SmartFarm'
.\app\Start-SatelliteBackend.ps1
```

Script mặc định dùng **live**, giữ OAuth Earth Engine trên máy chủ và tạo token backend trong `.satellite-token` (đã ignore). Xem token cục bộ để nhập vào app:

```powershell
Get-Content 'E:\New folder\SmartFarm\.satellite-token'
```

Emulator: URL `http://10.0.2.2:8000`. Thiết bị USB hoặc wireless ADB: chọn đúng serial từ `adb devices -l`, chạy `adb -s <serial> reverse tcp:8000 tcp:8000`, nhập `http://127.0.0.1:8000`. Ảnh mock dùng URL tương đối và được app ghép với backend URL. HTTP chỉ được cho phép trong debug. Triển khai production dùng HTTPS và xác thực người dùng; token nhập tay hiện tại phục vụ thử nghiệm, không phải cơ chế phân quyền từng vườn.

## Sử dụng

1. Nhấn giữ bản đồ thêm ít nhất ba đỉnh (GeoJSON gửi kinh độ trước vĩ độ). Nút Xóa bắt đầu lại; Vùng mẫu là polygon 1,21 ha tại 107.8E/11.55N, không phải vườn người dùng.
2. Nhập URL/token; chọn NDVI, EVI, NDMI hoặc nước mặt Sentinel-1. Ngày theo YYYY-MM-DD, ngày kết thúc không được tính. Nước mặt cần thêm kỳ nền trước kỳ sự kiện. Ngày mặc định là bộ ngày mẫu đã kiểm tra.
3. Phân tích chạy ngoài UI thread; ảnh PNG được đặt thành GroundOverlay theo bounds trả về. Có thống kê, cảnh báo, nguồn, nhãn mô phỏng/thật, thanh độ trong suốt. Không gửi token backend tới Google Earth Engine; chỉ gửi cho backend cùng origin. Không tự theo redirect mang token.
4. Backend xác thực, kiểm tra polygon, diện tích <=500 ha, khoảng ảnh ghép <=93 ngày. HTTP 401: kiểm tra token; 404: không có cảnh; 422: polygon/ngày; 502: xử lý Earth Engine thất bại. URL thumbnail hết hạn thì Phân tích lại.

NDVI/EVI phản ánh độ xanh tán, NDMI liên quan nước trong tán (SWIR gốc 20 m), không đo trực tiếp độ ẩm đất hoặc chẩn đoán bệnh từng cây. Sentinel-1 nước mặt chưa có kiểm chứng thực địa và chưa thêm pipeline lọc speckle/hiệu chỉnh địa hình. Màn hình này chưa hỗ trợ QR, phân đoạn cây, chuỗi thời gian hoặc lưu nhiều vườn.

## Kết quả kiểm tra 13/10/2026

- `assembleSatelliteDebug`: thành công; kiểm tra APK xác nhận launcher `com.utc.ag.satellite.SatelliteActivity`, package và chữ ký đúng giới hạn key.
- Backend: 18 kiểm thử đạt. `verify_satellite_http.py --mode live` và `--mode mock` xác nhận cả NDVI/EVI/NDMI/nước mặt qua POST có Bearer token, đúng nhãn dữ liệu, bounds và tải được PNG; yêu cầu không token bị từ chối 401. Script dừng server thử nghiệm khi xong.
- `lintSatelliteDebug` chạy xong nhưng **không sạch**: 2.511 lỗi + 1.224 cảnh báo trên model project gồm mã giải biên dịch; cấu hình cũ `abortOnError=false` vẫn còn. Lọc vị trí `src/satellite`, `src/common` và build.gradle không thấy lỗi Error, có cảnh báo icon/backup/chuỗi giao diện. Không coi exit code thành công là lint sạch.
- Chưa cài APK hoặc kiểm tra giao diện trên thiết bị. Chưa xác nhận Google Maps tải nền sau khi liên kết billing. Chưa đối chiếu kết quả phân tích với thực địa/Code Editor.

## Tài liệu tham khảo

- https://developers.google.com/maps/documentation/android-sdk/config
- https://developers.google.com/maps/documentation/android-sdk/get-api-key
- https://developers.google.com/earth-engine/guides/auth
