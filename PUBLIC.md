# Đưa VWing ra Internet (Cloudflare Quick Tunnel)

Chạy: `powershell -ExecutionPolicy Bypass -File Start-Public.ps1`
Script bật 2 tunnel HTTPS (VWing cổng 8790, vệ tinh cổng 8000), khởi động hai máy chủ và ghi địa chỉ vào `public-urls.txt`.

- Địa chỉ `*.trycloudflare.com` ĐỔI mỗi lần chạy, và máy tính phải bật + có mạng thì bạn bè mới dùng được.
- Bạn bè nhập địa chỉ VWing vào app (Thiết lập → Tài khoản & đồng bộ), tạo tài khoản, rồi bạn mời họ vào vườn bằng QR.
- Địa chỉ vệ tinh + mã `.satellite-token` chỉ đưa cho người tin cậy (dùng chung hạn mức Earth Engine của bạn).
- `.vwing-admin-token` là mã quản trị, không đưa cho ai.
- Địa chỉ cố định: cần tên miền trên Cloudflare, dùng `cloudflared tunnel login` → `tunnel create vwing` → `tunnel route dns`, rồi chạy tunnel với `--url` như trong script.
