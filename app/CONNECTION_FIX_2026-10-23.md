# Khôi phục kết nối phân tích vườn

Lỗi người dùng gửi: kết nối đến địa chỉ cũ 192.168.100.46:8000 bị timeout, tablet đang ở 192.168.10.143.

Đã xác minh ngày 23/10/2026:

- Máy tính có Wi-Fi 192.168.10.253, backend lắng nghe 0.0.0.0:8000, GET /v1/health trả ok=true, mode=live. GET chỉ số có xác thực trả HTTP 200.
- Cấu hình được lưu trên tablet hiện là http://127.0.0.1:8000; token khớp backend (không ghi token vào báo cáo). Lỗi trên màn hình là lỗi của yêu cầu trước tới IP cũ.
- Đã thiết lập adb reverse tcp:8000 tcp:8000, lưu lại cấu hình và kiểm tra kết nối từ chính VWing: Đã kết nối · Máy chủ LIVE.
- Đã bấm phân tích Tổng hợp vườn trên tablet. POST /v1/satellite/garden-analysis trả HTTP 200; UI hiển thị nguồn Earth Engine + RustGIS, kỳ 2026-09-23 đến 2026-10-23, 6 cảnh Sentinel-2, diện tích 0,22 ha, chu vi 188 m, độ phủ hợp lệ 100%, ảnh kết quả và marker.
- Ảnh xác minh: ../logs/garden-analysis-live-fixed-20261023.png.

## Kết nối lại

Giữ kết nối USB/ADB giữa tablet và máy tính. adb reverse mất khi ngắt kết nối; sau khi cắm lại, chạy:

```powershell
cd 'E:\New folder\SmartFarm'
.\app\Connect-Tablet.ps1
```

Script đã được bổ sung khôi phục chuyển tiếp cổng 8000 (Satellite) và 8790 (đồng bộ) cho đúng serial tablet đã xác minh, cả nhánh USB và nhánh wireless ADB đã ghép đôi. Cú pháp PowerShell và nhánh USB đã chạy thành công với HA24RANR.

Trong VWing dùng URL http://127.0.0.1:8000 rồi Kiểm tra kết nối. Khi backend chưa chạy, mở Start-SatelliteBackend.ps1 hoặc tác vụ VWing Satellite Backend đã được cấu hình trên máy tính.

Đây là kết nối qua ADB, chưa xác minh truy cập LAN trực tiếp khi rút USB/tắt wireless ADB. Không thay đổi hồ sơ vườn hoặc cài lại APK trong lần xử lý này.
