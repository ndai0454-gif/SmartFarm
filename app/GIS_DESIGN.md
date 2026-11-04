# VWING Geo Workspace — 13/10/2026

Thiết kế trực tiếp màn hình Android của biến thể `satellite`, trong `src/satellite/java/com/utc/ag/satellite/SatelliteActivity.java`. Lấy cảm hứng từ bảng màu và cách chia nhóm chuyên môn của poster được cung cấp, giữ thương hiệu VWING. Không sao chép logo, liên hệ hoặc lời mời tư vấn của GeoVision.

## Giao diện

- Nền xanh đậm `#081C30`, thẻ `#112C43`, vàng `#F1CC72`, xanh lá `#71D6AD`.
- Luồng 01 khu vực → 02 dữ liệu/phương pháp → 03 kết quả/bước tiếp theo.
- Từ 840 dp: bản đồ bên trái và bảng cuộn 360 dp bên phải. Màn hình hẹp: hai vùng xếp dọc, bảng điều khiển cuộn độc lập.
- Bộ chọn phương pháp giải thích NDVI/EVI/NDMI/Sentinel-1. Kỳ nền chỉ hiện khi chọn nước mặt.
- Kết nối URL/token nằm trong vùng mở rộng; nút Phân tích tự mở vùng này nếu thiếu token.
- Góc hướng dẫn gồm GIS, photogrammetry/drone, LiDAR và quy trình nghiên cứu. Drone/LiDAR là hướng dẫn, chưa có pipeline nhập/xử lý. Chưa có đặt lịch hoặc tư vấn trực tuyến.
- Cỡ nút tối thiểu 48 dp, nhãn trường gắn với ô nhập. Lưu vùng, phương pháp, khoảng ngày, độ trong suốt khi xoay màn hình; không lưu token.
- Giữ backend và hợp đồng API hiện có. Độ trong suốt đã chọn được áp dụng cho lớp ảnh mới.

## Build và xem thử

Tại thư mục project `E:\New folder\SmartFarm`:

```powershell
.\gradlew.bat :app:assembleSatelliteDebug --offline
```

APK tiêu chuẩn dùng package hiện có `com.example.test_tablet`; cài sẽ thay thế app cùng package. Bản sao tiện chia sẻ: `app/build/outputs/gis-design/VWING-GIS.apk`.

Để xem thử song song với app đang dùng:

```powershell
.\gradlew.bat -I app/gis-preview.init.gradle :app:assembleSatelliteDebug --offline
```

Package preview là `com.example.test_tablet.gispreview`. Bản sao: `app/build/outputs/gis-design/VWING-GIS-preview.apk`. Maps key hiện có có thể không cho phép package preview; không thay đổi giới hạn key. Nền Maps trống trong kiểm tra preview, vì vậy chưa xác nhận tải nền bản đồ hoặc phân tích live ở lần thiết kế này.

## Bằng chứng

Build tiêu chuẩn và preview thành công. Đã cài riêng preview trên tablet TB321FU, kiểm tra trực quan bố cục dọc/ngang, vùng mẫu 4 đỉnh, bộ chọn phương pháp, kỳ nền Sentinel-1 và hộp hướng dẫn drone. Cấu hình xoay được khôi phục sau kiểm tra. APK tiêu chuẩn chưa cài để tránh thay thế app hiện có.

Ảnh và UI hierarchy nằm tại `app/build/outputs/gis-design/`. Kết quả kiểm tra backend trước đây trong `SATELLITE_INTEGRATION.md` không phải kiểm tra lại trong lần thay giao diện này.
