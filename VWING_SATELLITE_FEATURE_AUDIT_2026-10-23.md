# Kiểm kê tính năng VWing và Satellite — 23/10/2026

## Phạm vi và cách hiểu

Tạm hiểu “stateless” là app **VWING Satellite** vừa được kiểm tra trong hội thoại. Không thấy module Android tên stateless. Kiểm kê theo mã nguồn hiện tại và kiểm thử tự động; chưa thao tác trên tablet hoặc xác minh máy chủ production.

- **VWing chính:** `E:\Android`, package `com.example.test_tablet`, Jetpack Compose.
- **Satellite riêng:** `E:\New folder\SmartFarm\app\src\satellite`, package `com.example.test_tablet.satellite`, Activity Java.
- `SmartFarm\app\src\main\com.utc.ag.apk` là nguồn legacy giải biên dịch; không đại diện cho tính năng Satellite đang build.
- Có xử lý trong mã không đồng nghĩa đã kiểm chứng phần cứng, nguồn dữ liệu ngoài hoặc độ chính xác thực địa.

## VWing: các tính năng có đường mở từ giao diện

| Nhóm | Tính năng trong mã hiện tại | Điều kiện và giới hạn |
|---|---|---|
| Tổng quan | Tổng số cây, công việc hôm nay, vấn đề cần xem, số liệu vườn, truy cập nhanh | Có bộ dữ liệu mẫu; phải phân biệt với dữ liệu người dùng |
| Hồ sơ cây | Thêm/sửa cây, mã cây, khu, giống, năm trồng, số trái, tọa độ, ảnh và lịch sử | Lưu cục bộ qua Store; thao tác chịu kiểm tra quyền |
| Bản đồ | Bản đồ vườn theo sơ đồ/tọa độ, Google satellite, ranh giới/khu/đường/nguồn nước, vị trí cây, tìm địa điểm, GPS và đi bộ ghi ranh | Google Maps, tìm địa điểm và GPS phụ thuộc cấu hình/quyền/mạng |
| QR | Tạo nhãn cây, lưu ảnh/tờ nhãn, quét bằng CameraX, QR nội bộ và QR cho người mua | QR người mua cần trang công khai và backend truy cập được |
| Nhật ký | Ghi chăm sóc/quan sát/thu hoạch, ảnh camera/thư viện, nhập giọng nói hệ thống, lịch sử và lọc bản ghi | Camera và nhận giọng nói cần quyền/dịch vụ Android; chưa thử thiết bị lần này |
| Vấn đề/công việc | Ghi vấn đề, tạo/giao việc, nhận việc mở, cập nhật tiến độ và kiểm tra lại | Luồng cộng tác cần máy chủ; sync server có kiểm tra quyền phía server |
| Thu hoạch | Ghi thu hoạch, dự tính sản lượng, giá bán và doanh thu theo dữ liệu nhập | Là phép tính/ước tính từ hồ sơ, không phải cảm biến tự đo năng suất |
| Dữ liệu | Nhập cây CSV/TSV, ảnh và chỉ số; sao lưu/khôi phục, gộp dữ liệu; nhập/xuất GIS | Định dạng và đường xử lý cụ thể có giới hạn; chưa kiểm tra mọi file thực |
| Tài khoản/cộng tác | Đăng ký/đăng nhập, OTP, vườn dùng chung, lời mời, vai trò/phạm vi khu/thời hạn, thu hồi thành viên, chuyển chủ | Có API; SMS thật chưa được xác minh trong lần này |
| Đồng bộ | Lưu máy trước, gửi sau, phiên bản, xung đột và lựa chọn bản giữ, ảnh, nhật ký kiểm toán | Cần URL và tài khoản backend hợp lệ; chưa thử hai tablet lần này |
| Chia sẻ | Trang truy xuất công khai chọn trường hiển thị; chia sẻ vị trí có bật/tắt/thời hạn | Cần server; kiểm thử server dùng dữ liệu thử |
| Giá/thời tiết | Giá thị trường có nguồn/cache; dự báo thời tiết và nguy cơ theo quy tắc | Có client/backend lấy nguồn ngoài; chưa xác minh độ sẵn sàng live hiện tại |
| Nước/đất | Sổ kết quả đo, môi trường và diễn giải theo dữ liệu | Không thấy bằng chứng tích hợp cảm biến IoT tự động trong luồng đã kiểm tra |
| Trợ lý | Trả lời câu hỏi về cây, vấn đề, quá hạn, số lượng, sản lượng; dẫn về bản ghi, tạo công việc nháp khi xác nhận | `Assistant.answer` là truy vấn/quy tắc xác định trên hồ sơ cục bộ, chưa phải chatbot LLM tổng quát |
| Vệ tinh | NDVI/EVI/NDMI, nước mặt, chuỗi thời gian, lớp ảnh, phân tích vùng và tổng hợp vườn | Có client và endpoint Earth Engine/mock; cần xác minh nguồn và cờ từng kết quả |
| GIS nâng cao | Công cụ hình học/chuyển đổi/xuất dữ liệu, nguồn GIS mở, routing, vùng tiếp cận, bản đồ chuyên đề, DEM/độ dốc và phân tích địa hình | Một số xử lý cục bộ, một số gọi backend/thư viện/nguồn ngoài; không phải tất cả đều dùng offline |
| DeepForest | Chọn ảnh hoặc ảnh nền, dự đoán đối tượng/tán cây, lớp kết quả; có luồng fine-tune | Có đường inference mô hình thật; cần runtime/trọng số và dữ liệu phù hợp, chưa xác minh độ chính xác thực địa |
| Super-resolution | Tạo job nâng độ phân giải, theo dõi trạng thái, xem/tải đầu ra | Có engine SEN2SRLite x4 với kiểm tra trọng số; pixel suy luận không phải phép đo mới; cần model và ảnh đầu vào hợp lệ |
| Drone | Sổ chuyến bay/nhập log, chọn ảnh, bản đồ đa phổ/VRA và báo cáo ước tính | Phần đa phổ/VRA hiện mô phỏng nhiều bước; chưa có bằng chứng điều khiển drone/phun thật |

Điểm nối: `E:\Android\app\src\main\kotlin\com\example\test_tablet\ui\vwing\VWingApp.kt` khai báo menu và routes. Logic cục bộ ở `data\vwing\Store.kt`; các client riêng ở `data\vwing`.

## Satellite riêng: 7 lựa chọn phân tích

| Lựa chọn | Đã có trong Activity | Giới hạn |
|---|---|---|
| Tổng hợp vườn | Ước tính số cây, độ phủ tán, chỉ số đất/tán, lớp phủ đất, địa hình và khuyến nghị | Ước tính số cây không phải kiểm kê từng cây; marker mẫu không phải hồ sơ cây đã đo |
| NDVI | Chọn kỳ ảnh, thống kê, ảnh phủ bản đồ | Cần backend; phân biệt mock/live |
| EVI | Tương tự NDVI | Cần backend |
| NDMI | Chỉ số liên quan nước trong tán | Không phải đo trực tiếp độ ẩm đất |
| Nước mặt Sentinel-1 | So sánh kỳ nền/kỳ sự kiện, nước mới/thường xuyên/đã rút | Chưa kiểm chứng thực địa trong lần này |
| Hạn hán 2000–2025 | Báo cáo NDVI/VCI/LST/TCI/VHI và chuỗi 26 năm | Phần số liệu chính đang được tạo mô phỏng trong backend, kể cả mode live |
| Drone đa phổ & VRA | Hiển thị 5 chỉ số, số cây/sản lượng, vùng phun/tiết kiệm và thông tin bảo hiểm | Phần chính là mô phỏng/công thức, không phải điều khiển hay chứng thư thật |

Tính năng phụ: nhấn giữ thêm đỉnh ranh, xóa/vùng mẫu, chọn tọa độ mẫu, 9 nhóm cây trồng, khoảng cách cây tùy chỉnh, kỳ ngày và kỳ nền nước, URL/token backend, ảnh phủ Google Maps và thanh độ trong suốt, thống kê/nguồn/cảnh báo, hướng dẫn kỹ thuật, giữ một phần trạng thái khi Activity được tạo lại.

**Chưa thấy trong app Satellite riêng:** tài khoản/vai trò nhiều người, quản lý nhiều hồ sơ vườn/cây lâu dài, nhật ký chăm sóc, QR, công việc, đồng bộ hồ sơ, market/weather UI, DeepForest/SR UI. Các endpoint backend có tồn tại không có nghĩa Activity này đã có nút gọi.

Mã: `app/src/satellite/java/com/utc/ag/satellite/SatelliteActivity.java:168` (7 lựa chọn), `:407` (request), `:492` trở đi (render). API và ảnh: `SatelliteApi.java`.

## Những tính năng cần ghi rõ mức độ hoàn thiện

1. **Hạn hán:** `vwing-satellite-backend/app/drought_monitoring.py:223` tạo chuỗi theo công thức; `:760` luôn sinh lưới mô phỏng. Mode live chỉ thử DEM thật, có fallback mock. `simulated = mode != "live"` ở `:941` chưa phản ánh nguồn của toàn bộ kết quả. Không thể coi chuỗi 26 năm là dữ liệu MODIS/CHIRPS thật chỉ vì nhãn LIVE.
2. **Drone đa phổ/VRA:** `drone_multispectral.py:277` nhận giá trị trung bình ảnh hoặc hằng số; `:289` dựng hàng cây/vùng stress giả; số cây/sản lượng/tiết kiệm/bảo hiểm có phép tính giả định. Chọn ảnh thật chưa đồng nghĩa xử lý orthomosaic đa phổ có tọa độ theo từng pixel. Không có bằng chứng hiệu chuẩn DLS, RTK, chẩn đoán sâu bệnh, xuất nhiệm vụ đã được drone chấp nhận hoặc điều khiển phun thật.
3. **AI:** `E:\Android\app\src\main\kotlin\com\example\test_tablet\data\vwing\Assistant.kt:54` là engine truy vấn hồ sơ. DeepForest/SR là các mô hình chuyên biệt khác, không đồng nghĩa trợ lý hội thoại đã dùng LLM.
4. **Công cụ FarmInsights/FlightOps phụ:** `MainActivity.kt` có đường mở từ thiết lập; nhiều màn dùng `data/SampleData.kt` (ví dụ Farmer Dashboard, Ops UploadStatus). Xếp là prototype/dữ liệu mẫu, không coi là quy trình bay/upload sản xuất đã hoàn tất.
5. **Satellite KPI:** vẫn có giá trị khởi tạo 87,5%, mặc định 76%/90%, tối thiểu hai vạch và chưa xóa KPI khi đổi vùng. Xem `app/APP_ERROR_CHECK_2026-10-23.md`.
6. **Flight log:** `data/vwing/DjiFlightLog.kt` tự ghi rõ giới hạn ánh xạ field; giải mã Protobuf thô không chứng minh đã hiểu đúng toàn bộ log DJI thật.

## Xác minh trong lần này

- Node sync server: **160 passed, 0 failed**. Log `logs/vwing-feature-check-20261023-sync.log`.
- VWing Android: `assembleDebug` hoàn tất (UP-TO-DATE). Tổng 223 test: **211 passed, 9 failed, 3 skipped**; lệnh tổng thể BUILD FAILED do test. Cả 9 lỗi thuộc VWingComposeUiTest: không tìm thấy nhãn/thành phần UI mong đợi hoặc không cuộn tới nút địa hình. Chưa kết luận là tính năng hỏng; cần đối chiếu test với UI mới. Log `logs/vwing-feature-check-20261023-android.log`; danh sách lỗi `logs/vwing-feature-check-20261023-android-failures.csv`; báo cáo HTML ở `E:\Android\app\build\reports\tests\testDebugUnitTest\index.html`.
- Backend phân tích: **287 passed, 1 failed** trong 158,13 giây. Test `test_catalog_lists_tree_bird_livestock_and_no_invented_model` mong đúng 3 model nhưng catalog hiện thêm checkpoint fine-tune `ft-1569bc4faa0f`; đây là khác biệt trạng thái catalog/test, không phải bằng chứng inference crash. Log `logs/vwing-feature-check-20261023-backend.log`.
- Satellite: trong lượt kiểm tra trước cùng ngày, assembleSatelliteDebug thành công, unit test NO-SOURCE, HTTP mock NDVI/EVI/NDMI/water đạt 4/4. Không dùng kết quả đó để khẳng định mọi tính năng nâng cao chạy thật.

Chưa cài/reinstall, chưa thử camera/GPS/QR trên thiết bị, chưa gọi GEE live hoặc gửi SMS thật, chưa kiểm chứng kết quả nông học ngoài thực địa. Không sửa mã ứng dụng trong lần kiểm kê này.

