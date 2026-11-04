---
name: ui-skills
description: Opinionated UI design & engineering constraints from ibelick/ui-skills (baseline-ui, fixing-motion-performance, fixing-accessibility, fixing-metadata) adapted for SmartFarm Aurora.
---

# ibelick/ui-skills — Hệ Quy Chuẩn Thiết Kế Giao Diện Dễ Nhìn & Dễ Thao Tác

Bộ quy tắc thiết kế giao diện từ kho mã nguồn [`ibelick/ui-skills`](https://github.com/ibelick/ui-skills.git), tập trung vào **tính dễ đọc (readability)**, **thao tác đơn giản 1 chạm (effortless interaction)**, **hiệu năng chuyển động mượt mà (compositor-only motion)** và **khả năng tiếp cận (WCAG AA/AAA accessibility)**.

## 1. `baseline-ui` — Quy chuẩn Giao diện Nền tảng
- **Typography (Chữ viết dễ đọc)**:
  - Tiêu đề (`h1, h2, h3`): Bắt buộc dùng `text-wrap: balance` để không bị rớt dòng lẻ chữ.
  - Đoạn văn & hướng dẫn (`p, .sub`): Bắt buộc dùng `text-wrap: pretty` và chiều cao dòng `line-height: 1.5`.
  - Số liệu (`NDVI`, `tọa độ VN-2000`, `nhiệt độ`, `diện tích`, `giá tiền`): Bắt buộc dùng `font-variant-numeric: tabular-nums` (`tnum`) để các chữ số thẳng hàng dọc, dễ so sánh.
- **Surface & Color (Màu sắc & Độ tương phản cao)**:
  - Không lạm dụng dải màu chuyển sắc (gradients) đa sắc gây rối mắt hoặc hiệu ứng phát sáng (glow) làm mờ chữ.
  - Dùng nền thẻ đặc/bán đặc độ tương phản cao (`#0B1D28` / `rgba(11, 29, 40, 0.92)`) kết hợp viền mảnh sắc nét `1px solid rgba(255, 255, 255, 0.14)`.
- **Layout & Safe Area**:
  - Luôn dùng `100dvh` thay vì `100vh`.
  - Tôn trọng `env(safe-area-inset-top)` và `env(safe-area-inset-bottom)` cho các thanh điều hướng cố định.
  - Mọi biểu mẫu nhập liệu (như ô tọa độ VN-2000 hoặc mã cây) không bao giờ chặn thao tác dán (`paste`) và hiển thị thông báo kết quả ngay sát nút bấm.

## 2. `fixing-motion-performance` — Quy chuẩn Hiệu năng Chuyển động
- **Không dùng hiệu ứng trễ khi tải trang**: Không chạy các chuỗi animation kéo dài >200ms khi vừa mở màn hình khiến người dùng phải chờ chữ hiện ra.
- **Chỉ animate thuộc tính Compositor**: Chỉ dùng `transform` và `opacity` với thời lượng `≤ 150ms cubic-bezier(0.16, 1, 0.3, 1)`. Tuyệt đối không animate `width`, `height`, `top`, `left`, `margin`, `padding`.
- **Tôn trọng `prefers-reduced-motion`**: Tắt toàn bộ chuyển động nếu thiết bị bật chế độ giảm chuyển động.

## 3. `fixing-accessibility` — Quy chuẩn Dễ Thao tác & Tiếp cận
- **Nhãn văn bản rõ ràng đi kèm biểu tượng**: Không dùng thanh điều hướng chỉ có icon trừu tượng; mọi nút trên thanh điều hướng đều phải có **Icon + Nhãn chữ tiếng Việt ngắn gọn** (`Tổng quan`, `Viễn thám`, `Sổ đỏ GIS`, `3D & RTK`, `Cây bệnh`, `Trợ lý AI`).
- **Kích thước vùng chạm lớn (Touch Target)**: Mọi nút bấm và thẻ tương tác có chiều cao tối thiểu `48px–54px` để nông dân dễ bấm bằng một tay ngoài thực địa.
- **ARIA & Focus**: Mọi nút đều có `aria-label` rõ nghĩa và viền `:focus-visible` sắc nét (`2px solid #4ADE80`).

## 4. `fixing-metadata` — Quy chuẩn Siêu dữ liệu HTML
- Khai báo `<html lang="vi">`, `<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">`, `<meta name="theme-color" content="#07141D">`, `<meta name="description">` và Open Graph tags đầy đủ.
