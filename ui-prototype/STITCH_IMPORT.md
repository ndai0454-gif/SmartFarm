# SmartFarm — brief đưa vào Google Stitch

## Trạng thái

Đã chuẩn bị cấu hình Streamable HTTP MCP tới https://stitch.googleapis.com/mcp trong cấu hình Codex. Server tạm tắt vì chưa có STITCH_API_KEY. Chưa nhập giao diện lên Stitch.

## Nội dung chuyển giao

Tái dựng bộ giao diện tham khảo SmartFarm tiếng Việt đã có trong thư mục này. Giữ các luồng và cách gọi màn hình, không thêm tính năng AI chưa được triển khai. Dữ liệu chỉ là minh họa.

### Prompt thiết kế

Create a coherent Vietnamese SmartFarm management application for Group 23, based on the supplied reference prototype and screenshot. This is an independent student prototype, not an official DJI application. Preserve a practical agricultural operations UI: white backgrounds, pale grey workspace, green accent #32B768, clear sans-serif typography, compact controls, readable Vietnamese diacritics. Desktop uses a left sidebar, mobile uses bottom navigation. Navigation: Tổng quan, Thửa đất, Thiết bị, Nhật ký, Cá nhân. Label all sample metrics, weather and maps as illustrative demo data.

Create these 8 connected screens:
1. Tổng quan: sample local weather, quick access, operational totals, recent jobs, devices.
2. Thửa đất: search, list/map toggle, selected plot information, add-plot form, flight-history link. Map is illustrative, no claim of live satellite data.
3. Thiết bị: example Agras device cards and a device-detail panel. No fake successful account/device connection.
4. Nhật ký: searchable flights, Phun/Rải filter, CSV export action, link to replay.
5. Phát lại chuyến bay: map, example serpentine flight path, dark detail panel with duration/area/sorties, playback controls and scrubber.
6. Đội bay: sample members, roles and job assignments.
7. Công việc: list and creation form with title, plot, operator and date.
8. Cá nhân: demo account and links to team, jobs, devices, records.

Do not invent crop diagnosis accuracy, EUDR certification, automatic dose control or live DJI connectivity. Keep exact Vietnamese labels. Use the supplied screenshot as a visual reference, but produce editable screens rather than a flattened screenshot. Distinguish generated redesigns from the original local prototype.

## Tệp nguồn

- index.html / styles.css / app.js: giao diện tương tác gốc.
- preview-desktop.png: ảnh tham chiếu tổng quan.
- SOURCE_MAP.md: bằng chứng tham chiếu từ project Android.
- assets/: icon và nền lấy từ project người dùng cung cấp.

## Lưu ý import

Không mặc định MCP có chức năng nhập nguyên một thư mục HTML thành thiết kế chỉnh sửa được. Khi xác thực xong, cần đọc danh sách tool thực tế của Stitch rồi chọn chức năng import hoặc tạo màn hình từ brief/ảnh phù hợp. Nếu tạo lại bằng prompt, phải mô tả là tái dựng, không gọi là import nguyên trạng.

## Xác thực

Theo Google Codelabs: ảnh tài khoản → Stitch settings → API key → Create key. Không dán key vào brief, source code hay cuộc trò chuyện. Cấu hình Codex đọc header X-Goog-Api-Key từ biến môi trường STITCH_API_KEY. Chỉ bật MCP và xác nhận kết nối sau khi key đã được cấu hình và tools/list thành công.

Nguồn: https://codelabs.developers.google.com/design-to-code-with-antigravity-stitch ; https://github.com/google-labs-code/stitch-sdk ; https://developers.openai.com/codex/mcp/
