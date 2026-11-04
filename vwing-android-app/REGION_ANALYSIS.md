# Phân tích vùng trên Bản đồ vườn

Cập nhật ngày 13/10/2026 trong app Android `E:\Android`. Không phải màn hình Satellite độc lập của project SmartFarm.

## Sử dụng

1. Mở **Bản đồ**. Ranh phân tích mặc định là polygon ranh vườn đang lưu; có thể chọn polygon khác trong **Ranh giới phân tích**.
2. Cuộn đến **Phân tích vùng đã chọn** (màn hình rộng đặt cạnh bản đồ). Chọn **GIS & viễn thám**, NDVI/EVI/NDMI và khoảng ngày YYYY-MM-DD. Ngày cuối không tính.
3. Bấm **Phân tích vùng**. App gửi đúng polygon, nhận thống kê và PNG từ backend Earth Engine; lớp ảnh phủ hiển thị trên bản đồ vườn. Thanh Độ đậm lớp ảnh điều chỉnh alpha.
4. **LiDAR & đám mây điểm**: khi có dữ liệu, chọn định dạng LAS/CSV và hệ tọa độ, rồi **Nhập & phân tích tệp điểm**. Không có tệp thì không có kết quả LiDAR. Không tạo điểm giả từ vệ tinh.

Các chip Khu A/B/C/D là bộ lọc cây. Chỉ phân tích một khu nếu tồn tại polygon `ZONE` có tên trùng khu đó; nếu chưa có app chặn phân tích thay vì âm thầm dùng cả vườn. Nhập polygon khu bằng KML/GeoJSON và gán loại Khu vực. Ranh mẫu luôn có cảnh báo.

## Dữ liệu và giới hạn

GIS: tối đa 500 ha, khoảng ảnh ghép 93 ngày; lưu rõ kỳ ảnh, số cảnh, độ phủ hợp lệ, nguồn, phương pháp, nhãn mô phỏng nếu backend đang mock. Sentinel-2 có băng 10 m/20 m; không chẩn đoán bệnh hay sức khỏe từng cây. NDMI liên quan nước trong tán, không phải đo độ ẩm đất tại vườn. Tham khảo: https://developers.google.com/earth-engine/datasets/catalog/COPERNICUS_S2_SR_HARMONIZED

LiDAR: UTF-8 CSV `x,y,z[,classification]` (cũng nhận dấu chấm phẩy/khoảng trắng), hoặc LAS 1.1–1.4 không nén, point formats 0–10 theo phiên bản. Chọn WGS84 EPSG:4326 (x kinh độ/y vĩ độ), WGS84/UTM48N EPSG:32648 hoặc UTM49N EPSG:32649. Chưa hỗ trợ VN-2000 hoặc LAZ; cần chuyển đổi/giải nén trước. Hệ tọa độ do người dùng khai báo, không tự suy đoán từ LAS metadata. Z phải là mét và giữ hệ cao độ của tệp.

Giới hạn 20 MiB/250.000 điểm, polygon LiDAR 3–512 đỉnh. Phân tích toàn bộ điểm trong polygon (kể cả mép), loại LAS withheld; tính min/max/mean/P95 Z, số điểm class 2, diện tích, mật độ điểm trên toàn polygon. Mật độ này không khẳng định độ phủ đồng đều. Bản đồ hiển thị tối đa 2.000 điểm lấy cách đều theo thứ tự tệp; thống kê không tính trên riêng mẫu preview. Đây là biểu diễn điểm từ trên xuống theo màu Z, chưa phải trình xem 3D hoặc tạo DTM/DSM/chiều cao tán.

## Kết nối tại máy phát triển

Backend live dùng project hiện có:

```powershell
cd 'E:\New folder\SmartFarm\app'
.\Start-SatelliteBackend.ps1 -Mode live
```

Với tablet đang nối ADB, ánh xạ cổng theo serial hiện tại:

```powershell
adb -s <serial> reverse tcp:8000 tcp:8000
```

Kết nối trong app: `http://127.0.0.1:8000`. Token ở `.satellite-token` của SmartFarm; không đưa token vào mã nguồn hoặc log. Kết nối thử nghiệm này cần backend đang chạy và ADB reverse còn hoạt động. Thiết bị ngoài kết nối phát triển cần backend HTTPS có thể truy cập được.

## Kiểm tra đã thực hiện

- `assembleDebug` và `testDebugUnitTest --offline`: thành công, **93 tests**, 0 lỗi/fail/skip.
- LAS/CSV: 14 ca kiểm thử, gồm hệ số scale/offset, formats 0–10, flags, tệp cắt/nén, giới hạn, polygon lõm/mép và đối chiếu UTM với PROJ.
- Backend live: dùng chính polygon ranh vườn đọc từ tablet, diện tích trả về 0,37 ha, kỳ 2026-09-13 → 2026-10-13 (không gồm ngày cuối), 12 cảnh, độ phủ 89,3%. NDVI 0,8490715; EVI 0,7797294; NDMI 0,3306711. `simulated=false`, PNG tải được cho cả ba chỉ số. Đây là số liệu vệ tinh theo ranh đã lưu, chưa kiểm chứng thực địa.
- Đã cài APK cập nhật trên tablet TB321FU, kiểm tra màn Bản đồ, nút phân tích NDVI, kết quả live và trạng thái LiDAR chưa có dữ liệu. Backend cấu hình dùng token trong vùng riêng app, giữ nguyên dữ liệu vườn.
- Mỗi kết quả gắn với vùng/tham số yêu cầu; đổi vùng/tham số sẽ xóa lớp cũ và không nhận kết quả trễ của vùng cũ. Token ảnh chỉ gửi cùng origin, không theo redirect.
- Không có tệp LiDAR thực của người dùng để kiểm chứng số liệu thực địa; không gọi các fixture kiểm thử là dữ liệu vườn thật.
- Kết quả/lớp GIS và LiDAR hiện nằm trong phiên màn hình; rời màn hoặc xoay máy có thể cần phân tích lại. Tab Super-Resolution có giữ job ID để tiếp tục tải kết quả; xem `SUPER_RESOLUTION.md`. Chưa lưu lịch sử phân tích. Đã so SHA-256 xác nhận dữ liệu vườn trước/sau cập nhật không đổi.

Bằng chứng tại `app/build/outputs/region-analysis/`: JSON/PNG vệ tinh, ảnh UI và hierarchy. `device-data-before.json` là bản sao dự phòng dữ liệu riêng trước cập nhật, không nên chia sẻ. Script lặp lại smoke live: `tools/verify_region_live.py --serial <serial>`.

Mã chính: `ui/vwing/RegionAnalysisPanel.kt`, `ui/vwing/MapScreen.kt`, `ui/vwing/GardenMap.kt`, `data/vwing/RegionAnalysis.kt`, `data/vwing/PointCloudAnalysis.kt`, `data/vwing/Satellite.kt`.
