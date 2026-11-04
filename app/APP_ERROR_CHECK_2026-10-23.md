# Kiểm tra app — 23/10/2026

Phạm vi: E:\New folder\SmartFarm\app. Chỉ kiểm tra, không sửa mã ứng dụng/cấu hình và không cài APK lên tablet.

## Kết quả

- `gradlew.bat :app:assembleSatelliteDebug :app:testSatelliteDebugUnitTest --offline --console=plain`: BUILD SUCCESSFUL. Phần compile/assemble dùng cache UP-TO-DATE; unit test NO-SOURCE, không có test để chạy.
- `vwing-satellite-backend\.venv\Scripts\python.exe app/verify_satellite_http.py --mode mock`: PASS 4/4 NDVI, EVI, NDMI, water; xác minh auth, bounds, nhãn simulated và PNG. Không xác minh GEE live hay UI thiết bị.
- Parser PowerShell: 7/7 script .ps1 trực tiếp dưới app không có lỗi cú pháp. Python AST: verify_satellite_http.py không có lỗi cú pháp.
- Lint hoàn tất trong 2 phút 23 giây với heap 6 GB (không đổi gradle.properties): 2.511 Error + 3 Fatal = 2.514 lỗi, 1.250 Warning. Lệnh vẫn BUILD SUCCESSFUL vì abortOnError false. Báo cáo mới ngày 23/10/2026 ở app/build/reports/lint-results-satelliteDebug.html và XML/TXT cùng tên. Chi tiết theo file/dòng: logs/app-lint-findings-20261023.csv. Các issue tại src/satellite chỉ có 44 Warning; không có Error/Fatal.

## Các lỗi đã xác định

### 1. Legacy: mã giải biên dịch không phải Java hợp lệ

Có 11.104 file Java trong app/src/main/java. Tìm thấy 75 file có dòng bắt đầu bằng khai báo kiểu `??` hoặc `final ??`, vốn không hợp lệ trong Java. Đây là danh sách theo mẫu cụ thể, không phải tổng số file lỗi đầy đủ.

Ví dụ:
- app/src/main/java/expo/modules/kotlin/activityresult/ActivityResultsManager.java:95 — `final ?? r2 = ...`
- app/src/main/java/okhttp3/internal/ws/WebSocketExtensions.java:121,129 — `?? r0 = z`, `?? r2 = z2`
- app/src/main/java/okhttp3/internal/ws/RealWebSocket.java:490 — `?? Poll = ...`
- app/src/main/java/kotlinx/coroutines/scheduling/WorkQueue.java:169 — `?? r1`

Danh sách: logs/app-legacy-invalid-java-20261023.txt.

app/build.gradle:48-60 tách Java giải biên dịch vào legacy; satellite có nguồn riêng. Vì vậy Satellite build thành công không chứng minh legacy build được. Chưa chạy build legacy; các ký hiệu trên được xác nhận trực tiếp trong mã nguồn.

### 2. Phạm vi lint vẫn kéo cả mã legacy vào satellite

Model lint mới tạo: app/build/intermediates/lint_report_lint_model/satelliteDebug/generateSatelliteDebugLintReportModel/satelliteDebug.xml:16 có `src\main\java` trong javaDirectories dù app/build.gradle:52 đặt main.java.srcDirs=[]. Do đó lintSatelliteDebug vẫn quét vùng 11.104 file giải biên dịch, gây nhiễu kết quả và tốn nhiều bộ nhớ/thời gian.

app/build.gradle:45 đặt abortOnError false; exit thành công của lint không đồng nghĩa hết lỗi. gradle.properties:3 hiện giới hạn heap mặc định 2 GB; lần kiểm tra này dùng override 6 GB cho riêng lệnh.

### 3. SatelliteActivity.java: số liệu tán cây không phản ánh trạng thái dữ liệu

File: app/src/satellite/java/com/utc/ag/satellite/SatelliteActivity.java
- Dòng 95: hiển thị sẵn “Sức khỏe tán · 87.5%” khi chưa phân tích; dòng 104 làm sáng sẵn 13/16 vạch.
- Dòng 398: Math.max(2, ...) luôn sáng ít nhất hai vạch, kể cả độ phủ 0%.
- Dòng 502: thiếu canopy_cover_pct vẫn dùng giá trị 76%; dòng 661 thiếu emergence_survival_pct vẫn dùng 90% và hiển thị qua cùng nhãn độ phủ tán.
- Dòng 272-291,351-356,879-886 đổi/xóa vùng và clearOverlay nhưng không reset KPI. clearOverlay tại 707-712 chỉ xóa overlay, marker và legend. Số liệu vùng trước có thể còn cạnh vùng mới.

Đây là kết luận từ mã; chưa tái hiện UI trên thiết bị. Nên biểu diễn “Chưa có dữ liệu”, cho phép 0 vạch và reset KPI khi đổi vùng/phương pháp.

### 4. Hai script không nhận diện địa chỉ mạng 10.x.x.x

- app/Start-SatelliteBackend.ps1:13
- app/Run-SatelliteForever.ps1:12

Regex `^(192\.168|10\.|172\.(1[6-9]|2\d|3[01]))\.` có dấu chấm trong nhánh 10\. rồi thêm dấu chấm sau nhóm, khiến nhánh này yêu cầu 10.. thay vì 10.

Tái hiện: 192.168.1.10=True, 172.16.0.5=True, 10.0.0.2=False. Start-SatelliteBackend có thể rơi về 127.0.0.1 khi máy chỉ dùng LAN 10.x; script chờ mạng có thể bỏ lỡ mạng đang có. Chỉ kiểm tra biểu thức, không khởi động các script.

### 5. Preset ngày bị cố định

SatelliteActivity.java:212-218 gắn cứng các nút 30/60/90 ngày vào mốc 2026-09-01. Tại 23/10/2026 đây không phải các khoảng lùi theo ngày hiện tại; “90 ngày” 01/06→01/09 cũng là 92 ngày. Nếu ý định là kỳ ảnh gần nhất thì cần tính động hoặc ghi rõ ngày kỳ cố định.

## Bằng chứng và giới hạn

Log build: logs/app-check-20261023-build.log.
Log HTTP mock: logs/app-check-20261023-http-mock.log.
Log lint: logs/app-check-20261023-lint.log.
Không kiểm tra tablet, Google Maps runtime, GEE live, release hay APK legacy; không dùng số lỗi của báo cáo lint cũ ngày 13/10 làm kết quả hiện tại.


## Phân bố lint mới

Nhóm nhiều nhất: WrongConstant 1.806; NewApi 504; ResourceType 102. Các số này là nhận định lint trên mã giải biên dịch, không phải 2.514 lỗi biên dịch Satellite.

Ví dụ file có nhiều vị trí lỗi: MediaMetricsListener.java (60), ViewCompat.java (52), RecyclerView.java (52), TypefaceCompatApi29Impl.java (49), AppCompatDelegateImpl.java (43). Đường dẫn đầy đủ và dòng nằm trong CSV.

Ba lỗi Fatal: SoftWareCanvas.java:49-50 (BlockedPrivateApi, truy cập mTileX/mTileY bị chặn từ target API 34); EmojiCompatInitializer.java:15 (EnsureInitializerMetadata, thiếu metadata manifest). Chúng đều thuộc src/main/java legacy.

44 cảnh báo tại src/satellite: 42 SetTextI18n ở SatelliteActivity.java, 2 cảnh báo manifest (DataExtractionRules, MissingApplicationIcon). Các lỗi KPI và preset mô tả phía trên là kiểm tra logic thủ công, không nằm trong số lỗi lint này.

## Bổ sung: AndroidManifest.xml legacy

File được người dùng xác nhận: app/src/main/com.utc.ag.apk/AndroidManifest.xml.

- Cả 3 manifest đều parse XML thành công.
- `:app:processLegacyDebugMainManifest --offline`: BUILD SUCCESSFUL. Cảnh báo ACCESS_NETWORK_STATE khai báo trùng ở dòng 7 và 11; extractNativeLibs không nên đặt trong source manifest ở dòng 91.
- `:app:processLegacyDebugResources --offline`: BUILD FAILED, Android resource linking failed. Ví dụ thiếu integer/ucrop_progress_loading_anim_time; thiếu các màu com_facebook_primary_button_* và common_google_signin_*; drawable/abc_btn_check_material.xml dùng giá trị 0x7f08000c không hợp lệ cho drawable reference.
- Vì vậy có lỗi thật ở bộ nguồn/tài nguyên legacy; chưa có bằng chứng lỗi cú pháp XML manifest. Báo đỏ IDE còn phụ thuộc variant và thông báo cụ thể tại dòng đang chọn; không suy ra tất cả các dòng đỏ đều là lỗi XML.
- Log: logs/app-check-20261023-legacy-manifest.log và logs/app-check-20261023-legacy-resources.log.
- Chưa sửa manifest hay thêm tài nguyên giả để làm hết báo lỗi.
