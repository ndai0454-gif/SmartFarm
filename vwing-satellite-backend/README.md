# VWING Satellite API

Backend Python (FastAPI) cho phần vệ tinh của VWING: **NDVI / EVI / NDMI** từ Sentinel-2 (qua `earthengine-api` + `eemont`) và **nước mặt / ngập** từ Sentinel-1 (qua `HYDRAFloods`). Ứng dụng Android gọi API này; thông tin xác thực Earth Engine chỉ nằm ở máy chủ.

## Thư viện đã cài (trong `.venv`, ghim phiên bản ở `requirements.txt`)

| Thư viện | Vai trò | Giấy phép |
|---|---|---|
| earthengine-api | kết nối Earth Engine | Apache-2.0 |
| eemont | `scaleAndOffset()`, `spectralIndices()` trên Sentinel-2 | MIT |
| spyndex | tính chỉ số bằng NumPy (kiểm thử, mô phỏng) với bộ công thức awesome-spectral-indices | MIT |
| hydrafloods | `Sentinel1`, `edge_otsu` cho nước mặt | MIT |
| fastapi, uvicorn | API | MIT / BSD |

`reference/awesome-spectral-indices` (danh mục công thức) và `reference/gee_s1_ard` (tiền xử lý Sentinel-1) chỉ là bản sao để tham khảo, **chưa đưa vào pipeline**. `android-maps-compose` chưa dùng: ứng dụng vẽ bản đồ bằng khung riêng.

## Hai chế độ

- `VWING_EE_MODE=mock` (mặc định): trả dữ liệu **mô phỏng** xác định, luôn có `simulated: true` và nguồn "MÔ PHỎNG". Dùng để thử giao diện khi chưa có tài khoản.
- `VWING_EE_MODE=live`: gọi Earth Engine thật. Cần `EE_PROJECT` và xác thực phía máy chủ.

## Chạy thử (mô phỏng)

```powershell
cd vwing-satellite-backend
$env:VWING_SAT_TOKEN = "chuoi-bi-mat-it-nhat-12-ky-tu"
$env:VWING_EE_MODE = "mock"
.\.venv\Scripts\python.exe -m uvicorn app.main:app --host 0.0.0.0 --port 8000
```

Địa chỉ trong app (Thiết lập vườn → Vệ tinh): `http://<IP máy>:8000`, kèm mã truy cập trên. Với emulator dùng `http://10.0.2.2:8000`.

## Chạy với Earth Engine thật

1. Tạo/chọn Cloud project, bật Earth Engine API, đăng ký mục đích sử dụng (noncommercial hoặc commercial; **điều kiện, hạn mức và chi phí của Earth Engine áp dụng riêng**).
2. Xác thực phía máy chủ: `.\.venv\Scripts\earthengine.exe authenticate` (thử cục bộ) hoặc service account/ADC khi triển khai Cloud Run. Không đưa JSON service account vào APK hay vào kho mã.
3. `$env:VWING_EE_MODE = "live"; $env:EE_PROJECT = "id-project-da-dang-ky"` rồi chạy uvicorn.

> **Chưa kiểm chứng:** mô-đun `app/ee_service.py` đã được viết theo API của eemont/HYDRAFloods đã cài (chữ ký hàm đã đối chiếu), nhưng **chưa chạy với tài khoản Earth Engine thật** trong dự án này. Trước khi dùng, đối chiếu cùng polygon/ngày giữa API và Code Editor (NDVI trung bình, số cảnh, độ phủ hợp lệ) và thử các trường hợp: khoảng ngày không có cảnh, toàn mây, ranh giới lớn.

## Điểm API

| Phương thức | Đường dẫn | Ghi chú |
|---|---|---|
| GET | `/v1/health` | không cần mã |
| GET | `/v1/satellite/indices` | danh mục chỉ số, băng, lưu ý |
| POST | `/v1/satellite/index` | ảnh ghép trung vị + thống kê + ảnh phủ bản đồ |
| POST | `/v1/satellite/timeseries` | chuỗi theo ngày (ngày thiếu ảnh bị bỏ, không thành 0) |
| POST | `/v1/satellite/water-change` | nước mặt kỳ nền so với kỳ sự kiện (Sentinel-1) |
| POST | `/v1/routing/isochrone` | vùng phục vụ theo mạng đường: tối đa 5 điểm, 4 mốc 1–60 phút, đi bộ/xe đạp/xe máy/ô tô (Valhalla) |
| POST | `/v1/routing/matrix` | thời gian và quãng đường giữa tối đa 10×10 điểm theo mạng đường; `null` = không có đường |
| POST | `/v1/terrain/dem` | lưới độ cao SRTM 30 m quanh vùng + vùng đệm (≤ 3 km) để app tính tầm nhìn và nhạy cảm ngập lụt |

Tất cả (trừ health) cần `Authorization: Bearer <VWING_SAT_TOKEN>`. Ranh giới là vòng GeoJSON `[[kinh độ, vĩ độ], …]` (kinh độ trước); diện tích tối đa 500 ha, khoảng ngày tối đa 93 ngày (ảnh ghép) / 400 ngày (chuỗi).

## Phương pháp và giới hạn

- Sentinel-2 `COPERNICUS/S2_SR_HARMONIZED`; loại mây/bóng/tuyết bằng lớp SCL (0, 1, 3, 8, 9, 10, 11); `scaleAndOffset()` đưa DN về phản xạ **trước** khi tính chỉ số (EVI cần đúng thang đo).
- Ảnh ghép là **trung vị trong khoảng ngày**, không phải ảnh của một ngày. Kết quả luôn kèm số cảnh, ngày, độ phủ hợp lệ, nguồn, phương pháp và cảnh báo.
- Điểm ảnh 10 m: **không đủ để kết luận từng cây** hay bệnh. NDVI/EVI chỉ phản ánh độ xanh tán; NDMI là độ ẩm tán lá, không phải độ ẩm đất vùng rễ.
- Nước/ngập: Sentinel-1 VV, ngưỡng edge-Otsu; nước mặt mới xuất hiện **không chứng minh thiệt hại cây trồng**. Chưa lọc speckle/hiệu chỉnh địa hình (gee_s1_ard); nếu bổ sung, kiểm tra để không lọc lặp.
- Ứng dụng gửi ranh giới trong thân yêu cầu; khi có CSDL vườn riêng, hãy đọc ranh giới ở server và kiểm tra quyền truy cập vườn.
- Bộ nhớ đệm trong tiến trình (theo ranh giới + tham số + phiên bản thuật toán). URL ảnh của Earth Engine hết hạn; ứng dụng phải tải lại bằng cách gọi lại API.

## Kiểm thử

```powershell
.\.venv\Scripts\python.exe -m pytest -q tests
```
17+ test: công thức NumPy khớp công bố và danh mục awesome-spectral-indices, EVI phải dùng phản xạ, kiểm tra ranh giới (đảo kinh/vĩ, tự cắt, giới hạn diện tích), xác thực, kết quả mô phỏng có nhãn, chuỗi không biến thiếu thành 0, ngày không hợp lệ.

## Đường đi (Valhalla) và địa hình (SRTM)

- **Định tuyến:** `app/routing.py` gọi Valhalla (mặc định máy chủ công cộng `https://valhalla1.openstreetmap.de`, đổi bằng `VALHALLA_URL`). **Toạ độ điểm xuất phát/đích được gửi tới máy chủ cộng đồng OSM**; muốn giữ nội bộ thì tự chạy Valhalla. Máy chủ công cộng giới hạn 60 phút/vùng và chỉ dành cho dùng vừa phải (backend đã đệm kết quả 6 giờ và giới hạn 30 yêu cầu/phút). Thời gian là ước tính theo dữ liệu OSM và tốc độ mặc định, không tính kẹt xe/thời tiết.
- **Địa hình:** `app/dem.py` lấy `USGS/SRTMGL1_003` qua Earth Engine (chế độ live; mock trả địa hình mô phỏng `simulated: true`). Lưới căn theo **tâm ô** SRTM, đã đối chiếu 15/15 ô với lấy mẫu độc lập. SRTM là độ cao bề mặt (gồm tán cây/nhà), ô ~30 m, sai số đứng vài mét.
- Tầm nhìn (viewshed) và nhạy cảm ngập lụt (TWI + HAND) được tính trong app (`Terrain.kt`), không tính ở máy chủ. Đây là chỉ số địa hình, **không phải mô hình thủy lực**.
