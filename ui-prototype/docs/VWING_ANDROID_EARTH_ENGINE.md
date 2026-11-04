# Tích hợp Google Earth Engine vào VWING Android

Hướng dẫn cho Android native (Kotlin, Android Studio), ngày 13/10/2026. Đây là ví dụ để triển khai thử, chưa được kết nối tài khoản GEE hay chạy trên thiết bị. Không cần chuyển app sang React Native.

## 1. Kiến trúc

Android: Google Maps SDK (hoặc Maps Compose), polygon vườn, marker cây, lớp NDVI, biểu đồ.

Backend Python: xác thực người dùng, kiểm tra quyền truy cập vườn, đọc ranh giới từ DB, gọi Earth Engine, trả tile URL và thống kê.

Earth Engine: chọn ảnh Sentinel-2, loại mây, tính chỉ số, tổng hợp theo ranh giới vườn.

Google Maps là bản đồ hiển thị. Earth Engine là nền tảng xử lý dữ liệu địa lý. Khóa Maps Android khác với thông tin xác thực Earth Engine; không đặt service-account JSON trong APK.

## 2. Chọn nội dung khóa học

| Bài | Áp dụng VWING | Ưu tiên |
|---|---|---|
| 1–10 | Tài khoản/project, server objects, lọc ảnh, raster/vector, hiển thị | Học trước |
| 11, 17 | NDVI và chuỗi thời gian theo vườn/khu | MVP |
| 12–16 | EVI, chỉ số nước/ẩm: chọn đúng công thức, băng và mục đích | Sau NDVI |
| 18–19 | Chỉ số ẩm hoặc dữ liệu mô hình độ ẩm; kiểm tra dataset, độ sâu, đơn vị, độ phân giải | Thử nghiệm |
| 20–23 | LST/AOD, bối cảnh nhiệt và khí quyển | Sau MVP |
| 24–28 | Khí quyển vùng rộng; không coi cột khí vệ tinh là nồng độ hít thở tại vườn | Thấp |
| 29–30 | DEM, slope, aspect cho địa hình | Sau MVP |
| 31–35 | Phân loại đất với mẫu chuẩn và đánh giá độc lập | Khi mở rộng vùng |

NDWI có nhiều định nghĩa; với độ ẩm thảm thực vật cần ghi rõ nếu dùng (NIR-SWIR)/(NIR+SWIR), thường gọi NDMI. EVI có hằng số nên phải chuyển DN Sentinel-2 sang phản xạ theo scale trước khi tính. LST là nhiệt độ bề mặt, không phải nhiệt độ không khí. Tên “soil moisture index” chưa chứng minh đo được độ ẩm vùng rễ; cần đối chiếu thực địa.

## 3. Chuẩn bị Google Cloud

1. Tạo hoặc chọn Cloud project, bật Earth Engine API, đăng ký mục đích sử dụng và hoàn thành xác minh nếu được yêu cầu.
2. Mở Code Editor để thử dữ liệu/thuật toán; mã JavaScript ở đây không được dán trực tiếp vào Kotlin.
3. Tạo backend Python. Khi triển khai Cloud Run, dùng service account gắn với dịch vụ và Application Default Credentials. Cấp quyền Earth Engine/Service Usage phù hợp, không dùng Owner để giải quyết lỗi quyền.
4. Bật Maps SDK for Android, thiết lập billing theo yêu cầu Maps, tạo Maps API key giới hạn theo package + SHA-1 và API. Dùng hướng dẫn Quickstart hiện tại để chọn phiên bản SDK tương thích app.

Earth Engine và Maps có điều kiện sử dụng/quota/chi phí riêng. Không mặc định app thương mại được sử dụng Earth Engine miễn phí. Noncommercial có tier, hạn mức và xác minh theo chính sách hiện hành.

## 4. Backend thử nghiệm NDVI

Tạo thư mục backend riêng, không đặt mã Python vào module Android. Cài:

```powershell
py -m venv .venv
.\.venv\Scripts\python.exe -m pip install earthengine-api fastapi uvicorn
.\.venv\Scripts\earthengine.exe authenticate
$env:EE_PROJECT = 'your-registered-project-id'
```

Lưu `main.py` bên dưới. Đây là lab dùng một polygon giả gần Bảo Lộc, không đại diện ranh giới vườn của bạn. Chỉ chạy loopback để thử. Endpoint chưa có auth nên không triển khai công khai nguyên trạng.

```python
import os
import ee
from datetime import date
from fastapi import FastAPI, HTTPException

ee.Initialize(project=os.environ["EE_PROJECT"])
app = FastAPI()

# GeoJSON/Earth Engine: kinh độ trước, vĩ độ sau.
AOI = ee.Geometry.Polygon([[
    [107.8000, 11.5500], [107.8010, 11.5500],
    [107.8010, 11.5510], [107.8000, 11.5510],
    [107.8000, 11.5500]
]])

def ndvi_image(image):
    scl = image.select("SCL")
    # Mask mẫu: loại no-data, defective, bóng mây, mây/cirrus/tuyết.
    # Không phải thuật toán loại mây hoàn hảo; cần đánh giá tại vườn.
    clear = (scl.neq(0).And(scl.neq(1)).And(scl.neq(3))
             .And(scl.neq(8)).And(scl.neq(9))
             .And(scl.neq(10)).And(scl.neq(11)))
    masked = image.updateMask(clear)
    ndvi = masked.normalizedDifference(["B8", "B4"]).rename("NDVI")
    return ndvi.copyProperties(image, ["system:time_start"])

@app.get("/demo/ndvi")
def get_ndvi(start: date, end: date):
    if end <= start or (end - start).days > 93:
        raise HTTPException(400, "Khoảng ngày phải từ 1 đến 93 ngày")
    scenes = (ee.ImageCollection("COPERNICUS/S2_SR_HARMONIZED")
              .filterBounds(AOI)
              .filterDate(start.isoformat(), end.isoformat())
              .filter(ee.Filter.lt("CLOUDY_PIXEL_PERCENTAGE", 80)))
    count = scenes.size().getInfo()
    if count == 0:
        raise HTTPException(404, "Không có cảnh ảnh trong khoảng ngày")
    composite = scenes.map(ndvi_image).median().clip(AOI)
    mean = composite.reduceRegion(
        reducer=ee.Reducer.mean(), geometry=AOI,
        scale=10, maxPixels=1000000
    ).getInfo().get("NDVI")
    if mean is None:
        raise HTTPException(404, "Không còn pixel hợp lệ sau khi loại mây")
    map_info = composite.getMapId({
        "min": -0.2, "max": 0.9,
        "palette": ["brown", "yellow", "green"]
    })
    return {
        "tileUrl": map_info["tile_fetcher"].url_format,
        "meanNdvi": mean,
        "source": "COPERNICUS/S2_SR_HARMONIZED",
        "start": start.isoformat(), "endExclusive": end.isoformat(),
        "sceneCount": count,
        "method": "median of per-scene NDVI after SCL mask",
        "nominalPixelSizeMeters": 10
    }
```

Chạy và mở Swagger:

```powershell
.\.venv\Scripts\python.exe -m uvicorn main:app --host 127.0.0.1 --port 8000
```

Mở `http://127.0.0.1:8000/docs`, thử `/demo/ndvi?start=2026-08-01&end=2026-09-01`. Kết quả là NDVI tổng hợp trong khoảng ngày; không gắn nhãn đó là ảnh chụp của một ngày cụ thể. `sceneCount` chỉ đếm cảnh đầu vào, không phải tỷ lệ pixel sạch.

## 5. Hiển thị trong Android

Thiết lập Google Maps theo Quickstart, khai báo INTERNET trong manifest. Với GoogleMap đã sẵn sàng, gắn overlay bằng hàm Kotlin sau (các import thuộc `com.google.android.gms.maps` và `.model`, thêm `java.net.URL`):

```kotlin
fun showNdviLayer(map: GoogleMap, template: String): TileOverlay? {
    val provider = object : UrlTileProvider(256, 256) {
        override fun getTileUrl(x: Int, y: Int, zoom: Int): URL? {
            val url = template
                .replace("{z}", zoom.toString())
                .replace("{x}", x.toString())
                .replace("{y}", y.toString())
            return URL(url)
        }
    }
    return map.addTileOverlay(
        TileOverlayOptions().tileProvider(provider).transparency(0.25f)
    )
}
```

Luồng: Retrofit/OkHttp lấy JSON từ backend trên IO dispatcher → nhận `tileUrl` → gọi hàm trên main thread. Đây là hàm tích hợp cho Maps SDK, không phải một Activity hoàn chỉnh. Nếu dùng Compose, quản lý overlay qua MapEffect và lifecycle theo phiên bản Maps Compose của app; tránh tạo lại overlay mỗi lần recomposition. Khi đổi vườn/ngày, `remove()` overlay cũ rồi thêm overlay mới. `LatLng` của Google Maps nhận vĩ độ trước, kinh độ sau, khác thứ tự GeoJSON.

Emulator Android thường truy cập máy host qua `10.0.2.2`. Với lab HTTP cần cấu hình network-security cho debug; bản phát hành dùng HTTPS. Điện thoại thật cần backend có địa chỉ truy cập được. Không đưa credential Earth Engine vào Android để sửa lỗi kết nối.

Tile URL không phải định danh kết quả lưu lâu dài. Khi URL không còn hoạt động, gọi backend tạo lại; không cố định thời hạn nếu chưa có hợp đồng API. Nếu dữ liệu vườn riêng tư, dùng tile proxy có xác thực hoặc URL ký ngắn hạn của hệ thống; không xem URL trực tiếp như cơ chế kiểm soát quyền người dùng.

## 6. Chuyển lab thành tính năng app

- Đổi endpoint thành `GET /gardens/{id}/satellite/ndvi?start=...&end=...`; lấy polygon đã kiểm tra từ DB, xác thực và kiểm tra quyền garden ở server.
- Cache theo garden + phiên bản boundary + khoảng ngày + dataset + phiên bản thuật toán. Không gọi Earth Engine lại mỗi lần người dùng kéo bản đồ.
- Tác vụ nặng dùng job queue, trạng thái pending/ready/failed; giới hạn diện tích, khoảng thời gian và tần suất.
- Bổ sung tỷ lệ diện tích hợp lệ, ngày cảnh ảnh, chú giải màu, nguồn, phương pháp và trạng thái thiếu dữ liệu.
- Endpoint time series trả điểm theo cảnh hoặc kỳ tổng hợp, cùng valid coverage; không chuyển kỳ thiếu ảnh thành giá trị 0. Biểu đồ Kotlin vẽ từ JSON, không dùng `ui.Chart` của Code Editor trong app.
- Không dùng màu PNG để tính mean NDVI: thống kê phải tính trên raster số ở backend.
- NDVI giảm chỉ tạo đề nghị kiểm tra khu vực. Kết hợp nhật ký, ảnh thực địa và xác nhận của người dùng trước khi quyết định tưới/phun.

## 7. Kiểm tra trước khi đưa vào sử dụng

Đối chiếu cùng polygon/ngày/thuật toán giữa Code Editor và API; thử khoảng không có cảnh, toàn mây, hết quyền, URL tile lỗi; kiểm tra chuyển ngày không giữ overlay cũ; thử mạng chậm và user không có quyền vườn. Sentinel-2 NDVI 10 m không đủ để xác định bệnh của từng cây, dù zoom bản đồ rất lớn.

## Tài liệu chính thức

- Access/project: https://developers.google.com/earth-engine/guides/access
- Noncommercial tiers: https://developers.google.com/earth-engine/guides/noncommercial_tiers
- Service account/ADC: https://developers.google.com/earth-engine/guides/service_account
- Sentinel-2 bands/SCL: https://developers.google.com/earth-engine/datasets/catalog/COPERNICUS_S2_SR_HARMONIZED
- getMapId: https://developers.google.com/earth-engine/apidocs/ee-image-getmapid
- Maps Android setup: https://developers.google.com/maps/documentation/android-sdk/start
- Tile overlay: https://developers.google.com/maps/documentation/android-sdk/tileoverlay
