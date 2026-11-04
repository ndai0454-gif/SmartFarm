# Sentinel-2 Super-Resolution trong VWING

Ngày tích hợp: 13/10/2026. UI trong Bản đồ vườn → Phân tích vùng đã chọn → **Super-Resolution**.

## Ý nghĩa độ phân giải

- Ảnh nguồn: bốn băng phản xạ Sentinel-2 L2A Harmonized, RGB+NIR ở 10 m.
- Mô hình thực: **ESAOpenSR SEN2SRLite NonReference_RGBN_x4**, SPAN và Fourier hard constraint, chạy trên CPU backend.
- Đầu ra học máy: lưới **2,5 m**. Tùy chọn xuất **1 m** là nội suy bilinear từ kết quả 2,5 m, được ghi rõ trong app, TIFF và JSON.
- Đây không phải quan sát gốc 1 m, không tạo cao độ/DEM, LiDAR hoặc kết luận từng cây. Chưa có ảnh độ phân giải cao tại vườn để kiểm chứng độ chính xác chi tiết suy diễn.
- Phần NDVI/EVI/NDMI hiện có tiếp tục dùng dữ liệu gốc; không tự thay số liệu bằng ảnh AI.

Tham khảo chính: [SEN2SR](https://github.com/ESAOpenSR/SEN2SR), [model metadata](https://huggingface.co/tacofoundation/SEN2SR/blob/main/SEN2SRLite/NonReference_RGBN_x4/mlm.json), [Sentinel-2 SR Harmonized](https://developers.google.com/earth-engine/datasets/catalog/COPERNICUS_S2_SR_HARMONIZED). S2DR3/S2DR4 của Gamma Earth là dịch vụ khác, yêu cầu tài khoản/API key và điều kiện riêng; chưa tích hợp dịch vụ đó: https://help.gamma.earth/docs/general-knowledge/

## Pipeline

1. Gửi đúng polygon và kỳ ngày (kết thúc không tính), chỉ dùng backend live.
2. Chọn một cảnh có độ phủ không mây tốt trong tối đa 10 cảnh ít mây; không dùng ảnh PNG chỉ số làm đầu vào AI. Ảnh gốc và ảnh AI trong so sánh luôn cùng cảnh, cùng cách hiển thị màu; không so với ảnh ghép trung vị của chức năng GIS.
3. Tải khung ngữ cảnh UTM **128×128 pixel / 10 m** gồm B04,B03,B02,B08 và mặt nạ hợp lệ. Chuyển DN về phản xạ ×0,0001. Điểm không hợp lệ điền 0 cho mô hình nhưng tiếp tục giữ nodata ở đầu ra.
4. Chạy mô hình có trọng số pretrained thật, thành 512×512 / 2,5 m; nếu chọn lưới 1 m mới nội suy lên 1280×1280.
5. Cắt theo polygon, lưu GeoTIFF 4 băng float32 phản xạ, UTM, nodata NaN. Không gọi Z hoặc ảnh màu là dữ liệu cao độ.
6. Xuất hai PNG với cùng khung WGS84 và cùng phép hiển thị gamma/stretch. JSON ghi nguồn/cảnh/ngày, hash trọng số, phiên bản, kích thước pixel gốc/mô hình/xuất, CRS và cảnh báo.

Giới hạn: 0,01–25 ha, khung vùng tối đa 960×960 m để còn ngữ cảnh; kỳ tối đa 93 ngày; tối thiểu 50% vùng và ngữ cảnh có dữ liệu hợp lệ. Mây/thiếu dữ liệu vẫn là nodata; chi tiết ở lân cận có thể bị ảnh hưởng. UTM hỗ trợ 80° Nam–84° Bắc, không qua kinh tuyến 180°.

## Cài backend và trọng số

Từ `E:\New folder\SmartFarm\vwing-satellite-backend`:

```powershell
.\.venv\Scripts\python.exe -m pip install -r requirements-sr.txt
.\.venv\Scripts\python.exe setup_sr.py
```

PyTorch CPU được ghim 2.8.0, SEN2SR 0.8.5. Trọng số khoảng 3,36 MB nằm ở `models/sen2srlite-rgbn-x4`, pin revision `469e9b3a049f9320ba66b3a39b9655dbd31480b3`. Chỉ tải safetensors/metadata từ revision này; không thực thi load.py tải từ mạng. SHA-256 đã cố định và kiểm tra lúc cài/lúc nạp. Model cache và artifacts đã ignore.

Khởi động live như hướng dẫn `REGION_ANALYSIS.md`, một uvicorn worker. Thông tin Earth Engine và token ở backend; Android không chứa trọng số hoặc thông tin xác thực Earth Engine.

## API và tải file

- `POST /v1/satellite/super-resolution`: `{region:{ring:[[lng,lat],...]},start,end,max_cloud_pct,output_pixel_size_m:2.5|1.0}` → HTTP202 và job ID.
- `GET /v1/satellite/super-resolution/{job_id}`: queued/running/succeeded/failed.
- `GET /v1/satellite/super-resolution/{job_id}/files/{filename}`: whitelist original.png, enhanced.png, original.tif, enhanced.tif, metadata.json.

Các API cần Bearer token; backend mock từ chối SR. Một model worker, tối đa hai job đang chạy/chờ; giới hạn lưu 20 job hoặc 24 giờ. Job theo phiên server; restart làm mất trạng thái, lần submit sau sẽ xóa artifact phiên trước. Tải file cần hoàn tất trước khi hết hạn, app có thể tạo lại job. Không dùng nhiều uvicorn worker với một kho artifact. File tải bị giới hạn kích thước và không theo redirect mang token.

## Bằng chứng xử lý thật

`verify_sr_live.py` đã chạy job và tải đủ PNG/GeoTIFF/JSON cho đúng polygon đang lưu trên tablet (~0,37 ha), kỳ yêu cầu 2026-09-13 → 2026-10-13:

- Cảnh được chọn: `20260918T030521_20260918T032310_T48PZT`, quan sát ngày **18/09/2026**, độ phủ hợp lệ trả về **100%**.
- TIFF gốc: 4 băng, pixel 10 m. TIFF AI: **33×37 pixel / 2,5 m**. TIFF tùy chọn: **80×91 pixel / 1 m**, có nhãn nội suy từ 2,5 m.
- Kiểm tra CRS UTM, affine pixel size, số băng, nodata, mặt nạ polygon và PNG trước/sau cùng kích thước thành công.
- Backend: **46 tests đạt**, gồm 4 kiểm tra engine (một smoke nạp trọng số thật) và các kiểm tra API/xếp hàng/xác thực/tải file. Smoke model CPU đầu tiên khoảng 1,9 giây, không bao gồm tải ảnh Earth Engine.
- Android: build APK và **102 tests đạt**, không fail/error/skip. Đã cài cập nhật trên TB321FU và tạo ảnh thật từ tab Super-Resolution. Xoay dọc → ngang giữ tab, vùng và cùng tác vụ; ảnh được tải lại theo job ID. Trạng thái này được hoist trên bố cục thích ứng qua `MapAnalysisSession`, không lưu lại draft của các form ghi hoạt động.
- Đã bấm **Tải GeoTIFF AI**, lưu bằng bộ chọn tài liệu Android vào `Download/VWING-AI-2026-09-18-2.5m.tif` trên tablet. Kéo tệp đã lưu về và đọc bằng rasterio xác nhận 33×37, 4 băng, EPSG:32648, pixel 2,5 m và tag inferred=true. Bản kiểm tra: `E:\Android\app\build\outputs\super-resolution\tablet-export.tif`. Chưa kiểm tra xoay máy khi bộ chọn nơi lưu đang mở.
- SHA-256 của dữ liệu vườn trước/sau cập nhật khớp; không thay đổi dữ liệu cây/hoạt động để thử mô hình.
- Artifact tại `E:\New folder\SmartFarm\vwing-satellite-backend\artifacts\verification\sr-2.5m` và `sr-1.0m`. `comparison.png` so sánh đúng hai ảnh cùng cảnh; không phải bằng chứng độ chính xác so với thực địa.

Mã backend: `app/sr_engine.py`, `app/sr_jobs.py`; cài model: `setup_sr.py`. Mã Android: `data/vwing/SuperResolution.kt`, `ui/vwing/SuperResolutionPanel.kt`, `MapAnalysisSession.kt`, tích hợp tại `RegionAnalysisPanel.kt`.
