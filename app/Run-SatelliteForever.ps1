# Giữ máy chủ vệ tinh VWing luôn chạy: nếu uvicorn thoát (lỗi, bị tắt) thì đợi vài giây rồi chạy lại.
# Được tác vụ "VWing Satellite Backend" của Windows gọi khi đăng nhập (xem Install-AutoStart.ps1). Log: logs\satellite-service.log
param([ValidateSet('live','mock')][string]$Mode = 'live', [int]$Port = 8000)
$ErrorActionPreference = 'Continue'
$root = Split-Path $PSScriptRoot -Parent
$logs = Join-Path $root 'logs'; New-Item -ItemType Directory -Force $logs | Out-Null
$log = Join-Path $logs 'satellite-service.log'
function Log($m) { Add-Content -Path $log -Encoding UTF8 -Value ("{0:yyyy-MM-dd HH:mm:ss} {1}" -f (Get-Date), $m) }

# Mạng (Wi-Fi) có thể chưa lên ngay sau khi đăng nhập: chờ tối đa 60 giây để lấy được địa chỉ LAN.
for ($i = 0; $i -lt 30; $i++) {
    if (Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue | Where-Object { $_.IPAddress -match '^(192\.168|10|172\.(1[6-9]|2\d|3[01]))\.' }) { break }
    Start-Sleep 2
}
$fails = 0
while ($true) {
    # cổng đã có tiến trình khác (ví dụ chạy tay): không tranh chấp, kiểm tra lại sau
    if (Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue) { Start-Sleep 30; continue }
    Log "khởi động máy chủ vệ tinh (chế độ $Mode, cổng $Port)"
    $started = Get-Date
    # tiến trình con riêng (không gộp stderr vào luồng lỗi của PowerShell: script gốc đặt ErrorAction=Stop nên sẽ chết ngay ở dòng log đầu)
    $p = Start-Process powershell -PassThru -WindowStyle Hidden -ArgumentList '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', ('"' + (Join-Path $PSScriptRoot 'Start-SatelliteBackend.ps1') + '"'), '-Mode', $Mode, '-Port', $Port `
        -RedirectStandardOutput (Join-Path $logs 'satellite.out') -RedirectStandardError (Join-Path $logs 'satellite.err')
    $p.WaitForExit()
    $ran = ((Get-Date) - $started).TotalSeconds
    Log ("máy chủ đã thoát sau {0:N0} giây" -f $ran)
    # chạy chết ngay (cấu hình hỏng...) thì lùi dần để không quay vòng liên tục
    if ($ran -lt 20) { $fails++ } else { $fails = 0 }
    Start-Sleep ([Math]::Min(300, 5 * [Math]::Pow(2, [Math]::Min($fails, 6))))
}
