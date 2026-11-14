# API OBIA (phân loại theo đối tượng) — `/v1/obia/*`

Chạy động cơ **Lite Classify** thật trên máy chủ, theo mô hình *workspace + job*. Thay cho bản mô phỏng
`LiteClassifyObiaEngine.kt` trong ứng dụng (bản đó không đọc ảnh thật).

> Based on Lite Classify by L. D. Barua (LeoBar) — https://github.com/leobarua/liteclassify

## Giấy phép — đọc trước khi triển khai
- Lite Classify theo **GPLv3 + điều khoản bổ sung** (`NOTICE.md` của repo gốc). Mã của nó **không được chép vào dự án này**
  và **không đóng gói vào APK**; máy chủ chỉ `import` từ thư mục `LITECLASSIFY_DIR` bạn tự clone.
- Điều khoản bổ sung: giữ nguyên dòng ghi công ở trên (trong màn hình "Giới thiệu"/tài liệu và mọi kết quả API — API tự gắn
  trường `attribution`), đánh dấu đây là bản đã chỉnh sửa (trường `notice`), **không dùng tên "Lite Classify"/"LeoBar" để đặt tên
  tính năng** của bạn. Trong ứng dụng hãy gọi tính năng bằng tên riêng, ví dụ "Phân loại đối tượng (OBIA)".
- Theo `COMMERCIAL-LICENSING.md` của tác giả: sinh viên, nhà nghiên cứu, cơ quan, tư vấn... dùng GPL là đủ, dùng cho mọi mục đích
  (kể cả công việc có thu phí), bản đồ tạo ra thuộc về bạn. Nhưng nếu **phân phối** Lite Classify hoặc chương trình xây trên nó thì
  phải công bố toàn bộ mã nguồn theo GPL. Chạy trên máy chủ riêng của bạn thì chưa phải phân phối; còn nhúng vào sản phẩm đóng mã
  nguồn, hoặc đưa máy chủ này cho bên thứ ba, thì cần giấy phép riêng từ tác giả (ldbarua@gmail.com).

## Cài đặt
```powershell
pip install -r requirements-obia.txt
git clone https://github.com/leobarua/liteclassify E:\liteclassify
$env:LITECLASSIFY_DIR = "E:\liteclassify"
$env:VWING_OBIA_DIR   = "E:\obia-data"      # tuỳ chọn; mặc định artifacts/obia
$env:VWING_SAT_TOKEN  = "..."               # như các API khác
uvicorn app.main:app --host 127.0.0.1 --port 8000
```
`GET /v1/obia/status` cho biết động cơ có sẵn không, có CUDA / đặc trưng sâu (DINOv2) không.
Không có PyTorch/trọng số thì bước `discover` vẫn chạy bằng đặc trưng truyền thống (`deep_used:false`).

## Luồng gọi (mọi đường đều cần `Authorization: Bearer <token>`)
| Bước | Gọi | Ghi chú |
|---|---|---|
| 1 | `POST /v1/obia/workspaces` `{ "name": "Vườn A" }` | trả `id` |
| 2 | `PUT /v1/obia/workspaces/{id}/inputs/ortho` | thân = byte GeoTIFF, `Content-Type: application/octet-stream`; ≥3 băng, có CRS. Tuỳ chọn `inputs/dsm` |
| 3 | `POST .../jobs/segment` | `n_segments` (5000), `compactness`, `sigma`, `device` auto/cpu/gpu, `max_pixels`, `extent` |
| 4 | `POST .../jobs/discover` | `n_groups` (12), `use_deep`, `use_features`. Trả các nhóm + vài segment ví dụ (lon/lat) để người dùng xem và đặt tên |
| 5 | `POST .../jobs/classify` | `classes`: tên → `{groups:[..], segments:[..], points:[[x,y]..], color}`; `n_trees`, `sieve_px` |
| 6 | `POST .../jobs/sample` | tuỳ chọn: sinh điểm lấy mẫu phân tầng (Olofsson 2014) `accuracy_sample.csv` để đi thực địa gán nhãn |
| 7 | `PUT .../inputs/reference` rồi `POST .../jobs/accuracy` | CSV `x,y,class` theo **CRS của ảnh**. Trả ma trận nhầm lẫn, UA/PA/OA |

Mỗi `POST .../jobs/*` trả `202` + job. Theo dõi bằng `GET /v1/obia/jobs/{job_id}` (`status`: queued/running/succeeded/failed/cancelled,
`progress` 0–1, `message`, `result`, `error`). Huỷ: `POST /v1/obia/jobs/{job_id}/cancel`.
Một workspace chỉ chạy một job một lúc (`409`); hàng đợi đầy → `429`.

### Tệp kết quả: `GET /v1/obia/workspaces/{id}/files/{name}`
`ortho_preview.png`, `segments.geojson` (≤20 000 đối tượng), `groups_preview.png`, `classified.tif` (uint8, 0 = chưa phân lớp,
GeoTIFF có bảng màu), `classified.qml` (kiểu QGIS), `classified_preview.png`, `legend.json`, `accuracy_sample.csv`,
`accuracy_report.csv`, `accuracy_points.gpkg`. Các PNG xem trước đi kèm `bounds_wgs84` `[tây, nam, đông, bắc]` trong `result`
để phủ lên bản đồ trong app (gần đúng; dùng GeoTIFF khi cần chính xác).

### Ví dụ
```bash
H="Authorization: Bearer $TOKEN"
WS=$(curl -s -H "$H" -H 'Content-Type: application/json' -d '{"name":"Vườn A"}' $API/v1/obia/workspaces | jq -r .id)
curl -s -X PUT -H "$H" -H 'Content-Type: application/octet-stream' --data-binary @ortho.tif $API/v1/obia/workspaces/$WS/inputs/ortho
JOB=$(curl -s -H "$H" -H 'Content-Type: application/json' -d '{"n_segments":8000}' $API/v1/obia/workspaces/$WS/jobs/segment | jq -r .id)
curl -s -H "$H" $API/v1/obia/jobs/$JOB
```

## Lưu trữ và giới hạn
- Workspace nằm ở `VWING_OBIA_DIR/<uuid>/{input,state,out,jobs}`; tự xoá sau 72 giờ không dùng (không xoá khi đang có job).
  Kết quả job còn đọc được sau khi khởi động lại; job đang chạy lúc tắt máy báo `failed: interrupted`.
- Tải lên tối đa 600 MB mỗi tệp (`413` nếu vượt). Ảnh lớn hơn giới hạn RAM sẽ được đọc ở độ phân giải thấp hơn
  (`load_reduction` trong kết quả segment cho biết hệ số).
- Chưa làm: tinh chỉnh theo độ xanh (greenness), gộp/tách nhóm tương tác, DSM chưa thử, WebODM client.

## Kiểm thử đã chạy / chưa chạy
`tests/test_obia_api.py`: truyền tải/lưu trữ/job bằng runner giả, và một bài chạy engine thật trên **ảnh GeoTIFF tổng hợp**
(4 vùng màu). Test này chứng minh đường ống nối đúng; **không** chứng minh chất lượng phân lớp trên ảnh drone thật,
đặc trưng sâu DINOv2/GPU, DSM, hay chạy trên Windows.
