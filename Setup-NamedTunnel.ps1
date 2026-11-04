# Địa chỉ HTTPS CỐ ĐỊNH cho VWing bằng Cloudflare Named Tunnel (cần một tên miền đã thêm vào tài khoản Cloudflare, DNS do Cloudflare quản lý).
# Chạy MỘT LẦN:   powershell -ExecutionPolicy Bypass -File Setup-NamedTunnel.ps1 -Domain vwing.example.com
# Sau đó Start-Public.ps1 tự dùng địa chỉ cố định:   https://app.<Domain>  (máy chủ VWing)   và   https://sat.<Domain>  (máy chủ vệ tinh).
param([Parameter(Mandatory = $true)][string]$Domain, [string]$Name = 'vwing')
$ErrorActionPreference = 'Stop'
if ($Domain -notmatch '^[a-z0-9.-]+\.[a-z]{2,}$') { throw 'Tên miền không hợp lệ (ví dụ: vwing.example.com).' }
$cf = "${env:ProgramFiles(x86)}\cloudflared\cloudflared.exe"
if (-not (Test-Path $cf)) { $cf = (Get-Command cloudflared).Source }
$cfDir = Join-Path $env:USERPROFILE '.cloudflared'

if (-not (Test-Path (Join-Path $cfDir 'cert.pem'))) {
    Write-Host 'Bước 1/4: đăng nhập Cloudflare (trình duyệt sẽ mở, chọn tên miền của bạn)…'
    & $cf tunnel login
    if (-not (Test-Path (Join-Path $cfDir 'cert.pem'))) { throw 'Chưa đăng nhập Cloudflare (không thấy cert.pem).' }
}
Write-Host "Bước 2/4: tạo tunnel '$Name'…"
$existing = (& $cf tunnel list --output json | ConvertFrom-Json) | Where-Object { $_.name -eq $Name } | Select-Object -First 1
if ($existing) { $id = $existing.id; Write-Host "Đã có tunnel $Name ($id)." }
else {
    $out = & $cf tunnel create $Name 2>&1 | Out-String
    $m = [regex]::Match($out, '[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}')
    if (-not $m.Success) { throw "Không đọc được mã tunnel: $out" }
    $id = $m.Value
}
$cred = Join-Path $cfDir "$id.json"
if (-not (Test-Path $cred)) { throw "Không thấy tệp khoá $cred (tunnel cũ tạo ở máy khác?). Xoá tunnel cũ rồi chạy lại." }

$app = "app.$Domain"; $sat = "sat.$Domain"
Write-Host "Bước 3/4: trỏ DNS $app và $sat về tunnel…"
& $cf tunnel route dns --overwrite-dns $Name $app
& $cf tunnel route dns --overwrite-dns $Name $sat

Write-Host 'Bước 4/4: ghi cấu hình…'
$cfg = Join-Path $PSScriptRoot 'named-tunnel.yml'
@"
tunnel: $id
credentials-file: $cred
ingress:
  - hostname: $app
    service: http://127.0.0.1:8790
  - hostname: $sat
    service: http://127.0.0.1:8000
  - service: http_status:404
"@ | Set-Content $cfg -Encoding ASCII
@{ app = "https://$app"; sat = "https://$sat"; config = $cfg } | ConvertTo-Json | Set-Content (Join-Path $PSScriptRoot 'named-tunnel.json') -Encoding UTF8
Write-Host "Xong. Địa chỉ cố định: https://$app  và  https://$sat"
Write-Host 'Chạy Start-Public.ps1 như bình thường; trong app nhập địa chỉ trên (một lần, không đổi nữa).'
