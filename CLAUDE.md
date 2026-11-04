# CLAUDE.md — Hướng dẫn Kiến trúc & Chuẩn Giao diện Native SmartFarm Aurora / VWing

Tài liệu cấu hình ngữ cảnh cho AI Coding Agent (Claude Code, Cursor, Antigravity) khi phát triển và vận hành dự án **SmartFarm Aurora / VWing**.

## 1. Cấu trúc Dự án
- `E:\New folder\SmartFarm\app\`: Ứng dụng Android Hybrid (`com.utc.ag` — `SmartFarmDashboardActivity`) đóng gói giao diện điều hành Liquid Glass.
- `E:\New folder\SmartFarm\ui-prototype\`: Giao diện Web/Hybrid (`index.html`, `datumapp-features.js`, `farmblock-features.js`, `server.js` cổng `8765`).
- `E:\Android\`: Ứng dụng Android gốc Kotlin & Jetpack Compose (`com.example.test_tablet`), đồng bộ APK sang `app/SmartFarm-Aurora-Mobile.apk`.
- `E:\New folder\SmartFarm\vwing-satellite-backend\`: Máy chủ Python FastAPI + Google Earth Engine + Open-Meteo (cổng `8000`).
- `E:\New folder\SmartFarm\vwing-sync-server\`: Máy chủ đồng bộ Node.js RBAC & Truy xuất nguồn gốc QR (cổng `8790`).
- `E:\New folder\SmartFarm\mobile-release-suite\`: Bộ công cụ 5 bước phát hành ứng dụng:
  1. `skills/build-native-ui/SKILL.md`: Quy chuẩn Material 3 & Apple Liquid Glass UI.
  2. `icon-generator/generate-icons.js`: Công cụ sinh App Icon tự động cho Android Adaptive Icon & Xcode `.appiconset`.
  3. `preflight/preflight-check.js`: Công cụ quét lỗi App Store Review Guidelines & Google Play trước khi submit.
  4. `aso/aso-audit.js`: Công cụ phân tích từ khóa ASO & đối chiếu đối thủ cạnh tranh.
  5. `apple-search-ads/asa_manager.py`: Bộ kịch bản tự động hóa chiến dịch Apple Search Ads (ASA) & Custom Product Pages (CPP).

## 2. Lệnh Thường Dùng
```powershell
# 1. Sinh bộ App Icon đa nền tảng (Android mipmap + iOS AppIcon.appiconset)
node mobile-release-suite/icon-generator/generate-icons.js

# 2. Chạy kiểm tra App Store & Google Play Preflight trước khi submit
node mobile-release-suite/preflight/preflight-check.js

# 3. Chạy kiểm toán ASO (App Store Optimization) & so sánh đối thủ
node mobile-release-suite/aso/aso-audit.js

# 4. Chạy bộ tối ưu hóa chiến dịch Apple Search Ads (ASA) & Custom Product Pages
python mobile-release-suite/apple-search-ads/asa_manager.py --simulate

# 5. Biên dịch APK Android
.\gradlew.bat :app:assembleDebug
```
