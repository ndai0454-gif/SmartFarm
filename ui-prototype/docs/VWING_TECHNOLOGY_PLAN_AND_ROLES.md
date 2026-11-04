# VWING — Technology Plan và User Roles & Features

Ngày: 17/10/2026 (cập nhật trạng thái 18/10/2026 sau khi hoàn thành tài khoản/RBAC, GIS RustGIS, Photon, Cloudflare Tunnel). Cơ sở: (1) dịch ngược DJI SmartFarm/Agras (`com.utc.ag` 4.20.0 và `com.dji.agflow` 4.6.13), (2) prototype web `ui-prototype/`, (3) ứng dụng Android VWING hiện có ở `E:\Android` (package `com.example.test_tablet`), (4) `VWING_IMPROVEMENT_PLAN.md` và slide Market Research Group 23.

**Mức bằng chứng dùng trong tài liệu:** `[XÁC MINH]` = có trong mã/chuỗi đã đọc hoặc đã chạy được; `[SUY LUẬN]` = suy ra từ tên chức năng, chưa thấy mã thực thi; `[ĐỀ XUẤT]` = đề xuất cho sản phẩm thật, chưa làm.

---

## PHẦN A. Kết quả dịch ngược DJI SmartFarm (nền tảng để thiết kế)

DJI SmartFarm là app React Native (bundle Hermes `index.android.bundle`, ~9,7 MB) chạy trên nền Android, phục vụ **dịch vụ bay phun/rải bằng drone nông nghiệp** (DJI Agras). Không build lại được từ source; chức năng được nhận diện qua tên module/route/chuỗi i18n.

### A1. Các nhóm chức năng của DJI SmartFarm `[XÁC MINH qua tên route trong bundle]`

| Nhóm | Route/chuỗi tiêu biểu | Ý nghĩa |
|---|---|---|
| Tài khoản | `account.loginflow`, `qrcodelogin`, `wechatregister`, `forgetPassword` | Đăng nhập nhiều cách (mật khẩu, QR, mạng xã hội), xác minh người thật |
| Thửa đất | `land.list`, `landDetail`, `landManagementMap`, `measure`, `rtk`, `tag`, `share`, `LandMemberShareSearch` | Quản lý thửa, đo diện tích (kể cả RTK), gắn thẻ, **chia sẻ thửa cho thành viên** |
| Thiết bị / drone | `manage.droneView`, `device.dailycheck`, `drone.changeDroneName`, `dronePartsSN`, `transferDroneActivated`, `device.Repair*`, `devicePurchase` | Danh sách drone, kiểm tra hằng ngày, đổi tên, SN linh kiện, chuyển nhượng, sửa chữa |
| Đội bay | `manage.team`, `AG_manage_team_member_add/remove/dailyCheck/weekly/monthly`, `Team.EverydayShareReport` | Tạo/giải tán đội, thêm–xoá thành viên, báo cáo ngày/tuần/tháng |
| Công việc | `job.new`, `job.weather`, `job.finish`, `job.grabbedOrders`, `job.company`, `job.response` | Tạo lịch bay, thời tiết, **đơn việc để nhận (chợ việc)**, doanh nghiệp, phản hồi |
| Nhật ký bay | `workrecord`, `FlightRecordDetail`, `FlightRecordFilter{Location,Mode,User,UsageType}`, `FlightRecordRouteReplayMap`, `flight.data` | Tra cứu, lọc theo vị trí/chế độ/người/loại việc, phát lại tuyến |
| Xếp hạng/ưu đãi | `flyer.rank`, `care.purchase/renew/accidentinsurance`, `component.dronerights*` | Xếp hạng phi công, gói bảo hiểm/bảo dưỡng, quyền lợi thiết bị |
| Thông báo | `notification.center`, `notification.cooperation` | Trung tâm thông báo, lời mời cộng tác |
| Hồ sơ | `Profile.*`, `FlyerLoan` | Thông tin cá nhân, khoản vay phi công |

### A2. Mô hình vai trò trong DJI SmartFarm `[XÁC MINH một phần]`

- Chuỗi mã **`TeamRole...`** và **`isOwnerOrLeader`** có trong bundle ⇒ có phân quyền theo đội, tối thiểu phân biệt **Chủ/Trưởng đội** với **Thành viên**. `[XÁC MINH]`
- Các route `grabbedOrders`, `job.company`, `flyer.rank` ⇒ tồn tại thêm hai nhóm: **nông dân giao việc** và **phi công/đội bay nhận việc**. `[SUY LUẬN]`
- Tên thành viên "member add/remove/dailyCheck" ⇒ trưởng đội xem được kiểm tra thiết bị hằng ngày của thành viên. `[SUY LUẬN]`
- Chưa xác định được ma trận quyền chi tiết (ai sửa/xoá thửa, ai xem nhật ký người khác) vì logic nằm trên máy chủ riêng `ag2-api.dji.com`; **không gọi API riêng của DJI** (đã quyết định ngoài phạm vi).

### A3. Định dạng dữ liệu bay `[XÁC MINH một phần]`

Hai namespace Protobuf `DJIFRProtoAG` / `DJIFRProtoSummary` được dựng lại. Chỉ tag cấp cao nhất của `FrameTimeState` được xác nhận trên wire (1 = flightControllerState, 7 = agriculturalData); tag lồng nhau (toạ độ, lưu lượng phun…) **chưa xác nhận** vì chưa có file log thật. Reader đã làm: `data/vwing/DjiFlightLog.kt` (8 test đạt).

### A4. Khác biệt cốt lõi giữa DJI SmartFarm và VWING

| | DJI SmartFarm | VWING |
|---|---|---|
| Đối tượng chính | Dịch vụ bay phun theo **thửa ruộng**, gắn với drone DJI | **Vườn cây lâu năm giá trị cao (sầu riêng, 1–5 ha)**, theo dõi **từng cây** |
| Đơn vị dữ liệu | Thửa + chuyến bay | **Cây** + sự kiện (quan sát, chăm sóc, vấn đề, thu hoạch) |
| Nguồn dữ liệu | Log drone DJI | Ghi tay, ảnh, QR, vệ tinh Sentinel, NDVI, thời tiết/đất, log DJI (nhập file) |
| AI | Không thấy trong bundle | Trợ lý trả lời **từ hồ sơ** có dẫn nguồn |
| Quyền | Đội, chủ/trưởng, thành viên | **Đã có tài khoản + RBAC theo vườn ở máy chủ** (5 vai trò, phạm vi theo khu, hết hạn, mời QR/SĐT, kiểm toán); chưa có OTP SMS |

---

## PHẦN B. TECHNOLOGY PLAN

### B1. Công cụ làm prototype (đã dùng / đang dùng)

| Mục đích | Công cụ | Trạng thái |
|---|---|---|
| Dịch ngược, nghiên cứu | jadx (Java), hermes-dec (JS Hermes), apktool; chuỗi nhị phân từ `index.android.bundle` | Đã dùng `[XÁC MINH]` |
| Prototype giao diện nhanh | Web tĩnh HTML/CSS/JS (`ui-prototype/`), server Node nhỏ cho xem thử | Đã có |
| Thiết kế & bàn giao | Figma (file `figma_vwing_full.json`, node logo 2038:10), Google Stitch handoff, landing page tĩnh | Đã có |
| Prototype chạy thật trên tablet | **Android native: Kotlin + Jetpack Compose + Material 3** (design system `VDesign.kt`, `VComponents.kt`, font Plus Jakarta Sans) | Đã có, cài trên tablet TB321FU |
| Bản đồ | Google Maps SDK + maps-compose 8.2.1; canvas bản đồ offline riêng (`GardenMap.kt`) | Đã có |
| Mã QR / ảnh | ZXing + CameraX; FileProvider | Đã có |
| Giọng nói | `RecognizerIntent` (Android) | Đã có |
| Đồng bộ + tài khoản | Máy chủ Node `vwing-sync-server` (không thư viện ngoài): đăng nhập SĐT + mật khẩu (scrypt), RBAC, mời, kiểm toán, xoá lan truyền, trang công khai R6, chia sẻ vị trí trực tiếp | Đã có, 12 test; chạy qua **Cloudflare Quick Tunnel** (HTTPS) |
| GIS nặng | **RustGIS** (gói PyPI `rustgis`, Apache-2.0) trong máy chủ vệ tinh: nhập KMZ/GPX/Shapefile/GPKG/CSV, xuất KML/GPX/CSV/WKT, buffer/clip/erase/dissolve/simplify, thống kê raster theo ranh | Đã có, test với máy chủ thật |
| Tìm địa điểm | **Photon** (komoot, Apache-2.0) dữ liệu OSM Việt Nam, tự dựng ở `E:\Photon` (461 nghìn địa điểm), có dự phòng demo công cộng | Đã có |
| Phát hành thử | APK release ký (R8), Cloudflare Tunnel, gửi APK trực tiếp | Đã build; chưa chạy thử bản release trên máy |
| Vệ tinh thử | FastAPI + earthengine-api + eemont + spyndex + hydrafloods; chế độ **mock** mặc định | Mock đã kiểm; **live chưa kiểm chứng đầy đủ** |
| Kiểm thử | JUnit **142 test**, pytest **77 test** (máy chủ vệ tinh), `node --test` **12 test** (VWing), adb wireless, screencap; quy trình test tay `E:\VWing-test\QUY-TRINH-TEST.md` | Đã có (18/10) |

Lý do chọn: Compose cho phép cùng một mã chạy tablet/điện thoại, truy cập camera/GPS/offline trực tiếp — các thứ mà web prototype không làm được.

### B2. Công nghệ dự kiến cho sản phẩm thật `[ĐỀ XUẤT]`

**Kiến trúc tổng thể:** app **offline-first** (ghi vào máy trước, đồng bộ sau) → API gateway → các dịch vụ miền → kho dữ liệu; dịch vụ GIS/vệ tinh/AI tách riêng.

| Tầng | Công nghệ đề xuất | Lý do / liên hệ với hiện có |
|---|---|---|
| **App nông dân & nhân viên** | Giữ **Kotlin + Jetpack Compose** (Android trước). Thêm **Room (SQLite)** làm kho cục bộ, **WorkManager** cho đồng bộ nền, **Hilt** để chia module. iOS để giai đoạn sau (Kotlin Multiplatform hoặc SwiftUI) | Đã có 119 test và logic hợp nhất/giải xung đột; vườn thường mất sóng → bắt buộc offline-first |
| **Web quản trị** (chủ vườn/HTX/admin) | React + TypeScript + MapLibre GL, hoặc nâng cấp từ `ui-prototype` | Làm việc trên màn hình lớn: báo cáo, phân quyền, nhập hàng loạt |
| **API & nghiệp vụ** | Node.js (NestJS) hoặc FastAPI — **chọn một ngôn ngữ chính**; REST + OpenAPI; WebSocket/SSE cho thông báo | Máy chủ sync hiện là Node thuần → tái dùng giao thức `If-Match`/ID thao tác |
| **Cơ sở dữ liệu** | **PostgreSQL + PostGIS** (cây, thửa, khu, ranh giới, truy vấn không gian); Redis (cache/hàng đợi) | Hiện lưu snapshot JSON, không đủ cho truy vấn nhiều vườn/nhiều người |
| **Lưu ảnh/tệp** | Object storage (S3-tương thích: AWS S3 / Cloudflare R2 / MinIO), ảnh nén + thumbnail, URL ký hạn | Ảnh cây, ảnh drone, KML/GeoJSON, báo cáo |
| **Xác thực & phân quyền** | OIDC/OAuth2 (Keycloak hoặc Firebase Auth / Auth0); đăng nhập **số điện thoại + OTP** (hợp với nông dân VN), JWT ngắn hạn + refresh; **RBAC theo vườn** (xem Phần C) | Thay mã chung hiện tại; DJI cũng dùng đội + chủ/thành viên |
| **Đồng bộ** | Giữ mô hình hiện tại: kéo → hợp nhất → đẩy với `If-Match`, ID thao tác chống trùng, mục "Xung đột" để người dùng chọn; bổ sung **xoá lan truyền (tombstone)** và đồng bộ thông tin vườn | Hai điểm "chưa làm" đã ghi trong README sync-server |
| **GIS & vệ tinh** | Sentinel-2/Sentinel-1 qua **Google Earth Engine** (giữ `eemont`, `spyndex`, `hydrafloods`) hoặc **Copernicus Data Space / STAC + rasterio** nếu cần tránh giấy phép thương mại EE; chạy trên Cloud Run/worker; kết quả lưu COG/GeoTIFF + tile | Backend FastAPI đã có; **cần chạy live và đối chiếu với Code Editor trước khi tin số** |
| **Drone / ảnh** | Nhập log DJI (Protobuf, sau khi xác nhận tag bằng file thật); ảnh orthomosaic từ **OpenDroneMap/WebODM**; bản đồ NDVI từ ảnh đa phổ; LiDAR qua PDAL/laspy | Đã có `DjiFlightLog.kt`, `PointCloudAnalysis.kt`, `Ndvi.kt` |
| **AI trợ lý** | LLM qua API (Claude) theo kiểu **truy vấn có cấu trúc → tổng hợp → dẫn nguồn bản ghi**; không trả lời khi thiếu dữ liệu. Vision model chẩn đoán ảnh lá/trái **chỉ ở mức gợi ý**, chủ vườn xác nhận. STT: Whisper hoặc dịch vụ giọng nói tiếng Việt | Đúng định hướng slide trang 16–18 và P0-04 |
| **Thời tiết/đất/nước** | API thời tiết (Open-Meteo/OpenWeather), nhập tay + cảm biến IoT qua MQTT (giai đoạn sau) | `EnvScreen` hiện nhập tay |
| **Hạ tầng & vận hành** | Docker; Cloud Run hoặc Kubernetes nhỏ; CI/CD GitHub Actions; Terraform; HTTPS bắt buộc; giám sát Sentry + Prometheus/Grafana; sao lưu DB hằng ngày | Hiện chỉ chạy LAN `http://` |
| **Bảo mật & dữ liệu** | Mã hoá khi truyền/lưu; khoá/token chỉ ở server (không trong APK); nhật ký kiểm toán; xuất/xoá dữ liệu theo yêu cầu (Nghị định 13/2023/NĐ-CP về dữ liệu cá nhân) | Dữ liệu vị trí vườn là nhạy cảm |

### B3. Lộ trình công nghệ

| Giai đoạn | Nội dung | Điều kiện qua cổng |
|---|---|---|
| **0 — Prototype (hiện tại)** | Compose + sync + tài khoản/RBAC + GIS + tìm địa điểm; vệ tinh live chạy được qua tunnel | Đã xong phần lớn; còn OTP, HTTPS cố định, kiểm tra bố cục ngang |
| **1 — MVP pilot (1–3 vườn)** | Tài khoản OTP + RBAC; Room + WorkManager; server HTTPS + PostgreSQL/PostGIS; xoá lan truyền; sao lưu | Mất mạng vẫn ghi được; hai thiết bị sửa cùng bản ghi không mất dữ liệu; đăng nhập tách quyền đúng |
| **2 — Dữ liệu thực địa** | Chạy Earth Engine live và đối chiếu; WebODM; xác nhận định dạng log DJI bằng file thật; web quản trị | Sai số NDVI so với Code Editor trong ngưỡng đã thoả thuận |
| **3 — AI & tối ưu** | Trợ lý AI truy vấn hồ sơ thật; chẩn đoán ảnh gợi ý; cảnh báo chủ động; EUDR/truy xuất nguồn gốc | Pilot đo được giảm thời gian ghi chép / phát hiện sớm |
| **4 — Mở rộng** | iOS, HTX/nhiều vườn, IoT, chợ dịch vụ bay (giống `grabbedOrders` của DJI) | Có khách trả phí |

### B4. Rủi ro công nghệ

- Earth Engine: điều khoản thương mại, hạn mức, chi phí — cần quyết định sớm EE hay Copernicus.
- Log DJI: tag lồng nhau chưa xác nhận; cần một file xuất thật; định dạng có thể đổi theo phiên bản app.
- API riêng của DJI: **không dùng** (không có quyền chính thức); chỉ nhập file do người dùng tự xuất.
- Chẩn đoán bệnh bằng ảnh: dễ sai, phải luôn là gợi ý có người xác nhận.
- Hiện UI trên tablet chưa kiểm thử bố cục ngang; chưa kiểm tra live Google Maps overlay trên nhiều máy.

---

## PHẦN C. USER ROLES & FEATURES

### C1. Danh sách vai trò `[ĐỀ XUẤT, dựa trên A2 + chức năng đã có trong VWING]`

| # | Vai trò | Mô tả | Tương ứng DJI SmartFarm |
|---|---|---|---|
| R1 | **Chủ vườn (Owner)** | Sở hữu vườn, quyết định cuối cùng, xem toàn bộ, quản lý thành viên | Chủ/Trưởng đội (`isOwnerOrLeader`) |
| R2 | **Quản lý vườn / Kỹ thuật viên (Manager)** | Điều hành hằng ngày, giao việc, xác minh vấn đề, dùng GIS/vệ tinh | Trưởng đội, quản lý thửa |
| R3 | **Nhân viên thực địa (Field Worker)** | Ghi nhật ký, chụp ảnh, quét QR, làm công việc được giao | Thành viên đội bay |
| R4 | **Phi công drone / Đơn vị bay thuê (Drone Operator)** | Nhập log bay, ảnh drone; thấy phạm vi được cấp | Phi công, `grabbedOrders`, `flyer.rank` |
| R5 | **Chuyên gia / Cố vấn nông học (Agronomist, chỉ xem + ghi chú)** | Xem hồ sơ cây, ảnh, chỉ số; thêm khuyến nghị có chữ ký | (không có tương ứng rõ) |
| R6 | **Người mua / Kiểm toán truy xuất (Buyer/Auditor, chỉ xem công khai)** | Quét QR để xem lịch sử công khai của cây/lô (EUDR) | (không có) |
| R7 | **Quản trị hệ thống (Admin)** | Quản lý tài khoản, gói dịch vụ, nhật ký kiểm toán, hỗ trợ | Phía DJI |
| R8 | **Trợ lý AI (tác nhân hệ thống)** | Không phải người dùng; chạy với quyền của người hỏi, chỉ đọc | — |

### C2. Chức năng theo module — nguồn gốc & trạng thái

Cột "Có trong app": ✅ đã cài trên Android VWING · 🟡 một phần · ⬜ chưa có.

| Module | Chức năng | DJI có? | Có trong app |
|---|---|---|---|
| **Tài khoản** | Đăng nhập OTP/QR, tạo vườn, mời thành viên bằng QR/SĐT, phân quyền | ✅ | 🟡 (SĐT + mật khẩu, chưa OTP; RBAC ở máy chủ) |
| **Tổng quan** | Việc hôm nay, cảnh báo, thời tiết, thống kê | ✅ | ✅ |
| **Vườn/Thửa/Khu** | Thiết lập vườn, vẽ ranh giới, nhập KML/GeoJSON, đo diện tích | ✅ | ✅ (KML/GeoJSON) |
| **Cây** | Hồ sơ cây, ID bền vững, QR, timeline, ảnh trước–sau | ⬜ (DJI theo thửa) | ✅ |
| **Bản đồ** | Bản đồ vườn, lớp NDVI/thay đổi/SR, Google Maps vệ tinh | ✅ | ✅ (marker cây chưa vẽ trên Google Map) |
| **Nhật ký** | Ghi hoạt động (chạm, giọng nói, ảnh, GPS), lọc, xuất | ✅ | ✅ |
| **Vấn đề** | Quy trình: ghi nhận → xác minh → xử lý → tái kiểm tra → xác nhận | ⬜ | ✅ |
| **Công việc** | Tạo/giao việc, hạn, quá hạn, kết quả | ✅ | 🟡 |
| **Sản lượng & giá** | Ước tính, thực thu, giả định rõ | ⬜ | ✅ |
| **Ảnh & NDVI** | Bộ ảnh theo thời gian, NDVI, xem ảnh | ⬜ | ✅ |
| **Nước/đất/thời tiết** | Ghi nhận có nguồn | ✅ (thời tiết) | ✅ |
| **Vệ tinh** | Sentinel-2 NDVI/EVI/NDMI, Sentinel-1 nước, Super-Resolution, LiDAR, quy trình GIS | ⬜ | ✅ (live chưa kiểm chứng) |
| **Drone** | Nhập log bay DJI, danh sách chuyến | ✅ | 🟡 (chỉ đếm khung, chưa vẽ tuyến) |
| **Thiết bị** | Danh sách drone, kiểm tra hằng ngày, sửa chữa | ✅ | ⬜ |
| **AI** | Hỏi đáp từ hồ sơ có dẫn nguồn | ⬜ | ✅ |
| **Đồng bộ/Sao lưu** | Offline, hợp nhất, xung đột | ✅ | ✅ (LAN, chưa HTTPS) |
| **Báo cáo** | HTML/JSON/CSV, báo cáo ngày–tuần–tháng | ✅ | 🟡 |
| **Truy xuất nguồn gốc** | Trang công khai theo QR, EUDR | ⬜ | ✅ (trang công khai theo trường chủ chọn; chưa xuất EUDR) |
| **Thông báo** | Trung tâm thông báo, lời mời | ✅ | ⬜ |
| **Chợ dịch vụ bay** | Đăng/nhận đơn phun | ✅ | ⬜ (giai đoạn 4) |

### C3. Ma trận quyền theo vai trò `[ĐỀ XUẤT]`

Ký hiệu: **X** toàn quyền · **S** sửa/tạo · **V** chỉ xem · **O** chỉ phần của mình/được giao · **–** không.

| Chức năng | R1 Chủ | R2 Quản lý | R3 Nhân viên | R4 Phi công | R5 Chuyên gia | R6 Người mua | R7 Admin |
|---|---|---|---|---|---|---|---|
| Quản lý thành viên & phân quyền | X | S (trừ chủ) | – | – | – | – | X |
| Thiết lập vườn, ranh giới, khu | X | S | V | V (vùng được cấp) | V | – | – |
| Hồ sơ cây (xem) | X | X | V | V (vùng bay) | V | V công khai | – |
| Hồ sơ cây (sửa/tạo/thay cây) | X | S | S (có dấu vết) | – | – | – | – |
| Ghi nhật ký / ảnh / QR | X | S | S | S (bay) | S (ghi chú) | – | – |
| Tạo & giao công việc | X | S | O (nhận) | O (nhận) | – | – | – |
| Vấn đề: mở / xử lý | X | S | S (mở) · O (xử lý) | – | S (khuyến nghị) | – | – |
| Vấn đề: **xác nhận kết quả đóng** | X | S | – | – | – | – | – |
| Bản đồ & lớp vệ tinh/NDVI | X | X | V | V | V | – | – |
| Chạy phân tích vệ tinh/GIS/LiDAR | X | S | – | – | S | – | – |
| Nhập log/ảnh drone | X | S | – | S | – | – | – |
| Sản lượng, doanh thu, giá | X | V | – | – | V | – | – |
| Hỏi AI | X | S | S (trong phạm vi quyền) | S (vùng bay) | S | – | – |
| Xuất dữ liệu / báo cáo | X | S | – | O | S | – | – |
| Xung đột đồng bộ | X | S | – | – | – | – | X |
| Trang truy xuất công khai | – | – | – | – | – | V | – |
| Nhật ký kiểm toán, gói dịch vụ | V | – | – | – | – | – | X |

**Nguyên tắc:** (1) quyền theo **vườn** và theo **khu/phạm vi** được cấp; (2) phi công thuê chỉ thấy vùng và khoảng thời gian được giao, hết hạn tự thu hồi; (3) người hoàn tất việc không tự đóng vấn đề (đúng P0-03); (4) AI không vượt quyền của người hỏi; (5) mọi sửa/xoá có dấu vết kiểm toán.

### C4. Luồng chính theo vai trò

- **Nhân viên (R3):** mở app → xem việc hôm nay → quét QR cây → ghi nhật ký (giọng nói/ảnh) → lưu offline → tự đồng bộ khi có mạng.
- **Quản lý (R2):** xem tổng quan/cảnh báo → xem NDVI vùng suy giảm → tạo vấn đề + giao việc → nhân viên xử lý → tái kiểm tra → xác nhận đóng.
- **Chủ vườn (R1):** xem báo cáo, sản lượng, hỏi AI "khu nào nhiều vấn đề chưa kiểm tra lại?" → AI trả kết quả kèm bản ghi nguồn → quyết định.
- **Phi công (R4):** nhận quyền theo vùng → bay → nhập log DJI → hệ thống gắn chuyến bay với khu/cây → chủ vườn xem tuyến.
- **Người mua (R6):** quét QR trên sản phẩm → xem lịch sử công khai đã được chủ vườn cho phép.

### C5. Việc cần làm để vai trò hoạt động thật

> **Cập nhật cuối 18/10/2026:** đã thêm OTP SMS (nhà cung cấp cắm được, chưa thử SMS thật), chuyển quyền chủ vườn, đơn việc mở cho nhân viên/phi công (nhận/trả/hoàn tất, người đến trước được giữ), script Named Tunnel cho địa chỉ HTTPS cố định (cần tên miền, chưa chạy thử), đã chạy thử bố cục ngang trên tablet và vệ tinh live với 6 máy khách song song.
>
> **Trạng thái 18/10/2026:** cả 5 mục dưới đây đã làm ở `vwing-sync-server` (rbac.js, public.js) và app (Account.kt, Cascade.kt, SyncCard/AccountUi). Còn lại: OTP SMS, HTTPS, chuyển quyền chủ vườn, kiểm tra giao diện trên tablet.

1. Xây tài khoản + RBAC theo vườn (chưa có — là khoảng trống lớn nhất so với DJI).
2. Mô hình `Membership(user, garden, role, scope, expiry)` trong DB và kiểm tra quyền ở **server**, không chỉ ở giao diện.
3. Mời thành viên bằng QR/SĐT, thu hồi, nhật ký kiểm toán.
4. Xoá lan truyền và đồng bộ thông tin vườn.
5. Trang công khai cho R6 với bộ trường dữ liệu do chủ chọn.

---

## Giới hạn của tài liệu

- Ma trận vai trò của DJI **không đọc được trực tiếp** (logic ở máy chủ DJI); chỉ `TeamRole` và `isOwnerOrLeader` là bằng chứng chắc chắn. Các vai trò R4–R8 và ma trận C3 là **thiết kế đề xuất**, cần xác nhận với khách hàng và pilot.
- Số test (119) và trạng thái cài tablet lấy từ ghi chú phiên làm việc 13/10; **chưa chạy lại** trong lần soạn này.
- Thiết lập vệ tinh chế độ live, log DJI thật và bố cục ngang tablet **chưa được kiểm chứng**.
