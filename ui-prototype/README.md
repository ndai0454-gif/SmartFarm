# SmartFarm — bản giao diện tham khảo

Bản web tiếng Việt độc lập, tái dựng các nhóm chức năng tìm được trong project Android `com.utc.ag`, phiên bản 4.20.0. Không phải ứng dụng chính thức của DJI, không kết nối máy chủ hoặc drone.

## Chạy

Mở `index.html` trực tiếp bằng trình duyệt, hoặc chạy `python -m http.server 8765 --bind 127.0.0.1` trong thư mục này, rồi mở `http://127.0.0.1:8765`.

## Màn hình và thao tác

- Tổng quan: thời tiết mẫu, thống kê, công việc, thiết bị, truy cập nhanh.
- Thửa đất: tìm kiếm, bản đồ/danh sách, thêm thông tin thửa, mở nhật ký theo thửa.
- Thiết bị: danh sách và chi tiết thiết bị mẫu.
- Nhật ký: tìm kiếm, lọc Phun/Rải, xuất CSV, mở phát lại.
- Phát lại: tuyến mô phỏng, phát/tạm dừng, kéo tiến độ, phóng to/thu nhỏ.
- Đội bay: thành viên và phân công.
- Công việc: thêm lịch và người thực hiện.
- Cá nhân: điều hướng các công cụ và thông tin bản demo.

Dữ liệu mẫu chỉ lưu trong bộ nhớ phiên; tải lại trang sẽ đặt lại. Liên kết thiết bị và mời thành viên hiển thị giới hạn, không giả lập kết nối thành công. Bản đồ là SVG minh họa, không phải bản đồ địa lý thực. Không có AI phân tích cây trong bản này vì mục tiêu là tham khảo giao diện SmartFarm.

## Mức độ khớp với ứng dụng gốc

Các icon tab và tài nguyên nền được sao chép từ project người dùng cung cấp. Nhóm chức năng có dấu vết trong bundle Hermes và layout Android. Bố cục web, câu chữ tiếng Việt, các biểu mẫu và dữ liệu là phần tái dựng; chưa xác minh độ giống từng pixel vì chưa chạy app gốc. Xem `SOURCE_MAP.md`.
