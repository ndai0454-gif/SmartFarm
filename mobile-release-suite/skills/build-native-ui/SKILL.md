---
name: build-native-ui
description: Official Native UI Architecture & Design Skill (Material 3 Expressive for Android + Liquid Glass for iOS 18/19 & Expo/Jetpack Compose) tailored for SmartFarm Aurora / VWing.
---

# Build Native UI Skill (Material 3 & Apple Liquid Glass)

Hướng dẫn chuẩn cho AI Agent / LLM khi xây dựng và tinh chỉnh giao diện di động chuẩn Native trên cả hai nền tảng **Android (Material 3 Expressive)** và **iOS (Apple Liquid Glass / HIG)** cho hệ sinh thái **SmartFarm Aurora / VWing**.

## 1. Triết lý Thiết kế Cốt lõi (Dual-Platform Native Craft)

1. **Android — Material 3 Expressive (`E:\Android` & `com.utc.ag`)**:
   - **Dynamic Color & Surface Tones**: Sử dụng hệ màu nông nghiệp sinh thái với nền tối sâu `#04121B` (Aurora Deep Slate) hoặc nền sáng kem lá `#F6F8F5` (Farmer Daylight Mode), điểm nhấn `#38EF7D` / `#A8DF65` (Chlorophyll Green) và `#156E3F` (Deep Canopy).
   - **Bo góc đồng nhất (Shape Scale)**: Thẻ chính dùng góc bo `28dp` (`LeafShape`), nút hành động và chip trạng thái dùng `18dp` (`LeafShapeSmall`) hoặc `999px` (Pill).
   - **Edge-to-Edge & Window Insets**: Luôn tôn trọng `statusBarsPadding()` và `navigationBarsPadding()`; không để thanh trạng thái hệ thống đè lên tiêu đề hoặc bản đồ.

2. **iOS & Hybrid — Apple Liquid Glass (`ui-prototype/index.html`)**:
   - **Multi-layer Specular Glass**:
     - Lớp nền kính mờ: `backdrop-filter: blur(26px) saturate(118%)`.
     - Viền phản xạ ánh sáng (Specular Border): `1px solid rgba(255, 255, 255, 0.22)` kết hợp luồng sáng chéo (`linear-gradient(180deg, rgba(255,255,255,0.20) 0%, rgba(255,255,255,0.25) 100%)`).
   - **Tabular Numerals (`tnum`)**: Toàn bộ số liệu cảm biến IoT, tọa độ `VN-2000`, chỉ số `NDVI`, sai số `RTK` và nhiệt độ bắt buộc bật `font-variant-numeric: tabular-nums` để không bị nhảy chữ khi số liệu cập nhật thời gian thực.

## 2. Quy chuẩn Thành phần Giao diện (Component Rules)

| Thành phần | Chuẩn Android (Jetpack Compose M3) | Chuẩn iOS / Liquid Glass (Web / Expo) |
|---|---|---|
| **Navigation** | Bottom Navigation Bar 5 mục (Trang chủ, Bản đồ, Vệ tinh, Kiểm tra, Thêm), cao `72dp`, chỉ báo viên thuốc. | Floating Glass Dock cố định cạnh dưới trên điện thoại (`height: 64px`, `border-radius: 26px`) hoặc cột trái trên Tablet/Desktop. |
| **Touch Targets** | Tối thiểu `48dp × 48dp` (khuyến nghị `52dp` khi thao tác ngoài rẫy). | Tối thiểu `44pt × 44pt` theo Apple HIG. |
| **Typography** | `Be Vietnam Pro` + `Plus Jakarta Sans`, phân cấp rõ Eyebrow (`12sp–15sp`), Giá trị lớn (`28sp–64sp`), Nhãn phụ (`13sp`). | `Inter` + `Inter Tight`, hỗ trợ Dynamic Type và chống cắt dấu tiếng Việt (`padding-bottom` cho các chữ có nét móc dưới như `g, y, p, q`). |
| **Motion & Reduced Motion** | Phản hồi chạm `bounceClick` mượt mà, không gây trễ luồng đọc. | Tôn trọng `@media (prefers-reduced-motion: reduce)` và tắt hiệu ứng làm trễ hiển thị trên thiết bị GPU yếu. |

## 3. Danh sách Kiểm tra Trước Khi Xuất Bản Giao Diện (UI Checklist)

- [x] Không dùng đường dẫn ảnh tương đối bị thiếu trong `assets/`.
- [x] Mọi lưới nhiều cột (`.sf-grid`) tự động gập về 1 cột (`grid-template-columns: 1fr`) trên màn hình dọc `≤ 860px`.
- [x] Mọi từ viết tắt kỹ thuật (`3D`, `GEE`, `NDVI`, `RTK`, `GNSS`, `VN-2000`, `DXF`, `TOF`) hiển thị đúng chữ hoa chuyên ngành.
- [x] Không chặn luồng vẽ đầu tiên (First Contentful Paint) bởi Google Fonts khi thiết bị ngoại tuyến ngoài vườn.
