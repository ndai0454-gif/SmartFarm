# Đưa máy chủ VWing và máy chủ vệ tinh ra Internet bằng Cloudflare Quick Tunnel (miễn phí, có HTTPS, không cần tên miền).
# Địa chỉ *.trycloudflare.com ĐỔI mỗi lần chạy: sau khi chạy phải gửi lại địa chỉ mới cho bạn bè.
# Muốn địa chỉ cố định hãy dùng named tunnel với tên miền của bạn (xem PUBLIC.md).
$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$cf = "${env:ProgramFiles(x86)}\cloudflared\cloudflared.exe"
if (-not (Test-Path $cf)) { $cf = (Get-Command cloudflared).Source }
$logs = Join-Path $root 'logs'; New-Item -ItemType Directory -Force $logs | Out-Null

# dừng bản cũ
Get-Process cloudflared -ErrorAction SilentlyContinue | Stop-Process -Force
foreach ($port in 8790, 8000, 2322) {
    Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue | ForEach-Object { Stop-Process -Id $_.OwningProcess -Force -ErrorAction SilentlyContinue }
}

function Start-Tunnel($port, $name) {
    $log = Join-Path $logs "tunnel-$name.log"; Remove-Item $log -ErrorAction SilentlyContinue
    Start-Process $cf -ArgumentList 'tunnel', '--no-autoupdate', '--url', "http://127.0.0.1:$port" -WindowStyle Hidden -RedirectStandardError $log | Out-Null
    for ($i = 0; $i -lt 40; $i++) {
        Start-Sleep 1
        if (Test-Path $log) { $m = [regex]::Match((Get-Content $log -Raw), 'https://[a-z0-9-]+\.trycloudflare\.com'); if ($m.Success) { return $m.Value } }
    }
    throw "Không lấy được địa chỉ tunnel cho cổng $port (xem $log)"
}
# Có Named Tunnel (đã chạy Setup-NamedTunnel.ps1) thì dùng địa chỉ cố định, không thì dùng Quick Tunnel (địa chỉ đổi mỗi lần).
$namedPath = Join-Path $root 'named-tunnel.json'
if (Test-Path $namedPath) {
    $named = Get-Content $namedPath -Raw | ConvertFrom-Json
    Start-Process $cf -ArgumentList 'tunnel', '--no-autoupdate', '--config', ('"' + $named.config + '"'), 'run' -WindowStyle Hidden -RedirectStandardError (Join-Path $logs 'tunnel-named.log') | Out-Null
    $vwingUrl = $named.app; $satUrl = $named.sat
    Write-Host "Dùng địa chỉ cố định: $vwingUrl , $satUrl"
} else {
    $vwingUrl = Start-Tunnel 8790 'vwing'
    $satUrl = Start-Tunnel 8000 'satellite'
}

# mã quản trị VWing (R7) sinh một lần
$adminPath = Join-Path $root '.vwing-admin-token'
if (-not (Test-Path $adminPath)) { [IO.File]::WriteAllText($adminPath, [Guid]::NewGuid().ToString('N') + [Guid]::NewGuid().ToString('N')) }
$env:VWING_TOKEN = [IO.File]::ReadAllText($adminPath).Trim()
$env:PUBLIC_BASE_URL = $vwingUrl
$env:DATA_DIR = Join-Path $root 'vwing-sync-server\data'
# Photon riêng (tìm địa điểm). Không có dữ liệu thì bỏ qua: máy chủ vệ tinh sẽ dùng máy chủ demo công cộng.
$photonOk = (Test-Path 'E:\Photon\photon_data') -and (Test-Path (Join-Path $root 'app\Start-Photon.ps1'))
if ($photonOk) {
    Start-Process powershell -ArgumentList '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', ('"' + (Join-Path $root 'app\Start-Photon.ps1') + '"') -WindowStyle Hidden `
        -RedirectStandardOutput (Join-Path $logs 'photon.out') -RedirectStandardError (Join-Path $logs 'photon.err')
    $env:PHOTON_URL = 'http://127.0.0.1:2322'
}
Start-Process node -ArgumentList 'server.js' -WorkingDirectory (Join-Path $root 'vwing-sync-server') -WindowStyle Hidden -RedirectStandardOutput (Join-Path $logs 'vwing.out') -RedirectStandardError (Join-Path $logs 'vwing.err')
Start-Process powershell -ArgumentList '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', ('"' + (Join-Path $root 'app\Start-SatelliteBackend.ps1') + '"'), '-Mode', 'live', '-Bind', '127.0.0.1', '-PublicUrl', $satUrl `
    -WindowStyle Hidden -RedirectStandardOutput (Join-Path $logs 'satellite.out') -RedirectStandardError (Join-Path $logs 'satellite.err')
Start-Sleep 6
@("Máy chủ VWing : $vwingUrl", "Máy chủ vệ tinh: $satUrl", "Cập nhật lúc $(Get-Date)") | Set-Content (Join-Path $root 'public-urls.txt') -Encoding UTF8
Get-Content (Join-Path $root 'public-urls.txt')
