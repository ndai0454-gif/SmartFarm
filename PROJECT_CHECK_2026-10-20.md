# Kiểm tra SmartFarm / VWing — 20/10/2026

## Phạm vi

Kiểm tra mã và chạy kiểm thử tại `E:\New folder\SmartFarm`: Node sync server, Python satellite backend, Android satellite flavor; kiểm tra cú pháp JavaScript của landing page và prototype server. App VWing Compose chính ở `E:\Android` chưa được build/test trong lần này. Không sửa mã ứng dụng, không chạy script public, không gọi Earth Engine live hoặc SMS thật.

Thư mục SmartFarm hiện không phải Git repository (`git status` trả `not a git repository`). Không có root README tổng hợp; tài liệu chia theo thành phần.

## Kết quả xác minh

| Hạng mục | Kết quả |
|---|---|
| `node --test test.js test-otp.js test-rbac-matrix.js` | 160 passed, 0 failed |
| `.venv\Scripts\python.exe -m pytest -q tests` trong satellite backend | 121 passed trong 45,73 giây |
| `python app/verify_satellite_http.py --mode mock` bằng Python của backend | 4/4 NDVI, EVI, NDMI, water: auth, bounds, nhãn simulated và PNG đạt |
| `:app:assembleSatelliteDebug --offline` | Task build APK hoàn tất; APK ở `app/build/outputs/apk/satellite/debug/app-satellite-debug.apk` |
| `node --check landing-page/app.js` và `node --check ui-prototype/server.js` | Đạt cú pháp; chưa kiểm tra giao diện trình duyệt |
| `:app:lintSatelliteDebug --offline` | Thất bại tại `lintAnalyzeSatelliteDebug`: `OutOfMemoryError: Java heap space`; lệnh build + lint kết thúc exit 1 sau 4 phút 15 giây, dù task assemble đã hoàn tất trước đó |

## Vấn đề và thứ tự ưu tiên

### P1 — Lint Android hiện không chạy xong với cấu hình hiện tại

Lần chạy mới thất bại do `Java heap space`; `gradle.properties:3` giới hạn heap 2 GB. Đây là lỗi công cụ kiểm tra, không phải bằng chứng app runtime crash. File báo cáo lint đang có từ 13/10/2026 vẫn chứa 2514 errors và 1224 warnings, phần lớn trỏ vào mã Java giải biên dịch ở `src/main/java`; không dùng số cũ này làm kết quả của lần kiểm tra hôm nay. Cần kiểm tra phạm vi nguồn lint của flavor và cấu hình bộ nhớ, rồi chạy lại. `app/build.gradle:45` còn đặt `abortOnError false`, vì vậy ngay cả khi lint chạy xong thì exit thành công cũng chưa đủ chứng minh không có lỗi lint.

### P1 — Dữ liệu hiển thị sẵn dù chưa phân tích

`app/src/satellite/java/com/utc/ag/satellite/SatelliteActivity.java:95` đặt sẵn “Sức khỏe tán · 87.5%”. Người dùng mới mở app thấy tỷ lệ có vẻ là kết quả đo dù chưa có dữ liệu. Tại dòng 477 còn có mặc định 76% nếu phản hồi thiếu trường. Dòng 394 ép thanh tỷ lệ luôn sáng ít nhất hai vạch kể cả khi độ phủ bằng 0.

Nên dùng trạng thái “Chưa có dữ liệu”, biểu diễn đúng 0%, và phân biệt độ phủ tán với sức khỏe cây. Các KPI không được xóa khi chọn ranh giới mới tại dòng 269–289, nên số liệu vùng trước có thể vẫn hiện cạnh vùng mới. Nhận định từ mã; chưa tái hiện trên thiết bị.

### P1 — Script public dừng tiến trình ngoài phạm vi project

`Start-Public.ps1:11–13` dừng mọi tiến trình `cloudflared` và mọi tiến trình đang nghe cổng 8790/8000/2322, không kiểm tra project sở hữu. Chạy script có thể ngắt tunnel hoặc dịch vụ khác trên máy.

Nên lưu PID của các tiến trình do script tạo và xác minh command line trước khi dừng. Dòng 53 chờ cố định 6 giây rồi xuất URL, chưa kiểm tra health hai backend nên cũng có thể báo địa chỉ khi dịch vụ đã khởi động lỗi. Chỉ kiểm tra tĩnh; không chạy script này.

### P2 — Mã quản trị chưa được bảo vệ bởi quy tắc ignore

Root `.gitignore` có `/.satellite-token` nhưng thiếu `/.vwing-admin-token`, trong khi `Start-Public.ps1:38–40` tạo/đọc tệp quản trị này tại root. `PUBLIC.md` xác nhận đây là mã quản trị cần giữ kín. Nếu sau này tạo Git repository và thêm toàn bộ thư mục, mã này có thể bị thêm nhầm. Hiện chưa có bằng chứng mã đã bị công khai.

### P2 — “Lần kiểm tra gần nhất” lấy cả sự kiện thu hoạch

`vwing-sync-server/public.js:55,69`: chọn sự kiện mới nhất của mọi loại thay vì sự kiện quan sát/tái kiểm tra. Tái hiện bằng `treeView` với RECHECK tại 1000 và HARVEST tại 2000 cho kết quả `lastCheck: 2000`.

Nên xác định loại sự kiện được tính là kiểm tra, lọc trước khi lấy mốc mới nhất, bổ sung test có chăm sóc/thu hoạch sau lần kiểm tra.

### Cần làm rõ — Phạm vi theo khu không áp dụng cho mọi tập dữ liệu

`vwing-sync-server/rbac.js:44–51,64`: `trees/events/issues/tasks/measures/zones` được lọc theo khu, nhưng `features/imagery/indices/droneFlights` đi qua nhánh mặc định cho phép. Tái hiện trực tiếp bằng `redact` với pilot chỉ được khu A vẫn trả một droneFlight được gắn zone B trong dữ liệu thử. Mã có chú thích rằng các tập này không gắn khu; vì vậy cần xác định đây là dữ liệu dùng chung toàn vườn có chủ đích hay phải thu hẹp quyền. Không kết luận toàn bộ cơ chế RBAC bị vượt qua.

## Giới hạn kết luận

Các test đạt không chứng minh bản đồ hoạt động trên thiết bị, SMS thật được gửi, tunnel hiện còn truy cập được, hoặc kết quả viễn thám chính xác ngoài thực địa. Lần này chưa kiểm tra tablet, trình duyệt trực quan, app Compose E:\Android, APK legacy giải biên dịch, Earth Engine live hay khoa học đếm cây.

README của sync server và satellite backend còn ghi số test cũ; nên cập nhật theo lệnh tái lập thay vì giữ số lượng cố định dễ lỗi thời.
