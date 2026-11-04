# Giữ máy chủ đồng bộ VWing (Node, cổng 8790) luôn chạy: thoát thì đợi vài giây rồi chạy lại.
# Được tác vụ "VWing Sync Server" của Windows gọi khi đăng nhập (xem Install-AutoStart.ps1). Log: logs\sync-service.log, logs\vwing.out/.err
param([int]$Port = 8790)
$ErrorActionPreference = 'Continue'
$root = Split-Path $PSScriptRoot -Parent
$logs = Join-Path $root 'logs'; New-Item -ItemType Directory -Force $logs | Out-Null
$log = Join-Path $logs 'sync-service.log'
function Log($m) { Add-Content -Path $log -Encoding UTF8 -Value ("{0:yyyy-MM-dd HH:mm:ss} {1}" -f (Get-Date), $m) }

$node = (Get-Command node -ErrorAction SilentlyContinue).Source
if (-not $node -and (Test-Path 'C:\Program Files\nodejs\node.exe')) { $node = 'C:\Program Files\nodejs\node.exe' }
if (-not $node) { Log 'không tìm thấy Node.js (node.exe) trong PATH'; exit 1 }

# mã quản trị VWing (R7): dùng chung với Start-Public.ps1, sinh một lần
$adminPath = Join-Path $root '.vwing-admin-token'
if (-not (Test-Path $adminPath)) { [IO.File]::WriteAllText($adminPath, [Guid]::NewGuid().ToString('N') + [Guid]::NewGuid().ToString('N')) }
$env:VWING_TOKEN = [IO.File]::ReadAllText($adminPath).Trim()
$env:PORT = "$Port"
$env:DATA_DIR = Join-Path $root 'vwing-sync-server\data'
Remove-Item Env:PUBLIC_BASE_URL -ErrorAction SilentlyContinue      # LAN: server tự lấy địa chỉ từ yêu cầu; địa chỉ công khai do Start-Public.ps1 đặt

$fails = 0
while ($true) {
    if (Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue) { Start-Sleep 30; continue }
    Log "khởi động máy chủ đồng bộ (cổng $Port)"
    $started = Get-Date
    $p = Start-Process $node -PassThru -WindowStyle Hidden -ArgumentList 'server.js' -WorkingDirectory (Join-Path $root 'vwing-sync-server') `
        -RedirectStandardOutput (Join-Path $logs 'vwing.out') -RedirectStandardError (Join-Path $logs 'vwing.err')
    $p.WaitForExit()
    $ran = ((Get-Date) - $started).TotalSeconds
    Log ("máy chủ đã thoát sau {0:N0} giây (mã {1})" -f $ran, $p.ExitCode)
    if ($ran -lt 20) { $fails++ } else { $fails = 0 }
    Start-Sleep ([Math]::Min(300, 5 * [Math]::Pow(2, [Math]::Min($fails, 6))))
}
