# VWing — Landing page sáng

Trang quảng bá tiếng Việt, nền trắng ngà và xanh lá, dùng Urbanist theo file Figma gốc. HTML/CSS/JavaScript thuần; không cần npm hay thư viện UI. Toàn bộ nội dung, hình SVG minh họa, màu và bố cục có thể sửa trong mã nguồn. Đây là prototype web cục bộ, chưa xuất bản lên internet.

## Xem

```powershell
cd 'E:\New folder\SmartFarm\landing-page'
.\Start-Landing.ps1
```

Mở http://127.0.0.1:4173. Nếu server đang chạy thì không cần khởi động lần nữa. Font được tải từ Google Fonts; khi offline dùng Arial dự phòng.

## Nội dung

- Hero: “Hiểu từng cây. Chăm cả khu vườn.”, minh họa vườn và giao diện hồ sơ cây nền sáng.
- Tính năng: bản đồ/khu vực, nhật ký chăm sóc, QR cây, trợ lý tra cứu hồ sơ và sản lượng/thu hoạch.
- Dữ liệu vệ tinh: chuyển NDVI/EVI/NDMI/nước mặt, dùng PNG và số liệu thật của **polygon mẫu** 1,21 ha, không phải vườn người dùng. Ảnh được tạo bằng backend Earth Engine; lớp nước có thể trong suốt vì không ghi nhận nước mới.
- Ba bước sử dụng, FAQ, hướng phát triển và CTA mở bản demo.
- Demo chọn 12 cây và xem nhật ký mẫu; không kết nối backend/điều khiển thiết bị/lưu dữ liệu người dùng.
- Không có số người dùng, đánh giá, đối tác, hiệu quả tăng năng suất hoặc liên kết Google Play chưa xác minh. Drone segmentation và cảm biến được ghi là chưa phát hành.

## Đối chiếu nội dung

Nguồn thiết kế: https://www.figma.com/design/uP7vzyUikZKk7WnWMEwoYD/?node-id=2038-2

Đã đọc font/nhãn/screens từ file Figma; các chức năng đối chiếu từ `E:\Android\app\src\main\kotlin\com\example\test_tablet\ui\vwing` (VWingApp, QrScreen, AssistantScreen, YieldScreen, SatelliteScreen). Bản `app/src/satellite` ở SmartFarm vừa tích hợp chỉ là màn hình GIS/vệ tinh độc lập; không đại diện cho toàn bộ tính năng VWing.

Số liệu mẫu: NDVI 0,702; EVI 0,657; NDMI 0,247 trong 01/08–01/09/2026 (end exclusive). Sentinel-1 so sánh kỳ nền 01–20/08 với kỳ sự kiện 01–10/09/2026, nước mới 0 ha. Legend đối chiếu trực tiếp `vwing-satellite-backend/app/indices.py` và ee_service.py.

## Figma: còn chặn quyền ghi

MCP hiện đăng nhập `daindse160368@fpt.edu.vn`; đọc được file nhưng thao tác import component bị từ chối “read-only mode”. **Chưa thay đổi file Figma.** Cần cấp Can edit cho tài khoản này hoặc kết nối lại tài khoản có quyền sửa file. Không dùng ảnh chụp trang hoàn chỉnh làm lớp thiết kế trong Figma; khi ghi được cần tạo layer text, layout và SVG có thể sửa.

## Kiểm tra

- Desktop 1440 px và mobile 390 px: đã xem trực quan, không tràn ngang; mockup điện thoại không cắt nút cuối.
- Menu mobile mở/đóng; demo chọn A-07 đổi hồ sơ và nhật ký; FAQ mở/đóng; lớp EVI/NDMI/nước mặt đổi ảnh/thống kê/legend.
- Preview: preview-desktop.png, preview-hero.png, preview-mobile.png. reference-current.png là ảnh thiết kế Figma cũ để so sánh, không dùng làm giao diện trang.

## Các file

- index.html: nội dung và SVG minh họa.
- styles.css: theme sáng và responsive.
- app.js: demo, menu và bộ chuyển lớp vệ tinh.
- assets/: PNG phân tích vệ tinh mẫu.
