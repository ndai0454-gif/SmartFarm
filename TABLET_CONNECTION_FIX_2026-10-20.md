# Sửa kết nối Lenovo TB321FU — 20/10/2026

## Kết quả

- ADB nhận USB serial `HA24RANR`, model `TB321FU`, Android 15.
- Android Studio log mới xác nhận `Device [HA24RANR] has come online`, `Successfully connected to adb` và nhận tiến trình `com.example.test_tablet` PID 30192 để debug.
- Lệnh shell qua USB trả `USB_CONNECTION_OK`; danh sách ADB cuối lần kiểm tra chỉ có kết nối USB.
- Terminal đã chuyển sang `CLASSIC`, tắt tự bật popup completion. Profile mới có `terminal.xml` và log xác nhận terminal JediTerm được tạo.
- Chưa quan sát lại lỗi `TerminalCommandCompletionTypingListener` / `LockAccessDisallowed` / `ClangPPMatchingHighlighter` trong log của phiên IDE mới. Đây là kiểm tra phiên khởi động mới, chưa phải bằng chứng lỗi không bao giờ tái diễn.

## Nguyên nhân được kiểm tra

1. Windows nhận USB ADB interface bằng `winusb.inf` / service `WINUSB`, nhưng `Device Parameters` thiếu `DeviceInterfaceGUIDs`. ADB không liệt kê thiết bị USB. Sau khi thêm Android ADB interface GUID `{F72FE0D4-CBCB-407D-8814-9ED673D0DD6B}` và restart đúng giao diện USB, serial `HA24RANR` xuất hiện ngay.
2. Trước sửa, hai mục mDNS Wi-Fi cùng tablet xuất hiện; một endpoint đã cũ. IDE liên tục chép agent và báo `process-tracker: Text file busy`, kết nối mirroring bị hủy. Kết nối được chuyển về USB; tự kết nối mDNS được tắt trong môi trường tiến trình ADB cuối lần kiểm tra, không thay đổi biến môi trường toàn hệ thống.
3. Stack trace người dùng gửi là lỗi terminal completion/C++ highlighter của IDE, có báo lỗi trùng khớp tại https://youtrack.jetbrains.com/issue/CPP-50015. Đây là vấn đề riêng với việc Windows thiếu đăng ký USB interface.

## Thay đổi đã áp dụng

- `app/Repair-TabletUsb.ps1`: kiểm tra đúng VID/PID/interface và service WINUSB, sao lưu registry trước khi thêm GUID, restart riêng ADB interface. Đã chạy thành công với quyền Administrator.
- Backup: `logs/tablet-usb-repair/20261020-141550-device-parameters.reg`.
- Log sửa: `logs/tablet-usb-repair/20261020-141550.log`.
- Không đổi driver MTP, không cài APK, không xóa dữ liệu tablet.
- `app/Connect-Tablet.ps1`: dùng SDK ADB, tìm endpoint Wi-Fi đang quảng bá của serial đã biết, kiểm tra serial thật sau khi kết nối; ưu tiên USB khi đã có. Đã kiểm tra cú pháp và chạy thành công khi tablet còn qua Wi-Fi.
- Cấu hình terminal hiện tại: `C:\Users\ndai0\AppData\Roaming\Google\AndroidStudio2026.2.1\options\terminal.xml`.

## Thay đổi IDE cần biết

Trong lúc thao tác menu Settings, coordinator đã kích hoạt nhầm cập nhật IDE. Android Studio đã được cập nhật từ Quail 4 / 2026.1.4 Patch 1 sang Rabbit 1 / 2026.2.1, build `AI-262.9437.185.2621.16467767`, và mở lại project. Đây là thay đổi ngoài ý định ban đầu; đã thông báo trực tiếp trong chat. Cấu hình Classic terminal đã được chuyển sang profile mới và kiểm tra sau khởi động.

## Kiểm tra lại

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" devices -l
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" -s HA24RANR shell getprop ro.product.model
```

Kết quả mong đợi: `HA24RANR device` và `TB321FU`. Nếu tablet yêu cầu cho phép USB debugging, người dùng tự chấp thuận trên tablet. Wi-Fi vẫn có thể bật lại tự động khi một tiến trình khác khởi động ADB với môi trường mặc định; nếu chỉ cần USB, chọn thiết bị mang serial `HA24RANR`.

Log IDE đã xác minh: `C:\Users\ndai0\AppData\Local\Google\AndroidStudio2026.2.1\log\idea.log`, các mốc 14:20:44–14:20:45 (Asia/Saigon).

Chưa chạy lại build/deploy app hay xác minh mirroring hình ảnh sau cập nhật; những kiểm tra đó không cần thiết để xác minh USB và IDE nhận tablet.
